package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.OwnCropBlock;
import com.holeydigsite.item.ScytheItem;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.plot.PlotBoardBlock;
import com.holeydigsite.plot.PlotBoardBlockEntity;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.power.SprinklerBlock;
import com.holeydigsite.power.SprinklerBlockEntity;
import com.holeydigsite.power.SprinklerTracker;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.power.FloodGateBlock;
import com.holeydigsite.power.FloodGateBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.FarmlandWaterManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * Phase 35 Part A: Scythe (sweep harvest, ripe only, replant from drops, Ledger credit, plot lock)
 * and Sprinkler (water network powered, no idle ticking, 9x9 FarmlandWaterManager hydration,
 * range edge, crop acceleration).
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase35Tests {

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

    private static int countInventory(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static void syncWater(GameTestHelper h, BlockPos... rel) {
        Set<BlockPos> abs = new HashSet<>();
        for (BlockPos r : rel) abs.add(h.absolutePos(r));
        WaterNetworks.recompute(h.getLevel(), abs);
    }

    private static PlotBoardBlockEntity plot(GameTestHelper h, String id, BlockPos board, int x0, int x1) {
        h.setBlock(board, ModBlocks.PLOT_BOARD.get().defaultBlockState().setValue(PlotBoardBlock.FACING, Direction.WEST));
        PlotBoardBlockEntity b = (PlotBoardBlockEntity) h.getBlockEntity(board);
        b.setPlot(id, new BlockPos(x0 - board.getX(), -board.getY(), -board.getZ()), new BlockPos(x1 - board.getX(), 4 - board.getY(), 4 - board.getZ()));
        b.register();
        return b;
    }

    private static void forgetPlot(GameTestHelper h, String id, BlockPos board) {
        PlotData.get(h.getLevel()).remove(id, h.absolutePos(board));
    }

    // --------------------------------------------------------------------------------------------
    // Scythe Tests
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void scytheHarvestsRipeOnlyAndLeavesUnripe(GameTestHelper h) {
        clear(h);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(new BlockPos(10 + dx, 1, 2 + dz), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            }
        }
        // Center: ripe wheat (age 7)
        h.setBlock(new BlockPos(10, 2, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        // Neighbor 1: ripe wheat (age 7)
        h.setBlock(new BlockPos(9, 2, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        // Neighbor 2: unripe wheat (age 3)
        h.setBlock(new BlockPos(11, 2, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        // Neighbor 3: ripe carrots (age 7)
        h.setBlock(new BlockPos(10, 2, 3), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));
        // Neighbor 4: unripe carrots (age 2)
        h.setBlock(new BlockPos(10, 2, 1), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 2));

        ServerPlayer p = player(h);
        ItemStack scythe = new ItemStack(ModItems.SCYTHE.get());
        InteractionResult res = click(h, p, new BlockPos(10, 2, 2), scythe);
        check(h, res.consumesAction(), "scythe useOn should consume action");

        // Ripe crops are replanted at age 0
        BlockState centerState = h.getBlockState(new BlockPos(10, 2, 2));
        check(h, centerState.is(Blocks.WHEAT) && centerState.getValue(CropBlock.AGE) == 0, "center ripe wheat replanted at age 0");

        BlockState ripeNeighbor = h.getBlockState(new BlockPos(9, 2, 2));
        check(h, ripeNeighbor.is(Blocks.WHEAT) && ripeNeighbor.getValue(CropBlock.AGE) == 0, "ripe neighbor wheat replanted at age 0");

        BlockState ripeCarrot = h.getBlockState(new BlockPos(10, 2, 3));
        check(h, ripeCarrot.is(Blocks.CARROTS) && ripeCarrot.getValue(CropBlock.AGE) == 0, "ripe carrot replanted at age 0");

        // Unripe crops remain untouched
        BlockState unripeWheat = h.getBlockState(new BlockPos(11, 2, 2));
        check(h, unripeWheat.is(Blocks.WHEAT) && unripeWheat.getValue(CropBlock.AGE) == 3, "unripe wheat remained untouched at age 3");

        BlockState unripeCarrot = h.getBlockState(new BlockPos(10, 2, 1));
        check(h, unripeCarrot.is(Blocks.CARROTS) && unripeCarrot.getValue(CropBlock.AGE) == 2, "unripe carrot remained untouched at age 2");

        // Player collected crops in inventory
        check(h, countInventory(p, Items.WHEAT) >= 2, "player collected wheat");
        check(h, countInventory(p, Items.CARROT) >= 1, "player collected carrot");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void scytheWorksOnOwnCrops(GameTestHelper h) {
        clear(h);
        h.setBlock(new BlockPos(10, 1, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        Block cropBlock = ModBlocks.GLOWROOT_CROP.get();
        h.setBlock(new BlockPos(10, 2, 2), cropBlock.defaultBlockState().setValue(OwnCropBlock.AGE, 3));

        ServerPlayer p = player(h);
        click(h, p, new BlockPos(10, 2, 2), new ItemStack(ModItems.SCYTHE.get()));

        BlockState stateAfter = h.getBlockState(new BlockPos(10, 2, 2));
        check(h, stateAfter.is(cropBlock) && stateAfter.getValue(OwnCropBlock.AGE) == 0, "own crop replanted at age 0");
        check(h, countInventory(p, ModItems.GLOWROOT.get()) >= 1, "player collected glowroot produce");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void scytheDoesNothingWhenCenterUnripe(GameTestHelper h) {
        clear(h);
        h.setBlock(new BlockPos(10, 1, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(10, 2, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 2));

        ServerPlayer p = player(h);
        InteractionResult res = click(h, p, new BlockPos(10, 2, 2), new ItemStack(ModItems.SCYTHE.get()));
        check(h, !res.consumesAction(), "clicking unripe center should pass");

        BlockState st = h.getBlockState(new BlockPos(10, 2, 2));
        check(h, st.is(Blocks.WHEAT) && st.getValue(CropBlock.AGE) == 2, "unripe wheat stays at age 2");
        check(h, countInventory(p, Items.WHEAT) == 0, "no wheat collected");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void scytheReplantsFromDropsAndDropsRemainder(GameTestHelper h) {
        clear(h);
        h.setBlock(new BlockPos(10, 1, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(10, 2, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        ServerPlayer p = player(h);
        click(h, p, new BlockPos(10, 2, 2), new ItemStack(ModItems.SCYTHE.get()));

        BlockState stateAfter = h.getBlockState(new BlockPos(10, 2, 2));
        check(h, stateAfter.is(Blocks.WHEAT) && stateAfter.getValue(CropBlock.AGE) == 0, "replanted at age 0");
        check(h, countInventory(p, Items.WHEAT) >= 1, "produce was delivered to player");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void scytheCreditsLedger(GameTestHelper h) {
        clear(h);
        h.setBlock(new BlockPos(10, 1, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(10, 2, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        ServerPlayer p = player(h);
        LedgerData data = LedgerData.get(p);
        check(h, !data.harvestedCrops().contains(BuiltInRegistries.BLOCK.getKey(Blocks.WHEAT)), "wheat not yet recorded in ledger");

        click(h, p, new BlockPos(10, 2, 2), new ItemStack(ModItems.SCYTHE.get()));

        check(h, data.harvestedCrops().contains(BuiltInRegistries.BLOCK.getKey(Blocks.WHEAT)), "scythe harvest credited ledger for wheat");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void scytheRespectsPlotLock(GameTestHelper h) {
        clear(h);
        BlockPos board = new BlockPos(8, 1, 0);
        try {
            plot(h, "p35_plot", board, 8, 14);
            // Plot is unbought (locked)
            h.setBlock(new BlockPos(10, 1, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(new BlockPos(10, 2, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

            ServerPlayer p = player(h);
            check(h, !PlotLock.canUse(p, h.getLevel(), h.absolutePos(new BlockPos(10, 2, 2))), "plot is locked");

            click(h, p, new BlockPos(10, 2, 2), new ItemStack(ModItems.SCYTHE.get()));

            BlockState cropAfter = h.getBlockState(new BlockPos(10, 2, 2));
            check(h, cropAfter.is(Blocks.WHEAT) && cropAfter.getValue(CropBlock.AGE) == 7, "crop in locked plot was not harvested");
            check(h, countInventory(p, Items.WHEAT) == 0, "no wheat obtained from locked plot");
        } finally {
            forgetPlot(h, "p35_plot", board);
        }
        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // Sprinkler Tests
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void sprinklerWetAndDryState(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(10, 1, 1);
        BlockPos sprinklerPos = new BlockPos(10, 1, 2);

        // Waterlogged intake connected to sprinkler
        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(sprinklerPos, ModBlocks.SPRINKLER.get().defaultBlockState());
        syncWater(h, intakePos, sprinklerPos);

        BlockState poweredState = h.getBlockState(sprinklerPos);
        check(h, poweredState.getValue(SprinklerBlock.WATER_POWERED), "sprinkler blockstate is water powered");

        SprinklerBlockEntity be = (SprinklerBlockEntity) h.getBlockEntity(sprinklerPos);
        check(h, be != null && be.isWaterPowered(), "sprinkler entity is water powered");
        check(h, SprinklerTracker.isNearWorkingSprinkler(h.getLevel(), h.absolutePos(sprinklerPos)), "tracker registered active sprinkler");
        check(h, FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), h.absolutePos(new BlockPos(12, 1, 2))), "farmland water ticket is active");

        // Remove water intake -> sprinkler becomes unpowered
        h.setBlock(intakePos, Blocks.AIR);
        syncWater(h, intakePos, sprinklerPos);

        BlockState unpoweredState = h.getBlockState(sprinklerPos);
        check(h, !unpoweredState.getValue(SprinklerBlock.WATER_POWERED), "sprinkler blockstate is unpowered after water cut");
        check(h, !be.isWaterPowered(), "sprinkler entity is unpowered");
        check(h, !SprinklerTracker.isNearWorkingSprinkler(h.getLevel(), h.absolutePos(sprinklerPos)), "tracker unregistered sprinkler");
        check(h, !FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), h.absolutePos(new BlockPos(12, 1, 2))), "ticket invalidated");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void sprinklerRangeEdge(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(10, 1, 1);
        BlockPos sprinklerPos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(sprinklerPos, ModBlocks.SPRINKLER.get().defaultBlockState());
        syncWater(h, intakePos, sprinklerPos);

        // Sprinkler is at x=10, z=2. RANGE = 4.
        // Distance 4 (in range)
        BlockPos inRangePlusX = new BlockPos(14, 1, 2);  // dx = +4
        BlockPos inRangeMinusX = new BlockPos(6, 1, 2);  // dx = -4
        // Distance 5 (out of range)
        BlockPos outRangePlusX = new BlockPos(15, 1, 2); // dx = +5
        BlockPos outRangeMinusX = new BlockPos(5, 1, 2); // dx = -5

        check(h, FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), h.absolutePos(inRangePlusX)), "dx=+4 has water ticket");
        check(h, FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), h.absolutePos(inRangeMinusX)), "dx=-4 has water ticket");
        check(h, !FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), h.absolutePos(outRangePlusX)), "dx=+5 does NOT have water ticket");
        check(h, !FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), h.absolutePos(outRangeMinusX)), "dx=-5 does NOT have water ticket");

        // Hydration check: Farmland in range (dx=4) with moisture 0 gains moisture 7 on random tick
        h.setBlock(inRangePlusX, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
        h.getBlockState(inRangePlusX).randomTick(h.getLevel(), h.absolutePos(inRangePlusX), h.getLevel().random);
        check(h, h.getBlockState(inRangePlusX).is(Blocks.FARMLAND) && h.getBlockState(inRangePlusX).getValue(FarmBlock.MOISTURE) == 7, "farmland at distance 4 hydrated to moisture 7");

        // Farmland out of range (dx=5) does NOT gain moisture and turns to dirt
        h.setBlock(outRangePlusX, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
        h.getBlockState(outRangePlusX).randomTick(h.getLevel(), h.absolutePos(outRangePlusX), h.getLevel().random);
        check(h, h.getBlockState(outRangePlusX).is(Blocks.DIRT), "farmland at distance 5 reverted to dirt (no water)");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void sprinklerNoWaterDoesNothing(GameTestHelper h) {
        clear(h);
        BlockPos sprinklerPos = new BlockPos(10, 1, 2);
        h.setBlock(sprinklerPos, ModBlocks.SPRINKLER.get().defaultBlockState());
        syncWater(h, sprinklerPos);

        BlockPos farmPos = new BlockPos(11, 1, 2);
        h.setBlock(farmPos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));

        check(h, !FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), h.absolutePos(farmPos)), "unpowered sprinkler grants no ticket");
        check(h, !SprinklerTracker.isNearWorkingSprinkler(h.getLevel(), h.absolutePos(farmPos)), "unpowered sprinkler not in tracker");

        h.getBlockState(farmPos).randomTick(h.getLevel(), h.absolutePos(farmPos), h.getLevel().random);
        check(h, h.getBlockState(farmPos).is(Blocks.DIRT), "unpowered sprinkler farmland reverts to dirt (no water)");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void sprinklerCropsSpeedUp(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(10, 1, 1);
        BlockPos sprinklerPos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(sprinklerPos, ModBlocks.SPRINKLER.get().defaultBlockState());
        syncWater(h, intakePos, sprinklerPos);

        BlockPos inRangePos = new BlockPos(11, 2, 2);
        BlockPos outRangePos = new BlockPos(20, 2, 2);
        BlockState wheatState = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1);
        h.setBlock(inRangePos, wheatState);
        h.setBlock(outRangePos, wheatState);

        check(h, SprinklerTracker.isNearWorkingSprinkler(h.getLevel(), h.absolutePos(inRangePos)), "in-range crop recognized by tracker");
        check(h, !SprinklerTracker.isNearWorkingSprinkler(h.getLevel(), h.absolutePos(outRangePos)), "out-of-range crop not recognized");

        // Test bonus growth chance over 200 trials for in-range crop
        int inRangeGrowCount = 0;
        for (int i = 0; i < 200; i++) {
            CropGrowEvent.Pre event = new CropGrowEvent.Pre(h.getLevel(), h.absolutePos(inRangePos), wheatState);
            NeoForge.EVENT_BUS.post(event);
            if (event.getResult() == CropGrowEvent.Pre.Result.GROW) {
                inRangeGrowCount++;
            }
        }
        check(h, inRangeGrowCount > 30 && inRangeGrowCount < 130, "crop growth was boosted by active sprinkler: count=" + inRangeGrowCount);

        // Test outside sprinkler range: exactly 0 forced GROW results
        int outRangeGrowCount = 0;
        for (int i = 0; i < 50; i++) {
            CropGrowEvent.Pre event = new CropGrowEvent.Pre(h.getLevel(), h.absolutePos(outRangePos), wheatState);
            NeoForge.EVENT_BUS.post(event);
            if (event.getResult() == CropGrowEvent.Pre.Result.GROW) {
                outRangeGrowCount++;
            }
        }
        check(h, outRangeGrowCount == 0, "outside sprinkler range receives 0 bonus growth events");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void sprinklerHasNoTickingBlockEntity(GameTestHelper h) {
        clear(h);
        SprinklerBlock sb = (SprinklerBlock) ModBlocks.SPRINKLER.get();
        check(h, sb.getTicker(h.getLevel(), sb.defaultBlockState(), ModBlockEntities.SPRINKLER.get()) == null,
                "sprinkler block must have no BlockEntityTicker (zero idle ticking)");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void sprinklerCleanupOnBreak(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(10, 1, 1);
        BlockPos sprinklerPos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(sprinklerPos, ModBlocks.SPRINKLER.get().defaultBlockState());
        syncWater(h, intakePos, sprinklerPos);

        check(h, SprinklerTracker.isNearWorkingSprinkler(h.getLevel(), h.absolutePos(sprinklerPos)), "tracker has active sprinkler");
        check(h, FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), h.absolutePos(new BlockPos(12, 1, 2))), "ticket is active");

        // Break sprinkler
        h.setBlock(sprinklerPos, Blocks.AIR);

        check(h, !SprinklerTracker.isNearWorkingSprinkler(h.getLevel(), h.absolutePos(sprinklerPos)), "tracker cleaned up after break");
        check(h, !FarmlandWaterManager.hasBlockWaterTicket(h.getLevel(), h.absolutePos(new BlockPos(12, 1, 2))), "ticket invalidated after break");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // ------------------------------------------------------------------------------------------------
    // Flood Gate tests (Part B)
    // ------------------------------------------------------------------------------------------------

    private static void setupWaterGate(GameTestHelper h, BlockPos gatePos, Direction facing) {
        BlockPos backPos = gatePos.relative(facing.getOpposite());
        // Encase the water intake in stone so fluid cannot flow into crops
        h.setBlock(backPos.below(), Blocks.STONE);
        h.setBlock(backPos.above(), Blocks.STONE);
        h.setBlock(backPos.relative(facing.getClockWise()), Blocks.STONE);
        h.setBlock(backPos.relative(facing.getCounterClockWise()), Blocks.STONE);
        h.setBlock(backPos.relative(facing.getOpposite()), Blocks.STONE);

        h.setBlock(backPos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(gatePos, ModBlocks.FLOOD_GATE.get().defaultBlockState().setValue(FloodGateBlock.FACING, facing));
        syncWater(h, backPos, gatePos);
    }

    @GameTest(template = "empty_long")
    public static void floodGateWashesRipeAndLeavesUnripe(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos gatePos = new BlockPos(2, 1, 2);
        setupWaterGate(h, gatePos, Direction.EAST);

        // Crop row: x=3 to 6 at y=0 farmland, y=1 crops
        h.setBlock(new BlockPos(3, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(4, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(5, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(6, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));

        // x=3: ripe wheat (age 7)
        h.setBlock(new BlockPos(3, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        // x=4: unripe wheat (age 2)
        h.setBlock(new BlockPos(4, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 2));
        // x=5: ripe carrot (age 7)
        h.setBlock(new BlockPos(5, 1, 2), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));
        // x=6: empty farmland (air)
        h.setBlock(new BlockPos(6, 1, 2), Blocks.AIR);

        FloodGateBlockEntity gate = (FloodGateBlockEntity) h.getBlockEntity(gatePos);
        gate.setOwner(p.getUUID());
        check(h, gate.triggerWash(p), "wash started");

        h.runAfterDelay(25, () -> {
            // Ripe wheat replanted at age 0
            BlockState s3 = h.getBlockState(new BlockPos(3, 1, 2));
            check(h, s3.is(Blocks.WHEAT) && s3.getValue(CropBlock.AGE) == 0, "ripe wheat harvested and replanted at age 0");

            // Unripe wheat left untouched at age 2
            BlockState s4 = h.getBlockState(new BlockPos(4, 1, 2));
            check(h, s4.is(Blocks.WHEAT) && s4.getValue(CropBlock.AGE) == 2, "unripe wheat remains untouched at age 2");

            // Ripe carrot replanted at age 0
            BlockState s5 = h.getBlockState(new BlockPos(5, 1, 2));
            check(h, s5.is(Blocks.CARROTS) && s5.getValue(CropBlock.AGE) == 0, "ripe carrot harvested and replanted at age 0");

            // No water blocks placed anywhere
            for (int x = 3; x <= 6; x++) {
                check(h, !h.getBlockState(new BlockPos(x, 1, 2)).is(Blocks.WATER), "no water placed in crop space at x=" + x);
            }

            // Drops delivered to end of row
            check(h, !h.getEntities(net.minecraft.world.entity.EntityType.ITEM).isEmpty(), "crops collected at row end");
            h.succeed();
        });
    }

    @GameTest(template = "empty_long")
    public static void floodGateRequiresWaterPower(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos gatePos = new BlockPos(2, 1, 2);

        // Gate without water intake -> unpowered
        h.setBlock(gatePos, ModBlocks.FLOOD_GATE.get().defaultBlockState().setValue(FloodGateBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(3, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(3, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        FloodGateBlockEntity gate = (FloodGateBlockEntity) h.getBlockEntity(gatePos);
        gate.setOwner(p.getUUID());
        check(h, !gate.triggerWash(p), "unpowered gate refuses to wash");
        check(h, !h.getBlockState(gatePos).getValue(FloodGateBlock.WASHING), "gate is not washing");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void floodGateHasCooldown(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos gatePos = new BlockPos(2, 1, 2);
        setupWaterGate(h, gatePos, Direction.EAST);

        h.setBlock(new BlockPos(3, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(3, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        FloodGateBlockEntity gate = (FloodGateBlockEntity) h.getBlockEntity(gatePos);
        gate.setOwner(p.getUUID());

        check(h, gate.triggerWash(p), "first wash starts");
        check(h, !gate.triggerWash(p), "second wash fails immediately due to cooldown / active wash");
        check(h, gate.cooldownUntil() > h.getLevel().getGameTime(), "cooldown timestamp set into future");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void floodGateStopsAtObstacle(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos gatePos = new BlockPos(2, 1, 2);
        setupWaterGate(h, gatePos, Direction.EAST);

        // x=3: farmland + ripe wheat
        h.setBlock(new BlockPos(3, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(3, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        // x=4: solid obstacle (cobblestone)
        h.setBlock(new BlockPos(4, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(4, 1, 2), Blocks.COBBLESTONE.defaultBlockState());

        // x=5: farmland + ripe wheat (behind obstacle)
        h.setBlock(new BlockPos(5, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(5, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        FloodGateBlockEntity gate = (FloodGateBlockEntity) h.getBlockEntity(gatePos);
        gate.setOwner(p.getUUID());
        check(h, gate.triggerWash(p), "wash starts");

        h.runAfterDelay(20, () -> {
            // First crop before obstacle harvested
            BlockState s3 = h.getBlockState(new BlockPos(3, 1, 2));
            check(h, s3.is(Blocks.WHEAT) && s3.getValue(CropBlock.AGE) == 0, "crop before obstacle was harvested");

            // Obstacle intact
            check(h, h.getBlockState(new BlockPos(4, 1, 2)).is(Blocks.COBBLESTONE), "obstacle still in place");

            // Crop behind obstacle never touched
            BlockState s5 = h.getBlockState(new BlockPos(5, 1, 2));
            check(h, s5.is(Blocks.WHEAT) && s5.getValue(CropBlock.AGE) == 7, "crop behind obstacle was untouched");
            h.succeed();
        });
    }

    @GameTest(template = "empty_long")
    public static void floodGateStopsAtGap(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos gatePos = new BlockPos(2, 1, 2);
        setupWaterGate(h, gatePos, Direction.EAST);

        // x=3: farmland + ripe wheat
        h.setBlock(new BlockPos(3, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(3, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        // x=4: GAP (stone instead of farmland)
        h.setBlock(new BlockPos(4, 0, 2), Blocks.STONE.defaultBlockState());
        h.setBlock(new BlockPos(4, 1, 2), Blocks.AIR);

        // x=5: farmland + ripe wheat (after gap)
        h.setBlock(new BlockPos(5, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(5, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        FloodGateBlockEntity gate = (FloodGateBlockEntity) h.getBlockEntity(gatePos);
        gate.setOwner(p.getUUID());
        check(h, gate.triggerWash(p), "wash starts");

        h.runAfterDelay(20, () -> {
            // First crop before gap harvested
            BlockState s3 = h.getBlockState(new BlockPos(3, 1, 2));
            check(h, s3.is(Blocks.WHEAT) && s3.getValue(CropBlock.AGE) == 0, "crop before gap harvested");

            // Crop after gap untouched
            BlockState s5 = h.getBlockState(new BlockPos(5, 1, 2));
            check(h, s5.is(Blocks.WHEAT) && s5.getValue(CropBlock.AGE) == 7, "crop after gap untouched");
            h.succeed();
        });
    }

    @GameTest(template = "empty_long")
    public static void floodGateFeedsBeltAndDropsBufferOnBreak(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos gatePos = new BlockPos(2, 1, 2);
        BlockPos beltPos = new BlockPos(2, 1, 3); // side belt at facing.getClockWise()

        setupWaterGate(h, gatePos, Direction.EAST);
        h.setBlock(beltPos, ModBlocks.CLUMP_BELT.get().defaultBlockState());
        syncWater(h, gatePos.relative(Direction.WEST), gatePos, beltPos);

        h.setBlock(new BlockPos(3, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(3, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        FloodGateBlockEntity gate = (FloodGateBlockEntity) h.getBlockEntity(gatePos);
        gate.setOwner(p.getUUID());
        check(h, gate.triggerWash(p), "wash starts");

        // After wash completes (around 10 ticks), buffer has items or is feeding belt
        h.runAfterDelay(12, () -> {
            // Break gate
            p.gameMode.destroyBlock(h.absolutePos(gatePos));
            check(h, h.getBlockState(gatePos).isAir(), "gate broken");

            // Verify items dropped in world from buffer
            check(h, !h.getEntities(net.minecraft.world.entity.EntityType.ITEM).isEmpty(), "buffer items dropped on break");
            h.succeed();
        });
    }

    @GameTest(template = "empty_long")
    public static void floodGateTriggeredByRedstone(GameTestHelper h) {
        clear(h);
        BlockPos gatePos = new BlockPos(2, 1, 2);
        setupWaterGate(h, gatePos, Direction.EAST);

        h.setBlock(new BlockPos(3, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(new BlockPos(3, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        // Place redstone block next to gate (at north side)
        h.setBlock(new BlockPos(2, 1, 1), Blocks.REDSTONE_BLOCK.defaultBlockState());

        h.runAfterDelay(5, () -> {
            check(h, h.getBlockState(gatePos).getValue(FloodGateBlock.WASHING), "redstone activated wash");
            h.succeed();
        });
    }

    @GameTest(template = "empty_long")
    public static void floodGateNoWaterBlocksPlaced(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos gatePos = new BlockPos(2, 1, 2);
        setupWaterGate(h, gatePos, Direction.EAST);

        for (int x = 3; x <= 7; x++) {
            h.setBlock(new BlockPos(x, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(new BlockPos(x, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }

        FloodGateBlockEntity gate = (FloodGateBlockEntity) h.getBlockEntity(gatePos);
        gate.setOwner(p.getUUID());
        check(h, gate.triggerWash(p), "wash starts");

        h.onEachTick(() -> {
            for (int x = 3; x <= 7; x++) {
                check(h, !h.getBlockState(new BlockPos(x, 1, 2)).is(Blocks.WATER), "no real water placed in crop space at x=" + x);
                check(h, !h.getBlockState(new BlockPos(x, 0, 2)).is(Blocks.WATER), "no real water placed in soil space at x=" + x);
            }
        });

        h.runAfterDelay(25, h::succeed);
    }

    @GameTest(template = "empty_long")
    public static void floodGateZeroIdleTicking(GameTestHelper h) {
        clear(h);
        FloodGateBlock fb = (FloodGateBlock) ModBlocks.FLOOD_GATE.get();
        BlockState idleState = fb.defaultBlockState().setValue(FloodGateBlock.WASHING, false);
        BlockState washingState = fb.defaultBlockState().setValue(FloodGateBlock.WASHING, true);

        check(h, fb.getTicker(h.getLevel(), idleState, ModBlockEntities.FLOOD_GATE.get()) == null,
                "idle flood gate must have null ticker (zero idle ticking)");
        check(h, fb.getTicker(h.getLevel(), washingState, ModBlockEntities.FLOOD_GATE.get()) != null,
                "active flood gate has ticker while washing");
        h.succeed();
    }
    @GameTest(template = "empty_long")
    public static void floodGateSavesCropsPickedMidWash(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos gatePos = new BlockPos(2, 1, 2);
        setupWaterGate(h, gatePos, Direction.EAST);
        for (int x = 3; x <= 10; x++) {
            h.setBlock(new BlockPos(x, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(new BlockPos(x, 1, 2), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        FloodGateBlockEntity gate = (FloodGateBlockEntity) h.getBlockEntity(gatePos);
        gate.setOwner(p.getUUID());
        check(h, gate.triggerWash(p), "wash started");
        // A few rows in, well before the wash reaches the end of the row
        h.runAfterDelay(FloodGateBlockEntity.STEP_TICKS * 3 + 1, () -> {
            net.minecraft.nbt.CompoundTag tag = gate.saveWithoutMetadata(h.getLevel().registryAccess());
            check(h, !tag.getList("Buffer", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),
                    "crops picked so far are in the saved buffer");
            h.succeed();
        });
    }
}
