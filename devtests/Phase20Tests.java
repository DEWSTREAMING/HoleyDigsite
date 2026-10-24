package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Phase 20 (sandbox only): wearable jewellery - head slot armour, ring refuses the head, numbers, Curios bridge. */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase20Tests {
    private static void check(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    private static boolean near(double a, double b, double tol) {
        return Math.abs(a - b) <= tol;
    }

    static SmithData data(String metal, int... grams) {
        return new SmithData(java.util.Arrays.stream(grams).mapToObj(g -> new SmithData.Ingot(HoleyDigsite.id(metal), g)).toList(), Optional.empty());
    }

    static SmithData mix(String a, int ga, String b, int gb) {
        return new SmithData(List.of(new SmithData.Ingot(HoleyDigsite.id(a), ga), new SmithData.Ingot(HoleyDigsite.id(b), gb)), Optional.empty());
    }

    static ItemStack piece(GameTestHelper h, Item item, SmithData d) {
        return SmithPieces.create(item, d, h.getLevel().registryAccess());
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    /** The mock player is not ticked by the level, so apply the worn head item's modifiers the way LivingEntity does on its tick. */
    private static void sync(ServerPlayer p) {
        for (var attr : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS)) {
            var inst = p.getAttribute(attr);
            inst.removeModifier(HoleyDigsite.id("jewellery_armor"));
            inst.removeModifier(HoleyDigsite.id("jewellery_toughness"));
        }
        p.getItemBySlot(EquipmentSlot.HEAD).forEachModifier(EquipmentSlot.HEAD, (attr, mod) -> p.getAttribute(attr).addOrReplacePermanentModifier(mod));
    }

    private static SmithPieces.Armor wear(GameTestHelper h, ItemStack s, SmithPieces.WearWhere where) {
        return SmithPieces.wearStats(s, h.getLevel().registryAccess(), where);
    }

    /** Head-slot armour per metal / weight: Folkiron ~ iron helmet, Silvane = netherite helmet, lighter = less. */
    @GameTest(template = "empty")
    public static void necklaceHeadArmourPerMetal(GameTestHelper h) {
        Item n = ModItems.SMITH_NECKLACE.get();
        SmithPieces.Armor f = wear(h, piece(h, n, data("folkiron", 150, 150)), SmithPieces.WearWhere.HEAD);
        check(h, near(f.armor(), 2.0, 0.25) && f.toughness() == 0, "folkiron 2x150 ~ iron helmet 2/0: " + f);
        SmithPieces.Armor s = wear(h, piece(h, n, data("silvane", 200, 200)), SmithPieces.WearWhere.HEAD);
        check(h, near(s.armor(), 3.0, 0.05) && near(s.toughness(), 3.0, 0.05), "silvane 2x200 = netherite helmet 3/3: " + s);
        SmithPieces.Armor g = wear(h, piece(h, n, data("goldchro", 200, 200)), SmithPieces.WearWhere.HEAD);
        check(h, near(g.armor(), 3.0, 0.3) && near(g.toughness(), 2.0, 0.3), "goldchro 2x200 ~ diamond helmet 3/2: " + g);
        SmithPieces.Armor light = wear(h, piece(h, n, data("silvane", 25, 25)), SmithPieces.WearWhere.HEAD);
        check(h, light.armor() < s.armor() && light.armor() >= s.armor() * 0.5 - 1e-9, "lighter necklace gives less but at least half: " + light);
        SmithPieces.Armor over = wear(h, piece(h, n, data("silvane", 900, 900)), SmithPieces.WearWhere.HEAD);
        check(h, near(over.armor(), s.armor(), 1e-9), "weight is capped: " + over);
        SmithPieces.Armor m = wear(h, piece(h, n, mix("folkiron", 200, "silvane", 200)), SmithPieces.WearWhere.HEAD);
        check(h, m.armor() > f.armor() && m.armor() < s.armor(), "mixed metals average: " + m);
        ItemAttributeModifiers mods = piece(h, n, data("silvane", 200, 200)).get(DataComponents.ATTRIBUTE_MODIFIERS);
        check(h, mods != null && mods.modifiers().size() == 2 && !mods.showInTooltip(), "head modifiers on the stack: " + mods);
        h.succeed();
    }

    /** Right-click equips the necklace to the head slot; the player's armour value counts it once; taking it off removes it. */
    @GameTest(template = "empty")
    public static void necklaceEquipsToHeadAndCountsOnce(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack n = piece(h, ModItems.SMITH_NECKLACE.get(), data("silvane", 200, 200));
        check(h, Equipable.get(n) != null && Equipable.get(n).getEquipmentSlot() == EquipmentSlot.HEAD, "necklace is Equipable in the head slot");
        check(h, player(h).getEquipmentSlotForItem(n) == EquipmentSlot.HEAD, "mobs / dispenser see it as head gear");
        p.setItemInHand(InteractionHand.MAIN_HAND, n);
        n.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        check(h, p.getItemBySlot(EquipmentSlot.HEAD).getItem() == n.getItem(), "necklace on the head");
        sync(p);
        check(h, near(p.getAttributeValue(Attributes.ARMOR), 3.0, 0.05), "armour counted once: " + p.getAttributeValue(Attributes.ARMOR));
        check(h, near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 3.0, 0.05), "toughness counted once: " + p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        sync(p);
        check(h, p.getAttributeValue(Attributes.ARMOR) == 0, "armour gone when taken off");
        p.getInventory().add(piece(h, ModItems.SMITH_NECKLACE.get(), data("silvane", 200, 200)));
        check(h, p.getAttributeValue(Attributes.ARMOR) == 0, "in the inventory gives nothing");
        h.succeed();
    }

    /** The ring cannot be worn in the head slot (or any vanilla slot); a ring right-click does nothing. */
    @GameTest(template = "empty")
    public static void ringRefusesTheHead(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack r = piece(h, ModItems.SMITH_RING.get(), data("silvane", 200));
        check(h, Equipable.get(r) == null, "ring is not Equipable");
        check(h, p.getEquipmentSlotForItem(r) == EquipmentSlot.MAINHAND, "ring has no armour slot");
        check(h, !r.canEquip(EquipmentSlot.HEAD, p), "ring cannot be put in the head slot");
        p.setItemInHand(InteractionHand.MAIN_HAND, r);
        r.getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        check(h, p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "right-click does not equip a ring");
        check(h, p.getAttributeValue(Attributes.ARMOR) == 0, "no armour from a ring in hand");
        check(h, r.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().isEmpty(), "ring has no head modifiers");
        h.succeed();
    }

    /** Curios set targets: 2 rings + necklace of the best metals add +3-4 armour and +1-2 toughness, never a whole extra netherite piece. */
    @GameTest(template = "empty")
    public static void curiosSetNumbers(GameTestHelper h) {
        SmithPieces.Armor ring = wear(h, piece(h, ModItems.SMITH_RING.get(), data("silvane", 200)), SmithPieces.WearWhere.CURIO);
        SmithPieces.Armor neck = wear(h, piece(h, ModItems.SMITH_NECKLACE.get(), data("silvane", 200, 200)), SmithPieces.WearWhere.CURIO);
        double armor = 2 * ring.armor() + neck.armor(), tough = 2 * ring.toughness() + neck.toughness();
        check(h, armor >= 3.0 && armor <= 4.0, "set armour +3-4: " + armor);
        check(h, tough >= 1.0 && tough <= 2.0, "set toughness +1-2: " + tough);
        check(h, neck.armor() < 3.0, "curio necklace below helmet level");
        SmithPieces.Armor fr = wear(h, piece(h, ModItems.SMITH_RING.get(), data("folkiron", 150)), SmithPieces.WearWhere.CURIO);
        check(h, fr.armor() > 0 && fr.armor() < ring.armor() && fr.toughness() == 0, "folkiron ring small: " + fr);
        h.succeed();
    }

    /** Pieces from before Phase 20 (no head modifiers) get them when the necklace is in a player's inventory. */
    @GameTest(template = "empty")
    public static void oldNecklaceGetsItsArmour(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack n = new ItemStack(ModItems.SMITH_NECKLACE.get());
        n.set(com.holeydigsite.registry.ModDataComponents.SMITH.get(), data("silvane", 200, 200));
        check(h, n.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().isEmpty(), "old necklace has no modifiers");
        n.getItem().inventoryTick(n, h.getLevel(), p, 0, false);
        check(h, n.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size() == 2, "modifiers added on the first tick");
        p.setItemSlot(EquipmentSlot.HEAD, n);
        sync(p);
        check(h, near(p.getAttributeValue(Attributes.ARMOR), 3.0, 0.05), "old necklace now armours");
        h.succeed();
    }

    /** Tooltips: armour line + where it can be worn; the ring says "Wear with Curios" when Curios is missing. */
    @GameTest(template = "empty")
    public static void tooltipLines(GameTestHelper h) {
        Item.TooltipContext ctx = Item.TooltipContext.of(h.getLevel());
        boolean curios = ModList.get().isLoaded("curios");
        String nt = piece(h, ModItems.SMITH_NECKLACE.get(), data("silvane", 200, 200)).getTooltipLines(ctx, null, TooltipFlag.NORMAL).toString();
        check(h, nt.contains("tooltip.holeydigsite.wear.head_t") && nt.contains("where_necklace"), "necklace lines: " + nt);
        String rt = piece(h, ModItems.SMITH_RING.get(), data("silvane", 200)).getTooltipLines(ctx, null, TooltipFlag.NORMAL).toString();
        check(h, rt.contains("tooltip.holeydigsite.wear.curio_t") && rt.contains(curios ? "where_ring" : "need_curios"), "ring lines: " + rt);
        h.succeed();
    }

    /** With Curios in the dev run: real ring / necklace slots give armour; otherwise just passes. */
    @GameTest(template = "empty")
    public static void curiosSlotsGiveArmour(GameTestHelper h) {
        if (ModList.get().isLoaded("curios")) {
            String err = Phase20CuriosHelper.run(h);
            check(h, err == null, err);
        }
        h.succeed();
    }
}
