package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.hub.HubData;
import com.holeydigsite.team.IslandClaimData;
import com.holeydigsite.team.IslandClaimRules;
import com.holeydigsite.team.IslandClaimRules.Centre;
import com.holeydigsite.team.IslandClaimRules.Chunk;
import com.holeydigsite.team.TeamConfig;
import com.holeydigsite.team.TeamSyncRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 34b tests:
 * 1. Spawn island claimed for first resident player; released when emptied so a newcomer can build and claim it.
 * 2. Disbanding when two people share an island: first owner keeps the island and claims, other leaver moves to fresh island.
 * 3. ownIslandForEveryone config controls newcomer island assignment.
 * 4. IslandClaimData owner tracking and HubData persistence.
 * 5. Hub command registration and permission level.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase34bTests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static final Centre C0 = new Centre(37, 28);

    private static final class TestWorld implements IslandClaimRules.Holders, IslandClaimRules.Owners {
        final Map<UUID, UUID> island = new HashMap<>();
        final Map<UUID, UUID> team = new HashMap<>();
        final Map<Chunk, UUID> owner = new HashMap<>();

        @Override
        public Set<UUID> members(UUID is) {
            Set<UUID> out = new HashSet<>();
            island.forEach((p, i) -> {
                if (i.equals(is)) out.add(p);
            });
            return out;
        }

        @Override
        public @Nullable UUID teamOf(UUID player) {
            return team.get(player);
        }

        @Override
        public @Nullable UUID ownerOf(Chunk chunk) {
            return owner.get(chunk);
        }
    }

    @GameTest(template = "empty")
    public static void spawnIslandClaimedAndReleasedWhenEmptied(GameTestHelper h) {
        TestWorld w = new TestWorld();
        UUID playerA = UUID.randomUUID();
        w.team.put(playerA, playerA);
        w.island.put(playerA, TeamSyncRules.SPAWN);

        List<Chunk> area = IslandClaimRules.area(C0, 1, List.of());

        // First player on spawn island gets it planned and claimed
        IslandClaimRules.Plan plan = IslandClaimRules.plan(true, TeamSyncRules.SPAWN, false, area, w, w);
        check(h, playerA.equals(plan.team()), "spawn island claims for player A");
        check(h, !plan.claim().isEmpty(), "spawn chunks planned to claim");
        check(h, plan.unclaim().isEmpty(), "no chunks to unclaim initially");
        check(h, plan.finished(), "plan finishes");

        for (Chunk c : plan.claim()) {
            w.owner.put(c, playerA);
        }

        // Player A leaves spawn island (joins party on another island)
        UUID playerB = UUID.randomUUID();
        UUID otherIsland = UUID.randomUUID();
        w.island.put(playerA, otherIsland);
        w.island.put(playerB, otherIsland);

        // Spawn island is now empty and was previously done -> unclaim plan
        IslandClaimRules.Plan unclaimPlan = IslandClaimRules.plan(true, TeamSyncRules.SPAWN, true, area, w, w);
        check(h, unclaimPlan.team() == null, "no team on empty spawn");
        check(h, !unclaimPlan.finished(), "empty spawn marked not done so next player can claim");
        check(h, unclaimPlan.unclaim().size() == area.size(), "all spawn chunks queued for unclaim");

        for (Chunk c : unclaimPlan.unclaim()) {
            w.owner.remove(c);
        }

        // Newcomer arrives on empty spawn island
        UUID newcomer = UUID.randomUUID();
        w.team.put(newcomer, newcomer);
        w.island.put(newcomer, TeamSyncRules.SPAWN);

        IslandClaimRules.Plan newcomerPlan = IslandClaimRules.plan(true, TeamSyncRules.SPAWN, false, area, w, w);
        check(h, newcomer.equals(newcomerPlan.team()), "newcomer claims spawn island");
        check(h, newcomerPlan.claim().size() == area.size(), "all chunks claimed for newcomer");
        check(h, newcomerPlan.finished(), "newcomer claim marked finished");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void disbandTwoPeopleOnOneIsland(GameTestHelper h) {
        UUID ownerA = UUID.randomUUID();
        UUID guestB = UUID.randomUUID();
        UUID island = UUID.randomUUID();

        TeamSyncRules.Parties parties = new TeamSyncRules.Parties() {
            @Override
            public @Nullable UUID partyOf(UUID player) {
                return null;
            }

            @Override
            public @Nullable UUID ownerOf(UUID party) {
                return null;
            }
        };

        TeamSyncRules.Islands islands = new TeamSyncRules.Islands() {
            @Override
            public @Nullable UUID islandOf(UUID player) {
                return island;
            }

            @Override
            public Set<UUID> members(UUID isl) {
                return isl.equals(island) ? Set.of(ownerA, guestB) : Set.of();
            }

            @Override
            public @Nullable UUID islandOwner(UUID isl) {
                return isl.equals(island) ? ownerA : null;
            }
        };

        // Reconcile for owner A: keeps island (no action returned)
        List<TeamSyncRules.Action> actionsA = TeamSyncRules.reconcile(true, ownerA, false, islands, parties);
        check(h, actionsA.isEmpty(), "owner stays on island after disband");

        // Reconcile for guest B: non-owner must leave shared island
        List<TeamSyncRules.Action> actionsB = TeamSyncRules.reconcile(true, guestB, false, islands, parties);
        check(h, actionsB.size() == 1, "non-owner gets action after disband");
        check(h, actionsB.get(0) instanceof TeamSyncRules.FreshIsland fi && fi.player().equals(guestB),
                "guest B gets fresh island");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void newcomerOwnIslandForEveryone(GameTestHelper h) {
        UUID newcomer = UUID.randomUUID();
        TeamSyncRules.Islands emptySpawn = new TeamSyncRules.Islands() {
            @Override
            public @Nullable UUID islandOf(UUID player) {
                return TeamSyncRules.SPAWN;
            }

            @Override
            public Set<UUID> members(UUID isl) {
                return Set.of(newcomer);
            }
        };

        // When ownIslandForEveryone is false (singleplayer / default client) and spawn is empty of others:
        List<TeamSyncRules.Action> actionsOff = TeamSyncRules.newcomer(true, false, newcomer, emptySpawn);
        check(h, actionsOff.isEmpty(), "lands on spawn when ownIslandForEveryone is off and spawn is empty");

        // When ownIslandForEveryone is true (dedicated server default):
        List<TeamSyncRules.Action> actionsOn = TeamSyncRules.newcomer(true, true, newcomer, emptySpawn);
        check(h, actionsOn.size() == 1 && actionsOn.get(0) instanceof TeamSyncRules.FreshIsland,
                "gets fresh island when ownIslandForEveryone is on");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void claimRecordOwnerPersistence(GameTestHelper h) {
        IslandClaimData d = IslandClaimData.get(h.getLevel().getServer());
        UUID is = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        check(h, d.ownerOf(is) == null, "new island has no owner");
        d.setOwner(is, owner);
        check(h, owner.equals(d.ownerOf(is)), "owner set");

        List<Chunk> chunks = List.of(new Chunk(10, 20), new Chunk(11, 20));
        d.finish(is, chunks);

        CompoundTag tag = d.save(new CompoundTag(), h.getLevel().registryAccess());
        IslandClaimData loaded = IslandClaimData.load(tag, h.getLevel().registryAccess());

        check(h, owner.equals(loaded.ownerOf(is)), "owner persists through save/load");
        check(h, loaded.isDone(is), "done status persists");
        check(h, loaded.claimed(is).equals(chunks), "claimed chunks persist");

        // Test release
        loaded.release(is);
        check(h, !loaded.isDone(is), "release clears done status");
        check(h, loaded.claimed(is).isEmpty(), "release clears claimed chunks");
        check(h, loaded.ownerOf(is) == null, "release clears owner");

        d.release(is);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hubDataPersistence(GameTestHelper h) {
        BlockPos anchor = new BlockPos(100, 64, 200);
        BlockPos landing = new BlockPos(120, 74, 220);
        List<Chunk> chunks = List.of(new Chunk(6, 12), new Chunk(7, 12));

        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasHub", true);
        tag.putLong("Anchor", anchor.asLong());
        tag.putLong("Landing", landing.asLong());
        tag.putIntArray("Chunks", new int[]{6, 12, 7, 12});

        HubData loaded = HubData.load(tag, h.getLevel().registryAccess());
        check(h, loaded.hasHub(), "hub state persists");
        check(h, anchor.equals(loaded.hubAnchor()), "hub anchor persists");
        check(h, landing.equals(loaded.hubLanding()), "hub landing persists");
        check(h, loaded.chunks().equals(chunks), "hub chunks persist");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hubCommandRegistration(GameTestHelper h) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        com.holeydigsite.command.ModCommands.register(dispatcher);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("holeydigsite");
        check(h, root != null, "/holeydigsite registered");
        CommandNode<CommandSourceStack> hubNode = root.getChild("hub");
        check(h, hubNode != null, "/holeydigsite hub registered");
        CommandNode<CommandSourceStack> createNode = hubNode.getChild("create");
        check(h, createNode != null, "/holeydigsite hub create registered");
        CommandNode<CommandSourceStack> confirmNode = createNode.getChild("confirm");
        check(h, confirmNode != null, "/holeydigsite hub create confirm registered");

        h.succeed();
    }
    @GameTest(template = "empty")
    public static void isHubAndRecordSpawnAsHubRules(GameTestHelper h) {
        Chunk hubChunk = new Chunk(2, 1);
        Centre inside = new Centre(37, 28); // 37/16 = 2, 28/16 = 1
        Centre outside = new Centre(300, 300);

        check(h, IslandClaimRules.isHub(inside, List.of(hubChunk)), "centre inside hub chunk is hub");
        check(h, !IslandClaimRules.isHub(outside, List.of(hubChunk)), "centre outside hub chunk is not hub");

        check(h, IslandClaimRules.recordSpawnAsHub(true, false, true, 0, false), "fresh server pack spawn is hub");
        check(h, !IslandClaimRules.recordSpawnAsHub(false, false, true, 0, false), "singleplayer/no hub template is not hub");
        check(h, !IslandClaimRules.recordSpawnAsHub(true, true, true, 0, false), "already has hub is not hub");
        check(h, !IslandClaimRules.recordSpawnAsHub(true, false, false, 0, false), "unbuilt spawn is not hub");
        check(h, !IslandClaimRules.recordSpawnAsHub(true, false, true, 1, false), "spawn with residents is not hub");
        check(h, !IslandClaimRules.recordSpawnAsHub(true, false, true, 0, true), "previously claimed spawn is not hub");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldSpawnIslandAfterHubCreateReoccupiedByResidents(GameTestHelper h) {
        TestWorld w = new TestWorld();
        UUID resident = UUID.randomUUID();
        w.team.put(resident, resident);
        w.island.put(resident, TeamSyncRules.SPAWN);

        Centre oldSpawn = C0;
        Centre hubCenter = new Centre(500, 500);
        List<Chunk> hubChunks = IslandClaimRules.area(hubCenter, 0, List.of());
        List<Chunk> spawnArea = IslandClaimRules.area(oldSpawn, 1, List.of());

        // Old spawn is not the hub
        check(h, !IslandClaimRules.isHub(oldSpawn, hubChunks), "old spawn is not the hub");

        // Initially claimed for resident
        IslandClaimRules.Plan p1 = IslandClaimRules.plan(true, TeamSyncRules.SPAWN, false, spawnArea, w, w);
        check(h, resident.equals(p1.team()), "initially claimed for resident");
        for (Chunk c : p1.claim()) {
            w.owner.put(c, resident);
        }

        // Resident leaves for a party on another island; old spawn empties
        UUID partyOwner = UUID.randomUUID();
        UUID otherIsland = UUID.randomUUID();
        w.island.put(resident, otherIsland);
        w.island.put(partyOwner, otherIsland);

        IslandClaimRules.Plan unclaimPlan = IslandClaimRules.plan(true, TeamSyncRules.SPAWN, true, spawnArea, w, w);
        check(h, unclaimPlan.unclaim().size() == spawnArea.size(), "old spawn unclaims when empty");
        for (Chunk c : unclaimPlan.unclaim()) {
            w.owner.remove(c);
        }

        // Resident disbands party and returns to old spawn island
        w.island.put(resident, TeamSyncRules.SPAWN);

        // Reclaimed for resident team, never a server claim
        IslandClaimRules.Plan p2 = IslandClaimRules.plan(true, TeamSyncRules.SPAWN, false, spawnArea, w, w);
        check(h, resident.equals(p2.team()), "reclaimed for resident team, never server");
        check(h, p2.claim().size() == spawnArea.size(), "all chunks claimed for resident");
        check(h, !IslandClaimRules.isHub(oldSpawn, hubChunks), "old spawn stays non-hub");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void newcomerOnFreshServerHubNeverGetsHubClaimed(GameTestHelper h) {
        Centre spawnAsHub = C0;
        List<Chunk> hubChunks = IslandClaimRules.area(spawnAsHub, 0, List.of());

        // On fresh server, spawn island is the hub
        check(h, IslandClaimRules.isHub(spawnAsHub, hubChunks), "spawn island is recorded as hub");

        // Newcomer standing on spawn island before sync
        UUID newcomer = UUID.randomUUID();
        TeamSyncRules.Islands spawnIsland = new TeamSyncRules.Islands() {
            @Override
            public @Nullable UUID islandOf(UUID player) {
                return TeamSyncRules.SPAWN;
            }

            @Override
            public Set<UUID> members(UUID isl) {
                return Set.of(newcomer);
            }
        };

        // ownIslandForEveryone moves them to a fresh island immediately
        List<TeamSyncRules.Action> actions = TeamSyncRules.newcomer(true, true, newcomer, spawnIsland);
        check(h, actions.size() == 1 && actions.get(0) instanceof TeamSyncRules.FreshIsland,
                "newcomer on hub gets fresh island");

        // And isHub check prevents any team claim on hub chunks
        check(h, IslandClaimRules.isHub(spawnAsHub, hubChunks), "hub chunks recognized and protected from team claims");

        h.succeed();
    }
}
