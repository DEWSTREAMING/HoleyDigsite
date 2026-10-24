package com.holeydigsite.shop.custom;

import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 21d: the operator-made shop entries of a dimension, saved per site + display slot in world {@link SavedData} (NOT in
 * the structure), so a store rebuild puts them back ({@link #reapply}). A display outside every site is keyed by its position.
 * Entry ids: {@code custom:<siteKey>/<slot>} or {@code custom:@x,y,z}.
 *
 * <p>Rules: only a display without a catalogue item can be custom (the layout/catalogue always wins); only
 * {@link #clear} removes an entry; every set / clear gives the entry a new revision so old blueprints stop working.</p>
 */
public class CustomShops extends SavedData {
    public static final String NAME = "holeydigsite_custom_shops";
    public static final String PREFIX = "custom:";

    public enum Result { OK, NO_DISPLAY, CATALOGUE_ITEM, BAD_VALUES }

    private final Map<String, CustomEntry> entries = new LinkedHashMap<>();
    private int nextRev = 1;

    public static CustomShops get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CustomShops::new, CustomShops::load, null), NAME);
    }

    // ------------------------------------------------------------------------------------------------ keys

    /** The smallest site whose zone contains {@code pos} (a plot kiosk beats its plot), or null. */
    @Nullable
    public static SiteData.Site siteAt(ServerLevel level, BlockPos pos) {
        SiteData.Site best = null;
        long bestVolume = Long.MAX_VALUE;
        for (SiteData.Site s : SiteData.get(level).all()) {
            BoundingBox z = s.zone();
            if (z.isInside(pos)) {
                long volume = (long) z.getXSpan() * z.getYSpan() * z.getZSpan();
                if (volume < bestVolume) {
                    best = s;
                    bestVolume = volume;
                }
            }
        }
        return best;
    }

    /** The entry id for the display at {@code pos} (slot from the display; 0 = not part of a layout). */
    public static String keyFor(ServerLevel level, BlockPos pos, int slot) {
        SiteData.Site site = slot > 0 ? siteAt(level, pos) : null;
        return site != null ? PREFIX + site.key() + "/" + slot : PREFIX + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ------------------------------------------------------------------------------------------------ queries

    @Nullable
    public CustomEntry entry(String id) {
        return entries.get(id);
    }

    @Nullable
    public static CustomEntry find(ServerLevel level, String id) {
        return get(level).entry(id);
    }

    public Map<String, CustomEntry> all() {
        return java.util.Collections.unmodifiableMap(entries);
    }

    /** True while the blueprint's entry still exists with the same revision (nothing changed since it was handed out). */
    public static boolean valid(ServerLevel level, CustomBlueprint bp) {
        CustomEntry e = find(level, bp.id());
        return e != null && e.rev() == bp.rev();
    }

    /**
     * Buy-back price (copper per item) the store around the Sell Pad at {@code padPos} pays for {@code stack}: 0 = no operator
     * entry of that store wants it (the normal sell value applies). Entries match by {@link CustomEntry#matches}.
     */
    public static long buyBack(ServerLevel level, BlockPos padPos, ItemStack stack) {
        SiteData.Site site = siteAt(level, padPos);
        if (site == null || stack.isEmpty()) {
            return 0;
        }
        String prefix = PREFIX + site.key() + "/";
        for (Map.Entry<String, CustomEntry> e : get(level).entries.entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue().hasBuyBack() && e.getValue().matches(stack)) {
                return e.getValue().buyBack();
            }
        }
        return 0;
    }

    /** Value of a stack on the Sell Pad at {@code padPos}: that store's buy-back price, else the normal sell value. */
    public static long stackValue(net.minecraft.world.level.Level level, BlockPos padPos, ItemStack stack) {
        if (level instanceof ServerLevel sl) {
            long bb = buyBack(sl, padPos, stack);
            if (bb > 0) {
                return bb * stack.getCount();
            }
        }
        return com.holeydigsite.economy.Selling.stackValue(stack, level.registryAccess());
    }

    /** Value of ONE item on the Sell Pad at {@code padPos} (see {@link #stackValue}). */
    public static long unitValue(net.minecraft.world.level.Level level, BlockPos padPos, ItemStack stack) {
        if (level instanceof ServerLevel sl) {
            long bb = buyBack(sl, padPos, stack);
            if (bb > 0) {
                return bb;
            }
        }
        return com.holeydigsite.economy.Selling.unitValue(stack, level.registryAccess());
    }

    // ------------------------------------------------------------------------------------------------ changes

    /** Makes the display at {@code pos} a custom entry (or edits it). The caller has checked permission. */
    public static Result set(ServerLevel level, BlockPos pos, ItemStack template, int count, long price, long buyBack) {
        if (!(level.getBlockEntity(pos) instanceof ShopDisplayBlockEntity display)) {
            return Result.NO_DISPLAY;
        }
        if (display.item() != null) {
            return Result.CATALOGUE_ITEM;
        }
        if (template.isEmpty() || count < 1 || count > CustomEntry.MAX_COUNT || price < 1 || price > CustomEntry.MAX_PRICE
                || buyBack < 0 || buyBack > CustomEntry.MAX_PRICE) {
            return Result.BAD_VALUES;
        }
        CustomShops data = get(level);
        String id = keyFor(level, pos, display.slot());
        CustomEntry entry = new CustomEntry(template.copyWithCount(1), count, price, buyBack, data.nextRev++);
        data.entries.put(id, entry);
        data.setDirty();
        display.setCustom(entry);
        return Result.OK;
    }

    /** Removes the custom entry of the display at {@code pos} (the only way to remove one). Returns false if there was none. */
    public static boolean clear(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ShopDisplayBlockEntity display)) {
            return false;
        }
        CustomShops data = get(level);
        String id = keyFor(level, pos, display.slot());
        boolean had = data.entries.remove(id) != null;
        if (had || display.custom() != null) {
            data.nextRev++;
            data.setDirty();
            display.setCustom(null);
        }
        return had;
    }

    /** The entry id of a display (for info / blueprints). */
    public static String idOf(ServerLevel level, ShopDisplayBlockEntity display) {
        return keyFor(level, display.getBlockPos(), display.slot());
    }

    /**
     * Puts the saved entries of the site {@code siteKey} back on its (new) displays after a build, skipping displays that
     * belong to the child zones {@code exclude}. A slot the layout now fills with a catalogue item loses its custom entry.
     */
    public static void reapply(ServerLevel level, String siteKey, BoundingBox zone, List<BoundingBox> exclude) {
        CustomShops data = get(level);
        boolean changed = false;
        for (BlockEntity be : Sites.blockEntities(level, zone)) {
            if (!(be instanceof ShopDisplayBlockEntity d) || d.slot() <= 0) {
                continue;
            }
            BlockPos p = d.getBlockPos();
            boolean skip = false;
            for (BoundingBox x : exclude) {
                if (x.isInside(p)) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                continue;
            }
            String id = PREFIX + siteKey + "/" + d.slot();
            CustomEntry e = data.entries.get(id);
            if (e == null) {
                if (d.custom() != null) {
                    d.setCustom(null);
                }
            } else if (d.item() != null) {
                data.entries.remove(id);     // the catalogue wins
                data.nextRev++;
                changed = true;
                d.setCustom(null);
            } else if (d.custom() == null || d.custom().rev() != e.rev()) {
                d.setCustom(e);
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    /** A short description of an entry for chat. */
    public static String describe(CustomEntry e) {
        return e.count() + "x " + e.template().getHoverName().getString() + " for " + e.price() + " copper"
                + (e.hasBuyBack() ? ", buy-back " + e.buyBack() + " copper each" : "");
    }

    public static boolean isOp(Player player) {
        return player.hasPermissions(2);
    }

    // ------------------------------------------------------------------------------------------------ saving

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextRev", nextRev);
        ListTag list = new ListTag();
        for (Map.Entry<String, CustomEntry> e : entries.entrySet()) {
            CompoundTag t = e.getValue().save(registries);
            t.putString("Id", e.getKey());
            list.add(t);
        }
        tag.put("Entries", list);
        return tag;
    }

    public static CustomShops load(CompoundTag tag, HolderLookup.Provider registries) {
        CustomShops data = new CustomShops();
        data.nextRev = Math.max(1, tag.getInt("NextRev"));
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            CustomEntry e = CustomEntry.load(registries, t);
            if (e != null) {
                data.entries.put(t.getString("Id"), e);
            }
        }
        return data;
    }

    /** Ids of every entry (copy), for commands. */
    public List<String> ids() {
        return new ArrayList<>(entries.keySet());
    }
}
