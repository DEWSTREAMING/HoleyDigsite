package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.BeltShape;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.belt.FilterBeltBlock;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.DrillStatus;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.CraftingForgeBlockEntity;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.tier.DirtTier;
import com.holeydigsite.transport.FilterMatch;
import com.holeydigsite.transport.GemCutting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Sandbox-only GameTests for Phase 12 (not shipped): water-powered full-height item belts, delivery targets, inclines,
 * corners, the Filter Belt, sluice tray -> belt, 2-tall drills, uncut gems and the Gem Cutter. Template {@code empty_long}
 * (26x5x5, floor y=0).
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseTwelveTests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 26; x++) for (int y = 1; y <= 4; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 0; x < 26; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    /** Places like a player would see it (spout / corner shape from the neighbours); NO water. */
    private static void put(GameTestHelper h, BlockPos rel, BlockState state) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, Block.updateFromNeighbourShapes(state, h.getLevel(), abs), 3);
    }

    private static void belt(GameTestHelper h, BlockPos rel, Block block, Direction facing, boolean water) {
        put(h, rel, block.defaultBlockState().setValue(ClumpBeltBlock.FACING, facing));
        if (water) {
            TestWater.underBelt(h, rel);
        }
    }

    private static void belt(GameTestHelper h, BlockPos rel, Direction facing) {
        belt(h, rel, ModBlocks.CLUMP_BELT.get(), facing, true);
    }

    private static ClumpBeltBlockEntity be(GameTestHelper h, BlockPos rel) {
        return (ClumpBeltBlockEntity) h.getBlockEntity(rel);
    }

    private static boolean water(GameTestHelper h, BlockPos rel) {
        return h.getBlockState(rel).getValue(ClumpBeltBlock.WATER);
    }

    private static int padCount(GameTestHelper h, BlockPos pad, Item item) {
        int n = 0;
        for (ItemStack s : ((PadBlockEntity) h.getBlockEntity(pad)).contents()) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static int padTotal(GameTestHelper h, BlockPos pad) {
        return ((PadBlockEntity) h.getBlockEntity(pad)).contents().stream().mapToInt(ItemStack::getCount).sum();
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    private static void click(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held, Vec3 offset, Direction face) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(offset), face, abs, false));
    }

    private static int itemEntities(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    // ------------------------------------------------------------------------------------------------
    // Water power
    // ------------------------------------------------------------------------------------------------

    /**
     * Without water a belt line is frozen; an intake touching ONE belt powers the whole line (belts pass water on); taking
     * the intake away stops the line again. A belt next to a drill's water port does not power the drill.
     */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void beltsNeedWaterAndShareIt(GameTestHelper h) {
        clear(h);
        BlockPos[] line = new BlockPos[5];
        for (int i = 0; i < 5; i++) {
            line[i] = new BlockPos(2 + i, 1, 2);
            belt(h, line[i], ModBlocks.CLUMP_BELT.get(), Direction.EAST, false);
        }
        // a drill whose back port touches belt 3 (drill at z=3 facing south: back = north = the belt)
        h.setBlock(new BlockPos(4, 0, 3), ModBlocks.DIRTROCK.get());
        h.setBlock(new BlockPos(4, 1, 3), ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.SOUTH));
        be(h, line[0]).insertByPlayer(1);
        BlockPos intake = new BlockPos(2, 1, 1);
        h.startSequence()
                .thenExecuteAfter(30, () -> {
                    check(h, !water(h, line[0]) && be(h, line[0]).positions()[0] == 0, "no water: frozen at step 0");
                    h.setBlock(intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
                })
                .thenExecuteAfter(2, () -> {
                    for (BlockPos b : line) check(h, water(h, b), "every belt of the line has water: " + b);
                    check(h, h.getBlockState(new BlockPos(4, 1, 3)).getValue(MiningDrillBlock.STATUS) == DrillStatus.NO_POWER,
                            "a belt doesn't power the drill next to it");
                })
                .thenExecuteAfter(25, () -> check(h, be(h, line[1]).clumpCount() == 1 || be(h, line[2]).clumpCount() == 1, "the clump moved on"))
                .thenExecute(() -> h.setBlock(intake, Blocks.AIR))
                .thenExecuteAfter(2, () -> {
                    for (BlockPos b : line) check(h, !water(h, b), "intake gone: no water on " + b);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Any item, delivery targets
    // ------------------------------------------------------------------------------------------------

    /**
     * Belts carry any item. Sell Pad takes sellable items (chunk, uncut gem, ingot) - a stick waits; a Crucible takes
     * meltables - a gem waits; a Crafting Forge takes ingots - a clump waits; a pan takes no chunk.
     */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void beltsCarryAnyItemAndTargetsChoose(GameTestHelper h) {
        clear(h);
        // row 0: -> Sell Pad
        belt(h, new BlockPos(2, 1, 0), Direction.EAST);
        BlockPos pad = new BlockPos(3, 1, 0);
        h.setBlock(pad, ModBlocks.SELL_PAD.get());
        // row 2: -> Crucible
        belt(h, new BlockPos(2, 1, 2), Direction.EAST);
        BlockPos crucible = new BlockPos(3, 1, 2);
        h.setBlock(crucible, ModBlocks.CRUCIBLE.get());
        // row 4: -> Crafting Forge, and a separate belt -> pan
        belt(h, new BlockPos(2, 1, 4), Direction.EAST);
        BlockPos forge = new BlockPos(3, 1, 4);
        h.setBlock(forge, ModBlocks.CRAFTING_FORGE.get().defaultBlockState());
        belt(h, new BlockPos(10, 1, 2), Direction.EAST);
        BlockPos pan = new BlockPos(11, 1, 2);
        h.setBlock(pan, ModBlocks.PAN.get());

        ClumpBeltBlockEntity toPad = be(h, new BlockPos(2, 1, 0)), toCrucible = be(h, new BlockPos(2, 1, 2));
        ClumpBeltBlockEntity toForge = be(h, new BlockPos(2, 1, 4)), toPan = be(h, new BlockPos(10, 1, 2));
        toPad.insertByPlayer(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get()), 1);
        toPad.insertByPlayer(new ItemStack(ModItems.UNCUT_RUBY_SMALL.get()), 1);
        toPad.insertByPlayer(IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 50), 1);
        toCrucible.insertByPlayer(new ItemStack(ModItems.UNCUT_SAPPHIRE_SMALL.get()), 1);   // front: refused, waits
        toForge.insertByPlayer(IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), 10), 1);
        toPan.insertByPlayer(new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get()), 1);
        h.startSequence()
                .thenWaitUntil(() -> check(h, padTotal(h, pad) == 3, "3 items reached the Sell Pad: " + padTotal(h, pad)))
                .thenExecute(() -> {
                    check(h, padCount(h, pad, ModItems.UNCUT_RUBY_SMALL.get()) == 1 && padCount(h, pad, ModItems.FOLKIRON_CHUNK_LARGE.get()) == 1,
                            "chunk + uncut gem on the pad");
                    toPad.insertByPlayer(new ItemStack(Items.STICK), 1);
                    toCrucible.takeAll();
                    toCrucible.insertByPlayer(new ItemStack(ModItems.GOLDCHRO_CHUNK_MEDIUM.get()), 1);
                    toForge.insertByPlayer(DirtTier.clumps(1, 1), 1);
                })
                .thenExecuteAfter(40, () -> {
                    check(h, toPad.clumpCount() == 1 && toPad.positions()[0] == ClumpBeltBlockEntity.LAST && padTotal(h, pad) == 3,
                            "a stick isn't sellable: it waits at the end");
                    check(h, ((CrucibleBlockEntity) h.getBlockEntity(crucible)).total() == 3, "the crucible took the 3 g Goldchro chunk");
                    check(h, ((CraftingForgeBlockEntity) h.getBlockEntity(forge)).grams(HoleyDigsite.id("goldchro")) == 10,
                            "the Crafting Forge pooled the 10 g ingot");
                    check(h, toForge.clumpCount() == 1, "a clump isn't a Crafting Forge input: it waits");
                    check(h, toPan.clumpCount() == 1 && PanBlock.fill(h.getBlockState(pan)) == 0, "a chunk doesn't go into a pan");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Incline, corners
    // ------------------------------------------------------------------------------------------------

    /**
     * Belt (y=1) -> Inclined Belt (y=2, its low end over the belt) -> Inclined Belt (y=3, staircase) -> belt (y=3) -> pan:
     * a clump climbs from y=1 to the top of y=3. The inclines get their water diagonally from the first belt.
     */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void inclinesLiftItemsAndStack(GameTestHelper h) {
        clear(h);
        BlockPos a = new BlockPos(2, 1, 2);
        belt(h, a, Direction.EAST);
        h.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        BlockPos i1 = new BlockPos(3, 2, 2);
        belt(h, i1, ModBlocks.INCLINE_BELT.get(), Direction.EAST, false);
        for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(4, y, 2), Blocks.STONE);
        BlockPos i2 = new BlockPos(4, 3, 2);
        belt(h, i2, ModBlocks.INCLINE_BELT.get(), Direction.EAST, false);
        for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(5, y, 2), Blocks.STONE);
        BlockPos top = new BlockPos(5, 3, 2);   // the high end of i2 is the top of its block: the belt in front, same level
        belt(h, top, ModBlocks.CLUMP_BELT.get(), Direction.EAST, false);
        for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(6, y, 2), Blocks.STONE);
        BlockPos pan = new BlockPos(6, 3, 2);
        h.setBlock(pan, ModBlocks.PAN.get());
        be(h, a).insertByPlayer(1);
        h.startSequence()
                .thenExecuteAfter(2, () -> check(h, water(h, i1) && water(h, i2) && water(h, top), "water reaches the inclines diagonally"))
                .thenWaitUntil(() -> check(h, be(h, i1).clumpCount() == 1, "clump on the first incline"))
                .thenWaitUntil(() -> check(h, be(h, i2).clumpCount() == 1, "clump on the second incline"))
                .thenWaitUntil(() -> check(h, PanBlock.fill(h.getBlockState(pan)) == 1, "clump arrived in the pan two blocks up"))
                .thenExecute(() -> check(h, !be(h, i1).canAccept(h.getBlockState(i1), Direction.NORTH), "inclines take nothing from the side"))
                .thenSucceed();
    }

    /** A belt fed only from one side is a corner (left / right); a feeder behind makes it straight. Items turn and arrive. */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void cornersFollowTheFeed(GameTestHelper h) {
        clear(h);
        BlockPos c = new BlockPos(5, 1, 2), left = new BlockPos(5, 1, 1), right = new BlockPos(5, 1, 3), back = new BlockPos(4, 1, 2);
        BlockPos pan = new BlockPos(6, 1, 2);
        h.setBlock(pan, ModBlocks.PAN.get());
        belt(h, c, Direction.EAST);
        check(h, ClumpBeltBlock.shape(h.getBlockState(c)) == BeltShape.STRAIGHT, "alone: straight");
        belt(h, left, Direction.SOUTH);     // north of an east belt = its left
        check(h, ClumpBeltBlock.shape(h.getBlockState(c)) == BeltShape.LEFT, "fed from the left: left corner");
        be(h, left).insertByPlayer(1);
        h.startSequence()
                .thenWaitUntil(() -> check(h, be(h, c).clumpCount() == 1, "turned onto the corner"))
                .thenExecute(() -> check(h, be(h, c).positions()[0] <= 2, "a corner takes side items at the start of the curve, at "
                        + be(h, c).positions()[0]))
                .thenWaitUntil(() -> check(h, PanBlock.fill(h.getBlockState(pan)) == 1, "arrived through the corner"))
                .thenExecute(() -> {
                    h.setBlock(left, Blocks.AIR);
                    belt(h, right, Direction.NORTH);
                    check(h, ClumpBeltBlock.shape(h.getBlockState(c)) == BeltShape.RIGHT, "fed from the right: right corner");
                    belt(h, back, Direction.EAST);
                    check(h, ClumpBeltBlock.shape(h.getBlockState(c)) == BeltShape.STRAIGHT, "a feeder behind: straight (side merges)");
                    be(h, right).insertByPlayer(1);
                })
                .thenWaitUntil(() -> check(h, be(h, c).clumpCount() == 1, "side merge onto the straight belt"))
                .thenExecute(() -> check(h, be(h, c).positions()[0] >= ClumpBeltBlockEntity.SIDE_ENTRY - 1, "straight belts take side items in the middle"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Filter Belt
    // ------------------------------------------------------------------------------------------------

    /**
     * Feeder -> Filter Belt (east, matches out to the right = south) -> straight belt / side belt. Filter by material
     * (a small Folkiron chunk sends ingots and other sizes sideways), by exact item (a Deep Dirt Clump only), the
     * uncut-only rule, the side toggle by clicking an edge, and a refused side waits.
     */
    @GameTest(template = "empty_long", timeoutTicks = 500)
    public static void filterBeltSortsAndWaits(GameTestHelper h) {
        clear(h);
        BlockPos feed = new BlockPos(2, 1, 2), f = new BlockPos(3, 1, 2), straight = new BlockPos(4, 1, 2), side = new BlockPos(3, 1, 3);
        belt(h, feed, Direction.EAST);
        belt(h, f, ModBlocks.FILTER_BELT.get(), Direction.EAST, true);
        h.setBlock(f, h.getBlockState(f).setValue(FilterBeltBlock.OUT_RIGHT, true));
        belt(h, straight, Direction.EAST);
        h.setBlock(new BlockPos(5, 1, 2), Blocks.STONE);
        belt(h, side, Direction.SOUTH);
        h.setBlock(new BlockPos(3, 1, 4), Blocks.STONE);
        ClumpBeltBlockEntity fb = be(h, f);
        // matching rules
        ItemStack folk = new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get());
        check(h, FilterMatch.matches(folk, IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 200)), "material: ingots match a chunk filter");
        check(h, !FilterMatch.matches(folk, new ItemStack(ModItems.GOLDCHRO_CHUNK_SMALL.get())), "other material doesn't match");
        check(h, FilterMatch.matches(new ItemStack(ModItems.RUBY_SMALL.get()), new ItemStack(ModItems.UNCUT_RUBY_LARGE.get())), "cut filter: uncut too");
        check(h, !FilterMatch.matches(new ItemStack(ModItems.UNCUT_RUBY_SMALL.get()), new ItemStack(ModItems.RUBY_LARGE.get())), "uncut filter: uncut only");
        check(h, FilterMatch.matches(DirtTier.clumps(2, 1), DirtTier.clumps(2, 1)) && !FilterMatch.matches(DirtTier.clumps(2, 1), DirtTier.clumps(1, 1)),
                "clumps: exact kind");
        // edges: clickedEdge on the top
        BlockPos abs = h.absolutePos(f);
        BlockState fs = h.getBlockState(f);
        check(h, Boolean.FALSE.equals(FilterBeltBlock.clickedEdge(fs, abs, new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, -0.4), Direction.UP, abs, false))),
                "north edge of an east belt = left");
        check(h, FilterBeltBlock.clickedEdge(fs, abs, new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false)) == null, "middle = take items");
        // player sets the filter
        ServerPlayer p = player(h);
        click(h, p, f, folk.copy(), new Vec3(0, 0.5, 0), Direction.UP);
        check(h, fb.filter().is(ModItems.FOLKIRON_CHUNK_SMALL.get()) && p.getMainHandItem().getCount() == 1, "filter set, item kept");
        be(h, feed).insertByPlayer(IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 50), 1);
        be(h, feed).insertByPlayer(new ItemStack(ModItems.GOLDCHRO_CHUNK_SMALL.get()), 1);
        be(h, feed).insertByPlayer(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get()), 1);
        h.startSequence()
                .thenWaitUntil(() -> check(h, be(h, side).clumpCount() + be(h, straight).clumpCount() == 3, "all 3 sorted"))
                .thenExecuteAfter(30, () -> {
                    check(h, be(h, side).items().stream().allMatch(s -> Selling.resource(s).material().equals(HoleyDigsite.id("folkiron"))) && be(h, side).clumpCount() == 2,
                            "Folkiron ingot + chunk went to the side: " + be(h, side).items());
                    check(h, be(h, straight).items().stream().allMatch(s -> s.is(ModItems.GOLDCHRO_CHUNK_SMALL.get())), "Goldchro went straight");
                    be(h, side).takeAll();
                    be(h, straight).takeAll();
                    fb.setFilter(DirtTier.clumps(2, 1));
                    be(h, feed).insertByPlayer(1, 1);
                    be(h, feed).insertByPlayer(1, 2);
                })
                .thenWaitUntil(() -> check(h, be(h, side).clumpCount() + be(h, straight).clumpCount() == 2, "both clumps sorted"))
                .thenExecuteAfter(25, () -> {
                    check(h, be(h, side).items().get(0).is(ModItems.DIRT_CLUMP.get()) && DirtTier.ofClump(be(h, side).items().get(0)) == 2,
                            "Deep clump to the side");
                    check(h, DirtTier.ofClump(be(h, straight).items().get(0)) == 1, "normal clump straight");
                    // side refuses (stone instead of the side belt): a match waits at the middle, the belt backs up behind it
                    h.setBlock(side, Blocks.STONE);
                    be(h, feed).insertByPlayer(1, 2);
                })
                .thenExecuteAfter(40, () -> {
                    check(h, fb.clumpCount() == 1 && fb.positions()[0] == ClumpBeltBlockEntity.MID, "match waits at the middle: " + java.util.Arrays.toString(fb.positions()));
                    be(h, feed).insertByPlayer(1, 1);
                })
                .thenExecuteAfter(40, () -> {
                    check(h, fb.clumpCount() == 2 && fb.positions()[1] <= ClumpBeltBlockEntity.MID - ClumpBeltBlockEntity.SPACING,
                            "the non-match queues behind it: " + java.util.Arrays.toString(fb.positions()));
                    // toggle to the left by clicking the north edge
                    click(h, p, f, ItemStack.EMPTY, new Vec3(0, 0.5, -0.4), Direction.UP);
                    check(h, !h.getBlockState(f).getValue(FilterBeltBlock.OUT_RIGHT), "clicked the left edge: matches go left");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Sluice tray -> belt
    // ------------------------------------------------------------------------------------------------

    /** A belt in front of a sluice's tray takes the tray's finds one by one and carries them to a Sell Pad. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void sluiceTrayEmptiesOntoABelt(GameTestHelper h) {
        clear(h);
        BlockPos sluice = new BlockPos(2, 1, 2), b = new BlockPos(3, 1, 2), pad = new BlockPos(4, 1, 2);
        h.setBlock(sluice, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        h.setBlock(pad, ModBlocks.SELL_PAD.get());
        SluiceBoxBlockEntity s = (SluiceBoxBlockEntity) h.getBlockEntity(sluice);
        s.addToTray(new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 3));
        s.addToTray(new ItemStack(ModItems.UNCUT_RUBY_SMALL.get(), 1));
        belt(h, b, Direction.EAST); // placing it next to the tray starts the transfer
        h.startSequence()
                .thenWaitUntil(() -> check(h, s.tray().isEmpty(), "tray emptied onto the belt"))
                .thenWaitUntil(() -> check(h, padTotal(h, pad) == 4, "all 4 finds on the Sell Pad: " + padTotal(h, pad)))
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // 2-tall drills
    // ------------------------------------------------------------------------------------------------

    /**
     * Drills are 2 tall: placing the lower half adds the upper (same facing, status mirrored); a player can't place one
     * under a block; breaking the upper half removes both and drops ONE drill; output still goes from the lower half into a
     * pan or onto a belt.
     */
    @GameTest(template = "empty_long", timeoutTicks = 500)
    public static void drillsAreTwoTall(GameTestHelper h) {
        clear(h);
        // row 2: intake -> drill (east) -> belt -> pan
        h.setBlock(new BlockPos(1, 1, 2), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(new BlockPos(2, 0, 2), ModBlocks.DIRTROCK.get());
        BlockPos d = new BlockPos(2, 1, 2);
        h.setBlock(d, ModBlocks.MINING_DRILL_T3.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        BlockState up = h.getBlockState(d.above());
        check(h, up.is(ModBlocks.MINING_DRILL_T3.get()) && !MiningDrillBlock.isLower(up) && up.getValue(MiningDrillBlock.FACING) == Direction.EAST,
                "the upper half is there, facing the same way");
        check(h, h.getLevel().getBlockEntity(h.absolutePos(d.above())) == null, "only the lower half has a block entity");
        belt(h, new BlockPos(3, 1, 2), Direction.EAST);
        BlockPos pan = new BlockPos(4, 1, 2);
        h.setBlock(pan, ModBlocks.PAN.get());
        // row 4: a drill with something right above can't be placed by a player
        ServerPlayer p = player(h);
        h.setBlock(new BlockPos(6, 2, 4), Blocks.STONE);
        click(h, p, new BlockPos(6, 0, 4), new ItemStack(ModItems.MINING_DRILL.get()), new Vec3(0, 0.5, 0), Direction.UP);
        check(h, h.getBlockState(new BlockPos(6, 1, 4)).isAir(), "no room for the upper half: not placed");
        click(h, p, new BlockPos(9, 0, 4), new ItemStack(ModItems.MINING_DRILL.get()), new Vec3(0, 0.5, 0), Direction.UP);
        check(h, h.getBlockState(new BlockPos(9, 1, 4)).is(ModBlocks.MINING_DRILL.get()) && h.getBlockState(new BlockPos(9, 2, 4)).is(ModBlocks.MINING_DRILL.get()),
                "placed by a player: both halves");
        h.startSequence()
                .thenExecuteAfter(5, () -> check(h, h.getBlockState(d.above()).getValue(MiningDrillBlock.STATUS) == DrillStatus.WORKING,
                        "the upper half shows the lower half's status"))
                .thenWaitUntil(() -> check(h, PanBlock.fill(h.getBlockState(pan)) >= 1, "drill -> belt -> pan"))
                .thenExecute(() -> {
                    ServerPlayer breaker = player(h);
                    breaker.gameMode.destroyBlock(h.absolutePos(new BlockPos(9, 2, 4)));
                })
                .thenExecuteAfter(2, () -> {
                    check(h, h.getBlockState(new BlockPos(9, 1, 4)).isAir() && h.getBlockState(new BlockPos(9, 2, 4)).isAir(), "both halves gone");
                    check(h, itemEntities(h, ModItems.MINING_DRILL.get()) == 1, "exactly one drill dropped: " + itemEntities(h, ModItems.MINING_DRILL.get()));
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Uncut gems + Gem Cutter
    // ------------------------------------------------------------------------------------------------

    /** Uncut gems: same grams, half the price per gram, cut item for each one, the tooltip form, rare-find tag. */
    @GameTest(template = "empty_long")
    public static void uncutGemsSellAtHalfPrice(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        Item[][] pairs = {
                {ModItems.UNCUT_RUBY_SMALL.get(), ModItems.RUBY_SMALL.get()}, {ModItems.UNCUT_RUBY_MEDIUM.get(), ModItems.RUBY_MEDIUM.get()},
                {ModItems.UNCUT_RUBY_LARGE.get(), ModItems.RUBY_LARGE.get()}, {ModItems.UNCUT_SAPPHIRE_SMALL.get(), ModItems.SAPPHIRE_SMALL.get()},
                {ModItems.UNCUT_SAPPHIRE_MEDIUM.get(), ModItems.SAPPHIRE_MEDIUM.get()}, {ModItems.UNCUT_SAPPHIRE_LARGE.get(), ModItems.SAPPHIRE_LARGE.get()},
                {ModItems.UNCUT_TOPAZ_SMALL.get(), ModItems.TOPAZ_SMALL.get()}, {ModItems.UNCUT_TOPAZ_MEDIUM.get(), ModItems.TOPAZ_MEDIUM.get()},
                {ModItems.UNCUT_TOPAZ_LARGE.get(), ModItems.TOPAZ_LARGE.get()}};
        for (Item[] pr : pairs) {
            ItemStack u = new ItemStack(pr[0]), c = new ItemStack(pr[1]);
            check(h, GemCutting.isUncut(u) && !GemCutting.isUncut(c) && GemCutting.cutItem(u) == pr[1], "cut of " + u);
            check(h, Selling.grams(u) == Selling.grams(c), "same grams " + u);
            long vu = Selling.unitValue(u, reg), vc = Selling.unitValue(c, reg);
            check(h, vu * 2 == vc, u + " sells for half: " + vu + " vs " + vc);
            check(h, u.is(com.holeydigsite.panning.Panning.RARE_FINDS), "rare find " + u);
            check(h, com.holeydigsite.forge.Metals.meltable(u) == null, "not meltable " + u);
        }
        check(h, Selling.unitValue(new ItemStack(ModItems.UNCUT_RUBY_LARGE.get()), reg) == 280, "large uncut ruby 280 copper");
        h.succeed();
    }

    /**
     * Belt -> Gem Cutter -> belt -> Sell Pad: all 9 uncut gems arrive cut (same size), other items pass unchanged; a gem
     * takes about 2 s under the wheel. A cutter without water does nothing. Players can put items on it.
     */
    @GameTest(template = "empty_long", timeoutTicks = 1200)
    public static void gemCutterCutsEveryGem(GameTestHelper h) {
        clear(h);
        BlockPos feed = new BlockPos(2, 1, 2), cutter = new BlockPos(3, 1, 2), out = new BlockPos(4, 1, 2), pad = new BlockPos(5, 1, 2);
        belt(h, feed, Direction.EAST);
        belt(h, cutter, ModBlocks.GEM_CUTTER.get(), Direction.EAST, true);
        belt(h, out, Direction.EAST);
        h.setBlock(pad, ModBlocks.SELL_PAD.get());
        // a dry cutter elsewhere
        BlockPos dry = new BlockPos(10, 1, 4);
        belt(h, dry, ModBlocks.GEM_CUTTER.get(), Direction.EAST, false);
        ServerPlayer p = player(h);
        click(h, p, dry, new ItemStack(ModItems.UNCUT_TOPAZ_SMALL.get(), 2), new Vec3(0, 0.5, 0), Direction.UP);
        check(h, be(h, dry).clumpCount() == 1 && p.getMainHandItem().getCount() == 1, "player put an uncut gem on the cutter");
        check(h, !be(h, cutter).canAccept(h.getBlockState(cutter), Direction.NORTH), "cutters take items from the back only");
        List<ItemStack> queue = new java.util.ArrayList<>(List.of(
                new ItemStack(ModItems.UNCUT_RUBY_SMALL.get()), new ItemStack(ModItems.UNCUT_RUBY_MEDIUM.get()), new ItemStack(ModItems.UNCUT_RUBY_LARGE.get()),
                new ItemStack(ModItems.UNCUT_SAPPHIRE_SMALL.get()), new ItemStack(ModItems.UNCUT_SAPPHIRE_MEDIUM.get()), new ItemStack(ModItems.UNCUT_SAPPHIRE_LARGE.get()),
                new ItemStack(ModItems.UNCUT_TOPAZ_SMALL.get()), new ItemStack(ModItems.UNCUT_TOPAZ_MEDIUM.get()), new ItemStack(ModItems.UNCUT_TOPAZ_LARGE.get()),
                new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get())));
        long[] firstGemDone = {-1};
        long start = h.getTick();
        h.onEachTick(() -> {
            if (!queue.isEmpty() && be(h, feed).insertByPlayer(queue.get(0), 1) == 1) {
                queue.remove(0);
            }
            if (firstGemDone[0] < 0 && be(h, cutter).items().stream().anyMatch(s -> s.is(ModItems.RUBY_SMALL.get()))) {
                firstGemDone[0] = h.getTick() - start;
            }
        });
        h.startSequence()
                .thenWaitUntil(() -> check(h, padTotal(h, pad) == 9 && be(h, out).clumpCount() == 1,
                        "9 items fill the pad's 9 slots, the 10th waits on the belt: " + padTotal(h, pad)))
                .thenExecute(() -> {
                    for (Item it : new Item[]{ModItems.RUBY_SMALL.get(), ModItems.RUBY_MEDIUM.get(), ModItems.RUBY_LARGE.get(), ModItems.SAPPHIRE_SMALL.get(),
                            ModItems.SAPPHIRE_MEDIUM.get(), ModItems.SAPPHIRE_LARGE.get(), ModItems.TOPAZ_SMALL.get(), ModItems.TOPAZ_MEDIUM.get(), ModItems.TOPAZ_LARGE.get(),
                            ModItems.FOLKIRON_CHUNK_MEDIUM.get()}) {
                        check(h, padCount(h, pad, it) + (int) be(h, out).items().stream().filter(x -> x.is(it)).count() == 1, "1 " + it);
                    }
                    check(h, firstGemDone[0] >= ClumpBeltBlockEntity.CUT_TICKS + 20, "cutting takes ~2 s: first gem cut after " + firstGemDone[0]);
                    check(h, be(h, dry).clumpCount() == 1 && GemCutting.isUncut(be(h, dry).items().get(0)) && be(h, dry).positions()[0] == 0,
                            "no water: the dry cutter did nothing");
                })
                .thenSucceed();
    }

    /** No item handlers on any Phase 12 belt block (hoppers / chests / other mods can't use them). */
    @GameTest(template = "empty_long")
    public static void newBeltBlocksHaveNoItemHandler(GameTestHelper h) {
        clear(h);
        Block[] blocks = {ModBlocks.INCLINE_BELT.get(), ModBlocks.FAST_INCLINE_BELT.get(), ModBlocks.FILTER_BELT.get(), ModBlocks.GEM_CUTTER.get()};
        for (int i = 0; i < blocks.length; i++) {
            BlockPos b = new BlockPos(2 + 2 * i, 1, 2);
            h.setBlock(b, blocks[i]);
            for (Direction d : Direction.values()) {
                check(h, h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(b), d) == null, "no item handler: " + blocks[i]);
            }
        }
        h.succeed();
    }
}
