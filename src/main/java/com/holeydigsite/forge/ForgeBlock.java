package com.holeydigsite.forge;

import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * A lava-powered hearth. A Crucible on top melts metal while the forge is {@link #LIT}.
 *
 * <ul>
 *   <li>Lava port on the back. The front with the glowing coals faces the player when placed, like the drill.</li>
 *   <li>{@link #LIT} means connected to an active lava network ({@link WaterNetworkBlock} machine,
 *       {@link NetworkFluid#LAVA}), pushed in by {@link WaterNetworks} only when the network changes.</li>
 *   <li>No fuel, no block entity, never ticks. When lit, it has a glowing model, light level 13, and embers in the client random display
 *       tick. The block update when LIT changes tells the Crucible above to start or stop melting.</li>
 * </ul>
 */
public class ForgeBlock extends Block implements WaterNetworkBlock {
    public static final MapCodec<ForgeBlock> CODEC = simpleCodec(ForgeBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final int heat;

    public ForgeBlock(Properties properties) {
        this(properties, 1);
    }

    /** A forge with its own heat from the tier stats. */
    public ForgeBlock(Properties properties, int heat) {
        super(properties);
        this.heat = heat;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
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

    /** How fast a crucible melts on this forge. */
    public int heat() {
        return heat;
    }

    /** Heat of the lit forge at {@code pos} (1 = Forge, 2 = Hot Forge), or 0 if there is no lit forge. */
    public static int heatAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ForgeBlock f && state.getValue(LIT) ? f.heat() : 0;
    }

    /** True if the block at {@code pos} is a lit Forge. */
    public static boolean isLitForge(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ForgeBlock && state.getValue(LIT);
    }

    // lava network

    @Override
    public Role waterRole() {
        return Role.MACHINE;
    }

    @Override
    public NetworkFluid networkFluid() {
        return NetworkFluid.LAVA;
    }

    /** The lava port is on the back. */
    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return side == state.getValue(FACING).getOpposite();
    }

    @Override
    public void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        if (state.getValue(LIT) != powered) {
            level.setBlock(pos, state.setValue(LIT, powered), Block.UPDATE_ALL); // neighbours (the crucible) hear it
            level.playSound(null, pos, powered ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) || oldState.getValue(FACING) != state.getValue(FACING)) {
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

    // player

    /** Empty hand: "Forge: Lit" / "Forge: Unlit - connect a Lava Intake in lava to the back". */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // holding something (e.g. a Crucible): let the item be used/placed
        }
        if (!level.isClientSide) {
            player.displayClientMessage(state.getValue(LIT)
                    ? Component.translatable("message.holeydigsite.forge.lit").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("message.holeydigsite.forge.unlit").withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.forge").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.forge.ports").withStyle(ChatFormatting.DARK_GRAY));
    }

    // client visuals

    /** Embers drifting out of the front and a quiet crackle while lit. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52;
        double y = pos.getY() + 0.35 + random.nextDouble() * 0.2;
        double side = (random.nextDouble() - 0.5) * 0.6;
        double px = x + (facing.getAxis() == Direction.Axis.Z ? side : 0);
        double pz = z + (facing.getAxis() == Direction.Axis.X ? side : 0);
        level.addParticle(ParticleTypes.SMALL_FLAME, px, y, pz, facing.getStepX() * 0.01, 0.01, facing.getStepZ() * 0.01);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0, 0);
        }
        if (random.nextInt(8) == 0) {
            level.playLocalSound(pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.4F, 0.8F + random.nextFloat() * 0.3F, false);
        }
    }
}
