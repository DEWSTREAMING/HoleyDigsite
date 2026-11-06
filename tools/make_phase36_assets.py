"""
Phase 36 assets generator:
- Item textures and models: hemp_seeds, hemp, fibre, rope, beam, pit_prop
- Hemp crop textures, models, blockstate, loot table
- Machine textures, 3D block models, blockstates, loot tables: Threshing Drum, Rope Machine, Sawmill
- 3D Preview sheet: docs/preview/phase36/hemp_and_timber_1c.png (with Gem Cutter and Mining Drill comparison)

Usage: python tools/make_phase36_assets.py
"""

import json
import math
import os
import sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/holeydigsite")
DATA = os.path.join(RES, "data/holeydigsite")
BLOCK_TEX = os.path.join(ASSETS, "textures/block")
ITEM_TEX = os.path.join(ASSETS, "textures/item")
PREVIEW_DIR = os.path.join(ROOT, "docs/preview/phase36")

# Holey Digsite palettes (matching make_phase12_assets.py and sluice_wood.png)
WOOD = [(85, 56, 30, 255), (108, 72, 38, 255), (132, 90, 48, 255), (154, 108, 60, 255)]
IRON = [(38, 38, 42, 255), (58, 59, 64, 255), (82, 83, 89, 255), (112, 113, 120, 255), (146, 147, 154, 255)]
STEEL = [(64, 66, 74, 255), (98, 101, 110, 255), (138, 142, 152, 255), (178, 182, 192, 255), (214, 217, 224, 255)]
GOLD = [(120, 84, 20, 255), (196, 150, 40, 255), (240, 204, 92, 255)]
PIPE = [(70, 72, 78, 255), (120, 122, 130, 255), (160, 162, 170, 255), (196, 198, 206, 255)]


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
# 1. Item Textures
# ------------------------------------------------------------------------------------------------
def make_item_textures():
    # A. Hemp Seeds: teardrop shaped striped seeds
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    c_dark = hexc('#3E301E')
    c_mid = hexc('#6B5738')
    c_light = hexc('#9A8358')
    c_stripe = hexc('#C2B088')
    s1 = [(5, 7), (6, 6), (7, 5), (6, 7), (7, 6), (8, 6), (7, 7), (8, 7), (9, 7), (6, 8), (7, 8), (8, 8), (7, 9)]
    for x, y in s1:
        img.putpixel((x, y), c_mid)
    img.putpixel((7, 5), c_dark)
    img.putpixel((7, 6), c_stripe)
    img.putpixel((7, 7), c_stripe)
    img.putpixel((8, 8), c_dark)
    s2 = [(10, 9), (11, 8), (11, 9), (12, 9), (10, 10), (11, 10), (12, 10), (11, 11)]
    for x, y in s2:
        img.putpixel((x, y), c_mid)
    img.putpixel((11, 8), c_dark)
    img.putpixel((11, 9), c_light)
    img.putpixel((11, 10), c_stripe)
    s3 = [(4, 10), (5, 9), (4, 11), (5, 10), (6, 10), (5, 11), (6, 11), (5, 12)]
    for x, y in s3:
        img.putpixel((x, y), c_mid)
    img.putpixel((5, 9), c_dark)
    img.putpixel((5, 10), c_stripe)
    save(img, os.path.join(ITEM_TEX, 'hemp_seeds.png'))

    # B. Hemp Stalk
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    stem = hexc('#7A9942')
    stem_d = hexc('#556B2D')
    stem_l = hexc('#A3C263')
    leaf = hexc('#4D7A27')
    leaf_l = hexc('#6FA33A')
    leaf_d = hexc('#36541A')
    for i in range(12):
        x = 3 + i
        y = 14 - i
        img.putpixel((x, y), stem)
        if x > 3:
            img.putpixel((x - 1, y), stem_d)
        if y < 14:
            img.putpixel((x, y + 1), stem_l)
    img.putpixel((6, 11), hexc('#C2A555'))
    img.putpixel((9, 8), hexc('#C2A555'))
    img.putpixel((4, 11), leaf)
    img.putpixel((3, 11), leaf_l)
    img.putpixel((7, 13), leaf)
    img.putpixel((8, 14), leaf_d)
    img.putpixel((7, 7), leaf)
    img.putpixel((6, 6), leaf_l)
    img.putpixel((5, 6), leaf_l)
    img.putpixel((10, 10), leaf)
    img.putpixel((11, 11), leaf_d)
    img.putpixel((11, 5), leaf)
    img.putpixel((12, 4), leaf_l)
    img.putpixel((13, 3), leaf_l)
    save(img, os.path.join(ITEM_TEX, 'hemp.png'))

    # C. Fibre Hank
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    f_dark = hexc('#8A7852')
    f_mid = hexc('#B3A072')
    f_light = hexc('#D6C498')
    f_high = hexc('#EDE0BD')
    band = hexc('#5E492B')
    for y in range(2, 14):
        for x in range(5, 11):
            c = f_mid
            if x in (5, 10):
                c = f_dark
            elif x in (7, 8):
                c = f_high if y % 2 == 0 else f_light
            img.putpixel((x, y), c)
    for y in (7, 8):
        for x in range(4, 12):
            img.putpixel((x, y), band)
    img.putpixel((7, 7), hexc('#856B43'))
    img.putpixel((8, 7), hexc('#856B43'))
    save(img, os.path.join(ITEM_TEX, 'fibre.png'))

    # D. Rope Coil
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    r_dark = hexc('#6E5531')
    r_mid = hexc('#9C7D4C')
    r_light = hexc('#C2A36B')
    r_high = hexc('#DFC48F')
    coil_pts = [
        (6, 3), (7, 3), (8, 3), (9, 3), (10, 4), (11, 5), (12, 6), (12, 7), (12, 8), (12, 9),
        (11, 10), (10, 11), (9, 12), (8, 12), (7, 12), (6, 12), (5, 11), (4, 10), (3, 9), (3, 8), (3, 7), (3, 6), (4, 5), (5, 4),
        (6, 5), (7, 5), (8, 5), (9, 5), (10, 6), (10, 7), (10, 8), (9, 9), (8, 10), (7, 10), (6, 10), (5, 9), (5, 8), (5, 7), (5, 6)
    ]
    for x, y in coil_pts:
        img.putpixel((x, y), r_mid)
        if x < 8:
            img.putpixel((x + 1, y), r_light)
        else:
            img.putpixel((x - 1, y), r_dark)
    img.putpixel((11, 12), r_light)
    img.putpixel((12, 13), r_mid)
    img.putpixel((13, 14), r_dark)
    img.putpixel((13, 13), r_high)
    for x in range(3, 13):
        for y in range(3, 13):
            if img.getpixel((x, y))[3] > 0:
                if (x + y) % 2 == 0:
                    img.putpixel((x, y), r_high)
                elif (x - y) % 3 == 0:
                    img.putpixel((x, y), r_dark)
    save(img, os.path.join(ITEM_TEX, 'rope.png'))

    # E. Timber Beam
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    w_dark = hexc('#4D3418')
    w_mid = hexc('#75532A')
    w_light = hexc('#9E7440')
    w_high = hexc('#BD925A')
    for x in range(2, 6):
        for y in range(10, 14):
            img.putpixel((x, y), w_light)
    img.putpixel((3, 11), w_dark)
    img.putpixel((4, 12), w_dark)
    img.putpixel((2, 10), w_high)
    img.putpixel((5, 13), w_dark)
    for i in range(10):
        for w in range(4):
            x = 2 + w + i
            y = 10 - i
            if 0 <= x < 16 and 0 <= y < 16:
                c = w_high if w == 0 else w_light
                if (x + y) % 4 == 0:
                    c = w_mid
                img.putpixel((x, y), c)
    for i in range(10):
        for h in range(4):
            x = 5 + i
            y = 10 + h - i
            if 0 <= x < 16 and 0 <= y < 16:
                c = w_mid if h < 3 else w_dark
                img.putpixel((x, y), c)
    save(img, os.path.join(ITEM_TEX, 'beam.png'))

    # F. Pit Prop
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    bark_d = hexc('#2E1F11')
    bark_m = hexc('#4A3521')
    bark_l = hexc('#634930')
    wood_in = hexc('#9E7749')
    wood_h = hexc('#BF9865')
    for y in range(1, 15):
        for x in range(5, 11):
            c = bark_m
            if x == 5:
                c = bark_d
            elif x == 6:
                c = bark_m
            elif x in (7, 8):
                c = bark_l
            elif x == 9:
                c = bark_m
            elif x == 10:
                c = bark_d
            if (y * 3 + x) % 5 == 0:
                c = bark_d
            img.putpixel((x, y), c)
    for x in range(6, 10):
        img.putpixel((x, 1), wood_h)
        img.putpixel((x, 2), wood_in)
    img.putpixel((7, 3), wood_in)
    img.putpixel((8, 3), wood_in)
    img.putpixel((7, 2), bark_d)
    for x in range(5, 11):
        img.putpixel((x, 14), bark_d)
    save(img, os.path.join(ITEM_TEX, 'pit_prop.png'))


# ------------------------------------------------------------------------------------------------
# 2. Hemp Crop Textures (Stages 0 - 3)
# ------------------------------------------------------------------------------------------------
def make_crop_textures():
    g_d = hexc('#3C5E1B')
    g_m = hexc('#5E8C2B')
    g_l = hexc('#87BD3E')
    stem = hexc('#6E8C34')
    earth = hexc('#473319')

    s0 = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    s0.putpixel((7, 15), earth)
    s0.putpixel((7, 14), stem)
    s0.putpixel((6, 13), g_l)
    s0.putpixel((7, 13), g_m)
    s0.putpixel((8, 13), g_d)
    save(s0, os.path.join(BLOCK_TEX, 'hemp_crop_stage0.png'))

    s1 = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(11, 16):
        s1.putpixel((7, y), stem)
    s1.putpixel((8, y), stem)
    s1.putpixel((5, 11), g_l)
    s1.putpixel((6, 11), g_m)
    s1.putpixel((6, 12), g_d)
    s1.putpixel((9, 11), g_m)
    s1.putpixel((10, 11), g_l)
    s1.putpixel((9, 12), g_d)
    s1.putpixel((7, 10), g_l)
    s1.putpixel((8, 10), g_m)
    s1.putpixel((7, 9), g_l)
    save(s1, os.path.join(BLOCK_TEX, 'hemp_crop_stage1.png'))

    s2 = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(6, 16):
        s2.putpixel((7, y), stem)
        s2.putpixel((8, y), stem)
    for x, y in [(4, 10), (5, 9), (6, 9), (9, 9), (10, 9), (11, 10)]:
        s2.putpixel((x, y), g_m)
    for x, y in [(3, 10), (4, 9), (11, 9), (12, 10)]:
        s2.putpixel((x, y), g_l)
    for x, y in [(5, 7), (6, 6), (9, 6), (10, 7)]:
        s2.putpixel((x, y), g_m)
    s2.putpixel((7, 5), g_l)
    s2.putpixel((8, 5), g_m)
    s2.putpixel((7, 4), g_l)
    save(s2, os.path.join(BLOCK_TEX, 'hemp_crop_stage2.png'))

    s3 = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(1, 16):
        s3.putpixel((7, y), stem)
        s3.putpixel((8, y), stem)
    for x, y in [(2, 11), (3, 10), (4, 10), (5, 9), (10, 9), (11, 10), (12, 10), (13, 11)]:
        s3.putpixel((x, y), g_m)
    for x, y in [(1, 11), (2, 10), (13, 10), (14, 11)]:
        s3.putpixel((x, y), g_l)
    for x, y in [(3, 7), (4, 6), (5, 6), (6, 5), (9, 5), (10, 6), (11, 6), (12, 7)]:
        s3.putpixel((x, y), g_m)
    for x, y in [(2, 7), (3, 6), (12, 6), (13, 7)]:
        s3.putpixel((x, y), g_l)
    for x, y in [(5, 3), (6, 2), (7, 1), (8, 1), (9, 2), (10, 3)]:
        s3.putpixel((x, y), g_m)
    s3.putpixel((7, 0), g_l)
    s3.putpixel((8, 0), g_l)
    for x, y in [(6, 1), (9, 1), (7, 2), (8, 2), (6, 4), (9, 4)]:
        s3.putpixel((x, y), hexc('#CFC07A'))
    save(s3, os.path.join(BLOCK_TEX, 'hemp_crop_stage3.png'))


# ------------------------------------------------------------------------------------------------
# 3. Machine Textures (gem_cutter_housing, clump_belt_frame_*, sluice_wood style)
# ------------------------------------------------------------------------------------------------
def load_sluice_wood():
    p = os.path.join(BLOCK_TEX, 'sluice_wood.png')
    return Image.open(p).convert('RGBA')


def load_belt_top():
    p = os.path.join(BLOCK_TEX, 'clump_belt_top.png')
    return Image.open(p).convert('RGBA')


def make_iron_housing_base():
    """
    Brushed dark iron plate matching gem_cutter_housing.png with recessed inner panel, rivets,
    and the 6x6 steel-and-gold water inlet port in the center [5..10, 5..10] for the 3D side stubs.
    """
    img = Image.new('RGBA', (16, 16), IRON[1])
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = IRON[0]
            elif x in (1, 14) or y in (1, 14):
                c = IRON[2]
            else:
                c = IRON[1]
                if x % 4 == 0:
                    c = IRON[2]
                elif x % 4 == 2:
                    c = IRON[0]
            img.putpixel((x, y), c)
    for y in range(3, 13):
        for x in range(3, 13):
            if y in (3, 12) or x in (3, 12):
                img.putpixel((x, y), IRON[0])
            else:
                img.putpixel((x, y), IRON[1] if (x + y) % 3 != 0 else IRON[0])
    for rx, ry in [(2, 2), (13, 2), (2, 13), (13, 13), (5, 2), (10, 2), (5, 13), (10, 13)]:
        img.putpixel((rx, ry), IRON[3])
    # Center 6x6 water inlet flange at [5..10, 5..10] used by the 3D side inlet stubs
    for y in range(5, 11):
        for x in range(5, 11):
            if x in (5, 10) or y in (5, 10):
                img.putpixel((x, y), PIPE[1])
            elif x in (6, 9) or y in (6, 9):
                img.putpixel((x, y), PIPE[2] if (x + y) % 2 == 0 else PIPE[0])
            else:
                img.putpixel((x, y), (20, 24, 30, 255))
    for bx, by in [(5, 5), (10, 5), (5, 10), (10, 10)]:
        img.putpixel((bx, by), GOLD[1])
    return img


def make_wood_and_iron_side(open_top_stanchion=False):
    """
    Sluice wood plank panel framed by heavy dark iron rails, vertical iron columns, rivets, and gold trim.
    When open_top_stanchion=True (for machines whose upper frame rises from z=2..14), the vertical
    iron columns at x=2..3 and x=12..13 and the sluice_wood core at x=4..11 continue straight through
    y=0..2 without a horizontal dark bar cutting across the stanchion joint at y=16.
    """
    img = load_sluice_wood().copy()
    for y in (13, 14, 15):
        for x in range(16):
            c = IRON[0] if y == 15 else (IRON[2] if y == 14 else IRON[1])
            img.putpixel((x, y), c)

    for y in (0, 1, 2):
        for x in range(16):
            if open_top_stanchion and 2 <= x <= 13:
                continue
            c = IRON[0] if y == 0 else (IRON[2] if y == 1 else IRON[1])
            img.putpixel((x, y), c)

    if open_top_stanchion:
        for y in range(0, 13):
            for x in (0, 15):
                img.putpixel((x, y), IRON[0])
            for x in (1, 14):
                img.putpixel((x, y), IRON[1] if y % 3 != 0 else IRON[2])
            img.putpixel((2, y), IRON[0])
            img.putpixel((13, y), IRON[0])
            img.putpixel((3, y), GOLD[1] if y % 4 != 1 else GOLD[2])
            img.putpixel((12, y), GOLD[1] if y % 4 != 1 else GOLD[2])
        for ry in (1, 5, 9, 12):
            img.putpixel((2, ry), IRON[3])
            img.putpixel((13, ry), IRON[3])
    else:
        for y in range(2, 14):
            for x in (0, 1, 2, 13, 14, 15):
                if x in (0, 15):
                    c = IRON[0]
                elif x in (1, 14):
                    c = IRON[1] if y % 3 != 0 else IRON[2]
                else:
                    c = GOLD[1]
                img.putpixel((x, y), c)
            if y in (3, 8, 12):
                img.putpixel((2, y), GOLD[2])
                img.putpixel((13, y), GOLD[2])

    for rx in (1, 5, 10, 14):
        img.putpixel((rx, 14), IRON[3])
        if not open_top_stanchion or rx in (1, 14):
            img.putpixel((rx, 1), IRON[3])
    return img


def make_belt_deck_top():
    """Clump belt rubber running surface in the center (x=2..13) with dark iron and gold-trimmed side rails."""
    img = load_belt_top().copy()
    for y in range(16):
        img.putpixel((0, y), IRON[0])
        img.putpixel((15, y), IRON[0])
        img.putpixel((1, y), IRON[3] if y % 4 == 1 else IRON[2])
        img.putpixel((14, y), IRON[3] if y % 4 == 1 else IRON[2])
        img.putpixel((2, y), GOLD[1] if y % 2 == 0 else GOLD[0])
        img.putpixel((13, y), GOLD[1] if y % 2 == 0 else GOLD[0])
    return img


def make_end_housing(with_hub=False):
    """Front/back housing using sluice_wood planks, dark iron corner columns, gold trim, and steel belt roller lip."""
    img = make_wood_and_iron_side(open_top_stanchion=False)
    for x in range(3, 13):
        img.putpixel((x, 3), GOLD[1] if x % 2 == 0 else GOLD[2])
        img.putpixel((x, 12), GOLD[0] if x % 2 == 0 else GOLD[1])
    for y in range(0, 13):
        img.putpixel((0, y), IRON[0])
        img.putpixel((15, y), IRON[0])
        img.putpixel((1, y), IRON[1] if y % 3 != 0 else IRON[2])
        img.putpixel((14, y), IRON[1] if y % 3 != 0 else IRON[2])
        img.putpixel((2, y), GOLD[1] if y % 4 != 1 else GOLD[2])
        img.putpixel((13, y), GOLD[1] if y % 4 != 1 else GOLD[2])
    for x in range(3, 13):
        img.putpixel((x, 0), IRON[0])
        img.putpixel((x, 1), STEEL[2] if x % 2 == 0 else STEEL[1])
        img.putpixel((x, 2), IRON[0])
    if with_hub:
        for y in range(6, 10):
            for x in range(6, 10):
                if x in (6, 9) or y in (6, 9):
                    img.putpixel((x, y), IRON[0])
                else:
                    img.putpixel((x, y), STEEL[2] if (x + y) % 2 == 0 else IRON[2])
        for bx, by in [(6, 6), (9, 6), (6, 9), (9, 9)]:
            img.putpixel((bx, by), GOLD[1])
    return img


def make_machine_textures():
    bottom = make_iron_housing_base()

    # A. THRESHING DRUM TEXTURES
    t_side = make_wood_and_iron_side(open_top_stanchion=True)
    save(t_side, os.path.join(BLOCK_TEX, 'threshing_drum_side.png'))

    t_top = make_belt_deck_top()
    save(t_top, os.path.join(BLOCK_TEX, 'threshing_drum_top.png'))

    t_front = make_end_housing(with_hub=False)
    save(t_front, os.path.join(BLOCK_TEX, 'threshing_drum_front.png'))
    save(t_front.copy(), os.path.join(BLOCK_TEX, 'threshing_drum_back.png'))
    save(bottom, os.path.join(BLOCK_TEX, 'threshing_drum_bottom.png'))

    rotor = load_sluice_wood().copy()
    for y in range(16):
        for x in (0, 1, 14, 15):
            rotor.putpixel((x, y), IRON[0] if x in (0, 15) else IRON[2])
        for x in range(2, 14):
            if y in (1, 5, 9, 13):
                rotor.putpixel((x, y), STEEL[2] if x % 2 == 0 else IRON[2])
            elif y in (2, 6, 10, 14) and x % 3 == 1:
                rotor.putpixel((x, y), GOLD[2])
            elif y in (2, 6, 10, 14) and x % 3 == 2:
                rotor.putpixel((x, y), GOLD[0])
    for y in range(6, 10):
        for x in range(6, 10):
            if x in (6, 9) or y in (6, 9):
                rotor.putpixel((x, y), GOLD[1])
            else:
                rotor.putpixel((x, y), STEEL[3] if (x + y) % 2 == 0 else STEEL[1])
    save(rotor, os.path.join(BLOCK_TEX, 'threshing_drum_rotor.png'))

    # B. ROPE MACHINE TEXTURES
    r_side = make_wood_and_iron_side(open_top_stanchion=True)
    save(r_side, os.path.join(BLOCK_TEX, 'rope_machine_side.png'))

    r_top = make_belt_deck_top()
    save(r_top, os.path.join(BLOCK_TEX, 'rope_machine_top.png'))

    r_front = make_end_housing(with_hub=True)
    save(r_front, os.path.join(BLOCK_TEX, 'rope_machine_front.png'))
    r_back = make_end_housing(with_hub=False)
    save(r_back, os.path.join(BLOCK_TEX, 'rope_machine_back.png'))
    save(bottom, os.path.join(BLOCK_TEX, 'rope_machine_bottom.png'))

    wheel = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if 5.0 <= d <= 7.4:
                if d >= 6.6:
                    c = IRON[0]
                elif d >= 5.7:
                    c = STEEL[3] if (x + y) % 2 == 0 else STEEL[1]
                else:
                    c = GOLD[1] if (x + y) % 2 == 0 else GOLD[2]
                wheel.putpixel((x, y), c)
            elif d < 5.0:
                if x in (7, 8) or y in (7, 8):
                    wheel.putpixel((x, y), STEEL[2] if (x + y) % 2 == 0 else IRON[2])
    for y in range(6, 10):
        for x in range(6, 10):
            if x in (6, 9) or y in (6, 9):
                wheel.putpixel((x, y), GOLD[1] if (x + y) % 2 == 0 else GOLD[2])
            else:
                wheel.putpixel((x, y), IRON[0] if (x == 7 and y == 7) else STEEL[3])
    for hx, hy in [(7, 1), (8, 14), (1, 8), (14, 7)]:
        wheel.putpixel((hx, hy), GOLD[2])
    save(wheel, os.path.join(BLOCK_TEX, 'rope_machine_wheel.png'))

    # C. SAWMILL TEXTURES
    s_side = make_wood_and_iron_side(open_top_stanchion=False)
    for x in range(16):
        s_side.putpixel((x, 0), STEEL[1] if x % 2 == 0 else IRON[0])
        s_side.putpixel((x, 1), STEEL[3] if x % 3 != 0 else STEEL[2])
    save(s_side, os.path.join(BLOCK_TEX, 'sawmill_side.png'))

    s_top = Image.new('RGBA', (16, 16), IRON[1])
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = IRON[0]
            elif x in (1, 14):
                c = IRON[3] if y % 4 == 1 else IRON[2]
            elif x == 13:
                c = GOLD[1] if y % 2 == 0 else GOLD[0]
            else:
                if x in (4, 11):
                    c = STEEL[1]
                elif (x + y) % 5 == 0:
                    c = IRON[2]
                else:
                    c = IRON[1]
            s_top.putpixel((x, y), c)
    for y in range(1, 15):
        s_top.putpixel((7, y), (20, 22, 26, 255))
        s_top.putpixel((8, y), (20, 22, 26, 255))
        s_top.putpixel((6, y), STEEL[2] if y % 2 == 0 else IRON[2])
        s_top.putpixel((9, y), STEEL[2] if y % 2 == 0 else IRON[2])
    save(s_top, os.path.join(BLOCK_TEX, 'sawmill_top.png'))

    s_front = make_end_housing(with_hub=True)
    for x in range(16):
        s_front.putpixel((x, 0), STEEL[1] if x % 2 == 0 else IRON[0])
        s_front.putpixel((x, 1), STEEL[3] if x % 3 != 0 else STEEL[2])
    save(s_front, os.path.join(BLOCK_TEX, 'sawmill_front.png'))
    save(s_front.copy(), os.path.join(BLOCK_TEX, 'sawmill_back.png'))
    save(bottom, os.path.join(BLOCK_TEX, 'sawmill_bottom.png'))

    blade = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            tooth = math.sin(ang * 12.0) > 0.15
            if d <= 6.2 or (d <= 7.3 and tooth):
                if d > 6.0:
                    c = STEEL[4] if tooth else STEEL[1]
                elif d > 4.2:
                    c = STEEL[3] if (x - y) % 3 == 0 else STEEL[2]
                else:
                    c = STEEL[2] if (x + y) % 2 == 0 else STEEL[1]
                blade.putpixel((x, y), c)
    for y in range(6, 10):
        for x in range(6, 10):
            if x in (6, 9) or y in (6, 9):
                blade.putpixel((x, y), GOLD[1] if (x + y) % 2 == 0 else GOLD[2])
            else:
                blade.putpixel((x, y), IRON[0] if (x == 7 and y == 7) else GOLD[0])
    save(blade, os.path.join(BLOCK_TEX, 'sawmill_blade.png'))


# ------------------------------------------------------------------------------------------------
# 4. Block & Item Models (explicit [0..16] UVs on every face, cutout moving parts)
# ------------------------------------------------------------------------------------------------
def make_models():
    for stage in range(4):
        write_json(os.path.join(ASSETS, f'models/block/hemp_crop_stage{stage}.json'), {
            "parent": "minecraft:block/crop",
            "render_type": "minecraft:cutout",
            "textures": {
                "crop": f"holeydigsite:block/hemp_crop_stage{stage}"
            }
        })

    write_json(os.path.join(ASSETS, 'blockstates/hemp_crop.json'), {
        "variants": {
            "age=0": {"model": "holeydigsite:block/hemp_crop_stage0"},
            "age=1": {"model": "holeydigsite:block/hemp_crop_stage1"},
            "age=2": {"model": "holeydigsite:block/hemp_crop_stage2"},
            "age=3": {"model": "holeydigsite:block/hemp_crop_stage3"}
        }
    })

    item_display = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.45, 0.45, 0.45]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.45, 0.45, 0.45]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}
    }

    # A. THRESHING DRUM MODELS
    drum_tex = {
        "particle": "holeydigsite:block/threshing_drum_side",
        "side": "holeydigsite:block/threshing_drum_side",
        "top": "holeydigsite:block/threshing_drum_top",
        "front": "holeydigsite:block/threshing_drum_front",
        "back": "holeydigsite:block/threshing_drum_back",
        "bottom": "holeydigsite:block/threshing_drum_bottom",
        "rotor": "holeydigsite:block/threshing_drum_rotor"
    }

    drum_frame_elements = [
        # Belt-height base body (y=0..16)
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
            "north": {"uv": [0, 0, 16, 16], "texture": "#front", "cullface": "north"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#back", "cullface": "south"},
            "west": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "west"},
            "east": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "east"}
        }},
        # Side water inlet stubs (outer face mapped to the 6x6 water inlet flange at [5..11, 5..11] of #bottom)
        {"from": [-1, 5, 5], "to": [0, 11, 11], "faces": {
            "west": {"uv": [5, 5, 11, 11], "texture": "#bottom"},
            "up": {"uv": [5, 0, 11, 1], "texture": "#bottom"},
            "down": {"uv": [5, 15, 11, 16], "texture": "#bottom"},
            "north": {"uv": [0, 5, 1, 11], "texture": "#bottom"},
            "south": {"uv": [15, 5, 16, 11], "texture": "#bottom"}
        }},
        {"from": [16, 5, 5], "to": [17, 11, 11], "faces": {
            "east": {"uv": [5, 5, 11, 11], "texture": "#bottom"},
            "up": {"uv": [5, 0, 11, 1], "texture": "#bottom"},
            "down": {"uv": [5, 15, 11, 16], "texture": "#bottom"},
            "north": {"uv": [0, 5, 1, 11], "texture": "#bottom"},
            "south": {"uv": [15, 5, 16, 11], "texture": "#bottom"}
        }},
        # Unbroken wood-and-iron side stanchions (x=0..2.5 and x=13.5..16, y=16..27, z=2..14)
        {"from": [0, 16, 2], "to": [2.5, 27, 14], "faces": {
            "west": {"uv": [2, 0, 14, 11], "texture": "#side"},
            "east": {"uv": [2, 0, 14, 11], "texture": "#side"},
            "north": {"uv": [13, 0, 16, 11], "texture": "#front"},
            "south": {"uv": [0, 0, 3, 11], "texture": "#back"},
            "up": {"uv": [0, 2, 3, 14], "texture": "#side"}
        }},
        {"from": [13.5, 16, 2], "to": [16, 27, 14], "faces": {
            "west": {"uv": [2, 0, 14, 11], "texture": "#side"},
            "east": {"uv": [2, 0, 14, 11], "texture": "#side"},
            "north": {"uv": [0, 0, 3, 11], "texture": "#front"},
            "south": {"uv": [13, 0, 16, 11], "texture": "#back"},
            "up": {"uv": [13, 2, 16, 14], "texture": "#side"}
        }},
        # Axle bearing blocks at y=22.5..25.5, z=6.5..9.5
        {"from": [-0.5, 22.5, 6.5], "to": [3, 25.5, 9.5], "faces": {
            "west": {"uv": [6, 6, 10, 10], "texture": "#rotor"},
            "east": {"uv": [6, 6, 10, 10], "texture": "#rotor"},
            "up": {"uv": [1, 1, 5, 3], "texture": "#bottom"},
            "down": {"uv": [1, 1, 5, 3], "texture": "#bottom"},
            "north": {"uv": [1, 1, 5, 3], "texture": "#bottom"},
            "south": {"uv": [1, 1, 5, 3], "texture": "#bottom"}
        }},
        {"from": [13, 22.5, 6.5], "to": [16.5, 25.5, 9.5], "faces": {
            "west": {"uv": [6, 6, 10, 10], "texture": "#rotor"},
            "east": {"uv": [6, 6, 10, 10], "texture": "#rotor"},
            "up": {"uv": [1, 1, 5, 3], "texture": "#bottom"},
            "down": {"uv": [1, 1, 5, 3], "texture": "#bottom"},
            "north": {"uv": [1, 1, 5, 3], "texture": "#bottom"},
            "south": {"uv": [1, 1, 5, 3], "texture": "#bottom"}
        }},
        # Overhead wood-and-iron cross-arch (y=27..29, matching Gem Cutter overhead arch)
        {"from": [0, 27, 2], "to": [16, 29, 14], "faces": {
            "up": {"uv": [0, 2, 16, 14], "texture": "#side"},
            "down": {"uv": [0, 2, 16, 14], "texture": "#side"},
            "north": {"uv": [0, 13, 16, 15], "texture": "#front"},
            "south": {"uv": [0, 13, 16, 15], "texture": "#back"},
            "west": {"uv": [2, 13, 14, 15], "texture": "#side"},
            "east": {"uv": [2, 13, 14, 15], "texture": "#side"}
        }}
    ]

    drum_rotor_elements = [
        {"from": [2, 23, 7], "to": [14, 25, 9], "faces": {
            "west": {"uv": [6, 6, 10, 10], "texture": "#rotor"},
            "east": {"uv": [6, 6, 10, 10], "texture": "#rotor"},
            "up": {"uv": [2, 7, 14, 9], "texture": "#rotor"},
            "down": {"uv": [2, 7, 14, 9], "texture": "#rotor"},
            "north": {"uv": [2, 7, 14, 9], "texture": "#rotor"},
            "south": {"uv": [2, 7, 14, 9], "texture": "#rotor"}
        }},
        {"from": [3, 21, 6], "to": [13, 27, 10], "faces": {
            "west": {"uv": [4, 2, 12, 14], "texture": "#rotor"},
            "east": {"uv": [4, 2, 12, 14], "texture": "#rotor"},
            "up": {"uv": [0, 2, 16, 14], "texture": "#rotor"},
            "down": {"uv": [0, 2, 16, 14], "texture": "#rotor"},
            "north": {"uv": [0, 2, 16, 14], "texture": "#rotor"},
            "south": {"uv": [0, 2, 16, 14], "texture": "#rotor"}
        }},
        {"from": [3, 22, 5], "to": [13, 26, 11], "faces": {
            "west": {"uv": [2, 4, 14, 12], "texture": "#rotor"},
            "east": {"uv": [2, 4, 14, 12], "texture": "#rotor"},
            "up": {"uv": [0, 2, 16, 14], "texture": "#rotor"},
            "down": {"uv": [0, 2, 16, 14], "texture": "#rotor"},
            "north": {"uv": [0, 2, 16, 14], "texture": "#rotor"},
            "south": {"uv": [0, 2, 16, 14], "texture": "#rotor"}
        }},
        {"from": [3.5, 27, 7.25], "to": [12.5, 28, 8.75], "faces": {
            "up": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "north": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "south": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "west": {"uv": [6, 6, 8, 8], "texture": "#rotor"},
            "east": {"uv": [6, 6, 8, 8], "texture": "#rotor"}
        }},
        {"from": [3.5, 20, 7.25], "to": [12.5, 21, 8.75], "faces": {
            "down": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "north": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "south": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "west": {"uv": [6, 6, 8, 8], "texture": "#rotor"},
            "east": {"uv": [6, 6, 8, 8], "texture": "#rotor"}
        }},
        {"from": [3.5, 23.25, 4], "to": [12.5, 24.75, 5], "faces": {
            "north": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "up": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "down": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "west": {"uv": [6, 6, 8, 8], "texture": "#rotor"},
            "east": {"uv": [6, 6, 8, 8], "texture": "#rotor"}
        }},
        {"from": [3.5, 23.25, 11], "to": [12.5, 24.75, 12], "faces": {
            "south": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "up": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "down": {"uv": [2, 1, 14, 3], "texture": "#rotor"},
            "west": {"uv": [6, 6, 8, 8], "texture": "#rotor"},
            "east": {"uv": [6, 6, 8, 8], "texture": "#rotor"}
        }}
    ]

    write_json(os.path.join(ASSETS, 'models/block/threshing_drum_rotor.json'), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": "holeydigsite:block/threshing_drum_rotor", "rotor": "holeydigsite:block/threshing_drum_rotor"},
        "elements": drum_rotor_elements
    })
    write_json(os.path.join(ASSETS, 'models/block/threshing_drum_working.json'), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": drum_tex,
        "elements": drum_frame_elements
    })
    write_json(os.path.join(ASSETS, 'models/block/threshing_drum.json'), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": drum_tex,
        "elements": drum_frame_elements + drum_rotor_elements
    })
    write_json(os.path.join(ASSETS, 'models/item/threshing_drum.json'), {
        "parent": "holeydigsite:block/threshing_drum",
        "display": item_display
    })

    # B. ROPE MACHINE MODELS
    rope_tex = {
        "particle": "holeydigsite:block/rope_machine_side",
        "side": "holeydigsite:block/rope_machine_side",
        "top": "holeydigsite:block/rope_machine_top",
        "front": "holeydigsite:block/rope_machine_front",
        "back": "holeydigsite:block/rope_machine_back",
        "bottom": "holeydigsite:block/rope_machine_bottom",
        "wheel": "holeydigsite:block/rope_machine_wheel",
        "rotor": "holeydigsite:block/threshing_drum_rotor"
    }

    rope_frame_elements = [
        # Belt-height base body (y=0..16)
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
            "north": {"uv": [0, 0, 16, 16], "texture": "#front", "cullface": "north"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#back", "cullface": "south"},
            "west": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "west"},
            "east": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "east"}
        }},
        # Side water inlet stubs (outer face mapped to the 6x6 water inlet flange at [5..11, 5..11] of #bottom)
        {"from": [-1, 5, 5], "to": [0, 11, 11], "faces": {
            "west": {"uv": [5, 5, 11, 11], "texture": "#bottom"},
            "up": {"uv": [5, 0, 11, 1], "texture": "#bottom"},
            "down": {"uv": [5, 15, 11, 16], "texture": "#bottom"},
            "north": {"uv": [0, 5, 1, 11], "texture": "#bottom"},
            "south": {"uv": [15, 5, 16, 11], "texture": "#bottom"}
        }},
        {"from": [16, 5, 5], "to": [17, 11, 11], "faces": {
            "east": {"uv": [5, 5, 11, 11], "texture": "#bottom"},
            "up": {"uv": [5, 0, 11, 1], "texture": "#bottom"},
            "down": {"uv": [5, 15, 11, 16], "texture": "#bottom"},
            "north": {"uv": [0, 5, 1, 11], "texture": "#bottom"},
            "south": {"uv": [15, 5, 16, 11], "texture": "#bottom"}
        }},
        # Unbroken wood-and-iron side stanchions (x=0..2.5 and x=13.5..16, y=16..27, z=2..14)
        {"from": [0, 16, 2], "to": [2.5, 27, 14], "faces": {
            "west": {"uv": [2, 0, 14, 11], "texture": "#side"},
            "east": {"uv": [2, 0, 14, 11], "texture": "#side"},
            "north": {"uv": [13, 0, 16, 11], "texture": "#front"},
            "south": {"uv": [0, 0, 3, 11], "texture": "#back"},
            "up": {"uv": [0, 2, 3, 14], "texture": "#side"}
        }},
        {"from": [13.5, 16, 2], "to": [16, 27, 14], "faces": {
            "west": {"uv": [2, 0, 14, 11], "texture": "#side"},
            "east": {"uv": [2, 0, 14, 11], "texture": "#side"},
            "north": {"uv": [0, 0, 3, 11], "texture": "#front"},
            "south": {"uv": [13, 0, 16, 11], "texture": "#back"},
            "up": {"uv": [13, 2, 16, 14], "texture": "#side"}
        }},
        # Top wood-and-iron cross-header bridging the stanchions (y=27..29, z=2..14)
        {"from": [0, 27, 2], "to": [16, 29, 14], "faces": {
            "up": {"uv": [0, 2, 16, 14], "texture": "#side"},
            "down": {"uv": [0, 2, 16, 14], "texture": "#side"},
            "north": {"uv": [0, 13, 16, 15], "texture": "#front"},
            "south": {"uv": [0, 13, 16, 15], "texture": "#back"},
            "west": {"uv": [2, 13, 14, 15], "texture": "#side"},
            "east": {"uv": [2, 13, 14, 15], "texture": "#side"}
        }},
        # Front wheel spindle bracket projecting to wheel center (x=6.5..9.5, y=23.5..26.5, z=2..5)
        {"from": [6.5, 23.5, 2], "to": [9.5, 26.5, 5], "faces": {
            "north": {"uv": [6, 6, 10, 10], "texture": "#front"},
            "south": {"uv": [6, 6, 10, 10], "texture": "#front"},
            "west": {"uv": [1, 1, 4, 3], "texture": "#bottom"},
            "east": {"uv": [1, 1, 4, 3], "texture": "#bottom"},
            "down": {"uv": [1, 1, 4, 3], "texture": "#bottom"}
        }},
        # Wound rope bobbin suspended inside the cradle (y=21.5..25.5, z=6..10)
        {"from": [2.5, 23, 7.5], "to": [13.5, 24.5, 9], "faces": {
            "up": {"uv": [2, 1, 14, 3], "texture": "#bottom"},
            "down": {"uv": [2, 1, 14, 3], "texture": "#bottom"},
            "north": {"uv": [2, 1, 14, 3], "texture": "#bottom"},
            "south": {"uv": [2, 1, 14, 3], "texture": "#bottom"}
        }},
        {"from": [4.5, 21.5, 6], "to": [11.5, 25.5, 10], "faces": {
            "up": {"uv": [2, 2, 14, 14], "texture": "#rotor"},
            "down": {"uv": [2, 2, 14, 14], "texture": "#rotor"},
            "north": {"uv": [2, 2, 14, 14], "texture": "#rotor"},
            "south": {"uv": [2, 2, 14, 14], "texture": "#rotor"},
            "west": {"uv": [0, 0, 2, 16], "texture": "#rotor"},
            "east": {"uv": [14, 0, 16, 16], "texture": "#rotor"}
        }}
    ]

    rope_wheel_elements = [
        {"from": [3.5, 20.5, 1.0], "to": [12.5, 29.5, 2.0], "faces": {
            "north": {"uv": [0.5, 0.5, 15.5, 15.5], "texture": "#wheel"},
            "south": {"uv": [0.5, 0.5, 15.5, 15.5], "texture": "#wheel"},
            "up": {"uv": [0.5, 0.5, 15.5, 1.5], "texture": "#wheel"},
            "down": {"uv": [0.5, 14.5, 15.5, 15.5], "texture": "#wheel"},
            "west": {"uv": [0.5, 0.5, 1.5, 15.5], "texture": "#wheel"},
            "east": {"uv": [14.5, 0.5, 15.5, 15.5], "texture": "#wheel"}
        }},
        {"from": [7.0, 24.0, 0.5], "to": [9.0, 26.0, 2.5], "faces": {
            "north": {"uv": [6, 6, 10, 10], "texture": "#wheel"},
            "south": {"uv": [6, 6, 10, 10], "texture": "#wheel"},
            "up": {"uv": [6, 6, 10, 8], "texture": "#wheel"},
            "down": {"uv": [6, 8, 10, 10], "texture": "#wheel"},
            "west": {"uv": [6, 6, 8, 10], "texture": "#wheel"},
            "east": {"uv": [8, 6, 10, 10], "texture": "#wheel"}
        }}
    ]

    write_json(os.path.join(ASSETS, 'models/block/rope_machine_wheel.json'), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": "holeydigsite:block/rope_machine_wheel", "wheel": "holeydigsite:block/rope_machine_wheel"},
        "elements": rope_wheel_elements
    })
    write_json(os.path.join(ASSETS, 'models/block/rope_machine_working.json'), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": rope_tex,
        "elements": rope_frame_elements
    })
    write_json(os.path.join(ASSETS, 'models/block/rope_machine.json'), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": rope_tex,
        "elements": rope_frame_elements + rope_wheel_elements
    })
    write_json(os.path.join(ASSETS, 'models/item/rope_machine.json'), {
        "parent": "holeydigsite:block/rope_machine",
        "display": item_display
    })

    # C. SAWMILL MODELS
    sawmill_tex = {
        "particle": "holeydigsite:block/sawmill_side",
        "side": "holeydigsite:block/sawmill_side",
        "top": "holeydigsite:block/sawmill_top",
        "front": "holeydigsite:block/sawmill_front",
        "back": "holeydigsite:block/sawmill_back",
        "bottom": "holeydigsite:block/sawmill_bottom",
        "blade": "holeydigsite:block/sawmill_blade"
    }

    sawmill_frame_elements = [
        {"from": [0, 0, 0], "to": [16, 15, 16], "faces": {
            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
            "north": {"uv": [0, 1, 16, 16], "texture": "#front", "cullface": "north"},
            "south": {"uv": [0, 1, 16, 16], "texture": "#back", "cullface": "south"},
            "west": {"uv": [0, 1, 16, 16], "texture": "#side", "cullface": "west"},
            "east": {"uv": [0, 1, 16, 16], "texture": "#side", "cullface": "east"}
        }},
        {"from": [-1, 5, 5], "to": [0, 11, 11], "faces": {
            "west": {"uv": [5, 5, 11, 11], "texture": "#bottom"},
            "up": {"uv": [5, 0, 11, 1], "texture": "#bottom"},
            "down": {"uv": [5, 15, 11, 16], "texture": "#bottom"},
            "north": {"uv": [0, 5, 1, 11], "texture": "#bottom"},
            "south": {"uv": [15, 5, 16, 11], "texture": "#bottom"}
        }},
        {"from": [16, 5, 5], "to": [17, 11, 11], "faces": {
            "east": {"uv": [5, 5, 11, 11], "texture": "#bottom"},
            "up": {"uv": [5, 0, 11, 1], "texture": "#bottom"},
            "down": {"uv": [5, 15, 11, 16], "texture": "#bottom"},
            "north": {"uv": [0, 5, 1, 11], "texture": "#bottom"},
            "south": {"uv": [15, 5, 16, 11], "texture": "#bottom"}
        }},
        {"from": [0, 15, 0], "to": [7, 16, 16], "faces": {
            "up": {"uv": [0, 0, 7, 16], "texture": "#top"},
            "down": {"uv": [0, 0, 7, 16], "texture": "#bottom"},
            "west": {"uv": [0, 0, 16, 1], "texture": "#side"},
            "east": {"uv": [6, 0, 7, 16], "texture": "#top"},
            "north": {"uv": [9, 0, 16, 1], "texture": "#front"},
            "south": {"uv": [0, 0, 7, 1], "texture": "#back"}
        }},
        {"from": [9, 15, 0], "to": [16, 16, 16], "faces": {
            "up": {"uv": [9, 0, 16, 16], "texture": "#top"},
            "down": {"uv": [9, 0, 16, 16], "texture": "#bottom"},
            "west": {"uv": [9, 0, 10, 16], "texture": "#top"},
            "east": {"uv": [0, 0, 16, 1], "texture": "#side"},
            "north": {"uv": [0, 0, 7, 1], "texture": "#front"},
            "south": {"uv": [9, 0, 16, 1], "texture": "#back"}
        }},
        {"from": [7, 15, 0], "to": [9, 16, 2], "faces": {
            "up": {"uv": [7, 0, 9, 2], "texture": "#top"},
            "down": {"uv": [7, 0, 9, 2], "texture": "#bottom"},
            "south": {"uv": [7, 1, 9, 2], "texture": "#top"},
            "north": {"uv": [7, 0, 9, 1], "texture": "#front"}
        }},
        {"from": [7, 15, 14], "to": [9, 16, 16], "faces": {
            "up": {"uv": [7, 14, 9, 16], "texture": "#top"},
            "down": {"uv": [7, 14, 9, 16], "texture": "#bottom"},
            "north": {"uv": [7, 14, 9, 15], "texture": "#top"},
            "south": {"uv": [7, 0, 9, 1], "texture": "#back"}
        }},
        {"from": [13, 16, 0], "to": [15, 18, 16], "faces": {
            "up": {"uv": [13, 0, 15, 16], "texture": "#top"},
            "west": {"uv": [0, 2, 16, 4], "texture": "#front"},
            "east": {"uv": [0, 0, 16, 2], "texture": "#side"},
            "north": {"uv": [1, 0, 3, 2], "texture": "#front"},
            "south": {"uv": [13, 0, 15, 2], "texture": "#back"}
        }},
        {"from": [9, 16, 7], "to": [10, 22, 9], "faces": {
            "west": {"uv": [1, 1, 3, 4], "texture": "#bottom"},
            "east": {"uv": [1, 1, 3, 4], "texture": "#bottom"},
            "north": {"uv": [1, 1, 2, 4], "texture": "#bottom"},
            "south": {"uv": [1, 1, 2, 4], "texture": "#bottom"},
            "up": {"uv": [1, 1, 2, 3], "texture": "#bottom"}
        }},
        {"from": [6.5, 21.5, 4], "to": [9.5, 22.5, 12], "faces": {
            "up": {"uv": [6, 1, 10, 4], "texture": "#bottom"},
            "down": {"uv": [6, 1, 10, 4], "texture": "#bottom"},
            "north": {"uv": [6, 3, 10, 4], "texture": "#front"},
            "south": {"uv": [6, 3, 10, 4], "texture": "#back"},
            "west": {"uv": [4, 3, 12, 4], "texture": "#front"},
            "east": {"uv": [4, 3, 12, 4], "texture": "#front"}
        }},
        {"from": [6.5, 19.5, 4], "to": [7.2, 21.5, 12], "faces": {
            "west": {"uv": [4, 1, 12, 3], "texture": "#bottom"},
            "east": {"uv": [4, 1, 12, 3], "texture": "#bottom"},
            "down": {"uv": [1, 1, 2, 4], "texture": "#bottom"},
            "north": {"uv": [1, 1, 2, 3], "texture": "#bottom"},
            "south": {"uv": [1, 1, 2, 3], "texture": "#bottom"}
        }},
        {"from": [8.8, 19.5, 4], "to": [9.5, 21.5, 12], "faces": {
            "west": {"uv": [4, 1, 12, 3], "texture": "#bottom"},
            "east": {"uv": [4, 1, 12, 3], "texture": "#bottom"},
            "down": {"uv": [1, 1, 2, 4], "texture": "#bottom"},
            "north": {"uv": [1, 1, 2, 3], "texture": "#bottom"},
            "south": {"uv": [1, 1, 2, 3], "texture": "#bottom"}
        }}
    ]

    sawmill_blade_elements = [
        {"from": [7.5, 8.5, 2.5], "to": [8.5, 19.5, 13.5], "faces": {
            "west": {"uv": [0.5, 0.5, 15.5, 15.5], "texture": "#blade"},
            "east": {"uv": [0.5, 0.5, 15.5, 15.5], "texture": "#blade"},
            "up": {"uv": [0.5, 7.5, 15.5, 8.5], "texture": "#blade"},
            "down": {"uv": [0.5, 7.5, 15.5, 8.5], "texture": "#blade"},
            "north": {"uv": [7.5, 0.5, 8.5, 15.5], "texture": "#blade"},
            "south": {"uv": [7.5, 0.5, 8.5, 15.5], "texture": "#blade"}
        }},
        {"from": [6.5, 13.0, 7.0], "to": [9.5, 15.0, 9.0], "faces": {
            "west": {"uv": [6, 6, 10, 10], "texture": "#blade"},
            "east": {"uv": [6, 6, 10, 10], "texture": "#blade"},
            "up": {"uv": [6, 6, 10, 8], "texture": "#blade"},
            "down": {"uv": [6, 8, 10, 10], "texture": "#blade"},
            "north": {"uv": [6, 6, 8, 10], "texture": "#blade"},
            "south": {"uv": [8, 6, 10, 10], "texture": "#blade"}
        }}
    ]

    write_json(os.path.join(ASSETS, 'models/block/sawmill_blade.json'), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": "holeydigsite:block/sawmill_blade", "blade": "holeydigsite:block/sawmill_blade"},
        "elements": sawmill_blade_elements
    })
    write_json(os.path.join(ASSETS, 'models/block/sawmill_working.json'), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": sawmill_tex,
        "elements": sawmill_frame_elements
    })
    write_json(os.path.join(ASSETS, 'models/block/sawmill.json'), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": sawmill_tex,
        "elements": sawmill_frame_elements + sawmill_blade_elements
    })
    write_json(os.path.join(ASSETS, 'models/item/sawmill.json'), {
        "parent": "holeydigsite:block/sawmill",
        "display": item_display
    })

    # D. BLOCKSTATES
    def make_machine_blockstate(name):
        variants = {}
        for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
            for powered in ("false", "true"):
                for working in ("false", "true"):
                    entry = {"model": f"holeydigsite:block/{name}_working" if working == "true" else f"holeydigsite:block/{name}"}
                    if y != 0:
                        entry["y"] = y
                    variants[f"facing={facing},water_powered={powered},working={working}"] = entry
        return {"variants": variants}

    write_json(os.path.join(ASSETS, 'blockstates/threshing_drum.json'), make_machine_blockstate("threshing_drum"))
    write_json(os.path.join(ASSETS, 'blockstates/rope_machine.json'), make_machine_blockstate("rope_machine"))

    sawmill_variants = {}
    for facing, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
        for mode in ("beam", "pit_prop"):
            for powered in ("false", "true"):
                for working in ("false", "true"):
                    entry = {"model": "holeydigsite:block/sawmill_working" if working == "true" else "holeydigsite:block/sawmill"}
                    if y != 0:
                        entry["y"] = y
                    sawmill_variants[f"facing={facing},mode={mode},water_powered={powered},working={working}"] = entry
    write_json(os.path.join(ASSETS, 'blockstates/sawmill.json'), {"variants": sawmill_variants})


# ------------------------------------------------------------------------------------------------
# 5. Preview Sheet (docs/preview/phase36/hemp_and_timber_1c.png)
# ------------------------------------------------------------------------------------------------
def make_preview_sheet():
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    sys.path.insert(0, os.path.join(ROOT, 'tools'))
    import make_long_sluice as mls

    def resolve_texture(ref):
        clean = ref.replace("holeydigsite:", "")
        if "iron_block" in clean:
            return Image.new("RGBA", (16, 16), (220, 220, 220, 255))
        if "black_concrete" in clean:
            return Image.new("RGBA", (16, 16), (20, 21, 24, 255))
        if clean.startswith("minecraft:"):
            return Image.new("RGBA", (16, 16), (128, 128, 128, 255))

        p = os.path.join(ASSETS, "textures", clean + ".png")
        if os.path.exists(p):
            return Image.open(p).convert("RGBA")
        base = clean.split("/")[-1]
        for sub in ["block", "item", "item/lump", "item/chunk"]:
            p2 = os.path.join(ASSETS, "textures", sub, base + ".png")
            if os.path.exists(p2):
                return Image.open(p2).convert("RGBA")
        raise FileNotFoundError(f"Cannot find texture for {ref}")

    mls.tex_image = lambda name, skin=None: resolve_texture(name.replace("|", "/"))

    with open(os.path.join(ASSETS, 'models/block/threshing_drum.json'), encoding='utf-8') as f:
        drum_idle = json.load(f)
    with open(os.path.join(ASSETS, 'models/block/rope_machine.json'), encoding='utf-8') as f:
        rope_idle = json.load(f)
    with open(os.path.join(ASSETS, 'models/block/sawmill.json'), encoding='utf-8') as f:
        sawmill_idle = json.load(f)
    with open(os.path.join(ASSETS, 'models/block/gem_cutter.json'), encoding='utf-8') as f:
        gem_cutter = json.load(f)
    with open(os.path.join(ASSETS, 'models/block/mining_drill_lower.json'), encoding='utf-8') as f:
        drill_base = json.load(f)
    with open(os.path.join(ASSETS, 'models/block/mining_drill_upper_working.json'), encoding='utf-8') as f:
        drill_top = json.load(f)
    with open(os.path.join(ASSETS, 'models/block/clump_belt.json'), encoding='utf-8') as f:
        belt_model = json.load(f)
    belt_tex = {k: v.replace("/", "|") for k, v in belt_model["textures"].items()}

    belt_infeed_part = (belt_model["elements"], belt_tex, 0, 16)
    belt_outfeed_part = (belt_model["elements"], belt_tex, 0, -16)

    cam_side = (2.2, 0.22, 0.001)
    cam_3q = (1.35, 0.75, -1.25)
    r_size = (560, 240)
    centre_line = (8, 14, 8)

    sheet = Image.new("RGB", (1580, 1145), (28, 30, 34))
    draw = ImageDraw.Draw(sheet)

    draw.rectangle([(0, 0), (1580, 60)], fill=(20, 22, 26))
    draw.text((25, 14), "Holey Digsite - Hemp & Timber Machines (1c Palette, Wood, Trim & Cutout Wheel Fix)", fill=(235, 238, 245))
    draw.text((25, 34), "Matched to Gem Cutter Housing, Clump Belt Frame & Sluice Wood Planks (with Gem Cutter & Drill Comparison)", fill=(160, 175, 195))
    draw.text((1200, 20), "v1.33.2  |  1c Asset Preview", fill=(240, 210, 110))

    machine_rows = [
        ("Threshing Drum", drum_idle, [
            "Wood: sluice_wood.png planks",
            "Metal: gem_cutter_housing iron + gold trim",
            "Belt deck: clump_belt_top.png at y=16",
            "Unbroken side stanchions from y=0 to y=27",
            "Symmetric rotor at (8, 24, 8), axis X",
            "Clear opening gap: y=16..20",
            "Water inlet flanges on sides (x=0, 16)"
        ], 70),
        ("Rope Machine", rope_idle, [
            "No checker plate, no big yellow ring/eye",
            "Unbroken wood & iron stanchions (y=0..27)",
            "Upper frame reads as one piece with base",
            "Small gold-trimmed hub & side inlet plate",
            "Cutout 1-piece wheel at (8, 25, 1.5)",
            "Turns cleanly like Gem Cutter wheel",
            "Wound rope bobbin in cradle at y=23.5"
        ], 335),
        ("Sawmill", sawmill_idle, [
            "Wood: sluice_wood.png side/front panels",
            "Table: dark iron & steel bed + gold fence",
            "Cutout 1-piece circular blade at (8, 14, 8)",
            "Gold arbor collar & directional steel teeth",
            "Explicit [0..16] UVs on riving knife guard",
            "Logs in at back (z=16), beams out at front",
            "Water inlet flanges on sides (x=0, 16)"
        ], 600)
    ]

    for title, idle_dict, notes, row_y in machine_rows:
        draw.rectangle([(20, row_y), (1560, row_y + 255)], fill=(34, 38, 44), outline=(55, 62, 72), width=1)
        draw.rectangle([(20, row_y), (350, row_y + 255)], fill=(28, 31, 36))
        draw.text((35, row_y + 12), title, fill=(240, 210, 110))
        for idx, line in enumerate(notes):
            draw.text((35, row_y + 36 + idx * 24), line, fill=(185, 195, 210))

        machine_part = (idle_dict["elements"], {k: v.replace("/", "|") for k, v in idle_dict["textures"].items()}, 0, 0)
        assembly = [belt_outfeed_part, machine_part, belt_infeed_part]

        r_side = mls.render(assembly, cam_side, scale=5.2, size=r_size, centre=centre_line)
        sheet.paste(r_side, (370, row_y + 8), r_side)
        draw.text((375, row_y + 232), "SIDE VIEW: Straight Infeed Belt (z=16) -> Machine -> Outfeed Belt (z=-16) at y=16", fill=(140, 160, 180))

        r_3q = mls.render(assembly, cam_3q, scale=4.4, size=r_size, centre=centre_line)
        sheet.paste(r_3q, (970, row_y + 8), r_3q)
        draw.text((975, row_y + 232), "3/4 VIEW: Same Palette, Wood, Bolts & Gold Trim as Gem Cutter and Drill", fill=(140, 160, 180))

    # Row 4: Reference Comparison Row (Gem Cutter between belts + Mining Drill)
    row_y = 865
    draw.rectangle([(20, row_y), (1560, row_y + 255)], fill=(34, 38, 44), outline=(55, 62, 72), width=1)
    draw.rectangle([(20, row_y), (350, row_y + 255)], fill=(28, 31, 36))
    draw.text((35, row_y + 12), "Reference: Gem Cutter & Drill", fill=(240, 210, 110))
    ref_notes = [
        "Existing mod machines for comparison:",
        "- Gem Cutter (between Clump Belts)",
        "- Mining Drill (sluice_wood + drill steel)",
        "Shared dark iron, steel flanges, rivets,",
        "gold trim stripes, and sluice_wood planks."
    ]
    for idx, line in enumerate(ref_notes):
        draw.text((35, row_y + 36 + idx * 24), line, fill=(185, 195, 210))

    gc_part = (gem_cutter["elements"], {k: v.replace("/", "|") for k, v in gem_cutter["textures"].items()}, 0, 0)
    gc_assembly = [belt_outfeed_part, gc_part, belt_infeed_part]
    r_gc_3q = mls.render(gc_assembly, cam_3q, scale=4.4, size=r_size, centre=centre_line)
    sheet.paste(r_gc_3q, (370, row_y + 8), r_gc_3q)
    draw.text((375, row_y + 232), "REFERENCE 1: Gem Cutter Between Straight Clump Belts (3/4 View)", fill=(140, 160, 180))

    drill_top_shifted = []
    for el in drill_top["elements"]:
        el2 = dict(el)
        el2["from"] = [el["from"][0], el["from"][1] + 16, el["from"][2]]
        el2["to"] = [el["to"][0], el["to"][1] + 16, el["to"][2]]
        drill_top_shifted.append(el2)
    drill_tex = {k: v.replace("/", "|") for k, v in {**drill_base["textures"], **drill_top["textures"]}.items()}
    drill_assembly = [
        (drill_base["elements"] + drill_top_shifted, drill_tex, 0, 0),
        (gem_cutter["elements"], {k: v.replace("/", "|") for k, v in gem_cutter["textures"].items()}, 0, -20)
    ]
    r_drill = mls.render(drill_assembly, cam_3q, scale=4.0, size=r_size, centre=(8, 16, -2))
    sheet.paste(r_drill, (970, row_y + 8), r_drill)
    draw.text((975, row_y + 232), "REFERENCE 2: Mining Drill & Gem Cutter Side-by-Side (Wood & Steel Reference)", fill=(140, 160, 180))

    out_1c = os.path.join(PREVIEW_DIR, 'hemp_and_timber_1c.png')
    sheet.save(out_1c)
    print(f"Generated 3D preview sheet: {out_1c} ({sheet.size})")


def main():
    print("Generating Phase 36 1c assets...")
    make_item_textures()
    make_crop_textures()
    make_machine_textures()
    make_models()
    make_preview_sheet()

    pbr_script = os.path.join(ROOT, 'tools/make_pbr.py')
    if os.path.exists(pbr_script):
        print("Running make_pbr.py...")
        import subprocess
        subprocess.run([sys.executable, pbr_script], check=True)

    print("Phase 36 1c assets generation complete!")


if __name__ == '__main__':
    main()
