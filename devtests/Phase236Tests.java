package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.forge.CraftingForgeBlock;
import com.holeydigsite.forge.CraftingForgeBlockEntity;
import com.holeydigsite.forge.ForgeRecipe;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.item.AssayersPickItem;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.item.PickHeadItem;
import com.holeydigsite.item.ProspectorPickItem;
import com.holeydigsite.power.LavaIntakeBlock;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.Sites;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 23.6 tests:
 * <ul>
 *   <li>Fitting and swapping pick heads (nothing lost; inventory full drops at feet)</li>
 *   <li>Each head finds only its rock (Sand Head -> Sandrock, Gravel Head -> Gravelrock)</li>
 *   <li>No head behaves as before (finds Deep/Abyssal, ignores Sandrock/Gravelrock)</li>
 *   <li>Assayer's Pick finds all four rock types</li>
 *   <li>Assayer's Pick counts rock types accurately in structure (within 55 characters)</li>
 *   <li>Range limits (Prospector 12 vs 13, Assayer 20 vs 21)</li>
 *   <li>Crafting Forge recipe for Assayer's Pick</li>
 *   <li>Kiosk displays show heads (including old-world auto-placement via ensureDisplays)</li>
 * </ul>
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase236Tests {
    private static final ResourceLocation GRAVEL_FLATS_SHOP = HoleyDigsite.id("gravel_flats_shop");
    private static final ResourceLocation SAND_DUNES_SHOP = HoleyDigsite.id("sand_dunes_shop");
    private static final ResourceLocation R_ASSAYER = HoleyDigsite.id("assayers_pick");

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 26; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 0; z < 5; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
            for (int z = 0; z < 5; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    private static BlockPos far(int n) {
        return new BlockPos(25000 + n * 64, 100, 25000);
    }

    private static void prepare(ServerLevel l, BlockPos a) {
        for (int x = -10; x <= 20; x++) {
            for (int y = -2; y <= 15; y++) {
                for (int z = -10; z <= 20; z++) {
                    l.setBlock(a.offset(x, y, z), y < 0 ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void cleanup(ServerLevel l, List<BlockPos> anchors) {
        SiteData data = SiteData.get(l);
        for (BlockPos a : anchors) {
            data.remove(SiteData.keyOf(GRAVEL_FLATS_SHOP, a));
            data.remove(SiteData.keyOf(SAND_DUNES_SHOP, a));
        }
        SiteDefs.clearOverrides();
    }

    private static <T extends BlockEntity> List<T> bes(ServerLevel l, BoundingBox zone, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ())) {
            BlockEntity be = l.getBlockEntity(p);
            if (type.isInstance(be)) out.add(type.cast(be));
        }
        return out;
    }

    private static Map<Integer, ShopDisplayBlockEntity> displayEntities(ServerLevel l, BoundingBox zone) {
        Map<Integer, ShopDisplayBlockEntity> m = new HashMap<>();
        for (ShopDisplayBlockEntity d : bes(l, zone, ShopDisplayBlockEntity.class)) {
            if (d.slot() > 0) m.put(d.slot(), d);
        }
        return m;
    }

    private static Map<Integer, Item> displays(ServerLevel l, BoundingBox zone) {
        Map<Integer, Item> m = new HashMap<>();
        for (ShopDisplayBlockEntity d : bes(l, zone, ShopDisplayBlockEntity.class)) {
            if (d.slot() > 0) m.put(d.slot(), d.item());
        }
        return m;
    }

    // ------------------------------------------------------------------------------------------------
    // 1. Fitting, swapping, detaching heads
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void fittingAndSwappingHeads(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack pick = new ItemStack(ModItems.PROSPECTORS_PICK.get());
        ItemStack sandHead = new ItemStack(ModItems.SAND_HEAD.get(), 2);

        // Fit Sand Head
        check(h, ProspectorPickItem.getHead(pick) == null, "pick starts with no head");
        boolean fitted = ProspectorPickItem.fitHead(p, pick, sandHead);
        check(h, fitted, "fitting sand head succeeded");
        check(h, ProspectorPickItem.getHead(pick) == ModItems.SAND_HEAD.get(), "pick now has Sand Head");
        check(h, sandHead.getCount() == 1, "consumed exactly 1 head: " + sandHead.getCount());
        check(h, p.getInventory().countItem(ModItems.SAND_HEAD.get()) == 0, "no head returned on first fit");

        // Swap to Gravel Head: Sand Head must be returned to inventory (nothing lost)
        ItemStack gravelHead = new ItemStack(ModItems.GRAVEL_HEAD.get(), 1);
        boolean swapped = ProspectorPickItem.fitHead(p, pick, gravelHead);
        check(h, swapped, "swapping to gravel head succeeded");
        check(h, ProspectorPickItem.getHead(pick) == ModItems.GRAVEL_HEAD.get(), "pick now has Gravel Head");
        check(h, gravelHead.isEmpty(), "consumed gravel head");
        check(h, p.getInventory().countItem(ModItems.SAND_HEAD.get()) == 1, "old Sand Head returned to inventory");

        // Sneak + use detaches the Gravel Head back to inventory
        boolean detached = ProspectorPickItem.detachHead(p, pick);
        check(h, detached, "detaching succeeded");
        check(h, ProspectorPickItem.getHead(pick) == null, "pick has no head after detach");
        check(h, p.getInventory().countItem(ModItems.GRAVEL_HEAD.get()) == 1, "Gravel Head returned to inventory");

        // Fit Gravel Head again, fill inventory, detach -> must drop at feet (nothing lost)
        ItemStack gravelHead2 = new ItemStack(ModItems.GRAVEL_HEAD.get(), 1);
        ProspectorPickItem.fitHead(p, pick, gravelHead2);
        check(h, ProspectorPickItem.getHead(pick) == ModItems.GRAVEL_HEAD.get(), "refitted gravel head");

        // Fill all inventory slots
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        BlockPos playerPos = p.blockPosition();
        ProspectorPickItem.detachHead(p, pick);
        check(h, ProspectorPickItem.getHead(pick) == null, "pick has no head after full-inventory detach");

        // Verify dropped item entity exists
        AABB dropBox = new AABB(playerPos).inflate(3.0);
        List<ItemEntity> dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, dropBox,
                e -> e.getItem().is(ModItems.GRAVEL_HEAD.get()));
        check(h, !dropped.isEmpty(), "Gravel Head dropped at feet when inventory was full");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 2. Each head finds only its rock
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void eachHeadFindsOnlyItsRock(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(12, 1, 2));

        // Place rocks in the test area
        BlockPos sandPos = h.absolutePos(new BlockPos(15, 1, 2));   // 3 east
        BlockPos gravelPos = h.absolutePos(new BlockPos(12, 1, 4)); // 2 south
        BlockPos deepPos = h.absolutePos(new BlockPos(12, 0, 2));   // 1 down
        BlockPos abyssalPos = h.absolutePos(new BlockPos(9, 1, 2));  // 3 west

        level.setBlock(sandPos, ModBlocks.SANDROCK.get().defaultBlockState(), 3);
        level.setBlock(gravelPos, ModBlocks.GRAVELROCK.get().defaultBlockState(), 3);
        level.setBlock(deepPos, ModBlocks.DEEP_DIRTROCK.get().defaultBlockState(), 3);
        level.setBlock(abyssalPos, ModBlocks.ABYSSAL_DIRTROCK.get().defaultBlockState(), 3);

        // Pick with Sand Head finds ONLY Sandrock
        ProspectorPickItem.Find fSand = ProspectorPickItem.scan(level, origin, ModItems.SAND_HEAD.get(), ProspectorPickItem.RANGE);
        check(h, fSand != null, "Sand Head found a rock");
        check(h, fSand.kind() == DirtTier.SAND, "Sand Head rock kind is Sandrock: " + fSand.kind());
        check(h, fSand.pos().equals(sandPos), "Sand Head found the sandrock pos");

        // Remove sandrock: Sand Head must find nothing (ignores gravel, deep, abyssal)
        level.setBlock(sandPos, Blocks.STONE.defaultBlockState(), 3);
        fSand = ProspectorPickItem.scan(level, origin, ModItems.SAND_HEAD.get(), ProspectorPickItem.RANGE);
        check(h, fSand == null, "Sand Head finds nothing when sandrock is absent");

        // Pick with Gravel Head finds ONLY Gravelrock
        ProspectorPickItem.Find fGravel = ProspectorPickItem.scan(level, origin, ModItems.GRAVEL_HEAD.get(), ProspectorPickItem.RANGE);
        check(h, fGravel != null, "Gravel Head found a rock");
        check(h, fGravel.kind() == DirtTier.GRAVEL, "Gravel Head rock kind is Gravelrock: " + fGravel.kind());
        check(h, fGravel.pos().equals(gravelPos), "Gravel Head found the gravelrock pos");

        // Remove gravelrock: Gravel Head must find nothing (ignores deep, abyssal)
        level.setBlock(gravelPos, Blocks.STONE.defaultBlockState(), 3);
        fGravel = ProspectorPickItem.scan(level, origin, ModItems.GRAVEL_HEAD.get(), ProspectorPickItem.RANGE);
        check(h, fGravel == null, "Gravel Head finds nothing when gravelrock is absent");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 3. No head behaves as before
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void noHeadBehavesAsBefore(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(12, 1, 2));

        // Place sandrock and gravelrock
        BlockPos sandPos = h.absolutePos(new BlockPos(14, 1, 2));
        BlockPos gravelPos = h.absolutePos(new BlockPos(12, 1, 3));
        level.setBlock(sandPos, ModBlocks.SANDROCK.get().defaultBlockState(), 3);
        level.setBlock(gravelPos, ModBlocks.GRAVELROCK.get().defaultBlockState(), 3);

        // Pick with no head ignores sandrock and gravelrock
        ProspectorPickItem.Find fNone = ProspectorPickItem.scan(level, origin, null, ProspectorPickItem.RANGE);
        check(h, fNone == null, "no head ignores sandrock and gravelrock");

        // Place deep dirtrock 2 blocks away (west)
        BlockPos deepPos = h.absolutePos(new BlockPos(10, 1, 2));
        level.setBlock(deepPos, ModBlocks.DEEP_DIRTROCK.get().defaultBlockState(), 3);
        fNone = ProspectorPickItem.scan(level, origin, null, ProspectorPickItem.RANGE);
        check(h, fNone != null && fNone.kind() == 2 && fNone.pos().equals(deepPos), "no head finds deep rock: " + fNone);

        // Place abyssal rock closer (1 block away, north)
        BlockPos abyssalPos = h.absolutePos(new BlockPos(12, 1, 1)); // 1 north
        level.setBlock(abyssalPos, ModBlocks.ABYSSAL_DIRTROCK.get().defaultBlockState(), 3);
        fNone = ProspectorPickItem.scan(level, origin, null, ProspectorPickItem.RANGE);
        check(h, fNone != null && fNone.kind() == 3 && fNone.pos().equals(abyssalPos), "no head finds nearest abyssal rock: " + fNone);

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 4. Assayer's Pick finds all four rock types
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void assayersPickFindsAllFour(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(12, 1, 2));

        BlockPos target = h.absolutePos(new BlockPos(15, 1, 2));

        // 1. Deep
        level.setBlock(target, ModBlocks.DEEP_DIRTROCK.get().defaultBlockState(), 3);
        ProspectorPickItem.Find f = AssayersPickItem.scanAll(level, origin, AssayersPickItem.RANGE);
        check(h, f != null && f.kind() == 2, "Assayer's Pick found Deep rock");

        // 2. Abyssal
        level.setBlock(target, ModBlocks.ABYSSAL_DIRTROCK.get().defaultBlockState(), 3);
        f = AssayersPickItem.scanAll(level, origin, AssayersPickItem.RANGE);
        check(h, f != null && f.kind() == 3, "Assayer's Pick found Abyssal rock");

        // 3. Gravelrock
        level.setBlock(target, ModBlocks.GRAVELROCK.get().defaultBlockState(), 3);
        f = AssayersPickItem.scanAll(level, origin, AssayersPickItem.RANGE);
        check(h, f != null && f.kind() == DirtTier.GRAVEL, "Assayer's Pick found Gravelrock");

        // 4. Sandrock
        level.setBlock(target, ModBlocks.SANDROCK.get().defaultBlockState(), 3);
        f = AssayersPickItem.scanAll(level, origin, AssayersPickItem.RANGE);
        check(h, f != null && f.kind() == DirtTier.SAND, "Assayer's Pick found Sandrock");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 5. Assayer's Pick counts right in structure
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void assayersPickCountIsRightInStructure(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(12, 1, 2));

        // Place known counts: 3 Abyssal, 12 Deep, 5 Sand, 7 Gravel
        // Abyssal x3
        for (int i = 0; i < 3; i++) {
            level.setBlock(h.absolutePos(new BlockPos(1 + i, 1, 1)), ModBlocks.ABYSSAL_DIRTROCK.get().defaultBlockState(), 3);
        }
        // Deep x12
        for (int i = 0; i < 12; i++) {
            level.setBlock(h.absolutePos(new BlockPos(1 + i, 1, 2)), ModBlocks.DEEP_DIRTROCK.get().defaultBlockState(), 3);
        }
        // Sandrock x5
        for (int i = 0; i < 5; i++) {
            level.setBlock(h.absolutePos(new BlockPos(1 + i, 1, 3)), ModBlocks.SANDROCK.get().defaultBlockState(), 3);
        }
        // Gravelrock x7
        for (int i = 0; i < 7; i++) {
            level.setBlock(h.absolutePos(new BlockPos(1 + i, 1, 4)), ModBlocks.GRAVELROCK.get().defaultBlockState(), 3);
        }

        AssayersPickItem.RockCounts counts = AssayersPickItem.countScan(level, origin, AssayersPickItem.RANGE);
        check(h, counts.abyssal() == 3, "abyssal count expected 3, got: " + counts.abyssal());
        check(h, counts.deep() == 12, "deep count expected 12, got: " + counts.deep());
        check(h, counts.sand() == 5, "sand count expected 5, got: " + counts.sand());
        check(h, counts.gravel() == 7, "gravel count expected 7, got: " + counts.gravel());

        Component msg = AssayersPickItem.countsMessage(counts);
        String text = msg.getString();
        check(h, text.equals("3 Abyssal, 12 Deep, 5 Sand, 7 Gravel"), "counts text matches: " + text);
        check(h, text.length() <= 55, "counts message fits within 55 characters: " + text.length());

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 6. Range limits (Prospector 12 vs 13, Assayer 20 vs 21)
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long")
    public static void rangeLimits(GameTestHelper h) {
        clear(h);
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(2, 1, 2));

        // Prospector's Pick: RANGE = 12
        BlockPos pos12 = h.absolutePos(new BlockPos(14, 1, 2)); // dx = 12
        BlockPos pos13 = h.absolutePos(new BlockPos(15, 1, 2)); // dx = 13

        level.setBlock(pos12, ModBlocks.DEEP_DIRTROCK.get().defaultBlockState(), 3);
        ProspectorPickItem.Find f12 = ProspectorPickItem.scan(level, origin, null, ProspectorPickItem.RANGE);
        check(h, f12 != null, "Prospector's pick finds rock at 12 blocks");

        level.setBlock(pos12, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos13, ModBlocks.DEEP_DIRTROCK.get().defaultBlockState(), 3);
        ProspectorPickItem.Find f13 = ProspectorPickItem.scan(level, origin, null, ProspectorPickItem.RANGE);
        check(h, f13 == null, "Prospector's pick does NOT find rock at 13 blocks");

        // Assayer's Pick: RANGE = 20
        BlockPos pos20 = h.absolutePos(new BlockPos(22, 1, 2)); // dx = 20
        BlockPos pos21 = h.absolutePos(new BlockPos(23, 1, 2)); // dx = 21

        level.setBlock(pos13, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos20, ModBlocks.SANDROCK.get().defaultBlockState(), 3);
        ProspectorPickItem.Find f20 = AssayersPickItem.scanAll(level, origin, AssayersPickItem.RANGE);
        check(h, f20 != null, "Assayer's pick finds rock at 20 blocks");

        level.setBlock(pos20, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos21, ModBlocks.SANDROCK.get().defaultBlockState(), 3);
        ProspectorPickItem.Find f21 = AssayersPickItem.scanAll(level, origin, AssayersPickItem.RANGE);
        check(h, f21 == null, "Assayer's pick does NOT find rock at 21 blocks");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------
    // 7. Crafting Forge recipe
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void forgeRecipe(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ForgeRecipes.Entry entry = ForgeRecipes.find(level.registryAccess(), R_ASSAYER);
        check(h, entry != null, "assayers_pick forge recipe registered");

        ForgeRecipe recipe = entry.recipe();
        check(h, recipe.result().item() == ModItems.ASSAYERS_PICK.get(), "recipe outputs assayers_pick");
        check(h, recipe.parts().stream().anyMatch(p -> p.item() == ModItems.PROSPECTORS_PICK.get() && p.count() == 1),
                "recipe requires 1 prospectors_pick");
        check(h, recipe.metals().get(HoleyDigsite.id("orchremia")) == 600, "recipe requires 600 g orchremia");
        check(h, recipe.metals().get(HoleyDigsite.id("silvane")) == 120, "recipe requires 120 g silvane");

        // Test casting in forge
        clear(h);
        BlockPos forgePos = new BlockPos(4, 1, 2);
        BlockPos intakePos = new BlockPos(5, 1, 2);
        h.setBlock(forgePos, ModBlocks.CRAFTING_FORGE.get().defaultBlockState().setValue(CraftingForgeBlock.FACING, Direction.WEST));
        BlockPos absIntake = h.absolutePos(intakePos);
        BlockState intakeState = ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(LavaIntakeBlock.LAVALOGGED, true);
        for (Direction dir : Direction.values()) {
            intakeState = intakeState.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), WaterNetworkBlock.connects(level, absIntake, intakeState, dir));
        }
        level.setBlock(absIntake, intakeState, Block.UPDATE_CLIENTS);
        WaterNetworks.recompute(level, Set.of(h.absolutePos(forgePos)));

        CraftingForgeBlockEntity forge = (CraftingForgeBlockEntity) h.getBlockEntity(forgePos);
        check(h, forge != null, "placed crafting forge");
        check(h, h.getBlockState(forgePos).getValue(CraftingForgeBlock.LIT), "crafting forge is lit by lava");

        forge.select(R_ASSAYER);
        forge.load(new ItemStack(ModItems.PROSPECTORS_PICK.get()), 1, false);
        forge.load(IngotItem.of(ModItems.ORCHREMIA_INGOT.get(), 600), 1, false);
        forge.load(IngotItem.of(ModItems.SILVANE_INGOT.get(), 120), 1, false);

        check(h, forge.missing(recipe) == null, "forge has all ingredients");
        ServerPlayer p = player(h);
        boolean castStarted = CraftingForgeBlock.tryCast(level, h.absolutePos(forgePos), p);
        check(h, castStarted && forge.isCasting(), "casting started");

        h.startSequence()
                .thenExecuteAfter(CraftingForgeBlockEntity.CAST_TICKS + 5, () -> {
                    check(h, forge.hasOutput(), "casting finished, has output");
                    List<ItemStack> output = forge.output();
                    check(h, output.stream().anyMatch(s -> s.is(ModItems.ASSAYERS_PICK.get())), "output contains Assayer's Pick: " + output);
                    h.succeed();
                });
    }

    // ------------------------------------------------------------------------------------------------
    // 8. Kiosk displays show heads (including old-world auto-placement)
    // ------------------------------------------------------------------------------------------------

    @GameTest(template = "empty", batch = "p236_kiosks", timeoutTicks = 100)
    public static void kioskDisplaysShowHeads(GameTestHelper h) {
        ServerLevel l = h.getLevel();

        // 1. Check layout mappings
        Map<Integer, ResourceLocation> gravelLayout = SiteDefs.layout(GRAVEL_FLATS_SHOP);
        check(h, HoleyDigsite.id("gravel_head").equals(gravelLayout.get(8)),
                "gravel flats layout slot 8 is gravel_head: " + gravelLayout.get(8));

        Map<Integer, ResourceLocation> sandLayout = SiteDefs.layout(SAND_DUNES_SHOP);
        check(h, HoleyDigsite.id("sand_head").equals(sandLayout.get(8)),
                "sand dunes layout slot 8 is sand_head: " + sandLayout.get(8));

        // 2. Simulate old-world kiosk (slot 8 missing in world, site version kept at 2)
        BlockPos a = far(50);
        prepare(l, a);
        try {
            SiteDef d = SiteDefs.get(GRAVEL_FLATS_SHOP);
            Sites.Result r = Sites.build(l, d, a, null, false);
            BoundingBox z = r.site().zone();

            Map<Integer, ShopDisplayBlockEntity> des = displayEntities(l, z);
            check(h, des.containsKey(8), "fresh kiosk has slot 8");
            BlockPos p8 = des.get(8).getBlockPos().immutable();

            // Simulate old world: remove slot 8 from world (set to AIR)
            l.setBlock(p8, Blocks.AIR.defaultBlockState(), 3);
            SiteData data = SiteData.get(l);
            SiteData.Site oldSite = data.get(d.id(), a);
            List<String> shownWithout8 = new ArrayList<>(oldSite.shown());
            shownWithout8.remove(BuiltInRegistries.ITEM.getKey(ModItems.GRAVEL_HEAD.get()).toString());
            data.put(new SiteData.Site(oldSite.id(), oldSite.anchor(), oldSite.zone(), oldSite.version(), shownWithout8, oldSite.parent()));

            check(h, l.getBlockState(p8).isAir(), "slot 8 simulated as air in old world");

            // Run checkAll (layout refresh calls ensureDisplays)
            Sites.checkAll(l);

            // Verify slot 8 was placed and populated with gravel_head
            Map<Integer, Item> shown = displays(l, z);
            check(h, shown.get(8) == ModItems.GRAVEL_HEAD.get(),
                    "slot 8 auto-placed and shows gravel_head: " + shown.get(8));

            h.succeed();
        } finally {
            cleanup(l, List.of(a));
        }
    }
}
