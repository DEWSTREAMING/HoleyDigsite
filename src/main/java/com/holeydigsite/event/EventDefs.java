package com.holeydigsite.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event definitions loader: reads {@code data/holeydigsite/events/index.json} (active event)
 * and {@code data/holeydigsite/events/<event>.json} (tiers, finds, chip prices).
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class EventDefs {
    private EventDefs() {}

    /** One tier: its rock, clump, loot table and the structure of its event island section (null = none). */
    public record EventTier(int tier, ResourceLocation rock, ResourceLocation clump, ResourceLocation table,
                            @Nullable ResourceLocation section) {}

    public record ChipValue(double perGram, double ingotPerGram, int each, int uncutEach) {
        public boolean isMetal() {
            return perGram > 0 || ingotPerGram > 0;
        }

        public double uncutRatio() {
            if (each <= 0) return 0.5;
            return uncutEach > 0 ? (double) uncutEach / each : 0.5;
        }
    }

    public record EventDef(
            String id,
            String name,
            List<EventTier> tiers,
            Map<ResourceLocation, ChipValue> chips
    ) {
        @Nullable
        public EventTier tier(int n) {
            for (EventTier t : tiers) {
                if (t.tier() == n) {
                    return t;
                }
            }
            return null;
        }
    }

    /** Every event thing (finds, rocks, Hardpan, clumps); a new event adds its items to these tags. */
    public static final TagKey<Item> EVENT_ITEMS = TagKey.create(Registries.ITEM, HoleyDigsite.id("event_items"));
    /** Event gems, metals, pebbles and trophies: what the event Sell Pad takes. */
    public static final TagKey<Item> EVENT_FINDS = TagKey.create(Registries.ITEM, HoleyDigsite.id("event_finds"));
    public static final TagKey<Item> EVENT_GEMS = TagKey.create(Registries.ITEM, HoleyDigsite.id("event_gems"));
    public static final TagKey<Item> EVENT_METALS = TagKey.create(Registries.ITEM, HoleyDigsite.id("event_metals"));

    // server side only: loaded from data/holeydigsite/events/ on every reload
    private static volatile String activeEventId = null;
    private static volatile Map<String, EventDef> events = Map.of();
    private static String testActiveEventOverride = null;

    public static EventDef parseEvent(String id, JsonObject obj) {
        String name = obj.has("name") ? obj.get("name").getAsString() : "event.holeydigsite." + id;
        List<EventTier> tiers = new ArrayList<>();
        if (obj.has("tiers") && obj.get("tiers").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("tiers")) {
                JsonObject t = el.getAsJsonObject();
                int tier = t.get("tier").getAsInt();
                ResourceLocation rock = ResourceLocation.parse(t.get("rock").getAsString());
                ResourceLocation clump = ResourceLocation.parse(t.get("clump").getAsString());
                ResourceLocation table = ResourceLocation.parse(t.get("table").getAsString());
                ResourceLocation section = t.has("section") ? ResourceLocation.parse(t.get("section").getAsString()) : null;
                tiers.add(new EventTier(tier, rock, clump, table, section));
            }
        }
        Map<ResourceLocation, ChipValue> chips = new LinkedHashMap<>();
        if (obj.has("chips") && obj.get("chips").isJsonObject()) {
            JsonObject cObj = obj.getAsJsonObject("chips");
            for (String key : cObj.keySet()) {
                JsonObject val = cObj.getAsJsonObject(key);
                double perGram = val.has("per_gram") ? val.get("per_gram").getAsDouble() : 0.0;
                double ingotPerGram = val.has("ingot_per_gram") ? val.get("ingot_per_gram").getAsDouble() : perGram;
                int each = val.has("each") ? val.get("each").getAsInt() : 0;
                int uncutEach = val.has("uncut_each") ? val.get("uncut_each").getAsInt() : (int) Math.round(each * 0.5);
                chips.put(ResourceLocation.parse(key), new ChipValue(perGram, ingotPerGram, each, uncutEach));
            }
        }
        return new EventDef(id, name, List.copyOf(tiers), Map.copyOf(chips));
    }

    @Nullable
    public static String getActiveEventId() {
        return testActiveEventOverride != null ? testActiveEventOverride : activeEventId;
    }

    public static void setActiveEventForTest(@Nullable String id) {
        testActiveEventOverride = id;
    }

    /** Test hook: adds (or with null removes) an event that isn't shipped. */
    public static void putEventForTest(String id, @Nullable EventDef def) {
        Map<String, EventDef> map = new LinkedHashMap<>(events);
        if (def == null) {
            map.remove(id);
        } else {
            map.put(id, def);
        }
        events = Map.copyOf(map);
    }

    @Nullable
    public static EventDef getActiveEvent() {
        String active = getActiveEventId();
        return active != null ? events.get(active) : null;
    }

    @Nullable
    public static EventDef getEvent(String id) {
        return events.get(id);
    }

    public static Collection<EventDef> allEvents() {
        return Collections.unmodifiableCollection(events.values());
    }

    /** True for anything that belongs to an event (tags work on the client too), or a clump with the dug mark. */
    public static boolean isEventItem(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(EVENT_ITEMS) || stack.has(ModDataComponents.EVENT_DUG.get()));
    }

    /** True for event rocks, Hardpan and event clumps. */
    public static boolean isEventRockOrClump(ItemStack stack) {
        return stack.is(EVENT_ITEMS) && !stack.is(EVENT_FINDS);
    }

    /** True for event metal chunks and ingots. */
    public static boolean isEventMetal(ItemStack stack) {
        return stack.is(EVENT_METALS);
    }

    /** True for event gems, cut or uncut. */
    public static boolean isEventGem(ItemStack stack) {
        return stack.is(EVENT_GEMS);
    }

    /** True for everything the event Sell Pad takes. */
    public static boolean isEventFind(ItemStack stack) {
        return stack.is(EVENT_FINDS);
    }

    /** True if {@code material} is the metal or gem material of any loaded event's finds. */
    public static boolean isEventMaterial(ResourceLocation material) {
        for (EventDef ev : events.values()) {
            if (ev.chips().containsKey(material)) {
                return true;
            }
        }
        return false;
    }

    /** Opal Chip value of a stack, or 0 if not an event find. */
    public static int chipValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        EventDef ev = getActiveEvent();
        if (ev == null) return 0;

        ResourceInfo info = Selling.resource(stack);
        ResourceLocation mat = info != null ? info.material() : BuiltInRegistries.ITEM.getKey(stack.getItem());
        ChipValue cv = ev.chips().get(mat);
        if (cv == null) {
            cv = ev.chips().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        if (cv == null) return 0;

        if (cv.isMetal()) {
            int grams = Selling.grams(stack);
            boolean ingot = info != null && info.isIngot();
            double perGram = ingot ? cv.ingotPerGram() : cv.perGram();
            return (int) Math.floor(grams * perGram * stack.getCount() + 1e-9);
        }
        boolean uncut = info != null && info.isUncut();
        int each = uncut ? cv.uncutEach() : cv.each();
        return each * stack.getCount();
    }

    /** Uncut to cut ratio for a gem material (default 0.5). */
    public static double uncutRatio(ResourceLocation material) {
        EventDef ev = getActiveEvent();
        if (ev != null) {
            ChipValue cv = ev.chips().get(material);
            if (cv != null) return cv.uncutRatio();
        }
        return 0.5;
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
                activeEventId = null;
                rm.getResource(HoleyDigsite.id("events/index.json")).ifPresent(res -> {
                    try (Reader r = res.openAsReader()) {
                        JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                        if (obj.has("active")) {
                            activeEventId = obj.get("active").getAsString();
                        }
                    } catch (Exception e) {
                        HoleyDigsite.LOGGER.warn("Bad events/index.json: {}", e.toString());
                    }
                });

                Map<String, EventDef> map = new LinkedHashMap<>();
                for (Map.Entry<ResourceLocation, Resource> entry : rm.listResources("events",
                        loc -> loc.getNamespace().equals(HoleyDigsite.MODID) && loc.getPath().endsWith(".json")
                                && !loc.getPath().endsWith("index.json")).entrySet()) {
                    String path = entry.getKey().getPath();
                    String id = path.substring("events/".length(), path.length() - ".json".length());
                    try (Reader r = entry.getValue().openAsReader()) {
                        map.put(id, parseEvent(id, JsonParser.parseReader(r).getAsJsonObject()));
                    } catch (Exception e) {
                        HoleyDigsite.LOGGER.warn("Bad event file {}: {}", entry.getKey(), e.toString());
                    }
                }
                events = Map.copyOf(map);
            }
        });
    }
}
