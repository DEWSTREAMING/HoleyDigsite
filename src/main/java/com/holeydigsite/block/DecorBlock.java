package com.holeydigsite.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Phase 21b: the simple decorative blocks given out by the Prospector's Ledger (claim flag, ore cart, tool rack, lucky
 * lantern, gem display case, golden pan trophy). One class, one {@code FACING} state; the front faces the player when placed.
 * The collision shape of each kind is given for the NORTH-facing model and rotated for the other directions.
 * The Tool Rack is a wall block: it hangs on the side of the block it was placed against and faces away from that wall.
 */
public class DecorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DecorBlock> CODEC = simpleCodec(p -> new DecorBlock(p, Kind.CLAIM_FLAG));

    /** The six kinds: the NORTH-facing boxes (x1, y1, z1, x2, y2, z2 in 1/16 blocks) and whether it hangs on a wall. */
    public enum Kind {
        CLAIM_FLAG(false, new double[][]{{6, 0, 6, 10, 16, 10}}),
        ORE_CART(false, new double[][]{{0, 0, 2, 16, 10, 14}}),
        TOOL_RACK(true, new double[][]{{0, 1, 12, 16, 15, 16}}),
        LUCKY_LANTERN(false, new double[][]{{5, 0, 5, 11, 15, 11}}),
        GEM_DISPLAY_CASE(false, new double[][]{{1, 0, 1, 15, 16, 15}}),
        GOLDEN_PAN_TROPHY(false, new double[][]{{2, 0, 2, 14, 10, 16}});

        private final boolean wall;
        private final VoxelShape[] shapes = new VoxelShape[4]; // by Direction.get2DDataValue (S, W, N, E)

        Kind(boolean wall, double[][] boxes) {
            this.wall = wall;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                VoxelShape s = Shapes.empty();
                for (double[] b : boxes) {
                    s = Shapes.or(s, rotated(d, b[0], b[1], b[2], b[3], b[4], b[5]));
                }
                shapes[d.get2DDataValue()] = s;
            }
        }

        public boolean isWall() {
            return wall;
        }

        public VoxelShape shape(Direction facing) {
            return shapes[facing.get2DDataValue()];
        }
    }

    /** A box given for the NORTH-facing model, rotated clockwise (seen from above) to {@code facing}. */
    private static VoxelShape rotated(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    private final Kind kind;

    public DecorBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind.shape(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!kind.isWall()) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
        // Wall block: face away from the wall that was clicked; clicking floor/ceiling picks the nearest wall that is there.
        Direction clicked = context.getClickedFace();
        if (clicked.getAxis().isHorizontal()) {
            BlockState s = defaultBlockState().setValue(FACING, clicked);
            return s.canSurvive(context.getLevel(), context.getClickedPos()) ? s : null;
        }
        for (Direction d : context.getNearestLookingDirections()) {
            if (d.getAxis().isHorizontal()) {
                BlockState s = defaultBlockState().setValue(FACING, d.getOpposite());
                if (s.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!kind.isWall()) {
            return true;
        }
        Direction back = state.getValue(FACING).getOpposite();
        BlockPos support = pos.relative(back);
        return level.getBlockState(support).isFaceSturdy(level, support, state.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (kind.isWall() && direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.ledger_decor").withStyle(ChatFormatting.GRAY));
    }
}
