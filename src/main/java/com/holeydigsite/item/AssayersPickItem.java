package com.holeydigsite.item;

import com.holeydigsite.tier.DirtTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Assayer's Pick made in the Crafting Forge. Right-click the ground to find the nearest buried Deep, Abyssal,
 * Gravelrock, or Sandrock within {@link #RANGE} blocks. Sneak + use counts all four rock types within range.
 * Only scans when used, never ticks.
 */
public class AssayersPickItem extends ProspectorPickItem {
    public static final int RANGE = 20;

    public AssayersPickItem(Properties properties) {
        super(properties);
    }

    /** Counts of each rock type in range. */
    public record RockCounts(int abyssal, int deep, int sand, int gravel) {
        public int total() {
            return abyssal + deep + sand + gravel;
        }
    }

    /** Finds the nearest rock of any of the 4 types (Deep, Abyssal, Gravelrock, Sandrock) within {@link #RANGE} blocks. */
    @Nullable
    public static Find scanAll(Level level, BlockPos origin) {
        return scanAll(level, origin, RANGE);
    }

    /** Finds the nearest rock of any of the 4 types within {@code range} blocks. */
    @Nullable
    public static Find scanAll(Level level, BlockPos origin, int range) {
        Find best = null;
        long bestSq = (long) range * range + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    long sq = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (sq >= bestSq) {
                        continue;
                    }
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    int kind = s.is(DirtTier.ROCK_TIER_3) ? 3
                            : s.is(DirtTier.ROCK_TIER_2) ? 2
                            : s.is(DirtTier.ROCK_GRAVEL) ? DirtTier.GRAVEL
                            : s.is(DirtTier.ROCK_SAND) ? DirtTier.SAND : 0;
                    if (kind != 0) {
                        bestSq = sq;
                        best = new Find(p.immutable(), kind, dx, dy, dz);
                    }
                }
            }
        }
        return best;
    }

    /** Counts all buried rock types within {@link #RANGE} blocks of the origin. */
    public static RockCounts countScan(Level level, BlockPos origin) {
        return countScan(level, origin, RANGE);
    }

    /** Counts all buried rock types within {@code range} blocks of the origin. */
    public static RockCounts countScan(Level level, BlockPos origin, int range) {
        int abyssal = 0, deep = 0, sand = 0, gravel = 0;
        long maxSq = (long) range * range;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    long sq = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (sq > maxSq) {
                        continue;
                    }
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (s.is(DirtTier.ROCK_TIER_3)) {
                        abyssal++;
                    } else if (s.is(DirtTier.ROCK_TIER_2)) {
                        deep++;
                    } else if (s.is(DirtTier.ROCK_SAND)) {
                        sand++;
                    } else if (s.is(DirtTier.ROCK_GRAVEL)) {
                        gravel++;
                    }
                }
            }
        }
        return new RockCounts(abyssal, deep, sand, gravel);
    }

    /** Action-bar message showing the counts of each rock type. */
    public static Component countsMessage(RockCounts counts) {
        return Component.translatable("text.holeydigsite.assayer.counts",
                counts.abyssal(), counts.deep(), counts.sand(), counts.gravel())
                .withStyle(ChatFormatting.AQUA);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            if (level instanceof ServerLevel sl) {
                player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                RockCounts counts = countScan(level, context.getClickedPos(), RANGE);
                player.displayClientMessage(countsMessage(counts), true);
                sl.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel sl) {
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            Find f = scanAll(level, context.getClickedPos(), RANGE);
            player.displayClientMessage(message(f), true);
            if (f != null) {
                double x = f.pos().getX() + 0.5, y = f.pos().getY() + 0.5, z = f.pos().getZ() + 0.5;
                sl.sendParticles(ParticleTypes.GLOW, x, y, z, 12, 0.4, 0.4, 0.4, 0.02);
                sl.sendParticles(ParticleTypes.WAX_ON, x, y, z, 6, 0.4, 0.4, 0.4, 0.0);
                sl.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F,
                        f.kind() == 3 ? 1.6F : 1.2F);
            } else {
                sl.playSound(null, context.getClickedPos(), SoundEvents.GRAVEL_HIT, SoundSource.PLAYERS, 0.6F, 0.8F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pick = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(pick);
        }
        if (player.isShiftKeyDown()) {
            if (level instanceof ServerLevel sl) {
                player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                RockCounts counts = countScan(level, player.blockPosition(), RANGE);
                player.displayClientMessage(countsMessage(counts), true);
                sl.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
            }
            return InteractionResultHolder.sidedSuccess(pick, level.isClientSide);
        }
        return InteractionResultHolder.pass(pick);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.holeydigsite.assayers_pick").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.assayers_pick.how", RANGE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
