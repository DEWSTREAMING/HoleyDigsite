package com.holeydigsite.event;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Punching (left-clicking) a pan picks it up instantly. */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class PanEvents {
    private PanEvents() {}

    /**
     * LeftClickBlock fires on both sides when a player starts hitting a block - in survival AND creative,
     * before any break progress. Cancelling on the client stops the normal mining/creative-break prediction;
     * the server does the actual pickup. Works with any held item and needs no tool.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PanBlock pan)) {
            return;
        }

        Player player = event.getEntity();
        if (player.isSpectator() || !level.mayInteract(player, pos) || !player.mayBuild()) {
            return; // adventure mode / spawn protection: behave like vanilla
        }

        event.setCanceled(true);
        if (!level.isClientSide) {
            pan.pickUp(state, level, pos, player);
        }
    }
}
