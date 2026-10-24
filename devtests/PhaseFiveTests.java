package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.BeltMode;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.DrillStatus;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

/**
 * Sandbox-only GameTests for Phase 5 (not shipped): Clump Belts and the Phase 4 fixes.
 * Templates: "empty" (5x4x5, stone floor at y=0) and "empty_long" (26x5x5).
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseFiveTests {
    private static final int STEPS = ClumpBeltBlockEntity.STEPS;

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h, int sizeX) {
        for (int x = 0; x < sizeX; x++) for (int y = 1; y <= 3; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    /** Places a block the way a player would see it (shape-dependent states such as the spout computed). */
    private static void place(GameTestHelper h, BlockPos rel, BlockState state) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, Block.updateFromNeighbourShapes(state, h.getLevel(), abs), 3);
        TestWater.underBelt(h, rel); // Phase 12: belts need water
    }

    private static void belt(GameTestHelper h, BlockPos rel, Direction facing) {
        place(h, rel, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, facing));
    }

    private static ClumpBeltBlockEntity be(GameTestHelper h, BlockPos rel) {
        return (ClumpBeltBlockEntity) h.getBlockEntity(rel);
    }

    private static BeltMode mode(GameTestHelper h, BlockPos rel) {
        return h.getBlockState(rel).getValue(ClumpBeltBlock.MODE);
    }

    private static int fill(GameTestHelper h, BlockPos pan) {
        return h.getBlockState(pan).getValue(PanBlock.FILL);
    }

    private static void pan(GameTestHelper h, BlockPos rel, int fill) {
        h.setBlock(rel, ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, fill));
    }

    /** Sum of the counts of item entities of {@code item} in the whole test area. */
    private static int items(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static int countInInventory(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Intake (x=1, in a stone basin) -> drill (x=2, facing east, on Dirtrock) in row z. Returns the drill pos. */
    private static BlockPos drillRow(GameTestHelper h, int z) {
        h.setBlock(new BlockPos(0, 1, z), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z - 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z + 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(new BlockPos(2, 0, z), ModBlocks.DIRTROCK.get());
        BlockPos drill = new BlockPos(2, 1, z);
        h.setBlock(drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        return drill;
    }

    /** Skips most of the drill's 15 s cycle so a clump comes out {@code ticks} ticks from now. */
    private static void drillReadyIn(GameTestHelper h, BlockPos drill, int ticks) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("WaterPowered", true);
        tag.putInt("Progress", MiningDrillBlock.CYCLE_TICKS - ticks);
        h.getBlockEntity(drill).loadCustomOnly(tag, h.getLevel().registryAccess());
    }

    private static DrillStatus status(GameTestHelper h, BlockPos drill) {
        return h.getBlockState(drill).getValue(MiningDrillBlock.STATUS);
    }

    // ------------------------------------------------------------------------------------------------
    // Drill -> belts -> pan
    // ------------------------------------------------------------------------------------------------

    /**
     * Drill (x=2) -> belts x=3,4,5 (east) -> pan x=6. The drill's clump enters the first belt at step 0, takes
     * 20 ticks per belt, drops into the pan (10 ticks) and fills it. Then 3 more clumps by hand fill it to 4.
     */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void drillBeltsPanDeliversClumps(GameTestHelper h) {
        clear(h, 8);
        BlockPos drill = drillRow(h, 2);
        BlockPos b1 = new BlockPos(3, 1, 2), b2 = new BlockPos(4, 1, 2), b3 = new BlockPos(5, 1, 2), pan = new BlockPos(6, 1, 2);
        pan(h, pan, 0);
        belt(h, b1, Direction.EAST);
        belt(h, b2, Direction.EAST);
        belt(h, b3, Direction.EAST);
        long[] entered = new long[1];
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(h, status(h, drill) == DrillStatus.WORKING, "drill should work, is " + status(h, drill));
                    check(h, h.getBlockState(b3).getValue(ClumpBeltBlock.SPOUT), "last belt (pan in front) shows the spout");
                    check(h, !h.getBlockState(b1).getValue(ClumpBeltBlock.SPOUT), "first belt has no spout");
                    check(h, mode(h, b1) == BeltMode.EMPTY, "empty belt is EMPTY (no ticker)");
                    drillReadyIn(h, drill, 3);
                })
                .thenWaitUntil(() -> check(h, be(h, b1).clumpCount() == 1, "drill clump should arrive on the first belt"))
                .thenExecute(() -> {
                    entered[0] = h.getTick();
                    check(h, be(h, b1).positions()[0] <= 1, "drill clump enters at the back, at " + Arrays.toString(be(h, b1).positions()));
                    check(h, ((MiningDrillBlockEntity) h.getBlockEntity(drill)).getProgress() < 5, "drill restarted its cycle");
                })
                .thenExecuteAfter(10, () -> {
                    check(h, mode(h, b1) == BeltMode.MOVING, "belt with a moving clump is MOVING");
                    int p = be(h, b1).positions()[0];
                    check(h, p >= 9 && p <= 11, "1 step per tick: ~10 after 10 ticks, at " + p);
                })
                .thenWaitUntil(() -> check(h, be(h, b3).drops().size() == 1, "clump drops off the last belt into the pan"))
                .thenExecute(() -> {
                    long travel = h.getTick() - entered[0];
                    check(h, travel >= 3 * STEPS - 3 && travel <= 3 * STEPS + 3, "3 belts take ~60 ticks, took " + travel);
                    check(h, fill(h, pan) == 0, "pan only gets the dirt when the clump lands");
                    check(h, be(h, b1).clumpCount() + be(h, b2).clumpCount() + be(h, b3).clumpCount() == 0, "nothing left on the belts");
                })
                .thenExecuteAfter(ClumpBeltBlockEntity.DROP_TICKS + 1, () -> {
                    check(h, fill(h, pan) == 1, "landed clump fills the pan, fill " + fill(h, pan));
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "no item entities");
                    check(h, be(h, b1).insertByPlayer(3) == 3, "3 clumps fit on an empty belt by hand");
                })
                .thenWaitUntil(() -> check(h, fill(h, pan) == PanBlock.MAX_FILL, "3 more clumps fill the pan to 4"))
                .thenExecuteAfter(2, () -> {
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "still no item entities");
                    check(h, mode(h, b1) == BeltMode.EMPTY && mode(h, b2) == BeltMode.EMPTY && mode(h, b3) == BeltMode.EMPTY,
                            "belts go back to EMPTY (stop ticking) once the clumps are through");
                })
                .thenSucceed();
    }

    /** A full pan: each clump bounces off and is destroyed. The pan stays at 4, no items appear. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fullPanVoidsClumps(GameTestHelper h) {
        clear(h, 5);
        BlockPos b1 = new BlockPos(1, 1, 2), b2 = new BlockPos(2, 1, 2), pan = new BlockPos(3, 1, 2);
        pan(h, pan, PanBlock.MAX_FILL);
        belt(h, b1, Direction.EAST);
        belt(h, b2, Direction.EAST);
        check(h, be(h, b1).insertByPlayer(4) == 4, "4 clumps fit on one belt");
        check(h, be(h, b1).insertByPlayer(1) == 0, "a 5th doesn't");
        h.startSequence()
                .thenExecuteAfter(100, () -> {
                    check(h, fill(h, pan) == PanBlock.MAX_FILL, "full pan stays at 4");
                    check(h, be(h, b1).clumpCount() == 0 && be(h, b2).clumpCount() == 0 && be(h, b2).drops().isEmpty(),
                            "all 4 clumps bounced off and were destroyed");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "voided clumps leave no item entities");
                    check(h, mode(h, b1) == BeltMode.EMPTY && mode(h, b2) == BeltMode.EMPTY, "belts are EMPTY again");
                })
                .thenSucceed();
    }

    /**
     * Drill -> 2 belts -> air. Pre-filled belts back up (19,14,9,4 on both), the drill can't put its clump on and
     * goes to "stopped". Taking the clumps off the front belt gets the line moving and the drill restarts.
     */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void beltIntoAirBacksUpAndStopsDrill(GameTestHelper h) {
        clear(h, 8);
        BlockPos drill = drillRow(h, 2);
        BlockPos b1 = new BlockPos(3, 1, 2), b2 = new BlockPos(4, 1, 2);
        belt(h, b1, Direction.EAST);
        belt(h, b2, Direction.EAST);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    be(h, b1).insertByPlayer(4);
                    be(h, b2).insertByPlayer(4);
                    drillReadyIn(h, drill, 30);
                })
                .thenExecuteAfter(40, () -> {
                    check(h, Arrays.equals(be(h, b2).positions(), new int[]{19, 14, 9, 4}), "front belt backed up: " + Arrays.toString(be(h, b2).positions()));
                    check(h, Arrays.equals(be(h, b1).positions(), new int[]{19, 14, 9, 4}), "second belt backed up: " + Arrays.toString(be(h, b1).positions()));
                    check(h, mode(h, b1) == BeltMode.BLOCKED && mode(h, b2) == BeltMode.BLOCKED, "backed-up belts are BLOCKED (static model)");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "clumps stop at the end, nothing falls off");
                })
                .thenWaitUntil(() -> check(h, status(h, drill) == DrillStatus.STOPPED, "drill stops when the belt is full, is " + status(h, drill)))
                .thenExecute(() -> {
                    check(h, be(h, b1).clumpCount() + be(h, b2).clumpCount() == 8, "no clump lost or added");
                    be(h, b2).takeAll();
                })
                .thenWaitUntil(() -> check(h, status(h, drill) == DrillStatus.WORKING, "drill restarts once the belt moves again"))
                .thenWaitUntil(() -> check(h, be(h, b1).clumpCount() + be(h, b2).clumpCount() == 5, "drill's waiting clump went onto the belt"))
                .thenSucceed();
    }

    /**
     * Two belts point into the north and south sides of the main belt; all 4 clumps merge (entering at the middle of
     * the main belt) and reach the pan. A belt pointing head-on into a belt's front hands nothing over.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sideMergeFromTwoBelts(GameTestHelper h) {
        clear(h, 5);
        BlockPos main = new BlockPos(1, 1, 2), main2 = new BlockPos(2, 1, 2), pan = new BlockPos(3, 1, 2);
        BlockPos north = new BlockPos(1, 1, 1), south = new BlockPos(1, 1, 3);
        BlockPos headOnA = new BlockPos(3, 1, 0), headOnB = new BlockPos(4, 1, 0);
        pan(h, pan, 0);
        belt(h, main, Direction.EAST);
        belt(h, main2, Direction.EAST);
        belt(h, north, Direction.SOUTH);   // points into main's north side
        belt(h, south, Direction.NORTH);   // points into main's south side
        belt(h, headOnA, Direction.EAST);  // A -> B's front
        belt(h, headOnB, Direction.WEST);  // B -> A's front
        be(h, north).insertByPlayer(2);
        be(h, south).insertByPlayer(2);
        be(h, headOnA).insertByPlayer(1);
        boolean[] sawSideEntry = new boolean[1];
        h.onEachTick(() -> {
            for (ClumpBeltBlockEntity.Clump c : be(h, main).clumps()) {
                if (c.side() >= 0) sawSideEntry[0] = true;
            }
        });
        h.startSequence()
                .thenWaitUntil(() -> check(h, fill(h, pan) == 4, "all 4 merged clumps should reach the pan, fill " + fill(h, pan)))
                .thenExecute(() -> {
                    check(h, sawSideEntry[0], "clumps entered the main belt from its sides");
                    check(h, be(h, north).clumpCount() + be(h, south).clumpCount() + be(h, main).clumpCount() + be(h, main2).clumpCount() == 0, "belts empty");
                    check(h, be(h, headOnA).clumpCount() == 1 && be(h, headOnB).clumpCount() == 0, "head-on belts don't hand over");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "nothing dropped");
                })
                .thenSucceed();
    }

    /**
     * Redstone: B1 -> B2 -> B3 -> pan, 4 clumps. A redstone block next to B2 freezes it (positions unchanged,
     * nothing enters or leaves, B1 backs up behind it); removing it resumes the line and all 4 reach the pan.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void redstoneStopsAndResumesLine(GameTestHelper h) {
        clear(h, 5);
        BlockPos b1 = new BlockPos(0, 1, 2), b2 = new BlockPos(1, 1, 2), b3 = new BlockPos(2, 1, 2), pan = new BlockPos(3, 1, 2);
        BlockPos torch = new BlockPos(1, 1, 1);
        pan(h, pan, 0);
        belt(h, b1, Direction.EAST);
        belt(h, b2, Direction.EAST);
        belt(h, b3, Direction.EAST);
        be(h, b1).insertByPlayer(4);
        int[][] frozen = new int[1][];
        h.startSequence()
                .thenExecuteAfter(25, () -> {
                    h.setBlock(torch, Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(1, () -> {
                    check(h, h.getBlockState(b2).getValue(ClumpBeltBlock.POWERED), "belt next to a redstone block is powered");
                    frozen[0] = be(h, b2).positions();
                    check(h, frozen[0].length > 0, "some clumps were on B2 when it stopped");
                })
                .thenExecuteAfter(40, () -> {
                    check(h, Arrays.equals(be(h, b2).positions(), frozen[0]), "powered belt: clumps frozen, "
                            + Arrays.toString(frozen[0]) + " -> " + Arrays.toString(be(h, b2).positions()));
                    check(h, be(h, b1).clumpCount() + be(h, b2).clumpCount() + be(h, b3).clumpCount()
                            + be(h, b3).drops().size() + fill(h, pan) == 4, "no clump lost while stopped");
                    check(h, be(h, b2).canAccept(h.getBlockState(b2), Direction.WEST) == false, "powered belt takes nothing in");
                    h.setBlock(torch, Blocks.AIR);
                })
                .thenExecuteAfter(1, () -> check(h, !h.getBlockState(b2).getValue(ClumpBeltBlock.POWERED), "signal gone: unpowered"))
                .thenWaitUntil(() -> check(h, fill(h, pan) == 4, "line resumed: all 4 clumps in the pan, fill " + fill(h, pan)))
                .thenExecute(() -> check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "nothing dropped"))
                .thenSucceed();
    }

    /** Powering a belt while a clump is dropping into the pan lands it at once - exactly one fill, never two. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void redstoneDuringDropLandsOnce(GameTestHelper h) {
        clear(h, 5);
        BlockPos belt = new BlockPos(2, 1, 2), pan = new BlockPos(3, 1, 2), lever = new BlockPos(2, 1, 1);
        pan(h, pan, 0);
        belt(h, belt, Direction.EAST);
        be(h, belt).insertByPlayer(1);
        h.startSequence()
                .thenWaitUntil(() -> check(h, be(h, belt).drops().size() == 1, "clump starts dropping"))
                .thenExecute(() -> h.setBlock(lever, Blocks.REDSTONE_BLOCK))
                .thenExecuteAfter(1, () -> {
                    check(h, fill(h, pan) == 1, "the dropping clump landed when the belt stopped, fill " + fill(h, pan));
                    check(h, be(h, belt).drops().isEmpty(), "no drop left hanging");
                })
                .thenExecuteAfter(30, () -> check(h, fill(h, pan) == 1, "still exactly 1, fill " + fill(h, pan)))
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Player, breaking, hoppers
    // ------------------------------------------------------------------------------------------------

    /**
     * Placing faces the way the player looks. Right-click with clumps puts 1 on; sneak-right-click (through the
     * real interaction path, where vanilla normally skips blocks when sneaking) puts on as many as fit; an empty
     * hand takes them back. A belt pointing into a wall keeps them.
     */
    @GameTest(template = "empty")
    public static void playerInsertAndTake(GameTestHelper h) {
        clear(h, 5);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        h.setBlock(new BlockPos(2, 0, 2), Blocks.STONE); // the template floor isn't always solid
        BlockPos floor = h.absolutePos(new BlockPos(2, 0, 2));
        Vec3 c = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 1, 0)));
        p.moveTo(c.x, c.y, c.z, 0.0F, 30.0F); // yaw 0 = looking south
        ItemStack beltItem = new ItemStack(ModItems.CLUMP_BELT.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, beltItem);
        beltItem.useOn(new net.minecraft.world.item.context.UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        BlockPos belt = new BlockPos(2, 1, 2);
        h.assertBlockPresent(ModBlocks.CLUMP_BELT.get(), belt);
        check(h, h.getBlockState(belt).getValue(ClumpBeltBlock.FACING) == Direction.SOUTH,
                "belt faces where the player looks (south), " + h.getBlockState(belt).getValue(ClumpBeltBlock.FACING));
        h.setBlock(new BlockPos(2, 1, 3), Blocks.STONE); // the belt runs into a wall: clumps stay on it

        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIRT_CLUMP.get(), 6));
        h.useBlock(belt, p);
        check(h, be(h, belt).clumpCount() == 1 && p.getMainHandItem().getCount() == 5, "right-click puts 1 clump on");
        p.setShiftKeyDown(true);
        BlockPos abs = h.absolutePos(belt);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        check(h, be(h, belt).clumpCount() == 4 && p.getMainHandItem().getCount() == 2,
                "sneak-click fills the belt: " + be(h, belt).clumpCount() + " on, " + p.getMainHandItem().getCount() + " in hand");
        p.setShiftKeyDown(false);
        h.useBlock(belt, p);
        check(h, be(h, belt).clumpCount() == 4 && p.getMainHandItem().getCount() == 2, "full belt takes no more");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.getInventory().clearContent();
        h.useBlock(belt, p);
        check(h, be(h, belt).clumpCount() == 0 && countInInventory(p, ModItems.DIRT_CLUMP.get()) == 4,
                "empty hand takes the 4 clumps back, got " + countInInventory(p, ModItems.DIRT_CLUMP.get()));
        check(h, mode(h, belt) == BeltMode.EMPTY || be(h, belt).isEmpty(), "belt is empty");
        h.succeed();
    }

    /** Breaking a belt drops the belt and the clumps on it. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void breakingBeltDropsClumps(GameTestHelper h) {
        clear(h, 5);
        BlockPos belt = new BlockPos(2, 1, 2);
        belt(h, belt, Direction.EAST);
        h.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        be(h, belt).insertByPlayer(3);
        h.runAfterDelay(5, () -> {
            h.getLevel().destroyBlock(h.absolutePos(belt), true);
            h.runAfterDelay(2, () -> {
                check(h, items(h, ModItems.DIRT_CLUMP.get()) == 3, "3 clumps dropped, got " + items(h, ModItems.DIRT_CLUMP.get()));
                check(h, items(h, ModItems.CLUMP_BELT.get()) == 1, "the belt dropped itself");
                h.succeed();
            });
        });
    }

    /** Hoppers can neither put clumps on a belt nor pull them off; the belt exposes no item handler. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void hopperCannotInsertOrExtract(GameTestHelper h) {
        clear(h, 5);
        BlockPos belt = new BlockPos(2, 2, 2), above = new BlockPos(2, 3, 2), below = new BlockPos(2, 1, 2);
        h.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
        belt(h, belt, Direction.EAST);
        h.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        h.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) h.getBlockEntity(above)).setItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 5));
        be(h, belt).insertByPlayer(2);
        for (Direction d : Direction.values()) {
            check(h, h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(belt), d) == null, "no item handler on " + d);
        }
        check(h, h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(belt), null) == null, "no item handler (null side)");
        h.runAfterDelay(100, () -> {
            check(h, ((HopperBlockEntity) h.getBlockEntity(above)).getItem(0).getCount() == 5, "hopper above kept its 5 clumps");
            check(h, ((HopperBlockEntity) h.getBlockEntity(below)).isEmpty(), "hopper below pulled nothing");
            check(h, be(h, belt).clumpCount() == 2, "belt still has its 2 clumps, has " + be(h, belt).clumpCount());
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 4 fixes
    // ------------------------------------------------------------------------------------------------

    /** Intake collars: the intake shows a connection on every side (and the top) that has a pipe. */
    @GameTest(template = "empty")
    public static void intakeShowsPipeConnections(GameTestHelper h) {
        clear(h, 5);
        BlockPos intake = new BlockPos(2, 1, 2);
        place(h, intake, ModBlocks.WATER_INTAKE.get().defaultBlockState());
        place(h, intake.above(), ModBlocks.WATER_PIPE.get().defaultBlockState());
        place(h, intake.north(), ModBlocks.WATER_PIPE.get().defaultBlockState());
        h.setBlock(intake.east(), Blocks.STONE);
        BlockState s = h.getBlockState(intake);
        check(h, s.getValue(PipeBlock.UP) && s.getValue(PipeBlock.NORTH), "collars up and north: " + s);
        check(h, !s.getValue(PipeBlock.EAST) && !s.getValue(PipeBlock.SOUTH) && !s.getValue(PipeBlock.WEST) && !s.getValue(PipeBlock.DOWN),
                "no collars where there's no pipe: " + s);
        check(h, h.getBlockState(intake.above()).getValue(PipeBlock.DOWN), "pipe above reaches down to the intake");
        h.setBlock(intake.above(), Blocks.AIR);
        check(h, !h.getBlockState(intake).getValue(PipeBlock.UP), "collar goes when the pipe goes");
        // placed with a pipe already there
        h.setBlock(intake, Blocks.AIR);
        place(h, intake, ModBlocks.WATER_INTAKE.get().defaultBlockState());
        check(h, h.getBlockState(intake).getValue(PipeBlock.NORTH), "collar computed on placement");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Client prediction
    // ------------------------------------------------------------------------------------------------

    /** A stand-in for a client's copy of one belt: ticks like a client and reloads when the server syncs. */
    private static final class Mirror {
        final BlockPos rel;
        ClumpBeltBlockEntity shadow;
        int lastSync = -1;
        boolean ticking;

        Mirror(BlockPos rel) {
            this.rel = rel;
        }

        /** One client tick, then the sync packet if the server sent one. Returns a mismatch description or null. */
        String tick(GameTestHelper h) {
            ClumpBeltBlockEntity server = be(h, rel);
            BlockState state = h.getBlockState(rel);
            var reg = h.getLevel().registryAccess();
            if (shadow == null) {
                shadow = new ClumpBeltBlockEntity(h.absolutePos(rel), state);
                shadow.setLevel(h.getLevel());
            }
            if (ticking) {
                ClumpBeltBlockEntity.clientTick(h.getLevel(), h.absolutePos(rel), state, shadow);
            }
            if (server.syncCount() != lastSync) {
                shadow.loadWithComponents(server.getUpdateTag(reg), reg);
                lastSync = server.syncCount();
            }
            ticking = state.getValue(ClumpBeltBlock.MODE) == BeltMode.MOVING && !state.getValue(ClumpBeltBlock.POWERED);
            int[] a = server.positions(), b = shadow.positions();
            int[] sa = server.clumps().stream().mapToInt(ClumpBeltBlockEntity.Clump::sideTicks).toArray();
            int[] sb = shadow.clumps().stream().mapToInt(ClumpBeltBlockEntity.Clump::sideTicks).toArray();
            if (!Arrays.equals(a, b) || !Arrays.equals(sa, sb)) {
                return rel + " server " + Arrays.toString(a) + Arrays.toString(sa) + " client " + Arrays.toString(b) + Arrays.toString(sb);
            }
            return null;
        }
    }

    /**
     * Drill + hand-fed clumps + a side feeder + a redstone stop on a 4-belt line into a pan, with a simulated client
     * copy of every belt. Between server syncs the client copies must predict exactly the server's positions.
     * Also logs how many syncs were sent compared with the number of clump-steps moved.
     */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void clientPredictionMatchesServer(GameTestHelper h) {
        clear(h, 10);
        BlockPos drill = drillRow(h, 2);
        BlockPos pan = new BlockPos(7, 1, 2);
        pan(h, pan, 0);
        BlockPos[] line = {new BlockPos(3, 1, 2), new BlockPos(4, 1, 2), new BlockPos(5, 1, 2), new BlockPos(6, 1, 2)};
        for (BlockPos b : line) belt(h, b, Direction.EAST);
        BlockPos feeder = new BlockPos(5, 1, 1);
        belt(h, feeder, Direction.SOUTH);
        BlockPos redstone = new BlockPos(4, 1, 3);
        Mirror[] mirrors = {new Mirror(line[0]), new Mirror(line[1]), new Mirror(line[2]), new Mirror(line[3]), new Mirror(feeder)};
        java.util.List<String> errors = new java.util.ArrayList<>();
        int[] steps = new int[1];
        int[][] last = new int[mirrors.length][];
        h.onEachTick(() -> {
            for (int i = 0; i < mirrors.length; i++) {
                String e = mirrors[i].tick(h);
                if (e != null && errors.size() < 10) errors.add("t=" + h.getTick() + " " + e);
                int[] now = be(h, mirrors[i].rel).positions();
                if (last[i] != null && now.length == last[i].length) {
                    for (int k = 0; k < now.length; k++) steps[0] += Math.max(0, now[k] - last[i][k]);
                }
                last[i] = now;
            }
        });
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    be(h, line[0]).insertByPlayer(4);
                    be(h, feeder).insertByPlayer(2);
                    drillReadyIn(h, drill, 20);
                })
                .thenExecuteAfter(30, () -> h.setBlock(redstone, Blocks.REDSTONE_BLOCK))
                .thenExecuteAfter(25, () -> h.setBlock(redstone, Blocks.AIR))
                .thenExecuteAfter(150, () -> {
                    check(h, errors.isEmpty(), "client prediction differs: " + errors);
                    int syncs = 0;
                    for (Mirror m : mirrors) syncs += be(h, m.rel).syncCount();
                    HoleyDigsite.LOGGER.info("[belt-sync] {} syncs for {} clump-steps moved", syncs, steps[0]);
                    check(h, fill(h, pan) == PanBlock.MAX_FILL, "pan filled, " + fill(h, pan));
                    check(h, steps[0] > 100, "clumps moved, " + steps[0]);
                })
                .thenSucceed();
    }
}
