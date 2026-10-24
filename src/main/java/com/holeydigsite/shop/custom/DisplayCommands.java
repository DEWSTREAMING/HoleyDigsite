package com.holeydigsite.shop.custom;

import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Map;

/**
 * Phase 21d: {@code /holeydigsite display set <price> [count] [buyprice]} (looking at a display; the held stack is the
 * template), {@code display clear}, {@code display info}, {@code display list [site]}. Permission level 2.
 */
public final class DisplayCommands {
    private DisplayCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("display")
                        .then(Commands.literal("set")
                                .then(Commands.argument("price", LongArgumentType.longArg(1, CustomEntry.MAX_PRICE))
                                        .executes(c -> set(c, 1, 0))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, CustomEntry.MAX_COUNT))
                                                .executes(c -> set(c, IntegerArgumentType.getInteger(c, "count"), 0))
                                                .then(Commands.argument("buyprice", LongArgumentType.longArg(1, CustomEntry.MAX_PRICE))
                                                        .executes(c -> set(c, IntegerArgumentType.getInteger(c, "count"),
                                                                LongArgumentType.getLong(c, "buyprice")))))))
                        .then(Commands.literal("clear").executes(DisplayCommands::clear))
                        .then(Commands.literal("info").executes(DisplayCommands::info))
                        .then(Commands.literal("list").executes(c -> list(c, ""))
                                .then(Commands.argument("site", StringArgumentType.greedyString())
                                        .executes(c -> list(c, StringArgumentType.getString(c, "site")))))));
    }

    /** The display the player is looking at, or null (with an error message). */
    private static BlockPos target(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        HitResult hit = p.pick(8.0, 1.0F, false);
        if (hit instanceof BlockHitResult b && p.level().getBlockEntity(b.getBlockPos()) instanceof ShopDisplayBlockEntity) {
            return b.getBlockPos();
        }
        c.getSource().sendFailure(Component.translatable("command.holeydigsite.display.look"));
        return null;
    }

    private static int set(CommandContext<CommandSourceStack> c, int count, long buy) throws CommandSyntaxException {
        BlockPos pos = target(c);
        if (pos == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.display.hold"));
            return 0;
        }
        CustomShops.Result r = CustomShops.set(p.serverLevel(), pos, held, count, LongArgumentType.getLong(c, "price"), buy);
        if (r != CustomShops.Result.OK) {
            c.getSource().sendFailure(Component.translatable(r == CustomShops.Result.CATALOGUE_ITEM
                    ? "command.holeydigsite.display.catalogue" : "command.holeydigsite.display.bad"));
            return 0;
        }
        CustomEntry e = CustomShops.find(p.serverLevel(), CustomShops.idOf(p.serverLevel(), (ShopDisplayBlockEntity) p.level().getBlockEntity(pos)));
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.display.set", CustomShops.describe(e)), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = target(c);
        if (pos == null) return 0;
        ServerLevel level = c.getSource().getPlayerOrException().serverLevel();
        boolean had = CustomShops.clear(level, pos);
        c.getSource().sendSuccess(() -> Component.translatable(had ? "command.holeydigsite.display.cleared" : "command.holeydigsite.display.none"), true);
        return had ? 1 : 0;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        BlockPos pos = target(c);
        if (pos == null) return 0;
        ServerLevel level = c.getSource().getPlayerOrException().serverLevel();
        ShopDisplayBlockEntity display = (ShopDisplayBlockEntity) level.getBlockEntity(pos);
        String id = CustomShops.idOf(level, display);
        CustomEntry e = CustomShops.find(level, id);
        if (display.item() != null) {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.display.info_catalogue", display.item().getDescription()), false);
        } else if (e == null) {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.display.info_empty", id), false);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.display.info", id, CustomShops.describe(e)), false);
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c, String site) {
        ServerLevel level = c.getSource().getLevel();
        int n = 0;
        for (Map.Entry<String, CustomEntry> e : CustomShops.get(level).all().entrySet()) {
            if (site.isEmpty() || e.getKey().contains(site)) {
                n++;
                String line = e.getKey() + ": " + CustomShops.describe(e.getValue());
                c.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
            }
        }
        int total = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.display.list", total), false);
        return n;
    }
}
