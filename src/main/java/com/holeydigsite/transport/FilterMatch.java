package com.holeydigsite.transport;

import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import net.minecraft.world.item.ItemStack;

/**
 * Filter Belt matching. A filter item with a material in {@code resource_weights} matches the whole material
 * (any Folkiron chunk size or ingot weight, where a cut Ruby matches cut and uncut Rubies). A filter set with an UNCUT gem
 * matches only uncut gems of that kind ("uncut only"). Any other filter item matches the exact item including its data (so a
 * Deep Dirt Clump filter takes only Deep clumps).
 */
public final class FilterMatch {
    private FilterMatch() {}

    public static boolean matches(ItemStack filter, ItemStack stack) {
        if (filter.isEmpty() || stack.isEmpty()) {
            return false;
        }
        ResourceInfo f = Selling.resource(filter);
        if (f != null) {
            ResourceInfo s = Selling.resource(stack);
            return s != null && s.material().equals(f.material()) && (!f.isUncut() || s.isUncut());
        }
        return ItemStack.isSameItemSameComponents(filter, stack);
    }
}
