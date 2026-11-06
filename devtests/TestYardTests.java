package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * GameTests for the Test Yard stations: verifies each station builder completes
 * without throwing and places its intended main block in the test area.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class TestYardTests {

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
        }
    }

    private static void testStation(GameTestHelper h, String builderKey, String mainBlockId, int[] offset) {
        clear(h);
        BlockPos origin = h.absolutePos(new BlockPos(10, 1, 2));
        try {
            TestYard.buildStation(h.getLevel(), origin, builderKey);
        } catch (Exception e) {
            h.fail("Station builder " + builderKey + " threw: " + e.getMessage());
            return;
        }

        BlockPos mainPos = origin.offset(offset[0], offset[1], offset[2]);
        BlockState state = h.getLevel().getBlockState(mainPos);
        Block expected = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(mainBlockId));
        check(h, state.is(expected), "Main block for " + builderKey + " at " + mainPos
                + " expected " + mainBlockId + " but was " + state);
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void station1DerrickDrill(GameTestHelper h) {
        testStation(h, "derrick_drill", "holeydigsite:mining_drill", new int[]{0, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station2LongSluice(GameTestHelper h) {
        testStation(h, "long_sluice", "holeydigsite:sluice_box_t2", new int[]{0, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station3SluiceModel(GameTestHelper h) {
        testStation(h, "sluice_model", "holeydigsite:sluice_model", new int[]{1, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station4Crates(GameTestHelper h) {
        testStation(h, "crates", "holeydigsite:crate_switch", new int[]{0, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station5SkinBench(GameTestHelper h) {
        testStation(h, "skin_bench", "holeydigsite:skin_bench", new int[]{0, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station6Contracts(GameTestHelper h) {
        testStation(h, "contracts", "holeydigsite:contract_board", new int[]{0, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station7FieldGuide(GameTestHelper h) {
        testStation(h, "field_guide", "minecraft:bookshelf", new int[]{0, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station8Farming(GameTestHelper h) {
        testStation(h, "farming", "holeydigsite:sprinkler", new int[]{0, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station9HempLine(GameTestHelper h) {
        testStation(h, "hemp_line", "holeydigsite:sawmill", new int[]{1, 0, 1});
    }

    @GameTest(template = "empty_long")
    public static void station10Selling(GameTestHelper h) {
        testStation(h, "selling", "holeydigsite:sell_pad", new int[]{-1, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station11Ledger(GameTestHelper h) {
        testStation(h, "ledger", "holeydigsite:lucky_lantern", new int[]{0, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station12DrillStatueMold(GameTestHelper h) {
        testStation(h, "drill_statue_mold", "holeydigsite:golden_drill_statue", new int[]{-2, 0, 0});
    }

    @GameTest(template = "empty_long")
    public static void station13ConveyorSkins(GameTestHelper h) {
        testStation(h, "conveyor_skins", "holeydigsite:skin_bench", new int[]{2, 0, -2});
    }

    @GameTest(template = "empty_long")
    public static void stationDefinitionsAndChestItemsParse(GameTestHelper h) {
        Path path = Path.of("tools", "yard", "stations.json").toAbsolutePath();
        if (!Files.isRegularFile(path)) {
            path = Path.of("..", "tools", "yard", "stations.json").toAbsolutePath().normalize();
        }
        try {
            List<TestYard.StationDef> stations = TestYard.loadStationDefs(path);
            check(h, stations.size() == 13, "Expected 12 stations, loaded " + stations.size());
            for (TestYard.StationDef st : stations) {
                check(h, !st.title().isEmpty(), "Empty title for station " + st.number());
                check(h, !st.signLines().isEmpty(), "No sign lines for station " + st.number());
                check(h, st.mainBlock() != null, "No main block for station " + st.number());
                var chestStacks = TestYard.buildChestStacks(h.getLevel(), st.chestItems());
                check(h, chestStacks.size() == st.chestItems().size(), "Chest items lost for station " + st.number());
                for (int i = 0; i < chestStacks.size(); i++) {
                    check(h, !chestStacks.get(i).isEmpty(), "Station " + st.number() + " chest item is not a real item: "
                            + st.chestItems().get(i).item());
                }
                check(h, !st.checks().isEmpty(), "No checks for station " + st.number());
                for (TestYard.ChestItemDef d : st.chestItems()) {
                    if (d.skin() != null) {
                        String family = d.family() != null ? d.family() : com.holeydigsite.skin.SkinDefs.DRILL;
                        check(h, com.holeydigsite.skin.SkinDefs.isFamily(family),
                                "Station " + st.number() + " kit has an unknown family: " + family);
                        check(h, com.holeydigsite.skin.SkinDefs.familiesOf(d.skin()).contains(family),
                                "Station " + st.number() + " kit " + d.skin() + " doesn't exist for " + family);
                    }
                }
            }
            h.succeed();
        } catch (Exception e) {
            h.fail("Failed to parse stations.json: " + e.getMessage());
        }
    }
}
