package com.holeydigsite.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * One entry of the shop catalogue, the {@code holeydigsite:shop_items} datapack registry
 * ({@code data/<namespace>/holeydigsite/shop_items/<name>.json}). Synced to clients for tooltips, display labels and
 * the Buy Pad's floating total.
 *
 * @param item  what one blueprint buys
 * @param count how many of it one blueprint buys (supported by the format, but every shipped entry is 1: one blueprint = one item)
 * @param price price of one blueprint's purchase, in copper (1 silver = 8, 1 gold = 64)
 * @param site  Phase 16: plot id (e.g. {@code deep_dig}) this item is exclusive to - only Buy Pads inside that plot take its
 *              blueprints; empty = sold everywhere
 * @param ledger Phase 21b: a Prospector's Ledger item - its Blueprint only comes from a Ledger reward (Shop Displays never hand
 *               it out); a Buy Pad then sells it like any other blueprint
 */
public record ShopItem(Item item, int count, long price, Optional<String> site, boolean ledger) {
    public static final Codec<ShopItem> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ShopItem::item),
            Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(ShopItem::count),
            Codec.LONG.fieldOf("price").forGetter(ShopItem::price),
            Codec.STRING.optionalFieldOf("site").forGetter(ShopItem::site),
            Codec.BOOL.optionalFieldOf("ledger", false).forGetter(ShopItem::ledger)
    ).apply(i, ShopItem::new));

    /** True if a Buy Pad in plot {@code plotId} (null = not in a plot) may sell this item. */
    public boolean availableAt(@org.jetbrains.annotations.Nullable String plotId) {
        return site.isEmpty() || site.get().equals(plotId);
    }

    /** The items one blueprint delivers. */
    public ItemStack result() {
        return new ItemStack(item, count);
    }
}
