"""Generates the Phase 4 textures (16x16, vanilla style) into src/main/resources/assets/holeydigsite/textures.

    python tools/make_phase4_textures.py        (needs Pillow; output is deterministic)

Dirtrock, Dirt Clump, Water Intake, Water Pipe and the Tier 1 Mining Drill (incl. the animated drill bit).
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                    'holeydigsite', 'textures')
BLOCK = os.path.join(ROOT, 'block')
ITEM = os.path.join(ROOT, 'item')
T = (0, 0, 0, 0)


def new(w=16, h=16):
    return Image.new('RGBA', (w, h), T)


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + '.png'))


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


# ------------------------------------------------------------------------------------------------
# Dirtrock: packed dark dirt with embedded rocky bits
# ------------------------------------------------------------------------------------------------
def dirtrock():
    rnd = random.Random(4042)
    img = new()
    dirt = [(47, 33, 23), (56, 39, 27), (64, 45, 31), (72, 51, 35)]
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgba(dirt[rnd.choice((0, 1, 1, 2, 2, 2, 3))]))
    # compacted streaks (darker horizontal smears)
    for _ in range(7):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for i in range(rnd.randint(2, 4)):
            img.putpixel(((x + i) % 16, y), rgba(dirt[0]))
    # rocky bits: small grey stones with a light top-left edge and dark bottom-right shadow
    stones = [(3, 2, 3), (11, 1, 2), (7, 7, 3), (1, 10, 2), (13, 9, 3), (5, 13, 2), (10, 13, 2), (14, 4, 1)]
    for sx, sy, size in stones:
        for dy in range(size):
            for dx in range(size):
                if size == 3 and (dx, dy) in ((0, 0), (2, 2)):
                    continue
                px, py = (sx + dx) % 16, (sy + dy) % 16
                shade = (98, 96, 92) if dx + dy == 0 or (dy == 0) else (78, 76, 73)
                if dx + dy >= size:
                    shade = (60, 58, 56)
                img.putpixel((px, py), rgba(shade))
        img.putpixel(((sx + size) % 16, (sy + size - 1) % 16), rgba((36, 25, 18)))  # shadow
    for x, y in ((8, 4), (2, 6), (12, 15), (6, 10)):  # tiny pebbles
        img.putpixel((x, y), rgba((88, 84, 80)))
    save(img, BLOCK, 'dirtrock')


# ------------------------------------------------------------------------------------------------
# Dirt Clump item: an irregular lump of dirt
# ------------------------------------------------------------------------------------------------
def dirt_clump():
    rnd = random.Random(77)
    img = new()
    rows = {  # y: (x0, x1) of the lump
        3: (6, 9), 4: (4, 11), 5: (3, 12), 6: (3, 13), 7: (2, 13), 8: (2, 13), 9: (2, 13),
        10: (3, 13), 11: (3, 12), 12: (5, 11), 13: (7, 9),
    }
    light, mid, dark, deep = (150, 108, 74), (121, 85, 58), (89, 61, 41), (60, 41, 28)
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            d = (x - 5) + (y - 5)  # lit from the top-left
            c = light if d < 0 else mid if d < 9 else dark
            if rnd.random() < 0.18:
                c = dark if c != dark else deep
            img.putpixel((x, y), rgba(c))
    # outline: darkest on the bottom/right
    for y, (x0, x1) in rows.items():
        img.putpixel((x1, y), rgba(deep))
        if y >= 9:
            img.putpixel((x0, y), rgba(dark))
    for x in range(5, 12):
        if img.getpixel((x, 12))[3]:
            img.putpixel((x, 12), rgba(deep))
    # crumbs and a pebble
    img.putpixel((12, 14), rgba(dark))
    img.putpixel((1, 11), rgba(mid))
    img.putpixel((8, 7), rgba((128, 124, 118)))
    img.putpixel((9, 7), rgba((96, 93, 88)))
    img.putpixel((5, 5), rgba((176, 128, 88)))
    img.putpixel((6, 4), rgba((176, 128, 88)))
    save(img, ITEM, 'dirt_clump')


# ------------------------------------------------------------------------------------------------
# Metal helpers
# ------------------------------------------------------------------------------------------------
STEEL = {'hi': (196, 202, 208), 'lt': (168, 174, 182), 'mid': (138, 144, 152), 'dk': (100, 106, 114), 'xdk': (62, 66, 72)}
WATER = {'hi': (120, 170, 255), 'mid': (63, 118, 228), 'dk': (40, 74, 170)}
PAINT = {'hi': (240, 186, 74), 'lt': (222, 160, 46), 'mid': (198, 136, 34), 'dk': (150, 98, 26), 'xdk': (92, 60, 20)}


def panel(img, pal, x0, y0, x1, y1):
    """A bevelled rectangle: light top/left edge, dark bottom/right edge."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), rgba(pal['mid']))
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), rgba(pal['lt']))
        img.putpixel((x, y1), rgba(pal['dk']))
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), rgba(pal['lt']))
        img.putpixel((x1, y), rgba(pal['dk']))
    img.putpixel((x0, y0), rgba(pal['hi']))


def border(img, c):
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, rgba(c))


def rivets(img, pts, pal):
    for x, y in pts:
        img.putpixel((x, y), rgba(pal['hi']))
        img.putpixel((x + 1, y + 1), rgba(pal['xdk']))


def flange(img, cx, cy, hole_rgb=None):
    """Round pipe flange (radius 4) with a 6x6 opening matching the 6 px pipe."""
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= 4.6:
                ang = math.atan2(y + 0.5 - cy, x + 0.5 - cx)
                c = STEEL['lt'] if -2.6 < ang < -0.6 else STEEL['dk'] if 0.6 < ang < 2.6 else STEEL['mid']
                img.putpixel((x, y), rgba(c))
            if 4.6 < d <= 5.3:
                img.putpixel((x, y), rgba(STEEL['xdk']))
    for y in range(5, 11):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (5, 10)
            if hole_rgb is None:
                img.putpixel((x, y), rgba((24, 24, 28) if not edge else (40, 42, 48)))
            else:
                img.putpixel((x, y), rgba(hole_rgb['dk'] if edge else hole_rgb['mid']))
    for x, y in ((7, 2), (2, 7), (12, 8), (8, 12)):  # bolts on the flange
        img.putpixel((x, y), rgba(STEEL['hi']))


# ------------------------------------------------------------------------------------------------
# Water Pipe: a "plus" layout so each face's default UV lands on the right part
# ------------------------------------------------------------------------------------------------
def water_pipe(filled):
    img = new()
    across = [STEEL['dk'], STEEL['mid'], STEEL['lt'], STEEL['hi'], STEEL['mid'], STEEL['dk']]
    for i in range(16):
        for k in range(6):
            img.putpixel((5 + k, i), rgba(across[k]))  # vertical arm (up/down faces of N/S arms)
            img.putpixel((i, 5 + k), rgba(across[k]))  # horizontal arm (side faces)
    for i in (0, 15):  # flange ring at the arm ends
        for k in range(6):
            img.putpixel((5 + k, i), rgba(STEEL['xdk']))
            img.putpixel((i, 5 + k), rgba(STEEL['xdk']))
    for i in (2, 13):  # joint bands
        for k in range(6):
            img.putpixel((5 + k, i), rgba(STEEL['dk']))
            img.putpixel((i, 5 + k), rgba(STEEL['dk']))
    # core / end cap: square box with a bolt in each corner
    panel(img, STEEL, 5, 5, 10, 10)
    for p in ((6, 6), (9, 6), (6, 9), (9, 9)):
        img.putpixel(p, rgba(STEEL['xdk']))
    if filled:  # small glass window showing water
        img.putpixel((7, 7), rgba(WATER['hi']))
        img.putpixel((8, 7), rgba(WATER['mid']))
        img.putpixel((7, 8), rgba(WATER['mid']))
        img.putpixel((8, 8), rgba(WATER['dk']))
    else:
        for p in ((7, 7), (8, 7), (7, 8), (8, 8)):
            img.putpixel(p, rgba(STEEL['mid']))
    save(img, BLOCK, 'water_pipe_filled' if filled else 'water_pipe')


# ------------------------------------------------------------------------------------------------
# Water Intake: steel box with grated slots (water visible when active) and a pipe flange on top
# ------------------------------------------------------------------------------------------------
def water_intake():
    for active in (False, True):
        img = new()
        panel(img, STEEL, 0, 0, 15, 15)
        border(img, STEEL['xdk'])
        for x in (3, 6, 9, 12):  # vertical inflow slots
            for y in range(4, 13):
                if active:
                    c = WATER['hi'] if y == 4 else WATER['dk'] if y >= 11 else WATER['mid']
                else:
                    c = (26, 26, 30)
                img.putpixel((x, y), rgba(c))
                img.putpixel((x + 1, y), rgba(STEEL['dk']))
        # status lamp in the top band
        lamp = WATER['hi'] if active else (34, 40, 56)
        for p in ((7, 2), (8, 2)):
            img.putpixel(p, rgba(lamp))
        rivets(img, ((1, 1), (13, 1), (1, 13), (13, 13)), STEEL)
        save(img, BLOCK, 'water_intake_side_active' if active else 'water_intake_side')
    top = new()
    panel(top, STEEL, 0, 0, 15, 15)
    border(top, STEEL['xdk'])
    flange(top, 8, 8)
    save(top, BLOCK, 'water_intake_top')


# ------------------------------------------------------------------------------------------------
# Tier 1 Mining Drill
# ------------------------------------------------------------------------------------------------
def casing():
    img = new()
    panel(img, PAINT, 0, 0, 15, 15)
    border(img, PAINT['xdk'])
    return img


def drill_front(frame, frames=4):
    """Casing with a round, spiral-fluted drill bit. Frame n rotates the flutes by n/frames of a turn."""
    img = casing()
    cx = cy = 8.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            if r <= 6.2:
                img.putpixel((x, y), rgba(STEEL['xdk']))  # bit housing ring
            if r <= 5.4:
                ang = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
                t = (ang * 3 + r * 0.16 - frame / frames / 3 * 3) % 1.0  # three flutes, spiral twist
                if t < 0.32:
                    c = (52, 54, 60)                                        # flute groove
                elif t < 0.45:
                    c = STEEL['hi']                                         # cutting edge
                else:
                    c = STEEL['lt'] if dy < 0 else STEEL['mid']             # lit from above
                img.putpixel((x, y), rgba(c))
            if r <= 1.3:
                img.putpixel((x, y), rgba(STEEL['dk']))                     # hub
    rivets(img, ((1, 1), (13, 1), (1, 13), (13, 13)), PAINT)
    return img


def drill_side(lamp):
    img = casing()
    for y in (9, 11, 13):  # vent slots
        for x in range(3, 13):
            img.putpixel((x, y), rgba(PAINT['xdk']))
            img.putpixel((x, y - 1), rgba(PAINT['dk']))
    # indicator lamp (4x3) with a dark bezel
    bezel = (40, 36, 30)
    for y in range(2, 7):
        for x in range(5, 11):
            img.putpixel((x, y), rgba(bezel))
    core, glow, edge = lamp
    for y in range(3, 6):
        for x in range(6, 10):
            img.putpixel((x, y), rgba(edge))
    for p in ((7, 4), (8, 4)):
        img.putpixel(p, rgba(glow))
    img.putpixel((7, 3), rgba(core))
    rivets(img, ((1, 1), (13, 1), (1, 13), (13, 13)), PAINT)
    return img


def mining_drill():
    frames = 4
    strip = Image.new('RGBA', (16, 16 * frames), T)
    for f in range(frames):
        strip.paste(drill_front(f, frames), (0, 16 * f))
    save(strip, BLOCK, 'mining_drill_front_working')
    with open(os.path.join(BLOCK, 'mining_drill_front_working.png.mcmeta'), 'w') as fh:
        fh.write('{\n  "animation": {\n    "frametime": 2\n  }\n}\n')
    save(drill_front(0, frames), BLOCK, 'mining_drill_front')

    lamps = {  # (hot spot, glow, edge)
        'working': ((210, 255, 200), (110, 235, 110), (40, 160, 60)),
        'stopped': ((255, 210, 200), (240, 70, 60), (150, 30, 30)),
        'no_power': ((80, 110, 170), (50, 72, 128), (30, 40, 74)),
    }
    for name, lamp in lamps.items():
        save(drill_side(lamp), BLOCK, 'mining_drill_side_' + name)

    back = casing()
    flange(back, 8, 8)
    save(back, BLOCK, 'mining_drill_back')

    top = casing()
    for i in range(1, 15):  # hazard stripes along the front edge
        for y in (1, 2):
            if (i + y) // 2 % 2 == 0:
                top.putpixel((i, y), rgba((36, 32, 28)))
    for x in range(4, 12):  # hatch
        for y in range(6, 12):
            top.putpixel((x, y), rgba(PAINT['dk'] if x in (4, 11) or y in (6, 11) else PAINT['mid']))
    top.putpixel((10, 8), rgba(STEEL['hi']))
    rivets(top, ((1, 13), (13, 13)), PAINT)
    save(top, BLOCK, 'mining_drill_top')

    bottom = new()
    panel(bottom, STEEL, 0, 0, 15, 15)
    border(bottom, STEEL['xdk'])
    save(bottom, BLOCK, 'mining_drill_bottom')


if __name__ == '__main__':
    dirtrock()
    dirt_clump()
    water_pipe(False)
    water_pipe(True)
    water_intake()
    mining_drill()
    print('textures written to', os.path.normpath(ROOT))
