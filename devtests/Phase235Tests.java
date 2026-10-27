package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.util.LabelWrap;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Wrapping of long floating label lines and the Money Pan label. */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase235Tests {
    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static int px(String s) {
        return s.length() * 6;
    }

    @GameTest(template = "empty")
    public static void longLinesWrapAtSeparatorsAndKeepColour(GameTestHelper h) {
        MutableComponent line = Component.empty()
                .append(Component.literal("Folkiron 0/2400 g").withStyle(ChatFormatting.RED))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Goldchro 0/60 g").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Orchremia 0/3600 g").withStyle(ChatFormatting.RED));
        List<Component> out = LabelWrap.wrap(List.of(line), Phase235Tests::px, 165);
        check(h, out.size() == 3, "three lines: " + out.size());
        check(h, out.get(0).getString().equals("Folkiron 0/2400 g |"), "first: " + out.get(0).getString());
        check(h, out.get(1).getString().equals("Goldchro 0/60 g |"), "second: " + out.get(1).getString());
        check(h, out.get(2).getString().equals("Orchremia 0/3600 g"), "third: " + out.get(2).getString());
        List<Component> two = LabelWrap.wrap(List.of(line), Phase235Tests::px, 230);
        check(h, two.size() == 2 && two.get(0).getString().equals("Folkiron 0/2400 g | Goldchro 0/60 g |"), "packs chunks when they fit");
        for (Component c : out) {
            check(h, px(c.getString()) <= 165, "fits: " + c.getString());
        }
        boolean red = false;
        for (var sib : out.get(2).getSiblings()) {
            if (sib.getStyle().getColor() != null && sib.getStyle().getColor().getValue() == ChatFormatting.RED.getColor()) red = true;
        }
        check(h, red, "colour kept on the wrapped part");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wrapNeverSplitsWordsAndLeavesShortLines(GameTestHelper h) {
        Component shortLine = Component.literal("Ready - pull the handle");
        check(h, LabelWrap.wrap(List.of(shortLine), Phase235Tests::px, 165).size() == 1, "short line stays one line");
        Component plain = Component.literal("one two three four five six seven eight nine ten eleven twelve thirteen fourteen");
        List<Component> out = LabelWrap.wrap(List.of(plain), Phase235Tests::px, 120);
        check(h, out.size() > 1, "wrapped");
        StringBuilder all = new StringBuilder();
        for (Component c : out) {
            check(h, px(c.getString()) <= 120, "fits: " + c.getString());
            all.append(all.length() > 0 ? " " : "").append(c.getString());
        }
        check(h, all.toString().equals(plain.getString()), "no word lost or cut: " + all);
        Component glued = Component.empty().append(Component.literal("aaaa bbb").withStyle(ChatFormatting.RED))
                .append(Component.literal("ccc ddd").withStyle(ChatFormatting.BLUE));
        List<Component> g = LabelWrap.wrap(List.of(glued), Phase235Tests::px, 30);
        boolean together = false;
        for (Component c : g) together |= c.getString().contains("bbbccc");
        check(h, together, "a word with two styles stays whole");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moneyPanLabelLines(GameTestHelper h) {
        long big = 3 * Selling.BAR + 6 * Selling.CROWN + 7 * Selling.PLATINUM + 5 * Selling.GOLD + 6 * Selling.SILVER + 1;
        List<Component> lines = Selling.balanceLines(big, c -> (c.getSiblings().size() + 1) / 2 * 50, 150);
        check(h, lines.size() == 3, "total line plus two breakdown lines: " + lines.size());
        TranslatableContents first = (TranslatableContents) lines.get(0).getContents();
        check(h, first.getKey().equals("tooltip.holeydigsite.money_pan.balance"), "first line is the balance");
        check(h, first.getArgs()[0] instanceof Component g && g.getContents() instanceof TranslatableContents t
                && t.getKey().equals("coin.holeydigsite.gold") && t.getArgs()[0].equals(Selling.goldTotal(big)), "first line has the gold total");
        check(h, (lines.get(1).getSiblings().size() + 1) / 2 == 3 && (lines.get(2).getSiblings().size() + 1) / 2 == 3, "3 coin kinds per line");
        List<Component> small = Selling.balanceLines(7 * Selling.GOLD + 7 * Selling.SILVER + 7, c -> 10, 150);
        check(h, small.size() == 1, "under 1 platinum is one line: " + small.size());
        h.succeed();
    }
}
