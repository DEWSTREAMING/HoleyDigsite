package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.DrillStatus;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.ForgeRecipe;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.forge.Metals;
import com.holeydigsite.forge.MoltenMetal;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.panning.Panning;
import com.holeydigsite.plot.PlotBoardBlock;
import com.holeydigsite.plot.PlotBoardBlockEntity;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.shovel.ShovelHelper;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sandbox-only GameTests for Phase 11 (not shipped): Gravelrock / Sandrock, gravel / sand clump kinds through drills,
 * shovels, pans, belts and sluices; the new loot tables and resources (weights, prices, melting, ingots); land plots (lock
 * for survival, open for creative, deed claim, outside machines can't reach in, saved data); new catalogue entries and
 * Crafting Forge recipes; the Tier 3 drill. Template {@code empty_long} (26x5x5).
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseElevenTests {

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

    private static void place(GameTestHelper h, BlockPos rel, BlockState state) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, Block.updateFromNeighbourShapes(state, h.getLevel(), abs), 3);
        TestWater.underBelt(h, rel); // Phase 12: belts need water
    }

    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(mode);
        p.getInventory().clearContent();
        return p;
    }

    private static void give(ServerPlayer p, ItemStack s) {
        for (int i = 9; i < 36; i++) {
            if (p.getInventory().items.get(i).isEmpty()) {
                p.getInventory().items.set(i, s);
                return;
            }
        }
    }

    /** Real click path with a hit on the top face of {@code rel}. */
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

    /** Intake (x=1) -> drill (x=2, east) on {@code rock} -> {@code front} block at x=3, row z. Returns the drill. */
    private static BlockPos drillRow(GameTestHelper h, int z, Block rock) {
        h.setBlock(new BlockPos(0, 1, z), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z - 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z + 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        h.setBlock(new BlockPos(2, 0, z), rock);
        BlockPos drill = new BlockPos(2, 1, z);
        h.setBlock(drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        return drill;
    }

    private static void drillReadyIn(GameTestHelper h, BlockPos drill, int ticks) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("WaterPowered", true);
        tag.putInt("Progress", MiningDrillBlock.cycleTicks(h.getBlockState(drill)) - ticks);
        h.getBlockEntity(drill).loadCustomOnly(tag, h.getLevel().registryAccess());
    }

    /** A plot board at {@code board} whose plot covers x [x0..x1], all of y 0..4, z 0..4 of the test area. */
    private static PlotBoardBlockEntity plot(GameTestHelper h, String id, BlockPos board, int x0, int x1) {
        h.setBlock(board, ModBlocks.PLOT_BOARD.get().defaultBlockState().setValue(PlotBoardBlock.FACING, Direction.WEST));
        PlotBoardBlockEntity b = (PlotBoardBlockEntity) h.getBlockEntity(board);
        b.setPlot(id, new BlockPos(x0 - board.getX(), -board.getY(), -board.getZ()), new BlockPos(x1 - board.getX(), 4 - board.getY(), 4 - board.getZ()));
        return b;
    }

    private static PlotData data(GameTestHelper h) {
        return PlotData.get(h.getLevel());
    }

    /** Removes the test's plot from the (shared) saved data, so later tests in this area aren't locked. */
    private static void forget(GameTestHelper h, String id, BlockPos board) {
        data(h).remove(id, h.absolutePos(board));
    }

    // ------------------------------------------------------------------------------------------------
    // Rocks, clump kinds
    // ------------------------------------------------------------------------------------------------

    /** Gravelrock and Sandrock are unbreakable like Dirtrock, drill bases with their own kind tags. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void newRocksAreUnbreakable(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h, GameType.SURVIVAL);
        Block[] rocks = {ModBlocks.GRAVELROCK.get(), ModBlocks.SANDROCK.get()};
        int[] kinds = {DirtTier.GRAVEL, DirtTier.SAND};
        for (int i = 0; i < 2; i++) {
            BlockPos at = new BlockPos(2 + i * 4, 1, 2);
            h.setBlock(at, rocks[i]);
            BlockPos abs = h.absolutePos(at);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            int top = h.getLevel().getMaxBuildHeight();
            p.gameMode.handleBlockBreakAction(abs, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 0);
            p.gameMode.handleBlockBreakAction(abs, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, top, 0);
            h.assertBlockPresent(rocks[i], at);
            BlockState s = h.getBlockState(at);
            check(h, s.getDestroySpeed(h.getLevel(), abs) < 0 && rocks[i].getExplosionResistance() >= 3600000.0F, "hardness -1, blast proof");
            check(h, s.is(BlockTags.WITHER_IMMUNE) && s.is(BlockTags.DRAGON_IMMUNE), "wither/dragon immune");
            check(h, s.is(MiningDrillBlock.DRILL_BASE), "drill base");
            check(h, DirtTier.ofRock(s) == kinds[i], "kind " + kinds[i]);
            check(h, !s.is(ShovelHelper.SCOOPABLE) && !s.isValidSpawn(h.getLevel(), abs, EntityType.ZOMBIE), "not scoopable, no spawns");
        }
        Vec3 boom = Vec3.atCenterOf(h.absolutePos(new BlockPos(4, 2, 2)));
        h.getLevel().explode(null, boom.x, boom.y, boom.z, 4.0F, Level.ExplosionInteraction.TNT);
        h.assertBlockPresent(rocks[0], new BlockPos(2, 1, 2));
        h.assertBlockPresent(rocks[1], new BlockPos(6, 1, 2));
        h.succeed();
    }

    /** A drill on Gravelrock puts a Gravel Clump (kind 4) into its pan, on Sandrock a Sand Clump (kind 5). */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void drillsMakeGravelAndSandClumps(GameTestHelper h) {
        clear(h);
        BlockPos dg = drillRow(h, 1, ModBlocks.GRAVELROCK.get()), ds = drillRow(h, 3, ModBlocks.SANDROCK.get());
        BlockPos pg = new BlockPos(3, 1, 1), ps = new BlockPos(3, 1, 3);
        h.setBlock(pg, ModBlocks.PAN.get());
        h.setBlock(ps, ModBlocks.PAN.get());
        h.runAfterDelay(3, () -> {
            check(h, h.getBlockState(dg).getValue(MiningDrillBlock.STATUS) == DrillStatus.WORKING, "drill on Gravelrock works");
            drillReadyIn(h, dg, 2);
            drillReadyIn(h, ds, 2);
            h.runAfterDelay(8, () -> {
                check(h, PanBlock.fill(h.getBlockState(pg)) == 1 && kind(h, pg) == DirtTier.GRAVEL, "gravel pan: " + h.getBlockState(pg));
                check(h, PanBlock.fill(h.getBlockState(ps)) == 1 && kind(h, ps) == DirtTier.SAND, "sand pan: " + h.getBlockState(ps));
                h.succeed();
            });
        });
    }

    /** An empty wooden shovel scoops vanilla gravel / sand (real break) into a gravel / sand pan. */
    @GameTest(template = "empty_long")
    public static void shovelScoopsGravelAndSand(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h, GameType.SURVIVAL);
        Block[] blocks = {Blocks.GRAVEL, Blocks.SAND, Blocks.DIRT};
        int[] kinds = {DirtTier.GRAVEL, DirtTier.SAND, DirtTier.DIRT};
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
            check(h, itemEntities(h, blocks[i].asItem()) == 0, "no block drop");
            click(h, p, pan, held, false);
            check(h, PanBlock.fill(h.getBlockState(pan)) == 1 && kind(h, pan) == kinds[i] && !ShovelHelper.isFull(p.getMainHandItem())
                    && p.getMainHandItem().get(com.holeydigsite.registry.ModDataComponents.SHOVEL_KIND.get()) == null, "pan got kind " + kinds[i]);
        }
        h.succeed();
    }

    /** A pan holds one kind: a gravel pan refuses sand and dirt (hand, hopper handler, drill); empty takes anything; pickup keeps it. */
    @GameTest(template = "empty_long")
    public static void panHoldsOneKind(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h, GameType.SURVIVAL);
        BlockPos pan = new BlockPos(5, 1, 2);
        h.setBlock(pan, ModBlocks.PAN.get());
        click(h, p, pan, DirtTier.clumps(DirtTier.GRAVEL, 2), false);
        check(h, kind(h, pan) == DirtTier.GRAVEL && PanBlock.fill(h.getBlockState(pan)) == 1, "gravel in");
        click(h, p, pan, DirtTier.clumps(DirtTier.SAND, 1), false);
        click(h, p, pan, DirtTier.clumps(DirtTier.DIRT, 1), false);
        check(h, PanBlock.fill(h.getBlockState(pan)) == 1, "sand and dirt refused by hand");
        IItemHandler cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pan), Direction.UP);
        check(h, cap.insertItem(0, DirtTier.clumps(DirtTier.SAND, 3), false).getCount() == 3, "hopper: sand refused");
        check(h, cap.insertItem(0, DirtTier.clumps(DirtTier.GRAVEL, 5), false).getCount() == 2, "hopper: gravel up to 4");
        check(h, cap.isItemValid(0, DirtTier.clumps(DirtTier.SAND, 1)) && cap.isItemValid(0, new ItemStack(ModItems.GRAVEL_CLUMP.get())), "clumps are valid items");
        check(h, !((PanBlock) h.getBlockState(pan).getBlock()).canAcceptClump(h.getLevel(), h.absolutePos(pan), h.getBlockState(pan), Direction.WEST, DirtTier.SAND),
                "drill input: other kind refused");
        ItemStack item = ModBlocks.PAN.get().createStack(4, DirtTier.SAND);
        check(h, PanBlock.getFill(item) == 4 && PanBlock.getTier(item) == DirtTier.SAND, "pan item keeps the sand kind");
        // clump items and names
        check(h, DirtTier.clumps(DirtTier.GRAVEL, 1).is(ModItems.GRAVEL_CLUMP.get()) && DirtTier.ofClump(new ItemStack(ModItems.SAND_CLUMP.get())) == DirtTier.SAND,
                "kind <-> item");
        check(h, ItemStack.isSameItemSameComponents(DirtTier.clumps(DirtTier.GRAVEL, 1), new ItemStack(ModItems.GRAVEL_CLUMP.get())), "drilled = bought gravel clumps stack");
        h.succeed();
    }

    /** Belts carry kinds: gravel into an empty pan, then a sand clump waits in front of the gravel pan. Sluice keeps kinds in order. */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void beltsAndSluicesCarryKinds(GameTestHelper h) {
        clear(h);
        BlockPos b = new BlockPos(3, 1, 2), pan = new BlockPos(4, 1, 2);
        h.setBlock(pan, ModBlocks.PAN.get());
        place(h, b, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        ClumpBeltBlockEntity belt = (ClumpBeltBlockEntity) h.getBlockEntity(b);
        belt.insertByPlayer(1, DirtTier.GRAVEL);
        BlockPos sl = new BlockPos(10, 1, 2);
        h.setBlock(sl, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        SluiceBoxBlockEntity s = (SluiceBoxBlockEntity) h.getBlockEntity(sl);
        s.addClumps(1, DirtTier.SAND);
        s.addClumps(1, DirtTier.GRAVEL);
        s.addClumps(1, 2);
        check(h, s.queuedTiers().equals(List.of(DirtTier.SAND, DirtTier.GRAVEL, 2)), "sluice queue keeps kinds in order: " + s.queuedTiers());
        List<ItemStack> drops = s.queuedAsItems();
        check(h, drops.stream().anyMatch(st -> st.is(ModItems.SAND_CLUMP.get())) && drops.stream().anyMatch(st -> st.is(ModItems.GRAVEL_CLUMP.get())),
                "sluice drops gravel/sand clump items: " + drops);
        h.startSequence()
                .thenWaitUntil(() -> check(h, PanBlock.fill(h.getBlockState(pan)) == 1, "gravel clump arrives"))
                .thenExecute(() -> {
                    check(h, kind(h, pan) == DirtTier.GRAVEL, "pan is a gravel pan");
                    belt.insertByPlayer(1, DirtTier.SAND);
                })
                .thenExecuteAfter(50, () -> {
                    check(h, belt.clumpCount() == 1 && PanBlock.fill(h.getBlockState(pan)) == 1, "sand waits in front of the gravel pan");
                    check(h, belt.takeAll().get(0).is(ModItems.SAND_CLUMP.get()), "taking it gives a Sand Clump");
                })
                .thenSucceed();
    }

    /** The gravel / sand tables give only their resources; ~1 gem in 25 / 20 scoops; every result is weighted and sellable. */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void gravelAndSandLootTables(GameTestHelper h) {
        Set<Item> gravel = Set.of(ModItems.ORCHREMIA_CHUNK_SMALL.get(), ModItems.ORCHREMIA_CHUNK_MEDIUM.get(), ModItems.ORCHREMIA_CHUNK_LARGE.get(),
                ModItems.FOLKIRON_CHUNK_SMALL.get(), ModItems.FOLKIRON_CHUNK_MEDIUM.get(),
                ModItems.UNCUT_SAPPHIRE_SMALL.get(), ModItems.UNCUT_SAPPHIRE_MEDIUM.get(), ModItems.UNCUT_SAPPHIRE_LARGE.get()); // Phase 12: uncut
        Set<Item> sand = Set.of(ModItems.SILVANE_CHUNK_SMALL.get(), ModItems.SILVANE_CHUNK_MEDIUM.get(), ModItems.SILVANE_CHUNK_LARGE.get(),
                ModItems.GOLDCHRO_CHUNK_SMALL.get(), ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), ModItems.GOLDCHRO_CHUNK_LARGE.get(),
                ModItems.UNCUT_TOPAZ_SMALL.get(), ModItems.UNCUT_TOPAZ_MEDIUM.get(), ModItems.UNCUT_TOPAZ_LARGE.get());
        int n = 4000;
        int[] gems = new int[2];
        long[] value = new long[2];
        var reg = h.getLevel().registryAccess();
        for (int k = 0; k < 2; k++) {
            int kind = k == 0 ? DirtTier.GRAVEL : DirtTier.SAND;
            Set<Item> allowed = k == 0 ? gravel : sand;
            for (int i = 0; i < n; i++) {
                List<ItemStack> loot = Panning.rollScoop(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2)), null, kind);
                boolean gem = false;
                for (ItemStack st : loot) {
                    check(h, allowed.contains(st.getItem()), "kind " + kind + " gave " + st);
                    check(h, Selling.resource(st) != null && Selling.unitValue(st, reg) > 0, "weighted + sellable: " + st);
                    gem |= Selling.resource(st) != null && !Selling.resource(st).meltable();
                    value[k] += Selling.unitValue(st, reg) * st.getCount();
                }
                if (gem) gems[k]++;
            }
        }
        double g1 = (double) n / Math.max(1, gems[0]), g2 = (double) n / Math.max(1, gems[1]);
        check(h, g1 > 19 && g1 < 32, "sapphire about 1 in 25: 1 in " + g1);
        check(h, g2 > 15 && g2 < 26, "topaz about 1 in 20: 1 in " + g2);
        double v1 = value[0] / (double) n, v2 = value[1] / (double) n;
        check(h, v1 > 125 && v1 < 155, "gravel ~139 copper per clump: " + v1);
        check(h, v2 > 180 && v2 < 215, "sand ~197 copper per clump: " + v2);
        HoleyDigsite.LOGGER.info("[economy] gravel {} copper/clump, 1 gem in {}; sand {} copper/clump, 1 gem in {}", v1, g1, v2, g2);
        h.succeed();
    }

    /** New resources: weights, prices, gems not meltable, Orchremia melts (own colour) and casts an Orchremia ingot. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void newResourcesWeightsPricesAndMelting(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        check(h, Selling.grams(new ItemStack(ModItems.ORCHREMIA_CHUNK_LARGE.get())) == 12 && Selling.unitValue(new ItemStack(ModItems.ORCHREMIA_CHUNK_LARGE.get()), reg) == 102,
                "large Orchremia 12 g = 102 copper");
        check(h, Selling.grams(new ItemStack(ModItems.SILVANE_CHUNK_LARGE.get())) == 7 && Selling.unitValue(new ItemStack(ModItems.SILVANE_CHUNK_LARGE.get()), reg) == 252,
                "large Silvane 7 g = 252");
        check(h, Selling.unitValue(new ItemStack(ModItems.SAPPHIRE_LARGE.get()), reg) == 1320 && Selling.unitValue(new ItemStack(ModItems.TOPAZ_LARGE.get()), reg) == 1400,
                "gems: Sapphire 1320, Topaz 1400");
        check(h, Selling.unitValue(IngotItem.of(ModItems.SILVANE_INGOT.get(), 50), reg) == 2250
                && Selling.unitValue(IngotItem.of(ModItems.ORCHREMIA_INGOT.get(), 40), reg) == 425, "ingots +25 %");
        ResourceInfo sapphire = Selling.resource(new ItemStack(ModItems.SAPPHIRE_SMALL.get()));
        check(h, sapphire != null && !sapphire.meltable() && Metals.meltable(new ItemStack(ModItems.TOPAZ_LARGE.get())) == null, "gems don't melt");
        check(h, Metals.ingotFor(HoleyDigsite.id("orchremia")) == ModItems.ORCHREMIA_INGOT.get()
                && Metals.ingotFor(HoleyDigsite.id("silvane")) == ModItems.SILVANE_INGOT.get(), "ingot items");
        clear(h);
        BlockPos forge = new BlockPos(4, 1, 2), cru = new BlockPos(4, 2, 2), mold = new BlockPos(3, 1, 2);
        h.setBlock(forge, ModBlocks.FORGE.get().defaultBlockState().setValue(com.holeydigsite.forge.ForgeBlock.FACING, Direction.WEST));
        BlockPos intake = h.absolutePos(new BlockPos(5, 1, 2));   // lava-logged intake behind the forge (flag 2: no flooding)
        BlockState in = ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(com.holeydigsite.power.LavaIntakeBlock.LAVALOGGED, true);
        for (Direction dir : Direction.values()) {
            in = in.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(dir),
                    com.holeydigsite.power.WaterNetworkBlock.connects(h.getLevel(), intake, in, dir));
        }
        h.getLevel().setBlock(intake, in, Block.UPDATE_CLIENTS);
        com.holeydigsite.power.WaterNetworks.recompute(h.getLevel(), Set.of(h.absolutePos(forge)));
        h.setBlock(cru, ModBlocks.CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.FACING, Direction.WEST));
        h.setBlock(mold, ModBlocks.MOLD.get());
        ServerPlayer p = player(h, GameType.SURVIVAL);
        click(h, p, cru, new ItemStack(ModItems.SAPPHIRE_MEDIUM.get(), 2), false);
        check(h, ((CrucibleBlockEntity) h.getBlockEntity(cru)).total() == 0, "sapphire refused");
        click(h, p, cru, new ItemStack(ModItems.ORCHREMIA_CHUNK_MEDIUM.get(), 4), false);   // 20 g
        CrucibleBlockEntity c = (CrucibleBlockEntity) h.getBlockEntity(cru);
        check(h, c.unmelted() == 20 && HoleyDigsite.id("orchremia").equals(c.metal()), "20 g Orchremia in");
        h.runAfterDelay(100, () -> {
            check(h, c.molten() == 20 && h.getBlockState(cru).getValue(CrucibleBlock.METAL) == MoltenMetal.ORCHREMIA, "molten, Orchremia colour");
            CrucibleBlock.pour(h.getLevel(), h.absolutePos(cru), h.getBlockState(cru), null);
            h.runAfterDelay(70, () -> {
                click(h, p, mold, ItemStack.EMPTY, false);
                ItemStack ingot = ItemStack.EMPTY;
                for (ItemStack st : p.getInventory().items) if (st.is(ModItems.ORCHREMIA_INGOT.get())) ingot = st;
                check(h, IngotItem.grams(ingot) == 20, "a 20 g Orchremia ingot: " + ingot);
                h.succeed();
            });
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Land plots
    // ------------------------------------------------------------------------------------------------

    /**
     * A locked plot (x 8..20): survival can't break, place, use items on or interact with blocks inside (incl. its shop button),
     * gets the message; outside works; creative is not restricted; the board stays clickable.
     */
    @GameTest(template = "empty_long")
    public static void lockedPlotBlocksSurvivalOnly(GameTestHelper h) {
        clear(h);
        BlockPos board = new BlockPos(8, 1, 0);
        try {
            plot(h, "test_lock", board, 8, 20);
            ServerPlayer p = player(h, GameType.SURVIVAL);
            BlockPos inside = new BlockPos(12, 1, 2), outside = new BlockPos(4, 1, 2);
            h.setBlock(inside, Blocks.DIRT);
            h.setBlock(outside, Blocks.DIRT);
            check(h, PlotLock.isLocked(h.getLevel(), h.absolutePos(inside)) && !PlotLock.isLocked(h.getLevel(), h.absolutePos(outside)), "bounds");
            // break
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
            p.gameMode.destroyBlock(h.absolutePos(inside));
            check(h, h.getBlockState(inside).is(Blocks.DIRT), "can't break inside (not even scoop): " + h.getBlockState(inside)
                    + " full=" + ShovelHelper.isFull(p.getMainHandItem()) + " locked=" + PlotLock.isLocked(h.getLevel(), h.absolutePos(inside))
                    + " creative=" + p.isCreative());
            check(h, !ShovelHelper.isFull(p.getMainHandItem()), "nothing scooped");
            p.gameMode.destroyBlock(h.absolutePos(outside));
            check(h, h.getBlockState(outside).isAir(), "outside: scooped");
            // place
            click(h, p, new BlockPos(14, 0, 2), new ItemStack(Items.COBBLESTONE, 4), false);
            check(h, h.getBlockState(new BlockPos(14, 1, 2)).isAir() && p.getMainHandItem().getCount() == 4, "can't place inside");
            click(h, p, new BlockPos(4, 0, 2), new ItemStack(Items.COBBLESTONE, 4), false);
            check(h, h.getBlockState(new BlockPos(4, 1, 2)).is(Blocks.COBBLESTONE), "outside: placed");
            // interact: a lever inside can't be flipped, a pan inside can't take clumps
            BlockPos lever = new BlockPos(16, 1, 2), pan = new BlockPos(18, 1, 2);
            h.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACE,
                    net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
            h.setBlock(pan, ModBlocks.PAN.get());
            click(h, p, lever, ItemStack.EMPTY, false);
            check(h, !h.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED), "lever not flipped");
            click(h, p, pan, new ItemStack(ModItems.DIRT_CLUMP.get(), 2), false);
            check(h, PanBlock.fill(h.getBlockState(pan)) == 0 && p.getMainHandItem().getCount() == 2, "pan inside not usable");
            // the board answers (no deed: just a message) - it's not swallowed by the lock
            click(h, p, board, ItemStack.EMPTY, false);
            // creative: no restriction
            ServerPlayer c = player(h, GameType.CREATIVE);
            c.gameMode.destroyBlock(h.absolutePos(inside));
            check(h, h.getBlockState(inside).isAir(), "creative breaks inside");
            click(h, c, lever, ItemStack.EMPTY, false);
            check(h, h.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED), "creative flips the lever");
            // the board is unbreakable in survival
            BlockPos ab = h.absolutePos(board);
            int top = h.getLevel().getMaxBuildHeight();
            p.gameMode.handleBlockBreakAction(ab, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, top, 0);
            p.gameMode.handleBlockBreakAction(ab, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, top, 0);
            check(h, h.getBlockState(board).is(ModBlocks.PLOT_BOARD.get()) && h.getBlockState(board).getDestroySpeed(h.getLevel(), ab) < 0, "board stays");
        } finally {
            forget(h, "test_lock", board);
        }
        h.succeed();
    }

    /** Wrong deed refused; the right deed is used up, the board shows Owned, saved data says owned, and the plot opens up. */
    @GameTest(template = "empty_long")
    public static void deedClaimUnlocksThePlot(GameTestHelper h) {
        clear(h);
        BlockPos board = new BlockPos(8, 1, 0);
        try {
            plot(h, "deep_dig", board, 8, 20);
            ServerPlayer p = player(h, GameType.SURVIVAL);
            BlockPos inside = new BlockPos(12, 1, 2);
            h.setBlock(inside, Blocks.DIRT);
            click(h, p, board, new ItemStack(ModItems.LAND_DEED_SAND_DUNES.get()), false);
            check(h, p.getMainHandItem().getCount() == 1 && !h.getBlockState(board).getValue(PlotBoardBlock.OWNED), "wrong deed refused");
            click(h, p, board, new ItemStack(ModItems.LAND_DEED_DEEP_DIG.get()), false);
            check(h, p.getMainHandItem().isEmpty() && h.getBlockState(board).getValue(PlotBoardBlock.OWNED), "claimed: deed used up, board Owned");
            PlotData.Plot plot = data(h).get("deep_dig", h.absolutePos(board));
            check(h, plot != null && plot.owned(), "saved data: owned");
            check(h, !PlotLock.isLocked(h.getLevel(), h.absolutePos(inside)), "unlocked");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
            p.gameMode.destroyBlock(h.absolutePos(inside));
            check(h, h.getBlockState(inside).isAir(), "can dig now");
            click(h, p, board, new ItemStack(ModItems.LAND_DEED_DEEP_DIG.get()), false);
            check(h, p.getMainHandItem().getCount() == 1, "a second deed isn't used");
            // saved data round trip
            CompoundTag tag = data(h).save(new CompoundTag(), h.getLevel().registryAccess());
            PlotData copy = PlotData.load(tag, h.getLevel().registryAccess());
            PlotData.Plot again = copy.get("deep_dig", h.absolutePos(board));
            check(h, again != null && again.owned() && again.box().equals(plot.box()), "save/load keeps bounds + ownership");
            // re-registering (board reload) keeps ownership
            ((PlotBoardBlockEntity) h.getBlockEntity(board)).register();
            check(h, data(h).get("deep_dig", h.absolutePos(board)).owned(), "reload keeps owned");
        } finally {
            forget(h, "deep_dig", board);
        }
        h.succeed();
    }

    /** Outside machines can't reach into a locked plot: drill output, belt delivery, hopper (pan handler); after claiming they can. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void outsideMachinesCantReachIn(GameTestHelper h) {
        clear(h);
        BlockPos board = new BlockPos(25, 1, 4);
        plot(h, "gravel_flats", board, 3, 25);    // x 3.. : the drill (x=2) is outside, its pan (x=3) inside
        BlockPos drill = drillRow(h, 1, ModBlocks.DIRTROCK.get());
        BlockPos pan = new BlockPos(3, 1, 1);
        h.setBlock(pan, ModBlocks.PAN.get());
        // belt outside (x=2, row 3) facing into a pan inside (x=3)
        BlockPos belt = new BlockPos(2, 1, 3), pan2 = new BlockPos(3, 1, 3);
        h.setBlock(pan2, ModBlocks.PAN.get());
        place(h, belt, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        IItemHandler cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pan2), Direction.UP);
        check(h, cap.insertItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 2), false).getCount() == 2, "hopper handler refuses while locked");
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    drillReadyIn(h, drill, 2);
                    ((ClumpBeltBlockEntity) h.getBlockEntity(belt)).insertByPlayer(1);
                })
                .thenExecuteAfter(40, () -> {
                    check(h, PanBlock.fill(h.getBlockState(pan)) == 0, "drill can't output into the locked plot");
                    check(h, PanBlock.fill(h.getBlockState(pan2)) == 0 && ((ClumpBeltBlockEntity) h.getBlockEntity(belt)).clumpCount() == 1,
                            "belt waits at the plot edge");
                    check(h, itemEntities(h, ModItems.DIRT_CLUMP.get()) == 0, "nothing dropped");
                    ServerPlayer p = player(h, GameType.SURVIVAL);
                    click(h, p, board, new ItemStack(ModItems.LAND_DEED_GRAVEL_FLATS.get()), false);
                })
                .thenWaitUntil(() -> check(h, PanBlock.fill(h.getBlockState(pan2)) == 1, "after claiming the belt delivers"))
                .thenExecute(() -> {
                    check(h, cap.insertItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 1), false).isEmpty(), "hopper handler works now");
                    forget(h, "gravel_flats", board);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Shop, recipes, Tier 3
    // ------------------------------------------------------------------------------------------------

    /** Deeds (400 / 2400 / 4500 gold) and the gravel / sand clumps are in the catalogue. */
    @GameTest(template = "empty_long")
    public static void catalogueHasDeedsAndClumps(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        Object[][] expected = {{ModItems.LAND_DEED_DEEP_DIG.get(), 400L * 64}, {ModItems.LAND_DEED_GRAVEL_FLATS.get(), 2400L * 64},
                {ModItems.LAND_DEED_SAND_DUNES.get(), 4500L * 64}, {ModItems.GRAVEL_CLUMP.get(), 150L}, {ModItems.SAND_CLUMP.get(), 210L}};
        for (Object[] e : expected) {
            var entry = ShopCatalogue.find(reg, (Item) e[0]);
            check(h, entry.isPresent() && entry.get().price() == (long) e[1], e[0] + " for " + e[1]);
        }
        check(h, ShopCatalogue.all(reg).size() >= 26, "26+ catalogue entries: " + ShopCatalogue.all(reg).size());
        h.succeed();
    }

    /** The three new recipes: Tier 3 Drill (both new metals + Goldchro) and the Orchremia / Silvane routes; T3 drill = 100 ticks. */
    @GameTest(template = "empty_long")
    public static void newForgeRecipesAndTier3Drill(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        check(h, ForgeRecipes.all(reg).size() >= 8, "8+ recipes: " + ForgeRecipes.all(reg).size());
        ForgeRecipe t3 = ForgeRecipes.find(reg, HoleyDigsite.id("mining_drill_t3")).recipe();
        check(h, t3.grams(HoleyDigsite.id("orchremia")) == 7000 && t3.grams(HoleyDigsite.id("silvane")) == 2400
                && t3.grams(HoleyDigsite.id("goldchro")) == 250 && t3.partCount(ModItems.MINING_DRILL_T2.get()) == 1
                && t3.resultStack().is(ModItems.MINING_DRILL_T3.get()), "T3 recipe");
        ForgeRecipe belt = ForgeRecipes.find(reg, HoleyDigsite.id("fast_clump_belt_orchremia")).recipe();
        check(h, belt.grams(HoleyDigsite.id("orchremia")) == 500 && belt.resultStack().getCount() == 4, "Orchremia belts");
        ForgeRecipe pan = ForgeRecipes.find(reg, HoleyDigsite.id("large_pan_silvane")).recipe();
        check(h, pan.grams(HoleyDigsite.id("silvane")) == 170 && pan.resultStack().is(ModItems.LARGE_PAN.get()), "Silvane Large Pan");
        check(h, ModBlocks.MINING_DRILL_T3.get().cycleTicks() == 100, "T3 drill: 100 ticks");
        check(h, com.holeydigsite.registry.ModBlockEntities.MINING_DRILL.get().isValid(ModBlocks.MINING_DRILL_T3.get().defaultBlockState()), "T3 uses the drill BE");
        h.succeed();
    }

    /** A Tier 3 drill on Sandrock makes a Sand Clump every 100 ticks. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void tier3DrillRunsThreeTimesAsFast(GameTestHelper h) {
        clear(h);
        BlockPos d = drillRow(h, 2, ModBlocks.SANDROCK.get());
        h.setBlock(d, ModBlocks.MINING_DRILL_T3.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        BlockPos pan = new BlockPos(3, 1, 2);
        h.setBlock(pan, ModBlocks.LARGE_PAN.get());
        h.startSequence()
                .thenExecuteAfter(95, () -> check(h, PanBlock.fill(h.getBlockState(pan)) == 0, "nothing before 100 ticks"))
                .thenExecuteAfter(10, () -> check(h, PanBlock.fill(h.getBlockState(pan)) == 1 && kind(h, pan) == DirtTier.SAND, "a sand clump at ~100"))
                .thenExecuteAfter(100, () -> check(h, PanBlock.fill(h.getBlockState(pan)) == 2, "2 at ~200"))
                .thenSucceed();
    }
}
