package com.holeydigsite.forge;

import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Crucible sits on a lit {@link ForgeBlock}, melts one metal at a time up to 200 g and pours it into the
 * {@link MoldBlock} in front of it. Contents are stored in {@link CrucibleBlockEntity}.
 *
 * <ul>
 *   <li><b>Input</b> (only meltable resources like Folkiron and Goldchro chunks and ingots, with other items refused): right-click with a stack adds as many as fit by grams, sneak-right-click adds every item of the crucible's metal or the held item from the inventory, thrown items go in, and hoppers or other mods can insert via {@link CrucibleItemHandler} (insert-only). Nothing can be taken out except by pouring.</li>
 *   <li><b>Melting</b> while the forge below is lit ({@link #HEATED}): 1 g every 4 ticks. Only a melting crucible ticks.</li>
 *   <li><b>Pouring</b> into the mold in front (same level, or one block lower next to the forge): empty-hand right-click
 *       or a redstone rising edge. Pours the mold's missing grams or all that's molten if less. Tip animation uses
 *       {@link #TIPPING} for {@link #TIP_TICKS} ticks on a scheduled tick.</li>
 *   <li><b>Looks</b>: {@link #FILL} uses 0 to 4 molten levels, {@link #METAL} sets the glow colour, and {@link #CHUNKS} shows solid chunks while
 *       something is unmelted. The floating text showing molten and melting amounts is drawn by the renderer.</li>
 * </ul>
 * Breaking a crucible loses its contents, so pour first.
 */
public class CrucibleBlock extends BaseEntityBlock implements com.holeydigsite.transport.ItemTarget {
    public static final MapCodec<CrucibleBlock> CODEC = simpleCodec(CrucibleBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 4);
    public static final EnumProperty<MoltenMetal> METAL = EnumProperty.create("metal", MoltenMetal.class);
    public static final BooleanProperty CHUNKS = BooleanProperty.create("chunks");
    public static final BooleanProperty HEATED = BooleanProperty.create("heated");
    public static final BooleanProperty TIPPING = BooleanProperty.create("tipping");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final int TIP_TICKS = 10;
    /** How often an auto-pouring crucible with metal looks at its mold while it holds metal. */
    public static final int AUTO_POLL_TICKS = 10;

    private static final VoxelShape OUTLINE = Block.box(2, 0, 2, 14, 9, 14);
    /** Hollow cup where thrown items fall inside. */
    private static final VoxelShape COLLISION = Shapes.join(OUTLINE, Block.box(3, 1, 3, 13, 9, 13), BooleanOp.ONLY_FIRST);

    private final int capacity;
    private final int ticksPerGram;

    /** Basic crucible using stats from the crucible tier file. */
    public CrucibleBlock(Properties properties) {
        this(properties, CrucibleBlockEntity.CAPACITY, CrucibleBlockEntity.TICKS_PER_GRAM);
    }

    /** Crucible with custom size and speed from tier stats. */
    public CrucibleBlock(Properties properties, int capacity, int ticksPerGram) {
        super(properties);
        this.capacity = capacity;
        this.ticksPerGram = ticksPerGram;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FILL, 0)
                .setValue(METAL, MoltenMetal.NONE).setValue(CHUNKS, false).setValue(HEATED, false)
                .setValue(TIPPING, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Grams this crucible holds. */
    public int capacity() {
        return capacity;
    }

    /** Ticks per gram at heat 1. */
    public int ticksPerGram() {
        return ticksPerGram;
    }

    /** Checks if auto-pour is on in this state. */
    public boolean autoPour(BlockState state) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILL, METAL, CHUNKS, HEATED, TIPPING, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    /**
     * On a forge the spout always faces the forge's front however the player stands or sneaks, pouring
     * into the mold in front of the forge. Anywhere else the spout faces the player.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState().setValue(FACING, placementFacing(context.getLevel(), pos, context.getHorizontalDirection().getOpposite()))
                .setValue(HEATED, ForgeBlock.isLitForge(context.getLevel(), pos.below()))
                .setValue(POWERED, context.getLevel().hasNeighborSignal(pos));
    }

    /** Returns the forge's front if pos sits on a forge, else fallback. Public for tests. */
    public static Direction placementFacing(BlockGetter level, BlockPos pos, Direction fallback) {
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() instanceof ForgeBlock ? below.getValue(ForgeBlock.FACING) : fallback;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    /** Visual state for the given contents. */
    public static BlockState withContents(BlockState state, CrucibleBlockEntity c) {
        int step = Math.max(1, c.capacity() / 4);   // 50 g per level (125 g on the Large Crucible)
        int fill = c.molten() <= 0 ? 0 : Math.min(4, (c.molten() + step - 1) / step);
        return state.setValue(FILL, fill).setValue(METAL, MoltenMetal.of(c.metal())).setValue(CHUNKS, c.unmelted() > 0);
    }

    // Block entity and ticking

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrucibleBlockEntity(pos, state);
    }

    @Nullable
    public static CrucibleBlockEntity crucible(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CrucibleBlockEntity c ? c : null;
    }

    /** Only a crucible with unmelted metal on a lit forge ticks. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(CHUNKS) || !state.getValue(HEATED)) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CRUCIBLE.get(), CrucibleBlockEntity::serverTick);
    }

    /** Handles forge state changes or redstone updates. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        boolean heated = ForgeBlock.isLitForge(level, pos.below());
        boolean powered = level.hasNeighborSignal(pos);
        boolean risingEdge = powered && !state.getValue(POWERED);
        if (heated != state.getValue(HEATED) || powered != state.getValue(POWERED)) {
            state = state.setValue(HEATED, heated).setValue(POWERED, powered);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        if (risingEdge) {
            pour(level, pos, state, null);
        }
    }

    /**
     * Picks up forge heat and redstone level one tick after placement.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** Handles the end of the tip animation and after-placement checks. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState next = state.setValue(TIPPING, false)
                .setValue(HEATED, ForgeBlock.isLitForge(level, pos.below()))
                .setValue(POWERED, level.hasNeighborSignal(pos));
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        }
        if (autoPour(next)) {
            autoPourStep(level, pos, next);
        }
    }

    /** Pours if the mold in front can take the target right now, keeping looking while metal is left. */
    private void autoPourStep(ServerLevel level, BlockPos pos, BlockState state) {
        CrucibleBlockEntity c = crucible(level, pos);
        if (c == null) {
            return;
        }
        if (c.molten() > 0 && autoPourReady(level, pos, state, c)) {
            pour(level, pos, state, null);
        }
        armAuto(level, pos);
    }

    /** Checks if there is a mold in front that is empty or can be topped up with this metal and enough molten content. */
    public static boolean autoPourReady(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity c) {
        BlockPos moldPos = MoldBlock.findInFront(level, pos, state.getValue(FACING));
        MoldBlockEntity mold = moldPos == null ? null : MoldBlock.mold(level, moldPos);
        return mold != null && mold.missing() > 0 && c.metal() != null
                && (mold.metal() == null || mold.metal().equals(c.metal())) && c.molten() >= mold.missing();
    }

    /** Ensures an auto-pouring crucible holding metal gets a look at its mold soon. */
    public void armAuto(Level level, BlockPos pos) {
        if (level.isClientSide || !(level instanceof ServerLevel sl)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() == this && autoPour(state) && crucible(level, pos) != null && crucible(level, pos).total() > 0
                && !sl.getBlockTicks().hasScheduledTick(pos, this)) {
            sl.scheduleTick(pos, this, AUTO_POLL_TICKS);
        }
    }

    // Player interaction

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            if (player.isShiftKeyDown()) {
                addInventory(level, pos, player, stack);
            } else {
                addHeld(level, pos, player, stack);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                addInventory(level, pos, player, ItemStack.EMPTY);
            } else {
                pour(level, pos, state, player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Adds as much of the held stack as fits. Public for tests. Returns the number of items taken. */
    public static int addHeld(Level level, BlockPos pos, Player player, ItemStack stack) {
        CrucibleBlockEntity c = crucible(level, pos);
        if (c == null) {
            return 0;
        }
        CrucibleBlockEntity.Refusal why = c.check(stack);
        if (why != CrucibleBlockEntity.Refusal.NONE) {
            refuse(player, c, why);
            return 0;
        }
        int n = c.insert(stack, stack.getCount(), false);
        stack.shrink(n);
        addFeedback(level, pos, player, c);
        return n;
    }

    /**
     * Adds every item of one metal from the inventory as many as fit. Public for tests. Returns the number of items taken.
     */
    public static int addInventory(Level level, BlockPos pos, Player player, ItemStack held) {
        CrucibleBlockEntity c = crucible(level, pos);
        if (c == null) {
            return 0;
        }
        Inventory inv = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>(inv.items);
        stacks.addAll(inv.offhand);
        ResourceLocation metal = c.metal();
        if (metal == null && Metals.meltable(held) != null) {
            metal = Metals.meltable(held).material();
        }
        if (metal == null) {
            for (ItemStack s : stacks) {
                ResourceInfo info = Metals.meltable(s);
                if (info != null) {
                    metal = info.material();
                    break;
                }
            }
        }
        if (metal == null) {
            refuse(player, c, held.isEmpty() ? CrucibleBlockEntity.Refusal.NOT_METAL : c.check(held));
            return 0;
        }
        int total = 0;
        for (ItemStack s : stacks) {
            ResourceInfo info = Metals.meltable(s);
            if (info != null && info.material().equals(metal)) {
                int n = c.insert(s, s.getCount(), false);
                s.shrink(n);
                total += n;
            }
        }
        inv.setChanged();
        if (total == 0) {
            refuse(player, c, c.room() <= 0 ? CrucibleBlockEntity.Refusal.FULL : CrucibleBlockEntity.Refusal.NOT_METAL);
        } else {
            addFeedback(level, pos, player, c);
        }
        return total;
    }

    private static void addFeedback(Level level, BlockPos pos, @Nullable Player player, CrucibleBlockEntity c) {
        level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.crucible.added",
                    Metals.name(c.metal()), c.total(), c.capacity()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static void refuse(Player player, CrucibleBlockEntity c, CrucibleBlockEntity.Refusal why) {
        Component msg = switch (why) {
            case GEM -> Component.translatable("message.holeydigsite.crucible.gem");
            case OTHER_METAL -> Component.translatable("message.holeydigsite.crucible.other_metal", Metals.name(c.metal()));
            case FULL -> Component.translatable("message.holeydigsite.crucible.full", c.capacity());
            default -> Component.translatable("message.holeydigsite.crucible.not_metal");
        };
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
    }

    // Thrown items

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel) || !(entity instanceof ItemEntity item) || !item.isAlive()) {
            return;
        }
        CrucibleBlockEntity c = crucible(level, pos);
        ItemStack stack = item.getItem();
        if (c == null || c.fits(stack) <= 0) {
            return;
        }
        int n = c.insert(stack, stack.getCount(), false);
        ItemStack rest = stack.copy();
        rest.shrink(n);
        if (rest.isEmpty()) {
            item.discard();
        } else {
            item.setItem(rest);
        }
        addFeedback(level, pos, item.getOwner() instanceof Player p ? p : null, c);
    }

    // Pouring

    /**
     * Pours into the mold in front. Public for tests. Returns grams poured.
     */
    public static int pour(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
        CrucibleBlockEntity c = crucible(level, pos);
        if (c == null) {
            return 0;
        }
        Direction facing = state.getValue(FACING);
        BlockPos moldPos = MoldBlock.findInFront(level, pos, facing);
        MoldBlockEntity mold = moldPos == null ? null : MoldBlock.mold(level, moldPos);
        String problem = null;
        Object[] args = new Object[0];
        if (mold == null) {
            problem = "message.holeydigsite.crucible.no_mold";
        } else if (c.molten() <= 0) {
            problem = c.unmelted() > 0 ? "message.holeydigsite.crucible.still_melting" : "message.holeydigsite.crucible.empty";
        } else if (mold.metal() != null && !mold.metal().equals(c.metal())) {
            problem = "message.holeydigsite.crucible.mold_other_metal";
            args = new Object[]{Metals.name(mold.metal())};
        } else if (mold.missing() <= 0) {
            problem = "message.holeydigsite.crucible.mold_full";
        }
        if (problem != null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable(problem, args).withStyle(ChatFormatting.RED), true);
            }
            return 0;
        }
        ResourceLocation metal = c.metal();
        int grams = c.takeMolten(Math.min(mold.missing(), c.molten()));
        mold.pour(metal, grams);   // the mold restarts its cooling
        level.setBlock(pos, level.getBlockState(pos).setValue(TIPPING, true), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), TIP_TICKS);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, moldPos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
        if (level instanceof ServerLevel sl) {
            // a stream from the spout down into the mold, then a few splashes in the mold
            double sx = pos.getX() + 0.5 + facing.getStepX() * 0.62;
            double sz = pos.getZ() + 0.5 + facing.getStepZ() * 0.62;
            sl.sendParticles(ParticleTypes.FALLING_LAVA, sx, pos.getY() + 0.45, sz, 6, 0.03, 0.05, 0.03, 0);
            sl.sendParticles(ParticleTypes.LAVA, moldPos.getX() + 0.5, moldPos.getY() + 0.3, moldPos.getZ() + 0.5, 3, 0.15, 0.05, 0.15, 0);
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.crucible.poured", grams, Metals.name(metal),
                    mold.grams(), mold.target()).withStyle(ChatFormatting.GOLD), true);
        }
        return grams;
    }

    // Client

    /** Bubbles over molten metal on a lit forge. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HEATED) && state.getValue(FILL) > 0 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.75, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.crucible").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.crucible.how").withStyle(ChatFormatting.DARK_GRAY));
    }

    // Belts deliver meltables following hopper rules.

    @Override
    public boolean canAcceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        return level.getBlockEntity(pos) instanceof CrucibleBlockEntity c && c.insert(one, 1, true) == 1;
    }

    @Override
    public void acceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        if (level.getBlockEntity(pos) instanceof CrucibleBlockEntity c) {
            c.insert(one, 1, false);
        }
    }
}
