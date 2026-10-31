package com.holeydigsite.crate;

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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mining Crate tables: {@code data/holeydigsite/crates/<tier>.json} (rarity odds, pools, panning find rate) and
 * {@code data/holeydigsite/crate_rules.json} (skin wears and Skin Scrap amounts). The built-in files are read from the
 * jar at class load so the client tooltip has the odds too; a datapack reload replaces them on the server.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class CrateDefs {
    private CrateDefs() {}

    /** Best to worst. */
    public static final List<String> RARITIES = List.of("common", "rare", "epic", "legendary");
    public static final List<String> BUILTIN_TIERS = List.of("prospector_crate", "outback_crate", "wooden_crate", "iron_crate", "gold_crate");

    public static final List<String> FIND_SOURCES = List.of("pan", "sluice", "drill", "shovel");

    public enum Kind { SKIN, TITLE, ITEM, COINS, INGOT }

    public static net.minecraft.ChatFormatting rarityFormatting(String rarity) {
        return switch (rarity) {
            case "rare" -> net.minecraft.ChatFormatting.BLUE;
            case "epic" -> net.minecraft.ChatFormatting.DARK_PURPLE;
            case "legendary" -> net.minecraft.ChatFormatting.GOLD;
            default -> net.minecraft.ChatFormatting.WHITE;
        };
    }

    /**
     * One pool entry. {@code id} is the skin id, title id or item id; {@code amount} is the item count, the coin copper or the
     * ingot grams.
     */
    public record Entry(Kind kind, String id, int amount, int weight) {}

    /**
     * One crate collection. {@code old}: a Wooden, Iron or Gold Crate from before collections (never sold, found or given any
     * more, still opens). {@code finds}: source to "one in N" for locked crates turning up while working (pan, sluice, drill,
     * shovel); a {@code site} limits the finds to the site of that id. Odds are per crate in the data and may be decimals.
     */
    public record CrateTier(String id, String name, String look, boolean old, Map<String, Double> odds, Map<String, Integer> finds,
                            String site, Map<String, List<Entry>> pools) {
        public CrateTier(String id, Map<String, Double> odds, int panFindOneIn, Map<String, List<Entry>> pools) {
            this(id, "crate.holeydigsite.collection." + id, id, false, odds, panFindOneIn > 0 ? Map.of("pan", panFindOneIn) : Map.of(), "", pools);
        }

        public double totalOdds() {
            double t = 0;
            for (double v : odds.values()) t += Math.max(0, v);
            return t;
        }

        public List<Entry> pool(String rarity) {
            return pools.getOrDefault(rarity, List.of());
        }

        /** Chance in percent of rolling {@code rarity}, as written in the data. */
        public double percent(String rarity) {
            double total = totalOdds();
            return total <= 0 ? 0 : 100.0 * Math.max(0, odds.getOrDefault(rarity, 0.0)) / total;
        }

        /** One in N for a find source (pan, sluice, drill, shovel), 0 if crates are not found there. */
        public int findOneIn(String source) {
            return finds.getOrDefault(source, 0);
        }

        public int panFindOneIn() {
            return findOneIn("pan");
        }

        public boolean found() {
            return !finds.isEmpty();
        }
    }

    public record Wear(String id, int odds, double scrapMultiplier) {}

    /**
     * {@code lockedSellCopper}: what the Sell Pad pays for a locked crate. {@code offCrateCopper} and {@code offSkinCopper}: the coins a
     * Ledger or contract crate and a Ledger skin pay instead while the player's crate switch is off.
     */
    public record Rules(List<Wear> wears, Map<String, Integer> scrap, int lockedSellCopper, int offCrateCopper,
                        Map<String, Integer> offSkinCopper) {
        public Rules(List<Wear> wears, Map<String, Integer> scrap) {
            this(wears, scrap, 48, 1536, Map.of("common", 768, "rare", 2304, "epic", 6144, "legendary", 15360));
        }
    }

    private static volatile Map<String, CrateTier> TIERS = Map.of();
    private static volatile Rules RULES = new Rules(List.of(new Wear("fresh_from_the_forge", 1, 1.0)), Map.of());

    static {
        loadBuiltin();
    }

    public static Map<String, CrateTier> tiers() {
        return TIERS;
    }

    @Nullable
    public static CrateTier tier(String id) {
        return id == null ? null : TIERS.get(id);
    }

    public static Rules rules() {
        return RULES;
    }

    /** Test hook: replace one tier (null restores nothing, call {@link #reloadBuiltin()} afterwards). */
    public static void setTierForTest(CrateTier tier) {
        Map<String, CrateTier> map = new LinkedHashMap<>(TIERS);
        map.put(tier.id(), tier);
        TIERS = Map.copyOf(map);
    }

    public static void reloadBuiltin() {
        loadBuiltin();
    }

    private static void loadBuiltin() {
        Map<String, CrateTier> map = new LinkedHashMap<>();
        for (String id : BUILTIN_TIERS) {
            try (InputStream in = CrateDefs.class.getResourceAsStream("/data/holeydigsite/crates/" + id + ".json")) {
                if (in != null) {
                    map.put(id, parseTier(id, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()));
                }
            } catch (Exception e) {
                HoleyDigsite.LOGGER.warn("Bad crate file {}: {}", id, e.toString());
            }
        }
        TIERS = Map.copyOf(map);
        try (InputStream in = CrateDefs.class.getResourceAsStream("/data/holeydigsite/crate_rules.json")) {
            if (in != null) {
                RULES = parseRules(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        } catch (Exception e) {
            HoleyDigsite.LOGGER.warn("Bad crate_rules.json: {}", e.toString());
        }
    }

    static CrateTier parseTier(String id, JsonObject obj) {
        Map<String, Double> odds = new LinkedHashMap<>();
        JsonObject o = obj.getAsJsonObject("odds");
        for (String r : RARITIES) {
            odds.put(r, o != null && o.has(r) ? o.get(r).getAsDouble() : 0.0);
        }
        Map<String, List<Entry>> pools = new LinkedHashMap<>();
        JsonObject p = obj.getAsJsonObject("pools");
        for (String r : RARITIES) {
            List<Entry> list = new ArrayList<>();
            if (p != null && p.has(r)) {
                for (JsonElement el : p.getAsJsonArray(r)) {
                    parseEntry(el.getAsJsonObject()).ifPresent(list::add);
                }
            }
            pools.put(r, List.copyOf(list));
        }
        Map<String, Integer> finds = new LinkedHashMap<>();
        if (obj.has("pan_find_one_in") && obj.get("pan_find_one_in").getAsInt() > 0) {
            finds.put("pan", obj.get("pan_find_one_in").getAsInt());
        }
        JsonObject f = obj.getAsJsonObject("finds");
        if (f != null) {
            for (String source : FIND_SOURCES) {
                if (f.has(source) && f.get(source).getAsInt() > 0) finds.put(source, f.get(source).getAsInt());
            }
        }
        boolean old = obj.has("old") && obj.get("old").getAsBoolean();
        String name = obj.has("name") ? obj.get("name").getAsString() : "crate.holeydigsite.collection." + id;
        String look = obj.has("look") ? obj.get("look").getAsString() : id;
        String site = obj.has("site") ? obj.get("site").getAsString() : "";
        return new CrateTier(id, name, look, old, Map.copyOf(odds), Map.copyOf(finds), site, Map.copyOf(pools));
    }

    private static Optional<Entry> parseEntry(JsonObject e) {
        int weight = e.has("weight") ? Math.max(1, e.get("weight").getAsInt()) : 1;
        if (e.has("skin")) return Optional.of(new Entry(Kind.SKIN, e.get("skin").getAsString(), 1, weight));
        if (e.has("title")) return Optional.of(new Entry(Kind.TITLE, e.get("title").getAsString(), 1, weight));
        if (e.has("coins")) return Optional.of(new Entry(Kind.COINS, "", e.get("coins").getAsInt(), weight));
        if (e.has("ingot")) {
            int grams = e.has("grams") ? e.get("grams").getAsInt() : 100;
            return Optional.of(new Entry(Kind.INGOT, e.get("ingot").getAsString(), grams, weight));
        }
        if (e.has("item")) {
            int count = e.has("count") ? e.get("count").getAsInt() : 1;
            return Optional.of(new Entry(Kind.ITEM, e.get("item").getAsString(), count, weight));
        }
        return Optional.empty();
    }

    static Rules parseRules(JsonObject obj) {
        List<Wear> wears = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("wears")) {
            JsonObject w = el.getAsJsonObject();
            wears.add(new Wear(w.get("id").getAsString(), w.get("odds").getAsInt(),
                    w.has("scrap_multiplier") ? w.get("scrap_multiplier").getAsDouble() : 1.0));
        }
        Map<String, Integer> scrap = new LinkedHashMap<>();
        JsonObject s = obj.getAsJsonObject("scrap");
        for (String r : RARITIES) {
            scrap.put(r, s != null && s.has(r) ? s.get(r).getAsInt() : 1);
        }
        Map<String, Integer> offSkin = new LinkedHashMap<>();
        JsonObject off = obj.getAsJsonObject("off_coins");
        JsonObject offSkins = off != null && off.has("skin") ? off.getAsJsonObject("skin") : null;
        for (String r : RARITIES) {
            offSkin.put(r, offSkins != null && offSkins.has(r) ? offSkins.get(r).getAsInt() : 0);
        }
        return new Rules(List.copyOf(wears), Map.copyOf(scrap), obj.has("locked_sell_copper") ? obj.get("locked_sell_copper").getAsInt() : 48,
                off != null && off.has("crate") ? off.get("crate").getAsInt() : 1536, Map.copyOf(offSkin));
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager rm, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void v, ResourceManager rm, ProfilerFiller profiler) {
                Map<String, CrateTier> map = new LinkedHashMap<>();
                for (Map.Entry<ResourceLocation, Resource> entry : rm.listResources("crates",
                        loc -> loc.getNamespace().equals(HoleyDigsite.MODID) && loc.getPath().endsWith(".json")).entrySet()) {
                    String path = entry.getKey().getPath();
                    String id = path.substring("crates/".length(), path.length() - ".json".length());
                    try (Reader r = entry.getValue().openAsReader()) {
                        map.put(id, parseTier(id, JsonParser.parseReader(r).getAsJsonObject()));
                    } catch (Exception e) {
                        HoleyDigsite.LOGGER.warn("Bad crate file {}: {}", entry.getKey(), e.toString());
                    }
                }
                if (!map.isEmpty()) {
                    TIERS = Map.copyOf(map);
                }
                rm.getResource(HoleyDigsite.id("crate_rules.json")).ifPresent(res -> {
                    try (Reader r = res.openAsReader()) {
                        RULES = parseRules(JsonParser.parseReader(r).getAsJsonObject());
                    } catch (Exception e) {
                        HoleyDigsite.LOGGER.warn("Bad crate_rules.json: {}", e.toString());
                    }
                });
            }
        });
    }
}
