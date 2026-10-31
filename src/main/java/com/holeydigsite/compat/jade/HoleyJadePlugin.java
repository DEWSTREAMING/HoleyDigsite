package com.holeydigsite.compat.jade;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.drill.MiningDrillBlockEntity;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinWear;
import com.holeydigsite.sluice.SluiceBoxBlock;
import com.holeydigsite.sluice.SluiceBoxBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Loaded by Jade only. Skins and wear are already synced to the client for rendering, so no server data is needed. */
@WailaPlugin
public class HoleyJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SkinLine.INSTANCE, MiningDrillBlock.class);
        registration.registerBlockComponent(SkinLine.INSTANCE, SluiceBoxBlock.class);
        registration.registerBlockComponent(CrateWaitingLine.INSTANCE, MiningDrillBlock.class);
        registration.registerBlockComponent(SwitchLine.INSTANCE, com.holeydigsite.crate.CrateSwitchBlock.class);
    }

    /** "Crate waiting" while a drill holds a crate find. */
    enum CrateWaitingLine implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(HoleyDigsite.MODID, "crate_waiting");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (be == null && accessor.getBlockState().hasProperty(MiningDrillBlock.HALF)
                    && accessor.getBlockState().getValue(MiningDrillBlock.HALF) == DoubleBlockHalf.UPPER) {
                be = accessor.getLevel().getBlockEntity(accessor.getPosition().below());
            }
            if (be instanceof MiningDrillBlockEntity drill && !drill.heldCrate().isEmpty()) {
                tooltip.add(Component.translatable("tooltip.holeydigsite.drill_crate_waiting").withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.translatable("tooltip.holeydigsite.drill_crate_hint").withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /** The viewer's own crate switch, from the Ledger data the client already has. */
    enum SwitchLine implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(HoleyDigsite.MODID, "crate_switch");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            boolean on = com.holeydigsite.ledger.LedgerData.get(accessor.getPlayer()).cratesOn();
            tooltip.add(Component.translatable(on ? "message.holeydigsite.crate_switch.on" : "message.holeydigsite.crate_switch.off")
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum SkinLine implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(HoleyDigsite.MODID, "skin");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            // the drill keeps its block entity in the lower half
            if (be == null && accessor.getBlockState().hasProperty(MiningDrillBlock.HALF)
                    && accessor.getBlockState().getValue(MiningDrillBlock.HALF) == DoubleBlockHalf.UPPER) {
                be = accessor.getLevel().getBlockEntity(accessor.getPosition().below());
            }
            if (be == null && accessor.getBlockState().getBlock() instanceof SluiceBoxBlock sluiceBlock) {
                be = accessor.getLevel().getBlockEntity(sluiceBlock.entityPos(accessor.getPosition(), accessor.getBlockState()));
            }
            String skin;
            String wear;
            if (be instanceof MiningDrillBlockEntity drill) {
                skin = drill.getSkin();
                wear = drill.getWear();
            } else if (be instanceof SluiceBoxBlockEntity sluice) {
                skin = sluice.getSkin();
                wear = sluice.getWear();
            } else {
                return;
            }
            if (skin == null || skin.isEmpty() || SkinDefs.DEFAULT_ID.equals(skin)) return;
            tooltip.add(Component.translatable("tooltip.holeydigsite.skin", SkinDefs.displayName(skin)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.holeydigsite.skin_wear", SkinWear.displayName(wear)).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
