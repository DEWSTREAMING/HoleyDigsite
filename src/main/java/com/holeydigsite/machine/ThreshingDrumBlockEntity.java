package com.holeydigsite.machine;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class ThreshingDrumBlockEntity extends WaterProcessingBlockEntity {
    public ThreshingDrumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THRESHING_DRUM.get(), pos, state);
    }

    @Override
    public ResourceLocation machineId() {
        return ThreshingDrumBlock.ID;
    }
}
