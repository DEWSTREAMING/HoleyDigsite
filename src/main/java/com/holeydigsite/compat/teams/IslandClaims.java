package com.holeydigsite.compat.teams;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.team.IslandClaimData;
import com.holeydigsite.team.IslandClaimRules;
import com.holeydigsite.team.IslandClaimRules.Centre;
import com.holeydigsite.team.IslandClaimRules.Chunk;
import com.holeydigsite.team.TeamConfig;
import de.melanx.skyblockbuilder.data.SkyblockSavedData;
import de.melanx.skyblockbuilder.data.Team;
import de.melanx.skyblockbuilder.events.SkyblockCreateTeamEvent;
import de.melanx.skyblockbuilder.util.WorldUtil;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Claims each island's chunks in FTB Chunks for the team that holds the island. Decisions come from
 * {@link IslandClaimRules}; this class reads both mods and carries them out. It only ever claims. When parties change,
 * FTB Chunks itself moves the claims with the players, and the island is looked at again here a tick later.
 */
final class IslandClaims {
    private IslandClaims() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(IslandClaims::onLogin);
        NeoForge.EVENT_BUS.addListener(IslandClaims::onIslandCommand);
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
    static void later(MinecraftServer server, Collection<UUID> islands) {
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
        if (record.isDone(islandId)) {
            return;
        }
        SkyblockSavedData data = data(server);
        Team island = data.getTeam(islandId);
        if (island == null || island.getIsland() == null) {
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
        IslandClaimRules.Plan plan = IslandClaimRules.plan(true, islandId, false, area,
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
        if (plan.team() == null && !plan.finished()) {
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

    private static Centre centre(Team t) {
        BlockPos c = t.getIsland().getCenter();
        return new Centre(c.getX(), c.getZ());
    }
}
