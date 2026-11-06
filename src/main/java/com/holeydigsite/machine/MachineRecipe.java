package com.holeydigsite.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data-driven recipe for water-powered machines (Threshing Drum, Rope Machine, Sawmill).
 */
public class MachineRecipe {
    public record IngredientEntry(@Nullable Item item, @Nullable TagKey<Item> tag, int count) {
        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            if (tag != null) {
                return stack.is(tag);
            }
            return item != null && stack.is(item);
        }
    }

    private final ResourceLocation id;
    private final ResourceLocation machine;
    private final String mode;
    private final List<IngredientEntry> ingredients;
    private final ItemStack result;
    private final int ticks;

    public MachineRecipe(ResourceLocation id, ResourceLocation machine, String mode, List<IngredientEntry> ingredients, ItemStack result, int ticks) {
        this.id = id;
        this.machine = machine;
        this.mode = mode == null ? "" : mode;
        this.ingredients = Collections.unmodifiableList(ingredients);
        this.result = result;
        this.ticks = ticks;
    }

    public ResourceLocation id() { return id; }
    public ResourceLocation machine() { return machine; }
    public String mode() { return mode; }
    public List<IngredientEntry> ingredients() { return ingredients; }
    public ItemStack result() { return result.copy(); }
    public int ticks() { return ticks; }

    public boolean matches(ResourceLocation machineId, String currentMode, ItemStack stack) {
        if (!this.machine.equals(machineId)) return false;
        if (!this.mode.isEmpty() && !this.mode.equalsIgnoreCase(currentMode)) return false;
        for (IngredientEntry ing : ingredients) {
            if (ing.matches(stack)) return true;
        }
        return false;
    }

    public boolean matchesIngredient(ResourceLocation machineId, ItemStack stack) {
        if (!this.machine.equals(machineId)) return false;
        for (IngredientEntry ing : ingredients) {
            if (ing.matches(stack)) return true;
        }
        return false;
    }

    public static MachineRecipe fromJson(ResourceLocation id, JsonObject json) {
        ResourceLocation machine = ResourceLocation.parse(GsonHelper.getAsString(json, "machine"));
        String mode = GsonHelper.getAsString(json, "mode", "");
        int ticks = GsonHelper.getAsInt(json, "ticks", 20);

        List<IngredientEntry> ingredients = new ArrayList<>();
        if (json.has("ingredients")) {
            JsonArray arr = json.getAsJsonArray("ingredients");
            for (JsonElement el : arr) {
                ingredients.add(parseIngredient(el.getAsJsonObject()));
            }
        } else if (json.has("ingredient")) {
            ingredients.add(parseIngredient(json.getAsJsonObject("ingredient")));
        } else {
            throw new IllegalArgumentException("Recipe " + id + " has neither ingredient nor ingredients");
        }

        JsonObject resObj = GsonHelper.getAsJsonObject(json, "result");
        String resItemStr = GsonHelper.getAsString(resObj, "item");
        int resCount = GsonHelper.getAsInt(resObj, "count", 1);
        Item resItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(resItemStr));
        if (resItem == Items.AIR) {
            throw new IllegalArgumentException("Unknown result item " + resItemStr + " in recipe " + id);
        }
        ItemStack result = new ItemStack(resItem, resCount);

        return new MachineRecipe(id, machine, mode, ingredients, result, ticks);
    }

    private static IngredientEntry parseIngredient(JsonObject obj) {
        int count = GsonHelper.getAsInt(obj, "count", 1);
        if (obj.has("tag")) {
            String tagStr = GsonHelper.getAsString(obj, "tag");
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagStr));
            return new IngredientEntry(null, tag, count);
        } else if (obj.has("item")) {
            String itemStr = GsonHelper.getAsString(obj, "item");
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemStr));
            if (item == Items.AIR) {
                throw new IllegalArgumentException("Unknown ingredient item: " + itemStr);
            }
            return new IngredientEntry(item, null, count);
        } else {
            throw new IllegalArgumentException("Ingredient must specify item or tag");
        }
    }
}
