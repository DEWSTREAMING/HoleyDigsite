package com.holeydigsite.smith;

import net.minecraft.core.Direction;

/**
 * Where the laid items lie on the Smithing Anvil's work surface (pixels of the NORTH-facing model, 16 = one block; the long axis is x,
 * the player stands at small z). Up to 3 ingots side by side in one row (centred when fewer) and the gem in a row in front of
 * them, so all four are visible and never overlap. The renderer draws each item as a flat square of {@link #INGOT_SIZE} /
 * {@link #GEM_SIZE} pixels at these spots.
 */
public final class AnvilLayout {
    private AnvilLayout() {}

    /** Square footprint (pixels) of a laid ingot / gem. */
    public static final float INGOT_SIZE = 4.5F;
    public static final float GEM_SIZE = 3.5F;
    /** Distance between neighbouring ingot centres. */
    public static final float INGOT_PITCH = 4.9F;
    public static final float CENTER_X = 7.2F;
    public static final float INGOT_Z = 9.8F;
    public static final float GEM_Z = 5.5F;

    /** Centre {x, z} of ingot {@code index} when {@code count} ingots are laid (north-facing model). */
    public static float[] ingot(int index, int count) {
        return new float[]{CENTER_X + (index - (count - 1) / 2.0F) * INGOT_PITCH, INGOT_Z};
    }

    /** Centre {x, z} of the gem (north-facing model). */
    public static float[] gem() {
        return new float[]{CENTER_X, GEM_Z};
    }

    /** The same spot for a block facing {@code facing} (rotated about the block centre like the blockstate variant), in pixels. */
    public static float[] rotated(float[] spot, Direction facing) {
        float dx = spot[0] - 8.0F;
        float dz = spot[1] - 8.0F;
        return switch (facing) {
            case EAST -> new float[]{8.0F - dz, 8.0F + dx};
            case SOUTH -> new float[]{8.0F - dx, 8.0F - dz};
            case WEST -> new float[]{8.0F + dz, 8.0F - dx};
            default -> spot.clone();
        };
    }
}
