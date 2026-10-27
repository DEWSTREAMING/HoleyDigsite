package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.compat.jei.display.CrateOddsRow;
import com.holeydigsite.crate.CrateDefs;
import com.holeydigsite.crate.CrateRolls;
import com.holeydigsite.crate.PendingCrates;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.skin.SkinBenchActions;
import com.holeydigsite.skin.SkinBenchBlockEntity;
import com.holeydigsite.skin.SkinBenchNetwork;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import com.holeydigsite.skin.SkinKitMigration;
import com.holeydigsite.skin.SkinWear;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Skin Kits: the item, the bench (apply, remove, swap, wrong family, scrap with a confirm), a skin never duplicated or lost
 * (full inventory, break, place, death drops, two players), crate skin rolls as kits with an even family split, title
 * duplicates still scrap, Ledger drill kits, the old-unlock migration (once), commands, saved crate rolls, the JEI crate row.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase26Tests {

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

    private static ServerPlayer fresh(GameTestHelper h, BlockPos near) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        LedgerData.get(p).resetAll();
        BlockPos abs = h.absolutePos(near);
        p.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 1.5, 0, 0);
        return p;
    }

    private static int kits(Player p, String skin, String family, String wear) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (SkinKitItem.matches(s, skin, family, wear)) n += s.getCount();
        }
        return n;
    }

    private static int allKits(Player p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (SkinKitItem.isKit(s)) n += s.getCount();
        }
        return n;
    }

    private static int count(Player p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static boolean skinned(ItemStack s) {
        String skin = s.get(ModDataComponents.SKIN.get());
        return skin != null && !skin.isEmpty() && !SkinDefs.DEFAULT_ID.equals(skin) && !s.is(ModItems.SKIN_KIT.get());
    }

    /** Every skin around: kits and skinned machine items in inventories, on the bench and lying on the ground nearby. */
    private static int skinsInWorld(GameTestHelper h, List<ServerPlayer> players, SkinBenchBlockEntity bench) {
        int n = 0;
        for (ServerPlayer p : players) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (SkinKitItem.isKit(s)) n += s.getCount();
                else if (skinned(s)) n += s.getCount();
            }
        }
        if (bench != null && bench.hasItem() && skinned(bench.getItem())) n++;
        AABB box = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(16);
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            ItemStack s = e.getItem();
            if (SkinKitItem.isKit(s) || skinned(s)) n += s.getCount();
        }
        return n;
    }

    private static SkinBenchBlockEntity bench(GameTestHelper h, BlockPos pos, ItemStack machine) {
        h.setBlock(pos, ModBlocks.SKIN_BENCH.get().defaultBlockState());
        SkinBenchBlockEntity bench = (SkinBenchBlockEntity) h.getBlockEntity(pos);
        if (!machine.isEmpty()) bench.setItem(machine);
        return bench;
    }

    /** A right-click on the bench through the block itself, as a player would. */
    private static void use(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BlockState state = h.getLevel().getBlockState(abs);
        var r = state.useItemOn(p.getMainHandItem(), h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
        if (r == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && p.getMainHandItem().isEmpty()) {
            state.useWithoutItem(h.getLevel(), p, hit);
        }
    }

    /** A sneak-use with the held kit on the bench (sneaking skips the block, so it goes to the item). */
    private static void sneakUse(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos abs = h.absolutePos(pos);
        p.setShiftKeyDown(true);
        p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        p.setShiftKeyDown(false);
    }

    @GameTest(template = "empty")
    public static void kitItemNameStackAndScrapValue(GameTestHelper h) {
        ItemStack brass = SkinKitItem.of("brass", SkinDefs.DRILL, SkinWear.FRESH, 1);
        check(h, "brass".equals(brass.get(ModDataComponents.SKIN.get())) && SkinDefs.DRILL.equals(brass.get(ModDataComponents.SKIN_FAMILY.get())),
                "kit carries skin and family");
        check(h, !brass.has(ModDataComponents.SKIN_WEAR.get()), "Fresh is stored as no wear component");
        check(h, "Brass Drill Skin".equals(brass.getHoverName().getString()), "name, got " + brass.getHoverName().getString());
        check(h, "Frosted Sluice Skin".equals(SkinKitItem.of("frosted", SkinDefs.SLUICE, "field_worn", 1).getHoverName().getString()), "sluice name");
        check(h, ItemStack.isSameItemSameComponents(brass, SkinKitItem.of("brass", SkinDefs.DRILL, null, 1)), "same kit stacks");
        check(h, !ItemStack.isSameItemSameComponents(brass, SkinKitItem.of("brass", SkinDefs.DRILL, "field_worn", 1)), "other wear does not stack");
        check(h, !ItemStack.isSameItemSameComponents(brass, SkinKitItem.of("brass", SkinDefs.SLUICE, null, 1)), "other family does not stack");
        check(h, SkinKitItem.scrapValue(SkinKitItem.of("obsidian", SkinDefs.DRILL, "pit_scarred", 1)) == 6, "epic pit scarred scraps for 6");
        check(h, SkinKitItem.scrapValue(SkinKitItem.of("rustic", SkinDefs.SLUICE, "cave_battered", 1)) == 1, "never below 1");
        check(h, !SkinKitItem.isKit(new ItemStack(ModItems.SKIN_KIT.get())), "a blank kit is not a kit");
        check(h, SkinKitItem.machineIcon(SkinKitItem.of("frosted", SkinDefs.SLUICE, "field_worn", 1)).is(ModItems.SLUICE_BOX.get()),
                "sluice kit icon is a sluice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void benchAppliesRemovesAndSwaps(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        SkinBenchBlockEntity bench = bench(h, pos, new ItemStack(ModItems.MINING_DRILL.get()));
        ServerPlayer p = fresh(h, pos);
        p.setItemInHand(InteractionHand.MAIN_HAND, SkinKitItem.of("brass", SkinDefs.DRILL, "field_worn", 2));
        use(h, p, pos);
        check(h, "brass".equals(bench.getItem().get(ModDataComponents.SKIN.get())) && "field_worn".equals(SkinWear.of(bench.getItem())),
                "drill wears brass, field worn");
        check(h, p.getMainHandItem().getCount() == 1, "one kit used up");
        // the same skin again changes nothing
        use(h, p, pos);
        check(h, p.getMainHandItem().getCount() == 1, "same skin and wear is refused, kit kept");
        // swap: frosted on, brass comes back as a kit
        p.setItemInHand(InteractionHand.MAIN_HAND, SkinKitItem.of("frosted", SkinDefs.DRILL, SkinWear.FRESH, 1));
        p.getInventory().setItem(8, SkinKitItem.of("brass", SkinDefs.DRILL, "field_worn", 1));
        use(h, p, pos);
        check(h, "frosted".equals(bench.getItem().get(ModDataComponents.SKIN.get())) && SkinWear.isFresh(SkinWear.of(bench.getItem())), "swapped to frosted");
        check(h, kits(p, "brass", SkinDefs.DRILL, "field_worn") == 2, "brass back as a kit (now 2), got " + kits(p, "brass", SkinDefs.DRILL, "field_worn"));
        check(h, kits(p, "frosted", SkinDefs.DRILL, SkinWear.FRESH) == 0, "frosted kit used up");
        // wheel: put a kit on by name, then take the skin off
        BlockPos abs = h.absolutePos(pos);
        check(h, SkinBenchNetwork.handleApply(p, new SkinBenchNetwork.ApplySkinPayload(abs, "brass", "field_worn")), "wheel apply");
        check(h, kits(p, "frosted", SkinDefs.DRILL, SkinWear.FRESH) == 1 && kits(p, "brass", SkinDefs.DRILL, "field_worn") == 1, "wheel swap returns frosted");
        check(h, SkinBenchNetwork.handleApply(p, SkinBenchNetwork.ApplySkinPayload.remove(abs)), "wheel remove");
        check(h, !bench.getItem().has(ModDataComponents.SKIN.get()) && !bench.getItem().has(ModDataComponents.SKIN_WEAR.get()), "machine is plain again");
        check(h, kits(p, "brass", SkinDefs.DRILL, "field_worn") == 2, "brass back again");
        check(h, !SkinBenchNetwork.handleApply(p, SkinBenchNetwork.ApplySkinPayload.remove(abs)), "nothing to take off");
        check(h, !SkinBenchNetwork.handleApply(p, new SkinBenchNetwork.ApplySkinPayload(abs, "obsidian", SkinWear.FRESH)), "no obsidian kit carried");
        // empty hand takes the machine back
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(h, p, pos);
        check(h, !bench.hasItem() && count(p, ModItems.MINING_DRILL.get()) == 1, "machine back in the inventory");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wrongFamilyIsRefused(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        SkinBenchBlockEntity bench = bench(h, pos, new ItemStack(ModItems.MINING_DRILL.get()));
        ServerPlayer p = fresh(h, pos);
        p.setItemInHand(InteractionHand.MAIN_HAND, SkinKitItem.of("brass", SkinDefs.SLUICE, SkinWear.FRESH, 1));
        use(h, p, pos);
        check(h, !bench.getItem().has(ModDataComponents.SKIN.get()), "sluice kit does not go on a drill");
        check(h, p.getMainHandItem().getCount() == 1, "kit kept");
        check(h, !SkinBenchNetwork.handleApply(p, new SkinBenchNetwork.ApplySkinPayload(h.absolutePos(pos), "brass", SkinWear.FRESH)),
                "wheel finds no drill kit");
        // the same kit fits a sluice
        bench.takeItem();
        bench.setItem(new ItemStack(ModBlocks.SLUICE_BOX_T2.get().asItem()));
        use(h, p, pos);
        check(h, "brass".equals(bench.getItem().get(ModDataComponents.SKIN.get())), "sluice kit goes on a sluice (Tier 2 too)");
        check(h, p.getMainHandItem().isEmpty(), "kit used");
        // a kit on an empty bench does nothing
        bench.takeItem();
        p.setItemInHand(InteractionHand.MAIN_HAND, SkinKitItem.of("rustic", SkinDefs.DRILL, SkinWear.FRESH, 1));
        use(h, p, pos);
        check(h, p.getMainHandItem().getCount() == 1 && !bench.hasItem(), "nothing on the bench, kit kept");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void skinIsNeverDuplicatedOrLost(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        ItemStack drill = new ItemStack(ModItems.MINING_DRILL.get());
        drill.set(ModDataComponents.SKIN.get(), "obsidian");
        SkinWear.set(drill, "pit_scarred");
        SkinBenchBlockEntity bench = bench(h, pos, drill);
        ServerPlayer p = fresh(h, pos);
        // two in hand, so the used one does not free the slot
        p.setItemInHand(InteractionHand.MAIN_HAND, SkinKitItem.of("rustic", SkinDefs.DRILL, SkinWear.FRESH, 2));
        List<ServerPlayer> ps = List.of(p);
        int start = skinsInWorld(h, ps, bench);
        check(h, start == 3, "three skins to start, got " + start);
        // full inventory: the old skin's kit drops at the feet
        for (int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        use(h, p, pos);
        check(h, "rustic".equals(bench.getItem().get(ModDataComponents.SKIN.get())), "rustic on");
        boolean dropped = false;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4))) {
            dropped |= SkinKitItem.matches(e.getItem(), "obsidian", SkinDefs.DRILL, "pit_scarred");
        }
        check(h, dropped, "obsidian kit dropped at the feet with a full inventory");
        check(h, skinsInWorld(h, ps, bench) == start, "swap with a full inventory keeps the count");
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16))) e.discard();
        for (int i = 1; i < 36; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
        p.getInventory().setItem(1, SkinKitItem.of("obsidian", SkinDefs.DRILL, "pit_scarred", 1));

        // break the bench: the skinned machine drops with its skin
        h.destroyBlock(pos);
        ItemStack machine = ItemStack.EMPTY;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(pos)).inflate(3))) {
            if (e.getItem().is(ModItems.MINING_DRILL.get())) {
                machine = e.getItem().copy();
                e.discard();
            }
        }
        check(h, "rustic".equals(machine.get(ModDataComponents.SKIN.get())), "bench drop keeps the skin");

        // place and break the drill: the skin follows the machine
        BlockPos dpos = new BlockPos(0, 1, 0);
        BlockState lower = ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.NORTH)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.LOWER);
        h.setBlock(dpos, lower);
        h.setBlock(dpos.above(), lower.setValue(MiningDrillBlock.HALF, DoubleBlockHalf.UPPER));
        ModBlocks.MINING_DRILL.get().setPlacedBy(h.getLevel(), h.absolutePos(dpos), lower, p, machine);
        check(h, "rustic".equals(((MiningDrillBlockEntity) h.getBlockEntity(dpos)).getSkin()), "placed drill wears it");
        p.gameMode.destroyBlock(h.absolutePos(dpos));
        int machines = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(dpos)).inflate(3))) {
            if (e.getItem().is(ModItems.MINING_DRILL.get()) && "rustic".equals(e.getItem().get(ModDataComponents.SKIN.get()))) machines++;
        }
        check(h, machines == 1, "exactly one skinned drill dropped, got " + machines);
        check(h, skinsInWorld(h, ps, null) == start, "break and place keep the count");

        // death drops: kits fall out like any item, components intact
        p.getInventory().dropAll();
        check(h, allKits(p) == 0, "inventory empty after the drops");
        check(h, skinsInWorld(h, ps, null) == start, "death drops keep the count");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void scrapNeedsASecondSneakUse(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        bench(h, pos, ItemStack.EMPTY);
        ServerPlayer p = fresh(h, pos);
        p.setItemInHand(InteractionHand.MAIN_HAND, SkinKitItem.of("obsidian", SkinDefs.SLUICE, "pit_scarred", 3));
        BlockPos abs = h.absolutePos(pos);
        sneakUse(h, p, pos);
        check(h, p.getMainHandItem().getCount() == 3 && count(p, ModItems.SKIN_SCRAP.get()) == 0, "first sneak-use only asks");
        sneakUse(h, p, pos);
        check(h, p.getMainHandItem().getCount() == 2 && count(p, ModItems.SKIN_SCRAP.get()) == 6, "second scraps one kit for 6");
        // another kit in between asks again
        check(h, SkinBenchActions.scrap(p, p.getMainHandItem(), abs) == SkinBenchActions.Outcome.SCRAP_ASKED, "asks again");
        ItemStack other = SkinKitItem.of("brass", SkinDefs.DRILL, SkinWear.FRESH, 1);
        check(h, SkinBenchActions.scrap(p, other, abs) == SkinBenchActions.Outcome.SCRAP_ASKED && other.getCount() == 1, "another kit only asks");
        check(h, SkinBenchActions.scrap(p, p.getMainHandItem(), abs) == SkinBenchActions.Outcome.SCRAP_ASKED, "switching back asks again");
        // leaving forgets the question
        SkinBenchActions.onLogout(new PlayerEvent.PlayerLoggedOutEvent(p));
        check(h, SkinBenchActions.scrap(p, p.getMainHandItem(), abs) == SkinBenchActions.Outcome.SCRAP_ASKED, "after a logout it asks again");
        // sneak-use away from a bench does nothing
        p.setShiftKeyDown(true);
        BlockPos ground = h.absolutePos(new BlockPos(0, 0, 0));
        var r = p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
        p.setShiftKeyDown(false);
        check(h, r == net.minecraft.world.InteractionResult.PASS, "only works on a bench");
        h.runAfterDelay(SkinBenchActions.SCRAP_CONFIRM_TICKS + 5, () -> {
            check(h, SkinBenchActions.scrap(p, p.getMainHandItem(), abs) == SkinBenchActions.Outcome.SCRAP_ASKED, "too late: asks again");
            check(h, p.getMainHandItem().getCount() == 2, "still 2 kits");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void crateSkinRollsAreKitsWithAnEvenSplit(GameTestHelper h) {
        RandomSource random = RandomSource.create(2626L);
        CrateDefs.CrateTier iron = CrateDefs.tier("iron_crate");
        int drill = 0, sluice = 0, skins = 0;
        for (int i = 0; i < 60000; i++) {
            CrateRolls.Roll r = CrateRolls.roll(iron, random);
            if (r.entry().kind() != CrateDefs.Kind.SKIN) {
                check(h, r.family() == null, "only skins roll a family");
                continue;
            }
            skins++;
            if (SkinDefs.DRILL.equals(r.family())) drill++;
            else if (SkinDefs.SLUICE.equals(r.family())) sluice++;
            else h.fail("unknown family " + r.family());
        }
        double share = 100.0 * drill / skins;
        check(h, skins > 10000 && Math.abs(share - 50) < 1.5, "drill share " + share + "% of " + skins);
        check(h, drill + sluice == skins, "every skin roll has a family");

        // granting: always a kit at the rolled wear and family, never scrap, even for a skin already owned many times
        clear(h);
        ServerPlayer p = fresh(h, new BlockPos(2, 1, 2));
        for (int i = 0; i < 40; i++) {
            CrateRolls.Roll r = new CrateRolls.Roll("iron_crate", "epic", "epic",
                    new CrateDefs.Entry(CrateDefs.Kind.SKIN, "obsidian", 1, 1), i % 2 == 0 ? "field_worn" : "fresh_from_the_forge",
                    i % 4 < 2 ? SkinDefs.DRILL : SkinDefs.SLUICE);
            CrateRolls.Outcome o = CrateRolls.grant(p, r);
            check(h, o.result() == CrateRolls.Result.KIT && o.scrap() == 0 && SkinKitItem.isKit(o.icon()), "skin roll is a kit");
        }
        check(h, count(p, ModItems.SKIN_SCRAP.get()) == 0, "no Skin Scrap from skins");
        check(h, kits(p, "obsidian", SkinDefs.DRILL, "field_worn") == 10 && kits(p, "obsidian", SkinDefs.SLUICE, "field_worn") == 10
                && kits(p, "obsidian", SkinDefs.DRILL, SkinWear.FRESH) == 10 && kits(p, "obsidian", SkinDefs.SLUICE, SkinWear.FRESH) == 10,
                "10 of each kit");
        check(h, LedgerData.get(p).unlockedSkins().isEmpty(), "nothing unlocked on the player");

        // titles still unlock, and a duplicate title still gives scrap
        CrateRolls.Roll title = new CrateRolls.Roll("iron_crate", "rare", "rare", new CrateDefs.Entry(CrateDefs.Kind.TITLE, "crate_cracker", 1, 1), null);
        check(h, CrateRolls.grant(p, title).result() == CrateRolls.Result.NEW, "new title");
        CrateRolls.Outcome dup = CrateRolls.grant(p, title);
        check(h, dup.result() == CrateRolls.Result.DUPLICATE && dup.scrap() == 3 && count(p, ModItems.SKIN_SCRAP.get()) == 3, "duplicate title gives 3 scrap");

        // the preview icon the spin shows is the same kit
        CrateRolls.Roll r = new CrateRolls.Roll("gold_crate", "rare", "rare", new CrateDefs.Entry(CrateDefs.Kind.SKIN, "brass", 1, 1), "pit_scarred", SkinDefs.SLUICE);
        check(h, SkinKitItem.matches(CrateRolls.previewIcon(r), "brass", SkinDefs.SLUICE, "pit_scarred"), "spin shows the sluice kit");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heldCrateRollKeepsItsFamily(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        PendingCrates data = new PendingCrates();
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        data.add(java.util.UUID.randomUUID(), h.getLevel(), pos, new CrateRolls.Roll("iron_crate", "rare", "rare",
                new CrateDefs.Entry(CrateDefs.Kind.SKIN, "frosted", 1, 1), "workshop_clean", SkinDefs.SLUICE));
        data.add(java.util.UUID.randomUUID(), h.getLevel(), pos.east(), new CrateRolls.Roll("iron_crate", "rare", "rare",
                new CrateDefs.Entry(CrateDefs.Kind.SKIN, "frosted", 1, 1), "workshop_clean"));
        PendingCrates back = PendingCrates.load(data.save(new CompoundTag(), reg), reg);
        check(h, back.all().size() == 2, "both held");
        check(h, SkinDefs.SLUICE.equals(back.all().get(0).roll.family()) && "workshop_clean".equals(back.all().get(0).roll.wear()), "family saved");
        check(h, back.all().get(1).roll.family() == null && SkinDefs.DRILL.equals(back.all().get(1).roll.kitFamily()),
                "a roll held from before kits becomes a drill kit");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ledgerSkinLevelsGiveFreshDrillKits(GameTestHelper h) {
        clear(h);
        ServerPlayer p = fresh(h, new BlockPos(2, 1, 2));
        LedgerData data = LedgerData.get(p);
        Map<Integer, String> levels = Map.of(13, "rustic", 22, "rustic_weathered", 33, "frosted", 39, "brass", 47, "obsidian");
        data.setRenown(LedgerDefs.curve().cumulativeForLevel(47));
        for (var e : levels.entrySet()) {
            check(h, LedgerManager.claim(p, e.getKey()), "claim level " + e.getKey());
            check(h, kits(p, e.getValue(), SkinDefs.DRILL, SkinWear.FRESH) == 1, "level " + e.getKey() + " gives a Fresh " + e.getValue() + " drill kit");
            check(h, kits(p, e.getValue(), SkinDefs.SLUICE, SkinWear.FRESH) == 0, "no sluice kit from level " + e.getKey());
        }
        check(h, allKits(p) == 5, "five kits in total");
        check(h, data.unlockedSkins().isEmpty(), "nothing unlocked on the player");
        check(h, !LedgerManager.claim(p, 13), "a level is claimed once");
        check(h, allKits(p) == 5, "still five kits");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldUnlocksBecomeKitsOnce(GameTestHelper h) {
        clear(h);
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        ServerPlayer p = fresh(h, new BlockPos(2, 1, 2));
        // player data as an old world saved it: unlocks and wears, no marker
        CompoundTag old = new CompoundTag();
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("brass"));
        list.add(StringTag.valueOf("rustic"));
        old.put("UnlockedSkins", list);
        CompoundTag wears = new CompoundTag();
        wears.putString("brass", "field_worn");
        old.put("SkinWears", wears);
        old.putBoolean("StarterGiven", true);
        LedgerData data = LedgerData.get(p);
        data.deserializeNBT(reg, old);
        check(h, !data.skinsMigrated(), "old data has no marker");

        // a skin already on a machine stays where it is
        ItemStack onMachine = new ItemStack(ModItems.MINING_DRILL.get());
        onMachine.set(ModDataComponents.SKIN.get(), "frosted");
        p.getInventory().setItem(0, onMachine);

        int given = SkinKitMigration.run(p);
        check(h, given == 4, "two skins x two families = 4 kits, got " + given);
        check(h, kits(p, "brass", SkinDefs.DRILL, "field_worn") == 1 && kits(p, "brass", SkinDefs.SLUICE, "field_worn") == 1, "brass kits at Field Worn");
        check(h, kits(p, "rustic", SkinDefs.DRILL, SkinWear.FRESH) == 1 && kits(p, "rustic", SkinDefs.SLUICE, SkinWear.FRESH) == 1, "rustic kits Fresh");
        check(h, data.unlockedSkins().isEmpty() && data.skinsMigrated(), "list cleared, marker set");
        check(h, "frosted".equals(p.getInventory().getItem(0).get(ModDataComponents.SKIN.get())), "skinned machine untouched");

        // next login: nothing again, and the marker survives saving and death
        check(h, SkinKitMigration.run(p) == 0 && allKits(p) == 4, "second run gives nothing");
        LedgerData saved = new LedgerData();
        saved.deserializeNBT(reg, data.serializeNBT(reg));
        check(h, saved.skinsMigrated() && saved.unlockedSkins().isEmpty(), "marker saved");
        LedgerData clone = new LedgerData();
        clone.copyFrom(data);
        check(h, clone.skinsMigrated(), "marker survives death");

        // a new player has nothing to turn into kits
        ServerPlayer q = h.makeMockServerPlayerInLevel();
        q.getInventory().clearContent();
        LedgerData.get(q).deserializeNBT(reg, new CompoundTag());
        check(h, SkinKitMigration.run(q) == 0 && LedgerData.get(q).skinsMigrated() && allKits(q) == 0, "new player: marker only");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void skinGiveAndListCommands(GameTestHelper h) {
        clear(h);
        ServerPlayer p = fresh(h, new BlockPos(2, 1, 2));
        CommandSourceStack src = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var cmds = h.getLevel().getServer().getCommands();
        cmds.performPrefixedCommand(src, "holeydigsite skin give @s brass mining_drill pit_scarred 3");
        check(h, kits(p, "brass", SkinDefs.DRILL, "pit_scarred") == 3, "3 worn brass drill kits");
        cmds.performPrefixedCommand(src, "holeydigsite skin give @s frosted sluice_box");
        check(h, kits(p, "frosted", SkinDefs.SLUICE, SkinWear.FRESH) == 1, "Fresh by default");
        cmds.performPrefixedCommand(src, "holeydigsite skin give @s all all");
        check(h, allKits(p) == 4 + SkinDefs.skins().size() * 2, "all skins x both families, got " + allKits(p));
        int before = allKits(p);
        cmds.performPrefixedCommand(src, "holeydigsite skin give @s brass drill");
        cmds.performPrefixedCommand(src, "holeydigsite skin give @s brass mining_drill nonsense");
        cmds.performPrefixedCommand(src, "holeydigsite skin give @s nothing mining_drill");
        check(h, allKits(p) == before, "bad family, wear or skin gives nothing");
        int listed;
        try {
            listed = cmds.getDispatcher().execute("holeydigsite skin list", src);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            listed = -1;
        }
        check(h, listed == allKits(p), "list counts the kits carried, got " + listed);
        boolean unlockGone;
        try {
            cmds.getDispatcher().execute("holeydigsite skin unlock @s brass", src);
            unlockGone = false;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            unlockGone = true;
        }
        check(h, unlockGone, "the old unlock command is gone");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void twoPlayersAtOneBench(GameTestHelper h) {
        clear(h);
        BlockPos pos = new BlockPos(2, 1, 2);
        SkinBenchBlockEntity bench = bench(h, pos, ItemStack.EMPTY);
        ServerPlayer a = fresh(h, pos);
        ServerPlayer b = fresh(h, pos);
        List<ServerPlayer> ps = List.of(a, b);
        a.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MINING_DRILL.get()));
        use(h, a, pos);
        check(h, bench.hasItem() && a.getMainHandItem().isEmpty(), "A put a drill on");
        a.setItemInHand(InteractionHand.MAIN_HAND, SkinKitItem.of("brass", SkinDefs.DRILL, SkinWear.FRESH, 1));
        b.setItemInHand(InteractionHand.MAIN_HAND, SkinKitItem.of("frosted", SkinDefs.DRILL, SkinWear.FRESH, 1));
        int start = skinsInWorld(h, ps, bench);
        use(h, a, pos);
        use(h, b, pos);
        check(h, "frosted".equals(bench.getItem().get(ModDataComponents.SKIN.get())), "last one wins");
        check(h, kits(b, "brass", SkinDefs.DRILL, SkinWear.FRESH) == 1 && kits(a, "brass", SkinDefs.DRILL, SkinWear.FRESH) == 0,
                "the swapped-off skin goes to whoever swapped it");
        check(h, skinsInWorld(h, ps, bench) == start, "count kept with two players");
        // both send a remove at once: only one gets the kit
        BlockPos abs = h.absolutePos(pos);
        boolean ra = SkinBenchNetwork.handleApply(a, SkinBenchNetwork.ApplySkinPayload.remove(abs));
        boolean rb = SkinBenchNetwork.handleApply(b, SkinBenchNetwork.ApplySkinPayload.remove(abs));
        check(h, ra && !rb, "only the first remove does anything");
        check(h, kits(a, "frosted", SkinDefs.DRILL, SkinWear.FRESH) == 1 && skinsInWorld(h, ps, bench) == start, "one frosted kit, count kept");
        // B's stale wheel pick of a kit B no longer has is refused
        check(h, !SkinBenchNetwork.handleApply(b, new SkinBenchNetwork.ApplySkinPayload(abs, "frosted", SkinWear.FRESH)), "stale pick refused");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void jeiCrateRowShowsBothKits(GameTestHelper h) {
        boolean sawSkin = false;
        for (CrateOddsRow row : CrateOddsRow.loadAll()) {
            for (CrateOddsRow.RarityPool pool : row.pools()) {
                for (CrateOddsRow.RewardEntry r : pool.rewards()) {
                    if (r.kind() != CrateDefs.Kind.SKIN) {
                        check(h, r.variants().size() == 1, "one icon for non-skins");
                        continue;
                    }
                    sawSkin = true;
                    check(h, r.variants().size() == 2 && SkinKitItem.matches(r.variants().get(0), r.id(), SkinDefs.DRILL, SkinWear.FRESH)
                            && SkinKitItem.matches(r.variants().get(1), r.id(), SkinDefs.SLUICE, SkinWear.FRESH), "drill and sluice kit for " + r.id());
                    check(h, SkinKitItem.isKit(r.icon()), "icon is a kit");
                    check(h, r.label().getString().contains("Skin Kit"), "label says kit: " + r.label().getString());
                }
            }
        }
        check(h, sawSkin, "crates have skins");
        h.succeed();
    }
}
