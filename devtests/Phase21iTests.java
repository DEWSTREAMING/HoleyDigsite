package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.BeltMode;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.belt.DownInclineBeltBlock;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.Sites;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.transport.DropChuteBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;

/**
 * Phase 21i (sandbox only): Downward item transport:
 * <ul>
 *   <li>Down incline moves item one block down onto a belt (powered) and stops without water</li>
 *   <li>A 3-step staircase of down inclines chains properly</li>
 *   <li>Drop chute 1 and 5 blocks tall delivers into belt and into sluice</li>
 *   <li>Full target makes the feeding belt wait and nothing is lost</li>
 *   <li>Chute never outputs into chest or hopper</li>
 *   <li>Both appear in an old world's Trading Post without a rebuild</li>
 * </ul>
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21iTests {
    private static final ResourceLocation HOME = HoleyDigsite.id("home");
    private static final ResourceLocation HOME_LAYOUT = HoleyDigsite.id("home");

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h, int sizeX) {
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y <= 8; y++) {
                for (int z = 0; z < 5; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static void place(GameTestHelper h, BlockPos rel, BlockState state) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, Block.updateFromNeighbourShapes(state, h.getLevel(), abs), 3);
        TestWater.underBelt(h, rel);
    }

    private static void belt(GameTestHelper h, BlockPos rel, Direction facing) {
        place(h, rel, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, facing));
    }

    private static void downIncline(GameTestHelper h, BlockPos rel, Direction facing) {
        place(h, rel, ModBlocks.DOWN_INCLINE_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, facing));
    }

    private static ClumpBeltBlockEntity be(GameTestHelper h, BlockPos rel) {
        return (ClumpBeltBlockEntity) h.getBlockEntity(rel);
    }

    private static int items(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static BlockPos far(int n) {
        return new BlockPos(20000 + n * 64, 100, 20000);
    }

    private static SiteDef def(ResourceLocation id) {
        SiteDef d = SiteDefs.get(id);
        if (d == null) throw new IllegalStateException("no site definition " + id);
        return d;
    }

    private static void prepare(ServerLevel l, BlockPos a) {
        BoundingBox big = new BoundingBox(a.getX() - 4, a.getY() - 2, a.getZ() - 4, a.getX() + 40, a.getY() + 34, a.getZ() + 40);
        Sites.clear(l, big);
        for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(48))) e.discard();
    }

    private static void cleanup(ServerLevel l, List<BlockPos> anchors) {
        SiteData data = SiteData.get(l);
        for (SiteData.Site s : new ArrayList<>(data.all())) {
            for (BlockPos a : anchors) {
                if (s.zone().isInside(a) || s.anchor().equals(a) || (s.parent() != null && s.parent().endsWith(a.getX() + "," + a.getY() + "," + a.getZ()))) {
                    data.remove(s.key());
                }
            }
        }
        PlotData pd = PlotData.get(l);
        for (PlotData.Plot p : new ArrayList<>(pd.all())) {
            for (BlockPos a : anchors) {
                if (p.board().getX() >= a.getX() - 2 && p.board().getX() < a.getX() + 40 && p.board().getZ() >= a.getZ() - 2
                        && p.board().getZ() < a.getZ() + 40) {
                    pd.remove(p.id(), p.board());
                }
            }
        }
        SiteDefs.clearOverrides();
    }

    private static <T extends BlockEntity> List<T> bes(ServerLevel l, BoundingBox zone, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ())) {
            BlockEntity be = l.getBlockEntity(p);
            if (type.isInstance(be)) out.add(type.cast(be));
        }
        return out;
    }

    private static Map<Integer, Item> displays(ServerLevel l, BoundingBox zone) {
        Map<Integer, Item> m = new HashMap<>();
        for (ShopDisplayBlockEntity d : bes(l, zone, ShopDisplayBlockEntity.class)) {
            if (d.slot() > 0) m.put(d.slot(), d.item());
        }
        return m;
    }

    /**
     * 1. down incline moves an item one block down onto a belt (powered) and stops without water.
     */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void downInclineMovesItemDownOntoBeltAndStopsWithoutWater(GameTestHelper h) {
        clear(h, 8);
        BlockPos incPos = new BlockPos(1, 2, 2);
        BlockPos flatPos = new BlockPos(2, 1, 2);
        belt(h, flatPos, Direction.EAST);
        downIncline(h, incPos, Direction.EAST);
        check(h, h.getBlockState(incPos).getValue(ClumpBeltBlock.WATER), "down incline has water");
        check(h, h.getBlockState(flatPos).getValue(ClumpBeltBlock.WATER), "flat belt has water");

        be(h, incPos).accept(Direction.WEST, 42, new ItemStack(ModItems.DIRT_CLUMP.get()));
        h.startSequence()
                .thenWaitUntil(() -> check(h, be(h, flatPos).clumpCount() == 1, "clump moved down onto flat belt"))
                .thenExecute(() -> {
                    check(h, be(h, incPos).clumpCount() == 0, "down incline is now empty");
                    // Cut water: remove water intakes
                    h.setBlock(new BlockPos(2, 0, 2), Blocks.AIR);
                    h.setBlock(new BlockPos(1, 1, 2), Blocks.AIR);
                    WaterNetworks.recompute(h.getLevel(), Set.of(h.absolutePos(flatPos), h.absolutePos(incPos)));
                })
                .thenExecuteAfter(2, () -> {
                    check(h, !h.getBlockState(incPos).getValue(ClumpBeltBlock.WATER), "down incline lost water");
                    check(h, !h.getBlockState(flatPos).getValue(ClumpBeltBlock.WATER), "flat belt lost water");
                    check(h, !ClumpBeltBlock.running(h.getBlockState(incPos)), "down incline not running");
                    // Place clump onto unpowered down incline
                    be(h, incPos).accept(Direction.WEST, 43, new ItemStack(ModItems.DIRT_CLUMP.get()));
                    check(h, be(h, incPos).positions()[0] == 0, "clump at step 0");
                })
                .thenExecuteAfter(30, () -> {
                    check(h, be(h, incPos).clumpCount() == 1, "clump still on down incline");
                    check(h, be(h, incPos).positions()[0] == 0, "clump did not move without water");
                    check(h, be(h, flatPos).clumpCount() <= 1, "flat belt got nothing new");
                })
                .thenSucceed();
    }

    /**
     * 2. a 3-step staircase of down inclines.
     */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void threeStepDownInclineStaircase(GameTestHelper h) {
        clear(h, 8);
        BlockPos s1 = new BlockPos(1, 4, 2);
        BlockPos s2 = new BlockPos(2, 3, 2);
        BlockPos s3 = new BlockPos(3, 2, 2);
        BlockPos flat = new BlockPos(4, 1, 2);

        belt(h, flat, Direction.EAST);
        downIncline(h, s3, Direction.EAST);
        downIncline(h, s2, Direction.EAST);
        downIncline(h, s1, Direction.EAST);

        check(h, h.getBlockState(s1).getValue(ClumpBeltBlock.WATER), "step 1 powered");
        check(h, h.getBlockState(s2).getValue(ClumpBeltBlock.WATER), "step 2 powered");
        check(h, h.getBlockState(s3).getValue(ClumpBeltBlock.WATER), "step 3 powered");
        check(h, h.getBlockState(flat).getValue(ClumpBeltBlock.WATER), "bottom flat belt powered");

        be(h, s1).accept(Direction.WEST, 42, new ItemStack(ModItems.DIRT_CLUMP.get()));

        h.startSequence()
                .thenWaitUntil(() -> check(h, be(h, s2).clumpCount() == 1, "clump reached step 2"))
                .thenWaitUntil(() -> check(h, be(h, s3).clumpCount() == 1, "clump reached step 3"))
                .thenWaitUntil(() -> check(h, be(h, flat).clumpCount() == 1, "clump reached flat belt at bottom"))
                .thenExecute(() -> {
                    check(h, be(h, s1).clumpCount() == 0, "step 1 empty");
                    check(h, be(h, s2).clumpCount() == 0, "step 2 empty");
                    check(h, be(h, s3).clumpCount() == 0, "step 3 empty");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "no items dropped in world");
                })
                .thenSucceed();
    }

    /**
     * 3. a chute 1 and 5 blocks tall delivers into a belt and into a sluice.
     */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void chuteOneAndFiveBlocksTallDeliversIntoBeltAndSluice(GameTestHelper h) {
        clear(h, 10);
        // Part A: 1-block chute over flat belt
        BlockPos bOut = new BlockPos(2, 1, 1);
        BlockPos chute1 = new BlockPos(2, 2, 1);
        BlockPos bIn1 = new BlockPos(1, 2, 1);
        belt(h, bOut, Direction.EAST);
        h.setBlock(chute1, ModBlocks.DROP_CHUTE.get().defaultBlockState());
        belt(h, bIn1, Direction.EAST);

        // Part B: 5-block chute stack over sluice box
        BlockPos sluicePos = new BlockPos(6, 1, 3);
        h.setBlock(sluicePos, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        BlockPos intakePos = new BlockPos(6, 1, 2);
        h.setBlock(intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        WaterNetworks.recompute(h.getLevel(), Set.of(h.absolutePos(sluicePos)));

        for (int y = 2; y <= 6; y++) {
            h.setBlock(new BlockPos(6, y, 3), ModBlocks.DROP_CHUTE.get().defaultBlockState());
        }
        BlockPos bIn5 = new BlockPos(5, 6, 3);
        belt(h, bIn5, Direction.EAST);

        // Feed both
        be(h, bIn1).accept(Direction.WEST, 101, new ItemStack(ModItems.DIRT_CLUMP.get()));
        be(h, bIn5).accept(Direction.WEST, 102, new ItemStack(ModItems.DIRT_CLUMP.get()));

        h.startSequence()
                .thenWaitUntil(() -> {
                    check(h, be(h, bOut).clumpCount() == 1, "1-block chute delivered onto output belt");
                    SluiceBoxBlockEntity sluice = (SluiceBoxBlockEntity) h.getBlockEntity(sluicePos);
                    check(h, sluice.queued() == 1, "5-block chute delivered straight down into sluice queue");
                })
                .thenExecute(() -> {
                    check(h, be(h, bIn1).clumpCount() == 0, "feeder 1 is empty");
                    check(h, be(h, bIn5).clumpCount() == 0, "feeder 5 is empty");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "no items dropped in world");
                })
                .thenSucceed();
    }

    /**
     * 4. full target -> the feeding belt waits and nothing is lost.
     */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void fullTargetFeedingBeltWaitsNothingLost(GameTestHelper h) {
        clear(h, 8);
        // Setup A: Chute directly into full sluice box
        BlockPos sluicePos = new BlockPos(2, 1, 1);
        h.setBlock(sluicePos, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        BlockPos sluiceIntake = new BlockPos(2, 1, 0);
        h.setBlock(sluiceIntake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        WaterNetworks.recompute(h.getLevel(), Set.of(h.absolutePos(sluicePos)));
        SluiceBoxBlockEntity sluice = (SluiceBoxBlockEntity) h.getBlockEntity(sluicePos);
        sluice.addClumps(SluiceBoxBlockEntity.MAX_QUEUE, 1);
        check(h, !sluice.hasQueueRoom(), "sluice queue is full");

        BlockPos chutePos = new BlockPos(2, 2, 1);
        h.setBlock(chutePos, ModBlocks.DROP_CHUTE.get().defaultBlockState());
        BlockPos feedPos = new BlockPos(1, 2, 1);
        belt(h, feedPos, Direction.EAST);

        // Setup B: Down incline into backed-up belt (step 0 occupied, stone in front)
        BlockPos stonePos = new BlockPos(6, 1, 3);
        h.setBlock(stonePos, Blocks.STONE.defaultBlockState());
        BlockPos backedBeltPos = new BlockPos(5, 1, 3);
        belt(h, backedBeltPos, Direction.EAST);
        be(h, backedBeltPos).insertByPlayer(4);

        BlockPos downPos = new BlockPos(4, 2, 3);
        downIncline(h, downPos, Direction.EAST);

        // Put item on feed belt A and down incline B
        be(h, feedPos).accept(Direction.WEST, 10, new ItemStack(ModItems.DIRT_CLUMP.get()));
        be(h, downPos).accept(Direction.WEST, 20, new ItemStack(ModItems.DIRT_CLUMP.get()));

        h.startSequence()
                // Wait for belts to move items to exit
                .thenExecuteAfter(35, () -> {
                    check(h, be(h, feedPos).clumpCount() == 1, "item on feed belt A has not been lost");
                    check(h, be(h, feedPos).positions()[0] == ClumpBeltBlockEntity.LAST, "item on feed belt A is waiting at exit");
                    check(h, be(h, downPos).clumpCount() == 1, "item on down incline B has not been lost");
                    check(h, be(h, downPos).positions()[0] == ClumpBeltBlockEntity.LAST, "item on down incline B is waiting at exit");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "nothing dropped in world");
                })
                .thenExecuteAfter(20, () -> {
                    check(h, be(h, feedPos).clumpCount() == 1, "item on feed belt A still waiting");
                    check(h, be(h, downPos).clumpCount() == 1, "item on down incline B still waiting");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "still nothing dropped in world");

                    // Now free the targets
                    sluice.washOneClump((ServerLevel) h.getLevel(), h.absolutePos(sluicePos));
                    be(h, backedBeltPos).clumps().clear();
                })
                .thenWaitUntil(() -> {
                    check(h, be(h, feedPos).clumpCount() == 0, "feed belt A is now empty");
                    check(h, be(h, downPos).clumpCount() == 0, "down incline B is now empty");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "nothing dropped");
                })
                .thenSucceed();
    }

    /**
     * 5. chute never outputs into a chest.
     */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void chuteNeverOutputsIntoChest(GameTestHelper h) {
        clear(h, 8);
        BlockPos chestPos = new BlockPos(2, 1, 2);
        h.setBlock(chestPos, Blocks.CHEST.defaultBlockState());
        BlockPos chutePos = new BlockPos(2, 2, 2);
        h.setBlock(chutePos, ModBlocks.DROP_CHUTE.get().defaultBlockState());
        BlockPos feedPos = new BlockPos(1, 2, 2);
        belt(h, feedPos, Direction.EAST);

        be(h, feedPos).accept(Direction.WEST, 77, new ItemStack(ModItems.DIRT_CLUMP.get()));

        h.startSequence()
                .thenExecuteAfter(35, () -> {
                    check(h, be(h, feedPos).clumpCount() == 1, "clump stayed on feeding belt");
                    check(h, be(h, feedPos).positions()[0] == ClumpBeltBlockEntity.LAST, "clump waiting at exit");
                    ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(chestPos);
                    check(h, chest.isEmpty(), "chest is completely empty");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "no items dropped in world");

                    // Player right-click on chute directly with dirt clump
                    ServerPlayer p = h.makeMockServerPlayerInLevel();
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIRT_CLUMP.get(), 3));
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(chutePos)), Direction.UP, h.absolutePos(chutePos), false);
                    h.getBlockState(chutePos).useItemOn(p.getMainHandItem(), h.getLevel(), p, InteractionHand.MAIN_HAND, hit);

                    check(h, p.getMainHandItem().getCount() == 3, "player's clumps were refused and not consumed");
                    check(h, chest.isEmpty(), "chest still empty after player right-click");
                })
                .thenExecute(() -> {
                    // Also test replacing chest with hopper
                    h.setBlock(chestPos, Blocks.HOPPER.defaultBlockState());
                })
                .thenExecuteAfter(20, () -> {
                    check(h, be(h, feedPos).clumpCount() == 1, "clump still on feeding belt");
                    HopperBlockEntity hopper = (HopperBlockEntity) h.getBlockEntity(chestPos);
                    check(h, hopper.isEmpty(), "hopper is completely empty");
                    check(h, items(h, ModItems.DIRT_CLUMP.get()) == 0, "no items dropped in world");
                })
                .thenSucceed();
    }

    /**
     * 6. both appear in an old world's Trading Post.
     */
    @GameTest(template = "empty", batch = "p21i_oldworld", timeoutTicks = 100)
    public static void bothAppearInOldWorldsTradingPost(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(21);
        prepare(l, a);
        try {
            SiteDef d = def(HOME);
            Map<Integer, ResourceLocation> now = new HashMap<>(SiteDefs.layout(d.layout()));
            check(h, now.get(34).equals(BuiltInRegistries.ITEM.getKey(ModItems.DOWN_INCLINE_BELT.get()))
                    && now.get(35).equals(BuiltInRegistries.ITEM.getKey(ModItems.DROP_CHUTE.get())),
                    "layout 34/35: " + now.get(34) + " " + now.get(35));
            Map<Integer, ResourceLocation> old = new HashMap<>(now);
            old.remove(34);
            old.remove(35);
            SiteDefs.overrideLayout(HOME_LAYOUT, old);
            Sites.Result r = Sites.build(l, d, a, null, false);
            BoundingBox z = r.site().zone();
            // In a real old world saved before 21i, displays 34 and 35 did not exist at all in the chunk
            List<BlockPos> removed = new ArrayList<>();
            for (BlockEntity be : Sites.blockEntities(l, z)) {
                if (be instanceof ShopDisplayBlockEntity disp && (disp.slot() == 34 || disp.slot() == 35)) {
                    removed.add(disp.getBlockPos());
                }
            }
            check(h, removed.size() == 2, "found slots 34 and 35 to simulate old world");
            for (BlockPos p : removed) {
                l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            check(h, !displays(l, z).containsKey(34) && !displays(l, z).containsKey(35),
                    "old store: 34 and 35 do not exist in world");
            BlockPos litter = a.offset(15, 11, 10);
            l.setBlock(litter, Blocks.GLASS.defaultBlockState(), 3);
            SiteDefs.overrideLayout(HOME_LAYOUT, now);
            Sites.checkAll(l);
            Map<Integer, Item> shown = displays(l, z);
            check(h, shown.get(34) == ModItems.DOWN_INCLINE_BELT.get() && shown.get(35) == ModItems.DROP_CHUTE.get(),
                    "new displays appear: " + shown.get(34) + " " + shown.get(35));
            check(h, l.getBlockState(litter).is(Blocks.GLASS), "no rebuild");
            h.succeed();
        } finally {
            SiteDefs.clearOverrides();
            cleanup(l, List.of(a));
        }
    }
}
