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
 * Phase 12 Inclined Belt (and Fast): a ramp that lifts items ONE block. Its low end (back edge, at the bottom of this
 * block) takes items from the flat belt one block lower behind it ({@link Direction#DOWN} entry), or straight in at its
 * back (a drill, a sluice tray, a player); its high end (front edge, at the top of this block) hands over to the belt /
 * target in front at the same level - or to the next incline one up, so inclines stack into a staircase. Up only (a
 * down-slope isn't part of Phase 12). Water-powered like flat belts; inclines link their water diagonally.
 */
public class InclineBeltBlock extends ClumpBeltBlock {
    public static final MapCodec<InclineBeltBlock> CODEC = simpleCodec(p -> new InclineBeltBlock(p, 1));

    /** Collision / outline: 4 stairs following the 45 degree ramp (Phase 12.1), per facing. Front = high end. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction f : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (int i = 0; i < 4; i++) {
                // step i covers 4 px of depth measured from the FRONT edge, height 16 - 4i
                double a = 4 * i, b = 4 * i + 4, h = 16 - 4 * i;
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

    public InclineBeltBlock(Properties properties, int speed) {
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

    /** Height of the ramp surface at {@code along} (0 = back edge, 1 = front edge), in blocks above the block's bottom. */
    public static float surfaceAt(float along) {
        return Math.clamp(along, 0F, 1F);
    }
}
