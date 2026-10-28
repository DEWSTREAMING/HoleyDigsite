package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.block.PanItemHandler;
import com.holeydigsite.plot.PlotBoardBlock;
import com.holeydigsite.plot.PlotBoardBlockEntity;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.MoneyPanBlock;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.team.TeamAccess;
import com.holeydigsite.team.TeamSyncRules;
import com.holeydigsite.team.TeamSyncRules.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teams: island-team plot access, claiming, the Money Pan, the per-team "plot bought" count, and the FTB Teams to
 * Skyblock Builder sync decisions. The team mods can't load in GameTests, so a fake {@link TeamAccess} gives teams to
 * areas of each test only (everywhere else answers "no team", as without a team mod), and the sync runs on fakes.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase27Tests {

    // ---- fake island teams: per test area, so tests running side by side don't see each other's teams ----

    private record Area(BoundingBox box, UUID team) {}

    private static final Map<UUID, Area> AREAS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> MEMBERS = new ConcurrentHashMap<>();

    private static final TeamAccess FAKE = new TeamAccess() {
        @Override
        public @Nullable UUID islandTeamAt(ServerLevel level, BlockPos pos) {
            for (Area a : AREAS.values()) {
                if (a.box().isInside(pos)) {
                    return a.team();
                }
            }
            return null;
        }

        @Override
        public Set<UUID> members(ServerLevel level, UUID team) {
            return MEMBERS.getOrDefault(team, Set.of());
        }

        @Override
        public boolean sameTeam(ServerLevel level, UUID a, UUID b) {
            for (Set<UUID> m : MEMBERS.values()) {
                if (m.contains(a)) return m.contains(b);
            }
            return !MEMBERS.values().stream().anyMatch(m -> m.contains(b));
        }
    };

    /** Gives the test-relative x range [x0, x1] (full height, z 0..4) to a new team with these members. */
    private static UUID team(GameTestHelper h, int x0, int x1, ServerPlayer... members) {
        if (TeamAccess.get() != FAKE) {
            TeamAccess.set(FAKE);
        }
        UUID id = UUID.randomUUID();
        BlockPos a = h.absolutePos(new BlockPos(x0, -1, 0)), b = h.absolutePos(new BlockPos(x1, 8, 4));
        AREAS.put(id, new Area(BoundingBox.fromCorners(a, b), id));
        Set<UUID> m = ConcurrentHashMap.newKeySet();
        for (ServerPlayer p : members) m.add(p.getUUID());
        MEMBERS.put(id, m);
        return id;
    }

    private static void dropTeams(UUID... ids) {
        for (UUID id : ids) {
            AREAS.remove(id);
            MEMBERS.remove(id);
        }
    }

    // ---- helpers (as in PhaseElevenTests) ----

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 26; x++) for (int y = 1; y <= 4; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 0; x < 26; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        p.getInventory().clearContent();
        return p;
    }

    private static void click(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
    }

    private static PlotBoardBlockEntity plot(GameTestHelper h, String id, BlockPos board, int x0, int x1) {
        h.setBlock(board, ModBlocks.PLOT_BOARD.get().defaultBlockState().setValue(PlotBoardBlock.FACING, Direction.WEST));
        PlotBoardBlockEntity b = (PlotBoardBlockEntity) h.getBlockEntity(board);
        b.setPlot(id, new BlockPos(x0 - board.getX(), -board.getY(), -board.getZ()), new BlockPos(x1 - board.getX(), 4 - board.getY(), 4 - board.getZ()));
        b.register();
        return b;
    }

    private static void buy(GameTestHelper h, String id, BlockPos board) {
        PlotData.get(h.getLevel()).setOwned(id, h.absolutePos(board), true, "test", null);
        h.getLevel().setBlock(h.absolutePos(board), h.getBlockState(board).setValue(PlotBoardBlock.OWNED, true), 3);
    }

    private static void forget(GameTestHelper h, String id, BlockPos board) {
        PlotData.get(h.getLevel()).remove(id, h.absolutePos(board));
    }

    // ------------------------------------------------------------------------------------------------
    // Plot access
    // ------------------------------------------------------------------------------------------------

    /** A bought plot on a team's island: the member digs and builds, a visitor gets the "another team" refusal. */
    @GameTest(template = "empty_long")
    public static void islandTeamUsesBoughtPlotVisitorDoesNot(GameTestHelper h) {
        clear(h);
        BlockPos board = new BlockPos(8, 1, 0);
        ServerPlayer member = player(h, GameType.SURVIVAL), visitor = player(h, GameType.SURVIVAL);
        UUID t = team(h, 0, 25, member);
        try {
            plot(h, "t27_a", board, 8, 20);
            buy(h, "t27_a", board);
            BlockPos inside = new BlockPos(12, 1, 2);
            BlockPos abs = h.absolutePos(inside);
            check(h, PlotLock.access(member, h.getLevel(), abs) == PlotLock.Access.OK, "member: ok");
            check(h, PlotLock.access(visitor, h.getLevel(), abs) == PlotLock.Access.OTHER_TEAM, "visitor: other team");
            h.setBlock(inside, Blocks.DIRT);
            visitor.gameMode.destroyBlock(abs);
            check(h, h.getBlockState(inside).is(Blocks.DIRT), "visitor can't break");
            click(h, visitor, new BlockPos(14, 0, 2), new ItemStack(Items.COBBLESTONE, 4));
            check(h, h.getBlockState(new BlockPos(14, 1, 2)).isAir() && visitor.getMainHandItem().getCount() == 4, "visitor can't place");
            member.gameMode.destroyBlock(abs);
            check(h, h.getBlockState(inside).isAir(), "member breaks");
            click(h, member, new BlockPos(14, 0, 2), new ItemStack(Items.COBBLESTONE, 4));
            check(h, h.getBlockState(new BlockPos(14, 1, 2)).is(Blocks.COBBLESTONE), "member places");
            // creative is never stopped, as before
            ServerPlayer creative = player(h, GameType.CREATIVE);
            check(h, PlotLock.access(creative, h.getLevel(), abs) == PlotLock.Access.OK, "creative: ok");
            // outside the plot the team doesn't matter
            check(h, PlotLock.access(visitor, h.getLevel(), h.absolutePos(new BlockPos(4, 1, 2))) == PlotLock.Access.OK, "outside: ok");
            check(h, PlotLock.Access.OTHER_TEAM.messageKey().equals("message.holeydigsite.plot.other_team"), "own message");
        } finally {
            forget(h, "t27_a", board);
            dropTeams(t);
        }
        h.succeed();
    }

    /** No team on the island (no team mod): a bought plot is open to everyone, an unbought one still locked. */
    @GameTest(template = "empty_long")
    public static void noTeamModBehavesAsBefore(GameTestHelper h) {
        clear(h);
        BlockPos b1 = new BlockPos(2, 1, 0), b2 = new BlockPos(14, 1, 0);
        try {
            plot(h, "t27_b", b1, 2, 10);
            plot(h, "t27_c", b2, 14, 22);
            buy(h, "t27_b", b1);
            ServerPlayer anyone = player(h, GameType.SURVIVAL);
            check(h, TeamAccess.EVERYONE.islandTeamAt(h.getLevel(), h.absolutePos(b1)) == null, "EVERYONE has no teams");
            check(h, TeamAccess.EVERYONE.allows(h.getLevel(), h.absolutePos(b1), anyone.getUUID()), "EVERYONE allows");
            check(h, PlotLock.access(anyone, h.getLevel(), h.absolutePos(new BlockPos(5, 1, 2))) == PlotLock.Access.OK, "bought: anyone");
            check(h, PlotLock.access(anyone, h.getLevel(), h.absolutePos(new BlockPos(18, 1, 2))) == PlotLock.Access.LOCKED, "unbought: locked");
            h.setBlock(new BlockPos(5, 1, 2), Blocks.DIRT);
            anyone.gameMode.destroyBlock(h.absolutePos(new BlockPos(5, 1, 2)));
            check(h, h.getBlockState(new BlockPos(5, 1, 2)).isAir(), "anyone digs in a bought plot");
        } finally {
            forget(h, "t27_b", b1);
            forget(h, "t27_c", b2);
        }
        h.succeed();
    }

    /** Machines are not players: a pan in a bought plot of a team still takes clumps from a hopper-style handler. */
    @GameTest(template = "empty_long")
    public static void machinesStillWorkInBoughtPlot(GameTestHelper h) {
        clear(h);
        BlockPos board = new BlockPos(8, 1, 0), pan = new BlockPos(12, 1, 2);
        UUID t = team(h, 0, 25); // a team nobody here is in
        try {
            plot(h, "t27_d", board, 8, 20);
            buy(h, "t27_d", board);
            h.setBlock(pan, ModBlocks.PAN.get());
            BlockPos abs = h.absolutePos(pan);
            check(h, PlotLock.access(null, h.getLevel(), abs) == PlotLock.Access.OK && !PlotLock.isLocked(h.getLevel(), abs), "machine access ok");
            ItemStack left = new PanItemHandler(h.getLevel(), abs).insertItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 1), false);
            check(h, left.isEmpty() && PanBlock.fill(h.getBlockState(pan)) > 0, "pan took the clump: left " + left);
        } finally {
            forget(h, "t27_d", board);
            dropTeams(t);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Claiming, Money Pan, ledger count
    // ------------------------------------------------------------------------------------------------

    /** Only the island's team can put a deed on its board; the visitor keeps the deed. */
    @GameTest(template = "empty_long")
    public static void onlyIslandTeamClaims(GameTestHelper h) {
        clear(h);
        BlockPos board = new BlockPos(8, 1, 0);
        ServerPlayer member = player(h, GameType.SURVIVAL), visitor = player(h, GameType.SURVIVAL);
        UUID t = team(h, 0, 25, member);
        try {
            plot(h, "deep_dig", board, 8, 20);
            click(h, visitor, board, new ItemStack(ModItems.LAND_DEED_DEEP_DIG.get()));
            check(h, visitor.getMainHandItem().getCount() == 1 && !h.getBlockState(board).getValue(PlotBoardBlock.OWNED), "visitor refused, deed kept");
            click(h, member, board, new ItemStack(ModItems.LAND_DEED_DEEP_DIG.get()));
            check(h, member.getMainHandItem().isEmpty() && h.getBlockState(board).getValue(PlotBoardBlock.OWNED), "member claims");
            PlotData.Plot p = PlotData.get(h.getLevel()).get("deep_dig", h.absolutePos(board));
            check(h, p != null && p.isOwnedBy(member.getUUID()), "buyer stored as owner");
        } finally {
            forget(h, "deep_dig", board);
            dropTeams(t);
        }
        h.succeed();
    }

    /** Anyone pays in; only the island's team takes coins out. */
    @GameTest(template = "empty_long")
    public static void moneyPanTakeByTeamOnly(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(5, 1, 2);
        ServerPlayer member = player(h, GameType.SURVIVAL), visitor = player(h, GameType.SURVIVAL);
        UUID t = team(h, 0, 12, member);
        try {
            h.setBlock(pos, ModBlocks.MONEY_PAN.get());
            MoneyPanBlockEntity pan = (MoneyPanBlockEntity) h.getBlockEntity(pos);
            BlockPos abs = h.absolutePos(pos);
            long in = MoneyPanBlock.depositHeld(h.getLevel(), abs, visitor, new ItemStack(ModItems.SILVER_COIN.get(), 3));
            check(h, in > 0 && pan.balance() == in, "visitor paid in: " + pan.balance());
            check(h, MoneyPanBlock.withdraw(h.getLevel(), abs, visitor) == 0 && pan.balance() == in, "visitor can't take");
            check(h, visitor.getInventory().items.stream().noneMatch(s -> s.is(ModItems.SILVER_COIN.get())), "visitor got nothing");
            check(h, MoneyPanBlock.withdraw(h.getLevel(), abs, member) == in && pan.balance() == 0, "member takes all");
            // a pan with no team (no team mod): anyone takes, as before
            BlockPos free = new BlockPos(20, 1, 2);
            h.setBlock(free, ModBlocks.MONEY_PAN.get());
            MoneyPanBlock.depositHeld(h.getLevel(), h.absolutePos(free), member, new ItemStack(ModItems.COPPER_COIN.get(), 5));
            check(h, MoneyPanBlock.withdraw(h.getLevel(), h.absolutePos(free), visitor) == 5, "no team: anyone takes");
        } finally {
            dropTeams(t);
        }
        h.succeed();
    }

    /** "Plot bought" counts the plots of the board's island team; without teams, all bought plots as before. */
    @GameTest(template = "empty_long")
    public static void plotBoughtCountsPerTeam(GameTestHelper h) {
        clear(h);
        BlockPos a1 = new BlockPos(1, 1, 0), a2 = new BlockPos(6, 1, 0), b1 = new BlockPos(16, 1, 0);
        try {
            plot(h, "t27_e", a1, 1, 4);
            plot(h, "t27_f", a2, 6, 9);
            plot(h, "t27_g", b1, 16, 19);
            buy(h, "t27_e", a1);
            buy(h, "t27_f", a2);
            buy(h, "t27_g", b1);
            ServerLevel l = h.getLevel();
            int before = PlotBoardBlock.teamPlotCount(l, h.absolutePos(a1)); // no team here yet: every bought plot without a team
            UUID ta = team(h, 0, 12), tb = team(h, 13, 25);
            try {
                check(h, PlotBoardBlock.teamPlotCount(l, h.absolutePos(a1)) == 2, "team A: 2, got " + PlotBoardBlock.teamPlotCount(l, h.absolutePos(a1)));
                check(h, PlotBoardBlock.teamPlotCount(l, h.absolutePos(b1)) == 1, "team B: 1");
            } finally {
                dropTeams(ta, tb);
            }
            check(h, before >= 3, "no teams: all bought plots count (" + before + ")");
        } finally {
            forget(h, "t27_e", a1);
            forget(h, "t27_f", a2);
            forget(h, "t27_g", b1);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Sync decisions (fake Skyblock Builder islands + fake FTB parties)
    // ------------------------------------------------------------------------------------------------

    /** A tiny world of islands and parties that applies the actions like the real sync does. */
    private static final class World implements TeamSyncRules.Islands, TeamSyncRules.Parties {
        final Map<UUID, UUID> island = new HashMap<>();   // player -> island team
        final Map<UUID, UUID> party = new HashMap<>();    // player -> party
        final Map<UUID, UUID> owner = new HashMap<>();    // party -> owner
        final Set<UUID> madeParties = new HashSet<>();
        int freshIslands;

        @Override
        public UUID islandOf(UUID player) {
            return island.get(player);
        }

        @Override
        public Set<UUID> members(UUID is) {
            Set<UUID> out = new HashSet<>();
            island.forEach((p, i) -> {
                if (i.equals(is)) out.add(p);
            });
            return out;
        }

        @Override
        public UUID partyOf(UUID player) {
            return party.get(player);
        }

        @Override
        public UUID ownerOf(UUID p) {
            return owner.get(p);
        }

        UUID makeParty(UUID creator) {
            UUID p = UUID.randomUUID();
            party.put(creator, p);
            owner.put(p, creator);
            return p;
        }

        void apply(List<Action> actions) {
            for (Action a : actions) {
                switch (a) {
                    case TeamSyncRules.Move m -> island.put(m.player(), m.island());
                    case TeamSyncRules.FreshIsland f -> {
                        island.put(f.player(), UUID.randomUUID());
                        freshIslands++;
                    }
                    case TeamSyncRules.MakeParty mp -> {
                        makeParty(mp.player());
                        madeParties.add(mp.player());
                    }
                }
            }
        }

        /** Every player's login check; must be empty once the world is in step. */
        List<Action> loginAll() {
            List<Action> out = new ArrayList<>();
            for (UUID p : new HashSet<>(island.keySet())) {
                out.addAll(TeamSyncRules.reconcile(true, p, false, this, this));
            }
            return out;
        }
    }

    /** Join, leave, kick, disband, made-on-a-held-island, login mismatch, newcomers, island command, sync off, no loop. */
    @GameTest(template = "empty")
    public static void syncDecisions(GameTestHelper h) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        World w = new World();
        // first player: Skyblock Builder puts them on spawn, nobody else there, so they keep it
        w.island.put(a, TeamSyncRules.SPAWN);
        check(h, TeamSyncRules.newcomer(true, a, w).isEmpty(), "first player keeps the spawn island");
        // second player: spawn is taken, so an island of their own
        w.island.put(b, TeamSyncRules.SPAWN);
        List<Action> nb = TeamSyncRules.newcomer(true, b, w);
        check(h, nb.equals(List.of(new TeamSyncRules.FreshIsland(b))), "newcomer gets own island: " + nb);
        w.apply(nb);
        UUID bIsland = w.islandOf(b);
        // A makes a party: it lives on A's island, nobody moves
        UUID pa = w.makeParty(a);
        check(h, TeamSyncRules.reconcile(true, a, true, w, w).isEmpty(), "party made: no move");
        // B joins: moved to A's island, B's old island id stays known (nothing deleted)
        w.party.put(b, pa);
        List<Action> join = TeamSyncRules.reconcile(true, b, false, w, w);
        check(h, join.equals(List.of(new TeamSyncRules.Move(b, TeamSyncRules.SPAWN))), "join moves to owner's island: " + join);
        w.apply(join);
        check(h, w.loginAll().isEmpty(), "in step after join (no loop)");
        // sync off: nothing at all
        w.party.put(c, pa);
        w.island.put(c, UUID.randomUUID());
        check(h, TeamSyncRules.reconcile(false, c, false, w, w).isEmpty(), "sync off: no join move");
        check(h, TeamSyncRules.newcomer(false, c, w).isEmpty() && TeamSyncRules.islandCreated(false, w.islandOf(c), w, w).isEmpty(), "sync off: nothing");
        // login mismatch: C joined while offline (or an old world) -> moved on login
        List<Action> login = TeamSyncRules.reconcile(true, c, false, w, w);
        check(h, login.equals(List.of(new TeamSyncRules.Move(c, TeamSyncRules.SPAWN))), "login mismatch fixed: " + login);
        w.apply(login);
        // B leaves (or is kicked: the same change for B): A's party holds the island, so B gets a fresh one
        w.party.remove(b);
        List<Action> leave = TeamSyncRules.reconcile(true, b, false, w, w);
        check(h, leave.equals(List.of(new TeamSyncRules.FreshIsland(b))), "leave/kick: fresh island: " + leave);
        w.apply(leave);
        check(h, !Objects.equals(w.islandOf(b), TeamSyncRules.SPAWN) && !Objects.equals(w.islandOf(b), bIsland), "B on a new island");
        // disband: members are kicked first (fresh islands), the owner leaves last and keeps the island
        w.party.remove(c);
        w.apply(TeamSyncRules.reconcile(true, c, false, w, w));
        w.party.remove(a);
        w.owner.remove(pa);
        check(h, TeamSyncRules.reconcile(true, a, false, w, w).isEmpty() && TeamSyncRules.SPAWN.equals(w.islandOf(a)), "disband: owner keeps island");
        check(h, w.freshIslands == 3 && !Objects.equals(w.islandOf(c), TeamSyncRules.SPAWN), "C got a fresh island too");
        check(h, w.loginAll().isEmpty(), "in step after disband (no loop)");
        // a party made on an island another party already holds gets its own island
        UUID d = UUID.randomUUID();
        w.island.put(d, w.islandOf(a));
        w.makeParty(a);
        w.makeParty(d);
        List<Action> second = TeamSyncRules.reconcile(true, d, true, w, w);
        check(h, second.equals(List.of(new TeamSyncRules.FreshIsland(d))), "second party on a held island moves: " + second);
        w.apply(second);
        check(h, w.loginAll().isEmpty(), "in step (no loop)");
        // an old shared island with no parties stays shared
        UUID e = UUID.randomUUID(), f = UUID.randomUUID(), shared = UUID.randomUUID();
        w.island.put(e, shared);
        w.island.put(f, shared);
        check(h, TeamSyncRules.reconcile(true, e, false, w, w).isEmpty(), "old shared island left alone");
        // Skyblock Builder's own create command: the creator gets a matching party, which is then in step
        UUID g = UUID.randomUUID(), gIsland = UUID.randomUUID();
        w.island.put(g, gIsland);
        List<Action> made = TeamSyncRules.islandCreated(true, gIsland, w, w);
        check(h, made.equals(List.of(new TeamSyncRules.MakeParty(g, gIsland))), "island command makes a party: " + made);
        w.apply(made);
        check(h, w.partyOf(g) != null && TeamSyncRules.reconcile(true, g, true, w, w).isEmpty(), "party made from island: no loop");
        check(h, TeamSyncRules.islandCreated(true, gIsland, w, w).isEmpty(), "no second party");
        check(h, w.loginAll().isEmpty(), "everything in step");
        h.succeed();
    }
}
