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
    public static final int PLATINUM = 512, CROWN = 4096, BAR = 32768;

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
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof com.holeydigsite.crate.CrateBlock
                && com.holeydigsite.crate.CrateBlock.isLocked(stack)) {
            return com.holeydigsite.crate.CrateDefs.rules().lockedSellCopper();
        }
        ResourceInfo info = resource(stack);
        if (info == null || stack.getItem() instanceof com.holeydigsite.item.CoinItem) {
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

    /** Splits copper into bars, crowns, platinum, gold, silver and copper, largest first. */
    public static long[] splitAll(long copper) {
        return new long[]{copper / BAR, copper % BAR / CROWN, copper % CROWN / PLATINUM, copper % PLATINUM / GOLD,
                copper % GOLD / SILVER, copper % SILVER};
    }

    /** Returns coin stacks paying the specified copper, largest denominations first up to stack limits. */
    public static List<ItemStack> coins(long copper) {
        long[] parts = splitAll(copper);
        List<ItemStack> out = new ArrayList<>();
        add(out, new ItemStack(ModItems.MINT_BAR.get()), parts[0]);
        add(out, new ItemStack(ModItems.RUBY_CROWN.get()), parts[1]);
        add(out, new ItemStack(ModItems.PLATINUM_COIN.get()), parts[2]);
        add(out, new ItemStack(ModItems.GOLD_COIN.get()), parts[3]);
        add(out, new ItemStack(ModItems.SILVER_COIN.get()), parts[4]);
        add(out, new ItemStack(ModItems.COPPER_COIN.get()), parts[5]);
        return out;
    }

    private static void add(List<ItemStack> out, ItemStack coin, long n) {
        while (n > 0) {
            int c = (int) Math.min(n, coin.getMaxStackSize());
            out.add(coin.copyWithCount(c));
            n -= c;
        }
    }

    /** Formats copper into a readable coin string, biggest coins first, with the gold total when it has platinum or more. */
    public static MutableComponent format(long copper) {
        return format(copper, true);
    }

    /**
     * Like {@link #format(long)} but the gold total is left off when the English text would be longer than {@code maxChars}
     * (counted from the numbers, so it works on a server without the lang file).
     */
    public static MutableComponent format(long copper, int maxChars) {
        return format(copper, textLength(copper, true) <= maxChars);
    }

    /** Length of the English text of {@link #format(long)}, with or without the gold total. */
    public static int textLength(long copper, boolean total) {
        long[] p = splitAll(copper);
        String[] words = {p[0] == 1 ? "bar" : "bars", p[1] == 1 ? "crown" : "crowns", "platinum", "gold", "silver", "copper"};
        int len = 0, n = 0;
        for (int i = 0; i < 6; i++) {
            if (p[i] > 0 || (i == 5 && n == 0)) {
                len += (n++ > 0 ? 1 : 0) + Long.toString(p[i]).length() + 1 + words[i].length();
            }
        }
        if (total && copper >= PLATINUM) {
            len += 1 + String.format(java.util.Locale.ROOT, "%,d", copper / GOLD).length() + 7;
        }
        return len;
    }

    /** The coin pieces of {@code copper} ("3 bars", "6 crowns", ...), biggest first, without the gold total. */
    public static List<Component> parts(long copper) {
        long[] p = splitAll(copper);
        List<Component> parts = new ArrayList<>();
        if (p[0] > 0) parts.add(Component.translatable(p[0] == 1 ? "coin.holeydigsite.bar" : "coin.holeydigsite.bars", p[0]));
        if (p[1] > 0) parts.add(Component.translatable(p[1] == 1 ? "coin.holeydigsite.crown" : "coin.holeydigsite.crowns", p[1]));
        if (p[2] > 0) parts.add(Component.translatable("coin.holeydigsite.platinum", p[2]));
        if (p[3] > 0) parts.add(Component.translatable("coin.holeydigsite.gold", p[3]));
        if (p[4] > 0) parts.add(Component.translatable("coin.holeydigsite.silver", p[4]));
        if (p[5] > 0 || parts.isEmpty()) parts.add(Component.translatable("coin.holeydigsite.copper", p[5]));
        return parts;
    }

    /** The gold total as a plain number with thousands separators, e.g. "1,981". */
    public static String goldTotal(long copper) {
        return String.format(java.util.Locale.ROOT, "%,d", copper / GOLD);
    }

    /** Packs the coin pieces into lines no wider than {@code max} (pieces are never split). */
    public static List<Component> packParts(long copper, java.util.function.ToIntFunction<Component> width, int max) {
        List<Component> lines = new ArrayList<>();
        List<Component> row = new ArrayList<>();
        for (Component part : parts(copper)) {
            row.add(part);
            if (row.size() > 1 && width.applyAsInt(join(row)) > max) {
                row.remove(row.size() - 1);
                lines.add(join(row));
                row.clear();
                row.add(part);
            }
        }
        if (!row.isEmpty()) lines.add(join(row));
        return lines;
    }

    private static MutableComponent join(List<Component> parts) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) c.append(" ");
            c.append(parts.get(i));
        }
        return c;
    }

    /**
     * The Money Pan label: with platinum or more "Balance: 1,981 gold" and the coin breakdown wrapped under it, below that
     * one line "Balance: 7 gold 7 silver 7 copper".
     */
    public static List<Component> balanceLines(long copper, java.util.function.ToIntFunction<Component> width, int max) {
        List<Component> lines = new ArrayList<>();
        if (copper < PLATINUM) {
            lines.add(Component.translatable("tooltip.holeydigsite.money_pan.balance", format(copper, false)));
            return lines;
        }
        lines.add(Component.translatable("tooltip.holeydigsite.money_pan.balance",
                Component.translatable("coin.holeydigsite.gold", goldTotal(copper))));
        lines.addAll(packParts(copper, width, max));
        return lines;
    }

    private static MutableComponent format(long copper, boolean total) {
        List<Component> parts = parts(copper);
        if (total && copper >= PLATINUM) {
            parts.add(Component.translatable("coin.holeydigsite.gold_total", String.format(java.util.Locale.ROOT, "%,d", copper / GOLD)));
        }
        return join(parts);
    }
}
