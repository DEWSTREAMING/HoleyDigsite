package com.holeydigsite.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that takes part in a fluid-power network: pipes, intakes and powered machines. Since Phase 9 there are two
 * separate fluids, {@link NetworkFluid#WATER} (drills, sluices) and {@link NetworkFluid#LAVA} (forges): blocks only
 * connect to blocks of the SAME fluid, so a lava pipe never joins a water pipe, intake or port and vice versa.
 * (The name stayed "Water..." from Phase 4; {@link WaterNetworks} computes both kinds.)
 *
 * <p>Two neighbouring network blocks are connected when BOTH report a water connection on the shared face
 * ({@link #hasWaterConnection}) - pipes and intakes on every face, machines only on their water port.
 * Machines never pass water on to each other. See {@link WaterNetworks} for how power is worked out.</p>
 */
public interface WaterNetworkBlock {
    /** What a block does in the network. */
    enum Role {
        /** Carries water; connects on all six faces. */
        PIPE,
        /** Source of power while it sits in water. */
        INTAKE,
        /** End point: is powered or not, never passes water through. */
        MACHINE,
        /**
         * Phase 12 belts: carries water on to other conduits, pipes and intakes like a pipe AND is powered like a machine.
         * Conduits never connect to machines (a belt next to a drill or sluice doesn't power it).
         */
        CONDUIT
    }

    Role waterRole();

    /** The two kinds of fluid power. Networks of different fluids never connect. */
    enum NetworkFluid { WATER, LAVA }

    /** Which fluid this block carries / uses. Water unless overridden (Phase 9 lava blocks). */
    default NetworkFluid networkFluid() {
        return NetworkFluid.WATER;
    }

    /** True if water can enter/leave this block through {@code side}. */
    boolean hasWaterConnection(BlockState state, Direction side);

    /** Intakes: true while the intake is in water. Others: false. */
    default boolean isActiveIntake(BlockState state) {
        return false;
    }

    /**
     * Machines: the network was recomputed and this machine is now powered (or not).
     * Only called on network changes - never every tick. Server side.
     */
    default void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {}

    /**
     * Phase 12: extra (non-face) neighbours this block exchanges water with - Inclined Belts link diagonally to the belt
     * at their low end and to the next incline of a staircase. Must be symmetric (both blocks report each other).
     */
    default void addExtraLinks(BlockGetter level, BlockPos pos, BlockState state, java.util.List<BlockPos> out) {}

    /**
     * True if the block at {@code pos} connects to its neighbour in direction {@code dir}
     * (both sides agree, same fluid, and it isn't machine-to-machine). Used for the network and for pipe visuals.
     */
    static boolean connects(BlockGetter level, BlockPos pos, BlockState state, Direction dir) {
        if (!(state.getBlock() instanceof WaterNetworkBlock self) || !self.hasWaterConnection(state, dir)) {
            return false;
        }
        BlockState other = level.getBlockState(pos.relative(dir));
        return other.getBlock() instanceof WaterNetworkBlock that
                && that.hasWaterConnection(other, dir.getOpposite())
                && self.networkFluid() == that.networkFluid()
                && !(self.waterRole() == Role.MACHINE && that.waterRole() == Role.MACHINE)
                && !(self.waterRole() == Role.CONDUIT && that.waterRole() == Role.MACHINE)
                && !(self.waterRole() == Role.MACHINE && that.waterRole() == Role.CONDUIT);
    }
}
