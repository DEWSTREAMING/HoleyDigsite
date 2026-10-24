package com.holeydigsite.transport;

import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Uncut gems and what the Gem Cutter turns them into ({@code ns:uncut_x} to {@code ns:x}, same size). */
public final class GemCutting {
    private GemCutting() {}

    /** True for an uncut gem (form "uncut" in resource_weights) that has a cut version. */
    public static boolean isUncut(ItemStack stack) {
        ResourceInfo info = Selling.resource(stack);
        return info != null && info.isUncut() && cutItem(stack) != Items.AIR;
    }

    /** The cut item for an uncut gem, or AIR. */
    public static Item cutItem(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getPath().startsWith("uncut_")) {
            return Items.AIR;
        }
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(6)));
    }

    /** The cut gem (same count), or the stack unchanged if it isn't an uncut gem. */
    public static ItemStack cut(ItemStack stack) {
        Item cut = cutItem(stack);
        return cut == Items.AIR ? stack : new ItemStack(cut, stack.getCount());
    }
}
