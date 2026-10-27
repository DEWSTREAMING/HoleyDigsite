package com.holeydigsite.skin;

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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin commands for Skin Kits: {@code /holeydigsite skin give <player> <skin|all> <family|all> [wear] [count]} and
 * {@code /holeydigsite skin list [player]} (the kits a player carries).
 */
public final class SkinCommands {
    private SkinCommands() {}

    private static List<String> withAll(Iterable<String> ids) {
        List<String> list = new ArrayList<>();
        list.add("all");
        ids.forEach(list::add);
        return list;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("skin")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("skin", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(withAll(SkinDefs.skins().keySet()), b))
                                                .then(Commands.argument("family", StringArgumentType.word())
                                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(withAll(SkinDefs.FAMILIES), b))
                                                        .executes(ctx -> give(ctx, SkinWear.FRESH, 1))
                                                        .then(Commands.argument("wear", StringArgumentType.word())
                                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                                        SkinWear.wears().stream().map(w -> w.id()).toList(), b))
                                                                .executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "wear"), 1))
                                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                                        .executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "wear"),
                                                                                IntegerArgumentType.getInteger(ctx, "count")))))))))
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, String wear, int count) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String skinArg = StringArgumentType.getString(ctx, "skin");
        String familyArg = StringArgumentType.getString(ctx, "family");
        if (!SkinWear.exists(wear)) {
            ctx.getSource().sendFailure(Component.translatable("command.holeydigsite.skin.bad_wear", wear));
            return 0;
        }
        boolean allSkins = "all".equalsIgnoreCase(skinArg);
        boolean allFamilies = "all".equalsIgnoreCase(familyArg);
        if (!allSkins && !SkinDefs.hasSkin(skinArg)) {
            ctx.getSource().sendFailure(Component.translatable("command.holeydigsite.skin.not_found", skinArg));
            return 0;
        }
        if (!allFamilies && !SkinDefs.isFamily(familyArg)) {
            ctx.getSource().sendFailure(Component.translatable("command.holeydigsite.skin.bad_family", familyArg));
            return 0;
        }
        List<String> skins = allSkins ? new ArrayList<>(SkinDefs.skins().keySet()) : List.of(skinArg);
        int given = 0;
        for (String skin : skins) {
            for (String family : SkinDefs.familiesOf(skin)) {
                if (!allFamilies && !family.equals(familyArg)) continue;
                LedgerManager.giveCountOrDrop(target, SkinKitItem.of(skin, family, wear, 1), count);
                given += count;
            }
        }
        if (given == 0) {
            ctx.getSource().sendFailure(Component.translatable("command.holeydigsite.skin.no_fit", skinArg, familyArg));
            return 0;
        }
        int total = given;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.skin.given", total, target.getDisplayName()), true);
        return total;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, ItemStack> firsts = new LinkedHashMap<>();
        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            ItemStack s = target.getInventory().getItem(i);
            if (!SkinKitItem.isKit(s)) continue;
            String key = SkinKitItem.skin(s) + "|" + SkinKitItem.family(s) + "|" + SkinKitItem.wear(s);
            counts.merge(key, s.getCount(), Integer::sum);
            firsts.putIfAbsent(key, s);
        }
        if (counts.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.skin.list.empty", target.getDisplayName()), false);
            return 0;
        }
        int total = 0;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.skin.list.header", target.getDisplayName()), false);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            ItemStack kit = firsts.get(e.getKey());
            int n = e.getValue();
            total += n;
            ctx.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.skin.list.line", n, kit.getHoverName(),
                    SkinWear.displayName(SkinKitItem.wear(kit))), false);
        }
        return total;
    }
}
