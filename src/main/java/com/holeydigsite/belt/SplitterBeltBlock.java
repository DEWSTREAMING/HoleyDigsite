package com.holeydigsite.belt;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Phase 13 Belt Splitter: a full-height, water-powered belt block that shares one line between up to three outputs.
 *
 * <ul>
 *   <li>Items enter from the back (or a side, like any belt) and stop in the middle, where the splitter picks an output
 *       ROUND-ROBIN: left, front, right, left, ... An output is only used if something that takes the item is there (a
 *       belt with room at that entry, a Crucible with room, a Crafting Forge, a Sell Pad, a Gem Cutter...). An output that
 *       is missing or refuses right now is skipped and the next one is tried; if all of them refuse, the item waits.</li>
 *   <li>Mode ({@link #TWO_WAY}): empty-hand right-click on the TOP toggles between 3-way (left / front / right) and 2-way
 *       (left / right only; the front stays closed). Empty-hand clicks on the sides still take the items off.</li>
 *   <li>Left and right items leave at the middle (like the Filter Belt's side exit); front items carry on to the end.</li>
 * </ul>
 */
public class SplitterBeltBlock extends ClumpBeltBlock {
    public static final MapCodec<SplitterBeltBlock> CODEC = simpleCodec(SplitterBeltBlock::new);
    /** Left and right only when true; left, front and right when false (the default). */
    public static final BooleanProperty TWO_WAY = BooleanProperty.create("two_way");

    /** Route ids (also stored per item). Seen looking along the facing. */
    public static final int LEFT = 0, FRONT = 1, RIGHT = 2;
    private static final int[] THREE = {LEFT, FRONT, RIGHT};
    private static final int[] TWO = {LEFT, RIGHT};

    public SplitterBeltBlock(Properties properties) {
        super(properties, 1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        addCommon(builder);
        builder.add(TWO_WAY, INPUT_SPEED);
    }

    /** The outputs in round-robin order for this state. */
    public static int[] order(BlockState state) {
        return state.getValue(TWO_WAY) ? TWO : THREE;
    }

    /** World direction of a route for a splitter in {@code state}. */
    public static Direction direction(BlockState state, int route) {
        Direction f = state.getValue(FACING);
        return route == LEFT ? f.getCounterClockWise() : route == RIGHT ? f.getClockWise() : f;
    }

    /** Empty hand on the top: switch 3-way / 2-way. Everything else as on any belt. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() == Direction.UP && player.getMainHandItem().isEmpty() && !player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                toggle(level, pos, state, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /** Switches the mode and tells the player. Public for tests. Returns the new state. */
    public static BlockState toggle(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState next = state.setValue(TWO_WAY, !state.getValue(TWO_WAY));
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 1.2F);
        if (player != null) {
            player.displayClientMessage(Component.translatable(next.getValue(TWO_WAY)
                    ? "message.holeydigsite.splitter.two" : "message.holeydigsite.splitter.three").withStyle(ChatFormatting.GREEN), true);
        }
        return next;
    }
}
