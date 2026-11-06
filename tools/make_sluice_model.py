"""Miniature Sluice Model: the starter sluice shrunk onto a stripped spruce stand.

Reads the Starter Sluice model (block/sluice_box_idle), excludes the water plane, scales the
elements by 0.625 to fit onto a 12x12x1.5 spruce stand at [2, 0, 2] to [14, 1.5, 14].

    python tools/make_sluice_model.py            # generates sluice_model.json
    python tools/make_sluice_model.py --preview  # also docs/preview/quick1/sluice_model.png

Collision shape in DecorBlock.Kind.SLUICE_MODEL:
    {2, 0, 2, 14, 1.5, 14} and {3, 1.5, 3, 13, 9, 13}
"""
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
PREVIEW_DIR = os.path.join(ROOT, 'docs/preview/quick1')
VANILLA_JAR = r'C:\Users\Dewey\curseforge\minecraft\Install\versions\1.21.1\1.21.1.jar'

SCALE = 0.625  # 10 / 16
BASE_Y = 1.5


def place(p):
    return [round(8 + (p[0] - 8) * SCALE, 3), round(BASE_Y + p[1] * SCALE, 3), round(8 + (p[2] - 8) * SCALE, 3)]


def build():
    with open(os.path.join(MODELS, 'block/sluice_box_idle.json'), encoding='utf-8') as fh:
        src = json.load(fh)

    textures = {
        'particle': 'holeydigsite:block/sluice_wood',
        'wood': 'holeydigsite:block/sluice_wood',
        'port': 'holeydigsite:block/sluice_port',
        'back': 'holeydigsite:block/sluice_back',
        'tray': 'holeydigsite:block/sluice_tray',
        'ramp': 'holeydigsite:block/sluice_ramp',
        'riffle': 'holeydigsite:block/sluice_riffle',
        'lamp': 'holeydigsite:block/sluice_lamp_idle',
        'stand': 'minecraft:block/stripped_spruce_log'
    }

    elements = [
        {
            'name': 'stand',
            'from': [2, 0, 2],
            'to': [14, 1.5, 14],
            'faces': {
                'down': {'uv': [2, 2, 14, 14], 'texture': '#stand', 'cullface': 'down'},
                'up': {'uv': [2, 2, 14, 14], 'texture': '#stand'},
                'north': {'uv': [2, 14.5, 14, 16], 'texture': '#stand'},
                'south': {'uv': [2, 14.5, 14, 16], 'texture': '#stand'},
                'east': {'uv': [2, 14.5, 14, 16], 'texture': '#stand'},
                'west': {'uv': [2, 14.5, 14, 16], 'texture': '#stand'}
            }
        }
    ]

    for el in src.get('elements', []):
        if el.get('name') == 'water' or any(f.get('texture') == '#water' for f in el.get('faces', {}).values()):
            continue
        new_el = {
            'name': el.get('name', 'element'),
            'from': place(el['from']),
            'to': place(el['to']),
            'faces': {}
        }
        for side, face_data in el.get('faces', {}).items():
            f = dict(face_data)
            f.pop('cullface', None)
            new_el['faces'][side] = f
        if 'rotation' in el:
            new_el['rotation'] = dict(el['rotation'], origin=place(el['rotation']['origin']))
        elements.append(new_el)

    model = {
        'parent': 'minecraft:block/block',
        'textures': textures,
        'elements': elements
    }

    out_path = os.path.join(MODELS, 'block/sluice_model.json')
    with open(out_path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(model, fh, indent=2)
        fh.write('\n')

    print(f'sluice_model: {len(elements)} elements written to {out_path}')
    return model


def tex_path(ref):
    ns, path = ref.split(':') if ':' in ref else ('minecraft', ref)
    return ns, path


def preview(new_model):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import make_long_sluice as mls
    vanilla = zipfile.ZipFile(VANILLA_JAR) if os.path.exists(VANILLA_JAR) else None
    cache = {}

    def image(ref):
        if ref not in cache:
            ns, path = tex_path(ref)
            if ns == 'minecraft':
                if vanilla and f'assets/minecraft/textures/{path}.png' in vanilla.namelist():
                    img = Image.open(io.BytesIO(vanilla.read(f'assets/minecraft/textures/{path}.png')))
                else:
                    img = Image.new('RGBA', (16, 16), (110, 80, 50, 255))
            else:
                img = Image.open(os.path.join(ASSETS, 'textures', path + '.png'))
            img = img.convert('RGBA')
            cache[ref] = img.crop((0, 0, img.width, img.width))
        return cache[ref]

    old_path = os.path.join(PREVIEW_DIR, 'old_sluice_model.json')
    if os.path.exists(old_path):
        with open(old_path, encoding='utf-8') as fh:
            old_model = json.load(fh)
    else:
        old_model = new_model

    with open(os.path.join(MODELS, 'block/sluice_box_idle.json'), encoding='utf-8') as fh:
        real_sluice = json.load(fh)

    def parts(model):
        return [(model['elements'], dict(model['textures']), 0, 0)]

    old_p = parts(old_model)
    new_p = parts(new_model)
    real_p = parts(real_sluice)

    for p in [*old_p, *new_p, *real_p]:
        for k in list(p[1]):
            p[1][k] = p[1][k].replace('/', '|')
    mls.tex_image = lambda name, skin=None: image(name.replace('|', '/'))

    os.makedirs(PREVIEW_DIR, exist_ok=True)
    cw, ch = 360, 400
    cells = [
        ('Old Sluice Model (legs)', old_p, (1.0, 0.6, -1.3), 18, (8, 6, 8)),
        ('New Sluice Model (miniature)', new_p, (1.0, 0.6, -1.3), 18, (8, 6, 8)),
        ('New Sluice Model, side', new_p, (1.6, 0.35, 0.0), 18, (8, 6, 8)),
        ('Starter Sluice (full block)', real_p, (1.0, 0.6, -1.3), 14, (8, 6, 8)),
    ]
    sheet = Image.new('RGBA', (cw * len(cells), ch + 24), (52, 56, 62, 255))
    d = ImageDraw.Draw(sheet)
    d.text((8, 6), 'Sluice Model Decor: old leg model, new miniature Starter Sluice, and the full machine', fill=(235, 235, 235, 255))
    for i, (label, p, cam, scale, centre) in enumerate(cells):
        sheet.alpha_composite(mls.render(p, cam, scale=scale, size=(cw, ch), centre=centre), (i * cw, 24))
        d.text((i * cw + 8, 28), label, fill=(255, 255, 255, 255))
    out = os.path.join(PREVIEW_DIR, 'sluice_model.png')
    sheet.convert('RGB').save(out)
    print('written preview to', out, sheet.size)


def main():
    model = build()
    if '--preview' in sys.argv:
        preview(model)


if __name__ == '__main__':
    main()
