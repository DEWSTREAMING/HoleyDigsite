package com.holeydigsite.power;

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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A water-powered machine that hydrates farmland in a 9x9 area and accelerates nearby crop growth.
 * Operates without idle ticking. Connects to water pipes from below or from the 4 horizontal sides.
 */
public class SprinklerBlock extends BaseEntityBlock implements WaterNetworkBlock {
    public static final MapCodec<SprinklerBlock> CODEC = simpleCodec(SprinklerBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATER_POWERED = BooleanProperty.create("water_powered");

    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 11.0, 12.0);

    public SprinklerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATER_POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATER_POWERED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATER_POWERED, false);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SprinklerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null; // no idle ticking
    }

    @Override
    public Role waterRole() {
        return Role.MACHINE;
    }

    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return side != Direction.UP;
    }

    @Override
    public void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        if (state.getValue(WATER_POWERED) != powered) {
            level.setBlock(pos, state.setValue(WATER_POWERED, powered), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(pos) instanceof SprinklerBlockEntity sprinkler) {
            sprinkler.setWaterPowered(powered);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            WaterNetworks.markDirty(level, pos);
            if (level instanceof ServerLevel sl && state.getValue(WATER_POWERED)) {
                SprinklerTracker.setSprinklerPowered(sl, pos, true);
            }
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof SprinklerBlockEntity sprinkler) {
                sprinkler.cleanup();
            }
            if (level instanceof ServerLevel sl) {
                SprinklerTracker.removeSprinkler(sl, pos);
            }
            WaterNetworks.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (state.getValue(WATER_POWERED)) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.sprinkler.working").withStyle(ChatFormatting.AQUA), true);
            } else {
                player.displayClientMessage(Component.translatable("message.holeydigsite.sprinkler.no_water").withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATER_POWERED)) {
            for (int i = 0; i < 3; i++) {
                double px = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
                double py = pos.getY() + 0.68;
                double pz = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
                double vx = (random.nextDouble() - 0.5) * 0.15;
                double vy = 0.08 + random.nextDouble() * 0.08;
                double vz = (random.nextDouble() - 0.5) * 0.15;
                level.addParticle(ParticleTypes.SPLASH, px, py, pz, vx, vy, vz);
            }
            if (random.nextFloat() < 0.10f) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.12F, 1.4F, false);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.sprinkler").withStyle(ChatFormatting.GRAY));
    }
}
