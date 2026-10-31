#!/usr/bin/env python3
"""Quick isometric previews of the generated world (no Minecraft needed): python tools/preview_world.py [out dir].

Writes iso_<view>.png for four corners and top.png (height-shaded map). Colours are rough block colours; the real look comes
from the client screenshots (devtests/ClientShots.java)."""
import os, sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_world as mw

COL = [  # substring -> colour (first match wins)
    ('pindan', (176, 74, 40)), ('hardpan', (185, 147, 107)), ('red_dirt_rock', (161, 71, 39)), ('mullock', (178, 178, 173)),
    ('opal_clay', (111, 128, 138)), ('bedrock', (60, 60, 60)), ('red_terracotta', (140, 60, 45)), ('orange_terracotta', (160, 85, 40)),
    ('terracotta', (150, 92, 66)), ('granite', (150, 105, 85)), ('copper', (160, 120, 90)), ('acacia', (170, 90, 50)),
    ('red_sandstone', (185, 100, 40)), ('packed_mud', (140, 105, 80)), ('iron_block', (220, 220, 220)), ('concrete', (160, 160, 160)),
    ('event_board', (250, 240, 200)), ('leaderboard', (250, 240, 200)), ('event_sell_pad', (200, 90, 200)),
    ('water', (52, 98, 214)), ('minecraft:light', None), ('site_marker', None),
    ('abyssal', (40, 20, 60)), ('deep_dirtrock', (90, 50, 30)), ('dirtrock', (120, 85, 55)), ('gravelrock', (95, 100, 110)),
    ('sandrock', (215, 170, 90)), ('grass_block', (95, 160, 60)), ('moss', (90, 130, 50)), ('short_grass', (110, 170, 70)),
    ('podzol', (90, 65, 30)), ('mud', (60, 50, 50)), ('coarse_dirt', (125, 90, 60)), ('rooted', (140, 100, 70)),
    ('dirt_path', (150, 125, 70)), ('dirt', (135, 95, 65)), ('gravel', (140, 135, 130)), ('clay', (160, 165, 180)),
    ('red_sand', (190, 100, 40)), ('sandstone', (220, 205, 150)), ('sand', (235, 220, 165)),
    ('dark_oak', (65, 45, 25)), ('spruce', (115, 85, 50)), ('birch', (215, 205, 160)), ('jungle_log', (110, 85, 40)),
    ('oak', (165, 130, 80)), ('leaves', (60, 125, 40)), ('glass', (190, 220, 235)), ('lantern', (255, 200, 80)),
    ('cobble', (120, 120, 120)), ('stone_bricks', (130, 130, 130)), ('andesite', (135, 135, 135)), ('tuff', (110, 110, 100)),
    ('stone', (125, 125, 125)), ('shop_display', (240, 210, 60)), ('sell_pad', (60, 200, 90)), ('buy_pad', (60, 120, 220)),
    ('money_pan', (230, 190, 40)), ('shop_button', (220, 50, 50)), ('plot_board', (250, 240, 200)), ('wool', None),
    ('rail', (150, 140, 120)), ('scaffold', (200, 180, 100)), ('barrel', (130, 95, 55)), ('hay', (210, 180, 60)),
    ('poppy', (200, 30, 30)), ('dandelion', (240, 220, 40)), ('cornflower', (80, 110, 230)), ('daisy', (240, 240, 230)),
    ('bluet', (220, 230, 240)), ('cane', (130, 190, 90)), ('bush', (140, 100, 50)), ('cocoa', (140, 80, 30)), ('sign', (180, 150, 100)),
    ('chain', (60, 60, 70)), ('ladder', (160, 120, 70)), ('composter', (120, 90, 50)),
]
WOOL = {'light_blue': (110, 170, 220), 'white': (235, 235, 235), 'orange': (235, 130, 40), 'brown': (110, 75, 45),
        'red': (170, 40, 40), 'yellow': (240, 210, 60)}


OFF = [0, 0]      # x, z shift so the event island (north of the template) fits the picture


def colour(st):
    b = mw.base(st)
    if b.endswith('_wool'):
        return WOOL.get(b.split(':')[1][:-5], (200, 200, 200))
    if b == 'minecraft:light':
        return None
    for k, c in COL:
        if k in b:
            return c
    return (160, 80, 160)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def iso(blocks, view, s=7, ymin=0):
    # rotate the world so the chosen corner faces the viewer
    def rot(x, z):
        return {0: (x, z), 1: (mw.TD - 1 - z, x), 2: (mw.TW - 1 - x, mw.TD - 1 - z), 3: (z, mw.TW - 1 - x)}[view]
    items = []
    for (x, y, z), (st, _) in blocks.items():
        if y < ymin:
            continue
        c = colour(st)
        if c is None:
            continue
        rx, rz = rot(x, z)
        items.append((rx + rz, y, rx, rz, c, st))
    items.sort(key=lambda t: (t[0], t[1]))
    span = mw.TW + mw.TD
    w, h = span * s + 20, span * s // 2 + mw.TH * s + 40
    img = Image.new('RGB', (w, h), (30, 32, 40))
    d = ImageDraw.Draw(img)
    ox, oy = mw.TD * s + 10, mw.TH * s + 20
    for _, y, rx, rz, c, st in items:
        sx = ox + (rx - rz) * s
        sy = oy + (rx + rz) * s // 2 - y * s
        small = any(k in st for k in ('flower', 'poppy', 'dandelion', 'grass{', 'short_grass', 'bush', 'lantern', 'sign', 'rail',
                                       'chain', 'cane', 'fence', 'pane', 'ladder', 'cocoa'))
        if 'slab' in st or 'carpet' in st:
            pass
        top = [(sx, sy), (sx + s, sy + s // 2), (sx, sy + s), (sx - s, sy + s // 2)]
        left = [(sx - s, sy + s // 2), (sx, sy + s), (sx, sy + 2 * s), (sx - s, sy + s + s // 2)]
        right = [(sx + s, sy + s // 2), (sx, sy + s), (sx, sy + 2 * s), (sx + s, sy + s + s // 2)]
        if small:
            r = s // 3
            d.ellipse([sx - r, sy + s - r, sx + r, sy + s + r], fill=c)
            continue
        d.polygon(left, fill=shade(c, 0.7))
        d.polygon(right, fill=shade(c, 0.55))
        d.polygon(top, fill=c)
    return img


def top(blocks, s=8):
    img = Image.new('RGB', (mw.TW * s, mw.TD * s), (30, 32, 40))
    d = ImageDraw.Draw(img)
    best = {}
    for (x, y, z), (st, _) in blocks.items():
        c = colour(st)
        if c is None:
            continue
        if (x, z) not in best or y > best[(x, z)][0]:
            best[(x, z)] = (y, c)
    for (x, z), (y, c) in best.items():
        d.rectangle([x * s, z * s, x * s + s - 1, z * s + s - 1], fill=shade(c, 0.6 + 0.04 * (y - mw.G + 5)))
    boxes = [(mw.site_box(sid), (255, 60, 60)) for sid in mw.SITES] + [(mw.kiosk_box(pid), (255, 255, 0)) for pid in mw.PLOTS]
    boxes += [(mw.ev_box(t), (255, 60, 60)) for t in mw.EV_SECTIONS] + [(mw.ev_kiosk_box(), (255, 255, 0))]
    for b, col in boxes:
        b = (b[0] - OFF[0], 0, b[2] - OFF[1], b[3] - OFF[0], 0, b[5] - OFF[1])
        d.rectangle([b[0] * s, b[2] * s, (b[3] + 1) * s - 1, (b[5] + 1) * s - 1], outline=col)
    return img


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(mw.HERE, 'world_snbt', 'preview')
    os.makedirs(out, exist_ok=True)
    mw.build()
    OFF[0] = min(0, min(k[0] for k in mw.W))
    OFF[1] = min(0, min(k[2] for k in mw.W))
    shifted = {(x - OFF[0], y, z - OFF[1]): v for (x, y, z), v in mw.W.items()}
    mw.TW = max(k[0] for k in shifted) + 1
    mw.TD = max(k[2] for k in shifted) + 1
    mw.W.clear()
    mw.W.update(shifted)
    top(mw.W).save(os.path.join(out, 'top.png'))
    for v in range(4):
        iso(mw.W, v).save(os.path.join(out, 'iso_%d.png' % v))
    print('previews in', out)
