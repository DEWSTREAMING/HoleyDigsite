package com.holeydigsite.belt;

import net.minecraft.util.StringRepresentable;

/**
 * What a Clump Belt is doing right now (block state {@code mode}). It picks the model and whether the belt ticks.
 *
 * <ul>
 *   <li>{@link #EMPTY}: nothing on it - static model, no ticker (costs nothing).</li>
 *   <li>{@link #MOVING}: at least one clump moved last tick - animated belt texture, ticks.</li>
 *   <li>{@link #BLOCKED}: clumps on it but none could move (output blocked, queue backed up), or only a clump
 *       dropping into the pan - static model, still ticks so it notices when the output frees up.</li>
 * </ul>
 * A powered (redstone) belt never ticks, whatever its mode.
 */
public enum BeltMode implements StringRepresentable {
    EMPTY("empty"),
    MOVING("moving"),
    BLOCKED("blocked");

    private final String name;

    BeltMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
