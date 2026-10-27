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
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import java.util.Map;

/**
 * Registers item model properties used by overrides on the client.
 *
 * <ul>
 *   <li>{@code holeydigsite:full} on minecraft:wooden_shovel (1 when full, else 0,
 *       used by the wooden shovel model override).</li>
 *   <li>{@code holeydigsite:fill} on holeydigsite:pan (fill / MAX_FILL, so the item
 *       shows the same dirt level as the placed block).</li>
 *   <li>{@code holeydigsite:tier} on holeydigsite:pan and holeydigsite:dirt_clump (dirt tier 1-3, for
 *       darker deep or abyssal models).</li>
 * </ul>
 * Also registers block entity renderers for the clump belt, sluice box, shop pads, money pan,
 * shop display, crucible, mold, crafting forge, smithing anvil, and plot boards.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // ItemProperties is not thread-safe, so run on the main thread.
        event.enqueueWork(() -> {
            ItemProperties.register(Items.WOODEN_SHOVEL, HoleyDigsite.id("full"),
                    (stack, level, entity, seed) -> ShovelHelper.isFull(stack) ? 1.0F : 0.0F);

            ItemProperties.register(ModItems.PAN.get(), HoleyDigsite.id("fill"),
                    (stack, level, entity, seed) -> PanBlock.getFill(stack) / (float) PanBlock.MAX_FILL);

            // Dirt tier 1-3 for darker dirt models.
            ItemProperties.register(ModItems.PAN.get(), HoleyDigsite.id("tier"),
                    (stack, level, entity, seed) -> PanBlock.getTier(stack));
            // What a full shovel carries for the gravel and sand looks.
            ItemProperties.register(Items.WOODEN_SHOVEL, HoleyDigsite.id("kind"),
                    (stack, level, entity, seed) -> ShovelHelper.isFull(stack) ? ShovelHelper.kind(stack) : 0.0F);
            // Large pan fill and tier overrides.
            ItemProperties.register(ModItems.LARGE_PAN.get(), HoleyDigsite.id("fill"),
                    (stack, level, entity, seed) -> PanBlock.getFill(stack) / (float) PanBlock.LARGE_MAX_FILL);
            ItemProperties.register(ModItems.LARGE_PAN.get(), HoleyDigsite.id("tier"),
                    (stack, level, entity, seed) -> PanBlock.getTier(stack));
            ItemProperties.register(ModItems.DIRT_CLUMP.get(), HoleyDigsite.id("tier"),
                    (stack, level, entity, seed) -> DirtTier.ofClump(stack));
            // Ingot size by weight (model overrides).
            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (item instanceof com.holeydigsite.item.IngotItem) {
                    ItemProperties.register(item, HoleyDigsite.id("size"),
                            (stack, level, entity, seed) -> com.holeydigsite.item.IngotItem.sizeStep(com.holeydigsite.item.IngotItem.grams(stack)));
                }
            }
            // Rings with a gem show the gem dot.
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
        event.registerBlockEntityRenderer(ModBlockEntities.SHOP_PAD.get(), PadRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MONEY_PAN.get(), MoneyPanRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SHOP_DISPLAY.get(), ShopDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRUCIBLE.get(), CrucibleRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MOLD.get(), MoldRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRAFTING_FORGE.get(), CraftingForgeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SMITHING_ANVIL.get(), SmithingAnvilRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLOT_BOARD.get(), PlotBoardRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SKIN_BENCH.get(), SkinBenchRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CONTRACT_BOARD.get(), ContractBoardRenderer::new);
    }

    @SubscribeEvent
    public static void registerItemDecorations(net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent event) {
        event.register(ModItems.SKIN_KIT.get(), SkinKitModel.DECORATOR);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : models.entrySet()) {
            ModelResourceLocation key = entry.getKey();
            if (HoleyDigsite.MODID.equals(key.id().getNamespace())) {
                String path = key.id().getPath();
                if (isSkinnableModel(path)) {
                    entry.setValue(new SkinnedBakedModel(entry.getValue()));
                } else if (path.equals("skin_kit") || path.equals("item/skin_kit")) {
                    entry.setValue(new SkinKitModel(entry.getValue()));
                }
            }
        }
    }

    private static boolean isSkinnableModel(String path) {
        if (path.startsWith("sluice_upgrade") || path.startsWith("block/sluice_upgrade") || path.startsWith("item/sluice_upgrade")) {
            return false;
        }
        return path.startsWith("mining_drill") || path.startsWith("block/mining_drill") || path.startsWith("item/mining_drill")
                || path.startsWith("sluice_box") || path.startsWith("block/sluice_box") || path.startsWith("item/sluice_box");
    }
}
