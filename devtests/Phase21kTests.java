package com.holeydigsite.devtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.shop.custom.CustomEntry;
import com.holeydigsite.shop.custom.CustomShops;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 21k (sandbox only):
 * <ul>
 *   <li>Trading Post automatically places missing display blocks from the structure template in existing worlds and populates them without a rebuild</li>
 *   <li>Player-built blocks are never overwritten by ensureDisplays</li>
 *   <li>Plot kiosks automatically place missing display blocks and update layout</li>
 *   <li>Drop Chute model has closed corners (no vertical slits), seamless rim pieces, and zero coplanar overlapping faces (no z-fighting)</li>
 * </ul>
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21kTests {
    private static final ResourceLocation HOME = HoleyDigsite.id("home");
    private static final ResourceLocation DEEP_DIG_SHOP = HoleyDigsite.id("deep_dig_shop");

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
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

    private static Map<Integer, ShopDisplayBlockEntity> displayEntities(ServerLevel l, BoundingBox zone) {
        Map<Integer, ShopDisplayBlockEntity> m = new HashMap<>();
        for (ShopDisplayBlockEntity d : bes(l, zone, ShopDisplayBlockEntity.class)) {
            if (d.slot() > 0) m.put(d.slot(), d);
        }
        return m;
    }

    private static Map<Integer, Item> displays(ServerLevel l, BoundingBox zone) {
        Map<Integer, Item> m = new HashMap<>();
        for (ShopDisplayBlockEntity d : bes(l, zone, ShopDisplayBlockEntity.class)) {
            if (d.slot() > 0) m.put(d.slot(), d.item());
        }
        return m;
    }

    /**
     * 1. Real path: Trading Post in existing world with missing displays (slots 34 and 35) automatically
     * places the display blocks on load and populates them with Down Conveyor Belt and Drop Chute without a rebuild.
     */
    @GameTest(template = "empty", batch = "p21k_trading_post", timeoutTicks = 100)
    public static void tradingPostAutoPlacesMissingDisplaysAndUpdatesLayout(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(41);
        prepare(l, a);
        try {
            SiteDef d = def(HOME);
            // Build the store initially
            Sites.Result r = Sites.build(l, d, a, null, false);
            BoundingBox z = r.site().zone();

            // Locate displays 34, 35 and 36
            Map<Integer, ShopDisplayBlockEntity> des = displayEntities(l, z);
            check(h, des.containsKey(34) && des.containsKey(35) && des.containsKey(36), "initial store has slots 34, 35 and 36");
            BlockPos p34 = des.get(34).getBlockPos().immutable();
            BlockPos p35 = des.get(35).getBlockPos().immutable();
            BlockPos p36 = des.get(36).getBlockPos().immutable();

            // Simulate real old world: remove displays 34, 35 and 36 (set to AIR)
            l.setBlock(p34, Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(p35, Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(p36, Blocks.AIR.defaultBlockState(), 3);

            // Revert SiteData to 33 items (pre-21i state)
            SiteData data = SiteData.get(l);
            SiteData.Site oldSite = data.get(d.id(), a);
            check(h, oldSite != null, "site data found");
            List<String> pre21iShown = new ArrayList<>(oldSite.shown());
            pre21iShown.remove(BuiltInRegistries.ITEM.getKey(ModItems.DOWN_INCLINE_BELT.get()).toString());
            pre21iShown.remove(BuiltInRegistries.ITEM.getKey(ModItems.DROP_CHUTE.get()).toString());
            pre21iShown.remove(BuiltInRegistries.ITEM.getKey(ModItems.SKIN_BENCH.get()).toString());
            pre21iShown.remove(BuiltInRegistries.ITEM.getKey(ModItems.WOODEN_CRATE.get()).toString());
            pre21iShown.remove(BuiltInRegistries.ITEM.getKey(ModItems.IRON_CRATE.get()).toString());
            pre21iShown.remove(BuiltInRegistries.ITEM.getKey(ModItems.GOLD_CRATE.get()).toString());
            check(h, pre21iShown.size() == 33, "pre-21i shown has 33 items: " + pre21iShown.size());
            data.put(new SiteData.Site(oldSite.id(), oldSite.anchor(), oldSite.zone(), oldSite.version(), pre21iShown, oldSite.parent()));

            // Verify the world is now simulating an old world
            check(h, l.getBlockState(p34).isAir(), "slot 34 is air in simulated old world");
            check(h, l.getBlockState(p35).isAir(), "slot 35 is air in simulated old world");
            check(h, l.getBlockState(p36).isAir(), "slot 36 is air in simulated old world");
            check(h, !displays(l, z).containsKey(34) && !displays(l, z).containsKey(35) && !displays(l, z).containsKey(36), "old store missing 34, 35 and 36");

            // Place a player block inside the store to verify no full rebuild occurs
            BlockPos litter = a.offset(15, 11, 10);
            l.setBlock(litter, Blocks.GLASS.defaultBlockState(), 3);

            // Configure a custom 21d display on an unmapped slot in the store to verify custom displays are preserved
            BlockPos customPos = a.offset(10, 11, 10);
            l.setBlock(customPos, ModBlocks.SHOP_DISPLAY.get().defaultBlockState(), 3);
            ShopDisplayBlockEntity customDisp = (ShopDisplayBlockEntity) l.getBlockEntity(customPos);
            check(h, customDisp != null, "custom display entity placed");
            customDisp.setSlot(99);
            CustomShops.set(l, customPos, new ItemStack(ModItems.GOLD_COIN.get()), 1, 100, 50);

            // Execute the real world-load check!
            List<Sites.Result> results = Sites.checkAll(l);
            Sites.Result homeResult = results.stream().filter(res -> res.site() != null && res.site().id().equals(HOME)).findFirst().orElse(null);
            check(h, homeResult != null, "checkAll returned a result for home store");
            check(h, homeResult.newItems().contains(BuiltInRegistries.ITEM.getKey(ModItems.DOWN_INCLINE_BELT.get()).toString()),
                    "added contains down incline belt");
            check(h, homeResult.newItems().contains(BuiltInRegistries.ITEM.getKey(ModItems.DROP_CHUTE.get()).toString()),
                    "added contains drop chute");
            check(h, homeResult.message() != null, "announcement message generated");

            // Verify display blocks 34, 35 and 36 were placed into the world and populated
            Map<Integer, Item> shown = displays(l, z);
            check(h, shown.get(34) == ModItems.DOWN_INCLINE_BELT.get(), "slot 34 shows down incline belt");
            check(h, shown.get(35) == ModItems.DROP_CHUTE.get(), "slot 35 shows drop chute");
            check(h, shown.get(36) == ModItems.SKIN_BENCH.get(), "slot 36 shows skin bench");
            check(h, l.getBlockState(litter).is(Blocks.GLASS), "player litter glass untouched (no rebuild)");

            // Verify SiteData updated to 36 items
            SiteData.Site updatedSite = data.get(d.id(), a);
            check(h, updatedSite != null && updatedSite.shown().size() == 39, "updated site records 39 shown items");

            // Verify custom display preserved
            ShopDisplayBlockEntity postCustom = (ShopDisplayBlockEntity) l.getBlockEntity(customPos);
            check(h, postCustom != null && postCustom.custom() != null && postCustom.custom().price() == 100, "custom display preserved");

            // Running checkAll again should be a no-op
            List<Sites.Result> secondResults = Sites.checkAll(l);
            check(h, secondResults.stream().noneMatch(res -> res.site() != null && res.site().id().equals(HOME)),
                    "second checkAll is a no-op");

            h.succeed();
        } finally {
            cleanup(l, List.of(a));
        }
    }

    /**
     * 2. Player-built blocks are never overwritten by ensureDisplays when auto-placing displays.
     */
    @GameTest(template = "empty", batch = "p21k_player_build", timeoutTicks = 100)
    public static void playerBuiltBlocksNeverOverwrittenByEnsureDisplays(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(42);
        prepare(l, a);
        try {
            SiteDef d = def(HOME);
            Sites.Result r = Sites.build(l, d, a, null, false);
            BoundingBox z = r.site().zone();

            Map<Integer, ShopDisplayBlockEntity> des = displayEntities(l, z);
            BlockPos p34 = des.get(34).getBlockPos().immutable();

            // Player builds an obsidian block at slot 34's location
            l.setBlock(p34, Blocks.OBSIDIAN.defaultBlockState(), 3);

            // Revert SiteData shown to omit slot 34
            SiteData data = SiteData.get(l);
            SiteData.Site oldSite = data.get(d.id(), a);
            List<String> shownWithout34 = new ArrayList<>(oldSite.shown());
            shownWithout34.remove(BuiltInRegistries.ITEM.getKey(ModItems.DOWN_INCLINE_BELT.get()).toString());
            data.put(new SiteData.Site(oldSite.id(), oldSite.anchor(), oldSite.zone(), oldSite.version(), shownWithout34, oldSite.parent()));

            // Run checkAll
            Sites.checkAll(l);

            // Verify the obsidian block was NOT overwritten!
            check(h, l.getBlockState(p34).is(Blocks.OBSIDIAN), "player obsidian block preserved, not overwritten by display");

            h.succeed();
        } finally {
            cleanup(l, List.of(a));
        }
    }

    /**
     * 3. Plot kiosks automatically place missing display blocks and update layout without rebuild.
     */
    @GameTest(template = "empty", batch = "p21k_kiosk", timeoutTicks = 100)
    public static void kioskAutoPlacesMissingDisplaysAndUpdatesLayout(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(43);
        prepare(l, a);
        try {
            SiteDef d = def(DEEP_DIG_SHOP);
            Sites.Result r = Sites.build(l, d, a, null, false);
            BoundingBox z = r.site().zone();

            Map<Integer, ShopDisplayBlockEntity> des = displayEntities(l, z);
            check(h, des.containsKey(7), "kiosk has slot 7");
            BlockPos p7 = des.get(7).getBlockPos().immutable();

            // Simulate missing slot 7 in kiosk
            l.setBlock(p7, Blocks.AIR.defaultBlockState(), 3);
            SiteData data = SiteData.get(l);
            SiteData.Site oldSite = data.get(d.id(), a);
            List<String> shownWithout7 = new ArrayList<>(oldSite.shown());
            shownWithout7.remove(BuiltInRegistries.ITEM.getKey(ModItems.PROSPECTORS_PICK.get()).toString());
            data.put(new SiteData.Site(oldSite.id(), oldSite.anchor(), oldSite.zone(), oldSite.version(), shownWithout7, oldSite.parent()));

            check(h, l.getBlockState(p7).isAir(), "slot 7 is air");

            // Run checkAll
            Sites.checkAll(l);

            // Verify slot 7 was restored and shows prospector's pick
            Map<Integer, Item> shown = displays(l, z);
            check(h, shown.get(7) == ModItems.PROSPECTORS_PICK.get(), "slot 7 restored with pick: " + shown.get(7));

            h.succeed();
        } finally {
            cleanup(l, List.of(a));
        }
    }

    /**
     * 4. Drop Chute model has closed corners (no vertical slits), seamless rim pieces, and zero coplanar overlapping faces.
     */
    @GameTest(template = "empty", batch = "p21k_chute_model", timeoutTicks = 100)
    public static void chuteModelHasNoGapsAndNoCoplanarOverlaps(GameTestHelper h) {
        JsonObject model = json("/assets/holeydigsite/models/block/drop_chute.json");
        check(h, model.has("elements") && model.getAsJsonArray("elements").size() >= 8, "drop chute model has elements");

        // Verify elements are in range and test for z-fighting (coplanar overlapping faces)
        Map<String, List<double[]>> planes = new HashMap<>();
        model.getAsJsonArray("elements").forEach(e -> {
            JsonObject el = e.getAsJsonObject();
            for (String k : new String[]{"from", "to"}) {
                el.getAsJsonArray(k).forEach(v -> check(h, v.getAsDouble() >= -16 && v.getAsDouble() <= 32, "element out of range"));
            }
            var frm = el.getAsJsonArray("from");
            var to = el.getAsJsonArray("to");
            double x0 = frm.get(0).getAsDouble(), y0 = frm.get(1).getAsDouble(), z0 = frm.get(2).getAsDouble();
            double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();

            JsonObject faces = el.getAsJsonObject("faces");
            for (String f : faces.keySet()) {
                String key;
                double[] rect;
                switch (f) {
                    case "north" -> { key = String.format(java.util.Locale.ROOT, "z:%.4f", z0); rect = new double[]{x0, y0, x1, y1}; }
                    case "south" -> { key = String.format(java.util.Locale.ROOT, "z:%.4f", z1); rect = new double[]{x0, y0, x1, y1}; }
                    case "west"  -> { key = String.format(java.util.Locale.ROOT, "x:%.4f", x0); rect = new double[]{z0, y0, z1, y1}; }
                    case "east"  -> { key = String.format(java.util.Locale.ROOT, "x:%.4f", x1); rect = new double[]{z0, y0, z1, y1}; }
                    case "down"  -> { key = String.format(java.util.Locale.ROOT, "y:%.4f", y0); rect = new double[]{x0, z0, x1, z1}; }
                    case "up"    -> { key = String.format(java.util.Locale.ROOT, "y:%.4f", y1); rect = new double[]{x0, z0, x1, z1}; }
                    default -> throw new IllegalStateException("unknown face " + f);
                }
                List<double[]> existing = planes.computeIfAbsent(key, k -> new ArrayList<>());
                for (double[] o : existing) {
                    boolean overlap = Math.min(rect[2], o[2]) > Math.max(rect[0], o[0]) + 1e-5
                            && Math.min(rect[3], o[3]) > Math.max(rect[1], o[1]) + 1e-5;
                    check(h, !overlap, "drop chute has overlapping coplanar faces in plane " + key);
                }
                existing.add(rect);
            }
        });

        // Verify full corner coverage on shaft (X=3, X=13 from Z:3 to Z:13 at Y:0..12)
        double coveredWestShaft = sumCoverage(planes.get("x:3.0000"), 3.0, 13.0, 0.0, 12.0);
        check(h, Math.abs(coveredWestShaft - 10.0 * 12.0) < 1e-3, "west shaft wall fully closed: " + coveredWestShaft);
        double coveredEastShaft = sumCoverage(planes.get("x:13.0000"), 3.0, 13.0, 0.0, 12.0);
        check(h, Math.abs(coveredEastShaft - 10.0 * 12.0) < 1e-3, "east shaft wall fully closed: " + coveredEastShaft);

        // Verify full rim coverage on collar (X=1, X=15 from Z:1 to Z:15 at Y:12..16)
        double coveredWestCollar = sumCoverage(planes.get("x:1.0000"), 1.0, 15.0, 12.0, 16.0);
        check(h, Math.abs(coveredWestCollar - 14.0 * 4.0) < 1e-3, "west collar wall fully closed: " + coveredWestCollar);
        double coveredEastCollar = sumCoverage(planes.get("x:15.0000"), 1.0, 15.0, 12.0, 16.0);
        check(h, Math.abs(coveredEastCollar - 14.0 * 4.0) < 1e-3, "east collar wall fully closed: " + coveredEastCollar);

        h.succeed();
    }

    private static double sumCoverage(List<double[]> rects, double uMin, double uMax, double vMin, double vMax) {
        if (rects == null) return 0;
        double sum = 0;
        for (double[] r : rects) {
            double u0 = Math.max(uMin, r[0]), u1 = Math.min(uMax, r[2]);
            double v0 = Math.max(vMin, r[1]), v1 = Math.min(vMax, r[3]);
            if (u1 > u0 && v1 > v0) {
                sum += (u1 - u0) * (v1 - v0);
            }
        }
        return sum;
    }

    private static JsonObject json(String path) {
        try (InputStream in = Phase21kTests.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
