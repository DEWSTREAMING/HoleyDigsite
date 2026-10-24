package com.holeydigsite.event;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanItemHandler;
import com.holeydigsite.registry.ModBlocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capability registration (mod bus).
 *
 * <p>Pan: item handler on every side that accepts only Dirt Clumps ({@link PanItemHandler}); no block entity needed.
 * The Mining Drill deliberately registers NO item handler - nothing can pull from it. Crucible (Phase 9): insert-only,
 * meltables only ({@link com.holeydigsite.forge.CrucibleItemHandler}).</p>
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class ModCapabilities {
    private ModCapabilities() {}

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> new PanItemHandler(level, pos),
                ModBlocks.PAN.get(), ModBlocks.LARGE_PAN.get());
        // Phase 9: hoppers / other mods may insert meltables into a Crucible (insert-only)
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, com.holeydigsite.registry.ModBlockEntities.CRUCIBLE.get(),
                (crucible, side) -> new com.holeydigsite.forge.CrucibleItemHandler(crucible));
        // Phase 10: hoppers may load ingots and parts into a Crafting Forge (insert-only)
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, com.holeydigsite.registry.ModBlockEntities.CRAFTING_FORGE.get(),
                (forge, side) -> new com.holeydigsite.forge.CraftingForgeItemHandler(forge));
    }
}
