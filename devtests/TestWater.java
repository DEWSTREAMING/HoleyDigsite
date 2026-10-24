package com.holeydigsite.devtest;

import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Set;

/**
 * Phase 12: belts only run with water. Test helper: puts a waterlogged (= active) Water Intake right under a belt (unless a
 * network block is already there) and recomputes that network at once, so the belt is powered from this tick on.
 */
public final class TestWater {
    private TestWater() {}

    public static void underBelt(GameTestHelper h, BlockPos rel) {
        if (!(h.getBlockState(rel).getBlock() instanceof ClumpBeltBlock)) {
            return;
        }
        BlockPos below = rel.below();
        if (h.getLevel().getBlockEntity(h.absolutePos(below)) != null) {
            return; // e.g. a hopper under the belt: leave it (that belt stays unpowered)
        }
        if (!(h.getBlockState(below).getBlock() instanceof WaterNetworkBlock)) {
            h.setBlock(below, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        }
        WaterNetworks.recompute(h.getLevel(), Set.of(h.absolutePos(rel)));
    }
}
