package com.holeydigsite.devtest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.forge.ForgeRecipes;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.item.BrushItem;
import com.holeydigsite.shop.ShopCatalogue;
import com.holeydigsite.sluice.SluiceBoxBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.TreeMap;

/**
 * Phase 15 tooling (sandbox only): dumps every holeydigsite block, item, stat, shop entry, Crafting Forge recipe and the
 * creative-tab order to {@code snapshot.json} (path: system property {@code holeydigsite.snapshot}, default
 * {@code snapshot.json}). Run it on the old and the new tree and diff the two files - the diff must be empty.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase15Snapshot {
    @GameTest(template = "empty", batch = "p15_snapshot", timeoutTicks = 100)
    public static void snapshot(GameTestHelper h) {
        try {
            JsonObject root = new JsonObject();
            TreeMap<String, JsonObject> blocks = new TreeMap<>();
            for (Block b : BuiltInRegistries.BLOCK) {
                var id = BuiltInRegistries.BLOCK.getKey(b);
                if (!id.getNamespace().equals(HoleyDigsite.MODID)) continue;
                JsonObject o = new JsonObject();
                BlockState d = b.defaultBlockState();
                o.addProperty("class", b.getClass().getName());
                o.addProperty("destroyTime", d.getDestroySpeed(h.getLevel(), net.minecraft.core.BlockPos.ZERO));
                o.addProperty("explosionResistance", b.getExplosionResistance());
                o.addProperty("mapColor", d.getMapColor(h.getLevel(), net.minecraft.core.BlockPos.ZERO).id);
                o.addProperty("sound", String.valueOf(d.getSoundType().getBreakSound().getLocation()));
                o.addProperty("pushReaction", d.getPistonPushReaction().name());
                o.addProperty("occludes", d.canOcclude());
                o.addProperty("defaultState", d.toString());
                int maxLight = 0;
                JsonArray props = new JsonArray();
                for (Property<?> p : b.getStateDefinition().getProperties()) {
                    props.add(p.getName() + "=" + p.getPossibleValues());
                }
                for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                    maxLight = Math.max(maxLight, s.getLightEmission());
                }
                o.add("properties", props);
                o.addProperty("states", b.getStateDefinition().getPossibleStates().size());
                JsonArray lights = new JsonArray();
                for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                    lights.add(s.getLightEmission());
                }
                o.add("lights", lights);
                JsonObject stats = new JsonObject();
                if (b instanceof MiningDrillBlock x) stats.addProperty("cycleTicks", x.cycleTicks());
                if (b instanceof SluiceBoxBlock x) stats.addProperty("sluice", x.stats().toString());
                if (b instanceof PanBlock x) stats.addProperty("maxFill", x.maxFill());
                if (b instanceof ClumpBeltBlock x) stats.addProperty("speed", x.speed());
                if (b instanceof CrucibleBlock x) {
                    stats.addProperty("capacity", x.capacity());
                    stats.addProperty("ticksPerGram", x.ticksPerGram());
                }
                if (b instanceof ForgeBlock x) stats.addProperty("heat", x.heat());
                if (b instanceof MoldBlock x) {
                    stats.addProperty("coolTicks", x.coolTicks());
                    stats.addProperty("targets", Arrays.toString(x.targets()));
                }
                o.add("stats", stats);
                blocks.put(id.toString(), o);
            }
            JsonObject bo = new JsonObject();
            blocks.forEach(bo::add);
            root.add("blocks", bo);

            TreeMap<String, JsonObject> items = new TreeMap<>();
            for (Item i : BuiltInRegistries.ITEM) {
                var id = BuiltInRegistries.ITEM.getKey(i);
                if (!id.getNamespace().equals(HoleyDigsite.MODID)) continue;
                JsonObject o = new JsonObject();
                ItemStack st = new ItemStack(i);
                o.addProperty("class", i.getClass().getName());
                o.addProperty("maxStack", st.getMaxStackSize());
                o.addProperty("rarity", st.getRarity().name());
                o.addProperty("components", i.components().toString());
                if (i instanceof BrushItem b) o.addProperty("brushCycle", b.cycleTicks());
                if (i instanceof net.minecraft.world.item.BlockItem bi) o.addProperty("block", String.valueOf(BuiltInRegistries.BLOCK.getKey(bi.getBlock())));
                items.put(id.toString(), o);
            }
            JsonObject io = new JsonObject();
            items.forEach(io::add);
            root.add("items", io);

            var access = h.getLevel().registryAccess();
            TreeMap<String, JsonObject> shop = new TreeMap<>();
            access.lookup(ShopCatalogue.SHOP_ITEMS).ifPresent(r -> r.listElements().forEach(e -> {
                JsonObject o = new JsonObject();
                o.addProperty("item", String.valueOf(BuiltInRegistries.ITEM.getKey(e.value().item())));
                o.addProperty("count", e.value().count());
                o.addProperty("price", e.value().price());
                shop.put(e.key().location().toString(), o);
            }));
            JsonObject so = new JsonObject();
            shop.forEach(so::add);
            root.add("shop", so);

            JsonArray recipes = new JsonArray();
            for (var e : ForgeRecipes.all(access)) {
                JsonObject o = new JsonObject();
                o.addProperty("id", e.id().toString());
                o.addProperty("order", e.recipe().order());
                o.addProperty("metals", e.recipe().metals().toString());
                StringBuilder parts = new StringBuilder();
                e.recipe().parts().forEach(p -> parts.append(BuiltInRegistries.ITEM.getKey(p.item())).append('x').append(p.count()).append(' '));
                o.addProperty("parts", parts.toString().trim());
                o.addProperty("result", BuiltInRegistries.ITEM.getKey(e.recipe().result().item()) + "x" + e.recipe().result().count());
                recipes.add(o);
            }
            root.add("forgeRecipes", recipes);

            CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(), true, access);
            JsonArray tab = new JsonArray();
            for (CreativeModeTab t : BuiltInRegistries.CREATIVE_MODE_TAB) {
                var tid = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(t);
                if (tid == null || !tid.getNamespace().equals(HoleyDigsite.MODID)) continue;
                for (ItemStack s : t.getDisplayItems()) {
                    tab.add(BuiltInRegistries.ITEM.getKey(s.getItem()) + " x" + s.getCount() + " " + s.getComponentsPatch());
                }
            }
            root.add("creativeTab", tab);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(Path.of(System.getProperty("holeydigsite.snapshot", "snapshot.json")), gson.toJson(root), StandardCharsets.UTF_8);
            h.succeed();
        } catch (Exception e) {
            e.printStackTrace();
            h.fail("snapshot failed: " + e);
        }
    }
}
