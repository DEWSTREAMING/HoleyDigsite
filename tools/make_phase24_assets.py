"""
Contract Board assets: the 16x16 texture, the block model (frame, cork, three pinned notes), blockstate, item model and loot table.

Usage: python tools/make_phase24_assets.py   (run from the project folder)
"""

import json
import os
import random

from PIL import Image

RES = "src/main/resources"
ASSETS = os.path.join(RES, "assets/holeydigsite")
DATA = os.path.join(RES, "data/holeydigsite")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def texture():
    rnd = random.Random(24)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 255))
    px = img.load()
    # top left: dark wood with grain
    for y in range(8):
        for x in range(8):
            base = (104, 72, 42) if y % 3 else (92, 62, 36)
            j = rnd.randint(-6, 6)
            px[x, y] = (base[0] + j, base[1] + j, base[2] + j, 255)
    # top right: cork
    for y in range(8):
        for x in range(8, 16):
            base = (192, 150, 96)
            j = rnd.randint(-14, 10)
            px[x, y] = (base[0] + j, base[1] + j, base[2] + j // 2, 255)
    # bottom left: paper with ink lines
    for y in range(8, 16):
        for x in range(8):
            c = (234, 224, 198)
            if (y - 8) % 2 == 1 and 1 <= x <= 6:
                c = (150, 132, 108)
            px[x, y] = c + (255,)
    # bottom right: pin red and a darker rim
    for y in range(8, 16):
        for x in range(8, 16):
            px[x, y] = (196, 48, 38, 255) if (x + y) % 5 else (150, 30, 26, 255)
    path = os.path.join(ASSETS, "textures/block/contract_board.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def face(uv):
    return {"uv": uv, "texture": "#0"}


def box(frm, to, uv, faces=("north", "south", "east", "west", "up", "down")):
    return {"from": frm, "to": to, "faces": {f: face(uv) for f in faces}}


def model():
    wood = [0, 0, 8, 8]
    els = [
        box([0, 14, 13], [16, 16, 16], wood),
        box([0, 0, 13], [16, 2, 16], wood),
        box([0, 2, 13], [2, 14, 16], wood),
        box([14, 2, 13], [16, 14, 16], wood),
        box([2, 2, 14.5], [14, 14, 16], [8, 0, 16, 8], ("north",)),
        box([3, 8, 14.1], [8, 13, 14.5], [0, 8, 5, 13], ("north", "up")),
        box([9, 9, 14.1], [13.5, 13, 14.5], [0, 8, 4.5, 12], ("north", "up")),
        box([5, 3, 14.1], [11, 8, 14.5], [0, 8, 6, 13], ("north", "up")),
        box([5, 12, 13.9], [6, 13, 14.1], [8, 8, 9, 9], ("north",)),
        box([10.5, 12, 13.9], [11.5, 13, 14.1], [8, 8, 9, 9], ("north",)),
        box([7.5, 7, 13.9], [8.5, 8, 14.1], [8, 8, 9, 9], ("north",)),
    ]
    write_json(os.path.join(ASSETS, "models/block/contract_board.json"), {
        "parent": "minecraft:block/block",
        "textures": {"0": "holeydigsite:block/contract_board", "particle": "holeydigsite:block/contract_board"},
        "elements": els,
    })
    write_json(os.path.join(ASSETS, "blockstates/contract_board.json"), {"variants": {
        "facing=north": {"model": "holeydigsite:block/contract_board"},
        "facing=east": {"model": "holeydigsite:block/contract_board", "y": 90},
        "facing=south": {"model": "holeydigsite:block/contract_board", "y": 180},
        "facing=west": {"model": "holeydigsite:block/contract_board", "y": 270},
    }})
    write_json(os.path.join(ASSETS, "models/item/contract_board.json"), {"parent": "holeydigsite:block/contract_board"})
    write_json(os.path.join(DATA, "loot_table/blocks/contract_board.json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "holeydigsite:contract_board"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": "holeydigsite:blocks/contract_board",
    })


if __name__ == '__main__':
    texture()
    model()
    print("contract board assets written")
