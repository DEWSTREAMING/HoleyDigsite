package com.holeydigsite.shop;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A shop pad (Sell Pad or Buy Pad) is a low counter that holds up to 9 stacks on top ({@link PadBlockEntity}), shown in a
 * 3x3 grid with a floating total. Nothing happens on the pad itself as an adjacent Shop Button acts on
 * it.
 * <ul>
 *   <li>Right-click with an accepted stack puts it on the pad, while refused items get a red message.</li>
 *   <li>Sneak-right-click moves every accepted stack from the inventory onto it, as many as fit.</li>
 *   <li>Items thrown onto it are added if accepted and there is room.</li>
 *   <li>Empty-hand right-click takes everything back into the inventory.</li>
 *   <li>Breaking drops the pad plus its contents, though locked pads cannot be broken in survival.</li>
 * </ul>
 */
public abstract class AbstractPadBlock extends BaseEntityBlock {
    /** A low counter 4 pixels high so dropped items rest inside the block space. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    protected AbstractPadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ShopLock.LOCKED, false));
    }

    /** Whether this pad takes the stack, such as a Sell Pad having a price or a Buy Pad having a catalogued blueprint. */
    public abstract boolean accepts(ItemStack stack, Level level);

    /** Position-aware version where a buy pad only takes plot-exclusive blueprints inside that plot. */
    public boolean accepts(ItemStack stack, Level level, BlockPos pos) {
        return accepts(stack, level);
    }

    /** Translation key of the red refused message. */
    protected abstract String refusedKey();

    /** Translation key of the yellow help message shown on an empty-hand click of an empty pad. */
    protected abstract String helpKey();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ShopLock.LOCKED);
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
        return new PadBlockEntity(pos, state);
    }

    @Nullable
    public static PadBlockEntity pad(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PadBlockEntity p ? p : null;
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
                placeInventory(level, pos, player);
            } else {
                placeHeld(level, pos, player, stack);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                placeInventory(level, pos, player);
            } else {
                takeBack(level, pos, player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Puts the held stack on the pad as much as fits. Public for tests. Returns how many went on. */
    public int placeHeld(Level level, BlockPos pos, Player player, ItemStack stack) {
        PadBlockEntity pad = pad(level, pos);
        if (pad == null) {
            return 0;
        }
        if (!accepts(stack, level, pos)) {
            player.displayClientMessage(Component.translatable(refusedKey()).withStyle(ChatFormatting.RED), true);
            return 0;
        }
        int n = pad.insert(stack);
        if (n == 0) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.pad.full").withStyle(ChatFormatting.RED), true);
        } else {
            placeSound(level, pos);
        }
        return n;
    }

    /** Moves every accepted stack of the inventory onto the pad. Public for tests. */
    public int placeInventory(Level level, BlockPos pos, Player player) {
        PadBlockEntity pad = pad(level, pos);
        if (pad == null) {
            return 0;
        }
        Inventory inv = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>(inv.items);
        stacks.addAll(inv.offhand);
        int moved = 0;
        boolean any = false;
        for (ItemStack s : stacks) {
            if (!s.isEmpty() && accepts(s, level, pos)) {
                any = true;
                moved += pad.insert(s);
            }
        }
        inv.setChanged();
        if (!any) {
            player.displayClientMessage(Component.translatable(refusedKey()).withStyle(ChatFormatting.RED), true);
        } else if (moved == 0) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.pad.full").withStyle(ChatFormatting.RED), true);
        } else {
            placeSound(level, pos);
        }
        return moved;
    }

    /** Empty hand: everything on the pad back into the inventory with overflow at the player's feet. */
    public void takeBack(Level level, BlockPos pos, Player player) {
        PadBlockEntity pad = pad(level, pos);
        if (pad == null) {
            return;
        }
        if (pad.isEmpty()) {
            player.displayClientMessage(Component.translatable(helpKey()).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        for (ItemStack s : pad.takeAll()) {
            if (!player.getInventory().add(s)) {
                player.drop(s, false);
            }
        }
        player.getInventory().setChanged();
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
    }

    private static void placeSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    // ------------------------------------------------------------------------------------------------
    // Thrown items
    // ------------------------------------------------------------------------------------------------

    /** An item entity touching the pad is put on if accepted and there is room. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel) || !(entity instanceof ItemEntity item) || !item.isAlive()) {
            return;
        }
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || !accepts(stack, level, pos)) {
            return;
        }
        PadBlockEntity pad = pad(level, pos);
        if (pad == null) {
            return;
        }
        ItemStack copy = stack.copy();
        if (pad.insert(copy) > 0) {
            placeSound(level, pos);
            if (copy.isEmpty()) {
                item.discard();
            } else {
                item.setItem(copy);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Breaking / locked
    // ------------------------------------------------------------------------------------------------

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            PadBlockEntity pad = pad(level, pos);
            if (pad != null) {
                for (ItemStack s : pad.takeAll()) {
                    Block.popResource(level, pos, s);
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
}
