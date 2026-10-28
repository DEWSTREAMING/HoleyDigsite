package com.holeydigsite.team;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config ({@code holeydigsite-server.toml}, per world). */
public final class TeamConfig {
    private TeamConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SYNC_FTB_TEAMS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("teams");
        SYNC_FTB_TEAMS = b.comment(
                        "With FTB Teams and Skyblock Builder both installed: keep Skyblock Builder island teams in step with",
                        "FTB Teams parties (join a party = move to its island, leave = an island of your own).")
                .define("syncFtbTeams", true);
        b.pop();
        SPEC = b.build();
    }

    /** False before the server config has loaded (then nothing syncs). */
    public static boolean syncEnabled() {
        return SPEC.isLoaded() && SYNC_FTB_TEAMS.get();
    }
}
