package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.BeltMode;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.crate.CrateBlock;
import com.holeydigsite.crate.CrateDefs;
import com.holeydigsite.crate.CrateRolls;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.skin.SkinBenchActions;
import com.holeydigsite.skin.SkinBenchBlockEntity;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import com.holeydigsite.skin.SkinWear;
import com.holeydigsite.transport.DropChuteBlock;
import com.holeydigsite.transport.DropChuteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Phase Goldfields tests: conveyor skins, 16 kits per conveyor drop, divided scrap value, Skin Bench stack splitting,
 * block entity skin preservation on place/drop, and the Goldfields Crate without a legendary tier.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseGoldfieldsTests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        LedgerData.get(p).resetAll();
        LedgerData.get(p).setCratesOn(true);
        return p;
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) total += s.getCount();
        }
        return total;
    }

    @GameTest(template = "empty")
    public static void sixNewSkinsAndConveyorFamily(GameTestHelper h) {
        Map<String, String> expected = Map.of(
                "patina", "common",
                "gunmetal", "common",
                "ironbark", "rare",
                "enamel", "rare",
                "quartz_reef", "epic",
                "nugget", "epic"
        );

        for (Map.Entry<String, String> e : expected.entrySet()) {
            SkinDefs.SkinDef def = SkinDefs.skin(e.getKey());
            check(h, def != null, "skin " + e.getKey() + " must exist");
            check(h, e.getValue().equals(def.rarity()), "skin " + e.getKey() + " rarity must be " + e.getValue());
            check(h, def.families().contains(SkinDefs.CONVEYOR), "skin " + e.getKey() + " must fit conveyor");
        }

        // All 18 skins fit conveyor
        for (SkinDefs.SkinDef def : SkinDefs.skins().values()) {
            check(h, def.families().contains(SkinDefs.CONVEYOR), "all skins must fit conveyor: " + def.id());
        }

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void conveyorSkinDropGives16Kits(GameTestHelper h) {
        ServerPlayer p = player(h);
        CrateRolls.Roll conveyorRoll = new CrateRolls.Roll(
                "goldfields_crate", "common", "common",
                new CrateDefs.Entry(CrateDefs.Kind.SKIN, "patina", 1, 1),
                SkinWear.FRESH, SkinDefs.CONVEYOR
        );
        CrateRolls.grant(p, conveyorRoll);
        check(h, count(p, ModItems.SKIN_KIT.get()) == 16, "conveyor skin drop gives 16 kits");

        p.getInventory().clearContent();
        CrateRolls.Roll drillRoll = new CrateRolls.Roll(
                "goldfields_crate", "common", "common",
                new CrateDefs.Entry(CrateDefs.Kind.SKIN, "patina", 1, 1),
                SkinWear.FRESH, SkinDefs.DRILL
        );
        CrateRolls.grant(p, drillRoll);
        check(h, count(p, ModItems.SKIN_KIT.get()) == 1, "drill skin drop gives 1 kit");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void conveyorKitScrapValueDividedBy16(GameTestHelper h) {
        ItemStack drillCommon = SkinKitItem.of("patina", SkinDefs.DRILL, SkinWear.FRESH, 1);
        ItemStack convCommon = SkinKitItem.of("patina", SkinDefs.CONVEYOR, SkinWear.FRESH, 1);
        int drillScrap = SkinKitItem.scrapValue(drillCommon);
        int convScrap = SkinKitItem.scrapValue(convCommon);
        check(h, convScrap == Math.max(1, (drillScrap + 15) / 16), "conveyor scrap is normal / 16 rounded up (min 1)");
        check(h, convScrap >= 1, "conveyor scrap is at least 1");

        // Epic skin check
        ItemStack drillEpic = SkinKitItem.of("quartz_reef", SkinDefs.DRILL, SkinWear.FRESH, 1);
        ItemStack convEpic = SkinKitItem.of("quartz_reef", SkinDefs.CONVEYOR, SkinWear.FRESH, 1);
        int drillEpicScrap = SkinKitItem.scrapValue(drillEpic);
        int convEpicScrap = SkinKitItem.scrapValue(convEpic);
        check(h, convEpicScrap == Math.max(1, (drillEpicScrap + 15) / 16), "epic conveyor scrap is normal / 16 rounded up");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void skinBenchStackSplittingAndRemoval(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, ModBlocks.SKIN_BENCH.get().defaultBlockState());
        SkinBenchBlockEntity bench = (SkinBenchBlockEntity) h.getBlockEntity(pos);

        // Put 16 plain clump belts on bench
        bench.setItem(new ItemStack(ModItems.CLUMP_BELT.get(), 16));
        ServerPlayer p = player(h);
        ItemStack kit = SkinKitItem.of("patina", SkinDefs.CONVEYOR, SkinWear.FRESH, 4);

        // Apply skin
        SkinBenchActions.Outcome out = SkinBenchActions.apply(p, bench, kit);
        check(h, out == SkinBenchActions.Outcome.APPLIED, "outcome APPLIED");
        check(h, bench.getItem().getCount() == 15, "bench still holds 15 plain belts");
        check(h, kit.getCount() == 3, "kit stack shrank by 1");

        // Check player received the 1 skinned belt
        boolean hasSkinned = false;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModItems.CLUMP_BELT.get()) && "patina".equals(s.get(ModDataComponents.SKIN.get()))) {
                hasSkinned = true;
                break;
            }
        }
        check(h, hasSkinned, "player received 1 skinned clump belt");

        // Test remove skin: put a stack of 4 skinned belts on bench
        ItemStack skinnedStack = new ItemStack(ModItems.CLUMP_BELT.get(), 4);
        skinnedStack.set(ModDataComponents.SKIN.get(), "patina");
        bench.setItem(skinnedStack);
        p.getInventory().clearContent();

        SkinBenchActions.Outcome remOut = SkinBenchActions.remove(p, bench);
        check(h, remOut == SkinBenchActions.Outcome.REMOVED, "outcome REMOVED");
        check(h, bench.getItem().getCount() == 3, "bench holds 3 remaining skinned belts");
        check(h, count(p, ModItems.SKIN_KIT.get()) == 1, "player got 1 kit back");
        check(h, count(p, ModItems.CLUMP_BELT.get()) == 1, "player got 1 plain belt back");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void placedBeltAndChutePreserveSkin(GameTestHelper h) {
        BlockPos beltPos = new BlockPos(1, 1, 1);
        ItemStack beltStack = new ItemStack(ModItems.CLUMP_BELT.get());
        beltStack.set(ModDataComponents.SKIN.get(), "ironbark");
        SkinWear.set(beltStack, "workshop_clean");

        h.setBlock(beltPos, ModBlocks.CLUMP_BELT.get().defaultBlockState());
        ModBlocks.CLUMP_BELT.get().setPlacedBy(h.getLevel(), h.absolutePos(beltPos), h.getBlockState(beltPos), null, beltStack);

        ClumpBeltBlockEntity be = (ClumpBeltBlockEntity) h.getBlockEntity(beltPos);
        check(h, "ironbark".equals(be.getSkin()), "placed belt BE has skin ironbark");
        check(h, "workshop_clean".equals(be.getWear()), "placed belt BE has wear workshop_clean");

        // Drop chute
        BlockPos chutePos = new BlockPos(2, 1, 1);
        ItemStack chuteStack = new ItemStack(ModItems.DROP_CHUTE.get());
        chuteStack.set(ModDataComponents.SKIN.get(), "nugget");
        h.setBlock(chutePos, ModBlocks.DROP_CHUTE.get().defaultBlockState());
        ModBlocks.DROP_CHUTE.get().setPlacedBy(h.getLevel(), h.absolutePos(chutePos), h.getBlockState(chutePos), null, chuteStack);

        DropChuteBlockEntity cbe = (DropChuteBlockEntity) h.getBlockEntity(chutePos);
        check(h, "nugget".equals(cbe.getSkin()), "placed chute BE has skin nugget");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void goldfieldsCrateHasNoLegendary(GameTestHelper h) {
        CrateDefs.CrateTier t = CrateDefs.tier("goldfields_crate");
        check(h, t != null, "goldfields_crate tier must exist");
        check(h, t.percent("legendary") == 0.0, "legendary odds are 0");
        check(h, t.pool("legendary").isEmpty(), "legendary pool is empty");
        check(h, t.percent("common") > 0, "common odds > 0");
        check(h, t.percent("rare") > 0, "rare odds > 0");
        check(h, t.percent("epic") > 0, "epic odds > 0");

        // Roll 200 times, ensure no roll ever returns legendary
        RandomSource rand = RandomSource.create(42);
        for (int i = 0; i < 200; i++) {
            CrateRolls.Roll r = CrateRolls.roll(t, rand);
            check(h, r != null, "roll is not null");
            check(h, !"legendary".equals(r.rolledRarity()) && !"legendary".equals(r.rarity()), "roll never produces legendary");
        }

        h.succeed();
    }
}
