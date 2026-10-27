package com.holeydigsite.skin;

import com.holeydigsite.crate.CrateDefs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Skin wear: how battered a crate skin looks. The order and odds come from {@code crate_rules.json}; index 0 is the best.
 * A missing or unknown wear reads as Fresh from the Forge, so skins and machines from before wear existed stay Fresh.
 */
public final class SkinWear {
    private SkinWear() {}

    public static final String FRESH = "fresh_from_the_forge";

    public static List<CrateDefs.Wear> wears() {
        return CrateDefs.rules().wears();
    }

    /** Position in the list (0 = best); unknown, empty and null count as Fresh. */
    public static int index(@Nullable String wear) {
        if (wear == null || wear.isEmpty()) return 0;
        List<CrateDefs.Wear> list = wears();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(wear)) return i;
        }
        return 0;
    }

    public static boolean exists(String wear) {
        for (CrateDefs.Wear w : wears()) {
            if (w.id().equals(wear)) return true;
        }
        return false;
    }

    public static String normalize(@Nullable String wear) {
        return wears().isEmpty() ? FRESH : wears().get(index(wear)).id();
    }

    public static boolean isFresh(@Nullable String wear) {
        return index(wear) == 0;
    }

    /** True if {@code a} is fresher than {@code b}. */
    public static boolean isBetter(@Nullable String a, @Nullable String b) {
        return index(a) < index(b);
    }

    public static double scrapMultiplier(@Nullable String wear) {
        List<CrateDefs.Wear> list = wears();
        return list.isEmpty() ? 1.0 : list.get(index(wear)).scrapMultiplier();
    }

    /** One wear roll with the data odds. */
    public static String roll(RandomSource random) {
        List<CrateDefs.Wear> list = wears();
        int total = 0;
        for (CrateDefs.Wear w : list) total += Math.max(0, w.odds());
        if (total <= 0) return FRESH;
        int r = random.nextInt(total);
        for (CrateDefs.Wear w : list) {
            r -= Math.max(0, w.odds());
            if (r < 0) return w.id();
        }
        return list.get(list.size() - 1).id();
    }

    public static Component displayName(@Nullable String wear) {
        String id = normalize(wear);
        ChatFormatting color = switch (index(id)) {
            case 0 -> ChatFormatting.GREEN;
            case 1 -> ChatFormatting.DARK_GREEN;
            case 2 -> ChatFormatting.YELLOW;
            case 3 -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
        return Component.translatable("wear.holeydigsite." + id).withStyle(color);
    }

    /** Folder under {@code textures/block/skin/}: {@code <skin>} for Fresh, {@code <skin>/<wear>} for the rest. */
    public static String textureKey(@Nullable String skin, @Nullable String wear) {
        if (skin == null || skin.isEmpty() || SkinDefs.DEFAULT_ID.equals(skin)) return "";
        return isFresh(wear) ? skin : skin + "/" + normalize(wear);
    }

    /** Wear on a machine item; empty for none (Fresh). */
    public static String of(ItemStack stack) {
        String w = stack.get(com.holeydigsite.registry.ModDataComponents.SKIN_WEAR.get());
        return w == null ? "" : w;
    }

    /** Sets or clears the wear component; Fresh is stored as no component so old and new Fresh items stack. */
    public static void set(ItemStack stack, @Nullable String wear) {
        if (isFresh(wear)) {
            stack.remove(com.holeydigsite.registry.ModDataComponents.SKIN_WEAR.get());
        } else {
            stack.set(com.holeydigsite.registry.ModDataComponents.SKIN_WEAR.get(), normalize(wear));
        }
    }
}
