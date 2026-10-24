package com.holeydigsite.shop;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The item shown by a Shop Display and used for blueprints, synced to clients for the renderer. */
public class ShopDisplayBlockEntity extends BlockEntity {
    @Nullable
    private Item shown;
    /** The display slot number in a store, where 0 is none and a store layout file maps slots to items. */
    private int slot;
    /** The operator-made entry on a display without a catalogue item, synced for the renderer. */
    @Nullable
    private com.holeydigsite.shop.custom.CustomEntry custom;
    /** Client-side cache of the price label cleared when data changes. */
    @Nullable
    public Component clientLabel;

    public ShopDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOP_DISPLAY.get(), pos, state);
    }

    @Nullable
    public Item item() {
        return shown;
    }

    @Nullable
    public com.holeydigsite.shop.custom.CustomEntry custom() {
        return custom;
    }

    /** Sets or clears (null) the operator-made entry. */
    public void setCustom(@Nullable com.holeydigsite.shop.custom.CustomEntry entry) {
        this.custom = entry;
        this.clientLabel = null;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public int slot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = Math.max(0, slot);
        setChanged();
    }

    public void setItem(@Nullable Item item) {
        this.shown = item;
        this.clientLabel = null;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag, registries);
    }

    private void write(CompoundTag tag, HolderLookup.Provider registries) {
        if (slot > 0) {
            tag.putInt("Slot", slot);
        }
        if (shown != null) {
            tag.putString("Item", BuiltInRegistries.ITEM.getKey(shown).toString());
        }
        if (custom != null) {
            tag.put("Custom", custom.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shown = null;
        custom = null;
        clientLabel = null;
        slot = tag.getInt("Slot");
        if (tag.contains("Item")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Item"));
            if (id != null) {
                Item it = BuiltInRegistries.ITEM.get(id);
                if (it != Items.AIR) {
                    shown = it;
                }
            }
        }
        if (tag.contains("Custom")) {
            custom = com.holeydigsite.shop.custom.CustomEntry.load(registries, tag.getCompound("Custom"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
