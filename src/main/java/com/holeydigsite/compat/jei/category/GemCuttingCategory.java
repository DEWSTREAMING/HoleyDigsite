package com.holeydigsite.compat.jei.category;

import com.holeydigsite.compat.jei.HoleyDigsiteJeiPlugin;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.compat.jei.display.GemCuttingRow;
import com.holeydigsite.registry.ModBlocks;
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

public class GemCuttingCategory implements IRecipeCategory<GemCuttingRow> {
    private final IDrawable icon;
    private final IDrawableStatic arrow;

    public GemCuttingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.GEM_CUTTER.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<GemCuttingRow> getRecipeType() {
        return HoleyDigsiteJeiPlugin.GEM_CUTTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.holeydigsite.gem_cutter");
    }

    @Override
    public int getWidth() {
        return 160;
    }

    @Override
    public int getHeight() {
        return 52;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, GemCuttingRow recipe, IFocusGroup focuses) {
        builder.addInputSlot(26, 10)
                .addItemStack(recipe.uncut())
                .setStandardSlotBackground()
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(JeiText.t("gem_uncut", recipe.uncutPercent())));

        builder.addOutputSlot(110, 10)
                .addItemStack(recipe.cut())
                .setOutputSlotBackground()
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(JeiText.t("gem_cut")));
    }

    @Override
    public void draw(GemCuttingRow recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, 66, 12);

        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, recipe.note(), 8, 38, 0x555555, false);
    }
}
