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
 * Water-powered auto-panning. Dirt clumps go in at the back from a drill, belt, or player, are washed
 * one every {@link SluiceBoxBlockEntity#CYCLE_TICKS} ticks using the standard panning loot roll, and finds collect in the front tray.
 *
 * <ul>
 *   <li>Facing: placed with the front tray towards the player, with water ports on both sides.</li>
 *   <li>Input: {@link DirtClumpTarget} from the back only, with a limited queue. A belt feeding a full sluice backs up without voiding items.</li>
 *   <li>Output: the tray, emptied by an empty-hand right-click which also shows the status.</li>
 *   <li>Status and dirt properties drive the model and layers, and only working sluices tick.</li>
 * </ul>
 */
public class SluiceBoxBlock extends BaseEntityBlock implements WaterNetworkBlock, DirtClumpTarget {
    public static final MapCodec<SluiceBoxBlock> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.INT.optionalFieldOf("cycle_ticks", Stats.T1.cycleTicks()).forGetter(b -> b.stats.cycleTicks()),
            com.mojang.serialization.Codec.INT.optionalFieldOf("max_queue", Stats.T1.maxQueue()).forGetter(b -> b.stats.maxQueue()),
            com.mojang.serialization.Codec.INT.optionalFieldOf("tray_slots", Stats.T1.traySlots()).forGetter(b -> b.stats.traySlots()),
            propertiesCodec()).apply(i, (cycle, queue, tray, props) -> new SluiceBoxBlock(props, new Stats(cycle, queue, tray))));

    /**
     * Speed and sizes of a sluice.
     *
     * @param cycleTicks ticks per clump
     * @param maxQueue   clumps waiting
     * @param traySlots  tray slots, where the sluice stops once they are all used
     */
    public record Stats(int cycleTicks, int maxQueue, int traySlots) {
        public static final Stats T1 = of("sluice_box");
        public static final Stats T2 = of("sluice_box_t2");

        /** The stats of a sluice tier from configuration. */
        public static Stats of(String tierId) {
            var d = com.holeydigsite.tier.Tiers.def(tierId);
            return new Stats(d.stat("cycle_ticks"), d.stat("max_queue"), d.stat("tray_slots"));
        }
    }

    private final Stats stats;

    /** The stats of the sluice block in the given state, defaulting to Tier 1. */
    public static Stats stats(BlockState state) {
        return state.getBlock() instanceof SluiceBoxBlock s ? s.stats : Stats.T1;
    }

    public Stats stats() {
        return stats;
    }

    // ------------------------------------------------------------------------------------------------
    // Layout hooks: the Starter sluice is one block, the long sluice (LongSluiceBlock) overrides these
    // ------------------------------------------------------------------------------------------------

    /** The block that holds the block entity: this one, or the back part of a long sluice. */
    public BlockPos entityPos(BlockPos pos, BlockState state) {
        return pos;
    }

    /** Every block of the sluice, from the entity part's position. */
    public List<BlockPos> allParts(BlockPos entityPos, BlockState state) {
        return List.of(entityPos);
    }

    /** True for the part that holds the block entity, the inserts and the contents. */
    protected boolean isEntityPart(BlockState state) {
        return true;
    }

    /** The block entity of the sluice that owns the block at {@code pos}, whichever part it is. */
    @Nullable
    public static SluiceBoxBlockEntity entityAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SluiceBoxBlock block) {
            return level.getBlockEntity(block.entityPos(pos, state)) instanceof SluiceBoxBlockEntity e ? e : null;
        }
        return null;
    }

    /** The block the tray empties into, seen from the block entity's position. */
    public BlockPos trayOutputPos(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING));
    }

    /** True if a belt right after this block takes the tray's items (and so connects to it). */
    public boolean feedsForward(BlockState state) {
        return true;
    }

    /** Insert slots: one for the Starter sluice. */
    public int insertSlots() {
        return 1;
    }

    /** The inserts in the sluice, in the order they went in. */
    public List<SluiceUpgrade> inserts(BlockState state) {
        SluiceUpgrade u = state.getValue(UPGRADE);
        return u == SluiceUpgrade.NONE ? List.of() : List.of(u);
    }

    /** {@code state} with exactly these inserts. */
    protected BlockState withInserts(BlockState state, List<SluiceUpgrade> list) {
        return state.setValue(UPGRADE, list.isEmpty() ? SluiceUpgrade.NONE : list.get(0));
    }

    /** Sets the state of the sluice (all its parts, for a long one) from the entity part's new state. */
    protected void placeState(Level level, BlockPos entityPos, BlockState newState, int flags) {
        level.setBlock(entityPos, newState, flags);
    }

    /** True if a player may put clumps in by hand at this part. */
    protected boolean takesHandClumps(BlockState state) {
        return true;
    }

    protected String portsTooltipKey() {
        return "tooltip.holeydigsite.sluice_box.ports";
    }
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<SluiceStatus> STATUS = EnumProperty.create("status", SluiceStatus.class);
    /** Indicates if clumps are queued and the dirt layer on the ramp is visible. */
    public static final BooleanProperty DIRT = BooleanProperty.create("dirt");
    /** The installed {@link SluiceUpgrade} drawn on the model. */
    public static final EnumProperty<SluiceUpgrade> UPGRADE = EnumProperty.create("upgrade", SluiceUpgrade.class);

    /** Base and side walls shape, leaving the trough open on top. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 5, 16),
            Block.box(0, 5, 0, 2, 12, 16),
            Block.box(14, 5, 0, 16, 12, 16));

    /** Creates a Tier 1 sluice. */
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

    /** Only a working sluice ticks. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(STATUS) != SluiceStatus.WORKING) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SLUICE_BOX.get(), SluiceBoxBlockEntity::serverTick);
    }

    /**
     * Updates status and dirt layer from power, tray, and queue states.
     */
    public void refreshStatus(Level level, BlockPos pos, BlockState state) {
        BlockPos entity = entityPos(pos, state);
        if (!entity.equals(pos)) {
            pos = entity;
            state = level.getBlockState(pos);
        }
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice) || !state.is(this)) {
            return;
        }
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
            sluice.resetProgress();
        }
        BlockState updated = state.setValue(STATUS, next).setValue(DIRT, sluice.queued() > 0);
        if (updated != state) {
            placeState(level, pos, updated, Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Water network
    // ------------------------------------------------------------------------------------------------

    /** Re-checks tray to belt interactions. */
    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        refreshStatus(level, pos, state);
    }

    /** Handles neighbor changes when a belt is placed in front. */
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

    /** Water ports are on both sides, avoiding the front and back. */
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
            WaterNetworks.markDirty(level, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof net.minecraft.server.level.ServerPlayer sp && level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice) {
            sluice.setOwnerPlayer(sp);
        }
        String skin = stack.get(com.holeydigsite.registry.ModDataComponents.SKIN.get());
        if (skin != null && !skin.isEmpty() && level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice) {
            sluice.setSkin(skin, com.holeydigsite.skin.SkinWear.of(stack));
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity be = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (be instanceof SluiceBoxBlockEntity sluice) {
            String skin = sluice.getSkin();
            if (skin != null && !skin.isEmpty() && !skin.equals("default")) {
                for (ItemStack drop : drops) {
                    if (drop.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof SluiceBoxBlock) {
                        drop.set(com.holeydigsite.registry.ModDataComponents.SKIN.get(), skin);
                        com.holeydigsite.skin.SkinWear.set(drop, sluice.getWear());
                    }
                }
            }
        }
        return drops;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && isEntityPart(state) && level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice
                    && !sluice.refunded()) {
                for (ItemStack s : sluice.queuedAsItems()) {
                    Block.popResource(level, pos, s);
                }
                for (ItemStack s : sluice.takeTray()) {
                    Block.popResource(level, pos, s);
                }
                for (SluiceUpgrade u : inserts(state)) {
                    Block.popResource(level, pos, new ItemStack(u.item()));
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
                && entityAt(level, pos) instanceof SluiceBoxBlockEntity sluice && sluice.hasQueueRoom();
    }

    @Override
    public void acceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier) {
        if (entityAt(level, pos) instanceof SluiceBoxBlockEntity sluice && sluice.addClumps(1, tier) > 0) {
            refreshStatus(level, pos, level.getBlockState(pos));
        }
    }

    /** A locked crate from a drill or belt goes through to the tray untouched. */
    @Override
    public boolean canAcceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        if (one.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof com.holeydigsite.crate.CrateBlock) {
            return canAcceptClump(level, pos, state, from, 1) && entityAt(level, pos) instanceof SluiceBoxBlockEntity s && !s.isTrayFull();
        }
        return DirtClumpTarget.super.canAcceptItem(level, pos, state, from, one);
    }

    @Override
    public void acceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        if (one.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof com.holeydigsite.crate.CrateBlock) {
            if (entityAt(level, pos) instanceof SluiceBoxBlockEntity s) {
                s.addToTray(one.copyWithCount(1));
            }
            return;
        }
        DirtClumpTarget.super.acceptItem(level, pos, state, from, one);
    }

    // ------------------------------------------------------------------------------------------------
    // Player
    // ------------------------------------------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        SluiceUpgrade upgrade = SluiceUpgrade.of(stack);
        if (upgrade != SluiceUpgrade.NONE) {
            return installUpgrade(upgrade, stack, state, level, pos, player);
        }
        if (!DirtTier.isClump(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!takesHandClumps(state)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.sluice.use_chute"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos entity = entityPos(pos, state);
        if (!(level.getBlockEntity(entity) instanceof SluiceBoxBlockEntity sluice)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            int added = sluice.addClumps(player.isShiftKeyDown() ? stack.getCount() : 1, DirtTier.panKind(stack));
            if (sluice.owner() == null && player instanceof net.minecraft.server.level.ServerPlayer sp) sluice.setOwnerPlayer(sp);
            if (added == 0) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.sluice_full"), true);
                return ItemInteractionResult.CONSUME;
            }
            stack.consume(added, player);
            level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            refreshStatus(level, entity, level.getBlockState(entity));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Installs an insert into a free slot. Public for tests; {@code pos} may be any part. */
    public ItemInteractionResult installUpgrade(SluiceUpgrade upgrade, ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        BlockPos entity = entityPos(pos, state);
        if (!entity.equals(pos)) {
            pos = entity;
            state = level.getBlockState(pos);
        }
        if (!level.isClientSide) {
            List<SluiceUpgrade> have = inserts(state);
            if (have.contains(upgrade)) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.sluice.upgrade_same").withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.CONSUME;
            }
            if (have.size() >= insertSlots()) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.sluice.upgrade_full").withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.CONSUME;
            }
            List<SluiceUpgrade> next = new java.util.ArrayList<>(have);
            next.add(upgrade);
            placeState(level, pos, withInserts(state, next), Block.UPDATE_ALL);
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

    /** Takes the last insert out and gives it back. Public for tests; {@code pos} may be any part. */
    public void removeUpgrade(BlockState state, Level level, BlockPos pos, Player player) {
        BlockPos entity = entityPos(pos, state);
        if (!entity.equals(pos)) {
            pos = entity;
            state = level.getBlockState(pos);
        }
        List<SluiceUpgrade> have = new java.util.ArrayList<>(inserts(state));
        if (have.isEmpty()) {
            return;
        }
        SluiceUpgrade upgrade = have.remove(have.size() - 1);
        placeState(level, pos, withInserts(state, have), Block.UPDATE_ALL);
        ItemStack back = new ItemStack(upgrade.item());
        if (!player.getInventory().add(back)) {
            player.drop(back, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
        player.displayClientMessage(Component.translatable("message.holeydigsite.sluice.upgrade_removed", upgrade.item().getDescription())
                .withStyle(ChatFormatting.YELLOW), true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        BlockPos entity = entityPos(pos, state);
        BlockState eState = entity.equals(pos) ? state : level.getBlockState(entity);
        if (!level.isClientSide && player.isShiftKeyDown() && !inserts(eState).isEmpty()) {
            removeUpgrade(eState, level, entity, player);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide && level.getBlockEntity(entity) instanceof SluiceBoxBlockEntity sluice) {
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
            refreshStatus(level, entity, eState);
            BlockState now = level.getBlockState(entity);
            player.displayClientMessage(statusMessage(now, sluice), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Creates the status component message. Public for tests. */
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
        tooltip.add(Component.translatable(portsTooltipKey()).withStyle(ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------------------------------------
    // Client visuals (no server cost)
    // ------------------------------------------------------------------------------------------------

    /** Adds particles and sounds while working. */
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
