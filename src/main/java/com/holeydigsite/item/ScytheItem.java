package com.holeydigsite.item;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.site.SiteProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * Hand tool that harvests ripe crops in a 3x3 area and replants each from its own drops.
 * Unripe crops are left untouched. Counts for the Ledger milestone and advancement tracking.
 */
public class ScytheItem extends Item {
    public static final TagKey<Block> CROPS = TagKey.create(Registries.BLOCK, HoleyDigsite.id("crops"));

    public ScytheItem(Properties properties) {
        super(properties);
    }

    public static boolean isCrop(BlockState state) {
        return state.is(CROPS);
    }

    public static boolean isRipe(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.hasProperty(BlockStateProperties.AGE_7)) {
            return state.getValue(BlockStateProperties.AGE_7) == 7;
        }
        if (state.hasProperty(BlockStateProperties.AGE_3)) {
            return state.getValue(BlockStateProperties.AGE_3) == 3;
        }
        if (state.hasProperty(BlockStateProperties.AGE_5)) {
            return state.getValue(BlockStateProperties.AGE_5) == 5;
        }
        return false;
    }

    public static Item getSeedItem(BlockState state, Level level, BlockPos pos) {
        if (state.getBlock() instanceof CropBlock crop) {
            ItemStack clone = crop.getCloneItemStack(level, pos, state);
            if (!clone.isEmpty()) {
                return clone.getItem();
            }
        }
        return state.getBlock().asItem();
    }

    public static BlockState getResetState(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }
        if (state.hasProperty(BlockStateProperties.AGE_7)) {
            return state.setValue(BlockStateProperties.AGE_7, 0);
        }
        if (state.hasProperty(BlockStateProperties.AGE_3)) {
            return state.setValue(BlockStateProperties.AGE_3, 0);
        }
        if (state.hasProperty(BlockStateProperties.AGE_5)) {
            return state.setValue(BlockStateProperties.AGE_5, 0);
        }
        return state;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState centerState = level.getBlockState(center);

        if (!isCrop(centerState) || !isRipe(centerState)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel sl = (ServerLevel) level;
        int harvested = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos target = center.offset(dx, 0, dz);
                if (player != null && !PlotLock.canUse(player, sl, target)) {
                    continue;
                }
                if (player != null && SiteProtection.restricted(player) && SiteProtection.isProtected(sl, target)) {
                    SiteProtection.tell(player);
                    continue;
                }

                BlockState state = sl.getBlockState(target);
                if (!isCrop(state) || !isRipe(state)) {
                    continue;
                }

                List<ItemStack> drops = Block.getDrops(state, sl, target, sl.getBlockEntity(target), player, context.getItemInHand());
                Item seedItem = getSeedItem(state, sl, target);
                boolean replanted = false;

                if (seedItem != null) {
                    for (ItemStack drop : drops) {
                        if (drop.is(seedItem) && drop.getCount() > 0) {
                            drop.shrink(1);
                            replanted = true;
                            break;
                        }
                    }
                }

                if (replanted) {
                    sl.setBlock(target, getResetState(state), Block.UPDATE_ALL);
                } else {
                    sl.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }

                for (ItemStack drop : drops) {
                    if (drop.isEmpty()) continue;
                    if (player != null && player.getInventory().add(drop)) continue;
                    Block.popResource(sl, target, drop);
                }

                if (player instanceof ServerPlayer sp) {
                    LedgerManager.recordHarvest(sp, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                }
                harvested++;
            }
        }

        if (harvested > 0) {
            sl.playSound(null, center, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
            sl.playSound(null, center, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.2F);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 1, 0, 0, 0, 0);
            if (player != null) {
                player.swing(context.getHand(), true);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.scythe").withStyle(ChatFormatting.GRAY));
    }
}
