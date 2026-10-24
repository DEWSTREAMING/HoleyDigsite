package com.holeydigsite.drill;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * State of a Mining Drill: cached water power (pushed in by the network), drilling progress and how long the
 * output has been blocked. No inventory - a finished clump waits "in the bit" until the output has room.
 */
public class MiningDrillBlockEntity extends BlockEntity {
    /** Cached result of the water network. Only changed by {@link com.holeydigsite.power.WaterNetworks}. */
    private boolean waterPowered;
    /** Ticks drilled towards the next clump, 0..CYCLE_TICKS. At CYCLE_TICKS a clump is ready and waits. */
    private int progress;
    /** Game time the output was first seen blocked, or -1. Not saved: a reload simply restarts the 3 s grace. */
    private long blockedSince = -1;

    public MiningDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINING_DRILL.get(), pos, state);
    }

    /**
     * Server tick - only runs while the drill is WORKING (see {@link MiningDrillBlock#getTicker}).
     * Per tick: one increment. Every {@link MiningDrillBlock#CHECK_INTERVAL} ticks (and when a clump is ready):
     * one look at the block in front.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, MiningDrillBlockEntity drill) {
        long now = level.getGameTime();
        int cycle = MiningDrillBlock.cycleTicks(state);
        boolean justReady = false;
        if (drill.progress < cycle) {
            drill.progress++;
            justReady = drill.progress == cycle;
        }
        if (!justReady && now % MiningDrillBlock.CHECK_INTERVAL != 0) {
            return;
        }

        Direction facing = state.getValue(MiningDrillBlock.FACING);
        BlockPos front = pos.relative(facing);
        Direction from = facing.getOpposite();
        boolean ready = drill.progress >= cycle;
        int tier = MiningDrillBlock.clumpTier(level, pos);
        boolean ok = ready ? ClumpOutputs.tryOutput(level, front, from, tier) : ClumpOutputs.canOutput(level, front, from, tier);
        level.blockEntityChanged(pos); // mark the chunk for saving (cheaper than setChanged's comparator update)

        if (ok) {
            drill.blockedSince = -1;
            if (ready) {
                drill.progress = 0;
            }
        } else if (drill.blockedSince < 0) {
            drill.blockedSince = now;
        } else if (now - drill.blockedSince > MiningDrillBlock.STOP_AFTER_BLOCKED_TICKS
                && state.getBlock() instanceof MiningDrillBlock block) {
            block.setStatus(level, pos, state, drill, DrillStatus.STOPPED);
        }
    }

    public boolean isWaterPowered() {
        return waterPowered;
    }

    void setWaterPowered(boolean powered) {
        if (waterPowered != powered) {
            waterPowered = powered;
            setChanged();
        }
    }

    public int getProgress() {
        return progress;
    }

    /** True while the output is blocked but the 3 s grace period hasn't run out yet. */
    public boolean isOutputBlocked() {
        return blockedSince >= 0;
    }

    void clearBlocked() {
        blockedSince = -1;
    }

    // ---- save/load ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("WaterPowered", waterPowered);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        waterPowered = tag.getBoolean("WaterPowered");
        progress = Math.clamp(tag.getInt("Progress"), 0, MiningDrillBlock.cycleTicks(getBlockState()));
    }
}
