package com.holeydigsite.power;

import com.holeydigsite.item.ScytheItem;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.transport.ItemOutputs;
import com.holeydigsite.transport.ItemTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Block entity for the Flood Gate.
 * Runs the row wash machine effect step-by-step, harvests and replants ripe crops,
 * buffers collected produce, and feeds out to attached belts. Zero idle ticking.
 */
public class FloodGateBlockEntity extends BlockEntity {
    public static final int MAX_ROW_LENGTH = 16;
    public static final int COOLDOWN_TICKS = 300; // 15 seconds cooldown
    public static final int STEP_TICKS = 3;       // 3 ticks per row block during wash
    public static final int FEED_TICKS = 8;       // 8 ticks per item transfer to belt

    private final NonNullList<ItemStack> buffer = NonNullList.create();
    private final List<ItemStack> washDrops = new ArrayList<>();

    @Nullable
    private UUID owner;
    private boolean waterPowered = false;
    private long cooldownUntil = 0;
    private boolean hadSignal = false;

    // Active wash state
    private boolean activeWash = false;
    private int washStep = 0;
    private int stepTimer = 0;
    private int feedTimer = 0;
    private BlockPos washLastPos = null;

    public FloodGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLOOD_GATE.get(), pos, state);
    }

    public boolean isWaterPowered() {
        return waterPowered;
    }

    public void setWaterPowered(boolean waterPowered) {
        this.waterPowered = waterPowered;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public long cooldownUntil() {
        return cooldownUntil;
    }

    public NonNullList<ItemStack> buffer() {
        return buffer;
    }

    public boolean isWashing() {
        return activeWash;
    }

    public void onNeighborSignal(boolean signal) {
        if (signal && !hadSignal) {
            hadSignal = true;
            triggerWash(null);
        } else if (!signal) {
            hadSignal = false;
        }
    }

    public boolean triggerWash(@Nullable Player player) {
        if (level == null || level.isClientSide) {
            return false;
        }

        if (!waterPowered) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.flood_gate.no_water").withStyle(ChatFormatting.RED), true);
            }
            return false;
        }

        if (level.getGameTime() < cooldownUntil) {
            if (player != null) {
                long sec = Math.max(1, (cooldownUntil - level.getGameTime() + 19) / 20);
                player.displayClientMessage(Component.translatable("message.holeydigsite.flood_gate.cooldown", sec).withStyle(ChatFormatting.YELLOW), true);
            }
            return false;
        }

        BlockState state = getBlockState();
        if (activeWash || state.getValue(FloodGateBlock.WASHING)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.flood_gate.washing").withStyle(ChatFormatting.AQUA), true);
            }
            return false;
        }

        Direction facing = state.getValue(FloodGateBlock.FACING);
        BlockPos firstCrop = worldPosition.relative(facing, 1);
        BlockPos firstSoil = firstCrop.below();
        if (!level.getBlockState(firstSoil).is(Blocks.FARMLAND)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.holeydigsite.flood_gate.no_farmland").withStyle(ChatFormatting.RED), true);
            }
            return false;
        }

        // Start wash
        cooldownUntil = level.getGameTime() + COOLDOWN_TICKS;
        activeWash = true;
        washStep = 0;
        stepTimer = 0;
        feedTimer = 0;
        washDrops.clear();
        washLastPos = firstCrop;

        level.setBlock(worldPosition, state.setValue(FloodGateBlock.WASHING, true), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.7F, 1.2F);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.holeydigsite.flood_gate.started").withStyle(ChatFormatting.AQUA), true);
        }
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FloodGateBlockEntity gate) {
        if (gate.activeWash) {
            gate.stepTimer++;
            if (gate.stepTimer >= STEP_TICKS) {
                gate.stepTimer = 0;
                gate.advanceWashStep((ServerLevel) level, state);
            }
        } else if (!gate.buffer.isEmpty()) {
            gate.feedTimer++;
            if (gate.feedTimer >= FEED_TICKS) {
                gate.feedTimer = 0;
                gate.feedBelt((ServerLevel) level, state);
            }
        } else {
            // Wash complete and buffer empty: return to idle
            level.setBlock(pos, state.setValue(FloodGateBlock.WASHING, false), Block.UPDATE_ALL);
            gate.setChanged();
        }
    }

    private void advanceWashStep(ServerLevel level, BlockState state) {
        washStep++;
        Direction facing = state.getValue(FloodGateBlock.FACING);
        BlockPos cropPos = worldPosition.relative(facing, washStep);
        BlockPos soilPos = cropPos.below();

        // 1. Plot check
        if (PlotLock.isLocked(level, cropPos)) {
            finishWash(level, state, "plot_locked");
            return;
        }

        // 2. Obstacle check
        BlockState cropState = level.getBlockState(cropPos);
        if (isObstacle(level, cropPos, cropState)) {
            finishWash(level, state, "blocked");
            return;
        }

        // 3. Farmland check
        BlockState soilState = level.getBlockState(soilPos);
        if (!soilState.is(Blocks.FARMLAND)) {
            finishWash(level, state, "gap");
            return;
        }

        // Valid crop row position
        washLastPos = cropPos;
        sendWashParticles(level, cropPos);

        // Process crop
        if (ScytheItem.isCrop(cropState)) {
            if (ScytheItem.isRipe(cropState)) {
                List<ItemStack> drops = Block.getDrops(cropState, level, cropPos, level.getBlockEntity(cropPos), null, ItemStack.EMPTY);
                Item seedItem = ScytheItem.getSeedItem(cropState, level, cropPos);
                boolean replanted = false;

                if (seedItem != null) {
                    for (ItemStack drop : drops) {
                        if (drop.is(seedItem) && drop.getCount() > 0) {
                            drop.shrink(1);
                            replanted = true;
                            break;
                        }
                    }
                }

                if (replanted) {
                    level.setBlock(cropPos, ScytheItem.getResetState(cropState), Block.UPDATE_ALL);
                } else {
                    level.setBlock(cropPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }

                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        washDrops.add(drop);
                    }
                }

                if (owner != null) {
                    ServerPlayer sp = level.getServer().getPlayerList().getPlayer(owner);
                    if (sp != null) {
                        LedgerManager.recordHarvest(sp, BuiltInRegistries.BLOCK.getKey(cropState.getBlock()));
                    }
                }
                level.playSound(null, cropPos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.7F, 1.0F);
            }
        }

        if (washStep >= MAX_ROW_LENGTH) {
            finishWash(level, state, "completed");
        }
    }

    private void finishWash(ServerLevel level, BlockState state, String reason) {
        activeWash = false;

        boolean sideBelt = hasAdjacentBelt(level, state);
        if (sideBelt) {
            // Buffer in the flood gate to feed out at belt speed
            for (ItemStack drop : washDrops) {
                mergeIntoBuffer(drop);
            }
        } else {
            // No belt at the gate: deliver to belt at row end or pop items at row end
            Direction facing = state.getValue(FloodGateBlock.FACING);
            BlockPos endPos = washLastPos != null ? washLastPos : worldPosition;
            BlockPos beltAtEnd = endPos.relative(facing);

            for (ItemStack drop : washDrops) {
                if (!drop.isEmpty()) {
                    if (ItemOutputs.canOutput(level, beltAtEnd, facing.getOpposite(), drop)) {
                        ItemOutputs.tryOutput(level, beltAtEnd, facing.getOpposite(), drop);
                    } else {
                        Block.popResource(level, endPos, drop);
                    }
                }
            }
        }
        washDrops.clear();

        if (buffer.isEmpty()) {
            level.setBlock(worldPosition, state.setValue(FloodGateBlock.WASHING, false), Block.UPDATE_ALL);
        }
        setChanged();
    }

    private void feedBelt(ServerLevel level, BlockState state) {
        if (buffer.isEmpty()) return;

        Direction facing = state.getValue(FloodGateBlock.FACING);
        Direction[] sides = {facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()};

        for (Direction side : sides) {
            BlockPos targetPos = worldPosition.relative(side);
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.getBlock() instanceof ItemTarget) {
                ItemStack first = buffer.get(0);
                if (first.isEmpty()) {
                    buffer.remove(0);
                    setChanged();
                    return;
                }
                ItemStack one = first.copyWithCount(1);
                if (ItemOutputs.tryOutput(level, targetPos, side.getOpposite(), one)) {
                    first.shrink(1);
                    if (first.isEmpty()) {
                        buffer.remove(0);
                    }
                    setChanged();
                    return;
                }
            }
        }

        // If no belt can accept, check if any belt exists at all
        if (!hasAdjacentBelt(level, state)) {
            // Player removed the belt: pop buffered items so nothing is lost
            ItemStack first = buffer.remove(0);
            if (!first.isEmpty()) {
                Block.popResource(level, worldPosition, first);
            }
            setChanged();
        }
    }

    private boolean hasAdjacentBelt(Level level, BlockState state) {
        Direction facing = state.getValue(FloodGateBlock.FACING);
        Direction[] sides = {facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()};
        for (Direction side : sides) {
            BlockState s = level.getBlockState(worldPosition.relative(side));
            if (s.getBlock() instanceof ItemTarget) {
                return true;
            }
        }
        return false;
    }

    private void mergeIntoBuffer(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (ItemStack existing : buffer) {
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int toAdd = Math.min(space, stack.getCount());
                if (toAdd > 0) {
                    existing.grow(toAdd);
                    stack.shrink(toAdd);
                    if (stack.isEmpty()) return;
                }
            }
        }
        if (!stack.isEmpty()) {
            buffer.add(stack.copy());
            stack.setCount(0);
        }
    }

    public static boolean isObstacle(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || ScytheItem.isCrop(state)) {
            return false;
        }
        return state.blocksMotion() || state.isSolidRender(level, pos);
    }

    private void sendWashParticles(ServerLevel level, BlockPos pos) {
        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.15;
        double pz = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.SPLASH, px, py, pz, 12, 0.25, 0.1, 0.25, 0.08);
        level.sendParticles(ParticleTypes.RAIN, px, py + 0.1, pz, 6, 0.2, 0.05, 0.2, 0);
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.25F, 1.4F);
    }

    public void dropBuffer() {
        if (level != null && !level.isClientSide) {
            for (ItemStack s : buffer) {
                if (!s.isEmpty()) {
                    Block.popResource(level, worldPosition, s);
                }
            }
            buffer.clear();
            for (ItemStack s : washDrops) {
                if (!s.isEmpty()) {
                    Block.popResource(level, worldPosition, s);
                }
            }
            washDrops.clear();
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putLong("CooldownUntil", cooldownUntil);
        tag.putBoolean("WaterPowered", waterPowered);
        tag.putBoolean("HadSignal", hadSignal);
        ListTag bufTag = new ListTag();
        // Crops picked by a wash still running go in too, so a save mid-wash loses nothing
        for (ItemStack s : buffer) {
            if (!s.isEmpty()) {
                bufTag.add(s.save(registries));
            }
        }
        for (ItemStack s : washDrops) {
            if (!s.isEmpty()) {
                bufTag.add(s.save(registries));
            }
        }
        tag.put("Buffer", bufTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        cooldownUntil = tag.getLong("CooldownUntil");
        waterPowered = tag.getBoolean("WaterPowered");
        hadSignal = tag.getBoolean("HadSignal");
        buffer.clear();
        if (tag.contains("Buffer", Tag.TAG_LIST)) {
            ListTag bufTag = tag.getList("Buffer", Tag.TAG_COMPOUND);
            for (int i = 0; i < bufTag.size(); i++) {
                ItemStack.parse(registries, bufTag.getCompound(i)).ifPresent(buffer::add);
            }
        }
    }
}
