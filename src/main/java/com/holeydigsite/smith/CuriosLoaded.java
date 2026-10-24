package com.holeydigsite.smith;

import net.neoforged.fml.ModList;

/** Whether the optional Curios API mod is installed (checked once; no Curios class is touched here). */
public final class CuriosLoaded {
    private CuriosLoaded() {}

    private static Boolean loaded;

    public static boolean is() {
        if (loaded == null) {
            ModList list = ModList.get();
            if (list == null) {
                return false;
            }
            loaded = list.isLoaded("curios");
        }
        return loaded;
    }
}
