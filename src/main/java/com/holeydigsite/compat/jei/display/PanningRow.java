package com.holeydigsite.compat.jei.display;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.sluice.SluiceUpgrade;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record PanningRow(
        int clumpKind,
        ItemStack clumpStack,
        Component clumpName,
        List<DropEntry> baseDrops,
        List<DropEntry> seedDrops,
        List<DropEntry> bonusDrops,
        List<Component> upgradeNotes
) {
    public record DropEntry(
            ItemStack stack,
            int minCount,
            int maxCount,
            int weight,
            int totalWeight,
            double chanceFraction,
            String chanceString
    ) {}

    public static List<PanningRow> loadAll() {
        List<PanningRow> rows = new ArrayList<>();
        for (int kind = DirtTier.MIN; kind <= DirtTier.MAX; kind++) {
            rows.add(loadKind(kind));
        }
        return List.copyOf(rows);
    }

    public static PanningRow loadKind(int kind) {
        ItemStack clump = DirtTier.clumps(kind, 1);
        Component name = clump.getHoverName();

        String mainLootPath = switch (kind) {
            case DirtTier.DIRT -> "panning/dirt_t1";
            case 2 -> "panning/dirt_t2";
            case 3 -> "panning/dirt_t3";
            case DirtTier.GRAVEL -> "panning/gravel";
            case DirtTier.SAND -> "panning/sand";
            case DirtTier.COARSE_DIRT -> "panning/coarse_dirt";
            case DirtTier.ROOTED_DIRT -> "panning/rooted_dirt";
            case DirtTier.MOSS -> "panning/moss";
            default -> "panning/dirt_t1";
        };

        List<DropEntry> baseDrops = parseLootTable(mainLootPath);

        // Seed drops
        String seedPath = switch (kind) {
            case DirtTier.DIRT -> "panning/seeds_dirt_1";
            case 2 -> "panning/seeds_dirt_2";
            case 3 -> "panning/seeds_dirt_3";
            case DirtTier.GRAVEL -> "panning/seeds_gravel";
            case DirtTier.SAND -> "panning/seeds_sand";
            default -> "panning/seeds_dirt_1";
        };
        List<DropEntry> seedDrops = parseLootTable(seedPath);

        // Bonus drops (e.g. sand mesh bonus)
        List<DropEntry> bonusDrops = List.of();
        if (kind == DirtTier.SAND) {
            bonusDrops = parseLootTable("panning/sand_mesh_bonus");
        }

        List<Component> notes = new ArrayList<>();
        if (kind == DirtTier.GRAVEL) {
            notes.add(JeiText.t("panning_riffle", JeiText.percent(SluiceUpgrade.RIFFLE.extraChance())));
        } else if (kind == DirtTier.SAND) {
            notes.add(JeiText.t("panning_mesh", JeiText.percent(SluiceUpgrade.MESH.extraChance())));
        }
        notes.add(JeiText.t("panning_seed"));

        return new PanningRow(
                kind,
                clump,
                name,
                baseDrops,
                seedDrops,
                bonusDrops,
                List.copyOf(notes)
        );
    }

    private static List<DropEntry> parseLootTable(String path) {
        List<DropEntry> list = new ArrayList<>();
        try (InputStream in = PanningRow.class.getResourceAsStream("/data/holeydigsite/loot_table/" + path + ".json")) {
            if (in == null) return List.of();
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray pools = root.getAsJsonArray("pools");
            if (pools == null) return List.of();

            for (JsonElement p : pools) {
                JsonObject pool = p.getAsJsonObject();
                double poolChance = 1.0;
                if (pool.has("conditions")) {
                    for (JsonElement c : pool.getAsJsonArray("conditions")) {
                        JsonObject cond = c.getAsJsonObject();
                        if (cond.has("condition") && cond.get("condition").getAsString().endsWith("random_chance")
                                && cond.has("chance")) {
                            poolChance *= cond.get("chance").getAsDouble();
                        }
                    }
                }

                JsonArray entries = pool.getAsJsonArray("entries");
                if (entries == null) continue;

                int totalWeight = 0;
                for (JsonElement e : entries) {
                    JsonObject entry = e.getAsJsonObject();
                    totalWeight += entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                }

                for (JsonElement e : entries) {
                    JsonObject entry = e.getAsJsonObject();
                    if (!entry.has("name")) continue;
                    ResourceLocation itemId = ResourceLocation.parse(entry.get("name").getAsString());
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item == Items.AIR) continue;

                    int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                    int minCount = 1, maxCount = 1;
                    if (entry.has("functions")) {
                        for (JsonElement f : entry.getAsJsonArray("functions")) {
                            JsonObject func = f.getAsJsonObject();
                            if (func.has("function") && func.get("function").getAsString().endsWith("set_count")) {
                                JsonElement countEl = func.get("count");
                                if (countEl != null && countEl.isJsonObject()) {
                                    JsonObject countObj = countEl.getAsJsonObject();
                                    if (countObj.has("min")) minCount = countObj.get("min").getAsInt();
                                    if (countObj.has("max")) maxCount = countObj.get("max").getAsInt();
                                } else if (countEl != null && countEl.isJsonPrimitive()) {
                                    minCount = maxCount = countEl.getAsInt();
                                }
                            }
                        }
                    }

                    double frac = totalWeight > 0 ? (poolChance * weight / totalWeight) : 0;
                    String pct = formatPercent(frac);
                    ItemStack stack = new ItemStack(item, minCount);
                    list.add(new DropEntry(stack, minCount, maxCount, weight, totalWeight, frac, pct));
                }
            }
        } catch (Exception e) {
            HoleyDigsite.LOGGER.warn("Failed to parse loot table {}: {}", path, e.toString());
        }
        return List.copyOf(list);
    }

    private static String formatPercent(double frac) {
        double pct = frac * 100.0;
        if (pct >= 10.0) {
            return String.format(Locale.ROOT, "%.0f%%", pct);
        } else if (pct >= 1.0) {
            return String.format(Locale.ROOT, "%.1f%%", pct);
        } else {
            return String.format(Locale.ROOT, "%.2f%%", pct);
        }
    }
}
