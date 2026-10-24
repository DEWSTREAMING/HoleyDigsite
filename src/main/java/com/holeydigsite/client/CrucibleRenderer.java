package com.holeydigsite.client;

import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.Metals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Crucible floating text "Folkiron: 87 g molten (+12 g melting)" - only when it holds metal and the player is near. The
 * molten level, glow and chunks are baked block-state models, so nothing else is drawn. Text is cached per block entity.
 */
public class CrucibleRenderer implements BlockEntityRenderer<CrucibleBlockEntity> {
    private record Cached(Object metal, int molten, int unmelted, Component label) {}
    private final WeakHashMap<CrucibleBlockEntity, Cached> cache = new WeakHashMap<>();

    public CrucibleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CrucibleBlockEntity c, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (c.total() <= 0 || !FloatingText.focused(c.getBlockPos(), 0.5)) {
            return;
        }
        Cached k = cache.get(c);
        if (k == null || !Objects.equals(k.metal, c.metal()) || k.molten != c.molten() || k.unmelted != c.unmelted()) {
            Component label = c.unmelted() > 0
                    ? Component.translatable("text.holeydigsite.crucible.melting", Metals.name(c.metal()), c.molten(), c.unmelted())
                    : Component.translatable("text.holeydigsite.crucible", Metals.name(c.metal()), c.molten());
            k = new Cached(c.metal(), c.molten(), c.unmelted(), label);
            cache.put(c, k);
        }
        FloatingText.draw(pose, buffers, k.label, 0.5, 0.95, 0.5, FloatingText.labelScale(c.getBlockPos()), light);
    }
}
