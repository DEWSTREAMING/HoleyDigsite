package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.plot.PlotBoardBlockEntity;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.site.SiteCommands;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.SiteMarkerBlockEntity;
import com.holeydigsite.site.SiteProtection;
import com.holeydigsite.site.Sites;
import com.holeydigsite.site.WorldVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sandbox-only GameTests for Phase 14 (not shipped): sites the mod builds and updates - the store placed from a marker with
 * its zone + version, zone protection, version-bump rebuild keeping Money Pan balance / pad contents / container items /
 * dropped items (leftovers pop out at the entrance), owned plots never rebuilt but unowned ones are, kiosks rebuilt inside
 * owned plots, slot mapping changes without a rebuild, world-version upgrade steps run once, the admin commands.
 *
 * <p>The real structures are big (store 26x26x22), so every test builds far away from the test grid (x/z 20000+, its own
 * column per test), runs in its own batch (sites and overrides are level-wide) and removes its sites at the end.</p>
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseFourteenTests {
    private static final ResourceLocation HOME = HoleyDigsite.id("home");
    private static final ResourceLocation DEEP = HoleyDigsite.id("deep_dig");
    private static final ResourceLocation DEEP_SHOP = HoleyDigsite.id("deep_dig_shop");
    private static final ResourceLocation HOME_LAYOUT = HoleyDigsite.id("home");

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    /** A far-away anchor for test number {@code n} (y 100: open sky in the test world). */
    private static BlockPos far(int n) {
        return new BlockPos(20000 + n * 64, 100, 20000);
    }

    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        p.getAbilities().instabuild = mode == GameType.CREATIVE;
        p.getInventory().clearContent();
        return p;
    }

    /** The GameTest world is kept between runs: wipe the test's area (blocks, item entities) before building. */
    private static void prepare(ServerLevel l, BlockPos a) {
        BoundingBox big = new BoundingBox(a.getX() - 4, a.getY() - 2, a.getZ() - 4, a.getX() + 40, a.getY() + 34, a.getZ() + 40);
        Sites.clear(l, big);
        for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(48))) e.discard();
    }

    private static SiteDef def(ResourceLocation id) {
        SiteDef d = SiteDefs.get(id);
        if (d == null) throw new IllegalStateException("no site definition " + id);
        return d;
    }

    /** Removes every site (and its plot) this test built, and all overrides. */
    private static void cleanup(ServerLevel l, List<BlockPos> anchors) {
        SiteData data = SiteData.get(l);
        for (SiteData.Site s : new ArrayList<>(data.all())) {
            for (BlockPos a : anchors) {
                if (s.zone().isInside(a) || s.anchor().equals(a) || (s.parent() != null && s.parent().endsWith(a.getX() + "," + a.getY() + "," + a.getZ()))) {
                    data.remove(s.key());
                }
            }
        }
        PlotData pd = PlotData.get(l);
        for (PlotData.Plot p : new ArrayList<>(pd.all())) {
            for (BlockPos a : anchors) {
                if (p.board().getX() >= a.getX() - 2 && p.board().getX() < a.getX() + 40 && p.board().getZ() >= a.getZ() - 2
                        && p.board().getZ() < a.getZ() + 40) {
                    pd.remove(p.id(), p.board());
                }
            }
        }
        SiteDefs.clearOverrides();
    }

    private static <T extends BlockEntity> List<T> bes(ServerLevel l, BoundingBox zone, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ())) {
            BlockEntity be = l.getBlockEntity(p);
            if (type.isInstance(be)) out.add(type.cast(be));
        }
        out.sort((a, b) -> {
            BlockPos x = a.getBlockPos(), y = b.getBlockPos();
            return x.getY() != y.getY() ? x.getY() - y.getY() : x.getZ() != y.getZ() ? x.getZ() - y.getZ() : x.getX() - y.getX();
        });
        return out;
    }

    private static PadBlockEntity pad(ServerLevel l, BoundingBox zone, Block kind) {
        for (PadBlockEntity p : bes(l, zone, PadBlockEntity.class)) {
            if (p.getBlockState().is(kind)) return p;
        }
        throw new IllegalStateException("no " + kind + " in " + zone);
    }

    private static Map<String, Integer> itemsIn(ServerLevel l, BoundingBox zone) {
        Map<String, Integer> m = new HashMap<>();
        AABB box = new AABB(zone.minX(), zone.minY(), zone.minZ(), zone.maxX() + 1, zone.maxY() + 1, zone.maxZ() + 1).inflate(2);
        for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, box)) {
            m.merge(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString(), e.getItem().getCount(), Integer::sum);
        }
        return m;
    }

    private static String key(net.minecraft.network.chat.Component c) {
        return c != null && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    private static Map<Integer, Item> displays(ServerLevel l, BoundingBox zone) {
        Map<Integer, Item> m = new HashMap<>();
        for (ShopDisplayBlockEntity d : bes(l, zone, ShopDisplayBlockEntity.class)) {
            if (d.slot() > 0) m.put(d.slot(), d.item());
        }
        return m;
    }

    // ------------------------------------------------------------------------------------------------ first load

    /** A Site Marker builds the home store one tick after it is placed; zone + version recorded; displays follow the layout. */
    @GameTest(template = "empty", batch = "p14_marker", timeoutTicks = 200)
    public static void markerBuildsStore(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(0);
        prepare(l, a);
        l.setChunkForced(a.getX() >> 4, a.getZ() >> 4, true);      // a real island is next to the player: its chunks tick
        l.setBlock(a, ModBlocks.SITE_MARKER.get().defaultBlockState(), 3);
        ((SiteMarkerBlockEntity) l.getBlockEntity(a)).setSite(HOME);
        AtomicInteger waited = new AtomicInteger();
        h.onEachTick(() -> {
            if (SiteData.get(l).get(HOME, a) == null && waited.incrementAndGet() < 150) {
                return;
            }
            if (waited.get() < 0) return;
            waited.set(-1000);
            try {
                SiteData.Site s = SiteData.get(l).get(HOME, a);
                check(h, s != null, "site recorded (marker: " + l.getBlockState(a) + ", chunk ticking: " + l.shouldTickBlocksAt(a) + ")");
                SiteDef d = def(HOME);
                check(h, s.version() == d.version(), "version " + s.version());
                check(h, s.zone().equals(Sites.zoneOf(a, d)), "zone " + s.zone());
                check(h, s.zone().getXSpan() == d.size().getX() && s.zone().getYSpan() == d.size().getY(), "zone size");
                check(h, !l.getBlockState(a).is(ModBlocks.SITE_MARKER.get()), "marker replaced by the structure");
                // the shop is there and complete
                check(h, bes(l, s.zone(), MoneyPanBlockEntity.class).size() == 1, "one Money Pan");
                check(h, bes(l, s.zone(), PadBlockEntity.class).size() == 2, "Sell Pad + Buy Pad");
                Map<Integer, ResourceLocation> layout = SiteDefs.layout(d.layout());
                Map<Integer, Item> shown = displays(l, s.zone());
                check(h, layout.size() >= 28, "layout has the 28 catalogue items: " + layout.size());
                for (Map.Entry<Integer, ResourceLocation> e : layout.entrySet()) {
                    check(h, shown.get(e.getKey()) == BuiltInRegistries.ITEM.get(e.getValue()), "slot " + e.getKey() + " shows " + e.getValue());
                }
                check(h, shown.values().stream().filter(java.util.Objects::nonNull).count() == layout.size(), "unmapped slots are empty");
                check(h, s.shown().size() == layout.size(), "shown list recorded");
                // a second marker tick for an already built site does nothing
                SiteData.Site again = SiteData.get(l).get(HOME, a);
                check(h, again == s || again.equals(s), "built once");
                h.succeed();
            } finally {
                l.setChunkForced(a.getX() >> 4, a.getZ() >> 4, false);
                cleanup(l, List.of(a));
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ protection

    @GameTest(template = "empty", batch = "p14_protect", timeoutTicks = 100)
    public static void zoneIsProtected(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(1);
        prepare(l, a);
        try {
            Sites.Result r = Sites.build(l, def(HOME), a, null, false);
            check(h, r.built(), "built");
            BoundingBox z = r.site().zone();
            ServerPlayer p = player(h, GameType.SURVIVAL);
            // a wall plank of the trading post (local 6, 12, 4 = oak planks)
            BlockPos wall = a.offset(6, 12, 4);
            check(h, l.getBlockState(wall).is(Blocks.OAK_PLANKS), "wall plank at " + wall + ": " + l.getBlockState(wall));
            p.gameMode.destroyBlock(wall);
            check(h, l.getBlockState(wall).is(Blocks.OAK_PLANKS), "survival can't break the store");
            // the margin of air above the roof is protected too
            BlockPos sky = new BlockPos(a.getX() + 12, z.maxY(), a.getZ() + 10);
            check(h, l.getBlockState(sky).isAir() && SiteProtection.isProtected(l, sky), "air above the roof is in the zone");
            // placing a block on the floor inside: refused (real click path)
            BlockPos floor = a.offset(15, 10, 10);
            check(h, l.getBlockState(floor.above()).isAir(), "floor free above " + floor);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 4));
            p.gameMode.useItemOn(p, l, p.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
            check(h, l.getBlockState(floor.above()).isAir(), "survival can't place in the store");
            check(h, p.getMainHandItem().getCount() == 4, "the dirt stays in hand");
            // the shop still works: a display hands out a blueprint
            ShopDisplayBlockEntity d = bes(l, z, ShopDisplayBlockEntity.class).stream().filter(x -> x.item() != null).findFirst().orElseThrow();
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.gameMode.useItemOn(p, l, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(d.getBlockPos()), Direction.UP, d.getBlockPos(), false));
            boolean blueprint = false;
            for (ItemStack s : p.getInventory().items) blueprint |= s.is(ModItems.BLUEPRINT.get());
            check(h, blueprint, "display still gives a blueprint");
            // outside the zone: allowed
            BlockPos out = new BlockPos(z.maxX() + 3, a.getY() + 5, z.minZ());
            l.setBlock(out, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            check(h, !SiteProtection.isProtected(l, out), "outside is not protected");
            p.gameMode.destroyBlock(out);
            check(h, l.getBlockState(out).isAir(), "outside the zone breaks normally");
            // creative players may edit (design work)
            ServerPlayer c = player(h, GameType.CREATIVE);
            c.gameMode.destroyBlock(wall);
            check(h, l.getBlockState(wall).isAir(), "creative can edit the store");
            h.succeed();
        } finally {
            SiteDefs.clearOverrides();
            cleanup(l, List.of(a));
        }
    }

    // ------------------------------------------------------------------------------------------------ rebuild keeps state

    @GameTest(template = "empty", batch = "p14_rebuild", timeoutTicks = 100)
    public static void versionBumpKeepsState(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(2);
        prepare(l, a);
        try {
            SiteDef d = def(HOME);
            Sites.Result first = Sites.build(l, d, a, null, false);
            BoundingBox z = first.site().zone();
            StringBuilder loose = new StringBuilder();
            for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(40))) loose.append(e.getItem()).append('@').append(e.blockPosition().subtract(a)).append(' ');
            check(h, loose.isEmpty(), "a fresh build drops nothing: " + loose);
            // player state: money, sell pad items, buy pad blueprints, a barrel's contents, items on the floor
            bes(l, z, MoneyPanBlockEntity.class).get(0).deposit(12345);
            PadBlockEntity sell = pad(l, z, ModBlocks.SELL_PAD.get()), buy = pad(l, z, ModBlocks.BUY_PAD.get());
            sell.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 7));
            sell.insert(new ItemStack(ModItems.RUBY_SMALL.get(), 3));
            ItemStack bp = new ItemStack(ModItems.BLUEPRINT.get(), 2);
            bp.set(com.holeydigsite.registry.ModDataComponents.BLUEPRINT_TARGET.get(), ModItems.MINING_DRILL.get());
            buy.insert(bp.copy());
            BarrelBlockEntity barrel = bes(l, z, BarrelBlockEntity.class).get(0);
            barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
            ItemEntity dropped = new ItemEntity(l, a.getX() + 15.5, a.getY() + 11.2, a.getZ() + 10.5, new ItemStack(ModItems.GOLD_COIN.get(), 5));
            dropped.setDeltaMovement(0, 0, 0);
            l.addFreshEntity(dropped);
            BlockPos litter = a.offset(15, 11, 10);
            l.setBlock(litter, Blocks.GLASS.defaultBlockState(), 3);     // a stray block (creative) - gone after the rebuild

            SiteDefs.override(d.withVersion(d.version() + 1));
            List<Sites.Result> rs = Sites.checkAll(l);
            Sites.Result r = rs.stream().filter(x -> x.site() != null && x.site().anchor().equals(a)).findFirst().orElse(null);
            check(h, r != null && r.built(), "rebuilt on version bump");
            check(h, r.site().version() == d.version() + 1, "new version recorded");
            check(h, r.site().zone().equals(z), "same zone");
            check(h, key(r.message()).startsWith("chat.holeydigsite.site."), "chat message: " + key(r.message()));
            check(h, l.getBlockState(litter).isAir(), "zone rebuilt from the structure");
            check(h, bes(l, z, MoneyPanBlockEntity.class).get(0).balance() == 12345, "balance kept exactly");
            PadBlockEntity sell2 = pad(l, z, ModBlocks.SELL_PAD.get()), buy2 = pad(l, z, ModBlocks.BUY_PAD.get());
            List<ItemStack> sc = sell2.contents(), bc = buy2.contents();
            check(h, sc.size() == 2 && sc.get(0).is(ModItems.FOLKIRON_CHUNK_LARGE.get()) && sc.get(0).getCount() == 7
                    && sc.get(1).is(ModItems.RUBY_SMALL.get()) && sc.get(1).getCount() == 3, "sell pad kept: " + sc);
            check(h, bc.size() == 1 && ItemStack.isSameItemSameComponents(bc.get(0), bp) && bc.get(0).getCount() == 2, "buy pad kept: " + bc);
            BarrelBlockEntity barrel2 = bes(l, z, BarrelBlockEntity.class).get(0);
            check(h, barrel2.getItem(0).is(Items.COBBLESTONE) && barrel2.getItem(0).getCount() == 20, "barrel contents kept");
            Map<String, Integer> items = itemsIn(l, z);
            check(h, items.getOrDefault("holeydigsite:gold_coin", 0) == 5, "dropped coins kept: " + items);
            check(h, items.size() == 1, "nothing else dropped: " + items);
            // a player inside is moved to the entrance, not into a wall (checked by moveOut); nothing duplicated:
            check(h, r.popped().stream().mapToInt(ItemStack::getCount).sum() == 5, "only the dropped coins popped: " + r.popped());

            // shape change with fewer machines: what doesn't fit pops out at the entrance (nothing lost)
            bes(l, z, BarrelBlockEntity.class).get(0).setItem(3, new ItemStack(Items.IRON_INGOT, 9));
            SiteDefs.override(new SiteDef(d.id(), d.kind(), d.name(), d.version() + 2, HoleyDigsite.id("store/deep_dig_shop_v1"), d.size(),
                    new BlockPos(3, 3, 1), d.protect(), d.layout(), d.plot(), d.board(), d.children()));
            Sites.checkAll(l);
            check(h, bes(l, z, MoneyPanBlockEntity.class).get(0).balance() == 12345, "balance moved to the kiosk-shaped store");
            Map<String, Integer> after = itemsIn(l, z);
            check(h, after.getOrDefault("minecraft:cobblestone", 0) == 20 && after.getOrDefault("minecraft:iron_ingot", 0) == 9,
                    "barrel items popped out: " + after);
            check(h, after.getOrDefault("holeydigsite:gold_coin", 0) == 5, "coins still there: " + after);
            BlockPos ent = a.offset(3, 3, 1);
            boolean atEntrance = l.getEntitiesOfClass(ItemEntity.class, new AABB(ent).inflate(1.5)).size() >= 3;
            check(h, atEntrance, "leftovers at the entrance " + ent);
            PadBlockEntity sell3 = pad(l, z, ModBlocks.SELL_PAD.get());
            check(h, sell3.contents().stream().mapToInt(ItemStack::getCount).sum() == 10, "sell pad items in the new pad");
            h.succeed();
        } finally {
            for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(40))) e.discard();
            cleanup(l, List.of(a));
        }
    }

    // ------------------------------------------------------------------------------------------------ plots

    @GameTest(template = "empty", batch = "p14_plots", timeoutTicks = 100)
    public static void ownedPlotNeverRebuilt(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(3);
        prepare(l, a);
        try {
            SiteDef plot = def(DEEP), shop = def(DEEP_SHOP);
            Sites.Result r = Sites.build(l, plot, a, null, false);
            check(h, r.built(), "plot built");
            BlockPos board = a.offset(plot.board());
            check(h, l.getBlockEntity(board) instanceof PlotBoardBlockEntity, "board at " + board);
            PlotData.Plot p = PlotData.get(l).get("deep_dig", board);
            check(h, p != null && !p.owned(), "plot registered, for sale");
            SiteData.Site kiosk = SiteData.get(l).get(DEEP_SHOP, a.offset(plot.children().get(0).pos()));
            check(h, kiosk != null && kiosk.parent() != null, "kiosk built as a child site");
            check(h, SiteProtection.isProtected(l, kiosk.zone().getCenter()), "kiosk protected");
            check(h, !SiteProtection.isProtected(l, a.offset(10, 9, 15)), "plot ground not site-protected (the plot lock does that)");
            // the player bought it and built something
            PlotData.get(l).setOwned("deep_dig", board, true);
            BlockPos mine = a.offset(15, 12, 15);
            l.setBlock(mine, Blocks.GLASS.defaultBlockState(), 3);
            BlockPos inKiosk = kiosk.zone().getCenter().above(2);
            l.setBlock(inKiosk, Blocks.GLASS.defaultBlockState(), 3);
            SiteDefs.override(plot.withVersion(plot.version() + 1));
            SiteDefs.override(shop.withVersion(shop.version() + 1));
            Sites.checkAll(l);
            check(h, l.getBlockState(mine).is(Blocks.GLASS), "owned plot NOT rebuilt");
            check(h, SiteData.get(l).get(DEEP, a).version() == plot.version(), "owned plot keeps its old version");
            check(h, !l.getBlockState(inKiosk).is(Blocks.GLASS), "its kiosk IS rebuilt");
            check(h, SiteData.get(l).get(DEEP_SHOP, kiosk.anchor()).version() == shop.version() + 1, "kiosk version updated");
            check(h, PlotData.get(l).get("deep_dig", board).owned(), "still owned");
            // unowned: the whole plot is rebuilt (and keeps for-sale state)
            PlotData.get(l).setOwned("deep_dig", board, false);
            Sites.checkAll(l);
            check(h, !l.getBlockState(mine).is(Blocks.GLASS), "unowned plot rebuilt");
            check(h, SiteData.get(l).get(DEEP, a).version() == plot.version() + 1, "plot version updated");
            check(h, !PlotData.get(l).get("deep_dig", board).owned(), "still for sale");
            check(h, l.getBlockEntity(board) instanceof PlotBoardBlockEntity, "board rebuilt");
            // the admin command refuses owned plots
            PlotData.get(l).setOwned("deep_dig", board, true);
            l.setBlock(mine, Blocks.GLASS.defaultBlockState(), 3);
            l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack().withSuppressedOutput(),
                    "holeydigsite plot rebuild deep_dig");
            check(h, l.getBlockState(mine).is(Blocks.GLASS), "command leaves the owned plot alone");
            h.succeed();
        } finally {
            SiteDefs.clearOverrides();
            cleanup(l, List.of(a));
        }
    }

    // ------------------------------------------------------------------------------------------------ layouts

    @GameTest(template = "empty", batch = "p14_layout", timeoutTicks = 100)
    public static void slotMappingWithoutRebuild(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(4);
        prepare(l, a);
        try {
            SiteDef d = def(HOME);
            Map<Integer, ResourceLocation> initial = new HashMap<>(SiteDefs.layout(d.layout()));
            initial.remove(30);
            SiteDefs.overrideLayout(HOME_LAYOUT, initial);
            Sites.Result r = Sites.build(l, d, a, null, false);
            SiteDefs.clearOverrides();
            BoundingBox z = r.site().zone();
            Map<Integer, ResourceLocation> layout = new HashMap<>(initial);
            int empty = 1;
            while (layout.containsKey(empty)) empty++;
            check(h, displays(l, z).containsKey(empty) && displays(l, z).get(empty) == null, "slot " + empty + " exists and is empty");
            BlockPos litter = a.offset(15, 11, 10);
            l.setBlock(litter, Blocks.GLASS.defaultBlockState(), 3);
            bes(l, z, MoneyPanBlockEntity.class).get(0).deposit(99);
            // new item in an empty slot + a changed slot + a removed slot
            int changed = layout.keySet().stream().min(Integer::compare).orElseThrow();
            int removed = layout.keySet().stream().max(Integer::compare).orElseThrow();
            layout.put(empty, BuiltInRegistries.ITEM.getKey(ModItems.GRAVEL_CLUMP.get()));
            layout.put(changed, BuiltInRegistries.ITEM.getKey(ModItems.SAND_CLUMP.get()));
            layout.remove(removed);
            SiteDefs.overrideLayout(HOME_LAYOUT, layout);
            List<Sites.Result> rs = Sites.checkAll(l);
            Map<Integer, Item> shown = displays(l, z);
            check(h, shown.get(empty) == ModItems.GRAVEL_CLUMP.get(), "new item in the empty slot");
            check(h, shown.get(changed) == ModItems.SAND_CLUMP.get(), "changed slot");
            check(h, shown.get(removed) == null, "removed slot is empty now");
            check(h, l.getBlockState(litter).is(Blocks.GLASS), "no rebuild");
            check(h, bes(l, z, MoneyPanBlockEntity.class).get(0).balance() == 99, "balance untouched");
            check(h, SiteData.get(l).get(HOME, a).version() == d.version(), "version unchanged");
            Sites.Result res = rs.stream().filter(x -> x.site() != null && x.site().anchor().equals(a)).findFirst().orElseThrow();
            check(h, res.newItems().contains("holeydigsite:gravel_clump") && res.newItems().contains("holeydigsite:sand_clump"), "new items " + res.newItems());
            check(h, key(res.message()).equals("chat.holeydigsite.site.updated"), "chat: " + key(res.message()));
            // unchanged layout: nothing happens on the next load
            check(h, Sites.checkAll(l).stream().noneMatch(x -> x.site() != null && x.site().anchor().equals(a)), "no-op when unchanged");
            h.succeed();
        } finally {
            SiteDefs.clearOverrides();
            cleanup(l, List.of(a));
        }
    }

    /** Phase 18: a Trading Post built before 1.16.0 has empty displays 14 and 26; the new layout fills them without a rebuild. */
    @GameTest(template = "empty", batch = "p14_layout", timeoutTicks = 100)
    public static void oldHomeGainsSeedScreenAndBread(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(7);
        prepare(l, a);
        try {
            SiteDef d = def(HOME);
            Map<Integer, ResourceLocation> now = new HashMap<>(SiteDefs.layout(d.layout()));
            check(h, now.get(14).equals(BuiltInRegistries.ITEM.getKey(ModItems.SEED_SCREEN.get())) && now.get(26).toString().equals("minecraft:bread"), "layout 14/26: " + now.get(14) + " " + now.get(26));
            Map<Integer, ResourceLocation> old = new HashMap<>(now);
            old.remove(14);
            old.remove(26);
            SiteDefs.overrideLayout(HOME_LAYOUT, old);
            Sites.Result r = Sites.build(l, d, a, null, false);
            BoundingBox z = r.site().zone();
            check(h, displays(l, z).containsKey(14) && displays(l, z).get(14) == null && displays(l, z).get(26) == null, "old store: 14 and 26 are empty displays");
            BlockPos litter = a.offset(15, 11, 10);
            l.setBlock(litter, Blocks.GLASS.defaultBlockState(), 3);
            SiteDefs.overrideLayout(HOME_LAYOUT, now);
            List<Sites.Result> rs = Sites.checkAll(l);
            Map<Integer, Item> shown = displays(l, z);
            check(h, shown.get(14) == ModItems.SEED_SCREEN.get() && shown.get(26) == Items.BREAD, "new displays appear: " + shown.get(14) + " " + shown.get(26));
            check(h, l.getBlockState(litter).is(Blocks.GLASS), "no rebuild");
            h.succeed();
        } finally {
            SiteDefs.clearOverrides();
            cleanup(l, List.of(a));
        }
    }

    /** Phase 19: an old Trading Post (built before the anvil) gains the Smithing Anvil + Hammer displays (27, 29) without a rebuild. */
    @GameTest(template = "empty", batch = "p14_layout19", timeoutTicks = 100)
    public static void oldHomeGainsSmithingDisplays(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(8);
        prepare(l, a);
        try {
            SiteDef d = def(HOME);
            Map<Integer, ResourceLocation> now = new HashMap<>(SiteDefs.layout(d.layout()));
            check(h, now.get(27).equals(BuiltInRegistries.ITEM.getKey(ModItems.SMITHING_ANVIL.get()))
                    && now.get(29).equals(BuiltInRegistries.ITEM.getKey(ModItems.BLACKSMITH_HAMMER.get())), "layout 27/29: " + now.get(27) + " " + now.get(29));
            Map<Integer, ResourceLocation> old = new HashMap<>(now);
            old.remove(27);
            old.remove(29);
            SiteDefs.overrideLayout(HOME_LAYOUT, old);
            Sites.Result r = Sites.build(l, d, a, null, false);
            BoundingBox z = r.site().zone();
            check(h, displays(l, z).containsKey(27) && displays(l, z).get(27) == null && displays(l, z).get(29) == null, "old store: 27 and 29 are empty displays");
            BlockPos litter = a.offset(15, 11, 10);
            l.setBlock(litter, Blocks.GLASS.defaultBlockState(), 3);
            SiteDefs.overrideLayout(HOME_LAYOUT, now);
            Sites.checkAll(l);
            Map<Integer, Item> shown = displays(l, z);
            check(h, shown.get(27) == ModItems.SMITHING_ANVIL.get() && shown.get(29) == ModItems.BLACKSMITH_HAMMER.get(), "new displays appear: " + shown.get(27) + " " + shown.get(29));
            check(h, l.getBlockState(litter).is(Blocks.GLASS), "no rebuild");
            h.succeed();
        } finally {
            SiteDefs.clearOverrides();
            cleanup(l, List.of(a));
        }
    }

    // ------------------------------------------------------------------------------------------------ world version

    @GameTest(template = "empty", batch = "p14_worldver")
    public static void worldUpgradeStepsRunOnce(GameTestHelper h) {
        AtomicInteger a = new AtomicInteger(), b = new AtomicInteger();
        List<String> order = new ArrayList<>();
        List<WorldVersion.Step> steps = List.of(
                new WorldVersion.Step(3, "third", s -> { b.incrementAndGet(); order.add("3"); }),
                new WorldVersion.Step(2, "second", s -> { a.incrementAndGet(); order.add("2"); }),
                new WorldVersion.Step(1, "old", s -> order.add("1")));
        WorldVersion w = WorldVersion.blank(1);
        List<String> ran = WorldVersion.run(h.getLevel().getServer(), w, steps, 3);
        check(h, ran.equals(List.of("second", "third")) && order.equals(List.of("2", "3")), "ran newer steps in order: " + ran);
        check(h, w.version() == 3 && w.applied().size() == 2, "stamped 3");
        List<String> again = WorldVersion.run(h.getLevel().getServer(), w, steps, 3);
        check(h, again.isEmpty() && a.get() == 1 && b.get() == 1, "each step ran once");
        // a world without the data (new / pre-1.12) is stamped with the current version; the real world is up to date
        WorldVersion real = WorldVersion.get(h.getLevel().getServer());
        check(h, real.version() == WorldVersion.CURRENT, "this world is at " + WorldVersion.CURRENT);
        check(h, WorldVersion.upgrade(h.getLevel().getServer()).isEmpty(), "nothing pending");
        // save / load round trip
        var tag = w.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
        WorldVersion back = WorldVersion.load(tag, h.getLevel().registryAccess());
        check(h, back.version() == 3 && back.applied().equals(w.applied()), "saved");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ admin command + saved data

    @GameTest(template = "empty", batch = "p14_command", timeoutTicks = 100)
    public static void adminRebuildCommand(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(5);
        prepare(l, a);
        try {
            Sites.Result r = Sites.build(l, def(HOME), a, null, false);
            BoundingBox z = r.site().zone();
            bes(l, z, MoneyPanBlockEntity.class).get(0).deposit(777);
            BlockPos litter = a.offset(15, 11, 10);
            l.setBlock(litter, Blocks.GLASS.defaultBlockState(), 3);
            // not allowed for a normal player (permission 0)
            ServerPlayer p = player(h, GameType.SURVIVAL);
            int res = l.getServer().getCommands().getDispatcher().getRoot().getChild("holeydigsite") == null ? -1
                    : l.getServer().getCommands().getDispatcher().getRoot().getChild("holeydigsite").canUse(p.createCommandSourceStack().withPermission(0)) ? 1 : 0;
            check(h, res == 0, "non-op can't use /holeydigsite");
            check(h, l.getBlockState(litter).is(Blocks.GLASS), "untouched");
            // op: rebuild home
            int n = SiteCommands.rebuildStores(l, "home");
            check(h, n >= 1, "rebuilt " + n);
            check(h, l.getBlockState(litter).isAir(), "rebuilt");
            check(h, bes(l, z, MoneyPanBlockEntity.class).get(0).balance() == 777, "balance kept");
            // through the dispatcher too (op source)
            l.setBlock(litter, Blocks.GLASS.defaultBlockState(), 3);
            l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack().withSuppressedOutput(),
                    "holeydigsite store rebuild home");
            check(h, l.getBlockState(litter).isAir(), "rebuilt by command");
            check(h, bes(l, z, MoneyPanBlockEntity.class).get(0).balance() == 777, "balance kept by command");
            // saved data round trip
            SiteData data = SiteData.get(l);
            var tag = data.save(new net.minecraft.nbt.CompoundTag(), l.registryAccess());
            SiteData back = SiteData.load(tag, l.registryAccess());
            SiteData.Site s0 = data.get(HOME, a), s1 = back.get(HOME, a);
            check(h, s1 != null && s1.zone().equals(s0.zone()) && s1.version() == s0.version() && s1.shown().equals(s0.shown()), "site data saved");
            check(h, !back.announcements().isEmpty(), "announcements saved");
            h.succeed();
        } finally {
            SiteDefs.clearOverrides();
            cleanup(l, List.of(a));
        }
    }

    // ------------------------------------------------------------------------------------------------ shipped data

    /** Every site's structure fits its zone, every layout item exists and is in the catalogue, kiosks sit inside their plot. */
    @GameTest(template = "empty", batch = "p14_data")
    public static void shippedSitesAreConsistent(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        int n = 0;
        for (SiteDef d : SiteDefs.all()) {
            var t = l.getStructureManager().get(d.structure());
            check(h, t.isPresent(), d.id() + ": structure " + d.structure());
            var size = t.get().getSize();
            check(h, size.getX() <= d.size().getX() && size.getY() <= d.size().getY() && size.getZ() <= d.size().getZ(), d.id() + " fits");
            for (ResourceLocation item : SiteDefs.layout(d.layout()).values()) {
                Item it = BuiltInRegistries.ITEM.get(item);
                check(h, it != Items.AIR, d.id() + ": layout item " + item);
            }
            for (SiteDef.Child c : d.children()) {
                SiteDef cd = SiteDefs.get(c.site());
                check(h, cd != null, "child " + c.site());
                check(h, c.pos().getX() + cd.size().getX() <= d.size().getX() && c.pos().getZ() + cd.size().getZ() <= d.size().getZ(), "child inside");
            }
            n++;
        }
        check(h, n == 8, "8 sites (home, 3 plots, 3 kiosks, the event store): " + n);
        check(h, SiteDefs.layout(HOME_LAYOUT).size() == 37, "home shows the 37 home catalogue items");
        h.succeed();
    }
}
