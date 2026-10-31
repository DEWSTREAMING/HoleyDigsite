package com.holeydigsite.guide;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.site.Sites;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles giving the Field Guide item on first join or to existing players,
 * auto-opening the guide once after the starting island is ready, and
 * dispatching open packets from the item or the Ledger button.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class FieldGuideManager {
    private static final Map<UUID, Integer> OPEN_COUNTS = new ConcurrentHashMap<>();

    private FieldGuideManager() {}

    /**
     * Gives the Field Guide once to a player who has not received one yet
     * (either on first join or when an existing player joins an upgraded world).
     */
    public static void checkStarterFieldGuide(ServerPlayer sp, LedgerData data) {
        if (data.starterFieldGuideGiven()) return;
        data.setStarterFieldGuideGiven(true);
        if (!hasFieldGuide(sp)) {
            ItemStack guide = new ItemStack(ModItems.FIELD_GUIDE.get());
            if (!sp.getInventory().add(guide)) {
                sp.drop(guide, false);
            }
        }
    }

    public static boolean hasFieldGuide(ServerPlayer sp) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            if (sp.getInventory().getItem(i).is(ModItems.FIELD_GUIDE.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Opens the Field Guide automatically the first time a player joins a world,
     * waiting until any queued starting-island site markers have finished building.
     */
    public static boolean checkFirstJoinOpen(ServerPlayer sp) {
        LedgerData data = LedgerData.get(sp);
        if (data.fieldGuideOpened()) {
            return false;
        }
        if (!Sites.isIslandReady(sp.serverLevel())) {
            return false;
        }
        data.setFieldGuideOpened(true);
        if (sp.connection != null) {
            sp.sendSystemMessage(Component.translatable("chat.holeydigsite.field_guide.first_join")
                    .withStyle(ChatFormatting.GOLD));
        }
        openForPlayer(sp);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 10 != 0) return;
        LedgerData data = LedgerData.get(sp);
        if (!data.fieldGuideOpened() && data.starterGiven()) {
            checkFirstJoinOpen(sp);
        }
    }

    public static void openFromItem(ServerPlayer sp) {
        openForPlayer(sp);
    }

    public static void openFromLedger(ServerPlayer sp) {
        openForPlayer(sp);
    }

    public static void openForPlayer(ServerPlayer sp) {
        OPEN_COUNTS.merge(sp.getUUID(), 1, Integer::sum);
        FieldGuideNetwork.sendOpen(sp);
    }

    public static int openCount(ServerPlayer sp) {
        return OPEN_COUNTS.getOrDefault(sp.getUUID(), 0);
    }

    public static void resetOpenCount(ServerPlayer sp) {
        OPEN_COUNTS.remove(sp.getUUID());
    }
}
