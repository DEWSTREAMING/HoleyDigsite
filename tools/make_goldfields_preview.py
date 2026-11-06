"""Renders in-game preview sheet for Phase Goldfields:
Conveyor skins, belt pieces (straight, corner, splitter, filter belt, drop chute),
and the Goldfields Crate (closed, locked, open).
"""

import json
import math
import os
import sys
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
import make_long_sluice as mls

ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'holeydigsite')
MODELS = os.path.join(ASSETS, 'models', 'block')
TEX = os.path.join(ASSETS, 'textures', 'block')
OUT_DIR = os.path.join(ROOT, 'docs', 'preview', 'goldfields')
OUT_FILE = os.path.join(OUT_DIR, 'in_game_look.png')


def load_model(name):
    path = os.path.join(MODELS, name + '.json')
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def resolve_model_elements(m):
    """If parented, resolves textures and elements."""
    if 'elements' in m:
        tex = {k: v.replace('holeydigsite:block/', '') for k, v in m.get('textures', {}).items()}
        return m['elements'], tex
    if 'parent' in m:
        parent_name = m['parent'].replace('holeydigsite:block/', '')
        parent_path = os.path.join(MODELS, parent_name + '.json')
        if os.path.exists(parent_path):
            with open(parent_path, 'r', encoding='utf-8') as f:
                pm = json.load(f)
            merged_tex = dict(pm.get('textures', {}))
            merged_tex.update(m.get('textures', {}))
            clean_tex = {k: v.replace('holeydigsite:block/', '') for k, v in merged_tex.items()}
            return pm['elements'], clean_tex
    return [], {}


def render_block(model_name, skin=None, cam=(1.2, 1.0, 1.4), scale=9.0, size=(240, 240), centre=(8, 8, 8)):
    m = load_model(model_name)
    els, tex = resolve_model_elements(m)
    parts = [(els, tex, 0, 0)]
    return mls.render(parts, cam, skin=skin, scale=scale, size=size, centre=centre)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    # 1. Belt skins row: 6 new skins on clump_belt
    new_skins = [
        ('patina', 'Patina (Common)'),
        ('gunmetal', 'Gunmetal (Common)'),
        ('ironbark', 'Ironbark (Rare)'),
        ('enamel', 'Enamel (Rare)'),
        ('quartz_reef', 'Quartz Reef (Epic)'),
        ('nugget', 'Nugget (Epic)')
    ]

    # 2. Conveyor pieces row: straight, corner, splitter, filter belt, drop chute, black opal
    pieces = [
        ('clump_belt', 'patina', 'Straight Belt (Patina)'),
        ('clump_belt_corner_left', 'gunmetal', 'Corner Belt (Gunmetal)'),
        ('belt_splitter_2way', 'ironbark', 'Belt Splitter (Ironbark)'),
        ('filter_belt_left', 'enamel', 'Filter Belt (Enamel)'),
        ('drop_chute', 'quartz_reef', 'Drop Chute (Quartz Reef)'),
        ('clump_belt', 'black_opal', 'Belt (Black Opal)')
    ]

    # 3. Goldfields Crate row
    crate_models = [
        ('goldfields_crate', None, 'Goldfields Crate (Closed)'),
        ('goldfields_crate_locked', None, 'Goldfields Crate (Locked)'),
        ('goldfields_crate_open', None, 'Goldfields Crate (Open)'),
        ('skin_bench', None, 'Skin Bench'),
        ('mining_drill_lower', 'patina', 'Mining Drill (Patina)'),
        ('ore_cart', None, 'Ore Cart (Find)')
    ]

    cell_w, cell_h = 240, 250
    cols = 6
    rows = 3
    padding = 30
    title_h = 50

    sheet_w = cell_w * cols + padding * 2
    sheet_h = title_h + cell_h * rows + padding * 2

    sheet = Image.new('RGBA', (sheet_w, sheet_h), (35, 38, 44, 255))
    draw = ImageDraw.Draw(sheet)

    # Header
    draw.text((padding, 16), "Phase Goldfields - Conveyor Skins & Goldfields Crate", fill=(245, 245, 245, 255))

    # Row 1: The Six New Skins
    r1_y = title_h + padding
    draw.text((padding, r1_y - 20), "NEW SKINS (CONVEYOR BELTS)", fill=(200, 200, 210, 255))
    for col, (skin_id, label) in enumerate(new_skins):
        x = padding + col * cell_w
        img = render_block('clump_belt', skin=skin_id, cam=(1.2, 1.1, 1.4), scale=9.5, centre=(8, 4, 8))
        sheet.paste(img, (x, r1_y), img)
        draw.text((x + 20, r1_y + cell_h - 25), label, fill=(220, 220, 220, 255))

    # Row 2: Belt Family Pieces
    r2_y = r1_y + cell_h
    draw.text((padding, r2_y - 20), "CONVEYOR FAMILY PIECES (WITH NEW SKINS)", fill=(200, 200, 210, 255))
    for col, (model_name, skin_id, label) in enumerate(pieces):
        x = padding + col * cell_w
        centre = (8, 8, 8) if model_name == 'drop_chute' else (8, 4, 8)
        scale = 8.5 if model_name == 'drop_chute' else 9.5
        img = render_block(model_name, skin=skin_id, cam=(1.2, 1.1, 1.4), scale=scale, centre=centre)
        sheet.paste(img, (x, r2_y), img)
        draw.text((x + 15, r2_y + cell_h - 25), label, fill=(220, 220, 220, 255))

    # Row 3: Goldfields Crate & Machines
    r3_y = r2_y + cell_h
    draw.text((padding, r3_y - 20), "GOLDFIELDS CRATE & MACHINES", fill=(200, 200, 210, 255))
    for col, (model_name, skin_id, label) in enumerate(crate_models):
        x = padding + col * cell_w
        cam = (1.2, 0.9, 1.4)
        centre = (8, 8, 8)
        img = render_block(model_name, skin=skin_id, cam=cam, scale=9.0, centre=centre)
        sheet.paste(img, (x, r3_y), img)
        draw.text((x + 15, r3_y + cell_h - 25), label, fill=(220, 220, 220, 255))

    sheet.save(OUT_FILE)
    print(f"Preview sheet generated: {OUT_FILE}")


if __name__ == '__main__':
    main()
