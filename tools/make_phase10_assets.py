"""Generates the Phase 10 textures AND model / blockstate JSON (deterministic; needs Pillow).

    python tools/make_phase10_assets.py

Crafting Forge (dark iron anvil-forge with a glowing basin and a cast handle), and the Tier 2 versions, derived from the
Tier 1 textures so they clearly belong together but look upgraded: dark steel / walnut bodies, Goldchro trim and a "II"
mark. Tier 2 Mining Drill, Tier 2 Sluice Box, Large Pan (8 fill levels x 3 dirt tiers), Goldchro Brush, Fast Clump Belt.
Reuses the helpers of make_phase9_assets.py (box / face / block_model / glow).
"""
import colorsys
import json
import os
import random
import shutil

from PIL import Image

import make_phase9_assets as p9
from make_phase9_assets import BLOCK, ITEM, MODELS, STATES, GLOW, box, block_model, item_parent, write_json, rgba, new, save

GOLD = [(150, 104, 16), (196, 146, 30), (232, 190, 60), (255, 230, 120)]     # Goldchro trim, dark..bright
STEEL = [(44, 50, 60), (58, 66, 78), (74, 84, 98), (92, 104, 120)]          # Tier 2 drill body
WALNUT = [(52, 34, 22), (66, 44, 28), (80, 54, 34), (96, 66, 42)]          # Tier 2 sluice wood
IRON = p9.IRON
LAVA = p9.LAVA


def load(folder, name):
    return Image.open(os.path.join(folder, name + '.png')).convert('RGBA')


def recolor(img, test, palette):
    """Maps every pixel for which test(h, s, v) is true onto `palette` by brightness."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if test(h * 360, s, v):
                i = min(len(palette) - 1, int(v * len(palette) * 0.999 * 1.15))
                px[x, y] = rgba(palette[min(i, len(palette) - 1)], a)
    return out


def gold_border(img, frames=1):
    """A 1-pixel Goldchro frame around each 16x16 frame (corners darker, like rivets)."""
    px = img.load()
    for f in range(frames):
        o = f * 16
        for i in range(16):
            for (x, y) in ((i, o), (i, o + 15), (0, o + i), (15, o + i)):
                if px[x, y][3]:
                    px[x, y] = rgba(GOLD[2] if (i % 4) else GOLD[1])
        for (x, y) in ((0, o), (15, o), (0, o + 15), (15, o + 15)):
            px[x, y] = rgba(GOLD[0])


def mark_ii(img, x, y, color=GOLD[3], shadow=GOLD[0], h=5):
    """A small Roman "II" (two bars with a dark drop shadow) at (x, y) (top-left), 4 px wide."""
    px = img.load()
    for dx in (0, 2):
        for dy in range(h):
            px[x + dx + 1, y + dy + 1] = rgba(shadow)
        for dy in range(h):
            px[x + dx, y + dy] = rgba(color)


def copy_json(src_folder, src, dst, replace):
    with open(os.path.join(src_folder, src + '.json')) as f:
        s = f.read()
    for a, b in replace:
        s = s.replace(a, b)
    with open(os.path.join(src_folder, dst + '.json'), 'w') as f:
        f.write(s)


def orange(h, s, v):
    return 15 <= h <= 55 and s > 0.35 and v > 0.25


def woody(h, s, v):
    return 10 <= h <= 45 and s > 0.2 and v < 0.75


# ------------------------------------------------------------------------------------------------ Tier 2 drill
def drill_t2():
    names = ['back', 'bottom', 'front', 'side_no_power', 'side_stopped', 'side_working', 'top']
    for n in names:
        img = recolor(load(BLOCK, 'mining_drill_' + n), orange, STEEL)
        gold_border(img)
        if n.startswith('side') or n == 'top':
            mark_ii(img, 2, 2)
        save(img, BLOCK, 'mining_drill_t2_' + n)
    img = recolor(load(BLOCK, 'mining_drill_front_working'), orange, STEEL)
    gold_border(img, frames=img.height // 16)
    img.save(os.path.join(BLOCK, 'mining_drill_t2_front_working.png'))
    # spins twice as fast
    with open(os.path.join(BLOCK, 'mining_drill_t2_front_working.png.mcmeta'), 'w') as f:
        json.dump({"animation": {"frametime": 1}}, f)
    for status in ('working', 'stopped', 'no_power'):
        copy_json(os.path.join(MODELS, 'block'), 'mining_drill_' + status, 'mining_drill_t2_' + status,
                  [('block/mining_drill_', 'block/mining_drill_t2_')])
    copy_json(STATES, 'mining_drill', 'mining_drill_t2', [('block/mining_drill_', 'block/mining_drill_t2_')])
    item_parent('mining_drill_t2', 'holeydigsite:block/mining_drill_t2_working')


# ------------------------------------------------------------------------------------------------ Tier 2 sluice
def sluice_t2():
    for n in ('wood', 'back', 'port'):
        img = recolor(load(BLOCK, 'sluice_' + n), woody, WALNUT)
        gold_border(img)
        if n == 'back':
            mark_ii(img, 9, 9)
        save(img, BLOCK, 'sluice_t2_' + n)
    for status in ('working', 'idle', 'stopped', 'no_power'):
        copy_json(os.path.join(MODELS, 'block'), 'sluice_box_' + status, 'sluice_box_t2_' + status,
                  [('block/sluice_wood', 'block/sluice_t2_wood'), ('block/sluice_back', 'block/sluice_t2_back'),
                   ('block/sluice_port', 'block/sluice_t2_port')])
    copy_json(STATES, 'sluice_box', 'sluice_box_t2', [('block/sluice_box_working', 'block/sluice_box_t2_working'),
                                                    ('block/sluice_box_idle', 'block/sluice_box_t2_idle'),
                                                    ('block/sluice_box_stopped', 'block/sluice_box_t2_stopped'),
                                                    ('block/sluice_box_no_power', 'block/sluice_box_t2_no_power')])
    item_parent('sluice_box_t2', 'holeydigsite:block/sluice_box_t2_idle')


# ------------------------------------------------------------------------------------------------ Large Pan
def large_pan():
    # a Goldchro trim texture for the rim top
    rnd = random.Random(10001)
    trim = new()
    p9.noise_fill(trim, GOLD[1:], rnd)
    save(trim, BLOCK, 'large_pan_trim')
    tex = {"particle": "holeydigsite:block/pan", "pan": "holeydigsite:block/pan", "trim": "holeydigsite:block/large_pan_trim"}

    def pan_elements():
        return [
            box((0, 0, 0), (16, 1, 16), '#pan'),
            box((0, 1, 0), (16, 6, 1), '#pan', up='#trim'),
            box((0, 1, 15), (16, 6, 16), '#pan', up='#trim'),
            box((0, 1, 1), (1, 6, 15), '#pan', up='#trim'),
            box((15, 1, 1), (16, 6, 15), '#pan', up='#trim'),
            box((6, 5.99, -0.01), (10, 6.01, 1.01), '#trim', faces='nu'),  # trim tab on the front rim ("II" side)
        ]
    block_model('large_pan', tex, pan_elements())
    # Phase 11: kinds 4 (gravel) and 5 (sand) use the vanilla gravel / sand textures
    dirt = {1: 'minecraft:block/dirt', 2: 'holeydigsite:block/pan_dirt_deep', 3: 'holeydigsite:block/pan_dirt_abyssal',
            4: 'minecraft:block/gravel', 5: 'minecraft:block/sand'}
    suffix = {1: '', 2: '_deep', 3: '_abyssal', 4: '_gravel', 5: '_sand'}
    for fill in range(1, 9):
        top = 1 + fill * 0.6
        for tier in (1, 2, 3, 4, 5):
            # a child model's "elements" REPLACE the parent's, so every fill model carries the whole pan + its dirt layer
            block_model(f'large_pan_fill_{fill}{suffix[tier]}', dict(tex, dirt=dirt[tier]),
                        pan_elements() + [box((1, 1, 1), (15, top, 15), '#dirt', faces='nsewu')])
    variants = {}
    for fill in range(0, 9):
        for tier in (1, 2, 3, 4, 5):
            variants[f"fill={fill},tier={tier}"] = {"model": "holeydigsite:block/" + (
                'large_pan' if fill == 0 else f'large_pan_fill_{fill}{suffix[tier]}')}
    # waterlogged is not in the variant key: every waterlogged state uses the same model
    write_json(STATES, 'large_pan', {"variants": variants})
    overrides = []
    for fill in range(1, 9):
        for tier in (1, 2, 3, 4, 5):
            pred = {"holeydigsite:fill": round(fill / 8, 4)}
            if tier > 1:
                pred["holeydigsite:tier"] = tier
            overrides.append({"predicate": pred, "model": f"holeydigsite:block/large_pan_fill_{fill}{suffix[tier]}"})
    write_json(os.path.join(MODELS, 'item'), 'large_pan', {"parent": "holeydigsite:block/large_pan", "overrides": overrides})


# ------------------------------------------------------------------------------------------------ Goldchro Brush
def goldchro_brush():
    img = load(ITEM, 'brush')
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if not a:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            h *= 360
            if (h < 15 or h > 330) and s > 0.4:            # red band -> Goldchro
                px[x, y] = rgba(GOLD[min(3, int(v * 4.2))], a)
            elif 15 <= h <= 50 and s > 0.25:               # wood handle -> dark walnut, bristles -> pale gold
                px[x, y] = rgba(WALNUT[min(3, int(v * 4.5))] if v < 0.6 else GOLD[3], a)
    save(img, ITEM, 'goldchro_brush')
    write_json(os.path.join(MODELS, 'item'), 'goldchro_brush',
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "holeydigsite:item/goldchro_brush"}})


# ------------------------------------------------------------------------------------------------ Fast Clump Belt
def fast_belt():
    for n in ('side', 'end', 'bottom', 'top'):
        img = load(BLOCK, 'clump_belt_' + n)
        if n == 'side':
            img = recolor(img, lambda h, s, v: True, STEEL)
            px = img.load()
            for x in range(16):
                for y in range(16):
                    if px[x, y][3] and y in (12, 13):
                        px[x, y] = rgba(GOLD[2] if x % 4 else GOLD[1])
        elif n == 'top':
            px = img.load()
            for y in range(16):
                for x in (0, 1, 14, 15):
                    if px[x, y][3]:
                        px[x, y] = rgba(GOLD[2] if y % 4 else GOLD[1])
        save(img, BLOCK, 'fast_clump_belt_' + n)
    img = load(BLOCK, 'clump_belt_top_moving')
    px = img.load()
    for y in range(img.height):
        for x in (0, 1, 14, 15):
            if px[x, y][3]:
                px[x, y] = rgba(GOLD[2] if (y % 16) % 4 else GOLD[1])
    img.save(os.path.join(BLOCK, 'fast_clump_belt_top_moving.png'))
    # every frame 1 tick (the normal belt holds the last frame 2 ticks): visibly faster
    with open(os.path.join(BLOCK, 'fast_clump_belt_top_moving.png.mcmeta'), 'w') as f:
        json.dump({"animation": {"frametime": 1}}, f)
    for m in ('clump_belt', 'clump_belt_moving'):
        copy_json(os.path.join(MODELS, 'block'), m, 'fast_' + m, [('block/clump_belt_', 'block/fast_clump_belt_')])
    copy_json(STATES, 'clump_belt', 'fast_clump_belt', [('block/clump_belt"', 'block/fast_clump_belt"'),
                                                        ('block/clump_belt_moving', 'block/fast_clump_belt_moving')])
    item_parent('fast_clump_belt', 'holeydigsite:block/fast_clump_belt')


# ------------------------------------------------------------------------------------------------ Crafting Forge
def crafting_forge_textures():
    rnd = random.Random(10010)
    # body: dark iron plates with rivets and a Goldchro band
    side = new()
    p9.noise_fill(side, IRON, rnd)
    for i in range(16):
        side.putpixel((i, 0), rgba(IRON[0])); side.putpixel((i, 15), rgba(IRON[0]))
        side.putpixel((0, i), rgba(IRON[0])); side.putpixel((15, i), rgba(IRON[0]))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        side.putpixel((x, y), rgba(IRON[3]))
    for x in range(1, 15):
        side.putpixel((x, 7), rgba(GOLD[1])); side.putpixel((x, 8), rgba(GOLD[2]))
    save(side, BLOCK, 'crafting_forge_side')
    # top: iron rim around a basin; unlit = dark slag, lit = glowing (animated) lava
    for name, lit in (('crafting_forge_top', False), ('crafting_forge_top_lit', True)):
        frames = 4 if lit else 1
        img = new(16 * frames)
        for f in range(frames):
            fr = random.Random(10020 + f)
            for y in range(16):
                for x in range(16):
                    if x < 2 or x > 13 or y < 2 or y > 13:
                        c = IRON[fr.randrange(1, 4)] if not (x in (0, 15) or y in (0, 15)) else IRON[0]
                    elif x in (2, 13) or y in (2, 13):
                        c = GOLD[1]
                    elif lit:
                        c = LAVA[fr.choices(range(5), weights=(1, 2, 3, 3, 1))[0]]
                    else:
                        c = (30, 26, 26) if fr.random() < 0.7 else (46, 38, 36)
                    img.putpixel((x, y + 16 * f), rgba(c))
        save(img, BLOCK, name, frametime=6 if lit else None, interpolate=lit)
    # front: plain iron with a "II"-less slot (the handle sits in front of it)
    front = side.copy()
    px = front.load()
    for y in range(3, 13):
        for x in range(6, 10):
            px[x, y] = rgba((24, 22, 24))
    save(front, BLOCK, 'crafting_forge_front')
    # handle: iron rod + red grip
    h = new()
    for y in range(16):
        for x in range(16):
            h.putpixel((x, y), rgba(IRON[2] if (x + y) % 3 else IRON[3]))
    for y in range(0, 5):
        for x in range(16):
            h.putpixel((x, y), rgba((150, 30, 30) if (x + y) % 3 else (180, 50, 40)))
    save(h, BLOCK, 'crafting_forge_handle')


def crafting_forge_models():
    tex = {"particle": "holeydigsite:block/crafting_forge_side", "side": "holeydigsite:block/crafting_forge_side",
           "front": "holeydigsite:block/crafting_forge_front"}
    for name, lit in (('crafting_forge', False), ('crafting_forge_lit', True)):
        t = dict(tex)
        t["top"] = "holeydigsite:block/" + ('crafting_forge_top_lit' if lit else 'crafting_forge_top')
        top = box((0, 10, 3), (16, 16, 16), '#side', north='#front', up='#top')
        if lit:
            top['faces']['up']['neoforge_data'] = dict(GLOW)
        block_model(name, t, [
            box((1, 0, 3), (15, 4, 15), '#side'),
            box((3, 4, 4), (13, 10, 14), '#side', north='#front'),
            top,
        ])
    ht = {"particle": "holeydigsite:block/crafting_forge_handle", "h": "holeydigsite:block/crafting_forge_handle",
          "side": "holeydigsite:block/crafting_forge_side"}
    pivot = {"origin": [8, 5, 3], "axis": "x", "angle": 0}

    def handle(angle):
        rot = dict(pivot, angle=angle) if angle else None
        return [
            box((6, 4, 2), (10, 6, 4), '#side', rotation=rot),        # hinge block on the front
            box((7.5, 5, 1.5), (8.5, 12, 2.5), '#h', rotation=rot),   # rod
            box((6.5, 12, 1), (9.5, 13.5, 3), '#h', rotation=rot),    # red grip
        ]
    block_model('crafting_forge_handle', ht, handle(0))
    # pulled: the grip swings DOWN towards the player (front = north = -z; a negative x angle tips the top north)
    block_model('crafting_forge_handle_down', ht, handle(-45))
    parts = []
    for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for lit, model in (('false', 'crafting_forge'), ('true', 'crafting_forge_lit')):
            a = {"model": "holeydigsite:block/" + model}
            if y:
                a["y"] = y
            parts.append({"when": {"facing": d, "lit": lit}, "apply": a})
        for casting, model in (('false', 'crafting_forge_handle'), ('true', 'crafting_forge_handle_down')):
            a = {"model": "holeydigsite:block/" + model}
            if y:
                a["y"] = y
            parts.append({"when": {"facing": d, "casting": casting}, "apply": a})
    write_json(STATES, 'crafting_forge', {"multipart": parts})
    # item: body + handle in one model
    write_json(os.path.join(MODELS, 'item'), 'crafting_forge', {
        "parent": "holeydigsite:block/crafting_forge_lit",
        "textures": {"h": "holeydigsite:block/crafting_forge_handle"},
        "elements": json.load(open(os.path.join(MODELS, 'block', 'crafting_forge_lit.json')))["elements"]
                    + json.load(open(os.path.join(MODELS, 'block', 'crafting_forge_handle.json')))["elements"]})


if __name__ == '__main__':
    drill_t2()
    sluice_t2()
    large_pan()
    goldchro_brush()
    fast_belt()
    crafting_forge_textures()
    crafting_forge_models()
    print('phase 10 assets written')
