package com.holeydigsite.devtest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.client.LedgerScreen;
import com.holeydigsite.drill.DrillStatus;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.forge.CraftingForgeBlock;
import com.holeydigsite.forge.CraftingForgeBlockEntity;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.forge.MoldBlockEntity;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.ledger.LedgerNetwork;
import com.holeydigsite.ledger.ModAttachments;
import com.holeydigsite.power.LavaIntakeBlock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.site.Sites;
import com.holeydigsite.sluice.LongSluiceBlock;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import com.holeydigsite.sluice.SluiceStatus;
import com.holeydigsite.sluice.SluiceUpgrade;
import com.holeydigsite.tier.DirtTier;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Automated photo studio (dev-only, not shipped in the release jar).
 * Activated with {@code -Dholeydigsite.studio=true}. Reads {@code tools/shots/shots.json} (or {@code studio_run.json}
 * in the game directory), creates a clean void world, builds each scene, waits for chunk rendering and working
 * machines, captures stills or frame bursts, writes a summary, and quits.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID, value = Dist.CLIENT)
public final class PhotoStudio {
    private PhotoStudio() {}

    private static final boolean ON = Boolean.getBoolean("holeydigsite.studio");
    /** Shader runs wait longer after each camera move, the pack recompiles and the first frames are slow. */
    private static final int SHADER_EXTRA_TICKS = Boolean.getBoolean("holeydigsite.studio.shaders") ? 80 : 0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** {@code at} maps a frame index to an action that runs just before that frame is taken. */
    private record BurstSpec(int frames, int every, Map<Integer, String> at) {}

    private record ShotSpec(
            String id,
            String scene,
            double[] eye,
            double[] target,
            long time,
            int settle,
            int fov,
            List<int[]> sizes,
            BurstSpec burst,
            String action,
            boolean gui,
            String anchor,
            boolean allParticles
    ) {}

    private static final class SceneContext {
        final String name;
        final BlockPos origin;
        final List<BlockPos> networkAnchors = new ArrayList<>();
        final List<BlockPos> drills = new ArrayList<>();
        final List<BlockPos> belts = new ArrayList<>();
        final List<BlockPos> sluices = new ArrayList<>();
        final List<BlockPos> forges = new ArrayList<>();
        Runnable refeed = () -> {};
        Runnable keepAlive = () -> {};
        boolean requiresSitesReady;
        boolean ready;

        SceneContext(String name, BlockPos origin) {
            this.name = name;
            this.origin = origin;
        }
    }

    private enum Phase {
        BOOT,
        WAIT_WORLD,
        SHOT_PREPARE_SCENE,
        SHOT_WAIT_SCENE,
        SHOT_AIM,
        SHOT_SETTLE,
        SHOT_BURST,
        FINISH,
        DONE
    }

    private static Phase phase = Phase.BOOT;
    private static int titleWait;
    private static int tickWait;
    private static int settleElapsed;
    private static int burstIndex;
    private static int burstCooldown;
    private static boolean actionTriggered;
    private static volatile boolean serverTaskDone;
    private static volatile Throwable serverTaskError;
    private static volatile String lastMachineProblem = "starting";

    private static Path shotsFile;
    private static Path outDir;
    private static Path islandNbt;
    private static Set<String> onlyFilter = Set.of();

    private static final List<ShotSpec> shots = new ArrayList<>();
    private static int currentShotIndex = -1;
    private static ShotSpec currentShot;
    private static final Map<String, SceneContext> builtScenes = new HashMap<>();
    private static SceneContext activeScene;

    private static final List<String> succeeded = new ArrayList<>();
    private static final Map<String, String> failed = new LinkedHashMap<>();
    private static final Map<String, String> warnings = new LinkedHashMap<>();

    private static java.lang.reflect.Field focusField;

    /** Floating labels are dev-friendly but not wanted in pictures; point the focus at a block nothing labels. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hideLabels(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        try {
            if (focusField == null) {
                focusField = com.holeydigsite.client.FloatingText.class.getDeclaredField("focus");
                focusField.setAccessible(true);
            }
            focusField.set(null, new BlockPos(0, -200, 0));
        } catch (ReflectiveOperationException e) {
            HoleyDigsite.LOGGER.warn("STUDIO cannot hide labels: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ON || phase == Phase.DONE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        cleanClientState(mc);

        if (tickWait > 0) {
            tickWait--;
            return;
        }

        switch (phase) {
            case BOOT -> tickBoot(mc);
            case WAIT_WORLD -> tickWaitWorld(mc);
            case SHOT_PREPARE_SCENE -> tickPrepareScene(mc);
            case SHOT_WAIT_SCENE -> tickWaitScene(mc);
            case SHOT_AIM -> tickAim(mc);
            case SHOT_SETTLE -> tickSettle(mc);
            case SHOT_BURST -> tickBurst(mc);
            case FINISH -> finishAndStop(mc);
            default -> {}
        }
    }

    private static void cleanClientState(Minecraft mc) {
        if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().clearMessages(true);
        }
        if (mc.getToasts() != null) {
            mc.getToasts().clear();
        }
        if (mc.getTutorial() != null) {
            mc.getTutorial().stop();
        }
        if (currentShot != null) {
            mc.options.hideGui = !currentShot.gui;
            if (!currentShot.gui && mc.screen != null && phase != Phase.BOOT && phase != Phase.WAIT_WORLD) {
                mc.setScreen(null);
            }
        }
    }

    private static void configureWindowAndOptions(Minecraft mc, int width, int height) {
        mc.options.hideGui = true;
        mc.options.fov().set(70);
        mc.options.gamma().set(0.5);
        mc.options.guiScale().set(3);
        mc.options.renderDistance().set(12);
        mc.options.simulationDistance().set(12);
        mc.options.cloudStatus().set(CloudStatus.OFF);
        mc.options.particles().set(ParticleStatus.MINIMAL);
        mc.options.bobView().set(false);
        mc.options.entityShadows().set(false);
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.onboardAccessibility = false;

        long win = mc.getWindow().getWindow();
        if (win != 0L) {
            GLFW.glfwSetWindowAttrib(win, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowPos(win, 0, 0);
            GLFW.glfwSetWindowSize(win, width, height);
        }
        if (mc.getMainRenderTarget().width != width || mc.getMainRenderTarget().height != height) {
            mc.getMainRenderTarget().resize(width, height, Minecraft.ON_OSX);
        }
    }

    private static void loadConfigAndShots(Minecraft mc) throws IOException {
        Path gameDir = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        Path projectRoot = gameDir.getParent() != null ? gameDir.getParent() : gameDir;

        String shotsProp = System.getProperty("holeydigsite.studio.shots");
        String outProp = System.getProperty("holeydigsite.studio.out");
        String onlyProp = System.getProperty("holeydigsite.studio.only");
        String islandProp = System.getProperty("holeydigsite.studio.island");

        Path runJson = gameDir.resolve("studio_run.json");
        if (Files.isRegularFile(runJson)) {
            JsonObject cfg = JsonParser.parseString(Files.readString(runJson, StandardCharsets.UTF_8)).getAsJsonObject();
            if (shotsProp == null && cfg.has("shots")) shotsProp = cfg.get("shots").getAsString();
            if (outProp == null && cfg.has("out")) outProp = cfg.get("out").getAsString();
            if (onlyProp == null && cfg.has("only")) onlyProp = cfg.get("only").getAsString();
            if (islandProp == null && cfg.has("island")) islandProp = cfg.get("island").getAsString();
        }

        shotsFile = shotsProp != null && !shotsProp.isBlank()
                ? Path.of(shotsProp).toAbsolutePath().normalize()
                : projectRoot.resolve("tools").resolve("shots").resolve("shots.json");
        outDir = outProp != null && !outProp.isBlank()
                ? Path.of(outProp).toAbsolutePath().normalize()
                : gameDir.resolve("screenshots");
        islandNbt = islandProp != null && !islandProp.isBlank()
                ? Path.of(islandProp).toAbsolutePath().normalize()
                : projectRoot.resolve("..").resolve("Holey Digsite Pack")
                        .resolve("config").resolve("skyblockbuilder").resolve("templates")
                        .resolve("islands").resolve("holey_digsite.nbt").normalize();

        if (onlyProp != null && !onlyProp.isBlank()) {
            onlyFilter = Arrays.stream(onlyProp.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        } else {
            onlyFilter = Set.of();
        }

        Files.createDirectories(outDir);
        shots.clear();

        if (!Files.isRegularFile(shotsFile)) {
            throw new IOException("Shots file not found: " + shotsFile);
        }
        JsonElement root = JsonParser.parseString(Files.readString(shotsFile, StandardCharsets.UTF_8));
        JsonArray arr = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("shots");
        if (arr == null) {
            throw new IOException("No 'shots' array in " + shotsFile);
        }

        for (int idx = 0; idx < arr.size(); idx++) {
            JsonElement el = arr.get(idx);
            String fallbackId = "shot_" + idx;
            try {
                JsonObject obj = el.getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : fallbackId;
                if (!onlyFilter.isEmpty() && !onlyFilter.contains(id)) {
                    continue;
                }
                String scene = obj.get("scene").getAsString();
                double[] eye = readVec3(obj.getAsJsonArray("eye"));
                double[] target = readVec3(obj.getAsJsonArray("target"));
                long time = obj.has("time") ? obj.get("time").getAsLong() : 6000L;
                int settle = obj.has("settle") ? obj.get("settle").getAsInt() : 40;
                int fov = obj.has("fov") ? obj.get("fov").getAsInt() : 70;
                List<int[]> sizes = new ArrayList<>();
                if (obj.has("sizes") && obj.get("sizes").isJsonArray()) {
                    for (JsonElement sEl : obj.getAsJsonArray("sizes")) {
                        JsonArray pair = sEl.getAsJsonArray();
                        int w = pair.get(0).getAsInt();
                        int h = pair.get(1).getAsInt();
                        if (w <= 0 || h <= 0) {
                            throw new IllegalArgumentException("Invalid size " + w + "x" + h);
                        }
                        sizes.add(new int[]{w, h});
                    }
                }
                if (sizes.isEmpty()) {
                    sizes.add(new int[]{1920, 1080});
                }
                BurstSpec burst = null;
                if (obj.has("burst") && obj.get("burst").isJsonObject()) {
                    JsonObject b = obj.getAsJsonObject("burst");
                    int frames = b.get("frames").getAsInt();
                    int every = b.has("every") ? b.get("every").getAsInt() : 2;
                    if (frames <= 0 || every <= 0) {
                        throw new IllegalArgumentException("Invalid burst settings");
                    }
                    Map<Integer, String> at = new HashMap<>();
                    if (b.has("at") && b.get("at").isJsonObject()) {
                        for (var e : b.getAsJsonObject("at").entrySet()) {
                            at.put(Integer.parseInt(e.getKey()), e.getValue().getAsString());
                        }
                    }
                    burst = new BurstSpec(frames, every, at);
                }
                String action = obj.has("action") && !obj.get("action").isJsonNull() ? obj.get("action").getAsString() : null;
                boolean gui = obj.has("gui") && obj.get("gui").getAsBoolean();
                String anchor = obj.has("anchor") && !obj.get("anchor").isJsonNull() ? obj.get("anchor").getAsString() : null;
                boolean allParticles = obj.has("particles") && "all".equals(obj.get("particles").getAsString());
                shots.add(new ShotSpec(id, scene, eye, target, time, settle, fov, sizes, burst, action, gui, anchor, allParticles));
            } catch (Exception e) {
                String badId = fallbackId;
                try {
                    if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                        badId = el.getAsJsonObject().get("id").getAsString();
                    }
                } catch (Exception ignored) {}
                if (onlyFilter.isEmpty() || onlyFilter.contains(badId)) {
                    recordFailure(badId, e);
                }
            }
        }
    }

    private static double[] readVec3(JsonArray arr) {
        if (arr == null || arr.size() != 3) {
            throw new IllegalArgumentException("Expected 3-element coordinate array");
        }
        return new double[]{
                arr.get(0).getAsDouble(),
                arr.get(1).getAsDouble(),
                arr.get(2).getAsDouble()
        };
    }

    private static void tickBoot(Minecraft mc) {
        if (!(mc.screen instanceof TitleScreen) && (mc.screen == null || mc.level != null || ++titleWait <= 120)) {
            return;
        }
        configureWindowAndOptions(mc, 1920, 1080);
        try {
            loadConfigAndShots(mc);
        } catch (Exception e) {
            recordFailure("config", e);
            phase = Phase.FINISH;
            return;
        }
        if (shots.isEmpty()) {
            phase = Phase.FINISH;
            return;
        }

        LevelSettings settings = new LevelSettings(
                "studio", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT
        );
        mc.createWorldOpenFlows().createFreshLevel(
                "studio_" + System.currentTimeMillis(),
                settings,
                new WorldOptions(1L, false, false),
                reg -> {
                    WorldDimensions dims = reg.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
                    if (dims.overworld() instanceof FlatLevelSource flat) {
                        flat.settings().getLayersInfo().clear();
                        flat.settings().getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
                        flat.settings().updateLayers();
                    }
                    return dims;
                },
                mc.screen
        );
        phase = Phase.WAIT_WORLD;
    }

    private static void tickWaitWorld(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        serverTaskDone = false;
        serverTaskError = null;
        server.execute(() -> {
            try {
                ServerLevel level = server.overworld();
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                GameRules rules = level.getGameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
                rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, server);
                rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, server);
                rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
                level.setWeatherParameters(1_000_000, 0, false, false);
                level.setDayTime(6000L);

                LedgerData data = sp.getData(ModAttachments.LEDGER_DATA);
                data.setStarterGiven(true);
                data.setStarterBedGiven(true);
                data.setStarterFieldGuideGiven(true);
                data.setFieldGuideOpened(true);
                sp.getInventory().clearContent();
                sp.setInvisible(true);
                sp.setGameMode(GameType.SPECTATOR);
                sp.getAbilities().flying = true;
                sp.onUpdateAbilities();
            } catch (Throwable t) {
                serverTaskError = t;
            } finally {
                serverTaskDone = true;
            }
        });
        tickWait = 20;
        currentShotIndex = -1;
        advanceToNextShot();
    }

    private static void advanceToNextShot() {
        currentShotIndex++;
        if (currentShotIndex >= shots.size()) {
            currentShot = null;
            phase = Phase.FINISH;
            return;
        }
        currentShot = shots.get(currentShotIndex);
        phase = Phase.SHOT_PREPARE_SCENE;
    }

    private static void tickPrepareScene(Minecraft mc) {
        if (!serverTaskDone) {
            return;
        }
        ShotSpec shot = currentShot;
        if (shot == null) {
            phase = Phase.FINISH;
            return;
        }
        try {
            validateShot(shot);
        } catch (Exception e) {
            recordFailure(shot.id, e);
            advanceToNextShot();
            return;
        }

        String sceneKey = canonicalScene(shot.scene);
        SceneContext existing = builtScenes.get(sceneKey);
        if (existing != null) {
            activeScene = existing;
            phase = Phase.SHOT_AIM;
            return;
        }

        var server = mc.getSingleplayerServer();
        if (server == null) {
            recordFailure(shot.id, new IllegalStateException("Singleplayer server unavailable"));
            advanceToNextShot();
            return;
        }

        SceneContext ctx = new SceneContext(sceneKey, originForScene(sceneKey));
        activeScene = ctx;
        serverTaskDone = false;
        serverTaskError = null;
        settleElapsed = 0;

        server.execute(() -> {
            try {
                ServerLevel level = server.overworld();
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.teleportTo(level, ctx.origin.getX() + 8.0, ctx.origin.getY() + 14.0, ctx.origin.getZ() + 8.0, 0.0F, 25.0F);
                buildScene(level, sp, ctx);
                for (BlockPos anchor : ctx.networkAnchors) {
                    WaterNetworks.recompute(level, Set.of(anchor));
                }
                ctx.refeed.run();
                primeDrills(level, ctx);
                discardLooseItems(level, ctx.origin);
            } catch (Throwable t) {
                serverTaskError = t;
            } finally {
                serverTaskDone = true;
            }
        });
        phase = Phase.SHOT_WAIT_SCENE;
    }

    private static void tickWaitScene(Minecraft mc) {
        if (!serverTaskDone) {
            return;
        }
        if (serverTaskError != null) {
            recordFailure(currentShot.id, serverTaskError);
            advanceToNextShot();
            return;
        }
        settleElapsed++;
        var server = mc.getSingleplayerServer();
        ServerLevel level = server != null ? server.overworld() : null;
        if (level == null) {
            recordFailure(currentShot.id, new IllegalStateException("Server overworld missing"));
            advanceToNextShot();
            return;
        }

        if (activeScene.requiresSitesReady && !Sites.isIslandReady(level) && settleElapsed < 200) {
            return;
        }
        if (settleElapsed == 15 && activeScene.requiresSitesReady) {
            server.execute(() -> decorateBuiltIsland(level, activeScene));
            return;
        }
        if (settleElapsed < 25) {
            return;
        }
        builtScenes.put(activeScene.name, activeScene);
        activeScene.ready = true;
        phase = Phase.SHOT_AIM;
    }

    private static void tickAim(Minecraft mc) {
        ShotSpec shot = currentShot;
        var server = mc.getSingleplayerServer();
        if (shot == null || server == null || activeScene == null) {
            advanceToNextShot();
            return;
        }

        mc.options.fov().set(shot.fov);
        mc.options.particles().set(shot.allParticles ? ParticleStatus.ALL : ParticleStatus.MINIMAL);
        mc.options.hideGui = !shot.gui;
        if (!shot.gui && mc.screen != null) {
            mc.setScreen(null);
        }

        BlockPos o = activeScene.origin;
        if (shot.anchor != null) {
            BlockPos a = anchorPos(server.overworld(), shot.anchor);
            if (a == null) {
                recordFailure(shot.id, new IllegalStateException("Anchor not found: " + shot.anchor));
                advanceToNextShot();
                return;
            }
            HoleyDigsite.LOGGER.info("STUDIO anchor {} at {} ({})", shot.anchor, a.toShortString(), server.overworld().getBlockState(a));
            o = a;
        }
        double ex = o.getX() + shot.eye[0];
        double ey = o.getY() + shot.eye[1];
        double ez = o.getZ() + shot.eye[2];
        double tx = o.getX() + shot.target[0];
        double ty = o.getY() + shot.target[1];
        double tz = o.getZ() + shot.target[2];

        double dx = tx - ex;
        double dy = ty - ey;
        double dz = tz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        serverTaskDone = false;
        serverTaskError = null;
        actionTriggered = false;
        settleElapsed = 0;
        lastMachineProblem = "aiming";

        server.execute(() -> {
            try {
                ServerLevel level = server.overworld();
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.setGameMode(GameType.SPECTATOR);
                sp.setInvisible(true);
                sp.getInventory().clearContent();
                level.setDayTime(shot.time);
                level.setWeatherParameters(1_000_000, 0, false, false);
                for (BlockPos anchor : activeScene.networkAnchors) {
                    WaterNetworks.recompute(level, Set.of(anchor));
                }
                activeScene.refeed.run();
                primeDrills(level, activeScene);
                discardLooseItems(level, activeScene.origin);
                double feetY = ey - sp.getEyeHeight();
                sp.teleportTo(level, ex, feetY, ez, yaw, pitch);
            } catch (Throwable t) {
                serverTaskError = t;
            } finally {
                serverTaskDone = true;
            }
        });

        if (mc.player != null) {
            double feetY = ey - mc.player.getEyeHeight();
            mc.player.moveTo(ex, feetY, ez, yaw, pitch);
            mc.player.yHeadRot = yaw;
            mc.player.yHeadRotO = yaw;
            mc.player.yBodyRot = yaw;
            mc.player.yBodyRotO = yaw;
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
        }

        phase = Phase.SHOT_SETTLE;
    }

    private static void tickSettle(Minecraft mc) {
        if (!serverTaskDone) {
            return;
        }
        if (serverTaskError != null) {
            recordFailure(currentShot.id, serverTaskError);
            advanceToNextShot();
            return;
        }

        settleElapsed++;
        ShotSpec shot = currentShot;
        var server = mc.getSingleplayerServer();
        ServerLevel level = server != null ? server.overworld() : null;

        int minWait = Math.max(15, shot.settle) + SHADER_EXTRA_TICKS;
        if (!actionTriggered && shot.action != null && settleElapsed >= Math.max(5, minWait - 6)) {
            actionTriggered = true;
            runShotAction(mc, server, shot.action);
        }

        if (server != null) {
            server.execute(() -> {
                if (activeScene != null) {
                    if (settleElapsed % 20 == 0) {
                        activeScene.keepAlive.run();
                    }
                    lastMachineProblem = machineProblem(server.overworld(), activeScene);
                }
            });
        }

        boolean chunksReady = mc.levelRenderer != null
                && mc.levelRenderer.hasRenderedAllSections()
                && mc.levelRenderer.countRenderedSections() > 0;
        String problem = !shot.gui ? lastMachineProblem : null;
        boolean machinesReady = problem == null;

        if (settleElapsed < minWait || ((!chunksReady || !machinesReady) && settleElapsed < minWait + 240)) {
            return;
        }

        if (!machinesReady) {
            HoleyDigsite.LOGGER.warn("STUDIO machine readiness timed out on shot {} ({}), capturing anyway", shot.id, problem);
            warnings.put(shot.id, "machines not ready: " + problem);
        }

        if (shot.burst != null) {
            burstIndex = 0;
            burstCooldown = 0;
            phase = Phase.SHOT_BURST;
        } else {
            try {
                captureStills(mc, shot);
                succeeded.add(shot.id);
                HoleyDigsite.LOGGER.info("STUDIO OK {}", shot.id);
            } catch (Throwable t) {
                recordFailure(shot.id, t);
            }
            if (shot.gui && mc.screen != null) {
                mc.setScreen(null);
            }
            advanceToNextShot();
        }
    }

    private static void tickBurst(Minecraft mc) {
        ShotSpec shot = currentShot;
        if (shot == null || shot.burst == null) {
            advanceToNextShot();
            return;
        }
        if (burstCooldown > 0) {
            burstCooldown--;
            return;
        }
        String atAction = shot.burst.at.get(burstIndex);
        if (atAction != null) {
            runShotAction(mc, mc.getSingleplayerServer(), atAction);
        }
        try {
            Path burstDir = outDir.resolve(shot.id);
            Files.createDirectories(burstDir);
            Path frameFile = burstDir.resolve(String.format(Locale.ROOT, "frame_%03d.png", burstIndex));
            captureToFile(mc, frameFile, shot.sizes.get(0)[0], shot.sizes.get(0)[1]);
            burstIndex++;
            if (burstIndex >= shot.burst.frames) {
                succeeded.add(shot.id);
                HoleyDigsite.LOGGER.info("STUDIO OK {} ({} burst frames)", shot.id, shot.burst.frames);
                if (shot.gui && mc.screen != null) {
                    mc.setScreen(null);
                }
                advanceToNextShot();
            } else {
                burstCooldown = Math.max(0, shot.burst.every - 1);
            }
        } catch (Throwable t) {
            recordFailure(shot.id, t);
            advanceToNextShot();
        }
    }

    private static void captureStills(Minecraft mc, ShotSpec shot) throws IOException {
        for (int i = 0; i < shot.sizes.size(); i++) {
            int[] sz = shot.sizes.get(i);
            String fileName = i == 0
                    ? shot.id + ".png"
                    : String.format(Locale.ROOT, "%s_%dx%d.png", shot.id, sz[0], sz[1]);
            captureToFile(mc, outDir.resolve(fileName), sz[0], sz[1]);
        }
    }

    private static void captureToFile(Minecraft mc, Path file, int targetW, int targetH) throws IOException {
        Files.createDirectories(file.getParent());
        cleanClientState(mc);
        try (NativeImage raw = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            if (raw.getWidth() == targetW && raw.getHeight() == targetH) {
                raw.writeToFile(file);
            } else {
                try (NativeImage scaled = new NativeImage(targetW, targetH, false)) {
                    raw.resizeSubRectTo(0, 0, raw.getWidth(), raw.getHeight(), scaled);
                    scaled.writeToFile(file);
                }
            }
        }
    }

    private static void runShotAction(Minecraft mc, net.minecraft.server.MinecraftServer server, String action) {
        if ("pour".equals(action) && server != null && activeScene != null) {
            server.execute(() -> {
                ServerLevel level = server.overworld();
                BlockPos cPos = activeScene.origin.offset(3, 1, 1);
                BlockPos mPos = activeScene.origin.offset(3, 0, 2);
                if (level.getBlockEntity(cPos) instanceof CrucibleBlockEntity cru) {
                    cru.setContents(HoleyDigsite.id("goldchro"), 0, 160);
                }
                BlockState cs = level.getBlockState(cPos);
                if (cs.getBlock() instanceof CrucibleBlock) {
                    level.setBlock(cPos, cs.setValue(CrucibleBlock.TIPPING, true), 2);
                }
                if (level.getBlockEntity(mPos) instanceof MoldBlockEntity mold) {
                    mold.pour(HoleyDigsite.id("goldchro"), 100);
                }
            });
        } else if ("open_ledger".equals(action) && server != null) {
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                LedgerData data = sp.getData(ModAttachments.LEDGER_DATA);
                for (String mId : List.of("first_pan", "first_sale", "buy_drill", "buy_sluice", "first_line", "first_ingot")) {
                    if (LedgerDefs.milestone(HoleyDigsite.id(mId)) != null) {
                        LedgerManager.grantMilestone(sp, HoleyDigsite.id(mId));
                    }
                }
                int targetRenown = Math.max(data.renown(), LedgerDefs.curve().cumulativeForLevel(6));
                data.setRenown(targetRenown);
                LedgerManager.claim(sp, 1);
                LedgerManager.claim(sp, 2);
                LedgerNetwork.syncToClient(sp);
            });
            mc.execute(() -> mc.setScreen(new LedgerScreen()));
        } else if ("refeed".equals(action) && server != null && activeScene != null) {
            server.execute(() -> activeScene.refeed.run());
        } else if (server != null && activeScene != null) {
            server.execute(() -> {
                try {
                    clipAction(server.overworld(), server.getPlayerList().getPlayers().get(0), action);
                } catch (Throwable t) {
                    HoleyDigsite.LOGGER.warn("STUDIO action {} failed: {}", action, t.toString());
                    warnings.put(currentShot != null ? currentShot.id : action, "action " + action + " failed: " + t);
                }
            });
        }
    }

    /** Trading Post blocks in the island template, relative to the island scene origin. */
    private static final BlockPos TP_SELL = new BlockPos(25, 11, 10);
    private static final BlockPos TP_BUY = new BlockPos(27, 11, 10);
    private static final BlockPos TP_PAN = new BlockPos(26, 11, 9);
    private static final BlockPos TP_BUTTON = new BlockPos(26, 11, 10);

    /** Server-side steps for the Field Guide clips. The "_setup" ones put a scene back to the first frame of its loop. */
    private static void clipAction(ServerLevel level, ServerPlayer sp, String action) {
        BlockPos o = activeScene.origin;
        switch (action) {
            case "sell_setup", "buy_setup" -> {
                PadBlockEntity sellPad = com.holeydigsite.shop.AbstractPadBlock.pad(level, o.offset(TP_SELL));
                PadBlockEntity buyPad = com.holeydigsite.shop.AbstractPadBlock.pad(level, o.offset(TP_BUY));
                MoneyPanBlockEntity pan = com.holeydigsite.shop.MoneyPanBlock.pan(level, o.offset(TP_PAN));
                if (sellPad == null || buyPad == null || pan == null) {
                    throw new IllegalStateException("Trading Post pads or pan missing");
                }
                sellPad.takeAll();
                buyPad.takeAll();
                if (action.startsWith("sell")) {
                    sellPad.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 4));
                    sellPad.insert(new ItemStack(ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), 3));
                    sellPad.insert(new ItemStack(ModItems.RUBY_LARGE.get(), 2));
                    sellPad.insert(new ItemStack(ModItems.SAPPHIRE_MEDIUM.get(), 2));
                    pan.setBalance(0);
                } else {
                    ItemStack bp = BlueprintItem.of(ModItems.MINING_DRILL.get(), 1);
                    long price = com.holeydigsite.shop.ShopCatalogue.forBlueprint(level.registryAccess(), bp)
                            .map(e -> e.price()).orElse(64L);
                    buyPad.insert(bp);
                    // a big pile before the press, a smaller one after
                    pan.setBalance(price + 5L * com.holeydigsite.economy.Selling.GOLD);
                }
            }
            case "press" -> {
                sp.getInventory().clearContent();
                com.holeydigsite.shop.ShopButtonBlock.press(level, o.offset(TP_BUTTON), sp);
                sp.getInventory().clearContent();
            }
            case "pour_setup" -> {
                BlockPos cPos = o.offset(3, 1, 1);
                BlockPos mPos = o.offset(3, 0, 2);
                if (level.getBlockEntity(mPos) instanceof MoldBlockEntity mold) {
                    mold.takeIngot();
                }
                if (level.getBlockEntity(cPos) instanceof CrucibleBlockEntity cru) {
                    cru.setContents(HoleyDigsite.id("goldchro"), 40, 160);
                }
                BlockState cs = level.getBlockState(cPos);
                if (cs.getBlock() instanceof CrucibleBlock && cs.getValue(CrucibleBlock.TIPPING)) {
                    level.setBlock(cPos, cs.setValue(CrucibleBlock.TIPPING, false), 2);
                }
            }
            case "pour_now" -> {
                BlockPos cPos = o.offset(3, 1, 1);
                if (CrucibleBlock.pour(level, cPos, level.getBlockState(cPos), null) <= 0) {
                    throw new IllegalStateException("crucible did not pour");
                }
            }
            case "plot_reset", "plot_claim" -> {
                boolean own = action.equals("plot_claim");
                var data = com.holeydigsite.plot.PlotData.get(level);
                for (var p : List.copyOf(data.all())) {
                    if (!p.id().equals(CLIP_PLOT)) {
                        continue;
                    }
                    data.setOwned(p.id(), p.board(), own, own ? sp.getGameProfile().getName() : "");
                    BlockState bs = level.getBlockState(p.board());
                    if (bs.getBlock() instanceof com.holeydigsite.plot.PlotBoardBlock) {
                        level.setBlock(p.board(), bs.setValue(com.holeydigsite.plot.PlotBoardBlock.OWNED, own), Block.UPDATE_CLIENTS);
                    }
                    if (own) {
                        // the same burst a real claim makes
                        BlockPos b = p.board();
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5, 30, 1.5, 0.8, 1.5, 0.1);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                                b.getX() + 0.5, b.getY() + 1.2, b.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.3);
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
    }

    private static final String CLIP_PLOT = "deep_dig";

    /** "plot:id" is that plot's board; eye and target are then relative to it. */
    private static BlockPos anchorPos(ServerLevel level, String anchor) {
        if (anchor.startsWith("plot:")) {
            String id = anchor.substring(5);
            for (var p : List.copyOf(com.holeydigsite.plot.PlotData.get(level).all())) {
                if (p.id().equals(id)) {
                    return p.board();
                }
            }
        }
        return null;
    }

    private static void validateShot(ShotSpec shot) {
        if (shot.id == null || shot.id.isBlank()) {
            throw new IllegalArgumentException("Shot id is blank");
        }
        if (shot.scene == null || shot.scene.isBlank()) {
            throw new IllegalArgumentException("Shot scene is blank");
        }
        if (shot.eye == null || shot.eye.length != 3 || shot.target == null || shot.target.length != 3) {
            throw new IllegalArgumentException("Shot eye/target must have 3 coordinates");
        }
        String canonical = canonicalScene(shot.scene);
        if (!Set.of("first_line", "island", "forge", "long_sluice", "derrick_skins", "outback").contains(canonical) && !"belts".equals(canonical)) {
            throw new IllegalArgumentException("Unknown scene: " + shot.scene);
        }
    }

    private static String canonicalScene(String scene) {
        if ("island".equals(scene) || "outback".equals(scene)) {
            return "island";
        }
        return scene;
    }

    private static BlockPos originForScene(String sceneKey) {
        return switch (sceneKey) {
            case "island" -> new BlockPos(0, 90, 0);
            case "first_line" -> new BlockPos(400, 90, 0);
            case "forge" -> new BlockPos(800, 90, 0);
            case "long_sluice" -> new BlockPos(1200, 90, 0);
            case "derrick_skins" -> new BlockPos(1600, 90, 0);
            case "belts" -> new BlockPos(2000, 90, 0);
            default -> new BlockPos(2000, 90, 0);
        };
    }

    /** A drill takes 15 s to make its first clump, so start each one close to done. */
    private static void primeDrills(ServerLevel level, SceneContext ctx) {
        for (BlockPos dPos : ctx.drills) {
            if (level.getBlockEntity(dPos) instanceof MiningDrillBlockEntity d) {
                d.setProgressForTest(Math.max(0, MiningDrillBlock.cycleTicks(level.getBlockState(dPos)) - 30));
            }
        }
    }

    /** Returns null when every tracked machine is running, otherwise a short reason. */
    private static String machineProblem(ServerLevel level, SceneContext ctx) {
        if (ctx == null) {
            return null;
        }
        for (BlockPos dPos : ctx.drills) {
            BlockState st = level.getBlockState(dPos);
            if (!(st.getBlock() instanceof MiningDrillBlock)) {
                return "drill " + dPos.toShortString() + " is " + st.getBlock();
            }
            if (st.getValue(MiningDrillBlock.STATUS) != DrillStatus.WORKING) {
                return "drill " + dPos.toShortString() + " status " + st.getValue(MiningDrillBlock.STATUS);
            }
        }
        boolean carrying = false;
        for (BlockPos bPos : ctx.belts) {
            BlockState st = level.getBlockState(bPos);
            if (!(st.getBlock() instanceof ClumpBeltBlock)) {
                return "belt " + bPos.toShortString() + " is " + st.getBlock();
            }
            if (!st.getValue(ClumpBeltBlock.WATER)) {
                return "belt " + bPos.toShortString() + " has no water";
            }
            if (level.getBlockEntity(bPos) instanceof ClumpBeltBlockEntity be && !be.isEmpty()) {
                carrying = true;
            }
        }
        if (!ctx.belts.isEmpty() && !carrying) {
            return "no belt is carrying anything";
        }
        for (BlockPos sPos : ctx.sluices) {
            BlockState st = level.getBlockState(sPos);
            if (!(st.getBlock() instanceof SluiceBoxBlock)) {
                return "sluice " + sPos.toShortString() + " is " + st.getBlock();
            }
            if (st.getValue(SluiceBoxBlock.STATUS) == SluiceStatus.NO_POWER) {
                return "sluice " + sPos.toShortString() + " has no water";
            }
        }
        for (BlockPos fPos : ctx.forges) {
            BlockState st = level.getBlockState(fPos);
            if (st.hasProperty(ForgeBlock.LIT) && !st.getValue(ForgeBlock.LIT)) {
                return "forge " + fPos.toShortString() + " not lit";
            }
            if (st.hasProperty(CraftingForgeBlock.LIT) && !st.getValue(CraftingForgeBlock.LIT)) {
                return "crafting forge " + fPos.toShortString() + " not lit";
            }
        }
        return null;
    }
    private static void discardLooseItems(ServerLevel level, BlockPos center) {
        AABB box = new AABB(center).inflate(180.0);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> !e.getTags().contains("studio_keep"))) {
            item.discard();
        }
    }

    private static void recordFailure(String id, Throwable t) {
        String msg = t.getClass().getSimpleName() + ": " + (t.getMessage() != null ? t.getMessage() : "failed");
        failed.put(id, msg);
        HoleyDigsite.LOGGER.error("STUDIO FAIL {} - {}", id, msg, t);
    }

    private static void finishAndStop(Minecraft mc) {
        phase = Phase.DONE;
        JsonObject summary = new JsonObject();
        JsonArray okArr = new JsonArray();
        for (String s : succeeded) { okArr.add(s); }
        JsonObject failObj = new JsonObject();
        for (var e : failed.entrySet()) failObj.addProperty(e.getKey(), e.getValue());
        summary.add("succeeded", okArr);
        summary.add("failed", failObj);
        JsonObject warnObj = new JsonObject();
        for (var e : warnings.entrySet()) warnObj.addProperty(e.getKey(), e.getValue());
        summary.add("warnings", warnObj);
        if (outDir != null) {
            try {
                Files.createDirectories(outDir);
                Files.writeString(outDir.resolve("studio_result.json"), GSON.toJson(summary), StandardCharsets.UTF_8);
            } catch (IOException e) {
                HoleyDigsite.LOGGER.error("Failed to write studio_result.json", e);
            }
        }
        HoleyDigsite.LOGGER.info("STUDIO SUMMARY: {} succeeded, {} with warnings {}, {} failed{}",
                succeeded.size(), warnings.size(), warnings.keySet(), failed.size(),
                failed.isEmpty() ? "" : " (" + String.join(", ", failed.keySet()) + ")");
        startShutdownWatchdog();
        mc.stop();
    }

    /** The integrated server can spin forever in its unload loop while saving the throwaway world; give it a while, then halt. */
    private static void startShutdownWatchdog() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(30_000L);
            } catch (InterruptedException e) {
                return;
            }
            for (var e : Thread.getAllStackTraces().entrySet()) {
                if (e.getKey().getName().equals("Server thread")) {
                    StringBuilder sb = new StringBuilder();
                    for (StackTraceElement el : e.getValue()) sb.append("\n    at ").append(el);
                    HoleyDigsite.LOGGER.warn("STUDIO shutdown stuck, server thread:{}", sb);
                }
            }
            HoleyDigsite.LOGGER.warn("STUDIO shutdown did not finish in 30 s, halting");
            Runtime.getRuntime().halt(0);
        }, "studio-shutdown-watchdog");
        t.setDaemon(true);
        t.start();
    }

    // ============================================================================================
    // Scene builders
    // ============================================================================================

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, 3);
    }

    private static void fix(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (!s.isAir()) {
            l.setBlock(p, Block.updateFromNeighbourShapes(s, l, p), 3);
        }
    }

    private static void wetUnderBelt(ServerLevel l, BlockPos beltPos, SceneContext ctx) {
        BlockPos intakePos = beltPos.below();
        set(l, intakePos, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = intakePos.relative(d);
            if (l.getBlockState(side).isAir()) {
                set(l, side, Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        if (l.getBlockState(intakePos.below()).isAir()) {
            set(l, intakePos.below(), Blocks.STONE_BRICKS.defaultBlockState());
        }
        ctx.networkAnchors.add(intakePos);
    }

    private static void buildScene(ServerLevel l, ServerPlayer sp, SceneContext ctx) throws Exception {
        switch (ctx.name) {
            case "first_line" -> buildFirstLineScene(l, sp, ctx);
            case "island" -> buildIslandScene(l, sp, ctx);
            case "forge" -> buildForgeScene(l, sp, ctx);
            case "long_sluice" -> buildLongSluiceScene(l, sp, ctx);
            case "derrick_skins" -> buildDerrickSkinsScene(l, sp, ctx);
            case "belts" -> buildBeltsScene(l, sp, ctx);
            default -> throw new IllegalArgumentException("Unsupported scene: " + ctx.name);
        }
    }

    /**
     * Hero scene: a lush floating skyblock island with a water pool, oak tree, mossy boulders, lanterns,
     * an excavated Dirtrock node pit, and a working first automated line
     * (Water Intake in pond -> pipes -> Derrick Drill on exposed Dirtrock node -> Clump Belts -> Sluice Box).
     */
    private static void buildFirstLineScene(ServerLevel l, ServerPlayer sp, SceneContext ctx) {
        BlockPos o = ctx.origin;

        // 1. Floating island base and terrain
        for (int x = -5; x <= 12; x++) {
            for (int z = -4; z <= 9; z++) {
                double dist = Math.hypot((x - 3.5) / 8.5, (z - 2.5) / 6.5);
                if (dist > 1.0) continue;

                // Tapered rocky island underside hanging into the void
                int depth = (int) Math.round((1.0 - dist) * 6.0) + 1;
                for (int dy = -depth - 1; dy <= -2; dy++) {
                    Block under = (x + z + dy) % 3 == 0 ? Blocks.ANDESITE : (x * z + dy) % 2 == 0 ? Blocks.STONE : Blocks.COBBLESTONE;
                    set(l, o.offset(x, dy, z), under.defaultBlockState());
                }

                // Terrain elevation:
                // z >= 4 forms a grassy background hill rising to y=0 and y=1
                int surfaceY = (z >= 6 && x >= -1 && x <= 7) ? 1 : (z >= 4 && x >= -2 && x <= 8) ? 0 : -1;

                // Top blocks
                for (int y = -1; y <= surfaceY; y++) {
                    Block b = (y == surfaceY)
                            ? ((x + z) % 5 == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK)
                            : Blocks.DIRT;
                    set(l, o.offset(x, y, z), b.defaultBlockState());
                }
            }
        }

        // 2. Clear out the excavated Dirtrock node pit around the drill (x=1..3, z=0..2)
        // Solid exposed Dirtrock node at y=-1 and y=-2
        for (int x = 1; x <= 3; x++) {
            for (int z = 0; z <= 2; z++) {
                set(l, o.offset(x, -2, z), ModBlocks.DIRTROCK.get().defaultBlockState());
                set(l, o.offset(x, -1, z), ModBlocks.DIRTROCK.get().defaultBlockState());
                set(l, o.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            }
        }
        // Pit retaining walls / quarry rim
        set(l, o.offset(1, -1, -1), Blocks.COBBLESTONE.defaultBlockState());
        set(l, o.offset(2, -1, -1), Blocks.COBBLESTONE.defaultBlockState());
        set(l, o.offset(3, -1, -1), Blocks.COBBLESTONE.defaultBlockState());
        set(l, o.offset(4, -1, 0), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, o.offset(4, -1, 2), Blocks.STONE_BRICKS.defaultBlockState());

        // 3. Natural Water Pool on the west side (x=-1..0, z=-1..1)
        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 1; z++) {
                set(l, o.offset(x, -3, z), Blocks.STONE.defaultBlockState());
                set(l, o.offset(x, -2, z), Blocks.GRAVEL.defaultBlockState());
                set(l, o.offset(x, -1, z), Blocks.WATER.defaultBlockState());
                set(l, o.offset(x, 0, z), Blocks.AIR.defaultBlockState());
            }
        }
        // Pool stone rim
        set(l, o.offset(-2, -1, -1), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        set(l, o.offset(-2, -1, 0), Blocks.STONE.defaultBlockState());
        set(l, o.offset(-2, -1, 1), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        set(l, o.offset(-1, -1, -2), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        set(l, o.offset(0, -1, -2), Blocks.STONE.defaultBlockState());
        set(l, o.offset(-1, -1, 2), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, o.offset(0, -1, 2), Blocks.MOSSY_COBBLESTONE.defaultBlockState());

        // Pan on the stone edge of the pool, clearly visible
        set(l, o.offset(0, -1, -1), Blocks.SMOOTH_STONE.defaultBlockState());
        set(l, o.offset(0, 0, -1), ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 3));

        // Submerged Water Intake at (0, -1, 0)
        BlockPos intake = o.offset(0, -1, 0);
        set(l, intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        ctx.networkAnchors.add(intake);

        // Water pipe rising from intake and feeding machines
        set(l, o.offset(0, 0, 0), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(1, 0, 0), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(1, 0, 1), ModBlocks.WATER_PIPE.get().defaultBlockState()); // into back of drill
        // Branch along z=2 to feed Sluice Box south port at (5, 0, 2)
        for (int x = 1; x <= 5; x++) {
            set(l, o.offset(x, 0, 2), ModBlocks.WATER_PIPE.get().defaultBlockState());
        }

        // 4. Mining Drill standing on Dirtrock at (2, 0, 1) facing EAST
        BlockPos drillPos = o.offset(2, 0, 1);
        set(l, drillPos, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        ctx.drills.add(drillPos);

        // 5. Clump Belts at (3, 0, 1) and (4, 0, 1) facing EAST
        BlockPos belt1 = o.offset(3, 0, 1);
        BlockPos belt2 = o.offset(4, 0, 1);
        wetUnderBelt(l, belt1, ctx);
        set(l, belt1, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        set(l, belt2, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        ctx.belts.add(belt1);
        ctx.belts.add(belt2);

        // 6. Sluice Box at (5, 0, 1) facing EAST (tray points east, unobscured)
        BlockPos sluicePos = o.offset(5, 0, 1);
        set(l, o.offset(5, -1, 1), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, sluicePos, ModBlocks.SLUICE_BOX.get().defaultBlockState()
                .setValue(SluiceBoxBlock.FACING, Direction.EAST)
                .setValue(SluiceBoxBlock.UPGRADE, SluiceUpgrade.RIFFLE));
        ctx.sluices.add(sluicePos);

        // Catch pan & decor behind/below sluice (leave tray face clear)
        set(l, o.offset(6, -1, 1), Blocks.COBBLESTONE.defaultBlockState());
        set(l, o.offset(6, -1, 2), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, o.offset(6, 0, 2), Blocks.BARREL.defaultBlockState());

        // 7. Background Hill, Oak Tree, and Lush Backdrop
        // Oak Tree at (1, 1, 6) with full spreading canopy
        BlockPos treeBase = o.offset(1, 1, 6);
        for (int ty = 0; ty < 5; ty++) set(l, treeBase.above(ty), Blocks.OAK_LOG.defaultBlockState());
        for (int dx = -4; dx <= 5; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 2; dy <= 4; dy++) {
                    if (Math.abs(dx) >= 4 && Math.abs(dz) >= 3) continue;
                    if (dx == 0 && dz == 0 && dy < 4) continue;
                    set(l, treeBase.offset(dx, dy, dz), Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(l, treeBase.offset(dx, 5, dz), Blocks.OAK_LEAVES.defaultBlockState());
            }
        }

        // Hillside bushes and greenery
        set(l, o.offset(4, 1, 3), Blocks.OAK_LEAVES.defaultBlockState());
        set(l, o.offset(-1, 0, 3), Blocks.OAK_LEAVES.defaultBlockState());
        set(l, o.offset(0, 1, 4), Blocks.OAK_LEAVES.defaultBlockState());

        // Lantern on spruce fence atop the background ridge
        set(l, o.offset(5, 1, 4), Blocks.SPRUCE_FENCE.defaultBlockState());
        set(l, o.offset(5, 2, 4), Blocks.SPRUCE_FENCE.defaultBlockState());
        set(l, o.offset(5, 3, 4), Blocks.LANTERN.defaultBlockState());

        // Wildflowers along the ridge
        set(l, o.offset(3, 1, 4), Blocks.POPPY.defaultBlockState());
        set(l, o.offset(2, 1, 4), Blocks.DANDELION.defaultBlockState());
        set(l, o.offset(4, 1, 4), Blocks.CORNFLOWER.defaultBlockState());
        set(l, o.offset(-1, 0, 4), Blocks.FERN.defaultBlockState());

        // Foreground decor post at the island corner
        set(l, o.offset(7, 0, -1), Blocks.SPRUCE_FENCE.defaultBlockState());
        set(l, o.offset(7, 1, -1), Blocks.LANTERN.defaultBlockState());

        for (BlockPos p : BlockPos.betweenClosed(o.offset(-5, -4, -4), o.offset(12, 9, 9))) {
            fix(l, p.immutable());
        }

        ctx.keepAlive = () -> {
            if (l.getBlockEntity(belt1) instanceof ClumpBeltBlockEntity b1 && b1.clumpCount() < 2) {
                b1.insertByPlayer(1, 1);
            }
            if (l.getBlockEntity(belt2) instanceof ClumpBeltBlockEntity b2 && b2.isEmpty()) {
                b2.insertByPlayer(1, 1);
            }
        };

        ctx.refeed = () -> {
            if (l.getBlockEntity(belt1) instanceof ClumpBeltBlockEntity b1) {
                b1.takeAll();
                b1.insertByPlayer(2, 1);
            }
            if (l.getBlockEntity(belt2) instanceof ClumpBeltBlockEntity b2) {
                b2.takeAll();
                b2.insertByPlayer(2, 1);
            }
            if (l.getBlockEntity(sluicePos) instanceof SluiceBoxBlockEntity sl) {
                sl.setOwnerPlayer(sp);
                sl.addClumps(4);
                sl.addToTray(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 2));
                sl.addToTray(new ItemStack(ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), 2));
                sl.addToTray(new ItemStack(ModItems.UNCUT_RUBY_MEDIUM.get(), 1));
                ModBlocks.SLUICE_BOX.get().refreshStatus(l, sluicePos, l.getBlockState(sluicePos));
            }
        };
    }

    /**
     * Full skyblock island template + Trading Post + Land Plots + Outback Event Island.
     */
    private static void buildIslandScene(ServerLevel l, ServerPlayer sp, SceneContext ctx) throws Exception {
        BlockPos t = ctx.origin;
        ctx.requiresSitesReady = true;
        if (islandNbt != null && Files.isRegularFile(islandNbt)) {
            var tag = NbtIo.readCompressed(islandNbt, NbtAccounter.unlimitedHeap());
            var template = l.getStructureManager().readStructure(tag);
            template.placeInWorld(l, t, t, new StructurePlaceSettings(), l.random, 2);
        }
        ctx.refeed = () -> decorateBuiltIsland(l, ctx);
    }

    private static void decorateBuiltIsland(ServerLevel l, SceneContext ctx) {
        BlockPos t = ctx.origin;
        // 1. Populate Trading Post checkout (Sell Pad, Buy Pad, Money Pan)
        BlockPos sellPos = t.offset(25, 11, 10);
        BlockPos buyPos = t.offset(27, 11, 10);
        BlockPos panPos = t.offset(26, 11, 9);
        if (l.getBlockEntity(sellPos) instanceof PadBlockEntity sell && sell.isEmpty()) {
            sell.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 4));
            sell.insert(new ItemStack(ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), 3));
            sell.insert(new ItemStack(ModItems.RUBY_LARGE.get(), 2));
            sell.insert(new ItemStack(ModItems.SAPPHIRE_MEDIUM.get(), 2));
        }
        if (l.getBlockEntity(buyPos) instanceof PadBlockEntity buy && buy.isEmpty()) {
            buy.insert(BlueprintItem.of(ModItems.MINING_DRILL.get(), 1));
            buy.insert(BlueprintItem.of(ModItems.SLUICE_BOX.get(), 1));
        }
        if (l.getBlockEntity(panPos) instanceof MoneyPanBlockEntity mp) {
            mp.setBalance(2 * 4096 + 6 * 64 + 18);
        }

        // Lanterns on the barrels beside the checkout so the pads read at thumbnail size
        for (int bx : new int[] {24, 28}) {
            BlockPos lp = t.offset(bx, 12, 10);
            if (l.getBlockState(lp.below()).is(Blocks.BARREL) && l.getBlockState(lp).isAir()) {
                set(l, lp, Blocks.LANTERN.defaultBlockState());
            }
        }

        // 2. Build a working line on the home island pool (x=4..9, y=10, z=16) for the island overview
        set(l, t.offset(6, 9, 16), ModBlocks.DIRTROCK.get().defaultBlockState());
        BlockPos homeIntake = t.offset(5, 9, 16);
        set(l, homeIntake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        set(l, t.offset(5, 10, 16), ModBlocks.WATER_PIPE.get().defaultBlockState());
        for (int x = 5; x <= 9; x++) {
            set(l, t.offset(x, 10, 15), ModBlocks.WATER_PIPE.get().defaultBlockState());
        }
        set(l, t.offset(6, 10, 16), ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        for (int x = 7; x <= 8; x++) {
            set(l, t.offset(x, 10, 16), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        }
        BlockPos homeSluice = t.offset(9, 10, 16);
        set(l, homeSluice, ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(SluiceBoxBlock.FACING, Direction.EAST));
        WaterNetworks.recompute(l, Set.of(homeIntake));
        if (l.getBlockEntity(homeSluice) instanceof SluiceBoxBlockEntity sl) {
            sl.addClumps(4);
        }
        for (int x = 7; x <= 8; x++) {
            if (l.getBlockEntity(t.offset(x, 10, 16)) instanceof ClumpBeltBlockEntity b) {
                b.takeAll();
                b.insertByPlayer(2, 1);
            }
        }

        // 3. Remove the Event Board on the Outback island so "Opal Chips" is never visible in frame,
        //    and uncover a Red Dirtrock pocket with a working Outback-skinned drill in Section 1.
        BlockPos eventBoard = t.offset(12, 11, -8);
        if (l.getBlockState(eventBoard).is(ModBlocks.EVENT_BOARD.get())) {
            set(l, eventBoard, Blocks.AIR.defaultBlockState());
        }
        // Uncover a small Red Dirtrock pit on the Outback island at (8..11, 9, -28..-25)
        for (int x = 8; x <= 11; x++) {
            for (int z = -28; z <= -25; z++) {
                set(l, t.offset(x, 9, z), ModBlocks.RED_DIRT_ROCK.get().defaultBlockState());
                set(l, t.offset(x, 10, z), Blocks.AIR.defaultBlockState());
                set(l, t.offset(x, 11, z), Blocks.AIR.defaultBlockState());
            }
        }
        BlockPos outbackDrill = t.offset(9, 10, -27);
        set(l, outbackDrill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.SOUTH));
        if (l.getBlockEntity(outbackDrill) instanceof MiningDrillBlockEntity dbe) {
            dbe.setSkin("outback_ochre", "");
        }
        set(l, t.offset(9, 10, -26), ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 3));
        BlockPos outbackIntake = t.offset(9, 9, -28);
        set(l, outbackIntake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        set(l, t.offset(9, 10, -28), ModBlocks.WATER_PIPE.get().defaultBlockState());
        WaterNetworks.recompute(l, Set.of(outbackIntake));

        for (BlockPos p : BlockPos.betweenClosed(t.offset(4, 9, 14), t.offset(10, 12, 17))) {
            fix(l, p.immutable());
        }
        for (BlockPos p : BlockPos.betweenClosed(t.offset(8, 9, -29), t.offset(11, 12, -25))) {
            fix(l, p.immutable());
        }
        discardLooseItems(l, t);
    }

    /**
     * Forge & casting scene: stone-brick smithy workshop with lava pipes feeding lit Forges and a Crafting Forge,
     * Crucibles filled with molten Goldchro and Folkiron, and a tipping Crucible pouring into a Casting Mold.
     */
    private static void buildForgeScene(ServerLevel l, ServerPlayer sp, SceneContext ctx) {
        BlockPos o = ctx.origin;
        for (int x = -2; x <= 10; x++) {
            for (int z = -2; z <= 6; z++) {
                set(l, o.offset(x, -1, z), (x + z) % 3 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                if (z == -2 || x == -2) {
                    for (int y = 0; y <= 3; y++) {
                        Block wall = y == 0 ? Blocks.DEEPSLATE_BRICKS : Blocks.STONE_BRICKS;
                        set(l, o.offset(x, y, z), wall.defaultBlockState());
                    }
                }
            }
        }
        // Corner pillars & warm lanterns (standard vanilla lanterns only)
        for (int y = 0; y <= 3; y++) {
            set(l, o.offset(-2, y, -2), Blocks.DARK_OAK_LOG.defaultBlockState());
            set(l, o.offset(4, y, -2), Blocks.DARK_OAK_LOG.defaultBlockState());
            set(l, o.offset(10, y, -2), Blocks.DARK_OAK_LOG.defaultBlockState());
        }
        set(l, o.offset(1, 3, -1), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        set(l, o.offset(7, 3, -1), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));

        // Lava intake at (8, 0, 0) walled with stone brick, feeding lava pipe along z=0
        BlockPos lavaIntake = o.offset(8, 0, 0);
        set(l, o.offset(9, 0, 0), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, o.offset(8, 0, -1), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, o.offset(8, 0, 1), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, lavaIntake, ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(LavaIntakeBlock.LAVALOGGED, true));
        for (int x = 1; x <= 7; x++) {
            set(l, o.offset(x, 0, 0), ModBlocks.LAVA_PIPE.get().defaultBlockState());
        }
        ctx.networkAnchors.add(lavaIntake);

        // Two Forges at (1, 0, 1) and (3, 0, 1) + Crafting Forge at (6, 0, 1), all facing SOUTH
        BlockState forgeState = ModBlocks.FORGE.get().defaultBlockState().setValue(ForgeBlock.FACING, Direction.SOUTH);
        BlockPos forge1 = o.offset(1, 0, 1);
        BlockPos forge2 = o.offset(3, 0, 1);
        BlockPos cForge = o.offset(6, 0, 1);
        set(l, forge1, forgeState);
        set(l, forge2, forgeState);
        set(l, cForge, ModBlocks.CRAFTING_FORGE.get().defaultBlockState().setValue(CraftingForgeBlock.FACING, Direction.SOUTH));
        ctx.forges.add(forge1);
        ctx.forges.add(forge2);
        ctx.forges.add(cForge);

        // Crucibles on top of the Forges + Molds in front at z=2
        set(l, forge1.above(), ModBlocks.LARGE_CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.FACING, Direction.SOUTH));
        set(l, forge1.south(), ModBlocks.MOLD.get().defaultBlockState());
        set(l, forge2.above(), ModBlocks.CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.FACING, Direction.SOUTH));
        set(l, forge2.south(), ModBlocks.MOLD.get().defaultBlockState());

        // Smithing Anvil & Tool Rack on the left side for atmosphere
        set(l, o.offset(-1, 0, 3), ModBlocks.SMITHING_ANVIL.get().defaultBlockState()
                .setValue(com.holeydigsite.smith.SmithingAnvilBlock.FACING, Direction.EAST));

        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, -2), o.offset(10, 4, 6))) {
            fix(l, p.immutable());
        }

        ctx.refeed = () -> {
            if (l.getBlockEntity(forge1.above()) instanceof CrucibleBlockEntity c1) {
                c1.setContents(HoleyDigsite.id("folkiron"), 24, 240);
            }
            if (l.getBlockEntity(forge1.south()) instanceof MoldBlockEntity m1) {
                m1.pour(HoleyDigsite.id("folkiron"), 80);
            }
            if (l.getBlockEntity(forge2.above()) instanceof CrucibleBlockEntity c2) {
                c2.setContents(HoleyDigsite.id("goldchro"), 0, 180);
            }
            if (l.getBlockEntity(forge2.south()) instanceof MoldBlockEntity m2) {
                m2.pour(HoleyDigsite.id("goldchro"), 100);
            }
            if (l.getBlockEntity(cForge) instanceof CraftingForgeBlockEntity cf) {
                cf.load(IngotItem.of(ModItems.FOLKIRON_INGOT.get(), 400), 1, false);
                cf.load(IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), 50), 1, false);
                cf.load(new ItemStack(ModItems.MINING_DRILL.get()), 1, false);
            }
        };
    }

    /**
     * Long Sluice in use: Tier 2 Mining Drill on Deep Dirtrock -> Clump Belt -> Incline Belt -> elevated Clump Belt
     * feeding into the top chute of a Long Sluice (fitted with Riffle Insert + Fine Mesh Screen), washing into its tray
     * and feeding a belt -> Gem Cutter -> Sell Pad.
     */
    private static void buildLongSluiceScene(ServerLevel l, ServerPlayer sp, SceneContext ctx) {
        BlockPos o = ctx.origin;
        // Floating island with a stone brick working strip under the line
        for (int x = -5; x <= 14; x++) {
            for (int z = -5; z <= 9; z++) {
                double dist = Math.hypot((x - 4.5) / 10.5, (z - 2.0) / 7.5);
                if (dist > 1.0) continue;
                int depth = (int) Math.round((1.0 - dist) * 6.0) + 1;
                for (int dy = -depth - 2; dy <= -3; dy++) {
                    Block under = (x + z + dy) % 3 == 0 ? Blocks.ANDESITE : (x * z + dy) % 2 == 0 ? Blocks.STONE : Blocks.COBBLESTONE;
                    set(l, o.offset(x, dy, z), under.defaultBlockState());
                }
                set(l, o.offset(x, -2, z), Blocks.DIRT.defaultBlockState());
                boolean strip = z >= -1 && z <= 3 && x >= -1 && x <= 11;
                Block top = strip ? ((x + z) % 4 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS)
                        : ((x + z) % 5 == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK);
                set(l, o.offset(x, -1, z), top.defaultBlockState());
                if (z >= 6 && x >= 0 && x <= 12) {
                    set(l, o.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
                    set(l, o.offset(x, -1, z), Blocks.DIRT.defaultBlockState());
                }
            }
        }
        BlockPos tree = o.offset(10, 1, 7);
        for (int ty = 0; ty < 5; ty++) set(l, tree.above(ty), Blocks.OAK_LOG.defaultBlockState());
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 2; dy <= 4; dy++) {
                    if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                    if (dx == 0 && dz == 0 && dy < 4) continue;
                    set(l, tree.offset(dx, dy, dz), Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
        }
        set(l, o.offset(-3, 0, 2), Blocks.POPPY.defaultBlockState());
        set(l, o.offset(12, 0, -3), Blocks.DANDELION.defaultBlockState());
        set(l, o.offset(13, 0, 0), Blocks.CORNFLOWER.defaultBlockState());
        set(l, o.offset(-3, 0, -2), Blocks.FERN.defaultBlockState());
        set(l, o.offset(12, 0, 4), Blocks.SPRUCE_FENCE.defaultBlockState());
        set(l, o.offset(12, 1, 4), Blocks.LANTERN.defaultBlockState());
        // Water pool behind the line at x=0..1, z=-1..0
        set(l, o.offset(0, -1, 0), Blocks.WATER.defaultBlockState());
        BlockPos intake = o.offset(1, -1, 0);
        set(l, intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        for (int x = 1; x <= 5; x++) {
            set(l, o.offset(x, 0, 0), ModBlocks.WATER_PIPE.get().defaultBlockState());
        }
        set(l, o.offset(0, 0, 0), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(0, 0, 1), ModBlocks.WATER_PIPE.get().defaultBlockState());
        ctx.networkAnchors.add(intake);

        // Tier 2 Drill on Deep Dirtrock at (1, 0, 1) facing EAST
        set(l, o.offset(1, -1, 1), ModBlocks.DEEP_DIRTROCK.get().defaultBlockState());
        BlockPos drill = o.offset(1, 0, 1);
        set(l, drill, ModBlocks.MINING_DRILL_T2.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        ctx.drills.add(drill);

        // Belt at (2, 0, 1) -> Incline Belt at (3, 1, 1) -> Upper Belt at (4, 1, 1) feeding into Long Sluice chute at (5, 1, 1)
        BlockPos bLow = o.offset(2, 0, 1);
        BlockPos bInc = o.offset(3, 1, 1);
        BlockPos bTop = o.offset(4, 1, 1);
        wetUnderBelt(l, bLow, ctx);
        set(l, bLow, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        set(l, o.offset(3, 0, 1), Blocks.SMOOTH_STONE.defaultBlockState());
        set(l, bInc, ModBlocks.FAST_INCLINE_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        set(l, o.offset(4, 0, 1), Blocks.SMOOTH_STONE.defaultBlockState());
        set(l, bTop, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        ctx.belts.add(bLow);
        ctx.belts.add(bInc);
        ctx.belts.add(bTop);

        // Long Sluice: back at (5, 0, 1), front at (6, 0, 1), chute at (5, 1, 1), facing EAST, Riffle + Mesh inserts
        BlockPos slBack = o.offset(5, 0, 1);
        set(l, slBack, ModBlocks.SLUICE_BOX_T2.get().defaultBlockState()
                .setValue(SluiceBoxBlock.FACING, Direction.EAST)
                .setValue(SluiceBoxBlock.UPGRADE, SluiceUpgrade.RIFFLE)
                .setValue(LongSluiceBlock.UPGRADE2, SluiceUpgrade.MESH));
        ctx.sluices.add(slBack);

        // Output belt at (7, 0, 1) -> Gem Cutter at (8, 0, 1) -> Belt at (9, 0, 1) -> Sell Pad at (10, 0, 1)
        BlockPos bOut1 = o.offset(7, 0, 1);
        BlockPos cutter = o.offset(8, 0, 1);
        BlockPos bOut2 = o.offset(9, 0, 1);
        wetUnderBelt(l, bOut1, ctx);
        set(l, bOut1, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        set(l, cutter, ModBlocks.GEM_CUTTER.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        set(l, bOut2, ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        set(l, o.offset(10, 0, 1), ModBlocks.SELL_PAD.get().defaultBlockState());
        ctx.belts.add(bOut1);
        ctx.belts.add(bOut2);

        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -2, -2), o.offset(11, 4, 5))) {
            fix(l, p.immutable());
        }

        ctx.keepAlive = () -> {
            if (l.getBlockEntity(bLow) instanceof ClumpBeltBlockEntity b && b.clumpCount() < 2) {
                b.insertByPlayer(1, 2);
            }
            if (l.getBlockEntity(bTop) instanceof ClumpBeltBlockEntity b && b.isEmpty()) {
                b.insertByPlayer(1, 2);
            }
            if (l.getBlockEntity(bOut1) instanceof ClumpBeltBlockEntity b && b.clumpCount() < 2) {
                b.insertByPlayer(new ItemStack(ModItems.UNCUT_RUBY_LARGE.get()), 1);
            }
            if (l.getBlockEntity(cutter) instanceof ClumpBeltBlockEntity b && b.isEmpty()) {
                b.insertByPlayer(new ItemStack(ModItems.UNCUT_SAPPHIRE_LARGE.get()), 1);
            }
            if (l.getBlockEntity(bOut2) instanceof ClumpBeltBlockEntity b && b.isEmpty()) {
                b.insertByPlayer(new ItemStack(ModItems.RUBY_LARGE.get()), 1);
            }
            if (l.getBlockEntity(o.offset(10, 0, 1)) instanceof PadBlockEntity pad && pad.contents().size() >= 4) {
                pad.takeAll();
            }
        };

        ctx.refeed = () -> {
            if (l.getBlockEntity(bLow) instanceof ClumpBeltBlockEntity b) {
                b.takeAll();
                b.insertByPlayer(2, 2);
            }
            if (l.getBlockEntity(bInc) instanceof ClumpBeltBlockEntity b) {
                b.takeAll();
                b.insertByPlayer(2, 2);
            }
            if (l.getBlockEntity(bTop) instanceof ClumpBeltBlockEntity b) {
                b.takeAll();
                b.insertByPlayer(2, 2);
            }
            if (l.getBlockEntity(slBack) instanceof SluiceBoxBlockEntity sl) {
                sl.setOwnerPlayer(sp);
                sl.addClumps(8);
                sl.addToTray(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 3));
                sl.addToTray(new ItemStack(ModItems.GOLDCHRO_CHUNK_LARGE.get(), 2));
                sl.addToTray(new ItemStack(ModItems.UNCUT_SAPPHIRE_LARGE.get(), 1));
                ModBlocks.SLUICE_BOX_T2.get().refreshStatus(l, slBack, l.getBlockState(slBack));
            }
            if (l.getBlockEntity(bOut1) instanceof ClumpBeltBlockEntity b) {
                b.takeAll();
                b.insertByPlayer(new ItemStack(ModItems.UNCUT_RUBY_LARGE.get()), 2);
            }
            if (l.getBlockEntity(cutter) instanceof ClumpBeltBlockEntity b) {
                b.takeAll();
                b.insertByPlayer(new ItemStack(ModItems.UNCUT_SAPPHIRE_LARGE.get()), 1);
            }
            if (l.getBlockEntity(bOut2) instanceof ClumpBeltBlockEntity b) {
                b.takeAll();
                b.insertByPlayer(new ItemStack(ModItems.RUBY_LARGE.get()), 2);
            }
        };
    }

    /**
     * A row of 6 working Derrick Drills showing distinct skins, with Black Opal prominently featured among them.
     */
    private static void buildDerrickSkinsScene(ServerLevel l, ServerPlayer sp, SceneContext ctx) {
        BlockPos o = ctx.origin;
        for (int x = -2; x <= 15; x++) {
            for (int z = -3; z <= 5; z++) {
                Block floor = z <= -1 ? Blocks.SPRUCE_PLANKS : ((x + z) % 2 == 0 ? Blocks.STONE_BRICKS : Blocks.POLISHED_ANDESITE);
                set(l, o.offset(x, -1, z), floor.defaultBlockState());
            }
        }
        // Water intake at (-1, -1, -1) feeding a pipe header at z=-1 behind all 6 drills
        BlockPos intake = o.offset(-1, 0, -1);
        set(l, o.offset(-2, 0, -1), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, o.offset(-1, 0, -2), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, o.offset(-1, 0, 0), Blocks.STONE_BRICKS.defaultBlockState());
        set(l, intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        for (int x = 0; x <= 12; x++) {
            set(l, o.offset(x, 0, -1), ModBlocks.WATER_PIPE.get().defaultBlockState());
        }
        ctx.networkAnchors.add(intake);

        String[] skins = {"rustic", "brass", "frosted", "black_opal", "opal", "outback_ochre"};
        Block[] bases = {
                ModBlocks.DIRTROCK.get(),
                ModBlocks.DEEP_DIRTROCK.get(),
                ModBlocks.ABYSSAL_DIRTROCK.get(),
                ModBlocks.OPAL_CLAY_ROCK.get(),
                ModBlocks.MULLOCK_ROCK.get(),
                ModBlocks.RED_DIRT_ROCK.get()
        };

        for (int i = 0; i < skins.length; i++) {
            int x = 1 + i * 2;
            set(l, o.offset(x, -1, 0), bases[i].defaultBlockState());
            BlockPos dPos = o.offset(x, 0, 0);
            set(l, dPos, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.SOUTH));
            int kind = DirtTier.ofRock(bases[i].defaultBlockState());
            set(l, o.offset(x, 0, 1), ModBlocks.PAN.get().defaultBlockState()
                    .setValue(PanBlock.FILL, 2)
                    .setValue(PanBlock.TIER, kind));
            if (l.getBlockEntity(dPos) instanceof MiningDrillBlockEntity dbe) {
                dbe.setSkin(skins[i], "");
                l.sendBlockUpdated(dPos.above(), l.getBlockState(dPos.above()), l.getBlockState(dPos.above()), 3);
            }
            ctx.drills.add(dPos);
        }

        // Skin Bench & Prospector/Outback Crates on the side
        set(l, o.offset(13, 0, 1), ModBlocks.SKIN_BENCH.get().defaultBlockState());
        set(l, o.offset(0, 0, 1), ModBlocks.OUTBACK_CRATE.get().defaultBlockState());

        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, -3), o.offset(15, 4, 5))) {
            fix(l, p.immutable());
        }

        ctx.keepAlive = () -> {
            for (int i = 0; i < skins.length; i++) {
                int kind = DirtTier.ofRock(bases[i].defaultBlockState());
                set(l, o.offset(1 + i * 2, 0, 1), ModBlocks.PAN.get().defaultBlockState()
                        .setValue(PanBlock.FILL, 2)
                        .setValue(PanBlock.TIER, kind));
            }
        };
        ctx.refeed = () -> {
            for (int i = 0; i < skins.length; i++) {
                int x = 1 + i * 2;
                BlockPos dPos = o.offset(x, 0, 0);
                BlockPos pPos = o.offset(x, 0, 1);
                int kind = DirtTier.ofRock(bases[i].defaultBlockState());
                set(l, pPos, ModBlocks.PAN.get().defaultBlockState()
                        .setValue(PanBlock.FILL, 2)
                        .setValue(PanBlock.TIER, kind));
                if (l.getBlockEntity(dPos) instanceof MiningDrillBlockEntity dbe) {
                    dbe.setSkin(skins[i], "");
                    l.sendBlockUpdated(dPos.above(), l.getBlockState(dPos.above()), l.getBlockState(dPos.above()), 3);
                }
            }
        };
    }

    /**
     * Belts page, as its text reads: a drill's belt runs into a Belt Splitter that shares the clumps between two
     * sluices, and a belt in front of each tray carries the finds away to a Sell Pad where they pile up.
     */
    private static void buildBeltsScene(ServerLevel l, ServerPlayer sp, SceneContext ctx) {
        BlockPos o = ctx.origin;
        for (int x = -5; x <= 11; x++) {
            for (int z = -3; z <= 8; z++) {
                double dist = Math.hypot((x - 3.0) / 8.0, (z - 2.0) / 6.0);
                if (dist > 1.0) continue;
                int depth = (int) Math.round((1.0 - dist) * 5.0) + 1;
                for (int dy = -depth - 2; dy <= -3; dy++) {
                    Block under = (x + z + dy) % 3 == 0 ? Blocks.ANDESITE : (x * z + dy) % 2 == 0 ? Blocks.STONE : Blocks.COBBLESTONE;
                    set(l, o.offset(x, dy, z), under.defaultBlockState());
                }
                set(l, o.offset(x, -2, z), Blocks.DIRT.defaultBlockState());
                boolean strip = x >= -2 && x <= 8 && z >= -1 && z <= 5;
                Block top = strip ? ((x + z) % 4 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS)
                        : ((x + z) % 5 == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK);
                set(l, o.offset(x, -1, z), top.defaultBlockState());
            }
        }
        BlockPos tree = o.offset(-3, 0, -1);
        for (int ty = 0; ty < 5; ty++) set(l, tree.above(ty), Blocks.OAK_LOG.defaultBlockState());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 2; dy <= 4; dy++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    if (dx == 0 && dz == 0 && dy < 4) continue;
                    set(l, tree.offset(dx, dy, dz), Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
        }
        set(l, o.offset(9, 0, 5), Blocks.POPPY.defaultBlockState());
        set(l, o.offset(-3, 0, 4), Blocks.DANDELION.defaultBlockState());
        set(l, o.offset(10, 0, 0), Blocks.FERN.defaultBlockState());

        // Pool and intake at the west end; a pipe column along x=0 feeds the drill's back,
        // and pipes along z=0 and z=4 reach the outer side of each sluice.
        set(l, o.offset(-2, -1, 2), Blocks.WATER.defaultBlockState());
        BlockPos intake = o.offset(-1, -1, 2);
        set(l, intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        set(l, o.offset(-2, -2, 2), Blocks.STONE.defaultBlockState());
        set(l, o.offset(-1, -2, 2), Blocks.STONE.defaultBlockState());
        ctx.networkAnchors.add(intake);
        set(l, o.offset(-1, 0, 2), ModBlocks.WATER_PIPE.get().defaultBlockState());
        for (int z = 0; z <= 4; z++) {
            set(l, o.offset(0, 0, z), ModBlocks.WATER_PIPE.get().defaultBlockState());
        }
        for (int x = 1; x <= 4; x++) {
            set(l, o.offset(x, 0, 0), ModBlocks.WATER_PIPE.get().defaultBlockState());
            set(l, o.offset(x, 0, 4), ModBlocks.WATER_PIPE.get().defaultBlockState());
        }

        set(l, o.offset(1, -1, 2), ModBlocks.DIRTROCK.get().defaultBlockState());
        BlockPos drill = o.offset(1, 0, 2);
        set(l, drill, ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        ctx.drills.add(drill);

        BlockState east = ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST);
        BlockPos feed = o.offset(2, 0, 2);
        BlockPos split = o.offset(3, 0, 2);
        BlockPos inA = o.offset(3, 0, 1);
        BlockPos inB = o.offset(3, 0, 3);
        wetUnderBelt(l, feed, ctx);
        set(l, feed, east);
        set(l, split, ModBlocks.BELT_SPLITTER.get().defaultBlockState()
                .setValue(ClumpBeltBlock.FACING, Direction.EAST)
                .setValue(com.holeydigsite.belt.SplitterBeltBlock.TWO_WAY, true));
        wetUnderBelt(l, inA, ctx);
        wetUnderBelt(l, inB, ctx);
        set(l, inA, east);
        set(l, inB, east);
        ctx.belts.add(feed);
        ctx.belts.add(split);
        ctx.belts.add(inA);
        ctx.belts.add(inB);

        BlockPos sluiceA = o.offset(4, 0, 1);
        BlockPos sluiceB = o.offset(4, 0, 3);
        for (BlockPos sPos : List.of(sluiceA, sluiceB)) {
            set(l, sPos, ModBlocks.SLUICE_BOX.get().defaultBlockState()
                    .setValue(SluiceBoxBlock.FACING, Direction.EAST)
                    .setValue(SluiceBoxBlock.UPGRADE, SluiceUpgrade.RIFFLE));
            ctx.sluices.add(sPos);
        }
        set(l, o.offset(4, 0, 2), Blocks.STONE_BRICK_WALL.defaultBlockState());

        // Tray belts: out of each tray, then towards the middle and onto the pad
        BlockPos trayA = o.offset(5, 0, 1);
        BlockPos trayB = o.offset(5, 0, 3);
        BlockPos turnA = o.offset(6, 0, 1);
        BlockPos turnB = o.offset(6, 0, 3);
        BlockPos last = o.offset(6, 0, 2);
        BlockPos pad = o.offset(7, 0, 2);
        wetUnderBelt(l, trayA, ctx);
        wetUnderBelt(l, trayB, ctx);
        wetUnderBelt(l, last, ctx);
        set(l, trayA, east);
        set(l, trayB, east);
        set(l, turnA, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.SOUTH));
        set(l, turnB, ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        set(l, last, east);
        set(l, pad, ModBlocks.SELL_PAD.get().defaultBlockState());
        for (BlockPos b : List.of(trayA, trayB, turnA, turnB, last)) {
            ctx.belts.add(b);
        }
        set(l, o.offset(8, 0, 3), Blocks.SPRUCE_FENCE.defaultBlockState());
        set(l, o.offset(8, 1, 3), Blocks.LANTERN.defaultBlockState());

        for (BlockPos p : BlockPos.betweenClosed(o.offset(-5, -3, -3), o.offset(11, 6, 8))) {
            fix(l, p.immutable());
        }

        ctx.keepAlive = () -> {
            if (l.getBlockEntity(feed) instanceof ClumpBeltBlockEntity b && b.clumpCount() < 2) {
                b.insertByPlayer(1, 1);
            }
            if (l.getBlockEntity(trayA) instanceof ClumpBeltBlockEntity b && b.isEmpty()) {
                b.insertByPlayer(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get()), 1);
            }
            if (l.getBlockEntity(trayB) instanceof ClumpBeltBlockEntity b && b.isEmpty()) {
                b.insertByPlayer(new ItemStack(ModItems.GOLDCHRO_CHUNK_MEDIUM.get()), 1);
            }
        };
        ctx.refeed = () -> {
            for (BlockPos bp : List.of(feed, inA, inB)) {
                if (l.getBlockEntity(bp) instanceof ClumpBeltBlockEntity b) {
                    b.takeAll();
                    b.insertByPlayer(bp.equals(feed) ? 2 : 1, 1);
                }
            }
            for (BlockPos sPos : List.of(sluiceA, sluiceB)) {
                if (l.getBlockEntity(sPos) instanceof SluiceBoxBlockEntity sl) {
                    sl.setOwnerPlayer(sp);
                    sl.addClumps(4);
                    ModBlocks.SLUICE_BOX.get().refreshStatus(l, sPos, l.getBlockState(sPos));
                }
            }
            Item[] finds = {ModItems.FOLKIRON_CHUNK_LARGE.get(), ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), ModItems.UNCUT_RUBY_MEDIUM.get()};
            int i = 0;
            for (BlockPos bp : List.of(trayA, trayB, turnA, turnB, last)) {
                if (l.getBlockEntity(bp) instanceof ClumpBeltBlockEntity b) {
                    b.takeAll();
                    b.insertByPlayer(new ItemStack(finds[i++ % finds.length]), 1);
                }
            }
            if (l.getBlockEntity(pad) instanceof PadBlockEntity p) {
                p.takeAll();
                p.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 2));
            }
        };
    }
}
