"""
Phase 30 assets: the Prospector Crate (weathered box with a hasp, plus a padlock model for locked crates), the Bolt Cutters
item, the Crate Switch block, and the loot tables of every crate (a locked crate drops locked).

Usage: python tools/make_phase30_assets.py   (run from the project folder)
Then:  python tools/make_pbr.py              (specular maps for the iron parts)
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


WOOD = ['#7A5F3E', '#6C5336', '#85694A', '#5F482E']
SEAM = '#3E2E1B'
IRON = ('#5B5E64', '#8A8E96', '#34363B')   # main, light, dark
BRASS = ('#C9962B', '#F0C75A', '#7E5A12')
DARK = hexc('#15110C')


def planks(rng, vertical=False):
    img = Image.new('RGBA', (16, 16))
    cols = [hexc(c) for c in WOOD]
    seam = hexc(SEAM)
    for y in range(16):
        for x in range(16):
            a, b = (x, y) if not vertical else (y, x)
            board = b // 4
            c = cols[board % len(cols)]
            if b % 4 == 3:
                c = seam
            elif rng.random() < 0.16:
                c = shade(c, 0.86 if rng.random() < 0.6 else 1.08)
            if (a + board * 5) % 11 == 0 and b % 4 == 1:
                c = shade(c, 0.8)
            if y > 11 and rng.random() < 0.35:
                c = shade(c, 0.78)   # grime near the bottom
            img.putpixel((x, y), c)
    return img


def band(img, x0, x1):
    main, light, dark = (hexc(c) for c in IRON)
    for y in range(16):
        for x in range(x0, x1 + 1):
            c = main
            if x == x0:
                c = light
            elif x == x1:
                c = dark
            img.putpixel((x, y), c)
    for y in (1, 14):
        img.putpixel((x0 + (x1 - x0) // 2, y), light)


def rust(img, rng, amount=14):
    for _ in range(amount):
        x, y = rng.randrange(16), rng.randrange(16)
        r, g, b, a = img.getpixel((x, y))
        if abs(r - g) < 20 and abs(g - b) < 20:   # grey iron only
            img.putpixel((x, y), hexc('#7A4A2A'))


def hasp(img):
    main, light, dark = (hexc(c) for c in IRON)
    for y in range(4, 11):
        for x in range(6, 10):
            c = main
            if y == 4 or x == 6:
                c = light
            elif y == 10 or x == 9:
                c = dark
            img.putpixel((x, y), c)
    img.putpixel((7, 7), DARK)
    img.putpixel((8, 7), DARK)
    img.putpixel((7, 8), DARK)


def textures():
    rng = random.Random('prospector_crate')
    side = planks(rng)
    band(side, 2, 3)
    band(side, 12, 13)
    rust(side, rng)
    save(side, os.path.join(TEX, 'prospector_crate_side.png'))
    front = planks(rng)
    band(front, 2, 3)
    band(front, 12, 13)
    hasp(front)
    rust(front, rng)
    save(front, os.path.join(TEX, 'prospector_crate_front.png'))
    top = planks(rng, vertical=True)
    band(top, 2, 3)
    band(top, 12, 13)
    rust(top, rng)
    save(top, os.path.join(TEX, 'prospector_crate_top.png'))
    bottom = planks(rng)
    for y in range(16):
        for x in range(16):
            bottom.putpixel((x, y), shade(bottom.getpixel((x, y)), 0.8))
    save(bottom, os.path.join(TEX, 'prospector_crate_bottom.png'))

    # padlock: body 6x5 at the left of the texture, shackle strip below it
    pad = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    main, light, dark = (hexc(c) for c in BRASS)
    for y in range(0, 6):
        for x in range(0, 8):
            c = main
            if y == 0 or x == 0:
                c = light
            elif y == 5 or x == 7:
                c = dark
            pad.putpixel((x, y), c)
    pad.putpixel((3, 2), DARK)
    pad.putpixel((4, 2), DARK)
    pad.putpixel((3, 3), DARK)
    steel = hexc(IRON[1])
    for y in range(8, 11):
        for x in range(0, 8):
            pad.putpixel((x, y), steel if y == 8 else hexc(IRON[0]))
    save(pad, os.path.join(TEX, 'prospector_padlock.png'))


def models():
    tex = {
        "side": "holeydigsite:block/prospector_crate_side",
        "front": "holeydigsite:block/prospector_crate_front",
        "top": "holeydigsite:block/prospector_crate_top",
        "bottom": "holeydigsite:block/prospector_crate_bottom",
        "inside": "holeydigsite:block/crate_inside",
        "particle": "holeydigsite:block/prospector_crate_side",
    }
    write_json(os.path.join(ASSETS, 'models/block/prospector_crate.json'),
               {'parent': 'holeydigsite:block/crate_closed', 'textures': tex})
    write_json(os.path.join(ASSETS, 'models/block/prospector_crate_open.json'),
               {'parent': 'holeydigsite:block/crate_open', 'textures': tex})
    closed = json.load(open(os.path.join(ASSETS, 'models/block/crate_closed.json')))

    def face(u0, v0, u1, v1):
        return {"uv": [u0, v0, u1, v1], "texture": "#padlock"}

    body = {"from": [6.5, 5, 0.3], "to": [9.5, 8.5, 1.05], "faces": {
        "north": face(0, 0, 8, 6), "south": face(0, 0, 8, 6), "east": face(0, 0, 4, 6), "west": face(0, 0, 4, 6),
        "up": face(0, 0, 8, 4), "down": face(0, 0, 8, 4)}}
    shackle = {"from": [7.3, 8.5, 0.55], "to": [8.7, 9.6, 0.85], "faces": {
        "north": face(0, 8, 8, 11), "south": face(0, 8, 8, 11), "east": face(0, 8, 8, 11), "west": face(0, 8, 8, 11),
        "up": face(0, 8, 8, 11)}}
    locked = dict(closed)
    locked['elements'] = closed['elements'] + [body, shackle]
    locked['textures'] = dict(tex, padlock="holeydigsite:block/prospector_padlock")
    write_json(os.path.join(ASSETS, 'models/block/prospector_crate_locked.json'), locked)

    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for open_ in ('false', 'true'):
            for lk in ('false', 'true'):
                model = 'prospector_crate_open' if open_ == 'true' else ('prospector_crate_locked' if lk == 'true' else 'prospector_crate')
                v = {"model": "holeydigsite:block/" + model}
                if y:
                    v["y"] = y
                variants[f"facing={facing},open={open_},locked={lk}"] = v
    write_json(os.path.join(ASSETS, 'blockstates/prospector_crate.json'), {"variants": variants})
    write_json(os.path.join(ASSETS, 'models/item/prospector_crate.json'), {
        "parent": "holeydigsite:block/prospector_crate",
        "overrides": [{"predicate": {"holeydigsite:locked": 1}, "model": "holeydigsite:block/prospector_crate_locked"}]})


def switch():
    # a small wall plaque: dark board, brass plate, a lever knob
    img = Image.new('RGBA', (16, 16))
    rng = random.Random('crate_switch')
    cols = [hexc(c) for c in WOOD]
    for y in range(16):
        for x in range(16):
            c = cols[(y // 4) % 4]
            if y % 4 == 3:
                c = hexc(SEAM)
            elif rng.random() < 0.14:
                c = shade(c, 0.88)
            img.putpixel((x, y), c)
    main, light, dark = (hexc(c) for c in BRASS)
    for y in range(3, 13):
        for x in range(3, 13):
            c = main
            if y == 3 or x == 3:
                c = light
            elif y == 12 or x == 12:
                c = dark
            img.putpixel((x, y), c)
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        img.putpixel((x, y), hexc(BRASS[2]))
    # box icon on the plate
    for y in range(6, 10):
        for x in range(5, 11):
            img.putpixel((x, y), hexc('#4B3A22'))
    for x in range(5, 11):
        img.putpixel((x, 7), hexc(IRON[0]))
    save(img, os.path.join(TEX, 'crate_switch.png'))
    def plaque(texture):
        return {
            "parent": "minecraft:block/block",
            "textures": {"0": "holeydigsite:block/" + texture, "particle": "holeydigsite:block/crate_switch"},
            "elements": [{"from": [3, 3, 14], "to": [13, 13, 16], "faces": {
                "north": {"uv": [3, 3, 13, 13], "texture": "#0"},
                "south": {"uv": [3, 3, 13, 13], "texture": "#0"},
                "east": {"uv": [14, 3, 16, 13], "texture": "#0"}, "west": {"uv": [14, 3, 16, 13], "texture": "#0"},
                "up": {"uv": [3, 14, 13, 16], "texture": "#0"}, "down": {"uv": [3, 14, 13, 16], "texture": "#0"}}}],
        }
    write_json(os.path.join(ASSETS, 'models/block/crate_switch.json'), plaque('crate_switch'))
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        variants[f"facing={facing}"] = {"model": "holeydigsite:block/crate_switch", "y": y} if y else {"model": "holeydigsite:block/crate_switch"}
    write_json(os.path.join(ASSETS, 'blockstates/crate_switch.json'), {"variants": variants})
    write_json(os.path.join(ASSETS, 'models/item/crate_switch.json'), {"parent": "holeydigsite:block/crate_switch",
        "display": {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}}})
    write_json(os.path.join(DATA, 'loot_table/blocks/crate_switch.json'), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "holeydigsite:crate_switch"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "holeydigsite:blocks/crate_switch"})


def cutters():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    red, red_l, red_d = hexc('#B03A2E'), hexc('#D9604F'), hexc('#7A2018')
    steel, steel_l, steel_d = hexc(IRON[1]), hexc('#C4C8CE'), hexc(IRON[2])
    # two handles from the bottom left to the pivot
    for i in range(0, 8):
        x, y = 1 + i, 14 - i
        for (dx, dy, c) in ((0, 0, red_l), (1, 0, red), (0, 1, red), (1, 1, red_d)):
            img.putpixel((x + dx - (1 if dy else 0), y + dy), c)
    for i in range(0, 8):
        x, y = 2 + i, 15 - i
        if x < 16 and y >= 0:
            img.putpixel((x, y), red_d if i % 2 else red)
    # jaws: two steel blades from the pivot to the upper right with a gap at the tip
    for i in range(0, 6):
        x, y = 8 + i, 8 - i
        img.putpixel((x, y), steel_l)
        img.putpixel((x + 1, y), steel)
        img.putpixel((x, y + 1), steel)
        img.putpixel((x + 1, y + 1), steel_d)
    img.putpixel((13, 2), (0, 0, 0, 0))
    img.putpixel((14, 3), steel_d)
    img.putpixel((8, 8), hexc('#2A2C30'))   # pivot
    save(img, os.path.join(ITEM_TEX, 'bolt_cutters.png'))
    write_json(os.path.join(ASSETS, 'models/item/bolt_cutters.json'),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "holeydigsite:item/bolt_cutters"}})


def loot():
    """Every crate drops locked when it is locked and still closed."""
    for name in ('wooden_crate', 'iron_crate', 'gold_crate', 'outback_crate', 'prospector_crate'):
        block = "holeydigsite:" + name

        def entry(locked):
            e = {"type": "minecraft:item", "name": block,
                 "conditions": [{"condition": "minecraft:block_state_property", "block": block,
                                 "properties": {"open": "false", "locked": "true" if locked else "false"}}]}
            if locked:
                e["functions"] = [{"function": "minecraft:set_components", "components": {"holeydigsite:locked": True}}]
            return e

        write_json(os.path.join(DATA, f'loot_table/blocks/{name}.json'), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [entry(True), entry(False)],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"holeydigsite:blocks/{name}"})


if __name__ == '__main__':
    textures()
    models()
    switch()
    cutters()
    loot()
    print('ok')
