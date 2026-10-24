package com.holeydigsite.client;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.smith.AnvilRecipes;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithMaterial;
import com.holeydigsite.smith.SmithPieces;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.Optional;

/**
 * Tints of the smithed pieces (Phase 19.1): every model layer is tinted by the ingot (or gem) its recipe names for that part, so a
 * mixed piece shows each metal where it was used; plain parts (grip, handle) are not tinted. Colours come from the synced {@code smithing_materials} registry; a piece without materials is plain.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID, value = Dist.CLIENT)
public final class SmithColors {
    private SmithColors() {}

    private static final int PLAIN = 0xFFC8C8C8;
    private static final int PLAIN_LAYER = 0xFFFFFFFF;

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> color(stack, tint), ModItems.SMITH_RING.get(), ModItems.SMITH_NECKLACE.get(),
                ModItems.SMITH_DAGGER.get(), ModItems.SMITH_AXE.get(), ModItems.SMITH_SWORD.get());
    }

    private static HolderLookup.Provider cachedFor;
    private static final java.util.Map<net.minecraft.world.item.Item, java.util.Optional<AnvilRecipe>> RECIPES = new java.util.HashMap<>();

    /** The recipe of a piece item, cached per registry set (colours are asked for every layer of every frame). */
    private static AnvilRecipe recipe(HolderLookup.Provider registries, net.minecraft.world.item.Item item) {
        if (registries != cachedFor) {
            RECIPES.clear();
            cachedFor = registries;
        }
        return RECIPES.computeIfAbsent(item, i -> java.util.Optional.ofNullable(AnvilRecipes.forItem(registries, i))).orElse(null);
    }

    /** ARGB tint of a piece's layer (0 = metal, 1 = gem). */
    static int color(ItemStack stack, int tint) {
        SmithData data = SmithPieces.data(stack);
        if (data == null) {
            return PLAIN;
        }
        var conn = Minecraft.getInstance().getConnection();
        HolderLookup.Provider registries = conn == null ? null : conn.registryAccess();
        AnvilRecipe recipe = recipe(registries, stack.getItem());
        Optional<ResourceLocation> material = SmithPieces.partMaterial(recipe, data, tint);
        if (material.isEmpty()) {
            return PLAIN_LAYER;          // wooden handle / leather grip: drawn in its own colours
        }
        SmithMaterial m = SmithMaterial.get(registries, material.get());
        return m == null ? PLAIN : 0xFF000000 | m.color();
    }
}
