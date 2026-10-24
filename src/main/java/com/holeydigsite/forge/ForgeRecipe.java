package com.holeydigsite.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One Crafting Forge recipe (Phase 10), an entry of the datapack registry {@code holeydigsite:forge_recipes}
 * ({@code data/<namespace>/holeydigsite/forge_recipes/<name>.json}), synced to clients for the floating text:
 * <pre>{@code
 * {
 *   "order": 10,
 *   "category": "drills",
 *   "metals": {"holeydigsite:folkiron": 100, "holeydigsite:goldchro": 25},
 *   "parts":  [{"item": "holeydigsite:mining_drill", "count": 1}],
 *   "result": {"item": "holeydigsite:mining_drill_t2", "count": 1}
 * }
 * }</pre>
 * Metals are EXACT grams taken from the forge's pooled grams (materials as in the {@code resource_weights} data map);
 * whatever is left is cast back into one ingot per metal. Parts are plain items (no components) and counts.
 *
 * @param order    position in the forge's recipe cycle (then by id)
 * @param category radial-wheel category id (defaults to {@code "other"})
 * @param variant  optional lang key distinguishing alternative recipes for the same result
 * @param metals   grams per material
 * @param parts    items consumed
 * @param result   what one cast makes
 */
public record ForgeRecipe(int order, String category, Optional<String> variant,
                          Map<ResourceLocation, Integer> metals, List<Part> parts, Result result) {

    public ForgeRecipe(int order, Map<ResourceLocation, Integer> metals, List<Part> parts, Result result) {
        this(order, "other", Optional.empty(), metals, parts, result);
    }

    /** A part the recipe consumes. */
    public record Part(Item item, int count) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Part::item),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Part::count)
        ).apply(i, Part::new));
    }

    /** What a cast makes. */
    public record Result(Item item, int count) {
        public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Result::item),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Result::count)
        ).apply(i, Result::new));

        public ItemStack stack() {
            return new ItemStack(item, count);
        }
    }

    public static final Codec<ForgeRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("order", 0).forGetter(ForgeRecipe::order),
            Codec.STRING.optionalFieldOf("category", "other").forGetter(ForgeRecipe::category),
            Codec.STRING.optionalFieldOf("variant").forGetter(ForgeRecipe::variant),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(1, 1_000_000)).optionalFieldOf("metals", Map.of())
                    .forGetter(ForgeRecipe::metals),
            Part.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(ForgeRecipe::parts),
            Result.CODEC.fieldOf("result").forGetter(ForgeRecipe::result)
    ).apply(i, ForgeRecipe::new));

    public ForgeRecipe {
        category = category == null || category.isBlank() ? "other" : category;
        variant = variant != null && variant.isPresent() && !variant.get().isBlank() ? variant : Optional.empty();
        metals = new TreeMap<>(metals); // stable, alphabetical order (folkiron before goldchro)
        parts = List.copyOf(parts);
    }

    public ItemStack resultStack() {
        return result.stack();
    }

    /** Base result name with count: "Tier 2 Mining Drill" or "4x Fast Conveyor Belt". */
    public Component baseName() {
        Component n = resultStack().getHoverName();
        return result.count() > 1 ? Component.translatable("text.holeydigsite.crafting_forge.count", result.count(), n) : n;
    }

    /** Explicit or automatically derived variant label (e.g. "Orchremia"), or null if not an alternative recipe. */
    @Nullable
    public Component variantLabel(@Nullable HolderLookup.Provider registries) {
        if (variant.isPresent()) {
            String v = variant.get();
            return Component.translatableWithFallback(v, v);
        }
        if (registries != null && ForgeRecipes.isDuplicated(registries, result.item()) && !metals.isEmpty()) {
            return Metals.name(metals.keySet().iterator().next());
        }
        return null;
    }

    /** "Tier 2 Mining Drill" or "4x Fast Conveyor Belt (Orchremia)". */
    public Component name() {
        return name(null);
    }

    public Component name(@Nullable HolderLookup.Provider registries) {
        Component base = baseName();
        Component var = variantLabel(registries);
        return var != null ? Component.translatable("text.holeydigsite.recipe.variant", base, var) : base;
    }

    /** Grams of {@code metal} this recipe needs (0 if none). */
    public int grams(ResourceLocation metal) {
        return metals.getOrDefault(metal, 0);
    }

    /** How many of {@code item} this recipe needs (0 if none). */
    public int partCount(Item item) {
        int n = 0;
        for (Part p : parts) {
            if (p.item() == item) {
                n += p.count();
            }
        }
        return n;
    }
}
