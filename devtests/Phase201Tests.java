package com.holeydigsite.devtest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.WorldFixups;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 20.1 (sandbox only): sign text fix-up (old text replaced, edited / missing sign untouched) + site versions. */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public final class Phase201Tests {
    private Phase201Tests() {}

    private static void check(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    private static void sign(GameTestHelper h, BlockPos rel, String... lines) {
        h.setBlock(rel, Blocks.OAK_SIGN);
        SignBlockEntity be = (SignBlockEntity) h.getBlockEntity(rel);
        SignText t = be.getFrontText();
        for (int i = 0; i < 4; i++) {
            t = t.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
        }
        be.setText(t, true);
    }

    private static String line(GameTestHelper h, BlockPos rel, int i) {
        return ((SignBlockEntity) h.getBlockEntity(rel)).getFrontText().getMessage(i, false).getString();
    }

    private static JsonObject entry(BlockPos p, String[] old, String[] now) {
        JsonObject o = new JsonObject();
        JsonArray pos = new JsonArray();
        pos.add(p.getX());
        pos.add(p.getY());
        pos.add(p.getZ());
        o.add("pos", pos);
        o.addProperty("block", "minecraft:oak_sign");
        JsonArray a = new JsonArray(), b = new JsonArray();
        for (String s : old) a.add(s);
        for (String s : now) b.add(s);
        o.add("old", a);
        o.add("new", b);
        return o;
    }

    @GameTest(template = "empty")
    public static void signFixupReplacesOnlyOldText(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        String[] old = {"Drill pad", "Scoop the dirt,", "drill the rock,", "pan in the pool"};
        String[] now = {"DRILL PAD", "", "Pan at", "the pool"};
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1), c = new BlockPos(3, 1, 1), d = new BlockPos(4, 1, 1);
        sign(h, a, old);                                                    // untouched old sign: replaced
        sign(h, b, "Drill pad", "my own note", "drill the rock,", "pan in the pool");   // player edited one line: left alone
        h.setBlock(c, Blocks.AIR);                                          // sign removed: nothing happens
        sign(h, d, old);
        JsonArray signs = new JsonArray();
        for (BlockPos p : new BlockPos[]{a, b, c}) signs.add(entry(p, old, now));
        WorldFixups.Result r = WorldFixups.applySigns(l, o, signs);
        check(h, r.applied() == 1 && r.skipped() == 2, "1 applied, 2 left alone: " + r);
        check(h, line(h, a, 0).equals("DRILL PAD") && line(h, a, 1).isEmpty() && line(h, a, 2).equals("Pan at") && line(h, a, 3).equals("the pool"), "old sign rewritten");
        check(h, line(h, b, 1).equals("my own note") && line(h, b, 0).equals("Drill pad"), "edited sign untouched");
        check(h, h.getLevel().getBlockState(h.absolutePos(c)).isAir(), "removed sign not recreated");
        check(h, line(h, d, 0).equals("Drill pad"), "sign not in the list untouched");
        WorldFixups.Result again = WorldFixups.applySigns(l, o, signs);       // a second run finds the new text: nothing to do
        check(h, again.applied() == 0, "second run changes nothing: " + again);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shippedSignFixupAndSiteVersions(GameTestHelper h) throws Exception {
        ServerLevel l = h.getLevel();
        var res = l.getServer().getResourceManager().getResource(HoleyDigsite.id("world_fixups/signs_1_18_1.json"));
        check(h, res.isPresent(), "signs fix-up shipped");
        JsonObject json;
        try (var r = res.get().openAsReader()) {
            json = JsonParser.parseReader(r).getAsJsonObject();
        }
        JsonArray signs = json.getAsJsonArray("signs");
        check(h, signs.size() == 4, "4 signs: " + signs.size());
        for (var e : signs) {
            JsonObject s = e.getAsJsonObject();
            check(h, s.getAsJsonArray("old").size() == 4 && s.getAsJsonArray("new").size() == 4, "4 lines each");
            check(h, !s.getAsJsonArray("old").equals(s.getAsJsonArray("new")), "text changes");
        }
        // an empty far-away area has no such signs: all left alone
        WorldFixups.Result r = WorldFixups.applySigns(l, new BlockPos(24000, 100, 24000), signs);
        check(h, r.applied() == 0 && r.skipped() == 4, "nothing applies to an empty area: " + r);
        // site versions bumped for the changed structures, kiosks unchanged
        for (String[] v : new String[][]{{"home", "4"}, {"deep_dig", "4"}, {"gravel_flats", "4"}, {"sand_dunes", "5"},
                {"deep_dig_shop", "2"}, {"gravel_flats_shop", "2"}, {"sand_dunes_shop", "2"}}) {
            var def = SiteDefs.get(HoleyDigsite.id(v[0]));
            check(h, def != null && def.version() == Integer.parseInt(v[1]), v[0] + " version " + (def == null ? "missing" : def.version()));
        }
        check(h, com.holeydigsite.site.WorldVersion.CURRENT == 4, "world version 4");
        h.succeed();
    }
}
