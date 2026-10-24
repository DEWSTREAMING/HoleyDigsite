package com.holeydigsite.shovel;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.registry.ModDataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Helpers for the vanilla wooden shovel's "full of dirt" state.
 *
 * <p>The state lives in the {@link ModDataComponents#SHOVEL_FULL} component on the stack, so it survives
 * saving, dropping, and moving between inventories, and is visible to the client for the item model.</p>
 */
public final class ShovelHelper {
    private ShovelHelper() {}

    /**
     * Blocks an empty wooden shovel can scoop. Defined in
     * {@code data/holeydigsite/tags/block/scoopable.json} (grass block + dirt) - add to the tag to extend it.
     */
    public static final TagKey<Block> SCOOPABLE = TagKey.create(Registries.BLOCK, HoleyDigsite.id("scoopable"));

    /**
     * Durability used per scoop. 0 since Phase 2 so scooping never wears the shovel out (no soft-lock on a
     * skyblock island); set to 1 to match what breaking the block normally would cost.
     */
    public static final int SCOOP_DURABILITY_COST = 0;

    /** True if the stack is a vanilla wooden shovel (full or empty). */
    public static boolean isWoodenShovel(ItemStack stack) {
        return stack.is(Items.WOODEN_SHOVEL);
    }

    /** True if the stack is a wooden shovel carrying dirt. */
    public static boolean isFull(ItemStack stack) {
        return isWoodenShovel(stack) && stack.getOrDefault(ModDataComponents.SHOVEL_FULL.get(), false);
    }

    /** True if the stack is an empty wooden shovel that can scoop {@code state}. */
    public static boolean canScoop(ItemStack stack, BlockState state) {
        return isWoodenShovel(stack) && !isFull(stack) && state.is(SCOOPABLE);
    }

    /** Kind of the scoop in a full shovel (Phase 11: 1 dirt, 4 gravel, 5 sand - see {@link com.holeydigsite.tier.DirtTier}). */
    public static int kind(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SHOVEL_KIND.get(), 1);
    }

    /** Fills the shovel with a scoop of {@code kind} (dirt kind 1 stores no kind component). */
    public static void fill(ItemStack stack, int kind) {
        setFull(stack, true);
        if (kind > 1) {
            stack.set(ModDataComponents.SHOVEL_KIND.get(), kind);
        }
    }

    /**
     * Sets or clears the full state. Clearing removes the component entirely so an emptied shovel
     * is identical to a fresh one (and its NBT stays clean).
     */
    public static void setFull(ItemStack stack, boolean full) {
        if (full) {
            stack.set(ModDataComponents.SHOVEL_FULL.get(), true);
        } else {
            stack.remove(ModDataComponents.SHOVEL_FULL.get());
            stack.remove(ModDataComponents.SHOVEL_KIND.get());
        }
    }

    /** A new wooden shovel that already holds dirt (used by the creative tab). */
    public static ItemStack createFullShovel() {
        ItemStack stack = new ItemStack(Items.WOODEN_SHOVEL);
        setFull(stack, true);
        return stack;
    }
}
