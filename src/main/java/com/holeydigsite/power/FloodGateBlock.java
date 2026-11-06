package com.holeydigsite.power;

import com.holeydigsite.plot.PlotLock;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A water-powered gate placed at the head of a crop row.
 * Opened by player interaction or redstone, washing down the row, harvesting ripe crops,
 * replanting them in order, and buffering output for connected belts.
 * Zero idle ticking: tickers run only while washing or draining the buffer.
 */
public class FloodGateBlock extends BaseEntityBlock implements WaterNetworkBlock {
    public static final MapCodec<FloodGateBlock> CODEC = simpleCodec(FloodGateBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATER_POWERED = BooleanProperty.create("water_powered");
    public static final BooleanProperty WASHING = BooleanProperty.create("washing");

    public FloodGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATER_POWERED, false)
                .setValue(WASHING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATER_POWERED, WASHING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATER_POWERED, false)
                .setValue(WASHING, false);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloodGateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(WASHING)) {
            return null; // zero idle ticking
        }
        return createTickerHelper(type, com.holeydigsite.registry.ModBlockEntities.FLOOD_GATE.get(), FloodGateBlockEntity::serverTick);
    }

    @Override
    public Role waterRole() {
        return Role.MACHINE;
    }

    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        // Water enters through bottom, back, or sides; front faces down the crop row, top has winch latch
        return side != Direction.UP && side != state.getValue(FACING);
    }

    @Override
    public void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        if (state.getValue(WATER_POWERED) != powered) {
            level.setBlock(pos, state.setValue(WATER_POWERED, powered), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(pos) instanceof FloodGateBlockEntity gate) {
            gate.setWaterPowered(powered);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            WaterNetworks.markDirty(level, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof FloodGateBlockEntity gate) {
            gate.setOwner(p.getUUID());
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof FloodGateBlockEntity gate) {
                gate.dropBuffer();
            }
            WaterNetworks.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!PlotLock.canUse(player, level, pos)) {
            return InteractionResult.CONSUME;
        }

        if (level.getBlockEntity(pos) instanceof FloodGateBlockEntity gate) {
            gate.triggerWash(player);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FloodGateBlockEntity gate) {
            boolean signal = level.hasNeighborSignal(pos);
            gate.onNeighborSignal(signal);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.flood_gate").withStyle(ChatFormatting.GRAY));
    }
}
