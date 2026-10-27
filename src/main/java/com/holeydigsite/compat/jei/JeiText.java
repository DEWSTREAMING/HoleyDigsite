package com.holeydigsite.compat.jei;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Small helpers for the JEI texts: lang keys under {@code jei.holeydigsite.} and number formatting. */
public final class JeiText {
    private JeiText() {}

    public static final String PREFIX = "jei.holeydigsite.";

    public static Component t(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    /** Whole numbers without decimals, otherwise up to two decimals with no trailing zeros. */
    public static String num(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("[.]$", "");
    }

    public static String percent(double fraction) {
        return num(fraction * 100.0);
    }

    public static String seconds(int ticks) {
        return num(ticks / 20.0);
    }
}
