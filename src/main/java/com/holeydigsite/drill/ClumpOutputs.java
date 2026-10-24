package com.holeydigsite.drill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ONE place that decides where a drill may put a Dirt Clump.
 *
 * <p>Any {@link com.holeydigsite.transport.ItemTarget} (Phase 12; clump-only ones via {@link DirtClumpTarget}): the pan, the Clump Belt (Phase 5) and the Sluice Box (Phase 6,
 * from its back only). Belts use this too for anything that isn't a pan or another belt. A later "chest output" upgrade would add a branch here (e.g. wrap
 * an item-handler capability) gated on the drill having that upgrade - nothing else in the drill needs to change.</p>
 *
 * <p>Clumps are never dropped into the world and never handed to hoppers/chests/other mods.</p>
 */
public final class ClumpOutputs {
    private ClumpOutputs() {}

    /** True if a clump of {@code tier} can go into the block at {@code target} right now ({@code from}: side facing the drill). */
    public static boolean canOutput(Level level, BlockPos target, Direction from, int tier) {
        return com.holeydigsite.transport.ItemOutputs.canOutput(level, target, from, com.holeydigsite.tier.DirtTier.clumps(tier, 1));
    }

    /** Puts one clump into the target if it can take it. Returns true if it did. */
    public static boolean tryOutput(Level level, BlockPos target, Direction from, int tier) {
        boolean ok = com.holeydigsite.transport.ItemOutputs.tryOutput(level, target, from, com.holeydigsite.tier.DirtTier.clumps(tier, 1));
        if (ok && level.getBlockEntity(target) instanceof com.holeydigsite.belt.ClumpBeltBlockEntity belt) {
            belt.markNewestFromDrill();
        }
        return ok;
    }
}
