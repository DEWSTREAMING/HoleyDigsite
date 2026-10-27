package com.holeydigsite.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** The lines of a crucible's floating label: what it holds, then "Auto-pour" while that is on. */
public final class CrucibleLabel {
    private CrucibleLabel() {}

    public static List<Component> lines(CrucibleBlockEntity c) {
        List<Component> out = new ArrayList<>();
        if (c.total() > 0) {
            out.add(c.unmelted() > 0
                    ? Component.translatable("text.holeydigsite.crucible.melting", Metals.name(c.metal()), c.molten(), c.unmelted())
                    : Component.translatable("text.holeydigsite.crucible", Metals.name(c.metal()), c.molten()));
        }
        BlockState state = c.getBlockState();
        if (state.getBlock() instanceof CrucibleBlock block && block.autoPour(state)) {
            out.add(Component.translatable("text.holeydigsite.crucible.auto"));
        }
        return out;
    }
}
