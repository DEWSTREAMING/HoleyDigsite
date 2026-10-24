#!/usr/bin/env python3
"""Phase 19 assets (Pillow): Smithing Anvil block model + textures, Blacksmith Hammer, the five smithed pieces.

Pieces are drawn in grey tones (the client tints them with the main metal's colour, the gem layer with the gem's colour), the hammer
and the anvil are fully coloured. Deterministic; `python tools/make_phase19_assets.py [preview]` (preview writes a 8x sheet to the temp dir).
"""
import json
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(ROOT, 'src/main/resources/assets/holeydigsite/textures')
MODELS = os.path.join(ROOT, 'src/main/resources/assets/holeydigsite/models')


def new():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def put(im, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def g(v):
    return (v, v, v)


HI, MID, LO, DARK, EDGE = g(250), g(208), g(160), g(118), g(84)


def line(im, p0, p1, c):
    (x0, y0), (x1, y1) = p0, p1
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        t = i / n if n else 0
        put(im, round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)


def outline(im, c=EDGE):
    """Dark pixels around everything painted (4-neighbourhood)."""
    src = im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        put(im, x, y, c)
                        break


# ---------------------------------------------------------------------------------------------- layered pieces (Phase 19.1)
# Each piece is split into layers; layer N is tinted by the client with the ingot / gem of that slot (see SmithColors).
LEATHER = [(70, 44, 24), (104, 66, 36), (136, 90, 50)]
WOODEN = [(96, 64, 32), (128, 88, 44), (160, 116, 64)]


class Layers:
    """Several 16x16 layers; painting a pixel in one layer clears it in the others."""

    def __init__(self, *names):
        self.im = {n: new() for n in names}

    def view(self, name):
        return _View(self, name)

    def clear(self, x, y):
        for im in self.im.values():
            im.putpixel((x, y), (0, 0, 0, 0))

    def painted(self, x, y):
        return any(im.getpixel((x, y))[3] > 0 for im in self.im.values())

    def outline(self, c=EDGE, layer=None):
        """Dark pixels around everything painted; each goes to `layer`, or to the layer of the neighbour that caused it."""
        add = []
        for y in range(16):
            for x in range(16):
                if self.painted(x, y):
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and self.painted(nx, ny):
                        owner = layer or next(n for n, im in self.im.items() if im.getpixel((nx, ny))[3] > 0)
                        add.append((owner, x, y))
                        break
        for owner, x, y in add:
            put(self.im[owner], x, y, c)


class _View:
    def __init__(self, layers, name):
        self.layers, self.name = layers, name

    def putpixel(self, xy, c):
        self.layers.clear(*xy)
        self.layers.im[self.name].putpixel(xy, c)


def ring():
    L = Layers('band')
    im = L.view('band')
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 10.0) ** 2) ** 0.5
            if 2.7 <= d <= 5.2:
                s = (x - 7.5) + (y - 10.0)         # light from the upper left
                put(im, x, y, HI if s < -3 else MID if s < 1.5 else LO)
    for x in (6, 7, 8, 9):          # collar under the gem
        put(im, x, 5, MID)
        put(im, x, 6, LO)
    put(im, 6, 5, HI)
    L.outline()
    return L.im


def ring_gem():
    im = new()
    rows = {2: (7, 8), 3: (6, 7, 8, 9), 4: (6, 7, 8, 9)}
    for y, xs in rows.items():
        for x in xs:
            put(im, x, y, HI if (x + y) % 3 == 0 else MID if x < 8 else LO)
    put(im, 7, 3, g(255))
    outline(im, DARK)
    return im


def necklace():
    L = Layers('chain', 'setting', 'gem')
    chain, setting, gem = L.view('chain'), L.view('setting'), L.view('gem')
    for x in range(2, 14):
        y = round(9 - 7 * ((x - 7.5) / 5.5) ** 2)
        put(chain, x, y, HI if (x % 2 == 0) else MID)
        put(chain, x, y + 1, LO)
    rows = {11: (7, 8), 12: (6, 7, 8, 9), 13: (6, 7, 8, 9), 14: (7, 8)}
    for y, xs in rows.items():
        for x in xs:
            put(gem, x, y, HI if (x + y) % 3 == 0 else MID if x < 8 else LO)
    put(gem, 7, 12, g(255))
    for x, y, c in ((6, 10, HI), (7, 10, MID), (8, 10, MID), (9, 10, LO),            # pendant setting: bezel round the gem
                    (6, 11, HI), (9, 11, LO), (5, 12, HI), (5, 13, MID), (10, 12, LO), (10, 13, LO),
                    (6, 14, MID), (9, 14, LO), (7, 15, MID), (8, 15, LO)):
        put(setting, x, y, c)
    L.outline(layer='setting')
    return L.im


# ---------------------------------------------------------------------------------------------- weapons / tool
S2 = 2 ** 0.5


def rect(im, c, u0, u1, v0, v1, color):
    """Paints the pixels whose centre lies in a rectangle rotated 45 degrees: v runs along the (1, -1) diagonal (up-right, the
    way a tool points), u across it. (cx, cy) is the origin; `color(u, v)` gives the pixel colour."""
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - c[0], y + 0.5 - c[1]
            u = (dx + dy) / S2
            v = (dx - dy) / S2
            if u0 <= u <= u1 and v0 <= v <= v1:
                put(im, x, y, color(u, v))


def band_shade(u0, u1):
    third = (u1 - u0) / 3
    return lambda u, v: HI if u < u0 + third else MID if u < u0 + 2 * third else LO


def blade_piece(length, half_width, sword_parts):
    """A sword / dagger pointing up-right. Sword: blade / guard / pommel (ingots 1 / 2 / 3) + leather grip. Dagger: blade + guard
    (ingot 1) + leather grip with a leather end cap."""
    if sword_parts:
        L = Layers('blade', 'guard', 'pommel', 'grip')
        guard, pommel = L.view('guard'), L.view('pommel')
    else:
        L = Layers('blade', 'grip')
        guard, pommel = L.view('blade'), L.view('grip')
    blade, grip = L.view('blade'), L.view('grip')
    c = (3.0, 13.0)                      # the guard sits here; v grows towards the tip
    rect(blade, c, -half_width, half_width, 0.6, 0.6 + length, band_shade(-half_width, half_width))
    # a pointed tip: remove the far corner pixels
    tipx = c[0] + (0.6 + length) / S2
    tipy = c[1] - (0.6 + length) / S2
    for dx, dy in ((0.5, -0.5), (-0.5, 0.5)):
        x, y = int(tipx + dx), int(tipy + dy)
        if 0 <= x < 16 and 0 <= y < 16 and abs(dx) > 1:
            L.clear(x, y)
    rect(guard, c, -3.0, 3.0, -0.4, 0.6, lambda u, v: HI if u < -1 else MID if u < 1.5 else LO)           # crossguard
    rect(grip, c, -0.6, 0.6, -3.0, -0.4, lambda u, v: LEATHER[2] if v > -2 else LEATHER[0])               # leather grip
    rect(pommel, c, -0.9, 0.9, -3.9, -2.9, (lambda u, v: MID) if sword_parts else (lambda u, v: LEATHER[1]))
    L.outline()
    return L.im


def sword():
    return blade_piece(14.0, 1.3, True)


def dagger():
    return blade_piece(8.8, 1.1, False)


def axe():
    L = Layers('head', 'binding', 'handle')
    c = (2.0, 14.0)
    rect(L.view('handle'), c, -0.6, 0.6, 0.0, 14.0, lambda u, v: WOODEN[0] if u > 0 else WOODEN[1])      # wooden handle
    rect(L.view('binding'), c, -0.9, 0.9, 9.4, 11.0, lambda u, v: MID)                                   # binding below the head
    hc = (11.5, 4.5)
    rect(L.view('head'), hc, -3.9, 2.6, -2.3, 1.6, lambda u, v: HI if u < -1.6 else MID if u < 0.6 else LO)   # head
    rect(L.view('head'), hc, -4.3, -2.8, 1.0, 2.6, lambda u, v: HI)                                      # blade edge sticking out
    rect(L.view('binding'), hc, 0.6, 2.6, -2.3, 1.6, lambda u, v: LO if u > 1.6 else MID)                # cheek / poll at the back
    L.outline()
    return L.im


# ---------------------------------------------------------------------------------------------- hammer (coloured)
WOOD = [(96, 64, 32), (128, 88, 44), (160, 116, 64)]
STEEL = [(54, 56, 66), (92, 96, 108), (138, 142, 154), (196, 200, 210)]


def hammer():
    im = new()
    c = (2.0, 14.0)
    rect(im, c, -0.7, 0.7, 0.0, 11.5, lambda u, v: WOOD[2] if u < -0.2 else WOOD[1] if u < 0.5 else WOOD[0])
    hc = (10.5, 5.5)
    rect(im, hc, -3.4, 3.4, -2.1, 2.1, lambda u, v: STEEL[3] if u < -1.7 else STEEL[2] if u < 1.0 else STEEL[1])
    rect(im, hc, -3.4, -2.6, -2.1, 2.1, lambda u, v: STEEL[3])
    rect(im, hc, 2.6, 3.4, -2.1, 2.1, lambda u, v: STEEL[0])
    outline(im, (20, 20, 28))
    return im


# ---------------------------------------------------------------------------------------------- anvil block
def noise_tex(base, seed, spread=3):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            v = rnd.choice(base)
            im.putpixel((x, y), v + (255,))
    return im


IRON = [(38, 38, 44), (50, 50, 58), (62, 62, 70), (50, 50, 58), (38, 38, 44), (76, 76, 86)]
GOLD = [(196, 146, 30), (232, 190, 60)]


def anvil_side():
    im = noise_tex(IRON, 1901)
    for x in range(16):                 # gold trim line, like the Crafting Forge
        im.putpixel((x, 7), GOLD[0] + (255,))
        im.putpixel((x, 8), GOLD[1] + (255,))
    for y in range(16):                 # darker rim at the edges
        for x in (0, 15):
            im.putpixel((x, y), (30, 30, 36, 255))
    return im


def anvil_top():
    rnd = random.Random(1902)
    steel = [(104, 106, 118), (116, 118, 130), (128, 130, 142), (116, 118, 130), (104, 106, 118)]
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), rnd.choice(steel) + (255,))
    for i in range(16):                 # worn rim
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            im.putpixel((x, y), (62, 62, 70, 255))
    for (x, y) in ((3, 5), (4, 6), (9, 9), (10, 10), (11, 3), (6, 11)):   # hammer marks
        im.putpixel((x, y), (82, 84, 96, 255))
    return im


def box(frm, to):
    def face(name, tex):
        return {"texture": tex}
    return {"from": frm, "to": to, "faces": {
        "north": face("north", "#side"), "south": face("south", "#side"), "east": face("east", "#side"),
        "west": face("west", "#side"), "up": {"texture": "#top"}, "down": face("down", "#side")}}


def anvil_model():
    elements = [
        box([2, 0, 2], [14, 4, 14]),       # foot
        box([4, 4, 5], [12, 10, 11]),      # waist
        box([1, 10, 4], [15, 12, 12]),     # plate underside (chamfer)
        box([0, 12, 3], [13, 16, 13]),     # work surface
        box([13, 13, 4], [15, 16, 12]),    # horn, first step
        box([15, 14, 5], [16, 16, 11]),    # horn tip
    ]
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "holeydigsite:block/smithing_anvil_side", "side": "holeydigsite:block/smithing_anvil_side",
                         "top": "holeydigsite:block/smithing_anvil_top"},
            "elements": elements}


PREVIEW_TINTS = [(255, 215, 80), (235, 240, 248), (200, 30, 60), (40, 90, 210)]   # metal 1, metal 2, metal 3 / gem...


def main():
    out = {'item/smith_ring_gem': ring_gem(), 'item/blacksmith_hammer': hammer(),
           'block/smithing_anvil_side': anvil_side(), 'block/smithing_anvil_top': anvil_top()}
    pieces = {}
    for base, layers in (('ring', ring()), ('necklace', necklace()), ('dagger', dagger()), ('sword', sword()), ('axe', axe())):
        for i, (lname, im) in enumerate(layers.items()):
            out['item/smith_%s%s' % (base, '' if i == 0 else '_' + lname)] = im
        pieces[base] = layers
    for name, im in out.items():
        im.save(os.path.join(TEX, name + '.png'))
    with open(os.path.join(MODELS, 'block/smithing_anvil.json'), 'w', newline='\n') as f:
        json.dump(anvil_model(), f, indent=2)
        f.write('\n')
    if 'preview' in sys.argv:
        # mixed metals: gold, silver, copper; untinted layers (grip / handle) as drawn
        metals = [(255, 215, 80), (200, 205, 215), (213, 124, 60)]
        untinted = {'dagger': {'grip'}, 'sword': {'grip'}, 'axe': {'handle'}}
        sheet = Image.new('RGBA', (len(pieces) * 17 * 6, 16 * 6 + 8), (70, 90, 70, 255))
        for i, (base, layers) in enumerate(pieces.items()):
            comp = new()
            slot = 0
            for lname, im in layers.items():
                if lname in untinted.get(base, ()):
                    t = (255, 255, 255)
                elif lname == 'gem':
                    t = (200, 30, 60)
                else:
                    t = metals[min(slot, 2)]
                    slot += 1
                tinted = Image.merge('RGBA', [im.getchannel(k).point(lambda v, c=c: v * c // 255) for k, c in enumerate(t)] + [im.getchannel(3)])
                comp.alpha_composite(tinted)
            comp = comp.resize((96, 96), Image.NEAREST)
            sheet.paste(comp, (i * 17 * 6, 4), comp)
        sheet.save(os.path.join(os.environ.get('TEMP', '.'), 'phase19_preview.png'))


if __name__ == '__main__':
    main()
