package com.holeydigsite.client;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.skin.SkinKitItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skin Kit icon: the kit draws as its family's machine in that skin and wear, a bit smaller and up to the right, with a small
 * paint tin in the bottom-left corner of the slot (see {@link #DECORATOR}). A blank kit keeps its own flat tin texture.
 */
public class SkinKitModel extends BakedModelWrapper<BakedModel> {
    private static final ResourceLocation TIN = HoleyDigsite.id("textures/gui/skin_kit_tin.png");

    private final Map<String, ItemStack> machines = new ConcurrentHashMap<>();
    private final Map<BakedModel, Shrunk> shrunk = new ConcurrentHashMap<>();
    private final ItemOverrides overrides = new ItemOverrides() {
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            if (!SkinKitItem.isKit(stack)) {
                return originalModel;
            }
            String key = SkinKitItem.skin(stack) + "|" + SkinKitItem.family(stack) + "|" + SkinKitItem.wear(stack);
            ItemStack machine = machines.computeIfAbsent(key, k -> SkinKitItem.machineIcon(stack));
            BakedModel inner = Minecraft.getInstance().getItemRenderer().getModel(machine, level, entity, seed);
            return shrunk.computeIfAbsent(inner, Shrunk::new);
        }
    };

    public SkinKitModel(BakedModel original) {
        super(original);
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    /** The machine model, scaled down so the tin and the slot edge stay visible. */
    private static final class Shrunk extends BakedModelWrapper<BakedModel> {
        Shrunk(BakedModel original) {
            super(original);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand) {
            if (ctx == ItemDisplayContext.GUI) {
                pose.translate(0.1F, 0.1F, 0.0F);
                pose.scale(0.78F, 0.78F, 0.78F);
            } else {
                pose.scale(0.6F, 0.6F, 0.6F);
            }
            originalModel.applyTransform(ctx, pose, leftHand);
            return this;
        }
    }

    public static final IItemDecorator DECORATOR = new IItemDecorator() {
        @Override
        public boolean render(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
            if (!SkinKitItem.isKit(stack)) {
                return false;
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.blit(TIN, x, y + 8, 0, 0, 8, 8, 8, 8);
            g.pose().popPose();
            return false;
        }
    };
}
