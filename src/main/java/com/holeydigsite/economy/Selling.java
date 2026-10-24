package com.holeydigsite.economy;

import com.holeydigsite.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Prices and coins. Value of an item = grams x copper-per-gram of its material (ingot price for ingots), rounded down
 * per item. Items without a {@link ResourceInfo} entry, or whose material has no {@link SellValue}, can't be sold -
 * that covers coins, machines, tools and everything vanilla, so there are no buy/sell loops.
 */
public final class Selling {
    private Selling() {}

    public static final int COPPER = 1, SILVER = 8, GOLD = 64;

    /** The resource entry of an item (material, form, meltable), or null. Per-stack weights: see {@link #grams}. */
    @Nullable
    public static ResourceInfo resource(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getItemHolder().getData(ModEconomy.RESOURCE_WEIGHTS);
    }

    /**
     * Weight of ONE item in grams, 0 if it has none: the {@code holeydigsite:weight} component if the stack has one (ingots,
     * Phase 9), else the fixed grams of its {@link ResourceInfo} entry (chunks, gems).
     */
    public static int grams(ItemStack stack) {
        ResourceInfo info = resource(stack);
        if (info == null) {
            return 0;
        }
        Integer weight = stack.get(com.holeydigsite.registry.ModDataComponents.WEIGHT.get());
        return weight != null ? weight : info.grams();
    }

    /** The price entry of a material (ingot / cut / uncut prices per gram), or null. */
    @Nullable
    public static SellValue price(HolderLookup.Provider registries, net.minecraft.resources.ResourceLocation material) {
        return registries.lookup(ModEconomy.SELL_VALUES)
                .flatMap(r -> r.get(ResourceKey.create(ModEconomy.SELL_VALUES, material)))
                .map(h -> h.value())
                .orElse(null);
    }

    /** Copper value of ONE item, or 0 if it can't be sold. Phase 19: pieces made on the Smithing Anvil are worth their materials plus a bonus. */
    public static long unitValue(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.has(com.holeydigsite.registry.ModDataComponents.SMITH.get())) {
            return com.holeydigsite.smith.SmithPieces.value(stack, registries);
        }
        ResourceInfo info = resource(stack);
        if (info == null || stack.is(ModItems.COPPER_COIN.get()) || stack.is(ModItems.SILVER_COIN.get())
                || stack.is(ModItems.GOLD_COIN.get())) {
            return 0;
        }
        SellValue price = price(registries, info.material());
        if (price == null) {
            return 0;
        }
        return (long) Math.floor(grams(stack) * price.perGram(info.form()) + 1e-9);
    }

    public static boolean isSellable(ItemStack stack, HolderLookup.Provider registries) {
        return unitValue(stack, registries) > 0;
    }

    /** Copper value of the whole stack. */
    public static long stackValue(ItemStack stack, HolderLookup.Provider registries) {
        return unitValue(stack, registries) * stack.getCount();
    }

    /** {gold, silver, copper} for an amount of copper, largest coins first. */
    public static long[] split(long copper) {
        return new long[]{copper / GOLD, copper % GOLD / SILVER, copper % SILVER};
    }

    /** Coin stacks paying {@code copper}, largest denominations first (stacks of at most 64). */
    public static List<ItemStack> coins(long copper) {
        long[] parts = split(copper);
        List<ItemStack> out = new ArrayList<>();
        add(out, new ItemStack(ModItems.GOLD_COIN.get()), parts[0]);
        add(out, new ItemStack(ModItems.SILVER_COIN.get()), parts[1]);
        add(out, new ItemStack(ModItems.COPPER_COIN.get()), parts[2]);
        return out;
    }

    private static void add(List<ItemStack> out, ItemStack coin, long n) {
        while (n > 0) {
            int c = (int) Math.min(n, coin.getMaxStackSize());
            out.add(coin.copyWithCount(c));
            n -= c;
        }
    }

    /** "1 gold 2 silver 4 copper" (zero parts left out; "0 copper" for nothing). */
    public static MutableComponent format(long copper) {
        long[] p = split(copper);
        List<Component> parts = new ArrayList<>();
        if (p[0] > 0) parts.add(Component.translatable("coin.holeydigsite.gold", p[0]));
        if (p[1] > 0) parts.add(Component.translatable("coin.holeydigsite.silver", p[1]));
        if (p[2] > 0 || parts.isEmpty()) parts.add(Component.translatable("coin.holeydigsite.copper", p[2]));
        MutableComponent c = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) c.append(" ");
            c.append(parts.get(i));
        }
        return c;
    }
}
