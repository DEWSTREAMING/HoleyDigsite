"""
3D models and textures for the clumps (dirt, gravel, sand) and the metal chunks.

Usage: python tools/make_lump_models.py   (run from the project folder; running it twice gives identical files)

Clumps: models/item/dirt_clump.json (with the overrides on holeydigsite:tier), dirt_clump_<kind>.json, gravel_clump.json and
sand_clump.json, each a full model of 4-6 cuboids. Textures are textures/item/lump/<kind>.png.
Chunks: <metal>_chunk_small|medium|large.json point at the shared shapes item/chunk_shape_<size>.json; textures are
textures/item/chunk/<metal>.png.

Every shape stands on y = BOTTOM and is centred on x/z 8, like the ingots, so the renderers can lift all flat-lying items by
the same amount (scale / 16). Faces have no explicit uv, so the textures run on across the cuboids. The lower rows of each
texture are darker (that is what the lowest cuboid shows).

Older generators (make_phase4/11/21f) wrote the flat sprites and models of these items; do not run them again for these items.
"""

import json
import math
import os
import random

from PIL import Image

RES = "src/main/resources/assets/holeydigsite"
MODELS = os.path.join(RES, "models/item")
LUMP_TEX = os.path.join(RES, "textures/item/lump")
CHUNK_TEX = os.path.join(RES, "textures/item/chunk")

BOTTOM = 6.0
GUI_SCALE = 0.85

# (x0, z0, x1, z1, y0 above bottom, y1 above bottom)
CLUMP = [
    (2, 3, 14, 13, 0, 3),
    (4, 4.5, 11, 11.5, 3, 5.5),
    (9, 8, 13, 12, 3, 4.5),
    (3, 8.5, 6, 12, 3, 4.5),
    (5, 1.5, 10, 3, 0, 2),
]
# kinds add one or two pieces to the base shape (never more than 6 cuboids in all)
CLUMP_EXTRA = {
    'dirt': [],
    'coarse': [],
    'deep': [],
    'rooted': [(10, 1.5, 12, 3, 0, 4)],     # a root poking out of the front
    'moss': [(3, 4, 9, 9, 5.5, 6.5)],         # moss cushion on top
    'abyssal': [(6, 5, 8, 7, 5.5, 7)],        # a jagged tip
    'gravel': [(6, 5, 9, 7.5, 5.5, 6.5)],
    'sand': [],
}
# sand is a lower, flatter pile
CLUMP_FLAT = {'sand'}

CLUMP_KINDS = {
    # pal: dark, mid, light, deep speck
    'dirt': dict(pal=[(78, 52, 34), (100, 70, 47), (122, 88, 60), (60, 40, 28)], seed=1),
    'coarse': dict(pal=[(104, 88, 72), (130, 112, 92), (154, 136, 116), (116, 98, 80)], seed=2, speck=(176, 164, 150), specks=14),
    'rooted': dict(pal=[(108, 58, 38), (136, 74, 50), (160, 90, 62), (112, 60, 38)], seed=3, roots=True),
    'moss': dict(pal=[(34, 62, 22), (52, 92, 34), (80, 128, 50), (118, 170, 66)], seed=4, patch=(112, 168, 64)),
    'deep': dict(pal=[(38, 26, 20), (58, 42, 32), (78, 58, 44), (30, 20, 16)], seed=5),
    'abyssal': dict(pal=[(14, 10, 12), (26, 20, 24), (40, 32, 40), (10, 8, 10)], seed=6, speck=(90, 60, 130), specks=8),
    'gravel': dict(pal=[(92, 90, 90), (122, 120, 118), (150, 148, 146), (66, 64, 66)], seed=7, speck=(176, 174, 170), specks=22),
    'sand': dict(pal=[(190, 170, 118), (212, 192, 138), (228, 210, 160), (170, 150, 102)], seed=8),
}

# dirt_clump overrides on the tier property
TIER_KINDS = {2: 'deep', 3: 'abyssal', 6: 'coarse', 7: 'rooted', 8: 'moss'}

# base colour, highlight, shine pixels, shine strength (same metals as the ingots)
METALS = {
    'folkiron': dict(base=(132, 128, 130), hi=(172, 166, 164), shine=2, glint=0.18),
    'goldchro': dict(base=(232, 190, 60), hi=(255, 236, 130), shine=6, glint=0.45),
    'orchremia': dict(base=(188, 104, 46), hi=(226, 152, 84), shine=3, glint=0.25),
    'silvane': dict(base=(214, 222, 232), hi=(250, 252, 255), shine=6, glint=0.5),
}

# chunk shapes: (x0, z0, x1, z1, y0, y1) above bottom, small to large
CHUNKS = {
    'small': [(5.5, 6, 10, 10, 0, 2), (6.5, 6.5, 9.5, 9, 2, 3.5), (10, 8, 11.5, 9.5, 0, 1.5)],
    'medium': [(4, 5, 11.5, 11, 0, 3), (5, 6, 10.5, 10.5, 3, 5), (9, 2.5, 12, 5, 0, 2), (3, 8.5, 5.5, 11.5, 3, 4.5)],
    'large': [(2.5, 3.5, 13, 12.5, 0, 4), (4, 4.5, 11.5, 11.5, 4, 7), (6, 5.5, 10, 9.5, 7, 8.5),
              (10.5, 0.5, 14, 3.5, 0, 3), (2, 9, 5, 13, 4, 6), (9, 10.5, 12.5, 13.5, 4, 5.5)],
}
CHUNK_SCALE = {'small': 0.9, 'medium': 0.9, 'large': 0.85}


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


def display(boxes, gui_scale):
    top = max(b for *_, b in boxes)
    cy = BOTTOM + top / 2
    c30 = math.cos(math.radians(30))
    gui_ty = round(-(cy - 8) * c30 * gui_scale, 3)
    hand_ty = round(-(cy - 8) * 0.55, 3)
    third_ty = round(-(cy - 8) * 0.5 + 1.5, 3)
    # the bottom sits where the flat sprite's bottom did, so the belt lift stays the same
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


def clump_boxes(kind):
    boxes = list(CLUMP)
    if kind in CLUMP_FLAT:
        boxes = [(x0, z0, x1, z1, a * 0.7, b * 0.7) for (x0, z0, x1, z1, a, b) in boxes]
    return boxes + list(CLUMP_EXTRA[kind])


def clump_model(kind):
    boxes = clump_boxes(kind)
    return {"textures": {"particle": "#t", "t": f"holeydigsite:item/lump/{kind}"},
            "elements": elements(boxes), "display": display(boxes, GUI_SCALE)}


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def clump_texture(k):
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
            px[x, y] = shade(k['speck'], 0.8 if y >= 8 else 1.0) + (255,)
    if k.get('roots'):
        # pale roots running over the faces
        for x in range(16):
            for y in (2, 7, 12):
                yy = y + (1 if x % 5 < 2 else 0)
                px[x, yy] = (176, 140, 98, 255) if yy < 8 else (140, 108, 74, 255)
        for y in range(16):
            px[3, y] = (176, 140, 98, 255) if y < 8 else (140, 108, 74, 255)
    if 'patch' in k:
        for _ in range(26):
            x, y = rng.randrange(16), rng.randrange(8)
            px[x, y] = mix(k['patch'], (200, 230, 120), 0.3) + (255,)
    return img


def chunk_texture(seed, m):
    rng = random.Random(seed)
    base, hi = m['base'], m['hi']
    rock = (88, 80, 74)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = rng.random()
            c = base
            if r < 0.14:
                c = rock
            elif r < 0.30:
                c = shade(base, 0.82)
            elif r > 0.86:
                c = mix(base, hi, 0.55)
            if y < 6:
                c = mix(c, hi, 0.2)
            if y >= 8:
                c = shade(c, 0.78 if y < 12 else 0.66)
            px[x, y] = c + (255,)
    glint = mix(hi, (255, 255, 255), m['glint'])
    spots = [(3, 2), (11, 1), (7, 3), (13, 4), (5, 1), (9, 4), (2, 5), (12, 6), (6, 5)]
    for x, y in spots[:m['shine'] + 1]:
        px[x, y] = glint + (255,)
    return img


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def main():
    os.makedirs(LUMP_TEX, exist_ok=True)
    os.makedirs(CHUNK_TEX, exist_ok=True)
    for kind, k in CLUMP_KINDS.items():
        clump_texture(k).save(os.path.join(LUMP_TEX, kind + '.png'))
        name = {'dirt': 'dirt_clump', 'gravel': 'gravel_clump', 'sand': 'sand_clump'}.get(kind, f'dirt_clump_{kind}')
        model = clump_model(kind)
        if kind == 'dirt':
            model["overrides"] = [{"predicate": {"holeydigsite:tier": t}, "model": f"holeydigsite:item/dirt_clump_{kinds}"}
                                  for t, kinds in sorted(TIER_KINDS.items())]
        write_json(os.path.join(MODELS, name + '.json'), model)
    for size, boxes in CHUNKS.items():
        write_json(os.path.join(MODELS, f'chunk_shape_{size}.json'),
                   {"textures": {"particle": "#t"}, "elements": elements(boxes),
                    "display": display(boxes, CHUNK_SCALE[size])})
    for i, (metal, m) in enumerate(METALS.items()):
        chunk_texture(100 + i, m).save(os.path.join(CHUNK_TEX, metal + '.png'))
        for size in CHUNKS:
            write_json(os.path.join(MODELS, f'{metal}_chunk_{size}.json'),
                       {"parent": f"holeydigsite:item/chunk_shape_{size}",
                        "textures": {"t": f"holeydigsite:item/chunk/{metal}"}})
    print('lump models written')


if __name__ == '__main__':
    main()
