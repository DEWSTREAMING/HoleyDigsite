package com.holeydigsite.contract;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.forge.Metals;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/** The words on a contract card and in chat. Plain components, so the server and the client build the same text. */
public final class ContractText {
    private ContractText() {}

    /** Name of a metal or gem material ("Folkiron", "Ruby"). */
    public static Component material(ResourceLocation id) {
        return Metals.name(id);
    }

    private static Component itemName(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item.getDescription();
    }

    /** What is wanted, e.g. "300 g of Folkiron ingots" or "3x cut Sapphire (2 g+)". */
    public static MutableComponent want(Contract c) {
        switch (c.kind) {
            case METAL:
                if (c.min > 0) {
                    return Component.translatable("contract.holeydigsite.want.metal_min", c.amount, material(c.item), c.min);
                }
                return Component.translatable("contract.holeydigsite.want.metal", c.amount, material(c.item));
            case GEM: {
                Component form = Component.translatable("contract.holeydigsite.form." + (c.form.isEmpty() ? "cut" : c.form));
                if (c.min > 1) {
                    return Component.translatable("contract.holeydigsite.want.gem_min", c.amount, form, material(c.item), c.min);
                }
                return Component.translatable("contract.holeydigsite.want.gem", c.amount, form, material(c.item));
            }
            case FOOD:
                return Component.translatable("contract.holeydigsite.want.food", c.amount, itemName(c.item));
            default: {
                Component piece = itemName(c.item);
                Component base = c.metal == null ? piece
                        : Component.translatable("item.holeydigsite.smith_named", material(c.metal), piece);
                MutableComponent out = Component.translatable("contract.holeydigsite.want.piece", c.amount, base);
                if (c.gem != null) {
                    out.append(Component.translatable(c.gem.equals("any") ? "contract.holeydigsite.with_any_gem" : "contract.holeydigsite.with_gem",
                            c.gem.equals("any") ? Component.empty() : material(ResourceLocation.parse(c.gem))));
                }
                if (c.min > 0) {
                    out.append(Component.translatable("contract.holeydigsite.piece_min", c.min));
                }
                return out;
            }
        }
    }

    /** "Have 120 / 300 g" or "Have 2 / 3". */
    public static MutableComponent progress(Contract c) {
        return Component.translatable(c.kind == ContractKind.METAL ? "contract.holeydigsite.progress_g" : "contract.holeydigsite.progress",
                c.delivered(), c.amount);
    }

    /** "2 days left", "Last day". */
    public static MutableComponent daysLeft(Contract c, long day, int days) {
        int left = c.daysLeft(day, days);
        if (left <= 1) {
            return Component.translatable("contract.holeydigsite.last_day");
        }
        return Component.translatable("contract.holeydigsite.days_left", left);
    }

    /** "Pays 3 gold 4 silver". */
    public static MutableComponent pays(Contract c, int maxChars) {
        return Component.translatable("contract.holeydigsite.pays", Selling.format(c.reward, maxChars));
    }

    /** The lines of one card for the board: what, progress, pays and days. */
    public static List<Component> card(Contract c, long day, int days) {
        List<Component> lines = new ArrayList<>();
        if (c == null) {
            lines.add(Component.translatable("contract.holeydigsite.empty"));
            return lines;
        }
        if (c.isDone()) {
            lines.add(want(c));
            lines.add(Component.translatable("contract.holeydigsite.done"));
            lines.add(Component.translatable("contract.holeydigsite.new_tomorrow"));
            return lines;
        }
        lines.add(want(c));
        lines.add(progress(c));
        lines.add(Component.empty().append(pays(c, 24)).append(" | ").append(daysLeft(c, day, days)));
        return lines;
    }

    /** Full details for chat. */
    public static List<Component> details(Contract c, long day, int days) {
        List<Component> lines = new ArrayList<>();
        lines.add(want(c));
        if (c.isDone()) {
            lines.add(Component.translatable("contract.holeydigsite.done"));
            return lines;
        }
        lines.add(progress(c));
        lines.add(Component.translatable("contract.holeydigsite.detail_pays", Selling.format(c.reward), c.renown));
        lines.add(daysLeft(c, day, days));
        return lines;
    }
}
