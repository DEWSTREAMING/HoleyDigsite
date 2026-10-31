package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.drill.ClumpOutputs;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.sluice.LongSluiceBlock;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.sluice.SluicePart;
import com.holeydigsite.sluice.SluiceRefunds;
import com.holeydigsite.sluice.SluiceStatus;
import com.holeydigsite.sluice.SluiceUpgrade;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The long sluice (Tier 2): placing, breaking, input at the chute, water ports and statuses, two inserts, the refund of old
 * one-block Tier 2 sluices, skins on all parts. Template {@code empty_long} (26x5x5).
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase29Tests {
    private static final Block LONG = ModBlocks.SLUICE_BOX_T2.get();

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 26; x++) for (int y = 1; y <= 4; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 0; x < 26; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.moveTo(h.absoluteVec(new Vec3(24.5, 1, 3.5)).x, h.absoluteVec(new Vec3(24.5, 1, 3.5)).y, h.absoluteVec(new Vec3(24.5, 1, 3.5)).z, 0, 0);
        return p;
    }

    private static void click(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held, boolean sneak, Direction face) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(sneak);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), face, abs, false));
        p.setShiftKeyDown(false);
    }

    private static int itemEntities(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** A long sluice facing {@code facing} with its back part at {@code back}. */
    private static SluiceBoxBlockEntity longSluice(GameTestHelper h, BlockPos back, Direction facing) {
        h.setBlock(back, LONG.defaultBlockState().setValue(SluiceBoxBlock.FACING, facing));
        return (SluiceBoxBlockEntity) h.getBlockEntity(back);
    }

    private static BlockPos front(BlockPos back, Direction facing) {
        return back.relative(facing);
    }

    // ------------------------------------------------------------------------------------------------ placing

    @GameTest(template = "empty_long")
    public static void placingNeedsRoomAndMakesThreeParts(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        ItemStack item = new ItemStack(LONG.asItem(), 3);
        // the player looks south, so the tray end faces him: north
        click(h, p, new BlockPos(10, 0, 3), item, false, Direction.UP);
        BlockPos back = new BlockPos(10, 1, 3);
        BlockState b = h.getBlockState(back), f = h.getBlockState(front(back, Direction.NORTH)), c = h.getBlockState(back.above());
        h.assertTrue(b.is(LONG) && f.is(LONG) && c.is(LONG), "three parts expected: " + b + " " + f + " " + c);
        h.assertTrue(LongSluiceBlock.part(b) == SluicePart.BACK && LongSluiceBlock.part(f) == SluicePart.FRONT
                && LongSluiceBlock.part(c) == SluicePart.CHUTE, "parts in the wrong places");
        h.assertTrue(b.getValue(SluiceBoxBlock.FACING) == Direction.NORTH && f.getValue(SluiceBoxBlock.FACING) == Direction.NORTH
                && c.getValue(SluiceBoxBlock.FACING) == Direction.NORTH, "the tray end must face the player");
        h.assertTrue(h.getBlockEntity(back) instanceof SluiceBoxBlockEntity && h.getLevel().getBlockEntity(h.absolutePos(front(back, Direction.NORTH))) == null
                && h.getLevel().getBlockEntity(h.absolutePos(back.above())) == null, "only the back part has the block entity");
        h.assertTrue(item.getCount() == 2, "one item used: " + item.getCount());
        ((SluiceBoxBlockEntity) h.getBlockEntity(back)).setOwnerPlayer(p);

        // front space taken
        h.setBlock(new BlockPos(14, 1, 2), Blocks.STONE);
        click(h, p, new BlockPos(14, 0, 3), item, false, Direction.UP);
        h.assertTrue(h.getBlockState(new BlockPos(14, 1, 3)).isAir() && item.getCount() == 2, "placed although the front space is taken");
        // chute space taken
        h.setBlock(new BlockPos(18, 2, 3), Blocks.STONE);
        click(h, p, new BlockPos(18, 0, 3), item, false, Direction.UP);
        h.assertTrue(h.getBlockState(new BlockPos(18, 1, 3)).isAir() && item.getCount() == 2, "placed although the chute space is taken");
        // a rotated sluice: looking west puts the tray end east
        p.moveTo(p.getX(), p.getY(), p.getZ(), 90, 0);
        click(h, p, new BlockPos(21, 0, 3), item, false, Direction.UP);
        h.assertTrue(h.getBlockState(new BlockPos(22, 1, 3)).is(LONG) && LongSluiceBlock.part(h.getBlockState(new BlockPos(22, 1, 3))) == SluicePart.FRONT
                && h.getBlockState(new BlockPos(21, 1, 3)).getValue(SluiceBoxBlock.FACING) == Direction.EAST, "east-facing sluice not placed correctly");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ breaking

    private static void fill(GameTestHelper h, ServerPlayer p, BlockPos back, Direction facing) {
        SluiceBoxBlockEntity s = longSluice(h, back, facing);
        s.addClumps(3, DirtTier.DIRT);
        s.addToTray(new ItemStack(Items.STICK, 5));
        BlockState st = h.getBlockState(back);
        LongSluiceBlock block = (LongSluiceBlock) st.getBlock();
        block.installUpgrade(SluiceUpgrade.RIFFLE, new ItemStack(ModItems.RIFFLE_INSERT.get()), st, h.getLevel(), h.absolutePos(back), p);
        block.installUpgrade(SluiceUpgrade.MESH, new ItemStack(ModItems.FINE_MESH_SCREEN.get()), h.getBlockState(back), h.getLevel(), h.absolutePos(back), p);
    }

    @GameTest(template = "empty_long")
    public static void breakingAnyPartRemovesAllAndDropsOnce(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos back = new BlockPos(10, 1, 3);
        BlockPos[] parts = {back, front(back, Direction.NORTH), back.above()};
        for (int i = 0; i < 3; i++) {
            clear(h);
            fill(h, p, back, Direction.NORTH);
            h.assertTrue(((SluiceBoxBlockEntity) h.getBlockEntity(back)).inserts().size() == 2, "two inserts expected before breaking");
            p.gameMode.destroyBlock(h.absolutePos(parts[i]));
            for (BlockPos part : parts) {
                h.assertTrue(h.getBlockState(part).isAir(), "part " + i + " broken but " + part + " is " + h.getBlockState(part));
            }
            h.assertTrue(itemEntities(h, LONG.asItem()) == 1, "sluice item dropped " + itemEntities(h, LONG.asItem()) + " times when part " + i + " broke");
            h.assertTrue(itemEntities(h, ModItems.DIRT_CLUMP.get()) == 3 && itemEntities(h, Items.STICK) == 5
                    && itemEntities(h, ModItems.RIFFLE_INSERT.get()) == 1 && itemEntities(h, ModItems.FINE_MESH_SCREEN.get()) == 1,
                    "contents not all dropped once when part " + i + " broke");
        }
        // two parts in one tick: still one sluice
        clear(h);
        fill(h, p, back, Direction.NORTH);
        p.gameMode.destroyBlock(h.absolutePos(parts[1]));
        p.gameMode.destroyBlock(h.absolutePos(parts[2]));
        h.getLevel().destroyBlock(h.absolutePos(parts[0]), true);
        h.assertTrue(itemEntities(h, LONG.asItem()) == 1 && itemEntities(h, ModItems.DIRT_CLUMP.get()) == 3,
                "breaking two parts in one tick duplicated or lost things: " + itemEntities(h, LONG.asItem()));
        // creative breaking drops the contents but no sluice item
        clear(h);
        ServerPlayer c = player(h);
        c.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        c.getAbilities().instabuild = true;
        fill(h, p, back, Direction.NORTH);
        c.gameMode.destroyBlock(h.absolutePos(parts[2]));
        h.assertTrue(h.getBlockState(back).isAir() && h.getBlockState(parts[1]).isAir(), "creative break left parts");
        h.assertTrue(itemEntities(h, ModItems.DIRT_CLUMP.get()) == 3, "creative break lost the contents");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ input

    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void clumpsOnlyGoInAtTheChute(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos back = new BlockPos(10, 1, 2);
        SluiceBoxBlockEntity s = longSluice(h, back, Direction.NORTH);
        BlockPos fr = front(back, Direction.NORTH), ch = back.above();
        ServerLevel level = h.getLevel();
        for (Direction d : Direction.values()) {
            boolean ok = d != Direction.NORTH && d != Direction.DOWN;
            h.assertTrue(ClumpOutputs.canOutput(level, h.absolutePos(ch), d, 1) == ok, "chute from " + d + " should be " + ok);
            h.assertTrue(!ClumpOutputs.canOutput(level, h.absolutePos(back), d, 1), "back part took a clump from " + d);
            h.assertTrue(!ClumpOutputs.canOutput(level, h.absolutePos(fr), d, 1), "front part took a clump from " + d);
        }
        // the drill's output path puts it in the queue
        h.assertTrue(ClumpOutputs.tryOutput(level, h.absolutePos(ch), Direction.EAST, 1) && s.queued() == 1, "drill output into the chute failed");
        // players: the chute takes it (any face), the lower parts refuse
        click(h, p, ch, new ItemStack(ModItems.DIRT_CLUMP.get(), 4), false, Direction.NORTH);
        h.assertTrue(s.queued() == 2, "player click on the chute: " + s.queued());
        click(h, p, back, new ItemStack(ModItems.DIRT_CLUMP.get(), 4), false, Direction.UP);
        click(h, p, fr, new ItemStack(ModItems.DIRT_CLUMP.get(), 4), true, Direction.UP);
        h.assertTrue(s.queued() == 2, "lower parts took clumps by hand: " + s.queued());
        // the queue holds 16
        click(h, p, ch, new ItemStack(ModItems.DIRT_CLUMP.get(), 64), true, Direction.UP);
        h.assertTrue(s.queued() == 16, "queue of 16 expected: " + s.queued());
        h.assertTrue(!ClumpOutputs.canOutput(level, h.absolutePos(ch), Direction.UP, 1), "full queue still takes clumps");
        s.takeTray();
        // a belt beside the chute delivers (south side, belt points north)
        BlockPos back2 = new BlockPos(16, 1, 2);
        SluiceBoxBlockEntity s2 = longSluice(h, back2, Direction.NORTH);
        BlockPos belt2 = new BlockPos(16, 2, 3);
        h.setBlock(belt2, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        TestWater.underBelt(h, belt2);
        ClumpBeltBlockEntity be = (ClumpBeltBlockEntity) h.getBlockEntity(belt2);
        be.accept(Direction.SOUTH, 1, new ItemStack(ModItems.DIRT_CLUMP.get()));
        h.startSequence().thenExecuteAfter(60, () -> {
            h.assertTrue(s2.queued() + s2.tray().size() >= 1 || s2.queued() == 1, "the belt did not deliver into the chute");
        }).thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------ water and state

    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void waterPortsAndStatusesOnAllParts(GameTestHelper h) {
        clear(h);
        BlockPos back = new BlockPos(10, 1, 2);
        SluiceBoxBlockEntity s = longSluice(h, back, Direction.NORTH);
        BlockPos fr = front(back, Direction.NORTH), ch = back.above();
        for (BlockPos part : new BlockPos[]{back, fr, ch}) {
            BlockState st = h.getBlockState(part);
            SluiceBoxBlock b = (SluiceBoxBlock) st.getBlock();
            for (Direction d : Direction.values()) {
                boolean port = part.equals(back) && (d == Direction.EAST || d == Direction.WEST);
                h.assertTrue(b.hasWaterConnection(st, d) == port, part + " port on " + d + " should be " + port);
            }
        }
        BlockPos in = new BlockPos(11, 1, 2);
        h.setBlock(in.north(), Blocks.STONE);
        h.setBlock(in.south(), Blocks.STONE);
        h.setBlock(in.east(), Blocks.STONE);
        h.setBlock(in, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        ServerPlayer p = player(h);
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    h.assertTrue(s.isWaterPowered(), "the back part's side port is not powered");
                    h.assertTrue(h.getBlockState(back).getValue(SluiceBoxBlock.STATUS) == SluiceStatus.IDLE, "no clumps yet: idle");
                    s.addClumps(2, DirtTier.DIRT);
                    ((SluiceBoxBlock) LONG).refreshStatus(h.getLevel(), h.absolutePos(ch), h.getBlockState(ch));
                    for (BlockPos part : new BlockPos[]{back, fr, ch}) {
                        h.assertTrue(h.getBlockState(part).getValue(SluiceBoxBlock.STATUS) == SluiceStatus.WORKING, part + " not working");
                        h.assertTrue(h.getBlockState(part).getValue(SluiceBoxBlock.DIRT), part + " shows no dirt");
                    }
                })
                .thenExecuteAfter(60, () -> {
                    h.assertTrue(s.queued() == 1 && !s.tray().isEmpty() || s.queued() == 1, "one clump should have been washed after 60 ticks: " + s.queued());
                    for (int i = 0; i < 17; i++) s.addToTray(new ItemStack(i % 2 == 0 ? Items.STICK : Items.APPLE, 64));
                    s.addToTray(new ItemStack(Items.BONE));
                    ((SluiceBoxBlock) LONG).refreshStatus(h.getLevel(), h.absolutePos(back), h.getBlockState(back));
                    for (BlockPos part : new BlockPos[]{back, fr, ch}) {
                        h.assertTrue(h.getBlockState(part).getValue(SluiceBoxBlock.STATUS) == SluiceStatus.STOPPED, part + " not stopped with a full tray");
                    }
                    s.takeTray();
                    ((SluiceBoxBlock) LONG).setWaterPowered(h.getLevel(), h.absolutePos(back), h.getBlockState(back), false);
                    for (BlockPos part : new BlockPos[]{back, fr, ch}) {
                        h.assertTrue(h.getBlockState(part).getValue(SluiceBoxBlock.STATUS) == SluiceStatus.NO_POWER, part + " not unpowered");
                    }
                    // empty hand on any part shows the status and empties the tray
                    s.addToTray(new ItemStack(Items.STICK, 3));
                    click(h, p, fr, ItemStack.EMPTY, false, Direction.UP);
                    h.assertTrue(s.tray().isEmpty() && count(p, Items.STICK) == 3, "empty-hand click on the front part did not hand out the tray");
                    s.addToTray(new ItemStack(Items.STICK, 2));
                    click(h, p, ch, ItemStack.EMPTY, false, Direction.UP);
                    h.assertTrue(s.tray().isEmpty() && count(p, Items.STICK) >= 2, "empty-hand click on the chute did not hand out the tray");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty_long")
    public static void trayEmptiesOntoABeltAtTheFrontEnd(GameTestHelper h) {
        clear(h);
        BlockPos back = new BlockPos(10, 1, 3);
        SluiceBoxBlockEntity s = longSluice(h, back, Direction.NORTH);
        BlockPos belt = new BlockPos(10, 1, 1);
        h.setBlock(belt, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        TestWater.underBelt(h, belt);
        BlockPos fr = front(back, Direction.NORTH);
        h.assertTrue(ClumpBeltBlock.feeds(h.getLevel(), h.absolutePos(fr), Direction.NORTH), "the front part feeds the belt");
        h.assertTrue(!ClumpBeltBlock.feeds(h.getLevel(), h.absolutePos(back), Direction.NORTH)
                && !ClumpBeltBlock.feeds(h.getLevel(), h.absolutePos(back.above()), Direction.NORTH), "only the front part may feed a belt");
        s.addToTray(new ItemStack(Items.STICK, 2));
        s.pushTrayToBelt(h.getLevel(), h.absolutePos(back), h.getBlockState(back));
        ClumpBeltBlockEntity be = (ClumpBeltBlockEntity) h.getBlockEntity(belt);
        h.assertTrue(s.tray().isEmpty() || s.tray().get(0).getCount() < 2, "nothing moved onto the belt at the front end: " + s.tray());
        h.assertTrue(be != null, "belt missing");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ inserts

    @GameTest(template = "empty_long")
    public static void twoInsertsInstallRefuseDuplicatesAndComeBack(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos back = new BlockPos(10, 1, 2);
        longSluice(h, back, Direction.NORTH);
        BlockPos fr = front(back, Direction.NORTH), ch = back.above();
        ItemStack riffle = new ItemStack(ModItems.RIFFLE_INSERT.get(), 2);
        click(h, p, fr, riffle, false, Direction.UP);
        h.assertTrue(h.getBlockState(back).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.RIFFLE && riffle.getCount() == 1,
                "riffle not installed from the front part");
        // the same insert twice is refused, nothing used up
        click(h, p, ch, riffle, false, Direction.UP);
        h.assertTrue(h.getBlockState(back).getValue(LongSluiceBlock.UPGRADE2) == SluiceUpgrade.NONE && riffle.getCount() == 1, "the same insert went in twice");
        ItemStack mesh = new ItemStack(ModItems.FINE_MESH_SCREEN.get());
        click(h, p, ch, mesh, false, Direction.UP);
        h.assertTrue(h.getBlockState(back).getValue(LongSluiceBlock.UPGRADE2) == SluiceUpgrade.MESH && mesh.getCount() == 0, "second insert not installed");
        // all three parts show both
        for (BlockPos part : new BlockPos[]{back, fr, ch}) {
            BlockState st = h.getBlockState(part);
            h.assertTrue(st.getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.RIFFLE && st.getValue(LongSluiceBlock.UPGRADE2) == SluiceUpgrade.MESH, part + " does not show both inserts");
        }
        // a third is refused
        ItemStack seed = new ItemStack(ModItems.SEED_SCREEN.get());
        click(h, p, back, seed, false, Direction.UP);
        h.assertTrue(seed.getCount() == 1 && h.getBlockState(back).getValue(LongSluiceBlock.UPGRADE2) == SluiceUpgrade.MESH, "a third insert went in");
        // sneak + empty hand gives each back (last in first), from any part
        click(h, p, fr, ItemStack.EMPTY, true, Direction.UP);
        h.assertTrue(count(p, ModItems.FINE_MESH_SCREEN.get()) == 1 && h.getBlockState(ch).getValue(LongSluiceBlock.UPGRADE2) == SluiceUpgrade.NONE, "mesh not given back");
        click(h, p, ch, ItemStack.EMPTY, true, Direction.UP);
        h.assertTrue(count(p, ModItems.RIFFLE_INSERT.get()) == 1 && h.getBlockState(back).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.NONE, "riffle not given back");
        // the Starter sluice still has one slot
        h.setBlock(new BlockPos(20, 1, 2), ModBlocks.SLUICE_BOX.get().defaultBlockState());
        click(h, p, new BlockPos(20, 1, 2), new ItemStack(ModItems.RIFFLE_INSERT.get()), false, Direction.UP);
        ItemStack second = new ItemStack(ModItems.FINE_MESH_SCREEN.get());
        click(h, p, new BlockPos(20, 1, 2), second, false, Direction.UP);
        h.assertTrue(second.getCount() == 1 && h.getBlockState(new BlockPos(20, 1, 2)).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.RIFFLE,
                "the Starter sluice took a second insert");
        h.succeed();
    }

    private static final Set<Item> SEEDS = new HashSet<>();

    static {
        SEEDS.add(ModItems.GLOWROOT_SEEDS.get());
        SEEDS.add(ModItems.RIVERBEAN_SEEDS.get());
        SEEDS.add(ModItems.DUNE_MELON_SEEDS.get());
        SEEDS.add(Items.WHEAT_SEEDS);
        SEEDS.add(Items.CARROT);
        SEEDS.add(Items.POTATO);
        SEEDS.add(Items.BEETROOT_SEEDS);
    }

    /** {value, seeds} of {@code washes} clumps of {@code kind} through a long sluice holding these inserts. */
    private static double[] wash(GameTestHelper h, SluiceUpgrade a, SluiceUpgrade b, int kind, int washes) {
        BlockPos rel = new BlockPos(3, 1, 2);
        h.setBlock(rel, LONG.defaultBlockState().setValue(SluiceBoxBlock.UPGRADE, a).setValue(LongSluiceBlock.UPGRADE2, b));
        BlockPos abs = h.absolutePos(rel);
        ServerLevel level = h.getLevel();
        var reg = level.registryAccess();
        double value = 0, seeds = 0;
        for (int i = 0; i < washes; i++) {
            var be = (SluiceBoxBlockEntity) level.getBlockEntity(abs);
            be.addClumps(1, kind);
            be.washOneClump(level, abs);
            for (ItemStack s : be.takeTray()) {
                value += Selling.stackValue(s, reg);
                if (SEEDS.contains(s.getItem())) seeds += s.getCount();
            }
        }
        return new double[]{value, seeds};
    }

    @GameTest(template = "empty_long", timeoutTicks = 600)
    public static void bothInsertsApplyTogether(GameTestHelper h) {
        clear(h);
        int n = 10000;
        SluiceUpgrade none = SluiceUpgrade.NONE;
        double gravel = wash(h, none, none, DirtTier.GRAVEL, n)[0];
        double sand = wash(h, none, none, DirtTier.SAND, n)[0];
        double dirt = wash(h, none, none, DirtTier.DIRT, n)[0];
        double gravelRM = wash(h, SluiceUpgrade.RIFFLE, SluiceUpgrade.MESH, DirtTier.GRAVEL, n)[0];
        double sandRM = wash(h, SluiceUpgrade.RIFFLE, SluiceUpgrade.MESH, DirtTier.SAND, n)[0];
        double dirtRM = wash(h, SluiceUpgrade.RIFFLE, SluiceUpgrade.MESH, DirtTier.DIRT, n)[0];
        String report = String.format("riffle+mesh: gravel %.2fx, sand %.2fx, dirt %.2fx", gravelRM / gravel, sandRM / sand, dirtRM / dirt);
        HoleyDigsite.LOGGER.info("PHASE29 {}", report);
        h.assertTrue(Math.abs(gravelRM / gravel - 1.25) <= 0.10, "riffle in a pair should still give ~+25% on gravel: " + report);
        h.assertTrue(Math.abs(sandRM / sand - 1.25) <= 0.10, "mesh in a pair should still give ~+25% on sand: " + report);
        h.assertTrue(Math.abs(dirtRM / dirt - 1.0) <= 0.10, "neither changes dirt: " + report);
        double[] riffleSeed = wash(h, SluiceUpgrade.RIFFLE, SluiceUpgrade.SEED, DirtTier.GRAVEL, n);
        h.assertTrue(Math.abs(riffleSeed[0] / gravel - 1.25) <= 0.10, "riffle + seed screen should keep the +25% on gravel: " + riffleSeed[0] / gravel);
        h.assertTrue(riffleSeed[1] > n / 30.0, "the seed screen next to a riffle gave too few seeds: " + riffleSeed[1]);
        double[] seedOnly = wash(h, SluiceUpgrade.SEED, none, DirtTier.GRAVEL, n);
        h.assertTrue(seedOnly[1] > n / 30.0, "the seed screen alone gave too few seeds: " + seedOnly[1]);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ old Tier 2 sluices

    private static CompoundTag oldSave(GameTestHelper h, BlockPos back) {
        SluiceBoxBlockEntity s = (SluiceBoxBlockEntity) h.getBlockEntity(back);
        CompoundTag tag = s.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(tag.getInt("Layout") != 0, "new saves must carry the layout marker");
        tag.remove("Layout");
        return tag;
    }

    /** What a one-block Tier 2 sluice of an old world looks like when loaded: the back part alone, a block entity without Layout. */
    private static SluiceBoxBlockEntity oldSluice(GameTestHelper h, BlockPos back, ServerPlayer owner, boolean withOwner) {
        SluiceBoxBlockEntity s = longSluice(h, back, Direction.NORTH);
        s.addClumps(3, DirtTier.GRAVEL);
        s.addToTray(new ItemStack(Items.STICK, 5));
        if (withOwner) s.setOwnerPlayer(owner);
        BlockState st = h.getBlockState(back);
        ((LongSluiceBlock) st.getBlock()).installUpgrade(SluiceUpgrade.RIFFLE, new ItemStack(ModItems.RIFFLE_INSERT.get()), st, h.getLevel(), h.absolutePos(back), owner);
        CompoundTag tag = oldSave(h, back);
        SluiceBoxBlockEntity be = (SluiceBoxBlockEntity) h.getBlockEntity(back);
        be.loadCustomOnly(tag, h.getLevel().registryAccess());
        h.assertTrue(be.isOldLayout(), "a save without Layout should count as an old sluice");
        // an old world has the back part alone
        h.getLevel().setBlock(h.absolutePos(back.relative(Direction.NORTH)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        h.getLevel().setBlock(h.absolutePos(back.above()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return be;
    }

    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void oldTier2SluiceBecomesAChestWithEverything(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos back = new BlockPos(10, 1, 2);
        oldSluice(h, back, p, true);
        // neighbours updating must not take the lone block away before its turn
        h.getLevel().neighborShapeChanged(Direction.NORTH, Blocks.AIR.defaultBlockState(), h.absolutePos(back), h.absolutePos(back.north()), 3, 0);
        h.assertTrue(h.getBlockState(back).is(LONG), "the old block vanished before it was handed back");
        h.getLevel().scheduleTick(h.absolutePos(back), LONG, 1);
        h.startSequence().thenExecuteAfter(4, () -> {
            h.assertTrue(h.getBlockState(back).is(Blocks.CHEST), "the old sluice did not become a chest: " + h.getBlockState(back));
            ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(back);
            int sluices = 0, riffles = 0, sticks = 0, clumps = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                if (s.is(LONG.asItem())) sluices += s.getCount();
                if (s.is(ModItems.RIFFLE_INSERT.get())) riffles += s.getCount();
                if (s.is(Items.STICK)) sticks += s.getCount();
                if (s.getItem() == DirtTier.clumps(DirtTier.GRAVEL, 1).getItem()) clumps += s.getCount();
            }
            h.assertTrue(sluices == 1 && riffles == 1 && sticks == 5 && clumps == 3,
                    "chest holds sluice " + sluices + ", riffle " + riffles + ", sticks " + sticks + ", clumps " + clumps);
            h.assertTrue(itemEntities(h, LONG.asItem()) == 0 && itemEntities(h, Items.STICK) == 0, "things were also dropped");
            h.assertTrue(SluiceRefunds.waitingFor(h.getLevel().getServer(), p.getUUID()).isEmpty(), "the online owner was queued instead of told");
        }).thenSucceed();
    }

    @GameTest(template = "empty_long")
    public static void oldSluiceExtrasDropOnTheChestAndOwnerlessIsSilent(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos back = new BlockPos(10, 1, 2);
        SluiceBoxBlockEntity s = oldSluice(h, back, p, false);
        for (int i = 0; i < 30; i++) s.addToTray(new ItemStack(i % 2 == 0 ? Items.STICK : Items.APPLE, 64));
        s.addToTray(new ItemStack(Items.BONE, 5));
        int before = SluiceRefunds.waitingFor(h.getLevel().getServer(), p.getUUID()).size();
        LongSluiceBlock.refundOld(h.getLevel(), h.absolutePos(back));
        h.assertTrue(h.getBlockState(back).is(Blocks.CHEST), "no chest");
        ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(back);
        int used = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (!chest.getItem(i).isEmpty()) used++;
        h.assertTrue(used == chest.getContainerSize(), "the chest should be full: " + used);
        h.assertTrue(itemEntities(h, Items.STICK) + itemEntities(h, Items.APPLE) + itemEntities(h, Items.BONE) > 0, "the rest should drop on top");
        h.assertTrue(SluiceRefunds.waitingFor(h.getLevel().getServer(), p.getUUID()).size() == before, "an ownerless sluice sent a message");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void offlineOwnerGetsTheLineAtNextLogin(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        java.util.UUID away = java.util.UUID.randomUUID();
        BlockPos back = new BlockPos(10, 1, 2);
        SluiceBoxBlockEntity s = oldSluice(h, back, p, false);
        s.setOwner(away);
        LongSluiceBlock.refundOld(h.getLevel(), h.absolutePos(back));
        BlockPos abs = h.absolutePos(back);
        List<String> waiting = SluiceRefunds.waitingFor(h.getLevel().getServer(), away);
        h.assertTrue(waiting.size() == 1 && waiting.get(0).equals(abs.getX() + " " + abs.getY() + " " + abs.getZ()), "offline owner not noted: " + waiting);
        h.assertTrue(SluiceRefunds.line(waiting.get(0)).getString().contains("Tier 2 Sluice") && SluiceRefunds.line(waiting.get(0)).getString().contains(waiting.get(0)),
                "line text: " + SluiceRefunds.line(waiting.get(0)).getString());
        // the note is handed over (and forgotten) when that player logs in
        SluiceRefunds.queue(h.getLevel().getServer(), p.getUUID(), abs);
        h.assertTrue(SluiceRefunds.waitingFor(h.getLevel().getServer(), p.getUUID()).size() == 1, "queue");
        SluiceRefunds.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
        h.assertTrue(SluiceRefunds.waitingFor(h.getLevel().getServer(), p.getUUID()).isEmpty(), "note kept after login");
        h.assertTrue(SluiceRefunds.waitingFor(h.getLevel().getServer(), away).size() == 1, "another player's note was taken");
        h.succeed();
    }

    @GameTest(template = "empty_long", timeoutTicks = 60)
    public static void starterSluiceIsNeverTouched(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos rel = new BlockPos(10, 1, 2);
        h.setBlock(rel, ModBlocks.SLUICE_BOX.get().defaultBlockState());
        SluiceBoxBlockEntity s = (SluiceBoxBlockEntity) h.getBlockEntity(rel);
        s.setOwnerPlayer(p);
        s.addClumps(2, DirtTier.DIRT);
        CompoundTag tag = s.saveWithFullMetadata(h.getLevel().registryAccess());
        tag.remove("Layout");
        s.loadCustomOnly(tag, h.getLevel().registryAccess());
        h.assertTrue(!s.isOldLayout(), "a Starter sluice must not count as old");
        h.getLevel().scheduleTick(h.absolutePos(rel), ModBlocks.SLUICE_BOX.get(), 1);
        h.startSequence().thenExecuteAfter(4, () -> {
            h.assertTrue(h.getBlockState(rel).is(ModBlocks.SLUICE_BOX.get()) && s.queued() == 2, "the Starter sluice changed");
            LongSluiceBlock.refundOld(h.getLevel(), h.absolutePos(rel));
            h.assertTrue(h.getBlockState(rel).is(ModBlocks.SLUICE_BOX.get()), "refundOld touched a Starter sluice");
        }).thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------ skins

    @GameTest(template = "empty_long")
    public static void skinsReachAllThreePartsAndComeBackOnTheItem(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        ItemStack item = new ItemStack(LONG.asItem());
        item.set(ModDataComponents.SKIN.get(), "rustic");
        click(h, p, new BlockPos(10, 0, 3), item, false, Direction.UP);
        BlockPos back = new BlockPos(10, 1, 3);
        SluiceBoxBlockEntity s = (SluiceBoxBlockEntity) h.getBlockEntity(back);
        h.assertTrue(s != null && "rustic".equals(s.getSkin()), "skin not applied on placing");
        for (BlockPos part : new BlockPos[]{back, back.north(), back.above()}) {
            SluiceBoxBlockEntity owner = SluiceBoxBlock.entityAt(h.getLevel(), h.absolutePos(part));
            h.assertTrue(owner == s && owner.getSkinKey().startsWith("rustic"), part + " does not read the skin");
        }
        p.gameMode.destroyBlock(h.absolutePos(back.above()));
        ItemStack dropped = h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(i -> i.is(LONG.asItem())).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue("rustic".equals(dropped.get(ModDataComponents.SKIN.get())), "the skin was lost when breaking the chute");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void longSluiceNumbersAndKind(GameTestHelper h) {
        SluiceBoxBlock.Stats t2 = SluiceBoxBlock.stats(LONG.defaultBlockState());
        h.assertTrue(t2.cycleTicks() == 50 && t2.maxQueue() == 16 && t2.traySlots() == 18, "long sluice stats " + t2);
        h.assertTrue(LONG instanceof LongSluiceBlock && !(ModBlocks.SLUICE_BOX.get() instanceof LongSluiceBlock), "kinds");
        h.assertTrue(((SluiceBoxBlock) LONG).insertSlots() == 2 && ((SluiceBoxBlock) ModBlocks.SLUICE_BOX.get()).insertSlots() == 1, "insert slots");
        h.assertTrue(LONG.defaultBlockState().getValue(SluiceBoxBlock.FACING) != null, "state");
        h.succeed();
    }
}
