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
 * Smithing data for a material from the datapack registry.
 *
 * @param color      RGB tint of pieces made of this material
 * @param damage     attack damage bonus at full weight
 * @param speed      mining speed at full weight
 * @param durability uses at full weight, where zero means not a metal
 * @param armor      armour points at full weight
 * @param toughness  armour toughness at full weight
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

    /** Checks if ingots of this material can be smithed. */
    public boolean isMetal() {
        return durability > 0;
    }

    /** Retrieves the material entry or null. */
    @Nullable
    public static SmithMaterial get(@Nullable HolderLookup.Provider registries, @Nullable ResourceLocation material) {
        if (registries == null || material == null) {
            return null;
        }
        return registries.lookup(KEY).flatMap(r -> r.get(ResourceKey.create(KEY, material))).map(h -> h.value()).orElse(null);
    }
}
