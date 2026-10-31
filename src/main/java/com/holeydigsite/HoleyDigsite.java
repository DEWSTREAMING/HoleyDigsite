package com.holeydigsite;

import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModCreativeTabs;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Holey Digsite - a Hydroneer-style dirt panning mod.
 *
 * <p>Gameplay loop:
 * dig dirt with a wooden shovel (or drill Dirt Clumps from Dirtrock) -> fill a gold pan -> pan it in water with a brush
 * (or belt the clumps into a Sluice Box) -> Folkiron, Goldchro and Rubies -> sell them on the Sell Pad for coins, or melt the
 * metal in a Crucible on a lava-lit Forge and cast weighted ingots in a Mold (worth more per gram).
 * Deeper rock (Deep / Abyssal Dirtrock) gives better clumps.</p>
 *
 * <p>This class only wires the registries to the mod event bus. Game logic lives in:
 * <ul>
 *   <li>{@code block/}    - the Pan block and its clumps-only item handler</li>
 *   <li>{@code power/}    - fluid power: water and lava intakes, pipes and the on/off networks</li>
 *   <li>{@code drill/}    - the Tier 1 Mining Drill and where it may output Dirt Clumps</li>
 *   <li>{@code belt/}     - the Clump Belt conveyor (block, contents/movement block entity)</li>
 *   <li>{@code sluice/}   - the Tier 1 Sluice Box (water-powered auto-panning into a tray)</li>
 *   <li>{@code forge/}    - melting and casting: Forge (lava powered), Crucible, adjustable Mold, ingot helpers</li>
 *   <li>{@code crafting/} - the "at least X g" recipe ingredient for weighted ingots</li>
 *   <li>{@code tier/}     - dirt tiers: rock -> clump tier -> pan tier -> loot table</li>
 *   <li>{@code economy/}  - data-driven resource weights (item data map) and sell values (datapack registry), coins</li>
 *   <li>{@code sellpad/}  - the Sell Pad (right-click / sneak / drop items to sell)</li>
 *   <li>{@code item/}     - the Pan block item, Dirt Clump, Brush and coins</li>
 *   <li>{@code shovel/}   - helpers for the vanilla wooden shovel's "full" state</li>
 *   <li>{@code panning/}  - hold-to-pan logic for the brush, loot rolling and feedback</li>
 *   <li>{@code event/}    - game-bus event handlers (common to client and server)</li>
 *   <li>{@code client/}   - client-only setup (item model properties, tooltips, the belt's clump renderer)</li>
 *   <li>{@code registry/} - DeferredRegisters for blocks, items, block entities, data components, creative tab</li>
 * </ul>
 * Event handler classes register themselves via {@code @EventBusSubscriber}.</p>
 */
@Mod(HoleyDigsite.MODID)
public class HoleyDigsite {
    public static final String MODID = "holeydigsite";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HoleyDigsite(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.holeydigsite.team.TeamConfig.SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.holeydigsite.crate.CrateConfig.SPEC, "holeydigsite-crates.toml");
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        com.holeydigsite.effect.ModEffects.EFFECTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.holeydigsite.registry.ModIngredients.INGREDIENT_TYPES.register(modEventBus);
        com.holeydigsite.ledger.ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        com.holeydigsite.ledger.ModTriggers.TRIGGERS.register(modEventBus);
        if (com.holeydigsite.smith.CuriosLoaded.is()) {
            com.holeydigsite.curios.CuriosCompat.init();
        }
        com.holeydigsite.compat.teams.TeamsCompat.init();
    }

    /** Shorthand for a {@code holeydigsite:<path>} resource location. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
