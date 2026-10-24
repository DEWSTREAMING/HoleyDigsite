package com.holeydigsite.transport;

import com.holeydigsite.belt.ClumpBeltBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 21i Drop Chute: an unpowered, vertical tube with an open top. It accepts items from a belt/incline/machine
 * pointing INTO its side or from above, and passes them straight DOWN through any number of stacked chutes to the
 * first non-chute block below.
 *
 * <p>No ticking and no storage: pass-through only. If the target below cannot take the item right now, or is missing/invalid
 * (chests, hoppers, air, locked plots), the chute refuses the item, making the feeding belt wait so nothing is ever lost.</p>
 */
public class DropChuteBlock extends Block implements ItemTarget {

    /** Hollow square tube with 2-block-pixel thick walls. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 16, 4),   // North wall
            Block.box(2, 0, 12, 14, 16, 14), // South wall
            Block.box(2, 0, 4, 4, 16, 12),   // West wall
            Block.box(12, 0, 4, 14, 16, 12)  // East wall
    ).optimize();

    public DropChuteBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Finds the first non-chute block position directly beneath this chute (or stack of chutes). */
    @Nullable
    public static BlockPos findBottomTarget(Level level, BlockPos pos) {
        BlockPos cur = pos.below();
        for (int i = 0; i < 256; i++) {
            if (cur.getY() < level.getMinBuildHeight()) {
                return null;
            }
            BlockState s = level.getBlockState(cur);
            if (s.getBlock() instanceof DropChuteBlock) {
                cur = cur.below();
                continue;
            }
            return cur;
        }
        return null;
    }

    @Override
    public boolean canAcceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        if (from == Direction.DOWN || one.isEmpty()) {
            return false;
        }
        BlockPos bottom = findBottomTarget(level, pos);
        if (bottom == null) {
            return false;
        }
        return ItemOutputs.canOutput(level, bottom, Direction.UP, one);
    }

    @Override
    public void acceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        BlockPos bottom = findBottomTarget(level, pos);
        if (bottom != null && ItemOutputs.tryOutput(level, bottom, Direction.UP, one)) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.3F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.1, 0.1, 0.1, 0.05);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!ClumpBeltBlock.playerInsertable(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            int want = player.isShiftKeyDown() ? stack.getCount() : 1;
            int dropped = 0;
            ItemStack one = stack.copyWithCount(1);
            while (dropped < want && canAcceptItem(level, pos, state, Direction.UP, one)) {
                acceptItem(level, pos, state, Direction.UP, one);
                dropped++;
            }
            if (dropped == 0) {
                return ItemInteractionResult.CONSUME;
            }
            stack.consume(dropped, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
