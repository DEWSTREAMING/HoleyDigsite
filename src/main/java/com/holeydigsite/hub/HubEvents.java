package com.holeydigsite.hub;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.teams.TeamsCompat;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;

import java.util.Locale;

/**
 * Intercepts {@code /sky spawn} when a server hub has been created, routing players to the hub landing.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class HubEvents {
    private HubEvents() {}

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        if (parse == null || parse.getContext() == null) {
            return;
        }
        String input = parse.getReader().getString().trim();
        if (input.startsWith("/")) {
            input = input.substring(1).trim();
        }
        if ("sky spawn".equalsIgnoreCase(input) || input.toLowerCase(Locale.ROOT).startsWith("sky spawn ")) {
            CommandSourceStack source = parse.getContext().getSource();
            if (source.getEntity() instanceof ServerPlayer player) {
                MinecraftServer server = source.getServer();
                HubData hub = HubData.get(server);
                if (hub.hasHub()) {
                    event.setCanceled(true);
                    BlockPos landing = hub.hubLanding();
                    ServerLevel level = server.overworld();
                    if (TeamsCompat.skyblockBuilder()) {
                        ServerLevel sbbLevel = SkyblockLevelHelper.getLevel(server);
                        if (sbbLevel != null) {
                            level = sbbLevel;
                        }
                    }
                    player.teleportTo(level, landing.getX() + 0.5, landing.getY() + 0.2, landing.getZ() + 0.5, 0f, 0f);
                    source.sendSuccess(() -> Component.translatable("message.holeydigsite.hub.teleport"), false);
                }
            }
        }
    }

    private static final class SkyblockLevelHelper {
        static ServerLevel getLevel(MinecraftServer server) {
            return de.melanx.skyblockbuilder.util.WorldUtil.getConfiguredLevel(server);
        }
    }
}
