package com.holeydigsite.client;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.shovel.ShovelHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** Client-only game-bus events. */
@EventBusSubscriber(modid = HoleyDigsite.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        FloatingText.pickFocus();
        RecipeWheels.clientTick();
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && RecipeWheels.onUseKeyTriggered(event.getHand())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /**
     * Tooltips:
     * <ul>
     *   <li>"Full of dirt" on a full wooden shovel.</li>
     *   <li>Items with a weight in the resource weights data map show their weight and sell value.</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> tip = event.getToolTip();
        if (ShovelHelper.isFull(stack)) {
            tip.add(1, Component.translatable("tooltip.holeydigsite.shovel.full").withStyle(ChatFormatting.GOLD));
        }

        ResourceInfo info = Selling.resource(stack);
        if (info == null || tip.isEmpty()) {
            return;
        }
        if (!info.isCrop() && !(stack.getItem() instanceof com.holeydigsite.item.IngotItem)) {
            tip.set(0, Component.translatable("tooltip.holeydigsite.weighted_name", tip.get(0).copy(), Selling.grams(stack))
                    .withStyle(tip.get(0).getStyle()));
        }
        HolderLookup.Provider registries = event.getContext().registries();
        if (registries == null) {
            return;
        }
        long each = Selling.unitValue(stack, registries);
        if (each > 0) {
            Component line = stack.getCount() > 1
                    ? Component.translatable("tooltip.holeydigsite.sells_for_stack", Selling.format(each),
                            stack.getCount(), Selling.format(each * stack.getCount()))
                    : Component.translatable("tooltip.holeydigsite.sells_for", Selling.format(each));
            tip.add(1, line.copy().withStyle(ChatFormatting.GOLD));
        }
        if (info.isUncut()) {
            tip.add(Component.translatable("tooltip.holeydigsite.uncut").withStyle(ChatFormatting.AQUA));
        }
        if (!info.meltable() && !info.isCrop()) {
            tip.add(Component.translatable("tooltip.holeydigsite.gem").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
