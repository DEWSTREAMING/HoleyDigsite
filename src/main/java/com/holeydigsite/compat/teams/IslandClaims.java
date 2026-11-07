package com.holeydigsite.compat.teams;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.hub.HubData;
import com.holeydigsite.team.IslandClaimData;
import com.holeydigsite.team.IslandClaimRules;
import com.holeydigsite.team.IslandClaimRules.Centre;
import com.holeydigsite.team.IslandClaimRules.Chunk;
import com.holeydigsite.team.TeamConfig;
import com.holeydigsite.team.TeamSyncRules;
import de.melanx.skyblockbuilder.config.common.TemplatesConfig;
import de.melanx.skyblockbuilder.data.SkyblockSavedData;
import de.melanx.skyblockbuilder.data.Team;
import de.melanx.skyblockbuilder.events.SkyblockCreateTeamEvent;
import de.melanx.skyblockbuilder.util.WorldUtil;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Claims each island's chunks in FTB Chunks for the team that holds the island. Decisions come from
 * {@link IslandClaimRules}; this class reads both mods and carries them out. When parties change,
 * FTB Chunks moves claims with players, and the islands are looked at again here a tick later.
 * Spawn islands in singleplayer / old worlds are claimed for whoever lives on them, and released when empty.
 * The server hub is whatever {@link HubData} records; only its chunks get a server claim, and never a team's.
 */
public final class IslandClaims {
    private IslandClaims() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(IslandClaims::onLogin);
        NeoForge.EVENT_BUS.addListener(IslandClaims::onIslandCommand);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, IslandClaims::onServerStarted);
    }

    /** A server made from the server pack: its spawn island is the hub, recorded before anyone has stood on it. */
    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        recordServerHubIfFresh(server);
        if (HubData.get(server).hasHub() && TeamConfig.claimEnabled()) {
            claimHub(server);
        }
    }

    static void recordServerHubIfFresh(MinecraftServer server) {
        ServerLevel level = WorldUtil.getConfiguredLevel(server);
        if (level == null || !WorldUtil.isSkyblock(level)) {
            return;
        }
        HubData hub = HubData.get(server);
        if (hub.hasHub() || !TemplatesConfig.mainSpawnIsland.isPresent()
                || IslandClaimData.get(server).isDone(TeamSyncRules.SPAWN)) {
            return;
        }
        SkyblockSavedData data = SkyblockSavedData.get(level);
        Team spawn = data.getSpawnOption().orElse(null);
        if (spawn == null) {
            spawn = data.getSpawn();
        }
        int residents = spawn == null ? 0 : (int) spawn.getPlayers().stream().filter(u -> !Util.NIL_UUID.equals(u)).count();
        boolean built = spawn != null && spawn.getIsland() != null;
        if (IslandClaimRules.recordSpawnAsHub(true, hub.hasHub(), built, residents,
                IslandClaimData.get(server).isDone(TeamSyncRules.SPAWN))) {
            Centre c = centre(spawn);
            hub.create(spawn.getIsland().getCenter(), level.getSharedSpawnPos(), IslandClaimRules.area(c, 0, List.of()));
            HoleyDigsite.LOGGER.info("Server hub: spawn island at {},{} recorded as the hub", c.x(), c.z());
        }
    }

    // ---- when ----

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !TeamConfig.claimEnabled()) {
            return;
        }
        MinecraftServer server = sp.getServer();
        UUID id = sp.getUUID();
        // a tick on, after the team sync has put the player where they belong
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (!ready(server)) {
                return;
            }
            recordServerHubIfFresh(server);
            if (HubData.get(server).hasHub()) {
                claimHub(server);
            }
            UUID island = SkyblockTeamAccess.teamIdOf(data(server), id);
            if (island != null) {
                ensure(server, island);
            }
        }));
    }

    private static void onIslandCommand(SkyblockCreateTeamEvent event) {
        if (!TeamConfig.claimEnabled() || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        MinecraftServer server = FTBTeamsAPI.api().getManager().getServer();
        String name = event.getName();
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (ready(server)) {
                Team team = data(server).getTeam(name);
                if (team != null) {
                    ensure(server, team.getId());
                }
            }
        }));
    }

    /** After a party change: look at these islands again one tick later, once FTB Chunks has moved its claims. */
    public static void later(MinecraftServer server, Collection<UUID> islands) {
        if (islands.isEmpty() || !TeamConfig.claimEnabled()) {
            return;
        }
        Set<UUID> ids = new LinkedHashSet<>(islands);
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (ready(server)) {
                ids.forEach(i -> ensure(server, i));
            }
        }));
    }

    // ---- doing ----

    private static boolean ready(MinecraftServer server) {
        if (!TeamConfig.claimEnabled() || !FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }
        ServerLevel level = WorldUtil.getConfiguredLevel(server);
        return level != null && WorldUtil.isSkyblock(level);
    }

    private static SkyblockSavedData data(MinecraftServer server) {
        return SkyblockSavedData.get(WorldUtil.getConfiguredLevel(server));
    }

    private static void ensure(MinecraftServer server, UUID islandId) {
        IslandClaimData record = IslandClaimData.get(server);
        boolean done = record.isDone(islandId);
        if (done && !TeamSyncRules.SPAWN.equals(islandId)) {
            return;
        }
        SkyblockSavedData data = data(server);
        Team island = data.getTeam(islandId);
        if (island == null || island.getIsland() == null) {
            return;
        }
        HubData hub = HubData.get(server);
        if (hub.hasHub() && IslandClaimRules.isHub(centre(island), hub.chunks())) {
            // a newcomer passing through the hub before their own island is ready never gets it claimed
            claimHub(server);
            return;
        }
        ServerLevel level = WorldUtil.getConfiguredLevel(server);
        List<Centre> others = new ArrayList<>();
        for (Team t : data.getTeams()) {
            if (t.getIsland() != null && !t.getId().equals(islandId)) {
                others.add(centre(t));
            }
        }
        List<Chunk> area = IslandClaimRules.area(centre(island), TeamConfig.claimMargin(), others);

        TeamManager ftb = FTBTeamsAPI.api().getManager();
        ClaimedChunkManager chunks = FTBChunksAPI.api().getManager();
        IslandClaimRules.Plan plan = IslandClaimRules.plan(true, islandId, done, area,
                new IslandClaimRules.Holders() {
                    @Override
                    public Set<UUID> members(UUID i) {
                        Team t = data.getTeam(i);
                        return t == null ? Set.of() : SkyblockTeamAccess.players(t);
                    }

                    @Override
                    public @Nullable UUID teamOf(UUID player) {
                        return ftb.getTeamForPlayerID(player).map(dev.ftb.mods.ftbteams.api.Team::getId).orElse(null);
                    }
                },
                c -> {
                    ClaimedChunk owner = chunks.getChunk(new ChunkDimPos(level.dimension(), c.x(), c.z()));
                    return owner == null ? null : owner.getTeamData().getTeam().getId();
                });

        if (!plan.unclaim().isEmpty()) {
            for (Chunk c : plan.unclaim()) {
                ClaimedChunk cc = chunks.getChunk(new ChunkDimPos(level.dimension(), c.x(), c.z()));
                if (cc != null) {
                    cc.unclaim(server.createCommandSourceStack(), false);
                }
            }
            record.release(islandId);
            HoleyDigsite.LOGGER.info("Island claim: {} chunks of spawn island released (island empty)", plan.unclaim().size());
            return;
        }

        if (plan.team() == null) {
            return;
        }
        dev.ftb.mods.ftbteams.api.Team owner = ftb.getTeamByID(plan.team()).orElse(null);
        if (owner == null) {
            return;
        }
        ChunkTeamData teamData = chunks.getOrCreateData(owner);
        List<Chunk> made = new ArrayList<>();
        for (Chunk c : plan.claim()) {
            ClaimResult r = teamData.claim(server.createCommandSourceStack(), new ChunkDimPos(level.dimension(), c.x(), c.z()), false);
            if (r.isSuccess()) {
                made.add(c);
            } else if (r.getResultId().contains("not_enough_power")) {
                HoleyDigsite.LOGGER.warn("Island claim: {} is at its chunk limit, {} of {} chunks claimed", owner.getShortName(), made.size(), plan.claim().size());
                break;
            } else {
                HoleyDigsite.LOGGER.info("Island claim: chunk {},{} not claimed: {}", c.x(), c.z(), r.getResultId());
            }
        }
        if (!plan.taken().isEmpty()) {
            HoleyDigsite.LOGGER.info("Island claim: {} chunks already belong to another team and were left alone", plan.taken().size());
        }
        record.finish(islandId, made);
        HoleyDigsite.LOGGER.info("Island claim: {} chunks of island {} claimed for {}", made.size(), island.getName(), owner.getShortName());
    }

    /** Server-claims every hub chunk {@link HubData} records. Safe to run again; a hub chunk a team holds is taken back. */
    public static void claimHub(MinecraftServer server) {
        if (!ready(server)) {
            return;
        }
        HubData hub = HubData.get(server);
        ServerLevel level = WorldUtil.getConfiguredLevel(server);
        if (!hub.hasHub() || level == null) {
            return;
        }
        TeamManager ftb = FTBTeamsAPI.api().getManager();
        ClaimedChunkManager chunks = FTBChunksAPI.api().getManager();
        dev.ftb.mods.ftbteams.api.Team serverTeam = ftb.getTeamByName("Server").orElse(null);
        if (serverTeam == null) {
            try {
                serverTeam = ftb.createServerTeam(server.createCommandSourceStack(), "Server", "Server", Color4I.rgb(0x4080C0));
            } catch (Exception e) {
                HoleyDigsite.LOGGER.warn("Server hub: could not make the Server team for the hub claim: {}", e.getMessage());
                return;
            }
        }
        UUID serverId = serverTeam.getId();
        ChunkTeamData teamData = chunks.getOrCreateData(serverTeam);
        int made = 0;
        for (Chunk c : hub.chunks()) {
            ChunkDimPos pos = new ChunkDimPos(level.dimension(), c.x(), c.z());
            ClaimedChunk cc = chunks.getChunk(pos);
            if (cc != null && serverId.equals(cc.getTeamData().getTeam().getId())) {
                continue;
            }
            if (cc != null) {
                cc.unclaim(server.createCommandSourceStack(), false);
            }
            if (teamData.claim(server.createCommandSourceStack(), pos, false).isSuccess()) {
                made++;
            }
        }
        if (made > 0) {
            HoleyDigsite.LOGGER.info("Server hub: {} chunks claimed for the server", made);
        }
    }

    private static Centre centre(Team t) {
        BlockPos c = t.getIsland().getCenter();
        return new Centre(c.getX(), c.getZ());
    }
}
