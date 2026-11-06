package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.TrophyStandBlockEntity;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.skin.SkinBenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The client only applies an update tag that isn't empty, so a block that has just been emptied must still send one.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public final class SyncTests {
    private SyncTests() {}

    private static CompoundTag updateTag(GameTestHelper h, BlockEntity be) {
        return be.getUpdateTag(h.getLevel().registryAccess());
    }

    @GameTest(template = "empty")
    public static void emptiedSkinBenchStillSyncs(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, ModBlocks.SKIN_BENCH.get());
        SkinBenchBlockEntity bench = (SkinBenchBlockEntity) h.getBlockEntity(pos);
        bench.setItem(new ItemStack(ModItems.MINING_DRILL.get()));
        CompoundTag full = updateTag(h, bench);
        bench.takeItem();
        CompoundTag empty = updateTag(h, bench);
        h.assertTrue(!empty.isEmpty(), "Empty bench sends an empty update tag");

        // What the client does: load the full tag, then the emptied one.
        SkinBenchBlockEntity client = new SkinBenchBlockEntity(h.absolutePos(pos), h.getBlockState(pos));
        client.loadWithComponents(full, h.getLevel().registryAccess());
        h.assertTrue(client.hasItem(), "Client copy should show the drill first");
        client.loadWithComponents(empty, h.getLevel().registryAccess());
        h.assertTrue(!client.hasItem(), "Client copy still shows the drill after it was taken");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void otherBlocksNeverSendAnEmptyUpdateTag(GameTestHelper h) {
        BlockPos drill = new BlockPos(1, 1, 1);
        h.setBlock(drill.below(), ModBlocks.DIRTROCK.get());
        h.setBlock(drill, ModBlocks.MINING_DRILL.get());
        h.assertTrue(!updateTag(h, (MiningDrillBlockEntity) h.getBlockEntity(drill)).isEmpty(),
                "Drill with no crate and no skin sends an empty update tag");

        BlockPos stand = new BlockPos(3, 1, 1);
        h.setBlock(stand, ModBlocks.WELCOME_STRANGER_STAND.get());
        h.assertTrue(!updateTag(h, (TrophyStandBlockEntity) h.getBlockEntity(stand)).isEmpty(),
                "Empty trophy stand sends an empty update tag");

        BlockPos display = new BlockPos(1, 1, 3);
        h.setBlock(display, ModBlocks.SHOP_DISPLAY.get());
        h.assertTrue(!updateTag(h, (ShopDisplayBlockEntity) h.getBlockEntity(display)).isEmpty(),
                "Unset shop display sends an empty update tag");
        h.succeed();
    }
}
