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
import net.minecraft.world.level.pathfinder.PathComputationType;

import java.util.List;

/**
 * Water Pipe: connects (like a fence, but in all 6 directions) to other pipes, intakes and machine water ports.
 *
 * <p>The six connection booleans come from vanilla {@link PipeBlock} (chorus plant), which also builds the
 * outline shapes. They are visual only - the network itself uses {@link WaterNetworkBlock#connects}, the same
 * rule, so what you see is what is connected. Static multipart model, no block entity, never ticks.</p>
 *
 * <p>{@link #FILLED} is a cheap "has water" visual: set by {@link WaterNetworks} when the network is powered.</p>
 */
public class WaterPipeBlock extends PipeBlock implements SimpleWaterloggedBlock, WaterNetworkBlock {
    public static final MapCodec<WaterPipeBlock> CODEC = simpleCodec(WaterPipeBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    /** Half the pipe's width in blocks: a 6x6 pixel pipe. */
    private static final float APOTHEM = 3.0F / 16.0F;

    public WaterPipeBlock(Properties properties) {
        super(APOTHEM, properties);
        BlockState state = stateDefinition.any().setValue(WATERLOGGED, false).setValue(FILLED, false);
        for (BooleanProperty side : PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED, FILLED);
    }

    // ---- connections (visual) ----

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState()
                .setValue(WATERLOGGED, level.getFluidState(pos).is(FluidTags.WATER));
        return withConnections(level, pos, state);
    }

    /** Sets each side's boolean from the shared connection rule. */
    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(dir), WaterNetworkBlock.connects(level, pos, state, dir));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), WaterNetworkBlock.connects(level, pos, state, direction));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---- network ----

    @Override
    public Role waterRole() {
        return Role.PIPE;
    }

    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return true;
    }

    /** Only a NEW pipe changes the network; connection/filled state changes don't. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
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
        // "tooltip.holeydigsite.water_pipe" / ".lava_pipe" (the Lava Pipe subclass shares this code)
        tooltip.add(Component.translatable("tooltip.holeydigsite." + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).getPath())
                .withStyle(ChatFormatting.GRAY));
    }
}
