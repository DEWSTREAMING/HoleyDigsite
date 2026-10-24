package com.holeydigsite.plot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A Land Deed (Phase 11): bought in the home island store; right-click the plot's For Sale board with it to claim the plot
 * (the deed is used up). One item per plot id ({@code deep_dig}, {@code gravel_flats}, {@code sand_dunes}).
 */
public class LandDeedItem extends Item {
    private static final java.util.Map<String, LandDeedItem> BY_PLOT = new java.util.HashMap<>();
    private final String plotId;

    public LandDeedItem(Properties properties, String plotId) {
        super(properties);
        this.plotId = plotId;
        BY_PLOT.put(plotId, this);
    }

    /** The deed item for a plot id, or null. */
    @org.jetbrains.annotations.Nullable
    public static LandDeedItem forPlot(String id) {
        return BY_PLOT.get(id);
    }

    public String plotId() {
        return plotId;
    }

    /** "Deep Dig" (lang {@code plot.holeydigsite.<id>}). */
    public static Component plotName(String id) {
        return Component.translatableWithFallback("plot.holeydigsite." + id, id);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.land_deed", plotName(plotId)).withStyle(ChatFormatting.GRAY));
    }
}
