"""Generates textures, models, blockstates, loot table, and crate data for the Goldfields Crate."""

import json
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'holeydigsite')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'holeydigsite')
TEX = os.path.join(ASSETS, 'textures', 'block')

WOOD = ['#7A4222', '#6B381C', '#884C28', '#5E2F16']
SEAM = '#32160A'
RED_EARTH = ['#B44A24', '#9E3C18', '#C4562C', '#8A3010']
GOLD_MAIN = '#E0A820'
GOLD_LIGHT = '#F6D248'
GOLD_DARK = '#B07E0A'
GOLD_RIVET = '#FFF288'
DARK_KEYHOLE = (35, 20, 10, 255)


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(col, f):
    return (max(0, min(255, int(col[0] * f))),
            max(0, min(255, int(col[1] * f))),
            max(0, min(255, int(col[2] * f))),
            col[3])


def planks(rng, vertical=False):
    img = Image.new('RGBA', (16, 16))
    cols = [hexc(c) for c in WOOD]
    earth_cols = [hexc(c) for c in RED_EARTH]
    seam = hexc(SEAM)
    for y in range(16):
        for x in range(16):
            a, b = (x, y) if not vertical else (y, x)
            board = b // 4
            c = cols[board % len(cols)]
            if b % 4 == 3:
                c = seam
            elif rng.random() < 0.16:
                c = shade(c, 0.88 if rng.random() < 0.6 else 1.08)
            if (a + board * 5) % 11 == 0 and b % 4 == 1:
                c = shade(c, 0.82)
            # Red earth dusting: stronger near the bottom and seams
            dust_prob = 0.40 if y > 11 else (0.15 if y > 7 else 0.05)
            if rng.random() < dust_prob:
                c = rng.choice(earth_cols)
                if rng.random() < 0.3:
                    c = shade(c, 0.85)
            img.putpixel((x, y), c)
    return img


def gold_band(img, x0, x1, rng):
    main, light, dark = hexc(GOLD_MAIN), hexc(GOLD_LIGHT), hexc(GOLD_DARK)
    rivet = hexc(GOLD_RIVET)
    earth_cols = [hexc(c) for c in RED_EARTH]
    for y in range(16):
        for x in range(x0, x1 + 1):
            c = main
            if x == x0:
                c = light
            elif x == x1:
                c = dark
            # Slight metal texture variation
            if rng.random() < 0.12:
                c = shade(c, 0.92 if rng.random() < 0.5 else 1.06)
            # Red earth patina on band edges
            if (x == x0 or x == x1) and rng.random() < 0.20:
                c = rng.choice(earth_cols)
            img.putpixel((x, y), c)
    # Gold rivets/studs at top and bottom
    mid_x = x0 + (x1 - x0) // 2
    for y in (1, 14):
        img.putpixel((mid_x, y), rivet)


def gold_hasp(img, rng):
    main, light, dark = hexc(GOLD_MAIN), hexc(GOLD_LIGHT), hexc(GOLD_DARK)
    for y in range(4, 11):
        for x in range(6, 10):
            c = main
            if y == 4 or x == 6:
                c = light
            elif y == 10 or x == 9:
                c = dark
            img.putpixel((x, y), c)
    # Latch slot
    img.putpixel((7, 7), DARK_KEYHOLE)
    img.putpixel((8, 7), DARK_KEYHOLE)
    img.putpixel((7, 8), DARK_KEYHOLE)


def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def make_textures():
    rng = random.Random('goldfields_crate')
    # side
    side = planks(rng)
    gold_band(side, 2, 3, rng)
    gold_band(side, 12, 13, rng)
    save_png(side, os.path.join(TEX, 'goldfields_crate_side.png'))

    # front
    front = planks(rng)
    gold_band(front, 2, 3, rng)
    gold_band(front, 12, 13, rng)
    gold_hasp(front, rng)
    save_png(front, os.path.join(TEX, 'goldfields_crate_front.png'))

    # top
    top = planks(rng, vertical=True)
    gold_band(top, 2, 3, rng)
    gold_band(top, 12, 13, rng)
    save_png(top, os.path.join(TEX, 'goldfields_crate_top.png'))

    # bottom
    bottom = planks(rng)
    for y in range(16):
        for x in range(16):
            bottom.putpixel((x, y), shade(bottom.getpixel((x, y)), 0.75))
    save_png(bottom, os.path.join(TEX, 'goldfields_crate_bottom.png'))

    # padlock: heavy gold padlock body (8x6) at left, hardened steel shackle below
    pad = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    main, light, dark = hexc(GOLD_MAIN), hexc(GOLD_LIGHT), hexc(GOLD_DARK)
    for y in range(0, 6):
        for x in range(0, 8):
            c = main
            if y == 0 or x == 0:
                c = light
            elif y == 5 or x == 7:
                c = dark
            pad.putpixel((x, y), c)
    # Keyhole
    pad.putpixel((3, 2), DARK_KEYHOLE)
    pad.putpixel((4, 2), DARK_KEYHOLE)
    pad.putpixel((3, 3), DARK_KEYHOLE)
    # Shackle: dark iron/steel strip at y=8..10
    steel_light = hexc('#8A8A94')
    steel_dark = hexc('#50505A')
    for y in range(8, 11):
        for x in range(0, 8):
            pad.putpixel((x, y), steel_light if y == 8 else steel_dark)
    save_png(pad, os.path.join(TEX, 'goldfields_padlock.png'))


def make_models():
    tex = {
        "side": "holeydigsite:block/goldfields_crate_side",
        "front": "holeydigsite:block/goldfields_crate_front",
        "top": "holeydigsite:block/goldfields_crate_top",
        "bottom": "holeydigsite:block/goldfields_crate_bottom",
        "inside": "holeydigsite:block/crate_inside",
        "particle": "holeydigsite:block/goldfields_crate_side"
    }
    write_json(os.path.join(ASSETS, 'models', 'block', 'goldfields_crate.json'),
               {'parent': 'holeydigsite:block/crate_closed', 'textures': tex})
    write_json(os.path.join(ASSETS, 'models', 'block', 'goldfields_crate_open.json'),
               {'parent': 'holeydigsite:block/crate_open', 'textures': tex})

    # Locked model: copy elements from prospector_crate_locked with goldfields textures
    locked = json.load(open(os.path.join(ASSETS, 'models', 'block', 'prospector_crate_locked.json'), encoding='utf-8'))
    locked_text = json.dumps(locked).replace('prospector_', 'goldfields_')
    write_json(os.path.join(ASSETS, 'models', 'block', 'goldfields_crate_locked.json'), json.loads(locked_text))

    # Item model
    write_json(os.path.join(ASSETS, 'models', 'item', 'goldfields_crate.json'), {
        "parent": "holeydigsite:block/goldfields_crate",
        "overrides": [{"predicate": {"holeydigsite:locked": 1}, "model": "holeydigsite:block/goldfields_crate_locked"}]
    })

    # Blockstate
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for open_ in ('false', 'true'):
            for lk in ('false', 'true'):
                model = 'goldfields_crate_open' if open_ == 'true' else ('goldfields_crate_locked' if lk == 'true' else 'goldfields_crate')
                v = {"model": "holeydigsite:block/" + model}
                if y:
                    v["y"] = y
                variants[f"facing={facing},open={open_},locked={lk}"] = v
    write_json(os.path.join(ASSETS, 'blockstates', 'goldfields_crate.json'), {"variants": variants})


def make_loot_table():
    loot = {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [
                    {
                        "type": "minecraft:item",
                        "name": "holeydigsite:goldfields_crate",
                        "conditions": [
                            {
                                "condition": "minecraft:block_state_property",
                                "block": "holeydigsite:goldfields_crate",
                                "properties": {
                                    "open": "false",
                                    "locked": "true"
                                }
                            }
                        ],
                        "functions": [
                            {
                                "function": "minecraft:set_components",
                                "components": {
                                    "holeydigsite:locked": True
                                }
                            }
                        ]
                    },
                    {
                        "type": "minecraft:item",
                        "name": "holeydigsite:goldfields_crate",
                        "conditions": [
                            {
                                "condition": "minecraft:block_state_property",
                                "block": "holeydigsite:goldfields_crate",
                                "properties": {
                                    "open": "false",
                                    "locked": "false"
                                }
                            }
                        ]
                    }
                ],
                "conditions": [
                    {
                        "condition": "minecraft:survives_explosion"
                    }
                ]
            }
        ],
        "random_sequence": "holeydigsite:blocks/goldfields_crate"
    }
    write_json(os.path.join(DATA, 'loot_table', 'blocks', 'goldfields_crate.json'), loot)


def make_crate_data():
    crate = {
        "name": "crate.holeydigsite.collection.goldfields_crate",
        "look": "goldfields",
        "odds": {
            "common": 80,
            "rare": 16,
            "epic": 4
        },
        "finds": {
            "pan": 2000,
            "sluice": 1440,
            "drill": 1440,
            "shovel": 3000
        },
        "pools": {
            "common": [
                {"skin": "patina"},
                {"skin": "gunmetal"},
                {"item": "holeydigsite:ore_cart"},
                {"item": "holeydigsite:miners_lantern"}
            ],
            "rare": [
                {"skin": "ironbark"},
                {"skin": "enamel"},
                {"item": "holeydigsite:golden_pan_trophy"}
            ],
            "epic": [
                {"skin": "quartz_reef"},
                {"skin": "nugget"}
            ]
        }
    }
    write_json(os.path.join(DATA, 'crates', 'goldfields_crate.json'), crate)


if __name__ == '__main__':
    make_textures()
    make_models()
    make_loot_table()
    make_crate_data()
    print("Goldfields Crate assets generated successfully.")
