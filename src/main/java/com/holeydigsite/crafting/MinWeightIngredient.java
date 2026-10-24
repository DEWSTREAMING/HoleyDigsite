package com.holeydigsite.crafting;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.registry.ModIngredients;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

/**
 * Recipe ingredient "at least X g of this item" (Phase 9, for the Phase 10 Tier 2 recipes):
 * <pre>{ "type": "holeydigsite:min_weight", "item": "holeydigsite:folkiron_ingot", "min_grams": 50 }</pre>
 * Matches the item when {@link Selling#grams} (the ingot's {@code holeydigsite:weight}) is at least {@code min_grams}.
 * Note: a normal crafting grid uses up the WHOLE ingot, even if it is heavier than needed - cast the right size.
 */
public record MinWeightIngredient(Holder<Item> item, int minGrams) implements ICustomIngredient {
    public static final MapCodec<MinWeightIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(MinWeightIngredient::item),
            Codec.intRange(1, 1_000_000).fieldOf("min_grams").forGetter(MinWeightIngredient::minGrams)
    ).apply(i, MinWeightIngredient::new));

    @Override
    public boolean test(ItemStack stack) {
        return stack.is(item) && Selling.grams(stack) >= minGrams;
    }

    /** Shown in recipe viewers: one ingot of exactly the minimum weight. */
    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(IngotItem.of(item.value(), minGrams));
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return ModIngredients.MIN_WEIGHT.get();
    }
}
