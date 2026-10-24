package com.holeydigsite.drill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block a Mining Drill may output Dirt Clumps into. Implemented by our blocks only, such as
 * the pan, the Clump Belt, and the Sluice Box. Hoppers, chests, and other storage are deliberately
 * not targets (see {@link ClumpOutputs}).
 */
public interface DirtClumpTarget extends com.holeydigsite.transport.ItemTarget {
    /** As an item target, clump-only blocks take any clump (Dirt, Gravel, or Sand) their own rules allow. */
    @Override
    default boolean canAcceptItem(Level level, BlockPos pos, BlockState state, Direction from, net.minecraft.world.item.ItemStack one) {
        return com.holeydigsite.tier.DirtTier.isClump(one)
                && canAcceptClump(level, pos, state, from, com.holeydigsite.tier.DirtTier.ofClump(one));
    }

    @Override
    default void acceptItem(Level level, BlockPos pos, BlockState state, Direction from, net.minecraft.world.item.ItemStack one) {
        acceptClump(level, pos, state, from, com.holeydigsite.tier.DirtTier.ofClump(one));
    }

    /**
     * Checks if one Dirt Clump can go into this block right now.
     *
     * @param from the side of this block the clump arrives from
     * @param tier the clump's dirt tier
     */
    boolean canAcceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier);

    /** Insert one clump. Only called right after {@link #canAcceptClump} returned true in the same tick. */
    void acceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier);
}
