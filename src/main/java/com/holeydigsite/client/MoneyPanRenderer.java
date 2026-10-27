package com.holeydigsite.client;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

import java.util.WeakHashMap;

/**
 * The Money Pan's floating "Balance: 12 gold 3 silver" - only when the balance is above zero and the player is near. The
 * coin pile itself is a baked model (block state {@code pile}), so nothing else is drawn here.
 */
public class MoneyPanRenderer implements BlockEntityRenderer<MoneyPanBlockEntity> {
    private record Cached(long balance, java.util.List<Component> label) {}
    private final WeakHashMap<MoneyPanBlockEntity, Cached> cache = new WeakHashMap<>();

    public MoneyPanRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MoneyPanBlockEntity pan, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        long balance = pan.balance();
        if (balance <= 0 || !FloatingText.focused(pan.getBlockPos(), 0.3)) {
            return;
        }
        Cached c = cache.get(pan);
        if (c == null || c.balance != balance) {
            c = new Cached(balance, Selling.balanceLines(balance, Minecraft.getInstance().font::width, FloatingText.MAX_WIDTH));
            cache.put(pan, c);
        }
        FloatingText.drawLines(pose, buffers, c.label, 0.5, 0.65, 0.5, FloatingText.labelScale(pan.getBlockPos()), light);
    }
}
