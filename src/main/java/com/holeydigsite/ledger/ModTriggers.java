package com.holeydigsite.ledger;

import com.holeydigsite.HoleyDigsite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

/**
 * Phase 21a: Registers the 11 custom advancement triggers for the Prospector's Ledger milestones.
 */
public final class ModTriggers {
    private ModTriggers() {}

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, HoleyDigsite.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, PannedTrigger> PANNED =
            TRIGGERS.register("panned", PannedTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SoldTrigger> SOLD =
            TRIGGERS.register("sold", SoldTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, EarnedTotalTrigger> EARNED_TOTAL =
            TRIGGERS.register("earned_total", EarnedTotalTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, CastTrigger> CAST =
            TRIGGERS.register("cast", CastTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, CutGemTrigger> CUT_GEM =
            TRIGGERS.register("cut_gem", CutGemTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SmithedTrigger> SMITHED =
            TRIGGERS.register("smithed", SmithedTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, PlotBoughtTrigger> PLOT_BOUGHT =
            TRIGGERS.register("plot_bought", PlotBoughtTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, HarvestedTrigger> HARVESTED =
            TRIGGERS.register("harvested", HarvestedTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, UpgradeInstalledTrigger> UPGRADE_INSTALLED =
            TRIGGERS.register("upgrade_installed", UpgradeInstalledTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, LineRunningTrigger> LINE_RUNNING =
            TRIGGERS.register("line_running", LineRunningTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, WearingTrigger> WEARING =
            TRIGGERS.register("wearing", WearingTrigger::new);

    // 1. holeydigsite:panned
    public static final class PannedTrigger extends SimpleCriterionTrigger<PannedTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, int tier) {
            this.trigger(player, inst -> inst.tier.isEmpty() || inst.tier.get() == tier);
            LedgerManager.grantMilestone(player, HoleyDigsite.id("first_pan"));
            if (tier == 3) LedgerManager.grantMilestone(player, HoleyDigsite.id("abyssal_rock"));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<Integer> tier)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.INT.optionalFieldOf("tier").forGetter(Instance::tier)
            ).apply(i, Instance::new));
        }
    }

    // 2. holeydigsite:sold
    public static final class SoldTrigger extends SimpleCriterionTrigger<SoldTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, long maxSingleValue) {
            this.trigger(player, inst -> inst.minSingleValue.isEmpty() || maxSingleValue >= inst.minSingleValue.get());
            LedgerManager.grantMilestone(player, HoleyDigsite.id("first_sale"));
            if (maxSingleValue >= 1000) LedgerManager.grantMilestone(player, HoleyDigsite.id("big_sale"));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<Long> minSingleValue)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.LONG.optionalFieldOf("min_single_value").forGetter(Instance::minSingleValue)
            ).apply(i, Instance::new));
        }
    }

    // 3. holeydigsite:earned_total
    public static final class EarnedTotalTrigger extends SimpleCriterionTrigger<EarnedTotalTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, long lifetimeCopper) {
            this.trigger(player, inst -> lifetimeCopper >= inst.min);
            if (lifetimeCopper >= 10_000) LedgerManager.grantMilestone(player, HoleyDigsite.id("earned_10k"));
            if (lifetimeCopper >= 1_000_000) LedgerManager.grantMilestone(player, HoleyDigsite.id("earned_1m"));
        }

        public record Instance(Optional<ContextAwarePredicate> player, long min)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.LONG.fieldOf("min").forGetter(Instance::min)
            ).apply(i, Instance::new));
        }
    }

    // 4. holeydigsite:cast
    public static final class CastTrigger extends SimpleCriterionTrigger<CastTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, ResourceLocation metal) {
            this.trigger(player, inst -> inst.metal.isEmpty() || inst.metal.get().equals(metal));
            LedgerManager.grantMilestone(player, HoleyDigsite.id("first_ingot"));
            if (HoleyDigsite.id("goldchro").equals(metal)) LedgerManager.grantMilestone(player, HoleyDigsite.id("goldchro_ingot"));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> metal)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    ResourceLocation.CODEC.optionalFieldOf("metal").forGetter(Instance::metal)
            ).apply(i, Instance::new));
        }
    }

    // 5. holeydigsite:cut_gem
    public static final class CutGemTrigger extends SimpleCriterionTrigger<CutGemTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, String size) {
            this.trigger(player, inst -> inst.size.isEmpty() || inst.size.get().equals(size));
            LedgerManager.grantMilestone(player, HoleyDigsite.id("cut_gem"));
            if ("large".equals(size)) LedgerManager.grantMilestone(player, HoleyDigsite.id("large_gem"));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<String> size)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("size").forGetter(Instance::size)
            ).apply(i, Instance::new));
        }
    }

    // 6. holeydigsite:smithed
    public static final class SmithedTrigger extends SimpleCriterionTrigger<SmithedTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, String piece, boolean mixed, boolean withGem, boolean netheriteTier) {
            this.trigger(player, inst -> {
                if (inst.piece.isPresent() && !inst.piece.get().equals(piece)) return false;
                if (inst.mixed.isPresent() && inst.mixed.get() != mixed) return false;
                if (inst.withGem.isPresent() && inst.withGem.get() != withGem) return false;
                if (inst.minTier.isPresent()) {
                    if ("netherite".equals(inst.minTier.get()) && !netheriteTier) return false;
                }
                return true;
            });
            LedgerManager.grantMilestone(player, HoleyDigsite.id("first_piece"));
            if (mixed) LedgerManager.grantMilestone(player, HoleyDigsite.id("mixed_piece"));
            if ("necklace".equals(piece) && withGem) LedgerManager.grantMilestone(player, HoleyDigsite.id("gem_necklace"));
            if ("sword".equals(piece) && netheriteTier) LedgerManager.grantMilestone(player, HoleyDigsite.id("netherite_sword"));
            if (LedgerData.get(player).recordPieceSmithed(piece) >= 5) {
                LedgerManager.grantMilestone(player, HoleyDigsite.id("every_piece"));
            }
        }

        public record Instance(
                Optional<ContextAwarePredicate> player,
                Optional<String> piece,
                Optional<Boolean> mixed,
                Optional<Boolean> withGem,
                Optional<String> minTier
        ) implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("piece").forGetter(Instance::piece),
                    Codec.BOOL.optionalFieldOf("mixed").forGetter(Instance::mixed),
                    Codec.BOOL.optionalFieldOf("with_gem").forGetter(Instance::withGem),
                    Codec.STRING.optionalFieldOf("min_tier").forGetter(Instance::minTier)
            ).apply(i, Instance::new));
        }
    }

    // 7. holeydigsite:plot_bought
    public static final class PlotBoughtTrigger extends SimpleCriterionTrigger<PlotBoughtTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, String plotId, int ownedCount) {
            this.trigger(player, inst -> {
                if (inst.plot.isPresent() && !inst.plot.get().equals(plotId)) return false;
                if (inst.count.isPresent() && ownedCount < inst.count.get()) return false;
                return true;
            });
            if ("deep_dig".equals(plotId)) LedgerManager.grantMilestone(player, HoleyDigsite.id("deep_dig"));
            if ("gravel_flats".equals(plotId)) LedgerManager.grantMilestone(player, HoleyDigsite.id("gravel_flats"));
            if ("sand_dunes".equals(plotId)) LedgerManager.grantMilestone(player, HoleyDigsite.id("sand_dunes"));
            if (ownedCount >= 3) LedgerManager.grantMilestone(player, HoleyDigsite.id("all_plots"));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<String> plot, Optional<Integer> count)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("plot").forGetter(Instance::plot),
                    Codec.INT.optionalFieldOf("count").forGetter(Instance::count)
            ).apply(i, Instance::new));
        }
    }

    // 8. holeydigsite:harvested
    public static final class HarvestedTrigger extends SimpleCriterionTrigger<HarvestedTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, ResourceLocation cropId, int distinctKinds) {
            this.trigger(player, inst -> {
                if (inst.crop.isPresent() && !inst.crop.get().equals(cropId)) return false;
                if (inst.kinds.isPresent() && distinctKinds < inst.kinds.get()) return false;
                return true;
            });
            LedgerManager.grantMilestone(player, HoleyDigsite.id("first_harvest"));
            if (distinctKinds >= 3) LedgerManager.grantMilestone(player, HoleyDigsite.id("all_crops"));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> crop, Optional<Integer> kinds)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    ResourceLocation.CODEC.optionalFieldOf("crop").forGetter(Instance::crop),
                    Codec.INT.optionalFieldOf("kinds").forGetter(Instance::kinds)
            ).apply(i, Instance::new));
        }
    }

    // 9. holeydigsite:upgrade_installed
    public static final class UpgradeInstalledTrigger extends SimpleCriterionTrigger<UpgradeInstalledTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, ResourceLocation upgradeItem) {
            this.trigger(player, inst -> inst.item.isEmpty() || inst.item.get().equals(upgradeItem));
            if (HoleyDigsite.id("riffle_insert").equals(upgradeItem) || HoleyDigsite.id("fine_mesh_screen").equals(upgradeItem)) {
                LedgerManager.grantMilestone(player, HoleyDigsite.id("plot_upgrade"));
            }
            if (HoleyDigsite.id("seed_screen").equals(upgradeItem)) {
                LedgerManager.grantMilestone(player, HoleyDigsite.id("seed_screen"));
            }
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> item)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Instance::item)
            ).apply(i, Instance::new));
        }
    }

    // 10. holeydigsite:line_running
    public static final class LineRunningTrigger extends SimpleCriterionTrigger<LineRunningTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player) {
            this.trigger(player, inst -> true);
            LedgerManager.grantMilestone(player, HoleyDigsite.id("first_line"));
        }

        public record Instance(Optional<ContextAwarePredicate> player)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player)
            ).apply(i, Instance::new));
        }
    }

    // 11. holeydigsite:wearing
    public static final class WearingTrigger extends SimpleCriterionTrigger<WearingTrigger.Instance> {
        @Override
        public Codec<Instance> codec() { return Instance.CODEC; }

        public void trigger(ServerPlayer player, String slot) {
            this.trigger(player, inst -> inst.slot.isEmpty() || inst.slot.get().equals(slot));
            if ("head".equals(slot) || "curios".equals(slot)) {
                LedgerManager.grantMilestone(player, HoleyDigsite.id("wear_jewellery"));
            }
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<String> slot)
                implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.optionalFieldOf("slot").forGetter(Instance::slot)
            ).apply(i, Instance::new));
        }
    }
}
