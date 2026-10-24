package com.holeydigsite.devtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.DecorBlock;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopButtonBlock;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.shop.ShopDisplayBlock;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.Sites;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 21b (sandbox only): Ledger titles (unlock / select / clear / re-log / chat prefix), "Coming soon" levels, the six
 * decorative blocks (placing, facing, drops, light, wall rack, assets), the Ledger blueprint gate at the Buy Pad / Shop Display,
 * the Ledger at the Trading Post in an old world, and the ownerless-sluice no-credit rule.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21bTests {
    private static void check(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    private static String key(Component c) {
        return c.getString().toLowerCase(java.util.Locale.ROOT);
    }

    private static LedgerDefs.LevelDef level(int n, List<LedgerDefs.RewardEntry> rewards, String coins, String title) {
        return new LedgerDefs.LevelDef(n, List.copyOf(rewards), Optional.ofNullable(coins), Optional.ofNullable(title));
    }

    private static LedgerDefs.RewardEntry item(String id, int count) {
        return new LedgerDefs.RewardEntry(Optional.of(ResourceLocation.parse(id)), count, Optional.empty(), Optional.empty());
    }

    // ------------------------------------------------------------------------------------------------ 5a: ownerless sluice

    /** An ownerless sluice fed by a drill + belt gives no line_running / Renown to a player standing next to it. */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void ownerlessLineGivesBystanderNothing(GameTestHelper h) {
        ServerPlayer bystander = player(h);
        LedgerData bd = LedgerData.get(bystander);
        BlockPos drillRel = new BlockPos(1, 2, 2);
        BlockPos beltRel = new BlockPos(1, 2, 3);
        BlockPos sluiceRel = new BlockPos(1, 2, 4);
        BlockPos stand = h.absolutePos(new BlockPos(2, 2, 4));
        bystander.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);

        h.setBlock(drillRel, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(com.holeydigsite.drill.MiningDrillBlock.FACING, Direction.SOUTH));
        h.setBlock(beltRel, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(com.holeydigsite.belt.ClumpBeltBlock.FACING, Direction.SOUTH));
        TestWater.underBelt(h, beltRel);
        h.setBlock(sluiceRel, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.SOUTH));
        SluiceBoxBlockEntity sluice = (SluiceBoxBlockEntity) h.getBlockEntity(sluiceRel);
        check(h, sluice.owner() == null, "the sluice is ownerless");
        check(h, com.holeydigsite.drill.ClumpOutputs.tryOutput(h.getLevel(), h.absolutePos(beltRel), Direction.NORTH, 1), "drill puts a clump on the belt");
        h.runAfterDelay(30, () -> {
            check(h, sluice.queued() == 1, "the clump reached the sluice");
            sluice.washOneClump(h.getLevel(), h.absolutePos(sluiceRel));
            check(h, !bd.isMilestoneCompleted(HoleyDigsite.id("first_line")), "no line_running for the bystander");
            check(h, !bd.isMilestoneCompleted(HoleyDigsite.id("first_pan")) && bd.sluiceRenown() == 0 && bd.renown() == 0,
                    "no pan milestone / Renown for the bystander");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ 1: coming soon

    /** A level with a missing item (or title) cannot be claimed, is skipped by Claim All and becomes claimable once the content exists. */
    @GameTest(template = "empty")
    public static void comingSoonLevels(GameTestHelper h) {
        Map<Integer, LedgerDefs.LevelDef> saved = new LinkedHashMap<>(LedgerDefs.levels());
        List<String> savedTitles = new ArrayList<>(LedgerDefs.titles());
        try {
            ServerPlayer p = player(h);
            LedgerData d = LedgerData.get(p);
            d.setRenown(5000);
            var reg = p.registryAccess();

            Map<Integer, LedgerDefs.LevelDef> edited = new LinkedHashMap<>(saved);
            edited.put(1, level(1, List.of(item("holeydigsite:no_such_item_yet", 1)), "S", null));        // missing item
            edited.put(2, level(2, List.of(), "S", "ghost_title"));                                        // missing title
            edited.put(3, level(3, List.of(item("minecraft:bread", 2)), null, null));                      // fine
            LedgerDefs.setLevelsForTest(edited);

            check(h, !LedgerManager.isLevelReady(LedgerDefs.level(1), reg), "level 1 (missing item) is Coming soon");
            check(h, !LedgerManager.isLevelReady(LedgerDefs.level(2), reg), "level 2 (missing title) is Coming soon");
            check(h, LedgerManager.isLevelReady(LedgerDefs.level(3), reg), "level 3 is ready");
            check(h, !LedgerManager.claim(p, 1) && !d.isLevelClaimed(1), "level 1 cannot be claimed and stays unclaimed");
            check(h, !LedgerManager.claim(p, 2) && !d.isLevelClaimed(2), "level 2 cannot be claimed and stays unclaimed");
            check(h, p.getInventory().isEmpty(), "nothing was given by the failed claims");

            int claimed = LedgerManager.claimAll(p);
            check(h, d.isLevelClaimed(3) && !d.isLevelClaimed(1) && !d.isLevelClaimed(2), "Claim All claims only the ready levels");
            check(h, claimed >= 1 && p.getInventory().countItem(net.minecraft.world.item.Items.BREAD) >= 2, "Claim All counted/gave the ready ones");
            check(h, !LedgerManager.claim(p, 2), "Claim All did not claim level 2");

            // the title now exists -> level 2 becomes claimable and unlocks it
            List<String> more = new ArrayList<>(savedTitles);
            more.add("ghost_title");
            LedgerDefs.setTitlesForTest(more);
            check(h, LedgerManager.isLevelReady(LedgerDefs.level(2), reg), "level 2 is ready once its title exists");
            check(h, LedgerManager.claim(p, 2) && d.isLevelClaimed(2) && d.hasTitle("ghost_title"), "level 2 claimable later, title unlocked");
            h.succeed();
        } finally {
            LedgerDefs.setLevelsForTest(saved);
            LedgerDefs.setTitlesForTest(savedTitles);
        }
    }

    /** Level 20's blueprint reward becomes "Coming soon" if its catalogue entry is missing: every shipped level is ready now. */
    @GameTest(template = "empty")
    public static void everyShippedLevelIsReady(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        for (int l = 1; l <= LedgerDefs.MAX_LEVEL; l++) {
            check(h, LedgerManager.isLevelReady(LedgerDefs.level(l), reg), "level " + l + " is not ready (would show Coming soon)");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 2: titles

    @GameTest(template = "empty")
    public static void titlesUnlockSelectClearRelogAndChatPrefix(GameTestHelper h) {
        ServerPlayer p = player(h);
        LedgerData d = LedgerData.get(p);
        d.setRenown(5000);
        check(h, LedgerDefs.titles().size() == 10, "10 titles");
        check(h, !LedgerManager.selectTitle(p, "greenhorn") && d.activeTitle().isEmpty(), "a locked title cannot be picked");

        check(h, LedgerManager.claim(p, 5), "claim level 5");
        check(h, d.hasTitle("greenhorn"), "claiming level 5 unlocks Greenhorn");
        check(h, d.activeTitle().isEmpty(), "nothing is selected by itself");
        check(h, !p.getDisplayName().getString().toLowerCase(java.util.Locale.ROOT).contains("greenhorn"), "no prefix before picking");

        check(h, LedgerManager.selectTitle(p, "greenhorn"), "pick Greenhorn");
        check(h, "greenhorn".equals(d.activeTitle()), "Greenhorn is active");
        String name = key(p.getDisplayName());
        check(h, name.startsWith("[") && name.contains("greenhorn") && name.indexOf("greenhorn") < name.indexOf(p.getName().getString().toLowerCase(java.util.Locale.ROOT)),
                "chat name has the title before the player's name: " + name);
        check(h, !LedgerManager.selectTitle(p, "claim_baron"), "another locked title is refused");

        // a second title
        check(h, LedgerManager.claim(p, 10) && d.hasTitle("panhandler"), "level 10 unlocks Panhandler");
        check(h, LedgerManager.selectTitle(p, "panhandler") && key(p.getDisplayName()).contains("panhandler")
                && !key(p.getDisplayName()).contains("greenhorn"), "switching title switches the prefix");

        // save/load (re-log) keeps unlocked + chosen
        CompoundTag saved = d.serializeNBT(h.getLevel().registryAccess());
        LedgerData loaded = new LedgerData();
        loaded.deserializeNBT(h.getLevel().registryAccess(), saved);
        check(h, loaded.hasTitle("greenhorn") && loaded.hasTitle("panhandler") && "panhandler".equals(loaded.activeTitle()),
                "unlocked titles and the choice survive save/load");

        // death keeps them too
        ServerPlayer respawned = player(h);
        respawned.restoreFrom(p, false);
        LedgerData afterDeath = LedgerData.get(respawned);
        check(h, afterDeath.hasTitle("greenhorn") && "panhandler".equals(afterDeath.activeTitle()), "death keeps titles");
        check(h, key(respawned.getDisplayName()).contains("panhandler"), "the prefix is back after respawn");

        // clear
        check(h, LedgerManager.selectTitle(p, "") && d.activeTitle().isEmpty(), "clearing the title works");
        check(h, !key(p.getDisplayName()).contains("panhandler"), "no prefix after clearing");
        check(h, d.hasTitle("panhandler"), "clearing does not lock the title");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 3: decor blocks

    private static final String[] DECOR = {"claim_flag", "ore_cart", "tool_rack", "lucky_lantern", "gem_display_case", "golden_pan_trophy"};

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = 1; y <= 3; y++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static InteractionResult place(GameTestHelper h, ServerPlayer p, Item item, BlockPos clickedRel, Direction face) {
        BlockPos abs = h.absolutePos(clickedRel);
        BlockPlaceContext ctx = new BlockPlaceContext(h.getLevel(), p, InteractionHand.MAIN_HAND, new ItemStack(item),
                new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false));
        return ((BlockItem) item).place(ctx);
    }

    @GameTest(template = "empty")
    public static void decorBlocksPlaceFaceThePlayerAndDropThemselves(GameTestHelper h) {
        floor(h);
        ServerPlayer p = player(h);
        p.setPos(h.absolutePos(new BlockPos(8, 1, 4)).getX() + 0.5, h.absolutePos(new BlockPos(8, 1, 4)).getY(), h.absolutePos(new BlockPos(8, 1, 4)).getZ() + 0.5);
        Map<String, Item> items = new LinkedHashMap<>();
        for (String id : DECOR) {
            Item it = BuiltInRegistries.ITEM.get(HoleyDigsite.id(id));
            check(h, it != net.minecraft.world.item.Items.AIR && it instanceof BlockItem, id + " is a registered block item");
            items.put(id, it);
        }
        float[] yRots = {0F, 90F, 180F, 270F};
        for (String id : DECOR) {
            if (id.equals("tool_rack")) continue;
            Block block = BuiltInRegistries.BLOCK.get(HoleyDigsite.id(id));
            check(h, block instanceof DecorBlock, id + " is a DecorBlock");
            BlockPos at = new BlockPos(1, 1, 1);
            for (float yRot : yRots) {
                h.setBlock(at, Blocks.AIR);
                p.setYRot(yRot);
                InteractionResult r = place(h, p, items.get(id), at.below(), Direction.UP);
                BlockState st = h.getBlockState(at);
                check(h, r.consumesAction() && st.is(block), id + " places on the floor (yRot " + yRot + "): " + st);
                Direction expected = Direction.fromYRot(yRot).getOpposite();
                check(h, st.getValue(DecorBlock.FACING) == expected, id + " faces the player: yRot " + yRot + " -> " + st.getValue(DecorBlock.FACING));
                check(h, !st.getShape(h.getLevel(), h.absolutePos(at)).isEmpty(), id + " has a collision shape");
            }
            // drops itself
            BlockState st = h.getBlockState(at);
            List<ItemStack> drops = Block.getDrops(st, h.getLevel(), h.absolutePos(at), null);
            check(h, drops.size() == 1 && drops.get(0).is(items.get(id)), id + " drops itself: " + drops);
            h.setBlock(at, Blocks.AIR);
        }

        // light
        check(h, ModBlocks.LUCKY_LANTERN.get().defaultBlockState().getLightEmission() == 15, "Lucky Lantern gives light 15");
        for (String id : DECOR) {
            if (!id.equals("lucky_lantern")) {
                check(h, BuiltInRegistries.BLOCK.get(HoleyDigsite.id(id)).defaultBlockState().getLightEmission() == 0, id + " gives no light");
            }
        }

        // shapes follow the facing (ore cart is long on its x axis facing north/south)
        BlockState cart = ModBlocks.ORE_CART.get().defaultBlockState();
        var north = cart.setValue(DecorBlock.FACING, Direction.NORTH).getShape(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1))).bounds();
        var east = cart.setValue(DecorBlock.FACING, Direction.EAST).getShape(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1))).bounds();
        check(h, north.getXsize() > north.getZsize() && east.getZsize() > east.getXsize(), "ore cart shape rotates with the facing");
        var flag = ModBlocks.CLAIM_FLAG.get().defaultBlockState().getShape(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1))).bounds();
        check(h, flag.getXsize() <= 0.3 && flag.maxY == 1.0, "claim flag collision is just the post, one block tall");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void toolRackIsAWallBlock(GameTestHelper h) {
        floor(h);
        ServerPlayer p = player(h);
        BlockPos pp = h.absolutePos(new BlockPos(8, 1, 4));
        p.setPos(pp.getX() + 0.5, pp.getY(), pp.getZ() + 0.5);
        Item rack = ModItems.TOOL_RACK.get();
        BlockPos wall = new BlockPos(3, 2, 2);
        h.setBlock(wall, Blocks.STONE);
        BlockPos at = new BlockPos(3, 2, 1);
        // click the north face of the wall: the rack hangs there and faces north (towards the player standing north of it)
        InteractionResult r = place(h, p, rack, wall, Direction.NORTH);
        BlockState st = h.getBlockState(at);
        check(h, r.consumesAction() && st.is(ModBlocks.TOOL_RACK.get()) && st.getValue(DecorBlock.FACING) == Direction.NORTH, "rack hangs on the wall's north face: " + st);
        var b = st.getShape(h.getLevel(), h.absolutePos(at)).bounds();
        check(h, b.minZ >= 0.7 && b.maxZ == 1.0, "rack is thin and sits on the wall side: " + b);
        // east face -> faces east
        BlockPos at2 = new BlockPos(4, 2, 2);
        place(h, p, rack, wall, Direction.EAST);
        check(h, h.getBlockState(at2).is(ModBlocks.TOOL_RACK.get()) && h.getBlockState(at2).getValue(DecorBlock.FACING) == Direction.EAST, "rack on the east face faces east");
        // drops itself and breaks when its wall goes
        List<ItemStack> drops = Block.getDrops(st, h.getLevel(), h.absolutePos(at), null);
        check(h, drops.size() == 1 && drops.get(0).is(rack), "rack drops itself");
        h.setBlock(wall, Blocks.AIR);
        check(h, h.getBlockState(at).isAir() && h.getBlockState(at2).isAir(), "rack falls off when its wall is removed");
        // on a bare floor (no wall near) it does not place
        h.setBlock(new BlockPos(6, 1, 1), Blocks.AIR);
        InteractionResult none = place(h, p, rack, new BlockPos(6, 0, 1), Direction.UP);
        check(h, !none.consumesAction() && h.getBlockState(new BlockPos(6, 1, 1)).isAir(), "rack needs a wall");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void decorAssetsExist(GameTestHelper h) {
        for (String id : DECOR) {
            JsonObject state = json("/assets/holeydigsite/blockstates/" + id + ".json");
            check(h, state.getAsJsonObject("variants").keySet().size() == 4, id + " blockstate has the 4 facings");
            JsonObject model = json("/assets/holeydigsite/models/block/" + id + ".json");
            check(h, model.has("elements") && model.getAsJsonArray("elements").size() >= 4, id + " model has elements");
            JsonObject itemModel = json("/assets/holeydigsite/models/item/" + id + ".json");
            check(h, itemModel.get("parent").getAsString().equals("holeydigsite:block/" + id), id + " item model");
            check(h, Phase21bTests.class.getResource("/assets/holeydigsite/textures/block/" + id + ".png") != null, id + " texture");
            json("/data/holeydigsite/loot_table/blocks/" + id + ".json");
            // every element stays inside the allowed range, and no two faces lie in the same plane and overlap (z-fighting)
            Map<String, List<double[]>> planes = new HashMap<>();
            model.getAsJsonArray("elements").forEach(e -> {
                JsonObject el = e.getAsJsonObject();
                for (String k : new String[]{"from", "to"}) {
                    el.getAsJsonArray(k).forEach(v -> check(h, v.getAsDouble() >= -16 && v.getAsDouble() <= 32, id + " element out of range"));
                }
                var frm = el.getAsJsonArray("from");
                var to = el.getAsJsonArray("to");
                double x0 = frm.get(0).getAsDouble(), y0 = frm.get(1).getAsDouble(), z0 = frm.get(2).getAsDouble();
                double x1 = to.get(0).getAsDouble(), y1 = to.get(1).getAsDouble(), z1 = to.get(2).getAsDouble();
                if (id.equals("golden_pan_trophy")) {
                    check(h, z1 <= 15.0, "golden_pan_trophy has no frying-pan handle sticking out the back");
                }
                JsonObject faces = el.getAsJsonObject("faces");
                for (String f : faces.keySet()) {
                    String key;
                    double[] rect;
                    switch (f) {
                        case "north" -> { key = String.format(java.util.Locale.ROOT, "z:%.4f", z0); rect = new double[]{x0, y0, x1, y1}; }
                        case "south" -> { key = String.format(java.util.Locale.ROOT, "z:%.4f", z1); rect = new double[]{x0, y0, x1, y1}; }
                        case "west"  -> { key = String.format(java.util.Locale.ROOT, "x:%.4f", x0); rect = new double[]{z0, y0, z1, y1}; }
                        case "east"  -> { key = String.format(java.util.Locale.ROOT, "x:%.4f", x1); rect = new double[]{z0, y0, z1, y1}; }
                        case "down"  -> { key = String.format(java.util.Locale.ROOT, "y:%.4f", y0); rect = new double[]{x0, z0, x1, z1}; }
                        case "up"    -> { key = String.format(java.util.Locale.ROOT, "y:%.4f", y1); rect = new double[]{x0, z0, x1, z1}; }
                        default -> throw new IllegalStateException("unknown face " + f);
                    }
                    List<double[]> existing = planes.computeIfAbsent(key, k -> new ArrayList<>());
                    if (id.equals("claim_flag")) {
                        check(h, existing.isEmpty(), "claim_flag has two faces in the same plane " + key);
                    }
                    for (double[] o : existing) {
                        boolean overlap = Math.min(rect[2], o[2]) > Math.max(rect[0], o[0]) + 1e-5
                                && Math.min(rect[3], o[3]) > Math.max(rect[1], o[1]) + 1e-5;
                        check(h, !overlap, id + " has overlapping coplanar faces in plane " + key);
                    }
                    existing.add(rect);
                }
            });
        }
        h.succeed();
    }

    private static JsonObject json(String path) {
        try (InputStream in = Phase21bTests.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------------------------------------ 4: blueprints + shop

    @GameTest(template = "empty")
    public static void ledgerBlueprintGateAtTheBuyPad(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        for (String id : new String[]{"claim_flag", "ore_cart", "lucky_lantern"}) {
            Item it = BuiltInRegistries.ITEM.get(HoleyDigsite.id(id));
            var entry = ShopCatalogue.find(reg, it);
            check(h, entry.isPresent() && entry.get().ledger(), id + " is a catalogue entry marked ledger");
            check(h, entry.get().price() > 0 && entry.get().price() <= 64, id + " is cheap decoration: " + entry.get().price());
        }
        check(h, !ShopCatalogue.find(reg, ModItems.TOOL_RACK.get()).isPresent(), "tool rack has no blueprint");
        check(h, !ShopCatalogue.find(reg, ModItems.PAN.get()).get().ledger(), "normal items are not ledger-only");

        // Buy Pad: a Ledger blueprint (the only way to get one) buys the item
        for (int x = 0; x < 9; x++) for (int z = 0; z < 5; z++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        BlockPos btn = new BlockPos(4, 1, 2), buy = new BlockPos(5, 1, 2), pan = new BlockPos(4, 1, 3);
        h.setBlock(btn, ModBlocks.SHOP_BUTTON.get());
        h.setBlock(buy, ModBlocks.BUY_PAD.get());
        h.setBlock(pan, ModBlocks.MONEY_PAN.get());
        ServerPlayer p = player(h);
        ItemStack bp = BlueprintItem.of(ModItems.CLAIM_FLAG.get());
        check(h, ModBlocks.BUY_PAD.get().accepts(bp, h.getLevel(), h.absolutePos(buy)), "the Buy Pad takes the Ledger blueprint");
        ((PadBlockEntity) h.getBlockEntity(buy)).insert(bp.copy());
        ((MoneyPanBlockEntity) h.getBlockEntity(pan)).deposit(100);
        ShopButtonBlock.press(h.getLevel(), h.absolutePos(btn), p);
        long price = ShopCatalogue.find(reg, ModItems.CLAIM_FLAG.get()).get().price();
        check(h, p.getInventory().countItem(ModItems.CLAIM_FLAG.get()) == 1, "a Claim Flag was bought with its blueprint");
        check(h, ((MoneyPanBlockEntity) h.getBlockEntity(pan)).balance() == 100 - price, "paid the blueprint price");

        // ... but a Shop Display never hands a Ledger blueprint out (it does for normal items)
        BlockPos disp = new BlockPos(1, 1, 1);
        h.setBlock(disp, ModBlocks.SHOP_DISPLAY.get());
        ShopDisplayBlockEntity be = (ShopDisplayBlockEntity) h.getBlockEntity(disp);
        be.setItem(ModItems.ORE_CART.get());
        ServerPlayer q = player(h);
        ShopDisplayBlock.interact(h.getLevel(), h.absolutePos(disp), q, ItemStack.EMPTY);
        check(h, q.getInventory().isEmpty() || q.getInventory().countItem(ModItems.BLUEPRINT.get()) == 0, "a Shop Display gives no Ledger blueprint");
        be.setItem(ModItems.PAN.get());
        ShopDisplayBlock.interact(h.getLevel(), h.absolutePos(disp), q, ItemStack.EMPTY);
        check(h, q.getInventory().countItem(ModItems.BLUEPRINT.get()) == 1, "a Shop Display still gives a normal blueprint");

        // the Ledger rewards give blueprints of exactly these three
        LedgerData d = LedgerData.get(q);
        d.setRenown(5000);
        q.getInventory().clearContent();
        check(h, LedgerManager.claim(q, 27), "claim level 27 (Claim Flag blueprint)");
        ItemStack got = q.getInventory().getItem(0);
        check(h, got.is(ModItems.BLUEPRINT.get()) && BlueprintItem.target(got) == ModItems.CLAIM_FLAG.get(), "level 27 gave the Claim Flag blueprint: " + got);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ledgerRewardsGiveTheDecorBlocks(GameTestHelper h) {
        ServerPlayer p = player(h);
        LedgerData d = LedgerData.get(p);
        d.setRenown(5000);
        Object[][] expect = {{10, "claim_flag"}, {20, "ore_cart"}, {30, "tool_rack"}, {35, "lucky_lantern"}, {40, "gem_display_case"}, {50, "golden_pan_trophy"}};
        for (Object[] e : expect) {
            p.getInventory().clearContent();
            check(h, LedgerManager.claim(p, (Integer) e[0]), "claim level " + e[0]);
            Item it = BuiltInRegistries.ITEM.get(HoleyDigsite.id((String) e[1]));
            check(h, p.getInventory().countItem(it) == 1, "level " + e[0] + " gave a " + e[1]);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 4b: Trading Post

    /** An old store without the Ledger display gets it (slot 30, 1 copper blueprint) on the next world load, without a rebuild. */
    @GameTest(template = "empty", batch = "p21b_store", timeoutTicks = 200)
    public static void ledgerSoldAtTheTradingPostInAnOldWorld(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = new BlockPos(24000, 100, 20000);
        ResourceLocation home = HoleyDigsite.id("home");
        try {
            BoundingBox big = new BoundingBox(a.getX() - 4, a.getY() - 2, a.getZ() - 4, a.getX() + 40, a.getY() + 34, a.getZ() + 40);
            Sites.clear(l, big);
            for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(48))) e.discard();
            SiteDef def = SiteDefs.get(home);
            Map<Integer, ResourceLocation> full = SiteDefs.layout(def.layout());
            ResourceLocation ledgerId = HoleyDigsite.id("prospectors_ledger");
            check(h, ledgerId.equals(full.get(30)), "the shipped home layout shows the Ledger in slot 30: " + full.get(30));
            Map<Integer, ResourceLocation> old = new HashMap<>(full);
            old.remove(30);
            SiteDefs.overrideLayout(def.layout(), old);          // the world as it was before 21b
            Sites.Result built = Sites.build(l, def, a, null, false);
            BoundingBox zone = built.site().zone();
            check(h, displayItem(l, zone, 30) == null, "the old store has no Ledger on display");
            int version = built.site().version();
            SiteDefs.clearOverrides();                           // load with the new files
            Sites.checkAll(l);
            SiteData.Site now = SiteData.get(l).get(home, a);
            check(h, now != null && now.version() == version, "no rebuild: version unchanged");
            Item shown = displayItem(l, zone, 30);
            check(h, shown == ModItems.PROSPECTORS_LEDGER.get(), "slot 30 now shows the Prospector's Ledger: " + shown);
            var entry = ShopCatalogue.find(l.registryAccess(), ModItems.PROSPECTORS_LEDGER.get());
            check(h, entry.isPresent() && entry.get().price() == 1 && !entry.get().ledger(), "the Ledger costs 1 copper and is buyable");
            h.succeed();
        } finally {
            SiteDefs.clearOverrides();
            SiteData data = SiteData.get(l);
            for (SiteData.Site s : new ArrayList<>(data.all())) {
                if (s.zone().isInside(a) || s.anchor().equals(a)) data.remove(s.key());
            }
            Sites.clear(l, new BoundingBox(a.getX() - 4, a.getY() - 2, a.getZ() - 4, a.getX() + 40, a.getY() + 34, a.getZ() + 40));
        }
    }

    private static Item displayItem(ServerLevel l, BoundingBox zone, int slot) {
        for (BlockPos p : BlockPos.betweenClosed(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ())) {
            if (l.getBlockEntity(p) instanceof ShopDisplayBlockEntity d && d.slot() == slot) {
                return d.item();
            }
        }
        return null;
    }
}
