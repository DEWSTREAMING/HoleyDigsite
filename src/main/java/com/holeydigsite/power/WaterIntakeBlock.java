package com.holeydigsite.power;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Water Intake: the power source of a water network. It is ACTIVE while it sits in water, i.e. while it is
 * waterlogged. Like the pan, it waterlogs when placed in any water, source or flowing.
 *
 * <p>Active/inactive is simply the {@link #WATERLOGGED} state, so the model shows it for free
 * (blockstate variants) and no extra state or ticking is needed. Gaining or losing water (bucket, placing it,
 * breaking it) marks the network dirty.</p>
 *
 * <p>Visual connections: six booleans (same names as the pipe's, from vanilla {@link PipeBlock}) say which faces a
 * pipe is attached to, using the shared {@link WaterNetworkBlock#connects} rule. The multipart model adds a collar on
 * each connected side and on top, reaching from the intake's casing to the block edge, so a pipe joins it with no
 * gap. Visual only - the network never reads them, and changing them never marks the network dirty.</p>
 */
public class WaterIntakeBlock extends Block implements SimpleWaterloggedBlock, WaterNetworkBlock {
    public static final MapCodec<WaterIntakeBlock> CODEC = simpleCodec(WaterIntakeBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** 14x14 footprint, 14 tall: water around the edges stays visible. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public WaterIntakeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(WATERLOGGED, false);
        for (BooleanProperty side : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    /** Sets each side's collar boolean from the shared connection rule. */
    private static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), WaterNetworkBlock.connects(level, pos, state, dir));
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ---- water ----

    /** Waterlogged in ANY water, source or flowing (same rule as the pan). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        BlockState state = defaultBlockState().setValue(WATERLOGGED, fluid.is(FluidTags.WATER));
        return withConnections(context.getLevel(), context.getClickedPos(), state);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                WaterNetworkBlock.connects(level, pos, state, direction));
    }

    // ---- network ----

    @Override
    public Role waterRole() {
        return Role.INTAKE;
    }

    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return true;
    }

    @Override
    public boolean isActiveIntake(BlockState state) {
        return state.getValue(WATERLOGGED);
    }

    /** Placed, or water added/removed (bucket): recompute. Collar (connection) changes don't touch the network. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) || oldState.getValue(WATERLOGGED) != state.getValue(WATERLOGGED)) {
            WaterNetworks.markDirty(level, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this)) {
            WaterNetworks.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.water_intake").withStyle(ChatFormatting.GRAY));
    }
}
