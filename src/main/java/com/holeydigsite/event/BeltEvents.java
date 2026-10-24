package com.holeydigsite.event;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.AbstractPadBlock;
import com.holeydigsite.shop.MoneyPanBlock;
import com.holeydigsite.shop.ShopButtonBlock;
import com.holeydigsite.shop.ShopDisplayBlock;
import com.holeydigsite.sluice.SluiceBoxBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sneak-right-click with Dirt Clumps on a Clump Belt or a Sluice Box should put as many in as fit, and sneak-right-click
 * with anything on a shop pad / Money Pan moves everything onto it, and on a Shop Display (creative) sets its
 * item (Phase 8), and sneak-right-click on a Crucible adds all chunks of its metal (Phase 9). Vanilla skips the
 * block's interaction when a player sneaks with an item in hand, so we tell it to use the block anyway in that case.
 * Runs on both sides (the client decides whether to send the click).
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class BeltEvents {
    private BeltEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block instanceof AbstractPadBlock || block instanceof MoneyPanBlock || block instanceof ShopButtonBlock
                || block instanceof ShopDisplayBlock || block instanceof com.holeydigsite.forge.CrucibleBlock
                || block instanceof com.holeydigsite.forge.CraftingForgeBlock || block instanceof com.holeydigsite.smith.SmithingAnvilBlock) {
            event.setUseBlock(TriState.TRUE); // sneak-right-click with anything: pads/pan take everything, display sets its item
            return;
        }
        if (block instanceof ClumpBeltBlock && ClumpBeltBlock.playerInsertable(event.getItemStack())) {
            event.setUseBlock(TriState.TRUE); // Phase 12: sneak puts the whole stack on (any non-block item); Filter Belt: sets the filter
            return;
        }
        if (!com.holeydigsite.tier.DirtTier.isClump(event.getItemStack())) {
            return;
        }
        if (block instanceof SluiceBoxBlock) {
            event.setUseBlock(TriState.TRUE);
        }
    }
}
