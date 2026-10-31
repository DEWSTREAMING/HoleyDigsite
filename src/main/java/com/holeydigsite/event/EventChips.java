package com.holeydigsite.event;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.board.BoardStats;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * Opal Chips on the server: earning (the event Sell Pad only), spending (only chips of the active event) and the client sync.
 * Chips of an ended event stay on the player and work again if that event comes back.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class EventChips {
    private EventChips() {}

    public record SyncPayload(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(HoleyDigsite.id("opal_chips_sync"));
        public static final StreamCodec<ByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, SyncPayload::tag, SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @Nullable
    public static String active() {
        return EventDefs.getActiveEventId();
    }

    /** The player's chips of the active event (0 when no event runs). */
    public static long balance(Player player) {
        String a = active();
        return a == null ? 0 : OpalChips.get(player).balance(a);
    }

    /** Pays chips for the active event, adds to the total earned and the event board. Returns what was added. */
    public static long earn(ServerPlayer sp, long chips) {
        String a = active();
        if (a == null || chips <= 0) {
            return 0;
        }
        OpalChips.get(sp).add(a, chips);
        BoardStats.get(sp.server).addChips(sp, a, chips);
        sync(sp);
        return chips;
    }

    /** Spends chips of the active event. False when no event runs or the balance is short (nothing is taken then). */
    public static boolean spend(ServerPlayer sp, long chips) {
        String a = active();
        if (a == null) {
            return false;
        }
        boolean ok = OpalChips.get(sp).take(a, chips);
        if (ok) {
            sync(sp);
        }
        return ok;
    }

    public static void sync(ServerPlayer sp) {
        if (sp.connection == null || !sp.connection.hasChannel(SyncPayload.TYPE)) {
            return;
        }
        CompoundTag tag = OpalChips.get(sp).serializeNBT(sp.registryAccess());
        String a = active();
        tag.putString("Active", a == null ? "" : a);
        PacketDistributor.sendToPlayer(sp, new SyncPayload(tag));
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player != null) {
                OpalChips chips = OpalChips.get(player);
                chips.deserializeNBT(player.registryAccess(), payload.tag());
                chips.setActive(payload.tag().getString("Active"));
            }
        }));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            BoardStats stats = BoardStats.get(sp.server);
            stats.name(sp.getUUID(), sp.getGameProfile().getName());
            stats.seedCoins(sp.getUUID(), com.holeydigsite.ledger.LedgerData.get(sp).lifetimeCopper());
            sync(sp);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }
}
