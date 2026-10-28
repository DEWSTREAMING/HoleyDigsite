package com.holeydigsite.compat.teams;

import com.holeydigsite.team.TeamAccess;
import de.melanx.skyblockbuilder.config.common.WorldConfig;
import de.melanx.skyblockbuilder.data.SkyblockSavedData;
import de.melanx.skyblockbuilder.data.Team;
import de.melanx.skyblockbuilder.util.WorldUtil;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Island teams from Skyblock Builder. A position belongs to the team whose island centre is nearest, as long as it is
 * within half the island distance (islands sit on a grid that far apart). The spawn island is the spawn team.
 */
final class SkyblockTeamAccess implements TeamAccess {
    @Override
    public @Nullable UUID islandTeamAt(ServerLevel level, BlockPos pos) {
        if (!WorldUtil.isSkyblock(level) || level != WorldUtil.getConfiguredLevel(level.getServer())) {
            return null;
        }
        Team team = teamAt(SkyblockSavedData.get(level), pos);
        return team == null ? null : team.getId();
    }

    @Override
    public Set<UUID> members(ServerLevel level, UUID team) {
        if (!WorldUtil.isSkyblock(level)) {
            return Set.of();
        }
        Team t = SkyblockSavedData.get(level).getTeam(team);
        return t == null ? Set.of() : players(t);
    }

    @Override
    public boolean sameTeam(ServerLevel level, UUID a, UUID b) {
        if (!WorldUtil.isSkyblock(level)) {
            return true;
        }
        return Objects.equals(teamIdOf(SkyblockSavedData.get(level), a), teamIdOf(SkyblockSavedData.get(level), b));
    }

    @Nullable
    static Team teamAt(SkyblockSavedData data, BlockPos pos) {
        int half = Math.max(1, WorldConfig.islandDistance / 2);
        Team best = null;
        long bestDist = Long.MAX_VALUE;
        for (Team t : data.getTeams()) {
            if (t.getIsland() == null) {
                continue;
            }
            BlockPos c = t.getIsland().getCenter();
            int dx = Math.abs(c.getX() - pos.getX()), dz = Math.abs(c.getZ() - pos.getZ());
            if (dx > half || dz > half) {
                continue;
            }
            long d = (long) dx * dx + (long) dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        return best;
    }

    /** The player's island team id: their own team, the spawn team, or null if Skyblock Builder doesn't know them. */
    @Nullable
    static UUID teamIdOf(SkyblockSavedData data, UUID player) {
        Team t = data.getTeamFromPlayer(player);
        if (t != null) {
            return t.getId();
        }
        Team spawn = data.getSpawnOption().orElse(null);
        return spawn != null && spawn.hasPlayer(player) ? spawn.getId() : null;
    }

    /** Real players only (the spawn team holds the nil id as a marker). */
    static Set<UUID> players(Team t) {
        Set<UUID> out = new HashSet<>(t.getPlayers());
        out.remove(Util.NIL_UUID);
        return out;
    }
}
