package com.holeydigsite.item;

import com.holeydigsite.block.PanBlock;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Item form of the Pan. Vanilla BlockItem already restores the fill level on placement
 * (from the {@code minecraft:block_state} component); this class only adds the tooltip.
 */
public class PanBlockItem extends BlockItem {
    public PanBlockItem(PanBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.pan.fill", PanBlock.getFill(stack), PanBlock.getMaxFill(stack))
                .withStyle(ChatFormatting.GRAY));
        if (PanBlock.getFill(stack) > 0 && PanBlock.getTier(stack) > 1) {
            tooltip.add(Component.translatable(DirtTier.dirtKey(PanBlock.getTier(stack))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
