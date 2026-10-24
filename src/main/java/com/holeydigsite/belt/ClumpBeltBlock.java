package com.holeydigsite.belt;

import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.transport.ItemTarget;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Conveyor belt (id {@code clump_belt}, Fast: {@code fast_clump_belt}) that is a full-height, water-powered belt
 * carrying any item along the direction it faces (the direction the player looked when placing it). See
 * {@link ClumpBeltBlockEntity} for the movement rules and the cost model. Subclasses: {@link InclineBeltBlock},
 * {@link FilterBeltBlock}, {@link GemCutterBlock}.
 *
 * <ul>
 *   <li>Power: a water {@link WaterNetworkBlock#waterRole() CONDUIT} (belts pass water on to each other on every face except
 *       the top, so one pipe or intake touching any belt powers the whole line). Without water or with a redstone signal, the
 *       belt freezes (nothing moves, enters or leaves). See {@link #WATER} and {@link #POWERED}.</li>
 *   <li>In: a Mining Drill or Sluice tray facing it, the belt behind it, a belt pointing into its side, or a player
 *       (right-click with any non-block item adds 1, or sneaking adds as many as fit). It acts as an {@link ItemTarget}.</li>
 *   <li>Out (front): next belt, a pan (spout, drop animation, +1 fill, where a full pan bounces a clump off and voids it),
 *       any other {@link ItemTarget}, or an inclined belt one block up. Otherwise items wait and the belt backs up.</li>
 *   <li>Corners: when fed only from one side, it shows a curved piece ({@link #SHAPE}) and items follow the curve.</li>
 *   <li>Empty-hand right-click takes the items on this block, and breaking it drops the belt and its items.</li>
 *   <li>No item handler: hoppers, chests and other mods cannot put items on or take them off.</li>
 * </ul>
 */
public class ClumpBeltBlock extends BaseEntityBlock implements ItemTarget, WaterNetworkBlock {
    public static final MapCodec<ClumpBeltBlock> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.intRange(1, 4).optionalFieldOf("speed", 1).forGetter(ClumpBeltBlock::speed),
            propertiesCodec()).apply(i, (speed, props) -> new ClumpBeltBlock(props, speed)));

    /** Steps per tick (1 is one block per second, 2 is two blocks per second for fast belts). */
    private final int speed;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BeltMode> MODE = EnumProperty.create("mode", BeltMode.class);
    /** Redstone signal stops the belt. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Indicates the block in front is a pan, showing a funnel/spout at the belt end. */
    public static final BooleanProperty SPOUT = BooleanProperty.create("spout");
    /** Connected to a water network with an active intake, running only with water. */
    public static final BooleanProperty WATER = BooleanProperty.create("water");
    /** Straight or curved corner for flat belts only. */
    public static final EnumProperty<BeltShape> SHAPE = EnumProperty.create("shape", BeltShape.class);
    /** Filter Belt, Belt Splitter and Gem Cutter match the speed of the belt feeding into their back. */
    public static final IntegerProperty INPUT_SPEED = IntegerProperty.create("input_speed", 1, 4);

    /** Block event id when a clump bounces off a full pan. */
    public static final int EVENT_BOUNCE = 1;

    /** Top of the belt surface in blocks where items sit. */
    public static final float SURFACE = 1.0F;

    public ClumpBeltBlock(Properties properties) {
        this(properties, 1);
    }

    public ClumpBeltBlock(Properties properties, int speed) {
        super(properties);
        this.speed = speed;
        registerDefaultState(defaults(stateDefinition.any()));
    }

    protected static BlockState defaults(BlockState s) {
        s = s.setValue(FACING, Direction.NORTH).setValue(MODE, BeltMode.EMPTY).setValue(POWERED, false)
                .setValue(SPOUT, false).setValue(WATER, false);
        if (s.hasProperty(SHAPE)) {
            s = s.setValue(SHAPE, BeltShape.STRAIGHT);
        }
        if (s.hasProperty(FilterBeltBlock.OUT_RIGHT)) {
            s = s.setValue(FilterBeltBlock.OUT_RIGHT, false);
        }
        if (s.hasProperty(SplitterBeltBlock.TWO_WAY)) {
            s = s.setValue(SplitterBeltBlock.TWO_WAY, false);
        }
        if (s.hasProperty(INPUT_SPEED)) {
            s = s.setValue(INPUT_SPEED, 1);
        }
        return s;
    }

    public int speed() {
        return speed;
    }

    public static int speed(BlockState state) {
        if (state.hasProperty(INPUT_SPEED)) {
            return state.getValue(INPUT_SPEED);
        }
        return state.getBlock() instanceof ClumpBeltBlock b ? b.speed : 1;
    }

    public static Component speedTierName(int speed) {
        return switch (speed) {
            case 1 -> Component.translatable("text.holeydigsite.belt.speed.1");
            case 2 -> Component.translatable("text.holeydigsite.belt.speed.2");
            default -> Component.translatableWithFallback("text.holeydigsite.belt.speed." + speed, speed + "x");
        };
    }

    public static Component speedLabel(BlockState state) {
        return Component.translatable("text.holeydigsite.belt.input_speed", speedTierName(speed(state)));
    }

    public static boolean running(BlockState state) {
        return state.getValue(WATER) && !state.getValue(POWERED);
    }

    public static BeltShape shape(BlockState state) {
        return state.hasProperty(SHAPE) ? state.getValue(SHAPE) : BeltShape.STRAIGHT;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    protected static void addCommon(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, POWERED, SPOUT, WATER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        addCommon(builder);
        builder.add(SHAPE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ------------------------------------------------------------------------------------------------
    // Placement, neighbours, redstone, corners
    // ------------------------------------------------------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState s = defaultBlockState()
                .setValue(FACING, facing)
                .setValue(POWERED, level.hasNeighborSignal(pos))
                .setValue(SPOUT, isPan(level.getBlockState(pos.relative(facing))));
        if (s.hasProperty(SHAPE)) {
            s = s.setValue(SHAPE, computeShape(level, pos, facing));
        }
        if (s.hasProperty(INPUT_SPEED)) {
            s = s.setValue(INPUT_SPEED, computeInputSpeed(level, pos, facing));
        }
        return s;
    }

    private static boolean isPan(BlockState state) {
        return state.getBlock() instanceof com.holeydigsite.block.PanBlock;
    }

    public static boolean feeds(BlockGetter level, BlockPos p, Direction dir) {
        BlockState b = level.getBlockState(p);
        if (b.getBlock() instanceof ClumpBeltBlock) {
            return b.getValue(FACING) == dir || (b.getBlock() instanceof FilterBeltBlock && FilterBeltBlock.outSide(b) == dir)
                    || (b.getBlock() instanceof SplitterBeltBlock && b.getValue(FACING).getAxis() != dir.getAxis() && dir.getAxis().isHorizontal());
        }
        if (b.getBlock() instanceof MiningDrillBlock) {
            return b.getValue(MiningDrillBlock.FACING) == dir && MiningDrillBlock.isLower(b);
        }
        if (b.getBlock() instanceof SluiceBoxBlock) {
            return b.getValue(SluiceBoxBlock.FACING) == dir;
        }
        return false;
    }

    public static BeltShape computeShape(BlockGetter level, BlockPos pos, Direction facing) {
        if (feeds(level, pos.relative(facing.getOpposite()), facing)) {
            return BeltShape.STRAIGHT;
        }
        BlockPos upBack = pos.relative(facing.getOpposite()).above();
        BlockState ub = level.getBlockState(upBack);
        if (ub.getBlock() instanceof DownInclineBeltBlock && ub.getValue(FACING) == facing) {
            return BeltShape.STRAIGHT;
        }
        boolean left = feeds(level, pos.relative(facing.getCounterClockWise()), facing.getClockWise());
        boolean right = feeds(level, pos.relative(facing.getClockWise()), facing.getCounterClockWise());
        return left && !right ? BeltShape.LEFT : right && !left ? BeltShape.RIGHT : BeltShape.STRAIGHT;
    }

    @Nullable
    private static BlockPos feedingBeltPos(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos backPos = pos.relative(facing.getOpposite());
        BlockState back = level.getBlockState(backPos);
        if (back.getBlock() instanceof ClumpBeltBlock && !(back.getBlock() instanceof DownInclineBeltBlock)
                && feeds(level, backPos, facing)) {
            return backPos;
        }
        BlockPos upBack = backPos.above();
        BlockState ub = level.getBlockState(upBack);
        if (ub.getBlock() instanceof DownInclineBeltBlock && ub.getValue(FACING) == facing) {
            return upBack;
        }
        return null;
    }

    public static int computeInputSpeed(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos curPos = pos;
        Direction curFacing = facing;
        for (int depth = 0; depth < 16; depth++) {
            BlockPos feedPos = feedingBeltPos(level, curPos, curFacing);
            if (feedPos == null || feedPos.equals(pos)) {
                return 1;
            }
            BlockState feedState = level.getBlockState(feedPos);
            if (!(feedState.getBlock() instanceof ClumpBeltBlock feedBelt)) {
                return 1;
            }
            if (!feedState.hasProperty(INPUT_SPEED)) {
                return Math.clamp(feedBelt.speed(), 1, 4);
            }
            curPos = feedPos;
            curFacing = feedState.getValue(FACING);
        }
        return 1;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction == facing) {
            state = state.setValue(SPOUT, isPan(neighborState));
        }
        if (state.hasProperty(SHAPE) && direction.getAxis().isHorizontal()) {
            state = state.setValue(SHAPE, computeShape(level, pos, facing));
        }
        if (state.hasProperty(INPUT_SPEED)) {
            state = state.setValue(INPUT_SPEED, computeInputSpeed(level, pos, facing));
        }
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        if (state.hasProperty(INPUT_SPEED)) {
            int nextSpeed = computeInputSpeed(level, pos, state.getValue(FACING));
            if (nextSpeed != state.getValue(INPUT_SPEED)) {
                state = state.setValue(INPUT_SPEED, nextSpeed);
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED) && level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt) {
            setStopFlag(level, pos, state, belt, POWERED, powered);
        }
    }

    private static void setStopFlag(Level level, BlockPos pos, BlockState state, ClumpBeltBlockEntity belt,
                                     BooleanProperty flag, boolean value) {
        boolean wasRunning = running(state);
        BlockState next = state.setValue(flag, value);
        if (wasRunning && !running(next)) {
            belt.finishDrops(level, pos, state);
            next = level.getBlockState(pos).setValue(flag, value);
        }
        if (!running(next) && belt.isEmpty()) {
            next = next.setValue(MODE, BeltMode.EMPTY);
        } else if (running(next) && !belt.isEmpty()) {
            next = next.setValue(MODE, BeltMode.MOVING);
        }
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        belt.sync();
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
    // Water
    // ------------------------------------------------------------------------------------------------

    @Override
    public Role waterRole() {
        return Role.CONDUIT;
    }

    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return side != Direction.UP;
    }

    @Override
    public void addExtraLinks(BlockGetter level, BlockPos pos, BlockState state, List<BlockPos> out) {
        if (state.getBlock() instanceof InclineBeltBlock) {
            BlockPos low = pos.relative(state.getValue(FACING).getOpposite()).below();
            if (level.getBlockState(low).getBlock() instanceof ClumpBeltBlock) {
                out.add(low);
            }
        }
        if (state.getBlock() instanceof DownInclineBeltBlock) {
            BlockPos low = pos.relative(state.getValue(FACING)).below();
            if (level.getBlockState(low).getBlock() instanceof ClumpBeltBlock) {
                out.add(low);
            }
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos up = pos.relative(d).above();
            BlockState u = level.getBlockState(up);
            if (u.getBlock() instanceof InclineBeltBlock && u.getValue(FACING) == d) {
                out.add(up);
            }
            BlockPos downInclinePos = pos.relative(d.getOpposite()).above();
            BlockState di = level.getBlockState(downInclinePos);
            if (di.getBlock() instanceof DownInclineBeltBlock && di.getValue(FACING) == d) {
                out.add(downInclinePos);
            }
        }
    }

    @Override
    public void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        if (state.getValue(WATER) != powered && level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt) {
            setStopFlag(level, pos, state, belt, WATER, powered);
        }
    }

    private void markWater(Level level, BlockPos pos, BlockState state) {
        WaterNetworks.markDirty(level, pos);
        List<BlockPos> links = new ArrayList<>(2);
        addExtraLinks(level, pos, state, links);
        if (state.getBlock() instanceof InclineBeltBlock) {
            links.add(pos.relative(state.getValue(FACING).getOpposite()).below());
        }
        if (state.getBlock() instanceof DownInclineBeltBlock) {
            BlockPos low = pos.relative(state.getValue(FACING)).below();
            links.add(low);
            level.neighborChanged(low, this, pos);
        }
        for (BlockPos l : links) {
            WaterNetworks.markDirty(level, l);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt) {
            belt.setOwner(p.getUUID());
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) belt.setOwnerPlayer(sp);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && state.hasProperty(INPUT_SPEED)) {
            int nextSpeed = computeInputSpeed(level, pos, state.getValue(FACING));
            if (nextSpeed != state.getValue(INPUT_SPEED)) {
                state = state.setValue(INPUT_SPEED, nextSpeed);
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
        }
        if (!oldState.is(this) || oldState.getValue(FACING) != state.getValue(FACING)) {
            markWater(level, pos, state);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Block entity + ticking
    // ------------------------------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClumpBeltBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!running(state) || state.getValue(MODE) == BeltMode.EMPTY) {
            return null;
        }
        if (level.isClientSide) {
            return state.getValue(MODE) == BeltMode.MOVING
                    ? createTickerHelper(type, ModBlockEntities.CLUMP_BELT.get(), ClumpBeltBlockEntity::clientTick)
                    : null;
        }
        return createTickerHelper(type, ModBlockEntities.CLUMP_BELT.get(), ClumpBeltBlockEntity::serverTick);
    }

    // ------------------------------------------------------------------------------------------------
    // Item input (drills, belts, sluice trays)
    // ------------------------------------------------------------------------------------------------

    @Override
    public boolean canAcceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        return level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt && belt.canAccept(state, from);
    }

    @Override
    public void acceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        if (level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt) {
            belt.accept(from, level.random.nextInt(256), one);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Player
    // ------------------------------------------------------------------------------------------------

    public static boolean playerInsertable(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof BlockItem);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!playerInsertable(stack) || !(level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            int want = player.isShiftKeyDown() ? stack.getCount() : 1;
            int added = belt.insertByPlayer(stack, want);
            if (belt.owner() == null) {
                belt.setOwner(player.getUUID());
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) belt.setOwnerPlayer(sp);
            }
            if (added == 0) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.belt_full"), true);
                return ItemInteractionResult.CONSUME;
            }
            stack.consume(added, player);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.1F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt)
                || belt.clumpCount() == 0) {
            if (player.getMainHandItem().isEmpty() && !running(state)) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable(state.getValue(POWERED)
                            ? "message.holeydigsite.belt.redstone" : "message.holeydigsite.belt.no_water").withStyle(ChatFormatting.AQUA), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (belt.owner() == null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                belt.setOwnerPlayer(sp);
            }
            giveAll(level, pos, player, belt);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected static void giveAll(Level level, BlockPos pos, Player player, ClumpBeltBlockEntity belt) {
        for (ItemStack taken : belt.takeAll()) {
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);
    }

    // ------------------------------------------------------------------------------------------------
    // Breaking
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt) {
                for (ItemStack s : belt.contentsForDrops()) {
                    Block.popResource(level, pos, s);
                }
            }
            markWater(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ------------------------------------------------------------------------------------------------
    // Client effects
    // ------------------------------------------------------------------------------------------------

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id != EVENT_BOUNCE) {
            return super.triggerEvent(state, level, pos, id, param);
        }
        if (level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof ClumpBeltBlockEntity belt) {
                belt.addBounce(level.getGameTime(), param & 0xFF, Math.max(1, param >> 8));
            }
            Direction facing = state.getValue(FACING);
            double x = pos.getX() + 0.5 + facing.getStepX() * 0.62;
            double y = pos.getY() + 1.0;
            double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.62;
            BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
            for (int i = 0; i < 6; i++) {
                level.addParticle(dirt, x + (level.random.nextDouble() - 0.5) * 0.25, y + level.random.nextDouble() * 0.1,
                        z + (level.random.nextDouble() - 0.5) * 0.25, 0.0, 0.05, 0.0);
            }
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String key = "tooltip." + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).toLanguageKey();
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        if (this instanceof FilterBeltBlock || this instanceof SplitterBeltBlock) {
            tooltip.add(Component.translatable("tooltip.holeydigsite.belt.matches_input").withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.holeydigsite.clump_belt.water").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.holeydigsite.clump_belt.redstone").withStyle(ChatFormatting.DARK_GRAY));
    }
}
