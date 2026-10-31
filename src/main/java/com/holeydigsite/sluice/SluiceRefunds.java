package com.holeydigsite.sluice;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat notes about old Tier 2 sluices that were handed back in a chest ({@link LongSluiceBlock#refundOld}). The owner
 * gets the line at once when online, otherwise at the next login.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public class SluiceRefunds extends SavedData {
    private static final String NAME = "holeydigsite_sluice_refunds";
    private final Map<UUID, List<String>> waiting = new LinkedHashMap<>();

    public static SluiceRefunds get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SluiceRefunds::new, SluiceRefunds::load, null), NAME);
    }

    static String posText(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** Tells the owner now if online, or keeps the note for the next login. */
    public static void tell(MinecraftServer server, UUID owner, BlockPos pos) {
        ServerPlayer sp = server.getPlayerList().getPlayer(owner);
        if (sp != null) {
            sp.sendSystemMessage(line(posText(pos)));
            return;
        }
        queue(server, owner, pos);
    }

    /** Keeps a note for the player's next login. */
    public static void queue(MinecraftServer server, UUID owner, BlockPos pos) {
        SluiceRefunds data = get(server);
        data.waiting.computeIfAbsent(owner, k -> new ArrayList<>()).add(posText(pos));
        data.setDirty();
    }

    /** Notes waiting for a player (read-only, for tests). */
    public static List<String> waitingFor(MinecraftServer server, UUID owner) {
        return List.copyOf(get(server).waiting.getOrDefault(owner, List.of()));
    }

    public static Component line(String pos) {
        return Component.translatable("chat.holeydigsite.sluice.refunded", pos);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.getServer() == null) {
            return;
        }
        SluiceRefunds data = get(sp.getServer());
        List<String> notes = data.waiting.remove(sp.getUUID());
        if (notes != null) {
            data.setDirty();
            for (String n : notes) {
                sp.sendSystemMessage(line(n));
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, List<String>> e : waiting.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Player", e.getKey());
            ListTag notes = new ListTag();
            for (String n : e.getValue()) {
                notes.add(net.minecraft.nbt.StringTag.valueOf(n));
            }
            p.put("Notes", notes);
            players.add(p);
        }
        tag.put("Waiting", players);
        return tag;
    }

    static SluiceRefunds load(CompoundTag tag, HolderLookup.Provider registries) {
        SluiceRefunds data = new SluiceRefunds();
        for (Tag t : tag.getList("Waiting", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            List<String> notes = new ArrayList<>();
            for (Tag n : p.getList("Notes", Tag.TAG_STRING)) {
                notes.add(n.getAsString());
            }
            data.waiting.put(p.getUUID("Player"), notes);
        }
        return data;
    }
}
