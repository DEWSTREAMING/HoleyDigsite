package com.holeydigsite.shop;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.CoinItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Money Pan: a shallow metal tray holding the shop's balance ({@link MoneyPanBlockEntity}). Purchases are paid from
 * here, never from the player's inventory; Sell Pad sales are paid in here.
 * <ul>
 *   <li>Right-click with coins: deposits that stack. Sneak-right-click: deposits every coin in the inventory.</li>
 *   <li>Empty-hand right-click: withdraws the whole balance as coins, largest first (overflow at the feet).</li>
 *   <li>Coins thrown onto it are deposited.</li>
 *   <li>{@link #PILE} 0-3 picks the baked coin-pile model (empty / a few coppers / silver pile / big gold pile).</li>
 *   <li>Breaking a crafted pan drops it plus its balance as coins; {@code locked=true} (island) can't be broken.</li>
 * </ul>
 */
public class MoneyPanBlock extends BaseEntityBlock {
    public static final MapCodec<MoneyPanBlock> CODEC = simpleCodec(MoneyPanBlock::new);
    public static final IntegerProperty PILE = IntegerProperty.create("pile", 0, 3);

    /** Pile steps: 1 below 1 gold, 2 below 10 gold, 3 from 10 gold. */
    public static int pileFor(long copper) {
        if (copper <= 0) return 0;
        if (copper < Selling.GOLD) return 1;
        if (copper < 10L * Selling.GOLD) return 2;
        return 3;
    }

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 3, 15);

    public MoneyPanBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ShopLock.LOCKED, false).setValue(PILE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ShopLock.LOCKED, PILE);
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
        return new MoneyPanBlockEntity(pos, state);
    }

    @Nullable
    public static MoneyPanBlockEntity pan(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MoneyPanBlockEntity p ? p : null;
    }

    /** Copper value of a coin stack, 0 for anything else. */
    public static long coinValue(ItemStack stack) {
        return stack.getItem() instanceof CoinItem coin ? (long) coin.getValue() * stack.getCount() : 0;
    }

    // ------------------------------------------------------------------------------------------------
    // Player
    // ------------------------------------------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            if (player.isShiftKeyDown()) {
                depositInventory(level, pos, player);
            } else if (coinValue(stack) > 0) {
                depositHeld(level, pos, player, stack);
            } else {
                player.displayClientMessage(Component.translatable("message.holeydigsite.money_pan.coins_only")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                depositInventory(level, pos, player);
            } else {
                withdraw(level, pos, player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Deposits the held coin stack. Public for tests. Returns the copper deposited. */
    public static long depositHeld(Level level, BlockPos pos, Player player, ItemStack stack) {
        MoneyPanBlockEntity pan = pan(level, pos);
        long v = coinValue(stack);
        if (pan == null || v <= 0) {
            return 0;
        }
        stack.setCount(0);
        pan.deposit(v);
        depositFeedback(level, pos, player, v, pan.balance());
        return v;
    }

    /** Deposits every coin in the inventory (main, hotbar, off hand). Public for tests. Returns the copper deposited. */
    public static long depositInventory(Level level, BlockPos pos, Player player) {
        MoneyPanBlockEntity pan = pan(level, pos);
        if (pan == null) {
            return 0;
        }
        Inventory inv = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>(inv.items);
        stacks.addAll(inv.offhand);
        long total = 0;
        for (ItemStack s : stacks) {
            long v = coinValue(s);
            if (v > 0) {
                total += v;
                s.setCount(0);
            }
        }
        inv.setChanged();
        if (total <= 0) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.money_pan.no_coins")
                    .withStyle(ChatFormatting.RED), true);
            return 0;
        }
        pan.deposit(total);
        depositFeedback(level, pos, player, total, pan.balance());
        return total;
    }

    /** Pays the whole balance out as coins, largest first, into the inventory (overflow at the feet). Public for tests. */
    public static long withdraw(Level level, BlockPos pos, Player player) {
        MoneyPanBlockEntity pan = pan(level, pos);
        if (pan == null) {
            return 0;
        }
        long all = pan.withdrawAll();
        if (all <= 0) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.money_pan.empty")
                    .withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }
        for (ItemStack coin : Selling.coins(all)) {
            if (!player.getInventory().add(coin)) {
                player.drop(coin, false);
            }
        }
        player.getInventory().setChanged();
        coinSound(level, pos, 0.9F);
        player.displayClientMessage(Component.translatable("message.holeydigsite.money_pan.withdrew", Selling.format(all, 46))
                .withStyle(ChatFormatting.GOLD), true);
        return all;
    }

    private static void depositFeedback(Level level, BlockPos pos, @Nullable Player player, long copper, long balance) {
        coinSound(level, pos, 1.3F);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.money_pan.deposited",
                    Selling.format(copper, 43), Selling.format(balance)).withStyle(ChatFormatting.GOLD), true);
        }
    }

    static void coinSound(Level level, BlockPos pos, float pitch) {
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, pitch);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.3F, pitch);
    }

    // ------------------------------------------------------------------------------------------------
    // Thrown coins
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel) || !(entity instanceof ItemEntity item) || !item.isAlive()) {
            return;
        }
        long v = coinValue(item.getItem());
        MoneyPanBlockEntity pan = pan(level, pos);
        if (v <= 0 || pan == null) {
            return;
        }
        item.discard();
        pan.deposit(v);
        depositFeedback(level, pos, item.getOwner() instanceof Player p ? p : null, v, pan.balance());
    }

    // ------------------------------------------------------------------------------------------------
    // Breaking / locked
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            MoneyPanBlockEntity pan = pan(level, pos);
            if (pan != null && pan.balance() > 0) {
                long all = pan.balance();
                for (ItemStack coin : Selling.coins(all)) {
                    Block.popResource(level, pos, coin);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return ShopLock.isLocked(state) ? 0.0F : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return ShopLock.isLocked(state) ? ShopLock.LOCKED_RESISTANCE : super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return !ShopLock.isLocked(state) && super.canEntityDestroy(state, level, pos, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.money_pan").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.money_pan.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
