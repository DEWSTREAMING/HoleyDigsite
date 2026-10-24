package com.holeydigsite.shop;

import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The shop's money: a balance in copper (a number, not item slots, no cap). Deposits come from coins and Sell Pad sales,
 * withdrawals from purchases and an empty-hand click. The coin pile look is the block state {@link MoneyPanBlock#PILE},
 * updated here whenever the balance changes; the balance itself is synced for the floating text.
 */
public class MoneyPanBlockEntity extends BlockEntity {
    private long balance;

    public MoneyPanBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONEY_PAN.get(), pos, state);
    }

    public long balance() {
        return balance;
    }

    public void deposit(long copper) {
        if (copper > 0) {
            setBalance(balance + copper);
        }
    }

    /** Takes {@code copper} out if the balance covers it; returns false (and changes nothing) otherwise. */
    public boolean withdraw(long copper) {
        if (copper < 0 || copper > balance) {
            return false;
        }
        setBalance(balance - copper);
        return true;
    }

    /** Takes the whole balance out; returns it. */
    public long withdrawAll() {
        long all = balance;
        setBalance(0);
        return all;
    }

    public void setBalance(long copper) {
        long v = Math.max(0, copper);
        if (v == balance) {
            return;
        }
        balance = v;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            int pile = MoneyPanBlock.pileFor(balance);
            if (state.hasProperty(MoneyPanBlock.PILE) && state.getValue(MoneyPanBlock.PILE) != pile) {
                level.setBlock(worldPosition, state.setValue(MoneyPanBlock.PILE, pile), Block.UPDATE_ALL);
            } else {
                level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Balance", balance);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        balance = Math.max(0, tag.getLong("Balance"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Balance", balance);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
