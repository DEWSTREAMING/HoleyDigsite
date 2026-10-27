package com.holeydigsite.compat.jei.category;

import com.holeydigsite.compat.jei.HoleyDigsiteJeiPlugin;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.compat.jei.display.PanningRow;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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

public class PanningCategory implements IRecipeCategory<PanningRow> {
    private final IDrawable icon;

    public PanningCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.PAN.get()));
    }

    @Override
    public RecipeType<PanningRow> getRecipeType() {
        return HoleyDigsiteJeiPlugin.PANNING;
    }

    @Override
    public Component getTitle() {
        return JeiText.t("panning_title");
    }

    @Override
    public int getWidth() {
        return 168;
    }

    @Override
    public int getHeight() {
        return 120;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PanningRow recipe, IFocusGroup focuses) {
        // Clump input slot
        builder.addInputSlot(6, 8)
                .addItemStack(recipe.clumpStack())
                .setStandardSlotBackground();

        // Base drops grid
        int slotX = 36, slotY = 8;
        int col = 0;
        for (PanningRow.DropEntry drop : recipe.baseDrops()) {
            int x = slotX + (col % 6) * 20;
            int y = slotY + (col / 6) * 20;
            builder.addOutputSlot(x, y)
                    .addItemStack(drop.stack())
                    .setOutputSlotBackground()
                    .addRichTooltipCallback((view, tooltip) -> {
                        String count = drop.minCount() == drop.maxCount()
                                ? String.valueOf(drop.minCount())
                                : drop.minCount() + "-" + drop.maxCount();
                        tooltip.add(JeiText.t("panning_count", count, drop.chanceString()));
                    });
            col++;
        }

        // Upgrade seed drops row
        if (!recipe.seedDrops().isEmpty()) {
            builder.addInputSlot(6, 68)
                    .addItemStack(new ItemStack(ModItems.SEED_SCREEN.get()))
                    .setStandardSlotBackground()
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(JeiText.t("panning_seed_upgrade")));

            int scol = 0;
            for (PanningRow.DropEntry drop : recipe.seedDrops()) {
                int x = 36 + (scol % 6) * 20;
                builder.addOutputSlot(x, 68)
                        .addItemStack(drop.stack())
                        .setOutputSlotBackground()
                        .addRichTooltipCallback((view, tooltip) ->
                                tooltip.add(JeiText.t("panning_seed_chance", drop.chanceString())));
                scol++;
            }
        }

        // Upgrade bonus drops row (e.g. Fine Mesh Screen Topaz)
        if (!recipe.bonusDrops().isEmpty()) {
            builder.addInputSlot(6, 88)
                    .addItemStack(new ItemStack(ModItems.FINE_MESH_SCREEN.get()))
                    .setStandardSlotBackground()
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(JeiText.t("panning_mesh_upgrade")));

            int bcol = 0;
            for (PanningRow.DropEntry drop : recipe.bonusDrops()) {
                int x = 36 + (bcol % 6) * 20;
                builder.addOutputSlot(x, 88)
                        .addItemStack(drop.stack())
                        .setOutputSlotBackground()
                        .addRichTooltipCallback((view, tooltip) ->
                                tooltip.add(JeiText.t("panning_mesh_chance", drop.chanceString())));
                bcol++;
            }
        }
    }

    @Override
    public void draw(PanningRow recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, recipe.clumpName(), 6, 0, 0x444444, false);

        int y = 108;
        if (!recipe.upgradeNotes().isEmpty()) {
            Component note = recipe.upgradeNotes().get(0);
            guiGraphics.drawString(font, note, 6, y, 0x666666, false);
        }
    }
}
