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

/** The item classes of Phase 19: smithed jewellery, weapons and axe (named and described from their {@link SmithData}) and the hammer. */
public final class SmithItems {
    private SmithItems() {}

    /** Ring and Necklace: only worth something. */
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
     * The Necklace: jewellery that can also be worn in the head armour slot (right-click, dispenser, armour slot). Its head
     * armour is a component on the stack (set when it is made; necklaces from before Phase 20 get it the first time they sit in
     * a player's inventory on the server).
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

    /** Sword and Dagger: a real weapon, stats set on the stack when it is made. */
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

    /** The smithed axe: chops, strips and fights like an axe, with the stats of its metals. */
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

    /** Blacksmith Hammer: right-click a Smithing Anvil to strike. No durability. */
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
