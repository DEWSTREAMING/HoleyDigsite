package com.holeydigsite.client;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.machine.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders floating status labels when focused, and turns moving parts while working.
 * Zero renderer overhead at rest beyond the label check.
 */
public class WaterMachineRenderer<T extends WaterProcessingBlockEntity> implements BlockEntityRenderer<T> {
    public static final ModelResourceLocation THRESHING_DRUM_ROTOR = ModelResourceLocation.standalone(HoleyDigsite.id("block/threshing_drum_rotor"));
    public static final ModelResourceLocation ROPE_MACHINE_WHEEL = ModelResourceLocation.standalone(HoleyDigsite.id("block/rope_machine_wheel"));
    public static final ModelResourceLocation SAWMILL_BLADE = ModelResourceLocation.standalone(HoleyDigsite.id("block/sawmill_blade"));

    private static final Direction[] SIDES = {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
    private final RandomSource random = RandomSource.create();

    public WaterMachineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(T machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (FloatingText.focused(machine.getBlockPos(), 0.75)) {
            renderLabels(machine, pose, buffers, light);
        }

        BlockState state = machine.getBlockState();
        if (!state.hasProperty(WaterProcessingBlock.WORKING) || !state.getValue(WaterProcessingBlock.WORKING)) {
            return; // Moving parts stop when not working, so the renderer costs nothing at rest beyond the label check.
        }
        if (machine.getLevel() == null) {
            return;
        }

        Direction facing = state.getValue(WaterProcessingBlock.FACING);
        long gameTime = machine.getLevel().getGameTime();

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-((facing.toYRot() + 180.0F) % 360.0F)));
        pose.translate(-0.5, -0.5, -0.5);

        if (machine instanceof ThreshingDrumBlockEntity) {
            // Slatted drum over the belt at y=24, z=8; turns around X axis
            float angle = ((gameTime % 3600) + partialTick) * 12.0f % 360f;
            pose.pushPose();
            pose.translate(0.5, 24.0 / 16.0, 8.0 / 16.0);
            pose.mulPose(Axis.XP.rotationDegrees(angle));
            pose.translate(-0.5, -24.0 / 16.0, -8.0 / 16.0);
            renderBakedModel(THRESHING_DRUM_ROTOR, state, pose, buffers, light, overlay);
            pose.popPose();
        } else if (machine instanceof RopeMachineBlockEntity) {
            // Twisting wheel on the front at z=1.5, y=25; turns around Z axis
            float angle = ((gameTime % 3600) + partialTick) * 10.0f % 360f;
            pose.pushPose();
            pose.translate(0.5, 25.0 / 16.0, 1.5 / 16.0);
            pose.mulPose(Axis.ZP.rotationDegrees(angle));
            pose.translate(-0.5, -25.0 / 16.0, -1.5 / 16.0);
            renderBakedModel(ROPE_MACHINE_WHEEL, state, pose, buffers, light, overlay);
            pose.popPose();
        } else if (machine instanceof SawmillBlockEntity) {
            // Saw blade through the table slot, centre y=14, z=8; turns fast around X axis
            float angle = ((gameTime % 3600) + partialTick) * 36.0f % 360f;
            pose.pushPose();
            pose.translate(0.5, 14.0 / 16.0, 8.0 / 16.0);
            pose.mulPose(Axis.XP.rotationDegrees(angle));
            pose.translate(-0.5, -14.0 / 16.0, -8.0 / 16.0);
            renderBakedModel(SAWMILL_BLADE, state, pose, buffers, light, overlay);
            pose.popPose();
        }

        pose.popPose();
    }

    private void renderBakedModel(ModelResourceLocation loc, BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(loc);
        if (model == null) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        for (Direction side : SIDES) {
            for (BakedQuad quad : model.getQuads(state, side, random, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null)) {
                vc.putBulkData(pose.last(), quad, 1f, 1f, 1f, 1f, light, overlay);
            }
        }
    }

    private void renderLabels(T machine, PoseStack pose, MultiBufferSource buffers, int light) {
        double y = 2.05;
        float scale = FloatingText.labelScale(machine.getBlockPos());

        if (!machine.isWaterPowered()) {
            Component label = Component.translatable("message.holeydigsite.machine.no_water").withStyle(ChatFormatting.RED);
            FloatingText.draw(pose, buffers, label, 0.5, y, 0.5, scale, light);
            y += 0.22;
        } else if (machine.isBufferFull()) {
            Component label = Component.translatable("message.holeydigsite.machine.buffer_full").withStyle(ChatFormatting.YELLOW);
            FloatingText.draw(pose, buffers, label, 0.5, y, 0.5, scale, light);
            y += 0.22;
        }

        if (machine instanceof SawmillBlockEntity sawmill) {
            boolean isBeam = "beam".equalsIgnoreCase(sawmill.mode());
            String key = isBeam ? "message.holeydigsite.sawmill.mode_beam" : "message.holeydigsite.sawmill.mode_pit_prop";
            Component modeLabel = Component.translatable(key).withStyle(ChatFormatting.AQUA);
            FloatingText.draw(pose, buffers, modeLabel, 0.5, y, 0.5, scale, light);
        }
    }
}
