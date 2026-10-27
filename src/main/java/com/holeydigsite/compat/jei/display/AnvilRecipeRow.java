package com.holeydigsite.compat.jei.display;

import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.forge.Metals;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.smith.AnvilRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record AnvilRecipeRow(
        ResourceLocation id,
        ItemStack result,
        String category,
        Optional<Component> variant,
        int ingots,
        int minGrams,
        AnvilRecipe.GemRule gemRule,
        int hits,
        double bonus,
        Optional<Double> gemBonus,
        Optional<AnvilRecipe.Gear> gear,
        Optional<AnvilRecipe.Wear> wear,
        Component note
) {
    public static List<AnvilRecipeRow> loadAll(@Nullable HolderLookup.Provider registries) {
        List<AnvilRecipes.Entry> entries = AnvilRecipes.all(registries);
        List<AnvilRecipeRow> rows = new ArrayList<>(entries.size());
        for (AnvilRecipes.Entry e : entries) {
            AnvilRecipe r = e.recipe();
            ItemStack result = new ItemStack(r.result());
            Component variant = r.variantLabel(registries);
            Component note = createNote(r);
            rows.add(new AnvilRecipeRow(
                    e.id(),
                    result,
                    r.category(),
                    Optional.ofNullable(variant),
                    r.ingots(),
                    r.minGrams(),
                    r.gem(),
                    r.hits(),
                    r.bonus(),
                    r.gemBonus(),
                    r.gear(),
                    r.wear(),
                    note
            ));
        }
        return List.copyOf(rows);
    }

    private static Component createNote(AnvilRecipe r) {
        int basePct = (int) Math.round(r.bonus() * 100);
        if (r.gear().isPresent()) {
            int fullGrams = r.gear().get().fullGrams();
            return JeiText.t("anvil_gear", fullGrams, basePct);
        }
        if (r.wear().isPresent()) {
            int fullGrams = r.wear().get().fullGrams();
            if (r.gemBonus().isPresent()) {
                int gemPct = (int) Math.round(r.gemBonus().get() * 100);
                return JeiText.t("anvil_wear_gem", fullGrams, basePct, gemPct);
            }
            return JeiText.t("anvil_wear", fullGrams, basePct);
        }
        return JeiText.t("anvil_bonus", basePct);
    }

    public static List<ItemStack> sampleIngots(int minGrams) {
        return List.of(
                Metals.ingot(com.holeydigsite.HoleyDigsite.id("folkiron"), minGrams),
                Metals.ingot(com.holeydigsite.HoleyDigsite.id("goldchro"), minGrams),
                Metals.ingot(com.holeydigsite.HoleyDigsite.id("orchremia"), minGrams),
                Metals.ingot(com.holeydigsite.HoleyDigsite.id("silvane"), minGrams)
        );
    }

    public static List<ItemStack> sampleGems() {
        return List.of(
                new ItemStack(ModItems.RUBY_SMALL.get()),
                new ItemStack(ModItems.RUBY_MEDIUM.get()),
                new ItemStack(ModItems.RUBY_LARGE.get()),
                new ItemStack(ModItems.SAPPHIRE_SMALL.get()),
                new ItemStack(ModItems.SAPPHIRE_MEDIUM.get()),
                new ItemStack(ModItems.SAPPHIRE_LARGE.get()),
                new ItemStack(ModItems.TOPAZ_SMALL.get()),
                new ItemStack(ModItems.TOPAZ_MEDIUM.get()),
                new ItemStack(ModItems.TOPAZ_LARGE.get())
        );
    }
}
