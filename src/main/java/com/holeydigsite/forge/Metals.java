package com.holeydigsite.forge;

import com.holeydigsite.economy.ModEconomy;
import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.IngotItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Melting rules, all read from the {@code holeydigsite:resource_weights} data map:
 * <ul>
 *   <li>an item can go into a crucible when it has an entry with {@code meltable: true} (Folkiron / Goldchro chunks and
 *       their ingots; Rubies are {@code meltable: false});</li>
 *   <li>its grams are {@link Selling#grams} - fixed per chunk, the {@code holeydigsite:weight} component for ingots;</li>
 *   <li>the ingot cast from a material is the item whose entry has that material and {@code form: "ingot"}.</li>
 * </ul>
 */
public final class Metals {
    private Metals() {}

    /** The resource entry if the item can be melted, else null. */
    @Nullable
    public static ResourceInfo meltable(ItemStack stack) {
        ResourceInfo info = Selling.resource(stack);
        return info != null && info.meltable() ? info : null;
    }

    /** The ingot item for a material, or null if no item is registered as its ingot. */
    @Nullable
    public static Item ingotFor(ResourceLocation material) {
        Map<ResourceKey<Item>, ResourceInfo> map = BuiltInRegistries.ITEM.getDataMap(ModEconomy.RESOURCE_WEIGHTS);
        for (Map.Entry<ResourceKey<Item>, ResourceInfo> e : map.entrySet()) {
            if (e.getValue().isIngot() && e.getValue().material().equals(material)) {
                return BuiltInRegistries.ITEM.get(e.getKey());
            }
        }
        return null;
    }

    /** An ingot of {@code grams} of the material, or EMPTY if the material has no ingot item. */
    public static ItemStack ingot(ResourceLocation material, int grams) {
        Item item = ingotFor(material);
        return item == null || grams <= 0 ? ItemStack.EMPTY : IngotItem.of(item, grams);
    }

    /** "Folkiron" (translation {@code material.holeydigsite.folkiron}, falls back to the id path). */
    public static Component name(@Nullable ResourceLocation material) {
        if (material == null) {
            return Component.empty();
        }
        String path = material.getPath();
        String fallback = path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return Component.translatableWithFallback("material." + material.getNamespace() + "." + path, fallback);
    }
}
