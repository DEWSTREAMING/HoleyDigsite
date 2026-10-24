package com.holeydigsite.smith;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * Placeholder tier of the smithed swords and axe. The real durability, damage and mining speed of a piece are set on
 * its stack from its metals (see {@link SmithPieces#create}). This only gives a piece without those components for creative or give, using
 * iron-like defaults. Not repairable.
 */
public final class SmithTier implements Tier {
    public static final SmithTier INSTANCE = new SmithTier();

    private SmithTier() {}

    @Override
    public int getUses() {
        return 250;
    }

    @Override
    public float getSpeed() {
        return 6.0F;
    }

    @Override
    public float getAttackDamageBonus() {
        return 2.0F;
    }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return BlockTags.INCORRECT_FOR_IRON_TOOL;
    }

    @Override
    public int getEnchantmentValue() {
        return 10;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }
}
