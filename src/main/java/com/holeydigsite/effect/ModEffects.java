package com.holeydigsite.effect;

import com.holeydigsite.HoleyDigsite;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Phase 18: our one mob effect. Steady Hands (Riverbean Stew) makes a panning scoop {@link #PAN_SPEEDUP_PERCENT}% faster; the work is
 * done in {@link com.holeydigsite.panning.Panning#cycleTicks(LivingEntity, net.minecraft.world.item.ItemStack)} - the effect itself does nothing per tick.
 */
public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, HoleyDigsite.MODID);

    /** Percent less time per panning scoop while Steady Hands is active. */
    public static final int PAN_SPEEDUP_PERCENT = 30;

    public static final DeferredHolder<MobEffect, MobEffect> STEADY_HANDS = EFFECTS.register("steady_hands", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x4FB6C9) { });

    /** True if the entity has Steady Hands. */
    public static boolean hasSteadyHands(LivingEntity entity) {
        return entity.hasEffect(STEADY_HANDS);
    }
}
