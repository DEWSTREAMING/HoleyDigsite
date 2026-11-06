package com.holeydigsite.machine;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Rope Machine: water-powered conveyor machine twisting fibre into rope.
 */
public class RopeMachineBlock extends WaterProcessingBlock {
    public static final MapCodec<RopeMachineBlock> CODEC = simpleCodec(RopeMachineBlock::new);
    public static final ResourceLocation ID = HoleyDigsite.id("rope_machine");

    private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.block();

    public RopeMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public ResourceLocation machineId() {
        return ID;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RopeMachineBlockEntity(pos, state);
    }
}
