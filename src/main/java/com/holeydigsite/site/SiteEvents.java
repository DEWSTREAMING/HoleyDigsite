package com.holeydigsite.site;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Wires up site tables, world load checks, store updated messages on login, and commands. */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class SiteEvents {
    private SiteEvents() {}

    /** Player data key: the last announcement serial this player has seen. */
    public static final String SEEN = "holeydigsite:seen_announcement";

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SiteDefs.Loader());
    }

    /** Handles world load by running content version upgrades first, then checking versions and refreshing layouts for every site. */
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        WorldVersion.upgrade(server);
        for (ServerLevel level : server.getAllLevels()) {
            Sites.checkAll(level);
            com.holeydigsite.event.EventIsland.checkEventChange(level);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel().getServer().overworld();
        for (ServerLevel l : level.getServer().getAllLevels()) {
            com.holeydigsite.plot.PlotData.get(l).syncOwner(player.getUUID(), player.getGameProfile().getName());
        }
        CompoundTag pd = player.getPersistentData();
        int seen = pd.getInt(SEEN);
        int last = seen;
        for (ServerLevel l : level.getServer().getAllLevels()) {
            SiteData data = SiteData.getIfExists(l);
            if (data == null) {
                continue;
            }
            for (SiteData.Announcement a : data.announcements()) {
                if (a.serial() > seen && l.getGameTime() - a.time() <= SiteData.ANNOUNCE_TICKS) {
                    player.sendSystemMessage(a.text());
                }
                last = Math.max(last, a.serial());
            }
        }
        pd.putInt(SEEN, last);
    }

    /** Builds Site Markers queued since the last tick (see {@link Sites#queueMarker}). */
    @SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Sites.buildQueued(level);
            if (Sites.isIslandReady(level)) {
                for (ServerPlayer sp : level.players()) {
                    com.holeydigsite.ledger.LedgerData data = com.holeydigsite.ledger.LedgerData.get(sp);
                    if (data.starterGiven() && !data.fieldGuideOpened()) {
                        com.holeydigsite.guide.FieldGuideManager.checkFirstJoinOpen(sp);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        com.holeydigsite.command.ModCommands.register(event.getDispatcher());
    }
}
