package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.DecorBlock;
import com.holeydigsite.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Golden Drill Statue as a small derrick: wide plinth, narrow frame up to the top of the block, spout on the front. */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase35cTests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static VoxelShape shape(GameTestHelper h, Direction facing) {
        BlockState st = ModBlocks.GOLDEN_DRILL_STATUE.get().defaultBlockState().setValue(DecorBlock.FACING, facing);
        return st.getShape(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)));
    }

    private static boolean solidAt(VoxelShape s, double x, double y, double z) {
        for (AABB b : s.toAabbs()) {
            if (b.minX <= x && x <= b.maxX && b.minY <= y && y <= b.maxY && b.minZ <= z && z <= b.maxZ) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = "empty")
    public static void goldenDrillStatueShapeIsPlinthAndDerrick(GameTestHelper h) {
        VoxelShape north = shape(h, Direction.NORTH);
        AABB all = north.bounds();
        check(h, all.minY == 0 && all.maxY == 1.0, "statue stands one block tall: " + all);
        check(h, Math.abs(all.minX - 2 / 16.0) < 1e-6 && Math.abs(all.maxX - 14 / 16.0) < 1e-6, "plinth is 12 wide: " + all);
        // the derrick is much narrower than the plinth: 1/16 above the corner of the plinth is empty
        check(h, solidAt(north, 2.5 / 16, 1 / 16.0, 2.5 / 16), "plinth corner is solid");
        check(h, !solidAt(north, 3.5 / 16, 10 / 16.0, 3.5 / 16), "air beside the derrick");
        check(h, solidAt(north, 0.5, 15 / 16.0, 0.5), "derrick reaches the top");
        // the spout sticks out on the front, so the shape follows the facing
        check(h, solidAt(north, 0.5, 4 / 16.0, 4.75 / 16), "spout side faces north");
        VoxelShape south = shape(h, Direction.SOUTH);
        check(h, solidAt(south, 0.5, 4 / 16.0, 11.25 / 16) && !solidAt(south, 0.5, 4 / 16.0, 4.75 / 16), "spout side turns to the south");
        h.succeed();
    }
}
