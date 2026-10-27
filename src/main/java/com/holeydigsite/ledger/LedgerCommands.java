package com.holeydigsite.ledger;

import com.holeydigsite.HoleyDigsite;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Prospector's Ledger admin and test commands under {@code /holeydigsite ledger ...}.
 */
public final class LedgerCommands {
    private LedgerCommands() {}

    private static List<String> levelSuggestions() {
        List<String> list = new ArrayList<>();
        list.add("all");
        for (int i = 1; i <= LedgerDefs.MAX_LEVEL; i++) {
            list.add(String.valueOf(i));
        }
        return list;
    }

    private static List<String> milestoneSuggestions() {
        List<String> list = new ArrayList<>();
        list.add("all");
        for (ResourceLocation id : LedgerDefs.milestones().keySet()) {
            list.add(id.getPath());
        }
        return list;
    }

    private static List<String> titleSuggestions(boolean includeNone) {
        List<String> list = new ArrayList<>();
        if (includeNone) {
            list.add("none");
        } else {
            list.add("all");
        }
        list.addAll(LedgerDefs.allTitles());
        return list;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("ledger")
                        .then(Commands.literal("info")
                                .executes(c -> info(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> info(c, EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("milestone")
                                .then(Commands.literal("list")
                                        .executes(c -> milestoneList(c, c.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> milestoneList(c, EntityArgument.getPlayer(c, "player")))))
                                .then(Commands.literal("grant")
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .then(Commands.argument("milestone", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(milestoneSuggestions(), b))
                                                        .executes(c -> milestoneGrant(c, StringArgumentType.getString(c, "milestone"))))))
                                .then(Commands.literal("revoke")
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .then(Commands.argument("milestone", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(milestoneSuggestions(), b))
                                                        .executes(c -> milestoneRevoke(c, StringArgumentType.getString(c, "milestone")))))))
                        .then(Commands.literal("renown")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                        .executes(LedgerCommands::renownAdd))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(LedgerCommands::renownSet)))))
                        .then(Commands.literal("level")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, LedgerDefs.MAX_LEVEL))
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(levelSuggestions().subList(1, levelSuggestions().size()), b))
                                                        .executes(LedgerCommands::levelSet)))))
                        .then(Commands.literal("claim")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(levelSuggestions(), b))
                                                .executes(LedgerCommands::claim))))
                        .then(Commands.literal("unclaim")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(levelSuggestions(), b))
                                                .executes(LedgerCommands::unclaim))))
                        .then(Commands.literal("title")
                                .then(Commands.literal("unlock")
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .then(Commands.argument("title", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(titleSuggestions(false), b))
                                                        .executes(LedgerCommands::titleUnlock))))
                                .then(Commands.literal("lock")
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .then(Commands.argument("title", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(titleSuggestions(false), b))
                                                        .executes(LedgerCommands::titleLock))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .then(Commands.argument("title", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(titleSuggestions(true), b))
                                                        .executes(LedgerCommands::titleSet)))))
                        .then(Commands.literal("caps")
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(LedgerCommands::capsReset))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(c -> resetWarn(c, EntityArgument.getPlayers(c, "player")))
                                        .then(Commands.literal("confirm")
                                                .executes(LedgerCommands::resetConfirm))))));
    }

    private static String formatRanges(Set<Integer> set) {
        if (set.isEmpty()) return "none";
        List<Integer> list = new ArrayList<>(new TreeSet<>(set));
        List<String> ranges = new ArrayList<>();
        int start = list.get(0);
        int prev = start;
        for (int i = 1; i < list.size(); i++) {
            int cur = list.get(i);
            if (cur == prev + 1) {
                prev = cur;
            } else {
                ranges.add(start == prev ? String.valueOf(start) : start + "-" + prev);
                start = cur;
                prev = cur;
            }
        }
        ranges.add(start == prev ? String.valueOf(start) : start + "-" + prev);
        return String.join(", ", ranges);
    }

    private static int info(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        LedgerData data = LedgerData.get(player);
        int lvl = data.level();
        int curR = data.renown();
        int nextR = lvl >= LedgerDefs.MAX_LEVEL ? curR : LedgerDefs.curve().cumulativeForLevel(lvl + 1);
        String renownStr = curR + "/" + nextR + (lvl >= LedgerDefs.MAX_LEVEL ? " (MAX)" : "");

        int msDone = data.completedMilestones().size();
        int msTotal = LedgerDefs.milestones().size();

        String claimedStr = formatRanges(data.claimedLevels());
        String selectedTitle = data.chosenTitle().isEmpty() ? "none" : data.chosenTitle();
        String unlockedTitles = data.unlockedTitles().isEmpty() ? "none" : String.join(", ", data.unlockedTitles());

        LedgerDefs.Curve curve = LedgerDefs.curve();
        String capsStr = String.format(Locale.ROOT, "pan %d/%d, sluice %d/%d, harvest %d/%d, smith %d/%d, sell %d/%d",
                data.panRenown(), curve.pan().cap(),
                data.sluiceRenown(), curve.sluice().cap(),
                data.harvestRenown(), curve.harvest().cap(),
                data.smithRenown(), curve.smith().cap(),
                data.sellRenown(), curve.sell().cap());

        String name = player.getGameProfile().getName();
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.info",
                name, lvl, renownStr, msDone, msTotal, claimedStr, selectedTitle, unlockedTitles, capsStr), false);
        return lvl;
    }

    private static int milestoneList(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        LedgerData data = LedgerData.get(player);
        String name = player.getGameProfile().getName();
        c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_list_header", name), false);

        for (String chapter : LedgerDefs.CHAPTER_ORDER) {
            List<LedgerDefs.Milestone> inChapter = LedgerDefs.milestones().values().stream()
                    .filter(m -> m.chapter().equalsIgnoreCase(chapter)).toList();
            if (inChapter.isEmpty()) continue;
            int done = (int) inChapter.stream().filter(m -> data.isMilestoneCompleted(m.id())).count();
            List<String> items = new ArrayList<>();
            for (LedgerDefs.Milestone m : inChapter) {
                boolean isDone = data.isMilestoneCompleted(m.id());
                items.add(m.id().getPath() + (isDone ? " [✓]" : " [open]"));
            }
            String chapterTitle = Component.translatable("ledger.holeydigsite.chapter." + chapter).getString();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_list_chapter",
                    chapterTitle, done, inChapter.size(), String.join(", ", items)), false);
        }
        return data.completedMilestones().size();
    }

    private static int milestoneGrant(CommandContext<CommandSourceStack> c, String target) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        if (target.equalsIgnoreCase("all")) {
            int count = 0;
            for (ServerPlayer sp : players) {
                for (ResourceLocation id : LedgerDefs.milestones().keySet()) {
                    if (LedgerManager.grantMilestone(sp, id)) {
                        count++;
                    }
                }
            }
            if (players.size() == 1) {
                String name = players.iterator().next().getGameProfile().getName();
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_grant_all_one", name), true);
            } else {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_grant_all_many", players.size()), true);
            }
            return count;
        }

        ResourceLocation id = target.contains(":") ? ResourceLocation.parse(target) : HoleyDigsite.id(target);
        if (LedgerDefs.milestone(id) == null) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.unknown_milestone", target));
            return 0;
        }

        int count = 0;
        for (ServerPlayer sp : players) {
            if (LedgerManager.grantMilestone(sp, id)) {
                count++;
            }
        }
        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_grant_one", id.getPath(), name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_grant_many", id.getPath(), players.size()), true);
        }
        return count;
    }

    private static int milestoneRevoke(CommandContext<CommandSourceStack> c, String target) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        if (target.equalsIgnoreCase("all")) {
            int count = 0;
            for (ServerPlayer sp : players) {
                for (ResourceLocation id : LedgerDefs.milestones().keySet()) {
                    if (LedgerManager.revokeMilestone(sp, id)) {
                        count++;
                    }
                }
            }
            if (players.size() == 1) {
                String name = players.iterator().next().getGameProfile().getName();
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_revoke_all_one", name), true);
            } else {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_revoke_all_many", players.size()), true);
            }
            return count;
        }

        ResourceLocation id = target.contains(":") ? ResourceLocation.parse(target) : HoleyDigsite.id(target);
        if (LedgerDefs.milestone(id) == null) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.unknown_milestone", target));
            return 0;
        }

        int count = 0;
        for (ServerPlayer sp : players) {
            if (LedgerManager.revokeMilestone(sp, id)) {
                count++;
            }
        }
        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_revoke_one", id.getPath(), name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.milestone_revoke_many", id.getPath(), players.size()), true);
        }
        return count;
    }

    private static int renownAdd(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        int amount = IntegerArgumentType.getInteger(c, "amount");
        for (ServerPlayer sp : players) {
            LedgerData data = LedgerData.get(sp);
            int oldLevel = data.level();
            data.addRenown(amount);
            int newLevel = data.level();
            if (newLevel > oldLevel) {
                LedgerManager.notifyLevelUp(sp, newLevel);
            }
            LedgerNetwork.syncToClient(sp);
        }

        if (players.size() == 1) {
            ServerPlayer sp = players.iterator().next();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.renown_add_one",
                    amount, sp.getGameProfile().getName(), LedgerData.get(sp).renown()), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.renown_add_many",
                    amount, players.size()), true);
        }
        return players.size();
    }

    private static int renownSet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        int amount = IntegerArgumentType.getInteger(c, "amount");
        for (ServerPlayer sp : players) {
            LedgerData data = LedgerData.get(sp);
            int oldLevel = data.level();
            data.setRenown(amount);
            int newLevel = data.level();
            if (newLevel > oldLevel) {
                LedgerManager.notifyLevelUp(sp, newLevel);
            }
            LedgerNetwork.syncToClient(sp);
        }

        if (players.size() == 1) {
            ServerPlayer sp = players.iterator().next();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.renown_set_one",
                    sp.getGameProfile().getName(), amount), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.renown_set_many",
                    amount, players.size()), true);
        }
        return players.size();
    }

    private static int levelSet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        int targetLevel = IntegerArgumentType.getInteger(c, "level");
        int targetRenown = LedgerDefs.curve().cumulativeForLevel(targetLevel);

        for (ServerPlayer sp : players) {
            LedgerData data = LedgerData.get(sp);
            int oldLevel = data.level();
            data.setRenown(targetRenown);
            int newLevel = data.level();
            if (newLevel > oldLevel) {
                LedgerManager.notifyLevelUp(sp, newLevel);
            }
            LedgerNetwork.syncToClient(sp);
        }

        if (players.size() == 1) {
            ServerPlayer sp = players.iterator().next();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.level_set_one",
                    sp.getGameProfile().getName(), targetLevel, targetRenown), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.level_set_many",
                    targetLevel, players.size()), true);
        }
        return players.size();
    }

    private static int claim(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        String target = StringArgumentType.getString(c, "target");

        if (target.equalsIgnoreCase("all")) {
            int claimed = 0;
            for (ServerPlayer sp : players) {
                claimed += LedgerManager.claimAll(sp);
            }
            if (players.size() == 1) {
                String name = players.iterator().next().getGameProfile().getName();
                final int finalClaimed = claimed;
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.claim_all_one", finalClaimed, name), true);
            } else {
                final int finalClaimed = claimed;
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.claim_all_many", finalClaimed, players.size()), true);
            }
            return claimed;
        }

        int level;
        try {
            level = Integer.parseInt(target);
        } catch (NumberFormatException e) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.invalid_level", target));
            return 0;
        }

        if (level < 1 || level > LedgerDefs.MAX_LEVEL) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.level_out_of_range", level));
            return 0;
        }

        int successCount = 0;
        for (ServerPlayer sp : players) {
            LedgerData data = LedgerData.get(sp);
            if (data.level() < level) {
                c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.level_not_reached",
                        sp.getGameProfile().getName(), level, data.level()));
                continue;
            }
            if (data.isLevelClaimed(level)) {
                c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.level_already_claimed",
                        level, sp.getGameProfile().getName()));
                continue;
            }
            if (!LedgerManager.isLevelReady(LedgerDefs.level(level), sp.registryAccess())) {
                c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.level_coming_soon", level));
                continue;
            }
            if (LedgerManager.claim(sp, level)) {
                successCount++;
            }
        }

        if (successCount > 0) {
            if (players.size() == 1) {
                String name = players.iterator().next().getGameProfile().getName();
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.claim_one", level, name), true);
            } else {
                final int finalSuccess = successCount;
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.claim_many", level, finalSuccess), true);
            }
        }
        return successCount;
    }

    private static int unclaim(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        String target = StringArgumentType.getString(c, "target");

        if (target.equalsIgnoreCase("all")) {
            for (ServerPlayer sp : players) {
                LedgerData data = LedgerData.get(sp);
                data.claimedLevels().clear();
                LedgerNetwork.syncToClient(sp);
            }
            if (players.size() == 1) {
                String name = players.iterator().next().getGameProfile().getName();
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.unclaim_all_one", name), true);
            } else {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.unclaim_all_many", players.size()), true);
            }
            return players.size();
        }

        int level;
        try {
            level = Integer.parseInt(target);
        } catch (NumberFormatException e) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.invalid_level", target));
            return 0;
        }

        if (level < 1 || level > LedgerDefs.MAX_LEVEL) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.level_out_of_range", level));
            return 0;
        }

        int unclaimed = 0;
        for (ServerPlayer sp : players) {
            LedgerData data = LedgerData.get(sp);
            if (data.claimedLevels().remove(level)) {
                unclaimed++;
                LedgerNetwork.syncToClient(sp);
            }
        }

        if (unclaimed == 0) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.not_claimed", level));
            return 0;
        }

        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.unclaim_one", level, name), true);
        } else {
            final int finalUnclaimed = unclaimed;
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.unclaim_many", level, finalUnclaimed), true);
        }
        return unclaimed;
    }

    private static int titleUnlock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        String titleId = StringArgumentType.getString(c, "title");

        if (titleId.equalsIgnoreCase("all")) {
            for (ServerPlayer sp : players) {
                LedgerData data = LedgerData.get(sp);
                for (String t : LedgerDefs.allTitles()) {
                    data.unlockTitle(t);
                }
                LedgerNetwork.syncToClient(sp);
            }
            if (players.size() == 1) {
                String name = players.iterator().next().getGameProfile().getName();
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_unlock_all_one", name), true);
            } else {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_unlock_all_many", players.size()), true);
            }
            return players.size();
        }

        if (!LedgerDefs.hasTitle(titleId)) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.unknown_title", titleId));
            return 0;
        }

        for (ServerPlayer sp : players) {
            LedgerData data = LedgerData.get(sp);
            data.unlockTitle(titleId);
            LedgerNetwork.syncToClient(sp);
        }

        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_unlock_one", titleId, name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_unlock_many", titleId, players.size()), true);
        }
        return players.size();
    }

    private static int titleLock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        String titleId = StringArgumentType.getString(c, "title");

        if (titleId.equalsIgnoreCase("all")) {
            for (ServerPlayer sp : players) {
                LedgerData data = LedgerData.get(sp);
                data.unlockedTitles().clear();
                data.setChosenTitle("");
                sp.refreshDisplayName();
                LedgerNetwork.syncToClient(sp);
            }
            if (players.size() == 1) {
                String name = players.iterator().next().getGameProfile().getName();
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_lock_all_one", name), true);
            } else {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_lock_all_many", players.size()), true);
            }
            return players.size();
        }

        if (!LedgerDefs.hasTitle(titleId)) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.unknown_title", titleId));
            return 0;
        }

        for (ServerPlayer sp : players) {
            LedgerData data = LedgerData.get(sp);
            data.unlockedTitles().remove(titleId);
            if (data.chosenTitle().equals(titleId)) {
                data.setChosenTitle("");
                sp.refreshDisplayName();
            }
            LedgerNetwork.syncToClient(sp);
        }

        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_lock_one", titleId, name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_lock_many", titleId, players.size()), true);
        }
        return players.size();
    }

    private static int titleSet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        String titleId = StringArgumentType.getString(c, "title");

        if (titleId.equalsIgnoreCase("none")) {
            for (ServerPlayer sp : players) {
                LedgerManager.selectTitle(sp, "");
            }
            if (players.size() == 1) {
                String name = players.iterator().next().getGameProfile().getName();
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_set_none_one", name), true);
            } else {
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_set_none_many", players.size()), true);
            }
            return players.size();
        }

        if (!LedgerDefs.hasTitle(titleId)) {
            c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.unknown_title", titleId));
            return 0;
        }

        int set = 0;
        for (ServerPlayer sp : players) {
            if (LedgerManager.selectTitle(sp, titleId)) {
                set++;
            } else {
                c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.title_not_unlocked",
                        titleId, sp.getGameProfile().getName()));
            }
        }

        if (set > 0) {
            if (players.size() == 1) {
                String name = players.iterator().next().getGameProfile().getName();
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_set_one", titleId, name), true);
            } else {
                final int finalSet = set;
                c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.title_set_many", titleId, finalSet), true);
            }
        }
        return set;
    }

    private static int capsReset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        for (ServerPlayer sp : players) {
            LedgerData data = LedgerData.get(sp);
            data.resetCaps();
            LedgerNetwork.syncToClient(sp);
        }

        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.caps_reset_one", name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.caps_reset_many", players.size()), true);
        }
        return players.size();
    }

    private static int resetWarn(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) {
        String names = players.size() == 1 ? players.iterator().next().getGameProfile().getName() : players.size() + " players";
        c.getSource().sendFailure(Component.translatable("command.holeydigsite.ledger.reset_warning", names));
        return 0;
    }

    private static int resetConfirm(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "player");
        for (ServerPlayer sp : players) {
            LedgerManager.resetPlayer(sp);
        }

        if (players.size() == 1) {
            String name = players.iterator().next().getGameProfile().getName();
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.reset_one", name), true);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable("command.holeydigsite.ledger.reset_many", players.size()), true);
        }
        return players.size();
    }
}
