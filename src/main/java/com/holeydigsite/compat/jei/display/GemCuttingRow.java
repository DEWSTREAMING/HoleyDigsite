package com.holeydigsite.compat.jei.display;

import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.SellValue;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.transport.GemCutting;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** @param uncutPercent what the uncut gem sells for, in percent of the cut gem */
public record GemCuttingRow(
        ItemStack uncut,
        ItemStack cut,
        int uncutPercent,
        Component note
) {
    private static final List<DeferredItem<Item>> UNCUT_GEMS = List.of(
            ModItems.UNCUT_RUBY_SMALL,
            ModItems.UNCUT_RUBY_MEDIUM,
            ModItems.UNCUT_RUBY_LARGE,
            ModItems.UNCUT_SAPPHIRE_SMALL,
            ModItems.UNCUT_SAPPHIRE_MEDIUM,
            ModItems.UNCUT_SAPPHIRE_LARGE,
            ModItems.UNCUT_TOPAZ_SMALL,
            ModItems.UNCUT_TOPAZ_MEDIUM,
            ModItems.UNCUT_TOPAZ_LARGE,
            ModItems.UNCUT_POTCH,
            ModItems.UNCUT_BOULDER_OPAL,
            ModItems.UNCUT_SAPPHIRE_PARTI,
            ModItems.UNCUT_BLACK_OPAL,
            ModItems.UNCUT_FIRE_OPAL,
            ModItems.UNCUT_PINK_DIAMOND
    );

    public static List<GemCuttingRow> loadAll(@Nullable HolderLookup.Provider registries) {
        List<GemCuttingRow> rows = new ArrayList<>();
        for (DeferredItem<Item> defer : UNCUT_GEMS) {
            ItemStack uncut = new ItemStack(defer.get());
            ItemStack cut = GemCutting.cut(uncut);
            int pct = (int) Math.round(uncutRatio(uncut, registries) * 100);
            rows.add(new GemCuttingRow(uncut, cut, pct, JeiText.t("gem_note", pct)));
        }
        return List.copyOf(rows);
    }

    /** Uncut sell value over cut sell value, from the sell values of the gem's material. */
    private static double uncutRatio(ItemStack uncut, @Nullable HolderLookup.Provider registries) {
        ResourceInfo info = Selling.resource(uncut);
        if (info == null) return 1.0;
        if (com.holeydigsite.event.EventDefs.isEventGem(uncut)) {
            return com.holeydigsite.event.EventDefs.uncutRatio(info.material());
        }
        SellValue value = registries == null ? null : Selling.price(registries, info.material());
        if (value == null) {
            value = fromJar(info.material());
        }
        return value == null ? 1.0 : value.uncutRatio();
    }

    @Nullable
    private static SellValue fromJar(ResourceLocation material) {
        String path = "/data/" + material.getNamespace() + "/holeydigsite/sell_values/" + material.getPath() + ".json";
        try (InputStream in = GemCuttingRow.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return SellValue.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .result().orElse(null);
        } catch (Exception e) {
            HoleyDigsite.LOGGER.warn("Failed to read sell values for {}: {}", material, e.toString());
            return null;
        }
    }
}
