package com.holeydigsite.drill;

import net.minecraft.util.StringRepresentable;

/** The Mining Drill's visible state (block state {@code status}); each has its own model/indicator light. */
public enum DrillStatus implements StringRepresentable {
    /** Drilling: animated bit, green light, dirt particles and a quiet drilling sound. Ticks. */
    WORKING("working"),
    /** Output blocked for more than 3 s: bit stops, red light. Waits for neighbour updates + a slow re-check. */
    STOPPED("stopped"),
    /** Not on Dirtrock or no water: dark/blue light. Waits for network/neighbour updates. */
    NO_POWER("no_power");

    private final String name;

    DrillStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
