package com.holeydigsite.client;

import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinModelProperties;
import com.holeydigsite.skin.SkinWear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baked model wrapper that dynamically applies skin textures to drill and sluice quads.
 * Fallback to default texture is automatic when a skin does not override a specific texture.
 */
public class SkinnedBakedModel extends BakedModelWrapper<BakedModel> {
    private final @Nullable String fixedSkin;
    private final Map<String, SkinnedBakedModel> itemModelCache = new ConcurrentHashMap<>();
    private final Map<QuadCacheKey, List<BakedQuad>> quadCache = new ConcurrentHashMap<>();
    private final ItemOverrides itemOverrides;

    private record QuadCacheKey(
            @Nullable BlockState state,
            @Nullable Direction side,
            @Nullable RenderType renderType,
            String skin
    ) {}

    public SkinnedBakedModel(BakedModel original) {
        this(original, null);
    }

    public SkinnedBakedModel(BakedModel original, @Nullable String fixedSkin) {
        super(original);
        this.fixedSkin = fixedSkin;
        this.itemOverrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                String skin = SkinWear.textureKey(stack.get(ModDataComponents.SKIN.get()), SkinWear.of(stack));
                if (skin.isEmpty()) {
                    return originalModel;
                }
                return itemModelCache.computeIfAbsent(skin, s -> new SkinnedBakedModel(originalModel, s));
            }
        };
    }

    @Override
    public ItemOverrides getOverrides() {
        return itemOverrides;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        ModelData data = super.getModelData(level, pos, state, modelData);
        if (state.getBlock() instanceof MiningDrillBlock && !MiningDrillBlock.isLower(state)) {
            // Upper half of drill has no block entity: fetch skin from the lower half
            if (level.getBlockEntity(pos.below()) instanceof MiningDrillBlockEntity drill) {
                String skin = drill.getSkinKey();
                if (skin != null && !skin.isEmpty()) {
                    return data.derive().with(SkinModelProperties.SKIN, skin).build();
                }
            }
        }
        return data;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        String skin = fixedSkin != null ? fixedSkin : extraData.get(SkinModelProperties.SKIN);
        if (skin == null || skin.isEmpty() || skin.equals(SkinDefs.DEFAULT_ID)) {
            return originalModel.getQuads(state, side, rand, extraData, renderType);
        }

        QuadCacheKey key = new QuadCacheKey(state, side, renderType, skin);
        return quadCache.computeIfAbsent(key, k -> {
            List<BakedQuad> originalQuads = originalModel.getQuads(state, side, rand, extraData, renderType);
            if (originalQuads.isEmpty()) {
                return originalQuads;
            }
            List<BakedQuad> remapped = new ArrayList<>(originalQuads.size());
            for (BakedQuad quad : originalQuads) {
                remapped.add(remapQuad(quad, skin));
            }
            return List.copyOf(remapped);
        });
    }

    private static BakedQuad remapQuad(BakedQuad quad, String skin) {
        TextureAtlasSprite originalSprite = quad.getSprite();
        if (originalSprite == null) {
            return quad;
        }
        ResourceLocation originalName = originalSprite.contents().name();
        if (!"holeydigsite".equals(originalName.getNamespace())) {
            return quad;
        }
        String path = originalName.getPath();
        if (!path.startsWith("block/")) {
            return quad;
        }

        // e.g. "block/mining_drill_lower_front" -> "block/skin/rustic/pit_scarred/mining_drill_lower_front"
        String subPath = path.substring("block/".length());
        TextureAtlasSprite newSprite = skinSprite(skin, subPath);
        if (newSprite == null) {
            // No custom skin texture for this quad (e.g. lamp, dirt, water), fall back to original
            return quad;
        }

        // Remap UVs
        int[] srcVertices = quad.getVertices();
        int[] dstVertices = srcVertices.clone();

        float oldU0 = originalSprite.getU0();
        float oldU1 = originalSprite.getU1();
        float oldV0 = originalSprite.getV0();
        float oldV1 = originalSprite.getV1();
        float oldDeltaU = oldU1 - oldU0;
        float oldDeltaV = oldV1 - oldV0;

        float newU0 = newSprite.getU0();
        float newU1 = newSprite.getU1();
        float newV0 = newSprite.getV0();
        float newV1 = newSprite.getV1();
        float newDeltaU = newU1 - newU0;
        float newDeltaV = newV1 - newV0;

        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            float u = Float.intBitsToFloat(srcVertices[offset + 4]);
            float v = Float.intBitsToFloat(srcVertices[offset + 5]);

            float relU = (oldDeltaU != 0.0f) ? (u - oldU0) / oldDeltaU : 0.0f;
            float relV = (oldDeltaV != 0.0f) ? (v - oldV0) / oldDeltaV : 0.0f;

            float newU = newU0 + relU * newDeltaU;
            float newV = newV0 + relV * newDeltaV;

            dstVertices[offset + 4] = Float.floatToRawIntBits(newU);
            dstVertices[offset + 5] = Float.floatToRawIntBits(newV);
        }

        return new BakedQuad(dstVertices, quad.getTintIndex(), quad.getDirection(), newSprite, quad.isShade(), quad.hasAmbientOcclusion());
    }

    /** The skin texture for a key like "rustic" or "rustic/pit_scarred"; a worn set that lacks the texture falls back to Fresh. */
    @Nullable
    private static TextureAtlasSprite skinSprite(String key, String subPath) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        while (true) {
            TextureAtlasSprite sprite = atlas.getSprite(ResourceLocation.fromNamespaceAndPath("holeydigsite", "block/skin/" + key + "/" + subPath));
            if (sprite != null && !sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return sprite;
            }
            int slash = key.indexOf('/');
            if (slash < 0) {
                return null;
            }
            key = key.substring(0, slash);
        }
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        String skin = fixedSkin != null ? fixedSkin : data.get(SkinModelProperties.SKIN);
        if (skin == null || skin.isEmpty() || skin.equals(SkinDefs.DEFAULT_ID)) {
            return originalModel.getParticleIcon(data);
        }
        TextureAtlasSprite original = originalModel.getParticleIcon(data);
        if (original == null) {
            return original;
        }
        ResourceLocation originalName = original.contents().name();
        if ("holeydigsite".equals(originalName.getNamespace()) && originalName.getPath().startsWith("block/")) {
            TextureAtlasSprite newSprite = skinSprite(skin, originalName.getPath().substring("block/".length()));
            if (newSprite != null) {
                return newSprite;
            }
        }
        return original;
    }
}
