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
 * Handles prices and coins. Item value is grams times the copper-per-gram of its material, rounded down per item.
 * Items without a {@link ResourceInfo} entry or whose material lacks a {@link SellValue} cannot be sold,
 * preventing buy or sell loops for coins, machines, and vanilla items.
 */
public final class Selling {
    private Selling() {}

    public static final int COPPER = 1, SILVER = 8, GOLD = 64;

    /** The resource entry of an item, or null. See {@link #grams} for per-stack weights. */
    @Nullable
    public static ResourceInfo resource(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getItemHolder().getData(ModEconomy.RESOURCE_WEIGHTS);
    }

    /**
     * Weight of a single item in grams, or 0 if it has none. Uses the weight component if present, otherwise the fixed grams from its {@link ResourceInfo} entry.
     */
    public static int grams(ItemStack stack) {
        ResourceInfo info = resource(stack);
        if (info == null) {
            return 0;
        }
        Integer weight = stack.get(com.holeydigsite.registry.ModDataComponents.WEIGHT.get());
        return weight != null ? weight : info.grams();
    }

    /** The price entry of a material, or null. */
    @Nullable
    public static SellValue price(HolderLookup.Provider registries, net.minecraft.resources.ResourceLocation material) {
        return registries.lookup(ModEconomy.SELL_VALUES)
                .flatMap(r -> r.get(ResourceKey.create(ModEconomy.SELL_VALUES, material)))
                .map(h -> h.value())
                .orElse(null);
    }

    /** Copper value of a single item, or 0 if unsellable. Smithing Anvil pieces are worth their materials plus a bonus. */
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

    /** Copper value of the entire stack. */
    public static long stackValue(ItemStack stack, HolderLookup.Provider registries) {
        return unitValue(stack, registries) * stack.getCount();
    }

    /** Splits an amount of copper into gold, silver, and copper quantities, largest first. */
    public static long[] split(long copper) {
        return new long[]{copper / GOLD, copper % GOLD / SILVER, copper % SILVER};
    }

    /** Returns coin stacks paying the specified copper, largest denominations first up to stack limits. */
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

    /** Formats copper into a readable coin string. */
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
