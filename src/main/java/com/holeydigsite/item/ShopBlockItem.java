package com.holeydigsite.item;

import com.holeydigsite.shop.ShopLock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

/**
 * Item of a shop block (Sell/Buy Pad, Money Pan, Shop Button). The normal item places the breakable, crafted version; the
 * creative-tab "Locked" variant carries {@code locked=true} in the vanilla {@code minecraft:block_state} component (like
 * the Pan's fill level) and places the unbreakable island version, for building shops on your own islands.
 */
public class ShopBlockItem extends BlockItem {
    public ShopBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static boolean isLockedVariant(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        return props != null && Boolean.TRUE.equals(props.get(ShopLock.LOCKED));
    }

    public static ItemStack locked(Block block) {
        ItemStack stack = new ItemStack(block);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(ShopLock.LOCKED, true));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return isLockedVariant(stack) ? Component.translatable("item.holeydigsite.locked", super.getName(stack)) : super.getName(stack);
    }
}
