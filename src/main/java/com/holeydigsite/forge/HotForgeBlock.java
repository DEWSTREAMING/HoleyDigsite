package com.holeydigsite.forge;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Phase 13 Hot Forge (Tier 2, made in the Crafting Forge): a {@link ForgeBlock} with a hotter fire. Lava-powered exactly like
 * the Forge (same back port, same {@code LIT}); any Crucible on top melts {@link #HEAT} times as fast on top of its own speed
 * (a Large Crucible on a Hot Forge melts four times as fast as a normal Crucible on a normal Forge). Brighter glow (light 15).
 */
public class HotForgeBlock extends ForgeBlock {
    public static final MapCodec<HotForgeBlock> CODEC = simpleCodec(HotForgeBlock::new);
    /** Default for the codec constructor only: the real value is the tier file's ({@code tiers/forge.json}). */
    public static final int HEAT = com.holeydigsite.tier.Tiers.stat("hot_forge", "heat");

    public HotForgeBlock(Properties properties) {
        this(properties, HEAT);
    }

    public HotForgeBlock(Properties properties, int heat) {
        super(properties, heat);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** More embers than the plain Forge. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (state.getValue(LIT)) {
            Direction facing = state.getValue(FACING);
            level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.5 * Math.abs(facing.getStepZ()),
                    pos.getY() + 0.3 + random.nextDouble() * 0.3,
                    pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.5 * Math.abs(facing.getStepX()), 0, 0.02, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.holeydigsite.hot_forge").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.forge.ports").withStyle(ChatFormatting.DARK_GRAY));
    }
}
