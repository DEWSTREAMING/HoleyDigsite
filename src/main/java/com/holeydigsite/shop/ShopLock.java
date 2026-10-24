package com.holeydigsite.shop;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The {@code locked} block state shared by the Sell Pad, Buy Pad, Money Pan and Shop Button. {@code locked=true} is the
 * island's shop: it can't be broken in survival (no mining progress), resists explosions and withers/dragons. Player
 * crafted blocks are {@code locked=false} and break normally, dropping themselves and their contents. Creative players
 * can break either (and the creative tab has locked variants for building shops on other islands).
 */
public final class ShopLock {
    private ShopLock() {}

    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    /** Explosion resistance of a locked block (same as bedrock / Dirtrock). */
    public static final float LOCKED_RESISTANCE = 3600000.0F;

    public static boolean isLocked(BlockState state) {
        return state.hasProperty(LOCKED) && state.getValue(LOCKED);
    }
}
