package com.holeydigsite.plot;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Data for a plot board, written by the island template with the plot ID and relative bounds.
 * Registers the plot in {@link PlotData} on load and after placement.
 */
public class PlotBoardBlockEntity extends BlockEntity {
    private String plotId = "";
    private int[] min = {0, 0, 0};
    private int[] max = {0, 0, 0};

    public PlotBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLOT_BOARD.get(), pos, state);
    }

    public String plotId() {
        return plotId;
    }

    /** World-space bounds of the plot. */
    public BoundingBox box() {
        BlockPos p = getBlockPos();
        return new BoundingBox(p.getX() + min[0], p.getY() + min[1], p.getZ() + min[2],
                p.getX() + max[0], p.getY() + max[1], p.getZ() + max[2]);
    }

    /** Sets the plot. */
    public void setPlot(String id, BlockPos relMin, BlockPos relMax) {
        plotId = id;
        min = new int[]{Math.min(relMin.getX(), relMax.getX()), Math.min(relMin.getY(), relMax.getY()), Math.min(relMin.getZ(), relMax.getZ())};
        max = new int[]{Math.max(relMin.getX(), relMax.getX()), Math.max(relMin.getY(), relMax.getY()), Math.max(relMin.getZ(), relMax.getZ())};
        setChanged();
        register();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) {
            sl.scheduleTick(getBlockPos(), getBlockState().getBlock(), 1);
        }
    }

    /** Puts the plot into {@link PlotData} and syncs the board's owned state. */
    public void register() {
        if (!(level instanceof ServerLevel sl) || plotId.isEmpty()) {
            return;
        }
        PlotData.Plot p = PlotData.get(sl).register(plotId, getBlockPos(), box());
        BlockState state = getBlockState();
        if (state.getBlock() instanceof PlotBoardBlock && state.getValue(PlotBoardBlock.OWNED) != p.owned()) {
            sl.setBlock(getBlockPos(), state.setValue(PlotBoardBlock.OWNED, p.owned()), Block.UPDATE_CLIENTS);
        }
        sl.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_CLIENTS);
    }

    private void write(CompoundTag tag) {
        tag.putString("Plot", plotId);
        tag.putIntArray("Min", min);
        tag.putIntArray("Max", max);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        plotId = tag.getString("Plot");
        int[] a = tag.getIntArray("Min"), b = tag.getIntArray("Max");
        if (a.length == 3) min = a;
        if (b.length == 3) max = b;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
