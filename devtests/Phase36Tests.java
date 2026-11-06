package com.holeydigsite.devtest;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.OwnCropBlock;
import com.holeydigsite.machine.*;
import com.holeydigsite.plot.PlotBoardBlock;
import com.holeydigsite.plot.PlotBoardBlockEntity;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.team.TeamAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 36: Hemp crop, Threshing Drum, Rope Machine, Sawmill.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase36Tests {

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
    // 1. Hemp Crop Tests
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void hempCropGrowsAndHarvestsByHand(GameTestHelper h) {
        clear(h);
        BlockPos farmPos = new BlockPos(10, 1, 2);
        BlockPos cropPos = new BlockPos(10, 2, 2);

        h.setBlock(farmPos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(cropPos, ModBlocks.HEMP_CROP.get().defaultBlockState().setValue(OwnCropBlock.AGE, 0));

        check(h, h.getBlockState(cropPos).is(ModBlocks.HEMP_CROP.get()), "hemp planted on farmland");
        check(h, h.getBlockState(cropPos).getValue(OwnCropBlock.AGE) == 0, "hemp starts at age 0");

        // Advance to full growth (age 3)
        h.setBlock(cropPos, ModBlocks.HEMP_CROP.get().defaultBlockState().setValue(OwnCropBlock.AGE, 3));
        check(h, h.getBlockState(cropPos).getValue(OwnCropBlock.AGE) == 3, "hemp reaches age 3");

        // Break the ripe crop dropping loot
        h.getLevel().destroyBlock(h.absolutePos(cropPos), true);

        // Check dropped items
        AABB box = new AABB(h.absolutePos(cropPos)).inflate(2.0);
        List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class, box);
        boolean hasHemp = false;
        boolean hasSeeds = false;
        for (ItemEntity e : items) {
            if (e.getItem().is(ModItems.HEMP.get())) hasHemp = true;
            if (e.getItem().is(ModItems.HEMP_SEEDS.get())) hasSeeds = true;
        }
        check(h, hasHemp, "breaking ripe hemp drops hemp stalk");
        check(h, hasSeeds, "breaking ripe hemp drops hemp seeds");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void hempCropHarvestsWithScythe(GameTestHelper h) {
        clear(h);
        BlockPos farmPos = new BlockPos(10, 1, 2);
        BlockPos cropPos = new BlockPos(10, 2, 2);

        h.setBlock(farmPos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(cropPos, ModBlocks.HEMP_CROP.get().defaultBlockState().setValue(OwnCropBlock.AGE, 3));

        ServerPlayer p = player(h);
        InteractionResult res = click(h, p, cropPos, new ItemStack(ModItems.SCYTHE.get()));
        check(h, res.consumesAction(), "scythe interaction succeeded");

        // Crop should be reset to age 0 (replanted)
        BlockState stateAfter = h.getBlockState(cropPos);
        check(h, stateAfter.is(ModBlocks.HEMP_CROP.get()), "hemp remains planted");
        check(h, stateAfter.getValue(OwnCropBlock.AGE) == 0, "hemp reset to age 0");

        // Player collects hemp produce directly into inventory
        int hemp = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModItems.HEMP.get())) hemp += s.getCount();
        }
        check(h, hemp >= 1, "scythe harvest drops hemp stalk into inventory");
        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 2. Threshing Drum Tests
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void threshingDrumTurnsHempToFibre(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos drumPos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        syncWater(h, intakePos, drumPos);

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        check(h, drum != null, "drum block entity exists");
        check(h, drum.isWaterPowered(), "drum is water powered");

        // Feed hemp
        drum.acceptInput(new ItemStack(ModItems.HEMP.get(), 1));
        check(h, h.getBlockState(drumPos).getValue(WaterProcessingBlock.WORKING), "drum started working");

        // Recipe duration is 60 ticks. Check at tick 75.
        h.runAtTickTime(75, () -> {
            check(h, drum.outputBuffer().size() == 1, "drum output buffer has 1 entry");
            ItemStack out = drum.outputBuffer().get(0);
            check(h, out.is(ModItems.FIBRE.get()) && out.getCount() == 1, "drum produced 1 fibre");
            h.succeed();
        });
    }

    // --------------------------------------------------------------------------------------------
    // 3. Rope Machine Tests
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void ropeMachineTurnsTwoFibreToOneRope(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos machinePos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(machinePos, ModBlocks.ROPE_MACHINE.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        syncWater(h, intakePos, machinePos);

        RopeMachineBlockEntity machine = (RopeMachineBlockEntity) h.getBlockEntity(machinePos);
        check(h, machine != null, "rope machine block entity exists");
        check(h, machine.isWaterPowered(), "rope machine is water powered");

        // Feed first fibre: not enough for recipe yet (needs 2)
        machine.acceptInput(new ItemStack(ModItems.FIBRE.get(), 1));
        check(h, !h.getBlockState(machinePos).getValue(WaterProcessingBlock.WORKING), "not working yet with 1 fibre");
        check(h, machine.inputBuffer().size() == 1, "1 fibre in input buffer");

        // Feed second fibre: recipe starts!
        machine.acceptInput(new ItemStack(ModItems.FIBRE.get(), 1));
        check(h, h.getBlockState(machinePos).getValue(WaterProcessingBlock.WORKING), "started working with 2 fibre");

        // Recipe duration is 80 ticks. Check at tick 95.
        h.runAtTickTime(95, () -> {
            check(h, machine.outputBuffer().size() == 1, "rope machine output buffer has 1 entry");
            ItemStack out = machine.outputBuffer().get(0);
            check(h, out.is(ModItems.ROPE.get()) && out.getCount() == 1, "produced 1 rope");
            h.succeed();
        });
    }

    // --------------------------------------------------------------------------------------------
    // 4. Refuse Wrong Items & No Water
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void machinesRefuseWrongItems(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos drumPos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        syncWater(h, intakePos, drumPos);

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        check(h, drum != null && drum.isWaterPowered(), "drum is water powered");

        ItemStack dirt = new ItemStack(Blocks.DIRT);
        check(h, !drum.canAcceptInput(dirt), "drum rejects dirt");

        WaterProcessingBlock block = (WaterProcessingBlock) h.getBlockState(drumPos).getBlock();
        check(h, !block.canAcceptItem(h.getLevel(), h.absolutePos(drumPos), h.getBlockState(drumPos), Direction.SOUTH, dirt),
                "block rejects dirt from back");

        ServerPlayer p = player(h);
        ItemStack stick = new ItemStack(Items.STICK);
        InteractionResult res = click(h, p, drumPos, stick);
        check(h, !res.consumesAction(), "clicking with non-placeable stick passes");
        check(h, drum.inputBuffer().isEmpty(), "input buffer remained empty");

        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void machinesDoNothingWithoutWater(GameTestHelper h) {
        clear(h);
        BlockPos drumPos = new BlockPos(10, 1, 2);
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.NORTH));

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        check(h, drum != null, "drum exists");
        check(h, !drum.isWaterPowered(), "drum has no water power");

        ItemStack hemp = new ItemStack(ModItems.HEMP.get());
        check(h, !drum.canAcceptInput(hemp), "drum cannot accept input without water");

        ServerPlayer p = player(h);
        InteractionResult res = click(h, p, drumPos, hemp);
        check(h, res.consumesAction(), "interaction handled (message shown)");
        check(h, drum.inputBuffer().isEmpty(), "drum did not consume hemp without water");
        check(h, !h.getBlockState(drumPos).getValue(WaterProcessingBlock.WORKING), "drum is not working");

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 5. Full Buffer Stops Intake
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void fullBufferStopsIntake(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos drumPos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        syncWater(h, intakePos, drumPos);

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        check(h, drum != null && drum.isWaterPowered(), "drum is water powered");

        // Fill output buffer with 8 items
        drum.outputBuffer().add(new ItemStack(ModItems.FIBRE.get(), 8));
        check(h, drum.isBufferFull(), "drum buffer is full with 8 items");

        ItemStack hemp = new ItemStack(ModItems.HEMP.get());
        check(h, !drum.canAcceptInput(hemp), "full buffer blocks input");

        WaterProcessingBlock block = (WaterProcessingBlock) h.getBlockState(drumPos).getBlock();
        check(h, !block.canAcceptItem(h.getLevel(), h.absolutePos(drumPos), h.getBlockState(drumPos), Direction.SOUTH, hemp),
                "full buffer blocks conveyor input");

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 6. Breaking Machine Drops Buffer
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void breakingMachineDropsBuffer(GameTestHelper h) {
        clear(h);
        BlockPos drumPos = new BlockPos(10, 1, 2);
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState());

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        drum.inputBuffer().add(new ItemStack(ModItems.HEMP.get(), 3));
        drum.outputBuffer().add(new ItemStack(ModItems.FIBRE.get(), 2));

        h.destroyBlock(drumPos);

        AABB box = new AABB(h.absolutePos(drumPos)).inflate(2.0);
        List<ItemEntity> items = h.getLevel().getEntitiesOfClass(ItemEntity.class, box);
        int hempCount = 0;
        int fibreCount = 0;
        for (ItemEntity e : items) {
            if (e.getItem().is(ModItems.HEMP.get())) hempCount += e.getItem().getCount();
            if (e.getItem().is(ModItems.FIBRE.get())) fibreCount += e.getItem().getCount();
        }
        check(h, hempCount == 3, "dropped 3 hemp from input buffer");
        check(h, fibreCount == 2, "dropped 2 fibre from output buffer");
        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 7. Recipe Table Parsing & Resiliency
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void machineRecipeManagerParsesAndSkipsBroken(GameTestHelper h) {
        // Test loaded recipes
        check(h, MachineRecipeManager.find(ThreshingDrumBlock.ID, "", new ItemStack(ModItems.HEMP.get())).isPresent(),
                "hemp -> fibre recipe found");
        check(h, MachineRecipeManager.find(RopeMachineBlock.ID, "", new ItemStack(ModItems.FIBRE.get())).isPresent(),
                "fibre -> rope recipe found");
        check(h, MachineRecipeManager.find(SawmillBlock.ID, "beam", new ItemStack(Items.OAK_LOG)).isPresent(),
                "log -> beam recipe found");
        check(h, MachineRecipeManager.find(SawmillBlock.ID, "pit_prop", new ItemStack(Items.OAK_LOG)).isPresent(),
                "log -> pit prop recipe found");

        // Test broken JSON throws exception in parser (allowing caller/loader to skip safely)
        String badJson = "{\"machine\": \"holeydigsite:threshing_drum\", \"ingredients\": []}"; // missing result
        JsonObject obj = JsonParser.parseString(badJson).getAsJsonObject();
        boolean threw = false;
        try {
            MachineRecipe.fromJson(HoleyDigsite.id("bad_test"), obj);
        } catch (IllegalArgumentException | com.google.gson.JsonSyntaxException e) {
            threw = true;
        } catch (Exception e) {
            threw = true;
        }
        check(h, threw, "malformed recipe throws on parsing to be skipped by loader");

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 8. Sawmill Two Modes
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void sawmillBeamAndPitPropModes(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos sawmillPos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(sawmillPos, ModBlocks.SAWMILL.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH)
                .setValue(SawmillBlock.MODE, SawmillBlock.Mode.BEAM));
        syncWater(h, intakePos, sawmillPos);

        SawmillBlockEntity sawmill = (SawmillBlockEntity) h.getBlockEntity(sawmillPos);
        check(h, sawmill != null && sawmill.isWaterPowered(), "sawmill is water powered");
        check(h, "beam".equals(sawmill.mode()), "sawmill starts in beam mode");

        // Feed 1 log in beam mode
        sawmill.acceptInput(new ItemStack(Items.OAK_LOG, 1));
        check(h, h.getBlockState(sawmillPos).getValue(WaterProcessingBlock.WORKING), "sawmill working on beam");

        h.runAtTickTime(75, () -> {
            check(h, sawmill.outputBuffer().size() == 1, "sawmill output buffer has 1 entry");
            ItemStack out = sawmill.outputBuffer().get(0);
            check(h, out.is(ModItems.BEAM.get()) && out.getCount() == 1, "sawmill produced 1 beam");
            sawmill.outputBuffer().clear();

            // Right click with empty hand and sneak to toggle mode
            ServerPlayer p = player(h);
            p.setShiftKeyDown(true);
            BlockPos abs = h.absolutePos(sawmillPos);
            InteractionResult toggleRes = p.gameMode.useItemOn(p, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
            check(h, toggleRes.consumesAction(), "empty hand click succeeded");
            check(h, h.getBlockState(sawmillPos).getValue(SawmillBlock.MODE) == SawmillBlock.Mode.PIT_PROP, "sawmill toggled to pit prop mode");
            check(h, "pit_prop".equals(sawmill.mode()), "block entity reports pit_prop mode");

            // Feed 1 log in pit prop mode
            sawmill.acceptInput(new ItemStack(Items.BIRCH_LOG, 1));
            check(h, h.getBlockState(sawmillPos).getValue(WaterProcessingBlock.WORKING), "sawmill working on pit props");

            h.runAtTickTime(155, () -> {
                check(h, sawmill.outputBuffer().size() == 1, "sawmill output buffer has 1 entry");
                ItemStack pitProps = sawmill.outputBuffer().get(0);
                check(h, pitProps.is(ModItems.PIT_PROP.get()) && pitProps.getCount() == 2, "sawmill produced 2 pit props");
                h.succeed();
            });
        });
    }

    // --------------------------------------------------------------------------------------------
    // 9. Plot Locks Block Stranger
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void plotLocksBlockStranger(GameTestHelper h) {
        clear(h);
        String plotId = "phase36_test_plot";
        BlockPos boardPos = new BlockPos(12, 1, 2);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos sawmillPos = new BlockPos(10, 1, 2);

        plot(h, plotId, boardPos, 8, 14);

        TeamAccess before = TeamAccess.get();
        UUID teamId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ServerPlayer stranger = player(h);

        try {
            TeamAccess.set(new TeamAccess() {
                @Override
                public UUID islandTeamAt(ServerLevel level, BlockPos pos) {
                    return teamId;
                }

                @Override
                public Set<UUID> members(ServerLevel level, UUID team) {
                    return Set.of(ownerId);
                }

                @Override
                public boolean sameTeam(ServerLevel level, UUID a, UUID b) {
                    return a.equals(b);
                }
            });

            PlotData.get(h.getLevel()).setOwned(plotId, h.absolutePos(boardPos), true, "Owner", ownerId);

            h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
            h.setBlock(sawmillPos, ModBlocks.SAWMILL.get().defaultBlockState()
                    .setValue(WaterProcessingBlock.FACING, Direction.NORTH)
                    .setValue(SawmillBlock.MODE, SawmillBlock.Mode.BEAM));
            syncWater(h, intakePos, sawmillPos);

            // Stranger attempts to interact
            check(h, !PlotLock.canUse(stranger, h.getLevel(), h.absolutePos(sawmillPos)), "stranger cannot use sawmill");

            BlockPos abs = h.absolutePos(sawmillPos);
            stranger.setShiftKeyDown(true);
            InteractionResult toggleRes = stranger.gameMode.useItemOn(stranger, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
            check(h, toggleRes == InteractionResult.FAIL, "stranger mode toggle refused by plot lock");
            check(h, h.getBlockState(sawmillPos).getValue(SawmillBlock.MODE) == SawmillBlock.Mode.BEAM, "mode did not change");

            // Stranger attempts to insert log
            ItemStack log = new ItemStack(Items.OAK_LOG);
            InteractionResult insertRes = click(h, stranger, sawmillPos, log);
            check(h, insertRes == InteractionResult.FAIL, "stranger input insertion refused by plot lock");
        } finally {
            TeamAccess.set(before);
            forgetPlot(h, plotId, boardPos);
        }

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 12. Input Buffer Cap & NBT Save/Load Integrity (Phase 36b Section 1)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void inputBufferIsCappedAndSavedWithoutLoss(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos drumPos = new BlockPos(10, 1, 2);
        BlockPos beltPos = new BlockPos(10, 1, 3);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        h.setBlock(beltPos, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(com.holeydigsite.belt.ClumpBeltBlock.FACING, Direction.NORTH));
        syncWater(h, intakePos, drumPos);

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        check(h, drum != null && drum.isWaterPowered(), "drum is water powered");

        ItemStack stack = new ItemStack(ModItems.HEMP.get(), 20);
        drum.acceptInput(stack);

        check(h, drum.totalInputItems() <= WaterProcessingBlockEntity.MAX_INPUT_ITEMS,
                "input items do not exceed cap of 8: " + drum.totalInputItems());
        check(h, drum.isInputFull(), "drum input buffer is full");
        check(h, !drum.canAcceptInput(new ItemStack(ModItems.HEMP.get(), 1)), "full input buffer blocks further input");

        CompoundTag tag = drum.saveWithFullMetadata(h.getLevel().registryAccess());
        ThreshingDrumBlockEntity fresh = new ThreshingDrumBlockEntity(h.absolutePos(drumPos), h.getBlockState(drumPos));
        fresh.loadWithComponents(tag, h.getLevel().registryAccess());

        check(h, fresh.totalInputItems() == drum.totalInputItems(),
                "all input items preserved on load: " + fresh.totalInputItems() + " vs " + drum.totalInputItems());
        for (ItemStack s : fresh.inputBuffer()) {
            check(h, s.getCount() <= s.getMaxStackSize(), "stack count within valid bounds: " + s.getCount());
        }

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 13. Sawmill Output Hand Take & Sneak Mode Toggle (Phase 36b Section 2)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void sawmillOutputCanBeTakenByHandAndSneakSwitchesMode(GameTestHelper h) {
        clear(h);
        BlockPos sawmillPos = new BlockPos(10, 1, 2);
        h.setBlock(sawmillPos, ModBlocks.SAWMILL.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH)
                .setValue(SawmillBlock.MODE, SawmillBlock.Mode.BEAM));

        SawmillBlockEntity sawmill = (SawmillBlockEntity) h.getBlockEntity(sawmillPos);
        check(h, "beam".equals(sawmill.mode()), "starts in beam mode");

        sawmill.outputBuffer().add(new ItemStack(ModItems.BEAM.get(), 1));

        ServerPlayer p = player(h);
        BlockPos abs = h.absolutePos(sawmillPos);

        // 1. Regular empty hand right click takes buffer, does not toggle mode
        p.setShiftKeyDown(false);
        InteractionResult takeRes = click(h, p, sawmillPos, ItemStack.EMPTY);
        check(h, takeRes.consumesAction(), "empty hand takes buffer");
        check(h, sawmill.outputBuffer().isEmpty(), "output buffer is now empty");
        check(h, p.getInventory().contains(new ItemStack(ModItems.BEAM.get())), "player received the beam");
        check(h, h.getBlockState(sawmillPos).getValue(SawmillBlock.MODE) == SawmillBlock.Mode.BEAM, "mode is still beam");

        p.getInventory().clearContent();

        // 2. Regular empty hand right click on empty buffer does not toggle mode
        InteractionResult noopRes = click(h, p, sawmillPos, ItemStack.EMPTY);
        check(h, noopRes == InteractionResult.PASS, "empty buffer empty hand passes without mode toggle");
        check(h, h.getBlockState(sawmillPos).getValue(SawmillBlock.MODE) == SawmillBlock.Mode.BEAM, "mode is still beam");

        // 3. Sneak empty hand right click toggles mode
        p.setShiftKeyDown(true);
        InteractionResult sneakToggleRes = click(h, p, sawmillPos, ItemStack.EMPTY);
        check(h, sneakToggleRes.consumesAction(), "sneak empty hand toggles mode");
        check(h, h.getBlockState(sawmillPos).getValue(SawmillBlock.MODE) == SawmillBlock.Mode.PIT_PROP, "mode toggled to pit prop");

        // 4. Sneak empty hand toggles mode even when output buffer has items
        sawmill.outputBuffer().add(new ItemStack(ModItems.PIT_PROP.get(), 2));
        InteractionResult sneakToggle2 = click(h, p, sawmillPos, ItemStack.EMPTY);
        check(h, sneakToggle2.consumesAction(), "sneak empty hand toggles mode with full buffer");
        check(h, h.getBlockState(sawmillPos).getValue(SawmillBlock.MODE) == SawmillBlock.Mode.BEAM, "mode toggled back to beam");
        check(h, sawmill.outputBuffer().size() == 1, "output buffer still holds pit props");

        // 5. Empty hand now takes the pit props
        p.setShiftKeyDown(false);
        InteractionResult takePitProps = click(h, p, sawmillPos, ItemStack.EMPTY);
        check(h, takePitProps.consumesAction(), "empty hand takes pit props");
        check(h, sawmill.outputBuffer().isEmpty(), "output buffer emptied");
        check(h, p.getInventory().contains(new ItemStack(ModItems.PIT_PROP.get())), "player received pit props");

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 14. Client Buffer Sync (Phase 36b Section 3)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void clientSeesBufferFullAndCount(GameTestHelper h) {
        clear(h);
        BlockPos drumPos = new BlockPos(10, 1, 2);
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState());

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        drum.outputBuffer().add(new ItemStack(ModItems.FIBRE.get(), 8));

        CompoundTag updateTag = drum.getUpdateTag(h.getLevel().registryAccess());
        check(h, updateTag.getInt("BufferedCount") == 8, "update tag contains buffered count 8");

        ThreshingDrumBlockEntity clientBe = new ThreshingDrumBlockEntity(h.absolutePos(drumPos), h.getBlockState(drumPos));
        clientBe.loadWithComponents(updateTag, h.getLevel().registryAccess());
        check(h, clientBe.totalBufferedItems() == 8, "client BE sees buffered count 8: " + clientBe.totalBufferedItems());
        check(h, clientBe.isBufferFull(), "client BE sees buffer is full");

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 15. Zero Ticking While Stuck & Wakeup on Front Belt (Phase 36b Section 4)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long", timeoutTicks = 60)
    public static void noTickingWhileStuckAndStartsOnBeltNeighbor(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos drumPos = new BlockPos(10, 1, 2);
        BlockPos frontPos = new BlockPos(10, 1, 1);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        syncWater(h, intakePos, drumPos);

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        drum.outputBuffer().add(new ItemStack(ModItems.FIBRE.get(), 1));

        drum.serverTick();
        check(h, !h.getBlockState(drumPos).getValue(WaterProcessingBlock.WORKING),
                "machine stops ticking when output buffer cannot go anywhere");

        h.setBlock(frontPos, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(com.holeydigsite.belt.ClumpBeltBlock.FACING, Direction.NORTH));
        TestWater.underBelt(h, frontPos);

        check(h, h.getBlockState(drumPos).getValue(WaterProcessingBlock.WORKING),
                "machine wakes up and starts ticking when belt is placed in front");

        h.runAtTickTime(15, () -> {
            check(h, drum.outputBuffer().isEmpty(), "buffer emptied onto front belt");
            h.succeed();
        });
    }

    // --------------------------------------------------------------------------------------------
    // 16. Hand Feeding Bulk and Sneak Single (Phase 36b Section 5)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void handFeedingBulkAndSneakSingle(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos drumPos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        syncWater(h, intakePos, drumPos);

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        ServerPlayer p = player(h);
        BlockPos abs = h.absolutePos(drumPos);

        // 1. Regular right-click inserts up to cap (8)
        p.setShiftKeyDown(false);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HEMP.get(), 16));
        InteractionResult res1 = p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
        check(h, res1.consumesAction(), "bulk right-click accepted");
        check(h, p.getMainHandItem().getCount() == 8, "player retained remaining 8 hemp: " + p.getMainHandItem().getCount());

        // Clear machine for single sneak feed test
        drum.inputBuffer().clear();
        drum.dropContents(h.getLevel(), h.absolutePos(drumPos));

        // 2. Sneak right-click inserts exactly 1
        p.setShiftKeyDown(true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HEMP.get(), 16));
        InteractionResult res2 = p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
        check(h, res2.consumesAction(), "sneak right-click accepted");
        check(h, p.getMainHandItem().getCount() == 15, "player has 15 hemp left after inserting 1: " + p.getMainHandItem().getCount());

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 17. Sawmill Pit Prop Pushes Both Props and Empties Buffer (Phase 36c Section 1a)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long", timeoutTicks = 160)
    public static void sawmillPitPropPushesBothPropsAndEmptiesBuffer(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos sawmillPos = new BlockPos(10, 1, 2);
        BlockPos belt1Pos = new BlockPos(10, 1, 1);
        BlockPos belt2Pos = new BlockPos(10, 1, 0);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(sawmillPos, ModBlocks.SAWMILL.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH)
                .setValue(SawmillBlock.MODE, SawmillBlock.Mode.PIT_PROP));
        h.setBlock(belt1Pos, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(com.holeydigsite.belt.ClumpBeltBlock.FACING, Direction.NORTH));
        h.setBlock(belt2Pos, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(com.holeydigsite.belt.ClumpBeltBlock.FACING, Direction.NORTH));
        TestWater.underBelt(h, belt1Pos);
        TestWater.underBelt(h, belt2Pos);
        syncWater(h, intakePos, sawmillPos);

        SawmillBlockEntity sawmill = (SawmillBlockEntity) h.getBlockEntity(sawmillPos);
        check(h, sawmill != null && sawmill.isWaterPowered(), "sawmill is water powered");

        sawmill.acceptInput(new ItemStack(Items.OAK_LOG, 1));
        check(h, sawmill.totalInputItems() == 1, "sawmill accepted 1 log");

        h.runAtTickTime(130, () -> {
            check(h, sawmill.outputBuffer().isEmpty(), "sawmill output buffer is empty: " + sawmill.totalBufferedItems());
            check(h, sawmill.totalInputItems() == 0, "sawmill input buffer is empty");
            int propsFound = 0;
            for (BlockPos bPos : List.of(belt1Pos, belt2Pos)) {
                if (h.getBlockEntity(bPos) instanceof com.holeydigsite.belt.ClumpBeltBlockEntity belt) {
                    for (ItemStack item : belt.items()) {
                        if (item.is(ModItems.PIT_PROP.get())) {
                            propsFound += item.getCount();
                        }
                    }
                }
            }
            check(h, propsFound == 2, "both pit props are on the belt line: " + propsFound);
            h.succeed();
        });
    }

    // --------------------------------------------------------------------------------------------
    // 18. Machine With Blocked Front Belt Wakes Up on Recheck Tick (Phase 36c Section 1b)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long", timeoutTicks = 80)
    public static void machineWithBlockedFrontBeltWakesUpOnRecheckTick(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos drumPos = new BlockPos(10, 1, 2);
        BlockPos frontPos = new BlockPos(10, 1, 1);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        h.setBlock(frontPos, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(com.holeydigsite.belt.ClumpBeltBlock.FACING, Direction.NORTH));
        TestWater.underBelt(h, frontPos);
        syncWater(h, intakePos, drumPos);

        ThreshingDrumBlockEntity drum = (ThreshingDrumBlockEntity) h.getBlockEntity(drumPos);
        check(h, drum != null && drum.isWaterPowered(), "drum is water powered");

        com.holeydigsite.belt.ClumpBeltBlockEntity belt = (com.holeydigsite.belt.ClumpBeltBlockEntity) h.getBlockEntity(frontPos);
        check(h, belt != null, "belt exists");

        belt.accept(Direction.SOUTH, 0, new ItemStack(Items.DIRT, 1));
        drum.outputBuffer().add(new ItemStack(ModItems.FIBRE.get(), 1));

        drum.serverTick();
        check(h, !h.getBlockState(drumPos).getValue(WaterProcessingBlock.WORKING), "drum stops when front cannot take item");
        check(h, h.getLevel().getBlockTicks().hasScheduledTick(h.absolutePos(drumPos), ModBlocks.THRESHING_DRUM.get()),
                "recheck tick is scheduled for stopped drum with buffered item");

        h.runAtTickTime(5, () -> {
            belt.clumps().clear();
            belt.setChanged();
            check(h, !h.getBlockState(drumPos).getValue(WaterProcessingBlock.WORKING), "drum still stopped after belt entry cleared");

            h.runAtTickTime(50, () -> {
                check(h, drum.outputBuffer().isEmpty(), "output buffer emptied after recheck wakeup: " + drum.totalBufferedItems());
                boolean hasFibre = false;
                for (ItemStack s : belt.items()) {
                    if (s.is(ModItems.FIBRE.get())) hasFibre = true;
                }
                check(h, hasFibre, "fibre was pushed to front belt");
                h.succeed();
            });
        });
    }

    // --------------------------------------------------------------------------------------------
    // 19. Machine With Empty Buffer Schedules No Ticks (Phase 36c Section 1c)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void machineWithEmptyBufferSchedulesNoTicks(GameTestHelper h) {
        clear(h);
        BlockPos intakePos = new BlockPos(9, 1, 2);
        BlockPos sawmillPos = new BlockPos(10, 1, 2);

        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(sawmillPos, ModBlocks.SAWMILL.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH));
        syncWater(h, intakePos, sawmillPos);

        SawmillBlockEntity sawmill = (SawmillBlockEntity) h.getBlockEntity(sawmillPos);
        check(h, sawmill != null && sawmill.isWaterPowered(), "sawmill is water powered");
        check(h, sawmill.outputBuffer().isEmpty(), "output buffer is empty");
        check(h, sawmill.inputBuffer().isEmpty(), "input buffer is empty");

        sawmill.serverTick();

        check(h, !h.getBlockState(sawmillPos).getValue(WaterProcessingBlock.WORKING), "idle machine is not working");
        check(h, !h.getLevel().getBlockTicks().hasScheduledTick(h.absolutePos(sawmillPos), ModBlocks.SAWMILL.get()),
                "idle machine with empty buffer schedules no ticks");

        h.succeed();
    }

    // --------------------------------------------------------------------------------------------
    // 20. Sneak With Item Does Not Switch Mode and Item Does Not Take Buffer (Phase 36c Section 2)
    // --------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void sneakWithItemDoesNotSwitchSawmillModeAndItemDoesNotTakeBuffer(GameTestHelper h) {
        clear(h);
        BlockPos sawmillPos = new BlockPos(10, 1, 2);
        h.setBlock(sawmillPos, ModBlocks.SAWMILL.get().defaultBlockState()
                .setValue(WaterProcessingBlock.FACING, Direction.NORTH)
                .setValue(SawmillBlock.MODE, SawmillBlock.Mode.BEAM));

        SawmillBlockEntity sawmill = (SawmillBlockEntity) h.getBlockEntity(sawmillPos);
        sawmill.outputBuffer().add(new ItemStack(ModItems.BEAM.get(), 1));

        ServerPlayer p = player(h);

        // 1. Right-click with a non-ingredient item (stick) does NOT take buffer
        p.setShiftKeyDown(false);
        click(h, p, sawmillPos, new ItemStack(Items.STICK));
        check(h, !p.getInventory().contains(new ItemStack(ModItems.BEAM.get())), "player did not take beam when holding stick");
        check(h, sawmill.totalBufferedItems() == 1, "output buffer still holds the beam");

        // 2. Sneak right-click with an item does NOT switch mode
        p.setShiftKeyDown(true);
        click(h, p, sawmillPos, new ItemStack(Items.STICK));
        check(h, h.getBlockState(sawmillPos).getValue(SawmillBlock.MODE) == SawmillBlock.Mode.BEAM,
                "sneak with stick did not switch sawmill mode");

        // 3. Normal empty hand right-click DOES take buffer
        p.setShiftKeyDown(false);
        InteractionResult res3 = click(h, p, sawmillPos, ItemStack.EMPTY);
        check(h, res3.consumesAction(), "empty hand right click succeeds");
        check(h, sawmill.outputBuffer().isEmpty(), "empty hand took the buffer");
        check(h, p.getInventory().contains(new ItemStack(ModItems.BEAM.get())), "player received the beam");

        // 4. Sneak empty hand right-click DOES switch mode
        p.setShiftKeyDown(true);
        InteractionResult res4 = click(h, p, sawmillPos, ItemStack.EMPTY);
        check(h, res4.consumesAction(), "empty hand sneak click succeeds");
        check(h, h.getBlockState(sawmillPos).getValue(SawmillBlock.MODE) == SawmillBlock.Mode.PIT_PROP,
                "sneak with empty hand switched mode to pit prop");

        h.succeed();
    }
}
