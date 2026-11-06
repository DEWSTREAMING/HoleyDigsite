package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.crate.CrateBlock;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.machine.SawmillBlock;
import com.holeydigsite.machine.SawmillBlockEntity;
import com.holeydigsite.machine.WaterProcessingBlock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.skin.SkinBenchActions;
import com.holeydigsite.skin.SkinBenchBlockEntity;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import com.holeydigsite.transport.DropChuteBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * Game tests for Yard Fixes 1:
 * 1. Log insertion onto belts and drop chutes reaching Sawmill; stone places as block.
 * 2. Crate switch off blocks locked and unlocked crate opening, preserving cutters and crates.
 * 3. Legacy skin kit family IDs ("drill", "sluice") normalize and apply on the Skin Bench.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class YardFixTests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 26; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 0; z < 5; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
            for (int z = 0; z < 5; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    private static InteractionResult click(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(rel);
        return p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
    }

    private static void syncWater(GameTestHelper h, BlockPos... rel) {
        Set<BlockPos> abs = new HashSet<>();
        for (BlockPos r : rel) abs.add(h.absolutePos(r));
        WaterNetworks.recompute(h.getLevel(), abs);
    }

    // --------------------------------------------------------------------------------------------
    // 1. Crate Switch Off Tests
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void crateSwitchOffBlocksOpeningWithBoltCutters(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        ServerPlayer p = player(h);

        // Crates switch is OFF
        LedgerData.get(p).setCratesOn(false);
        h.setBlock(pos, ModBlocks.PROSPECTOR_CRATE.get().defaultBlockState().setValue(CrateBlock.LOCKED, true));

        ItemStack cutters = new ItemStack(ModItems.BOLT_CUTTERS.get(), 3);
        boolean cutRes = CrateBlock.cut((ServerLevel) h.getLevel(), h.absolutePos(pos), h.getBlockState(pos), p, cutters);

        check(h, !cutRes, "cut refused with switch off returns false");
        check(h, cutters.getCount() == 3, "cutters are preserved when crates are off");
        BlockState st = h.getBlockState(pos);
        check(h, st.getValue(CrateBlock.LOCKED) && !st.getValue(CrateBlock.OPEN), "crate remains locked and closed");

        // Turn crates switch ON
        LedgerData.get(p).setCratesOn(true);
        boolean cutResOn = CrateBlock.cut((ServerLevel) h.getLevel(), h.absolutePos(pos), h.getBlockState(pos), p, cutters);

        check(h, cutResOn, "cut succeeds with switch on returns true");
        check(h, cutters.getCount() == 2, "1 bolt cutter consumed");
        BlockState opened = h.getBlockState(pos);
        check(h, opened.getValue(CrateBlock.OPEN) && !opened.getValue(CrateBlock.LOCKED), "crate is now unlocked and open");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void crateSwitchOffBlocksUnlockedCrateOpening(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        ServerPlayer p = player(h);

        // Crates switch is OFF
        LedgerData.get(p).setCratesOn(false);
        h.setBlock(pos, ModBlocks.WOODEN_CRATE.get().defaultBlockState());

        // Empty hand attempt to open unlocked crate
        InteractionResult resOff = click(h, p, pos, ItemStack.EMPTY);
        check(h, !h.getBlockState(pos).getValue(CrateBlock.OPEN), "unlocked crate cannot be opened when crates are off");

        // Turn crates switch ON
        LedgerData.get(p).setCratesOn(true);
        InteractionResult resOn = click(h, p, pos, ItemStack.EMPTY);
        check(h, h.getBlockState(pos).getValue(CrateBlock.OPEN), "unlocked crate opens when crates are on");

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 2. Logs onto Belts and Sawmill
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long", timeoutTicks = 120)
    public static void logInsertsOntoBeltAndReachesSawmill(GameTestHelper h) {
        clear(h);
        BlockPos sawmillPos = new BlockPos(10, 1, 2);
        BlockPos beltPos = new BlockPos(10, 1, 3);
        BlockPos sawmillIntake = sawmillPos.below();

        h.setBlock(sawmillIntake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));

        h.setBlock(sawmillPos, ModBlocks.SAWMILL.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH)
                .setValue(SawmillBlock.MODE, SawmillBlock.Mode.BEAM));
        h.setBlock(beltPos, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(ClumpBeltBlock.FACING, Direction.NORTH));

        TestWater.underBelt(h, beltPos);
        syncWater(h, sawmillIntake, sawmillPos);

        SawmillBlockEntity sawmill = (SawmillBlockEntity) h.getBlockEntity(sawmillPos);
        check(h, sawmill != null && sawmill.isWaterPowered(), "sawmill is water powered");

        ClumpBeltBlockEntity belt = (ClumpBeltBlockEntity) h.getBlockEntity(beltPos);
        check(h, belt != null, "belt exists");
        check(h, h.getBlockState(beltPos).getValue(ClumpBeltBlock.WATER), "belt is water powered");

        // Verify playerInsertable agrees that oak log is an insertable item
        check(h, ClumpBeltBlock.playerInsertable(new ItemStack(Items.OAK_LOG)), "oak log is player insertable on belt");

        ServerPlayer p = player(h);
        ItemStack logStack = new ItemStack(Items.OAK_LOG, 4);

        // Click on belt with oak log
        InteractionResult res = click(h, p, beltPos, logStack);
        check(h, res.consumesAction(), "inserting log on belt consumes action");
        check(h, logStack.getCount() == 3, "one log taken from stack");

        // Let the belt convey the log into the sawmill and sawmill process it into a beam
        h.runAfterDelay(60, () -> {
            check(h, !sawmill.outputBuffer().isEmpty(), "sawmill processed the log and has output");
            ItemStack out = sawmill.outputBuffer().get(0);
            check(h, out.is(ModItems.BEAM.get()) && out.getCount() == 1, "sawmill produced 1 beam");
            h.succeed();
        });
    }

    @GameTest(template = "empty_long")
    public static void stoneBlockPlacesInsteadOfInsertingOnBelt(GameTestHelper h) {
        clear(h);
        BlockPos beltPos = new BlockPos(4, 1, 2);
        h.setBlock(beltPos, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));

        // Stone block must NOT be player insertable on clump belt
        ItemStack stone = new ItemStack(Blocks.STONE);
        check(h, !ClumpBeltBlock.playerInsertable(stone), "stone block is not insertable on belt");

        // DropChute also rejects stone from player insertion
        BlockPos chutePos = new BlockPos(6, 1, 2);
        h.setBlock(chutePos, ModBlocks.DROP_CHUTE.get().defaultBlockState());
        check(h, !ClumpBeltBlock.playerInsertable(stone), "stone block is not insertable in drop chute");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void logInsertsIntoDropChuteAndPassesToBelt(GameTestHelper h) {
        clear(h);
        BlockPos beltPos = new BlockPos(4, 1, 2);
        BlockPos chutePos = new BlockPos(4, 2, 2);

        h.setBlock(beltPos, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        TestWater.underBelt(h, beltPos);
        h.setBlock(chutePos, ModBlocks.DROP_CHUTE.get().defaultBlockState());

        ServerPlayer p = player(h);
        ItemStack logStack = new ItemStack(Items.OAK_LOG, 2);

        // Right click Drop Chute with log
        InteractionResult res = click(h, p, chutePos, logStack);
        check(h, res.consumesAction(), "drop chute accepts log insertion");
        check(h, logStack.getCount() == 1, "1 log consumed into drop chute");

        ClumpBeltBlockEntity belt = (ClumpBeltBlockEntity) h.getBlockEntity(beltPos);
        check(h, belt != null && !belt.isEmpty(), "log dropped through chute onto belt");

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 3. Machines Line Up With Belts (Input from back belt, output onto front belt)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long", timeoutTicks = 140)
    public static void machinesLineUpWithBeltsBackInfeedToFrontOutfeed(GameTestHelper h) {
        clear(h);

        // Line 1: Threshing Drum (Hemp -> Fibre)
        BlockPos frontBelt1 = new BlockPos(3, 1, 1);
        BlockPos drumPos = new BlockPos(3, 1, 2);
        BlockPos backBelt1 = new BlockPos(3, 1, 3);
        BlockPos drumIntake = drumPos.below();

        h.setBlock(drumIntake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(frontBelt1, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        h.setBlock(backBelt1, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        TestWater.underBelt(h, frontBelt1);
        TestWater.underBelt(h, backBelt1);

        // Line 2: Rope Machine (2x Fibre -> 1x Rope)
        BlockPos frontBelt2 = new BlockPos(8, 1, 1);
        BlockPos ropePos = new BlockPos(8, 1, 2);
        BlockPos backBelt2 = new BlockPos(8, 1, 3);
        BlockPos ropeIntake = ropePos.below();

        h.setBlock(ropeIntake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(frontBelt2, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        h.setBlock(ropePos, ModBlocks.ROPE_MACHINE.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        h.setBlock(backBelt2, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        TestWater.underBelt(h, frontBelt2);
        TestWater.underBelt(h, backBelt2);

        // Line 3: Sawmill (Oak Log -> Beam)
        BlockPos frontBelt3 = new BlockPos(14, 1, 1);
        BlockPos sawmillPos = new BlockPos(14, 1, 2);
        BlockPos backBelt3 = new BlockPos(14, 1, 3);
        BlockPos sawmillIntake = sawmillPos.below();

        h.setBlock(sawmillIntake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(frontBelt3, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        h.setBlock(sawmillPos, ModBlocks.SAWMILL.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH)
                .setValue(SawmillBlock.MODE, SawmillBlock.Mode.BEAM));
        h.setBlock(backBelt3, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        TestWater.underBelt(h, frontBelt3);
        TestWater.underBelt(h, backBelt3);

        syncWater(h, drumIntake, drumPos, ropeIntake, ropePos, sawmillIntake, sawmillPos);

        // Verify water power on all machines and belts
        check(h, h.getBlockState(drumPos).getValue(WaterProcessingBlock.WATER_POWERED), "threshing drum is water powered");
        check(h, h.getBlockState(ropePos).getValue(WaterProcessingBlock.WATER_POWERED), "rope machine is water powered");
        check(h, h.getBlockState(sawmillPos).getValue(WaterProcessingBlock.WATER_POWERED), "sawmill is water powered");

        // Insert ingredients onto the back belts
        ClumpBeltBlockEntity bb1 = (ClumpBeltBlockEntity) h.getBlockEntity(backBelt1);
        check(h, bb1 != null, "back belt 1 exists");
        bb1.insertByPlayer(new ItemStack(ModItems.HEMP.get()), 1);

        ClumpBeltBlockEntity bb2 = (ClumpBeltBlockEntity) h.getBlockEntity(backBelt2);
        check(h, bb2 != null, "back belt 2 exists");
        bb2.insertByPlayer(new ItemStack(ModItems.FIBRE.get()), 2);

        ClumpBeltBlockEntity bb3 = (ClumpBeltBlockEntity) h.getBlockEntity(backBelt3);
        check(h, bb3 != null, "back belt 3 exists");
        bb3.insertByPlayer(new ItemStack(Items.OAK_LOG), 1);

        // Wait for items to convey through each machine onto the front belt
        h.runAfterDelay(85, () -> {
            ClumpBeltBlockEntity fb1 = (ClumpBeltBlockEntity) h.getBlockEntity(frontBelt1);
            check(h, fb1 != null && fb1.items().stream().anyMatch(s -> s.is(ModItems.FIBRE.get())),
                    "front belt 1 received fibre from threshing drum");

            ClumpBeltBlockEntity fb2 = (ClumpBeltBlockEntity) h.getBlockEntity(frontBelt2);
            check(h, fb2 != null && fb2.items().stream().anyMatch(s -> s.is(ModItems.ROPE.get())),
                    "front belt 2 received rope from rope machine");

            ClumpBeltBlockEntity fb3 = (ClumpBeltBlockEntity) h.getBlockEntity(frontBelt3);
            check(h, fb3 != null && fb3.items().stream().anyMatch(s -> s.is(ModItems.BEAM.get())),
                    "front belt 3 received beam from sawmill");

            h.succeed();
        });
    }

    // --------------------------------------------------------------------------------------------
    // 4. Legacy Skin Kit Family Normalization ("drill" -> "mining_drill", "sluice" -> "sluice_box")
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void legacySkinKitFamiliesNormalizeAndApplyOnBench(GameTestHelper h) {
        clear(h);
        BlockPos benchPos = new BlockPos(2, 1, 2);
        h.setBlock(benchPos, ModBlocks.SKIN_BENCH.get().defaultBlockState());
        SkinBenchBlockEntity bench = (SkinBenchBlockEntity) h.getBlockEntity(benchPos);
        check(h, bench != null, "skin bench block entity exists");
        ServerPlayer p = player(h);

        ItemStack drillKit = new ItemStack(ModItems.SKIN_KIT.get(), 2);
        drillKit.set(ModDataComponents.SKIN.get(), "black_opal");
        drillKit.set(ModDataComponents.SKIN_FAMILY.get(), "drill");

        check(h, SkinDefs.DRILL.equals(SkinKitItem.family(drillKit)), "drill kit family reads as mining_drill");
        check(h, SkinDefs.DRILL.equals(drillKit.get(ModDataComponents.SKIN_FAMILY.get())), "stored family updated to mining_drill");

        drillKit.set(ModDataComponents.SKIN_FAMILY.get(), "drill");
        bench.setItem(new ItemStack(ModItems.MINING_DRILL.get()));
        SkinBenchActions.Outcome res1 = SkinBenchActions.apply(p, bench, drillKit);
        check(h, res1 == SkinBenchActions.Outcome.APPLIED, "legacy drill kit applies to mining drill");
        check(h, "black_opal".equals(bench.getItem().get(ModDataComponents.SKIN.get())), "mining drill has black_opal skin");
        check(h, SkinDefs.DRILL.equals(drillKit.get(ModDataComponents.SKIN_FAMILY.get())), "remaining kit stack has fixed mining_drill family");

        ItemStack sluiceKit = new ItemStack(ModItems.SKIN_KIT.get(), 2);
        sluiceKit.set(ModDataComponents.SKIN.get(), "rustic");
        sluiceKit.set(ModDataComponents.SKIN_FAMILY.get(), "sluice");
        bench.setItem(new ItemStack(ModItems.SLUICE_BOX_T2.get()));
        SkinBenchActions.Outcome res2 = SkinBenchActions.apply(p, bench, sluiceKit);
        check(h, res2 == SkinBenchActions.Outcome.APPLIED, "legacy sluice kit applies to sluice box");
        check(h, "rustic".equals(bench.getItem().get(ModDataComponents.SKIN.get())), "sluice box has rustic skin");
        check(h, SkinDefs.SLUICE.equals(sluiceKit.get(ModDataComponents.SKIN_FAMILY.get())), "remaining sluice kit has fixed sluice_box family");

        h.succeed();
    }
}
