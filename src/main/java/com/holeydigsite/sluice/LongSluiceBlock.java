package com.holeydigsite.sluice;

import com.holeydigsite.plot.PlotLock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Tier 2 sluice: two blocks in a line, the tray end towards the player, and a chute on top of the back end.
 * The back part holds the block entity (queue, tray, inserts, water ports); the other two parts are shells that
 * pass everything on to it. Placing needs the front and chute space free, and breaking any part breaks all three.
 * Clumps go in at the chute, from the top or any side but the front.
 */
public class LongSluiceBlock extends SluiceBoxBlock {
    public static final EnumProperty<SluicePart> PART = EnumProperty.create("part", SluicePart.class);
    /** The second insert, shown on the front part (the first one shows on the back part). */
    public static final EnumProperty<SluiceUpgrade> UPGRADE2 = EnumProperty.create("upgrade2", SluiceUpgrade.class);

    private static final VoxelShape CHANNEL_Z = Shapes.or(
            Block.box(0, 0, 0, 16, 5, 16), Block.box(0, 5, 0, 2, 13, 16), Block.box(14, 5, 0, 16, 13, 16));
    private static final VoxelShape CHANNEL_X = Shapes.or(
            Block.box(0, 0, 0, 16, 5, 16), Block.box(0, 5, 0, 16, 13, 2), Block.box(0, 5, 14, 16, 13, 16));
    private static final VoxelShape CHUTE = Shapes.or(
            Block.box(4, 0, 4, 12, 3, 12), Block.box(2, 3, 2, 14, 7, 14),
            Shapes.join(Block.box(0, 7, 0, 16, 14.5, 16), Block.box(3, 8, 3, 13, 15, 13), BooleanOp.ONLY_FIRST));

    public static final MapCodec<LongSluiceBlock> LONG_CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.INT.optionalFieldOf("cycle_ticks", Stats.T2.cycleTicks()).forGetter(b -> b.stats().cycleTicks()),
            com.mojang.serialization.Codec.INT.optionalFieldOf("max_queue", Stats.T2.maxQueue()).forGetter(b -> b.stats().maxQueue()),
            com.mojang.serialization.Codec.INT.optionalFieldOf("tray_slots", Stats.T2.traySlots()).forGetter(b -> b.stats().traySlots()),
            propertiesCodec()).apply(i, (cycle, queue, tray, props) -> new LongSluiceBlock(props, new Stats(cycle, queue, tray))));

    public LongSluiceBlock(Properties properties, Stats stats) {
        super(properties, stats);
        registerDefaultState(defaultBlockState().setValue(PART, SluicePart.BACK).setValue(UPGRADE2, SluiceUpgrade.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return LONG_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART, UPGRADE2);
    }

    public static SluicePart part(BlockState state) {
        return state.getValue(PART);
    }

    // ------------------------------------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------------------------------------

    @Override
    public BlockPos entityPos(BlockPos pos, BlockState state) {
        return switch (state.getValue(PART)) {
            case BACK -> pos;
            case FRONT -> pos.relative(state.getValue(FACING).getOpposite());
            case CHUTE -> pos.below();
        };
    }

    /** Positions of the three parts, from the back part's position. */
    public static BlockPos frontPos(BlockPos back, BlockState state) {
        return back.relative(state.getValue(FACING));
    }

    @Override
    public List<BlockPos> allParts(BlockPos entityPos, BlockState state) {
        return List.of(entityPos, frontPos(entityPos, state), entityPos.above());
    }

    @Override
    protected boolean isEntityPart(BlockState state) {
        return state.getValue(PART) == SluicePart.BACK;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == SluicePart.BACK ? super.newBlockEntity(pos, state) : null;
    }

    @Override
    public BlockPos trayOutputPos(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING), 2);
    }

    @Override
    public boolean feedsForward(BlockState state) {
        return state.getValue(PART) == SluicePart.FRONT;
    }

    @Override
    public int insertSlots() {
        return 2;
    }

    @Override
    public List<SluiceUpgrade> inserts(BlockState state) {
        List<SluiceUpgrade> out = new ArrayList<>(2);
        if (state.getValue(UPGRADE) != SluiceUpgrade.NONE) out.add(state.getValue(UPGRADE));
        if (state.getValue(UPGRADE2) != SluiceUpgrade.NONE) out.add(state.getValue(UPGRADE2));
        return out;
    }

    @Override
    protected BlockState withInserts(BlockState state, List<SluiceUpgrade> list) {
        return state.setValue(UPGRADE, list.size() > 0 ? list.get(0) : SluiceUpgrade.NONE)
                .setValue(UPGRADE2, list.size() > 1 ? list.get(1) : SluiceUpgrade.NONE);
    }

    @Override
    protected boolean takesHandClumps(BlockState state) {
        return state.getValue(PART) == SluicePart.CHUTE;
    }

    @Override
    protected String portsTooltipKey() {
        return "tooltip.holeydigsite.sluice_box_t2.ports";
    }

    /** Sets the entity part to {@code newState} and gives the other two parts the same facing, status, dirt and inserts. */
    @Override
    protected void placeState(Level level, BlockPos entityPos, BlockState newState, int flags) {
        BlockState back = newState.setValue(PART, SluicePart.BACK);
        BlockPos front = frontPos(entityPos, back);
        BlockPos chute = entityPos.above();
        if (level.getBlockState(entityPos) != back) {
            level.setBlock(entityPos, back, flags | Block.UPDATE_KNOWN_SHAPE);
        }
        BlockState f = back.setValue(PART, SluicePart.FRONT);
        BlockState c = back.setValue(PART, SluicePart.CHUTE);
        BlockState oldF = level.getBlockState(front);
        BlockState oldC = level.getBlockState(chute);
        if (oldF.is(this) && oldF != f) {
            level.setBlock(front, f, flags | Block.UPDATE_KNOWN_SHAPE);
        }
        if (oldC.is(this) && oldC != c) {
            level.setBlock(chute, c, flags | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Placing, shape, breaking
    // ------------------------------------------------------------------------------------------------

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos front = pos.relative(facing);
        BlockPos top = pos.above();
        Player player = context.getPlayer();
        boolean room = top.getY() < level.getMaxBuildHeight() && level.getBlockState(front).canBeReplaced(context)
                && level.getBlockState(top).canBeReplaced(context);
        if (!room) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.sluice.long_no_room"), true);
            }
            return null;
        }
        if (player != null && !level.isClientSide && (!PlotLock.canUse(player, level, front) || !PlotLock.canUse(player, level, top))) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this) && state.getValue(PART) == SluicePart.BACK) {
            BlockPos front = frontPos(pos, state);
            BlockPos top = pos.above();
            if (canFill(level, front)) {
                level.setBlock(front, state.setValue(PART, SluicePart.FRONT), Block.UPDATE_ALL);
            }
            if (canFill(level, top)) {
                level.setBlock(top, state.setValue(PART, SluicePart.CHUTE), Block.UPDATE_ALL);
            }
        }
    }

    private static boolean canFill(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || s.canBeReplaced();
    }

    /** A part whose partner went away disappears: the last one standing drops the sluice and its contents. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        SluicePart part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        SluicePart want = null;
        switch (part) {
            case BACK -> {
                if (direction == facing) want = SluicePart.FRONT;
                else if (direction == Direction.UP) want = SluicePart.CHUTE;
            }
            case FRONT -> {
                if (direction == facing.getOpposite()) want = SluicePart.BACK;
            }
            case CHUTE -> {
                if (direction == Direction.DOWN) want = SluicePart.BACK;
            }
        }
        if (want != null && !(neighbor.getBlock() instanceof LongSluiceBlock && neighbor.getValue(PART) == want
                && neighbor.getValue(FACING) == facing)) {
            if (part == SluicePart.BACK && level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity be && be.isOldLayout()) {
                return state;
            }
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(PART) != SluicePart.BACK) {
            BlockPos back = entityPos(pos, state);
            BlockState b = level.getBlockState(back);
            if (b.is(this) && b.getValue(PART) == SluicePart.BACK) {
                if (player.getAbilities().instabuild) {
                    level.setBlock(back, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, back, Block.getId(b));
                } else {
                    level.destroyBlock(back, true, player);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** One item per sluice: only the back part drops it. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return state.getValue(PART) == SluicePart.BACK ? super.getDrops(state, params) : List.of();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(PART) == SluicePart.CHUTE) {
            return CHUTE;
        }
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? CHANNEL_Z : CHANNEL_X;
    }

    // ------------------------------------------------------------------------------------------------
    // Input and water
    // ------------------------------------------------------------------------------------------------

    @Override
    public boolean canAcceptClump(Level level, BlockPos pos, BlockState state, Direction from, int tier) {
        return state.getValue(PART) == SluicePart.CHUTE && from != state.getValue(FACING) && from != Direction.DOWN
                && entityAt(level, pos) != null && entityAt(level, pos).hasQueueRoom();
    }

    /** Ports on both sides of the back part, nowhere else. */
    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return state.getValue(PART) == SluicePart.BACK && super.hasWaterConnection(state, side);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != SluicePart.CHUTE) {
            super.animateTick(state, level, pos, random);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Sluices of old worlds
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isEntityPart(state) && level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity be && be.isOldLayout()) {
            refundOld(level, pos);
            return;
        }
        super.tick(state, level, pos, random);
    }

    /**
     * A one-block Tier 2 sluice from before the long sluice becomes a chest holding the sluice item, its inserts, its tray and
     * its queued clumps (what does not fit drops on top). The owner is told once. Public for tests.
     */
    public static void refundOld(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LongSluiceBlock block) || !(level.getBlockEntity(pos) instanceof SluiceBoxBlockEntity sluice)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        ItemStack self = new ItemStack(block.asItem());
        String skin = sluice.getSkin();
        if (skin != null && !skin.isEmpty() && !skin.equals("default")) {
            self.set(com.holeydigsite.registry.ModDataComponents.SKIN.get(), skin);
            com.holeydigsite.skin.SkinWear.set(self, sluice.getWear());
        }
        items.add(self);
        for (SluiceUpgrade u : block.inserts(state)) {
            items.add(new ItemStack(u.item()));
        }
        items.addAll(sluice.takeTray());
        items.addAll(sluice.queuedAsItems());
        sluice.clearQueue();
        sluice.markRefunded();
        UUID owner = sluice.owner();
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, state.getValue(FACING)), Block.UPDATE_ALL);
        ChestBlockEntity chest = level.getBlockEntity(pos) instanceof ChestBlockEntity c ? c : null;
        int slot = 0;
        for (ItemStack stack : items) {
            ItemStack rest = stack.copy();
            while (!rest.isEmpty()) {
                ItemStack part = rest.split(rest.getMaxStackSize());
                if (chest != null && slot < chest.getContainerSize()) {
                    chest.setItem(slot++, part);
                } else {
                    Block.popResource(level, pos.above(), part);
                }
            }
        }
        if (chest != null) {
            chest.setChanged();
        }
        if (owner != null) {
            SluiceRefunds.tell(level.getServer(), owner, pos);
        }
    }
}
