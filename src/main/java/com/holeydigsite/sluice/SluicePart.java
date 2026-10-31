package com.holeydigsite.sluice;

import net.minecraft.util.StringRepresentable;

/** The three blocks of a long sluice: the back end with the block entity and the water ports, the tray end, and the chute on the back end. */
public enum SluicePart implements StringRepresentable {
    BACK("back"),
    FRONT("front"),
    CHUTE("chute");

    private final String key;

    SluicePart(String key) {
        this.key = key;
    }

    @Override
    public String getSerializedName() {
        return key;
    }
}
