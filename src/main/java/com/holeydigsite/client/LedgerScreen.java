package com.holeydigsite.client;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.ledger.LedgerNetwork;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase 21a/21b: The Prospector's Ledger screen.
 * Shows the player's current Level and Renown progress bar (and chosen title), the 50-level scrollable reward track with
 * Claim buttons ("Coming soon" for levels whose content is missing), the 38 milestones grouped by chapter (plus ongoing
 * Renown caps) and the title picker.
 */
public class LedgerScreen extends Screen {
    private static final int WIN_W = 340;
    private static final int WIN_H = 216;
    private static final int LIST_TOP_OFFSET = 50;
    private static final int LIST_BOTTOM_OFFSET = 12;
    private static final int ROW_H = 24;
    private static final int MILESTONE_ROW_H = 22;
    private static final int TITLE_ROW_H = 22;

    private int leftPos;
    private int topPos;
    private int activeTab = 0; // 0 = Reward Track (1-50), 1 = Milestones, 2 = Titles
    private double scrollTrack = 0;
    private double scrollMilestones = 0;
    private double scrollTitles = 0;
    private boolean autoScrolled = false;

    private Button tabTrackBtn;
    private Button tabMilestonesBtn;
    private Button tabTitlesBtn;
    private Button claimAllBtn;

    public LedgerScreen() {
        super(Component.translatable("screen.holeydigsite.ledger.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - WIN_W) / 2;
        this.topPos = (this.height - WIN_H) / 2;

        this.tabTrackBtn = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.holeydigsite.ledger.tab.track"),
                b -> switchTab(0)
        ).bounds(leftPos + 10, topPos + 26, 84, 18).build());

        this.tabMilestonesBtn = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.holeydigsite.ledger.tab.milestones"),
                b -> switchTab(1)
        ).bounds(leftPos + 96, topPos + 26, 104, 18).build());

        this.tabTitlesBtn = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.holeydigsite.ledger.tab.titles"),
                b -> switchTab(2)
        ).bounds(leftPos + 202, topPos + 26, 56, 18).build());

        this.claimAllBtn = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.holeydigsite.ledger.claim_all"),
                b -> {
                    PacketDistributor.sendToServer(new LedgerNetwork.ClaimPayload(0));
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
        ).bounds(leftPos + WIN_W - 78, topPos + 26, 68, 18).build());

        if (!autoScrolled) {
            autoScrolled = true;
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                LedgerData data = LedgerData.get(player);
                int targetLvl = 1;
                int playerLvl = data.level();
                boolean foundUnclaimed = false;
                for (int l = 1; l <= playerLvl; l++) {
                    if (canClaim(data, player, l)) {
                        targetLvl = l;
                        foundUnclaimed = true;
                        break;
                    }
                }
                if (!foundUnclaimed) {
                    targetLvl = Math.min(LedgerDefs.MAX_LEVEL, Math.max(1, playerLvl));
                }
                int visibleH = WIN_H - LIST_TOP_OFFSET - LIST_BOTTOM_OFFSET;
                int maxScroll = Math.max(0, LedgerDefs.MAX_LEVEL * ROW_H - visibleH);
                this.scrollTrack = Mth.clamp((targetLvl - 2) * ROW_H, 0, maxScroll);
            }
        }
        updateButtons();
    }

    private void switchTab(int tab) {
        this.activeTab = tab;
        updateButtons();
    }

    /** A level the player has reached, not yet claimed, and whose rewards can all be given (no "Coming soon"). */
    private static boolean canClaim(LedgerData data, Player player, int lvl) {
        return data.level() >= lvl && !data.isLevelClaimed(lvl)
                && LedgerManager.isLevelReady(LedgerDefs.level(lvl), player.registryAccess());
    }

    private void updateButtons() {
        Player player = Minecraft.getInstance().player;
        LedgerData data = player != null ? LedgerData.get(player) : new LedgerData();
        int doneMs = data.completedMilestones().size();
        int totalMs = LedgerDefs.milestones().size();
        if (this.tabMilestonesBtn != null) {
            this.tabMilestonesBtn.setMessage(Component.translatable("screen.holeydigsite.ledger.tab.milestones_count", doneMs, totalMs));
        }
        boolean hasClaimable = false;
        if (player != null) {
            int lvl = data.level();
            for (int i = 1; i <= lvl; i++) {
                if (canClaim(data, player, i)) {
                    hasClaimable = true;
                    break;
                }
            }
        }
        if (this.claimAllBtn != null) {
            this.claimAllBtn.active = hasClaimable;
        }
        if (this.tabTrackBtn != null) this.tabTrackBtn.active = activeTab != 0;
        if (this.tabMilestonesBtn != null) this.tabMilestonesBtn.active = activeTab != 1;
        if (this.tabTitlesBtn != null) this.tabTitlesBtn.active = activeTab != 2;
    }

    @Override
    public void tick() {
        super.tick();
        updateButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        LedgerData data = LedgerData.get(player);
        LedgerDefs.Curve curve = LedgerDefs.curve();

        // Header: Title (+ chosen player title) & Level & Renown bar
        String active = data.activeTitle();
        if (active.isEmpty()) {
            g.drawString(this.font, this.title, leftPos + 10, topPos + 9, 0xFFF3D27A, true);
        } else {
            g.drawString(this.font, this.title, leftPos + 10, topPos + 4, 0xFFF3D27A, true);
            g.drawString(this.font, Component.literal("[").append(Component.translatable(LedgerDefs.titleKey(active))).append("]"),
                    leftPos + 10, topPos + 14, 0xFFFFFFFF, true);
        }

        int lvl = data.level();
        int renown = data.renown();
        int prevCum = curve.cumulativeForLevel(lvl);
        int nextCum = lvl < LedgerDefs.MAX_LEVEL ? curve.cumulativeForLevel(lvl + 1) : prevCum;
        int inLevel = renown - prevCum;
        int needForNext = Math.max(1, nextCum - prevCum);
        float progress = lvl >= LedgerDefs.MAX_LEVEL ? 1.0F : Mth.clamp((float) inLevel / needForNext, 0.0F, 1.0F);

        int barX = leftPos + 145;
        int barY = topPos + 8;
        int barW = WIN_W - 155;
        int barH = 12;
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF1A140E);
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF33281E);
        int fillW = (int) (barW * progress);
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH, 0xFFD49B27);
        }
        Component barLabel = lvl >= LedgerDefs.MAX_LEVEL
                ? Component.translatable("screen.holeydigsite.ledger.header_max", lvl, renown)
                : Component.translatable("screen.holeydigsite.ledger.header_bar", lvl, renown, nextCum);
        g.drawCenteredString(this.font, barLabel, barX + barW / 2, barY + 2, 0xFFFFFFFF);

        // List area
        int listX = leftPos + 8;
        int listY = topPos + LIST_TOP_OFFSET;
        int listW = WIN_W - 16;
        int listH = WIN_H - LIST_TOP_OFFSET - LIST_BOTTOM_OFFSET;

        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF1A140E);
        g.fill(listX, listY, listX + listW, listY + listH, 0xFF241D16);

        ItemStack hoveredStack = ItemStack.EMPTY;
        List<Component> hoveredLines = null;

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        if (activeTab == 0) {
            TrackHover hover = renderTrackTab(g, player, data, curve, listX, listY, listW, listH, mouseX, mouseY);
            hoveredStack = hover.stack;
            hoveredLines = hover.lines;
        } else if (activeTab == 1) {
            renderMilestonesTab(g, data, curve, listX, listY, listW, listH, mouseX, mouseY);
        } else {
            renderTitlesTab(g, data, listX, listY, listW, listH, mouseX, mouseY);
        }
        g.disableScissor();

        if (hoveredLines != null && !hoveredLines.isEmpty()) {
            g.renderComponentTooltip(this.font, hoveredLines, mouseX, mouseY);
        } else if (!hoveredStack.isEmpty()) {
            g.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        // Outer ledger frame
        g.fill(leftPos - 2, topPos - 2, leftPos + WIN_W + 2, topPos + WIN_H + 2, 0xFF140F0A);
        g.fill(leftPos, topPos, leftPos + WIN_W, topPos + WIN_H, 0xFF3B2E22);
        g.fill(leftPos + 2, topPos + 2, leftPos + WIN_W - 2, topPos + WIN_H - 2, 0xFF2D2218);
    }

    /** What the mouse hovers on the reward track: an item icon, or a row whose reward text did not fit in two lines. */
    private record TrackHover(ItemStack stack, List<Component> lines) {}

    private TrackHover renderTrackTab(GuiGraphics g, Player player, LedgerData data, LedgerDefs.Curve curve,
                                      int listX, int listY, int listW, int listH, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        List<Component> hoveredLines = null;
        int playerLvl = data.level();
        int totalH = LedgerDefs.MAX_LEVEL * ROW_H;
        int maxScroll = Math.max(0, totalH - listH);
        scrollTrack = Mth.clamp(scrollTrack, 0, maxScroll);
        boolean mouseInList = mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH;

        for (int lvl = 1; lvl <= LedgerDefs.MAX_LEVEL; lvl++) {
            int rowY = listY + (lvl - 1) * ROW_H - (int) scrollTrack;
            if (rowY + ROW_H < listY || rowY > listY + listH) continue;

            LedgerDefs.LevelDef def = LedgerDefs.level(lvl);
            boolean ready = LedgerManager.isLevelReady(def, player.registryAccess());
            boolean reached = playerLvl >= lvl;
            boolean claimed = data.isLevelClaimed(lvl);
            int bg = claimed ? 0xFF233022 : (reached && ready ? 0xFF3E311B : (lvl % 2 == 0 ? 0xFF282019 : 0xFF231C15));
            g.fill(listX + 1, rowY + 1, listX + listW - 7, rowY + ROW_H - 1, bg);

            // Level number + cumulative Renown
            int lvlColor = claimed ? 0xFF68D868 : (reached ? 0xFFFFD35B : 0xFF888078);
            String lvlStr = String.format(java.util.Locale.ROOT, "L%02d", lvl);
            g.drawString(this.font, lvlStr, listX + 5, rowY + 4, lvlColor, false);
            String reqStr = curve.cumulativeForLevel(lvl) + "R";
            g.drawString(this.font, reqStr, listX + 5, rowY + 13, 0xFF7A7067, false);

            int iconX = listX + 34;
            if (ready) {
                List<ItemStack> icons = buildDisplayStacks(def, curve);
                for (ItemStack icon : icons) {
                    g.renderItem(icon, iconX, rowY + 4);
                    g.renderItemDecorations(this.font, icon, iconX, rowY + 4);
                    if (mouseInList && mouseX >= iconX && mouseX < iconX + 16 && mouseY >= rowY + 4 && mouseY < rowY + 20) {
                        hovered = icon;
                    }
                    iconX += 18;
                }
            }

            // Summary text: wrapped to two lines (never cut mid-word); anything beyond goes to the row tooltip
            int textX = Math.max(listX + 74, iconX + 4);
            int maxTextW = Math.max(40, listW - (textX - listX) - 72);
            int textColor = reached ? 0xFFEAE2D6 : 0xFF9E9489;
            if (!ready) {
                g.drawString(this.font, Component.translatable("screen.holeydigsite.ledger.coming_soon_long"), textX, rowY + 8, 0xFF9E9489, false);
            } else {
                List<String> parts = buildLevelParts(def, curve, player);
                List<String> lines = wrapParts(parts, maxTextW);
                boolean overflow = lines.size() > 2;
                int n = Math.min(2, lines.size());
                int firstY = n == 1 ? rowY + 8 : rowY + 3;
                for (int i = 0; i < n; i++) {
                    String line = lines.get(i);
                    if (overflow && i == 1) {
                        line = ellipsize(line, maxTextW);
                    }
                    g.drawString(this.font, line, textX, firstY + i * 10, textColor, false);
                }
                if (overflow && mouseInList && mouseX >= textX && mouseX < textX + maxTextW && mouseY >= rowY && mouseY < rowY + ROW_H) {
                    hoveredLines = new ArrayList<>();
                    hoveredLines.add(Component.literal("L" + lvl).withStyle(ChatFormatting.GOLD));
                    for (String part : parts) {
                        hoveredLines.add(Component.literal(part).withStyle(ChatFormatting.GRAY));
                    }
                }
            }

            // Claim button / status on right
            int btnX = listX + listW - 56;
            int btnY = rowY + 4;
            int btnW = 48;
            int btnH = 16;
            if (claimed) {
                g.drawCenteredString(this.font, Component.translatable("screen.holeydigsite.ledger.claimed"), btnX + btnW / 2, btnY + 4, 0xFF68D868);
            } else if (!ready) {
                Component soon = Component.translatable("screen.holeydigsite.ledger.coming_soon");
                g.drawString(this.font, soon, listX + listW - 10 - this.font.width(soon), btnY + 4, 0xFFB0A090, false);
            } else if (reached) {
                boolean hoverBtn = mouseInList && mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, hoverBtn ? 0xFFE5AC39 : 0xFFB8831E);
                g.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, hoverBtn ? 0xFF5A4214 : 0xFF3E2D0D);
                g.drawCenteredString(this.font, Component.translatable("screen.holeydigsite.ledger.claim"), btnX + btnW / 2, btnY + 4, 0xFFFFE599);
            } else {
                g.drawCenteredString(this.font, Component.translatable("screen.holeydigsite.ledger.locked"), btnX + btnW / 2, btnY + 4, 0xFF6E655F);
            }
        }

        renderScrollbar(g, listX + listW - 5, listY, listH, scrollTrack, maxScroll);
        return new TrackHover(hovered, hoveredLines);
    }

    private List<ItemStack> buildDisplayStacks(LedgerDefs.LevelDef def, LedgerDefs.Curve curve) {
        List<ItemStack> out = new ArrayList<>();
        if (def == null) return out;
        int ingotGrams = (int) Math.round(curve.rewardIngotGrams());
        for (LedgerDefs.RewardEntry r : def.rewards()) {
            if (r.item().isPresent()) {
                Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(r.item().get());
                if (opt.isPresent() && opt.get() != Items.AIR) {
                    ItemStack s = new ItemStack(opt.get(), Math.min(64, r.count()));
                    r.clumpTier().ifPresent(t -> s.set(ModDataComponents.CLUMP_TIER.get(), t));
                    if (opt.get() instanceof IngotItem) {
                        s.set(ModDataComponents.WEIGHT.get(), ingotGrams);
                    }
                    out.add(s);
                }
            } else if (r.blueprint().isPresent()) {
                Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(r.blueprint().get());
                if (opt.isPresent() && opt.get() != Items.AIR) {
                    out.add(BlueprintItem.of(opt.get(), r.count()));
                }
            }
        }
        if (def.coins().isPresent()) {
            int copper = curve.coinCopper(def.coins().get());
            int gold = copper / Selling.GOLD;
            out.add(new ItemStack(ModItems.GOLD_COIN.get(), Math.max(1, Math.min(64, gold))));
        }
        return out;
    }

    /** The reward parts of a ready level, one entry per reward (item, blueprint, coins, title), in player-facing words. */
    private List<String> buildLevelParts(LedgerDefs.LevelDef def, LedgerDefs.Curve curve, Player player) {
        List<String> parts = new ArrayList<>();
        if (def == null) return parts;
        for (LedgerDefs.RewardEntry r : def.rewards()) {
            if (r.item().isPresent()) {
                Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(r.item().get());
                if (opt.isPresent()) {
                    ItemStack st = new ItemStack(opt.get());
                    r.clumpTier().ifPresent(t -> st.set(ModDataComponents.CLUMP_TIER.get(), t));
                    if (opt.get() instanceof IngotItem) {
                        st.set(ModDataComponents.WEIGHT.get(), (int) Math.round(curve.rewardIngotGrams()));
                    }
                    String name = st.getHoverName().getString();
                    parts.add(r.count() > 1 ? name + " x" + r.count() : name);
                }
            } else if (r.blueprint().isPresent()) {
                Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(r.blueprint().get());
                opt.ifPresent(item -> parts.add(BlueprintItem.of(item).getHoverName().getString()));
            }
        }
        def.coins().ifPresent(c -> parts.add("+" + Selling.format(curve.coinCopper(c)).getString()));
        def.title().ifPresent(t -> parts.add(Component.translatable("screen.holeydigsite.ledger.title_reward",
                Component.translatable(LedgerDefs.titleKey(t))).getString()));
        return parts;
    }

    /** Packs whole reward parts into lines of at most {@code maxW} pixels; a part wider than a line wraps at word breaks. */
    private List<String> wrapParts(List<String> parts, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String piece = parts.get(i) + (i < parts.size() - 1 ? "," : "");
            String candidate = cur.length() == 0 ? piece : cur + " " + piece;
            if (this.font.width(candidate) <= maxW) {
                cur = new StringBuilder(candidate);
                continue;
            }
            if (cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder();
            }
            if (this.font.width(piece) <= maxW) {
                cur.append(piece);
            } else {
                // a single part wider than the line: word wrap it
                List<net.minecraft.network.chat.FormattedText> split = this.font.getSplitter().splitLines(piece, maxW, Style.EMPTY);
                for (int k = 0; k < split.size(); k++) {
                    if (k < split.size() - 1) {
                        lines.add(split.get(k).getString());
                    } else {
                        cur.append(split.get(k).getString());
                    }
                }
            }
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    /** Shortens a line at a word boundary so that "..." fits in {@code maxW}. */
    private String ellipsize(String line, int maxW) {
        String s = line;
        while (!s.isEmpty() && this.font.width(s + "...") > maxW) {
            int cut = s.lastIndexOf(' ');
            s = cut > 0 ? s.substring(0, cut) : "";
        }
        return s.isEmpty() ? "..." : s + "...";
    }

    private void renderMilestonesTab(GuiGraphics g, LedgerData data, LedgerDefs.Curve curve,
                                     int listX, int listY, int listW, int listH, int mouseX, int mouseY) {
        List<MilestoneRow> rows = buildMilestoneRows(data, curve);
        int totalH = rows.size() * MILESTONE_ROW_H;
        int maxScroll = Math.max(0, totalH - listH);
        scrollMilestones = Mth.clamp(scrollMilestones, 0, maxScroll);

        for (int i = 0; i < rows.size(); i++) {
            int rowY = listY + i * MILESTONE_ROW_H - (int) scrollMilestones;
            if (rowY + MILESTONE_ROW_H < listY || rowY > listY + listH) continue;
            MilestoneRow row = rows.get(i);
            if (row.header != null) {
                g.fill(listX + 1, rowY + 2, listX + listW - 7, rowY + MILESTONE_ROW_H - 1, 0xFF35281C);
                g.drawString(this.font, row.header, listX + 6, rowY + 7, 0xFFF3D27A, true);
            } else if (row.milestone != null) {
                LedgerDefs.Milestone m = row.milestone;
                boolean done = data.isMilestoneCompleted(m.id());
                int bg = done ? 0xFF212E20 : (i % 2 == 0 ? 0xFF282019 : 0xFF231C15);
                g.fill(listX + 1, rowY + 1, listX + listW - 7, rowY + MILESTONE_ROW_H - 1, bg);
                String mark = done ? "[✓]" : "[ ]";
                g.drawString(this.font, mark, listX + 5, rowY + 3, done ? 0xFF68D868 : 0xFF7A7067, false);
                String title = Component.translatable(m.titleKey()).getString();
                g.drawString(this.font, title, listX + 26, rowY + 2, done ? 0xFFA8F0A8 : 0xFFEAE2D6, false);
                String hint = Component.translatable(m.hintKey()).getString();
                g.drawString(this.font, this.font.plainSubstrByWidth(hint, listW - 75), listX + 26, rowY + 12, 0xFF9E9489, false);
                String ren = "+" + m.renown() + " R";
                g.drawString(this.font, ren, listX + listW - 10 - this.font.width(ren), rowY + 7, done ? 0xFF68D868 : 0xFFFFD35B, false);
            } else if (row.ongoingLine != null) {
                g.fill(listX + 1, rowY + 1, listX + listW - 7, rowY + MILESTONE_ROW_H - 1, 0xFF261F18);
                g.drawString(this.font, row.ongoingLine, listX + 8, rowY + 7, 0xFFD8CFC2, false);
            }
        }

        renderScrollbar(g, listX + listW - 5, listY, listH, scrollMilestones, maxScroll);
    }

    private record MilestoneRow(Component header, LedgerDefs.Milestone milestone, Component ongoingLine) {}

    private List<MilestoneRow> buildMilestoneRows(LedgerData data, LedgerDefs.Curve curve) {
        List<MilestoneRow> rows = new ArrayList<>();
        for (String ch : LedgerDefs.CHAPTER_ORDER) {
            List<LedgerDefs.Milestone> list = LedgerDefs.milestonesForChapter(ch);
            if (list.isEmpty()) continue;
            long doneCount = list.stream().filter(m -> data.isMilestoneCompleted(m.id())).count();
            MutableComponent hdr = Component.translatable("ledger.holeydigsite.chapter." + ch)
                    .append(Component.literal(" (" + doneCount + "/" + list.size() + ")").withStyle(ChatFormatting.GRAY));
            rows.add(new MilestoneRow(hdr, null, null));
            for (LedgerDefs.Milestone m : list) {
                rows.add(new MilestoneRow(null, m, null));
            }
        }
        rows.add(new MilestoneRow(Component.translatable("screen.holeydigsite.ledger.ongoing_header"), null, null));
        rows.add(new MilestoneRow(null, null, Component.translatable("screen.holeydigsite.ledger.ongoing.pan_sluice",
                data.panRenown(), curve.pan().cap(), data.sluiceRenown(), curve.sluice().cap())));
        rows.add(new MilestoneRow(null, null, Component.translatable("screen.holeydigsite.ledger.ongoing.farm_smith",
                data.harvestRenown(), curve.harvest().cap(), data.smithRenown(), curve.smith().cap())));
        rows.add(new MilestoneRow(null, null, Component.translatable("screen.holeydigsite.ledger.ongoing.sell",
                data.sellRenown(), curve.sell().cap(), Selling.format(data.lifetimeCopper()))));
        return rows;
    }

    // ---- Titles tab (Phase 21b) ----

    /** Row 0 = "No title", then one row per title in level order. Returns the title id of a row ("" for none). */
    private static String titleAtRow(int row) {
        if (row == 0) return "";
        List<String> titles = LedgerDefs.titles();
        return row - 1 < titles.size() ? titles.get(row - 1) : null;
    }

    /** The level whose claim unlocks a title (0 if no level gives it). */
    private static int levelOfTitle(String id) {
        for (int l = 1; l <= LedgerDefs.MAX_LEVEL; l++) {
            LedgerDefs.LevelDef def = LedgerDefs.level(l);
            if (def != null && def.title().isPresent() && def.title().get().equals(id)) {
                return l;
            }
        }
        return 0;
    }

    private void renderTitlesTab(GuiGraphics g, LedgerData data, int listX, int listY, int listW, int listH, int mouseX, int mouseY) {
        int rows = 1 + LedgerDefs.titles().size();
        int totalH = rows * TITLE_ROW_H;
        int maxScroll = Math.max(0, totalH - listH);
        scrollTitles = Mth.clamp(scrollTitles, 0, maxScroll);
        String chosen = data.activeTitle();
        boolean mouseInList = mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH;

        for (int i = 0; i < rows; i++) {
            int rowY = listY + i * TITLE_ROW_H - (int) scrollTitles;
            if (rowY + TITLE_ROW_H < listY || rowY > listY + listH) continue;
            String id = titleAtRow(i);
            boolean none = id.isEmpty();
            boolean unlocked = none || data.hasTitle(id);
            boolean selected = none ? chosen.isEmpty() : chosen.equals(id);
            boolean hover = unlocked && mouseInList && mouseX < listX + listW - 7 && mouseY >= rowY && mouseY < rowY + TITLE_ROW_H;

            int bg = selected ? 0xFF3E311B : (hover ? 0xFF332A1E : (i % 2 == 0 ? 0xFF282019 : 0xFF231C15));
            g.fill(listX + 1, rowY + 1, listX + listW - 7, rowY + TITLE_ROW_H - 1, bg);
            if (selected) {
                g.fill(listX + 1, rowY + 1, listX + 3, rowY + TITLE_ROW_H - 1, 0xFFD49B27);
            }

            Component name = none ? Component.translatable("screen.holeydigsite.ledger.title_none")
                    : Component.translatable(LedgerDefs.titleKey(id));
            g.drawString(this.font, name, listX + 10, rowY + 3, unlocked ? (selected ? 0xFFFFD35B : 0xFFEAE2D6) : 0xFF77706A, false);
            Component hint;
            if (none) {
                hint = Component.translatable("screen.holeydigsite.ledger.title_none.hint");
            } else if (unlocked) {
                hint = Component.translatable("screen.holeydigsite.ledger.title_hint", name);
            } else {
                int lvl = levelOfTitle(id);
                hint = Component.translatable("screen.holeydigsite.ledger.title_locked", lvl);
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(hint.getString(), listW - 90), listX + 10, rowY + 12,
                    unlocked ? 0xFF9E9489 : 0xFF6A645E, false);
            if (selected) {
                g.drawString(this.font, Component.translatable("screen.holeydigsite.ledger.title_selected"),
                        listX + listW - 14 - this.font.width(Component.translatable("screen.holeydigsite.ledger.title_selected")),
                        rowY + 7, 0xFF68D868, false);
            } else if (!unlocked) {
                g.drawString(this.font, Component.translatable("screen.holeydigsite.ledger.locked"),
                        listX + listW - 14 - this.font.width(Component.translatable("screen.holeydigsite.ledger.locked")),
                        rowY + 7, 0xFF6E655F, false);
            }
        }

        renderScrollbar(g, listX + listW - 5, listY, listH, scrollTitles, maxScroll);
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int h, double scroll, int maxScroll) {
        g.fill(x, y, x + 4, y + h, 0xFF16110C);
        if (maxScroll <= 0) {
            g.fill(x, y, x + 4, y + h, 0xFF5A4938);
            return;
        }
        int thumbH = Math.max(16, (int) ((double) h * h / (h + maxScroll)));
        int thumbY = y + (int) ((h - thumbH) * (scroll / maxScroll));
        g.fill(x, thumbY, x + 4, thumbY + thumbH, 0xFF9C8265);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = 24;
        if (activeTab == 0) {
            scrollTrack -= scrollY * step;
        } else if (activeTab == 1) {
            scrollMilestones -= scrollY * step;
        } else {
            scrollTitles -= scrollY * step;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int listX = leftPos + 8;
        int listY = topPos + LIST_TOP_OFFSET;
        int listW = WIN_W - 16;
        int listH = WIN_H - LIST_TOP_OFFSET - LIST_BOTTOM_OFFSET;
        boolean inList = mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH;
        Player player = Minecraft.getInstance().player;
        if (button != 0 || !inList || player == null) {
            return false;
        }
        LedgerData data = LedgerData.get(player);
        if (activeTab == 0) {
            for (int lvl = 1; lvl <= LedgerDefs.MAX_LEVEL; lvl++) {
                int rowY = listY + (lvl - 1) * ROW_H - (int) scrollTrack;
                int btnX = listX + listW - 56;
                int btnY = rowY + 4;
                if (mouseX >= btnX && mouseX < btnX + 48 && mouseY >= btnY && mouseY < btnY + 16) {
                    if (canClaim(data, player, lvl)) {
                        PacketDistributor.sendToServer(new LedgerNetwork.ClaimPayload(lvl));
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
            }
        } else if (activeTab == 2) {
            int row = (int) ((mouseY - listY + scrollTitles) / TITLE_ROW_H);
            String id = titleAtRow(row);
            if (id != null && mouseX < listX + listW - 7 && (id.isEmpty() || data.hasTitle(id))) {
                PacketDistributor.sendToServer(new LedgerNetwork.SelectTitlePayload(id));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return false;
    }
}
