package com.holeydigsite.forge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Item handler of a Crucible (hoppers, other mods): insert-only, meltables only, as many items as fit by grams.
 * The slot always looks empty and nothing can be extracted.
 */
public record CrucibleItemHandler(CrucibleBlockEntity crucible) implements IItemHandler {
    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0 || stack.isEmpty() || crucible.isRemoved()) {
            return stack;
        }
        int n = crucible.insert(stack, stack.getCount(), simulate);
        return n <= 0 ? stack : stack.copyWithCount(stack.getCount() - n);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return Metals.meltable(stack) != null;
    }
}
