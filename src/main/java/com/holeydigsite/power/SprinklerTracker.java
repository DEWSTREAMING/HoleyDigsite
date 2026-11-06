package com.holeydigsite.power;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks active (water-powered) sprinklers per dimension to accelerate nearby crop growth during
 * {@link CropGrowEvent.Pre}.
 *
 * <p><b>Cost model:</b> No ticking. When a sprinkler gains or loses power, its coordinate is added
 * or removed from a simple set. During a crop's random growth check, we test whether an active sprinkler
 * covers the crop, which is a handful of integer comparisons rather than world block scanning.</p>
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class SprinklerTracker {
    private SprinklerTracker() {}

    public static final int RANGE = 4;
    public static final int VERTICAL_RANGE = 1;
    public static final float BONUS_GROWTH_CHANCE = 0.40f;

    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE_SPRINKLERS = new HashMap<>();

    public static synchronized void setSprinklerPowered(ServerLevel level, BlockPos pos, boolean powered) {
        Set<BlockPos> set = ACTIVE_SPRINKLERS.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        if (powered) {
            set.add(pos.immutable());
        } else {
            set.remove(pos);
        }
    }

    public static synchronized void removeSprinkler(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = ACTIVE_SPRINKLERS.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    public static synchronized boolean isNearWorkingSprinkler(LevelReader level, BlockPos pos) {
        if (level instanceof ServerLevel sl) {
            Set<BlockPos> set = ACTIVE_SPRINKLERS.get(sl.dimension());
            if (set != null && !set.isEmpty()) {
                int px = pos.getX();
                int py = pos.getY();
                int pz = pos.getZ();
                for (BlockPos sp : set) {
                    if (Math.abs(sp.getX() - px) <= RANGE
                            && Math.abs(sp.getZ() - pz) <= RANGE
                            && Math.abs(sp.getY() - py) <= VERTICAL_RANGE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onCropGrowPre(CropGrowEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            if (isNearWorkingSprinkler(sl, event.getPos())) {
                if (event.getResult() == CropGrowEvent.Pre.Result.DEFAULT) {
                    if (sl.random.nextFloat() < BONUS_GROWTH_CHANCE) {
                        event.setResult(CropGrowEvent.Pre.Result.GROW);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (SprinklerTracker.class) {
            ACTIVE_SPRINKLERS.clear();
        }
    }
}
