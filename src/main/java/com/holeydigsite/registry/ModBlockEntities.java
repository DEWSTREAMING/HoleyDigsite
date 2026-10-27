package com.holeydigsite.registry;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import net.minecraft.core.registries.Registries;
import com.holeydigsite.tier.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity type registrations. */
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HoleyDigsite.MODID);

    /** {@code holeydigsite:mining_drill} holding progress and cached water power of a Mining Drill. */
    @SuppressWarnings("DataFlowIssue") // vanilla passes null for the datafixer type
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MiningDrillBlockEntity>> MINING_DRILL =
            BLOCK_ENTITIES.register("mining_drill", () -> BlockEntityType.Builder
                    .of(MiningDrillBlockEntity::new, Tiers.blocksOf("mining_drill"))
                    .build(null));

    /** {@code holeydigsite:clump_belt} holding the clumps on a Clump Belt block and their positions. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClumpBeltBlockEntity>> CLUMP_BELT =
            BLOCK_ENTITIES.register("clump_belt", () -> BlockEntityType.Builder
                    .of(ClumpBeltBlockEntity::new, java.util.stream.Stream.of(Tiers.blocksOf("clump_belt"), Tiers.blocksOf("incline_belt"), Tiers.blocksOf("down_incline_belt"),
                            new Block[]{ModBlocks.FILTER_BELT.get(), ModBlocks.GEM_CUTTER.get(), ModBlocks.BELT_SPLITTER.get()})
                            .flatMap(java.util.Arrays::stream).toArray(Block[]::new))
                    .build(null));

    /** {@code holeydigsite:sluice_box} holding queue, progress, tray and cached water power of a Sluice Box. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SluiceBoxBlockEntity>> SLUICE_BOX =
            BLOCK_ENTITIES.register("sluice_box", () -> BlockEntityType.Builder
                    .of(SluiceBoxBlockEntity::new, Tiers.blocksOf("sluice_box"))
                    .build(null));

    /** {@code holeydigsite:shop_pad} holding the stacks on a Sell Pad or Buy Pad. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PadBlockEntity>> SHOP_PAD =
            BLOCK_ENTITIES.register("shop_pad", () -> BlockEntityType.Builder
                    .of(PadBlockEntity::new, ModBlocks.SELL_PAD.get(), ModBlocks.BUY_PAD.get())
                    .build(null));

    /** {@code holeydigsite:money_pan} holding the balance in copper. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoneyPanBlockEntity>> MONEY_PAN =
            BLOCK_ENTITIES.register("money_pan", () -> BlockEntityType.Builder
                    .of(MoneyPanBlockEntity::new, ModBlocks.MONEY_PAN.get())
                    .build(null));

    /** {@code holeydigsite:shop_display} holding the item a display shows. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopDisplayBlockEntity>> SHOP_DISPLAY =
            BLOCK_ENTITIES.register("shop_display", () -> BlockEntityType.Builder
                    .of(ShopDisplayBlockEntity::new, ModBlocks.SHOP_DISPLAY.get())
                    .build(null));

    /** {@code holeydigsite:crucible} holding metal, unmelted and molten grams. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.holeydigsite.forge.CrucibleBlockEntity>> CRUCIBLE =
            BLOCK_ENTITIES.register("crucible", () -> BlockEntityType.Builder
                    .of(com.holeydigsite.forge.CrucibleBlockEntity::new, Tiers.blocksOf("crucible"))
                    .build(null));

    /** {@code holeydigsite:mold} holding target weight, metal and grams of a Mold. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.holeydigsite.forge.MoldBlockEntity>> MOLD =
            BLOCK_ENTITIES.register("mold", () -> BlockEntityType.Builder
                    .of(com.holeydigsite.forge.MoldBlockEntity::new, Tiers.blocksOf("mold"))
                    .build(null));

    /** {@code holeydigsite:plot_board} holding which plot a board stands for and its bounds. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.holeydigsite.plot.PlotBoardBlockEntity>> PLOT_BOARD =
            BLOCK_ENTITIES.register("plot_board", () -> BlockEntityType.Builder
                    .of(com.holeydigsite.plot.PlotBoardBlockEntity::new, ModBlocks.PLOT_BOARD.get())
                    .build(null));

    /** {@code holeydigsite:crafting_forge} holding pooled grams, parts, selected recipe, cast progress and output. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.holeydigsite.forge.CraftingForgeBlockEntity>> CRAFTING_FORGE =
            BLOCK_ENTITIES.register("crafting_forge", () -> BlockEntityType.Builder
                    .of(com.holeydigsite.forge.CraftingForgeBlockEntity::new, ModBlocks.CRAFTING_FORGE.get())
                    .build(null));

    /** {@code holeydigsite:smithing_anvil} holding the ingots and gem laid on it, the selected recipe and the hits. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.holeydigsite.smith.SmithingAnvilBlockEntity>> SMITHING_ANVIL =
            BLOCK_ENTITIES.register("smithing_anvil", () -> BlockEntityType.Builder
                    .of(com.holeydigsite.smith.SmithingAnvilBlockEntity::new, ModBlocks.SMITHING_ANVIL.get())
                    .build(null));

    /** {@code holeydigsite:skin_bench} holding an inserted machine item. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.holeydigsite.skin.SkinBenchBlockEntity>> SKIN_BENCH =
            BLOCK_ENTITIES.register("skin_bench", () -> BlockEntityType.Builder
                    .of(com.holeydigsite.skin.SkinBenchBlockEntity::new, ModBlocks.SKIN_BENCH.get())
                    .build(null));

    /** {@code holeydigsite:contract_board}: no data, only a renderer. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.holeydigsite.contract.ContractBoardBlockEntity>> CONTRACT_BOARD =
            BLOCK_ENTITIES.register("contract_board", () -> BlockEntityType.Builder
                    .of(com.holeydigsite.contract.ContractBoardBlockEntity::new, ModBlocks.CONTRACT_BOARD.get())
                    .build(null));

    /** {@code holeydigsite:site_marker} holding the site id a template marker builds. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.holeydigsite.site.SiteMarkerBlockEntity>> SITE_MARKER =
            BLOCK_ENTITIES.register("site_marker", () -> BlockEntityType.Builder
                    .of(com.holeydigsite.site.SiteMarkerBlockEntity::new, ModBlocks.SITE_MARKER.get())
                    .build(null));
}
