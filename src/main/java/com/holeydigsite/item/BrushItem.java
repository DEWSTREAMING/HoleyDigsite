package com.holeydigsite.item;

import com.holeydigsite.block.PanBlock;
import com.holeydigsite.panning.Panning;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The panning brush (distinct from the vanilla archaeology brush).
 *
 * <p>Hold right-click on a waterlogged pan with dirt in it to pan it - see {@link Panning}.
 * The normal (non-sneaking) right-click is routed through {@link PanBlock#useItemOn}; {@link #useOn} covers
 * sneak-clicking, where vanilla skips the block's interaction.</p>
 *
 * <p>The brush has NO durability (no max damage is set in {@code ModItems}) and is never damaged, so it can
 * never break - important on a skyblock island where it can't be replaced easily.</p>
 */
public class BrushItem extends Item {
    /** Effectively "until released"; each pan cycle is {@link Panning#PAN_CYCLE_TICKS} of this. */
    private static final int USE_DURATION = 72000;

    private final int cycleTicks;

    /** The normal brush: one scoop every {@link Panning#PAN_CYCLE_TICKS} ticks. */
    public BrushItem(Properties properties) {
        this(properties, Panning.PAN_CYCLE_TICKS);
    }

    /** A brush with its own speed (Phase 10 Goldchro Brush: 20 ticks per scoop). */
    public BrushItem(Properties properties, int cycleTicks) {
        super(properties);
        this.cycleTicks = cycleTicks;
    }

    /** Ticks of holding right-click per scoop with this brush. */
    public int cycleTicks() {
        return cycleTicks;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.holeydigsite.brush.speed",
                String.format(java.util.Locale.ROOT, "%.1f", cycleTicks / 20.0)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** Sneak + right-click on a pan (otherwise PanBlock#useItemOn handles it first). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (player == null || !(state.getBlock() instanceof PanBlock)) {
            return InteractionResult.PASS;
        }
        Panning.tryStartPanning(state, context.getLevel(), context.getClickedPos(), player, context.getHand());
        return InteractionResult.CONSUME;
    }

    /** Same first/third-person animation as the vanilla brush. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        int usedTicks = getUseDuration(stack, entity) - remainingUseDuration + 1; // 1 on the first tick
        Panning.tickPanning(level, entity, usedTicks);
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        Panning.stopPanning(entity);
    }
}
