package com.holeydigsite.contract;

import com.holeydigsite.site.Boards;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /holeydigsite contract list [player]}, {@code contract reroll <player>}, {@code contract complete <player> <slot>},
 * {@code contract day <player>}, and {@code /holeydigsite site board} (places the Contract Board at its template spot).
 */
public final class ContractCommands {
    private ContractCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("contract")
                        .then(Commands.literal("list")
                                .executes(c -> list(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> list(c, EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("reroll")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> reroll(c, EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("complete")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, ContractDefs.MAX_SLOTS))
                                                .executes(c -> complete(c, EntityArgument.getPlayer(c, "player"),
                                                        IntegerArgumentType.getInteger(c, "slot"))))))
                        .then(Commands.literal("day")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> day(c, EntityArgument.getPlayer(c, "player"))))))
                .then(Commands.literal("site")
                        .then(Commands.literal("board").executes(ContractCommands::board))));
    }

    private static int list(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        ContractManager.refresh(p);
        ContractData d = ContractManager.data(p);
        long day = ContractManager.today(p.getServer());
        int days = ContractDefs.rules().days();
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.contract.list.header", p.getDisplayName(), day,
                d.completed()), false);
        for (int i = 0; i < d.size(); i++) {
            Contract k = d.get(i);
            int slot = i + 1;
            if (k == null) {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.contract.list.empty", slot), false);
            } else if (k.isDone()) {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.contract.list.done", slot), false);
            } else {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.contract.list.slot", slot,
                        ContractText.want(k), ContractText.progress(k), ContractText.pays(k, 40)
                                .append(" | ").append(ContractText.daysLeft(k, day, days))), false);
            }
        }
        return d.openCount();
    }

    private static int reroll(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        ContractManager.rerollAll(p);
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.contract.reroll", p.getDisplayName()), true);
        return 1;
    }

    private static int complete(CommandContext<CommandSourceStack> c, ServerPlayer p, int slot) {
        ContractManager.refresh(p);
        if (!ContractManager.forceComplete(p, slot - 1)) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.contract.complete.none", p.getDisplayName(), slot));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.contract.complete", slot, p.getDisplayName()), true);
        return 1;
    }

    private static int day(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        ContractManager.nextDay(p);
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.contract.day", p.getDisplayName()), true);
        return 1;
    }

    private static int board(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        SiteData data = SiteData.get(level);
        List<Boards.Spot> all = new ArrayList<>();
        for (SiteData.Site site : data.all()) {
            SiteDef def = SiteDefs.get(site.id());
            if (def != null) {
                all.addAll(Boards.ensure(level, site, def));
            }
        }
        if (all.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.site.board.none"));
            return 0;
        }
        for (Boards.Spot s : all) {
            String key = switch (s.state()) {
                case PLACED -> "command.holeydigsite.site.board.placed";
                case THERE -> "command.holeydigsite.site.board.there";
                case BLOCKED -> "command.holeydigsite.site.board.blocked";
            };
            Component at = Component.literal(s.pos().toShortString());
            if (s.state() == Boards.State.BLOCKED) {
                c.getSource().sendFailure(Component.translatable(key, at));
            } else {
                c.getSource().sendSuccess(() -> Component.translatable(key, at), true);
            }
        }
        return all.size();
    }
}
