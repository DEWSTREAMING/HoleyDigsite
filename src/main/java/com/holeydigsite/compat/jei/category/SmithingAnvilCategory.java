package com.holeydigsite.compat.jei.category;

import com.holeydigsite.compat.jei.HoleyDigsiteJeiPlugin;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.compat.jei.display.AnvilRecipeRow;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.smith.AnvilRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class SmithingAnvilCategory implements IRecipeCategory<AnvilRecipeRow> {
    private final IDrawable icon;
    private final IDrawableStatic arrow;

    public SmithingAnvilCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.SMITHING_ANVIL.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<AnvilRecipeRow> getRecipeType() {
        return HoleyDigsiteJeiPlugin.SMITHING_ANVIL;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.holeydigsite.smithing_anvil");
    }

    @Override
    public int getWidth() {
        return 160;
    }

    @Override
    public int getHeight() {
        return 68;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AnvilRecipeRow recipe, IFocusGroup focuses) {
        int x = 8;
        List<ItemStack> ingots = AnvilRecipeRow.sampleIngots(recipe.minGrams());
        for (int i = 0; i < recipe.ingots(); i++) {
            builder.addInputSlot(x, 12)
                    .addItemStacks(ingots)
                    .setStandardSlotBackground()
                    .addRichTooltipCallback((view, tooltip) ->
                            tooltip.add(JeiText.t("anvil_ingot", recipe.minGrams())));
            x += 20;
        }

        if (recipe.gemRule() != AnvilRecipe.GemRule.NONE) {
            List<ItemStack> gems = AnvilRecipeRow.sampleGems();
            String req = recipe.gemRule() == AnvilRecipe.GemRule.REQUIRED ? "anvil_gem_required" : "anvil_gem_optional";
            builder.addInputSlot(x, 12)
                    .addItemStacks(gems)
                    .setStandardSlotBackground()
                    .addRichTooltipCallback((view, tooltip) ->
                            tooltip.add(JeiText.t(req)));
        }

        builder.addOutputSlot(120, 12)
                .addItemStack(recipe.result())
                .setOutputSlotBackground();
    }

    @Override
    public void draw(AnvilRecipeRow recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, 88, 14);

        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, JeiText.t("anvil_hits", recipe.hits()), 88, 34, 0x666666, false);
        guiGraphics.drawString(font, recipe.note(), 8, 48, 0x444444, false);
    }
}
