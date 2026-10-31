package com.holeydigsite.ledger;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-player Prospector's Ledger state attached to {@link Player} via NeoForge data attachments.
 * Saved to player NBT and copied across death ({@code copyOnDeath}).
 */
public final class LedgerData implements INBTSerializable<CompoundTag> {
    private int renown;
    private final Set<Integer> claimedLevels = new LinkedHashSet<>();
    private final Set<ResourceLocation> completedMilestones = new LinkedHashSet<>();
    private long lifetimeCopper;

    private int panRenown;
    private int panRemainder;
    private int sluiceRenown;
    private int sluiceRemainder;
    private int harvestRenown;
    private int harvestRemainder;
    private int smithRenown;
    private int smithRemainder;
    private int sellRenown;
    private long sellRemainder;
    private int contractRenown;
    private final com.holeydigsite.contract.ContractData contracts = new com.holeydigsite.contract.ContractData();

    private final Set<ResourceLocation> harvestedCrops = new LinkedHashSet<>();
    private final Set<String> smithedPieces = new LinkedHashSet<>();
    private boolean starterGiven;
    private boolean starterBedGiven;
    private boolean starterFieldGuideGiven;
    private boolean fieldGuideOpened;
    private boolean oldWorldChecked;
    private String chosenTitle = "";
    private final Set<String> unlockedTitles = new LinkedHashSet<>();
    private final Set<String> unlockedSkins = new LinkedHashSet<>();
    /** Wear of each old unlocked skin that is not Fresh from the Forge (missing = Fresh). */
    private final Map<String, String> skinWears = new LinkedHashMap<>();
    private boolean skinsMigrated;
    /** The crate switch: null until the player first uses it (then the server config default decides). */
    private Boolean cratesOn;
    /** Collection items the player has had, as {@code crate_id/kind:id} (see {@link com.holeydigsite.crate.Collections}). */
    private final Set<String> collected = new LinkedHashSet<>();
    private boolean collectionsScanned;

    public static LedgerData get(Player player) {
        return player.getData(ModAttachments.LEDGER_DATA.get());
    }

    public int renown() { return renown; }
    public void setRenown(int renown) { this.renown = Math.max(0, renown); }
    public void addRenown(int delta) {
        if (delta > 0) {
            this.renown += delta;
        }
    }

    public int level() {
        return LedgerDefs.curve().levelForRenown(renown);
    }

    public Set<Integer> claimedLevels() { return claimedLevels; }
    public boolean isLevelClaimed(int lvl) { return claimedLevels.contains(lvl); }
    public void markLevelClaimed(int lvl) { claimedLevels.add(lvl); }

    public Set<ResourceLocation> completedMilestones() { return completedMilestones; }
    public boolean isMilestoneCompleted(ResourceLocation id) { return completedMilestones.contains(id); }
    public boolean markMilestoneCompleted(ResourceLocation id) { return completedMilestones.add(id); }

    public long lifetimeCopper() { return lifetimeCopper; }
    public void addLifetimeCopper(long copper) {
        if (copper > 0) {
            this.lifetimeCopper += copper;
        }
    }
    public void setLifetimeCopper(long lifetimeCopper) { this.lifetimeCopper = Math.max(0L, lifetimeCopper); }

    public int panRenown() { return panRenown; }
    public int sluiceRenown() { return sluiceRenown; }
    public int harvestRenown() { return harvestRenown; }
    public int smithRenown() { return smithRenown; }
    public int sellRenown() { return sellRenown; }
    public int contractRenown() { return contractRenown; }
    public com.holeydigsite.contract.ContractData contracts() { return contracts; }

    public void setPanRenown(int val) { this.panRenown = Math.max(0, val); }
    public void setSluiceRenown(int val) { this.sluiceRenown = Math.max(0, val); }
    public void setHarvestRenown(int val) { this.harvestRenown = Math.max(0, val); }
    public void setSmithRenown(int val) { this.smithRenown = Math.max(0, val); }
    public void setSellRenown(int val) { this.sellRenown = Math.max(0, val); }
    public void setContractRenown(int val) { this.contractRenown = Math.max(0, val); }

    public int addPanProgress(int count, LedgerDefs.OngoingRule rule) {
        if (count <= 0 || panRenown >= rule.cap()) return 0;
        panRemainder += count;
        int batches = panRemainder / rule.per();
        if (batches <= 0) return 0;
        panRemainder %= rule.per();
        int gain = Math.min(batches * rule.renown(), rule.cap() - panRenown);
        panRenown += gain;
        renown += gain;
        return gain;
    }

    public int addSluiceProgress(int count, LedgerDefs.OngoingRule rule) {
        if (count <= 0 || sluiceRenown >= rule.cap()) return 0;
        sluiceRemainder += count;
        int batches = sluiceRemainder / rule.per();
        if (batches <= 0) return 0;
        sluiceRemainder %= rule.per();
        int gain = Math.min(batches * rule.renown(), rule.cap() - sluiceRenown);
        sluiceRenown += gain;
        renown += gain;
        return gain;
    }

    public int addHarvestProgress(int count, LedgerDefs.OngoingRule rule) {
        if (count <= 0 || harvestRenown >= rule.cap()) return 0;
        harvestRemainder += count;
        int batches = harvestRemainder / rule.per();
        if (batches <= 0) return 0;
        harvestRemainder %= rule.per();
        int gain = Math.min(batches * rule.renown(), rule.cap() - harvestRenown);
        harvestRenown += gain;
        renown += gain;
        return gain;
    }

    public int addSmithProgress(int count, LedgerDefs.OngoingRule rule) {
        if (count <= 0 || smithRenown >= rule.cap()) return 0;
        smithRemainder += count;
        int batches = smithRemainder / rule.per();
        if (batches <= 0) return 0;
        smithRemainder %= rule.per();
        int gain = Math.min(batches * rule.renown(), rule.cap() - smithRenown);
        smithRenown += gain;
        renown += gain;
        return gain;
    }

    public int addSellProgress(long copper, LedgerDefs.OngoingRule rule) {
        if (copper <= 0 || sellRenown >= rule.cap()) return 0;
        sellRemainder += copper;
        long batches = sellRemainder / rule.per();
        if (batches <= 0) return 0;
        sellRemainder %= rule.per();
        int gain = (int) Math.min(batches * (long) rule.renown(), (long) (rule.cap() - sellRenown));
        sellRenown += gain;
        renown += gain;
        return gain;
    }

    /** Adds Renown from a finished contract, up to what is left under the cap. Returns the Renown added. */
    public int addContractRenown(int amount, LedgerDefs.OngoingRule rule) {
        if (amount <= 0 || contractRenown >= rule.cap()) return 0;
        int gain = Math.min(amount, rule.cap() - contractRenown);
        contractRenown += gain;
        renown += gain;
        return gain;
    }

    public Set<ResourceLocation> harvestedCrops() { return harvestedCrops; }
    public int recordCropHarvested(ResourceLocation cropId) {
        harvestedCrops.add(cropId);
        return harvestedCrops.size();
    }

    public Set<String> smithedPieces() { return smithedPieces; }
    public int recordPieceSmithed(String piece) {
        smithedPieces.add(piece);
        return smithedPieces.size();
    }

    public boolean starterGiven() { return starterGiven; }
    public void setStarterGiven(boolean starterGiven) { this.starterGiven = starterGiven; }
    public boolean starterBedGiven() { return starterBedGiven; }
    public void setStarterBedGiven(boolean starterBedGiven) { this.starterBedGiven = starterBedGiven; }
    public boolean starterFieldGuideGiven() { return starterFieldGuideGiven; }
    public void setStarterFieldGuideGiven(boolean starterFieldGuideGiven) { this.starterFieldGuideGiven = starterFieldGuideGiven; }
    public boolean fieldGuideOpened() { return fieldGuideOpened; }
    public void setFieldGuideOpened(boolean fieldGuideOpened) { this.fieldGuideOpened = fieldGuideOpened; }

    public boolean oldWorldChecked() { return oldWorldChecked; }
    public void setOldWorldChecked(boolean oldWorldChecked) { this.oldWorldChecked = oldWorldChecked; }

    public String chosenTitle() { return chosenTitle; }
    public void setChosenTitle(String chosenTitle) { this.chosenTitle = chosenTitle == null ? "" : chosenTitle; }

    /** Titles unlocked by claiming the level that grants them. */
    public Set<String> unlockedTitles() { return unlockedTitles; }
    public boolean hasTitle(String id) { return unlockedTitles.contains(id); }
    public boolean unlockTitle(String id) { return unlockedTitles.add(id); }

    /**
     * Skins unlocked on the player before Skin Kits existed. Nothing adds to this any more; {@link
     * com.holeydigsite.skin.SkinKitMigration} turns it into kits once and clears it.
     */
    public Set<String> unlockedSkins() { return unlockedSkins; }

    /** Wear of an old unlocked skin (Fresh if it was unlocked without one). */
    public String skinWear(String id) {
        return skinWears.getOrDefault(id, com.holeydigsite.skin.SkinWear.FRESH);
    }

    public void setSkinWear(String id, String wear) {
        if (com.holeydigsite.skin.SkinWear.isFresh(wear)) {
            skinWears.remove(id);
        } else {
            skinWears.put(id, com.holeydigsite.skin.SkinWear.normalize(wear));
        }
    }

    public void clearOldSkins() {
        unlockedSkins.clear();
        skinWears.clear();
    }

    /** True once the old unlocks were turned into kits (so it never happens twice). */
    public boolean skinsMigrated() { return skinsMigrated; }
    public void setSkinsMigrated(boolean skinsMigrated) { this.skinsMigrated = skinsMigrated; }

    /** True if crates and skins are on for this player (the crate switch), falling back to the server config. */
    public boolean cratesOn() { return cratesOn != null ? cratesOn : com.holeydigsite.crate.CrateConfig.defaultOn(); }
    public void setCratesOn(boolean on) { this.cratesOn = on; }

    public Set<String> collected() { return collected; }
    public boolean markCollected(String key) { return collected.add(key); }
    public boolean collectionsScanned() { return collectionsScanned; }
    public void setCollectionsScanned(boolean scanned) { this.collectionsScanned = scanned; }

    /** Chosen title if it is still unlocked, else empty string (no title). */
    public String activeTitle() { return unlockedTitles.contains(chosenTitle) ? chosenTitle : ""; }

    /** Resets the five ongoing renown play caps and remainders back to zero. */
    public void resetCaps() {
        this.panRenown = 0;
        this.panRemainder = 0;
        this.sluiceRenown = 0;
        this.sluiceRemainder = 0;
        this.harvestRenown = 0;
        this.harvestRemainder = 0;
        this.smithRenown = 0;
        this.smithRemainder = 0;
        this.sellRenown = 0;
        this.sellRemainder = 0;
        this.contractRenown = 0;
    }

    /** Wipes all Ledger progress, including renown, claims, milestones, titles, caps, and lifetime sales. */
    public void resetAll() {
        this.renown = 0;
        this.claimedLevels.clear();
        this.completedMilestones.clear();
        this.lifetimeCopper = 0;
        resetCaps();
        this.harvestedCrops.clear();
        this.smithedPieces.clear();
        this.starterGiven = true;
        this.starterBedGiven = true;
        this.starterFieldGuideGiven = true;
        this.fieldGuideOpened = true;
        this.oldWorldChecked = true;
        this.chosenTitle = "";
        this.unlockedTitles.clear();
        this.unlockedSkins.clear();
        this.skinWears.clear();
        this.skinsMigrated = true;
        this.collected.clear();
        this.collectionsScanned = true;
        this.contracts.clear();
    }

    public void copyFrom(LedgerData other) {
        this.renown = other.renown;
        this.claimedLevels.clear();
        this.claimedLevels.addAll(other.claimedLevels);
        this.completedMilestones.clear();
        this.completedMilestones.addAll(other.completedMilestones);
        this.lifetimeCopper = other.lifetimeCopper;
        this.panRenown = other.panRenown;
        this.panRemainder = other.panRemainder;
        this.sluiceRenown = other.sluiceRenown;
        this.sluiceRemainder = other.sluiceRemainder;
        this.harvestRenown = other.harvestRenown;
        this.harvestRemainder = other.harvestRemainder;
        this.smithRenown = other.smithRenown;
        this.smithRemainder = other.smithRemainder;
        this.sellRenown = other.sellRenown;
        this.sellRemainder = other.sellRemainder;
        this.contractRenown = other.contractRenown;
        this.contracts.copyFrom(other.contracts);
        this.harvestedCrops.clear();
        this.harvestedCrops.addAll(other.harvestedCrops);
        this.smithedPieces.clear();
        this.smithedPieces.addAll(other.smithedPieces);
        this.starterGiven = other.starterGiven;
        this.starterBedGiven = other.starterBedGiven;
        this.starterFieldGuideGiven = other.starterFieldGuideGiven;
        this.fieldGuideOpened = other.fieldGuideOpened;
        this.oldWorldChecked = other.oldWorldChecked;
        this.chosenTitle = other.chosenTitle;
        this.unlockedTitles.clear();
        this.unlockedTitles.addAll(other.unlockedTitles);
        this.unlockedSkins.clear();
        this.unlockedSkins.addAll(other.unlockedSkins);
        this.skinWears.clear();
        this.skinWears.putAll(other.skinWears);
        this.skinsMigrated = other.skinsMigrated;
        this.cratesOn = other.cratesOn;
        this.collected.clear();
        this.collected.addAll(other.collected);
        this.collectionsScanned = other.collectionsScanned;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Renown", renown);
        tag.put("ClaimedLevels", new IntArrayTag(claimedLevels.stream().mapToInt(Integer::intValue).toArray()));
        ListTag ms = new ListTag();
        for (ResourceLocation id : completedMilestones) {
            ms.add(StringTag.valueOf(id.toString()));
        }
        tag.put("CompletedMilestones", ms);
        tag.putLong("LifetimeCopper", lifetimeCopper);

        tag.putInt("PanRenown", panRenown);
        tag.putInt("PanRemainder", panRemainder);
        tag.putInt("SluiceRenown", sluiceRenown);
        tag.putInt("SluiceRemainder", sluiceRemainder);
        tag.putInt("HarvestRenown", harvestRenown);
        tag.putInt("HarvestRemainder", harvestRemainder);
        tag.putInt("SmithRenown", smithRenown);
        tag.putInt("SmithRemainder", smithRemainder);
        tag.putInt("SellRenown", sellRenown);
        tag.putLong("SellRemainder", sellRemainder);
        tag.putInt("ContractRenown", contractRenown);
        tag.put("Contracts", contracts.serialize());

        ListTag crops = new ListTag();
        for (ResourceLocation id : harvestedCrops) {
            crops.add(StringTag.valueOf(id.toString()));
        }
        tag.put("HarvestedCrops", crops);
        ListTag pieces = new ListTag();
        for (String p : smithedPieces) {
            pieces.add(StringTag.valueOf(p));
        }
        tag.put("SmithedPieces", pieces);
        tag.putBoolean("StarterGiven", starterGiven);
        tag.putBoolean("StarterBedGiven", starterBedGiven);
        tag.putBoolean("StarterFieldGuideGiven", starterFieldGuideGiven);
        tag.putBoolean("FieldGuideOpened", fieldGuideOpened);
        tag.putBoolean("OldWorldChecked", oldWorldChecked);
        tag.putString("ChosenTitle", chosenTitle);
        ListTag titles = new ListTag();
        for (String id : unlockedTitles) {
            titles.add(StringTag.valueOf(id));
        }
        tag.put("UnlockedTitles", titles);
        ListTag skins = new ListTag();
        for (String id : unlockedSkins) {
            skins.add(StringTag.valueOf(id));
        }
        tag.put("UnlockedSkins", skins);
        CompoundTag wears = new CompoundTag();
        skinWears.forEach(wears::putString);
        tag.put("SkinWears", wears);
        tag.putBoolean("SkinsMigrated", skinsMigrated);
        if (cratesOn != null) tag.putBoolean("CratesOn", cratesOn);
        ListTag coll = new ListTag();
        for (String key : collected) {
            coll.add(StringTag.valueOf(key));
        }
        tag.put("Collected", coll);
        tag.putBoolean("CollectionsScanned", collectionsScanned);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.renown = tag.getInt("Renown");
        this.claimedLevels.clear();
        for (int lvl : tag.getIntArray("ClaimedLevels")) {
            this.claimedLevels.add(lvl);
        }
        this.completedMilestones.clear();
        ListTag ms = tag.getList("CompletedMilestones", Tag.TAG_STRING);
        for (int i = 0; i < ms.size(); i++) {
            this.completedMilestones.add(ResourceLocation.parse(ms.getString(i)));
        }
        this.lifetimeCopper = tag.getLong("LifetimeCopper");

        this.panRenown = tag.getInt("PanRenown");
        this.panRemainder = tag.getInt("PanRemainder");
        this.sluiceRenown = tag.getInt("SluiceRenown");
        this.sluiceRemainder = tag.getInt("SluiceRemainder");
        this.harvestRenown = tag.getInt("HarvestRenown");
        this.harvestRemainder = tag.getInt("HarvestRemainder");
        this.smithRenown = tag.getInt("SmithRenown");
        this.smithRemainder = tag.getInt("SmithRemainder");
        this.sellRenown = tag.getInt("SellRenown");
        this.sellRemainder = tag.getLong("SellRemainder");
        this.contractRenown = tag.getInt("ContractRenown");
        this.contracts.deserialize(tag.getCompound("Contracts"));

        this.harvestedCrops.clear();
        ListTag crops = tag.getList("HarvestedCrops", Tag.TAG_STRING);
        for (int i = 0; i < crops.size(); i++) {
            this.harvestedCrops.add(ResourceLocation.parse(crops.getString(i)));
        }
        this.smithedPieces.clear();
        ListTag pieces = tag.getList("SmithedPieces", Tag.TAG_STRING);
        for (int i = 0; i < pieces.size(); i++) {
            this.smithedPieces.add(pieces.getString(i));
        }
        this.starterGiven = tag.getBoolean("StarterGiven");
        this.starterBedGiven = tag.getBoolean("StarterBedGiven");
        this.starterFieldGuideGiven = tag.getBoolean("StarterFieldGuideGiven");
        this.fieldGuideOpened = tag.getBoolean("FieldGuideOpened");
        this.oldWorldChecked = tag.getBoolean("OldWorldChecked");
        this.chosenTitle = tag.getString("ChosenTitle");
        this.unlockedTitles.clear();
        ListTag titles = tag.getList("UnlockedTitles", Tag.TAG_STRING);
        for (int i = 0; i < titles.size(); i++) {
            this.unlockedTitles.add(titles.getString(i));
        }
        this.unlockedSkins.clear();
        ListTag skins = tag.getList("UnlockedSkins", Tag.TAG_STRING);
        for (int i = 0; i < skins.size(); i++) {
            this.unlockedSkins.add(skins.getString(i));
        }
        this.skinWears.clear();
        CompoundTag wears = tag.getCompound("SkinWears");
        for (String key : wears.getAllKeys()) {
            this.skinWears.put(key, wears.getString(key));
        }
        this.skinsMigrated = tag.getBoolean("SkinsMigrated");
        this.cratesOn = tag.contains("CratesOn") ? tag.getBoolean("CratesOn") : null;
        this.collected.clear();
        ListTag coll = tag.getList("Collected", Tag.TAG_STRING);
        for (int i = 0; i < coll.size(); i++) {
            this.collected.add(coll.getString(i));
        }
        this.collectionsScanned = tag.getBoolean("CollectionsScanned");
    }
}
