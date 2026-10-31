package com.holeydigsite.client;

import com.holeydigsite.board.BoardNetwork;
import com.holeydigsite.board.ScoreBoardBlock;
import com.holeydigsite.board.ScoreBoardBlockEntity;
import com.holeydigsite.contract.ContractBoardBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** The Event Board and Leaderboard table as floating text in front of the board; asks for fresh numbers while looked at. */
public class ScoreBoardRenderer implements BlockEntityRenderer<ScoreBoardBlockEntity> {
    private static final double RANGE = 12.0;
    private static final int ASK_EVERY_TICKS = 100;
    private static final double TEXT_HEIGHT = 9.0;
    private static long lastAsk = Long.MIN_VALUE / 2;
    private static BlockPos lastPos = null;

    public ScoreBoardRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ScoreBoardBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (be.getLevel() == null || mc.player == null || !FloatingText.near(be.getBlockPos(), RANGE)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        if (!FloatingText.focused(pos, 1.0)) {
            return;
        }
        long now = be.getLevel().getGameTime();
        if ((now - lastAsk > ASK_EVERY_TICKS || !pos.equals(lastPos)) && mc.getConnection() != null
                && mc.getConnection().hasChannel(BoardNetwork.RefreshPayload.TYPE)) {
            lastAsk = now;
            lastPos = pos.immutable();
            PacketDistributor.sendToServer(new BoardNetwork.RefreshPayload(pos));
        }
        List<Component> lines = be.lines();
        if (lines.isEmpty()) {
            return;
        }
        // one table centred on the board face (as the Contract Board's cards): rows left-aligned, the block centred
        // on the block's middle, both ways
        Direction facing = be.getBlockState().getValue(ScoreBoardBlock.FACING);
        float scale = FloatingText.labelScale(pos) * 0.85F;
        double x = 0.5 + facing.getStepX() * ContractBoardBlock.CARD_OFFSET;
        double z = 0.5 + facing.getStepZ() * ContractBoardBlock.CARD_OFFSET;
        var font = mc.font;
        List<Component> all = com.holeydigsite.util.LabelWrap.wrap(lines, font::width, FloatingText.MAX_WIDTH + 60);
        int width = 0;
        for (Component c : all) {
            width = Math.max(width, font.width(c));
        }
        double gap = FloatingText.LINE * scale / FloatingText.SMALL;
        double y = 0.5 + gap * (all.size() - 1) / 2.0 + TEXT_HEIGHT * scale / 2.0;
        for (int i = 0; i < all.size(); i++) {
            Component c = all.get(i);
            // the title and help lines are centred, the rows start at the table's left edge
            float left = i == 0 || i == all.size() - 1 || i == 1 ? -font.width(c) / 2.0F : -width / 2.0F;
            FloatingText.drawFrom(pose, buffers, c, x, y - gap * i, z, scale, light, left);
        }
    }
}
