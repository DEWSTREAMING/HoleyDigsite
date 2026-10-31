package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.board.BoardStats;
import com.holeydigsite.board.ScoreBoardBlockEntity;
import com.holeydigsite.event.EventChips;
import com.holeydigsite.event.EventDefs;
import com.holeydigsite.event.EventIsland;
import com.holeydigsite.event.EventIslandData;
import com.holeydigsite.event.OpalChips;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.plot.PlotBoardBlock;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.sellpad.EventSellPadBlock;
import com.holeydigsite.shop.AbstractPadBlock;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopButtonBlock;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.Sites;
import com.holeydigsite.site.WorldVersion;
import com.holeydigsite.team.TeamAccess;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Event island (hidden nodes, kiosk, deeds, access, the event change, the old-world step), Opal Chips, the event store and the
 * boards. Islands are placed far away (x/z 30000+), each test in its own batch, and removed at the end.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase28bTests {
    private static final Block[] ROCKS = {ModBlocks.RED_DIRT_ROCK.get(), ModBlocks.MULLOCK_ROCK.get(), ModBlocks.OPAL_CLAY_ROCK.get()};

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static BlockPos origin(int n) {
        return new BlockPos(30000 + n * 256, 80, 30000);
    }

    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        p.getAbilities().instabuild = mode == GameType.CREATIVE;
        p.getInventory().clearContent();
        return p;
    }

    private static EventIsland.Layout layout() {
        EventIsland.Layout l = EventIsland.layout();
        if (l == null) throw new IllegalStateException("no event_island.json loaded");
        return l;
    }

    private static BoundingBox area(BlockPos origin) {
        return layout().frameBox(origin).inflatedBy(2);
    }

    private static EventIsland.Placed place(GameTestHelper h, BlockPos origin) {
        ServerLevel l = h.getLevel();
        cleanup(l, origin);
        return EventIsland.place(l, origin, EventDefs.getEvent("outback"));
    }

    /** Removes the island's blocks, sites, plots and record (the GameTest world is kept between runs). */
    private static void cleanup(ServerLevel l, BlockPos origin) {
        BoundingBox box = area(origin);
        SiteData sd = SiteData.get(l);
        for (SiteData.Site s : new ArrayList<>(sd.all())) {
            if (box.isInside(s.anchor())) sd.remove(s.key());
        }
        PlotData pd = PlotData.get(l);
        for (PlotData.Plot p : new ArrayList<>(pd.all())) {
            if (box.isInside(p.board())) pd.remove(p.id(), p.board());
        }
        EventIslandData.get(l).remove(origin);
        Sites.clear(l, box);
        for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1))) {
            e.discard();
        }
    }

    private static BlockPos sectionBoard(BlockPos origin, int tier) {
        return layout().section(tier).boardAt(origin);
    }

    private static void own(ServerLevel l, BlockPos board, String plot) {
        PlotData.get(l).setOwned(plot, board, true, "test", null);
        l.setBlock(board, l.getBlockState(board).setValue(PlotBoardBlock.OWNED, true), 3);
    }

    // ------------------------------------------------------------------------------------------------ the island

    /** Sections are Hardpan with pockets of only their own rock, nothing on the surface; the kiosk and the boards are there. */
    @GameTest(template = "empty", batch = "p28b_nodes", timeoutTicks = 400)
    public static void hiddenNodesOwnRockNothingOnSurface(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos o = origin(0);
        try {
            EventIsland.Placed p = place(h, o);
            check(h, p.blocked() == 0, "placed into empty space without blocked spots: " + p.blocked());
            int[] last = {Integer.MAX_VALUE};
            for (EventIsland.Section s : layout().sections()) {
                BoundingBox box = s.box(o);
                int[] counts = new int[3];
                int hardpan = 0;
                for (BlockPos q : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                    BlockState st = l.getBlockState(q);
                    if (st.is(ModBlocks.HARDPAN.get())) hardpan++;
                    for (int i = 0; i < 3; i++) {
                        if (st.is(ROCKS[i])) {
                            counts[i]++;
                            check(h, !l.getBlockState(q.above()).isAir() && !l.getBlockState(q.above(2)).isAir(),
                                    "tier " + s.tier() + " rock at " + q + " shows (air within 2 above)");
                            check(h, q.getY() <= o.getY() + 7, "tier " + s.tier() + " rock too high at " + q);
                        }
                    }
                }
                int own = counts[s.tier() - 1];
                check(h, own > 0, "tier " + s.tier() + " has rock pockets");
                for (int i = 0; i < 3; i++) {
                    check(h, i == s.tier() - 1 || counts[i] == 0, "tier " + s.tier() + " holds rock of tier " + (i + 1));
                }
                check(h, hardpan > own * 10, "tier " + s.tier() + " is mostly Hardpan (" + hardpan + " vs " + own + ")");
                check(h, own < last[0], "fewer rock blocks in the higher tier: " + own + " after " + last[0]);
                last[0] = own;
                BlockState board = l.getBlockState(s.boardAt(o));
                check(h, board.is(ModBlocks.PLOT_BOARD.get()) && !board.getValue(PlotBoardBlock.OWNED), "tier " + s.tier() + " board, for sale");
                check(h, PlotData.get(l).get(s.plot(), s.boardAt(o)) != null, "tier " + s.tier() + " plot registered");
            }
            // the kiosk: a recorded site with the deeds on display, the Event Sell Pad and the Event Board on its wall
            BlockPos kiosk = o.offset(layout().shopPos());
            SiteData.Site site = SiteData.get(l).get(HoleyDigsite.id("event_shop"), kiosk);
            check(h, site != null, "kiosk site recorded");
            boolean deed = false, pad = false;
            for (BlockEntity be : Sites.blockEntities(l, site.zone())) {
                if (be instanceof ShopDisplayBlockEntity d && d.slot() == 1) deed = d.item() == ModItems.LAND_DEED_EVENT_T1.get();
                if (be.getBlockState().is(ModBlocks.EVENT_SELL_PAD.get())) pad = true;
            }
            check(h, deed && pad, "kiosk shows the Tier 1 deed (" + deed + ") and has the Event Sell Pad (" + pad + ")");
            boolean board = false;
            for (BlockPos q : BlockPos.betweenClosed(site.zone().minX() - 1, site.zone().minY(), site.zone().minZ() - 1,
                    site.zone().maxX() + 1, site.zone().maxY(), site.zone().maxZ() + 1)) {
                if (l.getBlockState(q).is(ModBlocks.EVENT_BOARD.get())) board = true;
            }
            check(h, board, "Event Board at the kiosk");
            check(h, EventIslandData.get(l).get(o) != null && EventIslandData.get(l).get(o).event().equals("outback"), "island recorded for outback");
        } finally {
            cleanup(l, o);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ deeds and access

    /** Tier 2 needs Tier 1; a bought section is the island team's (member yes, visitor no), machines work there. */
    @GameTest(template = "empty", batch = "p28b_deeds", timeoutTicks = 400)
    public static void deedsInOrderAndTeamAccess(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos o = origin(1);
        TeamAccess before = TeamAccess.get();
        try {
            place(h, o);
            ServerPlayer member = player(h, GameType.SURVIVAL), visitor = player(h, GameType.SURVIVAL);
            UUID team = UUID.randomUUID();
            BoundingBox island = layout().frameBox(o);
            TeamAccess.set(new TeamAccess() {
                @Override
                public @Nullable UUID islandTeamAt(ServerLevel level, BlockPos pos) {
                    return island.isInside(pos) ? team : before.islandTeamAt(level, pos);
                }

                @Override
                public Set<UUID> members(ServerLevel level, UUID t) {
                    return t.equals(team) ? Set.of(member.getUUID()) : before.members(level, t);
                }

                @Override
                public boolean sameTeam(ServerLevel level, UUID a, UUID b) {
                    return a.equals(b);
                }
            });
            BlockPos b1 = sectionBoard(o, 1), b2 = sectionBoard(o, 2);
            ItemStack deed2 = new ItemStack(ModItems.LAND_DEED_EVENT_T2.get());
            check(h, !PlotBoardBlock.claim(l, b2, member, deed2) && deed2.getCount() == 1, "Tier 2 refused before Tier 1, deed kept");
            ItemStack deed1 = new ItemStack(ModItems.LAND_DEED_EVENT_T1.get());
            check(h, !PlotBoardBlock.claim(l, b1, visitor, deed1) && deed1.getCount() == 1, "a visitor can't buy the team's section");
            check(h, PlotBoardBlock.claim(l, b1, member, deed1) && deed1.isEmpty(), "member buys Tier 1");
            check(h, PlotBoardBlock.claim(l, b2, member, deed2) && deed2.isEmpty(), "then Tier 2");
            check(h, PlotBoardBlock.teamPlotCount(l, b1) == 0, "event sections don't count as land plots");
            BlockPos inside = layout().section(1).box(o).getCenter().atY(o.getY() + 9);
            check(h, PlotLock.access(member, l, inside) == PlotLock.Access.OK, "member uses the bought section");
            check(h, PlotLock.access(visitor, l, inside) == PlotLock.Access.OTHER_TEAM, "visitor gets the normal refusal");
            check(h, PlotLock.access(null, l, inside) == PlotLock.Access.OK && !PlotLock.isLocked(l, inside), "machines work there");
            BlockPos t3 = layout().section(3).box(o).getCenter().atY(o.getY() + 9);
            check(h, PlotLock.access(member, l, t3) == PlotLock.Access.LOCKED, "unbought Tier 3 is locked");
        } finally {
            TeamAccess.set(before);
            cleanup(l, o);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ event change

    /** A new active event rebuilds the sections: players' blocks and a machine with its contents go into a chest at the gate,
     * holes are filled, ownership stays, the old event's board is frozen. */
    @GameTest(template = "empty", batch = "p28b_change", timeoutTicks = 400)
    public static void eventChangeRebuildsSections(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos o = origin(2);
        String next = "t28b_second";
        BoardStats stats = BoardStats.get(l.getServer());
        boolean outbackFrozen = stats.frozen("outback");
        try {
            place(h, o);
            EventIsland.Section s1 = layout().section(1);
            BlockPos a = o.offset(s1.pos());
            BlockPos b1 = s1.boardAt(o);
            own(l, b1, s1.plot());
            BlockPos surface = a.offset(6, 9, 10), placed = a.offset(8, 10, 10), padPos = a.offset(10, 10, 10);
            check(h, l.getBlockState(surface).is(ModBlocks.PINDAN.get()), "Pindan on top: " + l.getBlockState(surface));
            // dig a hole, place a block and a Sell Pad with clumps on it
            l.setBlock(surface, Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(surface.below(), Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(placed, Blocks.COBBLESTONE.defaultBlockState(), 3);
            l.setBlock(padPos, ModBlocks.SELL_PAD.get().defaultBlockState(), 3);
            ((PadBlockEntity) l.getBlockEntity(padPos)).insert(new ItemStack(ModItems.DIRT_CLUMP.get(), 3));
            EventDefs.EventDef outback = EventDefs.getEvent("outback");
            EventDefs.putEventForTest(next, new EventDefs.EventDef(next, "event.holeydigsite.outback", outback.tiers(), outback.chips()));
            EventDefs.setActiveEventForTest(next);
            int rebuilt = EventIsland.checkEventChange(l);
            check(h, rebuilt >= 1, "the island was rebuilt for the new event");
            check(h, EventIslandData.get(l).get(o).event().equals(next), "island now built for " + next);
            check(h, l.getBlockState(surface).is(ModBlocks.PINDAN.get()) && l.getBlockState(surface.below()).is(ModBlocks.HARDPAN.get()),
                    "hole filled: " + l.getBlockState(surface));
            check(h, !l.getBlockState(placed).is(Blocks.COBBLESTONE) && !l.getBlockState(padPos).is(ModBlocks.SELL_PAD.get()), "players' blocks taken out");
            Map<String, Integer> got = new HashMap<>();
            int chests = 0;
            for (BlockPos rel : s1.chests()) {
                for (int up = 0; up < 3; up++) {
                    if (l.getBlockEntity(a.offset(rel).above(up)) instanceof ChestBlockEntity c) {
                        chests++;
                        for (int i = 0; i < c.getContainerSize(); i++) {
                            ItemStack st = c.getItem(i);
                            if (!st.isEmpty()) got.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), st.getCount(), Integer::sum);
                        }
                    }
                }
            }
            check(h, chests >= 1, "a chest at the gate");
            check(h, got.getOrDefault("cobblestone", 0) == 1 && got.getOrDefault("sell_pad", 0) == 1 && got.getOrDefault("dirt_clump", 0) == 3,
                    "block, machine and contents in the chest: " + got);
            PlotData.Plot p = PlotData.get(l).get(s1.plot(), b1);
            check(h, p != null && p.owned() && l.getBlockState(b1).getValue(PlotBoardBlock.OWNED), "ownership kept");
            check(h, stats.frozen("outback") && !stats.frozen(next), "old board frozen, new one counts");
        } finally {
            EventDefs.setActiveEventForTest(null);
            EventDefs.putEventForTest(next, null);
            if (!outbackFrozen) stats.unfreeze("outback");
            cleanup(l, o);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ old worlds

    /** The WorldVersion step's placement goes into air only: a block of the player's in the way stays, the rest is placed once. */
    @GameTest(template = "empty", batch = "p28b_oldworld", timeoutTicks = 400)
    public static void oldWorldStepLeavesBlockedSpot(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos o = origin(3);
        try {
            cleanup(l, o);
            check(h, WorldVersion.CURRENT == 5 && WorldVersion.STEPS.stream().anyMatch(s -> s.version() == 5), "a version 5 step");
            BlockPos home = o.offset(layout().home());
            BlockPos post = o.offset(7, 10, -7);                      // the gate's west post
            l.setBlock(post, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            EventIsland.Placed p = EventIsland.ensure(l, home);
            check(h, p != null && p.origin().equals(o), "placed at the template origin: " + (p == null ? null : p.origin()));
            check(h, p.blocked() >= 1 && l.getBlockState(post).is(Blocks.OAK_PLANKS), "the player's block stays: " + p.blocked());
            check(h, l.getBlockState(post.above()).is(Blocks.STRIPPED_ACACIA_LOG), "the rest of the post is placed");
            check(h, l.getBlockState(sectionBoard(o, 1)).is(ModBlocks.PLOT_BOARD.get()), "sections placed");
            check(h, EventIsland.ensure(l, home) == null, "only once");
        } finally {
            cleanup(l, o);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ Opal Chips

    /** Chips are per event, the total earned never goes down, only the active event's chips spend, they survive death, no command. */
    @GameTest(template = "empty", batch = "p28b_chips")
    public static void opalChipsPerEventAndNoCommand(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL);
        String other = "t28b_other";
        try {
            check(h, EventChips.balance(p) == 0, "starts empty");
            EventChips.earn(p, 50);
            OpalChips c = OpalChips.get(p);
            check(h, c.balance("outback") == 50 && c.earned("outback") == 50, "earned 50");
            check(h, EventChips.spend(p, 20) && c.balance("outback") == 30 && c.earned("outback") == 50, "spent 20, earned stays 50");
            check(h, !EventChips.spend(p, 31) && c.balance("outback") == 30, "can't overspend");
            EventDefs.setActiveEventForTest(other);
            check(h, EventChips.balance(p) == 0 && !EventChips.spend(p, 10), "outback chips can't be spent in another event");
            EventChips.earn(p, 5);
            check(h, c.balance(other) == 5 && c.balance("outback") == 30, "per event");
            EventDefs.setActiveEventForTest(null);
            check(h, EventChips.balance(p) == 30 && EventChips.spend(p, 10), "outback back on: its chips work again");
            ServerPlayer respawned = h.makeMockServerPlayerInLevel();
            respawned.restoreFrom(p, false);
            OpalChips after = OpalChips.get(respawned);
            check(h, after.balance("outback") == 20 && after.earned("outback") == 50 && after.balance(other) == 5, "kept through death");
            // no command gives, sets or shows a way to change chips
            CommandNode<CommandSourceStack> root = l(h).getServer().getCommands().getDispatcher().getRoot();
            List<String> names = new ArrayList<>();
            collect(root.getChild("holeydigsite"), names, 0);
            check(h, !names.isEmpty() && names.stream().noneMatch(n -> n.contains("chip")), "no chip command: " + names.stream().filter(n -> n.contains("chip")).toList());
        } finally {
            EventDefs.setActiveEventForTest(null);
        }
        h.succeed();
    }

    private static ServerLevel l(GameTestHelper h) {
        return h.getLevel();
    }

    private static void collect(@Nullable CommandNode<CommandSourceStack> n, List<String> out, int depth) {
        if (n == null || depth > 12) return;
        out.add(n.getName().toLowerCase());
        for (CommandNode<CommandSourceStack> c : n.getChildren()) collect(c, out, depth + 1);
    }

    // ------------------------------------------------------------------------------------------------ event store

    /** The Event Sell Pad pays the right chips and refuses other items; normal Sell Pads refuse event finds; chips buy the crate. */
    @GameTest(template = "empty", batch = "p28b_store")
    public static void eventSellPadPaysChipsAndBuysCrate(GameTestHelper h) {
        BlockPos pad = new BlockPos(1, 1, 1), button = new BlockPos(2, 1, 1), buy = new BlockPos(3, 1, 1);
        h.setBlock(pad, ModBlocks.EVENT_SELL_PAD.get());
        h.setBlock(button, ModBlocks.SHOP_BUTTON.get());
        h.setBlock(buy, ModBlocks.BUY_PAD.get());
        ServerLevel l = h.getLevel();
        ServerPlayer p = player(h, GameType.SURVIVAL);
        EventSellPadBlock block = (EventSellPadBlock) ModBlocks.EVENT_SELL_PAD.get();
        check(h, !block.accepts(new ItemStack(Items.DIRT), l) && !block.accepts(new ItemStack(ModItems.DIRT_CLUMP.get()), l),
                "refuses other items");
        check(h, block.refusedMessage(new ItemStack(Items.DIRT)).getContents() instanceof TranslatableContents t
                && t.getKey().equals("message.holeydigsite.event_sell_pad.refused"), "with its own line");
        check(h, block.accepts(new ItemStack(ModItems.POTCH.get()), l) && block.accepts(new ItemStack(ModItems.OUTBACK_GOLD_INGOT.get()), l),
                "takes event finds");
        check(h, !((AbstractPadBlock) ModBlocks.SELL_PAD.get()).accepts(new ItemStack(ModItems.POTCH.get()), l)
                && !((AbstractPadBlock) ModBlocks.SELL_PAD.get()).accepts(new ItemStack(ModItems.OUTBACK_GOLD_INGOT.get()), l),
                "the normal Sell Pad still refuses event finds");
        PadBlockEntity pe = (PadBlockEntity) h.getBlockEntity(pad);
        pe.insert(new ItemStack(ModItems.POTCH.get(), 3));                 // 3 x 2
        pe.insert(new ItemStack(ModItems.IRONSTONE_PEBBLE.get(), 5));      // 5 x 1
        pe.insert(new ItemStack(ModItems.OUTBACK_GOLD_INGOT.get(), 1));    // 10 g at 1.2 = 12
        long expected = EventDefs.chipValue(new ItemStack(ModItems.POTCH.get(), 3)) + EventDefs.chipValue(new ItemStack(ModItems.IRONSTONE_PEBBLE.get(), 5))
                + EventDefs.chipValue(new ItemStack(ModItems.OUTBACK_GOLD_INGOT.get()));
        check(h, expected == 6 + 5 + 12, "chip table: " + expected);
        long before = EventChips.balance(p);
        ShopButtonBlock.Result r = ShopButtonBlock.press(l, h.absolutePos(button), p);
        check(h, r.chipsEarned() == expected && EventChips.balance(p) == before + expected && pe.isEmpty(), "paid " + r.chipsEarned());
        // buying the crate with chips
        PadBlockEntity be = (PadBlockEntity) h.getBlockEntity(buy);
        be.insert(BlueprintItem.of(ModItems.OUTBACK_CRATE.get()));
        long bal = EventChips.balance(p);
        ShopButtonBlock.Result short1 = ShopButtonBlock.press(l, h.absolutePos(button), p);
        check(h, bal < 90 && short1.chipsSpent() == 0 && !be.isEmpty(), "not enough chips: nothing bought");
        EventChips.earn(p, 100);
        long earned = OpalChips.get(p).earned("outback");
        h.runAfterDelay(8, () -> {
            ShopButtonBlock.Result r2 = ShopButtonBlock.press(l, h.absolutePos(button), p);
            check(h, r2.chipsSpent() == 90 && EventChips.balance(p) == bal + 100 - 90, "spent 90 chips: " + r2.chipsSpent());
            check(h, p.getInventory().countItem(ModItems.OUTBACK_CRATE.get()) == 1, "got the crate");
            check(h, OpalChips.get(p).earned("outback") == earned, "earned total unchanged by spending");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ boards

    private static List<String> args(Component c) {
        List<String> out = new ArrayList<>();
        if (c.getContents() instanceof TranslatableContents t) {
            for (Object a : t.getArgs()) out.add(a instanceof Component ac ? ac.getString() : String.valueOf(a));
        }
        return out;
    }

    /** Counts per player, sorting by clumps or chips, an offline player on the board, the world board's coins page. */
    @GameTest(template = "empty", batch = "p28b_boards")
    public static void boardsCountSortAndShowOffline(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        String ev = "t28b_board";
        BoardStats stats = BoardStats.get(l.getServer());
        ServerPlayer a = player(h, GameType.SURVIVAL), b = player(h, GameType.SURVIVAL);
        UUID carol = UUID.randomUUID();
        stats.name(carol, "Carol28b");
        stats.name(a.getUUID(), "Alice28b");
        stats.name(b.getUUID(), "Bob28b");
        try {
            for (int i = 0; i < 5; i++) stats.addClump(l.getServer(), a.getUUID(), 9, ev);
            for (int i = 0; i < 2; i++) stats.addClump(l.getServer(), b.getUUID(), 9, ev);
            for (int i = 0; i < 7; i++) stats.addClump(l.getServer(), carol, 10, ev);   // offline: not on the server
            stats.addChips(a, ev, 50);
            stats.addChips(b, ev, 80);
            check(h, stats.eventClumps(ev, a.getUUID()) == 5 && stats.eventClumps(ev, carol) == 7 && stats.eventChips(ev, b.getUUID()) == 80,
                    "counts per player");
            check(h, stats.clumps(carol, 10) >= 7 && stats.clumps(a.getUUID(), 9) >= 5, "world clumps per kind");
            List<BoardStats.Row> byClumps = stats.eventTop(ev, false, 10), byChips = stats.eventTop(ev, true, 10);
            check(h, byClumps.size() == 3 && byClumps.get(0).name().equals("Carol28b") && byClumps.get(1).id().equals(a.getUUID()),
                    "sorted by clumps: " + byClumps);
            check(h, byChips.get(0).id().equals(b.getUUID()) && byChips.get(1).id().equals(a.getUUID()), "sorted by chips: " + byChips);
            check(h, stats.rank(ev, carol) == 1, "Carol first");
            // the Event Board shows the active event's table, the offline player included
            EventDefs.setActiveEventForTest(ev);
            h.setBlock(new BlockPos(1, 1, 1), ModBlocks.EVENT_BOARD.get());
            ScoreBoardBlockEntity board = (ScoreBoardBlockEntity) h.getBlockEntity(new BlockPos(1, 1, 1));
            List<Component> lines = board.buildLines(l.getServer());
            check(h, lines.size() >= 5 && args(lines.get(2)).contains("Carol28b") && args(lines.get(2)).contains("7"), "first row Carol: " + args(lines.get(2)));
            board.toggleSort();
            lines = board.buildLines(l.getServer());
            check(h, args(lines.get(2)).contains("80"), "sorted by chips on the board: " + args(lines.get(2)));
            // the frozen board stops counting
            stats.freeze(ev);
            stats.addClump(l.getServer(), a.getUUID(), 9, ev);
            check(h, stats.eventClumps(ev, a.getUUID()) == 5, "frozen board doesn't change");
            // the Leaderboard: lifetime coins (page 1), offline player included
            stats.seedCoins(carol, 999_999_000L);
            h.setBlock(new BlockPos(2, 1, 1), ModBlocks.LEADERBOARD.get());
            ScoreBoardBlockEntity world = (ScoreBoardBlockEntity) h.getBlockEntity(new BlockPos(2, 1, 1));
            List<Component> wl = world.buildLines(l.getServer());
            check(h, args(wl.get(2)).contains("Carol28b"), "coins page, Carol on top: " + args(wl.get(2)));
            // no Ledger level depends on events: the event milestones give no Renown
            for (String m : List.of("first_event_clump", "first_event_plot", "event_top_ten")) {
                LedgerDefs.Milestone ms = LedgerDefs.milestone(HoleyDigsite.id(m));
                check(h, ms != null && ms.renown() == 0 && ms.chapter().equals("events"), "milestone " + m);
            }
        } finally {
            EventDefs.setActiveEventForTest(null);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ op command: whole rebuild

    private static Map<String, Integer> chestItems(ServerLevel l, BlockPos anchor, List<BlockPos> spots) {
        Map<String, Integer> got = new HashMap<>();
        for (BlockPos rel : spots) {
            for (int up = 0; up < 3; up++) {
                if (l.getBlockEntity(anchor.offset(rel).above(up)) instanceof ChestBlockEntity c) {
                    for (int i = 0; i < c.getContainerSize(); i++) {
                        ItemStack st = c.getItem(i);
                        if (!st.isEmpty()) got.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), st.getCount(), Integer::sum);
                    }
                }
            }
        }
        return got;
    }

    /** {@code /holeydigsite site event rebuild}: frame and sections back to the template, players' things (with contents) in
     * chests, the moved Event Board back at its spot with its page, ownership kept. */
    @GameTest(template = "empty", batch = "p28b_rebuild", timeoutTicks = 400)
    public static void wholeIslandRebuildCommand(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos o = origin(4);
        try {
            place(h, o);
            EventIsland.Layout lay = layout();
            EventIsland.Section s1 = lay.section(1);
            BlockPos a = o.offset(s1.pos());
            own(l, s1.boardAt(o), s1.plot());
            // frame: a block, a chest with diamonds, a dug hole, the board moved
            BlockPos fBlock = o.offset(0, 10, -11), fChest = o.offset(2, 10, -11), hole = null;
            check(h, l.getBlockState(fBlock).isAir() && l.getBlockState(fChest).isAir(), "free frame spots: " + l.getBlockState(fBlock) + " " + l.getBlockState(fChest));
            for (int x = -4; x <= 4 && hole == null; x++) {
                if (l.getBlockState(o.offset(x, 9, -13)).is(ModBlocks.PINDAN.get())) hole = o.offset(x, 9, -13);
            }
            check(h, hole != null, "a Pindan spot on the frame");
            l.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(fBlock, Blocks.COBBLESTONE.defaultBlockState(), 3);
            l.setBlock(fChest, Blocks.CHEST.defaultBlockState(), 3);
            ((ChestBlockEntity) l.getBlockEntity(fChest)).setItem(0, new ItemStack(Items.DIAMOND, 5));
            BlockPos board = o.offset(lay.board()), moved = o.offset(4, 11, -11);
            check(h, l.getBlockState(board).is(ModBlocks.EVENT_BOARD.get()), "board at its spot");
            l.setBlock(board, Blocks.AIR.defaultBlockState(), 3);
            l.setBlock(moved, ModBlocks.EVENT_BOARD.get().defaultBlockState(), 3);
            ((ScoreBoardBlockEntity) l.getBlockEntity(moved)).nextPage();
            // a section: a block and a Sell Pad with clumps
            BlockPos sBlock = a.offset(8, 10, 10), sPad = a.offset(10, 10, 10);
            l.setBlock(sBlock, Blocks.COBBLESTONE.defaultBlockState(), 3);
            l.setBlock(sPad, ModBlocks.SELL_PAD.get().defaultBlockState(), 3);
            ((PadBlockEntity) l.getBlockEntity(sPad)).insert(new ItemStack(ModItems.DIRT_CLUMP.get(), 3));
            var src = l.getServer().createCommandSourceStack().withLevel(l).withPosition(net.minecraft.world.phys.Vec3.atCenterOf(o.offset(9, 10, -10)))
                    .withPermission(4).withSuppressedOutput();
            int n;
            try {
                n = l.getServer().getCommands().getDispatcher().execute("holeydigsite site event rebuild", src);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new IllegalStateException(e);
            }
            check(h, n == 1, "one island rebuilt: " + n);
            check(h, l.getBlockState(board).is(ModBlocks.EVENT_BOARD.get()) && !l.getBlockState(moved).is(ModBlocks.EVENT_BOARD.get()), "board back in place");
            check(h, ((ScoreBoardBlockEntity) l.getBlockEntity(board)).page() == 1, "board keeps its page");
            check(h, l.getBlockState(hole).is(ModBlocks.PINDAN.get()) && !l.getBlockState(fBlock).is(Blocks.COBBLESTONE), "frame back to the template");
            Map<String, Integer> frame = chestItems(l, o, lay.frameChests());
            check(h, frame.getOrDefault("cobblestone", 0) == 1 && frame.getOrDefault("chest", 0) == 1 && frame.getOrDefault("diamond", 0) == 5,
                    "frame things in chests by the kiosk: " + frame);
            Map<String, Integer> sec = chestItems(l, a, s1.chests());
            check(h, sec.getOrDefault("cobblestone", 0) == 1 && sec.getOrDefault("sell_pad", 0) == 1 && sec.getOrDefault("dirt_clump", 0) == 3,
                    "section things in chests at its gate: " + sec);
            PlotData.Plot p = PlotData.get(l).get(s1.plot(), s1.boardAt(o));
            check(h, p != null && p.owned(), "ownership kept");
        } finally {
            cleanup(l, o);
        }
        h.succeed();
    }
}
