"""Generates textures and models for Phase 28a (Outback event content).

Blocks:
  - holeydigsite:red_dirt_rock
  - holeydigsite:mullock_rock
  - holeydigsite:opal_clay_rock
  - holeydigsite:hardpan

Clumps (3D lump models):
  - holeydigsite:red_dirt_clump
  - holeydigsite:mullock_clump
  - holeydigsite:opal_clay_clump

Outback Gold:
  - chunks small, medium, large (3D chunk models)
  - ingot (ingot bar model with size variants)

Plain Find:
  - holeydigsite:ironstone_pebble

Gems (cut and uncut, play of colour for opals):
  - potch / uncut_potch
  - boulder_opal / uncut_boulder_opal
  - sapphire_parti / uncut_sapphire_parti
  - black_opal / uncut_black_opal
  - fire_opal / uncut_fire_opal
  - pink_diamond / uncut_pink_diamond

Trophy:
  - holeydigsite:welcome_stranger (72 kg legendary nugget)

Usage:
  python tools/make_phase28a_assets.py
"""

import json
import math
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'holeydigsite')
BLOCK_TEX = os.path.join(ROOT, 'textures', 'block')
ITEM_TEX = os.path.join(ROOT, 'textures', 'item')
LUMP_TEX = os.path.join(ITEM_TEX, 'lump')
CHUNK_TEX = os.path.join(ITEM_TEX, 'chunk')
INGOT_TEX = os.path.join(ITEM_TEX, 'ingot')
BLOCK_MODELS = os.path.join(ROOT, 'models', 'block')
ITEM_MODELS = os.path.join(ROOT, 'models', 'item')
BLOCKSTATES = os.path.join(ROOT, 'blockstates')

for d in (BLOCK_TEX, ITEM_TEX, LUMP_TEX, CHUNK_TEX, INGOT_TEX, BLOCK_MODELS, ITEM_MODELS, BLOCKSTATES):
    os.makedirs(d, exist_ok=True)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def shade(rgb, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in rgb)


def mix(rgb1, rgb2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(rgb1, rgb2))


# ------------------------------------------------------------------------------------------------
# 1. Blocks: textures, blockstates, block models, item models
# ------------------------------------------------------------------------------------------------

def make_red_dirt_rock():
    # Rich red-orange outback iron-rich soil & rock
    rng = random.Random(28101)
    base = (175, 75, 42)
    dark = (130, 52, 28)
    light = (205, 102, 58)
    iron = (105, 40, 22)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = rng.random()
            if r < 0.22:
                c = dark
            elif r < 0.32:
                c = iron
            elif r > 0.78:
                c = light
            else:
                c = base
            # subtle shading variation
            c = shade(c, 0.92 + 0.16 * rng.random())
            px[x, y] = c + (255,)
    img.save(os.path.join(BLOCK_TEX, 'red_dirt_rock.png'))


def make_mullock_rock():
    # Pale grey-white broken mine mullock rock
    rng = random.Random(28102)
    base = (195, 195, 190)
    dark = (150, 150, 145)
    light = (225, 225, 220)
    shadow = (125, 125, 120)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = rng.random()
            if r < 0.18:
                c = shadow
            elif r < 0.38:
                c = dark
            elif r > 0.75:
                c = light
            else:
                c = base
            c = shade(c, 0.94 + 0.12 * rng.random())
            px[x, y] = c + (255,)
    img.save(os.path.join(BLOCK_TEX, 'mullock_rock.png'))


def make_opal_clay_rock():
    # Grey-blue clay with sparkling opal flecks
    rng = random.Random(28103)
    base = (112, 128, 138)
    dark = (85, 96, 106)
    light = (142, 156, 166)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = rng.random()
            if r < 0.25:
                c = dark
            elif r > 0.75:
                c = light
            else:
                c = base
            c = shade(c, 0.95 + 0.1 * rng.random())
            px[x, y] = c + (255,)

    # Bright opal flecks: emerald green, electric cyan, fiery magenta
    opal_flecks = [
        ((45, 235, 150), [(3, 4), (11, 2), (7, 10), (13, 12)]),
        ((50, 215, 255), [(4, 4), (12, 2), (2, 11), (10, 8)]),
        ((255, 85, 140), [(7, 9), (14, 12), (5, 13)])
    ]
    for col, spots in opal_flecks:
        for x, y in spots:
            px[x, y] = col + (255,)
            # glow halo
            for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                nx, ny = (x + dx) % 16, (y + dy) % 16
                if rng.random() < 0.3:
                    px[nx, ny] = mix(px[nx, ny][:3], col, 0.4) + (255,)

    img.save(os.path.join(BLOCK_TEX, 'opal_clay_rock.png'))


def make_hardpan():
    # Cracked dry tan arid soil
    rng = random.Random(28104)
    base = (192, 154, 112)
    light = (218, 178, 134)
    dark = (156, 120, 84)
    fissure = (108, 76, 50)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = rng.random()
            if r < 0.22:
                c = dark
            elif r > 0.75:
                c = light
            else:
                c = base
            px[x, y] = c + (255,)

    # Distinct dry desert cracks
    cracks = [
        (2, 3), (3, 4), (4, 4), (5, 5), (6, 6), (7, 6), (8, 7), (9, 8),
        (9, 9), (10, 10), (11, 11), (12, 12), (13, 13),
        (6, 6), (6, 7), (5, 8), (5, 9), (4, 10),
        (8, 7), (9, 6), (10, 5), (11, 5), (12, 4),
        (1, 12), (2, 13), (3, 13), (4, 14)
    ]
    for x, y in cracks:
        px[x % 16, y % 16] = fissure + (255,)
        # cracked edge highlight
        if rng.random() < 0.7:
            px[(x + 1) % 16, y % 16] = light + (255,)

    img.save(os.path.join(BLOCK_TEX, 'hardpan.png'))


def write_block_models_and_states():
    blocks = ['red_dirt_rock', 'mullock_rock', 'opal_clay_rock', 'hardpan']
    for b in blocks:
        # block model
        write_json(os.path.join(BLOCK_MODELS, f'{b}.json'), {
            "parent": "minecraft:block/cube_all",
            "textures": {
                "all": f"holeydigsite:block/{b}"
            }
        })
        # blockstate
        write_json(os.path.join(BLOCKSTATES, f'{b}.json'), {
            "variants": {
                "": {"model": f"holeydigsite:block/{b}"}
            }
        })
        # item model
        write_json(os.path.join(ITEM_MODELS, f'{b}.json'), {
            "parent": f"holeydigsite:block/{b}"
        })


# ------------------------------------------------------------------------------------------------
# 2. Clumps: 3D models and textures
# ------------------------------------------------------------------------------------------------

BOTTOM = 6.0
CLUMP = [
    (2, 3, 14, 13, 0, 3),
    (4, 4.5, 11, 11.5, 3, 5.5),
    (9, 8, 13, 12, 3, 4.5),
    (3, 8.5, 6, 12, 3, 4.5),
    (5, 1.5, 10, 3, 0, 2),
]


def num(v):
    return int(v) if float(v).is_integer() else v


def elements(boxes):
    els = []
    for (x0, z0, x1, z1, a, b) in boxes:
        y0, y1 = BOTTOM + a, BOTTOM + b
        faces = {f: {"texture": "#t"} for f in ("north", "south", "east", "west", "up")}
        if a == 0:
            faces["down"] = {"texture": "#t"}
        els.append({"from": [num(x0), num(y0), num(z0)], "to": [num(x1), num(y1), num(z1)], "faces": faces})
    return els


def clump_display():
    top = 5.5
    cy = BOTTOM + top / 2
    c30 = math.cos(math.radians(30))
    gui_scale = 0.85
    gui_ty = round(-(cy - 8) * c30 * gui_scale, 3)
    hand_ty = round(-(cy - 8) * 0.55, 3)
    third_ty = round(-(cy - 8) * 0.5 + 1.5, 3)
    ground_ty = round(-2 - (BOTTOM - 8) * 0.5, 3)
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, gui_ty, 0], "scale": [gui_scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, ground_ty, 0], "scale": [0.5] * 3},
        "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, third_ty, 0], "scale": [0.5] * 3},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, third_ty, 0], "scale": [0.5] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, hand_ty, 0], "scale": [0.55] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, hand_ty, 0], "scale": [0.55] * 3},
    }


def make_clumps():
    clumps = {
        'red_dirt': dict(pal=[(130, 52, 28), (175, 75, 42), (205, 102, 58), (105, 40, 22)], seed=28201),
        'mullock': dict(pal=[(145, 145, 140), (190, 190, 185), (225, 225, 220), (120, 120, 115)], seed=28202),
        'opal_clay': dict(pal=[(85, 96, 106), (112, 128, 138), (142, 156, 166), (70, 80, 90)], seed=28203,
                          speck=(50, 215, 255), specks=8),
    }

    for name, k in clumps.items():
        rng = random.Random(k['seed'])
        dark, mid, light, deep = k['pal']
        img = Image.new('RGBA', (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                r = rng.random()
                c = mid
                if r < 0.22:
                    c = dark
                elif r > 0.82:
                    c = light
                elif r < 0.30:
                    c = deep
                if y < 5:
                    c = mix(c, light, 0.12)
                if y >= 8:
                    c = shade(c, 0.8 if y < 12 else 0.68)
                px[x, y] = c + (255,)
        if 'speck' in k:
            for _ in range(k['specks']):
                x, y = rng.randrange(16), rng.randrange(16)
                px[x, y] = k['speck'] + (255,)

        img.save(os.path.join(LUMP_TEX, f'{name}.png'))

        # 3D clump item model
        model = {
            "textures": {
                "particle": "#t",
                "t": f"holeydigsite:item/lump/{name}"
            },
            "elements": elements(CLUMP),
            "display": clump_display()
        }
        write_json(os.path.join(ITEM_MODELS, f'{name}_clump.json'), model)


# ------------------------------------------------------------------------------------------------
# 3. Outback Gold: Chunks and Ingot
# ------------------------------------------------------------------------------------------------

def make_outback_gold():
    # Chunk texture: rich Australian gold (235, 175, 45) with warm highlights and gleam
    rng = random.Random(28301)
    base = (235, 175, 45)
    hi = (255, 230, 110)
    rock = (110, 60, 35)  # reddish outback matrix speck
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = rng.random()
            c = base
            if r < 0.12:
                c = rock
            elif r < 0.30:
                c = shade(base, 0.82)
            elif r > 0.84:
                c = mix(base, hi, 0.55)
            if y < 6:
                c = mix(c, hi, 0.2)
            if y >= 8:
                c = shade(c, 0.78 if y < 12 else 0.66)
            px[x, y] = c + (255,)
    # sparkles
    glint = (255, 255, 220, 255)
    for x, y in [(3, 2), (11, 1), (7, 3), (13, 4), (5, 1), (9, 4)]:
        px[x, y] = glint
    img.save(os.path.join(CHUNK_TEX, 'outback_gold.png'))

    # Chunk item models pointing to chunk_shape_<size>
    for size in ('small', 'medium', 'large'):
        write_json(os.path.join(ITEM_MODELS, f'outback_gold_chunk_{size}.json'), {
            "parent": f"holeydigsite:item/chunk_shape_{size}",
            "textures": {
                "t": "holeydigsite:item/chunk/outback_gold"
            }
        })

    # Ingot texture: clean 16x16 bar texture
    ingot_img = Image.new('RGBA', (16, 16))
    ipx = ingot_img.load()
    for y in range(16):
        for x in range(16):
            c = base
            if y < 4:
                c = mix(base, hi, 0.6)
            elif y < 8:
                c = mix(base, hi, 0.2)
            elif y < 12:
                c = shade(base, 0.88)
            else:
                c = shade(base, 0.72)
            if x in (0, 15) or y in (0, 15):
                c = shade(c, 0.8)
            ipx[x, y] = c + (255,)
    # bevel glint
    for x in range(2, 14):
        ipx[x, 1] = mix(hi, (255, 255, 255), 0.7) + (255,)
    ingot_img.save(os.path.join(INGOT_TEX, 'outback_gold.png'))

    # Ingot models: base with size overrides, and size 1, 2, 3 models
    write_json(os.path.join(ITEM_MODELS, 'outback_gold_ingot.json'), {
        "parent": "holeydigsite:item/ingot_size_0",
        "textures": {
            "bar": "holeydigsite:item/ingot/outback_gold"
        },
        "overrides": [
            {"predicate": {"holeydigsite:size": 1}, "model": "holeydigsite:item/outback_gold_ingot_1"},
            {"predicate": {"holeydigsite:size": 2}, "model": "holeydigsite:item/outback_gold_ingot_2"},
            {"predicate": {"holeydigsite:size": 3}, "model": "holeydigsite:item/outback_gold_ingot_3"}
        ]
    })
    for sz in (1, 2, 3):
        write_json(os.path.join(ITEM_MODELS, f'outback_gold_ingot_{sz}.json'), {
            "parent": f"holeydigsite:item/ingot_size_{sz}",
            "textures": {
                "bar": "holeydigsite:item/ingot/outback_gold"
            }
        })


# ------------------------------------------------------------------------------------------------
# 4. Ironstone Pebble
# ------------------------------------------------------------------------------------------------

def make_ironstone_pebble():
    # Smooth dark brownish-grey ironstone river pebble
    grid = [
        '................',
        '................',
        '................',
        '................',
        '.....aaaaaa.....',
        '....abbcccda....',
        '...abbccccddda..',
        '..abbccccddddda.',
        '..abbcccdddddda.',
        '..abbccddddddda.',
        '...abdddddddda..',
        '....addddddda...',
        '.....aaaaaa.....',
        '................',
        '................',
        '................',
    ]
    pal = {
        '.': (0, 0, 0, 0),
        'a': (58, 42, 36, 255),    # dark outline
        'b': (148, 112, 96, 255),  # light highlight
        'c': (116, 84, 72, 255),   # mid ironstone
        'd': (82, 58, 50, 255),    # shadow
    }
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = pal[grid[y][x]]
    img.save(os.path.join(ITEM_TEX, 'ironstone_pebble.png'))
    write_json(os.path.join(ITEM_MODELS, 'ironstone_pebble.json'), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "holeydigsite:item/ironstone_pebble"}
    })


# ------------------------------------------------------------------------------------------------
# 5. Gems: Cut & Uncut (with play of colour for opals)
# ------------------------------------------------------------------------------------------------

def render_sprite(grid, pal, filename):
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            ch = grid[y][x]
            px[x, y] = pal.get(ch, (0, 0, 0, 0))
    img.save(os.path.join(ITEM_TEX, filename + '.png'))
    write_json(os.path.join(ITEM_MODELS, filename + '.json'), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"holeydigsite:item/{filename}"}
    })


def make_gems():
    # Common opal: Potch (smooth milky white/cream without play of colour)
    potch_cut = [
        '................',
        '................',
        '.....aaaaaa.....',
        '....abbcccda....',
        '...abbbcccdda...',
        '..abbbbcccddda..',
        '..abbbbcccddda..',
        '..abbbbcccddda..',
        '..abbbcccdddda..',
        '...abbccdddda...',
        '....abccddda....',
        '.....aaaaaa.....',
        '................',
        '................',
        '................',
        '................',
    ]
    potch_pal = {
        '.': (0, 0, 0, 0),
        'a': (160, 162, 165, 255),
        'b': (250, 250, 252, 255),
        'c': (228, 230, 234, 255),
        'd': (190, 194, 200, 255),
    }
    render_sprite(potch_cut, potch_pal, 'potch')

    potch_uncut = [
        '................',
        '................',
        '................',
        '.....oooooo.....',
        '...oo222222oo...',
        '..o221111110o...',
        '..o2111111100o..',
        '.o221111111100o.',
        '.o211111111100o.',
        '..o1111111100o..',
        '..o0111111000o..',
        '...oo000000oo...',
        '.....oooooo.....',
        '................',
        '................',
        '................',
    ]
    potch_uncut_pal = {
        '.': (0, 0, 0, 0),
        'o': (140, 142, 146, 255),
        '2': (235, 236, 240, 255),
        '1': (215, 218, 222, 255),
        '0': (175, 178, 185, 255),
    }
    render_sprite(potch_uncut, potch_uncut_pal, 'uncut_potch')

    # Boulder Opal: Ironstone host matrix split by vivid blue/green precious opal seam
    boulder_cut = [
        '................',
        '.....aaaaaa.....',
        '....abbGccda....',
        '...abbBBccdda...',
        '..abbBGGccddda..',
        '..abbGBCccddda..',
        '..abmBGCccddda..',
        '..abmmBBccddda..',
        '..abmmmccdddda..',
        '...abmmcdddda...',
        '....abccddda....',
        '.....aaaaaa.....',
        '................',
        '................',
        '................',
        '................',
    ]
    boulder_pal = {
        '.': (0, 0, 0, 0),
        'a': (65, 42, 32, 255),    # dark ironstone outline
        'b': (145, 102, 75, 255),  # ironstone highlight
        'c': (110, 75, 52, 255),   # ironstone mid
        'd': (78, 50, 36, 255),    # ironstone shadow
        'm': (95, 62, 44, 255),
        'B': (30, 95, 235, 255),   # vivid royal blue opal
        'G': (35, 225, 140, 255),  # electric emerald green opal
        'C': (45, 240, 255, 255),  # brilliant cyan flash
    }
    render_sprite(boulder_cut, boulder_pal, 'boulder_opal')

    boulder_uncut = [
        '................',
        '................',
        '.....oooooo.....',
        '...oo22G222oo...',
        '..o22BB11110o...',
        '..o2BGG111100o..',
        '.o22BG11111100o.',
        '.o21BB11111100o.',
        '..o1111111100o..',
        '..o0111111000o..',
        '...oo000000oo...',
        '.....oooooo.....',
        '................',
        '................',
        '................',
        '................',
    ]
    boulder_uncut_pal = {
        '.': (0, 0, 0, 0),
        'o': (55, 36, 28, 255),
        '2': (130, 90, 65, 255),
        '1': (100, 68, 48, 255),
        '0': (70, 46, 32, 255),
        'B': (25, 80, 210, 255),
        'G': (30, 205, 120, 255),
    }
    render_sprite(boulder_uncut, boulder_uncut_pal, 'uncut_boulder_opal')

    # Parti Sapphire: Australian sapphire with yellow, green, and deep blue color zones
    sapphire_cut = [
        '................',
        '.....aaaaaa.....',
        '....aYYYYdda....',
        '...aYYYYGGdda...',
        '..aYYGGGGGddda..',
        '..aYGGGBBBddda..',
        '..aGGGBBBBddda..',
        '..aGBBBBBBddda..',
        '..aBBBBBBdddda..',
        '...aBBBBdddda...',
        '....aBBBddda....',
        '.....aaaaaa.....',
        '................',
        '................',
        '................',
        '................',
    ]
    sapphire_pal = {
        '.': (0, 0, 0, 0),
        'a': (20, 35, 65, 255),
        'Y': (235, 210, 65, 255),  # golden yellow zone
        'G': (75, 165, 60, 255),   # olive green transition
        'B': (28, 70, 185, 255),   # deep royal blue zone
        'd': (15, 35, 95, 255),    # dark facet shadow
    }
    render_sprite(sapphire_cut, sapphire_pal, 'sapphire_parti')

    sapphire_uncut = [
        '................',
        '................',
        '.....oooooo.....',
        '...ooYYYGG2oo...',
        '..oYYYYGGG110o..',
        '..oYYGGGBB1100o.',
        '.o2YGGGBBB1100o.',
        '.o2GGGBBBB1100o.',
        '..oGBBBBBB100o..',
        '..o0BBBBB1000o..',
        '...oo000000oo...',
        '.....oooooo.....',
        '................',
        '................',
        '................',
        '................',
    ]
    sapphire_uncut_pal = {
        '.': (0, 0, 0, 0),
        'o': (25, 30, 50, 255),
        '2': (180, 160, 50, 255),
        '1': (25, 55, 120, 255),
        '0': (15, 30, 75, 255),
        'Y': (215, 190, 55, 255),
        'G': (65, 140, 50, 255),
        'B': (24, 60, 150, 255),
    }
    render_sprite(sapphire_uncut, sapphire_uncut_pal, 'uncut_sapphire_parti')

    # Black Opal: Dark body tone with vivid multi-colour play of colour (red, green, blue)
    black_opal_cut = [
        '................',
        '.....aaaaaa.....',
        '....abbRccda....',
        '...abbRGCcdda...',
        '..abbGGCBcddda..',
        '..abbCBRBcddda..',
        '..abbRRGCcddda..',
        '..abbGGBCcddda..',
        '..abbcccddddda..',
        '...abbccdddda...',
        '....abccddda....',
        '.....aaaaaa.....',
        '................',
        '................',
        '................',
        '................',
    ]
    black_opal_pal = {
        '.': (0, 0, 0, 0),
        'a': (18, 18, 24, 255),    # black outline
        'b': (55, 58, 68, 255),    # dark charcoal highlight
        'c': (32, 34, 42, 255),    # dark body
        'd': (14, 15, 20, 255),    # deep shadow
        'R': (255, 55, 75, 255),   # fiery scarlet red flash
        'G': (40, 245, 130, 255),  # intense emerald green flash
        'C': (45, 220, 255, 255),  # electric cyan flash
        'B': (65, 80, 255, 255),   # violet-blue flash
    }
    render_sprite(black_opal_cut, black_opal_pal, 'black_opal')

    black_opal_uncut = [
        '................',
        '................',
        '.....oooooo.....',
        '...oo22R222oo...',
        '..o22RG11110o...',
        '..o2GCB111100o..',
        '.o22BR11111100o.',
        '.o21GR11111100o.',
        '..o1111111100o..',
        '..o0111111000o..',
        '...oo000000oo...',
        '.....oooooo.....',
        '................',
        '................',
        '................',
        '................',
    ]
    black_opal_uncut_pal = {
        '.': (0, 0, 0, 0),
        'o': (20, 20, 26, 255),
        '2': (50, 52, 60, 255),
        '1': (30, 32, 38, 255),
        '0': (16, 17, 22, 255),
        'R': (240, 50, 70, 255),
        'G': (35, 225, 120, 255),
        'C': (40, 200, 240, 255),
        'B': (60, 75, 240, 255),
    }
    render_sprite(black_opal_uncut, black_opal_uncut_pal, 'uncut_black_opal')

    # Fire Opal: Fiery amber-orange body with vivid scarlet and golden flash
    fire_opal_cut = [
        '................',
        '.....aaaaaa.....',
        '....abbYccda....',
        '...abbYYRcdda...',
        '..abbYRRRcddda..',
        '..abbFRYYcddda..',
        '..abbFFRYcddda..',
        '..abbFFFRcddda..',
        '..abbcccddddda..',
        '...abbccdddda...',
        '....abccddda....',
        '.....aaaaaa.....',
        '................',
        '................',
        '................',
        '................',
    ]
    fire_opal_pal = {
        '.': (0, 0, 0, 0),
        'a': (120, 30, 10, 255),
        'b': (255, 180, 60, 255),
        'c': (235, 105, 20, 255),
        'd': (160, 45, 12, 255),
        'Y': (255, 240, 80, 255),  # brilliant golden flash
        'R': (255, 55, 25, 255),   # fiery crimson flash
        'F': (245, 125, 25, 255),  # glowing amber
    }
    render_sprite(fire_opal_cut, fire_opal_pal, 'fire_opal')

    fire_opal_uncut = [
        '................',
        '................',
        '.....oooooo.....',
        '...oo22Y222oo...',
        '..o22YR11110o...',
        '..o2FRY111100o..',
        '.o22FR11111100o.',
        '.o21YR11111100o.',
        '..o1111111100o..',
        '..o0111111000o..',
        '...oo000000oo...',
        '.....oooooo.....',
        '................',
        '................',
        '................',
        '................',
    ]
    fire_opal_uncut_pal = {
        '.': (0, 0, 0, 0),
        'o': (110, 32, 10, 255),
        '2': (240, 150, 45, 255),
        '1': (210, 90, 18, 255),
        '0': (140, 40, 10, 255),
        'Y': (255, 230, 70, 255),
        'R': (245, 50, 20, 255),
        'F': (230, 110, 20, 255),
    }
    render_sprite(fire_opal_uncut, fire_opal_uncut_pal, 'uncut_fire_opal')

    # Pink Diamond: Argyle fancy intense pink brilliant cut diamond
    pink_diamond_cut = [
        '................',
        '......aaaa......',
        '....aabbcdda....',
        '...abbbWHcdda...',
        '..abbWHHHHcddda.',
        '.abbWHHHHHHcddda',
        '.abbHHHHHHHcddda',
        '.abbHHHHHHHcddda',
        '..abbHHHHHcddda.',
        '...abbHHHcddda..',
        '....abbHcddda...',
        '.....abcddda....',
        '......aaaa......',
        '................',
        '................',
        '................',
    ]
    pink_diamond_pal = {
        '.': (0, 0, 0, 0),
        'a': (165, 45, 95, 255),
        'b': (255, 190, 225, 255),
        'c': (240, 110, 175, 255),
        'd': (185, 55, 115, 255),
        'W': (255, 255, 255, 255),  # dazzling diamond facet sparkle
        'H': (255, 140, 200, 255),  # intense fancy pink body
    }
    render_sprite(pink_diamond_cut, pink_diamond_pal, 'pink_diamond')

    pink_diamond_uncut = [
        '................',
        '................',
        '.....oooooo.....',
        '...oo222222oo...',
        '..o22W111110o...',
        '..o2WH1111100o..',
        '.o22H111111100o.',
        '.o211111111100o.',
        '..o1111111100o..',
        '..o0111111000o..',
        '...oo000000oo...',
        '.....oooooo.....',
        '................',
        '................',
        '................',
        '................',
    ]
    pink_diamond_uncut_pal = {
        '.': (0, 0, 0, 0),
        'o': (150, 42, 85, 255),
        '2': (245, 160, 205, 255),
        '1': (225, 95, 160, 255),
        '0': (165, 50, 100, 255),
        'W': (255, 250, 255, 255),
        'H': (250, 130, 185, 255),
    }
    render_sprite(pink_diamond_uncut, pink_diamond_uncut_pal, 'uncut_pink_diamond')


# ------------------------------------------------------------------------------------------------
# 6. Trophy: Welcome Stranger (massive 72 kg natural gold nugget)
# ------------------------------------------------------------------------------------------------

def make_welcome_stranger():
    # Massive iconic natural gold nugget shape
    grid = [
        '................',
        '....aaaaaa......',
        '...abbbHccaa....',
        '..abbWHHHccda...',
        '.abbWHHHHHHccda.',
        '.abWHHHssHHccda.',
        '.abHHHssHHHccda.',
        '.abHHssHHHHHccda',
        '.abHHHHHHssHccda',
        '.abbHHHHssHHccda',
        '..abbHHHHHHccda.',
        '...abbHHHHccda..',
        '....abbHccdda...',
        '.....aaccddaa...',
        '......aaaa......',
        '................',
    ]
    pal = {
        '.': (0, 0, 0, 0),
        'a': (125, 78, 18, 255),    # dark golden-brown outline
        'b': (255, 235, 130, 255),  # light metallic crest
        'c': (215, 150, 32, 255),   # shadow flank
        'd': (165, 105, 22, 255),   # deep shadow
        'W': (255, 255, 215, 255),  # bright nugget glint
        'H': (245, 188, 48, 255),   # rich Australian gold body
        's': (140, 85, 25, 255),    # dark natural crevice
    }
    render_sprite(grid, pal, 'welcome_stranger')


def main():
    print('Generating Phase 28a assets...')
    make_red_dirt_rock()
    make_mullock_rock()
    make_opal_clay_rock()
    make_hardpan()
    write_block_models_and_states()
    make_clumps()
    make_outback_gold()
    make_ironstone_pebble()
    make_gems()
    make_welcome_stranger()
    print('Phase 28a assets generated successfully!')


if __name__ == '__main__':
    main()
