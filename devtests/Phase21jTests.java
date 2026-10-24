package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.belt.FilterBeltBlock;
import com.holeydigsite.belt.GemCutterBlock;
import com.holeydigsite.belt.SplitterBeltBlock;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.forge.CraftingForgeBlock;
import com.holeydigsite.forge.CraftingForgeBlockEntity;
import com.holeydigsite.forge.ForgeRecipe;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.forge.Metals;
import com.holeydigsite.forge.RecipeWheelNetwork;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.smith.AnvilRecipes;
import com.holeydigsite.smith.SmithingAnvilBlock;
import com.holeydigsite.smith.SmithingAnvilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Phase 21j (sandbox only):
 * <ul>
 *   <li>Recipe wheel C2S packet switches the recipe on Crafting Forge and Smithing Anvil, and refuses invalid/forged ids</li>
 *   <li>Recipe categories group every Forge and Anvil recipe exactly once (defaulting to "other")</li>
 *   <li>Filter Belt and Belt Splitter match the speed of the belt feeding their back (1 for normal, 2 for fast, 1 for machines/air)</li>
 *   <li>Gem Cutter's cutting time (40 ticks) stays unchanged when fed by a fast belt</li>
 *   <li>Alternative recipes for duplicated results carry clear distinguishing labels</li>
 * </ul>
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21jTests {

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) {
            h.fail(msg);
        }
    }

    private static void clear(GameTestHelper h, int sizeX) {
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = 0; z < 5; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static void placeBelt(GameTestHelper h, BlockPos rel, BlockState state) {
        BlockPos abs = h.absolutePos(rel);
        h.getLevel().setBlock(abs, Block.updateFromNeighbourShapes(state, h.getLevel(), abs), Block.UPDATE_ALL);
        TestWater.underBelt(h, rel);
    }

    private static ClumpBeltBlockEntity beltBe(GameTestHelper h, BlockPos rel) {
        return (ClumpBeltBlockEntity) h.getBlockEntity(rel);
    }

    /**
     * 1. Selecting a recipe via the wheel packet switches the recipe on both Crafting Forge and Smithing Anvil;
     *    an invalid or forged recipe id (or out-of-range / busy forge) is refused.
     */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void wheelPacketSwitchesRecipeAndRefusesInvalid(GameTestHelper h) {
        clear(h, 8);
        BlockPos forgeRel = new BlockPos(2, 1, 2);
        BlockPos anvilRel = new BlockPos(4, 1, 2);
        h.setBlock(forgeRel, ModBlocks.CRAFTING_FORGE.get().defaultBlockState().setValue(CraftingForgeBlock.FACING, Direction.NORTH));
        h.setBlock(anvilRel, ModBlocks.SMITHING_ANVIL.get().defaultBlockState().setValue(SmithingAnvilBlock.FACING, Direction.NORTH));

        BlockPos forgeAbs = h.absolutePos(forgeRel);
        BlockPos anvilAbs = h.absolutePos(anvilRel);
        CraftingForgeBlockEntity forge = (CraftingForgeBlockEntity) h.getBlockEntity(forgeRel);
        SmithingAnvilBlockEntity anvil = (SmithingAnvilBlockEntity) h.getBlockEntity(anvilRel);

        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setPos(forgeAbs.getX() + 0.5, forgeAbs.getY() + 1.0, forgeAbs.getZ() + 0.5);

        // Valid selection on Crafting Forge
        ResourceLocation targetForgeRecipe = HoleyDigsite.id("large_pan_silvane");
        boolean okForge = RecipeWheelNetwork.handleSelect(player, new RecipeWheelNetwork.SelectRecipePayload(forgeAbs, targetForgeRecipe));
        check(h, okForge, "valid forge recipe packet accepted");
        check(h, forge.recipe() != null && targetForgeRecipe.equals(forge.recipe().id()),
                "forge switched to large_pan_silvane, got " + (forge.recipe() == null ? "null" : forge.recipe().id()));

        // Valid selection on Smithing Anvil (also resets hammer hits)
        ItemStack ingot = Metals.ingot(HoleyDigsite.id("folkiron"), 50);
        anvil.lay(ingot);
        SmithingAnvilBlock.hammer(h.getLevel(), anvilAbs, player);
        check(h, anvil.hits() == 1, "anvil has 1 hit before recipe switch");

        ResourceLocation targetAnvilRecipe = HoleyDigsite.id("sword");
        boolean okAnvil = RecipeWheelNetwork.handleSelect(player, new RecipeWheelNetwork.SelectRecipePayload(anvilAbs, targetAnvilRecipe));
        check(h, okAnvil, "valid anvil recipe packet accepted");
        check(h, anvil.recipe() != null && targetAnvilRecipe.equals(anvil.recipe().id()),
                "anvil switched to sword, got " + (anvil.recipe() == null ? "null" : anvil.recipe().id()));
        check(h, anvil.hits() == 0, "anvil hits reset on wheel recipe switch");

        // Forged / nonexistent recipe id -> refused on both
        ResourceLocation bogusId = HoleyDigsite.id("forged_nonexistent_recipe");
        check(h, !RecipeWheelNetwork.handleSelect(player, new RecipeWheelNetwork.SelectRecipePayload(forgeAbs, bogusId)),
                "unknown recipe refused on forge");
        check(h, targetForgeRecipe.equals(forge.recipe().id()), "forge recipe unchanged after unknown id");

        check(h, !RecipeWheelNetwork.handleSelect(player, new RecipeWheelNetwork.SelectRecipePayload(anvilAbs, bogusId)),
                "unknown recipe refused on anvil");
        check(h, targetAnvilRecipe.equals(anvil.recipe().id()), "anvil recipe unchanged after unknown id");

        // Cross-workstation recipe ids -> anvil recipe on forge and forge recipe on anvil must be refused
        check(h, !RecipeWheelNetwork.handleSelect(player, new RecipeWheelNetwork.SelectRecipePayload(forgeAbs, targetAnvilRecipe)),
                "anvil recipe id refused on crafting forge");
        check(h, !RecipeWheelNetwork.handleSelect(player, new RecipeWheelNetwork.SelectRecipePayload(anvilAbs, targetForgeRecipe)),
                "forge recipe id refused on smithing anvil");

        // Busy forge (casting) -> refuses recipe switch
        forge.select(HoleyDigsite.id("goldchro_brush"));
        h.setBlock(forgeRel, h.getBlockState(forgeRel).setValue(CraftingForgeBlock.LIT, true));
        forge.load(Metals.ingot(HoleyDigsite.id("goldchro"), 50), 1, false);
        forge.load(new ItemStack(ModItems.BRUSH.get()), 1, false);
        check(h, CraftingForgeBlock.tryCast(h.getLevel(), forgeAbs, player), "forge started casting");
        check(h, !RecipeWheelNetwork.handleSelect(player, new RecipeWheelNetwork.SelectRecipePayload(forgeAbs, targetForgeRecipe)),
                "casting forge refuses recipe switch");
        check(h, HoleyDigsite.id("goldchro_brush").equals(forge.recipe().id()), "casting forge kept its recipe");

        // Out-of-range player -> refused
        player.setPos(anvilAbs.getX() + 50.0, anvilAbs.getY(), anvilAbs.getZ());
        check(h, !RecipeWheelNetwork.handleSelect(player, new RecipeWheelNetwork.SelectRecipePayload(anvilAbs, HoleyDigsite.id("ring"))),
                "out-of-range player refused");
        check(h, targetAnvilRecipe.equals(anvil.recipe().id()), "anvil recipe unchanged after out-of-range packet");

        h.succeed();
    }

    /**
     * 2. Categories group every recipe (no recipe is missing or duplicated; recipes without a category fall into "other").
     */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void categoriesGroupEveryRecipeExactlyOnce(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();

        // Crafting Forge recipes
        List<ForgeRecipes.Entry> allForge = ForgeRecipes.all(reg);
        Map<String, List<ForgeRecipes.Entry>> forgeByCat = ForgeRecipes.byCategory(reg);
        check(h, !allForge.isEmpty(), "forge recipes loaded");
        Set<ResourceLocation> seenForge = new HashSet<>();
        int countForge = 0;
        for (Map.Entry<String, List<ForgeRecipes.Entry>> e : forgeByCat.entrySet()) {
            check(h, !e.getKey().isBlank(), "non-blank forge category key");
            check(h, !e.getValue().isEmpty(), "non-empty forge category " + e.getKey());
            for (ForgeRecipes.Entry entry : e.getValue()) {
                check(h, seenForge.add(entry.id()), "forge recipe appears only once: " + entry.id());
                check(h, e.getKey().equals(entry.recipe().category()), "category matches recipe.category()");
                countForge++;
            }
        }
        check(h, countForge == allForge.size(), "every forge recipe grouped: " + countForge + " vs " + allForge.size());
        for (String expected : List.of("drills", "sluices", "casting", "tools", "belts")) {
            check(h, forgeByCat.containsKey(expected), "forge has category '" + expected + "'");
        }

        // Smithing Anvil recipes
        List<AnvilRecipes.Entry> allAnvil = AnvilRecipes.all(reg);
        Map<String, List<AnvilRecipes.Entry>> anvilByCat = AnvilRecipes.byCategory(reg);
        check(h, !allAnvil.isEmpty(), "anvil recipes loaded");
        Set<ResourceLocation> seenAnvil = new HashSet<>();
        int countAnvil = 0;
        for (Map.Entry<String, List<AnvilRecipes.Entry>> e : anvilByCat.entrySet()) {
            check(h, !e.getKey().isBlank(), "non-blank anvil category key");
            check(h, !e.getValue().isEmpty(), "non-empty anvil category " + e.getKey());
            for (AnvilRecipes.Entry entry : e.getValue()) {
                check(h, seenAnvil.add(entry.id()), "anvil recipe appears only once: " + entry.id());
                check(h, e.getKey().equals(entry.recipe().category()), "category matches anvil recipe.category()");
                countAnvil++;
            }
        }
        check(h, countAnvil == allAnvil.size(), "every anvil recipe grouped: " + countAnvil + " vs " + allAnvil.size());
        for (String expected : List.of("jewellery", "weapons", "tools")) {
            check(h, anvilByCat.containsKey(expected), "anvil has category '" + expected + "'");
        }

        // Default category fallback to "other" when blank/omitted
        ForgeRecipe fallbackForge = new ForgeRecipe(
                99, "", Optional.empty(), Map.of(HoleyDigsite.id("folkiron"), 10),
                List.of(), new ForgeRecipe.Result(ModItems.PAN.get(), 1));
        check(h, "other".equals(fallbackForge.category()), "blank forge category falls back to 'other'");

        AnvilRecipe fallbackAnvil = new AnvilRecipe(
                99, "", Optional.empty(), 1, AnvilRecipe.GemRule.NONE, 4,
                ModItems.SMITH_RING.get(), 0.25, Optional.empty(), 10, Optional.empty(), List.of(), Optional.empty());
        check(h, "other".equals(fallbackAnvil.category()), "blank anvil category falls back to 'other'");

        h.succeed();
    }

    /**
     * 3. Filter Belt and Belt Splitter run at speed 1 behind a normal belt and at speed 2 behind a Fast Belt;
     *    removing or replacing the fast belt with a machine drops them back to speed 1; inclines keep their own tier speed.
     */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void filterAndSplitterMatchInputBeltSpeed(GameTestHelper h) {
        clear(h, 10);
        BlockPos feedPos = new BlockPos(1, 1, 2);
        BlockPos filterPos = new BlockPos(2, 1, 2);
        BlockPos splitterPos = new BlockPos(3, 1, 2);

        // 1. Place Filter Belt and Splitter with nothing behind them -> speed 1
        placeBelt(h, filterPos, ModBlocks.FILTER_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        placeBelt(h, splitterPos, ModBlocks.BELT_SPLITTER.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        check(h, ClumpBeltBlock.speed(h.getBlockState(filterPos)) == 1, "filter speed is 1 with nothing behind");
        check(h, ClumpBeltBlock.speed(h.getBlockState(splitterPos)) == 1, "splitter speed is 1 behind normal-speed filter");

        // 2. Place a normal Conveyor Belt feeding the Filter Belt -> still speed 1
        placeBelt(h, feedPos, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        check(h, ClumpBeltBlock.speed(h.getBlockState(filterPos)) == 1, "filter speed is 1 behind normal belt");
        check(h, ClumpBeltBlock.speed(h.getBlockState(splitterPos)) == 1, "splitter speed is 1 behind normal belt chain");

        // 3. Replace feeder with Fast Conveyor Belt -> Filter and chained Splitter both become speed 2!
        placeBelt(h, feedPos, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        check(h, ClumpBeltBlock.speed(h.getBlockState(filterPos)) == 2,
                "filter speed is 2 behind fast belt, got " + ClumpBeltBlock.speed(h.getBlockState(filterPos)));
        check(h, ClumpBeltBlock.speed(h.getBlockState(splitterPos)) == 2,
                "chained splitter speed is 2 behind fast filter, got " + ClumpBeltBlock.speed(h.getBlockState(splitterPos)));

        // Verify a fast-fed Filter Belt actually moves an item at 2 steps/tick (reaches LAST = 20 in 10 ticks)
        beltBe(h, filterPos).accept(Direction.WEST, 1, new ItemStack(ModItems.DIRT_CLUMP.get()));
        check(h, beltBe(h, filterPos).positions()[0] == 0, "clump starts at step 0 on filter");

        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    // After 5 ticks at speed 2, item should be at step 10 (whereas speed 1 would only be at step 5)
                    int pos5 = beltBe(h, filterPos).positions()[0];
                    check(h, pos5 >= 10, "filter belt moved item at fast speed (step " + pos5 + " after 5 ticks)");
                })
                .thenExecute(() -> {
                    // 4. Replace fast belt with a Mining Drill (machine) -> falls back to normal speed (1)
                    h.setBlock(feedPos, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
                    check(h, ClumpBeltBlock.speed(h.getBlockState(filterPos)) == 1,
                            "filter speed falls back to 1 when fed by drill");
                    check(h, ClumpBeltBlock.speed(h.getBlockState(splitterPos)) == 1,
                            "splitter speed falls back to 1 when upstream filter drops to 1");

                    // 5. Put fast belt back -> 2 again, then break it to AIR -> 1 again
                    placeBelt(h, feedPos, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
                    check(h, ClumpBeltBlock.speed(h.getBlockState(filterPos)) == 2, "filter back to speed 2");
                    h.setBlock(feedPos, Blocks.AIR);
                    check(h, ClumpBeltBlock.speed(h.getBlockState(filterPos)) == 1, "filter drops to 1 when feeder broken");
                    check(h, ClumpBeltBlock.speed(h.getBlockState(splitterPos)) == 1, "splitter drops to 1 when feeder broken");

                    // 6. Inclines keep their own tier speed
                    BlockPos inc1 = new BlockPos(6, 1, 2);
                    BlockPos inc2 = new BlockPos(6, 1, 3);
                    placeBelt(h, new BlockPos(5, 1, 2), ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
                    placeBelt(h, inc1, ModBlocks.INCLINE_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
                    placeBelt(h, inc2, ModBlocks.FAST_INCLINE_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
                    check(h, ClumpBeltBlock.speed(h.getBlockState(inc1)) == 1, "normal incline keeps speed 1");
                    check(h, ClumpBeltBlock.speed(h.getBlockState(inc2)) == 2, "fast incline keeps speed 2");
                })
                .thenSucceed();
    }

    /**
     * 4. Gem Cutter's cutting time (CUT_TICKS = 40) is unchanged when fed by a Fast Belt, while item travel speed matches input.
     */
    @GameTest(template = "empty_long", timeoutTicks = 120)
    public static void gemCutterCutTimeUnchangedOnFastInput(GameTestHelper h) {
        clear(h, 10);
        // Line A (z = 1): Normal belt -> Gem Cutter
        BlockPos normalFeed = new BlockPos(1, 1, 1);
        BlockPos cutterNormal = new BlockPos(2, 1, 1);
        placeBelt(h, normalFeed, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        placeBelt(h, cutterNormal, ModBlocks.GEM_CUTTER.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));

        // Line B (z = 3): Fast belt -> Gem Cutter
        BlockPos fastFeed = new BlockPos(1, 1, 3);
        BlockPos cutterFast = new BlockPos(2, 1, 3);
        placeBelt(h, fastFeed, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        placeBelt(h, cutterFast, ModBlocks.GEM_CUTTER.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));

        check(h, ClumpBeltBlock.speed(h.getBlockState(cutterNormal)) == 1, "normal-fed gem cutter has speed 1");
        check(h, ClumpBeltBlock.speed(h.getBlockState(cutterFast)) == 2, "fast-fed gem cutter has speed 2");

        // Place an uncut ruby at step 0 on both Gem Cutters
        beltBe(h, cutterNormal).accept(Direction.WEST, 10, new ItemStack(ModItems.UNCUT_RUBY_SMALL.get()));
        beltBe(h, cutterFast).accept(Direction.WEST, 11, new ItemStack(ModItems.UNCUT_RUBY_SMALL.get()));

        h.startSequence()
                // After 5 ticks: fast cutter has already reached MID (step 10), normal cutter is at step 5
                .thenExecuteAfter(5, () -> {
                    check(h, beltBe(h, cutterFast).positions()[0] == ClumpBeltBlockEntity.MID,
                            "fast cutter reached MID in 5 ticks, got " + beltBe(h, cutterFast).positions()[0]);
                    check(h, beltBe(h, cutterNormal).positions()[0] == 5,
                            "normal cutter at step 5 after 5 ticks, got " + beltBe(h, cutterNormal).positions()[0]);
                })
                // After 35 more ticks (40 ticks total, so fast cutter has been at MID for 35 ticks < CUT_TICKS=40):
                // fast cutter must STILL be cutting (uncut ruby at MID), proving cut time wasn't halved!
                .thenExecuteAfter(35, () -> {
                    ClumpBeltBlockEntity fastBe = beltBe(h, cutterFast);
                    check(h, fastBe.positions()[0] == ClumpBeltBlockEntity.MID,
                            "fast cutter gem still at MID after 35 cut ticks");
                    check(h, fastBe.clumps().get(0).stack().is(ModItems.UNCUT_RUBY_SMALL.get()),
                            "fast cutter gem still uncut before 40 cut ticks");
                })
                // After 5 more ticks (fast cutter reaches 40 cut ticks at MID): gem on fast cutter is now cut!
                .thenExecuteAfter(5, () -> {
                    ClumpBeltBlockEntity fastBe = beltBe(h, cutterFast);
                    check(h, fastBe.clumps().get(0).stack().is(ModItems.RUBY_SMALL.get()),
                            "fast cutter finished cutting after full 40 cut ticks");
                    // Normal cutter has been at MID for 35 ticks (since it took 10 ticks to reach MID), so still uncut!
                    ClumpBeltBlockEntity normBe = beltBe(h, cutterNormal);
                    check(h, normBe.clumps().get(0).stack().is(ModItems.UNCUT_RUBY_SMALL.get()),
                            "normal cutter gem still uncut at 35 cut ticks");
                })
                // 5 ticks later: normal cutter also finishes its 40 cut ticks, and fast cutter has moved the cut gem to exit (step 20)!
                .thenExecuteAfter(5, () -> {
                    ClumpBeltBlockEntity normBe = beltBe(h, cutterNormal);
                    check(h, normBe.clumps().get(0).stack().is(ModItems.RUBY_SMALL.get()),
                            "normal cutter finished cutting after its 40 cut ticks");
                    ClumpBeltBlockEntity fastBe = beltBe(h, cutterFast);
                    check(h, fastBe.positions()[0] >= ClumpBeltBlockEntity.LAST - 1,
                            "fast cutter moved cut gem to exit in 5 ticks, got " + fastBe.positions()[0]);
                })
                .thenSucceed();
    }

    /**
     * 5. Duplicated-result recipes have distinct alt-recipe labels (both explicit and auto-derived from metal).
     */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void altRecipeLabelsForDuplicatedResults(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();

        // Fast Conveyor Belt: Folkiron vs Orchremia
        ForgeRecipes.Entry beltFolk = ForgeRecipes.find(reg, HoleyDigsite.id("fast_clump_belt"));
        ForgeRecipes.Entry beltOrch = ForgeRecipes.find(reg, HoleyDigsite.id("fast_clump_belt_orchremia"));
        check(h, beltFolk != null && beltOrch != null, "both fast_clump_belt recipes exist");
        check(h, ForgeRecipes.isDuplicated(reg, ModItems.FAST_CLUMP_BELT.get()), "fast_clump_belt is recognized as duplicated");

        String beltFolkName = beltFolk.recipe().name(reg).getString();
        String beltOrchName = beltOrch.recipe().name(reg).getString();
        check(h, !beltFolkName.equals(beltOrchName), "fast belt recipe names are distinct: " + beltFolkName + " vs " + beltOrchName);
        check(h, beltFolkName.contains("Folkiron"), "fast_clump_belt name mentions Folkiron: " + beltFolkName);
        check(h, beltOrchName.contains("Orchremia"), "fast_clump_belt_orchremia name mentions Orchremia: " + beltOrchName);

        // Large Pan: Folkiron vs Silvane
        ForgeRecipes.Entry panFolk = ForgeRecipes.find(reg, HoleyDigsite.id("large_pan"));
        ForgeRecipes.Entry panSilv = ForgeRecipes.find(reg, HoleyDigsite.id("large_pan_silvane"));
        check(h, panFolk != null && panSilv != null, "both large_pan recipes exist");
        check(h, ForgeRecipes.isDuplicated(reg, ModItems.LARGE_PAN.get()), "large_pan is recognized as duplicated");

        String panFolkName = panFolk.recipe().name(reg).getString();
        String panSilvName = panSilv.recipe().name(reg).getString();
        check(h, !panFolkName.equals(panSilvName), "large pan recipe names are distinct: " + panFolkName + " vs " + panSilvName);
        check(h, panFolkName.contains("Folkiron"), "large_pan name mentions Folkiron: " + panFolkName);
        check(h, panSilvName.contains("Silvane"), "large_pan_silvane name mentions Silvane: " + panSilvName);

        // Non-duplicated recipe has no variant suffix
        ForgeRecipes.Entry drillT2 = ForgeRecipes.find(reg, HoleyDigsite.id("mining_drill_t2"));
        check(h, drillT2 != null, "mining_drill_t2 exists");
        check(h, !ForgeRecipes.isDuplicated(reg, ModItems.MINING_DRILL_T2.get()), "mining_drill_t2 is not duplicated");
        check(h, drillT2.recipe().variantLabel(reg) == null, "mining_drill_t2 has null variantLabel");

        // Auto-derivation from metal when variant field is omitted on a duplicated result item
        ForgeRecipe autoDerived = new ForgeRecipe(
                99, "belts", Optional.empty(), Map.of(HoleyDigsite.id("silvane"), 25),
                List.of(), new ForgeRecipe.Result(ModItems.FAST_CLUMP_BELT.get(), 4));
        check(h, autoDerived.variantLabel(reg) != null && autoDerived.variantLabel(reg).getString().contains("Silvane"),
                "auto-derived variant label from metal when result is duplicated");

        h.succeed();
    }
}
