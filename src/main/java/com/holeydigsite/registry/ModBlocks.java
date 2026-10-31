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
        Tiers.registerBlocks(BLOCKS);
    }

    /** {@code holeydigsite:pan} holding up to {@link PanBlock#MAX_FILL} scoops of dirt. */
    public static final DeferredBlock<PanBlock> PAN = Tiers.block("pan");

    /**
     * {@code holeydigsite:dirtrock} acting as the island unbreakable floor replacing bedrock.
     * Mining drills must stand on it ({@link MiningDrillBlock#DRILL_BASE}).
     */
    public static final DeferredBlock<Block> DIRTROCK = BLOCKS.registerSimpleBlock("dirtrock", rock(MapColor.DIRT));

    /**
     * {@code holeydigsite:deep_dirtrock} as tier 2 rock with unbreakable properties.
     */
    public static final DeferredBlock<Block> DEEP_DIRTROCK = BLOCKS.registerSimpleBlock("deep_dirtrock",
            rock(MapColor.TERRACOTTA_BROWN));

    /** {@code holeydigsite:abyssal_dirtrock} as tier 3 rock. */
    public static final DeferredBlock<Block> ABYSSAL_DIRTROCK = BLOCKS.registerSimpleBlock("abyssal_dirtrock",
            rock(MapColor.COLOR_BLACK));

    /**
     * Bedrock-like properties shared by every Dirtrock tier.
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

    /** {@code holeydigsite:water_intake} powering a water network while it sits in water. */
    public static final DeferredBlock<WaterIntakeBlock> WATER_INTAKE = BLOCKS.register("water_intake", () -> new WaterIntakeBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()));

    /** {@code holeydigsite:water_pipe} carrying water power between intakes and machines. */
    public static final DeferredBlock<WaterPipeBlock> WATER_PIPE = BLOCKS.register("water_pipe", () -> new WaterPipeBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()));

    /** {@code holeydigsite:mining_drill} Tier 1 Mining Drill. */
    public static final DeferredBlock<MiningDrillBlock> MINING_DRILL = Tiers.block("mining_drill");

    /** {@code holeydigsite:clump_belt} flat conveyor for Dirt Clumps. */
    public static final DeferredBlock<ClumpBeltBlock> CLUMP_BELT = Tiers.block("clump_belt");

    /** {@code holeydigsite:sluice_box} Tier 1 Sluice Box for water-powered autopanning. */
    public static final DeferredBlock<SluiceBoxBlock> SLUICE_BOX = Tiers.block("sluice_box");

    /** {@code holeydigsite:sell_pad} holding up to 9 stacks to sell. */
    public static final DeferredBlock<SellPadBlock> SELL_PAD = BLOCKS.register("sell_pad", () -> new SellPadBlock(shopBlock(MapColor.WOOD, SoundType.WOOD)));

    /** {@code holeydigsite:buy_pad} holding up to 9 blueprints to buy. */
    public static final DeferredBlock<BuyPadBlock> BUY_PAD = BLOCKS.register("buy_pad", () -> new BuyPadBlock(shopBlock(MapColor.WOOD, SoundType.WOOD)));

    /** {@code holeydigsite:money_pan} showing the shop balance. */
    public static final DeferredBlock<MoneyPanBlock> MONEY_PAN = BLOCKS.register("money_pan", () -> new MoneyPanBlock(shopBlock(MapColor.METAL, SoundType.METAL)));

    /** {@code holeydigsite:shop_button} executing sales on the adjacent pads. */
    public static final DeferredBlock<ShopButtonBlock> SHOP_BUTTON = BLOCKS.register("shop_button", () -> new ShopButtonBlock(shopBlock(MapColor.COLOR_RED, SoundType.METAL)));

    /** {@code holeydigsite:shop_display} as an unbreakable blueprint display. */
    public static final DeferredBlock<ShopDisplayBlock> SHOP_DISPLAY = BLOCKS.register("shop_display", () -> new ShopDisplayBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * {@code holeydigsite:site_marker} as an invisible unbreakable marker where the mod builds named sites.
     */
    public static final DeferredBlock<com.holeydigsite.site.SiteMarkerBlock> SITE_MARKER = BLOCKS.register("site_marker",
            () -> new com.holeydigsite.site.SiteMarkerBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .noCollission()
                    .pushReaction(PushReaction.BLOCK)));

    /** {@code holeydigsite:lava_intake} powering a lava network while lava-logged. */
    public static final DeferredBlock<com.holeydigsite.power.LavaIntakeBlock> LAVA_INTAKE = BLOCKS.register("lava_intake",
            () -> new com.holeydigsite.power.LavaIntakeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .lightLevel(s -> s.getValue(com.holeydigsite.power.LavaIntakeBlock.LAVALOGGED) ? 15 : 0)
                    .pushReaction(PushReaction.BLOCK)));

    /** {@code holeydigsite:lava_pipe} carrying lava power without joining water pipes. */
    public static final DeferredBlock<com.holeydigsite.power.LavaPipeBlock> LAVA_PIPE = BLOCKS.register("lava_pipe",
            () -> new com.holeydigsite.power.LavaPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()));

    /** {@code holeydigsite:forge} acting as a lava lit hearth for the crucible. */
    public static final DeferredBlock<com.holeydigsite.forge.ForgeBlock> FORGE = Tiers.block("forge");

    /** {@code holeydigsite:crucible} melting metal on a lit forge. */
    public static final DeferredBlock<com.holeydigsite.forge.CrucibleBlock> CRUCIBLE = Tiers.block("crucible");

    /** {@code holeydigsite:mold} acting as an adjustable ingot mold. */
    public static final DeferredBlock<com.holeydigsite.forge.MoldBlock> MOLD = Tiers.block("mold");

    /** {@code holeydigsite:hot_forge} melting twice as fast. */
    public static final DeferredBlock<com.holeydigsite.forge.HotForgeBlock> HOT_FORGE = Tiers.block("hot_forge");

    /** {@code holeydigsite:large_crucible} with increased capacity and auto pour. */
    public static final DeferredBlock<com.holeydigsite.forge.LargeCrucibleBlock> LARGE_CRUCIBLE = Tiers.block("large_crucible");

    /** {@code holeydigsite:casting_mold_t2} cooling faster and ejecting ingots. */
    public static final DeferredBlock<com.holeydigsite.forge.CastingMoldBlock> CASTING_MOLD_T2 = Tiers.block("casting_mold_t2");

    /** {@code holeydigsite:crafting_forge} casting recipes from pooled ingots and parts. */
    public static final DeferredBlock<com.holeydigsite.forge.CraftingForgeBlock> CRAFTING_FORGE = BLOCKS.register("crafting_forge",
            () -> new com.holeydigsite.forge.CraftingForgeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.ANVIL)
                    .noOcclusion()
                    .lightLevel(s -> s.getValue(com.holeydigsite.forge.CraftingForgeBlock.LIT) ? 10 : 0)
                    .pushReaction(PushReaction.BLOCK)));

    /** {@code holeydigsite:smithing_anvil} for laying ingots and gems to strike with the hammer. */
    public static final DeferredBlock<com.holeydigsite.smith.SmithingAnvilBlock> SMITHING_ANVIL = BLOCKS.register("smithing_anvil",
            () -> new com.holeydigsite.smith.SmithingAnvilBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.ANVIL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    /** {@code holeydigsite:skin_bench} for previewing and applying unlocked machine skins. */
    public static final DeferredBlock<com.holeydigsite.skin.SkinBenchBlock> SKIN_BENCH = BLOCKS.register("skin_bench",
            () -> new com.holeydigsite.skin.SkinBenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 5.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** {@code holeydigsite:contract_board}, the wall board that shows a player's contracts. */
    public static final DeferredBlock<com.holeydigsite.contract.ContractBoardBlock> CONTRACT_BOARD = BLOCKS.register("contract_board",
            () -> new com.holeydigsite.contract.ContractBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 5.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** {@code holeydigsite:mining_drill_t2} as a Tier 2 Mining Drill. */
    public static final DeferredBlock<MiningDrillBlock> MINING_DRILL_T2 = Tiers.block("mining_drill_t2");

    /** {@code holeydigsite:sluice_box_t2} as a Tier 2 Sluice Box. */
    public static final DeferredBlock<SluiceBoxBlock> SLUICE_BOX_T2 = Tiers.block("sluice_box_t2");

    /** {@code holeydigsite:large_pan} holding multiple scoops. */
    public static final DeferredBlock<com.holeydigsite.block.LargePanBlock> LARGE_PAN = Tiers.block("large_pan");

    /** {@code holeydigsite:fast_clump_belt} moving faster and mixing with normal belts. */
    public static final DeferredBlock<ClumpBeltBlock> FAST_CLUMP_BELT = Tiers.block("fast_clump_belt");

    private static BlockBehaviour.Properties belt(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.8F, 6.0F).sound(SoundType.METAL).noOcclusion();
    }

    /** {@code holeydigsite:incline_belt} lifting items one block. */
    public static final DeferredBlock<com.holeydigsite.belt.InclineBeltBlock> INCLINE_BELT = Tiers.block("incline_belt");
    public static final DeferredBlock<com.holeydigsite.belt.InclineBeltBlock> FAST_INCLINE_BELT = Tiers.block("fast_incline_belt");
    /** {@code holeydigsite:down_incline_belt} lowering items one block. */
    public static final DeferredBlock<com.holeydigsite.belt.DownInclineBeltBlock> DOWN_INCLINE_BELT = Tiers.block("down_incline_belt");
    public static final DeferredBlock<com.holeydigsite.belt.DownInclineBeltBlock> FAST_DOWN_INCLINE_BELT = Tiers.block("fast_down_incline_belt");
    /** {@code holeydigsite:drop_chute} acting as a vertical pass through tube. */
    public static final DeferredBlock<com.holeydigsite.transport.DropChuteBlock> DROP_CHUTE = BLOCKS.register("drop_chute",
            () -> new com.holeydigsite.transport.DropChuteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()));
    /** {@code holeydigsite:filter_belt} sorting matching items out of its side. */
    public static final DeferredBlock<com.holeydigsite.belt.FilterBeltBlock> FILTER_BELT = BLOCKS.register("filter_belt",
            () -> new com.holeydigsite.belt.FilterBeltBlock(belt(MapColor.COLOR_LIGHT_BLUE)));
    /** {@code holeydigsite:belt_splitter} sharing a line between directions. */
    public static final DeferredBlock<com.holeydigsite.belt.SplitterBeltBlock> BELT_SPLITTER = BLOCKS.register("belt_splitter",
            () -> new com.holeydigsite.belt.SplitterBeltBlock(belt(MapColor.COLOR_PURPLE)));
    /** {@code holeydigsite:gem_cutter} cutting uncut gems inline. */
    public static final DeferredBlock<com.holeydigsite.belt.GemCutterBlock> GEM_CUTTER = BLOCKS.register("gem_cutter",
            () -> new com.holeydigsite.belt.GemCutterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(1.5F, 6.0F).sound(SoundType.WOOD).noOcclusion()));

    /** {@code holeydigsite:gravelrock} acting as unbreakable rock for gravel clumps. */
    public static final DeferredBlock<Block> GRAVELROCK = BLOCKS.registerSimpleBlock("gravelrock", rock(MapColor.STONE));

    /** {@code holeydigsite:sandrock} acting as unbreakable rock for sand clumps. */
    public static final DeferredBlock<Block> SANDROCK = BLOCKS.registerSimpleBlock("sandrock", rock(MapColor.SAND));

    /** Event rocks (drill base) and filler. */
    public static final DeferredBlock<Block> RED_DIRT_ROCK = BLOCKS.registerSimpleBlock("red_dirt_rock", rock(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<Block> MULLOCK_ROCK = BLOCKS.registerSimpleBlock("mullock_rock", rock(MapColor.COLOR_LIGHT_GRAY));
    public static final DeferredBlock<Block> OPAL_CLAY_ROCK = BLOCKS.registerSimpleBlock("opal_clay_rock", rock(MapColor.COLOR_CYAN));
    public static final DeferredBlock<Block> HARDPAN = BLOCKS.registerSimpleBlock("hardpan",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_YELLOW)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(1.5F, 3.0F)
                    .sound(SoundType.ROOTED_DIRT)
                    .noLootTable());

    private static BlockBehaviour.Properties lamp(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.8F, 3.0F).sound(sound).lightLevel(s -> 15);
    }

    /** {@code holeydigsite:miners_lantern} for the Deep Dig kiosk. */
    public static final DeferredBlock<Block> MINERS_LANTERN = BLOCKS.registerSimpleBlock("miners_lantern", lamp(MapColor.METAL, SoundType.LANTERN));
    /** {@code holeydigsite:river_stone_lamp} for the Gravel Flats kiosk. */
    public static final DeferredBlock<Block> RIVER_STONE_LAMP = BLOCKS.registerSimpleBlock("river_stone_lamp", lamp(MapColor.STONE, SoundType.STONE));
    /** {@code holeydigsite:sandstone_lamp} for the Sand Dunes kiosk. */
    public static final DeferredBlock<Block> SANDSTONE_LAMP = BLOCKS.registerSimpleBlock("sandstone_lamp", lamp(MapColor.SAND, SoundType.STONE));

    private static BlockBehaviour.Properties decor(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.0F, 3.0F).sound(sound).noOcclusion();
    }

    /** {@code holeydigsite:claim_flag} as a post with a cloth flag. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> CLAIM_FLAG = BLOCKS.register("claim_flag",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.COLOR_RED, SoundType.WOOD), com.holeydigsite.block.DecorBlock.Kind.CLAIM_FLAG));
    /** {@code holeydigsite:ore_cart} as a small mine cart full of ore chunks. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> ORE_CART = BLOCKS.register("ore_cart",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.METAL, SoundType.METAL), com.holeydigsite.block.DecorBlock.Kind.ORE_CART));
    /** {@code holeydigsite:tool_rack} as a wall rack with tools. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> TOOL_RACK = BLOCKS.register("tool_rack",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.WOOD, SoundType.WOOD), com.holeydigsite.block.DecorBlock.Kind.TOOL_RACK));
    /** {@code holeydigsite:lucky_lantern} as a lantern with a gold nugget inside. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> LUCKY_LANTERN = BLOCKS.register("lucky_lantern",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.GOLD, SoundType.LANTERN).lightLevel(s -> 15),
                    com.holeydigsite.block.DecorBlock.Kind.LUCKY_LANTERN));
    /** {@code holeydigsite:gem_display_case} as a glass case with gems. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> GEM_DISPLAY_CASE = BLOCKS.register("gem_display_case",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.WOOD, SoundType.GLASS), com.holeydigsite.block.DecorBlock.Kind.GEM_DISPLAY_CASE));
    /** {@code holeydigsite:golden_pan_trophy} as a golden pan on a plinth. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> GOLDEN_PAN_TROPHY = BLOCKS.register("golden_pan_trophy",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.GOLD, SoundType.METAL), com.holeydigsite.block.DecorBlock.Kind.GOLDEN_PAN_TROPHY));

    /** {@code holeydigsite:miners_helmet_stand}, crate-only decor: a post with a miner's helmet. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> MINERS_HELMET_STAND = BLOCKS.register("miners_helmet_stand",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.WOOD, SoundType.WOOD).lightLevel(s -> 7),
                    com.holeydigsite.block.DecorBlock.Kind.MINERS_HELMET_STAND));
    /** {@code holeydigsite:rusty_mine_bell}, crate-only decor: an old bell in a timber frame. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> RUSTY_MINE_BELL = BLOCKS.register("rusty_mine_bell",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.COLOR_ORANGE, SoundType.METAL), com.holeydigsite.block.DecorBlock.Kind.RUSTY_MINE_BELL));
    /** {@code holeydigsite:sluice_model}, crate-only decor: a small sluice box on a stand. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> SLUICE_MODEL = BLOCKS.register("sluice_model",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.WOOD, SoundType.WOOD), com.holeydigsite.block.DecorBlock.Kind.SLUICE_MODEL));
    /** {@code holeydigsite:golden_drill_statue}, crate-only decor: a gold drill on a plinth. */
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> GOLDEN_DRILL_STATUE = BLOCKS.register("golden_drill_statue",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.GOLD, SoundType.METAL), com.holeydigsite.block.DecorBlock.Kind.GOLDEN_DRILL_STATUE));

    private static BlockBehaviour.Properties crate(MapColor color, SoundType sound, float strength) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(strength, 3.0F).sound(sound).noOcclusion();
    }

    /** {@code holeydigsite:wooden_crate}, a Mining Crate: place it and right-click to open. */
    public static final DeferredBlock<com.holeydigsite.crate.CrateBlock> WOODEN_CRATE = BLOCKS.register("wooden_crate",
            () -> new com.holeydigsite.crate.CrateBlock(crate(MapColor.WOOD, SoundType.WOOD, 1.0F), "wooden_crate"));
    public static final DeferredBlock<com.holeydigsite.crate.CrateBlock> IRON_CRATE = BLOCKS.register("iron_crate",
            () -> new com.holeydigsite.crate.CrateBlock(crate(MapColor.METAL, SoundType.WOOD, 1.5F), "iron_crate"));
    public static final DeferredBlock<com.holeydigsite.crate.CrateBlock> GOLD_CRATE = BLOCKS.register("gold_crate",
            () -> new com.holeydigsite.crate.CrateBlock(crate(MapColor.GOLD, SoundType.WOOD, 1.5F), "gold_crate"));

    public static final DeferredBlock<com.holeydigsite.crate.CrateBlock> PROSPECTOR_CRATE = BLOCKS.register("prospector_crate",
            () -> new com.holeydigsite.crate.CrateBlock(crate(MapColor.WOOD, SoundType.WOOD, 1.0F), "prospector_crate"));
    /** {@code holeydigsite:crate_switch}, the plaque at spawn that turns crates and skins on or off for the player who uses it. */
    public static final DeferredBlock<com.holeydigsite.crate.CrateSwitchBlock> CRATE_SWITCH = BLOCKS.register("crate_switch",
            () -> new com.holeydigsite.crate.CrateSwitchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.0F, 5.0F).sound(SoundType.WOOD).noOcclusion()));

    // Event island: red soil, the event store's blocks, its decor and the boards
    /** {@code holeydigsite:pindan}, the event island's red topsoil. Digs with a shovel, drops nothing. */
    public static final DeferredBlock<Block> PINDAN = BLOCKS.registerSimpleBlock("pindan",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(0.6F, 3.0F).sound(SoundType.ROOTED_DIRT).noLootTable());
    public static final DeferredBlock<com.holeydigsite.sellpad.EventSellPadBlock> EVENT_SELL_PAD = BLOCKS.register("event_sell_pad",
            () -> new com.holeydigsite.sellpad.EventSellPadBlock(shopBlock(MapColor.COLOR_ORANGE, SoundType.WOOD)));
    public static final DeferredBlock<com.holeydigsite.crate.CrateBlock> OUTBACK_CRATE = BLOCKS.register("outback_crate",
            () -> new com.holeydigsite.crate.CrateBlock(crate(MapColor.COLOR_ORANGE, SoundType.WOOD, 1.0F), "outback_crate"));
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> CORRUGATED_IRON_SHEET = BLOCKS.register("corrugated_iron_sheet",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.METAL, SoundType.METAL), com.holeydigsite.block.DecorBlock.Kind.CORRUGATED_IRON_SHEET));
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> LITTLE_WINDMILL = BLOCKS.register("little_windmill",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.METAL, SoundType.METAL), com.holeydigsite.block.DecorBlock.Kind.LITTLE_WINDMILL));
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> UTE_ORE_CART = BLOCKS.register("ute_ore_cart",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.COLOR_RED, SoundType.METAL), com.holeydigsite.block.DecorBlock.Kind.UTE_ORE_CART));
    public static final DeferredBlock<com.holeydigsite.block.DecorBlock> SOUTHERN_CROSS_BANNER = BLOCKS.register("southern_cross_banner",
            () -> new com.holeydigsite.block.DecorBlock(decor(MapColor.COLOR_BLUE, SoundType.WOOL), com.holeydigsite.block.DecorBlock.Kind.SOUTHERN_CROSS_BANNER));
    public static final DeferredBlock<com.holeydigsite.block.TrophyStandBlock> WELCOME_STRANGER_STAND = BLOCKS.register("welcome_stranger_stand",
            () -> new com.holeydigsite.block.TrophyStandBlock(decor(MapColor.WOOD, SoundType.WOOD)));
    /** {@code holeydigsite:event_board} (event store) and {@code holeydigsite:leaderboard} (Trading Post), unbreakable. */
    public static final DeferredBlock<com.holeydigsite.board.ScoreBoardBlock> EVENT_BOARD = BLOCKS.register("event_board",
            () -> new com.holeydigsite.board.ScoreBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).noOcclusion(), com.holeydigsite.board.ScoreBoardBlock.Kind.EVENT));
    public static final DeferredBlock<com.holeydigsite.board.ScoreBoardBlock> LEADERBOARD = BLOCKS.register("leaderboard",
            () -> new com.holeydigsite.board.ScoreBoardBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).noOcclusion(), com.holeydigsite.board.ScoreBoardBlock.Kind.WORLD));

    /** {@code holeydigsite:plot_board} for land plot status. */
    public static final DeferredBlock<com.holeydigsite.plot.PlotBoardBlock> PLOT_BOARD = BLOCKS.register("plot_board",
            () -> new com.holeydigsite.plot.PlotBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)));

    /** {@code holeydigsite:mining_drill_t3} as a Tier 3 Mining Drill. */
    public static final DeferredBlock<MiningDrillBlock> MINING_DRILL_T3 = Tiers.block("mining_drill_t3");

    private static BlockBehaviour.Properties crop() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP)
                .pushReaction(PushReaction.DESTROY);
    }

    /** {@code holeydigsite:glowroot_crop} for the Deep Dig. */
    public static final DeferredBlock<com.holeydigsite.block.OwnCropBlock> GLOWROOT_CROP = BLOCKS.register("glowroot_crop",
            () -> new com.holeydigsite.block.OwnCropBlock(crop(), () -> ModItems.GLOWROOT_SEEDS.get()));
    /** {@code holeydigsite:riverbean_crop} for the Gravel Flats. */
    public static final DeferredBlock<com.holeydigsite.block.OwnCropBlock> RIVERBEAN_CROP = BLOCKS.register("riverbean_crop",
            () -> new com.holeydigsite.block.OwnCropBlock(crop(), () -> ModItems.RIVERBEAN_SEEDS.get()));
    /** {@code holeydigsite:dune_melon_crop} for the Sand Dunes. */
    public static final DeferredBlock<com.holeydigsite.block.OwnCropBlock> DUNE_MELON_CROP = BLOCKS.register("dune_melon_crop",
            () -> new com.holeydigsite.block.OwnCropBlock(crop(), () -> ModItems.DUNE_MELON_SEEDS.get()));

    private static BlockBehaviour.Properties shopBlock(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(1.5F, 6.0F)
                .sound(sound)
                .noOcclusion();
    }
}
