package com.holeydigsite.machine;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class RopeMachineBlockEntity extends WaterProcessingBlockEntity {
    public RopeMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROPE_MACHINE.get(), pos, state);
    }

    @Override
    public ResourceLocation machineId() {
        return RopeMachineBlock.ID;
    }
}
