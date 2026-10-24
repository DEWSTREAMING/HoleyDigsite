package com.holeydigsite.ledger;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Core server-side logic for the Prospector's Ledger, handling milestones, ongoing Renown gains, level-up notifications, claiming rewards, and starter item distribution.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class LedgerManager {
    private LedgerManager() {}

    private static final ResourceLocation SILVANE = HoleyDigsite.id("silvane");

    // Events

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        LedgerDefs.Milestone m = LedgerDefs.byAdvancement(event.getAdvancement().id());
        if (m != null) {
            grantMilestone(sp, m.id());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        LedgerData data = LedgerData.get(sp);
        if (!data.starterGiven()) {
            data.setStarterGiven(true);
            data.setStarterBedGiven(true);
            giveOrDrop(sp, new ItemStack(ModItems.PROSPECTORS_LEDGER.get()));
        } else if (!data.starterBedGiven()) {
            checkStarterBed(sp, data);
        }
        sp.refreshDisplayName();
        LedgerNetwork.syncToClient(sp);
    }

    public static boolean hasBed(ServerPlayer player) {
        for (ItemStack st : player.getInventory().items) {
            if (!st.isEmpty() && st.is(ItemTags.BEDS)) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkStarterBed(ServerPlayer sp, LedgerData data) {
        if (data.starterBedGiven()) {
            return false;
        }
        data.setStarterBedGiven(true);
        if (hasBed(sp)) {
            return false;
        }
        giveOrDrop(sp, new ItemStack(Items.RED_BED));
        return true;
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            LedgerNetwork.syncToClient(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            LedgerNetwork.syncToClient(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        LedgerData oldData = LedgerData.get(event.getOriginal());
        LedgerData newData = LedgerData.get(event.getEntity());
        newData.copyFrom(oldData);
        event.getEntity().refreshDisplayName();
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (event.getSlot() == EquipmentSlot.HEAD && event.getTo().has(ModDataComponents.SMITH.get())) {
            ModTriggers.WEARING.get().trigger(sp, "head");
        }
    }

    // Milestones

    /**
     * Marks {@code milestoneId} completed for {@code sp} and awards its Renown once.
     * Returns true if the milestone was newly completed.
     */
    public static boolean grantMilestone(ServerPlayer sp, ResourceLocation milestoneId) {
        LedgerDefs.Milestone m = LedgerDefs.milestone(milestoneId);
        if (m == null) return false;
        LedgerData data = LedgerData.get(sp);
        if (!data.markMilestoneCompleted(milestoneId)) {
            return false;
        }
        int oldLevel = data.level();
        data.addRenown(m.renown());
        int newLevel = data.level();

        MinecraftServer server = sp.getServer();
        if (server != null) {
            AdvancementHolder holder = server.getAdvancements().get(m.advancement());
            if (holder != null) {
                AdvancementProgress prog = sp.getAdvancements().getOrStartProgress(holder);
                if (!prog.isDone()) {
                    for (String crit : prog.getRemainingCriteria()) {
                        sp.getAdvancements().award(holder, crit);
                    }
                }
            }
        }

        if (newLevel > oldLevel) {
            notifyLevelUp(sp, newLevel);
        }
        LedgerNetwork.syncToClient(sp);
        return true;
    }

    /**
     * Revokes {@code milestoneId} from {@code sp}, removes its Renown, and revokes its hidden advancement.
     * Returns true if the milestone was revoked.
     */
    public static boolean revokeMilestone(ServerPlayer sp, ResourceLocation milestoneId) {
        LedgerDefs.Milestone m = LedgerDefs.milestone(milestoneId);
        if (m == null) return false;
        LedgerData data = LedgerData.get(sp);
        if (!data.completedMilestones().remove(milestoneId)) {
            return false;
        }
        data.setRenown(Math.max(0, data.renown() - m.renown()));

        MinecraftServer server = sp.getServer();
        if (server != null) {
            AdvancementHolder holder = server.getAdvancements().get(m.advancement());
            if (holder != null) {
                AdvancementProgress prog = sp.getAdvancements().getOrStartProgress(holder);
                if (prog.hasProgress()) {
                    for (String crit : prog.getCompletedCriteria()) {
                        sp.getAdvancements().revoke(holder, crit);
                    }
                }
            }
        }

        LedgerNetwork.syncToClient(sp);
        return true;
    }

    /**
     * Completely wipes all Ledger data and revokes all milestone advancements for {@code sp}.
     */
    public static void resetPlayer(ServerPlayer sp) {
        LedgerData data = LedgerData.get(sp);
        MinecraftServer server = sp.getServer();
        if (server != null) {
            for (LedgerDefs.Milestone m : LedgerDefs.milestones().values()) {
                AdvancementHolder holder = server.getAdvancements().get(m.advancement());
                if (holder != null) {
                    AdvancementProgress prog = sp.getAdvancements().getOrStartProgress(holder);
                    if (prog.hasProgress()) {
                        for (String crit : prog.getCompletedCriteria()) {
                            sp.getAdvancements().revoke(holder, crit);
                        }
                    }
                }
            }
        }
        data.resetAll();
        sp.refreshDisplayName();
        LedgerNetwork.syncToClient(sp);
    }

    // Ongoing Renown and triggers

    public static void recordPan(ServerPlayer sp, int tier, int count) {
        ModTriggers.PANNED.get().trigger(sp, tier);
        LedgerData data = LedgerData.get(sp);
        int oldLevel = data.level();
        int gain = data.addPanProgress(count, LedgerDefs.curve().pan());
        if (gain > 0) {
            int newLevel = data.level();
            if (newLevel > oldLevel) notifyLevelUp(sp, newLevel);
            LedgerNetwork.syncToClient(sp);
        }
    }

    public static void recordSluice(ServerPlayer sp, int tier, int count) {
        ModTriggers.PANNED.get().trigger(sp, tier);
        LedgerData data = LedgerData.get(sp);
        int oldLevel = data.level();
        int gain = data.addSluiceProgress(count, LedgerDefs.curve().sluice());
        if (gain > 0) {
            int newLevel = data.level();
            if (newLevel > oldLevel) notifyLevelUp(sp, newLevel);
            LedgerNetwork.syncToClient(sp);
        }
    }

    public static void recordHarvest(ServerPlayer sp, ResourceLocation cropBlockId) {
        LedgerData data = LedgerData.get(sp);
        int kinds = data.recordCropHarvested(cropBlockId);
        ModTriggers.HARVESTED.get().trigger(sp, cropBlockId, kinds);
        int oldLevel = data.level();
        int gain = data.addHarvestProgress(1, LedgerDefs.curve().harvest());
        if (gain > 0) {
            int newLevel = data.level();
            if (newLevel > oldLevel) notifyLevelUp(sp, newLevel);
        }
        LedgerNetwork.syncToClient(sp);
    }

    public static void recordSmith(ServerPlayer sp, String piece, ItemStack created) {
        SmithData sd = SmithPieces.data(created);
        boolean mixed = false;
        boolean withGem = false;
        boolean netheriteTier = false;
        if (sd != null) {
            Set<ResourceLocation> metals = new HashSet<>();
            for (SmithData.Ingot ing : sd.ingots()) {
                metals.add(ing.metal());
            }
            mixed = metals.size() >= 2;
            withGem = sd.gem().isPresent();
            netheriteTier = SILVANE.equals(sd.mainMetal()) || created.getMaxDamage() >= 1800;
        }
        ModTriggers.SMITHED.get().trigger(sp, piece, mixed, withGem, netheriteTier);
        LedgerData data = LedgerData.get(sp);
        int oldLevel = data.level();
        int gain = data.addSmithProgress(1, LedgerDefs.curve().smith());
        if (gain > 0) {
            int newLevel = data.level();
            if (newLevel > oldLevel) notifyLevelUp(sp, newLevel);
        }
        LedgerNetwork.syncToClient(sp);
    }

    public static void recordSale(ServerPlayer sp, long totalCopperSold, long maxSingleItemValue) {
        if (totalCopperSold <= 0) return;
        LedgerData data = LedgerData.get(sp);
        int oldLevel = data.level();
        data.addLifetimeCopper(totalCopperSold);
        ModTriggers.SOLD.get().trigger(sp, maxSingleItemValue);
        ModTriggers.EARNED_TOTAL.get().trigger(sp, data.lifetimeCopper());
        int gain = data.addSellProgress(totalCopperSold, LedgerDefs.curve().sell());
        if (gain > 0) {
            int newLevel = data.level();
            if (newLevel > oldLevel) notifyLevelUp(sp, newLevel);
        }
        LedgerNetwork.syncToClient(sp);
    }

    public static void notifyLevelUp(ServerPlayer sp, int newLevel) {
        sp.displayClientMessage(
                Component.translatable("message.holeydigsite.ledger.level_up", newLevel).withStyle(ChatFormatting.GOLD),
                true
        );
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    // Claiming Rewards

    /**
     * Checks if a level can be claimed, ensuring all reward items are registered, blueprints have catalogue entries, and titles exist.
     */
    public static boolean isLevelReady(LedgerDefs.LevelDef def, HolderLookup.Provider registries) {
        if (def == null) return false;
        for (LedgerDefs.RewardEntry r : def.rewards()) {
            if (!isRewardAvailable(r, registries)) return false;
        }
        return def.title().isEmpty() || LedgerDefs.hasTitle(def.title().get());
    }

    public static boolean claim(ServerPlayer sp, int level) {
        if (level < 1 || level > LedgerDefs.MAX_LEVEL) return false;
        LedgerData data = LedgerData.get(sp);
        if (data.level() < level || data.isLevelClaimed(level)) {
            return false;
        }
        LedgerDefs.LevelDef def = LedgerDefs.level(level);
        HolderLookup.Provider registries = sp.registryAccess();
        if (!isLevelReady(def, registries)) return false;

        data.markLevelClaimed(level);
        int ingotGrams = (int) Math.round(LedgerDefs.curve().rewardIngotGrams());

        for (LedgerDefs.RewardEntry r : def.rewards()) {
            if (r.item().isPresent()) {
                Item item = BuiltInRegistries.ITEM.get(r.item().get());
                ItemStack template = new ItemStack(item);
                r.clumpTier().ifPresent(t -> template.set(ModDataComponents.CLUMP_TIER.get(), t));
                if (item instanceof IngotItem) {
                    template.set(ModDataComponents.WEIGHT.get(), ingotGrams);
                }
                giveCountOrDrop(sp, template, r.count());
            } else if (r.blueprint().isPresent()) {
                ItemStack bp = BlueprintItem.of(BuiltInRegistries.ITEM.get(r.blueprint().get()));
                giveCountOrDrop(sp, bp, r.count());
            }
        }

        if (def.coins().isPresent()) {
            int copper = LedgerDefs.curve().coinCopper(def.coins().get());
            if (copper > 0) {
                for (ItemStack coinStack : Selling.coins(copper)) {
                    giveOrDrop(sp, coinStack);
                }
            }
        }

        if (def.title().isPresent() && data.unlockTitle(def.title().get())) {
            sp.displayClientMessage(Component.translatable("message.holeydigsite.ledger.title_unlocked",
                    Component.translatable(LedgerDefs.titleKey(def.title().get()))).withStyle(ChatFormatting.GOLD), true);
        }

        sp.level().playSound(null, sp.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.1F);
        LedgerNetwork.syncToClient(sp);
        return true;
    }

    // Titles

    /** Picks one of the player's unlocked titles, or none ({@code ""}). Returns false if the title is not unlocked. */
    public static boolean selectTitle(ServerPlayer sp, String id) {
        LedgerData data = LedgerData.get(sp);
        String wanted = id == null ? "" : id;
        if (!wanted.isEmpty() && !data.hasTitle(wanted)) return false;
        data.setChosenTitle(wanted);
        sp.refreshDisplayName();
        LedgerNetwork.syncToClient(sp);
        return true;
    }

    /** The chat/name prefix {@code [Title] } for a title id (empty for none). */
    public static Component titlePrefix(String id) {
        if (id == null || id.isEmpty()) return Component.empty();
        return Component.literal("[").append(Component.translatable(LedgerDefs.titleKey(id))).append("] ")
                .withStyle(ChatFormatting.GOLD);
    }

    /** Shows the chosen title before the player's name in chat, death messages, and name tags. */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        String title = LedgerData.get(event.getEntity()).activeTitle();
        if (!title.isEmpty()) {
            event.setDisplayname(Component.empty().append(titlePrefix(title)).append(event.getDisplayname()));
        }
    }
    public static int claimAll(ServerPlayer sp) {
        LedgerData data = LedgerData.get(sp);
        int max = data.level();
        int claimed = 0;
        for (int lvl = 1; lvl <= max; lvl++) {
            if (!data.isLevelClaimed(lvl) && claim(sp, lvl)) {
                claimed++;
            }
        }
        return claimed;
    }

    public static void giveCountOrDrop(ServerPlayer sp, ItemStack template, int totalCount) {
        int remaining = totalCount;
        int maxStack = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            int c = Math.min(remaining, maxStack);
            giveOrDrop(sp, template.copyWithCount(c));
            remaining -= c;
        }
    }

    public static void giveOrDrop(ServerPlayer sp, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack copy = stack.copy();
        sp.getInventory().add(copy);
        if (!copy.isEmpty()) {
            ItemEntity dropped = sp.drop(copy, false);
            if (dropped != null) {
                dropped.setNoPickUpDelay();
            }
        }
    }

    /** Returns true if a reward entry can be given now, false if its item is not registered or its blueprint has no catalogue entry. */
    public static boolean isRewardAvailable(LedgerDefs.RewardEntry r, HolderLookup.Provider registries) {
        if (r.item().isPresent()) {
            Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(r.item().get());
            return opt.isPresent() && opt.get() != Items.AIR;
        }
        if (r.blueprint().isPresent()) {
            Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(r.blueprint().get());
            return opt.isPresent() && opt.get() != Items.AIR && ShopCatalogue.find(registries, opt.get()).isPresent();
        }
        return false;
    }

    // Old-world data checks

    public static void checkOldWorld(ServerPlayer sp) {
        LedgerData data = LedgerData.get(sp);
        MinecraftServer server = sp.getServer();
        if (server != null) {
            for (LedgerDefs.Milestone m : LedgerDefs.milestones().values()) {
                AdvancementHolder h = server.getAdvancements().get(m.advancement());
                if (h != null && sp.getAdvancements().getOrStartProgress(h).isDone()) {
                    grantMilestone(sp, m.id());
                }
            }
        }
        if (data.oldWorldChecked()) {
            LedgerNetwork.syncToClient(sp);
            return;
        }
        data.setOldWorldChecked(true);

        Set<String> ownedPlots = new HashSet<>();
        for (PlotData.Plot p : PlotData.get(sp.serverLevel()).all()) {
            if (p.owned()) {
                ownedPlots.add(p.id());
            }
        }
        if (ownedPlots.contains("deep_dig")) grantMilestone(sp, HoleyDigsite.id("deep_dig"));
        if (ownedPlots.contains("gravel_flats")) grantMilestone(sp, HoleyDigsite.id("gravel_flats"));
        if (ownedPlots.contains("sand_dunes")) grantMilestone(sp, HoleyDigsite.id("sand_dunes"));
        if (ownedPlots.size() >= 3) grantMilestone(sp, HoleyDigsite.id("all_plots"));

        long copper = data.lifetimeCopper();
        if (copper >= 1) grantMilestone(sp, HoleyDigsite.id("first_sale"));
        if (copper >= 10_000) grantMilestone(sp, HoleyDigsite.id("earned_10k"));
        if (copper >= 1_000_000) grantMilestone(sp, HoleyDigsite.id("earned_1m"));

        Set<String> cropsFound = new HashSet<>();
        Set<Item> smithedKinds = new HashSet<>();

        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            Item item = s.getItem();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String path = id.getPath();

            if (s.is(ModItems.BLUEPRINT.get()) && BlueprintItem.target(s) != null) {
                grantMilestone(sp, HoleyDigsite.id("first_blueprint"));
            }
            if (s.is(ModItems.WATER_INTAKE.get())) grantMilestone(sp, HoleyDigsite.id("water_intake"));
            if (s.is(ModItems.MINING_DRILL.get())) grantMilestone(sp, HoleyDigsite.id("first_drill"));
            if (s.is(ModItems.CLUMP_BELT.get())) grantMilestone(sp, HoleyDigsite.id("first_belt"));
            if (s.is(ModItems.SLUICE_BOX.get())) grantMilestone(sp, HoleyDigsite.id("first_sluice"));
            if (s.is(ModItems.FILTER_BELT.get())) grantMilestone(sp, HoleyDigsite.id("filter_belt"));
            if (s.is(ModItems.BELT_SPLITTER.get())) grantMilestone(sp, HoleyDigsite.id("belt_splitter"));
            if (s.is(ModItems.FORGE.get())) grantMilestone(sp, HoleyDigsite.id("lava_forge"));
            if (s.is(ModItems.MINING_DRILL_T2.get())) grantMilestone(sp, HoleyDigsite.id("tier2_machine"));
            if (s.is(ModItems.LARGE_CRUCIBLE.get())) grantMilestone(sp, HoleyDigsite.id("large_crucible"));
            if (s.is(ModItems.RIFFLE_INSERT.get()) || s.is(ModItems.FINE_MESH_SCREEN.get())) {
                grantMilestone(sp, HoleyDigsite.id("plot_upgrade"));
            }
            if (s.is(ModItems.SEED_SCREEN.get())) grantMilestone(sp, HoleyDigsite.id("seed_screen"));
            if (s.is(ModItems.SMITHING_ANVIL.get())) grantMilestone(sp, HoleyDigsite.id("buy_anvil"));

            if (item instanceof IngotItem) {
                grantMilestone(sp, HoleyDigsite.id("first_ingot"));
                if (s.is(ModItems.GOLDCHRO_INGOT.get())) {
                    grantMilestone(sp, HoleyDigsite.id("goldchro_ingot"));
                }
            }

            if (path.startsWith("uncut_ruby_") || path.startsWith("uncut_sapphire_") || path.startsWith("uncut_topaz_")) {
                grantMilestone(sp, HoleyDigsite.id("first_gem"));
            }
            if (path.startsWith("ruby_") || path.startsWith("sapphire_") || path.startsWith("topaz_")) {
                grantMilestone(sp, HoleyDigsite.id("first_gem"));
                grantMilestone(sp, HoleyDigsite.id("cut_gem"));
                if (path.endsWith("_large")) {
                    grantMilestone(sp, HoleyDigsite.id("large_gem"));
                }
            }

            if (s.is(ModItems.GLOWROOT.get()) || s.is(ModItems.ROASTED_GLOWROOT.get())) {
                grantMilestone(sp, HoleyDigsite.id("first_harvest"));
                cropsFound.add("glowroot");
            }
            if (s.is(ModItems.RIVERBEAN.get()) || s.is(ModItems.RIVERBEAN_STEW.get())) {
                grantMilestone(sp, HoleyDigsite.id("first_harvest"));
                cropsFound.add("riverbean");
            }
            if (s.is(ModItems.DUNE_MELON_SLICE.get()) || s.is(ModItems.DUNE_MELON_JUICE.get())) {
                grantMilestone(sp, HoleyDigsite.id("first_harvest"));
                cropsFound.add("dune_melon");
            }

            SmithData sd = SmithPieces.data(s);
            if (sd != null) {
                grantMilestone(sp, HoleyDigsite.id("first_piece"));
                smithedKinds.add(item);
                Set<ResourceLocation> metals = new HashSet<>();
                for (SmithData.Ingot ing : sd.ingots()) metals.add(ing.metal());
                if (metals.size() >= 2) {
                    grantMilestone(sp, HoleyDigsite.id("mixed_piece"));
                }
                if (s.is(ModItems.SMITH_NECKLACE.get()) && sd.gem().isPresent()) {
                    grantMilestone(sp, HoleyDigsite.id("gem_necklace"));
                }
                if (s.is(ModItems.SMITH_SWORD.get()) && (SILVANE.equals(sd.mainMetal()) || s.getMaxDamage() >= 1800)) {
                    grantMilestone(sp, HoleyDigsite.id("netherite_sword"));
                }
            }
        }

        if (cropsFound.size() >= 3 || data.harvestedCrops().size() >= 3) {
            grantMilestone(sp, HoleyDigsite.id("all_crops"));
        }
        if (smithedKinds.size() >= 5) grantMilestone(sp, HoleyDigsite.id("every_piece"));
        if (sp.getItemBySlot(EquipmentSlot.HEAD).has(ModDataComponents.SMITH.get())) {
            grantMilestone(sp, HoleyDigsite.id("wear_jewellery"));
        }

        LedgerNetwork.syncToClient(sp);
    }
}
