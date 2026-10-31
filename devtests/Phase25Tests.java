package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.jei.display.AnvilRecipeRow;
import com.holeydigsite.compat.jei.display.CastingRow;
import com.holeydigsite.compat.jei.display.CrateOddsRow;
import com.holeydigsite.compat.jei.display.CrucibleMeltingRow;
import com.holeydigsite.compat.jei.display.ForgeRecipeRow;
import com.holeydigsite.compat.jei.display.GemCuttingRow;
import com.holeydigsite.compat.jei.display.PanningRow;
import com.holeydigsite.compat.jei.display.ShopInfoRow;
import com.holeydigsite.forge.Metals;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.tier.DirtTier;
import com.holeydigsite.compat.jei.display.ShopInfoRow;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.CastingMoldBlock;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.sluice.SluiceUpgrade;
import com.holeydigsite.tier.Tiers;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * JEI Display Models (Phase 25): validates that every machine recipe, meltable, cast weight,
 * clump kind, gem, crate tier, and shop item translates into correct display rows with accurate data.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase25Tests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /** The arguments of a translatable component as strings. */
    private static List<String> args(Component c) {
        if (!(c.getContents() instanceof TranslatableContents t)) return List.of();
        return java.util.Arrays.stream(t.getArgs()).map(o -> o instanceof Component cc ? cc.getString() : String.valueOf(o)).toList();
    }

    private static String k(String name) {
        return "jei.holeydigsite." + name;
    }

    @GameTest(template = "empty")
    public static void forgeRecipes_everyRecipeProducesRow(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        List<ForgeRecipeRow> rows = ForgeRecipeRow.loadAll(reg);
        check(h, rows.size() == 14, "expected 14 forge recipe rows, got " + rows.size());

        boolean foundAssayer = false;
        boolean foundDrillT2 = false;
        boolean foundDrillT3 = false;

        for (ForgeRecipeRow r : rows) {
            check(h, !r.result().isEmpty(), "empty result in " + r.id());
            check(h, !r.metals().isEmpty(), "empty metals in " + r.id());
            check(h, key(r.tierLabel()).equals(k("tier")), "bad tier label in " + r.id());

            for (var m : r.metals().entrySet()) {
                check(h, m.getValue() > 0, "non-positive metal grams in " + r.id());
            }

            if (r.id().getPath().equals("assayers_pick")) {
                foundAssayer = true;
                check(h, args(r.tierLabel()).equals(List.of("III")), "assayer's pick should be Tier III");
                check(h, r.metals().get(HoleyDigsite.id("orchremia")) == 600, "assayerorchremia should be 600g");
                check(h, r.metals().get(HoleyDigsite.id("silvane")) == 120, "assayersilvane should be 120g");
                check(h, r.parts().size() == 1 && r.parts().get(0).is(ModItems.PROSPECTORS_PICK.get()), "assayer needs prospectors pick");
            } else if (r.id().getPath().equals("mining_drill_t2")) {
                foundDrillT2 = true;
                check(h, args(r.tierLabel()).equals(List.of("II")), "drill t2 should be Tier II");
            } else if (r.id().getPath().equals("mining_drill_t3")) {
                foundDrillT3 = true;
                check(h, args(r.tierLabel()).equals(List.of("III")), "drill t3 should be Tier III");
            }
        }

        check(h, foundAssayer, "missing assayers_pick row");
        check(h, foundDrillT2, "missing mining_drill_t2 row");
        check(h, foundDrillT3, "missing mining_drill_t3 row");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilRecipes_everyRecipeProducesRow(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        List<AnvilRecipeRow> rows = AnvilRecipeRow.loadAll(reg);
        check(h, rows.size() == 5, "expected 5 anvil recipe rows, got " + rows.size());

        boolean foundSword = false;
        boolean foundAxe = false;
        boolean foundDagger = false;
        boolean foundRing = false;
        boolean foundNecklace = false;

        for (AnvilRecipeRow r : rows) {
            check(h, !r.result().isEmpty(), "empty result in " + r.id());
            check(h, r.ingots() >= 1 && r.ingots() <= 3, "ingots must be 1-3 in " + r.id());
            check(h, r.minGrams() >= 10, "minGrams too low in " + r.id());
            check(h, r.hits() >= 4, "hits too low in " + r.id());
            check(h, r.bonus() > 0, "bonus must be positive in " + r.id());
            check(h, !r.note().getString().isEmpty(), "note empty in " + r.id());

            String path = r.id().getPath();
            switch (path) {
                case "sword" -> {
                    foundSword = true;
                    check(h, r.ingots() == 3, "sword needs 3 ingots");
                    check(h, r.gemRule() == AnvilRecipe.GemRule.NONE, "sword takes no gem");
                    check(h, r.gear().isPresent() && r.gear().get().fullGrams() == 750, "sword fullGrams 750");
                }
                case "axe" -> {
                    foundAxe = true;
                    check(h, r.ingots() == 2, "axe needs 2 ingots");
                    check(h, r.gemRule() == AnvilRecipe.GemRule.NONE, "axe takes no gem");
                    check(h, r.gear().isPresent() && r.gear().get().fullGrams() == 500, "axe fullGrams 500");
                }
                case "dagger" -> {
                    foundDagger = true;
                    check(h, r.ingots() == 1, "dagger needs 1 ingot");
                    check(h, r.gemRule() == AnvilRecipe.GemRule.NONE, "dagger takes no gem");
                    check(h, r.gear().isPresent() && r.gear().get().fullGrams() == 250, "dagger fullGrams 250");
                }
                case "ring" -> {
                    foundRing = true;
                    check(h, r.ingots() == 1, "ring needs 1 ingot");
                    check(h, r.gemRule() == AnvilRecipe.GemRule.OPTIONAL, "ring gem optional");
                    check(h, r.wear().isPresent() && r.wear().get().fullGrams() == 200, "ring fullGrams 200");
                    check(h, r.gemBonus().isPresent() && r.gemBonus().get() == 0.4, "ring gem bonus 40%");
                }
                case "necklace" -> {
                    foundNecklace = true;
                    check(h, r.ingots() == 2, "necklace needs 2 ingots");
                    check(h, r.gemRule() == AnvilRecipe.GemRule.REQUIRED, "necklace gem required");
                    check(h, r.wear().isPresent() && r.wear().get().fullGrams() == 400, "necklace fullGrams 400");
                }
            }
        }

        check(h, foundSword && foundAxe && foundDagger && foundRing && foundNecklace, "missing anvil piece rows");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crucibleMelting_everyMeltableProducesRow(GameTestHelper h) {
        List<CrucibleMeltingRow> rows = CrucibleMeltingRow.loadAll();
        check(h, rows.size() >= 16, "expected at least 16 meltables, got " + rows.size());

        int folkironCount = 0, goldchroCount = 0, orchremiaCount = 0, silvaneCount = 0, outbackGoldCount = 0;
        for (CrucibleMeltingRow r : rows) {
            check(h, !r.input().isEmpty(), "empty input stack");
            check(h, r.grams() > 0, "non-positive grams");
            check(h, !r.outputIngot().isEmpty(), "empty output ingot");
            check(h, key(r.note()).equals(k("crucible_note")), "note should be the crucible note");

            String metal = r.metal().getPath();
            switch (metal) {
                case "folkiron" -> folkironCount++;
                case "goldchro" -> goldchroCount++;
                case "orchremia" -> orchremiaCount++;
                case "silvane" -> silvaneCount++;
                case "outback_gold" -> outbackGoldCount++;
                default -> h.fail("unknown metal: " + metal);
            }

            // Gems must never be meltable
            String inputPath = BuiltInRegistries.ITEM.getKey(r.input().getItem()).getPath();
            check(h, !inputPath.contains("ruby") && !inputPath.contains("sapphire") && !inputPath.contains("topaz")
                    && !inputPath.contains("opal") && !inputPath.contains("potch") && !inputPath.contains("diamond"),
                    "gem was included in crucible melting: " + inputPath);
        }

        check(h, folkironCount == 4, "expected 4 folkiron meltables (3 chunks + 1 ingot), got " + folkironCount);
        check(h, goldchroCount == 4, "expected 4 goldchro meltables, got " + goldchroCount);
        check(h, orchremiaCount == 4, "expected 4 orchremia meltables, got " + orchremiaCount);
        check(h, silvaneCount == 4, "expected 4 silvane meltables, got " + silvaneCount);
        check(h, outbackGoldCount == 4, "expected 4 outback_gold meltables (3 chunks + 1 ingot), got " + outbackGoldCount);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void casting_everyWeightProducesRow(GameTestHelper h) {
        List<CastingRow> rows = CastingRow.loadAll();
        check(h, rows.size() == 40, "expected 40 casting rows (4 metals * 10 weights), got " + rows.size());

        for (CastingRow r : rows) {
            check(h, r.grams() > 0, "non-positive grams");
            check(h, r.castingMoldAllowed(), "casting mold should allow all weights");
            check(h, r.moldAllowed() == (r.grams() <= 200), "plain mold allowed only up to 200g");
            check(h, IngotItem.grams(r.outputIngot()) == r.grams(), "output ingot weight mismatch");
            check(h, !r.moldNote().getString().isEmpty(), "empty mold note");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void panning_everyClumpKindProducesRow(GameTestHelper h) {
        List<PanningRow> rows = PanningRow.loadAll();
        check(h, rows.size() == 11, "expected 11 clump kinds, got " + rows.size());

        for (PanningRow r : rows) {
            check(h, r.clumpKind() >= 1 && r.clumpKind() <= 11, "bad clump kind: " + r.clumpKind());
            check(h, !r.clumpStack().isEmpty(), "empty clump stack for kind " + r.clumpKind());
            check(h, !r.baseDrops().isEmpty(), "base drops empty for kind " + r.clumpKind());
            check(h, !r.seedDrops().isEmpty(), "seed drops empty for kind " + r.clumpKind());

            if (r.clumpKind() == DirtTier.GRAVEL) {
                boolean hasRiffle = r.upgradeNotes().stream().anyMatch(c -> key(c).equals(k("panning_riffle")));
                check(h, hasRiffle, "gravel missing Riffle upgrade note");
            } else if (r.clumpKind() == DirtTier.SAND) {
                boolean hasMesh = r.upgradeNotes().stream().anyMatch(c -> key(c).equals(k("panning_mesh")));
                check(h, hasMesh, "sand missing Fine Mesh upgrade note");
                check(h, !r.bonusDrops().isEmpty(), "sand missing bonus drops");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gemCutting_everyUncutGemProducesRow(GameTestHelper h) {
        List<GemCuttingRow> rows = GemCuttingRow.loadAll(h.getLevel().registryAccess());
        check(h, rows.size() == 15, "expected 15 gem cutting rows (3 base gems * 3 sizes + 6 event gems), got " + rows.size());

        for (GemCuttingRow r : rows) {
            String uncutPath = BuiltInRegistries.ITEM.getKey(r.uncut().getItem()).getPath();
            String cutPath = BuiltInRegistries.ITEM.getKey(r.cut().getItem()).getPath();
            check(h, uncutPath.startsWith("uncut_"), "uncut gem should start with uncut_: " + uncutPath);
            check(h, uncutPath.substring("uncut_".length()).equals(cutPath), "cut item mismatch: " + uncutPath + " -> " + cutPath);
            check(h, !r.note().getString().isEmpty(), "note empty in gem cutting");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void miningCrates_everyTierProducesRow(GameTestHelper h) {
        List<CrateOddsRow> rows = CrateOddsRow.loadAll();
        check(h, rows.size() == 5, "expected 5 crate tiers (Prospector, Outback and the 3 old Mining Crates), got " + rows.size());

        for (CrateOddsRow r : rows) {
            check(h, !r.crateStack().isEmpty(), "crate stack empty in " + r.tierId());
            check(h, !r.oddsSummary().getString().isEmpty(), "odds summary empty in " + r.tierId());
            check(h, !r.pools().isEmpty(), "pools empty in " + r.tierId());

            for (CrateOddsRow.RarityPool pool : r.pools()) {
                check(h, pool.percent() > 0, "pool percent should be > 0 in " + r.tierId());
                check(h, !pool.rewards().isEmpty(), "pool rewards empty for " + pool.rarity() + " in " + r.tierId());
                for (CrateOddsRow.RewardEntry reward : pool.rewards()) {
                    check(h, !reward.icon().isEmpty(), "reward icon empty in " + r.tierId());
                    check(h, !reward.label().getString().isEmpty(), "reward label empty in " + r.tierId());
                    check(h, reward.weight() >= 1, "reward weight < 1 in " + r.tierId());
                }
            }

            if (r.tierId().equals("wooden_crate")) {
                check(h, r.panFindOneIn() == 0, "the old wooden crate is not found by panning any more");
            }
            if (r.tierId().equals("prospector_crate")) {
                check(h, r.panFindOneIn() == 2000, "prospector crate pan find rate should be 2000");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shopInfo_everyShopItemProducesRow(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        List<ShopInfoRow> rows = ShopInfoRow.loadAll(reg);
        check(h, rows.size() >= 50, "expected at least 50 shop items, got " + rows.size());

        boolean foundSandHead = false;
        boolean foundGravelHead = false;
        boolean foundProspectorPick = false;
        boolean foundBread = false;

        for (ShopInfoRow r : rows) {
            check(h, r.item() != null, "item null in shop row");
            check(h, r.price() > 0 || com.holeydigsite.shop.ShopCatalogue.find(reg, r.item()).map(com.holeydigsite.shop.ShopItem::paidInChips).orElse(false),
                    "price <= 0 in shop row (and not paid in Opal Chips): " + r.item());
            check(h, !key(r.location()).isEmpty(), "location not translatable in shop row");
            check(h, args(r.infoLine()).get(0).equals(r.location().getString()), "info line missing location");

            String path = BuiltInRegistries.ITEM.getKey(r.item()).getPath();
            switch (path) {
                case "sand_head" -> {
                    foundSandHead = true;
                    check(h, key(r.location()).equals(k("location_sand_dunes")), "sand head location: " + key(r.location()));
                    check(h, r.price() == 2560, "sand head price should be 2560");
                    check(h, args(r.infoLine()).get(1).contains("40 gold"), "sand head price text should be 40 gold");
                }
                case "gravel_head" -> {
                    foundGravelHead = true;
                    check(h, key(r.location()).equals(k("location_gravel_flats")), "gravel head location: " + key(r.location()));
                    check(h, r.price() == 2048, "gravel head price should be 2048 (32 gold)");
                    check(h, args(r.infoLine()).get(1).contains("32 gold"), "gravel head price text should be 32 gold");
                }
                case "prospectors_pick" -> {
                    foundProspectorPick = true;
                    check(h, key(r.location()).equals(k("location_deep_dig")), "prospectors pick location: " + key(r.location()));
                    check(h, r.price() == 2048, "prospectors pick price should be 2048 (32 gold)");
                    check(h, args(r.infoLine()).get(1).contains("32 gold"), "prospectors pick price text should be 32 gold");
                }
                case "bread" -> {
                    foundBread = true;
                    check(h, key(r.location()).equals(k("location_trading_post")), "bread location: " + key(r.location()));
                    check(h, r.price() == 4, "bread price should be 4");
                }
            }
        }

        check(h, foundSandHead && foundGravelHead && foundProspectorPick && foundBread, "missing essential shop rows");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void notes_numbersComeFromGameData(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();

        List<String> crucible = args(CrucibleMeltingRow.crucibleNote());
        check(h, crucible.contains(String.valueOf(Tiers.stat("crucible", "capacity"))), "crucible note misses capacity: " + crucible);
        check(h, crucible.contains(String.valueOf(Tiers.stat("large_crucible", "capacity"))), "crucible note misses large capacity: " + crucible);
        check(h, crucible.get(0).equals(String.valueOf(20 / Tiers.stat("crucible", "ticks_per_gram"))), "crucible g/s: " + crucible);
        check(h, crucible.get(2).equals(String.valueOf(20 / Tiers.stat("large_crucible", "ticks_per_gram"))), "large crucible g/s: " + crucible);
        check(h, crucible.get(4).equals(String.valueOf(Tiers.stat("hot_forge", "heat") / Tiers.stat("forge", "heat"))), "hot forge factor: " + crucible);

        int maxMold = java.util.Arrays.stream(MoldBlock.TARGETS).max().getAsInt();
        int maxCasting = java.util.Arrays.stream(CastingMoldBlock.TARGETS).max().getAsInt();
        for (CastingRow r : CastingRow.loadAll()) {
            List<String> a = args(r.moldNote());
            if (r.moldAllowed()) {
                check(h, a.get(0).equals(String.valueOf(maxMold)), "mold max grams: " + a);
                check(h, a.get(1).equals(String.valueOf(MoldBlock.COOL_TICKS / 20)), "mold seconds: " + a);
                check(h, a.get(2).equals(String.valueOf(CastingMoldBlock.COOL_TICKS / 20)), "casting mold seconds: " + a);
            } else {
                check(h, a.get(0).equals(String.valueOf(maxCasting)), "casting mold max grams: " + a);
                check(h, a.get(1).equals(String.valueOf(CastingMoldBlock.COOL_TICKS / 20)), "casting mold seconds: " + a);
            }
        }

        for (PanningRow r : PanningRow.loadAll()) {
            for (Component c : r.upgradeNotes()) {
                if (key(c).equals(k("panning_riffle"))) {
                    check(h, args(c).get(0).equals(String.valueOf(Math.round(SluiceUpgrade.RIFFLE.extraChance() * 100))), "riffle percent: " + args(c));
                } else if (key(c).equals(k("panning_mesh"))) {
                    check(h, args(c).get(0).equals(String.valueOf(Math.round(SluiceUpgrade.MESH.extraChance() * 100))), "mesh percent: " + args(c));
                }
            }
        }

        for (GemCuttingRow r : GemCuttingRow.loadAll(reg)) {
            var info = Selling.resource(r.uncut());
            if (com.holeydigsite.event.EventDefs.isEventGem(r.uncut())) {
                check(h, r.uncutPercent() == 50, "event gem uncut percent: " + r.uncutPercent());
                check(h, args(r.note()).equals(List.of("50")), "event gem note args: " + args(r.note()));
                continue;
            }
            var price = Selling.price(reg, info.material());
            check(h, price != null, "no sell value for " + info.material());
            int expected = (int) Math.round(price.perGram(info.form()) / price.copperPerGram() * 100);
            check(h, r.uncutPercent() == expected, "uncut percent " + r.uncutPercent() + " vs " + expected);
            check(h, args(r.note()).equals(List.of(String.valueOf(expected))), "gem note args: " + args(r.note()));
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void langKeys_everyJeiKeyExists(GameTestHelper h) {
        Path src = null;
        for (String guess : new String[]{"src/main/java/com/holeydigsite/compat/jei", "../src/main/java/com/holeydigsite/compat/jei"}) {
            if (Files.isDirectory(Path.of(guess))) {
                src = Path.of(guess);
                break;
            }
        }
        check(h, src != null, "JEI source folder not found");
        Path lang = Path.of("src/main/resources/assets/holeydigsite/lang/en_us.json");
        if (!Files.exists(lang)) lang = Path.of("../src/main/resources/assets/holeydigsite/lang/en_us.json");
        String langText;
        Set<String> used = new HashSet<>();
        try {
            langText = Files.readString(lang);
            try (Stream<Path> files = Files.walk(src)) {
                Pattern direct = Pattern.compile("JeiText\\.t\\(\\s*\"([a-z0-9_]+)\"\\s*[,)]");
                Pattern indirect = Pattern.compile("\"((?:anvil_gem|location)_[a-z_]*[a-z])\"");
                for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    String code = Files.readString(f);
                    check(h, !code.contains("Component.literal("), "Component.literal left in " + f.getFileName());
                    Matcher m = direct.matcher(code);
                    while (m.find()) used.add(m.group(1));
                    m = indirect.matcher(code);
                    while (m.find()) used.add(m.group(1));
                }
            }
        } catch (IOException e) {
            h.fail("could not scan JEI sources: " + e);
            return;
        }
        for (String site : ShopInfoRow.KNOWN_SITES) used.add("location_" + site);
        check(h, used.size() >= 50, "expected at least 50 JEI keys, found " + used.size());
        for (String key : used) {
            check(h, langText.contains("\"jei.holeydigsite." + key + "\""), "missing lang key jei.holeydigsite." + key);
        }
        h.succeed();
    }
}
