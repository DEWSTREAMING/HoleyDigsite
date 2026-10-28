package com.holeydigsite.forge;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.smith.AnvilRecipes;
import com.holeydigsite.smith.SmithingAnvilBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Handles client to server networking for the recipe radial wheel on the Crafting Forge and Smithing Anvil.
 * The server validates range, plot ownership, workstation state and recipe existence before switching.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class RecipeWheelNetwork {
    private RecipeWheelNetwork() {}

    public record SelectRecipePayload(BlockPos pos, ResourceLocation recipeId) implements CustomPacketPayload {
        public static final Type<SelectRecipePayload> TYPE = new Type<>(HoleyDigsite.id("select_recipe"));
        public static final StreamCodec<ByteBuf, SelectRecipePayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SelectRecipePayload::pos,
                ResourceLocation.STREAM_CODEC, SelectRecipePayload::recipeId,
                SelectRecipePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(SelectRecipePayload.TYPE, SelectRecipePayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    handleSelect(sp, payload);
                }
            });
        });
    }

    public static void sendToServer(BlockPos pos, ResourceLocation recipeId) {
        PacketDistributor.sendToServer(new SelectRecipePayload(pos, recipeId));
    }

    public static boolean handleSelect(ServerPlayer player, SelectRecipePayload payload) {
        if (player == null || player.isSpectator() || payload == null || payload.pos() == null || payload.recipeId() == null) {
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
        if (be instanceof CraftingForgeBlockEntity forge) {
            if (forge.isCasting() || forge.hasOutput()) {
                return false;
            }
            ForgeRecipes.Entry entry = ForgeRecipes.find(level.registryAccess(), payload.recipeId());
            if (entry == null) {
                return false;
            }
            forge.select(entry.id());
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, 1.0F);
            player.displayClientMessage(
                    Component.translatable("message.holeydigsite.crafting_forge.recipe", entry.recipe().name(level.registryAccess()))
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return true;
        }
        if (be instanceof SmithingAnvilBlockEntity anvil) {
            AnvilRecipes.Entry entry = AnvilRecipes.find(level.registryAccess(), payload.recipeId());
            if (entry == null) {
                return false;
            }
            anvil.select(entry.id());
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, 1.0F);
            player.displayClientMessage(
                    Component.translatable("message.holeydigsite.anvil.recipe", entry.recipe().name(level.registryAccess()))
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return true;
        }
        return false;
    }
}
