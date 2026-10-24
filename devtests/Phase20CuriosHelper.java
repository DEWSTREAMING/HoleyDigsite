package com.holeydigsite.devtest;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.holeydigsite.curios.CuriosCompat;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.SmithPieces;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * Curios dev-run checks (kept out of Phase20Tests so that class loads without Curios). The GameTest server cannot create a real
 * player with Curios (its sync packet is not registered there), so the slot data is read from the API and the modifier code is
 * driven directly with an event on a plain entity. Returns an error text or null.
 */
public final class Phase20CuriosHelper {
    private Phase20CuriosHelper() {}

    public static String run(GameTestHelper h) {
        var level = h.getLevel();
        var slots = CuriosApi.getPlayerSlots(level);
        if (!slots.containsKey("ring") || !slots.containsKey("necklace")) {
            return "player slots lack ring / necklace: " + slots.keySet();
        }
        if (slots.get("ring").getSize() != 2 || slots.get("necklace").getSize() != 1) {
            return "slot sizes ring " + slots.get("ring").getSize() + " necklace " + slots.get("necklace").getSize();
        }
        ItemStack ring = Phase20Tests.piece(h, ModItems.SMITH_RING.get(), Phase20Tests.data("silvane", 200));
        ItemStack necklace = Phase20Tests.piece(h, ModItems.SMITH_NECKLACE.get(), Phase20Tests.data("silvane", 200, 200));
        if (!ring.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", "ring")))
                || ring.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", "necklace")))) {
            return "ring tag wrong";
        }
        if (!necklace.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", "necklace")))) {
            return "necklace tag wrong";
        }
        LivingEntity e = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        var reg = level.registryAccess();
        double ringArmor = modifierSum(ring, e, "ring", Attributes.ARMOR);
        if (Math.abs(ringArmor - SmithPieces.wearStats(ring, reg, SmithPieces.WearWhere.CURIO).armor()) > 1e-6 || ringArmor <= 0) {
            return "ring modifier armour " + ringArmor;
        }
        double ringTough = modifierSum(ring, e, "ring", Attributes.ARMOR_TOUGHNESS);
        if (Math.abs(ringTough - SmithPieces.wearStats(ring, reg, SmithPieces.WearWhere.CURIO).toughness()) > 1e-6 || ringTough <= 0) {
            return "ring modifier toughness " + ringTough;
        }
        // a necklace in a Curios slot gives only its curio value, not the head value from its head-group component
        double neckArmor = modifierSum(necklace, e, "necklace", Attributes.ARMOR);
        if (Math.abs(neckArmor - SmithPieces.wearStats(necklace, reg, SmithPieces.WearWhere.CURIO).armor()) > 1e-6) {
            return "necklace modifier armour " + neckArmor + " (must be the curio value, counted once)";
        }
        // pieces that are not ours add nothing
        if (modifierSum(new ItemStack(net.minecraft.world.item.Items.STICK), e, "ring", Attributes.ARMOR) != 0) {
            return "a stick got armour";
        }
        return null;
    }

    /** Armour (or toughness) Curios would give for {@code stack} in slot {@code slot}: the full CuriosApi path, which fires our event. */
    private static double modifierSum(ItemStack stack, LivingEntity e, String slot, Holder<Attribute> attr) {
        SlotContext ctx = new SlotContext(slot, e, 0, false, true);
        ResourceLocation id = CuriosApi.getSlotId(ctx);
        Multimap<Holder<Attribute>, AttributeModifier> map = CuriosApi.getAttributeModifiers(ctx, id, stack);
        double sum = 0;
        for (AttributeModifier m : map.get(attr)) {
            sum += m.amount();
        }
        return sum;
    }
}
