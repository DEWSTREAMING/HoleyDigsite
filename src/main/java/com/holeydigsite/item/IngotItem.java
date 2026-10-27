package com.holeydigsite.item;

import com.holeydigsite.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Folkiron or Goldchro ingot cast in a mould, with its weight in grams stored in the {@code holeydigsite:weight} data component
 * (default {@link #DEFAULT_GRAMS}). Ingots of the same weight stack, while different weights do not.
 * The Sell Pad pays the material's {@code ingot_copper_per_gram} multiplied by grams (see {@link com.holeydigsite.economy.Selling}).
 * Ingots can go back into a Crucible for re-melting at no gain, so weights can be combined or split.
 * Example name: "Folkiron Ingot, 50 g".
 */
public class IngotItem extends Item {
    /** Weight of an ingot without the component (e.g. from the creative tab or /give). */
    public static final int DEFAULT_GRAMS = 10;

    public IngotItem(Properties properties) {
        super(properties.component(ModDataComponents.WEIGHT.get(), DEFAULT_GRAMS));
    }

    /** Weights where the model gets bigger (a bar under 40 g is size 0, 40-89 size 1, 90-249 size 2, 250 and up size 3). */
    private static final int[] SIZE_FROM = {40, 90, 250};

    /** Model size 0-3 for a weight; the {@code holeydigsite:size} item property and the model overrides use it. */
    public static int sizeStep(int grams) {
        int step = 0;
        for (int from : SIZE_FROM) {
            if (grams >= from) step++;
        }
        return step;
    }

    /** An ingot of {@code item} weighing {@code grams}. */
    public static ItemStack of(Item item, int grams) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.WEIGHT.get(), Math.max(1, grams));
        return stack;
    }

    public static int grams(ItemStack stack) {
        Integer w = stack.get(ModDataComponents.WEIGHT.get());
        return w == null ? 0 : w;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("tooltip.holeydigsite.weighted_name", super.getName(stack), grams(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.ingot").withStyle(ChatFormatting.DARK_GRAY));
    }
}
