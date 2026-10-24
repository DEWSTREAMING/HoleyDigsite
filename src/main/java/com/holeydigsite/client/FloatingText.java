package com.holeydigsite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Name-tag style text floating above a block (pads, Money Pan, Shop Display, crucible, mold, Crafting Forge). Faces the
 * camera and is readable through blocks using a grey see-through pass and white pass, like a name tag.
 * <p>
 * Only one block's text is shown at a time, so labels of machines standing side by side never pile up.
 * Renderers ask {@link #focused(BlockPos, double)}, which also offers the block as a candidate. Once per client tick
 * {@link #pickFocus()} chooses among the candidates offered since the last tick:
 * <ol>
 *   <li>the block under the crosshair, if it is a candidate;</li>
 *   <li>else the candidate closest to the view direction (within {@link #LOOK_DEGREES}, up to {@link #TEXT_RANGE});</li>
 *   <li>else the nearest candidate within {@link #NEAR_RANGE} blocks.</li>
 * </ol>
 */
public final class FloatingText {
    private FloatingText() {}

    /** Distance in blocks within which floating text can be drawn when looked at. */
    public static final double TEXT_RANGE = 8.0;
    /** Without looking at anything, the nearest text within this distance is shown. */
    public static final double NEAR_RANGE = 3.0;
    /** Looking at means the labelled thing is within this angle of the view direction. */
    public static final double LOOK_DEGREES = 10.0;
    /** Distance within which the little items on pads or displays are drawn. */
    public static final double ITEM_RANGE = 24.0;
    /** Text scale for labels, where vanilla name tags use 0.025. */
    public static final float SMALL = 0.0125F;
    /** Distance between two stacked label lines at {@link #SMALL}, using a 9 px font line plus 1 px gap. */
    public static final double LINE = 10 * SMALL;
    /** Closer than this, labels shrink so they keep about the size they have at this distance. */
    public static final double FULL_SIZE_DIST = 3.0;
    /** Ticks without any candidate before the focus is dropped. */
    private static final int KEEP_TICKS = 5;

    /** Candidates offered since the last pick, mapping position to the world point of the labelled thing. */
    private static final Map<BlockPos, Vec3> CANDIDATES = new HashMap<>();
    private static BlockPos focus;
    private static int emptyTicks;

    /** Squared distance from the camera to the middle of the block. */
    public static double distSq(BlockPos pos) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return pos.distToCenterSqr(cam.x, cam.y, cam.z);
    }

    public static boolean near(BlockPos pos, double range) {
        return distSq(pos) <= range * range;
    }

    /**
     * Offers the block's label if within {@link #TEXT_RANGE} and returns whether it is the one label to draw now.
     * {@code anchorY} is the height above the block's bottom of the thing being labelled.
     */
    public static boolean focused(BlockPos pos, double anchorY) {
        if (!near(pos, TEXT_RANGE)) {
            return false;
        }
        CANDIDATES.putIfAbsent(pos.immutable(), new Vec3(pos.getX() + 0.5, pos.getY() + anchorY, pos.getZ() + 0.5));
        return pos.equals(focus);
    }

    /**
     * Label scale for a block, using {@link #SMALL} from {@link #FULL_SIZE_DIST} onwards and shrinking closer down to half.
     */
    public static float labelScale(BlockPos pos) {
        double d = Math.sqrt(distSq(pos));
        return (float) (SMALL * Math.clamp(d / FULL_SIZE_DIST, 0.5, 1.0));
    }

    /** The block whose label is shown, or null if none, used for the screenshot tool. */
    public static BlockPos focus() {
        return focus;
    }

    /** Once per client tick, choose the label to show from the candidates the renderers offered. */
    public static void pickFocus() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            focus = null;
            CANDIDATES.clear();
            return;
        }
        if (CANDIDATES.isEmpty()) {
            if (++emptyTicks > KEEP_TICKS) {
                focus = null;
            }
            return;
        }
        emptyTicks = 0;
        BlockPos pick = null;
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && CANDIDATES.containsKey(bh.getBlockPos())) {
            pick = bh.getBlockPos();
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        if (pick == null) {
            Vec3 look = mc.player.getViewVector(1.0F);
            double best = Math.cos(Math.toRadians(LOOK_DEGREES));
            for (Map.Entry<BlockPos, Vec3> e : CANDIDATES.entrySet()) {
                Vec3 to = e.getValue().subtract(eye);
                double len = to.length();
                if (len < 1.0E-4 || len > TEXT_RANGE) continue;
                double cos = to.dot(look) / len;
                if (cos > best) {
                    best = cos;
                    pick = e.getKey();
                }
            }
        }
        if (pick == null) {
            double best = NEAR_RANGE * NEAR_RANGE;
            for (Map.Entry<BlockPos, Vec3> e : CANDIDATES.entrySet()) {
                double d = e.getValue().distanceToSqr(eye);
                if (d <= best) {
                    best = d;
                    pick = e.getKey();
                }
            }
        }
        focus = pick;
        CANDIDATES.clear();
    }

    /**
     * Draws {@code text} centred at (x, y, z) relative to the block. {@code scale} is the vanilla name-tag scale
     * times a factor, and {@link #SMALL} is used for ordinary labels.
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, Component text, double x, double y, double z,
                            float scale, int light) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(scale, -scale, scale);
        Matrix4f m = pose.last().pose();
        int bg = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        float w = -font.width(text) / 2.0F;
        font.drawInBatch(text, w, 0, 553648127, false, m, buffers, Font.DisplayMode.SEE_THROUGH, bg, light);
        font.drawInBatch(text, w, 0, -1, false, m, buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
