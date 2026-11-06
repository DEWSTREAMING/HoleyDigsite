"""Golden Drill Statue: the derrick drill shrunk onto a deepslate plinth, in gold.

Reads the real drill models (block/mining_drill_lower_working, _upper_working and the auger), scales their boxes to fit
above the plinth and swaps every texture for a gold copy. Rerun it whenever the derrick changes:

    python tools/make_gold_statue.py            # model + gold textures
    python tools/make_gold_statue.py --preview  # also docs/preview/phase35c/golden_drill_statue.png (old and new)

The statue has no renderer, so the auger is baked into the model, standing still. The lamp keeps full light.
The collision shape in DecorBlock.Kind.GOLDEN_DRILL_STATUE follows PLINTH and the derrick box printed at the end.
"""
import colorsys
import io
import json
import os
import sys
import zipfile

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/holeydigsite')
MODELS = os.path.join(ASSETS, 'models')
TEX = os.path.join(ASSETS, 'textures/block')
SKIN = os.path.join(TEX, 'skin/brass')
NAME = 'golden_drill_statue'
VANILLA_JAR = r'C:\Users\Dewey\curseforge\minecraft\Install\versions\1.21.1\1.21.1.jar'

# plinth: deepslate block with a gold trim on top, same as the old statue
PLINTH = [([2, 0, 2], [14, 2, 14], '#plinth'), ([3, 2, 3], [13, 3, 13], '#trim')]
BASE_Y = 3          # the derrick stands on the trim
TOP_Y = 15.9        # and has to stay inside the block, lamp included

# gold ramp from shadow to highlight; the brass skin's light levels are pushed a bit brighter onto it
GOLD = [(66, 38, 6), (128, 82, 14), (196, 140, 30), (238, 188, 52), (255, 226, 112), (255, 248, 196)]


def gold(c, lift=1.12):
    r, g, b, a = c
    if a == 0:
        return c
    lum = min(1.0, ((0.3 * r + 0.59 * g + 0.11 * b) / 255) ** 0.85 * lift)
    t = lum * (len(GOLD) - 1)
    i = min(len(GOLD) - 2, int(t))
    f = t - i
    return tuple(round(GOLD[i][k] + (GOLD[i + 1][k] - GOLD[i][k]) * f) for k in range(3)) + (a,)


def gold_lamp(c):
    """Only the cage turns gold; the warm glass stays as it is."""
    h, l, s = colorsys.rgb_to_hls(*(v / 255 for v in c[:3]))
    return gold(c, 1.25) if s < 0.25 else c


def tex_path(ref):
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    return ns, path


def source_image(ref):
    """The brass skin copy of a drill texture if there is one, else the plain drill texture. First frame only."""
    ns, path = tex_path(ref)
    base = path.split('/')[-1]
    for p in (os.path.join(SKIN, base + '.png'), os.path.join(ASSETS, 'textures', path + '.png')):
        if os.path.exists(p):
            img = Image.open(p).convert('RGBA')
            return img.crop((0, 0, img.width, img.width))
    raise FileNotFoundError(ref)


def gold_texture(ref, out_name, lamp=False):
    img = source_image(ref)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            px[x, y] = gold_lamp(px[x, y]) if lamp else gold(px[x, y])
    img.save(os.path.join(TEX, out_name + '.png'))


def load(name):
    with open(os.path.join(MODELS, 'block', name + '.json'), encoding='utf-8') as fh:
        return json.load(fh)


def auto_uv(face, f, t):
    """What Minecraft would pick for a face with no uv, clamped to the texture."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    uv = {'north': (16 - x2, 16 - y2, 16 - x1, 16 - y1), 'south': (x1, 16 - y2, x2, 16 - y1),
          'east': (16 - z2, 16 - y2, 16 - z1, 16 - y1), 'west': (z1, 16 - y2, z2, 16 - y1),
          'up': (x1, z1, x2, z2), 'down': (x1, 16 - z2, x2, 16 - z1)}[face]
    return [round(min(16, max(0, v)), 3) for v in uv]


def derrick_parts():
    """(model name, y offset in the full-size drill, texture key prefix)."""
    return [('mining_drill_lower_working', 0, 'l'), ('mining_drill_auger', 0, 'a'), ('mining_drill_upper_working', 16, 'u')]


def build():
    textures = {'plinth': 'minecraft:block/polished_deepslate', 'trim': 'minecraft:block/gold_block',
                'particle': 'minecraft:block/gold_block'}
    raw = []  # (from, to, faces, rotation) in full-size drill coordinates
    for model, dy, prefix in derrick_parts():
        m = load(model)
        for el in m['elements']:
            f = [el['from'][0], el['from'][1] + dy, el['from'][2]]
            t = [el['to'][0], el['to'][1] + dy, el['to'][2]]
            faces = {}
            for side, spec in el['faces'].items():
                key = spec['texture'].lstrip('#')
                ref = m['textures'][key]
                lamp = 'lantern' in ref
                tkey = 'lamp' if lamp else prefix + key
                if tkey not in textures:
                    out = f'{NAME}_lamp' if lamp else f'{NAME}_{ref.split("/")[-1].replace("mining_drill_", "")}'
                    gold_texture(ref, out, lamp)
                    textures[tkey] = f'holeydigsite:block/{out}'
                faces[side] = {'uv': spec.get('uv') or auto_uv(side, el['from'], el['to']), 'texture': '#' + tkey}
            rot = dict(el['rotation'], origin=[el['rotation']['origin'][0], el['rotation']['origin'][1] + dy,
                                               el['rotation']['origin'][2]]) if 'rotation' in el else None
            raw.append((f, t, faces, rot, any(v['texture'] == '#lamp' for v in faces.values())))
    lo = [min(r[0][i] for r in raw) for i in range(3)]
    hi = [max(r[1][i] for r in raw) for i in range(3)]
    scale = (TOP_Y - BASE_Y) / (hi[1] - lo[1])

    def place(p):
        return [round(8 + (p[0] - 8) * scale, 3), round(BASE_Y + (p[1] - lo[1]) * scale, 3), round(8 + (p[2] - 8) * scale, 3)]

    elements = [{'from': f, 'to': t, 'faces': {s: {'uv': [2, 2, 14, 14] if tex == '#plinth' else [3, 3, 13, 13], 'texture': tex}
                                               for s in ('north', 'south', 'east', 'west', 'up', 'down')}}
                for f, t, tex in PLINTH]
    elements[0]['faces']['down']['cullface'] = 'down'
    for f, t, faces, rot, lamp in raw:
        el = {'from': place(f), 'to': place(t), 'faces': faces}
        if rot:
            el['rotation'] = dict(rot, origin=place(rot['origin']))
        if lamp:
            el['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
        elements.append(el)
    model = {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}
    with open(os.path.join(MODELS, 'block', NAME + '.json'), 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(model, fh, indent=2)
        fh.write('\n')
    box = place(lo) + place(hi)
    print(f'{NAME}: {len(elements)} elements, derrick scale {scale:.3f}, derrick box '
          f'{[round(v, 2) for v in box]} (x1 y1 z1 x2 y2 z2)')
    return model


# ---------------------------------------------------------------- preview

def preview(new_model):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import make_long_sluice as mls
    vanilla = zipfile.ZipFile(VANILLA_JAR) if os.path.exists(VANILLA_JAR) else None
    cache = {}

    def image(ref):
        if ref not in cache:
            ns, path = tex_path(ref)
            if ns == 'minecraft':
                if vanilla:
                    img = Image.open(io.BytesIO(vanilla.read(f'assets/minecraft/textures/{path}.png')))
                else:
                    img = Image.new('RGBA', (16, 16), (240, 200, 60, 255) if 'gold' in path else (70, 70, 76, 255))
            else:
                img = Image.open(os.path.join(ASSETS, 'textures', path + '.png'))
            img = img.convert('RGBA')
            cache[ref] = img.crop((0, 0, img.width, img.width))
        return cache[ref]

    def parts(model):
        return [(model['elements'], dict(model['textures']), 0, 0)]

    with open(os.path.join(ROOT, 'docs/preview/phase35c/old_golden_drill_statue.json'), encoding='utf-8') as fh:
        old = json.load(fh)
    real = [(load('mining_drill_lower_working')['elements'] + load('mining_drill_auger')['elements'],
             load('mining_drill_lower_working')['textures'] | {'bit': 'holeydigsite:block/mining_drill_bit'}, 0, 0),
            (load('mining_drill_upper_working')['elements'], load('mining_drill_upper_working')['textures'], 16, 0)]
    # the sluice renderer looks textures up by the last path part, so hand it whole references instead
    old_p, new_p = parts(old), parts(new_model)
    for p in [*old_p, *new_p, *real]:
        for k in list(p[1]):
            p[1][k] = p[1][k].replace('/', '|')
    mls.tex_image = lambda name, skin=None: image(name.replace('|', '/'))

    cw, ch = 360, 400
    cells = [('Old statue', old_p, (1.0, 0.6, -1.3), 18, (8, 8, 8)),
             ('New statue', new_p, (1.0, 0.6, -1.3), 18, (8, 8, 8)),
             ('New statue, back', new_p, (-1.0, 0.6, 1.3), 18, (8, 8, 8)),
             ('Real drill (2 blocks)', real, (1.0, 0.6, -1.3), 8.5, (8, 18, 8))]
    sheet = Image.new('RGBA', (cw * len(cells), ch + 24), (52, 56, 62, 255))
    d = ImageDraw.Draw(sheet)
    d.text((8, 6), 'Golden Drill Statue: old cube drill, new gold derrick, and the drill it copies', fill=(235, 235, 235, 255))
    for i, (label, p, cam, scale, centre) in enumerate(cells):
        sheet.alpha_composite(mls.render(p, cam, scale=scale, size=(cw, ch), centre=centre), (i * cw, 24))
        d.text((i * cw + 8, 28), label, fill=(255, 255, 255, 255))
    out = os.path.join(ROOT, 'docs/preview/phase35c/golden_drill_statue.png')
    sheet.convert('RGB').save(out)
    print('written', out, sheet.size)


def main():
    model = build()
    if '--preview' in sys.argv:
        preview(model)


if __name__ == '__main__':
    main()
