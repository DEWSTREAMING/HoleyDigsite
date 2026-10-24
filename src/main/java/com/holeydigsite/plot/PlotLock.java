package com.holeydigsite.plot;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Locked land plots (Phase 11). While a plot isn't bought, survival/adventure players can't break or place blocks, use items
 * on blocks, interact with blocks or empty/fill buckets inside its bounds - only walk. The plot's board stays clickable (to
 * claim it with the deed). Creative players aren't restricted. Machines outside can't reach in either:
 * {@link #isLocked(Level, BlockPos)} is checked by {@code ClumpOutputs}, belts and the pan's item handler.
 * Server side only (the client predicts, the server corrects); short action-bar message on a refusal.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class PlotLock {
    private PlotLock() {}

    /** True if {@code pos} is inside a plot that hasn't been bought yet (server only; always false on clients). */
    public static boolean isLocked(LevelAccessor level, BlockPos pos) {
        return level instanceof ServerLevel sl && PlotData.get(sl).lockedAt(pos) != null;
    }

    private static boolean restricted(Player player) {
        // abilities, not isCreative(): creative = instabuild (GameTest mock players always claim isCreative())
        return player != null && !player.getAbilities().instabuild && !player.isSpectator();
    }

    private static void tell(Player player) {
        player.displayClientMessage(Component.translatable("message.holeydigsite.plot.locked").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (restricted(event.getPlayer()) && isLocked(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            tell(event.getPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity e = event.getEntity();
        if ((e instanceof Player p ? restricted(p) : e != null) && isLocked(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            if (e instanceof Player p) tell(p);
        }
    }

    /** Right-click on a block inside, or placing against a block so the new block would land inside. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!restricted(player) || event.getLevel().isClientSide) {
            return;
        }
        BlockPos pos = event.getPos();
        if (event.getLevel().getBlockState(pos).getBlock() instanceof PlotBoardBlock) {
            return; // the board takes the deed
        }
        BlockPos against = pos.relative(event.getFace() == null ? net.minecraft.core.Direction.UP : event.getFace());
        if (isLocked(event.getLevel(), pos) || isLocked(event.getLevel(), against)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            tell(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (restricted(event.getEntity()) && !event.getLevel().isClientSide && isLocked(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) tell(event.getEntity());
        }
    }

    /** Buckets aimed into a locked plot (scooping its water / pouring into it). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!restricted(player) || event.getLevel().isClientSide || !(event.getItemStack().getItem() instanceof BucketItem)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        BlockHitResult hit = event.getLevel().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() == HitResult.Type.BLOCK
                && (isLocked(event.getLevel(), hit.getBlockPos()) || isLocked(event.getLevel(), hit.getBlockPos().relative(hit.getDirection())))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            tell(player);
        }
    }

    /** Explosions don't touch locked plots. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel) {
            event.getAffectedBlocks().removeIf(p -> isLocked(event.getLevel(), p));
        }
    }
}
