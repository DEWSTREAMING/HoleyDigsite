package com.holeydigsite.forge;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Crafting Forge's recipe book: datapack registry {@code holeydigsite:forge_recipes} (registered and synced in
 * {@link com.holeydigsite.economy.ModEconomy}). A new recipe is just a JSON file.
 */
public final class ForgeRecipes {
    private ForgeRecipes() {}

    public static final ResourceKey<Registry<ForgeRecipe>> KEY = ResourceKey.createRegistryKey(HoleyDigsite.id("forge_recipes"));

    /** One recipe with its id. */
    public record Entry(ResourceLocation id, ForgeRecipe recipe) {}

    /** Every recipe in cycle order: by {@code order}, then by id. */
    public static List<Entry> all(@Nullable HolderLookup.Provider registries) {
        if (registries == null) {
            return List.of();
        }
        return registries.lookup(KEY).map(r -> r.listElements()
                .map(h -> new Entry(h.key().location(), h.value()))
                .sorted(Comparator.comparingInt((Entry e) -> e.recipe().order()).thenComparing(e -> e.id().toString()))
                .toList()).orElse(List.of());
    }

    @Nullable
    public static Entry find(@Nullable HolderLookup.Provider registries, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        for (Entry e : all(registries)) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** True if more than one recipe in the registry makes {@code resultItem}. */
    public static boolean isDuplicated(@Nullable HolderLookup.Provider registries, Item resultItem) {
        int count = 0;
        for (Entry e : all(registries)) {
            if (e.recipe().result().item() == resultItem && ++count > 1) {
                return true;
            }
        }
        return false;
    }

    /** Recipes grouped by {@link ForgeRecipe#category()} in first-seen cycle order. Every recipe appears in exactly one category. */
    public static Map<String, List<Entry>> byCategory(@Nullable HolderLookup.Provider registries) {
        Map<String, List<Entry>> map = new LinkedHashMap<>();
        for (Entry e : all(registries)) {
            map.computeIfAbsent(e.recipe().category(), k -> new ArrayList<>()).add(e);
        }
        map.replaceAll((k, v) -> List.copyOf(v));
        return Collections.unmodifiableMap(map);
    }

    /** Translated title of a recipe category id (e.g. {@code "belts"} -> "Belts"). */
    public static Component categoryName(String category) {
        String key = (category == null || category.isBlank()) ? "other" : category;
        String fallback = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        return Component.translatableWithFallback("category.holeydigsite." + key, fallback);
    }

    /** True if {@code item} is a part of at least one recipe (only those can be loaded as parts). */
    public static boolean isPart(@Nullable HolderLookup.Provider registries, Item item) {
        if (registries == null) {
            return false;
        }
        return registries.lookup(KEY).map(r -> r.listElements().map(Holder::value)
                .anyMatch(rec -> rec.partCount(item) > 0)).orElse(false);
    }
}
