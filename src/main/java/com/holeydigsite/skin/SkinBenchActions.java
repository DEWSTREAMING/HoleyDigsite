package com.holeydigsite.skin;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the Skin Bench does with kits, server side. A skin is always in exactly one place: the kit is used up in the same step
 * the machine item gets the skin, and a skin taken off comes back as a kit (inventory, or at the feet when it is full).
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class SkinBenchActions {
    private SkinBenchActions() {}

    /** How long the second sneak-use has to scrap a kit. */
    public static final int SCRAP_CONFIRM_TICKS = 60;

    public enum Outcome { APPLIED, SWAPPED, REMOVED, SCRAP_ASKED, SCRAPPED, NO_MACHINE, WRONG_FAMILY, SAME_SKIN, NO_SKIN, NOT_A_KIT }

    private record PendingScrap(BlockPos pos, String skin, String family, String wear, long until) {}

    private static final Map<UUID, PendingScrap> PENDING_SCRAP = new HashMap<>();

    @Nullable
    private static String skinOn(ItemStack machine) {
        String s = machine.get(ModDataComponents.SKIN.get());
        return s == null || s.isEmpty() || SkinDefs.DEFAULT_ID.equals(s) ? null : s;
    }

    private static void say(ServerPlayer p, Component msg) {
        p.displayClientMessage(msg, true);
    }

    /** Puts one kit from {@code kit} (a stack in the player's inventory) on the machine on the bench. */
    public static Outcome apply(ServerPlayer p, SkinBenchBlockEntity bench, ItemStack kit) {
        if (!SkinKitItem.isKit(kit)) {
            say(p, Component.translatable("message.holeydigsite.skin_bench.blank"));
            return Outcome.NOT_A_KIT;
        }
        if (!bench.hasItem()) {
            say(p, Component.translatable("message.holeydigsite.skin_bench.no_machine"));
            return Outcome.NO_MACHINE;
        }
        ItemStack machine = bench.getItem();
        String family = SkinDefs.getMachineFamily(machine);
        String skin = SkinKitItem.skin(kit);
        String kitFamily = SkinKitItem.family(kit);
        String wear = SkinKitItem.wear(kit);
        SkinDefs.SkinDef def = SkinDefs.skin(skin);
        if (family == null || !family.equals(kitFamily) || (def != null && !def.appliesTo(family))) {
            say(p, Component.translatable("message.holeydigsite.skin_bench.wrong_family",
                    Component.translatable("skin_family.holeydigsite." + kitFamily + ".machine")).withStyle(ChatFormatting.RED));
            return Outcome.WRONG_FAMILY;
        }
        String oldSkin = skinOn(machine);
        String oldWear = SkinWear.normalize(SkinWear.of(machine));
        if (skin.equals(oldSkin) && wear.equals(oldWear)) {
            say(p, Component.translatable("message.holeydigsite.skin_bench.same"));
            return Outcome.SAME_SKIN;
        }
        ItemStack updated = machine.copy();
        updated.set(ModDataComponents.SKIN.get(), skin);
        SkinWear.set(updated, wear);
        kit.shrink(1);
        bench.setItem(updated);
        Component name = SkinDefs.displayName(skin);
        if (oldSkin != null) {
            LedgerManager.giveOrDrop(p, SkinKitItem.of(oldSkin, family, oldWear, 1));
            say(p, Component.translatable("message.holeydigsite.skin_bench.swapped", name));
        } else {
            say(p, Component.translatable("message.holeydigsite.skin_bench.applied", name, SkinWear.displayName(wear)));
        }
        p.level().playSound(null, bench.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6F, 1.2F);
        return oldSkin != null ? Outcome.SWAPPED : Outcome.APPLIED;
    }

    /** Takes the skin off the machine on the bench; it comes back as a kit. */
    public static Outcome remove(ServerPlayer p, SkinBenchBlockEntity bench) {
        if (!bench.hasItem()) {
            say(p, Component.translatable("message.holeydigsite.skin_bench.no_machine"));
            return Outcome.NO_MACHINE;
        }
        ItemStack machine = bench.getItem();
        String family = SkinDefs.getMachineFamily(machine);
        String oldSkin = skinOn(machine);
        if (family == null || oldSkin == null) {
            say(p, Component.translatable("message.holeydigsite.skin_bench.no_skin"));
            return Outcome.NO_SKIN;
        }
        String oldWear = SkinWear.normalize(SkinWear.of(machine));
        ItemStack updated = machine.copy();
        updated.remove(ModDataComponents.SKIN.get());
        updated.remove(ModDataComponents.SKIN_WEAR.get());
        bench.setItem(updated);
        LedgerManager.giveOrDrop(p, SkinKitItem.of(oldSkin, family, oldWear, 1));
        say(p, Component.translatable("message.holeydigsite.skin_bench.removed"));
        p.level().playSound(null, bench.getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
        return Outcome.REMOVED;
    }

    /** First sneak-use asks, a second one on the same bench with the same kit within a few seconds scraps one kit. */
    public static Outcome scrap(ServerPlayer p, ItemStack kit, BlockPos pos) {
        if (!SkinKitItem.isKit(kit)) {
            say(p, Component.translatable("message.holeydigsite.skin_bench.blank"));
            return Outcome.NOT_A_KIT;
        }
        String skin = SkinKitItem.skin(kit);
        String family = SkinKitItem.family(kit);
        String wear = SkinKitItem.wear(kit);
        int n = SkinKitItem.scrapValue(kit);
        long now = p.level().getGameTime();
        PendingScrap pending = PENDING_SCRAP.get(p.getUUID());
        if (pending != null && now <= pending.until() && pending.pos().equals(pos)
                && pending.skin().equals(skin) && pending.family().equals(family) && pending.wear().equals(wear)) {
            PENDING_SCRAP.remove(p.getUUID());
            kit.shrink(1);
            LedgerManager.giveCountOrDrop(p, new ItemStack(ModItems.SKIN_SCRAP.get()), n);
            say(p, Component.translatable("message.holeydigsite.skin_bench.scrapped", n));
            p.level().playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7F, 1.0F);
            return Outcome.SCRAPPED;
        }
        PENDING_SCRAP.put(p.getUUID(), new PendingScrap(pos.immutable(), skin, family, wear, now + SCRAP_CONFIRM_TICKS));
        say(p, Component.translatable("message.holeydigsite.skin_bench.scrap_confirm", n).withStyle(ChatFormatting.YELLOW));
        return Outcome.SCRAP_ASKED;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_SCRAP.remove(event.getEntity().getUUID());
    }
}
