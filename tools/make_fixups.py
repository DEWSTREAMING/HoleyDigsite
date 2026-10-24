#!/usr/bin/env python3
"""Phase 16.1: world fix-up data for EXISTING worlds (python tools/make_fixups.py).

Parts of the starting world are never rebuilt by the mod: the island template outside the site zones (home island trees, boulders)
and OWNED plots. This script compares the world as each shipped generator made it (git revisions OLD_REVS of make_world.py) with the
current generator and writes the differences there as groups of block changes:

  src/main/resources/data/holeydigsite/world_fixups/<NAME>.json
    {"origin": {"site": "holeydigsite:home", "offset": [18, 0, 3]},     # island origin = the home site's anchor - offset
     "groups": [{"expect": [[x, y, z, "minecraft:moss_block"], ...],      # unchanged neighbours that must still be there
                 "remove": [[x, y, z, "minecraft:oak_log"], ...],         # template coordinates + the block expected there
                 "place":  [[x, y, z, "minecraft:oak_log[axis=y]"], ...]}]}

The mod applies a group only if every expected non-leaf block is still there and every non-leaf target is free
(site/WorldFixups.java, run once by the WorldVersion step 2), so anything a player changed is left alone.
Kiosk zones and the home store zone are skipped: they rebuild themselves from the new structures.
"""
import importlib.util, json, os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
OLD_REVS = {'1.14.0': 'eef8043', '1.14.1': '8bea403', '1.14.2': 'c5a6b52'}   # world generators already shipped (1.14.3 = 1.14.2's)
# Each release that changes the never-rebuilt parts gets a NEW file (+ a WorldVersion step and an entry in WorldFixups.ALL); shipped files stay as
# they are (worlds that already ran them are stamped). floating_1_14_1.json = 1.14.2's file; slits_1_14_4.json = this one.
NAME = 'slits_1_14_4'
OUT = os.path.join(MOD, 'src', 'main', 'resources', 'data', 'holeydigsite', 'world_fixups', NAME + '.json')
sys.path.insert(0, HERE)
import make_world as new


def load_old(rev):
    src = subprocess.check_output(['git', 'show', rev + ':tools/make_world.py'], cwd=MOD)
    path = os.path.join(tempfile.mkdtemp(), 'make_world_old.py')
    with open(path, 'wb') as f:
        f.write(src)
    spec = importlib.util.spec_from_file_location('make_world_old', path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


NEAR = [(x, y, z) for x in range(-3, 4) for y in range(-3, 4) for z in range(-3, 4) if (x, y, z) != (0, 0, 0)]


def state(st):
    """Generator state 'ns:block{k:v,...}' -> block state string 'ns:block[k=v,...]'."""
    if '{' not in st:
        return st
    b, props = st[:-1].split('{', 1)
    return '%s[%s]' % (b, ','.join(p.replace(':', '=', 1) for p in props.split(',') if p))


def diff_groups(old, label):
    """Groups of changes from `old`'s world to the current generator's (see the module doc)."""
    skip = [new.kiosk_box(p) for p in new.PLOTS] + [new.site_box('home')]
    changed = set()
    for k in set(old.W) | set(new.W):
        if old.W.get(k) != new.W.get(k) and not any(new.inside(b, *k) for b in skip):
            a, b = old.W.get(k), new.W.get(k)
            if a and b and new.base(a[0]) == new.base(b[0]) and a[1] == b[1]:
                continue        # same block, other properties only (fence connections): leave as it is
            changed.add(k)
    groups, seen, nbt_skipped = [], set(), 0
    for k in sorted(changed):
        if k in seen:
            continue
        comp, todo = set(), [k]
        while todo:
            c = todo.pop()
            if c in comp:
                continue
            comp.add(c)
            for d in NEAR:      # one object's changes (a tree moved down) are not all touching: join changes up to 3 apart
                n = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
                if n in changed and n not in comp:
                    todo.append(n)
        seen |= comp
        if any((old.W.get(c) and old.W[c][1]) or (new.W.get(c) and new.W[c][1]) for c in comp):
            nbt_skipped += 1    # signs etc. carry NBT: not handled by the fix-up
            continue
        rem = [[*c, new.base(old.W[c][0])] for c in sorted(comp) if c in old.W and new.base(old.W[c][0]) != 'minecraft:light']
        put = [[*c, state(new.W[c][0])] for c in sorted(comp) if c in new.W and new.base(new.W[c][0]) != 'minecraft:light']
        # anchors: unchanged old blocks next to the group (not leaves / plants). They must still be there, so a group - above all one that
        # only adds blocks - is applied only where the world really has the old layout
        anchors = []
        for c in sorted(comp):
            for d in new.DIRS:
                n = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
                v = old.W.get(n)
                if (n not in comp and v and new.structural(v[0]) and 'leaves' not in v[0] and old.W.get(n) == new.W.get(n)
                        and [*n, new.base(v[0])] not in anchors):
                    anchors.append([*n, new.base(v[0])])
        if rem or put:
            groups.append({'where': (new.owner_of(k) or 'island') + ' (' + label + ')', 'expect': anchors[:12], 'remove': rem, 'place': put})
    return groups, nbt_skipped


def main():
    new.build()
    groups, nbt_skipped = [], 0
    for label, rev in OLD_REVS.items():
        old = load_old(rev)
        old.build()
        new.build()
        g, n = diff_groups(old, label)
        groups += g
        nbt_skipped += n
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump({'origin': {'site': 'holeydigsite:home', 'offset': list(new.SITES['home'][1])}, 'groups': groups}, f, indent=1)
        f.write('\n')
    print('%d groups (%d removes, %d places) -> %s; %d groups with NBT skipped' % (
        len(groups), sum(len(g['remove']) for g in groups), sum(len(g['place']) for g in groups), OUT, nbt_skipped))
    for g in groups:
        print('  %-13s %3d remove %3d place  first %s' % (g['where'], len(g['remove']), len(g['place']), (g['remove'] or g['place'])[0][:3]))


if __name__ == '__main__':
    main()
