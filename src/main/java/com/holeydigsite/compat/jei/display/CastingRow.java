package com.holeydigsite.compat.jei.display;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.jei.JeiText;
import com.holeydigsite.forge.CastingMoldBlock;
import com.holeydigsite.forge.Metals;
import com.holeydigsite.forge.MoldBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record CastingRow(
        ResourceLocation metal,
        Component metalName,
        int grams,
        ItemStack moltenInput,
        ItemStack outputIngot,
        boolean moldAllowed,
        boolean castingMoldAllowed,
        Component moldNote
) {
    public static final List<ResourceLocation> METALS = List.of(
            HoleyDigsite.id("folkiron"),
            HoleyDigsite.id("goldchro"),
            HoleyDigsite.id("orchremia"),
            HoleyDigsite.id("silvane")
    );

    public static List<CastingRow> loadAll() {
        int[] moldTargets = MoldBlock.TARGETS != null ? MoldBlock.TARGETS : new int[]{10, 25, 50, 75, 100, 150, 200};
        int[] castingTargets = CastingMoldBlock.TARGETS != null ? CastingMoldBlock.TARGETS : new int[]{10, 25, 50, 75, 100, 150, 200, 300, 400, 500};

        int maxMold = java.util.Arrays.stream(moldTargets).max().orElse(0);
        int maxCasting = java.util.Arrays.stream(castingTargets).max().orElse(0);

        List<Integer> allWeights = new ArrayList<>();
        for (int w : castingTargets) {
            if (!allWeights.contains(w)) allWeights.add(w);
        }

        List<CastingRow> rows = new ArrayList<>();
        for (ResourceLocation metal : METALS) {
            Component name = Metals.name(metal);
            for (int grams : allWeights) {
                boolean inMold = false;
                for (int mw : moldTargets) {
                    if (mw == grams) {
                        inMold = true;
                        break;
                    }
                }
                boolean inCasting = true;
                ItemStack output = Metals.ingot(metal, grams);
                ItemStack input = output.copy();
                Component note = inMold
                        ? JeiText.t("casting_note_both", maxMold, JeiText.seconds(MoldBlock.COOL_TICKS), JeiText.seconds(CastingMoldBlock.COOL_TICKS))
                        : JeiText.t("casting_note_only", maxCasting, JeiText.seconds(CastingMoldBlock.COOL_TICKS));

                rows.add(new CastingRow(
                        metal,
                        name,
                        grams,
                        input,
                        output,
                        inMold,
                        inCasting,
                        note
                ));
            }
        }
        return List.copyOf(rows);
    }
}
