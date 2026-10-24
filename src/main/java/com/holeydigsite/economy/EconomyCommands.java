package com.holeydigsite.economy;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.smith.AnvilRecipes;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import com.holeydigsite.tier.DirtTier;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Phase 21c: Economy and item inspection/spawning commands under {@code /holeydigsite give ...},
 * {@code /holeydigsite money ...}, and {@code /holeydigsite worth ...}.
 */
public final class EconomyCommands {
    private EconomyCommands() {}

    public static final List<String> METALS = List.of("folkiron", "goldchro", "orchremia", "silvane");
    public static final List<String> GEMS = List.of("ruby", "sapphire", "topaz");
    public static final List<String> SIZES = List.of("small", "medium", "large");
    public static final List<String> CLUMPS = List.of("dirt", "deep", "abyssal", "gravel", "sand", "coarse", "rooted", "moss");
    public static final List<String> PIECES = List.of("ring", "necklace", "dagger", "axe", "sword");
    public static final List<String> PIECE_GEMS = List.of("none", "ruby", "sapphire", "topaz");

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("money")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(EconomyCommands::moneyGive)))))
                .then(Commands.literal("worth")
                        .executes(c -> worth(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> worth(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("give")
                        .then(Commands.literal("ingot")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("metal", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(METALS, b))
                                                .then(Commands.argument("grams", IntegerArgumentType.integer(1))
                                                        .executes(c -> giveIngot(c, 1))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                                .executes(c -> giveIngot(c, IntegerArgumentType.getInteger(c, "count"))))))))
                        .then(Commands.literal("gem")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("gem", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(GEMS, b))
                                                .then(Commands.argument("size", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SIZES, b))
                                                        .executes(c -> giveGem(c, false, 1))
                                                        .then(Commands.literal("uncut")
                                                                .executes(c -> giveGem(c, true, 1))
                                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                                        .executes(c -> giveGem(c, true, IntegerArgumentType.getInteger(c, "count")))))
                                                        .then(Commands.literal("cut")
                                                                .executes(c -> giveGem(c, false, 1))
                                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                                        .executes(c -> giveGem(c, false, IntegerArgumentType.getInteger(c, "count")))))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                                .executes(c -> giveGem(c, false, IntegerArgumentType.getInteger(c, "count"))))))))
                        .then(Commands.literal("clump")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("tier", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(CLUMPS, b))
                                                .executes(c -> giveClump(c, 1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                        .executes(c -> giveClump(c, IntegerArgumentType.getInteger(c, "count")))))))
                        .then(Commands.literal("piece")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("piece", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(PIECES, b))
                                                .then(Commands.argument("metal", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(METALS, b))
                                                        .then(Commands.argument("grams", IntegerArgumentType.integer(1))
                                                                .executes(c -> givePiece(c, "none"))
                                                                .then(Commands.argument("gem", StringArgumentType.word())
                                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(PIECE_GEMS, b))
                                                                        .executes(c -> givePiece(c, StringArgumentType.getString(c, "gem")))))))))));
    }

    private static int moneyGive(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        int amount = IntegerArgumentType.getInteger(c, "amount");
        for (ServerPlayer sp : players) {
            for (ItemStack coin : Selling.coins(amount)) {
                LedgerManager.giveOrDrop(sp, coin);
            }
        }
        String formatted = Selling.format(amount).getString();
        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.money.given_one", formatted, amount, name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.money.given_many", formatted, amount, players.size()), true);
        }
        return players.size();
    }

    private static int giveIngot(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        String metal = StringArgumentType.getString(c, "metal").toLowerCase(Locale.ROOT);
        int grams = IntegerArgumentType.getInteger(c, "grams");

        ResourceLocation id = HoleyDigsite.id(metal + "_ingot");
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR || !(item instanceof IngotItem)) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.give.unknown_metal", metal));
            return 0;
        }

        ItemStack template = new ItemStack(item);
        template.set(ModDataComponents.WEIGHT.get(), grams);
        for (ServerPlayer sp : players) {
            LedgerManager.giveCountOrDrop(sp, template, count);
        }

        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.give.ingot_one", count, metal, grams, name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.give.ingot_many", count, metal, grams, players.size()), true);
        }
        return players.size();
    }

    private static int giveGem(CommandContext<CommandSourceStack> c, boolean uncut, int count) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        String gem = StringArgumentType.getString(c, "gem").toLowerCase(Locale.ROOT);
        String size = StringArgumentType.getString(c, "size").toLowerCase(Locale.ROOT);

        String path = (uncut ? "uncut_" : "") + gem + "_" + size;
        ResourceLocation id = HoleyDigsite.id(path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.give.unknown_gem", path));
            return 0;
        }

        ItemStack template = new ItemStack(item);
        for (ServerPlayer sp : players) {
            LedgerManager.giveCountOrDrop(sp, template, count);
        }

        String label = (uncut ? "uncut " : "") + gem + " (" + size + ")";
        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.give.gem_one", count, label, name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.give.gem_many", count, label, players.size()), true);
        }
        return players.size();
    }

    private static int giveClump(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        String tierStr = StringArgumentType.getString(c, "tier").toLowerCase(Locale.ROOT);
        int kind = switch (tierStr) {
            case "dirt" -> 1;
            case "deep" -> 2;
            case "abyssal" -> 3;
            case "gravel" -> 4;
            case "sand" -> 5;
            case "coarse" -> 6;
            case "rooted" -> 7;
            case "moss" -> 8;
            default -> 0;
        };
        if (kind <= 0) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.give.unknown_clump", tierStr));
            return 0;
        }

        ItemStack template = DirtTier.clumps(kind, 1);
        for (ServerPlayer sp : players) {
            LedgerManager.giveCountOrDrop(sp, template, count);
        }

        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.give.clump_one", count, tierStr, name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.give.clump_many", count, tierStr, players.size()), true);
        }
        return players.size();
    }

    private static int givePiece(CommandContext<CommandSourceStack> c, String gem) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        String piece = StringArgumentType.getString(c, "piece").toLowerCase(Locale.ROOT);
        String metal = StringArgumentType.getString(c, "metal").toLowerCase(Locale.ROOT);
        int grams = IntegerArgumentType.getInteger(c, "grams");

        Item pieceItem = switch (piece) {
            case "ring" -> ModItems.SMITH_RING.get();
            case "necklace" -> ModItems.SMITH_NECKLACE.get();
            case "dagger" -> ModItems.SMITH_DAGGER.get();
            case "axe" -> ModItems.SMITH_AXE.get();
            case "sword" -> ModItems.SMITH_SWORD.get();
            default -> null;
        };
        if (pieceItem == null) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.give.unknown_piece", piece));
            return 0;
        }

        ResourceLocation metalId = HoleyDigsite.id(metal);
        HolderLookup.Provider registries = c.getSource().registryAccess();
        AnvilRecipe recipe = AnvilRecipes.forItem(registries, pieceItem);
        int ingotsNeeded = recipe != null ? recipe.ingots() : 1;

        List<SmithData.Ingot> ingots = new ArrayList<>();
        for (int i = 0; i < ingotsNeeded; i++) {
            ingots.add(new SmithData.Ingot(metalId, grams));
        }

        Optional<SmithData.Gem> gemOpt = Optional.empty();
        if (gem != null && !gem.isEmpty() && !gem.equalsIgnoreCase("none")) {
            gemOpt = Optional.of(new SmithData.Gem(HoleyDigsite.id(gem.toLowerCase(Locale.ROOT)), 2));
        }

        SmithData data = new SmithData(ingots, gemOpt);
        ItemStack pieceStack = SmithPieces.create(pieceItem, data, registries);

        for (ServerPlayer sp : players) {
            LedgerManager.giveOrDrop(sp, pieceStack.copy());
        }

        String gemDesc = gemOpt.map(g -> " with " + g.material().getPath()).orElse("");
        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.give.piece_one", piece, metal, grams, gemDesc, name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.give.piece_many", piece, metal, grams, gemDesc, players.size()), true);
        }
        return players.size();
    }

    private record ValuedStack(ItemStack stack, long value) {}

    private static int worth(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        HolderLookup.Provider registries = c.getSource().registryAccess();
        ItemStack held = player.getMainHandItem();
        long heldVal = Selling.stackValue(held, registries);
        String heldStr;
        if (heldVal > 0) {
            heldStr = held.getHoverName().getString() + " x" + held.getCount() + " = " + Selling.format(heldVal).getString() + " (" + heldVal + "c)";
        } else {
            heldStr = "none";
        }

        long totalVal = 0;
        List<ValuedStack> sellable = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            long val = Selling.stackValue(s, registries);
            if (val > 0) {
                totalVal += val;
                sellable.add(new ValuedStack(s.copy(), val));
            }
        }

        sellable.sort((a, b) -> Long.compare(b.value(), a.value()));
        List<String> topStrings = new ArrayList<>();
        for (int i = 0; i < Math.min(3, sellable.size()); i++) {
            ValuedStack vs = sellable.get(i);
            topStrings.add((i + 1) + ") " + vs.stack().getHoverName().getString() + " x" + vs.stack().getCount() + " (" + vs.value() + "c)");
        }
        String topStr = topStrings.isEmpty() ? "none" : String.join(", ", topStrings);
        String totalFormatted = Selling.format(totalVal).getString() + " (" + totalVal + "c)";
        String playerName = player.getGameProfile().getName();

        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.worth.report", playerName, heldStr, totalFormatted, topStr), false);
        return (int) Math.min(Integer.MAX_VALUE, totalVal);
    }
}
