package com.holeydigsite.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

/**
 * Defines what a resource item is made of using an entry of the {@code holeydigsite:resource_weights} item data map
 * ({@code data/holeydigsite/data_maps/item/resource_weights.json}).
 *
 * @param material  material ID, e.g. {@code holeydigsite:folkiron}, with its price per gram in the
 *                  {@link SellValue sell_values} registry
 * @param grams     fixed weight of one item in grams (or target average weight when min/max are present),
 *                  shown in the tooltip and used for selling and melting
 * @param form      {@code "raw"} (chunks, gems) or {@code "ingot"} (sells at the ingot price per gram)
 * @param meltable  false for gems like rubies, which the crucible refuses
 * @param min       minimum weight in whole grams (defaults to {@code grams})
 * @param max       maximum weight in whole grams (defaults to {@code grams})
 * @param odds      relative odds for each integer weight from {@code min} to {@code max} (defaults to empty, meaning even odds)
 */
public record ResourceInfo(ResourceLocation material, int grams, String form, boolean meltable, int min, int max, List<Integer> odds) {
    public static final String RAW = "raw";
    public static final String INGOT = "ingot";
    /** Uncut gems, worth half price until cut. */
    public static final String UNCUT = "uncut";

    /** Crops and foods sold by the item where 1g equals 1 piece and no weight is shown. */
    public static final String CROP = "crop";

    public ResourceInfo(ResourceLocation material, int grams, String form, boolean meltable) {
        this(material, grams, form, meltable, grams, grams, List.of());
    }

    public ResourceInfo(ResourceLocation material, int grams, String form, boolean meltable, int min, int max) {
        this(material, grams, form, meltable, min, max, List.of());
    }

    public static final Codec<ResourceInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("material").forGetter(ResourceInfo::material),
            Codec.intRange(1, 1_000_000).fieldOf("grams").forGetter(ResourceInfo::grams),
            Codec.STRING.optionalFieldOf("form", RAW).forGetter(ResourceInfo::form),
            Codec.BOOL.optionalFieldOf("meltable", true).forGetter(ResourceInfo::meltable),
            Codec.intRange(1, 1_000_000).optionalFieldOf("min").forGetter(r -> r.hasRange() ? Optional.of(r.min()) : Optional.empty()),
            Codec.intRange(1, 1_000_000).optionalFieldOf("max").forGetter(r -> r.hasRange() ? Optional.of(r.max()) : Optional.empty()),
            Codec.INT.listOf().optionalFieldOf("odds", List.of()).forGetter(ResourceInfo::odds)
    ).apply(i, (mat, g, f, m, minOpt, maxOpt, oddsList) -> new ResourceInfo(mat, g, f, m, minOpt.orElse(g), maxOpt.orElse(g), oddsList)));

    public boolean hasRange() {
        return min != max;
    }

    public boolean isUncut() {
        return UNCUT.equals(form);
    }

    public boolean isCrop() {
        return CROP.equals(form);
    }

    public boolean isIngot() {
        return INGOT.equals(form);
    }

    /**
     * Rolls a weight within [min, max] according to the configured {@link #odds()} or uniform odds if not specified.
     */
    public int rollWeight(RandomSource random) {
        if (min == max) {
            return grams;
        }
        int range = max - min + 1;
        if (odds.isEmpty() || odds.size() != range) {
            return min + random.nextInt(range);
        }
        int totalWeight = 0;
        for (int w : odds) {
            totalWeight += w;
        }
        if (totalWeight <= 0) {
            return min + random.nextInt(range);
        }
        int pick = random.nextInt(totalWeight);
        int acc = 0;
        for (int i = 0; i < odds.size(); i++) {
            acc += odds.get(i);
            if (pick < acc) {
                return min + i;
            }
        }
        return max;
    }

    /** Theoretical expected weight in grams. */
    public double expectedWeight() {
        if (min == max) return grams;
        int range = max - min + 1;
        if (odds.isEmpty() || odds.size() != range) {
            return (min + max) / 2.0;
        }
        double sum = 0;
        int totalWeight = 0;
        for (int i = 0; i < odds.size(); i++) {
            int w = odds.get(i);
            sum += (min + i) * w;
            totalWeight += w;
        }
        return totalWeight > 0 ? sum / totalWeight : (min + max) / 2.0;
    }
}
