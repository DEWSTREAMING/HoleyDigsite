"""Cast metal tops for molds and the Casting Mold's pouring lip.

The metal_<metal> textures are what a cooled mold shows: a smooth ingot top with a lit top-left bevel, a shaded bottom-right
edge and one soft sheen, framed for the mold's cavity (x 4-11, y 5-10 of the texture). The Casting Mold gets a lip on its
front and back, the two sides it pushes ingots out of, so they still show when the cavity is full.
"""
import json
import os

from PIL import Image

from make_phase9_assets import METALS

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/holeydigsite')
TEX = os.path.join(ASSETS, 'textures/block')
CASTING = os.path.join(ASSETS, 'models/block/casting_mold_t2.json')

X0, Y0, X1, Y1 = 4, 5, 11, 10  # the cavity window the mold models show


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def metal_top(solid):
    dark, mid, light, bright = solid
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            # inside the window, light falls from the top left; outside it the same ramp continues for particles
            u = (x - X0) / (X1 - X0)
            v = (y - Y0) / (Y1 - Y0)
            t = max(0.0, min(1.0, 0.55 * u + 0.45 * v))
            c = lerp(light, mid, t)
            d = abs((x - X0) + (y - Y0) - 6)
            if d == 0:
                c = lerp(c, bright, 0.5)  # sheen
            elif d == 1:
                c = lerp(c, bright, 0.2)
            if y == Y0 or x == X0:
                c = bright
            if y == Y1 or x == X1:
                c = dark
            if (x, y) in ((X0, Y1), (X1, Y0)):
                c = mid
            img.putpixel((x, y), c + (255,))
    return img


def lip(z0, z1, end):
    """A short open channel sticking out of a rim; end is the face at its tip."""
    def el(frm, to, faces):
        return {'from': frm, 'to': to, 'faces': {f: {'texture': '#m'} for f in faces}}
    return [
        el([6, 2, z0], [10, 3, z1], (end, 'east', 'west', 'up', 'down')),
        el([6, 3, z0], [7, 4, z1], (end, 'east', 'west', 'up')),
        el([9, 3, z0], [10, 4, z1], (end, 'east', 'west', 'up')),
    ]


def casting_mold():
    with open(CASTING, encoding='utf-8') as fh:
        m = json.load(fh)
    m['elements'] = [e for e in m['elements'] if 0 <= e['from'][2] and e['to'][2] <= 16] + lip(-2, 1, 'north') + lip(15, 18, 'south')
    with open(CASTING, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(m, fh, indent=2)
        fh.write('\n')


def main():
    for metal, (_, solid) in METALS.items():
        metal_top(solid).save(os.path.join(TEX, f'metal_{metal}.png'))
    casting_mold()
    print(f'{len(METALS)} cast metal tops, casting mold lip')


if __name__ == '__main__':
    main()
