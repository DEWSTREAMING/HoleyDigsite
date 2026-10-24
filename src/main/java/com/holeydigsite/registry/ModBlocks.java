package com.holeydigsite.registry;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.sellpad.SellPadBlock;
import com.holeydigsite.shop.BuyPadBlock;
import com.holeydigsite.shop.MoneyPanBlock;
import com.holeydigsite.shop.ShopButtonBlock;
import com.holeydigsite.shop.ShopDisplayBlock;
import com.holeydigsite.power.WaterPipeBlock;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.tier.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block registrations. Each block's item is registered in {@link ModItems}. */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HoleyDigsite.MODID);

    static {
        // Phase 15: every tiered block (Pan, Drill, Sluice, belts, Forge, Crucible, Mold...) is registered from data/holeydigsite/tiers/*.json
        Tiers.registerBlocks(BLOCKS);
    }

    /** {@code holeydigsite:pan} - shallow gold pan that holds up to {@link PanBlock#MAX_FILL} scoops of dirt. */
    public static final DeferredBlock<PanBlock> PAN = Tiers.block("pan"); // pistons pop it off (drops keep the fill level)

    /**
     * {@code holeydigsite:dirtrock} - the island's unbreakable floor (replaces bedrock). Same properties as
     * vanilla bedrock: hardness -1, huge blast resistance, pistons can't move it, no drops. Wither/dragon immunity
     * comes from the vanilla block tags. Mining Drills must stand on it ({@link MiningDrillBlock#DRILL_BASE}).
     */
    public static final DeferredBlock<Block> DIRTROCK = BLOCKS.registerSimpleBlock("dirtrock", rock(MapColor.DIRT));

    /**
     * {@code holeydigsite:deep_dirtrock} - tier 2 rock: same unbreakable properties as Dirtrock; a drill on it makes
     * Deep Dirt Clumps (tag {@code holeydigsite:rock_tier_2}). Placed deeper in the phase 10 land plots.
     */
    public static final DeferredBlock<Block> DEEP_DIRTROCK = BLOCKS.registerSimpleBlock("deep_dirtrock",
            rock(MapColor.TERRACOTTA_BROWN));

    /** {@code holeydigsite:abyssal_dirtrock} - tier 3 rock (tag {@code holeydigsite:rock_tier_3}): Abyssal Dirt Clumps. */
    public static final DeferredBlock<Block> ABYSSAL_DIRTROCK = BLOCKS.registerSimpleBlock("abyssal_dirtrock",
            rock(MapColor.COLOR_BLACK));

    /**
     * Bedrock-like properties shared by every Dirtrock tier: hardness -1, huge blast resistance, pistons can't move it,
     * no drops, no mob spawns. Wither/dragon immunity comes from the vanilla block tags.
     */
    private static BlockBehaviour.Properties rock(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK)
                .sound(SoundType.ROOTED_DIRT);
    }

    /** {@code holeydigsite:water_intake} - powers a water network while it sits in water. */
    public static final DeferredBlock<WaterIntakeBlock> WATER_INTAKE = BLOCKS.register("water_intake", () -> new WaterIntakeBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()));

    /** {@code holeydigsite:water_pipe} - carries water power between intakes and machines. */
    public static final DeferredBlock<WaterPipeBlock> WATER_PIPE = BLOCKS.register("water_pipe", () -> new WaterPipeBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()));

    /** {@code holeydigsite:mining_drill} - Tier 1 Mining Drill, see {@link MiningDrillBlock}. */
    public static final DeferredBlock<MiningDrillBlock> MINING_DRILL = Tiers.block("mining_drill");

    /** {@code holeydigsite:clump_belt} - flat conveyor for Dirt Clumps, see {@link ClumpBeltBlock}. */
    public static final DeferredBlock<ClumpBeltBlock> CLUMP_BELT = Tiers.block("clump_belt");

    /** {@code holeydigsite:sluice_box} - Tier 1 Sluice Box, water-powered auto-panning, see {@link SluiceBoxBlock}. */
    public static final DeferredBlock<SluiceBoxBlock> SLUICE_BOX = Tiers.block("sluice_box");

    /** {@code holeydigsite:sell_pad} - holds up to 9 stacks to sell, see {@link SellPadBlock}. */
    public static final DeferredBlock<SellPadBlock> SELL_PAD = BLOCKS.register("sell_pad", () -> new SellPadBlock(shopBlock(MapColor.WOOD, SoundType.WOOD)));

    /** {@code holeydigsite:buy_pad} - holds up to 9 blueprints to buy, see {@link BuyPadBlock}. */
    public static final DeferredBlock<BuyPadBlock> BUY_PAD = BLOCKS.register("buy_pad", () -> new BuyPadBlock(shopBlock(MapColor.WOOD, SoundType.WOOD)));

    /** {@code holeydigsite:money_pan} - the shop's balance, see {@link MoneyPanBlock}. */
    public static final DeferredBlock<MoneyPanBlock> MONEY_PAN = BLOCKS.register("money_pan", () -> new MoneyPanBlock(shopBlock(MapColor.METAL, SoundType.METAL)));

    /** {@code holeydigsite:shop_button} - sells / buys on the pads next to it, see {@link ShopButtonBlock}. */
    public static final DeferredBlock<ShopButtonBlock> SHOP_BUTTON = BLOCKS.register("shop_button", () -> new ShopButtonBlock(shopBlock(MapColor.COLOR_RED, SoundType.METAL)));

    /** {@code holeydigsite:shop_display} - always-unbreakable blueprint display, see {@link ShopDisplayBlock}. */
    public static final DeferredBlock<ShopDisplayBlock> SHOP_DISPLAY = BLOCKS.register("shop_display", () -> new ShopDisplayBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * {@code holeydigsite:site_marker} - Phase 14: invisible, unbreakable marker in the island template; the mod builds the
     * named site (store / plot / kiosk) there on first load, see {@link com.holeydigsite.site.SiteMarkerBlock}.
     */
    public static final DeferredBlock<com.holeydigsite.site.SiteMarkerBlock> SITE_MARKER = BLOCKS.register("site_marker",
            () -> new com.holeydigsite.site.SiteMarkerBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .noCollission()
                    .pushReaction(PushReaction.BLOCK)));

    // ---- Phase 9: lava power, melting and casting ----

    /** {@code holeydigsite:lava_intake} - powers a LAVA network while it is lava-logged (sits in real lava), see {@link com.holeydigsite.power.LavaIntakeBlock}. */
    public static final DeferredBlock<com.holeydigsite.power.LavaIntakeBlock> LAVA_INTAKE = BLOCKS.register("lava_intake",
            () -> new com.holeydigsite.power.LavaIntakeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .lightLevel(s -> s.getValue(com.holeydigsite.power.LavaIntakeBlock.LAVALOGGED) ? 15 : 0)  // real lava inside
                    .pushReaction(PushReaction.BLOCK)));

    /** {@code holeydigsite:lava_pipe} - carries lava power (never joins water pipes), see {@link com.holeydigsite.power.LavaPipeBlock}. */
    public static final DeferredBlock<com.holeydigsite.power.LavaPipeBlock> LAVA_PIPE = BLOCKS.register("lava_pipe",
            () -> new com.holeydigsite.power.LavaPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()));

    /** {@code holeydigsite:forge} - lava-lit hearth for the crucible, see {@link com.holeydigsite.forge.ForgeBlock}. */
    public static final DeferredBlock<com.holeydigsite.forge.ForgeBlock> FORGE = Tiers.block("forge");

    /** {@code holeydigsite:crucible} - melts one metal (200 g) on a lit forge, see {@link com.holeydigsite.forge.CrucibleBlock}. */
    public static final DeferredBlock<com.holeydigsite.forge.CrucibleBlock> CRUCIBLE = Tiers.block("crucible");

    /** {@code holeydigsite:mold} - adjustable ingot mold, see {@link com.holeydigsite.forge.MoldBlock}. */
    public static final DeferredBlock<com.holeydigsite.forge.MoldBlock> MOLD = Tiers.block("mold");

    // ---- Phase 13: casting upgrades (Tier 2, Crafting Forge only) ----

    /** {@code holeydigsite:hot_forge} - a Forge that melts twice as fast, see {@link com.holeydigsite.forge.HotForgeBlock}. */
    public static final DeferredBlock<com.holeydigsite.forge.HotForgeBlock> HOT_FORGE = Tiers.block("hot_forge");

    /** {@code holeydigsite:large_crucible} - 500 g, twice the melting speed, auto-pour, see {@link com.holeydigsite.forge.LargeCrucibleBlock}. */
    public static final DeferredBlock<com.holeydigsite.forge.LargeCrucibleBlock> LARGE_CRUCIBLE = Tiers.block("large_crucible");

    /** {@code holeydigsite:casting_mold_t2} - cools 3x faster and ejects the ingot, see {@link com.holeydigsite.forge.CastingMoldBlock}. */
    public static final DeferredBlock<com.holeydigsite.forge.CastingMoldBlock> CASTING_MOLD_T2 = Tiers.block("casting_mold_t2");

    // ---- Phase 10: Crafting Forge ----

    /** {@code holeydigsite:crafting_forge} - lava-lit workstation that casts recipes from pooled ingots + parts, see {@link com.holeydigsite.forge.CraftingForgeBlock}. */
    public static final DeferredBlock<com.holeydigsite.forge.CraftingForgeBlock> CRAFTING_FORGE = BLOCKS.register("crafting_forge",
            () -> new com.holeydigsite.forge.CraftingForgeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.0F, 6.0F)            // breaks by hand, drops itself + its contents
                    .sound(SoundType.ANVIL)
                    .noOcclusion()
                    .lightLevel(s -> s.getValue(com.holeydigsite.forge.CraftingForgeBlock.LIT) ? 10 : 0)
                    .pushReaction(PushReaction.BLOCK)));

    // ---- Phase 19: Smithing Anvil ----

    /** {@code holeydigsite:smithing_anvil} - lay ingots + a gem on it and strike with the Blacksmith Hammer, see {@link com.holeydigsite.smith.SmithingAnvilBlock}. */
    public static final DeferredBlock<com.holeydigsite.smith.SmithingAnvilBlock> SMITHING_ANVIL = BLOCKS.register("smithing_anvil",
            () -> new com.holeydigsite.smith.SmithingAnvilBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)            // breaks by hand, drops itself + what lies on it
                    .sound(SoundType.ANVIL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    // ---- Phase 10: Tier 2 (made in the Crafting Forge only) ----

    /** {@code holeydigsite:mining_drill_t2} - Tier 2 Mining Drill: a clump every 150 ticks; otherwise the Tier 1 rules. */
    public static final DeferredBlock<MiningDrillBlock> MINING_DRILL_T2 = Tiers.block("mining_drill_t2");

    /** {@code holeydigsite:sluice_box_t2} - Tier 2 Sluice Box: a clump every 50 ticks, queue 8, tray 18 slots. */
    public static final DeferredBlock<SluiceBoxBlock> SLUICE_BOX_T2 = Tiers.block("sluice_box_t2");

    /** {@code holeydigsite:large_pan} - holds 8 scoops, see {@link com.holeydigsite.block.LargePanBlock}. */
    public static final DeferredBlock<com.holeydigsite.block.LargePanBlock> LARGE_PAN = Tiers.block("large_pan");

    /** {@code holeydigsite:fast_clump_belt} - Fast Clump Belt: 2 blocks per second; mixes with normal belts both ways. */
    public static final DeferredBlock<ClumpBeltBlock> FAST_CLUMP_BELT = Tiers.block("fast_clump_belt");

    // Phase 12: belt family (all share ClumpBeltBlockEntity), water-powered, full height
    private static BlockBehaviour.Properties belt(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.8F, 6.0F).sound(SoundType.METAL).noOcclusion();
    }

    /** {@code holeydigsite:incline_belt} - lifts items one block (see {@link com.holeydigsite.belt.InclineBeltBlock}). */
    public static final DeferredBlock<com.holeydigsite.belt.InclineBeltBlock> INCLINE_BELT = Tiers.block("incline_belt");
    public static final DeferredBlock<com.holeydigsite.belt.InclineBeltBlock> FAST_INCLINE_BELT = Tiers.block("fast_incline_belt");
    /** {@code holeydigsite:down_incline_belt} - lowers items one block (see {@link com.holeydigsite.belt.DownInclineBeltBlock}). */
    public static final DeferredBlock<com.holeydigsite.belt.DownInclineBeltBlock> DOWN_INCLINE_BELT = Tiers.block("down_incline_belt");
    public static final DeferredBlock<com.holeydigsite.belt.DownInclineBeltBlock> FAST_DOWN_INCLINE_BELT = Tiers.block("fast_down_incline_belt");
    /** {@code holeydigsite:drop_chute} - vertical pass-through tube (see {@link com.holeydigsite.transport.DropChuteBlock}). */
    public static final DeferredBlock<com.holeydigsite.transport.DropChuteBlock> DROP_CHUTE = BLOCKS.register("drop_chute",
            () -> new com.holeydigsite.transport.DropChuteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()));
    /** {@code holeydigsite:filter_belt} - sorts matching items out of its side. */
    public static final DeferredBlock<com.holeydigsite.belt.FilterBeltBlock> FILTER_BELT = BLOCKS.register("filter_belt",
            () -> new com.holeydigsite.belt.FilterBeltBlock(belt(MapColor.COLOR_LIGHT_BLUE)));
    /** {@code holeydigsite:belt_splitter} - shares a line between left / front / right (Phase 13). */
    public static final DeferredBlock<com.holeydigsite.belt.SplitterBeltBlock> BELT_SPLITTER = BLOCKS.register("belt_splitter",
            () -> new com.holeydigsite.belt.SplitterBeltBlock(belt(MapColor.COLOR_PURPLE)));
    /** {@code holeydigsite:gem_cutter} - inline belt machine that cuts uncut gems. */
    public static final DeferredBlock<com.holeydigsite.belt.GemCutterBlock> GEM_CUTTER = BLOCKS.register("gem_cutter",
            () -> new com.holeydigsite.belt.GemCutterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(1.5F, 6.0F).sound(SoundType.WOOD).noOcclusion()));

    // ---- Phase 11: new rocks, land plots, Tier 3 drill ----

    /** {@code holeydigsite:gravelrock} - unbreakable like Dirtrock; a drill on it makes Gravel Clumps (tag {@code rock_gravel}). */
    public static final DeferredBlock<Block> GRAVELROCK = BLOCKS.registerSimpleBlock("gravelrock", rock(MapColor.STONE));

    /** {@code holeydigsite:sandrock} - unbreakable like Dirtrock; a drill on it makes Sand Clumps (tag {@code rock_sand}). */
    public static final DeferredBlock<Block> SANDROCK = BLOCKS.registerSimpleBlock("sandrock", rock(MapColor.SAND));

    // ---- Phase 16: kiosk lamps (cheap, plot-exclusive, light level 15) ----

    private static BlockBehaviour.Properties lamp(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.8F, 3.0F).sound(sound).lightLevel(s -> 15);
    }

    /** {@code holeydigsite:miners_lantern} - Deep Dig kiosk. */
    public static final DeferredBlock<Block> MINERS_LANTERN = BLOCKS.registerSimpleBlock("miners_lantern", lamp(MapColor.METAL, SoundType.LANTERN));
    /** {@code holeydigsite:river_stone_lamp} - Gravel Flats kiosk. */
    public static final DeferredBlock<Block> RIVER_STONE_LAMP = BLOCKS.registerSimpleBlock("river_stone_lamp", lamp(MapColor.STONE, SoundType.STONE));
    /** {@code holeydigsite:sandstone_lamp} - Sand Dunes kiosk. */
    public static final DeferredBlock<Block> SANDSTONE_LAMP = BLOCKS.registerSimpleBlock("sandstone_lamp", lamp(MapColor.SAND, SoundType.STONE));

    // ---- Phase 21b: Ledger-only decorative blocks (Ledger rewards; claim flag, ore cart and lucky lantern also via blueprints) ----

    private static BlockBehaviour.Properties decor(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.0F, 3.0F).sound(sound).noOcclusion();
    }

    /** {@code holeydigsite:claim_flag} - a post with a small cloth flag. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> CLAIM_FLAG = BLOCKS.register("claim_flag",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.COLOR_RED, SoundType.WOOD), com.holeydigsite.block.DecorBlock.Kind.CLAIM_FLAG));
    /** {@code holeydigsite:ore_cart} - a small mine cart full of ore chunks. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> ORE_CART = BLOCKS.register("ore_cart",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.METAL, SoundType.METAL), com.holeydigsite.block.DecorBlock.Kind.ORE_CART));
    /** {@code holeydigsite:tool_rack} - a wall rack with a pick, a shovel and a pan. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> TOOL_RACK = BLOCKS.register("tool_rack",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.WOOD, SoundType.WOOD), com.holeydigsite.block.DecorBlock.Kind.TOOL_RACK));
    /** {@code holeydigsite:lucky_lantern} - a lantern with a gold nugget inside, light level 15. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> LUCKY_LANTERN = BLOCKS.register("lucky_lantern",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.GOLD, SoundType.LANTERN).lightLevel(s -> 15),
                    com.holeydigsite.block.DecorBlock.Kind.LUCKY_LANTERN));
    /** {@code holeydigsite:gem_display_case} - a glass case on a wooden stand with three gems. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> GEM_DISPLAY_CASE = BLOCKS.register("gem_display_case",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.WOOD, SoundType.GLASS), com.holeydigsite.block.DecorBlock.Kind.GEM_DISPLAY_CASE));
    /** {@code holeydigsite:golden_pan_trophy} - a golden pan on a plinth. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> GOLDEN_PAN_TROPHY = BLOCKS.register("golden_pan_trophy",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.GOLD, SoundType.METAL), com.holeydigsite.block.DecorBlock.Kind.GOLDEN_PAN_TROPHY));

    /** {@code holeydigsite:plot_board} - a land plot's "For Sale" / "Owned" board, see {@link com.holeydigsite.plot.PlotBoardBlock}. */
    public static final DeferredBlock<com.holeydigsite.plot.PlotBoardBlock> PLOT_BOARD = BLOCKS.register("plot_board",
            () -> new com.holeydigsite.plot.PlotBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F)     // unbreakable, like the Shop Display
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)));

    /** {@code holeydigsite:mining_drill_t3} - Tier 3 Mining Drill (placeholder): a clump every 100 ticks (3x Tier 1). */
    public static final DeferredBlock<MiningDrillBlock> MINING_DRILL_T3 = Tiers.block("mining_drill_t3");

    // ---- Phase 18: own crops (vanilla crop behaviour; seeds in ModItems) ----
    private static BlockBehaviour.Properties crop() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP)
                .pushReaction(PushReaction.DESTROY);
    }

    /** {@code holeydigsite:glowroot_crop} - Deep Dig crop. */
    public static final DeferredBlock<com.holeydigsite.block.OwnCropBlock> GLOWROOT_CROP = BLOCKS.register("glowroot_crop",
            () -> new com.holeydigsite.block.OwnCropBlock(crop(), () -> ModItems.GLOWROOT_SEEDS.get()));
    /** {@code holeydigsite:riverbean_crop} - Gravel Flats crop. */
    public static final DeferredBlock<com.holeydigsite.block.OwnCropBlock> RIVERBEAN_CROP = BLOCKS.register("riverbean_crop",
            () -> new com.holeydigsite.block.OwnCropBlock(crop(), () -> ModItems.RIVERBEAN_SEEDS.get()));
    /** {@code holeydigsite:dune_melon_crop} - Sand Dunes crop. */
    public static final DeferredBlock<com.holeydigsite.block.OwnCropBlock> DUNE_MELON_CROP = BLOCKS.register("dune_melon_crop",
            () -> new com.holeydigsite.block.OwnCropBlock(crop(), () -> ModItems.DUNE_MELON_SEEDS.get()));

    /** Crafted shop blocks break by hand and drop themselves; the {@code locked} state (island shop) is enforced in code. */
    private static BlockBehaviour.Properties shopBlock(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(1.5F, 6.0F)
                .sound(sound)
                .noOcclusion();
    }
}
