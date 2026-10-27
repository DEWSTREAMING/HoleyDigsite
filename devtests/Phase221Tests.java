package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.power.LavaIntakeBlock;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

/** Phase 22.1: crucible sync rate, plot owners by account id. */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase221Tests {
    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 10; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 0; z < 5; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static CrucibleBlockEntity hotLargeCrucible(GameTestHelper h) {
        clear(h);
        BlockPos forge = new BlockPos(4, 1, 2);
        BlockPos intake = new BlockPos(5, 1, 2);
        BlockPos cruc = new BlockPos(4, 2, 2);
        h.setBlock(forge, ModBlocks.HOT_FORGE.get().defaultBlockState().setValue(ForgeBlock.FACING, Direction.WEST));
        BlockPos abs = h.absolutePos(intake);
        BlockState s = ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(LavaIntakeBlock.LAVALOGGED, true);
        for (Direction dir : Direction.values()) {
            s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), WaterNetworkBlock.connects(h.getLevel(), abs, s, dir));
        }
        h.getLevel().setBlock(abs, s, Block.UPDATE_CLIENTS);
        WaterNetworks.recompute(h.getLevel(), Set.of(h.absolutePos(forge)));
        h.setBlock(cruc, ModBlocks.LARGE_CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.FACING, Direction.WEST));
        return (CrucibleBlockEntity) h.getBlockEntity(cruc);
    }

    /** A Large Crucible on a Hot Forge melts a gram every tick but tells clients only about every 10 ticks. */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void crucibleSyncIsThrottled(GameTestHelper h) {
        CrucibleBlockEntity c = hotLargeCrucible(h);
        c.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 64), 64, false);
        int[] start = new int[1];
        h.runAfterDelay(5, () -> {
            start[0] = c.syncCount();
            h.runAfterDelay(60, () -> {
                int sent = c.syncCount() - start[0];
                check(h, c.molten() >= 60, "melting went on: molten " + c.molten());
                check(h, sent >= 4 && sent <= 8, "about one update per 10 ticks over 60 ticks, got " + sent);
                h.succeed();
            });
        });
    }

    /** The last gram always reaches clients, and the crucible state ends up showing no chunks. */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void crucibleSyncsWhenMeltingEnds(GameTestHelper h) {
        CrucibleBlockEntity c = hotLargeCrucible(h);
        c.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 4), 4, false);
        h.runAfterDelay(45, () -> {
            check(h, c.unmelted() == 0 && c.molten() == 20, "all 20 g melted: " + c.molten() + "/" + c.unmelted());
            BlockState st = h.getBlockState(new BlockPos(4, 2, 2));
            check(h, !st.getValue(CrucibleBlock.CHUNKS), "block state shows no chunks left");
            h.succeed();
        });
    }

    private static BlockPos board(GameTestHelper h, int x) {
        return h.absolutePos(new BlockPos(x, 2, 1));
    }

    private static void plot(GameTestHelper h, String id, BlockPos b) {
        PlotData.get(h.getLevel()).register(id, b, new BoundingBox(b.getX(), b.getY(), b.getZ(), b.getX() + 3, b.getY() + 3, b.getZ() + 3));
    }

    /** Ownership follows the account id: a renamed owner is still the owner, a namesake is not. */
    @GameTest(template = "empty")
    public static void plotOwnerSurvivesRename(GameTestHelper h) {
        PlotData d = PlotData.get(h.getLevel());
        BlockPos b = board(h, 1);
        plot(h, "t221_a", b);
        UUID id = UUID.randomUUID();
        d.setOwned("t221_a", b, true, "OldName", id);
        d.syncOwner(id, "NewName");
        PlotData.Plot p = d.get("t221_a", b);
        check(h, p.isOwnedBy(id), "still owned by the account");
        check(h, "NewName".equals(p.owner()), "display name follows the rename: " + p.owner());
        check(h, !p.isOwnedBy(UUID.randomUUID()), "another account is not the owner");
        d.syncOwner(UUID.randomUUID(), "OldName");
        check(h, d.get("t221_a", b).isOwnedBy(id), "a namesake does not take over");
        d.remove("t221_a", b);
        h.succeed();
    }

    /** An old plot (name only) gets the id of the first matching player to join, and the id survives save and load. */
    @GameTest(template = "empty")
    public static void plotOwnerMigratesFromName(GameTestHelper h) {
        PlotData d = PlotData.get(h.getLevel());
        BlockPos b = board(h, 2);
        plot(h, "t221_b", b);
        d.setOwned("t221_b", b, true, "Digger");
        check(h, d.get("t221_b", b).ownerId() == null, "starts without an id");
        check(h, d.syncOwner(UUID.randomUUID(), "Someone") == 0, "other players change nothing");
        UUID id = UUID.randomUUID();
        check(h, d.syncOwner(id, "Digger") == 1, "the named player claims it");
        check(h, d.get("t221_b", b).isOwnedBy(id), "owned by that id now");
        check(h, d.syncOwner(UUID.randomUUID(), "Digger") == 0, "a later namesake does not re-claim it");
        PlotData copy = PlotData.load(d.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        PlotData.Plot p = copy.get("t221_b", b);
        check(h, p != null && p.isOwnedBy(id) && "Digger".equals(p.owner()), "id and name survive save and load");
        d.remove("t221_b", b);
        h.succeed();
    }

    /** Two players with two plots: each one owns only their own, by id. */
    @GameTest(template = "empty")
    public static void twoPlayersOwnTheirOwnPlots(GameTestHelper h) {
        PlotData d = PlotData.get(h.getLevel());
        ServerPlayer p1 = h.makeMockServerPlayerInLevel();
        ServerPlayer p2 = h.makeMockServerPlayerInLevel();
        BlockPos b1 = board(h, 3);
        BlockPos b2 = board(h, 6);
        plot(h, "t221_c", b1);
        plot(h, "t221_d", b2);
        d.setOwned("t221_c", b1, true, p1.getGameProfile().getName(), p1.getUUID());
        d.setOwned("t221_d", b2, true, p2.getGameProfile().getName(), p2.getUUID());
        check(h, d.get("t221_c", b1).isOwnedBy(p1.getUUID()) && !d.get("t221_c", b1).isOwnedBy(p2.getUUID()), "plot c belongs to player 1 only");
        check(h, d.get("t221_d", b2).isOwnedBy(p2.getUUID()) && !d.get("t221_d", b2).isOwnedBy(p1.getUUID()), "plot d belongs to player 2 only");
        d.remove("t221_c", b1);
        d.remove("t221_d", b2);
        h.succeed();
    }
}
