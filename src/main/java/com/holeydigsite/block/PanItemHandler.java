package com.holeydigsite.block;

import com.holeydigsite.registry.ModItems;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Item-handler capability of a placed pan, so hoppers and other mods can feed it.
 *
 * <ul>
 *   <li>Accepts ONLY Dirt Clumps: one clump = one fill level, while fill &lt; {@link PanBlock#MAX_FILL} and the clump's
 *       tier matches the dirt already in the pan.</li>
 *   <li>Nothing can be extracted, and the slot always looks empty (the dirt is block state, not items),
 *       so hoppers never think the pan is "full" - they simply get the clump handed back when it is.</li>
 * </ul>
 * Stateless: it reads and writes the pan's block state at the time of each call, so it is safe to cache.
 */
public record PanItemHandler(Level level, BlockPos pos) implements IItemHandler {

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
        if (slot != 0 || stack.isEmpty() || !isItemValid(slot, stack)) {
            return stack;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PanBlock) || com.holeydigsite.plot.PlotLock.isLocked(level, pos)) {
            return stack;
        }
        int tier = DirtTier.panKind(stack);
        if (!PanBlock.acceptsTier(state, tier)) {
            return stack; // a different tier waits until the pan is empty
        }
        int room = PanBlock.maxFill(state) - PanBlock.fill(state);
        int accepted = Math.min(room, stack.getCount());
        if (accepted <= 0) {
            return stack;
        }
        if (!simulate) {
            PanBlock.addDirt(level, pos, state, accepted, tier);
        }
        return stack.copyWithCount(stack.getCount() - accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return PanBlock.maxFill(level.getBlockState(pos));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return DirtTier.isClump(stack);
    }
}
