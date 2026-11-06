"""
Phase 35 assets: Scythe hand tool texture & model, Sprinkler block & item models,
block textures, loot table, and preview sheet for Dewey.

Usage: python tools/make_phase35_assets.py
"""

import json
import os
from PIL import Image, ImageDraw, ImageFont

RES = "src/main/resources"
ASSETS = os.path.join(RES, "assets/holeydigsite")
DATA = os.path.join(RES, "data/holeydigsite")
BLOCK_TEX = os.path.join(ASSETS, "textures/block")
ITEM_TEX = os.path.join(ASSETS, "textures/item")
PREVIEW_DIR = "docs/preview/phase35"


def hexc(h, a=255):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ------------------------------------------------------------------------------------------------
# 1. Scythe Texture & Model
# ------------------------------------------------------------------------------------------------
def make_scythe():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))

    wood = hexc('#7A5F3E')
    wood_l = hexc('#9A7952')
    wood_d = hexc('#5A4226')

    steel = hexc('#8A8E96')
    steel_l = hexc('#C8CCD4')
    steel_d = hexc('#484B52')
    steel_edge = hexc('#E8ECF4')

    brass = hexc('#C9962B')
    brass_d = hexc('#8A6314')

    # Curved snath (handle) from (2, 14) up to (10, 4)
    handle_pts = [
        (2, 14), (3, 13), (3, 12), (4, 11), (5, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5), (10, 4)
    ]
    for x, y in handle_pts:
        img.putpixel((x, y), wood)
        if x > 2 and y < 14:
            img.putpixel((x - 1, y), wood_d)
        if x < 15 and y > 0:
            img.putpixel((x + 1, y), wood_l)

    # Hand grips (nibs) sticking out
    img.putpixel((5, 11), wood_l)
    img.putpixel((7, 9), wood_l)

    # Collar / chine (clamp holding blade to handle)
    img.putpixel((10, 4), brass)
    img.putpixel((10, 3), brass)
    img.putpixel((11, 4), brass_d)

    # Curved blade extending out to the left from top
    # Blade back (darker) and sharp cutting edge (bright)
    blade_back = [
        (10, 3), (9, 2), (8, 2), (7, 2), (6, 2), (5, 3), (4, 4), (3, 5), (2, 7)
    ]
    blade_body = [
        (9, 3), (8, 3), (7, 3), (6, 3), (5, 4), (4, 5), (3, 6)
    ]
    blade_edge = [
        (8, 4), (7, 4), (6, 4), (5, 5), (4, 6), (3, 7)
    ]

    for x, y in blade_back:
        img.putpixel((x, y), steel_d)
    for x, y in blade_body:
        img.putpixel((x, y), steel)
    for x, y in blade_edge:
        img.putpixel((x, y), steel_edge)

    # Blade point
    img.putpixel((2, 8), steel_edge)

    save(img, os.path.join(ITEM_TEX, 'scythe.png'))

    # Item model
    write_json(os.path.join(ASSETS, 'models/item/scythe.json'), {
        "parent": "minecraft:item/handheld",
        "textures": {
            "layer0": "holeydigsite:item/scythe"
        }
    })


# ------------------------------------------------------------------------------------------------
# 2. Sprinkler Textures & Models
# ------------------------------------------------------------------------------------------------
def make_sprinkler():
    # Base texture: 16x16 cast iron casing
    base_img = Image.new('RGBA', (16, 16))
    iron = hexc('#4B4E54')
    iron_l = hexc('#6A6E77')
    iron_d = hexc('#323439')
    rust = hexc('#684532')

    for y in range(16):
        for x in range(16):
            c = iron
            if x in (0, 15) or y in (0, 15):
                c = iron_d
            elif (x + y * 3) % 7 == 0:
                c = rust
            elif (x * 3 + y) % 5 == 0:
                c = iron_l
            base_img.putpixel((x, y), c)

    save(base_img, os.path.join(BLOCK_TEX, 'sprinkler_base.png'))

    # Top nozzle texture: 16x16 brass with spray holes and water sheen
    top_img = Image.new('RGBA', (16, 16))
    brass = hexc('#C9962B')
    brass_l = hexc('#E8B84B')
    brass_d = hexc('#7A5714')
    hole = hexc('#221808')
    water = hexc('#4B9AE8')

    for y in range(16):
        for x in range(16):
            # Concentric ring design
            dx = x - 7.5
            dy = y - 7.5
            r2 = dx * dx + dy * dy
            if r2 < 4:
                c = hole  # center nozzle orifice
            elif r2 < 12:
                c = water if (x + y) % 2 == 0 else brass_l
            elif r2 < 28:
                c = brass
            elif r2 < 40:
                c = hole if (x in (4, 11) and y in (4, 11)) else brass_d
            else:
                c = iron_d
            top_img.putpixel((x, y), c)

    save(top_img, os.path.join(BLOCK_TEX, 'sprinkler_top.png'))

    # Side nozzle texture
    side_img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if y < 4:
                c = brass_l if x in (7, 8) else brass
            elif y < 12:
                c = iron_l if x in (6, 9) else iron
                if x in (0, 1, 14, 15):
                    c = iron_d
            else:
                c = iron_d if y == 15 else iron
            side_img.putpixel((x, y), c)

    save(side_img, os.path.join(BLOCK_TEX, 'sprinkler_side.png'))

    # Block model: small compact sprinkler machine
    model = {
        "parent": "minecraft:block/block",
        "ambientocclusion": True,
        "textures": {
            "particle": "holeydigsite:block/sprinkler_side",
            "base": "holeydigsite:block/sprinkler_base",
            "side": "holeydigsite:block/sprinkler_side",
            "top": "holeydigsite:block/sprinkler_top"
        },
        "elements": [
            # Base plinth
            {
                "from": [4, 0, 4],
                "to": [12, 3, 12],
                "faces": {
                    "down": {"uv": [4, 4, 12, 12], "texture": "#base", "cullface": "down"},
                    "up": {"uv": [4, 4, 12, 12], "texture": "#base"},
                    "north": {"uv": [4, 13, 12, 16], "texture": "#side"},
                    "south": {"uv": [4, 13, 12, 16], "texture": "#side"},
                    "west": {"uv": [4, 13, 12, 16], "texture": "#side"},
                    "east": {"uv": [4, 13, 12, 16], "texture": "#side"}
                }
            },
            # Side pipe collars (north, south, east, west)
            {
                "from": [5, 1, 3],
                "to": [11, 3, 4],
                "faces": {
                    "north": {"uv": [5, 13, 11, 15], "texture": "#side"},
                    "up": {"uv": [5, 3, 11, 4], "texture": "#base"},
                    "down": {"uv": [5, 3, 11, 4], "texture": "#base", "cullface": "down"},
                    "west": {"uv": [3, 13, 4, 15], "texture": "#side"},
                    "east": {"uv": [3, 13, 4, 15], "texture": "#side"}
                }
            },
            {
                "from": [5, 1, 12],
                "to": [11, 3, 13],
                "faces": {
                    "south": {"uv": [5, 13, 11, 15], "texture": "#side"},
                    "up": {"uv": [5, 12, 11, 13], "texture": "#base"},
                    "down": {"uv": [5, 12, 11, 13], "texture": "#base", "cullface": "down"},
                    "west": {"uv": [12, 13, 13, 15], "texture": "#side"},
                    "east": {"uv": [12, 13, 13, 15], "texture": "#side"}
                }
            },
            {
                "from": [3, 1, 5],
                "to": [4, 3, 11],
                "faces": {
                    "west": {"uv": [5, 13, 11, 15], "texture": "#side"},
                    "up": {"uv": [3, 5, 4, 11], "texture": "#base"},
                    "down": {"uv": [3, 5, 4, 11], "texture": "#base", "cullface": "down"},
                    "north": {"uv": [3, 13, 4, 15], "texture": "#side"},
                    "south": {"uv": [3, 13, 4, 15], "texture": "#side"}
                }
            },
            {
                "from": [12, 1, 5],
                "to": [13, 3, 11],
                "faces": {
                    "east": {"uv": [5, 13, 11, 15], "texture": "#side"},
                    "up": {"uv": [12, 5, 13, 11], "texture": "#base"},
                    "down": {"uv": [12, 5, 13, 11], "texture": "#base", "cullface": "down"},
                    "north": {"uv": [12, 13, 13, 15], "texture": "#side"},
                    "south": {"uv": [12, 13, 13, 15], "texture": "#side"}
                }
            },
            # Central riser column
            {
                "from": [6, 3, 6],
                "to": [10, 8, 10],
                "faces": {
                    "north": {"uv": [6, 8, 10, 13], "texture": "#side"},
                    "south": {"uv": [6, 8, 10, 13], "texture": "#side"},
                    "west": {"uv": [6, 8, 10, 13], "texture": "#side"},
                    "east": {"uv": [6, 8, 10, 13], "texture": "#side"}
                }
            },
            # Brass nozzle head
            {
                "from": [5, 8, 5],
                "to": [11, 11, 11],
                "faces": {
                    "up": {"uv": [5, 5, 11, 11], "texture": "#top"},
                    "down": {"uv": [5, 5, 11, 11], "texture": "#base"},
                    "north": {"uv": [5, 1, 11, 4], "texture": "#side"},
                    "south": {"uv": [5, 1, 11, 4], "texture": "#side"},
                    "west": {"uv": [5, 1, 11, 4], "texture": "#side"},
                    "east": {"uv": [5, 1, 11, 4], "texture": "#side"}
                }
            }
        ]
    }

    write_json(os.path.join(ASSETS, 'models/block/sprinkler.json'), model)

    # Blockstate: faces player, water_powered has subtle client effects
    variants = {}
    for facing, y in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
        variants[f"facing={facing},water_powered=false"] = {
            "model": "holeydigsite:block/sprinkler", "y": y
        } if y else {"model": "holeydigsite:block/sprinkler"}
        variants[f"facing={facing},water_powered=true"] = {
            "model": "holeydigsite:block/sprinkler", "y": y
        } if y else {"model": "holeydigsite:block/sprinkler"}

    write_json(os.path.join(ASSETS, 'blockstates/sprinkler.json'), {"variants": variants})

    # Item model
    write_json(os.path.join(ASSETS, 'models/item/sprinkler.json'), {
        "parent": "holeydigsite:block/sprinkler",
        "display": {
            "gui": {
                "rotation": [30, 225, 0],
                "translation": [0, 2.5, 0],
                "scale": [1.1, 1.1, 1.1]
            },
            "ground": {
                "rotation": [0, 0, 0],
                "translation": [0, 3, 0],
                "scale": [0.65, 0.65, 0.65]
            },
            "fixed": {
                "rotation": [0, 0, 0],
                "translation": [0, 0, 0],
                "scale": [0.8, 0.8, 0.8]
            },
            "thirdperson_righthand": {
                "rotation": [75, 45, 0],
                "translation": [0, 2.5, 0],
                "scale": [0.55, 0.55, 0.55]
            },
            "firstperson_righthand": {
                "rotation": [0, 45, 0],
                "translation": [0, 2.5, 0],
                "scale": [0.65, 0.65, 0.65]
            }
        }
    })

    # Block loot table: drops sprinkler when broken
    write_json(os.path.join(DATA, 'loot_table/blocks/sprinkler.json'), {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [
                    {
                        "type": "minecraft:item",
                        "name": "holeydigsite:sprinkler"
                    }
                ],
                "conditions": [
                    {
                        "condition": "minecraft:survives_explosion"
                    }
                ]
            }
        ],
        "random_sequence": "holeydigsite:blocks/sprinkler"
    })


# ------------------------------------------------------------------------------------------------
# 3. Flood Gate Textures & Models
# ------------------------------------------------------------------------------------------------
def make_flood_gate():
    iron = hexc('#4A4D53')
    iron_l = hexc('#6A6E77')
    iron_d = hexc('#303236')
    iron_darkest = hexc('#202124')
    rust = hexc('#6B4632')
    brass = hexc('#C9962B')
    brass_l = hexc('#E8B84B')
    brass_d = hexc('#7A5714')
    water = hexc('#3C8CD8')
    water_foam = hexc('#A5D6F8')
    water_d = hexc('#1E5894')

    # 1. Front (Closed)
    front_closed = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            # Outer heavy frame (2px border)
            if x < 2 or x >= 14 or y < 2 or y >= 14:
                c = iron_d
                if (x in (2, 13) and y in (2, 13)) or (x in (1, 14) and y in (1, 14)):
                    c = rust
                elif x == 0 or y == 0:
                    c = iron_l
                elif x == 15 or y == 15:
                    c = iron_darkest
            elif y in (2, 3):
                # Top header beam with brass latch
                c = brass if x in (7, 8) else iron_l
            else:
                # Sluice gate door panels (vertical bars / slats with rivets)
                if x in (4, 7, 10):
                    c = iron_darkest
                elif x in (5, 8, 11):
                    c = iron_l if y % 4 == 0 else iron
                elif (x + y) % 5 == 0:
                    c = rust
                else:
                    c = iron
                # Central horizontal reinforcing strap
                if y in (8, 9):
                    c = iron_l if (x in (3, 6, 9, 12)) else iron_d
                # Bottom seal edge
                if y == 13:
                    c = iron_darkest
            front_closed.putpixel((x, y), c)
    save(front_closed, os.path.join(BLOCK_TEX, 'flood_gate_front.png'))

    # 2. Front (Open / Washing)
    front_open = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if x < 2 or x >= 14 or y < 2 or y >= 14:
                c = iron_d
                if x == 0 or y == 0:
                    c = iron_l
                elif x == 15 or y == 15:
                    c = iron_darkest
            elif y in (2, 3, 4):
                # Gate is raised into upper recess
                c = iron_l if x in (5, 8, 11) else iron
                if y == 2 and x in (7, 8):
                    c = brass_l
            else:
                # Open water chute with churning water & foam
                dx = (x + y * 2) % 6
                if dx == 0 or y == 13:
                    c = water_foam
                elif dx < 3:
                    c = water
                else:
                    c = water_d
            front_open.putpixel((x, y), c)
    save(front_open, os.path.join(BLOCK_TEX, 'flood_gate_front_open.png'))

    # 3. Side (Iron casing with side water pipe connection port)
    side = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            # Frame border
            if x == 0 or y == 0:
                c = iron_l
            elif x == 15 or y == 15:
                c = iron_darkest
            elif (x in (2, 13) and y in (2, 13)):
                c = brass_d  # corner bolts
            else:
                c = iron
                if (x + y * 3) % 8 == 0:
                    c = rust
            # Center circular pipe port (radius ~3.5 centered at 8, 8)
            dx = x - 7.5
            dy = y - 7.5
            r2 = dx * dx + dy * dy
            if r2 < 6:
                c = iron_darkest  # hole interior
            elif r2 < 14:
                c = brass if (x + y) % 2 == 0 else iron_l  # collar rim
            elif r2 < 20:
                c = iron_d  # flange ring
            side.putpixel((x, y), c)
    save(side, os.path.join(BLOCK_TEX, 'flood_gate_side.png'))

    # 4. Top (Heavy plate with gate winch gear / latch)
    top = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0:
                c = iron_l
            elif x == 15 or y == 15:
                c = iron_darkest
            else:
                c = iron
                if (x * 2 + y) % 7 == 0:
                    c = rust
            # Center winch gear
            dx = x - 7.5
            dy = y - 7.5
            r2 = dx * dx + dy * dy
            if r2 < 4:
                c = brass_l
            elif r2 < 12:
                c = brass if (x == 7 or x == 8 or y == 7 or y == 8) else iron_darkest
            elif r2 < 18:
                c = brass_d
            top.putpixel((x, y), c)
    save(top, os.path.join(BLOCK_TEX, 'flood_gate_top.png'))

    # 5. Bottom (Heavy plate with bottom intake port)
    bottom = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0:
                c = iron_l
            elif x == 15 or y == 15:
                c = iron_darkest
            else:
                c = iron
            dx = x - 7.5
            dy = y - 7.5
            r2 = dx * dx + dy * dy
            if r2 < 8:
                c = iron_darkest
            elif r2 < 16:
                c = iron_l
            bottom.putpixel((x, y), c)
    save(bottom, os.path.join(BLOCK_TEX, 'flood_gate_bottom.png'))

    # Block model: closed gate
    write_json(os.path.join(ASSETS, 'models/block/flood_gate.json'), {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": "holeydigsite:block/flood_gate_front",
            "north": "holeydigsite:block/flood_gate_front",
            "south": "holeydigsite:block/flood_gate_side",
            "east": "holeydigsite:block/flood_gate_side",
            "west": "holeydigsite:block/flood_gate_side",
            "up": "holeydigsite:block/flood_gate_top",
            "down": "holeydigsite:block/flood_gate_bottom"
        }
    })

    # Block model: open gate
    write_json(os.path.join(ASSETS, 'models/block/flood_gate_open.json'), {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": "holeydigsite:block/flood_gate_front_open",
            "north": "holeydigsite:block/flood_gate_front_open",
            "south": "holeydigsite:block/flood_gate_side",
            "east": "holeydigsite:block/flood_gate_side",
            "west": "holeydigsite:block/flood_gate_side",
            "up": "holeydigsite:block/flood_gate_top",
            "down": "holeydigsite:block/flood_gate_bottom"
        }
    })

    # Blockstate
    variants = {}
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for facing, y in rot.items():
        for powered in ('false', 'true'):
            # Closed variant
            key_closed = f"facing={facing},washing=false,water_powered={powered}"
            variants[key_closed] = {"model": "holeydigsite:block/flood_gate", "y": y} if y else {"model": "holeydigsite:block/flood_gate"}
            # Open variant
            key_open = f"facing={facing},washing=true,water_powered={powered}"
            variants[key_open] = {"model": "holeydigsite:block/flood_gate_open", "y": y} if y else {"model": "holeydigsite:block/flood_gate_open"}

    write_json(os.path.join(ASSETS, 'blockstates/flood_gate.json'), {"variants": variants})

    # Item model
    write_json(os.path.join(ASSETS, 'models/item/flood_gate.json'), {
        "parent": "holeydigsite:block/flood_gate"
    })

    # Block loot table
    write_json(os.path.join(DATA, 'loot_table/blocks/flood_gate.json'), {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [
                    {
                        "type": "minecraft:item",
                        "name": "holeydigsite:flood_gate"
                    }
                ],
                "conditions": [
                    {
                        "condition": "minecraft:survives_explosion"
                    }
                ]
            }
        ],
        "random_sequence": "holeydigsite:blocks/flood_gate"
    })


# ------------------------------------------------------------------------------------------------
# 4. Preview Sheet
# ------------------------------------------------------------------------------------------------
def make_preview():
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    width = 540
    height = 200
    sheet = Image.new('RGB', (width, height), (32, 34, 40))
    draw = ImageDraw.Draw(sheet)

    # Title
    draw.text((15, 12), "Phase 35: Farming Tools (Scythe, Sprinkler, Flood Gate)", fill=(240, 240, 245))

    # Panel 1: Scythe
    draw.rectangle([15, 38, 175, 180], fill=(45, 48, 56), outline=(70, 75, 86))
    draw.text((25, 45), "Scythe", fill=(220, 220, 225))
    draw.text((25, 60), "4 Gold (256 copper)", fill=(180, 180, 190))
    draw.text((25, 75), "3x3 Harvest & Replant", fill=(140, 190, 140))

    scythe_img = Image.open(os.path.join(ITEM_TEX, 'scythe.png')).convert('RGBA')
    scythe_big = scythe_img.resize((64, 64), Image.NEAREST)
    sheet.paste(scythe_big, (60, 100), scythe_big)

    # Panel 2: Sprinkler
    draw.rectangle([190, 38, 350, 180], fill=(45, 48, 56), outline=(70, 75, 86))
    draw.text((200, 45), "Sprinkler", fill=(220, 220, 225))
    draw.text((200, 60), "12 Gold (768 copper)", fill=(180, 180, 190))
    draw.text((200, 75), "9x9 Wet & 1.5x Speed", fill=(100, 180, 240))

    top_img = Image.open(os.path.join(BLOCK_TEX, 'sprinkler_top.png')).convert('RGBA')
    top_big = top_img.resize((48, 48), Image.NEAREST)
    sheet.paste(top_big, (215, 105), top_big)

    side_img = Image.open(os.path.join(BLOCK_TEX, 'sprinkler_side.png')).convert('RGBA')
    side_big = side_img.resize((32, 32), Image.NEAREST)
    sheet.paste(side_big, (285, 115), side_big)

    # Panel 3: Flood Gate
    draw.rectangle([365, 38, 525, 180], fill=(45, 48, 56), outline=(70, 75, 86))
    draw.text((375, 45), "Flood Gate", fill=(220, 220, 225))
    draw.text((375, 60), "16 Gold (1024 copper)", fill=(180, 180, 190))
    draw.text((375, 75), "Row Wash & Belt Output", fill=(100, 210, 210))

    fg_closed = Image.open(os.path.join(BLOCK_TEX, 'flood_gate_front.png')).convert('RGBA')
    fg_closed_big = fg_closed.resize((48, 48), Image.NEAREST)
    sheet.paste(fg_closed_big, (385, 105), fg_closed_big)

    fg_open = Image.open(os.path.join(BLOCK_TEX, 'flood_gate_front_open.png')).convert('RGBA')
    fg_open_big = fg_open.resize((48, 48), Image.NEAREST)
    sheet.paste(fg_open_big, (455, 105), fg_open_big)

    preview_path = os.path.join(PREVIEW_DIR, 'farming_tools.png')
    save(sheet, preview_path)
    print("Saved preview to", preview_path)


if __name__ == '__main__':
    make_scythe()
    make_sprinkler()
    make_flood_gate()
    make_preview()
    print("Phase 35 assets generated successfully.")
