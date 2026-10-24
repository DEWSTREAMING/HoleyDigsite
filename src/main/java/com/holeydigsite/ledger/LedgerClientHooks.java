package com.holeydigsite.ledger;

import net.minecraft.client.Minecraft;

/**
 * Client-side screen opener isolated from server classloading.
 */
public final class LedgerClientHooks {
    private LedgerClientHooks() {}

    public static void openScreen() {
        Minecraft.getInstance().setScreen(new com.holeydigsite.client.LedgerScreen());
    }
}
