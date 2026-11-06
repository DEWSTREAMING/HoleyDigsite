package com.holeydigsite.economy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Flawless gems: rare jackpot gems worth many times normal value.
 * Configured in {@code data/holeydigsite/flawless_rules.json}.
 */
@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class FlawlessDefs {
    private FlawlessDefs() {}

    public static final TagKey<Item> GEMS = TagKey.create(Registries.ITEM, HoleyDigsite.id("gems"));

    private static final Set<ResourceLocation> GEM_MATERIALS = Set.of(
            HoleyDigsite.id("ruby"),
            HoleyDigsite.id("sapphire"),
            HoleyDigsite.id("topaz"),
            HoleyDigsite.id("potch"),
            HoleyDigsite.id("boulder_opal"),
            HoleyDigsite.id("sapphire_parti"),
            HoleyDigsite.id("black_opal"),
            HoleyDigsite.id("fire_opal"),
            HoleyDigsite.id("pink_diamond")
    );

    public record Rules(int machineOneIn, int handOneIn, double valueMultiplier) {}

    private static final Rules DEFAULT_RULES = new Rules(1500, 500, 12.0);
    private static volatile Rules rules = loadBuiltin();

    public static Rules rules() {
        return rules;
    }

    public static void setRulesForTest(Rules testRules) {
        rules = testRules != null ? testRules : DEFAULT_RULES;
    }

    /** True if the item is a gem (cut or uncut), either by tag or resource material. */
    public static boolean isGem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(GEMS)) {
            return true;
        }
        ResourceInfo info = Selling.resource(stack);
        return info != null && !info.isCrop() && GEM_MATERIALS.contains(info.material());
    }

    /** True if this stack has the flawless flag set. */
    public static boolean isFlawless(ItemStack stack) {
        return !stack.isEmpty() && stack.getOrDefault(ModDataComponents.FLAWLESS.get(), false);
    }

    /** Marks the stack as flawless (or removes flawless), adding the vanilla enchantment glint. */
    public static void setFlawless(ItemStack stack, boolean flawless) {
        if (stack.isEmpty()) {
            return;
        }
        if (flawless) {
            stack.set(ModDataComponents.FLAWLESS.get(), true);
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            stack.remove(ModDataComponents.FLAWLESS.get());
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    /**
     * Rolls for flawless on the given gem stack. Hand panning gets higher odds than machines.
     * Returns true if the gem became flawless.
     */
    public static boolean rollFlawless(ItemStack stack, boolean isHand, RandomSource random) {
        if (!isGem(stack) || stack.isEmpty()) {
            return false;
        }
        int oneIn = isHand ? rules.handOneIn() : rules.machineOneIn();
        if (oneIn > 0 && random.nextInt(oneIn) == 0) {
            setFlawless(stack, true);
            return true;
        }
        return false;
    }

    private static Rules loadBuiltin() {
        try (InputStream in = FlawlessDefs.class.getResourceAsStream("/data/holeydigsite/flawless_rules.json")) {
            if (in != null) {
                return parseRules(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        } catch (Exception e) {
            HoleyDigsite.LOGGER.warn("Failed to load builtin flawless_rules.json: {}", e.toString());
        }
        return DEFAULT_RULES;
    }

    static Rules parseRules(JsonObject obj) {
        int machine = obj.has("machine_one_in") ? obj.get("machine_one_in").getAsInt() : 1500;
        int hand = obj.has("hand_one_in") ? obj.get("hand_one_in").getAsInt() : 500;
        double mult = obj.has("value_multiplier") ? obj.get("value_multiplier").getAsDouble() : 12.0;
        return new Rules(machine, hand, mult);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager rm, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void v, ResourceManager rm, ProfilerFiller profiler) {
                rm.getResource(HoleyDigsite.id("flawless_rules.json")).ifPresent(res -> {
                    try (Reader r = res.openAsReader()) {
                        rules = parseRules(JsonParser.parseReader(r).getAsJsonObject());
                    } catch (Exception e) {
                        HoleyDigsite.LOGGER.warn("Bad flawless_rules.json: {}", e.toString());
                    }
                });
            }
        });
    }
}
