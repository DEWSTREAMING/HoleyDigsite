package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.item.CoinItem;
import com.holeydigsite.panning.Panning;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sandbox-only GameTests for Phase 2 (not shipped): panning, the brush, coin recipes and the loot economy.
 *
 * <p>Panning is driven by really holding the brush: the test right-clicks the pan through the normal block
 * interaction (which calls startUsingItem), then calls {@code ServerPlayer#doTick()} once per game tick, which
 * runs the vanilla use-item loop and so {@code BrushItem#onUseTick}. The mock player is aimed at the pan with
 * {@code lookAt}, so the "looking at the pan" ray check is exercised too.</p>
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public class PhaseTwoTests {
    private static final BlockPos P = new BlockPos(2, 1, 2);
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    private static final BlockPos STAND = new BlockPos(2, 1, 0);

    /** Phase 7: panning only gives our own resources (no vanilla ores). */
    static Set<Item> lootItems() {
        return Set.of(ModItems.FOLKIRON_CHUNK_SMALL.get(), ModItems.FOLKIRON_CHUNK_MEDIUM.get(), ModItems.FOLKIRON_CHUNK_LARGE.get(),
                ModItems.GOLDCHRO_CHUNK_SMALL.get(), ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), ModItems.GOLDCHRO_CHUNK_LARGE.get(),
                ModItems.UNCUT_RUBY_SMALL.get(), ModItems.UNCUT_RUBY_MEDIUM.get(), ModItems.UNCUT_RUBY_LARGE.get()); // Phase 12: loot gives uncut gems
    }

    private static final int DIRT_PRICE = CoinItem.GOLD_VALUE;

    // ------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    /** Places a pan with the given state and a survival player holding a brush, looking at the pan. */
    private static ServerPlayer setUpPanner(GameTestHelper h, int fill, boolean waterlogged) {
        // clean 5x3x5 air box on a stone floor (keeps the line of sight to the pan clear)
        for (BlockPos b : BlockPos.betweenClosed(0, 0, 0, 4, 3, 4)) {
            h.setBlock(b, b.getY() == 0 ? Blocks.STONE : Blocks.AIR);
        }
        h.setBlock(FLOOR, Blocks.STONE);
        h.setBlock(STAND.below(), Blocks.STONE);
        h.setBlock(P, ModBlocks.PAN.get().defaultBlockState()
                .setValue(PanBlock.FILL, fill).setValue(PanBlock.WATERLOGGED, waterlogged));

        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 feet = Vec3.atBottomCenterOf(h.absolutePos(STAND));
        p.moveTo(feet.x, feet.y, feet.z);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BRUSH.get()));
        lookAtPan(h, p);
        return p;
    }

    private static Vec3 panTop(GameTestHelper h) {
        return Vec3.atBottomCenterOf(h.absolutePos(P)).add(0, 0.2, 0);
    }

    private static void lookAtPan(GameTestHelper h, ServerPlayer p) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, panTop(h));
    }

    /** Right-click the pan's top face, like the client would. */
    private static void rightClickPan(GameTestHelper h, ServerPlayer p) {
        BlockPos abs = h.absolutePos(P);
        h.useBlock(P, p, new BlockHitResult(panTop(h), Direction.UP, abs, false));
    }

    /** Runs n game ticks of the player (incl. the use-item loop). */
    private static void tick(ServerPlayer p, int n) {
        for (int i = 0; i < n; i++) {
            p.doTick();
        }
    }

    private static int fill(GameTestHelper h) {
        BlockState state = h.getBlockState(P);
        return state.getBlock() instanceof PanBlock ? state.getValue(PanBlock.FILL) : -1;
    }

    private static List<ItemEntity> itemsAroundPan(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(P)).inflate(4));
    }

    // ------------------------------------------------------------------------------------------------
    // Panning
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty")
    public static void panDryDoesNothing(GameTestHelper h) {
        ServerPlayer p = setUpPanner(h, 2, false);
        rightClickPan(h, p);
        check(h, !p.isUsingItem(), "a dry pan must not start panning");
        tick(p, Panning.PAN_CYCLE_TICKS + 5);
        check(h, fill(h) == 2, "dry pan fill must stay 2, was " + fill(h));
        check(h, itemsAroundPan(h).isEmpty(), "dry pan must not give loot");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void panEmptyDoesNothing(GameTestHelper h) {
        ServerPlayer p = setUpPanner(h, 0, true);
        rightClickPan(h, p);
        check(h, !p.isUsingItem(), "an empty pan must not start panning");
        tick(p, Panning.PAN_CYCLE_TICKS + 5);
        check(h, fill(h) == 0, "empty pan stays at 0");
        check(h, itemsAroundPan(h).isEmpty(), "empty pan must not give loot");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void panFullCycle(GameTestHelper h) {
        ServerPlayer p = setUpPanner(h, PanBlock.MAX_FILL, true);
        rightClickPan(h, p);
        check(h, p.isUsingItem(), "brush on a filled waterlogged pan should start panning");

        // first cycle: nothing before PAN_CYCLE_TICKS, exactly one scoop at PAN_CYCLE_TICKS
        tick(p, Panning.PAN_CYCLE_TICKS - 1);
        check(h, fill(h) == PanBlock.MAX_FILL, "no scoop before the cycle completes, fill " + fill(h));
        check(h, itemsAroundPan(h).isEmpty(), "no loot before the cycle completes");
        tick(p, 1);
        check(h, fill(h) == PanBlock.MAX_FILL - 1, "one scoop after " + Panning.PAN_CYCLE_TICKS + " ticks, fill " + fill(h));
        check(h, !itemsAroundPan(h).isEmpty(), "first scoop should pop out loot");
        check(h, p.isUsingItem(), "still panning while holding and the pan has dirt");

        // keep holding: the remaining scoops
        tick(p, Panning.PAN_CYCLE_TICKS * (PanBlock.MAX_FILL - 1));
        check(h, fill(h) == 0, "pan should end at fill 0, was " + fill(h));
        check(h, !p.isUsingItem(), "panning stops by itself when the pan is empty");

        List<ItemEntity> loot = itemsAroundPan(h);
        check(h, loot.size() >= PanBlock.MAX_FILL, "at least one stack per scoop, got " + loot.size());
        double rimY = h.absolutePos(P).getY() + 0.25;
        for (ItemEntity e : loot) {
            check(h, lootItems().contains(e.getItem().getItem()), "unexpected loot " + e.getItem());
            check(h, e.getY() >= rimY - 0.01, "loot should spawn on top of the pan, y=" + e.getY());
        }

        // a 5th cycle does nothing: holding on an empty pan doesn't restart
        tick(p, Panning.PAN_CYCLE_TICKS);
        check(h, itemsAroundPan(h).size() == loot.size(), "no loot from an empty pan");

        // the brush never wears out
        ItemStack brush = p.getMainHandItem();
        check(h, brush.is(ModItems.BRUSH.get()), "brush should still be in hand");
        check(h, !brush.isDamageableItem() && brush.getDamageValue() == 0, "brush must have no durability");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void panEarlyReleaseCancels(GameTestHelper h) {
        ServerPlayer p = setUpPanner(h, 2, true);
        rightClickPan(h, p);
        tick(p, Panning.PAN_CYCLE_TICKS - 10);
        p.releaseUsingItem(); // let go of right-click
        check(h, !p.isUsingItem(), "released");
        tick(p, Panning.PAN_CYCLE_TICKS);
        check(h, fill(h) == 2, "early release: fill unchanged, was " + fill(h));
        check(h, itemsAroundPan(h).isEmpty(), "early release: no loot");

        // starting again begins a fresh cycle (no carried-over progress)
        rightClickPan(h, p);
        tick(p, 15);
        check(h, fill(h) == 2, "progress must not carry over from the cancelled cycle");
        tick(p, Panning.PAN_CYCLE_TICKS - 15);
        check(h, fill(h) == 1, "a full new cycle scoops once, fill " + fill(h));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void panLookAwayCancels(GameTestHelper h) {
        ServerPlayer p = setUpPanner(h, 2, true);
        rightClickPan(h, p);
        tick(p, 20);
        check(h, p.isUsingItem(), "panning while looking at the pan");
        p.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition().add(0, 10, 0)); // look at the sky
        tick(p, 1);
        check(h, !p.isUsingItem(), "looking away cancels panning");
        lookAtPan(h, p);
        tick(p, Panning.PAN_CYCLE_TICKS);
        check(h, fill(h) == 2, "look-away cancel: fill unchanged, was " + fill(h));
        check(h, itemsAroundPan(h).isEmpty(), "look-away cancel: no loot");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void panSneakClickAlsoWorks(GameTestHelper h) {
        ServerPlayer p = setUpPanner(h, 1, true);
        // sneaking skips the block's useItemOn, so BrushItem#useOn must start panning
        ItemStack brush = p.getMainHandItem();
        brush.useOn(new net.minecraft.world.item.context.UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(panTop(h), Direction.UP, h.absolutePos(P), false)));
        check(h, p.isUsingItem(), "sneak right-click with the brush should also start panning");
        tick(p, Panning.PAN_CYCLE_TICKS);
        check(h, fill(h) == 0, "one scoop, fill 0");
        check(h, !p.isUsingItem(), "stops when empty");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Coins
    // ------------------------------------------------------------------------------------------------

    /** Fills the 3x3 grid (slots 1-9) of a crafting menu and returns the result slot's item. */
    private static ItemStack craftResult(CraftingMenu menu, ItemStack... grid) {
        for (int i = 0; i < 9; i++) {
            menu.getSlot(1 + i).set(i < grid.length ? grid[i] : ItemStack.EMPTY);
        }
        return menu.getSlot(0).getItem();
    }

    private static ItemStack[] coins(Item coin, int slots) {
        ItemStack[] grid = new ItemStack[slots];
        for (int i = 0; i < slots; i++) grid[i] = new ItemStack(coin);
        return grid;
    }

    @GameTest(template = "empty")
    public static void coinRecipes(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        CraftingMenu menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(P)));
        Item copper = ModItems.COPPER_COIN.get(), silver = ModItems.SILVER_COIN.get(), gold = ModItems.GOLD_COIN.get();

        check(h, new ItemStack(copper).getMaxStackSize() == 64 && new ItemStack(gold).getMaxStackSize() == 64, "coins stack to 64");

        ItemStack r = craftResult(menu, coins(copper, 8));
        check(h, r.is(silver) && r.getCount() == 1, "8 copper -> 1 silver, got " + r);
        r = craftResult(menu, coins(silver, 8));
        check(h, r.is(gold) && r.getCount() == 1, "8 silver -> 1 gold, got " + r);
        r = craftResult(menu, new ItemStack(silver));
        check(h, r.is(copper) && r.getCount() == 8, "1 silver -> 8 copper, got " + r);
        r = craftResult(menu, new ItemStack(gold));
        check(h, r.is(silver) && r.getCount() == 8, "1 gold -> 8 silver, got " + r);
        r = craftResult(menu, coins(copper, 7));
        check(h, r.isEmpty(), "7 copper make nothing, got " + r);
        r = craftResult(menu, new ItemStack(copper));
        check(h, r.isEmpty(), "copper can't be split, got " + r);
        r = craftResult(menu, new ItemStack(copper, 8));
        check(h, r.isEmpty(), "one stack of 8 copper in ONE slot is not a recipe, got " + r);

        // actually taking the result consumes the ingredients (8 slots x 1)
        craftResult(menu, coins(copper, 8));
        menu.clicked(0, 0, ClickType.PICKUP, p);
        check(h, menu.getCarried().is(silver) && menu.getCarried().getCount() == 1, "took 1 silver: " + menu.getCarried());
        for (int i = 1; i <= 9; i++) {
            check(h, menu.getSlot(i).getItem().isEmpty(), "grid slot " + i + " should be used up");
        }
        menu.setCarried(ItemStack.EMPTY);

        // value is conserved both ways
        check(h, ((CoinItem) silver).getValue() == 8 * ((CoinItem) copper).getValue(), "silver = 8 copper");
        check(h, ((CoinItem) gold).getValue() == 8 * ((CoinItem) silver).getValue(), "gold = 8 silver");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Economy (rolls the REAL loot table through the loot system)
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void panningEconomy(GameTestHelper h) {
        final int scoops = 20_000;
        BlockPos pos = h.absolutePos(P);
        long value = 0;
        int goldchroScoops = 0, rubyScoops = 0, stacks = 0;
        Set<Item> loot = lootItems();
        for (int i = 0; i < scoops; i++) {
            boolean goldchro = false, ruby = false;
            for (ItemStack s : Panning.rollScoop(h.getLevel(), pos, null, 1)) {
                check(h, loot.contains(s.getItem()), "unexpected panning loot " + s);
                long v = Selling.stackValue(s, h.getLevel().registryAccess());
                check(h, v > 0, "every find must be sellable: " + s);
                value += v;
                goldchro |= s.is(ModItems.GOLDCHRO_CHUNK_SMALL.get()) || s.is(ModItems.GOLDCHRO_CHUNK_MEDIUM.get()) || s.is(ModItems.GOLDCHRO_CHUNK_LARGE.get());
                ruby |= s.is(Panning.RARE_FINDS) && !s.is(ModItems.GOLDCHRO_CHUNK_LARGE.get());
                stacks++;
            }
            if (goldchro) goldchroScoops++;
            if (ruby) rubyScoops++;
        }
        double avg = (double) value / scoops;
        double pGoldchro = (double) goldchroScoops / scoops, pRuby = (double) rubyScoops / scoops;
        HoleyDigsite.LOGGER.info(String.format(
                "[economy] tier 1, %d scoops: avg %.2f copper/scoop (%.3fx dirt), Goldchro 1 in %.1f, Ruby 1 in %.1f, %.3f stacks/scoop",
                scoops, avg, avg / DIRT_PRICE, 1 / pGoldchro, 1 / pRuby, (double) stacks / scoops));

        check(h, avg >= 1.15 * DIRT_PRICE && avg <= 1.35 * DIRT_PRICE, "avg value per scoop " + avg + " outside 1.15-1.35x dirt");
        check(h, Math.abs(pGoldchro * 8 - 1) <= 0.2, "Goldchro should be ~1 in 8 scoops, was 1 in " + (1 / pGoldchro));
        check(h, Math.abs(pRuby * 40 - 1) <= 0.25, "Ruby should be ~1 in 40 scoops, was 1 in " + (1 / pRuby));
        h.succeed();
    }
}
