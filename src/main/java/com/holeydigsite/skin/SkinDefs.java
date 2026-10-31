package com.holeydigsite.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.sluice.SluiceBoxBlock;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Data-driven skin definitions loaded from {@code data/holeydigsite/skins/*.json}.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class SkinDefs {
    private SkinDefs() {}

    public static final String DEFAULT_ID = "default";
    public static final String DRILL = "mining_drill";
    public static final String SLUICE = "sluice_box";
    /** Every machine family a skin can fit, in display order. */
    public static final List<String> FAMILIES = List.of(DRILL, SLUICE);

    public static boolean isFamily(@Nullable String family) {
        return family != null && FAMILIES.contains(family);
    }

    /** "Drill" / "Sluice", for kit names. */
    public static Component familyName(String family) {
        return Component.translatable("skin_family.holeydigsite." + family);
    }

    /** Families a skin fits; an unknown skin counts as fitting all of them so nothing made from it is stranded. */
    public static List<String> familiesOf(String skinId) {
        SkinDef def = skin(skinId);
        return def == null || def.families().isEmpty() ? FAMILIES : def.families();
    }

    public record SkinDef(
            String id,
            String nameKey,
            String rarity,
            List<String> families
    ) {
        public ChatFormatting rarityFormatting() {
            return switch (rarity.toLowerCase(Locale.ROOT)) {
                case "rare" -> ChatFormatting.BLUE;
                case "epic" -> ChatFormatting.DARK_PURPLE;
                case "legendary" -> ChatFormatting.GOLD;
                default -> ChatFormatting.WHITE;
            };
        }

        public int rarityColor() {
            return switch (rarity.toLowerCase(Locale.ROOT)) {
                case "rare" -> 0xFF5555FF;
                case "epic" -> 0xFFAA00AA;
                case "legendary" -> 0xFFFFAA00;
                default -> 0xFFFFFFFF;
            };
        }

        public boolean appliesTo(String family) {
            return families.contains(family);
        }

        public Component displayName() {
            return Component.translatable(nameKey).withStyle(rarityFormatting());
        }
    }

    private static volatile Map<String, SkinDef> SKINS = Map.of();

    private static final String[] BUILTIN_SKINS = {
            "rustic", "rustic_weathered", "brass", "obsidian", "frosted", "outback", "outback_dusty", "outback_ochre",
            "opal_calm", "opal", "opal_boulder", "black_opal"
    };

    static {
        loadBuiltinFromClasspath();
    }

    public static Map<String, SkinDef> skins() {
        return SKINS;
    }

    @Nullable
    public static SkinDef skin(String id) {
        if (id == null) return null;
        return SKINS.get(id);
    }

    public static boolean hasSkin(String id) {
        return id != null && SKINS.containsKey(id);
    }

    public static String nameKey(String id) {
        SkinDef def = skin(id);
        return def != null ? def.nameKey() : "skin.holeydigsite." + id;
    }

    public static Component displayName(String id) {
        if (DEFAULT_ID.equals(id) || id == null || id.isEmpty()) {
            return Component.translatable("skin.holeydigsite.default").withStyle(ChatFormatting.WHITE);
        }
        SkinDef def = skin(id);
        if (def != null) {
            return def.displayName();
        }
        return Component.literal(id);
    }

    @Nullable
    public static String getMachineFamily(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof BlockItem bi) {
            if (bi.getBlock() instanceof MiningDrillBlock) {
                return DRILL;
            }
            if (bi.getBlock() instanceof SluiceBoxBlock) {
                return SLUICE;
            }
        }
        return null;
    }

    public static List<SkinDef> skinsForFamily(String family) {
        List<SkinDef> list = new ArrayList<>();
        for (SkinDef def : SKINS.values()) {
            if (def.appliesTo(family)) {
                list.add(def);
            }
        }
        return list;
    }

    private static void loadBuiltinFromClasspath() {
        Map<String, SkinDef> map = new LinkedHashMap<>();
        for (String id : BUILTIN_SKINS) {
            String path = "/data/holeydigsite/skins/" + id + ".json";
            try (InputStream in = SkinDefs.class.getResourceAsStream(path)) {
                if (in != null) {
                    JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    map.put(id, parseSkin(id, obj));
                }
            } catch (Exception ignored) {}
        }
        SKINS = Collections.unmodifiableMap(map);
    }

    private static SkinDef parseSkin(String id, JsonObject obj) {
        String rarity = obj.has("rarity") ? obj.get("rarity").getAsString() : "common";
        String nameKey = obj.has("name") ? obj.get("name").getAsString() : "skin.holeydigsite." + id;
        List<String> families = new ArrayList<>();
        if (obj.has("families")) {
            for (JsonElement e : obj.getAsJsonArray("families")) {
                families.add(e.getAsString());
            }
        }
        return new SkinDef(id, nameKey, rarity, List.copyOf(families));
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<String, SkinDef>> {
        @Override
        protected Map<String, SkinDef> prepare(ResourceManager rm, ProfilerFiller profiler) {
            Map<String, SkinDef> map = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : rm.listResources("skins", loc -> loc.getNamespace().equals(HoleyDigsite.MODID) && loc.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation loc = entry.getKey();
                String path = loc.getPath();
                String id = path.substring("skins/".length(), path.length() - ".json".length());
                try (InputStream in = entry.getValue().open()) {
                    JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    map.put(id, parseSkin(id, obj));
                } catch (Exception ignored) {}
            }
            return map;
        }

        @Override
        protected void apply(Map<String, SkinDef> result, ResourceManager rm, ProfilerFiller profiler) {
            if (!result.isEmpty()) {
                SKINS = Collections.unmodifiableMap(result);
            }
        }
    }
}
