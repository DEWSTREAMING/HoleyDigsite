package com.holeydigsite.crate;

import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player has had from each crate collection, for the Ledger's Collections page. An entry counts once a crate gave it,
 * or the player owns it from another source (a title, a skin a Ledger level gave, an item found in the inventory by the
 * one-time scan at login). Old crates are not collections.
 */
public final class CrateCollections {
    private CrateCollections() {}

    /** The collections shown to players, in the order of {@link CrateDefs#BUILTIN_TIERS} (datapack ones after). */
    public static List<CrateDefs.CrateTier> shown() {
        List<CrateDefs.CrateTier> out = new ArrayList<>();
        for (String id : CrateDefs.BUILTIN_TIERS) {
            CrateDefs.CrateTier t = CrateDefs.tier(id);
            if (t != null && !t.old()) out.add(t);
        }
        for (CrateDefs.CrateTier t : CrateDefs.tiers().values()) {
            if (!t.old() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    public static String key(String tierId, CrateDefs.Entry e) {
        return tierId + "/" + e.kind().name().toLowerCase(java.util.Locale.ROOT) + ":" + e.id();
    }

    public static Component entryName(CrateDefs.Entry e) {
        return switch (e.kind()) {
            case SKIN -> SkinDefs.displayName(e.id());
            case TITLE -> Component.translatable(LedgerDefs.titleKey(e.id()));
            case COINS -> Component.translatable("item.holeydigsite.gold_coin");
            default -> Component.translatable(itemOf(e.id()).getDescriptionId());
        };
    }

    private static Item itemOf(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        Item item = loc == null ? Items.AIR : BuiltInRegistries.ITEM.get(loc);
        return item;
    }

    /** Remembers that a crate gave this entry. */
    public static void record(ServerPlayer sp, String tierId, CrateDefs.Entry e) {
        LedgerData.get(sp).markCollected(key(tierId, e));
    }

    /** True if the player has had this entry from a crate or owns it another way. */
    public static boolean has(Player player, String tierId, CrateDefs.Entry e) {
        LedgerData data = LedgerData.get(player);
        if (data.collected().contains(key(tierId, e))) return true;
        return switch (e.kind()) {
            case TITLE -> data.hasTitle(e.id());
            default -> false;
        };
    }

    /** A skin the player got from somewhere else (a Ledger level): it counts in every collection that holds it. */
    public static void recordSkin(ServerPlayer sp, String skin) {
        for (CrateDefs.CrateTier t : shown()) {
            for (String r : CrateDefs.RARITIES) {
                for (CrateDefs.Entry e : t.pool(r)) {
                    if (e.kind() == CrateDefs.Kind.SKIN && e.id().equals(skin)) record(sp, t.id(), e);
                }
            }
        }
    }

    public static int total(CrateDefs.CrateTier t) {
        int n = 0;
        for (String r : CrateDefs.RARITIES) n += t.pool(r).size();
        return n;
    }

    public static int have(Player player, CrateDefs.CrateTier t) {
        int n = 0;
        for (String r : CrateDefs.RARITIES) {
            for (CrateDefs.Entry e : t.pool(r)) {
                if (has(player, t.id(), e)) n++;
            }
        }
        return n;
    }

    /** Once per player: ticks the skin kits and decor the player already carries (inventory and ender chest). */
    public static void scanOnce(ServerPlayer sp) {
        LedgerData data = LedgerData.get(sp);
        if (data.collectionsScanned()) return;
        data.setCollectionsScanned(true);
        List<ItemStack> stacks = new ArrayList<>(sp.getInventory().items);
        stacks.addAll(sp.getInventory().offhand);
        for (int i = 0; i < sp.getEnderChestInventory().getContainerSize(); i++) stacks.add(sp.getEnderChestInventory().getItem(i));
        for (int level : data.claimedLevels()) {
            LedgerDefs.LevelDef def = LedgerDefs.level(level);
            if (def == null) continue;
            for (LedgerDefs.RewardEntry r : def.rewards()) {
                if (r.skin().isPresent()) recordSkin(sp, r.skin().get());
            }
        }
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            String skin = SkinKitItem.isKit(stack) ? SkinKitItem.skin(stack) : null;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            for (CrateDefs.CrateTier t : shown()) {
                for (String r : CrateDefs.RARITIES) {
                    for (CrateDefs.Entry e : t.pool(r)) {
                        if (e.kind() == CrateDefs.Kind.SKIN && e.id().equals(skin)) record(sp, t.id(), e);
                        else if (e.kind() == CrateDefs.Kind.ITEM && e.id().equals(id.toString())) record(sp, t.id(), e);
                    }
                }
            }
        }
    }
}
