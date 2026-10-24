package com.holeydigsite.site;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A site the mod builds and keeps up to date, like the home store, a land plot, or a plot's shop kiosk.
 * Loaded from {@code data/<ns>/site/<name>.json} (id = {@code <ns>:<name>}), see {@link SiteDefs}.
 *
 * <pre>{@code
 * {
 *   "kind": "store",                         // "store" (protected building) or "plot" (terrain; locked by PlotLock until bought)
 *   "name": "site.holeydigsite.home",        // translation key used in chat messages
 *   "version": 1,                            // bump when the STRUCTURE changes shape -> existing worlds rebuild it
 *   "structure": "holeydigsite:store/home_v1",  // data/holeydigsite/structure/store/home_v1.nbt, placed at the anchor
 *   "size": [26, 22, 20],                    // the zone (x, y, z) from the anchor; fixed per world once built
 *   "entrance": [2, 6, 10],                  // where leftovers / players inside go after a rebuild (relative)
 *   "protect": true,                         // survival players can't break/place inside the zone
 *   "layout": "holeydigsite:home",           // optional: data/holeydigsite/store_layout/home.json (display slot -> item)
 *   "plot": "deep_dig", "board": [9, 9, 0],  // plots (and their kiosks): plot id + the board (relative to the plot anchor)
 *   "children": [{"site": "holeydigsite:deep_dig_shop", "pos": [12, 7, 3]}]   // sites built inside this one
 * }
 * }</pre>
 */
public record SiteDef(ResourceLocation id, Kind kind, String name, int version, ResourceLocation structure, Vec3i size,
                      BlockPos entrance, boolean protect, @Nullable ResourceLocation layout, @Nullable String plot,
                      @Nullable BlockPos board, List<Child> children) {

    public enum Kind { STORE, PLOT }

    /** A site built inside this one, at {@code pos} relative to this site's anchor. */
    public record Child(ResourceLocation site, BlockPos pos) {}

    /** Same definition with another version. */
    public SiteDef withVersion(int v) {
        return new SiteDef(id, kind, name, v, structure, size, entrance, protect, layout, plot, board, children);
    }

    /** Same definition with another structure. */
    public SiteDef withStructure(ResourceLocation s) {
        return new SiteDef(id, kind, name, version, s, size, entrance, protect, layout, plot, board, children);
    }

    public static SiteDef parse(ResourceLocation id, JsonObject o) {
        Kind kind = "plot".equals(GsonHelper.getAsString(o, "kind", "store")) ? Kind.PLOT : Kind.STORE;
        List<Child> children = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "children", new JsonArray())) {
            JsonObject c = e.getAsJsonObject();
            children.add(new Child(ResourceLocation.parse(GsonHelper.getAsString(c, "site")), pos(c.getAsJsonArray("pos"))));
        }
        return new SiteDef(id, kind,
                GsonHelper.getAsString(o, "name", "site." + id.getNamespace() + "." + id.getPath()),
                GsonHelper.getAsInt(o, "version", 1),
                ResourceLocation.parse(GsonHelper.getAsString(o, "structure")),
                pos(o.getAsJsonArray("size")),
                o.has("entrance") ? pos(o.getAsJsonArray("entrance")) : BlockPos.ZERO,
                GsonHelper.getAsBoolean(o, "protect", kind == Kind.STORE),
                o.has("layout") ? ResourceLocation.parse(GsonHelper.getAsString(o, "layout")) : null,
                o.has("plot") ? GsonHelper.getAsString(o, "plot") : null,
                o.has("board") ? pos(o.getAsJsonArray("board")) : null,
                List.copyOf(children));
    }

    private static BlockPos pos(JsonArray a) {
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }
}
