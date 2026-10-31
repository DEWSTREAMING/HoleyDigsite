package com.holeydigsite.client;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the item a Shop Display sells, slowly turning, plus a small price label.
 */
public class ShopDisplayRenderer implements BlockEntityRenderer<ShopDisplayBlockEntity> {
    private final ItemRenderer itemRenderer;

    public ShopDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ShopDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Item item = display.item();
        com.holeydigsite.shop.custom.CustomEntry custom = display.custom(); // Operator-made entry
        ItemStack shownStack = custom != null ? custom.template() : item != null ? item.getDefaultInstance() : ItemStack.EMPTY;
        if (shownStack.isEmpty() || display.getLevel() == null || !FloatingText.near(display.getBlockPos(), FloatingText.ITEM_RANGE)) {
            return;
        }
        float angle = ((display.getLevel().getGameTime() + partialTick) * 1.2F) % 360.0F;
        pose.pushPose();
        pose.translate(0.5, 0.62, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        float scale = 1.1F;
        if (FlatItems.isFlat(shownStack)) {
            scale = FlatItems.isLump(shownStack) ? 1.4F : 1.6F;   // flat 3D models are smaller than a sprite
        }
        pose.scale(scale, scale, scale);
        itemRenderer.renderStatic(shownStack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                display.getLevel(), (int) display.getBlockPos().asLong());
        pose.popPose();
        if (FloatingText.focused(display.getBlockPos(), 0.62)) {
            if (display.clientLabel == null && custom != null) {
                Component price = custom.count() > 1
                        ? Component.translatable("tooltip.holeydigsite.display.price_count", custom.count(), Selling.format(custom.price()))
                        : Component.translatable("tooltip.holeydigsite.display.price", Selling.format(custom.price()));
                display.clientLabel = custom.hasBuyBack()
                        ? price.copy().append(Component.translatable("tooltip.holeydigsite.display.buyback", Selling.format(custom.buyBack())))
                        : price;
            } else if (display.clientLabel == null) {
                display.clientLabel = ShopCatalogue.find(display.getLevel().registryAccess(), item)
                        .map(e -> e.count() > 1
                                ? Component.translatable("tooltip.holeydigsite.display.price_count", e.count(), e.priceText())
                                : Component.translatable("tooltip.holeydigsite.display.price", e.priceText()))
                        .orElse(Component.translatable("tooltip.holeydigsite.display.not_sold"));
            }
            float sc = FloatingText.labelScale(display.getBlockPos());
            FloatingText.draw(pose, buffers, custom != null ? shownStack.getHoverName() : item.getDescription(), 0.5, 1.3 + FloatingText.LINE * sc / FloatingText.SMALL, 0.5, sc, light);
            FloatingText.draw(pose, buffers, display.clientLabel, 0.5, 1.3, 0.5, sc, light);
        }
    }
}
