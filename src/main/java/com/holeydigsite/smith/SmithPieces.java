package com.holeydigsite.smith;

import com.holeydigsite.economy.SellValue;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.Metals;
import com.holeydigsite.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Everything about a smithed piece (Phase 19): creating the stack from its ingredients (stats from the metals), its name and
 * tooltip, and its sell value.
 */
public final class SmithPieces {
    private SmithPieces() {}

    private static final ResourceLocation BASE_DAMAGE_ID = ResourceLocation.withDefaultNamespace("base_attack_damage");
    private static final ResourceLocation BASE_SPEED_ID = ResourceLocation.withDefaultNamespace("base_attack_speed");
    /** A tool never mines slower than this (wood level). */
    private static final double MIN_MINING_SPEED = 2.0;

    /** Computed stats of a weapon / tool: {@code scale} = share of the metals' full stats reached through the weight. */
    public record Stats(double scale, double damage, double speed, int durability) {}

    @Nullable
    public static SmithData data(ItemStack stack) {
        return stack.get(ModDataComponents.SMITH.get());
    }

    /**
     * Stats from the ingots: the metals' values averaged by grams, scaled by {@code floor + (1 - floor) * min(1, total / full_grams)}.
     * Damage = base + average bonus x scale x damage_scale; durability = average x scale x durability_scale; speed = average x scale.
     */
    /**
     * The material (metal or gem id) that tints layer {@code layer} of a piece's item model, or empty for a plain layer (wood, leather).
     * Follows the recipe's {@code parts}; an ingot slot the piece has fewer ingots for uses its last ingot. Without parts: layer 0 =
     * main metal, layer 1 = gem.
     */
    public static Optional<ResourceLocation> partMaterial(@Nullable AnvilRecipe recipe, SmithData data, int layer) {
        List<AnvilRecipe.Part> parts = recipe == null ? List.of() : recipe.parts();
        if (parts.isEmpty()) {
            return layer == 0 ? Optional.ofNullable(data.mainMetal()) : layer == 1 ? data.gem().map(SmithData.Gem::material) : Optional.empty();
        }
        if (layer < 0 || layer >= parts.size()) {
            return Optional.empty();
        }
        AnvilRecipe.Part part = parts.get(layer);
        return switch (part.source()) {
            case INGOT -> data.ingots().isEmpty() ? Optional.empty()
                    : Optional.of(data.ingots().get(Math.min(part.index(), data.ingots().size() - 1)).metal());
            case GEM -> data.gem().map(SmithData.Gem::material);
            case NONE -> Optional.empty();
        };
    }

    public static Stats stats(AnvilRecipe.Gear gear, SmithData data, HolderLookup.Provider registries) {
        double total = data.ingotGrams();
        double damage = 0, speed = 0, durability = 0;
        for (SmithData.Ingot ing : data.ingots()) {
            SmithMaterial m = SmithMaterial.get(registries, ing.metal());
            if (m != null) {
                damage += m.damage() * ing.grams();
                speed += m.speed() * ing.grams();
                durability += m.durability() * ing.grams();
            }
        }
        if (total <= 0) {
            return new Stats(0, gear.baseDamage(), MIN_MINING_SPEED, 1);
        }
        double scale = gear.floor() + (1 - gear.floor()) * Math.min(1.0, total / gear.fullGrams());
        return new Stats(scale,
                gear.baseDamage() + damage / total * scale * gear.damageScale(),
                Math.max(MIN_MINING_SPEED, speed / total * scale),
                (int) Math.max(1, Math.round(durability / total * scale * gear.durabilityScale())));
    }

    /** A piece of {@code item} made of {@code data}: the component plus (weapons / tools) durability, damage, speed and mining speed. */
    public static ItemStack create(Item item, SmithData data, HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.SMITH.get(), data);
        AnvilRecipe recipe = AnvilRecipes.forItem(registries, item);
        if (recipe != null && recipe.gear().isPresent()) {
            AnvilRecipe.Gear gear = recipe.gear().get();
            Stats s = stats(gear, data, registries);
            stack.set(DataComponents.MAX_DAMAGE, s.durability());
            stack.set(DataComponents.DAMAGE, 0);
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_DAMAGE_ID, s.damage(), AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_SPEED_ID, gear.attackSpeed(), AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .build());
            if (item instanceof AxeItem) {
                stack.set(DataComponents.TOOL, new Tool(List.of(Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_AXE, (float) s.speed())), 1.0F, 1));
            }
        }
        if (item instanceof SmithItems.Necklace) {
            applyHeadArmor(stack, registries);
        }
        return stack;
    }

    // ------------------------------------------------------------------------------------------------ wearing (Phase 20)

    /** Armour and toughness a worn piece gives. */
    public record Armor(double armor, double toughness) {
        public static final Armor NONE = new Armor(0, 0);

        public boolean isEmpty() {
            return armor <= 0 && toughness <= 0;
        }
    }

    /** Where a piece is worn. */
    public enum WearWhere { HEAD, CURIO }

    private static final ResourceLocation WEAR_ARMOR_ID = ResourceLocation.fromNamespaceAndPath("holeydigsite", "jewellery_armor");
    private static final ResourceLocation WEAR_TOUGHNESS_ID = ResourceLocation.fromNamespaceAndPath("holeydigsite", "jewellery_toughness");

    /** The recipe's wear block of the piece's item, if it is wearable at all. */
    @Nullable
    public static AnvilRecipe.Wear wearRule(Item item, HolderLookup.Provider registries) {
        AnvilRecipe recipe = AnvilRecipes.forItem(registries, item);
        return recipe == null ? null : recipe.wear().orElse(null);
    }

    /** Armour of a piece worn at {@code where} (NONE if the stack has no materials or the recipe does not allow that place). */
    public static Armor wearStats(ItemStack stack, HolderLookup.Provider registries, WearWhere where) {
        SmithData data = data(stack);
        AnvilRecipe.Wear wear = data == null ? null : wearRule(stack.getItem(), registries);
        if (wear == null) {
            return Armor.NONE;
        }
        return wearStats(wear, data, registries, where);
    }

    /** Mirror in tools/EconomySim.java (phase20). */
    public static Armor wearStats(AnvilRecipe.Wear wear, SmithData data, HolderLookup.Provider registries, WearWhere where) {
        AnvilRecipe.WearSlot slot = (where == WearWhere.HEAD ? wear.head() : wear.curio()).orElse(null);
        double total = data.ingotGrams();
        if (slot == null || total <= 0) {
            return Armor.NONE;
        }
        double armor = 0, toughness = 0;
        for (SmithData.Ingot ing : data.ingots()) {
            SmithMaterial m = SmithMaterial.get(registries, ing.metal());
            if (m != null) {
                armor += m.armor() * ing.grams();
                toughness += m.toughness() * ing.grams();
            }
        }
        double scale = wear.floor() + (1 - wear.floor()) * Math.min(1.0, total / wear.fullGrams());
        return new Armor(armor / total * scale * slot.armor(), toughness / total * scale * slot.toughness());
    }

    /** The item attribute modifiers of a necklace worn in the head slot (empty if it gives none). */
    public static ItemAttributeModifiers headModifiers(Armor a) {
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        if (a.armor() > 0) {
            b.add(Attributes.ARMOR, new AttributeModifier(WEAR_ARMOR_ID, a.armor(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD);
        }
        if (a.toughness() > 0) {
            b.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(WEAR_TOUGHNESS_ID, a.toughness(), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.HEAD);
        }
        // the piece's own tooltip tells the numbers, so the vanilla attribute block stays hidden
        return b.build().withTooltip(false);
    }

    /** Sets the head-slot armour component on a stack that has materials and a head wear rule; returns true if it changed anything. */
    public static boolean applyHeadArmor(ItemStack stack, HolderLookup.Provider registries) {
        Armor a = wearStats(stack, registries, WearWhere.HEAD);
        if (a.isEmpty()) {
            return false;
        }
        ItemAttributeModifiers mods = headModifiers(a);
        if (mods.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
            return false;
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, mods);
        return true;
    }

    // ------------------------------------------------------------------------------------------------ value

    /**
     * Copper value of one piece: (sum of ingot grams x the metal's ingot price + gem grams x the gem's cut price) x (1 + the recipe's
     * bonus), times the remaining durability for weapons / tools. 0 for an item without materials.
     */
    public static long value(ItemStack stack, HolderLookup.Provider registries) {
        SmithData data = data(stack);
        if (data == null) {
            return 0;
        }
        double sum = ingredientValue(data, registries);
        AnvilRecipe recipe = AnvilRecipes.forItem(registries, stack.getItem());
        double bonus = recipe == null ? 0 : recipe.bonusFor(data.gem().isPresent());
        double v = sum * (1 + bonus);
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            v *= Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) / (double) stack.getMaxDamage();
        }
        return (long) Math.floor(v + 1e-9);
    }

    /** Sell value of the ingredients alone (no bonus, no wear). */
    public static double ingredientValue(SmithData data, HolderLookup.Provider registries) {
        double sum = 0;
        for (SmithData.Ingot ing : data.ingots()) {
            SellValue p = Selling.price(registries, ing.metal());
            sum += p == null ? 0 : ing.grams() * p.perGram(true);
        }
        if (data.gem().isPresent()) {
            SmithData.Gem g = data.gem().get();
            SellValue p = Selling.price(registries, g.material());
            sum += p == null ? 0 : g.grams() * p.perGram(false);
        }
        return sum;
    }

    // ------------------------------------------------------------------------------------------------ name + tooltip

    /** "Goldchro Sword": the main metal (most grams) in front of the piece's plain name. */
    public static Component name(ItemStack stack, Component plain) {
        SmithData data = data(stack);
        ResourceLocation main = data == null ? null : data.mainMetal();
        return main == null ? plain : Component.translatable("item.holeydigsite.smith_named", Metals.name(main), plain);
    }

    /** "Goldchro 120 g, Silvane 90 g · Sapphire 4 g". */
    public static MutableComponent materials(SmithData data) {
        MutableComponent line = Component.empty();
        boolean first = true;
        for (SmithData.Ingot ing : data.ingots()) {
            if (!first) {
                line.append(", ");
            }
            line.append(Component.translatable("tooltip.holeydigsite.smith.part", Metals.name(ing.metal()), ing.grams()));
            first = false;
        }
        data.gem().ifPresent(g -> line.append(" · ").append(Component.translatable("tooltip.holeydigsite.smith.part",
                Metals.name(g.material()), g.grams())));
        return line;
    }

    /** "+2.6 Armour when worn on the head" / "+1.3 Armour in a Curios slot" and where the piece can be worn. */
    private static void wearTooltip(ItemStack stack, HolderLookup.Provider registries, List<Component> tooltip) {
        AnvilRecipe.Wear wear = wearRule(stack.getItem(), registries);
        if (wear == null) {
            return;
        }
        boolean curios = CuriosLoaded.is();
        Armor head = wearStats(stack, registries, WearWhere.HEAD);
        Armor curio = wearStats(stack, registries, WearWhere.CURIO);
        if (!head.isEmpty()) {
            tooltip.add(line("head", head).withStyle(ChatFormatting.BLUE));
        }
        if (!curio.isEmpty() && (curios || head.isEmpty())) {
            tooltip.add(line("curio", curio).withStyle(ChatFormatting.BLUE));
        }
        if (stack.getItem() instanceof SmithItems.Necklace) {
            tooltip.add(Component.translatable("tooltip.holeydigsite.wear.where_necklace").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable(curios ? "tooltip.holeydigsite.wear.where_ring" : "tooltip.holeydigsite.wear.need_curios")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static MutableComponent line(String where, Armor a) {
        return a.toughness() >= 0.05
                ? Component.translatable("tooltip.holeydigsite.wear." + where + "_t", fmt(a.armor()), fmt(a.toughness()))
                : Component.translatable("tooltip.holeydigsite.wear." + where, fmt(a.armor()));
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Tooltip lines of a smithed piece: its materials and what it sells for. */
    public static void tooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip) {
        SmithData data = data(stack);
        if (data == null) {
            tooltip.add(Component.translatable("tooltip.holeydigsite.smith.plain").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(materials(data).withStyle(ChatFormatting.GRAY));
        if (context.registries() != null) {
            wearTooltip(stack, context.registries(), tooltip);
            long v = value(stack, context.registries());
            if (v > 0) {
                tooltip.add(Component.translatable("tooltip.holeydigsite.sells_for", Selling.format(v)).withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
