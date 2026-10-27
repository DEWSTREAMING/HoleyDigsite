package com.holeydigsite.crate;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/**
 * Crate spin messages. The server tells the opener the strip and the result when the crate opens; the reward itself is
 * given later (see {@link PendingCrates}) and its text follows in a {@link ResultPayload}. A click to skip is answered by
 * giving the reward at once.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class CrateNetwork {
    private CrateNetwork() {}

    public record OpenPayload(BlockPos pos, ItemStack icon, List<ItemStack> strip, List<String> rarities,
                              int resultIndex, float stopFrac) implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(HoleyDigsite.id("crate_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenPayload::pos,
                ItemStack.STREAM_CODEC, OpenPayload::icon,
                ItemStack.LIST_STREAM_CODEC, OpenPayload::strip,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenPayload::rarities,
                ByteBufCodecs.VAR_INT, OpenPayload::resultIndex,
                ByteBufCodecs.FLOAT, OpenPayload::stopFrac,
                OpenPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The reward was given: this is the result line for the overlay and the chat. */
    public record ResultPayload(BlockPos pos, Component line) implements CustomPacketPayload {
        public static final Type<ResultPayload> TYPE = new Type<>(HoleyDigsite.id("crate_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResultPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ResultPayload::pos,
                ComponentSerialization.STREAM_CODEC, ResultPayload::line,
                ResultPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The player clicked to skip the spin. */
    public record SkipPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<SkipPayload> TYPE = new Type<>(HoleyDigsite.id("crate_skip"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SkipPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SkipPayload::pos,
                SkipPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(OpenPayload.TYPE, OpenPayload.STREAM_CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                com.holeydigsite.client.CrateSpinOverlay.start(payload);
            }
        }));
        registrar.playToClient(ResultPayload.TYPE, ResultPayload.STREAM_CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                com.holeydigsite.client.CrateSpinOverlay.result(payload);
            }
        }));
        registrar.playToServer(SkipPayload.TYPE, SkipPayload.STREAM_CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.getServer() != null) {
                skip(sp, payload.pos());
            }
        }));
    }

    /** Gives the held reward of the crate at {@code pos} if it is this player's. */
    public static boolean skip(ServerPlayer sp, BlockPos pos) {
        PendingCrates.Held h = PendingCrates.get(sp.getServer()).at(sp.serverLevel(), pos);
        if (h == null || !h.player.equals(sp.getUUID()) || h.granted) return false;
        return PendingCrates.grant(sp.getServer(), h);
    }

    public static void send(ServerPlayer player, OpenPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(OpenPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendResult(ServerPlayer player, ResultPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(ResultPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, payload);
    }
}
