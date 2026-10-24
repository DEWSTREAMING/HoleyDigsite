package com.holeydigsite.client;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.plot.LandDeedItem;
import com.holeydigsite.plot.PlotBoardBlock;
import com.holeydigsite.plot.PlotBoardBlockEntity;
import com.holeydigsite.shop.ShopCatalogue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.WeakHashMap;

/**
 * Plot board floating text readable from 24 blocks, showing the plot's name and either 'For Sale, 400 gold' (using the price of its
 * Land Deed in the shop catalogue) or 'Owned'. Cached per board, and rebuilt when the board state or plot changes.
 */
public class PlotBoardRenderer implements BlockEntityRenderer<PlotBoardBlockEntity> {
    private static final double RANGE = 24.0;

    private record Cached(String plot, BlockState state, Component name, Component status) {}
    private final WeakHashMap<PlotBoardBlockEntity, Cached> cache = new WeakHashMap<>();

    public PlotBoardRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PlotBoardBlockEntity b, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (b.getLevel() == null || b.plotId().isEmpty() || !FloatingText.near(b.getBlockPos(), RANGE)) {
            return;
        }
        Cached c = cache.get(b);
        if (c == null || !c.plot.equals(b.plotId()) || c.state != b.getBlockState()) {
            boolean owned = b.getBlockState().getValue(PlotBoardBlock.OWNED);
            Component status;
            if (owned) {
                status = Component.translatable("text.holeydigsite.plot.owned").withStyle(ChatFormatting.GREEN);
            } else {
                LandDeedItem deed = LandDeedItem.forPlot(b.plotId());
                var entry = deed == null ? java.util.Optional.<com.holeydigsite.shop.ShopItem>empty()
                        : ShopCatalogue.find(b.getLevel().registryAccess(), deed);
                status = entry.map(e -> Component.translatable("text.holeydigsite.plot.for_sale_price", Selling.format(e.price())))
                        .orElse(Component.translatable("text.holeydigsite.plot.for_sale")).copy().withStyle(ChatFormatting.GOLD);
            }
            c = new Cached(b.plotId(), b.getBlockState(), LandDeedItem.plotName(b.plotId()).copy().withStyle(ChatFormatting.WHITE), status);
            cache.put(b, c);
        }
        FloatingText.draw(pose, buffers, c.name, 0.5, 1.75, 0.5, 0.03F, light);
        FloatingText.draw(pose, buffers, c.status, 0.5, 1.45, 0.5, 0.025F, light);
    }

    @Override
    public boolean shouldRenderOffScreen(PlotBoardBlockEntity b) {
        return true;
    }
}
