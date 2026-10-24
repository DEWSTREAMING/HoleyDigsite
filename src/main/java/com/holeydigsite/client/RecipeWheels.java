package com.holeydigsite.client;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.CraftingForgeBlock;
import com.holeydigsite.forge.CraftingForgeBlockEntity;
import com.holeydigsite.forge.ForgeRecipe;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.forge.Metals;
import com.holeydigsite.forge.RecipeWheelNetwork;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.smith.AnvilRecipes;
import com.holeydigsite.smith.SmithingAnvilBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link RadialMenu} screens for the Crafting Forge and Smithing Anvil, tracking the 0.25 s
 * right-click hold that opens the wheel without breaking short-click interactions.
 */
public final class RecipeWheels {
    /** Hold threshold in client ticks (~0.25 s at 20 TPS). */
    public static final int HOLD_TICKS = 5;

    @Nullable
    private static BlockHitResult pendingHit;
    private static int holdTicks;
    private static boolean replayingTap;

    private RecipeWheels() {}

    /** Builds the two-level recipe radial menu for a {@link CraftingForgeBlockEntity}. */
    public static RadialMenu createForgeMenu(CraftingForgeBlockEntity forge, @Nullable HolderLookup.Provider registries) {
        Map<String, List<ForgeRecipes.Entry>> grouped = ForgeRecipes.byCategory(registries);
        ForgeRecipes.Entry current = forge.recipe();
        List<RadialMenu.Category> categories = new ArrayList<>();

        for (Map.Entry<String, List<ForgeRecipes.Entry>> group : grouped.entrySet()) {
            String catId = group.getKey();
            List<RadialMenu.Slice> slices = new ArrayList<>();
            for (ForgeRecipes.Entry entry : group.getValue()) {
                ForgeRecipe r = entry.recipe();
                List<Component> details = new ArrayList<>();
                for (Map.Entry<ResourceLocation, Integer> m : r.metals().entrySet()) {
                    int have = forge.grams(m.getKey());
                    int need = m.getValue();
                    boolean ok = have >= need;
                    details.add(Component.translatable("text.holeydigsite.crafting_forge.metal", Metals.name(m.getKey()), have, need)
                            .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                }
                for (ForgeRecipe.Part p : r.parts()) {
                    int have = forge.partCount(p.item());
                    int need = r.partCount(p.item());
                    boolean ok = have >= need;
                    details.add(Component.translatable("text.holeydigsite.crafting_forge.part",
                                    new ItemStack(p.item()).getHoverName(), have, need)
                            .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                }
                boolean usable = forge.missing(r) == null;
                boolean isCurrent = current != null && current.id().equals(entry.id());
                slices.add(new RadialMenu.Slice(
                        entry.id(),
                        r.resultStack(),
                        r.baseName(),
                        r.variantLabel(registries),
                        details,
                        usable,
                        isCurrent
                ));
            }
            if (!slices.isEmpty()) {
                ItemStack catIcon = slices.get(0).icon().copyWithCount(1);
                categories.add(new RadialMenu.Category(catId, ForgeRecipes.categoryName(catId), catIcon, slices));
            }
        }

        BlockPos pos = forge.getBlockPos();
        return new RadialMenu(
                Component.translatable("block.holeydigsite.crafting_forge"),
                categories,
                recipeId -> RecipeWheelNetwork.sendToServer(pos, recipeId)
        );
    }

    /** Builds the two-level recipe radial menu for a {@link SmithingAnvilBlockEntity}. */
    public static RadialMenu createAnvilMenu(SmithingAnvilBlockEntity anvil, @Nullable HolderLookup.Provider registries) {
        Map<String, List<AnvilRecipes.Entry>> grouped = AnvilRecipes.byCategory(registries);
        AnvilRecipes.Entry current = anvil.recipe();
        List<RadialMenu.Category> categories = new ArrayList<>();

        for (Map.Entry<String, List<AnvilRecipes.Entry>> group : grouped.entrySet()) {
            String catId = group.getKey();
            List<RadialMenu.Slice> slices = new ArrayList<>();
            for (AnvilRecipes.Entry entry : group.getValue()) {
                AnvilRecipe r = entry.recipe();
                List<Component> details = new ArrayList<>();

                int haveIngots = anvil.ingotCount();
                boolean countOk = haveIngots == r.ingots();
                details.add(Component.translatable("text.holeydigsite.anvil.ingots", haveIngots, r.ingots())
                        .withStyle(countOk ? ChatFormatting.GREEN : ChatFormatting.GRAY));

                boolean weightOk = haveIngots > 0;
                for (ItemStack s : anvil.ingots()) {
                    if (Selling.grams(s) < r.minGrams()) {
                        weightOk = false;
                        break;
                    }
                }
                details.add(Component.translatable("screen.holeydigsite.radial.min_grams", r.minGrams())
                        .withStyle(weightOk ? ChatFormatting.GREEN : ChatFormatting.GRAY));

                if (r.gem() == AnvilRecipe.GemRule.REQUIRED) {
                    boolean hasGem = anvil.gem().isPresent();
                    details.add(Component.translatable(hasGem ? "text.holeydigsite.anvil.gem_ok" : "text.holeydigsite.anvil.gem_missing")
                            .withStyle(hasGem ? ChatFormatting.AQUA : ChatFormatting.GRAY));
                } else if (r.gem() == AnvilRecipe.GemRule.OPTIONAL) {
                    details.add(Component.translatable(anvil.gem().isPresent() ? "text.holeydigsite.anvil.gem_ok" : "text.holeydigsite.anvil.gem_optional")
                            .withStyle(ChatFormatting.AQUA));
                }

                details.add(Component.translatable("screen.holeydigsite.radial.hits", r.hits())
                        .withStyle(ChatFormatting.DARK_GRAY));

                boolean usable = anvil.problem(r) == SmithingAnvilBlockEntity.Problem.NONE;
                boolean isCurrent = current != null && current.id().equals(entry.id());
                slices.add(new RadialMenu.Slice(
                        entry.id(),
                        new ItemStack(r.result()),
                        r.baseName(),
                        r.variantLabel(registries),
                        details,
                        usable,
                        isCurrent
                ));
            }
            if (!slices.isEmpty()) {
                ItemStack catIcon = slices.get(0).icon().copyWithCount(1);
                categories.add(new RadialMenu.Category(catId, ForgeRecipes.categoryName(catId), catIcon, slices));
            }
        }

        BlockPos pos = anvil.getBlockPos();
        return new RadialMenu(
                Component.translatable("block.holeydigsite.smithing_anvil"),
                categories,
                recipeId -> RecipeWheelNetwork.sendToServer(pos, recipeId)
        );
    }

    /**
     * Checks whether a right-click interaction on {@code hit} is eligible for hold-to-open-wheel.
     */
    private static boolean canHoldForWheel(Level level, Player player, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        ItemStack held = player.getMainHandItem();

        if (be instanceof CraftingForgeBlockEntity forge) {
            if (forge.isCasting() || forge.hasOutput()) {
                return false;
            }
            if (CraftingForgeBlock.isHandle(state, pos, hit.getLocation())) {
                return false;
            }
            return held.isEmpty();
        }
        if (be instanceof SmithingAnvilBlockEntity anvil) {
            if (!held.isEmpty()) {
                if (held.is(ModItems.BLACKSMITH_HAMMER.get())) {
                    return false;
                }
                if (anvil.check(held) != SmithingAnvilBlockEntity.Refusal.NOT_ACCEPTED) {
                    return false;
                }
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Called from {@code InputEvent.InteractionKeyMappingTriggered} when the use key is triggered.
     * Returns {@code true} if the event should be cancelled while we measure whether the press is a tap or a 0.25 s hold.
     */
    public static boolean onUseKeyTriggered(InteractionHand hand) {
        if (replayingTap || hand != InteractionHand.MAIN_HAND) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) {
            return false;
        }
        if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (!canHoldForWheel(mc.level, mc.player, bhr)) {
            return false;
        }
        if (pendingHit == null) {
            pendingHit = bhr;
            holdTicks = 0;
        }
        return true;
    }

    /** Called every client tick to advance the hold timer or replay a short tap if the button was released early. */
    public static void clientTick() {
        if (pendingHit == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null) {
            pendingHit = null;
            holdTicks = 0;
            return;
        }
        if (mc.screen != null) {
            pendingHit = null;
            holdTicks = 0;
            return;
        }

        boolean stillDown = mc.options.keyUse.isDown();
        if (!stillDown) {
            // Replay the normal short-click interaction if released early
            BlockHitResult hit = pendingHit;
            pendingHit = null;
            holdTicks = 0;
            replayingTap = true;
            try {
                InteractionResult res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                if (res.consumesAction()) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            } finally {
                replayingTap = false;
            }
            return;
        }

        // Verify the player is still looking at the same workstation block
        if (!(mc.hitResult instanceof BlockHitResult currentHit)
                || currentHit.getType() != HitResult.Type.BLOCK
                || !currentHit.getBlockPos().equals(pendingHit.getBlockPos())) {
            pendingHit = null;
            holdTicks = 0;
            return;
        }

        holdTicks++;
        if (holdTicks >= HOLD_TICKS) {
            BlockPos pos = pendingHit.getBlockPos();
            pendingHit = null;
            holdTicks = 0;
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be instanceof CraftingForgeBlockEntity forge) {
                mc.setScreen(createForgeMenu(forge, mc.level.registryAccess()));
            } else if (be instanceof SmithingAnvilBlockEntity anvil) {
                mc.setScreen(createAnvilMenu(anvil, mc.level.registryAccess()));
            }
        }
    }
}
