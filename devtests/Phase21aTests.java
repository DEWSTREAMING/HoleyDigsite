package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.OwnCropBlock;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.forge.MoldBlockEntity;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.sluice.SluiceUpgrade;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Phase 21a (sandbox only): Prospector's Ledger core tests – milestones, ongoing Renown & caps, level curve,
 * reward claiming (components, ingot weight, full-inventory drop, 21b skip), persistence across death and save/load,
 * starter Ledger on first join, old-world grant on first open, and custom triggers.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21aTests {
    private static void check(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    /** 1. Milestones grant Renown once only, and level-up thresholds follow curve.json. */
    @GameTest(template = "empty")
    public static void milestonesGrantOnceAndCurveMatches(GameTestHelper h) {
        check(h, LedgerDefs.milestones().size() == 41, "expected 41 milestones, got " + LedgerDefs.milestones().size());
        check(h, LedgerDefs.levels().size() == 50, "expected 50 levels, got " + LedgerDefs.levels().size());

        LedgerDefs.Curve c = LedgerDefs.curve();
        check(h, c.levelForRenown(0) == 0, "0 renown = L0");
        check(h, c.levelForRenown(15) == 1, "15 renown = L1");
        check(h, c.levelForRenown(30) == 2, "30 renown = L2");
        check(h, c.levelForRenown(250) == 10, "250 renown = L10");
        check(h, c.levelForRenown(1270) == 25, "1270 renown = L25");
        check(h, c.levelForRenown(4945) == 50, "4945 renown = L50");

        ServerPlayer p = player(h);
        LedgerData d = LedgerData.get(p);
        check(h, d.renown() == 0 && d.level() == 0, "fresh player starts at 0 renown / L0");

        boolean first = LedgerManager.grantMilestone(p, HoleyDigsite.id("first_pan"));
        check(h, first && d.renown() == 10, "first_pan grants 10 renown once: " + d.renown());
        boolean second = LedgerManager.grantMilestone(p, HoleyDigsite.id("first_pan"));
        check(h, !second && d.renown() == 10, "repeating first_pan gives 0 extra renown: " + d.renown());

        LedgerManager.grantMilestone(p, HoleyDigsite.id("first_sale"));
        check(h, d.renown() == 20 && d.level() == 1, "20 renown reaches L1: level=" + d.level());
        h.succeed();
    }

    /** 2. Ongoing Renown sources grant at their configured rates and stop at their per-source caps. */
    @GameTest(template = "empty")
    public static void ongoingSourcesAndCaps(GameTestHelper h) {
        ServerPlayer p = player(h);
        LedgerData d = LedgerData.get(p);
        // Pre-complete milestones triggered by these actions so we measure pure ongoing Renown
        for (var id : LedgerDefs.milestones().keySet()) {
            d.markMilestoneCompleted(id);
        }
        d.setRenown(0);

        // Pan: 1 per 4 scoops, cap 400
        LedgerManager.recordPan(p, 1, 3);
        check(h, d.panRenown() == 0, "3 scoops = 0 renown");
        LedgerManager.recordPan(p, 1, 1);
        check(h, d.panRenown() == 1 && d.renown() == 1, "4th scoop = +1 renown");
        LedgerManager.recordPan(p, 1, 4 * 500);
        check(h, d.panRenown() == 400 && d.renown() == 400, "pan capped at 400: " + d.panRenown());

        // Sluice: 1 per 40 clumps, cap 400
        LedgerManager.recordSluice(p, 1, 40 * 500);
        check(h, d.sluiceRenown() == 400 && d.renown() == 800, "sluice capped at 400: " + d.sluiceRenown());

        // Harvest: 2 per harvest, cap 600
        for (int i = 0; i < 350; i++) {
            LedgerManager.recordHarvest(p, HoleyDigsite.id("glowroot_crop"));
        }
        check(h, d.harvestRenown() == 600 && d.renown() == 1400, "harvest capped at 600: " + d.harvestRenown());

        // Smith: 5 per piece, cap 1000
        ItemStack ring = SmithPieces.create(ModItems.SMITH_RING.get(),
                new SmithData(List.of(new SmithData.Ingot(HoleyDigsite.id("folkiron"), 100)), Optional.empty()),
                h.getLevel().registryAccess());
        for (int i = 0; i < 250; i++) {
            LedgerManager.recordSmith(p, "ring", ring);
        }
        check(h, d.smithRenown() == 1000 && d.renown() == 2400, "smith capped at 1000: " + d.smithRenown());

        // Sell: 1 per 500 copper, cap 2000
        LedgerManager.recordSale(p, 500L * 2500L, 100);
        check(h, d.sellRenown() == 2000 && d.renown() == 4400, "sell capped at 2000: " + d.sellRenown());
        h.succeed();
    }

    /** 3. Claiming rewards: components, fixed ingot weight, full-inventory drop at feet, cannot double-claim, 21b skipped cleanly. */
    @GameTest(template = "empty")
    public static void claimRewardsComponentsOverflowAnd21bSkip(GameTestHelper h) {
        ServerPlayer p = player(h);
        LedgerData d = LedgerData.get(p);

        check(h, !LedgerManager.claim(p, 1), "cannot claim L1 before reaching L1");

        // Give enough Renown to reach L20 (cumCost(20) = 900)
        d.setRenown(1000);
        check(h, d.level() >= 20, "reached at least L20: " + d.level());

        // Claim L1 (coins: "S" = 128 copper = 2 Gold Coins)
        check(h, LedgerManager.claim(p, 1), "claim L1 succeeds");
        check(h, !LedgerManager.claim(p, 1), "cannot claim L1 twice");
        check(h, p.getInventory().countItem(ModItems.GOLD_COIN.get()) == 2, "L1 gave 2 Gold Coins");
        p.getInventory().clearContent();

        // Claim L11 (4 Deep Dirt Clumps, clump_tier = 2)
        check(h, LedgerManager.claim(p, 11), "claim L11 succeeds");
        ItemStack clumps = p.getInventory().getItem(0);
        check(h, clumps.is(ModItems.DIRT_CLUMP.get()) && clumps.getCount() == 4 && DirtTier.ofClump(clumps) == 2,
                "L11 gave 4 tier-2 clumps: " + clumps + " tier=" + DirtTier.ofClump(clumps));
        p.getInventory().clearContent();

        // Claim L12 (2 Folkiron Ingots with fixed 100 g weight)
        check(h, LedgerManager.claim(p, 12), "claim L12 succeeds");
        ItemStack ingots = p.getInventory().getItem(0);
        check(h, ingots.is(ModItems.FOLKIRON_INGOT.get()) && ingots.getCount() == 2 && Selling.grams(ingots) == 100,
                "L12 gave 2x 100g Folkiron Ingots: count=" + ingots.getCount() + " g=" + Selling.grams(ingots));
        p.getInventory().clearContent();

        // Claim L10 & L20 (Phase 21b decorative block, blueprint, and title) -> must skip cleanly without crashing
        check(h, LedgerManager.claim(p, 10), "claim L10 (21b claim_flag + title) succeeds without crash");
        check(h, LedgerManager.claim(p, 20), "claim L20 (21b ore_cart + blueprint + title) succeeds without crash");

        // Full inventory test: fill all 36 main slots with cobblestone, then claim L3 (4 bread) -> drops at feet
        for (int i = 0; i < 36; i++) {
            p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        BlockPos pPos = h.absolutePos(new BlockPos(2, 2, 2));
        p.setPos(pPos.getX() + 0.5, pPos.getY(), pPos.getZ() + 0.5);
        check(h, LedgerManager.claim(p, 3), "claim L3 with full inventory succeeds");
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pPos).inflate(3.0),
                e -> e.getItem().is(Items.BREAD));
        int breadDropped = drops.stream().mapToInt(e -> e.getItem().getCount()).sum();
        check(h, breadDropped == 4, "4 bread dropped at feet when inventory full: " + breadDropped);
        drops.forEach(ItemEntity::discard);
        h.succeed();
    }

    /** 4. Persistence across death & NBT save/load, free Ledger on first join, and old-world milestone grant on first open. */
    @GameTest(template = "empty")
    public static void persistenceDeathAndOldWorldGrant(GameTestHelper h) {
        // makeMockServerPlayerInLevel places the player via PlayerList.placeNewPlayer, which fires PlayerLoggedInEvent
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        LedgerData d = LedgerData.get(p);

        // First join gave 1 Prospector's Ledger; second join does not give another
        check(h, p.getInventory().countItem(ModItems.PROSPECTORS_LEDGER.get()) == 1, "first login gives 1 Ledger");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(p));
        check(h, p.getInventory().countItem(ModItems.PROSPECTORS_LEDGER.get()) == 1, "second login does not duplicate Ledger");
        p.getInventory().clearContent();

        // Put Ledger in main hand (slot 0) first, then add items for old-world check so slot 0 isn't overwritten
        ItemStack ledger = new ItemStack(ModItems.PROSPECTORS_LEDGER.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, ledger);
        d.setLifetimeCopper(15_000);
        p.getInventory().add(IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), 100));
        p.getInventory().add(BlueprintItem.of(ModItems.MINING_DRILL.get()));
        p.getInventory().add(new ItemStack(ModItems.RUBY_LARGE.get()));
        BlockPos boardPos = h.absolutePos(new BlockPos(1, 2, 1));
        PlotData pd = PlotData.get(h.getLevel());
        pd.register("deep_dig", boardPos, new BoundingBox(boardPos));
        pd.setOwned("deep_dig", boardPos, true);

        // Right-click the Ledger -> triggers checkOldWorld
        ledger.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        pd.remove("deep_dig", boardPos);

        check(h, d.isMilestoneCompleted(HoleyDigsite.id("first_sale")), "oldWorld granted first_sale");
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("earned_10k")), "oldWorld granted earned_10k");
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("first_ingot")), "oldWorld granted first_ingot");
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("goldchro_ingot")), "oldWorld granted goldchro_ingot");
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("first_blueprint")), "oldWorld granted first_blueprint");
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("large_gem")), "oldWorld granted large_gem");
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("deep_dig")), "oldWorld granted deep_dig");
        check(h, d.panRenown() == 0 && d.sellRenown() == 0, "oldWorld leaves ongoing renown at 0");

        int renownBefore = d.renown();
        d.markLevelClaimed(1);

        // Save/load round-trip
        CompoundTag saved = d.serializeNBT(h.getLevel().registryAccess());
        LedgerData loaded = new LedgerData();
        loaded.deserializeNBT(h.getLevel().registryAccess(), saved);
        check(h, loaded.renown() == renownBefore && loaded.isLevelClaimed(1)
                && loaded.isMilestoneCompleted(HoleyDigsite.id("goldchro_ingot")), "NBT round-trip preserves all progress");

        // Death respawn restoreFrom (invokes AttachmentType.copyOnDeath and fires PlayerEvent.Clone)
        ServerPlayer respawned = player(h);
        respawned.restoreFrom(p, false);
        LedgerData afterDeath = LedgerData.get(respawned);
        check(h, afterDeath.renown() == renownBefore && afterDeath.isLevelClaimed(1)
                && afterDeath.isMilestoneCompleted(HoleyDigsite.id("deep_dig")), "death clone preserves all progress");
        h.succeed();
    }

    /** 5. Custom triggers fire from real gameplay actions (sluice wash, upgrade install, crop harvest, mold take, wearing). */
    @GameTest(template = "empty")
    public static void gameplayTriggersEndToEnd(GameTestHelper h) {
        ServerPlayer p = player(h);
        LedgerData d = LedgerData.get(p);

        // 1. Sluice washing an Abyssal clump (tier 3) + installing Seed Screen
        BlockPos sluiceRel = new BlockPos(1, 2, 1);
        h.setBlock(sluiceRel, ModBlocks.SLUICE_BOX.get().defaultBlockState());
        SluiceBoxBlockEntity sluice = (SluiceBoxBlockEntity) h.getBlockEntity(sluiceRel);
        sluice.setOwnerPlayer(p);
        sluice.addClumps(1, 3);
        sluice.washOneClump(h.getLevel(), h.absolutePos(sluiceRel));
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("first_pan")), "sluice wash triggered first_pan");
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("abyssal_rock")), "tier 3 sluice wash triggered abyssal_rock");

        ((SluiceBoxBlock) ModBlocks.SLUICE_BOX.get()).installUpgrade(
                SluiceUpgrade.SEED, new ItemStack(ModItems.SEED_SCREEN.get()),
                h.getBlockState(sluiceRel), h.getLevel(), h.absolutePos(sluiceRel), p);
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("seed_screen")), "installing Seed Screen triggered seed_screen");

        // 2. Mold taking a cooled Goldchro ingot
        BlockPos moldRel = new BlockPos(2, 2, 1);
        h.setBlock(moldRel, ModBlocks.MOLD.get().defaultBlockState().setValue(MoldBlock.SOLID, true));
        MoldBlockEntity mold = (MoldBlockEntity) h.getBlockEntity(moldRel);
        mold.pour(HoleyDigsite.id("goldchro"), 50);
        h.setBlock(moldRel, h.getBlockState(moldRel).setValue(MoldBlock.SOLID, true));
        MoldBlock.click(h.getLevel(), h.absolutePos(moldRel), p, false);
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("first_ingot"))
                && d.isMilestoneCompleted(HoleyDigsite.id("goldchro_ingot")), "taking Goldchro ingot triggered cast milestones");

        // 3. Harvesting all 3 ripe own crops
        BlockPos cropRel = new BlockPos(3, 2, 1);
        for (var crop : List.of(ModBlocks.GLOWROOT_CROP.get(), ModBlocks.RIVERBEAN_CROP.get(), ModBlocks.DUNE_MELON_CROP.get())) {
            BlockPos abs = h.absolutePos(cropRel);
            var state = crop.defaultBlockState().setValue(OwnCropBlock.AGE, OwnCropBlock.MAX_AGE);
            h.setBlock(cropRel, state);
            crop.playerWillDestroy(h.getLevel(), abs, state, p);
        }
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("first_harvest"))
                && d.isMilestoneCompleted(HoleyDigsite.id("all_crops")), "harvesting all 3 ripe crops triggered first_harvest + all_crops");

        // 4. Wearing a smithed necklace on HEAD
        ItemStack necklace = SmithPieces.create(ModItems.SMITH_NECKLACE.get(),
                new SmithData(List.of(new SmithData.Ingot(HoleyDigsite.id("folkiron"), 100),
                        new SmithData.Ingot(HoleyDigsite.id("folkiron"), 100)),
                        Optional.of(new SmithData.Gem(HoleyDigsite.id("ruby"), 2))),
                h.getLevel().registryAccess());
        p.setItemSlot(EquipmentSlot.HEAD, necklace);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent(
                p, EquipmentSlot.HEAD, ItemStack.EMPTY, necklace));
        check(h, d.isMilestoneCompleted(HoleyDigsite.id("wear_jewellery")), "equipping necklace on head triggered wear_jewellery");

        // 5. Ownerless drill -> belt -> sluice gives no line_running or sluice credit to a nearby bystander;
        //    once owned, the line grants first_line to its owner.
        ServerPlayer bystander = player(h);
        LedgerData bd = LedgerData.get(bystander);
        BlockPos drillRel = new BlockPos(1, 2, 2);
        BlockPos beltRel = new BlockPos(1, 2, 3);
        BlockPos lineSluiceRel = new BlockPos(1, 2, 4);
        BlockPos standPos = h.absolutePos(new BlockPos(2, 2, 4));
        bystander.setPos(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5);

        h.setBlock(drillRel, ModBlocks.MINING_DRILL.get().defaultBlockState()
                .setValue(com.holeydigsite.drill.MiningDrillBlock.FACING, net.minecraft.core.Direction.SOUTH));
        h.setBlock(beltRel, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(com.holeydigsite.belt.ClumpBeltBlock.FACING, net.minecraft.core.Direction.SOUTH));
        TestWater.underBelt(h, beltRel);
        h.setBlock(lineSluiceRel, ModBlocks.SLUICE_BOX.get().defaultBlockState()
                .setValue(SluiceBoxBlock.FACING, net.minecraft.core.Direction.SOUTH));

        com.holeydigsite.belt.ClumpBeltBlockEntity beltBe =
                (com.holeydigsite.belt.ClumpBeltBlockEntity) h.getBlockEntity(beltRel);
        SluiceBoxBlockEntity lineSluiceBe = (SluiceBoxBlockEntity) h.getBlockEntity(lineSluiceRel);
        check(h, beltBe.owner() == null && lineSluiceBe.owner() == null, "belt and sluice start ownerless");

        check(h, com.holeydigsite.drill.ClumpOutputs.tryOutput(
                h.getLevel(), h.absolutePos(beltRel), net.minecraft.core.Direction.NORTH, 1), "drill outputs clump onto belt");
        h.runAfterDelay(25, () -> {
            check(h, lineSluiceBe.queued() == 1, "clump reached ownerless sluice via belt");
            lineSluiceBe.washOneClump(h.getLevel(), h.absolutePos(lineSluiceRel));
            check(h, !bd.isMilestoneCompleted(HoleyDigsite.id("first_line")),
                    "bystander next to ownerless drill+belt+sluice does NOT get first_line");
            check(h, !bd.isMilestoneCompleted(HoleyDigsite.id("first_pan")) && bd.sluiceRenown() == 0,
                    "bystander next to ownerless sluice does NOT get first_pan or sluice renown");

            // Now set bystander as owner of the sluice and run one more drill clump through the belt
            lineSluiceBe.setOwnerPlayer(bystander);
            check(h, com.holeydigsite.drill.ClumpOutputs.tryOutput(
                    h.getLevel(), h.absolutePos(beltRel), net.minecraft.core.Direction.NORTH, 1), "second drill clump onto belt");
        });
        h.runAfterDelay(50, () -> {
            check(h, bd.isMilestoneCompleted(HoleyDigsite.id("first_line")),
                    "owner of sluice gets first_line when drill+belt feeds it");
            h.succeed();
        });
    }
}
