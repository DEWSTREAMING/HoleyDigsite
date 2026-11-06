package com.holeydigsite.power;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.FarmlandWaterManager;
import net.neoforged.neoforge.common.ticket.AABBTicket;

/**
 * Block entity for the Sprinkler. Manages the FarmlandWaterManager ticket for keeping nearby farmland wet.
 * Has no ticker method (zero idle ticking).
 */
public class SprinklerBlockEntity extends BlockEntity {
    public static final int RANGE = 4;

    private boolean waterPowered;
    private AABBTicket ticket;

    public SprinklerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPRINKLER.get(), pos, state);
        this.waterPowered = state.hasProperty(SprinklerBlock.WATER_POWERED) && state.getValue(SprinklerBlock.WATER_POWERED);
    }

    public boolean isWaterPowered() {
        return getBlockState().hasProperty(SprinklerBlock.WATER_POWERED)
                ? getBlockState().getValue(SprinklerBlock.WATER_POWERED)
                : waterPowered;
    }

    public void setWaterPowered(boolean powered) {
        this.waterPowered = powered;
        if (level instanceof ServerLevel sl) {
            if (powered) {
                refreshTicket(sl);
                SprinklerTracker.setSprinklerPowered(sl, worldPosition, true);
            } else {
                invalidateTicket();
                SprinklerTracker.setSprinklerPowered(sl, worldPosition, false);
            }
        }
    }

    private void refreshTicket(ServerLevel sl) {
        if (ticket == null || !ticket.isValid()) {
            AABB area = new AABB(
                    worldPosition.getX() - RANGE, worldPosition.getY() - 1, worldPosition.getZ() - RANGE,
                    worldPosition.getX() + RANGE + 1, worldPosition.getY() + 2, worldPosition.getZ() + RANGE + 1
            );
            ticket = FarmlandWaterManager.addAABBTicket(sl, area);
        }
    }

    private void invalidateTicket() {
        if (ticket != null) {
            ticket.invalidate();
            ticket = null;
        }
    }

    public void cleanup() {
        invalidateTicket();
        if (level instanceof ServerLevel sl) {
            SprinklerTracker.removeSprinkler(sl, worldPosition);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl && isWaterPowered()) {
            refreshTicket(sl);
            SprinklerTracker.setSprinklerPowered(sl, worldPosition, true);
        }
    }

    @Override
    public void setRemoved() {
        cleanup();
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.waterPowered = tag.getBoolean("WaterPowered");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("WaterPowered", waterPowered);
    }
}
