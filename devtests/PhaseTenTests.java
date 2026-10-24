package com.holeydigsite.devtest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.DrillStatus;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.forge.CraftingForgeBlock;
import com.holeydigsite.forge.CraftingForgeBlockEntity;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.forge.ForgeRecipe;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.forge.MoldBlockEntity;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.BrushItem;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.panning.Panning;
import com.holeydigsite.power.LavaIntakeBlock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.shovel.ShovelHelper;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.sluice.SluiceStatus;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sandbox-only GameTests for Phase 10 (not shipped): Part A fixes (crucible faces the forge's front and tips towards the
 * mold; short action-bar messages), the Crafting Forge (loading, pooled grams, parts, recipe cycling, casting by handle and
 * redstone, exact leftovers, collecting, take-back, breaking, hoppers, shop) and the Tier 2 items (drill, sluice, Large Pan,
 * Goldchro Brush, Fast Clump Belt). Template {@code empty_long} (26x5x5, stone floor) unless noted.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class PhaseTenTests {
    /** Crafting Forge facing WEST (front/handle at x=4's west face, lava port east), lava-logged intake behind it. */
    private static final BlockPos CF = new BlockPos(4, 1, 2);
    private static final BlockPos CF_INTAKE = new BlockPos(5, 1, 2);
    private static final ResourceLocation FOLKIRON = HoleyDigsite.id("folkiron");
    private static final ResourceLocation GOLDCHRO = HoleyDigsite.id("goldchro");
    private static final ResourceLocation R_DRILL = HoleyDigsite.id("mining_drill_t2");
    private static final ResourceLocation R_SLUICE = HoleyDigsite.id("sluice_box_t2");
    private static final ResourceLocation R_BELT = HoleyDigsite.id("fast_clump_belt");

    // ------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------

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

    private static void recompute(GameTestHelper h, BlockPos... rel) {
        Set<BlockPos> abs = new HashSet<>();
        for (BlockPos r : rel) abs.add(h.absolutePos(r));
        WaterNetworks.recompute(h.getLevel(), abs);
    }

    /** Lava-logged (or empty) intake with collars, placed with flag 2 so its lava doesn't flood the test area. */
    private static void placeIntake(GameTestHelper h, BlockPos rel, boolean lavalogged) {
        BlockPos abs = h.absolutePos(rel);
        BlockState s = ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(LavaIntakeBlock.LAVALOGGED, lavalogged);
        for (Direction dir : Direction.values()) {
            s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), WaterNetworkBlock.connects(h.getLevel(), abs, s, dir));
        }
        h.getLevel().setBlock(abs, s, Block.UPDATE_CLIENTS);
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

    /** Total grams of ingots of {@code item} in the inventory, and a list of single ingot weights. */
    private static List<Integer> ingotWeights(ServerPlayer p, Item item) {
        List<Integer> w = new ArrayList<>();
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) for (int i = 0; i < s.getCount(); i++) w.add(IngotItem.grams(s));
        }
        return w;
    }

    /** Real click path (ServerPlayerGameMode.useItemOn) with a given hit point (relative to the block) and face. */
    private static void click(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held, boolean sneak, Vec3 hitRel, Direction face) {
        if (held.isEmpty() && !p.getMainHandItem().isEmpty()) {
            ItemStack old = p.getMainHandItem().copy();
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            give(p, old);
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(sneak);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atLowerCornerOf(abs).add(hitRel), face, abs, false));
        p.setShiftKeyDown(false);
    }

    /** Click on the Crafting Forge's top (the basin - not the handle). */
    private static void top(GameTestHelper h, ServerPlayer p, ItemStack held, boolean sneak) {
        click(h, p, CF, held, sneak, new Vec3(0.5, 1.0, 0.5), Direction.UP);
    }

    /** Click on the cast handle (front = west face of a west-facing forge). */
    private static void handle(GameTestHelper h, ServerPlayer p, ItemStack held, boolean sneak) {
        click(h, p, CF, held, sneak, new Vec3(0.1, 0.55, 0.5), Direction.WEST);
    }

    private static CraftingForgeBlockEntity cf(GameTestHelper h) {
        return (CraftingForgeBlockEntity) h.getBlockEntity(CF);
    }

    /** Crafting Forge facing west at CF; lit by a lava-logged intake behind it when {@code lit}. */
    private static void craftingForge(GameTestHelper h, boolean lit) {
        clear(h);
        h.setBlock(CF, ModBlocks.CRAFTING_FORGE.get().defaultBlockState().setValue(CraftingForgeBlock.FACING, Direction.WEST));
        if (lit) {
            placeIntake(h, CF_INTAKE, true);
        }
        recompute(h, CF);
    }

    private static ItemStack folk(int grams) {
        return IngotItem.of(ModItems.FOLKIRON_INGOT.get(), grams);
    }

    private static ItemStack gold(int grams) {
        return IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), grams);
    }

    /**
     * Loads 437 g Folkiron (2 x 200 + 37), 60 g Goldchro (50 + 10) and a Tier 1 Drill through real clicks - the spec's
     * "137 g + 40 g in, 100 g + 25 g used" example with the rebalanced recipe (400 g + 45 g): leftovers 37 g and 15 g.
     */
    private static void loadDrillKit(GameTestHelper h, ServerPlayer p) {
        top(h, p, folk(200).copyWithCount(2), false);
        top(h, p, folk(37), false);
        top(h, p, gold(50), false);
        top(h, p, gold(10), false);
        top(h, p, new ItemStack(ModItems.MINING_DRILL.get()), false);
    }

    private static int itemEntities(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static JsonObject resource(String path) {
        try (InputStream in = PhaseTenTests.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Part A1: crucible on a forge faces the forge's front and tips towards the mold
    // ------------------------------------------------------------------------------------------------

    /**
     * For every forge facing, a crucible placed on it through the real click path - player looking N/E/S/W, sneaking or not -
     * faces the forge's front. Away from a forge it still faces the player.
     */
    @GameTest(template = "empty_long")
    public static void cruciblePlacedOnForgeFacesTheForgeFront(GameTestHelper h) {
        clear(h);
        BlockPos forge = new BlockPos(10, 1, 2), cru = forge.above();
        ServerPlayer p = player(h);
        float[] yaws = {0, 90, 180, 270};
        for (Direction ff : Direction.Plane.HORIZONTAL) {
            h.setBlock(forge, ModBlocks.FORGE.get().defaultBlockState().setValue(ForgeBlock.FACING, ff));
            for (float yaw : yaws) {
                for (boolean sneak : new boolean[]{false, true}) {
                    h.setBlock(cru, Blocks.AIR);
                    p.setYRot(yaw);
                    p.setYHeadRot(yaw);
                    click(h, p, forge, new ItemStack(ModItems.CRUCIBLE.get()), sneak, new Vec3(0.5, 1.0, 0.5), Direction.UP);
                    BlockState s = h.getBlockState(cru);
                    check(h, s.is(ModBlocks.CRUCIBLE.get()), "crucible placed (forge " + ff + ", yaw " + yaw + ", sneak " + sneak + ")");
                    check(h, s.getValue(CrucibleBlock.FACING) == ff, "crucible faces the forge front " + ff + ", not "
                            + s.getValue(CrucibleBlock.FACING) + " (yaw " + yaw + ", sneak " + sneak + ")");
                }
            }
        }
        // not on a forge: spout towards the player (player looks north -> faces south)
        BlockPos free = new BlockPos(14, 0, 2);
        p.setYRot(180);
        click(h, p, free, new ItemStack(ModItems.CRUCIBLE.get()), false, new Vec3(0.5, 1.0, 0.5), Direction.UP);
        check(h, h.getBlockState(free.above()).getValue(CrucibleBlock.FACING) == Direction.SOUTH, "elsewhere it faces the player");
        h.succeed();
    }

    /** A crucible placed by click on a lit forge pours into the mold in front of the FORGE, in all 4 facings. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void pourTargetIsInFrontOfTheForge(GameTestHelper h) {
        clear(h);
        ServerPlayer p = player(h);
        int x = 3;
        for (Direction ff : Direction.Plane.HORIZONTAL) {
            BlockPos forge = new BlockPos(x, 1, 2);
            h.setBlock(forge, ModBlocks.FORGE.get().defaultBlockState().setValue(ForgeBlock.FACING, ff));
            BlockPos mold = forge.relative(ff);
            h.setBlock(mold, ModBlocks.MOLD.get());
            p.setYRot(ff == Direction.NORTH ? 90 : 0);  // look somewhere unrelated
            click(h, p, forge, new ItemStack(ModItems.CRUCIBLE.get()), true, new Vec3(0.5, 1.0, 0.5), Direction.UP);
            BlockPos cru = forge.above();
            ((CrucibleBlockEntity) h.getBlockEntity(cru)).setContents(FOLKIRON, 0, 80);
            int poured = CrucibleBlock.pour(h.getLevel(), h.absolutePos(cru), h.getBlockState(cru), p);
            check(h, poured == 50 && ((MoldBlockEntity) h.getBlockEntity(mold)).grams() == 50,
                    "forge " + ff + ": 50 g poured into the mold in front of the forge, poured " + poured);
            check(h, h.getBlockState(cru).getValue(CrucibleBlock.TIPPING), "tip animation runs");
            x += 5;
        }
        h.succeed();
    }

    /**
     * The tipped model tips TOWARDS the spout for every facing: the spout is on the model's north side, the tip rotation is
     * a NEGATIVE x angle (top moves to -z = north), and each blockstate variant rotates north onto its facing.
     */
    @GameTest(template = "empty_long")
    public static void tippedModelTipsTowardsTheSpout(GameTestHelper h) {
        JsonObject model = resource("/assets/holeydigsite/models/block/crucible_tipped.json");
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject rot = e.getAsJsonObject().getAsJsonObject("rotation");
            check(h, rot != null && rot.get("axis").getAsString().equals("x") && rot.get("angle").getAsFloat() < 0,
                    "every element tips with a negative x angle (towards north / the spout): " + rot);
        }
        boolean spoutNorth = false;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            var to = e.getAsJsonObject().getAsJsonArray("to");
            spoutNorth |= to.get(2).getAsFloat() <= 2.0F && e.getAsJsonObject().getAsJsonArray("from").get(2).getAsFloat() < 1.0F;
        }
        check(h, spoutNorth, "the spout sits on the model's north side");
        JsonObject states = resource("/assets/holeydigsite/blockstates/crucible.json");
        Map<String, Integer> expectY = Map.of("north", 0, "east", 90, "south", 180, "west", 270);
        int seen = 0;
        for (JsonElement part : states.getAsJsonArray("multipart")) {
            JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
            JsonObject apply = part.getAsJsonObject().getAsJsonObject("apply");
            if (when.has("facing") && apply.get("model").getAsString().endsWith("crucible_tipped")) {
                String f = when.get("facing").getAsString();
                int y = apply.has("y") ? apply.get("y").getAsInt() : 0;
                check(h, y == expectY.get(f), "tipped variant " + f + " rotates north onto " + f + " (y=" + y + ")");
                // north rotated clockwise by y lands on the facing: the tip direction == FACING == the pour direction
                Direction tip = Direction.NORTH;
                for (int i = 0; i < y / 90; i++) tip = tip.getClockWise();
                check(h, tip.getName().equals(f), "tip direction " + tip + " == facing " + f);
                seen++;
            }
        }
        check(h, seen == 4, "all 4 facings have a tipped variant: " + seen);
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Part A2: action-bar messages
    // ------------------------------------------------------------------------------------------------

    /** Every action-bar message (lang key message.holeydigsite.*) is at most ~55 characters with typical arguments. */
    @GameTest(template = "empty_long")
    public static void actionBarMessagesAreShort(GameTestHelper h) {
        JsonObject lang = resource("/assets/holeydigsite/lang/en_us.json");
        List<String> tooLong = new ArrayList<>();
        int n = 0;
        for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
            if (!e.getKey().startsWith("message.holeydigsite.")) continue;
            n++;
            // a typical argument is ~8 characters ("Folkiron", "Pan", "3 gold", "137")
            String text = e.getValue().getAsString().replaceAll("%(\\d\\$)?[sd]", "XXXXXXXX");
            if (text.length() > 55) tooLong.add(e.getKey() + " (" + text.length() + "): " + text);
        }
        check(h, n > 50, "found the messages: " + n);
        check(h, tooLong.isEmpty(), "too long: " + tooLong);
        // the empty Sell Pad's message is the short one from the spec
        check(h, lang.get("message.holeydigsite.sell_pad.help").getAsString().equals("Sell Pad empty - right-click with items to add them"),
                "sell pad help text");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Part C: Crafting Forge
    // ------------------------------------------------------------------------------------------------

    /** The five recipes are loaded in order with the specified grams, parts and results. */
    @GameTest(template = "empty_long")
    public static void forgeRecipesAreLoaded(GameTestHelper h) {
        List<ForgeRecipes.Entry> all = ForgeRecipes.all(h.getLevel().registryAccess());
        check(h, all.size() >= 5, "at least the 5 Phase 10 recipes, got " + all.size());   // Phase 11 adds 3
        check(h, all.get(0).id().equals(R_DRILL) && all.stream().anyMatch(e -> e.id().equals(R_BELT)), "order starts with the drill: " + all.stream().map(ForgeRecipes.Entry::id).toList());
        ForgeRecipe d = all.get(0).recipe();
        check(h, d.grams(FOLKIRON) == 400 && d.grams(GOLDCHRO) == 45 && d.partCount(ModItems.MINING_DRILL.get()) == 1
                && d.resultStack().is(ModItems.MINING_DRILL_T2.get()), "drill recipe");
        ForgeRecipe s = ForgeRecipes.find(h.getLevel().registryAccess(), R_SLUICE).recipe();
        check(h, s.grams(FOLKIRON) == 400 && s.grams(GOLDCHRO) == 45 && s.partCount(ModItems.SLUICE_BOX.get()) == 1, "sluice recipe");
        ForgeRecipe pan = ForgeRecipes.find(h.getLevel().registryAccess(), HoleyDigsite.id("large_pan")).recipe();
        check(h, pan.grams(FOLKIRON) == 1200 && pan.grams(GOLDCHRO) == 0 && pan.partCount(ModItems.PAN.get()) == 1, "large pan recipe");
        ForgeRecipe brush = ForgeRecipes.find(h.getLevel().registryAccess(), HoleyDigsite.id("goldchro_brush")).recipe();
        check(h, brush.grams(GOLDCHRO) == 16 && brush.partCount(ModItems.BRUSH.get()) == 1, "brush recipe");
        ForgeRecipe belt = ForgeRecipes.find(h.getLevel().registryAccess(), R_BELT).recipe();
        check(h, belt.grams(FOLKIRON) == 850 && belt.partCount(ModItems.CLUMP_BELT.get()) == 4
                && belt.resultStack().getCount() == 4 && belt.resultStack().is(ModItems.FAST_CLUMP_BELT.get()), "belt recipe: 4 -> 4");
        h.succeed();
    }

    /** Ingots of any weight pool as grams per metal; parts are stored as items; chunks, rubies, dirty pans and junk are refused. */
    @GameTest(template = "empty_long")
    public static void loadingPoolsGramsAndStoresParts(GameTestHelper h) {
        craftingForge(h, true);
        ServerPlayer p = player(h);
        top(h, p, folk(50).copyWithCount(2), false);
        top(h, p, folk(37), false);
        top(h, p, gold(40), false);
        check(h, cf(h).grams(FOLKIRON) == 137 && cf(h).grams(GOLDCHRO) == 40, "pooled: " + cf(h).pool());
        check(h, p.getMainHandItem().isEmpty(), "whole stack loaded");
        top(h, p, new ItemStack(ModItems.MINING_DRILL.get()), false);
        top(h, p, new ItemStack(ModItems.CLUMP_BELT.get(), 6), false);
        check(h, cf(h).partCount(ModItems.MINING_DRILL.get()) == 1 && cf(h).partCount(ModItems.CLUMP_BELT.get()) == 6, "parts: " + cf(h).parts());
        // refusals: the item stays in the hand
        ItemStack[] refused = {new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 3), new ItemStack(ModItems.RUBY_LARGE.get()),
                new ItemStack(Items.DIRT, 5), new ItemStack(Items.IRON_INGOT), ModBlocks.PAN.get().createStack(2)};
        for (ItemStack r : refused) {
            ItemStack held = r.copy();
            top(h, p, held, false);
            check(h, p.getMainHandItem().getCount() == r.getCount(), "refused: " + r);
        }
        check(h, cf(h).check(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get())) == CraftingForgeBlockEntity.Refusal.CHUNK, "chunk reason");
        check(h, cf(h).check(ModBlocks.PAN.get().createStack(2)) == CraftingForgeBlockEntity.Refusal.DIRTY, "pan with dirt: dirty");
        check(h, cf(h).check(new ItemStack(ModItems.PAN.get())) == CraftingForgeBlockEntity.Refusal.NONE, "an empty pan is a part");
        check(h, cf(h).grams(FOLKIRON) == 137 && cf(h).parts().size() == 2, "nothing else went in");
        h.succeed();
    }

    /** Sneak-right-click with anything loads every ingot from the inventory (both metals), nothing else. */
    @GameTest(template = "empty_long")
    public static void sneakLoadsEveryIngot(GameTestHelper h) {
        craftingForge(h, true);
        ServerPlayer p = player(h);
        give(p, folk(50).copyWithCount(3));
        give(p, gold(25));
        give(p, new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 4));
        top(h, p, folk(10), true);
        check(h, cf(h).grams(FOLKIRON) == 160 && cf(h).grams(GOLDCHRO) == 25, "all ingots loaded: " + cf(h).pool());
        check(h, count(p, ModItems.FOLKIRON_INGOT.get()) == 0 && count(p, ModItems.GOLDCHRO_INGOT.get()) == 0, "none left");
        check(h, count(p, ModItems.FOLKIRON_CHUNK_LARGE.get()) == 4, "chunks stay");
        h.succeed();
    }

    /** Empty hand on the forge (not the handle) cycles the recipes forward, sneaking backwards, wrapping both ways. */
    @GameTest(template = "empty_long")
    public static void recipeCyclingBothWays(GameTestHelper h) {
        craftingForge(h, true);
        ServerPlayer p = player(h);
        check(h, cf(h).recipe().id().equals(R_DRILL), "first recipe by default: " + cf(h).recipe().id());
        top(h, p, ItemStack.EMPTY, false);
        check(h, cf(h).recipe().id().equals(R_SLUICE), "next: " + cf(h).recipe().id());
        top(h, p, ItemStack.EMPTY, true);
        check(h, cf(h).recipe().id().equals(R_DRILL), "back: " + cf(h).recipe().id());
        top(h, p, ItemStack.EMPTY, true);
        List<ForgeRecipes.Entry> all = ForgeRecipes.all(h.getLevel().registryAccess());
        check(h, cf(h).recipe().id().equals(all.get(all.size() - 1).id()), "wraps backwards to the last: " + cf(h).recipe().id());
        top(h, p, ItemStack.EMPTY, false);
        check(h, cf(h).recipe().id().equals(R_DRILL), "wraps forwards to the first: " + cf(h).recipe().id());
        // clicking the handle does NOT cycle
        handle(h, p, ItemStack.EMPTY, false);
        check(h, cf(h).recipe().id().equals(R_DRILL) && !cf(h).isCasting(), "handle doesn't cycle (and can't cast: nothing loaded)");
        h.succeed();
    }

    /** Casting needs lava and every requirement; refusals change nothing. */
    @GameTest(template = "empty_long")
    public static void castingNeedsLavaAndAllRequirements(GameTestHelper h) {
        craftingForge(h, false);
        ServerPlayer p = player(h);
        loadDrillKit(h, p);
        handle(h, p, ItemStack.EMPTY, false);
        check(h, !cf(h).isCasting() && !h.getBlockState(CF).getValue(CraftingForgeBlock.CASTING), "unlit: no cast");
        placeIntake(h, CF_INTAKE, true);
        recompute(h, CF);
        check(h, h.getBlockState(CF).getValue(CraftingForgeBlock.LIT), "lit by the lava network");
        // switch to the sluice recipe: no Tier 1 sluice loaded
        cf(h).select(R_SLUICE);
        handle(h, p, ItemStack.EMPTY, false);
        check(h, !cf(h).isCasting(), "missing part: no cast");
        var missing = cf(h).missing(cf(h).recipe().recipe());
        check(h, missing != null && missing.part() == ModItems.SLUICE_BOX.get(), "the sluice is what's missing");
        // drill recipe with too little Goldchro
        cf(h).select(R_DRILL);
        CraftingForgeBlockEntity f = cf(h);
        List<ItemStack> back = f.takeBack();
        check(h, back.size() == 3, "took back 2 ingots + the drill: " + back);
        top(h, p, folk(400), false);
        top(h, p, gold(40), false);
        top(h, p, new ItemStack(ModItems.MINING_DRILL.get()), false);
        handle(h, p, ItemStack.EMPTY, false);
        check(h, !cf(h).isCasting(), "40 g of 45 g Goldchro: no cast");
        missing = cf(h).missing(cf(h).recipe().recipe());
        check(h, missing != null && GOLDCHRO.equals(missing.metal()) && missing.amount() == 5, "needs 5 g more Goldchro");
        top(h, p, gold(5), false);
        handle(h, p, new ItemStack(Items.STICK), false);     // holding anything works on the handle
        check(h, cf(h).isCasting() && h.getBlockState(CF).getValue(CraftingForgeBlock.CASTING), "now it casts (handle pulled)");
        h.succeed();
    }

    /**
     * The spec example through real clicks (rebalanced recipe 400 g + 45 g): 437 g Folkiron + 60 g Goldchro + a Tier 1 Drill,
     * the handle pulled: 5 s later the output is a Tier 2 Drill + a 37 g Folkiron ingot + a 15 g Goldchro ingot; an empty hand
     * collects it all.
     */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void castByHandleReturnsExactLeftovers(GameTestHelper h) {
        craftingForge(h, true);
        ServerPlayer p = player(h);
        loadDrillKit(h, p);
        handle(h, p, ItemStack.EMPTY, false);
        check(h, cf(h).isCasting(), "cast started");
        h.startSequence()
                .thenExecuteAfter(CraftingForgeBlockEntity.CAST_TICKS - 10, () -> {
                    check(h, cf(h).isCasting() && !cf(h).hasOutput(), "still casting just before 5 s");
                    top(h, p, folk(10), false);
                    check(h, cf(h).grams(FOLKIRON) == 437, "can't load while casting");
                })
                .thenExecuteAfter(15, () -> {
                    CraftingForgeBlockEntity f = cf(h);
                    check(h, !f.isCasting() && !h.getBlockState(CF).getValue(CraftingForgeBlock.CASTING), "cast done, handle up");
                    List<ItemStack> out = f.output();
                    check(h, out.size() == 3, "product + 2 leftover ingots: " + out);
                    check(h, out.get(0).is(ModItems.MINING_DRILL_T2.get()) && out.get(0).getCount() == 1, "Tier 2 Drill");
                    check(h, out.stream().anyMatch(s -> s.is(ModItems.FOLKIRON_INGOT.get()) && IngotItem.grams(s) == 37 && s.getCount() == 1),
                            "one 37 g Folkiron ingot");
                    check(h, out.stream().anyMatch(s -> s.is(ModItems.GOLDCHRO_INGOT.get()) && IngotItem.grams(s) == 15 && s.getCount() == 1),
                            "one 15 g Goldchro ingot");
                    check(h, f.pool().isEmpty() && f.parts().isEmpty(), "pool and parts emptied into the output");
                    check(h, !CraftingForgeBlock.tryCast(h.getLevel(), h.absolutePos(CF), null), "no new cast while output waits");
                    check(h, f.check(folk(10)) == CraftingForgeBlockEntity.Refusal.OUTPUT, "no loading while output waits");
                    top(h, p, ItemStack.EMPTY, false);   // collect
                    check(h, count(p, ModItems.MINING_DRILL_T2.get()) == 1, "drill collected");
                    check(h, ingotWeights(p, ModItems.FOLKIRON_INGOT.get()).stream().sorted().toList().equals(List.of(10, 37)),
                            "37 g Folkiron collected (+ the refused 10 g one): " + ingotWeights(p, ModItems.FOLKIRON_INGOT.get()));
                    check(h, ingotWeights(p, ModItems.GOLDCHRO_INGOT.get()).equals(List.of(15)), "15 g Goldchro collected");
                    check(h, cf(h).isEmpty(), "forge empty");
                })
                .thenSucceed();
    }

    /** Unused parts come back with the output; nothing loaded is lost. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void unusedPartsAreReturned(GameTestHelper h) {
        craftingForge(h, true);
        ServerPlayer p = player(h);
        top(h, p, folk(400), false);
        top(h, p, gold(45), false);
        top(h, p, new ItemStack(ModItems.MINING_DRILL.get(), 2), false);
        top(h, p, new ItemStack(ModItems.PAN.get()), false);
        top(h, p, new ItemStack(ModItems.CLUMP_BELT.get(), 3), false);
        handle(h, p, ItemStack.EMPTY, false);
        h.runAfterDelay(CraftingForgeBlockEntity.CAST_TICKS + 5, () -> {
            handle(h, p, ItemStack.EMPTY, false);   // the handle collects too
            check(h, count(p, ModItems.MINING_DRILL_T2.get()) == 1, "1 Tier 2 Drill");
            check(h, count(p, ModItems.MINING_DRILL.get()) == 1, "the second Tier 1 Drill came back");
            check(h, count(p, ModItems.PAN.get()) == 1 && count(p, ModItems.CLUMP_BELT.get()) == 3, "pan and belts came back");
            check(h, count(p, ModItems.FOLKIRON_INGOT.get()) == 0 && count(p, ModItems.GOLDCHRO_INGOT.get()) == 0,
                    "exact grams: no leftover ingots");
            h.succeed();
        });
    }

    /** A redstone rising edge casts (once per edge); while it stays powered no second cast starts. */
    @GameTest(template = "empty_long", timeoutTicks = 600)
    public static void castByRedstoneOncePerEdge(GameTestHelper h) {
        craftingForge(h, true);
        ServerPlayer p = player(h);
        cf(h).select(R_BELT);
        top(h, p, folk(900), false);
        top(h, p, new ItemStack(ModItems.CLUMP_BELT.get(), 8), false);
        BlockPos rs = new BlockPos(4, 1, 1);
        h.setBlock(rs, Blocks.REDSTONE_BLOCK);
        check(h, cf(h).isCasting(), "rising edge starts a cast");
        h.startSequence()
                .thenExecuteAfter(CraftingForgeBlockEntity.CAST_TICKS + 5, () -> {
                    check(h, cf(h).hasOutput() && cf(h).output().get(0).is(ModItems.FAST_CLUMP_BELT.get())
                            && cf(h).output().get(0).getCount() == 4, "4 Fast Clump Belts: " + cf(h).output());
                    top(h, p, ItemStack.EMPTY, false); // collect: 4 fast belts + 50 g Folkiron + 4 belts
                    check(h, count(p, ModItems.FAST_CLUMP_BELT.get()) == 4 && count(p, ModItems.CLUMP_BELT.get()) == 4
                            && ingotWeights(p, ModItems.FOLKIRON_INGOT.get()).equals(List.of(50)), "collected belts + 50 g");
                    // load again while the redstone is still on: no edge, no cast
                    top(h, p, folk(850), false);
                    top(h, p, new ItemStack(ModItems.CLUMP_BELT.get(), 4), false);
                    h.setBlock(new BlockPos(4, 1, 3), Blocks.REDSTONE_BLOCK);   // a second source: still no edge
                })
                .thenExecuteAfter(20, () -> {
                    check(h, !cf(h).isCasting() && !cf(h).hasOutput(), "still powered: no second cast");
                    h.setBlock(rs, Blocks.AIR);
                    h.setBlock(new BlockPos(4, 1, 3), Blocks.AIR);
                })
                .thenExecuteAfter(2, () -> {
                    check(h, !h.getBlockState(CF).getValue(CraftingForgeBlock.POWERED), "power off");
                    h.setBlock(rs, Blocks.REDSTONE_BLOCK);
                    check(h, cf(h).isCasting(), "new rising edge: cast");
                })
                .thenSucceed();
    }

    /** Sneak + empty hand on the handle takes everything back without casting: one ingot per metal, parts as items. */
    @GameTest(template = "empty_long")
    public static void takeBackWithoutCasting(GameTestHelper h) {
        craftingForge(h, true);
        ServerPlayer p = player(h);
        loadDrillKit(h, p);
        top(h, p, new ItemStack(ModItems.BRUSH.get()), false);
        handle(h, p, ItemStack.EMPTY, true);
        check(h, !cf(h).isCasting() && cf(h).isEmpty(), "forge empty, no cast");
        check(h, ingotWeights(p, ModItems.FOLKIRON_INGOT.get()).equals(List.of(437)), "one 437 g Folkiron ingot: " + ingotWeights(p, ModItems.FOLKIRON_INGOT.get()));
        check(h, ingotWeights(p, ModItems.GOLDCHRO_INGOT.get()).equals(List.of(60)), "one 60 g Goldchro ingot");
        check(h, count(p, ModItems.MINING_DRILL.get()) == 1 && count(p, ModItems.BRUSH.get()) == 1, "parts back");
        h.succeed();
    }

    /** Breaking drops the forge + pool as ingots + parts (survival player, real break). Also mid-cast: nothing consumed. */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void breakingDropsEverything(GameTestHelper h) {
        craftingForge(h, true);
        ServerPlayer p = player(h);
        loadDrillKit(h, p);
        handle(h, p, ItemStack.EMPTY, false);
        check(h, cf(h).isCasting(), "casting");
        h.runAfterDelay(20, () -> {
            p.gameMode.destroyBlock(h.absolutePos(CF));
            check(h, h.getBlockState(CF).isAir(), "broken");
            check(h, itemEntities(h, ModItems.CRAFTING_FORGE.get()) == 1, "drops itself");
            check(h, itemEntities(h, ModItems.MINING_DRILL.get()) == 1, "drops the drill");
            check(h, itemEntities(h, ModItems.MINING_DRILL_T2.get()) == 0, "the cast was cancelled");
            List<Integer> folk = new ArrayList<>(), gold = new ArrayList<>();
            for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
                if (e.getItem().is(ModItems.FOLKIRON_INGOT.get())) folk.add(IngotItem.grams(e.getItem()));
                if (e.getItem().is(ModItems.GOLDCHRO_INGOT.get())) gold.add(IngotItem.grams(e.getItem()));
            }
            check(h, folk.equals(List.of(437)) && gold.equals(List.of(60)), "ingots of the pooled grams: " + folk + " " + gold);
            h.succeed();
        });
    }

    /** A hopper above inserts ingots and parts; chunks stay in the hopper; nothing can be extracted. */
    @GameTest(template = "empty_long", timeoutTicks = 120)
    public static void hopperInsertsOnly(GameTestHelper h) {
        craftingForge(h, true);
        BlockPos hop = CF.above();
        h.setBlock(hop, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity hopper = (HopperBlockEntity) h.getBlockEntity(hop);
        hopper.setItem(0, folk(50).copyWithCount(2));
        hopper.setItem(1, new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 1));
        hopper.setItem(2, new ItemStack(ModItems.MINING_DRILL.get(), 1));
        IItemHandler cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(CF), Direction.UP);
        check(h, cap != null, "forge has an item handler");
        check(h, cap.insertItem(0, new ItemStack(ModItems.RUBY_LARGE.get()), false).getCount() == 1, "rubies refused");
        h.runAfterDelay(80, () -> {
            check(h, cf(h).grams(FOLKIRON) == 100, "hopper loaded 2 x 50 g: " + cf(h).pool());
            check(h, cf(h).partCount(ModItems.MINING_DRILL.get()) == 1, "and the drill");
            check(h, hopper.getItem(1).is(ModItems.FOLKIRON_CHUNK_LARGE.get()), "the chunk stays in the hopper");
            check(h, cap.extractItem(0, 64, false).isEmpty() && cap.getStackInSlot(0).isEmpty(), "nothing extracted");
            h.succeed();
        });
    }

    /** The shop sells the Crafting Forge for 40 gold through Buy Pad + button. */
    @GameTest(template = "empty_long")
    public static void shopSellsTheCraftingForge(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        var entry = ShopCatalogue.find(reg, ModItems.CRAFTING_FORGE.get());
        check(h, entry.isPresent() && entry.get().price() == 40 * 64 && entry.get().count() == 1, "40 gold");
        for (Item t2 : new Item[]{ModItems.MINING_DRILL_T2.get(), ModItems.SLUICE_BOX_T2.get(), ModItems.LARGE_PAN.get(),
                ModItems.GOLDCHRO_BRUSH.get(), ModItems.FAST_CLUMP_BELT.get()}) {
            check(h, ShopCatalogue.find(reg, t2).isEmpty(), t2 + " is NOT sold in the shop");
        }
        clear(h);
        BlockPos btn = new BlockPos(4, 1, 2), buy = new BlockPos(5, 1, 2), pan = new BlockPos(4, 1, 3);
        h.setBlock(btn, ModBlocks.SHOP_BUTTON.get());
        h.setBlock(buy, ModBlocks.BUY_PAD.get());
        h.setBlock(pan, ModBlocks.MONEY_PAN.get());
        ((MoneyPanBlockEntity) h.getBlockEntity(pan)).deposit(2560);
        ServerPlayer p = player(h);
        click(h, p, buy, BlueprintItem.of(ModItems.CRAFTING_FORGE.get(), 1), false, new Vec3(0.5, 0.3, 0.5), Direction.UP);
        click(h, p, btn, ItemStack.EMPTY, false, new Vec3(0.5, 0.5, 0.5), Direction.UP);
        check(h, count(p, ModItems.CRAFTING_FORGE.get()) == 1, "bought a Crafting Forge");
        check(h, ((MoneyPanBlockEntity) h.getBlockEntity(pan)).balance() == 0, "paid exactly 40 gold");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // Part B: Tier 2
    // ------------------------------------------------------------------------------------------------

    /** Intake (x=1, basin) -> drill (x=2, facing east) on {@code rock} -> pan (x=3) in row z. */
    private static BlockPos drillRow(GameTestHelper h, int z, Block drillBlock, Block rock, boolean water) {
        h.setBlock(new BlockPos(0, 1, z), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z - 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z + 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, z), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, water));
        h.setBlock(new BlockPos(2, 0, z), rock);
        BlockPos drill = new BlockPos(2, 1, z);
        h.setBlock(drill, drillBlock.defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        return drill;
    }

    /** Tier 2 drill: a clump every 150 ticks (not before), rock tiers, water power, no item handler. */
    @GameTest(template = "empty_long", timeoutTicks = 500)
    public static void tier2DrillIsTwiceAsFast(GameTestHelper h) {
        clear(h);
        BlockPos d1 = drillRow(h, 1, ModBlocks.MINING_DRILL_T2.get(), ModBlocks.DIRTROCK.get(), true);
        BlockPos d3 = drillRow(h, 3, ModBlocks.MINING_DRILL_T2.get(), ModBlocks.DEEP_DIRTROCK.get(), true);
        BlockPos pan1 = new BlockPos(3, 1, 1), pan3 = new BlockPos(3, 1, 3);
        h.setBlock(pan1, ModBlocks.PAN.get());
        h.setBlock(pan3, ModBlocks.PAN.get());
        // a dry T2 drill on Dirtrock and a powered one on stone stay off
        BlockPos dry = new BlockPos(8, 1, 2);
        h.setBlock(new BlockPos(7, 1, 2), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, false));
        h.setBlock(dry.below(), ModBlocks.DIRTROCK.get());
        h.setBlock(dry, ModBlocks.MINING_DRILL_T2.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        int cycle = MiningDrillBlock.CYCLE_TICKS_T2;
        check(h, cycle == 150 && ModBlocks.MINING_DRILL_T2.get().cycleTicks() == 150, "T2 cycle 150");
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(h, h.getBlockState(d1).getValue(MiningDrillBlock.STATUS) == DrillStatus.WORKING, "T2 drill works");
                    check(h, h.getBlockState(dry).getValue(MiningDrillBlock.STATUS) == DrillStatus.NO_POWER, "dry T2 drill has no power");
                    Component msg = MiningDrillBlock.statusMessage(h.getLevel(), h.absolutePos(d1), h.getBlockState(d1),
                            (MiningDrillBlockEntity) h.getBlockEntity(d1));
                    check(h, key(msg).equals("message.holeydigsite.drill.working"), "status message");
                    check(h, h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(d1), null) == null, "no item handler");
                })
                .thenExecuteAfter(cycle - 12, () -> check(h, PanBlock.fill(h.getBlockState(pan1)) == 0, "nothing before 150 ticks"))
                .thenExecuteAfter(15, () -> {
                    check(h, PanBlock.fill(h.getBlockState(pan1)) == 1, "a clump at ~150 ticks");
                    check(h, PanBlock.fill(h.getBlockState(pan3)) == 1 && h.getBlockState(pan3).getValue(PanBlock.TIER) == 2,
                            "on Deep Dirtrock: a tier 2 clump");
                })
                .thenExecuteAfter(cycle, () -> {
                    check(h, PanBlock.fill(h.getBlockState(pan1)) == 2, "2 clumps at ~300 ticks (Tier 1 would have 1)");
                    check(h, itemEntities(h, ModItems.DIRT_CLUMP.get()) == 0, "never drops clumps");
                })
                .thenSucceed();
    }

    /** Tier 2 sluice: queue 8, a clump every 50 ticks, tray of 18 slots, side water ports only, no item handler. */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void tier2SluiceStats(GameTestHelper h) {
        clear(h);
        BlockPos sl = new BlockPos(4, 1, 2);
        h.setBlock(sl, ModBlocks.SLUICE_BOX_T2.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        BlockPos in = new BlockPos(4, 1, 1);
        for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.WEST}) h.setBlock(in.relative(d), Blocks.STONE);
        h.setBlock(in, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        SluiceBoxBlockEntity s = (SluiceBoxBlockEntity) h.getBlockEntity(sl);
        ServerPlayer p = player(h);
        check(h, s.stats().cycleTicks() == 50 && s.stats().maxQueue() == 8 && s.stats().traySlots() == 18, "stats " + s.stats());
        check(h, h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(sl), null) == null, "no item handler");
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    click(h, p, sl, new ItemStack(ModItems.DIRT_CLUMP.get(), 12), true, new Vec3(0.5, 0.5, 0.5), Direction.UP);
                    check(h, s.queued() == 8 && p.getMainHandItem().getCount() == 4, "sneak fills the queue to 8: " + s.queued());
                    check(h, h.getBlockState(sl).getValue(SluiceBoxBlock.STATUS) == SluiceStatus.WORKING, "working");
                })
                .thenExecuteAfter(45, () -> check(h, s.queued() == 8, "nothing washed before 50 ticks"))
                .thenExecuteAfter(8, () -> check(h, s.queued() == 7, "one washed at ~50 ticks: " + s.queued()))
                .thenExecuteAfter(50, () -> {
                    check(h, s.queued() == 6, "another 50 ticks later: " + s.queued());
                    // fill the tray to 17 distinct stacks: still running; the 18th stops it
                    s.takeTray();
                    for (int i = 0; i < 17; i++) s.addToTray(new ItemStack(i % 2 == 0 ? Items.STICK : Items.APPLE, 64));
                    check(h, !s.isTrayFull(), "17 slots used: not full");
                    s.addToTray(new ItemStack(Items.BONE));
                    check(h, s.isTrayFull(), "18 slots used: full");
                    ((SluiceBoxBlock) h.getBlockState(sl).getBlock()).refreshStatus(h.getLevel(), h.absolutePos(sl), h.getBlockState(sl));
                    check(h, h.getBlockState(sl).getValue(SluiceBoxBlock.STATUS) == SluiceStatus.STOPPED, "stopped with a full tray");
                    // water ports: sides only
                    BlockState st = h.getBlockState(sl);
                    var b = (SluiceBoxBlock) st.getBlock();
                    check(h, b.hasWaterConnection(st, Direction.NORTH) && b.hasWaterConnection(st, Direction.SOUTH)
                            && !b.hasWaterConnection(st, Direction.EAST) && !b.hasWaterConnection(st, Direction.WEST), "ports on the sides");
                    // breaking drops the sluice + 6 queued clumps + the tray
                    p.gameMode.destroyBlock(h.absolutePos(sl));
                    check(h, itemEntities(h, ModItems.SLUICE_BOX_T2.get()) == 1 && itemEntities(h, ModItems.DIRT_CLUMP.get()) == 6,
                            "drops itself and its clumps");
                })
                .thenSucceed();
    }

    /** Large Pan: 8 scoops from shovel, clumps, hopper handler and drill input; keeps fill + tier when picked up and placed. */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void largePanHoldsEightFromEveryInput(GameTestHelper h) {
        clear(h);
        BlockPos pan = new BlockPos(6, 1, 2);
        h.setBlock(pan, ModBlocks.LARGE_PAN.get());
        ServerPlayer p = player(h);
        check(h, PanBlock.maxFill(h.getBlockState(pan)) == 8, "max 8");
        // shovel
        click(h, p, pan, ShovelHelper.createFullShovel(), false, new Vec3(0.5, 0.3, 0.5), Direction.UP);
        check(h, PanBlock.fill(h.getBlockState(pan)) == 1, "shovel +1");
        // clumps by hand
        for (int i = 0; i < 3; i++) click(h, p, pan, new ItemStack(ModItems.DIRT_CLUMP.get()), false, new Vec3(0.5, 0.3, 0.5), Direction.UP);
        check(h, PanBlock.fill(h.getBlockState(pan)) == 4, "clumps +3 = 4 (a normal pan would be full)");
        // hopper item handler
        IItemHandler cap = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pan), Direction.UP);
        check(h, cap != null && cap.getSlotLimit(0) == 8, "handler, limit 8");
        ItemStack rest = cap.insertItem(0, new ItemStack(ModItems.DIRT_CLUMP.get(), 10), false);
        check(h, rest.getCount() == 6 && PanBlock.fill(h.getBlockState(pan)) == 8, "hopper fills to 8, 6 handed back: " + rest);
        click(h, p, pan, new ItemStack(ModItems.DIRT_CLUMP.get()), false, new Vec3(0.5, 0.3, 0.5), Direction.UP);
        check(h, PanBlock.fill(h.getBlockState(pan)) == 8 && p.getMainHandItem().getCount() == 1, "full at 8: clump stays in the hand");
        // pick up: keeps 8; place again: 8
        click(h, p, pan, ItemStack.EMPTY, false, new Vec3(0.5, 0.3, 0.5), Direction.UP);
        check(h, h.getBlockState(pan).isAir() && count(p, ModItems.LARGE_PAN.get()) == 1, "picked up");
        ItemStack item = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.LARGE_PAN.get())) item = s;
        check(h, PanBlock.getFill(item) == 8 && PanBlock.getMaxFill(item) == 8, "item keeps fill 8: " + PanBlock.getFill(item));
        click(h, p, pan.below(), item.copy(), false, new Vec3(0.5, 1.0, 0.5), Direction.UP);
        check(h, h.getBlockState(pan).is(ModBlocks.LARGE_PAN.get()) && PanBlock.fill(h.getBlockState(pan)) == 8, "placed with fill 8");
        // tier: a deep pan stays deep when picked up, and refuses tier 1 dirt
        h.setBlock(pan, PanBlock.withFill(ModBlocks.LARGE_PAN.get().defaultBlockState(), 5).setValue(PanBlock.TIER, 2));
        click(h, p, pan, new ItemStack(ModItems.DIRT_CLUMP.get()), false, new Vec3(0.5, 0.3, 0.5), Direction.UP);
        check(h, PanBlock.fill(h.getBlockState(pan)) == 5, "tier 1 clump refused by a deep pan");
        ItemStack deep = ModBlocks.LARGE_PAN.get().createStack(5, 2);
        check(h, PanBlock.getFill(deep) == 5 && PanBlock.getTier(deep) == 2, "item keeps tier 2");
        // drill output into a Large Pan
        clear(h);
        BlockPos d = drillRow(h, 2, ModBlocks.MINING_DRILL.get(), ModBlocks.DIRTROCK.get(), true);
        BlockPos lp = new BlockPos(3, 1, 2);
        h.setBlock(lp, PanBlock.withFill(ModBlocks.LARGE_PAN.get().defaultBlockState(), 7));
        h.runAfterDelay(3, () -> {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("WaterPowered", true);
            tag.putInt("Progress", MiningDrillBlock.CYCLE_TICKS - 3);
            h.getBlockEntity(d).loadCustomOnly(tag, h.getLevel().registryAccess());
            h.runAfterDelay(10, () -> {
                check(h, PanBlock.fill(h.getBlockState(lp)) == 8, "drill put its clump into the Large Pan (7 -> 8)");
                h.succeed();
            });
        });
    }

    /** A belt into a Large Pan delivers up to 8; at 8 the next clumps bounce off and are voided (no items). */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void largePanBeltBouncesAtEight(GameTestHelper h) {
        clear(h);
        BlockPos b = new BlockPos(3, 1, 2), pan = new BlockPos(4, 1, 2);
        h.setBlock(pan, PanBlock.withFill(ModBlocks.LARGE_PAN.get().defaultBlockState(), 6));
        place(h, b, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        check(h, h.getBlockState(b).getValue(ClumpBeltBlock.SPOUT), "belt shows the spout for a Large Pan");
        ClumpBeltBlockEntity belt = (ClumpBeltBlockEntity) h.getBlockEntity(b);
        check(h, belt.insertByPlayer(4) == 4, "4 clumps on");
        h.runAfterDelay(120, () -> {
            check(h, PanBlock.fill(h.getBlockState(pan)) == 8, "pan filled to 8: " + PanBlock.fill(h.getBlockState(pan)));
            check(h, belt.isEmpty(), "the other 2 bounced off");
            check(h, itemEntities(h, ModItems.DIRT_CLUMP.get()) == 0, "voided, no items");
            h.succeed();
        });
    }

    /** Goldchro Brush: one scoop every 20 ticks (the Brush: 40). Real use-item loop like the Phase 2 tests. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void goldchroBrushPansEveryTwentyTicks(GameTestHelper h) {
        BlockPos pan = new BlockPos(2, 1, 2), stand = new BlockPos(2, 1, 0);
        for (BlockPos bp : BlockPos.betweenClosed(0, 0, 0, 4, 3, 4)) h.setBlock(bp, bp.getY() == 0 ? Blocks.STONE : Blocks.AIR);
        h.setBlock(pan, ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 4).setValue(PanBlock.WATERLOGGED, true));
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 feet = Vec3.atBottomCenterOf(h.absolutePos(stand));
        p.moveTo(feet.x, feet.y, feet.z);
        ItemStack brush = new ItemStack(ModItems.GOLDCHRO_BRUSH.get());
        check(h, brush.getItem() instanceof BrushItem b && b.cycleTicks() == 20 && Panning.cycleTicks(brush) == 20, "20 ticks");
        check(h, Panning.cycleTicks(new ItemStack(ModItems.BRUSH.get())) == 40, "normal brush 40");
        p.setItemInHand(InteractionHand.MAIN_HAND, brush);
        Vec3 topV = Vec3.atBottomCenterOf(h.absolutePos(pan)).add(0, 0.2, 0);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, topV);
        h.useBlock(pan, p, new BlockHitResult(topV, Direction.UP, h.absolutePos(pan), false));
        check(h, p.isUsingItem(), "panning started");
        for (int i = 0; i < 19; i++) p.doTick();
        check(h, PanBlock.fill(h.getBlockState(pan)) == 4, "no scoop before 20 ticks");
        p.doTick();
        check(h, PanBlock.fill(h.getBlockState(pan)) == 3, "one scoop at 20 ticks");
        for (int i = 0; i < 60; i++) p.doTick();
        check(h, PanBlock.fill(h.getBlockState(pan)) == 0, "4 scoops in 80 ticks (the Brush needs 160)");
        h.succeed();
    }

    /** Fast belts: 10 ticks per block (2 blocks/s); fast <-> normal belts hand over both ways. */
    @GameTest(template = "empty_long", timeoutTicks = 300)
    public static void fastBeltsMoveTwoBlocksPerSecondAndMix(GameTestHelper h) {
        clear(h);
        // row 1: 3 fast belts -> pan
        BlockPos[] fast = {new BlockPos(2, 1, 1), new BlockPos(3, 1, 1), new BlockPos(4, 1, 1)};
        for (BlockPos b : fast) place(h, b, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        BlockPos pan1 = new BlockPos(5, 1, 1);
        h.setBlock(pan1, ModBlocks.PAN.get());
        // row 3: normal -> fast -> normal -> pan
        BlockPos n1 = new BlockPos(2, 1, 3), f1 = new BlockPos(3, 1, 3), n2 = new BlockPos(4, 1, 3), pan3 = new BlockPos(5, 1, 3);
        place(h, n1, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        place(h, f1, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        place(h, n2, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        h.setBlock(pan3, ModBlocks.PAN.get());
        ClumpBeltBlockEntity first = (ClumpBeltBlockEntity) h.getBlockEntity(fast[0]);
        check(h, ClumpBeltBlock.speed(h.getBlockState(fast[0])) == 2 && ClumpBeltBlock.speed(h.getBlockState(n1)) == 1, "speeds");
        long[] start = new long[1];
        h.startSequence()
                .thenExecute(() -> {
                    first.insertByPlayer(1);
                    ((ClumpBeltBlockEntity) h.getBlockEntity(n1)).insertByPlayer(1);
                    start[0] = h.getTick();
                })
                .thenExecuteAfter(5, () -> {
                    int pos = first.positions()[0];
                    check(h, pos >= 8 && pos <= 10, "2 steps per tick: ~10 after 5 ticks, at " + pos);
                })
                .thenWaitUntil(() -> check(h, ((ClumpBeltBlockEntity) h.getBlockEntity(fast[2])).drops().size() == 1, "reaches the pan"))
                .thenExecute(() -> {
                    long t = h.getTick() - start[0];
                    check(h, t >= 28 && t <= 33, "3 fast belts take ~30 ticks (normal: 60), took " + t);
                })
                .thenWaitUntil(() -> check(h, PanBlock.fill(h.getBlockState(pan3)) == 1, "mixed line delivers too"))
                .thenExecute(() -> {
                    long t = h.getTick() - start[0];
                    // ~20 (normal) + ~10 (fast) + ~20 (normal) + 10 drop
                    check(h, t >= 55 && t <= 66, "normal + fast + normal + drop ~60 ticks, took " + t);
                    check(h, itemEntities(h, ModItems.DIRT_CLUMP.get()) == 0, "no items");
                })
                .thenSucceed();
    }

    /** End to end: a Large Pan cast in the forge from a Pan + 1200 g; the Goldchro Brush from a Brush + 16 g. */
    @GameTest(template = "empty_long", timeoutTicks = 400)
    public static void castLargePanAndGoldchroBrush(GameTestHelper h) {
        craftingForge(h, true);
        ServerPlayer p = player(h);
        cf(h).select(HoleyDigsite.id("large_pan"));
        top(h, p, folk(1250), false);
        top(h, p, new ItemStack(ModItems.PAN.get()), false);
        handle(h, p, ItemStack.EMPTY, false);
        h.startSequence()
                .thenExecuteAfter(CraftingForgeBlockEntity.CAST_TICKS + 3, () -> {
                    top(h, p, ItemStack.EMPTY, false);
                    check(h, count(p, ModItems.LARGE_PAN.get()) == 1 && ingotWeights(p, ModItems.FOLKIRON_INGOT.get()).equals(List.of(50)),
                            "Large Pan + 50 g");
                    cf(h).select(HoleyDigsite.id("goldchro_brush"));
                    top(h, p, gold(16), false);
                    top(h, p, new ItemStack(ModItems.BRUSH.get()), false);
                    handle(h, p, ItemStack.EMPTY, false);
                })
                .thenExecuteAfter(CraftingForgeBlockEntity.CAST_TICKS + 3, () -> {
                    top(h, p, ItemStack.EMPTY, false);
                    check(h, count(p, ModItems.GOLDCHRO_BRUSH.get()) == 1 && count(p, ModItems.GOLDCHRO_INGOT.get()) == 0, "Goldchro Brush, no leftovers");
                })
                .thenSucceed();
    }
}
