package com.holeydigsite.site;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The protected footprint of a store or kiosk (foundation, building, roof, and a margin of air above) cannot be changed by survival players (no breaking including shovel scoops, placing, buckets, fire, tool conversions like axe stripping, hoe, and shovel paths, pistons, or explosions). Using the shop (displays, pads, button, Money Pan, doors) still works. Creative players are not restricted so designs can be edited in place and exported.
 *
 * <p>Fire cannot be blocked with an event when it spreads from outside, so fire appearing inside a zone or right next to it is put out on the next tick before it can burn anything.</p>
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class SiteProtection {
    private SiteProtection() {}

    private static final List<BlockPos> FIRES = new ArrayList<>();

    /** The protected zone containing {@code pos} (grown by {@code margin}), or null. Server only. */
    public static BoundingBox zoneAt(LevelAccessor level, BlockPos pos, int margin) {
        if (!(level instanceof ServerLevel sl)) {
            return null;
        }
        SiteData data = SiteData.getIfExists(sl);
        if (data == null) {
            return null;
        }
        for (SiteData.Site s : data.all()) {
            BoundingBox z = s.zone();
            if (pos.getX() < z.minX() - margin || pos.getX() > z.maxX() + margin || pos.getY() < z.minY() - margin
                    || pos.getY() > z.maxY() + margin || pos.getZ() < z.minZ() - margin || pos.getZ() > z.maxZ() + margin) {
                continue;
            }
            SiteDef def = SiteDefs.get(s.id());
            if (def != null && def.protect()) {
                return z;
            }
        }
        return null;
    }

    public static boolean isProtected(LevelAccessor level, BlockPos pos) {
        return zoneAt(level, pos, 0) != null;
    }

    private static boolean restricted(Player player) {
        return player != null && !player.getAbilities().instabuild && !player.isSpectator();
    }

    private static void tell(Player player) {
        player.displayClientMessage(Component.translatable("message.holeydigsite.site.protected").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (restricted(event.getPlayer()) && isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            tell(event.getPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity e = event.getEntity();
        if ((e instanceof Player p ? restricted(p) : e != null) && isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            if (e instanceof Player p) {
                tell(p);
            }
        }
    }

    /** Axe stripping, hoe tilling, and shovel paths. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (!event.isSimulated() && restricted(event.getPlayer()) && isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Fire starters used on or against a zone. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!restricted(player) || event.getLevel().isClientSide) {
            return;
        }
        BlockPos pos = event.getPos();
        ItemStack held = event.getItemStack();
        BlockPos against = pos.relative(event.getFace() == null ? Direction.UP : event.getFace());
        // Block placement itself is refused by onPlace (EntityPlaceEvent), cancelling every right-click with a block in hand
        // here would also stop players from using the displays or pads while holding something.
        boolean fire = held.getItem() instanceof FlintAndSteelItem || held.getItem() instanceof FireChargeItem;
        if (fire && (isProtected(event.getLevel(), pos) || isProtected(event.getLevel(), against))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            tell(player);
        }
    }

    /** Buckets aimed into a zone for scooping or pouring. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!restricted(player) || event.getLevel().isClientSide || !(event.getItemStack().getItem() instanceof BucketItem)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        BlockHitResult hit = event.getLevel().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() == HitResult.Type.BLOCK && (isProtected(event.getLevel(), hit.getBlockPos())
                || isProtected(event.getLevel(), hit.getBlockPos().relative(hit.getDirection())))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            tell(player);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel) {
            event.getAffectedBlocks().removeIf(p -> isProtected(event.getLevel(), p));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos head = event.getFaceOffsetPos();
        if (isProtected(level, head)) {
            event.setCanceled(true);
            return;
        }
        PistonStructureResolver r = event.getStructureHelper();
        if (r != null && r.resolve()) {
            for (BlockPos p : r.getToPush()) {
                if (isProtected(level, p) || isProtected(level, p.relative(r.getPushDirection()))) {
                    event.setCanceled(true);
                    return;
                }
            }
            for (BlockPos p : r.getToDestroy()) {
                if (isProtected(level, p)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    /** Lava setting things alight inside or next to a zone. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getNewState().getBlock() instanceof BaseFireBlock && zoneAt(event.getLevel(), event.getPos(), 1) != null) {
            event.setCanceled(true);
        }
    }

    /** Fire spreading from outside, remembered and put out on the next tick. */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().getBlock() instanceof BaseFireBlock && event.getLevel() instanceof ServerLevel
                && zoneAt(event.getLevel(), event.getPos(), 1) != null) {
            FIRES.add(event.getPos().immutable());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (FIRES.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BlockPos> now = new ArrayList<>(FIRES);
        FIRES.clear();
        for (BlockPos p : now) {
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof BaseFireBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
