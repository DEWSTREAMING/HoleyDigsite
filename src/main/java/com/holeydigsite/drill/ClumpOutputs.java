package com.holeydigsite.drill;

import com.holeydigsite.event.EventDefs;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.tier.DirtTier;
import com.holeydigsite.transport.ItemOutputs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The ONE place that decides where a drill may put a Dirt Clump.
 *
 * <p>Any {@link com.holeydigsite.transport.ItemTarget} (clump-only ones via {@link DirtClumpTarget}): the pan, the Clump Belt and the Sluice Box (
 * from its back only). Belts use this too for anything that isn't a pan or another belt. A later "chest output" upgrade would add a branch here (e.g. wrap
 * an item-handler capability) gated on the drill having that upgrade (nothing else in the drill needs to change).</p>
 *
 * <p>Clumps are never dropped into the world and never handed to hoppers/chests/other mods.</p>
 *
 * <p>Drills on the active event's rock put the {@code holeydigsite:event_dug} mark on its clumps.</p>
 */
public final class ClumpOutputs {
    private ClumpOutputs() {}

    /** Creates one clump for a drill, setting the event_dug mark if this is an active event rock. */
    public static ItemStack drillClump(int tier) {
        ItemStack stack = DirtTier.clumps(tier, 1);
        if (DirtTier.isEventKind(tier)) {
            EventDefs.EventDef active = EventDefs.getActiveEvent();
            if (active != null) {
                net.minecraft.resources.ResourceLocation clumpId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                for (EventDefs.EventTier et : active.tiers()) {
                    if (et.clump().equals(clumpId)) {
                        stack.set(ModDataComponents.EVENT_DUG.get(), active.id());
                        break;
                    }
                }
            }
        }
        return stack;
    }

    /** The event whose mark a drill clump of {@code tier} gets now, or null. */
    @org.jetbrains.annotations.Nullable
    public static String markedEvent(int tier) {
        return drillClump(tier).get(ModDataComponents.EVENT_DUG.get());
    }

    /** True if a clump of {@code tier} can go into the block at {@code target} right now ({@code from}: side facing the drill). */
    public static boolean canOutput(Level level, BlockPos target, Direction from, int tier) {
        return ItemOutputs.canOutput(level, target, from, drillClump(tier));
    }

    /** Puts one clump into the target if it can take it. Returns true if it did. */
    public static boolean tryOutput(Level level, BlockPos target, Direction from, int tier) {
        boolean ok = ItemOutputs.tryOutput(level, target, from, drillClump(tier));
        if (ok && level.getBlockEntity(target) instanceof com.holeydigsite.belt.ClumpBeltBlockEntity belt) {
            belt.markNewestFromDrill();
        }
        return ok;
    }
}
