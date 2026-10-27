package com.holeydigsite.contract;

import com.google.gson.JsonArray;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The contract pool ({@code data/holeydigsite/contracts/*.json}) and its rules ({@code rules.json}).
 * A pool file is {@code {"kind": "metal", "entries": [...]}}. Built-in copies are read from the jar at class load (so
 * tests and the client work at once) and replaced on datapack reload.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class ContractDefs {
    private ContractDefs() {}

    public static final String[] BUILTIN_POOLS = {"metal", "gems", "food", "pieces"};
    public static final int MAX_SLOTS = 3;

    /**
     * One thing a contract can ask for.
     *
     * @param item     metal material, gem material, food item, or smithed piece item
     * @param form     gems: "cut" or "uncut"
     * @param metal    smithed pieces: required main metal (material id), or null
     * @param gem      smithed pieces: null (no gem asked), "any", or a gem material id
     * @param min      the smallest ingot (metal), gem weight (gems) or total ingot grams (pieces) that counts
     * @param plot     plot id the player must own, or empty
     * @param reward   multiplier on the sell value of what is asked for
     */
    public record Entry(String id, ContractKind kind, ResourceLocation item, String form, ResourceLocation metal, String gem,
                        int amountMin, int amountMax, int step, int min, int minLevel, int maxLevel, String plot,
                        int weight, double reward, int renown) {
        public boolean eligible(int level, java.util.Set<String> ownedPlots) {
            return level >= minLevel && level <= maxLevel && (plot.isEmpty() || ownedPlots.contains(plot));
        }
    }

    /** Chance of a crate by reward size: the rows are checked from the last to the first, the first that fits is used. */
    public record CrateRule(long minReward, double wooden, double iron) {}

    public record Rules(int slots, int days, int rewardStep, int rewardMin, List<CrateRule> crates) {}

    private static volatile List<Entry> ENTRIES = List.of();
    private static volatile Rules RULES = new Rules(MAX_SLOTS, 3, 8, 8, List.of());

    static {
        loadBuiltin();
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Rules rules() {
        return RULES;
    }

    public static List<Entry> entries(ContractKind kind) {
        return ENTRIES.stream().filter(e -> e.kind() == kind).toList();
    }

    /** Test hook: replaces the pool. */
    public static void setEntriesForTest(List<Entry> entries) {
        ENTRIES = List.copyOf(entries);
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
                ContractDefs.reloadFrom(manager);
            }
        });
    }

    private static void loadBuiltin() {
        try {
            List<Entry> all = new ArrayList<>();
            for (String name : BUILTIN_POOLS) {
                try (Reader r = open("/data/holeydigsite/contracts/" + name + ".json")) {
                    parsePool(JsonParser.parseReader(r).getAsJsonObject(), all);
                }
            }
            try (Reader r = open("/data/holeydigsite/contracts/rules.json")) {
                RULES = parseRules(JsonParser.parseReader(r).getAsJsonObject());
            }
            ENTRIES = List.copyOf(all);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load the built-in contract pool", e);
        }
    }

    private static void reloadFrom(ResourceManager manager) {
        try {
            List<Entry> all = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Resource> f : manager.listResources("contracts", p -> p.getPath().endsWith(".json")).entrySet()) {
                if (!f.getKey().getNamespace().equals(HoleyDigsite.MODID)) {
                    continue;
                }
                if (f.getKey().getPath().endsWith("/rules.json")) {
                    try (Reader r = f.getValue().openAsReader()) {
                        RULES = parseRules(JsonParser.parseReader(r).getAsJsonObject());
                    }
                    continue;
                }
                try (Reader r = f.getValue().openAsReader()) {
                    parsePool(JsonParser.parseReader(r).getAsJsonObject(), all);
                }
            }
            if (!all.isEmpty()) {
                ENTRIES = List.copyOf(all);
            }
        } catch (IOException | RuntimeException e) {
            HoleyDigsite.LOGGER.error("Failed to reload the contract pool", e);
        }
    }

    private static Reader open(String path) throws IOException {
        InputStream in = ContractDefs.class.getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Missing built-in resource: " + path);
        }
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    private static void parsePool(JsonObject root, List<Entry> out) {
        ContractKind kind = ContractKind.byId(root.get("kind").getAsString());
        for (JsonElement el : root.getAsJsonArray("entries")) {
            JsonObject o = el.getAsJsonObject();
            JsonArray amount = o.getAsJsonArray("amount");
            ResourceLocation item = ResourceLocation.parse(o.get(switch (kind) {
                case METAL -> "metal";
                case GEM -> "gem";
                case FOOD -> "item";
                case PIECE -> "piece";
            }).getAsString());
            int min = switch (kind) {
                case METAL -> o.has("min_ingot") ? o.get("min_ingot").getAsInt() : 0;
                case GEM -> o.has("min_weight") ? o.get("min_weight").getAsInt() : 0;
                case PIECE -> o.has("min_grams") ? o.get("min_grams").getAsInt() : 0;
                case FOOD -> 0;
            };
            out.add(new Entry(o.get("id").getAsString(), kind, item,
                    o.has("form") ? o.get("form").getAsString() : "",
                    kind == ContractKind.PIECE && o.has("metal") ? ResourceLocation.parse(o.get("metal").getAsString()) : null,
                    kind == ContractKind.PIECE && o.has("gem") ? o.get("gem").getAsString() : null,
                    amount.get(0).getAsInt(), amount.get(1).getAsInt(),
                    o.has("step") ? Math.max(1, o.get("step").getAsInt()) : 1, min,
                    o.has("min_level") ? o.get("min_level").getAsInt() : 1,
                    o.has("max_level") ? o.get("max_level").getAsInt() : Integer.MAX_VALUE,
                    o.has("plot") ? o.get("plot").getAsString() : "",
                    o.has("weight") ? o.get("weight").getAsInt() : 1,
                    o.has("reward") ? o.get("reward").getAsDouble() : 1.4,
                    o.has("renown") ? o.get("renown").getAsInt() : 10));
        }
    }

    private static Rules parseRules(JsonObject o) {
        List<CrateRule> crates = new ArrayList<>();
        if (o.has("crates")) {
            for (JsonElement el : o.getAsJsonArray("crates")) {
                JsonObject c = el.getAsJsonObject();
                crates.add(new CrateRule(c.get("min_reward").getAsLong(),
                        c.has("wooden") ? c.get("wooden").getAsDouble() : 0,
                        c.has("iron") ? c.get("iron").getAsDouble() : 0));
            }
        }
        crates.sort(java.util.Comparator.comparingLong(CrateRule::minReward));
        return new Rules(Math.min(MAX_SLOTS, o.has("slots") ? o.get("slots").getAsInt() : MAX_SLOTS),
                o.has("days") ? o.get("days").getAsInt() : 3,
                o.has("reward_step") ? Math.max(1, o.get("reward_step").getAsInt()) : 8,
                o.has("reward_min") ? o.get("reward_min").getAsInt() : 8, List.copyOf(crates));
    }
}
