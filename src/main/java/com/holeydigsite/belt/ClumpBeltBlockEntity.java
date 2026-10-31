package com.holeydigsite.belt;

import com.holeydigsite.block.PanBlock;
import com.holeydigsite.registry.ModBlockEntities;
import com.holeydigsite.tier.DirtTier;
import com.holeydigsite.transport.FilterMatch;
import com.holeydigsite.transport.GemCutting;
import com.holeydigsite.transport.ItemOutputs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Contents of one belt block (Conveyor Belt, Fast, Inclined, Filter Belt, Gem Cutter): up to {@value #MAX_CLUMPS} items
 * (one item each) and their positions, plus clumps that have just left the belt and are dropping into the pan in front.
 *
 * An item's position is an integer step {@code 0..}{@value #LAST} along the belt, from the back edge to the front.
 * {@code speed} steps per tick ({@value #STEPS} steps per block). Items keep at least {@value #SPACING} steps between them
 * and queue up behind the one in front. An item at its exit step that wants to move on leaves the block: into the next
 * belt, a pan, any other {@link com.holeydigsite.transport.ItemTarget}, an Inclined Belt above the block in front, or it
 * waits. Filter Belts send matching items out of their side at {@value #MID}; Gem Cutters hold uncut gems at {@value #MID}
 * for {@value #CUT_TICKS} ticks and cut them.
 *
 * The belt only ticks while it holds something and runs (water, no redstone: {@link ClumpBeltBlock#running}). The server
 * syncs the contents to clients only when an item enters or leaves the block, an item is cut and when the belt starts
 * or stops; clients run the same movement rules locally ({@link #clientTick}) and the renderer interpolates.
 * No entities, no physics, no item handler.
 */
public class ClumpBeltBlockEntity extends BlockEntity {
    /** Steps per block (= ticks per block at speed 1). */
    public static final int STEPS = 20;
    /** Last step on this block; the next move leaves the block. */
    public static final int LAST = STEPS - 1;
    /** Minimum distance between two items, in steps (a quarter block). */
    public static final int SPACING = 5;
    /** Where an item arriving from the side lands (the middle of the belt). */
    public static final int SIDE_ENTRY = 10;
    /** The middle: Filter Belt side exit, Gem Cutter wheel. */
    public static final int MID = 10;
    /** Ticks a side-arriving item takes to slide from the side edge to the middle (visual only). */
    public static final int SIDE_TICKS = 10;
    /** Length of the drop into a pan; the pan gets its +1 fill at the end. */
    public static final int DROP_TICKS = 10;
    /** Length of the client-side bounce off a full pan. */
    public static final int BOUNCE_TICKS = 8;
    /** At most this many items fit on a belt block (follows from STEPS and SPACING). */
    public static final int MAX_CLUMPS = 4;
    /** Gem Cutter: ticks to cut one gem (2 s). */
    public static final int CUT_TICKS = 40;

    /** One item on the belt. Fields are package-visible for the renderer. */
    public static final class Clump {
        /** Step along the belt, 0..LAST. */
        int pos;
        /** 3D data value of the side it came in from (2..5), or -1 for "from the back". Visual only. */
        int side = -1;
        /** Ticks left of the slide in from the side. Visual only. */
        int sideTicks;
        /** Random 0..255 used for the item's rotation, kept from belt to belt so it doesn't jump. */
        int seed;
        /** The item (count 1). */
        ItemStack stack = ItemStack.EMPTY;
        /** Gem Cutter progress (ticks). */
        int cut;
        /** Belt Splitter: the output chosen at the middle (SplitterBeltBlock.LEFT / FRONT / RIGHT), or -1 = not decided yet. */
        int route = -1;
        /** Server: game time it arrived; it doesn't move on that tick, whatever the tick order of the belts. */
        long arrived = Long.MIN_VALUE;
        boolean fromDrill;
        /** Client: position and side slide at the previous tick, for interpolation. */
        int prevPos, prevSideTicks;

        Clump(int pos, int seed) {
            this.pos = pos;
            this.seed = seed & 0xFF;
            this.prevPos = pos;
        }

        public int pos() { return pos; }
        public int prevPos() { return prevPos; }
        public int side() { return side; }
        public int sideTicks() { return sideTicks; }
        public int prevSideTicks() { return prevSideTicks; }
        public int seed() { return seed; }
        public ItemStack stack() { return stack; }
        public int cut() { return cut; }
        public int route() { return route; }
        /** Clump kind (1-5) for clumps, 0 for any other item. */
        public int tier() { return DirtTier.isClump(stack) ? DirtTier.ofClump(stack) : 0; }
    }

    /** A clump falling from the belt end into the pan in front. */
    public static final class Drop {
        int ticksLeft;
        final int seed;
        final int tier;
        /** Client: game time the animation started (worked out lazily by the renderer). */
        long clientStart = Long.MIN_VALUE;

        Drop(int ticksLeft, int seed, int tier) {
            this.ticksLeft = ticksLeft;
            this.seed = seed & 0xFF;
            this.tier = DirtTier.clamp(tier);
        }

        public int ticksLeft() { return ticksLeft; }
        public int seed() { return seed; }
        public int tier() { return tier; }
        public long clientStart() { return clientStart; }
        public void setClientStart(long t) { clientStart = t; }
    }

    /** Client only: a clump bouncing off a full pan. */
    public record Bounce(long start, int seed, int tier) {}

    /** Sorted by position, front (highest) first. */
    private final List<Clump> clumps = new ArrayList<>(MAX_CLUMPS);
    private final List<Drop> drops = new ArrayList<>(2);
    /** Client only. */
    private final List<Bounce> bounces = new ArrayList<>(1);
    /** Filter Belt: the filter item (count 1), or empty. */
    private ItemStack filter = ItemStack.EMPTY;
    /** Server: how many times the contents were sent to clients (for tests and debugging). */
    private int syncs;
    /** Belt Splitter: round-robin pointer - the slot of the output list tried first for the next item. */
    private int rr;
    @Nullable
    private java.util.UUID owner;
    @Nullable
    private transient net.minecraft.server.level.ServerPlayer cachedOwner;

    @Nullable
    public java.util.UUID owner() { return owner; }

    public void setOwner(@Nullable java.util.UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public void setOwnerPlayer(@Nullable net.minecraft.server.level.ServerPlayer sp) {
        this.cachedOwner = sp;
        this.owner = sp != null ? sp.getUUID() : null;
        setChanged();
    }

    public void markNewestFromDrill() {
        if (!clumps.isEmpty()) {
            clumps.get(clumps.size() - 1).fromDrill = true;
        }
    }

    @Nullable
    public net.minecraft.server.level.ServerPlayer resolveOwnerPlayer(net.minecraft.server.level.ServerLevel sl, BlockPos pos) {
        if (cachedOwner != null && !cachedOwner.isRemoved()) {
            return cachedOwner;
        }
        if (owner != null) {
            if (sl.getPlayerByUUID(owner) instanceof net.minecraft.server.level.ServerPlayer sp) return sp;
            net.minecraft.server.level.ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(owner);
            if (sp != null) return sp;
        }
        return null;
    }

    public ClumpBeltBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLUMP_BELT.get(), pos, state);
    }

    // ------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------

    public int clumpCount() {
        return clumps.size();
    }

    public List<Clump> clumps() {
        return clumps;
    }

    public List<Drop> drops() {
        return drops;
    }

    public List<Bounce> bounces() {
        return bounces;
    }

    /** The items on this block, front first (copies; for tests). */
    public List<ItemStack> items() {
        return clumps.stream().map(c -> c.stack.copy()).toList();
    }

    /** Number of client syncs sent so far (server side; for tests). */
    public int syncCount() {
        return syncs;
    }

    public boolean isEmpty() {
        return clumps.isEmpty() && drops.isEmpty();
    }

    /** Positions front to back (for tests). */
    public int[] positions() {
        return clumps.stream().mapToInt(c -> c.pos).toArray();
    }

    public ItemStack filter() {
        return filter;
    }

    /** Belt Splitter: slot (in {@link SplitterBeltBlock#order}) tried first for the next item. */
    public int roundRobin() {
        return rr;
    }

    /** Routes of the items on this block, front first (for tests; -1 = not decided). */
    public int[] routes() {
        return clumps.stream().mapToInt(c -> c.route).toArray();
    }

    /** Filter Belt: sets (or clears, with EMPTY) the filter item. */
    public void setFilter(ItemStack stack) {
        filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (level != null && !level.isClientSide) {
            level.blockEntityChanged(worldPosition);
            sync();
        }
    }

    /** True if an item can be put at {@code at} without breaking the spacing rule. */
    private boolean isFree(int at) {
        for (Clump c : clumps) {
            if (Math.abs(c.pos - at) < SPACING) {
                return false;
            }
        }
        return clumps.size() < MAX_CLUMPS;
    }

    /** Entry step for an item arriving through {@code from} (the side of this belt it comes in at), or -1. */
    static int entryFor(BlockState state, Direction from) {
        Direction facing = state.getValue(ClumpBeltBlock.FACING);
        if (from == facing.getOpposite()) {
            return 0;
        }
        if (state.getBlock() instanceof InclineBeltBlock) {
            return (from == Direction.DOWN || from == Direction.UP) ? 0 : -1;   // low end, or chute above
        }
        if (state.getBlock() instanceof DownInclineBeltBlock) {
            return from == Direction.UP ? 0 : -1;   // from chute or down incline above
        }
        if (state.getBlock() instanceof GemCutterBlock) {
            return -1;                                // from the back only
        }
        BeltShape shape = ClumpBeltBlock.shape(state);
        if (shape != BeltShape.STRAIGHT && from == shape.inputSide(facing)) {
            return 0;                                 // corner: the curve starts at the side edge
        }
        if (from.getAxis().isHorizontal() && from.getAxis() != facing.getAxis()) {
            return SIDE_ENTRY;
        }
        if (from == Direction.UP) {
            return 0;                                 // from chute above
        }
        return -1; // from the front (head-on) or from below
    }

    /** Can an item come in through {@code from} right now? Stopped belts (no water / redstone) take nothing. */
    public boolean canAccept(BlockState state, Direction from) {
        if (!ClumpBeltBlock.running(state)) {
            return false;
        }
        int entry = entryFor(state, from);
        return entry >= 0 && isFree(entry);
    }

    // ------------------------------------------------------------------------------------------------
    // Input (server)
    // ------------------------------------------------------------------------------------------------

    /** Puts a clump of {@code tier} in through {@code from}. Caller checked {@link #canAccept}. */
    public void accept(Direction from, int seed, int tier) {
        accept(from, seed, DirtTier.clumps(tier, 1));
    }

    /** Puts one item in through {@code from}. Caller checked {@link #canAccept}. */
    public void accept(Direction from, int seed, ItemStack one) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        int entry = entryFor(state, from);
        Clump c = new Clump(Math.max(0, entry), seed);
        c.stack = one.copyWithCount(1);
        if (entry == SIDE_ENTRY && from.getAxis().isHorizontal()) {
            c.side = from.get3DDataValue();
            c.sideTicks = SIDE_TICKS;
            c.prevSideTicks = SIDE_TICKS;
        }
        c.arrived = level.getGameTime();
        insert(c);
        contentsChanged();
    }

    /**
     * Player input: puts up to {@code max} Dirt Clumps wherever they fit (lowest free step first). Works while stopped
     * too - the stop is for the line, not for the player. Returns how many went on.
     */
    public int insertByPlayer(int max) {
        return insertByPlayer(max, 1);
    }

    /** Player input of clumps of {@code tier} (belts carry mixed kinds fine). */
    public int insertByPlayer(int max, int tier) {
        return insertByPlayer(DirtTier.clumps(tier, 1), max);
    }

    /** Player input of up to {@code max} items like {@code template} (one per slot). */
    public int insertByPlayer(ItemStack template, int max) {
        int added = 0;
        while (added < max) {
            int at = -1;
            for (int p = 0; p <= LAST; p++) {
                if (isFree(p)) {
                    at = p;
                    break;
                }
            }
            if (at < 0) {
                break;
            }
            Clump c = new Clump(at, level == null ? 0 : level.random.nextInt(256));
            c.stack = template.copyWithCount(1);
            c.arrived = level == null ? Long.MIN_VALUE : level.getGameTime();
            insert(c);
            added++;
        }
        if (added > 0) {
            contentsChanged();
        }
        return added;
    }

    /** Player: takes every item on this block (not the clumps already dropping), merged into stacks. */
    public List<ItemStack> takeAll() {
        List<ItemStack> out = merge(clumps.stream().map(c -> c.stack).toList());
        if (!clumps.isEmpty()) {
            clumps.clear();
            contentsChanged();
        }
        return out;
    }

    /** Items that should drop when the belt is broken (on the belt + dropping into the pan; not the filter). */
    public List<ItemStack> contentsForDrops() {
        List<ItemStack> all = new ArrayList<>();
        for (Clump c : clumps) all.add(c.stack);
        for (Drop d : drops) all.add(DirtTier.clumps(d.tier, 1));
        return merge(all);
    }

    private static List<ItemStack> merge(List<ItemStack> items) {
        List<ItemStack> out = new ArrayList<>();
        outer:
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            for (ItemStack o : out) {
                if (ItemStack.isSameItemSameComponents(o, s) && o.getCount() < o.getMaxStackSize()) {
                    o.grow(s.getCount());
                    continue outer;
                }
            }
            out.add(s.copy());
        }
        return out;
    }

    private void insert(Clump c) {
        int i = 0;
        while (i < clumps.size() && clumps.get(i).pos > c.pos) {
            i++;
        }
        clumps.add(i, c);
    }

    // ------------------------------------------------------------------------------------------------
    // Server tick (only while the belt holds something and runs)
    // ------------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, ClumpBeltBlockEntity belt) {
        belt.tickDrops(level, pos, state);
        boolean moved = belt.move(level, pos, state, true);
        level.blockEntityChanged(pos); // mark the chunk for saving (no comparator update needed)
        belt.updateMode(level, pos, state, moved);
    }

    /** Filter Belt: does this item leave through the side (it matches and hasn't passed the middle yet)? */
    private boolean exitsSideways(BlockState state, Clump c) {
        if (state.getBlock() instanceof SplitterBeltBlock) {
            return c.route == SplitterBeltBlock.LEFT || c.route == SplitterBeltBlock.RIGHT;
        }
        return state.getBlock() instanceof FilterBeltBlock && c.pos <= MID && FilterMatch.matches(filter, c.stack);
    }

    /** The side an item leaves through at the middle (Filter Belt: its output side; Splitter: the chosen one). */
    private static Direction sideOut(BlockState state, Clump c) {
        return state.getBlock() instanceof SplitterBeltBlock ? SplitterBeltBlock.direction(state, c.route) : FilterBeltBlock.outSide(state);
    }

    /**
     * Belt Splitter: picks the output for an item waiting at the middle - round-robin over left / front / right (or left /
     * right), skipping outputs that have nothing there or refuse the item right now. Returns the route, or -1 if all
     * refuse (the item waits). The pointer moves past the chosen output.
     */
    private int chooseRoute(Level level, BlockPos pos, BlockState state, Clump c) {
        int[] order = SplitterBeltBlock.order(state);
        for (int k = 0; k < order.length; k++) {
            int idx = Math.floorMod(rr + k, order.length);
            if (leave(level, pos, state, c, SplitterBeltBlock.direction(state, order[idx]), true)) {
                rr = (idx + 1) % order.length;
                return order[idx];
            }
        }
        return -1;
    }

    /** Gem Cutter: is this an uncut gem that still has to be cut at the wheel? */
    private static boolean needsCut(BlockState state, Clump c) {
        return state.getBlock() instanceof GemCutterBlock && c.pos <= MID && GemCutting.isUncut(c.stack);
    }

    /**
     * Moves every item one step (speed) if it can, front to back. An item at its exit step tries to leave (server only):
     * the front item at {@link #LAST} through the front, a matching item on a Filter Belt at {@link #MID} through the
     * side. The others stop {@link #SPACING} steps behind the item in front of them. On a Gem Cutter an uncut gem waits
     * at {@link #MID} for {@link #CUT_TICKS} ticks and is cut.
     *
     * @return true if anything moved or was being cut
     */
    private boolean move(Level level, BlockPos pos, BlockState state, boolean server) {
        boolean moved = false;
        long now = level.getGameTime();
        int speed = ClumpBeltBlock.speed(state);
        int ahead = Integer.MAX_VALUE; // position of the item in front, after it moved
        for (int i = 0; i < clumps.size(); i++) {
            Clump c = clumps.get(i);
            if (!server) {
                c.prevPos = c.pos;
                c.prevSideTicks = c.sideTicks;
            }
            if (server && c.arrived == now) {
                ahead = c.pos;  // arrived this tick: stays where it landed
                continue;
            }
            if (c.sideTicks > 0) {
                c.sideTicks--;
                moved = true;
            }
            boolean cutting = needsCut(state, c);
            if (cutting && c.pos == MID) {
                c.cut++;
                moved = true;
                if (server) {
                    if (c.cut % 10 == 1) {
                        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.4F, 1.3F);
                    }
                    if (c.cut >= CUT_TICKS) {
                        c.stack = GemCutting.cut(c.stack);
                        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                            net.minecraft.server.level.ServerPlayer sp = resolveOwnerPlayer(sl, pos);
                            if (sp != null) {
                                String gemPath = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.stack.getItem()).getPath();
                                String size = gemPath.endsWith("_large") ? "large" : gemPath.endsWith("_medium") ? "medium" : "small";
                                com.holeydigsite.ledger.ModTriggers.CUT_GEM.get().trigger(sp, size);
                            }
                        }
                        c.cut = 0;
                        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
                        contentsChanged();
                    }
                }
                ahead = c.pos;
                continue;
            }
            boolean splitter = state.getBlock() instanceof SplitterBeltBlock;
            if (splitter && c.route < 0) {
                if (c.pos > MID) {
                    c.route = SplitterBeltBlock.FRONT;   // put on past the middle (by hand): straight on
                } else if (c.pos == MID) {
                    int r = server ? chooseRoute(level, pos, state, c) : -1;   // clients wait for the server's decision
                    if (r < 0) {
                        ahead = c.pos;
                        continue;
                    }
                    c.route = r;
                    contentsChanged();
                }
            }
            boolean side = exitsSideways(state, c);
            int exit = side ? MID : LAST;
            boolean clear = ahead == Integer.MAX_VALUE || ahead - SPACING >= exit;
            if (clear && c.pos + speed > exit) {
                // at the exit (a fast belt leaves from LAST - 1 too: exactly 10 ticks per block)
                Direction out = side ? sideOut(state, c) : state.getValue(ClumpBeltBlock.FACING);
                if (server && tryLeave(level, pos, state, c, out)) {
                    clumps.remove(i);
                    i--;
                    moved = true;
                    contentsChanged();
                    continue; // the item behind sees the same "ahead"
                }
                if (server && splitter && side) {
                    c.route = -1;   // the chosen side stopped taking it (target removed): decide again
                }
                ahead = c.pos;
                continue;
            }
            int limit = ahead == Integer.MAX_VALUE ? LAST : ahead - SPACING;
            limit = Math.min(limit, exit);
            if (splitter && c.route < 0) {
                limit = Math.min(limit, MID);   // undecided items stop at the middle
            }
            if (cutting) {
                limit = Math.min(limit, MID);
            }
            if (c.pos < limit) {
                c.pos = Math.min(limit, c.pos + speed);
                moved = true;
            }
            ahead = c.pos;
        }
        return moved;
    }

    /**
     * An item wants to leave through {@code out}. Where to:
     * <ul>
     *   <li>a pan (front only): clumps with room start the drop animation, the pan gets +1 when it lands; a FULL pan
     *       bounces a clump off and destroys it (Phase 5 rule); a pan holding another kind, or a non-clump: wait;</li>
     *   <li>another belt with room at that entry: handed over;</li>
     *   <li>any other {@link com.holeydigsite.transport.ItemTarget} that takes it (sluice back, crucible, Crafting Forge,
     *       Sell Pad, pan through a filter side...): straight in;</li>
     *   <li>nothing there, but an Inclined Belt one block up whose low end is here: up it goes;</li>
     *   <li>anything else: it stays - the belt backs up.</li>
     * </ul>
     */
    private boolean tryLeave(Level level, BlockPos pos, BlockState state, Clump c, Direction out) {
        return leave(level, pos, state, c, out, false);
    }

    /**
     * {@link #tryLeave} or, with {@code simulate}, only the question "would it leave right now?" (nothing changes; a full pan
     * counts as a refusal, since leaving into it would destroy the item).
     */
    private boolean leave(Level level, BlockPos pos, BlockState state, Clump c, Direction out, boolean simulate) {
        boolean downIncline = state.getBlock() instanceof DownInclineBeltBlock;
        BlockPos front = downIncline ? pos.relative(out).below() : pos.relative(out);
        BlockState target = level.getBlockState(front);
        Direction from = out.getOpposite();
        if (com.holeydigsite.plot.PlotLock.isLocked(level, front)) {
            return false; // A locked land plot takes nothing, the item waits
        }
        boolean frontExit = out == state.getValue(ClumpBeltBlock.FACING);
        if (frontExit && target.getBlock() instanceof PanBlock) {
            if (!DirtTier.isClump(c.stack)) {
                return false;
            }
            int tier = DirtTier.panKind(c.stack);
            if (PanBlock.fill(target) + drops.size() < PanBlock.maxFill(target)) {
                if (!panTakesTier(target, tier)) {
                    return false; // other kind in the pan (or on its way): wait
                }
                if (!simulate) {
                    drops.add(new Drop(DROP_TICKS, c.seed, tier));
                }
            } else if (simulate) {
                return false;
            } else {
                // full pan: bounce off and void (client shows the hop + puff, see ClumpBeltBlock#triggerEvent)
                level.blockEvent(pos, state.getBlock(), ClumpBeltBlock.EVENT_BOUNCE, c.seed | tier << 8);
                level.playSound(null, front, SoundEvents.ROOTED_DIRT_HIT, SoundSource.BLOCKS, 0.5F, 1.4F);
            }
            return true;
        }
        if (target.getBlock() instanceof ClumpBeltBlock && level.getBlockEntity(front) instanceof ClumpBeltBlockEntity next) {
            if (next.canAccept(target, from)) {
                if (!simulate) {
                    next.accept(from, c.seed, c.stack);
                    if (c.fromDrill) next.markNewestFromDrill();
                }
                return true;
            }
            return false;
        }
        if (simulate ? ItemOutputs.canOutput(level, front, from, c.stack) : ItemOutputs.tryOutput(level, front, from, c.stack)) {
            if (!simulate && level instanceof net.minecraft.server.level.ServerLevel sl
                    && target.getBlock() instanceof com.holeydigsite.sluice.SluiceBoxBlock) {
                boolean drillFed = c.fromDrill || sl.getBlockState(pos.relative(state.getValue(ClumpBeltBlock.FACING).getOpposite())).getBlock() instanceof com.holeydigsite.drill.MiningDrillBlock;
                if (drillFed) {
                    net.minecraft.server.level.ServerPlayer sp = resolveOwnerPlayer(sl, front);
                    if (sp == null && com.holeydigsite.sluice.SluiceBoxBlock.entityAt(sl, front) instanceof com.holeydigsite.sluice.SluiceBoxBlockEntity sb) {
                        sp = sb.resolveOwnerPlayer(sl, front);
                    }
                    if (sp != null) {
                        com.holeydigsite.ledger.ModTriggers.LINE_RUNNING.get().trigger(sp);
                    }
                }
            }
            return true;
        }
        if (target.getBlock() instanceof com.holeydigsite.transport.ItemTarget) {
            return false; // a target that refused: wait for it
        }
        if (!downIncline) {
            BlockPos up = front.above();
            BlockState u = level.getBlockState(up);
            if (u.getBlock() instanceof InclineBeltBlock && u.getValue(ClumpBeltBlock.FACING) == out
                    && !com.holeydigsite.plot.PlotLock.isLocked(level, up)
                    && level.getBlockEntity(up) instanceof ClumpBeltBlockEntity incline && incline.canAccept(u, Direction.DOWN)) {
                if (!simulate) {
                    incline.accept(Direction.DOWN, c.seed, c.stack);
                    if (c.fromDrill) incline.markNewestFromDrill();
                }
                return true;
            }
        }
        return false;
    }

    /** The pan's kind, or the kind of the clumps already dropping into an empty pan, must match. */
    private boolean panTakesTier(BlockState pan, int tier) {
        if (PanBlock.fill(pan) > 0) {
            return pan.getValue(PanBlock.TIER) == tier;
        }
        return drops.isEmpty() || drops.get(0).tier == tier;
    }

    /** Counts down the drops; a landed clump fills the pan in front (or is lost if the pan went away/filled up). */
    private void tickDrops(Level level, BlockPos pos, BlockState state) {
        if (drops.isEmpty()) {
            return;
        }
        for (int i = 0; i < drops.size(); i++) {
            Drop d = drops.get(i);
            if (--d.ticksLeft <= 0) {
                drops.remove(i--);
                land(level, pos, state, d.tier);
            }
        }
    }

    private static void land(Level level, BlockPos pos, BlockState state, int tier) {
        BlockPos front = pos.relative(state.getValue(ClumpBeltBlock.FACING));
        BlockState pan = level.getBlockState(front);
        if (pan.getBlock() instanceof PanBlock && PanBlock.fill(pan) < PanBlock.maxFill(pan)
                && PanBlock.acceptsTier(pan, tier)) {
            PanBlock.addDirt(level, front, pan, 1, tier);
        }
    }

    /** The belt stops (redstone / water gone): clumps already in the air land right away instead of hanging there. */
    void finishDrops(Level level, BlockPos pos, BlockState state) {
        if (!drops.isEmpty()) {
            // clear first: landing updates the pan, which re-enters our neighborChanged before the state is set
            List<Drop> landing = new ArrayList<>(drops);
            drops.clear();
            for (Drop d : landing) {
                land(level, pos, state, d.tier);
            }
            contentsChanged();
        }
    }

    /** Picks EMPTY / MOVING / BLOCKED from the contents and applies it (this swaps the ticker on or off). */
    void updateMode(Level level, BlockPos pos, BlockState ignored, boolean moved) {
        BlockState state = level.getBlockState(pos); // re-read: a pan landing may have changed our spout state
        if (!(state.getBlock() instanceof ClumpBeltBlock)) {
            return;
        }
        BeltMode next = isEmpty() ? BeltMode.EMPTY : moved ? BeltMode.MOVING : BeltMode.BLOCKED;
        if (state.getValue(ClumpBeltBlock.MODE) != next) {
            level.setBlock(pos, state.setValue(ClumpBeltBlock.MODE, next), Block.UPDATE_CLIENTS);
            sync(); // started or stopped: resync positions
        }
    }

    /** Something arrived or was taken: wake the belt if it was empty, and tell clients. */
    private void contentsChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getValue(ClumpBeltBlock.MODE) == BeltMode.EMPTY && !isEmpty()) {
            level.setBlock(worldPosition, state.setValue(ClumpBeltBlock.MODE, BeltMode.MOVING), Block.UPDATE_CLIENTS);
        } else if (isEmpty() && state.getValue(ClumpBeltBlock.MODE) != BeltMode.EMPTY && !ClumpBeltBlock.running(state)) {
            // stopped belts don't tick, so nothing else would notice they became empty
            level.setBlock(worldPosition, state.setValue(ClumpBeltBlock.MODE, BeltMode.EMPTY), Block.UPDATE_CLIENTS);
        }
        level.blockEntityChanged(worldPosition);
        sync();
    }

    /** Sends the contents to clients (batched by vanilla: one packet per block per tick at most). */
    void sync() {
        if (level != null && !level.isClientSide) {
            syncs++;
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Client
    // ------------------------------------------------------------------------------------------------

    /** Client tick (only while MOVING and running): same movement rules, but items never leave on their own. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, ClumpBeltBlockEntity belt) {
        belt.move(level, pos, state, false);
        if (state.getBlock() instanceof GemCutterBlock) {
            for (Clump c : belt.clumps) {
                if (c.pos == MID && c.cut > 0 && GemCutting.isUncut(c.stack) && level.random.nextInt(3) == 0) {
                    GemCutterBlock.sparks(level, pos, state);
                }
            }
        }
    }

    /** Client: a clump hit a full pan in front. */
    public void addBounce(long start, int seed, int tier) {
        bounces.add(new Bounce(start, seed, tier));
    }

    // ------------------------------------------------------------------------------------------------
    // Save / sync
    // ------------------------------------------------------------------------------------------------

    private static final String ITEMS = "Items";
    private static final String DROPS = "Drops";
    private static final String DROP_TIERS = "DropTiers";
    private static final String FILTER = "Filter";

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("Owner", owner);
        writeContents(tag, registries);
    }

    private void writeContents(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Clump k : clumps) {
            CompoundTag t = new CompoundTag();
            t.putInt("P", (k.pos & 0xFF) | ((k.side + 1) & 0xFF) << 8 | (k.sideTicks & 0xFF) << 16 | (k.seed & 0xFF) << 24);
            t.putInt("Cut", k.cut);
            if (k.fromDrill) t.putBoolean("FD", true);
            if (k.route >= 0) {
                t.putInt("Route", k.route);
            }
            t.put("Item", k.stack.saveOptional(registries));
            list.add(t);
        }
        tag.put(ITEMS, list);
        tag.putInt("Rr", rr);
        int[] d = new int[drops.size()];
        for (int i = 0; i < d.length; i++) {
            d[i] = (drops.get(i).ticksLeft & 0xFF) | (drops.get(i).seed & 0xFF) << 8;
        }
        tag.putIntArray(DROPS, d);
        tag.putIntArray(DROP_TIERS, drops.stream().mapToInt(k -> k.tier).toArray());
        if (!filter.isEmpty()) {
            tag.put(FILTER, filter.saveOptional(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        clumps.clear();
        for (Tag t0 : tag.getList(ITEMS, Tag.TAG_COMPOUND)) {
            if (clumps.size() >= MAX_CLUMPS) {
                break;
            }
            CompoundTag t = (CompoundTag) t0;
            int v = t.getInt("P");
            Clump k = new Clump(Math.clamp(v & 0xFF, 0, LAST), (v >>> 24) & 0xFF);
            k.side = ((v >>> 8) & 0xFF) - 1;
            k.sideTicks = Math.clamp((v >>> 16) & 0xFF, 0, SIDE_TICKS);
            k.prevSideTicks = k.sideTicks;
            k.cut = t.getInt("Cut");
            k.fromDrill = t.getBoolean("FD");
            k.route = t.contains("Route") ? Math.clamp(t.getInt("Route"), -1, 2) : -1;
            k.stack = ItemStack.parseOptional(registries, t.getCompound("Item"));
            if (!k.stack.isEmpty()) {
                insert(k);
            }
        }
        rr = Math.max(0, tag.getInt("Rr"));
        drops.clear();
        int[] dv = tag.getIntArray(DROPS);
        int[] dt = tag.getIntArray(DROP_TIERS);
        for (int i = 0; i < dv.length; i++) {
            drops.add(new Drop(Math.clamp(dv[i] & 0xFF, 1, DROP_TICKS), (dv[i] >>> 8) & 0xFF, i < dt.length ? dt[i] : 1));
        }
        filter = tag.contains(FILTER) ? ItemStack.parseOptional(registries, tag.getCompound(FILTER)) : ItemStack.EMPTY;
    }

    /** Chunk data and the update packet carry the same contents as the save. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeContents(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
