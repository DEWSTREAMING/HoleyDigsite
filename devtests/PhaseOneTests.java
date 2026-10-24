package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shovel.ShovelHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Sandbox-only GameTests for Phase 1 (not shipped). */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public class PhaseOneTests {
    private static final BlockPos P = new BlockPos(2, 1, 2);
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);

    private static ServerPlayer survivalPlayer(GameTestHelper h, ItemStack held) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 c = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 1, 0)));
        p.moveTo(c.x, c.y, c.z);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        return p;
    }

    private static void placeFromItem(GameTestHelper h, ServerPlayer p, ItemStack stack) {
        BlockPos floor = h.absolutePos(FLOOR);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        h.setBlock(FLOOR, Blocks.STONE); // the test template floor is not reliable
        stack.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
    }

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    @GameTest(template = "empty")
    public static void scoopDirtAndGrass(GameTestHelper h) {
        for (Block b : List.of(Blocks.DIRT, Blocks.GRASS_BLOCK)) {
            h.setBlock(P, b);
            ItemStack shovel = new ItemStack(Items.WOODEN_SHOVEL);
            ServerPlayer p = survivalPlayer(h, shovel);
            p.gameMode.destroyBlock(h.absolutePos(P));
            h.assertBlockPresent(Blocks.AIR, P);
            check(h, ShovelHelper.isFull(p.getMainHandItem()), "shovel should be full after scooping " + b);
            check(h, p.getMainHandItem().getDamageValue() == ShovelHelper.SCOOP_DURABILITY_COST,
                    "scoop should cost " + ShovelHelper.SCOOP_DURABILITY_COST + " durability (Phase 2: 0)");
        }
        h.assertItemEntityNotPresent(Items.DIRT);
        h.assertItemEntityNotPresent(Items.GRASS_BLOCK);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullShovelBreaksNormally(GameTestHelper h) {
        h.setBlock(P, Blocks.DIRT);
        ServerPlayer p = survivalPlayer(h, ShovelHelper.createFullShovel());
        p.gameMode.destroyBlock(h.absolutePos(P));
        h.assertBlockPresent(Blocks.AIR, P);
        check(h, ShovelHelper.isFull(p.getMainHandItem()), "full shovel should stay full");
        h.assertItemEntityPresent(Items.DIRT);
        // stone with an empty shovel is not scooped
        h.setBlock(P, Blocks.STONE);
        ServerPlayer p2 = survivalPlayer(h, new ItemStack(Items.WOODEN_SHOVEL));
        p2.gameMode.destroyBlock(h.absolutePos(P));
        check(h, !ShovelHelper.isFull(p2.getMainHandItem()), "stone must not fill the shovel");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fillPanToMax(GameTestHelper h) {
        h.setBlock(P, ModBlocks.PAN.get());
        ServerPlayer p = survivalPlayer(h, ItemStack.EMPTY);
        for (int i = 1; i <= PanBlock.MAX_FILL; i++) {
            p.setItemInHand(InteractionHand.MAIN_HAND, ShovelHelper.createFullShovel());
            h.useBlock(P, p);
            check(h, h.getBlockState(P).getValue(PanBlock.FILL) == i, "fill should be " + i);
            check(h, !ShovelHelper.isFull(p.getMainHandItem()), "shovel should be emptied");
            check(h, p.getMainHandItem().is(Items.WOODEN_SHOVEL), "shovel should remain a shovel");
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, ShovelHelper.createFullShovel());
        h.useBlock(P, p);
        check(h, h.getBlockState(P).getValue(PanBlock.FILL) == PanBlock.MAX_FILL, "fill must not exceed max");
        check(h, ShovelHelper.isFull(p.getMainHandItem()), "shovel must stay full when pan is full");
        // empty shovel does nothing
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
        h.useBlock(P, p);
        h.assertBlockPresent(ModBlocks.PAN.get(), P);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rightClickPickupAndReplace(GameTestHelper h) {
        h.setBlock(P, ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 3));
        ServerPlayer p = survivalPlayer(h, ItemStack.EMPTY);
        h.useBlock(P, p);
        h.assertBlockPresent(Blocks.AIR, P);
        int slot = p.getInventory().findSlotMatchingItem(ModBlocks.PAN.get().createStack(3));
        check(h, slot >= 0, "pan with fill 3 should be in inventory");
        ItemStack pan = p.getInventory().getItem(slot).copy();
        check(h, PanBlock.getFill(pan) == 3, "item fill should be 3");
        placeFromItem(h, p, pan);
        h.assertBlockPresent(ModBlocks.PAN.get(), P);
        check(h, h.getBlockState(P).getValue(PanBlock.FILL) == 3, "placed pan should keep fill 3");
        // holding a non-shovel item: no pickup
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        h.useBlock(P, p);
        h.assertBlockPresent(ModBlocks.PAN.get(), P);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void punchPickup(GameTestHelper h) {
        h.setBlock(P, ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 2));
        ServerPlayer p = survivalPlayer(h, new ItemStack(Items.STICK));
        p.gameMode.handleBlockBreakAction(h.absolutePos(P), ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.UP, h.getLevel().getMaxBuildHeight(), 0);
        h.assertBlockPresent(Blocks.AIR, P);
        check(h, p.getInventory().findSlotMatchingItem(ModBlocks.PAN.get().createStack(2)) >= 0, "punched pan in inventory");
        // creative too
        h.setBlock(P, ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 1));
        ServerPlayer c = survivalPlayer(h, ItemStack.EMPTY);
        c.setGameMode(GameType.CREATIVE);
        c.gameMode.handleBlockBreakAction(h.absolutePos(P), ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.UP, h.getLevel().getMaxBuildHeight(), 0);
        h.assertBlockPresent(Blocks.AIR, P);
        check(h, c.getInventory().findSlotMatchingItem(ModBlocks.PAN.get().createStack(1)) >= 0, "creative punch picks up");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void waterlogging(GameTestHelper h) {
        ServerPlayer p = survivalPlayer(h, ItemStack.EMPTY);
        // source water
        h.setBlock(P, Blocks.WATER);
        placeFromItem(h, p, new ItemStack(ModItems.PAN.get()));
        h.assertBlockPresent(ModBlocks.PAN.get(), P);
        check(h, h.getBlockState(P).getValue(PanBlock.WATERLOGGED), "pan in source water should be waterlogged");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.useBlock(P, p);
        h.assertBlockPresent(Blocks.WATER, P);
        // flowing water
        h.setBlock(P, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3));
        placeFromItem(h, p, new ItemStack(ModItems.PAN.get()));
        h.assertBlockPresent(ModBlocks.PAN.get(), P);
        check(h, h.getBlockState(P).getValue(PanBlock.WATERLOGGED), "pan in flowing water should be waterlogged");
        // dry
        h.setBlock(P, Blocks.AIR);
        placeFromItem(h, p, new ItemStack(ModItems.PAN.get()));
        check(h, !h.getBlockState(P).getValue(PanBlock.WATERLOGGED), "dry pan not waterlogged");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lootKeepsFill(GameTestHelper h) {
        BlockState two = ModBlocks.PAN.get().defaultBlockState().setValue(PanBlock.FILL, 2);
        h.setBlock(P, two);
        List<ItemStack> drops = Block.getDrops(two, h.getLevel(), h.absolutePos(P), null);
        check(h, drops.size() == 1 && PanBlock.getFill(drops.get(0)) == 2, "loot should keep fill 2: " + drops);
        BlockState zero = ModBlocks.PAN.get().defaultBlockState();
        List<ItemStack> drops0 = Block.getDrops(zero, h.getLevel(), h.absolutePos(P), null);
        check(h, drops0.size() == 1 && ItemStack.isSameItemSameComponents(drops0.get(0), new ItemStack(ModItems.PAN.get())),
                "empty pan drop should be a plain pan: " + drops0);
        h.succeed();
    }
}
