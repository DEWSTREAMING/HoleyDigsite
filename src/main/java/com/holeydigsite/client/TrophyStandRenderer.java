package com.holeydigsite.client;

import com.holeydigsite.block.TrophyStandBlock;
import com.holeydigsite.block.TrophyStandBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The nugget on the Welcome Stranger stand, lying on the plinth and facing the stand's front. */
public class TrophyStandRenderer implements BlockEntityRenderer<TrophyStandBlockEntity> {
    private final ItemRenderer items;

    public TrophyStandRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(TrophyStandBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack s = be.shown();
        if (s.isEmpty() || be.getLevel() == null || !FloatingText.near(be.getBlockPos(), FloatingText.ITEM_RANGE)) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 10.0 / 16.0 + 0.01, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-be.getBlockState().getValue(TrophyStandBlock.FACING).toYRot()));
        float scale = 0.75F;
        if (FlatItems.isLump(s)) {
            pose.translate(0, scale / 16.0F, 0);
        } else {
            pose.translate(0, 0.2, 0);
        }
        pose.scale(scale, scale, scale);
        items.renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(),
                (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
