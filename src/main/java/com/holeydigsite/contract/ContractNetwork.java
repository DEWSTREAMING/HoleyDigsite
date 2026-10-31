package com.holeydigsite.contract;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The client asks the server to bring its contracts up to date when the board shows cards from an earlier day, and sends
 * right-clicks on a card whose text hangs over a block next to the board (the click would otherwise go to that block).
 * The server checks that the player really is next to a board.
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

    public record UseCardPayload(BlockPos pos, int slot) implements CustomPacketPayload {
        public static final Type<UseCardPayload> TYPE = new Type<>(HoleyDigsite.id("contract_use_card"));
        public static final StreamCodec<FriendlyByteBuf, UseCardPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, UseCardPayload::pos, ByteBufCodecs.VAR_INT, UseCardPayload::slot, UseCardPayload::new);

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
        registrar.playToServer(UseCardPayload.TYPE, UseCardPayload.STREAM_CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                useCardAt(sp, payload.pos(), payload.slot());
            }
        }));
    }

    /** Uses card {@code slot} of the board at {@code pos} as a right-click on the board would, if it is within reach. */
    public static boolean useCardAt(ServerPlayer sp, BlockPos pos, int slot) {
        if (slot < 0 || slot >= ContractDefs.rules().slots() || !boardInReach(sp, pos) || !sp.canInteractWithBlock(pos, 1.0)) {
            return false;
        }
        ItemStack held = sp.getMainHandItem();
        if (held.isEmpty()) {
            ContractBoardBlock.showCard(sp, slot);
        } else {
            ContractBoardBlock.deliver(sp, pos, slot, held);
        }
        return true;
    }

    private static boolean boardInReach(ServerPlayer sp, BlockPos pos) {
        return sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH * REACH
                && sp.level().isLoaded(pos) && sp.level().getBlockState(pos).getBlock() instanceof ContractBoardBlock;
    }

    /** Refreshes the player's contracts if a board is at {@code pos} within reach. */
    public static boolean refreshAt(ServerPlayer sp, BlockPos pos) {
        return boardInReach(sp, pos) && ContractManager.refresh(sp);
    }
}
