package com.holeydigsite.hub;

import com.holeydigsite.compat.teams.TeamsCompat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Op commands for creating a server hub on an existing server:
 * {@code /holeydigsite hub create} and {@code /holeydigsite hub create confirm}.
 */
public final class HubCommands {
    private HubCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite")
                .then(Commands.literal("hub").requires(s -> s.hasPermission(4))
                        .then(Commands.literal("create")
                                .executes(HubCommands::preview)
                                .then(Commands.literal("confirm").executes(HubCommands::confirmCreate)))));
    }

    private static int preview(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!TeamsCompat.skyblockBuilder()) {
            source.sendFailure(Component.literal("Skyblock Builder is required for hub creation."));
            return 0;
        }
        return HubCreator.preview(source);
    }

    private static int confirmCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!TeamsCompat.skyblockBuilder()) {
            source.sendFailure(Component.literal("Skyblock Builder is required for hub creation."));
            return 0;
        }
        return HubCreator.confirm(ctx);
    }
}
