package com.holeydigsite.client;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.sellpad.SellPadBlock;
import com.holeydigsite.shop.AbstractPadBlock;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopCatalogue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.WeakHashMap;

/**
 * Draws the stacks lying on a Sell Pad / Buy Pad in a 3x3 grid, and the floating total ("2 gold 6 silver", "Total: ...").
 * An empty pad returns immediately; items are drawn only within {@link FloatingText#ITEM_RANGE} and the text within
 * {@link FloatingText#TEXT_RANGE}. The text is rebuilt only when the contents change (cached per block entity).
 */
public class PadRenderer implements BlockEntityRenderer<PadBlockEntity> {
    private static final float TOP = 4.0F / 16.0F;
    /** Cell centres of the 3x3 grid on the 16x16 top (px). */
    private static final float[] CELLS = {3.2F, 8.0F, 12.8F};

    private record Cached(List<ItemStack> contents, Component label) {}
    private final WeakHashMap<PadBlockEntity, Cached> cache = new WeakHashMap<>();
    private final ItemRenderer itemRenderer;

    public PadRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(PadBlockEntity pad, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        List<ItemStack> items = pad.contents();
        if (items.isEmpty() || pad.getLevel() == null || !FloatingText.near(pad.getBlockPos(), FloatingText.ITEM_RANGE)) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            pose.pushPose();
            pose.translate(CELLS[i % 3] / 16.0, TOP + 0.001, CELLS[i / 3] / 16.0);
            pose.mulPose(Axis.YP.rotationDegrees(i * 37.0F));
            if (!(s.getItem() instanceof BlockItem)) {
                pose.translate(0, 0.06, 0);
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            } else {
                pose.translate(0, 0.14, 0);
            }
            pose.scale(0.3F, 0.3F, 0.3F);
            itemRenderer.renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                    pad.getLevel(), (int) pad.getBlockPos().asLong() + i);
            pose.popPose();
        }
        if (FloatingText.focused(pad.getBlockPos(), 0.3)) {
            Cached c = cache.get(pad);
            if (c == null || !sameContents(c.contents, items)) {
                c = new Cached(snapshot(items), label(pad, items));
                cache.put(pad, c);
            }
            FloatingText.draw(pose, buffers, c.label, 0.5, 0.7, 0.5, FloatingText.labelScale(pad.getBlockPos()), light);
        }
    }

    private static boolean sameContents(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static List<ItemStack> snapshot(List<ItemStack> items) {
        return items.stream().map(ItemStack::copy).toList();
    }

    private static Component label(PadBlockEntity pad, List<ItemStack> items) {
        var registries = pad.getLevel().registryAccess();
        if (pad.getBlockState().getBlock() instanceof SellPadBlock) {
            return Selling.format(SellPadBlock.value(items, registries));
        }
        return Component.translatable("tooltip.holeydigsite.pad.total", Selling.format(ShopCatalogue.totalCost(registries, items)));
    }
}
