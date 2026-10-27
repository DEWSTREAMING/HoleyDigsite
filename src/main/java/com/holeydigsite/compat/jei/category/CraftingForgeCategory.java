package com.holeydigsite.compat.jei.category;

import com.holeydigsite.compat.jei.HoleyDigsiteJeiPlugin;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.compat.jei.display.ForgeRecipeRow;
import com.holeydigsite.forge.Metals;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class CraftingForgeCategory implements IRecipeCategory<ForgeRecipeRow> {
    private final IDrawable icon;
    private final IDrawableStatic arrow;

    public CraftingForgeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.CRAFTING_FORGE.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<ForgeRecipeRow> getRecipeType() {
        return HoleyDigsiteJeiPlugin.CRAFTING_FORGE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.holeydigsite.crafting_forge");
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
    public void setRecipe(IRecipeLayoutBuilder builder, ForgeRecipeRow recipe, IFocusGroup focuses) {
        int x = 8;
        // Metal inputs in row 1
        for (Map.Entry<ResourceLocation, Integer> m : recipe.metals().entrySet()) {
            ItemStack ingot = Metals.ingot(m.getKey(), m.getValue());
            builder.addInputSlot(x, 8)
                    .addItemStack(ingot)
                    .setStandardSlotBackground()
                    .addRichTooltipCallback((view, tooltip) ->
                            tooltip.add(JeiText.t("metal_grams", m.getValue(), Metals.name(m.getKey()))));
            x += 20;
        }

        // Part inputs in row 2
        x = 8;
        for (ItemStack part : recipe.parts()) {
            builder.addInputSlot(x, 32)
                    .addItemStack(part)
                    .setStandardSlotBackground();
            x += 20;
        }

        // Output slot
        builder.addOutputSlot(120, 18)
                .addItemStack(recipe.result())
                .setOutputSlotBackground();
    }

    @Override
    public void draw(ForgeRecipeRow recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, 88, 20);

        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, recipe.tierLabel(), 116, 46, 0x666666, false);

        if (recipe.variant().isPresent()) {
            Component var = recipe.variant().get();
            guiGraphics.drawString(font, var, 8, 54, 0x888888, false);
        }
    }
}
