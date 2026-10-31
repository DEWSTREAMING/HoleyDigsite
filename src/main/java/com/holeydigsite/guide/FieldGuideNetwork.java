package com.holeydigsite.guide;

import com.holeydigsite.HoleyDigsite;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads for opening the Field Guide from the server (first join, item, or Ledger button).
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class FieldGuideNetwork {
    private FieldGuideNetwork() {}

    public record OpenPayload() implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(HoleyDigsite.id("field_guide_open"));
        public static final OpenPayload INSTANCE = new OpenPayload();
        public static final StreamCodec<ByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestOpenPayload() implements CustomPacketPayload {
        public static final Type<RequestOpenPayload> TYPE = new Type<>(HoleyDigsite.id("field_guide_request_open"));
        public static final RequestOpenPayload INSTANCE = new RequestOpenPayload();
        public static final StreamCodec<ByteBuf, RequestOpenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1").optional();
        reg.playToClient(
                OpenPayload.TYPE,
                OpenPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(FieldGuideClientHooks::openScreen)
        );
        reg.playToServer(
                RequestOpenPayload.TYPE,
                RequestOpenPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        FieldGuideManager.openFromLedger(sp);
                    }
                })
        );
    }

    public static void sendOpen(ServerPlayer player) {
        if (player.connection != null && player.connection.hasChannel(OpenPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, OpenPayload.INSTANCE);
        }
    }

    public static void sendRequestOpenToServer() {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(RequestOpenPayload.INSTANCE);
        }
    }
}
