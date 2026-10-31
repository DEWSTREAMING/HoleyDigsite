#!/usr/bin/env python3
"""Phase 28b assets: Pindan, the Event Sell Pad, Outback Crate, the event decor, the Welcome Stranger stand, the Event Board and
Leaderboard, and the three event plot deeds. Textures are drawn or tinted from existing ones; models and blockstates are written
next to them. Run: python tools/make_phase28b_assets.py (needs Pillow). Safe to run again."""
import json, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(os.path.dirname(HERE), 'src', 'main', 'resources', 'assets', 'holeydigsite')
TB, TI = os.path.join(A, 'textures', 'block'), os.path.join(A, 'textures', 'item')
MB, MI, BS = os.path.join(A, 'models', 'block'), os.path.join(A, 'models', 'item'), os.path.join(A, 'blockstates')


def wj(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(json.dumps(obj, indent=2) + '\n')


def tint(src, dst, mul, add=(0, 0, 0)):
    im = Image.open(src).convert('RGBA')
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            px[x, y] = (min(255, int(r * mul[0] + add[0])), min(255, int(g * mul[1] + add[1])), min(255, int(b * mul[2] + add[2])), a)
    im.save(dst)


def noise_tex(dst, base, spread, seed, specks=()):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            f = 1 + rnd.uniform(-spread, spread)
            px[x, y] = tuple(max(0, min(255, int(c * f))) for c in base) + (255,)
    for col, n in specks:
        for _ in range(n):
            px[rnd.randrange(16), rnd.randrange(16)] = col + (255,)
    im.save(dst)


def facing_states(name, model):
    wj(os.path.join(BS, name + '.json'), {'variants': {
        'facing=north': {'model': model}, 'facing=east': {'model': model, 'y': 90},
        'facing=south': {'model': model, 'y': 180}, 'facing=west': {'model': model, 'y': 270}}})


def box(f, t, tex, faces=('north', 'south', 'east', 'west', 'up', 'down')):
    return {'from': f, 'to': t, 'faces': {d: {'texture': tex} for d in faces}}


# ---- Pindan: red sandy soil
noise_tex(os.path.join(TB, 'pindan.png'), (176, 74, 40), 0.12, 2801, (((140, 52, 28), 18), ((205, 112, 64), 10)))
wj(os.path.join(MB, 'pindan.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'holeydigsite:block/pindan'}})
wj(os.path.join(BS, 'pindan.json'), {'variants': {'': [{'model': 'holeydigsite:block/pindan'}, {'model': 'holeydigsite:block/pindan', 'y': 90},
                                                       {'model': 'holeydigsite:block/pindan', 'y': 180}, {'model': 'holeydigsite:block/pindan', 'y': 270}]}})
wj(os.path.join(MI, 'pindan.json'), {'parent': 'holeydigsite:block/pindan'})

# ---- Event Sell Pad: the Sell Pad in red-brown with an opal-blue top
for part, mul, add in (('top', (0.55, 0.8, 1.15), (10, 20, 40)), ('side', (1.1, 0.7, 0.55), (0, 0, 0)), ('bottom', (1.1, 0.7, 0.55), (0, 0, 0))):
    tint(os.path.join(TB, 'sell_pad_%s.png' % part), os.path.join(TB, 'event_sell_pad_%s.png' % part), mul, add)
sp = json.load(open(os.path.join(MB, 'sell_pad.json'), encoding='utf-8'))
sp['textures'] = {k: v.replace('sell_pad', 'event_sell_pad') for k, v in sp['textures'].items()}
wj(os.path.join(MB, 'event_sell_pad.json'), sp)
wj(os.path.join(BS, 'event_sell_pad.json'), {'variants': {'locked=false': {'model': 'holeydigsite:block/event_sell_pad'},
                                                          'locked=true': {'model': 'holeydigsite:block/event_sell_pad'}}})
wj(os.path.join(MI, 'event_sell_pad.json'), {'parent': 'holeydigsite:block/event_sell_pad'})

# ---- Outback Crate: the wooden crate, sun-bleached red
for part in ('side', 'front', 'top', 'bottom'):
    tint(os.path.join(TB, 'wooden_crate_%s.png' % part), os.path.join(TB, 'outback_crate_%s.png' % part), (1.25, 0.72, 0.55), (10, 0, 0))
for suffix, parent in (('', 'crate_closed'), ('_open', 'crate_open')):
    wj(os.path.join(MB, 'outback_crate%s.json' % suffix), {'parent': 'holeydigsite:block/' + parent, 'textures': {
        'side': 'holeydigsite:block/outback_crate_side', 'front': 'holeydigsite:block/outback_crate_front',
        'top': 'holeydigsite:block/outback_crate_top', 'bottom': 'holeydigsite:block/outback_crate_bottom',
        'inside': 'holeydigsite:block/crate_inside', 'particle': 'holeydigsite:block/outback_crate_side'}})
v = {}
for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
    for o in ('false', 'true'):
        e = {'model': 'holeydigsite:block/outback_crate' + ('_open' if o == 'true' else '')}
        if y:
            e['y'] = y
        v['facing=%s,open=%s' % (f, o)] = e
wj(os.path.join(BS, 'outback_crate.json'), {'variants': v})
wj(os.path.join(MI, 'outback_crate.json'), {'parent': 'holeydigsite:block/outback_crate'})

# ---- decor textures
rnd = random.Random(7)
im = Image.new('RGBA', (16, 16))
px = im.load()
for y in range(16):
    for x in range(16):
        ridge = 1.0 + 0.22 * (1 if x % 4 in (0, 1) else -1) * (0.6 if x % 4 in (1, 2) else 1)
        c = [int(150 * ridge), int(155 * ridge), int(160 * ridge)]
        if rnd.random() < 0.08 + 0.05 * (y > 11):
            c = [150, 85, 45]
        px[x, y] = tuple(max(0, min(255, k)) for k in c) + (255,)
im.save(os.path.join(TB, 'corrugated_iron.png'))
import math
im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))        # the fan wheel: a ring, 12 narrow blades and a hub, seen from the front
px = im.load()
for y in range(16):
    for x in range(16):
        dx, dy = x - 7.5, y - 7.5
        r = math.hypot(dx, dy)
        a = (math.degrees(math.atan2(dy, dx)) + 360) % 30
        if 6.6 <= r <= 7.6:
            px[x, y] = (150, 152, 156, 255)                          # rim
        elif r < 1.6:
            px[x, y] = (95, 96, 100, 255)                            # hub
        elif r < 6.6 and (a < 9 or a > 27):
            px[x, y] = (205, 207, 212, 255) if a < 5 or a > 29 else (175, 177, 182, 255)   # blades
im.save(os.path.join(TB, 'little_windmill_fan.png'))
noise_tex(os.path.join(TB, 'windpump_steel.png'), (176, 180, 184), 0.07, 23, (((150, 92, 52), 9), ((200, 204, 208), 12)))
# ---- Australian flag: 22 x 11 texels on a 32 x 32 sheet (the cloth is wider than a block, so it keeps 16 texels a block)
NAVY, WHITE, RED = (1, 33, 105, 255), (255, 255, 255, 255), (228, 0, 43, 255)
im = Image.new('RGBA', (32, 32), NAVY)
px = im.load()
UNION = ['RWBBWRWBBWR',                         # the Union Jack canton, 11 x 5
         'BWWBWRWBWWB',
         'RRRRRRRRRRR',
         'BWWBWRWBWWB',
         'RWBBWRWBBWR']
for y, row in enumerate(UNION):
    for x, ch in enumerate(row):
        px[x, y] = {'R': RED, 'W': WHITE, 'B': NAVY}[ch]


def star(cx, cy, big=True, col=WHITE):
    pts = [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]
    if big:
        pts += [(1, 1), (-1, 1), (1, -1), (-1, -1), (0, -2), (0, 2), (2, 0), (-2, 0)]
    for dx, dy in pts:
        if 0 <= cx + dx < 22 and 0 <= cy + dy < 11:
            px[cx + dx, cy + dy] = col


star(5, 8)                                       # Commonwealth Star under the canton
for (cx, cy) in ((16, 1), (16, 9), (13, 4), (19, 4)):
    star(cx, cy, big=False)                      # the Southern Cross: Gamma, Alpha, Beta, Delta
px[18, 6] = WHITE                                # Epsilon, the small one
im.save(os.path.join(TB, 'australian_flag.png'))

# ---- Ore Ute: dusty white paint, cab with windows, wheels
noise_tex(os.path.join(TB, 'ute_paint.png'), (214, 206, 188), 0.06, 11, (((176, 120, 84), 14), ((190, 182, 165), 10)))
im = Image.open(os.path.join(TB, 'ute_paint.png')).convert('RGBA')
px = im.load()
for x in range(16):
    for y in range(3):
        px[x, y] = (120, 170, 200, 255) if 1 <= x <= 14 else px[x, y]      # window band (rows 0-2)
    px[x, 3] = (170, 160, 145, 255)                                         # door line under the windows
px[2, 6] = px[13, 6] = (255, 230, 140, 255)                                 # headlights (bonnet front)
im.save(os.path.join(TB, 'ute_cab.png'))

# ---- Welcome Stranger stand: brass plaque
im = Image.new('RGBA', (16, 16), (181, 140, 60, 255))
px = im.load()
for x in range(16):
    for y in range(16):
        if x in (0, 15) or y in (0, 15):
            px[x, y] = (120, 88, 32, 255)
        elif y in (5, 8, 11) and 3 <= x <= 12:
            px[x, y] = (110, 80, 28, 255)                                   # engraved lines
        elif (x + y) % 7 == 0:
            px[x, y] = (214, 176, 90, 255)
im.save(os.path.join(TB, 'brass_plaque.png'))

# ---- the Welcome Stranger: a big lumpy gold nugget (the 3D lump shapes of the clumps and chunks, bigger)
import sys
sys.path.insert(0, HERE)
import make_lump_models as lump
rnd = random.Random(1869)
im = Image.new('RGBA', (16, 16))
px = im.load()
for y in range(16):
    for x in range(16):
        f = 1 + rnd.uniform(-0.1, 0.1) - (0.25 if y >= 12 else 0)
        c = (232, 180, 44)
        if rnd.random() < 0.1:
            c = (150, 104, 22)                                                # pits
        elif rnd.random() < 0.08:
            c = (255, 232, 130)                                               # shine
        px[x, y] = tuple(max(0, min(255, int(k * f))) for k in c) + (255,)
os.makedirs(os.path.join(TI, 'lump'), exist_ok=True)
im.save(os.path.join(TI, 'lump', 'welcome_stranger.png'))
NUGGET = [(1, 2.5, 14, 12.5, 0, 4), (2.5, 3.5, 12.5, 11.5, 4, 7), (4, 5, 10.5, 10, 7, 9), (11.5, 8, 15.5, 14, 0, 3),
          (0.5, 7, 4, 13.5, 0, 2.5), (5.5, 0.5, 10, 3.5, 0, 2.5)]
wj(os.path.join(MI, 'welcome_stranger.json'), {'textures': {'particle': '#t', 't': 'holeydigsite:item/lump/welcome_stranger'},
                                               'elements': lump.elements(NUGGET), 'display': lump.display(NUGGET, lump.GUI_SCALE)})

DECOR = {
    'corrugated_iron_sheet': [box([0, 0, 7], [16, 16, 9], '#0')],
    # an outback windpump: open lattice tower stepping in, round fan wheel of 12 blades, tail vane behind
    'little_windmill': [box([3, 0, 3], [4, 6, 4], '#1'),
        box([12, 0, 3], [13, 6, 4], '#1'),
        box([3, 0, 12], [4, 6, 13], '#1'),
        box([12, 0, 12], [13, 6, 13], '#1'),
        box([4.5, 6, 4.5], [5.5, 12, 5.5], '#1'),
        box([10.5, 6, 4.5], [11.5, 12, 5.5], '#1'),
        box([4.5, 6, 10.5], [5.5, 12, 11.5], '#1'),
        box([10.5, 6, 10.5], [11.5, 12, 11.5], '#1'),
        box([6, 12, 6], [7, 18, 7], '#1'),
        box([9, 12, 6], [10, 18, 7], '#1'),
        box([6, 12, 9], [7, 18, 10], '#1'),
        box([9, 12, 9], [10, 18, 10], '#1'),
        box([3, 5.5, 3], [13, 6, 3.5], '#1'),
        box([3, 5.5, 12.5], [13, 6, 13], '#1'),
        box([3, 5.5, 3], [3.5, 6, 13], '#1'),
        box([12.5, 5.5, 3], [13, 6, 13], '#1'),
        box([4.5, 11.5, 4.5], [11.5, 12, 5.0], '#1'),
        box([4.5, 11.5, 11.0], [11.5, 12, 11.5], '#1'),
        box([4.5, 11.5, 4.5], [5.0, 12, 11.5], '#1'),
        box([11.0, 11.5, 4.5], [11.5, 12, 11.5], '#1'),
        dict(box([3.5, 2.75, 3.25], [12.5, 3.25, 3.75], '#1'), rotation={'angle': 45, 'axis': 'z', 'origin': [8, 3, 3.5]}),
        dict(box([3.5, 2.75, 12.25], [12.5, 3.25, 12.75], '#1'), rotation={'angle': 45, 'axis': 'z', 'origin': [8, 3, 12.5]}),
        dict(box([3.25, 2.75, 3.5], [3.75, 3.25, 12.5], '#1'), rotation={'angle': 45, 'axis': 'x', 'origin': [3.5, 3, 8]}),
        dict(box([12.25, 2.75, 3.5], [12.75, 3.25, 12.5], '#1'), rotation={'angle': 45, 'axis': 'x', 'origin': [12.5, 3, 8]}),
        dict(box([3.5, 2.75, 3.25], [12.5, 3.25, 3.75], '#1'), rotation={'angle': -45, 'axis': 'z', 'origin': [8, 3, 3.5]}),
        dict(box([3.5, 2.75, 12.25], [12.5, 3.25, 12.75], '#1'), rotation={'angle': -45, 'axis': 'z', 'origin': [8, 3, 12.5]}),
        dict(box([3.25, 2.75, 3.5], [3.75, 3.25, 12.5], '#1'), rotation={'angle': -45, 'axis': 'x', 'origin': [3.5, 3, 8]}),
        dict(box([12.25, 2.75, 3.5], [12.75, 3.25, 12.5], '#1'), rotation={'angle': -45, 'axis': 'x', 'origin': [12.5, 3, 8]}),
        box([6, 18, 6], [10, 19, 10], '#1'),
        box([7, 19, 7], [9, 23, 9], '#1'),
        box([7, 22.5, 4], [9, 25.5, 9], '#1'),
        {'from': [1.5, 17, 3.75], 'to': [14.5, 30, 3.75], 'faces': {'north': {'texture': '#0', 'uv': [0, 0, 16, 16]}, 'south': {'texture': '#0', 'uv': [16, 0, 0, 16]}}},
        box([7.5, 23.5, 9], [8.5, 24.5, 17], '#1'),
        box([7.75, 20.5, 15], [8.25, 28.5, 23], '#2')],
    # front (north) to back: bull bar, bonnet, cab, then the tray with its load
    'ute_ore_cart': [box([3, 2, 0], [13, 6.5, 0.5], '#4'), box([3, 2.5, 0.5], [13, 6.5, 4], '#0'),
                     {'from': [3, 2.5, 4], 'to': [13, 10, 8], 'faces': {
                         'north': {'texture': '#1', 'uv': [3, 0, 13, 7.5]}, 'south': {'texture': '#1', 'uv': [3, 0, 13, 7.5]},
                         'east': {'texture': '#1', 'uv': [4, 0, 8, 7.5]}, 'west': {'texture': '#1', 'uv': [8, 0, 12, 7.5]},
                         'up': {'texture': '#0', 'uv': [3, 4, 13, 8]}, 'down': {'texture': '#0', 'uv': [3, 4, 13, 8]}}},
                     box([3, 2.5, 8], [13, 3.5, 16], '#0'), box([3, 3.5, 8], [3.5, 5, 16], '#0'), box([12.5, 3.5, 8], [13, 5, 16], '#0'),
                     box([3.5, 3.5, 15.5], [12.5, 5, 16], '#0'), box([4, 3.5, 9], [12, 6, 15], '#2'), box([5.5, 6, 10], [10.5, 7.5, 14], '#2'),
                     box([2, 0, 1.5], [3, 3, 4.5], '#3'), box([13, 0, 1.5], [14, 3, 4.5], '#3'), box([2, 0, 11], [3, 3, 14], '#3'),
                     box([13, 0, 11], [14, 3, 14], '#3')],
    # the pole on its base, the flag flying out past the block (22 x 11 texels, 2:1)
    'southern_cross_banner': [box([7, 0, 7], [9, 16, 9], '#1'), box([4, 0, 4], [12, 1, 12], '#1'),
                              {'from': [9, 5, 7.75], 'to': [31, 16, 8.25], 'faces': {
                                  'north': {'texture': '#0', 'uv': [0, 0, 11, 5.5]}, 'south': {'texture': '#0', 'uv': [11, 0, 0, 5.5]},
                                  'east': {'texture': '#0', 'uv': [10, 5, 10.25, 5.5]}, 'up': {'texture': '#0', 'uv': [10, 5, 10.5, 5.25]},
                                  'down': {'texture': '#0', 'uv': [10, 5, 10.5, 5.25]}}}],
    # a wooden plinth with a brass plaque on the front; the nugget is drawn on top by the block entity renderer
    'welcome_stranger_stand': [box([2, 0, 2], [14, 2, 14], '#0'), box([3, 2, 3], [13, 9, 13], '#1'), box([2.5, 9, 2.5], [13.5, 10, 13.5], '#0'),
                               {'from': [5, 3.5, 2.5], 'to': [11, 7.5, 3], 'faces': {
                                   'north': {'texture': '#2', 'uv': [0, 0, 16, 16]}, 'east': {'texture': '#2', 'uv': [0, 0, 1, 16]},
                                   'west': {'texture': '#2', 'uv': [0, 0, 1, 16]}, 'up': {'texture': '#2', 'uv': [0, 0, 16, 1]},
                                   'down': {'texture': '#2', 'uv': [0, 0, 16, 1]}}}],
}
TEX = {
    'corrugated_iron_sheet': {'0': 'holeydigsite:block/corrugated_iron', 'particle': 'holeydigsite:block/corrugated_iron'},
    'little_windmill': {'0': 'holeydigsite:block/little_windmill_fan', '1': 'holeydigsite:block/windpump_steel',
                        '2': 'minecraft:block/red_concrete', 'particle': 'holeydigsite:block/windpump_steel'},
    'ute_ore_cart': {'0': 'holeydigsite:block/ute_paint', '1': 'holeydigsite:block/ute_cab', '2': 'minecraft:block/raw_gold_block',
                     '3': 'minecraft:block/black_concrete', '4': 'minecraft:block/iron_block', 'particle': 'holeydigsite:block/ute_paint'},
    'southern_cross_banner': {'0': 'holeydigsite:block/australian_flag', '1': 'minecraft:block/stripped_spruce_log',
                              'particle': 'holeydigsite:block/australian_flag'},
    'welcome_stranger_stand': {'0': 'minecraft:block/dark_oak_planks', '1': 'minecraft:block/spruce_planks',
                               '2': 'holeydigsite:block/brass_plaque', 'particle': 'minecraft:block/spruce_planks'},
}
# models taller or wider than a block: smaller in slots and on displays; the fan wheel has see-through gaps
EXTRA = {
    'little_windmill': {'render_type': 'minecraft:cutout', 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, -3.5, 0], 'scale': [0.36, 0.36, 0.36]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, -3, 0], 'scale': [0.4, 0.4, 0.4]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.2, 0.2, 0.2]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 1, 0], 'scale': [0.2, 0.2, 0.2]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, -2, 0], 'scale': [0.25, 0.25, 0.25]}}},
    'southern_cross_banner': {'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [-2, -1, 0], 'scale': [0.42, 0.42, 0.42]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [-3, 0, 0], 'scale': [0.45, 0.45, 0.45]}}},
}
for name, els in DECOR.items():
    model = {'parent': 'minecraft:block/block', 'textures': TEX[name], 'elements': els}
    model.update(EXTRA.get(name, {}))
    wj(os.path.join(MB, name + '.json'), model)
    facing_states(name, 'holeydigsite:block/' + name)
    wj(os.path.join(MI, name + '.json'), {'parent': 'holeydigsite:block/' + name})

# ---- boards: the Contract Board in acacia red (event) and dark oak (world)
for name, mul in (('event_board', (1.2, 0.75, 0.6)), ('leaderboard', (0.7, 0.62, 0.55))):
    tint(os.path.join(TB, 'contract_board.png'), os.path.join(TB, name + '.png'), mul)
    wj(os.path.join(MB, name + '.json'), {'parent': 'holeydigsite:block/contract_board',
                                          'textures': {'0': 'holeydigsite:block/' + name, 'particle': 'holeydigsite:block/' + name}})
    facing_states(name, 'holeydigsite:block/' + name)
    wj(os.path.join(MI, name + '.json'), {'parent': 'holeydigsite:block/' + name})

# ---- deeds: the Deep Dig deed in red ochre, blue-grey and opal blue
for t, mul in ((1, (1.25, 0.75, 0.6)), (2, (0.85, 0.9, 1.0)), (3, (0.6, 0.85, 1.2))):
    tint(os.path.join(TI, 'land_deed_deep_dig.png'), os.path.join(TI, 'land_deed_event_t%d.png' % t), mul)
    wj(os.path.join(MI, 'land_deed_event_t%d.json' % t), {'parent': 'minecraft:item/generated',
                                                         'textures': {'layer0': 'holeydigsite:item/land_deed_event_t%d' % t}})
print('phase 28b assets written')
