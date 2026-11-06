package com.holeydigsite.team;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The islands that have been claimed, and the chunks this mod claimed for each (so it never touches any other claim). */
public final class IslandClaimData extends SavedData {
    private static final String NAME = "holeydigsite_island_claims";
    private final Map<UUID, List<IslandClaimRules.Chunk>> islands = new HashMap<>();

    public static IslandClaimData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(IslandClaimData::new, IslandClaimData::load, null), NAME);
    }

    public boolean isDone(UUID island) {
        return islands.containsKey(island);
    }

    /** Chunks this mod claimed for the island. */
    public List<IslandClaimRules.Chunk> claimed(UUID island) {
        return islands.getOrDefault(island, List.of());
    }

    public void finish(UUID island, List<IslandClaimRules.Chunk> claimed) {
        islands.put(island, List.copyOf(claimed));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        islands.forEach((island, chunks) -> {
            CompoundTag e = new CompoundTag();
            e.put("Island", NbtUtils.createUUID(island));
            int[] xz = new int[chunks.size() * 2];
            for (int i = 0; i < chunks.size(); i++) {
                xz[i * 2] = chunks.get(i).x();
                xz[i * 2 + 1] = chunks.get(i).z();
            }
            e.putIntArray("Chunks", xz);
            list.add(e);
        });
        tag.put("Islands", list);
        return tag;
    }

    public static IslandClaimData load(CompoundTag tag, HolderLookup.Provider registries) {
        IslandClaimData d = new IslandClaimData();
        for (Tag t : tag.getList("Islands", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            int[] xz = e.getIntArray("Chunks");
            List<IslandClaimRules.Chunk> chunks = new ArrayList<>();
            for (int i = 0; i + 1 < xz.length; i += 2) {
                chunks.add(new IslandClaimRules.Chunk(xz[i], xz[i + 1]));
            }
            d.islands.put(NbtUtils.loadUUID(e.get("Island")), chunks);
        }
        return d;
    }
}
