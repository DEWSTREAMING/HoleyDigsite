package com.holeydigsite.devtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.BeltLanes;
import com.holeydigsite.crate.CrateBlock;
import com.holeydigsite.crate.CrateDefs;
import com.holeydigsite.crate.CrateNetwork;
import com.holeydigsite.crate.CrateRolls;
import com.holeydigsite.crate.CrateSpin;
import com.holeydigsite.crate.PendingCrates;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Held crate rewards, the spin strip, the helmet stand lamp and the belt lanes. */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase233Tests {
    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static ServerPlayer fresh(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        LedgerData.get(p).resetAll();
        return p;
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int y = 0; y <= 6; y++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    private static int items(ServerPlayer p) {
        return p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
    }

    private static CompoundTag ledger(GameTestHelper h, ServerPlayer p) {
        return LedgerData.get(p).serializeNBT(h.getLevel().registryAccess());
    }

    private static boolean changed(GameTestHelper h, ServerPlayer p, int itemsBefore, CompoundTag ledgerBefore) {
        return items(p) != itemsBefore || !ledgerBefore.equals(ledger(h, p));
    }

    private static void openAt(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        h.setBlock(rel, ModBlocks.WOODEN_CRATE.get().defaultBlockState());
        BlockState s = h.getBlockState(rel);
        BlockPos abs = h.absolutePos(rel);
        s.useWithoutItem(h.getLevel(), p, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void rewardComesWhenTheSpinEnds(GameTestHelper h) {
        clear(h);
        ServerPlayer p = fresh(h);
        BlockPos rel = new BlockPos(2, 1, 2);
        int before = items(p);
        CompoundTag led = ledger(h, p);
        openAt(h, p, rel);
        check(h, h.getBlockState(rel).getValue(CrateBlock.OPEN), "crate open");
        check(h, !changed(h, p, before, led), "nothing yet right after opening");
        h.runAfterDelay(CrateSpin.SPIN_TICKS - 5, () -> {
            check(h, !changed(h, p, before, led), "nothing before the spin is over");
            PendingCrates.Held held = PendingCrates.get(h.getLevel().getServer()).at(h.getLevel(), h.absolutePos(rel));
            check(h, held != null && !held.granted, "the roll is held");
        });
        h.runAfterDelay(CrateSpin.GRANT_TICKS + 3, () -> {
            check(h, changed(h, p, before, led), "the reward arrived after the spin");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void skipGivesTheRewardAtOnce(GameTestHelper h) {
        clear(h);
        ServerPlayer p = fresh(h);
        BlockPos rel = new BlockPos(2, 1, 2);
        int before = items(p);
        CompoundTag led = ledger(h, p);
        openAt(h, p, rel);
        ServerPlayer other = fresh(h);
        check(h, !CrateNetwork.skip(other, h.absolutePos(rel)), "someone else cannot skip it");
        check(h, !changed(h, p, before, led), "still held");
        check(h, CrateNetwork.skip(p, h.absolutePos(rel)), "skip accepted");
        check(h, changed(h, p, before, led), "reward given at once");
        int after = items(p);
        CompoundTag ledAfter = ledger(h, p);
        check(h, !CrateNetwork.skip(p, h.absolutePos(rel)), "a second skip gives nothing");
        check(h, items(p) == after && ledAfter.equals(ledger(h, p)), "never given twice");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void leavingMidSpinStillGivesTheRewardAndItSurvivesASave(GameTestHelper h) {
        clear(h);
        ServerPlayer p = fresh(h);
        BlockPos rel = new BlockPos(2, 1, 2);
        int before = items(p);
        CompoundTag led = ledger(h, p);
        openAt(h, p, rel);
        PendingCrates data = PendingCrates.get(h.getLevel().getServer());
        PendingCrates.Held held = data.at(h.getLevel(), h.absolutePos(rel));
        check(h, held != null, "held");
        // the saved copy has the same roll
        PendingCrates copy = PendingCrates.load(data.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        PendingCrates.Held again = copy.at(h.getLevel(), h.absolutePos(rel));
        check(h, again != null && again.player.equals(p.getUUID()) && !again.granted, "the held reward is saved with the world");
        check(h, again.roll.entry().equals(held.roll.entry()) && again.roll.rarity().equals(held.roll.rarity())
                && java.util.Objects.equals(again.roll.wear(), held.roll.wear()), "the saved roll is the same roll");
        // what the logout handler does
        PendingCrates.grantAll(p, false);
        check(h, changed(h, p, before, led), "given when the player leaves");
        check(h, held.granted, "marked as given");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stripHoldsOnlyRaritiesTheTierCanGive(GameTestHelper h) {
        RandomSource random = RandomSource.create(7L);
        int near = 0, spins = 0;
        float lo = 1, hi = 0;
        for (String tierId : CrateDefs.BUILTIN_TIERS) {
            CrateDefs.CrateTier tier = CrateDefs.tier(tierId);
            List<String> allowed = CrateRolls.givenRarities(tier);
            for (int i = 0; i < 1500; i++) {
                CrateRolls.Roll roll = CrateRolls.roll(tier, random);
                ItemStack icon = CrateRolls.previewIcon(roll);
                CrateRolls.Spin spin = CrateRolls.spin(tier, roll, icon, random);
                spins++;
                check(h, spin.icons().size() == CrateSpin.STRIP_SIZE && spin.rarities().size() == spin.icons().size(), tierId + ": strip size");
                check(h, spin.resultIndex() == CrateSpin.RESULT_INDEX, tierId + ": result index");
                check(h, ItemStack.matches(spin.icons().get(spin.resultIndex()), icon), tierId + ": the real result is at the result index");
                check(h, spin.rarities().get(spin.resultIndex()).equals(roll.rarity()), tierId + ": result rarity");
                for (String r : spin.rarities()) {
                    check(h, allowed.contains(r), tierId + ": strip shows " + r + " but the tier gives " + allowed);
                }
                check(h, spin.stopFrac() > 0 && spin.stopFrac() < 1, tierId + ": stops inside the slot");
                lo = Math.min(lo, spin.stopFrac());
                hi = Math.max(hi, spin.stopFrac());
                int ri = CrateDefs.RARITIES.indexOf(roll.rarity());
                for (int d = -1; d <= 1; d += 2) {
                    String nr = spin.rarities().get(spin.resultIndex() + d);
                    if (CrateDefs.RARITIES.indexOf(nr) > ri && (spin.stopFrac() < 0.2F || spin.stopFrac() > 0.8F)) {
                        near++;
                        break;
                    }
                }
            }
        }
        check(h, near > 0, "near misses happen");
        check(h, near < spins * 0.8, "near misses are not every spin: " + near + "/" + spins);
        check(h, lo < 0.2F && hi > 0.8F, "the stop point varies: " + lo + ".." + hi);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void spinCurveCrawlsAtTheEnd(GameTestHelper h) {
        double end = CrateSpin.RESULT_INDEX + 0.5;
        check(h, CrateSpin.position(0, CrateSpin.START, end) == CrateSpin.START, "starts at the start");
        check(h, Math.abs(CrateSpin.position(CrateSpin.SPIN_TICKS, CrateSpin.START, end) - end) < 1e-9, "ends on the stop point");
        double first = CrateSpin.position(10, CrateSpin.START, end) - CrateSpin.START;
        double last = end - CrateSpin.position(CrateSpin.SPIN_TICKS - 10, CrateSpin.START, end);
        check(h, first > 12 * last, "fast start, slow tail: " + first + " vs " + last);
        check(h, end - CrateSpin.position(CrateSpin.SPIN_TICKS - 20, CrateSpin.START, end) < 1.5, "the last second covers about one slot");
        check(h, CrateSpin.SPIN_TICKS >= 100 && CrateSpin.SPIN_TICKS <= 140, "about 5-7 seconds");
        check(h, CrateBlock.OPEN_TICKS > CrateSpin.GRANT_TICKS && CrateSpin.GRANT_TICKS > CrateSpin.SPIN_TICKS, "grant after the spin, removal after the grant");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void helmetStandHasItsOwnLamp(GameTestHelper h) {
        String base = "/assets/holeydigsite/";
        try (InputStream in = Phase233Tests.class.getResourceAsStream(base + "models/block/miners_helmet_stand.json")) {
            check(h, in != null, "model exists");
            JsonObject model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            check(h, !model.toString().contains("miners_lantern"), "no longer uses the lantern texture");
            String lamp = model.getAsJsonObject("textures").get("3").getAsString();
            check(h, lamp.equals("holeydigsite:block/miners_helmet_lamp"), "lamp texture is its own");
            check(h, Phase233Tests.class.getResource(base + "textures/block/miners_helmet_lamp.png") != null, "lamp texture resolves");
        } catch (java.io.IOException e) {
            h.fail("io " + e);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldClumpAndChunkSpritesAreGone(GameTestHelper h) {
        String base = "/assets/holeydigsite/textures/item/";
        check(h, Phase233Tests.class.getResource(base + "dirt_clump.png") == null, "flat dirt clump sprite removed");
        check(h, Phase233Tests.class.getResource(base + "folkiron_chunk_small.png") == null, "flat chunk sprite removed");
        check(h, Phase233Tests.class.getResource(base + "lump/dirt.png") != null, "3D clump texture stays");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void beltLanesAreStableAndInsideTheRails(GameTestHelper h) {
        boolean[] lanesUsed = new boolean[3];
        for (int seed = 0; seed < 256; seed++) {
            float a = BeltLanes.offset(seed);
            check(h, a == BeltLanes.offset(seed), "same seed, same offset");
            // a lump is about 0.2 block wide at the belt scale
            check(h, Math.abs(a) + 0.12F <= BeltLanes.FREE_HALF_WIDTH, "seed " + seed + " stays inside the rails: " + a);
            check(h, Math.abs(a) <= BeltLanes.maxOffset() + 1e-6, "within the stated maximum");
            if (a < -0.08F) lanesUsed[0] = true;
            else if (a > 0.08F) lanesUsed[2] = true;
            else lanesUsed[1] = true;
        }
        check(h, lanesUsed[0] && lanesUsed[1] && lanesUsed[2], "all lanes are used");
        h.succeed();
    }
}
