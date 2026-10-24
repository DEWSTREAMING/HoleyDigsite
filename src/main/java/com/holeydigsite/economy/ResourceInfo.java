package com.holeydigsite.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * Defines what a resource item is made of using an entry of the {@code holeydigsite:resource_weights} item data map
 * ({@code data/holeydigsite/data_maps/item/resource_weights.json}).
 *
 * @param material  material ID, e.g. {@code holeydigsite:folkiron}, with its price per gram in the
 *                  {@link SellValue sell_values} registry
 * @param grams     fixed weight of one item in grams, shown in the tooltip and used for selling and melting
 * @param form      {@code "raw"} (chunks, gems) or {@code "ingot"} (sells at the ingot price per gram)
 * @param meltable  false for gems like rubies, which the crucible refuses
 */
public record ResourceInfo(ResourceLocation material, int grams, String form, boolean meltable) {
    public static final String RAW = "raw";
    public static final String INGOT = "ingot";
    /** Uncut gems, worth half price until cut. */
    public static final String UNCUT = "uncut";

    /** Crops and foods sold by the item where 1g equals 1 piece and no weight is shown. */
    public static final String CROP = "crop";

    public static final Codec<ResourceInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("material").forGetter(ResourceInfo::material),
            Codec.intRange(1, 1_000_000).fieldOf("grams").forGetter(ResourceInfo::grams),
            Codec.STRING.optionalFieldOf("form", RAW).forGetter(ResourceInfo::form),
            Codec.BOOL.optionalFieldOf("meltable", true).forGetter(ResourceInfo::meltable)
    ).apply(i, ResourceInfo::new));

    public boolean isUncut() {
        return UNCUT.equals(form);
    }

    public boolean isCrop() {
        return CROP.equals(form);
    }

    public boolean isIngot() {
        return INGOT.equals(form);
    }
}
