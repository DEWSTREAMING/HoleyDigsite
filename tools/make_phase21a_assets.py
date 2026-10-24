"""Phase 21a assets (deterministic, Pillow): Prospector's Ledger item texture and model.

Run from the project root:  python tools/make_phase21a_assets.py
"""
import json
import os
from PIL import Image

ROOT = os.path.join('src', 'main', 'resources', 'assets', 'holeydigsite')
TEX_I = os.path.join(ROOT, 'textures', 'item')


def prospectors_ledger():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    leather_dark = (74, 44, 24, 255)
    leather = (118, 72, 38, 255)
    leather_hi = (148, 94, 52, 255)
    page = (236, 224, 194, 255)
    page_edge = (198, 182, 148, 255)
    gold_dark = (176, 126, 28, 255)
    gold = (232, 184, 54, 255)
    gold_hi = (255, 228, 118, 255)
    ribbon = (176, 38, 42, 255)

    # Book cover (x: 3..12, y: 2..13)
    for y in range(2, 14):
        for x in range(3, 13):
            if x == 3 or y == 13:
                im.putpixel((x, y), leather_dark)
            elif x == 4 or y == 2:
                im.putpixel((x, y), leather_hi)
            else:
                im.putpixel((x, y), leather)

    # Pages showing on right edge (x=12, y=3..12) and bottom (x=5..12, y=12)
    for y in range(3, 13):
        im.putpixel((12, y), page if y % 2 == 0 else page_edge)
    for x in range(5, 12):
        im.putpixel((x, 12), page)

    # Spine ridges on left (x=3)
    for y in (4, 7, 10):
        im.putpixel((3, y), gold_dark)
        im.putpixel((4, y), gold)

    # Gold corner clasps (top-right and bottom-right of cover)
    for p in [(10, 2), (11, 2), (11, 3), (10, 11), (11, 11)]:
        im.putpixel(p, gold)

    # Center Goldchro/pan emblem on cover (x: 6..9, y: 5..8)
    for x in range(6, 10):
        for y in range(5, 9):
            if (x in (6, 9) and y in (5, 8)):
                continue
            im.putpixel((x, y), gold_dark)
    im.putpixel((7, 6), gold_hi)
    im.putpixel((8, 6), gold)
    im.putpixel((7, 7), gold)
    im.putpixel((8, 7), gold_dark)

    # Red bookmark ribbon hanging at bottom (x=7, y=13..15)
    im.putpixel((7, 13), ribbon)
    im.putpixel((7, 14), ribbon)
    im.putpixel((8, 14), ribbon)
    return im


def main():
    os.makedirs(TEX_I, exist_ok=True)
    prospectors_ledger().save(os.path.join(TEX_I, 'prospectors_ledger.png'))
    model_path = os.path.join(ROOT, 'models', 'item', 'prospectors_ledger.json')
    os.makedirs(os.path.dirname(model_path), exist_ok=True)
    with open(model_path, 'w', encoding='utf-8') as f:
        json.dump({
            'parent': 'minecraft:item/generated',
            'textures': {'layer0': 'holeydigsite:item/prospectors_ledger'}
        }, f, indent=2)
        f.write('\n')


if __name__ == '__main__':
    main()
