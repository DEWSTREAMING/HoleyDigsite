package com.holeydigsite.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that takes part in a fluid power network, including pipes, intakes, and powered machines. There are two
 * separate fluids, {@link NetworkFluid#WATER} (for drills and sluices) and {@link NetworkFluid#LAVA} (for forges). Blocks only
 * connect to blocks of the same fluid, so a lava pipe never joins a water pipe, intake, or port, and vice versa.
 * The name remains "Water" from earlier iterations, and {@link WaterNetworks} computes both kinds.
 *
 * <p>Two neighbouring network blocks connect when both report a connection on the shared face
 * ({@link #hasWaterConnection}), with pipes and intakes connecting on every face and machines only on their water port.
 * Machines never pass water on to each other, and {@link WaterNetworks} handles how power works.</p>
 */
public interface WaterNetworkBlock {
    /** The role of a block in the network. */
    enum Role {
        /** Carries fluid and connects on all six faces. */
        PIPE,
        /** Generates power while sitting in fluid. */
        INTAKE,
        /** An end point that is either powered or unpowered and never passes fluid through. */
        MACHINE,
        /**
         * Belts that carry fluid on to other conduits, pipes, and intakes like a pipe while also being powered like a machine.
         * Conduits never connect to machines, so a belt next to a drill or sluice does not power it.
         */
        CONDUIT
    }

    Role waterRole();

    /** The two kinds of fluid power, where networks of different fluids never connect. */
    enum NetworkFluid { WATER, LAVA }

    /** The fluid this block carries or uses, defaulting to water unless overridden. */
    default NetworkFluid networkFluid() {
        return NetworkFluid.WATER;
    }

    /** True if fluid can enter or leave this block through {@code side}. */
    boolean hasWaterConnection(BlockState state, Direction side);

    /** True while an intake is active in fluid. */
    default boolean isActiveIntake(BlockState state) {
        return false;
    }

    /**
     * Called on server side when the network is recomputed and this machine's powered state changes, rather than every tick.
     */
    default void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {}

    /**
     * Extra non-face neighbours this block exchanges fluid with, such as inclined belts linking diagonally to the belt at their low end
     * and to the next staircase incline. This must be symmetric so both blocks report each other.
     */
    default void addExtraLinks(BlockGetter level, BlockPos pos, BlockState state, java.util.List<BlockPos> out) {}

    /**
     * True if the block at {@code pos} connects to its neighbour in direction {@code dir} where both sides agree, share the same fluid,
     * and it is not machine to machine. This is used for the network and pipe visuals.
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
