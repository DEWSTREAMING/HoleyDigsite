package com.holeydigsite.smith;

import com.holeydigsite.HoleyDigsite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Smithing data of one material, an entry of the {@code holeydigsite:smithing_materials} datapack registry
 * ({@code data/holeydigsite/holeydigsite/smithing_materials/<material>.json}, the file name is the material id; synced).
 * Metals have stats, gems only a colour.
 *
 * @param color      RGB tint of pieces made mainly of this material (the gem dot of jewellery)
 * @param damage     attack damage bonus of a piece of this metal at full weight (iron 2, diamond 3, netherite 4)
 * @param speed      axe mining speed at full weight (iron 6, diamond 8, netherite 9)
 * @param durability uses at full weight (iron 250, diamond 1561, netherite 2031); 0 = not a metal (a gem)
 * @param armor      armour points of a helmet-level piece at full weight (iron helmet 2, diamond 3, netherite 3); Phase 20
 * @param toughness  armour toughness of a helmet-level piece at full weight (iron 0, diamond 2, netherite 3); Phase 20
 */
public record SmithMaterial(int color, double damage, double speed, int durability, double armor, double toughness) {
    public static final ResourceKey<Registry<SmithMaterial>> KEY = ResourceKey.createRegistryKey(HoleyDigsite.id("smithing_materials"));

    public static final Codec<SmithMaterial> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("color").forGetter(SmithMaterial::color),
            Codec.doubleRange(0, 1000).optionalFieldOf("damage", 0.0).forGetter(SmithMaterial::damage),
            Codec.doubleRange(0, 1000).optionalFieldOf("speed", 0.0).forGetter(SmithMaterial::speed),
            Codec.intRange(0, 1_000_000).optionalFieldOf("durability", 0).forGetter(SmithMaterial::durability),
            Codec.doubleRange(0, 100).optionalFieldOf("armor", 0.0).forGetter(SmithMaterial::armor),
            Codec.doubleRange(0, 100).optionalFieldOf("toughness", 0.0).forGetter(SmithMaterial::toughness)
    ).apply(i, SmithMaterial::new));

    /** True if ingots of this material can be smithed (a gem has no durability). */
    public boolean isMetal() {
        return durability > 0;
    }

    /** The entry of {@code material}, or null. */
    @Nullable
    public static SmithMaterial get(@Nullable HolderLookup.Provider registries, @Nullable ResourceLocation material) {
        if (registries == null || material == null) {
            return null;
        }
        return registries.lookup(KEY).flatMap(r -> r.get(ResourceKey.create(KEY, material))).map(h -> h.value()).orElse(null);
    }
}
