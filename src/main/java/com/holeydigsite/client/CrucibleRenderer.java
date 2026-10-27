package com.holeydigsite.client;

import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.CrucibleLabel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Crucible floating text "Folkiron: 87 g molten (+12 g melting)" and, while auto-pour is on, "Auto-pour" under it. Only
 * when it holds metal or has auto-pour on, and the player is near. The molten level, glow and chunks are baked block-state
 * models, so nothing else is drawn. Text is cached per block entity.
 */
public class CrucibleRenderer implements BlockEntityRenderer<CrucibleBlockEntity> {
    private record Cached(Object metal, int molten, int unmelted, BlockState state, List<Component> lines) {}
    private final WeakHashMap<CrucibleBlockEntity, Cached> cache = new WeakHashMap<>();

    public CrucibleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CrucibleBlockEntity c, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = c.getBlockState();
        boolean auto = state.getBlock() instanceof CrucibleBlock b && b.autoPour(state);
        if ((c.total() <= 0 && !auto) || !FloatingText.focused(c.getBlockPos(), 0.5)) {
            return;
        }
        Cached k = cache.get(c);
        if (k == null || !Objects.equals(k.metal, c.metal()) || k.molten != c.molten() || k.unmelted != c.unmelted() || k.state != state) {
            k = new Cached(c.metal(), c.molten(), c.unmelted(), state, CrucibleLabel.lines(c));
            cache.put(c, k);
        }
        float sc = FloatingText.labelScale(c.getBlockPos());
        java.util.List<Component> shown = new java.util.ArrayList<>();
        for (Component line : k.lines) {
            boolean autoLine = line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("text.holeydigsite.crucible.auto");
            shown.add(autoLine ? line.copy().withStyle(ChatFormatting.GRAY) : line);
        }
        FloatingText.drawLines(pose, buffers, shown, 0.5, 0.95, 0.5, sc, light);
    }
}
