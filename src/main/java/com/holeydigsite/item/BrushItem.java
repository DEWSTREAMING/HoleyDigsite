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
 * The panning brush, distinct from the vanilla archaeology brush.
 *
 * <p>Hold right-click on a waterlogged pan with dirt in it to pan it (see {@link Panning}).
 * The normal right-click is routed through {@link PanBlock#useItemOn}, while {@link #useOn} covers
 * sneak-clicking where vanilla skips the block interaction.</p>
 *
 * <p>The brush has no durability and is never damaged, so it can never break, which is important
 * on a skyblock island where it cannot be replaced easily.</p>
 */
public class BrushItem extends Item {
    private static final int USE_DURATION = 72000;

    private final int cycleTicks;

    public BrushItem(Properties properties) {
        this(properties, Panning.PAN_CYCLE_TICKS);
    }

    public BrushItem(Properties properties, int cycleTicks) {
        super(properties);
        this.cycleTicks = cycleTicks;
    }

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
        int usedTicks = getUseDuration(stack, entity) - remainingUseDuration + 1;
        Panning.tickPanning(level, entity, usedTicks);
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        Panning.stopPanning(entity);
    }
}
