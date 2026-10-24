package com.holeydigsite.item;

import com.holeydigsite.tier.DirtTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Prospector's Pick (Phase 16, Deep Dig kiosk): right-click the ground to find the nearest buried Deep or Abyssal Dirtrock within
 * {@link #RANGE} blocks - particles at the rock, a sound and an action-bar line such as "Abyssal rock: ~6 blocks down-east".
 * Only scans when used (a cube of 25^3 blocks, nearest wins), has a short cooldown and never wears out.
 */
public class ProspectorPickItem extends Item {
    public static final int RANGE = 12;
    public static final int COOLDOWN_TICKS = 20;

    public ProspectorPickItem(Properties properties) {
        super(properties);
    }

    /** One scan result: where, which kind (2 = Deep, 3 = Abyssal) and the offset from the scan origin. */
    public record Find(BlockPos pos, int kind, int dx, int dy, int dz) {
        public double distance() {
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** The nearest Deep / Abyssal Dirtrock within {@link #RANGE} blocks of {@code origin}, or null. */
    @Nullable
    public static Find scan(Level level, BlockPos origin) {
        Find best = null;
        long bestSq = (long) RANGE * RANGE + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -RANGE; dy <= RANGE; dy++) {
            for (int dx = -RANGE; dx <= RANGE; dx++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    long sq = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (sq >= bestSq) {
                        continue;
                    }
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    int kind = s.is(DirtTier.ROCK_TIER_3) ? 3 : s.is(DirtTier.ROCK_TIER_2) ? 2 : 0;
                    if (kind != 0) {
                        bestSq = sq;
                        best = new Find(p.immutable(), kind, dx, dy, dz);
                    }
                }
            }
        }
        return best;
    }

    private static final String[] COMPASS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};

    /** "down-east", "north-west", "up", ... built from lang entries. */
    public static Component direction(Find f) {
        int horizontal = Math.max(Math.abs(f.dx()), Math.abs(f.dz()));
        String vertical = f.dy() <= -2 ? "down" : f.dy() >= 2 ? "up" : null;
        String compass = null;
        if (horizontal >= 2) {
            // 8 sectors; +x = east, -z = north
            int sector = Math.floorMod((int) Math.round(Math.atan2(f.dx(), -f.dz()) / (Math.PI / 4)), 8);
            compass = COMPASS[sector];
        }
        if (vertical == null && compass == null) {
            return Component.translatable("message.holeydigsite.prospect.dir.here");
        }
        if (vertical == null) {
            return Component.translatable("message.holeydigsite.prospect.dir." + compass);
        }
        if (compass == null) {
            return Component.translatable("message.holeydigsite.prospect.dir." + vertical);
        }
        return Component.translatable("message.holeydigsite.prospect.dir.combined",
                Component.translatable("message.holeydigsite.prospect.dir." + vertical),
                Component.translatable("message.holeydigsite.prospect.dir." + compass));
    }

    /** The action-bar line for a scan result (or "Nothing nearby"). Public for tests. */
    public static Component message(@Nullable Find f) {
        if (f == null) {
            return Component.translatable("message.holeydigsite.prospect.none").withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable("message.holeydigsite.prospect.found",
                Component.translatable("message.holeydigsite.prospect.rock." + f.kind()),
                Math.max(1, (int) Math.round(f.distance())), direction(f))
                .withStyle(f.kind() == 3 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD);
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
        if (level instanceof ServerLevel sl) {
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            Find f = scan(level, context.getClickedPos());
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.prospectors_pick").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.prospectors_pick.how", RANGE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
