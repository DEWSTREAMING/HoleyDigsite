package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.shop.ShopItem;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.skin.SkinBenchBlock;
import com.holeydigsite.skin.SkinBenchBlockEntity;
import com.holeydigsite.skin.SkinBenchNetwork;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import com.holeydigsite.skin.SkinModelProperties;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 22 tests:
 * <ul>
 *   <li>Skin component survives place -> break -> pick up on all machines</li>
 *   <li>Placed skinned blocks provide skin in BE and model data (including drill upper half)</li>
 *   <li>Skin Bench interactions, placement, retrieval, and drops on break</li>
 *   <li>Server validates skin bench network payloads (kit carried, family, reach)</li>
 *   <li>Admin skin give command</li>
 *   <li>Old unlocks (kept only for the move to kits) survive player clone (death/respawn) and NBT serialization</li>
 *   <li>Ledger skin rewards at levels 13, 22, 33, 39, 47 give drill kits on claim</li>
 *   <li>Trading Post layout slot 36 sells Skin Bench at 2048 copper</li>
 *   <li>All skin texture files exist for all tiers</li>
 * </ul>
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase22Tests {

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

    @GameTest(template = "empty")
    public static void testSkinSurvivesPlaceAndBreakDrill(GameTestHelper h) {
        clear(h, 5);
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

        // Mining Drill with Obsidian skin
        ItemStack drillStack = new ItemStack(ModItems.MINING_DRILL.get());
        drillStack.set(ModDataComponents.SKIN.get(), "obsidian");

        // Place drill
        BlockState drillState = ModBlocks.MINING_DRILL.get().defaultBlockState()
                .setValue(MiningDrillBlock.FACING, Direction.NORTH)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.LOWER);
        h.setBlock(pos, drillState);
        h.setBlock(pos.above(), drillState.setValue(MiningDrillBlock.HALF, DoubleBlockHalf.UPPER));
        ModBlocks.MINING_DRILL.get().setPlacedBy(h.getLevel(), h.absolutePos(pos), drillState, player, drillStack);

        MiningDrillBlockEntity be = (MiningDrillBlockEntity) h.getBlockEntity(pos);
        check(h, be != null, "drill block entity must exist");
        check(h, "obsidian".equals(be.getSkin()), "placed drill BE must have obsidian skin, got " + be.getSkin());

        // Break drill using player game mode (survival break)
        player.gameMode.destroyBlock(h.absolutePos(pos));

        // Verify dropped item has skin
        List<ItemEntity> items = h.getEntities(net.minecraft.world.entity.EntityType.ITEM);
        boolean foundSkinnedDrop = false;
        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            if (stack.is(ModItems.MINING_DRILL.get())) {
                if ("obsidian".equals(stack.get(ModDataComponents.SKIN.get()))) {
                    foundSkinnedDrop = true;
                    break;
                }
            }
        }
        check(h, foundSkinnedDrop, "dropped drill must retain obsidian skin component");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void testSkinSurvivesPlaceAndBreakSluice(GameTestHelper h) {
        clear(h, 5);
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

        // Sluice Box T2 with Rustic Weathered skin
        ItemStack sluiceStack = new ItemStack(ModBlocks.SLUICE_BOX_T2.get().asItem());
        sluiceStack.set(ModDataComponents.SKIN.get(), "rustic_weathered");

        BlockState sluiceState = ModBlocks.SLUICE_BOX_T2.get().defaultBlockState()
                .setValue(SluiceBoxBlock.FACING, Direction.NORTH);
        h.setBlock(pos, sluiceState);
        ModBlocks.SLUICE_BOX_T2.get().setPlacedBy(h.getLevel(), h.absolutePos(pos), sluiceState, player, sluiceStack);

        SluiceBoxBlockEntity be = (SluiceBoxBlockEntity) h.getBlockEntity(pos);
        check(h, be != null, "sluice block entity must exist");
        check(h, "rustic_weathered".equals(be.getSkin()), "placed sluice BE must have rustic_weathered skin");

        // Break sluice using player game mode (survival break)
        player.gameMode.destroyBlock(h.absolutePos(pos));

        List<ItemEntity> items = h.getEntities(net.minecraft.world.entity.EntityType.ITEM);
        boolean foundSkinnedDrop = false;
        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            if (stack.is(ModBlocks.SLUICE_BOX_T2.get().asItem())) {
                if ("rustic_weathered".equals(stack.get(ModDataComponents.SKIN.get()))) {
                    foundSkinnedDrop = true;
                    break;
                }
            }
        }
        check(h, foundSkinnedDrop, "dropped sluice must retain rustic_weathered skin component");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void testPlacedBlockModelData(GameTestHelper h) {
        clear(h, 5);
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = h.makeMockServerPlayerInLevel();

        ItemStack drillStack = new ItemStack(ModItems.MINING_DRILL_T3.get());
        drillStack.set(ModDataComponents.SKIN.get(), "brass");

        BlockState lowerState = ModBlocks.MINING_DRILL_T3.get().defaultBlockState()
                .setValue(MiningDrillBlock.FACING, Direction.NORTH)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperState = lowerState.setValue(MiningDrillBlock.HALF, DoubleBlockHalf.UPPER);

        h.setBlock(pos, lowerState);
        h.setBlock(pos.above(), upperState);
        ModBlocks.MINING_DRILL_T3.get().setPlacedBy(h.getLevel(), h.absolutePos(pos), lowerState, player, drillStack);

        MiningDrillBlockEntity lowerBe = (MiningDrillBlockEntity) h.getBlockEntity(pos);
        check(h, lowerBe != null, "lower drill BE exists");
        ModelData lowerData = lowerBe.getModelData();
        check(h, "brass".equals(lowerData.get(SkinModelProperties.SKIN)), "lower BE ModelData has brass skin");

        // Upper half has no BE
        check(h, h.getLevel().getBlockEntity(h.absolutePos(pos.above())) == null, "upper half of drill must not have a block entity");

        // Verify upper half resolution logic
        check(h, !MiningDrillBlock.isLower(upperState), "upper state is not lower");
        BlockPos lowerPos = h.absolutePos(pos.above()).below();
        check(h, h.getLevel().getBlockEntity(lowerPos) instanceof MiningDrillBlockEntity d && "brass".equals(d.getSkin()),
                "resolving lower block entity from upper pos yields brass skin");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void testSkinBenchInteractions(GameTestHelper h) {
        clear(h, 5);
        BlockPos pos = new BlockPos(1, 1, 1);

        h.setBlock(pos, ModBlocks.SKIN_BENCH.get().defaultBlockState());
        SkinBenchBlockEntity bench = (SkinBenchBlockEntity) h.getBlockEntity(pos);
        check(h, bench != null, "bench block entity exists");
        check(h, !bench.hasItem(), "bench starts empty");

        // Insert drill
        ItemStack drill = new ItemStack(ModItems.MINING_DRILL.get());
        drill.set(ModDataComponents.SKIN.get(), "rustic");
        bench.setItem(drill);
        check(h, bench.hasItem(), "bench now has item");
        check(h, "rustic".equals(bench.getItem().get(ModDataComponents.SKIN.get())), "bench holds rustic drill");

        // Take item back
        ItemStack taken = bench.takeItem();
        check(h, !bench.hasItem(), "bench empty after takeItem");
        check(h, taken.is(ModItems.MINING_DRILL.get()), "taken item is drill");
        check(h, "rustic".equals(taken.get(ModDataComponents.SKIN.get())), "taken item has skin");

        // Break bench drops stored item
        bench.setItem(drill);
        h.destroyBlock(pos);

        List<ItemEntity> items = h.getEntities(net.minecraft.world.entity.EntityType.ITEM);
        boolean foundDrillDrop = false;
        for (ItemEntity itemEntity : items) {
            if (itemEntity.getItem().is(ModItems.MINING_DRILL.get())) {
                foundDrillDrop = true;
                break;
            }
        }
        check(h, foundDrillDrop, "breaking bench dropped stored machine");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void testSkinBenchNetworkValidation(GameTestHelper h) {
        clear(h, 5);
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos absPos = h.absolutePos(pos);
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setPos(absPos.getX() + 0.5, absPos.getY() + 1.0, absPos.getZ() + 0.5);

        h.setBlock(pos, ModBlocks.SKIN_BENCH.get().defaultBlockState());
        SkinBenchBlockEntity bench = (SkinBenchBlockEntity) h.getBlockEntity(pos);
        check(h, bench != null, "bench BE exists");

        ItemStack drill = new ItemStack(ModItems.MINING_DRILL.get());
        bench.setItem(drill);
        player.getInventory().clearContent();
        String fresh = com.holeydigsite.skin.SkinWear.FRESH;

        // 1. A skin the player has no kit for
        boolean resLocked = SkinBenchNetwork.handleApply(player, new SkinBenchNetwork.ApplySkinPayload(absPos, "obsidian", fresh));
        check(h, !resLocked, "must reject a skin without a kit");
        check(h, !bench.getItem().has(ModDataComponents.SKIN.get()), "drill skin unchanged");

        // 2. Try applying nonexistent skin
        boolean resInvalid = SkinBenchNetwork.handleApply(player, new SkinBenchNetwork.ApplySkinPayload(absPos, "nonexistent", fresh));
        check(h, !resInvalid, "must reject nonexistent skin");

        // 3. Try applying from too far away (> 8 blocks)
        player.getInventory().setItem(3, SkinKitItem.of("obsidian", SkinDefs.DRILL, fresh, 1));
        player.setPos(absPos.getX() + 20.0, absPos.getY(), absPos.getZ());
        boolean resFar = SkinBenchNetwork.handleApply(player, new SkinBenchNetwork.ApplySkinPayload(absPos, "obsidian", fresh));
        check(h, !resFar, "must reject payload from out of reach");
        check(h, player.getInventory().getItem(3).getCount() == 1, "kit kept when out of reach");
        player.setPos(absPos.getX() + 0.5, absPos.getY() + 1.0, absPos.getZ() + 0.5);

        // 4. A kit the player carries, in range
        boolean resOk = SkinBenchNetwork.handleApply(player, new SkinBenchNetwork.ApplySkinPayload(absPos, "obsidian", fresh));
        check(h, resOk, "must accept a carried kit in range");
        check(h, "obsidian".equals(bench.getItem().get(ModDataComponents.SKIN.get())), "drill now has obsidian skin");
        check(h, player.getInventory().getItem(3).isEmpty(), "the kit is used up");

        // 5. Taking it off gives the kit back
        boolean resDefault = SkinBenchNetwork.handleApply(player, SkinBenchNetwork.ApplySkinPayload.remove(absPos));
        check(h, resDefault, "must accept taking the skin off");
        check(h, !bench.getItem().has(ModDataComponents.SKIN.get()), "drill now has default skin (no component)");
        boolean back = false;
        for (ItemStack s : player.getInventory().items) back |= SkinKitItem.matches(s, "obsidian", SkinDefs.DRILL, fresh);
        check(h, back, "obsidian kit back in the inventory");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void testSkinCommands(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        var src = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var cmds = h.getLevel().getServer().getCommands();
        String fresh = com.holeydigsite.skin.SkinWear.FRESH;

        // Give a single kit
        cmds.performPrefixedCommand(src, "holeydigsite skin give @s brass mining_drill");
        check(h, kitCount(player, "brass", SkinDefs.DRILL, fresh) == 1, "has a brass drill kit");
        check(h, kitCount(player, "rustic", SkinDefs.DRILL, fresh) == 0, "does not have rustic");

        // Give all skins for one family
        cmds.performPrefixedCommand(src, "holeydigsite skin give @s all sluice_box");
        for (String id : SkinDefs.skins().keySet()) {
            check(h, kitCount(player, id, SkinDefs.SLUICE, fresh) == 1, "sluice kit for " + id);
        }
        check(h, SkinDefs.skins().size() >= 5, "all 5 skins exist");

        // Unknown skin gives nothing
        cmds.performPrefixedCommand(src, "holeydigsite skin give @s nonexistent mining_drill");
        check(h, kitCount(player, "brass", SkinDefs.DRILL, fresh) == 1, "brass still one");

        h.succeed();
    }

    private static int kitCount(ServerPlayer p, String skin, String family, String wear) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (SkinKitItem.matches(s, skin, family, wear)) n += s.getCount();
        return n;
    }

    /** The old unlock list is only kept for the one-time move to kits, so it (and the marker) must survive death and saving. */
    @GameTest(template = "empty")
    public static void testOldUnlocksSurviveDeathAndNBT(GameTestHelper h) {
        ServerPlayer p1 = h.makeMockServerPlayerInLevel();
        LedgerData d1 = LedgerData.get(p1);
        check(h, d1 != null, "ledger data exists");
        d1.clearOldSkins();
        d1.unlockedSkins().add("frosted");
        d1.unlockedSkins().add("obsidian");
        d1.setSkinsMigrated(false);

        // Clone on respawn
        ServerPlayer p2 = h.makeMockServerPlayerInLevel();
        LedgerData d2 = LedgerData.get(p2);
        check(h, d2 != null, "p2 ledger exists");
        d2.copyFrom(d1);
        check(h, d2.unlockedSkins().contains("frosted"), "frosted survives clone");
        check(h, d2.unlockedSkins().contains("obsidian"), "obsidian survives clone");
        check(h, !d2.unlockedSkins().contains("brass"), "brass not there on clone");
        check(h, !d2.skinsMigrated(), "marker state survives clone");

        // NBT serialization
        HolderLookup.Provider registries = h.getLevel().registryAccess();
        d1.setSkinsMigrated(true);
        CompoundTag tag = d1.serializeNBT(registries);

        LedgerData d3 = new LedgerData();
        d3.deserializeNBT(registries, tag);
        check(h, d3.unlockedSkins().contains("frosted"), "frosted survives NBT");
        check(h, d3.unlockedSkins().contains("obsidian"), "obsidian survives NBT");
        check(h, !d3.unlockedSkins().contains("brass"), "brass not there after NBT");
        check(h, d3.skinsMigrated(), "marker survives NBT");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void testLedgerSkinRewards(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        LedgerData data = LedgerData.get(player);
        check(h, data != null, "ledger data exists");
        data.resetAll();
        player.getInventory().clearContent();

        // Verify skin rewards in definitions
        check(h, LedgerDefs.level(13) != null, "level 13 exists");
        check(h, "rustic".equals(LedgerDefs.level(13).rewards().get(0).skin().orElse(null)), "L13 reward is rustic");

        check(h, LedgerDefs.level(22) != null, "level 22 exists");
        check(h, "rustic_weathered".equals(LedgerDefs.level(22).rewards().get(0).skin().orElse(null)), "L22 reward is rustic_weathered");

        check(h, LedgerDefs.level(33) != null, "level 33 exists");
        check(h, "frosted".equals(LedgerDefs.level(33).rewards().get(0).skin().orElse(null)), "L33 reward is frosted");

        check(h, LedgerDefs.level(39) != null, "level 39 exists");
        check(h, "brass".equals(LedgerDefs.level(39).rewards().get(0).skin().orElse(null)), "L39 reward is brass");

        check(h, LedgerDefs.level(47) != null, "level 47 exists");
        check(h, "obsidian".equals(LedgerDefs.level(47).rewards().get(0).skin().orElse(null)), "L47 reward is obsidian");

        // Give player enough renown to reach level 13
        data.setRenown(LedgerDefs.curve().cumulativeForLevel(13));
        check(h, data.level() >= 13, "player reached level 13");
        check(h, LedgerManager.isRewardAvailable(LedgerDefs.level(13).rewards().get(0), h.getLevel().registryAccess()), "reward available for level 13");

        // Claim reward
        boolean claimed = LedgerManager.claim(player, 13);
        check(h, claimed, "claimed level 13 reward");
        check(h, kitCount(player, "rustic", SkinDefs.DRILL, com.holeydigsite.skin.SkinWear.FRESH) == 1,
                "player now has a Fresh rustic drill kit from the ledger claim");
        check(h, data.unlockedSkins().isEmpty(), "nothing unlocked on the player");

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void testTradingPostSlotAndPrice(GameTestHelper h) {
        HolderLookup.Provider reg = h.getLevel().registryAccess();

        // Check catalogue price of skin bench is 2048 copper
        Optional<ShopItem> entry = ShopCatalogue.find(reg, ModItems.SKIN_BENCH.get());
        check(h, entry.isPresent(), "Skin bench must be in shop catalogue");
        check(h, entry.get().price() == 2048, "Skin bench price must be 2048 copper, got " + entry.get().price());

        // Check trading post layout slot 36
        Map<Integer, ResourceLocation> layout = SiteDefs.layout(ResourceLocation.fromNamespaceAndPath(HoleyDigsite.MODID, "home"));
        check(h, layout != null, "home layout exists");
        ResourceLocation slot36 = layout.get(36);
        check(h, slot36 != null, "slot 36 exists in home layout");
        check(h, "skin_bench".equals(slot36.getPath()), "slot 36 is skin_bench, got " + slot36);

        h.succeed();
    }

    @GameTest(template = "empty")
    public static void testAllSkinTexturesExist(GameTestHelper h) {
        String[] skins = {"rustic", "rustic_weathered", "brass", "frosted", "obsidian"};
        File baseDir = new File("src/main/resources/assets/holeydigsite/textures/block/skin");
        if (!baseDir.exists()) {
            baseDir = new File("../src/main/resources/assets/holeydigsite/textures/block/skin");
        }
        check(h, baseDir.isDirectory(), "skin texture base directory exists: " + baseDir.getAbsolutePath());

        String[] requiredTextures = {
                "mining_drill_lower_front.png",
                "mining_drill_lower_back.png",
                "mining_drill_lower_side.png",
                "mining_drill_upper_front.png",
                "mining_drill_upper_side.png",
                "mining_drill_upper_top.png",
                "mining_drill_t2_lower_front.png",
                "mining_drill_t2_upper_front.png",
                "mining_drill_t3_lower_front.png",
                "mining_drill_t3_upper_front.png",
                "sluice_wood.png",
                "sluice_t2_wood.png"
        };

        for (String skin : skins) {
            File skinDir = new File(baseDir, skin);
            check(h, skinDir.isDirectory(), "skin directory exists for " + skin);
            File[] files = skinDir.listFiles((dir, name) -> name.endsWith(".png"));
            check(h, files != null && files.length == 54, "skin " + skin + " must have 54 png files, got " + (files == null ? 0 : files.length));

            for (String req : requiredTextures) {
                File texFile = new File(skinDir, req);
                check(h, texFile.isFile(), "skin " + skin + " missing required texture: " + req);
            }
        }

        h.succeed();
    }
}
