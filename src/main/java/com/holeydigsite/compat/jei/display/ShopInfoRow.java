package com.holeydigsite.compat.jei.display;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.shop.ShopItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record ShopInfoRow(
        Item item,
        int count,
        long price,
        Component location,
        Component formattedPrice,
        boolean ledger,
        Component infoLine
) {
    public static List<ShopInfoRow> loadAll(@Nullable HolderLookup.Provider registries) {
        List<ShopItem> items = ShopCatalogue.all(registries);
        if (items.isEmpty()) {
            items = loadBuiltin();
        }

        List<ShopInfoRow> rows = new ArrayList<>();
        for (ShopItem si : items) {
            Component loc = locationFor(si.site());
            MutableComponent priceText = Selling.format(si.price());
            Component line;
            if (si.ledger()) {
                line = JeiText.t("shop_line_ledger", loc, priceText);
            } else {
                line = JeiText.t("shop_line", loc, priceText);
            }

            rows.add(new ShopInfoRow(
                    si.item(),
                    si.count(),
                    si.price(),
                    loc,
                    priceText,
                    si.ledger(),
                    line
            ));
        }

        rows.sort(Comparator.comparing((ShopInfoRow r) -> r.location().getString())
                .thenComparingLong(ShopInfoRow::price));

        return List.copyOf(rows);
    }

    public static final String LOC_TRADING_POST = "location_trading_post";
    public static final List<String> KNOWN_SITES = List.of("deep_dig", "gravel_flats", "sand_dunes");

    public static Component locationFor(Optional<String> site) {
        if (site.isEmpty()) {
            return JeiText.t(LOC_TRADING_POST);
        }
        if (KNOWN_SITES.contains(site.get())) {
            return JeiText.t("location_" + site.get());
        }
        return JeiText.t("location_other", site.get());
    }

    private static final String[] BUILTIN_FILES = {
            "belt_splitter", "blacksmith_hammer", "bread", "brush", "buy_pad",
            "claim_flag", "clump_belt", "crafting_forge", "crucible", "dirt",
            "dirt_clump", "down_incline_belt", "drop_chute", "filter_belt", "fine_mesh_screen",
            "forge", "gem_cutter", "gold_crate", "gravel_clump", "gravel_head",
            "incline_belt", "iron_crate", "land_deed_deep_dig", "land_deed_gravel_flats", "land_deed_sand_dunes",
            "lava_bucket", "lava_intake", "lava_pipe", "lucky_lantern", "miners_lantern",
            "mining_drill", "mold", "money_pan", "ore_cart", "pan",
            "prospectors_ledger", "prospectors_pick", "riffle_insert", "river_stone_lamp", "sandstone_lamp",
            "sand_clump", "sand_head", "seed_screen", "sell_pad", "shop_button",
            "skin_bench", "sluice_box", "smithing_anvil", "water_intake", "water_pipe",
            "wooden_crate", "wooden_shovel"
    };

    private static List<ShopItem> loadBuiltin() {
        List<ShopItem> list = new ArrayList<>();
        for (String file : BUILTIN_FILES) {
            try (InputStream in = ShopInfoRow.class.getResourceAsStream("/data/holeydigsite/holeydigsite/shop_items/" + file + ".json")) {
                if (in == null) continue;
                JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                ResourceLocation itemId = ResourceLocation.parse(obj.get("item").getAsString());
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item == Items.AIR) continue;
                int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
                long price = obj.get("price").getAsLong();
                Optional<String> site = obj.has("site") ? Optional.of(obj.get("site").getAsString()) : Optional.empty();
                boolean ledger = obj.has("ledger") && obj.get("ledger").getAsBoolean();
                list.add(new ShopItem(item, count, price, site, ledger));
            } catch (Exception e) {
                HoleyDigsite.LOGGER.warn("Failed to load builtin shop item {}: {}", file, e.toString());
            }
        }
        return list;
    }
}
