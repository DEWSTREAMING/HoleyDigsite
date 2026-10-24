package com.holeydigsite.client;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shovel.ShovelHelper;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only setup on the MOD bus: item model properties used by model "overrides".
 *
 * <ul>
 *   <li>{@code holeydigsite:full} on minecraft:wooden_shovel - 1 when full, else 0.
 *       Used by our override of {@code assets/minecraft/models/item/wooden_shovel.json}.</li>
 *   <li>{@code holeydigsite:fill} on holeydigsite:pan - fill / MAX_FILL (0.0-1.0), so the pan item
 *       shows the same dirt level as the placed block.</li>
 *   <li>{@code holeydigsite:tier} on holeydigsite:pan and holeydigsite:dirt_clump - the dirt tier 1-3 (Phase 7), for the
 *       darker Deep / Abyssal models.</li>
 * </ul>
 * Also registers the block entity renderers: Clump Belt ({@link ClumpBeltRenderer}), Sluice Box tray
 * ({@link SluiceBoxRenderer}) and, since Phase 8, the shop: pads ({@link PadRenderer}), Money Pan ({@link MoneyPanRenderer})
 * and Shop Display ({@link ShopDisplayRenderer}); Phase 9: Crucible and Mold floating texts.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // ItemProperties is not thread-safe; run on the main thread.
        event.enqueueWork(() -> {
            ItemProperties.register(Items.WOODEN_SHOVEL, HoleyDigsite.id("full"),
                    (stack, level, entity, seed) -> ShovelHelper.isFull(stack) ? 1.0F : 0.0F);

            ItemProperties.register(ModItems.PAN.get(), HoleyDigsite.id("fill"),
                    (stack, level, entity, seed) -> PanBlock.getFill(stack) / (float) PanBlock.MAX_FILL);

            // Phase 7: dirt tier 1-3 (darker dirt models for Deep / Abyssal)
            ItemProperties.register(ModItems.PAN.get(), HoleyDigsite.id("tier"),
                    (stack, level, entity, seed) -> PanBlock.getTier(stack));
            // Phase 11: what a full shovel carries (1 dirt, 4 gravel, 5 sand) for the gravel/sand looks
            ItemProperties.register(Items.WOODEN_SHOVEL, HoleyDigsite.id("kind"),
                    (stack, level, entity, seed) -> ShovelHelper.isFull(stack) ? ShovelHelper.kind(stack) : 0.0F);
            // Phase 10: Large Pan (fill / 8 and tier, same overrides as the Pan)
            ItemProperties.register(ModItems.LARGE_PAN.get(), HoleyDigsite.id("fill"),
                    (stack, level, entity, seed) -> PanBlock.getFill(stack) / (float) PanBlock.LARGE_MAX_FILL);
            ItemProperties.register(ModItems.LARGE_PAN.get(), HoleyDigsite.id("tier"),
                    (stack, level, entity, seed) -> PanBlock.getTier(stack));
            ItemProperties.register(ModItems.DIRT_CLUMP.get(), HoleyDigsite.id("tier"),
                    (stack, level, entity, seed) -> DirtTier.ofClump(stack));
            // Phase 19: a ring with a gem shows the gem dot
            ItemProperties.register(ModItems.SMITH_RING.get(), HoleyDigsite.id("gem"),
                    (stack, level, entity, seed) -> {
                        var d = com.holeydigsite.smith.SmithPieces.data(stack);
                        return d != null && d.gem().isPresent() ? 1.0F : 0.0F;
                    });
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CLUMP_BELT.get(), ClumpBeltRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SLUICE_BOX.get(), SluiceBoxRenderer::new);
        // Phase 8: shop
        event.registerBlockEntityRenderer(ModBlockEntities.SHOP_PAD.get(), PadRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MONEY_PAN.get(), MoneyPanRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SHOP_DISPLAY.get(), ShopDisplayRenderer::new);
        // Phase 9: floating texts of the crucible and mold
        event.registerBlockEntityRenderer(ModBlockEntities.CRUCIBLE.get(), CrucibleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MOLD.get(), MoldRenderer::new);
        // Phase 10: Crafting Forge items on the basin + recipe text
        event.registerBlockEntityRenderer(ModBlockEntities.CRAFTING_FORGE.get(), CraftingForgeRenderer::new);
        // Phase 19: Smithing Anvil (ingots and gem on the work surface + recipe label)
        event.registerBlockEntityRenderer(ModBlockEntities.SMITHING_ANVIL.get(), SmithingAnvilRenderer::new);
        // Phase 11: land plot boards (name + price / Owned)
        event.registerBlockEntityRenderer(ModBlockEntities.PLOT_BOARD.get(), PlotBoardRenderer::new);
    }
}
