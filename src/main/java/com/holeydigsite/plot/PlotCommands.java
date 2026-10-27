package com.holeydigsite.plot;

import com.holeydigsite.ledger.ModTriggers;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Plot inspection and ownership commands under {@code /holeydigsite plot ...}.
 */
public final class PlotCommands {
    private PlotCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("plot")
                        .then(Commands.literal("list").executes(PlotCommands::list))
                        .then(Commands.literal("own")
                                .then(Commands.argument("plot", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(plotSuggestions(c.getSource().getLevel()), b))
                                        .executes(c -> own(c, null))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> own(c, EntityArgument.getPlayer(c, "player"))))))
                        .then(Commands.literal("unown")
                                .then(Commands.argument("plot", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(plotSuggestions(c.getSource().getLevel()), b))
                                        .executes(PlotCommands::unown)))));
    }

    public static Set<String> plotSuggestions(ServerLevel level) {
        Set<String> set = new LinkedHashSet<>(List.of("deep_dig", "gravel_flats", "sand_dunes"));
        for (SiteDef d : SiteDefs.all()) {
            if (d.kind() == SiteDef.Kind.PLOT && d.plot() != null) {
                set.add(d.plot());
            }
        }
        PlotData data = PlotData.get(level);
        for (PlotData.Plot p : data.all()) {
            set.add(p.id());
        }
        return set;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        Collection<PlotData.Plot> plots = PlotData.get(level).all();
        if (plots.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.plot.none"), false);
            return 0;
        }
        List<String> items = new ArrayList<>();
        for (PlotData.Plot p : plots) {
            if (p.owned()) {
                String owner = p.owner().isEmpty() ? "unknown" : p.owner();
                items.add(p.id() + " (owned by " + owner + ")");
            } else {
                items.add(p.id() + " (unowned)");
            }
        }
        String list = String.join(", ", items);
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.plot.list", list), false);
        return plots.size();
    }

    private static int own(CommandContext<CommandSourceStack> c, ServerPlayer targetPlayer) {
        String plotId = StringArgumentType.getString(c, "plot");
        ServerLevel level = c.getSource().getLevel();
        PlotData data = PlotData.get(level);
        List<PlotData.Plot> matches = new ArrayList<>();
        for (PlotData.Plot p : data.all()) {
            if (p.id().equalsIgnoreCase(plotId)) {
                matches.add(p);
            }
        }
        if (matches.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.plot.not_found", plotId));
            return 0;
        }

        ServerPlayer ownerPlayer = targetPlayer;
        if (ownerPlayer == null && c.getSource().getEntity() instanceof ServerPlayer sp) {
            ownerPlayer = sp;
        }
        String ownerName = ownerPlayer != null ? ownerPlayer.getGameProfile().getName() : c.getSource().getTextName();

        for (PlotData.Plot p : matches) {
            data.setOwned(p.id(), p.board(), true, ownerName, ownerPlayer != null ? ownerPlayer.getUUID() : null);
            BlockState state = level.getBlockState(p.board());
            if (state.is(ModBlocks.PLOT_BOARD.get())) {
                level.setBlock(p.board(), state.setValue(PlotBoardBlock.OWNED, true), Block.UPDATE_CLIENTS);
            }
        }

        if (ownerPlayer != null) {
            int ownedCount = (int) data.all().stream().filter(PlotData.Plot::owned).map(PlotData.Plot::id).distinct().count();
            ModTriggers.PLOT_BOUGHT.get().trigger(ownerPlayer, plotId, ownedCount);
        }

        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.plot.owned", plotId, ownerName), true);
        return matches.size();
    }

    private static int unown(CommandContext<CommandSourceStack> c) {
        String plotId = StringArgumentType.getString(c, "plot");
        ServerLevel level = c.getSource().getLevel();
        PlotData data = PlotData.get(level);
        List<PlotData.Plot> matches = new ArrayList<>();
        for (PlotData.Plot p : data.all()) {
            if (p.id().equalsIgnoreCase(plotId)) {
                matches.add(p);
            }
        }
        if (matches.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.plot.not_found", plotId));
            return 0;
        }

        for (PlotData.Plot p : matches) {
            data.setOwned(p.id(), p.board(), false, "");
            BlockState state = level.getBlockState(p.board());
            if (state.is(ModBlocks.PLOT_BOARD.get())) {
                level.setBlock(p.board(), state.setValue(PlotBoardBlock.OWNED, false), Block.UPDATE_CLIENTS);
            }
        }

        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.plot.unowned", plotId), true);
        return matches.size();
    }
}
