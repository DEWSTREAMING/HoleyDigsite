package com.holeydigsite.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@EventBusSubscriber(modid = HoleyDigsite.MODID)
public final class MachineRecipeManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static List<MachineRecipe> RECIPES = new ArrayList<>();

    private MachineRecipeManager() {}

    static {
        loadBuiltin();
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<List<MachineRecipe>>() {
            @Override
            protected List<MachineRecipe> prepare(ResourceManager manager, ProfilerFiller profiler) {
                return loadFrom(manager);
            }

            @Override
            protected void apply(List<MachineRecipe> loaded, ResourceManager manager, ProfilerFiller profiler) {
                RECIPES = List.copyOf(loaded);
            }
        });
    }

    public static List<MachineRecipe> all() {
        if (RECIPES.isEmpty()) loadBuiltin();
        return RECIPES;
    }

    public static Optional<MachineRecipe> find(ResourceLocation machineId, String mode, ItemStack stack) {
        if (RECIPES.isEmpty()) loadBuiltin();
        for (MachineRecipe r : RECIPES) {
            if (r.matches(machineId, mode, stack)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public static boolean isIngredient(ResourceLocation machineId, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (RECIPES.isEmpty()) loadBuiltin();
        for (MachineRecipe r : RECIPES) {
            if (r.matchesIngredient(machineId, stack)) {
                return true;
            }
        }
        return false;
    }

    /** True if any water machine takes this item, whichever machine it is. */
    public static boolean isAnyIngredient(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (RECIPES.isEmpty()) loadBuiltin();
        for (MachineRecipe r : RECIPES) {
            if (r.matchesIngredient(r.machine(), stack)) {
                return true;
            }
        }
        return false;
    }

    public static void setRecipesForTest(List<MachineRecipe> recipes) {
        RECIPES = List.copyOf(recipes);
    }

    public static void resetBuiltin() {
        loadBuiltin();
    }

    public static void loadBuiltin() {
        List<MachineRecipe> list = new ArrayList<>();
        String[] builtins = {
                "hemp_to_fibre.json",
                "fibre_to_rope.json",
                "log_to_beam.json",
                "log_to_pit_prop.json"
        };
        for (String file : builtins) {
            String path = "/data/holeydigsite/machine_recipes/" + file;
            try (var in = MachineRecipeManager.class.getResourceAsStream(path)) {
                if (in != null) {
                    try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        String base = file.replace(".json", "");
                        list.add(MachineRecipe.fromJson(HoleyDigsite.id(base), obj));
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load built-in recipe {}: {}", file, e.getMessage());
            }
        }
        RECIPES = List.copyOf(list);
    }

    public static List<MachineRecipe> loadFrom(ResourceManager manager) {
        List<MachineRecipe> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources("machine_recipes", p -> p.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String path = fileId.getPath();
                String name = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");
                ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), name);
                loaded.add(MachineRecipe.fromJson(recipeId, json));
            } catch (Exception e) {
                LOGGER.warn("Skipping invalid machine recipe {}: {}", fileId, e.getMessage());
            }
        }
        return loaded;
    }
}
