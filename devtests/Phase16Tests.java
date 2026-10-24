package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.ProspectorPickItem;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.BuyPadBlock;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.sluice.SluiceUpgrade;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Phase 16 (sandbox only): plot-exclusive shop items - catalogue site check at the Buy Pad, Prospector's Pick, sluice upgrades
 * (install / one slot / remove / drops / loot boost) and the lamps. Template {@code empty_long} (26x5x5).
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase16Tests {

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 26; x++) for (int y = 1; y <= 4; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 0; x < 26; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    private static void click(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held, boolean sneak) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(sneak);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
        p.setShiftKeyDown(false);
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static int itemEntities(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    // ------------------------------------------------------------------------------------------------ catalogue / Buy Pad

    @GameTest(template = "empty_long")
    public static void catalogueEntriesAreExclusive(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        Object[][] rows = {{ModItems.PROSPECTORS_PICK.get(), "deep_dig"}, {ModItems.RIFFLE_INSERT.get(), "gravel_flats"},
                {ModItems.FINE_MESH_SCREEN.get(), "sand_dunes"}, {ModItems.MINERS_LANTERN.get(), "deep_dig"},
                {ModItems.RIVER_STONE_LAMP.get(), "gravel_flats"}, {ModItems.SANDSTONE_LAMP.get(), "sand_dunes"}};
        for (Object[] r : rows) {
            var e = ShopCatalogue.find(reg, (Item) r[0]);
            h.assertTrue(e.isPresent(), r[0] + " not in the catalogue");
            h.assertTrue(e.get().site().equals(java.util.Optional.of((String) r[1])), r[0] + " has the wrong site: " + e.get().site());
            h.assertTrue(e.get().availableAt((String) r[1]) && !e.get().availableAt(null) && !e.get().availableAt("other"), r[0] + " availability");
        }
        h.assertTrue(ShopCatalogue.find(reg, ModItems.PAN.get()).get().site().isEmpty(), "pan must stay unrestricted");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void exclusiveBlueprintAcceptedAtItsKioskRefusedAtHome(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        PlotData data = PlotData.get(level);
        BlockPos board = h.absolutePos(new BlockPos(1, 1, 1));
        BoundingBox box = new BoundingBox(board.getX() - 1, board.getY() - 1, board.getZ() - 1, board.getX() + 8, board.getY() + 4, board.getZ() + 4);
        data.register("deep_dig", board, box);
        data.setOwned("deep_dig", board, true);
        try {
            BlockPos kiosk = new BlockPos(3, 1, 2), home = new BlockPos(20, 1, 2);
            h.setBlock(kiosk, ModBlocks.BUY_PAD.get());
            h.setBlock(home, ModBlocks.BUY_PAD.get());
            BuyPadBlock pad = (BuyPadBlock) ModBlocks.BUY_PAD.get();
            ServerPlayer p = player(h);
            ItemStack pick = BlueprintItem.of(ModItems.PROSPECTORS_PICK.get());
            h.assertTrue(pad.placeHeld(level, h.absolutePos(kiosk), p, pick.copy()) == 1, "kiosk pad refused its own plot's blueprint");
            h.assertTrue(pad.placeHeld(level, h.absolutePos(home), p, pick.copy()) == 0, "home pad accepted an exclusive blueprint");
            // a blueprint of another plot is refused at this kiosk too
            ItemStack riffle = BlueprintItem.of(ModItems.RIFFLE_INSERT.get());
            h.assertTrue(pad.placeHeld(level, h.absolutePos(kiosk), p, riffle) == 0, "deep dig kiosk accepted a Gravel Flats blueprint");
            // ordinary blueprints work everywhere
            ItemStack pan = BlueprintItem.of(ModItems.PAN.get());
            h.assertTrue(pad.placeHeld(level, h.absolutePos(home), p, pan.copy()) == 1, "home pad refused an ordinary blueprint");
            h.assertTrue(pad.placeHeld(level, h.absolutePos(kiosk), p, pan.copy()) == 1, "kiosk pad refused an ordinary blueprint");
            // thrown / bulk paths use the same check
            ServerPlayer q = player(h);
            q.getInventory().items.set(9, pick.copy());
            h.assertTrue(pad.placeInventory(level, h.absolutePos(home), q) == 0, "bulk put at home took the exclusive blueprint");
            h.assertTrue(((PadBlockEntity) level.getBlockEntity(h.absolutePos(home))).contents().stream().noneMatch(s -> s.is(ModItems.BLUEPRINT.get())
                    && BlueprintItem.target(s) == ModItems.PROSPECTORS_PICK.get()), "exclusive blueprint ended up on the home pad");
            h.succeed();
        } finally {
            data.remove("deep_dig", board);
        }
    }

    // ------------------------------------------------------------------------------------------------ Prospector's Pick

    @GameTest(template = "empty_long")
    public static void pickFindsBuriedRockOrNothing(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(12, 1, 2));
        h.assertTrue(ProspectorPickItem.scan(level, origin) == null, "found rock in an empty test area");
        h.setBlock(new BlockPos(12, 0, 2), ModBlocks.DEEP_DIRTROCK.get());   // 1 below the origin: Deep, straight down
        // (the test floor is at y=0 so the rock is the only tagged block)
        var f = ProspectorPickItem.scan(level, origin);
        h.assertTrue(f != null && f.kind() == 2 && f.dy() == -1, "deep rock under the click not found: " + f);
        BlockPos far = new BlockPos(20, 0, 3);                               // 8 east, 1 south, 1 down: Abyssal, farther
        h.setBlock(far, ModBlocks.ABYSSAL_DIRTROCK.get());
        f = ProspectorPickItem.scan(level, origin);
        h.assertTrue(f != null && f.kind() == 2, "nearest (the deep rock) should win: " + f);
        h.setBlock(new BlockPos(12, 0, 2), Blocks.STONE);
        f = ProspectorPickItem.scan(level, origin);
        h.assertTrue(f != null && f.kind() == 3 && f.dx() == 8 && f.dz() == 1, "abyssal rock 8 blocks east not found: " + f);
        h.assertTrue(ProspectorPickItem.direction(f).getString().contains("east"), "direction should be east: " + ProspectorPickItem.direction(f).getString());
        h.setBlock(new BlockPos(24, 1, 2), ModBlocks.DEEP_DIRTROCK.get());    // out of range (12 east) stays unseen once the near one is gone
        h.setBlock(far, Blocks.STONE);
        f = ProspectorPickItem.scan(level, origin);
        h.assertTrue(f != null && f.dx() == 12, "rock exactly 12 away should still be found: " + f);
        h.setBlock(new BlockPos(24, 1, 2), Blocks.STONE);
        h.setBlock(new BlockPos(25, 1, 2), ModBlocks.DEEP_DIRTROCK.get());    // 13 away
        h.assertTrue(ProspectorPickItem.scan(level, origin) == null, "rock 13 blocks away must not be found");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void pickUsesCooldownAndNeverBreaks(GameTestHelper h) {
        clear(h);
        h.setBlock(new BlockPos(5, 0, 2), ModBlocks.ABYSSAL_DIRTROCK.get());
        ServerPlayer p = player(h);
        ItemStack pick = new ItemStack(ModItems.PROSPECTORS_PICK.get());
        h.assertTrue(!pick.isDamageableItem(), "the pick must not have durability");
        click(h, p, new BlockPos(6, 0, 2), pick, false);
        h.assertTrue(p.getCooldowns().isOnCooldown(pick.getItem()), "no cooldown after use");
        h.assertTrue(!p.getMainHandItem().isEmpty(), "pick consumed");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ sluice upgrades

    private static BlockPos sluice(GameTestHelper h, Block block, int x) {
        BlockPos rel = new BlockPos(x, 1, 2);
        h.setBlock(rel, block.defaultBlockState());
        return rel;
    }

    @GameTest(template = "empty_long")
    public static void insertInstallsOneAtATimeAndRemoves(GameTestHelper h) {
        clear(h);
        for (Block b : new Block[]{ModBlocks.SLUICE_BOX.get(), ModBlocks.SLUICE_BOX_T2.get()}) {
            BlockPos rel = sluice(h, b, 3);
            ServerPlayer p = player(h);
            p.getInventory().items.set(9, new ItemStack(ModItems.FINE_MESH_SCREEN.get()));
            click(h, p, rel, new ItemStack(ModItems.RIFFLE_INSERT.get(), 2), false);
            BlockState s = h.getBlockState(rel);
            h.assertTrue(s.getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.RIFFLE, "riffle not installed on " + b);
            h.assertTrue(p.getMainHandItem().getCount() == 1, "one insert should be used up");
            // second upgrade refused, nothing consumed, still riffle
            ItemStack mesh = new ItemStack(ModItems.FINE_MESH_SCREEN.get());
            click(h, p, rel, mesh, false);
            h.assertTrue(h.getBlockState(rel).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.RIFFLE, "second upgrade replaced the first");
            h.assertTrue(mesh.getCount() == 1, "refused upgrade was consumed");
            // sneak + empty hand removes and returns it
            click(h, p, rel, ItemStack.EMPTY, true);
            h.assertTrue(h.getBlockState(rel).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.NONE, "not removed");
            h.assertTrue(count(p, ModItems.RIFFLE_INSERT.get()) == 1, "removed insert not returned");
            // plain empty-hand click (no sneak) keeps the upgrade
            click(h, p, rel, new ItemStack(ModItems.FINE_MESH_SCREEN.get()), false);
            click(h, p, rel, ItemStack.EMPTY, false);
            h.assertTrue(h.getBlockState(rel).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.MESH, "normal click must not remove the upgrade");
            // breaking drops it
            h.getLevel().removeBlock(h.absolutePos(rel), false);
            h.assertTrue(itemEntities(h, ModItems.FINE_MESH_SCREEN.get()) >= 1, "installed upgrade not dropped on break");
            h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        }
        h.succeed();
    }

    /** Total sell value (uncut as panned) of {@code washes} clumps of {@code kind} through a sluice with {@code upgrade}. */
    private static double washValue(GameTestHelper h, Block block, SluiceUpgrade upgrade, int kind, int washes) {
        BlockPos rel = new BlockPos(3, 1, 2);
        h.setBlock(rel, block.defaultBlockState().setValue(SluiceBoxBlock.UPGRADE, upgrade));
        BlockPos abs = h.absolutePos(rel);
        ServerLevel level = h.getLevel();
        var reg = level.registryAccess();
        double total = 0;
        for (int i = 0; i < washes; i++) {
            var be = (SluiceBoxBlockEntity) level.getBlockEntity(abs);
            be.addClumps(1, kind);
            be.washOneClump(level, abs);
            for (ItemStack s : be.takeTray()) {
                total += Selling.stackValue(s, reg);
            }
        }
        return total;
    }

    @GameTest(template = "empty_long", timeoutTicks = 600)
    public static void upgradesBoostOnlyTheirClumpKind(GameTestHelper h) {
        clear(h);
        int n = 12000;
        Block b = ModBlocks.SLUICE_BOX_T2.get();
        double gravel = washValue(h, b, SluiceUpgrade.NONE, DirtTier.GRAVEL, n);
        double gravelRiffle = washValue(h, b, SluiceUpgrade.RIFFLE, DirtTier.GRAVEL, n);
        double sand = washValue(h, b, SluiceUpgrade.NONE, DirtTier.SAND, n);
        double sandMesh = washValue(h, b, SluiceUpgrade.MESH, DirtTier.SAND, n);
        double sandRiffle = washValue(h, b, SluiceUpgrade.RIFFLE, DirtTier.SAND, n);
        double gravelMesh = washValue(h, b, SluiceUpgrade.MESH, DirtTier.GRAVEL, n);
        double dirt = washValue(h, b, SluiceUpgrade.NONE, DirtTier.DIRT, n);
        double dirtRiffle = washValue(h, b, SluiceUpgrade.RIFFLE, DirtTier.DIRT, n);
        String report = String.format("gravel %.0f -> riffle %.0f (%.2fx); sand %.0f -> mesh %.0f (%.2fx); riffle on sand %.2fx; mesh on gravel %.2fx; riffle on dirt %.2fx",
                gravel / n, gravelRiffle / n, gravelRiffle / gravel, sand / n, sandMesh / n, sandMesh / sand, sandRiffle / sand, gravelMesh / gravel, dirtRiffle / dirt);
        HoleyDigsite.LOGGER.info("PHASE16 {}", report);
        h.assertTrue(gravel > 0 && sand > 0 && dirt > 0, "no loot at all");
        h.assertTrue(near(gravelRiffle / gravel, 1.25, 0.10), "riffle on gravel should be ~+25%: " + report);
        h.assertTrue(near(sandMesh / sand, 1.25, 0.10), "mesh on sand should be ~+25%: " + report);
        h.assertTrue(near(sandRiffle / sand, 1.0, 0.10), "riffle must not change sand: " + report);
        h.assertTrue(near(gravelMesh / gravel, 1.0, 0.10), "mesh must not change gravel: " + report);
        h.assertTrue(near(dirtRiffle / dirt, 1.0, 0.10), "riffle must not change dirt: " + report);
        h.succeed();
    }

    private static boolean near(double v, double target, double tol) {
        return Math.abs(v - target) <= tol;
    }

    @GameTest(template = "empty_long")
    public static void meshAddsTopazAtLeastAsOften(GameTestHelper h) {
        clear(h);
        BlockPos rel = new BlockPos(3, 1, 2);
        h.setBlock(rel, ModBlocks.SLUICE_BOX_T2.get().defaultBlockState().setValue(SluiceBoxBlock.UPGRADE, SluiceUpgrade.MESH));
        BlockPos abs = h.absolutePos(rel);
        ServerLevel level = h.getLevel();
        int topaz = 0;
        for (int i = 0; i < 12000; i++) {
            var be = (SluiceBoxBlockEntity) level.getBlockEntity(abs);
            be.addClumps(1, DirtTier.SAND);
            be.washOneClump(level, abs);
            for (ItemStack s : be.takeTray()) {
                if (s.getItem().toString().contains("topaz")) topaz += s.getCount();
            }
        }
        // base 5% + 2% bonus + extra washes (~24% of 5%) -> about 8.2% of clumps: expect well above the 5% base
        h.assertTrue(topaz > 12000 * 0.065, "mesh topaz count too low: " + topaz);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ lamps

    @GameTest(template = "empty_long")
    public static void lampsPlaceLightAndDropThemselves(GameTestHelper h) {
        clear(h);
        Block[] lamps = {ModBlocks.MINERS_LANTERN.get(), ModBlocks.RIVER_STONE_LAMP.get(), ModBlocks.SANDSTONE_LAMP.get()};
        for (int i = 0; i < lamps.length; i++) {
            BlockPos rel = new BlockPos(2 + 2 * i, 1, 2);
            h.setBlock(rel, lamps[i]);
            BlockState s = h.getBlockState(rel);
            h.assertTrue(s.is(lamps[i]) && s.getLightEmission() == 15, lamps[i] + " does not place with light 15");
            List<ItemStack> drops = Block.getDrops(s, h.getLevel(), h.absolutePos(rel), null);
            h.assertTrue(drops.stream().anyMatch(d -> d.getItem() == s.getBlock().asItem()), lamps[i] + " does not drop itself: " + drops);
        }
        h.succeed();
    }
}
