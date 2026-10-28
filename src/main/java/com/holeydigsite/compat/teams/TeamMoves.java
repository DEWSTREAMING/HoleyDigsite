package com.holeydigsite.compat.teams;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Players whose island changed while they were offline; they are sent there on their next login. */
final class TeamMoves extends SavedData {
    private static final String NAME = "holeydigsite_team_moves";
    private final Set<UUID> pending = new LinkedHashSet<>();

    static TeamMoves get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TeamMoves::new, TeamMoves::load, null), NAME);
    }

    void add(UUID player) {
        if (pending.add(player)) {
            setDirty();
        }
    }

    /** True (and forgotten) if the player had a move waiting. */
    boolean take(UUID player) {
        if (pending.remove(player)) {
            setDirty();
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (UUID id : pending) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("Pending", list);
        return tag;
    }

    static TeamMoves load(CompoundTag tag, HolderLookup.Provider registries) {
        TeamMoves m = new TeamMoves();
        for (Tag t : tag.getList("Pending", Tag.TAG_INT_ARRAY)) {
            m.pending.add(NbtUtils.loadUUID(t));
        }
        return m;
    }
}
