package com.holeydigsite.belt;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * Corners: a flat belt fed only from one side (nothing feeding its back) shows a curved corner piece and items
 * follow the curve. LEFT = fed from the left (seen looking along the belt's facing), RIGHT = from the right.
 */
public enum BeltShape implements StringRepresentable {
    STRAIGHT("straight"), LEFT("left"), RIGHT("right");

    private final String name;

    BeltShape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The side items come in from on a corner (null for straight). */
    public Direction inputSide(Direction facing) {
        return switch (this) {
            case LEFT -> facing.getCounterClockWise();
            case RIGHT -> facing.getClockWise();
            default -> null;
        };
    }
}
