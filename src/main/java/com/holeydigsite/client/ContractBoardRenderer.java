package com.holeydigsite.client;

import com.holeydigsite.contract.Contract;
import com.holeydigsite.contract.ContractBoardBlock;
import com.holeydigsite.contract.ContractBoardBlockEntity;
import com.holeydigsite.contract.ContractData;
import com.holeydigsite.contract.ContractDefs;
import com.holeydigsite.contract.ContractManager;
import com.holeydigsite.contract.ContractNetwork;
import com.holeydigsite.contract.ContractText;
import com.holeydigsite.util.LabelWrap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The three cards of the Contract Board, as floating text in front of it. The card under the crosshair is highlighted (the same
 * maths the server uses for the click; a click on text over a neighbouring block is sent by {@link ClientEvents}). When the cards are from an earlier day the client asks the server for new ones.
 */
public class ContractBoardRenderer implements BlockEntityRenderer<ContractBoardBlockEntity> {
    private static final double RANGE = 12.0;
    private static final int ASK_EVERY_TICKS = 100;
    private static final double TEXT_HEIGHT = 9.0;
    private static long lastAsk = Long.MIN_VALUE / 2;

    public ContractBoardRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ContractBoardBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (be.getLevel() == null || mc.player == null || !FloatingText.near(be.getBlockPos(), RANGE)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        if (!FloatingText.focused(pos, 1.0)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(ContractBoardBlock.FACING);
        ContractData data = ContractManager.data(mc.player);
        long day = Math.max(0, be.getLevel().getDayTime() / 24000L);
        long now = be.getLevel().getGameTime();
        if (data.lastDay() < day && now - lastAsk > ASK_EVERY_TICKS && mc.getConnection() != null) {
            lastAsk = now;
            if (mc.getConnection().hasChannel(ContractNetwork.RefreshPayload.TYPE)) {
                PacketDistributor.sendToServer(new ContractNetwork.RefreshPayload(pos));
            }
        }
        int highlight = cardUnderCrosshair(pos, facing, partialTick);
        float scale = FloatingText.labelScale(pos) * 0.85F;
        double gap = FloatingText.LINE * scale / FloatingText.SMALL;
        double x = 0.5 + facing.getStepX() * ContractBoardBlock.CARD_OFFSET;
        double z = 0.5 + facing.getStepZ() * ContractBoardBlock.CARD_OFFSET;
        int days = ContractDefs.rules().days();
        for (int k = 0; k < ContractDefs.rules().slots(); k++) {
            List<Component> lines = styled(data.get(k), day, days, k == highlight);
            int n = LabelWrap.wrap(lines, mc.font::width, FloatingText.MAX_WIDTH).size();
            // lines hang down from their anchor, so a card's middle is half a text line below the plain stack maths
            double bottom = ContractBoardBlock.cardCentreY(k) - gap * (n - 1) / 2.0 + TEXT_HEIGHT * scale / 2.0;
            FloatingText.drawLines(pose, buffers, lines, x, bottom, z, scale, light);
        }
    }

    /**
     * The card under the crosshair, or -1, also when the crosshair is on a block next to the board (the text hangs over
     * it), but not when something stands between the player and the cards.
     */
    public static int cardUnderCrosshair(BlockPos pos, Direction facing, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.hitResult == null) {
            return -1;
        }
        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 look = mc.player.getViewVector(partialTick);
        HitResult hit = mc.hitResult;
        boolean onBoard = hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(pos);
        if (!onBoard && hit.getType() != HitResult.Type.MISS
                && hit.getLocation().distanceTo(eye) < ContractBoardBlock.cardDepth(pos, facing, eye, look) - 0.5) {
            return -1;
        }
        return ContractBoardBlock.cardUnderCrosshair(pos, facing, eye, look);
    }

    private static List<Component> styled(Contract c, long day, int days, boolean highlighted) {
        List<Component> raw = ContractText.card(c, day, days);
        List<Component> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            ChatFormatting colour;
            if (c == null) {
                colour = highlighted ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            } else if (c.isDone()) {
                colour = i == 0 ? (highlighted ? ChatFormatting.YELLOW : ChatFormatting.WHITE) : ChatFormatting.GREEN;
            } else if (i == 0) {
                colour = highlighted ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
            } else {
                colour = i == 1 ? ChatFormatting.GRAY : ChatFormatting.GOLD;
            }
            out.add(raw.get(i).copy().withStyle(colour));
        }
        return out;
    }

    @Override
    public boolean shouldRenderOffScreen(ContractBoardBlockEntity be) {
        return true;
    }
}
