package com.holeydigsite.client;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.drill.DrillStatus;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns the drill's auger. The block models leave it out; this draws {@link #AUGER} under the housing, with the drill's
 * skin, and spins it while the drill works (faster on quicker tiers). Stopped drills keep it still.
 */
public class MiningDrillRenderer implements BlockEntityRenderer<MiningDrillBlockEntity> {
    public static final ModelResourceLocation AUGER = ModelResourceLocation.standalone(HoleyDigsite.id("block/mining_drill_auger"));
    /** Degrees per tick for a Tier 1 drill (300 ticks a clump); quicker tiers turn faster in proportion. */
    private static final float T1_DEGREES_PER_TICK = 9f;
    private static final Direction[] SIDES = {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

    private final RandomSource random = RandomSource.create();

    public MiningDrillRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MiningDrillBlockEntity drill, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = drill.getBlockState();
        if (!(state.getBlock() instanceof MiningDrillBlock) || drill.getLevel() == null) {
            return;
        }
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(AUGER);
        float angle = 0;
        if (state.getValue(MiningDrillBlock.STATUS) == DrillStatus.WORKING) {
            float speed = T1_DEGREES_PER_TICK * MiningDrillBlock.CYCLE_TICKS / MiningDrillBlock.cycleTicks(state);
            angle = ((drill.getLevel().getGameTime() % 3600) + partialTick) * speed % 360f;
        }
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-angle));
        pose.translate(-0.5, 0, -0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        var data = drill.getModelData();
        for (Direction side : SIDES) {
            for (BakedQuad quad : model.getQuads(state, side, random, data, null)) {
                vc.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, overlay);
            }
        }
        pose.popPose();
    }
}
