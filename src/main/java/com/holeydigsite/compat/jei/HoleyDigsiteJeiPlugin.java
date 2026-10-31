package com.holeydigsite.compat.jei;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.contract.ContractDefs;
import com.holeydigsite.compat.jei.category.CastingCategory;
import com.holeydigsite.compat.jei.category.CraftingForgeCategory;
import com.holeydigsite.compat.jei.category.CrucibleMeltingCategory;
import com.holeydigsite.compat.jei.category.GemCuttingCategory;
import com.holeydigsite.compat.jei.category.MiningCrateCategory;
import com.holeydigsite.compat.jei.category.PanningCategory;
import com.holeydigsite.compat.jei.category.SmithingAnvilCategory;
import com.holeydigsite.compat.jei.display.AnvilRecipeRow;
import com.holeydigsite.compat.jei.display.CastingRow;
import com.holeydigsite.compat.jei.display.CrateOddsRow;
import com.holeydigsite.compat.jei.display.CrucibleMeltingRow;
import com.holeydigsite.compat.jei.display.ForgeRecipeRow;
import com.holeydigsite.compat.jei.display.GemCuttingRow;
import com.holeydigsite.compat.jei.display.PanningRow;
import com.holeydigsite.compat.jei.display.ShopInfoRow;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.item.PanBlockItem;
import com.holeydigsite.item.ShopBlockItem;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shovel.ShovelHelper;
import com.holeydigsite.skin.SkinKitItem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JeiPlugin
public class HoleyDigsiteJeiPlugin implements IModPlugin {
    public static final ResourceLocation PLUGIN_ID = HoleyDigsite.id("jei_plugin");

    public static final RecipeType<ForgeRecipeRow> CRAFTING_FORGE =
            RecipeType.create(HoleyDigsite.MODID, "crafting_forge", ForgeRecipeRow.class);
    public static final RecipeType<AnvilRecipeRow> SMITHING_ANVIL =
            RecipeType.create(HoleyDigsite.MODID, "smithing_anvil", AnvilRecipeRow.class);
    public static final RecipeType<CrucibleMeltingRow> CRUCIBLE_MELTING =
            RecipeType.create(HoleyDigsite.MODID, "crucible_melting", CrucibleMeltingRow.class);
    public static final RecipeType<CastingRow> CASTING =
            RecipeType.create(HoleyDigsite.MODID, "casting", CastingRow.class);
    public static final RecipeType<PanningRow> PANNING =
            RecipeType.create(HoleyDigsite.MODID, "panning", PanningRow.class);
    public static final RecipeType<GemCuttingRow> GEM_CUTTING =
            RecipeType.create(HoleyDigsite.MODID, "gem_cutting", GemCuttingRow.class);
    public static final RecipeType<CrateOddsRow> MINING_CRATES =
            RecipeType.create(HoleyDigsite.MODID, "mining_crates", CrateOddsRow.class);

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    /** Each skin, family and wear is its own Skin Kit entry. */
    @Override
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.SKIN_KIT.get(), new mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter<ItemStack>() {
            @Override
            public Object getSubtypeData(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext context) {
                return getLegacyStringSubtypeInfo(stack, context);
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext context) {
                return SkinKitItem.skin(stack) + "|" + SkinKitItem.family(stack) + "|" + SkinKitItem.wear(stack);
            }
        });
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new CraftingForgeCategory(guiHelper),
                new SmithingAnvilCategory(guiHelper),
                new CrucibleMeltingCategory(guiHelper),
                new CastingCategory(guiHelper),
                new PanningCategory(guiHelper),
                new GemCuttingCategory(guiHelper),
                new MiningCrateCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(CRAFTING_FORGE, ModBlocks.CRAFTING_FORGE.get());
        registration.addRecipeCatalysts(SMITHING_ANVIL, ModBlocks.SMITHING_ANVIL.get());
        registration.addRecipeCatalysts(CRUCIBLE_MELTING,
                ModBlocks.CRUCIBLE.get(), ModBlocks.LARGE_CRUCIBLE.get(),
                ModBlocks.FORGE.get(), ModBlocks.HOT_FORGE.get());
        registration.addRecipeCatalysts(CASTING, ModBlocks.MOLD.get(), ModBlocks.CASTING_MOLD_T2.get());
        registration.addRecipeCatalysts(PANNING,
                ModItems.PAN.get(), ModItems.LARGE_PAN.get(),
                ModBlocks.SLUICE_BOX.get(), ModBlocks.SLUICE_BOX_T2.get());
        registration.addRecipeCatalysts(GEM_CUTTING, ModBlocks.GEM_CUTTER.get());
        registration.addRecipeCatalysts(MINING_CRATES,
                ModBlocks.WOODEN_CRATE.get(), ModBlocks.IRON_CRATE.get(), ModBlocks.GOLD_CRATE.get());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        HolderLookup.Provider registries = null;
        if (Minecraft.getInstance().level != null) {
            registries = Minecraft.getInstance().level.registryAccess();
        } else if (Minecraft.getInstance().getConnection() != null) {
            registries = Minecraft.getInstance().getConnection().registryAccess();
        }

        registration.addRecipes(CRAFTING_FORGE, ForgeRecipeRow.loadAll(registries));
        registration.addRecipes(SMITHING_ANVIL, AnvilRecipeRow.loadAll(registries));
        registration.addRecipes(CRUCIBLE_MELTING, CrucibleMeltingRow.loadAll());
        registration.addRecipes(CASTING, CastingRow.loadAll());
        registration.addRecipes(PANNING, PanningRow.loadAll());
        registration.addRecipes(GEM_CUTTING, GemCuttingRow.loadAll(registries));
        registration.addRecipes(MINING_CRATES, CrateOddsRow.loadAll());

        registerInfoPages(registration, registries);
    }

    private void registerInfoPages(IRecipeRegistration registration, HolderLookup.Provider registries) {
        // Mining Drills
        List<ItemStack> drills = List.of(
                new ItemStack(ModBlocks.MINING_DRILL.get()),
                new ItemStack(ModBlocks.MINING_DRILL_T2.get()),
                new ItemStack(ModBlocks.MINING_DRILL_T3.get())
        );
        registration.addItemStackInfo(drills,
                JeiText.t("info_drills_1"),
                JeiText.t("info_drills_2"),
                JeiText.t("info_drills_3")
        );

        registration.addItemStackInfo(List.of(new ItemStack(ModBlocks.SLUICE_BOX.get())),
                JeiText.t("info_sluice_starter_1"),
                JeiText.t("info_sluice_starter_2")
        );
        registration.addItemStackInfo(List.of(new ItemStack(ModBlocks.SLUICE_BOX_T2.get())),
                JeiText.t("info_sluice_long_1"),
                JeiText.t("info_sluice_long_2"),
                JeiText.t("info_sluice_long_3")
        );

        // Belts & Chute
        List<ItemStack> belts = List.of(
                new ItemStack(ModBlocks.CLUMP_BELT.get()),
                new ItemStack(ModBlocks.FAST_CLUMP_BELT.get()),
                new ItemStack(ModBlocks.INCLINE_BELT.get()),
                new ItemStack(ModBlocks.FAST_INCLINE_BELT.get()),
                new ItemStack(ModBlocks.DOWN_INCLINE_BELT.get()),
                new ItemStack(ModBlocks.FAST_DOWN_INCLINE_BELT.get()),
                new ItemStack(ModBlocks.FILTER_BELT.get()),
                new ItemStack(ModBlocks.BELT_SPLITTER.get()),
                new ItemStack(ModBlocks.DROP_CHUTE.get())
        );
        registration.addItemStackInfo(belts,
                JeiText.t("info_belts_1"),
                JeiText.t("info_belts_2"),
                JeiText.t("info_belts_3")
        );

        // Sell Pad, Buy Pad, Money Pan, Shop Button
        List<ItemStack> shopPads = List.of(
                new ItemStack(ModBlocks.SELL_PAD.get()),
                new ItemStack(ModBlocks.BUY_PAD.get()),
                new ItemStack(ModBlocks.MONEY_PAN.get()),
                new ItemStack(ModBlocks.SHOP_BUTTON.get())
        );
        registration.addItemStackInfo(shopPads,
                JeiText.t("info_shop_pads_1"),
                JeiText.t("info_shop_pads_2"),
                JeiText.t("info_shop_pads_3"),
                JeiText.t("info_shop_pads_4")
        );

        // Contract Board
        registration.addItemStackInfo(new ItemStack(ModBlocks.CONTRACT_BOARD.get()),
                JeiText.t("info_contract_board_1", ContractDefs.rules().slots()),
                JeiText.t("info_contract_board_2"),
                JeiText.t("info_contract_board_3", ContractDefs.rules().days())
        );

        // Skin Bench
        registration.addItemStackInfo(new ItemStack(ModBlocks.SKIN_BENCH.get()),
                JeiText.t("info_skin_bench_1"),
                JeiText.t("info_skin_bench_2"),
                JeiText.t("info_skin_bench_3")
        );
        List<ItemStack> kits = new ArrayList<>();
        for (String skin : com.holeydigsite.skin.SkinDefs.skins().keySet()) {
            for (String family : com.holeydigsite.skin.SkinDefs.familiesOf(skin)) {
                kits.add(SkinKitItem.of(skin, family, com.holeydigsite.skin.SkinWear.FRESH, 1));
            }
        }
        if (!kits.isEmpty()) {
            registration.addItemStackInfo(kits, JeiText.t("info_skin_kit_1"), JeiText.t("info_skin_kit_2"));
        }

        // Hardpan
        registration.addItemStackInfo(new ItemStack(ModBlocks.HARDPAN.get()), JeiText.t("info_hardpan"));

        // Prospector's & Assayer's Pick
        List<ItemStack> picks = List.of(
                new ItemStack(ModItems.PROSPECTORS_PICK.get()),
                new ItemStack(ModItems.SAND_HEAD.get()),
                new ItemStack(ModItems.GRAVEL_HEAD.get()),
                new ItemStack(ModItems.ASSAYERS_PICK.get())
        );
        registration.addItemStackInfo(picks,
                JeiText.t("info_picks_1"),
                JeiText.t("info_picks_2"),
                JeiText.t("info_picks_3")
        );

        // Shop items from catalogue
        for (ShopInfoRow shopRow : ShopInfoRow.loadAll(registries)) {
            registration.addItemStackInfo(new ItemStack(shopRow.item()), shopRow.infoLine());
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        IIngredientManager ingredientManager = jeiRuntime.getIngredientManager();
        List<ItemStack> toRemove = new ArrayList<>();
        Set<Item> seenIngots = new HashSet<>();

        for (ItemStack stack : ingredientManager.getAllItemStacks()) {
            if (stack.is(ModItems.SITE_MARKER.get())) {
                toRemove.add(stack);
                continue;
            }
            if (stack.is(ModItems.SKIN_KIT.get()) && !SkinKitItem.isKit(stack)) {
                toRemove.add(stack);
                continue;
            }
            if (ShopBlockItem.isLockedVariant(stack)) {
                toRemove.add(stack);
                continue;
            }
            if (stack.getItem() instanceof PanBlockItem) {
                if (PanBlock.getFill(stack) > 0 || PanBlock.getTier(stack) > 1) {
                    toRemove.add(stack);
                    continue;
                }
            }
            if (ShovelHelper.isFull(stack)) {
                toRemove.add(stack);
                continue;
            }
            if (stack.getItem() instanceof IngotItem) {
                if (stack.has(ModDataComponents.WEIGHT.get())) {
                    int grams = stack.get(ModDataComponents.WEIGHT.get());
                    if (grams != 10) {
                        toRemove.add(stack);
                        continue;
                    }
                }
                if (!seenIngots.add(stack.getItem())) {
                    toRemove.add(stack);
                }
            }
        }

        if (!toRemove.isEmpty()) {
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toRemove);
        }
    }
}
