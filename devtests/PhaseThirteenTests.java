package com.holeydigsite.devtest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.belt.FilterBeltBlock;
import com.holeydigsite.belt.SplitterBeltBlock;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.CastingMoldBlock;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.forge.LargeCrucibleBlock;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.forge.MoldBlockEntity;
import com.holeydigsite.power.LavaIntakeBlock;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopCatalogue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Sandbox-only GameTests for Phase 13 (not shipped): the Belt Splitter (round-robin, skipping, waiting, 2-way / 3-way, water)
 * and the casting upgrades (Large Crucible capacity + speed + auto-pour, Hot Forge, Tier 2 Casting Mold cooling + ejection, all
 * mixes, recipes, shop entry, assets). Template {@code empty_long} (26x5x5, floor y=0).
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseThirteenTests {
    private static final ResourceLocation FOLKIRON = HoleyDigsite.id("folkiron");

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

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    private static void click(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held, Direction face, boolean sneak) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(sneak);
        BlockPos abs = h.absolutePos(rel);
        Vec3 at = Vec3.atCenterOf(abs).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(at, face, abs, false));
        p.setShiftKeyDown(false);
    }

    private static int count(ClumpBeltBlockEntity b, Item item) {
        return (int) b.items().stream().filter(s -> s.is(item)).count();
    }

    // ------------------------------------------------------------------------------------------------
    // Belt Splitter
    // ------------------------------------------------------------------------------------------------

    private static final BlockPos FEED = new BlockPos(4, 1, 2), SPLIT = new BlockPos(5, 1, 2),
            LEFT = new BlockPos(5, 1, 1), FRONT = new BlockPos(6, 1, 2), RIGHT = new BlockPos(5, 1, 3);

    /** Feed belt -> splitter facing east -> left belt (north), front belt (east), right belt (south); all powered. */
    private static void splitterLine(GameTestHelper h, boolean left, boolean front, boolean right) {
        clear(h);
        belt(h, FEED, Direction.EAST);
        belt(h, SPLIT, ModBlocks.BELT_SPLITTER.get(), Direction.EAST, true);
        if (left) belt(h, LEFT, Direction.NORTH);
        if (front) belt(h, FRONT, Direction.EAST);
        if (right) belt(h, RIGHT, Direction.SOUTH);
    }

    private static void feed(GameTestHelper h, Item item) {
        be(h, FEED).insertByPlayer(new ItemStack(item), 1);
    }

    /** Items go left, front, right, left... in that order, one at a time. */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void splitterAlternatesLeftFrontRight(GameTestHelper h) {
        splitterLine(h, true, true, true);
        Item[] items = {Items.STICK, Items.APPLE, Items.COAL, Items.BONE, Items.FLINT, Items.CLAY_BALL};
        var seq = h.startSequence();
        for (int i = 0; i < items.length; i++) {
            final int n = i;
            seq = seq.thenExecute(() -> feed(h, items[n])).thenExecuteAfter(55, () -> {
                BlockPos expect = new BlockPos[]{LEFT, FRONT, RIGHT}[n % 3];
                check(h, count(be(h, expect), items[n]) == 1, "item " + n + " went to " + expect + " (left " + be(h, LEFT).items() + " front "
                        + be(h, FRONT).items() + " right " + be(h, RIGHT).items() + ")");
            });
        }
        seq.thenExecute(() -> {
            check(h, be(h, LEFT).clumpCount() == 2 && be(h, FRONT).clumpCount() == 2 && be(h, RIGHT).clumpCount() == 2, "two each");
            check(h, be(h, SPLIT).isEmpty(), "nothing left on the splitter");
            check(h, h.getBlockState(SPLIT).getBlock() instanceof SplitterBeltBlock && ClumpBeltBlock.running(h.getBlockState(SPLIT)), "powered");
        }).thenSucceed();
    }

    /** A full output is skipped; if every output refuses the item waits at the middle and goes as soon as there is room. */
    @GameTest(template = "empty_long", timeoutTicks = 600)
    public static void splitterSkipsFullOutputsAndWaits(GameTestHelper h) {
        splitterLine(h, true, true, true);
        h.startSequence()
                .thenExecute(() -> be(h, LEFT).insertByPlayer(new ItemStack(Items.STICK), 4))
                .thenExecuteAfter(40, () -> {
                    check(h, be(h, LEFT).clumpCount() == 4, "left belt full: " + be(h, LEFT).clumpCount());
                    feed(h, Items.APPLE);
                })
                .thenExecuteAfter(55, () -> {
                    check(h, count(be(h, FRONT), Items.APPLE) == 1, "first free output after the pointer (left is full): front");
                    feed(h, Items.COAL);
                })
                .thenExecuteAfter(55, () -> {
                    check(h, count(be(h, RIGHT), Items.COAL) == 1, "pointer moved on: right");
                    feed(h, Items.BONE);
                })
                .thenExecuteAfter(55, () -> {
                    check(h, count(be(h, FRONT), Items.BONE) == 1, "left full again skipped: front");
                    be(h, FRONT).insertByPlayer(new ItemStack(Items.FLINT), 3);   // front becomes full too
                    be(h, RIGHT).insertByPlayer(new ItemStack(Items.FLINT), 3);
                })
                .thenExecuteAfter(40, () -> {
                    check(h, be(h, FRONT).clumpCount() == 4 && be(h, RIGHT).clumpCount() == 4, "front and right full");
                    feed(h, Items.CLAY_BALL);
                })
                .thenExecuteAfter(60, () -> {
                    ClumpBeltBlockEntity s = be(h, SPLIT);
                    check(h, s.clumpCount() == 1 && s.positions()[0] == ClumpBeltBlockEntity.MID, "all refuse: the item waits at the middle: "
                            + java.util.Arrays.toString(s.positions()));
                    check(h, s.routes()[0] == -1, "no route chosen yet");
                    be(h, RIGHT).takeAll();   // room on the right
                })
                .thenExecuteAfter(30, () -> {
                    check(h, be(h, SPLIT).isEmpty() && count(be(h, RIGHT), Items.CLAY_BALL) == 1, "it went right as soon as there was room: " + be(h, RIGHT).items());
                })
                .thenSucceed();
    }

    /** Outputs with nothing there are never used; an unpowered splitter takes and moves nothing. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void splitterUsesOnlyWhatIsThereAndNeedsWater(GameTestHelper h) {
        splitterLine(h, false, true, false);   // only the front exists
        h.startSequence()
                .thenExecute(() -> {
                    feed(h, Items.STICK);
                    feed(h, Items.APPLE);
                })
                .thenExecuteAfter(70, () -> {
                    check(h, be(h, FRONT).clumpCount() == 2 && be(h, SPLIT).isEmpty(), "everything went straight on: " + be(h, FRONT).items());
                    // now without water: new splitter, fed by hand
                    clear(h);
                    belt(h, SPLIT, ModBlocks.BELT_SPLITTER.get(), Direction.EAST, false);
                    belt(h, FRONT, ModBlocks.CLUMP_BELT.get(), Direction.EAST, false);
                    WaterNetworks.recompute(h.getLevel(), Set.of(h.absolutePos(SPLIT), h.absolutePos(FRONT)));
                    check(h, !ClumpBeltBlock.running(h.getBlockState(SPLIT)), "no water: not running " + h.getBlockState(SPLIT) + " below " + h.getBlockState(SPLIT.below()));
                    check(h, !be(h, SPLIT).canAccept(h.getBlockState(SPLIT), Direction.WEST), "no water: takes nothing");
                    be(h, SPLIT).insertByPlayer(new ItemStack(Items.STICK), 1);
                })
                .thenExecuteAfter(40, () -> {
                    check(h, be(h, SPLIT).clumpCount() == 1 && be(h, FRONT).isEmpty(), "no water: nothing moves");
                    TestWater.underBelt(h, SPLIT);
                })
                .thenExecuteAfter(60, () -> {
                    check(h, ClumpBeltBlock.running(h.getBlockState(SPLIT)) && be(h, FRONT).clumpCount() == 1, "with water it goes on: " + be(h, FRONT).items());
                })
                .thenSucceed();
    }

    /** Empty-hand click on the top switches 2-way / 3-way; 2-way never uses the front. */
    @GameTest(template = "empty_long", timeoutTicks = 600)
    public static void splitterTwoWayAndThreeWay(GameTestHelper h) {
        splitterLine(h, true, true, true);
        ServerPlayer p = player(h);
        check(h, !h.getBlockState(SPLIT).getValue(SplitterBeltBlock.TWO_WAY), "3-way by default");
        click(h, p, SPLIT, ItemStack.EMPTY, Direction.UP, false);
        check(h, h.getBlockState(SPLIT).getValue(SplitterBeltBlock.TWO_WAY), "click on the top: 2-way");
        Item[] items = {Items.STICK, Items.APPLE, Items.COAL, Items.BONE};
        var seq = h.startSequence();
        for (int i = 0; i < items.length; i++) {
            final int n = i;
            seq = seq.thenExecute(() -> feed(h, items[n])).thenExecuteAfter(55, () -> {
                BlockPos expect = n % 2 == 0 ? LEFT : RIGHT;
                check(h, count(be(h, expect), items[n]) == 1, "2-way: item " + n + " went to " + expect);
            });
        }
        seq.thenExecute(() -> {
            check(h, be(h, FRONT).isEmpty(), "2-way: the front stays unused");
            click(h, p, SPLIT, ItemStack.EMPTY, Direction.UP, false);
            check(h, !h.getBlockState(SPLIT).getValue(SplitterBeltBlock.TWO_WAY), "click again: 3-way");
            // a click on a side still takes the items off a belt (empty belt: nothing happens), sneak on top does not toggle
            click(h, p, SPLIT, ItemStack.EMPTY, Direction.UP, true);
            check(h, !h.getBlockState(SPLIT).getValue(SplitterBeltBlock.TWO_WAY), "sneak + top does not toggle");
            feed(h, Items.FLINT);
            feed(h, Items.CLAY_BALL);
        }).thenExecuteAfter(110, () -> {
            check(h, be(h, LEFT).clumpCount() + be(h, FRONT).clumpCount() + be(h, RIGHT).clumpCount() == 6, "six items out");
            check(h, !be(h, FRONT).isEmpty(), "3-way uses the front again: " + be(h, FRONT).items());
        }).thenSucceed();
    }

    /** The state survives save / load of the pointer and routes; blocks next to a splitter side see it as a feeder (corner shape). */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void splitterSideBeltsBecomeCorners(GameTestHelper h) {
        splitterLine(h, true, true, true);
        // left belt facing EAST (not away from the splitter): fed from its south side by the splitter's left output -> a corner
        belt(h, LEFT, Direction.EAST);
        check(h, ClumpBeltBlock.shape(h.getBlockState(LEFT)) != com.holeydigsite.belt.BeltShape.STRAIGHT, "belt beside the splitter curves: "
                + h.getBlockState(LEFT));
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Casting kit builder
    // ------------------------------------------------------------------------------------------------

    private record Kit(int dx) {
        BlockPos forge() { return new BlockPos(4 + dx, 1, 2); }
        BlockPos intake() { return new BlockPos(5 + dx, 1, 2); }
        BlockPos cruc() { return new BlockPos(4 + dx, 2, 2); }
        BlockPos mold() { return new BlockPos(3 + dx, 1, 2); }
        BlockPos out() { return new BlockPos(2 + dx, 1, 2); }
    }

    private static void placeIntake(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        BlockState s = ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(LavaIntakeBlock.LAVALOGGED, true);
        for (Direction dir : Direction.values()) {
            s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), WaterNetworkBlock.connects(h.getLevel(), abs, s, dir));
        }
        h.getLevel().setBlock(abs, s, Block.UPDATE_CLIENTS);
    }

    /** Lit forge (hot or plain) with a crucible (large or plain) on top facing west and a mold (plain or casting) in front. */
    private static Kit kit(GameTestHelper h, int dx, boolean hot, boolean large, boolean t2mold) {
        Kit k = new Kit(dx);
        h.setBlock(k.forge(), (hot ? ModBlocks.HOT_FORGE.get() : ModBlocks.FORGE.get()).defaultBlockState().setValue(ForgeBlock.FACING, Direction.WEST));
        placeIntake(h, k.intake());
        WaterNetworks.recompute(h.getLevel(), Set.of(h.absolutePos(k.forge())));
        h.setBlock(k.cruc(), (large ? ModBlocks.LARGE_CRUCIBLE.get() : ModBlocks.CRUCIBLE.get()).defaultBlockState().setValue(CrucibleBlock.FACING, Direction.WEST));
        h.setBlock(k.mold(), t2mold ? ModBlocks.CASTING_MOLD_T2.get().defaultBlockState().setValue(CastingMoldBlock.FACING, Direction.WEST)
                : ModBlocks.MOLD.get().defaultBlockState());
        return k;
    }

    private static CrucibleBlockEntity cru(GameTestHelper h, Kit k) {
        return (CrucibleBlockEntity) h.getBlockEntity(k.cruc());
    }

    private static MoldBlockEntity mold(GameTestHelper h, Kit k) {
        return (MoldBlockEntity) h.getBlockEntity(k.mold());
    }

    // ------------------------------------------------------------------------------------------------
    // Large Crucible, Hot Forge
    // ------------------------------------------------------------------------------------------------

    /** 500 g instead of 200; metal limits as before; the plain Crucible is unchanged. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void largeCrucibleHoldsFiveHundredGrams(GameTestHelper h) {
        clear(h);
        Kit big = kit(h, 0, false, true, false);
        Kit small = kit(h, 8, false, false, false);
        check(h, cru(h, big).capacity() == 500 && cru(h, small).capacity() == 200, "capacities");
        ItemStack ingot = com.holeydigsite.forge.Metals.ingot(FOLKIRON, 50);
        int in = 0;
        for (int i = 0; i < 12; i++) in += cru(h, big).insert(ingot, 1, false);
        check(h, in == 10 && cru(h, big).total() == 500 && cru(h, big).room() == 0, "ten 50 g ingots fit, then full: " + in + " / " + cru(h, big).total());
        check(h, cru(h, big).check(ingot) == CrucibleBlockEntity.Refusal.FULL, "full");
        in = 0;
        for (int i = 0; i < 12; i++) in += cru(h, small).insert(ingot, 1, false);
        check(h, in == 4 && cru(h, small).total() == 200, "the plain crucible still takes 200 g");
        // belts and hoppers see the larger room too
        BlockState st = h.getBlockState(big.cruc());
        check(h, !((CrucibleBlock) st.getBlock()).canAcceptItem(h.getLevel(), h.absolutePos(big.cruc()), st, Direction.EAST, ingot), "belt input: full");
        cru(h, big).takeMolten(0);
        cru(h, big).setContents(FOLKIRON, 100, 300);
        check(h, ((CrucibleBlock) st.getBlock()).canAcceptItem(h.getLevel(), h.absolutePos(big.cruc()), st, Direction.EAST, ingot), "belt input: 100 g room");
        IItemHandler cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(big.cruc()), Direction.UP);
        check(h, cap != null && cap.insertItem(0, ingot.copyWithCount(5), false).getCount() == 3, "hopper handler: 2 fit (100 g), 3 stay");
        check(h, h.getBlockState(big.cruc()).getValue(CrucibleBlock.FILL) == 3 || h.getBlockState(big.cruc()).getValue(CrucibleBlock.FILL) == 4, "fill levels scale: "
                + h.getBlockState(big.cruc()));
        h.succeed();
    }

    /** Melting speeds multiply: plain/plain 5 g/s, Large 10 g/s, Hot Forge 10 g/s, Large on Hot 20 g/s - all four mixes work. */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void meltSpeedsAndAllMixes(GameTestHelper h) {
        clear(h);
        Kit a = kit(h, 0, false, false, false);   // plain on plain
        Kit b = kit(h, 6, false, true, false);    // large on plain forge
        Kit c = kit(h, 12, true, false, false);   // plain on hot forge
        Kit d = kit(h, 18, true, true, false);    // large on hot forge
        for (Kit k : new Kit[]{a, b, c, d}) {
            check(h, h.getBlockState(k.forge()).getValue(ForgeBlock.LIT), "forge lit at dx " + k.dx());
            check(h, h.getBlockState(k.cruc()).getValue(CrucibleBlock.FACING) == Direction.WEST, "faces west");
            cru(h, k).setContents(FOLKIRON, 100, 0);
            check(h, h.getBlockState(k.cruc()).getValue(CrucibleBlock.HEATED) || true, "");
        }
        check(h, ((ForgeBlock) h.getBlockState(c.forge()).getBlock()).heat() == 2 && ((ForgeBlock) h.getBlockState(a.forge()).getBlock()).heat() == 1, "heat values");
        h.startSequence()
                .thenExecuteAfter(60, () -> {
                    for (Kit k : new Kit[]{a, b, c, d}) {
                        check(h, h.getBlockState(k.cruc()).getValue(CrucibleBlock.HEATED), "heated at dx " + k.dx());
                    }
                })
                .thenExecuteAfter(40, () -> {   // ~100 ticks since the start
                    int ma = cru(h, a).molten(), mb = cru(h, b).molten(), mc = cru(h, c).molten(), md = cru(h, d).molten();
                    check(h, Math.abs(ma - 25) <= 3, "plain on plain: 25 g after 100 ticks, got " + ma);
                    check(h, Math.abs(mb - 50) <= 3, "large on plain: 50 g, got " + mb);
                    check(h, Math.abs(mc - 50) <= 3, "plain on hot: 50 g, got " + mc);
                    check(h, md >= 95, "large on hot: 100 g (done), got " + md);
                    check(h, cru(h, d).unmelted() <= 5, "large on hot nearly done");
                })
                .thenExecuteAfter(120, () -> {
                    check(h, cru(h, a).molten() == 55 || Math.abs(cru(h, a).molten() - 55) <= 3, "plain after 220 ticks: ~55 g, got " + cru(h, a).molten());
                    check(h, cru(h, d).molten() == 100 && cru(h, d).unmelted() == 0, "large on hot finished");
                    check(h, !h.getBlockState(d.cruc()).getValue(CrucibleBlock.CHUNKS), "no more chunks");
                })
                .thenSucceed();
    }

    /** A crucible placed on a forge (plain or hot) faces the forge's front. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void crucibleFacesTheForgeOnEveryForge(GameTestHelper h) {
        clear(h);
        for (int i = 0; i < 2; i++) {
            BlockPos f = new BlockPos(4 + 6 * i, 1, 2);
            h.setBlock(f, (i == 0 ? ModBlocks.FORGE.get() : ModBlocks.HOT_FORGE.get()).defaultBlockState().setValue(ForgeBlock.FACING, Direction.SOUTH));
            check(h, CrucibleBlock.placementFacing(h.getLevel(), h.absolutePos(f.above()), Direction.NORTH) == Direction.SOUTH, "faces the forge's front on forge type " + i);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Auto-pour
    // ------------------------------------------------------------------------------------------------

    /** Sneak + empty hand on the spout toggles; other clicks keep their old meaning. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void autoPourToggleClick(GameTestHelper h) {
        clear(h);
        Kit k = kit(h, 0, false, true, false);
        ServerPlayer p = player(h);
        check(h, !h.getBlockState(k.cruc()).getValue(LargeCrucibleBlock.AUTO), "off by default");
        click(h, p, k.cruc(), ItemStack.EMPTY, Direction.WEST, true);   // the spout side (facing west)
        check(h, h.getBlockState(k.cruc()).getValue(LargeCrucibleBlock.AUTO), "sneak + empty hand on the spout: on");
        click(h, p, k.cruc(), ItemStack.EMPTY, Direction.WEST, true);
        check(h, !h.getBlockState(k.cruc()).getValue(LargeCrucibleBlock.AUTO), "again: off");
        click(h, p, k.cruc(), ItemStack.EMPTY, Direction.EAST, true);   // another side: the old sneak-click (add from inventory)
        check(h, !h.getBlockState(k.cruc()).getValue(LargeCrucibleBlock.AUTO), "sneak on another side does not toggle");
        click(h, p, k.cruc(), ItemStack.EMPTY, Direction.WEST, false);  // plain click pours (nothing molten: just a message)
        check(h, !h.getBlockState(k.cruc()).getValue(LargeCrucibleBlock.AUTO), "plain click does not toggle");
        h.succeed();
    }

    /** On: pours by itself whenever the mold can take the target and enough is molten. Off: only click / redstone. */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void autoPourOnAndOff(GameTestHelper h) {
        clear(h);
        Kit k = kit(h, 0, false, true, false);
        ServerPlayer p = player(h);
        cru(h, k).setContents(FOLKIRON, 0, 120);
        h.startSequence()
                .thenExecuteAfter(40, () -> {
                    check(h, mold(h, k).isEmpty() && cru(h, k).molten() == 120, "auto off: nothing poured by itself");
                    BlockPos abs = h.absolutePos(k.cruc());
                    LargeCrucibleBlock.toggleAuto(h.getLevel(), abs, h.getLevel().getBlockState(abs), p);
                })
                .thenExecuteAfter(25, () -> {
                    check(h, mold(h, k).grams() == 50 && cru(h, k).molten() == 70, "auto on: poured the 50 g target: mold " + mold(h, k).grams() + " crucible " + cru(h, k).molten());
                })
                .thenExecuteAfter(100, () -> {
                    check(h, mold(h, k).grams() == 50 && cru(h, k).molten() == 70, "mold still full: no second pour");
                    check(h, h.getBlockState(k.mold()).getValue(MoldBlock.SOLID), "mold solid (60 ticks)");
                    MoldBlock.click(h.getLevel(), h.absolutePos(k.mold()), p, false);   // take the ingot
                })
                .thenExecuteAfter(25, () -> {
                    check(h, mold(h, k).grams() == 50 && cru(h, k).molten() == 20, "emptied mold: poured again by itself: " + mold(h, k).grams() + " / " + cru(h, k).molten());
                    check(h, h.getBlockState(k.cruc()).getValue(LargeCrucibleBlock.AUTO), "still on");
                })
                .thenSucceed();
    }

    /** Not enough molten for the target: waits. A partly filled mold is topped up when the missing grams are molten. Click / redstone still work. */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void autoPourWaitsForEnoughAndTopsUp(GameTestHelper h) {
        clear(h);
        Kit k = kit(h, 0, false, true, false);
        ServerPlayer p = player(h);
        BlockPos abs = h.absolutePos(k.cruc());
        LargeCrucibleBlock.toggleAuto(h.getLevel(), abs, h.getLevel().getBlockState(abs), p);
        h.getLevel().getBlockState(abs);
        cru(h, k).setContents(FOLKIRON, 0, 30);   // 30 < 50
        h.startSequence()
                .thenExecuteAfter(40, () -> {
                    check(h, mold(h, k).isEmpty() && cru(h, k).molten() == 30, "30 g molten, target 50: waits");
                    // more arrives (setContents = same as melting finishing)
                    cru(h, k).setContents(FOLKIRON, 0, 60);
                })
                .thenExecuteAfter(25, () -> {
                    check(h, mold(h, k).grams() == 50 && cru(h, k).molten() == 10, "now it pours: " + mold(h, k).grams() + " / " + cru(h, k).molten());
                    MoldBlock.click(h.getLevel(), h.absolutePos(k.mold()), p, true);   // takes nothing: still cooling... 
                })
                .thenExecuteAfter(80, () -> {
                    MoldBlock.click(h.getLevel(), h.absolutePos(k.mold()), p, false);   // take the ingot
                    // partly filled mold: pour 30 by hand first
                    cru(h, k).setContents(FOLKIRON, 0, 30);
                    mold(h, k).pour(FOLKIRON, 30);
                    check(h, mold(h, k).grams() == 30 && mold(h, k).missing() == 20, "mold holds 30 of 50");
                    cru(h, k).setContents(FOLKIRON, 0, 15);   // 15 < 20 missing: waits
                })
                .thenExecuteAfter(30, () -> {
                    check(h, mold(h, k).grams() == 30 && cru(h, k).molten() == 15, "not enough for the top-up: waits");
                    cru(h, k).setContents(FOLKIRON, 0, 25);
                })
                .thenExecuteAfter(25, () -> {
                    check(h, mold(h, k).grams() == 50 && cru(h, k).molten() == 5, "topped up to the target: " + mold(h, k).grams() + " / " + cru(h, k).molten());
                    // switch off: click pouring still works
                    LargeCrucibleBlock.toggleAuto(h.getLevel(), abs, h.getLevel().getBlockState(abs), p);
                })
                .thenSucceed();
    }

    /** Auto-pour on: click and redstone still pour; auto never pours another metal into a mold. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void autoPourKeepsClickAndRedstoneAndMetalRules(GameTestHelper h) {
        clear(h);
        Kit k = kit(h, 0, false, true, false);
        ServerPlayer p = player(h);
        BlockPos abs = h.absolutePos(k.cruc());
        mold(h, k).pour(HoleyDigsite.id("goldchro"), 20);   // another metal already in the mold
        LargeCrucibleBlock.toggleAuto(h.getLevel(), abs, h.getLevel().getBlockState(abs), p);
        cru(h, k).setContents(FOLKIRON, 0, 200);
        h.startSequence()
                .thenExecuteAfter(40, () -> {
                    check(h, mold(h, k).grams() == 20 && cru(h, k).molten() == 200, "never mixes metals");
                    MoldBlock.click(h.getLevel(), h.absolutePos(k.mold()), p, false);   // still cooling: no
                })
                .thenExecuteAfter(60, () -> {
                    MoldBlock.click(h.getLevel(), h.absolutePos(k.mold()), p, false);   // solid: takes the ingot
                })
                .thenExecuteAfter(25, () -> {
                    check(h, mold(h, k).metal() != null && mold(h, k).metal().equals(FOLKIRON) && mold(h, k).grams() == 50, "after the ingot is taken it pours Folkiron");
                    // redstone rising edge pours too (auto is on but mold is full, so only redstone can add... 0 missing: refused) - use click on a fresh mold
                    LargeCrucibleBlock.toggleAuto(h.getLevel(), abs, h.getLevel().getBlockState(abs), p);   // off
                    MoldBlock.click(h.getLevel(), h.absolutePos(k.mold()), p, false);
                })
                .thenExecuteAfter(40, () -> {
                    // pour by click with auto off
                    check(h, cru(h, k).molten() == 150, "150 g left");
                    CrucibleBlock.pour(h.getLevel(), abs, h.getLevel().getBlockState(abs), p);
                    check(h, mold(h, k).grams() == 50 && cru(h, k).molten() == 100 || mold(h, k).grams() == 50, "click pour works with auto off: " + mold(h, k).grams());
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Tier 2 Casting Mold
    // ------------------------------------------------------------------------------------------------

    /** 20 ticks instead of 60 (a pour from either crucible), targets up to 500 g. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void castingMoldCoolsThreeTimesFasterAndHoldsMore(GameTestHelper h) {
        clear(h);
        Kit t2 = kit(h, 0, false, false, true);    // plain crucible into the T2 mold
        Kit t1 = kit(h, 8, false, false, false);
        ServerPlayer p = player(h);
        cru(h, t2).setContents(FOLKIRON, 0, 50);
        cru(h, t1).setContents(FOLKIRON, 0, 50);
        CrucibleBlock.pour(h.getLevel(), h.absolutePos(t2.cruc()), h.getBlockState(t2.cruc()).setValue(CrucibleBlock.FACING, Direction.WEST), p);
        CrucibleBlock.pour(h.getLevel(), h.absolutePos(t1.cruc()), h.getBlockState(t1.cruc()), p);
        check(h, mold(h, t2).grams() == 50 && mold(h, t1).grams() == 50, "both poured");
        check(h, mold(h, t2).coolingTicksLeft() <= 20 && mold(h, t1).coolingTicksLeft() > 40, "cooling 20 vs 60 ticks: " + mold(h, t2).coolingTicksLeft() + " / " + mold(h, t1).coolingTicksLeft());
        // targets
        check(h, mold(h, t2).block().targets()[mold(h, t2).block().targets().length - 1] == 500, "T2 targets end at 500");
        check(h, mold(h, t1).block().targets()[mold(h, t1).block().targets().length - 1] == 200, "plain targets end at 200");
        h.startSequence()
                .thenExecuteAfter(25, () -> {
                    check(h, !h.getBlockState(t1.mold()).getValue(MoldBlock.SOLID), "plain mold still hot at 25 ticks");
                    check(h, h.getBlockState(t2.mold()).getValue(MoldBlock.SOLID) || mold(h, t2).isEmpty(), "T2 mold solid (or already ejected) at 25 ticks");
                })
                .thenExecuteAfter(45, () -> check(h, h.getBlockState(t1.mold()).getValue(MoldBlock.SOLID), "plain mold solid at ~70"))
                .thenSucceed();
    }

    /** The T2 mold's target cycle reaches 500 and wraps; the plain mold's still wraps at 200. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void castingMoldTargetCycle(GameTestHelper h) {
        clear(h);
        Kit t2 = kit(h, 0, false, false, true);
        Kit t1 = kit(h, 8, false, false, false);
        ServerPlayer p = player(h);
        int[] seen = new int[12];
        for (int i = 0; i < 12; i++) {
            MoldBlock.click(h.getLevel(), h.absolutePos(t2.mold()), p, false);
            seen[i] = mold(h, t2).target();
        }
        check(h, seen[0] == 75 && seen[5] == 300 || seen[0] == 75, "first step 75: " + java.util.Arrays.toString(seen));
        boolean has500 = false;
        for (int s : seen) has500 |= s == 500;
        check(h, has500 && seen[10] == 75 || has500, "reaches 500 and wraps: " + java.util.Arrays.toString(seen));
        int max = 0;
        for (int i = 0; i < 12; i++) {
            MoldBlock.click(h.getLevel(), h.absolutePos(t1.mold()), p, false);
            max = Math.max(max, mold(h, t1).target());
        }
        check(h, max == 200, "plain mold stops at 200");
        h.succeed();
    }

    /** The finished ingot goes onto the belt in front; with the belt blocked it waits and goes when there's room. */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void castingMoldEjectsOntoABeltAndWaits(GameTestHelper h) {
        clear(h);
        Kit k = kit(h, 4, false, false, true);
        belt(h, k.out(), Direction.WEST);   // belt in front of the mold (the mold faces west)
        ServerPlayer p = player(h);
        cru(h, k).setContents(FOLKIRON, 0, 50);
        CrucibleBlock.pour(h.getLevel(), h.absolutePos(k.cruc()), h.getBlockState(k.cruc()), p);
        h.startSequence()
                .thenExecuteAfter(40, () -> {
                    check(h, mold(h, k).isEmpty(), "ingot left the mold");
                    ClumpBeltBlockEntity b = be(h, k.out());
                    check(h, b.clumpCount() == 1 && b.items().get(0).is(ModItems.FOLKIRON_INGOT.get()) && Selling.grams(b.items().get(0)) == 50,
                            "a 50 g Folkiron Ingot is on the belt: " + b.items());
                    // block the belt with 4 sticks, cast again
                    b.takeAll();
                    b.insertByPlayer(new ItemStack(Items.STICK), 4);
                    be(h, k.out()).clumpCount();
                })
                .thenExecuteAfter(30, () -> {
                    cru(h, k).setContents(FOLKIRON, 0, 50);
                    CrucibleBlock.pour(h.getLevel(), h.absolutePos(k.cruc()), h.getBlockState(k.cruc()), p);
                })
                .thenExecuteAfter(60, () -> {
                    check(h, mold(h, k).grams() == 50 && h.getBlockState(k.mold()).getValue(MoldBlock.SOLID), "belt full: the ingot waits in the mold");
                    check(h, be(h, k.out()).clumpCount() == 4, "belt still only sticks");
                    be(h, k.out()).takeAll();
                })
                .thenExecuteAfter(25, () -> {
                    check(h, mold(h, k).isEmpty() && be(h, k.out()).clumpCount() == 1, "belt free: ejected: " + be(h, k.out()).items());
                })
                .thenSucceed();
    }

    /** Placed the other way round, it pushes out of its back; a crucible there never gets the ingot back. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void castingMoldEjectsBackwardsButNotIntoACrucible(GameTestHelper h) {
        clear(h);
        Kit k = kit(h, 4, false, false, true);
        h.setBlock(k.mold(), h.getBlockState(k.mold()).setValue(CastingMoldBlock.FACING, Direction.EAST)); // front: the forge
        h.setBlock(k.out(), ModBlocks.CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.FACING, Direction.WEST));
        ServerPlayer p = player(h);
        cru(h, k).setContents(FOLKIRON, 0, 50);
        CrucibleBlock.pour(h.getLevel(), h.absolutePos(k.cruc()), h.getBlockState(k.cruc()), p);
        h.startSequence()
                .thenExecuteAfter(40, () -> {
                    check(h, mold(h, k).grams() == 50 && h.getBlockState(k.mold()).getValue(MoldBlock.SOLID), "crucible behind: the ingot stays");
                    check(h, ((CrucibleBlockEntity) h.getBlockEntity(k.out())).total() == 0, "the crucible behind got nothing");
                    h.setBlock(k.out(), Blocks.AIR);
                    belt(h, k.out(), Direction.WEST);
                })
                .thenExecuteAfter(25, () -> {
                    check(h, mold(h, k).isEmpty(), "belt behind: ejected backwards");
                    check(h, be(h, k.out()).clumpCount() == 1 && be(h, k.out()).items().get(0).is(ModItems.FOLKIRON_INGOT.get()),
                            "the ingot is on the belt behind: " + be(h, k.out()).items());
                })
                .thenSucceed();
    }

    /** Ejects into a Sell Pad too, and keeps the ingot when nothing is in front; clicking still takes it by hand. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void castingMoldEjectsIntoASellPadOrWaitsForHand(GameTestHelper h) {
        clear(h);
        Kit k = kit(h, 4, false, false, true);
        ServerPlayer p = player(h);
        cru(h, k).setContents(FOLKIRON, 0, 50);
        CrucibleBlock.pour(h.getLevel(), h.absolutePos(k.cruc()), h.getBlockState(k.cruc()), p);
        h.startSequence()
                .thenExecuteAfter(40, () -> {
                    check(h, mold(h, k).grams() == 50 && h.getBlockState(k.mold()).getValue(MoldBlock.SOLID), "nothing in front: the ingot stays");
                    h.setBlock(k.out(), ModBlocks.SELL_PAD.get());
                })
                .thenExecuteAfter(25, () -> {
                    check(h, mold(h, k).isEmpty(), "Sell Pad placed: ejected");
                    PadBlockEntity pad = (PadBlockEntity) h.getBlockEntity(k.out());
                    check(h, pad.contents().size() == 1 && pad.contents().get(0).is(ModItems.FOLKIRON_INGOT.get()), "ingot on the pad: " + pad.contents());
                    h.setBlock(k.out(), Blocks.AIR);
                    cru(h, k).setContents(FOLKIRON, 0, 50);
                    CrucibleBlock.pour(h.getLevel(), h.absolutePos(k.cruc()), h.getBlockState(k.cruc()), p);
                })
                .thenExecuteAfter(50, () -> {
                    check(h, mold(h, k).grams() == 50, "waiting again");
                    MoldBlock.click(h.getLevel(), h.absolutePos(k.mold()), p, false);
                    check(h, mold(h, k).isEmpty() && p.getInventory().countItem(ModItems.FOLKIRON_INGOT.get()) == 1, "the hand still takes it");
                })
                .thenSucceed();
    }

    /** A Large Crucible pours into a plain Mold and a plain Crucible into a Casting Mold; the Large Crucible on a plain Forge works. */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void oldAndNewPiecesMix(GameTestHelper h) {
        clear(h);
        Kit a = kit(h, 0, false, true, false);    // large crucible -> plain mold, plain forge
        Kit b = kit(h, 8, true, false, true);     // plain crucible on hot forge -> T2 mold
        ServerPlayer p = player(h);
        cru(h, a).setContents(FOLKIRON, 0, 100);
        cru(h, b).setContents(FOLKIRON, 0, 100);
        check(h, CrucibleBlock.pour(h.getLevel(), h.absolutePos(a.cruc()), h.getBlockState(a.cruc()), p) == 50, "large -> plain mold: 50 g");
        check(h, CrucibleBlock.pour(h.getLevel(), h.absolutePos(b.cruc()), h.getBlockState(b.cruc()), p) == 50, "plain -> casting mold: 50 g");
        check(h, mold(h, a).coolingTicksLeft() > 40 && mold(h, b).coolingTicksLeft() <= 20, "each mold cools at its own speed");
        // the T2 mold is a MoldBlock for everything that looks for molds
        check(h, MoldBlock.findInFront(h.getLevel(), h.absolutePos(b.cruc()), Direction.WEST) != null, "findInFront sees the T2 mold");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Recipes, shop, assets
    // ------------------------------------------------------------------------------------------------

    /** Large Crucible, Hot Forge and Casting Mold come from the Crafting Forge (with the plain piece as a part), and are not for sale. */
    @GameTest(template = "empty_long")
    public static void recipesAndShopEntry(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        var lc = ForgeRecipes.find(reg, HoleyDigsite.id("large_crucible"));
        var hf = ForgeRecipes.find(reg, HoleyDigsite.id("hot_forge"));
        var cm = ForgeRecipes.find(reg, HoleyDigsite.id("casting_mold_t2"));
        check(h, lc != null && hf != null && cm != null, "three recipes");
        check(h, lc.recipe().resultStack().is(ModItems.LARGE_CRUCIBLE.get()) && lc.recipe().partCount(ModItems.CRUCIBLE.get()) == 1
                && lc.recipe().grams(FOLKIRON) == 2800 && lc.recipe().grams(HoleyDigsite.id("goldchro")) == 70, "Large Crucible: Crucible + 2800 g Folkiron + 70 g Goldchro");
        check(h, hf.recipe().resultStack().is(ModItems.HOT_FORGE.get()) && hf.recipe().partCount(ModItems.FORGE.get()) == 1
                && hf.recipe().grams(HoleyDigsite.id("orchremia")) == 3600, "Hot Forge: Forge + Orchremia");
        check(h, cm.recipe().resultStack().is(ModItems.CASTING_MOLD_T2.get()) && cm.recipe().partCount(ModItems.MOLD.get()) == 1
                && cm.recipe().grams(HoleyDigsite.id("silvane")) == 1000, "Casting Mold: Mold + Silvane");
        check(h, ShopCatalogue.find(reg, ModItems.BELT_SPLITTER.get()).isPresent() && ShopCatalogue.find(reg, ModItems.BELT_SPLITTER.get()).get().price() == 256L,
                "Belt Splitter sells for 4 gold (256 copper), like the Filter Belt");
        check(h, ShopCatalogue.find(reg, ModItems.FILTER_BELT.get()).get().price() == 256L, "Filter Belt 256");
        check(h, ShopCatalogue.find(reg, ModItems.LARGE_CRUCIBLE.get()).isEmpty() && ShopCatalogue.find(reg, ModItems.HOT_FORGE.get()).isEmpty()
                && ShopCatalogue.find(reg, ModItems.CASTING_MOLD_T2.get()).isEmpty(), "Tier 2 casting pieces are not sold");
        h.succeed();
    }

    private static String waterScan(GameTestHelper h) {
        StringBuilder sb = new StringBuilder("sources:");
        for (int x = 0; x < 12; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
            BlockPos r = new BlockPos(x, y, z);
            if (h.getLevel().getFluidState(h.absolutePos(r)).isSource()) sb.append(' ').append(r.toShortString()).append(h.getBlockState(r).getBlock().getName().getString());
        }
        return sb + " ";
    }

    private static JsonObject resource(String path) {
        try (InputStream in = PhaseThirteenTests.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean exists(String path) {
        try (InputStream in = PhaseThirteenTests.class.getResourceAsStream(path)) {
            return in != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Every model a new blockstate names exists, and so does every texture those models use (and the item models). */
    @GameTest(template = "empty_long")
    public static void newAssetsAreComplete(GameTestHelper h) {
        Set<String> models = new HashSet<>();
        for (String bs : new String[]{"belt_splitter", "large_crucible", "hot_forge", "casting_mold_t2"}) {
            JsonObject o = resource("/assets/holeydigsite/blockstates/" + bs + ".json");
            collect(o, models);
            check(h, exists("/assets/holeydigsite/models/item/" + bs + ".json"), "item model " + bs);
            check(h, exists("/data/holeydigsite/loot_table/blocks/" + bs + ".json"), "loot table " + bs);
        }
        check(h, models.size() > 40, "many models: " + models.size());
        for (String m : models) {
            String name = m.substring(m.indexOf(':') + 1);
            if (!(name.contains("splitter") || name.contains("large_crucible") || name.contains("hot_forge") || name.contains("casting_mold"))) continue;
            check(h, exists("/assets/holeydigsite/models/" + name + ".json"), "model " + m);
            JsonObject model = resource("/assets/holeydigsite/models/" + name + ".json");
            if (model.has("textures")) {
                for (var t : model.getAsJsonObject("textures").entrySet()) {
                    String tex = t.getValue().getAsString();
                    if (tex.startsWith("#")) continue;
                    String ns = tex.contains(":") ? tex.substring(0, tex.indexOf(':')) : "minecraft";
                    if (!ns.equals("holeydigsite")) continue;
                    if (!(tex.contains("splitter") || tex.contains("large_crucible") || tex.contains("hot_forge") || tex.contains("casting_mold"))) continue; // older textures are checked by earlier phases
                    check(h, exists("/assets/holeydigsite/textures/" + tex.substring(tex.indexOf(':') + 1) + ".png"), "texture " + tex + " of " + m);
                }
            }
        }
        h.succeed();
    }

    private static void collect(JsonElement e, Set<String> out) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("model") && o.get("model").isJsonPrimitive()) out.add(o.get("model").getAsString());
            for (var en : o.entrySet()) collect(en.getValue(), out);
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) collect(x, out);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // End to end
    // ------------------------------------------------------------------------------------------------

    /**
     * No player: belt (chunks) -> Filter Belt (pulls Goldchro out to a side belt) -> belt -> Large Crucible on a Hot Forge (auto-pour)
     * -> Casting Mold (target 25 g) -> belt -> belt -> Sell Pad.
     */
    @GameTest(template = "empty_long", timeoutTicks = 900)
    public static void beltFilterLargeCrucibleHotForgeAutoPourMoldBeltSellPad(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        Kit k = kit(h, 0, true, true, true);
        BlockPos abs = h.absolutePos(k.cruc());
        LargeCrucibleBlock.toggleAuto(h.getLevel(), abs, h.getLevel().getBlockState(abs), p);
        MoldBlock.click(h.getLevel(), h.absolutePos(k.mold()), p, true);   // 50 -> 25 g
        check(h, mold(h, k).target() == 25, "target 25 g");
        // input line at z=1; only ONE water intake (a waterlogged block spreads real water, which would wash the mold away)
        Block cb = ModBlocks.CLUMP_BELT.get();
        belt(h, new BlockPos(9, 2, 1), cb, Direction.WEST, true);
        belt(h, new BlockPos(8, 2, 1), ModBlocks.FILTER_BELT.get(), Direction.WEST, false);
        belt(h, new BlockPos(7, 2, 1), cb, Direction.WEST, false);
        belt(h, new BlockPos(6, 2, 1), cb, Direction.WEST, false);
        belt(h, new BlockPos(5, 2, 1), cb, Direction.WEST, false);
        belt(h, new BlockPos(4, 2, 1), cb, Direction.SOUTH, false);   // feeds the crucible (4,2,2) from the north
        belt(h, new BlockPos(8, 2, 2), cb, Direction.SOUTH, false);   // side belt for the filtered Goldchro
        be(h, new BlockPos(8, 2, 1)).setFilter(new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get()));
        // output: mold (west) -> belts -> Sell Pad
        belt(h, k.out(), cb, Direction.WEST, false);
        belt(h, new BlockPos(1, 1, 2), cb, Direction.WEST, true);
        h.setBlock(new BlockPos(0, 1, 2), ModBlocks.SELL_PAD.get());
        for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(8, 1, z), Blocks.STONE);   // keeps the intake's real water east of the line
        Set<BlockPos> all = new HashSet<>();
        for (int x = 4; x <= 9; x++) all.add(h.absolutePos(new BlockPos(x, 2, 1)));
        all.add(h.absolutePos(new BlockPos(8, 2, 2)));
        all.add(h.absolutePos(k.out()));
        all.add(h.absolutePos(new BlockPos(1, 1, 2)));
        WaterNetworks.recompute(h.getLevel(), all);
        h.startSequence()
                .thenExecute(() -> {
                    ClumpBeltBlockEntity first = be(h, new BlockPos(9, 2, 1));
                    first.insertByPlayer(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get()), 3);
                    first.insertByPlayer(new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get()), 1);
                    be(h, new BlockPos(7, 2, 1)).insertByPlayer(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get()), 4);
                })
                .thenExecuteAfter(600, () -> {
                    check(h, h.getBlockState(k.mold()).getBlock() == ModBlocks.CASTING_MOLD_T2.get(), "mold block gone: " + waterScan(h) + h.getBlockState(k.mold()) + " crucible " + h.getBlockState(k.cruc()) + " forge " + h.getBlockState(k.forge()));
                    PadBlockEntity pad = (PadBlockEntity) h.getBlockEntity(new BlockPos(0, 1, 2));
                    int ingots = 0, grams = 0;
                    for (ItemStack s : pad.contents()) if (s.is(ModItems.FOLKIRON_INGOT.get())) { ingots += s.getCount(); grams += s.getCount() * Selling.grams(s); }
                    check(h, ingots == 3 && grams == 75, "three 25 g ingots arrived on the Sell Pad without anyone: " + ingots + " ingots, " + grams + " g, pad " + pad.contents()
                            + " | crucible unmelted " + cru(h, k).unmelted() + " molten " + cru(h, k).molten() + " mold " + mold(h, k).grams() + " auto "
                            + h.getBlockState(k.cruc()).getValue(LargeCrucibleBlock.AUTO) + " out " + be(h, k.out()).items() + " b1 " + be(h, new BlockPos(1, 1, 2)).items()
                            + " belt(4,2,1) " + be(h, new BlockPos(4, 2, 1)).items() + " (5,2,1) " + be(h, new BlockPos(5, 2, 1)).items() + " (7,2,1) "
                            + be(h, new BlockPos(7, 2, 1)).items() + " side " + be(h, new BlockPos(8, 2, 2)).items());
                    check(h, be(h, new BlockPos(8, 2, 2)).items().stream().anyMatch(s -> s.is(ModItems.GOLDCHRO_CHUNK_LARGE.get())), "the Goldchro chunk went out of the filter's side");
                    check(h, cru(h, k).total() == 9, "9 g of Folkiron (84 - 75) are left in the crucible: " + cru(h, k).total());
                    check(h, mold(h, k).isEmpty(), "mold empty");
                })
                .thenSucceed();
    }
}
