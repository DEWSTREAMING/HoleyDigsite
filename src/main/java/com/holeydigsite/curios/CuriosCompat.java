package com.holeydigsite.curios;

import com.holeydigsite.smith.SmithPieces;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * Curios API bridge (Phase 20). This class is only loaded when the Curios mod is present ({@link com.holeydigsite.smith.CuriosLoaded});
 * nothing else in the mod may refer to it directly. Worn smithed rings and necklaces give their armour and toughness through
 * Curios attribute modifiers, computed from the metals and weights they remember.
 */
public final class CuriosCompat {
    private CuriosCompat() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(CuriosCompat::onModifiers);
    }

    /** Adds the wear armour of a smithed piece in a Curios slot. */
    public static void onModifiers(CurioAttributeModifierEvent event) {
        var entity = event.getSlotContext().entity();
        if (entity == null || entity.level() == null) {
            return;
        }
        SmithPieces.Armor a = SmithPieces.wearStats(event.getItemStack(), entity.level().registryAccess(), SmithPieces.WearWhere.CURIO);
        if (!a.isEmpty() && entity instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.holeydigsite.ledger.ModTriggers.WEARING.get().trigger(sp, "curios");
        }
        if (a.armor() > 0) {
            event.addModifier(Attributes.ARMOR, new AttributeModifier(event.getId(), a.armor(), AttributeModifier.Operation.ADD_VALUE));
        }
        if (a.toughness() > 0) {
            event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(event.getId(), a.toughness(), AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
