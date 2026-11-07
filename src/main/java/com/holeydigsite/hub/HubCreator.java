package com.holeydigsite.hub;

import com.holeydigsite.compat.teams.IslandClaims;
import com.holeydigsite.team.IslandClaimRules;
import com.holeydigsite.team.IslandClaimRules.Centre;
import com.holeydigsite.team.TeamConfig;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import de.melanx.skyblockbuilder.config.common.TemplatesConfig;
import de.melanx.skyblockbuilder.config.common.WorldConfig;
import de.melanx.skyblockbuilder.data.SkyblockSavedData;
import de.melanx.skyblockbuilder.data.Team;
import de.melanx.skyblockbuilder.util.WorldUtil;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class HubCreator {
    private HubCreator() {}

    record GridPos(int gx, int gz, int distance) {}

    static GridPos findFreeGrid(ServerLevel level) {
        int distance = WorldConfig.islandDistance;
        SkyblockSavedData sbb = SkyblockSavedData.get(level);
        Set<String> occupied = new HashSet<>();
        for (Team t : sbb.getTeams()) {
            if (t.getIsland() != null) {
                BlockPos c = t.getIsland().getCenter();
                int gx = Math.round((float) c.getX() / distance);
                int gz = Math.round((float) c.getZ() / distance);
                occupied.add(gx + "," + gz);
            }
        }
        for (int r = 1; r < 100; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) == r) {
                        if (!occupied.contains(x + "," + z)) {
                            return new GridPos(x, z, distance);
                        }
                    }
                }
            }
        }
        return new GridPos(0, 1, distance);
    }

    static int preview(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        HubData hub = HubData.get(server);
        if (hub.hasHub()) {
            BlockPos l = hub.hubLanding();
            source.sendFailure(Component.translatable("command.holeydigsite.hub.already_exists", l.getX(), l.getY(), l.getZ()));
            return 0;
        }
        ServerLevel level = WorldUtil.getConfiguredLevel(server);
        if (level == null) {
            source.sendFailure(Component.literal("Skyblock world not found."));
            return 0;
        }
        GridPos grid = findFreeGrid(level);
        BlockPos anchor = new BlockPos(grid.gx() * grid.distance() - 15, 64, grid.gz() * grid.distance() - 15);
        BlockPos landing = anchor.offset(20, 10, 20);

        SkyblockSavedData sbb = SkyblockSavedData.get(level);
        Team spawnTeam = sbb.getSpawnOption().orElse(null);
        Set<UUID> residents = new HashSet<>();
        if (spawnTeam != null) {
            residents.addAll(spawnTeam.getPlayers());
            residents.remove(Util.NIL_UUID);
        }
        List<String> names = new ArrayList<>();
        for (UUID u : residents) {
            ServerPlayer sp = server.getPlayerList().getPlayer(u);
            if (sp != null) {
                names.add(sp.getGameProfile().getName());
            } else if (server.getProfileCache() != null) {
                names.add(server.getProfileCache().get(u).map(GameProfile::getName).orElse(u.toString()));
            } else {
                names.add(u.toString());
            }
        }
        String resList = names.isEmpty() ? "none" : String.join(", ", names);

        source.sendSuccess(() -> Component.translatable("command.holeydigsite.hub.preview_pos", landing.getX(), landing.getY(), landing.getZ(), grid.gx(), grid.gz()), false);
        source.sendSuccess(() -> Component.translatable("command.holeydigsite.hub.preview_residents", resList), false);
        source.sendSuccess(() -> Component.translatable("command.holeydigsite.hub.preview_backup"), false);
        source.sendSuccess(() -> Component.translatable("command.holeydigsite.hub.preview_confirm"), false);
        return 1;
    }

    static int confirm(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        HubData hub = HubData.get(server);
        if (hub.hasHub()) {
            BlockPos l = hub.hubLanding();
            source.sendFailure(Component.translatable("command.holeydigsite.hub.already_exists", l.getX(), l.getY(), l.getZ()));
            return 0;
        }
        ServerLevel level = WorldUtil.getConfiguredLevel(server);
        if (level == null) {
            source.sendFailure(Component.literal("Skyblock world not found."));
            return 0;
        }
        GridPos grid = findFreeGrid(level);
        BlockPos anchor = new BlockPos(grid.gx() * grid.distance() - 15, 64, grid.gz() * grid.distance() - 15);
        BlockPos landing = anchor.offset(20, 10, 20);

        Optional<StructureTemplate> template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("holeydigsite", "server_hub"));
        if (template.isEmpty()) {
            source.sendFailure(Component.literal("Structure holeydigsite:server_hub not found."));
            return 0;
        }

        template.get().placeInWorld(level, anchor, anchor, new StructurePlaceSettings().setIgnoreEntities(true), level.random, Block.UPDATE_CLIENTS);

        level.setDefaultSpawnPos(landing, 0);

        SkyblockSavedData sbb = SkyblockSavedData.get(level);
        Team spawnTeam = sbb.getSpawnOption().orElse(null);
        if (spawnTeam != null) {
            spawnTeam.setPossibleSpawns(List.of(new TemplatesConfig.Spawn(landing, WorldUtil.SpawnDirection.SOUTH)));
        }

        Centre hubCenter = new Centre(grid.gx() * grid.distance(), grid.gz() * grid.distance());
        hub.create(anchor, landing, IslandClaimRules.area(hubCenter, 0, List.of()));
        IslandClaims.claimHub(server);

        TeamConfig.OWN_ISLAND_FOR_EVERYONE.set(true);
        TeamConfig.SPEC.save();

        if (spawnTeam != null) {
            IslandClaims.later(server, List.of(spawnTeam.getId()));
        }

        source.sendSuccess(() -> Component.translatable("command.holeydigsite.hub.created", landing.getX(), landing.getY(), landing.getZ()), true);
        return 1;
    }
}
