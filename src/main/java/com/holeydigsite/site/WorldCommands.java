package com.holeydigsite.site;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * World admin commands under {@code /holeydigsite world ...}.
 */
public final class WorldCommands {
    private WorldCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("world")
                        .then(Commands.literal("version").executes(WorldCommands::version))));
    }

    private static int version(CommandContext<CommandSourceStack> c) {
        WorldVersion wv = WorldVersion.get(c.getSource().getServer());
        int v = wv.version();
        List<String> applied = wv.applied();
        String steps = applied.isEmpty() ? "none" : String.join(", ", applied);
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.world.version", v, WorldVersion.CURRENT, steps), false);
        return v;
    }
}
