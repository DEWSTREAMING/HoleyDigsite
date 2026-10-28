package com.holeydigsite.compat.teams;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.team.TeamConfig;
import com.holeydigsite.team.TeamSyncRules;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.melanx.skyblockbuilder.data.SkyblockSavedData;
import de.melanx.skyblockbuilder.data.Team;
import de.melanx.skyblockbuilder.events.SkyblockCreateTeamEvent;
import de.melanx.skyblockbuilder.util.WorldUtil;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.event.PlayerChangedTeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps Skyblock Builder island teams following FTB Teams parties. Decisions come from {@link TeamSyncRules}; this class
 * only reads both mods and carries the actions out. Nothing is deleted: old islands stay, inventories stay.
 */
final class FtbTeamSync {
    private FtbTeamSync() {}

    /** Skyblock Builder's first-join marker in the player's persistent data. */
    private static final String SBB_SPAWNED = "alreadySpawned";

    /** Set while this class changes teams itself, so the events that causes are not acted on again. */
    private static boolean busy;

    static void register() {
        TeamEvent.PLAYER_CHANGED.register(FtbTeamSync::onPlayerChanged);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, FtbTeamSync::onLogin);
        NeoForge.EVENT_BUS.addListener(FtbTeamSync::onIslandCommand);
    }

    // ---- events ----

    private static void onPlayerChanged(PlayerChangedTeamEvent event) {
        if (busy || !TeamConfig.syncEnabled() || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        MinecraftServer server = FTBTeamsAPI.api().getManager().getServer();
        if (!skyblock(server)) {
            return;
        }
        UUID id = event.getPlayerId();
        dev.ftb.mods.ftbteams.api.Team now = event.getTeam();
        boolean created = now.isPartyTeam() && id.equals(now.getOwner())
                && event.getPreviousTeam().map(dev.ftb.mods.ftbteams.api.Team::isPlayerTeam).orElse(true);
        Sides s = sides(server);
        apply(server, TeamSyncRules.reconcile(true, id, created, s, s));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        // read before Skyblock Builder's own login handler (lowest priority) sets it
        boolean newcomer = !sp.getPersistentData().getBoolean(SBB_SPAWNED);
        MinecraftServer server = sp.getServer();
        UUID id = sp.getUUID();
        server.tell(new TickTask(server.getTickCount(), () -> afterLogin(server, id, newcomer)));
    }

    private static void afterLogin(MinecraftServer server, UUID id, boolean newcomer) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        if (sp == null || !TeamConfig.syncEnabled() || !FTBTeamsAPI.api().isManagerLoaded() || !skyblock(server)) {
            return;
        }
        Sides s = sides(server);
        if (newcomer) {
            apply(server, TeamSyncRules.newcomer(true, id, s));
        }
        apply(server, TeamSyncRules.reconcile(true, id, false, s, s));
        if (TeamMoves.get(server).take(id)) {
            Team team = s.teamOf(id);
            if (team != null) {
                sendHome(sp, team);
                sp.sendSystemMessage(Component.translatable("message.holeydigsite.team.moved_offline").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** Skyblock Builder's create command: the team exists only after the event, so look one tick later. */
    private static void onIslandCommand(SkyblockCreateTeamEvent event) {
        if (busy || !TeamConfig.syncEnabled() || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        MinecraftServer server = FTBTeamsAPI.api().getManager().getServer();
        String name = event.getName();
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (!skyblock(server)) {
                return;
            }
            Team team = data(server).getTeam(name);
            if (team != null) {
                Sides s = sides(server);
                apply(server, TeamSyncRules.islandCreated(true, team.getId(), s, s));
            }
        }));
    }

    // ---- actions ----

    private static void apply(MinecraftServer server, List<TeamSyncRules.Action> actions) {
        if (actions.isEmpty()) {
            return;
        }
        boolean was = busy;
        busy = true;
        try {
            for (TeamSyncRules.Action a : actions) {
                switch (a) {
                    case TeamSyncRules.Move m -> move(server, m.player(), m.island(), "message.holeydigsite.team.moved");
                    case TeamSyncRules.FreshIsland f -> freshIsland(server, f.player());
                    case TeamSyncRules.MakeParty p -> makeParty(server, p.player(), p.island());
                }
            }
        } finally {
            busy = was;
        }
    }

    private static void move(MinecraftServer server, UUID player, UUID island, String message) {
        SkyblockSavedData data = data(server);
        Team target = data.getTeam(island);
        if (target == null) {
            return;
        }
        data.removePlayerFromTeam(player);
        if (!target.hasPlayer(player)) {
            data.addPlayerToTeam(target, player);
        }
        HoleyDigsite.LOGGER.info("Team sync: {} -> island team {} ({})", player, target.getName(), target.getId());
        ServerPlayer sp = server.getPlayerList().getPlayer(player);
        if (sp != null) {
            sendHome(sp, target);
            sp.sendSystemMessage(Component.translatable(message, target.getName()).withStyle(ChatFormatting.GOLD));
        } else {
            TeamMoves.get(server).add(player);
        }
    }

    private static void freshIsland(MinecraftServer server, UUID player) {
        SkyblockSavedData data = data(server);
        String base = nameOf(server, player);
        String name = base;
        for (int i = 2; data.teamExists(name); i++) {
            name = base + " " + i;
        }
        Team team = data.createTeam(name);
        if (team == null) {
            HoleyDigsite.LOGGER.warn("Team sync: could not make an island for {}", player);
            return;
        }
        move(server, player, team.getId(), "message.holeydigsite.team.fresh");
    }

    private static void makeParty(MinecraftServer server, UUID player, UUID island) {
        ServerPlayer sp = server.getPlayerList().getPlayer(player);
        Team team = data(server).getTeam(island);
        if (sp == null || team == null) {
            return;
        }
        try {
            FTBTeamsAPI.api().getManager().createPartyTeam(sp, team.getName(), "", Color4I.rgb(0xC8A040));
            sp.sendSystemMessage(Component.translatable("message.holeydigsite.team.party_made", team.getName()).withStyle(ChatFormatting.GOLD));
        } catch (CommandSyntaxException e) {
            HoleyDigsite.LOGGER.warn("Team sync: no party for {}: {}", sp.getGameProfile().getName(), e.getMessage());
        }
    }

    /** Teleports to the island and points the respawn there, so a bed on the old island doesn't pull them back. */
    private static void sendHome(ServerPlayer sp, Team team) {
        sp.setRespawnPosition(sp.serverLevel().dimension(), null, 0, false, false);
        WorldUtil.teleportToIsland(sp, team);
    }

    // ---- reading both mods ----

    private static boolean skyblock(MinecraftServer server) {
        ServerLevel level = WorldUtil.getConfiguredLevel(server);
        return level != null && WorldUtil.isSkyblock(level);
    }

    private static SkyblockSavedData data(MinecraftServer server) {
        return SkyblockSavedData.get(WorldUtil.getConfiguredLevel(server));
    }

    private static String nameOf(MinecraftServer server, UUID player) {
        ServerPlayer sp = server.getPlayerList().getPlayer(player);
        if (sp != null) {
            return sp.getGameProfile().getName();
        }
        return server.getProfileCache() == null ? "Island"
                : server.getProfileCache().get(player).map(com.mojang.authlib.GameProfile::getName).orElse("Island");
    }

    private static Sides sides(MinecraftServer server) {
        return new Sides(data(server), FTBTeamsAPI.api().getManager());
    }

    /** Both teams as {@link TeamSyncRules} sees them. */
    private record Sides(SkyblockSavedData data, TeamManager ftb) implements TeamSyncRules.Islands, TeamSyncRules.Parties {
        @Override
        public @Nullable UUID islandOf(UUID player) {
            return SkyblockTeamAccess.teamIdOf(data, player);
        }

        @Override
        public Set<UUID> members(UUID island) {
            Team t = data.getTeam(island);
            return t == null ? Set.of() : SkyblockTeamAccess.players(t);
        }

        @Override
        public @Nullable UUID partyOf(UUID player) {
            return ftb.getTeamForPlayerID(player).filter(dev.ftb.mods.ftbteams.api.Team::isPartyTeam)
                    .map(dev.ftb.mods.ftbteams.api.Team::getId).orElse(null);
        }

        @Override
        public @Nullable UUID ownerOf(UUID party) {
            return ftb.getTeamByID(party).map(dev.ftb.mods.ftbteams.api.Team::getOwner).orElse(null);
        }

        @Nullable
        Team teamOf(UUID player) {
            UUID id = islandOf(player);
            return id == null ? null : data.getTeam(id);
        }
    }
}
