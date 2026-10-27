package com.holeydigsite.client;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.crate.CrateNetwork;
import com.holeydigsite.crate.CrateSpin;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The crate opening spin: a strip of icons that slows to a crawl and stops on the reward, drawn over the HUD while the game
 * keeps running. A click (use or attack) skips to the end and asks the server to give the reward at once. The strip is
 * decoration; the numbers (length, curve) are in {@link CrateSpin}.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID, value = Dist.CLIENT)
public final class CrateSpinOverlay {
    private CrateSpinOverlay() {}

    private static final int FADE_TICKS = 8;

    private static List<ItemStack> strip = List.of();
    private static List<String> rarities = List.of();
    private static ItemStack result = ItemStack.EMPTY;
    private static Component line = Component.empty();
    private static String rarity = "common";
    private static BlockPos crate = BlockPos.ZERO;
    private static int resultIndex;
    private static double endPos;
    private static int age = -1;
    private static boolean landed;
    private static int lastSlot;

    public static boolean active() {
        return age >= 0;
    }

    public static void start(CrateNetwork.OpenPayload payload) {
        strip = payload.strip().isEmpty() ? List.of(payload.icon()) : payload.strip();
        rarities = payload.rarities();
        result = payload.icon();
        crate = payload.pos();
        resultIndex = Mth.clamp(payload.resultIndex(), 0, strip.size() - 1);
        rarity = resultIndex < rarities.size() ? rarities.get(resultIndex) : "common";
        endPos = resultIndex + Mth.clamp(payload.stopFrac(), 0.0F, 0.999F);
        line = Component.empty();
        age = 0;
        landed = false;
        lastSlot = 0;
    }

    /** The server gave the reward: show what it was. */
    public static void result(CrateNetwork.ResultPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(payload.line(), false);
        if (age >= 0 && payload.pos().equals(crate)) line = payload.line();
    }

    static double position(double t) {
        return CrateSpin.position(t, CrateSpin.START, endPos);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (age < 0) return;
        if (Minecraft.getInstance().player == null) {
            age = -1;
            return;
        }
        age++;
        if (!landed) {
            double now = position(age);
            double before = position(age - 1);
            if ((int) now != (int) before) {
                double speed = now - before;
                play(SoundEvents.NOTE_BLOCK_HAT.value(), Mth.clamp((float) (0.7 + speed * 1.1), 0.7F, 1.9F), 0.3F);
            }
            if (age >= CrateSpin.SPIN_TICKS) land();
        } else if (age >= CrateSpin.SPIN_TICKS + CrateSpin.HOLD_TICKS) {
            age = -1;
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (age >= 0 && !landed) {
            age = CrateSpin.SPIN_TICKS;
            land();
            PacketDistributor.sendToServer(new CrateNetwork.SkipPayload(crate));
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void land() {
        landed = true;
        switch (rarity) {
            case "rare" -> play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
            case "epic" -> play(SoundEvents.PLAYER_LEVELUP, 1.2F, 0.8F);
            case "legendary" -> play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.9F);
            default -> play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.8F);
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Mth.clamp((int) ((argb >>> 24) * alpha), 0, 255);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (age < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        DeltaTracker dt = event.getPartialTick();
        float partial = dt.getGameTimeDeltaPartialTick(false);
        double t = landed ? CrateSpin.SPIN_TICKS : Math.min(CrateSpin.SPIN_TICKS, age + partial);
        double pos = position(t);
        float fade = Mth.clamp((age + partial) / FADE_TICKS, 0F, 1F);
        float out = landed ? Mth.clamp((CrateSpin.SPIN_TICKS + CrateSpin.HOLD_TICKS - age - partial) / 10F, 0F, 1F) : 1F;
        float alpha = Math.min(fade, out);
        if (alpha <= 0.02F) return;

        int gw = g.guiWidth();
        int gh = g.guiHeight();
        // slots shrink on a narrow screen; the panel always fits with a margin
        int slot = Mth.clamp((gw - 16) / 9, 20, 40);
        int icon = slot * 3 / 4;
        float scale = icon / 16F;
        int barH = Math.max(2, slot / 12);
        int half = Math.min(slot * 9 / 2, gw / 2 - 4);
        int panelH = slot + barH + 6;
        int cx = gw / 2;
        int textRoom = 34;
        int cy = Mth.clamp(gh / 2 - 6, panelH / 2 + 4, Math.max(panelH / 2 + 4, gh - panelH / 2 - textRoom - 4));
        int top = cy - panelH / 2;
        int bottom = cy + panelH / 2;
        int color = rarityColor(rarity);

        g.fill(cx - half - 2, top - 2, cx + half + 2, bottom + 2, withAlpha(landed ? color : 0xFF6A5C44, alpha));
        g.fill(cx - half, top, cx + half, bottom, withAlpha(0xF0161210, alpha));

        g.enableScissor(cx - half, top, cx + half, bottom);
        int reach = half / slot + 2;
        int first = Math.max(0, (int) Math.floor(pos) - reach);
        int last = Math.min(strip.size() - 1, (int) Math.floor(pos) + reach);
        float pop = 0.6F + 0.4F * fade;
        for (int i = first; i <= last; i++) {
            int left = (int) Math.round(cx + (i - pos) * slot);
            int sTop = top + 3;
            String r = i < rarities.size() ? rarities.get(i) : "common";
            int rc = rarityColor(r);
            boolean win = landed && i == resultIndex;
            if (win) {
                g.fill(left + 1, sTop, left + slot - 1, sTop + slot + barH, withAlpha(rc & 0xFFFFFF | 0x66000000, alpha));
            } else {
                g.fill(left + 1, sTop, left + slot - 1, sTop + slot, withAlpha(0x30FFFFFF, alpha));
            }
            g.fill(left + 1, sTop + slot, left + slot - 1, sTop + slot + barH, withAlpha(rc, alpha));
            if (fade >= 0.35F) {
                g.pose().pushPose();
                float s = scale * pop * (win ? 1.12F : 1F);
                float ix = left + (slot - 16 * s) / 2F;
                float iy = sTop + (slot - 16 * s) / 2F;
                g.pose().translate(ix, iy, 0);
                g.pose().scale(s, s, 1F);
                g.renderItem(strip.get(i), 0, 0);
                g.pose().popPose();
            }
        }
        g.disableScissor();

        // marker: a line through the middle with a notch at each end
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        int mc2 = withAlpha(0xFFFFFFFF, alpha);
        g.fill(cx - 1, top - 2, cx + 1, bottom + 2, withAlpha(landed ? color : 0xFFE8D8A8, alpha));
        g.fill(cx - 4, top - 2, cx + 4, top, mc2);
        g.fill(cx - 4, bottom, cx + 4, bottom + 2, mc2);
        g.pose().popPose();

        int textY = bottom + 8;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        if (landed) {
            g.drawCenteredString(mc.font, result.getHoverName(), cx, textY, withAlpha(color, alpha));
            if (!line.getString().isEmpty()) {
                g.drawCenteredString(mc.font, line, cx, textY + 12, withAlpha(0xFFEAE2D6, alpha));
            }
        } else {
            g.drawCenteredString(mc.font, Component.translatable("screen.holeydigsite.crate.skip"), cx, textY, withAlpha(0xFF9E9489, alpha));
        }
        g.pose().popPose();
    }

    static int rarityColor(String rarity) {
        return switch (rarity) {
            case "rare" -> 0xFF4D7CFF;
            case "epic" -> 0xFFB04DFF;
            case "legendary" -> 0xFFFFAA00;
            default -> 0xFFC8C8C8;
        };
    }
}
