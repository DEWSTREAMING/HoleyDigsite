package com.holeydigsite.devtest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.BeltMode;
import com.holeydigsite.belt.BeltShape;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.InclineBeltBlock;
import com.holeydigsite.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 12.1 fix-up: the rebuilt Inclined Belt models (inside the block, same belt texture as flat belts, animated only
 * while running), animated corner belts, and heights that line up with flat belts. Rendering itself is checked with the
 * client screenshot tool; these tests read the shipped JSON and the block shapes.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public final class PhaseTwelveOneTests {
    private PhaseTwelveOneTests() {}

    private static final String[] INCLINES = {"incline_belt", "fast_incline_belt"};
    private static final String[] FLATS = {"clump_belt", "fast_clump_belt"};

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static JsonObject resource(String path) {
        try (InputStream in = PhaseTwelveOneTests.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean exists(String path) {
        try (InputStream in = PhaseTwelveOneTests.class.getResourceAsStream(path)) {
            return in != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static JsonObject model(String name) {
        return resource("/assets/holeydigsite/models/block/" + name + ".json");
    }

    /** "holeydigsite:block/x" -> "/assets/holeydigsite/textures/block/x.png" */
    private static String texturePath(String id) {
        String[] p = id.split(":");
        return "/assets/" + p[0] + "/textures/" + p[1] + ".png";
    }

    private static double[] vec(JsonArray a) {
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    /** The 8 corners of an element after its rotation (Minecraft rotates about origin, +angle on x tips +y to +z). */
    private static List<double[]> corners(JsonObject el) {
        double[] f = vec(el.getAsJsonArray("from")), t = vec(el.getAsJsonArray("to"));
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double[] p = {(i & 1) == 0 ? f[0] : t[0], (i & 2) == 0 ? f[1] : t[1], (i & 4) == 0 ? f[2] : t[2]};
            if (el.has("rotation")) {
                JsonObject r = el.getAsJsonObject("rotation");
                double[] o = vec(r.getAsJsonArray("origin"));
                double a = Math.toRadians(r.get("angle").getAsDouble());
                int ax = switch (r.get("axis").getAsString()) { case "x" -> 0; case "y" -> 1; default -> 2; };
                int i1 = ax == 0 ? 1 : ax == 1 ? 2 : 0, i2 = ax == 0 ? 2 : ax == 1 ? 0 : 1;
                double d1 = p[i1] - o[i1], d2 = p[i2] - o[i2];
                p[i1] = o[i1] + d1 * Math.cos(a) - d2 * Math.sin(a);
                p[i2] = o[i2] + d1 * Math.sin(a) + d2 * Math.cos(a);
                if (r.has("rescale") && r.get("rescale").getAsBoolean()) throw new IllegalStateException("rescale not expected");
            }
            out.add(p);
        }
        return out;
    }

    /** True if the state matches a multipart "when" (supports OR and a|b values). */
    private static boolean matches(BlockState state, JsonObject when) {
        if (when.has("OR")) {
            for (JsonElement e : when.getAsJsonArray("OR")) {
                if (matches(state, e.getAsJsonObject())) return true;
            }
            return false;
        }
        for (var e : when.entrySet()) {
            Property<?> prop = state.getBlock().getStateDefinition().getProperty(e.getKey());
            if (prop == null) return false;
            String value = name(state, prop);
            boolean any = false;
            for (String v : e.getValue().getAsString().split("\\|")) any |= v.equals(value);
            if (!any) return false;
        }
        return true;
    }

    private static <T extends Comparable<T>> String name(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    /** The models (with y rotation) a multipart blockstate applies to {@code state}. */
    private static List<String> applied(String blockstate, BlockState state) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : resource("/assets/holeydigsite/blockstates/" + blockstate + ".json").getAsJsonArray("multipart")) {
            JsonObject part = e.getAsJsonObject();
            if (!part.has("when") || matches(state, part.getAsJsonObject("when"))) {
                JsonObject apply = part.getAsJsonObject("apply");
                out.add(apply.get("model").getAsString() + "@" + (apply.has("y") ? apply.get("y").getAsInt() : 0));
            }
        }
        return out;
    }

    private static int yRot(Direction f) {
        return switch (f) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
    }

    private static BlockState running(Block b, Direction f, boolean moving) {
        return b.defaultBlockState().setValue(ClumpBeltBlock.FACING, f)
                .setValue(ClumpBeltBlock.MODE, moving ? BeltMode.MOVING : BeltMode.EMPTY)
                .setValue(ClumpBeltBlock.WATER, true).setValue(ClumpBeltBlock.POWERED, false);
    }

    /**
     * Every incline model element stays inside the block after rotation; the belt surface is one plate with the flat
     * belt's top texture running exactly from the back-bottom edge to the front-top edge (model front = north).
     */
    @GameTest(template = "empty_long")
    public static void inclineModelsStayInsideTheBlock(GameTestHelper h) {
        for (int k = 0; k < 2; k++) {
            for (String suffix : new String[]{"", "_moving"}) {
                String name = INCLINES[k] + suffix;
                JsonObject m = model(name);
                JsonObject flat = model(FLATS[k] + suffix);
                check(h, m.getAsJsonObject("textures").get("top").getAsString().equals(flat.getAsJsonObject("textures").get("top").getAsString()),
                        name + ": same belt texture as " + FLATS[k] + suffix);
                int plates = 0;
                for (JsonElement e : m.getAsJsonArray("elements")) {
                    JsonObject el = e.getAsJsonObject();
                    double lo = 99, hi = -99;
                    for (double[] p : corners(el)) {
                        for (double v : p) {
                            lo = Math.min(lo, v);
                            hi = Math.max(hi, v);
                        }
                    }
                    check(h, lo > -1.0E-3 && hi < 16 + 1.0E-3, name + ": element inside the block, got " + lo + ".." + hi);
                    JsonObject faces = el.getAsJsonObject("faces");
                    if (faces.has("up") && faces.getAsJsonObject("up").get("texture").getAsString().equals("#top")) {
                        plates++;
                        // the plate's top: lowest point at the back (z 16, y 0), highest at the front (z 0, y 16)
                        double minY = 99, zAtMin = 0, maxY = -99, zAtMax = 0;
                        for (double[] p : corners(el)) {
                            if (p[1] < minY) { minY = p[1]; zAtMin = p[2]; }
                            if (p[1] > maxY) { maxY = p[1]; zAtMax = p[2]; }
                        }
                        check(h, Math.abs(minY) < 1.0E-3 && Math.abs(zAtMin - 16) < 1.0E-3, name + ": belt starts at the back-bottom edge");
                        check(h, Math.abs(maxY - 16) < 1.0E-3 && Math.abs(zAtMax) < 1.0E-3, name + ": belt ends at the front-top edge");
                    }
                }
                check(h, plates == 1, name + ": exactly one belt surface, got " + plates);
            }
            check(h, resource("/assets/holeydigsite/models/item/" + INCLINES[k] + ".json").get("parent").getAsString()
                    .equals("holeydigsite:block/" + INCLINES[k]), INCLINES[k] + ": item model = block model");
        }
        h.succeed();
    }

    /** Inclines and corners switch to the animated model only while running and moving, in all 4 facings. */
    @GameTest(template = "empty_long")
    public static void beltModelsAnimateOnlyWhileMoving(GameTestHelper h) {
        Block[] inclines = {ModBlocks.INCLINE_BELT.get(), ModBlocks.FAST_INCLINE_BELT.get()};
        Block[] flats = {ModBlocks.CLUMP_BELT.get(), ModBlocks.FAST_CLUMP_BELT.get()};
        for (int k = 0; k < 2; k++) {
            for (Direction f : Direction.Plane.HORIZONTAL) {
                for (boolean moving : new boolean[]{false, true}) {
                    String suffix = moving ? "_moving" : "";
                    List<String> inc = applied(INCLINES[k], running(inclines[k], f, moving));
                    check(h, inc.contains("holeydigsite:block/" + INCLINES[k] + suffix + "@" + yRot(f)) && inc.size() == 1,
                            INCLINES[k] + " " + f + " moving=" + moving + ": " + inc);
                    for (BeltShape side : new BeltShape[]{BeltShape.LEFT, BeltShape.RIGHT}) {
                        BlockState s = running(flats[k], f, moving).setValue(ClumpBeltBlock.SHAPE, side);
                        String want = "holeydigsite:block/" + FLATS[k] + "_corner_" + side.getSerializedName() + suffix;
                        List<String> got = applied(FLATS[k], s);
                        check(h, got.contains(want + "@" + yRot(f)) && got.size() == 1, FLATS[k] + " " + f + " " + side + " moving=" + moving + ": " + got);
                        String top = model(want.substring(want.indexOf('/') + 1)).getAsJsonObject("textures").get("top").getAsString();
                        check(h, exists(texturePath(top)), "texture " + top);
                        check(h, moving == exists(texturePath(top) + ".mcmeta"), top + ": animated exactly when moving");
                    }
                }
                // stopped by redstone or without water: static even with items
                BlockState frozen = running(inclines[k], f, true).setValue(ClumpBeltBlock.POWERED, true);
                check(h, applied(INCLINES[k], frozen).contains("holeydigsite:block/" + INCLINES[k] + "@" + yRot(f)), "redstone: static incline");
                BlockState dry = running(flats[k], f, true).setValue(ClumpBeltBlock.WATER, false).setValue(ClumpBeltBlock.SHAPE, BeltShape.LEFT);
                check(h, applied(FLATS[k], dry).contains("holeydigsite:block/" + FLATS[k] + "_corner_left@" + yRot(f)), "dry: static corner");
            }
        }
        h.succeed();
    }

    /**
     * Heights line up: the ramp starts at the lower belt's top (0) and ends at the next belt's top (1); the outline
     * follows the slope (full height at the front, low at the back) and stays inside the block in every facing.
     */
    /** Whether the shape contains the point {@code along} (-0.5 back .. 0.5 front) of the middle line at height y. */
    private static boolean inside(VoxelShape s, Direction f, double along, double y) {
        double x = 0.5 + f.getStepX() * along, z = 0.5 + f.getStepZ() * along;
        for (AABB b : s.toAabbs()) {
            if (x >= b.minX && x <= b.maxX && y >= b.minY && y <= b.maxY && z >= b.minZ && z <= b.maxZ) return true;
        }
        return false;
    }

    @GameTest(template = "empty_long")
    public static void inclineHeightsLineUpWithFlatBelts(GameTestHelper h) {
        check(h, InclineBeltBlock.surfaceAt(0F) == 0F && InclineBeltBlock.surfaceAt(1F) == ClumpBeltBlock.SURFACE, "ramp ends");
        check(h, Math.abs(InclineBeltBlock.surfaceAt(0.5F) - 0.5F) < 1.0E-6, "45 degrees");
        BlockPos p = new BlockPos(1, 2, 1);
        for (Direction f : Direction.Plane.HORIZONTAL) {
            h.setBlock(p, ModBlocks.INCLINE_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, f));
            BlockPos abs = h.absolutePos(p);
            VoxelShape s = h.getBlockState(p).getShape(h.getLevel(), abs, CollisionContext.empty());
            AABB all = s.bounds();
            check(h, all.minX >= 0 && all.minY >= 0 && all.minZ >= 0 && all.maxX <= 1 && all.maxY <= 1 && all.maxZ <= 1, f + ": inside the block");
            // points along the facing: the high end is full height, the low end is only a low step
            check(h, inside(s, f, 0.45, 0.97), f + ": full height at the front (high end)");
            check(h, inside(s, f, -0.45, 0.2), f + ": low step at the back");
            check(h, !inside(s, f, -0.45, 0.3), f + ": nothing above the slope at the back");
            check(h, inside(s, f, 0.0, 0.45) && !inside(s, f, 0.0, 0.8), f + ": half height in the middle");
        }
        h.succeed();
    }
}
