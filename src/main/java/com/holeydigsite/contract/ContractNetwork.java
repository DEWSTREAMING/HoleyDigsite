package com.holeydigsite.contract;

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

/**
 * The client asks the server to bring its contracts up to date when the board shows cards from an earlier day. The server
 * checks that the player really is next to a board.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class ContractNetwork {
    private ContractNetwork() {}

    public static final double REACH = 10.0;

    public record RefreshPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<RefreshPayload> TYPE = new Type<>(HoleyDigsite.id("contract_refresh"));
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
                refreshAt(sp, payload.pos());
            }
        }));
    }

    /** Refreshes the player's contracts if a board is at {@code pos} within reach. */
    public static boolean refreshAt(ServerPlayer sp, BlockPos pos) {
        if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH * REACH
                || !sp.level().isLoaded(pos) || !(sp.level().getBlockState(pos).getBlock() instanceof ContractBoardBlock)) {
            return false;
        }
        return ContractManager.refresh(sp);
    }
}
