package com.holeydigsite.economy;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * The data-driven economy tables registered on the mod bus:
 * <ul>
 *   <li>{@link #RESOURCE_WEIGHTS} - item data map containing material and grams per item
 *       ({@code data/holeydigsite/data_maps/item/resource_weights.json}).</li>
 *   <li>{@link #SELL_VALUES} - datapack registry containing copper per gram per material
 *       ({@code data/holeydigsite/holeydigsite/sell_values/*.json}).</li>
 * </ul>
 * Both are synced to clients. New materials only require JSON entries.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class ModEconomy {
    private ModEconomy() {}

    public static final DataMapType<Item, ResourceInfo> RESOURCE_WEIGHTS = DataMapType
            .builder(HoleyDigsite.id("resource_weights"), Registries.ITEM, ResourceInfo.CODEC)
            .synced(ResourceInfo.CODEC, false)
            .build();

    public static final ResourceKey<Registry<SellValue>> SELL_VALUES =
            ResourceKey.createRegistryKey(HoleyDigsite.id("sell_values"));

    @SubscribeEvent
    public static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(RESOURCE_WEIGHTS);
    }

    @SubscribeEvent
    public static void registerDatapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SELL_VALUES, SellValue.CODEC, SellValue.CODEC);
        // Shop catalogue for blueprints, shop displays, and buy pads
        // Crafting forge recipes, synced so the forge's floating text can display them
        event.dataPackRegistry(com.holeydigsite.forge.ForgeRecipes.KEY, com.holeydigsite.forge.ForgeRecipe.CODEC, com.holeydigsite.forge.ForgeRecipe.CODEC);
        // Smithing anvil recipes and the stats or colours of the material pieces
        event.dataPackRegistry(com.holeydigsite.smith.AnvilRecipes.KEY, com.holeydigsite.smith.AnvilRecipe.CODEC, com.holeydigsite.smith.AnvilRecipe.CODEC);
        event.dataPackRegistry(com.holeydigsite.smith.SmithMaterial.KEY, com.holeydigsite.smith.SmithMaterial.CODEC, com.holeydigsite.smith.SmithMaterial.CODEC);
        event.dataPackRegistry(com.holeydigsite.shop.ShopCatalogue.SHOP_ITEMS, com.holeydigsite.shop.ShopItem.CODEC, com.holeydigsite.shop.ShopItem.CODEC);
    }
}
