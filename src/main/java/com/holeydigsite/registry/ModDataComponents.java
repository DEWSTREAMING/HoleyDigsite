package com.holeydigsite.registry;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.tier.DirtTier;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom item-stack data components.
 *
 * <p>Components are saved with the stack and synced to clients so client-side model properties can read them.</p>
 */
public final class ModDataComponents {
    private ModDataComponents() {}

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HoleyDigsite.MODID);

    /**
     * {@code holeydigsite:shovel_full}, set to true on a vanilla wooden shovel carrying a scoop of dirt, see {@link com.holeydigsite.shovel.ShovelHelper}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SHOVEL_FULL =
            DATA_COMPONENTS.registerComponentType("shovel_full", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /**
     * {@code holeydigsite:clump_tier}, tier 2 or 3 of a Deep or Abyssal Dirt Clump, see {@link com.holeydigsite.tier.DirtTier}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CLUMP_TIER =
            DATA_COMPONENTS.registerComponentType("clump_tier", builder -> builder
                    .persistent(Codec.intRange(DirtTier.MIN, DirtTier.MAX))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * {@code holeydigsite:blueprint_target}, the item a Blueprint buys as an {@link Item}, see {@link com.holeydigsite.item.BlueprintItem}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Item>> BLUEPRINT_TARGET =
            DATA_COMPONENTS.registerComponentType("blueprint_target", builder -> builder
                    .persistent(net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec())
                    .networkSynchronized(ByteBufCodecs.registry(Registries.ITEM)));

    /** {@code holeydigsite:custom_blueprint}, a Blueprint for an operator-made display entry, see {@link com.holeydigsite.shop.custom.CustomBlueprint}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.holeydigsite.shop.custom.CustomBlueprint>> CUSTOM_BLUEPRINT =
            DATA_COMPONENTS.registerComponentType("custom_blueprint", builder -> builder
                    .persistent(com.holeydigsite.shop.custom.CustomBlueprint.CODEC)
                    .networkSynchronized(com.holeydigsite.shop.custom.CustomBlueprint.STREAM_CODEC));

    /**
     * {@code holeydigsite:shovel_kind}, see {@link com.holeydigsite.tier.DirtTier}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SHOVEL_KIND =
            DATA_COMPONENTS.registerComponentType("shovel_kind", builder -> builder
                    .persistent(Codec.intRange(DirtTier.MIN, DirtTier.MAX))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * {@code holeydigsite:smith}, the ingots and gem of a piece made on the Smithing Anvil, see {@link com.holeydigsite.smith.SmithData}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.holeydigsite.smith.SmithData>> SMITH =
            DATA_COMPONENTS.registerComponentType("smith", builder -> builder
                    .persistent(com.holeydigsite.smith.SmithData.CODEC)
                    .networkSynchronized(com.holeydigsite.smith.SmithData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WEIGHT =
            DATA_COMPONENTS.registerComponentType("weight", builder -> builder
                    .persistent(Codec.intRange(1, 1_000_000))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * {@code holeydigsite:skin}, the skin id applied to a machine item (e.g. "rustic", "obsidian"), see {@link com.holeydigsite.skin.SkinDefs}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SKIN =
            DATA_COMPONENTS.registerComponentType("skin", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** {@code holeydigsite:skin_family}, the machine family a Skin Kit fits ("mining_drill" or "sluice_box"). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SKIN_FAMILY =
            DATA_COMPONENTS.registerComponentType("skin_family", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** {@code holeydigsite:skin_wear}, the wear of the skin on a machine item; absent means Fresh from the Forge. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SKIN_WEAR =
            DATA_COMPONENTS.registerComponentType("skin_wear", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /**
     * {@code holeydigsite:pick_head}, the head fitted onto a Prospector's Pick as an {@link Item}, see {@link com.holeydigsite.item.ProspectorPickItem}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Item>> PICK_HEAD =
            DATA_COMPONENTS.registerComponentType("pick_head", builder -> builder
                    .persistent(net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec())
                    .networkSynchronized(ByteBufCodecs.registry(Registries.ITEM)));
}
