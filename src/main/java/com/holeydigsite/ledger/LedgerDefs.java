package com.holeydigsite.ledger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and holds the Prospector's Ledger definitions from {@code data/holeydigsite/ledger/},
 * including {@code curve.json} (the 50 level Renown costs, coin reward sizes (S/M/L), reward ingot weight, and ongoing Renown rates and caps),
 * {@code milestones/*.json} (the 38 milestones across 8 chapters),
 * and {@code levels/*.json} (the 50 reward levels).
 * Pre-loads built-in defaults from the mod classpath at startup so client UI and unit checks work immediately, and reloads
 * from the server {@link ResourceManager} on datapack load or reload.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class LedgerDefs {
    private LedgerDefs() {}

    public static final int MAX_LEVEL = 50;

    public static final List<String> CHAPTER_ORDER = List.of(
            "start", "machines", "metal", "gems", "land", "farming", "smithing", "contracts", "riches"
    );

    private static final String[] BUILTIN_MILESTONES = {
            "abyssal_rock", "all_crops", "all_plots", "belt_splitter", "big_sale",
            "buff_food", "buy_anvil", "cut_gem", "deep_dig", "earned_10k",
            "earned_1m", "every_piece", "fifty_contracts", "filter_belt", "first_belt", "first_blueprint",
            "first_contract", "first_drill", "first_gem", "first_harvest", "first_ingot", "first_line",
            "first_pan", "first_piece", "first_sale", "first_sluice", "gem_necklace",
            "goldchro_ingot", "gravel_flats", "large_crucible", "large_gem", "lava_forge",
            "mixed_piece", "netherite_sword", "plot_upgrade", "sand_dunes", "seed_screen",
            "ten_contracts", "tier2_machine", "water_intake", "wear_jewellery"
    };

    public record OngoingRule(int per, int renown, int cap) {}

    public record Curve(
            int[] levelCosts,
            int[] cumulativeCosts,
            Map<String, Integer> coins,
            double rewardIngotGrams,
            OngoingRule pan,
            OngoingRule sluice,
            OngoingRule harvest,
            OngoingRule smith,
            OngoingRule sell,
            OngoingRule contract
    ) {
        public int costForLevel(int level) {
            if (level < 1 || level > levelCosts.length) return 0;
            return levelCosts[level - 1];
        }

        public int cumulativeForLevel(int level) {
            if (level <= 0) return 0;
            if (level > cumulativeCosts.length) return cumulativeCosts[cumulativeCosts.length - 1];
            return cumulativeCosts[level - 1];
        }

        public int levelForRenown(int renown) {
            int lvl = 0;
            for (int i = 0; i < cumulativeCosts.length; i++) {
                if (renown >= cumulativeCosts[i]) {
                    lvl = i + 1;
                } else {
                    break;
                }
            }
            return lvl;
        }

        public int coinCopper(String size) {
            return coins.getOrDefault(size, 0);
        }
    }

    public record Milestone(
            ResourceLocation id,
            String chapter,
            int order,
            int renown,
            ResourceLocation advancement,
            String titleKey,
            String hintKey
    ) {}

    public record RewardEntry(
            Optional<ResourceLocation> item,
            int count,
            Optional<Integer> clumpTier,
            Optional<ResourceLocation> blueprint,
            Optional<String> skin
    ) {
        public RewardEntry(Optional<ResourceLocation> item, int count, Optional<Integer> clumpTier, Optional<ResourceLocation> blueprint) {
            this(item, count, clumpTier, blueprint, Optional.empty());
        }
    }

    public record LevelDef(
            int level,
            List<RewardEntry> rewards,
            Optional<String> coins,
            Optional<String> title
    ) {}

    private static volatile Curve CURVE;
    private static volatile Map<ResourceLocation, Milestone> MILESTONES = Map.of();
    private static volatile Map<ResourceLocation, Milestone> BY_ADVANCEMENT = Map.of();
    private static volatile Map<Integer, LevelDef> LEVELS = Map.of();
    private static volatile List<String> TITLES = List.of();
    private static volatile List<String> CRATE_TITLES = List.of();
    private static volatile List<String> CONTRACT_TITLES = List.of();

    static {
        loadBuiltinFromClasspath();
    }

    public static Curve curve() {
        return CURVE;
    }

    public static Map<ResourceLocation, Milestone> milestones() {
        return MILESTONES;
    }

    @Nullable
    public static Milestone milestone(ResourceLocation id) {
        return MILESTONES.get(id);
    }

    @Nullable
    public static Milestone byAdvancement(ResourceLocation advancementId) {
        return BY_ADVANCEMENT.get(advancementId);
    }

    public static List<Milestone> milestonesForChapter(String chapter) {
        return MILESTONES.values().stream()
                .filter(m -> m.chapter().equals(chapter))
                .sorted(Comparator.comparingInt(Milestone::order).thenComparing(m -> m.id().toString()))
                .toList();
    }

    public static Map<Integer, LevelDef> levels() {
        return LEVELS;
    }

    @Nullable
    public static LevelDef level(int lvl) {
        return LEVELS.get(lvl);
    }

    /** The title ids from {@code ledger/titles.json}, in level order, with display names under the lang key ledger.holeydigsite.title.<id>. */
    public static List<String> titles() {
        return TITLES;
    }

    /** Titles that only come from Mining Crates ({@code crate_titles} in titles.json). */
    public static List<String> crateTitles() {
        return CRATE_TITLES;
    }

    /** Titles that come from contract milestones ({@code contract_titles} in titles.json). */
    public static List<String> contractTitles() {
        return CONTRACT_TITLES;
    }

    /** Level titles first, then crate titles, then contract titles. */
    public static List<String> allTitles() {
        List<String> all = new ArrayList<>(TITLES);
        all.addAll(CRATE_TITLES);
        all.addAll(CONTRACT_TITLES);
        return all;
    }

    public static boolean hasTitle(String id) {
        return TITLES.contains(id) || CRATE_TITLES.contains(id) || CONTRACT_TITLES.contains(id);
    }

    public static boolean isContractTitle(String id) {
        return CONTRACT_TITLES.contains(id);
    }

    public static boolean isCrateTitle(String id) {
        return CRATE_TITLES.contains(id);
    }

    /** Lang key of a title's display name. */
    public static String titleKey(String id) {
        return "ledger.holeydigsite.title." + id;
    }

    /** Test hook to replace the level definitions, keeping a copy of {@link #levels()} to restore them afterwards. */
    public static void setLevelsForTest(Map<Integer, LevelDef> levels) {
        LEVELS = Map.copyOf(levels);
    }

    /** Test hook to replace the title definitions, keeping a copy of {@link #titles()} to restore them afterwards. */
    public static void setTitlesForTest(List<String> titles) {
        TITLES = List.copyOf(titles);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void unused, ResourceManager manager, ProfilerFiller profiler) {
                reloadFromManager(manager);
            }
        });
    }

    private static void loadBuiltinFromClasspath() {
        try {
            Curve parsedCurve;
            try (Reader r = openClasspath("/data/holeydigsite/ledger/curve.json")) {
                parsedCurve = parseCurve(JsonParser.parseReader(r).getAsJsonObject());
            }
            Map<ResourceLocation, Milestone> ms = new LinkedHashMap<>();
            Map<ResourceLocation, Milestone> byAdv = new LinkedHashMap<>();
            for (String name : BUILTIN_MILESTONES) {
                try (Reader r = openClasspath("/data/holeydigsite/ledger/milestones/" + name + ".json")) {
                    ResourceLocation id = HoleyDigsite.id(name);
                    Milestone m = parseMilestone(id, JsonParser.parseReader(r).getAsJsonObject());
                    ms.put(id, m);
                    byAdv.put(m.advancement(), m);
                }
            }
            Map<Integer, LevelDef> lvs = new LinkedHashMap<>();
            for (int i = 1; i <= MAX_LEVEL; i++) {
                String file = String.format(java.util.Locale.ROOT, "/data/holeydigsite/ledger/levels/%02d.json", i);
                try (Reader r = openClasspath(file)) {
                    LevelDef d = parseLevel(JsonParser.parseReader(r).getAsJsonObject());
                    lvs.put(d.level(), d);
                }
            }
            CURVE = parsedCurve;
            MILESTONES = sortMilestones(ms);
            BY_ADVANCEMENT = Map.copyOf(byAdv);
            LEVELS = Map.copyOf(lvs);
            try (Reader r = openClasspath("/data/holeydigsite/ledger/titles.json")) {
                JsonObject titlesRoot = JsonParser.parseReader(r).getAsJsonObject();
                TITLES = parseTitles(titlesRoot, "titles");
                CRATE_TITLES = parseTitles(titlesRoot, "crate_titles");
                CONTRACT_TITLES = parseTitles(titlesRoot, "contract_titles");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load built-in Ledger definitions from classpath", e);
        }
    }

    private static void reloadFromManager(ResourceManager manager) {
        try {
            Optional<Resource> curveRes = manager.getResource(HoleyDigsite.id("ledger/curve.json"));
            if (curveRes.isPresent()) {
                try (Reader r = curveRes.get().openAsReader()) {
                    CURVE = parseCurve(JsonParser.parseReader(r).getAsJsonObject());
                }
            }
            Map<ResourceLocation, Milestone> ms = new LinkedHashMap<>();
            Map<ResourceLocation, Milestone> byAdv = new LinkedHashMap<>();
            for (var entry : manager.listResources("ledger/milestones", p -> p.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation fileId = entry.getKey();
                String path = fileId.getPath();
                String name = path.substring("ledger/milestones/".length(), path.length() - ".json".length());
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), name);
                try (Reader r = entry.getValue().openAsReader()) {
                    Milestone m = parseMilestone(id, JsonParser.parseReader(r).getAsJsonObject());
                    ms.put(id, m);
                    byAdv.put(m.advancement(), m);
                }
            }
            if (!ms.isEmpty()) {
                MILESTONES = sortMilestones(ms);
                BY_ADVANCEMENT = Map.copyOf(byAdv);
            }
            Map<Integer, LevelDef> lvs = new LinkedHashMap<>();
            for (var entry : manager.listResources("ledger/levels", p -> p.getPath().endsWith(".json")).entrySet()) {
                try (Reader r = entry.getValue().openAsReader()) {
                    LevelDef d = parseLevel(JsonParser.parseReader(r).getAsJsonObject());
                    lvs.put(d.level(), d);
                }
            }
            if (!lvs.isEmpty()) {
                Map<Integer, LevelDef> sorted = new LinkedHashMap<>();
                lvs.keySet().stream().sorted().forEach(k -> sorted.put(k, lvs.get(k)));
                LEVELS = Map.copyOf(sorted);
            }
            Optional<Resource> titlesRes = manager.getResource(HoleyDigsite.id("ledger/titles.json"));
            if (titlesRes.isPresent()) {
                try (Reader r = titlesRes.get().openAsReader()) {
                    JsonObject titlesRoot = JsonParser.parseReader(r).getAsJsonObject();
                    TITLES = parseTitles(titlesRoot, "titles");
                    CRATE_TITLES = parseTitles(titlesRoot, "crate_titles");
                    CONTRACT_TITLES = parseTitles(titlesRoot, "contract_titles");
                }
            }
        } catch (IOException e) {
            HoleyDigsite.LOGGER.error("Failed to reload Ledger definitions from datapack", e);
        }
    }

    private static Map<ResourceLocation, Milestone> sortMilestones(Map<ResourceLocation, Milestone> raw) {
        List<Milestone> list = new ArrayList<>(raw.values());
        list.sort(Comparator.comparingInt((Milestone m) -> {
            int idx = CHAPTER_ORDER.indexOf(m.chapter());
            return idx < 0 ? 999 : idx;
        }).thenComparingInt(Milestone::order).thenComparing(m -> m.id().toString()));
        Map<ResourceLocation, Milestone> out = new LinkedHashMap<>();
        for (Milestone m : list) {
            out.put(m.id(), m);
        }
        return Map.copyOf(out);
    }

    private static Reader openClasspath(String path) throws IOException {
        InputStream in = LedgerDefs.class.getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Missing built-in resource: " + path);
        }
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    private static Curve parseCurve(JsonObject root) {
        var arr = root.getAsJsonArray("levels");
        int[] costs = new int[arr.size()];
        int[] cum = new int[arr.size()];
        int sum = 0;
        for (int i = 0; i < arr.size(); i++) {
            costs[i] = arr.get(i).getAsInt();
            sum += costs[i];
            cum[i] = sum;
        }
        Map<String, Integer> coins = new LinkedHashMap<>();
        JsonObject coinsObj = root.getAsJsonObject("coins");
        for (String k : coinsObj.keySet()) {
            coins.put(k, coinsObj.get(k).getAsInt());
        }
        double ingotGrams = root.has("reward_ingot_grams") ? root.get("reward_ingot_grams").getAsDouble() : 100.0;
        JsonObject ongoing = root.getAsJsonObject("ongoing");
        return new Curve(
                costs,
                cum,
                Map.copyOf(coins),
                ingotGrams,
                new OngoingRule(ongoing.get("pan_scoops_per_renown").getAsInt(), 1, ongoing.get("pan_cap").getAsInt()),
                new OngoingRule(ongoing.get("sluice_clumps_per_renown").getAsInt(), 1, ongoing.get("sluice_cap").getAsInt()),
                new OngoingRule(1, ongoing.get("harvest_renown").getAsInt(), ongoing.get("harvest_cap").getAsInt()),
                new OngoingRule(1, ongoing.get("smith_renown").getAsInt(), ongoing.get("smith_cap").getAsInt()),
                new OngoingRule(ongoing.get("sell_copper_per_renown").getAsInt(), 1, ongoing.get("sell_cap").getAsInt()),
                new OngoingRule(1, 1, ongoing.has("contract_cap") ? ongoing.get("contract_cap").getAsInt() : 400)
        );
    }

    private static OngoingRule parseRule(JsonObject obj, String perKey) {
        return new OngoingRule(
                obj.get(perKey).getAsInt(),
                obj.get("renown").getAsInt(),
                obj.get("cap").getAsInt()
        );
    }

    private static Milestone parseMilestone(ResourceLocation id, JsonObject obj) {
        return new Milestone(
                id,
                obj.get("chapter").getAsString(),
                obj.get("order").getAsInt(),
                obj.get("renown").getAsInt(),
                ResourceLocation.parse(obj.get("advancement").getAsString()),
                obj.has("name") ? obj.get("name").getAsString() : obj.get("title").getAsString(),
                obj.get("hint").getAsString()
        );
    }

    private static List<String> parseTitles(JsonObject root, String key) {
        List<String> out = new ArrayList<>();
        if (!root.has(key)) return List.of();
        for (JsonElement el : root.getAsJsonArray(key)) {
            out.add(el.getAsString());
        }
        return List.copyOf(out);
    }

    private static LevelDef parseLevel(JsonObject obj) {
        int lvl = obj.get("level").getAsInt();
        List<RewardEntry> rewards = new ArrayList<>();
        if (obj.has("rewards")) {
            for (JsonElement el : obj.getAsJsonArray("rewards")) {
                JsonObject ro = el.getAsJsonObject();
                Optional<ResourceLocation> item = ro.has("item")
                        ? Optional.of(ResourceLocation.parse(ro.get("item").getAsString()))
                        : Optional.empty();
                int count = ro.has("count") ? ro.get("count").getAsInt() : 1;
                Optional<Integer> clumpTier = Optional.empty();
                if (ro.has("components")) {
                    JsonObject comp = ro.getAsJsonObject("components");
                    if (comp.has("holeydigsite:clump_tier")) {
                        clumpTier = Optional.of(comp.get("holeydigsite:clump_tier").getAsInt());
                    }
                }
                Optional<ResourceLocation> bp = ro.has("blueprint")
                        ? Optional.of(ResourceLocation.parse(ro.get("blueprint").getAsString()))
                        : Optional.empty();
                Optional<String> skin = ro.has("skin")
                        ? Optional.of(ro.get("skin").getAsString())
                        : Optional.empty();
                rewards.add(new RewardEntry(item, count, clumpTier, bp, skin));
            }
        }
        Optional<String> coins = obj.has("coins") ? Optional.of(obj.get("coins").getAsString()) : Optional.empty();
        Optional<String> title = obj.has("title") ? Optional.of(obj.get("title").getAsString()) : Optional.empty();
        return new LevelDef(lvl, List.copyOf(rewards), coins, title);
    }
}
