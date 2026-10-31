package com.holeydigsite.registry;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.item.BrushItem;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.CoinItem;
import com.holeydigsite.item.DirtClumpItem;
import com.holeydigsite.item.PanBlockItem;
import com.holeydigsite.item.ShopBlockItem;
import com.holeydigsite.tier.Tiers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registrations.
 *
 * <p>The shovel is the vanilla {@code minecraft:wooden_shovel} whose full state is a data component
 * ({@link ModDataComponents#SHOVEL_FULL}), so no shovel item is registered here.</p>
 */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HoleyDigsite.MODID);

    static {
        // Every tiered item (Pan, Brush, Drill, Sluice, belts, Forge, Crucible, Mold) is registered from tiers data.
        Tiers.registerItems(ITEMS);
    }

    /** {@code holeydigsite:pan}, places the Pan block and remembers its fill level. */
    public static final DeferredItem<PanBlockItem> PAN = Tiers.item("pan");

    /** {@code holeydigsite:brush}, pans a waterlogged, filled pan with no durability so it can never break. */
    public static final DeferredItem<BrushItem> BRUSH = Tiers.item("brush");

    // Coins stack to 64 with values in copper, where tiers exchange 8 to 1 via crafting recipes.

    /** {@code holeydigsite:copper_coin}, worth 1. */
    public static final DeferredItem<CoinItem> COPPER_COIN = ITEMS.register("copper_coin",
            () -> new CoinItem(CoinItem.COPPER_VALUE, new Item.Properties()));

    /** {@code holeydigsite:silver_coin}, worth 8 copper. */
    public static final DeferredItem<CoinItem> SILVER_COIN = ITEMS.register("silver_coin",
            () -> new CoinItem(CoinItem.SILVER_VALUE, new Item.Properties()));

    /** {@code holeydigsite:gold_coin}, worth 64 copper or 8 silver. */
    public static final DeferredItem<CoinItem> GOLD_COIN = ITEMS.register("gold_coin",
            () -> new CoinItem(CoinItem.GOLD_VALUE, new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<CoinItem> PLATINUM_COIN = ITEMS.register("platinum_coin",
            () -> new CoinItem(CoinItem.PLATINUM_VALUE, new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<CoinItem> RUBY_CROWN = ITEMS.register("ruby_crown",
            () -> new CoinItem(CoinItem.CROWN_VALUE, new Item.Properties().rarity(Rarity.EPIC)));

    public static final DeferredItem<CoinItem> MINT_BAR = ITEMS.register("mint_bar",
            () -> new CoinItem(CoinItem.BAR_VALUE, new Item.Properties().rarity(Rarity.EPIC)));

    // Drilling and water power.

    /**
     * {@code holeydigsite:dirt_clump}, one scoop of dirt as an item feeding a pan by hand, drill or hopper,
     * carrying its tier in {@code holeydigsite:clump_tier} as Dirt Clump, Deep Dirt Clump, or Abyssal Dirt Clump.
     */
    public static final DeferredItem<DirtClumpItem> DIRT_CLUMP = ITEMS.register("dirt_clump",
            () -> new DirtClumpItem(new Item.Properties()));

    /** {@code holeydigsite:dirtrock}, creative and building only as it cannot be mined in survival. */
    public static final DeferredItem<BlockItem> DIRTROCK = ITEMS.registerSimpleBlockItem(ModBlocks.DIRTROCK);

    public static final DeferredItem<BlockItem> WATER_INTAKE = ITEMS.registerSimpleBlockItem(ModBlocks.WATER_INTAKE);

    public static final DeferredItem<BlockItem> WATER_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.WATER_PIPE);

    public static final DeferredItem<BlockItem> MINING_DRILL = Tiers.item("mining_drill");

    // Clump belt.

    public static final DeferredItem<BlockItem> CLUMP_BELT = Tiers.item("clump_belt");

    // Sluice box.

    public static final DeferredItem<BlockItem> SLUICE_BOX = Tiers.item("sluice_box");

    // Deeper rock, resources, and Sell Pad.

    public static final DeferredItem<BlockItem> DEEP_DIRTROCK = ITEMS.registerSimpleBlockItem(ModBlocks.DEEP_DIRTROCK);

    public static final DeferredItem<BlockItem> ABYSSAL_DIRTROCK = ITEMS.registerSimpleBlockItem(ModBlocks.ABYSSAL_DIRTROCK);

    public static final DeferredItem<ShopBlockItem> SELL_PAD = ITEMS.register("sell_pad",
            () -> new ShopBlockItem(ModBlocks.SELL_PAD.get(), new Item.Properties()));

    // The shop.

    public static final DeferredItem<ShopBlockItem> BUY_PAD = ITEMS.register("buy_pad",
            () -> new ShopBlockItem(ModBlocks.BUY_PAD.get(), new Item.Properties()));

    public static final DeferredItem<ShopBlockItem> MONEY_PAN = ITEMS.register("money_pan",
            () -> new ShopBlockItem(ModBlocks.MONEY_PAN.get(), new Item.Properties()));

    public static final DeferredItem<ShopBlockItem> SHOP_BUTTON = ITEMS.register("shop_button",
            () -> new ShopBlockItem(ModBlocks.SHOP_BUTTON.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SHOP_DISPLAY = ITEMS.registerSimpleBlockItem(ModBlocks.SHOP_DISPLAY);

    /** {@code holeydigsite:blueprint}, names the item it buys in the {@code blueprint_target} component. */
    public static final DeferredItem<BlueprintItem> BLUEPRINT = ITEMS.register("blueprint",
            () -> new BlueprintItem(new Item.Properties()));

    // Lava power, melting, and casting.

    /** Fire resistant so breaking a lava-logged intake pops the item out into the lava it leaves behind without burning up. */
    public static final DeferredItem<BlockItem> LAVA_INTAKE = ITEMS.registerSimpleBlockItem(ModBlocks.LAVA_INTAKE, new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> LAVA_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.LAVA_PIPE);
    public static final DeferredItem<BlockItem> FORGE = Tiers.item("forge");
    public static final DeferredItem<BlockItem> CRUCIBLE = Tiers.item("crucible");
    public static final DeferredItem<BlockItem> MOLD = Tiers.item("mold");

    // Casting upgrades for the Crafting Forge only, and the Belt Splitter.
    public static final DeferredItem<BlockItem> HOT_FORGE = Tiers.item("hot_forge");
    public static final DeferredItem<BlockItem> LARGE_CRUCIBLE = Tiers.item("large_crucible");
    public static final DeferredItem<BlockItem> CASTING_MOLD_T2 = Tiers.item("casting_mold_t2");
    public static final DeferredItem<BlockItem> BELT_SPLITTER = ITEMS.registerSimpleBlockItem(ModBlocks.BELT_SPLITTER);

    // Crafting Forge.
    public static final DeferredItem<BlockItem> CRAFTING_FORGE = ITEMS.registerSimpleBlockItem(ModBlocks.CRAFTING_FORGE);

    // Tier 2 for the Crafting Forge only.
    public static final DeferredItem<BlockItem> MINING_DRILL_T2 = Tiers.item("mining_drill_t2");
    public static final DeferredItem<BlockItem> SLUICE_BOX_T2 = Tiers.item("sluice_box_t2");
    public static final DeferredItem<PanBlockItem> LARGE_PAN = Tiers.item("large_pan");
    /** Pans twice as fast with one scoop every 20 ticks and no durability like the brush. */
    public static final DeferredItem<BrushItem> GOLDCHRO_BRUSH = Tiers.item("goldchro_brush");
    public static final DeferredItem<BlockItem> FAST_CLUMP_BELT = Tiers.item("fast_clump_belt");
    // Belts.
    public static final DeferredItem<BlockItem> INCLINE_BELT = Tiers.item("incline_belt");
    public static final DeferredItem<BlockItem> FAST_INCLINE_BELT = Tiers.item("fast_incline_belt");
    public static final DeferredItem<BlockItem> DOWN_INCLINE_BELT = Tiers.item("down_incline_belt");
    public static final DeferredItem<BlockItem> FAST_DOWN_INCLINE_BELT = Tiers.item("fast_down_incline_belt");
    public static final DeferredItem<BlockItem> DROP_CHUTE = ITEMS.registerSimpleBlockItem(ModBlocks.DROP_CHUTE);
    public static final DeferredItem<BlockItem> FILTER_BELT = ITEMS.registerSimpleBlockItem(ModBlocks.FILTER_BELT);
    public static final DeferredItem<BlockItem> GEM_CUTTER = ITEMS.registerSimpleBlockItem(ModBlocks.GEM_CUTTER);

    // New rocks, clumps, resources, land deeds, and Tier 3 drill.
    // Plot-exclusive shop items, each only on sale at its plot kiosk, see ShopItem.site.

    /** {@code holeydigsite:prospectors_pick} for Deep Dig, finds buried Deep and Abyssal Dirtrock nearby. */
    public static final DeferredItem<com.holeydigsite.item.ProspectorPickItem> PROSPECTORS_PICK = ITEMS.register("prospectors_pick",
            () -> new com.holeydigsite.item.ProspectorPickItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    /** {@code holeydigsite:sand_head} for Prospector's Pick, sold at Sand Dunes kiosk. */
    public static final DeferredItem<com.holeydigsite.item.PickHeadItem> SAND_HEAD = ITEMS.register("sand_head",
            () -> new com.holeydigsite.item.PickHeadItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), com.holeydigsite.tier.DirtTier.SAND));
    /** {@code holeydigsite:gravel_head} for Prospector's Pick, sold at Gravel Flats kiosk. */
    public static final DeferredItem<com.holeydigsite.item.PickHeadItem> GRAVEL_HEAD = ITEMS.register("gravel_head",
            () -> new com.holeydigsite.item.PickHeadItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), com.holeydigsite.tier.DirtTier.GRAVEL));
    /** {@code holeydigsite:assayers_pick} Tier 2 pick crafted in Crafting Forge. */
    public static final DeferredItem<com.holeydigsite.item.AssayersPickItem> ASSAYERS_PICK = ITEMS.register("assayers_pick",
            () -> new com.holeydigsite.item.AssayersPickItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    /** {@code holeydigsite:riffle_insert} for Gravel Flats as a sluice upgrade giving plus 25 percent from gravel clumps. */
    public static final DeferredItem<com.holeydigsite.item.SluiceUpgradeItem> RIFFLE_INSERT = ITEMS.register("riffle_insert",
            () -> new com.holeydigsite.item.SluiceUpgradeItem(com.holeydigsite.sluice.SluiceUpgrade.RIFFLE,
                    new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    /** {@code holeydigsite:fine_mesh_screen} for Sand Dunes as a sluice upgrade giving plus 24 percent from sand clumps and more topaz. */
    public static final DeferredItem<com.holeydigsite.item.SluiceUpgradeItem> FINE_MESH_SCREEN = ITEMS.register("fine_mesh_screen",
            () -> new com.holeydigsite.item.SluiceUpgradeItem(com.holeydigsite.sluice.SluiceUpgrade.MESH,
                    new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    /** {@code holeydigsite:seed_screen} for Trading Post as a sluice upgrade where washed clumps sometimes give seeds. */
    public static final DeferredItem<com.holeydigsite.item.SluiceUpgradeItem> SEED_SCREEN = ITEMS.register("seed_screen",
            () -> new com.holeydigsite.item.SluiceUpgradeItem(com.holeydigsite.sluice.SluiceUpgrade.SEED,
                    new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    // Seeds and foods of our own crops.
    private static net.minecraft.world.food.FoodProperties.Builder food(int nutrition, float saturation) {
        return new net.minecraft.world.food.FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation);
    }

    private static net.minecraft.world.effect.MobEffectInstance buff(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int seconds) {
        return new net.minecraft.world.effect.MobEffectInstance(effect, seconds * 20, 0);
    }

    public static final DeferredItem<Item> GLOWROOT_SEEDS = ITEMS.register("glowroot_seeds", () -> new com.holeydigsite.item.FarmItems.Seeds(
            ModBlocks.GLOWROOT_CROP.get(), new Item.Properties(), "tooltip.holeydigsite.seeds.where_glowroot"));
    public static final DeferredItem<Item> GLOWROOT = ITEMS.register("glowroot", () -> new Item(new Item.Properties().food(food(2, 0.3F).build())));
    public static final DeferredItem<Item> ROASTED_GLOWROOT = ITEMS.register("roasted_glowroot", () -> new com.holeydigsite.item.FarmItems.Food(
            new Item.Properties().food(food(6, 0.7F).effect(() -> buff(net.minecraft.world.effect.MobEffects.DIG_SPEED, 120), 1.0F).build()),
            "tooltip.holeydigsite.food.haste", false));
    public static final DeferredItem<Item> RIVERBEAN_SEEDS = ITEMS.register("riverbean_seeds", () -> new com.holeydigsite.item.FarmItems.Seeds(
            ModBlocks.RIVERBEAN_CROP.get(), new Item.Properties(), "tooltip.holeydigsite.seeds.where_riverbean"));
    public static final DeferredItem<Item> RIVERBEAN = ITEMS.register("riverbean", () -> new Item(new Item.Properties().food(food(1, 0.2F).build())));
    public static final DeferredItem<Item> RIVERBEAN_STEW = ITEMS.register("riverbean_stew", () -> new com.holeydigsite.item.FarmItems.Food(
            new Item.Properties().stacksTo(16).food(food(8, 0.7F)
                    .effect(() -> buff(com.holeydigsite.effect.ModEffects.STEADY_HANDS, 120), 1.0F)
                    .usingConvertsTo(net.minecraft.world.item.Items.BOWL).build()),
            "tooltip.holeydigsite.food.panning", false));
    public static final DeferredItem<Item> DUNE_MELON_SEEDS = ITEMS.register("dune_melon_seeds", () -> new com.holeydigsite.item.FarmItems.Seeds(
            ModBlocks.DUNE_MELON_CROP.get(), new Item.Properties(), "tooltip.holeydigsite.seeds.where_dune_melon"));
    public static final DeferredItem<Item> DUNE_MELON_SLICE = ITEMS.register("dune_melon_slice", () -> new Item(new Item.Properties().food(food(2, 0.3F).build())));
    public static final DeferredItem<Item> DUNE_MELON_JUICE = ITEMS.register("dune_melon_juice", () -> new com.holeydigsite.item.FarmItems.Food(
            new Item.Properties().stacksTo(16).food(food(5, 0.6F).effect(() -> buff(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 90), 1.0F)
                    .usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE).build()),
            "tooltip.holeydigsite.food.speed", true));
    public static final DeferredItem<BlockItem> MINERS_LANTERN = ITEMS.registerSimpleBlockItem(ModBlocks.MINERS_LANTERN);
    public static final DeferredItem<BlockItem> RIVER_STONE_LAMP = ITEMS.registerSimpleBlockItem(ModBlocks.RIVER_STONE_LAMP);
    public static final DeferredItem<BlockItem> SANDSTONE_LAMP = ITEMS.registerSimpleBlockItem(ModBlocks.SANDSTONE_LAMP);

    public static final DeferredItem<BlockItem> GRAVELROCK = ITEMS.registerSimpleBlockItem(ModBlocks.GRAVELROCK);
    public static final DeferredItem<BlockItem> SANDROCK = ITEMS.registerSimpleBlockItem(ModBlocks.SANDROCK);
    public static final DeferredItem<BlockItem> PLOT_BOARD = ITEMS.registerSimpleBlockItem(ModBlocks.PLOT_BOARD);
    /** The Site Marker, creative only. */
    public static final DeferredItem<BlockItem> SITE_MARKER = ITEMS.registerSimpleBlockItem(ModBlocks.SITE_MARKER);
    public static final DeferredItem<BlockItem> MINING_DRILL_T3 = Tiers.item("mining_drill_t3");
    /** One scoop of gravel or sand, see {@link com.holeydigsite.tier.DirtTier}. */
    public static final DeferredItem<Item> GRAVEL_CLUMP = ITEMS.registerSimpleItem("gravel_clump");
    public static final DeferredItem<Item> SAND_CLUMP = ITEMS.registerSimpleItem("sand_clump");
    public static final DeferredItem<Item> ORCHREMIA_CHUNK_SMALL = ITEMS.registerSimpleItem("orchremia_chunk_small");
    public static final DeferredItem<Item> ORCHREMIA_CHUNK_MEDIUM = ITEMS.registerSimpleItem("orchremia_chunk_medium");
    public static final DeferredItem<Item> ORCHREMIA_CHUNK_LARGE = ITEMS.registerSimpleItem("orchremia_chunk_large",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> SILVANE_CHUNK_SMALL = ITEMS.registerSimpleItem("silvane_chunk_small");
    public static final DeferredItem<Item> SILVANE_CHUNK_MEDIUM = ITEMS.registerSimpleItem("silvane_chunk_medium");
    public static final DeferredItem<Item> SILVANE_CHUNK_LARGE = ITEMS.registerSimpleItem("silvane_chunk_large",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> SAPPHIRE_SMALL = ITEMS.registerSimpleItem("sapphire_small", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> SAPPHIRE_MEDIUM = ITEMS.registerSimpleItem("sapphire_medium", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> SAPPHIRE_LARGE = ITEMS.registerSimpleItem("sapphire_large", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> TOPAZ_SMALL = ITEMS.registerSimpleItem("topaz_small", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> TOPAZ_MEDIUM = ITEMS.registerSimpleItem("topaz_medium", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> TOPAZ_LARGE = ITEMS.registerSimpleItem("topaz_large", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<com.holeydigsite.item.IngotItem> ORCHREMIA_INGOT = ITEMS.register("orchremia_ingot",
            () -> new com.holeydigsite.item.IngotItem(new Item.Properties()));
    public static final DeferredItem<com.holeydigsite.item.IngotItem> SILVANE_INGOT = ITEMS.register("silvane_ingot",
            () -> new com.holeydigsite.item.IngotItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<com.holeydigsite.plot.LandDeedItem> LAND_DEED_DEEP_DIG = ITEMS.register("land_deed_deep_dig",
            () -> new com.holeydigsite.plot.LandDeedItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "deep_dig"));
    public static final DeferredItem<com.holeydigsite.plot.LandDeedItem> LAND_DEED_GRAVEL_FLATS = ITEMS.register("land_deed_gravel_flats",
            () -> new com.holeydigsite.plot.LandDeedItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "gravel_flats"));
    public static final DeferredItem<com.holeydigsite.plot.LandDeedItem> LAND_DEED_SAND_DUNES = ITEMS.register("land_deed_sand_dunes",
            () -> new com.holeydigsite.plot.LandDeedItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "sand_dunes"));

    /** Weighted ingots using the {@code holeydigsite:weight} component, see {@link com.holeydigsite.item.IngotItem}. */
    public static final DeferredItem<com.holeydigsite.item.IngotItem> FOLKIRON_INGOT = ITEMS.register("folkiron_ingot",
            () -> new com.holeydigsite.item.IngotItem(new Item.Properties()));
    public static final DeferredItem<com.holeydigsite.item.IngotItem> GOLDCHRO_INGOT = ITEMS.register("goldchro_ingot",
            () -> new com.holeydigsite.item.IngotItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    /*
     * Resources in three sizes each as separate items so they stack, with weights in grams and prices data-driven.
     */

    public static final DeferredItem<Item> FOLKIRON_CHUNK_SMALL = ITEMS.registerSimpleItem("folkiron_chunk_small");
    public static final DeferredItem<Item> FOLKIRON_CHUNK_MEDIUM = ITEMS.registerSimpleItem("folkiron_chunk_medium");
    public static final DeferredItem<Item> FOLKIRON_CHUNK_LARGE = ITEMS.registerSimpleItem("folkiron_chunk_large");

    public static final DeferredItem<Item> GOLDCHRO_CHUNK_SMALL = ITEMS.registerSimpleItem("goldchro_chunk_small");
    public static final DeferredItem<Item> GOLDCHRO_CHUNK_MEDIUM = ITEMS.registerSimpleItem("goldchro_chunk_medium");
    public static final DeferredItem<Item> GOLDCHRO_CHUNK_LARGE = ITEMS.registerSimpleItem("goldchro_chunk_large",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> RUBY_SMALL = ITEMS.registerSimpleItem("ruby_small",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> RUBY_MEDIUM = ITEMS.registerSimpleItem("ruby_medium",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> RUBY_LARGE = ITEMS.registerSimpleItem("ruby_large",
            new Item.Properties().rarity(Rarity.RARE));

    // Uncut gems from panning and sluicing, turned into cut gems by a Gem Cutter.
    public static final DeferredItem<Item> UNCUT_RUBY_SMALL = ITEMS.registerSimpleItem("uncut_ruby_small", new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<Item> UNCUT_RUBY_MEDIUM = ITEMS.registerSimpleItem("uncut_ruby_medium", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UNCUT_RUBY_LARGE = ITEMS.registerSimpleItem("uncut_ruby_large", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UNCUT_SAPPHIRE_SMALL = ITEMS.registerSimpleItem("uncut_sapphire_small", new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<Item> UNCUT_SAPPHIRE_MEDIUM = ITEMS.registerSimpleItem("uncut_sapphire_medium", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UNCUT_SAPPHIRE_LARGE = ITEMS.registerSimpleItem("uncut_sapphire_large", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UNCUT_TOPAZ_SMALL = ITEMS.registerSimpleItem("uncut_topaz_small", new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<Item> UNCUT_TOPAZ_MEDIUM = ITEMS.registerSimpleItem("uncut_topaz_medium", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UNCUT_TOPAZ_LARGE = ITEMS.registerSimpleItem("uncut_topaz_large", new Item.Properties().rarity(Rarity.UNCOMMON));
    // Smithing Anvil, Blacksmith Hammer, and the five smithed pieces with materials in the holeydigsite:smith component.
    public static final DeferredItem<BlockItem> SMITHING_ANVIL = ITEMS.registerSimpleBlockItem(ModBlocks.SMITHING_ANVIL);
    public static final DeferredItem<BlockItem> SKIN_BENCH = ITEMS.registerSimpleBlockItem(ModBlocks.SKIN_BENCH);
    public static final DeferredItem<BlockItem> CONTRACT_BOARD = ITEMS.registerSimpleBlockItem(ModBlocks.CONTRACT_BOARD);
    public static final DeferredItem<com.holeydigsite.smith.SmithItems.Hammer> BLACKSMITH_HAMMER = ITEMS.register("blacksmith_hammer",
            () -> new com.holeydigsite.smith.SmithItems.Hammer(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.holeydigsite.smith.SmithItems.Jewellery> SMITH_RING = ITEMS.register("smith_ring",
            () -> new com.holeydigsite.smith.SmithItems.Jewellery(new Item.Properties()));
    public static final DeferredItem<com.holeydigsite.smith.SmithItems.Jewellery> SMITH_NECKLACE = ITEMS.register("smith_necklace",
            () -> new com.holeydigsite.smith.SmithItems.Necklace(new Item.Properties()));
    public static final DeferredItem<com.holeydigsite.smith.SmithItems.Blade> SMITH_DAGGER = ITEMS.register("smith_dagger",
            () -> new com.holeydigsite.smith.SmithItems.Blade(new Item.Properties().attributes(
                    net.minecraft.world.item.SwordItem.createAttributes(com.holeydigsite.smith.SmithTier.INSTANCE, 2.0F, -1.6F))));
    public static final DeferredItem<com.holeydigsite.smith.SmithItems.Blade> SMITH_SWORD = ITEMS.register("smith_sword",
            () -> new com.holeydigsite.smith.SmithItems.Blade(new Item.Properties().attributes(
                    net.minecraft.world.item.SwordItem.createAttributes(com.holeydigsite.smith.SmithTier.INSTANCE, 3.0F, -2.4F))));
    public static final DeferredItem<com.holeydigsite.smith.SmithItems.Axe> SMITH_AXE = ITEMS.register("smith_axe",
            () -> new com.holeydigsite.smith.SmithItems.Axe(new Item.Properties().attributes(
                    net.minecraft.world.item.AxeItem.createAttributes(com.holeydigsite.smith.SmithTier.INSTANCE, 5.5F, -3.1F))));

    // Ledger-only decorative blocks.
    public static final DeferredItem<BlockItem> CLAIM_FLAG = ITEMS.registerSimpleBlockItem(ModBlocks.CLAIM_FLAG);
    public static final DeferredItem<BlockItem> ORE_CART = ITEMS.registerSimpleBlockItem(ModBlocks.ORE_CART);
    public static final DeferredItem<BlockItem> TOOL_RACK = ITEMS.registerSimpleBlockItem(ModBlocks.TOOL_RACK);
    public static final DeferredItem<BlockItem> LUCKY_LANTERN = ITEMS.registerSimpleBlockItem(ModBlocks.LUCKY_LANTERN);
    public static final DeferredItem<BlockItem> GEM_DISPLAY_CASE = ITEMS.registerSimpleBlockItem(ModBlocks.GEM_DISPLAY_CASE);
    public static final DeferredItem<BlockItem> GOLDEN_PAN_TROPHY = ITEMS.registerSimpleBlockItem(ModBlocks.GOLDEN_PAN_TROPHY);

    // Mining Crates and what only they give
    public static final DeferredItem<BlockItem> MINERS_HELMET_STAND = ITEMS.registerSimpleBlockItem(ModBlocks.MINERS_HELMET_STAND);
    public static final DeferredItem<BlockItem> RUSTY_MINE_BELL = ITEMS.registerSimpleBlockItem(ModBlocks.RUSTY_MINE_BELL);
    public static final DeferredItem<BlockItem> SLUICE_MODEL = ITEMS.registerSimpleBlockItem(ModBlocks.SLUICE_MODEL);
    public static final DeferredItem<BlockItem> GOLDEN_DRILL_STATUE = ITEMS.registerSimpleBlockItem(ModBlocks.GOLDEN_DRILL_STATUE);
    public static final DeferredItem<BlockItem> WOODEN_CRATE = ITEMS.registerSimpleBlockItem(ModBlocks.WOODEN_CRATE, new Item.Properties().stacksTo(16));
    public static final DeferredItem<BlockItem> IRON_CRATE = ITEMS.registerSimpleBlockItem(ModBlocks.IRON_CRATE, new Item.Properties().stacksTo(16));
    public static final DeferredItem<BlockItem> GOLD_CRATE = ITEMS.registerSimpleBlockItem(ModBlocks.GOLD_CRATE,
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> PROSPECTOR_CRATE = ITEMS.registerSimpleBlockItem(ModBlocks.PROSPECTOR_CRATE,
            new Item.Properties().stacksTo(16));
    /** {@code holeydigsite:bolt_cutters}, used up to open one locked crate. Bought at the Trading Post, never found or crafted. */
    public static final DeferredItem<Item> BOLT_CUTTERS = ITEMS.registerSimpleItem("bolt_cutters", new Item.Properties().stacksTo(16));
    public static final DeferredItem<BlockItem> CRATE_SWITCH = ITEMS.registerSimpleBlockItem(ModBlocks.CRATE_SWITCH);
    /** {@code holeydigsite:skin_scrap}, what a scrapped Skin Kit or a duplicate crate title turns into. Only sells at the Sell Pad. */
    public static final DeferredItem<Item> SKIN_SCRAP = ITEMS.registerSimpleItem("skin_scrap");
    /** {@code holeydigsite:skin_kit}, one machine skin as a tradeable item (skin, family and wear components). */
    public static final DeferredItem<com.holeydigsite.skin.SkinKitItem> SKIN_KIT = ITEMS.register("skin_kit",
            () -> new com.holeydigsite.skin.SkinKitItem(new Item.Properties()));

    // Prospector's Ledger.
    public static final DeferredItem<com.holeydigsite.ledger.ProspectorsLedgerItem> PROSPECTORS_LEDGER = ITEMS.register("prospectors_ledger",
            () -> new com.holeydigsite.ledger.ProspectorsLedgerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // Phase 28a: Outback event blocks and items
    public static final DeferredItem<BlockItem> RED_DIRT_ROCK = ITEMS.registerSimpleBlockItem(ModBlocks.RED_DIRT_ROCK);
    public static final DeferredItem<BlockItem> MULLOCK_ROCK = ITEMS.registerSimpleBlockItem(ModBlocks.MULLOCK_ROCK);
    public static final DeferredItem<BlockItem> OPAL_CLAY_ROCK = ITEMS.registerSimpleBlockItem(ModBlocks.OPAL_CLAY_ROCK);
    public static final DeferredItem<BlockItem> HARDPAN = ITEMS.registerSimpleBlockItem(ModBlocks.HARDPAN);
    public static final DeferredItem<BlockItem> PINDAN = ITEMS.registerSimpleBlockItem(ModBlocks.PINDAN);
    public static final DeferredItem<ShopBlockItem> EVENT_SELL_PAD = ITEMS.register("event_sell_pad",
            () -> new ShopBlockItem(ModBlocks.EVENT_SELL_PAD.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> OUTBACK_CRATE = ITEMS.registerSimpleBlockItem(ModBlocks.OUTBACK_CRATE,
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> CORRUGATED_IRON_SHEET = ITEMS.registerSimpleBlockItem(ModBlocks.CORRUGATED_IRON_SHEET);
    public static final DeferredItem<BlockItem> LITTLE_WINDMILL = ITEMS.registerSimpleBlockItem(ModBlocks.LITTLE_WINDMILL);
    public static final DeferredItem<BlockItem> UTE_ORE_CART = ITEMS.registerSimpleBlockItem(ModBlocks.UTE_ORE_CART);
    public static final DeferredItem<BlockItem> SOUTHERN_CROSS_BANNER = ITEMS.registerSimpleBlockItem(ModBlocks.SOUTHERN_CROSS_BANNER);
    public static final DeferredItem<BlockItem> WELCOME_STRANGER_STAND = ITEMS.registerSimpleBlockItem(ModBlocks.WELCOME_STRANGER_STAND,
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> EVENT_BOARD = ITEMS.registerSimpleBlockItem(ModBlocks.EVENT_BOARD);
    public static final DeferredItem<BlockItem> LEADERBOARD = ITEMS.registerSimpleBlockItem(ModBlocks.LEADERBOARD);
    public static final DeferredItem<com.holeydigsite.plot.LandDeedItem> LAND_DEED_EVENT_T1 = ITEMS.register("land_deed_event_t1",
            () -> new com.holeydigsite.plot.LandDeedItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "event_t1"));
    public static final DeferredItem<com.holeydigsite.plot.LandDeedItem> LAND_DEED_EVENT_T2 = ITEMS.register("land_deed_event_t2",
            () -> new com.holeydigsite.plot.LandDeedItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), "event_t2"));
    public static final DeferredItem<com.holeydigsite.plot.LandDeedItem> LAND_DEED_EVENT_T3 = ITEMS.register("land_deed_event_t3",
            () -> new com.holeydigsite.plot.LandDeedItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "event_t3"));

    public static final DeferredItem<Item> RED_DIRT_CLUMP = ITEMS.registerSimpleItem("red_dirt_clump");
    public static final DeferredItem<Item> MULLOCK_CLUMP = ITEMS.registerSimpleItem("mullock_clump");
    public static final DeferredItem<Item> OPAL_CLAY_CLUMP = ITEMS.registerSimpleItem("opal_clay_clump");

    public static final DeferredItem<Item> OUTBACK_GOLD_CHUNK_SMALL = ITEMS.registerSimpleItem("outback_gold_chunk_small");
    public static final DeferredItem<Item> OUTBACK_GOLD_CHUNK_MEDIUM = ITEMS.registerSimpleItem("outback_gold_chunk_medium");
    public static final DeferredItem<Item> OUTBACK_GOLD_CHUNK_LARGE = ITEMS.registerSimpleItem("outback_gold_chunk_large",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.holeydigsite.item.IngotItem> OUTBACK_GOLD_INGOT = ITEMS.register("outback_gold_ingot",
            () -> new com.holeydigsite.item.IngotItem(new Item.Properties()));

    public static final DeferredItem<Item> IRONSTONE_PEBBLE = ITEMS.registerSimpleItem("ironstone_pebble");

    public static final DeferredItem<Item> POTCH = ITEMS.registerSimpleItem("potch", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UNCUT_POTCH = ITEMS.registerSimpleItem("uncut_potch", new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<Item> BOULDER_OPAL = ITEMS.registerSimpleItem("boulder_opal", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UNCUT_BOULDER_OPAL = ITEMS.registerSimpleItem("uncut_boulder_opal", new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<Item> SAPPHIRE_PARTI = ITEMS.registerSimpleItem("sapphire_parti", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> UNCUT_SAPPHIRE_PARTI = ITEMS.registerSimpleItem("uncut_sapphire_parti", new Item.Properties().rarity(Rarity.COMMON));
    public static final DeferredItem<Item> BLACK_OPAL = ITEMS.registerSimpleItem("black_opal", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> UNCUT_BLACK_OPAL = ITEMS.registerSimpleItem("uncut_black_opal", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> FIRE_OPAL = ITEMS.registerSimpleItem("fire_opal", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> UNCUT_FIRE_OPAL = ITEMS.registerSimpleItem("uncut_fire_opal", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> PINK_DIAMOND = ITEMS.registerSimpleItem("pink_diamond", new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<Item> UNCUT_PINK_DIAMOND = ITEMS.registerSimpleItem("uncut_pink_diamond", new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> WELCOME_STRANGER = ITEMS.registerSimpleItem("welcome_stranger",
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    public static final DeferredItem<com.holeydigsite.guide.FieldGuideItem> FIELD_GUIDE = ITEMS.register("field_guide",
            () -> new com.holeydigsite.guide.FieldGuideItem(new Item.Properties().stacksTo(1)));
}
