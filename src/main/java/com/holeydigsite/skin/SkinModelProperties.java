package com.holeydigsite.skin;

import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Model property for passing skin id to baked block models.
 */
public final class SkinModelProperties {
    private SkinModelProperties() {}

    public static final ModelProperty<String> SKIN = new ModelProperty<>();
}
