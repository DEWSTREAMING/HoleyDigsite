package com.holeydigsite.command;

import com.holeydigsite.economy.EconomyCommands;
import com.holeydigsite.ledger.LedgerCommands;
import com.holeydigsite.plot.PlotCommands;
import com.holeydigsite.site.SiteCommands;
import com.holeydigsite.site.WorldCommands;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Phase 21c: Unified registration for operator commands under {@code /holeydigsite} (permission level 2).
 */
public final class ModCommands {
    private ModCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SiteCommands.register(dispatcher);
        LedgerCommands.register(dispatcher);
        EconomyCommands.register(dispatcher);
        PlotCommands.register(dispatcher);
        WorldCommands.register(dispatcher);
        com.holeydigsite.shop.custom.DisplayCommands.register(dispatcher);
    }
}
