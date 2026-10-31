package com.holeydigsite.crate;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.ledger.LedgerManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code /holeydigsite crate give <player> <id> [count] [locked]}, {@code crate odds <id>} and {@code crate switch <player> [on|off]}.
 */
public final class CrateCommands {
    private CrateCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("crate")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("tier", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(CrateDefs.tiers().keySet(), b))
                                                .executes(c -> give(c, 1, false))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(c -> give(c, IntegerArgumentType.getInteger(c, "count"), false))
                                                        .then(Commands.literal("locked")
                                                                .executes(c -> give(c, IntegerArgumentType.getInteger(c, "count"), true)))))))
                        .then(Commands.literal("odds")
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CrateDefs.tiers().keySet(), b))
                                        .executes(CrateCommands::odds)))
                        .then(Commands.literal("switch")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> switchTo(c, null))
                                        .then(Commands.literal("on").executes(c -> switchTo(c, true)))
                                        .then(Commands.literal("off").executes(c -> switchTo(c, false)))))));
    }

    private static int switchTo(CommandContext<CommandSourceStack> c, Boolean on) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        if (on == null) {
            boolean now = com.holeydigsite.ledger.LedgerData.get(target).cratesOn();
            c.getSource().sendSuccess(() -> Component.translatable(now ? "command.holeydigsite.crate.switch.is_on" : "command.holeydigsite.crate.switch.is_off",
                    target.getDisplayName()), false);
            return now ? 1 : 0;
        }
        CrateSwitchBlock.set(target, on);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "command.holeydigsite.crate.switch.set_on" : "command.holeydigsite.crate.switch.set_off",
                target.getDisplayName()), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> c, int count, boolean locked) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        String tier = StringArgumentType.getString(c, "tier");
        Item item = BuiltInRegistries.ITEM.get(HoleyDigsite.id(tier));
        if (CrateDefs.tier(tier) == null || item == Items.AIR) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.crate.unknown", tier));
            return 0;
        }
        LedgerManager.giveCountOrDrop(target, CrateBlock.stack(item, locked), count);
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.crate.given", count,
                Component.translatable(item.getDescriptionId()), target.getDisplayName()), true);
        return count;
    }

    private static int odds(CommandContext<CommandSourceStack> c) {
        String tier = StringArgumentType.getString(c, "tier");
        CrateDefs.CrateTier def = CrateDefs.tier(tier);
        if (def == null) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.crate.unknown", tier));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.crate.odds", tier, CrateBlock.oddsLine(def)), false);
        for (String r : CrateDefs.RARITIES) {
            String used = CrateRolls.poolRarity(def, r);
            int n = def.pool(r).size();
            if (def.percent(r) <= 0) continue;
            if (n == 0 && used != null) {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.crate.odds.fallback",
                        Component.translatable("rarity.holeydigsite." + r), Component.translatable("rarity.holeydigsite." + used)), false);
            } else {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.crate.odds.pool",
                        Component.translatable("rarity.holeydigsite." + r), n), false);
            }
        }
        if (CrateBlock.hasSkins(def)) {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.crate.odds.skins"), false);
        }
        for (String source : CrateDefs.FIND_SOURCES) {
            if (def.findOneIn(source) > 0) {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.crate.odds.find." + source, def.findOneIn(source)), false);
            }
        }
        for (String r : CrateDefs.RARITIES) {
            if (def.percent(r) <= 0) continue;
            for (CrateDefs.Entry e : def.pool(r)) {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.crate.odds.entry",
                        Component.translatable("rarity.holeydigsite." + r).withStyle(CrateDefs.rarityFormatting(r)),
                        CrateCollections.entryName(e)), false);
            }
        }
        return 1;
    }
}
