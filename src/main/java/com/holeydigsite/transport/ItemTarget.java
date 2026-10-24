package com.holeydigsite.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase 12: a block that belts (and drills, sluice trays) may deliver ONE item into. Implemented by OUR blocks only:
 * belts (all kinds), pans and the Sluice Box (clumps, via {@link com.holeydigsite.drill.DirtClumpTarget}), the Crucible
 * (meltables), the Crafting Forge (ingots and parts) and the Sell Pad (sellable items). Hoppers, chests and other mods'
 * storage are deliberately not targets (see {@link ItemOutputs}).
 */
public interface ItemTarget {
    /**
     * Can this one item go in right now?
     *
     * @param from side of THIS block the item arrives from ({@link Direction#DOWN} = the low end of an Inclined Belt)
     * @param one  a single item (count 1); not modified
     */
    boolean canAcceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one);

    /** Puts the item in. Only called right after {@link #canAcceptItem} returned true in the same tick. */
    void acceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one);
}
