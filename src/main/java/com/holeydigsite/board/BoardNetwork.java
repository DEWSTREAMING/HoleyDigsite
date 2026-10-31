package com.holeydigsite.board;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** A client looking at a board asks for its table now and then (the numbers change without the board knowing). */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class BoardNetwork {
    private BoardNetwork() {}

    public static final double REACH = 16.0;

    public record RefreshPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<RefreshPayload> TYPE = new Type<>(HoleyDigsite.id("board_refresh"));
        public static final StreamCodec<FriendlyByteBuf, RefreshPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RefreshPayload::pos, RefreshPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(RefreshPayload.TYPE, RefreshPayload.STREAM_CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                BlockPos pos = payload.pos();
                if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH * REACH && sp.level().isLoaded(pos)
                        && sp.level().getBlockEntity(pos) instanceof ScoreBoardBlockEntity board) {
                    board.resend();
                }
            }
        }));
    }
}
