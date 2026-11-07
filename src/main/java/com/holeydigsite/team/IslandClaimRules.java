package com.holeydigsite.team;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which chunks an island's team gets claimed, and for whom. Plain decisions with no team mod classes, so they can be
 * tested with fakes (the same way as {@link TeamSyncRules}).
 *
 * <ul>
 *   <li>The area is the part of the world the island template and the event island fill, in whole chunks plus a margin,
 *       and never a chunk that lies nearer another island's centre.</li>
 *   <li>The claiming team is the FTB team all the island's members share (their party, or their own team when alone).
 *       Members split over several teams and an empty island get nothing.</li>
 *   <li>The spawn island in singleplayer and on old worlds is claimed for whoever lives on it, and released when empty.</li>
 *   <li>The server hub is a recorded place ({@code HubData}); its chunks only ever get a server claim.</li>
 *   <li>Chunks someone else already owns stay theirs.</li>
 *   <li>An island is done once it has been tried, so it is claimed once and running again changes nothing.</li>
 * </ul>
 */
public final class IslandClaimRules {
    private IslandClaimRules() {}

    /*
     * How far the island reaches from its centre (Skyblock Builder's island centre = the template's origin), in blocks:
     * the template is 75 x 57 (x 0..74, z 0..56, home island, store and the three plots), the event island sits north of it
     * and fills x -31..51, z -42..-1 (data/holeydigsite/event_island.json). Bought plots all lie inside.
     */
    public static final int WEST = 31;
    public static final int EAST = 74;
    public static final int NORTH = 42;
    public static final int SOUTH = 56;

    public static final int MAX_MARGIN = 4;

    public record Chunk(int x, int z) {}

    /** An island centre in block coordinates. */
    public record Centre(int x, int z) {}

    /** The island side. */
    public interface Holders {
        /** Players on that island team. */
        Set<UUID> members(UUID island);

        /** The FTB team (party, or the player's own) the player is in; null if unknown. */
        @Nullable
        UUID teamOf(UUID player);
    }

    /** The claim side. */
    public interface Owners {
        /** The FTB team that owns the chunk, null if it is unclaimed. */
        @Nullable
        UUID ownerOf(Chunk chunk);
    }

    /** {@code claim} are the chunks to claim now, {@code unclaim} chunks to release, {@code taken} the ones another team owns. */
    public record Plan(@Nullable UUID team, List<Chunk> claim, List<Chunk> unclaim, List<Chunk> taken, boolean finished) {
        public static final Plan NOTHING = new Plan(null, List.of(), List.of(), List.of(), false);
        public static final Plan DONE = new Plan(null, List.of(), List.of(), List.of(), true);

        public Plan(@Nullable UUID team, List<Chunk> claim, List<Chunk> taken, boolean finished) {
            this(team, claim, List.of(), taken, finished);
        }
    }

    /**
     * The chunks of the island at {@code centre}: the reach in whole chunks, grown by {@code margin} chunks, leaving out any
     * chunk (by its middle) that lies nearer one of {@code others} than this centre.
     */
    public static List<Chunk> area(Centre centre, int margin, Collection<Centre> others) {
        int m = Math.max(0, Math.min(MAX_MARGIN, margin));
        int minX = Math.floorDiv(centre.x() - WEST, 16) - m;
        int maxX = Math.floorDiv(centre.x() + EAST, 16) + m;
        int minZ = Math.floorDiv(centre.z() - NORTH, 16) - m;
        int maxZ = Math.floorDiv(centre.z() + SOUTH, 16) + m;
        List<Chunk> out = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long own = dist(centre, x * 16 + 8, z * 16 + 8);
                boolean nearerOther = false;
                for (Centre o : others) {
                    if (!o.equals(centre) && dist(o, x * 16 + 8, z * 16 + 8) < own) {
                        nearerOther = true;
                        break;
                    }
                }
                if (!nearerOther) {
                    out.add(new Chunk(x, z));
                }
            }
        }
        return out;
    }

    private static long dist(Centre c, int x, int z) {
        long dx = x - c.x(), dz = z - c.z();
        return dx * dx + dz * dz;
    }

    /** An island is the server hub when its centre lies in one of the chunks recorded for the hub. */
    public static boolean isHub(Centre centre, Collection<Chunk> hubChunks) {
        return hubChunks.contains(new Chunk(Math.floorDiv(centre.x(), 16), Math.floorDiv(centre.z(), 16)));
    }

    /**
     * On a server made from the server pack, Skyblock Builder builds the hub as its spawn island. Record it as the hub only
     * while nobody has ever lived there: an old world that later gets the server pack's config keeps its spawn island as a
     * normal island.
     */
    public static boolean recordSpawnAsHub(boolean hubTemplate, boolean hasHub, boolean spawnBuilt, int residents, boolean everClaimed) {
        return hubTemplate && !hasHub && spawnBuilt && residents == 0 && !everClaimed;
    }

    /** The FTB team to claim for: every member of the island is in it. Null if there is none to name. */
    @Nullable
    public static UUID claimingTeam(UUID island, Holders holders) {
        UUID team = null;
        for (UUID member : holders.members(island)) {
            UUID t = holders.teamOf(member);
            if (t == null || (team != null && !team.equals(t))) {
                return null;
            }
            team = t;
        }
        return team;
    }

    /** What to do for one island now. {@code done} is whether this island was already claimed by an earlier run. */
    public static Plan plan(boolean enabled, UUID island, boolean done, List<Chunk> area, Holders holders, Owners owners) {
        if (!enabled) {
            return Plan.NOTHING;
        }
        if (TeamSyncRules.SPAWN.equals(island) && holders.members(island).isEmpty()) {
            if (done) {
                List<Chunk> unclaim = new ArrayList<>();
                for (Chunk c : area) {
                    if (owners.ownerOf(c) != null) {
                        unclaim.add(c);
                    }
                }
                return new Plan(null, List.of(), unclaim, List.of(), false);
            }
            return Plan.NOTHING;
        }
        if (done) {
            return Plan.DONE;
        }
        UUID team = claimingTeam(island, holders);
        if (team == null) {
            return Plan.NOTHING;
        }
        List<Chunk> claim = new ArrayList<>();
        List<Chunk> taken = new ArrayList<>();
        for (Chunk c : area) {
            UUID owner = owners.ownerOf(c);
            if (owner == null) {
                claim.add(c);
            } else if (!owner.equals(team)) {
                taken.add(c);
            }
        }
        return new Plan(team, claim, List.of(), taken, true);
    }
}
