package com.holeydigsite.smith;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Item classes for smithed jewellery, weapons, tools, and hammers. */
public final class SmithItems {
    private SmithItems() {}

    /** Jewellery items. */
    public static class Jewellery extends Item {
        public Jewellery(Properties properties) {
            super(properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            return SmithPieces.name(stack, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            SmithPieces.tooltip(stack, context, tooltip);
        }
    }

    /**
     * Jewellery that can be worn in the head armour slot.
     */
    public static class Necklace extends Jewellery implements Equipable {
        public Necklace(Properties properties) {
            super(properties);
            DispenserBlock.registerBehavior(this, ArmorItem.DISPENSE_ITEM_BEHAVIOR);
        }

        @Override
        public EquipmentSlot getEquipmentSlot() {
            return EquipmentSlot.HEAD;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return swapWithEquipmentSlot(this, level, player, hand);
        }

        @Override
        public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
            if (!level.isClientSide && entity instanceof Player && stack.get(DataComponents.ATTRIBUTE_MODIFIERS) != null
                    && stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().isEmpty() && SmithPieces.data(stack) != null) {
                SmithPieces.applyHeadArmor(stack, level.registryAccess());
            }
        }
    }

    /** Sword and dagger weapons. */
    public static class Blade extends SwordItem {
        public Blade(Properties properties) {
            super(SmithTier.INSTANCE, properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            return SmithPieces.name(stack, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            SmithPieces.tooltip(stack, context, tooltip);
        }
    }

    /** Smithed axe item. */
    public static class Axe extends AxeItem {
        public Axe(Properties properties) {
            super(SmithTier.INSTANCE, properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            return SmithPieces.name(stack, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            SmithPieces.tooltip(stack, context, tooltip);
        }
    }

    /** Blacksmith hammer for striking smithing anvils. */
    public static class Hammer extends Item {
        public Hammer(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            tooltip.add(Component.translatable("tooltip.holeydigsite.blacksmith_hammer").withStyle(ChatFormatting.GRAY));
        }
    }
}
