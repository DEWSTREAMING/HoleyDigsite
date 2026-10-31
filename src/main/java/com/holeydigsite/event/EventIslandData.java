package com.holeydigsite.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The event islands of one dimension ({@code data/holeydigsite_event_islands.dat}): for each copy of the home island, where
 * its event island is (the template origin it was placed from) and which event its sections were built for.
 */
public class EventIslandData extends SavedData {
    public static final String NAME = "holeydigsite_event_islands";

    public record Island(BlockPos origin, String event) {}

    private final Map<Long, Island> islands = new LinkedHashMap<>();

    public static EventIslandData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(EventIslandData::new, EventIslandData::load, null), NAME);
    }

    @Nullable
    public Island get(BlockPos origin) {
        return islands.get(origin.asLong());
    }

    public void put(Island island) {
        islands.put(island.origin().asLong(), island);
        setDirty();
    }

    public void remove(BlockPos origin) {
        if (islands.remove(origin.asLong()) != null) {
            setDirty();
        }
    }

    public Collection<Island> all() {
        return Collections.unmodifiableCollection(islands.values());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Island i : islands.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Origin", i.origin().asLong());
            t.putString("Event", i.event());
            list.add(t);
        }
        tag.put("Islands", list);
        return tag;
    }

    public static EventIslandData load(CompoundTag tag, HolderLookup.Provider registries) {
        EventIslandData d = new EventIslandData();
        for (Tag t0 : tag.getList("Islands", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            Island i = new Island(BlockPos.of(t.getLong("Origin")), t.getString("Event"));
            d.islands.put(i.origin().asLong(), i);
        }
        return d;
    }
}
