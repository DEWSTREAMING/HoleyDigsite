package com.holeydigsite.ledger;

import com.holeydigsite.HoleyDigsite;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Phase 21a: Networking for syncing {@link LedgerData} to the client and claiming level rewards from the Ledger screen.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class LedgerNetwork {
    private LedgerNetwork() {}

    public record SyncPayload(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(HoleyDigsite.id("ledger_sync"));
        public static final StreamCodec<ByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, SyncPayload::tag,
                SyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ClaimPayload(int level) implements CustomPacketPayload {
        public static final Type<ClaimPayload> TYPE = new Type<>(HoleyDigsite.id("ledger_claim"));
        public static final StreamCodec<ByteBuf, ClaimPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ClaimPayload::level,
                ClaimPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Phase 21b: the player picks one of their unlocked titles ({@code ""} = no title). */
    public record SelectTitlePayload(String title) implements CustomPacketPayload {
        public static final Type<SelectTitlePayload> TYPE = new Type<>(HoleyDigsite.id("ledger_title"));
        public static final StreamCodec<ByteBuf, SelectTitlePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), SelectTitlePayload::title,
                SelectTitlePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                Player player = ctx.player();
                if (player != null) {
                    LedgerData.get(player).deserializeNBT(player.registryAccess(), payload.tag());
                }
            });
        });
        registrar.playToServer(ClaimPayload.TYPE, ClaimPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    if (payload.level() == 0) {
                        LedgerManager.claimAll(sp);
                    } else {
                        LedgerManager.claim(sp, payload.level());
                    }
                }
            });
        });
        registrar.playToServer(SelectTitlePayload.TYPE, SelectTitlePayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    LedgerManager.selectTitle(sp, payload.title());
                }
            });
        });
    }

    public static void syncToClient(ServerPlayer player) {
        if (player.connection == null || !player.connection.hasChannel(SyncPayload.TYPE)) return;
        CompoundTag tag = LedgerData.get(player).serializeNBT(player.registryAccess());
        PacketDistributor.sendToPlayer(player, new SyncPayload(tag));
    }
}
