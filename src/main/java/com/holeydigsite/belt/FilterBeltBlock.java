package com.holeydigsite.belt;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Filter Belt, a Hydroneer-style sorter. Items that match the filter ({@link com.holeydigsite.transport.FilterMatch})
 * leave through the filter's side at the middle of the belt, while everything else carries on straight.
 *
 * <ul>
 *   <li>Right-click with any item, that item becomes the filter and stays in your hand.</li>
 *   <li>Sneak and empty hand clears the filter.</li>
 *   <li>Empty hand on the left or right edge (outer quarter of the top, or a side face) matches leave through that side
 *       ({@link #OUT_RIGHT}). Empty hand in the middle takes items off like any belt.</li>
 * </ul>
 * The side output delivers into whatever is there like a belt, crucible, pan, Gem Cutter, or Sell Pad. If it refuses, the item
 * waits at the middle and the belt backs up.
 */
public class FilterBeltBlock extends ClumpBeltBlock {
    public static final MapCodec<FilterBeltBlock> CODEC = simpleCodec(FilterBeltBlock::new);
    /** Matches leave to the right (seen looking along the facing) when true, to the left when false. */
    public static final BooleanProperty OUT_RIGHT = BooleanProperty.create("out_right");

    public FilterBeltBlock(Properties properties) {
        super(properties, 1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        addCommon(builder);
        builder.add(OUT_RIGHT, INPUT_SPEED);
    }

    /** The side matching items leave through. */
    public static Direction outSide(BlockState state) {
        Direction f = state.getValue(FACING);
        return state.getValue(OUT_RIGHT) ? f.getClockWise() : f.getCounterClockWise();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            belt.setFilter(stack);
            player.displayClientMessage(Component.translatable("message.holeydigsite.filter.set", stack.getHoverName(),
                    side(state)).withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.3F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static Component side(BlockState state) {
        return Component.translatable(state.getValue(OUT_RIGHT) ? "message.holeydigsite.filter.right" : "message.holeydigsite.filter.left");
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            belt.setFilter(ItemStack.EMPTY);
            player.displayClientMessage(Component.translatable("message.holeydigsite.filter.cleared").withStyle(ChatFormatting.YELLOW), true);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
            return InteractionResult.CONSUME;
        }
        Boolean edge = clickedEdge(state, pos, hit);
        if (edge != null) {
            level.setBlock(pos, state.setValue(OUT_RIGHT, edge), Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable("message.holeydigsite.filter.side",
                    side(level.getBlockState(pos))).withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.2F);
            return InteractionResult.CONSUME;
        }
        if (belt.clumpCount() > 0) {
            giveAll(level, pos, player, belt);
        } else {
            player.displayClientMessage(belt.filter().isEmpty()
                    ? Component.translatable("message.holeydigsite.filter.empty").withStyle(ChatFormatting.AQUA)
                    : Component.translatable("message.holeydigsite.filter.status", belt.filter().getHoverName(), side(state))
                            .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.CONSUME;
    }

    /** True for right edge, false for left edge, null for the middle half of the belt. Public for tests. */
    public static Boolean clickedEdge(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction f = state.getValue(FACING);
        Direction right = f.getClockWise();
        Vec3 local = hit.getLocation().subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double lateral = local.x * right.getStepX() + local.z * right.getStepZ();
        if (hit.getDirection() == right) return true;
        if (hit.getDirection() == right.getOpposite()) return false;
        if (Math.abs(lateral) > 0.25) return lateral > 0;
        return null;
    }
}
