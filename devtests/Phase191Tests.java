package com.holeydigsite.devtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.AnvilLayout;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.smith.AnvilRecipes;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/** Phase 19.1 (sandbox only): smithed pieces are drawn in parts tinted by their ingots; anvil item layout. */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase191Tests {
    private static void check(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    private static ResourceLocation rl(String s) {
        return HoleyDigsite.id(s);
    }

    private static SmithData.Ingot ingot(String metal, int grams) {
        return new SmithData.Ingot(rl(metal), grams);
    }

    private static Optional<ResourceLocation> part(GameTestHelper h, Item item, SmithData d, int layer) {
        return SmithPieces.partMaterial(AnvilRecipes.forItem(h.getLevel().registryAccess(), item), d, layer);
    }

    private static JsonObject json(String path) {
        try (InputStream in = Phase191Tests.class.getResourceAsStream(path)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException(path, e);
        }
    }

    /** Each piece's item model has one layer per recipe part, every texture exists, and parts only name valid slots. */
    @GameTest(template = "empty")
    public static void modelLayersMatchRecipeParts(GameTestHelper h) {
        Item[] items = {ModItems.SMITH_RING.get(), ModItems.SMITH_NECKLACE.get(), ModItems.SMITH_DAGGER.get(), ModItems.SMITH_AXE.get(), ModItems.SMITH_SWORD.get()};
        String[] names = {"ring", "necklace", "dagger", "axe", "sword"};
        int[] layers = {2, 3, 2, 3, 4};   // the ring's second layer (gem) is in its gem model
        for (int i = 0; i < items.length; i++) {
            AnvilRecipe r = AnvilRecipes.forItem(h.getLevel().registryAccess(), items[i]);
            check(h, r != null && r.parts().size() == layers[i], names[i] + " parts: " + (r == null ? "no recipe" : r.parts().size()));
            for (AnvilRecipe.Part p : r.parts()) {
                check(h, p.source() != AnvilRecipe.Part.Source.INGOT || p.index() < r.ingots(), names[i] + " part " + p.name() + " names an ingot the recipe lacks");
            }
            String model = names[i].equals("ring") ? "smith_ring_gem" : "smith_" + names[i];
            JsonObject tex = json("/assets/holeydigsite/models/item/" + model + ".json").getAsJsonObject("textures");
            check(h, tex.size() == layers[i], model + " has " + layers[i] + " layers: " + tex.size());
            for (int l = 0; l < layers[i]; l++) {
                check(h, tex.has("layer" + l), model + " layer" + l);
                ResourceLocation t = ResourceLocation.parse(tex.get("layer" + l).getAsString());
                check(h, Phase191Tests.class.getResource("/assets/" + t.getNamespace() + "/textures/" + t.getPath() + ".png") != null, "texture " + t);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void swordPartsFollowTheIngotOrder(GameTestHelper h) {
        SmithData d = new SmithData(List.of(ingot("folkiron", 150), ingot("goldchro", 150), ingot("silvane", 150)), Optional.empty());
        Item sword = ModItems.SMITH_SWORD.get();
        check(h, part(h, sword, d, 0).equals(Optional.of(rl("folkiron"))), "blade = ingot 1");
        check(h, part(h, sword, d, 1).equals(Optional.of(rl("goldchro"))), "guard = ingot 2");
        check(h, part(h, sword, d, 2).equals(Optional.of(rl("silvane"))), "pommel = ingot 3");
        check(h, part(h, sword, d, 3).isEmpty(), "grip is plain leather");
        check(h, part(h, sword, d, 4).isEmpty(), "no layer beyond the parts");
        // laying order, not weight, decides the part
        SmithData rev = new SmithData(List.of(ingot("silvane", 50), ingot("folkiron", 300), ingot("goldchro", 100)), Optional.empty());
        check(h, part(h, sword, rev, 0).equals(Optional.of(rl("silvane"))) && part(h, sword, rev, 1).equals(Optional.of(rl("folkiron"))), "order beats weight");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void otherPiecesFollowTheirRecipeParts(GameTestHelper h) {
        SmithData two = new SmithData(List.of(ingot("goldchro", 100), ingot("silvane", 100)), Optional.empty());
        Item axe = ModItems.SMITH_AXE.get();
        check(h, part(h, axe, two, 0).equals(Optional.of(rl("goldchro"))) && part(h, axe, two, 1).equals(Optional.of(rl("silvane"))), "axe head + binding");
        check(h, part(h, axe, two, 2).isEmpty(), "axe handle is plain wood");
        Item dagger = ModItems.SMITH_DAGGER.get();
        SmithData one = new SmithData(List.of(ingot("orchremia", 100)), Optional.empty());
        check(h, part(h, dagger, one, 0).equals(Optional.of(rl("orchremia"))) && part(h, dagger, one, 1).isEmpty(), "dagger blade + grip");
        SmithData withGem = new SmithData(List.of(ingot("goldchro", 100), ingot("folkiron", 100)), Optional.of(new SmithData.Gem(rl("sapphire"), 4)));
        Item neck = ModItems.SMITH_NECKLACE.get();
        check(h, part(h, neck, withGem, 0).equals(Optional.of(rl("goldchro"))) && part(h, neck, withGem, 1).equals(Optional.of(rl("folkiron")))
                && part(h, neck, withGem, 2).equals(Optional.of(rl("sapphire"))), "necklace chain / setting / gem");
        Item ring = ModItems.SMITH_RING.get();
        SmithData ringData = new SmithData(List.of(ingot("silvane", 40)), Optional.of(new SmithData.Gem(rl("ruby"), 3)));
        check(h, part(h, ring, ringData, 0).equals(Optional.of(rl("silvane"))) && part(h, ring, ringData, 1).equals(Optional.of(rl("ruby"))), "ring band + gem");
        check(h, part(h, ring, new SmithData(List.of(ingot("silvane", 40)), Optional.empty()), 1).isEmpty(), "ring without a gem has no gem tint");
        h.succeed();
    }

    /** A piece saved by 1.17.0 (ingots in order, nothing else) decodes unchanged and gets the new tints. */
    @GameTest(template = "empty")
    public static void oldPiecesPickUpTheLook(GameTestHelper h) {
        JsonObject old = JsonParser.parseString("{\"ingots\":[{\"metal\":\"holeydigsite:goldchro\",\"grams\":120},{\"metal\":\"holeydigsite:silvane\",\"grams\":90}]}").getAsJsonObject();
        SmithData d = SmithData.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        Item axe = ModItems.SMITH_AXE.get();
        check(h, part(h, axe, d, 0).equals(Optional.of(rl("goldchro"))) && part(h, axe, d, 1).equals(Optional.of(rl("silvane"))), "old axe is mixed now");
        h.succeed();
    }

    /** 3 ingots + the gem: four separate spots on the plate (x 0-16, z 3-13, horn included), for every facing and every ingot count. */
    @GameTest(template = "empty")
    public static void anvilLayoutHasFourSeparateSpots(GameTestHelper h) {
        for (Direction f : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            for (int n = 1; n <= 3; n++) {
                float[][] c = new float[n + 1][];
                float[] half = new float[n + 1];
                for (int i = 0; i < n; i++) {
                    c[i] = AnvilLayout.rotated(AnvilLayout.ingot(i, n), f);
                    half[i] = AnvilLayout.INGOT_SIZE / 2;
                }
                c[n] = AnvilLayout.rotated(AnvilLayout.gem(), f);
                half[n] = AnvilLayout.GEM_SIZE / 2;
                for (int i = 0; i <= n; i++) {
                    check(h, c[i][0] - half[i] >= 0 && c[i][0] + half[i] <= 16 && c[i][1] - half[i] >= 0 && c[i][1] + half[i] <= 16,
                            f + " " + n + " item " + i + " inside the block");
                    for (int j = i + 1; j <= n; j++) {
                        boolean apart = Math.abs(c[i][0] - c[j][0]) >= half[i] + half[j] || Math.abs(c[i][1] - c[j][1]) >= half[i] + half[j];
                        check(h, apart, f + " " + n + " items " + i + " and " + j + " overlap");
                    }
                }
            }
        }
        // north-facing: everything lies inside the work surface (z 3..13) and the gem is on the player's side (small z)
        float[] g = AnvilLayout.gem();
        float[] i0 = AnvilLayout.ingot(0, 3);
        check(h, g[1] - AnvilLayout.GEM_SIZE / 2 >= 3 && i0[1] + AnvilLayout.INGOT_SIZE / 2 <= 13, "within the work surface");
        check(h, g[1] < i0[1], "gem in front of the ingots");
        check(h, AnvilLayout.ingot(0, 1)[0] == AnvilLayout.gem()[0], "single ingot centred");
        h.succeed();
    }
}
