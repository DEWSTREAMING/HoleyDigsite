package com.holeydigsite.power;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.PipeBlock;

/**
 * The lava twin of the {@link WaterPipeBlock} (same shape, same connection rule, and the same
 * {@link WaterPipeBlock#FILLED} "network is live" state where the model shows an orange glow window). It carries
 * {@link NetworkFluid#LAVA} and only connects to lava pipes, Lava Intakes, and lava ports (never to anything
 * on a water network). It has no block entity and never ticks.
 */
public class LavaPipeBlock extends WaterPipeBlock {
    public static final MapCodec<LavaPipeBlock> CODEC = simpleCodec(LavaPipeBlock::new);

    public LavaPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    public NetworkFluid networkFluid() {
        return NetworkFluid.LAVA;
    }
}
