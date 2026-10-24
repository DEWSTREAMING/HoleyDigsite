package com.holeydigsite.forge;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A larger version of the {@link CrucibleBlock} that holds more capacity and melts faster,
 * while keeping the same basic mechanics of inserting metal and pouring into molds.
 *
 * <p><b>Auto-pour</b> (see {@link #AUTO}): sneak and empty-hand right-click on the spout side switches it on or off.
 * While active, it pours automatically whenever the mold in front is empty or can be topped up and enough metal is molten.</p>
 */
public class LargeCrucibleBlock extends CrucibleBlock {
    public static final MapCodec<LargeCrucibleBlock> CODEC = simpleCodec(LargeCrucibleBlock::new);
    public static final BooleanProperty AUTO = BooleanProperty.create("auto");

    public static final int CAPACITY = com.holeydigsite.tier.Tiers.stat("large_crucible", "capacity");
    public static final int TICKS_PER_GRAM = com.holeydigsite.tier.Tiers.stat("large_crucible", "ticks_per_gram");

    private static final VoxelShape OUTLINE = Block.box(1, 0, 1, 15, 11, 15);
    private static final VoxelShape COLLISION = Shapes.join(OUTLINE, Block.box(2, 1, 2, 14, 11, 14), BooleanOp.ONLY_FIRST);

    public LargeCrucibleBlock(Properties properties) {
        this(properties, CAPACITY, TICKS_PER_GRAM);
    }

    public LargeCrucibleBlock(Properties properties, int capacity, int ticksPerGram) {
        super(properties, capacity, ticksPerGram);
        registerDefaultState(defaultBlockState().setValue(AUTO, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AUTO);
    }

    @Override
    public boolean autoPour(BlockState state) {
        return state.getValue(AUTO);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && hit.getDirection() == state.getValue(FACING)) {
            if (!level.isClientSide) {
                toggleAuto(level, pos, state, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    public static BlockState toggleAuto(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState next = state.setValue(AUTO, !state.getValue(AUTO));
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, next.getValue(AUTO) ? 1.4F : 1.0F);
        if (next.getBlock() instanceof CrucibleBlock c) {
            c.armAuto(level, pos);
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable(next.getValue(AUTO)
                    ? "message.holeydigsite.crucible.auto_on" : "message.holeydigsite.crucible.auto_off").withStyle(ChatFormatting.GREEN), true);
        }
        return next;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.holeydigsite.large_crucible").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.large_crucible.auto").withStyle(ChatFormatting.DARK_GRAY));
    }
}
