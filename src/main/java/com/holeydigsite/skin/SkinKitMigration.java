package com.holeydigsite.skin;

import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Old worlds: skins used to be unlocks on the player that covered every machine family. On the first login with kits, each
 * unlocked skin becomes one kit per family it fits (drill and sluice), at the wear the unlock had, and the unlock list is
 * cleared. The marker stops it from ever running twice. Skins already on machines are not touched.
 */
public final class SkinKitMigration {
    private SkinKitMigration() {}

    /** Returns how many kits were given (0 if there was nothing to do or it already ran). */
    public static int run(ServerPlayer sp) {
        LedgerData data = LedgerData.get(sp);
        if (data == null || data.skinsMigrated()) {
            return 0;
        }
        List<String> skins = new ArrayList<>(data.unlockedSkins());
        int kits = 0;
        for (String skin : skins) {
            if (skin == null || skin.isEmpty() || SkinDefs.DEFAULT_ID.equals(skin)) continue;
            String wear = data.skinWear(skin);
            for (String family : SkinDefs.familiesOf(skin)) {
                LedgerManager.giveOrDrop(sp, SkinKitItem.of(skin, family, wear, 1));
                kits++;
            }
        }
        data.clearOldSkins();
        data.setSkinsMigrated(true);
        if (kits > 0) {
            sp.sendSystemMessage(Component.translatable("message.holeydigsite.skin.migrated", skins.size(), kits)
                    .withStyle(ChatFormatting.GOLD));
        }
        return kits;
    }
}
