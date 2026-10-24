package com.holeydigsite.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * What a resource item is made of: one entry of the {@code holeydigsite:resource_weights} item data map
 * ({@code data/holeydigsite/data_maps/item/resource_weights.json}).
 *
 * @param material  material id, e.g. {@code holeydigsite:folkiron}; its price per gram is in the
 *                  {@link SellValue sell_values} registry
 * @param grams     fixed weight of ONE item in grams (shown in the tooltip, used for selling and, in phase 8, melting)
 * @param form      {@code "raw"} (chunks, gems) or {@code "ingot"} (phase 8 - sells at the ingot price per gram)
 * @param meltable  false for gems (Rubies): the phase 8 crucible will refuse them
 */
public record ResourceInfo(ResourceLocation material, int grams, String form, boolean meltable) {
    public static final String RAW = "raw";
    public static final String INGOT = "ingot";
    /** Phase 12: uncut gems (half price until a Gem Cutter cuts them). */
    public static final String UNCUT = "uncut";

    /** Phase 18: crops and foods (sold by the item, 1 g = 1 piece; no weight shown). */
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
