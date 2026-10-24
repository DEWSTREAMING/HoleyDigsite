package com.holeydigsite.power;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.PipeBlock;

/**
 * Lava Pipe (Phase 9): the lava twin of the {@link WaterPipeBlock} - same shape, same connection rule and the same
 * {@link WaterPipeBlock#FILLED} "network is live" state (the model shows an orange glow window then), but it carries
 * {@link NetworkFluid#LAVA}: it only connects to lava pipes, Lava Intakes and lava ports (the Forge), never to anything
 * on a water network. No block entity, never ticks.
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
