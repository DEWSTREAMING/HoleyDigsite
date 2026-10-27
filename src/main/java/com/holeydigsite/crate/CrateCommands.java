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
 * {@code /holeydigsite crate give <player> <tier> [count]} and {@code /holeydigsite crate odds <tier>}.
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
                                                .executes(c -> give(c, 1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(c -> give(c, IntegerArgumentType.getInteger(c, "count")))))))
                        .then(Commands.literal("odds")
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CrateDefs.tiers().keySet(), b))
                                        .executes(CrateCommands::odds)))));
    }

    private static int give(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        String tier = StringArgumentType.getString(c, "tier");
        Item item = BuiltInRegistries.ITEM.get(HoleyDigsite.id(tier));
        if (CrateDefs.tier(tier) == null || item == Items.AIR) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.crate.unknown", tier));
            return 0;
        }
        LedgerManager.giveCountOrDrop(target, new ItemStack(item), count);
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
        if (def.panFindOneIn() > 0) {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.crate.odds.pan", def.panFindOneIn()), false);
        }
        return 1;
    }
}
