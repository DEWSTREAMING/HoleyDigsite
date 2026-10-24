package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.ClumpBeltBlockEntity;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.power.WaterIntakeBlock;
import com.holeydigsite.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only (not shipped): with -Dholeydigsite.shots=true the client creates a flat creative world, builds a scene
 * with drills, intakes, pipes, belts and pans, takes screenshots into run/screenshots and quits.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID, value = Dist.CLIENT)
public final class ClientShots {
    private ClientShots() {}

    private static final boolean ON = Boolean.getBoolean("holeydigsite.shots");
    private static int stage;
    private static int wait;
    private static int titleWait;
    private static BlockPos o;
    private static final List<Runnable> plan = new ArrayList<>();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (wait > 0) { wait--; return; }
        switch (stage) {
            case 0 -> {
                if (mc.screen instanceof TitleScreen || (mc.screen != null && mc.level == null && ++titleWait > 200)) {
                    mc.options.hideGui = true;
                    LevelSettings settings = new LevelSettings("shots", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("shots" + System.currentTimeMillis(), settings, new WorldOptions(1L, false, false),
                            reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                            mc.screen);
                    stage = 1;
                }
            }
            case 1 -> {
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                    stage = 2;
                    wait = 60;
                }
            }
            case 2 -> {
                var server = mc.getSingleplayerServer();
                server.execute(() -> build(server.overworld(), server.getPlayerList().getPlayers().get(0)));
                stage = 3;
                wait = 40;
                makePlan(mc);
            }
            case 3 -> {
                if (plan.isEmpty()) {
                    stage = 4;
                    mc.stop();
                } else {
                    plan.remove(0).run();
                }
            }
            default -> {}
        }
    }

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, 3);
    }

    private static void fix(ServerLevel l, BlockPos p) {
        l.setBlock(p, Block.updateFromNeighbourShapes(l.getBlockState(p), l, p), 3);
    }

    /** Phase 14: -Dholeydigsite.world=true shoots only the generated world (pack template + mod-built sites). */
    private static final boolean WORLD = Boolean.getBoolean("holeydigsite.world");
    /** Phase 14: the template origin (set by {@link #phase14}). */
    private static BlockPos t;

    private static void build(ServerLevel l, ServerPlayer player) {
        l.setDayTime(6000);
        l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, l.getServer());
        o = player.blockPosition().offset(3, 0, 3);
        if (WORLD) {
            phase14(l);
            player.teleportTo(l, t.getX() - 10, t.getY() + 30, t.getZ() - 10, -45, 30);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            return;
        }
        int y = o.getY();
        // clear an area
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, 0, -4), o.offset(12, 4, 10))) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, -4), o.offset(12, -1, 10))) l.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        // Row A (z=0): intake (pipe on top) -> pipes -> drill (east) -> 3 belts -> pan
        BlockPos intake = o;
        set(l, intake, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        set(l, o.offset(-1, 0, 0), Blocks.STONE.defaultBlockState());
        set(l, o.offset(0, 0, -1), Blocks.STONE.defaultBlockState());
        set(l, o.offset(0, 0, 1), Blocks.STONE.defaultBlockState());
        set(l, o.offset(0, 1, 0), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(1, 1, 0), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(1, 0, 0), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(2, -1, 0), ModBlocks.DIRTROCK.get().defaultBlockState());
        set(l, o.offset(2, 0, 0), ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        set(l, o.offset(6, 0, 0), ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 1));
        for (int x = 3; x <= 5; x++) set(l, o.offset(x, 0, 0), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        // Row B (z=3): intake with pipes on its west and north sides; belts into a FULL pan (bounces)
        BlockPos intake2 = o.offset(0, 0, 3);
        set(l, intake2, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        set(l, o.offset(-1, 0, 3), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(0, 0, 2), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(1, 0, 3), Blocks.STONE.defaultBlockState());
        set(l, o.offset(0, 0, 4), Blocks.STONE.defaultBlockState());
        // item models on the ground
        for (var it : new net.minecraft.world.item.Item[]{com.holeydigsite.registry.ModItems.CLUMP_BELT.get(), com.holeydigsite.registry.ModItems.DIRT_CLUMP.get()}) {
            var e = new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 8.5 + (it == com.holeydigsite.registry.ModItems.DIRT_CLUMP.get() ? 1 : 0), o.getY(), o.getZ() + 1.5, new net.minecraft.world.item.ItemStack(it));
            e.setNeverPickUp(); e.setUnlimitedLifetime(); e.setDeltaMovement(0, 0, 0);
            l.addFreshEntity(e);
        }
        set(l, o.offset(5, 0, 3), ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 4));
        for (int x = 3; x <= 4; x++) set(l, o.offset(x, 0, 3), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        // Row C (z=6): side merge: feeder belts from north and south into a line ending in air; one belt powered
        for (int x = 2; x <= 5; x++) set(l, o.offset(x, 0, 6), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        set(l, o.offset(3, 0, 5), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.SOUTH));
        set(l, o.offset(3, 0, 7), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        set(l, o.offset(8, 0, 6), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        set(l, o.offset(8, 0, 7), Blocks.REDSTONE_BLOCK.defaultBlockState());
        // Row D (z=9): sluices facing north (tray towards the camera): working + belt, idle with loot, stopped, no power
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, 0, 8), o.offset(12, 3, 12))) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, 8), o.offset(12, -1, 12))) l.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        int[] sx = {0, 3, 6, 9};
        for (int i = 0; i < 4; i++) {
            set(l, o.offset(sx[i], 0, 9), ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(com.holeydigsite.sluice.SluiceBoxBlock.FACING, Direction.NORTH));
            if (i < 3) {
                set(l, o.offset(sx[i] + 2, 0, 9), Blocks.STONE.defaultBlockState());
                set(l, o.offset(sx[i] + 1, 0, 8), Blocks.STONE.defaultBlockState());
                set(l, o.offset(sx[i] + 1, 0, 10), Blocks.STONE.defaultBlockState());
                set(l, o.offset(sx[i] + 1, 0, 9), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
            }
        }
        set(l, o.offset(0, 0, 10), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        set(l, o.offset(0, 0, 11), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.NORTH));
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, 0, -2), o.offset(10, 2, 12))) {
            if (!l.getBlockState(p).isAir()) fix(l, p.immutable());
        }
        phase7(l);
        phase8(l);
        phase9(l);
        phase10(l);
        phase11(l);
        phase12(l);
        phase121(l);
        load(l);
        player.teleportTo(l, o.getX() + 3.5, y + 6, o.getZ() - 4.0, 0, 50);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    /**
     * Phase 11 scene: the real island template (pack nbt, path in -Dholeydigsite.island) placed at z=80 with its three locked
     * plots, plus a strip at z=74..78: Gravelrock / Sandrock, gravel / sand pans and belts, new items, Orchremia / Silvane in
     * crucibles, the Tier 3 drill.
     */
    private static void phase11(ServerLevel l) {
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, 72), o.offset(46, 12, 106))) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        String island = System.getProperty("holeydigsite.island");
        if (island != null) {
            try {
                var tag = net.minecraft.nbt.NbtIo.readCompressed(java.nio.file.Path.of(island), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                var template = l.getStructureManager().readStructure(tag);
                BlockPos at = o.offset(0, -3, 80);
                template.placeInWorld(l, at, at, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), l.random, 2);
            } catch (Exception e) {
                HoleyDigsite.LOGGER.error("island", e);
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, 72), o.offset(20, -1, 78))) l.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
            set(l, o.offset(dx, -1, 74 + dz), ModBlocks.GRAVELROCK.get().defaultBlockState());
            set(l, o.offset(3 + dx, -1, 74 + dz), ModBlocks.SANDROCK.get().defaultBlockState());
        }
        BlockState pan = ModBlocks.PAN.get().defaultBlockState();
        set(l, o.offset(0, 0, 77), pan.setValue(PanBlock.FILL, 4).setValue(PanBlock.TIER, 4));
        set(l, o.offset(1, 0, 77), pan.setValue(PanBlock.FILL, 2).setValue(PanBlock.TIER, 4));
        set(l, o.offset(3, 0, 77), pan.setValue(PanBlock.FILL, 4).setValue(PanBlock.TIER, 5));
        set(l, o.offset(4, 0, 77), PanBlock.withFill(ModBlocks.LARGE_PAN.get().defaultBlockState(), 8).setValue(PanBlock.TIER, 5));
        set(l, o.offset(1, 0, 74), ModBlocks.MINING_DRILL_T3.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.SOUTH));
        for (int x = 6; x <= 9; x++) set(l, o.offset(x, 0, 75), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        // forges with Orchremia / Silvane crucibles and hot molds
        for (int i = 0; i < 2; i++) {
            BlockPos f = o.offset(12 + i * 3, 0, 75);
            set(l, f, ModBlocks.FORGE.get().defaultBlockState().setValue(ForgeBlock.FACING, Direction.SOUTH));
            set(l, f.above(), ModBlocks.CRUCIBLE.get().defaultBlockState().setValue(com.holeydigsite.forge.CrucibleBlock.FACING, Direction.SOUTH));
            set(l, f.south(), ModBlocks.MOLD.get().defaultBlockState());
        }
        // items on the floor
        net.minecraft.world.item.Item[] items = {com.holeydigsite.registry.ModItems.GRAVEL_CLUMP.get(), com.holeydigsite.registry.ModItems.SAND_CLUMP.get(),
                com.holeydigsite.registry.ModItems.ORCHREMIA_CHUNK_SMALL.get(), com.holeydigsite.registry.ModItems.ORCHREMIA_CHUNK_MEDIUM.get(),
                com.holeydigsite.registry.ModItems.ORCHREMIA_CHUNK_LARGE.get(), com.holeydigsite.registry.ModItems.SILVANE_CHUNK_SMALL.get(),
                com.holeydigsite.registry.ModItems.SILVANE_CHUNK_MEDIUM.get(), com.holeydigsite.registry.ModItems.SILVANE_CHUNK_LARGE.get(),
                com.holeydigsite.registry.ModItems.SAPPHIRE_SMALL.get(), com.holeydigsite.registry.ModItems.SAPPHIRE_MEDIUM.get(),
                com.holeydigsite.registry.ModItems.SAPPHIRE_LARGE.get(), com.holeydigsite.registry.ModItems.TOPAZ_SMALL.get(),
                com.holeydigsite.registry.ModItems.TOPAZ_MEDIUM.get(), com.holeydigsite.registry.ModItems.TOPAZ_LARGE.get(),
                com.holeydigsite.registry.ModItems.LAND_DEED_DEEP_DIG.get(), com.holeydigsite.registry.ModItems.LAND_DEED_GRAVEL_FLATS.get(),
                com.holeydigsite.registry.ModItems.LAND_DEED_SAND_DUNES.get()};
        for (int i = 0; i < items.length; i++) {
            var e = new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 6.3 + (i % 6) * 0.8, o.getY(), o.getZ() + 77.0 + (i / 6) * 0.8,
                    new net.minecraft.world.item.ItemStack(items[i]));
            e.setNeverPickUp(); e.setUnlimitedLifetime(); e.setDeltaMovement(0, 0, 0);
            l.addFreshEntity(e);
        }
        for (int i = 0; i < 2; i++) {
            var it = i == 0 ? com.holeydigsite.registry.ModItems.ORCHREMIA_INGOT.get() : com.holeydigsite.registry.ModItems.SILVANE_INGOT.get();
            var e = new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 11.5 + i * 0.9, o.getY(), o.getZ() + 77.6, com.holeydigsite.item.IngotItem.of(it, 50));
            e.setNeverPickUp(); e.setUnlimitedLifetime(); e.setDeltaMovement(0, 0, 0);
            l.addFreshEntity(e);
        }
    }

    private static BlockState beltState(Block b, Direction f) {
        return b.defaultBlockState().setValue(ClumpBeltBlock.FACING, f);
    }

    /** A waterlogged intake hidden in the floor (so it doesn't flood the scene); at ground level a pipe goes on top of it. */
    private static void wet(ServerLevel l, BlockPos p) {
        if (p.getY() < o.getY()) {
            set(l, p, ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
        } else {
            set(l, p.below(), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
            set(l, p, ModBlocks.WATER_PIPE.get().defaultBlockState());
        }
    }

    /**
     * Phase 12 scene at z=110..130: drill -> belts -> incline -> belt -> filter (side: crucible on a forge) -> Sell Pad;
     * sluice -> belt -> Gem Cutter -> belt -> Sell Pad; corners; the rugged drills (all tiers) with a sluice and pipes;
     * uncut vs cut gems.
     */
    private static void phase12(ServerLevel l) {
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-3, 0, 108), o.offset(16, 7, 132))) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-3, -1, 108), o.offset(16, -1, 132))) l.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        Block belt = ModBlocks.CLUMP_BELT.get();
        // line 1 (z=112)
        wet(l, o.offset(0, 0, 112));
        set(l, o.offset(1, 0, 112), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(2, -1, 112), ModBlocks.DIRTROCK.get().defaultBlockState());
        set(l, o.offset(2, 0, 112), ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.EAST));
        set(l, o.offset(3, 0, 112), beltState(belt, Direction.EAST));
        set(l, o.offset(4, 0, 112), beltState(belt, Direction.EAST));
        wet(l, o.offset(4, -1, 112));
        set(l, o.offset(5, 0, 112), Blocks.STONE.defaultBlockState());
        set(l, o.offset(5, 1, 112), beltState(ModBlocks.INCLINE_BELT.get(), Direction.EAST));
        set(l, o.offset(6, 0, 112), Blocks.STONE.defaultBlockState());
        set(l, o.offset(6, 1, 112), beltState(belt, Direction.EAST));
        set(l, o.offset(7, 0, 112), Blocks.STONE.defaultBlockState());
        set(l, o.offset(7, 1, 112), beltState(ModBlocks.FILTER_BELT.get(), Direction.EAST));
        set(l, o.offset(7, 0, 111), ModBlocks.FORGE.get().defaultBlockState().setValue(ForgeBlock.FACING, Direction.WEST));
        set(l, o.offset(7, 1, 111), ModBlocks.CRUCIBLE.get().defaultBlockState().setValue(com.holeydigsite.forge.CrucibleBlock.FACING, Direction.WEST));
        set(l, o.offset(8, 0, 112), Blocks.STONE.defaultBlockState());
        set(l, o.offset(8, 1, 112), beltState(belt, Direction.EAST));
        set(l, o.offset(9, 0, 112), Blocks.STONE.defaultBlockState());
        set(l, o.offset(9, 1, 112), ModBlocks.SELL_PAD.get().defaultBlockState());
        // line 2 (z=116): sluice -> belt -> Gem Cutter -> belt -> Sell Pad
        set(l, o.offset(2, 0, 116), ModBlocks.SLUICE_BOX.get().defaultBlockState().setValue(com.holeydigsite.sluice.SluiceBoxBlock.FACING, Direction.EAST));
        wet(l, o.offset(2, 0, 117));
        set(l, o.offset(3, 0, 116), beltState(belt, Direction.EAST));
        wet(l, o.offset(3, -1, 116));
        set(l, o.offset(4, 0, 116), beltState(ModBlocks.GEM_CUTTER.get(), Direction.EAST));
        set(l, o.offset(5, 0, 116), beltState(belt, Direction.EAST));
        set(l, o.offset(6, 0, 116), ModBlocks.SELL_PAD.get().defaultBlockState());
        // corners (z=120..122): east feeder -> right corner south -> belt -> left corner east -> belt
        set(l, o.offset(2, 0, 120), beltState(belt, Direction.EAST));
        wet(l, o.offset(2, -1, 120));
        set(l, o.offset(3, 0, 120), beltState(belt, Direction.SOUTH));
        set(l, o.offset(3, 0, 121), beltState(belt, Direction.SOUTH));
        set(l, o.offset(3, 0, 122), beltState(belt, Direction.EAST));
        set(l, o.offset(4, 0, 122), beltState(belt, Direction.EAST));
        set(l, o.offset(5, 0, 122), beltState(ModBlocks.FAST_CLUMP_BELT.get(), Direction.EAST));
        // drills, all tiers, facing south, intakes behind them, a sluice and pipes
        Block[] drills = {ModBlocks.MINING_DRILL.get(), ModBlocks.MINING_DRILL_T2.get(), ModBlocks.MINING_DRILL_T3.get()};
        for (int i = 0; i < 3; i++) {
            int x = 2 + i * 3;
            set(l, o.offset(x, -1, 126), ModBlocks.DIRTROCK.get().defaultBlockState());
            set(l, o.offset(x, 0, 126), drills[i].defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.SOUTH));
            if (i < 2) set(l, o.offset(x, 0, 125), ModBlocks.WATER_PIPE.get().defaultBlockState());
            set(l, o.offset(x, 0, 127), ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, i == 1 ? 4 : 2));
        }
        wet(l, o.offset(2, 0, 124));
        set(l, o.offset(3, 0, 124), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(4, 0, 124), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(5, 0, 124), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(11, 0, 126), ModBlocks.SLUICE_BOX_T2.get().defaultBlockState().setValue(com.holeydigsite.sluice.SluiceBoxBlock.FACING, Direction.SOUTH));
        set(l, o.offset(12, 0, 126), ModBlocks.WATER_PIPE.get().defaultBlockState());
        set(l, o.offset(12, 0, 125), ModBlocks.WATER_PIPE.get().defaultBlockState());
        wet(l, o.offset(12, 0, 124));
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-1, -1, 110), o.offset(14, 3, 128))) {
            if (!l.getBlockState(p).isAir()) fix(l, p.immutable());
        }
        // uncut vs cut gems on the floor (z=130)
        net.minecraft.world.item.Item[][] gems = {
                {com.holeydigsite.registry.ModItems.UNCUT_RUBY_SMALL.get(), com.holeydigsite.registry.ModItems.UNCUT_RUBY_MEDIUM.get(), com.holeydigsite.registry.ModItems.UNCUT_RUBY_LARGE.get(),
                 com.holeydigsite.registry.ModItems.UNCUT_SAPPHIRE_SMALL.get(), com.holeydigsite.registry.ModItems.UNCUT_SAPPHIRE_MEDIUM.get(), com.holeydigsite.registry.ModItems.UNCUT_SAPPHIRE_LARGE.get(),
                 com.holeydigsite.registry.ModItems.UNCUT_TOPAZ_SMALL.get(), com.holeydigsite.registry.ModItems.UNCUT_TOPAZ_MEDIUM.get(), com.holeydigsite.registry.ModItems.UNCUT_TOPAZ_LARGE.get()},
                {com.holeydigsite.registry.ModItems.RUBY_SMALL.get(), com.holeydigsite.registry.ModItems.RUBY_MEDIUM.get(), com.holeydigsite.registry.ModItems.RUBY_LARGE.get(),
                 com.holeydigsite.registry.ModItems.SAPPHIRE_SMALL.get(), com.holeydigsite.registry.ModItems.SAPPHIRE_MEDIUM.get(), com.holeydigsite.registry.ModItems.SAPPHIRE_LARGE.get(),
                 com.holeydigsite.registry.ModItems.TOPAZ_SMALL.get(), com.holeydigsite.registry.ModItems.TOPAZ_MEDIUM.get(), com.holeydigsite.registry.ModItems.TOPAZ_LARGE.get()}};
        for (int r = 0; r < 2; r++) for (int i = 0; i < 9; i++) {
            var e = new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 2.5 + i * 0.7, o.getY(), o.getZ() + 130.0 + r * 0.9,
                    new net.minecraft.world.item.ItemStack(gems[r][i]));
            e.setNeverPickUp(); e.setUnlimitedLifetime(); e.setDeltaMovement(0, 0, 0);
            l.addFreshEntity(e);
        }
    }

    private static ClumpBeltBlockEntity beltAt(ServerLevel l, int x, int y, int z) {
        return (ClumpBeltBlockEntity) l.getBlockEntity(o.offset(x, y, z));
    }

    /** Phase 12 contents: items on the lines, the filter, the sluice tray, a gem under the cutter wheel. */
    private static void phase12Load(ServerLevel l) {
        var I = new Object() {
            net.minecraft.world.item.ItemStack s(net.minecraft.world.item.Item it) { return new net.minecraft.world.item.ItemStack(it); }
        };
        beltAt(l, 3, 0, 112).insertByPlayer(2, 1);
        beltAt(l, 4, 0, 112).insertByPlayer(I.s(com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_LARGE.get()), 1);
        beltAt(l, 4, 0, 112).insertByPlayer(I.s(com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_MEDIUM.get()), 1);
        beltAt(l, 5, 1, 112).insertByPlayer(I.s(com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_MEDIUM.get()), 2);
        beltAt(l, 6, 1, 112).insertByPlayer(I.s(com.holeydigsite.registry.ModItems.UNCUT_RUBY_MEDIUM.get()), 1);
        beltAt(l, 7, 1, 112).setFilter(I.s(com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_SMALL.get()));
        beltAt(l, 8, 1, 112).insertByPlayer(I.s(com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_SMALL.get()), 1);
        ((com.holeydigsite.forge.CrucibleBlockEntity) l.getBlockEntity(o.offset(7, 1, 111))).setContents(HoleyDigsite.id("folkiron"), 30, 60);
        var sluice = (com.holeydigsite.sluice.SluiceBoxBlockEntity) l.getBlockEntity(o.offset(2, 0, 116));
        sluice.addToTray(I.s(com.holeydigsite.registry.ModItems.UNCUT_TOPAZ_LARGE.get()));
        sluice.addToTray(I.s(com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_SMALL.get()));
        beltAt(l, 4, 0, 116).insertByPlayer(I.s(com.holeydigsite.registry.ModItems.UNCUT_SAPPHIRE_LARGE.get()), 1);
        beltAt(l, 5, 0, 116).insertByPlayer(I.s(com.holeydigsite.registry.ModItems.RUBY_MEDIUM.get()), 1);
        beltAt(l, 2, 0, 120).insertByPlayer(3, 1);
        beltAt(l, 3, 0, 121).insertByPlayer(2, 4);
        beltAt(l, 4, 0, 122).insertByPlayer(2, 5);
    }

    /** Phase 11 contents once things exist: molten metal, belt clumps, one plot (Deep Dig) claimed for the "Owned" shot. */
    private static void phase11Load(ServerLevel l) {
        ((com.holeydigsite.forge.CrucibleBlockEntity) l.getBlockEntity(o.offset(12, 1, 75))).setContents(HoleyDigsite.id("orchremia"), 20, 140);
        ((com.holeydigsite.forge.CrucibleBlockEntity) l.getBlockEntity(o.offset(15, 1, 75))).setContents(HoleyDigsite.id("silvane"), 0, 180);
        ((com.holeydigsite.forge.MoldBlockEntity) l.getBlockEntity(o.offset(12, 0, 76))).pour(HoleyDigsite.id("orchremia"), 50);
        ((com.holeydigsite.forge.MoldBlockEntity) l.getBlockEntity(o.offset(15, 0, 76))).pour(HoleyDigsite.id("silvane"), 50);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(6, 0, 75))).insertByPlayer(2, 4);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(8, 0, 75))).insertByPlayer(2, 5);
    }

    private static void claimDeepDig(ServerLevel l) {
        BlockPos b = o.offset(5, 0, 91);   // template (5,3,11) placed at y-3
        if (l.getBlockEntity(b) instanceof com.holeydigsite.plot.PlotBoardBlockEntity be) {
            be.register();
            com.holeydigsite.plot.PlotData.get(l).setOwned(be.plotId(), b, true);
            l.setBlock(b, l.getBlockState(b).setValue(com.holeydigsite.plot.PlotBoardBlock.OWNED, true), 3);
        }
    }

    /** Phase 7 scene at z=14..20: the three rock tiers, tiered pans, belts with tiered clumps, items, the Sell Pad. */
    private static void phase7(ServerLevel l) {
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, 0, 13), o.offset(12, 4, 22))) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, 13), o.offset(12, -1, 22))) l.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        Block[] rocks = {ModBlocks.DIRTROCK.get(), ModBlocks.DEEP_DIRTROCK.get(), ModBlocks.ABYSSAL_DIRTROCK.get()};
        for (int i = 0; i < 3; i++) {
            // a 2x2 patch of each rock, a full pan of that tier in front, a drill on the rock
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) set(l, o.offset(i * 3 + dx, -1, 14 + dz), rocks[i].defaultBlockState());
            set(l, o.offset(i * 3, 0, 14), rocks[i].defaultBlockState());
            set(l, o.offset(i * 3 + 1, 0, 15), ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.NORTH));
            set(l, o.offset(i * 3 + 1, 0, 14), ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 4).setValue(PanBlock.TIER, i + 1));
            set(l, o.offset(i * 3, 0, 16), ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 2).setValue(PanBlock.TIER, i + 1));
        }
        // belts carrying mixed tiers (east), ending in air
        for (int x = 0; x <= 4; x++) set(l, o.offset(x, 0, 18), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        set(l, o.offset(5, 0, 18), Blocks.REDSTONE_BLOCK.defaultBlockState());
        // Sell Pads: one bare, one with a stack lying on it
        // items on the ground, row z=20
        var M = new net.minecraft.world.item.ItemStack[]{
                new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_SMALL.get()),
                new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_MEDIUM.get()),
                new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_LARGE.get()),
                new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_SMALL.get()),
                new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_MEDIUM.get()),
                new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_LARGE.get()),
                new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.RUBY_SMALL.get()),
                new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.RUBY_MEDIUM.get()),
                new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.RUBY_LARGE.get()),
                com.holeydigsite.tier.DirtTier.clumps(1, 1), com.holeydigsite.tier.DirtTier.clumps(2, 1), com.holeydigsite.tier.DirtTier.clumps(3, 1),
                ModBlocks.PAN.get().createStack(4, 3), new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.SELL_PAD.get())};
        for (int i = 0; i < M.length; i++) {
            var e = new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 0.5 + i * 0.75, o.getY(), o.getZ() + 20.5, M[i]);
            e.setNeverPickUp(); e.setUnlimitedLifetime(); e.setDeltaMovement(0, 0, 0);
            l.addFreshEntity(e);
        }
    }

    /** Phase 8 scene at z=24..32: Sell Pad with stacks, Buy Pad with blueprints, Money Pans at 4 pile steps, displays. */
    private static void phase8(ServerLevel l) {
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, 0, 23), o.offset(14, 4, 33))) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, 23), o.offset(14, -1, 33))) l.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        var I = com.holeydigsite.registry.ModItems.class;
        set(l, o.offset(2, 0, 28), ModBlocks.SELL_PAD.get().defaultBlockState());
        set(l, o.offset(3, 0, 28), ModBlocks.SHOP_BUTTON.get().defaultBlockState());
        set(l, o.offset(4, 0, 28), ModBlocks.BUY_PAD.get().defaultBlockState());
        set(l, o.offset(3, 0, 29), ModBlocks.MONEY_PAN.get().defaultBlockState());
        var sell = (com.holeydigsite.shop.PadBlockEntity) l.getBlockEntity(o.offset(2, 0, 28));
        for (var it : new net.minecraft.world.item.Item[]{com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_LARGE.get(), com.holeydigsite.registry.ModItems.RUBY_MEDIUM.get(),
                com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_SMALL.get(), com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_SMALL.get(), com.holeydigsite.registry.ModItems.RUBY_LARGE.get()}) {
            sell.insert(new net.minecraft.world.item.ItemStack(it, 3));
        }
        var buy = (com.holeydigsite.shop.PadBlockEntity) l.getBlockEntity(o.offset(4, 0, 28));
        buy.insert(com.holeydigsite.item.BlueprintItem.of(com.holeydigsite.registry.ModItems.MINING_DRILL.get(), 2));
        buy.insert(com.holeydigsite.item.BlueprintItem.of(com.holeydigsite.registry.ModItems.WATER_PIPE.get(), 1));
        ((com.holeydigsite.shop.MoneyPanBlockEntity) l.getBlockEntity(o.offset(3, 0, 29))).setBalance(2 * 64 + 6 * 8 + 4);
        long[] bal = {0, 40, 700, 12000};
        for (int i = 0; i < 4; i++) {
            set(l, o.offset(6 + i * 2, 0, 28), ModBlocks.MONEY_PAN.get().defaultBlockState());
            ((com.holeydigsite.shop.MoneyPanBlockEntity) l.getBlockEntity(o.offset(6 + i * 2, 0, 28))).setBalance(bal[i]);
        }
        net.minecraft.world.item.Item[] shown = {com.holeydigsite.registry.ModItems.MINING_DRILL.get(), com.holeydigsite.registry.ModItems.PAN.get(),
                com.holeydigsite.registry.ModItems.SLUICE_BOX.get(), com.holeydigsite.registry.ModItems.CLUMP_BELT.get(), net.minecraft.world.item.Items.DIRT};
        for (int i = 0; i < shown.length; i++) {
            set(l, o.offset(2 + i, 0, 25), ModBlocks.SHOP_DISPLAY.get().defaultBlockState());
            ((com.holeydigsite.shop.ShopDisplayBlockEntity) l.getBlockEntity(o.offset(2 + i, 0, 25))).setItem(shown[i]);
        }
    }

    /** Phase 9 scene at z=35..45: unlit + lit forges, lava pipes, crucibles (per metal / fill / chunks), molds, ingots. */
    private static void phase9(ServerLevel l) {
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, 0, 34), o.offset(16, 5, 46))) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, 34), o.offset(16, -1, 46))) l.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        var south = net.minecraft.core.Direction.SOUTH;
        BlockState forge = ModBlocks.FORGE.get().defaultBlockState().setValue(com.holeydigsite.forge.ForgeBlock.FACING, south);
        // unlit forge with a dry intake + dark pipe
        set(l, o.offset(1, 0, 38), forge);
        set(l, o.offset(1, 0, 37), ModBlocks.LAVA_PIPE.get().defaultBlockState());
        set(l, o.offset(1, 0, 36), ModBlocks.LAVA_INTAKE.get().defaultBlockState());
        // a row of lit forges fed by one filled intake through lava pipes behind them (z=37)
        for (int x = 3; x <= 12; x++) set(l, o.offset(x, 0, 37), ModBlocks.LAVA_PIPE.get().defaultBlockState());
        // Phase 9.1: the intake is lava-logged; stone around it keeps its lava in place
        set(l, o.offset(14, 0, 37), Blocks.STONE.defaultBlockState());
        set(l, o.offset(13, 0, 36), Blocks.STONE.defaultBlockState());
        set(l, o.offset(13, 0, 38), Blocks.STONE.defaultBlockState());
        set(l, o.offset(13, 0, 37), ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(com.holeydigsite.power.LavaIntakeBlock.LAVALOGGED, true));
        for (int x : new int[]{3, 6, 8, 10, 12}) set(l, o.offset(x, 0, 38), forge);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(0, 0, 36), o.offset(13, 0, 38))) fix(l, p.immutable());
        BlockState cru = ModBlocks.CRUCIBLE.get().defaultBlockState().setValue(com.holeydigsite.forge.CrucibleBlock.FACING, south);
        for (int x : new int[]{6, 8, 10, 12}) {
            set(l, o.offset(x, 1, 38), cru);
            set(l, o.offset(x, 0, 39), ModBlocks.MOLD.get().defaultBlockState());
        }
        var F = HoleyDigsite.id("folkiron"); var G = HoleyDigsite.id("goldchro");
        crucible(l, 6).setContents(F, 12, 87);
        crucible(l, 8).setContents(G, 0, 200);
        crucible(l, 10).setContents(F, 30, 0);
        crucible(l, 12).setContents(G, 0, 120);
        // ingot items on the floor
        int[] w = {10, 50, 200};
        for (int i = 0; i < 3; i++) {
            for (var it : new net.minecraft.world.item.Item[]{com.holeydigsite.registry.ModItems.FOLKIRON_INGOT.get(), com.holeydigsite.registry.ModItems.GOLDCHRO_INGOT.get()}) {
                var e = new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 1.5 + i * 0.8, o.getY(),
                        o.getZ() + 41.5 + (it == com.holeydigsite.registry.ModItems.GOLDCHRO_INGOT.get() ? 0.9 : 0), com.holeydigsite.item.IngotItem.of(it, w[i]));
                e.setNeverPickUp(); e.setUnlimitedLifetime(); e.setDeltaMovement(0, 0, 0);
                l.addFreshEntity(e);
            }
        }
    }

    /**
     * Phase 10 scene at z=49..64: four Crafting Forges facing south (unlit / loaded / casting / output) fed by one lava-logged
     * intake through pipes behind them; Tier 1 vs Tier 2 machines; Large Pans; fast belts; crucibles tipping in 4 facings.
     */
    private static void phase10(ServerLevel l) {
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, 0, 48), o.offset(30, 5, 72))) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, 48), o.offset(30, -1, 72))) l.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        var south = Direction.SOUTH;
        BlockState cf = ModBlocks.CRAFTING_FORGE.get().defaultBlockState().setValue(com.holeydigsite.forge.CraftingForgeBlock.FACING, south);
        // A (x=0): unlit, nothing behind it
        set(l, o.offset(0, 0, 51), cf);
        // B..D (x=5,10,15): lit through pipes at z=50 from an intake at x=17
        for (int x = 5; x <= 16; x++) set(l, o.offset(x, 0, 50), ModBlocks.LAVA_PIPE.get().defaultBlockState());
        set(l, o.offset(18, 0, 50), Blocks.STONE.defaultBlockState());
        set(l, o.offset(17, 0, 49), Blocks.STONE.defaultBlockState());
        set(l, o.offset(17, 0, 51), Blocks.STONE.defaultBlockState());
        set(l, o.offset(17, 0, 50), ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(com.holeydigsite.power.LavaIntakeBlock.LAVALOGGED, true));
        for (int x : new int[]{5, 10, 15}) set(l, o.offset(x, 0, 51), cf);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(4, 0, 49), o.offset(17, 0, 51))) fix(l, p.immutable());
        // Tier 1 vs Tier 2 machines (z=56): drills on Dirtrock with water intakes behind them, sluices
        for (int i = 0; i < 2; i++) {
            int x = 1 + i * 3;
            set(l, o.offset(x, -1, 56), ModBlocks.DIRTROCK.get().defaultBlockState());
            set(l, o.offset(x, 0, 56), (i == 0 ? ModBlocks.MINING_DRILL.get() : ModBlocks.MINING_DRILL_T2.get()).defaultBlockState()
                    .setValue(MiningDrillBlock.FACING, south));
            set(l, o.offset(x, 0, 55), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
            set(l, o.offset(x - 1, 0, 55), Blocks.STONE.defaultBlockState());
            set(l, o.offset(x + 1, 0, 55), Blocks.STONE.defaultBlockState());
            set(l, o.offset(x, 0, 54), Blocks.STONE.defaultBlockState());
            set(l, o.offset(x, 0, 57), ModBlocks.PAN.get().defaultBlockState());
        }
        for (int i = 0; i < 2; i++) {
            int x = 8 + i * 3;
            set(l, o.offset(x, 0, 56), (i == 0 ? ModBlocks.SLUICE_BOX.get() : ModBlocks.SLUICE_BOX_T2.get()).defaultBlockState()
                    .setValue(com.holeydigsite.sluice.SluiceBoxBlock.FACING, south));
            set(l, o.offset(x + 1, 0, 56), ModBlocks.WATER_INTAKE.get().defaultBlockState().setValue(WaterIntakeBlock.WATERLOGGED, true));
            set(l, o.offset(x + 2, 0, 56), Blocks.STONE.defaultBlockState());
            set(l, o.offset(x + 1, 0, 55), Blocks.STONE.defaultBlockState());
            set(l, o.offset(x + 1, 0, 57), Blocks.STONE.defaultBlockState());
        }
        for (BlockPos p : BlockPos.betweenClosed(o.offset(0, 0, 54), o.offset(12, 0, 57))) fix(l, p.immutable());
        // Large Pans (z=60): empty, 4 (tier 1), 8 (tier 1), 8 deep, 6 abyssal; a normal full pan for comparison
        BlockState lp = ModBlocks.LARGE_PAN.get().defaultBlockState();
        set(l, o.offset(1, 0, 60), ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 4));
        set(l, o.offset(3, 0, 60), lp);
        set(l, o.offset(4, 0, 60), PanBlock.withFill(lp, 4));
        set(l, o.offset(5, 0, 60), PanBlock.withFill(lp, 8));
        set(l, o.offset(6, 0, 60), PanBlock.withFill(lp, 8).setValue(PanBlock.TIER, 2));
        set(l, o.offset(7, 0, 60), PanBlock.withFill(lp, 6).setValue(PanBlock.TIER, 3));
        // fast belts (z=63) next to normal belts (z=64), each into a pan
        for (int x = 1; x <= 4; x++) {
            set(l, o.offset(x, 0, 63), ModBlocks.FAST_CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
            set(l, o.offset(x, 0, 64), ModBlocks.CLUMP_BELT.get().defaultBlockState().setValue(ClumpBeltBlock.FACING, Direction.EAST));
        }
        set(l, o.offset(5, 0, 63), ModBlocks.LARGE_PAN.get().defaultBlockState());
        set(l, o.offset(5, 0, 64), ModBlocks.PAN.get().defaultBlockState());
        for (BlockPos p : BlockPos.betweenClosed(o.offset(1, 0, 63), o.offset(5, 0, 64))) fix(l, p.immutable());
        // Tier 2 items on the floor
        net.minecraft.world.item.Item[] items = {com.holeydigsite.registry.ModItems.BRUSH.get(), com.holeydigsite.registry.ModItems.GOLDCHRO_BRUSH.get(),
                com.holeydigsite.registry.ModItems.LARGE_PAN.get(), com.holeydigsite.registry.ModItems.FAST_CLUMP_BELT.get(),
                com.holeydigsite.registry.ModItems.MINING_DRILL_T2.get(), com.holeydigsite.registry.ModItems.SLUICE_BOX_T2.get(),
                com.holeydigsite.registry.ModItems.CRAFTING_FORGE.get()};
        for (int i = 0; i < items.length; i++) {
            var e = new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 9.5 + (i % 4) * 0.9, o.getY(), o.getZ() + 60.3 + (i / 4) * 1.0,
                    new net.minecraft.world.item.ItemStack(items[i]));
            e.setNeverPickUp(); e.setUnlimitedLifetime(); e.setDeltaMovement(0, 0, 0);
            l.addFreshEntity(e);
        }
        // crucibles on forges in all 4 facings (z=53, x=15..18 is too far: use a separate strip at x=14..17, z=58..61)
        int i = 0;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            BlockPos f = o.offset(20 + (i % 2) * 6, 0, 58 + (i / 2) * 6);
            set(l, f, ModBlocks.FORGE.get().defaultBlockState().setValue(ForgeBlock.FACING, d));
            set(l, f.above(), ModBlocks.CRUCIBLE.get().defaultBlockState().setValue(com.holeydigsite.forge.CrucibleBlock.FACING,
                    com.holeydigsite.forge.CrucibleBlock.placementFacing(l, f.above(), Direction.NORTH)));
            set(l, f.relative(d), ModBlocks.MOLD.get().defaultBlockState());
            i++;
        }
    }

    /** Phase 10 contents: loads forges B..D and starts the casts (the forges are lit by now). */
    private static void phase10Load(ServerLevel l) {
        ServerPlayer p = l.getServer().getPlayerList().getPlayers().get(0);
        var folk = com.holeydigsite.registry.ModItems.FOLKIRON_INGOT.get();
        var gold = com.holeydigsite.registry.ModItems.GOLDCHRO_INGOT.get();
        for (int x : new int[]{5, 10, 15}) {
            var f = (com.holeydigsite.forge.CraftingForgeBlockEntity) l.getBlockEntity(o.offset(x, 0, 51));
            f.load(com.holeydigsite.item.IngotItem.of(folk, 437), 1, false);
            f.load(com.holeydigsite.item.IngotItem.of(gold, x == 5 ? 20 : 60), 1, false);
            f.load(new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.MINING_DRILL.get()), 1, false);
        }
        // D: cast now - its output is waiting when the shots are taken
        com.holeydigsite.forge.CraftingForgeBlock.tryCast(l, o.offset(15, 0, 51), p);
        for (int x : new int[]{1, 4}) ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(x, 0, 63))).insertByPlayer(2);
        for (int x : new int[]{1, 3}) ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(x, 0, 64))).insertByPlayer(2);
    }

    private static void tipAll(ServerLevel l) {
        int i = 0;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            BlockPos c = o.offset(20 + (i % 2) * 6, 1, 58 + (i / 2) * 6);
            ((com.holeydigsite.forge.CrucibleBlockEntity) l.getBlockEntity(c)).setContents(HoleyDigsite.id(i % 2 == 0 ? "folkiron" : "goldchro"), 0, 150);
            // hold the tipped state for the shot (the scheduled tick un-tips it after 10 ticks)
            l.setBlock(c, l.getBlockState(c).setValue(com.holeydigsite.forge.CrucibleBlock.TIPPING, true), 2);
            i++;
        }
    }

    private static com.holeydigsite.forge.CrucibleBlockEntity crucible(ServerLevel l, int x) {
        return (com.holeydigsite.forge.CrucibleBlockEntity) l.getBlockEntity(o.offset(x, 1, 38));
    }

    private static com.holeydigsite.forge.MoldBlockEntity mold(ServerLevel l, int x) {
        return (com.holeydigsite.forge.MoldBlockEntity) l.getBlockEntity(o.offset(x, 0, 39));
    }

    /** Server side: set mold targets and pour (molds start hot). */
    private static void pours(ServerLevel l) {
        ServerPlayer p = l.getServer().getPlayerList().getPlayers().get(0);
        com.holeydigsite.forge.MoldBlock.click(l, o.offset(12, 0, 39), p, false);
        com.holeydigsite.forge.MoldBlock.click(l, o.offset(12, 0, 39), p, false);   // 100 g
        mold(l, 8).pour(HoleyDigsite.id("goldchro"), 50);
        mold(l, 10).pour(HoleyDigsite.id("folkiron"), 25);
        mold(l, 12).pour(HoleyDigsite.id("goldchro"), 30);
    }

    private static void loadPhase7(ServerLevel l) {
        int[][] belt = {{0, 1}, {0, 2}, {1, 3}, {1, 1}, {2, 2}, {2, 3}, {3, 2}, {3, 1}, {4, 3}, {4, 2}};
        for (int[] b : belt) {
            ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(b[0], 0, 18))).insertByPlayer(1, b[1]);
        }
        var stack = new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 10.5, o.getY() + 0.3, o.getZ() + 15.5,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK, 3));
        stack.setNeverPickUp(); stack.setUnlimitedLifetime(); stack.setDeltaMovement(0, 0, 0);
        l.addFreshEntity(stack);
    }

    private static com.holeydigsite.sluice.SluiceBoxBlockEntity sl(ServerLevel l, int x) {
        return (com.holeydigsite.sluice.SluiceBoxBlockEntity) l.getBlockEntity(o.offset(x, 0, 9));
    }

    private static void sluices(ServerLevel l) {
        sl(l, 0).addClumps(4);
        sl(l, 3).addToTray(new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_SMALL.get(), 7));
        sl(l, 3).addToTray(new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 4));
        sl(l, 3).addToTray(new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_SMALL.get(), 1));
        sl(l, 3).addToTray(new net.minecraft.world.item.ItemStack(com.holeydigsite.registry.ModItems.RUBY_MEDIUM.get(), 1));
        for (var it : new net.minecraft.world.item.Item[]{com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_SMALL.get(),
                com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_LARGE.get(), com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_MEDIUM.get(),
                com.holeydigsite.registry.ModItems.RUBY_SMALL.get(), com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_MEDIUM.get()}) {
            for (int k = 0; k < 2; k++) sl(l, 6).addToTray(new net.minecraft.world.item.ItemStack(it, 64));
        }
        sl(l, 6).addClumps(2);
        sl(l, 9).addClumps(1);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(0, 0, 10))).insertByPlayer(3);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(0, 0, 11))).insertByPlayer(4);
        for (int x : new int[]{0, 3, 6, 9}) {
            BlockPos p = o.offset(x, 0, 9);
            ModBlocks.SLUICE_BOX.get().refreshStatus(l, p, l.getBlockState(p));
        }
    }

    private static void load(ServerLevel l) {
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(3, 0, 0))).insertByPlayer(4);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(4, 0, 0))).insertByPlayer(2);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(3, 0, 3))).insertByPlayer(4);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(4, 0, 3))).insertByPlayer(4);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(3, 0, 5))).insertByPlayer(2);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(3, 0, 7))).insertByPlayer(2);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(2, 0, 6))).insertByPlayer(4);
        ((ClumpBeltBlockEntity) l.getBlockEntity(o.offset(8, 0, 6))).insertByPlayer(3);
    }

    private static void cam(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.teleportTo(server.overworld(), o.getX() + x, o.getY() + y, o.getZ() + z, yaw, pitch);
        });
    }

    /** Puts the camera (eye) at {@code eye} looking at {@code at}, both relative to the scene origin. */
    private static void look(Minecraft mc, double ex, double ey, double ez, double ax, double ay, double az) {
        double dx = ax - ex, dy = ay - ey, dz = az - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        cam(mc, ex, ey - 1.62, ez, yaw, pitch);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> HoleyDigsite.LOGGER.info("SHOT {}", name));
    }

    private static void reload(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> load(server.overworld()));
    }

    private static void idle(int n) {
        for (int i = 0; i < n; i++) plan.add(() -> {});
    }

    private static void burst(Minecraft mc, String name, int n) {
        for (int i = 0; i < n; i++) {
            final int k = i;
            plan.add(() -> shot(mc, String.format("%s_%02d", name, k)));
        }
    }

    private static void server(Minecraft mc, java.util.function.Consumer<ServerLevel> job) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> job.accept(server.overworld()));
    }


    // ------------------------------------------------------------------ Phase 12.1 (z = 140..190)
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** Incline cell bases: cell i (facing FACINGS[i % 4], fast if i >= 4) at x = 3 + (i % 4) * 9, z = 146 / 160. */
    private static BlockPos inclineBase(int i) {
        return o.offset(3 + (i % 4) * 9, 0, i < 4 ? 146 : 160);
    }
    /** Corner cell: index = facing * 2 + (right ? 1 : 0), fast if >= 8; x = 2 + (k % 8) * 4, z = 174 / 179. */
    private static BlockPos cornerCell(int k) {
        return o.offset(2 + (k % 8) * 4, 0, k < 8 ? 174 : 179);
    }

    /**
     * Phase 12.1 scene: per facing (normal + Fast) a belt -> Inclined Belt (one up) -> belt -> belt line, with items;
     * one staircase (two inclines); left/right corners in all facings (normal + Fast); a row of Shop Displays and two
     * Sell Pads side by side for the label rule; incline items on the floor (item model).
     */
    private static void phase121(ServerLevel l) {
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-3, 0, 138), o.offset(40, 8, 194))) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-3, -1, 138), o.offset(40, -1, 194))) l.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        for (int i = 0; i < 8; i++) {
            Direction f = FACINGS[i % 4];
            boolean fast = i >= 4;
            Block flat = fast ? ModBlocks.FAST_CLUMP_BELT.get() : ModBlocks.CLUMP_BELT.get();
            Block inc = fast ? ModBlocks.FAST_INCLINE_BELT.get() : ModBlocks.INCLINE_BELT.get();
            // centre the 4-long line on the cell
            BlockPos b = inclineBase(i).relative(f, -2);
            set(l, b, beltState(flat, f));
            wet(l, b.below());
            set(l, b.relative(f, 1).above(), beltState(inc, f));
            for (int k = 2; k <= 3; k++) {
                set(l, b.relative(f, k), Blocks.SMOOTH_STONE.defaultBlockState());
                set(l, b.relative(f, k).above(), beltState(flat, f));
            }
            set(l, b.relative(f, 1), Blocks.SMOOTH_STONE.defaultBlockState());
        }
        // staircase east at z=153: belt, incline, incline (one higher), belt
        BlockPos s0 = o.offset(30, 0, 153);
        set(l, s0, beltState(ModBlocks.CLUMP_BELT.get(), Direction.EAST));
        wet(l, s0.below());
        set(l, s0.offset(1, 0, 0), Blocks.SMOOTH_STONE.defaultBlockState());
        set(l, s0.offset(1, 1, 0), beltState(ModBlocks.INCLINE_BELT.get(), Direction.EAST));
        for (int y = 0; y <= 1; y++) set(l, s0.offset(2, y, 0), Blocks.SMOOTH_STONE.defaultBlockState());
        set(l, s0.offset(2, 2, 0), beltState(ModBlocks.INCLINE_BELT.get(), Direction.EAST));
        for (int y = 0; y <= 1; y++) set(l, s0.offset(3, y, 0), Blocks.SMOOTH_STONE.defaultBlockState());
        set(l, s0.offset(3, 2, 0), beltState(ModBlocks.CLUMP_BELT.get(), Direction.EAST));
        // corners: feeder from the left / right side -> corner -> belt out
        for (int k = 0; k < 16; k++) {
            Direction f = FACINGS[(k % 8) / 2];
            boolean right = (k % 2) == 1;
            Block flat = k >= 8 ? ModBlocks.FAST_CLUMP_BELT.get() : ModBlocks.CLUMP_BELT.get();
            BlockPos c = cornerCell(k);
            Direction in = right ? f.getClockWise() : f.getCounterClockWise();
            set(l, c.relative(in), beltState(flat, in.getOpposite()));
            wet(l, c.relative(in).below());
            set(l, c, beltState(flat, f));
            set(l, c.relative(f), beltState(flat, f));
        }
        // a row of Shop Displays (z=186) and two Sell Pads side by side (z=189)
        net.minecraft.world.item.Item[] shown = {com.holeydigsite.registry.ModItems.INCLINE_BELT.get(), com.holeydigsite.registry.ModItems.FAST_INCLINE_BELT.get(),
                com.holeydigsite.registry.ModItems.FILTER_BELT.get(), com.holeydigsite.registry.ModItems.GEM_CUTTER.get(),
                com.holeydigsite.registry.ModItems.LAND_DEED_GRAVEL_FLATS.get(), com.holeydigsite.registry.ModItems.GOLDCHRO_BRUSH.get(),
                com.holeydigsite.registry.ModItems.FAST_CLUMP_BELT.get()};
        for (int i = 0; i < shown.length; i++) {
            BlockPos d = o.offset(4 + i, 0, 186);
            set(l, d, ModBlocks.SHOP_DISPLAY.get().defaultBlockState());
            ((com.holeydigsite.shop.ShopDisplayBlockEntity) l.getBlockEntity(d)).setItem(shown[i]);
        }
        set(l, o.offset(5, 0, 189), ModBlocks.SELL_PAD.get().defaultBlockState());
        set(l, o.offset(6, 0, 189), ModBlocks.SELL_PAD.get().defaultBlockState());
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-1, -1, 140), o.offset(38, 4, 190))) {
            if (!l.getBlockState(p).isAir()) fix(l, p.immutable());
        }
        // incline items on the floor (item model)
        net.minecraft.world.item.Item[] floor = {com.holeydigsite.registry.ModItems.INCLINE_BELT.get(), com.holeydigsite.registry.ModItems.FAST_INCLINE_BELT.get()};
        for (int i = 0; i < 2; i++) {
            var e = new net.minecraft.world.entity.item.ItemEntity(l, o.getX() + 12.5 + i * 1.0, o.getY(), o.getZ() + 186.5,
                    new net.minecraft.world.item.ItemStack(floor[i]));
            e.setNeverPickUp(); e.setUnlimitedLifetime(); e.setDeltaMovement(0, 0, 0);
            l.addFreshEntity(e);
        }
    }

    /** Phase 12.1 contents: items on every incline line and corner, items on the two Sell Pads. */
    private static void phase121Load(ServerLevel l) {
        net.minecraft.world.item.Item[] its = {com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_LARGE.get(), com.holeydigsite.registry.ModItems.UNCUT_RUBY_MEDIUM.get(),
                com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), com.holeydigsite.registry.ModItems.UNCUT_SAPPHIRE_LARGE.get()};
        for (int i = 0; i < 8; i++) {
            Direction f = FACINGS[i % 4];
            BlockPos b = inclineBase(i).relative(f, -2);
            // more than the two upper belts hold, so the line backs up and items wait ON the slope
            ((ClumpBeltBlockEntity) l.getBlockEntity(b)).insertByPlayer(4, 1);
            ((ClumpBeltBlockEntity) l.getBlockEntity(b.relative(f, 1).above())).insertByPlayer(new net.minecraft.world.item.ItemStack(its[i % 4]), 4);
            ((ClumpBeltBlockEntity) l.getBlockEntity(b.relative(f, 2).above())).insertByPlayer(new net.minecraft.world.item.ItemStack(its[(i + 1) % 4]), 4);
            ((ClumpBeltBlockEntity) l.getBlockEntity(b.relative(f, 3).above())).insertByPlayer(new net.minecraft.world.item.ItemStack(its[(i + 2) % 4]), 4);
        }
        BlockPos s0 = o.offset(30, 0, 153);
        ((ClumpBeltBlockEntity) l.getBlockEntity(s0.offset(1, 1, 0))).insertByPlayer(3, 2);
        ((ClumpBeltBlockEntity) l.getBlockEntity(s0.offset(2, 2, 0))).insertByPlayer(new net.minecraft.world.item.ItemStack(its[0]), 2);
        for (int k = 0; k < 16; k++) {
            ((ClumpBeltBlockEntity) l.getBlockEntity(cornerCell(k))).insertByPlayer(new net.minecraft.world.item.ItemStack(its[k % 4]), 2);
        }
        ((com.holeydigsite.shop.PadBlockEntity) l.getBlockEntity(o.offset(5, 0, 189))).insert(new net.minecraft.world.item.ItemStack(its[0], 3));
        ((com.holeydigsite.shop.PadBlockEntity) l.getBlockEntity(o.offset(6, 0, 189))).insert(new net.minecraft.world.item.ItemStack(its[1], 2));
    }

    /** Fresh items at the start of the staircase and on every corner feeder (so they are moving when shot). */
    private static void phase121Refeed(ServerLevel l) {
        net.minecraft.world.item.Item[] its = {com.holeydigsite.registry.ModItems.FOLKIRON_CHUNK_LARGE.get(), com.holeydigsite.registry.ModItems.UNCUT_RUBY_MEDIUM.get(),
                com.holeydigsite.registry.ModItems.GOLDCHRO_CHUNK_MEDIUM.get(), com.holeydigsite.registry.ModItems.UNCUT_SAPPHIRE_LARGE.get()};
        BlockPos s0 = o.offset(30, 0, 153);
        for (int x = 0; x <= 3; x++) {
            BlockPos p = s0.offset(x, x == 0 ? 0 : x == 1 ? 1 : 2, 0);
            ((ClumpBeltBlockEntity) l.getBlockEntity(p)).takeAll();
        }
        ((ClumpBeltBlockEntity) l.getBlockEntity(s0)).insertByPlayer(new net.minecraft.world.item.ItemStack(its[0]), 4);
        ((ClumpBeltBlockEntity) l.getBlockEntity(s0.offset(1, 1, 0))).insertByPlayer(new net.minecraft.world.item.ItemStack(its[1]), 4);
        for (int k = 0; k < 16; k++) {
            Direction f = FACINGS[(k % 8) / 2];
            Direction in = (k % 2) == 1 ? f.getClockWise() : f.getCounterClockWise();
            BlockPos c = cornerCell(k);
            for (BlockPos p : new BlockPos[]{c.relative(in), c, c.relative(f)}) ((ClumpBeltBlockEntity) l.getBlockEntity(p)).takeAll();
            ((ClumpBeltBlockEntity) l.getBlockEntity(c.relative(in))).insertByPlayer(new net.minecraft.world.item.ItemStack(its[k % 4]), 4);
            ((ClumpBeltBlockEntity) l.getBlockEntity(c)).insertByPlayer(new net.minecraft.world.item.ItemStack(its[(k + 1) % 4]), 2);
        }
    }

    /** Camera beside / in front of / behind incline cell {@code i} (relative to o), looking at the incline. */
    private static void inclineView(Minecraft mc, int i, String view, double dist, double height) {
        Direction f = FACINGS[i % 4];
        BlockPos c = inclineBase(i).relative(f, -1).above().subtract(o); // the incline block
        double cx = c.getX() + 0.5, cy = c.getY() + 0.5, cz = c.getZ() + 0.5;
        Direction d = switch (view) {
            case "side" -> f.getClockWise();
            case "front" -> f;
            default -> f.getOpposite();
        };
        double ox = d.getStepX() * dist, oz = d.getStepZ() * dist;
        if (view.equals("front") || view.equals("back")) { // a little off-axis so both side walls show
            ox += f.getClockWise().getStepX() * 1.2;
            oz += f.getClockWise().getStepZ() * 1.2;
        }
        look(mc, cx + ox, cy + height, cz + oz, cx, cy, cz);
    }

    /** Phase 12.1 shots: inclines in all facings (side / front / back, normal + Fast), staircase, corners while moving, labels. */
    // ------------------------------------------------------------------------------------------------ Phase 14
    /**
     * Phase 14 scene: the pack's island template (-Dholeydigsite.island = holey_digsite.nbt) placed in the sky; its Site Markers
     * make the mod build the Trading Post and the three plots (with kiosks) from the jar's structures, like a new world does.
     */
    private static void phase14(ServerLevel l) {
        t = o.offset(0, 45, 0);
        String island = System.getProperty("holeydigsite.island");
        try {
            var tag = net.minecraft.nbt.NbtIo.readCompressed(java.nio.file.Path.of(island), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var template = l.getStructureManager().readStructure(tag);
            template.placeInWorld(l, t, t, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), l.random, 2);
        } catch (Exception e) {
            HoleyDigsite.LOGGER.error("island", e);
        }
    }

    /** Digs the Abyssal pocket under Deep Dig's hill open (as a player would) and a few Deep pockets. */
    private static void phase14Dig(ServerLevel l) {
        // Abyssal pocket under the hill: x 13..16, z 39..42, rock at y 4
        for (int x = 13; x <= 16; x++) for (int z = 39; z <= 42; z++) {
            for (int y = 5; y < 16; y++) l.setBlock(t.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        // Deep pocket under the meadow podzol (x 2..5, z 45..48, rock at y 5)
        for (int x = 2; x <= 5; x++) for (int z = 45; z <= 48; z++) for (int y = 6; y < 12; y++) l.setBlock(t.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        // drills on the uncovered rock
        set(l, t.offset(14, 5, 40), ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.SOUTH));
        set(l, t.offset(14, 5, 41), ModBlocks.PAN.get().defaultBlockState());
        set(l, t.offset(3, 6, 46), ModBlocks.MINING_DRILL.get().defaultBlockState().setValue(MiningDrillBlock.FACING, Direction.SOUTH));
        set(l, t.offset(3, 6, 47), ModBlocks.PAN.get().defaultBlockState());
        l.setBlock(t.offset(16, 5, 42), Blocks.LANTERN.defaultBlockState(), 2);
        l.setBlock(t.offset(5, 6, 48), Blocks.LANTERN.defaultBlockState(), 2);
    }

    private static void lookT(Minecraft mc, double ex, double ey, double ez, double ax, double ay, double az) {
        look(mc, ex + t.getX() - o.getX(), ey + t.getY() - o.getY(), ez + t.getZ() - o.getZ(),
                ax + t.getX() - o.getX(), ay + t.getY() - o.getY(), az + t.getZ() - o.getZ());
    }

    private static void viewT(Minecraft mc, String name, double ex, double ey, double ez, double ax, double ay, double az, int settle) {
        String only = System.getProperty("holeydigsite.only");     // e.g. -Dholeydigsite.only=d0 : just the dig shots
        if (only != null && !name.startsWith(only)) return;
        plan.add(() -> lookT(mc, ex, ey, ez, ax, ay, az));
        idle(settle);
        plan.add(() -> shot(mc, name));
    }

    private static void makePlan(Minecraft mc) {
        if (WORLD) {
            makePlanPhase14(mc);
        } else {
            makePlanPhase121(mc);
        }
    }

    /** Phase 14 views (template coordinates: home x 0..44 z 0..31, store building x 23..41 z 7..19, plots z 36..56). */
    private static void makePlanPhase14(Minecraft mc) {
        idle(120);
        viewT(mc, "w00_overview", -10, 34, -10, 24, 6, 18, 120);
        viewT(mc, "w01_overview_se", 92, 40, 84, 36, 6, 28, 100);
        viewT(mc, "w02_home_west", -9, 17, 30, 12, 9, 12, 80);
        viewT(mc, "w03_spawn_view", 10.5, 11.6, 12.5, 30, 13, 13, 60);
        viewT(mc, "w04_store_front", 9, 15, 20, 31, 14, 12, 60);
        viewT(mc, "w05_store_nw", 14, 22, -10, 33, 13, 13, 60);
        viewT(mc, "w06_store_back", 50, 18, 26, 32, 13, 12, 60);
        viewT(mc, "w07_inside_east", 24.6, 12.6, 13.5, 40, 11.5, 13, 50);
        viewT(mc, "w08_inside_checkout", 29.5, 12.6, 15.5, 26, 11.2, 9, 50);
        viewT(mc, "w09_checkout_close", 26.5, 12.4, 12.2, 26.5, 11, 9.5, 50);
        viewT(mc, "w10_inside_west", 39.5, 12.6, 13.5, 24, 11.5, 12, 50);
        viewT(mc, "w11_display_label", 34, 12.6, 14, 35, 12.2, 11, 50);
        viewT(mc, "p00_deep_dig_sw", -3, 21, 59, 10, 8, 46, 80);
        viewT(mc, "p01_deep_dig_ne", 22, 20, 34, 9, 7, 47, 80);
        viewT(mc, "p02_gravel_sw", 25, 19, 59, 37, 7, 46, 80);
        viewT(mc, "p03_gravel_ne", 50, 19, 33, 37, 7, 47, 80);
        viewT(mc, "p04_sand_sw", 52, 21, 59, 64, 9, 46, 80);
        viewT(mc, "p05_sand_ne", 77, 21, 33, 64, 9, 47, 80);
        viewT(mc, "k00_kiosk_deep", 13, 13, 42.5, 4, 10, 41.5, 40);
        viewT(mc, "k01_kiosk_gravel", 36, 13, 41, 44, 10, 40, 40);
        viewT(mc, "k02_kiosk_sand", 60.5, 13, 52, 60.5, 10, 43, 40);
        plan.add(() -> server(mc, ClientShots::phase14Dig)); idle(20);
        viewT(mc, "d00_abyssal_dug", 14.5, 21, 49, 14.5, 5, 40.5, 60);
        viewT(mc, "d01_deep_dug", 3.5, 19, 54, 3.5, 5, 46.5, 40);
        viewT(mc, "d02_pit_open_deep", 14, 12, 52, 11.5, 6, 47.5, 40);
        idle(10);
    }

    private static void makePlanPhase121(Minecraft mc) {
        idle(60);
        plan.add(() -> server(mc, ClientShots::phase121Load)); idle(5);
        plan.add(() -> server(mc, l -> l.setDayTime(6000)));
        plan.add(() -> look(mc, 16.0, 10.0, 138.0, 16.0, 0.0, 153.0)); idle(80); plan.add(() -> shot(mc, "a00_overview"));
        String[] fn = {"n", "e", "s", "w"};
        for (int i = 0; i < 8; i++) {
            final int k = i;
            String name = (i < 4 ? "inc_" : "fast_") + fn[i % 4];
            plan.add(() -> inclineView(mc, k, "side", 3.2, 0.9)); idle(12); plan.add(() -> shot(mc, "a1_" + name + "_side"));
            plan.add(() -> inclineView(mc, k, "front", 3.4, 2.9)); idle(12); plan.add(() -> shot(mc, "a2_" + name + "_front"));
            if (i < 4) {
                plan.add(() -> inclineView(mc, k, "back", 3.6, 1.6)); idle(12); plan.add(() -> shot(mc, "a3_" + name + "_back"));
            }
        }
        // close side view of a backed-up line (items wait on the slope; static belt)
        plan.add(() -> inclineView(mc, 1, "side", 1.9, 0.9)); idle(15); plan.add(() -> shot(mc, "a4_close_blocked"));
        // staircase while moving: fresh items, frames 3 ticks apart (items climb, belt texture scrolls)
        plan.add(() -> look(mc, 32.0, 3.6, 150.2, 32.0, 1.6, 153.5)); idle(10);
        plan.add(() -> server(mc, ClientShots::phase121Refeed)); idle(12);
        plan.add(() -> shot(mc, "a5_staircase_0")); idle(3); plan.add(() -> shot(mc, "a5_staircase_1"));
        plan.add(() -> look(mc, 31.6, 2.6, 155.6, 32.0, 2.0, 153.5)); idle(1); plan.add(() -> shot(mc, "a5_staircase_close"));
        // corners from above while moving, two frames 2 ticks apart
        plan.add(() -> look(mc, 17.0, 9.0, 171.0, 17.0, 0.0, 177.0)); idle(10);
        plan.add(() -> server(mc, ClientShots::phase121Refeed)); idle(8);
        plan.add(() -> shot(mc, "b0_corners_0")); idle(2); plan.add(() -> shot(mc, "b0_corners_1"));
        plan.add(() -> server(mc, ClientShots::phase121Refeed));
        for (int k = 0; k < 8; k += 2) {
            final int a = k;
            plan.add(() -> {
                BlockPos c = cornerCell(a).subtract(o);
                look(mc, c.getX() + 2.5, 4.2, c.getZ() - 2.0, c.getX() + 2.5, 0.4, c.getZ() + 0.6);
            });
            idle(10); plan.add(() -> shot(mc, "b1_corner_pair_" + fn[a / 2]));
        }
        plan.add(() -> look(mc, 15.0, 3.0, 182.0, 15.0, 0.6, 179.5)); idle(10); plan.add(() -> shot(mc, "b2_fast_corners"));
        // Shop Display row: looking at the third one; standing near without looking at any; pads
        plan.add(() -> look(mc, 6.5, 2.2, 182.5, 6.5, 0.7, 186.5)); idle(25); plan.add(() -> shot(mc, "c0_displays_look_3rd"));
        plan.add(() -> look(mc, 9.0, 2.2, 183.0, 10.5, 0.7, 186.5)); idle(25); plan.add(() -> shot(mc, "c1_displays_look_6th"));
        plan.add(() -> look(mc, 2.6, 1.6, 186.5, 12.0, 1.6, 190.0)); idle(25); plan.add(() -> shot(mc, "c2_near_not_looking"));
        plan.add(() -> look(mc, 7.5, 1.6, 185.0, 7.5, 1.0, 186.5)); idle(25); plan.add(() -> shot(mc, "c2b_very_close"));
        plan.add(() -> look(mc, 7.0, 6.0, 178.0, 7.0, 0.0, 187.0)); idle(25); plan.add(() -> shot(mc, "c3_displays_far"));
        plan.add(() -> look(mc, 5.5, 2.0, 191.8, 5.5, 0.3, 189.5)); idle(25); plan.add(() -> shot(mc, "c4_two_sell_pads"));
        plan.add(() -> look(mc, 13.0, 1.4, 184.6, 13.0, 0.2, 186.5)); idle(10); plan.add(() -> shot(mc, "c5_incline_items"));
        idle(10);
    }

    /** Phase 12 shots: belt lines, filter, sluice -> Gem Cutter, corners, rugged drills, gems (z=110..131). */
    private static void makePlanPhase12(Minecraft mc) {
        idle(60);
        plan.add(() -> server(mc, ClientShots::phase12Load)); idle(5);
        plan.add(() -> server(mc, l -> l.setDayTime(6000)));
        plan.add(() -> look(mc, 12.0, 9.0, 104.0, 6.0, 0.0, 118.0)); idle(60); plan.add(() -> shot(mc, "80_phase12_overview"));
        plan.add(() -> look(mc, 5.5, 3.6, 107.5, 5.5, 0.8, 112.0)); idle(15); plan.add(() -> shot(mc, "81_drill_belts_incline_filter_crucible"));
        plan.add(() -> look(mc, 9.5, 3.4, 114.5, 7.5, 1.6, 112.0)); idle(15); plan.add(() -> shot(mc, "82_filter_icon_and_side_crucible"));
        plan.add(() -> look(mc, 4.0, 2.6, 119.8, 4.0, 0.8, 116.0)); idle(30); plan.add(() -> shot(mc, "83_sluice_gem_cutter_sell_pad"));
        plan.add(() -> look(mc, 4.0, 5.5, 125.0, 3.5, 0.0, 121.0)); idle(15); plan.add(() -> shot(mc, "84_corners"));
        plan.add(() -> look(mc, 7.0, 2.8, 133.0, 7.0, 1.0, 126.0)); idle(20); plan.add(() -> shot(mc, "85_rugged_drills_all_tiers"));
        plan.add(() -> look(mc, 5.0, 2.6, 121.5, 3.5, 1.0, 125.5)); idle(10); plan.add(() -> shot(mc, "86_drill_backs_pipes_lanterns"));
        plan.add(() -> look(mc, 5.3, 1.6, 132.6, 5.3, 0.0, 130.4)); idle(10); plan.add(() -> shot(mc, "87_uncut_vs_cut_gems"));
        plan.add(() -> look(mc, 5.6, 2.2, 108.6, 5.2, 1.0, 112.3)); idle(10); plan.add(() -> shot(mc, "88_incline_side"));
        plan.add(() -> look(mc, 4.2, 2.0, 113.6, 4.5, 1.2, 116.5)); idle(10); plan.add(() -> shot(mc, "89_gem_cutter_wheel"));
        idle(10);
    }

    /** Phase 11 shots (kept for reference; not run). */
    private static void makePlanPhase11(Minecraft mc) {
        idle(60);
        plan.add(() -> server(mc, ClientShots::phase11Load)); idle(5);
        plan.add(() -> server(mc, l -> l.setDayTime(6000)));
        plan.add(() -> look(mc, 21.5, 22.0, 70.0, 21.5, 0.0, 92.0)); idle(60); plan.add(() -> shot(mc, "70_plots_overview"));
        plan.add(() -> look(mc, 6.5, 9.0, 86.0, 6.5, 0.0, 97.0)); idle(20); plan.add(() -> shot(mc, "71_deep_dig"));
        plan.add(() -> look(mc, 21.5, 9.0, 86.0, 21.5, 0.0, 97.0)); idle(20); plan.add(() -> shot(mc, "72_gravel_flats"));
        plan.add(() -> look(mc, 36.5, 9.0, 86.0, 36.5, 0.0, 97.0)); idle(20); plan.add(() -> shot(mc, "73_sand_dunes"));
        plan.add(() -> look(mc, 5.5, 1.8, 88.0, 5.5, 0.6, 91.5)); idle(20); plan.add(() -> shot(mc, "74_board_for_sale"));
        plan.add(() -> server(mc, ClientShots::claimDeepDig)); idle(10);
        plan.add(() -> look(mc, 5.5, 1.8, 88.0, 5.5, 0.6, 91.5)); idle(10); plan.add(() -> shot(mc, "75_board_owned"));
        plan.add(() -> look(mc, 2.5, 3.5, 70.5, 2.5, 0.0, 75.5)); idle(20); plan.add(() -> shot(mc, "76_new_rocks_pans_t3_drill"));
        plan.add(() -> look(mc, 8.5, 2.6, 80.0, 8.5, 0.0, 77.0)); idle(10); plan.add(() -> shot(mc, "77_new_items"));
        plan.add(() -> look(mc, 13.8, 2.8, 79.5, 13.8, 0.6, 75.5)); idle(10); plan.add(() -> shot(mc, "78_new_metals_molten"));
        idle(10);
    }

    /** Phase 10 shots (kept for reference; not run). */
    private static void makePlanPhase10(Minecraft mc) {
        idle(60);
        plan.add(() -> server(mc, ClientShots::phase10Load)); idle(5);
        plan.add(() -> server(mc, l -> l.setDayTime(13500)));
        plan.add(() -> look(mc, 8.0, 6.5, 62.0, 8.0, 0.4, 51.5)); idle(40); plan.add(() -> shot(mc, "50_phase10_overview"));
        plan.add(() -> look(mc, 0.5, 2.2, 54.6, 0.5, 0.9, 51.5)); idle(20); plan.add(() -> shot(mc, "51_crafting_forge_unlit"));
        plan.add(() -> look(mc, 5.5, 2.2, 54.6, 5.5, 0.9, 51.5)); idle(10); plan.add(() -> shot(mc, "52_crafting_forge_loaded_text"));
        plan.add(() -> server(mc, l -> com.holeydigsite.forge.CraftingForgeBlock.tryCast(l, o.offset(10, 0, 51), null)));
        plan.add(() -> look(mc, 10.5, 2.2, 54.6, 10.5, 0.9, 51.5)); idle(25); plan.add(() -> shot(mc, "53_crafting_forge_casting"));
        plan.add(() -> look(mc, 12.8, 1.4, 51.5, 10.5, 0.9, 51.5)); idle(5); plan.add(() -> shot(mc, "54_crafting_forge_casting_handle_side"));
        idle(120);
        plan.add(() -> look(mc, 15.5, 2.2, 54.6, 15.5, 0.9, 51.5)); idle(10); plan.add(() -> shot(mc, "55_crafting_forge_output"));
        plan.add(() -> look(mc, 7.0, 3.0, 61.0, 6.0, 0.3, 56.0)); idle(20); plan.add(() -> shot(mc, "56_tier1_vs_tier2_machines"));
        plan.add(() -> look(mc, 5.0, 2.6, 63.5, 5.0, 0.0, 60.0)); idle(20); plan.add(() -> shot(mc, "57_large_pans"));
        plan.add(() -> look(mc, 3.5, 2.6, 67.0, 3.5, 0.0, 63.5)); idle(10); plan.add(() -> shot(mc, "58_fast_belts"));
        plan.add(() -> look(mc, 10.8, 1.6, 63.0, 10.8, 0.0, 60.8)); idle(10); plan.add(() -> shot(mc, "59_tier2_items"));
        plan.add(() -> server(mc, ClientShots::tipAll)); idle(3);
        // each tipping crucible from the side (profile), its mold visible: N and S from the west, E and W from the south
        double[][] cams = {{20, 58, -1}, {26, 58, 0}, {20, 64, -1}, {26, 64, 0}};
        String[] names = {"north", "east", "south", "west"};
        for (int i = 0; i < 4; i++) {
            double fx = cams[i][0] + 0.5, fz = cams[i][1] + 0.5;
            boolean zAxis = cams[i][2] < 0;
            final String n = names[i];
            plan.add(() -> look(mc, zAxis ? fx - 3.2 : fx, 2.0, zAxis ? fz : fz + 3.2, fx, 1.2, fz));
            idle(3);
            plan.add(() -> shot(mc, "6" + "0123".charAt(java.util.List.of(names).indexOf(n)) + "_crucible_tip_facing_" + n));
        }
        plan.add(() -> look(mc, 23.5, 9.0, 61.6, 23.5, 0.0, 61.5)); idle(3); plan.add(() -> shot(mc, "64_crucibles_tip_top"));
        idle(10);
    }

    /** Phase 9 shots (kept for reference; not run). */
    private static void makePlanPhase9(Minecraft mc) {
        idle(60);
        plan.add(() -> server(mc, l -> l.setDayTime(13500)));
        plan.add(() -> look(mc, 7.0, 3.6, 45.0, 7.0, 0.4, 38.0)); idle(40); plan.add(() -> shot(mc, "40_phase9_overview"));
        plan.add(() -> look(mc, 2.0, 1.7, 41.5, 2.0, 0.5, 38.0)); idle(30); plan.add(() -> shot(mc, "41_forge_unlit_vs_lit"));
        plan.add(() -> look(mc, 9.0, 3.4, 41.0, 9.0, 1.2, 38.3)); idle(30); plan.add(() -> shot(mc, "42_crucibles_fill_glow_text"));
        plan.add(() -> server(mc, ClientShots::pours)); idle(15);
        plan.add(() -> look(mc, 9.5, 1.9, 42.0, 9.5, 0.1, 39.2)); idle(15); plan.add(() -> shot(mc, "43_molds_hot_text"));
        idle(90); plan.add(() -> shot(mc, "44_molds_cooled"));
        plan.add(() -> server(mc, l -> {
            BlockPos c = o.offset(6, 1, 38);
            com.holeydigsite.forge.CrucibleBlock.pour(l, c, l.getBlockState(c), null);
        }));
        plan.add(() -> look(mc, 7.5, 2.4, 41.0, 6.0, 0.8, 38.5)); idle(3); plan.add(() -> shot(mc, "45_crucible_tipping"));
        plan.add(() -> look(mc, 2.3, 1.3, 43.5, 2.3, 0.1, 41.9)); idle(30); plan.add(() -> shot(mc, "46_ingot_items"));
        idle(10);
    }
}
