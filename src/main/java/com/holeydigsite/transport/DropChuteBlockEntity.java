package com.holeydigsite.transport;

import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.skin.SkinModelProperties;
import com.holeydigsite.skin.SkinWear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Carries the applied skin and wear for a placed Drop Chute.
 */
public class DropChuteBlockEntity extends BlockEntity {
    private String skin = "";
    private String wear = "";

    public DropChuteBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DROP_CHUTE.get(), pos, state);
    }

    public String getSkin() {
        return skin;
    }

    public String getWear() {
        return wear;
    }

    public String getSkinKey() {
        return SkinWear.textureKey(skin, wear);
    }

    public void setSkin(String skin) {
        setSkin(skin, "");
    }

    public void setSkin(String skin, String wear) {
        this.skin = skin == null ? "" : skin;
        this.wear = wear == null || SkinWear.isFresh(wear) ? "" : wear;
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
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (!skin.isEmpty() && !skin.equals("default")) {
            builder.set(ModDataComponents.SKIN.get(), skin);
            if (!wear.isEmpty()) {
                builder.set(ModDataComponents.SKIN_WEAR.get(), wear);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
        skin = tag.getString("Skin");
        wear = tag.getString("SkinWear");
        requestModelDataUpdate();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!skin.isEmpty()) {
            tag.putString("Skin", skin);
            if (!wear.isEmpty()) {
                tag.putString("SkinWear", wear);
            }
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
