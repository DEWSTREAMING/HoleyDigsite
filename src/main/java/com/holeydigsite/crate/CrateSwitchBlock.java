package com.holeydigsite.crate;

import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerNetwork;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Crate Switch: a plaque at spawn. Right-click turns crates and skins on or off for the player who used it. The state lives on
 * the player ({@link LedgerData#cratesOn()}), so the plaque itself never changes.
 */
public class CrateSwitchBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CrateSwitchBlock> CODEC = simpleCodec(CrateSwitchBlock::new);

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        SHAPES[Direction.NORTH.get2DDataValue()] = Block.box(3, 3, 14, 13, 13, 16);
        SHAPES[Direction.SOUTH.get2DDataValue()] = Block.box(3, 3, 0, 13, 13, 2);
        SHAPES[Direction.EAST.get2DDataValue()] = Block.box(0, 3, 3, 2, 13, 13);
        SHAPES[Direction.WEST.get2DDataValue()] = Block.box(14, 3, 3, 16, 13, 13);
    }

    public CrateSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        return defaultBlockState().setValue(FACING, clicked.getAxis().isHorizontal() ? clicked : ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            boolean on = toggle(sp);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, on ? 0.7F : 0.5F);
        }
        return InteractionResult.CONSUME;
    }

    /** Flips the switch for a player and says which way it is now. Returns the new state. */
    public static boolean toggle(ServerPlayer sp) {
        boolean on = !LedgerData.get(sp).cratesOn();
        set(sp, on);
        return on;
    }

    public static void set(ServerPlayer sp, boolean on) {
        LedgerData.get(sp).setCratesOn(on);
        LedgerNetwork.syncToClient(sp);
        sp.displayClientMessage(Component.translatable(on ? "message.holeydigsite.crate_switch.on" : "message.holeydigsite.crate_switch.off"), true);
    }
}
