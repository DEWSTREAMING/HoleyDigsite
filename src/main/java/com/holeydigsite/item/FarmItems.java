package com.holeydigsite.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Seeds and foods of the three own crops, being ordinary items with one grey tooltip line. */
public final class FarmItems {
    private FarmItems() {}

    /** Seeds that can be planted on farmland, with a tooltip saying where they are found. */
    public static class Seeds extends ItemNameBlockItem {
        private final String tooltip;

        public Seeds(Block crop, Properties properties, String tooltip) {
            super(crop, properties);
            this.tooltip = tooltip;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, context, lines, flag);
            lines.add(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Food with a tooltip line describing what the buff does, using the drinking animation if specified. */
    public static class Food extends Item {
        private final String tooltip;
        private final boolean drink;

        public Food(Properties properties, String tooltip, boolean drink) {
            super(properties);
            this.tooltip = tooltip;
            this.drink = drink;
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return drink ? UseAnim.DRINK : UseAnim.EAT;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, context, lines, flag);
            if (tooltip != null) {
                lines.add(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
