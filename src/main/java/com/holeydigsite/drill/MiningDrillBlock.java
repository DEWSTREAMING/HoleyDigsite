package com.holeydigsite.drill;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.tier.DirtTier;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tier 1 Mining Drill placed on Dirtrock and powered by water. Drills one Dirt Clump every
 * {@link #CYCLE_TICKS} ticks into the block in front of it (a pan or a Clump Belt, see {@link ClumpOutputs}).
 *
 * <ul>
 *   <li>Facing: horizontal, placed with its front (output, spinning bit) towards the player and the water port
 *       (back) facing away.</li>
 *   <li>Power: a {@link WaterNetworkBlock} machine, where the network pushes on or off into the block entity.</li>
 *   <li>Status ({@link #STATUS}) drives the model: working (animated bit, green light), stopped (red light,
 *       output blocked for more than {@link #STOP_AFTER_BLOCKED_TICKS} ticks), no_power (blue light).</li>
 *   <li>Cost: only a working drill has a block entity ticker. Stopped or no-power drills react to neighbour
 *       updates and network changes, plus a slow scheduled re-check every {@link #RECHECK_TICKS} ticks.</li>
 *   <li>No inventory, no item-handler capability, never drops clumps into the world.</li>
 * </ul>
 */
public class MiningDrillBlock extends BaseEntityBlock implements WaterNetworkBlock {
    public static final MapCodec<MiningDrillBlock> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.INT.optionalFieldOf("cycle_ticks", 300).forGetter(MiningDrillBlock::cycleTicks),
            propertiesCodec()).apply(i, (ticks, props) -> new MiningDrillBlock(props, ticks)));
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DrillStatus> STATUS = EnumProperty.create("status", DrillStatus.class);
    /** Drills are two blocks tall, like a door. The lower half stands on the rock, has the block entity, the
     *  water port, and the output. The upper half is housing, lamp, and spout, and mirrors the lower half's status. */
    public static final EnumProperty<net.minecraft.world.level.block.state.properties.DoubleBlockHalf> HALF =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Tier 1 ticks per Dirt Clump: 300 = 15 seconds. Each drill block has its own {@link #cycleTicks()}. */
    public static final int CYCLE_TICKS = com.holeydigsite.tier.Tiers.stat("mining_drill", "cycle_ticks");
    /** Tier 2 Mining Drill: 150 ticks (7.5 seconds), twice as fast. */
    public static final int CYCLE_TICKS_T2 = com.holeydigsite.tier.Tiers.stat("mining_drill_t2", "cycle_ticks");
    /** Tier 3 Mining Drill: 100 ticks (5 seconds), three times Tier 1. */
    public static final int CYCLE_TICKS_T3 = com.holeydigsite.tier.Tiers.stat("mining_drill_t3", "cycle_ticks");
    /** The drill switches to stopped once its output has been blocked for longer than this (3 seconds). */
    public static final int STOP_AFTER_BLOCKED_TICKS = 60;
    /** While working, the output is checked this often (and always on the tick a clump is ready). */
    public static final int CHECK_INTERVAL = 5;
    /** Slow re-check for stopped or no-power drills as a backup for missed neighbour updates. */
    public static final int RECHECK_TICKS = 40;

    /**
     * Blocks a drill must stand on to work: {@code data/holeydigsite/tags/block/drill_base.json} (all three Dirtrock
     * tiers). The rock's tier becomes the clumps' tier ({@link #clumpTier}).
     */
    public static final TagKey<net.minecraft.world.level.block.Block> DRILL_BASE =
            TagKey.create(Registries.BLOCK, HoleyDigsite.id("drill_base"));

    private final int cycleTicks;

    /** Tier 1 drill. */
    public MiningDrillBlock(Properties properties) {
        this(properties, CYCLE_TICKS);
    }

    /** A drill with its own speed (Tier 2: {@link #CYCLE_TICKS_T2}). Every other rule is the same. */
    public MiningDrillBlock(Properties properties, int cycleTicks) {
        super(properties);
        this.cycleTicks = cycleTicks;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, DrillStatus.NO_POWER)
                .setValue(HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
    }

    /** Ticks per Dirt Clump for this drill. */
    public int cycleTicks() {
        return cycleTicks;
    }

    /** Ticks per clump of the drill block in {@code state} (Tier 1 if it isn't a drill). */
    public static int cycleTicks(BlockState state) {
        return state.getBlock() instanceof MiningDrillBlock d ? d.cycleTicks : CYCLE_TICKS;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS, HALF);
    }

    /** BaseEntityBlock defaults to invisible for block entity renderers, but we use a normal baked model. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * The front (output, spinning bit) faces the player, so the water port on the back faces away from them
     * (like a furnace). Players place it while looking at the spot from the side where the pan or belt goes.
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public static boolean isLower(BlockState state) {
        return state.getValue(HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
    }

    /** The lower half's position for either half. */
    public static BlockPos lowerPos(BlockPos pos, BlockState state) {
        return isLower(state) ? pos : pos.below();
    }

    /** The two halves belong together, so a half whose partner went away disappears and the lower half drops the drill. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        boolean lower = isLower(state);
        if (direction == (lower ? Direction.UP : Direction.DOWN)) {
            boolean partner = neighbor.getBlock() instanceof MiningDrillBlock && isLower(neighbor) != lower;
            if (!partner) {
                return Blocks.AIR.defaultBlockState();
            }
            if (!lower) {
                return state.setValue(STATUS, neighbor.getValue(STATUS)).setValue(FACING, neighbor.getValue(FACING));
            }
        }
        return state;
    }

    /** Creative players breaking the upper half don't get the drill dropped by the lower half. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !isLower(state) && player.getAbilities().instabuild) {
            BlockPos below = pos.below();
            BlockState b = level.getBlockState(below);
            if (b.is(this) && isLower(b)) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(b));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    // ------------------------------------------------------------------------------------------------
    // Block entity + ticking
    // ------------------------------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isLower(state) ? new MiningDrillBlockEntity(pos, state) : null;
    }

    /** Only a working drill ticks, which is re-read by vanilla whenever the block state changes. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(STATUS) != DrillStatus.WORKING || !isLower(state)) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.MINING_DRILL.get(), MiningDrillBlockEntity::serverTick);
    }

    /** Slow re-check for stopped or no-power drills. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isLower(state)) {
            refreshStatus(level, pos, state);
        }
    }

    /** Something next to the drill changed, so re-evaluate right away. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && isLower(state)) {
            refreshStatus(level, pos, state);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------------------------------------

    /** The block in front, where clumps go. */
    public static BlockPos outputPos(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING));
    }

    /** Tier of the clumps this drill makes based on the rock it stands on. */
    public static int clumpTier(Level level, BlockPos pos) {
        return DirtTier.ofRock(level.getBlockState(pos.below()));
    }

    public static boolean isOnDrillBase(Level level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(DRILL_BASE);
    }

    /**
     * Works out the status from current conditions and applies it. A stopped drill only restarts when its output
     * has room again, and a powered no-power drill starts working.
     */
    public void refreshStatus(Level level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof MiningDrillBlockEntity drill)) {
            return;
        }
        DrillStatus current = state.getValue(STATUS);
        DrillStatus next;
        if (!isOnDrillBase(level, pos) || !drill.isWaterPowered()) {
            next = DrillStatus.NO_POWER;
        } else if (current == DrillStatus.STOPPED) {
            Direction facing = state.getValue(FACING);
            next = ClumpOutputs.canOutput(level, pos.relative(facing), facing.getOpposite(), clumpTier(level, pos))
                    ? DrillStatus.WORKING : DrillStatus.STOPPED;
        } else {
            next = DrillStatus.WORKING;
        }
        setStatus(level, pos, state, drill, next);
    }

    /** Applies a status change so clients get the new model and the ticker is swapped by vanilla. */
    void setStatus(Level level, BlockPos pos, BlockState state, MiningDrillBlockEntity drill, DrillStatus next) {
        if (state.getValue(STATUS) != next) {
            if (next == DrillStatus.WORKING) {
                drill.clearBlocked();
            }
            level.setBlock(pos, state.setValue(STATUS, next), Block.UPDATE_CLIENTS);
            BlockState up = level.getBlockState(pos.above());
            if (up.is(this) && !isLower(up) && up.getValue(STATUS) != next) {
                level.setBlock(pos.above(), up.setValue(STATUS, next), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        if (next != DrillStatus.WORKING) {
            level.scheduleTick(pos, this, RECHECK_TICKS);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Water network
    // ------------------------------------------------------------------------------------------------

    @Override
    public Role waterRole() {
        return Role.MACHINE;
    }

    /** The water port is on the back. */
    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return isLower(state) && side == state.getValue(FACING).getOpposite();
    }

    @Override
    public void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        if (level.getBlockEntity(pos) instanceof MiningDrillBlockEntity drill) {
            drill.setWaterPowered(powered);
            refreshStatus(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) || oldState.getValue(FACING) != state.getValue(FACING)) {
            WaterNetworks.markDirty(level, pos);
        }
        if (!oldState.is(this) && isLower(state) && !level.isClientSide) {
            BlockPos up = pos.above();
            BlockState above = level.getBlockState(up);
            boolean otherTierUpper = above.getBlock() instanceof MiningDrillBlock && !above.is(this) && !isLower(above);
            if (otherTierUpper || (!above.is(this) && (above.isAir() || above.canBeReplaced()))) {
                level.setBlock(up, state.setValue(HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            }
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this)) {
            WaterNetworks.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ------------------------------------------------------------------------------------------------
    // Player feedback
    // ------------------------------------------------------------------------------------------------

    /** Right-click with an empty hand shows status and reason in the action bar. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        BlockPos lower = lowerPos(pos, state);
        if (!level.isClientSide && level.getBlockEntity(lower) instanceof MiningDrillBlockEntity drill) {
            player.displayClientMessage(statusMessage(level, lower, level.getBlockState(lower), drill), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Returns the status message component. Public for tests. */
    public static Component statusMessage(Level level, BlockPos pos, BlockState state, MiningDrillBlockEntity drill) {
        String key;
        Object[] args = new Object[0];
        ChatFormatting color;
        switch (state.getValue(STATUS)) {
            case WORKING -> {
                color = ChatFormatting.GREEN;
                if (drill.isOutputBlocked()) {
                    key = "message.holeydigsite.drill.working_blocked";
                } else {
                    key = "message.holeydigsite.drill.working";
                    args = new Object[]{Math.max(1, (cycleTicks(state) - drill.getProgress() + 19) / 20)};
                }
            }
            case STOPPED -> {
                color = ChatFormatting.RED;
                key = "message.holeydigsite.drill.stopped";
            }
            default -> {
                color = ChatFormatting.AQUA;
                key = !isOnDrillBase(level, pos) ? "message.holeydigsite.drill.no_dirtrock" : "message.holeydigsite.drill.no_water";
            }
        }
        return Component.translatable(key, args).withStyle(color);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String key = "tooltip." + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).toLanguageKey();
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.mining_drill.ports").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------------------------------------
    // Client visuals (no server cost)
    // ------------------------------------------------------------------------------------------------

    /**
     * Client random display tick that shows a puff of dirt at the drill's front and plays a grinding sound while working.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(STATUS) != DrillStatus.WORKING || !isLower(state)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.55;
        double y = pos.getY() + 0.15;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.55;
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
        for (int i = 0; i < 4; i++) {
            level.addParticle(dirt,
                    x + (random.nextDouble() - 0.5) * (facing.getAxis() == Direction.Axis.X ? 0.1 : 0.6),
                    y + random.nextDouble() * 0.3,
                    z + (random.nextDouble() - 0.5) * (facing.getAxis() == Direction.Axis.Z ? 0.1 : 0.6),
                    facing.getStepX() * 0.05, 0.05, facing.getStepZ() * 0.05);
        }
        level.playLocalSound(pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS,
                0.12F, 0.5F + random.nextFloat() * 0.15F, false);
    }
}
