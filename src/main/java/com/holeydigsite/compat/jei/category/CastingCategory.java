package com.holeydigsite.compat.jei.category;

import com.holeydigsite.compat.jei.HoleyDigsiteJeiPlugin;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.compat.jei.display.CastingRow;
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

public class CastingCategory implements IRecipeCategory<CastingRow> {
    private final IDrawable icon;
    private final IDrawableStatic arrow;

    public CastingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.MOLD.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<CastingRow> getRecipeType() {
        return HoleyDigsiteJeiPlugin.CASTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.holeydigsite.mold");
    }

    @Override
    public int getWidth() {
        return 160;
    }

    @Override
    public int getHeight() {
        return 58;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CastingRow recipe, IFocusGroup focuses) {
        builder.addInputSlot(24, 10)
                .addItemStack(recipe.moltenInput())
                .setStandardSlotBackground()
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(JeiText.t("casting_molten", recipe.metalName(), recipe.grams())));

        builder.addOutputSlot(108, 10)
                .addItemStack(recipe.outputIngot())
                .setOutputSlotBackground()
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(JeiText.t("casting_cast", recipe.grams(), recipe.metalName())));
    }

    @Override
    public void draw(CastingRow recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, 64, 12);

        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, recipe.grams() + "g", 132, 16, 0x666666, false);
        guiGraphics.drawString(font, recipe.moldNote(), 8, 42, 0x555555, false);
    }
}
