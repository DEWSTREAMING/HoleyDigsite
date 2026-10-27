"""Phase 16 assets (deterministic, Pillow): item/block textures, models, blockstates for the plot-exclusive items.

Run from the project root:  python tools/make_phase16_assets.py
Writes: Prospector's Pick, Riffle Insert, Fine Mesh Screen item textures; three lamp textures + blocks; the two sluice-insert
overlay textures/models; and adds the `upgrade` multipart entries to the sluice blockstates (idempotent).
"""
import json
import os
from PIL import Image

ROOT = os.path.join('src', 'main', 'resources', 'assets', 'holeydigsite')
TEX_B = os.path.join(ROOT, 'textures', 'block')
TEX_I = os.path.join(ROOT, 'textures', 'item')


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def img():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


# ---------------------------------------------------------------- items
def pick():
    im = img()
    wood, wood_d = (139, 94, 52, 255), (98, 64, 34, 255)
    for i in range(11):                      # handle from bottom-left to the upper right
        x, y = 2 + i, 13 - i
        im.putpixel((x, y), wood)
        if x + 1 < 16:
            im.putpixel((x + 1, y), wood_d)
    steel, steel_d, steel_l = (170, 178, 188, 255), (104, 112, 124, 255), (222, 228, 236, 255)
    # head: an arc across the top right
    arc = [(5, 4), (6, 3), (7, 2), (8, 2), (9, 2), (10, 2), (11, 3), (12, 4), (13, 5), (13, 6), (13, 7),
           (4, 5), (3, 6), (3, 7), (6, 4), (7, 3), (12, 5), (12, 6)]
    for p in arc:
        im.putpixel(p, steel)
    for p in [(7, 2), (8, 2), (9, 2), (10, 2)]:
        im.putpixel(p, steel_l)
    for p in [(3, 7), (13, 7), (4, 5), (12, 6)]:
        im.putpixel(p, steel_d)
    # glowing prospecting gem in the middle of the head
    for p, c in [((8, 3), (255, 214, 64, 255)), ((9, 3), (255, 170, 30, 255)), ((8, 4), (255, 170, 30, 255)), ((9, 4), (214, 120, 20, 255))]:
        im.putpixel(p, c)
    return im


def riffle_insert():
    im = img()
    frame, frame_d, bar = (176, 132, 70, 255), (120, 86, 40, 255), (226, 190, 110, 255)
    for x in range(1, 15):
        for y in (3, 12):
            im.putpixel((x, y), frame)
        for y in (4, 13):
            im.putpixel((x, y), frame_d)
    for y in range(3, 13):
        for x in (1, 14):
            im.putpixel((x, y), frame)
    for x in range(3, 14, 3):                # riffle bars
        for y in range(5, 12):
            im.putpixel((x, y), bar)
            im.putpixel((x + 1 if x + 1 < 14 else x, y), frame_d)
    return im


def mesh_screen():
    im = img()
    frame, frame_d, wire = (104, 112, 124, 255), (66, 72, 84, 255), (196, 204, 214, 255)
    for x in range(1, 15):
        for y in (3, 12):
            im.putpixel((x, y), frame)
        for y in (4, 13):
            im.putpixel((x, y), frame_d)
    for y in range(3, 13):
        for x in (1, 14):
            im.putpixel((x, y), frame)
    for x in range(2, 14):
        for y in range(5, 12):
            if x % 2 == 0 or y % 2 == 1:
                im.putpixel((x, y), wire)
    # topaz fleck
    im.putpixel((8, 8), (255, 190, 40, 255))
    im.putpixel((9, 8), (255, 220, 90, 255))
    return im


# ---------------------------------------------------------------- lamps
def lamp(base, edge, glow, glow_hi, pattern):
    im = Image.new('RGBA', (16, 16), base)
    for i in range(16):
        for p in [(i, 0), (i, 15), (0, i), (15, i)]:
            im.putpixel(p, edge)
    for x in range(3, 13):
        for y in range(3, 13):
            im.putpixel((x, y), glow)
    for x in range(5, 11):
        for y in range(5, 11):
            im.putpixel((x, y), glow_hi)
    pattern(im, base, edge)
    return im


def miners(im, base, edge):          # iron frame with rivets and a cross bar
    for i in range(3, 13):
        im.putpixel((i, 8), edge)
        im.putpixel((8, i), edge)
    for p in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        im.putpixel(p, (200, 200, 205, 255))


def river(im, base, edge):           # speckled stones
    import random
    r = random.Random(16)
    for _ in range(26):
        x, y = r.randrange(1, 15), r.randrange(1, 15)
        if not (3 <= x < 13 and 3 <= y < 13):
            c = r.choice([(120, 128, 134, 255), (150, 158, 162, 255), (96, 104, 112, 255)])
            im.putpixel((x, y), c)


def sandy(im, base, edge):           # sandstone bands
    for y in (1, 2, 13, 14):
        for x in range(1, 15):
            if y in (2, 13):
                im.putpixel((x, y), (196, 170, 112, 255))


def overlay_riffle():
    im = img()
    for x in range(16):
        for y in range(16):
            im.putpixel((x, y), (232, 196, 112, 255) if (x + y) % 5 else (176, 132, 70, 255))
    return im


def overlay_mesh():
    im = img()
    for x in range(16):
        for y in range(16):
            if x % 2 == 0 or y % 2 == 0:
                im.putpixel((x, y), (196, 204, 214, 255))
    return im


def block_cube(name):
    dump(os.path.join(ROOT, 'models', 'block', name + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'holeydigsite:block/' + name}})
    dump(os.path.join(ROOT, 'blockstates', name + '.json'), {'variants': {'': {'model': 'holeydigsite:block/' + name}}})
    dump(os.path.join(ROOT, 'models', 'item', name + '.json'), {'parent': 'holeydigsite:block/' + name})


def item(name, parent='minecraft:item/generated'):
    dump(os.path.join(ROOT, 'models', 'item', name + '.json'), {'parent': parent, 'textures': {'layer0': 'holeydigsite:item/' + name}})


def rot(angle_elem):
    return angle_elem


def overlay_model(name, texture, elements):
    dump(os.path.join(ROOT, 'models', 'block', name + '.json'),
         {'parent': 'minecraft:block/block', 'textures': {'particle': 'holeydigsite:block/' + texture, 'insert': 'holeydigsite:block/' + texture},
          'elements': elements})


def face_all(tex='#insert'):
    return {d: {'texture': tex} for d in ('north', 'south', 'east', 'west', 'up', 'down')}


def sluice_blockstates():
    """Adds the `upgrade` overlay parts to both sluice tiers (idempotent)."""
    yaw = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for state in ('sluice_box', 'sluice_box_t2'):
        path = os.path.join(ROOT, 'blockstates', state + '.json')
        with open(path, encoding='utf-8') as f:
            data = json.load(f)
        data['multipart'] = [p for p in data['multipart'] if 'upgrade' not in p.get('when', {})]
        for up in ('riffle', 'mesh'):
            for facing, y in yaw.items():
                apply = {'model': 'holeydigsite:block/sluice_upgrade_' + up}
                if y:
                    apply['y'] = y
                data['multipart'].append({'when': {'facing': facing, 'upgrade': up}, 'apply': apply})
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=1)
            f.write('\n')


def main():
    save(pick(), os.path.join(TEX_I, 'prospectors_pick.png'))
    save(riffle_insert(), os.path.join(TEX_I, 'riffle_insert.png'))
    save(mesh_screen(), os.path.join(TEX_I, 'fine_mesh_screen.png'))
    item('prospectors_pick', 'minecraft:item/handheld')
    item('riffle_insert')
    item('fine_mesh_screen')
    save(lamp((70, 74, 82, 255), (40, 42, 48, 255), (255, 176, 40, 255), (255, 232, 130, 255), miners), os.path.join(TEX_B, 'miners_lantern.png'))
    save(lamp((132, 140, 146, 255), (88, 96, 104, 255), (150, 230, 240, 255), (225, 252, 255, 255), river), os.path.join(TEX_B, 'river_stone_lamp.png'))
    save(lamp((222, 200, 140, 255), (178, 150, 96, 255), (255, 190, 90, 255), (255, 238, 170, 255), sandy), os.path.join(TEX_B, 'sandstone_lamp.png'))
    for n in ('miners_lantern', 'river_stone_lamp', 'sandstone_lamp'):
        block_cube(n)
    save(overlay_riffle(), os.path.join(TEX_B, 'sluice_insert_riffle.png'))
    save(overlay_mesh(), os.path.join(TEX_B, 'sluice_insert_mesh.png'))
    rotation = {'origin': [8, 8, 8], 'axis': 'x', 'angle': -22.5}
    # Riffle Insert: three more raised bars in between the sluice's own riffles (same slope as the ramp)
    bars = [{'from': [2, 8, z], 'to': [14, 9.4, z + 0.9], 'rotation': rotation, 'faces': face_all()} for z in (7.5, 10.5, 13.5)]   # 0.9: not flush with the tray dirt
    overlay_model('sluice_upgrade_riffle', 'sluice_insert_riffle', bars)
    # Fine Mesh Screen: a flat grid lying just above the ramp
    mesh = [{'from': [2, 8.6, 4], 'to': [14, 8.7, 15], 'rotation': rotation,
             'faces': {'up': {'texture': '#insert'}, 'down': {'texture': '#insert'}}}]
    overlay_model('sluice_upgrade_mesh', 'sluice_insert_mesh', mesh)
    sluice_blockstates()


if __name__ == '__main__':
    main()
