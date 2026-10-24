package com.holeydigsite.shop;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contents of a Sell Pad or Buy Pad: up to {@value #SLOTS} stacks lying on top (3x3). No ticking, no item handler -
 * only players (click, sneak-click, thrown items) put things on it and only the Shop Button or an empty hand empty it.
 * The stacks are synced to clients for the renderer and the floating price text.
 */
public class PadBlockEntity extends BlockEntity {
    public static final int SLOTS = 9;

    private final List<ItemStack> items = new ArrayList<>(SLOTS);

    public PadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOP_PAD.get(), pos, state);
    }

    /** The stacks on the pad (read-only view). */
    public List<ItemStack> contents() {
        return Collections.unmodifiableList(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** How many of {@code stack} would fit (merging into matching stacks first, then free slots). */
    public int roomFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int room = 0;
        for (ItemStack s : items) {
            if (ItemStack.isSameItemSameComponents(s, stack)) {
                room += Math.max(0, s.getMaxStackSize() - s.getCount());
            }
        }
        room += (SLOTS - items.size()) * stack.getMaxStackSize();
        return Math.min(room, stack.getCount());
    }

    /**
     * Puts as much of {@code stack} on the pad as fits and SHRINKS {@code stack} by that much. Returns how many went on.
     */
    public int insert(ItemStack stack) {
        int moved = 0;
        for (ItemStack s : items) {
            if (stack.isEmpty()) {
                break;
            }
            if (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n);
                stack.shrink(n);
                moved += n;
            }
        }
        while (!stack.isEmpty() && items.size() < SLOTS) {
            int n = Math.min(stack.getCount(), stack.getMaxStackSize());
            items.add(stack.split(n));
            moved += n;
        }
        if (moved > 0) {
            changed();
        }
        return moved;
    }

    /** Takes everything off the pad. */
    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>(items);
        if (!items.isEmpty()) {
            items.clear();
            changed();
        }
        return out;
    }

    /** Removes the given stacks (by identity) from the pad - used by the Shop Button after selling. */
    public void removeAll(List<ItemStack> sold) {
        if (items.removeIf(s -> sold.stream().anyMatch(x -> x == s))) {
            changed();
        }
    }

    /** Saves and sends the contents to clients (only on real changes). */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeItems(tag, registries);
    }

    private void writeItems(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                list.add(s.save(registries));
            }
        }
        tag.put("Items", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        for (Tag t : tag.getList("Items", Tag.TAG_COMPOUND)) {
            if (items.size() < SLOTS) {
                ItemStack.parse(registries, t).ifPresent(items::add);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeItems(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
