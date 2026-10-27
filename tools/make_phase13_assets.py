#!/usr/bin/env python3
"""Phase 13 assets: Belt Splitter (frame, 3-way / 2-way tops, models, blockstate), Large Crucible (the crucible models scaled up,
gold-trimmed textures, auto-pour lamp), Hot Forge (white-hot recolours of the forge) and the Tier 2 Casting Mold (gold rim, eject
arrow). Textures, block/item models and blockstates. Needs Pillow and the Phase 9/12 assets already in the resources.

    python tools/make_phase13_assets.py
"""
import copy
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw
import make_phase12_assets as p12
from make_phase12_assets import TB, MB, MI, BS, NS, save, dump, ys, apply, belt_frame, belt_textures, belt_cube

METALS = ['folkiron', 'goldchro', 'orchremia', 'silvane', 'other']
SPLITTER_TRIM = [(60, 30, 90), (100, 60, 150), (150, 100, 200), (190, 150, 235), (225, 200, 250)]
ARROW = (215, 170, 255, 255)
CLOSED = (70, 40, 100, 255)
GOLD = [(120, 84, 20, 255), (196, 150, 40, 255), (240, 204, 92, 255)]


def load(*path):
    return json.load(open(os.path.join(*path)))


# ------------------------------------------------------------------------------------------------
# Belt Splitter
# ------------------------------------------------------------------------------------------------

def arrow(d, tip, direction, length=5):
    x, y = tip
    dx, dy = direction
    d.line((x, y, x - dx * length, y - dy * length), fill=ARROW)
    # head
    px, py = -dy, dx
    d.line((x, y, x - dx * 2 + px * 2, y - dy * 2 + py * 2), fill=ARROW)
    d.line((x, y, x - dx * 2 - px * 2, y - dy * 2 - py * 2), fill=ARROW)


def splitter():
    belt_frame('belt_splitter', SPLITTER_TRIM)
    top = Image.open(os.path.join(TB, 'clump_belt_top.png')).convert('RGBA').crop((0, 0, 16, 16))
    tx = belt_textures('belt_splitter')
    for two_way in (False, True):
        img = top.copy()
        d = ImageDraw.Draw(img)
        d.rectangle((5, 5, 10, 10), outline=SPLITTER_TRIM[2] + (255,), fill=SPLITTER_TRIM[0] + (255,))
        d.point((7, 7), fill=ARROW)
        d.point((8, 8), fill=ARROW)
        arrow(d, (1, 8), (-1, 0), 3)     # left (west when facing north)
        arrow(d, (14, 8), (1, 0), 3)     # right
        if two_way:
            d.line((5, 3, 10, 3), fill=CLOSED)   # front closed
            d.line((5, 2, 10, 2), fill=CLOSED)
            d.line((5, 4, 10, 4), fill=CLOSED)
        else:
            arrow(d, (8, 1), (0, -1), 3)         # front
        name = 'belt_splitter_top_%s' % ('2' if two_way else '3')
        save(img, TB, name + '.png')
        dump({'parent': 'minecraft:block/block', 'textures': {**tx, 'top': '%s:block/%s' % (NS, name)},
              'elements': [belt_cube('#top')]}, MB, 'belt_splitter_%s.json' % ('2way' if two_way else '3way'))
    dump({'parent': '%s:block/belt_splitter_3way' % NS}, MI, 'belt_splitter.json')
    parts = []
    for f, y in ys():
        for two_way in (False, True):
            parts.append({'when': {'facing': f, 'two_way': 'true' if two_way else 'false'},
                          'apply': apply('%s:block/belt_splitter_%s' % (NS, '2way' if two_way else '3way'), y)})
        parts.append({'when': {'facing': f, 'spout': 'true'}, 'apply': apply('%s:block/clump_belt_spout' % NS, y)})
    dump({'multipart': parts}, BS, 'belt_splitter.json')


# ------------------------------------------------------------------------------------------------
# Large Crucible
# ------------------------------------------------------------------------------------------------

SX = 14.0 / 12.0   # footprint 2..14 -> 1..15
SY = 1.2           # height 9 -> 10.8


def scale_model(model):
    def sc(v):
        return [round((v[0] - 8) * SX + 8, 3), round(v[1] * SY, 3), round((v[2] - 8) * SX + 8, 3)]
    m = copy.deepcopy(model)
    for e in m['elements']:
        e['from'], e['to'] = sc(e['from']), sc(e['to'])
        if 'rotation' in e:
            e['rotation']['origin'] = sc(e['rotation']['origin'])
    return m


def retex(model, mapping):
    m = copy.deepcopy(model)
    for k, v in m['textures'].items():
        for a, b in mapping.items():
            v = v.replace(a, b)
        m['textures'][k] = v
    return m


def trim_rows(img, rows, colors):
    for y, c in rows:
        for x in range(16):
            img.putpixel((x, y), colors[c])
    return img


def large_crucible():
    side = Image.open(os.path.join(TB, 'crucible_side.png')).convert('RGBA')
    side = trim_rows(side, [(7, 2), (8, 1), (13, 1), (14, 2), (15, 0)], GOLD)
    for x in (3, 8, 12):   # rivets
        side.putpixel((x, 10), GOLD[2])
    save(side, TB, 'large_crucible_side.png')
    inner = Image.open(os.path.join(TB, 'crucible_inner.png')).convert('RGBA')
    save(inner.point(lambda v: min(255, int(v * 1.05))), TB, 'large_crucible_inner.png')
    names = ['crucible', 'crucible_tipped'] + ['crucible_chunks_%s' % m for m in METALS] + \
            ['crucible_molten_%s_%d' % (m, i) for m in METALS for i in range(1, 5)]
    base = {n: load(MB, n.replace('crucible_molten_%s' % 'x', 'x') + '.json') for n in ['crucible', 'crucible_tipped']}
    for n in names:
        if n in base:
            model = base[n]
        elif n.startswith('crucible_chunks_'):
            model = retex(load(MB, 'crucible_chunks_folkiron.json'), {'folkiron': n.split('_')[-1]})
        else:
            metal, i = n.split('_')[2], n.split('_')[3]
            model = retex(load(MB, 'crucible_molten_folkiron_%s.json' % i), {'folkiron': metal})
        model = retex(scale_model(model), {'crucible_side': 'large_crucible_side', 'crucible_inner': 'large_crucible_inner'})
        dump(model, MB, n.replace('crucible', 'large_crucible', 1) + '.json')
    # auto-pour lamp: a small glowing stud on top of the spout side (north), shown when auto=true
    lamp = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ImageDraw.Draw(lamp).rectangle((2, 2, 13, 13), fill=(90, 230, 120, 255), outline=(40, 140, 70, 255))
    save(lamp, TB, 'large_crucible_lamp.png')
    face = lambda: {'texture': '#l', 'uv': [0, 0, 16, 16], 'neoforge_data': {'block_light': 15, 'sky_light': 15}}
    dump({'parent': 'minecraft:block/block', 'textures': {'particle': '%s:block/large_crucible_lamp' % NS, 'l': '%s:block/large_crucible_lamp' % NS},
          'elements': [{'from': [6.5, 10.8, -0.6], 'to': [9.5, 12.0, 0.4], 'faces': {k: face() for k in ('north', 'up', 'east', 'west')}}]},
         MB, 'large_crucible_lamp.json')
    # blockstate: the crucible's with the new model names, plus the lamp
    bs = json.dumps(load(BS, 'crucible.json')).replace('holeydigsite:block/crucible', 'holeydigsite:block/large_crucible')
    bs = json.loads(bs)
    for f, y in ys():
        bs['multipart'].append({'when': {'facing': f, 'auto': 'true', 'tipping': 'false'}, 'apply': apply('%s:block/large_crucible_lamp' % NS, y)})
    dump(bs, BS, 'large_crucible.json')
    dump({'parent': '%s:block/large_crucible' % NS}, MI, 'large_crucible.json')


# ------------------------------------------------------------------------------------------------
# Hot Forge
# ------------------------------------------------------------------------------------------------

def heat(img, lit):
    img = img.convert('RGBA')
    out = Image.new('RGBA', img.size)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            if lit:
                r, g, b = min(255, int(r * 1.15 + 45)), min(255, int(g * 1.1 + 40)), min(255, int(b * 0.8 + 25))
            else:
                r, g, b = min(255, int(r * 1.12 + 22)), int(g * 0.85), int(b * 0.7)
            out.putpixel((x, y), (r, g, b, a))
    return out


def hot_forge():
    for n in ('forge_back', 'forge_bottom', 'forge_front', 'forge_front_lit', 'forge_side', 'forge_top', 'forge_top_lit'):
        img = Image.open(os.path.join(TB, n + '.png'))
        lit = n.endswith('_lit')
        h = heat(img, lit)
        if n in ('forge_side', 'forge_front', 'forge_front_lit', 'forge_back'):   # brass bands top and bottom
            w, hh = h.size
            for top in range(0, hh, w):   # every frame of an animated strip gets its own bands
                for x in range(w):
                    for y, c in ((0, 2), (1, 1), (w - 2, 1), (w - 1, 0)):
                        h.putpixel((x, top + y), GOLD[c])
        save(h, TB, 'hot_' + n + '.png')
    meta = os.path.join(TB, 'forge_front_lit.png.mcmeta')
    open(os.path.join(TB, 'hot_forge_front_lit.png.mcmeta'), 'w').write(open(meta).read())
    for src, dst in (('forge', 'hot_forge'), ('forge_lit', 'hot_forge_lit')):
        m = json.dumps(load(MB, src + '.json')).replace('block/forge_', 'block/hot_forge_')
        dump(json.loads(m), MB, dst + '.json')
    bs = json.dumps(load(BS, 'forge.json')).replace('block/forge', 'block/hot_forge')
    dump(json.loads(bs), BS, 'hot_forge.json')
    dump({'parent': '%s:block/hot_forge' % NS}, MI, 'hot_forge.json')


# ------------------------------------------------------------------------------------------------
# Tier 2 Casting Mold
# ------------------------------------------------------------------------------------------------

def casting_mold():
    m = Image.open(os.path.join(TB, 'mold.png')).convert('RGBA')
    out = Image.new('RGBA', m.size)
    for y in range(m.height):
        for x in range(m.width):
            r, g, b, a = m.getpixel((x, y))
            out.putpixel((x, y), (min(255, int(r * 1.1 + 12)), min(255, int(g * 1.03 + 6)), int(b * 0.85), a))
    trim_rows(out, [(12, 2), (14, 1)], GOLD)
    save(out, TB, 'casting_mold.png')
    cav = Image.open(os.path.join(TB, 'mold_cavity.png')).convert('RGBA')
    d = ImageDraw.Draw(cav)
    a = (240, 204, 92, 255)
    d.line((8, 2, 8, 6), fill=a)           # eject arrow, pointing north = the front
    d.line((8, 2, 6, 4), fill=a)
    d.line((8, 2, 10, 4), fill=a)
    save(cav, TB, 'casting_mold_cavity.png')
    model = retex(load(MB, 'mold.json'), {'block/mold_cavity': 'block/casting_mold_cavity', 'block/mold"': 'block/casting_mold"'})
    model['textures'] = {'particle': '%s:block/casting_mold' % NS, 'm': '%s:block/casting_mold' % NS, 'cav': '%s:block/casting_mold_cavity' % NS}
    dump(model, MB, 'casting_mold_t2.json')
    dump({'parent': '%s:block/casting_mold_t2' % NS}, MI, 'casting_mold_t2.json')
    bs = load(BS, 'mold.json')
    parts = [dict(p) for p in bs['multipart'] if 'when' in p]          # metal overlays: same models, no rotation needed
    base = [{'when': {'facing': f}, 'apply': apply('%s:block/casting_mold_t2' % NS, y)} for f, y in ys()]
    dump({'multipart': base + parts}, BS, 'casting_mold_t2.json')


if __name__ == '__main__':
    splitter()
    large_crucible()
    hot_forge()
    casting_mold()
    print('Phase 13 assets written')
