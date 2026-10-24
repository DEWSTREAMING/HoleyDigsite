package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.panning.Panning;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.AbstractPadBlock;
import com.holeydigsite.shovel.ShovelHelper;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;
import java.util.Set;

/**
 * Sandbox-only GameTests for Phase 7 (not shipped): own resources, Dirtrock tiers, tiered clumps / pans / belts /
 * sluices and the per-tier economy. (Its instant-selling Sell Pad tests moved to PhaseEightTests: the pad no longer sells.)
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseSevenTests {
    private static final BlockPos C = new BlockPos(2, 1, 2);

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h, int sizeX) {
        for (int x = 0; x < sizeX; x++) for (int y = 1; y <= 3; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 0; x < sizeX; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static int entities(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static String key(net.minecraft.network.chat.Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /** Intake (x) -> drill on {@code rock} (x+1, facing east) -> {@code front} block at x+2, all in row z=2. */
    private static BlockPos drillLane(GameTestHelper h, int x, Block rock) {
        h.setBlock(new BlockPos(x - 1, 1, 2), Blocks.STONE);
        h.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(x, 1, 3), Blocks.STONE);
        h.setBlock(new BlockPos(x, 1, 2), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(new BlockPos(x + 1, 0, 2), rock);
        BlockPos drill = new BlockPos(x + 1, 1, 2);
        h.setBlock(drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        return drill;
    }

    /** Skips the drill's wait: powered, 3 ticks before the next clump. */
    private static void almostDone(GameTestHelper h, BlockPos drill) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("WaterPowered", true);
        tag.putInt("Progress", MiningDrillBlock.CYCLE_TICKS - 3);
        h.getBlockEntity(drill).loadCustomOnly(tag, h.getLevel().registryAccess());
    }

    // ------------------------------------------------------------------------------------------------
    // Resources and loot
    // ------------------------------------------------------------------------------------------------

    /** All three tier tables give only our 9 resources, every one of them sellable with a weight. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void lootTablesOnlyGiveOurResources(GameTestHelper h) {
        Set<Item> ours = PhaseTwoTests.lootItems();
        BlockPos pos = h.absolutePos(C);
        for (int tier = 1; tier <= 3; tier++) {
            for (int i = 0; i < 3000; i++) {
                for (ItemStack s : Panning.rollScoop(h.getLevel(), pos, null, tier)) {
                    check(h, ours.contains(s.getItem()), "tier " + tier + " gave " + s);
                    check(h, Selling.grams(s) > 0 && Selling.unitValue(s, h.getLevel().registryAccess()) > 0,
                            "weight + price for " + s);
                }
            }
        }
        // weights from the data map, prices per gram from the sell_values registry
        var reg = h.getLevel().registryAccess();
        check(h, Selling.grams(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get())) == 12, "large Folkiron 12 g");
        check(h, Selling.grams(new ItemStack(ModItems.GOLDCHRO_CHUNK_MEDIUM.get())) == 3, "medium Goldchro 3 g");
        check(h, Selling.grams(new ItemStack(ModItems.RUBY_SMALL.get())) == 1, "small Ruby 1 g");
        check(h, Selling.unitValue(new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get()), reg) == 10, "small Folkiron 10 copper");
        check(h, Selling.unitValue(new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get()), reg) == 112, "large Goldchro 112 copper");
        check(h, Selling.unitValue(new ItemStack(ModItems.RUBY_LARGE.get()), reg) == 560, "large Ruby 560 copper");
        check(h, !Selling.resource(new ItemStack(ModItems.RUBY_MEDIUM.get())).meltable(), "rubies are not meltable");
        // rare-find fanfare: all rubies + large Goldchro, nothing else
        check(h, new ItemStack(ModItems.RUBY_SMALL.get()).is(Panning.RARE_FINDS) && new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get()).is(Panning.RARE_FINDS)
                && !new ItemStack(ModItems.GOLDCHRO_CHUNK_MEDIUM.get()).is(Panning.RARE_FINDS), "rare finds tag");
        h.succeed();
    }

    /** Tier 2 ~1.6x and tier 3 ~2.2x the tier 1 value; Ruby ~1 in 20 / 1 in 10. (Tier 1 targets: PhaseTwoTests.) */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void tierEconomy(GameTestHelper h) {
        final int scoops = 20_000;
        BlockPos pos = h.absolutePos(C);
        double[] avg = new double[4];
        double[] pRuby = new double[4], pGoldchro = new double[4], pLargeRuby = new double[4];
        for (int tier = 1; tier <= 3; tier++) {
            long value = 0;
            int ruby = 0, goldchro = 0, largeRuby = 0;
            for (int i = 0; i < scoops; i++) {
                boolean r = false, g = false, lr = false;
                for (ItemStack s : Panning.rollScoop(h.getLevel(), pos, null, tier)) {
                    value += Selling.stackValue(s, h.getLevel().registryAccess());
                    r |= s.is(ModItems.UNCUT_RUBY_SMALL.get()) || s.is(ModItems.UNCUT_RUBY_MEDIUM.get()) || s.is(ModItems.UNCUT_RUBY_LARGE.get()); // Phase 12: uncut
                    lr |= s.is(ModItems.UNCUT_RUBY_LARGE.get());
                    g |= s.is(ModItems.GOLDCHRO_CHUNK_SMALL.get()) || s.is(ModItems.GOLDCHRO_CHUNK_MEDIUM.get()) || s.is(ModItems.GOLDCHRO_CHUNK_LARGE.get());
                }
                if (r) ruby++;
                if (g) goldchro++;
                if (lr) largeRuby++;
            }
            avg[tier] = (double) value / scoops;
            pRuby[tier] = (double) ruby / scoops;
            pGoldchro[tier] = (double) goldchro / scoops;
            pLargeRuby[tier] = (double) largeRuby / scoops;
            HoleyDigsite.LOGGER.info(String.format("[economy] tier %d: avg %.2f copper/scoop (%.2fx tier 1), Goldchro 1 in %.1f, Ruby 1 in %.1f, large Ruby 1 in %.0f",
                    tier, avg[tier], avg[tier] / avg[1], 1 / pGoldchro[tier], 1 / pRuby[tier], largeRuby == 0 ? Double.POSITIVE_INFINITY : 1 / pLargeRuby[tier]));
        }
        check(h, avg[2] / avg[1] >= 1.45 && avg[2] / avg[1] <= 1.75, "tier 2 ~1.6x tier 1, is " + avg[2] / avg[1]);
        check(h, avg[3] / avg[1] >= 2.0 && avg[3] / avg[1] <= 2.4, "tier 3 ~2.2x tier 1, is " + avg[3] / avg[1]);
        check(h, Math.abs(pRuby[2] * 20 - 1) <= 0.2, "Ruby ~1 in 20 at tier 2, 1 in " + 1 / pRuby[2]);
        check(h, Math.abs(pRuby[3] * 10 - 1) <= 0.2, "Ruby ~1 in 10 at tier 3, 1 in " + 1 / pRuby[3]);
        check(h, pGoldchro[2] > pGoldchro[1] && pGoldchro[3] > pGoldchro[2], "more Goldchro deeper");
        check(h, pLargeRuby[3] < 0.025, "large rubies stay rare at tier 3: 1 in " + 1 / pLargeRuby[3]);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Rock tiers
    // ------------------------------------------------------------------------------------------------

    /** A drill on Dirtrock / Deep / Abyssal Dirtrock puts tier 1 / 2 / 3 dirt into the pan in front. */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void drillRockGivesClumpTier(GameTestHelper h) {
        clear(h, 20);
        Block[] rocks = {ModBlocks.DIRTROCK.get(), ModBlocks.DEEP_DIRTROCK.get(), ModBlocks.ABYSSAL_DIRTROCK.get()};
        BlockPos[] drills = new BlockPos[3], pans = new BlockPos[3];
        for (int i = 0; i < 3; i++) {
            drills[i] = drillLane(h, 2 + i * 6, rocks[i]);
            pans[i] = drills[i].east();
            h.setBlock(pans[i], ModBlocks.PAN.get().defaultBlockState());
        }
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    for (BlockPos d : drills) almostDone(h, d);
                })
                .thenWaitUntil(() -> {
                    for (BlockPos p : pans) check(h, h.getBlockState(p).getValue(PanBlock.FILL) == 1, "pan got its clump");
                })
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        int tier = h.getBlockState(pans[i]).getValue(PanBlock.TIER);
                        check(h, tier == i + 1, "drill on " + rocks[i] + " makes tier " + (i + 1) + " dirt, pan has " + tier);
                    }
                })
                .thenSucceed();
    }

    /** Deep and Abyssal Dirtrock are as unbreakable as Dirtrock (mining, scooping, explosions, pistons, tags). */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deeperRockIsUnbreakable(GameTestHelper h) {
        clear(h, 5);
        ServerPlayer p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
        Block[] rocks = {ModBlocks.DEEP_DIRTROCK.get(), ModBlocks.ABYSSAL_DIRTROCK.get()};
        BlockPos[] at = {new BlockPos(1, 1, 2), new BlockPos(3, 1, 2)};
        int top = h.getLevel().getMaxBuildHeight();
        for (int i = 0; i < 2; i++) {
            h.setBlock(at[i], rocks[i]);
            BlockPos abs = h.absolutePos(at[i]);
            p.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 0);
            p.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, top, 0);
            h.assertBlockPresent(rocks[i], at[i]);
            BlockState s = h.getBlockState(at[i]);
            check(h, s.getDestroySpeed(h.getLevel(), abs) < 0, "hardness -1");
            check(h, rocks[i].getExplosionResistance() >= 3600000.0F, "explosion-proof");
            check(h, s.is(BlockTags.WITHER_IMMUNE) && s.is(BlockTags.DRAGON_IMMUNE), "wither/dragon immune");
            check(h, s.is(MiningDrillBlock.DRILL_BASE), "drills work on it");
            check(h, !s.is(ShovelHelper.SCOOPABLE), "not scoopable");
            check(h, !s.isValidSpawn(h.getLevel(), abs, EntityType.ZOMBIE), "no mob spawns");
        }
        check(h, !ShovelHelper.isFull(p.getMainHandItem()), "nothing scooped");
        h.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        Vec3 boom = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 2)));
        h.getLevel().explode(null, boom.x, boom.y, boom.z, 4.0F, Level.ExplosionInteraction.TNT);
        h.assertBlockPresent(rocks[0], at[0]);
        h.assertBlockPresent(rocks[1], at[1]);
        h.assertBlockNotPresent(Blocks.STONE, new BlockPos(2, 1, 2));
        // pistons can't push them
        BlockPos rock2 = new BlockPos(2, 1, 4);
        h.setBlock(rock2, rocks[1]);
        h.setBlock(new BlockPos(1, 1, 4), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(0, 1, 4), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(10, () -> {
            h.assertBlockPresent(rocks[1], rock2);
            check(h, !h.getBlockState(new BlockPos(1, 1, 4)).getValue(PistonBaseBlock.EXTENDED), "piston can't extend");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Clumps, pans
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty")
    public static void clumpTierComponentAndStacking(GameTestHelper h) {
        ItemStack plain = new ItemStack(ModItems.DIRT_CLUMP.get());
        ItemStack t1 = DirtTier.clumps(1, 1), t2 = DirtTier.clumps(2, 1), t2b = DirtTier.clumps(2, 5), t3 = DirtTier.clumps(3, 1);
        check(h, ItemStack.isSameItemSameComponents(plain, t1), "tier 1 clumps stack with plain (bought) clumps");
        check(h, ItemStack.isSameItemSameComponents(t2, t2b), "same tier stacks");
        check(h, !ItemStack.isSameItemSameComponents(t1, t2) && !ItemStack.isSameItemSameComponents(t2, t3), "different tiers don't stack");
        check(h, DirtTier.ofClump(plain) == 1 && DirtTier.ofClump(t2) == 2 && DirtTier.ofClump(t3) == 3, "tier read back");
        check(h, key(plain.getHoverName()).equals("item.holeydigsite.dirt_clump"), "Dirt Clump");
        check(h, key(t2.getHoverName()).equals("item.holeydigsite.dirt_clump.tier_2"), "Deep Dirt Clump: " + key(t2.getHoverName()));
        check(h, key(t3.getHoverName()).equals("item.holeydigsite.dirt_clump.tier_3"), "Abyssal Dirt Clump");
        // survives a save round trip
        ItemStack back = ItemStack.parse(h.getLevel().registryAccess(), t3.save(h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        check(h, DirtTier.ofClump(back) == 3, "tier saved");
        h.succeed();
    }

    /** A pan holds one tier: other tiers are refused (hand, hopper, drill) until it is empty; pickup keeps tier + fill. */
    @GameTest(template = "empty")
    public static void panHoldsOneTier(GameTestHelper h) {
        clear(h, 5);
        ServerPlayer p = player(h);
        h.setBlock(C, ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 2));
        p.setItemInHand(InteractionHand.MAIN_HAND, DirtTier.clumps(2, 3));
        h.useBlock(C, p);
        check(h, h.getBlockState(C).getValue(PanBlock.FILL) == 2 && p.getMainHandItem().getCount() == 3,
                "tier 1 pan refuses a Deep clump");
        IItemHandler handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(C), Direction.UP);
        check(h, handler.insertItem(0, DirtTier.clumps(3, 1), false).getCount() == 1, "hopper can't insert another tier");
        check(h, handler.insertItem(0, DirtTier.clumps(1, 1), false).isEmpty(), "same tier goes in");
        check(h, !((PanBlock) ModBlocks.PAN.get()).canAcceptClump(h.getLevel(), h.absolutePos(C), h.getBlockState(C), Direction.WEST, 2),
                "drill/belt input refuses another tier");
        p.setItemInHand(InteractionHand.MAIN_HAND, ShovelHelper.createFullShovel());
        h.useBlock(C, p);
        check(h, h.getBlockState(C).getValue(PanBlock.FILL) == 4, "shovel dirt is tier 1");

        // an empty pan takes any tier
        h.setBlock(C, ModBlocks.PAN.get().defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND, DirtTier.clumps(2, 3));
        h.useBlock(C, p);
        h.useBlock(C, p);
        h.useBlock(C, p);
        check(h, h.getBlockState(C).getValue(PanBlock.FILL) == 3 && h.getBlockState(C).getValue(PanBlock.TIER) == 2, "deep pan 3/4");
        p.setItemInHand(InteractionHand.MAIN_HAND, ShovelHelper.createFullShovel());
        h.useBlock(C, p);
        check(h, h.getBlockState(C).getValue(PanBlock.FILL) == 3 && ShovelHelper.isFull(p.getMainHandItem()), "deep pan refuses shovel dirt");

        // pick up: the item keeps fill + tier; placing restores both
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.useBlock(C, p);
        h.assertBlockNotPresent(ModBlocks.PAN.get(), C);
        ItemStack pan = p.getInventory().items.stream().filter(s -> s.is(ModItems.PAN.get())).findFirst().orElse(ItemStack.EMPTY);
        check(h, PanBlock.getFill(pan) == 3 && PanBlock.getTier(pan) == 2, "pan item keeps fill 3 + tier 2: " + pan);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, pan);
        BlockPos floor = h.absolutePos(C.below());
        pan.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        check(h, h.getBlockState(C).is(ModBlocks.PAN.get()) && h.getBlockState(C).getValue(PanBlock.FILL) == 3
                && h.getBlockState(C).getValue(PanBlock.TIER) == 2, "placed back as a deep pan: " + h.getBlockState(C));
        h.succeed();
    }

    /** Hand panning rolls the pan's tier table; the emptied pan goes back to tier 1. (Own batch: roll counters.) */
    @GameTest(template = "empty", batch = "p7_pan_rolls", timeoutTicks = 200)
    public static void panningRollsThePansTier(GameTestHelper h) {
        for (BlockPos b : BlockPos.betweenClosed(0, 0, 0, 4, 3, 4)) {
            h.setBlock(b, b.getY() == 0 ? Blocks.STONE : Blocks.AIR);
        }
        h.setBlock(C, ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 2).setValue(PanBlock.TIER, 3)
                .setValue(PanBlock.WATERLOGGED, true));
        ServerPlayer p = player(h);
        Vec3 feet = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 1, 0)));
        p.moveTo(feet.x, feet.y, feet.z);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BRUSH.get()));
        Vec3 top = Vec3.atBottomCenterOf(h.absolutePos(C)).add(0, 0.2, 0);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, top);
        long t1 = Panning.rollCount(1), t3 = Panning.rollCount(3);
        h.useBlock(C, p, new BlockHitResult(top, Direction.UP, h.absolutePos(C), false));
        for (int i = 0; i < Panning.PAN_CYCLE_TICKS * 2; i++) p.doTick();
        check(h, h.getBlockState(C).getValue(PanBlock.FILL) == 0, "panned empty");
        check(h, Panning.rollCount(3) - t3 == 2 && Panning.rollCount(1) == t1, "2 scoops from the tier 3 table, none from tier 1");
        check(h, h.getBlockState(C).getValue(PanBlock.TIER) == 1, "an empty pan is back to tier 1");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Belts and sluices
    // ------------------------------------------------------------------------------------------------

    /** A belt carries the tier; a pan with another tier makes it wait (backs up, nothing voided). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void beltKeepsTierAndWaitsForOtherTierPan(GameTestHelper h) {
        clear(h, 5);
        BlockPos belt = new BlockPos(1, 1, 2), pan = new BlockPos(2, 1, 2);
        h.setBlock(pan, ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 2));
        h.setBlock(belt, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST)
                .setValue(ClumpBeltBlock.SPOUT, true));
        TestWater.underBelt(h, belt);
        ClumpBeltBlockEntity be = (ClumpBeltBlockEntity) h.getBlockEntity(belt);
        be.insertByPlayer(1, 2);
        h.startSequence()
                .thenExecuteAfter(60, () -> {
                    check(h, be.clumpCount() == 1 && be.clumps().get(0).tier() == 2, "deep clump waits on the belt");
                    check(h, h.getBlockState(pan).getValue(PanBlock.FILL) == 2, "tier 1 pan untouched");
                    // empty the pan -> the deep clump drops in
                    h.setBlock(pan, ModBlocks.PAN.get().defaultBlockState());
                })
                .thenWaitUntil(() -> check(h, h.getBlockState(pan).getValue(PanBlock.FILL) == 1, "clump lands"))
                .thenExecute(() -> {
                    check(h, h.getBlockState(pan).getValue(PanBlock.TIER) == 2, "pan is now deep");
                    List<ItemStack> taken = be.takeAll();
                    check(h, taken.isEmpty(), "belt empty");
                    be.insertByPlayer(2, 3);
                    be.insertByPlayer(1, 1);
                    List<ItemStack> back = be.takeAll();
                    check(h, back.size() == 2 && DirtTier.ofClump(back.get(0)) == 1 && back.get(0).getCount() == 1
                            && DirtTier.ofClump(back.get(1)) == 3 && back.get(1).getCount() == 2, "take-all gives one stack per tier: " + back);
                })
                .thenSucceed();
    }

    /** The sluice queue keeps each clump's tier, in order, and breaking drops them per tier. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sluiceQueueKeepsTiers(GameTestHelper h) {
        clear(h, 5);
        h.setBlock(C, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        SluiceBoxBlockEntity s = (SluiceBoxBlockEntity) h.getBlockEntity(C);
        s.addClumps(1, 3);
        s.addClumps(1, 1);
        s.addClumps(1, 2);
        check(h, s.queuedTiers().equals(List.of(3, 1, 2)), "queue order + tiers " + s.queuedTiers());
        ServerPlayer p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, DirtTier.clumps(3, 5));
        h.useBlock(C, p);
        check(h, s.queuedTiers().equals(List.of(3, 1, 2, 3)), "hand insert keeps the tier " + s.queuedTiers());
        h.getLevel().destroyBlock(h.absolutePos(C), true);
        h.runAfterDelay(2, () -> {
            int[] perTier = new int[4];
            for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
                if (e.getItem().is(ModItems.DIRT_CLUMP.get())) perTier[DirtTier.ofClump(e.getItem())] += e.getItem().getCount();
            }
            check(h, perTier[1] == 1 && perTier[2] == 1 && perTier[3] == 2, "dropped clumps per tier 1/2/3 = "
                    + perTier[1] + "/" + perTier[2] + "/" + perTier[3]);
            h.succeed();
        });
    }

    /** Deep drill -> belt -> sluice: a tier 2 clump arrives and the sluice rolls the tier 2 table. (Own batch.) */
    @GameTest(template = "empty_long", batch = "p7_sluice_rolls", timeoutTicks = 400)
    public static void sluiceRollsTheClumpsTier(GameTestHelper h) {
        clear(h, 10);
        BlockPos drill = drillLane(h, 2, ModBlocks.DEEP_DIRTROCK.get());
        BlockPos belt = new BlockPos(4, 1, 2), sluice = new BlockPos(5, 1, 2);
        h.setBlock(belt, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        TestWater.underBelt(h, belt);
        h.setBlock(sluice, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(5, 1, 0), Blocks.STONE);
        h.setBlock(new BlockPos(4, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(6, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(5, 1, 1), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        long[] before = new long[4];
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    for (int t = 1; t <= 3; t++) before[t] = Panning.rollCount(t);
                    almostDone(h, drill);
                })
                .thenWaitUntil(() -> check(h, ((SluiceBoxBlockEntity) h.getBlockEntity(sluice)).queued() == 1, "clump arrives"))
                .thenExecute(() -> check(h, ((SluiceBoxBlockEntity) h.getBlockEntity(sluice)).queuedTiers().equals(List.of(2)), "it is a Deep clump"))
                .thenWaitUntil(() -> check(h, !((SluiceBoxBlockEntity) h.getBlockEntity(sluice)).tray().isEmpty(), "washed"))
                .thenExecute(() -> {
                    check(h, Panning.rollCount(2) - before[2] == 1 && Panning.rollCount(1) == before[1] && Panning.rollCount(3) == before[3],
                            "exactly one tier 2 roll");
                    for (ItemStack st : ((SluiceBoxBlockEntity) h.getBlockEntity(sluice)).tray()) {
                        check(h, PhaseTwoTests.lootItems().contains(st.getItem()), "our loot only: " + st);
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Sell Pad
    // ------------------------------------------------------------------------------------------------

    private static void pad(GameTestHelper h) {
        clear(h, 5);
        h.setBlock(C, ModBlocks.SELL_PAD.get());
    }

    /** Coins, machines, clumps and vanilla items are refused and stay in the hand. */
    @GameTest(template = "empty")
    public static void sellPadRefusesUnsellable(GameTestHelper h) {
        pad(h);
        ServerPlayer p = player(h);
        for (ItemStack s : new ItemStack[]{new ItemStack(ModItems.GOLD_COIN.get(), 5), new ItemStack(ModItems.SLUICE_BOX.get()),
                DirtTier.clumps(3, 4), new ItemStack(Items.DIAMOND, 2), new ItemStack(ModItems.SELL_PAD.get())}) {
            ItemStack copy = s.copy();
            p.setItemInHand(InteractionHand.MAIN_HAND, s);
            h.useBlock(C, p);
            check(h, ItemStack.matches(p.getMainHandItem(), copy), "refused and kept: " + copy + " -> " + p.getMainHandItem());
            check(h, !Selling.isSellable(copy, h.getLevel().registryAccess()), "not sellable: " + copy);
        }
        check(h, count(p, ModItems.COPPER_COIN.get()) + count(p, ModItems.SILVER_COIN.get()) == 0, "nothing paid");
        check(h, AbstractPadBlock.pad(h.getLevel(), h.absolutePos(C)).isEmpty(), "nothing lands on the pad");
        h.succeed();
    }

    /** Sell Pad: breaks by hand and drops itself; craftable from planks + logs (island tree). */
    @GameTest(template = "empty")
    public static void sellPadBreaksAndCrafts(GameTestHelper h) {
        pad(h);
        ServerPlayer p = player(h);
        BlockPos abs = h.absolutePos(C);
        check(h, h.getBlockState(C).getDestroySpeed(h.getLevel(), abs) >= 0, "breakable");
        p.gameMode.destroyBlock(abs);
        h.assertBlockNotPresent(ModBlocks.SELL_PAD.get(), C);
        h.runAfterDelay(2, () -> {
            check(h, entities(h, ModItems.SELL_PAD.get()) == 1, "drops itself");
            CraftingMenu menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), abs));
            ItemStack pl = new ItemStack(Items.OAK_PLANKS), lg = new ItemStack(Items.OAK_LOG), e = ItemStack.EMPTY;
            ItemStack[] grid = {pl, pl, pl, lg, lg, lg, e, e, e};
            for (int i = 0; i < 9; i++) menu.getSlot(1 + i).set(grid[i].copy());
            ItemStack r = menu.getSlot(0).getItem();
            check(h, r.is(ModItems.SELL_PAD.get()), "3 planks over 3 logs -> Sell Pad, got " + r);
            h.succeed();
        });
    }
}
