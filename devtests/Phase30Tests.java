package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.contract.Contract;
import com.holeydigsite.contract.ContractKind;
import com.holeydigsite.contract.ContractManager;
import com.holeydigsite.crate.CrateBlock;
import com.holeydigsite.crate.CrateCollections;
import com.holeydigsite.crate.CrateDefs;
import com.holeydigsite.crate.CrateFinds;
import com.holeydigsite.crate.CrateRolls;
import com.holeydigsite.crate.PendingCrates;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.event.ShovelEvents;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.panning.Panning;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.AbstractPadBlock;
import com.holeydigsite.shop.MoneyPanBlock;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopButtonBlock;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import com.holeydigsite.skin.SkinWear;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crate collections, locked crates and Bolt Cutters, finds from the pan, sluices, drills and shovel, the crate switch and
 * what it changes, old crates, and rewards. Template {@code empty_long} (26x5x5).
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase30Tests {
    private static final BlockPos BTN = new BlockPos(4, 1, 2);
    private static final BlockPos SELL = new BlockPos(3, 1, 2);
    private static final BlockPos BUY = new BlockPos(5, 1, 2);
    private static final BlockPos MONEY = new BlockPos(4, 1, 3);

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
        h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        LedgerData.get(p).resetAll();
        LedgerData.get(p).setCratesOn(true);
        return p;
    }

    private static int count(Player p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static long coins(Player p) {
        long v = 0;
        for (ItemStack s : p.getInventory().items) v += MoneyPanBlock.coinValue(s);
        return v;
    }

    private static ItemStack locked(Item item) {
        return CrateBlock.stack(item, true);
    }

    private static boolean isLockedProspector(ItemStack s) {
        return s.is(ModItems.PROSPECTOR_CRATE.get()) && CrateBlock.isLocked(s);
    }

    private static int lockedOnGround(GameTestHelper h) {
        int n = 0;
        for (ItemEntity e : h.getEntities(EntityType.ITEM)) if (isLockedProspector(e.getItem())) n += e.getItem().getCount();
        return n;
    }

    private static boolean hasKey(List<net.minecraft.network.chat.Component> lines, String key) {
        for (net.minecraft.network.chat.Component c : lines) {
            if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc && tc.getKey().equals(key)) return true;
        }
        return false;
    }

    private static String lang(String key) {
        try (var in = Phase30Tests.class.getResourceAsStream("/assets/holeydigsite/lang/en_us.json")) {
            var obj = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            return obj.has(key) ? obj.get(key).getAsString() : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static BlockHitResult hit(GameTestHelper h, BlockPos rel, Direction face) {
        BlockPos abs = h.absolutePos(rel);
        return new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
    }

    // ------------------------------------------------------------------------------------------------ data

    @GameTest(template = "empty_long")
    public static void collectionsLoadFromData(GameTestHelper h) {
        CrateDefs.CrateTier p = CrateDefs.tier("prospector_crate");
        check(h, p != null && !p.old(), "prospector loaded and not old");
        check(h, p.name().equals("crate.holeydigsite.collection.prospector_crate") && p.look().equals("prospector"), "name and look");
        check(h, Math.abs(p.percent("common") - 80) < 1e-9 && Math.abs(p.percent("rare") - 16) < 1e-9
                && Math.abs(p.percent("epic") - 3) < 1e-9 && Math.abs(p.percent("legendary") - 1) < 1e-9, "odds 80/16/3/1");
        check(h, p.findOneIn("pan") == 2000 && p.findOneIn("sluice") == 1440 && p.findOneIn("drill") == 1440 && p.findOneIn("shovel") == 3000
                && p.site().isEmpty(), "finds");
        List<String> skins = new java.util.ArrayList<>();
        for (String r : CrateDefs.RARITIES) {
            for (CrateDefs.Entry e : p.pool(r)) {
                check(h, e.kind() == CrateDefs.Kind.SKIN || e.kind() == CrateDefs.Kind.TITLE || e.kind() == CrateDefs.Kind.ITEM,
                        "no coin or ingot filler in a collection");
                if (e.kind() == CrateDefs.Kind.ITEM) {
                    check(h, !e.id().endsWith("clump") && !e.id().endsWith("seeds") && !e.id().endsWith("ingot"), "no clump, seed or ingot filler: " + e.id());
                }
                if (e.kind() == CrateDefs.Kind.SKIN) skins.add(r + ":" + e.id());
            }
        }
        check(h, skins.equals(List.of("common:rustic", "common:rustic_weathered", "rare:brass", "rare:frosted", "epic:obsidian")), "skins " + skins);
        check(h, p.pool("legendary").size() == 2 && p.pool("legendary").get(0).id().equals("jackpot")
                && p.pool("legendary").get(1).id().equals("holeydigsite:golden_drill_statue"), "legendary is the Jackpot title and the statue");
        check(h, p.pool("common").size() == 4 && p.pool("rare").size() == 4 && p.pool("epic").size() == 3, "pool sizes");

        CrateDefs.CrateTier o = CrateDefs.tier("outback_crate");
        check(h, o != null && !o.old() && o.look().equals("outback") && o.found() && o.site().equals("event_island"), "outback is a collection found on the event island");
        check(h, o.pool("common").size() == 4 && o.pool("rare").size() == 5 && o.pool("epic").size() == 3 && o.pool("legendary").size() == 1,
                "outback contents");
        for (String old : List.of("wooden_crate", "iron_crate", "gold_crate")) {
            CrateDefs.CrateTier t = CrateDefs.tier(old);
            check(h, t != null && t.old() && !t.found(), old + " is old");
        }
        boolean filler = false;
        for (CrateDefs.Entry e : CrateDefs.tier("wooden_crate").pool("common")) filler |= e.kind() == CrateDefs.Kind.COINS || e.kind() == CrateDefs.Kind.INGOT;
        check(h, filler, "old crates keep their filler");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void oddsMayBeDecimals(GameTestHelper h) {
        CrateDefs.Entry common = new CrateDefs.Entry(CrateDefs.Kind.COINS, "", 64, 1);
        CrateDefs.Entry top = new CrateDefs.Entry(CrateDefs.Kind.TITLE, "jackpot", 1, 1);
        try {
            CrateDefs.setTierForTest(new CrateDefs.CrateTier("test_decimals", Map.of("common", 99.5, "rare", 0.0, "epic", 0.0, "legendary", 0.5), 0,
                    Map.of("common", List.of(common), "rare", List.of(), "epic", List.of(), "legendary", List.of(top))));
            CrateDefs.CrateTier t = CrateDefs.tier("test_decimals");
            check(h, Math.abs(t.percent("legendary") - 0.5) < 1e-9, "0.5 percent kept");
            RandomSource random = RandomSource.create(30L);
            int n = 400000, legendary = 0;
            for (int i = 0; i < n; i++) {
                if (CrateRolls.roll(t, random).rarity().equals("legendary")) legendary++;
            }
            check(h, Math.abs(legendary - n * 0.005) < n * 0.005 * 0.15, "0.5% over many rolls: " + legendary);
        } finally {
            CrateDefs.reloadBuiltin();
        }
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void commandsGiveOddsAndSwitch(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack src = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var cmds = h.getLevel().getServer().getCommands();
        cmds.performPrefixedCommand(src, "holeydigsite crate give @s prospector_crate 2 locked");
        cmds.performPrefixedCommand(src, "holeydigsite crate give @s prospector_crate 3");
        int lockedN = 0, plainN = 0;
        for (ItemStack s : p.getInventory().items) {
            if (!s.is(ModItems.PROSPECTOR_CRATE.get())) continue;
            if (CrateBlock.isLocked(s)) lockedN += s.getCount();
            else plainN += s.getCount();
        }
        check(h, lockedN == 2 && plainN == 3, "2 locked and 3 plain: " + lockedN + "/" + plainN);
        int odds;
        try {
            odds = cmds.getDispatcher().execute("holeydigsite crate odds prospector_crate", src);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            odds = -1;
        }
        check(h, odds == 1, "odds command works for a collection");
        cmds.performPrefixedCommand(src, "holeydigsite crate switch @s off");
        check(h, !LedgerData.get(p).cratesOn(), "switch command: off");
        cmds.performPrefixedCommand(src, "holeydigsite crate switch @s on");
        check(h, LedgerData.get(p).cratesOn(), "switch command: on");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ locked crates

    @GameTest(template = "empty_long")
    public static void lockedCrateNeedsCuttersAndUsesOne(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        ServerPlayer p = player(h);
        h.setBlock(pos, ModBlocks.PROSPECTOR_CRATE.get().defaultBlockState().setValue(CrateBlock.LOCKED, true));
        BlockHitResult hit = hit(h, pos, Direction.UP);
        BlockState st = h.getBlockState(pos);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        st.useWithoutItem(h.getLevel(), p, hit);
        check(h, !h.getBlockState(pos).getValue(CrateBlock.OPEN) && h.getBlockState(pos).getValue(CrateBlock.LOCKED), "an empty hand does not open it");
        ItemStack stick = new ItemStack(Items.STICK);
        ItemInteractionResult r = st.useItemOn(stick, h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
        check(h, r == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "other items fall through to the empty-hand message");
        check(h, h.getBlockState(pos).getValue(CrateBlock.LOCKED) && PendingCrates.get(h.getLevel().getServer()).at(h.getLevel(), h.absolutePos(pos)) == null,
                "still locked, nothing rolled");

        ItemStack cutters = new ItemStack(ModItems.BOLT_CUTTERS.get(), 3);
        p.setItemInHand(InteractionHand.MAIN_HAND, cutters);
        st.useItemOn(cutters, h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
        BlockState now = h.getBlockState(pos);
        check(h, now.getValue(CrateBlock.OPEN) && !now.getValue(CrateBlock.LOCKED), "cutters open it");
        check(h, cutters.getCount() == 2, "exactly one pair used: " + cutters.getCount());
        check(h, PendingCrates.get(h.getLevel().getServer()).at(h.getLevel(), h.absolutePos(pos)) != null, "the spin started on the server");
        // an open crate takes no more cutters
        now.useItemOn(cutters, h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
        check(h, cutters.getCount() == 2, "an open crate does not eat cutters");

        // an unlocked crate does not use them either
        BlockPos plain = new BlockPos(4, 1, 2);
        h.setBlock(plain, ModBlocks.PROSPECTOR_CRATE.get().defaultBlockState());
        ItemInteractionResult r2 = h.getBlockState(plain).useItemOn(cutters, h.getLevel(), p, InteractionHand.MAIN_HAND, hit(h, plain, Direction.UP));
        check(h, r2 == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && cutters.getCount() == 2, "an unlocked crate does not use cutters");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void lockedCratesBreakPlaceAndStackApart(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModBlocks.PROSPECTOR_CRATE.get().defaultBlockState().setValue(CrateBlock.LOCKED, true));
        p.gameMode.destroyBlock(h.absolutePos(pos));
        check(h, lockedOnGround(h) == 1, "breaking a locked crate gives it back locked");
        h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        h.setBlock(pos, ModBlocks.PROSPECTOR_CRATE.get().defaultBlockState());
        p.gameMode.destroyBlock(h.absolutePos(pos));
        boolean plainDrop = false;
        for (ItemEntity e : h.getEntities(EntityType.ITEM)) plainDrop |= e.getItem().is(ModItems.PROSPECTOR_CRATE.get()) && !CrateBlock.isLocked(e.getItem());
        check(h, plainDrop, "an unlocked crate drops unlocked");

        ItemStack a = locked(ModItems.PROSPECTOR_CRATE.get()), b = new ItemStack(ModItems.PROSPECTOR_CRATE.get());
        check(h, !ItemStack.isSameItemSameComponents(a, b), "locked and unlocked do not stack together");
        p.getInventory().add(a);
        p.getInventory().add(b);
        long stacks = p.getInventory().items.stream().filter(s -> s.is(ModItems.PROSPECTOR_CRATE.get())).count();
        check(h, stacks == 2, "two separate stacks: " + stacks);

        // placing a locked one keeps the lock on the block
        h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
        ItemStack held = locked(ModItems.PROSPECTOR_CRATE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos floor = new BlockPos(6, 0, 2);
        p.gameMode.useItemOn(p, h.getLevel(), held, InteractionHand.MAIN_HAND, hit(h, floor, Direction.UP));
        BlockState placed = h.getBlockState(new BlockPos(6, 1, 2));
        check(h, placed.is(ModBlocks.PROSPECTOR_CRATE.get()) && placed.getValue(CrateBlock.LOCKED), "placed locked");
        ItemStack plainHeld = new ItemStack(ModItems.PROSPECTOR_CRATE.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, plainHeld);
        p.gameMode.useItemOn(p, h.getLevel(), plainHeld, InteractionHand.MAIN_HAND, hit(h, new BlockPos(8, 0, 2), Direction.UP));
        check(h, !h.getBlockState(new BlockPos(8, 1, 2)).getValue(CrateBlock.LOCKED), "placed unlocked");
        // picking the block gives the lock back too
        ItemStack picked = placed.getBlock().getCloneItemStack(h.getLevel(), h.absolutePos(new BlockPos(6, 1, 2)), placed);
        check(h, CrateBlock.isLocked(picked), "pick block keeps the lock");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void lockedCratesSellSmallAndTrade(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        ItemStack lockedCrate = locked(ModItems.PROSPECTOR_CRATE.get());
        long price = Selling.unitValue(lockedCrate, reg);
        check(h, price == 48, "locked crate sells for 48 copper: " + price);
        check(h, Selling.unitValue(new ItemStack(ModItems.PROSPECTOR_CRATE.get()), reg) == 0, "an unlocked crate has no sell value");
        double share = price / 1536.0;
        check(h, share >= 0.02 && share <= 0.05, "a few percent of a cutter: " + share);
        check(h, Selling.unitValue(locked(ModItems.WOODEN_CRATE.get()), reg) == 48, "any locked crate sells the same");
        h.setBlock(new BlockPos(3, 1, 2), ModBlocks.SELL_PAD.get());
        check(h, ((AbstractPadBlock) ModBlocks.SELL_PAD.get()).accepts(lockedCrate, h.getLevel()), "the Sell Pad takes a locked crate");
        check(h, ModItems.BOLT_CUTTERS.get().getDefaultMaxStackSize() == 16, "cutters stack to 16");
        check(h, Selling.unitValue(new ItemStack(ModItems.BOLT_CUTTERS.get()), reg) == 0, "cutters do not sell back");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ finds

    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void handPanningFindsALockedCrate(GameTestHelper h) {
        clear(h);
        CrateFinds.setTestOneIn(1);
        try {
            BlockPos pan = new BlockPos(1, 1, 1);
            h.setBlock(pan, PanBlock.withFill(ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.WATERLOGGED, true), 4));
            ServerPlayer p = player(h);
            BlockPos abs = h.absolutePos(pan);
            p.moveTo(abs.getX() + 0.5, abs.getY() + 0.6, abs.getZ() + 0.5, 0, 90);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BRUSH.get()));
            check(h, Panning.tryStartPanning(h.getBlockState(pan), h.getLevel(), abs, p, InteractionHand.MAIN_HAND), "panning starts");
            Panning.tickPanning(h.getLevel(), p, Panning.cycleTicks(p, p.getMainHandItem()));
            check(h, lockedOnGround(h) >= 1, "a locked Prospector Crate popped out of the pan");
        } finally {
            CrateFinds.setTestOneIn(0);
        }
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void sluiceTraysFindCratesAndKeepThemWhenFull(GameTestHelper h) {
        clear(h);
        CrateFinds.setTestOneIn(1);
        try {
            ServerPlayer p = player(h);
            // Starter Sluice Box
            BlockPos sp = new BlockPos(3, 1, 3);
            h.setBlock(sp, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.NORTH));
            SluiceBoxBlockEntity starter = (SluiceBoxBlockEntity) h.getBlockEntity(sp);
            starter.setOwnerPlayer(p);
            starter.addClumps(1);
            starter.washOneClump(h.getLevel(), h.absolutePos(sp));
            check(h, starter.tray().stream().anyMatch(Phase30Tests::isLockedProspector), "the Starter Sluice tray holds a locked crate");

            // a full tray keeps the find (it goes past the slot count like any burst of loot)
            starter.takeTray();
            int slots = SluiceBoxBlockEntity.TRAY_SLOTS;
            int filled = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                if (filled >= slots) break;
                if (item == Items.AIR || item == ModItems.PROSPECTOR_CRATE.get()) continue;
                starter.addToTray(new ItemStack(item));
                filled++;
            }
            check(h, starter.isTrayFull(), "tray is full");
            starter.addClumps(1);
            starter.washOneClump(h.getLevel(), h.absolutePos(sp));
            check(h, starter.tray().stream().anyMatch(Phase30Tests::isLockedProspector), "a full tray still keeps the crate");

            // Long sluice (Tier 2)
            ItemStack item = new ItemStack(ModBlocks.SLUICE_BOX_T2.get().asItem(), 1);
            p.setItemInHand(InteractionHand.MAIN_HAND, item);
            p.gameMode.useItemOn(p, h.getLevel(), item, InteractionHand.MAIN_HAND, hit(h, new BlockPos(10, 0, 3), Direction.UP));
            BlockPos back = new BlockPos(10, 1, 3);
            SluiceBoxBlockEntity longSluice = (SluiceBoxBlockEntity) h.getBlockEntity(back);
            check(h, longSluice != null, "long sluice placed");
            longSluice.setOwnerPlayer(p);
            longSluice.addClumps(1);
            longSluice.washOneClump(h.getLevel(), h.absolutePos(back));
            check(h, longSluice.tray().stream().anyMatch(Phase30Tests::isLockedProspector), "the long sluice tray holds a locked crate");
        } finally {
            CrateFinds.setTestOneIn(0);
        }
        h.succeed();
    }

    private static MiningDrillBlockEntity drillAt(GameTestHelper h, BlockPos drill, Direction facing, ServerPlayer owner) {
        h.setBlock(new BlockPos(drill.getX(), 0, drill.getZ()), ModBlocks.DIRTROCK.get());
        h.setBlock(drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, facing));
        MiningDrillBlockEntity be = (MiningDrillBlockEntity) h.getBlockEntity(drill);
        be.setOwner(owner.getUUID());
        return be;
    }

    private static void cycles(GameTestHelper h, BlockPos drill, MiningDrillBlockEntity be, int n) {
        BlockPos abs = h.absolutePos(drill);
        for (int i = 0; i < n * (MiningDrillBlock.CYCLE_TICKS + 2); i++) {
            MiningDrillBlockEntity.serverTick(h.getLevel(), abs, h.getLevel().getBlockState(abs), be);
        }
    }

    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void drillIntoAPanHoldsTheCrateAndKeepsMakingClumps(GameTestHelper h) {
        clear(h);
        CrateFinds.setTestOneIn(1);
        try {
            ServerPlayer p = player(h);
            BlockPos drill = new BlockPos(2, 1, 2), front = new BlockPos(3, 1, 2);
            MiningDrillBlockEntity be = drillAt(h, drill, Direction.EAST, p);
            h.setBlock(front, ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 0));
            cycles(h, drill, be, 1);
            check(h, PanBlock.fill(h.getBlockState(front)) == 1, "the clump went into the pan");
            check(h, isLockedProspector(be.heldCrate()), "the find is held in the drill");
            long rolls = CrateFinds.rolls();
            cycles(h, drill, be, 2);
            check(h, PanBlock.fill(h.getBlockState(front)) >= 3, "the drill kept making clumps with a crate waiting: " + PanBlock.fill(h.getBlockState(front)));
            check(h, !be.isOutputBlocked(), "the waiting crate does not block the drill");
            check(h, h.getBlockState(drill).getValue(MiningDrillBlock.STATUS) != com.holeydigsite.drill.DrillStatus.STOPPED, "and does not stop it");
            check(h, CrateFinds.rolls() == rolls, "no new roll while a crate is held");
            check(h, isLockedProspector(be.heldCrate()) && be.heldCrate().getCount() == 1, "still one crate");
            check(h, h.getEntities(EntityType.ITEM).isEmpty(), "no item appears in the world");

            var reg = h.getLevel().registryAccess();
            var tag = be.saveWithFullMetadata(reg);
            be.setHeldCrate(ItemStack.EMPTY);
            be.loadCustomOnly(tag, reg);
            check(h, isLockedProspector(be.heldCrate()), "the waiting crate survives save and load");

            BlockHitResult hit = hit(h, drill, Direction.UP);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            h.getBlockState(drill.above()).useWithoutItem(h.getLevel(), p, hit(h, drill.above(), Direction.UP));
            check(h, be.heldCrate().isEmpty(), "the upper half hands the crate over");
            int got = 0;
            for (ItemStack st : p.getInventory().items) if (isLockedProspector(st)) got += st.getCount();
            check(h, got == 1, "the player has the locked crate: " + got);
            h.getBlockState(drill).useWithoutItem(h.getLevel(), p, hit);
            check(h, h.getEntities(EntityType.ITEM).isEmpty(), "a second click only shows the status");

        } finally {
            CrateFinds.setTestOneIn(0);
        }
        h.succeed();
    }

    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void drillIntoASluicePutsTheCrateInTheTray(GameTestHelper h) {
        clear(h);
        CrateFinds.setTestOneIn(1);
        try {
            ServerPlayer p = player(h);
            BlockPos sluice = new BlockPos(3, 1, 2), drill = new BlockPos(3, 1, 3);
            h.setBlock(sluice, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.NORTH));
            SluiceBoxBlockEntity be = (SluiceBoxBlockEntity) h.getBlockEntity(sluice);
            MiningDrillBlockEntity d = drillAt(h, drill, Direction.NORTH, p);
            cycles(h, drill, d, 1);
            check(h, be.queued() == 1, "the clump is in the sluice");
            // the direct ticks above do not advance game time, so the drill's look for the waiting crate only happens when the clock lines up;
            // wait for a game tick on which it does, then give the drill one more tick
            long now = h.getLevel().getGameTime();
            int wait = (int) ((MiningDrillBlock.CHECK_INTERVAL - now % MiningDrillBlock.CHECK_INTERVAL) % MiningDrillBlock.CHECK_INTERVAL) + MiningDrillBlock.CHECK_INTERVAL;
            h.runAfterDelay(wait, () -> {
                MiningDrillBlockEntity.serverTick(h.getLevel(), h.absolutePos(drill), h.getLevel().getBlockState(h.absolutePos(drill)), d);
                check(h, d.heldCrate().isEmpty(), "the crate did not wait: the sluice took it on the next look");
                check(h, be.tray().stream().anyMatch(Phase30Tests::isLockedProspector), "the crate went through to the tray untouched");
                check(h, h.getEntities(EntityType.ITEM).isEmpty(), "no item appears in the world");
                h.succeed();
            });
        } finally {
            CrateFinds.setTestOneIn(0);
        }
    }

    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void drillIntoABeltSendsTheCrateOutBetweenClumps(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos drill = new BlockPos(2, 1, 2), front = new BlockPos(3, 1, 2);
        MiningDrillBlockEntity be = drillAt(h, drill, Direction.EAST, p);
        h.setBlock(front, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(com.holeydigsite.belt.ClumpBeltBlock.FACING, Direction.EAST));
        TestWater.underBelt(h, front);
        be.setHeldCrate(locked(ModItems.PROSPECTOR_CRATE.get()));
        be.setProgressForTest(0);
        BlockPos abs = h.absolutePos(drill);
        boolean[] sent = {false};
        h.onEachTick(() -> {
            if (sent[0]) return;
            MiningDrillBlockEntity.serverTick(h.getLevel(), abs, h.getLevel().getBlockState(abs), be);
            if (be.heldCrate().isEmpty()) {
                sent[0] = true;
                var belt = (com.holeydigsite.belt.ClumpBeltBlockEntity) h.getBlockEntity(front);
                check(h, belt.items().stream().anyMatch(Phase30Tests::isLockedProspector), "the crate is on the belt");
                check(h, h.getEntities(EntityType.ITEM).isEmpty(), "no item in the world");
            }
        });
        h.runAfterDelay(60, () -> {
            check(h, sent[0], "the crate went out onto the belt while the drill was still mid-cycle");
            h.succeed();
        });
    }

    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void breakingADrillDropsTheWaitingCrateOnce(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos drill = new BlockPos(2, 1, 2);
        MiningDrillBlockEntity be = drillAt(h, drill, Direction.EAST, p);
        be.setHeldCrate(locked(ModItems.PROSPECTOR_CRATE.get()));
        p.gameMode.destroyBlock(h.absolutePos(drill));
        int crates = 0, drills = 0;
        for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
            if (isLockedProspector(e.getItem())) crates += e.getItem().getCount();
            if (e.getItem().is(ModItems.MINING_DRILL.get())) drills += e.getItem().getCount();
        }
        check(h, crates == 1 && drills == 1, "the drill and the crate dropped once: " + drills + "/" + crates);
        check(h, CrateFinds.drillFind(h.getLevel(), h.absolutePos(drill), null).isEmpty(), "an ownerless drill finds nothing");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void shovelScoopsFindACrate(GameTestHelper h) {
        clear(h);
        CrateFinds.setTestOneIn(1);
        try {
            ServerPlayer p = player(h);
            BlockPos pos = new BlockPos(2, 1, 2);
            h.setBlock(pos, Blocks.DIRT);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(h.getLevel(), h.absolutePos(pos), h.getBlockState(pos), p);
            ShovelEvents.onBlockBreak(event);
            check(h, event.isCanceled(), "the shovel took the scoop");
            check(h, lockedOnGround(h) == 1, "a locked crate dropped from the scoop");
            // a block that gives no clump finds nothing
            h.getEntities(EntityType.ITEM).forEach(ItemEntity::discard);
            h.setBlock(pos, Blocks.STONE);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
            ShovelEvents.onBlockBreak(new BlockEvent.BreakEvent(h.getLevel(), h.absolutePos(pos), h.getBlockState(pos), p));
            check(h, lockedOnGround(h) == 0, "stone gives no find");
        } finally {
            CrateFinds.setTestOneIn(0);
        }
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void withTheSwitchOffNothingIsFound(GameTestHelper h) {
        clear(h);
        CrateFinds.setTestOneIn(1);
        try {
            ServerPlayer on = player(h);
            ServerPlayer off = player(h);
            LedgerData.get(off).setCratesOn(false);
            BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
            check(h, !CrateFinds.roll(h.getLevel(), at, "pan", on).isEmpty(), "switch on: a find");
            check(h, CrateFinds.roll(h.getLevel(), at, "pan", off).isEmpty(), "switch off: nothing for the player");
            check(h, CrateFinds.roll(h.getLevel(), at, "shovel", off).isEmpty(), "switch off: no shovel find");
            check(h, CrateFinds.roll(h.getLevel(), at, "pan", null).isEmpty(), "no owner: nothing");
            // a machine uses its owner's switch
            BlockPos sp = new BlockPos(3, 1, 3);
            h.setBlock(sp, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.NORTH));
            SluiceBoxBlockEntity sluice = (SluiceBoxBlockEntity) h.getBlockEntity(sp);
            sluice.setOwnerPlayer(off);
            sluice.addClumps(1);
            sluice.washOneClump(h.getLevel(), h.absolutePos(sp));
            check(h, sluice.tray().stream().noneMatch(Phase30Tests::isLockedProspector), "a sluice owned by a player with the switch off finds none");
            sluice.takeTray();
            sluice.setOwner(null);
            sluice.addClumps(1);
            sluice.washOneClump(h.getLevel(), h.absolutePos(sp));
            check(h, sluice.tray().stream().noneMatch(Phase30Tests::isLockedProspector), "an ownerless sluice finds none");
            sluice.takeTray();
            sluice.setOwnerPlayer(on);
            sluice.addClumps(1);
            sluice.washOneClump(h.getLevel(), h.absolutePos(sp));
            check(h, sluice.tray().stream().anyMatch(Phase30Tests::isLockedProspector), "the same sluice owned by a player with the switch on finds one");
        } finally {
            CrateFinds.setTestOneIn(0);
        }
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void findRatesFollowTheDataWithoutTheTestRate(GameTestHelper h) {
        ServerPlayer p = player(h);
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        int n = 300000, hits = 0;
        for (int i = 0; i < n; i++) {
            if (!CrateFinds.roll(h.getLevel(), at, "sluice", p).isEmpty()) hits++;
        }
        double expected = n / 1440.0;
        check(h, Math.abs(hits - expected) < expected * 0.2, "sluice finds 1 in 1440: " + hits + " vs " + expected);
        // a plot-specific crate only turns up on its site
        try {
            CrateDefs.setTierForTest(new CrateDefs.CrateTier("test_site", "x", "x", false, Map.of("common", 1.0, "rare", 0.0, "epic", 0.0, "legendary", 0.0),
                    Map.of("pan", 1), "holeydigsite:nowhere", Map.of("common", List.of(new CrateDefs.Entry(CrateDefs.Kind.TITLE, "jackpot", 1, 1)),
                    "rare", List.of(), "epic", List.of(), "legendary", List.of())));
            CrateFinds.setTestOneIn(1);
            int site = 0;
            for (int i = 0; i < 50; i++) {
                ItemStack s = CrateFinds.roll(h.getLevel(), at, "pan", p);
                if (s.getItem() == BuiltInRegistries.ITEM.get(HoleyDigsite.id("test_site"))) site++;
            }
            check(h, site == 0, "a site-limited collection is not found outside its site");
        } finally {
            CrateFinds.setTestOneIn(0);
            CrateDefs.reloadBuiltin();
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ the switch

    @GameTest(template = "empty_long")
    public static void theSwitchTogglesPerPlayerAndSurvivesRelogAndDeath(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModBlocks.CRATE_SWITCH.get());
        ServerPlayer a = player(h), b = player(h);
        BlockHitResult hit = hit(h, pos, Direction.UP);
        check(h, LedgerData.get(a).cratesOn() && LedgerData.get(b).cratesOn(), "on by default (server config default_on is true)");
        h.getBlockState(pos).useWithoutItem(h.getLevel(), a, hit);
        check(h, !LedgerData.get(a).cratesOn() && LedgerData.get(b).cratesOn(), "only the player who used it is switched");
        h.getBlockState(pos).useWithoutItem(h.getLevel(), a, hit);
        check(h, LedgerData.get(a).cratesOn(), "and back on");
        h.getBlockState(pos).useWithoutItem(h.getLevel(), a, hit);
        // relog: the data is saved to NBT and read back
        var reg = h.getLevel().registryAccess();
        LedgerData read = new LedgerData();
        read.deserializeNBT(reg, LedgerData.get(a).serializeNBT(reg));
        check(h, !read.cratesOn(), "the state survives a save and load");
        // death: copyOnDeath
        LedgerData copy = new LedgerData();
        copy.copyFrom(LedgerData.get(a));
        check(h, !copy.cratesOn(), "the state survives death");
        // a player who never used it follows the server default (on in tests)
        LedgerData fresh = new LedgerData();
        check(h, fresh.cratesOn(), "default_on");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void withTheSwitchOffCutterPurchasesAreRefused(GameTestHelper h) {
        clear(h);
        h.setBlock(BTN, ModBlocks.SHOP_BUTTON.get());
        h.setBlock(SELL, ModBlocks.SELL_PAD.get());
        h.setBlock(BUY, ModBlocks.BUY_PAD.get());
        h.setBlock(MONEY, ModBlocks.MONEY_PAN.get());
        ServerPlayer p = player(h);
        ((MoneyPanBlockEntity) h.getBlockEntity(MONEY)).deposit(10000);
        ((PadBlockEntity) h.getBlockEntity(BUY)).insert(BlueprintItem.of(ModItems.BOLT_CUTTERS.get(), 2));
        LedgerData.get(p).setCratesOn(false);
        ShopButtonBlock.Result r = ShopButtonBlock.press(h.getLevel(), h.absolutePos(BTN), p);
        check(h, r.spent() == 0 && count(p, ModItems.BOLT_CUTTERS.get()) == 0, "nothing bought with the switch off");
        check(h, !((PadBlockEntity) h.getBlockEntity(BUY)).isEmpty(), "the blueprints stay on the pad");
        check(h, ((MoneyPanBlockEntity) h.getBlockEntity(MONEY)).balance() == 10000, "no coins taken");
        // other things still sell
        ((PadBlockEntity) h.getBlockEntity(BUY)).takeAll();
        ((PadBlockEntity) h.getBlockEntity(BUY)).insert(BlueprintItem.of(ModItems.PAN.get(), 1));
        ShopButtonBlock.Result r2 = ShopButtonBlock.press(h.getLevel(), h.absolutePos(BTN), p);
        check(h, r2.boughtStacks() == 1, "other purchases still work with the switch off");
        // switch back on: cutters sell
        LedgerData.get(p).setCratesOn(true);
        h.setBlock(BTN, ModBlocks.SHOP_BUTTON.get());
        ((PadBlockEntity) h.getBlockEntity(BUY)).insert(BlueprintItem.of(ModItems.BOLT_CUTTERS.get(), 2));
        ShopButtonBlock.Result r3 = ShopButtonBlock.press(h.getLevel(), h.absolutePos(BTN), p);
        check(h, r3.spent() == 2 * 1536 && count(p, ModItems.BOLT_CUTTERS.get()) == 2, "with the switch on: two cutters for 3072 copper: " + r3);
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void withTheSwitchOffRewardsPayCoinsAndNothingIsTaken(GameTestHelper h) {
        ServerPlayer p = player(h);
        LedgerData data = LedgerData.get(p);
        // things the player already has
        ItemStack ownedCrate = locked(ModItems.PROSPECTOR_CRATE.get());
        p.getInventory().add(ownedCrate);
        p.getInventory().add(new ItemStack(ModItems.BOLT_CUTTERS.get(), 2));
        p.getInventory().add(SkinKitItem.of("brass", SkinDefs.DRILL, SkinWear.FRESH, 1));
        data.setCratesOn(false);
        check(h, count(p, ModItems.PROSPECTOR_CRATE.get()) == 1 && count(p, ModItems.BOLT_CUTTERS.get()) == 2, "owned items stay");

        data.setRenown(LedgerDefs.curve().cumulativeForLevel(36));
        long before = coins(p);
        check(h, LedgerManager.claim(p, 19), "claim 19");
        check(h, count(p, ModItems.PROSPECTOR_CRATE.get()) == 1 && coins(p) - before == 1536, "L19 pays 1536 copper instead of a crate: " + (coins(p) - before));
        before = coins(p);
        check(h, LedgerManager.claim(p, 36), "claim 36");
        check(h, count(p, ModItems.PROSPECTOR_CRATE.get()) == 1 && coins(p) - before == 3072, "L36 pays 3072 copper (two crates)");
        before = coins(p);
        data.setRenown(LedgerDefs.curve().cumulativeForLevel(13));
        int kitsBefore = p.getInventory().items.stream().filter(SkinKitItem::isKit).mapToInt(ItemStack::getCount).sum();
        check(h, LedgerManager.claim(p, 13), "claim 13");
        int kitsAfter = p.getInventory().items.stream().filter(SkinKitItem::isKit).mapToInt(ItemStack::getCount).sum();
        check(h, kitsAfter == kitsBefore && coins(p) - before == 768, "L13 skin pays its Fresh scrap value (768) and no kit: " + (coins(p) - before));
        // back on: rewards are normal again
        data.setCratesOn(true);
        data.setRenown(LedgerDefs.curve().cumulativeForLevel(49));
        check(h, LedgerManager.claim(p, 49), "claim 49");
        check(h, count(p, ModItems.PROSPECTOR_CRATE.get()) == 1 + 4, "L49 gives four crates with the switch on");

        // contracts
        ServerPlayer q = player(h);
        LedgerData.get(q).setCratesOn(false);
        long seed = 0;
        while (ContractManager.crateFor(9000, RandomSource.create(seed)).isEmpty()) seed++;
        ItemStack expected = ContractManager.crateFor(9000, RandomSource.create(seed));
        Contract c = new Contract("t", ContractKind.FOOD, BuiltInRegistries.ITEM.getKey(Items.WHEAT), "", null, null, 1, 0, 9000, 20, 0);
        ContractManager.data(q).set(0, c);
        c.markDone();
        ContractManager.complete(q, c, RandomSource.create(seed));
        check(h, count(q, ModItems.PROSPECTOR_CRATE.get()) == 0 && coins(q) == 9000 + expected.getCount() * 1536L,
                "contract pays coins instead of the crate: " + coins(q));
        h.succeed();
    }

    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void withTheSwitchOffACrateYouHaveStillOpens(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        LedgerData.get(p).setCratesOn(false);
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModBlocks.PROSPECTOR_CRATE.get().defaultBlockState().setValue(CrateBlock.LOCKED, true));
        ItemStack cutters = new ItemStack(ModItems.BOLT_CUTTERS.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, cutters);
        h.getBlockState(pos).useItemOn(cutters, h.getLevel(), p, InteractionHand.MAIN_HAND, hit(h, pos, Direction.UP));
        check(h, h.getBlockState(pos).getValue(CrateBlock.OPEN) && cutters.isEmpty(), "a crate and cutters you hold still work with the switch off");
        h.runAfterDelay(CrateBlock.OPEN_TICKS + 5, () -> {
            check(h, h.getBlockState(pos).isAir(), "the crate is used up");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ old crates and collections

    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void oldCratesStillOpenWithTheirOldPools(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModBlocks.WOODEN_CRATE.get());
        BlockState st = h.getBlockState(pos);
        check(h, !st.getValue(CrateBlock.LOCKED), "an old crate is unlocked");
        st.useWithoutItem(h.getLevel(), p, hit(h, pos, Direction.UP));
        check(h, h.getBlockState(pos).getValue(CrateBlock.OPEN), "an old Wooden Crate opens with an empty hand");
        // the old pools still hold filler, and the rolls come from them
        RandomSource random = RandomSource.create(7L);
        boolean filler = false;
        for (int i = 0; i < 400 && !filler; i++) {
            CrateRolls.Roll r = CrateRolls.roll(CrateDefs.tier("wooden_crate"), random);
            filler = r.entry().kind() == CrateDefs.Kind.COINS || r.entry().kind() == CrateDefs.Kind.INGOT || r.entry().kind() == CrateDefs.Kind.ITEM
                    && (r.entry().id().endsWith("clump") || r.entry().id().endsWith("seeds"));
        }
        check(h, filler, "old pools still give filler");
        var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        ModBlocks.WOODEN_CRATE.get().appendHoverText(new ItemStack(ModItems.WOODEN_CRATE.get()), Item.TooltipContext.EMPTY, tooltip,
                net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        check(h, hasKey(tooltip, "tooltip.holeydigsite.crate.old"), "an Old crate line in the tooltip");
        h.runAfterDelay(CrateBlock.OPEN_TICKS + 5, () -> {
            check(h, h.getBlockState(pos).isAir(), "used up");
            h.succeed();
        });
    }

    @GameTest(template = "empty_long")
    public static void collectionTooltipListsEveryItemWithOdds(GameTestHelper h) {
        var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        ModBlocks.PROSPECTOR_CRATE.get().appendHoverText(locked(ModItems.PROSPECTOR_CRATE.get()), Item.TooltipContext.EMPTY, tooltip,
                net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        String all = String.join("\n", tooltip.stream().map(net.minecraft.network.chat.Component::getString).toList());
        for (String r : CrateDefs.RARITIES) {
            check(h, all.contains(net.minecraft.network.chat.Component.translatable("rarity.holeydigsite." + r).getString()), "rarity " + r + " listed");
        }
        check(h, all.contains("80%") && all.contains("16%") && all.contains("3%") && all.contains("1%"), "odds listed: " + all);
        CrateDefs.CrateTier t = CrateDefs.tier("prospector_crate");
        for (String r : CrateDefs.RARITIES) {
            for (CrateDefs.Entry e : t.pool(r)) {
                check(h, all.contains(CrateCollections.entryName(e).getString()), "item listed: " + e.id());
            }
        }
        check(h, hasKey(tooltip, "tooltip.holeydigsite.crate.locked") && hasKey(tooltip, "tooltip.holeydigsite.crate.collection"), "locked and collection lines");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void collectionsTrackWhatCratesGaveAndWhatYouOwn(GameTestHelper h) {
        ServerPlayer p = player(h);
        CrateDefs.CrateTier t = CrateDefs.tier("prospector_crate");
        CrateDefs.Entry rustic = t.pool("common").get(0);
        CrateDefs.Entry statue = t.pool("legendary").get(1);
        check(h, !CrateCollections.has(p, t.id(), rustic) && !CrateCollections.has(p, t.id(), statue), "nothing ticked at the start");
        CrateRolls.grant(p, new CrateRolls.Roll("prospector_crate", "common", "common", rustic, SkinWear.FRESH));
        check(h, CrateCollections.has(p, t.id(), rustic), "a skin from a crate is ticked");
        CrateRolls.grant(p, new CrateRolls.Roll("prospector_crate", "legendary", "legendary", statue, null));
        check(h, CrateCollections.has(p, t.id(), statue), "decor from a crate is ticked");
        CrateDefs.Entry title = t.pool("common").get(2);
        check(h, !CrateCollections.has(p, t.id(), title), "title not yet");
        LedgerData.get(p).unlockTitle("lucky_strike");
        check(h, CrateCollections.has(p, t.id(), title), "an owned title counts, from any source");
        check(h, CrateCollections.have(p, t) == 3 && CrateCollections.total(t) == 13, "3 of 13: " + CrateCollections.have(p, t) + "/" + CrateCollections.total(t));
        // old worlds start from what the player carries
        ServerPlayer q = player(h);
        q.getInventory().add(SkinKitItem.of("obsidian", SkinDefs.DRILL, SkinWear.FRESH, 1));
        q.getInventory().add(new ItemStack(ModItems.RUSTY_MINE_BELL.get()));
        LedgerData.get(q).setCollectionsScanned(false);
        CrateCollections.scanOnce(q);
        CrateDefs.Entry obsidian = t.pool("epic").get(0), bell = t.pool("rare").get(3);
        check(h, CrateCollections.has(q, t.id(), obsidian) && CrateCollections.has(q, t.id(), bell), "the first-login scan ticks owned kits and decor");
        check(h, LedgerData.get(q).collectionsScanned(), "scanned once");
        // the saved list survives NBT
        var reg = h.getLevel().registryAccess();
        LedgerData read = new LedgerData();
        read.deserializeNBT(reg, LedgerData.get(p).serializeNBT(reg));
        check(h, read.collected().equals(LedgerData.get(p).collected()), "collection ticks survive save and load");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void ledgerLevelsAndContractsGiveUnlockedProspectorCrates(GameTestHelper h) {
        ServerPlayer p = player(h);
        LedgerData.get(p).setRenown(LedgerDefs.curve().cumulativeForLevel(49));
        check(h, LedgerManager.claim(p, 19) && LedgerManager.claim(p, 36) && LedgerManager.claim(p, 49), "claims");
        int unlocked = 0, lockedN = 0;
        for (ItemStack s : p.getInventory().items) {
            if (!s.is(ModItems.PROSPECTOR_CRATE.get())) continue;
            if (CrateBlock.isLocked(s)) lockedN += s.getCount();
            else unlocked += s.getCount();
        }
        check(h, unlocked == 1 + 2 + 4 && lockedN == 0, "1 + 2 + 4 unlocked crates: " + unlocked + "/" + lockedN);
        check(h, count(p, ModItems.WOODEN_CRATE.get()) + count(p, ModItems.IRON_CRATE.get()) + count(p, ModItems.GOLD_CRATE.get()) == 0, "no old crates given");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void boltCuttersAreNotInCratesContractsOrLedgerRewards(GameTestHelper h) {
        for (CrateDefs.CrateTier t : CrateDefs.tiers().values()) {
            for (String r : CrateDefs.RARITIES) {
                for (CrateDefs.Entry e : t.pool(r)) {
                    check(h, !e.id().contains("bolt_cutters"), "no cutters in " + t.id());
                }
            }
        }
        for (int lvl = 1; lvl <= LedgerDefs.MAX_LEVEL; lvl++) {
            var def = LedgerDefs.level(lvl);
            if (def == null) continue;
            for (var rw : def.rewards()) {
                check(h, rw.item().isEmpty() || !rw.item().get().getPath().contains("bolt_cutters"), "no cutters in Ledger level " + lvl);
            }
        }
        for (int i = 0; i < 2000; i++) {
            ItemStack s = ContractManager.crateFor(9000, RandomSource.create(i));
            check(h, s.isEmpty() || !s.is(ModItems.BOLT_CUTTERS.get()), "no cutters from contracts");
        }
        check(h, com.holeydigsite.shop.ShopCatalogue.find(h.getLevel().registryAccess(), ModItems.BOLT_CUTTERS.get()).isPresent(), "bought at the Trading Post");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void messagesFitTheActionBar(GameTestHelper h) {
        for (String key : List.of("message.holeydigsite.crate.locked", "message.holeydigsite.crate_switch.on", "message.holeydigsite.crate_switch.off",
                "message.holeydigsite.crate_switch.paid", "message.holeydigsite.button.crates_off")) {
            String raw = lang(key);
            check(h, raw != null, "lang for " + key);
            String text = raw.replace("%s", "1g 20s");
            check(h, text.length() <= 55, key + " is " + text.length() + " characters");
        }
        h.succeed();
    }
}
