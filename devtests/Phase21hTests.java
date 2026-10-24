package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.forge.CastingMoldBlock;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.CrucibleBlockEntity;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.forge.LargeCrucibleBlock;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.power.LavaIntakeBlock;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/**
 * Phase 21h (sandbox only): Bug fix - crucible label freezes while melting:
 * <ul>
 *   <li>Crucible filled to capacity with chunks on a lit forge melts over time with no player interaction</li>
 *   <li>The data sent to clients (update tag / update packet) continuously reflects grams moving from melting to molten</li>
 *   <li>Rejected inserts (crucible full) never leave state corrupted or half-updated</li>
 *   <li>Works for both basic Crucible and Large Crucible on Hot Forge</li>
 * </ul>
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21hTests {
    private static final ResourceLocation FOLKIRON = HoleyDigsite.id("folkiron");

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 26; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 0; z < 5; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private record Kit(int dx) {
        BlockPos forge() { return new BlockPos(4 + dx, 1, 2); }
        BlockPos intake() { return new BlockPos(5 + dx, 1, 2); }
        BlockPos cruc() { return new BlockPos(4 + dx, 2, 2); }
        BlockPos mold() { return new BlockPos(3 + dx, 1, 2); }
    }

    private static void placeIntake(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        BlockState s = ModBlocks.LAVA_INTAKE.get().defaultBlockState().setValue(LavaIntakeBlock.LAVALOGGED, true);
        for (Direction dir : Direction.values()) {
            s = s.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), WaterNetworkBlock.connects(h.getLevel(), abs, s, dir));
        }
        h.getLevel().setBlock(abs, s, Block.UPDATE_CLIENTS);
    }

    private static Kit kit(GameTestHelper h, int dx, boolean hot, boolean large) {
        Kit k = new Kit(dx);
        h.setBlock(k.forge(), (hot ? ModBlocks.HOT_FORGE.get() : ModBlocks.FORGE.get()).defaultBlockState().setValue(ForgeBlock.FACING, Direction.WEST));
        placeIntake(h, k.intake());
        WaterNetworks.recompute(h.getLevel(), Set.of(h.absolutePos(k.forge())));
        h.setBlock(k.cruc(), (large ? ModBlocks.LARGE_CRUCIBLE.get() : ModBlocks.CRUCIBLE.get()).defaultBlockState().setValue(CrucibleBlock.FACING, Direction.WEST));
        h.setBlock(k.mold(), ModBlocks.MOLD.get().defaultBlockState());
        return k;
    }

    private static CrucibleBlockEntity cru(GameTestHelper h, Kit k) {
        return (CrucibleBlockEntity) h.getBlockEntity(k.cruc());
    }

    /**
     * Fills a crucible to capacity (200 g), verifies rejected insert leaves state clean,
     * lets time pass with NO interaction, and verifies the update tag/packet reflects grams melting.
     */
    @GameTest(template = "empty_long", timeoutTicks = 200)
    public static void crucibleLabelSyncsWhileMeltingWithoutInteraction(GameTestHelper h) {
        clear(h);
        Kit k = kit(h, 0, false, false);
        CrucibleBlockEntity c = cru(h, k);

        // Fill to capacity with 200 g of Folkiron chunks (40 * 5 g = 200 g)
        int taken = c.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 40), 40, false);
        check(h, taken == 40 && c.total() == 200 && c.unmelted() == 200 && c.molten() == 0,
                "crucible filled to 200 g capacity with chunks: " + c.total());

        // A rejected insert (crucible full) must leave state completely untouched
        ItemStack extra = new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get(), 1); // 12 g chunk
        int rejected = c.insert(extra, 1, false);
        check(h, rejected == 0, "extra insert rejected when full");
        check(h, c.total() == 200 && c.unmelted() == 200 && c.molten() == 0, "state untouched by rejected insert");

        // Initial update tag check
        CompoundTag tag0 = c.getUpdateTag(h.getLevel().registryAccess());
        check(h, tag0.getInt("Unmelted") == 200 && tag0.getInt("Molten") == 0,
                "initial update tag has 200 g unmelted, 0 g molten: " + tag0);

        // After 20 ticks (1 second), with 4 ticks/gram at heat 1, 5 g should have melted
        h.runAfterDelay(20, () -> {
            check(h, c.molten() == 5 && c.unmelted() == 195,
                    "server state after 20 ticks: molten " + c.molten() + ", unmelted " + c.unmelted());

            // The data a client would receive from getUpdateTag / getUpdatePacket
            CompoundTag tag1 = c.getUpdateTag(h.getLevel().registryAccess());
            check(h, tag1.getInt("Molten") == 5 && tag1.getInt("Unmelted") == 195,
                    "update tag after 20 ticks shows 5 g molten, 195 g unmelted: " + tag1);

            ClientboundBlockEntityDataPacket pkt = (ClientboundBlockEntityDataPacket) c.getUpdatePacket();
            check(h, pkt != null && pkt.getTag() != null && pkt.getTag().getInt("Molten") == 5,
                    "update packet carries 5 g molten");

            // Client block entity loading this tag reflects the exact molten/unmelted grams
            CrucibleBlockEntity clientBe = new CrucibleBlockEntity(h.absolutePos(k.cruc()), h.getBlockState(k.cruc()));
            clientBe.loadWithComponents(tag1, h.getLevel().registryAccess());
            check(h, clientBe.molten() == 5 && clientBe.unmelted() == 195,
                    "client copy reflects live melting: molten " + clientBe.molten() + ", unmelted " + clientBe.unmelted());

            // Rejecting an insert mid-melt still leaves state untouched
            int midReject = c.insert(extra, 1, false);
            check(h, midReject == 0, "mid-melt insert still rejected");
            check(h, c.total() == 200, "total remains 200");

            // After 40 more ticks (total 60 ticks = 3 seconds), 15 g should have melted
            h.runAfterDelay(40, () -> {
                check(h, c.molten() == 15 && c.unmelted() == 185,
                        "server state after 60 ticks: molten " + c.molten() + ", unmelted " + c.unmelted());

                CompoundTag tag2 = c.getUpdateTag(h.getLevel().registryAccess());
                check(h, tag2.getInt("Molten") == 15 && tag2.getInt("Unmelted") == 185,
                        "update tag after 60 ticks shows 15 g molten, 185 g unmelted: " + tag2);

                clientBe.loadWithComponents(tag2, h.getLevel().registryAccess());
                check(h, clientBe.molten() == 15 && clientBe.unmelted() == 185,
                        "client copy updated to 15 g molten");

                h.succeed();
            });
        });
    }

    /**
     * Tests that a Large Crucible (500 g) on a Hot Forge (heat 2) melts at 1 g/tick and syncs update tags continuously.
     */
    @GameTest(template = "empty_long", timeoutTicks = 100)
    public static void largeCrucibleHotForgeSyncsWhileMelting(GameTestHelper h) {
        clear(h);
        Kit k = kit(h, 0, true, true);
        CrucibleBlockEntity c = cru(h, k);

        // Fill to capacity with 500 g of Folkiron chunks (64 + 36 = 100 chunks * 5 g = 500 g)
        int taken1 = c.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 64), 64, false); // 320 g
        int taken2 = c.insert(new ItemStack(ModItems.FOLKIRON_CHUNK_MEDIUM.get(), 36), 36, false); // 180 g
        check(h, taken1 == 64 && taken2 == 36 && c.total() == 500 && c.unmelted() == 500 && c.molten() == 0,
                "large crucible filled to 500 g with chunks");

        // ticksPerGram = 2, heat = 2 -> 1 gram melts every tick
        h.runAfterDelay(25, () -> {
            check(h, c.molten() == 25 && c.unmelted() == 475,
                    "large crucible at 25 ticks: molten " + c.molten() + ", unmelted " + c.unmelted());

            CompoundTag tag = c.getUpdateTag(h.getLevel().registryAccess());
            check(h, tag.getInt("Molten") == 25 && tag.getInt("Unmelted") == 475,
                    "large crucible update tag shows 25 g molten: " + tag);

            CrucibleBlockEntity clientBe = new CrucibleBlockEntity(h.absolutePos(k.cruc()), h.getBlockState(k.cruc()));
            clientBe.loadWithComponents(tag, h.getLevel().registryAccess());
            check(h, clientBe.molten() == 25 && clientBe.unmelted() == 475,
                    "client copy updated: " + clientBe.molten());

            h.succeed();
        });
    }
}
