package com.holeydigsite.site;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.plot.PlotBoardBlockEntity;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.sellpad.SellPadBlock;
import com.holeydigsite.shop.BuyPadBlock;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import com.holeydigsite.economy.Selling;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds and updates the mod's sites: the home store, the land plots and their shop kiosks.
 *
 * <p>A {@link SiteMarkerBlock} in the island template names a site. One tick after it loads, the site's
 * structure from the mod jar is placed with its origin at the marker and recorded in {@link SiteData} (anchor, zone,
 * version). On every world load ({@link #checkAll}), a site whose recorded version is older than its {@link SiteDef}'s is
 * rebuilt. Player state inside (Money Pan balance, pad contents, container items, dropped items) is saved, the zone is
 * cleared and rebuilt, and the state goes back into the new blocks. Whatever doesn't fit pops out at the entrance.
 * Owned plots are never rebuilt, only their kiosk. Display slot mappings ({@code store_layout}) apply without a
 * rebuild.</p>
 */
public final class Sites {
    private Sites() {}

    /** Player state saved from a zone before a rebuild. */
    public static final class State {
        public long money;
        public final List<ItemStack> sell = new ArrayList<>();
        public final List<ItemStack> buy = new ArrayList<>();
        public final List<ItemStack> dropped = new ArrayList<>();
        public final Map<Block, List<ItemStack>> machines = new LinkedHashMap<>();

        public boolean isEmpty() {
            return money == 0 && sell.isEmpty() && buy.isEmpty() && dropped.isEmpty() && machines.isEmpty();
        }
    }

    /** What a build did (tests, commands). */
    public record Result(boolean built, @Nullable SiteData.Site site, State saved, List<ItemStack> popped, List<String> newItems,
                         @Nullable Component message) {
        static Result failed() {
            return new Result(false, null, new State(), List.of(), List.of(), null);
        }
    }

    // ------------------------------------------------------------------------------------------------ entry points

    /** Markers waiting to be built, level to position to ticks waited. Built by {@link #buildQueued} on the next level tick. */
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Map<BlockPos, Integer>> QUEUE = new java.util.HashMap<>();

    /**
     * A Site Marker was placed or loaded, build it on the next level tick. This is not a scheduled block tick, which only run in
     * ticking chunks that for a freshly placed island can take a while, this runs as soon as the chunk is loaded.
     */
    public static void queueMarker(ServerLevel level, BlockPos pos) {
        QUEUE.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>()).putIfAbsent(pos.immutable(), 0);
    }

    /** True once any queued site markers in this dimension have finished building. */
    public static boolean isIslandReady(ServerLevel level) {
        Map<BlockPos, Integer> q = QUEUE.get(level.dimension());
        return q == null || q.isEmpty();
    }

    /** Level tick: builds the queued markers whose block entity has its site (the template sets the NBT after the block). */
    public static void buildQueued(ServerLevel level) {
        Map<BlockPos, Integer> q = QUEUE.get(level.dimension());
        if (q == null || q.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, Integer> e : new ArrayList<>(q.entrySet())) {
            BlockPos pos = e.getKey();
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SiteMarkerBlockEntity marker)) {
                q.remove(pos);           // gone (already built, broken) or unloaded: it queues itself again when it loads
                continue;
            }
            if (marker.site() == null) {
                if (e.getValue() > 40) {
                    HoleyDigsite.LOGGER.warn("Site marker at {} has no site set - ignored", pos);
                    q.remove(pos);
                } else {
                    q.put(pos, e.getValue() + 1);
                }
                continue;
            }
            q.remove(pos);
            buildFromMarker(level, pos, marker.site());
        }
    }

    /** Builds the named site at the marker (once). */
    public static void buildFromMarker(ServerLevel level, BlockPos pos, ResourceLocation siteId) {
        SiteData data = SiteData.get(level);
        SiteDef def = SiteDefs.get(siteId);
        if (def == null) {
            HoleyDigsite.LOGGER.error("Site marker at {} names unknown site {}", pos, siteId);
            return;
        }
        if (data.get(siteId, pos) != null) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);   // already built (marker left over)
            return;
        }
        Result r = build(level, def, pos, null, false);
        if (r.built()) {
            WorldFixups.applyNewIsland(level, siteId, pos);   // An island placed from an older pack template gets fixed too
            if (siteId.equals(HoleyDigsite.id("home"))) {
                com.holeydigsite.event.EventIsland.onHomeBuilt(level, pos);
            }
        }
    }

    /**
     * World load, runs every site's version check and layout refresh. Plots first, where an unowned plot rebuild also rebuilds its
     * kiosk, then stores and kiosks.
     */
    public static List<Result> checkAll(ServerLevel level) {
        List<Result> results = new ArrayList<>();
        SiteData data = SiteData.getIfExists(level);
        if (data == null) {
            return results;
        }
        List<String> keys = data.all().stream()
                .sorted(Comparator.comparing((SiteData.Site s) -> kindOrder(s.id())).thenComparing(s -> s.parent() != null))
                .map(SiteData.Site::key).toList();
        for (String key : keys) {
            SiteData.Site site = data.get(key);
            SiteDef def = site == null ? null : SiteDefs.get(site.id());
            if (def == null) {
                continue;
            }
            if (site.version() < def.version()) {
                if (def.kind() == SiteDef.Kind.PLOT && plotOwned(level, def, site.anchor())) {
                    HoleyDigsite.LOGGER.info("Site {} has a new version {} but the plot is owned: not rebuilt", key, def.version());
                    continue;
                }
                HoleyDigsite.LOGGER.info("Updating site {} from version {} to {}", key, site.version(), def.version());
                results.add(build(level, def, site.anchor(), site.parent(), true));
            } else if (def.layout() != null) {
                Result r = refreshLayout(level, site, def);
                if (r != null) {
                    results.add(r);
                }
            }
        }
        return results;
    }

    private static int kindOrder(ResourceLocation id) {
        SiteDef d = SiteDefs.get(id);
        return d != null && d.kind() == SiteDef.Kind.PLOT ? 0 : 1;
    }

    /** True if the plot site's board says the plot was bought. */
    public static boolean plotOwned(ServerLevel level, SiteDef def, BlockPos anchor) {
        if (def.plot() == null || def.board() == null) {
            return false;
        }
        PlotData.Plot p = PlotData.get(level).get(def.plot(), anchor.offset(def.board()));
        return p != null && p.owned();
    }

    /** Applies a changed slot mapping without rebuilding; returns null if nothing changed. */
    @Nullable
    public static Result refreshLayout(ServerLevel level, SiteData.Site site, SiteDef def) {
        forceLoad(level, site.zone());
        ensureDisplays(level, site, def);
        Boards.announceBlocked(level, Boards.ensure(level, site, def));
        Map<Integer, ResourceLocation> layout = SiteDefs.layout(def.layout());
        List<String> wanted = wantedItems(level, site.zone(), layout);
        if (wanted.equals(site.shown())) {
            return null;
        }
        List<String> shown = applyLayout(level, site.zone(), layout);
        reapplyCustom(level, def, site.anchor(), site.zone());
        SiteData data = SiteData.get(level);
        SiteData.Site now = new SiteData.Site(site.id(), site.anchor(), site.zone(), site.version(), shown, site.parent());
        data.put(now);
        List<String> added = added(site.shown(), shown);
        Component msg = added.isEmpty() ? null : announce(level, def, added);
        return new Result(true, now, new State(), List.of(), added, msg);
    }

    // ------------------------------------------------------------------------------------------------ build / rebuild

    /**
     * Builds or rebuilds a site with its anchor at {@code anchor}, saves player state, clears the zone, places the
     * structure and the children, puts the state back, records the site.
     */
    public static Result build(ServerLevel level, SiteDef def, BlockPos anchor, @Nullable String parent, boolean announce) {
        Optional<StructureTemplate> template = level.getStructureManager().get(def.structure());
        if (template.isEmpty()) {
            HoleyDigsite.LOGGER.error("Site {}: structure {} not found", def.id(), def.structure());
            return Result.failed();
        }
        SiteData data = SiteData.get(level);
        SiteData.Site old = data.get(def.id(), anchor);
        BoundingBox zone = old != null ? old.zone() : zoneOf(anchor, def);
        var size = template.get().getSize();
        if (size.getX() > zone.getXSpan() || size.getY() > zone.getYSpan() || size.getZ() > zone.getZSpan()) {
            HoleyDigsite.LOGGER.error("Site {}: structure {} ({}) does not fit the zone {} - not built", def.id(), def.structure(),
                    size, zone);
            return Result.failed();
        }
        forceLoad(level, zone);
        State saved = collect(level, zone);
        clear(level, zone);
        collectItemEntities(level, zone, saved.dropped);   // anything a cleared block dropped anyway

        template.get().placeInWorld(level, anchor, anchor, new StructurePlaceSettings().setIgnoreEntities(true), level.random,
                Block.UPDATE_CLIENTS);
        // children (a plot's kiosk) are built fresh inside the cleared zone
        for (SiteDef.Child c : def.children()) {
            SiteDef cd = SiteDefs.get(c.site());
            if (cd == null) {
                HoleyDigsite.LOGGER.error("Site {}: unknown child site {}", def.id(), c.site());
                continue;
            }
            build(level, cd, anchor.offset(c.pos()), SiteData.keyOf(def.id(), anchor), false);
        }
        // plot boards register their plot (keeps the owned flag)
        for (BlockEntity be : blockEntities(level, zone)) {
            if (be instanceof PlotBoardBlockEntity board) {
                board.register();
            }
        }
        List<String> shown = def.layout() != null ? applyLayout(level, zone, SiteDefs.layout(def.layout())) : List.of();
        if (def.layout() != null) {
            reapplyCustom(level, def, anchor, zone);   // operator-made display entries survive the rebuild
        }
        BlockPos entrance = anchor.offset(def.entrance());
        List<ItemStack> popped = restore(level, zone, saved, entrance);
        moveOut(level, zone, entrance);

        SiteData.Site site = new SiteData.Site(def.id(), anchor.immutable(), zone, def.version(), shown, parent);
        data.put(site);
        List<String> added = old == null ? List.of() : added(old.shown(), shown);
        Component msg = announce && old != null ? announce(level, def, added) : null;
        HoleyDigsite.LOGGER.info("Built site {} at {} (version {}{})", def.id(), anchor, def.version(),
                old == null ? "" : ", rebuilt from " + old.version());
        return new Result(true, site, saved, popped, added, msg);
    }

    /** Puts the saved custom shop entries of this site back on its displays, child sites' displays are theirs. */
    private static void reapplyCustom(ServerLevel level, SiteDef def, BlockPos anchor, BoundingBox zone) {
        List<BoundingBox> children = new ArrayList<>();
        for (SiteDef.Child c : def.children()) {
            SiteDef cd = SiteDefs.get(c.site());
            if (cd != null) {
                children.add(zoneOf(anchor.offset(c.pos()), cd));
            }
        }
        com.holeydigsite.shop.custom.CustomShops.reapply(level, SiteData.keyOf(def.id(), anchor), zone, children);
    }

    public static BoundingBox zoneOf(BlockPos anchor, SiteDef def) {
        return new BoundingBox(anchor.getX(), anchor.getY(), anchor.getZ(),
                anchor.getX() + def.size().getX() - 1, anchor.getY() + def.size().getY() - 1, anchor.getZ() + def.size().getZ() - 1);
    }

    private static void forceLoad(ServerLevel level, BoundingBox zone) {
        for (int cx = zone.minX() >> 4; cx <= zone.maxX() >> 4; cx++) {
            for (int cz = zone.minZ() >> 4; cz <= zone.maxZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** Block entities inside the zone, bottom-up, north-west first, a stable order for first Money Pan and others. */
    public static List<BlockEntity> blockEntities(ServerLevel level, BoundingBox zone) {
        List<BlockEntity> out = new ArrayList<>();
        for (int cx = zone.minX() >> 4; cx <= zone.maxX() >> 4; cx++) {
            for (int cz = zone.minZ() >> 4; cz <= zone.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (zone.isInside(be.getBlockPos())) {
                        out.add(be);
                    }
                }
            }
        }
        out.sort(Comparator.comparingInt((BlockEntity b) -> b.getBlockPos().getY())
                .thenComparingInt(b -> b.getBlockPos().getZ()).thenComparingInt(b -> b.getBlockPos().getX()));
        return out;
    }

    private static AABB box(BoundingBox zone) {
        return new AABB(zone.minX(), zone.minY(), zone.minZ(), zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1);
    }

    /** Takes all player state out of the zone, the blocks are left empty. */
    public static State collect(ServerLevel level, BoundingBox zone) {
        State s = new State();
        for (BlockEntity be : blockEntities(level, zone)) {
            BlockPos pos = be.getBlockPos();
            Block block = be.getBlockState().getBlock();
            if (be instanceof MoneyPanBlockEntity pan) {
                s.money += pan.withdrawAll();
            } else if (be instanceof PadBlockEntity pad) {
                (block instanceof BuyPadBlock ? s.buy : s.sell).addAll(pad.takeAll());
            } else {
                IItemHandler h = handler(level, be);
                if (h != null) {
                    List<ItemStack> list = s.machines.computeIfAbsent(block, b -> new ArrayList<>());
                    for (int i = 0; i < h.getSlots(); i++) {
                        for (int guard = 0; guard < 64; guard++) {
                            ItemStack out = h.extractItem(i, Integer.MAX_VALUE, false);
                            if (out.isEmpty()) {
                                break;
                            }
                            list.add(out);
                        }
                    }
                    if (list.isEmpty()) {
                        s.machines.remove(block);
                    }
                }
            }
        }
        collectItemEntities(level, zone, s.dropped);
        return s;
    }

    @Nullable
    private static IItemHandler handler(ServerLevel level, BlockEntity be) {
        if (be instanceof Container c) {
            return new InvWrapper(c);
        }
        if (be instanceof ShopDisplayBlockEntity || be instanceof PlotBoardBlockEntity || be instanceof SiteMarkerBlockEntity) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
    }

    private static void collectItemEntities(ServerLevel level, BoundingBox zone, List<ItemStack> into) {
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box(zone))) {
            if (!e.getItem().isEmpty()) {
                into.add(e.getItem().copy());
            }
            e.discard();
        }
    }

    /** Removes every block in the zone without drops or neighbour updates, top-down. */
    public static void clear(ServerLevel level, BoundingBox zone) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = zone.maxY(); y >= zone.minY(); y--) {
            for (int z = zone.minZ(); z <= zone.maxZ(); z++) {
                for (int x = zone.minX(); x <= zone.maxX(); x++) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
    }

    /** Puts saved state into the new blocks; returns what had to pop out at the entrance. */
    public static List<ItemStack> restore(ServerLevel level, BoundingBox zone, State s, BlockPos entrance) {
        List<ItemStack> leftovers = new ArrayList<>();
        List<BlockEntity> bes = blockEntities(level, zone);
        // money -> the first Money Pan, else coins
        MoneyPanBlockEntity pan = (MoneyPanBlockEntity) bes.stream().filter(b -> b instanceof MoneyPanBlockEntity).findFirst().orElse(null);
        if (s.money > 0) {
            if (pan != null) {
                pan.deposit(s.money);
            } else {
                leftovers.addAll(coins(s.money));
            }
        }
        // pads
        fillPads(bes, s.sell, false, leftovers);
        fillPads(bes, s.buy, true, leftovers);
        // containers / machines of the same block
        for (Map.Entry<Block, List<ItemStack>> e : s.machines.entrySet()) {
            List<ItemStack> rest = new ArrayList<>();
            for (ItemStack st : e.getValue()) {
                ItemStack left = st.copy();
                for (BlockEntity be : bes) {
                    if (left.isEmpty()) {
                        break;
                    }
                    if (be.getBlockState().is(e.getKey())) {
                        IItemHandler h = handler(level, be);
                        if (h != null) {
                            left = ItemHandlerHelper.insertItem(h, left, false);
                        }
                    }
                }
                if (!left.isEmpty()) {
                    rest.add(left);
                }
            }
            leftovers.addAll(rest);
        }
        leftovers.addAll(s.dropped);
        for (ItemStack st : leftovers) {
            pop(level, entrance, st);
        }
        return leftovers;
    }

    private static void fillPads(List<BlockEntity> bes, List<ItemStack> stacks, boolean buy, List<ItemStack> leftovers) {
        for (ItemStack st : stacks) {
            ItemStack left = st.copy();
            for (BlockEntity be : bes) {
                if (left.isEmpty()) {
                    break;
                }
                if (be instanceof PadBlockEntity pad && (be.getBlockState().getBlock() instanceof BuyPadBlock) == buy
                        && (buy || be.getBlockState().getBlock() instanceof SellPadBlock)) {
                    pad.insert(left);   // shrinks left
                }
            }
            if (!left.isEmpty()) {
                leftovers.add(left);
            }
        }
    }

    /** Coins worth {@code copper}, biggest first, in full stacks. */
    public static List<ItemStack> coins(long copper) {
        List<ItemStack> out = new ArrayList<>();
        return Selling.coins(copper);
    }

    /** Drops a stack at the entrance with no motion and never despawns. */
    static void pop(ServerLevel level, BlockPos at, ItemStack stack) {
        ItemEntity e = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, stack.copy());
        e.setDeltaMovement(0, 0, 0);
        e.setUnlimitedLifetime();
        level.addFreshEntity(e);
    }

    /** Players standing inside a rebuilt zone go to the entrance so nobody ends up inside a wall. */
    private static void moveOut(ServerLevel level, BoundingBox zone, BlockPos entrance) {
        for (ServerPlayer p : level.players()) {
            if (zone.isInside(p.blockPosition())) {
                p.teleportTo(entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ layouts

    /**
     * Ensures any display blocks defined in the site's structure template exist in the world (placed into air,
     * without disturbing anything players built or custom displays).
     */
    public static void ensureDisplays(ServerLevel level, SiteData.Site site, SiteDef def) {
        if (def.structure() == null) {
            return;
        }
        Optional<StructureTemplate> templateOpt = level.getStructureManager().get(def.structure());
        if (templateOpt.isEmpty()) {
            return;
        }
        StructureTemplate template = templateOpt.get();
        List<StructureTemplate.StructureBlockInfo> displays = template.filterBlocks(site.anchor(),
                new StructurePlaceSettings(), ModBlocks.SHOP_DISPLAY.get());
        for (StructureTemplate.StructureBlockInfo info : displays) {
            BlockPos pos = info.pos();
            if (!site.zone().isInside(pos)) {
                continue;
            }
            int slot = info.nbt() != null ? info.nbt().getInt("Slot") : 0;
            if (slot <= 0) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShopDisplayBlockEntity d) {
                if (d.slot() <= 0) {
                    d.setSlot(slot);
                }
            } else if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, info.state(), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof ShopDisplayBlockEntity d) {
                    d.setSlot(slot);
                }
            }
        }
    }

    private static List<ShopDisplayBlockEntity> slottedDisplays(ServerLevel level, BoundingBox zone) {
        List<ShopDisplayBlockEntity> out = new ArrayList<>();
        for (BlockEntity be : blockEntities(level, zone)) {
            if (be instanceof ShopDisplayBlockEntity d && d.slot() > 0) {
                out.add(d);
            }
        }
        out.sort(Comparator.comparingInt(ShopDisplayBlockEntity::slot));
        return out;
    }

    @Nullable
    private static Item item(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        Item it = BuiltInRegistries.ITEM.get(id);
        return it == Items.AIR ? null : it;
    }

    /**
     * Sets every numbered display in the zone to its layout item (unmapped slot = empty). Returns the items now shown, in
     * slot order.
     */
    public static List<String> applyLayout(ServerLevel level, BoundingBox zone, Map<Integer, ResourceLocation> layout) {
        List<String> shown = new ArrayList<>();
        for (ShopDisplayBlockEntity d : slottedDisplays(level, zone)) {
            Item want = item(layout.get(d.slot()));
            if (d.item() != want) {
                d.setItem(want);
            }
            if (want != null) {
                shown.add(BuiltInRegistries.ITEM.getKey(want).toString());
            }
        }
        return List.copyOf(shown);
    }

    /**
     * The items the layout would show without touching the world. This needs the slots that exist, which are only known from the
     * world, so this uses the recorded shown list's size as a hint only when the chunks are not loaded. The simplest correct
     * answer is to load the zone and read the slots.
     */
    private static List<String> wantedItems(ServerLevel level, BoundingBox zone, Map<Integer, ResourceLocation> layout) {
        forceLoad(level, zone);
        List<String> out = new ArrayList<>();
        for (ShopDisplayBlockEntity d : slottedDisplays(level, zone)) {
            Item want = item(layout.get(d.slot()));
            if (want != null) {
                out.add(BuiltInRegistries.ITEM.getKey(want).toString());
            }
        }
        return out;
    }

    private static List<String> added(List<String> before, List<String> after) {
        Set<String> old = new LinkedHashSet<>(before);
        return after.stream().filter(i -> !old.contains(i)).distinct().toList();
    }

    // ------------------------------------------------------------------------------------------------ announcements

    /** "Store updated: new items X, Y" (or "Store updated: new look") to everyone online and to players logging in soon. */
    static Component announce(ServerLevel level, SiteDef def, List<String> added) {
        MutableComponent msg;
        if (added.isEmpty()) {
            msg = Component.translatable("chat.holeydigsite.site.rebuilt", Component.translatable(def.name()));
        } else {
            MutableComponent list = Component.empty();
            for (int i = 0; i < added.size(); i++) {
                if (i > 0) {
                    list.append(", ");
                }
                Item it = item(ResourceLocation.tryParse(added.get(i)));
                list.append(it == null ? Component.literal(added.get(i)) : it.getDescription().copy().withStyle(ChatFormatting.YELLOW));
            }
            msg = Component.translatable("chat.holeydigsite.site.updated", Component.translatable(def.name()), list);
        }
        msg = msg.withStyle(ChatFormatting.GOLD);
        SiteData.Announcement a = SiteData.get(level).announce(msg, level.getGameTime());
        if (level.getServer() != null) {
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(msg);
                p.getPersistentData().putInt(SiteEvents.SEEN, Math.max(p.getPersistentData().getInt(SiteEvents.SEEN), a.serial()));
            }
        }
        return msg;
    }
}
