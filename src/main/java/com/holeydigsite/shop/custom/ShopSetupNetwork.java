package com.holeydigsite.shop.custom;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Networking of the operator's shop display setup screen. The server opens it ({@link OpenPayload}) and the client
 * answers with {@link SavePayload}. Every save re-checks permission, distance and the display on the server.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class ShopSetupNetwork {
    private ShopSetupNetwork() {}

    /** Open the screen for the display at {@code pos}. */
    public record OpenPayload(BlockPos pos, ItemStack template, boolean hasEntry, int count, long price, long buyBack)
            implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(HoleyDigsite.id("shop_setup_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenPayload::pos,
                ItemStack.OPTIONAL_STREAM_CODEC, OpenPayload::template,
                ByteBufCodecs.BOOL, OpenPayload::hasEntry,
                ByteBufCodecs.VAR_INT, OpenPayload::count,
                ByteBufCodecs.VAR_LONG, OpenPayload::price,
                ByteBufCodecs.VAR_LONG, OpenPayload::buyBack,
                OpenPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Save the entry or clear it. */
    public record SavePayload(BlockPos pos, boolean clear, ItemStack template, int count, long price, long buyBack)
            implements CustomPacketPayload {
        public static final Type<SavePayload> TYPE = new Type<>(HoleyDigsite.id("shop_setup_save"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SavePayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SavePayload::pos,
                ByteBufCodecs.BOOL, SavePayload::clear,
                ItemStack.OPTIONAL_STREAM_CODEC, SavePayload::template,
                ByteBufCodecs.VAR_INT, SavePayload::count,
                ByteBufCodecs.VAR_LONG, SavePayload::price,
                ByteBufCodecs.VAR_LONG, SavePayload::buyBack,
                SavePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(OpenPayload.TYPE, OpenPayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> ShopSetupClientHooks.open(payload)));
        registrar.playToServer(SavePayload.TYPE, SavePayload.STREAM_CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        handleSave(sp, payload);
                    }
                }));
    }

    /** Opens the setup screen for an op at the display {@code pos}. */
    public static void open(ServerPlayer op, BlockPos pos, ItemStack held) {
        ServerLevel level = op.serverLevel();
        if (!CustomShops.isOp(op) || !(level.getBlockEntity(pos) instanceof ShopDisplayBlockEntity display) || display.item() != null) {
            return;
        }
        CustomEntry e = display.custom();
        OpenPayload payload = e != null
                ? new OpenPayload(pos, e.template(), true, e.count(), e.price(), e.buyBack())
                : new OpenPayload(pos, held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1), false, 1, 0, 0);
        if (op.connection != null && op.connection.hasChannel(OpenPayload.TYPE)) {
            PacketDistributor.sendToPlayer(op, payload);
        }
    }

    /** The server side of save or clear, made public for tests. */
    public static CustomShops.Result handleSave(ServerPlayer sp, SavePayload p) {
        if (!CustomShops.isOp(sp)) {
            sp.displayClientMessage(Component.translatable("message.holeydigsite.display.not_op").withStyle(ChatFormatting.RED), true);
            return CustomShops.Result.NO_DISPLAY;
        }
        ServerLevel level = sp.serverLevel();
        if (sp.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5) > 64.0
                || !(level.getBlockEntity(p.pos()) instanceof ShopDisplayBlockEntity)) {
            return CustomShops.Result.NO_DISPLAY;
        }
        if (p.clear()) {
            CustomShops.clear(level, p.pos());
            sp.displayClientMessage(Component.translatable("message.holeydigsite.display.cleared").withStyle(ChatFormatting.YELLOW), true);
            return CustomShops.Result.OK;
        }
        CustomShops.Result r = CustomShops.set(level, p.pos(), p.template(), p.count(), p.price(), p.buyBack());
        sp.displayClientMessage(Component.translatable(switch (r) {
            case OK -> "message.holeydigsite.display.saved";
            case CATALOGUE_ITEM -> "message.holeydigsite.display.catalogue";
            default -> "message.holeydigsite.display.bad";
        }).withStyle(r == CustomShops.Result.OK ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return r;
    }
}
