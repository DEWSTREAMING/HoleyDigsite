#!/usr/bin/env python3
"""Phase 12 assets: rugged 2-tall Mining Drills (3 tiers), full-height water-powered belts (flat, corners, fast, inclined,
filter), the Gem Cutter and uncut gems. Textures, block/item models and blockstates. Needs Pillow.

    python tools/make_phase12_assets.py

Style: timber + dark iron (matches the Sluice Box and the pipes); the timber is taken from sluice_wood.png so the colours
stay consistent. Old Phase 4/10/11 drill models + textures are replaced (the old scripts would recreate unused files)."""
import json
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'holeydigsite')
TB = os.path.join(ROOT, 'textures', 'block')
TI = os.path.join(ROOT, 'textures', 'item')
MB = os.path.join(ROOT, 'models', 'block')
MI = os.path.join(ROOT, 'models', 'item')
BS = os.path.join(ROOT, 'blockstates')
NS = 'holeydigsite'

IRON = [(38, 38, 42), (58, 59, 64), (82, 83, 89), (112, 113, 120), (146, 147, 154)]
TRIM = {  # tier -> band colours (dark, mid, light)
    '': [(52, 52, 56), (78, 79, 85), (108, 109, 116)],               # Tier 1: dark iron
    '_t2': [(120, 84, 20), (196, 150, 40), (240, 204, 92)],         # Tier 2: Goldchro
    '_t3': [(118, 58, 26), (190, 102, 48), (226, 150, 92)],          # Tier 3: Orchremia (+ Silvane rivets)
}
RIVET = {'': (150, 151, 158), '_t2': (110, 80, 30), '_t3': (205, 215, 225)}
MARK = {'': None, '_t2': 'II', '_t3': 'III'}


def save(img, *path):
    p = os.path.join(*path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def dump(obj, *path):
    p = os.path.join(*path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)


def wood(vertical=False, seed=1):
    """Weathered timber from the Sluice Box's wood (horizontal planks), optionally turned to vertical boards."""
    base = Image.open(os.path.join(TB, 'sluice_wood.png')).convert('RGBA').crop((0, 0, 16, 16))
    rnd = random.Random(seed)
    px = base.load()
    for _ in range(14):  # weathering: a few darker / greyer specks
        x, y = rnd.randrange(16), rnd.randrange(16)
        r, g, b, a = px[x, y]
        k = rnd.choice([0.82, 0.88, 1.06])
        px[x, y] = (int(r * k), int(g * k), int(b * k * 0.98), a)
    return base.rotate(90) if vertical else base


def band(img, rows, tier):
    d, m, l = TRIM[tier]
    for x in range(16):
        for i, y in enumerate(rows):
            img.putpixel((x, y), l if i == 0 else (m if i < len(rows) - 1 else d))


def rivets(img, y, xs, tier):
    for x in xs:
        img.putpixel((x, y), RIVET[tier])


def posts(img, tier):
    d, m, _ = TRIM[tier]
    for y in range(16):
        img.putpixel((0, y), IRON[1])
        img.putpixel((15, y), IRON[0])


def mark(img, text, tier):
    if not text:
        return
    _, m, l = TRIM[tier]
    x0 = 8 - (len(text) * 2 - 1) // 2
    for i in range(len(text)):
        for y in range(5, 10):
            img.putpixel((x0 + i * 2, y), l if y == 5 else m)


def flange(img, cx, cy, r=5):
    """Round iron pipe flange with bolts (like the pipes / sluice port)."""
    d = ImageDraw.Draw(img)
    d.ellipse((cx - r, cy - r, cx + r - 1, cy + r - 1), fill=IRON[2], outline=IRON[0])
    d.ellipse((cx - r + 2, cy - r + 2, cx + r - 3, cy + r - 3), fill=(24, 26, 30), outline=IRON[3])
    for bx, by in [(cx - r + 1, cy - 1), (cx + r - 2, cy - 1), (cx - 1, cy - r + 1), (cx - 1, cy + r - 2)]:
        img.putpixel((bx, by), IRON[4])


# ------------------------------------------------------------------------------------------------
# Mining drills (2 tall)
# ------------------------------------------------------------------------------------------------

def drill_textures(tier):
    n = 'mining_drill' + tier
    side = wood(True, 3)
    posts(side, tier)
    band(side, [0, 1], tier)
    band(side, [13, 14, 15], tier)
    rivets(side, 14, [3, 8, 12], tier)
    rivets(side, 1, [3, 12], tier)
    save(side, TB, n + '_lower_side.png')

    front = side.copy()
    d = ImageDraw.Draw(front)
    d.ellipse((2, 2, 13, 13), fill=IRON[1], outline=IRON[0])
    d.ellipse((4, 4, 11, 11), fill=(22, 20, 18))
    save(front, TB, n + '_lower_front.png')

    back = side.copy()
    flange(back, 8, 8, 6)
    save(back, TB, n + '_lower_back.png')

    bottom = wood(False, 5)
    band(bottom, [0, 1], tier)
    band(bottom, [14, 15], tier)
    save(bottom, TB, n + '_bottom.png')

    up_side = wood(True, 7)
    posts(up_side, tier)
    band(up_side, [0, 1, 2], tier)
    band(up_side, [14, 15], tier)
    rivets(up_side, 1, [3, 8, 12], tier)
    mark(up_side, MARK[tier], tier)
    save(up_side, TB, n + '_upper_side.png')

    up_front = up_side.copy() if not MARK[tier] else wood(True, 7)
    if MARK[tier]:
        posts(up_front, tier)
        band(up_front, [0, 1, 2], tier)
        band(up_front, [14, 15], tier)
    d = ImageDraw.Draw(up_front)
    d.rectangle((4, 9, 11, 15), fill=IRON[1], outline=IRON[0])  # chute opening at the bottom front
    d.rectangle((6, 11, 9, 15), fill=(24, 22, 20))
    save(up_front, TB, n + '_upper_front.png')

    top = wood(False, 9)
    band(top, [0, 1], tier)
    band(top, [14, 15], tier)
    for y in range(16):
        top.putpixel((7, y), TRIM[tier][1])
        top.putpixel((8, y), TRIM[tier][0])
    rivets(top, 4, [7], tier)
    rivets(top, 11, [8], tier)
    save(top, TB, n + '_upper_top.png')


def drill_shared_textures():
    # chunky iron bit, static + spinning (4 frames)
    def bit(shift):
        img = Image.new('RGBA', (16, 16), IRON[2])
        for y in range(16):
            for x in range(16):
                v = (x + y + shift * 4) % 8
                img.putpixel((x, y), IRON[3] if v < 2 else IRON[1] if v < 4 else IRON[2])
        d = ImageDraw.Draw(img)
        d.rectangle((0, 0, 15, 15), outline=IRON[0])
        return img
    save(bit(0), TB, 'mining_drill_bit.png')
    strip = Image.new('RGBA', (16, 64))
    for i in range(4):
        strip.paste(bit(i), (0, i * 16))
    save(strip, TB, 'mining_drill_bit_working.png')
    dump({'animation': {'frametime': 2}}, TB, 'mining_drill_bit_working.png.mcmeta')
    # spout (iron chute)
    sp = Image.new('RGBA', (16, 16), IRON[2])
    d = ImageDraw.Draw(sp)
    d.rectangle((0, 0, 15, 15), outline=IRON[0])
    for x in range(1, 15):
        sp.putpixel((x, 1), IRON[4])
    save(sp, TB, 'mining_drill_spout.png')
    # oil-lantern lamp per status: frame + glass
    glass = {'working': [(255, 214, 120), (255, 170, 60), (220, 120, 30)],
             'stopped': [(255, 120, 100), (210, 50, 40), (150, 30, 25)],
             'no_power': [(70, 78, 90), (48, 54, 64), (34, 38, 46)]}
    for status, g in glass.items():
        img = Image.new('RGBA', (16, 16), IRON[0])
        d = ImageDraw.Draw(img)
        d.rectangle((2, 3, 13, 13), fill=g[1])
        d.rectangle((4, 5, 11, 11), fill=g[0])
        d.rectangle((6, 7, 9, 9), fill=(255, 245, 210) if status == 'working' else g[0])
        for y in (2, 14):
            for x in range(1, 15):
                img.putpixel((x, y), IRON[2])
        for x in (1, 14):
            for y in range(2, 15):
                img.putpixel((x, y), IRON[2])
        for y in range(3, 14, 3):
            img.putpixel((7, y), IRON[1])
            img.putpixel((8, y), IRON[1])
        save(img, TB, 'mining_drill_lantern_%s.png' % status)


def face(tex, uv=None, cull=None, rotation=None):
    f = {'texture': tex, 'uv': uv or [0, 0, 16, 16]}
    if cull:
        f['cullface'] = cull
    if rotation:
        f['rotation'] = rotation
    return f


def box(frm, to, faces, rot=None):
    e = {'from': frm, 'to': to, 'faces': faces}
    if rot:
        e['rotation'] = rot
    return e


def drill_lower_elements(dy=0):
    return [
        box([0, dy, 0], [16, 16 + dy, 16], {
            'north': face('#front', cull=None if dy else 'north'), 'south': face('#back', cull=None if dy else 'south'),
            'east': face('#side', cull=None if dy else 'east'), 'west': face('#side', cull=None if dy else 'west'),
            'down': face('#bottom', cull=None if dy else 'down'), 'up': face('#bottom')}),
        box([4, 4 + dy, -3], [12, 12 + dy, 0], {  # the chunky bit sticking out of the front
            'north': face('#bit'), 'east': face('#bit', [0, 4, 3, 12]), 'west': face('#bit', [13, 4, 16, 12]),
            'up': face('#bit', [4, 0, 12, 3]), 'down': face('#bit', [4, 13, 12, 16])}),
        box([6, 6 + dy, -5], [10, 10 + dy, -3], {
            'north': face('#bit', [6, 6, 10, 10]), 'east': face('#bit', [0, 6, 2, 10]), 'west': face('#bit', [14, 6, 16, 10]),
            'up': face('#bit', [6, 0, 10, 2]), 'down': face('#bit', [6, 14, 10, 16])}),
    ]


def drill_upper_elements(dy=0):
    return [
        box([0, dy, 0], [16, 16 + dy, 16], {
            'north': face('#front', cull=None if dy else 'north'), 'south': face('#side', cull=None if dy else 'south'),
            'east': face('#side', cull=None if dy else 'east'), 'west': face('#side', cull=None if dy else 'west'),
            'up': face('#top', cull=None if dy else 'up'), 'down': face('#top')}),
        box([5, dy, -4], [11, 3 + dy, 0], {  # spout over the belt / pan in front
            'north': face('#spout', [5, 13, 11, 16]), 'east': face('#spout', [0, 13, 4, 16]), 'west': face('#spout', [12, 13, 16, 16]),
            'up': face('#spout', [5, 0, 11, 4]), 'down': face('#spout', [5, 12, 11, 16])}),
        box([6, 16 + dy, 6], [10, 21 + dy, 10], {  # oil lantern on top
            'north': face('#lamp', [4, 3, 12, 14]), 'south': face('#lamp', [4, 3, 12, 14]), 'east': face('#lamp', [4, 3, 12, 14]),
            'west': face('#lamp', [4, 3, 12, 14]), 'up': face('#lamp', [2, 2, 6, 6])}),
        box([7, 21 + dy, 7], [9, 22 + dy, 9], {'north': face('#spout', [0, 0, 2, 1]), 'south': face('#spout', [0, 0, 2, 1]),
                                               'east': face('#spout', [0, 0, 2, 1]), 'west': face('#spout', [0, 0, 2, 1]), 'up': face('#spout', [0, 0, 2, 2])}),
    ]


def drill_models(tier):
    n = 'mining_drill' + tier
    t = lambda s: '%s:block/%s' % (NS, s)
    for working in (False, True):
        dump({'parent': 'minecraft:block/block',
              'textures': {'particle': t(n + '_lower_side'), 'front': t(n + '_lower_front'), 'back': t(n + '_lower_back'),
                           'side': t(n + '_lower_side'), 'bottom': t(n + '_bottom'),
                           'bit': t('mining_drill_bit_working' if working else 'mining_drill_bit')},
              'elements': drill_lower_elements()}, MB, n + '_lower' + ('_working' if working else '') + '.json')
    for status in ('working', 'stopped', 'no_power'):
        dump({'parent': 'minecraft:block/block',
              'textures': {'particle': t(n + '_upper_side'), 'front': t(n + '_upper_front'), 'side': t(n + '_upper_side'),
                           'top': t(n + '_upper_top'), 'spout': t('mining_drill_spout'), 'lamp': t('mining_drill_lantern_' + status)},
              'elements': drill_upper_elements()}, MB, n + '_upper_' + status + '.json')
    # item: both halves, scaled down
    dump({'parent': 'minecraft:block/block',
          'textures': {'particle': t(n + '_lower_side'), 'front': t(n + '_lower_front'), 'back': t(n + '_lower_back'),
                       'side': t(n + '_lower_side'), 'bottom': t(n + '_bottom'), 'bit': t('mining_drill_bit'),
                       'top': t(n + '_upper_top'), 'spout': t('mining_drill_spout'), 'lamp': t('mining_drill_lantern_working'),
                       'ufront': t(n + '_upper_front'), 'uside': t(n + '_upper_side')},
          'elements': drill_lower_elements(-8) + [
              {**e, 'faces': {k: {**v, 'texture': {'#front': '#ufront', '#side': '#uside'}.get(v['texture'], v['texture'])}
                              for k, v in e['faces'].items()}} for e in drill_upper_elements(8)],
          'display': {
              'gui': {'rotation': [30, 225, 0], 'translation': [0, -1.5, 0], 'scale': [0.34, 0.34, 0.34]},
              'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.18, 0.18, 0.18]},
              'fixed': {'rotation': [0, 0, 0], 'translation': [0, -4, 0], 'scale': [0.36, 0.36, 0.36]},
              'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 1.5, 1.5], 'scale': [0.25, 0.25, 0.25]},
              'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.28, 0.28, 0.28]},
              'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.28, 0.28, 0.28]}}},
         MI, n + '.json')
    variants = {}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for status in ('working', 'stopped', 'no_power'):
            lo = {'model': '%s:block/%s_lower%s' % (NS, n, '_working' if status == 'working' else '')}
            up = {'model': '%s:block/%s_upper_%s' % (NS, n, status)}
            if y:
                lo['y'] = y
                up['y'] = y
            variants['facing=%s,half=lower,status=%s' % (f, status)] = lo
            variants['facing=%s,half=upper,status=%s' % (f, status)] = up
    dump({'variants': variants}, BS, n + '.json')


def remove_old_drill_files():
    for tier in ('', '_t2', '_t3'):
        n = 'mining_drill' + tier
        for s in ('_no_power', '_stopped', '_working'):
            p = os.path.join(MB, n + s + '.json')
            if os.path.exists(p):
                os.remove(p)
        for s in ('_back', '_bottom', '_front', '_front_working', '_side_no_power', '_side_stopped', '_side_working', '_top'):
            for ext in ('.png', '.png.mcmeta'):
                p = os.path.join(TB, n + s + ext)
                if os.path.exists(p):
                    os.remove(p)


# ------------------------------------------------------------------------------------------------
# Belts
# ------------------------------------------------------------------------------------------------

BELT_TRIM = {'clump_belt': IRON, 'fast_clump_belt': [(120, 84, 20), (170, 124, 30), (220, 178, 64), (240, 204, 92), (250, 226, 140)],
             'filter_belt': [(20, 70, 90), (30, 110, 140), (60, 160, 190), (110, 200, 220), (170, 230, 240)]}
PIPE = [(70, 72, 78), (120, 122, 130), (160, 162, 170), (196, 198, 206)]


def belt_frame(name, trim):
    side = wood(False, 11)
    for x in range(16):  # iron rail at the top, built-in water pipe along the bottom
        side.putpixel((x, 0), trim[3])
        side.putpixel((x, 1), trim[2])
        side.putpixel((x, 2), trim[0])
        side.putpixel((x, 10), (40, 30, 22))
        side.putpixel((x, 11), PIPE[3])
        side.putpixel((x, 12), PIPE[2])
        side.putpixel((x, 13), PIPE[1])
        side.putpixel((x, 14), PIPE[0])
    for x in (3, 12):
        for y in range(10, 16):
            side.putpixel((x, y), IRON[1] if y in (10, 15) else IRON[2])
    for x in (2, 7, 13):
        side.putpixel((x, 1), trim[4] if len(trim) > 4 else IRON[4])
    save(side, TB, name + '_frame_side.png')
    end = wood(True, 12)
    for x in range(16):
        end.putpixel((x, 0), trim[3])
        end.putpixel((x, 1), trim[2])
        end.putpixel((x, 2), trim[0])
    flange(end, 8, 12, 4)
    save(end, TB, name + '_frame_end.png')
    bottom = wood(False, 13)
    save(bottom, TB, name + '_frame_bottom.png')


def corner_frame(straight, frame):
    """Phase 12.1: one corner tile made from a straight belt tile by bending it around the NW corner (polar remap).
    The straight tile runs towards v=0 (north) with u 0..15 across (rails at u 0-1 and 14-15). On the corner the
    belt comes in at the west edge and leaves at the north edge: radius from the NW corner = u, angle = v
    (90 deg = v 16 at the west edge, 0 deg = v 0 at the north edge). So the stripes become spokes that move round the
    curve exactly as fast as the straight belt's stripes (same 4 px period, 4 periods per quarter). Outside the
    belt (the far corner) is the timber deck."""
    import math
    img = frame.copy()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5, y + 0.5)
            if r >= 16.0:
                continue
            ang = math.degrees(math.atan2(y + 0.5, x + 0.5))  # 0 = along the north edge, 90 = along the west edge
            u = min(15, int(r))
            v = min(15, int(ang / 90.0 * 16.0))
            img.putpixel((x, y), straight.getpixel((u, v)))
    return img


def belt_top_corner(name, src_top, src_moving):
    """Corner tops (LEFT: input from the west edge, out of the north edge; centre at the NW corner; RIGHT = mirror),
    static + animated, from the straight belt's own textures (Phase 12.1)."""
    frame = wood(False, 14)
    top = Image.open(os.path.join(TB, src_top)).convert('RGBA').crop((0, 0, 16, 16))
    img = corner_frame(top, frame)
    save(img, TB, name + '_corner_left.png')
    save(img.transpose(Image.FLIP_LEFT_RIGHT), TB, name + '_corner_right.png')
    anim = Image.open(os.path.join(TB, src_moving)).convert('RGBA')
    frames = anim.height // 16
    left = Image.new('RGBA', (16, 16 * frames))
    for k in range(frames):
        left.paste(corner_frame(anim.crop((0, 16 * k, 16, 16 * k + 16)), frame), (0, 16 * k))
    save(left, TB, name + '_corner_left_moving.png')
    save(left.transpose(Image.FLIP_LEFT_RIGHT), TB, name + '_corner_right_moving.png')
    with open(os.path.join(TB, src_moving + '.mcmeta')) as f:
        meta = json.load(f)
    for side in ('left', 'right'):  # same timing as the straight belt
        dump(meta, TB, name + '_corner_' + side + '_moving.png.mcmeta')


def belt_cube(top_tex, top_uv=None):
    return box([0, 0, 0], [16, 16, 16], {
        'up': face(top_tex, top_uv), 'down': face('#bottom', cull='down'),
        'north': face('#end', cull='north'), 'south': face('#end', cull='south'),
        'west': face('#side', cull='west'), 'east': face('#side', cull='east')})


def belt_textures(prefix):
    t = lambda s: '%s:block/%s' % (NS, s)
    return {'particle': t(prefix + '_frame_side'), 'side': t(prefix + '_frame_side'), 'end': t(prefix + '_frame_end'),
            'bottom': t(prefix + '_frame_bottom')}


def spout_model():
    t = '%s:block/mining_drill_spout' % NS
    dump({'parent': 'minecraft:block/block', 'textures': {'particle': t, 'spout': t}, 'elements': [
        box([4, 13, -3], [12, 16, 0], {'north': face('#spout', [4, 13, 12, 16]), 'east': face('#spout', [0, 13, 3, 16]),
                                       'west': face('#spout', [13, 13, 16, 16]), 'down': face('#spout', [4, 0, 12, 3]),
                                       'up': face('#spout', [4, 0, 12, 3])}),
        box([4, 15.9, -3], [12, 17, 0], {'east': face('#spout', [0, 0, 3, 1]), 'west': face('#spout', [13, 0, 16, 1]),
                                         'north': face('#spout', [4, 0, 12, 1])})]}, MB, 'clump_belt_spout.json')


def flat_belt(name, frame, top, top_moving):
    tx = belt_textures(frame)
    t = lambda s: '%s:block/%s' % (NS, s)
    dump({'parent': 'minecraft:block/block', 'textures': {**tx, 'top': t(top)}, 'elements': [belt_cube('#top')]}, MB, name + '.json')
    dump({'parent': 'minecraft:block/block', 'textures': {**tx, 'top': t(top_moving)}, 'elements': [belt_cube('#top')]},
         MB, name + '_moving.json')
    for side in ('left', 'right'):
        for moving in ('', '_moving'):
            dump({'parent': 'minecraft:block/block', 'textures': {**tx, 'top': t(frame + '_corner_' + side + moving)},
                  'elements': [belt_cube('#top')]}, MB, name + '_corner_' + side + moving + '.json')
    dump({'parent': '%s:block/%s' % (NS, name)}, MI, name + '.json')


def ys():
    return (('north', 0), ('east', 90), ('south', 180), ('west', 270))


def moving_when(f, extra):
    return {'facing': f, 'mode': 'moving', 'powered': 'false', 'water': 'true', **extra}


def static_when(f, extra):
    return {'OR': [{'facing': f, 'mode': 'empty|blocked', **extra}, {'facing': f, 'powered': 'true', **extra},
                   {'facing': f, 'water': 'false', **extra}]}


def apply(model, y):
    a = {'model': model}
    if y:
        a['y'] = y
    return a


def flat_belt_states(name):
    parts = []
    for f, y in ys():
        parts.append({'when': moving_when(f, {'shape': 'straight'}), 'apply': apply('%s:block/%s_moving' % (NS, name), y)})
        parts.append({'when': static_when(f, {'shape': 'straight'}), 'apply': apply('%s:block/%s' % (NS, name), y)})
        for side in ('left', 'right'):  # Phase 12.1: corners animate like straight belts
            parts.append({'when': moving_when(f, {'shape': side}),
                          'apply': apply('%s:block/%s_corner_%s_moving' % (NS, name, side), y)})
            parts.append({'when': static_when(f, {'shape': side}), 'apply': apply('%s:block/%s_corner_%s' % (NS, name, side), y)})
        parts.append({'when': {'facing': f, 'spout': 'true'}, 'apply': apply('%s:block/clump_belt_spout' % NS, y)})
    dump({'multipart': parts}, BS, name + '.json')


INCLINE_RAIL = 3.0  # thickness (px, at right angles to the slope) of the sloped iron rail band under the belt


def incline_elements():
    """Phase 12.1 Inclined Belt, front = north = high end. All 45 degree elements turn about the block centre with
    +45 on x (the north end goes UP) and NO rescale, so a length of 16*sqrt(2) spans the block exactly corner to corner.
      - belt: a zero-thickness plate whose top is the diagonal from the back-bottom edge (z16, y0) to the front-top edge
        (z0, y16); full width, the same top texture (and uv) as a flat belt, so the rails line up and the animation
        (towards v=0 = north) runs up the slope. Zero thickness = it can't poke out of the block at the two edges.
      - rail band: a 3 px thick beam right under the plate, shortened by its thickness at both ends so its bottom corners
        stay inside the block; its sides show the iron rail rows of the flat belt's side texture.
      - timber body: 1 px stairs under the slope (tops on the diagonal at each step's back edge, so they never rise above
        the belt and their corners hide inside the rail band), auto-uv so the side texture (pipe, posts) reads as one
        piece, inset 0.02 px from the sides to avoid z-fighting with the rail band."""
    half = 8 * 2 ** 0.5  # 11.3137
    rot = {'origin': [8, 8, 8], 'axis': 'x', 'angle': 45}
    r = lambda v: round(v, 4)
    plate = box([0, 8, r(8 - half)], [16, 8, r(8 + half)], {'up': face('#top'), 'down': face('#bottom')}, rot)
    t = INCLINE_RAIL
    rail = box([0, 8 - t, r(8 - half + t)], [16, 8, r(8 + half - t)], {
        'west': face('#side', [0, 0, 16, t]), 'east': face('#side', [0, 0, 16, t]), 'down': face('#bottom'),
        'north': face('#end', [0, 0, 16, t]), 'south': face('#end', [0, 0, 16, t])}, rot)
    els = [plate, rail]
    inset = 0.02
    for i in range(15):  # step z i..i+1, top = the diagonal at z = i+1
        top = 15 - i
        faces = {'down': face('#bottom', [inset, i, 16 - inset, i + 1], 'down'),
                 'west': {'texture': '#side'}, 'east': {'texture': '#side'}, 'up': {'texture': '#bottom'},
                 'south': {'texture': '#end'}}
        if i == 0:
            faces['north'] = face('#end', [0, 16 - top, 16, 16], 'north')
        els.append(box([inset, 0, i], [16 - inset, top, i + 1], faces))
    return els


def incline_belt(name, frame, top, top_moving):
    """Ramp from the back bottom edge to the front top edge (Phase 12.1 rebuild, see incline_elements)."""
    tx = belt_textures(frame)
    t = lambda s: '%s:block/%s' % (NS, s)
    for moving in (False, True):
        dump({'parent': 'minecraft:block/block', 'textures': {**tx, 'top': t(top_moving if moving else top)},
              'elements': incline_elements()}, MB, name + ('_moving' if moving else '') + '.json')
    dump({'parent': '%s:block/%s' % (NS, name)}, MI, name + '.json')
    parts = []
    for f, y in ys():
        parts.append({'when': moving_when(f, {}), 'apply': apply('%s:block/%s_moving' % (NS, name), y)})
        parts.append({'when': static_when(f, {}), 'apply': apply('%s:block/%s' % (NS, name), y)})
        parts.append({'when': {'facing': f, 'spout': 'true'}, 'apply': apply('%s:block/clump_belt_spout' % NS, y)})
    dump({'multipart': parts}, BS, name + '.json')


def filter_belt():
    belt_frame('filter_belt', BELT_TRIM['filter_belt'])
    top = Image.open(os.path.join(TB, 'clump_belt_top.png')).convert('RGBA').crop((0, 0, 16, 16))
    arrow = (90, 220, 235, 255)
    for right in (False, True):
        img = top.copy()
        d = ImageDraw.Draw(img)
        d.rectangle((5, 5, 10, 10), outline=(30, 110, 140, 255))  # filter frame in the middle
        # arrow from the middle to the output edge (model faces north: west = left, east = right)
        if right:
            d.line((9, 8, 14, 8), fill=arrow)
            d.line((12, 6, 14, 8), fill=arrow)
            d.line((12, 10, 14, 8), fill=arrow)
        else:
            d.line((1, 8, 6, 8), fill=arrow)
            d.line((1, 8, 3, 6), fill=arrow)
            d.line((1, 8, 3, 10), fill=arrow)
        save(img, TB, 'filter_belt_top_%s.png' % ('right' if right else 'left'))
        tx = belt_textures('filter_belt')
        dump({'parent': 'minecraft:block/block',
              'textures': {**tx, 'top': '%s:block/filter_belt_top_%s' % (NS, 'right' if right else 'left')},
              'elements': [belt_cube('#top')]}, MB, 'filter_belt_%s.json' % ('right' if right else 'left'))
    dump({'parent': '%s:block/filter_belt_left' % NS}, MI, 'filter_belt.json')
    parts = []
    for f, y in ys():
        for right in (False, True):
            parts.append({'when': {'facing': f, 'out_right': 'true' if right else 'false'},
                          'apply': apply('%s:block/filter_belt_%s' % (NS, 'right' if right else 'left'), y)})
        parts.append({'when': {'facing': f, 'spout': 'true'}, 'apply': apply('%s:block/clump_belt_spout' % NS, y)})
    dump({'multipart': parts}, BS, 'filter_belt.json')


def gem_cutter():
    housing = wood(True, 21)
    for x in range(16):
        for y in (0, 1, 14, 15):
            housing.putpixel((x, y), IRON[2] if y in (1, 14) else IRON[0])
    rivets(housing, 1, [3, 8, 12], '')
    rivets(housing, 14, [3, 8, 12], '')
    save(housing, TB, 'gem_cutter_housing.png')
    wheel = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    import math
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            ang = math.degrees(math.atan2(y - 7.5, x - 7.5)) % 30
            if r <= 6.2 or (r <= 7.8 and ang < 14):
                c = IRON[3] if r > 5 else IRON[2] if r > 2 else IRON[0]
                if r <= 5 and int(math.degrees(math.atan2(y - 7.5, x - 7.5)) + 360) % 60 < 8:
                    c = IRON[4]
                wheel.putpixel((x, y), c + (255,))
    save(wheel, TB, 'gem_cutter_wheel.png')
    tx = belt_textures('clump_belt')
    t = lambda s: '%s:block/%s' % (NS, s)
    housing_els = [
        box([0, 16, 4], [3, 25, 12], {'north': face('#housing', [0, 0, 3, 9]), 'south': face('#housing', [13, 0, 16, 9]),
                                      'west': face('#housing', [4, 0, 12, 9]), 'east': face('#housing', [4, 0, 12, 9]),
                                      'up': face('#housing', [0, 4, 3, 12])}),
        box([13, 16, 4], [16, 25, 12], {'north': face('#housing', [13, 0, 16, 9]), 'south': face('#housing', [0, 0, 3, 9]),
                                        'west': face('#housing', [4, 0, 12, 9]), 'east': face('#housing', [4, 0, 12, 9]),
                                        'up': face('#housing', [13, 4, 16, 12])}),
        box([0, 25, 3], [16, 29, 13], {'north': face('#housing', [0, 0, 16, 4]), 'south': face('#housing', [0, 0, 16, 4]),
                                       'west': face('#housing', [3, 0, 13, 4]), 'east': face('#housing', [3, 0, 13, 4]),
                                       'up': face('#housing', [0, 3, 16, 13]), 'down': face('#housing', [0, 3, 16, 13])}),
        box([7.5, 16.5, 3.5], [8.5, 25.5, 12.5], {'west': face('#wheel', [0.5, 0.5, 15.5, 15.5]),
                                                  'east': face('#wheel', [0.5, 0.5, 15.5, 15.5])}),
        box([3, 22, 7.5], [13, 23, 8.5], {'north': face('#housing', [0, 0, 10, 1]), 'south': face('#housing', [0, 0, 10, 1]),
                                          'up': face('#housing', [0, 0, 10, 1]), 'down': face('#housing', [0, 0, 10, 1])}),
    ]
    for moving in (False, True):
        dump({'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
              'textures': {**tx, 'top': t('clump_belt_top_moving' if moving else 'clump_belt_top'), 'housing': t('gem_cutter_housing'),
                           'wheel': t('gem_cutter_wheel'), 'particle': t('gem_cutter_housing')},
              'elements': [belt_cube('#top')] + housing_els}, MB, 'gem_cutter' + ('_moving' if moving else '') + '.json')
    dump({'parent': '%s:block/gem_cutter' % NS, 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, -2, 0], 'scale': [0.45, 0.45, 0.45]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, -2, 0], 'scale': [0.45, 0.45, 0.45]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]}}}, MI, 'gem_cutter.json')
    parts = []
    for f, y in ys():
        parts.append({'when': moving_when(f, {}), 'apply': apply('%s:block/gem_cutter_moving' % NS, y)})
        parts.append({'when': static_when(f, {}), 'apply': apply('%s:block/gem_cutter' % NS, y)})
        parts.append({'when': {'facing': f, 'spout': 'true'}, 'apply': apply('%s:block/clump_belt_spout' % NS, y)})
    dump({'multipart': parts}, BS, 'gem_cutter.json')


# ------------------------------------------------------------------------------------------------
# Uncut gems
# ------------------------------------------------------------------------------------------------

GEMS = {'ruby': [(92, 26, 34), (140, 44, 52), (176, 74, 82), (186, 140, 142)],
        'sapphire': [(28, 42, 92), (44, 72, 140), (76, 108, 176), (150, 160, 190)],
        'topaz': [(110, 70, 22), (168, 118, 40), (206, 158, 70), (214, 196, 150)]}
STONE = [(84, 80, 76), (112, 107, 101), (138, 132, 124)]
SIZES = {'small': 3.0, 'medium': 4.6, 'large': 6.2}


def uncut_gem(gem, size, seed):
    rnd = random.Random(seed)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    r0 = SIZES[size]
    import math
    # an irregular lump: radius wobbles with the angle
    wob = [rnd.uniform(0.75, 1.15) for _ in range(8)]
    pal = GEMS[gem]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 8.0
            ang = (math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 8
            i = int(ang) % 8
            f = ang - int(ang)
            rr = r0 * (wob[i] * (1 - f) + wob[(i + 1) % 8] * f)
            d = math.hypot(dx, dy)
            if d <= rr:
                light = (-dx - dy) / (rr * 1.6)
                c = pal[2] if light > 0.35 else pal[1] if light > -0.25 else pal[0]
                if rnd.random() < 0.18:
                    c = pal[3]  # cloudy specks
                img.putpixel((x, y), c + (255,))
    # bits of stone stuck to it
    for _ in range(int(r0)):
        x, y = rnd.randrange(16), rnd.randrange(16)
        if img.getpixel((x, y))[3] and rnd.random() < 0.9:
            for ddx, ddy in ((0, 0), (1, 0), (0, 1)):
                if 0 <= x + ddx < 16 and 0 <= y + ddy < 16 and img.getpixel((x + ddx, y + ddy))[3]:
                    img.putpixel((x + ddx, y + ddy), rnd.choice(STONE) + (255,))
    # dark outline
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if not img.getpixel((x, y))[3] and any(0 <= x + a < 16 and 0 <= y + b < 16 and img.getpixel((x + a, y + b))[3]
                                                   for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), tuple(max(0, c - 40) for c in pal[0]) + (255,))
    return out


def belts_only():
    """Phase 12.1: `python tools/make_phase12_assets.py belts` regenerates only the belt corners + incline files."""
    belt_top_corner('clump_belt', 'clump_belt_top.png', 'clump_belt_top_moving.png')
    belt_top_corner('fast_clump_belt', 'fast_clump_belt_top.png', 'fast_clump_belt_top_moving.png')
    flat_belt('clump_belt', 'clump_belt', 'clump_belt_top', 'clump_belt_top_moving')
    flat_belt('fast_clump_belt', 'fast_clump_belt', 'fast_clump_belt_top', 'fast_clump_belt_top_moving')
    flat_belt_states('clump_belt')
    flat_belt_states('fast_clump_belt')
    incline_belt('incline_belt', 'clump_belt', 'clump_belt_top', 'clump_belt_top_moving')
    incline_belt('fast_incline_belt', 'fast_clump_belt', 'fast_clump_belt_top', 'fast_clump_belt_top_moving')
    print('Phase 12.1 belt assets written')


def main():
    remove_old_drill_files()
    drill_shared_textures()
    for tier in ('', '_t2', '_t3'):
        drill_textures(tier)
        drill_models(tier)
    belt_frame('clump_belt', BELT_TRIM['clump_belt'])
    belt_frame('fast_clump_belt', BELT_TRIM['fast_clump_belt'])
    belt_top_corner('clump_belt', 'clump_belt_top.png', 'clump_belt_top_moving.png')
    belt_top_corner('fast_clump_belt', 'fast_clump_belt_top.png', 'fast_clump_belt_top_moving.png')
    spout_model()
    flat_belt('clump_belt', 'clump_belt', 'clump_belt_top', 'clump_belt_top_moving')
    flat_belt('fast_clump_belt', 'fast_clump_belt', 'fast_clump_belt_top', 'fast_clump_belt_top_moving')
    flat_belt_states('clump_belt')
    flat_belt_states('fast_clump_belt')
    incline_belt('incline_belt', 'clump_belt', 'clump_belt_top', 'clump_belt_top_moving')
    incline_belt('fast_incline_belt', 'fast_clump_belt', 'fast_clump_belt_top', 'fast_clump_belt_top_moving')
    filter_belt()
    gem_cutter()
    for old in ('clump_belt_side', 'clump_belt_end', 'clump_belt_bottom', 'clump_belt_spout', 'fast_clump_belt_side',
                'fast_clump_belt_end', 'fast_clump_belt_bottom'):
        p = os.path.join(TB, old + '.png')
        if os.path.exists(p):
            os.remove(p)
    seed = 100
    for gem in GEMS:
        for size in SIZES:
            save(uncut_gem(gem, size, seed), TI, 'uncut_%s_%s.png' % (gem, size))
            dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': '%s:item/uncut_%s_%s' % (NS, gem, size)}},
                 MI, 'uncut_%s_%s.json' % (gem, size))
            seed += 1
    print('Phase 12 assets written')


if __name__ == '__main__':
    import sys
    belts_only() if sys.argv[1:] == ['belts'] else main()
