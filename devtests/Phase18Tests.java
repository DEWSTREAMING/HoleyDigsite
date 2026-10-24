package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.effect.ModEffects;
import com.holeydigsite.panning.Panning;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.sluice.SluiceUpgrade;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 18 (sandbox only): Seed Screen (install / remove / drop, seed tables per clump kind), own crops (plant, grow, loot), foods
 * and effects, sell values. Template {@code empty_long} (26x5x5).
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase18Tests {

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 26; x++) for (int y = 1; y <= 4; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 0; x < 26; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
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

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static int itemEntities(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    // ------------------------------------------------------------------------------------------------ Seed Screen

    @GameTest(template = "empty_long")
    public static void screenInstallsOneAtATimeRemovesAndDrops(GameTestHelper h) {
        clear(h);
        for (Block b : new Block[]{ModBlocks.SLUICE_BOX.get(), ModBlocks.SLUICE_BOX_T2.get()}) {
            BlockPos rel = new BlockPos(3, 1, 2);
            h.setBlock(rel, b.defaultBlockState());
            ServerPlayer p = player(h);
            click(h, p, rel, new ItemStack(ModItems.SEED_SCREEN.get(), 2), false);
            h.assertTrue(h.getBlockState(rel).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.SEED, "seed screen not installed on " + b);
            h.assertTrue(p.getMainHandItem().getCount() == 1, "one screen should be used up");
            // another upgrade is refused while the slot is taken
            ItemStack riffle = new ItemStack(ModItems.RIFFLE_INSERT.get());
            click(h, p, rel, riffle, false);
            h.assertTrue(h.getBlockState(rel).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.SEED && riffle.getCount() == 1, "slot not exclusive");
            // and the screen cannot replace a value upgrade
            click(h, p, rel, ItemStack.EMPTY, true);
            h.assertTrue(h.getBlockState(rel).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.NONE && count(p, ModItems.SEED_SCREEN.get()) == 1, "not removed / not returned");
            click(h, p, rel, new ItemStack(ModItems.RIFFLE_INSERT.get()), false);
            ItemStack screen = new ItemStack(ModItems.SEED_SCREEN.get());
            click(h, p, rel, screen, false);
            h.assertTrue(h.getBlockState(rel).getValue(SluiceBoxBlock.UPGRADE) == SluiceUpgrade.RIFFLE && screen.getCount() == 1, "screen replaced the riffle");
            click(h, p, rel, ItemStack.EMPTY, true);
            click(h, p, rel, new ItemStack(ModItems.SEED_SCREEN.get()), false);
            h.getLevel().removeBlock(h.absolutePos(rel), false);
            h.assertTrue(itemEntities(h, ModItems.SEED_SCREEN.get()) >= 1, "installed screen not dropped on break");
            h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        }
        h.succeed();
    }

    private static final Map<String, Item> SEED_ITEMS = new HashMap<>();

    static {
        SEED_ITEMS.put("glowroot", ModItems.GLOWROOT_SEEDS.get());
        SEED_ITEMS.put("riverbean", ModItems.RIVERBEAN_SEEDS.get());
        SEED_ITEMS.put("dune_melon", ModItems.DUNE_MELON_SEEDS.get());
        SEED_ITEMS.put("wheat", Items.WHEAT_SEEDS);
        SEED_ITEMS.put("carrot", Items.CARROT);
        SEED_ITEMS.put("potato", Items.POTATO);
        SEED_ITEMS.put("beetroot", Items.BEETROOT_SEEDS);
    }

    /** Seeds per type from {@code washes} clumps of {@code kind} through a Tier 2 sluice with {@code upgrade}. */
    private static Map<String, Integer> wash(GameTestHelper h, SluiceUpgrade upgrade, int kind, int washes) {
        BlockPos rel = new BlockPos(3, 1, 2);
        h.setBlock(rel, ModBlocks.SLUICE_BOX_T2.get().defaultBlockState().setValue(SluiceBoxBlock.UPGRADE, upgrade));
        BlockPos abs = h.absolutePos(rel);
        ServerLevel level = h.getLevel();
        Map<String, Integer> seeds = new HashMap<>();
        for (int i = 0; i < washes; i++) {
            var be = (SluiceBoxBlockEntity) level.getBlockEntity(abs);
            be.addClumps(1, kind);
            be.washOneClump(level, abs);
            for (ItemStack s : be.takeTray()) {
                for (Map.Entry<String, Item> e : SEED_ITEMS.entrySet()) {
                    if (s.is(e.getValue())) seeds.merge(e.getKey(), s.getCount(), Integer::sum);
                }
            }
        }
        return seeds;
    }

    private static int total(Map<String, Integer> m) {
        return m.values().stream().mapToInt(Integer::intValue).sum();
    }

    @GameTest(template = "empty_long", timeoutTicks = 800)
    public static void seedsOnlyWithScreenAndFromTheRightTable(GameTestHelper h) {
        clear(h);
        int n = 20000;
        // without the screen (and with the other upgrades) there are no seeds at all
        for (int kind = DirtTier.MIN; kind <= 5; kind++) {
            h.assertTrue(total(wash(h, SluiceUpgrade.NONE, kind, 3000)) == 0, "seeds without a screen on kind " + kind);
        }
        h.assertTrue(total(wash(h, SluiceUpgrade.RIFFLE, DirtTier.GRAVEL, 3000)) == 0, "riffle gave seeds");
        h.assertTrue(total(wash(h, SluiceUpgrade.MESH, DirtTier.SAND, 3000)) == 0, "mesh gave seeds");
        double[] expectRate = {0, 1 / 12.0, 1 / 11.0, 1 / 10.0, 1 / 11.0, 1 / 11.0};
        StringBuilder report = new StringBuilder();
        for (int kind = DirtTier.MIN; kind <= 5; kind++) {
            Map<String, Integer> s = wash(h, SluiceUpgrade.SEED, kind, n);
            int sum = total(s);
            double rate = sum / (double) n;
            report.append(String.format("kind %d: 1 per %.1f %s; ", kind, 1 / rate, s));
            h.assertTrue(Math.abs(rate - expectRate[kind]) < expectRate[kind] * 0.15, "seed rate of kind " + kind + ": " + report);
            h.assertTrue(1 / rate >= 8 && 1 / rate <= 15, "target 1 per 8-15 clumps: " + report);
            switch (kind) {
                case 1 -> h.assertTrue(s.keySet().stream().noneMatch(k -> k.equals("glowroot") || k.equals("riverbean") || k.equals("dune_melon"))
                        && s.size() == 4, "dirt kind 1 gives only the four vanilla seeds: " + s);
                case 2, 3 -> {
                    h.assertTrue(!s.containsKey("riverbean") && !s.containsKey("dune_melon"), "deep dirt gave another plot's crop: " + s);
                    double own = s.getOrDefault("glowroot", 0) / (double) sum;
                    h.assertTrue(Math.abs(own - 0.5) < 0.06, "glowroot should be ~half of deep dirt seeds: " + s);
                    h.assertTrue(s.size() == 5, "deep dirt: glowroot + the four vanilla seeds: " + s);
                }
                case 4 -> h.assertTrue(s.size() == 1 && s.containsKey("riverbean"), "gravel gives riverbean only: " + s);
                default -> h.assertTrue(s.size() == 1 && s.containsKey("dune_melon"), "sand gives dune melon only: " + s);
            }
        }
        HoleyDigsite.LOGGER.info("PHASE18 {}", report);
        // deeper dirt gives slightly more seeds
        h.succeed();
    }

    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void screenLeavesNormalLootAlone(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        var reg = level.registryAccess();
        int n = 8000;
        double[] value = new double[2];
        for (int run = 0; run < 2; run++) {
            BlockPos rel = new BlockPos(3, 1, 2);
            h.setBlock(rel, ModBlocks.SLUICE_BOX_T2.get().defaultBlockState().setValue(SluiceBoxBlock.UPGRADE, run == 0 ? SluiceUpgrade.NONE : SluiceUpgrade.SEED));
            BlockPos abs = h.absolutePos(rel);
            for (int i = 0; i < n; i++) {
                var be = (SluiceBoxBlockEntity) level.getBlockEntity(abs);
                be.addClumps(1, DirtTier.SAND);
                be.washOneClump(level, abs);
                for (ItemStack s : be.takeTray()) value[run] += Selling.stackValue(s, reg);
            }
        }
        h.assertTrue(Math.abs(value[1] / value[0] - 1) < 0.08, "the screen must not change the value of the normal loot: " + value[0] + " vs " + value[1]);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ crops

    private static Block[] crops() {
        return new Block[]{ModBlocks.GLOWROOT_CROP.get(), ModBlocks.RIVERBEAN_CROP.get(), ModBlocks.DUNE_MELON_CROP.get()};
    }

    private static Item[] seeds() {
        return new Item[]{ModItems.GLOWROOT_SEEDS.get(), ModItems.RIVERBEAN_SEEDS.get(), ModItems.DUNE_MELON_SEEDS.get()};
    }

    private static Item[] produce() {
        return new Item[]{ModItems.GLOWROOT.get(), ModItems.RIVERBEAN.get(), ModItems.DUNE_MELON_SLICE.get()};
    }

    @GameTest(template = "empty_long")
    public static void cropsPlantOnFarmlandAndGrow(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        for (int i = 0; i < 3; i++) {
            BlockPos soil = new BlockPos(2 + 4 * i, 1, 2), plant = soil.above();
            h.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            h.setBlock(soil.east(), Blocks.WATER);
            h.setBlock(plant.above(), Blocks.LIGHT);
            ServerPlayer p = player(h);
            ItemStack seed = new ItemStack(seeds()[i], 2);
            click(h, p, soil, seed, false);
            BlockState s = h.getBlockState(plant);
            h.assertTrue(s.is(crops()[i]) && s.getValue(com.holeydigsite.block.OwnCropBlock.AGE) == 0, "seed did not plant " + crops()[i] + ": " + s);
            h.assertTrue(seed.getCount() == 1, "seed not used up");
            // random ticks (vanilla growth) bring it to the last stage
            BlockPos abs = h.absolutePos(plant);
            int ticks = 0;
            while (level.getBlockState(abs).is(crops()[i]) && level.getBlockState(abs).getValue(com.holeydigsite.block.OwnCropBlock.AGE) < 3 && ticks++ < 5000) {
                level.getBlockState(abs).randomTick(level, abs, level.random);
            }
            h.assertTrue(level.getBlockState(abs).is(crops()[i]) && level.getBlockState(abs).getValue(com.holeydigsite.block.OwnCropBlock.AGE) == 3,
                    crops()[i] + " did not ripen by random ticks (" + ticks + "): " + level.getBlockState(abs));
            h.assertTrue(((net.minecraft.world.level.block.CropBlock) crops()[i]).isMaxAge(level.getBlockState(abs)), "isMaxAge");
        }
        // not on plain dirt or stone
        h.setBlock(new BlockPos(20, 1, 2), Blocks.DIRT);
        ServerPlayer q = player(h);
        click(h, q, new BlockPos(20, 1, 2), new ItemStack(seeds()[0]), false);
        h.assertTrue(h.getBlockState(new BlockPos(20, 2, 2)).isAir(), "planted on plain dirt");
        h.succeed();
    }

    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void cropLootUnripeOnlySeedRipeProduceAndSeeds(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        int[] produceCount = {1, 2, 3};
        for (int i = 0; i < 3; i++) {
            BlockPos rel = new BlockPos(3, 2, 2);
            Item seed = seeds()[i], prod = produce()[i];
            for (int age = 0; age <= 3; age++) {
                BlockState s = crops()[i].defaultBlockState().setValue(com.holeydigsite.block.OwnCropBlock.AGE, age);
                int seedSum = 0;
                for (int k = 0; k < 400; k++) {
                    List<ItemStack> drops = Block.getDrops(s, level, h.absolutePos(rel), null);
                    int prodN = drops.stream().filter(d -> d.is(prod)).mapToInt(ItemStack::getCount).sum();
                    int seedN = drops.stream().filter(d -> d.is(seed)).mapToInt(ItemStack::getCount).sum();
                    boolean other = drops.stream().anyMatch(d -> !d.is(prod) && !d.is(seed));
                    h.assertTrue(!other, "unexpected drop: " + drops);
                    if (age < 3) {
                        h.assertTrue(prodN == 0 && seedN == 1, crops()[i] + " age " + age + " must drop exactly its seed: " + drops);
                    } else {
                        h.assertTrue(prodN == produceCount[i], crops()[i] + " ripe must drop " + produceCount[i] + " produce: " + drops);
                    }
                    seedSum += seedN;
                }
                if (age == 3) {
                    double avg = seedSum / 400.0;
                    h.assertTrue(avg > 2.2 && avg < 3.2, crops()[i] + " ripe seeds on average " + avg + " (vanilla 2.71)");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void cropBreaksWithoutFarmland(GameTestHelper h) {
        clear(h);
        BlockPos soil = new BlockPos(3, 1, 2);
        h.setBlock(soil, Blocks.FARMLAND);
        h.setBlock(soil.above(), crops()[0].defaultBlockState());
        h.setBlock(soil, Blocks.STONE);
        h.runAfterDelay(2, () -> {
            h.assertTrue(!h.getBlockState(soil.above()).is(crops()[0]), "crop floats without farmland");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ foods

    private static ItemStack eat(GameTestHelper h, ServerPlayer p, ItemStack food) {
        p.getFoodData().setFoodLevel(4);
        p.getFoodData().setSaturation(0);
        return food.finishUsingItem(h.getLevel(), p);
    }

    @GameTest(template = "empty_long")
    public static void foodsFeedAndApplyTheirEffects(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        // raw foods: hunger only
        for (Item raw : new Item[]{ModItems.GLOWROOT.get(), ModItems.RIVERBEAN.get(), ModItems.DUNE_MELON_SLICE.get()}) {
            p = player(h);
            eat(h, p, new ItemStack(raw));
            h.assertTrue(p.getFoodData().getFoodLevel() > 4, raw + " did not feed");
            h.assertTrue(p.getActiveEffects().isEmpty(), raw + " must have no buff: " + p.getActiveEffects());
        }
        // roasted glowroot: Haste
        p = player(h);
        eat(h, p, new ItemStack(ModItems.ROASTED_GLOWROOT.get()));
        h.assertTrue(p.getFoodData().getFoodLevel() >= 8, "roasted glowroot should feed well");
        var haste = p.getEffect(MobEffects.DIG_SPEED);
        h.assertTrue(haste != null && haste.getAmplifier() == 0 && haste.getDuration() >= 60 * 20 && haste.getDuration() <= 180 * 20, "haste: " + haste);
        // stew: Steady Hands, bowl back, faster panning
        p = player(h);
        ItemStack stew = new ItemStack(ModItems.RIVERBEAN_STEW.get());
        ItemStack after = eat(h, p, stew);
        h.assertTrue(p.getFoodData().getFoodLevel() >= 8, "stew should feed well");
        h.assertTrue(ModEffects.hasSteadyHands(p), "stew must give Steady Hands");
        var sh = p.getEffect(ModEffects.STEADY_HANDS);
        h.assertTrue(sh.getDuration() >= 60 * 20 && sh.getDuration() <= 180 * 20, "steady hands length " + sh.getDuration());
        h.assertTrue(after.is(Items.BOWL) || count(p, Items.BOWL) >= 1, "stew must leave a bowl");
        ItemStack brush = new ItemStack(ModItems.BRUSH.get());
        int slow = Panning.cycleTicks(brush), fast = Panning.cycleTicks(p, brush);
        h.assertTrue(slow == 40 && fast == 28, "scoop ticks with Steady Hands: " + slow + " -> " + fast);
        ServerPlayer q = player(h);
        h.assertTrue(Panning.cycleTicks(q, brush) == 40, "no effect, no speedup");
        // juice: Speed, bottle back
        p = player(h);
        ItemStack juice = new ItemStack(ModItems.DUNE_MELON_JUICE.get());
        after = eat(h, p, juice);
        h.assertTrue(p.getFoodData().getFoodLevel() > 4, "juice should feed");
        var speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
        h.assertTrue(speed != null && speed.getAmplifier() == 0 && speed.getDuration() <= 180 * 20, "speed: " + speed);
        h.assertTrue(after.is(Items.GLASS_BOTTLE) || count(p, Items.GLASS_BOTTLE) >= 1, "juice must leave a bottle");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void recipesAreRegistered(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        String[] ids = {"roasted_glowroot", "roasted_glowroot_from_campfire", "roasted_glowroot_from_smoking", "riverbean_stew", "dune_melon_juice"};
        for (String id : ids) {
            h.assertTrue(rm.byKey(HoleyDigsite.id(id)).isPresent(), "missing recipe " + id);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ selling / shop

    @GameTest(template = "empty_long")
    public static void sellValuesAndCatalogue(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        Object[][] own = {{ModItems.GLOWROOT.get(), 12L}, {ModItems.ROASTED_GLOWROOT.get(), 16L}, {ModItems.RIVERBEAN.get(), 6L},
                {ModItems.RIVERBEAN_STEW.get(), 22L}, {ModItems.DUNE_MELON_SLICE.get(), 5L}, {ModItems.DUNE_MELON_JUICE.get(), 12L}};
        for (Object[] r : own) {
            long v = Selling.unitValue(new ItemStack((Item) r[0]), reg);
            h.assertTrue(v == (Long) r[1], r[0] + " sells for " + v + ", expected " + r[1]);
        }
        // vanilla crops sell, for less per harvest than any own crop
        long wheat = Selling.unitValue(new ItemStack(Items.WHEAT), reg), beet = Selling.unitValue(new ItemStack(Items.BEETROOT), reg);
        long carrot = Selling.unitValue(new ItemStack(Items.CARROT), reg), potato = Selling.unitValue(new ItemStack(Items.POTATO), reg);
        h.assertTrue(wheat == 8 && beet == 8 && carrot == 3 && potato == 3, "vanilla crop values " + wheat + "/" + beet + "/" + carrot + "/" + potato);
        h.assertTrue(carrot * 2.71 < 12 && potato * 2.71 < 12 && wheat < 12, "vanilla crops must pay less than own crops");
        // seeds and bread are not sold back (no loops)
        for (Item it : new Item[]{ModItems.GLOWROOT_SEEDS.get(), ModItems.RIVERBEAN_SEEDS.get(), ModItems.DUNE_MELON_SEEDS.get(), Items.WHEAT_SEEDS, Items.BREAD}) {
            h.assertTrue(Selling.unitValue(new ItemStack(it), reg) == 0, it + " must not sell");
        }
        // catalogue: seed screen unrestricted, starter bread cheap
        var screen = ShopCatalogue.find(reg, ModItems.SEED_SCREEN.get());
        h.assertTrue(screen.isPresent() && screen.get().site().isEmpty() && screen.get().price() == 1536, "seed screen entry");
        var bread = ShopCatalogue.find(reg, Items.BREAD);
        h.assertTrue(bread.isPresent() && bread.get().price() <= 16, "starter bread entry");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void sellPadTakesFarmGoods(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        for (Item it : new Item[]{ModItems.GLOWROOT.get(), ModItems.RIVERBEAN_STEW.get(), Items.WHEAT}) {
            h.assertTrue(com.holeydigsite.sellpad.SellPadBlock.value(List.of(new ItemStack(it, 3)), reg) == 3 * Selling.unitValue(new ItemStack(it), reg)
                    && Selling.isSellable(new ItemStack(it), reg), it + " not sellable at the pad");
        }
        h.succeed();
    }
}
