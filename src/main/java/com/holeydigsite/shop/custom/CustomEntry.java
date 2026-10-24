package com.holeydigsite.shop.custom;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * An operator-made shop entry on a Shop Display. {@code template} is a copy of the item with count 1 and components kept,
 * and one purchase delivers {@code count} of it for {@code price} copper. If {@code buyBack} is greater than zero, the store's
 * Sell Pad pays that many copper per matching item. {@code rev} is a worldwide serial changing whenever the entry is set or
 * cleared, causing old blueprints to be refused when they no longer match.
 */
public record CustomEntry(ItemStack template, int count, long price, long buyBack, int rev) {
    public static final int MAX_COUNT = 64;
    public static final long MAX_PRICE = 1_000_000_000L;

    /** Checks if an item matches for buy-back by requiring the same item and identical data components. */
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(template, stack);
    }

    public boolean hasBuyBack() {
        return buyBack > 0;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Template", template.save(registries));
        tag.putInt("Count", count);
        tag.putLong("Price", price);
        tag.putLong("BuyBack", buyBack);
        tag.putInt("Rev", rev);
        return tag;
    }

    @Nullable
    public static CustomEntry load(HolderLookup.Provider registries, CompoundTag tag) {
        if (!tag.contains("Template")) {
            return null;
        }
        Optional<ItemStack> stack = ItemStack.parse(registries, tag.get("Template"));
        if (stack.isEmpty() || stack.get().isEmpty()) {
            return null;
        }
        return new CustomEntry(stack.get().copyWithCount(1), Math.max(1, Math.min(MAX_COUNT, tag.getInt("Count"))),
                tag.getLong("Price"), tag.getLong("BuyBack"), tag.getInt("Rev"));
    }
}
