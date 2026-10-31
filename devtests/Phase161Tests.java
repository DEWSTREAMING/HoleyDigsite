package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.Sites;
import com.holeydigsite.site.WorldFixups;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 16.1 (sandbox only): nothing floats in the shipped plots, and a plot rebuild / a kiosk-only rebuild leaves the kiosk
 * exactly as the structure designs it. Same idea as tools/check_world.py's connectivity check, but on the real placed world.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase161Tests {
    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static boolean isRock(BlockState s) {
        String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals(HoleyDigsite.MODID) && p.endsWith("rock");
    }

    private static boolean structural(BlockState s) {
        return !s.isAir() && !s.is(Blocks.LIGHT) && !s.is(BlockTags.FLOWERS) && !s.is(BlockTags.REPLACEABLE)
                && !s.is(Blocks.SUGAR_CANE) && !s.is(Blocks.WATER);
    }

    /** Blocks of {@code region} that do not join the unbreakable rock through face-adjacent structural blocks of {@code zone}. */
    private static List<BlockPos> floating(ServerLevel l, BoundingBox zone, BoundingBox region) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        for (BlockPos p : BlockPos.betweenClosed(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ())) {
            if (isRock(l.getBlockState(p))) {
                seen.add(p.immutable());
                q.add(p.immutable());
            }
        }
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            for (BlockPos n : neighbours(l, c)) {
                if (zone.isInside(n) && !seen.contains(n) && structural(l.getBlockState(n))) {
                    seen.add(n);
                    q.add(n);
                }
            }
        }
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ())) {
            if (structural(l.getBlockState(p)) && !seen.contains(p)) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    /** Face neighbours; plus, like tools/check_world.py: stairs join stairs at their edges (a stepped roof), leaves join leaves diagonally. */
    private static List<BlockPos> neighbours(ServerLevel l, BlockPos c) {
        List<BlockPos> out = new ArrayList<>();
        BlockState s = l.getBlockState(c);
        for (Direction d : Direction.values()) {
            out.add(c.relative(d));
        }
        boolean stairs = s.is(BlockTags.STAIRS), leaves = s.is(BlockTags.LEAVES);
        if (stairs) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                out.add(c.relative(d).above());
                out.add(c.relative(d).below());
            }
        }
        if (leaves) {
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                if (x != 0 || y != 0 || z != 0) out.add(c.offset(x, y, z));
            }
        }
        out.removeIf(n -> {
            BlockPos d = n.subtract(c);
            boolean face = Math.abs(d.getX()) + Math.abs(d.getY()) + Math.abs(d.getZ()) == 1;
            BlockState t = l.getBlockState(n);
            // leaves only join logs / leaves; the extra diagonal links only join the same kind
            if (s.is(BlockTags.LEAVES) != t.is(BlockTags.LEAVES) && !(t.is(BlockTags.LOGS) || s.is(BlockTags.LOGS))) return true;
            return !face && !((stairs && t.is(BlockTags.STAIRS)) || (leaves && t.is(BlockTags.LEAVES)));
        });
        return out;
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel l, BoundingBox zone) {
        Map<BlockPos, BlockState> m = new HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ())) {
            BlockState s = l.getBlockState(p);
            if (!s.isAir()) {
                m.put(p.immutable(), s);
            }
        }
        return m;
    }

    private static void cleanup(ServerLevel l, BlockPos a) {
        SiteData data = SiteData.get(l);
        for (SiteData.Site s : new ArrayList<>(data.all())) {
            if (s.zone().isInside(a) || s.anchor().equals(a) || (s.parent() != null && s.parent().endsWith(a.getX() + "," + a.getY() + "," + a.getZ()))) {
                data.remove(s.key());
            }
        }
        PlotData pd = PlotData.get(l);
        for (PlotData.Plot p : new ArrayList<>(pd.all())) {
            if (p.board().getX() >= a.getX() - 2 && p.board().getX() < a.getX() + 40 && p.board().getZ() >= a.getZ() - 2 && p.board().getZ() < a.getZ() + 40) {
                pd.remove(p.id(), p.board());
            }
        }
        SiteDefs.clearOverrides();
    }

    @GameTest(template = "empty", batch = "p161_plots", timeoutTicks = 300)
    public static void plotsAndKiosksDoNotFloatAndSurviveRebuilds(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        String[] plots = {"deep_dig", "gravel_flats", "sand_dunes"};
        for (int i = 0; i < plots.length; i++) {
            BlockPos a = new BlockPos(22000 + i * 64, 100, 22000);
            BoundingBox big = new BoundingBox(a.getX() - 4, a.getY() - 2, a.getZ() - 4, a.getX() + 40, a.getY() + 34, a.getZ() + 40);
            Sites.clear(l, big);
            for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(48))) e.discard();
            try {
                SiteDef plot = SiteDefs.get(HoleyDigsite.id(plots[i])), shop = SiteDefs.get(HoleyDigsite.id(plots[i] + "_shop"));
                check(h, plot != null && shop != null && plot.version() >= 2 && shop.version() >= 2, plots[i] + ": v2 defs shipped");
                Sites.build(l, plot, a, null, false);
                BoundingBox zone = Sites.zoneOf(a, plot);
                SiteData.Site kiosk = SiteData.get(l).get(shop.id(), a.offset(plot.children().get(0).pos()));
                check(h, kiosk != null, plots[i] + ": kiosk built");
                List<BlockPos> loose = floating(l, zone, zone);
                check(h, loose.isEmpty(), plots[i] + ": " + loose.size() + " floating blocks, first " + (loose.isEmpty() ? "" : loose.get(0).subtract(a)));
                Map<BlockPos, BlockState> before = snapshot(l, kiosk.zone());
                check(h, before.size() > 100, plots[i] + ": kiosk is built (" + before.size() + " blocks)");

                // an unowned plot rebuild (kiosk is rebuilt as its child) must give the same kiosk again
                SiteDefs.override(plot.withVersion(plot.version() + 1));
                Sites.checkAll(l);
                check(h, snapshot(l, kiosk.zone()).equals(before), plots[i] + ": kiosk differs after a plot rebuild");
                check(h, floating(l, zone, zone).isEmpty(), plots[i] + ": floating blocks after a plot rebuild");

                // an owned plot only rebuilds its kiosk
                SiteDefs.clearOverrides();
                BlockPos board = a.offset(plot.board());
                PlotData.get(l).setOwned(plots[i], board, true);
                SiteDefs.override(shop.withVersion(shop.version() + 1));
                Sites.checkAll(l);
                check(h, snapshot(l, kiosk.zone()).equals(before), plots[i] + ": kiosk differs after a kiosk-only rebuild");
            } finally {
                cleanup(l, a);
            }
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ world fix-ups

    private static JsonArray groups(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    @GameTest(template = "empty_long")
    public static void fixupsMoveOnlyUntouchedGroups(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        for (int x = 0; x < 26; x++) for (int y = 1; y <= 4; y++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        // a hovering boulder (y 3 over the floor at y 1) and a hovering post with a leaf; the fix lowers both by one
        String g = "[{\"remove\":[[2,3,2,\"minecraft:mossy_cobblestone\"]],\"place\":[[2,2,2,\"minecraft:mossy_cobblestone\"]]},"
                + "{\"remove\":[[6,3,2,\"minecraft:oak_log\"],[6,4,2,\"minecraft:oak_leaves\"]],"
                + "\"place\":[[6,2,2,\"minecraft:oak_log[axis=y]\"],[6,3,2,\"minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]\"]]},"
                + "{\"remove\":[[10,3,2,\"minecraft:mossy_cobblestone\"]],\"place\":[[10,2,2,\"minecraft:mossy_cobblestone\"]]},"
                + "{\"remove\":[[14,3,2,\"minecraft:mossy_cobblestone\"]],\"place\":[[14,2,2,\"minecraft:mossy_cobblestone\"]]},"
                + "{\"expect\":[[18,1,2,\"minecraft:stone\"]],\"remove\":[],\"place\":[[18,2,2,\"minecraft:oak_log[axis=y]\"]]},"
                + "{\"expect\":[[22,1,2,\"minecraft:stone\"]],\"remove\":[],\"place\":[[22,2,2,\"minecraft:oak_log[axis=y]\"]]}]";
        h.setBlock(new BlockPos(18, 1, 2), Blocks.STONE);                   // add-only group with its anchor in place: applied
        h.setBlock(new BlockPos(22, 1, 2), Blocks.DIRT);                    // anchor gone (not the old layout): left alone
        h.setBlock(new BlockPos(2, 3, 2), Blocks.MOSSY_COBBLESTONE);
        h.setBlock(new BlockPos(6, 3, 2), Blocks.OAK_LOG);
        // no leaf at (6,4,2): a missing leaf must not stop the group
        h.setBlock(new BlockPos(10, 3, 2), Blocks.GLASS);                    // player replaced the boulder: group left alone
        h.setBlock(new BlockPos(14, 3, 2), Blocks.MOSSY_COBBLESTONE);
        h.setBlock(new BlockPos(14, 2, 2), Blocks.FURNACE);                 // player built where it would land: left alone
        WorldFixups.Result r = WorldFixups.apply(l, o, groups(g));
        check(h, r.applied() == 3 && r.skipped() == 3, "3 applied, 3 left alone: " + r);
        check(h, h.getBlockState(new BlockPos(18, 2, 2)).is(Blocks.OAK_LOG) && h.getBlockState(new BlockPos(22, 2, 2)).isAir(), "add-only groups follow their anchors");
        check(h, h.getBlockState(new BlockPos(2, 2, 2)).is(Blocks.MOSSY_COBBLESTONE) && h.getBlockState(new BlockPos(2, 3, 2)).isAir(), "boulder lowered");
        check(h, h.getBlockState(new BlockPos(6, 2, 2)).is(Blocks.OAK_LOG) && h.getBlockState(new BlockPos(6, 3, 2)).is(Blocks.OAK_LEAVES), "post + leaf lowered");
        check(h, h.getBlockState(new BlockPos(10, 3, 2)).is(Blocks.GLASS) && h.getBlockState(new BlockPos(10, 2, 2)).isAir(), "player's glass untouched");
        check(h, h.getBlockState(new BlockPos(14, 3, 2)).is(Blocks.MOSSY_COBBLESTONE) && h.getBlockState(new BlockPos(14, 2, 2)).is(Blocks.FURNACE),
                "player's furnace untouched");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "p161_fixdata")
    public static void shippedFixupsParseAndFit(GameTestHelper h) throws Exception {
        ServerLevel l = h.getLevel();
        for (String name : WorldFixups.ALL) {
            var res = l.getServer().getResourceManager().getResource(HoleyDigsite.id("world_fixups/" + name + ".json"));
            check(h, res.isPresent(), "fix-up file shipped: " + name);
            com.google.gson.JsonObject json;
            try (var r = res.get().openAsReader()) {
                json = JsonParser.parseReader(r).getAsJsonObject();
            }
            JsonArray gs = json.getAsJsonArray("groups");
            check(h, gs.size() >= 5 || json.has("signs"), name + " has groups: " + gs.size());
            // on an empty far-away area nothing matches: every group is left alone, and no state fails to parse
            WorldFixups.Result r = WorldFixups.apply(l, new BlockPos(24000, 100, 24000), gs);
            check(h, r.applied() == 0, name + ": nothing applies to an empty area: " + r.applied());
            check(h, r.notes().stream().noneMatch(n -> n.contains("bad state")), name + ": all block states parse: " + r.notes());
        }
        // one WorldVersion step per fix-up file, in order, then the event island step (1.27.0)
        var steps = com.holeydigsite.site.WorldVersion.STEPS;
        check(h, steps.size() == WorldFixups.ALL.size() + 1 && com.holeydigsite.site.WorldVersion.CURRENT == 2 + WorldFixups.ALL.size()
                && steps.get(steps.size() - 1).version() == com.holeydigsite.site.WorldVersion.CURRENT, "a step per fix-up file, then the event island");
        for (int i = 0; i < WorldFixups.ALL.size(); i++) {
            check(h, steps.get(i).version() == i + 2, "fix-up step " + i + " is version " + (i + 2));
        }
        h.succeed();
    }
}
