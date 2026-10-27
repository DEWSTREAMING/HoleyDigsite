package com.holeydigsite.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Splits floating label lines that are too wide, at spaces and preferably after a "|" separator, never inside a word.
 * Every word keeps its own style. Plain Java so it can be tested without a client.
 */
public final class LabelWrap {
    private LabelWrap() {}

    private record Word(String text, Style style) {}

    /** {@code width} measures plain text in pixels. */
    public static List<Component> wrap(List<Component> lines, ToIntFunction<String> width, int max) {
        List<Component> out = new ArrayList<>();
        for (Component line : lines) {
            out.addAll(wrapOne(line, width, max));
        }
        return out;
    }

    private static List<Component> wrapOne(Component line, ToIntFunction<String> width, int max) {
        List<Word> words = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Style[] curStyle = {Style.EMPTY};
        line.visit((style, text) -> {
            for (char ch : text.toCharArray()) {
                if (ch == ' ') {
                    flush(words, cur, curStyle[0]);
                } else {
                    if (cur.length() > 0 && !style.equals(curStyle[0])) {
                        // style changes inside a word: the pieces stay together (null = glued to the next piece)
                        flush(words, cur, curStyle[0]);
                        words.add(null);
                    }
                    curStyle[0] = style;
                    cur.append(ch);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        flush(words, cur, curStyle[0]);

        // atoms: runs of words that end at a "|" word or at the end of the line
        List<List<Word>> atoms = new ArrayList<>();
        List<Word> atom = new ArrayList<>();
        for (Word w : words) {
            atom.add(w);
            if (w != null && w.text.equals("|")) {
                atoms.add(atom);
                atom = new ArrayList<>();
            }
        }
        if (!atom.isEmpty()) {
            atoms.add(atom);
        }

        List<Component> out = new ArrayList<>();
        List<Word> row = new ArrayList<>();
        for (List<Word> a : atoms) {
            if (!row.isEmpty() && widthOf(row, a, width) <= max) {
                row.addAll(a);
                continue;
            }
            if (!row.isEmpty()) {
                out.add(build(row));
                row = new ArrayList<>();
            }
            if (widthOf(row, a, width) <= max) {
                row.addAll(a);
                continue;
            }
            for (Word w : a) {
                if (w != null && !row.isEmpty() && row.get(row.size() - 1) != null && widthOf(row, List.of(w), width) > max) {
                    out.add(build(row));
                    row = new ArrayList<>();
                }
                row.add(w);
            }
        }
        if (!row.isEmpty()) {
            out.add(build(row));
        }
        return out.isEmpty() ? List.of(line) : out;
    }

    private static void flush(List<Word> words, StringBuilder cur, Style style) {
        if (cur.length() > 0) {
            words.add(new Word(cur.toString(), style));
            cur.setLength(0);
        }
    }

    private static int widthOf(List<Word> row, List<Word> add, ToIntFunction<String> width) {
        StringBuilder sb = new StringBuilder();
        boolean glue = true;
        for (List<Word> part : List.of(row, add)) {
            for (Word w : part) {
                if (w == null) {
                    glue = true;
                    continue;
                }
                if (sb.length() > 0 && !glue) sb.append(' ');
                sb.append(w.text);
                glue = false;
            }
        }
        return width.applyAsInt(sb.toString());
    }

    private static Component build(List<Word> row) {
        MutableComponent c = Component.empty();
        boolean glue = true;
        for (Word w : row) {
            if (w == null) {
                glue = true;
                continue;
            }
            if (!glue) {
                c.append(Component.literal(" "));
            }
            c.append(Component.literal(w.text).withStyle(w.style));
            glue = false;
        }
        return c;
    }
}
