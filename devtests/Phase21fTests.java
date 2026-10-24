package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shovel.ShovelHelper;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.tier.DirtTier;
import com.holeydigsite.panning.Panning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/**
 * Phase 21f (sandbox only): Coarse dirt, rooted dirt, and moss become pannable:
 * <ul>
 *   <li>Empty wooden shovel scoops coarse dirt, rooted dirt, and moss block into full shovel with kind 6, 7, 8</li>
 *   <li>Depositing into pan sets pan fill and tier (6, 7, 8)</li>
 *   <li>Clumps: DIRT_CLUMP with CLUMP_TIER 6, 7, 8; names; feeding pans and sluices</li>
 *   <li>Panning loot tables: coarse dirt (+20-30% value vs plain dirt), rooted dirt (= plain dirt ore, vanilla seeds ~1/10, own-crop ~1/60),
 *       moss (~50% dirt metals, seeds ~1/3.5, own-crop ~1/25)</li>
 *   <li>Starter bed given once on join to existing players without a bed, never twice, and not if already present</li>
 * </ul>
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21fTests {

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

    private static void click(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held, boolean sneak) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(sneak);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false));
        p.setShiftKeyDown(false);
    }

    private static int itemEntities(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int kind(GameTestHelper h, BlockPos pan) {
        return h.getBlockState(pan).getValue(PanBlock.TIER);
    }

    /** An empty wooden shovel scoops vanilla coarse dirt, rooted dirt, and moss block into full shovel with kind 6, 7, 8. */
    @GameTest(template = "empty_long")
    public static void shovelScoopsCoarseRootedMoss(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h, GameType.SURVIVAL);
        Block[] blocks = {Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK};
        int[] kinds = {DirtTier.COARSE_DIRT, DirtTier.ROOTED_DIRT, DirtTier.MOSS};

        for (int i = 0; i < 3; i++) {
            BlockPos b = new BlockPos(2 + i * 4, 1, 1), pan = new BlockPos(2 + i * 4, 1, 3);
            h.setBlock(b, blocks[i]);
            h.setBlock(pan, ModBlocks.PAN.get());
            ItemStack shovel = new ItemStack(Items.WOODEN_SHOVEL);
            p.setItemInHand(InteractionHand.MAIN_HAND, shovel);
            p.gameMode.destroyBlock(h.absolutePos(b));
            ItemStack held = p.getMainHandItem();
            check(h, h.getBlockState(b).isAir() && ShovelHelper.isFull(held) && ShovelHelper.kind(held) == kinds[i],
                    blocks[i] + " scooped as kind " + ShovelHelper.kind(held));
            check(h, itemEntities(h, blocks[i].asItem()) == 0, "no block drop for " + blocks[i]);

            // Right-click into pan
            click(h, p, pan, held, false);
            check(h, PanBlock.fill(h.getBlockState(pan)) == 1 && kind(h, pan) == kinds[i] && !ShovelHelper.isFull(p.getMainHandItem())
                    && p.getMainHandItem().get(ModDataComponents.SHOVEL_KIND.get()) == null, "pan got kind " + kinds[i]);

            // Pick up pan: preserves fill and tier
            ItemStack panItem = ((PanBlock) ModBlocks.PAN.get()).createStack(PanBlock.fill(h.getBlockState(pan)), kind(h, pan));
            check(h, PanBlock.getFill(panItem) == 1 && PanBlock.getTier(panItem) == kinds[i], "pan stack keeps kind " + kinds[i]);
        }
        h.succeed();
    }

    /** Clumps: DIRT_CLUMP with CLUMP_TIER 6, 7, 8; names; feeding pans and sluices. */
    @GameTest(template = "empty_long")
    public static void clumpItemsAndPans(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h, GameType.SURVIVAL);

        // Clump items, names, and ofClump
        ItemStack coarseClump = DirtTier.clumps(DirtTier.COARSE_DIRT, 1);
        ItemStack rootedClump = DirtTier.clumps(DirtTier.ROOTED_DIRT, 1);
        ItemStack mossClump = DirtTier.clumps(DirtTier.MOSS, 1);

        check(h, coarseClump.is(ModItems.DIRT_CLUMP.get()) && DirtTier.ofClump(coarseClump) == DirtTier.COARSE_DIRT, "coarse clump kind");
        check(h, rootedClump.is(ModItems.DIRT_CLUMP.get()) && DirtTier.ofClump(rootedClump) == DirtTier.ROOTED_DIRT, "rooted clump kind");
        check(h, mossClump.is(ModItems.DIRT_CLUMP.get()) && DirtTier.ofClump(mossClump) == DirtTier.MOSS, "moss clump kind");

        check(h, coarseClump.getHoverName().getString().equals("Coarse Dirt Clump"), "coarse clump name");
        check(h, rootedClump.getHoverName().getString().equals("Rooted Dirt Clump"), "rooted clump name");
        check(h, mossClump.getHoverName().getString().equals("Moss Clump"), "moss clump name");

        // Pan feeding
        BlockPos pan = new BlockPos(5, 1, 2);
        h.setBlock(pan, ModBlocks.PAN.get());

        // Feeding coarse clump to empty pan
        click(h, p, pan, DirtTier.clumps(DirtTier.COARSE_DIRT, 2), false);
        check(h, PanBlock.fill(h.getBlockState(pan)) == 1 && kind(h, pan) == DirtTier.COARSE_DIRT, "coarse in pan");

        // Adding another coarse clump
        click(h, p, pan, DirtTier.clumps(DirtTier.COARSE_DIRT, 1), false);
        check(h, PanBlock.fill(h.getBlockState(pan)) == 2 && kind(h, pan) == DirtTier.COARSE_DIRT, "coarse fill +1");

        // Refuses different kind (rooted)
        click(h, p, pan, DirtTier.clumps(DirtTier.ROOTED_DIRT, 1), false);
        check(h, PanBlock.fill(h.getBlockState(pan)) == 2, "pan refuses different tier");

        // Sluice queue with new kinds
        BlockPos sl = new BlockPos(8, 1, 2);
        h.setBlock(sl, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        SluiceBoxBlockEntity s = (SluiceBoxBlockEntity) h.getBlockEntity(sl);
        s.addClumps(1, DirtTier.COARSE_DIRT);
        s.addClumps(1, DirtTier.ROOTED_DIRT);
        s.addClumps(1, DirtTier.MOSS);
        check(h, s.queuedTiers().equals(List.of(DirtTier.COARSE_DIRT, DirtTier.ROOTED_DIRT, DirtTier.MOSS)), "sluice queue order");

        List<ItemStack> drops = s.queuedAsItems();
        check(h, drops.size() == 3 && drops.stream().allMatch(st -> st.is(ModItems.DIRT_CLUMP.get())), "sluice drops clump items");

        h.succeed();
    }

    /** Panning loot tables: coarse (+20-30%), rooted (= dirt ore, seed rates), moss (50% metals, seed rates). */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void panningLootAndSeedRates(GameTestHelper h) {
        int n = 3000;
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        BlockPos origin = h.absolutePos(new BlockPos(2, 1, 2));

        // 1. Plain dirt baseline
        double dirtMetalVal = 0, dirtGemVal = 0;
        for (int i = 0; i < n; i++) {
            List<ItemStack> loot = Panning.rollScoop(h.getLevel(), origin, null, DirtTier.DIRT);
            for (ItemStack st : loot) {
                var res = Selling.resource(st);
                if (res != null) {
                    long val = Selling.unitValue(st, reg) * st.getCount();
                    if (res.meltable()) dirtMetalVal += val;
                    else dirtGemVal += val;
                }
            }
        }
        double avgDirtMetal = dirtMetalVal / n;
        double avgDirtTotal = (dirtMetalVal + dirtGemVal) / n;

        // 2. Coarse dirt: +20-30% value vs plain dirt
        double coarseVal = 0;
        for (int i = 0; i < n; i++) {
            List<ItemStack> loot = Panning.rollScoop(h.getLevel(), origin, null, DirtTier.COARSE_DIRT);
            for (ItemStack st : loot) {
                coarseVal += Selling.unitValue(st, reg) * st.getCount();
            }
        }
        double avgCoarse = coarseVal / n;
        double coarseRatio = avgCoarse / avgDirtTotal;
        check(h, coarseRatio >= 1.18 && coarseRatio <= 1.32, "coarse dirt +20-30% value: " + coarseRatio + "x (" + avgCoarse + " vs " + avgDirtTotal + ")");

        // 3. Rooted dirt: same ore value, vanilla seeds ~1 in 10, own-crop ~1 in 60
        double rootedOreVal = 0;
        int rootedTotalSeeds = 0, rootedOwnSeeds = 0;
        for (int i = 0; i < n; i++) {
            List<ItemStack> loot = Panning.rollScoop(h.getLevel(), origin, null, DirtTier.ROOTED_DIRT);
            for (ItemStack st : loot) {
                var res = Selling.resource(st);
                if (res != null && (res.meltable() || !res.isCrop())) {
                    rootedOreVal += Selling.unitValue(st, reg) * st.getCount();
                }
                if (st.getItem().toString().contains("seed") || st.is(Items.CARROT) || st.is(Items.POTATO)) {
                    rootedTotalSeeds += st.getCount();
                    if (st.is(ModItems.GLOWROOT_SEEDS.get())) {
                        rootedOwnSeeds += st.getCount();
                    }
                }
            }
        }
        double avgRootedOre = rootedOreVal / n;
        double rootedOreRatio = avgRootedOre / avgDirtTotal;
        check(h, rootedOreRatio >= 0.94 && rootedOreRatio <= 1.06, "rooted ore matches dirt: " + rootedOreRatio + "x (" + avgRootedOre + " vs " + avgDirtTotal + ")");
        double rootedSeedRate = (double) n / Math.max(1, rootedTotalSeeds);
        double rootedOwnRate = (double) n / Math.max(1, rootedOwnSeeds);
        check(h, rootedSeedRate >= 7.5 && rootedSeedRate <= 13.5, "rooted total seeds ~1 in 10: 1 in " + rootedSeedRate);
        check(h, rootedOwnRate >= 42.0 && rootedOwnRate <= 85.0, "rooted own-crop seeds ~1 in 60: 1 in " + rootedOwnRate);

        // 4. Moss: ~50% dirt metals, seeds ~1 in 3-4, own-crop ~1 in 25
        double mossMetalVal = 0;
        int mossTotalSeeds = 0, mossOwnSeeds = 0;
        Set<Item> mossOwn = Set.of(ModItems.GLOWROOT_SEEDS.get(), ModItems.RIVERBEAN_SEEDS.get(), ModItems.DUNE_MELON_SEEDS.get());
        for (int i = 0; i < n; i++) {
            List<ItemStack> loot = Panning.rollScoop(h.getLevel(), origin, null, DirtTier.MOSS);
            for (ItemStack st : loot) {
                var res = Selling.resource(st);
                if (res != null && res.meltable()) {
                    mossMetalVal += Selling.unitValue(st, reg) * st.getCount();
                }
                if (st.getItem().toString().contains("seed") || st.is(Items.CARROT) || st.is(Items.POTATO)) {
                    mossTotalSeeds += st.getCount();
                    if (mossOwn.contains(st.getItem())) {
                        mossOwnSeeds += st.getCount();
                    }
                }
            }
        }
        double avgMossMetal = mossMetalVal / n;
        double mossMetalRatio = avgMossMetal / avgDirtMetal;
        check(h, mossMetalRatio >= 0.44 && mossMetalRatio <= 0.56, "moss metal ~50% of dirt metal: " + mossMetalRatio + "x (" + avgMossMetal + " vs " + avgDirtMetal + ")");
        double mossSeedRate = (double) n / Math.max(1, mossTotalSeeds);
        double mossOwnRate = (double) n / Math.max(1, mossOwnSeeds);
        check(h, mossSeedRate >= 2.8 && mossSeedRate <= 4.6, "moss total seeds ~1 in 3-4: 1 in " + mossSeedRate);
        check(h, mossOwnRate >= 18.0 && mossOwnRate <= 36.0, "moss own-crop seeds ~1 in 25: 1 in " + mossOwnRate);

        HoleyDigsite.LOGGER.info("[phase21f] coarse {}x dirt; rooted ore {}x, 1 seed in {}, 1 own in {}; moss metal {}x, 1 seed in {}, 1 own in {}",
                coarseRatio, rootedOreRatio, rootedSeedRate, rootedOwnRate, mossMetalRatio, mossSeedRate, mossOwnRate);

        h.succeed();
    }

    /** Starter bed is given once to an existing player without a bed, never twice, and not if already present. */
    @GameTest(template = "empty_long")
    public static void starterBedGivenOnceNeverTwice(GameTestHelper h) {
        ServerPlayer p = player(h, GameType.SURVIVAL);
        LedgerData data = LedgerData.get(p);
        HolderLookup.Provider provider = h.getLevel().registryAccess();

        // Simulate an existing player: starterGiven is true, starterBedGiven is false, inventory is empty
        data.setStarterGiven(true);
        data.setStarterBedGiven(false);
        check(h, !LedgerManager.hasBed(p), "no bed initially");

        // First check: should give a bed
        boolean given1 = LedgerManager.checkStarterBed(p, data);
        check(h, given1, "first check gives bed");
        check(h, data.starterBedGiven(), "flag set after first check");
        check(h, LedgerManager.hasBed(p), "player now has a bed");
        check(h, p.getInventory().items.stream().filter(s -> s.is(Items.RED_BED)).count() == 1, "exactly 1 red bed in inventory");

        // Second check: should NOT give another bed
        boolean given2 = LedgerManager.checkStarterBed(p, data);
        check(h, !given2, "second check does not give bed");
        check(h, p.getInventory().items.stream().filter(s -> s.is(Items.RED_BED)).count() == 1, "still only 1 red bed");

        // Test with a fresh player that already has a bed in inventory
        ServerPlayer p2 = player(h, GameType.SURVIVAL);
        LedgerData data2 = LedgerData.get(p2);
        p2.getInventory().add(new ItemStack(Items.RED_BED));
        data2.setStarterGiven(true);
        data2.setStarterBedGiven(false);
        check(h, LedgerManager.hasBed(p2), "p2 already has a bed");

        boolean givenP2 = LedgerManager.checkStarterBed(p2, data2);
        check(h, !givenP2, "no bed given if player already has one");
        check(h, data2.starterBedGiven(), "flag marked true for p2");
        check(h, p2.getInventory().items.stream().filter(s -> s.is(Items.RED_BED)).count() == 1, "p2 still has 1 bed");

        // Persistence test: serialize & deserialize
        CompoundTag tag = data.serializeNBT(provider);
        check(h, tag.getBoolean("StarterBedGiven"), "StarterBedGiven serialized in NBT");

        LedgerData dataLoaded = new LedgerData();
        dataLoaded.deserializeNBT(provider, tag);
        check(h, dataLoaded.starterBedGiven(), "StarterBedGiven loaded from NBT");

        h.succeed();
    }
}
