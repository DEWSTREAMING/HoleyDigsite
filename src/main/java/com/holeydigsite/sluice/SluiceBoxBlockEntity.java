package com.holeydigsite.sluice;

import com.holeydigsite.panning.Panning;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State of a Sluice Box, including cached water power, queued Dirt Clumps, progress on the current
 * clump, and the output tray.
 *
 * <p>The tray is a list of stacks with {@link #TRAY_SLOTS} slots. The sluice only starts a clump while at least one
 * slot is free. A single roll can produce more than one stack, so after the last roll the tray may hold a stack or two
 * more than {@link #TRAY_SLOTS}, though nothing is ever thrown away. Only a player can empty it.</p>
 */
public class SluiceBoxBlockEntity extends BlockEntity {
    /** Clumps waiting to be washed. */
    public static final int MAX_QUEUE = com.holeydigsite.tier.Tiers.stat("sluice_box", "max_queue");
    /** Ticks per clump: 100 = 5 seconds (12 clumps a minute). */
    public static final int CYCLE_TICKS = com.holeydigsite.tier.Tiers.stat("sluice_box", "cycle_ticks");
    /** Tray slots; the sluice stops once they are all used. */
    public static final int TRAY_SLOTS = com.holeydigsite.tier.Tiers.stat("sluice_box", "tray_slots");

    /** Save format marker: sluices saved before the long sluice have none and are handed back (see {@link LongSluiceBlock#refundOld}). */
    private static final int LAYOUT = 1;
    private int layout = LAYOUT;
    private boolean refunded;
    private boolean waterPowered;
    /** Tiers of the queued clumps, oldest first, where each clump keeps its tier and rolls that tier's table. */
    private final List<Integer> queue = new ArrayList<>(8);


    private int progress;
    private final List<ItemStack> tray = new ArrayList<>();
    private String skin = "";
    /** Wear of the applied skin, empty for Fresh. */
    private String wear = "";

    public String getSkin() { return skin; }

    public String getWear() { return wear; }

    public String getSkinKey() { return com.holeydigsite.skin.SkinWear.textureKey(skin, wear); }

    public void setSkin(String skin) {
        setSkin(skin, "");
    }

    public void setSkin(String skin, String wear) {
        this.skin = skin == null ? "" : skin;
        this.wear = wear == null || com.holeydigsite.skin.SkinWear.isFresh(wear) ? "" : wear;
        setChanged();
        if (level != null) {
            if (getBlockState().getBlock() instanceof SluiceBoxBlock block) {
                for (BlockPos part : block.allParts(worldPosition, getBlockState())) {
                    BlockState ps = level.getBlockState(part);
                    level.sendBlockUpdated(part, ps, ps, 3);
                }
            }
            requestModelDataUpdate();
        }
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        return net.neoforged.neoforge.client.model.data.ModelData.builder()
                .with(com.holeydigsite.skin.SkinModelProperties.SKIN, getSkinKey())
                .build();
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        String s = input.get(com.holeydigsite.registry.ModDataComponents.SKIN.get());
        if (s != null) {
            this.skin = s;
            String w = input.get(com.holeydigsite.registry.ModDataComponents.SKIN_WEAR.get());
            this.wear = w == null ? "" : w;
        }
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (!skin.isEmpty() && !skin.equals("default")) {
            builder.set(com.holeydigsite.registry.ModDataComponents.SKIN.get(), skin);
            if (!wear.isEmpty()) {
                builder.set(com.holeydigsite.registry.ModDataComponents.SKIN_WEAR.get(), wear);
            }
        }
    }
    @Nullable
    private java.util.UUID owner;
    @Nullable
    private transient net.minecraft.server.level.ServerPlayer cachedOwner;

    @Nullable
    public java.util.UUID owner() { return owner; }

    public void setOwner(@Nullable java.util.UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public void setOwnerPlayer(@Nullable net.minecraft.server.level.ServerPlayer sp) {
        this.cachedOwner = sp;
        this.owner = sp != null ? sp.getUUID() : null;
        setChanged();
    }

    @Nullable
    public net.minecraft.server.level.ServerPlayer resolveOwnerPlayer(ServerLevel level, BlockPos pos) {
        if (cachedOwner != null && !cachedOwner.isRemoved()) {
            return cachedOwner;
        }
        if (owner != null) {
            if (level.getPlayerByUUID(owner) instanceof net.minecraft.server.level.ServerPlayer sp) return sp;
            net.minecraft.server.level.ServerPlayer sp = level.getServer().getPlayerList().getPlayer(owner);
            if (sp != null) return sp;
        }
        return null;
    }

    public SluiceBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SLUICE_BOX.get(), pos, state);
    }

    /** True once the contents were handed back, so removing the block drops nothing more. */
    boolean refunded() {
        return refunded;
    }

    void markRefunded() {
        refunded = true;
    }

    /** A long sluice block entity read from a save without the layout marker: the one-block Tier 2 sluice of an old world. */
    public boolean isOldLayout() {
        return layout != LAYOUT && getBlockState().getBlock() instanceof LongSluiceBlock;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl && isOldLayout()) {
            sl.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Tick (WORKING only)
    // ------------------------------------------------------------------------------------------------

    /** Server tick while the sluice is working (see {@link SluiceBoxBlock#getTicker}). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, SluiceBoxBlockEntity sluice) {
        if (++sluice.progress < SluiceBoxBlock.stats(state).cycleTicks()) {
            level.blockEntityChanged(pos);
            return;
        }
        sluice.progress = 0;
        sluice.washOneClump((ServerLevel) level, pos);
        if (state.getBlock() instanceof SluiceBoxBlock block) {
            block.refreshStatus(level, pos, level.getBlockState(pos));
        }
    }

    /**
     * One clump through the riffles with the same loot roll as panning by hand, putting results into the tray. An installed upgrade
     * gives clumps of its own kind a chance of a free second wash, along with any bonus rolls. Public for tests.
     */
    public void washOneClump(ServerLevel level, BlockPos pos) {
        if (queue.isEmpty()) {
            return;
        }
        int tier = queue.remove(0);
        net.minecraft.server.level.ServerPlayer ownerSp = resolveOwnerPlayer(level, pos);
        if (ownerSp != null) {
            com.holeydigsite.ledger.LedgerManager.recordSluice(ownerSp, tier, 1);
        }
        List<ItemStack> loot = new ArrayList<>(Panning.rollScoop(level, pos, null, tier));
        for (SluiceUpgrade upgrade : inserts()) {
            if (upgrade.appliesTo(tier)) {
                if (upgrade.extraChance() > 0 && level.random.nextDouble() < upgrade.extraChance()) {
                    loot.addAll(Panning.rollScoop(level, pos, null, tier));
                }
                if (upgrade.bonusTable(tier) != null) {
                    loot.addAll(Panning.rollTable(level, pos, null, upgrade.bonusTable(tier)));
                }
            }
        }
        ItemStack crate = com.holeydigsite.crate.CrateFinds.sluiceFind(level, pos, ownerSp);
        if (!crate.isEmpty()) {
            loot.add(crate);
        }
        for (ItemStack stack : loot) {
            addToTray(stack);
        }
        if (!loot.isEmpty()) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5F, 1.2F + level.random.nextFloat() * 0.3F);
        }
        if (loot.stream().anyMatch(s -> s.is(Panning.RARE_FINDS))) {
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.4F, 1.5F);
        }
        changed();
    }

    // ------------------------------------------------------------------------------------------------
    // Queue
    // ------------------------------------------------------------------------------------------------

    public int queued() {
        return queue.size();
    }

    /** Tiers of the queued clumps, oldest first (read-only, for tests). */
    public List<Integer> queuedTiers() {
        return Collections.unmodifiableList(queue);
    }

    /** The inserts in this sluice (one for the Starter sluice, up to two for the long one). */
    public List<SluiceUpgrade> inserts() {
        return getBlockState().getBlock() instanceof SluiceBoxBlock b ? b.inserts(getBlockState()) : List.of();
    }

    /** This sluice's speed and sizes (Tier 1 or 2, from its block). */
    public SluiceBoxBlock.Stats stats() {
        return SluiceBoxBlock.stats(getBlockState());
    }

    public boolean hasQueueRoom() {
        return queue.size() < stats().maxQueue();
    }

    /** Adds up to {@code max} tier 1 clumps to the queue; returns how many went in. */
    public int addClumps(int max) {
        return addClumps(max, 1);
    }

    /** Adds up to {@code max} clumps of {@code tier} to the queue; returns how many went in. */
    public int addClumps(int max, int tier) {
        int n = Math.max(0, Math.min(max, stats().maxQueue() - queue.size()));
        for (int i = 0; i < n; i++) {
            queue.add(DirtTier.clamp(tier));
        }
        if (n > 0) {
            changed();
        }
        return n;
    }

    /** The queued clumps as item stacks (one per tier), e.g. to drop when the sluice is broken. */
    public List<ItemStack> queuedAsItems() {
        int[] perTier = new int[DirtTier.MAX + 1];
        for (int t : queue) {
            perTier[t]++;
        }
        List<ItemStack> out = new ArrayList<>();
        for (int t = DirtTier.MIN; t <= DirtTier.MAX; t++) {
            if (perTier[t] > 0) {
                out.add(DirtTier.clumps(t, perTier[t]));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // Tray
    // ------------------------------------------------------------------------------------------------

    /** The tray stacks (read-only view, for the renderer and tests). */
    public List<ItemStack> tray() {
        return Collections.unmodifiableList(tray);
    }

    public boolean isTrayFull() {
        return tray.size() >= stats().traySlots();
    }

    /** Merges into matching stacks first, then starts new stacks. Public for tests. */
    public void addToTray(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemStack s : tray) {
            if (rest.isEmpty()) {
                break;
            }
            if (ItemStack.isSameItemSameComponents(s, rest) && s.getCount() < s.getMaxStackSize()) {
                int move = Math.min(rest.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(move);
                rest.shrink(move);
            }
        }
        while (!rest.isEmpty()) {
            tray.add(rest.split(rest.getMaxStackSize()));
        }
        changed();
    }

    /**
     * Moves tray items onto a running belt in front of the tray, one item at a time while the belt has room.
     * Returns true if the tray still holds something and a belt is there.
     */
    public boolean pushTrayToBelt(Level level, BlockPos pos, BlockState state) {
        if (tray.isEmpty()) {
            return false;
        }
        net.minecraft.core.Direction facing = state.getValue(SluiceBoxBlock.FACING);
        BlockPos front = state.getBlock() instanceof SluiceBoxBlock b ? b.trayOutputPos(pos, state) : pos.relative(facing);
        BlockState b = level.getBlockState(front);
        if (!(b.getBlock() instanceof com.holeydigsite.belt.ClumpBeltBlock)
                || !(level.getBlockEntity(front) instanceof com.holeydigsite.belt.ClumpBeltBlockEntity belt)
                || com.holeydigsite.plot.PlotLock.isLocked(level, front)) {
            return false;
        }
        boolean movedAny = false;
        while (!tray.isEmpty() && belt.canAccept(b, facing.getOpposite())) {
            ItemStack first = tray.get(0);
            belt.accept(facing.getOpposite(), level.random.nextInt(256), first.copyWithCount(1));
            first.shrink(1);
            if (first.isEmpty()) {
                tray.remove(0);
            }
            movedAny = true;
        }
        if (movedAny) {
            changed();
        }
        return !tray.isEmpty();
    }

    /** Empties the queue (for the old-sluice refund, after {@link #queuedAsItems}). */
    void clearQueue() {
        if (!queue.isEmpty()) {
            queue.clear();
            changed();
        }
    }

    /** Takes everything out of the tray. */
    public List<ItemStack> takeTray() {
        List<ItemStack> out = new ArrayList<>(tray);
        if (!tray.isEmpty()) {
            tray.clear();
            changed();
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // Power / progress
    // ------------------------------------------------------------------------------------------------

    public boolean isWaterPowered() {
        return waterPowered;
    }

    void setWaterPowered(boolean powered) {
        if (waterPowered != powered) {
            waterPowered = powered;
            setChanged();
        }
    }

    public int progress() {
        return progress;
    }

    void resetProgress() {
        progress = 0;
    }

    /** Saves and sends the tray and queue to clients on real changes. */
    private void changed() {
        if (level != null && !level.isClientSide) {
            level.blockEntityChanged(worldPosition);
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Save / sync
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("WaterPowered", waterPowered);
        tag.putInt("Progress", progress);
        tag.putInt("Layout", LAYOUT);
        if (owner != null) tag.putUUID("Owner", owner);
        if (!skin.isEmpty()) tag.putString("Skin", skin);
        if (!skin.isEmpty() && !wear.isEmpty()) tag.putString("SkinWear", wear);
        writeContents(tag, registries);
    }

    private void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("Queue", queue.stream().mapToInt(Integer::intValue).toArray());
        ListTag list = new ListTag();
        for (ItemStack s : tray) {
            if (!s.isEmpty()) {
                list.add(s.save(registries));
            }
        }
        tag.put("Tray", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        layout = tag.getInt("Layout");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (tag.contains("WaterPowered")) {
            waterPowered = tag.getBoolean("WaterPowered");
        }
        if (tag.contains("Progress")) {
            progress = Math.clamp(tag.getInt("Progress"), 0, stats().cycleTicks());
        }
        queue.clear();
        for (int t : tag.getIntArray("Queue")) {
            if (queue.size() < stats().maxQueue()) {
                queue.add(DirtTier.clamp(t));
            }
        }
        tray.clear();
        for (Tag t : tag.getList("Tray", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(tray::add);
        }
        skin = tag.getString("Skin");
        wear = tag.getString("SkinWear");
    }

    /** Clients get the queue and the tray for the renderer, rather than power or progress. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Layout", LAYOUT);
        if (!skin.isEmpty()) tag.putString("Skin", skin);
        if (!skin.isEmpty() && !wear.isEmpty()) tag.putString("SkinWear", wear);
        writeContents(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
