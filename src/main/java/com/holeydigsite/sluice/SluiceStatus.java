package com.holeydigsite.sluice;

import net.minecraft.util.StringRepresentable;

/** The Sluice Box's visible state (block state {@code status}); each has its own model and indicator lamp. */
public enum SluiceStatus implements StringRepresentable {
    /** Powered, has clumps, tray has room: flowing water, dirt layer, green lamp, splashes + water sound. Ticks. */
    WORKING("working"),
    /** Powered but no clumps queued: water still flows, no dirt, yellow lamp. Doesn't tick. */
    IDLE("idle"),
    /** Tray full: water stands still, red lamp. Doesn't tick; collecting the tray restarts it. */
    STOPPED("stopped"),
    /** No water power: dry ramp, dark lamp. Doesn't tick. */
    NO_POWER("no_power");

    private final String name;

    SluiceStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
