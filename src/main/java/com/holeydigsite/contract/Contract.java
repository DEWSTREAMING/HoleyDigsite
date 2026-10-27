package com.holeydigsite.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One open (or just finished) contract in a player's slot. It stores the resolved ask, so changing the pool later never
 * breaks a contract that is already up.
 */
public final class Contract {
    public final String entry;
    public final ContractKind kind;
    /** Metal material, gem material, food item or piece item. */
    public final ResourceLocation item;
    /** Gems: "cut" or "uncut"; otherwise empty. */
    public final String form;
    /** Pieces: required main metal, or null. */
    @Nullable
    public final ResourceLocation metal;
    /** Pieces: null (no gem asked), "any", or a gem material id. */
    @Nullable
    public final String gem;
    /** Grams (metal) or count. */
    public final int amount;
    /** Smallest ingot (metal), gem weight (gems) or total ingot grams (pieces). */
    public final int min;
    public final long reward;
    public final int renown;
    public final long issued;
    private int delivered;
    private boolean done;

    public Contract(String entry, ContractKind kind, ResourceLocation item, String form, @Nullable ResourceLocation metal,
                    @Nullable String gem, int amount, int min, long reward, int renown, long issued) {
        this.entry = entry;
        this.kind = kind;
        this.item = item;
        this.form = form == null ? "" : form;
        this.metal = metal;
        this.gem = gem;
        this.amount = amount;
        this.min = min;
        this.reward = reward;
        this.renown = renown;
        this.issued = issued;
    }

    public int delivered() {
        return delivered;
    }

    public boolean isDone() {
        return done;
    }

    public int remaining() {
        return Math.max(0, amount - delivered);
    }

    /** Adds delivered units (never past the amount). Returns true when this finished the contract. */
    public boolean add(int units) {
        if (done || units <= 0) {
            return false;
        }
        delivered = Math.min(amount, delivered + units);
        if (delivered >= amount) {
            done = true;
            return true;
        }
        return false;
    }

    public void markDone() {
        delivered = amount;
        done = true;
    }

    /** Two open contracts never share this: same kind and the same item (and metal, for pieces). */
    public String key() {
        return kind.id() + ":" + item + (metal == null ? "" : "|" + metal);
    }

    /** Whole days this contract has been up, as of {@code day}. */
    public long age(long day) {
        return Math.max(0, day - issued);
    }

    /** Days left including today: 3 on the day it is issued, 1 on the last day. */
    public int daysLeft(long day, int days) {
        return (int) Math.max(0, days - age(day));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Entry", entry);
        t.putString("Kind", kind.id());
        t.putString("Item", item.toString());
        if (!form.isEmpty()) t.putString("Form", form);
        if (metal != null) t.putString("Metal", metal.toString());
        if (gem != null) t.putString("Gem", gem);
        t.putInt("Amount", amount);
        t.putInt("Min", min);
        t.putLong("Reward", reward);
        t.putInt("Renown", renown);
        t.putLong("Issued", issued);
        t.putInt("Delivered", delivered);
        t.putBoolean("Done", done);
        return t;
    }

    public static Contract load(CompoundTag t) {
        Contract c = new Contract(t.getString("Entry"), ContractKind.byId(t.getString("Kind")), ResourceLocation.parse(t.getString("Item")),
                t.getString("Form"), t.contains("Metal") ? ResourceLocation.parse(t.getString("Metal")) : null,
                t.contains("Gem") ? t.getString("Gem") : null, t.getInt("Amount"), t.getInt("Min"), t.getLong("Reward"),
                t.getInt("Renown"), t.getLong("Issued"));
        c.delivered = t.getInt("Delivered");
        c.done = t.getBoolean("Done");
        return c;
    }
}
