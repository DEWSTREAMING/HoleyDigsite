package com.holeydigsite.forge;

import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Contents of a Crafting Forge:
 * <ul>
 *   <li>{@link #pool()}: ingots melted into pooled grams per metal;</li>
 *   <li>{@link #parts()}: recipe parts as items, at most {@link #PART_SLOTS} stacks;</li>
 *   <li>the selected recipe (cycled by the player);</li>
 *   <li>{@link #castTicks()}: progress of a running cast (5 seconds);</li>
 *   <li>{@link #output()}: after a cast, the product, one leftover ingot per metal with exactly the leftover grams, and every
 *       unused part. While output is waiting nothing can be loaded and nothing cast.</li>
 * </ul>
 * Nothing is ever destroyed except what a completed recipe consumes.
 * Ticks only while casting.
 */
public class CraftingForgeBlockEntity extends BlockEntity {
    public static final int PART_SLOTS = 9;
    public static final int CAST_TICKS = 100;

    private final TreeMap<ResourceLocation, Integer> pool = new TreeMap<>();
    private final List<ItemStack> parts = new ArrayList<>();
    private final List<ItemStack> output = new ArrayList<>();
    @Nullable
    private ResourceLocation selected;
    private int castTicks;
    private int clientVersion;

    public CraftingForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRAFTING_FORGE.get(), pos, state);
    }

    // ------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------

    public Map<ResourceLocation, Integer> pool() {
        return Collections.unmodifiableMap(pool);
    }

    public int grams(ResourceLocation metal) {
        return pool.getOrDefault(metal, 0);
    }

    public List<ItemStack> parts() {
        return Collections.unmodifiableList(parts);
    }

    public int partCount(Item item) {
        int n = 0;
        for (ItemStack s : parts) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    public List<ItemStack> output() {
        return Collections.unmodifiableList(output);
    }

    public boolean hasOutput() {
        return !output.isEmpty();
    }

    public boolean isCasting() {
        return castTicks > 0;
    }

    public int castTicks() {
        return castTicks;
    }

    public int clientVersion() {
        return clientVersion;
    }

    public boolean isEmpty() {
        return pool.isEmpty() && parts.isEmpty() && output.isEmpty();
    }

    // ------------------------------------------------------------------------------------------------
    // Recipe selection
    // ------------------------------------------------------------------------------------------------

    @Nullable
    public ForgeRecipes.Entry recipe() {
        List<ForgeRecipes.Entry> all = ForgeRecipes.all(level == null ? null : level.registryAccess());
        if (all.isEmpty()) {
            return null;
        }
        for (ForgeRecipes.Entry e : all) {
            if (e.id().equals(selected)) {
                return e;
            }
        }
        return all.get(0);
    }

    @Nullable
    public ForgeRecipes.Entry cycle(int step) {
        List<ForgeRecipes.Entry> all = ForgeRecipes.all(level == null ? null : level.registryAccess());
        if (all.isEmpty()) {
            return null;
        }
        ForgeRecipes.Entry current = recipe();
        int i = current == null ? 0 : all.indexOf(current);
        ForgeRecipes.Entry next = all.get(Math.floorMod(i + step, all.size()));
        selected = next.id();
        changed();
        return next;
    }

    public void select(ResourceLocation id) {
        selected = id;
        changed();
    }

    // ------------------------------------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------------------------------------

    public enum Refusal { NONE, CASTING, OUTPUT, CHUNK, NOT_ACCEPTED, DIRTY, FULL }

    @Nullable
    public static ResourceLocation ingotMetal(ItemStack stack) {
        if (!(stack.getItem() instanceof IngotItem)) {
            return null;
        }
        ResourceInfo info = Metals.meltable(stack);
        return info == null ? null : info.material();
    }

    public Refusal check(ItemStack stack) {
        if (stack.isEmpty()) {
            return Refusal.NOT_ACCEPTED;
        }
        if (isCasting()) {
            return Refusal.CASTING;
        }
        if (hasOutput()) {
            return Refusal.OUTPUT;
        }
        if (ingotMetal(stack) != null) {
            return Refusal.NONE;
        }
        if (Metals.meltable(stack) != null) {
            return Refusal.CHUNK;
        }
        if (!ForgeRecipes.isPart(level == null ? null : level.registryAccess(), stack.getItem())) {
            return Refusal.NOT_ACCEPTED;
        }
        if (!stack.getComponentsPatch().isEmpty()) {
            return Refusal.DIRTY;
        }
        return partRoom(stack) > 0 ? Refusal.NONE : Refusal.FULL;
    }

    private int partRoom(ItemStack stack) {
        int room = 0;
        for (ItemStack s : parts) {
            if (ItemStack.isSameItemSameComponents(s, stack)) {
                room += s.getMaxStackSize() - s.getCount();
            }
        }
        return room + (PART_SLOTS - parts.size()) * stack.getMaxStackSize();
    }

    public int load(ItemStack stack, int max, boolean simulate) {
        if (check(stack) != Refusal.NONE || max <= 0) {
            return 0;
        }
        ResourceLocation metal = ingotMetal(stack);
        if (metal != null) {
            int n = Math.min(max, stack.getCount());
            if (!simulate) {
                pool.merge(metal, n * Selling.grams(stack), Integer::sum);
                changed();
            }
            return n;
        }
        int n = Math.min(Math.min(max, stack.getCount()), partRoom(stack));
        if (n <= 0 || simulate) {
            return Math.max(n, 0);
        }
        int left = n;
        for (ItemStack s : parts) {
            if (left > 0 && ItemStack.isSameItemSameComponents(s, stack)) {
                int move = Math.min(left, s.getMaxStackSize() - s.getCount());
                s.grow(move);
                left -= move;
            }
        }
        while (left > 0 && parts.size() < PART_SLOTS) {
            int move = Math.min(left, stack.getMaxStackSize());
            parts.add(stack.copyWithCount(move));
            left -= move;
        }
        changed();
        return n;
    }

    // ------------------------------------------------------------------------------------------------
    // Requirements
    // ------------------------------------------------------------------------------------------------

    public record Missing(@Nullable ResourceLocation metal, @Nullable Item part, int amount) {}

    @Nullable
    public Missing missing(ForgeRecipe recipe) {
        for (Map.Entry<ResourceLocation, Integer> m : recipe.metals().entrySet()) {
            int have = grams(m.getKey());
            if (have < m.getValue()) {
                return new Missing(m.getKey(), null, m.getValue() - have);
            }
        }
        for (ForgeRecipe.Part p : recipe.parts()) {
            int need = recipe.partCount(p.item());
            int have = partCount(p.item());
            if (have < need) {
                return new Missing(null, p.item(), need - have);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------
    // Casting
    // ------------------------------------------------------------------------------------------------

    void startCast() {
        castTicks = 1;
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CraftingForgeBlockEntity forge) {
        if (forge.castTicks <= 0) {
            return;
        }
        forge.castTicks++;
        if (level instanceof ServerLevel sl && forge.castTicks % 10 == 0) {
            sl.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6, 0.25, 0.1, 0.25, 0.1);
            if (forge.castTicks % 30 == 0) {
                level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4F, 1.2F + level.random.nextFloat() * 0.3F);
            }
        }
        if (forge.castTicks >= CAST_TICKS) {
            forge.finishCast(level, pos);
        } else {
            level.blockEntityChanged(pos);
        }
    }

    void finishCast(Level level, BlockPos pos) {
        castTicks = 0;
        ForgeRecipes.Entry entry = recipe();
        List<ItemStack> out = new ArrayList<>();
        if (entry != null && missing(entry.recipe()) == null) {
            ForgeRecipe r = entry.recipe();
            for (Map.Entry<ResourceLocation, Integer> m : r.metals().entrySet()) {
                pool.merge(m.getKey(), -m.getValue(), Integer::sum);
            }
            for (ForgeRecipe.Part p : r.parts()) {
                removeParts(p.item(), p.count());
            }
            out.add(r.resultStack());
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.0F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.02);
        }
        out.addAll(drainContents());
        output.addAll(out);
        changed();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CraftingForgeBlock) {
            level.setBlock(pos, state.setValue(CraftingForgeBlock.CASTING, false), Block.UPDATE_CLIENTS);
        }
    }

    private void removeParts(Item item, int count) {
        int left = count;
        for (int i = parts.size() - 1; i >= 0 && left > 0; i--) {
            ItemStack s = parts.get(i);
            if (s.is(item)) {
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
                if (s.isEmpty()) {
                    parts.remove(i);
                }
            }
        }
    }

    private List<ItemStack> drainContents() {
        List<ItemStack> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> m : pool.entrySet()) {
            if (m.getValue() > 0) {
                ItemStack ingot = Metals.ingot(m.getKey(), m.getValue());
                if (!ingot.isEmpty()) {
                    out.add(ingot);
                }
            }
        }
        pool.clear();
        for (ItemStack s : parts) {
            if (!s.isEmpty()) {
                out.add(s.copy());
            }
        }
        parts.clear();
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // Taking things out
    // ------------------------------------------------------------------------------------------------

    public List<ItemStack> takeOutput() {
        List<ItemStack> out = new ArrayList<>(output);
        if (!output.isEmpty()) {
            output.clear();
            changed();
        }
        return out;
    }

    public List<ItemStack> takeBack() {
        if (isCasting()) {
            return List.of();
        }
        List<ItemStack> out = drainContents();
        if (!out.isEmpty()) {
            changed();
        }
        return out;
    }

    public List<ItemStack> dropContents() {
        castTicks = 0;
        List<ItemStack> out = new ArrayList<>(output);
        output.clear();
        out.addAll(drainContents());
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // Sync / save
    // ------------------------------------------------------------------------------------------------

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag p = new CompoundTag();
        pool.forEach((k, v) -> p.putInt(k.toString(), v));
        tag.put("Pool", p);
        tag.put("Parts", saveList(parts, registries));
        tag.put("Output", saveList(output, registries));
        if (selected != null) {
            tag.putString("Recipe", selected.toString());
        }
        tag.putInt("CastTicks", castTicks);
    }

    private static ListTag saveList(List<ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                list.add(s.save(registries));
            }
        }
        return list;
    }

    private void readContents(CompoundTag tag, HolderLookup.Provider registries) {
        pool.clear();
        CompoundTag p = tag.getCompound("Pool");
        for (String k : p.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(k);
            if (id != null && p.getInt(k) > 0) {
                pool.put(id, p.getInt(k));
            }
        }
        parts.clear();
        for (Tag t : tag.getList("Parts", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(parts::add);
        }
        output.clear();
        for (Tag t : tag.getList("Output", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(output::add);
        }
        selected = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        castTicks = Math.clamp(tag.getInt("CastTicks"), 0, CAST_TICKS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeContents(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readContents(tag, registries);
        clientVersion++;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeContents(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
