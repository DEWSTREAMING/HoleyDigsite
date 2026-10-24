package com.holeydigsite.block;

import com.holeydigsite.drill.DirtClumpTarget;
import com.holeydigsite.panning.Panning;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shovel.ShovelHelper;
import com.holeydigsite.tier.DirtTier;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The gold pan: a shallow, waterloggable block that holds 0 to MAX_FILL scoops of dirt.
 *
 * <p>Interactions:
 * <ul>
 *   <li>Right-click with a full wooden shovel: fill increases by 1, shovel becomes empty.</li>
 *   <li>Right-click with a Dirt Clump: fill increases by 1, uses one clump.</li>
 *   <li>Automation: a Mining Drill outputs clumps into it, and hoppers or other mods can insert clumps.</li>
 *   <li>Hold right-click with the Brush on a waterlogged pan with fill > 0 to pan for loot.</li>
 *   <li>Right-click with an empty main hand: pick the pan up into the inventory.</li>
 *   <li>Left-click (punch): pick the pan up instantly.</li>
 * </ul>
 * The picked-up item stores the fill level and tier in the block state component,
 * which the block item re-applies automatically when placed again.</p>
 *
 * <p>Pans only take more dirt of the same tier if already containing dirt.
 * When emptied, it resets to tier 1 and accepts anything again.</p>
 */
public class PanBlock extends Block implements SimpleWaterloggedBlock, DirtClumpTarget {
    public static final MapCodec<PanBlock> CODEC = simpleCodec(PanBlock::new);

    public static final int MAX_FILL = 4;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MAX_FILL);
    public static final int LARGE_MAX_FILL = 8;
    public static final IntegerProperty LARGE_FILL = IntegerProperty.create("fill", 0, LARGE_MAX_FILL);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty TIER = IntegerProperty.create("tier", DirtTier.MIN, DirtTier.MAX);

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 4, 15);

    public PanBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(fillProperty(), 0).setValue(WATERLOGGED, false).setValue(TIER, 1));
    }

    public IntegerProperty fillProperty() {
        return FILL;
    }

    public int maxFill() {
        return MAX_FILL;
    }

    public static IntegerProperty fillProp(BlockState state) {
        return state.getBlock() instanceof PanBlock p ? p.fillProperty() : FILL;
    }

    public static int fill(BlockState state) {
        return state.getValue(fillProp(state));
    }

    public static int maxFill(BlockState state) {
        return state.getBlock() instanceof PanBlock p ? p.maxFill() : MAX_FILL;
    }

    public static BlockState withFill(BlockState state, int fill) {
        return state.setValue(fillProp(state), fill);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(fillProperty(), WATERLOGGED, TIER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(WATERLOGGED, fluid.is(FluidTags.WATER));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof com.holeydigsite.item.BrushItem) {
            Panning.tryStartPanning(state, level, pos, player, hand);
            return ItemInteractionResult.CONSUME;
        }
        boolean clump = DirtTier.isClump(stack);
        if (!clump && !ShovelHelper.isFull(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (fill(state) >= maxFill(state)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.pan_full"), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        int tier = clump ? DirtTier.ofClump(stack) : ShovelHelper.kind(stack);
        if (!acceptsTier(state, tier)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.pan_other_tier",
                        Component.translatable(DirtTier.dirtKey(state.getValue(TIER)))), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        if (!level.isClientSide) {
            addDirt(level, pos, state, 1, tier);
            if (clump) {
                stack.consume(1, player);
            } else {
                ShovelHelper.setFull(stack, false);
            }
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean acceptsTier(BlockState state, int tier) {
        return fill(state) == 0 || state.getValue(TIER) == tier;
    }

    public static void addDirt(Level level, BlockPos pos, BlockState state, int amount, int tier) {
        int fill = Math.min(maxFill(state), fill(state) + amount);
        level.setBlock(pos, withFill(state, fill).setValue(TIER, DirtTier.clamp(tier)), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
    }

    @Override
    public boolean canAcceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier) {
        return fill(state) < maxFill(state) && acceptsTier(state, tier);
    }

    @Override
    public void acceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier) {
        addDirt(level, pos, state, 1, tier);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(this)) {
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            pickUp(state, level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public void pickUp(BlockState state, Level level, BlockPos pos, Player player) {
        ItemStack stack = createStack(fill(state), state.getValue(TIER));
        BlockState replacement = state.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();

        level.setBlock(pos, replacement, Block.UPDATE_ALL);
        level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F,
                1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public ItemStack createStack(int fill) {
        return createStack(fill, 1);
    }

    public ItemStack createStack(int fill, int tier) {
        ItemStack stack = new ItemStack(this);
        if (fill > 0) {
            BlockItemStateProperties props = BlockItemStateProperties.EMPTY.with(fillProperty(), Math.min(fill, maxFill()));
            if (DirtTier.clamp(tier) > 1) {
                props = props.with(TIER, DirtTier.clamp(tier));
            }
            stack.set(DataComponents.BLOCK_STATE, props);
        }
        return stack;
    }

    public static int getTier(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        Integer tier = props == null ? null : props.get(TIER);
        return tier == null ? 1 : DirtTier.clamp(tier);
    }

    public static int getFill(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        IntegerProperty prop = stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof PanBlock p
                ? p.fillProperty() : FILL;
        Integer fill = props == null ? null : props.get(prop);
        return fill == null ? 0 : fill;
    }

    public static int getMaxFill(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof PanBlock p ? p.maxFill() : MAX_FILL;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return createStack(fill(state), state.getValue(TIER));
    }
}
