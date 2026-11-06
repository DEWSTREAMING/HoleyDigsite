package com.holeydigsite.machine;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.site.SiteProtection;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Sawmill: water-powered conveyor machine cutting logs into beams or pit props.
 * Mode is toggled by sneak-right-clicking with an empty hand.
 * Empty hand takes buffered output.
 */
public class SawmillBlock extends WaterProcessingBlock {
    public static final MapCodec<SawmillBlock> CODEC = simpleCodec(SawmillBlock::new);
    public static final ResourceLocation ID = HoleyDigsite.id("sawmill");

    private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.block();

    public enum Mode implements StringRepresentable {
        BEAM("beam"),
        PIT_PROP("pit_prop");

        private final String name;
        Mode(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    public SawmillBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MODE, Mode.BEAM));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public ResourceLocation machineId() {
        return ID;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(MODE, Mode.BEAM);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!PlotLock.canUse(player, level, pos)) {
            return InteractionResult.FAIL;
        }
        if (SiteProtection.restricted(player) && SiteProtection.isProtected(level, pos)) {
            SiteProtection.tell(player);
            return InteractionResult.FAIL;
        }

        // Sneak and empty hand switches mode, always.
        if (player.isShiftKeyDown()) {
            Mode current = state.getValue(MODE);
            Mode next = current == Mode.BEAM ? Mode.PIT_PROP : Mode.BEAM;
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(MODE, next), 3);
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.2F);
                String msgKey = next == Mode.BEAM ? "message.holeydigsite.sawmill.mode_beam" : "message.holeydigsite.sawmill.mode_pit_prop";
                player.displayClientMessage(Component.translatable(msgKey), true);
                if (level.getBlockEntity(pos) instanceof SawmillBlockEntity be) {
                    be.checkStartRecipe();
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Empty hand takes the buffer when there is something in it (same as the other two machines).
        if (level.getBlockEntity(pos) instanceof WaterProcessingBlockEntity be && !be.outputBuffer().isEmpty()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }

        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SawmillBlockEntity(pos, state);
    }
}
