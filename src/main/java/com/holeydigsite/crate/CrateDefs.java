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
    public static final List<String> BUILTIN_TIERS = List.of("wooden_crate", "iron_crate", "gold_crate");

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

    public record CrateTier(String id, Map<String, Integer> odds, int panFindOneIn, Map<String, List<Entry>> pools) {
        public int totalOdds() {
            int t = 0;
            for (int v : odds.values()) t += Math.max(0, v);
            return t;
        }

        public List<Entry> pool(String rarity) {
            return pools.getOrDefault(rarity, List.of());
        }

        /** Chance in percent of rolling {@code rarity}, as written in the data. */
        public double percent(String rarity) {
            int total = totalOdds();
            return total <= 0 ? 0 : 100.0 * Math.max(0, odds.getOrDefault(rarity, 0)) / total;
        }
    }

    public record Wear(String id, int odds, double scrapMultiplier) {}

    public record Rules(List<Wear> wears, Map<String, Integer> scrap) {}

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
        Map<String, Integer> odds = new LinkedHashMap<>();
        JsonObject o = obj.getAsJsonObject("odds");
        for (String r : RARITIES) {
            odds.put(r, o != null && o.has(r) ? o.get(r).getAsInt() : 0);
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
        int pan = obj.has("pan_find_one_in") ? obj.get("pan_find_one_in").getAsInt() : 0;
        return new CrateTier(id, Map.copyOf(odds), pan, Map.copyOf(pools));
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
        return new Rules(List.copyOf(wears), Map.copyOf(scrap));
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
