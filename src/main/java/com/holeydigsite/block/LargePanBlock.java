package com.holeydigsite.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Large Pan (Tier 2): holds {@value PanBlock#LARGE_MAX_FILL} scoops instead of 4. Everything else is the Pan:
 * waterlogging, shovel / clumps / hoppers / drills / belts (bounce and void when full), one dirt tier at a time, hand
 * panning, instant pick-up that keeps fill and tier. Made in the Crafting Forge only.
 */
public class LargePanBlock extends PanBlock {
    public static final MapCodec<LargePanBlock> CODEC = simpleCodec(LargePanBlock::new);

    /** Wider and deeper than the Pan: 16x16, 6 pixels tall. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    public LargePanBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Called from the constructor: only a static constant. */
    @Override
    public IntegerProperty fillProperty() {
        return LARGE_FILL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public int maxFill() {
        return LARGE_MAX_FILL;
    }
}
