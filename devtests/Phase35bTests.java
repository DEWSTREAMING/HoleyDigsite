package com.holeydigsite.devtest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.ledger.ComingUpDefs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The Ledger's Coming Up list (parser and shipped file) and the new screen lang keys.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase35bTests {
    private static final Gson GSON = new Gson();

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static JsonObject lang(GameTestHelper h) {
        try (InputStream in = Phase35bTests.class.getClassLoader()
                .getResourceAsStream("assets/holeydigsite/lang/en_us.json")) {
            check(h, in != null, "Missing en_us.json");
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            h.fail("Failed reading en_us.json: " + e.getMessage());
            return null;
        }
    }

    private static boolean has(JsonObject lang, String key) {
        return lang.has(key) && !lang.get(key).getAsString().isBlank();
    }

    @GameTest(template = "empty_long", batch = "phase35b")
    public static void coming_up_file_parses(GameTestHelper h) {
        List<ComingUpDefs.Entry> entries = ComingUpDefs.loadClasspath();
        // The list changes every release, so check its shape rather than its contents
        check(h, !entries.isEmpty(), "the shipped Coming Up list is empty");
        check(h, entries.stream().anyMatch(e -> e.status() == ComingUpDefs.Status.IN_THE_WORKS),
                "something in the shipped list should be in the works");
        check(h, entries.stream().map(ComingUpDefs.Entry::nameKey).distinct().count() == entries.size(),
                "no entry is listed twice");
        check(h, ComingUpDefs.entries().size() == entries.size(), "entries() should fall back to the shipped file");
        h.succeed();
    }

    @GameTest(template = "empty_long", batch = "phase35b")
    public static void coming_up_lang_keys_exist(GameTestHelper h) {
        JsonObject lang = lang(h);
        for (ComingUpDefs.Entry e : ComingUpDefs.loadClasspath()) {
            check(h, has(lang, e.nameKey()), "Missing lang key " + e.nameKey());
            check(h, has(lang, e.descriptionKey()), "Missing lang key " + e.descriptionKey());
        }
        for (String key : List.of(
                "screen.holeydigsite.field_guide.close",
                "screen.holeydigsite.ledger.close",
                "screen.holeydigsite.close.tooltip",
                "screen.holeydigsite.ledger.tab.coming_up",
                "screen.holeydigsite.ledger.coming_up.in_the_works",
                "screen.holeydigsite.ledger.coming_up.planned",
                "screen.holeydigsite.ledger.coming_up.empty",
                "screen.holeydigsite.ledger.coming_up.footer")) {
            check(h, has(lang, key), "Missing lang key " + key);
        }
        h.succeed();
    }

    @GameTest(template = "empty_long", batch = "phase35b")
    public static void coming_up_unknown_status_is_planned(GameTestHelper h) {
        List<ComingUpDefs.Entry> entries = ComingUpDefs.parse("""
                {"entries": [
                  {"name": "a", "description": "a.desc", "status": "someday"},
                  {"name": "b", "description": "b.desc"},
                  {"name": "c", "description": "c.desc", "status": "IN_THE_WORKS"}
                ]}""");
        check(h, entries.size() == 3, "Expected 3 entries, got " + entries.size());
        check(h, entries.get(0).status() == ComingUpDefs.Status.PLANNED, "Unknown status should be planned");
        check(h, entries.get(1).status() == ComingUpDefs.Status.PLANNED, "Missing status should be planned");
        check(h, entries.get(2).status() == ComingUpDefs.Status.IN_THE_WORKS, "Status should ignore case");
        h.succeed();
    }

    @GameTest(template = "empty_long", batch = "phase35b")
    public static void coming_up_broken_file_is_empty(GameTestHelper h) {
        check(h, ComingUpDefs.parse("{\"entries\": [ {\"name\": ").isEmpty(), "Cut-off JSON should give an empty list");
        check(h, ComingUpDefs.parse("not json at all {").isEmpty(), "Garbage should give an empty list");
        check(h, ComingUpDefs.parse("").isEmpty(), "Empty file should give an empty list");
        check(h, ComingUpDefs.parse("[1, 2, 3]").isEmpty(), "A bare array should give an empty list");
        check(h, ComingUpDefs.parse("{\"entries\": \"nope\"}").isEmpty(), "Non-array entries should give an empty list");
        check(h, ComingUpDefs.parse((String) null).isEmpty(), "Null should give an empty list");
        h.succeed();
    }

    @GameTest(template = "empty_long", batch = "phase35b")
    public static void coming_up_skips_incomplete_entries(GameTestHelper h) {
        List<ComingUpDefs.Entry> entries = ComingUpDefs.parse("""
                {"entries": [
                  {"name": "a", "description": "a.desc", "status": "planned"},
                  {"name": "b"},
                  {"description": "c.desc"},
                  {"name": "", "description": "d.desc"},
                  "loose string",
                  {"name": "e", "description": "e.desc", "status": "in_the_works"}
                ]}""");
        check(h, entries.size() == 2, "Expected 2 complete entries, got " + entries.size());
        check(h, entries.get(0).nameKey().equals("a") && entries.get(1).nameKey().equals("e"),
                "Kept the wrong entries");
        h.succeed();
    }
}
