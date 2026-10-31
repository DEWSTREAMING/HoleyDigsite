package com.holeydigsite.event;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.shovel.ShovelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Scooping dirt with the vanilla wooden shovel. */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class ShovelEvents {
    private ShovelEvents() {}

    /**
     * When an EMPTY wooden shovel breaks a scoopable block (grass/dirt), cancel the normal break,
     * remove the block without drops, and mark the shovel full. A full shovel breaks blocks normally.
     * BreakEvent only fires on the server.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getMainHandItem();
        if (!(event.getLevel() instanceof ServerLevel level) || !ShovelHelper.canScoop(tool, event.getState())) {
            return;
        }

        BlockPos pos = event.getPos();
        event.setCanceled(true);
        level.destroyBlock(pos, false, player);
        ShovelHelper.fill(tool, com.holeydigsite.tier.DirtTier.ofScooped(event.getState()));
        com.holeydigsite.crate.CrateFinds.shovelFind(level, pos, player);

        if (ShovelHelper.SCOOP_DURABILITY_COST > 0 && !player.getAbilities().instabuild) {
            tool.hurtAndBreak(ShovelHelper.SCOOP_DURABILITY_COST, player, EquipmentSlot.MAINHAND);
        }
    }
}
