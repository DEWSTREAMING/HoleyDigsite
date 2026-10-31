package com.holeydigsite.guide;

import net.minecraft.client.Minecraft;

/**
 * Isolates client-only screen opening from common classes.
 */
public final class FieldGuideClientHooks {
    private FieldGuideClientHooks() {}

    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        FieldGuideDefs.reload(mc.getResourceManager());
        mc.setScreen(new com.holeydigsite.client.FieldGuideScreen());
    }
}
