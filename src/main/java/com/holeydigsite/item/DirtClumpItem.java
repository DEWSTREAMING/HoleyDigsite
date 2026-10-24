package com.holeydigsite.item;

import com.holeydigsite.tier.DirtTier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Dirt Clump: one scoop of dirt as an item. Its tier ({@link DirtTier}) comes from the rock it was drilled from
 * and picks the name ("Dirt Clump" / "Deep Dirt Clump" / "Abyssal Dirt Clump") and, client side, the texture
 * (item model override on {@code holeydigsite:tier}).
 */
public class DirtClumpItem extends Item {
    public DirtClumpItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        int tier = DirtTier.ofClump(stack);
        return tier <= 1 ? super.getName(stack) : Component.translatable(getDescriptionId() + ".tier_" + tier);
    }
}
