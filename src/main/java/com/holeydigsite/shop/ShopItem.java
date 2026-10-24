package com.holeydigsite.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * One entry of the shop catalogue, the {@code holeydigsite:shop_items} datapack registry
 * ({@code data/<namespace>/holeydigsite/shop_items/<name>.json}), synced to clients for tooltips, display labels, and
 * the Buy Pad floating total.
 *
 * @param item  what one blueprint buys
 * @param count how many items one blueprint buys
 * @param price price of one blueprint purchase in copper
 * @param site  plot ID this item is exclusive to, where empty means sold everywhere
 * @param ledger whether this is a Prospector Ledger item whose blueprint only comes from a ledger reward
 */
public record ShopItem(Item item, int count, long price, Optional<String> site, boolean ledger) {
    public static final Codec<ShopItem> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ShopItem::item),
            Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(ShopItem::count),
            Codec.LONG.fieldOf("price").forGetter(ShopItem::price),
            Codec.STRING.optionalFieldOf("site").forGetter(ShopItem::site),
            Codec.BOOL.optionalFieldOf("ledger", false).forGetter(ShopItem::ledger)
    ).apply(i, ShopItem::new));

    /** Checks if a Buy Pad in the given plot may sell this item. */
    public boolean availableAt(@org.jetbrains.annotations.Nullable String plotId) {
        return site.isEmpty() || site.get().equals(plotId);
    }

    /** The items delivered by one blueprint. */
    public ItemStack result() {
        return new ItemStack(item, count);
    }
}
