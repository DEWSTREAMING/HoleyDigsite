package com.holeydigsite.client;

import com.holeydigsite.skin.SkinBenchBlock;
import com.holeydigsite.skin.SkinBenchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the machine currently resting on top of the Skin Bench.
 */
public class SkinBenchRenderer implements BlockEntityRenderer<SkinBenchBlockEntity> {
    private final ItemRenderer itemRenderer;

    public SkinBenchRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(SkinBenchBlockEntity bench, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack item = bench.getItem();
        if (item.isEmpty() || bench.getLevel() == null) {
            return;
        }

        Direction facing = bench.getBlockState().getValue(SkinBenchBlock.FACING);
        float rotation = switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };

        pose.pushPose();
        pose.translate(0.5, 1.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.scale(0.7F, 0.7F, 0.7F);

        itemRenderer.renderStatic(item, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                bench.getLevel(), (int) bench.getBlockPos().asLong());
        pose.popPose();
    }
}
