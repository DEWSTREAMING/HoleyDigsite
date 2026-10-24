#!/usr/bin/env python3
"""Phase 21i assets generator:
Generates textures, models, blockstates, and loot tables for:
- down_incline_belt & fast_down_incline_belt
- drop_chute
"""
import json
import os
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources'))
TB = os.path.join(ROOT, 'assets', 'holeydigsite', 'textures', 'block')
MB = os.path.join(ROOT, 'assets', 'holeydigsite', 'models', 'block')
MI = os.path.join(ROOT, 'assets', 'holeydigsite', 'models', 'item')
BS = os.path.join(ROOT, 'assets', 'holeydigsite', 'blockstates')
LT = os.path.join(ROOT, 'data', 'holeydigsite', 'loot_table', 'blocks')
NS = 'holeydigsite'

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')

def face(tex, uv=None, cull=None):
    f = {'texture': tex, 'uv': uv or [0, 0, 16, 16]}
    if cull:
        f['cullface'] = cull
    return f

def box(frm, to, faces, rot=None):
    e = {'from': frm, 'to': to, 'faces': faces}
    if rot:
        e['rotation'] = rot
    return e

def belt_textures(prefix):
    t = lambda s: '%s:block/%s' % (NS, s)
    return {
        'particle': t(prefix + '_frame_side'),
        'side': t(prefix + '_frame_side'),
        'end': t(prefix + '_frame_end'),
        'bottom': t(prefix + '_frame_bottom')
    }

def down_incline_elements():
    half = 8 * 2 ** 0.5  # 11.3137
    rot = {'origin': [8, 8, 8], 'axis': 'x', 'angle': -45}
    r = lambda v: round(v, 4)
    plate = box([0, 8, r(8 - half)], [16, 8, r(8 + half)], {'up': face('#top'), 'down': face('#bottom')}, rot)
    t = 3.0
    rail = box([0, 8 - t, r(8 - half + t)], [16, 8, r(8 + half - t)], {
        'west': face('#side', [0, 0, 16, t]), 'east': face('#side', [0, 0, 16, t]), 'down': face('#bottom'),
        'north': face('#end', [0, 0, 16, t]), 'south': face('#end', [0, 0, 16, t])}, rot)
    els = [plate, rail]
    inset = 0.02
    for i in range(1, 16):
        top = i
        faces = {
            'down': face('#bottom', [inset, i, 16 - inset, i + 1], 'down'),
            'west': {'texture': '#side'},
            'east': {'texture': '#side'},
            'up': {'texture': '#bottom'}
        }
        if i == 15:
            faces['south'] = face('#end', [0, 0, 16, 16], 'south')
        els.append(box([inset, 0, i], [16 - inset, top, i + 1], faces))
    return els

def ys():
    return (('north', 0), ('east', 90), ('south', 180), ('west', 270))

def moving_when(f):
    return {'facing': f, 'mode': 'moving', 'powered': 'false', 'water': 'true'}

def static_when(f):
    return {'OR': [{'facing': f, 'mode': 'empty|blocked'}, {'facing': f, 'powered': 'true'}, {'facing': f, 'water': 'false'}]}

def apply_model(model, y):
    a = {'model': model}
    if y:
        a['y'] = y
    return a

def make_down_incline(name, frame, top, top_moving):
    tx = belt_textures(frame)
    t = lambda s: '%s:block/%s' % (NS, s)
    for moving in (False, True):
        write_json(os.path.join(MB, name + ('_moving' if moving else '') + '.json'), {
            'parent': 'minecraft:block/block',
            'textures': {**tx, 'top': t(top_moving if moving else top)},
            'elements': down_incline_elements()
        })
    write_json(os.path.join(MI, name + '.json'), {'parent': '%s:block/%s' % (NS, name)})
    parts = []
    for f, y in ys():
        parts.append({'when': moving_when(f), 'apply': apply_model('%s:block/%s_moving' % (NS, name), y)})
        parts.append({'when': static_when(f), 'apply': apply_model('%s:block/%s' % (NS, name), y)})
        parts.append({'when': {'facing': f, 'spout': 'true'}, 'apply': apply_model('%s:block/clump_belt_spout' % NS, y)})
    write_json(os.path.join(BS, name + '.json'), {'multipart': parts})
    # loot table
    write_json(os.path.join(LT, name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'bonus_rolls': 0,
            'entries': [{'type': 'minecraft:item', 'name': '%s:%s' % (NS, name)}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]
        }],
        'random_sequence': '%s:blocks/%s' % (NS, name)
    })

def make_chute_texture():
    img = Image.new('RGBA', (16, 16), (65, 68, 74, 255))
    draw = ImageDraw.Draw(img)
    # Iron plate bands
    draw.line([(0, 0), (15, 0)], fill=(120, 125, 135, 255))
    draw.line([(0, 1), (15, 1)], fill=(90, 95, 105, 255))
    draw.line([(0, 14), (15, 14)], fill=(45, 48, 52, 255))
    draw.line([(0, 15), (15, 15)], fill=(35, 38, 42, 255))
    # Vertical corners / seams
    draw.line([(0, 0), (0, 15)], fill=(90, 95, 105, 255))
    draw.line([(15, 0), (15, 15)], fill=(45, 48, 52, 255))
    # Mid-body seam
    draw.line([(0, 7), (15, 7)], fill=(50, 52, 58, 255))
    draw.line([(0, 8), (15, 8)], fill=(80, 84, 92, 255))
    # Copper rivets
    rivet = (185, 120, 65, 255)
    rivet_dark = (120, 70, 35, 255)
    for rx, ry in [(2, 3), (13, 3), (2, 12), (13, 12)]:
        img.putpixel((rx, ry), rivet)
        img.putpixel((rx, ry + 1), rivet_dark)
    img.save(os.path.join(TB, 'drop_chute.png'))
    print('Generated drop_chute.png')

def make_drop_chute():
    make_chute_texture()
    tex = '%s:block/drop_chute' % NS
    # Model: Upper funnel collar (1 to 15, Y 12..16) + Lower shaft (3 to 13, Y 0..12)
    # Walls meet with end-caps at corners (no slits), top rim pieces join with seamless UVs,
    # and no two faces in the same plane (zero coplanar overlaps / z-fighting).
    elements = [
        # Upper collar (X: 1..15, Z: 1..15, Y: 12..16)
        box([1, 12, 1], [15, 16, 3], {
            'north': face('#tex', [1, 0, 15, 4]),
            'south': face('#tex', [1, 0, 15, 4]),
            'up': face('#tex', [1, 1, 15, 3]),
            'down': face('#tex', [1, 1, 15, 3]),
            'west': face('#tex', [1, 0, 3, 4]),
            'east': face('#tex', [13, 0, 15, 4]),
        }),
        box([1, 12, 13], [15, 16, 15], {
            'south': face('#tex', [1, 0, 15, 4]),
            'north': face('#tex', [1, 0, 15, 4]),
            'up': face('#tex', [1, 13, 15, 15]),
            'down': face('#tex', [1, 13, 15, 15]),
            'west': face('#tex', [13, 0, 15, 4]),
            'east': face('#tex', [1, 0, 3, 4]),
        }),
        box([1, 12, 3], [3, 16, 13], {
            'west': face('#tex', [3, 0, 13, 4]),
            'east': face('#tex', [3, 0, 13, 4]),
            'up': face('#tex', [1, 3, 3, 13]),
            'down': face('#tex', [1, 3, 3, 13]),
        }),
        box([13, 12, 3], [15, 16, 13], {
            'east': face('#tex', [3, 0, 13, 4]),
            'west': face('#tex', [3, 0, 13, 4]),
            'up': face('#tex', [13, 3, 15, 13]),
            'down': face('#tex', [13, 3, 15, 13]),
        }),
        # Lower shaft (X: 3..13, Z: 3..13, Y: 0..12)
        box([3, 0, 3], [13, 12, 5], {
            'north': face('#tex', [3, 4, 13, 16]),
            'south': face('#tex', [3, 4, 13, 16]),
            'west': face('#tex', [3, 4, 5, 16]),
            'east': face('#tex', [11, 4, 13, 16]),
            'up': face('#tex', [3, 3, 13, 5]),
            'down': face('#tex', [3, 3, 13, 5], 'down'),
        }),
        box([3, 0, 11], [13, 12, 13], {
            'south': face('#tex', [3, 4, 13, 16]),
            'north': face('#tex', [3, 4, 13, 16]),
            'west': face('#tex', [11, 4, 13, 16]),
            'east': face('#tex', [3, 4, 5, 16]),
            'up': face('#tex', [3, 11, 13, 13]),
            'down': face('#tex', [3, 11, 13, 13], 'down'),
        }),
        box([3, 0, 5], [5, 12, 11], {
            'west': face('#tex', [5, 4, 11, 16]),
            'east': face('#tex', [5, 4, 11, 16]),
            'up': face('#tex', [3, 5, 5, 11]),
            'down': face('#tex', [3, 5, 5, 11], 'down'),
        }),
        box([11, 0, 5], [13, 12, 11], {
            'east': face('#tex', [5, 4, 11, 16]),
            'west': face('#tex', [5, 4, 11, 16]),
            'up': face('#tex', [11, 5, 13, 11]),
            'down': face('#tex', [11, 5, 13, 11], 'down'),
        }),
    ]
    write_json(os.path.join(MB, 'drop_chute.json'), {
        'parent': 'minecraft:block/block',
        'textures': {'particle': tex, 'tex': tex},
        'elements': elements
    })
    write_json(os.path.join(BS, 'drop_chute.json'), {
        'variants': {'': {'model': '%s:block/drop_chute' % NS}}
    })
    write_json(os.path.join(MI, 'drop_chute.json'), {
        'parent': '%s:block/drop_chute' % NS
    })

def main():
    make_down_incline('down_incline_belt', 'clump_belt', 'clump_belt_top', 'clump_belt_top_moving')
    make_down_incline('fast_down_incline_belt', 'fast_clump_belt', 'fast_clump_belt_top', 'fast_clump_belt_top_moving')
    make_drop_chute()
    print('Generated Phase 21i models, blockstates, and textures')

if __name__ == '__main__':
    main()
