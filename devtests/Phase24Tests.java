package com.holeydigsite.devtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.contract.Contract;
import com.holeydigsite.contract.ContractBoardBlock;
import com.holeydigsite.contract.ContractData;
import com.holeydigsite.contract.ContractDefs;
import com.holeydigsite.contract.ContractKind;
import com.holeydigsite.contract.ContractManager;
import com.holeydigsite.contract.ContractText;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.site.Boards;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.Sites;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Contracts (Phase 24): rolls only offer gated entries, rotation by day, partial and over-delivery, what each kind accepts,
 * rewards, two players at one board, the board in an old world's Trading Post, saving, milestones, commands.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase24Tests {
    private static final ResourceLocation FOLKIRON = HoleyDigsite.id("folkiron");
    private static final ResourceLocation SILVANE = HoleyDigsite.id("silvane");
    private static final ResourceLocation RUBY = HoleyDigsite.id("ruby");
    private static final ResourceLocation SAPPHIRE = HoleyDigsite.id("sapphire");

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static ServerPlayer fresh(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        LedgerData.get(p).resetAll();
        return p;
    }

    private static void setLevel(ServerPlayer p, int level) {
        LedgerData.get(p).setRenown(LedgerDefs.curve().cumulativeForLevel(level));
    }

    /** A seed that does not look like its neighbours (the random source gives alike first numbers for seeds 1, 2, 3). */
    private static long seed(long i) {
        long z = i * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static ContractManager.Eligibility el(int level, String... plots) {
        return new ContractManager.Eligibility(level, new HashSet<>(List.of(plots)));
    }

    private static long coinValue(Player p) {
        long total = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() == ModItems.COPPER_COIN.get()) total += (long) s.getCount() * Selling.COPPER;
            else if (s.getItem() == ModItems.SILVER_COIN.get()) total += (long) s.getCount() * Selling.SILVER;
            else if (s.getItem() == ModItems.GOLD_COIN.get()) total += (long) s.getCount() * Selling.GOLD;
            else if (s.getItem() == ModItems.PLATINUM_COIN.get()) total += (long) s.getCount() * Selling.PLATINUM;
            else if (s.getItem() == ModItems.RUBY_CROWN.get()) total += (long) s.getCount() * Selling.CROWN;
            else if (s.getItem() == ModItems.MINT_BAR.get()) total += (long) s.getCount() * Selling.BAR;
        }
        return total;
    }

    private static int count(Player p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static Contract metal(ResourceLocation metal, int grams, int minIngot, long reward) {
        return new Contract("t", ContractKind.METAL, metal, "", null, null, grams, minIngot, reward, 10, 0);
    }

    private static Contract gem(ResourceLocation gem, String form, int count, int minWeight) {
        return new Contract("t", ContractKind.GEM, gem, form, null, null, count, minWeight, 500, 10, 0);
    }

    private static Contract food(Item item, int count) {
        return new Contract("t", ContractKind.FOOD, BuiltInRegistries.ITEM.getKey(item), "", null, null, count, 0, 300, 10, 0);
    }

    private static Contract piece(Item item, ResourceLocation metal, String gem, int minGrams) {
        return new Contract("t", ContractKind.PIECE, BuiltInRegistries.ITEM.getKey(item), "", metal, gem, 1, minGrams, 900, 12, 0);
    }

    private static ItemStack ingot(Item item, int grams) {
        return IngotItem.of(item, grams);
    }

    private static ItemStack smithed(ServerPlayer p, Item piece, List<SmithData.Ingot> ingots, Optional<SmithData.Gem> gem) {
        return SmithPieces.create(piece, new SmithData(ingots, gem), p.registryAccess());
    }

    // ------------------------------------------------------------------------------------------------ pool and rolls

    @GameTest(template = "empty")
    public static void poolHasAllFourKindsAndIsValid(GameTestHelper h) {
        for (ContractKind k : ContractKind.values()) {
            check(h, ContractDefs.entries(k).size() >= 8, k + " has entries: " + ContractDefs.entries(k).size());
        }
        Set<String> ids = new HashSet<>();
        var reg = h.getLevel().registryAccess();
        for (ContractDefs.Entry e : ContractDefs.entries()) {
            check(h, ids.add(e.id()), "unique id " + e.id());
            check(h, e.amountMin() >= 1 && e.amountMax() >= e.amountMin() && e.weight() > 0, "amounts of " + e.id());
            check(h, e.reward() >= 1.3 && e.reward() <= 1.6, "reward multiplier of " + e.id());
            check(h, e.renown() >= 10 && e.renown() <= 25, "Renown of " + e.id());
            long reward = ContractManager.reward(e, e.amountMax(), reg);
            check(h, reward > 0, "priced " + e.id());
            if (e.kind() == ContractKind.FOOD || e.kind() == ContractKind.PIECE) {
                check(h, BuiltInRegistries.ITEM.containsKey(e.item()), "item exists for " + e.id());
            }
            if (!e.plot().isEmpty()) {
                check(h, Set.of("deep_dig", "gravel_flats", "sand_dunes").contains(e.plot()), "plot id of " + e.id());
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rollsOnlyOfferGatedEntries(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        Set<String> seenAtLow = new HashSet<>();
        for (int i = 0; i < 600; i++) {
            Contract c = ContractManager.roll(reg, el(3), Set.of(), RandomSource.create(seed(i)), 0);
            check(h, c != null, "something to roll at level 3");
            ContractDefs.Entry e = ContractDefs.entries().stream().filter(x -> x.id().equals(c.entry)).findFirst().orElseThrow();
            check(h, e.minLevel() <= 3 && e.plot().isEmpty(), "level 3 only gets open entries: " + e.id());
            seenAtLow.add(e.id());
        }
        check(h, seenAtLow.size() >= 4, "variety at level 3: " + seenAtLow);
        // a plot opens its own entries, and only its own
        Set<String> gravelOnly = new HashSet<>();
        for (int i = 0; i < 1500; i++) {
            Contract c = ContractManager.roll(reg, el(40, "gravel_flats"), Set.of(), RandomSource.create(seed(i)), 0);
            ContractDefs.Entry e = ContractDefs.entries().stream().filter(x -> x.id().equals(c.entry)).findFirst().orElseThrow();
            check(h, e.eligible(40, Set.of("gravel_flats")), "eligible: " + e.id());
            check(h, !"sand_dunes".equals(e.plot()), "no Sandrock entry without Sand Dunes: " + e.id());
            if ("gravel_flats".equals(e.plot())) gravelOnly.add(e.id());
        }
        check(h, gravelOnly.size() >= 3, "Gravel Flats entries appear: " + gravelOnly);
        // a late player with every plot sees all four kinds
        Set<ContractKind> kinds = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            kinds.add(ContractManager.roll(reg, el(40, "deep_dig", "gravel_flats", "sand_dunes"), Set.of(), RandomSource.create(seed(7000 + i)), 0).kind);
        }
        check(h, kinds.size() == 4, "all four kinds: " + kinds);
        // open keys are never repeated
        Contract a = ContractManager.roll(reg, el(40, "gravel_flats", "sand_dunes"), Set.of(), RandomSource.create(1), 0);
        Contract b = ContractManager.roll(reg, el(40, "gravel_flats", "sand_dunes"), Set.of(a.key()), RandomSource.create(1), 0);
        check(h, !a.key().equals(b.key()), "different item");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rewardsAreAboveTheSellPad(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        // 300 g of Folkiron ingots sells for 300 x 6.25 = 1875 copper
        ContractDefs.Entry e = ContractDefs.entries().stream().filter(x -> x.id().equals("folkiron_mid")).findFirst().orElseThrow();
        long sellPad = Selling.unitValue(ingot(ModItems.FOLKIRON_INGOT.get(), 300), reg);
        long reward = ContractManager.reward(e, 300, reg);
        check(h, sellPad == 1875, "sell value " + sellPad);
        check(h, reward >= sellPad * 1.3 - 8 && reward <= sellPad * 1.6, "reward " + reward + " is 1.3-1.6 times " + sellPad);
        check(h, reward % ContractDefs.rules().rewardStep() == 0, "rounded to silver");
        // a cut Sapphire (2 g) is worth 660; three of them with the entry multiplier
        ContractDefs.Entry g = ContractDefs.entries().stream().filter(x -> x.id().equals("sapphire_medium")).findFirst().orElseThrow();
        long three = ContractManager.reward(g, 3, reg);
        long sell = 3L * Selling.unitValue(new ItemStack(ModItems.SAPPHIRE_MEDIUM.get()), reg);
        check(h, three >= sell * 1.3 - 8 && three <= sell * 1.6, "gem reward " + three + " vs " + sell);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ rotation

    @GameTest(template = "empty")
    public static void rotationRefillsFinishedAndKeepsUnfinishedUpToThreeDays(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        setLevel(p, 30);
        ContractData d = ContractManager.data(p);
        ContractManager.refresh(p, 10);
        check(h, d.size() == 3 && d.get(0) != null && d.get(1) != null && d.get(2) != null, "three contracts");
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 3; i++) check(h, keys.add(d.get(i).key()), "no two the same kind and item");
        Contract a0 = d.get(0), a1 = d.get(1), a2 = d.get(2);
        check(h, a0.issued == 10 && d.lastDay() == 10, "issued today");

        ContractManager.refresh(p, 10);
        check(h, d.get(0) == a0 && d.get(1) == a1 && d.get(2) == a2, "same day, nothing changes");

        check(h, ContractManager.forceComplete(p, 0), "finish slot 1");
        check(h, d.get(0) == a0 && a0.isDone(), "a finished contract stays on the board until the next day");
        ContractManager.refresh(p, 10);
        check(h, d.get(0) == a0, "still done the same day");
        ContractManager.refresh(p, 11);
        check(h, d.get(0) != a0 && !d.get(0).isDone() && d.get(0).issued == 11, "finished slot refilled on the next day");
        check(h, d.get(1) == a1 && d.get(2) == a2, "unfinished ones stay");
        ContractManager.refresh(p, 12);
        check(h, d.get(1) == a1 && d.get(2) == a2, "still there on day 3 of their life");
        check(h, a1.daysLeft(12, 3) == 1, "last day: " + a1.daysLeft(12, 3));
        ContractManager.refresh(p, 13);
        check(h, d.get(1) != a1 && d.get(2) != a2 && d.get(1).issued == 13 && d.get(2).issued == 13, "swapped after three days");
        check(h, d.get(0).issued == 11, "the newer one is kept");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sameDayRefreshIsStableAndSeededPerPlayer(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        setLevel(p, 30);
        ContractManager.refresh(p, 40);
        List<String> first = new ArrayList<>();
        for (int i = 0; i < 3; i++) first.add(ContractManager.data(p).get(i).entry);
        ContractManager.data(p).clear();
        ContractManager.refresh(p, 40);
        List<String> again = new ArrayList<>();
        for (int i = 0; i < 3; i++) again.add(ContractManager.data(p).get(i).entry);
        check(h, first.equals(again), "the same player and day roll the same contracts: " + first + " vs " + again);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ delivering

    @GameTest(template = "empty")
    public static void partialAndOverDeliveryKeepsTheRest(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        ContractData d = ContractManager.data(p);
        d.set(0, food(Items.WHEAT, 20));
        d.setLastDay(5);
        ItemStack hand = new ItemStack(Items.WHEAT, 12);
        int taken = ContractManager.deliver(p, 0, hand);
        check(h, taken == 12 && hand.isEmpty(), "all 12 taken");
        check(h, d.get(0).delivered() == 12 && !d.get(0).isDone(), "progress 12 / 20 is saved");
        check(h, coinValue(p) == 0, "no pay yet");
        ItemStack more = new ItemStack(Items.WHEAT, 40);
        taken = ContractManager.deliver(p, 0, more);
        check(h, taken == 8 && more.getCount() == 32, "only the 8 still needed are taken, 32 stay in the hand: " + taken + "/" + more.getCount());
        check(h, d.get(0).isDone() && d.completed() == 1, "done");
        check(h, coinValue(p) == 300, "paid 300 copper: " + coinValue(p));
        ItemStack after = new ItemStack(Items.WHEAT, 5);
        check(h, ContractManager.deliver(p, 0, after) == 0 && after.getCount() == 5, "a finished contract takes nothing");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void metalCountsGramsAndRespectsMinimumIngotWeight(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        var reg = p.registryAccess();
        Contract any = metal(FOLKIRON, 300, 0, 1000);
        Contract min = metal(FOLKIRON, 300, 100, 1000);
        ItemStack small = ingot(ModItems.FOLKIRON_INGOT.get(), 50);
        ItemStack big = ingot(ModItems.FOLKIRON_INGOT.get(), 200);
        check(h, ContractManager.unitValue(any, small, reg) == 50, "a 50 g ingot counts 50 g");
        check(h, ContractManager.unitValue(min, small, reg) == 0, "50 g is under the minimum ingot weight");
        check(h, ContractManager.unitValue(min, big, reg) == 200, "200 g counts");
        check(h, ContractManager.unitValue(any, ingot(ModItems.GOLDCHRO_INGOT.get(), 200), reg) == 0, "wrong metal");
        check(h, ContractManager.unitValue(any, new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get()), reg) == 0, "chunks are not ingots");
        // two 200 g ingots for 300 g: both are taken (ingots are whole), the contract is done
        ContractData d = ContractManager.data(p);
        d.set(0, any);
        ItemStack two = ingot(ModItems.FOLKIRON_INGOT.get(), 200);
        two.setCount(3);
        int taken = ContractManager.deliver(p, 0, two);
        check(h, taken == 2 && two.getCount() == 1 && any.isDone() && any.delivered() == 300, "2 ingots finish 300 g, the third stays: " + taken);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gemsMatchMaterialCutStateAndWeight(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        var reg = p.registryAccess();
        Contract cut = gem(RUBY, "cut", 3, 2);
        Contract uncut = gem(RUBY, "uncut", 3, 0);
        check(h, ContractManager.unitValue(cut, new ItemStack(ModItems.RUBY_MEDIUM.get()), reg) == 1, "cut medium ruby (2 g)");
        check(h, ContractManager.unitValue(cut, new ItemStack(ModItems.RUBY_LARGE.get()), reg) == 1, "cut large ruby (4 g)");
        check(h, ContractManager.unitValue(cut, new ItemStack(ModItems.RUBY_SMALL.get()), reg) == 0, "1 g is under the minimum weight");
        check(h, ContractManager.unitValue(cut, new ItemStack(ModItems.UNCUT_RUBY_LARGE.get()), reg) == 0, "uncut does not count for cut");
        check(h, ContractManager.unitValue(uncut, new ItemStack(ModItems.UNCUT_RUBY_SMALL.get()), reg) == 1, "uncut small ruby");
        check(h, ContractManager.unitValue(uncut, new ItemStack(ModItems.RUBY_LARGE.get()), reg) == 0, "cut does not count for uncut");
        check(h, ContractManager.unitValue(cut, new ItemStack(ModItems.SAPPHIRE_MEDIUM.get()), reg) == 0, "another gem");
        check(h, ContractManager.unitValue(cut, ingot(ModItems.GOLDCHRO_INGOT.get(), 20), reg) == 0, "an ingot is not a gem");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cropsAndFoodMatchTheItem(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        var reg = p.registryAccess();
        Contract stew = food(ModItems.RIVERBEAN_STEW.get(), 8);
        check(h, ContractManager.unitValue(stew, new ItemStack(ModItems.RIVERBEAN_STEW.get()), reg) == 1, "stew");
        check(h, ContractManager.unitValue(stew, new ItemStack(ModItems.RIVERBEAN.get()), reg) == 0, "beans are not stew");
        check(h, ContractManager.unitValue(stew, new ItemStack(Items.BREAD), reg) == 0, "bread is not stew");
        Contract wheat = food(Items.WHEAT, 8);
        check(h, ContractManager.unitValue(wheat, new ItemStack(Items.WHEAT), reg) == 1, "vanilla wheat");
        check(h, ContractManager.unitValue(wheat, new ItemStack(Items.CARROT), reg) == 0, "carrot is not wheat");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void smithedPiecesMatchPieceMetalAndGem(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        var reg = p.registryAccess();
        SmithData.Ingot sil60 = new SmithData.Ingot(SILVANE, 60);
        SmithData.Gem ruby = new SmithData.Gem(RUBY, 1);
        ItemStack silvaneRingRuby = smithed(p, ModItems.SMITH_RING.get(), List.of(sil60), Optional.of(ruby));
        ItemStack silvaneRing = smithed(p, ModItems.SMITH_RING.get(), List.of(sil60), Optional.empty());
        ItemStack folkironRingRuby = smithed(p, ModItems.SMITH_RING.get(), List.of(new SmithData.Ingot(FOLKIRON, 60)), Optional.of(ruby));
        ItemStack lightRing = smithed(p, ModItems.SMITH_RING.get(), List.of(new SmithData.Ingot(SILVANE, 30)), Optional.of(ruby));
        ItemStack silvaneNecklace = smithed(p, ModItems.SMITH_NECKLACE.get(), List.of(sil60), Optional.of(ruby));
        Contract c = piece(ModItems.SMITH_RING.get(), SILVANE, RUBY.toString(), 60);
        check(h, ContractManager.unitValue(c, silvaneRingRuby, reg) == 1, "a Silvane ring with a Ruby");
        check(h, ContractManager.unitValue(c, silvaneRing, reg) == 0, "no gem");
        check(h, ContractManager.unitValue(c, folkironRingRuby, reg) == 0, "wrong metal");
        check(h, ContractManager.unitValue(c, lightRing, reg) == 0, "under the minimum weight");
        check(h, ContractManager.unitValue(c, silvaneNecklace, reg) == 0, "wrong piece");
        Contract anyGem = piece(ModItems.SMITH_RING.get(), null, "any", 0);
        check(h, ContractManager.unitValue(anyGem, folkironRingRuby, reg) == 1, "any metal, any gem");
        check(h, ContractManager.unitValue(anyGem, silvaneRing, reg) == 0, "any gem still needs a gem");
        Contract noGem = piece(ModItems.SMITH_RING.get(), FOLKIRON, null, 0);
        check(h, ContractManager.unitValue(noGem, folkironRingRuby, reg) == 1, "no gem asked, a gem does not hurt");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ rewards

    @GameTest(template = "empty")
    public static void finishingPaysCoinsRenownAndCountsContracts(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        LedgerData ledger = LedgerData.get(p);
        ContractData d = ContractManager.data(p);
        Contract c = new Contract("t", ContractKind.FOOD, BuiltInRegistries.ITEM.getKey(Items.WHEAT), "", null, null, 4, 0, 5000, 20, 0);
        d.set(0, c);
        int renown0 = ledger.renown();
        ItemStack hand = new ItemStack(Items.WHEAT, 4);
        ContractManager.deliver(p, 0, hand);
        check(h, coinValue(p) == 5000, "coins add up to the reward: " + coinValue(p));
        check(h, count(p, ModItems.RUBY_CROWN.get()) == 1 && count(p, ModItems.PLATINUM_COIN.get()) == 1 && count(p, ModItems.GOLD_COIN.get()) == 6
                        && count(p, ModItems.SILVER_COIN.get()) == 1 && count(p, ModItems.COPPER_COIN.get()) == 0,
                "biggest coins first: 1 crown, 1 platinum, 6 gold, 1 silver");
        check(h, ledger.contractRenown() == 20, "Renown counted as contract Renown: " + ledger.contractRenown());
        check(h, ledger.renown() >= renown0 + 20, "Renown added");
        check(h, d.completed() == 1, "one contract finished");
        check(h, ledger.isMilestoneCompleted(HoleyDigsite.id("first_contract")), "first contract milestone");
        // the cap holds
        ledger.setContractRenown(LedgerDefs.curve().contract().cap() - 5);
        int renown1 = ledger.renown();
        check(h, ledger.addContractRenown(20, LedgerDefs.curve().contract()) == 5 && ledger.renown() == renown1 + 5, "only what is left under the cap");
        check(h, ledger.addContractRenown(20, LedgerDefs.curve().contract()) == 0, "nothing past the cap");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cratesComeOnlyFromBiggerContractsAndNeverGold(GameTestHelper h) {
        int one = 0, two = 0, other = 0, smallCrate = 0;
        RandomSource rng = RandomSource.create(24L);
        int n = 20000;
        for (int i = 0; i < n; i++) {
            ItemStack small = ContractManager.crateFor(1000, rng);
            if (!small.isEmpty()) smallCrate++;
            ItemStack mid = ContractManager.crateFor(4000, rng);
            if (mid.is(ModItems.PROSPECTOR_CRATE.get()) && mid.getCount() == 1) one++;
            ItemStack big = ContractManager.crateFor(9000, rng);
            if (big.is(ModItems.PROSPECTOR_CRATE.get()) && big.getCount() == 2) two++;
            if ((!mid.isEmpty() && !mid.is(ModItems.PROSPECTOR_CRATE.get())) || (!big.isEmpty() && !big.is(ModItems.PROSPECTOR_CRATE.get()))
                    || com.holeydigsite.crate.CrateBlock.isLocked(mid) || com.holeydigsite.crate.CrateBlock.isLocked(big)) other++;
        }
        check(h, smallCrate == 0, "small contracts never give a crate");
        check(h, other == 0, "only unlocked Prospector Crates");
        check(h, Math.abs(one / (double) n - 0.10) < 0.02, "mid contracts: one crate about 10%: " + one / (double) n);
        check(h, Math.abs(two / (double) n - 0.03) < 0.015, "big contracts: two crates about 3%: " + two / (double) n);
        // a rolled crate arrives in the inventory
        ServerPlayer p = fresh(h);
        long seed = 0;
        while (ContractManager.crateFor(9000, RandomSource.create(seed)).isEmpty()) seed++;
        ItemStack expected = ContractManager.crateFor(9000, RandomSource.create(seed));
        Contract c = new Contract("t", ContractKind.FOOD, BuiltInRegistries.ITEM.getKey(Items.WHEAT), "", null, null, 1, 0, 9000, 20, 0);
        ContractManager.data(p).set(0, c);
        c.markDone();
        ContractManager.complete(p, c, RandomSource.create(seed));
        check(h, count(p, expected.getItem()) == expected.getCount(), "the crate(s) are in the inventory");
        check(h, coinValue(p) == 9000, "and the coins");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ board and players

    private static BlockPos board(GameTestHelper h) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
        h.setBlock(new BlockPos(2, 2, 3), Blocks.STONE);
        h.setBlock(new BlockPos(2, 2, 2), ModBlocks.CONTRACT_BOARD.get().defaultBlockState().setValue(ContractBoardBlock.FACING, Direction.NORTH));
        return new BlockPos(2, 2, 2);
    }

    private static void click(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held) {
        BlockPos abs = h.absolutePos(rel);
        Vec3 eye = Vec3.atCenterOf(abs).add(0, 0, -2);
        p.moveTo(eye.x, eye.y - p.getEyeHeight(), eye.z, 0, 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0, -0.5), Direction.NORTH, abs, false);
        p.gameMode.useItemOn(p, h.getLevel(), held, InteractionHand.MAIN_HAND, hit);
    }

    @GameTest(template = "empty")
    public static void twoPlayersAtOneBoardHaveSeparateContracts(GameTestHelper h) {
        BlockPos b = board(h);
        ServerPlayer a = fresh(h), c = fresh(h);
        ContractManager.data(a).set(0, food(Items.WHEAT, 10));
        ContractManager.data(c).set(0, food(Items.WHEAT, 10));
        ContractManager.data(a).set(1, null);
        long day = ContractManager.today(h.getLevel().getServer());
        ContractManager.data(a).setLastDay(day);
        ContractManager.data(c).setLastDay(day);
        click(h, a, b, new ItemStack(Items.WHEAT, 6));
        check(h, ContractManager.data(a).get(0).delivered() == 6, "player A delivered 6 through the real click: " + ContractManager.data(a).get(0).delivered());
        check(h, ContractManager.data(c).get(0).delivered() == 0, "player B's card is untouched");
        click(h, c, b, new ItemStack(Items.WHEAT, 3));
        check(h, ContractManager.data(c).get(0).delivered() == 3 && ContractManager.data(a).get(0).delivered() == 6, "each their own");
        // something no card wants is refused and stays in the hand
        ItemStack stone = new ItemStack(Items.STICK, 4);
        click(h, a, b, stone);
        check(h, stone.getCount() == 4, "unwanted items stay in the hand");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cardUnderTheLookRayIsChosenTheSameWayEverywhere(GameTestHelper h) {
        BlockPos pos = new BlockPos(10, 70, 10);
        Direction f = Direction.NORTH;
        Vec3 eye = new Vec3(10.5, 71.0, 7.0);
        Vec3 plane = ContractBoardBlock.planePoint(pos, f);
        double dz = plane.z - eye.z;
        int top = ContractBoardBlock.cardAt(pos, f, eye, new Vec3(0, (70.5 + 0.5 - eye.y) / dz, 1).normalize());
        int mid = ContractBoardBlock.cardAt(pos, f, eye, new Vec3(0, (70.5 - eye.y) / dz, 1).normalize());
        int low = ContractBoardBlock.cardAt(pos, f, eye, new Vec3(0, (70.5 - 0.5 - eye.y) / dz, 1).normalize());
        check(h, top == 0 && mid == 1 && low == 2, "top, middle, bottom: " + top + mid + low);
        check(h, ContractBoardBlock.cardAt(pos, f, eye, new Vec3(0, 0, -1)) == 1, "looking away falls back to the middle");
        check(h, ContractBoardBlock.cardCentreY(0) > ContractBoardBlock.cardCentreY(1) && ContractBoardBlock.cardCentreY(1) > ContractBoardBlock.cardCentreY(2),
                "cards are stacked top to bottom");
        // text hanging over the block above the board still counts as the top card, from the front and from the side
        Vec3 aboveTop = new Vec3(plane.x, 70 + ContractBoardBlock.cardCentreY(0) + 0.15, plane.z);
        check(h, ContractBoardBlock.cardUnderCrosshair(pos, f, eye, aboveTop.subtract(eye)) == 0, "text over the block above");
        Vec3 side = new Vec3(8.0, 70.2, 8.0);
        Vec3 belowLow = new Vec3(plane.x, 70 + ContractBoardBlock.cardCentreY(2) - 0.15, plane.z);
        check(h, ContractBoardBlock.cardUnderCrosshair(pos, f, side, belowLow.subtract(side)) == 2, "text over the block below, from the side");
        check(h, ContractBoardBlock.cardUnderCrosshair(pos, f, eye, new Vec3(plane.x, 72.5, plane.z).subtract(eye)) == -1,
                "well above the cards is no card");
        check(h, ContractBoardBlock.cardUnderCrosshair(pos, f, eye, new Vec3(plane.x + 2.0, 70.5, plane.z).subtract(eye)) == -1,
                "beside the cards is no card");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void contractTextFitsTheCards(GameTestHelper h) {
        List<Contract> all = List.of(metal(FOLKIRON, 300, 100, 123456), gem(SAPPHIRE, "cut", 3, 2), food(ModItems.RIVERBEAN_STEW.get(), 64),
                piece(ModItems.SMITH_RING.get(), SILVANE, RUBY.toString(), 60), piece(ModItems.SMITH_AXE.get(), null, null, 0));
        for (Contract c : all) {
            List<Component> lines = ContractText.card(c, 5, 3);
            check(h, lines.size() == 3, "three lines per card");
            TranslatableContents tc = (TranslatableContents) ContractText.want(c).getContents();
            check(h, tc.getKey().startsWith("contract.holeydigsite.want.") && tc.getArgs()[0].equals(c.amount), "what is wanted: " + tc.getKey());
        }
        TranslatableContents metalWant = (TranslatableContents) ContractText.want(all.get(0)).getContents();
        check(h, metalWant.getKey().equals("contract.holeydigsite.want.metal_min") && metalWant.getArgs()[2].equals(100), "min ingot shown");
        // every key the cards and the details use is in the lang file
        JsonObject lang;
        try (var in = Phase24Tests.class.getResourceAsStream("/assets/holeydigsite/lang/en_us.json")) {
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        Set<String> used = new HashSet<>();
        Contract nearlyDone = food(Items.WHEAT, 5);
        nearlyDone.add(2);
        List<Contract> forKeys = new ArrayList<>(all);
        forKeys.add(nearlyDone);
        forKeys.add(piece(ModItems.SMITH_RING.get(), null, "any", 0));
        forKeys.add(gem(RUBY, "uncut", 4, 0));
        for (Contract c : forKeys) {
            for (Component line : ContractText.card(c, 5, 3)) keys(line, used);
            for (Component line : ContractText.details(c, 5, 3)) keys(line, used);
            keys(ContractText.daysLeft(c, 5, 3), used);
            keys(ContractText.daysLeft(c, 6, 3), used);
        }
        keys(Component.translatable("contract.holeydigsite.empty"), used);
        for (String k : used) {
            if (k.startsWith("contract.") || k.startsWith("message.holeydigsite.contract") || k.startsWith("chat.holeydigsite.contract")) {
                check(h, lang.has(k), "lang key " + k);
            }
        }
        for (String k : List.of("message.holeydigsite.contract.done", "message.holeydigsite.contract.progress", "message.holeydigsite.contract.progress_g",
                "message.holeydigsite.contract.no_match", "message.holeydigsite.contract.hint", "chat.holeydigsite.contract.reward",
                "chat.holeydigsite.contract.crate", "chat.holeydigsite.board.blocked", "block.holeydigsite.contract_board", "ledger.holeydigsite.title.contractor",
                "ledger.holeydigsite.chapter.contracts")) {
            check(h, lang.has(k), "lang key " + k);
        }
        for (String id : List.of("first_contract", "ten_contracts", "fifty_contracts")) {
            check(h, lang.has("ledger.holeydigsite.milestone." + id) && lang.has("ledger.holeydigsite.milestone." + id + ".hint"), "milestone text " + id);
        }
        check(h, ContractText.card(null, 5, 3).size() == 1, "empty slot");
        Contract done = food(Items.WHEAT, 2);
        done.markDone();
        check(h, ContractText.card(done, 5, 3).size() == 3, "done card");
        h.succeed();
    }

    private static void keys(Component c, Set<String> out) {
        if (c.getContents() instanceof TranslatableContents tc) {
            out.add(tc.getKey());
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component inner) keys(inner, out);
            }
        }
        for (Component sibling : c.getSiblings()) keys(sibling, out);
    }

    // ------------------------------------------------------------------------------------------------ Ledger and saving

    @GameTest(template = "empty")
    public static void milestonesTitleAndOldDataWork(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        LedgerData ledger = LedgerData.get(p);
        ContractData d = ContractManager.data(p);
        check(h, LedgerDefs.milestonesForChapter("contracts").size() == 3, "three contract milestones");
        check(h, LedgerDefs.CHAPTER_ORDER.contains("contracts"), "chapter listed");
        check(h, LedgerDefs.isContractTitle("contractor") && !LedgerDefs.titles().contains("contractor"), "contract title");
        d.setCompleted(9);
        check(h, !ledger.isMilestoneCompleted(HoleyDigsite.id("ten_contracts")), "not yet");
        Contract c = food(Items.WHEAT, 1);
        d.set(0, c);
        ContractManager.deliver(p, 0, new ItemStack(Items.WHEAT));
        check(h, ledger.isMilestoneCompleted(HoleyDigsite.id("ten_contracts")), "10 contracts");
        check(h, !ledger.hasTitle("contractor"), "no title yet");
        d.setCompleted(49);
        Contract c2 = food(Items.WHEAT, 1);
        d.set(1, c2);
        ContractManager.deliver(p, 1, new ItemStack(Items.WHEAT));
        check(h, ledger.isMilestoneCompleted(HoleyDigsite.id("fifty_contracts")) && ledger.hasTitle("contractor"), "50 contracts and the title");
        // save, load, and the copy made on death
        ContractManager.refresh(p, 77);
        CompoundTag tag = ledger.serializeNBT(p.registryAccess());
        LedgerData loaded = new LedgerData();
        loaded.deserializeNBT(p.registryAccess(), tag);
        check(h, loaded.contracts().completed() == 50 && loaded.contracts().lastDay() == 77, "saved with the Ledger");
        for (int i = 0; i < 3; i++) {
            Contract a = d.get(i), b = loaded.contracts().get(i);
            check(h, (a == null) == (b == null) && (a == null || (a.key().equals(b.key()) && a.amount == b.amount && a.reward == b.reward
                    && a.delivered() == b.delivered() && a.isDone() == b.isDone())), "slot " + i + " kept");
        }
        LedgerData copy = new LedgerData();
        copy.copyFrom(ledger);
        check(h, copy.contracts().completed() == 50 && copy.contractRenown() == ledger.contractRenown(), "copied on death");
        // an old save without contracts reads as empty
        LedgerData old = new LedgerData();
        tag.remove("Contracts");
        old.deserializeNBT(p.registryAccess(), tag);
        check(h, old.contracts().get(0) == null && old.contracts().lastDay() == ContractData.NEVER, "old data has no contracts");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ the Trading Post

    private static BlockPos far(int n) {
        return new BlockPos(20000 + n * 64, 100, 20200);
    }

    private static void prepare(ServerLevel l, BlockPos a) {
        BoundingBox big = new BoundingBox(a.getX() - 4, a.getY() - 2, a.getZ() - 4, a.getX() + 40, a.getY() + 34, a.getZ() + 40);
        Sites.clear(l, big);
        for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(48))) e.discard();
    }

    private static void cleanup(ServerLevel l, BlockPos a) {
        SiteData data = SiteData.get(l);
        for (SiteData.Site s : new ArrayList<>(data.all())) {
            if (s.zone().isInside(a) || s.anchor().equals(a)) data.remove(s.key());
        }
        PlotData pd = PlotData.get(l);
        for (PlotData.Plot p : new ArrayList<>(pd.all())) {
            if (p.board().getX() >= a.getX() - 2 && p.board().getX() < a.getX() + 40 && p.board().getZ() >= a.getZ() - 2 && p.board().getZ() < a.getZ() + 40) {
                pd.remove(p.id(), p.board());
            }
        }
        SiteDefs.clearOverrides();
    }

    @GameTest(template = "empty", batch = "p24_trading_post", timeoutTicks = 100)
    public static void boardIsInNewStoresAndPlacedIntoAirInOldOnes(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = far(60);
        prepare(l, a);
        try {
            SiteDef d = SiteDefs.get(HoleyDigsite.id("home"));
            Sites.Result r = Sites.build(l, d, a, null, false);
            SiteData.Site site = r.site();
            List<Boards.Spot> spots = Boards.ensure(l, site, d);
            check(h, spots.size() == 3 && spots.stream().allMatch(s -> s.state() == Boards.State.THERE),
                    "a new Trading Post has its Contract Board, Leaderboard and Crate Switch from the template: " + spots);
            List<Boards.Spot> switches = spots.stream().filter(s -> l.getBlockState(s.pos()).getBlock() instanceof com.holeydigsite.crate.CrateSwitchBlock).toList();
            check(h, switches.size() == 1, "one Crate Switch: " + spots);
            BlockPos switchAt = switches.get(0).pos();
            List<Boards.Spot> contract = spots.stream().filter(s -> l.getBlockState(s.pos()).getBlock() instanceof ContractBoardBlock).toList();
            check(h, contract.size() == 1, "one Contract Board: " + spots);
            BlockPos at = contract.get(0).pos();
            check(h, l.getBlockState(at).getBlock() instanceof ContractBoardBlock && l.getBlockState(at).getValue(ContractBoardBlock.FACING) == Direction.EAST,
                    "facing into the store");

            // an old world: the spot is air
            l.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            BlockPos litter = a.offset(15, 11, 10);
            l.setBlock(litter, Blocks.GLASS.defaultBlockState(), 3);
            Sites.checkAll(l);
            check(h, l.getBlockState(at).getBlock() instanceof ContractBoardBlock, "placed into air on load");
            l.setBlock(switchAt, Blocks.AIR.defaultBlockState(), 3);
            Sites.checkAll(l);
            check(h, l.getBlockState(switchAt).getBlock() instanceof com.holeydigsite.crate.CrateSwitchBlock, "the Crate Switch is placed into air in an old store too");
            check(h, l.getBlockState(litter).is(Blocks.GLASS), "nothing else touched (no rebuild)");

            // a player block at the spot is never replaced
            l.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
            List<Boards.Spot> blocked = Boards.ensure(l, SiteData.get(l).get(d.id(), a), d);
            check(h, blocked.stream().filter(s -> s.state() == Boards.State.BLOCKED).map(Boards.Spot::pos).toList().equals(List.of(at)),
                    "blocked spot is reported: " + blocked);
            check(h, l.getBlockState(at).is(Blocks.CHEST), "the player's chest stays");
            Sites.checkAll(l);
            check(h, l.getBlockState(at).is(Blocks.CHEST), "still there after a load check");
            h.succeed();
        } finally {
            cleanup(l, a);
        }
    }

    // ------------------------------------------------------------------------------------------------ commands

    @GameTest(template = "empty")
    public static void commandsListRerollCompleteAndDay(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        setLevel(p, 30);
        String name = "@s";
        var cmds = h.getLevel().getServer().getCommands();
        CommandSourceStack src = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        cmds.performPrefixedCommand(src, "holeydigsite contract list " + name);
        ContractData d = ContractManager.data(p);
        check(h, d.get(0) != null && d.get(1) != null && d.get(2) != null, "list brings the contracts up to date");
        Contract before = d.get(0);
        cmds.performPrefixedCommand(src, "holeydigsite contract reroll " + name);
        check(h, d.get(0) != null && d.get(0) != before && d.rerolls() == 1, "reroll replaces them");
        cmds.performPrefixedCommand(src, "holeydigsite contract complete " + name + " 2");
        check(h, d.get(1).isDone() && d.completed() == 1 && coinValue(p) > 0, "complete pays slot 2");
        Contract open = d.get(0);
        cmds.performPrefixedCommand(src, "holeydigsite contract day " + name);
        check(h, d.get(1) != null && !d.get(1).isDone(), "a new day refills the finished slot");
        check(h, d.get(0) == open || d.get(0).issued >= open.issued, "the others are kept for now");
        cmds.performPrefixedCommand(src, "holeydigsite contract complete " + name + " 2");
        cmds.performPrefixedCommand(src, "holeydigsite contract complete " + name + " 2");
        check(h, d.completed() == 2, "completing an already finished slot does nothing: " + d.completed());
        h.succeed();
    }
}
