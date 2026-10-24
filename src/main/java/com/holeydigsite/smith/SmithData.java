package com.holeydigsite.smith;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Data component storing ingots and gems comprising a smithed piece.
 */
public record SmithData(List<Ingot> ingots, Optional<Gem> gem) {
    /** An individual ingot component with metal and grams. */
    public record Ingot(ResourceLocation metal, int grams) {
        public static final Codec<Ingot> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("metal").forGetter(Ingot::metal),
                Codec.intRange(1, 1_000_000).fieldOf("grams").forGetter(Ingot::grams)
        ).apply(i, Ingot::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ingot> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Ingot::metal, ByteBufCodecs.VAR_INT, Ingot::grams, Ingot::new);
    }

    /** An individual gem component with material and grams. */
    public record Gem(ResourceLocation material, int grams) {
        public static final Codec<Gem> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("material").forGetter(Gem::material),
                Codec.intRange(1, 1_000_000).fieldOf("grams").forGetter(Gem::grams)
        ).apply(i, Gem::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Gem> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Gem::material, ByteBufCodecs.VAR_INT, Gem::grams, Gem::new);
    }

    public static final Codec<SmithData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Ingot.CODEC.listOf().fieldOf("ingots").forGetter(SmithData::ingots),
            Gem.CODEC.optionalFieldOf("gem").forGetter(SmithData::gem)
    ).apply(i, SmithData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmithData> STREAM_CODEC = StreamCodec.composite(
            Ingot.STREAM_CODEC.apply(ByteBufCodecs.list()), SmithData::ingots,
            ByteBufCodecs.optional(Gem.STREAM_CODEC), SmithData::gem,
            SmithData::new);

    public SmithData {
        ingots = List.copyOf(ingots);
    }

    public int ingotGrams() {
        int n = 0;
        for (Ingot i : ingots) {
            n += i.grams();
        }
        return n;
    }

    /** The metal with the most grams in total. */
    @Nullable
    public ResourceLocation mainMetal() {
        ResourceLocation best = null;
        int bestGrams = -1;
        for (Ingot i : ingots) {
            int g = 0;
            for (Ingot j : ingots) {
                if (j.metal().equals(i.metal())) {
                    g += j.grams();
                }
            }
            if (g > bestGrams) {
                best = i.metal();
                bestGrams = g;
            }
        }
        return best;
    }
}
