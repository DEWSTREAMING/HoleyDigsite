package com.holeydigsite.client;

import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
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

import java.util.List;

/**
 * Draws the first few stacks in a Sluice Box's tray, lying flat in the tray at the front of the block (like items on
 * a campfire). Static: nothing is computed per frame except these few item draws, and an empty tray returns at once.
 */
public class SluiceBoxRenderer implements BlockEntityRenderer<SluiceBoxBlockEntity> {
    /** Spots in the tray (pixels, in the model's north-facing layout: front = low z). */
    private static final float[][] SPOTS = {
            {5.0F, 2.6F}, {8.0F, 2.2F}, {11.0F, 2.6F},
            {6.5F, 4.0F}, {9.5F, 4.0F}, {8.0F, 3.4F}};
    /** Top of the tray floor, in pixels. */
    private static final float TRAY_TOP = 5.0F;

    private final ItemRenderer itemRenderer;

    public SluiceBoxRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(SluiceBoxBlockEntity sluice, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        List<ItemStack> tray = sluice.tray();
        if (tray.isEmpty()) {
            return;
        }
        Direction facing = sluice.getBlockState().getValue(SluiceBoxBlock.FACING);
        float modelY = (facing.toYRot() + 180.0F) % 360.0F; // same rotation as the blockstate variant
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-modelY));
        pose.translate(-0.5, 0, -0.5);
        int n = Math.min(tray.size(), SPOTS.length);
        for (int i = 0; i < n; i++) {
            pose.pushPose();
            pose.translate(SPOTS[i][0] / 16.0, (TRAY_TOP + 0.3F + i * 0.05F) / 16.0, SPOTS[i][1] / 16.0);
            if (FlatItems.isLump(tray.get(i))) {
                float scale = 0.6F;
                pose.translate(0, scale / 16.0, 0);
                pose.mulPose(Axis.YP.rotationDegrees(i * 47.0F));
                pose.scale(scale, scale, scale);
            } else {
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(i * 47.0F));
                pose.scale(0.3F, 0.3F, 0.3F);
            }
            itemRenderer.renderStatic(tray.get(i), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                    sluice.getLevel(), (int) sluice.getBlockPos().asLong() + i);
            pose.popPose();
        }
        pose.popPose();
    }
}
