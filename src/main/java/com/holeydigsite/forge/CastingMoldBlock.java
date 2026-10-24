package com.holeydigsite.forge;

import com.holeydigsite.transport.ItemOutputs;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.List;

/**
 * Phase 13 Casting Mold (Tier 2, made in the Crafting Forge): an adjustable {@link MoldBlock} that
 * <ul>
 *   <li>cools three times faster ({@link #COOL_TICKS} ticks) and takes targets up to the Large Crucible's 500 g;</li>
 *   <li><b>ejects</b> the finished ingot by itself into the {@link com.holeydigsite.transport.ItemTarget} in front of it
 *       (a belt, Sell Pad, Crafting Forge...), in the direction of {@link #FACING} (the way the player looked when placing
 *       it). If the target refuses, the ingot stays and it tries again about every half second. Clicking still takes it.</li>
 * </ul>
 */
public class CastingMoldBlock extends MoldBlock {
    public static final MapCodec<CastingMoldBlock> CODEC = simpleCodec(CastingMoldBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final int COOL_TICKS = com.holeydigsite.tier.Tiers.stat("casting_mold_t2", "cool_ticks");
    public static final int RETRY_TICKS = 10;
    public static final int[] TARGETS = com.holeydigsite.tier.Tiers.def("casting_mold_t2").statArray("targets", null);

    public CastingMoldBlock(Properties properties) {
        this(properties, COOL_TICKS, TARGETS);
    }

    public CastingMoldBlock(Properties properties, int coolTicks, int[] targets) {
        super(properties, coolTicks, targets);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    /** The ingot comes out in the direction the player was looking. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void solidTick(ServerLevel level, BlockPos pos, BlockState state, MoldBlockEntity mold) {
        if (!eject(level, pos, mold)) {
            level.scheduleTick(pos, this, RETRY_TICKS);   // blocked or nothing there yet: look again soon
        }
    }

    /**
     * Pushes the finished ingot into the target in front. Public for tests. Returns true if the mold is empty afterwards
     * (it ejected, or there was nothing to eject).
     */
    public static boolean eject(ServerLevel level, BlockPos pos, MoldBlockEntity mold) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CastingMoldBlock) || mold.isEmpty() || mold.metal() == null || !state.getValue(SOLID)) {
            return mold.isEmpty();
        }
        Direction facing = state.getValue(FACING);
        ItemStack ingot = Metals.ingot(mold.metal(), mold.grams());
        if (ingot.isEmpty()) {
            return false;
        }
        if (!ItemOutputs.tryOutput(level, pos.relative(facing), facing.getOpposite(), ingot)) {
            return false;
        }
        net.minecraft.resources.ResourceLocation castMetal = mold.metal();
        mold.takeIngot();
        if (castMetal != null) {
            net.minecraft.server.level.ServerPlayer sp = mold.resolveOwnerPlayer(level);
            if (sp != null) {
                com.holeydigsite.ledger.ModTriggers.CAST.get().trigger(sp, castMetal);
            }
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.4F);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.holeydigsite.casting_mold").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.casting_mold.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
