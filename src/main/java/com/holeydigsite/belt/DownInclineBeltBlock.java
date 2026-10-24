package com.holeydigsite.belt;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Phase 21i Down Inclined Belt (and Fast): a ramp that lowers items ONE block. Its high end (back edge, at the top of this
 * block) takes items from the flat belt or machine behind it at the same level (or from a down incline one higher behind it);
 * its low end (front edge, at the bottom of this block) hands over to the belt / target one block lower in front. Stacks
 * into a staircase of down inclines. Water-powered like flat belts; down inclines link their water diagonally to the low
 * end in front.
 */
public class DownInclineBeltBlock extends ClumpBeltBlock {
    public static final MapCodec<DownInclineBeltBlock> CODEC = simpleCodec(p -> new DownInclineBeltBlock(p, 1));

    /** Collision / outline: 4 stairs following the 45 degree ramp downwards, per facing. Front = low end. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction f : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (int i = 0; i < 4; i++) {
                // step i covers 4 px of depth measured from the FRONT edge (a to b). Height is 4*(i+1): 4 at front edge, 16 at back edge.
                double a = 4 * i, b = 4 * i + 4, h = 4 * (i + 1);
                VoxelShape step = switch (f) {
                    case NORTH -> Block.box(0, 0, a, 16, h, b);
                    case SOUTH -> Block.box(0, 0, 16 - b, 16, h, 16 - a);
                    case WEST -> Block.box(a, 0, 0, b, h, 16);
                    default -> Block.box(16 - b, 0, 0, 16 - a, h, 16);
                };
                shape = Shapes.or(shape, step);
            }
            SHAPES.put(f, shape.optimize());
        }
    }

    public DownInclineBeltBlock(Properties properties, int speed) {
        super(properties, speed);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        addCommon(builder);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Height of the ramp surface at {@code along} (0 = back edge (high), 1 = front edge (low)), in blocks above the block's bottom. */
    public static float surfaceAt(float along) {
        return Math.clamp(1.0F - along, 0F, 1F);
    }
}
