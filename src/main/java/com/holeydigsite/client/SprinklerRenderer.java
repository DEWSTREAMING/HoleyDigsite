package com.holeydigsite.client;

import com.holeydigsite.power.SprinklerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

/**
 * Renders the floating "No water" label when the sprinkler is unpowered and focused.
 */
public class SprinklerRenderer implements BlockEntityRenderer<SprinklerBlockEntity> {
    public SprinklerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SprinklerBlockEntity sprinkler, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!sprinkler.isWaterPowered() && FloatingText.focused(sprinkler.getBlockPos(), 0.75)) {
            Component label = Component.translatable("message.holeydigsite.sprinkler.no_water").withStyle(ChatFormatting.RED);
            FloatingText.draw(pose, buffers, label, 0.5, 0.85, 0.5, FloatingText.labelScale(sprinkler.getBlockPos()), light);
        }
    }
}
