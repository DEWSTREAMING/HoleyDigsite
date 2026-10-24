"""Generates the Phase 8 textures AND the model / blockstate JSON for the shop blocks (deterministic; needs Pillow).

    python tools/make_phase8_assets.py

Textures (16x16, vanilla style): Buy Pad (blue blueprint top), Money Pan (tray metal + coin piles), Shop Button
(post, red button, pressed), Shop Display (plinth), Blueprint item (blue paper scroll).
Models: buy_pad, money_pan_0..3 (tray + baked coin piles), shop_button / shop_button_pressed, shop_display, item models.
"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'holeydigsite')
BLOCK = os.path.join(RES, 'textures', 'block')
ITEM = os.path.join(RES, 'textures', 'item')
MODELS = os.path.join(RES, 'models')
STATES = os.path.join(RES, 'blockstates')
T = (0, 0, 0, 0)


def new():
    return Image.new('RGBA', (16, 16), T)


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + '.png'))


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def write_json(folder, name, data):
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, name + '.json'), 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


# ------------------------------------------------------------------------------------------------ textures
WOOD = [(88, 62, 36), (100, 71, 42), (112, 80, 48), (122, 88, 53)]
TRIM = (60, 42, 25)


def buy_pad():
    rnd = random.Random(8101)
    blue = {'o': (20, 44, 92), 'd': (36, 78, 150), 'm': (52, 104, 190), 'l': (96, 150, 226), 'w': (226, 238, 255)}
    top = new()
    for y in range(16):
        for x in range(16):
            c = blue['m'] if rnd.random() > 0.2 else blue['d']
            if x in (0, 15) or y in (0, 15):
                c = blue['o']
            elif x in (1, 14) or y in (1, 14):
                c = blue['l']
            top.putpixel((x, y), rgba(c))
    # blueprint grid + a tiny gear/scroll symbol
    for i in range(3, 14, 3):
        for k in range(2, 14):
            top.putpixel((i, k), rgba(blue['l']))
            top.putpixel((k, i), rgba(blue['l']))
    scroll = ['.wwwwwwww.', 'wwllllllww', 'w.wwwwww.w', 'w.w....w.w', 'w.wwwwww.w', 'w.w....w.w', 'w.wwwwww.w', 'wwllllllww', '.wwwwwwww.']
    for y, row in enumerate(scroll):
        for x, ch in enumerate(row):
            if ch == 'w':
                top.putpixel((x + 3, y + 3), rgba(blue['w']))
            elif ch == 'l':
                top.putpixel((x + 3, y + 3), rgba(blue['l']))
    save(top, BLOCK, 'buy_pad_top')
    side = new()
    for y in range(16):
        for x in range(16):
            c = WOOD[rnd.choice((0, 1, 1, 2))]
            if y in (12, 15):
                c = TRIM
            if y == 13:
                c = blue['m'] if x % 4 else blue['l']
            side.putpixel((x, y), rgba(c))
    save(side, BLOCK, 'buy_pad_side')
    bottom = new()
    for y in range(16):
        for x in range(16):
            bottom.putpixel((x, y), rgba(WOOD[rnd.choice((0, 0, 1))]))
    save(bottom, BLOCK, 'buy_pad_bottom')


def money_pan():
    rnd = random.Random(8202)
    metal = [(120, 124, 132), (136, 140, 148), (152, 156, 164), (104, 108, 116)]
    side = new()
    for y in range(16):
        for x in range(16):
            c = metal[rnd.choice((0, 1, 1, 2))]
            if y in (0, 15):
                c = metal[3]
            side.putpixel((x, y), rgba(c))
    save(side, BLOCK, 'money_pan_side')
    inner = new()
    for y in range(16):
        for x in range(16):
            c = (70, 74, 82) if rnd.random() > 0.3 else (58, 62, 70)
            inner.putpixel((x, y), rgba(c))
    save(inner, BLOCK, 'money_pan_inner')
    rim = new()
    for y in range(16):
        for x in range(16):
            rim.putpixel((x, y), rgba(metal[2] if (x + y) % 5 else metal[1]))
    save(rim, BLOCK, 'money_pan_rim')
    # coin piles: stacked coin discs seen from the side (rows of rims) with highlights
    for name, pal in (('copper', ((120, 62, 30), (178, 96, 48), (222, 140, 82), (244, 190, 130))),
                      ('silver', ((110, 118, 130), (170, 180, 194), (214, 224, 236), (244, 250, 255))),
                      ('gold', ((122, 82, 14), (196, 140, 32), (236, 186, 56), (255, 232, 130)))):
        img = new()
        for y in range(16):
            for x in range(16):
                row = y % 4
                c = pal[1] if row in (1, 2) else pal[0] if row == 3 else pal[2]
                if row == 0 and (x + y // 4 * 3) % 6 == 0:
                    c = pal[3]
                if (x + (y // 4) * 5) % 8 == 7:
                    c = pal[0]
                img.putpixel((x, y), rgba(c))
        save(img, BLOCK, 'coin_pile_' + name)


def shop_button():
    rnd = random.Random(8303)
    post = new()
    for y in range(16):
        for x in range(16):
            c = (74, 78, 88) if rnd.random() > 0.25 else (90, 94, 104)
            if y in (0, 15):
                c = (52, 56, 64)
            post.putpixel((x, y), rgba(c))
    for x in (2, 13):                              # rivets
        post.putpixel((x, 2), rgba((190, 194, 204)))
        post.putpixel((x, 13), rgba((190, 194, 204)))
    save(post, BLOCK, 'shop_button_post')
    for name, base in (('', (214, 40, 40)), ('_pressed', (140, 24, 24))):
        top = new()
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                d = (dx * dx + dy * dy) ** 0.5
                c = (60, 64, 72)
                if d < 7.4:
                    c = tuple(int(v * 0.55) for v in base)
                if d < 6.4:
                    k = 1.0 - d / 14.0
                    c = tuple(min(255, int(v * (0.85 + 0.3 * k))) for v in base)
                if name == '' and d < 3.4 and dx < 0.5 and dy < 0.5:
                    c = (255, 150, 140)
                top.putpixel((x, y), rgba(c))
        save(top, BLOCK, 'shop_button_top' + name)
        side = new()
        for y in range(16):
            for x in range(16):
                shade = 0.7 + 0.3 * (1 - abs(x - 7.5) / 8)
                side.putpixel((x, y), rgba(tuple(int(v * shade) for v in base)))
        save(side, BLOCK, 'shop_button_side' + name)


def shop_display():
    rnd = random.Random(8404)
    side = new()
    for y in range(16):
        for x in range(16):
            c = WOOD[rnd.choice((0, 1, 1, 2))]
            if y in (0, 15):
                c = TRIM
            side.putpixel((x, y), rgba(c))
    for x in range(3, 13):                          # brass plaque
        for y in range(5, 10):
            edge = x in (3, 12) or y in (5, 9)
            side.putpixel((x, y), rgba((120, 84, 24) if edge else (214, 164, 52)))
    save(side, BLOCK, 'shop_display_side')
    top = new()
    for y in range(16):
        for x in range(16):
            c = (96, 28, 40) if (x + y) % 7 else (112, 36, 50)
            if x in (0, 15) or y in (0, 15):
                c = (60, 18, 26)
            top.putpixel((x, y), rgba(c))
    save(top, BLOCK, 'shop_display_top')


def blueprint():
    pal = {'o': (18, 40, 92), 'd': (34, 76, 150), 'm': (58, 112, 204), 'l': (130, 178, 244), 'w': (232, 242, 255), 'r': (150, 110, 60), 'R': (110, 76, 38)}
    rows = [
        '................',
        '..rrrr..........',
        '.rRRRRr.oooooo..',
        '.rRRRRroddddddo.',
        '..rrrrodmmmmmmdo',
        '....ooddmwwwmmdo',
        '....odmmwlllwmdo',
        '....odmmwlwlwmdo',
        '....odmmwlllwmdo',
        '....odmmmwwwmmdo',
        '....odmmlmmlmmdo',
        '....odmmmllmmmdo',
        '.....odmmmmmmddo',
        '.....oddddddddo.',
        '......oooooooo..',
        '................',
    ]
    img = new()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), rgba(pal[ch]))
    save(img, ITEM, 'blueprint')


# ------------------------------------------------------------------------------------------------ models
def cube(frm, to, tex_by_face, shade=True):
    faces = {}
    for f in ('north', 'south', 'west', 'east', 'up', 'down'):
        faces[f] = {'texture': '#' + tex_by_face.get(f, tex_by_face['all'])}
    return {'from': frm, 'to': to, 'faces': faces}


def pad_model(name):
    write_json(os.path.join(MODELS, 'block'), name, {
        'parent': 'minecraft:block/block',
        'textures': {'particle': f'holeydigsite:block/{name}_side', 'top': f'holeydigsite:block/{name}_top',
                     'side': f'holeydigsite:block/{name}_side', 'bottom': f'holeydigsite:block/{name}_bottom'},
        'elements': [{'from': [0, 0, 0], 'to': [16, 4, 16], 'faces': {
            'north': {'texture': '#side'}, 'south': {'texture': '#side'}, 'west': {'texture': '#side'},
            'east': {'texture': '#side'}, 'up': {'texture': '#top'}, 'down': {'texture': '#bottom', 'cullface': 'down'}}}]})


def tray_elements():
    s = {'all': 'side', 'up': 'rim', 'down': 'side'}
    return [
        cube([1, 0, 1], [15, 1, 15], {'all': 'side', 'up': 'inner'}),
        cube([1, 1, 1], [15, 3, 2], s), cube([1, 1, 14], [15, 3, 15], s),
        cube([1, 1, 2], [2, 3, 14], s), cube([14, 1, 2], [15, 3, 14], s),
    ]


PILES = {
    1: [([4, 1, 4], [7, 2.5, 7], 'copper'), ([8, 1, 6], [11, 2, 9], 'copper'), ([6, 1, 9], [9, 3, 12], 'copper')],
    2: [([3, 1, 3], [7, 4, 7], 'silver'), ([8, 1, 3], [12, 3, 7], 'silver'), ([3, 1, 8], [7, 3, 12], 'copper'),
        ([8, 1, 8], [12, 4.5, 12], 'silver'), ([6, 1, 6], [10, 5, 10], 'silver')],
    3: [([2, 1, 2], [14, 3.5, 14], 'gold'), ([3, 3.5, 3], [13, 5.5, 13], 'gold'), ([4.5, 5.5, 4.5], [11.5, 7.5, 11.5], 'gold'),
        ([6, 7.5, 6], [10, 9, 10], 'gold'), ([2.5, 3.5, 11], [6, 5, 14], 'silver'), ([10, 3.5, 2], [13.5, 5, 5.5], 'silver')],
}


def money_pan_models():
    tex = {'particle': 'holeydigsite:block/money_pan_side', 'side': 'holeydigsite:block/money_pan_side',
           'inner': 'holeydigsite:block/money_pan_inner', 'rim': 'holeydigsite:block/money_pan_rim',
           'copper': 'holeydigsite:block/coin_pile_copper', 'silver': 'holeydigsite:block/coin_pile_silver',
           'gold': 'holeydigsite:block/coin_pile_gold'}
    for pile in range(4):
        els = tray_elements()
        for frm, to, kind in PILES.get(pile, []):
            els.append(cube(frm, to, {'all': kind}))
        write_json(os.path.join(MODELS, 'block'), f'money_pan_{pile}', {'parent': 'minecraft:block/block', 'textures': tex, 'elements': els})
    write_json(os.path.join(MODELS, 'item'), 'money_pan', {'parent': 'holeydigsite:block/money_pan_2'})
    write_json(STATES, 'money_pan', {'variants': {f'locked={l},pile={p}': {'model': f'holeydigsite:block/money_pan_{p}'}
                                                  for l in ('false', 'true') for p in range(4)}})


def button_models():
    for suffix, top in (('', 9), ('_pressed', 8)):
        tex = {'particle': 'holeydigsite:block/shop_button_post', 'post': 'holeydigsite:block/shop_button_post',
               'btop': f'holeydigsite:block/shop_button_top{suffix}', 'bside': f'holeydigsite:block/shop_button_side{suffix}'}
        els = [cube([4, 0, 4], [12, 1, 12], {'all': 'post'}), cube([6, 1, 6], [10, top - 3, 10], {'all': 'post'}),
               {'from': [4, top - 3, 4], 'to': [12, top, 12], 'faces': {
                   'north': {'texture': '#bside'}, 'south': {'texture': '#bside'}, 'west': {'texture': '#bside'},
                   'east': {'texture': '#bside'}, 'up': {'texture': '#btop'}, 'down': {'texture': '#post'}}}]
        write_json(os.path.join(MODELS, 'block'), 'shop_button' + suffix, {'parent': 'minecraft:block/block', 'textures': tex, 'elements': els})
    write_json(os.path.join(MODELS, 'item'), 'shop_button', {'parent': 'holeydigsite:block/shop_button'})
    write_json(STATES, 'shop_button', {'variants': {f'locked={l},pressed={p}': {'model': 'holeydigsite:block/shop_button' + ('_pressed' if p == 'true' else '')}
                                                    for l in ('false', 'true') for p in ('false', 'true')}})


def display_model():
    tex = {'particle': 'holeydigsite:block/shop_display_side', 'side': 'holeydigsite:block/shop_display_side',
           'top': 'holeydigsite:block/shop_display_top'}
    els = [{'from': [2, 0, 2], 'to': [14, 3, 14], 'faces': {
        'north': {'texture': '#side'}, 'south': {'texture': '#side'}, 'west': {'texture': '#side'}, 'east': {'texture': '#side'},
        'up': {'texture': '#top'}, 'down': {'texture': '#side', 'cullface': 'down'}}}]
    write_json(os.path.join(MODELS, 'block'), 'shop_display', {'parent': 'minecraft:block/block', 'textures': tex, 'elements': els})
    write_json(os.path.join(MODELS, 'item'), 'shop_display', {'parent': 'holeydigsite:block/shop_display'})
    write_json(STATES, 'shop_display', {'variants': {'': {'model': 'holeydigsite:block/shop_display'}}})


def pads():
    pad_model('buy_pad')
    write_json(os.path.join(MODELS, 'item'), 'buy_pad', {'parent': 'holeydigsite:block/buy_pad'})
    for n in ('sell_pad', 'buy_pad'):
        write_json(STATES, n, {'variants': {f'locked={l}': {'model': f'holeydigsite:block/{n}'} for l in ('false', 'true')}})


def blueprint_model():
    write_json(os.path.join(MODELS, 'item'), 'blueprint', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'holeydigsite:item/blueprint'}})


if __name__ == '__main__':
    buy_pad(); money_pan(); shop_button(); shop_display(); blueprint()
    pads(); money_pan_models(); button_models(); display_model(); blueprint_model()
    print('Phase 8 assets written to', os.path.abspath(RES))
