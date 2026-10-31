"""labPBR specular maps (<texture>_s.png) for shaders that read them (Complementary with resource-pack materials, BSL, ...).

Channels: R smoothness, G F0 (238-254 = metal tinted by the texture's own colour), B porosity, A emission (255 = none).
Materials come from the pixel colours inside a list of machine, metal, gem and light textures, so regenerated textures
just need a rerun. Animated textures get a copy of their .mcmeta.

    python tools/make_pbr.py           write all maps
    python tools/make_pbr.py --clean   remove them
"""
import colorsys
import os
import re
import shutil
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, 'src/main/resources/assets/holeydigsite/textures')

METAL_F0 = 240
NONE = 255

MACHINE = re.compile(r'^block/(skin/.*|mining_drill.*|sluice_(?!water|dirt).*|crucible.*|large_crucible.*|forge.*|hot_forge.*'
                     r'|crafting_forge.*|mold.*|casting_mold.*|clump_belt.*|fast_clump_belt.*|filter_belt.*|belt_splitter.*'
                     r'|gem_cutter.*|water_pipe.*|water_intake.*|lava_pipe.*|lava_intake.*|smithing_anvil.*|windpump_steel'
                     r'|corrugated_iron|ore_cart|ute_.*|brass_plaque|golden_pan_trophy|money_pan.*|pan|large_pan_trim'
                     r'|iron_crate.*|gold_crate.*|prospector_crate.*|prospector_padlock|outback_crate.*|outback_padlock|shop_button.*|sell_pad.*|buy_pad.*|event_sell_pad.*|skin_bench.*|tool_rack'
                     r'|drop_chute|gem_display_case|rusty_mine_bell|miners_helmet|little_windmill_fan|golden_drill.*)$')
ALL_METAL = re.compile(r'^(block/(metal_|chunks_|coin_pile_).*|item/(ingot|lump|chunk)/.*|item/\w+_coin|item/welcome_stranger)$')
GEM = re.compile(r'^item/(black_opal|boulder_opal|fire_opal|pink_diamond|potch|ruby_\w+|sapphire_\w+|topaz_\w+|smith_\w+_gem)$')
UNCUT = re.compile(r'^item/uncut_\w+$')
SMITH = re.compile(r'^item/smith_(?!\w+_(grip|handle|binding)$)\w+$')
LIGHT = re.compile(r'^(block/(.*lantern.*|.*_lamp(_working|_idle|_stopped)?|(hot_|crafting_)?forge_\w+_lit|glowroot_crop_stage\d)'
                   r'|item/(glowroot|roasted_glowroot))$')
STONE = re.compile(r'^block/(forge.*|hot_forge.*|river_stone_lamp|sandstone_lamp)$')
MOLTEN = re.compile(r'^block/(molten_\w+|lava_pipe_filled)$')
LAVA = re.compile(r'^block/lava_intake_\w+_active$')


def hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def machine_px(c):
    h, s, v = hsv(c)
    deg = h * 360
    if v < 0.18:
        return (60, 10, 0)  # outlines and gaps
    if s < 0.18:
        return (140 + int(60 * v), METAL_F0, 0)  # steel, iron
    if 35 <= deg <= 62 and s > 0.35 and v > 0.45:
        return (195, METAL_F0, 0)  # gold, brass
    if 8 <= deg < 35 and s > 0.5 and v > 0.55:
        return (165, METAL_F0, 0)  # copper
    return (45, 10, 30)  # wood, paint, cloth


def metal_px(c):
    v = hsv(c)[2]
    return (60, 10, 0) if v < 0.15 else (150 + int(70 * v), METAL_F0, 0)


def glow(c):
    h, s, v = hsv(c)
    return min(254, int(v * 254)) if v > 0.72 and (s > 0.3 or v > 0.9) else NONE


def lava_glow(c):
    h, s, v = hsv(c)
    return 220 if h * 360 < 55 and s > 0.5 and v > 0.55 else NONE


def obsidian_glow(c):
    h, s, v = hsv(c)
    return 254 if 250 <= h * 360 <= 320 and s > 0.35 and v >= 0.18 else NONE  # the trim; the body is darker


def frosted_glow(c):
    h, s, v = hsv(c)
    return 70 if 180 <= h * 360 <= 240 and s > 0.2 and v > 0.55 else NONE


def black_opal_glow(c):
    h, s, v = hsv(c)
    return min(215, int(110 + 120 * v)) if s > 0.4 and v > 0.4 else NONE  # the flecks, strong but not lamp-bright; the body is dark and greyish


# skins with a glowing accent, by skin id (every wear of the skin shares it)
SKIN_GLOW = {'obsidian': obsidian_glow, 'frosted': frosted_glow, 'black_opal': black_opal_glow}
# skins whose glowing pixels are smooth and not metal, as (R, G, B) of the map
SKIN_GLOW_MATERIAL = {'black_opal': (250, 30, 0)}
# skins whose steel parts are a polished, non-metal body
SKIN_BODY = {'black_opal': (238, 28, 0)}


def material(key):
    """(pixel -> (R, G, B), pixel -> A) for a texture key like 'block/sluice_lamp_working', or None to skip."""
    if MOLTEN.match(key):
        return (lambda c: (200, 10, 0)), (lambda c: 230)
    if ALL_METAL.match(key):
        return metal_px, (lambda c: NONE)
    if GEM.match(key):
        return (lambda c: (235, 40, 0)), (lambda c: NONE)
    if UNCUT.match(key):
        return (lambda c: (130, 25, 20)), (lambda c: NONE)
    base = machine_px if (MACHINE.match(key) or SMITH.match(key)) else None
    light = glow if LIGHT.match(key) else (lava_glow if LAVA.match(key) else None)
    if key.startswith('block/skin/'):
        light = SKIN_GLOW.get(key.split('/')[2], light)
    if base is None and light is None:
        return None
    return base or (lambda c: (45, 10, 30)), light or (lambda c: NONE)


def clean_overlays():
    n = 0
    for skin in SKIN_GLOW:
        path = os.path.join(TEX, 'block/skin', skin, 'glow')
        if os.path.isdir(path):
            n += len(os.listdir(path))
            shutil.rmtree(path)
    return n


def glow_overlays():
    """Fresh skins' glowing pixels on their own, for the fullbright layer SkinnedBakedModel adds; worn skins share these."""
    n = 0
    for skin, light in SKIN_GLOW.items():
        src = os.path.join(TEX, 'block/skin', skin)
        for f in sorted(os.listdir(src)):
            if not f.endswith('.png') or f.endswith(('_s.png', '_n.png')):
                continue
            img = Image.open(os.path.join(src, f)).convert('RGBA')
            out = Image.new('RGBA', img.size)
            px, opx = img.load(), out.load()
            lit = False
            for y in range(img.height):
                for x in range(img.width):
                    if px[x, y][3] >= 8 and light(px[x, y]) != NONE:
                        opx[x, y] = px[x, y]
                        lit = True
            if lit:
                os.makedirs(os.path.join(src, 'glow'), exist_ok=True)
                out.save(os.path.join(src, 'glow', f))
                meta = os.path.join(src, f + '.mcmeta')
                if os.path.exists(meta):
                    shutil.copyfile(meta, os.path.join(src, 'glow', f + '.mcmeta'))
                n += 1
    return n


def targets():
    for dirpath, _, files in os.walk(TEX):
        for f in files:
            if not f.endswith('.png') or f.endswith(('_s.png', '_n.png')):
                continue
            rel = os.path.relpath(os.path.join(dirpath, f), TEX).replace(os.sep, '/')[:-4]
            if rel.startswith(('block/', 'item/')):
                yield rel


def skin_base(key):
    if not key.startswith('block/skin/'):
        return None
    path = os.path.join(TEX, 'block', key.rsplit('/', 1)[1] + '.png')
    return Image.open(path).convert('RGBA') if os.path.exists(path) else None


def write(key, rgb, alpha, stone=False):
    src = os.path.join(TEX, key + '.png')
    img = Image.open(src).convert('RGBA')
    out = Image.new('RGBA', img.size)
    px, opx = img.load(), out.load()
    w, h = img.size
    mats = {(x, y): rgb(px[x, y]) for y in range(h) for x in range(w)}
    base = skin_base(key)
    skin = key.split('/')[2] if key.startswith('block/skin/') else None
    if base is not None and base.width == img.width and img.height % base.width == 0:
        # a skin keeps the default layout, so its metal and wood sit where the default texture's do; a strip of more
        # frames than the default (an animated skin) repeats the default's frames in order
        bpx = base.load()
        bframes = max(1, base.height // base.width)
        mats = {(x, y): rgb(bpx[x, (y % w) + w * ((y // w) % bframes)]) for y in range(h) for x in range(w)}

    def metal(x, y):
        return px[x % w, y % h][3] >= 8 and mats[(x % w, y % h)][1] == METAL_F0

    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] < 8:
                opx[x, y] = (0, 0, 0, NONE)
                continue
            a = alpha(c)
            if a != NONE:
                opx[x, y] = SKIN_GLOW_MATERIAL.get(skin, (120, 10, 0)) + (a,)  # the light itself is not metal
                continue
            r, g, b = mats[(x, y)]
            if rgb is machine_px and g == METAL_F0:
                # trim comes in bands; lone bright or grey pixels are wood grain, and grey in stone textures is stone
                n = sum(metal(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)) - 1
                if stone and hsv(c)[1] < 0.18:
                    r, g, b = 70, 10, 60
                elif n < 3:
                    r, g, b = 45, 10, 30
                elif skin in SKIN_BODY and r != 195:
                    r, g, b = SKIN_BODY[skin]
            opx[x, y] = (r, g, b, a)
    out.save(os.path.join(TEX, key + '_s.png'))
    if os.path.exists(src + '.mcmeta'):
        shutil.copyfile(src + '.mcmeta', os.path.join(TEX, key + '_s.png.mcmeta'))


def clean():
    n = 0
    for dirpath, _, files in os.walk(TEX):
        for f in files:
            if f.endswith(('_s.png', '_s.png.mcmeta')):
                os.remove(os.path.join(dirpath, f))
                n += 1
    return n


def main():
    removed = clean() + clean_overlays()
    if '--clean' in sys.argv:
        print(f'{removed} PBR files removed')
        return
    overlays = glow_overlays()
    n = 0
    for key in sorted(targets()):
        m = material(key)
        if m:
            write(key, *m, stone=bool(STONE.match(key)))
            n += 1
    print(f'{n} specular maps written, {overlays} glow overlays')


if __name__ == '__main__':
    main()
