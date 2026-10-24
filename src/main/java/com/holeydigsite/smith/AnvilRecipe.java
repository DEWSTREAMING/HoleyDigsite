package com.holeydigsite.smith;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One Smithing Anvil recipe, an entry of the {@code holeydigsite:anvil_recipes} datapack registry
 * ({@code data/holeydigsite/holeydigsite/anvil_recipes/<name>.json}, synced):
 * <pre>{@code
 * { "order": 5, "category": "weapons", "ingots": 3, "gem": "none", "hits": 6, "result": "holeydigsite:smith_sword", "bonus": 0.4, "min_grams": 25,
 *   "gear": {"full_grams": 750, "base_damage": 3.0, "attack_speed": -2.4, "damage_scale": 1.0, "durability_scale": 1.0, "floor": 0.5} }
 * }</pre>
 * Every ingot slot takes exactly ONE whole ingot of at least {@code min_grams} (any metal, any weight; no pooling).
 *
 * @param order     position in the anvil's recipe cycle
 * @param category  radial-wheel category id (defaults to {@code "other"})
 * @param variant   optional lang key distinguishing alternative recipes for the same result
 * @param ingots    number of ingot slots (1-3)
 * @param gem       whether a cut gem is none / optional / required
 * @param hits      valid hammer hits needed
 * @param result    the piece item (one item per piece kind; its materials are in the {@link SmithData} component)
 * @param bonus     value bonus over the ingredients' sell value (0.25 = +25%)
 * @param gemBonus  bonus instead of {@code bonus} when the piece has a gem (optional)
 * @param minGrams  an ingot lighter than this is refused
 * @param gear      stats for weapons / tools; empty for jewellery
 * @param parts     the item model's layers in order (layer N of the piece's model = parts[N]): which ingot slot (1-3) or the gem
 *                  tints it, or none (plain wood / leather); empty = layer 0 main metal, layer 1 gem. Example:
 *                  {@code "parts": [{"name": "blade", "from": "ingot1"}, {"name": "grip", "from": "none"}]}
 * @param wear      armour of worn jewellery (see {@link Wear}); empty = not wearable
 */
public record AnvilRecipe(int order, String category, Optional<String> variant, int ingots, GemRule gem, int hits, Item result,
                          double bonus, Optional<Double> gemBonus, int minGrams, Optional<Gear> gear, List<Part> parts, Optional<Wear> wear) {
    public static final int MAX_INGOTS = 3;

    public AnvilRecipe(int order, int ingots, GemRule gem, int hits, Item result, double bonus, Optional<Double> gemBonus,
                       int minGrams, Optional<Gear> gear, List<Part> parts, Optional<Wear> wear) {
        this(order, "other", Optional.empty(), ingots, gem, hits, result, bonus, gemBonus, minGrams, gear, parts, wear);
    }

    public AnvilRecipe {
        category = category == null || category.isBlank() ? "other" : category;
        variant = variant != null && variant.isPresent() && !variant.get().isBlank() ? variant : Optional.empty();
    }

    /** Whether the recipe takes a gem. */
    public enum GemRule {
        NONE, OPTIONAL, REQUIRED;

        public static final Codec<GemRule> CODEC = Codec.STRING.comapFlatMap(s -> {
            for (GemRule r : values()) {
                if (r.name().toLowerCase(Locale.ROOT).equals(s)) {
                    return DataResult.success(r);
                }
            }
            return DataResult.error(() -> "gem must be none, optional or required: " + s);
        }, r -> r.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Stats of a weapon or tool where {@code full_grams} is the total ingot weight at which the metals' stats are fully reached,
     * and lighter pieces get a scaled fraction of them never below {@code floor}.
     */
    public record Gear(int fullGrams, double baseDamage, double attackSpeed, double damageScale, double durabilityScale, double floor) {
        public static final Codec<Gear> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1_000_000).fieldOf("full_grams").forGetter(Gear::fullGrams),
                Codec.doubleRange(0, 100).fieldOf("base_damage").forGetter(Gear::baseDamage),
                Codec.doubleRange(-4, 4).fieldOf("attack_speed").forGetter(Gear::attackSpeed),
                Codec.doubleRange(0, 10).optionalFieldOf("damage_scale", 1.0).forGetter(Gear::damageScale),
                Codec.doubleRange(0, 10).optionalFieldOf("durability_scale", 1.0).forGetter(Gear::durabilityScale),
                Codec.doubleRange(0, 1).optionalFieldOf("floor", 0.5).forGetter(Gear::floor)
        ).apply(i, Gear::new));
    }

    /** Multipliers of the metals' helmet-level armour and toughness for one way of wearing a piece. */
    public record WearSlot(double armor, double toughness) {
        public static final Codec<WearSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 10).fieldOf("armor").forGetter(WearSlot::armor),
                Codec.doubleRange(0, 10).fieldOf("toughness").forGetter(WearSlot::toughness)
        ).apply(i, WearSlot::new));
    }

    /**
     * Armour of a worn piece: the metals' armour / toughness averaged by grams, times
     * {@code floor + (1 - floor) * min(1, totalGrams / full_grams)}, times the multipliers of the slot it is worn in
     * ({@code head} = the head armour slot, {@code curio} = a Curios slot; absent = cannot be worn there).
     * Example: {@code "wear": {"full_grams": 400, "floor": 0.5, "head": {"armor": 1.0, "toughness": 1.0}, "curio": {"armor": 0.5, "toughness": 0.2}}}
     */
    public record Wear(int fullGrams, double floor, Optional<WearSlot> head, Optional<WearSlot> curio) {
        public static final Codec<Wear> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1_000_000).fieldOf("full_grams").forGetter(Wear::fullGrams),
                Codec.doubleRange(0, 1).optionalFieldOf("floor", 0.5).forGetter(Wear::floor),
                WearSlot.CODEC.optionalFieldOf("head").forGetter(Wear::head),
                WearSlot.CODEC.optionalFieldOf("curio").forGetter(Wear::curio)
        ).apply(i, Wear::new));
    }

    /** One drawn part of the piece: its name (documentation) and where its colour comes from. */
    public record Part(String name, Source source, int index) {
        /** Where a part's tint comes from. */
        public enum Source { INGOT, GEM, NONE }

        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Part::name),
                Codec.STRING.fieldOf("from").forGetter(Part::fromString)
        ).apply(i, Part::parse));

        /** "ingot1".."ingot3", "gem" or "none". */
        static Part parse(String name, String from) {
            if (from.startsWith("ingot") && from.length() == 6 && from.charAt(5) >= '1' && from.charAt(5) <= '0' + MAX_INGOTS) {
                return new Part(name, Source.INGOT, from.charAt(5) - '1');
            }
            return new Part(name, "gem".equals(from) ? Source.GEM : Source.NONE, 0);
        }

        String fromString() {
            return source == Source.INGOT ? "ingot" + (index + 1) : source.name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<AnvilRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("order", 0).forGetter(AnvilRecipe::order),
            Codec.STRING.optionalFieldOf("category", "other").forGetter(AnvilRecipe::category),
            Codec.STRING.optionalFieldOf("variant").forGetter(AnvilRecipe::variant),
            Codec.intRange(1, MAX_INGOTS).fieldOf("ingots").forGetter(AnvilRecipe::ingots),
            GemRule.CODEC.optionalFieldOf("gem", GemRule.NONE).forGetter(AnvilRecipe::gem),
            Codec.intRange(1, 64).fieldOf("hits").forGetter(AnvilRecipe::hits),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("result").forGetter(AnvilRecipe::result),
            Codec.doubleRange(0, 100).fieldOf("bonus").forGetter(AnvilRecipe::bonus),
            Codec.doubleRange(0, 100).optionalFieldOf("gem_bonus").forGetter(AnvilRecipe::gemBonus),
            Codec.intRange(1, 1_000_000).optionalFieldOf("min_grams", 1).forGetter(AnvilRecipe::minGrams),
            Gear.CODEC.optionalFieldOf("gear").forGetter(AnvilRecipe::gear),
            Part.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(AnvilRecipe::parts),
            Wear.CODEC.optionalFieldOf("wear").forGetter(AnvilRecipe::wear)
    ).apply(i, AnvilRecipe::new));

    /** The bonus that applies to a piece with or without a gem. */
    public double bonusFor(boolean hasGem) {
        return hasGem ? gemBonus.orElse(bonus) : bonus;
    }

    public Component baseName() {
        return new ItemStack(result).getHoverName();
    }

    @Nullable
    public Component variantLabel(@Nullable HolderLookup.Provider registries) {
        if (variant.isPresent()) {
            String v = variant.get();
            return Component.translatableWithFallback(v, v);
        }
        return null;
    }

    /** "Sword", "Necklace": the piece's plain item name (plus variant if present). */
    public Component name() {
        return name(null);
    }

    public Component name(@Nullable HolderLookup.Provider registries) {
        Component base = baseName();
        Component var = variantLabel(registries);
        return var != null ? Component.translatable("text.holeydigsite.recipe.variant", base, var) : base;
    }
}
