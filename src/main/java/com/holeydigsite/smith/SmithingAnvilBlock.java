package com.holeydigsite.smith;

import com.holeydigsite.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Smithing Anvil. Lay whole ingots and a cut gem on it, pick a recipe, strike it with the Blacksmith
 * Hammer, and get a piece that remembers its materials. See {@link SmithingAnvilBlockEntity} for the contents and {@link AnvilRecipe}.
 *
 * <h2>Controls</h2>
 * <ul>
 *   <li><b>Lay</b>: right-click with an ingot or a cut gem puts one on the anvil, even from a stack. Uncut gems, unknown metals,
 *       too light ingots, a second gem and a fourth ingot are refused with a short message.</li>
 *   <li><b>Take back</b>: empty-hand right-click returns the item laid last. <b>Recipe</b>: sneak and empty hand cycles.</li>
 *   <li><b>Hammer</b>: right-click with the Blacksmith Hammer. Each valid hit counts, and the last one makes the piece into the
 *       player's inventory. A mismatching recipe says what is wrong and uses nothing up.</li>
 *   <li><b>Break</b>: drops the anvil plus everything on it.</li>
 * </ul>
 * All state is in the block entity.
 */
public class SmithingAnvilBlock extends BaseEntityBlock {
    public static final MapCodec<SmithingAnvilBlock> CODEC = simpleCodec(SmithingAnvilBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Ticks between two hammer hits. */
    public static final int HIT_COOLDOWN = 8;

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES[d.get2DDataValue()] = Shapes.or(
                    rotated(d, 2, 0, 2, 14, 4, 14),      // foot
                    rotated(d, 4, 4, 5, 12, 10, 11),     // waist
                    rotated(d, 0, 10, 3, 16, 16, 13));   // top
        }
    }

    /** A box given for the NORTH-facing model with the long axis along x, rotated to {@code facing}. */
    private static VoxelShape rotated(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public SmithingAnvilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    public static SmithingAnvilBlockEntity anvil(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SmithingAnvilBlockEntity a ? a : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmithingAnvilBlockEntity(pos, state);
    }

    /** Breaking drops the anvil plus everything on it. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && anvil(level, pos) instanceof SmithingAnvilBlockEntity a) {
            for (ItemStack s : a.dropContents()) {
                Block.popResource(level, pos, s);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ------------------------------------------------------------------------------------------------ player

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(ModItems.BLACKSMITH_HAMMER.get())) {
            if (!level.isClientSide && !player.getCooldowns().isOnCooldown(stack.getItem())) {
                player.getCooldowns().addCooldown(stack.getItem(), HIT_COOLDOWN);
                hammer(level, pos, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        SmithingAnvilBlockEntity a = anvil(level, pos);
        if (a == null || a.check(stack) == SmithingAnvilBlockEntity.Refusal.NOT_ACCEPTED) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            layHeld(level, pos, player, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                cycle(level, pos, player);
            } else {
                takeBack(level, pos, player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Lays one item of the held stack on the anvil. Returns whether it was laid. */
    public static boolean layHeld(Level level, BlockPos pos, Player player, ItemStack stack) {
        SmithingAnvilBlockEntity a = anvil(level, pos);
        if (a == null) {
            return false;
        }
        SmithingAnvilBlockEntity.Refusal why = a.check(stack);
        if (why != SmithingAnvilBlockEntity.Refusal.NONE) {
            AnvilRecipes.Entry e = a.recipe();
            String key = switch (why) {
                case UNCUT -> "message.holeydigsite.anvil.uncut";
                case UNKNOWN_METAL -> "message.holeydigsite.anvil.metal";
                case LIGHT -> "message.holeydigsite.anvil.light";
                case FULL -> "message.holeydigsite.anvil.full";
                case SECOND_GEM -> "message.holeydigsite.anvil.gem_second";
                case EVENT_ITEM -> "message.holeydigsite.anvil.refused_event";
                default -> "message.holeydigsite.anvil.refused";
            };
            Component msg = why == SmithingAnvilBlockEntity.Refusal.LIGHT && e != null
                    ? Component.translatable(key, e.recipe().minGrams()) : Component.translatable(key);
            player.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        Component name = stack.getHoverName();
        a.lay(stack);
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
        player.displayClientMessage(Component.translatable("message.holeydigsite.anvil.laid", name).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Gives the item laid last back, or empty if the anvil was empty. */
    public static ItemStack takeBack(Level level, BlockPos pos, Player player) {
        SmithingAnvilBlockEntity a = anvil(level, pos);
        if (a == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = a.takeLast();
        if (s.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.anvil.empty").withStyle(ChatFormatting.YELLOW), true);
            return s;
        }
        give(player, s.copy());
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 0.8F);
        player.displayClientMessage(Component.translatable("message.holeydigsite.anvil.took", s.getHoverName()).withStyle(ChatFormatting.GOLD), true);
        return s;
    }

    /** Selects the next recipe. */
    public static void cycle(Level level, BlockPos pos, Player player) {
        SmithingAnvilBlockEntity a = anvil(level, pos);
        if (a == null) {
            return;
        }
        AnvilRecipes.Entry e = a.cycle(1);
        if (e == null) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.anvil.no_recipes").withStyle(ChatFormatting.RED), true);
            return;
        }
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, 1.4F);
        player.displayClientMessage(Component.translatable("message.holeydigsite.anvil.recipe", e.recipe().name(level.registryAccess())).withStyle(ChatFormatting.YELLOW), true);
    }

    /** What one hammer hit did. */
    public enum Hit { NO_ANVIL, NO_RECIPE, MISMATCH, HIT, MADE }

    /**
     * One hammer hit. A hit counts only if the contents match the selected recipe
     * ({@link SmithingAnvilBlockEntity#problem}). Otherwise a short red message says what is wrong and nothing changes. The
     * last needed hit makes the piece, which goes to the player.
     */
    public static Hit hammer(Level level, BlockPos pos, Player player) {
        SmithingAnvilBlockEntity a = anvil(level, pos);
        if (a == null) {
            return Hit.NO_ANVIL;
        }
        AnvilRecipes.Entry e = a.recipe();
        if (e == null) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.anvil.no_recipes").withStyle(ChatFormatting.RED), true);
            return Hit.NO_RECIPE;
        }
        AnvilRecipe r = e.recipe();
        SmithingAnvilBlockEntity.Problem p = a.problem(r);
        if (p != SmithingAnvilBlockEntity.Problem.NONE) {
            a.resetHits();
            Component msg = switch (p) {
                case FEW_INGOTS -> Component.translatable("message.holeydigsite.anvil.few", r.ingots(), a.ingotCount());
                case MANY_INGOTS -> Component.translatable("message.holeydigsite.anvil.many", r.ingots(), a.ingotCount());
                case LIGHT -> Component.translatable("message.holeydigsite.anvil.heavy", r.minGrams());
                case NEEDS_GEM -> Component.translatable("message.holeydigsite.anvil.needs_gem");
                default -> Component.translatable("message.holeydigsite.anvil.no_gem");
            };
            player.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), true);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.25F, 1.6F);
            return Hit.MISMATCH;
        }
        int hits = a.addHit();
        double x = pos.getX() + 0.5, y = pos.getY() + 1.02, z = pos.getZ() + 0.5;
        if (hits < r.hits()) {
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 0.8F + 0.06F * hits);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.25, 0.05, 0.25, 0.12);
                sl.sendParticles(ParticleTypes.CRIT, x, y, z, 3, 0.2, 0.05, 0.2, 0.05);
            }
            player.displayClientMessage(Component.translatable("message.holeydigsite.anvil.hit", hits, r.hits()).withStyle(ChatFormatting.GOLD), true);
            return Hit.HIT;
        }
        ItemStack piece = a.finish(r);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.holeydigsite.ledger.LedgerManager.recordSmith(sp, e.id().getPath(), piece);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 1.1F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 24, 0.3, 0.1, 0.3, 0.2);
            sl.sendParticles(ParticleTypes.FLAME, x, y, z, 6, 0.2, 0.05, 0.2, 0.02);
        }
        player.displayClientMessage(Component.translatable("message.holeydigsite.anvil.made", piece.getHoverName()).withStyle(ChatFormatting.GREEN), true);
        give(player, piece);
        return Hit.MADE;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.smithing_anvil").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.smithing_anvil.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
