package com.holeydigsite.item;

import com.holeydigsite.tier.DirtTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Head attachment for the Prospector's Pick (Sand Head, Gravel Head).
 */
public class PickHeadItem extends Item {
    private final int rockKind;

    public PickHeadItem(Properties properties, int rockKind) {
        super(properties);
        this.rockKind = rockKind;
    }

    public int rockKind() {
        return rockKind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        InteractionHand otherHand = context.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        if (otherStack.getItem() instanceof ProspectorPickItem) {
            if (ProspectorPickItem.fitHead(player, otherStack, context.getItemInHand())) {
                return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack head = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        if (otherStack.getItem() instanceof ProspectorPickItem) {
            if (ProspectorPickItem.fitHead(player, otherStack, head)) {
                return InteractionResultHolder.sidedSuccess(head, level.isClientSide);
            }
        }
        return InteractionResultHolder.pass(head);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String nameKey = rockKind == DirtTier.SAND ? "sand_head" : "gravel_head";
        tooltip.add(Component.translatable("tooltip.holeydigsite." + nameKey).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.pick_head.fits").withStyle(ChatFormatting.DARK_GRAY));
    }
}
