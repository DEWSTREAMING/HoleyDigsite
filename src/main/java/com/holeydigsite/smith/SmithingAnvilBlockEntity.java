package com.holeydigsite.smith;

import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Contents of a Smithing Anvil: up to {@link AnvilRecipe#MAX_INGOTS} whole ingots and one cut gem in the order they were
 * laid, the selected recipe and the valid hammer hits so far. Any change to the
 * contents or the recipe restarts the hits. Never ticks, and nothing is lost since take-back and breaking return every item.
 */
public class SmithingAnvilBlockEntity extends BlockEntity {
    public static final int MAX_ITEMS = AnvilRecipe.MAX_INGOTS + 1;

    private final List<ItemStack> items = new ArrayList<>();
    @Nullable
    private ResourceLocation selected;
    private int hits;
    /** Bumped on every data update so the renderer knows when to rebuild its cached text. */
    private int clientVersion;

    public SmithingAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMITHING_ANVIL.get(), pos, state);
    }

    // ------------------------------------------------------------------------------------------------ queries

    /** Everything laid on the anvil in laying order (read-only). */
    public List<ItemStack> items() {
        return Collections.unmodifiableList(items);
    }

    public List<ItemStack> ingots() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : items) {
            if (isIngot(s)) {
                out.add(s);
            }
        }
        return out;
    }

    public int ingotCount() {
        return ingots().size();
    }

    public Optional<ItemStack> gem() {
        for (ItemStack s : items) {
            if (!isIngot(s)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int hits() {
        return hits;
    }

    public int clientVersion() {
        return clientVersion;
    }

    private static boolean isIngot(ItemStack s) {
        return s.getItem() instanceof IngotItem;
    }

    /** What the contents are as a {@link SmithData}. */
    public SmithData data() {
        List<SmithData.Ingot> ingots = new ArrayList<>();
        for (ItemStack s : ingots()) {
            ResourceInfo info = Selling.resource(s);
            if (info != null) {
                ingots.add(new SmithData.Ingot(info.material(), Selling.grams(s)));
            }
        }
        Optional<SmithData.Gem> gem = gem().map(g -> {
            ResourceInfo info = Selling.resource(g);
            return new SmithData.Gem(info.material(), Selling.grams(g));
        });
        return new SmithData(ingots, gem);
    }

    // ------------------------------------------------------------------------------------------------ recipe

    private HolderLookup.Provider registries() {
        return level == null ? null : level.registryAccess();
    }

    /** The selected recipe, falling back to the first one. */
    @Nullable
    public AnvilRecipes.Entry recipe() {
        List<AnvilRecipes.Entry> all = AnvilRecipes.all(registries());
        for (AnvilRecipes.Entry e : all) {
            if (e.id().equals(selected)) {
                return e;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }

    /** Selects the next or previous recipe and restarts the hits. */
    @Nullable
    public AnvilRecipes.Entry cycle(int step) {
        List<AnvilRecipes.Entry> all = AnvilRecipes.all(registries());
        if (all.isEmpty()) {
            return null;
        }
        AnvilRecipes.Entry cur = recipe();
        int i = cur == null ? 0 : all.indexOf(cur);
        AnvilRecipes.Entry next = all.get(Math.floorMod(i + step, all.size()));
        selected = next.id();
        hits = 0;
        changed();
        return next;
    }

    /** Selects a recipe by id. */
    public void select(ResourceLocation id) {
        selected = id;
        hits = 0;
        changed();
    }

    // ------------------------------------------------------------------------------------------------ laying things on

    /** Why an item can't be laid on the anvil, or {@link Refusal#NONE}. */
    public enum Refusal { NONE, NOT_ACCEPTED, UNCUT, UNKNOWN_METAL, LIGHT, FULL, SECOND_GEM, EVENT_ITEM }

    public Refusal check(ItemStack stack) {
        if (com.holeydigsite.event.EventDefs.isEventMetal(stack) || com.holeydigsite.event.EventDefs.isEventGem(stack)
                || com.holeydigsite.event.EventDefs.isEventItem(stack)) {
            return Refusal.EVENT_ITEM;
        }
        ResourceInfo info = Selling.resource(stack);
        if (stack.isEmpty() || info == null) {
            return Refusal.NOT_ACCEPTED;
        }
        if (info.isUncut()) {
            return Refusal.UNCUT;
        }
        if (isIngot(stack) && info.isIngot()) {
            SmithMaterial m = SmithMaterial.get(registries(), info.material());
            if (m == null || !m.isMetal()) {
                return Refusal.UNKNOWN_METAL;
            }
            if (ingotCount() >= AnvilRecipe.MAX_INGOTS) {
                return Refusal.FULL;
            }
            AnvilRecipes.Entry e = recipe();
            if (e != null && Selling.grams(stack) < e.recipe().minGrams()) {
                return Refusal.LIGHT;
            }
            return Refusal.NONE;
        }
        if (!info.meltable() && ResourceInfo.RAW.equals(info.form())) {
            return gem().isPresent() ? Refusal.SECOND_GEM : Refusal.NONE;
        }
        return Refusal.NOT_ACCEPTED;
    }

    /** Lays one item after a successful check. */
    public boolean lay(ItemStack stack) {
        if (check(stack) != Refusal.NONE) {
            return false;
        }
        items.add(stack.copyWithCount(1));
        hits = 0;
        changed();
        return true;
    }

    /** Takes the item laid last and restarts the hits. */
    public ItemStack takeLast() {
        if (items.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack s = items.remove(items.size() - 1);
        hits = 0;
        changed();
        return s;
    }

    // ------------------------------------------------------------------------------------------------ hammering

    /** What is wrong with the contents for a recipe. */
    public enum Problem { NONE, FEW_INGOTS, MANY_INGOTS, LIGHT, NEEDS_GEM, NO_GEM }

    /** Checks the contents against a recipe. */
    public Problem problem(AnvilRecipe recipe) {
        List<ItemStack> ingots = ingots();
        if (ingots.size() < recipe.ingots()) {
            return Problem.FEW_INGOTS;
        }
        if (ingots.size() > recipe.ingots()) {
            return Problem.MANY_INGOTS;
        }
        for (ItemStack s : ingots) {
            if (Selling.grams(s) < recipe.minGrams()) {
                return Problem.LIGHT;
            }
        }
        boolean gem = gem().isPresent();
        if (recipe.gem() == AnvilRecipe.GemRule.REQUIRED && !gem) {
            return Problem.NEEDS_GEM;
        }
        if (recipe.gem() == AnvilRecipe.GemRule.NONE && gem) {
            return Problem.NO_GEM;
        }
        return Problem.NONE;
    }

    /** Counts one valid hit and returns the hits so far. */
    int addHit() {
        hits++;
        changed();
        return hits;
    }

    void resetHits() {
        if (hits != 0) {
            hits = 0;
            changed();
        }
    }

    /** The finished piece for a recipe, using up the contents. */
    ItemStack finish(AnvilRecipe recipe) {
        ItemStack piece = SmithPieces.create(recipe.result(), data(), registries());
        items.clear();
        hits = 0;
        changed();
        return piece;
    }

    // ------------------------------------------------------------------------------------------------ breaking

    /** Everything laid on the anvil, emptied. */
    public List<ItemStack> dropContents() {
        List<ItemStack> out = new ArrayList<>(items);
        items.clear();
        hits = 0;
        return out;
    }

    // ------------------------------------------------------------------------------------------------ sync / save

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void write(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack s : items) {
            list.add(s.save(registries));
        }
        tag.put("Items", list);
        if (selected != null) {
            tag.putString("Recipe", selected.toString());
        }
        tag.putInt("Hits", hits);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        for (Tag t : tag.getList("Items", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(items::add);
        }
        selected = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
        hits = Math.max(0, tag.getInt("Hits"));
        clientVersion++;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
