package com.holeydigsite.plot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Land plots of one dimension (Phase 11), saved with the world ({@code data/holeydigsite_plots.dat}).
 *
 * <p>A plot is registered by its {@link PlotBoardBlockEntity} (plot id + bounds stored in the board's NBT by the island
 * template) the first time the board loads; from then on the bounds are known even while the board's chunk isn't loaded.
 * Key = {@code id@boardPos}, so every island copy (one per Skyblock Builder team) has its own plots. Ownership is a single
 * flag per plot: once a deed is claimed, the plot is open for everyone (single-player first).</p>
 */
public class PlotData extends SavedData {
    public static final String NAME = "holeydigsite_plots";

    /** One plot: its id ("deep_dig"), board position, world-space bounds, whether it has been bought, and who owns it. */
    public record Plot(String id, BlockPos board, BoundingBox box, boolean owned, String owner) {
        public Plot(String id, BlockPos board, BoundingBox box, boolean owned) {
            this(id, board, box, owned, "");
        }
        public String key() {
            return keyOf(id, board);
        }
    }

    private final Map<String, Plot> plots = new LinkedHashMap<>();

    public static String keyOf(String id, BlockPos board) {
        return id + "@" + board.getX() + "," + board.getY() + "," + board.getZ();
    }

    public static PlotData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlotData::new, PlotData::load, null), NAME);
    }

    /** Adds the plot (locked) or updates its bounds; keeps the owned flag. Returns the stored plot. */
    public Plot register(String id, BlockPos board, BoundingBox box) {
        String key = keyOf(id, board);
        Plot old = plots.get(key);
        Plot now = new Plot(id, board.immutable(), box, old != null && old.owned(), old != null ? old.owner() : "");
        if (!now.equals(old)) {
            plots.put(key, now);
            setDirty();
        }
        return now;
    }

    @Nullable
    public Plot get(String id, BlockPos board) {
        return plots.get(keyOf(id, board));
    }

    public void setOwned(String id, BlockPos board, boolean owned, String owner) {
        Plot p = get(id, board);
        if (p != null) {
            String o = owned ? (owner == null ? "" : owner) : "";
            if (p.owned() != owned || !p.owner().equals(o)) {
                plots.put(p.key(), new Plot(p.id(), p.board(), p.box(), owned, o));
                setDirty();
            }
        }
    }

    public void setOwned(String id, BlockPos board, boolean owned) {
        setOwned(id, board, owned, "");
    }

    /** Forgets a plot (tests; a removed board). */
    public void remove(String id, BlockPos board) {
        if (plots.remove(keyOf(id, board)) != null) {
            setDirty();
        }
    }

    /** The id of the plot (owned or not) whose bounds contain {@code pos}, or null. */
    @Nullable
    public String plotIdAt(BlockPos pos) {
        for (Plot p : plots.values()) {
            if (p.box().isInside(pos)) {
                return p.id();
            }
        }
        return null;
    }

    /** The locked (not yet bought) plot containing {@code pos}, or null. */
    @Nullable
    public Plot lockedAt(BlockPos pos) {
        for (Plot p : plots.values()) {
            if (!p.owned() && p.box().isInside(pos)) {
                return p;
            }
        }
        return null;
    }

    public Collection<Plot> all() {
        return Collections.unmodifiableCollection(plots.values());
    }

    // ---- save / load ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Plot p : plots.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("Id", p.id());
            t.putLong("Board", p.board().asLong());
            BoundingBox b = p.box();
            t.putIntArray("Box", new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
            t.putBoolean("Owned", p.owned());
            if (p.owner() != null && !p.owner().isEmpty()) {
                t.putString("Owner", p.owner());
            }
            list.add(t);
        }
        tag.put("Plots", list);
        return tag;
    }

    public static PlotData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlotData d = new PlotData();
        for (Tag t0 : tag.getList("Plots", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            int[] b = t.getIntArray("Box");
            if (b.length != 6) {
                continue;
            }
            Plot p = new Plot(t.getString("Id"), BlockPos.of(t.getLong("Board")),
                    new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), t.getBoolean("Owned"), t.getString("Owner"));
            d.plots.put(p.key(), p);
        }
        return d;
    }
}
