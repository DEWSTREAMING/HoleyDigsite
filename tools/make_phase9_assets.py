"""Generates the Phase 9 textures AND the model / blockstate JSON (deterministic; needs Pillow).

    python tools/make_phase9_assets.py

Textures (16x16, vanilla style): Lava Intake + Lava Pipe (dark iron, orange glow window when live), Forge (deepslate
bricks, coal front - animated glow when lit), Crucible (clay-orange cup), molten metal (animated) and solid chunks per
metal, Mold (iron cast), ingot items.
Models: lava_intake(_active, collars), lava_pipe core/side (+ filled), forge_unlit / forge_lit, crucible / crucible_tipped,
crucible_molten_<metal>_<fill>, crucible_chunks_<metal>, mold, mold_metal_<metal>_<hot|solid>_<level>, item models.
Glowing faces use NeoForge's per-face "neoforge_data": {"block_light": 15} (full-bright, no shader needed).
"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'holeydigsite')
BLOCK = os.path.join(RES, 'textures', 'block')
ITEM = os.path.join(RES, 'textures', 'item')
MODELS = os.path.join(RES, 'models')
STATES = os.path.join(RES, 'blockstates')
T = (0, 0, 0, 0)
GLOW = {"block_light": 15, "sky_light": 15}


def new(h=16):
    return Image.new('RGBA', (16, h), T)


def save(img, folder, name, frametime=None, interpolate=False):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + '.png'))
    meta = os.path.join(folder, name + '.png.mcmeta')
    if frametime:
        with open(meta, 'w') as f:
            json.dump({"animation": {"frametime": frametime, "interpolate": interpolate}}, f)
            f.write('\n')


def rgba(c, a=255):
    return (int(c[0]), int(c[1]), int(c[2]), a)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def write_json(folder, name, data):
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, name + '.json'), 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


# ------------------------------------------------------------------------------------------------ palettes
IRON = [(38, 38, 44), (50, 50, 58), (62, 62, 70), (76, 76, 86)]        # dark iron (lava kit)
LAVA = [(120, 30, 8), (190, 60, 10), (236, 110, 20), (255, 170, 50), (255, 222, 120)]
BRICK = [(44, 44, 50), (58, 58, 64), (70, 70, 76), (84, 84, 90)]       # deepslate-ish bricks
CLAY = [(120, 60, 34), (146, 76, 42), (170, 92, 52), (192, 112, 66)]   # crucible clay
METALS = {
    # (molten colours dark..bright, solid colours dark..bright)
    'folkiron': ([(205, 95, 30), (240, 135, 50), (255, 180, 95), (255, 228, 175)],
                 [(78, 76, 80), (104, 102, 106), (132, 128, 130), (168, 162, 160)]),
    'goldchro': ([(190, 120, 10), (236, 170, 20), (255, 210, 60), (255, 244, 150)],
                 [(150, 104, 16), (196, 146, 30), (232, 190, 60), (255, 230, 120)]),
    'other': ([(160, 70, 40), (210, 110, 60), (240, 160, 90), (255, 210, 150)],
              [(90, 90, 96), (116, 116, 122), (144, 144, 150), (180, 180, 186)]),
    # Phase 11 (appended so the earlier textures keep their random sequence): ochre/rust-red Orchremia, silvery-white Silvane
    'orchremia': ([(170, 60, 20), (215, 95, 35), (240, 140, 60), (255, 196, 120)],
                  [(112, 50, 26), (150, 72, 34), (188, 104, 46), (222, 150, 80)]),
    'silvane': ([(170, 190, 210), (205, 220, 235), (232, 242, 252), (255, 255, 255)],
                [(150, 156, 166), (186, 192, 202), (218, 224, 232), (246, 248, 252)]),
}


def noise_fill(img, pal, rnd, weights=(1, 2, 2, 1), y0=0):
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y + y0), rgba(rnd.choices(pal, weights=weights[:len(pal)])[0]))


# ------------------------------------------------------------------------------------------------ lava intake / pipe
def lava_kit():
    rnd = random.Random(9101)
    # pipe: dark iron with rivet bands; filled = orange glow window in the middle
    for name, filled in (('lava_pipe', False), ('lava_pipe_filled', True)):
        img = new()
        noise_fill(img, IRON, rnd)
        for i in range(16):
            for b in (0, 15):
                img.putpixel((i, b), rgba(IRON[0]))
                img.putpixel((b, i), rgba(IRON[0]))
        for x in range(5, 11):
            for y in range(5, 11):
                if filled:
                    c = LAVA[3] if (x + y) % 3 else LAVA[4]
                    if x in (5, 10) or y in (5, 10):
                        c = LAVA[2]
                else:
                    c = (24, 22, 24) if x in (5, 10) or y in (5, 10) else (34, 30, 30)
                img.putpixel((x, y), rgba(c))
        save(img, BLOCK, name)
    # collar
    img = new()
    noise_fill(img, IRON[1:], rnd)
    for i in range(16):
        img.putpixel((i, 4), rgba(IRON[0])); img.putpixel((i, 11), rgba(IRON[0]))
    save(img, BLOCK, 'lava_intake_collar')
    # top: grate
    top = new()
    noise_fill(top, IRON, rnd)
    for y in range(3, 13):
        for x in range(3, 13):
            top.putpixel((x, y), rgba((20, 18, 20) if (x % 3 == 0) else IRON[2]))
    save(top, BLOCK, 'lava_intake_top')
    top = new()
    noise_fill(top, IRON, rnd)
    for y in range(3, 13):
        for x in range(3, 13):
            top.putpixel((x, y), rgba(LAVA[3] if (x % 3 == 0) else IRON[2]))
    save(top, BLOCK, 'lava_intake_top_active')
    for name, active in (('lava_intake_side', False), ('lava_intake_side_active', True)):
        img = new()
        noise_fill(img, IRON, rnd)
        for i in range(16):
            img.putpixel((i, 2), rgba(IRON[0])); img.putpixel((i, 13), rgba(IRON[0]))
        for y in range(5, 11):
            for x in range(4, 12):
                c = (LAVA[2] if y in (5, 10) else LAVA[3 + (x + y) % 2]) if active else ((26, 24, 26) if y in (5, 10) else (36, 32, 32))
                img.putpixel((x, y), rgba(c))
        for x in (1, 14):
            for y in (3, 12):
                img.putpixel((x, y), rgba(IRON[3]))
        save(img, BLOCK, name)


# ------------------------------------------------------------------------------------------------ forge
def bricks(rnd, img, y0=0):
    for y in range(16):
        for x in range(16):
            mortar = (y % 4 == 3) or ((x + (4 if (y // 4) % 2 else 0)) % 8 == 7)
            c = (30, 30, 34) if mortar else BRICK[rnd.choice((1, 2, 2, 3))]
            img.putpixel((x, y + y0), rgba(c))


def forge():
    rnd = random.Random(9201)
    side = new(); bricks(rnd, side); save(side, BLOCK, 'forge_side')
    top = new()
    bricks(rnd, top)
    for y in range(2, 14):
        for x in range(2, 14):
            top.putpixel((x, y), rgba((40, 38, 40) if x in (2, 13) or y in (2, 13) else (26, 24, 26)))
    save(top, BLOCK, 'forge_top')
    top = new()
    bricks(rnd, top)
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            top.putpixel((x, y), rgba((60, 40, 30) if edge else rnd.choice(LAVA[1:4])))
    save(top, BLOCK, 'forge_top_lit')
    bottom = new(); bricks(rnd, bottom); save(bottom, BLOCK, 'forge_bottom')
    back = new()
    bricks(rnd, back)
    for y in range(4, 12):     # lava port
        for x in range(4, 12):
            c = IRON[2] if x in (4, 11) or y in (4, 11) else (20, 18, 20)
            back.putpixel((x, y), rgba(c))
    for y in range(6, 10):
        for x in range(6, 10):
            back.putpixel((x, y), rgba(LAVA[1]))
    save(back, BLOCK, 'forge_back')
    # front: brick frame with a coal opening; unlit = dark coals, lit = 4-frame flickering glow
    def front_frame(img, y0, coal):
        bricks(rnd, img, y0)
        for y in range(5, 13):
            for x in range(3, 13):
                if y == 5 or x in (3, 12):
                    img.putpixel((x, y + y0), rgba((26, 26, 30)))
                else:
                    img.putpixel((x, y + y0), rgba(coal(x, y)))
    unlit = new()
    front_frame(unlit, 0, lambda x, y: rnd.choice([(22, 22, 24), (34, 32, 32), (48, 44, 42)]))
    save(unlit, BLOCK, 'forge_front')
    lit = new(64)
    for f in range(4):
        fr = random.Random(9300 + f)
        front_frame(lit, f * 16, lambda x, y: fr.choices(LAVA, weights=(1, 2, 3, 3, 1 + (y > 9)))[0] if y > 7 else
                    fr.choice([LAVA[2], LAVA[3], LAVA[4], (60, 30, 20)]))
    save(lit, BLOCK, 'forge_front_lit', frametime=3, interpolate=True)


# ------------------------------------------------------------------------------------------------ crucible / metals
def crucible():
    rnd = random.Random(9401)
    side = new()
    noise_fill(side, CLAY, rnd)
    for x in range(16):
        side.putpixel((x, 0), rgba(CLAY[3])); side.putpixel((x, 15), rgba(CLAY[0]))
        side.putpixel((x, 1), rgba(shade(CLAY[3], 1.08)))
    save(side, BLOCK, 'crucible_side')
    inner = new()
    noise_fill(inner, [shade(c, 0.42) for c in CLAY], rnd)
    save(inner, BLOCK, 'crucible_inner')
    for metal, (molten, solid) in METALS.items():
        img = new(64)
        for f in range(4):
            fr = random.Random(9500 + f * 7 + len(metal))
            for y in range(16):
                for x in range(16):
                    v = (x * 3 + y * 5 + f * 4 + fr.randint(0, 3)) % 11
                    c = molten[3] if v < 2 else molten[2] if v < 6 else molten[1] if v < 10 else molten[0]
                    img.putpixel((x, y + f * 16), rgba(c))
        save(img, BLOCK, 'molten_' + metal, frametime=8, interpolate=True)
        s = new()
        noise_fill(s, solid, rnd)
        for i in range(16):
            s.putpixel((i, 0), rgba(solid[3])); s.putpixel((0, i), rgba(solid[3]))
            s.putpixel((i, 15), rgba(solid[0])); s.putpixel((15, i), rgba(solid[0]))
        save(s, BLOCK, 'metal_' + metal)
        ch = new()
        noise_fill(ch, [shade(c, 0.85) for c in solid], rnd, (2, 2, 1, 1))
        save(ch, BLOCK, 'chunks_' + metal)


def mold():
    rnd = random.Random(9601)
    img = new()
    noise_fill(img, [(70, 72, 78), (84, 86, 92), (96, 98, 104), (110, 112, 118)], rnd)
    for i in range(16):
        img.putpixel((i, 0), rgba((130, 132, 138))); img.putpixel((i, 15), rgba((56, 58, 62)))
    save(img, BLOCK, 'mold')
    cav = new()
    noise_fill(cav, [(40, 40, 44), (50, 50, 54), (58, 58, 62)], rnd, (1, 2, 1))
    save(cav, BLOCK, 'mold_cavity')


def ingot_items():
    shape = [
        '................',
        '................',
        '................',
        '................',
        '................',
        '....aaaaaaaa....',
        '...alllllllla...',
        '..alllllllllla..',
        '.dmmmmmmmmmmmmb.',
        '.dmmmmmmmmmmmmb.',
        '.dmmmmmmmmmmmmb.',
        '.ddmmmmmmmmmmbb.',
        '..dddddddddddb..',
        '................',
        '................',
        '................',
    ]
    for metal in ('folkiron', 'goldchro', 'orchremia', 'silvane'):
        solid = METALS[metal][1]
        img = new()
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                c = {'a': solid[3], 'l': shade(solid[3], 0.95), 'm': solid[2], 'd': solid[1], 'b': solid[0]}.get(ch)
                if c:
                    img.putpixel((x, y), rgba(c))
        save(img, ITEM, metal + '_ingot')


# ------------------------------------------------------------------------------------------------ models
def face(tex, uv=None, cull=None, glow=False, rot=None):
    f = {"texture": tex}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    if rot:
        f["rotation"] = rot
    if glow:
        f["neoforge_data"] = dict(GLOW)
    return f


def box(frm, to, tex, faces='nsewud', glow=False, uv_auto=True, rotation=None, **per):
    names = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}
    x0, y0, z0 = frm
    x1, y1, z1 = to
    uvs = {'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
           'west': [z0, 16 - y1, z1, 16 - y0], 'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0],
           'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0]}
    el = {"from": list(frm), "to": list(to), "faces": {}}
    for k in faces:
        n = names[k]
        el["faces"][n] = face(per.get(n, tex), uvs[n] if uv_auto else None, glow=glow)
    if rotation:
        el["rotation"] = rotation
    return el


def block_model(name, textures, elements, parent="minecraft:block/block", **extra):
    m = {"parent": parent, "textures": textures, "elements": elements}
    m.update(extra)
    write_json(os.path.join(MODELS, 'block'), name, m)


def item_parent(name, parent):
    write_json(os.path.join(MODELS, 'item'), name, {"parent": parent})


def lava_models():
    # intake: like the water intake, own textures
    for name, side, top in (('lava_intake', 'lava_intake_side', 'lava_intake_top'),
                            ('lava_intake_active', 'lava_intake_side_active', 'lava_intake_top_active')):
        active = name.endswith('active')
        el = box((1, 0, 1), (15, 14, 15), '#side', up='#top', down='#top', glow=False)
        if active:
            for n in ('north', 'south', 'east', 'west', 'up'):
                el['faces'][n]['neoforge_data'] = dict(GLOW)
        block_model(name, {"particle": "holeydigsite:block/" + side, "side": "holeydigsite:block/" + side,
                           "top": "holeydigsite:block/" + top}, [el])
    collar_side = {"from": [4, 4, 0], "to": [12, 12, 1], "faces": {
        "north": face("#pipe", [4, 4, 12, 12], "north"), "up": face("#pipe", [4, 0, 12, 1]),
        "down": face("#pipe", [4, 15, 12, 16]), "west": face("#pipe", [0, 4, 1, 12]), "east": face("#pipe", [15, 4, 16, 12])}}
    collar_up = {"from": [4, 14, 4], "to": [12, 16, 12], "faces": {
        "up": face("#pipe", [4, 4, 12, 12], "up"), "north": face("#pipe", [4, 0, 12, 2]),
        "south": face("#pipe", [4, 0, 12, 2]), "west": face("#pipe", [4, 0, 12, 2]), "east": face("#pipe", [4, 0, 12, 2])}}
    tex = {"particle": "holeydigsite:block/lava_intake_collar", "pipe": "holeydigsite:block/lava_intake_collar"}
    block_model('lava_intake_collar_side', tex, [collar_side])
    block_model('lava_intake_collar_up', tex, [collar_up])
    item_parent('lava_intake', 'holeydigsite:block/lava_intake_active')
    # pipe core + side, plain and filled (filled faces glow)
    for suffix, texname, glow in (('', 'lava_pipe', False), ('_filled', 'lava_pipe_filled', True)):
        t = {"particle": "holeydigsite:block/" + texname, "pipe": "holeydigsite:block/" + texname}
        block_model('lava_pipe_core' + suffix, t, [{"from": [5, 5, 5], "to": [11, 11, 11],
                    "faces": {d: face("#pipe", glow=glow) for d in ('down', 'up', 'north', 'south', 'west', 'east')}}])
        block_model('lava_pipe_side' + suffix, t, [{"from": [5, 5, 0], "to": [11, 11, 5], "faces": {
            "down": face("#pipe", glow=glow), "up": face("#pipe", glow=glow), "west": face("#pipe", glow=glow),
            "east": face("#pipe", glow=glow), "north": face("#pipe", cull="north", glow=glow)}}])
    # item: a straight piece (same display as the water pipe item)
    water_item = json.load(open(os.path.join(MODELS, 'item', 'water_pipe.json')))
    water_item["textures"] = {"particle": "holeydigsite:block/lava_pipe_filled", "pipe": "holeydigsite:block/lava_pipe_filled"}
    write_json(os.path.join(MODELS, 'item'), 'lava_pipe', water_item)
    # blockstates
    parts = [{"when": {"active": "false"}, "apply": {"model": "holeydigsite:block/lava_intake"}},
             {"when": {"active": "true"}, "apply": {"model": "holeydigsite:block/lava_intake_active"}}]
    for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        a = {"model": "holeydigsite:block/lava_intake_collar_side"}
        if y:
            a["y"] = y
        parts.append({"when": {d: "true"}, "apply": a})
    parts.append({"when": {"up": "true"}, "apply": {"model": "holeydigsite:block/lava_intake_collar_up"}})
    write_json(STATES, 'lava_intake', {"multipart": parts})
    parts = []
    for filled in ('false', 'true'):
        sfx = '_filled' if filled == 'true' else ''
        parts.append({"when": {"filled": filled}, "apply": {"model": "holeydigsite:block/lava_pipe_core" + sfx}})
        for d, rot in (('north', {}), ('east', {"y": 90}), ('south', {"y": 180}), ('west', {"y": 270}),
                       ('up', {"x": 270}), ('down', {"x": 90})):
            a = {"model": "holeydigsite:block/lava_pipe_side" + sfx}
            a.update(rot)
            parts.append({"when": {d: "true", "filled": filled}, "apply": a})
    write_json(STATES, 'lava_pipe', {"multipart": parts})


def forge_models():
    for name, front, top, glow in (('forge', 'forge_front', 'forge_top', False), ('forge_lit', 'forge_front_lit', 'forge_top_lit', True)):
        el = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
            "north": face("#front", cull="north", glow=glow), "south": face("#back", cull="south"),
            "east": face("#side", cull="east"), "west": face("#side", cull="west"),
            "up": face("#top", cull="up", glow=glow), "down": face("#bottom", cull="down")}}
        block_model(name, {"particle": "holeydigsite:block/forge_side", "front": "holeydigsite:block/" + front,
                           "back": "holeydigsite:block/forge_back", "side": "holeydigsite:block/forge_side",
                           "top": "holeydigsite:block/" + top, "bottom": "holeydigsite:block/forge_bottom"}, [el])
    item_parent('forge', 'holeydigsite:block/forge_lit')
    variants = {}
    for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for lit in ('false', 'true'):
            v = {"model": "holeydigsite:block/forge" + ('_lit' if lit == 'true' else '')}
            if y:
                v["y"] = y
            variants[f"facing={d},lit={lit}"] = v
    write_json(STATES, 'forge', {"variants": variants})


def crucible_elements(rotation=None):
    # a shallow cup (9 px) so a player standing next to the forge can see the molten level
    els = [
        box((2, 0, 2), (14, 1, 14), '#side', up='#inner'),                                    # floor
        box((2, 1, 2), (14, 9, 3), '#side', faces='nsewu', south='#inner', up='#side'),        # north wall (front)
        box((2, 1, 13), (14, 9, 14), '#side', faces='nsewu', north='#inner', up='#side'),      # south wall
        box((2, 1, 3), (3, 9, 13), '#side', faces='weu', east='#inner', up='#side'),           # west wall
        box((13, 1, 3), (14, 9, 13), '#side', faces='weu', west='#inner', up='#side'),         # east wall
        box((6.5, 7, 0.5), (9.5, 9, 2), '#side', faces='nsewud'),                            # spout (front = north)
    ]
    if rotation:
        for e in els:
            e["rotation"] = rotation
    return els


def crucible_models():
    tex = {"particle": "holeydigsite:block/crucible_side", "side": "holeydigsite:block/crucible_side",
           "inner": "holeydigsite:block/crucible_inner"}
    block_model('crucible', tex, crucible_elements())
    # tipped forward (towards the spout / mold; a NEGATIVE x angle lifts the back = tips north, Phase 10 fix); the molten layer is hidden while tipping
    block_model('crucible_tipped', tex, crucible_elements({"origin": [8, 0, 2], "axis": "x", "angle": -22.5}))
    item_parent('crucible', 'holeydigsite:block/crucible')
    for metal in METALS:
        mt = {"particle": f"holeydigsite:block/molten_{metal}", "molten": f"holeydigsite:block/molten_{metal}"}
        for fill in range(1, 5):
            top = (3, 5, 7, 8.5)[fill - 1]
            block_model(f'crucible_molten_{metal}_{fill}', mt, [box((3, 1, 3), (13, top, 13), '#molten', faces='u', glow=True)])
        ct = {"particle": f"holeydigsite:block/chunks_{metal}", "c": f"holeydigsite:block/chunks_{metal}"}
        block_model(f'crucible_chunks_{metal}', ct, [
            box((4, 1, 4), (7, 4, 7), '#c'), box((8, 1, 5), (12, 3, 9), '#c'), box((5, 1, 9), (8, 5, 12), '#c'),
            box((9, 1, 10), (11, 4, 12), '#c')])
    parts = []
    for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for tip, model in (('false', 'crucible'), ('true', 'crucible_tipped')):
            a = {"model": "holeydigsite:block/" + model}
            if y:
                a["y"] = y
            parts.append({"when": {"facing": d, "tipping": tip}, "apply": a})
    for metal in METALS:
        for fill in range(1, 5):
            parts.append({"when": {"fill": str(fill), "metal": metal, "tipping": "false"},
                          "apply": {"model": f"holeydigsite:block/crucible_molten_{metal}_{fill}"}})
        parts.append({"when": {"chunks": "true", "metal": metal, "tipping": "false"},
                      "apply": {"model": f"holeydigsite:block/crucible_chunks_{metal}"}})
    write_json(STATES, 'crucible', {"multipart": parts})


def mold_models():
    tex = {"particle": "holeydigsite:block/mold", "m": "holeydigsite:block/mold", "cav": "holeydigsite:block/mold_cavity"}
    els = [
        box((1, 0, 1), (15, 1, 15), '#m', up='#cav'),                    # base (cavity floor)
        box((1, 1, 1), (15, 4, 5), '#m', faces='nsewu', south='#m'),      # rim north
        box((1, 1, 11), (15, 4, 15), '#m', faces='nsewu'),                # rim south
        box((1, 1, 5), (4, 4, 11), '#m', faces='weu'),                    # rim west
        box((12, 1, 5), (15, 4, 11), '#m', faces='weu'),                  # rim east
    ]
    block_model('mold', tex, els)
    item_parent('mold', 'holeydigsite:block/mold')
    parts = [{"apply": {"model": "holeydigsite:block/mold"}}]
    for metal in METALS:
        for state in ('hot', 'solid'):
            t = f"holeydigsite:block/{'molten' if state == 'hot' else 'metal'}_{metal}"
            for level in range(1, 4):
                block_model(f'mold_metal_{metal}_{state}_{level}', {"particle": t, "t": t},
                            [box((4, 1, 5), (12, 1 + level, 11), '#t', faces='u', glow=(state == 'hot'))])
                parts.append({"when": {"metal": metal, "solid": "true" if state == 'solid' else "false", "level": str(level)},
                              "apply": {"model": f"holeydigsite:block/mold_metal_{metal}_{state}_{level}"}})
    write_json(STATES, 'mold', {"multipart": parts})


def ingot_models():
    for metal in ('folkiron', 'goldchro', 'orchremia', 'silvane'):
        write_json(os.path.join(MODELS, 'item'), metal + '_ingot',
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"holeydigsite:item/{metal}_ingot"}})


if __name__ == '__main__':
    lava_kit()
    forge()
    crucible()
    mold()
    lava_models()
    forge_models()
    crucible_models()
    mold_models()
    print('phase 9 assets written')
