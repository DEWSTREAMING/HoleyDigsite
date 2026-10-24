package com.holeydigsite.site;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Server-side tables for sites, reloaded with datapacks:
 * <ul>
 *   <li>{@code data/<ns>/site/<name>.json} - {@link SiteDef}s (structure, version, zone, ...)</li>
 *   <li>{@code data/<ns>/store_layout/<name>.json} - {@code {"slots": {"1": "minecraft:dirt", ...}}}: which catalogue item each
 *       numbered Shop Display shows. Changing it updates the displays on the next world load, no rebuild needed.</li>
 * </ul>
 * Tests and the headless check can {@link #override} a def or layout (kept across reloads until cleared).
 */
public final class SiteDefs {
    private SiteDefs() {}

    private static final FileToIdConverter SITES = FileToIdConverter.json("site");
    private static final FileToIdConverter LAYOUTS = FileToIdConverter.json("store_layout");

    private static Map<ResourceLocation, SiteDef> defs = Map.of();
    private static Map<ResourceLocation, Map<Integer, ResourceLocation>> layouts = Map.of();
    private static final Map<ResourceLocation, SiteDef> defOverrides = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Map<Integer, ResourceLocation>> layoutOverrides = new LinkedHashMap<>();

    @Nullable
    public static SiteDef get(ResourceLocation id) {
        SiteDef o = defOverrides.get(id);
        return o != null ? o : defs.get(id);
    }

    public static Collection<SiteDef> all() {
        Map<ResourceLocation, SiteDef> m = new LinkedHashMap<>(defs);
        m.putAll(defOverrides);
        return Collections.unmodifiableCollection(m.values());
    }

    /** Returns the slot to item ID mapping of a layout, or an empty map if unknown. */
    public static Map<Integer, ResourceLocation> layout(@Nullable ResourceLocation id) {
        if (id == null) {
            return Map.of();
        }
        Map<Integer, ResourceLocation> o = layoutOverrides.get(id);
        if (o != null) {
            return o;
        }
        return layouts.getOrDefault(id, Map.of());
    }

    public static void override(SiteDef def) {
        defOverrides.put(def.id(), def);
    }

    public static void overrideLayout(ResourceLocation id, Map<Integer, ResourceLocation> slots) {
        layoutOverrides.put(id, Map.copyOf(slots));
    }

    public static void clearOverrides() {
        defOverrides.clear();
        layoutOverrides.clear();
    }

    public static void clearOverride(ResourceLocation id) {
        defOverrides.remove(id);
        layoutOverrides.remove(id);
    }

    /** Parses a layout file's JSON. */
    public static Map<Integer, ResourceLocation> parseLayout(JsonObject o) {
        Map<Integer, ResourceLocation> m = new TreeMap<>();
        JsonObject slots = o.getAsJsonObject("slots");
        if (slots != null) {
            for (Map.Entry<String, JsonElement> e : slots.entrySet()) {
                if (!e.getValue().isJsonNull() && !e.getValue().getAsString().isEmpty()) {
                    m.put(Integer.parseInt(e.getKey()), ResourceLocation.parse(e.getValue().getAsString()));
                }
            }
        }
        return Collections.unmodifiableMap(m);
    }

    /** The datapack reload listener. */
    public static final class Loader extends SimplePreparableReloadListener<Object[]> {
        @Override
        protected Object[] prepare(ResourceManager rm, ProfilerFiller profiler) {
            Map<ResourceLocation, SiteDef> d = new LinkedHashMap<>();
            Map<ResourceLocation, Map<Integer, ResourceLocation>> l = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : SITES.listMatchingResources(rm).entrySet()) {
                ResourceLocation id = SITES.fileToId(e.getKey());
                try (Reader r = e.getValue().openAsReader()) {
                    d.put(id, SiteDef.parse(id, JsonParser.parseReader(r).getAsJsonObject()));
                } catch (Exception ex) {
                    HoleyDigsite.LOGGER.error("Bad site file {}", e.getKey(), ex);
                }
            }
            for (Map.Entry<ResourceLocation, Resource> e : LAYOUTS.listMatchingResources(rm).entrySet()) {
                ResourceLocation id = LAYOUTS.fileToId(e.getKey());
                try (Reader r = e.getValue().openAsReader()) {
                    l.put(id, parseLayout(JsonParser.parseReader(r).getAsJsonObject()));
                } catch (Exception ex) {
                    HoleyDigsite.LOGGER.error("Bad store layout file {}", e.getKey(), ex);
                }
            }
            return new Object[]{d, l};
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void apply(Object[] data, ResourceManager rm, ProfilerFiller profiler) {
            defs = Collections.unmodifiableMap((Map<ResourceLocation, SiteDef>) data[0]);
            layouts = Collections.unmodifiableMap((Map<ResourceLocation, Map<Integer, ResourceLocation>>) data[1]);
            HoleyDigsite.LOGGER.info("Holey Digsite: {} site definitions, {} store layouts", defs.size(), layouts.size());
        }
    }
}
