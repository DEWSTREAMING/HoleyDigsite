package com.holeydigsite.forge;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Contents of a Mold: the player-set {@link #target()} weight, and the metal + grams poured in so far. Cooling is the
 * block state {@link MoldBlock#SOLID} plus {@link #coolUntil} (game time), driven by one scheduled tick - no ticking.
 */
public class MoldBlockEntity extends BlockEntity {
    private int target = MoldBlock.DEFAULT_TARGET;
    @Nullable
    private ResourceLocation metal;
    private int grams;
    private long coolUntil;
    @Nullable
    private java.util.UUID owner;
    @Nullable
    private transient net.minecraft.server.level.ServerPlayer cachedOwner;

    @Nullable
    public java.util.UUID owner() { return owner; }

    public void setOwnerPlayer(@Nullable net.minecraft.server.level.ServerPlayer sp) {
        this.cachedOwner = sp;
        this.owner = sp != null ? sp.getUUID() : null;
        setChanged();
    }

    @Nullable
    public net.minecraft.server.level.ServerPlayer resolveOwnerPlayer(net.minecraft.server.level.ServerLevel level) {
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

    public MoldBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOLD.get(), pos, state);
    }

    public int target() {
        return target;
    }

    /** The mold block this entity belongs to (the plain Mold if it isn't placed yet). */
    public MoldBlock block() {
        return getBlockState().getBlock() instanceof MoldBlock b ? b : (MoldBlock) com.holeydigsite.registry.ModBlocks.MOLD.get();
    }

    @Nullable
    public ResourceLocation metal() {
        return metal;
    }

    public int grams() {
        return grams;
    }

    public boolean isEmpty() {
        return grams <= 0;
    }

    /** Grams still missing to reach the target. */
    public int missing() {
        return Math.max(0, target - grams);
    }

    /** Game ticks until the metal is solid (0 when solid or empty). */
    public int coolingTicksLeft() {
        if (level == null || isEmpty() || getBlockState().getValue(MoldBlock.SOLID)) {
            return 0;
        }
        return (int) Math.max(0, coolUntil - level.getGameTime());
    }

    long coolUntil() {
        return coolUntil;
    }

    void setTarget(int target) {
        this.target = target;
        sync();
    }

    /** Adds molten metal (a pour); restarts cooling. */
    public void pour(ResourceLocation metal, int grams) {
        if (grams <= 0 || level == null) {
            return;
        }
        this.metal = metal;
        this.grams += grams;
        int cool = block().coolTicks();
        this.coolUntil = level.getGameTime() + cool;
        BlockState state = level.getBlockState(worldPosition);
        level.setBlock(worldPosition, MoldBlock.withContents(state, this).setValue(MoldBlock.SOLID, false), Block.UPDATE_CLIENTS);
        level.scheduleTick(worldPosition, state.getBlock(), cool);
        sync();
    }

    /** Removes the (cooled) ingot and empties the mold; returns it (EMPTY if the material has no ingot item). */
    public ItemStack takeIngot() {
        ItemStack ingot = metal == null ? ItemStack.EMPTY : Metals.ingot(metal, grams);
        metal = null;
        grams = 0;
        if (level != null && !level.isClientSide) {
            level.setBlock(worldPosition, MoldBlock.withContents(level.getBlockState(worldPosition), this).setValue(MoldBlock.SOLID, false), Block.UPDATE_CLIENTS);
        }
        sync();
        return ingot;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- save/load ----

    private void write(CompoundTag tag) {
        tag.putInt("Target", target);
        if (metal != null) {
            tag.putString("Metal", metal.toString());
        }
        tag.putInt("Grams", grams);
    }

    private void read(CompoundTag tag) {
        target = tag.contains("Target") ? block().validTargetFor(tag.getInt("Target")) : MoldBlock.DEFAULT_TARGET;
        metal = tag.contains("Metal") ? ResourceLocation.tryParse(tag.getString("Metal")) : null;
        grams = Math.max(0, tag.getInt("Grams"));
        if (metal == null) {
            grams = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag);
        tag.putLong("CoolUntil", coolUntil);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        read(tag);
        coolUntil = tag.getLong("CoolUntil");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
