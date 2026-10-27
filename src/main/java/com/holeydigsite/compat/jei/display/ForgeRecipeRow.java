package com.holeydigsite.compat.jei.display;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.forge.ForgeRecipe;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.tier.Tiers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ForgeRecipeRow(
        ResourceLocation id,
        ItemStack result,
        String category,
        Optional<Component> variant,
        Map<ResourceLocation, Integer> metals,
        List<ItemStack> parts,
        Component tierLabel
) {
    public static List<ForgeRecipeRow> loadAll(@Nullable HolderLookup.Provider registries) {
        List<ForgeRecipes.Entry> entries = ForgeRecipes.all(registries);
        List<ForgeRecipeRow> rows = new ArrayList<>(entries.size());
        for (ForgeRecipes.Entry e : entries) {
            ForgeRecipe r = e.recipe();
            ItemStack res = r.resultStack();
            List<ItemStack> partStacks = r.parts().stream()
                    .map(p -> new ItemStack(p.item(), p.count()))
                    .toList();
            Component variant = r.variantLabel(registries);
            Component tier = JeiText.t("tier", tierMark(e));
            rows.add(new ForgeRecipeRow(
                    e.id(),
                    res,
                    r.category(),
                    Optional.ofNullable(variant),
                    r.metals(),
                    partStacks,
                    tier
            ));
        }
        return List.copyOf(rows);
    }

    public static String tierMark(ForgeRecipes.Entry entry) {
        String path = BuiltInRegistries.ITEM.getKey(entry.recipe().result().item()).getPath();
        try {
            return Tiers.def(path).mark();
        } catch (Exception ignored) {
            if (entry.recipe().metals().containsKey(HoleyDigsite.id("orchremia"))
                    || entry.recipe().metals().containsKey(HoleyDigsite.id("silvane"))) {
                return "III";
            }
            return "II";
        }
    }
}
