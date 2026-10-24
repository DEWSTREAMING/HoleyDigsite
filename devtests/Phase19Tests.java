package com.holeydigsite.devtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.sellpad.SellPadBlock;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.smith.AnvilRecipes;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithMaterial;
import com.holeydigsite.smith.SmithPieces;
import com.holeydigsite.smith.SmithingAnvilBlock;
import com.holeydigsite.smith.SmithingAnvilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Phase 19 (sandbox only): Smithing Anvil (lay / take back / cycle / refusals / hammering / mixed metals / breaking), the pieces
 * (materials, value, tool stats), data and assets. Template {@code empty_long} (26x5x5).
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase19Tests {
    private static final BlockPos REL = new BlockPos(3, 1, 2);

    private static void clear(GameTestHelper h) {
        for (int x = 0; x < 26; x++) for (int y = 1; y <= 4; y++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int x = 0; x < 26; x++) for (int z = 0; z < 5; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        for (ItemEntity e : h.getEntities(EntityType.ITEM)) e.discard();
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    /** A fresh anvil at {@link #REL}. */
    private static SmithingAnvilBlockEntity anvil(GameTestHelper h) {
        clear(h);
        h.setBlock(REL, ModBlocks.SMITHING_ANVIL.get().defaultBlockState());
        return (SmithingAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(REL));
    }

    private static BlockPos abs(GameTestHelper h) {
        return h.absolutePos(REL);
    }

    private static void click(GameTestHelper h, ServerPlayer p, ItemStack held, boolean sneak) {
        // whatever the previous click put into the hand slot (items taken back, pieces made) moves to a free slot first
        ItemStack old = p.getMainHandItem();
        if (!old.isEmpty() && old != held) {
            for (int i = 9; i < 36; i++) {
                if (p.getInventory().items.get(i).isEmpty()) {
                    p.getInventory().items.set(i, old);
                    break;
                }
            }
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(sneak);
        BlockPos a = abs(h);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(a).add(0, 0.5, 0), Direction.UP, a, false));
        p.setShiftKeyDown(false);
    }

    private static void select(SmithingAnvilBlockEntity be, String recipe) {
        be.select(HoleyDigsite.id(recipe));
    }

    private static ItemStack folk(int g) {
        return IngotItem.of(ModItems.FOLKIRON_INGOT.get(), g);
    }

    private static ItemStack gold(int g) {
        return IngotItem.of(ModItems.GOLDCHRO_INGOT.get(), g);
    }

    private static ItemStack silv(int g) {
        return IngotItem.of(ModItems.SILVANE_INGOT.get(), g);
    }

    private static void check(GameTestHelper h, boolean ok, String msg) {
        if (!ok) h.fail(msg);
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static AnvilRecipe recipe(GameTestHelper h, String id) {
        return AnvilRecipes.find(h.getLevel().registryAccess(), HoleyDigsite.id(id)).recipe();
    }

    private static SmithData data(ItemStack s) {
        return SmithPieces.data(s);
    }

    private static double attack(ItemStack s) {
        var mods = s.get(DataComponents.ATTRIBUTE_MODIFIERS);
        for (var e : mods.modifiers()) {
            if (e.attribute().is(Attributes.ATTACK_DAMAGE)) return e.modifier().amount();
        }
        return -1;
    }

    // ------------------------------------------------------------------------------------------------ data

    @GameTest(template = "empty")
    public static void recipesAndMaterialsLoad(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        List<AnvilRecipes.Entry> all = AnvilRecipes.all(reg);
        check(h, all.size() == 5, "5 recipes: " + all.size());
        String[] order = {"ring", "necklace", "dagger", "axe", "sword"};
        int[] ingots = {1, 2, 1, 2, 3};
        for (int i = 0; i < 5; i++) {
            check(h, all.get(i).id().equals(HoleyDigsite.id(order[i])), "order " + i + ": " + all.get(i).id());
            check(h, all.get(i).recipe().ingots() == ingots[i], order[i] + " ingots");
        }
        check(h, all.get(0).recipe().gem() == AnvilRecipe.GemRule.OPTIONAL && all.get(1).recipe().gem() == AnvilRecipe.GemRule.REQUIRED
                && all.get(4).recipe().gem() == AnvilRecipe.GemRule.NONE, "gem rules");
        check(h, all.get(0).recipe().gear().isEmpty() && all.get(1).recipe().gear().isEmpty() && all.get(4).recipe().gear().isPresent(), "gear only on weapons / tools");
        for (String m : new String[]{"folkiron", "orchremia", "goldchro", "silvane"}) {
            SmithMaterial sm = SmithMaterial.get(reg, HoleyDigsite.id(m));
            check(h, sm != null && sm.isMetal(), m + " is a smithing metal");
        }
        for (String g : new String[]{"ruby", "sapphire", "topaz"}) {
            SmithMaterial sm = SmithMaterial.get(reg, HoleyDigsite.id(g));
            check(h, sm != null && !sm.isMetal(), g + " has a colour only");
        }
        h.succeed();
    }

    /** Every model texture and model file the new items and the block refer to exists in the jar. */
    @GameTest(template = "empty")
    public static void assetsExist(GameTestHelper h) {
        String[] models = {"item/smith_ring", "item/smith_ring_gem", "item/smith_necklace", "item/smith_dagger", "item/smith_axe", "item/smith_sword",
                "item/blacksmith_hammer", "item/smithing_anvil", "block/smithing_anvil"};
        for (String m : models) {
            JsonObject o = json("/assets/holeydigsite/models/" + m + ".json");
            if (o.has("textures")) {
                for (var e : o.getAsJsonObject("textures").entrySet()) {
                    ResourceLocation t = ResourceLocation.parse(e.getValue().getAsString());
                    check(h, exists("/assets/" + t.getNamespace() + "/textures/" + t.getPath() + ".png"), m + ": texture " + t);
                }
            }
            if (o.has("overrides")) {
                for (var ov : o.getAsJsonArray("overrides")) {
                    ResourceLocation t = ResourceLocation.parse(ov.getAsJsonObject().get("model").getAsString());
                    check(h, exists("/assets/" + t.getNamespace() + "/models/" + t.getPath() + ".json"), m + ": override model " + t);
                }
            }
        }
        check(h, exists("/assets/holeydigsite/blockstates/smithing_anvil.json"), "blockstate");
        check(h, exists("/data/holeydigsite/loot_table/blocks/smithing_anvil.json"), "loot table");
        JsonObject lang = json("/assets/holeydigsite/lang/en_us.json");
        for (String k : new String[]{"block.holeydigsite.smithing_anvil", "item.holeydigsite.blacksmith_hammer", "item.holeydigsite.smith_ring",
                "item.holeydigsite.smith_necklace", "item.holeydigsite.smith_dagger", "item.holeydigsite.smith_axe", "item.holeydigsite.smith_sword",
                "item.holeydigsite.smith_named", "tooltip.holeydigsite.smith.part", "message.holeydigsite.anvil.uncut", "text.holeydigsite.anvil.ingots"}) {
            check(h, lang.has(k), "lang " + k);
        }
        h.succeed();
    }

    private static boolean exists(String path) {
        return Phase19Tests.class.getResource(path) != null;
    }

    private static JsonObject json(String path) {
        try (InputStream in = Phase19Tests.class.getResourceAsStream(path)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException(path, e);
        }
    }

    // ------------------------------------------------------------------------------------------------ laying things on

    @GameTest(template = "empty_long")
    public static void layOneAtATimeAndTakeBackInReverse(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        ItemStack stack = folk(50).copyWithCount(5);
        click(h, p, stack, false);
        check(h, be.ingotCount() == 1 && stack.getCount() == 4, "a stack lays ONE ingot: " + be.ingotCount() + " / " + stack.getCount());
        click(h, p, stack, true);   // sneak-right-click lays one too
        check(h, be.ingotCount() == 2 && stack.getCount() == 3, "sneak lays one: " + be.ingotCount());
        ItemStack gem = new ItemStack(ModItems.SAPPHIRE_SMALL.get());
        click(h, p, gem, false);
        check(h, be.items().size() == 3 && be.gem().isPresent() && gem.isEmpty(), "gem laid");
        check(h, be.items().get(0).is(ModItems.FOLKIRON_INGOT.get()) && IngotItem.grams(be.items().get(0)) == 50, "ingot keeps its weight");
        // empty hand takes the last item back, then the ingots in reverse
        click(h, p, ItemStack.EMPTY, false);
        check(h, count(p, ModItems.SAPPHIRE_SMALL.get()) == 1 && be.gem().isEmpty() && be.items().size() == 2, "gem taken back first");
        click(h, p, ItemStack.EMPTY, false);
        click(h, p, ItemStack.EMPTY, false);
        check(h, be.isEmpty() && count(p, ModItems.FOLKIRON_INGOT.get()) == 5, "all back (3 left in the stack + 2): " + count(p, ModItems.FOLKIRON_INGOT.get()));
        click(h, p, ItemStack.EMPTY, false); // nothing left: no crash
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void anvilHoldsThreeIngotsAndOneGemAndRefusesTheRest(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        for (int i = 0; i < 3; i++) click(h, p, folk(50), false);
        ItemStack fourth = folk(50);
        click(h, p, fourth, false);
        check(h, be.ingotCount() == 3 && fourth.getCount() == 1, "a fourth ingot is refused and stays in hand");
        click(h, p, new ItemStack(ModItems.RUBY_SMALL.get()), false);
        ItemStack second = new ItemStack(ModItems.SAPPHIRE_SMALL.get());
        click(h, p, second, false);
        check(h, be.gem().isPresent() && be.gem().get().is(ModItems.RUBY_SMALL.get()) && second.getCount() == 1, "only one gem");
        // chunks, plain items and our own coins are not laid (and give no refusal that eats them)
        ItemStack chunk = new ItemStack(ModItems.FOLKIRON_CHUNK_LARGE.get());
        click(h, p, chunk, false);
        check(h, chunk.getCount() == 1 && be.items().size() == 4, "chunks refused");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void uncutGemsAreRefused(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        ItemStack uncut = new ItemStack(ModItems.UNCUT_SAPPHIRE_MEDIUM.get());
        click(h, p, uncut, false);
        check(h, be.isEmpty() && uncut.getCount() == 1, "uncut gem not laid, not used up");
        check(h, be.check(uncut) == SmithingAnvilBlockEntity.Refusal.UNCUT, "refusal is UNCUT");
        click(h, p, new ItemStack(ModItems.SAPPHIRE_MEDIUM.get()), false);
        check(h, be.gem().isPresent(), "the cut gem goes on");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void tinyIngotsAreRefusedByTheSelectedRecipe(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        int ringMin = recipe(h, "ring").minGrams(), swordMin = recipe(h, "sword").minGrams();
        check(h, swordMin > ringMin, "the sword needs heavier ingots than the ring");
        ItemStack tiny = folk(ringMin - 1);
        click(h, p, tiny, false);
        check(h, be.isEmpty() && tiny.getCount() == 1, "below the ring's minimum: refused");
        click(h, p, folk(ringMin), false);
        check(h, be.ingotCount() == 1, "the ring takes its minimum");
        // a light ingot laid for the ring is a mismatch for the sword / dagger (nothing used up)
        select(be, "dagger");
        check(h, be.problem(recipe(h, "dagger")) == SmithingAnvilBlockEntity.Problem.LIGHT, "light ingot found");
        check(h, SmithingAnvilBlock.hammer(h.getLevel(), abs(h), p) == SmithingAnvilBlock.Hit.MISMATCH && be.ingotCount() == 1 && be.hits() == 0, "hit refused");
        select(be, "sword");
        ItemStack light = folk(ringMin);
        click(h, p, light, false);
        check(h, be.ingotCount() == 1 && light.getCount() == 1, "sword recipe selected: light ingot refused at laying");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void sneakWithEmptyHandCyclesTheRecipes(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        String[] order = {"ring", "necklace", "dagger", "axe", "sword", "ring"};
        check(h, be.recipe().id().equals(HoleyDigsite.id("ring")), "starts at the first recipe");
        for (int i = 1; i < order.length; i++) {
            click(h, p, ItemStack.EMPTY, true);
            check(h, be.recipe().id().equals(HoleyDigsite.id(order[i])), "step " + i + ": " + be.recipe().id());
        }
        // cycling restarts the hits
        click(h, p, folk(60), false);
        SmithingAnvilBlock.hammer(h.getLevel(), abs(h), p);
        check(h, be.hits() == 1, "one hit");
        click(h, p, ItemStack.EMPTY, true);
        check(h, be.hits() == 0 && be.ingotCount() == 1, "cycling restarts the hits and keeps the contents");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ hammering

    @GameTest(template = "empty_long")
    public static void wrongContentsAreRefusedAndNothingIsUsedUp(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        BlockPos a = abs(h);
        check(h, SmithingAnvilBlock.hammer(h.getLevel(), a, p) == SmithingAnvilBlock.Hit.MISMATCH, "empty anvil: mismatch");
        select(be, "sword");
        click(h, p, folk(100), false);
        click(h, p, folk(100), false);
        check(h, be.problem(recipe(h, "sword")) == SmithingAnvilBlockEntity.Problem.FEW_INGOTS, "2 of 3 ingots");
        check(h, SmithingAnvilBlock.hammer(h.getLevel(), a, p) == SmithingAnvilBlock.Hit.MISMATCH && be.hits() == 0 && be.ingotCount() == 2, "sword with 2 ingots");
        click(h, p, folk(100), false);
        click(h, p, new ItemStack(ModItems.RUBY_SMALL.get()), false);
        check(h, be.problem(recipe(h, "sword")) == SmithingAnvilBlockEntity.Problem.NO_GEM, "a gem does not belong on a sword");
        check(h, SmithingAnvilBlock.hammer(h.getLevel(), a, p) == SmithingAnvilBlock.Hit.MISMATCH && be.items().size() == 4, "nothing used");
        select(be, "ring");
        check(h, be.problem(recipe(h, "ring")) == SmithingAnvilBlockEntity.Problem.MANY_INGOTS, "3 ingots for a ring");
        select(be, "necklace");
        check(h, be.problem(recipe(h, "necklace")) == SmithingAnvilBlockEntity.Problem.MANY_INGOTS, "3 ingots for a necklace");
        be.takeLast();   // the gem
        be.takeLast();   // one ingot -> 2 ingots, no gem
        check(h, be.problem(recipe(h, "necklace")) == SmithingAnvilBlockEntity.Problem.NEEDS_GEM, "a necklace needs its gem");
        check(h, SmithingAnvilBlock.hammer(h.getLevel(), a, p) == SmithingAnvilBlock.Hit.MISMATCH, "no gem: refused");
        check(h, count(p, ModItems.SMITH_NECKLACE.get()) == 0 && be.ingotCount() == 2, "nothing made, nothing lost");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void exactRecipeCraftsAfterTheHits(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        BlockPos a = abs(h);
        for (String id : new String[]{"ring", "dagger", "axe", "sword", "necklace"}) {
            AnvilRecipe r = recipe(h, id);
            select(be, id);
            for (int i = 0; i < r.ingots(); i++) click(h, p, folk(Math.max(r.minGrams(), 100)), false);
            if (r.gem() == AnvilRecipe.GemRule.REQUIRED) click(h, p, new ItemStack(ModItems.SAPPHIRE_MEDIUM.get()), false);
            for (int i = 1; i < r.hits(); i++) {
                check(h, SmithingAnvilBlock.hammer(h.getLevel(), a, p) == SmithingAnvilBlock.Hit.HIT && be.hits() == i, id + ": hit " + i);
            }
            check(h, count(p, r.result()) == 0, id + ": not made before the last hit");
            check(h, SmithingAnvilBlock.hammer(h.getLevel(), a, p) == SmithingAnvilBlock.Hit.MADE, id + ": the last hit makes it");
            check(h, count(p, r.result()) == 1 && be.isEmpty() && be.hits() == 0, id + ": piece in the inventory, anvil empty");
        }
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void hammerHitsHaveACooldown(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        click(h, p, folk(100), false);
        ItemStack hammer = new ItemStack(ModItems.BLACKSMITH_HAMMER.get());
        click(h, p, hammer, false);
        check(h, be.hits() == 1, "first hit");
        click(h, p, hammer, false);
        check(h, be.hits() == 1, "second click right away is ignored (cooldown)");
        p.getCooldowns().removeCooldown(hammer.getItem());
        click(h, p, hammer, false);
        check(h, be.hits() == 2, "hits again after the cooldown");
        check(h, hammer.getCount() == 1 && !hammer.isDamageableItem(), "the hammer is never used up");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void mixedMetalsAreAcceptedAndRemembered(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        select(be, "sword");
        click(h, p, folk(60), false);
        click(h, p, gold(120), false);
        click(h, p, silv(90), false);
        for (int i = 0; i < recipe(h, "sword").hits(); i++) SmithingAnvilBlock.hammer(h.getLevel(), abs(h), p);
        ItemStack sword = null;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.SMITH_SWORD.get())) sword = s;
        check(h, sword != null, "sword made");
        SmithData d = data(sword);
        check(h, d.ingots().size() == 3 && d.gem().isEmpty(), "3 ingots, no gem");
        check(h, d.ingots().get(0).equals(new SmithData.Ingot(HoleyDigsite.id("folkiron"), 60))
                && d.ingots().get(1).equals(new SmithData.Ingot(HoleyDigsite.id("goldchro"), 120))
                && d.ingots().get(2).equals(new SmithData.Ingot(HoleyDigsite.id("silvane"), 90)), "every ingot in laying order: " + d);
        check(h, HoleyDigsite.id("goldchro").equals(d.mainMetal()), "main metal = most grams");
        Component name = sword.getHoverName();
        check(h, name.getContents() instanceof TranslatableContents t && t.getKey().equals("item.holeydigsite.smith_named")
                && t.getArgs()[0] instanceof Component c && c.getContents() instanceof TranslatableContents m && m.getKey().equals("material.holeydigsite.goldchro"),
                "named after the main metal: " + name);
        // a sword of three equal Folkiron ingots is fine too
        select(be, "sword");
        for (int i = 0; i < 3; i++) click(h, p, folk(60), false);
        for (int i = 0; i < recipe(h, "sword").hits(); i++) SmithingAnvilBlock.hammer(h.getLevel(), abs(h), p);
        check(h, count(p, ModItems.SMITH_SWORD.get()) == 2, "second sword");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void ringAndNecklaceRememberMetalGramsAndGem(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        click(h, p, gold(120), false);
        ItemStack sapphire = new ItemStack(ModItems.SAPPHIRE_MEDIUM.get());
        int gemGrams = Selling.grams(sapphire);
        click(h, p, sapphire, false);
        for (int i = 0; i < recipe(h, "ring").hits(); i++) SmithingAnvilBlock.hammer(h.getLevel(), abs(h), p);
        ItemStack ring = null;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.SMITH_RING.get())) ring = s;
        SmithData d = data(ring);
        check(h, d.ingots().equals(List.of(new SmithData.Ingot(HoleyDigsite.id("goldchro"), 120)))
                && d.gem().equals(Optional.of(new SmithData.Gem(HoleyDigsite.id("sapphire"), gemGrams))), "ring: " + d);
        // component survives a save / load round trip
        var reg = h.getLevel().registryAccess();
        ItemStack back = ItemStack.parse(reg, ring.save(reg)).orElseThrow();
        check(h, ItemStack.isSameItemSameComponents(ring, back), "round trip");
        // pieces with different materials do not stack, equal ones do
        ItemStack other = SmithPieces.create(ModItems.SMITH_RING.get(), new SmithData(List.of(new SmithData.Ingot(HoleyDigsite.id("goldchro"), 121)), Optional.empty()), reg);
        check(h, !ItemStack.isSameItemSameComponents(ring, other) && ItemStack.isSameItemSameComponents(ring, back), "stacking");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void breakingTheAnvilDropsEverythingOnIt(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        click(h, p, folk(50), false);
        click(h, p, gold(70), false);
        click(h, p, new ItemStack(ModItems.RUBY_SMALL.get()), false);
        h.getLevel().removeBlock(abs(h), false);
        int ingots = 0, rubies = 0;
        for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
            if (e.getItem().is(ModItems.FOLKIRON_INGOT.get()) && IngotItem.grams(e.getItem()) == 50) ingots++;
            if (e.getItem().is(ModItems.GOLDCHRO_INGOT.get()) && IngotItem.grams(e.getItem()) == 70) ingots++;
            if (e.getItem().is(ModItems.RUBY_SMALL.get())) rubies++;
        }
        check(h, ingots == 2 && rubies == 1, "both ingots (with their weights) and the gem dropped: " + ingots + " " + rubies);
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void anvilContentsSurviveSaveAndLoad(GameTestHelper h) {
        SmithingAnvilBlockEntity be = anvil(h);
        ServerPlayer p = player(h);
        select(be, "axe");
        click(h, p, silv(77), false);
        click(h, p, gold(33), false);
        SmithingAnvilBlock.hammer(h.getLevel(), abs(h), p);
        var reg = h.getLevel().registryAccess();
        var tag = be.saveWithFullMetadata(reg);
        BlockState state = h.getLevel().getBlockState(abs(h));
        SmithingAnvilBlockEntity copy = new SmithingAnvilBlockEntity(abs(h), state);
        copy.setLevel(h.getLevel());
        copy.loadWithComponents(tag, reg);
        check(h, copy.items().size() == 2 && IngotItem.grams(copy.items().get(0)) == 77 && IngotItem.grams(copy.items().get(1)) == 33, "items");
        check(h, copy.recipe().id().equals(HoleyDigsite.id("axe")) && copy.hits() == 1, "recipe + hits");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ value

    private static ItemStack piece(GameTestHelper h, Item item, SmithData data) {
        return SmithPieces.create(item, data, h.getLevel().registryAccess());
    }

    private static SmithData.Ingot ing(String metal, int g) {
        return new SmithData.Ingot(HoleyDigsite.id(metal), g);
    }

    @GameTest(template = "empty")
    public static void sellValueIsIngredientsTimesBonus(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        // ring without a gem: Goldchro ingot price 20 per gram, +25%
        ItemStack ring = piece(h, ModItems.SMITH_RING.get(), new SmithData(List.of(ing("goldchro", 100)), Optional.empty()));
        check(h, Selling.unitValue(ring, reg) == 2500, "ring: " + Selling.unitValue(ring, reg));
        // with a gem: (ingot + gem at the CUT price) x 1.40
        ItemStack sapphire = new ItemStack(ModItems.SAPPHIRE_MEDIUM.get());
        long gem = Selling.unitValue(sapphire, reg);
        ItemStack gemRing = piece(h, ModItems.SMITH_RING.get(), new SmithData(List.of(ing("goldchro", 100)), Optional.of(new SmithData.Gem(HoleyDigsite.id("sapphire"), Selling.grams(sapphire)))));
        check(h, Selling.unitValue(gemRing, reg) == (long) Math.floor((2000 + gem) * 1.4 + 1e-9), "gem ring: " + Selling.unitValue(gemRing, reg) + " vs " + (2000 + gem) * 1.4);
        ItemStack necklace = piece(h, ModItems.SMITH_NECKLACE.get(), new SmithData(List.of(ing("silvane", 50), ing("goldchro", 50)),
                Optional.of(new SmithData.Gem(HoleyDigsite.id("sapphire"), Selling.grams(sapphire)))));
        check(h, Selling.unitValue(necklace, reg) == (long) Math.floor((50 * 45 + 50 * 20 + gem) * 1.4 + 1e-9), "necklace: " + Selling.unitValue(necklace, reg));
        ItemStack dagger = piece(h, ModItems.SMITH_DAGGER.get(), new SmithData(List.of(ing("folkiron", 80)), Optional.empty()));
        check(h, Selling.unitValue(dagger, reg) == (long) Math.floor(80 * 6.25 * 1.25 + 1e-9), "dagger: " + Selling.unitValue(dagger, reg));
        ItemStack sword = piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("silvane", 200), ing("silvane", 200), ing("goldchro", 200)), Optional.empty()));
        check(h, Selling.unitValue(sword, reg) == (long) Math.floor((400 * 45 + 200 * 20) * 1.4 + 1e-9), "sword: " + Selling.unitValue(sword, reg));
        // the Sell Pad takes pieces but not a plain item without materials
        SellPadBlock pad = (SellPadBlock) ModBlocks.SELL_PAD.get();
        check(h, pad.accepts(sword, h.getLevel()) && pad.accepts(ring, h.getLevel()), "Sell Pad accepts pieces");
        check(h, !pad.accepts(new ItemStack(ModItems.SMITH_SWORD.get()), h.getLevel()), "a piece without materials is worth nothing");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void damagedToolsAreWorthLess(GameTestHelper h) {
        var reg = h.getLevel().registryAccess();
        ItemStack axe = piece(h, ModItems.SMITH_AXE.get(), new SmithData(List.of(ing("goldchro", 150), ing("goldchro", 150)), Optional.empty()));
        long full = Selling.unitValue(axe, reg);
        check(h, full == (long) Math.floor(300 * 20 * 1.3 + 1e-9), "axe new: " + full);
        axe.setDamageValue(axe.getMaxDamage() / 2);
        long half = Selling.unitValue(axe, reg);
        check(h, Math.abs(half - full / 2.0) <= full * 0.01, "half worn: " + half + " vs " + full);
        axe.setDamageValue(axe.getMaxDamage() - 1);
        check(h, Selling.unitValue(axe, reg) < full / 100, "nearly broken is worth almost nothing");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ tool stats

    @GameTest(template = "empty")
    public static void folkironIsIronAndSilvaneIsNetherite(GameTestHelper h) {
        ItemStack fs = piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("folkiron", 150), ing("folkiron", 150), ing("folkiron", 150)), Optional.empty()));
        check(h, Math.abs(attack(fs) - 5.0) <= 0.5 && Math.abs(fs.getMaxDamage() - 250) <= 50, "Folkiron sword ~ iron (5 / 250): " + attack(fs) + " / " + fs.getMaxDamage());
        ItemStack ss = piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("silvane", 200), ing("silvane", 200), ing("silvane", 200)), Optional.empty()));
        check(h, Math.abs(attack(ss) - 7.0) <= 0.7 && Math.abs(ss.getMaxDamage() - 2031) <= 300, "Silvane sword ~ netherite (7 / 2031): " + attack(ss) + " / " + ss.getMaxDamage());
        check(h, attack(ss) > 6.0 && ss.getMaxDamage() > 1561, "beyond diamond");
        ItemStack gold3 = piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("goldchro", 200), ing("goldchro", 200), ing("goldchro", 200)), Optional.empty()));
        check(h, attack(gold3) > 6.0 && gold3.getMaxDamage() > 1561, "Goldchro beyond diamond: " + attack(gold3) + " / " + gold3.getMaxDamage());
        // mixing averages by grams: 200 g Silvane + 100 g Folkiron + 100 g Folkiron lies between the pure swords
        ItemStack mix = piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("silvane", 200), ing("folkiron", 100), ing("folkiron", 100)), Optional.empty()));
        check(h, attack(mix) > attack(piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("folkiron", 133), ing("folkiron", 133), ing("folkiron", 134)), Optional.empty())))
                && attack(mix) < attack(ss), "mixed metals average");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heavierPiecesAreBetterUpToTheCap(GameTestHelper h) {
        double lastDamage = 0;
        int lastUses = 0;
        for (int g : new int[]{25, 50, 100, 150, 200, 250}) {
            ItemStack s = piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("silvane", g), ing("silvane", g), ing("silvane", g)), Optional.empty()));
            check(h, attack(s) > lastDamage && s.getMaxDamage() > lastUses, "heavier is better at " + g + " g: " + attack(s));
            lastDamage = attack(s);
            lastUses = s.getMaxDamage();
        }
        // above the cap (750 g total) nothing improves
        ItemStack cap = piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("silvane", 250), ing("silvane", 250), ing("silvane", 250)), Optional.empty()));
        ItemStack over = piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("silvane", 500), ing("silvane", 500), ing("silvane", 500)), Optional.empty()));
        check(h, attack(cap) == attack(over) && cap.getMaxDamage() == over.getMaxDamage(), "capped");
        // the lightest sword is still a (stone-level) weapon
        ItemStack light = piece(h, ModItems.SMITH_SWORD.get(), new SmithData(List.of(ing("folkiron", 25), ing("folkiron", 25), ing("folkiron", 25)), Optional.empty()));
        check(h, attack(light) >= 4.0 && light.getMaxDamage() >= 131, "lightest sword: " + attack(light) + " / " + light.getMaxDamage());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theAxeChopsWithTheSpeedOfItsMetals(GameTestHelper h) {
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        ItemStack iron = piece(h, ModItems.SMITH_AXE.get(), new SmithData(List.of(ing("folkiron", 150), ing("folkiron", 150)), Optional.empty()));
        float ironSpeed = iron.getDestroySpeed(log);
        check(h, Math.abs(ironSpeed - 6.0F) <= 1.0F, "Folkiron axe mines like iron: " + ironSpeed);
        ItemStack best = piece(h, ModItems.SMITH_AXE.get(), new SmithData(List.of(ing("silvane", 200), ing("silvane", 200)), Optional.empty()));
        float bestSpeed = best.getDestroySpeed(log);
        check(h, bestSpeed >= 8.5F && bestSpeed > ironSpeed, "the best axe mines like netherite: " + bestSpeed);
        check(h, iron.getDestroySpeed(Blocks.STONE.defaultBlockState()) <= 1.0F, "no pickaxe");
        check(h, iron.getMaxDamage() > 0 && iron.isDamageableItem(), "durability from the metals");
        h.succeed();
    }
}
