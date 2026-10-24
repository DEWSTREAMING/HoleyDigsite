package com.holeydigsite.transport;

import com.holeydigsite.plot.PlotLock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether an item may be pushed into a block, extending previous clump output logic.
 * Only {@link ItemTarget} blocks take anything, and nothing reaches into a locked land plot. Items are never
 * dropped into the world and never handed to hoppers, chests or other mods.
 */
public final class ItemOutputs {
    private ItemOutputs() {}

    public static boolean canOutput(Level level, BlockPos target, Direction from, ItemStack one) {
        if (one.isEmpty() || PlotLock.isLocked(level, target)) {
            return false;
        }
        BlockState state = level.getBlockState(target);
        return state.getBlock() instanceof ItemTarget t && t.canAcceptItem(level, target, state, from, one);
    }

    /** Puts one item (a copy of {@code one} with count 1) into the target if it can take it. Returns true if it did. */
    public static boolean tryOutput(Level level, BlockPos target, Direction from, ItemStack one) {
        if (!canOutput(level, target, from, one)) {
            return false;
        }
        BlockState state = level.getBlockState(target);
        ((ItemTarget) state.getBlock()).acceptItem(level, target, state, from, one.copyWithCount(1));
        return true;
    }
}
