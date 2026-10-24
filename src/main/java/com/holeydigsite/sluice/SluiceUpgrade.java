package com.holeydigsite.sluice;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The upgrade slot of a Sluice Box (the {@code upgrade} block state, so it shows on the model and costs
 * nothing to store). Both inserts only affect their own clump kind, where each wash of that kind has {@code extraChance} to be
 * washed a second time for free, and the Fine Mesh Screen also rolls a small
 * extra Topaz table. Keep the numbers in sync with {@code tools/EconomySim.java}.
 */
public enum SluiceUpgrade implements StringRepresentable {
    NONE("none", 0, 0.0, null, () -> null),
    /** Riffle Insert for gravel clumps, +25% value. */
    RIFFLE("riffle", DirtTier.GRAVEL, 0.25, null, () -> ModItems.RIFFLE_INSERT.get()),
    /** Fine Mesh Screen for sand clumps, +24% value and a slightly better Topaz chance. */
    MESH("mesh", DirtTier.SAND, 0.24, "panning/sand_mesh_bonus", () -> ModItems.FINE_MESH_SCREEN.get()),
    /**
     * Seed Screen for every clump kind, with no extra wash but one extra seed roll per clump from the table of
     * that kind ({@code panning/seeds_<kind>}, see {@link #seedTable}).
     */
    SEED("seed", 0, 0.0, null, () -> ModItems.SEED_SCREEN.get());

    /** {@link #kind()} of an upgrade that works on clumps of every kind. */
    public static final int ANY_KIND = 0;

    private static final String[] SEED_TABLES = {null, "panning/seeds_dirt_1", "panning/seeds_dirt_2", "panning/seeds_dirt_3",
            "panning/seeds_gravel", "panning/seeds_sand", "panning/seeds_dirt_1", "panning/seeds_dirt_1", "panning/seeds_dirt_1"};

    private final String key;
    private final int kind;
    private final double extraChance;
    private final ResourceKey<LootTable> bonusTable;
    private final Supplier<Item> item;

    SluiceUpgrade(String key, int kind, double extraChance, @Nullable String bonusTable, Supplier<Item> item) {
        this.key = key;
        this.kind = kind;
        this.extraChance = extraChance;
        this.bonusTable = bonusTable == null ? null : ResourceKey.create(Registries.LOOT_TABLE, HoleyDigsite.id(bonusTable));
        this.item = item;
    }

    @Override
    public String getSerializedName() {
        return key;
    }

    /** The clump kind this upgrade improves ({@link DirtTier}). */
    public int kind() {
        return kind;
    }

    /** Chance of a free extra wash of a clump of {@link #kind()}. */
    public double extraChance() {
        return extraChance;
    }

    /** True if this upgrade does something for a clump of {@code clumpKind}. */
    public boolean appliesTo(int clumpKind) {
        return this != NONE && (kind == ANY_KIND || kind == clumpKind);
    }

    /** The extra loot table rolled once for a clump of {@code clumpKind} (null = none): the fixed bonus table, or the seed table for the Seed Screen. */
    @Nullable
    public ResourceKey<LootTable> bonusTable(int clumpKind) {
        if (this == SEED) {
            return seedTable(clumpKind);
        }
        return appliesTo(clumpKind) ? bonusTable : null;
    }

    /** Seed table of a clump kind (1-5). */
    public static ResourceKey<LootTable> seedTable(int clumpKind) {
        return ResourceKey.create(Registries.LOOT_TABLE, HoleyDigsite.id(SEED_TABLES[DirtTier.clamp(clumpKind)]));
    }

    /** Extra loot table rolled once per clump of {@link #kind()}, or null (not for the Seed Screen: see {@link #bonusTable(int)}). */
    @Nullable
    public ResourceKey<LootTable> bonusTable() {
        return bonusTable;
    }

    /** The item that installs this upgrade (null for NONE). */
    @Nullable
    public Item item() {
        return item.get();
    }

    /** The upgrade a stack installs, or NONE. */
    public static SluiceUpgrade of(ItemStack stack) {
        for (SluiceUpgrade u : values()) {
            if (u != NONE && stack.is(u.item.get())) {
                return u;
            }
        }
        return NONE;
    }
}
