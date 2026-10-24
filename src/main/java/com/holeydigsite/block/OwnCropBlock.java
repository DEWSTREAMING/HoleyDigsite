package com.holeydigsite.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Phase 18: Glowroot, Riverbean and Dune Melon plants - plain vanilla crops (4 growth stages {@code age 0-3}, random-tick growth on
 * farmland, bone meal, no block entity, no ticking of our own). The loot table (data) drops produce only when ripe, the seed always.
 */
public class OwnCropBlock extends CropBlock {
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape[] SHAPE_BY_AGE = {
            Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 0, 0, 16, 5, 16), Block.box(0, 0, 0, 16, 8, 16), Block.box(0, 0, 0, 16, 11, 16)};

    private final Supplier<Item> seed;

    public OwnCropBlock(Properties properties, Supplier<Item> seed) {
        super(properties);
        this.seed = seed;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        Item item = seed == null ? null : seed.get();
        return item == null ? Items.WHEAT_SEEDS : item;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_AGE[getAge(state)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public BlockState playerWillDestroy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide && isMaxAge(state) && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.holeydigsite.ledger.LedgerManager.recordHarvest(sp, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
