package com.holeydigsite.forge;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An adjustable open ingot cast with a player-set target weight (see {@link #TARGETS}), backed by {@link MoldBlockEntity}.
 *
 * <ul>
 *   <li>Empty mold, empty hand: right-click cycles the target (10, 25, 50, 75, 100, 150, 200 g), and sneak-right-click goes backwards.</li>
 *   <li>A Crucible pours up to the target weight, restarting cooling on each pour.</li>
 *   <li>Cooled: empty-hand right-click retrieves the ingot.</li>
 *   <li>Breaking a mold with metal inside drops the ingot.</li>
 * </ul>
 */
public class MoldBlock extends BaseEntityBlock {
    public static final MapCodec<MoldBlock> CODEC = simpleCodec(MoldBlock::new);
    public static final EnumProperty<MoltenMetal> METAL = EnumProperty.create("metal", MoltenMetal.class);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    public static final BooleanProperty SOLID = BooleanProperty.create("solid");

    public static final int[] TARGETS = com.holeydigsite.tier.Tiers.def("mold").statArray("targets", null);
    public static final int DEFAULT_TARGET = 50;
    public static final int COOL_TICKS = com.holeydigsite.tier.Tiers.stat("mold", "cool_ticks");

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 4, 15);

    private final int coolTicks;
    private final int[] targets;

    public MoldBlock(Properties properties) {
        this(properties, COOL_TICKS, TARGETS);
    }

    public MoldBlock(Properties properties, int coolTicks, int[] targets) {
        super(properties);
        this.coolTicks = coolTicks;
        this.targets = targets;
        registerDefaultState(stateDefinition.any().setValue(METAL, MoltenMetal.NONE).setValue(LEVEL, 0).setValue(SOLID, false));
    }

    public int coolTicks() {
        return coolTicks;
    }

    public int[] targets() {
        return targets;
    }

    public int validTargetFor(int grams) {
        for (int t : targets()) {
            if (t == grams) {
                return t;
            }
        }
        return DEFAULT_TARGET;
    }

    public int nextTargetFor(int current, boolean backwards) {
        int[] ts = targets();
        int i = 0;
        for (int k = 0; k < ts.length; k++) {
            if (ts[k] == current) {
                i = k;
            }
        }
        return ts[Math.floorMod(i + (backwards ? -1 : 1), ts.length)];
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(METAL, LEVEL, SOLID);
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof net.minecraft.server.level.ServerPlayer sp && mold(level, pos) instanceof MoldBlockEntity m) {
            m.setOwnerPlayer(sp);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoldBlockEntity(pos, state);
    }

    @Nullable
    public static MoldBlockEntity mold(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MoldBlockEntity m ? m : null;
    }

    @Nullable
    public static BlockPos findInFront(BlockGetter level, BlockPos crucible, Direction facing) {
        BlockPos front = crucible.relative(facing);
        if (level.getBlockState(front).getBlock() instanceof MoldBlock) {
            return front;
        }
        if (level.getBlockState(front.below()).getBlock() instanceof MoldBlock) {
            return front.below();
        }
        return null;
    }

    public static int validTarget(int grams) {
        for (int t : TARGETS) {
            if (t == grams) {
                return t;
            }
        }
        return DEFAULT_TARGET;
    }

    public static int nextTarget(int current, boolean backwards) {
        int i = 0;
        for (int k = 0; k < TARGETS.length; k++) {
            if (TARGETS[k] == current) {
                i = k;
            }
        }
        i = Math.floorMod(i + (backwards ? -1 : 1), TARGETS.length);
        return TARGETS[i];
    }

    public static BlockState withContents(BlockState state, MoldBlockEntity m) {
        int level = m.isEmpty() ? 0 : m.grams() >= m.target() ? 3 : m.grams() * 2 < m.target() ? 1 : 2;
        return state.setValue(METAL, m.isEmpty() ? MoltenMetal.NONE : MoltenMetal.of(m.metal())).setValue(LEVEL, level);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MoldBlockEntity m = mold(level, pos);
        if (m == null || m.isEmpty()) {
            return;
        }
        if (state.getValue(SOLID)) {
            solidTick(level, pos, state, m);
            return;
        }
        long left = m.coolUntil() - level.getGameTime();
        if (left > 0) {
            level.scheduleTick(pos, this, (int) left);
            return;
        }
        level.setBlock(pos, state.setValue(SOLID, true), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.8F);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.01);
        solidTick(level, pos, state.setValue(SOLID, true), m);
    }

    protected void solidTick(ServerLevel level, BlockPos pos, BlockState state, MoldBlockEntity mold) {
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            click(level, pos, player, player.isShiftKeyDown());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void click(Level level, BlockPos pos, Player player, boolean sneaking) {
        MoldBlockEntity m = mold(level, pos);
        if (m == null) {
            return;
        }
        if (m.owner() == null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            m.setOwnerPlayer(sp);
        }
        if (m.isEmpty()) {
            m.setTarget(m.block().nextTargetFor(m.target(), sneaking));
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, 1.5F);
            player.displayClientMessage(Component.translatable("message.holeydigsite.mold.target", m.target())
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!level.getBlockState(pos).getValue(SOLID)) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.mold.cooling",
                    (m.coolingTicksLeft() + 19) / 20).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        net.minecraft.resources.ResourceLocation castMetal = m.metal();
        ItemStack ingot = m.takeIngot();
        if (!ingot.isEmpty()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp && castMetal != null) {
                com.holeydigsite.ledger.ModTriggers.CAST.get().trigger(sp, castMetal);
            }
            player.displayClientMessage(Component.translatable("message.holeydigsite.mold.took", ingot.getHoverName())
                    .withStyle(ChatFormatting.GOLD), true);
            if (!player.getInventory().add(ingot)) {
                player.drop(ingot, false);
            }
            player.getInventory().setChanged();
        }
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            MoldBlockEntity m = mold(level, pos);
            if (m != null && !m.isEmpty() && m.metal() != null) {
                ItemStack ingot = Metals.ingot(m.metal(), m.grams());
                if (!ingot.isEmpty()) {
                    Block.popResource(level, pos, ingot);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LEVEL) > 0 && !state.getValue(SOLID) && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.3,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.mold").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.mold.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
