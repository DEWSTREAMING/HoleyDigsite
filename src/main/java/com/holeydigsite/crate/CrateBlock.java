package com.holeydigsite.crate;

import com.holeydigsite.plot.PlotLock;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A Mining Crate. Place it (front faces the player) and right-click: the server rolls and holds the reward, the lid
 * pops open, the opener's client plays the spin, the reward is given when the spin ends and the crate is gone after {@link #OPEN_TICKS}. Breaking an unopened crate
 * drops it (loot table checks {@code open=false}).
 */
public class CrateBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CrateBlock> CODEC = simpleCodec(p -> new CrateBlock(p, "wooden_crate"));
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** How long the open crate stays before it is used up (the spin plus the time the result stays on screen). */
    public static final int OPEN_TICKS = CrateSpin.OPEN_TICKS;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 13, 15);

    private final String tier;

    public CrateBlock(Properties properties, String tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(OPEN, false));
    }

    public String tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(OPEN)) {
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            open(sl, pos, state, sp);
        }
        return InteractionResult.CONSUME;
    }

    /** Opens a closed crate for {@code sp}. Server only; false if nothing happened. */
    public static boolean open(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer sp) {
        if (!(state.getBlock() instanceof CrateBlock crate) || state.getValue(OPEN)) {
            return false;
        }
        if (!PlotLock.canUse(sp, level, pos)) {
            return false;
        }
        CrateDefs.CrateTier def = CrateDefs.tier(crate.tier);
        CrateRolls.Roll roll = def == null ? null : CrateRolls.roll(def, level.random);
        if (roll == null) {
            sp.displayClientMessage(Component.translatable("message.holeydigsite.crate.empty"), true);
            return false;
        }
        level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
        PendingCrates.get(level.getServer()).add(sp.getUUID(), level, pos, roll);
        level.scheduleTick(pos, state.getBlock(), CrateSpin.GRANT_TICKS);
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8F, 1.2F);
        ItemStack icon = CrateRolls.previewIcon(roll);
        CrateRolls.Spin spin = CrateRolls.spin(def, roll, icon, level.random);
        CrateNetwork.send(sp, new CrateNetwork.OpenPayload(pos, icon, spin.icons(), spin.rarities(),
                spin.resultIndex(), spin.stopFrac()));
        return true;
    }

    /**
     * First tick (spin over): give the reward and let the result stay a while. Second tick: the crate is used up. A reward
     * that cannot be given yet (player offline) stays held and arrives on login.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN)) return;
        PendingCrates data = PendingCrates.get(level.getServer());
        PendingCrates.Held held = data.at(level, pos);
        if (held != null && !held.spinOver) {
            held.spinOver = true;
            data.setDirty();
            PendingCrates.grant(level.getServer(), held);
            level.scheduleTick(pos, this, OPEN_TICKS - CrateSpin.GRANT_TICKS);
            return;
        }
        if (held != null) {
            PendingCrates.grant(level.getServer(), held);
            if (held.granted) data.remove(held);
        }
        level.removeBlock(pos, false);
        level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 8, 0.25, 0.2, 0.25, 0.02);
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.holeydigsite.crate").withStyle(ChatFormatting.GRAY));
        CrateDefs.CrateTier def = CrateDefs.tier(tier);
        if (def != null) {
            tooltip.add(oddsLine(def).withStyle(ChatFormatting.GRAY));
            if (hasSkins(def)) {
                tooltip.add(Component.translatable("tooltip.holeydigsite.crate.skins").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    public static boolean hasSkins(CrateDefs.CrateTier def) {
        for (String r : CrateDefs.RARITIES) {
            for (CrateDefs.Entry e : def.pool(r)) {
                if (e.kind() == CrateDefs.Kind.SKIN) return true;
            }
        }
        return false;
    }

    /** "Common 70%, Rare 25%, Epic 5%" (rarities at 0 are left out). */
    public static net.minecraft.network.chat.MutableComponent oddsLine(CrateDefs.CrateTier def) {
        net.minecraft.network.chat.MutableComponent line = Component.empty();
        boolean first = true;
        for (String r : CrateDefs.RARITIES) {
            double pct = def.percent(r);
            if (pct <= 0) continue;
            if (!first) line.append(", ");
            first = false;
            String num = pct == Math.rint(pct) ? String.valueOf((int) pct) : String.format(java.util.Locale.ROOT, "%.1f", pct);
            line.append(Component.translatable("rarity.holeydigsite." + r).withStyle(CrateDefs.rarityFormatting(r))).append(" " + num + "%");
        }
        return line;
    }
}
