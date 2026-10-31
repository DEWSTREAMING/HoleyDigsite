"""Preview sheet of the Outback collection: every skin on the Derrick drill and the long sluice, Black Opal at four points of its loop.

    python tools/make_phase31_previews.py          # writes docs/preview/phase31/outback_skins.png

Uses the renderer of make_long_sluice.py (orthographic, flat shaded, no lighting) and the generated skin textures.
"""
import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_long_sluice as mls  # noqa: E402

ROOT = mls.ROOT
MODELS = mls.MODELS
OUT = os.path.join(ROOT, 'docs/preview/phase31')
SKINS = [(None, 'Default'), ('outback', 'Outback (common)'), ('outback_dusty', 'Dusty Outback (common)'), ('outback_ochre', 'Ochre (rare)'),
         ('opal_calm', 'Calm Opal (rare)'), ('opal', 'Opal (epic)'), ('opal_boulder', 'Boulder Opal (epic)'), ('black_opal', 'Black Opal (legendary)')]
FRAME = [0]


def tex_image(name, skin=None):
    key = (name, skin, FRAME[0])
    if key not in mls._cache:
        path = os.path.join(mls.TEX, name + '.png')
        if skin:
            sp = os.path.join(mls.TEX, 'skin', skin, name + '.png')
            if os.path.exists(sp):
                path = sp
        img = Image.open(path).convert('RGBA')
        n = max(1, img.height // img.width)
        f = FRAME[0] % n
        mls._cache[key] = img.crop((0, f * img.width, img.width, (f + 1) * img.width))
    return mls._cache[key]


mls.tex_image = tex_image


def model(name):
    return json.load(open(os.path.join(MODELS, 'block', name + '.json')))


def drill_parts():
    parts = []
    for half, dy in (('lower', 0), ('upper', 16)):
        m = model(f'mining_drill_t3_{half}_working')
        tex = {k: v.split(':')[-1] for k, v in m['textures'].items()}
        parts.append((m['elements'], tex, dy, 0))
    return parts


def cell(parts, skin, cam, scale, size, centre):
    return mls.render(parts, cam, skin, scale=scale, size=size, centre=centre)


def main():
    os.makedirs(OUT, exist_ok=True)
    drill = drill_parts()
    sluice = mls.preview('working')
    cw, ch = 330, 360
    cols = 3
    rows = len(SKINS) + 1
    sheet = Image.new('RGBA', (cw * cols, 24 + rows * ch), (52, 56, 62, 255))
    d = ImageDraw.Draw(sheet)
    d.text((8, 6), 'Outback collection on the Derrick drill (front) and the long sluice (front, back)', fill=(235, 235, 235, 255))
    y = 24
    for skin, label in SKINS:
        FRAME[0] = 0
        for i, (parts, cam, scale, centre) in enumerate((
                (drill, (1.0, 0.6, -1.3), 9.0, (8, 16, 8)),
                (sluice, (1.0, 0.6, -1.3), 5.2, (8, 12, 0)),
                (sluice, (-1.0, 0.6, 1.3), 5.2, (8, 12, 0)))):
            sheet.alpha_composite(cell(parts, skin, cam, scale, (cw, ch), centre), (i * cw, y))
        d.text((8, y + 4), label, fill=(255, 255, 255, 255))
        y += ch
    # the loop: Black Opal at four frames of its eight
    FRAME[0] = 0
    d.text((8, y + 4), 'Black Opal on the drill at frames 0, 3 and 5 of its loop', fill=(255, 255, 255, 255))
    for i, f in enumerate((0, 3, 5)):
        FRAME[0] = f
        sheet.alpha_composite(cell(drill, 'black_opal', (1.0, 0.6, -1.3), 9.0, (cw, ch), (8, 16, 8)), (i * cw, y))
    sheet.convert('RGB').save(os.path.join(OUT, 'outback_skins.png'))
    print('written', os.path.join(OUT, 'outback_skins.png'), sheet.size)


if __name__ == '__main__':
    main()
