package com.holeydigsite.contract;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Contract Board: a wall board in the Trading Post that shows the looking player's three contracts as cards. Right-click
 * with something a card wants to deliver it; sneak with an empty hand for the full details. The card under the crosshair is
 * found with {@link #cardAt}, which the client uses for its highlight and the server for the click, so both agree.
 */
public class ContractBoardBlock extends BaseEntityBlock {
    public static final MapCodec<ContractBoardBlock> CODEC = simpleCodec(ContractBoardBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Vertical distance between the centres of two cards, and how far the card plane floats in front of the block centre. */
    public static final double CARD_STEP = 0.5;
    public static final double CARD_OFFSET = 0.42;

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES[d.get2DDataValue()] = switch (d) {
                case SOUTH -> Block.box(0, 0, 0, 16, 16, 3);
                case EAST -> Block.box(0, 0, 0, 3, 16, 16);
                case WEST -> Block.box(13, 0, 0, 16, 16, 16);
                default -> Block.box(0, 0, 13, 16, 16, 16);
            };
        }
    }

    public ContractBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ContractBoardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
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
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
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

    // ------------------------------------------------------------------------------------------------ cards

    /** The card plane's centre for a board, in world coordinates. */
    public static Vec3 planePoint(BlockPos pos, Direction facing) {
        return new Vec3(pos.getX() + 0.5 + facing.getStepX() * CARD_OFFSET, pos.getY() + 0.5, pos.getZ() + 0.5 + facing.getStepZ() * CARD_OFFSET);
    }

    /** Height of the centre of card {@code k} (0 = top) above the block's bottom. */
    public static double cardCentreY(int k) {
        return 0.5 + (1 - k) * CARD_STEP;
    }

    /** Half the width of a card's text (165 font px at the label scale, a bit to spare). */
    public static final double CARD_HALF_WIDTH = 0.9;

    /** Which card (0 top, 1 middle, 2 bottom) is under the crosshair; the middle one when it is on none. */
    public static int cardAt(BlockPos pos, Direction facing, Vec3 eye, Vec3 look) {
        int k = cardUnderCrosshair(pos, facing, eye, look);
        return k < 0 ? 1 : k;
    }

    /**
     * The card whose text is under the crosshair, or -1. The text is drawn facing the camera, so this measures in the
     * camera's own right/up directions at each card's depth, which keeps it on the text even when the text hangs over
     * the blocks above and below the board or the player looks at it from the side.
     */
    public static int cardUnderCrosshair(BlockPos pos, Direction facing, Vec3 eye, Vec3 look) {
        look = look.normalize();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-6) {
            return -1;
        }
        right = right.normalize();
        Vec3 up = right.cross(look);
        Vec3 plane = planePoint(pos, facing);
        int best = -1;
        double bestOff = CARD_STEP * 0.6;
        for (int k = 0; k < 3; k++) {
            Vec3 d = new Vec3(plane.x, pos.getY() + cardCentreY(k), plane.z).subtract(eye);
            if (d.dot(look) <= 0 || Math.abs(d.dot(right)) > CARD_HALF_WIDTH) {
                continue;
            }
            double off = Math.abs(d.dot(up));
            if (off < bestOff) {
                bestOff = off;
                best = k;
            }
        }
        return best;
    }

    /** Depth along the look ray of the card plane's centre, to tell whether something else is in front of the cards. */
    public static double cardDepth(BlockPos pos, Direction facing, Vec3 eye, Vec3 look) {
        return planePoint(pos, facing).subtract(eye).dot(look.normalize());
    }

    // ------------------------------------------------------------------------------------------------ use

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || held.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return deliver(sp, pos, cardAt(pos, state.getValue(FACING), sp.getEyePosition(), sp.getViewVector(1.0F)), held);
    }

    /** Hands the held stack to card {@code slot}, or to the first card that wants it. */
    public static ItemInteractionResult deliver(ServerPlayer sp, BlockPos pos, int slot, ItemStack held) {
        Level level = sp.level();
        ContractManager.refresh(sp);
        ContractData data = ContractManager.data(sp);
        var reg = sp.registryAccess();
        Contract at = data.get(slot);
        if (at == null || ContractManager.unitValue(at, held, reg) <= 0) {
            slot = ContractManager.firstFit(data, held, reg);
        }
        if (slot < 0) {
            sp.displayClientMessage(Component.translatable("message.holeydigsite.contract.no_match").withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.FAIL;
        }
        if (ContractManager.deliver(sp, slot, held) > 0) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.1F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return showCard(sp, cardAt(pos, state.getValue(FACING), sp.getEyePosition(), sp.getViewVector(1.0F)));
    }

    /** Empty hand: sneaking lists card {@code slot}'s details in chat, otherwise a hint. */
    public static InteractionResult showCard(ServerPlayer sp, int slot) {
        ContractManager.refresh(sp);
        if (sp.isShiftKeyDown()) {
            Contract c = ContractManager.data(sp).get(slot);
            if (c == null) {
                sp.displayClientMessage(Component.translatable("contract.holeydigsite.empty"), true);
            } else {
                long day = ContractManager.today(sp.getServer());
                for (Component line : ContractText.details(c, day, ContractDefs.rules().days())) {
                    sp.sendSystemMessage(line.copy().withStyle(ChatFormatting.GRAY));
                }
            }
        } else {
            sp.displayClientMessage(Component.translatable("message.holeydigsite.contract.hint").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.contract_board").withStyle(ChatFormatting.GRAY));
    }
}
