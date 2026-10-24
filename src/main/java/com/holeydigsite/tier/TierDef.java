package com.holeydigsite.tier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.holeydigsite.HoleyDigsite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One tier of a machine or tool family, read from {@code data/holeydigsite/tiers/<family>.json}. The same file feeds
 * the Java registration (this class), the asset generator ({@code tools/make_tiers.py}), and the generic GameTests.
 *
 * <p>Fields include {@code id} for the registry name, {@code name} for the English display name, {@code kind} for which block or item class the family
 * builds (see {@link TierKinds}), {@code mark} for I, II, III, or IV drawn on the textures, {@code block} and {@code item} for properties,
 * {@code stats} for numbers the block or block entity reads, {@code tab} for creative-tab slots, and {@code obtain}
 * for shop prices and Crafting Forge recipes. Registries cannot change at runtime, so this is read once from the bundled jar.</p>
 */
public final class TierDef {
    /** One creative-tab slot with sort {@code order}, {@code stack} kind ("item" or "full_pan"), and an optional dirt tier. */
    public record TabSlot(int order, String stack, int dirtTier) {}

    /** One Crafting Forge recipe containing the file name, sort order, category, variant, grams per metal, parts, and result count. */
    public record Recipe(String file, int order, String category, String variant, java.util.LinkedHashMap<String, Integer> metals,
                         List<Part> parts, int count) {
        public record Part(String item, int count) {}
    }

    /** The shop catalogue entry of a tier. */
    public record Shop(int price, int count) {}

    private final String family;
    private final JsonObject json;

    TierDef(String family, JsonObject json) {
        this.family = family;
        this.json = json;
    }

    public String family() { return family; }
    public String id() { return json.get("id").getAsString(); }
    public String name() { return json.get("name").getAsString(); }
    public String kind() { return json.has("kind") ? json.get("kind").getAsString() : "default"; }
    public String mark() { return json.has("mark") ? json.get("mark").getAsString() : "I"; }
    public JsonObject json() { return json; }
    public ResourceLocation location() { return HoleyDigsite.id(id()); }

    /** Indicates whether this tier registers a block, which is false for items-only families like the brush. */
    public boolean hasBlock() { return json.has("block"); }

    // ---- stats ----

    public int stat(String key, int fallback) {
        JsonObject s = json.has("stats") ? json.getAsJsonObject("stats") : null;
        return s != null && s.has(key) ? s.get(key).getAsInt() : fallback;
    }

    public int stat(String key) {
        JsonObject s = json.has("stats") ? json.getAsJsonObject("stats") : null;
        if (s == null || !s.has(key)) {
            throw new IllegalStateException("tier " + id() + " has no stat '" + key + "'");
        }
        return s.get(key).getAsInt();
    }

    public int[] statArray(String key, int[] fallback) {
        JsonObject s = json.has("stats") ? json.getAsJsonObject("stats") : null;
        if (s == null || !s.has(key)) {
            return fallback;
        }
        JsonArray a = s.getAsJsonArray(key);
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsInt();
        }
        return out;
    }

    // ---- creative tab ----

    public List<TabSlot> tabSlots() {
        List<TabSlot> out = new ArrayList<>();
        if (json.has("tab")) {
            for (JsonElement e : json.getAsJsonArray("tab")) {
                JsonObject o = e.getAsJsonObject();
                out.add(new TabSlot(o.get("order").getAsInt(), o.has("stack") ? o.get("stack").getAsString() : "item",
                        o.has("dirt_tier") ? o.get("dirt_tier").getAsInt() : 1));
            }
        }
        return out;
    }

    // ---- how it is obtained ----

    public Shop shop() {
        JsonObject o = json.has("obtain") ? json.getAsJsonObject("obtain") : null;
        if (o == null || !o.has("shop")) {
            return null;
        }
        JsonObject s = o.getAsJsonObject("shop");
        return new Shop(s.get("price").getAsInt(), s.has("count") ? s.get("count").getAsInt() : 1);
    }

    public List<Recipe> recipes() {
        List<Recipe> out = new ArrayList<>();
        JsonObject o = json.has("obtain") ? json.getAsJsonObject("obtain") : null;
        if (o == null || !o.has("recipes")) {
            return out;
        }
        for (JsonElement e : o.getAsJsonArray("recipes")) {
            JsonObject r = e.getAsJsonObject();
            java.util.LinkedHashMap<String, Integer> metals = new java.util.LinkedHashMap<>();
            for (var m : r.getAsJsonObject("metals").entrySet()) {
                metals.put(m.getKey(), m.getValue().getAsInt());
            }
            List<Recipe.Part> parts = new ArrayList<>();
            for (JsonElement p : r.getAsJsonArray("parts")) {
                JsonObject po = p.getAsJsonObject();
                parts.add(new Recipe.Part(po.get("item").getAsString(), po.has("count") ? po.get("count").getAsInt() : 1));
            }
            out.add(new Recipe(r.has("file") ? r.get("file").getAsString() : id(), r.get("order").getAsInt(),
                    r.has("category") ? r.get("category").getAsString() : "other",
                    r.has("variant") ? r.get("variant").getAsString() : "",
                    metals, parts,
                    r.has("count") ? r.get("count").getAsInt() : 1));
        }
        return out;
    }

    // ---- block / item properties ----

    /**
     * Builds the block properties from the {@code block} section. {@code lightWhen} is the family's active state test
     * (such as a lit forge or filled crucible), and the {@code light} level applies while it holds.
     */
    public BlockBehaviour.Properties blockProperties(Predicate<BlockState> lightWhen) {
        JsonObject b = json.getAsJsonObject("block");
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of();
        p.mapColor(constant(MapColor.class, b.get("map_color").getAsString()));
        float strength = b.get("strength").getAsFloat();
        p.strength(strength, b.has("resistance") ? b.get("resistance").getAsFloat() : strength);
        p.sound(constant(SoundType.class, b.get("sound").getAsString()));
        if (b.has("no_occlusion") && b.get("no_occlusion").getAsBoolean()) {
            p.noOcclusion();
        }
        if (b.has("push_reaction")) {
            p.pushReaction(PushReaction.valueOf(b.get("push_reaction").getAsString()));
        }
        int light = b.has("light") ? b.get("light").getAsInt() : 0;
        if (light > 0 && lightWhen != null) {
            p.lightLevel(s -> lightWhen.test(s) ? light : 0);
        }
        return p;
    }

    public Item.Properties itemProperties() {
        Item.Properties p = new Item.Properties();
        JsonObject i = json.has("item") ? json.getAsJsonObject("item") : null;
        if (i != null) {
            if (i.has("stacks_to")) {
                p.stacksTo(i.get("stacks_to").getAsInt());
            }
            if (i.has("rarity")) {
                p.rarity(Rarity.valueOf(i.get("rarity").getAsString()));
            }
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private static <T> T constant(Class<T> type, String name) {
        try {
            return (T) type.getField(name).get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("unknown " + type.getSimpleName() + " constant '" + name + "' in a tier file", e);
        }
    }
}
