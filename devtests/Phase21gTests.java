package com.holeydigsite.devtest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Phase 21g: Distinct gem shapes for Sapphire (cushion cut / hexagonal barrel)
 * and Topaz (emerald cut / long striated prism with pointed end).
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21gTests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) {
            h.fail(msg);
        }
    }

    private static final String[] GEMS = {"ruby", "sapphire", "topaz"};
    private static final String[] SIZES = {"small", "medium", "large"};

    @GameTest(template = "empty")
    public static void all18GemTexturesExistAndAre16x16(GameTestHelper h) {
        for (String gem : GEMS) {
            for (String size : SIZES) {
                for (String prefix : new String[]{"", "uncut_"}) {
                    String name = prefix + gem + "_" + size;
                    String path = "/assets/holeydigsite/textures/item/" + name + ".png";
                    try (InputStream in = Phase21gTests.class.getResourceAsStream(path)) {
                        check(h, in != null, "Texture file exists: " + path);
                        BufferedImage img = ImageIO.read(in);
                        check(h, img != null, "Valid image: " + path);
                        check(h, img.getWidth() == 16 && img.getHeight() == 16, "16x16: " + path);
                        boolean hasPixels = false;
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                if (((img.getRGB(x, y) >> 24) & 0xFF) > 0) {
                                    hasPixels = true;
                                    break;
                                }
                            }
                            if (hasPixels) break;
                        }
                        check(h, hasPixels, "Non-empty: " + path);
                    } catch (Exception e) {
                        h.fail("Failed to read " + path + ": " + e.getMessage());
                    }
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cutGemShapesAreDistinct(GameTestHelper h) {
        // Sapphire cut must be wider than tall (cushion / oval cut)
        // Topaz cut must be taller than wide (emerald cut)
        for (String size : SIZES) {
            BufferedImage sImg = load("/assets/holeydigsite/textures/item/sapphire_" + size + ".png");
            int[] sBox = bbox(sImg);
            int sW = sBox[2] - sBox[0] + 1;
            int sH = sBox[3] - sBox[1] + 1;
            check(h, sW > sH, "Cut sapphire " + size + " is wider than tall (" + sW + "x" + sH + ")");

            BufferedImage tImg = load("/assets/holeydigsite/textures/item/topaz_" + size + ".png");
            int[] tBox = bbox(tImg);
            int tW = tBox[2] - tBox[0] + 1;
            int tH = tBox[3] - tBox[1] + 1;
            check(h, tH > tW, "Cut topaz " + size + " is taller than wide (" + tW + "x" + tH + ")");

            BufferedImage rImg = load("/assets/holeydigsite/textures/item/ruby_" + size + ".png");
            int[] rBox = bbox(rImg);
            int rW = rBox[2] - rBox[0] + 1;
            int rH = rBox[3] - rBox[1] + 1;
            check(h, rW != tW || rH != tH || sW != tW, "Gem sizes have distinct silhouettes for size " + size);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void uncutGemShapesAreDistinct(GameTestHelper h) {
        for (String size : SIZES) {
            BufferedImage sImg = load("/assets/holeydigsite/textures/item/uncut_sapphire_" + size + ".png");
            int[] sBox = bbox(sImg);
            int sW = sBox[2] - sBox[0] + 1;
            int sH = sBox[3] - sBox[1] + 1;

            BufferedImage tImg = load("/assets/holeydigsite/textures/item/uncut_topaz_" + size + ".png");
            int[] tBox = bbox(tImg);
            int tW = tBox[2] - tBox[0] + 1;
            int tH = tBox[3] - tBox[1] + 1;

            check(h, tH > tW, "Uncut topaz " + size + " is long vertical prism (" + tW + "x" + tH + ")");
            check(h, sW >= tW, "Uncut sapphire " + size + " is stouter than uncut topaz (" + sW + "x" + sH + " vs " + tW + "x" + tH + ")");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gemDisplayCaseModelContainsDistinctGemElements(GameTestHelper h) {
        String path = "/assets/holeydigsite/models/block/gem_display_case.json";
        try (InputStream in = Phase21gTests.class.getResourceAsStream(path)) {
            check(h, in != null, "gem_display_case.json exists");
            JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray elements = model.getAsJsonArray("elements");
            check(h, elements != null && elements.size() > 10, "gem_display_case has elements");

            // Check texture atlas
            JsonObject textures = model.getAsJsonObject("textures");
            check(h, textures.get("0").getAsString().equals("holeydigsite:block/gem_display_case"), "Texture atlas is gem_display_case");

            // Look for the gem elements (indices 4..15 in elements array)
            // Ruby at cx=4.8, Sapphire at cx=11.2, Topaz at cx=8.0
            boolean foundRuby = false;
            boolean foundSapphire = false;
            boolean foundTopaz = false;

            for (JsonElement el : elements) {
                JsonArray from = el.getAsJsonObject().getAsJsonArray("from");
                JsonArray to = el.getAsJsonObject().getAsJsonArray("to");
                double x0 = from.get(0).getAsDouble();
                double x1 = to.get(0).getAsDouble();
                double z0 = from.get(2).getAsDouble();
                double z1 = to.get(2).getAsDouble();
                double cx = (x0 + x1) / 2.0;
                double cz = (z0 + z1) / 2.0;
                double dx = x1 - x0;
                double dz = z1 - z0;

                if (Math.abs(cx - 4.8) < 0.2 && Math.abs(cz - 5.5) < 0.2) {
                    foundRuby = true;
                } else if (Math.abs(cx - 11.2) < 0.2 && Math.abs(cz - 5.5) < 0.2) {
                    foundSapphire = true;
                    // Sapphire is cushion cut: wider in X than Z
                    if (dx > 2.0) {
                        check(h, dx > dz, "Sapphire element in case is wider than deep (cushion cut)");
                    }
                } else if (Math.abs(cx - 8.0) < 0.2 && Math.abs(cz - 10.5) < 0.2) {
                    foundTopaz = true;
                    // Topaz is emerald cut: longer in Z than X
                    if (dz > 2.0) {
                        check(h, dz > dx, "Topaz element in case is longer than wide (emerald cut)");
                    }
                }
            }

            check(h, foundRuby, "Found ruby elements in display case");
            check(h, foundSapphire, "Found sapphire elements in display case");
            check(h, foundTopaz, "Found topaz elements in display case");
        } catch (Exception e) {
            h.fail("Failed to verify gem_display_case.json: " + e.getMessage());
        }
        h.succeed();
    }

    private static BufferedImage load(String path) {
        try (InputStream in = Phase21gTests.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("Resource not found: " + path);
            return ImageIO.read(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int[] bbox(BufferedImage img) {
        int minX = 16, minY = 16, maxX = -1, maxY = -1;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (((img.getRGB(x, y) >> 24) & 0xFF) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[]{minX, minY, maxX, maxY};
    }
}
