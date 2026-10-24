package com.holeydigsite.shop;

import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.economy.Selling;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A shop display that renders the sold item on a plinth with a price label, and gives a free blueprint on right-click.
 */
public class ShopDisplayBlock extends BaseEntityBlock {
    public static final MapCodec<ShopDisplayBlock> CODEC = simpleCodec(ShopDisplayBlock::new);

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public ShopDisplayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopDisplayBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            interact(level, pos, player, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            interact(level, pos, player, ItemStack.EMPTY);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Click logic for setting items or getting blueprints. */
    /** Blueprints given by a sneak right-click. */
    public static final int SNEAK_BLUEPRINTS = 16;

    public static void interact(Level level, BlockPos pos, Player player, ItemStack held) {
        if (!(level.getBlockEntity(pos) instanceof ShopDisplayBlockEntity display)) {
            return;
        }
        if (player.isShiftKeyDown() && player instanceof net.minecraft.server.level.ServerPlayer sp && com.holeydigsite.shop.custom.CustomShops.isOp(sp)
                && display.item() == null && !(player.getAbilities().instabuild && !held.isEmpty())) {
            com.holeydigsite.shop.custom.ShopSetupNetwork.open(sp, pos, held);
            return;
        }
        if (player.isShiftKeyDown() && player.getAbilities().instabuild && !held.isEmpty()) {
            Item target = held.getItem() == com.holeydigsite.registry.ModItems.BLUEPRINT.get() && BlueprintItem.target(held) != null
                    ? BlueprintItem.target(held) : held.getItem();
            display.setItem(target);
            player.displayClientMessage(Component.translatable("message.holeydigsite.display.set", target.getDescription())
                    .withStyle(ChatFormatting.GREEN), true);
            return;
        }
        Item shown = display.item();
        if (shown == null && display.custom() != null && level instanceof net.minecraft.server.level.ServerLevel sl) {
            ItemStack bp = com.holeydigsite.shop.custom.CustomBlueprint.stackFor(com.holeydigsite.shop.custom.CustomShops.idOf(sl, display),
                    display.custom(), player.isShiftKeyDown() ? SNEAK_BLUEPRINTS : 1);
            if (!player.getInventory().add(bp)) {
                player.drop(bp, false);
            }
            player.getInventory().setChanged();
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.7F, 1.1F);
            return;
        }
        if (shown == null) {
            player.displayClientMessage(Component.translatable(player.getAbilities().instabuild
                    ? "message.holeydigsite.display.empty_creative" : "message.holeydigsite.display.empty")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (ShopCatalogue.find(level.registryAccess(), shown).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.display.not_sold")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (ShopCatalogue.isLedgerItem(level.registryAccess(), shown)) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.display.ledger_only")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack bp = BlueprintItem.of(shown, player.isShiftKeyDown() ? SNEAK_BLUEPRINTS : 1); // sneak = a bulk stack
        if (!player.getInventory().add(bp)) {
            player.drop(bp, false);
        }
        player.getInventory().setChanged();
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.7F, 1.1F);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.shop_display").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.shop_display.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
