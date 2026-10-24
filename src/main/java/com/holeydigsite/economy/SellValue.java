package com.holeydigsite.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Price of one material, an entry of the {@code holeydigsite:sell_values} datapack registry
 * ({@code data/<namespace>/holeydigsite/sell_values/<material>.json}, the file name is the material id).
 * Synced to clients, so tooltips can show "Sells for ...".
 *
 * @param copperPerGram      copper coins per gram of raw material (chunks, gems)
 * @param ingotCopperPerGram copper per gram for ingots of this material (phase 8); defaults to the raw price
 * @param uncutCopperPerGram copper per gram for uncut gems (Phase 12); defaults to half the raw (cut) price
 */
public record SellValue(double copperPerGram, Optional<Double> ingotCopperPerGram, Optional<Double> uncutCopperPerGram) {
    public static final Codec<SellValue> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0, 1_000_000).fieldOf("copper_per_gram").forGetter(SellValue::copperPerGram),
            Codec.doubleRange(0, 1_000_000).optionalFieldOf("ingot_copper_per_gram").forGetter(SellValue::ingotCopperPerGram),
            Codec.doubleRange(0, 1_000_000).optionalFieldOf("uncut_copper_per_gram").forGetter(SellValue::uncutCopperPerGram)
    ).apply(i, SellValue::new));

    public double perGram(boolean ingot) {
        return ingot ? ingotCopperPerGram.orElse(copperPerGram) : copperPerGram;
    }

    /** Price per gram for an item of this {@link ResourceInfo#form() form} (raw, ingot or uncut). */
    public double perGram(String form) {
        if (ResourceInfo.UNCUT.equals(form)) {
            return uncutCopperPerGram.orElse(copperPerGram / 2);
        }
        return perGram(ResourceInfo.INGOT.equals(form));
    }
}
