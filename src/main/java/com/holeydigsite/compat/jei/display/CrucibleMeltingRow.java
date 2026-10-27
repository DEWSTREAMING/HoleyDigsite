package com.holeydigsite.compat.jei.display;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.economy.ModEconomy;
import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.forge.Metals;
import com.holeydigsite.tier.Tiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CrucibleMeltingRow(
        ItemStack input,
        ResourceLocation metal,
        int grams,
        Component metalName,
        ItemStack outputIngot,
        Component note
) {
    public static Component crucibleNote() {
        int heatRatio = Math.max(1, Tiers.stat("hot_forge", "heat") / Math.max(1, Tiers.stat("forge", "heat")));
        return JeiText.t("crucible_note",
                JeiText.num(20.0 / Tiers.stat("crucible", "ticks_per_gram")), Tiers.stat("crucible", "capacity"),
                JeiText.num(20.0 / Tiers.stat("large_crucible", "ticks_per_gram")), Tiers.stat("large_crucible", "capacity"),
                heatRatio);
    }

    public static List<CrucibleMeltingRow> loadAll() {
        Map<Item, ResourceInfo> items = new LinkedHashMap<>();
        try {
            Map<ResourceKey<Item>, ResourceInfo> map = BuiltInRegistries.ITEM.getDataMap(ModEconomy.RESOURCE_WEIGHTS);
            if (map != null && !map.isEmpty()) {
                for (Map.Entry<ResourceKey<Item>, ResourceInfo> e : map.entrySet()) {
                    Item item = BuiltInRegistries.ITEM.get(e.getKey());
                    if (item != Items.AIR && e.getValue().meltable() && !e.getValue().isCrop()) {
                        items.put(item, e.getValue());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (items.isEmpty()) {
            loadFromBuiltinJson(items);
        }

        List<CrucibleMeltingRow> rows = new ArrayList<>();
        Component note = crucibleNote();
        for (Map.Entry<Item, ResourceInfo> e : items.entrySet()) {
            Item item = e.getKey();
            ResourceInfo info = e.getValue();
            if (!info.meltable() || info.isCrop()) {
                continue;
            }
            int grams = info.grams();
            ItemStack input = new ItemStack(item);
            if (info.isIngot()) {
                input = Metals.ingot(info.material(), grams);
            }
            ItemStack outputIngot = Metals.ingot(info.material(), grams);
            rows.add(new CrucibleMeltingRow(
                    input,
                    info.material(),
                    grams,
                    Metals.name(info.material()),
                    outputIngot,
                    note
            ));
        }

        rows.sort(Comparator.comparing((CrucibleMeltingRow r) -> r.metal().toString())
                .thenComparingInt(CrucibleMeltingRow::grams));

        return List.copyOf(rows);
    }

    private static void loadFromBuiltinJson(Map<Item, ResourceInfo> out) {
        try (InputStream in = CrucibleMeltingRow.class.getResourceAsStream("/data/holeydigsite/data_maps/item/resource_weights.json")) {
            if (in == null) return;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject values = obj.getAsJsonObject("values");
            if (values == null) return;
            for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
                ResourceLocation itemId = ResourceLocation.parse(entry.getKey());
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item == Items.AIR) continue;
                JsonObject val = entry.getValue().getAsJsonObject();
                ResourceLocation material = ResourceLocation.parse(val.get("material").getAsString());
                int grams = val.get("grams").getAsInt();
                String form = val.has("form") ? val.get("form").getAsString() : ResourceInfo.RAW;
                boolean meltable = !val.has("meltable") || val.get("meltable").getAsBoolean();
                out.put(item, new ResourceInfo(material, grams, form, meltable));
            }
        } catch (Exception e) {
            HoleyDigsite.LOGGER.warn("Failed to read builtin resource_weights.json: {}", e.toString());
        }
    }
}
