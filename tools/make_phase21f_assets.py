"""Generates the Phase 21f textures AND model / blockstate JSON (deterministic; needs Pillow).

    python tools/make_phase21f_assets.py

Coarse dirt, Rooted dirt, and Moss:
- Dirt clump textures and item models for tier 6 (coarse), 7 (rooted), 8 (moss)
- Pan models and blockstates for all 8 tiers
- Large Pan models and blockstates for all 8 tiers
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from make_phase9_assets import BLOCK, ITEM, MODELS, STATES, write_json, rgba, save
from make_phase10_assets import load, copy_json
from make_phase11_assets import by_value, generated

COARSE = [(68, 48, 34), (88, 62, 44), (112, 80, 56), (138, 100, 70)]
ROOTED = [(80, 58, 40), (104, 76, 52), (130, 96, 68), (170, 140, 110)]
MOSS = [(40, 68, 24), (58, 92, 34), (84, 128, 48), (116, 168, 60)]

NEW_KINDS = [
    ('coarse', 6, COARSE, 'minecraft:block/coarse_dirt'),
    ('rooted', 7, ROOTED, 'minecraft:block/rooted_dirt'),
    ('moss', 8, MOSS, 'minecraft:block/moss_block'),
]


def clump_assets():
    clump = load(ITEM, 'dirt_clump')
    for name, tier, pal, _ in NEW_KINDS:
        img = by_value(clump, pal)
        save(img, ITEM, f'dirt_clump_{name}')
        generated(f'dirt_clump_{name}')

    # Update dirt_clump.json item model with overrides
    m = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": "holeydigsite:item/dirt_clump"
        },
        "overrides": [
            {"predicate": {"holeydigsite:tier": 2}, "model": "holeydigsite:item/dirt_clump_deep"},
            {"predicate": {"holeydigsite:tier": 3}, "model": "holeydigsite:item/dirt_clump_abyssal"},
            {"predicate": {"holeydigsite:tier": 6}, "model": "holeydigsite:item/dirt_clump_coarse"},
            {"predicate": {"holeydigsite:tier": 7}, "model": "holeydigsite:item/dirt_clump_rooted"},
            {"predicate": {"holeydigsite:tier": 8}, "model": "holeydigsite:item/dirt_clump_moss"},
        ]
    }
    write_json(os.path.join(MODELS, 'item'), 'dirt_clump', m)


def pan_assets():
    # 1. Pan fill models for coarse, rooted, moss
    for fill in range(1, 5):
        for name, _, _, tex in NEW_KINDS:
            copy_json(os.path.join(MODELS, 'block'), f'pan_fill_{fill}', f'pan_fill_{fill}_{name}',
                      [('"minecraft:block/dirt"', f'"{tex}"')])

    suffix = {1: '', 2: '_deep', 3: '_abyssal', 4: '_gravel', 5: '_sand', 6: '_coarse', 7: '_rooted', 8: '_moss'}

    # 2. Blockstate pan.json (all 8 tiers x 5 fill levels = 40 variants)
    variants = {}
    for tier in range(1, 9):
        for fill in range(0, 5):
            variants[f"fill={fill},tier={tier}"] = {
                "model": "holeydigsite:block/" + ('pan' if fill == 0 else f'pan_fill_{fill}{suffix[tier]}')
            }
    write_json(STATES, 'pan', {"variants": variants})

    # 3. Item model pan.json with overrides for all 8 tiers
    overrides = []
    for fill in range(1, 5):
        for tier in range(1, 9):
            pred = {"holeydigsite:fill": fill / 4}
            if tier > 1:
                pred["holeydigsite:tier"] = tier
            overrides.append({"predicate": pred, "model": f"holeydigsite:block/pan_fill_{fill}{suffix[tier]}"})
    write_json(os.path.join(MODELS, 'item'), 'pan', {"parent": "holeydigsite:block/pan", "overrides": overrides})


def large_pan_assets():
    # 1. Large pan fill models for coarse, rooted, moss
    for fill in range(1, 9):
        for name, _, _, tex in NEW_KINDS:
            copy_json(os.path.join(MODELS, 'block'), f'large_pan_fill_{fill}', f'large_pan_fill_{fill}_{name}',
                      [('"minecraft:block/dirt"', f'"{tex}"')])

    suffix = {1: '', 2: '_deep', 3: '_abyssal', 4: '_gravel', 5: '_sand', 6: '_coarse', 7: '_rooted', 8: '_moss'}

    # 2. Blockstate large_pan.json (all 8 tiers x 9 fill levels = 72 variants)
    variants = {}
    for fill in range(0, 9):
        for tier in range(1, 9):
            variants[f"fill={fill},tier={tier}"] = {
                "model": "holeydigsite:block/" + ('large_pan' if fill == 0 else f'large_pan_fill_{fill}{suffix[tier]}')
            }
    write_json(STATES, 'large_pan', {"variants": variants})

    # 3. Item model large_pan.json
    overrides = []
    for fill in range(1, 9):
        for tier in range(1, 9):
            pred = {"holeydigsite:fill": round(fill / 8, 4)}
            if tier > 1:
                pred["holeydigsite:tier"] = tier
            overrides.append({"predicate": pred, "model": f"holeydigsite:block/large_pan_fill_{fill}{suffix[tier]}"})
    write_json(os.path.join(MODELS, 'item'), 'large_pan', {"parent": "holeydigsite:block/large_pan", "overrides": overrides})


if __name__ == '__main__':
    clump_assets()
    pan_assets()
    large_pan_assets()
    print('phase 21f assets written')
