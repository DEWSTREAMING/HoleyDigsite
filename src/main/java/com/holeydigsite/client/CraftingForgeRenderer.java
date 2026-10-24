package com.holeydigsite.client;

import com.holeydigsite.forge.CraftingForgeBlock;
import com.holeydigsite.forge.CraftingForgeBlockEntity;
import com.holeydigsite.forge.ForgeRecipe;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.forge.Metals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Crafting Forge: up to four items on the basin (the output, else the loaded parts and one ingot per metal) and a few
 * short floating lines when the player is near:
 * <pre>
 *   Tier 2 Mining Drill (1/5)
 *   Folkiron 137/100 g | Goldchro 20/25 g      (green = met, red = missing, grey = loaded but not needed)
 *   Tier 1 Mining Drill 1/1
 *   Ready - pull the handle / Casting... / Unlit - pipe lava to the back
 * </pre>
 * The lines are rebuilt only when the block entity received new data or the block state changed (cached).
 */
public class CraftingForgeRenderer implements BlockEntityRenderer<CraftingForgeBlockEntity> {
    private static final int MAX_ITEMS = 4;
    private static final float[][] SPOTS = {{5.5F, 6.5F}, {10.5F, 6.5F}, {5.5F, 11.5F}, {10.5F, 11.5F}};

    private record Cached(int version, BlockState state, List<Component> lines, List<ItemStack> items) {}
    private final WeakHashMap<CraftingForgeBlockEntity, Cached> cache = new WeakHashMap<>();
    private final ItemRenderer itemRenderer;

    public CraftingForgeRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(CraftingForgeBlockEntity f, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (f.getLevel() == null || !FloatingText.near(f.getBlockPos(), FloatingText.ITEM_RANGE)) {
            return;
        }
        Cached c = cache.get(f);
        if (c == null || c.version != f.clientVersion() || c.state != f.getBlockState()) {
            c = new Cached(f.clientVersion(), f.getBlockState(), lines(f), items(f));
            cache.put(f, c);
        }
        for (int i = 0; i < c.items.size(); i++) {
            ItemStack s = c.items.get(i);
            pose.pushPose();
            pose.translate(SPOTS[i][0] / 16.0, 1.0 + 0.002, SPOTS[i][1] / 16.0);
            pose.mulPose(Axis.YP.rotationDegrees(i * 53.0F + 20.0F));
            if (s.getItem() instanceof BlockItem) {
                pose.translate(0, 0.1, 0);
                pose.scale(0.3F, 0.3F, 0.3F);
            } else {
                pose.translate(0, 0.03, 0);
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                pose.scale(0.35F, 0.35F, 0.35F);
            }
            itemRenderer.renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                    f.getLevel(), (int) f.getBlockPos().asLong() + i);
            pose.popPose();
        }
        if (FloatingText.focused(f.getBlockPos(), 1.0)) {
            float sc = FloatingText.labelScale(f.getBlockPos());
            double gap = FloatingText.LINE * sc / FloatingText.SMALL;
            double y = 1.2 + gap * (c.lines.size() - 1);
            for (Component line : c.lines) {
                FloatingText.draw(pose, buffers, line, 0.5, y, 0.5, sc, light);
                y -= gap;
            }
        }
    }

    private static List<ItemStack> items(CraftingForgeBlockEntity f) {
        List<ItemStack> out = new ArrayList<>();
        if (f.hasOutput()) {
            for (ItemStack s : f.output()) {
                if (out.size() < MAX_ITEMS) out.add(s.copyWithCount(1));
            }
            return out;
        }
        for (ItemStack s : f.parts()) {
            if (out.size() < MAX_ITEMS) out.add(s.copyWithCount(1));
        }
        for (ResourceLocation metal : f.pool().keySet()) {
            ItemStack ingot = Metals.ingot(metal, Math.max(1, f.grams(metal)));
            if (out.size() < MAX_ITEMS && !ingot.isEmpty()) out.add(ingot);
        }
        return out;
    }

    static List<Component> lines(CraftingForgeBlockEntity f) {
        List<Component> lines = new ArrayList<>();
        BlockState state = f.getBlockState();
        if (f.hasOutput()) {
            lines.add(Component.translatable("text.holeydigsite.crafting_forge.done").withStyle(ChatFormatting.GREEN));
            MutableComponent l = Component.empty();
            List<ItemStack> out = f.output();
            for (int i = 0; i < out.size() && i < 4; i++) {
                if (i > 0) l.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY));
                ItemStack s = out.get(i);
                ResourceLocation metal = CraftingForgeBlockEntity.ingotMetal(s);
                if (metal != null) {   // leftover ingot, short: "37 g Folkiron"
                    l.append(Component.translatable("text.holeydigsite.crafting_forge.ingot", com.holeydigsite.item.IngotItem.grams(s), Metals.name(metal)));
                } else {
                    l.append(s.getCount() > 1 ? Component.translatable("text.holeydigsite.crafting_forge.count", s.getCount(), s.getHoverName())
                            : s.getHoverName());
                }
            }
            if (out.size() > 4) l.append(Component.literal(" ..."));
            lines.add(l.withStyle(ChatFormatting.WHITE));
            return lines;
        }
        List<ForgeRecipes.Entry> all = ForgeRecipes.all(f.getLevel().registryAccess());
        ForgeRecipes.Entry e = f.recipe();
        if (e == null) {
            lines.add(Component.translatable("text.holeydigsite.crafting_forge.no_recipes").withStyle(ChatFormatting.RED));
            return lines;
        }
        ForgeRecipe r = e.recipe();
        lines.add(Component.translatable("text.holeydigsite.crafting_forge.recipe", r.name(f.getLevel().registryAccess()), all.indexOf(e) + 1, all.size())
                .withStyle(ChatFormatting.GOLD));
        // metals: required ones have/needed, extra loaded metals in grey
        MutableComponent metals = Component.empty();
        boolean first = true;
        for (Map.Entry<ResourceLocation, Integer> m : r.metals().entrySet()) {
            int have = f.grams(m.getKey());
            if (!first) metals.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
            metals.append(Component.translatable("text.holeydigsite.crafting_forge.metal", Metals.name(m.getKey()), have, m.getValue())
                    .withStyle(have >= m.getValue() ? ChatFormatting.GREEN : ChatFormatting.RED));
            first = false;
        }
        for (Map.Entry<ResourceLocation, Integer> m : f.pool().entrySet()) {
            if (r.grams(m.getKey()) == 0 && m.getValue() > 0) {
                if (!first) metals.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
                metals.append(Component.translatable("text.holeydigsite.crafting_forge.extra_metal", Metals.name(m.getKey()), m.getValue())
                        .withStyle(ChatFormatting.GRAY));
                first = false;
            }
        }
        if (!first) lines.add(metals);
        // parts
        MutableComponent parts = Component.empty();
        first = true;
        List<Item> shown = new ArrayList<>();
        for (ForgeRecipe.Part p : r.parts()) {
            if (shown.contains(p.item())) continue;
            shown.add(p.item());
            int need = r.partCount(p.item());
            int have = f.partCount(p.item());
            if (!first) parts.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
            parts.append(Component.translatable("text.holeydigsite.crafting_forge.part", new ItemStack(p.item()).getHoverName(), have, need)
                    .withStyle(have >= need ? ChatFormatting.GREEN : ChatFormatting.RED));
            first = false;
        }
        for (ItemStack s : f.parts()) {
            if (shown.contains(s.getItem())) continue;
            shown.add(s.getItem());
            if (!first) parts.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
            parts.append(Component.translatable("text.holeydigsite.crafting_forge.extra_part", f.partCount(s.getItem()), s.getHoverName())
                    .withStyle(ChatFormatting.GRAY));
            first = false;
        }
        if (!first) lines.add(parts);
        // status
        if (f.isCasting() || state.getValue(CraftingForgeBlock.CASTING)) {
            lines.add(Component.translatable(state.getValue(CraftingForgeBlock.LIT)
                    ? "text.holeydigsite.crafting_forge.casting" : "text.holeydigsite.crafting_forge.paused").withStyle(ChatFormatting.GOLD));
        } else if (!state.getValue(CraftingForgeBlock.LIT)) {
            lines.add(Component.translatable("text.holeydigsite.crafting_forge.unlit").withStyle(ChatFormatting.RED));
        } else if (f.missing(r) == null) {
            lines.add(Component.translatable("text.holeydigsite.crafting_forge.ready").withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }
}
