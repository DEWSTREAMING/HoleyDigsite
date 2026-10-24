package com.holeydigsite.sluice;

import com.holeydigsite.drill.DirtClumpTarget;
import com.holeydigsite.tier.DirtTier;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tier 1 Sluice Box: water-powered auto-panning. Dirt Clumps go in at the BACK (drill, belt or player), are washed
 * one every {@link SluiceBoxBlockEntity#CYCLE_TICKS} ticks with the same loot roll as hand panning
 * ({@link com.holeydigsite.panning.Panning#rollScoop}), and the finds collect in the tray at the FRONT.
 *
 * <ul>
 *   <li>Facing: placed with its front (tray) towards the player. Water ports on BOTH sides (left and right).</li>
 *   <li>Input: {@link DirtClumpTarget} from the back only; queue of {@link SluiceBoxBlockEntity#MAX_QUEUE}. A belt
 *       feeding a full sluice backs up - nothing is voided.</li>
 *   <li>Output: the tray, emptied by an empty-hand right-click (which also shows the status). No item handler.</li>
 *   <li>Status ({@link #STATUS}) picks the model and lamp; {@link #DIRT} shows the dirt layer while clumps are
 *       queued. Only a WORKING sluice ticks; the others react to network changes, input and collecting.</li>
 * </ul>
 */
public class SluiceBoxBlock extends BaseEntityBlock implements WaterNetworkBlock, DirtClumpTarget {
    public static final MapCodec<SluiceBoxBlock> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.INT.optionalFieldOf("cycle_ticks", Stats.T1.cycleTicks()).forGetter(b -> b.stats.cycleTicks()),
            com.mojang.serialization.Codec.INT.optionalFieldOf("max_queue", Stats.T1.maxQueue()).forGetter(b -> b.stats.maxQueue()),
            com.mojang.serialization.Codec.INT.optionalFieldOf("tray_slots", Stats.T1.traySlots()).forGetter(b -> b.stats.traySlots()),
            propertiesCodec()).apply(i, (cycle, queue, tray, props) -> new SluiceBoxBlock(props, new Stats(cycle, queue, tray))));

    /**
     * Speed and sizes of a sluice (Phase 10: Tier 2 is twice as fast with twice the queue and tray).
     *
     * @param cycleTicks ticks per clump
     * @param maxQueue   clumps waiting
     * @param traySlots  tray slots; the sluice stops once they are all used
     */
    public record Stats(int cycleTicks, int maxQueue, int traySlots) {
        public static final Stats T1 = of("sluice_box");
        public static final Stats T2 = of("sluice_box_t2");

        /** The stats of a sluice tier from {@code tiers/sluice_box.json}. */
        public static Stats of(String tierId) {
            var d = com.holeydigsite.tier.Tiers.def(tierId);
            return new Stats(d.stat("cycle_ticks"), d.stat("max_queue"), d.stat("tray_slots"));
        }
    }

    private final Stats stats;

    /** The stats of the sluice block in {@code state} (Tier 1 if it isn't a sluice). */
    public static Stats stats(BlockState state) {
        return state.getBlock() instanceof SluiceBoxBlock s ? s.stats : Stats.T1;
    }

    public Stats stats() {
        return stats;
    }
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<SluiceStatus> STATUS = EnumProperty.create("status", SluiceStatus.class);
    /** Clumps are queued: the dirt layer on the ramp is visible. */
    public static final BooleanProperty DIRT = BooleanProperty.create("dirt");
    /** Phase 16: the installed {@link SluiceUpgrade} (one slot; drawn on the model). */
    public static final EnumProperty<SluiceUpgrade> UPGRADE = EnumProperty.create("upgrade", SluiceUpgrade.class);

    /** Base + side walls; the trough is open on top. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 5, 16),
            Block.box(0, 5, 0, 2, 12, 16),
            Block.box(14, 5, 0, 16, 12, 16));

    /** Tier 1 sluice. */
    public SluiceBoxBlock(Properties properties) {
        this(properties, Stats.T1);
    }

    public SluiceBoxBlock(Properties properties, Stats stats) {
        super(properties);
        this.stats = stats;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(STATUS, SluiceStatus.NO_POWER)
                .setValue(DIRT, false)
                .setValue(UPGRADE, SluiceUpgrade.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS, DIRT, UPGRADE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** Front (tray) faces the player, like the drill. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return new SluiceBoxBlockEntity(pos, state);
    }

    /** Only a WORKING sluice ticks (vanilla re-reads this whenever the block state changes). */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(STATUS) != SluiceStatus.WORKING) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SLUICE_BOX.get(), SluiceBoxBlockEntity::serverTick);
    }

    /**
     * Works out status + dirt layer from power, tray and queue, and applies them (one block update, clients only).
     * Called after anything that can change them: network, clumps in, a clump washed, the tray collected.
     */
    public void refreshStatus(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice) || !state.is(this)) {
            return;
        }
        // Phase 12: tray -> belt in front (as space allows); keep trying a few times a second while both are there
        if (sluice.pushTrayToBelt(level, pos, state)) {
            level.scheduleTick(pos, this, 4);
        }
        SluiceStatus next;
        if (!sluice.isWaterPowered()) {
            next = SluiceStatus.NO_POWER;
        } else if (sluice.isTrayFull()) {
            next = SluiceStatus.STOPPED;
        } else if (sluice.queued() == 0) {
            next = SluiceStatus.IDLE;
        } else {
            next = SluiceStatus.WORKING;
        }
        if (sluice.queued() == 0) {
            sluice.resetProgress(); // a half-washed clump doesn't exist without a clump
        }
        BlockState updated = state.setValue(STATUS, next).setValue(DIRT, sluice.queued() > 0);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Water network
    // ------------------------------------------------------------------------------------------------

    /** Phase 12: re-check (tray -> belt). */
    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        refreshStatus(level, pos, state);
    }

    /** Phase 12: a belt placed in front starts taking the tray. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moved) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, moved);
        if (!level.isClientSide && neighborPos.equals(pos.relative(state.getValue(FACING)))) {
            refreshStatus(level, pos, state);
        }
    }

    @Override
    public Role waterRole() {
        return Role.MACHINE;
    }

    /** Water ports on both sides (left and right of the flow), never front or back. */
    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return side.getAxis().isHorizontal() && side.getAxis() != state.getValue(FACING).getAxis();
    }

    @Override
    public void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        if (level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice) {
            sluice.setWaterPowered(powered);
            refreshStatus(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) || oldState.getValue(FACING) != state.getValue(FACING)) {
            WaterNetworks.markDirty(level, pos); // also sets the first status once the block entity exists
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof net.minecraft.server.level.ServerPlayer sp && level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice) {
            sluice.setOwnerPlayer(sp);
        }
    }

    /** Breaking drops the sluice (loot table) plus its queued clumps and everything in the tray. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice) {
                for (ItemStack s : sluice.queuedAsItems()) {
                    Block.popResource(level, pos, s);
                }
                for (ItemStack s : sluice.takeTray()) {
                    Block.popResource(level, pos, s);
                }
                if (state.getValue(UPGRADE) != SluiceUpgrade.NONE) {
                    Block.popResource(level, pos, new ItemStack(state.getValue(UPGRADE).item()));
                }
            }
            WaterNetworks.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ------------------------------------------------------------------------------------------------
    // Clump input (drill / belt)
    // ------------------------------------------------------------------------------------------------

    @Override
    public boolean canAcceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier) {
        return (from == state.getValue(FACING).getOpposite() || from == Direction.UP)
                && level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice && sluice.hasQueueRoom();
    }

    @Override
    public void acceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier) {
        if (level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice && sluice.addClumps(1, tier) > 0) {
            refreshStatus(level, pos, level.getBlockState(pos));
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Player
    // ------------------------------------------------------------------------------------------------

    /** Right-click with Dirt Clumps: 1 into the queue (sneaking: as many as fit). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        SluiceUpgrade upgrade = SluiceUpgrade.of(stack);
        if (upgrade != SluiceUpgrade.NONE) {
            return installUpgrade(upgrade, stack, state, level, pos, player);
        }
        if (!DirtTier.isClump(stack) || !(level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            int added = sluice.addClumps(player.isShiftKeyDown() ? stack.getCount() : 1, DirtTier.ofClump(stack));
            if (sluice.owner() == null && player instanceof net.minecraft.server.level.ServerPlayer sp) sluice.setOwnerPlayer(sp);
            if (added == 0) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.sluice_full"), true);
                return ItemInteractionResult.CONSUME;
            }
            stack.consume(added, player);
            level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            refreshStatus(level, pos, state);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Phase 16: right-click with an upgrade item installs it (one slot; an installed upgrade must be removed first). Public for tests. */
    public ItemInteractionResult installUpgrade(SluiceUpgrade upgrade, ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            if (state.getValue(UPGRADE) != SluiceUpgrade.NONE) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.sluice.upgrade_full").withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.CONSUME;
            }
            level.setBlock(pos, state.setValue(UPGRADE, upgrade), Block.UPDATE_ALL);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.holeydigsite.ledger.ModTriggers.UPGRADE_INSTALLED.get().trigger(
                        sp, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(upgrade.item()));
                if (level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice && sluice.owner() == null) {
                    sluice.setOwnerPlayer(sp);
                }
            }
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4F, 1.4F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            player.displayClientMessage(Component.translatable("message.holeydigsite.sluice.upgrade_installed", upgrade.item().getDescription())
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sneak + empty hand takes the installed upgrade back (nothing is lost). Public for tests. */
    public void removeUpgrade(BlockState state, Level level, BlockPos pos, Player player) {
        SluiceUpgrade upgrade = state.getValue(UPGRADE);
        level.setBlock(pos, state.setValue(UPGRADE, SluiceUpgrade.NONE), Block.UPDATE_ALL);
        ItemStack back = new ItemStack(upgrade.item());
        if (!player.getInventory().add(back)) {
            player.drop(back, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
        player.displayClientMessage(Component.translatable("message.holeydigsite.sluice.upgrade_removed", upgrade.item().getDescription())
                .withStyle(ChatFormatting.YELLOW), true);
    }

    /** Empty hand: collect the whole tray (overflow drops at the player's feet), then show the status. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player.isShiftKeyDown() && state.getValue(UPGRADE) != SluiceUpgrade.NONE) {
            removeUpgrade(state, level, pos, player);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice) {
            if (sluice.owner() == null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sluice.setOwnerPlayer(sp);
            }
            List<ItemStack> items = sluice.takeTray();
            for (ItemStack s : items) {
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
            }
            if (!items.isEmpty()) {
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
            }
            refreshStatus(level, pos, state);
            BlockState now = level.getBlockState(pos);
            player.displayClientMessage(statusMessage(now, sluice), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** "Sluice Box: <status> - <reason>". Public for tests. */
    public static Component statusMessage(BlockState state, SluiceBoxBlockEntity sluice) {
        return switch (state.getValue(STATUS)) {
            case WORKING -> Component.translatable("message.holeydigsite.sluice.working",
                    Math.max(1, (stats(state).cycleTicks() - sluice.progress() + 19) / 20), sluice.queued())
                    .withStyle(ChatFormatting.GREEN);
            case IDLE -> Component.translatable("message.holeydigsite.sluice.idle").withStyle(ChatFormatting.YELLOW);
            case STOPPED -> Component.translatable("message.holeydigsite.sluice.stopped").withStyle(ChatFormatting.RED);
            case NO_POWER -> Component.translatable("message.holeydigsite.sluice.no_power").withStyle(ChatFormatting.AQUA);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String key = "tooltip." + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).toLanguageKey();
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.sluice_box.ports").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------------------------------------
    // Client visuals (no server cost)
    // ------------------------------------------------------------------------------------------------

    /** Working: water splashing into the tray and, now and then, a quiet rush of water. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(STATUS) != SluiceStatus.WORKING) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.3;
        double y = pos.getY() + 0.4;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.3;
        for (int i = 0; i < 6; i++) {
            level.addParticle(ParticleTypes.SPLASH,
                    x + (random.nextDouble() - 0.5) * (facing.getAxis() == Direction.Axis.X ? 0.2 : 0.7),
                    y,
                    z + (random.nextDouble() - 0.5) * (facing.getAxis() == Direction.Axis.Z ? 0.2 : 0.7),
                    facing.getStepX() * 0.05, 0.1, facing.getStepZ() * 0.05);
        }
        if (random.nextInt(3) == 0) {
            level.playLocalSound(pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.15F, 1.1F + random.nextFloat() * 0.2F, false);
        }
    }
}
