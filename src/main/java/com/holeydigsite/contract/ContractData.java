package com.holeydigsite.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * A player's contracts: three slots, the last day they were refreshed, and counters. Lives inside the Ledger data, so it is saved
 * with the player, copied on death and synced to the client with the Ledger.
 */
public final class ContractData {
    public static final long NEVER = -1;

    private final Contract[] slots = new Contract[ContractDefs.MAX_SLOTS];
    private long lastDay = NEVER;
    private int completed;
    private int rerolls;

    public int size() {
        return slots.length;
    }

    @Nullable
    public Contract get(int slot) {
        return slot >= 0 && slot < slots.length ? slots[slot] : null;
    }

    public void set(int slot, @Nullable Contract c) {
        if (slot >= 0 && slot < slots.length) {
            slots[slot] = c;
        }
    }

    public long lastDay() {
        return lastDay;
    }

    public void setLastDay(long day) {
        lastDay = day;
    }

    public int completed() {
        return completed;
    }

    public void setCompleted(int n) {
        completed = Math.max(0, n);
    }

    public int rerolls() {
        return rerolls;
    }

    public int nextReroll() {
        return ++rerolls;
    }

    public int openCount() {
        int n = 0;
        for (Contract c : slots) {
            if (c != null && !c.isDone()) n++;
        }
        return n;
    }

    public void clear() {
        java.util.Arrays.fill(slots, null);
        lastDay = NEVER;
        completed = 0;
        rerolls = 0;
    }

    public void copyFrom(ContractData other) {
        deserialize(other.serialize());
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("LastDay", lastDay);
        tag.putInt("Completed", completed);
        tag.putInt("Rerolls", rerolls);
        ListTag list = new ListTag();
        for (int i = 0; i < slots.length; i++) {
            CompoundTag t = slots[i] == null ? new CompoundTag() : slots[i].save();
            t.putInt("Slot", i);
            list.add(t);
        }
        tag.put("Slots", list);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        java.util.Arrays.fill(slots, null);
        lastDay = tag.contains("LastDay") ? tag.getLong("LastDay") : NEVER;
        completed = tag.getInt("Completed");
        rerolls = tag.getInt("Rerolls");
        for (Tag t : tag.getList("Slots", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            int slot = c.getInt("Slot");
            if (slot >= 0 && slot < slots.length && c.contains("Kind")) {
                try {
                    slots[slot] = Contract.load(c);
                } catch (RuntimeException e) {
                    slots[slot] = null;
                }
            }
        }
    }
}
