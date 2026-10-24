package com.holeydigsite.sellpad;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.shop.AbstractPadBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;

import java.util.List;

/**
 * Sell Pad (Phase 8 rework): holds up to 9 stacks of sellable items on top, shows their total value floating above it,
 * and is sold by pressing an adjacent Shop Button (coins into the button's adjacent Money Pan). Prices are data-driven
 * ({@link Selling}); items without a price (coins, machines, clumps, tools, blueprints, vanilla items) are refused.
 * See {@link AbstractPadBlock} for the clicks.
 */
public class SellPadBlock extends AbstractPadBlock implements com.holeydigsite.transport.ItemTarget {
    public static final MapCodec<SellPadBlock> CODEC = simpleCodec(SellPadBlock::new);

    public SellPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean accepts(ItemStack stack, Level level) {
        return Selling.isSellable(stack, level.registryAccess());
    }

    /** Phase 21d: also takes items the store's operator entries buy back. */
    @Override
    public boolean accepts(ItemStack stack, Level level, net.minecraft.core.BlockPos pos) {
        return accepts(stack, level) || (level instanceof net.minecraft.server.level.ServerLevel sl
                && com.holeydigsite.shop.custom.CustomShops.buyBack(sl, pos, stack) > 0);
    }

    @Override
    protected String refusedKey() {
        return "message.holeydigsite.sell_pad.refused";
    }

    @Override
    protected String helpKey() {
        return "message.holeydigsite.sell_pad.help";
    }

    /** Total copper value of the stacks on a Sell Pad (what the floating text shows and the button pays). */
    public static long value(List<ItemStack> stacks, HolderLookup.Provider registries) {
        long total = 0;
        for (ItemStack s : stacks) {
            total += Selling.stackValue(s, registries);
        }
        return total;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.sell_pad").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.sell_pad.how").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- Phase 12: belts put sellable items on the pad (selling still needs the Shop Button) ----

    @Override
    public boolean canAcceptItem(Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.core.Direction from, ItemStack one) {
        com.holeydigsite.shop.PadBlockEntity pad = pad(level, pos);
        return pad != null && accepts(one, level) && pad.roomFor(one.copyWithCount(1)) >= 1;
    }

    @Override
    public void acceptItem(Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                           net.minecraft.core.Direction from, ItemStack one) {
        com.holeydigsite.shop.PadBlockEntity pad = pad(level, pos);
        if (pad != null) {
            pad.insert(one.copyWithCount(1));
        }
    }
}
