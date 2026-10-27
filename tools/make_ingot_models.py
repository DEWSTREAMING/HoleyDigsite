"""
3D ingot models and textures.

Usage: python tools/make_ingot_models.py   (run from the project folder; running it twice gives identical files)

Every ingot item has four sizes picked by weight (IngotItem.sizeStep: under 40 g, 40-89, 90-249, 250 and up). The shapes are
shared (item/ingot_size_N.json); each metal has a texture (textures/item/ingot/<metal>.png) and one model per size that
points at it. The base model of a metal is size 0 and carries the overrides for sizes 1-3. A new metal is one entry in METALS.

The bar lies along x, bottom at y = BOTTOM. Renderers that lay ingots flat (Smithing Anvil, Crafting Forge) rely on that
and on the fixed transform below, see BAR_BOTTOM / FIXED_SCALE in the renderers.
"""

import json
import math
import os

from PIL import Image

RES = "src/main/resources/assets/holeydigsite"
MODELS = os.path.join(RES, "models/item")
TEX = os.path.join(RES, "textures/item/ingot")

BOTTOM = 6.0
GUI_SCALE = 0.9

# base colour, highlight, shine pixels (more = shinier), shine strength
METALS = {
    'folkiron': dict(base=(132, 128, 130), hi=(172, 166, 164), shine=2, glint=0.18),
    'goldchro': dict(base=(232, 190, 60), hi=(255, 236, 130), shine=6, glint=0.45),
    'orchremia': dict(base=(188, 104, 46), hi=(226, 152, 84), shine=3, glint=0.25),
    'silvane': dict(base=(214, 222, 232), hi=(250, 252, 255), shine=6, glint=0.5),
}

# length (x), width (z) of the bottom layer, thickness of each layer, inset per layer on each side along x and z
SIZES = [
    dict(L=7.0, W=4.0, th=[1.5, 1.5], ix=0.5, iz=0.5),
    dict(L=10.0, W=6.0, th=[1.5, 1.5, 1.0], ix=0.75, iz=0.5),
    dict(L=12.0, W=7.0, th=[2.0, 1.5, 1.5], ix=0.75, iz=0.5),
    dict(L=15.0, W=10.0, th=[2.5, 2.5, 2.5], ix=1.0, iz=0.75),
]

# texture regions (rows): top 0-5, long sides 6-10, ends 11-15
UV_TOP = [0, 0, 16, 6]
UV_SIDE = [0, 6, 16, 11]
UV_END = [0, 11, 16, 16]


def num(v):
    return int(v) if float(v).is_integer() else v


def layers(s):
    y = BOTTOM
    out = []
    for i, t in enumerate(s['th']):
        x0 = 8 - s['L'] / 2 + i * s['ix']
        x1 = 8 + s['L'] / 2 - i * s['ix']
        z0 = 8 - s['W'] / 2 + i * s['iz']
        z1 = 8 + s['W'] / 2 - i * s['iz']
        out.append((x0, y, z0, x1, y + t, z1))
        y += t
    return out


def height(s):
    return sum(s['th'])


def shape_model(n):
    s = SIZES[n]
    els = []
    for i, (x0, y0, z0, x1, y1, z1) in enumerate(layers(s)):
        faces = {
            "up": {"uv": UV_TOP, "texture": "#bar"},
            "north": {"uv": UV_SIDE, "texture": "#bar"},
            "south": {"uv": UV_SIDE, "texture": "#bar"},
            "east": {"uv": UV_END, "texture": "#bar"},
            "west": {"uv": UV_END, "texture": "#bar"},
        }
        if i == 0:
            faces["down"] = {"uv": UV_END, "texture": "#bar"}
        els.append({"from": [num(x0), num(y0), num(z0)], "to": [num(x1), num(y1), num(z1)], "faces": faces})
    cy = BOTTOM + height(s) / 2
    c30 = math.cos(math.radians(30))
    gui_ty = round(-(cy - 8) * c30 * GUI_SCALE, 3)
    hand_ty = round(-(cy - 8) * 0.55, 3)
    third_ty = round(-(cy - 8) * 0.5 + 1.5, 3)
    ground_ty = round(-(cy - 8) * 0.5 + 2.0, 3)
    return {
        "textures": {"particle": "#bar"},
        "elements": els,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, gui_ty, 0], "scale": [GUI_SCALE] * 3},
            "ground": {"rotation": [0, 0, 0], "translation": [0, ground_ty, 0], "scale": [0.5] * 3},
            "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, third_ty, 0], "scale": [0.5] * 3},
            "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, third_ty, 0], "scale": [0.5] * 3},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, hand_ty, 0], "scale": [0.55] * 3},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, hand_ty, 0], "scale": [0.55] * 3},
        },
    }


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def texture(m):
    base, hi = m['base'], m['hi']
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    top, top_edge = base, shade(base, 0.9)
    side, side_top = shade(base, 0.82), mix(base, hi, 0.5)
    end, end_top = shade(base, 0.68), shade(base, 0.8)
    for y in range(16):
        for x in range(16):
            if y < 6:
                c = top
                if y == 1:
                    c = hi
                elif y == 2:
                    c = mix(base, hi, 0.35)
                elif y == 5:
                    c = top_edge
                if x == 0 or x == 15:
                    c = mix(c, hi, 0.4) if y == 1 else shade(c, 0.93)
            elif y < 11:
                c = side_top if y == 6 else side
                if y == 10:
                    c = shade(side, 0.88)
            else:
                c = end_top if y == 11 else end
                if y == 15:
                    c = shade(end, 0.85)
            px[x, y] = c + (255,)
    # shine pixels on the top and the upper side edge, spread by a fixed pattern
    spots = [(3, 3), (11, 2), (7, 4), (13, 3), (5, 1), (9, 3), (2, 7), (12, 7), (6, 7)]
    glint = mix(hi, (255, 255, 255), m['glint'])
    for x, y in spots[:m['shine'] + 1]:
        px[x, y] = glint + (255,)
    return img


def mint_texture():
    """Stamped Mint Bar: pale white-gold, a raised border and a star stamp on top, serial ridges on the long side."""
    m = dict(base=(238, 226, 176), hi=(255, 250, 222), shine=5, glint=0.5)
    img = texture(m)
    px = img.load()
    dark, stamp = (150, 128, 70), (255, 255, 240)
    for x in range(1, 15):
        px[x, 0] = dark + (255,)
        px[x, 5] = dark + (255,)
    for y in range(0, 6):
        px[1, y] = dark + (255,)
        px[14, y] = dark + (255,)
    star = [(8, 1), (8, 2), (7, 2), (9, 2), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (7, 4), (9, 4)]
    for p in star:
        px[p] = stamp + (255,)
    for p in [(8, 2), (8, 3)]:
        px[p] = dark + (255,)
    for x in (3, 5, 11, 13):
        for y in (7, 8, 9):
            px[x, y] = dark + (255,)
    return img


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def main():
    for n in range(len(SIZES)):
        write_json(os.path.join(MODELS, f'ingot_size_{n}.json'), shape_model(n))
    os.makedirs(TEX, exist_ok=True)
    for metal, m in METALS.items():
        texture(m).save(os.path.join(TEX, metal + '.png'))
        tex = {"bar": f"holeydigsite:item/ingot/{metal}"}
        for n in range(1, len(SIZES)):
            write_json(os.path.join(MODELS, f'{metal}_ingot_{n}.json'),
                       {"parent": f"holeydigsite:item/ingot_size_{n}", "textures": tex})
        write_json(os.path.join(MODELS, f'{metal}_ingot.json'), {
            "parent": "holeydigsite:item/ingot_size_0",
            "textures": tex,
            "overrides": [{"predicate": {"holeydigsite:size": n}, "model": f"holeydigsite:item/{metal}_ingot_{n}"}
                          for n in range(1, len(SIZES))],
        })
    mint_texture().save(os.path.join(TEX, 'mint_bar.png'))
    write_json(os.path.join(MODELS, 'mint_bar.json'),
               {"parent": "holeydigsite:item/ingot_size_2", "textures": {"bar": "holeydigsite:item/ingot/mint_bar"}})
    print('ingot models written')


if __name__ == '__main__':
    main()
