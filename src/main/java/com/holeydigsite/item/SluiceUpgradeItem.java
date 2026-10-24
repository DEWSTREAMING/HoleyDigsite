package com.holeydigsite.item;

import com.holeydigsite.sluice.SluiceUpgrade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A sluice upgrade (Riffle Insert, Fine Mesh Screen: sold only at their plot's kiosk; Seed Screen: Trading Post). Right-click a Sluice Box with it to install
 * (see {@link com.holeydigsite.sluice.SluiceBoxBlock}); the work happens in the block.
 */
public class SluiceUpgradeItem extends Item {
    private final SluiceUpgrade upgrade;

    public SluiceUpgradeItem(SluiceUpgrade upgrade, Properties properties) {
        super(properties);
        this.upgrade = upgrade;
    }

    public SluiceUpgrade upgrade() {
        return upgrade;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(upgrade == SluiceUpgrade.SEED ? "tooltip.holeydigsite.seed_screen"
                : "tooltip.holeydigsite." + upgrade.getSerializedName() + "_insert").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.sluice_upgrade.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
