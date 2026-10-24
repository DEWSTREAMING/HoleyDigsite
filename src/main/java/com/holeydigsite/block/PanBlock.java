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
 * The gold pan: a shallow, waterloggable block that holds 0-{@value #MAX_FILL} scoops of dirt.
 *
 * <p>Interactions:
 * <ul>
 *   <li>Right-click with a full wooden shovel: fill +1, shovel becomes empty (nothing happens at max fill).</li>
 *   <li>Right-click with a Dirt Clump: fill +1, uses one clump (exactly like a full shovel).</li>
 *   <li>Automation: a Mining Drill in front outputs clumps into it ({@link DirtClumpTarget}), and hoppers /
 *       other mods can insert clumps through the item-handler capability ({@link PanItemHandler}).</li>
 *   <li>Hold right-click with the Brush on a waterlogged pan with fill > 0: pan it for loot
 *       (see {@link com.holeydigsite.panning.Panning}).</li>
 *   <li>Right-click with an empty main hand: pick the pan up into the inventory.</li>
 *   <li>Left-click (punch): pick the pan up - handled in {@link com.holeydigsite.event.PanEvents}
 *       so it also works instantly in creative mode.</li>
 * </ul>
 * The picked-up item stores the fill level (and tier) in the vanilla {@code minecraft:block_state} component,
 * which vanilla {@code BlockItem} re-applies automatically when the pan is placed again.</p>
 *
 * <p>Phase 7 - one dirt tier at a time ({@link #TIER}): a pan with dirt in it only takes more dirt of the same tier
 * (shovel dirt is tier 1; clumps carry theirs) and shows a hint otherwise. When it is panned empty it goes back to
 * tier 1 and takes anything again. The tier picks the loot table and darkens the dirt in the model.</p>
 */
public class PanBlock extends Block implements SimpleWaterloggedBlock, DirtClumpTarget {
    public static final MapCodec<PanBlock> CODEC = simpleCodec(PanBlock::new);

    /** Maximum number of dirt scoops a pan holds. Models exist for fill 0..4 - add more if you raise this. */
    public static final int MAX_FILL = 4;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MAX_FILL);
    /** Phase 10 Large Pan: 8 scoops. Same property NAME ("fill"), so blockstates, loot tables and items work alike. */
    public static final int LARGE_MAX_FILL = 8;
    public static final IntegerProperty LARGE_FILL = IntegerProperty.create("fill", 0, LARGE_MAX_FILL);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** Tier of the dirt inside (1-3). Meaningless (always reset to 1) while the pan is empty. */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", DirtTier.MIN, DirtTier.MAX);

    /** Outline/collision: 14x14 pixels wide, 4 pixels tall (the pan's rim height). */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 4, 15);

    public PanBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(fillProperty(), 0).setValue(WATERLOGGED, false).setValue(TIER, 1));
    }

    /**
     * The fill property of this pan: {@link #FILL} (0-4) or, for the Large Pan, {@link #LARGE_FILL} (0-8). Overridden by
     * {@link LargePanBlock}; it must only return a static constant (it is called from the constructor).
     */
    public IntegerProperty fillProperty() {
        return FILL;
    }

    /** Scoops this pan holds. */
    public int maxFill() {
        return MAX_FILL;
    }

    // ---- fill helpers that work for every pan size (use these, not the FILL constant, on any pan state) ----

    /** The fill property of the pan in {@code state}. */
    public static IntegerProperty fillProp(BlockState state) {
        return state.getBlock() instanceof PanBlock p ? p.fillProperty() : FILL;
    }

    /** Scoops of dirt in the pan in {@code state}. */
    public static int fill(BlockState state) {
        return state.getValue(fillProp(state));
    }

    /** Capacity of the pan in {@code state}. */
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

    // ------------------------------------------------------------------------------------------------
    // Placement & waterlogging
    // ------------------------------------------------------------------------------------------------

    /**
     * Waterlogs the pan when placed in ANY water - source or flowing - so it always "sits in water"
     * (Phase 2 panning only needs to check {@link #WATERLOGGED}). Note a waterlogged block holds a water
     * source, so placing a pan in flowing water turns that spot into a source block.
     * The fill level is applied afterwards by BlockItem from the item's block_state component.
     */
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
            // let the water inside the pan keep flowing into neighbours
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ------------------------------------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------------------------------------

    /** Right-click with an item: the brush starts panning, a full wooden shovel or a Dirt Clump adds dirt. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof com.holeydigsite.item.BrushItem) {
            // CONSUME (not success) so the arm doesn't swing - the brush use animation takes over
            Panning.tryStartPanning(state, level, pos, player, hand);
            return ItemInteractionResult.CONSUME;
        }
        boolean clump = DirtTier.isClump(stack);
        if (!clump && !ShovelHelper.isFull(stack)) {
            // Anything else (incl. empty hand) falls through to useWithoutItem / normal item use.
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (fill(state) >= maxFill(state)) {
            // Pan is full: do nothing, but consume the click so the item doesn't try anything else.
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.pan_full"), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        int tier = clump ? DirtTier.ofClump(stack) : ShovelHelper.kind(stack); // Phase 11: the shovel carries dirt, gravel or sand
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
                stack.consume(1, player); // creative players keep the clump, like other vanilla item uses
            } else {
                ShovelHelper.setFull(stack, false);
            }
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** True if dirt of {@code tier} may go in: the pan is empty, or already holds that tier. */
    public static boolean acceptsTier(BlockState state, int tier) {
        return fill(state) == 0 || state.getValue(TIER) == tier;
    }

    /**
     * Server: adds {@code amount} scoops of {@code tier} dirt (caller checks there is room and the tier fits). One place
     * for the block update
     * and the sound, shared by shovel, clump, drill and hopper input.
     */
    public static void addDirt(Level level, BlockPos pos, BlockState state, int amount, int tier) {
        int fill = Math.min(maxFill(state), fill(state) + amount);
        level.setBlock(pos, withFill(state, fill).setValue(TIER, DirtTier.clamp(tier)), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
    }

    // ---- Mining Drill output ----

    @Override
    public boolean canAcceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier) {
        return fill(state) < maxFill(state) && acceptsTier(state, tier);
    }

    @Override
    public void acceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier) {
        addDirt(level, pos, state, 1, tier);
    }

    // ---- capability bookkeeping ----

    /** Hoppers query the item handler fresh each time, but other mods may cache it: tell them when a pan appears/goes. */
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

    /**
     * Right-click with nothing in the main hand: pick the pan up.
     * (Vanilla also calls this after useItemOn passes while holding an item, so the hand check matters.)
     */
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

    /**
     * Removes the pan from the world and puts it (with its fill level) straight into the player's inventory,
     * dropping it at their feet if the inventory is full. A waterlogged pan leaves its water behind.
     * Server side only.
     */
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

    // ------------------------------------------------------------------------------------------------
    // Item <-> fill level
    // ------------------------------------------------------------------------------------------------

    /** A pan item with tier 1 dirt (or empty). */
    public ItemStack createStack(int fill) {
        return createStack(fill, 1);
    }

    /**
     * A pan item with the given fill level and dirt tier. Empty pans get no component so they stack with fresh pans;
     * tier 1 pans store only the fill.
     */
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

    /** Reads the dirt tier stored on a pan item (1 if none). */
    public static int getTier(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        Integer tier = props == null ? null : props.get(TIER);
        return tier == null ? 1 : DirtTier.clamp(tier);
    }

    /** Reads the fill level stored on a pan item (0 if none); works for both pan sizes. */
    public static int getFill(ItemStack stack) {
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        IntegerProperty prop = stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof PanBlock p
                ? p.fillProperty() : FILL;
        Integer fill = props == null ? null : props.get(prop);
        return fill == null ? 0 : fill;
    }

    /** Capacity of a pan item (4, or 8 for the Large Pan). */
    public static int getMaxFill(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof PanBlock p ? p.maxFill() : MAX_FILL;
    }

    /** Middle-click (pick block) in creative gives a pan with the same fill level. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return createStack(fill(state), state.getValue(TIER));
    }
}
