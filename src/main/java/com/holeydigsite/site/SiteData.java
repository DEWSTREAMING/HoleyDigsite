package com.holeydigsite.site;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sites built in one dimension, saved with the world ({@code data/holeydigsite_sites.dat}): for every store,
 * plot and kiosk the mod placed, its anchor, its zone (fixed once built), the version it was built from and the items its
 * displays showed last, plus recent store updated announcements for players who log in after a rebuild.
 */
public class SiteData extends SavedData {
    public static final String NAME = "holeydigsite_sites";
    /** Announcements older than this in game ticks are not shown to players logging in. */
    public static final long ANNOUNCE_TICKS = 72000;

    /** One built site where {@code parent} is the key of the site this one was built inside, or null. */
    public record Site(ResourceLocation id, BlockPos anchor, BoundingBox zone, int version, List<String> shown,
                       @Nullable String parent) {
        public String key() {
            return keyOf(id, anchor);
        }
    }

    public record Announcement(int serial, long time, Component text) {}

    private final Map<String, Site> sites = new LinkedHashMap<>();
    private final List<Announcement> announcements = new ArrayList<>();
    private int serial;

    public static String keyOf(ResourceLocation id, BlockPos anchor) {
        return id + "@" + anchor.getX() + "," + anchor.getY() + "," + anchor.getZ();
    }

    private static SavedData.Factory<SiteData> factory(HolderLookup.Provider unused) {
        return new SavedData.Factory<>(SiteData::new, SiteData::load, null);
    }

    public static SiteData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(level.registryAccess()), NAME);
    }

    /** The data if this level has any sites, without creating the file. */
    @Nullable
    public static SiteData getIfExists(ServerLevel level) {
        return level.getDataStorage().get(factory(level.registryAccess()), NAME);
    }

    @Nullable
    public Site get(String key) {
        return sites.get(key);
    }

    @Nullable
    public Site get(ResourceLocation id, BlockPos anchor) {
        return sites.get(keyOf(id, anchor));
    }

    public void put(Site site) {
        sites.put(site.key(), site);
        setDirty();
    }

    public void remove(String key) {
        if (sites.remove(key) != null) {
            setDirty();
        }
    }

    public Collection<Site> all() {
        return Collections.unmodifiableCollection(sites.values());
    }

    /** All sites with this id. */
    public List<Site> byId(ResourceLocation id) {
        return sites.values().stream().filter(s -> s.id().equals(id)).toList();
    }

    public List<Announcement> announcements() {
        return Collections.unmodifiableList(announcements);
    }

    public Announcement announce(Component text, long time) {
        Announcement a = new Announcement(++serial, time, text);
        announcements.add(a);
        while (announcements.size() > 20) {
            announcements.remove(0);
        }
        setDirty();
        return a;
    }

    // ---- save / load ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Site s : sites.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("Id", s.id().toString());
            t.putLong("Anchor", s.anchor().asLong());
            BoundingBox b = s.zone();
            t.putIntArray("Zone", new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
            t.putInt("Version", s.version());
            ListTag shown = new ListTag();
            for (String id : s.shown()) {
                shown.add(StringTag.valueOf(id));
            }
            t.put("Shown", shown);
            if (s.parent() != null) {
                t.putString("Parent", s.parent());
            }
            list.add(t);
        }
        tag.put("Sites", list);
        ListTag ann = new ListTag();
        for (Announcement a : announcements) {
            CompoundTag t = new CompoundTag();
            t.putInt("Serial", a.serial());
            t.putLong("Time", a.time());
            t.putString("Text", Component.Serializer.toJson(a.text(), registries));
            ann.add(t);
        }
        tag.put("Announcements", ann);
        tag.putInt("Serial", serial);
        return tag;
    }

    public static SiteData load(CompoundTag tag, HolderLookup.Provider registries) {
        SiteData d = new SiteData();
        for (Tag t0 : tag.getList("Sites", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            int[] b = t.getIntArray("Zone");
            ResourceLocation id = ResourceLocation.tryParse(t.getString("Id"));
            if (b.length != 6 || id == null) {
                continue;
            }
            List<String> shown = new ArrayList<>();
            for (Tag s : t.getList("Shown", Tag.TAG_STRING)) {
                shown.add(s.getAsString());
            }
            Site s = new Site(id, BlockPos.of(t.getLong("Anchor")), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]),
                    t.getInt("Version"), List.copyOf(shown), t.contains("Parent") ? t.getString("Parent") : null);
            d.sites.put(s.key(), s);
        }
        for (Tag t0 : tag.getList("Announcements", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            Component text = Component.Serializer.fromJson(t.getString("Text"), registries);
            if (text != null) {
                d.announcements.add(new Announcement(t.getInt("Serial"), t.getLong("Time"), text));
            }
        }
        d.serial = tag.getInt("Serial");
        return d;
    }
}
