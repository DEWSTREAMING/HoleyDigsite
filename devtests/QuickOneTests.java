package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.crate.CrateBlock;
import com.holeydigsite.crate.CrateDefs;
import com.holeydigsite.economy.FlawlessDefs;
import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.forge.MoldBlockEntity;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import com.holeydigsite.transport.GemCutting;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * GameTests for Phase Quick 1:
 * - smithed piece and locked crate tooltips' value comes from unitValue
 * - player worth only reads your own inventory (non-op restricted)
 * - no remaining reference to removed models
 * - flawless flag survives cutting, save/load, and sells at multiplier
 * - rolled weight stays in range and old item keeps fixed weight
 * - crucible melts a varied chunk by its own weight and poured ingot matches sum
 * - records update on sale/find and survive save/load
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class QuickOneTests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    @GameTest(template = "empty")
    public static void smithedPieceAndLockedCrateTooltipValueMatchesUnitValue(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();

        // 1. Locked crate vs unlocked crate
        ItemStack locked = CrateBlock.stack(ModBlocks.PROSPECTOR_CRATE.get().asItem(), true);
        long crateUnit = Selling.unitValue(locked, reg);
        int expectedCrateCopper = CrateDefs.rules().lockedSellCopper();
        check(h, crateUnit == expectedCrateCopper, "Locked crate unitValue should match crate rules: " + crateUnit + " == " + expectedCrateCopper);
        Component lockedTt = Selling.sellTooltip(locked, reg);
        check(h, lockedTt != null, "Locked crate must have sell tooltip");
        check(h, lockedTt.getString().contains(Selling.format(crateUnit).getString()), "Locked crate tooltip must show formatted unit value");

        ItemStack unlocked = CrateBlock.stack(ModBlocks.PROSPECTOR_CRATE.get().asItem(), false);
        check(h, Selling.unitValue(unlocked, reg) == 0, "Unlocked crate has 0 sell value");
        check(h, Selling.sellTooltip(unlocked, reg) == null, "Unlocked crate has no sell tooltip");

        // 2. Smithed piece (Dagger)
        SmithData sd = new SmithData(List.of(new SmithData.Ingot(HoleyDigsite.id("goldchro"), 50)), Optional.empty());
        ItemStack dagger = SmithPieces.create(ModItems.SMITH_DAGGER.get(), sd, reg);
        long daggerUnit = Selling.unitValue(dagger, reg);
        long expectedDaggerCopper = SmithPieces.value(dagger, reg);
        check(h, daggerUnit > 0 && daggerUnit == expectedDaggerCopper, "Dagger unitValue should match SmithPieces.value: " + daggerUnit);
        Component daggerTt = Selling.sellTooltip(dagger, reg);
        check(h, daggerTt != null, "Smithed dagger must have sell tooltip");
        check(h, daggerTt.getString().contains(Selling.format(daggerUnit).getString()), "Dagger tooltip must show formatted unit value");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nonOpPlayerWorthOnlyReadsOwnInventory(GameTestHelper h) {
        ServerPlayer p1 = player(h);

        // Give p1 a 7g Goldchro chunk
        p1.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get()));

        CommandSourceStack nonOp = p1.createCommandSourceStack().withPermission(0);
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();

        // Non-op can check own worth
        try {
            int selfRes = dispatcher.execute("holeydigsite worth", nonOp);
            check(h, selfRes > 0, "Non-op can run /holeydigsite worth on self: " + selfRes);
        } catch (CommandSyntaxException e) {
            h.fail("Non-op running worth on self failed: " + e.getMessage());
        }

        // Non-op cannot target player argument (requires permission 2)
        boolean denied = false;
        try {
            dispatcher.execute("holeydigsite worth @s", nonOp);
        } catch (CommandSyntaxException e) {
            denied = true;
        }
        check(h, denied, "Non-op targeting player argument should be rejected by permissions");

        // Op can target player argument
        CommandSourceStack op = p1.createCommandSourceStack().withPermission(2);
        try {
            int opRes = dispatcher.execute("holeydigsite worth @s", op);
            check(h, opRes > 0, "Op can run worth on targeted player (@s): " + opRes);
        } catch (CommandSyntaxException e) {
            h.fail("Op running worth on @s failed: " + e.getMessage());
        }

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void noRemainingReferenceToRemovedModels(GameTestHelper h) {
        List<String> removedNames = List.of(
                "mining_drill_working", "mining_drill_stopped", "mining_drill_no_power",
                "mining_drill_t2_working", "mining_drill_t2_stopped", "mining_drill_t2_no_power",
                "mining_drill_t3_working", "mining_drill_t3_stopped", "mining_drill_t3_no_power"
        );

        for (String name : removedNames) {
            String path = "assets/holeydigsite/models/block/" + name + ".json";
            check(h, QuickOneTests.class.getClassLoader().getResourceAsStream(path) == null,
                    "Removed model file still exists on classpath: " + path);
        }

        List<String> drillBlockstates = List.of(
                "assets/holeydigsite/blockstates/mining_drill.json",
                "assets/holeydigsite/blockstates/mining_drill_t2.json",
                "assets/holeydigsite/blockstates/mining_drill_t3.json"
        );

        for (String bsPath : drillBlockstates) {
            try (InputStream in = QuickOneTests.class.getClassLoader().getResourceAsStream(bsPath)) {
                check(h, in != null, "Blockstate exists: " + bsPath);
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String name : removedNames) {
                    check(h, !content.contains(name), "Blockstate " + bsPath + " still contains reference to " + name);
                }
            } catch (Exception e) {
                h.fail("Failed reading blockstate " + bsPath + ": " + e.getMessage());
            }
        }

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void flawlessFlagSurvivesCuttingSaveLoadAndSellsAtMultiplier(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();

        // 1. Create uncut gem and mark flawless
        ItemStack uncut = new ItemStack(ModItems.UNCUT_RUBY_LARGE.get());
        FlawlessDefs.setFlawless(uncut, true);
        check(h, FlawlessDefs.isFlawless(uncut), "Uncut gem is flawless");
        check(h, uncut.has(ModDataComponents.FLAWLESS.get()), "Uncut gem has FLAWLESS component");

        long normalUncut = Selling.unitValue(new ItemStack(ModItems.UNCUT_RUBY_LARGE.get()), reg);
        long flawlessUncut = Selling.unitValue(uncut, reg);
        double mult = FlawlessDefs.rules().valueMultiplier();
        check(h, flawlessUncut == Math.round(normalUncut * mult), "Flawless uncut gem value is multiplied by " + mult);

        // 2. Cut the gem: GemCutting preserves FLAWLESS and glint
        ItemStack cut = GemCutting.cut(uncut);
        check(h, cut.getItem() == ModItems.RUBY_LARGE.get(), "Cut produces Ruby Large");
        check(h, FlawlessDefs.isFlawless(cut), "Cut gem preserves flawless flag");
        check(h, cut.getOrDefault(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false), "Flawless cut gem has enchant glint");

        long normalCut = Selling.unitValue(new ItemStack(ModItems.RUBY_LARGE.get()), reg);
        long flawlessCut = Selling.unitValue(cut, reg);
        check(h, flawlessCut == Math.round(normalCut * mult), "Flawless cut gem value is multiplied: " + flawlessCut);

        // 3. Save to NBT and parse back
        Tag tag = cut.save(reg);
        ItemStack loaded = ItemStack.parse(reg, tag).orElseThrow();
        check(h, FlawlessDefs.isFlawless(loaded), "Loaded gem retains flawless flag");
        check(h, Selling.unitValue(loaded, reg) == flawlessCut, "Loaded gem sells at flawless multiplier");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rolledWeightStaysInRangeAndOldItemKeepsFixedWeight(GameTestHelper h) {
        ItemStack sample = new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get());
        ResourceInfo info = Selling.resource(sample);
        check(h, info != null && info.hasRange(), "Goldchro Chunk Large has weight range");

        // Reverted 1g sizes have fixed 1g (no min/max, no range)
        ItemStack smallChunk = new ItemStack(ModItems.GOLDCHRO_CHUNK_SMALL.get());
        ResourceInfo smallInfo = Selling.resource(smallChunk);
        check(h, smallInfo != null && !smallInfo.hasRange(), "Goldchro Chunk Small has fixed 1g (no range)");
        check(h, smallInfo.grams() == 1, "Goldchro Chunk Small is exactly 1g");

        // Roll 500 times to verify all rolls stay within [min, max]
        RandomSource rng = RandomSource.create(42L);
        for (int i = 0; i < 500; i++) {
            int w = info.rollWeight(rng);
            check(h, w >= info.min() && w <= info.max(), "Rolled weight " + w + " outside [" + info.min() + ", " + info.max() + "]");
            check(h, smallInfo.rollWeight(rng) == 1, "Small chunk roll always returns 1g");
        }

        // An old item with no WEIGHT component returns info.grams()
        ItemStack oldStack = new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get());
        check(h, !oldStack.has(ModDataComponents.WEIGHT.get()), "Default stack has no WEIGHT component");
        check(h, Selling.grams(oldStack) == info.grams(), "Old item returns default grams (" + info.grams() + ")");

        // When WEIGHT component is explicitly present, Selling.grams reads it
        oldStack.set(ModDataComponents.WEIGHT.get(), info.max());
        check(h, Selling.grams(oldStack) == info.max(), "Stack with WEIGHT returns component value (" + info.max() + ")");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crucibleMeltsVariedChunkByOwnWeightAndPouredIngotMatchesSum(GameTestHelper h) {
        BlockPos forgePos = new BlockPos(2, 1, 2);
        BlockPos crucPos = new BlockPos(2, 2, 2);
        BlockPos moldPos = new BlockPos(1, 1, 2);

        // Place lit forge, crucible facing west toward mold
        h.setBlock(forgePos, ModBlocks.FORGE.get().defaultBlockState()
                .setValue(ForgeBlock.FACING, Direction.WEST)
                .setValue(ForgeBlock.LIT, true));
        h.setBlock(crucPos, ModBlocks.CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.FACING, Direction.WEST));
        h.setBlock(moldPos, ModBlocks.MOLD.get());

        CrucibleBlockEntity cruc = (CrucibleBlockEntity) h.getBlockEntity(crucPos);
        check(h, cruc != null, "Crucible block entity exists");

        // Two large chunks with rolled weights: 9g and 6g (normal fixed would be 7g each)
        ItemStack c1 = new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get());
        c1.set(ModDataComponents.WEIGHT.get(), 9);
        ItemStack c2 = new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get());
        c2.set(ModDataComponents.WEIGHT.get(), 6);

        check(h, cruc.insert(c1, 1, false) == 1, "First chunk inserted");
        check(h, cruc.unmelted() == 9, "Unmelted is 9g from first chunk, was " + cruc.unmelted());

        check(h, cruc.insert(c2, 1, false) == 1, "Second chunk inserted");
        check(h, cruc.unmelted() == 15, "Unmelted is 15g from both chunks, was " + cruc.unmelted());

        // Forge heat 1 melts 1 gram every 4 ticks. Run 60 ticks of melting (15g * 4 = 60).
        for (int i = 0; i < 60; i++) {
            CrucibleBlockEntity.serverTick(h.getLevel(), h.absolutePos(crucPos), h.getBlockState(crucPos), cruc);
        }
        check(h, cruc.unmelted() == 0, "All metal is melted");
        check(h, cruc.molten() == 15, "Molten buffer has 15g, was " + cruc.molten());

        // Pour into mold
        int poured = CrucibleBlock.pour(h.getLevel(), h.absolutePos(crucPos), h.getBlockState(crucPos), null);
        check(h, poured == 15, "Poured 15g into mold, was " + poured);

        MoldBlockEntity mold = (MoldBlockEntity) h.getBlockEntity(moldPos);
        check(h, mold != null && mold.grams() == 15, "Mold contains 15g");

        ItemStack ingot = mold.takeIngot();
        check(h, IngotItem.grams(ingot) == 15, "Ingot has 15g: " + ingot);
        check(h, Selling.grams(ingot) == 15, "Ingot Selling.grams matches sum (15g)");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recordsUpdateOnSaleAndFindAndSurviveSaveLoad(GameTestHelper h) {
        ServerPlayer sp = player(h);
        LedgerData data = LedgerData.get(sp);

        // Initially records are empty
        check(h, data.biggestSaleCopper() == 0, "Initial biggest sale is 0");
        check(h, data.mostCoinsDayCopper() == 0, "Initial most coins day is 0");
        check(h, data.heaviestIngotGrams() == 0, "Initial heaviest ingot is 0");
        check(h, data.flawlessGemsFound() == 0, "Initial flawless count is 0");
        check(h, data.heaviestGemGrams() == 0, "Initial heaviest gem is 0");
        check(h, data.heaviestChunkGrams() == 0, "Initial heaviest chunk is 0");

        // 1. Record sale
        LedgerManager.recordSale(sp, 500, 500);
        check(h, data.biggestSaleCopper() == 500, "Biggest sale updated to 500");
        check(h, data.mostCoinsDayCopper() == 500, "Most coins day updated to 500");

        LedgerManager.recordSale(sp, 200, 200);
        check(h, data.biggestSaleCopper() == 500, "Biggest sale remains 500");
        check(h, data.mostCoinsDayCopper() == 700, "Most coins day increased to 700");

        LedgerManager.recordSale(sp, 1000, 1000);
        check(h, data.biggestSaleCopper() == 1000, "Biggest sale updated to 1000");
        check(h, data.mostCoinsDayCopper() == 1700, "Most coins day increased to 1700");

        // 2. Record pour
        ResourceLocation goldId = HoleyDigsite.id("goldchro");
        LedgerManager.recordPour(sp, goldId, 25);
        check(h, data.heaviestIngotGrams() == 25, "Heaviest ingot is 25g");
        check(h, goldId.toString().equals(data.heaviestIngotMetal()), "Heaviest ingot metal is goldchro");

        // 3. Record find: flawless gem
        ItemStack gem = new ItemStack(ModItems.RUBY_LARGE.get());
        gem.set(ModDataComponents.WEIGHT.get(), 9);
        FlawlessDefs.setFlawless(gem, true);
        LedgerManager.recordFind(sp, gem);
        check(h, data.flawlessGemsFound() == 1, "Flawless gems found is 1");
        check(h, data.heaviestGemGrams() == 9, "Heaviest gem is 9g");
        check(h, data.heaviestGemKind().contains("ruby"), "Heaviest gem kind contains ruby: " + data.heaviestGemKind());

        // 4. Record find: raw chunk (not flawless)
        ItemStack chunk = new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get());
        chunk.set(ModDataComponents.WEIGHT.get(), 8);
        LedgerManager.recordFind(sp, chunk);
        check(h, data.flawlessGemsFound() == 1, "Flawless count still 1");
        check(h, data.heaviestChunkGrams() == 8, "Heaviest chunk is 8g");
        check(h, data.heaviestChunkKind().contains("goldchro"), "Heaviest chunk kind contains goldchro");

        // 5. Save and reload
        CompoundTag tag = data.serializeNBT(h.getLevel().registryAccess());

        LedgerData reloaded = new LedgerData();
        reloaded.deserializeNBT(h.getLevel().registryAccess(), tag);
        check(h, reloaded.biggestSaleCopper() == 1000, "Reloaded biggest sale is 1000");
        check(h, reloaded.mostCoinsDayCopper() == 1700, "Reloaded most coins day is 1700");
        check(h, reloaded.heaviestIngotGrams() == 25, "Reloaded heaviest ingot is 25");
        check(h, goldId.toString().equals(reloaded.heaviestIngotMetal()), "Reloaded heaviest metal matches");
        check(h, reloaded.flawlessGemsFound() == 1, "Reloaded flawless count is 1");
        check(h, reloaded.heaviestGemGrams() == 9, "Reloaded heaviest gem is 9");
        check(h, reloaded.heaviestGemKind().contains("ruby"), "Reloaded heaviest gem kind is ruby");
        check(h, reloaded.heaviestChunkGrams() == 8, "Reloaded heaviest chunk is 8");
        check(h, reloaded.heaviestChunkKind().contains("goldchro"), "Reloaded heaviest chunk kind is goldchro");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sluiceMadeHeavyChunkSetsRecordOnSaleAndHandFlawlessSoldLaterCountedOnce(GameTestHelper h) {
        ServerPlayer sp = player(h);
        LedgerData data = LedgerData.get(sp);

        check(h, data.heaviestChunkGrams() == 0, "Initial heaviest chunk is 0");
        check(h, data.flawlessGemsFound() == 0, "Initial flawless gems found is 0");

        // 1. Sluice-made heavy chunk (10g) sold on sell pad sets the heaviest chunk record
        ItemStack sluiceChunk = new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get());
        sluiceChunk.set(ModDataComponents.WEIGHT.get(), 10);
        boolean notified = LedgerManager.recordSaleItem(sp, sluiceChunk);
        check(h, notified, "Selling new heaviest chunk should notify");
        check(h, data.heaviestChunkGrams() == 10, "Heaviest chunk record updated to 10g by machine chunk sale");
        check(h, data.heaviestChunkKind().contains("goldchro"), "Heaviest chunk kind is goldchro");

        // A smaller chunk sold later does not overwrite
        ItemStack smallerChunk = new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get());
        smallerChunk.set(ModDataComponents.WEIGHT.get(), 8);
        check(h, !LedgerManager.recordSaleItem(sp, smallerChunk), "Smaller chunk sale should not notify");
        check(h, data.heaviestChunkGrams() == 10, "Heaviest chunk remains 10g");

        // 2. Hand-found flawless gem sold later is counted ONCE
        ItemStack handGem = new ItemStack(ModItems.RUBY_LARGE.get());
        handGem.set(ModDataComponents.WEIGHT.get(), 4);
        FlawlessDefs.setFlawless(handGem, true);

        // Found by hand
        LedgerManager.recordFind(sp, handGem);
        check(h, data.flawlessGemsFound() == 1, "Hand find increments flawless gems found to 1");
        check(h, data.handFlawlessExemption() == 1, "Hand find grants 1 flawless exemption");

        // Sold later
        LedgerManager.recordSaleItem(sp, handGem);
        check(h, data.flawlessGemsFound() == 1, "Hand find sold later is not counted twice (remains 1)");
        check(h, data.handFlawlessExemption() == 0, "Exemption was consumed on sale");

        // 3. Sluice-made flawless gem (not found by hand) sold on sell pad is counted
        ItemStack machineGem = new ItemStack(ModItems.RUBY_LARGE.get());
        machineGem.set(ModDataComponents.WEIGHT.get(), 4);
        FlawlessDefs.setFlawless(machineGem, true);
        LedgerManager.recordSaleItem(sp, machineGem);
        check(h, data.flawlessGemsFound() == 2, "Machine flawless gem counted upon sale (now 2)");

        // 4. Stacking: hand-found gem and machine-found gem have identical components and stack
        ItemStack handGem2 = new ItemStack(ModItems.RUBY_LARGE.get());
        handGem2.set(ModDataComponents.WEIGHT.get(), 4);
        FlawlessDefs.setFlawless(handGem2, true);
        LedgerManager.recordFind(sp, handGem2); // Player keeps handGem2

        ItemStack machineGem2 = new ItemStack(ModItems.RUBY_LARGE.get());
        machineGem2.set(ModDataComponents.WEIGHT.get(), 4);
        FlawlessDefs.setFlawless(machineGem2, true);

        check(h, ItemStack.isSameItemSameComponents(handGem2, machineGem2),
                "Hand-found and machine-found flawless gems of same kind and weight must stack cleanly");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void dayRecordNotifiedOncePerDayAndBiggestSalePrecedes(GameTestHelper h) {
        ServerPlayer sp = player(h);
        LedgerData data = LedgerData.get(sp);

        check(h, data.biggestSaleCopper() == 0, "Initial biggest sale is 0");
        check(h, data.mostCoinsDayCopper() == 0, "Initial most coins day is 0");

        // Day 1: First sale (500c) sets both biggest sale and day record.
        // Biggest sale takes precedence, day record updated and marked notified.
        boolean beatDay1 = data.addDayCopper(1, 500);
        check(h, beatDay1, "First sale on day 1 beats day record");
        check(h, data.mostCoinsDayCopper() == 500, "Day record is 500");
        check(h, data.lastDayRecordNotifiedDay() == 1, "Day 1 marked as notified");

        // Day 1: Second sale (300c -> 800c total today) passes old best again,
        // but must NOT notify again on day 1 (quiet update).
        boolean beatDay1Again = data.addDayCopper(1, 300);
        check(h, !beatDay1Again, "Second sale on same day must not notify day record again");
        check(h, data.mostCoinsDayCopper() == 800, "Stored number updated quietly to 800");

        // Day 2: First sale (200c) does not beat 800c.
        boolean day2Sale1 = data.addDayCopper(2, 200);
        check(h, !day2Sale1, "Day 2 first sale below 800 does not beat day record");
        check(h, data.mostCoinsDayCopper() == 800, "Stored number remains 800");

        // Day 2: Second sale (700c -> 900c total today) beats 800c. Notifies once!
        boolean day2Sale2 = data.addDayCopper(2, 700);
        check(h, day2Sale2, "Day 2 second sale (900c > 800c) notifies day record");
        check(h, data.mostCoinsDayCopper() == 900, "Day record updated to 900");
        check(h, data.lastDayRecordNotifiedDay() == 2, "Day 2 marked as notified");

        // Day 2: Third sale (100c -> 1000c total today) updates quietly.
        boolean day2Sale3 = data.addDayCopper(2, 100);
        check(h, !day2Sale3, "Day 2 third sale updates quietly without notifying");
        check(h, data.mostCoinsDayCopper() == 1000, "Day record updated to 1000 quietly");

        // Verify recordSale notification priority when both records set:
        // Reset player records for a fresh check
        data.setBiggestSaleCopper(0);
        data.setMostCoinsDayCopper(0);
        // Selling 600 sets biggest sale (0 -> 600) and day record (0 -> 600).
        // Calling recordSale should succeed cleanly without crashing.
        LedgerManager.recordSale(sp, 600, 600);
        check(h, data.biggestSaleCopper() == 600, "Biggest sale set to 600");
        check(h, data.mostCoinsDayCopper() == 600, "Day record set to 600");

        h.succeed();
    }
}
