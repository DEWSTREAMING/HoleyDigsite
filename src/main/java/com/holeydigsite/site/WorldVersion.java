package com.holeydigsite.site;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The world's content version (Phase 14) - a general hook so later phases can fix up existing worlds
 * ({@code data/holeydigsite_world.dat} in the overworld).
 *
 * <p>On every world load, each {@link Step} in {@link #STEPS} whose version is higher than the world's runs once, in order,
 * and the world is stamped with the new version. A world without the file (a new world, or one made before 1.12.0) is
 * stamped with {@link #CURRENT} and no steps run.</p>
 *
 * <p>Adding an upgrade later: bump {@link #CURRENT} and add {@code new Step(CURRENT, "what it does", server -> ...)} to
 * {@link #STEPS} (e.g. replace a renamed block in every site zone, move saved data). Steps must be safe to run on any world
 * that was stamped with an older version.</p>
 */
public class WorldVersion extends SavedData {
    public static final String NAME = "holeydigsite_world";

    /** Content version of this mod build. 1 = Phase 14 (1.12.0): sites + this hook. 2 = Phase 16.1 (1.14.2): floating-block fix-ups. 3 = 1.14.4: slits under the Trading Post corner / kiosk foundation. 4 = 1.18.1: new sign texts. */
    public static final int CURRENT = 4;

    /** One upgrade: runs once on worlds older than {@code version}. */
    public record Step(int version, String name, Consumer<MinecraftServer> run) {}

    /** The upgrade steps. */
    public static final List<Step> STEPS = List.of(
            new Step(2, "floating blocks on the starting island and in owned plots (Phase 16.1)",
                    server -> WorldFixups.applyAll(server, WorldFixups.ALL.get(0))),
            new Step(3, "air slits under ground-level blocks in owned plots (Phase 16.1, 1.14.4)",
                    server -> WorldFixups.applyAll(server, WorldFixups.ALL.get(1))),
            new Step(4, "sign texts rewritten (Phase 20.1, 1.18.1): drill pad, signpost, plot sign posts",
                    server -> WorldFixups.applyAll(server, WorldFixups.ALL.get(2))));

    private int version;
    private final List<String> applied = new ArrayList<>();

    public int version() {
        return version;
    }

    public List<String> applied() {
        return List.copyOf(applied);
    }

    public void setVersion(int v) {
        version = v;
        setDirty();
    }

    private static SavedData.Factory<WorldVersion> factory() {
        return new SavedData.Factory<>(() -> {
            WorldVersion w = new WorldVersion();
            w.version = CURRENT;   // a world without the file is new (or pre-1.12): nothing to upgrade
            w.setDirty();
            return w;
        }, WorldVersion::load, null);
    }

    public static WorldVersion get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** World load: runs the pending {@link #STEPS}. */
    public static List<String> upgrade(MinecraftServer server) {
        return run(server, get(server), STEPS, CURRENT);
    }

    /** Runs every step newer than the data's version (once each, in version order) and stamps the data. */
    public static List<String> run(MinecraftServer server, WorldVersion data, List<Step> steps, int current) {
        List<String> ran = new ArrayList<>();
        List<Step> sorted = new ArrayList<>(steps);
        sorted.sort(Comparator.comparingInt(Step::version));
        for (Step s : sorted) {
            if (s.version() > data.version) {
                HoleyDigsite.LOGGER.info("World upgrade {}: {}", s.version(), s.name());
                s.run().accept(server);
                data.version = s.version();
                data.applied.add(s.version() + ": " + s.name());
                ran.add(s.name());
                data.setDirty();
            }
        }
        if (data.version < current) {
            data.version = current;
            data.setDirty();
        }
        return ran;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("ContentVersion", version);
        ListTag list = new ListTag();
        for (String s : applied) {
            list.add(StringTag.valueOf(s));
        }
        tag.put("Applied", list);
        return tag;
    }

    public static WorldVersion load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldVersion w = new WorldVersion();
        w.version = tag.getInt("ContentVersion");
        for (Tag t : tag.getList("Applied", Tag.TAG_STRING)) {
            w.applied.add(t.getAsString());
        }
        return w;
    }

    /** A blank instance (tests). */
    public static WorldVersion blank(int version) {
        WorldVersion w = new WorldVersion();
        w.version = version;
        return w;
    }
}
