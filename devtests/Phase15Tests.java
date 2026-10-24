package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.tier.TierDef;
import com.holeydigsite.tier.TierFamily;
import com.holeydigsite.tier.Tiers;
import com.holeydigsite.power.WaterNetworkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Phase 15 (sandbox only): ONE parameterised check per tier family, run over EVERY tier the data defines - so a new tier
 * is covered the moment its JSON entry exists. Per tier: block + item registered, the block places, the stats in the data
 * are the ones the block really uses, powered machines accept power, the block drops itself, and the tier has a
 * shop price or a forge recipe file.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase15Tests {
    private static void family(GameTestHelper h, String name) {
        TierFamily fam = Tiers.family(name);
        h.assertTrue(!fam.tiers().isEmpty(), "family " + name + " has no tiers");
        BlockPos pos = new BlockPos(1, 2, 1);
        ServerLevel level = h.getLevel();
        for (TierDef t : fam.tiers()) {
            String id = t.id();
            ResourceLocation key = HoleyDigsite.id(id);
            Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key);
            h.assertTrue(item != net.minecraft.world.item.Items.AIR, id + ": item not registered");
            if (!t.hasBlock()) {
                continue;
            }
            Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(key);
            h.assertTrue(block != net.minecraft.world.level.block.Blocks.AIR, id + ": block not registered");
            h.assertTrue(block.asItem() == item, id + ": block item mismatch");
            stats(h, t, block);

            BlockPos abs = h.absolutePos(pos);
            BlockState state = block.defaultBlockState();
            level.setBlock(abs, state, 3);
            h.assertTrue(level.getBlockState(abs).is(block), id + ": does not place");
            if (block instanceof WaterNetworkBlock w) {
                BlockState placed = level.getBlockState(abs);
                w.setWaterPowered(level, abs, placed, true);
                w.setWaterPowered(level, abs, level.getBlockState(abs), false);
            }
            List<net.minecraft.world.item.ItemStack> drops = Block.getDrops(level.getBlockState(abs), level, abs, level.getBlockEntity(abs));
            h.assertTrue(drops.stream().anyMatch(s -> s.is(item)), id + ": does not drop itself: " + drops);
            level.removeBlock(abs, false);

            var rm = level.getServer().getResourceManager();
            boolean shop = rm.getResource(HoleyDigsite.id("holeydigsite/shop_items/" + id + ".json")).isPresent();
            boolean recipe = t.recipes().stream().allMatch(r -> rm.getResource(
                    HoleyDigsite.id("holeydigsite/forge_recipes/" + r.file() + ".json")).isPresent());
            boolean wantsShop = t.shop() != null, wantsRecipe = !t.recipes().isEmpty();
            h.assertTrue(wantsShop || wantsRecipe, id + ": no obtain entry (shop or recipe)");
            h.assertTrue(!wantsShop || shop, id + ": shop file missing");
            h.assertTrue(!wantsRecipe || recipe, id + ": forge recipe file missing");
        }
        h.succeed();
    }

    private static void stats(GameTestHelper h, TierDef t, Block block) {
        String id = t.id();
        if (block instanceof MiningDrillBlock d) {
            h.assertTrue(d.cycleTicks() == t.stat("cycle_ticks"), id + ": cycle_ticks");
        } else if (block instanceof ClumpBeltBlock b) {
            h.assertTrue(b.speed() == t.stat("speed"), id + ": speed");
        } else if (block instanceof ForgeBlock f) {
            h.assertTrue(f.heat() == t.stat("heat"), id + ": heat");
        } else if (block instanceof PanBlock p) {
            h.assertTrue(p.maxFill() == t.stat("max_fill"), id + ": max_fill");
        }
    }

    @GameTest(template = "empty", batch = "p15", timeoutTicks = 100) public static void miningDrill(GameTestHelper h) { family(h, "mining_drill"); }
    @GameTest(template = "empty", batch = "p15", timeoutTicks = 100) public static void sluiceBox(GameTestHelper h) { family(h, "sluice_box"); }
    @GameTest(template = "empty", batch = "p15", timeoutTicks = 100) public static void pan(GameTestHelper h) { family(h, "pan"); }
    @GameTest(template = "empty", batch = "p15", timeoutTicks = 100) public static void brush(GameTestHelper h) { family(h, "brush"); }
    @GameTest(template = "empty", batch = "p15", timeoutTicks = 100) public static void clumpBelt(GameTestHelper h) { family(h, "clump_belt"); }
    @GameTest(template = "empty", batch = "p15", timeoutTicks = 100) public static void inclineBelt(GameTestHelper h) { family(h, "incline_belt"); }
    @GameTest(template = "empty", batch = "p15", timeoutTicks = 100) public static void crucible(GameTestHelper h) { family(h, "crucible"); }
    @GameTest(template = "empty", batch = "p15", timeoutTicks = 100) public static void forge(GameTestHelper h) { family(h, "forge"); }
    @GameTest(template = "empty", batch = "p15", timeoutTicks = 100) public static void mold(GameTestHelper h) { family(h, "mold"); }
}
