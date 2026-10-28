package com.holeydigsite.plot;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.team.TeamAccess;
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
import org.jetbrains.annotations.Nullable;

/**
 * Land plot access. While a plot is unbought, survival and adventure players can only walk inside its bounds, and cannot
 * break or place blocks, use items on blocks, interact with blocks, or empty and fill buckets. Once bought, the same holds
 * for anyone outside the island's team ({@link TeamAccess}; without a team mod there is no team, so everyone may).
 * The plot board stays clickable, and creative players are not restricted. Machines are not players: they only check
 * {@link #isLocked(LevelAccessor, BlockPos)} (in {@code ItemOutputs}, belts, sluices and the pan's item handler), so they
 * keep working inside bought plots. Server side only, with a short action bar line on refusal.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class PlotLock {
    private PlotLock() {}

    public enum Access {
        OK, LOCKED, OTHER_TEAM;

        public String messageKey() {
            return this == OTHER_TEAM ? "message.holeydigsite.plot.other_team" : "message.holeydigsite.plot.locked";
        }
    }

    /** True if {@code pos} is inside an unbought plot (server only, always false on clients). */
    public static boolean isLocked(LevelAccessor level, BlockPos pos) {
        return level instanceof ServerLevel sl && PlotData.get(sl).lockedAt(pos) != null;
    }

    /**
     * Whether {@code player} may use {@code pos}: the one plot check for players. A null player (a machine) is only
     * stopped by unbought plots.
     */
    public static Access access(@Nullable Player player, LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl) || (player != null && !restricted(player))) {
            return Access.OK;
        }
        PlotData.Plot plot = PlotData.get(sl).plotAt(pos);
        if (plot == null) {
            return Access.OK;
        }
        if (!plot.owned()) {
            return Access.LOCKED;
        }
        if (player == null || TeamAccess.get().allows(sl, pos, player.getUUID())) {
            return Access.OK;
        }
        return Access.OTHER_TEAM;
    }

    /** {@link #access} for a player, telling them why on refusal. */
    public static boolean canUse(Player player, LevelAccessor level, BlockPos pos) {
        Access a = access(player, level, pos);
        if (a != Access.OK) {
            tell(player, a);
            return false;
        }
        return true;
    }

    /** The block clicked and the one next to it (where a placed block or poured fluid lands); one message at most. */
    private static boolean canUseBoth(Player player, LevelAccessor level, BlockPos a, BlockPos b) {
        Access r = access(player, level, a);
        if (r == Access.OK) {
            r = access(player, level, b);
        }
        if (r != Access.OK) {
            tell(player, r);
            return false;
        }
        return true;
    }

    /** True unless the player is in survival or adventure (creative and spectators are never stopped). */
    public static boolean restricted(Player player) {
        // abilities, not isCreative(): creative = instabuild (GameTest mock players always claim isCreative())
        return player != null && !player.getAbilities().instabuild && !player.isSpectator();
    }

    private static void tell(Player player, Access a) {
        player.displayClientMessage(Component.translatable(a.messageKey()).withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && !canUse(event.getPlayer(), event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity e = event.getEntity();
        if (e instanceof Player p) {
            if (!canUse(p, event.getLevel(), event.getPos())) {
                event.setCanceled(true);
            }
        } else if (e != null && isLocked(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Right-clicking on a block inside, or placing against a block so the new block lands inside. */
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
        if (!canUseBoth(player, event.getLevel(), pos, against)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        Access a = access(event.getEntity(), event.getLevel(), event.getPos());
        if (a != Access.OK) {
            event.setCanceled(true);
            if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) tell(event.getEntity(), a);
        }
    }

    /** Buckets aimed into a locked plot for scooping water or pouring into it. */
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
                && !canUseBoth(player, event.getLevel(), hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection()))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Explosions never touch unbought plots (bought ones are the team's own business). */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel) {
            event.getAffectedBlocks().removeIf(p -> isLocked(event.getLevel(), p));
        }
    }
}
