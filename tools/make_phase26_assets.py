"""
Skin Kit assets: the flat paint-tin item texture (used for a blank kit and as the particle), the small tin drawn in the
corner of a kit's slot, and the item model.

Usage: python tools/make_phase26_assets.py   (run from the project folder)
"""

import json
import os

from PIL import Image

ASSETS = "src/main/resources/assets/holeydigsite"


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


CLEAR = (0, 0, 0, 0)
OUT = hexc('#2B2118')       # outline
TIN = hexc('#9EA4AB')       # tin body
TIN_L = hexc('#C9CED3')     # highlight
TIN_D = hexc('#6C7279')     # shadow
LID = hexc('#7E848B')
PAINT = hexc('#C8452F')     # paint drip / label
PAINT_L = hexc('#E8754F')
STENCIL = hexc('#E3B22E')   # brass stencil band


def tin16():
    img = Image.new('RGBA', (16, 16), CLEAR)
    px = img.load()
    # body x 3..12, y 5..14
    for y in range(5, 15):
        for x in range(3, 13):
            c = TIN
            if x == 3 or x == 4:
                c = TIN_L
            if x >= 11:
                c = TIN_D
            px[x, y] = c
    # label band y 8..11
    for y in range(8, 12):
        for x in range(3, 13):
            px[x, y] = STENCIL if y in (8, 11) else PAINT
    for x in (5, 7, 9):
        px[x, 9] = PAINT_L
        px[x + 1, 10] = PAINT_L
    # lid y 3..4
    for x in range(2, 14):
        px[x, 3] = LID
        px[x, 4] = TIN_L if x < 6 else LID
    # paint drip over the rim
    for (x, y) in ((6, 4), (7, 4), (7, 5), (7, 6), (10, 4), (10, 5)):
        px[x, y] = PAINT
    # handle
    for x in range(5, 11):
        px[x, 1] = OUT
    px[4, 2] = OUT
    px[11, 2] = OUT
    # outline
    for y in range(3, 15):
        px[1 if y < 5 else 2, y] = OUT
        px[14 if y < 5 else 13, y] = OUT
    for x in range(1, 15):
        px[x, 2] = OUT if 1 <= x <= 14 and (x <= 4 or x >= 11) else px[x, 2]
    for x in range(2, 14):
        px[x, 15] = OUT
    px[1, 2] = OUT
    px[14, 2] = OUT
    for x in range(2, 14):
        px[x, 2] = OUT if px[x, 2] == CLEAR else px[x, 2]
    return img


def tin8():
    img = Image.new('RGBA', (8, 8), CLEAR)
    px = img.load()
    for y in range(2, 8):
        for x in range(1, 7):
            px[x, y] = OUT
    for y in range(3, 7):
        for x in range(2, 6):
            px[x, y] = TIN_L if x == 2 else (TIN_D if x == 5 else TIN)
    for x in range(2, 6):
        px[x, 4] = PAINT
        px[x, 5] = PAINT if x != 3 else PAINT_L
    for x in range(2, 6):
        px[x, 1] = OUT
    px[3, 2] = PAINT
    return img


def main():
    os.makedirs(os.path.join(ASSETS, "textures/item"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "textures/gui"), exist_ok=True)
    tin16().save(os.path.join(ASSETS, "textures/item/skin_kit.png"))
    tin8().save(os.path.join(ASSETS, "textures/gui/skin_kit_tin.png"))
    model = {"parent": "minecraft:item/generated", "textures": {"layer0": "holeydigsite:item/skin_kit"}}
    path = os.path.join(ASSETS, "models/item/skin_kit.json")
    with open(path, 'w', newline='\n') as f:
        json.dump(model, f, indent=2)
        f.write('\n')
    print("skin kit assets written")


if __name__ == '__main__':
    main()
