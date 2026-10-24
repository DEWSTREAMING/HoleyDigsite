package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.forge.MoldBlockEntity;
import com.holeydigsite.forge.MoltenMetal;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.power.LavaIntakeBlock;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.power.WaterPipeBlock;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopButtonBlock;
import com.holeydigsite.shop.ShopCatalogue;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Set;

/**
 * Sandbox-only GameTests for Phase 9 (not shipped): lava power (separate from water, never consumed), Forge, Crucible
 * (meltables only, one metal, 200 g, hopper insert, melting rate, pouring by click / redstone), adjustable Mold (targets,
 * partial pours and top-ups, cooling, exact-weight ingots), ingots (stacking, Sell Pad price per gram, re-melting), the
 * "at least X g" ingredient and the shop entries. Template {@code empty_long} (26x5x5, stone floor).
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseNineTests {
    /** Forge facing west (front x=3, lava port east), Lava Intake behind it, Crucible on top, Mold in front at the forge's level. */
    private static final BlockPos FORGE = new BlockPos(4, 1, 2);
    private static final BlockPos INTAKE = new BlockPos(5, 1, 2);
    private static final BlockPos CRUC = new BlockPos(4, 2, 2);
    private static final BlockPos MOLD = new BlockPos(3, 1, 2);
    private static final ResourceLocation FOLKIRON = HoleyDigsite.id("folkiron");
    private static final ResourceLocation GOLDCHRO = HoleyDigsite.id("goldchro");

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

    /** Sets a block the way a player placing it would see it: pipe connections computed from the neighbours. */
    private static void place(GameTestHelper h, BlockPos rel, BlockState state) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, Block.updateFromNeighbourShapes(state, h.getLevel(), abs), 3);
    }

    private static void recompute(GameTestHelper h, BlockPos... rel) {
        Set<BlockPos> abs = new java.util.HashSet<>();
        for (BlockPos r : rel) abs.add(h.absolutePos(r));
        WaterNetworks.recompute(h.getLevel(), abs);
    }

    private static BlockState forgeState() {
        return ModBlocks.FORGE.get().defaultBlockState().setValue(ForgeBlock.FACING, Direction.WEST);
    }

    /**
     * Puts a Lava Intake at {@code rel} with the collars computed from the neighbours, but WITHOUT neighbour / shape updates
     * (flag 2 only), so a lava-logged intake does not schedule a lava tick and flood the test area with its source.
     */
    private static void placeIntake(GameTestHelper h, BlockPos rel, boolean lavalogged) {
        BlockPos abs = h.absolutePos(rel);
        BlockState s = ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(LavaIntakeBlock.LAVALOGGED, lavalogged);
        for (Direction dir : Direction.values()) {
            s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), WaterNetworkBlock.connects(h.getLevel(), abs, s, dir));
        }
        h.getLevel().setBlock(abs, s, Block.UPDATE_CLIENTS);
    }

    private static void placeLavalogged(GameTestHelper h, BlockPos rel) {
        placeIntake(h, rel, true);
    }

    /** Takes the lava out the way an empty bucket does (the block's own BucketPickup); returns what the bucket would get. */
    private static ItemStack emptyIntake(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        BlockState st = h.getLevel().getBlockState(abs);
        return ((LavaIntakeBlock) st.getBlock()).pickupBlock(null, h.getLevel(), abs, st);
    }

    /** Number of lava blocks (any fluid state of lava) in the whole 26x5x5 test area; {@code sourcesOnly} counts only sources. */
    private static int lavaBlocks(GameTestHelper h, boolean sourcesOnly) {
        int n = 0;
        for (int x = 0; x < 26; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z < 5; z++) {
            var f = h.getLevel().getFluidState(h.absolutePos(new BlockPos(x, y, z)));
            if (f.is(FluidTags.LAVA) && (!sourcesOnly || f.isSource())) n++;
        }
        return n;
    }

    /** 1-block stone basin at {@code floorTop} (the cell itself stays free); the floor is already stone. */
    private static void basin(GameTestHelper h, BlockPos cell) {
        for (Direction d : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            h.setBlock(cell.relative(d), Blocks.STONE);
        }
    }

    /** Forge (+ lit by a filled Lava Intake when {@code lit}), Crucible facing west on top, Mold in front. */
    private static void kit(GameTestHelper h, boolean lit) {
        clear(h);
        h.setBlock(FORGE, forgeState());
        if (lit) {
            placeLavalogged(h, INTAKE);
        }
        recompute(h, FORGE);
        h.setBlock(CRUC, ModBlocks.CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.FACING, Direction.WEST));
        h.setBlock(MOLD, ModBlocks.MOLD.get());
    }

    private static boolean lit(GameTestHelper h, BlockPos forge) {
        return h.getBlockState(forge).getValue(ForgeBlock.LIT);
    }

    private static CrucibleBlockEntity cru(GameTestHelper h) {
        return (CrucibleBlockEntity) h.getBlockEntity(CRUC);
    }

    private static MoldBlockEntity mold(GameTestHelper h) {
        return (MoldBlockEntity) h.getBlockEntity(MOLD);
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
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
        throw new IllegalStateException("inventory full");
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static ItemStack find(ServerPlayer p, Item item) {
        for (ItemStack s : p.getInventory().items) if (s.is(item)) return s;
        return ItemStack.EMPTY;
    }

    /** Real click path (ServerPlayerGameMode.useItemOn) with {@code held} in the main hand. */
    private static void use(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held, boolean sneak) {
        if (held.isEmpty() && !p.getMainHandItem().isEmpty()) {
            ItemStack old = p.getMainHandItem().copy();
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            give(p, old);
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(sneak);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        p.setShiftKeyDown(false);
    }

    private static int itemEntities(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    // ------------------------------------------------------------------------------------------------
    // Lava power
    // ------------------------------------------------------------------------------------------------

    /** Filled intake -> 3 lava pipes -> the forge's back port: lit + pipes glow; emptying the intake turns everything off. */
    @GameTest(template = "empty_long")
    public static void lavaNetworkPowersForge(GameTestHelper h) {
        clear(h);
        BlockPos forge = new BlockPos(10, 1, 2);
        h.setBlock(forge, forgeState());
        for (int x = 11; x <= 13; x++) place(h, new BlockPos(x, 1, 2), ModBlocks.LAVA_PIPE.get().defaultBlockState());
        placeLavalogged(h, new BlockPos(14, 1, 2));
        for (int x = 11; x <= 13; x++) place(h, new BlockPos(x, 1, 2), h.getBlockState(new BlockPos(x, 1, 2)));
        recompute(h, new BlockPos(14, 1, 2));
        check(h, lit(h, forge), "forge lit by the lava network");
        check(h, h.getBlockState(new BlockPos(12, 1, 2)).getValue(WaterPipeBlock.FILLED), "lava pipes show the glow");
        check(h, h.getBlockState(new BlockPos(11, 1, 2)).getValue(PipeBlock.WEST), "pipe connects to the forge's back port");
        check(h, h.getBlockState(new BlockPos(14, 1, 2)).getValue(PipeBlock.WEST), "intake collar towards the pipe");
        check(h, emptyIntake(h, new BlockPos(14, 1, 2)).is(Items.LAVA_BUCKET), "an empty bucket gets the lava");
        recompute(h, new BlockPos(14, 1, 2));
        check(h, !h.getBlockState(new BlockPos(14, 1, 2)).getValue(LavaIntakeBlock.LAVALOGGED), "emptied intake is inactive");
        check(h, !lit(h, forge), "forge unlit without lava");
        check(h, !h.getBlockState(new BlockPos(12, 1, 2)).getValue(WaterPipeBlock.FILLED), "pipes dark again");
        // the forge's FRONT is not a port
        clear(h);
        h.setBlock(forge, forgeState());
        placeLavalogged(h, new BlockPos(9, 1, 2));
        recompute(h, new BlockPos(9, 1, 2));
        check(h, !lit(h, forge), "an intake at the front does not light the forge");
        h.succeed();
    }

    /** Lava and water never connect: pipes, intakes, the forge port and the drill port all refuse the other fluid. */
    @GameTest(template = "empty_long")
    public static void lavaAndWaterNetworksAreSeparate(GameTestHelper h) {
        clear(h);
        place(h, new BlockPos(11, 1, 2), ModBlocks.LAVA_PIPE.get().defaultBlockState());
        place(h, new BlockPos(12, 1, 2), ModBlocks.WATER_PIPE.get().defaultBlockState());
        place(h, new BlockPos(11, 1, 2), h.getBlockState(new BlockPos(11, 1, 2)));
        check(h, !h.getBlockState(new BlockPos(11, 1, 2)).getValue(PipeBlock.EAST) && !h.getBlockState(new BlockPos(12, 1, 2)).getValue(PipeBlock.WEST),
                "lava pipe and water pipe don't join");
        // water intake behind a forge: unlit
        BlockPos f1 = new BlockPos(4, 1, 0);
        h.setBlock(f1, forgeState());
        place(h, new BlockPos(5, 1, 0), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        recompute(h, new BlockPos(5, 1, 0));
        check(h, !lit(h, f1), "a water intake can't light a forge");
        // lava intake -> water pipe -> forge port: unlit
        BlockPos f2 = new BlockPos(4, 1, 4);
        h.setBlock(f2, forgeState());
        place(h, new BlockPos(5, 1, 4), ModBlocks.WATER_PIPE.get().defaultBlockState());
        placeLavalogged(h, new BlockPos(6, 1, 4));
        recompute(h, new BlockPos(6, 1, 4));
        check(h, !lit(h, f2), "lava can't travel through a water pipe");
        check(h, !h.getBlockState(new BlockPos(5, 1, 4)).getValue(PipeBlock.EAST), "water pipe has no collar to the lava intake");
        // lava intake behind a drill on Dirtrock: no power
        BlockPos drill = new BlockPos(18, 1, 2);
        h.setBlock(new BlockPos(18, 0, 2), ModBlocks.DIRTROCK.get());
        h.setBlock(drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        placeLavalogged(h, new BlockPos(17, 1, 2));
        recompute(h, new BlockPos(17, 1, 2));
        check(h, !((MiningDrillBlockEntity) h.getBlockEntity(drill)).isWaterPowered(), "lava doesn't power a water machine");
        check(h, !h.getBlockState(new BlockPos(17, 1, 2)).getValue(PipeBlock.EAST), "lava intake has no collar to the drill's water port");
        h.succeed();
    }

    /**
     * Lava-logged intake in a stone basin next to a forge: lights it, the lava is never used up (still there after 10 s, no
     * lava anywhere else), an empty bucket (real click path) switches the forge off, a Lava Bucket switches it on again.
     */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void lavaIsNeverConsumedAndPowersOnlyWhileLavalogged(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos in = new BlockPos(8, 1, 2);
        basin(h, in);                                   // forge goes in the west wall below
        BlockPos forge = new BlockPos(7, 1, 2);
        h.setBlock(forge, forgeState());
        placeLavalogged(h, in);
        recompute(h, in);
        check(h, h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "lava-logged");
        check(h, h.getLevel().getFluidState(h.absolutePos(in)).isSourceOfType(Fluids.LAVA), "it holds a real lava source");
        check(h, lit(h, forge), "forge lit while the intake is lava-logged");
        h.runAfterDelay(200, () -> {
            check(h, h.getLevel().getFluidState(h.absolutePos(in)).isSourceOfType(Fluids.LAVA)
                    && h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "the lava is still there after 10 s (never consumed)");
            check(h, lit(h, forge), "still lit");
            check(h, lavaBlocks(h, false) == 1, "no other lava anywhere: " + lavaBlocks(h, false));
            use(h, p, in, new ItemStack(Items.BUCKET), false);          // empty bucket takes the lava out
            check(h, p.getMainHandItem().is(Items.LAVA_BUCKET), "got the Lava Bucket back");
            h.runAfterDelay(2, () -> {
                check(h, !h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "inactive without lava");
                check(h, !lit(h, forge), "forge unlit without lava (network recomputed at the end of the tick)");
                check(h, lavaBlocks(h, false) == 0, "the lava went into the bucket, none left in the world");
                use(h, p, in, p.getMainHandItem(), false);              // lava bucket back in
                h.runAfterDelay(2, () -> {
                    check(h, h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED) && lit(h, forge), "lava back in: active, forge lit");
                    h.succeed();
                });
            });
        });
    }

    /** Lava merely NEXT TO an intake (or above it) no longer activates it - only being lava-logged does. */
    @GameTest(template = "empty_long")
    public static void lavaNextToAnIntakeDoesNotActivateIt(GameTestHelper h) {
        clear(h);
        BlockPos lava = new BlockPos(8, 1, 2);
        basin(h, lava);
        h.setBlock(lava, Blocks.LAVA);
        BlockPos in = new BlockPos(8, 2, 2);                                 // sits on top of the lava
        place(h, in, ModBlocks.LAVA_INTAKE.get().defaultBlockState());
        BlockPos forge = new BlockPos(7, 2, 2);
        h.setBlock(forge, forgeState());
        recompute(h, in);
        check(h, !h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "an intake next to lava is not lava-logged");
        check(h, !LavaIntakeBlock.class.cast(h.getBlockState(in).getBlock()).isActiveIntake(h.getBlockState(in)), "and not active");
        check(h, !lit(h, forge), "the forge stays unlit");
        h.succeed();
    }

    /** Lava Bucket emptied onto an empty intake lava-logs it, an empty bucket takes it out: exactly one bucket in, one bucket out. */
    @GameTest(template = "empty_long")
    public static void bucketInAndOutGivesBackExactlyOneBucket(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos in = new BlockPos(8, 1, 2);
        basin(h, in);
        place(h, in, ModBlocks.LAVA_INTAKE.get().defaultBlockState());
        check(h, !h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "dry intake is inactive");
        use(h, p, in, new ItemStack(Items.LAVA_BUCKET), false);
        check(h, h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "lava-logged by the bucket");
        check(h, p.getMainHandItem().is(Items.BUCKET) && count(p, Items.LAVA_BUCKET) == 0 && count(p, Items.BUCKET) == 1,
                "the bucket is empty now: " + p.getMainHandItem());
        check(h, h.getLevel().getFluidState(h.absolutePos(in)).isSourceOfType(Fluids.LAVA), "real lava source in the block");
        // a second Lava Bucket on a lava-logged intake does nothing
        ItemStack second = new ItemStack(Items.LAVA_BUCKET);
        use(h, p, in, second, false);
        check(h, p.getMainHandItem().is(Items.LAVA_BUCKET) && p.getMainHandItem().getCount() == 1, "a lava-logged intake takes no second bucket");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        use(h, p, in, p.getMainHandItem(), false);
        check(h, !h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "the empty bucket took the lava out");
        check(h, p.getMainHandItem().is(Items.LAVA_BUCKET) && p.getMainHandItem().getCount() == 1 && count(p, Items.BUCKET) == 0,
                "exactly one Lava Bucket, no empty bucket left: " + p.getMainHandItem());
        check(h, h.getLevel().getFluidState(h.absolutePos(in)).isEmpty() && lavaBlocks(h, false) == 0, "no lava left in the world");
        use(h, p, in, p.getMainHandItem(), false);       // and an empty intake can be refilled
        check(h, h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED) && p.getMainHandItem().is(Items.BUCKET), "refilled");
        check(h, itemEntities(h, Items.LAVA_BUCKET) == 0 && itemEntities(h, Items.BUCKET) == 0, "no bucket dropped on the floor");
        h.succeed();
    }

    /** The BucketPickup / LiquidBlockContainer contract (dispensers, other mods): only SOURCE lava goes in, one bucket comes out. */
    @GameTest(template = "empty_long")
    public static void bucketInterfaceAcceptsOnlySourceLava(GameTestHelper h) {
        clear(h);
        BlockPos in = new BlockPos(8, 1, 2);
        basin(h, in);
        place(h, in, ModBlocks.LAVA_INTAKE.get().defaultBlockState());
        BlockPos abs = h.absolutePos(in);
        LavaIntakeBlock block = (LavaIntakeBlock) h.getBlockState(in).getBlock();
        BlockState st = h.getBlockState(in);
        check(h, block.canPlaceLiquid(null, h.getLevel(), abs, st, Fluids.LAVA), "source lava can be placed");
        check(h, !block.canPlaceLiquid(null, h.getLevel(), abs, st, Fluids.FLOWING_LAVA), "flowing lava can't");
        check(h, !block.canPlaceLiquid(null, h.getLevel(), abs, st, Fluids.WATER), "water can't");
        check(h, !block.placeLiquid(h.getLevel(), abs, st, Fluids.FLOWING_LAVA.getFlowing(3, false)), "placing flowing lava is refused");
        check(h, !h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "still dry");
        check(h, block.pickupBlock(null, h.getLevel(), abs, st).isEmpty(), "nothing to pick up while dry");
        check(h, block.placeLiquid(h.getLevel(), abs, st, Fluids.LAVA.getSource(false)) && h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "source lava lava-logs it");
        st = h.getBlockState(in);
        check(h, !block.canPlaceLiquid(null, h.getLevel(), abs, st, Fluids.LAVA)
                && !block.placeLiquid(h.getLevel(), abs, st, Fluids.LAVA.getSource(false)), "a lava-logged intake accepts no second source");
        ItemStack got = block.pickupBlock(null, h.getLevel(), abs, st);
        check(h, got.is(Items.LAVA_BUCKET) && got.getCount() == 1, "one Lava Bucket out");
        check(h, block.pickupBlock(null, h.getLevel(), abs, h.getBlockState(in)).isEmpty(), "and only one");
        check(h, block.getPickupSound().isPresent(), "pickup sound");
        h.succeed();
    }

    /** Breaking a LAVA-LOGGED intake: only the intake drops, the lava stays as a normal source block, never a bucket. */
    @GameTest(template = "empty_long")
    public static void breakingLavalogged_leavesLavaAndDropsNoBucket(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos in = new BlockPos(8, 1, 2);
        basin(h, in);
        place(h, in, ModBlocks.LAVA_INTAKE.get().defaultBlockState());
        use(h, p, in, new ItemStack(Items.LAVA_BUCKET), false);
        check(h, h.getBlockState(in).getValue(LavaIntakeBlock.LAVALOGGED), "lava-logged");
        BlockPos abs = h.absolutePos(in);
        p.gameMode.destroyBlock(abs);                                    // a survival player breaks it
        check(h, itemEntities(h, ModItems.LAVA_INTAKE.get()) == 1, "the intake drops");
        check(h, itemEntities(h, Items.LAVA_BUCKET) == 0 && itemEntities(h, Items.BUCKET) == 0, "no bucket drops");
        check(h, h.getBlockState(in).is(Blocks.LAVA) && h.getLevel().getFluidState(abs).isSourceOfType(Fluids.LAVA),
                "the lava stays as a normal lava source block: " + h.getBlockState(in));
        h.succeed();
    }

    /** The dropped intake lands in the lava it leaves behind and must survive it (fire resistant item). */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void droppedIntakeSurvivesTheLava(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos in = new BlockPos(8, 1, 2);
        basin(h, in);
        place(h, in, ModBlocks.LAVA_INTAKE.get().defaultBlockState());
        use(h, p, in, new ItemStack(Items.LAVA_BUCKET), false);
        p.gameMode.destroyBlock(h.absolutePos(in));
        h.runAfterDelay(100, () -> {
            check(h, itemEntities(h, ModItems.LAVA_INTAKE.get()) == 1, "the intake item is still there 5 s after landing in the lava: " + itemEntities(h, ModItems.LAVA_INTAKE.get()));
            check(h, itemEntities(h, Items.LAVA_BUCKET) == 0 && itemEntities(h, Items.BUCKET) == 0, "and still no bucket");
            h.succeed();
        });
    }

    /** Breaking an EMPTY intake: only the intake drops, nothing else (no lava, no bucket). */
    @GameTest(template = "empty_long")
    public static void breakingEmptyIntakeDropsNoBucket(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos in = new BlockPos(8, 1, 2);
        place(h, in, ModBlocks.LAVA_INTAKE.get().defaultBlockState());
        p.gameMode.destroyBlock(h.absolutePos(in));
        check(h, itemEntities(h, ModItems.LAVA_INTAKE.get()) == 1, "the intake drops");
        check(h, itemEntities(h, Items.LAVA_BUCKET) == 0 && itemEntities(h, Items.BUCKET) == 0, "no bucket drops");
        check(h, h.getBlockState(in).isAir() && lavaBlocks(h, false) == 0, "no lava appears: " + h.getBlockState(in));
        h.succeed();
    }

    /** The loot table drops the intake and never a bucket, whatever the state. */
    @GameTest(template = "empty_long")
    public static void lootTableNeverDropsABucket(GameTestHelper h) {
        for (boolean logged : new boolean[]{false, true}) {
            BlockState st = ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(LavaIntakeBlock.LAVALOGGED, logged);
            var drops = Block.getDrops(st, h.getLevel(), h.absolutePos(new BlockPos(8, 1, 2)), null);
            check(h, drops.size() == 1 && drops.get(0).is(ModItems.LAVA_INTAKE.get()) && drops.get(0).getCount() == 1,
                    "loot (lavalogged=" + logged + ") = just the intake: " + drops);
        }
        h.succeed();
    }

    /**
     * Placing an intake into a lava SOURCE (a bucket of lava in a 1-block basin) through the real click path keeps that lava
     * as a real source sharing the block (no free lava, none lost).
     */
    @GameTest(template = "empty_long")
    public static void intakePlacedIntoLavaSourceKeepsTheSource(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos lava = new BlockPos(8, 1, 2);
        for (BlockPos s : new BlockPos[]{new BlockPos(7, 1, 2), new BlockPos(9, 1, 2), new BlockPos(8, 1, 1), new BlockPos(8, 1, 3)}) {
            h.setBlock(s, Blocks.DIRT);
        }
        h.setBlock(lava, Blocks.LAVA);
        check(h, h.getLevel().getFluidState(h.absolutePos(lava)).isSourceOfType(Fluids.LAVA), "lava in the basin");
        use(h, p, new BlockPos(8, 0, 2), new ItemStack(ModItems.LAVA_INTAKE.get()), false);
        BlockState st = h.getBlockState(lava);
        check(h, st.is(ModBlocks.LAVA_INTAKE.get()) && st.getValue(LavaIntakeBlock.LAVALOGGED), "intake placed into the lava is lava-logged: " + st);
        check(h, h.getLevel().getFluidState(h.absolutePos(lava)).isSourceOfType(Fluids.LAVA), "the lava is still a source in that block");
        check(h, lavaBlocks(h, true) == 1 && lavaBlocks(h, false) == 1, "exactly one lava source, nothing duplicated");
        h.succeed();
    }

    /**
     * Flowing lava never lava-logs the intake or creates a source: placed INTO flowing lava the intake just replaces it
     * (inactive); flowing lava running up to an empty intake does not enter it. Lava is never duplicated.
     */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void flowingLavaDoesNotLavalogOrCreateASource(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        // 1) placed into flowing lava -> replaces it, inactive, no source
        BlockPos spot = new BlockPos(3, 1, 2);
        h.setBlock(spot, Fluids.FLOWING_LAVA.getFlowing(2, false).createLegacyBlock());
        check(h, h.getLevel().getFluidState(h.absolutePos(spot)).is(FluidTags.LAVA) && !h.getLevel().getFluidState(h.absolutePos(spot)).isSource(),
                "flowing lava set up");
        use(h, p, new BlockPos(3, 0, 2), new ItemStack(ModItems.LAVA_INTAKE.get()), false);
        BlockState st = h.getBlockState(spot);
        check(h, st.is(ModBlocks.LAVA_INTAKE.get()) && !st.getValue(LavaIntakeBlock.LAVALOGGED), "intake in flowing lava is NOT lava-logged: " + st);
        check(h, h.getLevel().getFluidState(h.absolutePos(spot)).isEmpty(), "no fluid in the intake block (no source created)");
        // 2) a lava source in a stone corridor flows up to an empty intake: it must not enter it
        for (int x = 12; x <= 17; x++) {
            h.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
            h.setBlock(new BlockPos(x, 1, 3), Blocks.STONE);
        }
        h.setBlock(new BlockPos(11, 1, 2), Blocks.STONE);
        h.setBlock(new BlockPos(18, 1, 2), Blocks.STONE);
        BlockPos intake = new BlockPos(13, 1, 2);
        place(h, intake, ModBlocks.LAVA_INTAKE.get().defaultBlockState());
        BlockPos source = new BlockPos(16, 1, 2);
        h.setBlock(source, Blocks.LAVA);
        h.runAfterDelay(200, () -> {
            check(h, h.getLevel().getFluidState(h.absolutePos(new BlockPos(14, 1, 2))).is(FluidTags.LAVA)
                    && !h.getLevel().getFluidState(h.absolutePos(new BlockPos(14, 1, 2))).isSource(), "lava did flow up next to the intake");
            BlockState now = h.getBlockState(intake);
            check(h, now.is(ModBlocks.LAVA_INTAKE.get()) && !now.getValue(LavaIntakeBlock.LAVALOGGED), "flowing lava did not lava-log the intake: " + now);
            check(h, h.getLevel().getFluidState(h.absolutePos(intake)).isEmpty(), "and left no fluid inside it");
            check(h, lavaBlocks(h, true) == 1, "still exactly one lava source (no duplication): " + lavaBlocks(h, true));
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Crucible input
    // ------------------------------------------------------------------------------------------------

    /** Meltables only, one metal at a time, 200 g cap (as many items as fit by grams). */
    @GameTest(template = "empty_long")
    public static void crucibleAcceptsMeltablesOnly(GameTestHelper h) {
        kit(h, false);
        ServerPlayer p = player(h);
        use(h, p, CRUC, new ItemStack(ModItems.RUBY_LARGE.get(), 2), false);
        check(h, p.getMainHandItem().getCount() == 2 && cru(h).total() == 0, "rubies refused");
        use(h, p, CRUC, new ItemStack(Items.DIRT, 5), false);
        check(h, p.getMainHandItem().getCount() == 5 && cru(h).total() == 0, "dirt refused");
        use(h, p, CRUC, new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 3), false);
        check(h, cru(h).unmelted() == 36 && FOLKIRON.equals(cru(h).metal()) && p.getMainHandItem().isEmpty(), "3 large Folkiron = 36 g");
        check(h, h.getBlockState(CRUC).getValue(CrucibleBlock.CHUNKS) && h.getBlockState(CRUC).getValue(CrucibleBlock.METAL) == MoltenMetal.FOLKIRON,
                "chunks visible, folkiron colour");
        use(h, p, CRUC, new ItemStack(ModItems.GOLDCHRO_CHUNK_SMALL.get(), 4), false);
        check(h, p.getMainHandItem().getCount() == 4 && cru(h).total() == 36, "Goldchro refused while Folkiron is in");
        use(h, p, CRUC, new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 20), false);
        check(h, cru(h).total() == 36 + 13 * 12 && p.getMainHandItem().getCount() == 7, "13 more large fit (192 g), 7 stay: " + cru(h).total());
        use(h, p, CRUC, new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 1), false);
        use(h, p, CRUC, new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 2), false);
        check(h, cru(h).total() == 199 && p.getMainHandItem().getCount() == 1, "medium + one small = 199 g, the second small doesn't fit");
        check(h, cru(h).total() <= CrucibleBlockEntity.CAPACITY, "never above 200 g");
        h.succeed();
    }

    /** Sneak-right-click adds every item of the crucible's metal; other metals and gems stay. Thrown chunks go in. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void crucibleSneakAndThrown(GameTestHelper h) {
        kit(h, false);
        ServerPlayer p = player(h);
        give(p, new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 5));
        give(p, new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 2));
        give(p, new ItemStack(ModItems.GOLDCHRO_CHUNK_SMALL.get(), 3));
        give(p, new ItemStack(ModItems.RUBY_SMALL.get(), 1));
        use(h, p, CRUC, new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 1), true);  // sneak with a Folkiron chunk in hand
        check(h, cru(h).unmelted() == 12 + 10 + 10, "all Folkiron moved in (held + inventory): " + cru(h).unmelted());
        check(h, count(p, ModItems.GOLDCHRO_CHUNK_SMALL.get()) == 3 && count(p, ModItems.RUBY_SMALL.get()) == 1, "Goldchro and Ruby stay");
        // thrown items
        BlockPos abs = h.absolutePos(CRUC);
        ItemEntity e = new ItemEntity(h.getLevel(), abs.getX() + 0.5, abs.getY() + 0.6, abs.getZ() + 0.5,
                new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 2), 0, 0, 0);
        h.getLevel().addFreshEntity(e);
        ItemEntity r = new ItemEntity(h.getLevel(), abs.getX() + 0.5, abs.getY() + 0.6, abs.getZ() + 0.5,
                new ItemStack(ModItems.RUBY_SMALL.get(), 1), 0, 0, 0);
        h.getLevel().addFreshEntity(r);
        h.runAfterDelay(30, () -> {
            check(h, cru(h).unmelted() == 32 + 24, "thrown chunks went in: " + cru(h).unmelted());
            check(h, r.isAlive(), "a thrown ruby stays an item");
            h.succeed();
        });
    }

    /** A hopper above inserts chunks; the item handler refuses gems and never gives anything out. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void hopperInsertsButNothingComesOut(GameTestHelper h) {
        kit(h, false);
        BlockPos hop = CRUC.above();
        h.setBlock(hop, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity hopper = (HopperBlockEntity) h.getBlockEntity(hop);
        hopper.setItem(0, new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 4));
        hopper.setItem(1, new ItemStack(ModItems.RUBY_SMALL.get(), 1));
        IItemHandler cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(CRUC), Direction.UP);
        check(h, cap != null, "crucible has an item handler");
        check(h, cap.insertItem(0, new ItemStack(ModItems.RUBY_LARGE.get()), false).getCount() == 1, "handler refuses rubies");
        check(h, !cap.isItemValid(0, new ItemStack(Items.IRON_INGOT)), "handler: vanilla items invalid");
        h.runAfterDelay(60, () -> {
            check(h, cru(h).unmelted() == 48, "hopper fed 4 large Folkiron (48 g): " + cru(h).unmelted());
            check(h, hopper.getItem(1).is(ModItems.RUBY_SMALL.get()), "the ruby stays in the hopper");
            check(h, cap.extractItem(0, 64, false).isEmpty() && cap.getStackInSlot(0).isEmpty(), "nothing can be extracted");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Melting
    // ------------------------------------------------------------------------------------------------

    /** Nothing melts on an unlit forge; lit, 1 g every 4 ticks (5 g/s); the ticker goes away when done. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void meltsOnlyWhileLitAtTheRightRate(GameTestHelper h) {
        kit(h, false);
        ServerPlayer p = player(h);
        use(h, p, CRUC, new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 4), false); // 20 g
        check(h, !h.getBlockState(CRUC).getValue(CrucibleBlock.HEATED), "unlit forge: not heated");
        check(h, ModBlocks.CRUCIBLE.get().getTicker(h.getLevel(), h.getBlockState(CRUC), ModBlockEntities.CRUCIBLE.get()) == null,
                "no ticker while not heated");
        h.runAfterDelay(60, () -> {
            check(h, cru(h).molten() == 0 && cru(h).unmelted() == 20, "nothing melted in 3 s on an unlit forge");
            placeLavalogged(h, INTAKE);
            recompute(h, INTAKE);
            check(h, lit(h, FORGE) && h.getBlockState(CRUC).getValue(CrucibleBlock.HEATED), "forge lit -> crucible heated");
            h.runAfterDelay(40, () -> {
                int m = cru(h).molten();
                check(h, m >= 9 && m <= 11, "about 10 g molten after 2 s: " + m);
                h.runAfterDelay(45, () -> {
                    check(h, cru(h).molten() == 20 && cru(h).unmelted() == 0, "all 20 g molten after ~4 s: " + cru(h).molten());
                    BlockState st = h.getBlockState(CRUC);
                    check(h, !st.getValue(CrucibleBlock.CHUNKS) && st.getValue(CrucibleBlock.FILL) == 1, "no chunks, fill step 1: " + st);
                    check(h, ModBlocks.CRUCIBLE.get().getTicker(h.getLevel(), st, ModBlockEntities.CRUCIBLE.get()) == null,
                            "no ticker once everything is molten");
                    // unlit again: added metal waits
                    emptyIntake(h, INTAKE);
                    recompute(h, INTAKE);
                    use(h, p, CRUC, new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 2), false);
                    h.runAfterDelay(40, () -> {
                        check(h, cru(h).molten() == 20 && cru(h).unmelted() == 10, "forge out -> melting pauses");
                        h.succeed();
                    });
                });
            });
        });
    }

    /** A full crucible (200 g) melts in 40 s (800 ticks). */
    @GameTest(template = "empty_long", timeoutTicks = 900)
    public static void fullCrucibleMeltsInFortySeconds(GameTestHelper h) {
        kit(h, true);
        cru(h).setContents(GOLDCHRO, 200, 0);
        h.runAfterDelay(790, () -> {
            check(h, cru(h).unmelted() > 0, "not done before 40 s: " + cru(h).unmelted());
            h.runAfterDelay(15, () -> {
                check(h, cru(h).molten() == 200 && cru(h).unmelted() == 0, "200 g molten after ~40 s: molten " + cru(h).molten() + " unmelted " + cru(h).unmelted()
                        + " " + h.getBlockState(CRUC) + " forge " + h.getBlockState(FORGE));
                check(h, h.getBlockState(CRUC).getValue(CrucibleBlock.FILL) == 4
                        && h.getBlockState(CRUC).getValue(CrucibleBlock.METAL) == MoltenMetal.GOLDCHRO, "full, gold colour");
                h.succeed();
            });
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Pouring / mold
    // ------------------------------------------------------------------------------------------------

    /** Empty-hand click pours the mold's target; a full mold refuses; a redstone rising edge pours too (once per edge). */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void pourByClickAndByRedstone(GameTestHelper h) {
        kit(h, false);
        ServerPlayer p = player(h);
        cru(h).setContents(FOLKIRON, 0, 120);
        use(h, p, CRUC, ItemStack.EMPTY, false);
        check(h, mold(h).grams() == 50 && cru(h).molten() == 70, "click poured 50 g: mold " + mold(h).grams() + ", crucible " + cru(h).molten());
        check(h, h.getBlockState(CRUC).getValue(CrucibleBlock.TIPPING), "tip animation");
        check(h, h.getBlockState(MOLD).getValue(MoldBlock.LEVEL) == 3 && !h.getBlockState(MOLD).getValue(MoldBlock.SOLID), "mold full + hot");
        use(h, p, CRUC, ItemStack.EMPTY, false);
        check(h, cru(h).molten() == 70, "full mold: nothing poured");
        h.runAfterDelay(CrucibleBlock.TIP_TICKS + 1, () -> check(h, !h.getBlockState(CRUC).getValue(CrucibleBlock.TIPPING), "tip animation ends"));
        h.runAfterDelay(MoldBlock.COOL_TICKS + 2, () -> {
            MoldBlock.click(h.getLevel(), h.absolutePos(MOLD), p, false);
            check(h, IngotItem.grams(find(p, ModItems.FOLKIRON_INGOT.get())) == 50, "took a 50 g ingot");
            // redstone: one pour per rising edge
            h.setBlock(new BlockPos(4, 2, 1), Blocks.REDSTONE_BLOCK);
            check(h, mold(h).grams() == 50 && cru(h).molten() == 20, "redstone poured 50 g: " + cru(h).molten());
            h.setBlock(new BlockPos(4, 2, 3), Blocks.REDSTONE_BLOCK);   // still powered: no new edge
            check(h, cru(h).molten() == 20, "no second pour while still powered");
            h.runAfterDelay(MoldBlock.COOL_TICKS + 2, () -> {
                MoldBlock.click(h.getLevel(), h.absolutePos(MOLD), p, false);
                h.setBlock(new BlockPos(4, 2, 1), Blocks.AIR);
                h.setBlock(new BlockPos(4, 2, 3), Blocks.AIR);
                h.setBlock(new BlockPos(4, 2, 1), Blocks.REDSTONE_BLOCK);
                check(h, mold(h).grams() == 20 && cru(h).molten() == 0, "second edge pours the last 20 g (less than the target)");
                check(h, h.getBlockState(MOLD).getValue(MoldBlock.LEVEL) == 1, "mold under half full");
                check(h, cru(h).metal() == null && h.getBlockState(CRUC).getValue(CrucibleBlock.METAL) == MoltenMetal.NONE, "empty crucible forgets its metal");
                h.succeed();
            });
        });
    }

    /** Empty hand cycles 10/25/50/75/100/150/200 (sneak backwards); a mold with metal keeps its target. */
    @GameTest(template = "empty_long")
    public static void moldTargetCycles(GameTestHelper h) {
        kit(h, false);
        ServerPlayer p = player(h);
        check(h, mold(h).target() == 50, "default 50 g");
        int[] expected = {75, 100, 150, 200, 10, 25, 50};
        for (int e : expected) {
            use(h, p, MOLD, ItemStack.EMPTY, false);
            check(h, mold(h).target() == e, "forward to " + e + ", got " + mold(h).target());
        }
        use(h, p, MOLD, ItemStack.EMPTY, true);
        check(h, mold(h).target() == 25, "sneak goes back to 25");
        use(h, p, MOLD, ItemStack.EMPTY, true);
        use(h, p, MOLD, ItemStack.EMPTY, true);
        check(h, mold(h).target() == 200, "back past 10 wraps to 200");
        cru(h).setContents(FOLKIRON, 0, 30);
        CrucibleBlock.pour(h.getLevel(), h.absolutePos(CRUC), h.getLevel().getBlockState(h.absolutePos(CRUC)), null);
        use(h, p, MOLD, ItemStack.EMPTY, false);
        check(h, mold(h).target() == 200 && mold(h).grams() == 30, "a mold with metal doesn't change its target");
        h.succeed();
    }

    /** Partial pours and top-ups up to the target; another metal is refused. */
    @GameTest(template = "empty_long")
    public static void partialPoursAndTopUps(GameTestHelper h) {
        kit(h, false);
        ServerPlayer p = player(h);
        use(h, p, MOLD, ItemStack.EMPTY, false);
        use(h, p, MOLD, ItemStack.EMPTY, false);   // 50 -> 75 -> 100
        check(h, mold(h).target() == 100, "target 100");
        cru(h).setContents(FOLKIRON, 0, 30);
        use(h, p, CRUC, ItemStack.EMPTY, false);
        check(h, mold(h).grams() == 30 && h.getBlockState(MOLD).getValue(MoldBlock.LEVEL) == 1, "30 of 100");
        cru(h).setContents(FOLKIRON, 0, 40);
        use(h, p, CRUC, ItemStack.EMPTY, false);
        check(h, mold(h).grams() == 70 && h.getBlockState(MOLD).getValue(MoldBlock.LEVEL) == 2, "topped up to 70");
        cru(h).setContents(FOLKIRON, 0, 50);
        use(h, p, CRUC, ItemStack.EMPTY, false);
        check(h, mold(h).grams() == 100 && cru(h).molten() == 20, "only the missing 30 g poured, 20 stay: " + cru(h).molten());
        check(h, h.getBlockState(MOLD).getValue(MoldBlock.LEVEL) == 3, "level full");
        // another metal into a Folkiron mold
        BlockPos abs = h.absolutePos(MOLD);
        h.setBlock(MOLD, Blocks.AIR);
        h.setBlock(MOLD, ModBlocks.MOLD.get());
        cru(h).setContents(FOLKIRON, 0, 10);
        use(h, p, CRUC, ItemStack.EMPTY, false);
        cru(h).setContents(GOLDCHRO, 0, 40);
        use(h, p, CRUC, ItemStack.EMPTY, false);
        check(h, mold(h).grams() == 10 && FOLKIRON.equals(mold(h).metal()) && cru(h).molten() == 40, "Goldchro refused by a Folkiron mold");
        h.succeed();
    }

    /** Hot metal can't be taken; after 3 s it is solid and gives an ingot with the exact weight; the target stays. */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void coolingThenIngotWithExactWeight(GameTestHelper h) {
        kit(h, false);
        ServerPlayer p = player(h);
        use(h, p, MOLD, ItemStack.EMPTY, true);   // 50 -> 25
        use(h, p, MOLD, ItemStack.EMPTY, false);  // back to 50
        cru(h).setContents(GOLDCHRO, 0, 80);
        use(h, p, CRUC, ItemStack.EMPTY, false);
        use(h, p, MOLD, ItemStack.EMPTY, false);
        check(h, count(p, ModItems.GOLDCHRO_INGOT.get()) == 0 && mold(h).grams() == 50 && mold(h).target() == 50, "still hot: nothing taken, target kept");
        h.runAfterDelay(30, () -> check(h, !h.getBlockState(MOLD).getValue(MoldBlock.SOLID), "still hot after 1.5 s"));
        h.runAfterDelay(MoldBlock.COOL_TICKS + 2, () -> {
            check(h, h.getBlockState(MOLD).getValue(MoldBlock.SOLID), "solid after 3 s");
            use(h, p, MOLD, ItemStack.EMPTY, false);
            ItemStack ingot = find(p, ModItems.GOLDCHRO_INGOT.get());
            check(h, ingot.getCount() == 1 && IngotItem.grams(ingot) == 50, "Goldchro Ingot of exactly 50 g: " + ingot);
            check(h, mold(h).isEmpty() && mold(h).target() == 50 && h.getBlockState(MOLD).getValue(MoldBlock.LEVEL) == 0, "mold empty, target kept");
            h.succeed();
        });
    }

    /** Breaking a mold with metal in it drops the ingot. */
    @GameTest(template = "empty_long")
    public static void breakingMoldDropsIngot(GameTestHelper h) {
        kit(h, false);
        cru(h).setContents(FOLKIRON, 0, 25);
        CrucibleBlock.pour(h.getLevel(), h.absolutePos(CRUC), h.getLevel().getBlockState(h.absolutePos(CRUC)), null);
        h.getLevel().destroyBlock(h.absolutePos(MOLD), true);
        ItemStack drop = h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(ModItems.FOLKIRON_INGOT.get()))
                .findFirst().orElse(ItemStack.EMPTY);
        check(h, IngotItem.grams(drop) == 25, "dropped a 25 g ingot: " + drop);
        check(h, itemEntities(h, ModItems.MOLD.get()) == 1, "and the mold itself");
        h.succeed();
    }

    /** Pouring needs a mold in front (at the crucible's level or one lower) and molten metal. */
    @GameTest(template = "empty_long")
    public static void pourNeedsAMoldInFront(GameTestHelper h) {
        kit(h, false);
        h.setBlock(MOLD, Blocks.AIR);
        cru(h).setContents(FOLKIRON, 0, 60);
        check(h, CrucibleBlock.pour(h.getLevel(), h.absolutePos(CRUC), h.getBlockState(CRUC), null) == 0, "no mold: nothing poured");
        h.setBlock(new BlockPos(5, 1, 2), ModBlocks.MOLD.get());   // behind: not used
        check(h, CrucibleBlock.pour(h.getLevel(), h.absolutePos(CRUC), h.getBlockState(CRUC), null) == 0, "a mold behind isn't used");
        h.setBlock(new BlockPos(3, 2, 2), ModBlocks.MOLD.get());   // in front, same level
        check(h, CrucibleBlock.pour(h.getLevel(), h.absolutePos(CRUC), h.getBlockState(CRUC), null) == 50, "mold in front at the same level works");
        cru(h).setContents(FOLKIRON, 30, 0);
        h.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
        h.setBlock(MOLD, ModBlocks.MOLD.get());
        check(h, CrucibleBlock.pour(h.getLevel(), h.absolutePos(CRUC), h.getBlockState(CRUC), null) == 0, "nothing molten yet: nothing poured");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Ingots / economy / shop
    // ------------------------------------------------------------------------------------------------

    /** Same weight stacks, different weights don't; names carry the weight; price per gram = ingot price; save round trip. */
    @GameTest(template = "empty_long")
    public static void ingotsStackByWeightAndPricePerGram(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        ItemStack a = IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 50), b = IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 50),
                c = IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 75);
        check(h, ItemStack.isSameItemSameComponents(a, b), "same weight stacks");
        check(h, !ItemStack.isSameItemSameComponents(a, c), "different weights don't");
        check(h, ItemStack.isSameItemSameComponents(new ItemStack(ModItems.FOLKIRON_INGOT.get()), IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 10)),
                "a plain ingot is 10 g and stacks with a 10 g one");
        check(h, a.getHoverName().getString().contains("50 g"), "name has the weight: " + a.getHoverName().getString());
        check(h, Selling.grams(c) == 75, "grams from the component");
        check(h, Selling.unitValue(a, reg) == 312, "50 g Folkiron ingot = floor(50 x 6.25) = 312: " + Selling.unitValue(a, reg));
        check(h, Selling.unitValue(IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), 50), reg) == 1000, "50 g Goldchro ingot = 1000");
        check(h, Selling.unitValue(IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 200), reg) == 1250, "200 g Folkiron = 1250");
        check(h, Selling.unitValue(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get()), reg) == 60, "raw stays 5/g (12 g = 60)");
        ItemStack back = ItemStack.parse(reg, c.copyWithCount(3).save(reg)).orElseThrow();
        check(h, ItemStack.matches(back, c.copyWithCount(3)), "save round trip keeps the weight");
        check(h, IngotItem.grams(back) == 75, "weight 75 after load");
        h.succeed();
    }

    /** The Sell Pad + Shop Button pay ingot_copper_per_gram x grams for each ingot. */
    @GameTest(template = "empty_long")
    public static void sellPadPaysIngotPricePerGram(GameTestHelper h) {
        clear(h);
        BlockPos btn = new BlockPos(4, 1, 2), sell = new BlockPos(3, 1, 2), pan = new BlockPos(4, 1, 3);
        h.setBlock(btn, ModBlocks.SHOP_BUTTON.get());
        h.setBlock(sell, ModBlocks.SELL_PAD.get());
        h.setBlock(pan, ModBlocks.MONEY_PAN.get());
        ServerPlayer p = player(h);
        use(h, p, sell, IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 50).copyWithCount(2), false);
        use(h, p, sell, IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), 25), false);
        use(h, p, sell, IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 10), false);
        PadBlockEntity pad = (PadBlockEntity) h.getBlockEntity(sell);
        check(h, pad.contents().size() == 3, "50 g and 10 g Folkiron ingots are separate stacks on the pad: " + pad.contents());
        use(h, p, btn, ItemStack.EMPTY, false);
        long balance = ((MoneyPanBlockEntity) h.getBlockEntity(pan)).balance();
        check(h, balance == 2 * 312 + 500 + 62, "paid 2 x 312 + 500 + 62 = 1186, got " + balance);
        h.succeed();
    }

    /** Ingots can go back into a crucible (same grams, so no gain); other metals and too-heavy ingots are refused. */
    @GameTest(template = "empty_long")
    public static void ingotsCanBeRemeltedAtNoGain(GameTestHelper h) {
        kit(h, false);
        ServerPlayer p = player(h);
        use(h, p, CRUC, IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 50).copyWithCount(2), false);
        check(h, cru(h).unmelted() == 100 && p.getMainHandItem().isEmpty(), "two 50 g ingots = 100 g");
        use(h, p, CRUC, IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 150), false);
        check(h, cru(h).unmelted() == 100 && p.getMainHandItem().getCount() == 1, "a 150 g ingot doesn't fit into 100 g of room");
        use(h, p, CRUC, IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), 10), false);
        check(h, cru(h).unmelted() == 100, "Goldchro ingot refused");
        h.succeed();
    }

    /** {"type":"holeydigsite:min_weight", ...} parses and matches only heavy-enough ingots of that item. */
    @GameTest(template = "empty_long")
    public static void minWeightIngredient(GameTestHelper h) {
        var json = JsonParser.parseString("{\"type\":\"holeydigsite:min_weight\",\"item\":\"holeydigsite:folkiron_ingot\",\"min_grams\":50}");
        Ingredient ing = Ingredient.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        check(h, ing.test(IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 50)), "50 g matches");
        check(h, ing.test(IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 200)), "200 g matches");
        check(h, !ing.test(IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 49)), "49 g doesn't");
        check(h, !ing.test(IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), 100)), "another metal doesn't");
        check(h, ing.getItems().length == 1 && IngotItem.grams(ing.getItems()[0]) == 50, "shows a 50 g ingot");
        h.succeed();
    }

    /** The six new catalogue entries, and buying a Forge through Buy Pad + button. */
    @GameTest(template = "empty_long")
    public static void shopSellsTheForgeKit(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        Object[][] expected = {{ModItems.FORGE.get(), 1536L}, {ModItems.CRUCIBLE.get(), 1024L}, {ModItems.MOLD.get(), 256L},
                {ModItems.LAVA_INTAKE.get(), 512L}, {ModItems.LAVA_PIPE.get(), 16L}, {Items.LAVA_BUCKET, 1024L}};
        for (Object[] e : expected) {
            var entry = ShopCatalogue.find(reg, (Item) e[0]);
            check(h, entry.isPresent() && entry.get().price() == (long) e[1] && entry.get().count() == 1, e[0] + " for " + e[1]);
        }
        clear(h);
        BlockPos btn = new BlockPos(4, 1, 2), buy = new BlockPos(5, 1, 2), pan = new BlockPos(4, 1, 3);
        h.setBlock(btn, ModBlocks.SHOP_BUTTON.get());
        h.setBlock(buy, ModBlocks.BUY_PAD.get());
        h.setBlock(pan, ModBlocks.MONEY_PAN.get());
        ((MoneyPanBlockEntity) h.getBlockEntity(pan)).deposit(1536 + 1024);
        ServerPlayer p = player(h);
        use(h, p, buy, BlueprintItem.of(ModItems.FORGE.get(), 1), false);
        use(h, p, buy, BlueprintItem.of(Items.LAVA_BUCKET, 1), false);
        use(h, p, btn, ItemStack.EMPTY, false);
        check(h, count(p, ModItems.FORGE.get()) == 1 && count(p, Items.LAVA_BUCKET) == 1, "bought a Forge and a Lava Bucket");
        check(h, ((MoneyPanBlockEntity) h.getBlockEntity(pan)).balance() == 0, "paid exactly 24 + 16 gold");
        h.succeed();
    }
}
