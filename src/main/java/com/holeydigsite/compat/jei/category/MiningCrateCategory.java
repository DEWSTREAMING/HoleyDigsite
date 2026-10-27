package com.holeydigsite.compat.jei.category;

import com.holeydigsite.compat.jei.HoleyDigsiteJeiPlugin;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.compat.jei.display.CrateOddsRow;
import com.holeydigsite.crate.CrateDefs;
import com.holeydigsite.registry.ModBlocks;
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

public class MiningCrateCategory implements IRecipeCategory<CrateOddsRow> {
    private final IDrawable icon;

    public MiningCrateCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.WOODEN_CRATE.get()));
    }

    @Override
    public RecipeType<CrateOddsRow> getRecipeType() {
        return HoleyDigsiteJeiPlugin.MINING_CRATES;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("tooltip.holeydigsite.crate");
    }

    @Override
    public int getWidth() {
        return 168;
    }

    @Override
    public int getHeight() {
        return 126;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CrateOddsRow recipe, IFocusGroup focuses) {
        builder.addInputSlot(6, 6)
                .addItemStack(recipe.crateStack())
                .setStandardSlotBackground();

        int y = 28;
        for (CrateOddsRow.RarityPool pool : recipe.pools()) {
            int x = 6;
            for (CrateOddsRow.RewardEntry reward : pool.rewards()) {
                builder.addOutputSlot(x, y)
                        .addItemStacks(reward.variants())
                        .setOutputSlotBackground()
                        .addRichTooltipCallback((view, tooltip) -> {
                            tooltip.add(Component.translatable("rarity.holeydigsite." + pool.rarity()).withStyle(pool.color()));
                            tooltip.add(reward.label());
                            tooltip.add(JeiText.t("crate_weight", reward.weight()));
                            if (reward.kind() == CrateDefs.Kind.SKIN) {
                                tooltip.add(JeiText.t("crate_skin_split"));
                            }
                        });
                x += 18;
                if (x > 150) break;
            }
            y += 24;
        }
    }

    @Override
    public void draw(CrateOddsRow recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, recipe.crateName(), 30, 6, 0x333333, false);
        guiGraphics.drawString(font, recipe.oddsSummary(), 30, 16, 0x666666, false);

        int y = 28;
        for (CrateOddsRow.RarityPool pool : recipe.pools()) {
            String pct = pool.percent() == Math.rint(pool.percent())
                    ? String.valueOf((int) pool.percent())
                    : String.format(java.util.Locale.ROOT, "%.1f", pool.percent());
            Component label = Component.translatable("rarity.holeydigsite." + pool.rarity())
                    .append(" " + pct + "%")
                    .withStyle(pool.color());
            guiGraphics.drawString(font, label, 120, y + 4, 0x333333, false);
            y += 24;
        }

        if (recipe.panFindOneIn() > 0) {
            guiGraphics.drawString(font, JeiText.t("crate_find_rate", recipe.panFindOneIn()), 6, 114, 0x777777, false);
        }
    }
}
