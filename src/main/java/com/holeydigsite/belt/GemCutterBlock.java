package com.holeydigsite.belt;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Phase 12 Gem Cutter: an inline belt machine (a belt segment under a rugged cutting-wheel housing). Water-powered and
 * part of the belt/water network like a belt; items come in from the BACK only and leave out of the front at belt speed.
 * An uncut gem stops under the wheel ({@link ClumpBeltBlockEntity#MID}) for {@link ClumpBeltBlockEntity#CUT_TICKS}
 * ticks (sparks, grinding) and comes out as the cut gem of the same size; every other item just passes through.
 * Players can put items on it and take them off like a belt. Ticks only while it holds something.
 */
public class GemCutterBlock extends ClumpBeltBlock {
    public static final MapCodec<GemCutterBlock> CODEC = simpleCodec(GemCutterBlock::new);

    public GemCutterBlock(Properties properties) {
        super(properties, 1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        addCommon(builder);
        builder.add(INPUT_SPEED);
    }

    /** Client: a few sparks at the wheel. */
    public static void sparks(Level level, BlockPos pos, BlockState state) {
        Direction f = state.getValue(FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.02, y = pos.getY() + 1.05, z = pos.getZ() + 0.5 + f.getStepZ() * 0.02;
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (level.random.nextDouble() - 0.5) * 0.3, y + level.random.nextDouble() * 0.15,
                    z + (level.random.nextDouble() - 0.5) * 0.3, f.getStepX() * 0.1, 0.08, f.getStepZ() * 0.1);
        }
        if (level.random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.CRIT, x, y + 0.1, z, f.getStepX() * 0.2, 0.15, f.getStepZ() * 0.2);
        }
    }
}
