package com.holeydigsite.guide;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holeydigsite.HoleyDigsite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads the eight Field Guide page definitions from
 * {@code assets/holeydigsite/field_guide/pages/*.json}.
 */
public final class FieldGuideDefs {
    public static final int WIN_W = 412;
    public static final int WIN_H = 216;

    private static final Gson GSON = new Gson();
    private static final String PAGES_DIR = "assets/holeydigsite/field_guide/pages";
    private static final long END_PAUSE_MS = 1000L;
    private static final Map<UUID, Integer> LAST_PAGE = new HashMap<>();

    private static final List<String> DEFAULT_PAGE_IDS = List.of(
            "dig_and_pan",
            "sell",
            "buy",
            "first_line",
            "belts",
            "metal",
            "land_plots",
            "ledger"
    );

    public record Page(
            String id,
            int order,
            String titleKey,
            List<String> textKeys,
            String clip,
            int frames,
            int fps,
            int cols,
            int frameW,
            int frameH
    ) {
        public ResourceLocation texture() {
            return HoleyDigsite.id("textures/gui/field_guide/" + clip + ".png");
        }

        public int rows() {
            int safeCols = Math.max(1, cols);
            int safeFrames = Math.max(1, frames);
            return (safeFrames + safeCols - 1) / safeCols;
        }

        public int sheetWidth() {
            return Math.max(1, cols) * Math.max(1, frameW);
        }

        public int sheetHeight() {
            return rows() * Math.max(1, frameH);
        }
    }

    private static volatile List<Page> pages;

    private FieldGuideDefs() {}

    public static int lastPageFor(UUID playerId) {
        if (playerId == null) return 0;
        return LAST_PAGE.getOrDefault(playerId, 0);
    }

    public static void setLastPageFor(UUID playerId, int pageIndex) {
        if (playerId != null) {
            LAST_PAGE.put(playerId, Math.max(0, pageIndex));
        }
    }

    public static List<Page> pages() {
        ensureLoaded();
        return pages;
    }

    public static Page pageById(String id) {
        for (Page p : pages()) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Computes which frame index (0..frames-1) to display at {@code elapsedMs}.
     * Single-frame pages always return 0. Multi-frame clips step at {@code fps}
     * and pause on the final frame for {@link #END_PAUSE_MS} before looping.
     */
    public static int frameAt(Page page, long elapsedMs) {
        if (page == null || page.frames() <= 1 || page.fps() <= 0) {
            return 0;
        }
        long frameMs = Math.max(1L, 1000L / page.fps());
        long playMs = page.frames() * frameMs;
        long cycleMs = playMs + END_PAUSE_MS;
        long pos = Math.floorMod(elapsedMs, cycleMs);
        if (pos >= playMs) {
            return page.frames() - 1;
        }
        return (int) Math.min(page.frames() - 1, pos / frameMs);
    }

    public static synchronized void reload() {
        pages = null;
        ensureLoaded();
    }

    public static synchronized void reload(ResourceManager resourceManager) {
        if (resourceManager == null) {
            reload();
            return;
        }
        Map<String, Page> loaded = new LinkedHashMap<>();
        var found = resourceManager.listResources("field_guide/pages",
                rl -> HoleyDigsite.MODID.equals(rl.getNamespace()) && rl.getPath().endsWith(".json"));
        for (var entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            int slash = path.lastIndexOf('/');
            String fileName = slash >= 0 ? path.substring(slash + 1) : path;
            String id = fileName.substring(0, fileName.length() - ".json".length());
            try (InputStream in = entry.getValue().open();
                 InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Page p = parsePage(id, GSON.fromJson(r, JsonObject.class));
                loaded.put(id, p);
            } catch (Exception e) {
                HoleyDigsite.LOGGER.error("Failed to load Field Guide page {}", entry.getKey(), e);
            }
        }
        if (loaded.isEmpty()) {
            reload();
            return;
        }
        List<Page> sorted = new ArrayList<>(loaded.values());
        sorted.sort(Comparator.comparingInt(Page::order).thenComparing(Page::id));
        pages = List.copyOf(sorted);
    }

    private static void ensureLoaded() {
        if (pages != null) return;
        synchronized (FieldGuideDefs.class) {
            if (pages != null) return;
            Set<String> ids = discoverClasspathPageIds();
            List<Page> list = new ArrayList<>();
            for (String id : ids) {
                String resPath = PAGES_DIR + "/" + id + ".json";
                try (InputStream in = FieldGuideDefs.class.getClassLoader().getResourceAsStream(resPath)) {
                    if (in == null) continue;
                    try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        JsonObject root = GSON.fromJson(r, JsonObject.class);
                        list.add(parsePage(id, root));
                    }
                } catch (Exception e) {
                    HoleyDigsite.LOGGER.error("Failed to load Field Guide page {}", resPath, e);
                }
            }
            list.sort(Comparator.comparingInt(Page::order).thenComparing(Page::id));
            pages = List.copyOf(list);
        }
    }

    private static Set<String> discoverClasspathPageIds() {
        Set<String> ids = new LinkedHashSet<>(DEFAULT_PAGE_IDS);
        try {
            Enumeration<URL> urls = FieldGuideDefs.class.getClassLoader().getResources(PAGES_DIR);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    File dir = new File(url.toURI());
                    File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                    if (files != null) {
                        for (File f : files) {
                            String n = f.getName();
                            ids.add(n.substring(0, n.length() - 5));
                        }
                    }
                } else if ("jar".equals(protocol)) {
                    JarURLConnection conn = (JarURLConnection) url.openConnection();
                    try (JarFile jar = conn.getJarFile()) {
                        Enumeration<JarEntry> entries = jar.entries();
                        String prefix = PAGES_DIR + "/";
                        while (entries.hasMoreElements()) {
                            String name = entries.nextElement().getName();
                            if (name.startsWith(prefix) && name.endsWith(".json") && name.indexOf('/', prefix.length()) < 0) {
                                ids.add(name.substring(prefix.length(), name.length() - 5));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private static Page parsePage(String fallbackId, JsonObject obj) {
        String id = obj.has("id") ? obj.get("id").getAsString() : fallbackId;
        int order = obj.has("order") ? obj.get("order").getAsInt() : 999;
        String titleKey = obj.get("title").getAsString();
        List<String> textKeys = new ArrayList<>();
        if (obj.has("text") && obj.get("text").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("text")) {
                textKeys.add(el.getAsString());
            }
        }
        String clip = obj.has("clip") ? obj.get("clip").getAsString() : id;
        int frames = obj.has("frames") ? Math.max(1, obj.get("frames").getAsInt()) : 1;
        int fps = obj.has("fps") ? Math.max(1, obj.get("fps").getAsInt()) : 10;
        int cols = obj.has("cols") ? Math.max(1, obj.get("cols").getAsInt()) : 1;
        int frameW = obj.has("frame_w") ? Math.max(1, obj.get("frame_w").getAsInt()) : 320;
        int frameH = obj.has("frame_h") ? Math.max(1, obj.get("frame_h").getAsInt()) : 180;
        return new Page(id, order, titleKey, List.copyOf(textKeys), clip, frames, fps, cols, frameW, frameH);
    }
}
