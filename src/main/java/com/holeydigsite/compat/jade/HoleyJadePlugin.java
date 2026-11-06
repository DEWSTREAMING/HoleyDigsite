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
        registration.registerBlockComponent(SprinklerLine.INSTANCE, com.holeydigsite.power.SprinklerBlock.class);
        registration.registerBlockComponent(FloodGateLine.INSTANCE, com.holeydigsite.power.FloodGateBlock.class);
        registration.registerBlockComponent(MachineLine.INSTANCE, com.holeydigsite.machine.WaterProcessingBlock.class);
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

    enum SprinklerLine implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(HoleyDigsite.MODID, "sprinkler_status");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlock() instanceof com.holeydigsite.power.SprinklerBlock) {
                boolean powered = accessor.getBlockState().hasProperty(com.holeydigsite.power.SprinklerBlock.WATER_POWERED)
                        && accessor.getBlockState().getValue(com.holeydigsite.power.SprinklerBlock.WATER_POWERED);
                if (!powered) {
                    tooltip.add(Component.translatable("message.holeydigsite.sprinkler.no_water").withStyle(ChatFormatting.RED));
                } else {
                    tooltip.add(Component.translatable("message.holeydigsite.sprinkler.working").withStyle(ChatFormatting.AQUA));
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    
    enum MachineLine implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(HoleyDigsite.MODID, "machine_status");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlock() instanceof com.holeydigsite.machine.WaterProcessingBlock) {
                boolean powered = accessor.getBlockState().hasProperty(com.holeydigsite.machine.WaterProcessingBlock.WATER_POWERED)
                        && accessor.getBlockState().getValue(com.holeydigsite.machine.WaterProcessingBlock.WATER_POWERED);
                boolean working = accessor.getBlockState().hasProperty(com.holeydigsite.machine.WaterProcessingBlock.WORKING)
                        && accessor.getBlockState().getValue(com.holeydigsite.machine.WaterProcessingBlock.WORKING);

                if (!powered) {
                    tooltip.add(Component.translatable("message.holeydigsite.machine.no_water").withStyle(ChatFormatting.RED));
                } else if (accessor.getBlockEntity() instanceof com.holeydigsite.machine.WaterProcessingBlockEntity be && be.isBufferFull()) {
                    tooltip.add(Component.translatable("message.holeydigsite.machine.buffer_full").withStyle(ChatFormatting.YELLOW));
                } else if (working) {
                    tooltip.add(Component.translatable("message.holeydigsite.machine.working").withStyle(ChatFormatting.AQUA));
                } else {
                    tooltip.add(Component.translatable("message.holeydigsite.machine.ready").withStyle(ChatFormatting.GREEN));
                }

                if (accessor.getBlock() instanceof com.holeydigsite.machine.SawmillBlock) {
                    com.holeydigsite.machine.SawmillBlock.Mode mode = accessor.getBlockState().getValue(com.holeydigsite.machine.SawmillBlock.MODE);
                    String key = mode == com.holeydigsite.machine.SawmillBlock.Mode.BEAM ? "message.holeydigsite.sawmill.mode_beam" : "message.holeydigsite.sawmill.mode_pit_prop";
                    tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GOLD));
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum FloodGateLine implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(HoleyDigsite.MODID, "flood_gate_status");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlock() instanceof com.holeydigsite.power.FloodGateBlock) {
                boolean powered = accessor.getBlockState().hasProperty(com.holeydigsite.power.FloodGateBlock.WATER_POWERED)
                        && accessor.getBlockState().getValue(com.holeydigsite.power.FloodGateBlock.WATER_POWERED);
                boolean washing = accessor.getBlockState().hasProperty(com.holeydigsite.power.FloodGateBlock.WASHING)
                        && accessor.getBlockState().getValue(com.holeydigsite.power.FloodGateBlock.WASHING);
                if (!powered) {
                    tooltip.add(Component.translatable("message.holeydigsite.flood_gate.no_water").withStyle(ChatFormatting.RED));
                } else if (washing) {
                    tooltip.add(Component.translatable("message.holeydigsite.flood_gate.washing").withStyle(ChatFormatting.AQUA));
                } else {
                    tooltip.add(Component.translatable("message.holeydigsite.flood_gate.ready").withStyle(ChatFormatting.GREEN));
                }
            }
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
