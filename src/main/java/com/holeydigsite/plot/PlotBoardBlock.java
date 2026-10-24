package com.holeydigsite.plot;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A land plot board, showing "For Sale" until its Land Deed is used, then "Owned".
 * Unbreakable in survival. Right-clicking with the matching deed claims the plot, using the deed,
 * updating {@link PlotData}, and playing effects.
 */
public class PlotBoardBlock extends BaseEntityBlock {
    public static final MapCodec<PlotBoardBlock> CODEC = simpleCodec(PlotBoardBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OWNED = BooleanProperty.create("owned");

    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(7, 0, 7, 9, 10, 9), Block.box(1, 8, 6.5, 15, 16, 9.5));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(7, 0, 7, 9, 10, 9), Block.box(6.5, 8, 1, 9.5, 16, 15));

    public PlotBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OWNED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OWNED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlotBoardBlockEntity(pos, state);
    }

    @Nullable
    public static PlotBoardBlockEntity board(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PlotBoardBlockEntity b ? b : null;
    }

    /** Registers the plot one tick after placement once NBT is loaded. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        PlotBoardBlockEntity b = board(level, pos);
        if (b != null) {
            b.register();
        }
    }

    // ---- player ----

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof LandDeedItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            claim(level, pos, player, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            PlotBoardBlockEntity b = board(level, pos);
            String id = b == null ? "" : b.plotId();
            player.displayClientMessage(state.getValue(OWNED)
                    ? Component.translatable("message.holeydigsite.plot.owned", LandDeedItem.plotName(id)).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("message.holeydigsite.plot.for_sale", LandDeedItem.plotName(id)).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Claims the plot with a deed. Returns true if the plot became owned. */
    public static boolean claim(Level level, BlockPos pos, Player player, ItemStack deed) {
        PlotBoardBlockEntity b = board(level, pos);
        BlockState state = level.getBlockState(pos);
        if (b == null || !(level instanceof ServerLevel sl) || !(deed.getItem() instanceof LandDeedItem item)) {
            return false;
        }
        if (state.getValue(OWNED)) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.plot.already_owned").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (!item.plotId().equals(b.plotId())) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.plot.wrong_deed",
                    LandDeedItem.plotName(item.plotId())).withStyle(ChatFormatting.RED), true);
            return false;
        }
        b.register();
        PlotData.get(sl).setOwned(b.plotId(), pos, true, player.getGameProfile().getName());
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            int ownedCount = (int) PlotData.get(sl).all().stream().filter(PlotData.Plot::owned).map(PlotData.Plot::id).distinct().count();
            com.holeydigsite.ledger.ModTriggers.PLOT_BOUGHT.get().trigger(sp, b.plotId(), ownedCount);
        }
        level.setBlock(pos, level.getBlockState(pos).setValue(OWNED, true), Block.UPDATE_CLIENTS);
        deed.consume(1, player);
        level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
        level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 1.5, 0.8, 1.5, 0.1);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.3);
        player.displayClientMessage(Component.translatable("message.holeydigsite.plot.claimed", LandDeedItem.plotName(b.plotId()))
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }
}
