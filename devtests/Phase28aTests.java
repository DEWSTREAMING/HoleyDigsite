package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.drill.ClumpOutputs;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.economy.EconomyCommands;
import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.event.EventDefs;
import com.holeydigsite.forge.CraftingForgeBlockEntity;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.Metals;
import com.holeydigsite.forge.MoldBlockEntity;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.sellpad.SellPadBlock;
import com.holeydigsite.smith.SmithingAnvilBlockEntity;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 28a: Outback Event content tests:
 * - 3 event rocks are drill base; hardpan is not and drops nothing.
 * - Drills attach event_dug component on active event rocks; mark survives clump belt.
 * - Marked clumps pan as event kinds (9, 10, 11); unmarked pan as tier 1 dirt (1); marked and unmarked do not stack.
 * - Event loot tables (red_dirt, mullock, opal_clay) only drop their tier finds.
 * - Outback Gold melts and casts into ingots; Welcome Stranger does not melt.
 * - Station refusals: Sell Pad refuses event finds; Crafting Forge refuses event metal; Anvil refuses event metal and gems.
 * - Admin give commands skip and refuse event items.
 * - Event definitions load from index.json and outback.json with all ids existing and valid chip values.
 * - Old kinds 1 to 8 still pan as before.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase28aTests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = 0; z < 5; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /** Each event rock is in drill_base; Hardpan is not, and drops nothing. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eventRocksAreDrillBaseAndHardpanDropsNothing(GameTestHelper h) {
        clear(h);

        check(h, ModBlocks.RED_DIRT_ROCK.get().defaultBlockState().is(MiningDrillBlock.DRILL_BASE),
                "red_dirt_rock must be in drill_base tag");
        check(h, ModBlocks.MULLOCK_ROCK.get().defaultBlockState().is(MiningDrillBlock.DRILL_BASE),
                "mullock_rock must be in drill_base tag");
        check(h, ModBlocks.OPAL_CLAY_ROCK.get().defaultBlockState().is(MiningDrillBlock.DRILL_BASE),
                "opal_clay_rock must be in drill_base tag");
        check(h, !ModBlocks.HARDPAN.get().defaultBlockState().is(MiningDrillBlock.DRILL_BASE),
                "hardpan must NOT be in drill_base tag");

        // Hardpan drops nothing when mined
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, ModBlocks.HARDPAN.get().defaultBlockState());
        ServerLevel level = h.getLevel();
        BlockPos absPos = h.absolutePos(pos);
        BlockState state = level.getBlockState(absPos);
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(absPos))
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.DIAMOND_PICKAXE));
        List<ItemStack> drops = state.getDrops(params);
        check(h, drops.isEmpty(), "hardpan must drop nothing when mined, got: " + drops);

        h.succeed();
    }

    /** A drill on active event rock makes a marked clump; mark survives a clump belt. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void drillOutputsMarkedClumpAndSurvivesBelt(GameTestHelper h) {
        clear(h);

        EventDefs.setActiveEventForTest("outback");

        ItemStack redClump = ClumpOutputs.drillClump(DirtTier.RED_DIRT);
        check(h, redClump.is(ModItems.RED_DIRT_CLUMP.get()), "drill on red dirt rock must produce red_dirt_clump");
        check(h, "outback".equals(redClump.get(ModDataComponents.EVENT_DUG.get())),
                "red_dirt_clump must have event_dug = outback");

        ItemStack mullockClump = ClumpOutputs.drillClump(DirtTier.MULLOCK);
        check(h, mullockClump.is(ModItems.MULLOCK_CLUMP.get()), "drill on mullock rock must produce mullock_clump");
        check(h, "outback".equals(mullockClump.get(ModDataComponents.EVENT_DUG.get())),
                "mullock_clump must have event_dug = outback");

        ItemStack clayClump = ClumpOutputs.drillClump(DirtTier.OPAL_CLAY);
        check(h, clayClump.is(ModItems.OPAL_CLAY_CLUMP.get()), "drill on opal clay rock must produce opal_clay_clump");
        check(h, "outback".equals(clayClump.get(ModDataComponents.EVENT_DUG.get())),
                "opal_clay_clump must have event_dug = outback");

        // Belt test: insert marked clump into clump belt, verify mark survives
        BlockPos beltPos = new BlockPos(2, 1, 2);
        h.setBlock(beltPos, ModBlocks.CLUMP_BELT.get().defaultBlockState());
        if (h.getBlockEntity(beltPos) instanceof ClumpBeltBlockEntity belt) {
            belt.accept(Direction.NORTH, 0, redClump.copy());
            check(h, belt.clumpCount() > 0, "belt should hold clump");
            ItemStack held = belt.clumps().get(0).stack();
            check(h, "outback".equals(held.get(ModDataComponents.EVENT_DUG.get())),
                    "mark must survive on clump belt");
        } else {
            h.fail("Missing ClumpBeltBlockEntity at " + beltPos);
        }

        EventDefs.setActiveEventForTest(null);
        h.succeed();
    }

    /** A marked clump rolls event table; an unmarked one rolls tier 1 dirt; marked and unmarked do not stack. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void markedClumpPansEventTableUnmarkedPansT1AndNoStack(GameTestHelper h) {
        clear(h);

        ItemStack markedRed = new ItemStack(ModItems.RED_DIRT_CLUMP.get());
        markedRed.set(ModDataComponents.EVENT_DUG.get(), "outback");
        ItemStack unmarkedRed = new ItemStack(ModItems.RED_DIRT_CLUMP.get());

        // Stacking check
        check(h, !ItemStack.isSameItemSameComponents(markedRed, unmarkedRed),
                "marked and unmarked red dirt clumps must not have same components");

        // Pan kinds
        check(h, DirtTier.panKind(markedRed) == DirtTier.RED_DIRT,
                "marked red dirt clump must pan as RED_DIRT (9)");
        check(h, DirtTier.panKind(unmarkedRed) == DirtTier.DIRT,
                "unmarked red dirt clump must pan as DIRT (1)");

        ItemStack markedMullock = new ItemStack(ModItems.MULLOCK_CLUMP.get());
        markedMullock.set(ModDataComponents.EVENT_DUG.get(), "outback");
        ItemStack unmarkedMullock = new ItemStack(ModItems.MULLOCK_CLUMP.get());
        check(h, DirtTier.panKind(markedMullock) == DirtTier.MULLOCK,
                "marked mullock clump must pan as MULLOCK (10)");
        check(h, DirtTier.panKind(unmarkedMullock) == DirtTier.DIRT,
                "unmarked mullock clump must pan as DIRT (1)");

        ItemStack markedClay = new ItemStack(ModItems.OPAL_CLAY_CLUMP.get());
        markedClay.set(ModDataComponents.EVENT_DUG.get(), "outback");
        ItemStack unmarkedClay = new ItemStack(ModItems.OPAL_CLAY_CLUMP.get());
        check(h, DirtTier.panKind(markedClay) == DirtTier.OPAL_CLAY,
                "marked opal clay clump must pan as OPAL_CLAY (11)");
        check(h, DirtTier.panKind(unmarkedClay) == DirtTier.DIRT,
                "unmarked opal clay clump must pan as DIRT (1)");

        // Inactive event test: drill on event rock when event is inactive does not mark
        EventDefs.setActiveEventForTest("winter_fest");
        ItemStack inactiveClump = ClumpOutputs.drillClump(DirtTier.RED_DIRT);
        check(h, !inactiveClump.has(ModDataComponents.EVENT_DUG.get()),
                "clump from drill during inactive event must not have event_dug component");
        check(h, DirtTier.panKind(inactiveClump) == DirtTier.DIRT,
                "unmarked clump from inactive event must pan as plain dirt");
        EventDefs.setActiveEventForTest(null);

        h.succeed();
    }

    /** Each of the three tables gives only its tier's finds. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void threeLootTablesGiveOnlyTheirTiersFinds(GameTestHelper h) {
        clear(h);

        ServerLevel level = h.getLevel();
        Vec3 origin = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1)));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.BRUSH))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ARCHAEOLOGY);

        Set<Item> t1Allowed = Set.of(
                ModItems.OUTBACK_GOLD_CHUNK_SMALL.get(),
                ModItems.IRONSTONE_PEBBLE.get(),
                ModItems.UNCUT_POTCH.get()
        );
        Set<Item> t2Allowed = Set.of(
                ModItems.OUTBACK_GOLD_CHUNK_SMALL.get(),
                ModItems.OUTBACK_GOLD_CHUNK_MEDIUM.get(),
                ModItems.UNCUT_BOULDER_OPAL.get(),
                ModItems.UNCUT_SAPPHIRE_PARTI.get()
        );
        Set<Item> t3Allowed = Set.of(
                ModItems.OUTBACK_GOLD_CHUNK_MEDIUM.get(),
                ModItems.OUTBACK_GOLD_CHUNK_LARGE.get(),
                ModItems.UNCUT_BLACK_OPAL.get(),
                ModItems.UNCUT_FIRE_OPAL.get(),
                ModItems.UNCUT_PINK_DIAMOND.get(),
                ModItems.WELCOME_STRANGER.get()
        );

        testTableDrops(h, level, HoleyDigsite.id("panning/red_dirt"), params, t1Allowed, "red_dirt");
        testTableDrops(h, level, HoleyDigsite.id("panning/mullock"), params, t2Allowed, "mullock");
        testTableDrops(h, level, HoleyDigsite.id("panning/opal_clay"), params, t3Allowed, "opal_clay");

        h.succeed();
    }

    private static void testTableDrops(GameTestHelper h, ServerLevel level, ResourceLocation tableId,
                                      LootParams params, Set<Item> allowed, String tableName) {
        ResourceKey<LootTable> key = ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, tableId);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        check(h, table != LootTable.EMPTY, "Loot table " + tableId + " must exist");

        for (int i = 0; i < 200; i++) {
            List<ItemStack> items = table.getRandomItems(params);
            for (ItemStack s : items) {
                check(h, allowed.contains(s.getItem()),
                        "Loot table " + tableName + " dropped unallowed item: " + s.getItem());
            }
        }
    }

    /** Outback Gold melts and casts into ingots; Welcome Stranger does not melt. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void outbackGoldMeltsAndCasts(GameTestHelper h) {
        clear(h);

        // Check meltable resource metadata
        ItemStack smallChunk = new ItemStack(ModItems.OUTBACK_GOLD_CHUNK_SMALL.get());
        ItemStack medChunk = new ItemStack(ModItems.OUTBACK_GOLD_CHUNK_MEDIUM.get());
        ItemStack largeChunk = new ItemStack(ModItems.OUTBACK_GOLD_CHUNK_LARGE.get());
        ItemStack trophy = new ItemStack(ModItems.WELCOME_STRANGER.get());

        ResourceInfo sInfo = Metals.meltable(smallChunk);
        check(h, sInfo != null && sInfo.material().equals(HoleyDigsite.id("outback_gold")),
                "small chunk must be meltable outback_gold");
        check(h, sInfo.grams() == 1, "small chunk must be 1 gram");

        ResourceInfo mInfo = Metals.meltable(medChunk);
        check(h, mInfo != null && mInfo.grams() == 3, "medium chunk must be 3 grams");

        ResourceInfo lInfo = Metals.meltable(largeChunk);
        check(h, lInfo != null && lInfo.grams() == 8, "large chunk must be 8 grams");

        // Welcome stranger is a trophy and must not be meltable
        check(h, Metals.meltable(trophy) == null, "welcome_stranger trophy must NOT melt");

        // Check ingot lookup for casting
        Item ingotItem = Metals.ingotFor(HoleyDigsite.id("outback_gold"));
        check(h, ingotItem == ModItems.OUTBACK_GOLD_INGOT.get(),
                "ingotFor(outback_gold) must return outback_gold_ingot");

        // Test in world crucible and mold
        BlockPos forgePos = new BlockPos(1, 1, 1);
        BlockPos cruciblePos = new BlockPos(1, 2, 1);
        BlockPos moldPos = new BlockPos(1, 2, 0);

        h.setBlock(forgePos, ModBlocks.FORGE.get().defaultBlockState().setValue(com.holeydigsite.forge.ForgeBlock.LIT, true));
        h.setBlock(cruciblePos, ModBlocks.CRUCIBLE.get().defaultBlockState().setValue(com.holeydigsite.forge.CrucibleBlock.FACING, Direction.NORTH));
        h.setBlock(moldPos, ModBlocks.MOLD.get().defaultBlockState());

        if (h.getBlockEntity(cruciblePos) instanceof CrucibleBlockEntity crucible
                && h.getBlockEntity(moldPos) instanceof MoldBlockEntity mold) {
            // Load 10g into crucible (1 large + 1 small + 1 small = 10g)
            crucible.insert(largeChunk.copy(), 1, false);
            crucible.insert(smallChunk.copy(), 1, false);
            crucible.insert(smallChunk.copy(), 1, false);
            check(h, crucible.unmelted() == 10, "crucible should hold 10g unmelted outback_gold");

            // Pour molten metal into mold
            mold.pour(HoleyDigsite.id("outback_gold"), 10);
            check(h, mold.metal().equals(HoleyDigsite.id("outback_gold")), "mold should hold outback_gold");
            check(h, mold.grams() == 10, "mold should hold 10g");

            // Empty mold yields outback_gold_ingot
            ItemStack emptied = mold.takeIngot();
            check(h, emptied.is(ModItems.OUTBACK_GOLD_INGOT.get()),
                    "emptying mold must give outback_gold_ingot, got: " + emptied);
            check(h, Selling.grams(emptied) == 10, "ingot must have weight 10g");
        } else {
            h.fail("Failed to find crucible or mold block entities");
        }

        h.succeed();
    }

    /** Stations refuse event finds / metal / gems with specific refusal messages. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stationRefusals(GameTestHelper h) {
        clear(h);

        // 1. Sell Pad refuses event finds
        BlockPos sellPadPos = new BlockPos(1, 1, 1);
        h.setBlock(sellPadPos, ModBlocks.SELL_PAD.get().defaultBlockState());
        if (ModBlocks.SELL_PAD.get() instanceof SellPadBlock sellPad) {
            ItemStack ingot = new ItemStack(ModItems.OUTBACK_GOLD_INGOT.get());
            ItemStack potch = new ItemStack(ModItems.POTCH.get());
            ItemStack pebble = new ItemStack(ModItems.IRONSTONE_PEBBLE.get());

            checkTranslatableKey(h, sellPad.refusedMessage(ingot), "message.holeydigsite.sell_pad.refused_event");
            checkTranslatableKey(h, sellPad.refusedMessage(potch), "message.holeydigsite.sell_pad.refused_event");
            checkTranslatableKey(h, sellPad.refusedMessage(pebble), "message.holeydigsite.sell_pad.refused_event");
        }

        // 2. Crafting Forge refuses event metal
        BlockPos forgePos = new BlockPos(2, 1, 1);
        h.setBlock(forgePos, ModBlocks.CRAFTING_FORGE.get().defaultBlockState());
        if (h.getBlockEntity(forgePos) instanceof CraftingForgeBlockEntity forge) {
            ItemStack ingot = new ItemStack(ModItems.OUTBACK_GOLD_INGOT.get());
            ItemStack chunk = new ItemStack(ModItems.OUTBACK_GOLD_CHUNK_SMALL.get());
            check(h, forge.check(ingot) == CraftingForgeBlockEntity.Refusal.EVENT_ITEM,
                    "Crafting Forge must refuse outback_gold_ingot with EVENT_ITEM");
            check(h, forge.check(chunk) == CraftingForgeBlockEntity.Refusal.EVENT_ITEM,
                    "Crafting Forge must refuse outback_gold_chunk with EVENT_ITEM");
        }

        // 3. Smithing Anvil refuses event metal and gems
        BlockPos anvilPos = new BlockPos(3, 1, 1);
        h.setBlock(anvilPos, ModBlocks.SMITHING_ANVIL.get().defaultBlockState());
        if (h.getBlockEntity(anvilPos) instanceof SmithingAnvilBlockEntity anvil) {
            ItemStack ingot = new ItemStack(ModItems.OUTBACK_GOLD_INGOT.get());
            ItemStack cutOpal = new ItemStack(ModItems.BOULDER_OPAL.get());
            ItemStack uncutOpal = new ItemStack(ModItems.UNCUT_BOULDER_OPAL.get());
            ItemStack diamond = new ItemStack(ModItems.PINK_DIAMOND.get());

            check(h, anvil.check(ingot) == SmithingAnvilBlockEntity.Refusal.EVENT_ITEM,
                    "Smithing Anvil must refuse outback_gold_ingot with EVENT_ITEM");
            check(h, anvil.check(cutOpal) == SmithingAnvilBlockEntity.Refusal.EVENT_ITEM,
                    "Smithing Anvil must refuse cut boulder_opal with EVENT_ITEM");
            check(h, anvil.check(uncutOpal) == SmithingAnvilBlockEntity.Refusal.EVENT_ITEM,
                    "Smithing Anvil must refuse uncut boulder_opal with EVENT_ITEM");
            check(h, anvil.check(diamond) == SmithingAnvilBlockEntity.Refusal.EVENT_ITEM,
                    "Smithing Anvil must refuse pink_diamond with EVENT_ITEM");
        }

        h.succeed();
    }

    private static void checkTranslatableKey(GameTestHelper h, Component comp, String expectedKey) {
        if (comp.getContents() instanceof TranslatableContents tc) {
            check(h, expectedKey.equals(tc.getKey()), "Expected translatable key " + expectedKey + " but got " + tc.getKey());
        } else {
            h.fail("Component is not translatable: " + comp);
        }
    }

    /** Admin give commands skip and refuse event items. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void adminGiveRefusesEventItems(GameTestHelper h) {
        clear(h);

        check(h, EconomyCommands.isEventMetal("outback_gold"), "isEventMetal(outback_gold) must be true");
        check(h, EconomyCommands.isEventMetal("outback"), "isEventMetal(outback) must be true");
        check(h, !EconomyCommands.isEventMetal("folkiron"), "isEventMetal(folkiron) must be false");

        check(h, EconomyCommands.isEventGem("potch"), "isEventGem(potch) must be true");
        check(h, EconomyCommands.isEventGem("boulder_opal"), "isEventGem(boulder_opal) must be true");
        check(h, EconomyCommands.isEventGem("sapphire_parti"), "isEventGem(sapphire_parti) must be true");
        check(h, EconomyCommands.isEventGem("black_opal"), "isEventGem(black_opal) must be true");
        check(h, EconomyCommands.isEventGem("fire_opal"), "isEventGem(fire_opal) must be true");
        check(h, EconomyCommands.isEventGem("pink_diamond"), "isEventGem(pink_diamond) must be true");
        check(h, !EconomyCommands.isEventGem("ruby"), "isEventGem(ruby) must be false");

        check(h, EconomyCommands.isEventClump("red_dirt"), "isEventClump(red_dirt) must be true");
        check(h, EconomyCommands.isEventClump("mullock"), "isEventClump(mullock) must be true");
        check(h, EconomyCommands.isEventClump("opal_clay"), "isEventClump(opal_clay) must be true");
        check(h, !EconomyCommands.isEventClump("dirt"), "isEventClump(dirt) must be false");

        check(h, !EconomyCommands.METALS.contains("outback_gold"), "METALS must not suggest outback_gold");
        check(h, !EconomyCommands.GEMS.contains("potch"), "GEMS must not suggest potch");
        check(h, !EconomyCommands.CLUMPS.contains("red_dirt"), "CLUMPS must not suggest red_dirt");

        h.succeed();
    }

    /** Event data files load, active event is outback, and all referenced ids exist. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eventDataFilesLoadAndAllIdsExist(GameTestHelper h) {
        clear(h);

        check(h, "outback".equals(EventDefs.getActiveEventId()), "active event id must be outback");
        EventDefs.EventDef ev = EventDefs.getActiveEvent();
        check(h, ev != null, "active EventDef must not be null");
        check(h, ev.tiers().size() == 3, "outback event must have exactly 3 tiers");

        for (EventDefs.EventTier tier : ev.tiers()) {
            check(h, BuiltInRegistries.BLOCK.containsKey(tier.rock()), "tier rock must exist: " + tier.rock());
            check(h, BuiltInRegistries.ITEM.containsKey(tier.clump()), "tier clump must exist: " + tier.clump());
        }

        // Chip prices check
        check(h, EventDefs.chipValue(new ItemStack(ModItems.OUTBACK_GOLD_CHUNK_SMALL.get())) == 1,
                "outback_gold_chunk_small must be worth 1 chip");
        check(h, EventDefs.chipValue(new ItemStack(ModItems.OUTBACK_GOLD_INGOT.get())) == 12,
                "outback_gold_ingot (10g) must be worth 12 chips");
        check(h, EventDefs.chipValue(new ItemStack(ModItems.IRONSTONE_PEBBLE.get())) == 1,
                "ironstone_pebble must be worth 1 chip");
        check(h, EventDefs.chipValue(new ItemStack(ModItems.POTCH.get())) == 2,
                "cut potch must be worth 2 chips");
        check(h, EventDefs.chipValue(new ItemStack(ModItems.UNCUT_POTCH.get())) == 1,
                "uncut potch must be worth 1 chip");
        check(h, EventDefs.chipValue(new ItemStack(ModItems.WELCOME_STRANGER.get())) == 360,
                "welcome_stranger must be worth 360 chips");

        // Non-event item chip value must be 0
        check(h, EventDefs.chipValue(new ItemStack(ModItems.FOLKIRON_INGOT.get())) == 0,
                "non-event item must have 0 chip value");

        h.succeed();
    }

    /** Old kinds 1 to 8 still pan as before. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oldKindsOneToEightStillPan(GameTestHelper h) {
        clear(h);

        for (int k = DirtTier.MIN; k <= 8; k++) {
            check(h, !DirtTier.isEventKind(k), "kind " + k + " must NOT be an event kind");
            ItemStack clump = DirtTier.clumps(k, 1);
            check(h, DirtTier.panKind(clump) == k, "clump kind " + k + " must pan as " + k);
        }

        for (int k = 9; k <= DirtTier.MAX; k++) {
            check(h, DirtTier.isEventKind(k), "kind " + k + " MUST be an event kind");
        }

        h.succeed();
    }
}
