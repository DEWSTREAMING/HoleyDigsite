package com.holeydigsite.crate;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config for crates ({@code holeydigsite-crates.toml}, per world). */
public final class CrateConfig {
    private CrateConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEFAULT_ON;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("crates");
        DEFAULT_ON = b.comment(
                        "Crates and skins for players who have not used the Crate Switch yet.",
                        "Off: they find no crates, cannot buy Bolt Cutters, and crate and skin rewards pay coins instead.")
                .define("default_on", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean defaultOn() {
        return !SPEC.isLoaded() || DEFAULT_ON.get();
    }
}
