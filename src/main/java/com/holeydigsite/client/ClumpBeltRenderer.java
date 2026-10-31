package com.holeydigsite.client;

import com.holeydigsite.belt.BeltLanes;
import com.holeydigsite.belt.BeltMode;
import com.holeydigsite.belt.BeltShape;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.belt.DownInclineBeltBlock;
import com.holeydigsite.belt.FilterBeltBlock;
import com.holeydigsite.belt.InclineBeltBlock;
import com.holeydigsite.belt.SplitterBeltBlock;
import com.holeydigsite.tier.DirtTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Quaternionf;

import java.util.Iterator;

/**
 * Draws the items on a belt, the drop into the pan and bounce off a full pan animations, items
 * following a corner's curve or an incline's ramp, and a Filter Belt's filter icon. Positions come from the block
 * entity's steps, interpolated with the partial tick only while the belt is MOVING and running (frozen items don't
 * jitter). Returns immediately for an empty belt without a filter.
 */
public class ClumpBeltRenderer implements BlockEntityRenderer<ClumpBeltBlockEntity> {
    /** Size of an item relative to a dropped item. */
    private static final float SCALE = 0.75F;
    /** GROUND display moves items up 2/16 * 0.5, so lifting by scale * 2/16 puts the model's bottom on the belt. */
    private static float lift(float scale) {
        return scale * 2.0F / 16.0F;
    }

    /** Draw scale of an item on a belt: lumps are a bit smaller so a busy line does not run together. */
    private static float beltScale(ItemStack stack) {
        return FlatItems.isLump(stack) ? SCALE * BeltLanes.LUMP_SCALE : SCALE;
    }
    /** Where on the belt (0 = back edge, 1 = front edge) an item at step 0 sits, plus 1/STEPS per step. */
    private static final float START = 1.5F / ClumpBeltBlockEntity.STEPS;
    /** Pan dirt surface height inside the pan, in blocks. */
    private static final float PAN_FLOOR = 1.5F / 16.0F;

    private final ItemRenderer itemRenderer;
    /** One clump stack per kind (index = kind) for the drop and bounce animations. */
    private final ItemStack[] clumps = {ItemStack.EMPTY, DirtTier.clumps(1, 1), DirtTier.clumps(2, 1), DirtTier.clumps(3, 1),
            DirtTier.clumps(4, 1), DirtTier.clumps(5, 1), DirtTier.clumps(6, 1), DirtTier.clumps(7, 1), DirtTier.clumps(8, 1),
            DirtTier.clumps(9, 1), DirtTier.clumps(10, 1), DirtTier.clumps(11, 1)};




    public ClumpBeltRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ClumpBeltBlockEntity belt, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = belt.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = belt.getBlockState();
        if (!(state.getBlock() instanceof ClumpBeltBlock)) {
            return;
        }
        boolean hasFilter = !belt.filter().isEmpty();
        boolean showSpeedLabel = (state.getBlock() instanceof FilterBeltBlock || state.getBlock() instanceof SplitterBeltBlock)
                && Minecraft.getInstance().hitResult instanceof BlockHitResult bh
                && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(belt.getBlockPos());
        if (belt.clumps().isEmpty() && belt.drops().isEmpty() && belt.bounces().isEmpty() && !hasFilter && !showSpeedLabel) {
            return;
        }
        Direction facing = state.getValue(ClumpBeltBlock.FACING);
        boolean moving = state.getValue(ClumpBeltBlock.MODE) == BeltMode.MOVING && ClumpBeltBlock.running(state);
        float pt = moving ? partialTick : 1.0F;
        float yaw0 = -facing.toYRot();
        boolean incline = state.getBlock() instanceof InclineBeltBlock;
        boolean downIncline = state.getBlock() instanceof DownInclineBeltBlock;
        BeltShape shape = ClumpBeltBlock.shape(state);
        long now = level.getGameTime();

        // Items on the belt
        for (ClumpBeltBlockEntity.Clump c : belt.clumps()) {
            float step = Mth.lerp(pt, c.prevPos(), c.pos());
            float along = START + step / ClumpBeltBlockEntity.STEPS;
            float sc = beltScale(c.stack());
            float lift = lift(sc);
            float lane = BeltLanes.offset(c.seed());
            pose.pushPose();
            float yaw = yaw0 + c.seed() * 1.41F;
            if (shape != BeltShape.STRAIGHT && c.side() < 0) {
                // Corner: a quarter circle from the side edge's middle to the front edge's middle
                float theta = Mth.clamp(along, 0F, 1F) * Mth.HALF_PI;
                Direction right = facing.getClockWise();
                float sign = shape == BeltShape.LEFT ? -1F : 1F;   // The curve's centre is on the input side
                float cx = 0.5F + facing.getStepX() * 0.5F + right.getStepX() * 0.5F * sign;
                float cz = 0.5F + facing.getStepZ() * 0.5F + right.getStepZ() * 0.5F * sign;
                float cos = Mth.cos(theta), sin = Mth.sin(theta);
                // the lane changes the radius: the curve's centre is on the inner side
                float radius = 0.5F - lane * sign;
                float x = cx + radius * (-cos * facing.getStepX() - sign * sin * right.getStepX());
                float z = cz + radius * (-cos * facing.getStepZ() - sign * sin * right.getStepZ());
                pose.translate(x, ClumpBeltBlock.SURFACE + lift, z);
                yaw += sign * (90F - theta * Mth.RAD_TO_DEG);
            } else {
                float side = 0;
                Direction from = null;
                if (c.side() >= 0 && (c.sideTicks() > 0 || c.prevSideTicks() > 0)) {
                    from = Direction.from3DDataValue(c.side());
                    side = 0.5F * Mth.lerp(pt, c.prevSideTicks(), c.sideTicks()) / ClumpBeltBlockEntity.SIDE_TICKS;
                }
                // an item still sliding in from the side moves into its lane as it arrives
                lane *= 1.0F - side * 2.0F;
                if (incline) {
                    // Stand on the 45 degree surface, tilting up to the surface normal and lifting along it. The tilt eases out over the ends so the handover to flat belts is smooth.
                    translateOnBelt(pose, facing, along, from, side, lane, InclineBeltBlock.surfaceAt(along));
                    float tilt = 45F * Mth.clamp(Math.min(along, 1F - along) / 0.06F, 0F, 1F);
                    Direction right = facing.getClockWise();
                    pose.mulPose(new Quaternionf().rotationAxis(tilt * Mth.DEG_TO_RAD, right.getStepX(), 0, right.getStepZ()));
                    pose.translate(0, lift, 0);
                } else if (downIncline) {
                    // Stand on the 45 degree surface sloping downwards, leaning forward.
                    translateOnBelt(pose, facing, along, from, side, lane, DownInclineBeltBlock.surfaceAt(along));
                    float tilt = -45F * Mth.clamp(Math.min(along, 1F - along) / 0.06F, 0F, 1F);
                    Direction right = facing.getClockWise();
                    pose.mulPose(new Quaternionf().rotationAxis(tilt * Mth.DEG_TO_RAD, right.getStepX(), 0, right.getStepZ()));
                    pose.translate(0, lift, 0);
                } else {
                    translateOnBelt(pose, facing, along, from, side, lane, ClumpBeltBlock.SURFACE + lift);
                }
            }
            draw(pose, buffers, light, yaw, sc, level, c.seed(), c.stack());
            pose.popPose();
        }

        // Dropping into the pan: an arc over the spout lip, landing in the pan's middle
        float exit = START + 1.0F; // One step past the end
        float dropStartY = downIncline ? 0.0F : ClumpBeltBlock.SURFACE;
        float dropTargetY = downIncline ? -1.0F + PAN_FLOOR : PAN_FLOOR;
        for (Iterator<ClumpBeltBlockEntity.Drop> it = belt.drops().iterator(); it.hasNext(); ) {
            ClumpBeltBlockEntity.Drop d = it.next();
            if (d.clientStart() == Long.MIN_VALUE) {
                d.setClientStart(now - (ClumpBeltBlockEntity.DROP_TICKS - d.ticksLeft()));
            }
            float t = (now - d.clientStart() + partialTick) / ClumpBeltBlockEntity.DROP_TICKS;
            if (t >= 1.0F) {
                it.remove(); // Landed
                continue;
            }
            t = Math.max(0, t);
            float along = Mth.lerp(t, exit, 1.5F);
            ItemStack dropStack = clumps[DirtTier.clamp(d.tier())];
            float base = beltScale(dropStack);
            float y = Mth.lerp(t, dropStartY, dropTargetY) + 0.2F * Mth.sin(t * Mth.PI) + lift(base);
            float scale = t > 0.7F ? base * (1.0F - (t - 0.7F)) : base;
            pose.pushPose();
            translateOnBelt(pose, facing, along, null, 0, BeltLanes.offset(d.seed()) * (1.0F - t), y);
            draw(pose, buffers, light, yaw0 + d.seed() * 1.41F, scale, level, d.seed(), dropStack);
            pose.popPose();
        }

        // Bounce off a full pan: a short hop back up, then gone
        float bounceBaseY = downIncline ? 0.0F : ClumpBeltBlock.SURFACE;
        for (Iterator<ClumpBeltBlockEntity.Bounce> it = belt.bounces().iterator(); it.hasNext(); ) {
            ClumpBeltBlockEntity.Bounce b = it.next();
            float t = (now - b.start() + partialTick) / ClumpBeltBlockEntity.BOUNCE_TICKS;
            if (t >= 1.0F) {
                it.remove();
                continue;
            }
            t = Math.max(0, t);
            float along = exit - 0.18F * t;
            ItemStack bounceStack = clumps[DirtTier.clamp(b.tier())];
            float base = beltScale(bounceStack);
            float y = bounceBaseY + 0.3F * Mth.sin(t * Mth.PI) + lift(base);
            pose.pushPose();
            translateOnBelt(pose, facing, along, null, 0, BeltLanes.offset(b.seed()), y);
            draw(pose, buffers, light, yaw0 + b.seed() * 1.41F + t * 120.0F, base * (1.0F - 0.4F * t), level, b.seed(), bounceStack);
            pose.popPose();
        }

        // Filter Belt: the filter item hovering over the side matches leave from
        if (hasFilter && state.getBlock() instanceof FilterBeltBlock) {
            Direction out = FilterBeltBlock.outSide(state);
            pose.pushPose();
            pose.translate(0.5F + out.getStepX() * 0.3F, 1.28F + 0.03F * Mth.sin((now + partialTick) / 10F), 0.5F + out.getStepZ() * 0.3F);
            pose.mulPose(Axis.YP.rotationDegrees((now + partialTick) * 3F));
            pose.scale(0.45F, 0.45F, 0.45F);
            itemRenderer.renderStatic(belt.filter(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
            pose.popPose();
        }

        // Speed label when looking at a Filter Belt or Belt Splitter
        if (showSpeedLabel && FloatingText.focused(belt.getBlockPos(), 1.0)) {
            float sc = FloatingText.labelScale(belt.getBlockPos());
            double y = hasFilter ? 1.55 : 1.25;
            FloatingText.draw(pose, buffers, ClumpBeltBlock.speedLabel(state).copy().withStyle(
                    ClumpBeltBlock.speed(state) > 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY), 0.5, y, 0.5, sc, light);
        }
    }

    /**
     * Moves to a point on the belt: {@code along} 0..1 from the back edge to the front edge (more than 1 = past the
     * front), {@code side} 0..0.5 towards the {@code from} edge,
     * {@code lane} blocks to the right of the belt's direction.
     */
    private static void translateOnBelt(PoseStack pose, Direction facing, float along, Direction from, float side, float lane, float y) {
        Direction right = facing.getClockWise();
        float x = 0.5F + facing.getStepX() * (along - 0.5F) + right.getStepX() * lane;
        float z = 0.5F + facing.getStepZ() * (along - 0.5F) + right.getStepZ() * lane;
        if (from != null) {
            x += from.getStepX() * side;
            z += from.getStepZ() * side;
        }
        pose.translate(x, y, z);
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, int light, float yaw, float scale, Level level, int seed, ItemStack stack) {
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, seed);
    }

    /** Items that drop or bounce are drawn over the block in front, so include it to prevent early culling. */
    @Override
    public AABB getRenderBoundingBox(ClumpBeltBlockEntity belt) {
        return new AABB(belt.getBlockPos()).inflate(1.0);
    }
}
