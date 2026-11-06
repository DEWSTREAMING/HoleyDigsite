package com.holeydigsite.machine;

import org.jetbrains.annotations.Nullable;

import com.holeydigsite.transport.ItemOutputs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Block entity for water processing machines. Zero idle ticking: runs only while actively processing or draining output buffer.
 */
public abstract class WaterProcessingBlockEntity extends BlockEntity {
    public static final int MAX_BUFFER_ITEMS = 8;
    public static final int MAX_INPUT_ITEMS = 8;
    public static final int FEED_INTERVAL_TICKS = 8;

    private boolean waterPowered = false;
    private final NonNullList<ItemStack> inputBuffer = NonNullList.create();
    private final NonNullList<ItemStack> outputBuffer = NonNullList.create();

    private ItemStack processingInput = ItemStack.EMPTY;
    private ItemStack plannedResult = ItemStack.EMPTY;
    private int progressTicks = 0;
    private int totalTicks = 0;
    private int feedCooldown = 0;

    private int clientBufferedCount = 0;
    private int lastSentBufferedCount = -1;

    protected WaterProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        if (state.hasProperty(WaterProcessingBlock.WATER_POWERED)) {
            this.waterPowered = state.getValue(WaterProcessingBlock.WATER_POWERED);
        }
    }

    public abstract ResourceLocation machineId();
    public String mode() { return ""; }

    public boolean isWaterPowered() { return waterPowered; }
    public void setWaterPowered(boolean powered) {
        this.waterPowered = powered;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            checkStartRecipe();
            Direction facing = getBlockState().getValue(WaterProcessingBlock.FACING);
            updateWorkingStatus(worldPosition.relative(facing), facing);
        }
    }

    public NonNullList<ItemStack> inputBuffer() { return inputBuffer; }
    public NonNullList<ItemStack> outputBuffer() { return outputBuffer; }

    public int totalBufferedItems() {
        if (level == null || level.isClientSide) {
            if (clientBufferedCount > 0 || outputBuffer.isEmpty()) {
                return clientBufferedCount;
            }
        }
        int count = 0;
        for (ItemStack s : outputBuffer) count += s.getCount();
        return count;
    }

    public int totalInputItems() {
        int count = processingInput.getCount();
        for (ItemStack s : inputBuffer) count += s.getCount();
        return count;
    }

    public boolean isBufferFull() {
        return totalBufferedItems() >= MAX_BUFFER_ITEMS;
    }

    public boolean isInputFull() {
        return totalInputItems() >= MAX_INPUT_ITEMS;
    }

    public int availableInputSpace() {
        return Math.max(0, MAX_INPUT_ITEMS - totalInputItems());
    }

    public boolean canAcceptInput(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !waterPowered || isInputFull() || isBufferFull()) return false;
        return MachineRecipeManager.find(machineId(), mode(), stack).isPresent();
    }

    public void acceptInput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        int space = availableInputSpace();
        if (space <= 0) return;

        ItemStack toInsert = stack.split(Math.min(stack.getCount(), space));

        for (ItemStack existing : inputBuffer) {
            if (ItemStack.isSameItemSameComponents(existing, toInsert)) {
                int add = Math.min(toInsert.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (add > 0) {
                    existing.grow(add);
                    toInsert.shrink(add);
                    if (toInsert.isEmpty()) break;
                }
            }
        }
        while (!toInsert.isEmpty()) {
            int count = Math.min(toInsert.getCount(), toInsert.getMaxStackSize());
            inputBuffer.add(toInsert.split(count));
        }
        setChanged();
        checkStartRecipe();
    }

    public void checkStartRecipe() {
        if (level == null || level.isClientSide || !waterPowered || isBufferFull() || !processingInput.isEmpty()) {
            return;
        }
        for (int i = 0; i < inputBuffer.size(); i++) {
            ItemStack in = inputBuffer.get(i);
            Optional<MachineRecipe> opt = MachineRecipeManager.find(machineId(), mode(), in);
            if (opt.isPresent()) {
                MachineRecipe r = opt.get();
                int needed = 1;
                for (MachineRecipe.IngredientEntry ing : r.ingredients()) {
                    if (ing.matches(in)) {
                        needed = ing.count();
                        break;
                    }
                }
                if (in.getCount() >= needed) {
                    processingInput = in.split(needed);
                    if (in.isEmpty()) {
                        inputBuffer.remove(i);
                    }
                    plannedResult = r.result();
                    progressTicks = 0;
                    totalTicks = r.ticks();
                    setChanged();
                    ensureWorkingState(true);
                    return;
                }
            }
        }
        Direction facing = getBlockState().getValue(WaterProcessingBlock.FACING);
        updateWorkingStatus(worldPosition.relative(facing), facing);
    }

    public void notifyBufferChanged() {
        if (level != null && !level.isClientSide) {
            int current = totalBufferedItems();
            if (current != lastSentBufferedCount) {
                lastSentBufferedCount = current;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    public void onFrontNeighborChanged() {
        if (level == null || level.isClientSide) return;
        Direction facing = getBlockState().getValue(WaterProcessingBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);
        if (!outputBuffer.isEmpty()) {
            if (ItemOutputs.canOutput(level, frontPos, facing.getOpposite(), outputBuffer.get(0).copyWithCount(1))) {
                feedCooldown = FEED_INTERVAL_TICKS;
                ensureWorkingState(true);
            }
        }
        checkStartRecipe();
    }

    public void onScheduledTick() {
        if (level == null || level.isClientSide || outputBuffer.isEmpty()) return;

        Direction facing = getBlockState().getValue(WaterProcessingBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);
        ItemStack top = outputBuffer.get(0);

        if (ItemOutputs.canOutput(level, frontPos, facing.getOpposite(), top.copyWithCount(1))) {
            feedCooldown = FEED_INTERVAL_TICKS;
            ensureWorkingState(true);
        } else {
            ensureWorkingState(false);
        }
    }

    public void updateWorkingStatus(BlockPos frontPos, Direction facing) {
        if (level == null || level.isClientSide) return;

        if (!processingInput.isEmpty() && waterPowered) {
            ensureWorkingState(true);
            return;
        }

        if (!outputBuffer.isEmpty()) {
            ItemStack top = outputBuffer.get(0);
            if (ItemOutputs.canOutput(level, frontPos, facing.getOpposite(), top.copyWithCount(1))) {
                ensureWorkingState(true);
                return;
            }
        }

        ensureWorkingState(false);
    }

    public void ensureWorkingState(boolean working) {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(WaterProcessingBlock.WORKING) && state.getValue(WaterProcessingBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(WaterProcessingBlock.WORKING, working), 3);
            }
            if (!working && !outputBuffer.isEmpty()) {
                if (level instanceof ServerLevel sl && !sl.getBlockTicks().hasScheduledTick(worldPosition, getBlockState().getBlock())) {
                    sl.scheduleTick(worldPosition, getBlockState().getBlock(), WaterProcessingBlock.RECHECK_TICKS);
                }
            }
        }
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;

        Direction facing = getBlockState().getValue(WaterProcessingBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);

        // Feed from output buffer to front belt
        if (!outputBuffer.isEmpty()) {
            feedCooldown++;
            if (feedCooldown >= FEED_INTERVAL_TICKS) {
                feedCooldown = 0;
                ItemStack top = outputBuffer.get(0);
                ItemStack one = top.copyWithCount(1);
                if (ItemOutputs.tryOutput(level, frontPos, facing.getOpposite(), one)) {
                    top.shrink(1);
                    if (top.isEmpty()) {
                        outputBuffer.remove(0);
                    }
                    setChanged();
                    notifyBufferChanged();
                }
            }
        }

        // Processing recipe
        if (!processingInput.isEmpty()) {
            if (waterPowered) {
                progressTicks++;
                if (progressTicks >= totalTicks) {
                    ItemStack out = plannedResult.copy();
                    processingInput = ItemStack.EMPTY;
                    plannedResult = ItemStack.EMPTY;
                    progressTicks = 0;

                    // Try to push directly to front
                    if (!out.isEmpty() && ItemOutputs.tryOutput(level, frontPos, facing.getOpposite(), out)) {
                        out.shrink(1);
                    }
                    if (!out.isEmpty()) {
                        addToOutputBuffer(out);
                    }
                    setChanged();
                    notifyBufferChanged();

                    checkStartRecipe();
                }
            }
        } else {
            checkStartRecipe();
        }

        updateWorkingStatus(frontPos, facing);
    }

    private void addToOutputBuffer(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (ItemStack s : outputBuffer) {
            if (ItemStack.isSameItemSameComponents(s, stack)) {
                int add = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                if (add > 0) {
                    s.grow(add);
                    stack.shrink(add);
                    if (stack.isEmpty()) break;
                }
            }
        }
        while (!stack.isEmpty()) {
            int toAdd = Math.min(stack.getCount(), stack.getMaxStackSize());
            outputBuffer.add(stack.split(toAdd));
        }
        notifyBufferChanged();
    }

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack s : inputBuffer) {
            if (!s.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), s);
        }
        inputBuffer.clear();
        if (!processingInput.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), processingInput);
            processingInput = ItemStack.EMPTY;
        }
        for (ItemStack s : outputBuffer) {
            if (!s.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), s);
        }
        outputBuffer.clear();
        notifyBufferChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("WaterPowered", waterPowered);
        tag.putInt("ProgressTicks", progressTicks);
        tag.putInt("TotalTicks", totalTicks);

        ListTag inList = new ListTag();
        for (ItemStack s : inputBuffer) {
            inList.add(s.saveOptional(registries));
        }
        tag.put("InputBuffer", inList);

        if (!processingInput.isEmpty()) {
            tag.put("ProcessingInput", processingInput.saveOptional(registries));
        }
        if (!plannedResult.isEmpty()) {
            tag.put("PlannedResult", plannedResult.saveOptional(registries));
        }

        ListTag outList = new ListTag();
        for (ItemStack s : outputBuffer) {
            outList.add(s.saveOptional(registries));
        }
        tag.put("OutputBuffer", outList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        waterPowered = tag.getBoolean("WaterPowered");
        progressTicks = tag.getInt("ProgressTicks");
        totalTicks = tag.getInt("TotalTicks");

        if (tag.contains("BufferedCount")) {
            clientBufferedCount = tag.getInt("BufferedCount");
        }

        inputBuffer.clear();
        ListTag inList = tag.getList("InputBuffer", Tag.TAG_COMPOUND);
        for (int i = 0; i < inList.size(); i++) {
            ItemStack.parse(registries, inList.getCompound(i)).ifPresent(inputBuffer::add);
        }

        if (tag.contains("ProcessingInput")) {
            processingInput = ItemStack.parse(registries, tag.getCompound("ProcessingInput")).orElse(ItemStack.EMPTY);
        } else {
            processingInput = ItemStack.EMPTY;
        }
        if (tag.contains("PlannedResult")) {
            plannedResult = ItemStack.parse(registries, tag.getCompound("PlannedResult")).orElse(ItemStack.EMPTY);
        } else {
            plannedResult = ItemStack.EMPTY;
        }

        outputBuffer.clear();
        ListTag outList = tag.getList("OutputBuffer", Tag.TAG_COMPOUND);
        for (int i = 0; i < outList.size(); i++) {
            ItemStack.parse(registries, outList.getCompound(i)).ifPresent(outputBuffer::add);
        }
        lastSentBufferedCount = totalBufferedItems();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("WaterPowered", waterPowered);
        tag.putInt("BufferedCount", totalBufferedItems());
        tag.putBoolean("Working", getBlockState().hasProperty(WaterProcessingBlock.WORKING) && getBlockState().getValue(WaterProcessingBlock.WORKING));
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
