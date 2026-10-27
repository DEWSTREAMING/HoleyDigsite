package com.holeydigsite.drill;

import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.skin.SkinModelProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * State of a Mining Drill: cached water power (pushed in by the network), drilling progress and how long the
 * output has been blocked. No inventory - a finished clump waits "in the bit" until the output has room.
 */
public class MiningDrillBlockEntity extends BlockEntity {
    /** Cached result of the water network. Only changed by {@link com.holeydigsite.power.WaterNetworks}. */
    private boolean waterPowered;
    /** Ticks drilled towards the next clump, 0..CYCLE_TICKS. At CYCLE_TICKS a clump is ready and waits. */
    private int progress;
    /** Game time the output was first seen blocked, or -1. Not saved: a reload simply restarts the 3 s grace. */
    private long blockedSince = -1;
    /** Applied skin id, e.g. "rustic", "obsidian", or empty for default. */
    private String skin = "";
    /** Wear of the applied skin, empty for Fresh. */
    private String wear = "";

    public MiningDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINING_DRILL.get(), pos, state);
    }

    /**
     * Server tick - only runs while the drill is WORKING (see {@link MiningDrillBlock#getTicker}).
     * Per tick: one increment. Every {@link MiningDrillBlock#CHECK_INTERVAL} ticks (and when a clump is ready):
     * one look at the block in front.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, MiningDrillBlockEntity drill) {
        long now = level.getGameTime();
        int cycle = MiningDrillBlock.cycleTicks(state);
        boolean justReady = false;
        if (drill.progress < cycle) {
            drill.progress++;
            justReady = drill.progress == cycle;
        }
        if (!justReady && now % MiningDrillBlock.CHECK_INTERVAL != 0) {
            return;
        }

        Direction facing = state.getValue(MiningDrillBlock.FACING);
        BlockPos front = pos.relative(facing);
        Direction from = facing.getOpposite();
        boolean ready = drill.progress >= cycle;
        int tier = MiningDrillBlock.clumpTier(level, pos);
        boolean ok = ready ? ClumpOutputs.tryOutput(level, front, from, tier) : ClumpOutputs.canOutput(level, front, from, tier);
        level.blockEntityChanged(pos); // mark the chunk for saving (cheaper than setChanged's comparator update)

        if (ok) {
            drill.blockedSince = -1;
            if (ready) {
                drill.progress = 0;
            }
        } else if (drill.blockedSince < 0) {
            drill.blockedSince = now;
        } else if (now - drill.blockedSince > MiningDrillBlock.STOP_AFTER_BLOCKED_TICKS
                && state.getBlock() instanceof MiningDrillBlock block) {
            block.setStatus(level, pos, state, drill, DrillStatus.STOPPED);
        }
    }

    public boolean isWaterPowered() {
        return waterPowered;
    }

    void setWaterPowered(boolean powered) {
        if (waterPowered != powered) {
            waterPowered = powered;
            setChanged();
        }
    }

    public int getProgress() {
        return progress;
    }

    /** True while the output is blocked but the 3 s grace period hasn't run out yet. */
    public boolean isOutputBlocked() {
        return blockedSince >= 0;
    }

    void clearBlocked() {
        blockedSince = -1;
    }

    public String getSkin() {
        return skin;
    }

    public String getWear() {
        return wear;
    }

    /** Folder of the skin textures for this skin and wear, see {@link com.holeydigsite.skin.SkinWear#textureKey}. */
    public String getSkinKey() {
        return com.holeydigsite.skin.SkinWear.textureKey(skin, wear);
    }

    public void setSkin(String skin) {
        setSkin(skin, "");
    }

    public void setSkin(String skin, String wear) {
        this.skin = skin == null ? "" : skin;
        this.wear = wear == null || com.holeydigsite.skin.SkinWear.isFresh(wear) ? "" : wear;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            requestModelDataUpdate();
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(SkinModelProperties.SKIN, getSkinKey())
                .build();
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        String s = input.get(ModDataComponents.SKIN.get());
        if (s != null) {
            this.skin = s;
            String w = input.get(ModDataComponents.SKIN_WEAR.get());
            this.wear = w == null ? "" : w;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (!skin.isEmpty() && !skin.equals("default")) {
            builder.set(ModDataComponents.SKIN.get(), skin);
            if (!wear.isEmpty()) {
                builder.set(ModDataComponents.SKIN_WEAR.get(), wear);
            }
        }
    }

    // ---- save/load ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("WaterPowered", waterPowered);
        tag.putInt("Progress", progress);
        if (!skin.isEmpty()) {
            tag.putString("Skin", skin);
            if (!wear.isEmpty()) {
                tag.putString("SkinWear", wear);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        waterPowered = tag.getBoolean("WaterPowered");
        progress = Math.clamp(tag.getInt("Progress"), 0, MiningDrillBlock.cycleTicks(getBlockState()));
        skin = tag.getString("Skin");
        wear = tag.getString("SkinWear");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (!skin.isEmpty()) {
            tag.putString("Skin", skin);
            if (!wear.isEmpty()) {
                tag.putString("SkinWear", wear);
            }
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
