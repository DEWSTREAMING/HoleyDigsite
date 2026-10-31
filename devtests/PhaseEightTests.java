package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.ShopBlockItem;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.sellpad.SellPadBlock;
import com.holeydigsite.shop.AbstractPadBlock;
import com.holeydigsite.shop.MoneyPanBlock;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopButtonBlock;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.shop.ShopDisplayBlock;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.shop.ShopItem;
import com.holeydigsite.shop.ShopLock;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Sandbox-only GameTests for Phase 8 (not shipped): Sell Pad (holds, no instant selling), Money Pan, Buy Pad + blueprints,
 * Shop Button, Shop Display, the data-driven catalogue and the locked (unbreakable island) variants.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseEightTests {
    /** Shop layout in the test area: button in the middle, Sell Pad west, Buy Pad east, Money Pan south. */
    private static final BlockPos BTN = new BlockPos(4, 1, 2);
    private static final BlockPos SELL = new BlockPos(3, 1, 2);
    private static final BlockPos BUY = new BlockPos(5, 1, 2);
    private static final BlockPos PAN = new BlockPos(4, 1, 3);

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 9; x++) for (int y = 1; y <= 3; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 0; x < 9; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    /** Button with all three neighbours. */
    private static void shop(GameTestHelper h) {
        clear(h);
        h.setBlock(BTN, ModBlocks.SHOP_BUTTON.get());
        h.setBlock(SELL, ModBlocks.SELL_PAD.get());
        h.setBlock(BUY, ModBlocks.BUY_PAD.get());
        h.setBlock(PAN, ModBlocks.MONEY_PAN.get());
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    /** Puts a stack into the main inventory (slots 9+), never the hotbar slot the tests use as the "hand". */
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

    private static int entities(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** Counts dropped items around the player, wherever they stand. */
    private static int wide(GameTestHelper h, ServerPlayer p, Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16)).stream()
                .map(ItemEntity::getItem).filter(x -> x.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static PadBlockEntity pad(GameTestHelper h, BlockPos rel) {
        return (PadBlockEntity) h.getBlockEntity(rel);
    }

    private static MoneyPanBlockEntity pan(GameTestHelper h, BlockPos rel) {
        return (MoneyPanBlockEntity) h.getBlockEntity(rel);
    }

    private static long padItems(PadBlockEntity pad) {
        return pad.contents().stream().mapToLong(ItemStack::getCount).sum();
    }

    private static long coinsValue(ServerPlayer p) {
        long v = 0;
        for (ItemStack s : p.getInventory().items) v += MoneyPanBlock.coinValue(s);
        for (ItemStack s : p.getInventory().offhand) v += MoneyPanBlock.coinValue(s);
        return v;
    }

    /** The tests treat hotbar slot 0 as "the hand"; keep whatever is there when the hand should be empty. */
    private static void stash(ServerPlayer p, ItemStack held, InteractionHand hand) {
        if (held.isEmpty() && hand == InteractionHand.MAIN_HAND && !p.getMainHandItem().isEmpty()) {
            ItemStack old = p.getMainHandItem().copy();
            p.setItemInHand(hand, ItemStack.EMPTY);
            give(p, old);
        }
    }

    private static void press(GameTestHelper h, ServerPlayer p) {
        BlockState st = h.getBlockState(BTN);
        if (st.hasProperty(ShopButtonBlock.PRESSED) && st.getValue(ShopButtonBlock.PRESSED)) {
            h.setBlock(BTN, st.setValue(ShopButtonBlock.PRESSED, false)); // let the animation finish
        }
        use(h, p, BTN, ItemStack.EMPTY);
    }

    private static void sneakUse(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held, InteractionHand hand) {
        stash(p, held, hand);
        p.setItemInHand(hand, held);
        p.setShiftKeyDown(true);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getItemInHand(hand), hand, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        p.setShiftKeyDown(false);
    }

    private static void use(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held) {
        stash(p, held, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(false);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    private static void drop(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack stack, double dx) {
        Vec3 above = Vec3.atBottomCenterOf(h.absolutePos(rel)).add(dx, 0.6, 0);
        ItemEntity e = new ItemEntity(h.getLevel(), above.x, above.y, above.z, stack, 0, 0, 0);
        e.setThrower(p);
        h.getLevel().addFreshEntity(e);
    }

    private static ItemStack bp(Item target, int n) {
        return BlueprintItem.of(target, n);
    }

    // ------------------------------------------------------------------------------------------------
    // Catalogue / blueprint
    // ------------------------------------------------------------------------------------------------

    /** Every item the old trader sold + the new shop blocks is in the data-driven catalogue at the old prices. */
    @GameTest(template = "empty_long")
    public static void catalogueHasTheTradersItemsAndPrices(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        Object[][] expected = {
                {Items.DIRT, 1, 64L}, {ModItems.PAN.get(), 1, 16L}, {ModItems.BRUSH.get(), 1, 16L}, {Items.WOODEN_SHOVEL, 1, 16L},
                {ModItems.MINING_DRILL.get(), 1, 2048L}, {ModItems.WATER_INTAKE.get(), 1, 512L}, {ModItems.WATER_PIPE.get(), 1, 8L},
                {ModItems.DIRT_CLUMP.get(), 1, 80L}, {ModItems.CLUMP_BELT.get(), 1, 16L}, {ModItems.SLUICE_BOX.get(), 1, 3072L},
                {ModItems.SELL_PAD.get(), 1, 8L}, {ModItems.BUY_PAD.get(), 1, 8L}, {ModItems.MONEY_PAN.get(), 1, 8L}, {ModItems.SHOP_BUTTON.get(), 1, 8L},
                // Phase 9: the melting and casting kit
                {ModItems.FORGE.get(), 1, 1536L}, {ModItems.CRUCIBLE.get(), 1, 1024L}, {ModItems.MOLD.get(), 1, 256L},
                {ModItems.LAVA_INTAKE.get(), 1, 512L}, {ModItems.LAVA_PIPE.get(), 1, 16L}, {Items.LAVA_BUCKET, 1, 1024L},
                // Phase 10: the Crafting Forge (Tier 2 items are made in it, not sold)
                {ModItems.CRAFTING_FORGE.get(), 1, 2560L},
                // Phase 11: land deeds and the gravel / sand clumps
                {ModItems.LAND_DEED_DEEP_DIG.get(), 1, 400L * 64}, {ModItems.LAND_DEED_GRAVEL_FLATS.get(), 1, 2400L * 64},
                {ModItems.LAND_DEED_SAND_DUNES.get(), 1, 4500L * 64}, {ModItems.GRAVEL_CLUMP.get(), 1, 150L}, {ModItems.SAND_CLUMP.get(), 1, 210L},
                // Phase 12: Inclined Belt, Filter Belt, Gem Cutter
                {ModItems.INCLINE_BELT.get(), 1, 32L}, {ModItems.FILTER_BELT.get(), 1, 256L}, {ModItems.GEM_CUTTER.get(), 1, 2048L},
                {ModItems.BELT_SPLITTER.get(), 1, 256L},
                // Phase 16: plot-exclusive kiosk items
                {ModItems.PROSPECTORS_PICK.get(), 1, 2048L}, {ModItems.RIFFLE_INSERT.get(), 1, 11520L}, {ModItems.FINE_MESH_SCREEN.get(), 1, 15360L},
                {ModItems.MINERS_LANTERN.get(), 1, 24L}, {ModItems.RIVER_STONE_LAMP.get(), 1, 24L}, {ModItems.SANDSTONE_LAMP.get(), 1, 24L},
                // Phase 18: Seed Screen and the starter bread
                {ModItems.SEED_SCREEN.get(), 1, 1536L}, {net.minecraft.world.item.Items.BREAD, 1, 4L},
                // Phase 19: Smithing Anvil + Blacksmith Hammer
                {ModItems.SMITHING_ANVIL.get(), 1, 18432L}, {ModItems.BLACKSMITH_HAMMER.get(), 1, 2048L},
                // Phase 21b: Ledger blueprints + Prospector's Ledger
                {ModItems.CLAIM_FLAG.get(), 1, 16L}, {ModItems.ORE_CART.get(), 1, 32L},
                {ModItems.LUCKY_LANTERN.get(), 1, 24L}, {ModItems.PROSPECTORS_LEDGER.get(), 1, 1L},
                // Phase 21i: Down Incline + Drop Chute
                {ModItems.DOWN_INCLINE_BELT.get(), 1, 32L}, {ModItems.DROP_CHUTE.get(), 1, 24L},
                // Phase 22: Skin Bench
                {ModItems.SKIN_BENCH.get(), 1, 2048L},
                // Bolt Cutters (the crates left the shop in Phase 30)
                {ModItems.BOLT_CUTTERS.get(), 1, 1536L},
                // pick heads (kiosks)
                {ModItems.GRAVEL_HEAD.get(), 1, 2048L}, {ModItems.SAND_HEAD.get(), 1, 2560L},
                // Phase 28b: the event store (deeds in gold, the rest in Opal Chips, no coin price)
                {ModItems.LAND_DEED_EVENT_T1.get(), 1, 400L * 64}, {ModItems.LAND_DEED_EVENT_T2.get(), 1, 2000L * 64},
                {ModItems.LAND_DEED_EVENT_T3.get(), 1, 3600L * 64}, {ModItems.OUTBACK_CRATE.get(), 1, 0L},
                {ModItems.CORRUGATED_IRON_SHEET.get(), 1, 0L}, {ModItems.SOUTHERN_CROSS_BANNER.get(), 1, 0L},
                {ModItems.LITTLE_WINDMILL.get(), 1, 0L}, {ModItems.UTE_ORE_CART.get(), 1, 0L}, {ModItems.WELCOME_STRANGER_STAND.get(), 1, 0L}};
        for (Object[] e : expected) {
            var entry = ShopCatalogue.find(reg, (Item) e[0]);
            check(h, entry.isPresent(), "catalogue has " + e[0]);
            check(h, entry.get().count() == (int) e[1] && entry.get().price() == (long) e[2],
                    e[0] + ": " + entry.get().count() + "x for " + entry.get().price() + ", expected " + e[1] + "x for " + e[2]);
        }
        check(h, ShopCatalogue.all(reg).size() == expected.length, "exactly " + expected.length + " entries, got " + ShopCatalogue.all(reg).size());
        check(h, ShopCatalogue.find(reg, Items.DIAMOND).isEmpty(), "diamonds are not for sale");
        h.succeed();
    }

    /** Blueprints: named after the target, stack per target, can't be sold, price lookup, save round trip. */
    @GameTest(template = "empty_long")
    public static void blueprintComponentStackingAndName(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        ItemStack drill = bp(ModItems.MINING_DRILL.get(), 1), drill2 = bp(ModItems.MINING_DRILL.get(), 3), pipe = bp(ModItems.WATER_PIPE.get(), 1);
        check(h, ItemStack.isSameItemSameComponents(drill, drill2), "same target stacks");
        check(h, !ItemStack.isSameItemSameComponents(drill, pipe), "different targets don't stack");
        check(h, BlueprintItem.target(drill) == ModItems.MINING_DRILL.get(), "target read back");
        check(h, drill.getHoverName().getString().contains("Tier 1 Mining Drill") || drill.getHoverName().getContents().toString().contains("blueprint.for"),
                "named after the target: " + drill.getHoverName());
        check(h, !Selling.isSellable(drill, reg) && Selling.stackValue(drill2, reg) == 0, "blueprints can't be sold");
        check(h, ShopCatalogue.forBlueprint(reg, drill).get().price() == 2048, "price of a Tier 1 Mining Drill blueprint");
        check(h, ShopCatalogue.forBlueprint(reg, new ItemStack(ModItems.BLUEPRINT.get())).isEmpty(), "blank blueprint has no price");
        check(h, ShopCatalogue.forBlueprint(reg, bp(Items.DIAMOND, 1)).isEmpty(), "uncatalogued blueprint has no price");
        ItemStack back = ItemStack.parse(reg, drill2.save(reg)).orElseThrow();
        check(h, ItemStack.matches(back, drill2), "save round trip");
        check(h, ShopCatalogue.totalCost(reg, List.of(drill2, pipe)) == 3 * 2048 + 8, "total cost 3 drills + pipes");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Sell Pad
    // ------------------------------------------------------------------------------------------------

    /** Right-click places the stack (no coins, no selling), merges with matching stacks; unsellable is refused. */
    @GameTest(template = "empty_long")
    public static void sellPadHoldsStacks(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        use(h, p, SELL, new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 3));
        PadBlockEntity pad = pad(h, SELL);
        check(h, pad.contents().size() == 1 && padItems(pad) == 3, "3 Folkiron on the pad: " + pad.contents());
        check(h, p.getMainHandItem().isEmpty(), "taken from the hand");
        check(h, coinsValue(p) == 0, "nothing was sold");
        use(h, p, SELL, new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 5));
        check(h, pad.contents().size() == 1 && padItems(pad) == 8, "merged into one stack of 8: " + pad.contents());
        use(h, p, SELL, new ItemStack(ModItems.RUBY_SMALL.get(), 2));
        check(h, pad.contents().size() == 2, "second kind starts a new slot");
        // total value shown = what the button would pay: 8 x 60 + 2 x 140
        check(h, SellPadBlock.value(pad.contents(), h.getLevel().registryAccess()) == 8 * 60 + 2 * 140, "pad value");
        h.succeed();
    }

    /** The pad holds at most 9 stacks; what doesn't fit stays with the player. */
    @GameTest(template = "empty_long")
    public static void sellPadHoldsNineStacks(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        Item[] kinds = {ModItems.FOLKIRON_CHUNK_SMALL.get(), ModItems.FOLKIRON_CHUNK_MEDIUM.get(), ModItems.FOLKIRON_CHUNK_LARGE.get(),
                ModItems.GOLDCHRO_CHUNK_SMALL.get(), ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), ModItems.GOLDCHRO_CHUNK_LARGE.get(),
                ModItems.RUBY_SMALL.get(), ModItems.RUBY_MEDIUM.get(), ModItems.RUBY_LARGE.get()};
        for (Item k : kinds) give(p, new ItemStack(k, 2));
        sneakUse(h, p, SELL, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        PadBlockEntity pad = pad(h, SELL);
        check(h, pad.contents().size() == 9 && padItems(pad) == 18, "all 9 kinds on the pad");
        check(h, count(p, kinds[0]) == 0, "inventory emptied of them");
        // a 10th kind: no room
        ItemStack extra = new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 64); // merges up to 64 into the existing small stack
        use(h, p, SELL, extra);
        check(h, pad.contents().size() == 9 && pad.contents().get(0).getCount() == 64, "merge fills the stack to 64: " + pad.contents().get(0));
        ItemStack rest = new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 10);
        use(h, p, SELL, rest);
        check(h, pad.contents().size() == 9 && p.getMainHandItem().getCount() == 10, "full small stack, 10 stay in the hand: " + p.getMainHandItem());
        h.succeed();
    }

    /** Sneak-right-click moves every sellable stack (hotbar, inventory, off hand) onto the pad; the rest stays. */
    @GameTest(template = "empty_long")
    public static void sellPadSneakMovesEverythingSellable(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MINING_DRILL.get()));
        give(p, new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 5));
        give(p, new ItemStack(ModItems.RUBY_LARGE.get(), 1));
        give(p, new ItemStack(ModItems.GOLD_COIN.get(), 3));
        give(p, DirtTier.clumps(2, 2));
        give(p, bp(Items.DIRT, 2));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), 1));
        sneakUse(h, p, SELL, p.getMainHandItem(), InteractionHand.MAIN_HAND);
        PadBlockEntity pad = pad(h, SELL);
        check(h, padItems(pad) == 5 + 1 + 1 && pad.contents().size() == 3, "Folkiron, Ruby and Goldchro moved: " + pad.contents());
        check(h, count(p, ModItems.FOLKIRON_CHUNK_SMALL.get()) == 0 && count(p, ModItems.RUBY_LARGE.get()) == 0
                && count(p, ModItems.GOLDCHRO_CHUNK_MEDIUM.get()) == 0, "sellables gone from the inventory (incl. off hand)");
        check(h, count(p, ModItems.MINING_DRILL.get()) == 1 && count(p, ModItems.GOLD_COIN.get()) == 3
                && count(p, ModItems.DIRT_CLUMP.get()) == 2 && count(p, ModItems.BLUEPRINT.get()) == 2, "machine, coins, clumps and blueprints kept");
        check(h, coinsValue(p) == 3 * 64, "no coins were paid");
        check(h, h.getBlockState(SELL.above()).isAir(), "the drill in hand was not placed");
        h.succeed();
    }

    /** Coins, machines, clumps, tools, blueprints and vanilla items are refused: they stay in the hand. */
    @GameTest(template = "empty_long")
    public static void sellPadRefusesUnsellables(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        for (ItemStack s : new ItemStack[]{new ItemStack(ModItems.GOLD_COIN.get(), 5), new ItemStack(ModItems.SLUICE_BOX.get()),
                DirtTier.clumps(3, 4), new ItemStack(Items.DIAMOND, 2), new ItemStack(ModItems.SELL_PAD.get()),
                new ItemStack(ModItems.BRUSH.get()), bp(ModItems.MINING_DRILL.get(), 1)}) {
            ItemStack copy = s.copy();
            use(h, p, SELL, s);
            check(h, ItemStack.matches(p.getMainHandItem(), copy), "refused and kept: " + copy + " -> " + p.getMainHandItem());
        }
        check(h, pad(h, SELL).isEmpty(), "nothing on the pad");
        // sneak with only unsellables: nothing moves either
        give(p, new ItemStack(ModItems.GOLD_COIN.get(), 5));
        sneakUse(h, p, SELL, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        check(h, pad(h, SELL).isEmpty() && count(p, ModItems.GOLD_COIN.get()) == 5, "sneak moves nothing unsellable");
        h.succeed();
    }

    /** Thrown sellable items land on the pad (merging); unsellable ones, and what doesn't fit, stay items. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void sellPadTakesThrownItems(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        drop(h, p, SELL, new ItemStack(ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), 2), 0);
        drop(h, p, SELL, new ItemStack(Items.STICK), 0.2);
        drop(h, p, SELL, new ItemStack(ModItems.GOLD_COIN.get(), 3), -0.2);
        h.startSequence()
                .thenWaitUntil(() -> check(h, entities(h, ModItems.GOLDCHRO_CHUNK_MEDIUM.get()) == 0, "Goldchro goes onto the pad"))
                .thenExecuteAfter(20, () -> {
                    PadBlockEntity pad = pad(h, SELL);
                    check(h, pad.contents().size() == 1 && padItems(pad) == 2 && pad.contents().get(0).is(ModItems.GOLDCHRO_CHUNK_MEDIUM.get()),
                            "2 Goldchro on the pad: " + pad.contents());
                    check(h, entities(h, Items.STICK) == 1 && entities(h, ModItems.GOLD_COIN.get()) == 3, "stick and coins stay items");
                    check(h, entities(h, ModItems.COPPER_COIN.get()) + entities(h, ModItems.SILVER_COIN.get()) == 0, "nothing was sold");
                })
                .thenSucceed();
    }

    /** Thrown items that don't fit stay as items (full pad). */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void sellPadFullLeavesThrownItems(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        PadBlockEntity pad = pad(h, SELL);
        Item[] kinds = {ModItems.FOLKIRON_CHUNK_SMALL.get(), ModItems.FOLKIRON_CHUNK_MEDIUM.get(), ModItems.FOLKIRON_CHUNK_LARGE.get(),
                ModItems.GOLDCHRO_CHUNK_SMALL.get(), ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), ModItems.GOLDCHRO_CHUNK_LARGE.get(),
                ModItems.RUBY_SMALL.get(), ModItems.RUBY_MEDIUM.get(), ModItems.RUBY_LARGE.get()};
        for (Item k : kinds) pad.insert(new ItemStack(k, 1));
        ItemStack almost = new ItemStack(ModItems.RUBY_LARGE.get(), 64); // merges 63 into the existing 1, leaves nothing... (stack of 64)
        drop(h, p, SELL, new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 64), 0);
        h.startSequence()
                .thenExecuteAfter(30, () -> {
                    check(h, pad.contents().size() == 9, "still 9 slots");
                    check(h, pad.contents().get(0).getCount() == 64, "merged up to a full stack: " + pad.contents().get(0));
                    check(h, entities(h, ModItems.FOLKIRON_CHUNK_SMALL.get()) == 1, "the 1 that didn't fit stays an item, got " + entities(h, ModItems.FOLKIRON_CHUNK_SMALL.get()));
                })
                .thenSucceed();
    }

    /** Empty-hand right-click takes everything back (overflow at the feet); sneak-clicking an empty pad does nothing. */
    @GameTest(template = "empty_long")
    public static void sellPadEmptyHandTakesBack(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        PadBlockEntity pad = pad(h, SELL);
        pad.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 7));
        pad.insert(new ItemStack(ModItems.RUBY_MEDIUM.get(), 2));
        use(h, p, SELL, ItemStack.EMPTY);
        check(h, pad.isEmpty(), "pad emptied");
        check(h, count(p, ModItems.FOLKIRON_CHUNK_LARGE.get()) == 7 && count(p, ModItems.RUBY_MEDIUM.get()) == 2, "all back in the inventory");
        sneakUse(h, p, SELL, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        check(h, padItems(pad) == 9, "sneak puts them straight back");
        h.succeed();
    }

    /** Breaking a crafted Sell Pad drops it AND its contents; contents survive a save/load. */
    @GameTest(template = "empty_long")
    public static void sellPadBreaksWithContentsAndSaves(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        PadBlockEntity pad = pad(h, SELL);
        pad.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 7));
        pad.insert(new ItemStack(ModItems.RUBY_MEDIUM.get(), 2));
        var reg = h.getLevel().registryAccess();
        CompoundTag tag = pad.saveWithoutMetadata(reg);
        pad.takeAll();
        pad.loadWithComponents(tag, reg);
        check(h, padItems(pad) == 9 && pad.contents().size() == 2, "save round trip: " + pad.contents());
        check(h, h.getBlockState(SELL).getDestroySpeed(h.getLevel(), h.absolutePos(SELL)) >= 0, "breakable");
        p.gameMode.destroyBlock(h.absolutePos(SELL));
        h.runAfterDelay(2, () -> {
            check(h, entities(h, ModItems.SELL_PAD.get()) == 1, "drops itself");
            check(h, entities(h, ModItems.FOLKIRON_CHUNK_LARGE.get()) == 7 && entities(h, ModItems.RUBY_MEDIUM.get()) == 2, "drops its contents");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Money Pan
    // ------------------------------------------------------------------------------------------------

    /** Right-click with coins deposits that stack only; other items are refused. */
    @GameTest(template = "empty_long")
    public static void moneyPanDepositsHeldCoins(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        give(p, new ItemStack(ModItems.GOLD_COIN.get(), 9)); // other coins stay
        use(h, p, PAN, new ItemStack(ModItems.SILVER_COIN.get(), 5));
        check(h, pan(h, PAN).balance() == 40 && p.getMainHandItem().isEmpty(), "5 silver = 40 copper, got " + pan(h, PAN).balance());
        check(h, count(p, ModItems.GOLD_COIN.get()) == 9, "other coins untouched");
        use(h, p, PAN, new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 2));
        check(h, pan(h, PAN).balance() == 40 && p.getMainHandItem().getCount() == 2, "non-coins refused");
        h.succeed();
    }

    /** Sneak-right-click deposits every coin (inventory + off hand) and nothing else. */
    @GameTest(template = "empty_long")
    public static void moneyPanSneakDepositsAllCoins(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        give(p, new ItemStack(ModItems.GOLD_COIN.get(), 3));
        give(p, new ItemStack(ModItems.SILVER_COIN.get(), 7));
        give(p, new ItemStack(ModItems.COPPER_COIN.get(), 5));
        give(p, new ItemStack(ModItems.RUBY_SMALL.get(), 1));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.GOLD_COIN.get(), 1));
        sneakUse(h, p, PAN, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        check(h, pan(h, PAN).balance() == 4 * 64 + 56 + 5, "balance " + pan(h, PAN).balance());
        check(h, coinsValue(p) == 0 && count(p, ModItems.RUBY_SMALL.get()) == 1, "all coins gone, ruby kept");
        h.succeed();
    }

    /** Coins thrown onto the pan are deposited. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void moneyPanTakesThrownCoins(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        drop(h, p, PAN, new ItemStack(ModItems.GOLD_COIN.get(), 2), 0);
        drop(h, p, PAN, new ItemStack(Items.STICK), 0.2);
        h.startSequence()
                .thenWaitUntil(() -> check(h, entities(h, ModItems.GOLD_COIN.get()) == 0, "coins deposited"))
                .thenExecuteAfter(10, () -> {
                    check(h, pan(h, PAN).balance() == 128, "balance " + pan(h, PAN).balance());
                    check(h, entities(h, Items.STICK) == 1, "the stick stays");
                })
                .thenSucceed();
    }

    /** Empty hand withdraws the whole balance as exact coins, largest first; overflow drops at the feet. */
    @GameTest(template = "empty_long")
    public static void moneyPanWithdrawsExactCoins(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        pan(h, PAN).deposit(2 * 64 + 6 * 8 + 4); // 2 gold 6 silver 4 copper
        use(h, p, PAN, ItemStack.EMPTY);
        check(h, count(p, ModItems.GOLD_COIN.get()) == 2 && count(p, ModItems.SILVER_COIN.get()) == 6 && count(p, ModItems.COPPER_COIN.get()) == 4,
                "2/6/4, got " + count(p, ModItems.GOLD_COIN.get()) + "/" + count(p, ModItems.SILVER_COIN.get()) + "/" + count(p, ModItems.COPPER_COIN.get()));
        check(h, pan(h, PAN).balance() == 0, "balance now 0");
        // a huge balance: no cap, stacks of 64, overflow at the feet when the inventory is full
        pan(h, PAN).deposit(1_000_000_000L * 64);
        check(h, pan(h, PAN).balance() == 64_000_000_000L, "no cap (beyond int range)");
        pan(h, PAN).withdrawAll();
        pan(h, PAN).deposit(70L * 32768);
        p.getInventory().clearContent();
        for (int i = 1; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
        int before = wide(h, p, ModItems.MINT_BAR.get());
        use(h, p, PAN, ItemStack.EMPTY);
        check(h, wide(h, p, ModItems.MINT_BAR.get()) - before == 6 && count(p, ModItems.MINT_BAR.get()) == 64 && pan(h, PAN).balance() == 0, "one stack in the free slot, the rest at the feet: before " + before + " after " + wide(h, p, ModItems.MINT_BAR.get()) + " inv " + count(p, ModItems.MINT_BAR.get()));
        h.succeed();
    }

    /** The balance (and the coin pile state) survive saving; pile steps follow the balance. */
    @GameTest(template = "empty_long")
    public static void moneyPanSavesAndShowsPileSteps(GameTestHelper h) {
        shop(h);
        MoneyPanBlockEntity pan = pan(h, PAN);
        long[] balances = {0, 1, 63, 64, 639, 640, 5000};
        int[] piles = {0, 1, 1, 2, 2, 3, 3};
        for (int i = 0; i < balances.length; i++) {
            pan.setBalance(balances[i]);
            check(h, h.getBlockState(PAN).getValue(MoneyPanBlock.PILE) == piles[i], "balance " + balances[i] + " -> pile " + piles[i] + ", got " + h.getBlockState(PAN).getValue(MoneyPanBlock.PILE));
        }
        CompoundTag tag = pan.saveWithoutMetadata(h.getLevel().registryAccess());
        pan.setBalance(0);
        pan.loadWithComponents(tag, h.getLevel().registryAccess());
        check(h, pan.balance() == 5000, "balance restored");
        check(h, !pan.withdraw(5001) && pan.balance() == 5000, "can't overdraw");
        check(h, pan.withdraw(4999) && pan.balance() == 1, "withdraw");
        h.succeed();
    }

    /** Breaking a crafted Money Pan drops it plus its balance as coins. */
    @GameTest(template = "empty_long")
    public static void moneyPanBreakDropsBalance(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        pan(h, PAN).deposit(3 * 64 + 2 * 8 + 1);
        p.gameMode.destroyBlock(h.absolutePos(PAN));
        h.runAfterDelay(2, () -> {
            check(h, entities(h, ModItems.MONEY_PAN.get()) == 1, "drops itself");
            check(h, entities(h, ModItems.GOLD_COIN.get()) == 3 && entities(h, ModItems.SILVER_COIN.get()) == 2 && entities(h, ModItems.COPPER_COIN.get()) == 1,
                    "drops 3 gold 2 silver 1 copper");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Buy Pad
    // ------------------------------------------------------------------------------------------------

    /** Buy Pad takes only catalogued blueprints (stacks, merges), refuses everything else; empty hand takes back. */
    @GameTest(template = "empty_long")
    public static void buyPadAcceptsOnlyBlueprints(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        for (ItemStack s : new ItemStack[]{new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 2), new ItemStack(ModItems.GOLD_COIN.get()),
                new ItemStack(ModItems.MINING_DRILL.get()), new ItemStack(ModItems.BLUEPRINT.get()), bp(Items.DIAMOND, 1)}) {
            ItemStack copy = s.copy();
            use(h, p, BUY, s);
            check(h, ItemStack.matches(p.getMainHandItem(), copy), "refused: " + copy);
        }
        check(h, pad(h, BUY).isEmpty(), "nothing on the Buy Pad");
        use(h, p, BUY, bp(ModItems.MINING_DRILL.get(), 1));
        use(h, p, BUY, bp(ModItems.MINING_DRILL.get(), 2));
        use(h, p, BUY, bp(ModItems.WATER_PIPE.get(), 1));
        PadBlockEntity pad = pad(h, BUY);
        check(h, pad.contents().size() == 2 && padItems(pad) == 4, "3 drill blueprints merged + 1 pipe: " + pad.contents());
        check(h, ShopCatalogue.totalCost(h.getLevel().registryAccess(), pad.contents()) == 3 * 2048 + 8, "total cost");
        // sneak moves blueprints from the inventory, leaves the rest
        give(p, bp(ModItems.CLUMP_BELT.get(), 2));
        give(p, new ItemStack(ModItems.RUBY_LARGE.get()));
        sneakUse(h, p, BUY, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        check(h, padItems(pad) == 6 && count(p, ModItems.RUBY_LARGE.get()) == 1, "sneak moved the blueprints only");
        use(h, p, BUY, ItemStack.EMPTY);
        check(h, pad.isEmpty() && count(p, ModItems.BLUEPRINT.get()) == 6, "empty hand takes the blueprints back");
        h.succeed();
    }

    /** Thrown blueprints land on the Buy Pad; thrown junk doesn't. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void buyPadTakesThrownBlueprints(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        drop(h, p, BUY, bp(ModItems.SLUICE_BOX.get(), 1), 0);
        drop(h, p, BUY, new ItemStack(Items.STICK), 0.2);
        h.startSequence()
                .thenWaitUntil(() -> check(h, entities(h, ModItems.BLUEPRINT.get()) == 0, "blueprint onto the pad"))
                .thenExecuteAfter(10, () -> {
                    check(h, padItems(pad(h, BUY)) == 1, "on the pad");
                    check(h, entities(h, Items.STICK) == 1, "stick stays, got " + entities(h, Items.STICK) + " wide " + wide(h, p, Items.STICK) + " all " + h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(BUY)).inflate(8)).stream().map(e -> e.getItem() + "@" + e.position()).toList() + " player " + p.position() + " pad " + h.absolutePos(BUY));
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------------------------------------
    // Shop Button
    // ------------------------------------------------------------------------------------------------

    /** The button sells EXACTLY the pad's value into the adjacent Money Pan and clears the pad; the inventory gets nothing. */
    @GameTest(template = "empty_long")
    public static void buttonSellsIntoTheMoneyPan(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        give(p, new ItemStack(ModItems.GOLD_COIN.get(), 1)); // must not be touched
        pad(h, SELL).insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 3));        // 180
        pad(h, SELL).insert(new ItemStack(ModItems.GOLDCHRO_CHUNK_SMALL.get(), 1));        // 16
        pad(h, SELL).insert(new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 7));        // 70
        long expected = 180 + 16 + 70;
        check(h, SellPadBlock.value(pad(h, SELL).contents(), h.getLevel().registryAccess()) == expected, "pad value 266");
        press(h, p);
        check(h, pan(h, PAN).balance() == expected, "balance = exact pad value, got " + pan(h, PAN).balance());
        check(h, pad(h, SELL).isEmpty(), "pad cleared");
        check(h, coinsValue(p) == 64, "inventory coins untouched, nothing paid to the inventory");
        check(h, h.getBlockState(BTN).getValue(ShopButtonBlock.PRESSED), "button is down");
        h.succeed();
    }

    /** No Money Pan next to the button: nothing is sold (the pad keeps everything, no coins appear anywhere). */
    @GameTest(template = "empty_long")
    public static void buttonWithoutMoneyPanSellsNothing(GameTestHelper h) {
        shop(h);
        h.setBlock(PAN, Blocks.AIR);
        ServerPlayer p = player(h);
        pad(h, SELL).insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 3));
        pad(h, BUY).insert(bp(Items.DIRT, 1));
        press(h, p);
        check(h, padItems(pad(h, SELL)) == 3, "pad keeps its items");
        check(h, coinsValue(p) == 0 && entities(h, ModItems.COPPER_COIN.get()) + entities(h, ModItems.SILVER_COIN.get()) + entities(h, ModItems.GOLD_COIN.get()) == 0,
                "no coins paid out");
        check(h, count(p, Items.DIRT) == 0 && padItems(pad(h, BUY)) == 1, "nothing bought");
        h.succeed();
    }

    /** Buying pays from the balance, delivers the items, USES UP the blueprints, never touches inventory coins. */
    @GameTest(template = "empty_long")
    public static void buttonBuysFromTheBalance(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        give(p, new ItemStack(ModItems.GOLD_COIN.get(), 64)); // lots of inventory coins, must stay
        pan(h, PAN).deposit(2048 + 8 + 500);
        pad(h, BUY).insert(bp(ModItems.MINING_DRILL.get(), 1));
        pad(h, BUY).insert(bp(ModItems.WATER_PIPE.get(), 1));
        press(h, p);
        check(h, count(p, ModItems.MINING_DRILL.get()) == 1 && count(p, ModItems.WATER_PIPE.get()) == 1, "1 drill and 1 pipe (one blueprint = one item)");
        check(h, pan(h, PAN).balance() == 500, "paid exactly 2056 from the balance, got " + pan(h, PAN).balance());
        check(h, count(p, ModItems.GOLD_COIN.get()) == 64, "inventory coins never touched");
        check(h, pad(h, BUY).isEmpty(), "the blueprints are used up: the pad is empty, got " + pad(h, BUY).contents());
        // pressing again buys nothing (no blueprints left)
        pan(h, PAN).deposit(5000);
        press(h, p);
        check(h, count(p, ModItems.MINING_DRILL.get()) == 1 && pan(h, PAN).balance() == 5500, "nothing left to buy");
        // several blueprints of one target buy several items
        pad(h, BUY).insert(bp(ModItems.DIRT_CLUMP.get(), 3));
        pan(h, PAN).withdrawAll();
        pan(h, PAN).deposit(3 * 80 + 7);
        press(h, p);
        check(h, count(p, ModItems.DIRT_CLUMP.get()) == 3 && pan(h, PAN).balance() == 7 && pad(h, BUY).isEmpty(), "3 blueprints = 3 clumps for 240");
        h.succeed();
    }

    /** 10 Clump Belt blueprints buy 10 belts for exactly 2 gold 4 silver, and are all used up. */
    @GameTest(template = "empty_long")
    public static void tenBlueprintsBuyTenItems(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        var reg = h.getLevel().registryAccess();
        pad(h, BUY).insert(bp(ModItems.CLUMP_BELT.get(), 10));
        check(h, ShopCatalogue.totalCost(reg, pad(h, BUY).contents()) == 160, "10 belts cost 160 copper");
        check(h, java.util.Arrays.equals(Selling.split(160), new long[]{2, 4, 0}), "160 copper = 2 gold 4 silver");
        pan(h, PAN).deposit(160 + 3);
        press(h, p);
        check(h, count(p, ModItems.CLUMP_BELT.get()) == 10, "10 belts, got " + count(p, ModItems.CLUMP_BELT.get()));
        check(h, pan(h, PAN).balance() == 3 && pad(h, BUY).isEmpty(), "paid 160, pad empty");
        // one copper short: nothing happens, the 10 blueprints stay
        pad(h, BUY).insert(bp(ModItems.CLUMP_BELT.get(), 10));
        pan(h, PAN).withdrawAll();
        pan(h, PAN).deposit(159);
        press(h, p);
        check(h, count(p, ModItems.CLUMP_BELT.get()) == 10 && pan(h, PAN).balance() == 159 && padItems(pad(h, BUY)) == 10, "short by 1 copper: nothing changes");
        h.succeed();
    }

    /** Not enough balance (or no Money Pan): nothing changes, inventory coins are NOT used instead. */
    @GameTest(template = "empty_long")
    public static void buttonNotEnoughMoneyChangesNothing(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        give(p, new ItemStack(ModItems.GOLD_COIN.get(), 64)); // 4096 copper in the inventory, enough for the drill
        pan(h, PAN).deposit(2047);                                          // one copper short
        pad(h, BUY).insert(bp(ModItems.MINING_DRILL.get(), 1));
        press(h, p);
        check(h, count(p, ModItems.MINING_DRILL.get()) == 0, "nothing delivered");
        check(h, pan(h, PAN).balance() == 2047, "balance unchanged");
        check(h, count(p, ModItems.GOLD_COIN.get()) == 64, "inventory coins not used");
        check(h, padItems(pad(h, BUY)) == 1, "failed purchase: blueprint still on the pad");
        // no Money Pan at all
        h.setBlock(PAN, Blocks.AIR);
        press(h, p);
        check(h, count(p, ModItems.MINING_DRILL.get()) == 0 && count(p, ModItems.GOLD_COIN.get()) == 64 && padItems(pad(h, BUY)) == 1, "no pan: nothing bought, blueprint stays");
        // partial: two pads' worth is all-or-nothing
        h.setBlock(PAN, ModBlocks.MONEY_PAN.get());
        pan(h, PAN).deposit(2048);
        pad(h, BUY).insert(bp(ModItems.WATER_INTAKE.get(), 1)); // total 2560 > 2048
        press(h, p);
        check(h, count(p, ModItems.MINING_DRILL.get()) == 0 && count(p, ModItems.WATER_INTAKE.get()) == 0 && pan(h, PAN).balance() == 2048,
                "all or nothing: nothing bought, balance intact");
        h.succeed();
    }

    /** One press: sell first (sale money pays for the purchase), then buy. */
    @GameTest(template = "empty_long")
    public static void buttonSellsThenBuysInOnePress(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        pan(h, PAN).deposit(100);                                                     // far too little for a drill alone
        pad(h, SELL).insert(new ItemStack(ModItems.RUBY_LARGE.get(), 6));             // 6 x 560 = 3360
        pad(h, BUY).insert(bp(ModItems.MINING_DRILL.get(), 1));                       // 2048
        ShopButtonBlock.Result r = ShopButtonBlock.press(h.getLevel(), h.absolutePos(BTN), p);
        check(h, r.sold() == 3360 && r.spent() == 2048, "sold 3360, spent 2048: " + r);
        check(h, count(p, ModItems.MINING_DRILL.get()) == 1, "drill bought with the sale money");
        check(h, pan(h, PAN).balance() == 100 + 3360 - 2048, "balance " + pan(h, PAN).balance());
        check(h, pad(h, SELL).isEmpty() && pad(h, BUY).isEmpty(), "sell pad cleared, blueprint used up");
        h.succeed();
    }

    /** Only the 4 horizontal neighbours count: pads 2 blocks away, diagonal or above/below are ignored. */
    @GameTest(template = "empty_long")
    public static void buttonActsOnlyOnAdjacentBlocks(GameTestHelper h) {
        clear(h);
        h.setBlock(BTN, ModBlocks.SHOP_BUTTON.get());
        BlockPos far = new BlockPos(2, 1, 2), diag = new BlockPos(3, 1, 3), farPan = new BlockPos(4, 1, 4), above = new BlockPos(4, 2, 2);
        h.setBlock(far, ModBlocks.SELL_PAD.get());        // 2 away
        h.setBlock(diag, ModBlocks.SELL_PAD.get());       // diagonal
        h.setBlock(farPan, ModBlocks.MONEY_PAN.get());    // 2 away
        h.setBlock(above, ModBlocks.SELL_PAD.get());      // above the button
        for (BlockPos p : new BlockPos[]{far, diag, above}) pad(h, p).insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 2));
        ServerPlayer pl = player(h);
        press(h, pl);
        check(h, padItems(pad(h, far)) == 2 && padItems(pad(h, diag)) == 2 && padItems(pad(h, above)) == 2, "non-adjacent pads untouched");
        check(h, pan(h, farPan).balance() == 0, "non-adjacent pan untouched");
        // now put a pan next to it: still the far pads are not sold
        h.setBlock(PAN, ModBlocks.MONEY_PAN.get());
        press(h, pl);
        check(h, padItems(pad(h, far)) == 2 && pan(h, PAN).balance() == 0, "pan alone: nothing adjacent to sell");
        h.succeed();
    }

    /** The press animation: the state goes down, then back up by itself via one scheduled tick. */
    @GameTest(template = "empty_long", timeoutTicks = 60)
    public static void buttonAnimates(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        press(h, p);
        check(h, h.getBlockState(BTN).getValue(ShopButtonBlock.PRESSED), "pressed");
        h.startSequence()
                .thenExecuteAfter(ShopButtonBlock.PRESS_TICKS + 3, () ->
                        check(h, !h.getBlockState(BTN).getValue(ShopButtonBlock.PRESSED), "released"))
                .thenSucceed();
    }

    /** Several pads of the same kind next to one button all count; two Money Pans: the first is used. */
    @GameTest(template = "empty_long")
    public static void buttonHandlesTwoSellPads(GameTestHelper h) {
        shop(h);
        BlockPos north = BTN.north();
        h.setBlock(north, ModBlocks.SELL_PAD.get());
        ServerPlayer p = player(h);
        pad(h, SELL).insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 1));  // 60
        pad(h, north).insert(new ItemStack(ModItems.RUBY_SMALL.get(), 1));           // 140
        press(h, p);
        check(h, pan(h, PAN).balance() == 200 && pad(h, SELL).isEmpty() && pad(h, north).isEmpty(), "both pads sold: " + pan(h, PAN).balance());
        h.succeed();
    }

    /** Pressing with an item in hand presses (and never places the block); exact change helpers still hold. */
    @GameTest(template = "empty_long")
    public static void buttonWorksWithItemInHandAndCoinsAreExact(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        pad(h, SELL).insert(new ItemStack(ModItems.RUBY_LARGE.get(), 1));
        use(h, p, BTN, new ItemStack(Items.DIRT, 4));
        check(h, pan(h, PAN).balance() == 560 && p.getMainHandItem().getCount() == 4, "pressed with dirt in hand, dirt kept");
        check(h, h.getBlockState(BTN.above()).isAir(), "no block placed on the button");
        long v = Selling.stackValue(new ItemStack(ModItems.GOLDCHRO_CHUNK_SMALL.get()), h.getLevel().registryAccess())
                + Selling.stackValue(new ItemStack(ModItems.FOLKIRON_CHUNK_SMALL.get(), 7), h.getLevel().registryAccess());
        long[] split = Selling.split(v);
        check(h, v == 86 && split[0] == 1 && split[1] == 2 && split[2] == 6, "86 copper -> 1/2/6, got " + v);
        check(h, Selling.coins(32768L * 70).get(0).getCount() == 64 && Selling.coins(32768L * 70).get(1).getCount() == 6, "biggest coins in stacks of 64");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Shop Display
    // ------------------------------------------------------------------------------------------------

    private static void display(GameTestHelper h, Item item) {
        clear(h);
        h.setBlock(BTN, ModBlocks.SHOP_DISPLAY.get());
        ((ShopDisplayBlockEntity) h.getBlockEntity(BTN)).setItem(item);
    }

    /** Right-click gives a free, unlimited Blueprint of the shown item. */
    @GameTest(template = "empty_long")
    public static void displayGivesBlueprints(GameTestHelper h) {
        display(h, ModItems.MINING_DRILL.get());
        ServerPlayer p = player(h);
        use(h, p, BTN, ItemStack.EMPTY);
        use(h, p, BTN, ItemStack.EMPTY);
        use(h, p, BTN, new ItemStack(Items.DIRT)); // even with something in the hand
        check(h, count(p, ModItems.BLUEPRINT.get()) == 3, "3 blueprints (1 per plain click), unlimited: " + count(p, ModItems.BLUEPRINT.get()));
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModItems.BLUEPRINT.get())) {
                check(h, BlueprintItem.target(s) == ModItems.MINING_DRILL.get(), "for the Tier 1 Mining Drill");
            }
        }
        check(h, h.getBlockState(BTN).is(ModBlocks.SHOP_DISPLAY.get()), "display still there");
        // sneak-right-click gives a stack of 16 (survival too)
        int before16 = count(p, ModItems.BLUEPRINT.get());
        sneakUse(h, p, BTN, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        check(h, count(p, ModItems.BLUEPRINT.get()) == before16 + 16, "sneak-click gives 16, got " + (count(p, ModItems.BLUEPRINT.get()) - before16));
        // an uncatalogued item gives nothing
        ((ShopDisplayBlockEntity) h.getBlockEntity(BTN)).setItem(Items.DIAMOND);
        int before = count(p, ModItems.BLUEPRINT.get());
        use(h, p, BTN, ItemStack.EMPTY);
        check(h, count(p, ModItems.BLUEPRINT.get()) == before, "diamond is not in the catalogue: nothing given");
        h.succeed();
    }

    /** A displayed blueprint works end-to-end: display -> Buy Pad -> button -> item. */
    @GameTest(template = "empty_long")
    public static void displayBlueprintBuysOnTheBuyPad(GameTestHelper h) {
        shop(h);
        BlockPos disp = new BlockPos(6, 1, 2);
        h.setBlock(disp, ModBlocks.SHOP_DISPLAY.get());
        ((ShopDisplayBlockEntity) h.getBlockEntity(disp)).setItem(ModItems.CLUMP_BELT.get());
        ServerPlayer p = player(h);
        use(h, p, disp, ItemStack.EMPTY);
        ItemStack blueprint = p.getInventory().items.stream().filter(s -> s.is(ModItems.BLUEPRINT.get())).findFirst().orElseThrow();
        use(h, p, BUY, blueprint);
        pan(h, PAN).deposit(16);
        press(h, p);
        check(h, count(p, ModItems.CLUMP_BELT.get()) == 1 && pan(h, PAN).balance() == 0 && pad(h, BUY).isEmpty(), "1 belt for 2 silver, blueprint used up");
        h.succeed();
    }

    /** Unbreakable in survival: no mining progress, explosion proof, piston proof; creative can set and break it. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void displayIsUnbreakableInSurvival(GameTestHelper h) {
        display(h, ModItems.PAN.get());
        BlockPos abs = h.absolutePos(BTN);
        ServerPlayer p = player(h);
        BlockState st = h.getBlockState(BTN);
        check(h, st.getDestroySpeed(h.getLevel(), abs) < 0, "hardness -1");
        check(h, st.getDestroyProgress(p, h.getLevel(), abs) == 0.0F, "no mining progress");
        check(h, st.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, "piston proof");
        p.gameMode.handleBlockBreakAction(abs, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, 320, 0);
        h.getLevel().explode(null, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 1.5, 6.0F, Level.ExplosionInteraction.TNT);
        h.startSequence().thenExecuteAfter(10, () -> {
            h.assertBlockPresent(ModBlocks.SHOP_DISPLAY.get(), BTN);
            check(h, ((ShopDisplayBlockEntity) h.getBlockEntity(BTN)).item() == ModItems.PAN.get(), "still shows the pan");
        }).thenSucceed();
    }

    /** Creative sneak-right-click with an item (or a blueprint) sets what the display shows; survival can't. */
    @GameTest(template = "empty_long")
    public static void displayIsSetInCreative(GameTestHelper h) {
        clear(h);
        h.setBlock(BTN, ModBlocks.SHOP_DISPLAY.get());
        ShopDisplayBlockEntity be = (ShopDisplayBlockEntity) h.getBlockEntity(BTN);
        check(h, be.item() == null, "starts empty");
        ServerPlayer survival = player(h);
        sneakUse(h, survival, BTN, new ItemStack(ModItems.SLUICE_BOX.get()), InteractionHand.MAIN_HAND);
        check(h, be.item() == null, "survival can't set it");
        use(h, survival, BTN, ItemStack.EMPTY);
        check(h, count(survival, ModItems.BLUEPRINT.get()) == 0, "empty display gives nothing");
        ServerPlayer creative = h.makeMockServerPlayerInLevel();
        creative.setGameMode(GameType.CREATIVE);
        creative.getInventory().clearContent();
        sneakUse(h, creative, BTN, new ItemStack(ModItems.SLUICE_BOX.get()), InteractionHand.MAIN_HAND);
        check(h, be.item() == ModItems.SLUICE_BOX.get(), "creative sets the Sluice Box");
        check(h, count(creative, ModItems.SLUICE_BOX.get()) == 0 || creative.getMainHandItem().is(ModItems.SLUICE_BOX.get()), "no item consumed");
        sneakUse(h, creative, BTN, bp(ModItems.BRUSH.get(), 1), InteractionHand.MAIN_HAND);
        check(h, be.item() == ModItems.BRUSH.get(), "a blueprint sets its target");
        use(h, creative, BTN, ItemStack.EMPTY);
        check(h, count(creative, ModItems.BLUEPRINT.get()) >= 1, "creative also gets blueprints by right-click");
        // save round trip
        var reg = h.getLevel().registryAccess();
        CompoundTag tag = be.saveWithoutMetadata(reg);
        be.setItem(null);
        be.loadWithComponents(tag, reg);
        check(h, be.item() == ModItems.BRUSH.get(), "item survives saving");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Locked (island) variants and recipes
    // ------------------------------------------------------------------------------------------------

    /** Locked pads / pan / button can't be broken in survival or blown up; unlocked ones can. Locked items place locked. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void lockedShopBlocksAreProtected(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        Block[] blocks = {ModBlocks.SELL_PAD.get(), ModBlocks.BUY_PAD.get(), ModBlocks.MONEY_PAN.get(), ModBlocks.SHOP_BUTTON.get()};
        for (int i = 0; i < blocks.length; i++) {
            BlockPos rel = new BlockPos(1 + i * 2, 1, 1);
            h.setBlock(rel, blocks[i].defaultBlockState().setValue(ShopLock.LOCKED, true));
            BlockState st = h.getBlockState(rel);
            check(h, st.getDestroyProgress(p, h.getLevel(), h.absolutePos(rel)) == 0.0F, blocks[i] + " locked: no mining progress");
            BlockPos free = new BlockPos(1 + i * 2, 1, 3);
            h.setBlock(free, blocks[i]);
            check(h, h.getBlockState(free).getDestroyProgress(p, h.getLevel(), h.absolutePos(free)) > 0.0F, blocks[i] + " crafted: breakable");
        }
        check(h, h.getBlockState(new BlockPos(7, 1, 1)).getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, "locked button is piston proof");
        BlockPos mid = h.absolutePos(new BlockPos(4, 1, 2));
        h.getLevel().explode(null, mid.getX() + 0.5, mid.getY() + 0.2, mid.getZ() + 0.5, 6.0F, Level.ExplosionInteraction.TNT);
        h.startSequence().thenExecuteAfter(5, () -> {
            for (int i = 0; i < blocks.length; i++) {
                h.assertBlockPresent(blocks[i], new BlockPos(1 + i * 2, 1, 1));
                check(h, h.getBlockState(new BlockPos(1 + i * 2, 1, 1)).getValue(ShopLock.LOCKED), "still locked");
                check(h, h.getBlockState(new BlockPos(1 + i * 2, 1, 3)).isAir(), blocks[i] + " crafted version was blown up");
            }
        }).thenSucceed();
    }

    /** Creative-tab "Locked" items place the locked variant, normal items the crafted one. */
    @GameTest(template = "empty_long")
    public static void lockedItemsPlaceLockedBlocks(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        p.setGameMode(GameType.CREATIVE);
        h.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        ItemStack lockedPad = ShopBlockItem.locked(ModBlocks.SELL_PAD.get());
        check(h, ShopBlockItem.isLockedVariant(lockedPad) && !ShopBlockItem.isLockedVariant(new ItemStack(ModItems.SELL_PAD.get())), "variant detection");
        check(h, lockedPad.getHoverName().getString().length() > 0, "has a name");
        BlockPos abs = h.absolutePos(new BlockPos(1, 0, 1));
        p.setItemInHand(InteractionHand.MAIN_HAND, lockedPad);
        lockedPad.useOn(new net.minecraft.world.item.context.UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false)));
        check(h, h.getBlockState(new BlockPos(1, 1, 1)).is(ModBlocks.SELL_PAD.get()) && h.getBlockState(new BlockPos(1, 1, 1)).getValue(ShopLock.LOCKED), "locked item -> locked pad");
        h.setBlock(new BlockPos(3, 0, 1), Blocks.STONE);
        ItemStack plain = new ItemStack(ModItems.MONEY_PAN.get());
        BlockPos abs2 = h.absolutePos(new BlockPos(3, 0, 1));
        p.setItemInHand(InteractionHand.MAIN_HAND, plain);
        plain.useOn(new net.minecraft.world.item.context.UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs2).add(0, 0.5, 0), Direction.UP, abs2, false)));
        check(h, h.getBlockState(new BlockPos(3, 1, 1)).is(ModBlocks.MONEY_PAN.get()) && !h.getBlockState(new BlockPos(3, 1, 1)).getValue(ShopLock.LOCKED), "plain item -> crafted pan");
        h.succeed();
    }

    /** Recipes from island materials: Sell Pad (3 planks over 3 logs), Buy Pad (logs over planks), Money Pan, Shop Button. */
    @GameTest(template = "empty_long")
    public static void shopRecipes(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos abs = h.absolutePos(new BlockPos(1, 1, 1));
        ItemStack pl = new ItemStack(Items.OAK_PLANKS), lg = new ItemStack(Items.OAK_LOG), st = new ItemStack(Items.STICK), e = ItemStack.EMPTY;
        Object[][] cases = {
                {new ItemStack[]{pl, pl, pl, lg, lg, lg, e, e, e}, ModItems.SELL_PAD.get()},
                {new ItemStack[]{lg, lg, lg, pl, pl, pl, e, e, e}, ModItems.BUY_PAD.get()},
                {new ItemStack[]{st, e, st, pl, pl, pl, e, e, e}, ModItems.MONEY_PAN.get()},
                {new ItemStack[]{pl, e, e, st, e, e, lg, e, e}, ModItems.SHOP_BUTTON.get()},
        };
        for (Object[] c : cases) {
            CraftingMenu menu = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), abs));
            ItemStack[] grid = (ItemStack[]) c[0];
            for (int i = 0; i < 9; i++) menu.getSlot(1 + i).set(grid[i].copy());
            ItemStack r = menu.getSlot(0).getItem();
            check(h, r.is((Item) c[1]), "recipe for " + c[1] + ", got " + r);
        }
        h.succeed();
    }

    /** The drops and loot tables of the new blocks give back the block (pick-block-less survival break). */
    @GameTest(template = "empty_long")
    public static void shopBlocksDropThemselves(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        Block[] blocks = {ModBlocks.BUY_PAD.get(), ModBlocks.SHOP_BUTTON.get()};
        Item[] items = {ModItems.BUY_PAD.get(), ModItems.SHOP_BUTTON.get()};
        for (int i = 0; i < blocks.length; i++) h.setBlock(new BlockPos(1 + i * 2, 1, 1), blocks[i]);
        for (int i = 0; i < blocks.length; i++) p.gameMode.destroyBlock(h.absolutePos(new BlockPos(1 + i * 2, 1, 1)));
        h.runAfterDelay(2, () -> {
            for (Item it : items) check(h, entities(h, it) == 1, it + " drops itself, got " + entities(h, it));
            h.succeed();
        });
    }
}
