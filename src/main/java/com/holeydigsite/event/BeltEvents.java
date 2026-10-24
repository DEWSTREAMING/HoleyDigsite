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
 * Sneak-right-clicking with Dirt Clumps on a Clump Belt or Sluice Box inserts as many as fit, while sneak-right-clicking
 * with anything on a shop pad or Money Pan moves everything onto it. On a Shop Display it sets its item,
 * and on a Crucible it adds all chunks of its metal. Vanilla skips block interaction when a player sneaks with
 * an item in hand, so this forces block usage in that case. Runs on both sides.
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
            event.setUseBlock(TriState.TRUE); // pads/pan take everything, display sets its item
            return;
        }
        if (block instanceof ClumpBeltBlock && ClumpBeltBlock.playerInsertable(event.getItemStack())) {
            event.setUseBlock(TriState.TRUE); // sneak puts the whole stack on, or sets the filter for a Filter Belt
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
