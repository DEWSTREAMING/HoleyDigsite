package com.holeydigsite.crate;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Locked crates turning up while you work: hand panning, a sluice's tray, a drill's output and the scooping shovel. The chances
 * are the {@code finds} block of each collection (one in N per scoop, clump or scoop). Only players with the crate switch on
 * find them; a machine uses the switch of its owner, and a machine without an online owner finds nothing.
 */
public final class CrateFinds {
    private CrateFinds() {}

    private static volatile int testOneIn;
    private static long rolls;

    /** Test hook: every source finds at this "one in N" in every collection that can be found there (0 = use the data). */
    public static void setTestOneIn(int oneIn) {
        testOneIn = oneIn;
    }

    /** How many find rolls were made since the server started (for tests). */
    public static long rolls() {
        return rolls;
    }

    public static boolean allowed(@Nullable Player player) {
        return player != null && LedgerData.get(player).cratesOn();
    }

    /** Rolls one source and returns a locked crate, or empty. Rarer finds are tried first. */
    public static ItemStack roll(ServerLevel level, BlockPos pos, String source, @Nullable Player owner) {
        if (!allowed(owner)) return ItemStack.EMPTY;
        rolls++;
        List<CrateDefs.CrateTier> tiers = new ArrayList<>();
        for (CrateDefs.CrateTier t : CrateDefs.tiers().values()) {
            if (!t.old() && (testOneIn > 0 ? t.found() : t.findOneIn(source) > 0)) tiers.add(t);
        }
        tiers.sort((a, b) -> Integer.compare(b.findOneIn(source), a.findOneIn(source)));
        for (CrateDefs.CrateTier t : tiers) {
            int oneIn = testOneIn > 0 ? testOneIn : t.findOneIn(source);
            if (!t.site().isEmpty() && !inSite(level, pos, t.site())) continue;
            if (level.random.nextInt(oneIn) == 0) {
                Item item = BuiltInRegistries.ITEM.get(HoleyDigsite.id(t.id()));
                if (item != Items.AIR) return CrateBlock.stack(item, true);
            }
        }
        return ItemStack.EMPTY;
    }

    /** The site filter of a crate file: a site id (or its path), or {@code event_island} for anywhere on an event island. */
    private static boolean inSite(ServerLevel level, BlockPos pos, String site) {
        if (site.equals("event_island")) return com.holeydigsite.event.EventIsland.islandAt(level, pos) != null;
        SiteData data = SiteData.getIfExists(level);
        if (data == null) return false;
        for (SiteData.Site s : data.all()) {
            if (s.zone().isInside(pos) && (s.id().toString().equals(site) || s.id().getPath().equals(site))) return true;
        }
        return false;
    }

    /** Hand panning: the find pops out beside the pan. Returns true if one was found. */
    public static boolean handFind(ServerLevel level, BlockPos pos, Player player) {
        ItemStack crate = roll(level, pos, "pan", player);
        if (crate.isEmpty()) return false;
        com.holeydigsite.panning.Panning.popOut(level, pos, crate, player);
        found(level, pos, player);
        return true;
    }

    /** The scooping shovel: the find drops where the block was. */
    public static boolean shovelFind(ServerLevel level, BlockPos pos, Player player) {
        ItemStack crate = roll(level, pos, "shovel", player);
        if (crate.isEmpty()) return false;
        com.holeydigsite.panning.Panning.popOut(level, pos, crate, player);
        found(level, pos, player);
        return true;
    }

    private static void found(ServerLevel level, BlockPos pos, Player player) {
        player.displayClientMessage(Component.translatable("message.holeydigsite.crate.found"), true);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    /** A sluice's wash: the crate for the tray, or empty. */
    public static ItemStack sluiceFind(ServerLevel level, BlockPos pos, @Nullable ServerPlayer owner) {
        return roll(level, pos, "sluice", owner);
    }

    /**
     * A drill's finished clump: the roll for a crate that takes the place of the drill's next output. The drill holds it and sends it
     * out through its normal output like a clump; it is never dropped into the world.
     */
    public static ItemStack drillFind(ServerLevel level, BlockPos drillPos, @Nullable UUID owner) {
        if (owner == null) return ItemStack.EMPTY;
        ServerPlayer sp = level.getServer().getPlayerList().getPlayer(owner);
        return roll(level, drillPos, "drill", sp);
    }

    public static ItemStack cutters(int count) {
        return new ItemStack(ModItems.BOLT_CUTTERS.get(), count);
    }
}
