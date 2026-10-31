package com.holeydigsite.devtest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.forge.CraftingForgeBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.CrucibleLabel;
import com.holeydigsite.forge.ForgeRecipe;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.forge.LargeCrucibleBlock;

import com.holeydigsite.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 3D clumps and chunks, the front lever of the Crafting Forge, the wheel count label and the crucible auto-pour line. */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase232Tests {
    private static final String ROOT = "/assets/holeydigsite/";
    private static final int BUDGET = 6;
    private static final net.minecraft.resources.ResourceLocation FOLKIRON = HoleyDigsite.id("folkiron");
    private static final String[] METALS = {"folkiron", "goldchro", "orchremia", "silvane"};
    private static final String[] SIZES = {"small", "medium", "large"};
    private static final String[] CLUMPS = {"dirt_clump", "dirt_clump_coarse", "dirt_clump_rooted", "dirt_clump_moss", "dirt_clump_deep",
            "dirt_clump_abyssal", "gravel_clump", "sand_clump"};

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static JsonObject load(String path) {
        try (InputStream in = Phase232Tests.class.getResourceAsStream(ROOT + path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean exists(String path) {
        return Phase232Tests.class.getResource(ROOT + path) != null;
    }

    /** The model with its parents merged: nearest elements and per-key textures win. */
    private static JsonObject resolve(String model) {
        JsonObject m = load("models/" + model + ".json");
        if (m == null) return null;
        JsonObject out = new JsonObject();
        JsonObject tex = new JsonObject();
        if (m.has("parent") && m.get("parent").getAsString().startsWith("holeydigsite:")) {
            JsonObject p = resolve(m.get("parent").getAsString().replace("holeydigsite:", ""));
            if (p == null) return null;
            if (p.has("elements")) out.add("elements", p.get("elements"));
            for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("textures").entrySet()) tex.add(e.getKey(), e.getValue());
            if (p.has("display")) out.add("display", p.get("display"));
        }
        if (m.has("elements")) out.add("elements", m.get("elements"));
        if (m.has("display")) out.add("display", m.get("display"));
        if (m.has("textures")) {
            for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("textures").entrySet()) tex.add(e.getKey(), e.getValue());
        }
        out.add("textures", tex);
        return out;
    }

    private static List<String> allModels() {
        List<String> out = new ArrayList<>();
        for (String c : CLUMPS) out.add("item/" + c);
        for (String m : METALS) for (String s : SIZES) out.add("item/" + m + "_chunk_" + s);
        out.add("item/red_dirt_clump");
        out.add("item/mullock_clump");
        out.add("item/opal_clay_clump");
        for (String s : SIZES) out.add("item/outback_gold_chunk_" + s);
        return out;
    }

    /** Each face as {axis, plane coordinate, direction sign, min a, max a, min b, max b}. */
    private static List<double[]> faces(JsonObject shape) {
        List<double[]> out = new ArrayList<>();
        for (JsonElement el : shape.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            double[] f = new double[3];
            double[] t = new double[3];
            for (int i = 0; i < 3; i++) {
                f[i] = e.getAsJsonArray("from").get(i).getAsDouble();
                t[i] = e.getAsJsonArray("to").get(i).getAsDouble();
            }
            for (Map.Entry<String, JsonElement> face : e.getAsJsonObject("faces").entrySet()) {
                String d = face.getKey();
                int axis = switch (d) {
                    case "up", "down" -> 1;
                    case "north", "south" -> 2;
                    default -> 0;
                };
                double coord = switch (d) {
                    case "up" -> t[1];
                    case "down" -> f[1];
                    case "south" -> t[2];
                    case "north" -> f[2];
                    case "east" -> t[0];
                    default -> f[0];
                };
                int a = axis == 0 ? 1 : 0;
                int b = axis == 2 ? 1 : 2;
                double sign = d.equals("up") || d.equals("south") || d.equals("east") ? 1 : -1;
                out.add(new double[]{axis, coord, sign, f[a], t[a], f[b], t[b]});
            }
        }
        return out;
    }

    @GameTest(template = "empty")
    public static void everyModelParsesAndTexturesResolve(GameTestHelper h) {
        for (String m : allModels()) {
            JsonObject model = resolve(m);
            check(h, model != null, m + " parses");
            check(h, model.has("elements"), m + " has elements");
            int n = model.getAsJsonArray("elements").size();
            check(h, n >= 3 && n <= BUDGET, m + " has " + n + " elements (budget 3.." + BUDGET + ")");
            JsonObject tex = model.getAsJsonObject("textures");
            check(h, tex.has("t") && tex.has("particle"), m + " names its texture");
            String t = tex.get("t").getAsString().replace("holeydigsite:", "");
            check(h, exists("textures/" + t + ".png"), m + " texture " + t + " exists");
            for (JsonElement el : model.getAsJsonArray("elements")) {
                for (Map.Entry<String, JsonElement> face : el.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    JsonObject fo = face.getValue().getAsJsonObject();
                    check(h, fo.get("texture").getAsString().equals("#t"), m + " face uses #t");
                }
            }
        }
        JsonObject dirt = load("models/item/dirt_clump.json");
        JsonArray ov = dirt.getAsJsonArray("overrides");
        check(h, ov.size() == 5, "dirt clump keeps its five tier overrides");
        for (JsonElement e : ov) {
            String target = e.getAsJsonObject().get("model").getAsString().replace("holeydigsite:", "");
            check(h, load("models/" + target + ".json") != null, "override target " + target + " parses");
        }
        for (Item item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            String path = key.getPath();
            if (key.getNamespace().equals(HoleyDigsite.MODID) && (path.contains("_chunk_") || path.endsWith("_clump"))) {
                check(h, allModels().contains("item/" + path), "registered " + path + " is covered by the generator");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void elementsInsideWithValidUvs(GameTestHelper h) {
        for (String m : allModels()) {
            JsonObject model = resolve(m);
            for (JsonElement el : model.getAsJsonArray("elements")) {
                JsonObject e = el.getAsJsonObject();
                for (int i = 0; i < 3; i++) {
                    double f = e.getAsJsonArray("from").get(i).getAsDouble();
                    double t = e.getAsJsonArray("to").get(i).getAsDouble();
                    check(h, f >= 0 && t <= 16 && f < t, m + " element inside 0..16 on axis " + i);
                }
                check(h, e.getAsJsonArray("from").get(1).getAsDouble() >= 6.0 - 1e-9, m + " stands on y 6 or above");
                for (Map.Entry<String, JsonElement> face : e.getAsJsonObject("faces").entrySet()) {
                    JsonObject fo = face.getValue().getAsJsonObject();
                    if (!fo.has("uv")) continue;
                    JsonArray uv = fo.getAsJsonArray("uv");
                    check(h, uv.size() == 4, m + " uv has four numbers");
                    double u0 = uv.get(0).getAsDouble(), v0 = uv.get(1).getAsDouble(), u1 = uv.get(2).getAsDouble(), v1 = uv.get(3).getAsDouble();
                    check(h, u0 >= 0 && v0 >= 0 && u1 <= 16 && v1 <= 16 && u0 < u1 && v0 < v1, m + " uv in 0..16");
                }
            }
            JsonObject display = model.getAsJsonObject("display");
            check(h, display.has("fixed") && display.has("gui") && display.has("ground") && display.has("firstperson_righthand")
                    && display.has("thirdperson_righthand"), m + " display has all contexts");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void noCoplanarOverlappingFaces(GameTestHelper h) {
        for (String m : allModels()) {
            List<double[]> fs = faces(resolve(m));
            for (int i = 0; i < fs.size(); i++) {
                for (int j = i + 1; j < fs.size(); j++) {
                    double[] a = fs.get(i);
                    double[] b = fs.get(j);
                    if (a[0] != b[0] || a[2] != b[2] || Math.abs(a[1] - b[1]) > 1e-6) continue;
                    double ox = Math.min(a[4], b[4]) - Math.max(a[3], b[3]);
                    double oy = Math.min(a[6], b[6]) - Math.max(a[5], b[5]);
                    check(h, !(ox > 1e-6 && oy > 1e-6), m + " faces " + i + " and " + j + " overlap in one plane");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chunksGrowFromSmallToLarge(GameTestHelper h) {
        for (String metal : METALS) {
            double lastVolume = 0;
            double lastWidth = 0;
            for (String size : SIZES) {
                double[] lo = {99, 99, 99};
                double[] hi = {0, 0, 0};
                for (JsonElement el : resolve("item/" + metal + "_chunk_" + size).getAsJsonArray("elements")) {
                    JsonObject e = el.getAsJsonObject();
                    for (int i = 0; i < 3; i++) {
                        lo[i] = Math.min(lo[i], e.getAsJsonArray("from").get(i).getAsDouble());
                        hi[i] = Math.max(hi[i], e.getAsJsonArray("to").get(i).getAsDouble());
                    }
                }
                double volume = (hi[0] - lo[0]) * (hi[1] - lo[1]) * (hi[2] - lo[2]);
                double width = Math.max(hi[0] - lo[0], hi[2] - lo[2]);
                check(h, volume > lastVolume * 1.5, metal + " " + size + " clearly bigger than the one before");
                check(h, width > lastWidth, metal + " " + size + " wider than the one before");
                lastVolume = volume;
                lastWidth = width;
            }
        }
        h.succeed();
    }

    private static double[] average(String tex) throws Exception {
        try (InputStream in = Phase232Tests.class.getResourceAsStream(ROOT + "textures/" + tex + ".png")) {
            var img = ImageIO.read(in);
            double r = 0, g = 0, b = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int p = img.getRGB(x, y);
                    r += (p >> 16) & 255;
                    g += (p >> 8) & 255;
                    b += p & 255;
                }
            }
            return new double[]{r / 256, g / 256, b / 256};
        }
    }

    @GameTest(template = "empty")
    public static void kindsAreEasyToTellApart(GameTestHelper h) throws Exception {
        List<double[]> avg = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String c : CLUMPS) {
            avg.add(average(resolve("item/" + c).getAsJsonObject("textures").get("t").getAsString().replace("holeydigsite:", "")));
            names.add(c);
        }
        for (int i = 0; i < avg.size(); i++) {
            for (int j = i + 1; j < avg.size(); j++) {
                double d = 0;
                for (int k = 0; k < 3; k++) d += Math.abs(avg.get(i)[k] - avg.get(j)[k]);
                check(h, d > 30, names.get(i) + " and " + names.get(j) + " look too alike (" + d + ")");
            }
        }
        double[] moss = avg.get(3);
        check(h, moss[1] > moss[0] + 15 && moss[1] > moss[2] + 15, "moss shows green");
        double abyssal = avg.get(5)[0] + avg.get(5)[1] + avg.get(5)[2];
        for (int i = 0; i < avg.size(); i++) {
            if (i != 5) check(h, avg.get(i)[0] + avg.get(i)[1] + avg.get(i)[2] > abyssal + 30, names.get(i) + " is lighter than abyssal");
        }
        double[] gold = average("item/chunk/goldchro");
        double[] folk = average("item/chunk/folkiron");
        check(h, gold[0] > folk[0] + 40 && gold[2] < folk[2], "goldchro chunk is gold coloured");
        h.succeed();
    }

    /** A point given in the north-facing frame (pixels: lx from the viewer's right edge, y, lz from the front), as a world point. */
    private static Vec3 point(BlockPos pos, Direction facing, double lx, double y, double lz) {
        double wx;
        double wz;
        switch (facing) {
            case NORTH -> { wx = lx; wz = lz; }
            case SOUTH -> { wx = 16 - lx; wz = 16 - lz; }
            case WEST -> { wx = lz; wz = 16 - lx; }
            default -> { wx = 16 - lz; wz = lx; }
        }
        return new Vec3(pos.getX() + wx / 16.0, pos.getY() + y / 16.0, pos.getZ() + wz / 16.0);
    }

    @GameTest(template = "empty")
    public static void forgeHandleHitAreaMatchesTheHandle(GameTestHelper h) {
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos pos = h.absolutePos(rel);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = ModBlocks.CRAFTING_FORGE.get().defaultBlockState().setValue(CraftingForgeBlock.FACING, d);
            h.setBlock(rel, s);
            check(h, CraftingForgeBlock.isHandle(s, pos, point(pos, d, 8, 12, 0)), d + ": the lever counts as the handle");
            check(h, CraftingForgeBlock.isHandle(s, pos, point(pos, d, 6, 11, 0)), d + ": the boss counts as the handle");
            check(h, !CraftingForgeBlock.isHandle(s, pos, point(pos, d, 8, 12, 0.5)), d + ": inside the front is not the handle");
            check(h, !CraftingForgeBlock.isHandle(s, pos, point(pos, d, 8, 16, 8)), d + ": the brim top is not the handle");
            check(h, !CraftingForgeBlock.isHandle(s, pos, point(pos, d, 0, 12, 8)), d + ": the side is not the handle");
            check(h, !CraftingForgeBlock.isHandle(s, pos, point(pos, d, 8, 12, 16)), d + ": the back is not the handle");
            check(h, !CraftingForgeBlock.isHandle(s, pos, point(pos, d, 8, 8, 0)), d + ": the foot level is not the handle");
            check(h, !CraftingForgeBlock.isHandle(s, pos, point(pos, d, 2, 12, 0)), d + ": the front corner is not the handle");
            boolean inShape = false;
            Vec3 lever = point(pos, d, 8, 12, 0).subtract(pos.getX(), pos.getY(), pos.getZ());
            for (AABB box : s.getShape(h.getLevel(), pos).toAabbs()) {
                if (box.inflate(1e-6).contains(lever)) inShape = true;
            }
            check(h, inShape, d + ": the lever is inside the block shape");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wheelLabelShowsTheCountOnce(GameTestHelper h) {
        boolean seen = false;
        for (ForgeRecipes.Entry e : ForgeRecipes.all(h.getLevel().registryAccess())) {
            ForgeRecipe r = e.recipe();
            Component name = r.baseName();
            int count = r.resultStack().getCount();
            boolean counted = name.getContents() instanceof TranslatableContents t0
                    && t0.getKey().equals("text.holeydigsite.crafting_forge.count");
            if (count <= 1) {
                check(h, !counted, e.id() + ": single output has no count");
                continue;
            }
            seen = true;
            check(h, counted, e.id() + ": count in the base name");
            TranslatableContents t = (TranslatableContents) name.getContents();
            check(h, t.getArgs()[0].equals(count), e.id() + ": count argument");
            check(h, !(t.getArgs()[1] instanceof Component c && c.getContents() instanceof TranslatableContents inner
                    && inner.getKey().equals("text.holeydigsite.crafting_forge.count")), e.id() + ": count not nested twice");
        }
        check(h, seen, "at least one recipe with several outputs");
        h.succeed();
    }

    private static boolean hasAuto(List<Component> lines) {
        for (Component c : lines) {
            if (c.getContents() instanceof TranslatableContents t && t.getKey().equals("text.holeydigsite.crucible.auto")) return true;
        }
        return false;
    }

    @GameTest(template = "empty")
    public static void crucibleLabelHasAutoLineOnlyWhileOn(GameTestHelper h) {
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockState off = ModBlocks.LARGE_CRUCIBLE.get().defaultBlockState().setValue(LargeCrucibleBlock.AUTO, false);
        h.setBlock(rel, off);
        CrucibleBlockEntity c = (CrucibleBlockEntity) h.getBlockEntity(rel);
        check(h, CrucibleLabel.lines(c).isEmpty(), "empty crucible, auto off: no label");
        c.setContents(FOLKIRON, 0, 40);
        List<Component> lines = CrucibleLabel.lines(c);
        check(h, lines.size() == 1 && !hasAuto(lines), "metal, auto off: one line, no auto-pour");
        h.setBlock(rel, off.setValue(LargeCrucibleBlock.AUTO, true));
        c = (CrucibleBlockEntity) h.getBlockEntity(rel);
        c.setContents(null, 0, 0);
        check(h, CrucibleLabel.lines(c).size() == 1 && hasAuto(CrucibleLabel.lines(c)), "empty crucible, auto on: just the auto-pour line");
        c.setContents(FOLKIRON, 0, 40);
        lines = CrucibleLabel.lines(c);
        check(h, lines.size() == 2 && hasAuto(lines) && !hasAuto(lines.subList(0, 1)), "metal, auto on: metal line then auto-pour");
        h.setBlock(rel, ModBlocks.CRUCIBLE.get().defaultBlockState());
        c = (CrucibleBlockEntity) h.getBlockEntity(rel);
        c.setContents(FOLKIRON, 0, 40);
        check(h, !hasAuto(CrucibleLabel.lines(c)), "plain crucible never shows auto-pour");
        JsonObject lang = load("lang/en_us.json");
        String text = lang.get("text.holeydigsite.crucible.auto").getAsString();
        check(h, text.length() <= 20, "auto-pour line is short: " + text);
        h.succeed();
    }
}
