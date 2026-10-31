package com.holeydigsite.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.board.BoardStats;
import com.holeydigsite.plot.PlotBoardBlockEntity;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.Sites;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The event island: its own island next to each copy of the home island, with three tier sections (plots with deeds), the event
 * kiosk and a frame (ground, gate, bridge, windmill). Where things go is data ({@code data/holeydigsite/event_island.json},
 * written by {@code tools/make_world.py}), relative to the island template's origin, which is the home site's anchor minus
 * {@code home}.
 *
 * <p>Placing never overwrites anything: blocks only go into air. A new island gets it right after its home site is built; old
 * worlds get it from a {@link com.holeydigsite.site.WorldVersion} step. When the active event changes, the sections (owned or
 * not) are rebuilt for the new event: whatever players placed goes into chests at the section's gate, holes are filled and
 * ownership stays.</p>
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class EventIsland {
    private EventIsland() {}

    /** Blocks a section is made of; anything else found in a section on a rebuild is a player's and goes into a chest. */
    public static final TagKey<Block> SECTION_BLOCKS = TagKey.create(Registries.BLOCK, HoleyDigsite.id("event_section_blocks"));
    /** The rock hanging under the island (below the bedrock layer, {@link #UNDERSIDE} rows), also part of a section. */
    public static final TagKey<Block> UNDERSIDE_BLOCKS = TagKey.create(Registries.BLOCK, HoleyDigsite.id("event_underside_blocks"));
    public static final int UNDERSIDE = 4;

    public record Section(int tier, String plot, BlockPos pos, Vec3i size, BlockPos board, BlockPos gate, List<BlockPos> chests) {
        public BoundingBox box(BlockPos origin) {
            BlockPos a = origin.offset(pos);
            return new BoundingBox(a.getX(), a.getY(), a.getZ(), a.getX() + size.getX() - 1, a.getY() + size.getY() - 1,
                    a.getZ() + size.getZ() - 1);
        }

        public BlockPos boardAt(BlockPos origin) {
            return origin.offset(pos).offset(board);
        }
    }

    public record Layout(BlockPos home, String side, ResourceLocation frame, BlockPos framePos, Vec3i frameSize,
                         ResourceLocation shopSite, BlockPos shopPos, List<Section> sections, BlockPos board, List<BlockPos> frameChests) {
        public BoundingBox frameBox(BlockPos origin) {
            BlockPos a = origin.offset(framePos);
            return new BoundingBox(a.getX(), a.getY(), a.getZ(), a.getX() + frameSize.getX() - 1, a.getY() + frameSize.getY() - 1,
                    a.getZ() + frameSize.getZ() - 1);
        }

        @Nullable
        public Section section(int tier) {
            for (Section s : sections) {
                if (s.tier() == tier) {
                    return s;
                }
            }
            return null;
        }
    }

    private static volatile Layout LAYOUT = null;

    @Nullable
    public static Layout layout() {
        return LAYOUT;
    }

    // ------------------------------------------------------------------------------------------------ placing

    /** What a placement did. */
    public record Placed(BlockPos origin, int blocked, @Nullable BlockPos firstBlocked) {}

    public static BlockPos originFor(BlockPos homeAnchor) {
        Layout l = LAYOUT;
        return l == null ? homeAnchor : homeAnchor.subtract(l.home());
    }

    /** True on the GameTest server, where tests place event islands themselves (the test sites must not grow islands). */
    private static boolean isTestServer(MinecraftServer server) {
        return server instanceof net.minecraft.gametest.framework.GameTestServer;
    }

    /** A home site was built from its marker (a new island): give it its event island. */
    public static void onHomeBuilt(ServerLevel level, BlockPos homeAnchor) {
        if (level.getServer() == null || isTestServer(level.getServer())) {
            return;
        }
        Placed p = ensure(level, homeAnchor);
        if (p != null && p.blocked() > 0) {
            announce(level, p, true);
        }
    }

    /** WorldVersion step: every existing island copy in every level gets its event island (air only), with one chat line each. */
    public static int addToAllIslands(MinecraftServer server) {
        int n = 0;
        for (ServerLevel level : server.getAllLevels()) {
            SiteData sites = SiteData.getIfExists(level);
            if (sites == null) {
                continue;
            }
            for (SiteData.Site s : sites.byId(HoleyDigsite.id("home"))) {
                Placed p = ensure(level, s.anchor());
                if (p != null) {
                    announce(level, p, p.blocked() > 0);
                    n++;
                }
            }
        }
        return n;
    }

    /** Places the event island for the home site at {@code homeAnchor} unless it has one. Returns null if nothing was placed. */
    @Nullable
    public static Placed ensure(ServerLevel level, BlockPos homeAnchor) {
        Layout l = LAYOUT;
        EventDefs.EventDef ev = EventDefs.getActiveEvent();
        if (l == null || ev == null) {
            HoleyDigsite.LOGGER.warn("No event island placed at {}: {}", homeAnchor, l == null ? "no layout" : "no active event");
            return null;
        }
        BlockPos origin = originFor(homeAnchor);
        if (EventIslandData.get(level).get(origin) != null) {
            return null;
        }
        return place(level, origin, ev);
    }

    /** Places the kiosk, the frame and the sections for {@code ev} at the template origin, into air only, and records the island. */
    public static Placed place(ServerLevel level, BlockPos origin, EventDefs.EventDef ev) {
        Layout l = LAYOUT;
        List<BlockPos> blocked = new ArrayList<>();
        // the kiosk first: the event board hangs on its wall
        SiteDef shop = SiteDefs.get(l.shopSite());
        if (shop != null) {
            BlockPos anchor = origin.offset(l.shopPos());
            if (placeAirOnly(level, shop.structure(), anchor, blocked)) {
                BoundingBox zone = Sites.zoneOf(anchor, shop);
                List<String> shown = shop.layout() != null ? Sites.applyLayout(level, zone, SiteDefs.layout(shop.layout())) : List.of();
                SiteData.get(level).put(new SiteData.Site(shop.id(), anchor.immutable(), zone, shop.version(), shown, null));
            }
        }
        placeAirOnly(level, l.frame(), origin.offset(l.framePos()), blocked);
        for (Section s : l.sections()) {
            EventDefs.EventTier t = ev.tier(s.tier());
            if (t == null || t.section() == null) {
                continue;
            }
            placeAirOnly(level, t.section(), origin.offset(s.pos()), blocked);
            registerBoards(level, s.box(origin));
        }
        EventIslandData.get(level).put(new EventIslandData.Island(origin.immutable(), ev.id()));
        HoleyDigsite.LOGGER.info("Event island for {} placed at {} ({} spots left alone)", ev.id(), origin, blocked.size());
        return new Placed(origin.immutable(), blocked.size(), blocked.isEmpty() ? null : blocked.get(0));
    }

    private static void registerBoards(ServerLevel level, BoundingBox box) {
        for (BlockEntity be : Sites.blockEntities(level, box)) {
            if (be instanceof PlotBoardBlockEntity b) {
                b.register();
            }
        }
    }

    /** Places a structure, but only into air; spots that hold something else are left and collected in {@code blocked}. */
    public static boolean placeAirOnly(ServerLevel level, ResourceLocation structure, BlockPos anchor, List<BlockPos> blocked) {
        Optional<StructureTemplate> t = level.getStructureManager().get(structure);
        if (t.isEmpty()) {
            HoleyDigsite.LOGGER.error("Event island: structure {} not found", structure);
            return false;
        }
        Vec3i size = t.get().getSize();
        for (int cx = anchor.getX() >> 4; cx <= (anchor.getX() + size.getX()) >> 4; cx++) {
            for (int cz = anchor.getZ() >> 4; cz <= (anchor.getZ() + size.getZ()) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true).addProcessor(new AirOnly(blocked));
        t.get().placeInWorld(level, anchor, anchor, settings, level.random, Block.UPDATE_CLIENTS);
        return true;
    }

    private static final class AirOnly extends StructureProcessor {
        private final List<BlockPos> blocked;

        AirOnly(List<BlockPos> blocked) {
            this.blocked = blocked;
        }

        @Nullable
        @Override
        public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
                                                                 StructureTemplate.StructureBlockInfo original,
                                                                 StructureTemplate.StructureBlockInfo current,
                                                                 StructurePlaceSettings settings) {
            BlockState now = level.getBlockState(current.pos());
            if (now.isAir()) {
                return current;
            }
            if (now.getBlock() != current.state().getBlock()) {
                blocked.add(current.pos().immutable());
            }
            return null;
        }

        @Override
        protected StructureProcessorType<?> getType() {
            return StructureProcessorType.NOP;
        }
    }

    private static void announce(ServerLevel level, Placed p, boolean blocked) {
        BlockPos gate = p.origin().offset(LAYOUT.framePos()).offset(LAYOUT.frameSize().getX() / 2, 10, LAYOUT.frameSize().getZ());
        Component msg = blocked
                ? Component.translatable("chat.holeydigsite.event_island.blocked", gate.getX(), gate.getZ(), p.blocked())
                : Component.translatable("chat.holeydigsite.event_island.placed", gate.getX(), gate.getZ());
        msg = msg.copy().withStyle(ChatFormatting.GOLD);
        SiteData.Announcement a = SiteData.get(level).announce(msg, level.getGameTime());
        if (level.getServer() != null) {
            for (ServerPlayer pl : level.getServer().getPlayerList().getPlayers()) {
                pl.sendSystemMessage(msg);
                pl.getPersistentData().putInt(com.holeydigsite.site.SiteEvents.SEEN,
                        Math.max(pl.getPersistentData().getInt(com.holeydigsite.site.SiteEvents.SEEN), a.serial()));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ lookups

    /** The island holding {@code pos}, else the nearest one in the level, or null when there are none. */
    @Nullable
    public static EventIslandData.Island islandAtOrNearest(ServerLevel level, BlockPos pos) {
        EventIslandData.Island at = islandAt(level, pos);
        if (at != null) {
            return at;
        }
        EventIslandData.Island best = null;
        double bd = Double.MAX_VALUE;
        for (EventIslandData.Island i : EventIslandData.get(level).all()) {
            double d = i.origin().distSqr(pos);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** The recorded event island whose frame holds {@code pos}, or null. */
    @Nullable
    public static EventIslandData.Island islandAt(ServerLevel level, BlockPos pos) {
        Layout l = LAYOUT;
        if (l == null) {
            return null;
        }
        for (EventIslandData.Island i : EventIslandData.get(level).all()) {
            if (l.frameBox(i.origin()).isInside(pos)) {
                return i;
            }
        }
        return null;
    }

    /** Tier of an event section plot id ({@code event_t2} = 2), or 0. */
    public static int tierOf(String plotId) {
        if (plotId != null && plotId.startsWith("event_t")) {
            try {
                return Integer.parseInt(plotId.substring(7));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    public static boolean isEventPlot(String plotId) {
        return tierOf(plotId) > 0;
    }

    /** True if the section of tier {@code tier} on the event island around {@code pos} is bought. */
    public static boolean sectionOwned(ServerLevel level, BlockPos pos, int tier) {
        EventIslandData.Island i = islandAt(level, pos);
        Section s = LAYOUT == null ? null : LAYOUT.section(tier);
        if (i == null || s == null) {
            return false;
        }
        PlotData.Plot p = PlotData.get(level).get(s.plot(), s.boardAt(i.origin()));
        return p != null && p.owned();
    }

    // ------------------------------------------------------------------------------------------------ event change

    /** World load: islands built for another event are rebuilt for the active one. Returns how many were. */
    public static int checkEventChange(ServerLevel level) {
        EventDefs.EventDef ev = EventDefs.getActiveEvent();
        if (ev == null || LAYOUT == null) {
            return 0;
        }
        int n = 0;
        BoardStats stats = level.getServer() == null ? null : BoardStats.get(level.getServer());
        for (EventIslandData.Island i : List.copyOf(EventIslandData.get(level).all())) {
            if (i.event().equals(ev.id())) {
                continue;
            }
            if (stats != null) {
                stats.freeze(i.event());
                stats.unfreeze(ev.id());
            }
            rebuild(level, i, ev);
            n++;
        }
        return n;
    }

    /** What a section rebuild did (tests). */
    public record Rebuilt(int sections, int stacks, int chests) {}

    /**
     * Rebuilds every section of the island for {@code ev}: players' blocks and everything in them go into chests at the
     * section's gate, the zone is cleared and the new section placed (holes filled, new pockets), and ownership stays.
     */
    public static Rebuilt rebuild(ServerLevel level, EventIslandData.Island island, EventDefs.EventDef ev) {
        Layout l = LAYOUT;
        int sections = 0, stacks = 0, chests = 0;
        for (Section s : l.sections()) {
            EventDefs.EventTier t = ev.tier(s.tier());
            if (t == null || t.section() == null) {
                continue;
            }
            Optional<StructureTemplate> template = level.getStructureManager().get(t.section());
            if (template.isEmpty()) {
                HoleyDigsite.LOGGER.error("Event island: structure {} not found", t.section());
                continue;
            }
            BoundingBox zone = s.box(island.origin());
            BlockPos anchor = island.origin().offset(s.pos());
            List<ItemStack> things = takePlayerThings(level, zone);
            Sites.clear(level, zone);
            for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(zone.minX(), zone.minY(), zone.minZ(), zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1))) {
                things.add(e.getItem().copy());
                e.discard();
            }
            template.get().placeInWorld(level, anchor, anchor, new StructurePlaceSettings().setIgnoreEntities(true), level.random,
                    Block.UPDATE_CLIENTS);
            registerBoards(level, zone);
            things.removeIf(ItemStack::isEmpty);
            stacks += things.size();
            chests += intoChests(level, anchor, s.chests(), anchor.offset(s.gate()), things);
            BlockPos gate = anchor.offset(s.gate());
            for (ServerPlayer p : level.players()) {
                if (zone.isInside(p.blockPosition())) {
                    p.teleportTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5);
                }
            }
            sections++;
        }
        EventIslandData.get(level).put(new EventIslandData.Island(island.origin(), ev.id()));
        HoleyDigsite.LOGGER.info("Event island at {} rebuilt for {}: {} stacks into {} chests", island.origin(), ev.id(), stacks, chests);
        return new Rebuilt(sections, stacks, chests);
    }

    // ------------------------------------------------------------------------------------------------ whole-island rebuild (op command)

    /** What a whole-island rebuild did: frame blocks put back to the template, chests filled with players' things. */
    public record WholeRebuild(int blocks, int chests, int stacks) {}

    /**
     * Rebuilds the whole event island to the current layout and active event: the kiosk (as a site rebuild, which keeps its
     * Money Pan and pads), the frame (everything not the template's block goes into chests near the kiosk, then the template is
     * placed in full; the Event Board moves back to its spot and keeps its page) and the sections (as on an event change).
     */
    public static WholeRebuild rebuildWhole(ServerLevel level, EventIslandData.Island island) {
        Layout l = LAYOUT;
        EventDefs.EventDef ev = EventDefs.getActiveEvent();
        if (l == null || ev == null) {
            return new WholeRebuild(0, 0, 0);
        }
        BlockPos origin = island.origin();
        SiteDef shop = SiteDefs.get(l.shopSite());
        BoundingBox kiosk = shop == null ? null : Sites.zoneOf(origin.offset(l.shopPos()), shop);
        if (shop != null) {
            Sites.build(level, shop, origin.offset(l.shopPos()), null, false);
        }
        Optional<StructureTemplate> frame = level.getStructureManager().get(l.frame());
        int blocks = 0, chests = 0, stacks = 0;
        if (frame.isPresent()) {
            BlockPos anchor = origin.offset(l.framePos());
            java.util.Map<BlockPos, BlockState> want = templateBlocks(level, frame.get(), anchor);
            BoundingBox box = l.frameBox(origin);
            List<BoundingBox> skip = new ArrayList<>();
            for (Section s : l.sections()) {
                skip.add(s.box(origin));
            }
            if (kiosk != null) {
                skip.add(kiosk);
            }
            // the Event Board: wherever it is in the frame, its page and sort move to the template's spot
            CompoundTag boardData = null;
            List<BlockPos> placed = new ArrayList<>();
            List<ItemStack> things = new ArrayList<>();
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (inAny(skip, p)) {
                            continue;
                        }
                        BlockState now = level.getBlockState(p);
                        BlockState t = want.get(p);
                        if (t != null ? now.is(t.getBlock()) : now.isAir()) {
                            continue;
                        }
                        blocks++;
                        if (now.isAir()) {
                            continue;                        // a hole: the template fills it
                        }
                        BlockEntity be = level.getBlockEntity(p);
                        if (be instanceof com.holeydigsite.board.ScoreBoardBlockEntity sb && now.is(ModBlocks.EVENT_BOARD.get())) {
                            if (boardData == null) {
                                boardData = sb.saveWithoutMetadata(level.registryAccess());
                            }
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            continue;
                        }
                        if (!(now.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) {
                            things.addAll(Block.getDrops(now, level, p, be));
                        }
                        placed.add(p);
                    }
                }
            }
            for (BlockPos p : placed) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1))) {
                if (!inAny(skip, e.blockPosition())) {
                    things.add(e.getItem().copy());
                    e.discard();
                }
            }
            frame.get().placeInWorld(level, anchor, anchor, new StructurePlaceSettings().setIgnoreEntities(true), level.random,
                    Block.UPDATE_CLIENTS);
            BlockPos boardAt = origin.offset(l.board());
            if (boardData != null && level.getBlockEntity(boardAt) instanceof com.holeydigsite.board.ScoreBoardBlockEntity sb) {
                sb.loadWithComponents(boardData, level.registryAccess());
                sb.setChanged();
                sb.resend();
            }
            things.removeIf(ItemStack::isEmpty);
            stacks += things.size();
            chests += intoChests(level, origin, l.frameChests(), origin.offset(l.board()).relative(Direction.WEST), things);
            for (ServerPlayer pl : level.players()) {
                if (box.isInside(pl.blockPosition()) && !inAny(skip, pl.blockPosition()) && !level.getBlockState(pl.blockPosition()).isAir()) {
                    BlockPos g = origin.offset(l.board()).relative(Direction.WEST);
                    pl.teleportTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
                }
            }
        }
        Rebuilt r = rebuild(level, island, ev);
        HoleyDigsite.LOGGER.info("Event island at {} rebuilt in full: {} frame blocks, {} chests", origin, blocks, chests + r.chests());
        return new WholeRebuild(blocks, chests + r.chests(), stacks + r.stacks());
    }

    private static boolean inAny(List<BoundingBox> boxes, BlockPos p) {
        for (BoundingBox b : boxes) {
            if (b.isInside(p)) {
                return true;
            }
        }
        return false;
    }

    /** The template's blocks by world position when placed at {@code anchor} (no rotation). */
    private static java.util.Map<BlockPos, BlockState> templateBlocks(ServerLevel level, StructureTemplate t, BlockPos anchor) {
        CompoundTag tag = t.save(new CompoundTag());
        List<BlockState> palette = new ArrayList<>();
        var blocks = level.holderLookup(Registries.BLOCK);
        for (net.minecraft.nbt.Tag p : tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            palette.add(net.minecraft.nbt.NbtUtils.readBlockState(blocks, (CompoundTag) p));
        }
        java.util.Map<BlockPos, BlockState> out = new java.util.HashMap<>();
        for (net.minecraft.nbt.Tag b0 : tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag b = (CompoundTag) b0;
            net.minecraft.nbt.ListTag pos = b.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
            BlockState st = palette.get(b.getInt("state"));
            if (!st.isAir()) {
                out.put(anchor.offset(pos.getInt(0), pos.getInt(1), pos.getInt(2)), st);
            }
        }
        return out;
    }

    /**
     * Takes out everything a player put into the zone, the way breaking would: what item handlers hold, each block's loot (worked
     * out while every block is still there, so two-block machines drop once) and what blocks drop from their contents when removed.
     */
    private static List<ItemStack> takePlayerThings(ServerLevel level, BoundingBox zone) {
        List<ItemStack> out = new ArrayList<>();
        Sites.State st = Sites.collect(level, zone);
        if (st.money > 0) {
            out.addAll(Sites.coins(st.money));
        }
        out.addAll(st.sell);
        out.addAll(st.buy);
        out.addAll(st.dropped);
        st.machines.values().forEach(out::addAll);
        List<BlockPos> placed = new ArrayList<>();
        for (int y = zone.minY(); y <= zone.maxY(); y++) {
            for (int z = zone.minZ(); z <= zone.maxZ(); z++) {
                for (int x = zone.minX(); x <= zone.maxX(); x++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (state.isAir() || state.is(SECTION_BLOCKS) || y < zone.minY() + UNDERSIDE && state.is(UNDERSIDE_BLOCKS)) {
                        continue;
                    }
                    if (!(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) {
                        out.addAll(Block.getDrops(state, level, p, level.getBlockEntity(p)));
                    }
                    placed.add(p);
                }
            }
        }
        for (BlockPos p : placed) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(zone.minX() - 1, zone.minY() - 1, zone.minZ() - 1,
                zone.maxX() + 2, zone.maxY() + 2, zone.maxZ() + 2);
        for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
            out.add(e.getItem().copy());
            e.discard();
        }
        return out;
    }

    /** Puts the stacks into chests on the section's chest spots (layer by layer upwards). Returns the chests used. */
    private static int intoChests(ServerLevel level, BlockPos anchor, List<BlockPos> spots, BlockPos gate, List<ItemStack> things) {
        int used = 0, i = 0;
        for (int layer = 0; i < things.size() && layer < 16; layer++) {
            for (BlockPos rel : spots) {
                if (i >= things.size()) {
                    break;
                }
                BlockPos at = anchor.offset(rel).above(layer);
                if (!level.getBlockState(at).isAir() && !level.getBlockState(at).canBeReplaced()) {
                    continue;
                }
                level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
                if (level.getBlockEntity(at) instanceof ChestBlockEntity chest) {
                    for (int slot = 0; slot < chest.getContainerSize() && i < things.size(); slot++) {
                        chest.setItem(slot, things.get(i++));
                    }
                    chest.setChanged();
                    used++;
                }
            }
        }
        while (i < things.size()) {        // never lose anything
            net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(level, gate.getX() + 0.5,
                    gate.getY() + 0.1, gate.getZ() + 0.5, things.get(i++));
            e.setUnlimitedLifetime();
            level.addFreshEntity(e);
        }
        return used;
    }

    // ------------------------------------------------------------------------------------------------ layout loading

    public static Layout parse(JsonObject o) {
        List<Section> sections = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("sections")) {
            JsonObject s = e.getAsJsonObject();
            List<BlockPos> chests = new ArrayList<>();
            for (JsonElement c : s.getAsJsonArray("chests")) {
                chests.add(pos(c.getAsJsonArray()));
            }
            sections.add(new Section(s.get("tier").getAsInt(), s.get("plot").getAsString(), pos(s.getAsJsonArray("pos")),
                    pos(s.getAsJsonArray("size")), pos(s.getAsJsonArray("board")), pos(s.getAsJsonArray("gate")), List.copyOf(chests)));
        }
        JsonObject frame = o.getAsJsonObject("frame");
        JsonObject shop = o.getAsJsonObject("shop");
        List<BlockPos> frameChests = new ArrayList<>();
        if (o.has("frame_chests")) {
            for (JsonElement c : o.getAsJsonArray("frame_chests")) {
                frameChests.add(pos(c.getAsJsonArray()));
            }
        }
        return new Layout(pos(o.getAsJsonArray("home")), o.has("side") ? o.get("side").getAsString() : "north",
                ResourceLocation.parse(frame.get("structure").getAsString()), pos(frame.getAsJsonArray("pos")), pos(frame.getAsJsonArray("size")),
                ResourceLocation.parse(shop.get("site").getAsString()), pos(shop.getAsJsonArray("pos")), List.copyOf(sections),
                o.has("board") ? pos(o.getAsJsonArray("board")) : BlockPos.ZERO, List.copyOf(frameChests));
    }

    private static BlockPos pos(JsonArray a) {
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager rm, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void v, ResourceManager rm, ProfilerFiller profiler) {
                rm.getResource(HoleyDigsite.id("event_island.json")).ifPresent(res -> {
                    try (Reader r = res.openAsReader()) {
                        LAYOUT = parse(JsonParser.parseReader(r).getAsJsonObject());
                    } catch (Exception e) {
                        HoleyDigsite.LOGGER.error("Bad event_island.json", e);
                    }
                });
            }
        });
    }
}
