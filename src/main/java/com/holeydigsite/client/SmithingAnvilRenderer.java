package com.holeydigsite.client;

import com.holeydigsite.smith.AnvilLayout;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.smith.AnvilRecipes;
import com.holeydigsite.smith.SmithingAnvilBlock;
import com.holeydigsite.smith.SmithingAnvilBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Smithing Anvil: the laid ingots lie flat on the work surface (left to right in laying order), the gem in front of them, and a
 * short floating label shows when the anvil is looked at (one label at a time):
 * <pre>
 *   Necklace (2/5)
 *   1/2 ingots, gem missing      (green = fine, red = wrong; "Ready - hit it with the hammer" / "Hammering 2/5" when valid)
 * </pre>
 * The text is rebuilt only when the block entity received new data or the block state changed (cached).
 */
public class SmithingAnvilRenderer implements BlockEntityRenderer<SmithingAnvilBlockEntity> {
    /** Longest ingot bar (15 px) after the fixed display scale of 0.5, in blocks. */
    private static final float BAR_LENGTH = 15.0F / 16.0F * 0.5F;
    /** The bar's underside sits 2 px below the model centre, which is 1/16 block after the fixed scale. */
    private static final float BAR_LIFT = 1.0F / 16.0F;
    private record Cached(int version, BlockState state, List<Component> lines) {}
    private final WeakHashMap<SmithingAnvilBlockEntity, Cached> cache = new WeakHashMap<>();
    private final ItemRenderer itemRenderer;

    public SmithingAnvilRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(SmithingAnvilBlockEntity a, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (a.getLevel() == null || !FloatingText.near(a.getBlockPos(), FloatingText.ITEM_RANGE)) {
            return;
        }
        Cached c = cache.get(a);
        if (c == null || c.version != a.clientVersion() || c.state != a.getBlockState()) {
            c = new Cached(a.clientVersion(), a.getBlockState(), lines(a));
            cache.put(a, c);
        }
        Direction facing = a.getBlockState().getValue(SmithingAnvilBlock.FACING);
        float modelY = (facing.toYRot() + 180.0F) % 360.0F; // same rotation as the blockstate variant
        int ingot = 0;
        int ingots = a.ingotCount();
        int n = 0;
        for (ItemStack s : a.items()) {
            boolean isIngot = s.getItem() instanceof com.holeydigsite.item.IngotItem;
            float[] spot = isIngot ? AnvilLayout.ingot(Math.min(ingot++, 2), ingots) : AnvilLayout.gem();
            pose.pushPose();
            pose.translate(0.5, 1.0 + 0.003, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-modelY));
            pose.translate((spot[0] - 8.0F) / 16.0, 0, (spot[1] - 8.0F) / 16.0);
            pose.translate(0, 0.02, 0);
            float scale;
            if (isIngot) {
                // the bar model lies flat already (fixed display turns it), the biggest bar is INGOT_SIZE long
                scale = AnvilLayout.INGOT_SIZE / 16.0F / BAR_LENGTH;
                pose.translate(0, scale * BAR_LIFT, 0);
            } else {
                scale = AnvilLayout.GEM_SIZE / 16.0F;
            }
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            pose.scale(scale, scale, scale);
            itemRenderer.renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                    a.getLevel(), (int) a.getBlockPos().asLong() + n++);
            pose.popPose();
        }
        if (FloatingText.focused(a.getBlockPos(), 1.0)) {
            float sc = FloatingText.labelScale(a.getBlockPos());
            FloatingText.drawLines(pose, buffers, c.lines, 0.5, 1.25, 0.5, sc, light);
        }
    }

    /** The label: recipe name + position in the cycle, then what is right or missing. */
    static List<Component> lines(SmithingAnvilBlockEntity a) {
        List<Component> lines = new ArrayList<>();
        List<AnvilRecipes.Entry> all = AnvilRecipes.all(a.getLevel().registryAccess());
        AnvilRecipes.Entry e = a.recipe();
        if (e == null) {
            lines.add(Component.translatable("text.holeydigsite.anvil.no_recipes").withStyle(ChatFormatting.RED));
            return lines;
        }
        AnvilRecipe r = e.recipe();
        lines.add(Component.translatable("text.holeydigsite.anvil.recipe", r.name(a.getLevel().registryAccess()), all.indexOf(e) + 1, all.size()).withStyle(ChatFormatting.GOLD));
        int have = a.ingotCount();
        boolean gem = a.gem().isPresent();
        List<MutableComponent> parts = new ArrayList<>();
        parts.add(Component.translatable("text.holeydigsite.anvil.ingots", have, r.ingots())
                .withStyle(have == r.ingots() ? ChatFormatting.GREEN : ChatFormatting.RED));
        switch (r.gem()) {
            case REQUIRED -> parts.add(gem ? Component.translatable("text.holeydigsite.anvil.gem_ok").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("text.holeydigsite.anvil.gem_missing").withStyle(ChatFormatting.RED));
            case OPTIONAL -> parts.add(gem ? Component.translatable("text.holeydigsite.anvil.gem_ok").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("text.holeydigsite.anvil.gem_optional").withStyle(ChatFormatting.GRAY));
            default -> {
                if (gem) {
                    parts.add(Component.translatable("text.holeydigsite.anvil.gem_remove").withStyle(ChatFormatting.RED));
                }
            }
        }
        SmithingAnvilBlockEntity.Problem p = a.problem(r);
        if (p == SmithingAnvilBlockEntity.Problem.LIGHT) {
            parts.add(Component.translatable("text.holeydigsite.anvil.light", r.minGrams()).withStyle(ChatFormatting.RED));
        }
        MutableComponent line = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            line.append(parts.get(i));
        }
        lines.add(line);
        if (p == SmithingAnvilBlockEntity.Problem.NONE) {
            lines.add(a.hits() > 0
                    ? Component.translatable("text.holeydigsite.anvil.progress", a.hits(), r.hits()).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("text.holeydigsite.anvil.ready").withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }
}
