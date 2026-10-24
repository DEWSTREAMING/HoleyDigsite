"""Generates the Phase 11 textures AND model / blockstate JSON (deterministic; needs Pillow).

    python tools/make_phase11_assets.py

Gravelrock / Sandrock (from the Dirtrock texture), Gravel / Sand Clumps (from the Dirt Clump), Orchremia (rust/ochre, from the
Folkiron chunks) and Silvane (silvery white, from the Goldchro chunks) chunks, Sapphire / Topaz (from the Rubies), Land Deeds,
the Plot Board, the Tier 3 Mining Drill, full shovels of gravel / sand, and the pan models + blockstate for the gravel / sand
kinds (pan.json is rewritten for all 5 kinds). Molten metal / ingots of the new metals: tools/make_phase9_assets.py (METALS) -
regenerate only the files it adds (it is not byte-identical for older files and still writes the pre-9.1 lava intake state).
Large Pan gravel/sand models: tools/make_phase10_assets.py.
"""
import colorsys
import json
import os

from PIL import Image

import make_phase9_assets as p9
from make_phase9_assets import BLOCK, ITEM, MODELS, STATES, box, block_model, item_parent, write_json, rgba, new, save
from make_phase10_assets import load, recolor, gold_border, copy_json, orange

GRAVEL = [(70, 70, 72), (96, 94, 92), (122, 118, 114), (150, 146, 140)]
SAND = [(176, 152, 104), (204, 182, 130), (222, 204, 152), (240, 226, 180)]
ORCH = [(96, 40, 20), (140, 62, 28), (184, 96, 40), (220, 146, 74)]          # rust / ochre with a light sheen
SILV = [(150, 158, 170), (188, 196, 208), (220, 228, 238), (250, 252, 255)]   # silvery white
SAPPHIRE_HUE, TOPAZ_HUE = 222, 38


def by_value(img, palette, test=lambda h, s, v, a: a > 0):
    """Maps pixels passing `test` onto `palette` by brightness (keeps the shading)."""
    out = img.copy()
    px = out.load()
    vals = [colorsys.rgb_to_hsv(*(c / 255 for c in px[x, y][:3]))[2] for y in range(out.height) for x in range(out.width) if px[x, y][3]]
    lo, hi = (min(vals), max(vals)) if vals else (0, 1)
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if test(h * 360, s, v, a):
                t = 0 if hi <= lo else (v - lo) / (hi - lo)
                px[x, y] = rgba(palette[min(len(palette) - 1, int(t * len(palette)))], a)
    return out


def hue_shift(img, hue, sat_mul=1.0, val_mul=1.0):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if not a:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.25:
                r2, g2, b2 = colorsys.hsv_to_rgb(hue / 360, min(1, s * sat_mul), min(1, v * val_mul))
                px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    return out


def cube(name, tex):
    write_json(os.path.join(MODELS, 'block'), name, {"parent": "minecraft:block/cube_all", "textures": {"all": "holeydigsite:block/" + tex}})
    write_json(STATES, name, {"variants": {"": {"model": "holeydigsite:block/" + name}}})
    item_parent(name, "holeydigsite:block/" + name)


def generated(name):
    write_json(os.path.join(MODELS, 'item'), name, {"parent": "minecraft:item/generated", "textures": {"layer0": "holeydigsite:item/" + name}})


# ------------------------------------------------------------------------------------------------ rocks + clumps
def rocks_and_clumps():
    rock = load(BLOCK, 'dirtrock')
    save(by_value(rock, GRAVEL + [(176, 172, 166)]), BLOCK, 'gravelrock')
    save(by_value(rock, [(150, 120, 74)] + SAND), BLOCK, 'sandrock')
    cube('gravelrock', 'gravelrock')
    cube('sandrock', 'sandrock')
    clump = load(ITEM, 'dirt_clump')
    save(by_value(clump, GRAVEL), ITEM, 'gravel_clump')
    save(by_value(clump, SAND), ITEM, 'sand_clump')
    generated('gravel_clump')
    generated('sand_clump')


# ------------------------------------------------------------------------------------------------ resources
def resources():
    for size in ('small', 'medium', 'large'):
        save(by_value(load(ITEM, f'folkiron_chunk_{size}'), ORCH), ITEM, f'orchremia_chunk_{size}')
        save(by_value(load(ITEM, f'goldchro_chunk_{size}'), SILV), ITEM, f'silvane_chunk_{size}')
        save(hue_shift(load(ITEM, f'ruby_{size}'), SAPPHIRE_HUE, 1.0, 1.0), ITEM, f'sapphire_{size}')
        save(hue_shift(load(ITEM, f'ruby_{size}'), TOPAZ_HUE, 1.0, 1.15), ITEM, f'topaz_{size}')
        for n in (f'orchremia_chunk_{size}', f'silvane_chunk_{size}', f'sapphire_{size}', f'topaz_{size}'):
            generated(n)


# ------------------------------------------------------------------------------------------------ deeds
def deeds():
    shape = [
        '................',
        '..pppppppppppp..',
        '.pPPPPPPPPPPPPp.',
        '.pPPllllllllPPp.',
        '.pPPPPPPPPPPPPp.',
        '.pPPllllllPPPPp.',
        '.pPPPPPPPPPPPPp.',
        '.pPPlllllllPPPp.',
        '.pPPPPPPPPPPPPp.',
        '.pPPPPPPPPsssPp.',
        '.pPPllllPsSSSsp.',
        '.pPPPPPPPsSSSsp.',
        '.pPPPPPPPPsssPp.',
        '..pppppppppprr..',
        '............rr..',
        '................',
    ]
    seals = {'deep_dig': ((120, 70, 40), (170, 110, 60)), 'gravel_flats': ((90, 96, 104), (150, 156, 166)),
             'sand_dunes': ((196, 150, 40), (240, 200, 90))}
    for plot, (dark, light) in seals.items():
        img = new()
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                c = {'p': (150, 130, 96), 'P': (232, 220, 186), 'l': (120, 104, 80), 's': dark, 'S': light,
                     'r': (170, 30, 30)}.get(ch)
                if c:
                    img.putpixel((x, y), rgba(c))
        save(img, ITEM, 'land_deed_' + plot)
        generated('land_deed_' + plot)


# ------------------------------------------------------------------------------------------------ plot board
def plot_board():
    for name, strip, text in (('plot_board_for_sale', (170, 36, 36), (250, 236, 210)), ('plot_board_owned', (40, 140, 60), (230, 250, 230))):
        img = new()
        for y in range(16):
            for x in range(16):
                wood = (150, 112, 66) if (y // 3 + x // 7) % 2 else (136, 100, 58)
                c = wood
                if y in (0, 15) or x in (0, 15):
                    c = (96, 68, 40)
                if 5 <= y <= 10 and 1 <= x <= 14:
                    c = strip
                    if y in (7, 8) and x % 2 == 0 and 2 <= x <= 13:
                        c = text
                img.putpixel((x, y), rgba(c))
        save(img, BLOCK, name)
    for owned, tex in (('false', 'plot_board_for_sale'), ('true', 'plot_board_owned')):
        t = {"particle": "minecraft:block/oak_planks", "post": "minecraft:block/stripped_oak_log", "board": "holeydigsite:block/" + tex,
             "edge": "minecraft:block/oak_planks"}
        block_model('plot_board_' + ('owned' if owned == 'true' else 'for_sale'), t, [
            box((7, 0, 7), (9, 10, 9), '#post'),
            box((1, 8, 6.5), (15, 16, 9.5), '#edge', north='#board', south='#board'),
        ])
    variants = {}
    for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for owned in ('false', 'true'):
            v = {"model": "holeydigsite:block/plot_board_" + ('owned' if owned == 'true' else 'for_sale')}
            if y:
                v["y"] = y
            variants[f"facing={d},owned={owned}"] = v
    write_json(STATES, 'plot_board', {"variants": variants})
    item_parent('plot_board', 'holeydigsite:block/plot_board_for_sale')


# ------------------------------------------------------------------------------------------------ Tier 3 drill
def mark_iii(img, x, y, color, shadow, h=5):
    px = img.load()
    for dx in (0, 2, 4):
        for dy in range(h):
            px[x + dx + 1, y + dy + 1] = rgba(shadow)
        for dy in range(h):
            px[x + dx, y + dy] = rgba(color)


def trim(img, frames=1):
    px = img.load()
    for f in range(frames):
        o = f * 16
        for i in range(16):
            for (x, y) in ((i, o), (i, o + 15), (0, o + i), (15, o + i)):
                if px[x, y][3]:
                    px[x, y] = rgba(ORCH[2] if (i % 4) else ORCH[1])


def drill_t3():
    for n in ('back', 'bottom', 'front', 'side_no_power', 'side_stopped', 'side_working', 'top'):
        img = recolor(load(BLOCK, 'mining_drill_' + n), orange, SILV)
        trim(img)
        if n.startswith('side') or n == 'top':
            mark_iii(img, 2, 2, ORCH[3], ORCH[0])
        save(img, BLOCK, 'mining_drill_t3_' + n)
    img = recolor(load(BLOCK, 'mining_drill_front_working'), orange, SILV)
    trim(img, frames=img.height // 16)
    img.save(os.path.join(BLOCK, 'mining_drill_t3_front_working.png'))
    with open(os.path.join(BLOCK, 'mining_drill_t3_front_working.png.mcmeta'), 'w') as f:
        json.dump({"animation": {"frametime": 1}}, f)
    for status in ('working', 'stopped', 'no_power'):
        copy_json(os.path.join(MODELS, 'block'), 'mining_drill_' + status, 'mining_drill_t3_' + status,
                  [('block/mining_drill_', 'block/mining_drill_t3_')])
    copy_json(STATES, 'mining_drill', 'mining_drill_t3', [('block/mining_drill_', 'block/mining_drill_t3_')])
    item_parent('mining_drill_t3', 'holeydigsite:block/mining_drill_t3_working')


# ------------------------------------------------------------------------------------------------ shovel + pans
def shovel():
    empty, full = load(ITEM, 'wooden_shovel'), load(ITEM, 'wooden_shovel_full')
    pe, pf = empty.load(), full.load()
    dirt = lambda x, y: pf[x, y][3] and pf[x, y] != pe[x, y]
    for name, pal in (('gravel', GRAVEL), ('sand', SAND)):
        img = full.copy()
        px = img.load()
        sub = by_value(full, pal)
        ps = sub.load()
        for y in range(16):
            for x in range(16):
                if dirt(x, y):
                    px[x, y] = ps[x, y]
        save(img, ITEM, 'wooden_shovel_full_' + name)
        generated('wooden_shovel_full_' + name)
        # it is a handheld item like the others
        write_json(os.path.join(MODELS, 'item'), 'wooden_shovel_full_' + name,
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": "holeydigsite:item/wooden_shovel_full_" + name}})
    mc_models = os.path.join(MODELS, '..', '..', 'minecraft', 'models', 'item')
    path = os.path.join(mc_models, 'wooden_shovel.json')
    m = json.load(open(path))
    m["overrides"] = [
        {"predicate": {"holeydigsite:full": 1}, "model": "holeydigsite:item/wooden_shovel_full"},
        {"predicate": {"holeydigsite:full": 1, "holeydigsite:kind": 4}, "model": "holeydigsite:item/wooden_shovel_full_gravel"},
        {"predicate": {"holeydigsite:full": 1, "holeydigsite:kind": 5}, "model": "holeydigsite:item/wooden_shovel_full_sand"},
    ]
    with open(path, 'w') as f:
        json.dump(m, f, indent=2)
        f.write('\n')


def pans():
    # pan_fill_N_gravel / _sand: copies of pan_fill_N with the vanilla gravel / sand texture
    for fill in range(1, 5):
        for kind, tex in (('gravel', 'minecraft:block/gravel'), ('sand', 'minecraft:block/sand')):
            copy_json(os.path.join(MODELS, 'block'), f'pan_fill_{fill}', f'pan_fill_{fill}_{kind}', [('"minecraft:block/dirt"', f'"{tex}"')])
    suffix = {1: '', 2: '_deep', 3: '_abyssal', 4: '_gravel', 5: '_sand'}
    variants = {}
    for tier in range(1, 6):
        for fill in range(0, 5):
            variants[f"fill={fill},tier={tier}"] = {"model": "holeydigsite:block/" + ('pan' if fill == 0 else f'pan_fill_{fill}{suffix[tier]}')}
    write_json(STATES, 'pan', {"variants": variants})
    overrides = []
    for fill in range(1, 5):
        for tier in range(1, 6):
            pred = {"holeydigsite:fill": fill / 4}
            if tier > 1:
                pred["holeydigsite:tier"] = tier
            overrides.append({"predicate": pred, "model": f"holeydigsite:block/pan_fill_{fill}{suffix[tier]}"})
    write_json(os.path.join(MODELS, 'item'), 'pan', {"parent": "holeydigsite:block/pan", "overrides": overrides})


if __name__ == '__main__':
    rocks_and_clumps()
    resources()
    deeds()
    plot_board()
    drill_t3()
    shovel()
    pans()
    print('phase 11 assets written')
