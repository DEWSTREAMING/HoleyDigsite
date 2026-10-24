package com.holeydigsite.forge;

import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Contents of a Crucible. Holds one metal at a time in two buffers up to {@link #CAPACITY} grams,
 * {@link #unmelted()} (solid chunks waiting) and {@link #molten()} (ready to pour). While the Forge below is lit, unmelted
 * grams turn molten at one gram every {@link #TICKS_PER_GRAM} ticks (1 s per 5 g, constant). When both buffers are empty
 * the crucible forgets its metal and accepts any meltable again.
 *
 * <p>Ticks only while melting (the block's ticker exists only for {@code chunks=true, heated=true}). Clients get the
 * numbers for the floating text every time a gram melts and on every other change.</p>
 */
public class CrucibleBlockEntity extends BlockEntity {
    public static final int CAPACITY = com.holeydigsite.tier.Tiers.stat("crucible", "capacity");
    /** 4 ticks per gram is 1 second per 5 g. A full 200 g crucible melts in 40 s. */
    public static final int TICKS_PER_GRAM = com.holeydigsite.tier.Tiers.stat("crucible", "ticks_per_gram");
    /** The Large Crucible holds more and melts faster. See {@link CrucibleBlock#capacity()}. */

    @Nullable
    private ResourceLocation metal;
    private int unmelted;
    private int molten;
    private int progress;

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUCIBLE.get(), pos, state);
    }

    @Nullable
    public ResourceLocation metal() {
        return metal;
    }

    public int unmelted() {
        return unmelted;
    }

    public int molten() {
        return molten;
    }

    public int total() {
        return unmelted + molten;
    }

    /** Grams this crucible holds at most (200; 500 for the Large Crucible). */
    public int capacity() {
        return getBlockState().getBlock() instanceof CrucibleBlock b ? b.capacity() : CAPACITY;
    }

    /** Ticks per gram at heat 1 (4; 2 for the Large Crucible). */
    public int ticksPerGram() {
        return getBlockState().getBlock() instanceof CrucibleBlock b ? b.ticksPerGram() : TICKS_PER_GRAM;
    }

    public int room() {
        return capacity() - total();
    }

    // input

    /** Why an item cannot go in, or {@link Refusal#NONE}. */
    public enum Refusal { NONE, NOT_METAL, GEM, OTHER_METAL, FULL }

    /** Checks one item of {@code stack} against the current contents. */
    public Refusal check(ItemStack stack) {
        ResourceInfo info = Selling.resource(stack);
        if (info == null) {
            return Refusal.NOT_METAL;
        }
        if (!info.meltable()) {
            return Refusal.GEM;
        }
        if (metal != null && !metal.equals(info.material())) {
            return Refusal.OTHER_METAL;
        }
        int grams = Selling.grams(stack);
        return grams <= 0 ? Refusal.NOT_METAL : grams > room() ? Refusal.FULL : Refusal.NONE;
    }

    /** How many items of {@code stack} fit (by grams), 0 if it's refused. */
    public int fits(ItemStack stack) {
        if (check(stack) != Refusal.NONE) {
            return 0;
        }
        return Math.min(stack.getCount(), room() / Selling.grams(stack));
    }

    /**
     * Adds up to {@code max} items of {@code stack} (not changing the stack). Returns how many were taken; the caller
     * shrinks its stack. {@code simulate} only counts.
     */
    public int insert(ItemStack stack, int max, boolean simulate) {
        int n = Math.min(max, fits(stack));
        if (n <= 0 || simulate) {
            return Math.max(n, 0);
        }
        ResourceInfo info = Selling.resource(stack);
        metal = info.material();
        unmelted += n * Selling.grams(stack);
        changed(true);
        return n;
    }

    // melting

    /**
     * Server tick while melting. One gram every {@link #ticksPerGram()} ticks at heat 1. A Hot Forge below (heat 2) doubles
     * the speed, multiplying with the Large Crucible's speed.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity crucible) {
        if (crucible.unmelted <= 0) {
            crucible.changed(true); // turns the ticker off (chunks=false)
            return;
        }
        int heat = ForgeBlock.heatAt(level, pos.below());
        if (heat <= 0) {
            return; // the forge just went out; its block update turns our ticker off
        }
        int perGram = crucible.ticksPerGram();
        crucible.progress += heat;
        boolean melted = false;
        while (crucible.progress >= perGram && crucible.unmelted > 0) {
            crucible.progress -= perGram;
            crucible.unmelted--;
            crucible.molten++;
            melted = true;
        }
        if (crucible.unmelted == 0) {
            crucible.progress = 0;
        }
        if (melted) {
            crucible.changed(true);          // state + numbers to clients whenever a gram melts, and when the last gram melts
        } else {
            level.blockEntityChanged(pos);   // just mark the chunk for saving
        }
    }

    // output

    /** Takes up to {@code grams} molten metal out; returns how much was taken. Forgets the metal when empty. */
    public int takeMolten(int grams) {
        int n = Math.max(0, Math.min(grams, molten));
        if (n > 0) {
            molten -= n;
            changed(true);
        }
        return n;
    }

    /** Sets the contents directly (tests, debugging). */
    public void setContents(@Nullable ResourceLocation metal, int unmelted, int molten) {
        this.metal = metal;
        this.unmelted = Math.clamp(unmelted, 0, capacity());
        this.molten = Math.clamp(molten, 0, capacity() - this.unmelted);
        changed(true);
    }

    // sync / visuals

    /** Marks dirty; with {@code sync}, updates the block state visuals (fill, metal, chunks) and tells clients. */
    private void changed(boolean sync) {
        if (total() == 0) {
            metal = null;
            progress = 0;
        }
        setChanged();
        if (!sync || level == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);   // the live state (the cached one can lag behind)
        if (state.getBlock() instanceof CrucibleBlock) {
            BlockState next = CrucibleBlock.withContents(state, this);
            if (next != state) {
                level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
            } else {
                level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            }
            ((CrucibleBlock) state.getBlock()).armAuto(level, worldPosition);
        }
    }

    // save/load

    private void write(CompoundTag tag) {
        if (metal != null) {
            tag.putString("Metal", metal.toString());
        }
        tag.putInt("Unmelted", unmelted);
        tag.putInt("Molten", molten);
    }

    private void read(CompoundTag tag) {
        metal = tag.contains("Metal") ? ResourceLocation.tryParse(tag.getString("Metal")) : null;
        unmelted = Math.clamp(tag.getInt("Unmelted"), 0, capacity());
        molten = Math.clamp(tag.getInt("Molten"), 0, capacity() - unmelted);
        if (unmelted + molten == 0) {
            metal = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        read(tag);
        progress = Math.clamp(tag.getInt("Progress"), 0, ticksPerGram());
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
