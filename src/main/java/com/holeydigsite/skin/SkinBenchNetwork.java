package com.holeydigsite.skin;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.plot.PlotLock;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The Skin Bench wheel: the client asks for a skin and wear (or "default" to take the skin off); the server checks reach, the
 * bench and the machine, then uses a matching kit from the player's own inventory. The client never decides what is owned.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class SkinBenchNetwork {
    private SkinBenchNetwork() {}

    public record ApplySkinPayload(BlockPos pos, String skinId, String wear) implements CustomPacketPayload {
        public static final Type<ApplySkinPayload> TYPE = new Type<>(HoleyDigsite.id("apply_skin"));
        public static final StreamCodec<ByteBuf, ApplySkinPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ApplySkinPayload::pos,
                ByteBufCodecs.STRING_UTF8, ApplySkinPayload::skinId,
                ByteBufCodecs.STRING_UTF8, ApplySkinPayload::wear,
                ApplySkinPayload::new
        );

        /** Take the skin off. */
        public static ApplySkinPayload remove(BlockPos pos) {
            return new ApplySkinPayload(pos, SkinDefs.DEFAULT_ID, "");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2").optional();
        registrar.playToServer(ApplySkinPayload.TYPE, ApplySkinPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    handleApply(sp, payload);
                }
            });
        });
    }

    public static void sendToServer(BlockPos pos, String skinId, String wear) {
        PacketDistributor.sendToServer(new ApplySkinPayload(pos, skinId, wear));
    }

    public static boolean handleApply(ServerPlayer player, ApplySkinPayload payload) {
        if (player == null || player.isSpectator() || payload == null || payload.pos() == null || payload.skinId() == null) {
            return false;
        }
        Level level = player.level();
        BlockPos pos = payload.pos();
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            return false;
        }
        if (PlotLock.access(player, level, pos) != PlotLock.Access.OK) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SkinBenchBlockEntity bench) || !bench.hasItem()) {
            return false;
        }
        String family = SkinDefs.getMachineFamily(bench.getItem());
        if (family == null) {
            return false;
        }
        if (SkinDefs.DEFAULT_ID.equals(payload.skinId())) {
            return SkinBenchActions.remove(player, bench) == SkinBenchActions.Outcome.REMOVED;
        }
        ItemStack kit = findKit(player.getInventory(), payload.skinId(), family, payload.wear());
        if (kit.isEmpty()) {
            return false;
        }
        SkinBenchActions.Outcome out = SkinBenchActions.apply(player, bench, kit);
        return out == SkinBenchActions.Outcome.APPLIED || out == SkinBenchActions.Outcome.SWAPPED;
    }

    /** The first kit in the inventory with this skin, family and wear (the real stack, so using it shrinks it). */
    public static ItemStack findKit(Inventory inv, String skin, String family, String wear) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (SkinKitItem.matches(s, skin, family, wear)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }
}
