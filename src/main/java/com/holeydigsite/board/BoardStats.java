package com.holeydigsite.board;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.ledger.LedgerManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The numbers behind the boards, one per player per stat, saved with the world ({@code data/holeydigsite_boards.dat} in the
 * overworld) so offline players stay on them: clumps dug by each player's drills per clump kind, lifetime coins earned, and per
 * event the event clumps dug and the Opal Chips earned. Numbers only go up; an ended event's numbers are frozen.
 */
public class BoardStats extends SavedData {
    public static final String NAME = "holeydigsite_boards";

    public record Row(UUID id, String name, long clumps, long chips) {}

    private static final class EventBoard {
        final Map<UUID, Long> clumps = new HashMap<>();
        final Map<UUID, Long> chips = new HashMap<>();
        boolean frozen;
    }

    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Map<Integer, Long>> clumps = new HashMap<>();
    private final Map<UUID, Long> coins = new HashMap<>();
    private final Map<String, EventBoard> events = new LinkedHashMap<>();

    public static BoardStats get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(BoardStats::new, BoardStats::load, null), NAME);
    }

    // ------------------------------------------------------------------------------------------------ recording

    public void name(UUID id, String name) {
        if (name != null && !name.isEmpty() && !name.equals(names.get(id))) {
            names.put(id, name);
            setDirty();
        }
    }

    public String nameOf(UUID id) {
        return names.getOrDefault(id, id.toString().substring(0, 8));
    }

    /**
     * A drill owned by {@code owner} made a clump of {@code kind}; {@code event} is the event whose mark it got (null for none).
     * The owner may be offline.
     */
    public void addClump(MinecraftServer server, UUID owner, int kind, @Nullable String event) {
        clumps.computeIfAbsent(owner, k -> new HashMap<>()).merge(kind, 1L, Long::sum);
        boolean firstEvent = false;
        int rank = 0;
        if (event != null) {
            EventBoard b = board(event);
            if (!b.frozen) {
                firstEvent = b.clumps.merge(owner, 1L, Long::sum) == 1L;
                rank = rank(event, owner);
            }
        }
        setDirty();
        ServerPlayer sp = server == null ? null : server.getPlayerList().getPlayer(owner);
        if (sp != null) {
            name(owner, sp.getGameProfile().getName());
            if (firstEvent) {
                LedgerManager.grantMilestone(sp, HoleyDigsite.id("first_event_clump"));
            }
            if (rank > 0 && rank <= 10) {
                LedgerManager.grantMilestone(sp, HoleyDigsite.id("event_top_ten"));
            }
        }
    }

    public void addCoins(ServerPlayer sp, long copper) {
        if (copper <= 0) {
            return;
        }
        name(sp.getUUID(), sp.getGameProfile().getName());
        coins.merge(sp.getUUID(), copper, Long::sum);
        setDirty();
    }

    /** Login: a world from before the boards starts each player at their Ledger's lifetime earnings. */
    public void seedCoins(UUID id, long lifetime) {
        if (lifetime > coins.getOrDefault(id, 0L)) {
            coins.put(id, lifetime);
            setDirty();
        }
    }

    public void addChips(ServerPlayer sp, String event, long chips) {
        EventBoard b = board(event);
        if (b.frozen || chips <= 0) {
            return;
        }
        name(sp.getUUID(), sp.getGameProfile().getName());
        b.chips.merge(sp.getUUID(), chips, Long::sum);
        setDirty();
    }

    /** The event ended: its board keeps its numbers and never changes again (unless the event comes back). */
    public void freeze(String event) {
        board(event).frozen = true;
        setDirty();
    }

    /** The event is active (again): its board counts again. */
    public void unfreeze(String event) {
        EventBoard b = events.get(event);
        if (b != null && b.frozen) {
            b.frozen = false;
            setDirty();
        }
    }

    private EventBoard board(String event) {
        EventBoard b = events.get(event);
        if (b == null) {
            b = new EventBoard();
            events.put(event, b);
            setDirty();
        }
        return b;
    }

    // ------------------------------------------------------------------------------------------------ reading

    public boolean frozen(String event) {
        EventBoard b = events.get(event);
        return b != null && b.frozen;
    }

    public List<String> events() {
        return List.copyOf(events.keySet());
    }

    public long eventClumps(String event, UUID id) {
        EventBoard b = events.get(event);
        return b == null ? 0 : b.clumps.getOrDefault(id, 0L);
    }

    public long eventChips(String event, UUID id) {
        EventBoard b = events.get(event);
        return b == null ? 0 : b.chips.getOrDefault(id, 0L);
    }

    public long clumps(UUID id, int kind) {
        return clumps.getOrDefault(id, Map.of()).getOrDefault(kind, 0L);
    }

    public long coins(UUID id) {
        return coins.getOrDefault(id, 0L);
    }

    /** Kinds anyone has dug, in kind order. */
    public List<Integer> kinds() {
        TreeSet<Integer> out = new TreeSet<>();
        clumps.values().forEach(m -> out.addAll(m.keySet()));
        return List.copyOf(out);
    }

    /** The event's top {@code n}, by clumps (ties by chips) or by chips (ties by clumps). */
    public List<Row> eventTop(String event, boolean byChips, int n) {
        EventBoard b = events.get(event);
        if (b == null) {
            return List.of();
        }
        java.util.Set<UUID> ids = new java.util.HashSet<>(b.clumps.keySet());
        ids.addAll(b.chips.keySet());
        List<Row> rows = new ArrayList<>();
        for (UUID id : ids) {
            rows.add(new Row(id, nameOf(id), b.clumps.getOrDefault(id, 0L), b.chips.getOrDefault(id, 0L)));
        }
        Comparator<Row> c = byChips
                ? Comparator.comparingLong(Row::chips).thenComparingLong(Row::clumps)
                : Comparator.comparingLong(Row::clumps).thenComparingLong(Row::chips);
        rows.sort(c.reversed().thenComparing(Row::name));
        return rows.subList(0, Math.min(n, rows.size()));
    }

    /** 1-based place by clumps in the event, or 0 if not on it. */
    public int rank(String event, UUID id) {
        List<Row> all = eventTop(event, false, Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Top {@code n} for one clump kind ({@code kind} > 0) or for lifetime coins ({@code kind} = 0); the number is in clumps. */
    public List<Row> worldTop(int kind, int n) {
        List<Row> rows = new ArrayList<>();
        if (kind == 0) {
            coins.forEach((id, v) -> rows.add(new Row(id, nameOf(id), v, 0)));
        } else {
            clumps.forEach((id, m) -> {
                long v = m.getOrDefault(kind, 0L);
                if (v > 0) {
                    rows.add(new Row(id, nameOf(id), v, 0));
                }
            });
        }
        rows.sort(Comparator.comparingLong(Row::clumps).reversed().thenComparing(Row::name));
        return rows.subList(0, Math.min(n, rows.size()));
    }

    // ------------------------------------------------------------------------------------------------ save / load

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        java.util.Set<UUID> ids = new java.util.HashSet<>(names.keySet());
        ids.addAll(clumps.keySet());
        ids.addAll(coins.keySet());
        for (UUID id : ids) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            if (names.containsKey(id)) {
                t.putString("Name", names.get(id));
            }
            t.putLong("Coins", coins.getOrDefault(id, 0L));
            CompoundTag k = new CompoundTag();
            clumps.getOrDefault(id, Map.of()).forEach((kind, v) -> k.putLong(String.valueOf(kind), v));
            t.put("Clumps", k);
            players.add(t);
        }
        tag.put("Players", players);
        ListTag evs = new ListTag();
        for (Map.Entry<String, EventBoard> e : events.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Event", e.getKey());
            t.putBoolean("Frozen", e.getValue().frozen);
            t.put("Clumps", counts(e.getValue().clumps));
            t.put("Chips", counts(e.getValue().chips));
            evs.add(t);
        }
        tag.put("Events", evs);
        return tag;
    }

    private static ListTag counts(Map<UUID, Long> m) {
        ListTag list = new ListTag();
        m.forEach((id, v) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putLong("N", v);
            list.add(t);
        });
        return list;
    }

    private static void readCounts(ListTag list, Map<UUID, Long> into) {
        for (Tag t0 : list) {
            CompoundTag t = (CompoundTag) t0;
            into.put(t.getUUID("Id"), t.getLong("N"));
        }
    }

    public static BoardStats load(CompoundTag tag, HolderLookup.Provider registries) {
        BoardStats d = new BoardStats();
        for (Tag t0 : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            UUID id = t.getUUID("Id");
            if (t.contains("Name")) {
                d.names.put(id, t.getString("Name"));
            }
            if (t.getLong("Coins") > 0) {
                d.coins.put(id, t.getLong("Coins"));
            }
            CompoundTag k = t.getCompound("Clumps");
            Map<Integer, Long> m = new HashMap<>();
            for (String key : k.getAllKeys()) {
                try {
                    m.put(Integer.parseInt(key), k.getLong(key));
                } catch (NumberFormatException ignored) {
                }
            }
            if (!m.isEmpty()) {
                d.clumps.put(id, m);
            }
        }
        for (Tag t0 : tag.getList("Events", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            EventBoard b = new EventBoard();
            b.frozen = t.getBoolean("Frozen");
            readCounts(t.getList("Clumps", Tag.TAG_COMPOUND), b.clumps);
            readCounts(t.getList("Chips", Tag.TAG_COMPOUND), b.chips);
            d.events.put(t.getString("Event"), b);
        }
        return d;
    }
}
