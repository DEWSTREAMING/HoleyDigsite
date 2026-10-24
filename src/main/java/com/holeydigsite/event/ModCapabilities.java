package com.holeydigsite.event;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanItemHandler;
import com.holeydigsite.registry.ModBlocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Handles capability registration on the mod bus. Pans use an item handler accepting only Dirt Clumps without a block entity.
 * The Mining Drill deliberately registers no item handler, while crucibles and crafting forges use insert-only handlers.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class ModCapabilities {
    private ModCapabilities() {}

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> new PanItemHandler(level, pos),
                ModBlocks.PAN.get(), ModBlocks.LARGE_PAN.get());
        // Allows hoppers or other mods to insert meltables into a crucible.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, com.holeydigsite.registry.ModBlockEntities.CRUCIBLE.get(),
                (crucible, side) -> new com.holeydigsite.forge.CrucibleItemHandler(crucible));
        // Allows hoppers to load ingots and parts into a crafting forge.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, com.holeydigsite.registry.ModBlockEntities.CRAFTING_FORGE.get(),
                (forge, side) -> new com.holeydigsite.forge.CraftingForgeItemHandler(forge));
    }
}
