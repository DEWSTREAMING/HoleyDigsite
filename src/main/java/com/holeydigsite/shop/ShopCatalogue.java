package com.holeydigsite.shop;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.plot.PlotData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The data-driven shop catalogue using the datapack registry {@code holeydigsite:shop_items} registered in
 * {@link com.holeydigsite.economy.ModEconomy}. A new shop item is just a JSON file with no code needed.
 */
public final class ShopCatalogue {
    private ShopCatalogue() {}

    public static final ResourceKey<Registry<ShopItem>> SHOP_ITEMS = ResourceKey.createRegistryKey(HoleyDigsite.id("shop_items"));

    /** The catalogue entry selling the item, if any. */
    public static Optional<ShopItem> find(HolderLookup.Provider registries, Item item) {
        if (registries == null || item == null) {
            return Optional.empty();
        }
        return registries.lookup(SHOP_ITEMS)
                .flatMap(r -> r.listElements().map(Holder::value).filter(e -> e.item() == item).findFirst());
    }

    /** The catalogue entry a blueprint stack is for, if it is a blueprint of a catalogued item. */
    public static Optional<ShopItem> forBlueprint(HolderLookup.Provider registries, ItemStack stack) {
        Item target = BlueprintItem.target(stack);
        return target == null ? Optional.empty() : find(registries, target);
    }

    /** Whether a buy pad at pos takes this blueprint being catalogued and exclusive items only inside their plot. */
    public static boolean buyableAt(Level level, BlockPos pos, ItemStack blueprint) {
        Optional<ShopItem> entry = forBlueprint(level.registryAccess(), blueprint);
        if (entry.isEmpty()) {
            return false;
        }
        if (entry.get().site().isEmpty()) {
            return true;
        }
        return level instanceof ServerLevel sl && entry.get().availableAt(PlotData.get(sl).plotIdAt(pos));
    }

    /** True if the stack is a blueprint of a catalogued item exclusive to some plot. */
    public static boolean isExclusive(Level level, ItemStack blueprint) {
        return forBlueprint(level.registryAccess(), blueprint).map(e -> e.site().isPresent()).orElse(false);
    }

    /** True if the item is catalogued as a ledger item where its blueprint only comes from a ledger reward. */
    public static boolean isLedgerItem(HolderLookup.Provider registries, Item item) {
        return find(registries, item).map(ShopItem::ledger).orElse(false);
    }

    /** Every catalogue entry in no particular order. */
    public static List<ShopItem> all(HolderLookup.Provider registries) {
        return registries.lookup(SHOP_ITEMS).map(r -> r.listElements().map(Holder::value).toList()).orElse(List.of());
    }

    /** Total price of the blueprints in stacks where each blueprint is one purchase and unknown blueprints cost zero. */
    public static long totalCost(HolderLookup.Provider registries, List<ItemStack> stacks) {
        long total = 0;
        for (ItemStack s : stacks) {
            var custom = com.holeydigsite.shop.custom.CustomBlueprint.of(s);
            total += custom != null ? custom.price() * s.getCount() : forBlueprint(registries, s).map(e -> e.price() * s.getCount()).orElse(0L);
        }
        return total;
    }
}
