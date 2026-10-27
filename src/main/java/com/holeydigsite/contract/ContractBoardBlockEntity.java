package com.holeydigsite.contract;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: contracts belong to the player. It exists so the board can draw its cards. */
public class ContractBoardBlockEntity extends BlockEntity {
    public ContractBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTRACT_BOARD.get(), pos, state);
    }
}
