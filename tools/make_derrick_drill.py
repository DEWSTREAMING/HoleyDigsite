"""Derrick shape for the mining drill: an open frame with the auger pointing down into the node.

Rewrites the elements of every drill block and item model (all tiers and states) and keeps each file's textures, so tier
looks and skins carry over. The block models leave the auger out: it is its own model (block/mining_drill_auger), turned
by MiningDrillRenderer while the drill works. Also draws the auger bit textures. Sketches of other shapes:
make_drill_designs.py.
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/holeydigsite')
MODELS = os.path.join(ASSETS, 'models')
TEX = os.path.join(ASSETS, 'textures/block')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def box(frm, to, tex, faces=FACES, rotation=None):
    if isinstance(tex, str):
        tex = {f: tex for f in faces}
    el = {'from': list(frm), 'to': list(to), 'faces': {f: {'texture': t} for f, t in tex.items()}}
    if rotation:
        el['rotation'] = rotation
    return el


def housing(frm, to, front='#front', side='#side', top='#side', bottom='#side', back=None):
    return box(frm, to, {'north': front, 'south': back or side, 'east': side, 'west': side, 'up': top, 'down': bottom})


AUGER_TURN = (0, 22.5, 45, -22.5)  # a cross of two blades repeats every 90 degrees, so this reads as one spiral


def auger(top, width):
    els = [box([7, 0, 7], [9, top, 9], '#bit', faces=('north', 'south', 'east', 'west'))]
    y, w, i = top - 1.5, width / 2, 0
    while y >= 0:
        r = {'origin': [8, y, 8], 'axis': 'y', 'angle': AUGER_TURN[i % 4]}
        els.append(box([8 - w, y, 7], [8 + w, y + 1.5, 9], '#bit', rotation=r))
        els.append(box([7, y, 8 - w], [9, y + 1.5, 8 + w], '#bit', rotation=r))
        y, w, i = y - 2, max(2, w - 1), i + 1
    return els


def corners(lo, hi, y0, y1, tex):
    return [box([x, y0, z], [x + hi - lo, y1, z + hi - lo], tex) for x in (lo, 16 - hi) for z in (lo, 16 - hi)]


def lower():
    return [
        box([0, 0, 0], [16, 2, 2], '#bottom'), box([0, 0, 14], [16, 2, 16], '#bottom'),
        box([0, 0, 2], [2, 2, 14], '#bottom'), box([14, 0, 2], [16, 2, 14], '#bottom'),
        *corners(0, 2, 2, 16, '#side'),
        housing([2, 9, 2], [14, 15, 14], back='#back'),
        box([5, 7, 5], [11, 9, 11], '#side'),
    ]


AUGER_TOP, AUGER_WIDTH = 7, 12


def lantern(lit):
    faces = {f: {'texture': '#lamp', 'uv': [0, 0, 16, 16]} for f in FACES}
    el = {'from': [6, 16, 6], 'to': [10, 21, 10], 'faces': faces}
    if lit:
        el['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
    return el


def upper(lit=False):
    return [
        *corners(0, 2, 0, 14, '#side'),
        box([2, 6, 0], [14, 7, 1], '#side'), box([2, 6, 15], [14, 7, 16], '#side'),
        box([0, 6, 2], [1, 7, 14], '#side'), box([15, 6, 2], [16, 7, 14], '#side'),
        box([0, 14, 0], [16, 16, 16], {'north': '#side', 'south': '#side', 'east': '#side', 'west': '#side',
                                       'up': '#top', 'down': '#side'}),
        housing([5, 0, 5], [11, 9, 11], top='#top'),
        box([6, 0, -2], [10, 2, 5], '#spout'),
        lantern(lit),
        box([7, 21, 7], [9, 22, 9], '#spout'),
    ]


def shifted(elements, dy, rename=None):
    out = json.loads(json.dumps(elements))
    for el in out:
        el['from'][1] += dy
        el['to'][1] += dy
        if 'rotation' in el:
            el['rotation']['origin'][1] += dy
        for face in el['faces'].values():
            if rename and face['texture'] in rename:
                face['texture'] = rename[face['texture']]
    return out


def rewrite(path, elements):
    with open(path, encoding='utf-8') as fh:
        m = json.load(fh)
    m['elements'] = elements
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(m, fh, indent=2)
        fh.write('\n')


def models():
    n = 0
    for tier in ('', '_t2', '_t3'):
        base = f'mining_drill{tier}'
        for state in ('lower', 'lower_working'):
            rewrite(os.path.join(MODELS, 'block', f'{base}_{state}.json'), lower())
            n += 1
        for state in ('upper_working', 'upper_stopped', 'upper_no_power'):
            rewrite(os.path.join(MODELS, 'block', f'{base}_{state}.json'), upper(state != 'upper_no_power'))
            n += 1
        # the item shows both halves in one model, centred on the block (lower half from -8)
        rewrite(os.path.join(MODELS, 'item', f'{base}.json'),
                shifted(lower() + auger(AUGER_TOP, AUGER_WIDTH), -8)
                + shifted(upper(), 8, {'#front': '#ufront', '#side': '#uside'}))
        n += 1
    # one auger for every tier: the bit texture is shared, and skins swap it like the rest of the drill
    with open(os.path.join(MODELS, 'block', 'mining_drill_auger.json'), 'w', encoding='utf-8', newline='\n') as fh:
        json.dump({'parent': 'minecraft:block/block',
                   'textures': {'bit': 'holeydigsite:block/mining_drill_bit', 'particle': 'holeydigsite:block/mining_drill_bit'},
                   'elements': auger(AUGER_TOP, AUGER_WIDTH)}, fh, indent=2)
        fh.write('\n')
    return n + 1


STEEL = [(64, 66, 74), (98, 101, 110), (138, 142, 152), (178, 182, 192), (214, 217, 224)]


def bit_frame(shift):
    """Bright steel with dark diagonal grooves; moving the grooves down a frame at a time reads as the bit turning."""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            d = (x + y + shift) % 8
            c = STEEL[(0, 1, 2, 3, 4, 3, 2, 1)[d]]
            if (x * 7 + y * 13) % 11 == 0:
                c = tuple(max(0, v - 18) for v in c)
            img.putpixel((x, y), c + (255,))
    return img


def lantern_tex(inner, outer):
    """A caged glass lamp: dark caps and bars over glass that is brightest in the middle."""
    cap, bar = (54, 56, 62), (38, 40, 46)
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if y < 2 or y > 13:
                c = cap if y not in (1, 14) else (92, 96, 104)
            elif x in (0, 15, 7, 8):
                c = bar
            else:
                d = min(1.0, (abs(x - 7.5) / 7.5 + abs(y - 7.5) / 6) / 1.6)
                c = tuple(round(inner[i] + (outer[i] - inner[i]) * d) for i in range(3))
            img.putpixel((x, y), c + (255,))
    return img


LANTERNS = {'working': ((255, 244, 200), (255, 190, 90)), 'stopped': ((255, 92, 62), (196, 26, 22)),
            'no_power': ((84, 92, 104), (46, 52, 62))}


def textures():
    for state, (inner, outer) in LANTERNS.items():
        lantern_tex(inner, outer).save(os.path.join(TEX, f'mining_drill_lantern_{state}.png'))
    still = bit_frame(0)
    still.save(os.path.join(TEX, 'mining_drill_bit.png'))
    strip = Image.new('RGBA', (16, 64))
    for f in range(4):
        strip.paste(bit_frame(f * 2), (0, f * 16))
    strip.save(os.path.join(TEX, 'mining_drill_bit_working.png'))


def main():
    n = models()
    textures()
    print(f'{n} drill models rewritten, bit textures drawn')


if __name__ == '__main__':
    main()
