package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.crate.CrateBlock;
import com.holeydigsite.crate.CrateDefs;
import com.holeydigsite.crate.CrateRolls;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.panning.Panning;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.skin.SkinBenchBlockEntity;
import com.holeydigsite.skin.SkinBenchNetwork;
import com.holeydigsite.skin.SkinModelProperties;
import com.holeydigsite.skin.SkinWear;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mining Crates and skin wear: odds over many rolls, rarity fallback, Skin Scrap by rarity and wear (duplicate titles, scrapped
 * kits), every skin roll a kit at its wear, wear odds, old data reads as Fresh, rewards arrive (full inventory drops), opening
 * only on the server, panning finds from hand pans only, Trading Post and Ledger sources, commands.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase23Tests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = 0; z < 5; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static ServerPlayer fresh(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        LedgerData.get(p).resetAll();
        return p;
    }

    private static int count(Player p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** Drill kits of a skin and wear in the inventory (rolls made with {@link #roll} have no family, so they are drill kits). */
    private static int kits(Player p, String skin, String wear) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (com.holeydigsite.skin.SkinKitItem.matches(s, skin, com.holeydigsite.skin.SkinDefs.DRILL, wear)) n += s.getCount();
        }
        return n;
    }

    private static CrateRolls.Roll roll(String rarity, CrateDefs.Kind kind, String id, String wear) {
        return new CrateRolls.Roll("iron_crate", rarity, rarity, new CrateDefs.Entry(kind, id, 1, 1), wear);
    }

    @GameTest(template = "empty")
    public static void oddsMatchDataOverManyRolls(GameTestHelper h) {
        RandomSource random = RandomSource.create(2323L);
        int n = 40000;
        for (String tierId : CrateDefs.BUILTIN_TIERS) {
            CrateDefs.CrateTier tier = CrateDefs.tier(tierId);
            check(h, tier != null, "tier " + tierId + " loaded");
            Map<String, Integer> counts = new HashMap<>();
            for (int i = 0; i < n; i++) {
                CrateRolls.Roll r = CrateRolls.roll(tier, random);
                check(h, r != null, "roll gives a result");
                counts.merge(r.rolledRarity(), 1, Integer::sum);
                check(h, tier.pool(r.rarity()).contains(r.entry()), "entry comes from the pool it says");
            }
            for (String rarity : CrateDefs.RARITIES) {
                double got = 100.0 * counts.getOrDefault(rarity, 0) / n;
                double want = tier.percent(rarity);
                check(h, Math.abs(got - want) < 1.0, tierId + " " + rarity + " " + got + "% vs data " + want + "%");
            }
        }
        // the designed odds
        check(h, CrateDefs.tier("wooden_crate").percent("common") == 70 && CrateDefs.tier("wooden_crate").percent("legendary") == 0, "wooden odds");
        check(h, CrateDefs.tier("gold_crate").percent("legendary") == 10, "gold odds");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyRarityFallsBackToNextLower(GameTestHelper h) {
        CrateDefs.Entry epic = new CrateDefs.Entry(CrateDefs.Kind.TITLE, "high_roller", 1, 1);
        CrateDefs.Entry common = new CrateDefs.Entry(CrateDefs.Kind.COINS, "", 64, 1);
        CrateDefs.CrateTier t = new CrateDefs.CrateTier("test_crate",
                Map.of("common", 0.0, "rare", 0.0, "epic", 0.0, "legendary", 100.0), 0,
                Map.of("common", List.of(common), "rare", List.of(), "epic", List.of(epic), "legendary", List.of()));
        check(h, "epic".equals(CrateRolls.poolRarity(t, "legendary")), "legendary falls back to epic");
        check(h, "common".equals(CrateRolls.poolRarity(t, "rare")), "rare falls back to common");
        RandomSource random = RandomSource.create(5L);
        for (int i = 0; i < 200; i++) {
            CrateRolls.Roll r = CrateRolls.roll(t, random);
            check(h, "legendary".equals(r.rolledRarity()) && "epic".equals(r.rarity()) && r.entry() == epic, "always the epic entry");
        }
        // built-in data: no legendary skins yet, so a legendary roll never gives a skin
        for (CrateDefs.Entry e : CrateDefs.tier("gold_crate").pool("legendary")) {
            check(h, e.kind() != CrateDefs.Kind.SKIN, "no legendary skins yet");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicatesGiveSkinScrapByRarityAndWear(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        LedgerData data = LedgerData.get(p);
        check(h, CrateRolls.scrapFor("common", null) == 1 && CrateRolls.scrapFor("rare", null) == 3
                && CrateRolls.scrapFor("epic", null) == 8 && CrateRolls.scrapFor("legendary", null) == 20, "base scrap by rarity");
        check(h, CrateRolls.scrapFor("epic", "fresh_from_the_forge") == 16, "epic fresh x2");
        check(h, CrateRolls.scrapFor("rare", "workshop_clean") == 5, "rare clean 4.5 rounds to 5");
        check(h, CrateRolls.scrapFor("epic", "pit_scarred") == 6, "epic scarred x0.75");
        check(h, CrateRolls.scrapFor("common", "cave_battered") == 1, "never below 1");

        // a skin is never scrap by itself any more: it is a kit, and scrapping it on purpose gives the old duplicate amount
        CrateRolls.grant(p, roll("epic", CrateDefs.Kind.SKIN, "obsidian", "pit_scarred"));
        CrateRolls.Outcome o = CrateRolls.grant(p, roll("epic", CrateDefs.Kind.SKIN, "obsidian", "pit_scarred"));
        check(h, o.result() == CrateRolls.Result.KIT && o.scrap() == 0, "second obsidian is still a kit, got " + o.result() + " " + o.scrap());
        check(h, count(p, ModItems.SKIN_SCRAP.get()) == 0, "no scrap from a skin roll");
        ItemStack kit = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.SKIN_KIT.get())) kit = s;
        check(h, kit.getCount() == 2, "two obsidian kits");
        BlockPos bench = h.absolutePos(new BlockPos(1, 1, 1));
        h.setBlock(new BlockPos(1, 1, 1), ModBlocks.SKIN_BENCH.get().defaultBlockState());
        com.holeydigsite.skin.SkinBenchActions.scrap(p, kit, bench);
        com.holeydigsite.skin.SkinBenchActions.scrap(p, kit, bench);
        check(h, count(p, ModItems.SKIN_SCRAP.get()) == 6, "scrapping the worse kit gives 6 Skin Scrap");
        check(h, kit.getCount() == 1, "one kit left");

        CrateRolls.grant(p, roll("rare", CrateDefs.Kind.TITLE, "crate_cracker", null));
        CrateRolls.Outcome t2 = CrateRolls.grant(p, roll("rare", CrateDefs.Kind.TITLE, "crate_cracker", null));
        check(h, t2.result() == CrateRolls.Result.DUPLICATE && t2.scrap() == 3, "duplicate rare title gives 3");
        CrateRolls.grant(p, roll("legendary", CrateDefs.Kind.TITLE, "jackpot", null));
        CrateRolls.Outcome t3 = CrateRolls.grant(p, roll("legendary", CrateDefs.Kind.TITLE, "jackpot", null));
        check(h, t3.scrap() == 20, "duplicate legendary title gives 20");
        check(h, count(p, ModItems.SKIN_SCRAP.get()) == 29, "29 Skin Scrap in total, got " + count(p, ModItems.SKIN_SCRAP.get()));

        // Skin Scrap only sells, at the Sell Pad price from the data
        long each = Selling.unitValue(new ItemStack(ModItems.SKIN_SCRAP.get()), h.getLevel().registryAccess());
        check(h, each == 384, "Skin Scrap sells for 384 copper, got " + each);
        check(h, ShopCatalogue.find(h.getLevel().registryAccess(), ModItems.SKIN_SCRAP.get()).isEmpty(), "Skin Scrap is not in the shop");
        h.succeed();
    }

    /** Every crate skin is a kit at the wear it rolled: better, same or worse, each one is kept, none turns into scrap. */
    @GameTest(template = "empty")
    public static void everySkinRollIsAKitAtItsWear(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        LedgerData data = LedgerData.get(p);
        CrateRolls.Outcome first = CrateRolls.grant(p, roll("rare", CrateDefs.Kind.SKIN, "brass", "field_worn"));
        check(h, first.result() == CrateRolls.Result.KIT && kits(p, "brass", "field_worn") == 1, "brass kit at Field Worn");
        CrateRolls.Outcome up = CrateRolls.grant(p, roll("rare", CrateDefs.Kind.SKIN, "brass", "workshop_clean"));
        check(h, up.result() == CrateRolls.Result.KIT && up.scrap() == 0, "better wear is another kit");
        check(h, kits(p, "brass", "workshop_clean") == 1 && kits(p, "brass", "field_worn") == 1, "both wears kept");
        CrateRolls.Outcome same = CrateRolls.grant(p, roll("rare", CrateDefs.Kind.SKIN, "brass", "workshop_clean"));
        check(h, same.result() == CrateRolls.Result.KIT && same.scrap() == 0 && kits(p, "brass", "workshop_clean") == 2, "same wear stacks");
        CrateRolls.grant(p, roll("rare", CrateDefs.Kind.SKIN, "brass", "fresh_from_the_forge"));
        check(h, kits(p, "brass", "fresh_from_the_forge") == 1, "Fresh kit");
        check(h, count(p, ModItems.SKIN_SCRAP.get()) == 0, "no scrap from any skin roll");
        check(h, data.unlockedSkins().isEmpty(), "nothing is unlocked on the player");
        // the old unlock wear (read once for the move to kits) survives death copy and NBT
        data.setSkinWear("frosted", "pit_scarred");
        data.unlockedSkins().add("frosted");
        LedgerData copy = new LedgerData();
        copy.deserializeNBT(h.getLevel().registryAccess(), data.serializeNBT(h.getLevel().registryAccess()));
        check(h, "pit_scarred".equals(copy.skinWear("frosted")), "wear survives NBT");
        LedgerData clone = new LedgerData();
        clone.copyFrom(data);
        check(h, "pit_scarred".equals(clone.skinWear("frosted")), "wear survives death copy");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wearOddsOverManyRolls(GameTestHelper h) {
        RandomSource random = RandomSource.create(77L);
        Map<String, Integer> counts = new HashMap<>();
        int n = 40000;
        for (int i = 0; i < n; i++) counts.merge(SkinWear.roll(random), 1, Integer::sum);
        double[] want = {10, 20, 35, 25, 10};
        String[] ids = {"fresh_from_the_forge", "workshop_clean", "field_worn", "pit_scarred", "cave_battered"};
        for (int i = 0; i < ids.length; i++) {
            double got = 100.0 * counts.getOrDefault(ids[i], 0) / n;
            check(h, Math.abs(got - want[i]) < 1.0, ids[i] + " " + got + "% vs " + want[i] + "%");
            check(h, SkinWear.index(ids[i]) == i, "order " + ids[i]);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldUnlocksAndMachinesReadAsFresh(GameTestHelper h) {
        clear(h);
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        // player data from before wear: only UnlockedSkins
        CompoundTag old = new CompoundTag();
        ListTag skins = new ListTag();
        skins.add(StringTag.valueOf("rustic"));
        old.put("UnlockedSkins", skins);
        LedgerData d = new LedgerData();
        d.deserializeNBT(reg, old);
        check(h, d.unlockedSkins().contains("rustic") && SkinWear.isFresh(d.skinWear("rustic")), "old unlock reads Fresh");

        // an old skinned drill: only "Skin" saved
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockState lower = ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.NORTH)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.LOWER);
        h.setBlock(pos, lower);
        h.setBlock(pos.above(), lower.setValue(MiningDrillBlock.HALF, DoubleBlockHalf.UPPER));
        MiningDrillBlockEntity drill = (MiningDrillBlockEntity) h.getBlockEntity(pos);
        CompoundTag tag = drill.saveWithoutMetadata(reg);
        tag.putString("Skin", "brass");
        tag.remove("SkinWear");
        drill.loadWithComponents(tag, reg);
        check(h, "brass".equals(drill.getSkin()) && drill.getWear().isEmpty(), "old drill has no wear");
        check(h, "brass".equals(drill.getModelData().get(SkinModelProperties.SKIN)), "old drill uses the Fresh textures");
        ItemStack oldItem = new ItemStack(ModItems.MINING_DRILL.get());
        oldItem.set(ModDataComponents.SKIN.get(), "brass");
        check(h, SkinWear.isFresh(SkinWear.of(oldItem)), "old item reads Fresh");

        // a worn drill keeps its wear through place, model data and break
        ServerPlayer p = fresh(h);
        ItemStack worn = new ItemStack(ModItems.MINING_DRILL.get());
        worn.set(ModDataComponents.SKIN.get(), "rustic");
        SkinWear.set(worn, "cave_battered");
        h.setBlock(pos, Blocks.AIR);
        h.setBlock(pos.above(), Blocks.AIR);
        h.setBlock(pos, lower);
        h.setBlock(pos.above(), lower.setValue(MiningDrillBlock.HALF, DoubleBlockHalf.UPPER));
        ModBlocks.MINING_DRILL.get().setPlacedBy(h.getLevel(), h.absolutePos(pos), lower, p, worn);
        drill = (MiningDrillBlockEntity) h.getBlockEntity(pos);
        check(h, "cave_battered".equals(drill.getWear()), "placed drill has the wear");
        check(h, "rustic/cave_battered".equals(drill.getModelData().get(SkinModelProperties.SKIN)), "model key picks the worn set");
        p.gameMode.destroyBlock(h.absolutePos(pos));
        boolean found = false;
        for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
            ItemStack s = e.getItem();
            found |= s.is(ModItems.MINING_DRILL.get()) && "cave_battered".equals(SkinWear.of(s));
        }
        check(h, found, "dropped drill keeps the wear");

        // sluice save/load round trip
        BlockPos sp = new BlockPos(3, 1, 1);
        h.setBlock(sp, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.NORTH));
        SluiceBoxBlockEntity sluice = (SluiceBoxBlockEntity) h.getBlockEntity(sp);
        sluice.setSkin("frosted", "workshop_clean");
        CompoundTag st = sluice.saveWithoutMetadata(reg);
        sluice.setSkin("", "");
        sluice.loadWithComponents(st, reg);
        check(h, "workshop_clean".equals(sluice.getWear()) && "frosted/workshop_clean".equals(sluice.getSkinKey()), "sluice wear saved");

        // worn texture files exist for every skin and wear
        java.io.File base = new java.io.File("src/main/resources/assets/holeydigsite/textures/block/skin");
        if (!base.exists()) base = new java.io.File("../src/main/resources/assets/holeydigsite/textures/block/skin");
        for (String skin : new String[]{"rustic", "rustic_weathered", "brass", "frosted", "obsidian", "outback", "outback_dusty", "outback_ochre", "opal_calm", "opal", "opal_boulder", "black_opal"}) {
            for (String wear : new String[]{"workshop_clean", "field_worn", "pit_scarred", "cave_battered"}) {
                java.io.File[] files = new java.io.File(base, skin + "/" + wear).listFiles((dir, name) -> name.endsWith(".png") && !name.endsWith("_s.png") && !name.endsWith("_n.png"));
                check(h, files != null && files.length == 54, skin + "/" + wear + " has 54 textures");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void skinBenchAppliesOwnedWear(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos abs = h.absolutePos(pos);
        ServerPlayer p = fresh(h);
        p.setPos(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5);
        h.setBlock(pos, ModBlocks.SKIN_BENCH.get().defaultBlockState());
        SkinBenchBlockEntity bench = (SkinBenchBlockEntity) h.getBlockEntity(pos);
        bench.setItem(new ItemStack(ModItems.MINING_DRILL.get()));
        p.getInventory().add(com.holeydigsite.skin.SkinKitItem.of("obsidian", com.holeydigsite.skin.SkinDefs.DRILL, "pit_scarred", 1));
        check(h, SkinBenchNetwork.handleApply(p, new SkinBenchNetwork.ApplySkinPayload(abs, "obsidian", "pit_scarred")), "apply works");
        check(h, "pit_scarred".equals(SkinWear.of(bench.getItem())), "bench puts the kit's wear on the machine");
        check(h, kits(p, "obsidian", "pit_scarred") == 0, "kit used up");
        check(h, SkinBenchNetwork.handleApply(p, SkinBenchNetwork.ApplySkinPayload.remove(abs)), "default works");
        check(h, !bench.getItem().has(ModDataComponents.SKIN_WEAR.get()), "default clears the wear");
        check(h, kits(p, "obsidian", "pit_scarred") == 1, "the kit comes back with its wear");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rewardsUnlockOrArrive(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        LedgerData data = LedgerData.get(p);
        CrateRolls.Outcome t = CrateRolls.grant(p, roll("common", CrateDefs.Kind.TITLE, "lucky_strike", null));
        check(h, t.result() == CrateRolls.Result.NEW && data.hasTitle("lucky_strike"), "title unlocked");
        check(h, LedgerManager.selectTitle(p, "lucky_strike"), "crate title can be chosen");
        check(h, LedgerDefs.isCrateTitle("lucky_strike") && !LedgerDefs.titles().contains("lucky_strike"), "crate-only title");
        CrateRolls.Outcome s = CrateRolls.grant(p, roll("epic", CrateDefs.Kind.SKIN, "obsidian", "field_worn"));
        check(h, s.result() == CrateRolls.Result.KIT && kits(p, "obsidian", "field_worn") == 1 && data.unlockedSkins().isEmpty(),
                "skin arrives as a kit at its wear");
        CrateRolls.grant(p, new CrateRolls.Roll("wooden_crate", "common", "common", new CrateDefs.Entry(CrateDefs.Kind.COINS, "", 192, 1), null));
        check(h, count(p, ModItems.GOLD_COIN.get()) == 3, "192 copper arrive as 3 gold coins");
        CrateRolls.grant(p, new CrateRolls.Roll("wooden_crate", "common", "common", new CrateDefs.Entry(CrateDefs.Kind.INGOT, "holeydigsite:folkiron_ingot", 100, 1), null));
        boolean ingot = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            ingot |= st.is(ModItems.FOLKIRON_INGOT.get()) && Integer.valueOf(100).equals(st.get(ModDataComponents.WEIGHT.get()));
        }
        check(h, ingot, "100 g Folkiron ingot arrives");
        // full inventory: decor drops at the feet
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        CrateRolls.grant(p, new CrateRolls.Roll("gold_crate", "legendary", "legendary",
                new CrateDefs.Entry(CrateDefs.Kind.ITEM, "holeydigsite:golden_drill_statue", 1, 1), null));
        boolean dropped = false;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4))) {
            dropped |= e.getItem().is(ModItems.GOLDEN_DRILL_STATUE.get());
        }
        check(h, dropped, "statue dropped at the feet with a full inventory");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void cratesOpenOnlyOnTheServerAndAreUsedUp(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockState closed = ModBlocks.WOODEN_CRATE.get().defaultBlockState();
        h.setBlock(pos, closed);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(pos)), Direction.UP, h.absolutePos(pos), false);
        // a player that is not a server player (as on a client) does not open it
        Player notServer = h.makeMockPlayer(GameType.SURVIVAL);
        closed.useWithoutItem(h.getLevel(), notServer, hit);
        check(h, !h.getBlockState(pos).getValue(CrateBlock.OPEN), "not opened without a server player");

        ServerPlayer p = fresh(h);
        int before = p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
        CompoundTag ledgerBefore = LedgerData.get(p).serializeNBT(h.getLevel().registryAccess());
        h.getBlockState(pos).useWithoutItem(h.getLevel(), p, hit);
        check(h, h.getBlockState(pos).getValue(CrateBlock.OPEN), "crate is open");
        int after = p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
        boolean gotSomething = after > before || !ledgerBefore.equals(LedgerData.get(p).serializeNBT(h.getLevel().registryAccess()));
        check(h, !gotSomething, "the reward is held until the spin ends");
        // opening again does nothing
        CompoundTag mid = LedgerData.get(p).serializeNBT(h.getLevel().registryAccess());
        int midCount = p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
        h.getBlockState(pos).useWithoutItem(h.getLevel(), p, hit);
        check(h, midCount == p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum()
                && mid.equals(LedgerData.get(p).serializeNBT(h.getLevel().registryAccess())), "an open crate gives nothing more");

        // an unopened crate drops itself
        BlockPos pos2 = new BlockPos(0, 1, 0);
        h.setBlock(pos2, ModBlocks.GOLD_CRATE.get().defaultBlockState());
        p.gameMode.destroyBlock(h.absolutePos(pos2));
        boolean dropped = false;
        for (ItemEntity e : h.getEntities(EntityType.ITEM)) dropped |= e.getItem().is(ModItems.GOLD_CRATE.get());
        check(h, dropped, "unopened crate drops itself");

        h.runAfterDelay(CrateBlock.OPEN_TICKS + 5, () -> {
            int end = p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
            check(h, end > before || !ledgerBefore.equals(LedgerData.get(p).serializeNBT(h.getLevel().registryAccess())), "the reward arrived");
            check(h, h.getBlockState(pos).isAir(), "opened crate is used up");
            for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
                check(h, !e.getItem().is(ModItems.WOODEN_CRATE.get()), "used up crate drops nothing");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void panningFindsFromHandPansOnly(GameTestHelper h) {
        clear(h);
        // the rate over many rolls: Prospector Crates only, always locked
        ServerPlayer finder = fresh(h);
        int n = 600000, prospector = 0, other = 0;
        for (int i = 0; i < n; i++) {
            ItemStack s = com.holeydigsite.crate.CrateFinds.roll(h.getLevel(), h.absolutePos(BlockPos.ZERO), "pan", finder);
            if (s.is(ModItems.PROSPECTOR_CRATE.get()) && CrateBlock.isLocked(s)) prospector++;
            else if (!s.isEmpty()) other++;
        }
        check(h, Math.abs(prospector - n / 2000.0) < n / 2000.0 * 0.2, "prospector 1 in 2000: " + prospector);
        check(h, other == 0, "nothing but locked Prospector Crates");

        // the old crates have no finds at all
        for (String old : List.of("wooden_crate", "iron_crate", "gold_crate")) {
            check(h, CrateDefs.tier(old).old() && !CrateDefs.tier(old).found(), old + " is old and never found");
        }
        // a sluice without an owner rolls nothing
        BlockPos sp = new BlockPos(3, 1, 3);
        h.setBlock(sp, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.NORTH));
        SluiceBoxBlockEntity sluice = (SluiceBoxBlockEntity) h.getBlockEntity(sp);
        long rolls = Panning.crateFindRolls();
        long findRolls = com.holeydigsite.crate.CrateFinds.rolls();
        for (int i = 0; i < 200; i++) {
            sluice.addClumps(1);
            sluice.washOneClump(h.getLevel(), h.absolutePos(sp));
            sluice.takeTray();
        }
        check(h, Panning.crateFindRolls() == rolls, "sluice washes are not hand scoops");
        check(h, com.holeydigsite.crate.CrateFinds.rolls() == findRolls, "an ownerless sluice rolls no crate find");

        // a real hand pan does
        BlockPos pan = new BlockPos(1, 1, 1);
        BlockState panState = PanBlock.withFill(ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.WATERLOGGED, true), 4);
        h.setBlock(pan, panState);
        ServerPlayer p = fresh(h);
        BlockPos abs = h.absolutePos(pan);
        p.moveTo(abs.getX() + 0.5, abs.getY() + 0.6, abs.getZ() + 0.5, 0, 90);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BRUSH.get()));
        check(h, Panning.tryStartPanning(h.getBlockState(pan), h.getLevel(), abs, p, InteractionHand.MAIN_HAND), "panning starts");
        int cycle = Panning.cycleTicks(p, p.getMainHandItem());
        Panning.tickPanning(h.getLevel(), p, cycle);
        Panning.tickPanning(h.getLevel(), p, cycle * 2);
        check(h, Panning.crateFindRolls() == rolls + 2, "two hand scoops roll twice, got " + (Panning.crateFindRolls() - rolls));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void tradingPostAndLedgerSources(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        Map<Integer, ResourceLocation> layout = SiteDefs.layout(HoleyDigsite.id("home"));
        check(h, "bolt_cutters".equals(layout.get(37).getPath()) && !layout.containsKey(38) && !layout.containsKey(39),
                "slot 37 sells Bolt Cutters and the crates are gone");
        check(h, ShopCatalogue.find(reg, ModItems.BOLT_CUTTERS.get()).get().price() == 1536, "cutter price");
        check(h, ShopCatalogue.find(reg, ModItems.WOODEN_CRATE.get()).isEmpty() && ShopCatalogue.find(reg, ModItems.IRON_CRATE.get()).isEmpty()
                && ShopCatalogue.find(reg, ModItems.GOLD_CRATE.get()).isEmpty(), "the old crates are not sold");
        check(h, LedgerDefs.level(19).rewards().get(0).item().get().getPath().equals("prospector_crate")
                && LedgerDefs.level(19).rewards().get(0).count() == 1, "L19 one Prospector Crate");
        check(h, LedgerDefs.level(36).rewards().get(0).item().get().getPath().equals("prospector_crate")
                && LedgerDefs.level(36).rewards().get(0).count() == 2, "L36 two");
        check(h, LedgerDefs.level(49).rewards().get(0).item().get().getPath().equals("prospector_crate")
                && LedgerDefs.level(49).rewards().get(0).count() == 4, "L49 four");
        ServerPlayer p = fresh(h);
        LedgerData.get(p).setRenown(LedgerDefs.curve().cumulativeForLevel(19));
        check(h, LedgerManager.claim(p, 19), "claim L19");
        check(h, count(p, ModItems.PROSPECTOR_CRATE.get()) == 1, "Prospector Crate from the Ledger");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crateAndSkinCommands(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        CommandSourceStack src = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        String name = "@s";
        var cmds = h.getLevel().getServer().getCommands();
        cmds.performPrefixedCommand(src, "holeydigsite crate give " + name + " iron_crate 3");
        check(h, count(p, ModItems.IRON_CRATE.get()) == 3, "3 iron crates given");
        cmds.performPrefixedCommand(src, "holeydigsite crate give " + name + " gold_crate");
        check(h, count(p, ModItems.GOLD_CRATE.get()) == 1, "1 gold crate given");
        int odds;
        try {
            odds = cmds.getDispatcher().execute("holeydigsite crate odds wooden_crate", src);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            odds = -1;
        }
        check(h, odds == 1, "odds command works");
        cmds.performPrefixedCommand(src, "holeydigsite skin give " + name + " brass mining_drill pit_scarred");
        check(h, kits(p, "brass", "pit_scarred") == 1, "kit with a wear");
        cmds.performPrefixedCommand(src, "holeydigsite skin give " + name + " brass mining_drill");
        check(h, kits(p, "brass", SkinWear.FRESH) == 1, "kit without a wear is Fresh");
        cmds.performPrefixedCommand(src, "holeydigsite skin give " + name + " frosted mining_drill nonsense");
        check(h, kits(p, "frosted", SkinWear.FRESH) == 0 && kits(p, "frosted", "nonsense") == 0, "a bad wear gives nothing");
        h.succeed();
    }
}
