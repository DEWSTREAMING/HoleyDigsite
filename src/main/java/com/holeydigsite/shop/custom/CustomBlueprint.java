package com.holeydigsite.shop.custom;

import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 21d: the data component ({@code holeydigsite:custom_blueprint}) of a Blueprint for an operator-made display entry. It
 * names the entry by its stable id and revision and carries a snapshot of what it buys (template, count, price) so clients
 * can show the tooltip and the Buy Pad's total. The server only honours it while {@code rev} still matches the entry.
 */
public record CustomBlueprint(String id, int rev, ItemStack template, int count, long price) {
    public static final Codec<CustomBlueprint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(CustomBlueprint::id),
            Codec.INT.fieldOf("rev").forGetter(CustomBlueprint::rev),
            ItemStack.CODEC.fieldOf("template").forGetter(CustomBlueprint::template),
            Codec.INT.fieldOf("count").forGetter(CustomBlueprint::count),
            Codec.LONG.fieldOf("price").forGetter(CustomBlueprint::price)
    ).apply(i, CustomBlueprint::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CustomBlueprint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CustomBlueprint::id,
            ByteBufCodecs.VAR_INT, CustomBlueprint::rev,
            ItemStack.STREAM_CODEC, CustomBlueprint::template,
            ByteBufCodecs.VAR_INT, CustomBlueprint::count,
            ByteBufCodecs.VAR_LONG, CustomBlueprint::price,
            CustomBlueprint::new);

    /** The custom data of a blueprint stack, or null (not a blueprint / a normal catalogue blueprint). */
    @Nullable
    public static CustomBlueprint of(ItemStack stack) {
        return stack.is(ModItems.BLUEPRINT.get()) ? stack.get(ModDataComponents.CUSTOM_BLUEPRINT.get()) : null;
    }

    /** A Blueprint stack for the entry {@code id}. */
    public static ItemStack stackFor(String id, CustomEntry entry, int amount) {
        ItemStack bp = new ItemStack(ModItems.BLUEPRINT.get(), amount);
        bp.set(ModDataComponents.CUSTOM_BLUEPRINT.get(),
                new CustomBlueprint(id, entry.rev(), entry.template().copyWithCount(1), entry.count(), entry.price()));
        return bp;
    }
}
