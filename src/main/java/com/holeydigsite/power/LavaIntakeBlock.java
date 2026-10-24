package com.holeydigsite.power;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * The power source of a lava network that works like the {@link WaterIntakeBlock} by sitting
 * inside real lava as lava-logged and remaining active while {@link #LAVALOGGED} is true.
 *
 * <ul>
 *   <li>When placed into a lava source block, it keeps that lava as a real source sharing the block, where {@link #getFluidState}
 *       returns still lava so it renders around it. Placed into flowing lava, it replaces the flowing lava and stays inactive without duplicating lava.</li>
 *   <li>A lava bucket emptied onto an inactive intake lava-logs it, and an empty bucket on a lava-logged intake picks the
 *       lava up again using {@link LiquidBlockContainer}, {@link BucketPickup}, and {@link #useItemOn} so the click is
 *       handled directly on the block, resulting in exactly one bucket in and one bucket out.</li>
 *   <li>Breaking the intake drops only the intake and leaves the lava behind as a normal lava source block, similar to how breaking a waterlogged block leaves water.</li>
 *   <li>Lava is never consumed by the network.</li>
 * </ul>
 *
 * <p>Flowing lava never lava-logs it because {@link #canPlaceLiquid} only accepts the source fluid from buckets and dispensers, preventing
 * adjacent flowing lava from entering or turning it into a source.</p>
 *
 * <p>It has no block entity and never ticks, marking the network dirty only when {@link #LAVALOGGED} changes or when placed and
 * removed. Collar pipe booleans are visual, matching the {@link WaterIntakeBlock}.</p>
 */
public class LavaIntakeBlock extends Block implements BucketPickup, LiquidBlockContainer, WaterNetworkBlock {
    public static final MapCodec<LavaIntakeBlock> CODEC = simpleCodec(LavaIntakeBlock::new);
    /** True when the intake holds a real lava source as its active state. */
    public static final BooleanProperty LAVALOGGED = BooleanProperty.create("lavalogged");

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public LavaIntakeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(LAVALOGGED, false);
        for (BooleanProperty side : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAVALOGGED, PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ---- lava ----

    private static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), WaterNetworkBlock.connects(level, pos, state, dir));
        }
        return state;
    }

    /** Lava-logged only when placed into a lava source, while flowing lava is simply replaced without creating a source. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        BlockState state = defaultBlockState().setValue(LAVALOGGED, fluid.isSourceOfType(Fluids.LAVA));
        return withConnections(context.getLevel(), context.getClickedPos(), state);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(LAVALOGGED) ? Fluids.LAVA.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(LAVALOGGED)) {
            // let the lava inside the intake keep flowing into neighbours, like a normal source
            level.scheduleTick(pos, Fluids.LAVA, Fluids.LAVA.getTickDelay(level));
        }
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                WaterNetworkBlock.connects(level, pos, state, direction));
    }

    // ---- buckets (same contract as vanilla waterlogging, for lava) ----

    /** Only the lava source fluid from buckets lava-logs it, while flowing lava never does. */
    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return !state.getValue(LAVALOGGED) && fluid == Fluids.LAVA;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!state.getValue(LAVALOGGED) && fluidState.getType() == Fluids.LAVA) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(LAVALOGGED, true), Block.UPDATE_ALL);
                level.scheduleTick(pos, Fluids.LAVA, Fluids.LAVA.getTickDelay(level));
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(LAVALOGGED)) {
            level.setBlock(pos, state.setValue(LAVALOGGED, false), Block.UPDATE_ALL);
            return new ItemStack(Items.LAVA_BUCKET);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Fluids.LAVA.getPickupSound();
    }

    /**
     * Handles bucket interactions directly on the block using {@link #placeLiquid} and {@link #pickupBlock} so one bucket goes in and one comes out.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        boolean lavalogged = state.getValue(LAVALOGGED);
        if (stack.is(Items.LAVA_BUCKET) && !lavalogged) {
            if (!level.isClientSide) {
                if (placeLiquid(level, pos, state, Fluids.LAVA.getSource(false))) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Items.BUCKET) && lavalogged) {
            if (!level.isClientSide) {
                ItemStack lavaBucket = pickupBlock(player, level, pos, state);
                if (!lavaBucket.isEmpty()) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, lavaBucket));
                    level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // ---- network ----

    @Override
    public Role waterRole() {
        return Role.INTAKE;
    }

    @Override
    public NetworkFluid networkFluid() {
        return NetworkFluid.LAVA;
    }

    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return true;
    }

    /** True if lava-logged. */
    @Override
    public boolean isActiveIntake(BlockState state) {
        return state.getValue(LAVALOGGED);
    }

    /** Recomputes the network when placed or when lava is added and removed via buckets, ignoring collar changes. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) || oldState.getValue(LAVALOGGED) != state.getValue(LAVALOGGED)) {
            WaterNetworks.markDirty(level, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    /**
     * Recomputes the network on removal without dropping extra items, leaving the lava behind in the world as a normal source block.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this)) {
            WaterNetworks.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ---- client ----

    /** Spawns lava pop particles above a lava-logged intake periodically. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LAVALOGGED) && random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0, 0, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.lava_intake").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.lava_intake.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
