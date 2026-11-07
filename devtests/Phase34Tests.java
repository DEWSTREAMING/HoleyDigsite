package com.holeydigsite.devtest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.team.IslandClaimData;
import com.holeydigsite.team.IslandClaimRules;
import com.holeydigsite.team.IslandClaimRules.Centre;
import com.holeydigsite.team.IslandClaimRules.Chunk;
import com.holeydigsite.team.TeamConfig;
import com.holeydigsite.team.TeamSyncRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Claiming islands in FTB Chunks: the rules (area, team, taken chunks, done once), a small fake of what FTB Chunks does
 * when parties change, the saved record, the settings and the inventory resync when another mod refuses a placement.
 * FTB Chunks itself can't load here, so the real thing is checked by the headless pack test.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase34Tests {
    private static final Gson GSON = new Gson();

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static final Centre C0 = new Centre(8192 + 37, 8192 + 28);

    // ---- area ----

    private static boolean covers(List<Chunk> area, int cx, int cz, int x0, int z0, int x1, int z1) {
        Set<Chunk> set = new HashSet<>(area);
        for (int x = Math.floorDiv(cx + x0, 16); x <= Math.floorDiv(cx + x1, 16); x++) {
            for (int z = Math.floorDiv(cz + z0, 16); z <= Math.floorDiv(cz + z1, 16); z++) {
                if (!set.contains(new Chunk(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The reach constants against the shipped files: template size, the three plots, the event island frame and sections. */
    @GameTest(template = "empty")
    public static void areaCoversTheWholeIsland(GameTestHelper h) {
        JsonObject ev = readJson("/data/holeydigsite/event_island.json");
        JsonObject frame = ev.getAsJsonObject("frame");
        int fx = frame.getAsJsonArray("pos").get(0).getAsInt(), fz = frame.getAsJsonArray("pos").get(2).getAsInt();
        int fw = frame.getAsJsonArray("size").get(0).getAsInt(), fd = frame.getAsJsonArray("size").get(2).getAsInt();
        int minX = fx, maxX = fx + fw - 1, minZ = fz, maxZ = fz + fd - 1;
        for (var e : ev.getAsJsonArray("sections")) {
            JsonObject s = e.getAsJsonObject();
            JsonArray pos = s.getAsJsonArray("pos"), size = s.getAsJsonArray("size");
            minX = Math.min(minX, pos.get(0).getAsInt());
            maxX = Math.max(maxX, pos.get(0).getAsInt() + size.get(0).getAsInt() - 1);
            minZ = Math.min(minZ, pos.get(2).getAsInt());
            maxZ = Math.max(maxZ, pos.get(2).getAsInt() + size.get(2).getAsInt() - 1);
        }
        // template 75 x 57 (tools/make_world.py TW, TD); the plots stand at x 0 / 27 / 54, z 36, 21 square
        minX = Math.min(minX, 0);
        maxX = Math.max(maxX, 74);
        minZ = Math.min(minZ, 0);
        maxZ = Math.max(maxZ, 56);
        int plotMaxX = 54 + 20, plotMaxZ = 36 + 20;
        check(h, plotMaxX <= maxX && plotMaxZ <= maxZ, "bought plots lie inside the template");
        check(h, -IslandClaimRules.WEST <= minX && minX == -31 && IslandClaimRules.EAST == maxX && -IslandClaimRules.NORTH == minZ
                && IslandClaimRules.SOUTH == maxZ, "constants match the files: " + minX + ".." + maxX + " / " + minZ + ".." + maxZ);

        List<Chunk> area0 = IslandClaimRules.area(C0, 0, List.of());
        check(h, covers(area0, C0.x(), C0.z(), minX, minZ, maxX, maxZ), "every block of the island is in a claimed chunk");
        check(h, covers(area0, C0.x(), C0.z(), 0, 36, 74, 56), "all three plots are claimed");
        List<Chunk> area1 = IslandClaimRules.area(C0, 1, List.of());
        check(h, area1.size() > area0.size() && covers(area1, C0.x(), C0.z(), minX - 16, minZ - 16, maxX + 16, maxZ + 16), "margin 1 adds a ring");
        check(h, IslandClaimRules.area(C0, 99, List.of()).size() == IslandClaimRules.area(C0, IslandClaimRules.MAX_MARGIN, List.of()).size(), "margin is capped");
        check(h, area1.size() < 150, "default area is modest: " + area1.size());
        HoleyDigsite.LOGGER.info("Phase34: island area {} chunks with margin 0, {} with margin 1", area0.size(), area1.size());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void areaLeavesOutChunksNearerAnotherIsland(GameTestHelper h) {
        Centre near = new Centre(C0.x() + 90, C0.z());
        List<Chunk> alone = IslandClaimRules.area(C0, 1, List.of());
        List<Chunk> shared = IslandClaimRules.area(C0, 1, List.of(near, C0));
        check(h, shared.size() < alone.size(), "chunks nearer the other island are left out");
        for (Chunk c : shared) {
            long own = sq(C0, c), other = sq(near, c);
            check(h, own <= other, "chunk " + c + " is nearer the other island");
        }
        // islands on the real grid (8192 apart) never compete
        check(h, IslandClaimRules.area(C0, 1, List.of(new Centre(C0.x() + 8192, C0.z()), new Centre(C0.x(), C0.z() - 8192))).equals(alone), "far islands change nothing");
        h.succeed();
    }

    private static long sq(Centre c, Chunk k) {
        long dx = k.x() * 16 + 8 - c.x(), dz = k.z() * 16 + 8 - c.z();
        return dx * dx + dz * dz;
    }

    private static JsonObject readJson(String path) {
        try (InputStream in = Phase34Tests.class.getResourceAsStream(path)) {
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException(path, e);
        }
    }

    // ---- a small world that does what Skyblock Builder + FTB Teams + FTB Chunks do ----

    private static final class World implements IslandClaimRules.Holders, IslandClaimRules.Owners {
        final Map<UUID, UUID> island = new HashMap<>();       // player -> island
        final Map<UUID, UUID> team = new HashMap<>();         // player -> FTB team (own team id = player id, or a party)
        final Map<Chunk, UUID> owner = new HashMap<>();       // claims
        final Map<UUID, Set<Chunk>> originals = new HashMap<>(); // player -> chunks they brought into a party
        final Map<UUID, Centre> centre = new HashMap<>();
        final Set<UUID> done = new HashSet<>();
        boolean enabled = true;
        int claimsMade;

        @Override
        public Set<UUID> members(UUID is) {
            Set<UUID> out = new HashSet<>();
            island.forEach((p, i) -> {
                if (i.equals(is)) out.add(p);
            });
            return out;
        }

        @Override
        public @Nullable UUID teamOf(UUID player) {
            return team.get(player);
        }

        @Override
        public @Nullable UUID ownerOf(Chunk chunk) {
            return owner.get(chunk);
        }

        UUID newPlayer() {
            UUID p = UUID.randomUUID();
            team.put(p, p);
            return p;
        }

        UUID newIsland(int gx) {
            UUID i = UUID.randomUUID();
            centre.put(i, new Centre(gx * 8192 + 37, 28));
            return i;
        }

        /** What the mod does for one island; returns the number of chunks it claimed. */
        int ensure(UUID is) {
            List<Centre> others = new ArrayList<>();
            centre.forEach((k, v) -> {
                if (!k.equals(is)) others.add(v);
            });
            List<Chunk> area = IslandClaimRules.area(centre.get(is), 1, others);
            IslandClaimRules.Plan plan = IslandClaimRules.plan(enabled, is, done.contains(is), area, this, this);
            for (Chunk c : plan.claim()) {
                owner.put(c, plan.team());
                claimsMade++;
            }
            if (plan.finished()) {
                done.add(is);
            }
            return plan.claim().size();
        }

        /** FTB Chunks: a player joins a party. All claims of their old team go to the party and are remembered as theirs. */
        void join(UUID player, UUID party) {
            UUID old = team.get(player);
            Set<Chunk> mine = new HashSet<>();
            owner.forEach((c, t) -> {
                if (t.equals(old)) mine.add(c);
            });
            mine.forEach(c -> owner.put(c, party));
            originals.put(player, mine);
            team.put(player, party);
        }

        /** FTB Chunks: a player leaves; their remembered chunks go back to their own team. If the party is gone, all of it goes to them. */
        void leave(UUID player, boolean partyGone) {
            UUID party = team.get(player);
            Set<Chunk> back = new HashSet<>(originals.getOrDefault(player, Set.of()));
            if (partyGone) {
                owner.forEach((c, t) -> {
                    if (t.equals(party)) back.add(c);
                });
            }
            back.forEach(c -> owner.put(c, player));
            originals.remove(player);
            team.put(player, player);
        }

        long owned(UUID t) {
            return owner.values().stream().filter(t::equals).count();
        }
    }

    // ---- the team to claim for ----

    @GameTest(template = "empty")
    public static void claimingTeamIsTheOneEveryoneShares(GameTestHelper h) {
        World w = new World();
        UUID a = w.newPlayer(), b = w.newPlayer(), is = w.newIsland(1), party = UUID.randomUUID();
        check(h, IslandClaimRules.claimingTeam(is, w) == null, "an empty island has no team");
        w.island.put(a, is);
        check(h, a.equals(IslandClaimRules.claimingTeam(is, w)), "alone: the player's own team");
        w.island.put(b, is);
        check(h, IslandClaimRules.claimingTeam(is, w) == null, "two players in two teams: nobody to claim for");
        w.team.put(a, party);
        w.team.put(b, party);
        check(h, party.equals(IslandClaimRules.claimingTeam(is, w)), "in one party: the party");
        w.team.remove(b);
        check(h, IslandClaimRules.claimingTeam(is, w) == null, "a member FTB doesn't know: nothing guessed");
        UUID spawn = TeamSyncRules.SPAWN;
        w.island.put(a, spawn);
        w.team.put(a, a);
        check(h, a.equals(IslandClaimRules.claimingTeam(spawn, w)), "spawn island is claimed for its resident in Phase 34b");
        h.succeed();
    }

    // ---- planning ----

    @GameTest(template = "empty")
    public static void newIslandIsClaimedOnceForItsTeam(GameTestHelper h) {
        World w = new World();
        UUID a = w.newPlayer(), is = w.newIsland(1);
        w.island.put(a, is);
        int n = w.ensure(is);
        check(h, n > 0 && w.owned(a) == n, "all of the area claimed for the player's team: " + n);
        check(h, w.done.contains(is), "island marked done");
        check(h, w.ensure(is) == 0, "running again claims nothing");
        w.done.clear();
        check(h, w.ensure(is) == 0 && w.claimsMade == n, "even without the record, owned chunks are not claimed twice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void settingOffClaimsNothing(GameTestHelper h) {
        World w = new World();
        UUID a = w.newPlayer(), is = w.newIsland(1);
        w.island.put(a, is);
        w.enabled = false;
        check(h, w.ensure(is) == 0 && w.owner.isEmpty() && w.done.isEmpty(), "off: nothing claimed and not marked done");
        w.enabled = true;
        check(h, w.ensure(is) > 0, "switched on later: claimed then");
        // switching off leaves what is claimed
        w.enabled = false;
        long before = w.owner.size();
        w.ensure(is);
        check(h, w.owner.size() == before, "off again: existing claims untouched");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chunksOfAnotherTeamStayTheirs(GameTestHelper h) {
        World w = new World();
        UUID a = w.newPlayer(), other = UUID.randomUUID(), is = w.newIsland(1);
        w.island.put(a, is);
        List<Chunk> area = IslandClaimRules.area(w.centre.get(is), 1, List.of());
        Chunk taken = area.get(5), mine = area.get(6);
        w.owner.put(taken, other);
        w.owner.put(mine, a);
        int n = w.ensure(is);
        check(h, other.equals(w.owner.get(taken)), "the other team keeps its chunk");
        check(h, n == area.size() - 2, "the rest is claimed (not the taken one, not the one already ours): " + n);
        check(h, w.done.contains(is), "done");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyIslandKeepsItsClaimAndGetsNoNewOne(GameTestHelper h) {
        World w = new World();
        UUID a = w.newPlayer(), is = w.newIsland(1), empty = w.newIsland(2);
        w.island.put(a, is);
        w.ensure(is);
        check(h, w.ensure(empty) == 0 && !w.done.contains(empty), "an island nobody lives on is not claimed");
        // the player leaves the island for another: it keeps its claim, nothing is removed
        UUID is2 = w.newIsland(3);
        w.island.put(a, is2);
        long before = w.owned(a);
        w.ensure(is);
        check(h, w.owned(a) == before, "the island they left stays claimed");
        w.ensure(is2);
        check(h, w.owned(a) > before, "the new one is claimed too");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldWorldIslandsAreClaimedOnLogin(GameTestHelper h) {
        World w = new World();
        UUID a = w.newPlayer(), b = w.newPlayer(), party = UUID.randomUUID(), is = w.newIsland(1), is2 = w.newIsland(2);
        w.island.put(a, is);
        w.island.put(b, is2);
        w.team.put(a, party);
        w.team.put(b, party);
        w.island.put(b, is);
        check(h, w.ensure(is) > 0 && w.owned(party) > 0 && w.owned(a) == 0, "an island already in a party is claimed for the party");
        w.island.put(b, is2);
        w.team.put(b, b);
        check(h, w.ensure(is2) > 0 && w.owned(b) > 0, "a loner's island is claimed for them");
        check(h, w.ensure(is) == 0 && w.ensure(is2) == 0, "second login changes nothing");
        h.succeed();
    }

    // ---- teams changing ----

    @GameTest(template = "empty")
    public static void joiningLeavingReturningAndDisbanding(GameTestHelper h) {
        World w = new World();
        UUID a = w.newPlayer(), b = w.newPlayer(), isA = w.newIsland(1), isB = w.newIsland(2), party = UUID.randomUUID();
        w.island.put(a, isA);
        w.island.put(b, isB);
        w.ensure(isA);
        w.ensure(isB);
        long sizeA = w.owned(a), sizeB = w.owned(b);
        check(h, sizeA > 0 && sizeA == sizeB, "both islands claimed for their own player");

        // B makes a party, A joins and moves to B's island
        w.join(b, party);
        w.join(a, party);
        w.island.put(a, isB);
        check(h, w.ensure(isB) == 0 && w.ensure(isA) == 0, "joining: nothing new to claim, nothing removed");
        check(h, w.owned(party) == sizeA + sizeB && w.owned(a) == 0 && w.owned(b) == 0, "both islands now belong to the party");
        check(h, IslandClaimRules.claimingTeam(isB, w).equals(party), "the shared island is the party's");
        check(h, IslandClaimRules.claimingTeam(isA, w) == null, "the island they left is empty");

        // A leaves and returns to the old island
        w.leave(a, false);
        w.island.put(a, isA);
        check(h, w.ensure(isA) == 0 && w.ensure(isB) == 0, "leaving: nothing new to claim");
        check(h, w.owned(a) == sizeA && w.owned(party) == sizeB, "the old island is protected again for A, the party keeps the other");

        // A joins again, then the party is disbanded: everything goes back to the leavers
        w.join(a, party);
        w.island.put(a, isB);
        w.leave(a, false);
        w.leave(b, true);
        w.island.put(a, isA);
        check(h, w.ensure(isA) == 0 && w.ensure(isB) == 0, "disbanding: nothing new to claim");
        check(h, w.owned(a) == sizeA && w.owned(b) == sizeB && w.owned(party) == 0, "each island belongs to its own player again");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void membersInDifferentTeamsAreWaitedFor(GameTestHelper h) {
        World w = new World();
        UUID a = w.newPlayer(), b = w.newPlayer(), is = w.newIsland(1), party = UUID.randomUUID();
        w.island.put(a, is);
        w.island.put(b, is);
        check(h, w.ensure(is) == 0 && !w.done.contains(is), "not claimed while the island's people are in different teams");
        w.team.put(a, party);
        w.team.put(b, party);
        check(h, w.ensure(is) > 0 && w.owned(party) > 0, "claimed once they share a party");
        h.succeed();
    }

    // ---- the record ----

    @GameTest(template = "empty")
    public static void claimRecordSurvivesSaving(GameTestHelper h) {
        IslandClaimData d = IslandClaimData.get(h.getLevel().getServer());
        UUID is = UUID.randomUUID();
        check(h, !d.isDone(is), "new island is not done");
        List<Chunk> chunks = List.of(new Chunk(1, 2), new Chunk(-3, 4));
        d.finish(is, chunks);
        check(h, d.isDone(is) && d.claimed(is).equals(chunks), "recorded");
        CompoundTag tag = d.save(new CompoundTag(), h.getLevel().registryAccess());
        var back = IslandClaimData.load(tag, h.getLevel().registryAccess());
        check(h, back.isDone(is) && back.claimed(is).equals(chunks), "survives a save and load");
        check(h, !back.isDone(UUID.randomUUID()), "other islands stay not done");
        d.finish(is, List.of());
        h.succeed();
    }

    // ---- settings ----

    @GameTest(template = "empty")
    public static void settingsDefaults(GameTestHelper h) {
        check(h, TeamConfig.CLAIM_ISLANDS.getDefault(), "claimIslands is on by default");
        check(h, TeamConfig.CLAIM_MARGIN_CHUNKS.getDefault() == 1, "margin default is one chunk");
        check(h, TeamConfig.SYNC_FTB_TEAMS.getDefault(), "syncFtbTeams default unchanged");
        h.succeed();
    }

    // ---- another mod refusing a placement ----

    private static final class CountingSync implements ContainerSynchronizer {
        final AtomicInteger full = new AtomicInteger();

        @Override
        public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
            full.incrementAndGet();
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {}

        @Override
        public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {}

        @Override
        public void sendDataChange(AbstractContainerMenu menu, int id, int value) {}
    }

    @GameTest(template = "empty")
    public static void refusedPlacementResyncsTheInventory(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        CountingSync sync = new CountingSync();
        p.containerMenu.setSynchronizer(sync);
        int base = sync.full.get();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        BlockSnapshot snap = BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), pos);
        BlockEvent.EntityPlaceEvent ok = new BlockEvent.EntityPlaceEvent(snap, Blocks.AIR.defaultBlockState(), p);
        NeoForge.EVENT_BUS.post(ok);
        check(h, sync.full.get() == base, "an allowed placement sends nothing extra");
        BlockEvent.EntityPlaceEvent refused = new BlockEvent.EntityPlaceEvent(snap, Blocks.AIR.defaultBlockState(), p);
        refused.setCanceled(true);
        NeoForge.EVENT_BUS.post(refused);
        check(h, sync.full.get() > base, "a refused placement sends the inventory again");
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }
}
