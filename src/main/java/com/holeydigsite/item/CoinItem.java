package com.holeydigsite.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A coin worth a fixed number of copper coins. Coins exchange 8:1 in a crafting grid
 * (8 copper = 1 silver, and so on up to the Mint Bar, and back) - see {@code data/holeydigsite/recipe/}.
 * Coins are the shop currency: Sell Pad sales are paid into a Money Pan, purchases are paid from it (see {@code shop/}).
 */
public class CoinItem extends Item {
    /** Exchange rate between neighbouring coin tiers (fits a crafting grid with one slot to spare). */
    public static final int EXCHANGE_RATE = 8;

    public static final int COPPER_VALUE = 1;
    public static final int SILVER_VALUE = COPPER_VALUE * EXCHANGE_RATE; // 8
    public static final int GOLD_VALUE = SILVER_VALUE * EXCHANGE_RATE;   // 64
    public static final int PLATINUM_VALUE = GOLD_VALUE * EXCHANGE_RATE; // 512
    public static final int CROWN_VALUE = PLATINUM_VALUE * EXCHANGE_RATE; // 4096
    public static final int BAR_VALUE = CROWN_VALUE * EXCHANGE_RATE;     // 32768

    private final int value;

    public CoinItem(int value, Properties properties) {
        super(properties);
        this.value = value;
    }

    /** Worth in copper coins. */
    public int getValue() {
        return value;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.coin.value", value).withStyle(ChatFormatting.GRAY));
    }
}
