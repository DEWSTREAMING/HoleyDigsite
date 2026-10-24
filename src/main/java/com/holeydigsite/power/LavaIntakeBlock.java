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
 * Lava Intake: the power source of a LAVA network. Since Phase 9.1 it works like the {@link WaterIntakeBlock}: it sits
 * INSIDE real lava ("lava-logged") and is active exactly while {@link #LAVALOGGED}.
 *
 * <ul>
 *   <li>Placed into a lava SOURCE block it keeps that lava as a real source sharing the block ({@link #getFluidState}
 *       returns still lava, so the lava keeps rendering around it). Placed into FLOWING lava it just replaces the flowing
 *       lava and stays inactive - lava is never duplicated.</li>
 *   <li>A Lava Bucket emptied onto an inactive intake lava-logs it; an empty bucket on a lava-logged intake picks the
 *       lava up again ({@link LiquidBlockContainer} / {@link BucketPickup}, plus {@link #useItemOn} so the click is
 *       handled right on the block). Exactly one bucket in, one bucket out.</li>
 *   <li>Breaking the intake drops ONLY the intake (the loot table never drops a bucket); the lava stays behind as a
 *       normal lava source block, like breaking a waterlogged block leaves the water.</li>
 *   <li>Lava is never consumed by the network.</li>
 * </ul>
 *
 * <p>Flowing lava never lava-logs it: {@link #canPlaceLiquid} only accepts the SOURCE fluid (buckets, dispensers), so
 * lava flowing next to an empty intake can neither enter it nor turn it into a source.</p>
 *
 * <p>No block entity, never ticks: the network is marked dirty only when LAVALOGGED changes (or the block is placed /
 * removed). Collars ({@link PipeBlock} booleans) are visual, like the {@link WaterIntakeBlock}'s.</p>
 */
public class LavaIntakeBlock extends Block implements BucketPickup, LiquidBlockContainer, WaterNetworkBlock {
    public static final MapCodec<LavaIntakeBlock> CODEC = simpleCodec(LavaIntakeBlock::new);
    /** The intake holds a real lava source ("lava-logged"). This IS the active state of the intake. */
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

    /** Lava-logged only when placed into a lava SOURCE. Flowing lava is simply replaced (no source is created). */
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

    /** Only the lava SOURCE fluid (what a bucket holds) lava-logs it; flowing lava ({@code FLOWING_LAVA}) never does. */
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
     * Lava Bucket onto an empty intake lava-logs it (the bucket becomes empty); an empty Bucket onto a lava-logged intake
     * takes the lava out (you get the Lava Bucket). Handled right on the block so the click always lands here; it uses
     * the same {@link #placeLiquid} / {@link #pickupBlock} as the bucket item itself, so one bucket in = one bucket out.
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

    /** Active = lava-logged. */
    @Override
    public boolean isActiveIntake(BlockState state) {
        return state.getValue(LAVALOGGED);
    }

    /** Placed, or lava added/removed (bucket): recompute. Collar changes don't touch the network. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) || oldState.getValue(LAVALOGGED) != state.getValue(LAVALOGGED)) {
            WaterNetworks.markDirty(level, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    /**
     * Removed: recompute. Nothing is dropped here - the lava stays in the world as a normal lava source (the block's
     * fluid state becomes the replacement block), and the loot table only ever drops the intake itself.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(this)) {
            WaterNetworks.markDirty(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ---- client ----

    /** Now and then a lava pop above a lava-logged intake. */
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
