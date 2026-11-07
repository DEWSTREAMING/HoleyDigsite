package com.holeydigsite.team;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config ({@code holeydigsite-server.toml}, per world). */
public final class TeamConfig {
    private TeamConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SYNC_FTB_TEAMS;
    public static final ModConfigSpec.BooleanValue CLAIM_ISLANDS;
    public static final ModConfigSpec.IntValue CLAIM_MARGIN_CHUNKS;
    public static final ModConfigSpec.BooleanValue OWN_ISLAND_FOR_EVERYONE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("teams");
        SYNC_FTB_TEAMS = b.comment(
                        "With FTB Teams and Skyblock Builder both installed: keep Skyblock Builder island teams in step with",
                        "FTB Teams parties (join a party = move to its island, leave = an island of your own).")
                .define("syncFtbTeams", true);
        CLAIM_ISLANDS = b.comment(
                        "With FTB Chunks, FTB Teams and Skyblock Builder installed: claim every island for its team, so it is",
                        "protected and shows on the map. Off = no new claims; claims already made stay as they are.")
                .define("claimIslands", true);
        CLAIM_MARGIN_CHUNKS = b.comment(
                        "How many chunks to claim beyond the edge of the island (home island, store, plots and event island).",
                        "0 = just the island. Every claimed chunk counts against the team's FTB Chunks limit.")
                .defineInRange("claimMarginChunks", 1, 0, IslandClaimRules.MAX_MARGIN);
        OWN_ISLAND_FOR_EVERYONE = b.comment(
                        "Give every player (including the first player on a server) their own island when joining, leaving the",
                        "spawn island as a shared hub. On by default on a dedicated server, off in single player.")
                .define("ownIslandForEveryone", FMLEnvironment.dist.isDedicatedServer());
        b.pop();
        SPEC = b.build();
    }

    /** False before the server config has loaded (then nothing syncs). */
    public static boolean syncEnabled() {
        return SPEC.isLoaded() && SYNC_FTB_TEAMS.get();
    }

    /** False before the server config has loaded (then nothing is claimed). */
    public static boolean claimEnabled() {
        return SPEC.isLoaded() && CLAIM_ISLANDS.get();
    }

    public static int claimMargin() {
        return SPEC.isLoaded() ? CLAIM_MARGIN_CHUNKS.get() : 1;
    }

    public static boolean ownIslandForEveryone() {
        return SPEC.isLoaded() ? OWN_ISLAND_FOR_EVERYONE.get() : FMLEnvironment.dist.isDedicatedServer();
    }
}
