package com.holeydigsite.panning;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Panning a filled, waterlogged pan with the {@link com.holeydigsite.item.BrushItem Brush}.
 *
 * <p>Flow:
 * <ol>
 *   <li>Right-click the pan with the brush ({@link PanBlock#useItemOn} or {@code BrushItem#useOn} when sneaking)
 *       -> {@link #tryStartPanning}: checks water + fill, remembers which pan is targeted and starts the
 *       use-item animation (UseAnim.BRUSH, like the vanilla brush).</li>
 *   <li>Every tick while right-click is held -> {@link #tickPanning}: cancels if the player looks away
 *       from that pan or the pan can no longer be panned; plays swish/splash feedback; every
 *       {@link #PAN_CYCLE_TICKS} ticks completes one cycle on the server ({@link #completeCycle}).</li>
 *   <li>A completed cycle removes 1 fill level, rolls the pan tier's loot table once and pops the loot out
 *       on top of the pan. Holding on continues with the next scoop until the pan is empty.</li>
 * </ol>
 * Releasing right-click early simply stops using the item: progress is only counted in use-ticks,
 * so a partial cycle gives no loot and changes nothing.</p>
 *
 * <p>The tick logic runs on BOTH sides (like vanilla's brush): the client does particles, local sounds and
 * its own cancel checks; only the server changes the block and rolls loot.</p>
 */
public final class Panning {
    private Panning() {}

    /** Ticks of holding right-click per pan cycle (one scoop). 40 ticks = 2 seconds. */
    public static final int PAN_CYCLE_TICKS = 40;

    /** Ticks per scoop for the brush in use: {@link #PAN_CYCLE_TICKS}, 20 with the Goldchro Brush (Phase 10). */
    public static int cycleTicks(net.minecraft.world.item.ItemStack brush) {
        return brush.getItem() instanceof com.holeydigsite.item.BrushItem b ? b.cycleTicks() : PAN_CYCLE_TICKS;
    }

    /** Phase 18: the same, {@link com.holeydigsite.effect.ModEffects#PAN_SPEEDUP_PERCENT}% shorter while the player has Steady Hands. */
    public static int cycleTicks(LivingEntity entity, net.minecraft.world.item.ItemStack brush) {
        int base = cycleTicks(brush);
        if (com.holeydigsite.effect.ModEffects.hasSteadyHands(entity)) {
            return Math.max(1, base * (100 - com.holeydigsite.effect.ModEffects.PAN_SPEEDUP_PERCENT) / 100);
        }
        return base;
    }

    /** Swish sound/dust interval while panning, in ticks (same rhythm as the vanilla brush). */
    private static final int SWISH_INTERVAL = 10;

    /*
     * Loot rolled once per completed scoop: one table per dirt tier,
     * data/holeydigsite/loot_table/panning/dirt_t1.json .. dirt_t3.json (see DirtTier#lootTable). Edit those files
     * (or override them with a datapack) to change drop rates without touching code; the Sluice Box uses the same ones.
     */

    /**
     * Items that get the extra "rare find" fanfare when they come out of the pan or sluice.
     * Defined in {@code data/holeydigsite/tags/item/panning_rare_finds.json} (all Rubies, large Goldchro).
     */
    public static final TagKey<Item> RARE_FINDS = TagKey.create(Registries.ITEM, HoleyDigsite.id("panning_rare_finds"));

    /**
     * The pan each player started panning, so looking at a DIFFERENT pan also cancels.
     * Weak keys: entries vanish with the player object. Client and server players are different objects,
     * so in singleplayer both sides share the map safely (it is synchronized for the two threads).
     */
    private static final Map<LivingEntity, BlockPos> TARGETS = Collections.synchronizedMap(new WeakHashMap<>());

    // ------------------------------------------------------------------------------------------------
    // Start
    // ------------------------------------------------------------------------------------------------

    /** True if the pan can be panned right now: sitting in water with at least 1 scoop of dirt. */
    public static boolean canPan(BlockState state) {
        return state.getBlock() instanceof PanBlock
                && state.getValue(PanBlock.WATERLOGGED)
                && PanBlock.fill(state) > 0;
    }

    /**
     * Right-click on a pan with the brush. A dry or empty pan shows an action-bar hint instead.
     * Called on both sides; returns true if panning started.
     */
    public static boolean tryStartPanning(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!state.getValue(PanBlock.WATERLOGGED)) {
            hint(level, player, "message.holeydigsite.pan_needs_water");
            return false;
        }
        if (PanBlock.fill(state) <= 0) {
            hint(level, player, "message.holeydigsite.pan_empty");
            return false;
        }
        TARGETS.put(player, pos.immutable());
        player.startUsingItem(hand);
        return true;
    }

    private static void hint(Level level, Player player, String key) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    /** Forget the target when the player stops using the brush (released, cancelled or finished). */
    public static void stopPanning(LivingEntity entity) {
        TARGETS.remove(entity);
    }

    // ------------------------------------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------------------------------------

    /**
     * One tick of holding right-click with the brush.
     *
     * @param usedTicks how many ticks the brush has been held, starting at 1 on the first tick
     */
    public static void tickPanning(Level level, LivingEntity entity, int usedTicks) {
        if (!(entity instanceof Player player)) {
            entity.releaseUsingItem();
            return;
        }

        // Cancel if not looking at the pan we started on, or it can't be panned any more
        BlockPos target = TARGETS.get(player);
        BlockHitResult hit = lookedAtBlock(player);
        if (target == null || hit == null || !hit.getBlockPos().equals(target)) {
            player.releaseUsingItem();
            return;
        }
        BlockState state = level.getBlockState(target);
        if (!canPan(state)) {
            player.releaseUsingItem();
            return;
        }

        playPanningEffects(level, player, target, hit, usedTicks);

        if (usedTicks % cycleTicks(player, player.getUseItem()) == 0 && level instanceof ServerLevel serverLevel) {
            completeCycle(serverLevel, target, state, player);
        }
    }

    /** The block the player is looking at within reach (same ray the vanilla brush uses), or null. */
    @Nullable
    private static BlockHitResult lookedAtBlock(Player player) {
        HitResult result = ProjectileUtil.getHitResultOnViewVector(player,
                e -> !e.isSpectator() && e.isPickable(), player.blockInteractionRange());
        return result instanceof BlockHitResult blockHit && result.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    /** Swishing sounds, water splashes and a bit of churned-up dirt while panning. */
    private static void playPanningEffects(Level level, Player player, BlockPos pos, BlockHitResult hit, int usedTicks) {
        RandomSource random = level.getRandom();
        if (level.isClientSide) {
            // a few splash droplets every other tick, spread over the pan's water surface
            if (usedTicks % 2 == 0) {
                for (int i = 0; i < 3; i++) {
                    level.addParticle(ParticleTypes.SPLASH,
                            pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.3,
                            pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                            (random.nextDouble() - 0.5) * 0.1, 0.1, (random.nextDouble() - 0.5) * 0.1);
                }
            }
            if (usedTicks % SWISH_INTERVAL == SWISH_INTERVAL / 2) {
                BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
                Vec3 at = hit.getLocation();
                for (int i = 0; i < 4; i++) {
                    level.addParticle(dirt, at.x, at.y + 0.05, at.z,
                            (random.nextDouble() - 0.5) * 0.6, 0.05, (random.nextDouble() - 0.5) * 0.6);
                }
            }
        }

        // Sounds: playSound(player, ...) plays locally on the client and for everyone else from the server
        if (usedTicks % SWISH_INTERVAL == SWISH_INTERVAL / 2) {
            level.playSound(player, pos, SoundEvents.BRUSH_SAND, SoundSource.BLOCKS, 0.6F, 1.2F + random.nextFloat() * 0.2F);
            level.playSound(player, pos, SoundEvents.GENERIC_SWIM, SoundSource.BLOCKS, 0.25F, 1.4F + random.nextFloat() * 0.3F);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Completing a cycle
    // ------------------------------------------------------------------------------------------------

    /** Server: one finished scoop -> fill -1, roll the pan's tier table, pop it out, play the reward sound. */
    private static void completeCycle(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        int newFill = PanBlock.fill(state) - 1;
        int tier = state.getValue(PanBlock.TIER);
        BlockState next = PanBlock.withFill(state, newFill);
        if (newFill <= 0) {
            next = next.setValue(PanBlock.TIER, 1); // an empty pan takes any tier again
        }
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);

        List<ItemStack> loot = rollScoop(level, pos, player, tier);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.holeydigsite.ledger.LedgerManager.recordPan(sp, tier, 1);
        }
        boolean rare = false;
        for (ItemStack stack : loot) {
            rare |= stack.is(RARE_FINDS);
            popOut(level, pos, stack, player);
        }

        if (!loot.isEmpty()) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1.0F + level.random.nextFloat() * 0.3F);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.6F);
        }
        if (rare) {
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.5F);
        }

        if (newFill <= 0) {
            player.releaseUsingItem(); // pan is empty - stop the animation
        }
    }

    /** How many scoops of each tier were rolled since the server started (index = tier; for tests and debugging). */
    private static final long[] ROLLS = new long[DirtTier.MAX + 1];

    public static long rollCount(int tier) {
        return ROLLS[DirtTier.clamp(tier)];
    }

    /**
     * Rolls the panning table of {@code tier} once (one scoop) with archaeology-style LootParams: origin = pan/sluice
     * centre, this_entity = the panning player (optional, e.g. for luck). Global loot modifiers apply.
     * Shared by hand panning and the Sluice Box; public so the economy GameTest can roll the real tables.
     */
    public static List<ItemStack> rollScoop(ServerLevel level, BlockPos pos, @Nullable Player player, int tier) {
        ROLLS[DirtTier.clamp(tier)]++;
        return rollTable(level, pos, player, DirtTier.lootTable(tier));
    }

    /** One roll of any loot table with panning context (Phase 16: the sluice upgrades' extra rolls). */
    public static List<ItemStack> rollTable(ServerLevel level, BlockPos pos, @Nullable Player player,
                                            net.minecraft.resources.ResourceKey<LootTable> key) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player == null ? 0.0F : player.getLuck())
                .create(LootContextParamSets.ARCHAEOLOGY);
        return table.getRandomItems(params);
    }

    /**
     * Spawns a loot stack just above the pan's rim with a small hop toward the player (like a dispenser
     * spitting it out), so it lands where it can be picked up.
     */
    private static void popOut(ServerLevel level, BlockPos pos, ItemStack stack, Player player) {
        RandomSource random = level.random;
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.35; // pan rim is 4px (0.25) tall
        double z = pos.getZ() + 0.5;
        Vec3 toPlayer = new Vec3(player.getX() - x, 0, player.getZ() - z);
        toPlayer = toPlayer.lengthSqr() > 1.0E-4 ? toPlayer.normalize().scale(0.08) : Vec3.ZERO;

        ItemEntity entity = new ItemEntity(level, x, y, z, stack,
                toPlayer.x + (random.nextDouble() - 0.5) * 0.05,
                0.22 + random.nextDouble() * 0.05,
                toPlayer.z + (random.nextDouble() - 0.5) * 0.05);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }
}
