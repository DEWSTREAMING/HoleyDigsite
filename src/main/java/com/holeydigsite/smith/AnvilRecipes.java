package com.holeydigsite.smith;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
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

/** The Smithing Anvil's recipe book: datapack registry {@code holeydigsite:anvil_recipes} (registered in {@link com.holeydigsite.economy.ModEconomy}). */
public final class AnvilRecipes {
    private AnvilRecipes() {}

    public static final ResourceKey<Registry<AnvilRecipe>> KEY = ResourceKey.createRegistryKey(HoleyDigsite.id("anvil_recipes"));

    /** One recipe with its id. */
    public record Entry(ResourceLocation id, AnvilRecipe recipe) {}

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

    /** Recipes grouped by {@link AnvilRecipe#category()} in first-seen cycle order. Every recipe appears in exactly one category. */
    public static Map<String, List<Entry>> byCategory(@Nullable HolderLookup.Provider registries) {
        Map<String, List<Entry>> map = new LinkedHashMap<>();
        for (Entry e : all(registries)) {
            map.computeIfAbsent(e.recipe().category(), k -> new ArrayList<>()).add(e);
        }
        map.replaceAll((k, v) -> List.copyOf(v));
        return Collections.unmodifiableMap(map);
    }

    /** The recipe that makes {@code item}, or null. */
    @Nullable
    public static AnvilRecipe forItem(@Nullable HolderLookup.Provider registries, Item item) {
        for (Entry e : all(registries)) {
            if (e.recipe().result() == item) {
                return e.recipe();
            }
        }
        return null;
    }
}
