package com.holeydigsite.machine;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class SawmillBlockEntity extends WaterProcessingBlockEntity {
    public SawmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAWMILL.get(), pos, state);
    }

    @Override
    public ResourceLocation machineId() {
        return SawmillBlock.ID;
    }

    @Override
    public String mode() {
        BlockState state = getBlockState();
        if (state.hasProperty(SawmillBlock.MODE)) {
            return state.getValue(SawmillBlock.MODE).getSerializedName();
        }
        return "beam";
    }
}
