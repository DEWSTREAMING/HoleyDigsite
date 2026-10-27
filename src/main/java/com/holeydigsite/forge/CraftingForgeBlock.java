package com.holeydigsite.forge;

import com.holeydigsite.power.WaterNetworkBlock;
import com.holeydigsite.power.WaterNetworks;
import com.holeydigsite.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Crafting Forge, a lava-powered workstation. Load ingots (pooled as grams per metal) and recipe parts, pick a
 * recipe, pull the cast handle. See {@link CraftingForgeBlockEntity} for the contents and {@link ForgeRecipe} for recipes.
 *
 * <h2>Controls</h2>
 * <ul>
 *   <li><b>Load</b>: right-click the forge (not the handle) with ingots (whole stack) or a recipe part (whole stack),
 *       sneak-right-click with anything loads every ingot from the inventory. Hoppers insert too. Chunks, rubies and
 *       other items are refused with a short message.</li>
 *   <li><b>Recipe</b>: empty-hand right-click (not the handle) equals next recipe, sneak plus empty hand equals previous.</li>
 *   <li><b>Cast</b>: right-click the HANDLE on the front (any hand) or a redstone rising edge. Needs the forge lit, all
 *       requirements and no waiting output. Takes {@link CraftingForgeBlockEntity#CAST_TICKS} ticks.</li>
 *   <li><b>Collect</b>: empty-hand right-click anywhere while output is waiting (product, leftover ingots, parts).</li>
 *   <li><b>Take back</b> without casting: sneak plus empty-hand right-click on the handle.</li>
 *   <li><b>Break</b>: drops the forge plus everything inside (pool as ingots, parts, output).</li>
 * </ul>
 * Lava port on the back, like the Forge. {@link #LIT} is pushed in by the lava network, no fuel, never ticks unless casting.
 */
public class CraftingForgeBlock extends BaseEntityBlock implements WaterNetworkBlock, com.holeydigsite.transport.ItemTarget {
    public static final MapCodec<CraftingForgeBlock> CODEC = simpleCodec(CraftingForgeBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** A cast is running, handle pulled down, glow and sparks. */
    public static final BooleanProperty CASTING = BooleanProperty.create("casting");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /**
     * Where the cast lever sits. FRONT: on the front face of the top section under the brim edge. SIDE: on the viewer's right
     * face. CLASSIC: the first design, a handle standing on the front with the brim cut back. Keep in step with HANDLE in
     * tools/make_phase10_assets.py.
     */
    public static final String HANDLE_FRONT = "front", HANDLE_SIDE = "side", HANDLE_CLASSIC = "classic";
    public static final String HANDLE = HANDLE_FRONT;
    /** Closed brim all round (front and side designs). */
    public static final boolean SIDE_HANDLE = !HANDLE_CLASSIC.equals(HANDLE);

    /** Depth of the handle zone on the front, in pixels (the body starts behind it). Front handle only. */
    public static final double HANDLE_DEPTH = 4.0;

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES[d.get2DDataValue()] = SIDE_HANDLE ? Shapes.or(
                    rotated(d, 1, 0, 1, 15, 4, 15),     // foot
                    rotated(d, 4, 4, 3, 12, 10, 13),    // waist
                    rotated(d, 0, 10, 0, 16, 16, 16))   // top with the basin (the lever hangs off its side face)
                    : Shapes.or(
                    rotated(d, 1, 0, 3, 15, 4, 15),
                    rotated(d, 3, 4, 4, 13, 10, 14),
                    rotated(d, 0, 10, 3, 16, 16, 16),
                    rotated(d, 6, 4, 0, 10, 13, 4));   // cast handle (front)
        }
    }

    /** A box given for the NORTH-facing model where front is low z, rotated to {@code facing}. */
    private static VoxelShape rotated(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public CraftingForgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false)
                .setValue(CASTING, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, CASTING, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    /** The handle (front) faces the player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
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
    public static CraftingForgeBlockEntity forge(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CraftingForgeBlockEntity f ? f : null;
    }

    // block entity and ticking

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingForgeBlockEntity(pos, state);
    }

    /** Only a casting, lit forge ticks, a cast pauses while the lava is gone. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(CASTING) || !state.getValue(LIT)) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CRAFTING_FORGE.get(), CraftingForgeBlockEntity::serverTick);
    }

    // lava network

    @Override
    public Role waterRole() {
        return Role.MACHINE;
    }

    @Override
    public NetworkFluid networkFluid() {
        return NetworkFluid.LAVA;
    }

    /** The lava port is on the back. */
    @Override
    public boolean hasWaterConnection(BlockState state, Direction side) {
        return side == state.getValue(FACING).getOpposite();
    }

    @Override
    public void setWaterPowered(ServerLevel level, BlockPos pos, BlockState state, boolean powered) {
        if (state.getValue(LIT) != powered) {
            level.setBlock(pos, state.setValue(LIT, powered), Block.UPDATE_ALL);
            level.playSound(null, pos, powered ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) || oldState.getValue(FACING) != state.getValue(FACING)) {
            WaterNetworks.markDirty(level, pos);
        }
        if (!oldState.is(this)) {
            level.invalidateCapabilities(pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    /** Breaking drops the forge (loot table) plus everything inside (pool as ingots, parts, output). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && forge(level, pos) instanceof CraftingForgeBlockEntity f) {
                for (ItemStack s : f.dropContents()) {
                    Block.popResource(level, pos, s);
                }
            }
            WaterNetworks.markDirty(level, pos);
            level.invalidateCapabilities(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Redstone, one cast per rising edge, silently does nothing when it can't cast. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            if (powered) {
                tryCast(level, pos, null);
            }
        }
    }

    // player

    /** True if the hit is on the cast lever's own area (front or side face of the top section), or with the classic design the front {@link #HANDLE_DEPTH} pixels. */
    public static boolean isHandle(BlockState state, BlockPos pos, Vec3 hit) {
        Direction facing = state.getValue(FACING);
        if (HANDLE_FRONT.equals(HANDLE)) {
            double wx = hit.x - pos.getX(), wz = hit.z - pos.getZ();
            // lateral position across the front, 0 at the viewer's left, and depth from the front face, in blocks
            double lat = switch (facing) {
                case NORTH -> wx;
                case SOUTH -> 1 - wx;
                case WEST -> wz;
                default -> 1 - wz;
            };
            double depth = switch (facing) {
                case NORTH -> wz;
                case SOUTH -> 1 - wz;
                case WEST -> wx;
                default -> 1 - wx;
            };
            double y = hit.y - pos.getY();
            return depth <= 1.0E-4 && lat > 5.0 / 16.0 && lat < 11.0 / 16.0 && y >= 10.5 / 16.0 && y < 14.5 / 16.0;
        }
        if (HANDLE_SIDE.equals(HANDLE)) {
            // the hit in the frame of the north facing model, in blocks: lx 0 is the viewer's right edge, lz 0 the front
            double wx = hit.x - pos.getX(), wz = hit.z - pos.getZ();
            double lx = switch (facing) {
                case NORTH -> wx;
                case SOUTH -> 1 - wx;
                case WEST -> 1 - wz;
                default -> wz;
            };
            double lz = switch (facing) {
                case NORTH -> wz;
                case SOUTH -> 1 - wz;
                case WEST -> wx;
                default -> 1 - wx;
            };
            double y = hit.y - pos.getY();
            return lx <= 1.0E-4 && lz > 5.0 / 16.0 && lz < 11.0 / 16.0 && y >= 10.5 / 16.0 && y < 14.5 / 16.0;
        }
        double local = switch (facing) {
            case NORTH -> hit.z - pos.getZ();
            case SOUTH -> pos.getZ() + 1 - hit.z;
            case WEST -> hit.x - pos.getX();
            default -> pos.getX() + 1 - hit.x;
        };
        double y = hit.y - pos.getY();
        double lateral = facing.getAxis() == Direction.Axis.Z ? hit.x - pos.getX() : hit.z - pos.getZ();
        return local <= HANDLE_DEPTH / 16.0 + 1.0E-4 && y < 13.5 / 16.0 && lateral > 4.5 / 16.0 && lateral < 11.5 / 16.0;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            if (isHandle(state, pos, hit.getLocation())) {
                handle(level, pos, player);
            } else if (player.isShiftKeyDown()) {
                loadAllIngots(level, pos, player);
            } else {
                loadHeld(level, pos, player, stack);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            CraftingForgeBlockEntity f = forge(level, pos);
            if (f == null) {
                return InteractionResult.CONSUME;
            }
            boolean handle = isHandle(state, pos, hit.getLocation());
            if (f.hasOutput()) {
                collect(level, pos, player);
            } else if (handle && player.isShiftKeyDown()) {
                takeBack(level, pos, player);
            } else if (handle) {
                handle(level, pos, player);
            } else if (f.isCasting()) {
                busy(player, f);
            } else {
                ForgeRecipes.Entry e = f.cycle(player.isShiftKeyDown() ? -1 : 1);
                if (e == null) {
                    msg(player, "message.holeydigsite.crafting_forge.no_recipes", ChatFormatting.RED);
                } else {
                    level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3F, 1.4F);
                    player.displayClientMessage(Component.translatable("message.holeydigsite.crafting_forge.recipe",
                            e.recipe().name(level.registryAccess())).withStyle(ChatFormatting.YELLOW), true);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The handle, collect if output is waiting, else try to cast. */
    private static void handle(Level level, BlockPos pos, Player player) {
        CraftingForgeBlockEntity f = forge(level, pos);
        if (f != null && f.hasOutput()) {
            collect(level, pos, player);
        } else {
            tryCast(level, pos, player);
        }
    }

    /**
     * Starts a cast if the forge is lit, has no output waiting, isn't casting and the selected recipe's requirements are all
     * loaded. With a player, a refusal shows a short red reason, from redstone ({@code player == null}) nothing happens.
     * Public for tests. Returns true if a cast started.
     */
    public static boolean tryCast(Level level, BlockPos pos, @Nullable Player player) {
        BlockState state = level.getBlockState(pos);
        CraftingForgeBlockEntity f = forge(level, pos);
        if (f == null || !(state.getBlock() instanceof CraftingForgeBlock)) {
            return false;
        }
        if (f.isCasting()) {
            if (player != null) busy(player, f);
            return false;
        }
        if (f.hasOutput()) {
            if (player != null) msg(player, "message.holeydigsite.crafting_forge.output_waiting", ChatFormatting.RED);
            return false;
        }
        ForgeRecipes.Entry e = f.recipe();
        if (e == null) {
            if (player != null) msg(player, "message.holeydigsite.crafting_forge.no_recipes", ChatFormatting.RED);
            return false;
        }
        if (!state.getValue(LIT)) {
            if (player != null) msg(player, "message.holeydigsite.crafting_forge.unlit", ChatFormatting.RED);
            return false;
        }
        CraftingForgeBlockEntity.Missing m = f.missing(e.recipe());
        if (m != null) {
            if (player != null) {
                Component what = m.metal() != null
                        ? Component.translatable("message.holeydigsite.crafting_forge.needs_metal", m.amount(), Metals.name(m.metal()))
                        : Component.translatable("message.holeydigsite.crafting_forge.needs_part", m.amount(),
                                new ItemStack(m.part()).getHoverName());
                player.displayClientMessage(what.copy().withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        f.startCast();
        level.setBlock(pos, state.setValue(CASTING, true), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.6F);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.0F);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.crafting_forge.casting",
                    e.recipe().name(level.registryAccess())).withStyle(ChatFormatting.GOLD), true);
        }
        return true;
    }

    /** Loads the held stack (ingots of any weight, or a recipe part). Public for tests, returns items taken. */
    public static int loadHeld(Level level, BlockPos pos, Player player, ItemStack stack) {
        CraftingForgeBlockEntity f = forge(level, pos);
        if (f == null) {
            return 0;
        }
        CraftingForgeBlockEntity.Refusal why = f.check(stack);
        if (why != CraftingForgeBlockEntity.Refusal.NONE) {
            refuse(player, f, why);
            return 0;
        }
        ResourceLocation metal = CraftingForgeBlockEntity.ingotMetal(stack);
        int gramsBefore = metal == null ? 0 : f.grams(metal);
        Component name = stack.getHoverName();
        int n = f.load(stack, stack.getCount(), false);
        stack.consume(n, player);
        loadSound(level, pos);
        if (metal != null) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.crafting_forge.loaded_metal",
                    f.grams(metal) - gramsBefore, Metals.name(metal), f.grams(metal)).withStyle(ChatFormatting.GOLD), true);
        } else {
            player.displayClientMessage(Component.translatable("message.holeydigsite.crafting_forge.loaded_part", n, name)
                    .withStyle(ChatFormatting.GOLD), true);
        }
        return n;
    }

    /** Sneak-click, every ingot in the inventory (main, hotbar, off hand). Public for tests, returns grams loaded. */
    public static int loadAllIngots(Level level, BlockPos pos, Player player) {
        CraftingForgeBlockEntity f = forge(level, pos);
        if (f == null) {
            return 0;
        }
        Inventory inv = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>(inv.items);
        stacks.addAll(inv.offhand);
        int grams = 0;
        CraftingForgeBlockEntity.Refusal blocked = null;
        for (ItemStack s : stacks) {
            if (CraftingForgeBlockEntity.ingotMetal(s) == null) {
                continue;
            }
            CraftingForgeBlockEntity.Refusal why = f.check(s);
            if (why != CraftingForgeBlockEntity.Refusal.NONE) {
                blocked = why;
                break;
            }
            int g = s.getCount() * com.holeydigsite.economy.Selling.grams(s);
            int n = f.load(s, s.getCount(), false);
            if (n > 0) {
                grams += g;
                s.shrink(n);
            }
        }
        inv.setChanged();
        if (grams > 0) {
            loadSound(level, pos);
            player.displayClientMessage(Component.translatable("message.holeydigsite.crafting_forge.loaded_all", grams)
                    .withStyle(ChatFormatting.GOLD), true);
        } else if (blocked != null) {
            refuse(player, f, blocked);
        } else {
            msg(player, "message.holeydigsite.crafting_forge.no_ingots", ChatFormatting.RED);
        }
        return grams;
    }

    /** Collects the output into the inventory (overflow at the feet). Public for tests, returns the stacks given. */
    public static List<ItemStack> collect(Level level, BlockPos pos, Player player) {
        CraftingForgeBlockEntity f = forge(level, pos);
        if (f == null) {
            return List.of();
        }
        List<ItemStack> items = f.takeOutput();
        give(player, items);
        if (!items.isEmpty()) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
            Component msg = items.size() == 1
                    ? Component.translatable("message.holeydigsite.crafting_forge.collected_one", items.get(0).getHoverName())
                    : Component.translatable("message.holeydigsite.crafting_forge.collected", items.get(0).getHoverName(), items.size() - 1);
            player.displayClientMessage(msg.copy().withStyle(ChatFormatting.GREEN), true);
        }
        return items;
    }

    /** Takes everything back out without casting. Public for tests, returns the stacks given. */
    public static List<ItemStack> takeBack(Level level, BlockPos pos, Player player) {
        CraftingForgeBlockEntity f = forge(level, pos);
        if (f == null) {
            return List.of();
        }
        if (f.isCasting()) {
            busy(player, f);
            return List.of();
        }
        List<ItemStack> items = f.takeBack();
        give(player, items);
        if (items.isEmpty()) {
            msg(player, "message.holeydigsite.crafting_forge.empty", ChatFormatting.YELLOW);
        } else {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 0.8F);
            msg(player, "message.holeydigsite.crafting_forge.took_back", ChatFormatting.GOLD);
        }
        return items;
    }

    private static void give(Player player, List<ItemStack> items) {
        for (ItemStack s : items) {
            ItemStack copy = s.copy();
            if (!player.getInventory().add(copy) && !copy.isEmpty()) {
                player.drop(copy, false);
            }
        }
    }

    private static void loadSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    private static void busy(Player player, CraftingForgeBlockEntity f) {
        int s = Math.max(1, (CraftingForgeBlockEntity.CAST_TICKS - f.castTicks() + 19) / 20);
        player.displayClientMessage(Component.translatable("message.holeydigsite.crafting_forge.busy", s)
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static void refuse(Player player, CraftingForgeBlockEntity f, CraftingForgeBlockEntity.Refusal why) {
        if (why == CraftingForgeBlockEntity.Refusal.CASTING) {
            busy(player, f);
            return;
        }
        String key = switch (why) {
            case OUTPUT -> "message.holeydigsite.crafting_forge.output_waiting";
            case CHUNK -> "message.holeydigsite.crafting_forge.chunk";
            case DIRTY -> "message.holeydigsite.crafting_forge.dirty";
            case FULL -> "message.holeydigsite.crafting_forge.full";
            default -> "message.holeydigsite.crafting_forge.refused";
        };
        msg(player, key, ChatFormatting.RED);
    }

    private static void msg(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    // client visuals

    /** Embers while lit, sparks and flames around the basin while casting. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        if (state.getValue(CASTING)) {
            for (int i = 0; i < 4; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (random.nextDouble() - 0.5) * 0.6, y + 0.05,
                        z + (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.2, 0.15, (random.nextDouble() - 0.5) * 0.2);
            }
            level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.4, y, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.03, 0);
            if (random.nextInt(4) == 0) {
                level.playLocalSound(pos, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.2F, false);
            }
        } else if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.5, y + 0.02,
                    z + (random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.crafting_forge").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.crafting_forge.how").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.crafting_forge.cast").withStyle(ChatFormatting.DARK_GRAY));
    }

    // belts deliver ingots and recipe parts (same rules as hoppers)

    @Override
    public boolean canAcceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        return level.getBlockEntity(pos) instanceof CraftingForgeBlockEntity f && f.load(one, 1, true) == 1;
    }

    @Override
    public void acceptItem(Level level, BlockPos pos, BlockState state, Direction from, ItemStack one) {
        if (level.getBlockEntity(pos) instanceof CraftingForgeBlockEntity f) {
            f.load(one, 1, false);
        }
    }
}
