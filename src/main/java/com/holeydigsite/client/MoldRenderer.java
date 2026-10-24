package com.holeydigsite.client;

import com.holeydigsite.forge.Metals;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.forge.MoldBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Mold floating text, when the player is near: "Mold: 50 g" (empty), "Folkiron: 30 / 50 g - cooling" (hot) or
 * "Folkiron: 50 g - ready" (solid). The metal itself is a baked block-state model. Text is cached per block entity.
 */
public class MoldRenderer implements BlockEntityRenderer<MoldBlockEntity> {
    private record Cached(int target, Object metal, int grams, boolean solid, Component label) {}
    private final WeakHashMap<MoldBlockEntity, Cached> cache = new WeakHashMap<>();

    public MoldRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MoldBlockEntity m, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!FloatingText.focused(m.getBlockPos(), 0.3)) {
            return;
        }
        boolean solid = m.getBlockState().getValue(MoldBlock.SOLID);
        Cached k = cache.get(m);
        if (k == null || k.target != m.target() || !Objects.equals(k.metal, m.metal()) || k.grams != m.grams() || k.solid != solid) {
            Component label;
            if (m.isEmpty()) {
                label = Component.translatable("text.holeydigsite.mold", m.target());
            } else if (solid) {
                label = Component.translatable("text.holeydigsite.mold.ready", Metals.name(m.metal()), m.grams());
            } else {
                label = Component.translatable("text.holeydigsite.mold.hot", Metals.name(m.metal()), m.grams(), m.target());
            }
            k = new Cached(m.target(), m.metal(), m.grams(), solid, label);
            cache.put(m, k);
        }
        FloatingText.draw(pose, buffers, k.label, 0.5, 0.55, 0.5, FloatingText.labelScale(m.getBlockPos()), light);
    }
}
