package com.holeydigsite.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable two-level radial menu screen rendered around the crosshair without pausing the game.
 *
 * <ul>
 *   <li>Inner ring: categories ({@link Category}). A category with a single {@link Slice} can be picked directly from
 *       the inner ring, while hovering a multi-slice category opens its slices in the outer ring.</li>
 *   <li>Outer ring: the slices of the currently opened multi-slice category, arranged in an arc centred on that category.</li>
 *   <li>Each slice renders its result item icon, tinted green if {@link Slice#usable()} or greyed if not.</li>
 *   <li>The highlighted slice shows an info panel with result name and count, alt-recipe variant label, required
 *       metals or parts, and readiness status.</li>
 *   <li>Releasing right-click selects the highlighted slice, whereas releasing in the centre dead zone or pressing Escape cancels.</li>
 * </ul>
 */
public class RadialMenu extends Screen {
    public static final int DEAD_RADIUS = 22;
    public static final int INNER_MIN = 24;
    public static final int INNER_MAX = 60;
    public static final int OUTER_MIN = 66;
    public static final int OUTER_MAX = 104;

    private static final double GAP_ANGLE = Math.toRadians(2.5);
    private static final double OUTER_SLICE_SPAN = Math.toRadians(44.0);

    // Slice fill colours (ARGB)
    private static final int COL_USABLE = 0xCC1E5A28;
    private static final int COL_USABLE_HOVER = 0xEE2E8B3D;
    private static final int COL_UNUSABLE = 0xB826262C;
    private static final int COL_UNUSABLE_HOVER = 0xDC3E3E48;
    private static final int COL_DEAD_ZONE = 0x90141418;
    private static final int COL_ACCENT_HOVER = 0xFF66FF66;
    private static final int COL_ACCENT_UNUSABLE_HOVER = 0xFFD0D0D8;
    private static final int COL_ACCENT_CURRENT = 0xFFFFD84A;

    /** One selectable recipe or option slice in the radial wheel. */
    public record Slice(
            ResourceLocation id,
            ItemStack icon,
            Component title,
            @Nullable Component variant,
            List<Component> details,
            boolean usable,
            boolean current
    ) {}

    /** One inner-ring category grouping one or more {@link Slice}s. */
    public record Category(
            String id,
            Component title,
            ItemStack icon,
            List<Slice> slices
    ) {
        public boolean isSingle() {
            return slices.size() == 1;
        }

        public boolean anyUsable() {
            for (Slice s : slices) {
                if (s.usable()) {
                    return true;
                }
            }
            return false;
        }

        public boolean anyCurrent() {
            for (Slice s : slices) {
                if (s.current()) {
                    return true;
                }
            }
            return false;
        }
    }

    private final List<Category> categories;
    private final Consumer<ResourceLocation> onSelect;
    private int activeCategory = -1;
    private int activeSlice = -1;
    private boolean openedWithHold;
    private boolean finished;
    private double lastMouseX;
    private double lastMouseY;

    public RadialMenu(Component title, List<Category> categories, Consumer<ResourceLocation> onSelect) {
        super(title);
        this.categories = List.copyOf(categories);
        this.onSelect = onSelect;
    }

    public List<Category> categories() {
        return categories;
    }

    public int activeCategory() {
        return activeCategory;
    }

    @Nullable
    public Slice highlightedSlice() {
        if (activeCategory < 0 || activeCategory >= categories.size()) {
            return null;
        }
        Category cat = categories.get(activeCategory);
        if (activeSlice < 0 || activeSlice >= cat.slices().size()) {
            return null;
        }
        return cat.slices().get(activeSlice);
    }

    @Override
    protected void init() {
        super.init();
        this.openedWithHold = isUseDown();
        this.lastMouseX = this.width / 2.0;
        this.lastMouseY = this.height / 2.0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world crisp behind the wheel without full-screen blur or dim overlays.
    }

    /** Updates the hovered category and slice from GUI-scaled cursor coordinates. */
    public void updateHover(double mouseX, double mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        if (categories.isEmpty()) {
            activeCategory = -1;
            activeSlice = -1;
            return;
        }
        double cx = this.width / 2.0;
        double cy = this.height / 2.0;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.hypot(dx, dy);

        if (dist < DEAD_RADIUS) {
            activeCategory = -1;
            activeSlice = -1;
            return;
        }

        double angle = normalizeAngle(Math.atan2(dy, dx) + Math.PI / 2.0);
        int nCats = categories.size();
        double catStep = (Math.PI * 2.0) / nCats;

        if (dist <= INNER_MAX || activeCategory < 0) {
            int newCat = Mth.clamp((int) Math.floor(angle / catStep), 0, nCats - 1);
            if (newCat != activeCategory) {
                activeCategory = newCat;
                playTickSound(0.75F);
            }
            Category cat = categories.get(activeCategory);
            if (cat.isSingle()) {
                activeSlice = 0;
            } else if (dist <= INNER_MAX) {
                activeSlice = -1;
            }
        }

        if (activeCategory >= 0 && activeCategory < nCats) {
            Category cat = categories.get(activeCategory);
            if (cat.isSingle()) {
                activeSlice = 0;
            } else if (dist > INNER_MAX) {
                int m = cat.slices().size();
                double catCenter = (activeCategory + 0.5) * catStep;
                double span = Math.min(Math.PI * 2.0, m * OUTER_SLICE_SPAN);
                double rel = signedAngleDiff(angle, catCenter);
                double halfSpan = span / 2.0;
                if (Math.abs(rel) <= halfSpan + Math.toRadians(18.0)) {
                    int idx = Mth.clamp((int) Math.floor((rel + halfSpan) / (span / m)), 0, m - 1);
                    if (idx != activeSlice) {
                        activeSlice = idx;
                        playTickSound(1.0F);
                    }
                } else {
                    // If the cursor moves far around the wheel outside the open arc, switch to the category at this angle.
                    int newCat = Mth.clamp((int) Math.floor(angle / catStep), 0, nCats - 1);
                    if (newCat != activeCategory) {
                        activeCategory = newCat;
                        activeSlice = categories.get(activeCategory).isSingle() ? 0 : -1;
                        playTickSound(0.75F);
                    }
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateHover(mouseX, mouseY);

        if (openedWithHold && !isUseDown() && !finished) {
            completeSelection();
            return;
        }

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Centre dead-zone disc
        drawRingSector(graphics, cx, cy, 0, DEAD_RADIUS - 2, 0, Math.PI * 2.0, COL_DEAD_ZONE, 28);

        int nCats = categories.size();
        if (nCats > 0) {
            double catStep = (Math.PI * 2.0) / nCats;
            List<IconPlacement> icons = new ArrayList<>();

            // 1. Inner ring: categories
            for (int i = 0; i < nCats; i++) {
                Category cat = categories.get(i);
                double a0 = i * catStep + (nCats > 1 ? GAP_ANGLE / 2.0 : 0);
                double a1 = (i + 1) * catStep - (nCats > 1 ? GAP_ANGLE / 2.0 : 0);
                boolean hovered = (i == activeCategory);
                boolean usable = cat.anyUsable();
                int fill = usable
                        ? (hovered ? COL_USABLE_HOVER : COL_USABLE)
                        : (hovered ? COL_UNUSABLE_HOVER : COL_UNUSABLE);
                drawRingSector(graphics, cx, cy, INNER_MIN, INNER_MAX, a0, a1, fill, 16);

                if (hovered) {
                    int accent = usable ? COL_ACCENT_HOVER : COL_ACCENT_UNUSABLE_HOVER;
                    drawRingSector(graphics, cx, cy, INNER_MAX - 2, INNER_MAX, a0, a1, accent, 16);
                }
                if (cat.anyCurrent()) {
                    drawRingSector(graphics, cx, cy, INNER_MIN, INNER_MIN + 2, a0, a1, COL_ACCENT_CURRENT, 16);
                }

                double midA = (a0 + a1) / 2.0;
                double midR = (INNER_MIN + INNER_MAX) / 2.0;
                int ix = (int) Math.round(cx + midR * Math.sin(midA)) - 8;
                int iy = (int) Math.round(cy - midR * Math.cos(midA)) - 8;
                icons.add(new IconPlacement(cat.icon(), ix, iy, usable, cat.isSingle()));
            }

            // 2. Outer ring: slices of the hovered multi-slice category
            if (activeCategory >= 0 && activeCategory < nCats) {
                Category cat = categories.get(activeCategory);
                if (!cat.isSingle()) {
                    int m = cat.slices().size();
                    double catCenter = (activeCategory + 0.5) * catStep;
                    double span = Math.min(Math.PI * 2.0, m * OUTER_SLICE_SPAN);
                    double sliceStep = span / m;
                    double startAngle = catCenter - span / 2.0;

                    for (int j = 0; j < m; j++) {
                        Slice slice = cat.slices().get(j);
                        double a0 = startAngle + j * sliceStep + GAP_ANGLE / 2.0;
                        double a1 = startAngle + (j + 1) * sliceStep - GAP_ANGLE / 2.0;
                        boolean hovered = (j == activeSlice);
                        boolean usable = slice.usable();
                        int fill = usable
                                ? (hovered ? COL_USABLE_HOVER : COL_USABLE)
                                : (hovered ? COL_UNUSABLE_HOVER : COL_UNUSABLE);
                        drawRingSector(graphics, cx, cy, OUTER_MIN, OUTER_MAX, a0, a1, fill, 14);

                        if (hovered) {
                            int accent = usable ? COL_ACCENT_HOVER : COL_ACCENT_UNUSABLE_HOVER;
                            drawRingSector(graphics, cx, cy, OUTER_MAX - 2, OUTER_MAX, a0, a1, accent, 14);
                        }
                        if (slice.current()) {
                            drawRingSector(graphics, cx, cy, OUTER_MIN, OUTER_MIN + 2, a0, a1, COL_ACCENT_CURRENT, 14);
                        }

                        double midA = (a0 + a1) / 2.0;
                        double midR = (OUTER_MIN + OUTER_MAX) / 2.0;
                        int ix = (int) Math.round(cx + midR * Math.sin(midA)) - 8;
                        int iy = (int) Math.round(cy - midR * Math.cos(midA)) - 8;
                        icons.add(new IconPlacement(slice.icon(), ix, iy, usable, true));
                    }
                }
            }

            graphics.flush();

            // Render item icons on top of the flushed ring geometry
            for (IconPlacement p : icons) {
                graphics.renderItem(p.stack(), p.x(), p.y());
                if (p.showCount()) {
                    graphics.renderItemDecorations(this.font, p.stack(), p.x(), p.y());
                }
                if (!p.usable()) {
                    // Subtle grey veil over unusable slice icons so usable vs unusable is clear at a glance
                    graphics.pose().pushPose();
                    graphics.pose().translate(0.0F, 0.0F, 200.0F);
                    graphics.fill(p.x(), p.y(), p.x() + 16, p.y() + 16, 0x6618181C);
                    graphics.pose().popPose();
                }
            }
        }

        // Centre label or category header
        if (activeCategory >= 0 && activeCategory < categories.size()) {
            Component catTitle = categories.get(activeCategory).title().copy().withStyle(ChatFormatting.YELLOW);
            graphics.drawCenteredString(this.font, catTitle, cx, cy - 4, 0xFFFFFF);
        } else {
            Component cancelHint = Component.translatable("screen.holeydigsite.radial.cancel").withStyle(ChatFormatting.GRAY);
            graphics.drawCenteredString(this.font, cancelHint, cx, cy + OUTER_MAX + 8, 0xAAAAAA);
        }

        // Highlighted slice detail panel
        Slice highlighted = highlightedSlice();
        if (highlighted != null) {
            renderSlicePanel(graphics, cx, cy, highlighted);
        }
    }

    private void renderSlicePanel(GuiGraphics graphics, int cx, int cy, Slice slice) {
        List<Component> lines = new ArrayList<>();

        Component countTitle = slice.icon().getCount() > 1
                ? Component.translatable("text.holeydigsite.crafting_forge.count", slice.icon().getCount(), slice.title())
                : slice.title().copy();
        lines.add(countTitle.copy().withStyle(slice.usable() ? ChatFormatting.GREEN : ChatFormatting.WHITE));

        if (slice.variant() != null) {
            lines.add(Component.literal("(").append(slice.variant()).append(")").withStyle(ChatFormatting.GOLD));
        }

        lines.addAll(slice.details());

        Component status = Component.translatable(
                slice.usable() ? "screen.holeydigsite.radial.ready" : "screen.holeydigsite.radial.missing"
        ).withStyle(slice.usable() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
        lines.add(status);

        int textW = 0;
        for (Component line : lines) {
            textW = Math.max(textW, this.font.width(line));
        }
        int pad = 6;
        int iconArea = 20;
        int boxW = Math.max(110, textW + pad * 2 + iconArea);
        int boxH = pad * 2 + lines.size() * 11;

        int boxX = cx + OUTER_MAX + 12;
        int boxY = cy - boxH / 2;
        if (boxX + boxW > this.width - 6) {
            boxX = Math.max(6, cx - boxW / 2);
            boxY = Math.min(this.height - boxH - 6, cy + OUTER_MAX + 8);
        }
        boxY = Mth.clamp(boxY, 6, Math.max(6, this.height - boxH - 6));

        int bg = slice.usable() ? 0xE0142C18 : 0xE018181E;
        int border = slice.usable() ? 0xFF48C858 : 0xFF585864;
        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, bg);
        graphics.renderOutline(boxX, boxY, boxW, boxH, border);

        graphics.renderItem(slice.icon(), boxX + pad, boxY + pad);
        graphics.renderItemDecorations(this.font, slice.icon(), boxX + pad, boxY + pad);

        int ty = boxY + pad;
        for (int i = 0; i < lines.size(); i++) {
            int tx = (i <= 1) ? (boxX + pad + iconArea) : (boxX + pad);
            graphics.drawString(this.font, lines.get(i), tx, ty, 0xFFFFFF, true);
            ty += 11;
        }
    }

    private static void drawRingSector(
            GuiGraphics graphics,
            double cx,
            double cy,
            double rMin,
            double rMax,
            double a0,
            double a1,
            int argb,
            int steps
    ) {
        if (a1 <= a0 || rMax <= rMin) {
            return;
        }
        VertexConsumer vc = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f mat = graphics.pose().last().pose();
        double da = (a1 - a0) / steps;
        for (int s = 0; s < steps; s++) {
            double t0 = a0 + s * da;
            double t1 = a0 + (s + 1) * da;
            float sin0 = (float) Math.sin(t0);
            float cos0 = (float) Math.cos(t0);
            float sin1 = (float) Math.sin(t1);
            float cos1 = (float) Math.cos(t1);

            float x0Out = (float) (cx + rMax * sin0);
            float y0Out = (float) (cy - rMax * cos0);
            float x0In = (float) (cx + rMin * sin0);
            float y0In = (float) (cy - rMin * cos0);
            float x1In = (float) (cx + rMin * sin1);
            float y1In = (float) (cy - rMin * cos1);
            float x1Out = (float) (cx + rMax * sin1);
            float y1Out = (float) (cy - rMax * cos1);

            vc.addVertex(mat, x0Out, y0Out, 0.0F).setColor(argb);
            vc.addVertex(mat, x0In, y0In, 0.0F).setColor(argb);
            vc.addVertex(mat, x1In, y1In, 0.0F).setColor(argb);
            vc.addVertex(mat, x1Out, y1Out, 0.0F).setColor(argb);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateHover(mouseX, mouseY);
            completeSelection();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.finished = true;
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void completeSelection() {
        if (finished) {
            return;
        }
        finished = true;
        Slice selected = highlightedSlice();
        this.onClose();
        if (selected != null) {
            onSelect.accept(selected.id());
        }
    }

    private static boolean isUseDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return false;
        }
        long win = mc.getWindow().getWindow();
        InputConstants.Key useKey = mc.options.keyUse.getKey();
        if (useKey.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(win, useKey.getValue()) == GLFW.GLFW_PRESS;
        }
        if (useKey.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(win, useKey.getValue());
        }
        return false;
    }

    private static void playTickSound(float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.2F));
        }
    }

    private static double normalizeAngle(double a) {
        double twoPi = Math.PI * 2.0;
        double r = a % twoPi;
        return r < 0 ? r + twoPi : r;
    }

    private static double signedAngleDiff(double a, double b) {
        double diff = (a - b + Math.PI) % (Math.PI * 2.0);
        if (diff < 0) {
            diff += Math.PI * 2.0;
        }
        return diff - Math.PI;
    }

    private record IconPlacement(ItemStack stack, int x, int y, boolean usable, boolean showCount) {}
}
