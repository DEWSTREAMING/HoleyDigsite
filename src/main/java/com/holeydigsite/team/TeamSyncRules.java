package com.holeydigsite.team;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What to do so the island teams (Skyblock Builder) follow the parties (FTB Teams). Plain decisions with no team mod
 * classes, so they can be tested with fakes. The FTB party is the master; a party lives on its owner's island.
 *
 * <ul>
 *   <li>Joined a party: move to the owner's island (unless already there).</li>
 *   <li>Made a party on an island another party already holds: the new party gets a fresh island.</li>
 *   <li>Not in a party (left, kicked, disbanded) but on an island a party holds: a fresh island of their own.
 *       The last one on an island keeps it.</li>
 *   <li>First time on the server and the spawn island already has someone: an island of their own.</li>
 *   <li>Made an island by command while not in a party: a matching party.</li>
 * </ul>
 * Every rule only moves a player away from a state that breaks it, so applying the actions and asking again gives nothing.
 */
public final class TeamSyncRules {
    private TeamSyncRules() {}

    /** The island side (Skyblock Builder). */
    public interface Islands {
        /** The player's island team, {@link #SPAWN} for the shared spawn island, null if the player has none yet. */
        @Nullable
        UUID islandOf(UUID player);

        /** Players on that island team. */
        java.util.Set<UUID> members(UUID island);
    }

    /** The party side (FTB Teams). */
    public interface Parties {
        /** The player's party, null when they are only in their own personal team. */
        @Nullable
        UUID partyOf(UUID player);

        @Nullable
        UUID ownerOf(UUID party);
    }

    /** Skyblock Builder's spawn team id (its nil UUID). */
    public static final UUID SPAWN = new UUID(0L, 0L);

    public sealed interface Action permits Move, FreshIsland, MakeParty {}

    /** Put {@code player} into the {@code island} team and send them there. */
    public record Move(UUID player, UUID island) implements Action {}

    /** Give {@code player} a new island of their own and send them there. */
    public record FreshIsland(UUID player) implements Action {}

    /** Make a party for {@code player}, named after their island. */
    public record MakeParty(UUID player, UUID island) implements Action {}

    /**
     * After a party change (join, leave, kick, disband) or at login. {@code created} is true when the player has just
     * made this party.
     */
    public static List<Action> reconcile(boolean enabled, UUID player, boolean created, Islands islands, Parties parties) {
        List<Action> out = new ArrayList<>();
        if (!enabled) {
            return out;
        }
        UUID party = parties.partyOf(player);
        UUID island = islands.islandOf(player);
        if (party != null) {
            UUID owner = parties.ownerOf(party);
            if (owner == null) {
                return out;
            }
            if (!owner.equals(player)) {
                UUID target = islands.islandOf(owner);
                if (target != null && !target.equals(island)) {
                    out.add(new Move(player, target));
                }
            } else if (created && island != null && heldByOtherParty(island, player, party, islands, parties)) {
                out.add(new FreshIsland(player));
            }
            return out;
        }
        if (island != null && heldByOtherParty(island, player, null, islands, parties)) {
            out.add(new FreshIsland(player));
        }
        return out;
    }

    /** A player Skyblock Builder has just put on the spawn island for the first time. */
    public static List<Action> newcomer(boolean enabled, UUID player, Islands islands) {
        List<Action> out = new ArrayList<>();
        if (!enabled || !SPAWN.equals(islands.islandOf(player))) {
            return out;
        }
        for (UUID m : islands.members(SPAWN)) {
            if (!m.equals(player)) {
                out.add(new FreshIsland(player));
                break;
            }
        }
        return out;
    }

    /** An island just made by Skyblock Builder's own create command. */
    public static List<Action> islandCreated(boolean enabled, UUID island, Islands islands, Parties parties) {
        List<Action> out = new ArrayList<>();
        if (!enabled || SPAWN.equals(island)) {
            return out;
        }
        for (UUID m : islands.members(island)) {
            if (parties.partyOf(m) == null) {
                out.add(new MakeParty(m, island));
            }
        }
        return out;
    }

    /** True if someone else on {@code island} is in a party (other than {@code ownParty}) whose owner lives there. */
    static boolean heldByOtherParty(UUID island, UUID player, @Nullable UUID ownParty, Islands islands, Parties parties) {
        for (UUID m : islands.members(island)) {
            if (m.equals(player)) {
                continue;
            }
            UUID p = parties.partyOf(m);
            if (p == null || Objects.equals(p, ownParty)) {
                continue;
            }
            UUID owner = parties.ownerOf(p);
            if (owner != null && island.equals(islands.islandOf(owner))) {
                return true;
            }
        }
        return false;
    }
}
