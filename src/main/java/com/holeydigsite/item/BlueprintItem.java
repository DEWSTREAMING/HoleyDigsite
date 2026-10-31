package com.holeydigsite.item;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.shop.ShopItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Blueprint ({@code holeydigsite:blueprint}): names the item it is for in the {@code holeydigsite:blueprint_target}
 * component ("Blueprint: Tier 1 Mining Drill"). Put on a Buy Pad and press the Shop Button to buy that item from the
 * catalogue (each blueprint = one purchase of one item). Blueprints come free from Shop Displays, stack per target, and can't be
 * sold (they have no weight). They are used up when the Shop Button completes the purchase.
 */
public class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    /** A blueprint for {@code target}. */
    public static ItemStack of(Item target, int count) {
        ItemStack stack = new ItemStack(ModItems.BLUEPRINT.get(), count);
        stack.set(ModDataComponents.BLUEPRINT_TARGET.get(), target);
        return stack;
    }

    public static ItemStack of(Item target) {
        return of(target, 1);
    }

    /** The item a blueprint is for, or null (not a blueprint / blank). */
    @Nullable
    public static Item target(ItemStack stack) {
        return stack.is(ModItems.BLUEPRINT.get()) ? stack.get(ModDataComponents.BLUEPRINT_TARGET.get()) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        var custom = com.holeydigsite.shop.custom.CustomBlueprint.of(stack);
        if (custom != null) {
            return Component.translatable("item.holeydigsite.blueprint.for", custom.template().getHoverName());
        }
        Item target = target(stack);
        return target == null ? super.getName(stack)
                : Component.translatable("item.holeydigsite.blueprint.for", target.getDescription());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var customBp = com.holeydigsite.shop.custom.CustomBlueprint.of(stack);
        if (customBp != null) {
            tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.price", Selling.format(customBp.price())).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.gives", customBp.count(), customBp.template().getHoverName()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.how").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Item target = target(stack);
        if (target == null) {
            tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        Optional<ShopItem> entry = ShopCatalogue.find(context.registries(), target);
        if (entry.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.not_sold").withStyle(ChatFormatting.RED));
            return;
        }
        ShopItem e = entry.get();
        tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.price", e.priceText())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.gives", e.count(), target.getDescription())
                .withStyle(ChatFormatting.GRAY));
        e.site().ifPresent(site -> tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.exclusive",
                Component.translatable("plot.holeydigsite." + site)).withStyle(ChatFormatting.AQUA)));
        if (e.ledger()) {
            tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.ledger").withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.holeydigsite.blueprint.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
