"""Phase 21b / 21e assets (deterministic, Pillow): the six Ledger-only decorative blocks.

Run from the project root:  python tools/make_phase21b_assets.py [preview]

Writes, for claim_flag, ore_cart, tool_rack, lucky_lantern, gem_display_case and golden_pan_trophy:
  * assets/holeydigsite/textures/block/<id>.png   (64x32 tile sheet: 8 tiles of 16x16, reusing our real item/block textures)
  * assets/holeydigsite/models/block/<id>.json    (elements; NORTH is the front)
  * assets/holeydigsite/models/item/<id>.json
  * assets/holeydigsite/blockstates/<id>.json     (facing -> y rotation; the model's front is north)
  * data/holeydigsite/loot_table/blocks/<id>.json (drops itself)
Lang names, shop entries and the block classes are written by hand (see the dev guide, Phase 21b).
"""
import json
import os
import random
import sys
from PIL import Image

ASSETS = os.path.join('src', 'main', 'resources', 'assets', 'holeydigsite')
DATA = os.path.join('src', 'main', 'resources', 'data', 'holeydigsite')
TEX = os.path.join(ASSETS, 'textures', 'block')
ITEM_TEX = os.path.join(ASSETS, 'textures', 'item')
MODELS = os.path.join(ASSETS, 'models', 'block')
ITEM_MODELS = os.path.join(ASSETS, 'models', 'item')
STATES = os.path.join(ASSETS, 'blockstates')
LOOT = os.path.join(DATA, 'loot_table', 'blocks')

SHEET_W, SHEET_H = 64, 32          # 4 x 2 tiles of 16 px
FACES = ('north', 'east', 'south', 'west', 'up', 'down')
GLOW = {'block_light': 15, 'sky_light': 15}


# ------------------------------------------------------------------------------------------ real asset loaders & tile painters


def load_tex(folder, name):
    return Image.open(os.path.join(folder, name + '.png')).convert('RGBA')


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + ((c[3],) if len(c) > 3 else (255,))


def brighten(im, factor):
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a:
                out.putpixel((x, y), (min(255, int(r * factor)), min(255, int(g * factor)), min(255, int(b * factor)), a))
    return out


def composite_on(bg, fg, ox=0, oy=0):
    out = bg.copy()
    for y in range(fg.height):
        for x in range(fg.width):
            px, py = x + ox, y + oy
            if 0 <= px < out.width and 0 <= py < out.height:
                c = fg.getpixel((x, y))
                if c[3] > 0:
                    out.putpixel((px, py), c)
    return out


def tile_flat(rng, base, noise=0.07, alpha=255):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(base + (alpha,), 1 + rng.uniform(-noise, noise)))
    return im


def tile_wood(rng, base):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        row = 1 + 0.06 * ((y // 4) % 2) - 0.08 * (y % 4 == 3)
        for x in range(16):
            n = rng.uniform(-0.03, 0.03)
            im.putpixel((x, y), shade(base + (255,), row + n))
    for _ in range(4):
        x, y = rng.randrange(1, 14), rng.randrange(16)
        for k in range(2):
            im.putpixel((x + k, y), shade(base + (255,), 0.85))
    return im


def tile_metal(rng, base):
    im = tile_flat(rng, base, 0.05)
    for x in range(16):
        im.putpixel((x, 0), shade(base + (255,), 1.25))
        im.putpixel((x, 15), shade(base + (255,), 0.7))
    for y in range(16):
        im.putpixel((0, y), shade(base + (255,), 1.15))
        im.putpixel((15, y), shade(base + (255,), 0.75))
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im.putpixel(p, shade(base + (255,), 1.45))
    return im


def tile_cloth(rng, base, stripe):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            weave = 0.93 + 0.07 * ((x + y) % 2)
            c = stripe if y in (6, 7, 8) or x in (14, 15) else base
            im.putpixel((x, y), shade(c + (255,), weave * (1 + rng.uniform(-0.03, 0.03))))
    return im


def tile_glass(tint, inner_alpha, edge_alpha):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            a = edge_alpha if edge else inner_alpha
            im.putpixel((x, y), tint + (a,))
    for i in range(3, 8):
        im.putpixel((i, 12 - i), (255, 255, 255, min(255, inner_alpha + 85)))
    return im


def tile_dirt_ore():
    """Our Dirtrock + Dirt Clump look with small Folkiron and Goldchro chunks mixed in."""
    base = composite_on(load_tex(TEX, 'dirtrock'), load_tex(ITEM_TEX, 'dirt_clump'), -2, -2)
    base = composite_on(base, load_tex(ITEM_TEX, 'dirt_clump'), 4, 3)
    base = composite_on(base, load_tex(ITEM_TEX, 'folkiron_chunk_small'), -3, 4)
    base = composite_on(base, load_tex(ITEM_TEX, 'goldchro_chunk_small'), 4, -3)
    return base


PAL = dict(
    wood=(156, 112, 62), lightwood=(212, 182, 136), dark=(86, 56, 30),
    iron=(112, 116, 122), darkiron=(58, 60, 66), stone=(72, 74, 82),
    cloth=(184, 40, 44), stripe=(236, 190, 56), velvet=(36, 38, 52),
)


def make_tile(rng, name):
    if name in ('wood', 'lightwood', 'dark'):
        return tile_wood(rng, PAL[name])
    if name in ('iron', 'darkiron', 'stone'):
        return tile_metal(rng, PAL[name])
    if name == 'cloth':
        return tile_cloth(rng, PAL['cloth'], PAL['stripe'])
    if name == 'velvet':
        return tile_cloth(rng, PAL['velvet'], PAL['velvet'])
    if name == 'case_glass':
        return tile_glass((196, 228, 244), 32, 165)
    if name == 'lantern_glass':
        return tile_glass((255, 242, 196), 24, 95)
    if name == 'pan':
        return brighten(load_tex(TEX, 'pan'), 1.06)
    if name == 'metal_goldchro':
        return load_tex(TEX, 'metal_goldchro')
    if name == 'dirtrock':
        return load_tex(TEX, 'dirtrock')
    if name == 'dirt_ore':
        return tile_dirt_ore()
    if name == 'dirt_clump':
        return composite_on(load_tex(TEX, 'dirtrock'), load_tex(ITEM_TEX, 'dirt_clump'))
    if name == 'goldchro_chunk_large':
        return composite_on(load_tex(TEX, 'chunks_goldchro'), load_tex(ITEM_TEX, 'goldchro_chunk_large'))
    if name == 'goldchro_chunk_medium':
        return composite_on(load_tex(TEX, 'chunks_goldchro'), load_tex(ITEM_TEX, 'goldchro_chunk_medium'))
    if name == 'goldchro_chunk_bright':
        bg = brighten(load_tex(TEX, 'chunks_goldchro'), 1.2)
        fg = brighten(load_tex(ITEM_TEX, 'goldchro_chunk_large'), 1.15)
        return composite_on(bg, fg)
    if name == 'folkiron_chunk_large':
        return composite_on(load_tex(TEX, 'chunks_folkiron'), load_tex(ITEM_TEX, 'folkiron_chunk_large'))
    if name in ('ruby_large', 'sapphire_large', 'topaz_large'):
        fg = load_tex(ITEM_TEX, name)
        border = fg.getpixel((4, 2))
        bg = Image.new('RGBA', (16, 16), border)
        return composite_on(bg, fg)
    if name in ('prospectors_pick', 'brush', 'goldchro_brush', 'blacksmith_hammer'):
        return load_tex(ITEM_TEX, name)
    raise ValueError('unknown tile: ' + name)


def paint(seed, names):
    """names: list (<= 8) of tile specs -> 64x32 sheet image."""
    rng = random.Random(seed)
    sheet = Image.new('RGBA', (SHEET_W, SHEET_H), (0, 0, 0, 0))
    for i, n in enumerate(names):
        sheet.paste(make_tile(rng, n), ((i % 4) * 16, (i // 4) * 16))
    return sheet


# ------------------------------------------------------------------------------------------ geometry & UV helpers


def tile_uv(tile, px0=0.0, py0=0.0, px1=16.0, py1=16.0):
    """Maps pixel sub-rect (px0..px1, py0..py1) inside 16x16 `tile` onto the 64x32 sheet's 0..16 UV coordinates."""
    tx, ty = tile % 4, tile // 4
    return [
        round(tx * 4.0 + px0 * 0.25, 4),
        round(ty * 8.0 + py0 * 0.5, 4),
        round(tx * 4.0 + px1 * 0.25, 4),
        round(ty * 8.0 + py1 * 0.5, 4),
    ]


def face_auto_px(face, frm, to):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    if face == 'north':
        return (16 - x1, 16 - y1, 16 - x0, 16 - y0)
    if face == 'south':
        return (x0, 16 - y1, x1, 16 - y0)
    if face == 'west':
        return (z0, 16 - y1, z1, 16 - y0)
    if face == 'east':
        return (16 - z1, 16 - y1, 16 - z0, 16 - y0)
    if face == 'up':
        return (x0, z0, x1, z1)
    if face == 'down':
        return (x0, 16 - z1, x1, 16 - z0)
    raise ValueError(face)


def elem(frm, to, tile, faces=FACES, sub_uv=None, per_tile=None, per_uv=None, glow=False, shade_el=True):
    """Builds a block model element.
    - By default (`sub_uv=None`), uses proportional 1:1 block-to-texel UV inside `tile` so textures are never squished.
    - If `sub_uv=(px0, py0, px1, py1)`, maps all faces to that pixel region of `tile` (for item sprites / chunks / gems).
    """
    per_tile = per_tile or {}
    per_uv = per_uv or {}
    fdict = {}
    for f in faces:
        t = per_tile.get(f, tile)
        if f in per_uv:
            px_rect = per_uv[f]
        elif sub_uv is not None:
            px_rect = sub_uv
        else:
            px_rect = face_auto_px(f, frm, to)
        fd = {'uv': tile_uv(t, *px_rect), 'texture': '#0'}
        if glow:
            fd['neoforge_data'] = dict(GLOW)
        fdict[f] = fd
    out = {'from': [round(v, 4) for v in frm], 'to': [round(v, 4) for v in to], 'faces': fdict}
    if not shade_el:
        out['shade'] = False
    return out


# ------------------------------------------------------------------------------------------ model builders


def claim_flag_elements():
    # Tiles: 0=wood, 1=cloth, 2=metal_goldchro, 3=dirtrock
    # Every face lies in its own plane so no two faces can ever z-fight.
    return [
        elem((4, 0, 4), (12, 2, 12), 3),
        elem((7, 2, 7), (9, 14, 9), 0, faces=('north', 'east', 'south', 'west')),
        elem((6.5, 14, 6.5), (9.5, 16, 9.5), 2),
        elem((9, 13, 7.4), (15.2, 13.8, 8.6), 2, faces=('north', 'east', 'south', 'up', 'down')),
        elem((9, 8, 7.5), (15, 13, 8.5), 1, faces=('north', 'east', 'south', 'down'), sub_uv=(0, 2, 16, 14)),
    ]


def golden_pan_trophy_elements():
    # Tiles: 0=stone (plinth), 1=pan (real pan.png, gilded), 2=metal_goldchro (plaque + rim top),
    #        3=goldchro_chunk_large, 4=goldchro_chunk_medium
    els = [
        elem((3, 0, 3), (13, 6, 13), 0, faces=('north', 'east', 'south', 'west', 'down')),
        elem((2, 6, 2), (14, 7, 14), 0, faces=('north', 'east', 'south', 'west', 'down')),
        elem((5, 2, 2.5), (11, 4.5, 3), 2, faces=('north', 'east', 'west', 'up', 'down'), sub_uv=(0, 0, 16, 16)),
    ]
    # Read our real Pan block model (`models/block/pan.json`: square basin with floor + 4 rims, no handle)
    # and lift it onto the plinth (`y + 7`), gilding the top of the rim with Goldchro metal.
    with open(os.path.join(MODELS, 'pan.json'), encoding='utf-8') as f:
        pan_model = json.load(f)
    for pe in pan_model['elements']:
        name = pe.get('name', '')
        x0, y0, z0 = pe['from']
        x1, y1, z1 = pe['to']
        if name == 'floor':
            faces, per_tile = FACES, None
        elif name in ('rim_west', 'rim_east'):
            faces, per_tile = ('east', 'west', 'up'), {'up': 2}
        else:
            faces, per_tile = ('north', 'east', 'south', 'west', 'up'), {'up': 2}
        els.append(elem((x0, y0 + 7, z0), (x1, y1 + 7, z1), 1, faces=faces, per_tile=per_tile))

    # Goldchro chunks sitting inside the pan basin (using our real goldchro_chunk_large / medium textures)
    lg_uv = (2, 4, 15, 14)
    md_uv = (3, 5, 13, 13)
    top_faces = ('north', 'east', 'south', 'west', 'up')
    els.extend([
        elem((3.2, 8, 3.8), (8.2, 10.6, 8.2), 3, faces=top_faces, sub_uv=lg_uv),
        elem((7.6, 8, 7.6), (12.4, 10.4, 12.2), 3, faces=top_faces, sub_uv=lg_uv),
        elem((8.8, 8, 3.4), (12.4, 9.8, 6.8), 4, faces=top_faces, sub_uv=md_uv),
        elem((3.6, 8, 8.8), (7.0, 9.6, 12.0), 4, faces=top_faces, sub_uv=md_uv),
        elem((5.6, 10.6, 5.6), (9.8, 11.8, 9.6), 3, faces=top_faces, sub_uv=lg_uv),
    ])
    return els


def tool_rack_elements():
    # Tiles: 0=lightwood (high-contrast birch-tone backboard), 1=dark (frame rails & pegs),
    #        2=prospectors_pick, 3=brush, 4=blacksmith_hammer, 5=pan, 6=metal_goldchro
    els = [
        # Backboard and top/bottom rails
        elem((0.5, 2, 14.5), (15.5, 14, 16), 0, faces=('north', 'east', 'south', 'west')),
        elem((0, 14, 13.6), (16, 15.2, 16), 1),
        elem((0, 0.8, 13.6), (16, 2, 16), 1),
        # Wooden hanging pegs for the tools
        elem((11.5, 11.2, 13.2), (12.3, 12.0, 14.5), 1, faces=('north', 'east', 'west', 'up', 'down')),
        elem((3.5, 11.2, 13.2), (4.3, 12.0, 14.5), 1, faces=('north', 'east', 'west', 'up', 'down')),
        elem((11.5, 6.2, 13.2), (12.3, 7.0, 14.5), 1, faces=('north', 'east', 'west', 'up', 'down')),
        elem((3.4, 6.8, 12.8), (4.2, 7.4, 14.5), 1, faces=('north', 'east', 'west', 'up', 'down')),
        # 1. Prospector's Pick (top-left when viewing north face) - real item texture
        elem((8.4, 6.5, 13.3), (15.4, 13.5, 13.6), 2, faces=('north', 'south'), sub_uv=(0, 0, 16, 16)),
        # 2. Blacksmith Hammer (top-right when viewing north face) - real item texture
        elem((0.6, 6.5, 13.3), (7.6, 13.5, 13.6), 4, faces=('north', 'south'), sub_uv=(0, 0, 16, 16)),
        # 3. Brush (bottom-left when viewing north face) - real item texture
        elem((8.2, 2.1, 13.1), (15.2, 9.1, 13.4), 3, faces=('north', 'south'), sub_uv=(0, 0, 16, 16)),
        # 4. Pan (bottom-right when viewing north face) - our square panning pan with 4 raised rims and no handle
        elem((1.4, 2.3, 13.8), (6.2, 7.1, 14.5), 5, faces=('north', 'east', 'west', 'up', 'down')),
        elem((1.4, 6.5, 13.0), (6.2, 7.1, 13.8), 5, faces=('north', 'east', 'west', 'up', 'down'), per_tile={'north': 6}),
        elem((1.4, 2.3, 13.0), (6.2, 2.9, 13.8), 5, faces=('north', 'east', 'west', 'up', 'down'), per_tile={'north': 6}),
        elem((1.4, 2.9, 13.0), (2.0, 6.5, 13.8), 5, faces=('north', 'east', 'west'), per_tile={'north': 6}),
        elem((5.6, 2.9, 13.0), (6.2, 6.5, 13.8), 5, faces=('north', 'east', 'west'), per_tile={'north': 6}),
    ]
    return els


def cut_gem_elements(cx, y0, cz, scale, tile):
    """Builds a 3D brilliant-cut gem on the velvet using the exact opaque row bands of ruby/sapphire/topaz_large.png:
      - culet point at the bottom (rows 11..13, cols 6..10)
      - pavilion (rows 8..11, cols 4..12)
      - wide octagonal girdle in the upper-middle (rows 5..8, cols 1..15, showing the dark girdle line)
      - flat table crown at the top (rows 2..5, cols 4..12, showing the bright specular highlight & dark top border)
    """
    r_culet = 0.45 * scale
    r_pav = 0.95 * scale
    r_girdle = 1.45 * scale
    r_table = 0.90 * scale
    h0 = y0
    h1 = round(y0 + 0.55 * scale, 4)
    h2 = round(y0 + 1.35 * scale, 4)
    h3 = round(y0 + 2.35 * scale, 4)
    h4 = round(y0 + 3.05 * scale, 4)
    side_faces = ('north', 'east', 'south', 'west')
    return [
        elem((cx - r_culet, h0, cz - r_culet), (cx + r_culet, h1, cz + r_culet), tile,
             faces=side_faces, sub_uv=(6, 11, 10, 13)),
        elem((cx - r_pav, h1, cz - r_pav), (cx + r_pav, h2, cz + r_pav), tile,
             faces=('north', 'east', 'south', 'west', 'down'), sub_uv=(4, 8, 12, 11)),
        elem((cx - r_girdle, h2, cz - r_girdle), (cx + r_girdle, h3, cz + r_girdle), tile,
             faces=FACES, sub_uv=(1, 5, 15, 8)),
        elem((cx - r_table, h3, cz - r_table), (cx + r_table, h4, cz + r_table), tile,
             faces=('north', 'east', 'south', 'west', 'up'),
             sub_uv=(4, 2, 12, 5), per_uv={'up': (3, 3, 13, 6)}),
    ]


def gem_display_case_elements():
    # Tiles: 0=wood, 1=velvet, 2=case_glass, 3=ruby_large, 4=sapphire_large, 5=topaz_large, 6=metal_goldchro
    els = [
        elem((1, 0, 1), (15, 2, 15), 0),
        elem((2, 2, 2), (14, 6, 14), 0, faces=('north', 'east', 'south', 'west')),
        elem((1, 6, 1), (15, 7, 15), 6),
        elem((2, 7, 2), (14, 8, 14), 1, faces=('north', 'east', 'south', 'west', 'up')),
    ]
    # Three brilliant-cut gems on the velvet cushion (Ruby front-left, Sapphire front-right, Topaz back-center)
    els.extend(cut_gem_elements(4.8, 8.0, 5.5, 1.15, 3))
    els.extend(cut_gem_elements(11.2, 8.0, 5.5, 1.15, 4))
    els.extend(cut_gem_elements(8.0, 8.0, 10.5, 1.15, 5))
    # Goldchro corner pillars, open-top frame, and glass panels (sides + top window)
    els.extend([
        elem((1.5, 8, 1.5), (2.5, 15, 2.5), 6, faces=('north', 'east', 'south', 'west')),
        elem((13.5, 8, 1.5), (14.5, 15, 2.5), 6, faces=('north', 'east', 'south', 'west')),
        elem((1.5, 8, 13.5), (2.5, 15, 14.5), 6, faces=('north', 'east', 'south', 'west')),
        elem((13.5, 8, 13.5), (14.5, 15, 14.5), 6, faces=('north', 'east', 'south', 'west')),
        elem((1.5, 15, 1.5), (14.5, 16, 2.5), 6),
        elem((1.5, 15, 13.5), (14.5, 16, 14.5), 6),
        elem((1.5, 15, 2.5), (2.5, 16, 13.5), 6, faces=('east', 'west', 'up', 'down')),
        elem((13.5, 15, 2.5), (14.5, 16, 13.5), 6, faces=('east', 'west', 'up', 'down')),
        elem((2.5, 8, 2.0), (13.5, 15, 14.0), 2, faces=('north', 'south'), sub_uv=(0, 0, 16, 16)),
        elem((2.0, 8, 2.5), (14.0, 15, 13.5), 2, faces=('east', 'west'), sub_uv=(0, 0, 16, 16)),
        elem((2.5, 15.2, 2.5), (13.5, 15.5, 13.5), 2, faces=('up',), sub_uv=(0, 0, 16, 16)),
    ])
    return els


def ore_cart_elements():
    # Tiles: 0=iron, 1=dirt_ore, 2=darkiron, 3=dirt_clump, 4=goldchro_chunk_large, 5=folkiron_chunk_large
    top_faces = ('north', 'east', 'south', 'west', 'up')
    clump_uv = (2, 3, 14, 13)
    gold_uv = (2, 4, 15, 14)
    folk_uv = (1, 3, 14, 15)
    return [
        # Cart tub & rims
        elem((1, 3, 3), (15, 4, 13), 0, faces=('north', 'east', 'south', 'west', 'down')),
        elem((1, 4, 3), (15, 9, 4), 0, faces=('north', 'east', 'south', 'west')),
        elem((1, 4, 12), (15, 9, 13), 0, faces=('north', 'east', 'south', 'west')),
        elem((1, 4, 4), (2, 9, 12), 0, faces=('east', 'west', 'up')),
        elem((14, 4, 4), (15, 9, 12), 0, faces=('east', 'west', 'up')),
        elem((0.5, 9, 2.5), (15.5, 9.8, 4.0), 2),
        elem((0.5, 9, 12.0), (15.5, 9.8, 13.5), 2),
        # Dirtrock + Dirt Clump bed & heaped dirt mounds
        elem((2, 4, 4), (14, 8.2, 12), 1, faces=('up',)),
        elem((2.5, 8.2, 4.5), (9.0, 9.5, 11.2), 3, faces=top_faces, sub_uv=clump_uv),
        elem((9.1, 8.2, 4.8), (13.5, 9.3, 11.4), 1, faces=top_faces),
        elem((5.2, 9.5, 5.6), (10.2, 10.3, 10.2), 3, faces=top_faces, sub_uv=clump_uv),
        # Folkiron & Goldchro chunks mixed in on top of the dirt
        elem((3.0, 9.5, 4.8), (6.2, 11.3, 7.8), 4, faces=top_faces, sub_uv=gold_uv),
        elem((6.6, 10.3, 6.2), (9.4, 12.0, 9.4), 5, faces=top_faces, sub_uv=folk_uv),
        elem((9.5, 9.3, 7.8), (12.7, 11.1, 10.8), 4, faces=top_faces, sub_uv=gold_uv),
        elem((9.8, 9.3, 4.6), (12.6, 10.9, 7.2), 5, faces=top_faces, sub_uv=folk_uv),
        elem((3.6, 9.5, 8.2), (6.3, 11.0, 10.8), 5, faces=top_faces, sub_uv=folk_uv),
        # Axles & wheels
        elem((3, 1, 3), (4, 3, 13), 2, faces=('east', 'west', 'down')),
        elem((12, 1, 3), (13, 3, 13), 2, faces=('east', 'west', 'down')),
        elem((2, 0, 2), (5, 3, 3), 2),
        elem((11, 0, 2), (14, 3, 3), 2),
        elem((2, 0, 13), (5, 3, 14), 2),
        elem((11, 0, 13), (14, 3, 14), 2),
    ]


def lucky_lantern_elements():
    # Tiles: 0=darkiron (lantern frame), 1=lantern_glass (clear warm glass), 2=goldchro_chunk_bright
    gold_uv = (2, 4, 15, 14)
    top_faces = ('north', 'east', 'south', 'west', 'up')
    return [
        # Base, hood, top cap & hanging loop
        elem((5, 0, 5), (11, 2, 11), 0),
        elem((5, 10, 5), (11, 12, 11), 0),
        elem((6, 12, 6), (10, 13, 10), 0, faces=top_faces),
        elem((7.5, 13, 7.5), (8.5, 14, 8.5), 0, faces=('north', 'east', 'south', 'west')),
        elem((7, 14, 7.5), (9, 15, 8.5), 0),
        # Corner iron posts
        elem((5, 2, 5), (6, 10, 6), 0, faces=('north', 'east', 'south', 'west')),
        elem((10, 2, 5), (11, 10, 6), 0, faces=('north', 'east', 'south', 'west')),
        elem((5, 2, 10), (6, 10, 11), 0, faces=('north', 'east', 'south', 'west')),
        elem((10, 2, 10), (11, 10, 11), 0, faces=('north', 'east', 'south', 'west')),
        # Large, bright, full-bright glowing Goldchro nugget filling the lantern interior
        elem((6.4, 2.0, 6.4), (9.6, 3.2, 9.6), 2, faces=('north', 'east', 'south', 'west'),
             sub_uv=gold_uv, glow=True, shade_el=False),
        elem((6.1, 3.2, 6.1), (9.9, 7.6, 9.9), 2, faces=FACES,
             sub_uv=gold_uv, glow=True, shade_el=False),
        elem((6.6, 7.6, 6.6), (9.4, 8.8, 9.4), 2, faces=top_faces,
             sub_uv=gold_uv, glow=True, shade_el=False),
        # Clear single-face glass panes on the 4 sides so the Goldchro nugget is unobstructed
        elem((6, 2, 5.5), (10, 10, 5.8), 1, faces=('north',), sub_uv=(0, 0, 16, 16)),
        elem((6, 2, 10.2), (10, 10, 10.5), 1, faces=('south',), sub_uv=(0, 0, 16, 16)),
        elem((5.5, 2, 6), (5.8, 10, 10), 1, faces=('west',), sub_uv=(0, 0, 16, 16)),
        elem((10.2, 2, 6), (10.5, 10, 10), 1, faces=('east',), sub_uv=(0, 0, 16, 16)),
    ]


# id -> (tile names, render_type or None, seed, element builder)
BLOCKS = {
    'claim_flag': (['wood', 'cloth', 'metal_goldchro', 'dirtrock'], None, 211, claim_flag_elements),
    'ore_cart': (['iron', 'dirt_ore', 'darkiron', 'dirt_clump', 'goldchro_chunk_large', 'folkiron_chunk_large'],
                 None, 212, ore_cart_elements),
    'tool_rack': (['lightwood', 'dark', 'prospectors_pick', 'brush', 'blacksmith_hammer', 'pan', 'metal_goldchro'],
                  'minecraft:cutout', 213, tool_rack_elements),
    'lucky_lantern': (['darkiron', 'lantern_glass', 'goldchro_chunk_bright'],
                      'minecraft:translucent', 214, lucky_lantern_elements),
    'gem_display_case': (['wood', 'velvet', 'case_glass', 'ruby_large', 'sapphire_large', 'topaz_large', 'metal_goldchro'],
                         'minecraft:translucent', 215, gem_display_case_elements),
    'golden_pan_trophy': (['stone', 'pan', 'metal_goldchro', 'goldchro_chunk_large', 'goldchro_chunk_medium'],
                          None, 216, golden_pan_trophy_elements),
}


def check_no_overlapping_coplanar_faces(bid, elements):
    """Verifies that no two faces in `elements` lie in the same plane and overlap in 2D (prevents z-fighting)."""
    planes = {}   # (axis, coord) -> list of (u0, v0, u1, v1, face_dir, elem_idx)
    for idx, el in enumerate(elements):
        x0, y0, z0 = el['from']
        x1, y1, z1 = el['to']
        for f in el['faces']:
            if f == 'north':
                key, rect = ('z', round(z0, 4)), (x0, y0, x1, y1)
            elif f == 'south':
                key, rect = ('z', round(z1, 4)), (x0, y0, x1, y1)
            elif f == 'west':
                key, rect = ('x', round(x0, 4)), (z0, y0, z1, y1)
            elif f == 'east':
                key, rect = ('x', round(x1, 4)), (z0, y0, z1, y1)
            elif f == 'down':
                key, rect = ('y', round(y0, 4)), (x0, z0, x1, z1)
            elif f == 'up':
                key, rect = ('y', round(y1, 4)), (x0, z0, x1, z1)
            else:
                raise ValueError(f)
            for (u0, v0, u1, v1, pf, pidx) in planes.get(key, ()):
                if min(rect[2], u1) > max(rect[0], u0) + 1e-5 and min(rect[3], v1) > max(rect[1], v0) + 1e-5:
                    raise AssertionError(f'{bid}: overlapping coplanar faces on {key}: elem {pidx} ({pf}) and elem {idx} ({f})')
            planes.setdefault(key, []).append((*rect, f, idx))


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def build(bid):
    names, render_type, seed, builder = BLOCKS[bid]
    os.makedirs(TEX, exist_ok=True)
    sheet = paint(seed, names)
    sheet.save(os.path.join(TEX, bid + '.png'))
    elements = builder()
    check_no_overlapping_coplanar_faces(bid, elements)
    model = {'parent': 'minecraft:block/block'}
    if render_type:
        model['render_type'] = render_type
    model['textures'] = {'0': 'holeydigsite:block/' + bid, 'particle': 'holeydigsite:block/' + bid}
    model['elements'] = elements
    write_json(os.path.join(MODELS, bid + '.json'), model)
    write_json(os.path.join(ITEM_MODELS, bid + '.json'), {'parent': 'holeydigsite:block/' + bid})
    write_json(os.path.join(STATES, bid + '.json'), {'variants': {
        'facing=' + d: ({'model': 'holeydigsite:block/' + bid} if y == 0 else {'model': 'holeydigsite:block/' + bid, 'y': y})
        for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}})
    write_json(os.path.join(LOOT, bid + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'bonus_rolls': 0,
                   'entries': [{'type': 'minecraft:item', 'name': 'holeydigsite:' + bid}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': 'holeydigsite:blocks/' + bid})
    return sheet, elements


def render_preview_card(sheet, elements, size=140):
    """Orthographic 3D preview from the North-West-Up viewing angle so we can inspect the model & UVs directly."""
    import math
    # Camera looks from (-0.42, +0.46, -0.78) towards (8, 8, 8) -> shows NORTH (front), WEST (right), and UP (top)
    vx, vy, vz = -0.38, 0.44, -0.813
    vn = math.hypot(vx, vy, vz)
    dx, dy, dz = -vx / vn, -vy / vn, -vz / vn
    # Right vector on screen (looking towards +z, so -x is to the right, +x is to the left)
    rx, ry, rz = -dz, 0.0, dx
    rn = math.hypot(rx, ry, rz)
    rx, ry, rz = rx / rn, ry / rn, rz / rn
    # Up vector on screen
    ux, uy, uz = ry * dz - rz * dy, rz * dx - rx * dz, rx * dy - ry * dx

    im = Image.new('RGBA', (size, size), (42, 46, 54, 255))
    scale = 20.0 / size
    shades = {'up': 1.0, 'north': 0.88, 'west': 0.74, 'east': 0.72, 'south': 0.65, 'down': 0.55}
    visible_faces = [f for f, d in (('north', dz > 0), ('south', dz < 0), ('west', dx > 0),
                                    ('east', dx < 0), ('up', dy < 0), ('down', dy > 0)) if d]

    for py in range(size):
        v_cam = (size * 0.5 - py) * scale
        for px in range(size):
            u_cam = (px - size * 0.5) * scale
            ox = 8.0 + u_cam * rx + v_cam * ux - 40.0 * dx
            oy = 8.0 + u_cam * ry + v_cam * uy - 40.0 * dy
            oz = 8.0 + u_cam * rz + v_cam * uz - 40.0 * dz
            hits = []
            for el in elements:
                x0, y0, z0 = el['from']
                x1, y1, z1 = el['to']
                glow_el = not el.get('shade', True)
                for f in visible_faces:
                    if f not in el['faces']:
                        continue
                    if f == 'north' and abs(dz) > 1e-6:
                        t = (z0 - oz) / dz
                        hx, hy = ox + t * dx, oy + t * dy
                        if x0 <= hx <= x1 and y0 <= hy <= y1:
                            s = (x1 - hx) / max(1e-6, x1 - x0)
                            tt = (y1 - hy) / max(1e-6, y1 - y0)
                        else:
                            continue
                    elif f == 'west' and abs(dx) > 1e-6:
                        t = (x0 - ox) / dx
                        hz, hy = oz + t * dz, oy + t * dy
                        if z0 <= hz <= z1 and y0 <= hy <= y1:
                            s = (hz - z0) / max(1e-6, z1 - z0)
                            tt = (y1 - hy) / max(1e-6, y1 - y0)
                        else:
                            continue
                    elif f == 'up' and abs(dy) > 1e-6:
                        t = (y1 - oy) / dy
                        hx, hz = ox + t * dx, oz + t * dz
                        if x0 <= hx <= x1 and z0 <= hz <= z1:
                            s = (hx - x0) / max(1e-6, x1 - x0)
                            tt = (hz - z0) / max(1e-6, z1 - z0)
                        else:
                            continue
                    else:
                        continue
                    u0, v0, u1, v1 = el['faces'][f]['uv']
                    u = u0 + s * (u1 - u0)
                    v = v0 + tt * (v1 - v0)
                    tx = max(0, min(SHEET_W - 1, int(u * SHEET_W / 16.0)))
                    ty = max(0, min(SHEET_H - 1, int(v * SHEET_H / 16.0)))
                    col = sheet.getpixel((tx, ty))
                    if col[3] > 0:
                        k = 1.0 if glow_el else shades[f]
                        hits.append((t, (int(col[0] * k), int(col[1] * k), int(col[2] * k), col[3])))
            if hits:
                hits.sort(key=lambda h: h[0], reverse=True)
                r, g, b = 42, 46, 54
                for _, (cr, cg, cb, ca) in hits:
                    a = ca / 255.0
                    r = int(cr * a + r * (1 - a))
                    g = int(cg * a + g * (1 - a))
                    b = int(cb * a + b * (1 - a))
                im.putpixel((px, py), (r, g, b, 255))
    return im


if __name__ == '__main__':
    built = {}
    for b in BLOCKS:
        built[b] = build(b)
        print('wrote', b)
    if 'preview' in sys.argv:
        card_s = 140
        out = Image.new('RGBA', (len(BLOCKS) * (card_s + 8) + 8, card_s + 32 * 2 + 20), (28, 30, 36, 255))
        for i, (b, (sheet, els)) in enumerate(built.items()):
            x = 8 + i * (card_s + 8)
            out.paste(render_preview_card(sheet, els, card_s), (x, 8))
            out.paste(sheet.resize((128, 64), Image.NEAREST), (x + 6, card_s + 12))
        preview_path = os.path.join('build', 'phase21e_preview.png')
        os.makedirs('build', exist_ok=True)
        out.save(preview_path)
        print('preview written to', preview_path)
