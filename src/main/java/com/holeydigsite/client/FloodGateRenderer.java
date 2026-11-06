package com.holeydigsite.client;

import com.holeydigsite.power.FloodGateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

/**
 * Renders the floating status label when the flood gate is unpowered or on cooldown and focused.
 */
public class FloodGateRenderer implements BlockEntityRenderer<FloodGateBlockEntity> {
    public FloodGateRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FloodGateBlockEntity gate, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!gate.isWaterPowered() && FloatingText.focused(gate.getBlockPos(), 0.75)) {
            Component label = Component.translatable("message.holeydigsite.flood_gate.no_water").withStyle(ChatFormatting.RED);
            FloatingText.draw(pose, buffers, label, 0.5, 1.15, 0.5, FloatingText.labelScale(gate.getBlockPos()), light);
        } else if (gate.getLevel() != null && gate.getLevel().getGameTime() < gate.cooldownUntil() && FloatingText.focused(gate.getBlockPos(), 0.75)) {
            long sec = Math.max(1, (gate.cooldownUntil() - gate.getLevel().getGameTime() + 19) / 20);
            Component label = Component.translatable("message.holeydigsite.flood_gate.cooldown", sec).withStyle(ChatFormatting.YELLOW);
            FloatingText.draw(pose, buffers, label, 0.5, 1.15, 0.5, FloatingText.labelScale(gate.getBlockPos()), light);
        }
    }
}
