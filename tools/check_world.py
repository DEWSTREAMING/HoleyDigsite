#!/usr/bin/env python3
"""Safety checks for the generated world (python tools/check_world.py). Exit 0 = all good.

  * unbreakable rock at y B under every island column (no digging to the void), kiosks stand on their plot
  * water: contained (no flowing out), every pool has a 2x2 of sources (infinite)
  * sand / gravel supported; plants, signs, lanterns, ladders, rails, cocoa attached
  * block light > 0 on every standable cell of the islands and bridges (BFS light, like the game, without sky light)
  * spawn point: two air blocks on solid ground
  * nothing but leaves, roof eaves and bridges beyond an island's edge (Phase 16.1)
  * no air slit under ground-level blocks (Phase 16.1)
  * connectivity (Phase 16.1): every non-air block joins the island's unbreakable rock through face-adjacent non-air blocks;
    each detached group is reported with its template coordinates and its owner site (nothing floats in mid-air)
The in-game checks (devtests, headless HoleyTest) confirm the same things in a real world."""
import os, sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_world as mw

errors = []


def err(msg):
    errors.append(msg)


REPLACEABLE = ('short_grass', 'poppy', 'dandelion', 'cornflower', 'daisy', 'bluet', 'dead_bush', 'fern', 'minecraft:light')
SOIL = ('grass_block', 'moss_block', 'dirt', 'coarse_dirt', 'podzol', 'rooted_dirt', 'mud', 'packed_mud')


def b(st):
    return mw.base(st) if st else None


def solid(st):
    """Full, opaque-ish block (water can't flow into it, mobs can stand on it)."""
    return st is not None and mw.full_block(st) and 'water' not in st and 'glass' not in b(st) and 'light' != b(st).split(':')[1]


def ceiling(st):
    """Can a lantern / hanging sign hang under this block (center support on its bottom face)?"""
    if st is None or b(st) == 'minecraft:light':
        return False
    if 'type:top' in st or 'half:top' in st:
        return False      # top slabs / upside-down stairs have no bottom face at the block's edge
    return mw.full_block(st) or 'chain' in st or 'slab' in st or 'stairs' in st or 'fence' in st or 'leaves' in st


def main():
    mw.build()
    Wd = mw.W
    g = mw.get
    # ---- 1. rock under every island column
    for name, cols in mw.MASKS.items():
        for (x, z) in cols:
            st = g(x, mw.B, z)
            if st not in mw.ROCKS:
                err('%s column %d,%d: y%d is %s, not unbreakable rock' % (name, x, z, mw.B, st))
    all_cols = set().union(*mw.MASKS.values())
    for (x, y, z), (st, _) in Wd.items():
        if y < mw.G - 1 and (x, z) not in all_cols and b(st) != 'holeydigsite:site_marker':
            err('terrain outside any island mask at %d,%d,%d: %s' % (x, y, z, st))
            break
    for pid in mw.PLOTS:
        kb = mw.kiosk_box(pid)
        for x in range(kb[0], kb[3] + 1):
            for z in range(kb[2], kb[5] + 1):
                if (x, z) not in mw.MASKS[pid]:
                    err('%s kiosk overhangs the plot at %d,%d' % (pid, x, z))
    # ---- 2. water
    water = {k for k, (st, _) in Wd.items() if b(st) == 'minecraft:water'}
    for (x, y, z) in water:
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
            n = (x + dx, y + dy, z + dz)
            if n in water:
                continue
            st = g(*n)
            if st is None or any(r in b(st) for r in REPLACEABLE) or not mw.full_block(st) and 'sugar_cane' not in st:
                if st is not None and any(k in b(st) for k in ('stairs', 'slab', 'fence', 'pane', 'ladder', 'sign', 'lantern', 'leaves')):
                    continue   # waterloggable / not flowable-into: water does not flow into these
                err('water at %s leaks into %s (%s)' % ((x, y, z), n, st))
    seen = set()
    for w0 in water:
        if w0 in seen:
            continue
        comp, q = set(), deque([w0])
        while q:
            c = q.popleft()
            if c in comp:
                continue
            comp.add(c)
            for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
                n = (c[0] + d[0], c[1], c[2] + d[2])
                if n in water and n not in comp:
                    q.append(n)
        seen |= comp
        if not any({(x, y, z), (x + 1, y, z), (x, y, z + 1), (x + 1, y, z + 1)} <= comp for (x, y, z) in comp):
            err('water body at %s (%d blocks) has no 2x2 of sources' % (w0, len(comp)))
    # ---- 3. gravity blocks
    for (x, y, z), (st, _) in Wd.items():
        if b(st) in ('minecraft:sand', 'minecraft:gravel', 'minecraft:red_sand'):
            below = g(x, y - 1, z)
            if below is None or b(below) == 'minecraft:water':
                err('%s at %d,%d,%d would fall' % (st, x, y, z))
    # ---- 4. attachments
    for (x, y, z), (st, _) in Wd.items():
        bb = b(st)
        below, above = g(x, y - 1, z), g(x, y + 1, z)
        if any(k in bb for k in ('poppy', 'dandelion', 'cornflower', 'daisy', 'bluet', 'short_grass', 'fern')):
            if not below or not any(s in b(below) for s in SOIL):
                err('plant %s at %d,%d,%d has no soil (%s)' % (bb, x, y, z, below))
        elif bb.endswith('_fence') and all('%s:false' % d in st for d in ('east', 'north', 'south', 'west')):
            if not mw.structural(below):        # a lone post must stand on something (it may touch a wall, but not hover beside it)
                err('fence post at %d,%d,%d hovers over %s' % (x, y, z, below))
        elif bb == 'minecraft:dead_bush' and (not below or not any(s in b(below) for s in ('sand', 'dirt', 'podzol', 'terracotta'))):
            err('dead bush at %d,%d,%d not on sand' % (x, y, z))
        elif bb == 'minecraft:sugar_cane':
            if b(below) == 'minecraft:sugar_cane':
                continue
            if not below or not any(s in b(below) for s in ('sand', 'grass_block', 'dirt', 'moss', 'mud')):
                err('sugar cane at %d,%d,%d not on soil' % (x, y, z))
            elif not any(b(g(x + dx, y - 1, z + dz)) == 'minecraft:water' for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                err('sugar cane at %d,%d,%d has no water next to its soil' % (x, y, z))
        elif bb == 'minecraft:lantern':
            if 'hanging:true' in st and not ceiling(above):
                err('hanging lantern at %d,%d,%d hangs from %s (needs a full bottom face or a chain)' % (x, y, z, above))
            if 'hanging:false' in st and (below is None or b(below) == 'minecraft:light'):
                err('lantern at %d,%d,%d stands on nothing' % (x, y, z))
        elif bb.endswith('_wall_sign') or bb == 'minecraft:ladder' or bb == 'minecraft:cocoa':
            f = st.split('facing:')[1].split(',')[0].rstrip('}')
            d = {'north': (0, 1), 'south': (0, -1), 'east': (-1, 0), 'west': (1, 0)}[f]
            if bb == 'minecraft:cocoa':
                d = (-d[0], -d[1])     # cocoa faces its log
            back = g(x + d[0], y, z + d[1])
            if back is None or b(back) == 'minecraft:light' or (bb == 'minecraft:cocoa' and 'jungle_log' not in back):
                err('%s at %d,%d,%d has nothing behind it (%s)' % (bb, x, y, z, back))
        elif bb.endswith('_hanging_sign'):
            if not ceiling(above):
                err('hanging sign at %d,%d,%d hangs from nothing' % (x, y, z))
        elif bb.endswith('_sign') or bb == 'minecraft:rail' or bb == 'holeydigsite:plot_board':
            if below is None or not mw.full_block(below):
                err('%s at %d,%d,%d stands on %s' % (bb, x, y, z, below))
    # ---- 5. light
    light = {}
    q = deque()
    for k, (st, _) in Wd.items():
        if b(st) == 'minecraft:light':
            light[k] = 15
            q.append(k)
        elif b(st) == 'minecraft:lantern':
            light[k] = 15
            q.append(k)
    while q:
        c = q.popleft()
        lv = light[c]
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
            if not (0 <= n[0] < mw.TW + 4 and -2 <= n[1] < mw.TH + 4 and 0 <= n[2] < mw.TD + 4):
                continue
            st = g(*n)
            if st is not None and mw.full_block(st) and 'glass' not in st and 'leaves' not in st:
                continue
            nl = lv - (2 if st is not None and ('leaves' in st or 'water' in st) else 1)
            if nl > light.get(n, 0):
                light[n] = nl
                q.append(n)
    dark = []
    walk_cols = all_cols | {(x, z) for (x, y, z), (st, _) in Wd.items() if y == mw.G}
    for (x, z) in walk_cols:
        for y in range(1, mw.TH):
            st, below, above = g(x, y, z), g(x, y - 1, z), g(x, y + 1, z)
            standable = (st is None or any(r in b(st) for r in REPLACEABLE)) and solid(below) and \
                (above is None or any(r in b(above) for r in REPLACEABLE) or not mw.full_block(above))
            if standable and 'leaves' not in (below or '') and light.get((x, y, z), 0) <= 0:
                dark.append((x, y, z))
    for d in dark[:25]:
        err('dark standable cell %s (on %s)' % (d, g(d[0], d[1] - 1, d[2])))
    if len(dark) > 25:
        err('... %d dark cells in total' % len(dark))
    # ---- 6. spawn
    sx, sy, sz = mw.SPAWN
    if g(sx, sy, sz) not in (None,) and not any(r in b(g(sx, sy, sz)) for r in REPLACEABLE) or g(sx, sy + 1, sz) is not None \
            or not solid(g(sx, sy - 1, sz)):
        err('spawn %s is not 2 air on solid ground: %s / %s / %s' % (mw.SPAWN, g(sx, sy - 1, sz), g(sx, sy, sz), g(sx, sy + 1, sz)))
    # ---- 7. connectivity: no floating blocks
    for grp in mw.detached_groups(Wd):
        err('floating group of %d block(s) at %s [%s]: %s' % (len(grp), min(grp), mw.owner_of(min(grp)) if hasattr(mw, 'owner_of') else '?',
                                                          sorted({b(g(*k)) for k in grp})[:4]))
    # ---- 8. nothing hangs off an island's edge (a prop beside a post or wall over the void still "connects" in step 7)
    on_island = set().union(*mw.MASKS.values()) | mw.BRIDGE_COLS
    for (x, y, z), (st, _) in Wd.items():
        bb = b(st)
        if (x, z) not in on_island and mw.structural(st) and not bb.endswith(('leaves', '_stairs', '_slab')) and bb != 'minecraft:cocoa':
            err('%s at %d,%d,%d hangs off the island edge [%s]' % (bb, x, y, z, mw.owner_of((x, y, z))))
    # ---- 9. no slit under ground-level blocks (the Trading Post's corner sat on air over the softened island edge)
    for top, gap, ground in mw.slits():
        err('%s at %s has an air gap under it %s (ground %s) [%s]' % (b(g(*top)), top, gap, b(g(*ground)), mw.owner_of(top)))
    # ---- report
    for e in errors[:80]:
        print('FAIL', e)
    for g in mw.SETTLED:
        print('settled: group at %s (%d blocks): %s [%s]' % (g[0], g[1], 'removed' if g[2] == 'removed' else 'lowered by %d' % g[2], mw.owner_of(g[0])))
    rep = mw.economy_counts()
    print('scoopable:', rep)
    print('checked %d blocks, %d water, %d lit cells: %s' % (len(Wd), len(water), len(light),
                                                               'OK' if not errors else '%d problems' % len(errors)))
    return 0 if not errors else 1


if __name__ == '__main__':
    sys.exit(main())
