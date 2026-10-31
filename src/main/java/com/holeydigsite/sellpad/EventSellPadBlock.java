package com.holeydigsite.sellpad;

import com.holeydigsite.event.EventDefs;
import com.holeydigsite.shop.AbstractPadBlock;
import com.holeydigsite.shop.PadBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The event store's Sell Pad: takes event finds (gems, Outback Gold chunks and ingots, pebbles, trophies) and, on the Shop
 * Button, pays Opal Chips of the active event to the player who pressed it. Everything else is refused.
 */
public class EventSellPadBlock extends AbstractPadBlock implements com.holeydigsite.transport.ItemTarget {
    public static final MapCodec<EventSellPadBlock> CODEC = simpleCodec(EventSellPadBlock::new);

    public EventSellPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Server: event finds worth chips in the active event. Client: any event find (the tags sync, the chip table doesn't). */
    @Override
    public boolean accepts(ItemStack stack, Level level) {
        if (!EventDefs.isEventFind(stack)) {
            return false;
        }
        return level.isClientSide || EventDefs.chipValue(stack.copyWithCount(1)) > 0 || EventDefs.chipValue(stack) > 0;
    }

    @Override
    protected String refusedKey() {
        return "message.holeydigsite.event_sell_pad.refused";
    }

    @Override
    protected String helpKey() {
        return "message.holeydigsite.event_sell_pad.help";
    }

    /** Opal Chips the stacks are worth in the active event. */
    public static long value(List<ItemStack> stacks) {
        long total = 0;
        for (ItemStack s : stacks) {
            total += EventDefs.chipValue(s);
        }
        return total;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.event_sell_pad").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean canAcceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        PadBlockEntity pad = pad(level, pos);
        return pad != null && accepts(one, level) && pad.roomFor(one.copyWithCount(1)) >= 1;
    }

    @Override
    public void acceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        PadBlockEntity pad = pad(level, pos);
        if (pad != null) {
            pad.insert(one.copyWithCount(1));
        }
    }
}
