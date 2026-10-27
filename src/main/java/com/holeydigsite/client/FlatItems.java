package com.holeydigsite.client;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.item.IngotItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Items whose model is a 3D shape that already lies flat after the FIXED transform (ingots, clumps, metal chunks).
 * Renderers skip the 90 degree turn for them and lift them by {@code scale / 16} so they rest on the surface.
 */
public final class FlatItems {
    private FlatItems() {
    }

    public static boolean isLump(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!key.getNamespace().equals(HoleyDigsite.MODID)) {
            return false;
        }
        String path = key.getPath();
        return path.endsWith("_clump") || path.contains("_chunk_");
    }

    public static boolean isFlat(ItemStack stack) {
        return stack.getItem() instanceof IngotItem || isLump(stack);
    }
}
