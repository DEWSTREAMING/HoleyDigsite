package com.holeydigsite.site;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Op commands for sites:
 * <ul>
 *   <li>{@code /holeydigsite store rebuild [home|<plot>|all]}, rebuild the home store or a plot's kiosk now</li>
 *   <li>{@code /holeydigsite store status}, show every site's id, anchor, and built version</li>
 *   <li>{@code /holeydigsite plot rebuild <plot>}, rebuild an unowned plot</li>
 *   <li>{@code /holeydigsite site place <site>}, build a site at your feet</li>
 *   <li>{@code /holeydigsite site export <site> <name>}, save the nearest such site's zone as
 *       {@code <world>/generated/holeydigsite/structures/<name>.nbt} plus {@code <name>_layout.json}</li>
 * </ul>
 */
public final class SiteCommands {
    private SiteCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("holeydigsite").requires(s -> s.hasPermission(2))
                .then(Commands.literal("store")
                        .then(Commands.literal("rebuild")
                                .executes(c -> rebuildStores(c, "all"))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(storeTargets(), b))
                                        .executes(c -> rebuildStores(c, StringArgumentType.getString(c, "target")))))
                        .then(Commands.literal("status").executes(SiteCommands::status)))
                .then(Commands.literal("plot")
                        .then(Commands.literal("rebuild")
                                .then(Commands.argument("plot", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(plotTargets(), b))
                                        .executes(c -> rebuildPlot(c, StringArgumentType.getString(c, "plot"))))))
                .then(Commands.literal("site")
                        .then(Commands.literal("fixup").executes(SiteCommands::fixup))
                        .then(Commands.literal("place")
                                .then(Commands.argument("site", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(SiteDefs.all().stream().map(SiteDef::id), b))
                                        .executes(SiteCommands::place)))
                        .then(Commands.literal("export")
                                .then(Commands.argument("site", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(SiteDefs.all().stream().map(SiteDef::id), b))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(SiteCommands::export))))));
    }

    private static List<String> storeTargets() {
        List<String> out = new ArrayList<>(List.of("all"));
        for (SiteDef d : SiteDefs.all()) {
            if (d.kind() == SiteDef.Kind.STORE) {
                out.add(d.plot() != null ? d.plot() : d.id().getPath());
            }
        }
        return out;
    }

    private static List<String> plotTargets() {
        return SiteDefs.all().stream().filter(d -> d.kind() == SiteDef.Kind.PLOT && d.plot() != null).map(SiteDef::plot).toList();
    }

    /** Store or kiosk sites matching home, a plot id, a full id, or all. */
    static boolean matchesStore(SiteDef def, String target) {
        if (def.kind() != SiteDef.Kind.STORE) {
            return false;
        }
        return target.equals("all") || def.id().getPath().equals(target) || def.id().toString().equals(target)
                || target.equals(def.plot());
    }

    /** Rebuilds every matching store site in the source's level. */
    public static int rebuildStores(ServerLevel level, String target) {
        int n = 0;
        SiteData data = SiteData.getIfExists(level);
        if (data == null) {
            return 0;
        }
        for (SiteData.Site s : new ArrayList<>(data.all())) {
            SiteDef def = SiteDefs.get(s.id());
            if (def != null && matchesStore(def, target) && Sites.build(level, def, s.anchor(), s.parent(), true).built()) {
                n++;
            }
        }
        return n;
    }

    private static int rebuildStores(CommandContext<CommandSourceStack> c, String target) {
        int n = rebuildStores(c.getSource().getLevel(), target);
        if (n == 0) {
            c.getSource().sendFailure(Component.literal("No store matches '" + target + "' in this dimension"));
        } else {
            c.getSource().sendSuccess(() -> Component.literal("Rebuilt " + n + " store site(s) (" + target + ")"), true);
        }
        return n;
    }

    private static int rebuildPlot(CommandContext<CommandSourceStack> c, String plot) {
        ServerLevel level = c.getSource().getLevel();
        SiteData data = SiteData.getIfExists(level);
        int n = 0, owned = 0;
        if (data != null) {
            for (SiteData.Site s : new ArrayList<>(data.all())) {
                SiteDef def = SiteDefs.get(s.id());
                if (def == null || def.kind() != SiteDef.Kind.PLOT || !plot.equals(def.plot())) {
                    continue;
                }
                if (Sites.plotOwned(level, def, s.anchor())) {
                    owned++;
                } else if (Sites.build(level, def, s.anchor(), s.parent(), true).built()) {
                    n++;
                }
            }
        }
        final int built = n, kept = owned;
        c.getSource().sendSuccess(() -> Component.literal("Rebuilt " + built + " plot(s); " + kept + " owned plot(s) left alone"), true);
        return n;
    }

    /** Re-applies every shipped world fix-up, with details in the log. */
    private static int fixup(CommandContext<CommandSourceStack> c) {
        int n = 0;
        for (String name : WorldFixups.ALL) {
            n += WorldFixups.applyAll(c.getSource().getServer(), name);
        }
        int applied = n;
        c.getSource().sendSuccess(() -> Component.literal("World fix-ups: " + applied + " groups applied (details in the log)"), true);
        return n;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        SiteData data = SiteData.getIfExists(c.getSource().getLevel());
        if (data == null || data.all().isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No sites in this dimension"), false);
            return 0;
        }
        for (SiteData.Site s : data.all()) {
            SiteDef def = SiteDefs.get(s.id());
            String v = def == null ? "no definition" : "mod version " + def.version();
            c.getSource().sendSuccess(() -> Component.literal(s.id() + " @ " + s.anchor().toShortString() + ": built v" + s.version()
                    + " (" + v + "), " + s.shown().size() + " items shown"), false);
        }
        return data.all().size();
    }

    private static int place(CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "site");
        SiteDef def = SiteDefs.get(id);
        if (def == null) {
            c.getSource().sendFailure(Component.literal("Unknown site " + id));
            return 0;
        }
        BlockPos at = BlockPos.containing(c.getSource().getPosition());
        boolean ok = Sites.build(c.getSource().getLevel(), def, at, null, false).built();
        c.getSource().sendSuccess(() -> Component.literal(ok ? "Built " + id + " at " + at.toShortString() : "Could not build " + id), true);
        return ok ? 1 : 0;
    }

    private static int export(CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "site");
        String name = StringArgumentType.getString(c, "name");
        ServerLevel level = c.getSource().getLevel();
        SiteData data = SiteData.getIfExists(level);
        BlockPos me = BlockPos.containing(c.getSource().getPosition());
        SiteData.Site site = data == null ? null : data.byId(id).stream()
                .min(Comparator.comparingDouble(s -> s.anchor().distSqr(me))).orElse(null);
        if (site == null) {
            c.getSource().sendFailure(Component.literal("No built site " + id + " here"));
            return 0;
        }
        try {
            Path out = export(level, site, name);
            c.getSource().sendSuccess(() -> Component.literal("Exported " + id + " to " + out), true);
            return 1;
        } catch (Exception e) {
            HoleyDigsite.LOGGER.error("export", e);
            c.getSource().sendFailure(Component.literal("Export failed: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Saves the site's zone as a structure file and a layout file with the displays' current items. Displays without a slot
     * number get the next free numbers. Player state is stripped and shop blocks are saved locked.
     */
    public static Path export(ServerLevel level, SiteData.Site site, String name) throws java.io.IOException {
        BoundingBox z = site.zone();
        List<ShopDisplayBlockEntity> displays = new ArrayList<>();
        for (BlockEntity be : Sites.blockEntities(level, z)) {
            if (be instanceof ShopDisplayBlockEntity d) {
                displays.add(d);
            }
        }
        int next = displays.stream().mapToInt(ShopDisplayBlockEntity::slot).max().orElse(0) + 1;
        Map<Integer, String> slots = new TreeMap<>();
        for (ShopDisplayBlockEntity d : displays) {
            if (d.slot() <= 0) {
                d.setSlot(next++);
            }
            slots.put(d.slot(), d.item() == null ? "" : BuiltInRegistries.ITEM.getKey(d.item()).toString());
        }
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, new BlockPos(z.minX(), z.minY(), z.minZ()), new BlockPos(z.getXSpan(), z.getYSpan(), z.getZSpan()),
                false, Blocks.STRUCTURE_VOID);
        CompoundTag tag = t.save(new CompoundTag());
        strip(tag);
        Path dir = level.getServer().getWorldPath(LevelResource.GENERATED_DIR).resolve("holeydigsite").resolve("structures");
        Files.createDirectories(dir);
        Path file = dir.resolve(name + ".nbt");
        NbtIo.writeCompressed(tag, file);
        JsonObject layout = new JsonObject();
        JsonObject s = new JsonObject();
        slots.forEach((k, v) -> s.addProperty(String.valueOf(k), v));
        layout.add("slots", s);
        Files.writeString(dir.resolve(name + "_layout.json"), new GsonBuilder().setPrettyPrinting().create().toJson(layout));
        return file;
    }

    /** Removes player state from a saved structure and locks the shop blocks. */
    static void strip(CompoundTag tag) {
        for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag b = (CompoundTag) t;
            if (b.contains("nbt")) {
                CompoundTag nbt = b.getCompound("nbt");
                nbt.remove("Items");
                nbt.remove("Balance");
                nbt.remove("Item");      // displays: the layout file decides
            }
        }
        for (Tag t : tag.getList("palette", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            if (p.contains("Properties")) {
                CompoundTag props = p.getCompound("Properties");
                if (props.contains("locked")) props.putString("locked", "true");
                if (props.contains("pile")) props.putString("pile", "0");
                if (props.contains("pressed")) props.putString("pressed", "false");
            }
        }
        ListTag unused = tag.getList("entities", Tag.TAG_COMPOUND);
        unused.clear();
    }
}
