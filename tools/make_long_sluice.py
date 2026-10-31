"""Long sluice (Tier 2): channel over two blocks with a chute on the back end.

Writes the block models of the three parts (back, front, chute) in every status, the dirt and insert overlays, the
blockstate and the item model of `sluice_box_t2`, and renders previews to docs/preview/phase29/. The textures are the
existing sluice ones, so every skin and wear fits without new files.

    python tools/make_long_sluice.py            # models + blockstate + previews
    python tools/make_long_sluice.py --no-preview

Layout, in the model's own north-facing frame (the tray end is north, z = 0). Along the channel u runs from 0 (tray end,
front part) to 32 (rear wall, back part): the front part holds u 0..16 (local z = u), the back part u 16..32 (local z = u - 16).
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/holeydigsite')
MODELS = os.path.join(ASSETS, 'models')
TEX = os.path.join(ASSETS, 'textures/block')
PREVIEW = os.path.join(ROOT, 'docs/preview/phase29')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
STATUSES = ('working', 'idle', 'stopped', 'no_power')

# floor: seven steps of 4 blocks-pixels from u 4 up to u 32, rising 0.8 each
TRAY_END = 4
STEP = 4
STEPS = 7
BASE_TOP = 3
FLOOR0 = 4.0
RISE = 0.8
WALL_OVER = 7
REAR = 28  # the last 4 px are the rear wall's tower


def floor_top(i):
    return round(FLOOR0 + RISE * i, 2)


def wall_top(i):
    return 16 if TRAY_END + STEP * i >= REAR else min(16, round(floor_top(i) + WALL_OVER, 2))


def box(frm, to, tex, faces=FACES, uv=None):
    if isinstance(tex, str):
        tex = {f: tex for f in faces}
    el = {'from': list(frm), 'to': list(to), 'faces': {f: {'texture': t} for f, t in tex.items()}}
    if uv:
        for f, u in uv.items():
            if f in el['faces']:
                el['faces'][f]['uv'] = u
    return el


def named(el, name):
    el['name'] = name
    return el


def textures(status, tier_wood='sluice_t2_wood'):
    lamp = {'working': 'sluice_lamp_working', 'idle': 'sluice_lamp_idle', 'stopped': 'sluice_lamp_stopped',
            'no_power': 'sluice_lamp_off'}[status]
    t = {'wood': tier_wood, 'port': 'sluice_t2_port', 'back': 'sluice_t2_back', 'tray': 'sluice_tray',
         'ramp': 'sluice_ramp', 'riffle': 'sluice_riffle', 'lamp': lamp}
    if status != 'no_power':
        t['water'] = 'sluice_water_still' if status == 'stopped' else 'sluice_water_flow'
    return t


def span(part):
    """u range of a channel part and the local z offset."""
    return (16, 32, -16) if part == 'back' else (0, 16, 0)


def channel(part, status):
    lo, hi, off = span(part)
    els = [named(box([0, 0, lo + off], [16, BASE_TOP, hi + off], '#wood'), 'base')]
    # walls (outer face: water ports on the back part only)
    for i in range(STEPS):
        u0, u1 = TRAY_END + STEP * i, TRAY_END + STEP * (i + 1)
        if u1 <= lo or u0 >= hi:
            continue
        a, b = max(u0, lo), min(u1, hi)
        z0, z1 = a + off, b + off
        top = wall_top(i)
        port = '#port' if part == 'back' else '#wood'
        els.append(named(box([0, BASE_TOP, z0], [2, top, z1], {'west': port, 'east': '#wood', 'up': '#wood', 'north': '#wood',
                                                                'south': '#wood'}), f'wall_w{i}'))
        els.append(named(box([14, BASE_TOP, z0], [16, top, z1], {'east': port, 'west': '#wood', 'up': '#wood', 'north': '#wood',
                                                                  'south': '#wood'}), f'wall_e{i}'))
        f = floor_top(i)
        els.append(named(box([2, BASE_TOP, z0], [14, f, z1], {'up': '#ramp', 'north': '#wood', 'south': '#wood'}), f'floor{i}'))
        if status != 'no_power':
            els.append(named(box([2, f + 0.05, z0], [14, f + 0.1, z1], {'up': '#water'}), f'water{i}'))
    if part == 'front':
        # the tray: flat floor, lip at the very end
        els.append(named(box([2, BASE_TOP, 0], [14, 3.6, TRAY_END], {'up': '#tray'}), 'tray'))
        els.append(named(box([2, 3.6, 0], [14, 5.2, 1], {'up': '#tray', 'north': '#tray', 'south': '#tray'}), 'lip'))
        els.append(named(box([7, 1, -0.5], [9, 2.5, 0], {'north': '#lamp', 'up': '#lamp', 'west': '#lamp', 'east': '#lamp'}), 'lamp'))
        els.append(named(box([0, BASE_TOP, 0], [2, 8, TRAY_END], {'west': '#wood', 'east': '#wood', 'up': '#wood', 'north': '#wood', 'south': '#wood'}), 'wall_w_tray'))
        els.append(named(box([14, BASE_TOP, 0], [16, 8, TRAY_END], {'west': '#wood', 'east': '#wood', 'up': '#wood', 'north': '#wood', 'south': '#wood'}), 'wall_e_tray'))
    else:
        els.append(named(box([2, BASE_TOP, 15], [14, 16, 16], {'south': '#back', 'north': '#wood', 'up': '#wood'}), 'rear'))
    # riffles across the channel every 8
    for u in range(8, 32, 8):
        if not lo <= u < hi:
            continue
        i = min(STEPS - 1, (u - TRAY_END) // STEP)
        f = floor_top(i)
        els.append(named(box([2, f, u + off], [14, f + 1.3, u + off + 1], {'up': '#riffle', 'north': '#riffle', 'south': '#riffle'}), f'riffle{u}'))
    return els


def chute(status):
    t = {'up': '#wood', 'down': '#wood', 'north': '#wood', 'south': '#wood', 'east': '#wood', 'west': '#wood'}
    els = [
        named(box([4, 0, 4], [12, 3, 12], t), 'spout'),
        named(box([2, 3, 2], [14, 7, 14], t), 'lower'),
        # open hopper: four walls and a dark floor
        named(box([1, 7, 1], [15, 13, 3], {**t, 'north': '#back'}), 'wall_n'),
        named(box([1, 7, 13], [15, 13, 15], t), 'wall_s'),
        named(box([1, 7, 3], [3, 13, 13], t), 'wall_w'),
        named(box([13, 7, 3], [15, 13, 13], t), 'wall_e'),
        named(box([3, 7, 3], [13, 8, 13], {'up': '#ramp'}), 'inner'),
        # flared rim
        named(box([0, 13, 0], [16, 14.5, 2], t), 'rim_n'),
        named(box([0, 13, 14], [16, 14.5, 16], t), 'rim_s'),
        named(box([0, 13, 2], [2, 14.5, 14], t), 'rim_w'),
        named(box([14, 13, 2], [16, 14.5, 14], t), 'rim_e'),
        named(box([7, 9.5, 0.5 - 1.0], [9, 11.5, 1], {'north': '#lamp', 'up': '#lamp', 'west': '#lamp', 'east': '#lamp'}), 'lamp'),
    ]
    return els


def dirt(part):
    """Dirt heaps on the floor steps, a few in the back part under the chute and fewer on the front part."""
    steps = {'back': (4, 5, 6), 'front': (1, 3)}[part]
    lo, hi, off = span(part)
    out = []
    for n, i in enumerate(steps):
        u0 = TRAY_END + STEP * i
        f = floor_top(i)
        z0 = u0 + off + 0.5
        out.append(box([4 + n % 2 * 2, f + 0.1, z0], [12 - n % 2, f + 1.5 + 0.3 * (n % 2), z0 + 3], '#dirt'))
    return out


def insert(part):
    lo, hi, off = span(part)
    i = 5 if part == 'back' else 2
    u0 = TRAY_END + STEP * i
    f = floor_top(i)
    z0 = u0 + off + 0.5
    return [box([2, f + 0.15, z0], [14, f + 0.9, z0 + 3], {'up': '#insert', 'down': '#insert', 'north': '#insert', 'south': '#insert',
                                                         'east': '#insert', 'west': '#insert'})]


def model(tex, elements, particle=None):
    full = {k: 'holeydigsite:block/' + v for k, v in tex.items()}
    full['particle'] = full.get(particle or 'wood')
    return {'parent': 'minecraft:block/block', 'textures': full, 'elements': elements}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(obj, fh, indent=2)
        fh.write('\n')


def shift(elements, dz=0.0, dy=0.0):
    out = json.loads(json.dumps(elements))
    for el in out:
        el['from'][2] += dz
        el['to'][2] += dz
        el['from'][1] += dy
        el['to'][1] += dy
    return out


def blockstate():
    parts = []
    rot = {'north': None, 'east': 90, 'south': 180, 'west': 270}

    def add(when, name, face):
        apply = {'model': 'holeydigsite:block/' + name}
        if rot[face]:
            apply['y'] = rot[face]
        parts.append({'when': dict(when, facing=face), 'apply': apply})

    for face in rot:
        for st in STATUSES:
            add({'part': 'back', 'status': st}, f'sluice_box_t2_back_{st}', face)
            add({'part': 'front', 'status': st}, f'sluice_box_t2_front_{st}', face)
            add({'part': 'chute', 'status': st}, f'sluice_box_t2_chute_{st}', face)
        add({'part': 'back', 'dirt': 'true'}, 'sluice_box_t2_dirt_back', face)
        add({'part': 'front', 'dirt': 'true'}, 'sluice_box_t2_dirt_front', face)
        for up in ('riffle', 'mesh', 'seed'):
            add({'part': 'back', 'upgrade': up}, f'sluice_upgrade_long_{up}_back', face)
            add({'part': 'front', 'upgrade2': up}, f'sluice_upgrade_long_{up}_front', face)
    return {'multipart': parts}


def item_model():
    tex = textures('idle')
    # centred on the block: model elements must stay within -16..32, and the tray end reaches past z = 0
    els = shift(channel('front', 'idle'), dz=-8) + shift(channel('back', 'idle'), dz=8) + shift(chute('idle'), dz=8, dy=16)
    m = model(tex, els)
    m['textures']['dirt'] = 'holeydigsite:block/sluice_dirt'
    m['display'] = {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, -1.5, 0], 'scale': [0.38, 0.38, 0.38]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.2, 0.2, 0.2]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, -2, 0], 'scale': [0.4, 0.4, 0.4]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 1.5], 'scale': [0.28, 0.28, 0.28]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.3, 0.3, 0.3]},
    }
    return m


def write_all():
    for st in STATUSES:
        tex = textures(st)
        write(os.path.join(MODELS, 'block', f'sluice_box_t2_back_{st}.json'), model(tex, channel('back', st)))
        write(os.path.join(MODELS, 'block', f'sluice_box_t2_front_{st}.json'), model(tex, channel('front', st)))
        write(os.path.join(MODELS, 'block', f'sluice_box_t2_chute_{st}.json'), model(tex, chute(st)))
    for part in ('back', 'front'):
        m = {'parent': 'minecraft:block/block', 'textures': {'particle': 'holeydigsite:block/sluice_dirt', 'dirt': 'holeydigsite:block/sluice_dirt'},
             'elements': dirt(part)}
        write(os.path.join(MODELS, 'block', f'sluice_box_t2_dirt_{part}.json'), m)
        for up in ('riffle', 'mesh', 'seed'):
            tx = 'holeydigsite:block/sluice_insert_' + up
            write(os.path.join(MODELS, 'block', f'sluice_upgrade_long_{up}_{part}.json'),
                  {'parent': 'minecraft:block/block', 'textures': {'particle': tx, 'insert': tx}, 'elements': insert(part)})
    write(os.path.join(MODELS, 'item', 'sluice_box_t2.json'), item_model())
    write(os.path.join(ASSETS, 'blockstates', 'sluice_box_t2.json'), blockstate())


# ---------------------------------------------------------------- preview renderer (orthographic, z-buffered, textured)
_cache = {}


def tex_image(name, skin=None):
    key = (name, skin)
    if key not in _cache:
        path = os.path.join(TEX, name + '.png')
        if skin:
            sp = os.path.join(TEX, 'skin', skin, name + '.png')
            if os.path.exists(sp):
                path = sp
        img = Image.open(path).convert('RGBA')
        _cache[key] = img.crop((0, 0, img.width, img.width))
    return _cache[key]


NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
LIGHT = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.62, 'west': 0.62}


def auto_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {'north': (16 - x2, 16 - y2, 16 - x1, 16 - y1), 'south': (x1, 16 - y2, x2, 16 - y1),
            'east': (16 - z2, 16 - y2, 16 - z1, 16 - y1), 'west': (z1, 16 - y2, z2, 16 - y1),
            'up': (x1, z1, x2, z2), 'down': (x1, 16 - z2, x2, 16 - z1)}[face]


def face_corners(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {'north': ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1)), 'south': ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2)),
            'east': ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2)), 'west': ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1)),
            'up': ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2)), 'down': ((x1, y1, z2), (x2, y1, z2), (x1, y1, z1))}[face]


def render(parts, cam, skin=None, scale=7.5, size=(420, 420), centre=(8, 12, 0)):
    """parts: list of (elements, texture map name->file, dy, dz)."""
    cl = math.sqrt(sum(c * c for c in cam))
    cam = tuple(c / cl for c in cam)
    fwd = tuple(-c for c in cam)
    right = (fwd[2], 0, -fwd[0])
    rl = math.hypot(right[0], right[2])
    right = (right[0] / rl, 0, right[2] / rl)
    up = (right[1] * fwd[2] - right[2] * fwd[1], right[2] * fwd[0] - right[0] * fwd[2], right[0] * fwd[1] - right[1] * fwd[0])
    w, h = size
    cx, cy = w / 2, h * 0.55

    def proj(p):
        q = (p[0] - centre[0], p[1] - centre[1], p[2] - centre[2])
        return (cx + scale * sum(a * b for a, b in zip(q, right)), cy + scale * sum(a * b for a, b in zip(q, up)),
                sum(a * b for a, b in zip(q, fwd)))

    img = Image.new('RGBA', size, (0, 0, 0, 0))
    px = img.load()
    zbuf = [[1e9] * w for _ in range(h)]
    for elements, tex, dy, dz in parts:
        for el in elements:
            f = (el['from'][0], el['from'][1] + dy, el['from'][2] + dz)
            t = (el['to'][0], el['to'][1] + dy, el['to'][2] + dz)
            for face, spec in el['faces'].items():
                n = NORMALS[face]
                if sum(a * b for a, b in zip(n, cam)) <= 1e-6:
                    continue
                tl, tr, bl = (proj(p) for p in face_corners(face, f, t))
                ux, uy, uz = tr[0] - tl[0], tr[1] - tl[1], tr[2] - tl[2]
                vx, vy, vz = bl[0] - tl[0], bl[1] - tl[1], bl[2] - tl[2]
                det = ux * vy - uy * vx
                if abs(det) < 1e-6:
                    continue
                uv = spec.get('uv') or auto_uv(face, el['from'], el['to'])
                key = spec['texture'].lstrip('#')
                timg = tex_image(tex[key].split('/')[-1], skin)
                k = timg.width / 16
                xs = (tl[0], tr[0], bl[0], tr[0] + vx)
                ys = (tl[1], tr[1], bl[1], tr[1] + vy)
                shade = LIGHT[face]
                for yy in range(max(0, int(min(ys))), min(h, int(max(ys)) + 1)):
                    for xx in range(max(0, int(min(xs))), min(w, int(max(xs)) + 1)):
                        dx, dyy = xx + 0.5 - tl[0], yy + 0.5 - tl[1]
                        s = (dx * vy - dyy * vx) / det
                        q = (ux * dyy - uy * dx) / det
                        if not (0 <= s <= 1 and 0 <= q <= 1):
                            continue
                        z = tl[2] + s * uz + q * vz
                        if z >= zbuf[yy][xx]:
                            continue
                        tu = uv[0] + s * (uv[2] - uv[0])
                        tv = uv[1] + q * (uv[3] - uv[1])
                        c = timg.getpixel((min(timg.width - 1, max(0, int(tu * k))), min(timg.height - 1, max(0, int(tv * k)))))
                        if c[3] < 128:
                            continue
                        zbuf[yy][xx] = z
                        px[xx, yy] = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), 255)
    return img


def preview(status='working', with_inserts=True, with_dirt=True):
    tex = {k: 'holeydigsite:block/' + v for k, v in textures(status).items()}
    tex.update({'dirt': 'sluice_dirt', 'insert': 'sluice_insert_riffle'})
    parts = [(channel('front', status), tex, 0, -16), (channel('back', status), tex, 0, 0), (chute(status), tex, 16, 0)]
    if with_dirt:
        parts += [(dirt('front'), tex, 0, -16), (dirt('back'), tex, 0, 0)]
    if with_inserts:
        t2 = dict(tex, insert='sluice_insert_mesh')
        parts += [(insert('front'), t2, 0, -16), (insert('back'), tex, 0, 0)]
    return parts


def previews():
    os.makedirs(PREVIEW, exist_ok=True)
    sheets = []
    for name, skin, st in (('plain', None, 'working'), ('rustic', 'rustic', 'working'), ('brass', 'brass', 'idle')):
        parts = preview(st)
        front = render(parts, (1.0, 0.6, -1.3), skin)
        back = render(parts, (-1.0, 0.6, 1.3), skin)
        side = render(parts, (1.6, 0.35, 0.0), skin)
        sheet = Image.new('RGBA', (front.width * 3, front.height + 22), (52, 56, 62, 255))
        sheet.alpha_composite(front, (0, 22))
        sheet.alpha_composite(back, (front.width, 22))
        sheet.alpha_composite(side, (front.width * 2, 22))
        ImageDraw.Draw(sheet).text((8, 6), f'long sluice, {name}, {st}: front, back, side (tray end is north / left in the side view)', fill=(235, 235, 235, 255))
        sheet.save(os.path.join(PREVIEW, f'long_sluice_{name}.png'))
        sheets.append(sheet)
    print('previews written to', PREVIEW)


def main():
    write_all()
    print('models, blockstate and item model written')
    if '--no-preview' not in sys.argv:
        previews()


if __name__ == '__main__':
    main()
