package com.holeydigsite.client;

import com.holeydigsite.guide.FieldGuideClientHooks;
import com.holeydigsite.guide.FieldGuideDefs;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/**
 * Eight-page illustrated Field Guide screen styled after the Prospector's Ledger.
 * Fits within a 1280x720 window at GUI scale 3 (426x240 GUI units).
 */
public class FieldGuideScreen extends Screen {
    public static final int WIN_W = FieldGuideDefs.WIN_W;
    public static final int WIN_H = FieldGuideDefs.WIN_H;
    private static final int PIC_W = 240;
    private static final int PIC_H = 135;
    private static final int DOT_SIZE = 6;
    private static final int DOT_GAP = 6;

    private static final int COL_BG = 0xF0241D16;
    private static final int COL_PANEL = 0xFF3B2E22;
    private static final int COL_BORDER = 0xFFD49B27;
    private static final int COL_BORDER_DARK = 0xFF140F0A;
    private static final int COL_TEXT = 0xFFF4E8D1;
    private static final int COL_MUTED = 0xFFB8A99A;
    private static final int COL_GOLD = 0xFFFFD369;

    private int leftPos;
    private int topPos;
    private int currentPage;
    private long pageStartMs;
    private Button prevBtn;
    private Button nextBtn;

    public FieldGuideScreen() {
        super(Component.translatable("screen.holeydigsite.field_guide.title"));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - WIN_W) / 2;
        this.topPos = (this.height - WIN_H) / 2;

        List<FieldGuideDefs.Page> pages = FieldGuideDefs.pages();
        int maxPage = Math.max(0, pages.size() - 1);
        UUID playerId = playerUuid();
        this.currentPage = Math.clamp(FieldGuideDefs.lastPageFor(playerId), 0, maxPage);
        this.pageStartMs = Util.getMillis();

        int navY = topPos + WIN_H - 26;
        this.prevBtn = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.holeydigsite.field_guide.prev"),
                b -> setPage(currentPage - 1)
        ).bounds(leftPos + 12, navY, 56, 18).build());

        this.nextBtn = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.holeydigsite.field_guide.next"),
                b -> setPage(currentPage + 1)
        ).bounds(leftPos + WIN_W - 68, navY, 56, 18).build());

        updateNavButtons();
    }

    private UUID playerUuid() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID() : null;
    }

    public int currentPage() {
        return currentPage;
    }

    public void setPage(int index) {
        List<FieldGuideDefs.Page> pages = FieldGuideDefs.pages();
        if (pages.isEmpty()) return;
        int clamped = Math.clamp(index, 0, pages.size() - 1);
        if (clamped != this.currentPage) {
            this.currentPage = clamped;
            this.pageStartMs = Util.getMillis();
            FieldGuideDefs.setLastPageFor(playerUuid(), this.currentPage);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.6f, 1.1f);
            }
        }
        updateNavButtons();
    }

    private void updateNavButtons() {
        int count = FieldGuideDefs.pages().size();
        if (prevBtn != null) prevBtn.active = currentPage > 0;
        if (nextBtn != null) nextBtn.active = currentPage + 1 < count;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // super.render calls this after the page is drawn, and a dim there would cover the page; render dims first instead
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);

        // Outer frame in the Ledger's dark-brown and gold palette
        g.fill(leftPos - 2, topPos - 2, leftPos + WIN_W + 2, topPos + WIN_H + 2, COL_BORDER_DARK);
        g.fill(leftPos - 1, topPos - 1, leftPos + WIN_W + 1, topPos + WIN_H + 1, COL_BORDER);
        g.fill(leftPos, topPos, leftPos + WIN_W, topPos + WIN_H, COL_BG);

        // Header bar
        g.fill(leftPos + 6, topPos + 6, leftPos + WIN_W - 6, topPos + 24, COL_PANEL);
        g.drawString(this.font, this.title, leftPos + 12, topPos + 11, COL_GOLD, true);

        List<FieldGuideDefs.Page> pages = FieldGuideDefs.pages();
        if (!pages.isEmpty()) {
            FieldGuideDefs.Page page = pages.get(Math.clamp(currentPage, 0, pages.size() - 1));

            // Page title centered in header, page number on the right
            Component pageTitle = Component.translatable(page.titleKey());
            int titleX = leftPos + (WIN_W - this.font.width(pageTitle)) / 2;
            g.drawString(this.font, pageTitle, titleX, topPos + 11, COL_TEXT, true);

            Component pageIndicator = Component.translatable(
                    "screen.holeydigsite.field_guide.page", currentPage + 1, pages.size());
            g.drawString(this.font, pageIndicator,
                    leftPos + WIN_W - 12 - this.font.width(pageIndicator), topPos + 11, COL_MUTED, false);

            // Left picture frame (240x135, 16:9)
            int picX = leftPos + 12;
            int picY = topPos + 34;
            g.fill(picX - 2, picY - 2, picX + PIC_W + 2, picY + PIC_H + 2, COL_BORDER_DARK);
            g.fill(picX - 1, picY - 1, picX + PIC_W + 1, picY + PIC_H + 1, COL_BORDER);
            g.fill(picX, picY, picX + PIC_W, picY + PIC_H, 0xFF14100C);

            long elapsedMs = Math.max(0L, Util.getMillis() - pageStartMs);
            int frameIdx = FieldGuideDefs.frameAt(page, elapsedMs);
            int cols = Math.max(1, page.cols());
            int col = frameIdx % cols;
            int row = frameIdx / cols;
            float u = (float) (col * page.frameW());
            float v = (float) (row * page.frameH());
            g.blit(page.texture(), picX, picY, PIC_W, PIC_H,
                    u, v, page.frameW(), page.frameH(), page.sheetWidth(), page.sheetHeight());

            // Right text panel beside the picture
            int textBoxX = picX + PIC_W + 8;
            int textBoxY = picY - 1;
            int textBoxW = leftPos + WIN_W - 10 - textBoxX;
            int textBoxH = PIC_H + 2;
            g.fill(textBoxX, textBoxY, textBoxX + textBoxW, textBoxY + textBoxH, COL_PANEL);
            g.renderOutline(textBoxX, textBoxY, textBoxW, textBoxH, 0xFF544332);

            int textX = textBoxX + 6;
            int textY = textBoxY + 6;
            int wrapW = textBoxW - 12;
            for (String key : page.textKeys()) {
                Component lineComp = Component.translatable(key);
                List<FormattedCharSequence> wrapped = this.font.split(lineComp, wrapW - 6);
                boolean first = true;
                for (FormattedCharSequence seq : wrapped) {
                    if (textY + 9 > textBoxY + textBoxH - 4) break;
                    if (first) {
                        g.drawString(this.font, "-", textX, textY, COL_GOLD, false);
                        first = false;
                    }
                    g.drawString(this.font, seq, textX + 6, textY, COL_TEXT, false);
                    textY += 10;
                }
                textY += 4;
            }

            // Page dots in the bottom bar between Prev and Next
            renderPageDots(g, pages.size(), mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPageDots(GuiGraphics g, int count, int mouseX, int mouseY) {
        int totalW = count * DOT_SIZE + (count - 1) * DOT_GAP;
        int startX = leftPos + (WIN_W - totalW) / 2;
        int dotY = topPos + WIN_H - 20;
        for (int i = 0; i < count; i++) {
            int dx = startX + i * (DOT_SIZE + DOT_GAP);
            boolean active = (i == currentPage);
            boolean hovered = mouseX >= dx - 2 && mouseX <= dx + DOT_SIZE + 2
                    && mouseY >= dotY - 2 && mouseY <= dotY + DOT_SIZE + 2;
            int borderCol = active ? COL_GOLD : (hovered ? COL_TEXT : 0xFF6E5A44);
            int fillCol = active ? COL_BORDER : (hovered ? 0xFF8A7258 : COL_PANEL);
            g.fill(dx - 1, dotY - 1, dx + DOT_SIZE + 1, dotY + DOT_SIZE + 1, borderCol);
            g.fill(dx, dotY, dx + DOT_SIZE, dotY + DOT_SIZE, fillCol);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int count = FieldGuideDefs.pages().size();
            if (count > 0) {
                int totalW = count * DOT_SIZE + (count - 1) * DOT_GAP;
                int startX = leftPos + (WIN_W - totalW) / 2;
                int dotY = topPos + WIN_H - 20;
                for (int i = 0; i < count; i++) {
                    int dx = startX + i * (DOT_SIZE + DOT_GAP);
                    if (mouseX >= dx - 2 && mouseX <= dx + DOT_SIZE + 2
                            && mouseY >= dotY - 2 && mouseY <= dotY + DOT_SIZE + 2) {
                        setPage(i);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            setPage(currentPage - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            setPage(currentPage + 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
