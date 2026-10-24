package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.DrillStatus;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterPipeBlock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shovel.ShovelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Sandbox-only GameTests for Phase 4 (not shipped): Dirtrock, Dirt Clumps, water power and the Tier 1 Mining Drill.
 *
 * <p>Layout used by the drill tests (template "empty", 5x4x5 with a stone floor at y=0), one row along x:
 * <pre>  stone | intake (waterlogged) | drill (facing east) | pan          y=1
 *         |                      | dirtrock below      |              y=0</pre>
 * The intake sits in a little stone basin so its water can't run off into other tests.</p>
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public class PhaseFourTests {
    private static final int CYCLE = MiningDrillBlock.CYCLE_TICKS;

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    /** Builds intake (x=1) -> drill (x=2, facing east) -> pan (x=3) in row z. Returns the drill position. */
    private static BlockPos drillRow(GameTestHelper h, int z, boolean dirtrock, boolean water, int panFill) {
        h.setBlock(new BlockPos(0, 1, z), Blocks.STONE);              // basin around the intake
        h.setBlock(new BlockPos(1, 1, z - 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z + 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, water));
        h.setBlock(new BlockPos(2, 0, z), dirtrock ? ModBlocks.DIRTROCK.get() : Blocks.STONE);
        BlockPos drill = new BlockPos(2, 1, z);
        h.setBlock(drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        if (panFill >= 0) {
            h.setBlock(new BlockPos(3, 1, z), ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, panFill));
        } else {
            h.setBlock(new BlockPos(3, 1, z), Blocks.AIR);
        }
        return drill;
    }

    /** Sets a block the way a player placing it would see it: pipe connections computed from the neighbours. */
    private static void place(GameTestHelper h, BlockPos rel, BlockState state) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, h.getLevel(), abs), 3);
    }

    /** The "empty" template isn't always clean: clear everything above the floor. */
    private static void clear(GameTestHelper h, int sizeX) {
        for (int x = 0; x < sizeX; x++) for (int y = 1; y <= 3; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    private static DrillStatus status(GameTestHelper h, BlockPos drill) {
        return h.getBlockState(drill).getValue(MiningDrillBlock.STATUS);
    }

    private static MiningDrillBlockEntity drillBe(GameTestHelper h, BlockPos drill) {
        return (MiningDrillBlockEntity) h.getBlockEntity(drill);
    }

    private static int fill(GameTestHelper h, BlockPos pan) {
        return h.getBlockState(pan).getValue(PanBlock.FILL);
    }

    private static String messageKey(GameTestHelper h, BlockPos drill) {
        BlockPos abs = h.absolutePos(drill);
        Component c = MiningDrillBlock.statusMessage(h.getLevel(), abs, h.getLevel().getBlockState(abs), drillBe(h, drill));
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    // ------------------------------------------------------------------------------------------------
    // Drill
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void drillOnDirtrockPoweredFillsPan(GameTestHelper h) {
        clear(h, 5);
        BlockPos drill = drillRow(h, 2, true, true, 0);
        BlockPos pan = new BlockPos(3, 1, 2);
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(h, drillBe(h, drill).isWaterPowered(), "drill next to an active intake should be powered");
                    check(h, status(h, drill) == DrillStatus.WORKING, "powered drill on Dirtrock should work, is " + status(h, drill));
                    check(h, messageKey(h, drill).equals("message.holeydigsite.drill.working"), "status message " + messageKey(h, drill));
                })
                .thenExecuteAfter(CYCLE - 10, () -> check(h, fill(h, pan) == 0, "no clump before 300 ticks"))
                .thenExecuteAfter(15, () -> {
                    check(h, fill(h, pan) == 1, "one clump after ~300 ticks, fill " + fill(h, pan));
                    h.assertItemEntityNotPresent(ModItems.DIRT_CLUMP.get());
                })
                .thenExecuteAfter(CYCLE, () -> {
                    check(h, fill(h, pan) == 2, "second clump after another 300 ticks, fill " + fill(h, pan));
                    check(h, h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(drill), Direction.EAST) == null
                            && h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(drill), null) == null,
                            "the drill must expose no item handler");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void drillWithoutDirtrockOrWaterDoesNothing(GameTestHelper h) {
        clear(h, 5);
        BlockPos onStone = drillRow(h, 1, false, true, 0);   // powered, but on stone
        BlockPos dry = drillRow(h, 3, true, false, 0);       // on Dirtrock, intake out of water
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    check(h, status(h, onStone) == DrillStatus.NO_POWER, "drill on stone: no_power, is " + status(h, onStone));
                    check(h, messageKey(h, onStone).equals("message.holeydigsite.drill.no_dirtrock"), "reason " + messageKey(h, onStone));
                    check(h, status(h, dry) == DrillStatus.NO_POWER, "drill without water: no_power, is " + status(h, dry));
                    check(h, !drillBe(h, dry).isWaterPowered(), "dry intake gives no power");
                    check(h, messageKey(h, dry).equals("message.holeydigsite.drill.no_water"), "reason " + messageKey(h, dry));
                })
                .thenExecuteAfter(CYCLE + 30, () -> {
                    check(h, fill(h, new BlockPos(3, 1, 1)) == 0 && fill(h, new BlockPos(3, 1, 3)) == 0, "unpowered drills produce nothing");
                    check(h, drillBe(h, onStone).getProgress() == 0 && drillBe(h, dry).getProgress() == 0, "unpowered drills don't progress");
                    h.assertItemEntityNotPresent(ModItems.DIRT_CLUMP.get());
                    // water arrives -> starts working without any other trigger
                    h.setBlock(new BlockPos(1, 1, 3), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
                })
                .thenExecuteAfter(2, () -> check(h, status(h, dry) == DrillStatus.WORKING, "drill should start once its intake is in water"))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void drillStopsWhenBlockedAndRestarts(GameTestHelper h) {
        clear(h, 5);
        BlockPos full = drillRow(h, 1, true, true, PanBlock.MAX_FILL);  // pan already full
        BlockPos nothing = drillRow(h, 3, true, true, -1);              // nothing in front
        BlockPos pan = new BlockPos(3, 1, 1);
        h.startSequence()
                .thenExecuteAfter(30, () -> check(h, status(h, full) == DrillStatus.WORKING, "still working during the 3 s grace"))
                .thenExecuteAfter(50, () -> {
                    check(h, status(h, full) == DrillStatus.STOPPED, "full pan: stopped after > 3 s, is " + status(h, full));
                    check(h, status(h, nothing) == DrillStatus.STOPPED, "no target: stopped, is " + status(h, nothing));
                    check(h, messageKey(h, full).equals("message.holeydigsite.drill.stopped"), "reason " + messageKey(h, full));
                    // empty the pan: the neighbour update restarts the drill straight away
                    h.setBlock(pan, ModBlocks.PAN.get().defaultBlockState());
                })
                .thenExecuteAfter(1, () -> check(h, status(h, full) == DrillStatus.WORKING, "emptied pan restarts the drill, is " + status(h, full)))
                .thenExecuteAfter(CYCLE, () -> {
                    check(h, fill(h, pan) == 1, "restarted drill delivered a clump, fill " + fill(h, pan));
                    check(h, status(h, nothing) == DrillStatus.STOPPED, "drill with nothing in front stays stopped");
                    h.assertItemEntityNotPresent(ModItems.DIRT_CLUMP.get()); // never drops clumps into the world
                    // a hopper in front is NOT an accepted target
                    h.setBlock(new BlockPos(3, 1, 3), Blocks.HOPPER);
                })
                .thenExecuteAfter(RECHECK + 5, () -> {
                    check(h, status(h, nothing) == DrillStatus.STOPPED, "a hopper is not a drill target");
                    HopperBlockEntity hopper = (HopperBlockEntity) h.getBlockEntity(new BlockPos(3, 1, 3));
                    check(h, hopper.isEmpty(), "the drill must never put clumps into a hopper");
                })
                .thenSucceed();
    }

    private static final int RECHECK = MiningDrillBlock.RECHECK_TICKS;

    @GameTest(template = "empty")
    public static void drillPlacementAndStatusRightClick(GameTestHelper h) {
        clear(h, 5);
        // Phase 5: placed with its FRONT towards the player: player looks east -> front faces west (at the player),
        // water port (back) faces east, away from the player
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        BlockPos floor = h.absolutePos(new BlockPos(2, 0, 2));
        h.setBlock(new BlockPos(2, 0, 2), ModBlocks.DIRTROCK.get());
        Vec3 c = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 1, 2)));
        p.moveTo(c.x, c.y, c.z, -90.0F, 30.0F); // yaw -90 = looking east
        ItemStack drillItem = new ItemStack(ModItems.MINING_DRILL.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, drillItem);
        drillItem.useOn(new net.minecraft.world.item.context.UseOnContext(p, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        BlockPos drill = new BlockPos(2, 1, 2);
        h.assertBlockPresent(ModBlocks.MINING_DRILL.get(), drill);
        check(h, h.getBlockState(drill).getValue(MiningDrillBlock.FACING) == Direction.WEST,
                "drill front should face the player (west), faces " + h.getBlockState(drill).getValue(MiningDrillBlock.FACING));
        check(h, ((MiningDrillBlock) ModBlocks.MINING_DRILL.get()).hasWaterConnection(h.getBlockState(drill), Direction.EAST),
                "the water port should face away from the player (east)");
        // empty-hand right-click shows the status (and doesn't break or pick anything up)
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.useBlock(drill, p);
        h.assertBlockPresent(ModBlocks.MINING_DRILL.get(), drill);
        // breaking it by hand drops the drill (no tool needed)
        check(h, net.minecraft.world.level.block.Block.getDrops(h.getBlockState(drill), h.getLevel(), h.absolutePos(drill), h.getBlockEntity(drill))
                .stream().anyMatch(s -> s.is(ModItems.MINING_DRILL.get())), "drill drops itself");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Pan input: right-click and item handler
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty")
    public static void panTakesClumpsByHand(GameTestHelper h) {
        clear(h, 5);
        BlockPos pan = new BlockPos(2, 1, 2);
        h.setBlock(pan, ModBlocks.PAN.get());
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIRT_CLUMP.get(), 6));
        check(h, new ItemStack(ModItems.DIRT_CLUMP.get()).getMaxStackSize() == 64, "clumps stack to 64");
        for (int i = 1; i <= PanBlock.MAX_FILL; i++) {
            h.useBlock(pan, p);
            check(h, fill(h, pan) == i, "fill should be " + i);
            check(h, p.getMainHandItem().getCount() == 6 - i, "one clump used per fill");
        }
        h.useBlock(pan, p);
        check(h, fill(h, pan) == PanBlock.MAX_FILL && p.getMainHandItem().getCount() == 2, "full pan: nothing happens, clumps kept");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void panItemHandlerAcceptsOnlyClumps(GameTestHelper h) {
        clear(h, 5);
        BlockPos pan = new BlockPos(2, 1, 2);
        h.setBlock(pan, ModBlocks.PAN.get());
        IItemHandler handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pan), Direction.UP);
        check(h, handler != null, "pan exposes an item handler");
        ItemStack dirt = new ItemStack(Items.DIRT, 3);
        check(h, handler.insertItem(0, dirt, false).getCount() == 3 && fill(h, pan) == 0, "pan rejects non-clumps");
        ItemStack rest = handler.insertItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 3), true);
        check(h, rest.getCount() == 0 && fill(h, pan) == 0, "simulate accepts 3 but changes nothing");
        rest = handler.insertItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 6), false);
        check(h, rest.getCount() == 2 && fill(h, pan) == 4, "6 clumps -> fill 4, 2 handed back; got " + rest + " fill " + fill(h, pan));
        rest = handler.insertItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 1), false);
        check(h, rest.getCount() == 1, "full pan rejects clumps");
        check(h, handler.extractItem(0, 64, false).isEmpty() && handler.getStackInSlot(0).isEmpty(), "nothing can be extracted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void hopperInsertsClumpsIntoPan(GameTestHelper h) {
        clear(h, 5);
        BlockPos pan = new BlockPos(1, 1, 2), hopper = new BlockPos(1, 2, 2);
        BlockPos pan2 = new BlockPos(3, 1, 2), hopper2 = new BlockPos(3, 2, 2);
        h.setBlock(pan, ModBlocks.PAN.get());
        h.setBlock(pan2, ModBlocks.PAN.get());
        h.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        h.setBlock(hopper2, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) h.getBlockEntity(hopper)).setItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 6));
        ((HopperBlockEntity) h.getBlockEntity(hopper2)).setItem(0, new ItemStack(Items.DIRT, 3));
        h.runAfterDelay(100, () -> {
            check(h, fill(h, pan) == PanBlock.MAX_FILL, "hopper filled the pan to 4, fill " + fill(h, pan));
            ItemStack left = ((HopperBlockEntity) h.getBlockEntity(hopper)).getItem(0);
            check(h, left.is(ModItems.DIRT_CLUMP.get()) && left.getCount() == 2, "2 clumps stay in the hopper, " + left);
            check(h, fill(h, pan2) == 0, "hopper can't put dirt blocks into a pan");
            check(h, ((HopperBlockEntity) h.getBlockEntity(hopper2)).getItem(0).getCount() == 3, "dirt stays in the hopper");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Water network
    // ------------------------------------------------------------------------------------------------

    /**
     * Template "empty_long" (26x5x5): intake at x=1 in a basin, pipes x=2..23 (22 pipes), drill at x=24 facing east,
     * pan at x=25. Break a pipe -> power lost; put it back -> power back; intake loses water -> power lost.
     */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void pipeNetworkPowersThroughManyPipes(GameTestHelper h) {
        int z = 2;
        clear(h, 26);
        h.setBlock(new BlockPos(0, 1, z), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z - 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z + 1), Blocks.STONE);
        BlockPos intake = new BlockPos(1, 1, z);
        h.setBlock(intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        for (int x = 2; x <= 23; x++) {
            place(h, new BlockPos(x, 1, z), ModBlocks.WATER_PIPE.get().defaultBlockState());
        }
        h.setBlock(new BlockPos(24, 0, z), ModBlocks.DIRTROCK.get());
        BlockPos drill = new BlockPos(24, 1, z);
        h.setBlock(drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(25, 1, z), ModBlocks.PAN.get());
        BlockPos cut = new BlockPos(12, 1, z);

        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(h, drillBe(h, drill).isWaterPowered() && status(h, drill) == DrillStatus.WORKING,
                            "drill powered through 22 pipes, status " + status(h, drill));
                    for (int x = 2; x <= 23; x++) {
                        BlockState s = h.getBlockState(new BlockPos(x, 1, z));
                        check(h, s.getValue(WaterPipeBlock.FILLED), "pipe at x=" + x + " should show water");
                        check(h, s.getValue(WaterPipeBlock.EAST) && s.getValue(WaterPipeBlock.WEST) && !s.getValue(WaterPipeBlock.NORTH),
                                "pipe at x=" + x + " connects east+west only");
                    }
                    h.setBlock(cut, Blocks.AIR); // break a pipe in the middle
                })
                .thenExecuteAfter(2, () -> {
                    check(h, !drillBe(h, drill).isWaterPowered() && status(h, drill) == DrillStatus.NO_POWER,
                            "broken pipe: drill loses power, status " + status(h, drill));
                    check(h, h.getBlockState(new BlockPos(5, 1, z)).getValue(WaterPipeBlock.FILLED), "intake side still has water");
                    check(h, !h.getBlockState(new BlockPos(20, 1, z)).getValue(WaterPipeBlock.FILLED), "drill side is dry");
                    place(h, cut, ModBlocks.WATER_PIPE.get().defaultBlockState());
                })
                .thenExecuteAfter(2, () -> {
                    check(h, drillBe(h, drill).isWaterPowered() && status(h, drill) == DrillStatus.WORKING, "pipe replaced: powered again");
                    // the intake leaves the water (bucket / drained)
                    h.setBlock(intake, h.getBlockState(intake).setValue(WaterIntakeBlock.WATERLOGGED, false));
                })
                .thenExecuteAfter(2, () -> {
                    check(h, !drillBe(h, drill).isWaterPowered() && status(h, drill) == DrillStatus.NO_POWER, "dry intake: no power");
                    check(h, !h.getBlockState(new BlockPos(5, 1, z)).getValue(WaterPipeBlock.FILLED), "all pipes dry");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void pipeOnlyConnectsToDrillPort(GameTestHelper h) {
        clear(h, 5);
        // drill facing east: port is WEST. A pipe on its north side must not connect or power it.
        h.setBlock(new BlockPos(2, 0, 3), ModBlocks.DIRTROCK.get());
        BlockPos drill = new BlockPos(2, 1, 3);
        h.setBlock(drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE); // basin
        h.setBlock(new BlockPos(3, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(2, 1, 0), Blocks.STONE);
        h.setBlock(new BlockPos(2, 1, 1), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        place(h, new BlockPos(2, 1, 2), ModBlocks.WATER_PIPE.get().defaultBlockState()); // touches the drill's side
        place(h, new BlockPos(1, 1, 3), ModBlocks.WATER_PIPE.get().defaultBlockState()); // at the port, but no intake
        h.runAfterDelay(2, () -> {
            check(h, h.getBlockState(new BlockPos(2, 1, 2)).getValue(WaterPipeBlock.NORTH), "pipe connects to the intake");
            check(h, !h.getBlockState(new BlockPos(2, 1, 2)).getValue(WaterPipeBlock.SOUTH), "pipe must not connect to the drill's side");
            check(h, h.getBlockState(new BlockPos(1, 1, 3)).getValue(WaterPipeBlock.EAST), "pipe connects to the drill's back port");
            check(h, h.getBlockState(new BlockPos(2, 1, 2)).getValue(WaterPipeBlock.FILLED), "side pipe has water");
            check(h, !drillBe(h, drill).isWaterPowered(), "a pipe touching the drill's side gives no power");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Dirtrock + recipes
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dirtrockIsUnbreakable(GameTestHelper h) {
        clear(h, 5);
        BlockPos rock = new BlockPos(2, 1, 2);
        h.setBlock(rock, ModBlocks.DIRTROCK.get());
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
        // the real survival mining path: start + finish breaking (hardness is checked here, not in destroyBlock)
        BlockPos abs = h.absolutePos(rock);
        int top = h.getLevel().getMaxBuildHeight();
        p.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 0);
        p.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, top, 0);
        h.assertBlockPresent(ModBlocks.DIRTROCK.get(), rock);
        check(h, h.getBlockState(rock).getDestroyProgress(p, h.getLevel(), abs) == 0, "no mining progress on Dirtrock");
        check(h, !ShovelHelper.isFull(p.getMainHandItem()), "Dirtrock is not scoopable");
        check(h, h.getBlockState(rock).getDestroySpeed(h.getLevel(), h.absolutePos(rock)) < 0, "hardness -1 like bedrock");
        // explosion right next to it
        h.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        Vec3 boom = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 2)));
        h.getLevel().explode(null, boom.x, boom.y, boom.z, 4.0F, Level.ExplosionInteraction.TNT);
        h.assertBlockPresent(ModBlocks.DIRTROCK.get(), rock);
        h.assertBlockNotPresent(Blocks.STONE, new BlockPos(3, 1, 2)); // the explosion was real
        // pistons can't push it
        BlockPos rock2 = new BlockPos(2, 1, 1);
        h.setBlock(rock2, ModBlocks.DIRTROCK.get());
        h.setBlock(new BlockPos(1, 1, 1), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(10, () -> {
            h.assertBlockPresent(ModBlocks.DIRTROCK.get(), rock2);
            h.assertBlockNotPresent(ModBlocks.DIRTROCK.get(), new BlockPos(3, 1, 1));
            check(h, !h.getBlockState(new BlockPos(1, 1, 1)).getValue(PistonBaseBlock.EXTENDED), "piston can't extend into Dirtrock");
            h.assertItemEntityNotPresent(ModItems.DIRTROCK.get());
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void panAndBrushRecipesFromTheTree(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        CraftingMenu menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2))));
        ItemStack s = new ItemStack(Items.STICK), pl = new ItemStack(Items.OAK_PLANKS), e = ItemStack.EMPTY;
        ItemStack r = craft(menu, s, e, e, pl, e, pl, e, pl, e);
        check(h, r.is(ModItems.PAN.get()) && r.getCount() == 1, "stick + 3 planks in a bowl shape -> pan, got " + r);
        r = craft(menu, e, e, e, pl, e, pl, e, pl, e);
        check(h, r.is(Items.BOWL), "vanilla bowl recipe still works, got " + r);
        r = craft(menu, new ItemStack(Items.OAK_SAPLING), e, e, s, e, e, e, e, e);
        check(h, r.is(ModItems.BRUSH.get()), "sapling over stick -> brush, got " + r);
        r = craft(menu, new ItemStack(Items.OAK_LEAVES), e, e, s, e, e, e, e, e);
        check(h, r.is(ModItems.BRUSH.get()), "leaves over stick -> brush, got " + r);
        r = craft(menu, new ItemStack(Items.STRING), e, e, s, e, e, e, e, e);
        check(h, r.is(ModItems.BRUSH.get()), "string over stick -> brush, got " + r);
        h.succeed();
    }

    private static ItemStack craft(CraftingMenu menu, ItemStack... grid) {
        for (int i = 0; i < 9; i++) {
            menu.getSlot(1 + i).set(i < grid.length ? grid[i].copy() : ItemStack.EMPTY);
        }
        return menu.getSlot(0).getItem();
    }
}
