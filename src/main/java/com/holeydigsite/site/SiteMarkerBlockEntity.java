package com.holeydigsite.site;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The site a {@link SiteMarkerBlock} builds ({@code Site:"holeydigsite:home"}). */
public class SiteMarkerBlockEntity extends BlockEntity {
    @Nullable
    private ResourceLocation site;

    public SiteMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SITE_MARKER.get(), pos, state);
    }

    @Nullable
    public ResourceLocation site() {
        return site;
    }

    public void setSite(@Nullable ResourceLocation site) {
        this.site = site;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // the template may load the marker without placing it (world generation): build one tick later
        if (level instanceof ServerLevel sl) {
            Sites.queueMarker(sl, getBlockPos());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (site != null) {
            tag.putString("Site", site.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        site = tag.contains("Site") ? ResourceLocation.tryParse(tag.getString("Site")) : null;
    }
}
