package com.holeydigsite.machine;

import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.site.SiteProtection;
import com.holeydigsite.transport.ItemTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

/**
 * Base machine block for water-powered conveyor processing machines (Threshing Drum, Rope Machine, Sawmill).
 * Front faces player on placement; takes items from back belt and outputs to front belt or buffer.
 * Connects to water pipes on bottom and horizontal sides.
 */
public abstract class WaterProcessingBlock extends BaseEntityBlock implements WaterNetworkBlock, ItemTarget {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATER_POWERED = BooleanProperty.create("water_powered");
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final int RECHECK_TICKS = 40;

    protected WaterProcessingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATER_POWERED, false)
                .setValue(WORKING, false));
    }

    public abstract ResourceLocation machineId();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATER_POWERED, WORKING);
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
                .setValue(WORKING, false);
    }

    @Override
    public Role waterRole() {
        return Role.MACHINE;
    }

    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == Direction.DOWN || (side.getAxis() != Direction.Axis.Y && side != facing && side != facing.getOpposite());
    }

    @Override
    public void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        if (state.getValue(WATER_POWERED) != powered) {
            level.setBlock(pos, state.setValue(WATER_POWERED, powered), 3);
        }
        if (level.getBlockEntity(pos) instanceof WaterProcessingBlockEntity be) {
            be.setWaterPowered(powered);
        }
    }

    @Override
    public boolean canAcceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        if (from != state.getValue(FACING).getOpposite()) return false;
        if (!state.getValue(WATER_POWERED)) return false;
        if (PlotLock.isLocked(level, pos)) return false;
        if (level.getBlockEntity(pos) instanceof WaterProcessingBlockEntity be) {
            return be.canAcceptInput(one);
        }
        return false;
    }

    @Override
    public void acceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        if (level.getBlockEntity(pos) instanceof WaterProcessingBlockEntity be) {
            be.acceptInput(one);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!PlotLock.canUse(player, level, pos)) {
            return ItemInteractionResult.FAIL;
        }
        if (SiteProtection.restricted(player) && SiteProtection.isProtected(level, pos)) {
            SiteProtection.tell(player);
            return ItemInteractionResult.FAIL;
        }
        if (level.getBlockEntity(pos) instanceof WaterProcessingBlockEntity be) {
            if (!MachineRecipeManager.isIngredient(be.machineId(), stack)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!be.isWaterPowered()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.holeydigsite.machine.no_water"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (be.isInputFull() || be.isBufferFull()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.holeydigsite.machine.buffer_full"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (be.canAcceptInput(stack)) {
                if (!level.isClientSide) {
                    int space = be.availableInputSpace();
                    int count = player.isShiftKeyDown() ? 1 : Math.min(stack.getCount(), space);
                    if (count > 0) {
                        ItemStack taken = stack.split(count);
                        be.acceptInput(taken);
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!PlotLock.canUse(player, level, pos)) {
            return InteractionResult.FAIL;
        }
        if (SiteProtection.restricted(player) && SiteProtection.isProtected(level, pos)) {
            SiteProtection.tell(player);
            return InteractionResult.FAIL;
        }
        if (level.getBlockEntity(pos) instanceof WaterProcessingBlockEntity be) {
            if (!be.outputBuffer().isEmpty()) {
                if (!level.isClientSide) {
                    ItemStack top = be.outputBuffer().get(0);
                    ItemStack taken = top.split(Math.min(top.getCount(), top.getMaxStackSize()));
                    if (top.isEmpty()) {
                        be.outputBuffer().remove(0);
                    }
                    be.setChanged();
                    be.notifyBufferChanged();
                    if (!player.addItem(taken)) {
                        player.drop(taken, false);
                    }
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
                    be.checkStartRecipe();
                    Direction facing = state.getValue(FACING);
                    be.updateWorkingStatus(pos.relative(facing), facing);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (level.getBlockEntity(pos) instanceof WaterProcessingBlockEntity be) {
            be.onScheduledTick();
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WaterProcessingBlockEntity be) {
            Direction facing = state.getValue(FACING);
            if (neighborPos.equals(pos.relative(facing))) {
                be.onFrontNeighborChanged();
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof WaterProcessingBlockEntity be) {
                be.dropContents(level, pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(WORKING)) {
            return null; // zero idle ticking
        }
        return (lvl, p, s, be) -> {
            if (be instanceof WaterProcessingBlockEntity processingBe) {
                processingBe.serverTick();
            }
        };
    }
}
