package com.holeydigsite.belt;

/**
 * Where an item rides across the width of a belt. The offset comes from the item's seed only, so it never changes while the
 * item moves and is the same on every client. Rendering only: nothing here touches capacity, spacing or routing.
 */
public final class BeltLanes {
    private BeltLanes() {}

    /** Lane centres across the belt, in blocks from the middle (the rails take about 2 px at each side). */
    public static final float[] LANES = {-0.14F, 0.0F, 0.14F};
    /** Extra loose spread added to the lane, in blocks (plus or minus). */
    public static final float JITTER = 0.025F;
    /** The free width of the belt from the middle to the inside of a rail, in blocks. */
    public static final float FREE_HALF_WIDTH = 0.375F;
    /** Lumps on a belt are drawn at this share of the usual belt item scale. */
    public static final float LUMP_SCALE = 0.85F;

    private static int mix(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }

    /** Sideways offset in blocks for an item with this seed (positive = to the right of the belt's direction). */
    public static float offset(int seed) {
        int h = mix(seed);
        float lane = LANES[Math.floorMod(h >>> 4, LANES.length)];
        float jitter = ((h >>> 12) & 0xFF) / 255.0F * 2.0F - 1.0F;
        return lane + jitter * JITTER;
    }

    /** Largest absolute offset {@link #offset} can give. */
    public static float maxOffset() {
        float m = 0;
        for (float l : LANES) m = Math.max(m, Math.abs(l));
        return m + JITTER;
    }
}
