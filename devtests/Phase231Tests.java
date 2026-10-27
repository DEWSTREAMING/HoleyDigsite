package com.holeydigsite.devtest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 3D ingot models: every model and override target parses, geometry is inside the block with valid UVs, no coplanar overlaps, weights pick the right size. */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase231Tests {
    private static final String ROOT = "/assets/holeydigsite/";
    private static final int[] MOLD_WEIGHTS = {10, 25, 50, 75, 100, 150, 200, 300, 400, 500};
    private static final int[] EXPECTED = {0, 0, 1, 1, 2, 2, 2, 3, 3, 3};

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static JsonObject load(String path) {
        try (InputStream in = Phase231Tests.class.getResourceAsStream(ROOT + path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean exists(String path) {
        return Phase231Tests.class.getResource(ROOT + path) != null;
    }

    private static List<Item> ingots() {
        List<Item> out = new ArrayList<>();
        for (Item i : BuiltInRegistries.ITEM) {
            if (i instanceof IngotItem && BuiltInRegistries.ITEM.getKey(i).getNamespace().equals(HoleyDigsite.MODID)) out.add(i);
        }
        return out;
    }

    private static String name(Item i) {
        return BuiltInRegistries.ITEM.getKey(i).getPath();
    }

    /** The model an item shows at a size step, resolved like the game does (last override whose predicate is reached). */
    private static String modelAt(JsonObject base, String baseName, int step) {
        String model = baseName;
        JsonArray ov = base.has("overrides") ? base.getAsJsonArray("overrides") : new JsonArray();
        for (JsonElement e : ov) {
            JsonObject o = e.getAsJsonObject();
            double p = o.getAsJsonObject("predicate").get("holeydigsite:size").getAsDouble();
            if (step >= p) model = o.get("model").getAsString().replace("holeydigsite:", "");
        }
        return model;
    }

    /** Each face as {axis, plane coordinate, min a, max a, min b, max b}. */
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
                out.add(new double[]{axis, coord, f[a], t[a], f[b], t[b]});
            }
        }
        return out;
    }

    @GameTest(template = "empty")
    public static void everyModelParsesAndOverridesResolve(GameTestHelper h) {
        List<Item> items = ingots();
        check(h, items.size() >= 4, "at least the four ingots registered, found " + items.size());
        for (Item item : items) {
            String n = name(item);
            JsonObject base = load("models/item/" + n + ".json");
            check(h, base != null, n + " model parses");
            check(h, base.has("overrides") && base.getAsJsonArray("overrides").size() == 3, n + " has 3 overrides");
            for (int step = 0; step < 4; step++) {
                String m = modelAt(base, "item/" + n, step);
                JsonObject model = load("models/" + m + ".json");
                check(h, model != null, n + " size " + step + " model " + m + " parses");
                String parent = model.get("parent").getAsString().replace("holeydigsite:", "");
                check(h, parent.equals("item/ingot_size_" + step), n + " size " + step + " uses shape " + step + ", got " + parent);
                String tex = model.getAsJsonObject("textures").get("bar").getAsString().replace("holeydigsite:", "");
                check(h, exists("textures/" + tex + ".png"), n + " texture " + tex + " exists");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shapesStayInsideWithValidUvs(GameTestHelper h) {
        for (int step = 0; step < 4; step++) {
            JsonObject shape = load("models/item/ingot_size_" + step + ".json");
            check(h, shape != null, "shape " + step + " parses");
            for (JsonElement el : shape.getAsJsonArray("elements")) {
                JsonObject e = el.getAsJsonObject();
                for (int i = 0; i < 3; i++) {
                    double f = e.getAsJsonArray("from").get(i).getAsDouble();
                    double t = e.getAsJsonArray("to").get(i).getAsDouble();
                    check(h, f >= 0 && t <= 16 && f < t, "shape " + step + " element inside 0..16 on axis " + i);
                }
                for (Map.Entry<String, JsonElement> face : e.getAsJsonObject("faces").entrySet()) {
                    JsonObject fo = face.getValue().getAsJsonObject();
                    JsonArray uv = fo.getAsJsonArray("uv");
                    check(h, uv.size() == 4, "uv has four numbers");
                    double u0 = uv.get(0).getAsDouble();
                    double v0 = uv.get(1).getAsDouble();
                    double u1 = uv.get(2).getAsDouble();
                    double v1 = uv.get(3).getAsDouble();
                    check(h, u0 >= 0 && v0 >= 0 && u1 <= 16 && v1 <= 16 && u0 < u1 && v0 < v1, "shape " + step + " uv in 0..16");
                    check(h, fo.get("texture").getAsString().equals("#bar"), "face uses #bar");
                }
            }
            JsonObject display = shape.getAsJsonObject("display");
            check(h, display.has("fixed") && display.has("gui") && display.has("ground") && display.has("firstperson_righthand")
                    && display.has("thirdperson_righthand"), "display has all contexts");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void noCoplanarOverlappingFaces(GameTestHelper h) {
        for (int step = 0; step < 4; step++) {
            List<double[]> fs = faces(load("models/item/ingot_size_" + step + ".json"));
            for (int i = 0; i < fs.size(); i++) {
                for (int j = i + 1; j < fs.size(); j++) {
                    double[] a = fs.get(i);
                    double[] b = fs.get(j);
                    if (a[0] != b[0] || Math.abs(a[1] - b[1]) > 1e-6) continue;
                    double ox = Math.min(a[3], b[3]) - Math.max(a[2], b[2]);
                    double oy = Math.min(a[5], b[5]) - Math.max(a[4], b[4]);
                    check(h, !(ox > 1e-6 && oy > 1e-6), "shape " + step + " faces " + i + " and " + j + " overlap in one plane");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sizesGrowStepByStep(GameTestHelper h) {
        double lastVolume = 0;
        double lastLength = 0;
        for (int step = 0; step < 4; step++) {
            double minX = 99, maxX = 0, maxY = 0, minY = 99, minZ = 99, maxZ = 0;
            for (JsonElement el : load("models/item/ingot_size_" + step + ".json").getAsJsonArray("elements")) {
                JsonObject e = el.getAsJsonObject();
                minX = Math.min(minX, e.getAsJsonArray("from").get(0).getAsDouble());
                maxX = Math.max(maxX, e.getAsJsonArray("to").get(0).getAsDouble());
                minY = Math.min(minY, e.getAsJsonArray("from").get(1).getAsDouble());
                maxY = Math.max(maxY, e.getAsJsonArray("to").get(1).getAsDouble());
                minZ = Math.min(minZ, e.getAsJsonArray("from").get(2).getAsDouble());
                maxZ = Math.max(maxZ, e.getAsJsonArray("to").get(2).getAsDouble());
            }
            double length = maxX - minX;
            double volume = length * (maxY - minY) * (maxZ - minZ);
            check(h, minY == 6.0, "shape " + step + " bottom at y 6 (the renderers rely on it), was " + minY);
            check(h, length <= 15.0 + 1e-9, "longest bar is 15 px (anvil layout), was " + length);
            check(h, volume > lastVolume * 1.4, "shape " + step + " clearly bigger than the one before");
            check(h, length > lastLength, "shape " + step + " longer than the one before");
            lastVolume = volume;
            lastLength = length;
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void weightPicksTheSize(GameTestHelper h) {
        for (int i = 0; i < MOLD_WEIGHTS.length; i++) {
            int step = IngotItem.sizeStep(MOLD_WEIGHTS[i]);
            check(h, step == EXPECTED[i], MOLD_WEIGHTS[i] + " g should be size " + EXPECTED[i] + ", was " + step);
        }
        int[][] edges = {{1, 0}, {39, 0}, {40, 1}, {89, 1}, {90, 2}, {249, 2}, {250, 3}, {5000, 3}};
        for (int[] e : edges) {
            check(h, IngotItem.sizeStep(e[0]) == e[1], e[0] + " g edge");
        }
        for (Item item : ingots()) {
            JsonObject base = load("models/item/" + name(item) + ".json");
            for (int w : MOLD_WEIGHTS) {
                int step = IngotItem.sizeStep(IngotItem.grams(IngotItem.of(item, w)));
                String m = modelAt(base, "item/" + name(item), step);
                String want = step == 0 ? "item/" + name(item) : "item/" + name(item) + "_" + step;
                check(h, m.equals(want), name(item) + " at " + w + " g shows " + m + ", wanted " + want);
            }
        }
        check(h, IngotItem.sizeStep(IngotItem.grams(ModItems.FOLKIRON_INGOT.get().getDefaultInstance())) == 0, "default 10 g ingot is size 0");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyMetalHasAllFourSizes(GameTestHelper h) {
        for (Item item : ingots()) {
            String n = name(item);
            check(h, exists("models/item/" + n + ".json"), n + " base model");
            for (int s = 1; s < 4; s++) {
                check(h, exists("models/item/" + n + "_" + s + ".json"), n + " size " + s + " model");
            }
        }
        for (int s = 0; s < 4; s++) {
            check(h, exists("models/item/ingot_size_" + s + ".json"), "shape " + s);
        }
        h.succeed();
    }
}
