package com.holeydigsite.tier;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Clump kinds (a generalisation of dirt tiers). One small int travels everywhere a tier used to
 * (pan {@code tier} block state, belt clumps, sluice queue, drill output, shovel), so every "one tier at a time" rule now
 * means "one kind at a time":
 * <pre>
 *   kind 1 = Dirt (Dirtrock, all shovel dirt)   -> Dirt Clump (no component)       -> panning/dirt_t1
 *   kind 2 = Deep dirt (Deep Dirtrock)           -> Dirt Clump, clump_tier 2        -> panning/dirt_t2
 *   kind 3 = Abyssal dirt (Abyssal Dirtrock)     -> Dirt Clump, clump_tier 3        -> panning/dirt_t3
 *   kind 4 = Gravel (Gravelrock, vanilla gravel) -> Gravel Clump (own item)          -> panning/gravel
 *   kind 5 = Sand (Sandrock, vanilla sand)       -> Sand Clump (own item)            -> panning/sand
 *   kind 9 = Red Dirt (Red Dirt Rock)            -> Red Dirt Clump (own item)        -> panning/red_dirt
 *   kind 10 = Mullock (Mullock Rock)             -> Mullock Clump (own item)         -> panning/mullock
 *   kind 11 = Opal Clay (Opal Clay Rock)         -> Opal Clay Clump (own item)       -> panning/opal_clay
 * </pre>
 * Rock to kind is tag based; event clumps require the {@code holeydigsite:event_dug} mark to pan as event kinds.
 */
public final class DirtTier {
    private DirtTier() {}

    public static final int MIN = 1, MAX = 11;
    public static final int DIRT = 1, GRAVEL = 4, SAND = 5, COARSE_DIRT = 6, ROOTED_DIRT = 7, MOSS = 8;
    public static final int RED_DIRT = 9, MULLOCK = 10, OPAL_CLAY = 11;

    public static final TagKey<Block> ROCK_TIER_2 = TagKey.create(Registries.BLOCK, HoleyDigsite.id("rock_tier_2"));
    public static final TagKey<Block> ROCK_TIER_3 = TagKey.create(Registries.BLOCK, HoleyDigsite.id("rock_tier_3"));
    public static final TagKey<Block> ROCK_GRAVEL = TagKey.create(Registries.BLOCK, HoleyDigsite.id("rock_gravel"));
    public static final TagKey<Block> ROCK_SAND = TagKey.create(Registries.BLOCK, HoleyDigsite.id("rock_sand"));
    public static final TagKey<Block> ROCK_RED_DIRT = TagKey.create(Registries.BLOCK, HoleyDigsite.id("rock_red_dirt"));
    public static final TagKey<Block> ROCK_MULLOCK = TagKey.create(Registries.BLOCK, HoleyDigsite.id("rock_mullock"));
    public static final TagKey<Block> ROCK_OPAL_CLAY = TagKey.create(Registries.BLOCK, HoleyDigsite.id("rock_opal_clay"));

    /** One row per kind, with the name used in language keys and loot table paths. */
    private record Kind(String loot, String key) {}

    private static final Kind[] KINDS = {
            null,
            new Kind("panning/dirt_t1", "dirt_1"),
            new Kind("panning/dirt_t2", "dirt_2"),
            new Kind("panning/dirt_t3", "dirt_3"),
            new Kind("panning/gravel", "dirt_4"),
            new Kind("panning/sand", "dirt_5"),
            new Kind("panning/coarse_dirt", "dirt_6"),
            new Kind("panning/rooted_dirt", "dirt_7"),
            new Kind("panning/moss", "dirt_8"),
            new Kind("panning/red_dirt", "dirt_9"),
            new Kind("panning/mullock", "dirt_10"),
            new Kind("panning/opal_clay", "dirt_11"),
    };

    @SuppressWarnings("unchecked")
    private static final ResourceKey<LootTable>[] LOOT = new ResourceKey[MAX + 1];

    static {
        for (int t = MIN; t <= MAX; t++) {
            LOOT[t] = ResourceKey.create(Registries.LOOT_TABLE, HoleyDigsite.id(KINDS[t].loot()));
        }
    }

    public static int clamp(int kind) {
        return Math.clamp(kind, MIN, MAX);
    }

    /** True for the dirt kinds (1-3 and 6-8), which share the Dirt Clump item. */
    public static boolean isDirt(int kind) {
        int k = clamp(kind);
        return k <= 3 || (k >= 6 && k <= 8);
    }

    /** True for the event kinds (9-11). */
    public static boolean isEventKind(int kind) {
        int k = clamp(kind);
        return k >= RED_DIRT && k <= OPAL_CLAY;
    }

    /** Kind of the rock a drill stands on. */
    public static int ofRock(BlockState rock) {
        if (rock.is(ROCK_RED_DIRT)) return RED_DIRT;
        if (rock.is(ROCK_MULLOCK)) return MULLOCK;
        if (rock.is(ROCK_OPAL_CLAY)) return OPAL_CLAY;
        if (rock.is(ROCK_SAND)) return SAND;
        if (rock.is(ROCK_GRAVEL)) return GRAVEL;
        if (rock.is(ROCK_TIER_3)) return 3;
        if (rock.is(ROCK_TIER_2)) return 2;
        return 1;
    }

    /** Kind a wooden shovel gets from a scoopable block (vanilla gravel / sand, else dirt). */
    public static int ofScooped(BlockState block) {
        if (block.is(Blocks.COARSE_DIRT)) return COARSE_DIRT;
        if (block.is(Blocks.ROOTED_DIRT)) return ROOTED_DIRT;
        if (block.is(Blocks.MOSS_BLOCK)) return MOSS;
        if (block.is(Blocks.GRAVEL)) return GRAVEL;
        if (block.is(Blocks.SAND) || block.is(Blocks.RED_SAND)) return SAND;
        return DIRT;
    }

    /** True if the stack is any clump. */
    public static boolean isClump(ItemStack stack) {
        return stack.is(ModItems.DIRT_CLUMP.get())
                || stack.is(ModItems.GRAVEL_CLUMP.get())
                || stack.is(ModItems.SAND_CLUMP.get())
                || stack.is(ModItems.RED_DIRT_CLUMP.get())
                || stack.is(ModItems.MULLOCK_CLUMP.get())
                || stack.is(ModItems.OPAL_CLAY_CLUMP.get());
    }

    /** Kind of a clump stack (Dirt Clump uses its tier component, or 1 if none). */
    public static int ofClump(ItemStack stack) {
        if (stack.is(ModItems.GRAVEL_CLUMP.get())) return GRAVEL;
        if (stack.is(ModItems.SAND_CLUMP.get())) return SAND;
        if (stack.is(ModItems.RED_DIRT_CLUMP.get())) return RED_DIRT;
        if (stack.is(ModItems.MULLOCK_CLUMP.get())) return MULLOCK;
        if (stack.is(ModItems.OPAL_CLAY_CLUMP.get())) return OPAL_CLAY;
        return clamp(stack.getOrDefault(ModDataComponents.CLUMP_TIER.get(), 1));
    }

    /**
     * Kind of a clump stack for panning and sluicing.
     * Event clumps require the event_dug mark to pan as their event kind;
     * unmarked event clumps pan as plain tier 1 dirt.
     */
    public static int panKind(ItemStack stack) {
        if (!isClump(stack)) {
            return 1;
        }
        int kind = ofClump(stack);
        if (isEventKind(kind)) {
            String mark = stack.get(ModDataComponents.EVENT_DUG.get());
            if (mark == null || mark.isEmpty()) {
                return DIRT;
            }
        }
        return kind;
    }

    /** Sets the dirt tier on a Dirt Clump stack (tier 1 removes the component so it stacks with plain clumps). */
    public static ItemStack setClumpTier(ItemStack stack, int tier) {
        int t = clamp(tier);
        if (t <= 1) {
            stack.remove(ModDataComponents.CLUMP_TIER.get());
        } else {
            stack.set(ModDataComponents.CLUMP_TIER.get(), t);
        }
        return stack;
    }

    /** The clump item of a kind. */
    public static Item clumpItem(int kind) {
        return switch (clamp(kind)) {
            case GRAVEL -> ModItems.GRAVEL_CLUMP.get();
            case SAND -> ModItems.SAND_CLUMP.get();
            case RED_DIRT -> ModItems.RED_DIRT_CLUMP.get();
            case MULLOCK -> ModItems.MULLOCK_CLUMP.get();
            case OPAL_CLAY -> ModItems.OPAL_CLAY_CLUMP.get();
            default -> ModItems.DIRT_CLUMP.get();
        };
    }

    /** {@code count} clumps of {@code kind}. Unmarked by default. */
    public static ItemStack clumps(int kind, int count) {
        ItemStack stack = new ItemStack(clumpItem(kind), count);
        return isDirt(kind) ? setClumpTier(stack, kind) : stack;
    }

    /** The panning loot table for a kind. */
    public static ResourceKey<LootTable> lootTable(int kind) {
        return LOOT[clamp(kind)];
    }

    /** Translation key for the dirt kind used in pan tooltips and hints. */
    public static String dirtKey(int kind) {
        return "tier.holeydigsite." + KINDS[clamp(kind)].key();
    }
}
