package com.holeydigsite.registry;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.crafting.MinWeightIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Custom recipe ingredient types. */
public final class ModIngredients {
    private ModIngredients() {}

    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, HoleyDigsite.MODID);

    /** {@code holeydigsite:min_weight}, at least X grams of an ingot, see {@link MinWeightIngredient}. */
    public static final DeferredHolder<IngredientType<?>, IngredientType<MinWeightIngredient>> MIN_WEIGHT =
            INGREDIENT_TYPES.register("min_weight", () -> new IngredientType<>(MinWeightIngredient.CODEC));
}
