package com.holeydigsite.skin;

import com.holeydigsite.crate.CrateRolls;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Skin Kit: one machine skin as an item, so it can be traded. It carries the skin, the machine family it fits and the wear.
 * Kits only go on and come off at the Skin Bench, with the machine as an item, never on a placed machine.
 */
public class SkinKitItem extends Item {
    public SkinKitItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(String skin, String family, @Nullable String wear, int count) {
        ItemStack s = new ItemStack(ModItems.SKIN_KIT.get(), Math.max(1, count));
        s.set(ModDataComponents.SKIN.get(), skin);
        s.set(ModDataComponents.SKIN_FAMILY.get(), family);
        SkinWear.set(s, wear);
        return s;
    }

    /** A kit with both a skin and a family (a blank kit from a command or creative does nothing). */
    public static boolean isKit(ItemStack stack) {
        return stack.is(ModItems.SKIN_KIT.get()) && skin(stack) != null && family(stack) != null;
    }

    @Nullable
    public static String skin(ItemStack stack) {
        String s = stack.get(ModDataComponents.SKIN.get());
        return s == null || s.isEmpty() || SkinDefs.DEFAULT_ID.equals(s) ? null : s;
    }

    @Nullable
    public static String family(ItemStack stack) {
        String f = stack.get(ModDataComponents.SKIN_FAMILY.get());
        return f == null || f.isEmpty() ? null : f;
    }

    public static String wear(ItemStack stack) {
        return SkinWear.normalize(SkinWear.of(stack));
    }

    public static boolean matches(ItemStack stack, String skin, String family, @Nullable String wear) {
        return isKit(stack) && skin.equals(skin(stack)) && family.equals(family(stack))
                && SkinWear.normalize(wear).equals(wear(stack));
    }

    /** The basic machine of a family, for icons. */
    public static Item machineItem(String family) {
        return SkinDefs.SLUICE.equals(family) ? ModItems.SLUICE_BOX.get() : ModItems.MINING_DRILL.get();
    }

    /** The family's machine wearing this kit's skin, for the kit icon. */
    public static ItemStack machineIcon(ItemStack kit) {
        String family = family(kit);
        ItemStack s = new ItemStack(machineItem(family == null ? SkinDefs.DRILL : family));
        String skin = skin(kit);
        if (skin != null) {
            s.set(ModDataComponents.SKIN.get(), skin);
            SkinWear.set(s, wear(kit));
        }
        return s;
    }

    public static String rarity(String skin) {
        SkinDefs.SkinDef def = SkinDefs.skin(skin);
        return def == null ? "common" : def.rarity();
    }

    /** Skin Scrap for scrapping one kit: the Phase 23 duplicate amount, rarity times wear. */
    public static int scrapValue(ItemStack kit) {
        String skin = skin(kit);
        return CrateRolls.scrapFor(skin == null ? "common" : rarity(skin), wear(kit));
    }

    /** A family for a rolled skin, each family it fits equally likely. */
    public static String rollFamily(String skin, RandomSource random) {
        List<String> families = SkinDefs.familiesOf(skin);
        return families.get(random.nextInt(families.size()));
    }

    @Override
    public Component getName(ItemStack stack) {
        String skin = skin(stack);
        String family = family(stack);
        if (skin == null || family == null) {
            return super.getName(stack);
        }
        SkinDefs.SkinDef def = SkinDefs.skin(skin);
        Component skinName = def != null ? Component.translatable(def.nameKey()) : Component.literal(skin);
        return Component.translatable("item.holeydigsite.skin_kit.named", skinName, SkinDefs.familyName(family))
                .withStyle(def != null ? def.rarityFormatting() : ChatFormatting.WHITE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String skin = skin(stack);
        String family = family(stack);
        if (skin == null || family == null) {
            tooltip.add(Component.translatable("tooltip.holeydigsite.skin_kit.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        String rarity = rarity(skin);
        tooltip.add(Component.translatable("tooltip.holeydigsite.skin_kit.rarity",
                Component.translatable("rarity.holeydigsite." + rarity)).withStyle(com.holeydigsite.crate.CrateDefs.rarityFormatting(rarity)));
        tooltip.add(Component.translatable("tooltip.holeydigsite.skin_kit.fits." + family).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.skin_wear", SkinWear.displayName(wear(stack))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.skin_kit.use").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.skin_kit.scrap", scrapValue(stack)).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Sneak-use on a Skin Bench: scrap, with a confirm step. Sneaking skips the block's own use, so it lands here. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null || !ctx.getPlayer().isSecondaryUseActive()
                || !(ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof SkinBenchBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer sp) {
            SkinBenchActions.scrap(sp, ctx.getItemInHand(), ctx.getClickedPos());
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }
}
