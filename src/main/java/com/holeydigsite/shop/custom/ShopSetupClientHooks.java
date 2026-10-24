package com.holeydigsite.shop.custom;

import com.holeydigsite.client.ShopSetupScreen;
import net.minecraft.client.Minecraft;

/** A client-side screen opener isolated from server classloading. */
public final class ShopSetupClientHooks {
    private ShopSetupClientHooks() {}

    public static void open(ShopSetupNetwork.OpenPayload p) {
        Minecraft.getInstance().setScreen(new ShopSetupScreen(p));
    }
}
