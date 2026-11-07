package com.holeydigsite.hub;

import com.holeydigsite.team.IslandClaimRules.Chunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Saved record of a server hub created via {@code /holeydigsite hub create confirm}.
 */
public final class HubData extends SavedData {
    private static final String NAME = "holeydigsite_hub";

    private boolean hasHub;
    private BlockPos hubAnchor = BlockPos.ZERO;
    private BlockPos hubLanding = BlockPos.ZERO;
    private final List<Chunk> chunks = new ArrayList<>();

    public static HubData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HubData::new, HubData::load, null), NAME);
    }

    public boolean hasHub() {
        return hasHub;
    }

    public BlockPos hubAnchor() {
        return hubAnchor;
    }

    public BlockPos hubLanding() {
        return hubLanding;
    }

    public List<Chunk> chunks() {
        return List.copyOf(chunks);
    }

    public void create(BlockPos anchor, BlockPos landing, List<Chunk> hubChunks) {
        this.hasHub = true;
        this.hubAnchor = anchor;
        this.hubLanding = landing;
        this.chunks.clear();
        this.chunks.addAll(hubChunks);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("HasHub", hasHub);
        if (hasHub) {
            tag.putLong("Anchor", hubAnchor.asLong());
            tag.putLong("Landing", hubLanding.asLong());
            int[] xz = new int[chunks.size() * 2];
            for (int i = 0; i < chunks.size(); i++) {
                xz[i * 2] = chunks.get(i).x();
                xz[i * 2 + 1] = chunks.get(i).z();
            }
            tag.putIntArray("Chunks", xz);
        }
        return tag;
    }

    public static HubData load(CompoundTag tag, HolderLookup.Provider registries) {
        HubData d = new HubData();
        d.hasHub = tag.getBoolean("HasHub");
        if (d.hasHub) {
            d.hubAnchor = BlockPos.of(tag.getLong("Anchor"));
            d.hubLanding = BlockPos.of(tag.getLong("Landing"));
            int[] xz = tag.getIntArray("Chunks");
            for (int i = 0; i + 1 < xz.length; i += 2) {
                d.chunks.add(new Chunk(xz[i], xz[i + 1]));
            }
        }
        return d;
    }
}
