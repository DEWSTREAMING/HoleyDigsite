package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.plot.PlotBoardBlock;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 21c (sandbox only): Tests admin and operator commands under {@code /holeydigsite}:
 * ledger renown/level/milestone/claim/title/caps/reset, money give, give ingot/gem/clump/piece,
 * worth, plot own/unown, and info/version outputs.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21cTests {

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

    private static void exec(GameTestHelper h, CommandSourceStack source, String cmd) {
        h.getLevel().getServer().getCommands().performPrefixedCommand(source, cmd);
    }

    // ------------------------------------------------------------------------------------------------ 1: Ledger Renown & Level

    @GameTest(template = "empty")
    public static void ledgerRenownAndLevel(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);
        LedgerData data = LedgerData.get(p);

        exec(h, source, "holeydigsite ledger renown set @s 250");
        check(h, data.renown() == 250, "Renown should be 250, was " + data.renown());

        exec(h, source, "holeydigsite ledger renown add @s 100");
        check(h, data.renown() == 350, "Renown should be 350, was " + data.renown());

        exec(h, source, "holeydigsite ledger level set @s 5");
        int expectedRenown = LedgerDefs.curve().cumulativeForLevel(5);
        check(h, data.level() == 5, "Level should be 5, was " + data.level());
        check(h, data.renown() == expectedRenown, "Renown should match level 5 cost (" + expectedRenown + "), was " + data.renown());

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 2: Ledger Milestones

    @GameTest(template = "empty")
    public static void ledgerMilestones(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);
        LedgerData data = LedgerData.get(p);

        exec(h, source, "holeydigsite ledger milestone grant @s first_pan");
        check(h, data.isMilestoneCompleted(HoleyDigsite.id("first_pan")), "first_pan should be completed");
        int renownGain = LedgerDefs.milestone(HoleyDigsite.id("first_pan")).renown();
        check(h, data.renown() >= renownGain, "Renown should include first_pan reward");

        int curRenown = data.renown();
        exec(h, source, "holeydigsite ledger milestone revoke @s first_pan");
        check(h, !data.isMilestoneCompleted(HoleyDigsite.id("first_pan")), "first_pan should be revoked");
        check(h, data.renown() == curRenown - renownGain, "Renown should decrease by first_pan reward");

        exec(h, source, "holeydigsite ledger milestone grant @s all");
        check(h, data.completedMilestones().size() == LedgerDefs.milestones().size(), "All milestones should be completed");

        exec(h, source, "holeydigsite ledger milestone revoke @s all");
        check(h, data.completedMilestones().isEmpty(), "All milestones should be revoked");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 3: Ledger Claims

    @GameTest(template = "empty")
    public static void ledgerClaims(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);
        LedgerData data = LedgerData.get(p);

        exec(h, source, "holeydigsite ledger level set @s 5");
        exec(h, source, "holeydigsite ledger claim @s 1");
        check(h, data.isLevelClaimed(1), "Level 1 should be claimed");

        exec(h, source, "holeydigsite ledger unclaim @s 1");
        check(h, !data.isLevelClaimed(1), "Level 1 should be unclaimed");

        exec(h, source, "holeydigsite ledger claim @s all");
        check(h, data.isLevelClaimed(1) && data.isLevelClaimed(2) && data.isLevelClaimed(3), "Levels 1-3 should be claimed");

        exec(h, source, "holeydigsite ledger unclaim @s all");
        check(h, data.claimedLevels().isEmpty(), "All levels should be unclaimed");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 4: Ledger Titles

    @GameTest(template = "empty")
    public static void ledgerTitles(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);
        LedgerData data = LedgerData.get(p);

        exec(h, source, "holeydigsite ledger title unlock @s greenhorn");
        check(h, data.hasTitle("greenhorn"), "greenhorn should be unlocked");

        exec(h, source, "holeydigsite ledger title set @s greenhorn");
        check(h, "greenhorn".equals(data.activeTitle()), "active title should be greenhorn");

        exec(h, source, "holeydigsite ledger title set @s none");
        check(h, data.activeTitle().isEmpty(), "active title should be empty");

        exec(h, source, "holeydigsite ledger title lock @s greenhorn");
        check(h, !data.hasTitle("greenhorn"), "greenhorn should be locked");

        exec(h, source, "holeydigsite ledger title unlock @s all");
        check(h, data.unlockedTitles().size() == LedgerDefs.titles().size(), "all titles should be unlocked");

        exec(h, source, "holeydigsite ledger title lock @s all");
        check(h, data.unlockedTitles().isEmpty(), "all titles should be locked");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 5: Caps & Reset

    @GameTest(template = "empty")
    public static void ledgerCapsAndReset(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);
        LedgerData data = LedgerData.get(p);

        data.addPanProgress(10, LedgerDefs.curve().pan());
        check(h, data.panRenown() > 0, "panRenown should be > 0");
        int r = data.renown();

        exec(h, source, "holeydigsite ledger caps reset @s");
        check(h, data.panRenown() == 0, "panRenown should be 0 after caps reset");
        check(h, data.renown() == r, "renown should be preserved on caps reset");

        exec(h, source, "holeydigsite ledger reset @s");
        check(h, data.renown() == r, "reset without confirm should not change data");

        exec(h, source, "holeydigsite ledger reset @s confirm");
        check(h, data.renown() == 0, "reset with confirm should wipe renown");
        check(h, data.completedMilestones().isEmpty(), "reset should wipe milestones");
        check(h, data.unlockedTitles().isEmpty(), "reset should wipe titles");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 6: Economy Money

    @GameTest(template = "empty")
    public static void economyMoney(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);

        // 137 copper = 2 gold (128) + 1 silver (8) + 1 copper (1)
        exec(h, source, "holeydigsite money give @s 137");

        int gold = p.getInventory().countItem(ModItems.GOLD_COIN.get());
        int silver = p.getInventory().countItem(ModItems.SILVER_COIN.get());
        int copper = p.getInventory().countItem(ModItems.COPPER_COIN.get());

        check(h, gold == 2, "Should have 2 gold coins, got " + gold);
        check(h, silver == 1, "Should have 1 silver coin, got " + silver);
        check(h, copper == 1, "Should have 1 copper coin, got " + copper);

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 7: Economy Give

    @GameTest(template = "empty")
    public static void economyGive(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);

        // Ingot with weight
        exec(h, source, "holeydigsite give ingot @s goldchro 50 2");
        check(h, p.getInventory().countItem(ModItems.GOLDCHRO_INGOT.get()) == 2, "Should have 2 goldchro ingots");
        ItemStack ingotStack = p.getInventory().getItem(0);
        check(h, ingotStack.getOrDefault(ModDataComponents.WEIGHT.get(), 0) == 50, "Ingot weight should be 50g");

        // Gem cut and uncut
        exec(h, source, "holeydigsite give gem @s sapphire large 3");
        check(h, p.getInventory().countItem(ModItems.SAPPHIRE_LARGE.get()) == 3, "Should have 3 large sapphires");

        exec(h, source, "holeydigsite give gem @s ruby medium uncut 2");
        check(h, p.getInventory().countItem(BuiltInRegistries.ITEM.get(HoleyDigsite.id("uncut_ruby_medium"))) == 2, "Should have 2 uncut medium rubies");

        // Clump tiers
        exec(h, source, "holeydigsite give clump @s deep 5");
        check(h, p.getInventory().countItem(ModItems.DIRT_CLUMP.get()) == 5, "Should have 5 deep dirt clumps");

        exec(h, source, "holeydigsite give clump @s gravel 4");
        check(h, p.getInventory().countItem(ModItems.GRAVEL_CLUMP.get()) == 4, "Should have 4 gravel clumps");

        // Smithed piece: sword (3 folkiron ingots @ 150g = 450g)
        exec(h, source, "holeydigsite give piece @s sword folkiron 150");
        check(h, p.getInventory().countItem(ModItems.SMITH_SWORD.get()) == 1, "Should have 1 smithed sword");
        ItemStack swordStack = ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModItems.SMITH_SWORD.get())) {
                swordStack = p.getInventory().getItem(i);
                break;
            }
        }
        SmithData swordData = SmithPieces.data(swordStack);
        check(h, swordData != null, "Sword should have smith data component");
        check(h, swordData.ingots().size() == 3, "Sword should have 3 ingots");
        check(h, swordData.ingotGrams() == 450, "Sword should have 450g total ingots");
        check(h, swordStack.has(DataComponents.MAX_DAMAGE), "Sword should have durability component");

        // Smithed piece with gem: necklace (2 silvane ingots @ 100g + sapphire)
        exec(h, source, "holeydigsite give piece @s necklace silvane 100 sapphire");
        check(h, p.getInventory().countItem(ModItems.SMITH_NECKLACE.get()) == 1, "Should have 1 smithed necklace");
        ItemStack neckStack = ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModItems.SMITH_NECKLACE.get())) {
                neckStack = p.getInventory().getItem(i);
                break;
            }
        }
        SmithData neckData = SmithPieces.data(neckStack);
        check(h, neckData != null && neckData.gem().isPresent(), "Necklace should have smith data with gem");
        check(h, neckData.gem().get().material().equals(HoleyDigsite.id("sapphire")), "Necklace gem should be sapphire");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 8: Economy Worth

    @GameTest(template = "empty")
    public static void economyWorth(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);

        ItemStack ingot = new ItemStack(ModItems.GOLDCHRO_INGOT.get(), 1);
        ingot.set(ModDataComponents.WEIGHT.get(), 50);
        p.setItemInHand(InteractionHand.MAIN_HAND, ingot);

        exec(h, source, "holeydigsite worth @s");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 9: Plot Own / Unown

    @GameTest(template = "empty")
    public static void plotOwnUnown(GameTestHelper h) {
        ServerPlayer p = player(h);
        String name = p.getGameProfile().getName();
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);

        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos pos = h.absolutePos(rel);
        h.setBlock(rel, ModBlocks.PLOT_BOARD.get().defaultBlockState());
        PlotData.get(h.getLevel()).register("deep_dig", pos, new BoundingBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 5, pos.getY() + 5, pos.getZ() + 5));

        exec(h, source, "holeydigsite plot own deep_dig @s");
        PlotData.Plot plot = PlotData.get(h.getLevel()).get("deep_dig", pos);
        check(h, plot != null && plot.owned(), "Plot should be owned");
        check(h, name.equals(plot.owner()), "Plot owner should be " + name);
        check(h, h.getBlockState(rel).getValue(PlotBoardBlock.OWNED), "Plot board block should be marked owned");

        exec(h, source, "holeydigsite plot unown deep_dig");
        PlotData.Plot plot2 = PlotData.get(h.getLevel()).get("deep_dig", pos);
        check(h, plot2 != null && !plot2.owned(), "Plot should be unowned");
        check(h, !h.getBlockState(rel).getValue(PlotBoardBlock.OWNED), "Plot board block should not be marked owned");

        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ 10: Info & Version Output

    @GameTest(template = "empty")
    public static void infoAndVersionOutputs(GameTestHelper h) {
        ServerPlayer p = player(h);
        CommandSourceStack source = p.createCommandSourceStack().withPermission(2);

        exec(h, source, "holeydigsite ledger info @s");
        exec(h, source, "holeydigsite ledger milestone list @s");
        exec(h, source, "holeydigsite plot list");
        exec(h, source, "holeydigsite world version");

        h.succeed();
    }
}
