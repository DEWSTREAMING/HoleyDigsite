"""
Mining Crate assets: crate textures (wooden, iron, gold), closed/open crate models, the four crate-only decor blocks,
Skin Scrap item texture, blockstates, item models and loot tables.

Usage: python tools/make_phase23_assets.py   (run from the project folder)

The decor reuses existing textures where it shows our things: the Sluice Model uses the sluice textures, the Golden Drill
Statue the Brass drill skin, the helmet lamp the Miner's Lantern.
"""

import json
import os
import random

from PIL import Image

RES = "src/main/resources"
ASSETS = os.path.join(RES, "assets/holeydigsite")
DATA = os.path.join(RES, "data/holeydigsite")
TEX = os.path.join(ASSETS, "textures/block")
ITEM_TEX = os.path.join(ASSETS, "textures/item")


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ------------------------------------------------------------------------------------------------ crate textures
WOODS = {
    'wooden_crate': ['#A27B4C', '#94703F', '#B08954', '#8A673A'],
    'iron_crate': ['#9A7246', '#8B653B', '#A67D4E', '#7F5D35'],
    'gold_crate': ['#4E3622', '#43301E', '#5A3F28', '#3B2A1A'],
}
SEAM = {'wooden_crate': '#5E4322', 'iron_crate': '#55401F', 'gold_crate': '#231710'}
TRIM = {  # (main, light, dark)
    'wooden_crate': ('#6B4E2A', '#7E5E34', '#4F381D'),
    'iron_crate': ('#8C9198', '#BCC1C8', '#5B5F66'),
    'gold_crate': ('#E3B22E', '#FFE27A', '#A2740E'),
}


def planks(tier, rng, vertical=False):
    img = Image.new('RGBA', (16, 16))
    cols = [hexc(c) for c in WOODS[tier]]
    seam = hexc(SEAM[tier])
    for y in range(16):
        for x in range(16):
            a, b = (x, y) if not vertical else (y, x)
            board = b // 4
            c = cols[board % len(cols)]
            if b % 4 == 3:
                c = seam
            elif rng.random() < 0.12:
                c = shade(c, 0.9 if rng.random() < 0.5 else 1.08)
            if (a + board * 5) % 11 == 0 and b % 4 == 1:
                c = shade(c, 0.82)  # grain knot
            img.putpixel((x, y), c)
    return img


def frame(img, tier, width=1, rivets=False):
    main, light, dark = (hexc(c) for c in TRIM[tier])
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            if d < width:
                c = main
                if x == 0 or y == 0:
                    c = light
                if x == 15 or y == 15:
                    c = dark
                img.putpixel((x, y), c)
    if rivets:
        for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
            img.putpixel((x, y), light)


def brace(img, tier):
    main, light, dark = (hexc(c) for c in TRIM[tier])
    for i in range(2, 14):
        img.putpixel((i, 15 - i), main)
        img.putpixel((i, 14 - i), light if tier == 'gold_crate' else shade(main, 1.1))


def rope(img):
    rope_c, rope_d = hexc('#CDB27A'), hexc('#9C8350')
    for x in range(5, 11):
        img.putpixel((x, 5), rope_c if x % 2 else rope_d)
    for y in range(5, 8):
        img.putpixel((5, y), rope_d)
        img.putpixel((10, y), rope_d)
    for x in range(5, 11):
        img.putpixel((x, 8), rope_d if x % 2 else rope_c)


def lock(img):
    gold, light, dark = hexc('#E3B22E'), hexc('#FFE27A'), hexc('#A2740E')
    for y in range(2, 8):
        for x in range(6, 10):
            img.putpixel((x, y), light if y == 2 or x == 6 else (dark if x == 9 or y == 7 else gold))
    img.putpixel((7, 4), hexc('#1A1208'))
    img.putpixel((8, 4), hexc('#1A1208'))
    img.putpixel((7, 5), hexc('#1A1208'))


def crate_textures():
    for tier in WOODS:
        rng = random.Random(tier)
        trim_w = 2 if tier == 'iron_crate' else 1
        side = planks(tier, rng)
        brace(side, tier)
        frame(side, tier, trim_w, rivets=tier != 'wooden_crate')
        if tier == 'wooden_crate':
            rope(side)
        save(side, os.path.join(TEX, f'{tier}_side.png'))
        front = planks(tier, random.Random(tier + 'f'))
        frame(front, tier, trim_w, rivets=tier != 'wooden_crate')
        if tier == 'gold_crate':
            lock(front)
        elif tier == 'iron_crate':
            main, light, dark = (hexc(c) for c in TRIM[tier])
            for x in range(2, 14):
                front.putpixel((x, 7), main)
                front.putpixel((x, 8), dark)
        else:
            stencil = hexc('#4F381D')
            for (x, y) in ((5, 6), (6, 7), (7, 8), (8, 8), (9, 7), (10, 6), (7, 7), (8, 7)):
                front.putpixel((x, y), stencil)
        save(front, os.path.join(TEX, f'{tier}_front.png'))
        top = planks(tier, random.Random(tier + 't'), vertical=True)
        frame(top, tier, trim_w, rivets=tier != 'wooden_crate')
        save(top, os.path.join(TEX, f'{tier}_top.png'))
        bottom = planks(tier, random.Random(tier + 'b'), vertical=True)
        save(bottom, os.path.join(TEX, f'{tier}_bottom.png'))
    # inside: dark boards with straw
    rng = random.Random('inside')
    inside = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = hexc('#3A2A18') if y % 4 != 3 else hexc('#2A1E10')
            if rng.random() < 0.35:
                c = hexc('#C9A84E') if rng.random() < 0.6 else hexc('#A88A3A')
            inside.putpixel((x, y), c)
    save(inside, os.path.join(TEX, 'crate_inside.png'))


def face(tex, uv=(0, 0, 16, 16), cull=None, rotation=0):
    f = {'uv': list(uv), 'texture': tex}
    if cull:
        f['cullface'] = cull
    if rotation:
        f['rotation'] = rotation
    return f


def box(frm, to, faces, rotation=None):
    e = {'from': frm, 'to': to, 'faces': faces}
    if rotation:
        e['rotation'] = rotation
    return e


def crate_models():
    body = box([1, 0, 1], [15, 11, 15], {
        'north': face('#front', (1, 5, 15, 16)), 'south': face('#side', (1, 5, 15, 16)),
        'east': face('#side', (1, 5, 15, 16)), 'west': face('#side', (1, 5, 15, 16)),
        'up': face('#inside', (1, 1, 15, 15)), 'down': face('#bottom', (1, 1, 15, 15), 'down')})
    lid_faces = {
        'north': face('#side', (0, 0, 16, 2.5)), 'south': face('#side', (0, 0, 16, 2.5)),
        'east': face('#side', (0, 0, 16, 2.5)), 'west': face('#side', (0, 0, 16, 2.5)),
        'up': face('#top'), 'down': face('#inside')}
    closed = {'parent': 'minecraft:block/block', 'ambientocclusion': False,
              'textures': {'particle': '#side'},
              'elements': [body, box([0.5, 11, 0.5], [15.5, 13.5, 15.5], lid_faces)],
              'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 1, 0], 'scale': [0.625, 0.625, 0.625]}}}
    opened = {'parent': 'minecraft:block/block', 'ambientocclusion': False,
              'textures': {'particle': '#side'},
              'elements': [body, box([0.5, 11, 0.5], [15.5, 13.5, 15.5], lid_faces,
                                     {'origin': [8, 11, 15.5], 'axis': 'x', 'angle': 45})]}
    write_json(os.path.join(ASSETS, 'models/block/crate_closed.json'), closed)
    write_json(os.path.join(ASSETS, 'models/block/crate_open.json'), opened)
    for tier in WOODS:
        tex = {k: f'holeydigsite:block/{tier}_{k}' for k in ('side', 'front', 'top', 'bottom')}
        tex['inside'] = 'holeydigsite:block/crate_inside'
        tex['particle'] = f'holeydigsite:block/{tier}_side'
        write_json(os.path.join(ASSETS, f'models/block/{tier}.json'), {'parent': 'holeydigsite:block/crate_closed', 'textures': tex})
        write_json(os.path.join(ASSETS, f'models/block/{tier}_open.json'), {'parent': 'holeydigsite:block/crate_open', 'textures': tex})
        write_json(os.path.join(ASSETS, f'models/item/{tier}.json'), {'parent': f'holeydigsite:block/{tier}'})
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for open_ in ('false', 'true'):
                v = {'model': f'holeydigsite:block/{tier}' + ('_open' if open_ == 'true' else '')}
                if y:
                    v['y'] = y
                variants[f'facing={facing},open={open_}'] = v
        write_json(os.path.join(ASSETS, f'blockstates/{tier}.json'), {'variants': variants})
        write_json(os.path.join(DATA, f'loot_table/blocks/{tier}.json'), {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1, 'bonus_rolls': 0,
                       'entries': [{'type': 'minecraft:item', 'name': f'holeydigsite:{tier}'}],
                       'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'holeydigsite:{tier}',
                                       'properties': {'open': 'false'}},
                                      {'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': f'holeydigsite:blocks/{tier}'})


# ------------------------------------------------------------------------------------------------ decor
def all_faces(tex, uv=None, cull_down=False):
    f = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        f[d] = face(tex, uv) if uv else {'texture': tex}
    if cull_down:
        f['down']['cullface'] = 'down'
    return f


def decor_textures():
    rng = random.Random('helmet')
    helmet = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = hexc('#D9A825') if y < 12 else hexc('#7A5A12')
            if y in (5, 6):
                c = hexc('#9E7A16')
            if rng.random() < 0.1:
                c = shade(c, 0.88)
            helmet.putpixel((x, y), c)
    save(helmet, os.path.join(TEX, 'miners_helmet.png'))
    # lamp: the lens is drawn in the 8x8 square at (4,4); the rest of the sheet is rim metal for the sides
    lamp = Image.new('RGBA', (16, 16))
    rim = [hexc('#4A3A12'), hexc('#6E5518'), hexc('#8A6C22')]
    for y in range(16):
        for x in range(16):
            lamp.putpixel((x, y), rim[(x * 3 + y) % 3])
    for y in range(8):
        for x in range(8):
            d = ((x - 3.5) ** 2 + (y - 3.5) ** 2) ** 0.5
            if d > 3.9:
                c = rim[0] if d > 4.3 else rim[1]
            elif d > 3.0:
                c = hexc('#A8821E')          # inner rim
            elif d > 1.6:
                c = hexc('#FFD34A')          # warm glow
            else:
                c = hexc('#FFF6C8')          # bright centre
            lamp.putpixel((4 + x, 4 + y), c)
    save(lamp, os.path.join(TEX, 'miners_helmet_lamp.png'))
    rng = random.Random('bell')
    bell = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = hexc('#8E5A2C')
            r = rng.random()
            if r < 0.3:
                c = hexc('#6E3F1E')
            elif r < 0.42:
                c = hexc('#4F8A6E')  # verdigris
            elif r < 0.5:
                c = hexc('#B07A3E')
            if y == 0:
                c = shade(c, 1.15)
            bell.putpixel((x, y), c)
    save(bell, os.path.join(TEX, 'rusty_mine_bell.png'))


def decor_models():
    spruce = 'minecraft:block/spruce_planks'
    log = 'minecraft:block/stripped_spruce_log'
    dark = 'minecraft:block/dark_oak_log'
    models = {
        'miners_helmet_stand': ({'0': spruce, '1': log, '2': 'holeydigsite:block/miners_helmet',
                                  '3': 'holeydigsite:block/miners_helmet_lamp', 'particle': 'holeydigsite:block/miners_helmet'}, [
            box([4, 0, 4], [12, 2, 12], all_faces('#0', (4, 4, 12, 12), True)),
            box([7, 2, 7], [9, 11, 9], all_faces('#1', (7, 2, 9, 11))),
            box([4, 11, 4], [12, 15, 12], all_faces('#2', (4, 1, 12, 5))),
            box([3, 11, 3], [13, 12, 13], all_faces('#2', (3, 12, 13, 13))),
            box([6, 11.5, 2.5], [10, 15.5, 4], {'north': face('#3', (4, 4, 12, 12)), 'up': face('#3', (0, 0, 2, 2)),
                                                'down': face('#3', (0, 0, 2, 2)), 'east': face('#3', (0, 0, 2, 2)),
                                                'west': face('#3', (0, 0, 2, 2))}),
        ]),
        'rusty_mine_bell': ({'0': dark, '1': 'holeydigsite:block/rusty_mine_bell', 'particle': 'holeydigsite:block/rusty_mine_bell'}, [
            box([1, 0, 6.5], [3, 16, 9.5], all_faces('#0', (0, 0, 2, 16), True)),
            box([13, 0, 6.5], [15, 16, 9.5], all_faces('#0', (0, 0, 2, 16), True)),
            box([3, 13, 7], [13, 15, 9], all_faces('#0', (0, 0, 10, 2))),
            box([7.5, 11, 7.5], [8.5, 13, 8.5], all_faces('#1', (0, 0, 1, 2))),
            box([5.5, 6, 5.5], [10.5, 11, 10.5], all_faces('#1', (2, 2, 7, 7))),
            box([4.5, 4, 4.5], [11.5, 6, 11.5], all_faces('#1', (1, 9, 8, 11))),
        ]),
        'sluice_model': ({'0': 'holeydigsite:block/sluice_wood', '1': 'holeydigsite:block/sluice_riffle',
                          '2': 'holeydigsite:block/sluice_ramp', '3': log, 'particle': 'holeydigsite:block/sluice_wood'}, [
            box([2, 0, 4], [3, 4, 5], all_faces('#3', (0, 0, 1, 4), True)),
            box([13, 0, 4], [14, 3, 5], all_faces('#3', (0, 0, 1, 3), True)),
            box([2, 0, 11], [3, 4, 12], all_faces('#3', (0, 0, 1, 4), True)),
            box([13, 0, 11], [14, 3, 12], all_faces('#3', (0, 0, 1, 3), True)),
            box([1, 4, 3], [15, 5, 13], {'up': face('#1', (1, 3, 15, 13)), 'down': face('#0', (1, 3, 15, 13)),
                                         'north': face('#0', (1, 0, 15, 1)), 'south': face('#0', (1, 0, 15, 1)),
                                         'east': face('#0', (3, 0, 13, 1)), 'west': face('#0', (3, 0, 13, 1))}),
            box([1, 5, 3], [15, 8, 4], all_faces('#0', (1, 0, 15, 3))),
            box([1, 5, 12], [15, 8, 13], all_faces('#0', (1, 0, 15, 3))),
            box([14, 5, 4], [15, 9, 12], all_faces('#0', (4, 0, 12, 4))),
            box([11, 5, 4], [14, 7, 12], {'up': face('#2', (0, 0, 16, 16)), 'west': face('#0', (4, 0, 12, 2))},
                {'origin': [14, 5, 8], 'axis': 'z', 'angle': -22.5}),
        ]),
        'golden_drill_statue': ({'0': 'minecraft:block/polished_deepslate', '1': 'minecraft:block/gold_block',
                                 '2': 'holeydigsite:block/skin/brass/mining_drill_lower_front',
                                 '3': 'holeydigsite:block/skin/brass/mining_drill_lower_side',
                                 '4': 'holeydigsite:block/skin/brass/mining_drill_upper_front',
                                 '5': 'holeydigsite:block/skin/brass/mining_drill_upper_side',
                                 '6': 'holeydigsite:block/skin/brass/mining_drill_upper_top',
                                 'particle': 'minecraft:block/gold_block'}, [
            box([2, 0, 2], [14, 3, 14], all_faces('#0', (2, 2, 14, 14), True)),
            box([3, 3, 3], [13, 4, 13], all_faces('#1', (3, 3, 13, 13))),
            box([5, 4, 5], [11, 10, 11], {'north': face('#2', (0, 0, 16, 16)), 'south': face('#3'), 'east': face('#3'),
                                          'west': face('#3'), 'down': face('#3')}),
            box([5, 10, 5], [11, 16, 11], {'north': face('#4'), 'south': face('#5'), 'east': face('#5'),
                                           'west': face('#5'), 'up': face('#6')}),
        ]),
    }
    for name, (tex, elements) in models.items():
        write_json(os.path.join(ASSETS, f'models/block/{name}.json'),
                   {'parent': 'minecraft:block/block', 'textures': tex, 'elements': elements})
        write_json(os.path.join(ASSETS, f'models/item/{name}.json'), {'parent': f'holeydigsite:block/{name}'})
        variants = {}
        for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            v = {'model': f'holeydigsite:block/{name}'}
            if y:
                v['y'] = y
            variants[f'facing={facing}'] = v
        write_json(os.path.join(ASSETS, f'blockstates/{name}.json'), {'variants': variants})
        write_json(os.path.join(DATA, f'loot_table/blocks/{name}.json'), {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': f'holeydigsite:{name}'}],
                       'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': f'holeydigsite:blocks/{name}'})


def skin_scrap():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    rng = random.Random('scrap')
    colors = ['#C9A04A', '#5B6FD6', '#8E3FB0', '#6E8A9A', '#B5652E', '#E0E0E0']
    chips = [(3, 4, 4, 3), (8, 3, 4, 3), (5, 8, 5, 3), (10, 8, 3, 4), (3, 11, 4, 2), (8, 12, 4, 2)]
    for i, (x0, y0, w, h) in enumerate(chips):
        c = hexc(colors[i % len(colors)])
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if (x == x0 + w - 1 and y == y0) or rng.random() < 0.08:
                    continue
                img.putpixel((x, y), shade(c, 1.15) if y == y0 else (shade(c, 0.75) if y == y0 + h - 1 else c))
    save(img, os.path.join(ITEM_TEX, 'skin_scrap.png'))
    write_json(os.path.join(ASSETS, 'models/item/skin_scrap.json'),
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'holeydigsite:item/skin_scrap'}})


def main():
    crate_textures()
    crate_models()
    decor_textures()
    decor_models()
    skin_scrap()
    print('Phase 23 assets written')


if __name__ == '__main__':
    main()
