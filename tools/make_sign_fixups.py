#!/usr/bin/env python3
"""Phase 20.1: sign-text fix-ups for EXISTING worlds (python tools/make_sign_fixups.py).

Signs carry NBT, so make_fixups.py (blocks only) skips them. This compares the signs of the last shipped generator (OLD_REV) with the
current make_world.py and writes data/holeydigsite/world_fixups/<NAME>.json:
  {"origin": {...as make_fixups...}, "groups": [],
   "signs": [{"where": "...", "pos": [x, y, z], "block": "minecraft:oak_sign", "old": [4 lines], "new": [4 lines]}]}
The mod (site/WorldFixups) replaces a sign's front text only if the block is still that sign AND all 4 lines still equal "old"
(player-edited or removed signs are left alone). Signs in the home store zone and the kiosks rebuild themselves and are skipped.
"""
import json, os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import make_fixups as mf
new = mf.new

OLD_REV = '40a4599'          # the generator shipped as 1.18.0
NAME = 'signs_1_18_1'
OUT = os.path.join(MOD, 'src', 'main', 'resources', 'data', 'holeydigsite', 'world_fixups', NAME + '.json')


def lines_of(nbt):
    """Front text lines of a sign NBT string made by sign_nbt()."""
    m = re.search(r"front_text:{messages:\[(.*?)\],color", nbt)
    q = chr(39); bs = chr(92)
    return [json.loads(x.replace(bs + q, q))["text"] for x in re.findall(q + "((?:[^" + q + bs + bs + "]|" + bs + bs + ".)*)" + q, m.group(1))]


def main():
    old = mf.load_old(OLD_REV)
    old.build()
    new.build()
    skip = [new.kiosk_box(p) for p in new.PLOTS] + [new.site_box('home')]
    signs = []
    for k in sorted(set(old.W) & set(new.W)):
        o, n = old.W[k], new.W[k]
        if 'sign' not in o[0] or not o[1] or not n[1] or any(new.inside(b, *k) for b in skip):
            continue
        lo, ln = lines_of(o[1]), lines_of(n[1])
        if lo != ln:
            assert new.base(o[0]) == new.base(n[0]), k
            signs.append({'where': new.owner_of(k) or 'island', 'pos': list(k), 'block': new.base(o[0]), 'old': lo, 'new': ln})
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump({'origin': {'site': 'holeydigsite:home', 'offset': list(new.SITES['home'][1])}, 'groups': [], 'signs': signs}, f, indent=1, ensure_ascii=False)
        f.write('\n')
    for s in signs:
        print(s['where'], s['pos'], s['old'], '->', s['new'])
    print(len(signs), 'signs ->', OUT)


if __name__ == '__main__':
    main()
