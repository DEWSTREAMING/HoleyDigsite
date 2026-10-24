#!/usr/bin/env python3
"""Holey Digsite world generator (Phase 14).

Designs the whole starting world in ONE coordinate space (the island template's) and splits it into:
  * jar structures  src/main/resources/data/holeydigsite/structure/...  (SNBT here, NBT via NbtTool - see make_world.sh)
      store/home_vN.snbt          the trading post (home store) zone
      plot/<plot>_vN.snbt         a land plot's zone (terrain, landmark, board) minus its kiosk zone
      store/<plot>_shop_vN.snbt   a plot's themed shop kiosk
  * site definitions + store layouts  data/holeydigsite/site/*.json, data/holeydigsite/store_layout/*.json
  * the pack's island template  <pack>/_source/island/holey_digsite.snbt: the home island OUTSIDE the store zone, the
    bridges and one Site Marker per top-level site (the mod builds the store / plots there on first load)

Usage:  python tools/make_world.py [pack root]
        python tools/check_world.py      (safety + economy checks, imports this file)
        python tools/preview_world.py    (isometric / top-down PNG previews, imports this file)

Shipping a change: edit this file, bump the site's VERSION below if its SHAPE changed (new structure file name
<name>_v<VERSION>), run make_world.sh, keep the old structure files (old worlds don't need them, but they are harmless).
Only display items changed? Edit LAYOUTS below (or the json) - no version bump.

Coordinates: x east, y up, z south. B = the unbreakable base layer (y 4), G = normal ground top (y 9, players stand at 10).
"""
import json, math, os, sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
PACK = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith('-') else os.path.join(MOD, '..', 'Holey Digsite Pack')
OUT_SNBT = os.path.join(HERE, 'world_snbt')
DATA = os.path.join(MOD, 'src', 'main', 'resources', 'data', 'holeydigsite')

B, G = 4, 9
TW, TH, TD = 75, 32, 57

DIRTROCK = 'holeydigsite:dirtrock'
DEEP = 'holeydigsite:deep_dirtrock'
ABYSSAL = 'holeydigsite:abyssal_dirtrock'
GRAVELROCK = 'holeydigsite:gravelrock'
SANDROCK = 'holeydigsite:sandrock'
ROCKS = {DIRTROCK, DEEP, ABYSSAL, GRAVELROCK, SANDROCK}
LIGHT = 'minecraft:light{level:15,waterlogged:false}'
WATER = 'minecraft:water{level:0}'
AIR = 'minecraft:air'

# ------------------------------------------------------------------------------------------------ sites
# Versions: bump when a site's SHAPE changes (existing worlds then rebuild it on load, keeping player state).
# v2 (Phase 16.1): floating props settled, kiosk roofs closed, mill wall/roof gap closed. sand_dunes v3: palms with joints (v2 had
# squashed them). deep_dig v3: the crane stands on the island (v2's post hung over the void).
# home v3, gravel_flats v3, sand_dunes v4: air slits under ground-level blocks filled (Trading Post corner, kiosk foundation). Older structure files are kept.
VERSIONS = {'home': 4, 'deep_dig': 4, 'gravel_flats': 4, 'sand_dunes': 5,
            'deep_dig_shop': 2, 'gravel_flats_shop': 2, 'sand_dunes_shop': 2}
# Sites whose structure was built by hand in-game and exported with /holeydigsite site export (see the dev guide): the
# generator still designs the rest of the world and writes their site json, but never writes their structure or layout.
HAND_BUILT = set()          # e.g. {'home'} once data/holeydigsite/structure/store/home_v2.nbt is a hand-made design
SITES = {   # id: (kind, anchor, size (x, y, z))
    'home':         ('store', (18, 0, 3), (26, 26, 22)),
    'deep_dig':     ('plot', (0, 0, 36), (21, 32, 21)),
    'gravel_flats': ('plot', (27, 0, 36), (21, 32, 21)),
    'sand_dunes':   ('plot', (54, 0, 36), (21, 32, 21)),
}
PLOTS = ('deep_dig', 'gravel_flats', 'sand_dunes')
PLOT_NAMES = {'deep_dig': 'Deep Dig', 'gravel_flats': 'Gravel Flats', 'sand_dunes': 'Sand Dunes'}
KIOSK = (7, 11, 7)
# plot -> (kiosk anchor plot-local, front facing). The kiosk zone is a site of its own (protected, rebuilt on updates).
KIOSK_AT = {'deep_dig': ((1, 7, 2), 'east'), 'gravel_flats': ((14, 7, 1), 'west'), 'sand_dunes': ((3, 7, 4), 'south')}
# plot -> (board plot-local, facing): at the bridge end the player arrives from
BOARD_AT = {'deep_dig': ((13, G + 1, 1), 'north'), 'gravel_flats': ((7, G + 1, 1), 'north'), 'sand_dunes': ((1, G + 1, 13), 'west')}
PLOT_ENTRANCE = {'deep_dig': (10, G + 1, 1), 'gravel_flats': (10, G + 1, 1), 'sand_dunes': (1, G + 1, 16)}
SPAWN = (10, G + 1, 12)          # template-relative, player feet; faces east (the store)

# Display slot -> catalogue item. Changing these only (no version bump) updates the displays on the next world load.
HOME_ROWS = {   # row name -> items in slot order (None = empty display, kept for future items)
    'checkout': ['holeydigsite:sell_pad', 'holeydigsite:shop_button', 'holeydigsite:buy_pad', 'holeydigsite:money_pan'],
    'digging':  ['minecraft:dirt', 'minecraft:wooden_shovel', 'holeydigsite:pan', 'holeydigsite:brush', 'holeydigsite:dirt_clump'],
    'drills':   ['holeydigsite:mining_drill', 'holeydigsite:water_intake', 'holeydigsite:water_pipe', 'holeydigsite:sluice_box', 'holeydigsite:seed_screen'],
    'belts':    ['holeydigsite:clump_belt', 'holeydigsite:incline_belt', 'holeydigsite:filter_belt', 'holeydigsite:belt_splitter',
                 'holeydigsite:gem_cutter', 'holeydigsite:down_incline_belt', 'holeydigsite:drop_chute'],
    'forge':    ['minecraft:lava_bucket', 'holeydigsite:lava_intake', 'holeydigsite:lava_pipe', 'holeydigsite:forge',
                 'holeydigsite:crucible', 'holeydigsite:mold'],
    'crafting': ['minecraft:bread', 'holeydigsite:smithing_anvil', 'holeydigsite:crafting_forge', 'holeydigsite:blacksmith_hammer', 'holeydigsite:prospectors_ledger'],
    'land':     ['holeydigsite:land_deed_deep_dig', 'holeydigsite:land_deed_gravel_flats', 'holeydigsite:land_deed_sand_dunes'],
}
KIOSK_ITEMS = {
    'deep_dig': ['holeydigsite:dirt_clump', 'holeydigsite:mining_drill', 'holeydigsite:miners_lantern', 'holeydigsite:water_intake',
                 'holeydigsite:water_pipe', 'holeydigsite:clump_belt', 'holeydigsite:prospectors_pick'],
    'gravel_flats': ['holeydigsite:gravel_clump', 'holeydigsite:sluice_box', 'holeydigsite:river_stone_lamp', 'holeydigsite:water_intake',
                     'holeydigsite:water_pipe', 'holeydigsite:clump_belt', 'holeydigsite:riffle_insert'],
    'sand_dunes': ['holeydigsite:sand_clump', 'holeydigsite:sluice_box', 'holeydigsite:sandstone_lamp', 'holeydigsite:water_intake',
                   'holeydigsite:water_pipe', 'holeydigsite:clump_belt', 'holeydigsite:fine_mesh_screen'],
}


def site_box(sid):
    _, (ax, ay, az), (sx, sy, sz) = SITES[sid]
    return (ax, ay, az, ax + sx - 1, ay + sy - 1, az + sz - 1)


def kiosk_box(pid):
    _, (ax, ay, az), _ = SITES[pid]
    (kx, ky, kz), _ = KIOSK_AT[pid]
    return (ax + kx, ay + ky, az + kz, ax + kx + KIOSK[0] - 1, ay + ky + KIOSK[1] - 1, az + kz + KIOSK[2] - 1)


def inside(box, x, y, z):
    return box[0] <= x <= box[3] and box[1] <= y <= box[4] and box[2] <= z <= box[5]


# ------------------------------------------------------------------------------------------------ world grid
W = {}            # (x, y, z) -> (state, nbt or None)
MASKS = {}        # island name -> set of (x, z) columns that are island ground (checker: rock under all of them)
LIGHTS_NOTE = []


def put(x, y, z, state, nbt=None):
    if 0 <= x < TW and 0 <= y < TH and 0 <= z < TD:
        if state is None:
            W.pop((x, y, z), None)
        else:
            W[(x, y, z)] = (state, nbt)


def get(x, y, z):
    v = W.get((x, y, z))
    return v[0] if v else None


def base(state):
    return state.split('{')[0] if state else None


def fill(x0, y0, z0, x1, y1, z1, state):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                put(x, y, z, state)


def clear(x0, y0, z0, x1, y1, z1):
    fill(x0, y0, z0, x1, y1, z1, None)


def h01(*a):
    """Deterministic hash -> 0..1."""
    v = 0x811C9DC5
    for i in a:
        v = ((v ^ (int(round(i * 7)) & 0xFFFFFFFF)) * 0x01000193) & 0xFFFFFFFF
    v ^= v >> 15
    v = (v * 0x2C1B3C6D) & 0xFFFFFFFF
    v ^= v >> 12
    return (v & 0xFFFFFF) / float(0xFFFFFF)


def pick(options, *a):
    return options[min(len(options) - 1, int(h01(*a) * len(options)))]


def blob(cx, cz, rx, rz, seed, wob=0.12):
    """Organic outline: an ellipse whose radius wobbles with the angle."""
    def f(x, z):
        dx, dz = (x - cx) / rx, (z - cz) / rz
        r = math.hypot(dx, dz)
        a = math.atan2(dz, dx)
        lim = 1 + wob * math.sin(3 * a + seed) + wob * 0.6 * math.sin(5 * a + 2 * seed) + wob * 0.4 * math.sin(7 * a + 3 * seed)
        return r <= lim
    return f


def edge_dist(mask, x, z, maxd=4):
    """Chebyshev distance to the nearest column outside the mask (0 = on the edge), capped."""
    for d in range(1, maxd + 2):
        for dx in range(-d, d + 1):
            for dz in range(-d, d + 1):
                if max(abs(dx), abs(dz)) == d and not mask(x + dx, z + dz):
                    return d - 1
    return maxd + 1


def underside(x, z, depth, seed):
    """Decorative rock hanging under the base layer (y B-1 .. B-depth): the floating-island look."""
    for i in range(1, depth + 1):
        y = B - i
        if y < 0:
            break
        r = h01(x, y, z, seed)
        put(x, y, z, 'minecraft:stone' if r < 0.45 else 'minecraft:andesite' if r < 0.65 else 'minecraft:tuff' if r < 0.8
            else 'minecraft:cobblestone' if r < 0.9 else 'minecraft:coarse_dirt' if i == 1 else 'minecraft:stone')


SIGN_WIDTH, HANGING_WIDTH = 90, 60
WIDTH_WARNINGS = []


def sign_nbt(lines, color='black', glow=False):
    for t in lines:
        if text_width(t) > SIGN_WIDTH:
            WIDTH_WARNINGS.append('%r is %d px (> %d)' % (t, text_width(t), SIGN_WIDTH))
    msgs = ','.join("'%s'" % json.dumps({'text': t}, ensure_ascii=False).replace("'", "\\'") for t in (list(lines) + ['', '', '', ''])[:4])
    empty = ','.join(["'\"\"'"] * 4)
    return ('{id:"minecraft:sign",front_text:{messages:[%s],color:"%s",has_glowing_text:%db},'
            'back_text:{messages:[%s],color:"black",has_glowing_text:0b},is_waxed:1b}' % (msgs, color, 1 if glow else 0, empty))


_NARROW = {'i': 2, 'l': 3, 't': 4, 'f': 5, 'k': 5, 'I': 4, ' ': 4, ',': 2, '.': 2, "'": 3, '!': 2, ':': 2, ';': 2, '|': 2}


def text_width(t):
    """Approximate pixel width of a line in Minecraft's default font (advance incl. spacing; arrows count 9)."""
    return sum(9 if ord(c) > 127 else _NARROW.get(c, 6) for c in t)


def hanging_sign_nbt(lines, color='black', glow=False):
    for t in lines:
        if text_width(t) > HANGING_WIDTH:
            WIDTH_WARNINGS.append('hanging sign %r is %d px (> %d)' % (t, text_width(t), HANGING_WIDTH))
    return sign_nbt(lines, color, glow).replace('minecraft:sign', 'minecraft:hanging_sign')


def display(x, y, z, slot):
    put(x, y, z, 'holeydigsite:shop_display', '{id:"holeydigsite:shop_display",Slot:%d}' % slot)


def lantern(x, y, z, hanging=False):
    put(x, y, z, 'minecraft:lantern{hanging:%s,waterlogged:false}' % ('true' if hanging else 'false'))


def lantern_post(x, y, z, fence='minecraft:spruce_fence', h=2):
    for _ in range(3):                      # Phase 16.1: stand on the ground that was really built, not on a nominal height
        if y > 0 and not structural(get(x, y - 1, z)) and any(structural(get(x, yy, z)) for yy in range(y - 3, y - 1)):
            y -= 1
    for i in range(h):
        put(x, y + i, z, fence)
    lantern(x, y + h, z)


def stairs(kind, facing, half='bottom'):
    return 'minecraft:%s_stairs{facing:%s,half:%s,shape:straight,waterlogged:false}' % (kind, facing, half)


def slab(kind, t='bottom'):
    return 'minecraft:%s_slab{type:%s,waterlogged:false}' % (kind, t)


def log(kind, axis='y'):
    return 'minecraft:%s{axis:%s}' % (kind, axis)


def leaves(kind):
    return 'minecraft:%s_leaves{distance:1,persistent:true,waterlogged:false}' % kind


def tree(x, y, z, kind='oak', height=5, seed=0):
    """Round-ish tree; trunk from y (standing on the ground block at y-1)."""
    top = y + height - 1
    logs = [(x, y + i, z) for i in range(height)]
    for ly, r in ((top - 2, 2), (top - 1, 2), (top, 1), (top + 1, 1)):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                corner = abs(dx) == r and abs(dz) == r
                if corner and (ly in (top - 1, top + 1) or r == 1 or h01(x + dx, ly, z + dz, seed) < 0.55):
                    continue
                if (x + dx, ly, z + dz) not in logs and get(x + dx, ly, z + dz) is None:
                    put(x + dx, ly, z + dz, leaves(kind))
    for p in logs:
        put(*p, log(kind + '_log'))


def palm(x, y, z, height, lean):
    """Jungle-log palm with drooping fronds; lean = (dx, dz) applied twice up the trunk."""
    pts, cx, cz = [], x, z
    for i in range(height):
        if i in (height // 2, height - 2):
            # Phase 16.1: a joint at the bend, so the trunk is one piece face to face (it used to touch only at a corner,
            # which made the top half a "floating" group)
            if lean[0]:
                pts.append((cx + lean[0], y + i - 1, cz))
            if lean[1]:
                pts.append((cx + lean[0], y + i - 1, cz + lean[1]))
            cx += lean[0]
            cz += lean[1]
        pts.append((cx, y + i, cz))
    for p in pts:
        put(*p, log('jungle_log'))
    tx, ty, tz = pts[-1]
    lf = leaves('jungle')
    put(tx, ty + 1, tz, lf)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        put(tx + dx, ty + 1, tz + dz, lf)
        put(tx + 2 * dx, ty, tz + 2 * dz, lf)
        put(tx + 3 * dx, ty - 1, tz + 3 * dz, lf)
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        put(tx + dx, ty, tz + dz, lf)
    put(tx, ty - 1, tz + 1, 'minecraft:cocoa{age:2,facing:north}')      # coconuts (cocoa faces its log)
    put(tx - 1, ty - 1, tz, 'minecraft:cocoa{age:2,facing:east}')


# ------------------------------------------------------------------------------------------------ rotation (kiosks)
ROT = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
TURNS = {'north': 0, 'east': 1, 'south': 2, 'west': 3}


def rot_state(state, turns):
    """Rotates a block state's direction properties clockwise (seen from above) by 90 degrees x turns."""
    if '{' not in state or turns % 4 == 0:
        return state
    name, props = state[:-1].split('{', 1)
    out = []
    for kv in props.split(','):
        k, v = kv.split(':', 1)
        if k == 'facing' and v in ROT:
            for _ in range(turns):
                v = ROT[v]
        elif k == 'axis' and v in ('x', 'z') and turns % 2:
            v = 'z' if v == 'x' else 'x'
        elif k == 'rotation':
            v = str((int(v) + 4 * turns) % 16)
        out.append('%s:%s' % (k, v))
    return name + '{' + ','.join(out) + '}'


def stamp(local, size, anchor, facing):
    """Places a local build (front at kz = 0, facing north) at anchor, rotated so its front faces `facing`."""
    sx, _, sz = size
    t = TURNS[facing]
    ax, ay, az = anchor
    for (kx, ky, kz), (st, nbt) in local.items():
        x, z = kx, kz
        for _ in range(t):          # clockwise: (x, z) -> (sz-1-z, x) in a square footprint
            x, z = (sz - 1) - z, x
        put(ax + x, ay + ky, az + z, rot_state(st, t), nbt)


# ------------------------------------------------------------------------------------------------ connections pass
FENCE_LIKE = ('_fence', 'glass_pane', 'iron_bars', '_wall')
NOT_FULL = ('stairs', 'slab', 'lantern', 'sign', 'water', 'light', 'leaves', 'fence', 'pane', 'bars', 'torch', 'ladder', 'rail',
            'chain', 'flower', 'poppy', 'dandelion', 'cornflower', 'daisy', 'bluet', 'tulip', 'allium', 'short_grass', 'fern', 'bush', 'cane', 'cocoa', 'carpet',
            'scaffolding', 'shop_display', 'pad', 'button', 'money_pan', 'plot_board', 'trapdoor', 'door', 'campfire', 'barrel_x',
            'flower_pot', 'lily', 'kelp', 'seagrass', 'cactus', 'bell', 'azalea', 'wall', 'site_marker', 'pointed', 'anvil', 'hay_x',
            'cauldron', 'grindstone', 'composter_x')


def full_block(st):
    if st is None:
        return False
    b = base(st)
    return not any(k in b for k in NOT_FULL)


def connect():
    """Sets fence / pane / wall connections from their neighbours (like the game would on placement)."""
    for (x, y, z), (st, nbt) in list(W.items()):
        b = base(st)
        if not any(b.endswith(k) or k in b for k in FENCE_LIKE) or 'gate' in b or 'sign' in b:
            continue
        sides = {}
        for d, (dx, dz) in (('north', (0, -1)), ('south', (0, 1)), ('east', (1, 0)), ('west', (-1, 0))):
            n = get(x + dx, y, z + dz)
            nb = base(n)
            if nb is None:
                c = False
            elif b.endswith('_fence'):
                c = nb.endswith('_fence') or nb.endswith('_fence_gate') or full_block(n)
            elif b.endswith('_wall'):
                c = nb.endswith('_wall') or full_block(n)
            else:   # panes, bars
                c = nb.endswith('glass_pane') or nb.endswith('iron_bars') or full_block(n) or nb.endswith('_wall')
            sides[d] = c
        if b.endswith('_wall'):
            up = not (sides['north'] and sides['south'] and not sides['east'] and not sides['west']) and \
                 not (sides['east'] and sides['west'] and not sides['north'] and not sides['south'])
            st2 = '%s{east:%s,north:%s,south:%s,up:%s,waterlogged:false,west:%s}' % (
                b, *('low' if sides[d] else 'none' for d in ('east', 'north', 'south')), 'true' if up else 'false',
                'low' if sides['west'] else 'none')
        else:
            st2 = '%s{east:%s,north:%s,south:%s,waterlogged:false,west:%s}' % (
                b, *('true' if sides[d] else 'false' for d in ('east', 'north', 'south', 'west')))
        W[(x, y, z)] = (st2, nbt)


# ------------------------------------------------------------------------------------------------ connectivity (Phase 16.1)
PLANTS = {'short_grass', 'tall_grass', 'fern', 'large_fern', 'poppy', 'dandelion', 'cornflower', 'oxeye_daisy', 'azure_bluet', 'dead_bush',
          'sugar_cane', 'red_tulip', 'allium', 'blue_orchid', 'light', 'air'}
DIRS = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
# leaves (and the logs touching them) also join diagonally: a canopy is one object even where its leaves meet only at an edge
DIRS26 = tuple((x, y, z) for x in (-1, 0, 1) for y in (-1, 0, 1) for z in (-1, 0, 1) if (x, y, z) != (0, 0, 0))


# stair roofs step up one block per row: neighbouring stairs also join at their edges (the way a roof of stairs reads as one piece)
DIRS_STAIR = tuple((dx, dy, dz) for dy in (-1, 1) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def neighbours(Wd, c):
    st = Wd[c][0]
    leafy, stair = 'leaves' in st, 'stairs' in st
    for d in (DIRS26 if leafy else DIRS + DIRS_STAIR if stair else DIRS):
        n = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
        if n not in Wd or not structural(Wd[n][0]) or not linked(st, Wd[n][0]):
            continue
        if d in DIRS or (leafy and 'leaves' in Wd[n][0]) or (stair and 'stairs' in Wd[n][0]):
            yield n


def structural(st):
    """A block that can hold things up / be held up: not air, not an invisible light, not a plant (plants need soil, checked separately)."""
    return st is not None and base(st).split(':')[1] not in PLANTS


def linked(a, c):
    """Face-adjacent structural blocks count as joined, except that leaves only join logs and other leaves (a boulder touching a
    bush is still floating)."""
    la, lc = 'leaves' in a, 'leaves' in c
    if la != lc:
        other = c if la else a
        return 'log' in other or 'wood' in other
    return True


def detached_groups(Wd=None):
    """Groups of structural blocks (face-adjacent) that do not join the unbreakable rock. Returns the groups, smallest key first."""
    Wd = W if Wd is None else Wd
    keys = {k for k, (st, _) in Wd.items() if structural(st)}
    rockb = {base(r) for r in ROCKS}
    seen = {k for k in keys if base(Wd[k][0]) in rockb}
    q = deque(seen)
    while q:
        c = q.popleft()
        for n in neighbours(Wd, c):
            if n not in seen:
                seen.add(n)
                q.append(n)
    loose = keys - seen
    groups = []
    while loose:
        start = loose.pop()
        grp, q = {start}, deque([start])
        while q:
            c = q.popleft()
            for n in neighbours(Wd, c):
                if n in loose:
                    loose.discard(n)
                    grp.add(n)
                    q.append(n)
        groups.append(grp)
    return sorted(groups, key=min)




SETTLED = []      # (first cell, size, drop or 'removed') of every group settle_floating changed - printed by check_world.py


def slits():
    """Phase 16.1: ground-level blocks (y <= G) with a 1-2 block air gap under them and ground right below that - e.g. the Trading Post's
    corner foundation where the island edge was softened under it. Gaps next to water (a footbridge over the stream) are meant to be open.
    Returns [(top block pos, gap cells, ground pos)]."""
    cols = set().union(*MASKS.values())
    out = []
    for (x, y, z), (st, _) in sorted(W.items()):
        if y > G or (x, z) not in cols or not structural(st) or not full_block(st) or base(st).endswith('_leaves'):
            continue
        if structural(get(x, y - 1, z)):
            continue
        for d in (2, 3):
            g = get(x, y - d, z)
            if structural(g):
                gap = [(x, y - i, z) for i in range(1, d)]
                wet = any('water' in (get(*c) or '') or any('water' in (get(c[0] + dx, c[1], c[2] + dz) or '')
                                                             for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) for c in gap) or 'water' in g
                if not wet:
                    out.append(((x, y, z), gap, (x, y - d, z)))
                break
    return out


def fill_slits():
    """Fills every slit() with the ground's block (or, on unbreakable rock, the block above it)."""
    for top, gap, ground in slits():
        g = get(*ground)
        fill_state = get(*top) if g in ROCKS else g
        for c in gap:
            put(*c, fill_state)


def settle_floating(max_drop=4, passes=6):
    """Phase 16.1: props placed at a nominal height (trees, boulders, posts, barrels, gallows) can end up hanging over terrain that is
    lower there. Every detached group is lowered until it rests on the block below (at most `max_drop`); groups with nothing to rest
    on within that range stay, and tools/check_world.py reports them. Plants / lights below are overwritten."""
    moved = 0
    for _ in range(passes):
        changed = False
        for grp in detached_groups():
            cells = set(grp)
            if any(inside(kiosk_box(pid), *c) for pid in PLOTS for c in cells):
                continue                      # kiosks are designed to stand on their own (see build_kiosk), never settled
            if any(structural(get(*n)) and n not in cells and not ('leaves' in get(*n) and any('leaves' in W[c][0] for c in cells))
                   for c in cells for d in DIRS26 if d not in DIRS for n in [(c[0] + d[0], c[1] + d[1], c[2] + d[2])]):
                continue                      # touches something at a corner: part of a bigger object joined badly
                                              # (lowering it would squash that object) - left for check_world.py to report
            drop = None
            for k in range(1, max_drop + 2):
                hit = False
                for (x, y, z) in cells:
                    n = (x, y - k, z)
                    if n not in cells and structural(get(*n)) and n in W:
                        hit = True
                        break
                    if y - k < 0:
                        hit = True
                        break
                if hit:
                    drop = k - 1
                    break
            if drop is None or drop > max_drop:
                # nothing to rest on: a small prop standing outside every island (a fence post beyond the edge) is dropped
                if len(cells) <= 8 and not any((c[0], c[2]) in cols for c in cells for cols in MASKS.values()):
                    for c in cells:
                        del W[c]
                    SETTLED.append((min(cells), len(cells), 'removed'))
                    moved += len(cells)
                    changed = True
                continue
            if drop == 0:
                continue
            old = {c: W[c] for c in cells}
            for c in cells:
                del W[c]
            if any((c[0], c[1] - drop, c[2]) in W and structural(get(c[0], c[1] - drop, c[2])) for c in cells):
                W.update(old)
                continue
            for (x, y, z), v in old.items():
                W[(x, y - drop, z)] = v
            SETTLED.append((min(cells), len(cells), drop))
            moved += len(cells)
            changed = True
        if not changed:
            break
    return moved


# ------------------------------------------------------------------------------------------------ terrain helper
def column(x, z, h, top, fill_fn, rock5=DIRTROCK, rock4=DIRTROCK, depth=3, seed=0):
    """One island column: underside, rock at y4 (rock4) and y5 (rock5, None = fill), fill up to h-1, `top` at h."""
    underside(x, z, depth, seed)
    put(x, B, z, rock4)
    if rock5 is not None:
        put(x, B + 1, z, rock5)
    for y in range(B + (1 if rock5 is None else 2), h):
        put(x, y, z, fill_fn(x, y, z))
    if h > B + (0 if rock5 is None else 1) and top is not None:
        put(x, h, z, top)


def soften(h, ed, keep=False):
    """Rounds an island's rim: the outer ring 2 lower, the next ring 1 lower (never below the rock)."""
    if keep:
        return h
    if ed == 0:
        return max(B + 2, h - 2)
    if ed == 1:
        return max(B + 2, h - 1)
    return h


def kiosk_cols(pid):
    (kx, ky, kz), _ = KIOSK_AT[pid]
    return {(kx + i, kz + j) for i in range(-1, KIOSK[0] + 1) for j in range(-1, KIOSK[2] + 1)}


def flowers(x, y, z, seed, rate=0.08, kinds=('minecraft:poppy', 'minecraft:dandelion', 'minecraft:cornflower', 'minecraft:oxeye_daisy',
                                              'minecraft:azure_bluet')):
    r = h01(x, z, seed)
    if r < rate * 0.45:
        put(x, y, z, pick(list(kinds), x, z, seed + 1))
    elif r < rate * 1.6:
        put(x, y, z, 'minecraft:short_grass')


# ================================================================================================ HOME ISLAND
STORE_BOX = site_box('home')
_home_w = blob(9.0, 15.0, 10.4, 14.8, 1.3, 0.09)
_home_e = blob(31.0, 13.5, 14.2, 11.8, 0.4, 0.05)
_home_s = blob(37.0, 26.0, 5.0, 5.0, 2.0, 0.05)
POOL = {(x, z) for x in (4, 5) for z in (15, 16, 17)}
PAD = {(6, 15), (6, 16), (6, 17)}


def surface_y(x, z):
    """Top of the ground column at x, z: the highest block that is not a plant, a tree part or an invisible light."""
    for y in range(TH - 1, -1, -1):
        st = get(x, y, z)
        if st and structural(st) and not base(st).endswith(('leaves', '_log')):
            return y
    return G


def home_mask(x, z):
    if not (0 <= x <= 44 and 0 <= z <= 31):
        return False
    if (8 <= x <= 12 or 35 <= x <= 39) and 27 <= z <= 31:      # bridge landings
        return True
    return _home_w(x, z) or _home_e(x, z) or _home_s(x, z)


def home_height(x, z):
    h = G
    d1 = math.hypot((x - 4.0) / 4.6, (z - 5.0) / 4.2)          # NW hill
    if d1 < 1:
        h += int(round(3.4 * (1 - d1 * d1)))
    d2 = math.hypot((x - 3.5) / 3.0, (z - 24.0) / 3.2)        # SW knoll
    if d2 < 1:
        h += int(round(1.6 * (1 - d2 * d2)))
    return h


def build_home():
    mask = home_mask
    cols = set()
    path = set()
    for x in range(10, 23):                    # spawn -> store porch
        path |= {(x, 12), (x, 13)}
    for z in range(13, 31):                    # spawn -> Deep Dig bridge
        path.add((10, z))
        if z >= 20:
            path.add((11, z))
    for x in range(11, 15):
        path.add((x, 11) if x < 13 else (x, 12))
    for z in range(25, 31):                    # store yard -> Gravel Flats bridge
        path |= {(37, z), (38, z)}
    for x in range(0, 45):
        for z in range(0, 32):
            if not mask(x, z):
                continue
            cols.add((x, z))
            ed = edge_dist(mask, x, z, 3)
            h = home_height(x, z)
            h = soften(h, ed, keep=z >= 27 or (x, z) in POOL or (x, z) in PAD)
            if (x, z) in POOL or (x, z) in PAD:
                h = G
            depth = min(4, 1 + ed + (1 if h01(x, z, 7) > 0.6 else 0))

            def fill_fn(px, py, pz):
                return 'minecraft:stone' if py <= B + 1 and h01(px, py, pz) < 0.5 else (
                    'minecraft:coarse_dirt' if h01(px, py, pz, 3) < 0.7 else 'minecraft:rooted_dirt')
            if (x, z) in POOL:
                column(x, z, G - 1, DIRTROCK, fill_fn, depth=depth, seed=3)
                put(x, G, z, WATER)
                continue
            if (x, z) in PAD:
                column(x, z, G - 1, DIRTROCK, fill_fn, depth=depth, seed=3)
                put(x, G, z, 'minecraft:dirt')        # scoop it: the drill stands on the Dirtrock below
                continue
            # surface: grass near spawn (scoopable, kept small), moss elsewhere (not scoopable), rock on the hill's steep side
            if (x, z) in path:
                top = 'minecraft:dirt_path'
            elif h >= G + 2 and x >= 6 and z >= 5 and h01(x, z, 11) < 0.7:
                top = pick(['minecraft:stone', 'minecraft:andesite', 'minecraft:mossy_cobblestone'], x, z)
            elif math.hypot(x - 9, z - 14) < 4.6 and (x, z) not in POOL:
                top = 'minecraft:grass_block{snowy:false}'
            else:
                top = 'minecraft:moss_block' if h01(x, z, 5) < 0.88 else 'minecraft:grass_block{snowy:false}'
            if x >= 18 and inside(STORE_BOX, x, G, z):
                top = 'minecraft:moss_block' if h01(x, z, 5) < 0.75 else 'minecraft:coarse_dirt'
            column(x, z, h, top, fill_fn, depth=depth, seed=3)
            if top.startswith(('minecraft:grass', 'minecraft:moss')) and not inside(STORE_BOX, x, h + 1, z):
                flowers(x, h + 1, z, 99)
    MASKS['home'] = cols
    # pool rim stones + drill pad sign
    for (x, z) in ((3, 14), (3, 18), (6, 14), (6, 18)):
        put(x, G + 1, z, None)
        put(x, G, z, 'minecraft:mossy_cobblestone')
    put(7, G + 1, 18, 'minecraft:oak_sign{rotation:6,waterlogged:false}',
        sign_nbt(['DRILL PAD', '', 'Pan at', 'the pool']))   # pool is behind the reader (sign faces NW): no arrow
    put(11, G + 1, 10, 'minecraft:oak_sign{rotation:4,waterlogged:false}',
        sign_nbt(['Trading Post ↑', '', 'Land plots →', '']))   # reader looks east: store ahead, plots to the right (south)
    # trees + boulders
    # Phase 16.1: stand on the surface that was really built (the island's edge is softened below home_height), never on a nominal height
    tree(4, surface_y(4, 5) + 1, 5, 'oak', 5, 1)
    tree(2, surface_y(2, 22) + 1, 22, 'birch', 6, 2)
    tree(15, surface_y(15, 25) + 1, 25, 'oak', 4, 3)
    for (x, z, n) in ((1, 11, 2), (2, 11, 1), (1, 12, 1), (14, 3, 1), (16, 21, 1)):
        if (x, z) not in cols:
            continue                                   # (14, 3) lies beyond the island's edge
        hh = surface_y(x, z)
        for i in range(n):
            put(x, hh + 1 + i, z, 'minecraft:mossy_cobblestone' if i == 0 else 'minecraft:cobblestone')
    # a little wood pile + chopping block near the hill
    for z in (8, 9):
        put(8, G + 1, z, log('oak_log', 'x'))
    put(8, G + 2, 8, log('oak_log', 'x'))
    put(9, G + 1, 7, log('stripped_oak_log'))
    # lights (invisible) so nothing spawns at night: every 6 blocks, 4 above the ground, outside the store zone
    for x in range(1, 45, 6):
        for z in range(1, 32, 6):
            if mask(x, z) and not inside(STORE_BOX, x, G + 4, z):
                hh = max(h for h in range(B, TH) if get(x, h, z) and not base(get(x, h, z)).endswith(('leaves', 'log', 'flower',
                                                                                                     'grass', 'light')))
                if get(x, hh + 4, z) is None:
                    put(x, hh + 4, z, LIGHT)
    lantern_post(9, G + 1, 27, 'minecraft:spruce_fence', 2)
    lantern_post(13, G + 1, 27, 'minecraft:spruce_fence', 2)
    lantern_post(36, G + 1, 29, 'minecraft:spruce_fence', 2) if False else None


# ================================================================================================ TRADING POST (home store)
STORE_SLOTS = {}     # row -> [slot numbers]


def build_store():
    ax, ay, az = SITES['home'][1]

    def L(lx, ly, lz, st, nbt=None):
        put(ax + lx, ly, az + lz, st, nbt)
    FL = G + 1                      # floor block y (players stand at FL + 1)
    X0, X1, Z0, Z1 = 5, 23, 4, 16   # building walls (local)
    ZC = 10                         # door / ridge line
    POSTS_X, POSTS_Z = (5, 11, 17, 23), (4, 10, 16)
    # ---- clear the building volume
    for lx in range(X0 - 1, X1 + 2):
        for lz in range(Z0 - 1, Z1 + 2):
            for y in range(FL, 26):
                L(lx, y, lz, None)
    # ---- foundation: stone plinth one block up, plank floor
    for lx in range(X0, X1 + 1):
        for lz in range(Z0, Z1 + 1):
            edge = lx in (X0, X1) or lz in (Z0, Z1)
            L(lx, G, lz, 'minecraft:cobblestone' if edge else 'minecraft:coarse_dirt')
            if edge:
                L(lx, FL, lz, pick(['minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:stone_bricks',
                                    'minecraft:cobblestone'], lx, lz, 4))
            else:
                L(lx, FL, lz, 'minecraft:spruce_planks' if (lx + lz) % 7 else 'minecraft:stripped_spruce_log{axis:x}')
    # ---- walls: dark oak log frame, oak plank infill, windows
    WY0, WY1 = FL + 1, FL + 4      # wall rows
    for lx in range(X0, X1 + 1):
        for lz in range(Z0, Z1 + 1):
            if not (lx in (X0, X1) or lz in (Z0, Z1)):
                continue
            post = (lx in POSTS_X and lz in (Z0, Z1)) or (lz in POSTS_Z and lx in (X0, X1))
            for y in range(WY0, WY1 + 1):
                if post:
                    L(lx, y, lz, log('dark_oak_log'))
                    continue
                along_x = lz in (Z0, Z1)
                bay = ((lx - X0) % 6) if along_x else ((lz - Z0) % 6)
                window = bay in (2, 3, 4) and y in (WY0 + 1, WY0 + 2) and not (lx == X0 and 8 <= lz <= 12)
                if lx == X0 and ZC - 1 <= lz <= ZC + 1 and y <= WY0 + 2:
                    L(lx, y, lz, None)                                     # 3-wide doorway
                    continue
                if y == WY0:
                    L(lx, y, lz, 'minecraft:stripped_dark_oak_log{axis:%s}' % ('x' if along_x else 'z'))
                elif window:
                    L(lx, y, lz, 'minecraft:glass_pane')
                else:
                    L(lx, y, lz, 'minecraft:oak_planks')
            # top plate
            L(lx, WY1 + 1, lz, log('dark_oak_log', 'x' if lz in (Z0, Z1) else 'z'))
    for lz in range(ZC - 1, ZC + 2):
        L(X0, WY0 + 3, lz, log('dark_oak_log', 'z'))                       # door lintel
    # window sills (outside) + flower boxes on the north / south walls
    for lx in range(X0 + 1, X1):
        bay = (lx - X0) % 6
        if bay in (2, 3, 4):
            for lz, f in ((Z0 - 1, 'north'), (Z1 + 1, 'south')):
                L(lx, WY0, lz, 'minecraft:spruce_trapdoor{facing:%s,half:top,open:false,powered:false,waterlogged:false}' % f)
    # ---- roof: ridge along x on ZC, 45 degrees, 1 overhang; spruce stairs, dark oak ridge
    TOP = WY1 + 1
    RIDGE = TOP + (ZC - (Z0 - 1))
    for lx in range(X0 - 1, X1 + 2):
        for lz in range(Z0 - 1, Z1 + 2):
            d = abs(lz - ZC)
            y = RIDGE - d
            if d == 0:
                L(lx, y, lz, slab('dark_oak', 'bottom') if lx in (X0 - 1, X1 + 1) else log('dark_oak_log', 'x'))
                L(lx, y + 1, lz, slab('dark_oak') if lx not in (X0 - 1, X1 + 1) else None)
            else:
                L(lx, y, lz, stairs('spruce', 'south' if lz < ZC else 'north'))
                if lx in (X0 - 1, X1 + 1):
                    L(lx, y - 1, lz, stairs('spruce', 'north' if lz < ZC else 'south', 'top'))   # eave edge trim
    # gables (end walls above the top plate) with a round-ish window
    for lx in (X0, X1):
        for lz in range(Z0 + 1, Z1):
            d = abs(lz - ZC)
            for y in range(TOP + 1, RIDGE - d):
                win = d <= 1 and y in (TOP + 3, TOP + 4) and not (d == 1 and y == TOP + 4)
                L(lx, y, lz, 'minecraft:glass_pane' if win else ('minecraft:spruce_planks' if d else log('dark_oak_log')))
    # the inner roof underside (planks) so the inside looks finished
    for lx in range(X0 + 1, X1):
        for lz in range(Z0, Z1 + 1):
            d = abs(lz - ZC)
            if d >= 1:
                L(lx, RIDGE - d - 1 + (0), lz, 'minecraft:spruce_planks') if RIDGE - d - 1 > TOP else None
    # tie beams + hanging lanterns inside
    for lx in (11, 17):
        for lz in range(Z0 + 1, Z1):
            L(lx, TOP, lz, log('dark_oak_log', 'z'))
        for lz in (7, 13):
            L(lx, TOP - 1, lz, 'minecraft:chain{axis:y,waterlogged:false}')
            lantern(ax + lx, TOP - 2, az + lz, True)
    for lx in (8, 14, 20):
        L(lx, TOP, ZC, 'minecraft:chain{axis:y,waterlogged:false}') if False else None
    for (lx, lz) in ((8, 7), (8, 13), (14, 10), (20, 10), (20, 6), (20, 14), (14, 6), (14, 14)):
        L(lx, TOP + 1, lz, LIGHT) if get(ax + lx, TOP + 1, az + lz) is None else None
    # ---- porch / covered veranda in front of the door (west): deck lx 1..4, lz 6..14
    PZ0, PZ1 = 6, 14
    for lx in range(1, X0):
        for lz in range(PZ0, PZ1 + 1):
            for y in range(B + 1, FL):
                if get(ax + lx, y, az + lz) is None:
                    L(lx, y, lz, 'minecraft:coarse_dirt')
            L(lx, G, lz, 'minecraft:cobblestone' if lx == 1 or lz in (PZ0, PZ1) else 'minecraft:coarse_dirt')
            L(lx, FL, lz, 'minecraft:spruce_planks' if lx > 1 else log('stripped_spruce_log', 'z'))
            for y in range(FL + 1, FL + 6):
                L(lx, y, lz, None)
    for lz in range(ZC - 1, ZC + 2):                                       # steps down to the path
        L(0, FL, lz, stairs('spruce', 'east'))
        L(0, FL + 1, lz, None)
    for (lx, lz) in ((1, PZ0), (1, PZ1), (1, ZC - 2), (1, ZC + 2)):        # porch posts
        for y in range(FL + 1, FL + 4):
            L(lx, y, lz, log('dark_oak_log'))
    for lz in list(range(PZ0 + 1, ZC - 2)) + list(range(ZC + 3, PZ1)):     # railings between the posts
        L(1, FL + 1, lz, 'minecraft:dark_oak_fence')
    for lx in range(2, X0):
        for lz in (PZ0, PZ1):
            L(lx, FL + 1, lz, 'minecraft:dark_oak_fence')
    for lx in range(0, X0):                                                # lean-to roof
        for lz in range(PZ0 - 1, PZ1 + 2):
            if lx == 0:
                L(lx, FL + 4, lz, stairs('spruce', 'east'))
            elif lx == 1:
                L(lx, FL + 4, lz, log('dark_oak_log', 'z') if PZ0 <= lz <= PZ1 else stairs('spruce', 'east'))
            elif lx < 4:
                L(lx, FL + 4, lz, slab('spruce', 'bottom'))       # bottom slabs: lanterns can hang from them
            else:
                L(lx, FL + 5, lz, stairs('spruce', 'east'))
                L(lx, FL + 4, lz, 'minecraft:spruce_planks')
    for lz in (PZ0 - 1, PZ1 + 1):
        L(0, FL + 4, lz, stairs('spruce', 'east'))
    for lz in (8, 12):
        lantern(ax + 2, FL + 3, az + lz, True)
    # store name: hanging sign on the porch beam + big glowing wall signs on the gable
    L(1, FL + 3, ZC, 'minecraft:spruce_hanging_sign{attached:false,rotation:4,waterlogged:false}',
      hanging_sign_nbt(['TRADING POST', 'Open'], 'black'))
    # name board on the porch roof: dark oak backing, three glowing signs facing the path
    for lz in range(ZC - 2, ZC + 3):
        L(1, FL + 5, lz, 'minecraft:dark_oak_planks')
        L(1, FL + 6, lz, slab('dark_oak') if lz in (ZC - 2, ZC + 2) else 'minecraft:dark_oak_planks')
    for lz, txt in ((ZC - 1, ['', 'TOOLS', '&', 'MACHINES']), (ZC, ['HOLEY', 'DIGSITE', '', 'Trading Post']),
                    (ZC + 1, ['', 'WE BUY', 'GOLD', ''])):
        for dy in (5, 6):
            L(0, FL + dy, lz, None)
        L(0, FL + 6 if lz == ZC else FL + 5, lz, 'minecraft:dark_oak_wall_sign{facing:west,waterlogged:false}', sign_nbt(txt, 'yellow', True))
    L(0, FL + 5, ZC, 'minecraft:dark_oak_wall_sign{facing:west,waterlogged:false}', sign_nbt(['', 'Est. 1849', '', ''], 'white', True))
    # ---- inside: counters with displays (2 apart; labels show one at a time)
    slot = [0]

    def disp(lx, lz, counter, row):
        L(lx, FL + 1, lz, counter)
        slot[0] += 1
        display(ax + lx, FL + 2, az + lz, slot[0])
        STORE_SLOTS.setdefault(row, []).append(slot[0])

    cx = 'minecraft:stripped_spruce_log{axis:x}'
    cz = 'minecraft:stripped_spruce_log{axis:z}'
    barrel = 'minecraft:barrel{facing:up,open:false}'
    # checkout: north-west corner by the door. Display counter on the wall (lz 5), the real pads in front (lz 7)
    for lx in range(6, 11):
        L(lx, FL + 1, 5, cx)
    for lx in (6, 7, 8, 9):
        disp(lx, 5, cx, 'checkout')
    # pads: Sell Pad (7), Shop Button (8), Buy Pad (9) on the floor; Money Pan behind the button
    L(7, FL + 1, 7, 'holeydigsite:sell_pad{locked:true}')
    L(8, FL + 1, 7, 'holeydigsite:shop_button{locked:true,pressed:false}')
    L(9, FL + 1, 7, 'holeydigsite:buy_pad{locked:true}')
    L(8, FL + 1, 6, 'holeydigsite:money_pan{locked:true,pile:0}')
    L(10, FL + 1, 6, barrel)
    L(6, FL + 1, 6, barrel)
    L(8, FL + 4, 5, 'minecraft:oak_wall_sign{facing:south,waterlogged:false}',
      sign_nbt(['CHECKOUT', 'Put it on a pad,', 'hit the button', '']))
    # Digging - north wall
    for lx in range(12, 22):
        L(lx, FL + 1, 5, cx)
    for lx in (13, 15, 17, 19, 21):
        disp(lx, 5, cx, 'digging')
    L(17, FL + 4, 5, 'minecraft:oak_wall_sign{facing:south,waterlogged:false}', sign_nbt(['', 'Digging', '', '']))
    # Drills & Water - island A (lz 8), Belts - island B (lz 12)
    for lz, row, title in ((8, 'drills', 'Drills'), (12, 'belts', 'Belts')):
        for lx in range(13, 22):
            L(lx, FL + 1, lz, cx)
        for lx in (13, 15, 17, 19, 21):
            disp(lx, lz, cx, row)
        L(12, FL + 1, lz, 'minecraft:spruce_planks')
        L(12, FL + 2, lz, 'minecraft:oak_wall_sign{facing:west,waterlogged:false}', sign_nbt(['', title, '', '']))
    # Forge & Lava - south wall
    for lx in range(10, 22):
        L(lx, FL + 1, 15, cx)
    for lx in (11, 13, 15, 17, 19, 21):
        disp(lx, 15, cx, 'forge')
    L(16, FL + 4, 15, 'minecraft:oak_wall_sign{facing:north,waterlogged:false}', sign_nbt(['', 'Forge', '', '']))
    # Crafting - east wall (room for future Tier 2/3 items)
    for lz in range(6, 15):
        L(22, FL + 1, lz, cz)
    for lz in (6, 8, 10, 12, 14):
        disp(22, lz, cz, 'crafting')
    L(22, FL + 4, ZC, 'minecraft:oak_wall_sign{facing:west,waterlogged:false}', sign_nbt(['', 'Crafting', '', '']))
    # Land office: south-west corner by the door, deeds on a desk + a map on the wall
    for lx in range(6, 9):
        L(lx, FL + 1, 15, cx)
    for lx in (6, 7, 8):
        disp(lx, 15, cx, 'land')
    L(7, FL + 4, 15, 'minecraft:oak_wall_sign{facing:north,waterlogged:false}', sign_nbt(['LAND FOR SALE', 'Deed goes on', "the plot's board", '']))
    L(6, FL + 1, 13, barrel)
    L(9, FL + 1, 14, 'minecraft:lectern{facing:north,has_book:false,powered:false}') if False else None
    # crates / decor
    for (lx, lz) in ((22, 5), (22, 15), (10, 14)):
        L(lx, FL + 1, lz, barrel)
    L(10, FL + 1, 15, 'minecraft:spruce_planks')
    # Phase 21i: extra belt displays on Belts island
    disp(14, 12, cx, 'belts')
    disp(16, 12, cx, 'belts')
    # ---- outside: yard, wood pile, cart wheel, lantern posts, flower beds
    for lx in range(X0 + 1, X1, 3):
        pass
    lantern_post(ax + 0, G + 1, az + 6, 'minecraft:dark_oak_fence')
    lantern_post(ax + 0, G + 1, az + 14, 'minecraft:dark_oak_fence')
    for (lx, lz) in ((X1 + 2, 6), (X1 + 2, 14), (14, Z1 + 2), (8, Z0 - 2)):
        lantern_post(ax + lx, G + 1, az + lz, 'minecraft:spruce_fence')
    for lx in range(16, 21):                                           # log pile behind the store (south)
        L(lx, G + 1, Z1 + 2, log('spruce_log', 'x'))
        if 17 <= lx <= 19:
            L(lx, G + 2, Z1 + 2, log('spruce_log', 'x'))
    for (lx, lz) in ((X1 + 1, 18), (X1 + 2, 18), (X1 + 1, 19)):
        L(lx, G + 1, lz, 'minecraft:hay_block{axis:y}')
    L(X1 + 1, G + 2, 18, 'minecraft:hay_block{axis:x}')
    L(3, G + 1, 18, barrel)
    L(2, G + 1, 18, 'minecraft:composter{level:0}')
    # lights outside in the zone (yard)
    for (lx, lz) in ((2, 2), (10, 1), (18, 1), (25, 4), (25, 12), (25, 19), (2, 19), (10, 19), (18, 20), (2, 10)):
        if get(ax + lx, G + 5, az + lz) is None:
            L(lx, G + 5, lz, LIGHT)
    return STORE_SLOTS


# ================================================================================================ PLOTS
def P(pid):
    ox, oy, oz = SITES[pid][1]

    def f(lx, y, lz, st, nbt=None):
        if 0 <= lx < 21 and 0 <= lz < 21:
            put(ox + lx, y, oz + lz, st, nbt)
    return f


def plot_mask(seed, extra=(), rx=10.6, rz=10.6, wob=0.07, pid=None):
    shape = blob(10, 10, rx, rz, seed, wob)
    ex = set(extra)
    if pid:                                   # the kiosk and the board always stand on the plot
        (kx, ky, kz), _ = KIOSK_AT[pid]
        ex |= {(kx + i, kz + j) for i in range(KIOSK[0]) for j in range(KIOSK[2])}
        (bx, by, bz), _ = BOARD_AT[pid]
        ex.add((bx, bz))

    def m(lx, lz):
        if not (0 <= lx < 21 and 0 <= lz < 21):
            return False
        return (lx, lz) in ex or shape(lx, lz)
    return m


def board(pid):
    (bx, by, bz), facing = BOARD_AT[pid]
    ox, oy, oz = SITES[pid][1]
    nbt = '{id:"holeydigsite:plot_board",Plot:"%s",Min:[I;%d,%d,%d],Max:[I;%d,%d,%d]}' % (
        pid, -bx, -by, -bz, 20 - bx, 31 - by, 20 - bz)
    y = max(yy for yy in range(B, G + 3) if full_block(get(ox + bx, yy, oz + bz))) + 1    # on the ground
    assert y == by, '%s board must stand on the ground at y %d (ground top %d)' % (pid, by, y - 1)
    put(ox + bx, by, oz + bz, 'holeydigsite:plot_board{facing:%s,owned:false}' % facing, nbt)


def plot_lights(pid, heights, extra=(), step=5):
    p = P(pid)
    kb = kiosk_box(pid)
    ox, oy, oz = SITES[pid][1]
    for lx in range(2, 21, step):
        for lz in range(2, 21, step):
            hh = heights.get((lx, lz))
            if hh is None or inside(kb, ox + lx, hh + 4, oz + lz):
                continue
            y = hh + 4
            while get(ox + lx, y, oz + lz) is not None and y < TH - 1:
                y += 1
            p(lx, y, lz, LIGHT)
    for (lx, y, lz) in extra:
        p(lx, y, lz, LIGHT)


def sign_post(p, lx, y, lz, rot, lines, wood='oak'):
    p(lx, y, lz, 'minecraft:%s_sign{rotation:%d,waterlogged:false}' % (wood, rot), sign_nbt(lines))


# ---------------------------------------------------------------- Deep Dig: quarry / mine
DD = {}


def build_deep_dig():
    pid = 'deep_dig'
    p = P(pid)
    ox, oy, oz = SITES[pid][1]
    landing = ({(lx, lz) for lx in range(8, 13) for lz in range(0, 3)} | {(lx, lz) for lx in range(18, 21) for lz in range(8, 13)}
               | {(13, 1), (14, 1), (13, 2)})
    mask = plot_mask(2.1, landing, 10.7, 10.4, pid=pid)
    # the quarry pit: three 1-block terraces down to a floor at y 6
    PIT = [(set((lx, lz) for lx in range(9, 19) for lz in range(9, 19)), G - 1),
           (set((lx, lz) for lx in range(10, 18) for lz in range(10, 18)), G - 2),
           (set((lx, lz) for lx in range(11, 17) for lz in range(11, 17)), G - 3)]
    POOL = {(15, 14), (16, 14), (15, 15), (16, 15)}                       # in the pit floor
    DEEP1 = {(lx, lz) for lx in range(11, 14) for lz in range(11, 15)}    # under the pit floor, one corner dug open
    DEEP1_OPEN = {(11, 11), (12, 11), (11, 12)}
    DEEP2 = {(lx, lz) for lx in range(2, 6) for lz in range(9, 13)}       # under the west meadow (podzol hint)
    DEEP3 = set()
    ABYSS1 = {(lx, lz) for lx in range(13, 17) for lz in range(3, 7)}     # under the north-east hill: ~9 layers of digging
    ABYSS2 = {(lx, lz) for lx in range(4, 8) for lz in range(16, 20)}     # under the south-west mound
    POOL2 = {(6, 14), (7, 14), (6, 15), (7, 15)}                          # meadow pool between DEEP2 and ABYSS2
    kb = kiosk_box(pid)

    def height(lx, lz):
        h = G
        d = math.hypot((lx - 16) / 4.6, (lz - 3.5) / 3.6)                   # NE hill (cliff faces the pit)
        if d < 1:
            h = G + (4 if d < 0.55 else 3 if d < 0.75 else 2 if d < 0.9 else 1)
        d2 = math.hypot((lx - 5.5) / 3.4, (lz - 17.5) / 3.2)                # SW mound
        if d2 < 1:
            h = G + (2 if d2 < 0.6 else 1)
        for cells, ph in PIT:
            if (lx, lz) in cells:
                h = ph
        if (lx, lz) in DEEP1_OPEN:
            h = B + 1                                                      # half-exposed Deep Dirtrock
        if (lx, lz) in landing or (lx, lz) in POOL2:
            h = G
        return h

    heights = {}
    for lx in range(21):
        for lz in range(21):
            if not mask(lx, lz):
                continue
            ed = edge_dist(mask, lx, lz, 3)
            h = height(lx, lz)
            if h >= G:
                h = soften(h, ed, keep=(lx, lz) in landing or (lx, lz) in ABYSS1 or (lx, lz) in POOL2 or (lx, lz) in kiosk_cols(pid)
                           or (lx, lz) == BOARD_AT[pid][0][::2])
            heights[(lx, lz)] = h
            abyss = (lx, lz) in ABYSS1 or (lx, lz) in ABYSS2
            deep = (lx, lz) in DEEP1 or (lx, lz) in DEEP2 or (lx, lz) in DEEP3

            def fill_fn(x, y, z, h=h):
                if y == h - 1 and h > G + 1 and h01(x, z, y) < 0.5:
                    return 'minecraft:stone'
                return 'minecraft:rooted_dirt' if h01(x, y, z, 13) < 0.22 else 'minecraft:coarse_dirt'
            # surface (scoopable grass kept small - the value is in the rock)
            if (lx, lz) in POOL or (lx, lz) in POOL2:
                top = None
            elif (lx, lz) in DEEP1_OPEN:
                top = None
            elif abyss:
                top = 'minecraft:mud' if h01(lx, lz, 4) < 0.5 else 'minecraft:packed_mud'   # dark, wet patch = deep down
            elif (lx, lz) in DEEP2 or (lx, lz) in DEEP3:
                top = 'minecraft:podzol{snowy:false}'                                     # darker dirt = rock below
            elif any((lx, lz) in c for c, _ in PIT):
                top = pick(['minecraft:coarse_dirt', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt', 'minecraft:packed_mud'], lx, lz)
            elif h >= G + 2:
                top = 'minecraft:moss_block' if h01(lx, lz, 5) < 0.55 else 'minecraft:coarse_dirt'
            elif lx <= 8 and h01(lx, lz, 6) < 0.45:
                top = 'minecraft:grass_block{snowy:false}'
            else:
                top = pick(['minecraft:coarse_dirt', 'minecraft:moss_block', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt'], lx, lz)
            rock4 = ABYSSAL if abyss else DIRTROCK
            rock5 = None if abyss else (DEEP if deep else DIRTROCK)
            column(ox + lx, oz + lz, h, top, fill_fn, rock5=rock5, rock4=rock4, depth=min(4, 1 + ed), seed=11)
            if (lx, lz) in POOL or (lx, lz) in POOL2:
                p(lx, h, lz, WATER)
                p(lx, h - 1, lz, DIRTROCK)
            elif top and top.startswith(('minecraft:grass', 'minecraft:moss')) and h <= G + 1:
                flowers(ox + lx, h + 1, oz + lz, 17, 0.07)
            # an Abyssal pocket is a dip in the Dirtrock floor: ring it with Dirtrock at y5
            if abyss:
                pass
    for sink in (ABYSS1, ABYSS2):
        for (lx, lz) in sink:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    q = (lx + dx, lz + dz)
                    if q not in sink and q in heights and heights[q] > B + 1:
                        p(q[0], B + 1, q[1], DIRTROCK)
    for cell in DEEP1 | DEEP2 | DEEP3 | ABYSS1 | ABYSS2:
        assert cell in heights, 'Deep Dig rock pocket cell %s is off the island' % (cell,)
    DD.update(heights=heights, deep=DEEP1 | DEEP2 | DEEP3, abyss=ABYSS1 | ABYSS2, open=DEEP1_OPEN, pit=PIT)
    MASKS[pid] = {(ox + lx, oz + lz) for (lx, lz) in heights}
    # ---- quarry dressing: ladders + scaffolding, crane, rails out of the pit, spoil heap
    for y in range(G - 2, G + 1):                                         # scaffolding tower in the NE pit corner
        p(17, y + 0, 10, 'minecraft:scaffolding{bottom:false,distance:0,waterlogged:false}') if heights.get((17, 10)) is not None and y > heights[(17, 10)] else None
    for y in range(heights[(17, 10)] + 1, G + 3):
        p(17, y, 10, 'minecraft:scaffolding{bottom:false,distance:0,waterlogged:false}')
    for (lx, lz) in ((11, 13),):                                          # ladder down into the open Deep corner
        p(lx, B + 2, lz - 1, None)
    # the open corner: ladder on its south wall
    p(11, B + 1 + 1, 12, None)
    # rails: from the pit floor west up the terraces to the spoil heap
    rails = [((15, G - 3 + 1, 13), 'east_west'), ((14, G - 3 + 1, 13), 'east_west'),
             ((13, G - 3 + 1, 13), 'east_west'), ((12, G - 3 + 1, 13), 'east_west'),
             ((10, G - 2 + 1, 13), 'ascending_west'), ((9, G - 1 + 1, 13), 'ascending_west'),
             ((8, G + 1, 13), 'east_west'), ((7, G + 1, 13), 'east_west')]
    rails[3] = ((11, G - 3 + 1, 13), 'ascending_west')
    rails.insert(3, ((12, G - 3 + 1, 13), 'east_west'))
    for (lx, y, lz), shape in rails:
        if heights.get((lx, lz)) is not None and heights[(lx, lz)] == y - 1:
            p(lx, y, lz, 'minecraft:rail{shape:%s,waterlogged:false}' % shape)
    # crane / pulley over the pit. Phase 16.1: its post used to stand at lx 19 / lz 18, beyond the island's edge (post and barrel
    # hung over the void); now it stands on the pit's south rim and reaches out over the pit
    for y in range(heights[(17, 17)] + 1, G + 6):
        p(17, y, 17, log('spruce_log'))
    for lx in range(14, 17):
        p(lx, G + 5, 17, log('spruce_log', 'x'))
    for y in range(G + 2, G + 5):
        p(14, y, 17, 'minecraft:chain{axis:y,waterlogged:false}')
    p(14, G + 1, 17, 'minecraft:lantern{hanging:true,waterlogged:false}')
    p(16, heights[(16, 17)] + 1, 17, 'minecraft:barrel{facing:up,open:false}')
    # spoil heap west of the pit (coarse dirt / gravel-free)
    for (lx, lz, n) in ((6, 12, 1), (7, 11, 1), (7, 12, 1), (6, 11, 1)):
        hh = heights.get((lx, lz))
        if hh is not None and hh == G:
            p(lx, hh + 1, lz, 'minecraft:coarse_dirt')
    # cliff: ladder up the NE hill's face from the pit rim
    for y in range(G, G + 4):
        if heights.get((13, 6), 0) >= y:
            pass
    hx, hz = 15, 7
    for y in range(heights.get((hx, hz + 1), G) + 1, heights.get((hx, hz), G) + 1):
        p(hx, y, hz + 1, 'minecraft:ladder{facing:south,waterlogged:false}')
    # stakes + signs = hints
    lantern_post(ox + 6, heights[(6, 17)] + 1, oz + 17, 'minecraft:oak_fence', 1)
    lantern_post(ox + 13, heights[(13, 6)] + 1, oz + 6, 'minecraft:oak_fence', 1) if (13, 6) in heights else None
    sign_post(p, 7, G + 1, 3, 8, ['DEEP DIG', 'Good rock', 'under the mud', ''])
    for lz in range(3, 9):
        p(10, heights[(10, lz)], lz, 'minecraft:dirt_path') if heights.get((10, lz)) == G else None
    for lx in range(13, 18):
        p(lx, heights[(lx, 10)], 10, 'minecraft:dirt_path') if False else None
    board(pid)
    clear(*kb)
    plot_lights(pid, heights, [(14, G - 1, 14), (12, G - 1, 16), (16, G, 11), (4, G + 6, 16), (16, G + 7, 4)])
    return heights


# ---------------------------------------------------------------- Gravel Flats: riverbed
GF = {}


def build_gravel_flats():
    pid = 'gravel_flats'
    p = P(pid)
    ox, oy, oz = SITES[pid][1]
    landing = ({(lx, lz) for lx in range(8, 13) for lz in range(0, 3)} | {(lx, lz) for lx in range(0, 3) for lz in range(8, 13)}
               | {(lx, lz) for lx in range(18, 21) for lz in range(14, 19)})
    mask = plot_mask(4.7, landing, 10.8, 10.6, pid=pid)
    WL = G - 2                                                            # water level y 7
    # stream centreline: from the north-west pool, past the footbridge, to the south-east pool
    path_pts = [(5, 6), (6, 7), (8, 9), (9, 11), (10, 13), (12, 14), (14, 15)]

    def stream_d(lx, lz):
        best = 99
        for (a, b), (c, d) in zip(path_pts, path_pts[1:]):
            vx, vz = c - a, d - b
            t = max(0, min(1, ((lx - a) * vx + (lz - b) * vz) / float(vx * vx + vz * vz)))
            best = min(best, math.hypot(lx - (a + t * vx), lz - (b + t * vz)))
        return best
    POOL_N = {(lx, lz) for lx in range(3, 7) for lz in range(4, 8) if not (lx, lz) in ((3, 4), (6, 4))}
    POOL_S = {(lx, lz) for lx in range(13, 17) for lz in range(14, 18) if not (lx, lz) in ((16, 17), (13, 17))}
    STREAM = {(lx, lz) for lx in range(21) for lz in range(21) if stream_d(lx, lz) <= 0.95} | POOL_N | POOL_S
    BARS = {(9, 12), (7, 8)}                                              # gravel bars in the stream
    EXPOSED = {(lx, lz) for lx in range(4, 7) for lz in range(13, 17)}    # a cut bank showing the Gravelrock
    ROCKBED = {(lx, lz) for lx in range(21) for lz in range(21)}          # Gravelrock under the whole riverbed
    FOOT = {(lx, lz) for lx in range(6, 12) for lz in range(9, 12)}       # foot bridge where the W-E path crosses
    kb = kiosk_box(pid)

    def height(lx, lz):
        if (lx, lz) in STREAM:
            return WL
        d = stream_d(lx, lz)
        h = WL if d < 1.9 else (G - 1 if d < 4.2 else G)                  # terraces stepping up from the water
        if lx >= 15 and lz <= 9:
            h = G
        if (lx, lz) in EXPOSED:
            h = B + 1
        if (lx, lz) in landing:
            h = G
        return h

    heights = {}
    for lx in range(21):
        for lz in range(21):
            if not mask(lx, lz):
                continue
            ed = edge_dist(mask, lx, lz, 3)
            h = height(lx, lz)
            if h >= G - 1 and (lx, lz) not in STREAM:
                h = max(WL, soften(h, ed, keep=(lx, lz) in landing or (lx, lz) in kiosk_cols(pid) or (lx, lz) == BOARD_AT[pid][0][::2]))
            heights[(lx, lz)] = h
            water = (lx, lz) in STREAM

            def fill_fn(x, y, z):
                r = h01(x, y, z, 21)
                return 'minecraft:clay' if r < 0.25 else 'minecraft:packed_mud' if r < 0.45 else 'minecraft:coarse_dirt'
            if water:
                column(ox + lx, oz + lz, WL - 1, 'minecraft:gravel' if (lx, lz) in BARS else None, fill_fn,
                       rock5=GRAVELROCK, depth=min(4, 1 + ed), seed=17)
                if (lx, lz) in BARS:
                    p(lx, WL, lz, 'minecraft:gravel')
                    p(lx, WL - 1, lz, 'minecraft:clay')
                else:
                    p(lx, WL, lz, WATER)
                    # pools: unbreakable floor right under the water (infinite source)
                    if (lx, lz) in POOL_N or (lx, lz) in POOL_S:
                        for y in range(B + 1, WL):
                            p(lx, y, lz, GRAVELROCK)
                    else:
                        p(lx, WL - 1, lz, 'minecraft:clay' if h01(lx, lz, 2) < 0.5 else 'minecraft:mud')
                continue
            if (lx, lz) in EXPOSED:
                top = None
            elif (lx, lz) in landing:
                top = 'minecraft:coarse_dirt'
            elif h >= G - 1 and h01(lx, lz, 3) < 0.07:
                top = 'minecraft:mossy_cobblestone'
            elif h == G and h01(lx, lz, 8) < 0.35:
                top = 'minecraft:coarse_dirt'
            else:
                top = 'minecraft:gravel'
            column(ox + lx, oz + lz, h, top, fill_fn, rock5=GRAVELROCK, depth=min(4, 1 + ed), seed=17)
    MASKS[pid] = {(ox + lx, oz + lz) for (lx, lz) in heights}
    GF.update(heights=heights, stream=STREAM, exposed=EXPOSED, pools=POOL_N | POOL_S)
    # ---- foot bridge over the stream (the W-E path)
    for (lx, lz) in FOOT:
        edge = lz in (9, 11)
        p(lx, G, lz, 'minecraft:spruce_planks' if not edge else log('stripped_spruce_log', 'x'))
        if (lx, lz) in STREAM or heights.get((lx, lz), G) < G:
            for y in range(heights.get((lx, lz), WL) + 1, G):
                if (lx in (6, 11) and edge):
                    p(lx, y, lz, log('spruce_log'))
                elif (lx, lz) not in STREAM:
                    p(lx, y, lz, None)
        if edge:
            p(lx, G + 1, lz, 'minecraft:spruce_fence')
    for lx in (6, 11):
        for lz in (9, 11):
            p(lx, G + 1, lz, log('spruce_log'))
            p(lx, G + 2, lz, 'minecraft:lantern{hanging:false,waterlogged:false}') if lx == 11 else None
    # paths across the terraces
    for lx in range(0, 21):
        lz = 10
        if heights.get((lx, lz)) is not None and (lx, lz) not in FOOT and (lx, lz) not in STREAM and heights[(lx, lz)] >= G - 1:
            pass
    # ---- water wheel + mill house on the south bank (landmark)
    MX0, MX1, MZ0, MZ1 = 7, 11, 16, 19
    for lx in range(MX0, MX1 + 1):
        for lz in range(MZ0, MZ1 + 1):
            if (lx, lz) not in heights:
                continue
            for y in range(heights[(lx, lz)] + 1, G):
                p(lx, y, lz, 'minecraft:cobblestone')
            p(lx, G, lz, 'minecraft:cobblestone' if lx in (MX0, MX1) or lz in (MZ0, MZ1) else 'minecraft:spruce_planks')
            heights[(lx, lz)] = G
            edge = lx in (MX0, MX1) or lz in (MZ0, MZ1)
            for y in range(G + 1, G + 4):
                if not edge:
                    p(lx, y, lz, None)
                    continue
                corner = lx in (MX0, MX1) and lz in (MZ0, MZ1)
                door = lz == MZ0 and lx == 9 and y <= G + 2
                win = not corner and y == G + 2 and (lx == MX1 or lz == MZ1)
                p(lx, y, lz, None if door else log('spruce_log') if corner else 'minecraft:glass_pane' if win else 'minecraft:spruce_planks')
    RZ = (MZ0 + MZ1) / 2.0
    for lx in range(MX0 - 1, MX1 + 2):                                    # gable roof, ridge along x
        for lz in range(MZ0 - 1, MZ1 + 2):
            d = abs(lz - RZ)
            y = G + 4 + int(2.5 - d)
            p(lx, y, lz, stairs('dark_oak', 'south' if lz < RZ else 'north'))
    for lx in (MX0, MX1):
        for lz in range(MZ0 + 1, MZ1):
            for y in range(G + 4, G + 4 + int(2.5 - abs(lz - RZ))):
                p(lx, y, lz, 'minecraft:spruce_planks')
    for lx in range(MX0, MX1 + 1):                                        # walls up to the underside of each roof row (no slit under the eaves)
        for lz in (MZ0, MZ1):
            for y in range(G + 4, G + 4 + int(2.5 - abs(lz - RZ))):
                p(lx, y, lz, log('spruce_log') if lx in (MX0, MX1) else 'minecraft:spruce_planks')
    lantern(ox + 9, G + 4, oz + 17, True)
    p(9, G + 5, 17, 'minecraft:chain{axis:y,waterlogged:false}')
    p(9, G + 1, MZ0 - 1, None)
    # the wheel: a ring in the y-z plane at lx 12 beside the mill, dipping into the stream
    wx, cy, cz, R = 12, G + 1, 15, 3
    ring = {(y, lz) for y in range(cy - R, cy + R + 1) for lz in range(cz - R, cz + R + 1) if abs(math.hypot(y - cy, lz - cz) - R) < 0.5}
    for (y, lz) in sorted(ring):          # close diagonal steps so the rim is one connected piece (nothing floats at its edges)
        for dy, dz in ((1, 1), (1, -1)):
            if (y + dy, lz + dz) in ring and (y + dy, lz) not in ring and (y, lz + dz) not in ring:
                cand = min(((y + dy, lz), (y, lz + dz)), key=lambda c: abs(math.hypot(c[0] - cy, c[1] - cz) - R))
                ring.add(cand)
    for y in range(cy - R, cy + R + 1):
        for lz in range(cz - R, cz + R + 1):
            d = math.hypot(y - cy, lz - cz)
            if (y, lz) in ring:
                p(wx, y, lz, 'minecraft:stripped_spruce_log{axis:x}' if (y + lz) % 2 else 'minecraft:spruce_planks')
            elif d < R - 0.5 and (y == cy or lz == cz):
                p(wx, y, lz, 'minecraft:stripped_spruce_log{axis:%s}' % ('z' if y == cy else 'y'))
    p(wx, cy, cz, log('spruce_log', 'x'))
    p(wx - 1, cy, cz, log('spruce_log', 'x'))
    # old sluice: a trough on posts from the north pool towards the mill, along the stream's east bank
    trough = [(6, 4), (7, 5), (8, 6)]
    for (lx, lz) in [(7, 4), (8, 4), (9, 4), (10, 4), (11, 4)]:
        if (lx, lz) in heights:
            p(lx, G + 1, lz, slab('spruce'))
            if lx % 2 == 1:
                for y in range(heights[(lx, lz)] + 1, G + 1):
                    p(lx, y, lz, 'minecraft:spruce_fence')
    p(6, G + 1, 4, None)
    # boulders
    for (lx, lz, n) in ((3, 9, 2), (4, 9, 1), (16, 12, 1), (17, 11, 2), (18, 12, 1), (14, 19, 1), (2, 16, 1)):
        hh = heights.get((lx, lz))
        if hh is None or (lx, lz) in STREAM or (lx, lz) in EXPOSED:
            continue
        for i in range(n):
            p(lx, hh + 1 + i, lz, pick(['minecraft:andesite', 'minecraft:mossy_cobblestone', 'minecraft:cobblestone'], lx, lz, i))
    # cut bank: a ladder out, a lantern stake, a sign
    p(4, B + 2, 12, None)
    for y in range(B + 2, heights.get((5, 12), G) + 1):
        p(5, y, 12, None) if False else None
    for y in range(B + 2, G):
        if heights.get((7, 14), 0) >= y:
            p(7, y, 14, 'minecraft:coarse_dirt')
    for y in range(B + 2, heights.get((5, 17), B) + 1):
        p(5, y, 17, get(ox + 5, y, oz + 17))
    for y in range(B + 2, (heights.get((5, 17)) or B) + 1):
        p(5, y + 0, 16, None) if False else None
    ly = heights.get((5, 17))
    if ly is not None:
        for y in range(B + 2, ly + 1):
            p(5, y, 16, 'minecraft:ladder{facing:north,waterlogged:false}') if False else None
    # ladder on the cut bank's south wall
    for y in range(B + 2, (heights.get((5, 17)) or B + 1) + 1):
        pass
    sign_post(p, 3, G + 1, 10, 4, ['GRAVEL FLATS', 'Rock under', 'the banks', ''])
    board(pid)
    clear(*kb)
    plot_lights(pid, heights, [(5, B + 3, 14), (9, WL + 3, 11), (14, WL + 3, 16), (3, WL + 3, 4)])
    return heights


# ---------------------------------------------------------------- Sand Dunes: desert
SD = {}


def build_sand_dunes():
    pid = 'sand_dunes'
    p = P(pid)
    ox, oy, oz = SITES[pid][1]
    landing = {(lx, lz) for lx in range(0, 3) for lz in range(14, 19)}
    mask = plot_mask(0.9, landing, 10.8, 10.8, pid=pid)
    OASIS = {(lx, lz) for lx in range(12, 16) for lz in range(11, 15)} - {(12, 11), (15, 14)}
    HOLLOWS = [((16.5, 4.5), 2.7), ((5.0, 3.5), 2.4), ((17.5, 18.0), 2.3)]   # wind-carved, Sandrock showing
    BEDS = [((16.5, 4.5), 4.2), ((5.0, 3.5), 3.8), ((17.5, 18.0), 3.6), ((9.0, 19.0), 2.6), ((7.5, 10.0), 2.6)]
    DUNES = [((9.0, 5.5), 5.2, 6), ((17.5, 10.0), 3.6, 4), ((7.5, 16.5), 3.8, 4), ((3.0, 9.5), 2.6, 2), ((12.5, 19.0), 2.6, 2)]
    kb = kiosk_box(pid)

    def hollow_depth(lx, lz):
        for (c, r) in HOLLOWS:
            d = math.hypot(lx - c[0], lz - c[1])
            if d < r:
                return 4 if d < r * 0.55 else 3 if d < r * 0.8 else 1
        return 0

    def bed(lx, lz):
        return any(math.hypot(lx - c[0], lz - c[1]) < r for (c, r) in BEDS)

    def height(lx, lz):
        h = G
        for (c, r, a) in DUNES:
            d = math.hypot((lx - c[0]) / r, (lz - c[1]) / (r * 0.75))
            if d < 1:
                h = max(h, G + int(round(a * (1 - d * d) ** 1.2)))
        hd = hollow_depth(lx, lz)
        if hd:
            h = G - hd                         # 4 deep = the Sandrock (y 5) is the floor
        if (lx, lz) in OASIS:
            h = G
        if (lx, lz) in landing:
            h = G
        return max(h, B + 1)

    heights = {}
    for lx in range(21):
        for lz in range(21):
            if not mask(lx, lz):
                continue
            ed = edge_dist(mask, lx, lz, 3)
            h = height(lx, lz)
            if h >= G and (lx, lz) not in OASIS:
                h = soften(h, ed, keep=(lx, lz) in landing or (lx, lz) in kiosk_cols(pid) or (lx, lz) == BOARD_AT[pid][0][::2])
            heights[(lx, lz)] = h

            def fill_fn(x, y, z, h=h):
                return 'minecraft:sandstone' if h01(x, y, z, 23) < 0.8 else 'minecraft:smooth_sandstone'
            rock5 = SANDROCK if bed(lx, lz) else DIRTROCK
            if (lx, lz) in OASIS:
                column(ox + lx, oz + lz, h - 1, SANDROCK if bed(lx, lz) else 'minecraft:smooth_sandstone', fill_fn,
                       rock5=rock5, depth=min(4, 1 + ed), seed=23)
                for y in range(B + 2, h):
                    p(lx, y, lz, DIRTROCK)            # unbreakable pool floor
                p(lx, h, lz, WATER)
                continue
            hd = hollow_depth(lx, lz)
            if h <= B + 1:
                top = None
            elif (lx, lz) in landing:
                top = 'minecraft:smooth_sandstone'
            elif hd >= 3:
                top = 'minecraft:sandstone'
            elif hd:
                top = 'minecraft:sand'
            else:
                top = 'minecraft:sand' if h >= G or h01(lx, lz, 9) < 0.6 else 'minecraft:sandstone'
            column(ox + lx, oz + lz, h, top, fill_fn, rock5=rock5, depth=min(4, 1 + ed), seed=23)
            # crests get two layers of sand (still small: the value is in the Sandrock)
            if h >= G + 3:
                p(lx, h - 1, lz, 'minecraft:sand')
    MASKS[pid] = {(ox + lx, oz + lz) for (lx, lz) in heights}
    SD.update(heights=heights, beds=BEDS, hollows=HOLLOWS, oasis=OASIS)
    # oasis: grass rim, palms, sugar cane, lily pads
    for lx in range(10, 18):
        for lz in range(9, 17):
            if (lx, lz) in OASIS or (lx, lz) not in heights:
                continue
            if any((lx + dx, lz + dz) in OASIS for dx in (-1, 0, 1) for dz in (-1, 0, 1)) and heights[(lx, lz)] == G:
                p(lx, G, lz, 'minecraft:grass_block{snowy:false}' if h01(lx, lz, 31) < 0.7 else 'minecraft:moss_block')
                if h01(lx, lz, 33) < 0.3 and get(ox + lx, G + 1, oz + lz) is None:
                    p(lx, G + 1, lz, 'minecraft:short_grass')
    palm(ox + 11, G + 1, oz + 10, 6, (0, -1))
    palm(ox + 16, G + 1, oz + 15, 7, (1, 1))
    palm(ox + 16, G + 1, oz + 11, 5, (1, -1))
    for (lx, lz) in ((11, 13), (13, 15)):
        if (lx, lz) in heights:
            for y in (G + 1, G + 2):
                p(lx, y, lz, 'minecraft:sugar_cane{age:0}')
    p(13, G, 12, 'minecraft:lily_pad') if False else None
    # ruins: an arch and a broken colonnade on the dune north of the oasis
    ax_, az_ = 12, 6
    for lz in (az_, az_ + 3):
        base_y = heights.get((ax_, lz), G)
        for y in range(base_y + 1, G + 5):
            p(ax_, y, lz, 'minecraft:chiseled_sandstone' if y == G + 2 else 'minecraft:cut_sandstone')
    for lz in range(az_, az_ + 4):
        p(ax_, G + 5, lz, 'minecraft:smooth_sandstone' if lz in (az_, az_ + 3) else slab('smooth_sandstone'))
    p(ax_, G + 4, az_ + 1, stairs('sandstone', 'south', 'top'))
    p(ax_, G + 4, az_ + 2, stairs('sandstone', 'north', 'top'))
    for lx in range(13, 18):
        hh = heights.get((lx, az_), G)
        top = G + (3 if lx < 15 else 1 if lx < 17 else 0)
        for y in range(hh + 1, top + 1):
            p(lx, y, az_, 'minecraft:cut_sandstone' if (lx + y) % 3 else 'minecraft:chiseled_sandstone')
        if top > hh:
            p(lx, top + 1, az_, slab('sandstone') if lx in (14, 16) else None)
    for (lx, lz, hh) in ((19, 8, 3), (14, 2, 2), (3, 19, 2), (8, 13, 1)):
        if (lx, lz) not in heights:
            continue
        b0 = heights[(lx, lz)]
        for y in range(b0 + 1, b0 + 1 + hh):
            p(lx, y, lz, 'minecraft:cut_sandstone' if y < b0 + hh else 'minecraft:chiseled_sandstone')
    # dead bushes + cactus on sand
    for (lx, lz) in ((5, 9), (9, 15), (18, 13), (3, 6), (14, 18), (10, 3), (19, 15)):
        hh = heights.get((lx, lz))
        if hh is not None and get(ox + lx, hh, oz + lz) == 'minecraft:sand' and get(ox + lx, hh + 1, oz + lz) is None:
            p(lx, hh + 1, lz, 'minecraft:dead_bush')
    # hollows: ladders/steps are not needed (they are 1-step bowls); a lantern stake at each
    for (c, r) in HOLLOWS:
        lx, lz = int(c[0] + r * 0.9), int(c[1])
        if (lx, lz) in heights:
            lantern_post(ox + lx, heights[(lx, lz)] + 1, oz + lz, 'minecraft:birch_fence', 1)
    sign_post(p, 3, G + 1, 12, 4, ['SAND DUNES', 'Dig the', 'hollows', ''], 'birch')
    board(pid)
    clear(*kb)
    plot_lights(pid, heights, [(16, B + 4, 4), (5, B + 4, 3), (17, B + 4, 18), (13, G + 2, 12)])
    return heights


# ================================================================================================ KIOSKS
KIOSK_THEMES = {
    # wood, post, canopy A, canopy B, floor, foundation, counter, sign wood, extra
    'deep_dig': dict(wood='minecraft:spruce_planks', post=log('spruce_log'), a='minecraft:spruce_planks',
                     b='minecraft:dark_oak_planks', floor='minecraft:spruce_planks', found='minecraft:cobblestone',
                     counter=log('stripped_spruce_log', 'x'), sign='spruce', roof='spruce', style='shack'),
    'gravel_flats': dict(wood='minecraft:oak_planks', post=log('stripped_oak_log'), a='minecraft:light_blue_wool',
                         b='minecraft:white_wool', floor='minecraft:oak_planks', found='minecraft:mossy_cobblestone',
                         counter=log('stripped_oak_log', 'x'), sign='oak', roof='oak', style='stall'),
    'sand_dunes': dict(wood='minecraft:smooth_sandstone', post='minecraft:cut_sandstone', a='minecraft:orange_wool',
                       b='minecraft:white_wool', floor='minecraft:smooth_sandstone', found='minecraft:sandstone',
                       counter='minecraft:cut_sandstone', sign='birch', roof='sandstone', style='tent'),
}


def build_kiosk(pid):
    """A 7 x 7 shop kiosk built facing north (front open at kz 0) and rotated into place. Floor at ky 2 (= G)."""
    t = KIOSK_THEMES[pid]
    loc = {}

    def K(kx, ky, kz, st, nbt=None):
        if st is None:
            loc.pop((kx, ky, kz), None)
        else:
            loc[(kx, ky, kz)] = (st, nbt)
    for kx in range(7):
        for kz in range(7):
            K(kx, 0, kz, t['found'])
            K(kx, 1, kz, t['found'])
            K(kx, 2, kz, t['floor'] if 0 < kx < 6 and kz > 0 else t['found'])
    for (kx, kz) in ((0, 0), (6, 0), (0, 6), (6, 6)):
        for ky in range(3, 6):
            K(kx, ky, kz, t['post'])
    # back + side walls up to the counters
    for kx in range(1, 6):
        K(kx, 3, 6, t['wood'])
        K(kx, 4, 6, t['wood'] if t['style'] != 'stall' else None)
    for kz in range(1, 6):
        for kx in (0, 6):
            K(kx, 3, kz, t['wood'])
    # displays: back counter (kz 5) + the side counters (kx 1 and 5)
    slots = []
    for (kx, kz) in ((2, 5), (4, 5), (1, 4), (5, 4), (1, 2), (5, 2), (3, 5)):
        K(kx, 3, kz, t['counter'])
        slots.append((kx, kz))
    for kx in range(1, 6):
        K(kx, 3, 5, t['counter'])
    for kz in (1, 3):
        K(1, 3, kz, t['counter'])
        K(5, 3, kz, t['counter'])
    for i, (kx, kz) in enumerate(slots):
        K(kx, 4, kz, 'holeydigsite:shop_display', '{id:"holeydigsite:shop_display",Slot:%d}' % (i + 1))
    # checkout in the middle: Sell Pad - Button - Buy Pad, Money Pan behind the button
    K(2, 3, 3, 'holeydigsite:sell_pad{locked:true}')
    K(3, 3, 3, 'holeydigsite:shop_button{locked:true,pressed:false}')
    K(4, 3, 3, 'holeydigsite:buy_pad{locked:true}')
    K(3, 3, 4, 'holeydigsite:money_pan{locked:true,pile:0}')
    # roof
    style = t['style']
    if style == 'shack':
        # pent roof sloping to the front, plank canopy
        for kx in range(-0, 7):
            for kz in range(0, 7):
                y = 6 + (1 if kz >= 3 else 0) + (1 if kz >= 6 else 0)
                K(kx, y, kz, stairs(t['roof'], 'south') if kz in (0, 3) else t['a'] if kx % 2 == 0 else t['b'])
        for kx in (0, 6):
            for kz in range(1, 6):
                for ky in range(4, 6 + (1 if kz >= 3 else 0)):
                    K(kx, ky, kz, t['wood'] if ky > 3 else None)
        for kx in range(1, 6):
            for ky in range(5, 8):
                K(kx, ky, 6, t['wood'])
        K(3, 5, 2, 'minecraft:lantern{hanging:true,waterlogged:false}')
        K(1, 5, 5, 'minecraft:lantern{hanging:true,waterlogged:false}') if False else None
        # mine-cart wheels look: an ore cart (iron trapdoors + coal) beside the kiosk front
        K(0, 3, 0, t['post'])
    elif style == 'stall':
        # striped market awning, peaked
        for kx in range(7):
            for kz in range(7):
                d = abs(kz - 3)
                y = 8 - (1 if d >= 2 else 0) - (1 if d >= 3 else 0)
                K(kx, y, kz, t['a'] if kx % 2 == 0 else t['b'])
        for kz in range(7):
            K(3, 9, kz, slab('oak')) if False else None
        K(3, 7, 3, 'minecraft:chain{axis:y,waterlogged:false}')
        K(3, 6, 3, 'minecraft:chain{axis:y,waterlogged:false}')
        K(3, 5, 3, 'minecraft:lantern{hanging:true,waterlogged:false}')
        for (kx, kz) in ((0, 0), (6, 0), (0, 6), (6, 6)):
            K(kx, 6, kz, t['post'])
        # fishing-hut touches: barrels, a net (cobweb-free: string = no), oars
        K(0, 4, 3, 'minecraft:barrel{facing:up,open:false}') if False else None
    else:  # tent
        for kx in range(7):
            for kz in range(7):
                d = abs(kx - 3)
                y = 6 + (3 - d) // 1 - (1 if d == 3 else 0)
                y = {0: 9, 1: 8, 2: 7, 3: 6}[d]
                K(kx, y, kz, t['a'] if (kz % 2 == 0) else t['b'])
        K(3, 8, 3, 'minecraft:chain{axis:y,waterlogged:false}')
        K(3, 7, 3, 'minecraft:chain{axis:y,waterlogged:false}')
        K(3, 6, 3, 'minecraft:lantern{hanging:true,waterlogged:false}')
        for (kx, kz) in ((0, 0), (6, 0), (0, 6), (6, 6)):
            K(kx, 6, kz, t['post'])
        K(0, 3, 0, 'minecraft:decorated_pot{facing:north,waterlogged:false,cracked:false}') if False else None
    # Phase 16.1: the canopy steps up one block per row, so neighbouring rows used to touch only at their edges (gaps and roof bits
    # that looked unsupported). Fill the step under each higher block so every row joins the next one face to face.
    roof_kinds = {base(t['a']), base(t['b'])}
    roof = {c: v for c, v in loc.items() if c[1] >= 6 and (base(v[0]) in roof_kinds or base(v[0]).endswith('_stairs'))}
    for (kx, ky, kz), (st, _) in sorted(roof.items()):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (kx + dx, ky - 1, kz + dz) in roof and (kx, ky - 1, kz) not in loc and (kx + dx, ky, kz + dz) not in loc:
                K(kx, ky - 1, kz, t['a'] if base(st).endswith('_stairs') else st)
    # name board hanging from the canopy's front edge (chains down to it if the canopy is high there) + invisible light
    K(3, 4, 1, LIGHT)
    roof_y = min(ky for (kx, ky, kz) in loc if kx == 3 and kz == 0 and ky > 5)
    for ky in range(6, roof_y):
        K(3, ky, 0, 'minecraft:chain{axis:y,waterlogged:false}')
    K(3, 5, 0, 'minecraft:%s_hanging_sign{attached:false,rotation:8,waterlogged:false}' % t['sign'],
      hanging_sign_nbt([PLOT_NAMES[pid], 'Supplies'], 'black'))
    (kx0, ky0, kz0), facing = KIOSK_AT[pid]
    ox, oy, oz = SITES[pid][1]
    clear(*kiosk_box(pid))
    stamp(loc, KIOSK, (ox + kx0, oy + ky0, oz + kz0), facing)


# ================================================================================================ BRIDGES
BRIDGE_COLS = set()      # (x, z) columns of the bridges (they span the void on purpose)


def bridge(x0, z0, x1, z1, y, along):
    """Rope-and-plank bridge; along = 'x' or 'z'. Deck y, rails on the long edges, lanterns at both ends."""
    xs, zs = range(x0, x1 + 1), range(z0, z1 + 1)
    BRIDGE_COLS.update((x, z) for x in xs for z in zs)
    for x in xs:
        for z in zs:
            edge = (z in (z0, z1)) if along == 'x' else (x in (x0, x1))
            put(x, y, z, 'minecraft:spruce_planks' if edge else slab('spruce', 'top'))
            if edge:
                put(x, y - 1, z, log('spruce_log', along))
                put(x, y + 1, z, 'minecraft:spruce_fence')
    ends = [(x0, z0), (x1, z0), (x0, z1), (x1, z1)]
    for (x, z) in ends:
        put(x, y + 1, z, log('spruce_log'))
        lantern(x, y + 2, z)
    put((x0 + x1) // 2, y + 4, (z0 + z1) // 2, LIGHT)


# ================================================================================================ OUTPUT
def snbt(blocks, size, origin):
    ox, oy, oz = origin
    palette, idx, data = [], {}, []
    for (x, y, z) in sorted(blocks, key=lambda q: (q[1], q[2], q[0])):
        st, nbt = blocks[(x, y, z)]
        if st == AIR:
            continue
        if st not in idx:
            idx[st] = len(palette)
            palette.append(st)
        e = '{pos:[%d,%d,%d],state:"%s"' % (x - ox, y - oy, z - oz, st.replace('"', '\\"'))
        if nbt:
            e += ',nbt:' + nbt
        data.append(e + '}')
    return '{DataVersion:3955,size:[%d,%d,%d],entities:[],palette:[%s],data:[%s]}' % (
        size[0], size[1], size[2], ','.join('"%s"' % s for s in palette), ','.join(data))


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)


def build():
    """Builds the whole world into W (idempotent)."""
    W.clear()
    MASKS.clear()
    SETTLED.clear()
    BRIDGE_COLS.clear()
    STORE_SLOTS.clear()
    build_home()
    build_store()
    build_deep_dig()
    build_gravel_flats()
    build_sand_dunes()
    for pid in PLOTS:
        build_kiosk(pid)
    bridge(8, 32, 12, 35, G, 'z')       # home -> Deep Dig
    bridge(35, 32, 39, 35, G, 'z')      # home -> Gravel Flats
    bridge(21, 44, 26, 48, G, 'x')      # Deep Dig -> Gravel Flats
    bridge(48, 50, 53, 54, G, 'x')      # Gravel Flats -> Sand Dunes
    settle_floating()
    fill_slits()
    connect()
    return W


def owner_of(k):
    for pid in PLOTS:
        if inside(kiosk_box(pid), *k):
            return pid + '_shop'
    for sid in SITES:
        if inside(site_box(sid), *k):
            return sid
    return None


def split():
    parts = {sid: {} for sid in SITES}
    parts.update({pid + '_shop': {} for pid in PLOTS})
    template = {}
    for k, v in W.items():
        o = owner_of(k)
        (parts[o] if o else template)[k] = v
    return parts, template


def struct_name(sid):
    if sid.endswith('_shop') or SITES[sid][0] == 'store':
        return 'store/%s_v%d' % (sid, VERSIONS[sid])
    return 'plot/%s_v%d' % (sid, VERSIONS[sid])


def box_of(sid):
    return kiosk_box(sid[:-5]) if sid.endswith('_shop') else site_box(sid)


SCOOPABLE = ('minecraft:grass_block', 'minecraft:dirt', 'minecraft:gravel', 'minecraft:sand', 'minecraft:red_sand')


def economy_counts():
    """Free scoopable blocks per area (the shovel turns each into a clump of that kind)."""
    def count(box_or_set, kinds):
        n = 0
        for k, v in W.items():
            if base(v[0]) in kinds and (inside(box_or_set, *k) if isinstance(box_or_set, tuple) else (k[0], k[2]) in box_or_set):
                n += 1
        return n
    home_cols = MASKS['home']
    return {
        'home': {'dirt': count(home_cols, ('minecraft:grass_block', 'minecraft:dirt'))},
        'deep_dig': {'dirt': count(site_box('deep_dig'), ('minecraft:grass_block', 'minecraft:dirt')),
                     'gravel': count(site_box('deep_dig'), ('minecraft:gravel',)),
                     'sand': count(site_box('deep_dig'), ('minecraft:sand', 'minecraft:red_sand'))},
        'gravel_flats': {'dirt': count(site_box('gravel_flats'), ('minecraft:grass_block', 'minecraft:dirt')),
                         'gravel': count(site_box('gravel_flats'), ('minecraft:gravel',)),
                         'sand': count(site_box('gravel_flats'), ('minecraft:sand', 'minecraft:red_sand'))},
        'sand_dunes': {'dirt': count(site_box('sand_dunes'), ('minecraft:grass_block', 'minecraft:dirt')),
                       'gravel': count(site_box('sand_dunes'), ('minecraft:gravel',)),
                       'sand': count(site_box('sand_dunes'), ('minecraft:sand', 'minecraft:red_sand'))},
        'rock_cells': {r.split(':')[1]: sum(1 for k, v in W.items() if v[0] == r and inside(site_box(p), *k))
                       for p, r in (('deep_dig', DEEP), ('deep_dig', ABYSSAL), ('gravel_flats', GRAVELROCK), ('sand_dunes', SANDROCK))},
    }


def main():
    build()
    parts, template = split()
    os.makedirs(OUT_SNBT, exist_ok=True)
    for sid, blocks in parts.items():
        out = os.path.join(OUT_SNBT, struct_name(sid) + '.snbt')
        if sid in HAND_BUILT:
            if os.path.exists(out):
                os.remove(out)               # never convert over the hand-made .nbt
            continue
        b = box_of(sid)
        size = (b[3] - b[0] + 1, b[4] - b[1] + 1, b[5] - b[2] + 1)
        write(os.path.join(OUT_SNBT, struct_name(sid) + '.snbt'), snbt(blocks, size, b[:3]))
    for sid in SITES:                          # one marker per top-level site (kiosks are children of their plot)
        template[SITES[sid][1]] = ('holeydigsite:site_marker', '{id:"holeydigsite:site_marker",Site:"holeydigsite:%s"}' % sid)
    write(os.path.join(PACK, '_source', 'island', 'holey_digsite.snbt'), snbt(template, (TW, TH, TD), (0, 0, 0)))

    # ---- site definitions + layouts
    def site_json(sid, obj):
        write(os.path.join(DATA, 'site', sid + '.json'), json.dumps(obj, indent=2) + '\n')
    site_json('home', {'kind': 'store', 'name': 'site.holeydigsite.home', 'version': VERSIONS['home'],
                       'structure': 'holeydigsite:' + struct_name('home'), 'size': list(SITES['home'][2]),
                       'entrance': [0, G + 2, 10], 'protect': True, 'layout': 'holeydigsite:home'})
    for pid in PLOTS:
        (bx, by, bz), _ = BOARD_AT[pid]
        (kx, ky, kz), facing = KIOSK_AT[pid]
        site_json(pid, {'kind': 'plot', 'name': 'site.holeydigsite.' + pid, 'version': VERSIONS[pid],
                        'structure': 'holeydigsite:' + struct_name(pid), 'size': list(SITES[pid][2]),
                        'entrance': list(PLOT_ENTRANCE[pid]), 'protect': False, 'plot': pid, 'board': [bx, by, bz],
                        'children': [{'site': 'holeydigsite:%s_shop' % pid, 'pos': [kx, ky, kz]}]})
        front = {'north': [3, 3, -1], 'south': [3, 3, 7], 'east': [7, 3, 3], 'west': [-1, 3, 3]}[facing]
        site_json(pid + '_shop', {'kind': 'store', 'name': 'site.holeydigsite.%s_shop' % pid, 'version': VERSIONS[pid + '_shop'],
                                  'structure': 'holeydigsite:' + struct_name(pid + '_shop'), 'size': list(KIOSK),
                                  'entrance': front, 'protect': True, 'layout': 'holeydigsite:%s_shop' % pid,
                                  'plot': pid, 'board': [bx - kx, by - ky, bz - kz]})
        if pid + '_shop' not in HAND_BUILT:
            write(os.path.join(DATA, 'store_layout', pid + '_shop.json'),
              json.dumps({'slots': {str(i + 1): it for i, it in enumerate(KIOSK_ITEMS[pid]) if it}}, indent=2) + '\n')
    lay = {}
    for row, items in HOME_ROWS.items():
        for s, it in zip(STORE_SLOTS[row], items):
            if it:
                lay[str(s)] = it
    if 'home' not in HAND_BUILT:
        write(os.path.join(DATA, 'store_layout', 'home.json'),
          json.dumps({'slots': dict(sorted(lay.items(), key=lambda kv: int(kv[0])))}, indent=2) + '\n')
    rep = economy_counts()
    rep['home_slots'] = sum(len(v) for v in STORE_SLOTS.values())
    rep['home_items'] = len(lay)
    rep['spawn'] = SPAWN
    write(os.path.join(OUT_SNBT, 'report.json'), json.dumps(rep, indent=2))
    print(json.dumps(rep))
    for w in sorted(set(WIDTH_WARNINGS)):
        print('SIGN WIDTH:', w)
    print('blocks', len(W), 'structures', sorted(struct_name(s) for s in parts))


if __name__ == '__main__':
    main()
