package com.holeydigsite.crate;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.ledger.LedgerNetwork;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import com.holeydigsite.skin.SkinWear;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side crate rolls: a rarity with the tier's odds, then an entry from that rarity's pool (by weight). A rarity with an
 * empty pool falls back to the next lower rarity that has entries. Skins also roll a wear and the machine family of the kit.
 */
public final class CrateRolls {
    private CrateRolls() {}

    /**
     * What was rolled: the rarity the odds picked, the rarity whose pool was used, the entry and, for skins, the wear and the
     * machine family of the kit (null family: rolled before kits existed, given as a drill kit).
     */
    public record Roll(String tier, String rolledRarity, String rarity, CrateDefs.Entry entry, @Nullable String wear,
                       @Nullable String family) {
        public Roll(String tier, String rolledRarity, String rarity, CrateDefs.Entry entry, @Nullable String wear) {
            this(tier, rolledRarity, rarity, entry, wear, null);
        }

        public String kitFamily() {
            return family != null && SkinDefs.isFamily(family) ? family : SkinDefs.familiesOf(entry.id()).get(0);
        }
    }

    /** KIT: a skin, always given as a Skin Kit. DUPLICATE: a title already owned, given as Skin Scrap. */
    public enum Result { NEW, KIT, DUPLICATE, ITEM }

    /** What the player got: {@code scrap} is the Skin Scrap count for a duplicate title, {@code icon} what the spin lands on. */
    public record Outcome(Roll roll, Result result, int scrap, ItemStack icon, Component line) {}

    public static String rollRarity(CrateDefs.CrateTier tier, RandomSource random) {
        double total = tier.totalOdds();
        if (total <= 0) return CrateDefs.RARITIES.get(0);
        double r = random.nextDouble() * total;
        for (String rarity : CrateDefs.RARITIES) {
            r -= Math.max(0, tier.odds().getOrDefault(rarity, 0.0));
            if (r < 0) return rarity;
        }
        return CrateDefs.RARITIES.get(0);
    }

    /** The rarity whose pool is used: this one, or the next lower one with entries; null if the crate is empty. */
    @Nullable
    public static String poolRarity(CrateDefs.CrateTier tier, String rarity) {
        int i = CrateDefs.RARITIES.indexOf(rarity);
        for (int j = Math.max(0, i); j >= 0; j--) {
            if (!tier.pool(CrateDefs.RARITIES.get(j)).isEmpty()) return CrateDefs.RARITIES.get(j);
        }
        for (int j = Math.max(0, i) + 1; j < CrateDefs.RARITIES.size(); j++) {
            if (!tier.pool(CrateDefs.RARITIES.get(j)).isEmpty()) return CrateDefs.RARITIES.get(j);
        }
        return null;
    }

    @Nullable
    public static Roll roll(CrateDefs.CrateTier tier, RandomSource random) {
        String rolled = rollRarity(tier, random);
        String rarity = poolRarity(tier, rolled);
        if (rarity == null) return null;
        List<CrateDefs.Entry> pool = tier.pool(rarity);
        int total = 0;
        for (CrateDefs.Entry e : pool) total += e.weight();
        int r = random.nextInt(total);
        CrateDefs.Entry pick = pool.get(pool.size() - 1);
        for (CrateDefs.Entry e : pool) {
            r -= e.weight();
            if (r < 0) {
                pick = e;
                break;
            }
        }
        boolean skin = pick.kind() == CrateDefs.Kind.SKIN;
        String wear = skin ? SkinWear.roll(random) : null;
        String family = skin ? SkinKitItem.rollFamily(pick.id(), random) : null;
        return new Roll(tier.id(), rolled, rarity, pick, wear, family);
    }

    /** Skin Scrap for a duplicate title or a scrapped kit: the rarity amount, for skins times the wear multiplier, rounded, at least 1. */
    public static int scrapFor(String rarity, @Nullable String wear) {
        int base = CrateDefs.rules().scrap().getOrDefault(rarity, 1);
        if (wear == null) return Math.max(1, base);
        return (int) Math.max(1, Math.round(base * SkinWear.scrapMultiplier(wear)));
    }

    public static Outcome grant(ServerPlayer sp, Roll roll) {
        LedgerData data = LedgerData.get(sp);
        CrateDefs.Entry e = roll.entry();
        CrateCollections.record(sp, roll.tier(), e);
        Outcome out;
        switch (e.kind()) {
            case SKIN -> {
                String wear = SkinWear.normalize(roll.wear());
                if (!SkinDefs.hasSkin(e.id())) {
                    // a skin a datapack removed: nothing to make a kit of, so it counts as scrap
                    ItemStack icon = new ItemStack(ModItems.SKIN_SCRAP.get());
                    int n = scrapFor(roll.rarity(), wear);
                    out = scrap(sp, roll, icon, Component.translatable("crate.holeydigsite.result.duplicate", Component.literal(e.id()), n), n);
                    break;
                }
                ItemStack kit = SkinKitItem.of(e.id(), roll.kitFamily(), wear, 1);
                LedgerManager.giveOrDrop(sp, kit.copy());
                out = new Outcome(roll, Result.KIT, 0, kit, Component.translatable("crate.holeydigsite.result.skin_kit",
                        kit.getHoverName(), SkinWear.displayName(wear)));
            }
            case TITLE -> {
                ItemStack icon = new ItemStack(Items.NAME_TAG);
                Component name = Component.translatable(LedgerDefs.titleKey(e.id())).withStyle(ChatFormatting.GOLD);
                icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
                if (LedgerDefs.hasTitle(e.id()) && data.unlockTitle(e.id())) {
                    out = new Outcome(roll, Result.NEW, 0, icon, Component.translatable("crate.holeydigsite.result.title_new", name));
                } else {
                    int n = scrapFor(roll.rarity(), null);
                    out = scrap(sp, roll, icon, Component.translatable("crate.holeydigsite.result.title_duplicate", name, n), n);
                }
            }
            case COINS -> {
                List<ItemStack> coins = Selling.coins(e.amount());
                for (ItemStack c : coins) LedgerManager.giveOrDrop(sp, c);
                ItemStack icon = coins.isEmpty() ? new ItemStack(ModItems.GOLD_COIN.get()) : coins.get(0).copy();
                out = new Outcome(roll, Result.ITEM, 0, icon, Component.translatable("crate.holeydigsite.result.coins", Selling.format(e.amount())));
            }
            case INGOT -> {
                ItemStack ingot = new ItemStack(item(e.id()));
                ingot.set(ModDataComponents.WEIGHT.get(), Math.max(1, e.amount()));
                LedgerManager.giveOrDrop(sp, ingot.copy());
                out = new Outcome(roll, Result.ITEM, 0, ingot, Component.translatable("crate.holeydigsite.result.item", ingot.getHoverName(), 1));
            }
            default -> {
                ItemStack stack = new ItemStack(item(e.id()));
                int count = Math.max(1, e.amount());
                LedgerManager.giveCountOrDrop(sp, stack, count);
                out = new Outcome(roll, Result.ITEM, 0, stack.copyWithCount(Math.min(count, stack.getMaxStackSize())),
                        Component.translatable("crate.holeydigsite.result.item", stack.getHoverName(), count));
            }
        }
        LedgerNetwork.syncToClient(sp);
        return out;
    }

    private static Outcome scrap(ServerPlayer sp, Roll roll, ItemStack icon, Component line, int n) {
        LedgerManager.giveCountOrDrop(sp, new ItemStack(ModItems.SKIN_SCRAP.get()), n);
        return new Outcome(roll, Result.DUPLICATE, n, icon, line);
    }

    private static Item item(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        Item item = loc == null ? Items.AIR : BuiltInRegistries.ITEM.get(loc);
        return item == Items.AIR ? ModItems.SKIN_SCRAP.get() : item;
    }

    /** The decoration strip: icons, their rarities, where the result sits and where in its slot the marker stops. */
    public record Spin(List<ItemStack> icons, List<String> rarities, int resultIndex, float stopFrac) {}

    /** Rarities this tier can actually give (the odds pick one, an empty pool falls back to another). */
    public static List<String> givenRarities(CrateDefs.CrateTier tier) {
        List<String> out = new ArrayList<>();
        for (String r : CrateDefs.RARITIES) {
            if (tier.odds().getOrDefault(r, 0.0) <= 0) continue;
            String used = poolRarity(tier, r);
            if (used != null && !out.contains(used)) out.add(used);
        }
        return out;
    }

    /**
     * Builds the strip for a roll. The server already decided the result; the rest is decoration drawn from the crate's own
     * odds. Sometimes a rarer item is placed right after the result (the marker stops just short of it) or right before it
     * (the marker rolls slowly past it). Rarities the tier cannot give never appear.
     */
    public static Spin spin(CrateDefs.CrateTier tier, Roll roll, ItemStack resultIcon, RandomSource random) {
        List<ItemStack> icons = new ArrayList<>();
        List<String> rarities = new ArrayList<>();
        int index = CrateSpin.RESULT_INDEX;
        for (int i = 0; i < CrateSpin.STRIP_SIZE; i++) {
            if (i == index) {
                icons.add(resultIcon.copy());
                rarities.add(roll.rarity());
                continue;
            }
            Roll r = roll(tier, random);
            if (r == null) {
                icons.add(resultIcon.copy());
                rarities.add(roll.rarity());
            } else {
                icons.add(icon(r.entry(), r.wear(), r.family()));
                rarities.add(r.rarity());
            }
        }
        float stop = CrateSpin.STOP_MIN + random.nextFloat() * (CrateSpin.STOP_MAX - CrateSpin.STOP_MIN);
        List<String> rarer = new ArrayList<>();
        for (String r : givenRarities(tier)) {
            if (CrateDefs.RARITIES.indexOf(r) > CrateDefs.RARITIES.indexOf(roll.rarity())) rarer.add(r);
        }
        if (!rarer.isEmpty() && random.nextDouble() < CrateSpin.NEAR_MISS_CHANCE) {
            String bigger = rarer.get(random.nextInt(rarer.size()));
            List<CrateDefs.Entry> pool = tier.pool(bigger);
            CrateDefs.Entry e = pool.get(random.nextInt(pool.size()));
            boolean after = random.nextBoolean();
            int slot = after ? index + 1 : index - 1;
            boolean skin = e.kind() == CrateDefs.Kind.SKIN;
            icons.set(slot, icon(e, skin ? SkinWear.roll(random) : null, skin ? SkinKitItem.rollFamily(e.id(), random) : null));
            rarities.set(slot, bigger);
            float edge = CrateSpin.EDGE_MIN + random.nextFloat() * (CrateSpin.EDGE_MAX - CrateSpin.EDGE_MIN);
            stop = after ? 1 - edge : edge;
        }
        return new Spin(icons, rarities, index, stop);
    }

    /** What the spin shows for a roll before the reward is given (same icon the grant reports). */
    public static ItemStack previewIcon(Roll roll) {
        CrateDefs.Entry e = roll.entry();
        switch (e.kind()) {
            case SKIN -> {
                return SkinDefs.hasSkin(e.id()) ? icon(e, SkinWear.normalize(roll.wear()), roll.kitFamily())
                        : new ItemStack(ModItems.SKIN_SCRAP.get());
            }
            case TITLE -> {
                ItemStack icon = new ItemStack(Items.NAME_TAG);
                icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        Component.translatable(LedgerDefs.titleKey(e.id())).withStyle(ChatFormatting.GOLD));
                return icon;
            }
            case COINS -> {
                List<ItemStack> coins = Selling.coins(e.amount());
                return coins.isEmpty() ? new ItemStack(ModItems.GOLD_COIN.get()) : coins.get(0).copy();
            }
            case INGOT -> {
                ItemStack ingot = new ItemStack(item(e.id()));
                ingot.set(ModDataComponents.WEIGHT.get(), Math.max(1, e.amount()));
                return ingot;
            }
            default -> {
                ItemStack stack = new ItemStack(item(e.id()));
                return stack.copyWithCount(Math.min(Math.max(1, e.amount()), stack.getMaxStackSize()));
            }
        }
    }

    /** The icon of an entry without giving it; a skin shows as its kit (a drill kit when no family is given). */
    public static ItemStack icon(CrateDefs.Entry e, @Nullable String wear, @Nullable String family) {
        return switch (e.kind()) {
            case SKIN -> SkinKitItem.of(e.id(), family != null ? family : SkinDefs.familiesOf(e.id()).get(0), wear, 1);
            case TITLE -> new ItemStack(Items.NAME_TAG);
            case COINS -> new ItemStack(ModItems.GOLD_COIN.get());
            default -> new ItemStack(item(e.id()));
        };
    }
}
