package com.holeydigsite.crate;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Crate rewards that are rolled but not given yet because the spin is still running. Saved with the world, so a reward
 * survives a logout, a crash or a chunk unload: it is given when the spin ends, on a click to skip, when the player leaves,
 * when the crate's chunk unloads, or at the latest on the next login.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public class PendingCrates extends SavedData {
    public static final String NAME = "holeydigsite_pending_crates";

    /** One open crate. {@code granted}: the reward is given and the entry only waits for the crate block to go. */
    public static final class Held {
        public final UUID player;
        public final String dimension;
        public final BlockPos pos;
        public final CrateRolls.Roll roll;
        public boolean granted;
        public boolean spinOver;

        Held(UUID player, String dimension, BlockPos pos, CrateRolls.Roll roll) {
            this.player = player;
            this.dimension = dimension;
            this.pos = pos;
            this.roll = roll;
        }
    }

    private final List<Held> held = new ArrayList<>();

    public static PendingCrates get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PendingCrates::new, PendingCrates::load, null), NAME);
    }

    public List<Held> all() {
        return held;
    }

    public Held add(UUID player, ServerLevel level, BlockPos pos, CrateRolls.Roll roll) {
        Held h = new Held(player, level.dimension().location().toString(), pos.immutable(), roll);
        held.add(h);
        setDirty();
        return h;
    }

    @Nullable
    public Held at(ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        for (Held h : held) {
            if (h.pos.equals(pos) && h.dimension.equals(dim)) return h;
        }
        return null;
    }

    public void remove(Held h) {
        if (held.remove(h)) setDirty();
    }

    @Nullable
    private static ServerPlayer find(MinecraftServer server, UUID id) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        if (sp != null) return sp;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer found) return found;
        }
        return null;
    }

    /** Gives the reward if it is not given yet. False if the player is not online (it stays held). */
    public static boolean grant(MinecraftServer server, Held h) {
        if (h.granted) return true;
        ServerPlayer sp = find(server, h.player);
        if (sp == null) return false;
        h.granted = true;
        CrateRolls.Outcome out = CrateRolls.grant(sp, h.roll);
        get(server).setDirty();
        CrateNetwork.sendResult(sp, new CrateNetwork.ResultPayload(h.pos, out.line()));
        return true;
    }

    /** Everything still held for this player, given now (the player is online). */
    public static void grantAll(ServerPlayer sp, boolean tellInChat) {
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        PendingCrates data = get(server);
        for (Held h : new ArrayList<>(data.held)) {
            if (!h.player.equals(sp.getUUID()) || h.granted) continue;
            h.granted = true;
            CrateRolls.Outcome out = CrateRolls.grant(sp, h.roll);
            data.setDirty();
            if (tellInChat) sp.sendSystemMessage(out.line());
            else CrateNetwork.sendResult(sp, new CrateNetwork.ResultPayload(h.pos, out.line()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) grantAll(sp, false);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) grantAll(sp, true);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getServer() == null) return;
        PendingCrates data = get(level.getServer());
        if (data.held.isEmpty()) return;
        String dim = level.dimension().location().toString();
        for (Held h : new ArrayList<>(data.held)) {
            if (!h.granted && h.dimension.equals(dim) && event.getChunk().getPos().equals(new net.minecraft.world.level.ChunkPos(h.pos))) {
                grant(level.getServer(), h);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Held h : held) {
            CompoundTag t = new CompoundTag();
            t.putUUID("player", h.player);
            t.putString("dim", h.dimension);
            t.putLong("pos", h.pos.asLong());
            t.putBoolean("granted", h.granted);
            t.putBoolean("over", h.spinOver);
            CrateRolls.Roll r = h.roll;
            t.putString("tier", r.tier());
            t.putString("rolled", r.rolledRarity());
            t.putString("rarity", r.rarity());
            t.putString("kind", r.entry().kind().name());
            t.putString("id", r.entry().id());
            t.putInt("amount", r.entry().amount());
            t.putInt("weight", r.entry().weight());
            if (r.wear() != null) t.putString("wear", r.wear());
            if (r.family() != null) t.putString("family", r.family());
            list.add(t);
        }
        tag.put("held", list);
        return tag;
    }

    public static PendingCrates load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingCrates data = new PendingCrates();
        for (Tag raw : tag.getList("held", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            CrateDefs.Kind kind;
            try {
                kind = CrateDefs.Kind.valueOf(t.getString("kind"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            CrateRolls.Roll roll = new CrateRolls.Roll(t.getString("tier"), t.getString("rolled"), t.getString("rarity"),
                    new CrateDefs.Entry(kind, t.getString("id"), t.getInt("amount"), t.getInt("weight")),
                    t.contains("wear") ? t.getString("wear") : null,
                    t.contains("family") ? t.getString("family") : null);
            Held h = new Held(t.getUUID("player"), t.getString("dim"), BlockPos.of(t.getLong("pos")), roll);
            h.granted = t.getBoolean("granted");
            h.spinOver = t.getBoolean("over");
            data.held.add(h);
        }
        return data;
    }
}
