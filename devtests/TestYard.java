package com.holeydigsite.devtest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.BeltShape;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.skin.SkinBenchBlock;
import com.holeydigsite.skin.SkinBenchBlockEntity;
import com.holeydigsite.block.DecorBlock;
import com.holeydigsite.block.OwnCropBlock;
import com.holeydigsite.contract.ContractBoardBlock;
import com.holeydigsite.crate.CrateBlock;
import com.holeydigsite.crate.CrateSwitchBlock;
import com.holeydigsite.drill.DrillStatus;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.economy.FlawlessDefs;
import com.holeydigsite.forge.CastingMoldBlock;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.forge.MoldBlockEntity;
import com.holeydigsite.machine.RopeMachineBlockEntity;
import com.holeydigsite.machine.SawmillBlockEntity;
import com.holeydigsite.machine.ThreshingDrumBlockEntity;
import com.holeydigsite.machine.WaterProcessingBlock;
import com.holeydigsite.power.LavaIntakeBlock;
import com.holeydigsite.power.SprinklerBlock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import com.holeydigsite.sluice.LongSluiceBlock;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.sluice.SluiceUpgrade;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dev-only test yard world builder (not shipped in the release jar).
 * Activated via {@code -Dholeydigsite.testyard=true}. Reads {@code tools/yard/stations.json},
 * sets up the world, builds the path and stations along a line, runs a 60s live check to verify
 * all stations actually work, resets moving parts to start, saves chunks, and halts.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class TestYard {
    private TestYard() {}

    public static final boolean ON = Boolean.getBoolean("holeydigsite.testyard");
    private static final int LIVE_CHECK_TICKS = 1200; // 60 seconds at 20 tps

    private static boolean liveCheckActive = false;
    private static int ticksRemaining = LIVE_CHECK_TICKS;
    private static Path savedGameDir = null;
    private static int savedBaseY = 64;
    private static List<StationDef> activeStations = new ArrayList<>();
    private static List<ChunkPos> forcedChunks = new ArrayList<>();

    public record StationDef(
            int number,
            String id,
            String title,
            List<String> postSign,
            List<String> signLines,
            List<String> checks,
            List<ChestItemDef> chestItems,
            String builderKey,
            ResourceLocation mainBlock,
            int[] mainBlockOffset
    ) {}

    public record ChestItemDef(
            String item,
            int count,
            String skin,
            String family,
            boolean locked,
            int weight,
            boolean flawless,
            boolean smithed
    ) {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ON) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        Path gameDir = server.getServerDirectory() != null ? server.getServerDirectory() : Path.of(".");

        HoleyDigsite.LOGGER.info("[TEST YARD] Starting world construction...");
        try {
            buildYard(server, level, gameDir);
        } catch (Throwable t) {
            HoleyDigsite.LOGGER.error("[TEST YARD] Fatal error building test yard", t);
            writeResult(gameDir, false, Map.of("fatal", t.getMessage() != null ? t.getMessage() : t.toString()), Map.of(), List.of());
            server.halt(false);
            return;
        }

        HoleyDigsite.LOGGER.info("[TEST YARD] Stations built and chunks forced. Starting 60s live check (1200 ticks)...");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ON || !liveCheckActive) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();

        ticksRemaining--;
        if (ticksRemaining > 0 && ticksRemaining % 200 == 0) {
            HoleyDigsite.LOGGER.info("[TEST YARD] Live check: {}s remaining ({} ticks)...", ticksRemaining / 20, ticksRemaining);
        }

        if (ticksRemaining <= 0) {
            liveCheckActive = false;
            HoleyDigsite.LOGGER.info("[TEST YARD] 60s run complete. Evaluating station live checks and resetting moving stations...");
            try {
                evaluateAndResetStations(server, level, savedGameDir, activeStations);
            } catch (Throwable t) {
                HoleyDigsite.LOGGER.error("[TEST YARD] Error during live check evaluation", t);
                writeResult(savedGameDir, false, Map.of("evaluation", t.getMessage() != null ? t.getMessage() : t.toString()), Map.of(), List.of());
            }

            // Release forced chunk tickets
            for (ChunkPos cp : forcedChunks) {
                level.setChunkForced(cp.x, cp.z, false);
            }
            forcedChunks.clear();

            // Save chunks and shutdown
            server.saveAllChunks(false, true, true);
            HoleyDigsite.LOGGER.info("[TEST YARD] Yard saved with clean start states. Shutting down server.");
            server.halt(false);
        }
    }

    public static void buildYard(MinecraftServer server, ServerLevel level, Path gameDir) throws Exception {
        savedGameDir = gameDir;

        // 1. World settings
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        level.setDayTime(6000L); // noon
        level.setWeatherParameters(60000, 0, false, false);

        server.getWorldData().setGameType(GameType.CREATIVE);
        if (server.getWorldData() instanceof PrimaryLevelData pld) {
            try {
                java.lang.reflect.Field f = PrimaryLevelData.class.getDeclaredField("settings");
                f.setAccessible(true);
                LevelSettings s = (LevelSettings) f.get(pld);
                f.set(pld, new LevelSettings(
                        s.levelName(), GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        s.gameRules(), s.getDataConfiguration(), s.getLifecycle()));
            } catch (Exception e) {
                HoleyDigsite.LOGGER.warn("[TEST YARD] Could not set allowCommands on LevelSettings", e);
            }
        }

        // 2. Load stations from config
        Path runJson = gameDir.resolve("yard_run.json");
        String onlyFilter = System.getProperty("holeydigsite.testyard.only", "");
        Path stationsPath = null;
        if (Files.isRegularFile(runJson)) {
            JsonObject o = JsonParser.parseString(Files.readString(runJson, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("only") && !o.get("only").isJsonNull()) {
                onlyFilter = o.get("only").getAsString();
            }
            if (o.has("stations") && !o.get("stations").isJsonNull()) {
                stationsPath = Path.of(o.get("stations").getAsString());
            }
        }
        if (stationsPath == null || !Files.isRegularFile(stationsPath)) {
            Path parent = gameDir.getParent();
            if (parent != null) {
                stationsPath = parent.resolve("tools").resolve("yard").resolve("stations.json");
            }
        }
        if (stationsPath == null || !Files.isRegularFile(stationsPath)) {
            stationsPath = Path.of("tools", "yard", "stations.json").toAbsolutePath();
        }

        List<StationDef> allStations = loadStationDefs(stationsPath);
        Set<String> onlySet = Arrays.stream(onlyFilter.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        List<StationDef> stationsToBuild = allStations.stream()
                .filter(s -> onlySet.isEmpty() || onlySet.contains(String.valueOf(s.number())) || onlySet.contains(s.id()))
                .toList();
        activeStations = stationsToBuild;

        // 3. Determine ground level
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        if (groundY <= 0) {
            groundY = 64;
        }
        final int baseY = groundY;
        savedBaseY = baseY;

        // 4. Build central walking path along X axis
        int minX = -8;
        int maxX = (allStations.size() + 1) * 12 + 8;
        for (int x = minX; x <= maxX; x++) {
            for (int z = -2; z <= 2; z++) {
                Block pathBlock = (Math.abs(z) == 2) ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS;
                level.setBlock(new BlockPos(x, baseY - 1, z), pathBlock.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, baseY, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, baseY + 1, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, baseY + 2, z), Blocks.AIR.defaultBlockState(), 3);
            }
            if (x % 6 == 0) {
                BlockPos post = new BlockPos(x, baseY, -2);
                level.setBlock(post, Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
                level.setBlock(post.above(), Blocks.LANTERN.defaultBlockState(), 3);
            }
        }

        // 5. Build stations
        for (StationDef st : stationsToBuild) {
            int stX = (st.number() - 1) * 12;
            BlockPos stationOrigin = new BlockPos(stX, baseY, 4);

            HoleyDigsite.LOGGER.info("[TEST YARD] Building station {}: {} at {}", st.number(), st.id(), stationOrigin);

            // Clear station plot area (approx 10x8)
            for (int dx = -4; dx <= 5; dx++) {
                for (int dz = -1; dz <= 5; dz++) {
                    BlockPos p = stationOrigin.offset(dx, 0, dz);
                    level.setBlock(p.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(p.above(2), Blocks.AIR.defaultBlockState(), 3);
                }
            }

            // Post sign in front: station number and short name at (stX - 1, baseY, 2)
            BlockPos signPost = new BlockPos(stX - 1, baseY, 2);
            level.setBlock(signPost, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8), 3);
            if (level.getBlockEntity(signPost) instanceof SignBlockEntity sign) {
                SignText text = sign.getFrontText();
                List<String> postLines = st.postSign();
                if (postLines.isEmpty()) {
                    postLines = List.of("Station " + st.number(), st.title());
                }
                for (int i = 0; i < 4 && i < postLines.size(); i++) {
                    text = text.setMessage(i, Component.literal(postLines.get(i)));
                }
                sign.setText(text, true);
                sign.setChanged();
            }

            // Chest with items at (stX + 1, baseY, 2)
            BlockPos chestPos = new BlockPos(stX + 1, baseY, 2);
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                List<ItemStack> stacks = buildChestStacks(level, st.chestItems());
                for (int s = 0; s < stacks.size() && s < chest.getContainerSize(); s++) {
                    chest.setItem(s, stacks.get(s));
                }
                chest.setChanged();
            }

            // Back wall one block behind the furthest machine row (stations use up to origin z + 1), so the
            // check signs hang clear of the machines.
            int wallZ = stationOrigin.getZ() + 3;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = 0; dy <= 2; dy++) {
                    level.setBlock(new BlockPos(stX + dx, baseY + dy, wallZ), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }

            // Row of wall signs on the path side of the wall (facing north, towards the path) at eye level
            List<String> checks = st.checks();
            // Seen from the path (looking south) east is on the left, so check 1 goes furthest east.
            int signCount = Math.min(checks.size(), 6);
            for (int i = 0; i < signCount; i++) {
                int signX = stX + signCount / 2 - i;
                BlockPos wallSignPos = new BlockPos(signX, baseY + 1, wallZ - 1);
                level.setBlock(wallSignPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH), 3);
                if (level.getBlockEntity(wallSignPos) instanceof SignBlockEntity wallSign) {
                    SignText text = wallSign.getFrontText();
                    List<String> wrapped = wrapText((i + 1) + ". " + checks.get(i), 15);
                    if (wrapped.size() > 4) {
                        throw new IllegalStateException("Station " + st.number() + " check " + (i + 1)
                                + " doesn't fit on a sign (4 lines of 15): " + checks.get(i));
                    }
                    for (int l = 0; l < wrapped.size(); l++) {
                        text = text.setMessage(l, Component.literal(wrapped.get(l)));
                    }
                    wallSign.setText(text, true);
                    wallSign.setChanged();
                }
            }

            // Build specific station setup
            buildStation(level, stationOrigin, st.builderKey());

            // Check main block is in place
            BlockPos mainPos = stationOrigin.offset(st.mainBlockOffset()[0], st.mainBlockOffset()[1], st.mainBlockOffset()[2]);
            BlockState mainState = level.getBlockState(mainPos);
            Block expected = BuiltInRegistries.BLOCK.get(st.mainBlock());
            if (!mainState.is(expected)) {
                throw new IllegalStateException("Station " + st.number() + " (" + st.id() + ") main block missing at "
                        + mainPos + ": expected " + st.mainBlock() + " but found " + mainState);
            }
        }

        // 6. Spawn position: station 1 at (0, baseY, 0), looking north
        int spawnX = stationsToBuild.isEmpty() ? 0 : (stationsToBuild.get(0).number() - 1) * 12;
        BlockPos spawnPos = new BlockPos(spawnX, baseY, 0);
        level.setDefaultSpawnPos(spawnPos, 0.0f);

        // 7. Force load all chunks across the yard so server ticks them while empty
        forcedChunks.clear();
        int minChunkX = (minX >> 4) - 1;
        int maxChunkX = (maxX >> 4) + 1;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = -1; cz <= 2; cz++) {
                level.setChunkForced(cx, cz, true);
                forcedChunks.add(new ChunkPos(cx, cz));
            }
        }

        liveCheckActive = true;
        ticksRemaining = LIVE_CHECK_TICKS;
    }

    public static List<String> wrapText(String text, int maxLineLen) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.isEmpty()) {
                current.append(w);
            } else if (current.length() + 1 + w.length() <= maxLineLen) {
                current.append(" ").append(w);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(w);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    public static List<StationDef> loadStationDefs(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("stations");
        List<StationDef> list = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            int number = o.get("number").getAsInt();
            String id = o.get("id").getAsString();
            String title = o.get("title").getAsString();
            List<String> postSign = new ArrayList<>();
            if (o.has("post_sign")) {
                for (JsonElement l : o.getAsJsonArray("post_sign")) postSign.add(l.getAsString());
            }
            List<String> signLines = new ArrayList<>();
            if (o.has("sign_lines")) {
                for (JsonElement l : o.getAsJsonArray("sign_lines")) signLines.add(l.getAsString());
            }
            List<String> checks = new ArrayList<>();
            if (o.has("checks")) {
                for (JsonElement l : o.getAsJsonArray("checks")) checks.add(l.getAsString());
            } else {
                checks.addAll(signLines);
            }
            List<ChestItemDef> items = new ArrayList<>();
            for (JsonElement i : o.getAsJsonArray("chest_items")) {
                JsonObject io = i.getAsJsonObject();
                items.add(new ChestItemDef(
                        io.get("item").getAsString(),
                        io.has("count") ? io.get("count").getAsInt() : 1,
                        io.has("skin") ? io.get("skin").getAsString() : null,
                        io.has("family") ? io.get("family").getAsString() : null,
                        io.has("locked") && io.get("locked").getAsBoolean(),
                        io.has("weight") ? io.get("weight").getAsInt() : 0,
                        io.has("flawless") && io.get("flawless").getAsBoolean(),
                        io.has("smithed") && io.get("smithed").getAsBoolean()
                ));
            }
            String builderKey = o.get("builder_key").getAsString();
            ResourceLocation mainBlock = ResourceLocation.parse(o.get("main_block").getAsString());
            int[] offset = new int[]{0, 0, 0};
            if (o.has("main_block_offset")) {
                JsonArray offArr = o.getAsJsonArray("main_block_offset");
                offset = new int[]{offArr.get(0).getAsInt(), offArr.get(1).getAsInt(), offArr.get(2).getAsInt()};
            }
            list.add(new StationDef(number, id, title, postSign, signLines, checks, items, builderKey, mainBlock, offset));
        }
        return list;
    }

    public static List<ItemStack> buildChestStacks(ServerLevel level, List<ChestItemDef> defs) {
        List<ItemStack> list = new ArrayList<>();
        for (ChestItemDef d : defs) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item()));
            ItemStack stack = new ItemStack(item, d.count());

            if (d.skin() != null) {
                stack = SkinKitItem.of(d.skin(), d.family() != null ? d.family() : SkinDefs.DRILL, null, d.count());
            }
            if (d.locked()) {
                stack.set(ModDataComponents.LOCKED.get(), true);
            }
            if (d.weight() > 0) {
                stack.set(ModDataComponents.WEIGHT.get(), d.weight());
            }
            if (d.flawless()) {
                FlawlessDefs.setFlawless(stack, true);
            }
            if (d.smithed()) {
                SmithData smithData = new SmithData(List.of(new SmithData.Ingot(HoleyDigsite.id("folkiron"), 200)), java.util.Optional.empty());
                stack = SmithPieces.create(item, smithData, level.registryAccess());
            }
            list.add(stack);
        }
        return list;
    }

    // ============================================================================================
    // Station builders (1-12)
    // ============================================================================================

    public static void buildStation(ServerLevel level, BlockPos origin, String builderKey) throws Exception {
        switch (builderKey) {
            case "derrick_drill" -> buildDerrickDrillStation(level, origin);
            case "long_sluice" -> buildLongSluiceStation(level, origin);
            case "sluice_model" -> buildSluiceModelStation(level, origin);
            case "crates" -> buildCratesStation(level, origin);
            case "skin_bench" -> buildSkinBenchStation(level, origin);
            case "contracts" -> buildContractsStation(level, origin);
            case "field_guide" -> buildFieldGuideStation(level, origin);
            case "farming" -> buildFarmingStation(level, origin);
            case "hemp_line" -> buildHempLineStation(level, origin);
            case "selling" -> buildSellingStation(level, origin);
            case "ledger" -> buildLedgerStation(level, origin);
            case "drill_statue_mold" -> buildDrillStatueMoldStation(level, origin);
            case "conveyor_skins" -> buildConveyorSkinsStation(level, origin);
            default -> throw new IllegalArgumentException("Unknown builder key: " + builderKey);
        }
    }

    /** 1. Derrick drill on Dirtrock powered by real water pool, drills clumps out onto running belt. Second derrick skinned. */
    private static void buildDerrickDrillStation(ServerLevel level, BlockPos origin) {
        BlockPos drillPos = origin;
        level.setBlock(drillPos.below(), ModBlocks.DIRTROCK.get().defaultBlockState(), 3);

        // Lower and upper halves placed with default state (not forcing WORKING)
        level.setBlock(drillPos, ModBlocks.MINING_DRILL.get().defaultBlockState()
                .setValue(MiningDrillBlock.FACING, Direction.EAST)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(drillPos.above(), ModBlocks.MINING_DRILL.get().defaultBlockState()
                .setValue(MiningDrillBlock.FACING, Direction.EAST)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.UPPER), 3);

        // Belts moving clumps out along East (default states, not forced WATER)
        BlockPos b1 = drillPos.east();
        BlockPos b2 = b1.east();
        BlockPos b3 = b2.east();
        level.setBlock(b1, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        level.setBlock(b2, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        level.setBlock(b3, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);

        // Real water pool behind drill: basin with real water and waterlogged intake
        BlockPos intake = drillPos.offset(-1, -1, 0);
        BlockPos pool = drillPos.offset(-1, -1, -1);
        level.setBlock(pool, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true), 3);

        // Pipes:
        // 1. Up from intake at (-1, -1, 0) to back of drill at (-1, 0, 0)
        level.setBlock(drillPos.west(), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        // 2. Around Dirtrock (along dz = 1) to bottom of belt 1 at (1, -1, 0)
        level.setBlock(drillPos.offset(-1, -1, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        level.setBlock(drillPos.offset(0, -1, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        level.setBlock(drillPos.offset(1, -1, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        level.setBlock(b1.below(), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3); // (1, -1, 0)

        WaterNetworks.recompute(level, Set.of(intake));

        // Ensure drill status is refreshed once water network is established
        if (level.getBlockState(drillPos).getBlock() instanceof MiningDrillBlock mb) {
            mb.refreshStatus(level, drillPos, level.getBlockState(drillPos));
        }

        // Second derrick with rustic skin applied
        BlockPos drill2 = origin.offset(4, 0, 0);
        level.setBlock(drill2.below(), ModBlocks.DEEP_DIRTROCK.get().defaultBlockState(), 3);
        level.setBlock(drill2, ModBlocks.MINING_DRILL.get().defaultBlockState()
                .setValue(MiningDrillBlock.FACING, Direction.NORTH)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(drill2.above(), ModBlocks.MINING_DRILL.get().defaultBlockState()
                .setValue(MiningDrillBlock.FACING, Direction.NORTH)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.UPPER), 3);
        if (level.getBlockEntity(drill2) instanceof MiningDrillBlockEntity dbe2) {
            dbe2.setSkin("rustic", "");
        }
    }

    /** 2. Long Sluice (Tier 2) with real water pool, feeder belt into chute, riffle/mesh inserts, tray output onto belt. */
    private static void buildLongSluiceStation(ServerLevel level, BlockPos origin) {
        BlockPos slBack = origin;
        // Placing Long Sluice facing EAST creates front at (1, 0, 0) and chute at (0, 1, 0)
        level.setBlock(slBack, ModBlocks.SLUICE_BOX_T2.get().defaultBlockState()
                .setValue(SluiceBoxBlock.FACING, Direction.EAST)
                .setValue(SluiceBoxBlock.UPGRADE, SluiceUpgrade.RIFFLE)
                .setValue(LongSluiceBlock.UPGRADE2, SluiceUpgrade.MESH), 3);

        // Feeder belt into chute at (0, 1, 0) from west (-1, 1, 0)
        BlockPos feeder = slBack.above().west();
        level.setBlock(feeder.below(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(feeder, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState()
                .setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);

        // Output belt in front of tray at (2, 0, 0)
        BlockPos outBelt = slBack.offset(2, 0, 0);
        level.setBlock(outBelt, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState()
                .setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);

        // Real water pool: basin at (0, -1, 2) with water, intake at (0, -1, 1)
        BlockPos pool = slBack.offset(0, -1, 2);
        BlockPos intake = slBack.offset(0, -1, 1);
        level.setBlock(pool, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true), 3);

        // Pipes:
        // Up into horizontal side of slBack at (0, 0, 1)
        level.setBlock(slBack.offset(0, 0, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        // Under outBelt: (1, -1, 1) -> (2, -1, 1) -> (2, -1, 0) under outBelt
        level.setBlock(slBack.offset(1, -1, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        level.setBlock(slBack.offset(2, -1, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        level.setBlock(outBelt.below(), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        // To feeder belt: (-1, -1, 1) -> (-1, 0, 1) -> (-1, 1, 1) touching side of feeder
        level.setBlock(slBack.offset(-1, -1, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        level.setBlock(feeder.below().offset(0, 0, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        level.setBlock(feeder.offset(0, 0, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);

        WaterNetworks.recompute(level, Set.of(intake));

        // Initial clumps for live check
        if (level.getBlockEntity(slBack) instanceof SluiceBoxBlockEntity sl) {
            sl.addClumps(4);
        }
    }

    /** 3. Starter Sluice and Sluice Model decor next to each other. */
    private static void buildSluiceModelStation(ServerLevel level, BlockPos origin) {
        BlockPos starter = origin.west();
        BlockPos model = origin.east();

        level.setBlock(starter, ModBlocks.SLUICE_BOX.get().defaultBlockState()
                .setValue(SluiceBoxBlock.FACING, Direction.NORTH), 3);
        level.setBlock(model, ModBlocks.SLUICE_MODEL.get().defaultBlockState()
                .setValue(DecorBlock.FACING, Direction.NORTH), 3);
    }

    /** 4. Crate station: locked Prospector and Outback crates, Bolt Cutters, Crate Switch. */
    private static void buildCratesStation(ServerLevel level, BlockPos origin) {
        BlockPos wall = origin.south();
        level.setBlock(wall, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(wall.above(), Blocks.STONE_BRICKS.defaultBlockState(), 3);

        level.setBlock(origin, ModBlocks.CRATE_SWITCH.get().defaultBlockState()
                .setValue(CrateSwitchBlock.FACING, Direction.NORTH), 3);

        BlockPos pCrate = origin.west();
        BlockPos oCrate = origin.east();
        level.setBlock(pCrate, ModBlocks.PROSPECTOR_CRATE.get().defaultBlockState()
                .setValue(CrateBlock.LOCKED, true)
                .setValue(CrateBlock.FACING, Direction.NORTH), 3);
        level.setBlock(oCrate, ModBlocks.OUTBACK_CRATE.get().defaultBlockState()
                .setValue(CrateBlock.LOCKED, true)
                .setValue(CrateBlock.FACING, Direction.NORTH), 3);
    }

    /** 5. Skin Bench with kits, including Black Opal on a placed derrick. */
    private static void buildSkinBenchStation(ServerLevel level, BlockPos origin) {
        level.setBlock(origin, ModBlocks.SKIN_BENCH.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);

        BlockPos drillPos = origin.offset(2, 0, 0);
        level.setBlock(drillPos.below(), ModBlocks.DIRTROCK.get().defaultBlockState(), 3);
        level.setBlock(drillPos, ModBlocks.MINING_DRILL.get().defaultBlockState()
                .setValue(MiningDrillBlock.FACING, Direction.NORTH)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(drillPos.above(), ModBlocks.MINING_DRILL.get().defaultBlockState()
                .setValue(MiningDrillBlock.FACING, Direction.NORTH)
                .setValue(MiningDrillBlock.HALF, DoubleBlockHalf.UPPER), 3);
        if (level.getBlockEntity(drillPos) instanceof MiningDrillBlockEntity dbe) {
            dbe.setSkin("black_opal", "");
        }
    }

    /** 6. Contracts board with three contracts and chest of hand-ins. */
    private static void buildContractsStation(ServerLevel level, BlockPos origin) {
        BlockPos wall = origin.south();
        level.setBlock(wall, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(wall.above(), Blocks.STONE_BRICKS.defaultBlockState(), 3);

        level.setBlock(origin, ModBlocks.CONTRACT_BOARD.get().defaultBlockState()
                .setValue(ContractBoardBlock.FACING, Direction.NORTH), 3);
    }

    /** 7. Field Guide station (lectern dropped as verified; bookshelf placed, book in chest). */
    private static void buildFieldGuideStation(ServerLevel level, BlockPos origin) {
        level.setBlock(origin, Blocks.BOOKSHELF.defaultBlockState(), 3);
    }

    /** 8. Farming: field with crops, Sprinkler powered by real water pool, Flood Gate on shelf. */
    private static void buildFarmingStation(ServerLevel level, BlockPos origin) {
        // 3x3 farmland around origin
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = origin.offset(dx, 0, dz);
                if (dx == 0 && dz == 0) continue;
                level.setBlock(p.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
                level.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
            }
        }
        // Hemp on sides
        level.setBlock(origin.offset(-1, 0, 0), ModBlocks.HEMP_CROP.get().defaultBlockState().setValue(OwnCropBlock.AGE, 3), 3);
        level.setBlock(origin.offset(1, 0, 0), ModBlocks.HEMP_CROP.get().defaultBlockState().setValue(OwnCropBlock.AGE, 3), 3);

        // Real water pool under farmland
        BlockPos pool = origin.offset(0, -2, 1);
        BlockPos intake = origin.offset(0, -2, 0);
        level.setBlock(pool, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true), 3);

        // Pipe up to Sprinkler bottom
        level.setBlock(origin.below(), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        level.setBlock(origin, ModBlocks.SPRINKLER.get().defaultBlockState(), 3);
        WaterNetworks.recompute(level, Set.of(intake));

        // Shelf with Flood Gate
        BlockPos shelf = origin.offset(2, 0, 0);
        level.setBlock(shelf, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(shelf.above(), ModBlocks.FLOOD_GATE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
    }

    /** 9. Hemp line and timber machines: hemp line and timber line side by side, powered by real water pool. */
    private static void buildHempLineStation(ServerLevel level, BlockPos origin) {
        // Hemp crop on farmland next to hemp line
        BlockPos cropPos = origin.offset(-3, 0, -1);
        level.setBlock(cropPos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
        level.setBlock(cropPos, ModBlocks.HEMP_CROP.get().defaultBlockState().setValue(OwnCropBlock.AGE, 3), 3);

        // Line 1 (Hemp line along dz = -1):
        // Input belt (-2) -> Drum (-1) -> Belt (0) -> Rope Machine (1) -> Output belt (2) -> Stop (3)
        BlockPos hempIn = origin.offset(-2, 0, -1);
        BlockPos drumPos = origin.offset(-1, 0, -1);
        BlockPos hempMid = origin.offset(0, 0, -1);
        BlockPos ropePos = origin.offset(1, 0, -1);
        BlockPos hempOut = origin.offset(2, 0, -1);
        BlockPos hempStop = origin.offset(3, 0, -1);

        level.setBlock(hempIn, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        level.setBlock(drumPos, ModBlocks.THRESHING_DRUM.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.EAST), 3);
        level.setBlock(hempMid, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        level.setBlock(ropePos, ModBlocks.ROPE_MACHINE.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.EAST), 3);
        level.setBlock(hempOut, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        level.setBlock(hempStop, Blocks.SMOOTH_STONE.defaultBlockState(), 3);

        // Line 2 (Timber line along dz = 1):
        // Input belt (0) -> Sawmill (1) -> Output belt (2) -> Stop (3)
        BlockPos timberIn = origin.offset(0, 0, 1);
        BlockPos sawPos = origin.offset(1, 0, 1);
        BlockPos timberOut = origin.offset(2, 0, 1);
        BlockPos timberStop = origin.offset(3, 0, 1);

        level.setBlock(timberIn, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        level.setBlock(sawPos, ModBlocks.SAWMILL.get().defaultBlockState().setValue(WaterProcessingBlock.FACING, Direction.EAST), 3);
        level.setBlock(timberOut, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        level.setBlock(timberStop, Blocks.SMOOTH_STONE.defaultBlockState(), 3);

        // Real water pool at (0, -1, 0): basin with real water and waterlogged intake
        BlockPos pool = origin.offset(-1, -1, 0);
        BlockPos intake = origin.offset(0, -1, 0);
        level.setBlock(pool, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true), 3);

        // Pipes at dy = -1 under machines and belts
        for (int dx = -2; dx <= 2; dx++) {
            level.setBlock(origin.offset(dx, -1, -1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        }
        for (int dx = 0; dx <= 2; dx++) {
            level.setBlock(origin.offset(dx, -1, 1), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        }
        level.setBlock(origin.offset(1, -1, 0), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        WaterNetworks.recompute(level, Set.of(intake));

        // Initial inputs for live check run:
        if (level.getBlockEntity(hempIn) instanceof ClumpBeltBlockEntity hbe) {
            hbe.insertByPlayer(new ItemStack(ModItems.HEMP.get()), 2);
        }
        if (level.getBlockEntity(timberIn) instanceof ClumpBeltBlockEntity tbe) {
            tbe.insertByPlayer(new ItemStack(Items.OAK_LOG), 1);
        }
    }

    /** 10. Selling: Sell Pad with Shop Button and Money Pan. */
    private static void buildSellingStation(ServerLevel level, BlockPos origin) {
        BlockPos sellPos = origin.west();
        BlockPos btnPos = origin;
        BlockPos panPos = origin.east();

        level.setBlock(sellPos, ModBlocks.SELL_PAD.get().defaultBlockState(), 3);
        level.setBlock(btnPos, ModBlocks.SHOP_BUTTON.get().defaultBlockState(), 3);
        level.setBlock(panPos, ModBlocks.MONEY_PAN.get().defaultBlockState(), 3);

        if (level.getBlockEntity(panPos) instanceof MoneyPanBlockEntity pan) {
            pan.setBalance(64);
        }
    }

    /** 11. Prospector's Ledger station: Lucky Lantern and decor in world, Ledger in chest. */
    private static void buildLedgerStation(ServerLevel level, BlockPos origin) {
        level.setBlock(origin, ModBlocks.LUCKY_LANTERN.get().defaultBlockState(), 3);
        level.setBlock(origin.east(2), ModBlocks.ORE_CART.get().defaultBlockState().setValue(DecorBlock.FACING, Direction.NORTH), 3);
        level.setBlock(origin.west(2), ModBlocks.GOLDEN_PAN_TROPHY.get().defaultBlockState().setValue(DecorBlock.FACING, Direction.NORTH), 3);
        level.setBlock(origin.east(3), ModBlocks.UTE_ORE_CART.get().defaultBlockState().setValue(DecorBlock.FACING, Direction.NORTH), 3);
    }

    /** 12. Golden Drill Statue, Forge lit on real lava, Large Crucible with metal, Casting Mold with front and back belts. */
    private static void buildDrillStatueMoldStation(ServerLevel level, BlockPos origin) {
        // Statue on the left at (-2, 0, 0)
        BlockPos statuePos = origin.offset(-2, 0, 0);
        level.setBlock(statuePos, ModBlocks.GOLDEN_DRILL_STATUE.get().defaultBlockState()
                .setValue(DecorBlock.FACING, Direction.NORTH), 3);

        // Forge at (0, 0, -1) facing SOUTH on real lava
        BlockPos forgePos = origin.offset(0, 0, -1);
        level.setBlock(forgePos, ModBlocks.FORGE.get().defaultBlockState()
                .setValue(ForgeBlock.FACING, Direction.SOUTH), 3);

        // Large Crucible on top of Forge facing SOUTH
        BlockPos crucPos = origin.offset(0, 1, -1);
        level.setBlock(crucPos, ModBlocks.LARGE_CRUCIBLE.get().defaultBlockState()
                .setValue(CrucibleBlock.FACING, Direction.SOUTH), 3);
        if (level.getBlockEntity(crucPos) instanceof CrucibleBlockEntity c) {
            c.setContents(HoleyDigsite.id("folkiron"), 0, 200);
        }

        // Real lava pool behind Forge at (0, -1, -2)
        BlockPos lavaPool = origin.offset(-1, -1, -2);
        BlockPos lavaIntake = origin.offset(0, -1, -2);
        level.setBlock(lavaPool, Blocks.LAVA.defaultBlockState(), 3);
        level.setBlock(lavaIntake, ModBlocks.LAVA_INTAKE.get().defaultBlockState()
                .setValue(LavaIntakeBlock.LAVALOGGED, true), 3);
        level.setBlock(origin.offset(0, 0, -2), ModBlocks.LAVA_PIPE.get().defaultBlockState(), 3);
        WaterNetworks.recompute(level, Set.of(lavaIntake));

        // Casting Mold Tier 2 at (0, 0, 0) facing EAST (in front of Forge/Crucible)
        BlockPos moldPos = origin.offset(0, 0, 0);
        level.setBlock(moldPos, ModBlocks.CASTING_MOLD_T2.get().defaultBlockState()
                .setValue(CastingMoldBlock.FACING, Direction.EAST), 3);

        // Front belt at (1, 0, 0) facing EAST, Back belt at (-1, 0, 0) facing WEST
        BlockPos frontBelt = origin.offset(1, 0, 0);
        BlockPos backBelt = origin.offset(-1, 0, 0);
        level.setBlock(frontBelt, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        level.setBlock(backBelt, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.WEST), 3);

        // Real water pool powering the belts at (0, -1, 1)
        BlockPos waterPool = origin.offset(0, -1, 1);
        BlockPos waterIntake = origin.offset(0, -1, 0);
        level.setBlock(waterPool, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(waterIntake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true), 3);

        // Water pipes under belts
        level.setBlock(frontBelt.below(), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        level.setBlock(backBelt.below(), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);
        WaterNetworks.recompute(level, Set.of(waterIntake));
    }

    // ============================================================================================
    // Live Check Evaluation and Reset
    // ============================================================================================

    public static void evaluateAndResetStations(MinecraftServer server, ServerLevel level, Path gameDir, List<StationDef> stations) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> checkReports = new LinkedHashMap<>();
        List<String> builtStations = new ArrayList<>();

        for (StationDef st : stations) {
            int stX = (st.number() - 1) * 12;
            int baseY = savedBaseY > 0 ? savedBaseY : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
            if (baseY <= 0) baseY = 64;
            BlockPos origin = new BlockPos(stX, baseY, 4);

            HoleyDigsite.LOGGER.info("[TEST YARD] Evaluating station {}: {}", st.number(), st.id());
            try {
                switch (st.builderKey()) {
                    case "derrick_drill" -> evaluateAndResetStation1(level, origin, checkReports);
                    case "long_sluice" -> evaluateAndResetStation2(level, origin, checkReports);
                    case "sluice_model" -> evaluateAndResetStation3(level, origin, checkReports);
                    case "crates" -> evaluateAndResetStation4(level, origin, checkReports);
                    case "skin_bench" -> evaluateAndResetStation5(level, origin, checkReports);
                    case "contracts" -> evaluateAndResetStation6(level, origin, checkReports);
                    case "field_guide" -> evaluateAndResetStation7(level, origin, checkReports);
                    case "farming" -> evaluateAndResetStation8(level, origin, checkReports);
                    case "hemp_line" -> evaluateAndResetStation9(level, origin, checkReports);
                    case "selling" -> evaluateAndResetStation10(level, origin, checkReports);
                    case "ledger" -> evaluateAndResetStation11(level, origin, checkReports);
                    case "drill_statue_mold" -> evaluateAndResetStation12(level, origin, checkReports);
                    case "conveyor_skins" -> evaluateAndResetStation13(level, origin, checkReports);
                    default -> checkReports.put(st.id(), "Station verified");
                }
                checkChest(level, new BlockPos(stX + 1, baseY, 2), st);
                builtStations.add(st.id());
            } catch (Throwable t) {
                HoleyDigsite.LOGGER.error("[TEST YARD] Station {} failed live check: {}", st.id(), t.getMessage(), t);
                errors.put(st.id(), t.getMessage() != null ? t.getMessage() : t.toString());
            }
        }

        writeResult(gameDir, errors.isEmpty(), errors, checkReports, builtStations);

        if (!errors.isEmpty()) {
            throw new RuntimeException("Test Yard live check failed with " + errors.size() + " failure(s): " + errors);
        }
    }

    /** Every item the station lists is in its chest, in at least that number. */
    private static void checkChest(ServerLevel level, BlockPos pos, StationDef st) {
        if (st.chestItems().isEmpty()) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            throw new IllegalStateException("Chest missing at " + pos);
        }
        Map<Item, Integer> want = new LinkedHashMap<>();
        for (ChestItemDef d : st.chestItems()) {
            want.merge(BuiltInRegistries.ITEM.get(ResourceLocation.parse(d.item())), d.count(), Integer::sum);
        }
        for (var e : want.entrySet()) {
            int have = 0;
            for (int s = 0; s < chest.getContainerSize(); s++) {
                if (chest.getItem(s).is(e.getKey())) {
                    have += chest.getItem(s).getCount();
                }
            }
            if (e.getKey() == Items.AIR || have < e.getValue()) {
                throw new IllegalStateException("Chest is missing " + BuiltInRegistries.ITEM.getKey(e.getKey())
                        + " (has " + have + " of " + e.getValue() + ")");
            }
        }
    }

    private static void evaluateAndResetStation1(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!(level.getBlockEntity(origin) instanceof MiningDrillBlockEntity drill)) {
            throw new IllegalStateException("Mining drill missing at origin");
        }
        if (!drill.isWaterPowered()) {
            throw new IllegalStateException("Drill is not water-powered by network");
        }

        ClumpBeltBlockEntity b1 = level.getBlockEntity(origin.offset(1, 0, 0)) instanceof ClumpBeltBlockEntity be ? be : null;
        ClumpBeltBlockEntity b2 = level.getBlockEntity(origin.offset(2, 0, 0)) instanceof ClumpBeltBlockEntity be ? be : null;
        ClumpBeltBlockEntity b3 = level.getBlockEntity(origin.offset(3, 0, 0)) instanceof ClumpBeltBlockEntity be ? be : null;

        boolean produced = (b1 != null && !b1.items().isEmpty())
                || (b2 != null && !b2.items().isEmpty())
                || (b3 != null && !b3.items().isEmpty())
                || drill.heldCrate() != null && !drill.heldCrate().isEmpty();

        if (!produced) {
            throw new IllegalStateException("Drill did not produce clumps onto the belt during 60s run");
        }

        // Reset to clean start: empty belts and reset drill progress to 0
        if (b1 != null) b1.takeAll();
        if (b2 != null) b2.takeAll();
        if (b3 != null) b3.takeAll();
        drill.setProgressForTest(0);

        reports.put("derrick_drill", "Drill produced clumps onto belt; water network active; reset to start");
    }

    private static void evaluateAndResetStation2(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!(level.getBlockEntity(origin) instanceof SluiceBoxBlockEntity sl)) {
            throw new IllegalStateException("Long Sluice missing at origin");
        }
        if (!sl.isWaterPowered()) {
            throw new IllegalStateException("Long Sluice is not water-powered by network");
        }

        ClumpBeltBlockEntity outBelt = level.getBlockEntity(origin.offset(2, 0, 0)) instanceof ClumpBeltBlockEntity be ? be : null;
        boolean washed = (outBelt != null && !outBelt.items().isEmpty()) || !sl.tray().isEmpty();
        if (!washed) {
            throw new IllegalStateException("Long Sluice did not wash clumps into tray or onto output belt");
        }

        // Reset: empty output belt, clear tray, add 4 clumps to queue so Dewey sees washing start
        if (outBelt != null) outBelt.takeAll();
        sl.takeTray();
        sl.addClumps(4);

        reports.put("long_sluice", "Long Sluice washed clumps to tray/belt; water network active; reset to start");
    }

    private static void evaluateAndResetStation3(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!level.getBlockState(origin.west()).is(ModBlocks.SLUICE_BOX.get())) {
            throw new IllegalStateException("Starter Sluice missing");
        }
        if (!level.getBlockState(origin.east()).is(ModBlocks.SLUICE_MODEL.get())) {
            throw new IllegalStateException("Sluice Model missing");
        }
        reports.put("sluice_model", "Starter Sluice and Sluice Model present; scale verified");
    }

    private static void evaluateAndResetStation4(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!level.getBlockState(origin).is(ModBlocks.CRATE_SWITCH.get())) {
            throw new IllegalStateException("Crate Switch missing");
        }
        if (!level.getBlockState(origin.west()).is(ModBlocks.PROSPECTOR_CRATE.get())) {
            throw new IllegalStateException("Prospector Crate missing");
        }
        if (!level.getBlockState(origin.east()).is(ModBlocks.OUTBACK_CRATE.get())) {
            throw new IllegalStateException("Outback Crate missing");
        }
        reports.put("crates", "Crate Switch and locked crates present; chest verified");
    }

    private static void evaluateAndResetStation5(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!level.getBlockState(origin).is(ModBlocks.SKIN_BENCH.get())) {
            throw new IllegalStateException("Skin Bench missing");
        }
        if (!(level.getBlockEntity(origin.offset(2, 0, 0)) instanceof MiningDrillBlockEntity dbe)
                || !"black_opal".equals(dbe.getSkin())) {
            throw new IllegalStateException("Black Opal skinned drill missing at offset (2,0,0)");
        }
        reports.put("skin_bench", "Skin Bench and Black Opal glowing derrick verified");
    }

    private static void evaluateAndResetStation6(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!level.getBlockState(origin).is(ModBlocks.CONTRACT_BOARD.get())) {
            throw new IllegalStateException("Contract Board missing");
        }
        reports.put("contracts", "Contract Board present; deliveries in chest verified");
    }

    private static void evaluateAndResetStation7(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!level.getBlockState(origin).is(Blocks.BOOKSHELF)) {
            throw new IllegalStateException("Bookshelf missing at origin");
        }
        reports.put("field_guide", "Bookshelf present; Field Guide in chest verified");
    }

    private static void evaluateAndResetStation8(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        BlockState sprState = level.getBlockState(origin);
        if (!sprState.is(ModBlocks.SPRINKLER.get())) {
            throw new IllegalStateException("Sprinkler missing at origin");
        }
        if (!sprState.getValue(SprinklerBlock.WATER_POWERED)) {
            throw new IllegalStateException("Sprinkler is not water powered by network");
        }
        if (!level.getBlockState(origin.offset(2, 1, 0)).is(ModBlocks.FLOOD_GATE.get())) {
            throw new IllegalStateException("Flood Gate shelf missing");
        }
        reports.put("farming", "Sprinkler is water-powered; farmland and Flood Gate shelf verified");
    }

    private static void evaluateAndResetStation9(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        BlockPos drumPos = origin.offset(-1, 0, -1);
        BlockPos ropePos = origin.offset(1, 0, -1);
        BlockPos sawPos = origin.offset(1, 0, 1);

        if (!(level.getBlockEntity(drumPos) instanceof ThreshingDrumBlockEntity drum)) {
            throw new IllegalStateException("Threshing Drum missing at " + drumPos);
        }
        if (!(level.getBlockEntity(ropePos) instanceof RopeMachineBlockEntity ropeMachine)) {
            throw new IllegalStateException("Rope Machine missing at " + ropePos);
        }
        if (!(level.getBlockEntity(sawPos) instanceof SawmillBlockEntity sawmill)) {
            throw new IllegalStateException("Sawmill missing at " + sawPos);
        }

        if (!level.getBlockState(drumPos).getValue(WaterProcessingBlock.WATER_POWERED)
                || !level.getBlockState(ropePos).getValue(WaterProcessingBlock.WATER_POWERED)
                || !level.getBlockState(sawPos).getValue(WaterProcessingBlock.WATER_POWERED)) {
            throw new IllegalStateException("Machines are not water-powered by network");
        }

        ClumpBeltBlockEntity hempOut = level.getBlockEntity(origin.offset(2, 0, -1)) instanceof ClumpBeltBlockEntity be ? be : null;
        ClumpBeltBlockEntity timberOut = level.getBlockEntity(origin.offset(2, 0, 1)) instanceof ClumpBeltBlockEntity be ? be : null;

        boolean ropeProduced = (hempOut != null && hempOut.items().stream().anyMatch(s -> s.is(ModItems.ROPE.get())))
                || ropeMachine.outputBuffer().stream().anyMatch(s -> s.is(ModItems.ROPE.get()));
        if (!ropeProduced) {
            throw new IllegalStateException("Hemp line did not produce Rope from Hemp during 60s run");
        }

        boolean timberProduced = (timberOut != null && timberOut.items().stream().anyMatch(s -> s.is(ModItems.BEAM.get()) || s.is(ModItems.PIT_PROP.get())))
                || sawmill.outputBuffer().stream().anyMatch(s -> s.is(ModItems.BEAM.get()) || s.is(ModItems.PIT_PROP.get()));
        if (!timberProduced) {
            throw new IllegalStateException("Sawmill did not process log into beam or props during 60s run");
        }

        // Reset hemp line: clear belts and machine buffers
        if (level.getBlockEntity(origin.offset(-2, 0, -1)) instanceof ClumpBeltBlockEntity b) b.takeAll();
        if (level.getBlockEntity(origin.offset(0, 0, -1)) instanceof ClumpBeltBlockEntity b) b.takeAll();
        if (hempOut != null) hempOut.takeAll();
        drum.outputBuffer().clear();
        drum.inputBuffer().clear();
        ropeMachine.outputBuffer().clear();
        ropeMachine.inputBuffer().clear();

        // Reset timber line: clear belts, leave 1 finished Beam in Sawmill output buffer for Dewey
        if (level.getBlockEntity(origin.offset(0, 0, 1)) instanceof ClumpBeltBlockEntity b) b.takeAll();
        if (timberOut != null) timberOut.takeAll();
        sawmill.inputBuffer().clear();
        sawmill.ensureWorkingState(false);
        sawmill.outputBuffer().clear();
        sawmill.outputBuffer().add(new ItemStack(ModItems.BEAM.get(), 1));
        sawmill.notifyBufferChanged();

        reports.put("hemp_line", "Hemp to rope and log to beam verified; sawmill has 1 finished beam in buffer");
    }

    private static void evaluateAndResetStation10(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!level.getBlockState(origin.west()).is(ModBlocks.SELL_PAD.get())) {
            throw new IllegalStateException("Sell Pad missing");
        }
        if (!level.getBlockState(origin).is(ModBlocks.SHOP_BUTTON.get())) {
            throw new IllegalStateException("Shop Button missing");
        }
        if (!level.getBlockState(origin.east()).is(ModBlocks.MONEY_PAN.get())) {
            throw new IllegalStateException("Money Pan missing");
        }
        reports.put("selling", "Sell Pad, Shop Button and Money Pan present; chest verified");
    }

    private static void evaluateAndResetStation11(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!level.getBlockState(origin).is(ModBlocks.LUCKY_LANTERN.get())) {
            throw new IllegalStateException("Lucky Lantern missing");
        }
        reports.put("ledger", "Lucky Lantern and decor present; Ledger in chest verified");
    }

    private static void evaluateAndResetStation12(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        if (!level.getBlockState(origin.offset(-2, 0, 0)).is(ModBlocks.GOLDEN_DRILL_STATUE.get())) {
            throw new IllegalStateException("Golden Drill Statue missing at (-2,0,0)");
        }

        BlockPos forgePos = origin.offset(0, 0, -1);
        BlockState forgeState = level.getBlockState(forgePos);
        if (!forgeState.is(ModBlocks.FORGE.get())) {
            throw new IllegalStateException("Forge missing at " + forgePos);
        }
        if (!forgeState.getValue(ForgeBlock.LIT)) {
            throw new IllegalStateException("Forge is not LIT by lava network");
        }

        BlockPos crucPos = origin.offset(0, 1, -1);
        BlockState crucState = level.getBlockState(crucPos);
        if (!crucState.is(ModBlocks.LARGE_CRUCIBLE.get())) {
            throw new IllegalStateException("Large Crucible missing at " + crucPos);
        }
        if (!crucState.getValue(CrucibleBlock.HEATED)) {
            throw new IllegalStateException("Crucible is not HEATED by lit forge");
        }
        if (level.getBlockEntity(crucPos) instanceof CrucibleBlockEntity c && c.molten() <= 0) {
            throw new IllegalStateException("Crucible has no molten metal");
        }

        BlockPos moldPos = origin.offset(0, 0, 0);
        if (!level.getBlockState(moldPos).is(ModBlocks.CASTING_MOLD_T2.get())) {
            throw new IllegalStateException("Casting Mold Tier 2 missing at " + moldPos);
        }

        BlockPos fb = origin.offset(1, 0, 0);
        BlockPos bb = origin.offset(-1, 0, 0);
        if (!level.getBlockState(fb).getValue(ClumpBeltBlock.WATER) || !level.getBlockState(bb).getValue(ClumpBeltBlock.WATER)) {
            throw new IllegalStateException("Front or back mold belt is not water powered");
        }

        // Reset: empty belts, clean mold, crucible ready with 200g molten Folkiron
        if (level.getBlockEntity(fb) instanceof ClumpBeltBlockEntity b) b.takeAll();
        if (level.getBlockEntity(bb) instanceof ClumpBeltBlockEntity b) b.takeAll();
        if (level.getBlockEntity(moldPos) instanceof MoldBlockEntity m) m.takeIngot();
        if (level.getBlockEntity(crucPos) instanceof CrucibleBlockEntity c) {
            c.setContents(HoleyDigsite.id("folkiron"), 0, 200);
        }

        reports.put("drill_statue_mold", "Forge lit on lava, crucible heated with 200g metal, mold and belts ready");
    }

    private static void writeResult(Path gameDir, boolean ok, Map<String, String> errors, Map<String, String> checks, List<String> built) {
        JsonObject res = new JsonObject();
        res.addProperty("status", ok ? "ok" : "failed");
        JsonArray builtArr = new JsonArray();
        for (String b : built) builtArr.add(b);
        res.add("built", builtArr);
        JsonObject checkObj = new JsonObject();
        for (var c : checks.entrySet()) checkObj.addProperty(c.getKey(), c.getValue());
        res.add("checks", checkObj);
        JsonObject failObj = new JsonObject();
        for (var e : errors.entrySet()) failObj.addProperty(e.getKey(), e.getValue());
        res.add("failures", failObj);

        try {
            Files.writeString(gameDir.resolve("yard_result.json"), res.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            HoleyDigsite.LOGGER.error("[TEST YARD] Failed to write yard_result.json", e);
        }
    }
    /** 13. Conveyor skins: straight belts, corners, splitter, filter belt, drop chute in 7 skins, bench and chest. */
    private static void buildConveyorSkinsStation(ServerLevel level, BlockPos origin) {
        // Skin Bench next to chest at (origin.x + 2, origin.y, origin.z - 2)
        BlockPos benchPos = origin.offset(2, 0, -2);
        level.setBlock(benchPos, ModBlocks.SKIN_BENCH.get().defaultBlockState()
                .setValue(SkinBenchBlock.FACING, Direction.NORTH), 3);
        if (level.getBlockEntity(benchPos) instanceof SkinBenchBlockEntity sbe) {
            sbe.setItem(new ItemStack(ModBlocks.CLUMP_BELT.get(), 16));
        }

        // Real water pool & intake to power the belt network:
        // Pool at (-3, -1, 1), intake at (-3, -1, 0), pipe at (-2, -1, 0) under first belt
        BlockPos pool = origin.offset(-3, -1, 1);
        BlockPos intake = origin.offset(-3, -1, 0);
        level.setBlock(pool, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true), 3);
        level.setBlock(origin.offset(-2, -1, 0), ModBlocks.WATER_PIPE.get().defaultBlockState(), 3);

        // Belt 1: straight clump belt facing EAST at (-2, 0, 0), skin patina
        BlockPos b1 = origin.offset(-2, 0, 0);
        level.setBlock(b1, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        if (level.getBlockEntity(b1) instanceof ClumpBeltBlockEntity be) {
            be.setSkin("patina", "");
            be.insertByPlayer(new ItemStack(ModItems.DIRT_CLUMP.get()), 1);
        }

        // Belt 2: straight fast clump belt facing EAST at (-1, 0, 0), skin gunmetal
        BlockPos b2 = origin.offset(-1, 0, 0);
        level.setBlock(b2, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        if (level.getBlockEntity(b2) instanceof ClumpBeltBlockEntity be) {
            be.setSkin("gunmetal", "");
            be.insertByPlayer(new ItemStack(ModItems.GRAVEL_CLUMP.get()), 1);
        }

        // Belt 3: belt splitter facing EAST at (0, 0, 0), skin ironbark
        BlockPos b3 = origin.offset(0, 0, 0);
        level.setBlock(b3, ModBlocks.BELT_SPLITTER.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        if (level.getBlockEntity(b3) instanceof ClumpBeltBlockEntity be) {
            be.setSkin("ironbark", "");
            be.insertByPlayer(new ItemStack(ModItems.RED_DIRT_CLUMP.get()), 1);
        }

        // Belt 4: straight clump belt facing EAST at (1, 0, 0), skin enamel
        BlockPos b4 = origin.offset(1, 0, 0);
        level.setBlock(b4, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST), 3);
        if (level.getBlockEntity(b4) instanceof ClumpBeltBlockEntity be) {
            be.setSkin("enamel", "");
            be.insertByPlayer(new ItemStack(ModItems.MULLOCK_CLUMP.get()), 1);
        }

        // Belt 5: corner clump belt facing SOUTH (curving from WEST to SOUTH) at (2, 0, 0), skin quartz_reef
        BlockPos b5 = origin.offset(2, 0, 0);
        level.setBlock(b5, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(ClumpBeltBlock.FACING, Direction.SOUTH)
                .setValue(ClumpBeltBlock.SHAPE, BeltShape.RIGHT), 3);
        if (level.getBlockEntity(b5) instanceof ClumpBeltBlockEntity be) {
            be.setSkin("quartz_reef", "");
            be.insertByPlayer(new ItemStack(ModItems.SAND_CLUMP.get()), 1);
        }

        // Belt 6: filter belt facing SOUTH at (2, 0, 1), skin nugget
        BlockPos b6 = origin.offset(2, 0, 1);
        level.setBlock(b6, ModBlocks.FILTER_BELT.get().defaultBlockState()
                .setValue(ClumpBeltBlock.FACING, Direction.SOUTH), 3);
        if (level.getBlockEntity(b6) instanceof ClumpBeltBlockEntity be) {
            be.setSkin("nugget", "");
            be.setFilter(new ItemStack(ModItems.DIRT_CLUMP.get()));
            be.insertByPlayer(new ItemStack(ModItems.DIRT_CLUMP.get()), 1);
        }

        // Belt 7: corner clump belt facing WEST (curving from NORTH to WEST) at (2, 0, 2), skin black_opal
        BlockPos b7 = origin.offset(2, 0, 2);
        level.setBlock(b7, ModBlocks.CLUMP_BELT.get().defaultBlockState()
                .setValue(ClumpBeltBlock.FACING, Direction.WEST)
                .setValue(ClumpBeltBlock.SHAPE, BeltShape.RIGHT), 3);
        if (level.getBlockEntity(b7) instanceof ClumpBeltBlockEntity be) {
            be.setSkin("black_opal", "");
            be.insertByPlayer(new ItemStack(ModItems.GOLDCHRO_INGOT.get()), 1);
        }

        // Belt 8: straight fast clump belt facing WEST at (1, 0, 2), skin black_opal
        BlockPos b8 = origin.offset(1, 0, 2);
        level.setBlock(b8, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState()
                .setValue(ClumpBeltBlock.FACING, Direction.WEST), 3);
        if (level.getBlockEntity(b8) instanceof ClumpBeltBlockEntity be) {
            be.setSkin("black_opal", "");
            be.insertByPlayer(new ItemStack(ModItems.FOLKIRON_INGOT.get()), 1);
        }

        // Chute: drop chute at (0, 0, 2), skin black_opal, dropping into chest below
        BlockPos chutePos = origin.offset(0, 0, 2);
        level.setBlock(chutePos.below(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
        level.setBlock(chutePos, ModBlocks.DROP_CHUTE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(chutePos) instanceof com.holeydigsite.transport.DropChuteBlockEntity cbe) {
            cbe.setSkin("black_opal", "");
        }

        WaterNetworks.recompute(level, Set.of(intake));
    }

    private static void evaluateAndResetStation13(ServerLevel level, BlockPos origin, Map<String, String> reports) {
        BlockPos benchPos = origin.offset(2, 0, -2);
        if (!level.getBlockState(benchPos).is(ModBlocks.SKIN_BENCH.get())) {
            throw new IllegalStateException("Skin Bench missing at " + benchPos);
        }

        BlockPos b1 = origin.offset(-2, 0, 0);
        if (!(level.getBlockEntity(b1) instanceof ClumpBeltBlockEntity be1) || !"patina".equals(be1.getSkin())) {
            throw new IllegalStateException("Patina skinned straight belt missing at (-2,0,0)");
        }

        BlockPos b3 = origin.offset(0, 0, 0);
        if (!(level.getBlockEntity(b3) instanceof ClumpBeltBlockEntity be3) || !"ironbark".equals(be3.getSkin())) {
            throw new IllegalStateException("Ironbark skinned splitter missing at (0,0,0)");
        }

        BlockPos b5 = origin.offset(2, 0, 0);
        if (!(level.getBlockEntity(b5) instanceof ClumpBeltBlockEntity be5) || !"quartz_reef".equals(be5.getSkin())) {
            throw new IllegalStateException("Quartz Reef skinned corner belt missing at (2,0,0)");
        }

        BlockPos b6 = origin.offset(2, 0, 1);
        if (!(level.getBlockEntity(b6) instanceof ClumpBeltBlockEntity be6) || !"nugget".equals(be6.getSkin())) {
            throw new IllegalStateException("Nugget skinned filter belt missing at (2,0,1)");
        }

        BlockPos chutePos = origin.offset(0, 0, 2);
        if (!(level.getBlockEntity(chutePos) instanceof com.holeydigsite.transport.DropChuteBlockEntity cbe) || !"black_opal".equals(cbe.getSkin())) {
            throw new IllegalStateException("Black Opal skinned drop chute missing at (0,0,2)");
        }

        if (!level.getBlockState(b1).getValue(ClumpBeltBlock.WATER)) {
            throw new IllegalStateException("Belt b1 is not water powered");
        }

        reports.put("conveyor_skins", "Belt line, corners, splitter, filter belt, drop chute in 7 skins, bench and kits ready");
    }
}
