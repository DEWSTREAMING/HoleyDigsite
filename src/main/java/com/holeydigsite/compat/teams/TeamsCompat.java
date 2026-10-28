package com.holeydigsite.compat.teams;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.team.TeamAccess;
import net.neoforged.fml.ModList;

/**
 * Entry point for the optional team mods. Only this class is touched from outside the package, and only after the
 * mod list checks below, so nothing here loads when the mods are missing.
 */
public final class TeamsCompat {
    private TeamsCompat() {}

    public static final String SKYBLOCK_BUILDER = "skyblockbuilder";
    public static final String FTB_TEAMS = "ftbteams";

    public static boolean skyblockBuilder() {
        return ModList.get().isLoaded(SKYBLOCK_BUILDER);
    }

    public static boolean ftbTeams() {
        return ModList.get().isLoaded(FTB_TEAMS);
    }

    public static void init() {
        if (skyblockBuilder()) {
            TeamAccess.set(new SkyblockTeamAccess());
            HoleyDigsite.LOGGER.info("Skyblock Builder found: bought plots and Money Pans belong to the island's team");
            if (ftbTeams()) {
                FtbTeamSync.register();
                HoleyDigsite.LOGGER.info("FTB Teams found: island teams follow FTB Teams parties");
            }
        }
    }
}
