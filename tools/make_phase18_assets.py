"""Phase 18 assets (deterministic, Pillow): textures for the Seed Screen, the three own crops (seeds, foods, 4 growth stages each),
the Steady Hands effect icon and the sluice overlay for the Seed Screen; adds the `seed` upgrade parts to both sluice blockstates
(idempotent). Models/lang/loot of the crops come from the reviewed local-model files (branch phase18-local).

Run from the project root:  python tools/make_phase18_assets.py
"""
import json
import os
import random
from PIL import Image

ROOT = os.path.join('src', 'main', 'resources', 'assets', 'holeydigsite')
TEX_B = os.path.join(ROOT, 'textures', 'block')
TEX_I = os.path.join(ROOT, 'textures', 'item')
TEX_E = os.path.join(ROOT, 'textures', 'mob_effect')


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def img(w=16, h=16):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def px(im, pts, c):
    for p in pts:
        if 0 <= p[0] < im.width and 0 <= p[1] < im.height:
            im.putpixel(p, c)


def col(r, g, b):
    return (r, g, b, 255)


# palette
LEAF, LEAF_D, LEAF_L = col(74, 150, 54), col(44, 106, 40), col(118, 190, 78)
STEM = col(60, 120, 44)
GLOW, GLOW_D, GLOW_L = col(255, 168, 40), col(214, 110, 24), col(255, 224, 110)
BEAN, BEAN_D, BEAN_L = col(46, 156, 160), col(26, 106, 116), col(120, 220, 214)
MEL, MEL_D, MEL_L = col(226, 196, 84), col(176, 142, 48), col(250, 232, 140)
SEED_C, SEED_D = col(206, 176, 110), col(140, 108, 60)


# ---------------------------------------------------------------- crops (two crossed planes, 16x16)
def stalk(im, x, h, c=STEM):
    for y in range(16 - h, 16):
        im.putpixel((x, y), c)


def leaves(im, x, y, c=LEAF, d=LEAF_D, size=2):
    for i in range(1, size + 1):
        px(im, [(x - i, y - (i - 1)), (x + i, y - (i - 1))], c)
        px(im, [(x - i, y - (i - 1) + 1), (x + i, y - (i - 1) + 1)], d)


def glowroot_stage(n):
    im = img()
    xs = (3, 8, 12)
    heights = (3, 6, 9, 11)
    for x in xs:
        h = heights[n] - (1 if x == 8 else 0) + (1 if x == 8 and n == 3 else 0)
        stalk(im, x, h)
        if n >= 1:
            leaves(im, x, 16 - h + 1, size=1 + (n >= 2))
        if n == 3:
            px(im, [(x - 1, 15), (x, 15), (x + 1, 15)], GLOW)       # glowing roots at the surface
            px(im, [(x, 14), (x - 1, 14), (x + 1, 14)], GLOW_L)
            px(im, [(x - 1, 13), (x + 1, 13)], GLOW_D)
            px(im, [(x, 16 - h)], GLOW_L)
    if n == 2:
        px(im, [(8, 15), (3, 15), (12, 15)], GLOW_D)
    return im


def riverbean_stage(n):
    im = img()
    xs = (2, 7, 12) if n else (4, 11)
    heights = (3, 6, 10, 14)
    for x in xs:
        stalk(im, x, heights[n])
        stalk(im, x + 1, max(2, heights[n] - 2), LEAF_D)
        if n >= 1:
            leaves(im, x, 16 - heights[n] + 2, size=1 + (n >= 2))
        if n >= 2:
            for k in range(2 if n == 2 else 4):                      # pods hang from the vine
                y = 16 - heights[n] + 4 + k * 2
                if y < 15:
                    px(im, [(x + 2, y), (x + 2, y + 1)], BEAN if n == 3 else LEAF_L)
                    if n == 3:
                        px(im, [(x + 3, y)], BEAN_L)
                        px(im, [(x + 3, y + 1)], BEAN_D)
    return im


def dune_melon_stage(n):
    im = img()
    # a vine along the ground, a melon grows at the end
    vine_y = 14
    length = (3, 6, 9, 12)[n]
    for x in range(1, 1 + length):
        im.putpixel((x, vine_y if x % 4 < 2 else vine_y + 1), STEM)
    if n >= 1:
        leaves(im, 3, 13, size=1)
    if n >= 2:
        leaves(im, 7, 13, size=2)
    sizes = {2: 3, 3: 6}
    if n >= 2:
        s = sizes[n]
        x0 = 2 + length - s - 1
        y0 = 16 - s
        for x in range(x0, x0 + s):
            for y in range(y0, 16):
                im.putpixel((x, y), MEL if (x - x0) % 3 else MEL_D)
        px(im, [(x0 + 1, y0)], MEL_L)
        if n == 3:
            px(im, [(x0 + 1, y0 + 1), (x0 + 2, y0 + 1), (x0 + 1, y0 + 2)], MEL_L)
            px(im, [(x0 + s - 1, 15), (x0 + s - 1, 14)], MEL_D)
            px(im, [(x0 + 2, y0 - 1)], STEM)
    return im


# ---------------------------------------------------------------- items
def seeds(c, d, accent):
    im = img()
    random.seed(sum(c))
    pts = [(5, 6), (6, 6), (9, 5), (10, 5), (4, 9), (5, 9), (8, 9), (9, 9), (11, 8), (6, 12), (7, 12), (10, 11), (11, 11)]
    for p in pts:
        im.putpixel(p, c)
        if p[0] + 1 < 16 and (p[0] + 1, p[1]) not in pts:
            im.putpixel((p[0] + 1, p[1]), d)
    px(im, [(5, 6), (9, 5), (4, 9), (8, 9), (6, 12)], accent)
    return im


def glowroot_item(roasted=False):
    im = img()
    body, dark, light = (GLOW, GLOW_D, GLOW_L) if not roasted else (col(176, 96, 36), col(120, 62, 24), col(220, 148, 70))
    rows = {4: (7, 9), 5: (6, 10), 6: (6, 10), 7: (5, 11), 8: (5, 11), 9: (6, 10), 10: (6, 10), 11: (7, 9), 12: (7, 8), 13: (8, 8)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            im.putpixel((x, y), body)
        im.putpixel((b, y), dark)
    px(im, [(7, 5), (7, 6), (6, 7)], light)
    px(im, [(7, 2), (8, 2), (8, 3), (7, 3), (9, 3), (6, 3)], LEAF)
    px(im, [(8, 4)], LEAF_D)
    if not roasted:
        px(im, [(5, 6), (11, 6), (4, 8), (12, 8), (7, 14)], GLOW_L)    # glow flecks
    else:
        px(im, [(8, 7), (9, 9), (7, 10)], col(80, 40, 16))
    return im


def pods(c, d, l):
    im = img()
    for (x, y, ln) in ((4, 3, 8), (8, 4, 8), (11, 5, 7)):
        for i in range(ln):
            xx = x + (1 if i in (3, 4) else 0)
            im.putpixel((xx, y + i), c)
            im.putpixel((xx + 1, y + i), d)
        px(im, [(x, y + 1), (x, y + 2)], l)
    px(im, [(4, 2), (8, 3), (11, 4)], STEM)
    return im


def stew():
    im = img()
    bowl, bowl_d, rim = col(150, 100, 56), col(104, 66, 34), col(190, 140, 80)
    for y, (a, b) in {8: (2, 13), 9: (2, 13), 10: (3, 12), 11: (4, 11), 12: (5, 10), 13: (6, 9)}.items():
        for x in range(a, b + 1):
            im.putpixel((x, y), bowl if y < 12 else bowl_d)
    for x in range(2, 14):
        im.putpixel((x, 7), rim)
        im.putpixel((x, 8), BEAN if x % 3 else BEAN_D)          # beans and broth on top
    px(im, [(5, 7), (9, 7), (11, 7)], BEAN_L)
    px(im, [(6, 5), (7, 4), (9, 4), (10, 3)], col(220, 230, 236))    # steam
    return im


def slice_item():
    im = img()
    for y in range(5, 12):
        w = 12 - (y - 5) * 1
        a = 8 - w // 2
        for x in range(a, a + w):
            im.putpixel((x, y), MEL if y < 10 else MEL_L)
    for x in range(2, 14):
        im.putpixel((x, 4), MEL_D)
    px(im, [(5, 7), (8, 8), (10, 6), (7, 6)], col(110, 80, 30))     # seeds
    return im


def juice():
    im = img()
    glass = col(214, 232, 240)
    for y in range(4, 14):
        px(im, [(4, y), (11, y)], glass)
    px(im, [(x, 13) for x in range(4, 12)], glass)
    for y in range(6, 13):
        for x in range(5, 11):
            im.putpixel((x, y), MEL if y > 7 else MEL_L)
    px(im, [(5, 4), (6, 4), (9, 4), (10, 4)], glass)
    px(im, [(6, 2), (7, 2), (8, 2), (9, 2), (6, 3), (9, 3)], col(200, 200, 205))
    px(im, [(6, 8), (6, 9)], col(255, 255, 255))
    return im


def seed_screen():
    im = img()
    frame, frame_d, wire = col(124, 90, 50), col(84, 58, 30), col(196, 204, 214)
    for x in range(1, 15):
        for y in (3, 12):
            im.putpixel((x, y), frame)
        for y in (4, 13):
            im.putpixel((x, y), frame_d)
    for y in range(3, 13):
        for x in (1, 14):
            im.putpixel((x, y), frame)
    for x in range(2, 14):
        for y in range(5, 12):
            if x % 3 == 0 or y % 3 == 0:
                im.putpixel((x, y), wire)
    px(im, [(5, 7), (6, 7), (9, 9), (10, 9), (7, 10)], col(110, 190, 70))   # green seeds caught in the mesh
    px(im, [(5, 8), (9, 10)], col(60, 130, 40))
    return im


def overlay_seed():
    im = img()
    for x in range(16):
        for y in range(16):
            if x % 3 == 0 or y % 3 == 0:
                im.putpixel((x, y), col(176, 140, 90) if (x + y) % 2 else col(124, 90, 50))
    return im


def effect_icon():
    im = img(18, 18)
    for y in range(18):
        for x in range(18):
            if (x - 8.5) ** 2 + (y - 8.5) ** 2 <= 56:
                im.putpixel((x, y), col(40, 90, 110))
    # a hand holding a pan
    px(im, [(x, 11) for x in range(4, 14)], col(200, 210, 216))
    px(im, [(x, 12) for x in range(5, 13)], col(150, 160, 168))
    px(im, [(8, y) for y in range(4, 11)], col(238, 196, 150))
    px(im, [(9, y) for y in range(5, 10)], col(214, 168, 120))
    px(im, [(7, 4), (10, 4), (10, 5)], col(238, 196, 150))
    px(im, [(5, 8), (12, 8), (4, 7), (13, 7)], BEAN_L)       # steady marks
    return im


# ---------------------------------------------------------------- sluice blockstates
def sluice_blockstates():
    yaw = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for state in ('sluice_box', 'sluice_box_t2'):
        path = os.path.join(ROOT, 'blockstates', state + '.json')
        with open(path, encoding='utf-8') as f:
            data = json.load(f)
        data['multipart'] = [p for p in data['multipart'] if p.get('when', {}).get('upgrade') != 'seed']
        for facing, y in yaw.items():
            apply = {'model': 'holeydigsite:block/sluice_upgrade_seed'}
            if y:
                apply['y'] = y
            data['multipart'].append({'when': {'facing': facing, 'upgrade': 'seed'}, 'apply': apply})
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=1)
            f.write('\n')


def main():
    for n in range(4):
        save(glowroot_stage(n), os.path.join(TEX_B, 'glowroot_crop_stage%d.png' % n))
        save(riverbean_stage(n), os.path.join(TEX_B, 'riverbean_crop_stage%d.png' % n))
        save(dune_melon_stage(n), os.path.join(TEX_B, 'dune_melon_crop_stage%d.png' % n))
    save(seeds(col(255, 178, 60), col(190, 110, 30), GLOW_L), os.path.join(TEX_I, 'glowroot_seeds.png'))
    save(seeds(col(60, 170, 170), col(30, 110, 120), BEAN_L), os.path.join(TEX_I, 'riverbean_seeds.png'))
    save(seeds(SEED_C, SEED_D, col(244, 226, 170)), os.path.join(TEX_I, 'dune_melon_seeds.png'))
    save(glowroot_item(False), os.path.join(TEX_I, 'glowroot.png'))
    save(glowroot_item(True), os.path.join(TEX_I, 'roasted_glowroot.png'))
    save(pods(BEAN, BEAN_D, BEAN_L), os.path.join(TEX_I, 'riverbean.png'))
    save(stew(), os.path.join(TEX_I, 'riverbean_stew.png'))
    save(slice_item(), os.path.join(TEX_I, 'dune_melon_slice.png'))
    save(juice(), os.path.join(TEX_I, 'dune_melon_juice.png'))
    save(seed_screen(), os.path.join(TEX_I, 'seed_screen.png'))
    save(overlay_seed(), os.path.join(TEX_B, 'sluice_insert_seed.png'))
    save(effect_icon(), os.path.join(TEX_E, 'steady_hands.png'))
    # overlay model: a flat grid just above the ramp, like the mesh but one pixel higher
    model = {'parent': 'minecraft:block/block',
             'textures': {'particle': 'holeydigsite:block/sluice_insert_seed', 'insert': 'holeydigsite:block/sluice_insert_seed'},
             'elements': [{'from': [2, 9.2, 4], 'to': [14, 9.3, 15], 'rotation': {'origin': [8, 8, 8], 'axis': 'x', 'angle': -22.5},
                           'faces': {'up': {'texture': '#insert'}, 'down': {'texture': '#insert'}}}]}
    path = os.path.join(ROOT, 'models', 'block', 'sluice_upgrade_seed.json')
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=2)
        f.write('\n')
    sluice_blockstates()


if __name__ == '__main__':
    main()
