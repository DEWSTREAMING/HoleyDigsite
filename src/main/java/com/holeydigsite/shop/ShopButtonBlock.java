package com.holeydigsite.shop;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.sellpad.SellPadBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop Button: a small post with a big button on top. Pressing it (right-click, with anything in hand) acts on every
 * Sell Pad, Buy Pad and Money Pan directly next to it on the four horizontal sides:
 * <ol>
 *   <li><b>Sell:</b> everything on the adjacent Sell Pads is sold, and the coins go into the adjacent Money Pan's balance.
 *       With no Money Pan next to the button nothing is sold, showing a red message.</li>
 *   <li><b>Buy:</b> the blueprints on the adjacent Buy Pads are bought with the Money Pan's balance and the items go into the presser's inventory.
 *       Blueprints stay on the pad. Not enough balance shows a red message with the missing amount.</li>
 * </ol>
 * Selling runs first so sale money can pay for the purchase in the same press. With several Money Pans adjacent, the
 * first in N-E-S-W order is used. The press animation uses the {@link #PRESSED} state, reset by one scheduled tick.
 */
public class ShopButtonBlock extends Block {
    public static final MapCodec<ShopButtonBlock> CODEC = simpleCodec(ShopButtonBlock::new);
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");

    /** Ticks the button stays down. */
    public static final int PRESS_TICKS = 6;

    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 9, 12);
    private static final VoxelShape SHAPE_PRESSED = Block.box(4, 0, 4, 12, 8, 12);

    public ShopButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ShopLock.LOCKED, false).setValue(PRESSED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ShopLock.LOCKED, PRESSED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PRESSED) ? SHAPE_PRESSED : SHAPE;
    }

    // ------------------------------------------------------------------------------------------------
    // Press
    // ------------------------------------------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            press(level, pos, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            press(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PRESSED)) {
            level.setBlock(pos, state.setValue(PRESSED, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.9F);
        }
    }

    /** Result of a button press for tests. */
    public record Result(long sold, int itemsSold, long spent, int boughtStacks) {}

    /** Presses the button, running the animation, selling, and buying. Public for tests. */
    public static Result press(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ShopButtonBlock && !state.getValue(PRESSED)) {
            level.setBlock(pos, state.setValue(PRESSED, true), Block.UPDATE_ALL);
            level.scheduleTick(pos, state.getBlock(), PRESS_TICKS);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 0.9F);
        }

        List<BlockPos> sellPads = new ArrayList<>(), buyPads = new ArrayList<>();
        MoneyPanBlockEntity pan = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = pos.relative(d);
            Block b = level.getBlockState(p).getBlock();
            if (b instanceof SellPadBlock) {
                sellPads.add(p);
            } else if (b instanceof BuyPadBlock) {
                buyPads.add(p);
            } else if (b instanceof MoneyPanBlock && pan == null) {
                pan = MoneyPanBlock.pan(level, p);
            }
        }

        List<PadBlockEntity> sells = new ArrayList<>(), buys = new ArrayList<>();
        for (BlockPos p : sellPads) {
            PadBlockEntity pad = AbstractPadBlock.pad(level, p);
            if (pad != null && !pad.isEmpty()) sells.add(pad);
        }
        for (BlockPos p : buyPads) {
            PadBlockEntity pad = AbstractPadBlock.pad(level, p);
            if (pad != null && !pad.isEmpty()) buys.add(pad);
        }
        if (sells.isEmpty() && buys.isEmpty()) {
            error(player, "message.holeydigsite.button.nothing", ChatFormatting.YELLOW);
            return new Result(0, 0, 0, 0);
        }
        if (pan == null) {
            error(player, "message.holeydigsite.button.no_pan", ChatFormatting.RED);
            return new Result(0, 0, 0, 0);
        }

        // Sell items.
        long sold = 0;
        long maxSingle = 0;
        int itemsSold = 0;
        for (PadBlockEntity pad : sells) {
            for (ItemStack s : pad.contents()) {
                long v = com.holeydigsite.shop.custom.CustomShops.stackValue(level, pad.getBlockPos(), s);
                if (v > 0) {
                    sold += v;
                    maxSingle = Math.max(maxSingle, com.holeydigsite.shop.custom.CustomShops.unitValue(level, pad.getBlockPos(), s));
                    itemsSold += s.getCount();
                }
            }
        }
        if (sold > 0) {
            for (PadBlockEntity pad : sells) {
                pad.takeAll();
            }
            pan.deposit(sold);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.holeydigsite.ledger.LedgerManager.recordSale(sp, sold, maxSingle);
            }
            cashSound(level, pos);
            player.displayClientMessage(Component.translatable("message.holeydigsite.button.sold", itemsSold,
                    Selling.format(sold, 34)).withStyle(ChatFormatting.GOLD), true);
        }

        // Buy all adjacent buy pads at once.
        long cost = 0;
        List<ItemStack> delivery = new ArrayList<>();
        int stacks = 0;
        for (PadBlockEntity pad : buys) {
            for (ItemStack bp : pad.contents()) {
                var custom = com.holeydigsite.shop.custom.CustomBlueprint.of(bp);
                if (custom != null) {
                    if (!(level instanceof net.minecraft.server.level.ServerLevel sl) || !com.holeydigsite.shop.custom.CustomShops.valid(sl, custom)) {
                        error(player, "message.holeydigsite.button.stale", ChatFormatting.RED);
                        level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 1.0F);
                        return new Result(sold, itemsSold, 0, 0);
                    }
                    cost += custom.price() * bp.getCount();
                    delivery.add(custom.template().copyWithCount(custom.count() * bp.getCount()));
                    stacks += bp.getCount();
                    continue;
                }
                var entry = ShopCatalogue.forBlueprint(level.registryAccess(), bp);
                if (entry.isPresent()) {
                    cost += entry.get().price() * bp.getCount();
                    delivery.add(entry.get().item().getDefaultInstance().copyWithCount(entry.get().count() * bp.getCount()));
                    stacks += bp.getCount();
                }
            }
        }
        long spent = 0;
        if (cost > 0) {
            if (!pan.withdraw(cost)) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.button.not_enough",
                        Selling.format(cost - pan.balance(), 26)).withStyle(ChatFormatting.RED), true);
                level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 1.0F);
                return new Result(sold, itemsSold, 0, 0);
            }
            spent = cost;
            for (PadBlockEntity pad : buys) {
                pad.takeAll();
            }
            for (ItemStack d : delivery) {
                ItemStack rest = d.copy();
                while (!rest.isEmpty()) {
                    ItemStack part = rest.split(Math.min(rest.getCount(), rest.getMaxStackSize()));
                    if (!player.getInventory().add(part)) {
                        player.drop(part, false);
                    }
                }
            }
            player.getInventory().setChanged();
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5F, 1.4F);
            cashSound(level, pos);
            player.displayClientMessage(Component.translatable("message.holeydigsite.button.bought", stacks,
                    Selling.format(cost, 32)).withStyle(ChatFormatting.GREEN), true);
        }
        return new Result(sold, itemsSold, spent, stacks);
    }

    private static void error(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    private static void cashSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7F, 1.6F);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.3F);
    }

    // ------------------------------------------------------------------------------------------------
    // Locked
    // ------------------------------------------------------------------------------------------------

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return ShopLock.isLocked(state) ? PushReaction.BLOCK : super.getPistonPushReaction(state);
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
        tooltip.add(Component.translatable("tooltip.holeydigsite.shop_button").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.shop_button.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
