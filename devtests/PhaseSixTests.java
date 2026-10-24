package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.BeltMode;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.drill.DrillStatus;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.sluice.SluiceStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.Set;

/**
 * Sandbox-only GameTests for Phase 6 (not shipped): the Tier 1 Sluice Box.
 * Standard layout (template "empty", 5x4x5): sluice at (2,1,2) facing EAST (tray east, input west), water intake at
 * its north side (2,1,1) in a little stone basin.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseSixTests {
    private static final int CYCLE = SluiceBoxBlockEntity.CYCLE_TICKS;
    private static final BlockPos SLUICE = new BlockPos(2, 1, 2);
    private static Set<Item> panningLoot() {
        return PhaseTwoTests.lootItems();
    }

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h, int sizeX) {
        for (int x = 0; x < sizeX; x++) for (int y = 1; y <= 3; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    private static void place(GameTestHelper h, BlockPos rel, BlockState state) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, Block.updateFromNeighbourShapes(state, h.getLevel(), abs), 3);
        TestWater.underBelt(h, rel); // Phase 12: belts need water
    }

    /** Water intake in a stone basin at {@code at} (open only towards {@code open}). */
    private static void intake(GameTestHelper h, BlockPos at, boolean water, Direction open) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d != open && h.getBlockState(at.relative(d)).isAir()) h.setBlock(at.relative(d), Blocks.STONE);
        }
        h.setBlock(at, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, water));
    }

    /** Sluice at (2,1,2) facing east, intake on its north side. */
    private static void sluiceSetup(GameTestHelper h, boolean water) {
        clear(h, 5);
        h.setBlock(SLUICE, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        intake(h, new BlockPos(2, 1, 1), water, Direction.SOUTH);
    }

    private static SluiceBoxBlockEntity be(GameTestHelper h, BlockPos rel) {
        return (SluiceBoxBlockEntity) h.getBlockEntity(rel);
    }

    private static SluiceStatus status(GameTestHelper h, BlockPos rel) {
        return h.getBlockState(rel).getValue(SluiceBoxBlock.STATUS);
    }

    private static String messageKey(GameTestHelper h, BlockPos rel) {
        Component c = SluiceBoxBlock.statusMessage(h.getBlockState(rel), be(h, rel));
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static int items(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static int allItems(GameTestHelper h) {
        return h.getEntities(EntityType.ITEM).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static int trayCount(SluiceBoxBlockEntity s) {
        return s.tray().stream().mapToInt(ItemStack::getCount).sum();
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    // ------------------------------------------------------------------------------------------------

    /** Powered sluice: a clump put in by hand comes out as panning loot in the tray after 100 ticks (not before). */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void poweredSluiceWashesInsertedClump(GameTestHelper h) {
        sluiceSetup(h, true);
        ServerPlayer p = player(h);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(h, status(h, SLUICE) == SluiceStatus.IDLE, "powered, empty sluice is IDLE, is " + status(h, SLUICE));
                    check(h, messageKey(h, SLUICE).equals("message.holeydigsite.sluice.idle"), "idle message " + messageKey(h, SLUICE));
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIRT_CLUMP.get(), 3));
                    h.useBlock(SLUICE, p);
                    check(h, be(h, SLUICE).queued() == 1 && p.getMainHandItem().getCount() == 2, "right-click puts 1 clump in");
                    check(h, status(h, SLUICE) == SluiceStatus.WORKING && h.getBlockState(SLUICE).getValue(SluiceBoxBlock.DIRT),
                            "working with the dirt layer showing: " + h.getBlockState(SLUICE));
                    check(h, messageKey(h, SLUICE).equals("message.holeydigsite.sluice.working"), "working message " + messageKey(h, SLUICE));
                })
                .thenExecuteAfter(CYCLE - 5, () -> check(h, be(h, SLUICE).tray().isEmpty() && be(h, SLUICE).queued() == 1, "nothing before 100 ticks"))
                .thenExecuteAfter(8, () -> {
                    SluiceBoxBlockEntity s = be(h, SLUICE);
                    check(h, !s.tray().isEmpty() && s.queued() == 0, "clump washed into the tray after 100 ticks: " + s.tray());
                    check(h, s.tray().stream().allMatch(st -> panningLoot().contains(st.getItem())), "same loot as panning: " + s.tray());
                    check(h, status(h, SLUICE) == SluiceStatus.IDLE && !h.getBlockState(SLUICE).getValue(SluiceBoxBlock.DIRT),
                            "back to IDLE, no dirt layer: " + h.getBlockState(SLUICE));
                    check(h, allItems(h) == 0, "no item entities");
                })
                .thenSucceed();
    }

    /** No water: queued clumps just wait (NO_POWER). Water on -> it starts. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void unpoweredSluiceDoesNothing(GameTestHelper h) {
        sluiceSetup(h, false);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(h, be(h, SLUICE).addClumps(2) == 2, "2 clumps queued");
                    ((SluiceBoxBlock) ModBlocks.SLUICE_BOX.get()).refreshStatus(h.getLevel(), h.absolutePos(SLUICE), h.getLevel().getBlockState(h.absolutePos(SLUICE)));
                    check(h, status(h, SLUICE) == SluiceStatus.NO_POWER, "dry intake: NO_POWER, is " + status(h, SLUICE));
                    check(h, messageKey(h, SLUICE).equals("message.holeydigsite.sluice.no_power"), "message " + messageKey(h, SLUICE));
                })
                .thenExecuteAfter(150, () -> {
                    check(h, be(h, SLUICE).queued() == 2 && be(h, SLUICE).tray().isEmpty() && be(h, SLUICE).progress() == 0, "nothing happened");
                    h.setBlock(new BlockPos(2, 1, 1), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
                })
                .thenExecuteAfter(2, () -> check(h, status(h, SLUICE) == SluiceStatus.WORKING, "water on: WORKING, is " + status(h, SLUICE)))
                .thenExecuteAfter(CYCLE + 2, () -> check(h, be(h, SLUICE).queued() == 1 && !be(h, SLUICE).tray().isEmpty(), "first clump washed"))
                .thenSucceed();
    }

    /** Drill -> belt -> sluice back; the drill's clump ends up as loot in the tray. */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void drillBeltSluiceEndToEnd(GameTestHelper h) {
        clear(h, 8);
        intake(h, new BlockPos(1, 1, 2), true, Direction.EAST);
        h.setBlock(new BlockPos(2, 0, 2), ModBlocks.DIRTROCK.get());
        BlockPos drill = new BlockPos(2, 1, 2);
        h.setBlock(drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        BlockPos belt = new BlockPos(3, 1, 2), sluice = new BlockPos(4, 1, 2);
        place(h, belt, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        h.setBlock(sluice, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        intake(h, new BlockPos(4, 1, 1), true, Direction.SOUTH);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(h, status(h, sluice) == SluiceStatus.IDLE, "sluice powered from its side, is " + status(h, sluice));
                    CompoundTag tag = new CompoundTag();
                    tag.putBoolean("WaterPowered", true);
                    tag.putInt("Progress", MiningDrillBlock.CYCLE_TICKS - 3);
                    h.getBlockEntity(drill).loadCustomOnly(tag, h.getLevel().registryAccess());
                })
                .thenWaitUntil(() -> check(h, be(h, sluice).queued() == 1, "drill clump arrives via the belt"))
                .thenExecute(() -> check(h, status(h, sluice) == SluiceStatus.WORKING, "working"))
                .thenWaitUntil(() -> check(h, !be(h, sluice).tray().isEmpty(), "washed into the tray"))
                .thenExecute(() -> {
                    check(h, be(h, sluice).queued() == 0 && allItems(h) == 0, "queue empty, no item entities");
                    check(h, h.getBlockState(drill).getValue(MiningDrillBlock.STATUS) == DrillStatus.WORKING, "drill keeps working");
                })
                .thenSucceed();
    }

    /** A full tray stops the sluice (clumps wait); collecting it with an empty hand restarts it. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullTrayStopsAndCollectingRestarts(GameTestHelper h) {
        sluiceSetup(h, true);
        ServerPlayer p = player(h);
        Item[] fillers = {Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.GLASS, Items.CLAY_BALL, Items.FLINT};
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    for (Item it : fillers) be(h, SLUICE).addToTray(new ItemStack(it));
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIRT_CLUMP.get(), 2));
                    h.useBlock(SLUICE, p);
                    h.useBlock(SLUICE, p);
                    check(h, be(h, SLUICE).queued() == 2, "2 queued");
                    check(h, status(h, SLUICE) == SluiceStatus.STOPPED, "tray full: STOPPED, is " + status(h, SLUICE));
                    check(h, messageKey(h, SLUICE).equals("message.holeydigsite.sluice.stopped"), "message " + messageKey(h, SLUICE));
                })
                .thenExecuteAfter(CYCLE + 20, () -> {
                    check(h, be(h, SLUICE).queued() == 2 && be(h, SLUICE).tray().size() == 9, "stopped: nothing washed");
                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    p.getInventory().clearContent();
                    h.useBlock(SLUICE, p);
                    int got = 0;
                    for (ItemStack s : p.getInventory().items) got += s.getCount();
                    check(h, got == 9 && be(h, SLUICE).tray().isEmpty(), "empty hand collects all 9, got " + got);
                    check(h, status(h, SLUICE) == SluiceStatus.WORKING, "collecting restarts it, is " + status(h, SLUICE));
                })
                .thenExecuteAfter(CYCLE + 3, () -> check(h, be(h, SLUICE).queued() == 1 && !be(h, SLUICE).tray().isEmpty(), "washing again"))
                .thenSucceed();
    }

    /**
     * Belts pointing into the sluice's front or side feed nothing (even with room in the queue). A belt into the
     * back fills the queue to 4 and then backs up - no clump is voided.
     */
    @GameTest(template = "empty", timeoutTicks = 250)
    public static void fullQueueBacksUpBelt(GameTestHelper h) {
        clear(h, 5);
        // unpowered sluice (no intake) so the queue just fills up
        h.setBlock(SLUICE, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        BlockPos back = new BlockPos(1, 1, 2), front = new BlockPos(3, 1, 2), side = new BlockPos(2, 1, 1);
        place(h, back, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        place(h, front, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.WEST));
        place(h, side, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.SOUTH));
        ((ClumpBeltBlockEntity) h.getBlockEntity(front)).insertByPlayer(1);
        ((ClumpBeltBlockEntity) h.getBlockEntity(side)).insertByPlayer(1);
        h.startSequence()
                .thenExecuteAfter(40, () -> {
                    check(h, be(h, SLUICE).queued() == 0, "front/side belts put nothing in, queued " + be(h, SLUICE).queued());
                    check(h, ((ClumpBeltBlockEntity) h.getBlockEntity(front)).clumpCount() == 1, "belt into the front keeps its clump");
                    check(h, ((ClumpBeltBlockEntity) h.getBlockEntity(side)).clumpCount() == 1, "belt into the side keeps its clump");
                    be(h, SLUICE).addClumps(2);
                    ((ClumpBeltBlockEntity) h.getBlockEntity(back)).insertByPlayer(4);
                })
                .thenExecuteAfter(80, () -> {
                    ClumpBeltBlockEntity b = (ClumpBeltBlockEntity) h.getBlockEntity(back);
                    check(h, be(h, SLUICE).queued() == SluiceBoxBlockEntity.MAX_QUEUE, "queue filled to 4, " + be(h, SLUICE).queued());
                    check(h, Arrays.equals(b.positions(), new int[]{19, 14}), "belt backed up with the 2 that didn't fit: " + Arrays.toString(b.positions()));
                    check(h, h.getBlockState(back).getValue(ClumpBeltBlock.MODE) == BeltMode.BLOCKED, "belt BLOCKED");
                    check(h, allItems(h) == 0, "nothing voided or dropped");
                })
                .thenSucceed();
    }

    /** Hoppers can neither insert clumps nor take tray items; no item handler on any side. */
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void hopperCannotInsertOrExtract(GameTestHelper h) {
        clear(h, 5);
        BlockPos sluice = new BlockPos(2, 2, 2), above = new BlockPos(2, 3, 2), below = new BlockPos(2, 1, 2);
        h.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
        h.setBlock(sluice, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        h.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) h.getBlockEntity(above)).setItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 5));
        be(h, sluice).addToTray(new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 3));
        for (Direction d : Direction.values()) {
            check(h, h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(sluice), d) == null, "no item handler on " + d);
        }
        check(h, h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(sluice), null) == null, "no item handler (null side)");
        h.runAfterDelay(100, () -> {
            check(h, ((HopperBlockEntity) h.getBlockEntity(above)).getItem(0).getCount() == 5, "hopper above kept its clumps");
            check(h, be(h, sluice).queued() == 0, "nothing inserted");
            check(h, ((HopperBlockEntity) h.getBlockEntity(below)).isEmpty() && trayCount(be(h, sluice)) == 3, "hopper below took nothing");
            h.succeed();
        });
    }

    /** Breaking drops the sluice, its queued clumps and the tray. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void breakingDropsClumpsAndTray(GameTestHelper h) {
        clear(h, 5);
        h.setBlock(SLUICE, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        be(h, SLUICE).addClumps(3);
        be(h, SLUICE).addToTray(new ItemStack(Items.RAW_COPPER, 5));
        be(h, SLUICE).addToTray(new ItemStack(Items.DIAMOND, 1));
        h.runAfterDelay(2, () -> {
            h.getLevel().destroyBlock(h.absolutePos(SLUICE), true);
            h.runAfterDelay(2, () -> {
                check(h, items(h, ModItems.SLUICE_BOX.get()) == 1, "sluice dropped itself");
                check(h, items(h, ModItems.DIRT_CLUMP.get()) == 3, "3 queued clumps dropped, " + items(h, ModItems.DIRT_CLUMP.get()));
                check(h, items(h, Items.RAW_COPPER) == 5 && items(h, Items.DIAMOND) == 1, "tray dropped");
                h.succeed();
            });
        });
    }

    /** Water ports only on the two sides: an intake or pipe at the back or front doesn't connect. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void waterPortsOnlyOnSides(GameTestHelper h) {
        clear(h, 5);
        h.setBlock(SLUICE, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        BlockState s = h.getBlockState(SLUICE);
        SluiceBoxBlock block = ModBlocks.SLUICE_BOX.get();
        check(h, block.hasWaterConnection(s, Direction.NORTH) && block.hasWaterConnection(s, Direction.SOUTH)
                && !block.hasWaterConnection(s, Direction.EAST) && !block.hasWaterConnection(s, Direction.WEST)
                && !block.hasWaterConnection(s, Direction.UP) && !block.hasWaterConnection(s, Direction.DOWN), "ports north+south only");
        intake(h, new BlockPos(1, 1, 2), true, Direction.EAST);   // at the back
        place(h, new BlockPos(3, 1, 2), ModBlocks.WATER_PIPE.get().defaultBlockState()); // at the front
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(h, status(h, SLUICE) == SluiceStatus.NO_POWER, "intake at the back doesn't power it, is " + status(h, SLUICE));
                    check(h, !h.getBlockState(new BlockPos(3, 1, 2)).getValue(PipeBlock.WEST), "pipe at the front doesn't connect");
                    place(h, new BlockPos(2, 1, 3), ModBlocks.WATER_PIPE.get().defaultBlockState()); // side
                    check(h, h.getBlockState(new BlockPos(2, 1, 3)).getValue(PipeBlock.NORTH), "pipe at the side connects");
                    intake(h, new BlockPos(2, 1, 1), true, Direction.SOUTH); // other side, directly
                })
                .thenExecuteAfter(2, () -> check(h, status(h, SLUICE) == SluiceStatus.IDLE, "intake at a side powers it, is " + status(h, SLUICE)))
                .thenSucceed();
    }

    /** Placed with the tray facing the player; sneak-right-click fills the queue to 4 through the real click path. */
    @GameTest(template = "empty")
    public static void placementAndSneakInsert(GameTestHelper h) {
        clear(h, 5);
        ServerPlayer p = player(h);
        h.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        BlockPos floor = h.absolutePos(new BlockPos(2, 0, 2));
        Vec3 c = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 1, 2)));
        p.moveTo(c.x, c.y, c.z, -90.0F, 30.0F); // looking east
        ItemStack item = new ItemStack(ModItems.SLUICE_BOX.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, item);
        item.useOn(new net.minecraft.world.item.context.UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        h.assertBlockPresent(ModBlocks.SLUICE_BOX.get(), SLUICE);
        check(h, h.getBlockState(SLUICE).getValue(SluiceBoxBlock.FACING) == Direction.WEST,
                "tray faces the player (west), " + h.getBlockState(SLUICE).getValue(SluiceBoxBlock.FACING));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIRT_CLUMP.get(), 6));
        p.setShiftKeyDown(true);
        BlockPos abs = h.absolutePos(SLUICE);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        check(h, be(h, SLUICE).queued() == 4 && p.getMainHandItem().getCount() == 2,
                "sneak-click fills the queue: " + be(h, SLUICE).queued() + " queued, " + p.getMainHandItem().getCount() + " in hand");
        p.setShiftKeyDown(false);
        h.useBlock(SLUICE, p);
        check(h, be(h, SLUICE).queued() == 4 && p.getMainHandItem().getCount() == 2, "full queue takes no more");
        check(h, h.getBlockState(SLUICE).getValue(SluiceBoxBlock.DIRT), "dirt layer shows while clumps are queued");
        h.succeed();
    }
}
