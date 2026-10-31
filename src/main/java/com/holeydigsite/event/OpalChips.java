package com.holeydigsite.event;

import com.holeydigsite.ledger.ModAttachments;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A player's Opal Chips: per event, the balance and everything ever earned there. Saved on the player and copied on death.
 * Chips only come from the event Sell Pad ({@link EventChips#earn}); there is no command for them. On the client this holds
 * the last synced copy plus the active event id.
 */
public final class OpalChips implements INBTSerializable<CompoundTag> {
    private final Map<String, long[]> accounts = new LinkedHashMap<>();
    /** Client only: the active event when this copy was synced ("" = none). */
    private String active = "";

    public static OpalChips get(Player player) {
        return player.getData(ModAttachments.OPAL_CHIPS.get());
    }

    public long balance(String event) {
        long[] a = accounts.get(event);
        return a == null ? 0 : a[0];
    }

    public long earned(String event) {
        long[] a = accounts.get(event);
        return a == null ? 0 : a[1];
    }

    public Set<String> events() {
        return accounts.keySet();
    }

    public String active() {
        return active;
    }

    void setActive(String id) {
        active = id == null ? "" : id;
    }

    /** Adds chips to an event's balance and its total earned. */
    void add(String event, long n) {
        if (n <= 0 || event == null || event.isEmpty()) {
            return;
        }
        long[] a = accounts.computeIfAbsent(event, k -> new long[2]);
        a[0] += n;
        a[1] += n;
    }

    /** Takes chips from an event's balance; the total earned stays. False (and nothing taken) if there aren't enough. */
    boolean take(String event, long n) {
        if (n <= 0) {
            return true;
        }
        long[] a = accounts.get(event);
        if (a == null || a[0] < n) {
            return false;
        }
        a[0] -= n;
        return true;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<String, long[]> e : accounts.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Event", e.getKey());
            t.putLong("Balance", e.getValue()[0]);
            t.putLong("Earned", e.getValue()[1]);
            list.add(t);
        }
        tag.put("Accounts", list);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        accounts.clear();
        for (Tag t0 : tag.getList("Accounts", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) t0;
            accounts.put(t.getString("Event"), new long[]{Math.max(0, t.getLong("Balance")), Math.max(0, t.getLong("Earned"))});
        }
        if (tag.contains("Active")) {
            active = tag.getString("Active");
        }
    }
}
