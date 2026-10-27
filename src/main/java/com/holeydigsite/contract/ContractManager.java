package com.holeydigsite.contract;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.ResourceInfo;
import com.holeydigsite.economy.SellValue;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.IngotItem;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerDefs;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.ledger.LedgerNetwork;
import com.holeydigsite.ledger.ModTriggers;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.smith.AnvilRecipe;
import com.holeydigsite.smith.AnvilRecipes;
import com.holeydigsite.smith.SmithData;
import com.holeydigsite.smith.SmithPieces;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the contracts: rolling from the pool, the daily rotation, matching and taking delivered items, and the rewards.
 * Nothing ticks: a player's contracts are brought up to date when they use or look at a Contract Board.
 */
public final class ContractManager {
    private ContractManager() {}

    /** Metal and gem used to price a piece that does not name one. */
    private static final ResourceLocation FALLBACK_METAL = HoleyDigsite.id("folkiron");
    private static final ResourceLocation FALLBACK_GEM = HoleyDigsite.id("ruby");
    private static final long DAY_TICKS = 24000L;

    /** What a player can be asked for: their Ledger level and the plots they own. */
    public record Eligibility(int level, Set<String> plots) {}

    public static ContractData data(net.minecraft.world.entity.player.Player player) {
        return LedgerData.get(player).contracts();
    }

    /** The in-game day number (the overworld's day time divided by 24000). */
    public static long today(MinecraftServer server) {
        return Math.max(0, server.overworld().getDayTime() / DAY_TICKS);
    }

    public static Eligibility eligibility(ServerPlayer sp) {
        Set<String> plots = new HashSet<>();
        for (PlotData.Plot p : PlotData.get(sp.getServer().overworld()).all()) {
            if (p.owned()) {
                plots.add(p.id());
            }
        }
        return new Eligibility(LedgerData.get(sp).level(), plots);
    }

    // ------------------------------------------------------------------------------------------------ rolling

    static String keyOf(ContractDefs.Entry e) {
        return e.kind().id() + ":" + e.item() + (e.metal() == null ? "" : "|" + e.metal());
    }

    /** Copper value of what the entry asks for at this amount (before the reward multiplier). */
    public static double value(ContractDefs.Entry e, int amount, HolderLookup.Provider reg) {
        switch (e.kind()) {
            case METAL: {
                SellValue p = Selling.price(reg, e.item());
                return p == null ? 0 : amount * p.perGram(true);
            }
            case GEM: {
                SellValue p = Selling.price(reg, e.item());
                return p == null ? 0 : amount * Math.max(1, e.min()) * p.perGram("uncut".equals(e.form()) ? ResourceInfo.UNCUT : ResourceInfo.RAW);
            }
            case FOOD: {
                Item item = BuiltInRegistries.ITEM.get(e.item());
                return (double) amount * Selling.unitValue(new ItemStack(item), reg);
            }
            default: {
                SellValue metal = Selling.price(reg, e.metal() != null ? e.metal() : FALLBACK_METAL);
                double sum = metal == null ? 0 : e.min() * metal.perGram(true);
                if (e.gem() != null) {
                    SellValue gem = Selling.price(reg, "any".equals(e.gem()) ? FALLBACK_GEM : ResourceLocation.parse(e.gem()));
                    sum += gem == null ? 0 : gem.perGram(false);
                }
                AnvilRecipe recipe = AnvilRecipes.forItem(reg, BuiltInRegistries.ITEM.get(e.item()));
                double bonus = recipe == null ? 0 : recipe.bonusFor(e.gem() != null);
                return amount * sum * (1 + bonus);
            }
        }
    }

    /** The coin reward: the value times the entry's multiplier, rounded to the rules' step. */
    public static long reward(ContractDefs.Entry e, int amount, HolderLookup.Provider reg) {
        double v = value(e, amount, reg) * e.reward();
        if (v <= 0) {
            return 0;
        }
        ContractDefs.Rules rules = ContractDefs.rules();
        long r = Math.round(v / rules.rewardStep()) * rules.rewardStep();
        return Math.max(rules.rewardMin(), r);
    }

    /** Rolls one contract from the pool, or null if nothing fits (every eligible kind is already open, or the pool is empty). */
    @Nullable
    public static Contract roll(HolderLookup.Provider reg, Eligibility el, Set<String> openKeys, RandomSource rng, long day) {
        List<ContractDefs.Entry> pool = new ArrayList<>();
        for (ContractDefs.Entry e : ContractDefs.entries()) {
            if (e.eligible(el.level(), el.plots()) && e.weight() > 0 && !openKeys.contains(keyOf(e))) {
                pool.add(e);
            }
        }
        while (!pool.isEmpty()) {
            int total = 0;
            for (ContractDefs.Entry e : pool) total += e.weight();
            int pick = rng.nextInt(total);
            ContractDefs.Entry chosen = pool.get(pool.size() - 1);
            for (ContractDefs.Entry e : pool) {
                pick -= e.weight();
                if (pick < 0) {
                    chosen = e;
                    break;
                }
            }
            int steps = Math.max(0, (chosen.amountMax() - chosen.amountMin()) / chosen.step());
            int amount = chosen.amountMin() + rng.nextInt(steps + 1) * chosen.step();
            long reward = reward(chosen, amount, reg);
            if (reward > 0) {
                return new Contract(chosen.id(), chosen.kind(), chosen.item(), chosen.form(), chosen.metal(), chosen.gem(),
                        amount, chosen.min(), reward, chosen.renown(), day);
            }
            pool.remove(chosen);
        }
        return null;
    }

    /** Mixes a seed so that neighbouring days or slots give unrelated numbers (the game's random source does not). */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static RandomSource rngFor(UUID id, long day, int slot, int reroll) {
        long h = mix(id.getMostSignificantBits() ^ mix(id.getLeastSignificantBits()));
        h = mix(h + day);
        h = mix(h + slot);
        h = mix(h + reroll);
        return RandomSource.create(h);
    }

    private static Set<String> openKeys(ContractData d, int except) {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < d.size(); i++) {
            Contract c = d.get(i);
            if (i != except && c != null && !c.isDone()) {
                keys.add(c.key());
            }
        }
        return keys;
    }

    // ------------------------------------------------------------------------------------------------ rotation

    /** Brings the player's contracts up to date for {@code day}. Returns true if anything changed (and syncs). */
    public static boolean refresh(ServerPlayer sp, long day) {
        ContractData d = data(sp);
        ContractDefs.Rules rules = ContractDefs.rules();
        boolean changed = false;
        boolean newDay = d.lastDay() < day;
        if (d.lastDay() > day) {
            for (int i = 0; i < d.size(); i++) {
                Contract c = d.get(i);
                if (c != null && c.issued > day) {
                    d.set(i, rebased(c, day));
                }
            }
            d.setLastDay(day);
            changed = true;
        }
        Eligibility el = null;
        for (int i = 0; i < Math.min(rules.slots(), d.size()); i++) {
            Contract c = d.get(i);
            boolean replace = c == null || (newDay && (c.isDone() || c.age(day) >= rules.days()));
            if (!replace) continue;
            if (el == null) el = eligibility(sp);
            Contract next = roll(sp.registryAccess(), el, openKeys(d, i), rngFor(sp.getUUID(), day, i, d.rerolls()), day);
            if (next != c) {
                d.set(i, next);
                changed = true;
            }
        }
        if (newDay) {
            d.setLastDay(day);
            changed = true;
        }
        if (changed) {
            LedgerNetwork.syncToClient(sp);
        }
        return changed;
    }

    public static boolean refresh(ServerPlayer sp) {
        return refresh(sp, today(sp.getServer()));
    }

    private static Contract rebased(Contract c, long day) {
        Contract n = new Contract(c.entry, c.kind, c.item, c.form, c.metal, c.gem, c.amount, c.min, c.reward, c.renown, day);
        n.add(c.delivered());
        return n;
    }

    /** Test command: starts a new day for this player (everything ages one day, then rotates). */
    public static void nextDay(ServerPlayer sp) {
        ContractData d = data(sp);
        long day = d.lastDay() < 0 ? today(sp.getServer()) : d.lastDay() + 1;
        refresh(sp, day);
    }

    /** Command: replaces all three contracts with new rolls, unfinished or not. */
    public static void rerollAll(ServerPlayer sp) {
        ContractData d = data(sp);
        long day = today(sp.getServer());
        int reroll = d.nextReroll();
        Eligibility el = eligibility(sp);
        for (int i = 0; i < d.size(); i++) {
            d.set(i, null);
        }
        for (int i = 0; i < Math.min(ContractDefs.rules().slots(), d.size()); i++) {
            d.set(i, roll(sp.registryAccess(), el, openKeys(d, i), rngFor(sp.getUUID(), day, i, reroll), day));
        }
        d.setLastDay(day);
        LedgerNetwork.syncToClient(sp);
    }

    // ------------------------------------------------------------------------------------------------ matching

    /** How much one item of the stack counts towards the contract (grams for metal, 1 otherwise), 0 if it does not fit. */
    public static int unitValue(Contract c, ItemStack stack, HolderLookup.Provider reg) {
        if (stack.isEmpty() || c.isDone()) {
            return 0;
        }
        switch (c.kind) {
            case METAL: {
                ResourceInfo info = Selling.resource(stack);
                if (!(stack.getItem() instanceof IngotItem) || info == null || !info.isIngot() || !info.material().equals(c.item)) {
                    return 0;
                }
                int grams = Selling.grams(stack);
                return grams >= Math.max(1, c.min) ? grams : 0;
            }
            case GEM: {
                ResourceInfo info = Selling.resource(stack);
                if (info == null || info.isIngot() || info.isCrop() || !info.material().equals(c.item)) {
                    return 0;
                }
                boolean wantUncut = "uncut".equals(c.form);
                if (info.isUncut() != wantUncut) {
                    return 0;
                }
                return Selling.grams(stack) >= c.min ? 1 : 0;
            }
            case FOOD:
                return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(c.item) ? 1 : 0;
            default: {
                if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(c.item)) {
                    return 0;
                }
                SmithData sd = SmithPieces.data(stack);
                if (sd == null || sd.ingotGrams() < c.min) {
                    return 0;
                }
                if (c.metal != null && !c.metal.equals(sd.mainMetal())) {
                    return 0;
                }
                if (c.gem != null) {
                    if (sd.gem().isEmpty()) return 0;
                    if (!c.gem.equals("any") && !sd.gem().get().material().toString().equals(c.gem)) return 0;
                }
                return 1;
            }
        }
    }

    /** The first open slot the stack fits, or -1. */
    public static int firstFit(ContractData d, ItemStack stack, HolderLookup.Provider reg) {
        for (int i = 0; i < d.size(); i++) {
            Contract c = d.get(i);
            if (c != null && unitValue(c, stack, reg) > 0) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------------------------------------ delivering

    /**
     * Takes what the contract still needs from {@code stack} (never more; ingots are whole). Returns how many items were taken.
     * A finished contract pays out at once.
     */
    public static int deliver(ServerPlayer sp, int slot, ItemStack stack) {
        ContractData d = data(sp);
        Contract c = d.get(slot);
        if (c == null || c.isDone()) {
            return 0;
        }
        int per = unitValue(c, stack, sp.registryAccess());
        if (per <= 0) {
            return 0;
        }
        int n = Math.min(stack.getCount(), (c.remaining() + per - 1) / per);
        stack.shrink(n);
        boolean finished = c.add(n * per);
        if (finished) {
            complete(sp, c, sp.getRandom());
        } else {
            sp.displayClientMessage(Component.translatable(c.kind == ContractKind.METAL
                    ? "message.holeydigsite.contract.progress_g" : "message.holeydigsite.contract.progress", c.delivered(), c.amount)
                    .withStyle(ChatFormatting.YELLOW), true);
            LedgerNetwork.syncToClient(sp);
        }
        return n;
    }

    /** Command: finishes the slot's contract and pays it. */
    public static boolean forceComplete(ServerPlayer sp, int slot) {
        Contract c = data(sp).get(slot);
        if (c == null || c.isDone()) {
            return false;
        }
        c.markDone();
        complete(sp, c, sp.getRandom());
        return true;
    }

    // ------------------------------------------------------------------------------------------------ rewards

    /** Which crate a finished contract of this reward size gives, or null. */
    @Nullable
    public static Item crateFor(long reward, RandomSource rng) {
        ContractDefs.CrateRule rule = null;
        for (ContractDefs.CrateRule r : ContractDefs.rules().crates()) {
            if (reward >= r.minReward()) rule = r;
        }
        if (rule == null) {
            return null;
        }
        if (rule.iron() > 0 && rng.nextDouble() < rule.iron()) {
            return ModItems.IRON_CRATE.get();
        }
        if (rule.wooden() > 0 && rng.nextDouble() < rule.wooden()) {
            return ModItems.WOODEN_CRATE.get();
        }
        return null;
    }

    /** Pays a finished contract: coins (biggest first), Renown under the cap, maybe a crate, then the Ledger counters. */
    public static void complete(ServerPlayer sp, Contract c, RandomSource rng) {
        ContractData d = data(sp);
        LedgerData ledger = LedgerData.get(sp);
        for (ItemStack coins : Selling.coins(c.reward)) {
            LedgerManager.giveOrDrop(sp, coins);
        }
        int oldLevel = ledger.level();
        int renown = ledger.addContractRenown(c.renown, LedgerDefs.curve().contract());
        if (ledger.level() > oldLevel) {
            LedgerManager.notifyLevelUp(sp, ledger.level());
        }
        Item crate = crateFor(c.reward, rng);
        if (crate != null) {
            LedgerManager.giveOrDrop(sp, new ItemStack(crate));
        }
        d.setCompleted(d.completed() + 1);

        sp.displayClientMessage(Component.translatable("message.holeydigsite.contract.done", Selling.format(c.reward, 30))
                .withStyle(ChatFormatting.GOLD), true);
        MutableComponent chat = Component.translatable("chat.holeydigsite.contract.reward", Selling.format(c.reward), renown);
        if (crate != null) {
            chat.append(Component.translatable("chat.holeydigsite.contract.crate", crate.getDescription()));
        }
        sp.sendSystemMessage(chat.withStyle(ChatFormatting.GOLD));

        ModTriggers.CONTRACTS_DONE.get().trigger(sp, d.completed());
        for (String title : LedgerDefs.contractTitles()) {
            if (d.completed() >= 50 && ledger.unlockTitle(title)) {
                sp.displayClientMessage(Component.translatable("message.holeydigsite.ledger.title_unlocked",
                        Component.translatable(LedgerDefs.titleKey(title))).withStyle(ChatFormatting.GOLD), true);
            }
        }
        LedgerNetwork.syncToClient(sp);
    }
}
