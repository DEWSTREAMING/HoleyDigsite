package com.holeydigsite.ledger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The Ledger's Coming Up list, read from {@code assets/holeydigsite/ledger/coming_up.json}.
 * Client-side data: it is updated each release and a resource pack can replace it.
 * Lives outside the screen so GameTests can parse it on a server.
 */
public final class ComingUpDefs {
    public static final String RESOURCE_PATH = "ledger/coming_up.json";
    private static final String CLASSPATH_PATH = "assets/" + HoleyDigsite.MODID + "/" + RESOURCE_PATH;

    public enum Status {
        IN_THE_WORKS, PLANNED;

        public static Status parse(String s) {
            return s != null && s.trim().toLowerCase(Locale.ROOT).equals("in_the_works") ? IN_THE_WORKS : PLANNED;
        }
    }

    public record Entry(String nameKey, String descriptionKey, Status status) {}

    private static volatile List<Entry> entries;

    private ComingUpDefs() {}

    public static List<Entry> entries() {
        List<Entry> e = entries;
        if (e == null) {
            e = loadClasspath();
            entries = e;
        }
        return e;
    }

    /** Never throws: anything unreadable gives an empty list, entries missing a key are skipped. */
    public static List<Entry> parse(Reader reader) {
        try {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return List.of();
            JsonElement list = root.getAsJsonObject().get("entries");
            if (list == null || !list.isJsonArray()) return List.of();
            List<Entry> out = new ArrayList<>();
            for (JsonElement el : (JsonArray) list) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String name = string(o, "name");
                String desc = string(o, "description");
                if (name == null || desc == null) continue;
                out.add(new Entry(name, desc, Status.parse(string(o, "status"))));
            }
            return List.copyOf(out);
        } catch (Exception e) {
            HoleyDigsite.LOGGER.warn("Could not read the Coming Up list: {}", e.getMessage());
            return List.of();
        }
    }

    public static List<Entry> parse(String json) {
        return parse(new StringReader(json == null ? "" : json));
    }

    private static String string(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || !el.isJsonPrimitive()) return null;
        String s = el.getAsString();
        return s.isBlank() ? null : s;
    }

    public static List<Entry> loadClasspath() {
        try (InputStream in = ComingUpDefs.class.getClassLoader().getResourceAsStream(CLASSPATH_PATH)) {
            if (in == null) return List.of();
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return List.of();
        }
    }

    public static void reload(ResourceManager resourceManager) {
        Optional<Resource> res = resourceManager.getResource(HoleyDigsite.id(RESOURCE_PATH));
        if (res.isEmpty()) {
            entries = List.of();
            return;
        }
        try (Reader r = res.get().openAsReader()) {
            entries = parse(r);
        } catch (Exception e) {
            HoleyDigsite.LOGGER.warn("Could not read the Coming Up list: {}", e.getMessage());
            entries = List.of();
        }
    }
}
