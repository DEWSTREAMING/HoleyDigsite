package com.holeydigsite.power;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Water power: on/off networks of intakes, pipes and machines.
 *
 * <p>A machine is powered when it is connected through pipes (or directly) to at least one ACTIVE intake.
 * One intake powers any number of machines.</p>
 *
 * <p><b>Cost model:</b> nothing runs per tick while nothing changes. Blocks call {@link #markDirty} when a pipe,
 * intake or machine is placed or removed, or an intake gains/loses its water. At the end of that level tick the
 * dirty positions are processed once: a bounded flood fill ({@link #MAX_NODES}) finds each affected network, and
 * the result is pushed into the blocks - machines cache it in their block entity
 * ({@link WaterNetworkBlock#setWaterPowered}), pipes in their {@code filled} block state.
 * Batching per tick means a /fill of 100 pipes costs one flood fill, not 100.</p>
 *
 * <p>Unloaded chunks count as "no connection" (we never load chunks from here).</p>
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class WaterNetworks {
    private WaterNetworks() {}

    /**
     * Largest network (pipes + intakes + machines) that is evaluated. Bigger networks carry NO power, so a
     * runaway pipe build can't make a single update expensive.
     */
    public static final int MAX_NODES = 512;

    /** Positions changed this tick, per dimension. Server thread only. */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> DIRTY = new HashMap<>();

    /** Queue a network recompute around {@code pos} for the end of this tick. No-op on the client. */
    public static void markDirty(Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            DIRTY.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>()).add(pos.immutable());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && !DIRTY.isEmpty()) {
            Set<BlockPos> dirty = DIRTY.remove(level.dimension());
            if (dirty != null) {
                recompute(level, dirty);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DIRTY.clear();
    }

    // ------------------------------------------------------------------------------------------------
    // Recompute
    // ------------------------------------------------------------------------------------------------

    /**
     * Re-evaluates every network touching the given positions (the position itself and its 6 neighbours, so
     * a removed pipe updates both halves it used to join). Public so tests can force it synchronously.
     */
    public static void recompute(ServerLevel level, Set<BlockPos> changed) {
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos center : changed) {
            seed(level, center, done);
            for (Direction dir : Direction.values()) {
                seed(level, center.relative(dir), done);
            }
        }
    }

    private static void seed(ServerLevel level, BlockPos pos, Set<BlockPos> done) {
        if (done.contains(pos) || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WaterNetworkBlock) {
            Network network = flood(level, pos);
            done.addAll(network.visited);
            network.apply(level);
        }
    }

    /** One connected network found by {@link #flood}. */
    private static final class Network {
        final Set<BlockPos> visited = new HashSet<>();
        final List<BlockPos> pipes = new ArrayList<>();
        final List<BlockPos> machines = new ArrayList<>();
        boolean hasActiveIntake;
        boolean overflow;

        boolean powered() {
            return hasActiveIntake && !overflow;
        }

        void apply(ServerLevel level) {
            boolean powered = powered();
            for (BlockPos pos : machines) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof WaterNetworkBlock machine) {
                    machine.setWaterPowered(level, pos, state, powered);
                }
            }
            for (BlockPos pos : pipes) {
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(WaterPipeBlock.FILLED) && state.getValue(WaterPipeBlock.FILLED) != powered) {
                    // visual only: tell clients, skip neighbour updates/shape updates (nothing else depends on it)
                    level.setBlock(pos, state.setValue(WaterPipeBlock.FILLED, powered),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    /**
     * Breadth-first search from {@code start} over connected network blocks. Machines are end points: they are
     * collected but water doesn't pass through them (except when the search starts AT a machine, to find the
     * network behind its port).
     */
    private static Network flood(ServerLevel level, BlockPos start) {
        Network net = new Network();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        net.visited.add(start);
        queue.add(start);

        search:
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof WaterNetworkBlock block)) {
                continue;
            }
            switch (block.waterRole()) {
                case PIPE -> net.pipes.add(pos);
                case INTAKE -> net.hasActiveIntake |= block.isActiveIntake(state);
                case MACHINE, CONDUIT -> net.machines.add(pos);
            }
            if (block.waterRole() == WaterNetworkBlock.Role.MACHINE && !pos.equals(start)) {
                continue; // machines don't pass water on
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (net.visited.contains(next) || !level.isLoaded(next)
                        || !WaterNetworkBlock.connects(level, pos, state, dir)) {
                    continue;
                }
                if (net.visited.size() >= MAX_NODES) {
                    net.overflow = true;
                    break search;
                }
                net.visited.add(next);
                queue.add(next);
            }
            if (block.waterRole() == WaterNetworkBlock.Role.CONDUIT) {
                List<BlockPos> extra = new ArrayList<>(2);
                block.addExtraLinks(level, pos, state, extra);
                for (BlockPos next : extra) {
                    if (net.visited.contains(next) || !level.isLoaded(next)) {
                        continue;
                    }
                    if (net.visited.size() >= MAX_NODES) {
                        net.overflow = true;
                        break search;
                    }
                    net.visited.add(next);
                    queue.add(next);
                }
            }
        }
        if (net.overflow) {
            HoleyDigsite.LOGGER.debug("Water network at {} is larger than {} blocks - it carries no power", start, MAX_NODES);
        }
        return net;
    }
}
