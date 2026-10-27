package com.holeydigsite.crate;

/**
 * The numbers of the crate spin, shared by the server (when the reward is given) and the client (what is drawn).
 * Change them here to tweak the feel.
 */
public final class CrateSpin {
    private CrateSpin() {}

    /** Length of the spin in ticks (6 s). */
    public static final int SPIN_TICKS = 120;
    /** How long the result stays on screen after the landing. */
    public static final int HOLD_TICKS = 60;
    /** Server gives the reward this long after the spin starts (a little after the client lands). */
    public static final int GRANT_TICKS = SPIN_TICKS + 4;
    /** The crate block is removed this long after it was opened. */
    public static final int OPEN_TICKS = SPIN_TICKS + HOLD_TICKS;
    /**
     * Ease-out power: 3 is the old cubic. Higher means a faster start and a longer crawl at the end. At 3.5 the last slot takes
     * about a third of the spin.
     */
    public static final double CURVE_POWER = 3.5;
    /** The reward sits at this strip index. */
    public static final int RESULT_INDEX = 32;
    /** Strip length; the extra slots after the result fill the sides of the panel. */
    public static final int STRIP_SIZE = RESULT_INDEX + 12;
    /** Chance that a result below the top rarity gets a rarer item placed right next to it. */
    public static final double NEAR_MISS_CHANCE = 0.4;
    /** Where the marker stops inside the result slot (0 = left edge, 1 = right edge), normally. */
    public static final float STOP_MIN = 0.12F;
    public static final float STOP_MAX = 0.88F;
    /** Near misses stop closer to the edge next to the rare item. */
    public static final float EDGE_MIN = 0.03F;
    public static final float EDGE_MAX = 0.16F;

    /** Strip position (in slots, the marker's coordinate) after {@code t} ticks, from {@code start} to {@code end}. */
    public static double position(double t, double start, double end) {
        double x = Math.max(0, Math.min(1, t / SPIN_TICKS));
        double eased = 1 - Math.pow(1 - x, CURVE_POWER);
        return start + (end - start) * eased;
    }

    /** The marker's start position: the middle of the first slot. */
    public static final double START = 0.5;
}
