package com.holeydigsite.registry;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.ShopBlockItem;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.shovel.ShovelHelper;
import com.holeydigsite.tier.DirtTier;
import com.holeydigsite.tier.TierDef;
import com.holeydigsite.tier.TierFamily;
import com.holeydigsite.tier.Tiers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The Holey Digsite creative tab. */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    /** A group of creative tab entries and where it sorts. Tier slots come from the tier files. */
    private record Slot(int order, Runnable add) {}

    /** The stack a tier tab slot shows, either the item itself or a full pan of the slot's dirt tier. */
    private static ItemStack tabStack(TierDef def, TierDef.TabSlot slot) {
        if (slot.stack().equals("full_pan")) {
            PanBlock pan = (PanBlock) Tiers.<net.minecraft.world.level.block.Block>block(def.id()).get();
            return pan.createStack(pan.maxFill(), slot.dirtTier());
        }
        return Tiers.<net.minecraft.world.item.Item>item(def.id()).get().getDefaultInstance();
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HoleyDigsite.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + HoleyDigsite.MODID))
                    .icon(() -> ModItems.PAN.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Tiered items come from data/holeydigsite/tiers/*.json with a sort slot each. The hand-written groups below sit between them.
                        List<Slot> slots = new ArrayList<>();
                        slots.add(new Slot(100, () -> {
                            // Tools of the trade including a vanilla wooden shovel for convenience
                            output.accept(Items.WOODEN_SHOVEL);
                            output.accept(ModItems.PROSPECTORS_LEDGER.get());
                            output.accept(ShovelHelper.createFullShovel());
                        }));
                        slots.add(new Slot(200, () -> {
                            // Coins
                            output.accept(ModItems.COPPER_COIN.get());
                            output.accept(ModItems.SILVER_COIN.get());
                            output.accept(ModItems.GOLD_COIN.get());
                            output.accept(ModItems.PLATINUM_COIN.get());
                            output.accept(ModItems.RUBY_CROWN.get());
                            output.accept(ModItems.MINT_BAR.get());
                            // Drilling and water power
                            output.accept(ModItems.DIRT_CLUMP.get());
                        }));
                        slots.add(new Slot(220, () -> {
                            output.accept(ModItems.WATER_INTAKE.get());
                            output.accept(ModItems.WATER_PIPE.get());
                            output.accept(ModItems.DIRTROCK.get());
                        }));
                        slots.add(new Slot(300, () -> {
                            // Rock tiers, tiered clumps and pans, resources, selling
                            output.accept(ModItems.DEEP_DIRTROCK.get());
                            output.accept(ModItems.ABYSSAL_DIRTROCK.get());
                            output.accept(DirtTier.clumps(2, 1));
                            output.accept(DirtTier.clumps(3, 1));
                            output.accept(DirtTier.clumps(DirtTier.COARSE_DIRT, 1));
                            output.accept(DirtTier.clumps(DirtTier.ROOTED_DIRT, 1));
                            output.accept(DirtTier.clumps(DirtTier.MOSS, 1));
                        }));
                        slots.add(new Slot(320, () -> {
                            output.accept(ModItems.FOLKIRON_CHUNK_SMALL.get());
                            output.accept(ModItems.FOLKIRON_CHUNK_MEDIUM.get());
                            output.accept(ModItems.FOLKIRON_CHUNK_LARGE.get());
                            output.accept(ModItems.GOLDCHRO_CHUNK_SMALL.get());
                            output.accept(ModItems.GOLDCHRO_CHUNK_MEDIUM.get());
                            output.accept(ModItems.GOLDCHRO_CHUNK_LARGE.get());
                            output.accept(ModItems.RUBY_SMALL.get());
                            output.accept(ModItems.RUBY_MEDIUM.get());
                            output.accept(ModItems.RUBY_LARGE.get());
                            output.accept(ModItems.SELL_PAD.get());
                            // Crafted shop versions break normally. Locked ones are unbreakable island versions.
                            output.accept(ModItems.BUY_PAD.get());
                            output.accept(ModItems.MONEY_PAN.get());
                            output.accept(ModItems.SHOP_BUTTON.get());
                            output.accept(ModItems.SHOP_DISPLAY.get());
                            output.accept(ShopBlockItem.locked(ModBlocks.SELL_PAD.get()));
                            output.accept(ShopBlockItem.locked(ModBlocks.BUY_PAD.get()));
                            output.accept(ShopBlockItem.locked(ModBlocks.MONEY_PAN.get()));
                            output.accept(ShopBlockItem.locked(ModBlocks.SHOP_BUTTON.get()));
                            // Melting and casting
                            output.accept(ModItems.LAVA_INTAKE.get());
                            output.accept(ModItems.LAVA_PIPE.get());
                            output.accept(Items.LAVA_BUCKET);
                        }));
                        slots.add(new Slot(430, () -> {
                            for (int g : new int[]{10, 50, 200}) {
                                output.accept(com.holeydigsite.item.IngotItem.of(ModItems.FOLKIRON_INGOT.get(), g));
                            }
                            for (int g : new int[]{10, 50, 200}) {
                                output.accept(com.holeydigsite.item.IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), g));
                            }
                        }));
                        slots.add(new Slot(500, () -> {
                            // Crafting Forge and Tier 2
                            output.accept(ModItems.CRAFTING_FORGE.get());
                        }));
                        slots.add(new Slot(600, () -> {
                            // New rocks, clumps, resources, land plots, Tier 3
                            output.accept(ModItems.GRAVELROCK.get());
                            output.accept(ModItems.SANDROCK.get());
                            output.accept(ModItems.GRAVEL_CLUMP.get());
                            output.accept(ModItems.SAND_CLUMP.get());
                        }));
                        slots.add(new Slot(620, () -> {
                            for (var it : new net.neoforged.neoforge.registries.DeferredItem<?>[]{ModItems.ORCHREMIA_CHUNK_SMALL, ModItems.ORCHREMIA_CHUNK_MEDIUM,
                                    ModItems.ORCHREMIA_CHUNK_LARGE, ModItems.SILVANE_CHUNK_SMALL, ModItems.SILVANE_CHUNK_MEDIUM, ModItems.SILVANE_CHUNK_LARGE,
                                    ModItems.SAPPHIRE_SMALL, ModItems.SAPPHIRE_MEDIUM, ModItems.SAPPHIRE_LARGE, ModItems.TOPAZ_SMALL, ModItems.TOPAZ_MEDIUM,
                                    ModItems.TOPAZ_LARGE}) {
                                output.accept(it.get());
                            }
                            for (int g : new int[]{10, 50, 200}) {
                                output.accept(com.holeydigsite.item.IngotItem.of(ModItems.ORCHREMIA_INGOT.get(), g));
                            }
                            for (int g : new int[]{10, 50, 200}) {
                                output.accept(com.holeydigsite.item.IngotItem.of(ModItems.SILVANE_INGOT.get(), g));
                            }
                            output.accept(ModItems.LAND_DEED_DEEP_DIG.get());
                            output.accept(ModItems.LAND_DEED_GRAVEL_FLATS.get());
                            output.accept(ModItems.LAND_DEED_SAND_DUNES.get());
                            output.accept(ModItems.PLOT_BOARD.get());
                            output.accept(ModItems.SITE_MARKER.get());
                        }));
                        slots.add(new Slot(730, () -> {
                            // Belt overhaul, uncut gems, Gem Cutter, Belt Splitter
                            output.accept(ModItems.FILTER_BELT.get());
                            output.accept(ModItems.GEM_CUTTER.get());
                            output.accept(ModItems.BELT_SPLITTER.get());
                        }));
                        slots.add(new Slot(760, () -> {
                            // Plot exclusive kiosk items
                            output.accept(ModItems.PROSPECTORS_PICK.get());
                            output.accept(ModItems.GRAVEL_HEAD.get());
                            output.accept(ModItems.SAND_HEAD.get());
                            output.accept(ModItems.ASSAYERS_PICK.get());
                            output.accept(ModItems.RIFFLE_INSERT.get());
                            output.accept(ModItems.FINE_MESH_SCREEN.get());
                            output.accept(ModItems.MINERS_LANTERN.get());
                            output.accept(ModItems.RIVER_STONE_LAMP.get());
                            output.accept(ModItems.SANDSTONE_LAMP.get());
                        }));
                        slots.add(new Slot(765, () -> {
                            // Seed Screen, seeds and foods
                            for (var it : new net.neoforged.neoforge.registries.DeferredItem<?>[]{ModItems.SEED_SCREEN, ModItems.GLOWROOT_SEEDS, ModItems.GLOWROOT,
                                    ModItems.ROASTED_GLOWROOT, ModItems.RIVERBEAN_SEEDS, ModItems.RIVERBEAN, ModItems.RIVERBEAN_STEW,
                                    ModItems.DUNE_MELON_SEEDS, ModItems.DUNE_MELON_SLICE, ModItems.DUNE_MELON_JUICE}) {
                                output.accept(it.get());
                            }
                        }));
                        slots.add(new Slot(767, () -> {
                            // Smithing Anvil, Blacksmith Hammer and example pieces whose recipes load with the world
                            output.accept(ModItems.SMITHING_ANVIL.get());
                            output.accept(ModItems.BLACKSMITH_HAMMER.get());
                            var reg = parameters.holders();
                            java.util.function.BiFunction<String, Integer, com.holeydigsite.smith.SmithData.Ingot> ing =
                                    (m, g) -> new com.holeydigsite.smith.SmithData.Ingot(HoleyDigsite.id(m), g);
                            var ruby = java.util.Optional.of(new com.holeydigsite.smith.SmithData.Gem(HoleyDigsite.id("ruby"), 2));
                            var sapphire = java.util.Optional.of(new com.holeydigsite.smith.SmithData.Gem(HoleyDigsite.id("sapphire"), 2));
                            var none = java.util.Optional.<com.holeydigsite.smith.SmithData.Gem>empty();
                            output.accept(com.holeydigsite.smith.SmithPieces.create(ModItems.SMITH_RING.get(),
                                    new com.holeydigsite.smith.SmithData(java.util.List.of(ing.apply("goldchro", 50)), ruby), reg));
                            output.accept(com.holeydigsite.smith.SmithPieces.create(ModItems.SMITH_NECKLACE.get(),
                                    new com.holeydigsite.smith.SmithData(java.util.List.of(ing.apply("silvane", 50), ing.apply("goldchro", 50)), sapphire), reg));
                            output.accept(com.holeydigsite.smith.SmithPieces.create(ModItems.SMITH_DAGGER.get(),
                                    new com.holeydigsite.smith.SmithData(java.util.List.of(ing.apply("goldchro", 200)), none), reg));
                            output.accept(com.holeydigsite.smith.SmithPieces.create(ModItems.SMITH_AXE.get(),
                                    new com.holeydigsite.smith.SmithData(java.util.List.of(ing.apply("orchremia", 150), ing.apply("orchremia", 150)), none), reg));
                            output.accept(com.holeydigsite.smith.SmithPieces.create(ModItems.SMITH_SWORD.get(),
                                    new com.holeydigsite.smith.SmithData(java.util.List.of(ing.apply("folkiron", 150), ing.apply("folkiron", 150), ing.apply("folkiron", 150)), none), reg));
                            output.accept(com.holeydigsite.smith.SmithPieces.create(ModItems.SMITH_SWORD.get(),
                                    new com.holeydigsite.smith.SmithData(java.util.List.of(ing.apply("silvane", 200), ing.apply("silvane", 200), ing.apply("goldchro", 200)), none), reg));
                        }));
                        slots.add(new Slot(768, () -> output.accept(ModItems.SKIN_BENCH.get())));
                        slots.add(new Slot(769, () -> output.accept(ModItems.CONTRACT_BOARD.get())));
                        slots.add(new Slot(770, () -> {
                            for (var it : new net.neoforged.neoforge.registries.DeferredItem<?>[]{ModItems.UNCUT_RUBY_SMALL, ModItems.UNCUT_RUBY_MEDIUM,
                                    ModItems.UNCUT_RUBY_LARGE, ModItems.UNCUT_SAPPHIRE_SMALL, ModItems.UNCUT_SAPPHIRE_MEDIUM, ModItems.UNCUT_SAPPHIRE_LARGE,
                                    ModItems.UNCUT_TOPAZ_SMALL, ModItems.UNCUT_TOPAZ_MEDIUM, ModItems.UNCUT_TOPAZ_LARGE}) {
                                output.accept(it.get());
                            }
                        }));
                        slots.add(new Slot(775, () -> {
                            // Ledger only decorative blocks
                            for (var it : new net.neoforged.neoforge.registries.DeferredItem<?>[]{ModItems.CLAIM_FLAG, ModItems.ORE_CART,
                                    ModItems.TOOL_RACK, ModItems.LUCKY_LANTERN, ModItems.GEM_DISPLAY_CASE, ModItems.GOLDEN_PAN_TROPHY}) {
                                output.accept(it.get());
                            }
                        }));
                        slots.add(new Slot(776, () -> {
                            for (var it : new net.neoforged.neoforge.registries.DeferredItem<?>[]{ModItems.WOODEN_CRATE, ModItems.IRON_CRATE,
                                    ModItems.GOLD_CRATE, ModItems.SKIN_SCRAP, ModItems.MINERS_HELMET_STAND, ModItems.RUSTY_MINE_BELL,
                                    ModItems.SLUICE_MODEL, ModItems.GOLDEN_DRILL_STATUE}) {
                                output.accept(it.get());
                            }
                        }));
                        slots.add(new Slot(777, () -> {
                            for (String skin : com.holeydigsite.skin.SkinDefs.skins().keySet()) {
                                for (String family : com.holeydigsite.skin.SkinDefs.familiesOf(skin)) {
                                    output.accept(com.holeydigsite.skin.SkinKitItem.of(skin, family, com.holeydigsite.skin.SkinWear.FRESH, 1));
                                }
                            }
                        }));
                        for (TierFamily family : Tiers.families()) {
                            for (TierDef def : family.tiers()) {
                                for (TierDef.TabSlot slot : def.tabSlots()) {
                                    slots.add(new Slot(slot.order(), () -> output.accept(tabStack(def, slot))));
                                }
                            }
                        }
                        slots.sort(Comparator.comparingInt(Slot::order));
                        slots.forEach(s -> s.add().run());
                        // one blueprint per catalogue entry
                        parameters.holders().lookup(ShopCatalogue.SHOP_ITEMS).ifPresent(r -> r.listElements()
                                .map(h -> h.value())
                                .sorted(Comparator.comparingLong(com.holeydigsite.shop.ShopItem::price))
                                .forEach(e -> output.accept(BlueprintItem.of(e.item()))));
                    })
                    .build());
}
