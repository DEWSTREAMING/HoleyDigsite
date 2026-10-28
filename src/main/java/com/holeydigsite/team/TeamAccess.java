package com.holeydigsite.team;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Who belongs to which island. The rest of the mod only asks this, never a team mod directly.
 * With Skyblock Builder the team is the one whose island holds the position; without a team mod every position
 * answers "no team", so everyone may use everything as before.
 */
public interface TeamAccess {
    /** The team whose island holds {@code pos}, or null when no team owns that spot (anyone may use it). */
    @Nullable
    UUID islandTeamAt(ServerLevel level, BlockPos pos);

    /** Players in that team. Empty for an unknown team. */
    Set<UUID> members(ServerLevel level, UUID team);

    /** True if both players are in the same island team. */
    boolean sameTeam(ServerLevel level, UUID a, UUID b);

    /** True if {@code player} may use things on the island at {@code pos}. */
    default boolean allows(ServerLevel level, BlockPos pos, UUID player) {
        UUID team = islandTeamAt(level, pos);
        return team == null || members(level, team).contains(player);
    }

    /** No team mod: no island has a team, so nothing is restricted. */
    TeamAccess EVERYONE = new TeamAccess() {
        @Override
        public @Nullable UUID islandTeamAt(ServerLevel level, BlockPos pos) {
            return null;
        }

        @Override
        public Set<UUID> members(ServerLevel level, UUID team) {
            return Set.of();
        }

        @Override
        public boolean sameTeam(ServerLevel level, UUID a, UUID b) {
            return true;
        }
    };

    /** The active answer: set once at startup by a team compat, swapped by tests. */
    static TeamAccess get() {
        return Holder.current;
    }

    /** Sets the active team source and returns the old one (tests put the old one back). */
    static TeamAccess set(TeamAccess access) {
        TeamAccess old = Holder.current;
        Holder.current = access == null ? EVERYONE : access;
        return old;
    }

    final class Holder {
        private static volatile TeamAccess current = EVERYONE;

        private Holder() {}
    }
}
