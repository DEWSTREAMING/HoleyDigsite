package com.holeydigsite.compat.jei.display;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.crate.CrateBlock;
import com.holeydigsite.crate.CrateDefs;
import com.holeydigsite.crate.CrateRolls;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import com.holeydigsite.skin.SkinWear;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CrateOddsRow(
        String tierId,
        ItemStack crateStack,
        Component crateName,
        Component oddsSummary,
        int panFindOneIn,
        List<RarityPool> pools
) {
    public record RewardEntry(
            CrateDefs.Kind kind,
            String id,
            int amount,
            int weight,
            ItemStack icon,
            List<ItemStack> variants,
            Component label
    ) {}

    public record RarityPool(
            String rarity,
            double percent,
            ChatFormatting color,
            List<RewardEntry> rewards
    ) {}

    public static List<CrateOddsRow> loadAll() {
        List<CrateOddsRow> rows = new ArrayList<>();
        Map<String, CrateDefs.CrateTier> tiers = CrateDefs.tiers();
        if (tiers == null || tiers.isEmpty()) {
            CrateDefs.reloadBuiltin();
            tiers = CrateDefs.tiers();
        }

        for (String tierId : CrateDefs.BUILTIN_TIERS) {
            CrateDefs.CrateTier def = tiers.get(tierId);
            if (def == null) continue;

            Item item = BuiltInRegistries.ITEM.get(HoleyDigsite.id(tierId));
            ItemStack crateStack = new ItemStack(item != Items.AIR ? item : ModItems.WOODEN_CRATE.get());
            Component name = crateStack.getHoverName();
            Component oddsLine = CrateBlock.oddsLine(def);

            List<RarityPool> pools = new ArrayList<>();
            for (String rarity : CrateDefs.RARITIES) {
                double pct = def.percent(rarity);
                if (pct <= 0) continue;

                List<RewardEntry> rewards = new ArrayList<>();
                for (CrateDefs.Entry e : def.pool(rarity)) {
                    ItemStack icon = createIcon(e);
                    Component label = createLabel(e);
                    rewards.add(new RewardEntry(e.kind(), e.id(), e.amount(), e.weight(), icon, variants(e, icon), label));
                }

                pools.add(new RarityPool(rarity, pct, CrateDefs.rarityFormatting(rarity), List.copyOf(rewards)));
            }

            rows.add(new CrateOddsRow(
                    tierId,
                    crateStack,
                    name,
                    oddsLine,
                    def.panFindOneIn(),
                    List.copyOf(pools)
            ));
        }

        return List.copyOf(rows);
    }

    /** What the slot cycles through: a skin shows its kit for every family it fits (the roll splits evenly between them). */
    private static List<ItemStack> variants(CrateDefs.Entry e, ItemStack icon) {
        if (e.kind() != CrateDefs.Kind.SKIN) return List.of(icon);
        List<ItemStack> out = new ArrayList<>();
        for (String family : SkinDefs.familiesOf(e.id())) {
            out.add(SkinKitItem.of(e.id(), family, SkinWear.FRESH, 1));
        }
        return List.copyOf(out);
    }

    private static ItemStack createIcon(CrateDefs.Entry e) {
        return switch (e.kind()) {
            case SKIN -> SkinKitItem.of(e.id(), SkinDefs.familiesOf(e.id()).get(0), SkinWear.FRESH, 1);
            case TITLE -> {
                ItemStack icon = new ItemStack(Items.NAME_TAG);
                icon.set(DataComponents.CUSTOM_NAME, Component.translatable(LedgerDefs.titleKey(e.id())).withStyle(ChatFormatting.GOLD));
                yield icon;
            }
            case COINS -> {
                List<ItemStack> coins = Selling.coins(e.amount());
                yield coins.isEmpty() ? new ItemStack(ModItems.GOLD_COIN.get()) : coins.get(0).copy();
            }
            case INGOT -> {
                Item ingotItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.id()));
                ItemStack ingot = new ItemStack(ingotItem);
                ingot.set(ModDataComponents.WEIGHT.get(), Math.max(1, e.amount()));
                yield ingot;
            }
            case ITEM -> {
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.id()));
                ItemStack stack = new ItemStack(it);
                stack.setCount(Math.min(Math.max(1, e.amount()), stack.getMaxStackSize()));
                yield stack;
            }
        };
    }

    private static Component createLabel(CrateDefs.Entry e) {
        return switch (e.kind()) {
            case SKIN -> JeiText.t("crate_skin_kit", SkinDefs.displayName(e.id()));
            case TITLE -> Component.translatable(LedgerDefs.titleKey(e.id()));
            case COINS -> Selling.format(e.amount());
            case INGOT -> JeiText.t("crate_ingot", e.amount(), e.id());
            case ITEM -> {
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.id()));
                yield it.getName(new ItemStack(it));
            }
        };
    }
}
