package com.holeydigsite.shop;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;

import java.util.List;

/**
 * Buy Pad: like the Sell Pad, but it only takes Blueprints of catalogued items. Floating text shows the total cost, and an
 * adjacent Shop Button buys everything on it with the balance of the button's adjacent Money Pan. The blueprints are used up
 * when a purchase completes, while a failed purchase leaves them on the pad.
 */
public class BuyPadBlock extends AbstractPadBlock {
    public static final MapCodec<BuyPadBlock> CODEC = simpleCodec(BuyPadBlock::new);

    public BuyPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean accepts(ItemStack stack, Level level) {
        var custom = com.holeydigsite.shop.custom.CustomBlueprint.of(stack);
        if (custom != null) {
            return !(level instanceof net.minecraft.server.level.ServerLevel sl) || com.holeydigsite.shop.custom.CustomShops.valid(sl, custom);
        }
        return ShopCatalogue.forBlueprint(level.registryAccess(), stack).isPresent();
    }

    @Override
    public boolean accepts(ItemStack stack, Level level, BlockPos pos) {
        if (com.holeydigsite.shop.custom.CustomBlueprint.of(stack) != null) {
            return accepts(stack, level);
        }
        return ShopCatalogue.buyableAt(level, pos, stack);
    }

    @Override
    protected String refusedKey() {
        return "message.holeydigsite.buy_pad.refused";
    }

    @Override
    protected String helpKey() {
        return "message.holeydigsite.buy_pad.help";
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.buy_pad").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.buy_pad.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
