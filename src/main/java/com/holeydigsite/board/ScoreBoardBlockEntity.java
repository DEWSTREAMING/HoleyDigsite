package com.holeydigsite.board;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.event.EventDefs;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Page and sort of a {@link ScoreBoardBlock}. The table itself is worked out on the server whenever the board is sent to a
 * client (on load, on use, and when a client looking at it asks, see {@link BoardNetwork}).
 */
public class ScoreBoardBlockEntity extends BlockEntity {
    public static final int ROWS = 10;
    private int page;
    private boolean byChips;
    /** Client: the lines last sent. */
    private List<Component> lines = List.of();

    public ScoreBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCORE_BOARD.get(), pos, state);
    }

    public ScoreBoardBlock.Kind kind() {
        return getBlockState().getBlock() instanceof ScoreBoardBlock b ? b.kind() : ScoreBoardBlock.Kind.WORLD;
    }

    public int page() {
        return page;
    }

    public boolean byChips() {
        return byChips;
    }

    public List<Component> lines() {
        return lines;
    }

    public void nextPage() {
        page++;
        changed();
    }

    public void toggleSort() {
        byChips = !byChips;
        changed();
    }

    public void resend() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void changed() {
        setChanged();
        resend();
    }

    // ------------------------------------------------------------------------------------------------ the table

    /** The event ids this board pages through: the active event first, then the others, latest first. */
    public static List<String> eventPages(BoardStats stats) {
        List<String> out = new ArrayList<>();
        String active = EventDefs.getActiveEventId();
        if (active != null) {
            out.add(active);
        }
        List<String> past = new ArrayList<>(stats.events());
        java.util.Collections.reverse(past);
        for (String e : past) {
            if (!e.equals(active)) {
                out.add(e);
            }
        }
        return out;
    }

    private static Component eventName(String id) {
        EventDefs.EventDef def = EventDefs.getEvent(id);
        return Component.translatableWithFallback(def != null ? def.name() : "event.holeydigsite." + id, id);
    }

    /** The lines shown, worked out from the world's {@link BoardStats}. */
    public List<Component> buildLines(MinecraftServer server) {
        BoardStats stats = BoardStats.get(server);
        List<Component> out = new ArrayList<>();
        if (kind() == ScoreBoardBlock.Kind.EVENT) {
            List<String> pages = eventPages(stats);
            if (pages.isEmpty()) {
                out.add(Component.translatable("text.holeydigsite.board.no_event").withStyle(ChatFormatting.GOLD));
                return out;
            }
            int p = Math.floorMod(page, pages.size());
            String ev = pages.get(p);
            MutableComponent title = Component.translatable("text.holeydigsite.board.event_title", eventName(ev)).withStyle(ChatFormatting.GOLD);
            if (!ev.equals(EventDefs.getActiveEventId()) || stats.frozen(ev)) {
                title.append(Component.translatable("text.holeydigsite.board.ended").withStyle(ChatFormatting.GRAY));
            }
            out.add(title);
            out.add(Component.translatable(byChips ? "text.holeydigsite.board.by_chips" : "text.holeydigsite.board.by_clumps",
                    p + 1, pages.size()).withStyle(ChatFormatting.GRAY));
            List<BoardStats.Row> rows = stats.eventTop(ev, byChips, ROWS);
            for (int i = 0; i < rows.size(); i++) {
                BoardStats.Row r = rows.get(i);
                out.add(Component.translatable("text.holeydigsite.board.event_row", i + 1, r.name(), r.clumps(), r.chips()));
            }
            if (rows.isEmpty()) {
                out.add(Component.translatable("text.holeydigsite.board.empty").withStyle(ChatFormatting.GRAY));
            }
            out.add(Component.translatable("text.holeydigsite.board.event_help").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            List<Integer> kinds = new ArrayList<>();
            kinds.add(0);
            kinds.addAll(stats.kinds());
            int p = Math.floorMod(page, kinds.size());
            int kind = kinds.get(p);
            out.add((kind == 0 ? Component.translatable("text.holeydigsite.board.coins_title")
                    : Component.translatable("text.holeydigsite.board.clumps_title", DirtTier.clumps(kind, 1).getHoverName()))
                    .withStyle(ChatFormatting.GOLD));
            out.add(Component.translatable("text.holeydigsite.board.page", p + 1, kinds.size()).withStyle(ChatFormatting.GRAY));
            List<BoardStats.Row> rows = stats.worldTop(kind, ROWS);
            for (int i = 0; i < rows.size(); i++) {
                BoardStats.Row r = rows.get(i);
                out.add(kind == 0
                        ? Component.translatable("text.holeydigsite.board.coins_row", i + 1, r.name(), Selling.format(r.clumps()))
                        : Component.translatable("text.holeydigsite.board.clumps_row", i + 1, r.name(), r.clumps()));
            }
            if (rows.isEmpty()) {
                out.add(Component.translatable("text.holeydigsite.board.empty").withStyle(ChatFormatting.GRAY));
            }
            out.add(Component.translatable("text.holeydigsite.board.world_help").withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ save / sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Page", page);
        tag.putBoolean("ByChips", byChips);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        page = tag.getInt("Page");
        byChips = tag.getBoolean("ByChips");
        if (tag.contains("Lines")) {
            List<Component> got = new ArrayList<>();
            for (Tag t : tag.getList("Lines", Tag.TAG_STRING)) {
                Component c = Component.Serializer.fromJson(t.getAsString(), registries);
                if (c != null) {
                    got.add(c);
                }
            }
            lines = List.copyOf(got);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        if (level != null && level.getServer() != null) {
            ListTag list = new ListTag();
            for (Component c : buildLines(level.getServer())) {
                list.add(StringTag.valueOf(Component.Serializer.toJson(c, registries)));
            }
            tag.put("Lines", list);
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
