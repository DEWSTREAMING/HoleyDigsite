package com.holeydigsite.tier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 15: the tier registry. Reads {@code data/holeydigsite/tiers/index.json} and one JSON per family from the mod jar when
 * the mod is constructed (registries cannot change at runtime, so these files are NOT reloadable datapack data), registers
 * every tier's block and item by looping, and hands the registered holders to {@code ModBlocks} / {@code ModItems} /
 * {@code ModBlockEntities} / the creative tab.
 */
public final class Tiers {
    private Tiers() {}

    private static final String ROOT = "/data/holeydigsite/tiers/";

    private static final Map<String, TierFamily> FAMILIES = new LinkedHashMap<>();
    private static final Map<String, TierDef> BY_ID = new LinkedHashMap<>();
    private static final Map<String, DeferredBlock<Block>> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<Item>> ITEMS = new LinkedHashMap<>();

    static {
        try (InputStream in = open("index.json")) {
            JsonObject index = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement f : index.getAsJsonArray("families")) {
                String family = f.getAsString();
                try (InputStream fin = open(family + ".json")) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(fin, StandardCharsets.UTF_8)).getAsJsonObject();
                    List<TierDef> tiers = new ArrayList<>();
                    for (JsonElement t : root.getAsJsonArray("tiers")) {
                        TierDef def = new TierDef(family, t.getAsJsonObject());
                        if (BY_ID.put(def.id(), def) != null) {
                            throw new IllegalStateException("duplicate tier id " + def.id());
                        }
                        tiers.add(def);
                    }
                    FAMILIES.put(family, new TierFamily(family, List.copyOf(tiers)));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the tier definitions", e);
        }
    }

    private static InputStream open(String name) throws IOException {
        InputStream in = Tiers.class.getResourceAsStream(ROOT + name);
        if (in == null) {
            throw new IOException("missing " + ROOT + name);
        }
        return in;
    }

    // ---- lookups ----

    public static List<TierFamily> families() { return List.copyOf(FAMILIES.values()); }

    public static TierFamily family(String name) {
        TierFamily f = FAMILIES.get(name);
        if (f == null) {
            throw new IllegalArgumentException("no tier family '" + name + "'");
        }
        return f;
    }

    public static TierDef def(String id) {
        TierDef d = BY_ID.get(id);
        if (d == null) {
            throw new IllegalArgumentException("no tier '" + id + "'");
        }
        return d;
    }

    /** A stat of tier {@code id} (compile-time-free replacement for the old per-tier constants). */
    public static int stat(String id, String key) {
        return def(id).stat(key);
    }

    // ---- registration ----

    /** Registers every tier block (call once, from {@code ModBlocks}). */
    public static void registerBlocks(DeferredRegister.Blocks blocks) {
        for (TierFamily f : FAMILIES.values()) {
            for (TierDef d : f.tiers()) {
                if (d.hasBlock()) {
                    BLOCKS.put(d.id(), blocks.register(d.id(), () -> TierKinds.createBlock(d)));
                }
            }
        }
    }

    /** Registers every tier item (call once, from {@code ModItems}). */
    public static void registerItems(DeferredRegister.Items items) {
        for (TierFamily f : FAMILIES.values()) {
            for (TierDef d : f.tiers()) {
                ITEMS.put(d.id(), items.register(d.id(), () -> TierKinds.createItem(d, d.hasBlock() ? BLOCKS.get(d.id()).get() : null)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> DeferredBlock<T> block(String id) {
        DeferredBlock<Block> b = BLOCKS.get(id);
        if (b == null) {
            throw new IllegalArgumentException("no tier block '" + id + "' (registered yet?)");
        }
        return (DeferredBlock<T>) (DeferredBlock<?>) b;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Item> DeferredItem<T> item(String id) {
        DeferredItem<Item> i = ITEMS.get(id);
        if (i == null) {
            throw new IllegalArgumentException("no tier item '" + id + "' (registered yet?)");
        }
        return (DeferredItem<T>) (DeferredItem<?>) i;
    }

    /** All blocks of a family, in file order - the valid blocks of its block entity type. */
    public static Block[] blocksOf(String family) {
        return family(family).tiers().stream().filter(TierDef::hasBlock).map(d -> BLOCKS.get(d.id()).get()).toArray(Block[]::new);
    }
}
