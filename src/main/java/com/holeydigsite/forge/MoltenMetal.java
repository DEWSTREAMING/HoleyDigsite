package com.holeydigsite.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * Which colour the molten metal / ingot in a Crucible or Mold shows (block state {@code metal}). Materials themselves
 * are data-driven ({@code resource_weights}); a material added later without its own colour shows as {@link #OTHER}.
 */
public enum MoltenMetal implements StringRepresentable {
    NONE("none"),
    FOLKIRON("folkiron"),
    GOLDCHRO("goldchro"),
    ORCHREMIA("orchremia"),
    SILVANE("silvane"),
    OTHER("other");

    private final String name;

    MoltenMetal(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static MoltenMetal of(@Nullable ResourceLocation material) {
        if (material == null) {
            return NONE;
        }
        return switch (material.getPath()) {
            case "folkiron" -> FOLKIRON;
            case "goldchro" -> GOLDCHRO;
            case "orchremia" -> ORCHREMIA;
            case "silvane" -> SILVANE;
            default -> OTHER;
        };
    }
}
