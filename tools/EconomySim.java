import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Holey Digsite economy check for the three panning loot tables (Phase 7: own resources, Dirtrock tiers, Sell Pad).
 *
 * <p>Reads, from the project folder (or the resources folder given as the first argument):</p>
 * <ul>
 *   <li>{@code data/holeydigsite/loot_table/panning/dirt_t1.json .. dirt_t3.json} - what one scoop/clump gives,</li>
 *   <li>{@code data/holeydigsite/data_maps/item/resource_weights.json} - grams + material per item,</li>
 *   <li>{@code data/holeydigsite/holeydigsite/sell_values/*.json} - Sell Pad price per gram per material,</li>
 * </ul>
 * computes the EXACT expected Sell Pad value per scoop and the chance of Goldchro / Ruby / large Ruby per scoop for each
 * tier, double-checks with a Monte Carlo run and reports the gold/hour of one drill + one sluice per tier.
 * No dependencies:</p>
 *
 * <pre>    java tools/EconomySim.java</pre>
 *
 * <p>Supports what the panning tables use: pools with {@code rolls} (constant), an optional
 * {@code minecraft:random_chance} condition, item entries with {@code weight}, and {@code set_count} with a
 * constant or {@code minecraft:uniform} count (inclusive integers, like vanilla). Item value = floor(grams x price per
 * gram), like {@code Selling.unitValue} in the mod.</p>
 */
public class EconomySim {
    static final int SILVER = 8, GOLD = 64;
    /** Buying 1 dirt (= 1 scoop) from the trader costs 1 gold coin. */
    static final int DIRT_PRICE = 1 * GOLD;

    // --- targets ------------------------------------------------------------------------------------
    /** Tier 1: average Sell Pad value per scoop as a multiple of the dirt price. */
    static final double T1_MIN_RATIO = 1.15, T1_MAX_RATIO = 1.35;
    /** Tier 2 / 3 value relative to tier 1 (about 1.6x / 2.2x). */
    static final double[] TIER_RATIO = {0, 1.0, 1.6, 2.2};
    /** Some Ruby (any size): 1 in 40 / 20 / 10 scoops. Goldchro (any size) at tier 1: 1 in 8. */
    static final double[] RUBY_ONE_IN = {0, 40, 20, 10};
    static final double GOLDCHRO_T1_ONE_IN = 8;
    /** "About": accept +-10% on the tier ratios and +-20% on the 1-in-N targets. */
    static final double RATIO_TOL = 0.10, TOLERANCE = 0.20;

    /** Machines: a Tier 1 drill makes 1 clump per 300 ticks (4/min); a sluice washes 1 per 100 ticks (12/min). */
    static final double DRILL_CLUMPS_PER_MIN = 4, SLUICE_CLUMPS_PER_MIN = 12;
    /** A new line: drill 32 + sluice 48 + intake 8 + 4 pipes 0.5 + 4 belts 1 gold. */
    static final double LINE_COST_GOLD = 32 + 48 + 8 + 0.5 + 1;

    static final int MONTE_CARLO_SCOOPS = 1_000_000;

    record Entry(String item, int weight, int min, int max) {
        double avgCount() { return (min + max) / 2.0; }
    }

    record Pool(int rolls, double chance, List<Entry> entries) {
        int totalWeight() { return entries.stream().mapToInt(Entry::weight).sum(); }
    }

    /** form: raw / ingot / uncut (Phase 12). Targets are checked with the CUT value; uncut is reported separately. */
    record Resource(String material, int grams, String form) {}
    /** Phase 12: price per gram of uncut gems (sell_values uncut_copper_per_gram, default half the cut price). */
    static final Map<String, Double> UNCUT_PER_GRAM = new LinkedHashMap<>();

    static final Map<String, Resource> RESOURCES = new LinkedHashMap<>();
    static final Map<String, Double> PRICE_PER_GRAM = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path res = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        Map<String, Object> weights = (Map<String, Object>) new Json(Files.readString(
                res.resolve("data/holeydigsite/data_maps/item/resource_weights.json"))).parse();
        for (var e : ((Map<String, Object>) weights.get("values")).entrySet()) {
            Map<String, Object> v = (Map<String, Object>) e.getValue();
            RESOURCES.put(e.getKey(), new Resource((String) v.get("material"), ((Number) v.get("grams")).intValue(),
                    (String) v.getOrDefault("form", "raw")));
        }
        Path sellDir = res.resolve("data/holeydigsite/holeydigsite/sell_values");
        try (var files = Files.list(sellDir)) {
            for (Path f : files.sorted().toList()) {
                Map<String, Object> v = (Map<String, Object>) new Json(Files.readString(f)).parse();
                String mat = "holeydigsite:" + f.getFileName().toString().replace(".json", "");
                PRICE_PER_GRAM.put(mat, ((Number) v.get("copper_per_gram")).doubleValue());
                UNCUT_PER_GRAM.put(mat, v.containsKey("uncut_copper_per_gram") ? ((Number) v.get("uncut_copper_per_gram")).doubleValue()
                        : ((Number) v.get("copper_per_gram")).doubleValue() / 2);
            }
        }
        System.out.println("Sell Pad prices (copper per gram): " + PRICE_PER_GRAM);
        System.out.print("Item values (copper):");
        for (String item : RESOURCES.keySet()) System.out.printf(" %s=%d", item.replace("holeydigsite:", ""), price(item));
        System.out.println();
        System.out.printf("Dirt costs %d copper (1 gold); Dirt Clumps 4 for 5 gold = 80 copper each%n", DIRT_PRICE);

        double[] ev = new double[4];
        // Phase 10: expected grams per scoop per material, per tier
        List<Map<String, Double>> gramsPerScoop = new ArrayList<>();
        for (int i = 0; i <= 3; i++) gramsPerScoop.add(new LinkedHashMap<>());
        boolean ok = true;
        for (int tier = 1; tier <= 3; tier++) {
            Path table = res.resolve("data/holeydigsite/loot_table/panning/dirt_t" + tier + ".json");
            List<Pool> pools = parseTable(new Json(Files.readString(table)).parse());
            System.out.println();
            System.out.println("=== TIER " + tier + " (" + table.getFileName() + ") ===");
            for (Pool p : pools) {
                System.out.printf("  pool: rolls=%d chance=%.3f %s%n", p.rolls, p.chance,
                        p.entries.stream().map(e -> e.item.replace("holeydigsite:", "") + " x" + e.min + "-" + e.max + " w" + e.weight).toList());
            }
            // ---- exact ----
            double value = 0;
            for (Pool p : pools) {
                for (Entry e : p.entries) {
                    double perRoll = (double) e.weight / p.totalWeight();
                    value += p.chance * p.rolls * perRoll * e.avgCount() * price(e.item);
                    gramsPerScoop.get(tier).merge(material(e.item), p.chance * p.rolls * perRoll * e.avgCount() * RESOURCES.get(e.item).grams, Double::sum);
                }
            }
            double pGoldchro = pAny(pools, i -> material(i).equals("holeydigsite:goldchro"));
            double pRuby = pAny(pools, i -> material(i).equals("holeydigsite:ruby"));
            double pLargeRuby = pAny(pools, i -> i.equals("holeydigsite:ruby_large"));
            ev[tier] = value;
            System.out.println("  EXACT");
            System.out.printf("    avg Sell Pad value per scoop: %.2f copper = %.3fx dirt price = %.2fx tier 1%n", value, value / DIRT_PRICE, value / ev[1]);
            System.out.printf("    Goldchro 1 in %.1f, Ruby 1 in %.1f, large Ruby 1 in %.0f scoops%n", 1 / pGoldchro, 1 / pRuby, 1 / pLargeRuby);

            // ---- Monte Carlo ----
            Random random = new Random(12345 + tier);
            double total = 0;
            long g = 0, r = 0;
            for (int i = 0; i < MONTE_CARLO_SCOOPS; i++) {
                boolean hasG = false, hasR = false;
                for (Pool p : pools) {
                    for (int k = 0; k < p.rolls; k++) {
                        if (random.nextDouble() >= p.chance) continue;
                        Entry e = pick(p, random);
                        int count = e.min + random.nextInt(e.max - e.min + 1);
                        total += count * price(e.item);
                        hasG |= material(e.item).equals("holeydigsite:goldchro");
                        hasR |= material(e.item).equals("holeydigsite:ruby");
                    }
                }
                if (hasG) g++;
                if (hasR) r++;
            }
            System.out.printf("  MONTE CARLO (%,d scoops): avg %.2f copper, Goldchro 1 in %.1f, Ruby 1 in %.1f%n",
                    MONTE_CARLO_SCOOPS, total / MONTE_CARLO_SCOOPS, (double) MONTE_CARLO_SCOOPS / g, (double) MONTE_CARLO_SCOOPS / r);

            // ---- machines ----
            double goldPerHour = DRILL_CLUMPS_PER_MIN * 60 * value / GOLD;
            System.out.printf("  1 drill + 1 sluice: %.0f clumps/h -> %.0f gold/h (%.1f gold/min); a new line (%.1f gold) pays back in %.0f min%n",
                    DRILL_CLUMPS_PER_MIN * 60, goldPerHour, goldPerHour / 60, LINE_COST_GOLD, LINE_COST_GOLD / (goldPerHour / 60));
            System.out.printf("  1 sluice + 3 drills (sluice full): %.0f gold/h%n", SLUICE_CLUMPS_PER_MIN * 60 * value / GOLD);

            // ---- checks ----
            boolean valueOk = tier == 1
                    ? value >= T1_MIN_RATIO * DIRT_PRICE && value <= T1_MAX_RATIO * DIRT_PRICE
                    : Math.abs(value / ev[1] / TIER_RATIO[tier] - 1) <= RATIO_TOL;
            boolean rubyOk = Math.abs(pRuby * RUBY_ONE_IN[tier] - 1) <= TOLERANCE;
            boolean goldchroOk = tier != 1 || Math.abs(pGoldchro * GOLDCHRO_T1_ONE_IN - 1) <= TOLERANCE;
            boolean largeRare = pLargeRuby < 1.0 / 50;
            System.out.printf("  checks: value %s, ruby %s, goldchro %s, large ruby rare %s%n",
                    valueOk ? "OK" : "FAIL", rubyOk ? "OK" : "FAIL", goldchroOk ? "OK" : "FAIL", largeRare ? "OK" : "FAIL");
            ok &= valueOk && rubyOk && goldchroOk && largeRare;
        }
        // Phase 11: gravel and sand (one tier each), new rocks on the land plots
        Map<String, Double> gravelGrams = new LinkedHashMap<>(), sandGrams = new LinkedHashMap<>();
        double evGravel = rockTable(res, "gravel", 1.8, 25, "holeydigsite:sapphire", ev[1], gravelGrams);
        double evSand = rockTable(res, "sand", 2.5, 20, "holeydigsite:topaz", ev[1], sandGrams);
        ok &= evGravel > 0 && evSand > 0;
        PLOT_T2_PER_MIN.put("holeydigsite:orchremia", gravelGrams.getOrDefault("holeydigsite:orchremia", 0.0) * T2_DRILL_CLUMPS_PER_MIN);
        PLOT_T2_PER_MIN.put("holeydigsite:silvane", sandGrams.getOrDefault("holeydigsite:silvane", 0.0) * T2_DRILL_CLUMPS_PER_MIN);
        PLOT_T2_PER_MIN.put("holeydigsite:goldchro", sandGrams.getOrDefault("holeydigsite:goldchro", 0.0) * T2_DRILL_CLUMPS_PER_MIN);
        PLOT_T2_PER_MIN.put("holeydigsite:folkiron", gravelGrams.getOrDefault("holeydigsite:folkiron", 0.0) * T2_DRILL_CLUMPS_PER_MIN);
        ok &= tier2(res, gramsPerScoop, ev);
        ok &= plots(ev, evGravel, evSand, gravelGrams, sandGrams, gramsPerScoop.get(1));
        ok &= gemCutter(res);
        ok &= phase13(res, gramsPerScoop.get(1));
        ok &= phase14(ev[1], evGravel, evSand);
        ok &= phase16(res);
        ok &= phase18(res, ev[1]);
        ok &= phase19(res, gravelGrams, sandGrams);
        ok &= phase20(res);
        ok &= phase21(res, ev, evGravel, evSand);
        ok &= phase21b(res);
        ok &= phase21f(res, ev[1]);
        ok &= phase21i(res);
        System.out.println();
        System.out.println(ok ? "RESULT: all targets met" : "RESULT: targets NOT met - adjust weights");
        System.exit(ok ? 0 : 1);
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 11: gravel / sand tables, land plot prices, Tier 3 drill
    // ------------------------------------------------------------------------------------------------

    /** Analyses panning/<name>.json; returns its value per clump (or -1 if a target is missed by more than the tolerance). */
    static double rockTable(Path res, String name, double targetRatio, double gemOneIn, String gemMaterial, double evT1,
                            Map<String, Double> grams) throws Exception {
        Path table = res.resolve("data/holeydigsite/loot_table/panning/" + name + ".json");
        List<Pool> pools = parseTable(new Json(Files.readString(table)).parse());
        double value = 0;
        for (Pool p : pools) {
            for (Entry e : p.entries) {
                double perRoll = (double) e.weight / p.totalWeight();
                value += p.chance * p.rolls * perRoll * e.avgCount() * price(e.item);
                grams.merge(material(e.item), p.chance * p.rolls * perRoll * e.avgCount() * RESOURCES.get(e.item).grams, Double::sum);
            }
        }
        double pGem = pAny(pools, i -> material(i).equals(gemMaterial));
        boolean valueOk = Math.abs(value / evT1 / targetRatio - 1) <= RATIO_TOL;
        boolean gemOk = Math.abs(pGem * gemOneIn - 1) <= TOLERANCE;
        System.out.println();
        System.out.printf("=== %s (%s) ===%n", name.toUpperCase(), table.getFileName());
        System.out.printf("  avg value per clump %.1f copper = %.2fx tier 1 dirt (target %.1fx) [%s]; %s 1 in %.1f (target %.0f) [%s]%n",
                value, value / evT1, targetRatio, valueOk ? "OK" : "FAIL", gemMaterial.replace("holeydigsite:", ""), 1 / pGem, gemOneIn, gemOk ? "OK" : "FAIL");
        System.out.printf("  grams per clump: %s%n", grams.entrySet().stream().map(e -> String.format("%s %.2f g", e.getKey().replace("holeydigsite:", ""), e.getValue())).toList());
        System.out.printf("  Tier 1 line %.0f gold/h, Tier 2 line %.0f gold/h%n", DRILL_CLUMPS_PER_MIN * 60 * value / GOLD, T2_DRILL_CLUMPS_PER_MIN * 60 * value / GOLD);
        return valueOk && gemOk ? value : -1;
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 12: uncut gems + Gem Cutter
    // ------------------------------------------------------------------------------------------------

    /** Gem Cutter payback target: at most this many minutes of the line it is bought for (Tier 2 line on Abyssal Dirtrock). */
    static final double CUTTER_PAYBACK_MAX_MIN = 60, CUTTER_PAYBACK_MIN_MIN = 20;
    /** Phase 17: gem share of the (cut) value per clump on the gem-rich rocks. */
    static final double GEM_SHARE_MIN = 0.10, GEM_SHARE_MAX = 0.20;

    /** Value of one item when sold as it comes out of the pan (uncut gems at the uncut price). */
    static int priceAsPanned(String item) {
        Resource r = RESOURCES.get(item);
        if (!"uncut".equals(r.form)) return price(item);
        return (int) Math.floor(r.grams * UNCUT_PER_GRAM.get(r.material) + 1e-9);
    }

    /** Value per clump sold uncut vs cut, per table; Gem Cutter payback per line. */
    @SuppressWarnings("unchecked")
    static boolean gemCutter(Path res) throws Exception {
        System.out.println();
        System.out.println("=== PHASE 12: UNCUT GEMS + GEM CUTTER (loot gives uncut gems; the Gem Cutter cuts them to the full price) ===");
        String[] tables = {"dirt_t1", "dirt_t2", "dirt_t3", "gravel", "sand"};
        Map<String, double[]> v = new LinkedHashMap<>();
        boolean ok0 = true;
        for (String t : tables) {
            List<Pool> pools = parseTable(new Json(Files.readString(res.resolve("data/holeydigsite/loot_table/panning/" + t + ".json"))).parse());
            double cut = 0, uncut = 0;
            for (Pool p : pools) for (Entry e : p.entries) {
                double n = p.chance * p.rolls * (double) e.weight / p.totalWeight() * e.avgCount();
                cut += n * price(e.item);
                uncut += n * priceAsPanned(e.item);
            }
            v.put(t, new double[]{uncut, cut});
            double gemCut = 0;
            for (Pool p : pools) for (Entry e : p.entries) {
                if ("uncut".equals(RESOURCES.get(e.item).form))
                    gemCut += p.chance * p.rolls * (double) e.weight / p.totalWeight() * e.avgCount() * price(e.item);
            }
            double share = gemCut / cut;
            boolean rich = t.equals("dirt_t3") || t.equals("gravel") || t.equals("sand");
            if (rich) ok0 &= share >= GEM_SHARE_MIN * 0.9 && share <= GEM_SHARE_MAX * 1.1;
            System.out.printf("  %-8s gem share of value per clump: %.1f%% (%.1f of %.1f copper)%s%n", t, 100 * share, gemCut, cut,
                    rich ? String.format(" (target %.0f-%.0f%%) [%s]", 100 * GEM_SHARE_MIN, 100 * GEM_SHARE_MAX,
                            share >= GEM_SHARE_MIN * 0.9 && share <= GEM_SHARE_MAX * 1.1 ? "OK" : "OUT OF RANGE") : "");
            System.out.printf("  %-8s per clump: %.1f copper sold uncut, %.1f copper cut (+%.1f, +%.0f%%)%n", t, uncut, cut, cut - uncut, 100 * (cut / uncut - 1));
        }
        Map<String, Object> e = (Map<String, Object>) new Json(Files.readString(Path.of("src/main/resources/data/holeydigsite/holeydigsite/shop_items/gem_cutter.json"))).parse();
        double priceGold = ((Number) e.get("price")).doubleValue() / GOLD;
        String[][] lines = {{"Tier 1 line, Dirtrock", "dirt_t1", "4"}, {"Tier 2 line, Abyssal Dirtrock", "dirt_t3", "8"},
                {"Tier 2 line, Gravelrock", "gravel", "8"}, {"Tier 2 line, Sandrock", "sand", "8"}};
        boolean ok = ok0;
        for (String[] l : lines) {
            double[] x = v.get(l[1]);
            double gainPerMin = (x[1] - x[0]) * Double.parseDouble(l[2]) / GOLD;
            double minutes = priceGold / gainPerMin;
            boolean target = !l[1].equals("dirt_t1");
            if (target) ok &= minutes >= CUTTER_PAYBACK_MIN_MIN * 0.9 && minutes <= CUTTER_PAYBACK_MAX_MIN * 1.1;
            System.out.printf("  Gem Cutter (%.0f gold) on a %-30s +%.2f gold/min -> pays back in %.0f min%s%n", priceGold, l[0] + ":",
                    gainPerMin, minutes, target ? String.format(" (target %.0f-%.0f) [%s]", CUTTER_PAYBACK_MIN_MIN, CUTTER_PAYBACK_MAX_MIN,
                            minutes >= CUTTER_PAYBACK_MIN_MIN * 0.9 && minutes <= CUTTER_PAYBACK_MAX_MIN * 1.1 ? "OK" : "OUT OF RANGE") : "");
        }
        return ok;
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 16: plot-exclusive sluice upgrades (must match sluice/SluiceUpgrade.java and the shop_items files)
    // ------------------------------------------------------------------------------------------------

    /** Chance of a free extra wash of the same clump kind (Riffle Insert: gravel; Fine Mesh Screen: sand). */
    static final double RIFFLE_EXTRA = 0.25, MESH_EXTRA = 0.24;
    /** Fine Mesh Screen: extra Topaz roll per sand clump (loot table panning/sand_mesh_bonus). */
    static final String MESH_BONUS_TABLE = "sand_mesh_bonus";
    /** Payback target: 30-60 minutes of a Tier 2 line (8 clumps/min) on the upgrade's own plot. */
    static final double UPGRADE_MIN = 30, UPGRADE_MAX = 60;

    static double tableValue(Path res, String name) throws Exception {
        double v = 0;
        for (Pool p : parseTable(new Json(Files.readString(res.resolve("data/holeydigsite/loot_table/panning/" + name + ".json"))).parse())) {
            for (Entry e : p.entries) {
                v += p.chance * p.rolls * (double) e.weight / p.totalWeight() * e.avgCount() * priceAsPanned(e.item);
            }
        }
        return v;
    }

    static long shopPrice(Path res, String item) throws Exception {
        Path f = res.resolve("data/holeydigsite/holeydigsite/shop_items/" + item + ".json");
        return ((Number) ((Map<String, Object>) new Json(Files.readString(f)).parse()).get("price")).longValue();
    }

    @SuppressWarnings("unchecked")
    static boolean phase16(Path res) throws Exception {
        System.out.println();
        System.out.println("=== PHASE 16: PLOT-EXCLUSIVE ITEMS (upgrades pay back in " + UPGRADE_MIN + "-" + UPGRADE_MAX + " min of a Tier 2 line, sold uncut) ===");
        boolean ok = true;
        double gravel = tableValue(res, "gravel"), sand = tableValue(res, "sand");
        double bonusSand = tableValue(res, MESH_BONUS_TABLE);
        Object[][] rows = {{"riffle_insert", "Gravel Flats / Gravel", gravel, gravel * RIFFLE_EXTRA},
                {"fine_mesh_screen", "Sand Dunes / Sand", sand, sand * MESH_EXTRA + bonusSand}};
        for (Object[] r : rows) {
            double base = (Double) r[2], gain = (Double) r[3];
            long price = shopPrice(res, (String) r[0]);
            double gainPerMin = gain * T2_DRILL_CLUMPS_PER_MIN / GOLD;
            double minutes = price / (double) GOLD / gainPerMin;
            boolean in = minutes >= UPGRADE_MIN * 0.9 && minutes <= UPGRADE_MAX * 1.1;
            ok &= in;
            System.out.printf("  %-17s %-22s +%.1f copper per clump on %.1f (+%.1f%%), %.2f gold/min on a Tier 2 line; %d copper = %.0f gold -> pays back in %.0f min [%s]%n",
                    r[0], r[1], gain, base, 100 * gain / base, gainPerMin, price, price / (double) GOLD, minutes, in ? "OK" : "OUT OF RANGE");
        }
        long pick = shopPrice(res, "prospectors_pick"), drill = shopPrice(res, "mining_drill");
        boolean pickOk = Math.abs(pick / (double) drill - 1) <= 0.25;
        ok &= pickOk;
        System.out.printf("  prospectors_pick  %d copper vs Tier 1 drill %d [%s]%n", pick, drill, pickOk ? "OK" : "FAIL");
        for (String lamp : new String[]{"miners_lantern", "river_stone_lamp", "sandstone_lamp"}) {
            long p = shopPrice(res, lamp);
            ok &= p <= 64;
            System.out.printf("  %-17s %d copper [%s]%n", lamp, p, p <= 64 ? "OK" : "TOO DEAR");
        }
        return ok;
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 18: seeds and farming
    // ------------------------------------------------------------------------------------------------
    /** Seed Screen: about one seed per 8-15 clumps on every clump kind. */
    static final double SEED_ONE_IN_MIN = 8, SEED_ONE_IN_MAX = 15;
    /** A hand-harvested 9x9 field (centre water) of one own crop earns 10-20% of a Tier 1 line. */
    static final double FARM_MIN = 0.10, FARM_MAX = 0.20;
    static final int FARM_PLANTS = 80;
    /** Minutes from planting to the next planting: ~20 min vanilla growth (3 stages, wet farmland, rows) + harvesting/replanting by hand. */
    static final double FARM_CYCLE_MIN = 25;
    /** Seeds per ripe crop: 1 + binomial(3, 0.5714) (the vanilla loot); one goes back into the soil. */
    static final double SEEDS_PER_HARVEST = 1 + 3 * 0.5714286;
    /** Seed Screen payback: its price against the food income of a farm (average of the three crops sold raw), 20-40 min. */
    static final double SCREEN_PAYBACK_MIN = 20, SCREEN_PAYBACK_MAX = 40;

    static double sellValue(Path res, String name) throws Exception {
        Path f = res.resolve("data/holeydigsite/holeydigsite/sell_values/" + name + ".json");
        return ((Number) ((Map<String, Object>) new Json(Files.readString(f)).parse()).get("copper_per_gram")).doubleValue();
    }

    /** Chance that one clump of a kind gives a seed (the table's pool chance; one roll per clump). */
    static double seedChance(Path res, String table) throws Exception {
        List<Pool> pools = parseTable(new Json(Files.readString(res.resolve("data/holeydigsite/loot_table/panning/" + table + ".json"))).parse());
        return pools.get(0).chance * pools.get(0).rolls;
    }

    @SuppressWarnings("unchecked")
    static boolean phase18(Path res, double evDirt) throws Exception {
        System.out.println();
        System.out.println("=== PHASE 18: SEEDS AND FARMING ===");
        boolean ok = true;
        for (String t : new String[]{"seeds_dirt_1", "seeds_dirt_2", "seeds_dirt_3", "seeds_gravel", "seeds_sand"}) {
            double p = seedChance(res, t);
            boolean in = 1 / p >= SEED_ONE_IN_MIN && 1 / p <= SEED_ONE_IN_MAX;
            ok &= in;
            System.out.printf("  %-13s 1 seed per %.1f clumps (target %.0f-%.0f) [%s]%n", t, 1 / p, SEED_ONE_IN_MIN, SEED_ONE_IN_MAX, in ? "OK" : "OUT OF RANGE");
        }
        double t1Hour = evDirt * DRILL_CLUMPS_PER_MIN * 60;       // copper per hour of a Tier 1 line (1 drill + 1 sluice)
        double perHarvestPerHour = FARM_PLANTS * 60.0 / FARM_CYCLE_MIN;
        System.out.printf("  Tier 1 line: %.0f copper/h = %.0f gold/h; Seed Screen: %.1f seeds/h from dirt clumps%n", t1Hour, t1Hour / GOLD,
                DRILL_CLUMPS_PER_MIN * 60 * seedChance(res, "seeds_dirt_1"));
        System.out.printf("  farm: %d plants, %.0f min per cycle = %.0f harvests/h; seeds multiply x%.2f per cycle%n", FARM_PLANTS, FARM_CYCLE_MIN, perHarvestPerHour, SEEDS_PER_HARVEST - 1 + 1);
        // name, items per harvest, raw item, processed item and how many raw per processed item
        Object[][] crops = {
                {"glowroot", 1, "glowroot", "roasted_glowroot", 1},
                {"riverbean", 2, "riverbean", "riverbean_stew", 3},
                {"dune_melon", 3, "dune_melon_slice", "dune_melon_juice", 2}};
        double rawSum = 0;
        for (Object[] c : crops) {
            int per = (Integer) c[1];
            double raw = per * sellValue(res, (String) c[2]);
            double cooked = per * sellValue(res, (String) c[3]) / (Integer) c[4];
            rawSum += raw * perHarvestPerHour;
            for (int k = 0; k < 2; k++) {
                double v = (k == 0 ? raw : cooked) * perHarvestPerHour;
                boolean in = v / t1Hour >= FARM_MIN && v / t1Hour <= FARM_MAX;
                ok &= in;
                System.out.printf("  %-10s sold %-7s %5.1f copper per harvest, farm %5.0f copper/h = %4.1f%% of a Tier 1 line (target 10-20%%) [%s]%n",
                        c[0], k == 0 ? "raw" : "cooked", k == 0 ? raw : cooked, v, 100 * v / t1Hour, in ? "OK" : "OUT OF RANGE");
            }
        }
        // vanilla crops: worth less than the own ones (carrot / potato drop 1 + binomial(3, 0.5714) items, wheat / beetroot 1)
        double ownMin = Double.MAX_VALUE;
        for (Object[] c : crops) ownMin = Math.min(ownMin, (Integer) c[1] * sellValue(res, (String) c[2]));
        for (String v : new String[]{"wheat", "beetroot", "carrot", "potato"}) {
            double per = (v.equals("carrot") || v.equals("potato")) ? SEEDS_PER_HARVEST : 1;
            double h = per * sellValue(res, v);
            boolean in = h < ownMin;
            ok &= in;
            System.out.printf("  vanilla %-9s %4.1f copper per harvest, farm %5.0f copper/h = %4.1f%% (less than the worst own crop, %.1f) [%s]%n", v, h,
                    h * perHarvestPerHour, 100 * h * perHarvestPerHour / t1Hour, ownMin, in ? "OK" : "FAIL");
        }
        double screen = shopPrice(res, "seed_screen");
        double perMin = rawSum / 3 / 60 / GOLD;
        double payback = screen / GOLD / perMin;
        boolean in = payback >= SCREEN_PAYBACK_MIN * 0.9 && payback <= SCREEN_PAYBACK_MAX * 1.1;
        ok &= in;
        System.out.printf("  seed_screen   %.0f copper = %.0f gold; a farm sold raw earns %.2f gold/min -> pays back in %.0f min (target %.0f-%.0f) [%s]%n",
                screen, screen / GOLD, perMin, payback, SCREEN_PAYBACK_MIN, SCREEN_PAYBACK_MAX, in ? "OK" : "OUT OF RANGE");
        double start = DRILL_CLUMPS_PER_MIN * 30 * seedChance(res, "seeds_dirt_1");
        int cycles = (int) Math.ceil(Math.log(FARM_PLANTS / start) / Math.log(SEEDS_PER_HARVEST));
        System.out.printf("  ramp-up: 30 min of a Tier 1 line gives %.0f seeds; each ripe crop returns %.2f seeds (1 replanted), so the field is full after %d cycles = %.0f min%n",
                start, SEEDS_PER_HARVEST, cycles, cycles * FARM_CYCLE_MIN);
        long bread = shopPrice(res, "bread");
        System.out.printf("  starter food: bread %d copper (a Tier 1 scoop is worth %.0f)%n", bread, evDirt);
        ok &= bread <= 16;
        return ok;
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 19: Smithing Anvil (value bonus per piece, anvil + hammer payback, gear stats vs vanilla tiers)
    // ------------------------------------------------------------------------------------------------
    /** Simple pieces (ring, dagger) about +25%, complex ones (necklace, sword, ring with a gem) about +40%, the axe in between. */
    static final double SIMPLE_BONUS = 0.25, COMPLEX_BONUS = 0.40, BONUS_TOL = 0.05;
    /** Anvil + hammer pay back in 30-60 min of smithing a Tier 2 line's metal. */
    static final double SMITH_PAYBACK_MIN = 30, SMITH_PAYBACK_MAX = 60;

    record SmithMetal(double damage, double speed, double durability) {}

    @SuppressWarnings("unchecked")
    static Map<String, Object> readJson(Path f) throws Exception {
        return (Map<String, Object>) new Json(Files.readString(f)).parse();
    }

    /** Mirror of SmithPieces.stats: {scale, damage, speed, durability} of a gear piece from {metal, grams} ingots. */
    static double[] gearStats(Map<String, Object> gear, Map<String, SmithMetal> metals, String[] names, int[] grams) {
        double total = 0, d = 0, s = 0, u = 0;
        for (int i = 0; i < names.length; i++) {
            SmithMetal m = metals.get(names[i]);
            total += grams[i];
            d += m.damage * grams[i];
            s += m.speed * grams[i];
            u += m.durability * grams[i];
        }
        double floor = num(gear.getOrDefault("floor", 0.5));
        double scale = floor + (1 - floor) * Math.min(1.0, total / num(gear.get("full_grams")));
        return new double[]{scale,
                num(gear.get("base_damage")) + d / total * scale * num(gear.getOrDefault("damage_scale", 1.0)),
                Math.max(2.0, s / total * scale),
                Math.max(1, Math.round(u / total * scale * num(gear.getOrDefault("durability_scale", 1.0))))};
    }

    @SuppressWarnings("unchecked")
    static boolean phase19(Path res, Map<String, Double> gravelGrams, Map<String, Double> sandGrams) throws Exception {
        System.out.println();
        System.out.println("=== PHASE 19: SMITHING ANVIL (bonus +25% simple / +40% complex; anvil + hammer pay back in " + (int) SMITH_PAYBACK_MIN + "-" + (int) SMITH_PAYBACK_MAX + " min of a Tier 2 line; gear Folkiron = iron, best = netherite) ===");
        boolean ok = true;
        Path recDir = res.resolve("data/holeydigsite/holeydigsite/anvil_recipes");
        Map<String, Map<String, Object>> recipes = new LinkedHashMap<>();
        try (var files = Files.list(recDir)) {
            for (Path f : files.sorted().toList()) recipes.put(f.getFileName().toString().replace(".json", ""), readJson(f));
        }
        Map<String, SmithMetal> metals = new LinkedHashMap<>();
        try (var files = Files.list(res.resolve("data/holeydigsite/holeydigsite/smithing_materials"))) {
            for (Path f : files.sorted().toList()) {
                Map<String, Object> m = readJson(f);
                if (m.containsKey("durability")) {
                    metals.put("holeydigsite:" + f.getFileName().toString().replace(".json", ""),
                            new SmithMetal(num(m.get("damage")), num(m.get("speed")), num(m.get("durability"))));
                }
            }
        }
        // ---- bonus per piece ----
        double bonusSum = 0;
        for (var e : recipes.entrySet()) {
            double b = num(e.getValue().get("bonus"));
            boolean hasGem = !"none".equals(e.getValue().getOrDefault("gem", "none"));
            String kind = e.getKey();
            boolean simple = kind.equals("ring") || kind.equals("dagger");
            boolean axe = kind.equals("axe");
            boolean in = axe ? b >= SIMPLE_BONUS - 1e-9 && b <= COMPLEX_BONUS + 1e-9
                    : Math.abs(b - (simple ? SIMPLE_BONUS : COMPLEX_BONUS)) <= BONUS_TOL + 1e-9;
            ok &= in;
            bonusSum += b;
            System.out.printf("  %-9s %d ingot(s), gem %-8s %d hits: +%.0f%%%s [%s]%n", kind, ((Number) e.getValue().get("ingots")).intValue(),
                    e.getValue().getOrDefault("gem", "none"), ((Number) e.getValue().get("hits")).intValue(), 100 * b,
                    e.getValue().containsKey("gem_bonus") ? " (with a gem +" + Math.round(100 * num(e.getValue().get("gem_bonus"))) + "%)" : "", in ? "OK" : "OUT OF RANGE");
            if (e.getValue().containsKey("gem_bonus")) {
                double gb = num(e.getValue().get("gem_bonus"));
                boolean gin = Math.abs(gb - COMPLEX_BONUS) <= BONUS_TOL + 1e-9;
                ok &= gin;
                System.out.printf("  %-9s with a gem: +%.0f%% (complex) [%s]%n", kind, 100 * gb, gin ? "OK" : "OUT OF RANGE");
            }
        }
        double avg = bonusSum / recipes.size();
        // ---- payback: smith everything a Tier 2 line makes of metal ----
        long anvil = shopPrice(res, "smithing_anvil"), hammer = shopPrice(res, "blacksmith_hammer");
        double[] ingotPerGram = new double[1];
        Map<String, Double> perMin = new LinkedHashMap<>();
        for (var line : new Object[][]{{"Gravelrock line", gravelGrams}, {"Sandrock line", sandGrams}}) {
            Map<String, Double> grams = (Map<String, Double>) line[1];
            double copperPerMin = 0;
            for (var m : grams.entrySet()) {
                if (!metals.containsKey(m.getKey())) continue;
                Path sv = res.resolve("data/holeydigsite/holeydigsite/sell_values/" + m.getKey().replace("holeydigsite:", "") + ".json");
                Map<String, Object> v = readJson(sv);
                double ingot = num(v.getOrDefault("ingot_copper_per_gram", v.get("copper_per_gram")));
                copperPerMin += m.getValue() * T2_DRILL_CLUMPS_PER_MIN * ingot;
            }
            double gain = copperPerMin * avg;
            double minutes = (anvil + hammer) / gain;
            boolean in = minutes >= SMITH_PAYBACK_MIN * 0.9 && minutes <= SMITH_PAYBACK_MAX * 1.1;
            ok &= in;
            System.out.printf("  %-16s metal %.0f copper/min as ingots (%.1f gold/min); smithed at the average bonus +%.0f%% = +%.2f gold/min; anvil %d + hammer %d copper = %.0f gold -> pays back in %.0f min [%s]%n",
                    line[0], copperPerMin, copperPerMin / GOLD, 100 * avg, gain / GOLD, anvil, hammer, (anvil + hammer) / (double) GOLD, minutes, in ? "OK" : "OUT OF RANGE");
        }
        // ---- gear vs vanilla (damage = attack damage modifier, iron sword 5 / diamond 6 / netherite 7; durability 250 / 1561 / 2031; axe speed 6 / 8 / 9) ----
        Map<String, Object> sword = (Map<String, Object>) recipes.get("sword").get("gear");
        Map<String, Object> axe = (Map<String, Object>) recipes.get("axe").get("gear");
        Map<String, Object> dagger = (Map<String, Object>) recipes.get("dagger").get("gear");
        String F = "holeydigsite:folkiron", O = "holeydigsite:orchremia", G = "holeydigsite:goldchro", S = "holeydigsite:silvane";
        double[] fs = gearStats(sword, metals, new String[]{F, F, F}, new int[]{150, 150, 150});
        boolean fok = Math.abs(fs[1] - 5.0) <= 0.5 && Math.abs(fs[3] - 250) <= 50;
        ok &= fok;
        System.out.printf("  Folkiron sword 3x150 g: damage %.2f, %.0f uses (iron 5 / 250) [%s]%n", fs[1], fs[3], fok ? "OK" : "OUT OF RANGE");
        double[] fa = gearStats(axe, metals, new String[]{F, F}, new int[]{150, 150});
        boolean faok = Math.abs(fa[2] - 6.0) <= 1.0 && Math.abs(fa[3] - 250) <= 50;
        ok &= faok;
        System.out.printf("  Folkiron axe 2x150 g: damage %.2f, mining speed %.2f, %.0f uses (iron 8 / 6 / 250) [%s]%n", fa[1], fa[2], fa[3], faok ? "OK" : "OUT OF RANGE");
        double[] ss = gearStats(sword, metals, new String[]{S, S, S}, new int[]{200, 200, 200});
        boolean sok = Math.abs(ss[1] - 7.0) <= 0.7 && Math.abs(ss[3] - 2031) <= 300 && ss[1] > 6.0 && ss[3] > 1561;
        ok &= sok;
        System.out.printf("  Silvane sword 3x200 g: damage %.2f, %.0f uses (diamond 6 / 1561, netherite 7 / 2031) [%s]%n", ss[1], ss[3], sok ? "OK" : "OUT OF RANGE");
        double[] gs = gearStats(sword, metals, new String[]{S, S, G}, new int[]{200, 200, 200});
        boolean gsok = gs[1] > 6.0 && gs[3] > 1561 * 1.05;
        ok &= gsok;
        System.out.printf("  Silvane/Silvane/Goldchro sword 3x200 g: damage %.2f, %.0f uses (beyond diamond) [%s]%n", gs[1], gs[3], gsok ? "OK" : "OUT OF RANGE");
        double[] gg = gearStats(sword, metals, new String[]{G, G, G}, new int[]{200, 200, 200});
        boolean ggok = gg[1] > 6.0 && gg[3] > 1561;
        ok &= ggok;
        System.out.printf("  Goldchro sword 3x200 g: damage %.2f, %.0f uses (beyond diamond) [%s]%n", gg[1], gg[3], ggok ? "OK" : "OUT OF RANGE");
        double[] oo = gearStats(sword, metals, new String[]{O, O, O}, new int[]{200, 200, 200});
        System.out.printf("  Orchremia sword 3x200 g: damage %.2f, %.0f uses (between iron and diamond: info)%n", oo[1], oo[3]);
        double[] sa = gearStats(axe, metals, new String[]{S, S}, new int[]{200, 200});
        boolean saok = sa[2] >= 8.5 && sa[3] > 1561;
        ok &= saok;
        System.out.printf("  Silvane axe 2x200 g: damage %.2f, mining speed %.2f, %.0f uses (diamond 8 / 1561, netherite 9 / 2031) [%s]%n", sa[1], sa[2], sa[3], saok ? "OK" : "OUT OF RANGE");
        double[] sd = gearStats(dagger, metals, new String[]{S}, new int[]{250});
        System.out.printf("  Silvane dagger 1x250 g: damage %.2f (attack speed 2.4), %.0f uses (info)%n", sd[1], sd[3]);
        double[] light = gearStats(sword, metals, new String[]{F, F, F}, new int[]{25, 25, 25});
        System.out.printf("  lightest Folkiron sword 3x25 g: damage %.2f, %.0f uses (stone is 4 / 131: info)%n", light[1], light[3]);
        // ---- what a piece is worth ----
        double swordIngots = 3 * 200 * num(readJson(res.resolve("data/holeydigsite/holeydigsite/sell_values/silvane.json")).get("ingot_copper_per_gram"));
        System.out.printf("  Silvane sword 3x200 g: ingredients %.0f copper (%.0f gold), sells for %.0f copper (+%.0f%%)%n", swordIngots, swordIngots / GOLD,
                swordIngots * (1 + num(recipes.get("sword").get("bonus"))), 100 * num(recipes.get("sword").get("bonus")));
        return ok;
    }

    /** Mirror of SmithPieces.wearStats: {armor, toughness} of a worn piece. slot = "head" or "curio" (null if the recipe does not allow it). */
    @SuppressWarnings("unchecked")
    static double[] wearStats(Map<String, Object> wear, Map<String, double[]> armour, String[] metals, int[] grams, String slot) {
        Map<String, Object> sl = (Map<String, Object>) wear.get(slot);
        if (sl == null) return new double[]{0, 0};
        double total = 0, a = 0, t = 0;
        for (int i = 0; i < metals.length; i++) {
            total += grams[i];
            a += armour.get(metals[i])[0] * grams[i];
            t += armour.get(metals[i])[1] * grams[i];
        }
        double floor = num(wear.getOrDefault("floor", 0.5));
        double scale = floor + (1 - floor) * Math.min(1.0, total / num(wear.get("full_grams")));
        return new double[]{a / total * scale * num(sl.get("armor")), t / total * scale * num(sl.get("toughness"))};
    }

    /** Phase 20: worn jewellery. Necklace in the head slot ~ helmet of the metal; best Curios set (2 rings + necklace) +3-4 armour, +1-2 toughness. */
    @SuppressWarnings("unchecked")
    static boolean phase20(Path res) throws Exception {
        System.out.println();
        System.out.println("=== PHASE 20: WEARABLE JEWELLERY (necklace on the head = helmet of its metal: Folkiron iron 2/0, best netherite 3/3; best Curios set +3-4 armour, +1-2 toughness) ===");
        boolean ok = true;
        Map<String, Object> neck = (Map<String, Object>) readJson(res.resolve("data/holeydigsite/holeydigsite/anvil_recipes/necklace.json")).get("wear");
        Map<String, Object> ring = (Map<String, Object>) readJson(res.resolve("data/holeydigsite/holeydigsite/anvil_recipes/ring.json")).get("wear");
        Map<String, double[]> armour = new LinkedHashMap<>();
        try (var files = Files.list(res.resolve("data/holeydigsite/holeydigsite/smithing_materials"))) {
            for (Path f : files.sorted().toList()) {
                Map<String, Object> m = readJson(f);
                if (m.containsKey("durability")) {
                    armour.put("holeydigsite:" + f.getFileName().toString().replace(".json", ""), new double[]{num(m.getOrDefault("armor", 0.0)), num(m.getOrDefault("toughness", 0.0))});
                }
            }
        }
        String F = "holeydigsite:folkiron", O = "holeydigsite:orchremia", G = "holeydigsite:goldchro", S = "holeydigsite:silvane";
        double[] f = wearStats(neck, armour, new String[]{F, F}, new int[]{150, 150}, "head");
        boolean fok = Math.abs(f[0] - 2.0) <= 0.25 && f[1] == 0;
        ok &= fok;
        System.out.printf("  Folkiron necklace 2x150 g on the head: %.2f armour, %.2f toughness (iron helmet 2 / 0) [%s]%n", f[0], f[1], fok ? "OK" : "OUT OF RANGE");
        double[] s = wearStats(neck, armour, new String[]{S, S}, new int[]{200, 200}, "head");
        boolean sok = Math.abs(s[0] - 3.0) <= 0.1 && Math.abs(s[1] - 3.0) <= 0.1;
        ok &= sok;
        System.out.printf("  Silvane necklace 2x200 g on the head: %.2f armour, %.2f toughness (netherite helmet 3 / 3) [%s]%n", s[0], s[1], sok ? "OK" : "OUT OF RANGE");
        double[] g = wearStats(neck, armour, new String[]{G, G}, new int[]{200, 200}, "head");
        boolean gok = Math.abs(g[0] - 3.0) <= 0.3 && Math.abs(g[1] - 2.0) <= 0.3;
        ok &= gok;
        System.out.printf("  Goldchro necklace 2x200 g on the head: %.2f armour, %.2f toughness (diamond helmet 3 / 2) [%s]%n", g[0], g[1], gok ? "OK" : "OUT OF RANGE");
        double[] o = wearStats(neck, armour, new String[]{O, O}, new int[]{200, 200}, "head");
        System.out.printf("  Orchremia necklace 2x200 g on the head: %.2f armour, %.2f toughness (between iron and diamond: info)%n", o[0], o[1]);
        double[] r = wearStats(ring, armour, new String[]{S}, new int[]{200}, "curio");
        double[] n = wearStats(neck, armour, new String[]{S, S}, new int[]{200, 200}, "curio");
        double setA = 2 * r[0] + n[0], setT = 2 * r[1] + n[1];
        boolean setOk = setA >= 3.0 && setA <= 4.0 && setT >= 1.0 && setT <= 2.0;
        ok &= setOk;
        System.out.printf("  best Curios set (2 Silvane rings 200 g + Silvane necklace 2x200 g): +%.2f armour, +%.2f toughness (target +3-4 / +1-2; a netherite chestplate is 8 / 3) [%s]%n", setA, setT, setOk ? "OK" : "OUT OF RANGE");
        double[] fr = wearStats(ring, armour, new String[]{F}, new int[]{150}, "curio");
        double[] fn = wearStats(neck, armour, new String[]{F, F}, new int[]{150, 150}, "curio");
        System.out.printf("  Folkiron set (rings 150 g, necklace 2x150 g): +%.2f armour, +%.2f toughness (info)%n", 2 * fr[0] + fn[0], 2 * fr[1] + fn[1]);
        double[] light = wearStats(neck, armour, new String[]{S, S}, new int[]{25, 25}, "head");
        boolean lok = light[0] >= s[0] * 0.5 - 1e-9 && light[0] < s[0];
        ok &= lok;
        System.out.printf("  lightest Silvane necklace 2x25 g on the head: %.2f armour (floor: at least half of %.2f) [%s]%n", light[0], s[0], lok ? "OK" : "OUT OF RANGE");
        return ok;
    }

    /** Land plot prices (gold) - must match the deeds in data/holeydigsite/holeydigsite/shop_items/land_deed_*.json. */
    @SuppressWarnings("unchecked")
    static boolean plots(double[] ev, double evGravel, double evSand, Map<String, Double> gravelGrams, Map<String, Double> sandGrams,
                         Map<String, Double> t1Grams) throws Exception {
        System.out.println();
        System.out.println("=== PHASE 11: LAND PLOTS (expected progression, target 1-3 h each) ===");
        Path shop = Path.of("src/main/resources/data/holeydigsite/holeydigsite/shop_items");
        double t1Dirt = DRILL_CLUMPS_PER_MIN * 60 * ev[1] / GOLD;           // 1 Tier 1 line on Dirtrock
        double t2Abyssal = T2_DRILL_CLUMPS_PER_MIN * 60 * ev[3] / GOLD;       // 1 Tier 2 line on Abyssal Dirtrock
        double t2Gravel = T2_DRILL_CLUMPS_PER_MIN * 60 * evGravel / GOLD;     // 1 Tier 2 line on Gravelrock
        String[][] stages = {
                {"land_deed_deep_dig", "home island: 1 Tier 1 line on Dirtrock", String.valueOf(t1Dirt)},
                {"land_deed_gravel_flats", "with Deep Dig: 1 Tier 2 line on Abyssal Dirtrock", String.valueOf(t2Abyssal)},
                {"land_deed_sand_dunes", "with Gravel Flats: 1 Tier 2 line on Abyssal + 1 on Gravelrock", String.valueOf(t2Abyssal + t2Gravel)}};
        boolean ok = true;
        for (String[] st : stages) {
            Map<String, Object> e = (Map<String, Object>) new Json(Files.readString(shop.resolve(st[0] + ".json"))).parse();
            double priceGold = ((Number) e.get("price")).doubleValue() / GOLD;
            double perHour = Double.parseDouble(st[2]);
            double hours = priceGold / perHour;
            boolean in = hours >= 1.0 * 0.9 && hours <= 3.0 * 1.1;
            ok &= in;
            System.out.printf("  %-24s %6.0f gold; income %s = %.0f gold/h -> %.1f h [%s]%n", st[0], priceGold, st[1], perHour, hours, in ? "OK" : "OUT OF RANGE");
        }
        // Tier 3 drill: metal from both new plots (Tier 2 lines there)
        double orchPerMin = gravelGrams.getOrDefault("holeydigsite:orchremia", 0.0) * T2_DRILL_CLUMPS_PER_MIN;
        double silvPerMin = sandGrams.getOrDefault("holeydigsite:silvane", 0.0) * T2_DRILL_CLUMPS_PER_MIN;
        System.out.printf("  Tier 2 line on Gravelrock: %.1f g Orchremia/min; on Sandrock: %.1f g Silvane/min, %.2f g Goldchro/min%n",
                orchPerMin, silvPerMin, sandGrams.getOrDefault("holeydigsite:goldchro", 0.0) * T2_DRILL_CLUMPS_PER_MIN);
        return ok;
    }


    // ------------------------------------------------------------------------------------------------
    // Phase 13: casting upgrades (Large Crucible, Hot Forge, Tier 2 Mold) - Crafting Forge only
    // ------------------------------------------------------------------------------------------------
    static final java.util.Set<String> P13_RESULTS = java.util.Set.of("holeydigsite:large_crucible", "holeydigsite:hot_forge", "holeydigsite:casting_mold_t2");
    /** Target: the metal of each upgrade takes 20-40 minutes of ONE Tier 2 line (T2 drill + T2 sluice, 8 clumps/min). */
    static final double P13_MIN = 20, P13_MAX = 40;
    /** Splitter price in copper; target: about a Filter Belt (4 gold). */
    static final int SPLITTER_PRICE = 256, FILTER_PRICE = 256;

    /**
     * Phase 14 economy guard: the free scoopable material on each plot (grass/dirt, gravel, sand - counted by make_world.py into
     * tools/world_snbt/report.json) may be worth at most 40% of that plot's deed; the main value must come from drilling its
     * buried rock. The home island is held to the same 40% of the first deed (Deep Dig). Value per scoop = the clump's expected
     * Sell Pad value (gems uncut; cutting adds ~2%).
     */
    @SuppressWarnings("unchecked")
    static boolean phase14(double evDirt, double evGravel, double evSand) throws Exception {
        System.out.println();
        System.out.println("=== PHASE 14: FREE SCOOPABLE MATERIAL vs PLOT PRICE (target <= 40% of the deed) ===");
        Path report = Path.of("tools/world_snbt/report.json");
        if (!Files.exists(report)) {
            System.out.println("  tools/world_snbt/report.json missing - run tools/make_world.py first");
            return false;
        }
        Map<String, Object> rep = (Map<String, Object>) new Json(Files.readString(report)).parse();
        Path shop = Path.of("src/main/resources/data/holeydigsite/holeydigsite/shop_items");
        boolean ok = true;
        double firstDeed = 0;
        for (String area : new String[]{"deep_dig", "gravel_flats", "sand_dunes", "home"}) {
            Map<String, Object> c = (Map<String, Object>) rep.get(area);
            double dirt = num(c.get("dirt")), gravel = num(c.get("gravel")), sand = num(c.get("sand"));
            double value = dirt * evDirt + gravel * evGravel + sand * evSand;
            double price;
            if (area.equals("home")) {
                price = firstDeed;
            } else {
                Map<String, Object> deed = (Map<String, Object>) new Json(Files.readString(shop.resolve("land_deed_" + area + ".json"))).parse();
                price = num(deed.get("price"));
                if (firstDeed == 0) firstDeed = price;
            }
            double share = value / price;
            boolean good = share <= 0.40;
            ok &= good;
            System.out.printf("  %-13s %4.0f dirt + %4.0f gravel + %4.0f sand = %7.0f copper (%5.1f gold) vs %s %6.0f gold: %4.1f%% [%s]%n",
                    area, dirt, gravel, sand, value, value / GOLD, area.equals("home") ? "Deep Dig deed" : "deed", price / GOLD, share * 100,
                    good ? "OK" : "TOO MUCH");
        }
        System.out.printf("  values per scoop: dirt %.1f, gravel %.1f, sand %.1f copper%n", evDirt, evGravel, evSand);
        return ok;
    }

    static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    @SuppressWarnings("unchecked")
    static boolean phase13(Path res, Map<String, Double> t1) throws Exception {
        System.out.println();
        System.out.println("=== PHASE 13: BELT SPLITTER + CASTING UPGRADES (Large Crucible 500 g / 2x speed, Hot Forge 2x, T2 Mold 3x cooling) ===");
        boolean ok = Math.abs(SPLITTER_PRICE / (double) FILTER_PRICE - 1) <= 0.25;
        System.out.printf("  Belt Splitter %d copper = %.1f gold (Filter Belt %.1f gold) [%s]%n", SPLITTER_PRICE, SPLITTER_PRICE / 64.0, FILTER_PRICE / 64.0, ok ? "OK" : "FAIL");
        // a Tier 2 line: on Dirtrock (Folkiron, Goldchro), on Gravelrock (Orchremia), on Sandrock (Silvane)
        Map<String, Double> rate = new LinkedHashMap<>();
        rate.put("holeydigsite:folkiron", t1.getOrDefault("holeydigsite:folkiron", 0.0) * T2_DRILL_CLUMPS_PER_MIN);
        rate.put("holeydigsite:goldchro", t1.getOrDefault("holeydigsite:goldchro", 0.0) * T2_DRILL_CLUMPS_PER_MIN);
        rate.put("holeydigsite:orchremia", PLOT_T2_PER_MIN.get("holeydigsite:orchremia"));
        rate.put("holeydigsite:silvane", PLOT_T2_PER_MIN.get("holeydigsite:silvane"));
        System.out.printf("  Tier 2 line: %.0f g Folkiron/min, %.2f g Goldchro/min (Dirtrock); %.0f g Orchremia/min (Gravelrock); %.0f g Silvane/min (Sandrock)%n",
                rate.get("holeydigsite:folkiron"), rate.get("holeydigsite:goldchro"), rate.get("holeydigsite:orchremia"), rate.get("holeydigsite:silvane"));
        Path dir = res.resolve("data/holeydigsite/holeydigsite/forge_recipes");
        int found = 0;
        try (var files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                Map<String, Object> r = (Map<String, Object>) new Json(Files.readString(f)).parse();
                String result = (String) ((Map<String, Object>) r.get("result")).get("item");
                if (!P13_RESULTS.contains(result)) continue;
                found++;
                double minutes = 0, value = 0;
                StringBuilder need = new StringBuilder();
                for (var m : ((Map<String, Object>) r.getOrDefault("metals", Map.of())).entrySet()) {
                    double grams = ((Number) m.getValue()).doubleValue();
                    double mins = grams / rate.get(m.getKey());
                    minutes = Math.max(minutes, mins);
                    value += grams * PRICE_PER_GRAM.get(m.getKey()) * 1.25;
                    need.append(String.format("%s %.0f g (%.1f min) ", m.getKey().replace("holeydigsite:", ""), grams, mins));
                }
                boolean in = minutes >= P13_MIN && minutes <= P13_MAX;
                ok &= in;
                System.out.printf("  %-18s %s-> %.1f min of a Tier 2 line (metals in parallel); ingots would sell for %.0f gold  [%s]%n",
                        f.getFileName().toString().replace(".json", ""), need, minutes, value / GOLD, in ? "OK" : "OUT OF RANGE");
            }
        }
        ok &= found == P13_RESULTS.size();
        if (found != P13_RESULTS.size()) System.out.println("  MISSING recipes: found " + found + " of " + P13_RESULTS.size());
        return ok;
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 10: Tier 2 recipes (Crafting Forge) and Tier 2 lines
    // ------------------------------------------------------------------------------------------------

    /** Target: the metal for each Tier 2 item takes 15-45 minutes of ONE Tier 1 line (drill + sluice on Dirtrock). */
    static final double RECIPE_MIN_MINUTES = 15, RECIPE_MAX_MINUTES = 45;
    /** Phase 11: g/min of the new metals from a Tier 2 line on their plot (filled in by main before tier2()). */
    static final Map<String, Double> PLOT_T2_PER_MIN = new LinkedHashMap<>();
    /** Recipes measured against Tier 2 plot lines instead of a Tier 1 line, with their own (wider) target. */
    static final double T3_MIN_MINUTES = 30, T3_MAX_MINUTES = 90;
    /** Tier 2: drill 1 clump / 150 ticks (8/min), sluice 1 / 50 ticks (24/min). */
    static final double T2_DRILL_CLUMPS_PER_MIN = 8, T2_SLUICE_CLUMPS_PER_MIN = 24;

    @SuppressWarnings("unchecked")
    static boolean tier2(Path res, List<Map<String, Double>> gramsPerScoop, double[] ev) throws Exception {
        System.out.println();
        System.out.println("=== PHASE 10: TIER 2 (Crafting Forge recipes, data/holeydigsite/holeydigsite/forge_recipes) ===");
        Map<String, Double> t1 = gramsPerScoop.get(1);
        System.out.printf("  Tier 1 line (%.0f clumps/min): %.1f g Folkiron/min, %.2f g Goldchro/min%n", DRILL_CLUMPS_PER_MIN,
                t1.getOrDefault("holeydigsite:folkiron", 0.0) * DRILL_CLUMPS_PER_MIN, t1.getOrDefault("holeydigsite:goldchro", 0.0) * DRILL_CLUMPS_PER_MIN);
        boolean ok = true;
        Path dir = res.resolve("data/holeydigsite/holeydigsite/forge_recipes");
        // the first recipe (lowest order) per result is the main route; later ones are alternatives (Phase 11) that must cost
        // about the same in ingot value (+-15 %), so no route is a shortcut
        Map<String, Double> mainValue = new LinkedHashMap<>();
        Map<String, Integer> mainOrder = new LinkedHashMap<>();
        java.util.List<Path> all;
        try (var files = Files.list(dir)) { all = files.sorted().toList(); }
        for (Path f : all) {
            Map<String, Object> r = (Map<String, Object>) new Json(Files.readString(f)).parse();
            String result = (String) ((Map<String, Object>) r.get("result")).get("item");
            if (P13_RESULTS.contains(result)) continue;
            int order = ((Number) r.getOrDefault("order", 0)).intValue();
            double v = 0;
            for (var m : ((Map<String, Object>) r.getOrDefault("metals", Map.of())).entrySet())
                v += ((Number) m.getValue()).doubleValue() * PRICE_PER_GRAM.get(m.getKey()) * 1.25;
            if (!mainOrder.containsKey(result) || order < mainOrder.get(result)) { mainOrder.put(result, order); mainValue.put(result, v); }
        }
        {
            for (Path f : all) {
                Map<String, Object> r = (Map<String, Object>) new Json(Files.readString(f)).parse();
                String result = (String) ((Map<String, Object>) r.get("result")).get("item");
            if (P13_RESULTS.contains(result)) continue;
                boolean alternative = ((Number) r.getOrDefault("order", 0)).intValue() != mainOrder.get(result);
                Map<String, Object> metals = (Map<String, Object>) r.getOrDefault("metals", Map.of());
                boolean plotMetal = metals.keySet().stream().anyMatch(k -> PLOT_T2_PER_MIN.containsKey(k) && !k.endsWith("goldchro") && !k.endsWith("folkiron"));
                double minutes = 0;
                double sellValue = 0;
                StringBuilder need = new StringBuilder();
                for (var m : metals.entrySet()) {
                    double grams = ((Number) m.getValue()).doubleValue();
                    double perMin = plotMetal ? PLOT_T2_PER_MIN.getOrDefault(m.getKey(), 1e-9)   // Phase 11: Tier 2 lines on the plots
                            : t1.getOrDefault(m.getKey(), 0.0) * DRILL_CLUMPS_PER_MIN;
                    double mins = grams / perMin;
                    minutes = Math.max(minutes, mins);
                    sellValue += grams * PRICE_PER_GRAM.get(m.getKey()) * 1.25; // as ingots (+25 %)
                    need.append(String.format("%s %.0f g (%.1f min) ", m.getKey().replace("holeydigsite:", ""), grams, mins));
                }
                boolean inRange = alternative
                        ? Math.abs(sellValue / mainValue.get(result) - 1) <= 0.15
                        : plotMetal
                        ? minutes >= T3_MIN_MINUTES * 0.9 && minutes <= T3_MAX_MINUTES * 1.1
                        : minutes >= RECIPE_MIN_MINUTES * 0.9 && minutes <= RECIPE_MAX_MINUTES * 1.1;
                ok &= inRange;
                System.out.printf("  %-22s %s-> %.1f min of %s; the metal sells for %.0f gold as ingots  [%s]%n",
                        f.getFileName().toString().replace(".json", ""), need, minutes,
                        plotMetal ? "Tier 2 plot lines (each metal from its own plot, in parallel; target 30-90)" : "a Tier 1 line",
                        sellValue / GOLD, inRange ? "OK" : "OUT OF RANGE");
                if (alternative) System.out.printf("      alternative route: %.0f%% of the main recipe's ingot value (target +-15%%)%n", 100 * sellValue / mainValue.get(result));
            }
        }
        System.out.println("  Tier 2 line (T2 drill + T2 sluice: 8 clumps/min, sluice has room for 24/min):");
        for (int tier = 1; tier <= 3; tier++) {
            double perHour = T2_DRILL_CLUMPS_PER_MIN * 60 * ev[tier] / GOLD;
            System.out.printf("    tier %d rock: %.0f gold/h (Tier 1 line: %.0f); 3 T2 drills into 1 T2 sluice: %.0f gold/h%n", tier, perHour,
                    DRILL_CLUMPS_PER_MIN * 60 * ev[tier] / GOLD, T2_SLUICE_CLUMPS_PER_MIN * 60 * ev[tier] / GOLD);
        }
        return ok;
    }

    /** Phase 21: Prospector's Ledger Renown curve, milestones, ongoing caps, and reward coins/items. */
    @SuppressWarnings("unchecked")
    static boolean phase21(Path res, double[] ev, double evGravel, double evSand) throws Exception {
        boolean ok = true;
        Path ledgerDir = res.resolve("data/holeydigsite/ledger");
        Map<String, Object> curve = (Map<String, Object>) new Json(Files.readString(ledgerDir.resolve("curve.json"))).parse();
        List<Object> levelCostsRaw = (List<Object>) curve.get("levels");
        if (levelCostsRaw.size() != 50) throw new IllegalStateException("curve.json must have 50 levels, found " + levelCostsRaw.size());
        int[] levelCosts = levelCostsRaw.stream().mapToInt(o -> ((Number) o).intValue()).toArray();
        Map<String, Object> coinsDef = (Map<String, Object>) curve.get("coins");
        int coinS = ((Number) coinsDef.get("S")).intValue();
        int coinM = ((Number) coinsDef.get("M")).intValue();
        int coinL = ((Number) coinsDef.get("L")).intValue();
        double ingotGrams = ((Number) curve.get("reward_ingot_grams")).doubleValue();

        int totalMilestoneRenown = 0;
        Map<String, Integer> chapterRenown = new LinkedHashMap<>();
        try (var files = Files.list(ledgerDir.resolve("milestones"))) {
            for (Path f : files.sorted().toList()) {
                if (!f.toString().endsWith(".json")) continue;
                Map<String, Object> m = (Map<String, Object>) new Json(Files.readString(f)).parse();
                int r = ((Number) m.get("renown")).intValue();
                String ch = (String) m.get("chapter");
                totalMilestoneRenown += r;
                chapterRenown.merge(ch, r, Integer::sum);
            }
        }

        int cum10 = 0, cum25 = 0, cum50 = 0;
        for (int i = 0; i < 50; i++) {
            if (i < 10) cum10 += levelCosts[i];
            if (i < 25) cum25 += levelCosts[i];
            cum50 += levelCosts[i];
        }

        // Early game (L1-10): start + early machines/metal/gems/farming milestones (~150 Renown) + ~125 ongoing/hr
        double earlyMilestones = 150.0;
        double earlyOngoingPerHour = 125.0; // ~300 scoops (75) + early sluice/crops/sales (50)
        double hoursL10 = Math.max(0.2, (cum10 - earlyMilestones) / earlyOngoingPerHour);
        boolean l10Ok = hoursL10 >= 0.5 && hoursL10 <= 1.5;
        ok &= l10Ok;

        // Full 50-level play-through: all 38 milestones (1305) + ongoing Renown across pan/sluice/crop/smith/sell (~185/hr average across 20h)
        double avgOngoingPerHour = 185.0;
        double hoursL50 = (cum50 - totalMilestoneRenown) / avgOngoingPerHour;
        boolean l50Ok = hoursL50 >= 15.0 && hoursL50 <= 25.0;
        ok &= l50Ok;

        // Reward value across all 50 levels (coins + items at Sell Pad / shop equivalent)
        long totalCoinCopper = 0;
        long totalItemCopper = 0;
        Map<String, Integer> shopPrices = new LinkedHashMap<>();
        try (var sFiles = Files.list(res.resolve("data/holeydigsite/holeydigsite/shop_items"))) {
            for (Path sf : sFiles.toList()) {
                if (!sf.toString().endsWith(".json")) continue;
                Map<String, Object> e = (Map<String, Object>) new Json(Files.readString(sf)).parse();
                shopPrices.put((String) e.get("item"), ((Number) e.get("price")).intValue());
            }
        }
        try (var files = Files.list(ledgerDir.resolve("levels"))) {
            for (Path f : files.sorted().toList()) {
                if (!f.toString().endsWith(".json")) continue;
                Map<String, Object> lv = (Map<String, Object>) new Json(Files.readString(f)).parse();
                String c = (String) lv.get("coins");
                if ("S".equals(c)) totalCoinCopper += coinS;
                else if ("M".equals(c)) totalCoinCopper += coinM;
                else if ("L".equals(c)) totalCoinCopper += coinL;
                for (Object ro : (List<Object>) lv.getOrDefault("rewards", List.of())) {
                    Map<String, Object> r = (Map<String, Object>) ro;
                    String item = (String) r.get("item");
                    if (item == null) continue;
                    int count = ((Number) r.getOrDefault("count", 1)).intValue();
                    if (item.endsWith("_ingot")) {
                        String mat = item.replace("_ingot", "");
                        double perG = PRICE_PER_GRAM.getOrDefault(mat, 0.0);
                        totalItemCopper += Math.round(count * ingotGrams * perG * 1.25);
                    } else if (RESOURCES.containsKey(item)) {
                        totalItemCopper += (long) count * price(item);
                    } else if (shopPrices.containsKey(item)) {
                        totalItemCopper += (long) count * shopPrices.get(item);
                    }
                }
            }
        }
        long totalRewardCopper = totalCoinCopper + totalItemCopper;
        // Player reaches L50 around the 1M+ copper mark (earned_1m is a milestone, plus ongoing sell cap at 1M copper)
        double estPlayerEarningsCopper = 1_160_000.0;
        double rewardSharePct = 100.0 * totalRewardCopper / estPlayerEarningsCopper;
        boolean shareOk = rewardSharePct <= 10.0;
        ok &= shareOk;

        System.out.println();
        System.out.println("=== Phase 21: Prospector's Ledger ===");
        System.out.printf("  Milestones: 38 total = %d Renown %s%n", totalMilestoneRenown, chapterRenown);
        System.out.printf("  Curve: L1-10 = %d Renown (~%.2f h, target ~1.0 h) [%s]%n", cum10, hoursL10, l10Ok ? "OK" : "OUT OF RANGE");
        System.out.printf("         L1-25 = %d Renown, L1-50 = %d Renown (~%.1f h, target 15-25 h) [%s]%n", cum25, cum50, hoursL50, l50Ok ? "OK" : "OUT OF RANGE");
        System.out.printf("  Rewards: coins (S=%d c, M=%d c, L=%d c) = %d c (%d gold); items (ingots @ %.0fg) = %d c (%d gold)%n",
                coinS, coinM, coinL, totalCoinCopper, totalCoinCopper / GOLD, ingotGrams, totalItemCopper, totalItemCopper / GOLD);
        System.out.printf("  Total 50-level reward value: %d c (%d gold) = %.1f%% of ~%.0fk gold earned (target <= 10%%) [%s]%n",
                totalRewardCopper, totalRewardCopper / GOLD, rewardSharePct, estPlayerEarningsCopper / GOLD / 1000.0, shareOk ? "OK" : "TOO HIGH");
        return ok;
    }

    /**
     * Phase 21b: Ledger blueprints (cheap decoration priced around the kiosk lamps) and the 1-copper Ledger at the Trading Post.
     * Also checks that every title a level hands out is in titles.json and every reward block has a catalogue/blueprint story.
     */
    @SuppressWarnings("unchecked")
    static boolean phase21b(Path res) throws Exception {
        boolean ok = true;
        System.out.println();
        System.out.println("=== Phase 21b: Ledger decoration, blueprints and titles ===");
        long lampLow = Long.MAX_VALUE, lampHigh = 0;
        for (String lamp : new String[]{"miners_lantern", "river_stone_lamp", "sandstone_lamp"}) {
            long p = shopPrice(res, lamp);
            lampLow = Math.min(lampLow, p);
            lampHigh = Math.max(lampHigh, p);
        }
        for (String id : new String[]{"claim_flag", "ore_cart", "lucky_lantern"}) {
            Path f = res.resolve("data/holeydigsite/holeydigsite/shop_items/" + id + ".json");
            Map<String, Object> e = (Map<String, Object>) new Json(Files.readString(f)).parse();
            long price = ((Number) e.get("price")).longValue();
            boolean ledgerFlag = Boolean.TRUE.equals(e.get("ledger"));
            boolean inRange = price >= lampLow / 2 && price <= lampHigh * 2;   // "around the kiosk lamps"
            ok &= ledgerFlag && inRange;
            System.out.printf("  %-14s blueprint %d copper (lamps %d-%d), ledger-only=%b [%s]%n",
                    id, price, lampLow, lampHigh, ledgerFlag, ledgerFlag && inRange ? "OK" : "FAIL");
        }
        long ledgerPrice = shopPrice(res, "prospectors_ledger");
        ok &= ledgerPrice == 1;
        System.out.printf("  prospectors_ledger at the Trading Post: %d copper [%s]%n", ledgerPrice, ledgerPrice == 1 ? "OK" : "FAIL");
        List<Object> titles = (List<Object>) ((Map<String, Object>) new Json(Files.readString(
                res.resolve("data/holeydigsite/ledger/titles.json"))).parse()).get("titles");
        int given = 0;
        try (var files = Files.list(res.resolve("data/holeydigsite/ledger/levels"))) {
            for (Path f : files.sorted().toList()) {
                if (!f.toString().endsWith(".json")) continue;
                Map<String, Object> lv = (Map<String, Object>) new Json(Files.readString(f)).parse();
                Object t = lv.get("title");
                if (t != null) {
                    given++;
                    ok &= titles.contains(t);
                }
            }
        }
        ok &= given == titles.size();
        System.out.printf("  titles: %d in titles.json, %d handed out by levels [%s]%n", titles.size(), given, given == titles.size() ? "OK" : "FAIL");
        return ok;
    }
    /**
     * Phase 21f: Coarse dirt, rooted dirt, and moss panning tables.
     * Coarse: +20-30% value vs plain dirt.
     * Rooted: same ore value as plain dirt, vanilla seeds ~1 in 10, own-crop seeds ~1 in 60.
     * Moss: ~50% metal value of plain dirt, seeds ~1 in 3-4, own-crop seeds ~1 in 25.
     */
    static boolean phase21f(Path res, double evT1) throws Exception {
        boolean ok = true;
        System.out.println();
        System.out.println("=== Phase 21f: Coarse dirt, rooted dirt and moss ===");

        // 1. Coarse dirt
        List<Pool> coarsePools = parseTable(new Json(Files.readString(res.resolve("data/holeydigsite/loot_table/panning/coarse_dirt.json"))).parse());
        double coarseVal = 0;
        for (Pool p : coarsePools) {
            for (Entry e : p.entries) {
                coarseVal += p.chance * p.rolls * (double) e.weight / p.totalWeight() * e.avgCount() * price(e.item);
            }
        }
        double coarseRatio = coarseVal / evT1;
        boolean coarseOk = coarseRatio >= 1.20 && coarseRatio <= 1.30;
        ok &= coarseOk;
        System.out.printf("  coarse_dirt: %.2f copper per scoop = %.2fx dirt (+%.1f%%) (target +20-30%%) [%s]%n",
                coarseVal, coarseRatio, 100 * (coarseRatio - 1), coarseOk ? "OK" : "OUT OF RANGE");

        // 2. Rooted dirt
        List<Pool> rootedPools = parseTable(new Json(Files.readString(res.resolve("data/holeydigsite/loot_table/panning/rooted_dirt.json"))).parse());
        double rootedOreVal = 0;
        double rootedTotalSeedChance = 0;
        double rootedOwnSeedChance = 0;
        for (Pool p : rootedPools) {
            boolean isSeed = p.entries.stream().anyMatch(e -> e.item.contains("seed") || e.item.equals("minecraft:carrot") || e.item.equals("minecraft:potato"));
            if (!isSeed) {
                for (Entry e : p.entries) {
                    rootedOreVal += p.chance * p.rolls * (double) e.weight / p.totalWeight() * e.avgCount() * price(e.item);
                }
            } else {
                for (Entry e : p.entries) {
                    double rollChance = p.chance * p.rolls * (double) e.weight / p.totalWeight();
                    rootedTotalSeedChance += rollChance;
                    if (e.item.startsWith("holeydigsite:")) {
                        rootedOwnSeedChance += rollChance;
                    }
                }
            }
        }
        boolean rootedOreOk = Math.abs(rootedOreVal - evT1) <= 1.0;
        boolean rootedTotalSeedOk = rootedTotalSeedChance >= 0.08 && rootedTotalSeedChance <= 0.12; // ~1 in 10
        boolean rootedOwnSeedOk = rootedOwnSeedChance >= 0.014 && rootedOwnSeedChance <= 0.019; // ~1 in 60
        ok &= rootedOreOk && rootedTotalSeedOk && rootedOwnSeedOk;
        System.out.printf("  rooted_dirt: ore %.2f copper per scoop = %.2fx dirt [%s]; seeds 1 in %.1f scoops (target ~10) [%s]; own-crop seeds 1 in %.1f scoops (target ~60) [%s]%n",
                rootedOreVal, rootedOreVal / evT1, rootedOreOk ? "OK" : "FAIL",
                1.0 / rootedTotalSeedChance, rootedTotalSeedOk ? "OK" : "OUT OF RANGE",
                1.0 / rootedOwnSeedChance, rootedOwnSeedOk ? "OK" : "OUT OF RANGE");

        // 3. Moss
        List<Pool> mossPools = parseTable(new Json(Files.readString(res.resolve("data/holeydigsite/loot_table/panning/moss.json"))).parse());
        double mossMetalVal = 0;
        double mossTotalSeedChance = 0;
        double mossOwnSeedChance = 0;
        for (Pool p : mossPools) {
            boolean isSeed = p.entries.stream().anyMatch(e -> e.item.contains("seed") || e.item.equals("minecraft:carrot") || e.item.equals("minecraft:potato"));
            if (!isSeed) {
                for (Entry e : p.entries) {
                    mossMetalVal += p.chance * p.rolls * (double) e.weight / p.totalWeight() * e.avgCount() * price(e.item);
                }
            } else {
                for (Entry e : p.entries) {
                    double rollChance = p.chance * p.rolls * (double) e.weight / p.totalWeight();
                    mossTotalSeedChance += rollChance;
                    if (e.item.startsWith("holeydigsite:")) {
                        mossOwnSeedChance += rollChance;
                    }
                }
            }
        }
        double dirtMetalVal = 70.80 + 4.30;
        double mossMetalRatio = mossMetalVal / dirtMetalVal;
        boolean mossMetalOk = mossMetalRatio >= 0.45 && mossMetalRatio <= 0.55; // ~50%
        boolean mossTotalSeedOk = mossTotalSeedChance >= 0.25 && mossTotalSeedChance <= 0.334; // 1 in 3-4 scoops
        boolean mossOwnSeedOk = mossOwnSeedChance >= 0.035 && mossOwnSeedChance <= 0.045; // ~1 in 25 scoops
        ok &= mossMetalOk && mossTotalSeedOk && mossOwnSeedOk;
        System.out.printf("  moss: metal %.2f copper per scoop = %.1f%% of dirt metal (target ~50%%) [%s]; seeds 1 in %.1f scoops (target 3-4) [%s]; own-crop seeds 1 in %.1f scoops (target ~25) [%s]%n",
                mossMetalVal, 100 * mossMetalRatio, mossMetalOk ? "OK" : "OUT OF RANGE",
                1.0 / mossTotalSeedChance, mossTotalSeedOk ? "OK" : "OUT OF RANGE",
                1.0 / mossOwnSeedChance, mossOwnSeedOk ? "OK" : "OUT OF RANGE");

        return ok;
    }

    static boolean phase21i(Path res) throws Exception {
        System.out.println();
        System.out.println("=== Phase 21i: Moving items downwards (down incline + drop chute) ===");
        boolean ok = true;
        long downIncline = shopPrice(res, "down_incline_belt");
        long incline = shopPrice(res, "incline_belt");
        long chute = shopPrice(res, "drop_chute");
        long belt = shopPrice(res, "clump_belt");
        boolean downOk = downIncline == incline;
        boolean chuteOk = chute > belt && chute <= belt * 2;
        ok &= downOk && chuteOk;
        System.out.printf("  down_incline_belt  %d copper (same as incline_belt %d) [%s]%n", downIncline, incline, downOk ? "OK" : "FAIL");
        System.out.printf("  drop_chute         %d copper (plain belt %d, target a bit more) [%s]%n", chute, belt, chuteOk ? "OK" : "FAIL");
        return ok;
    }

    /** P(at least one item matching the predicate in one scoop). */
    static double pAny(List<Pool> pools, java.util.function.Predicate<String> match) {
        double none = 1;
        for (Pool p : pools) {
            double share = p.entries.stream().filter(e -> match.test(e.item)).mapToInt(Entry::weight).sum() / (double) p.totalWeight();
            none *= Math.pow(1 - p.chance * share, p.rolls);
        }
        return 1 - none;
    }

    static String material(String item) {
        Resource r = RESOURCES.get(item);
        if (r == null) throw new IllegalArgumentException("No weight for " + item + " - add it to resource_weights.json");
        return r.material;
    }

    /** Sell Pad value of ONE item in copper: floor(grams x price per gram). */
    static int price(String item) {
        Resource r = RESOURCES.get(item);
        if (r == null) throw new IllegalArgumentException("No weight for " + item + " - add it to resource_weights.json");
        Double perGram = PRICE_PER_GRAM.get(r.material);
        if (perGram == null) throw new IllegalArgumentException("No sell value for " + r.material);
        return (int) Math.floor(r.grams * perGram + 1e-9);
    }

    static Entry pick(Pool p, Random random) {
        int n = random.nextInt(p.totalWeight());
        for (Entry e : p.entries) {
            n -= e.weight;
            if (n < 0) return e;
        }
        throw new IllegalStateException();
    }

    // ------------------------------------------------------------------------------------------------
    // Loot table -> pools
    // ------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static List<Pool> parseTable(Object root) {
        List<Pool> pools = new ArrayList<>();
        for (Object po : (List<Object>) ((Map<String, Object>) root).get("pools")) {
            Map<String, Object> pool = (Map<String, Object>) po;
            int rolls = ((Number) pool.getOrDefault("rolls", 1)).intValue();
            double chance = 1;
            for (Object co : (List<Object>) pool.getOrDefault("conditions", List.of())) {
                Map<String, Object> c = (Map<String, Object>) co;
                if ("minecraft:random_chance".equals(c.get("condition"))) chance *= ((Number) c.get("chance")).doubleValue();
                else throw new IllegalArgumentException("Unsupported pool condition: " + c.get("condition"));
            }
            List<Entry> entries = new ArrayList<>();
            for (Object eo : (List<Object>) pool.get("entries")) {
                Map<String, Object> e = (Map<String, Object>) eo;
                int min = 1, max = 1;
                for (Object fo : (List<Object>) e.getOrDefault("functions", List.of())) {
                    Map<String, Object> f = (Map<String, Object>) fo;
                    if (!"minecraft:set_count".equals(f.get("function"))) continue;
                    Object count = f.get("count");
                    if (count instanceof Number n) {
                        min = max = n.intValue();
                    } else {
                        Map<String, Object> u = (Map<String, Object>) count;
                        min = (int) Math.floor(((Number) u.get("min")).doubleValue());
                        max = (int) Math.floor(((Number) u.get("max")).doubleValue());
                    }
                }
                entries.add(new Entry((String) e.get("name"), ((Number) e.getOrDefault("weight", 1)).intValue(), min, max));
            }
            pools.add(new Pool(rolls, chance, entries));
        }
        return pools;
    }

    /** Minimal JSON parser (objects, arrays, strings, numbers, true/false/null) - enough for loot tables. */
    static final class Json {
        private final String s;
        private int i;

        Json(String s) { this.s = s; }

        Object parse() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++; ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws(); String k = str(); ws(); expect(':');
                    m.put(k, parse()); ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect('}'); return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++; ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(parse()); ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect(']'); return l;
                }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return true; }
            if (s.startsWith("false", i)) { i += 5; return false; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }

        private String str() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') {
                    char n = s.charAt(i++);
                    b.append(switch (n) {
                        case 'n' -> '\n'; case 't' -> '\t';
                        case 'u' -> { char u = (char) Integer.parseInt(s.substring(i, i + 4), 16); i += 4; yield u; }
                        default -> n;
                    });
                } else {
                    b.append(c);
                }
            }
            i++;
            return b.toString();
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private void expect(char c) {
            if (s.charAt(i) != c) throw new IllegalArgumentException("Expected '" + c + "' at " + i);
            i++;
        }
    }
}
