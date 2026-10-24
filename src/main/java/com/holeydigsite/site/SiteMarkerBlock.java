package com.holeydigsite.site;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Site Marker (Phase 14): an invisible block in the island template that says "build site X here". Its block entity holds
 * the site id ({@code {id:"holeydigsite:site_marker",Site:"holeydigsite:home"}}). On the first level tick after it is placed or
 * loaded ({@link Sites#queueMarker}), the mod builds the site with the structure's origin at this block ({@link Sites#buildFromMarker}); the structure replaces the
 * marker. Unbreakable, no collision; creative players can place one and set the site with the NBT.
 */
public class SiteMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<SiteMarkerBlock> CODEC = simpleCodec(SiteMarkerBlock::new);

    public SiteMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SiteMarkerBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel sl) {
            Sites.queueMarker(sl, pos);
            sl.scheduleTick(pos, this, 1);     // fallback; whichever comes first builds it, once
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SiteMarkerBlockEntity marker && marker.site() != null) {
            Sites.buildFromMarker(level, pos, marker.site());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.site_marker").withStyle(ChatFormatting.GRAY));
    }
}
