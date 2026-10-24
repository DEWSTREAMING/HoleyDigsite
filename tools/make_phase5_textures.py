"""Regenerates the Phase 5 (Clump Belt) textures. Usage: python tools/make_phase5_textures.py  (needs Pillow)."""
import json, os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'holeydigsite', 'textures', 'block')

RUBBER = (46, 44, 42, 255)
RUBBER_HI = (70, 67, 63, 255)     # slat ridge
RUBBER_LO = (32, 30, 29, 255)     # gap behind a ridge
RAIL = (150, 152, 156, 255)
RAIL_HI = (196, 198, 202, 255)
RAIL_LO = (98, 100, 104, 255)
BOLT = (70, 72, 76, 255)
METAL = (128, 131, 136, 255)
METAL_HI = (170, 173, 178, 255)
METAL_LO = (88, 90, 95, 255)

def noise(x, y, k=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (k * 83492791)) & 7

def belt_frame(shift):
    """16x16 belt top, travel direction = up (towards v=0). shift moves the slats up by that many pixels."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if x in (0, 15):
                c = RAIL_LO
            elif x in (1, 14):
                c = RAIL_HI if y % 4 else RAIL
                if y % 8 == 3:
                    c = BOLT
            else:
                r = (y + shift) % 4
                c = RUBBER_HI if r == 0 else RUBBER_LO if r == 1 else RUBBER
                if noise(x, (y + shift) % 16) == 0 and r > 1:
                    c = tuple(min(255, v + 6) for v in c[:3]) + (255,)
            im.putpixel((x, y), c)
    return im

def strip(kind):
    """16x16 texture whose bottom 3 rows are a belt side (kind='side') or end (kind='end'); rest is metal."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = METAL if noise(x, y, 3) else METAL_HI
            im.putpixel((x, y), c)
    for x in range(16):
        if kind == 'side':
            im.putpixel((x, 13), RAIL_HI)
            im.putpixel((x, 14), BOLT if x % 4 == 2 else RAIL)
            im.putpixel((x, 15), RAIL_LO)
        else:  # end: the rubber wrapping round a roller between the two rails
            edge = x in (0, 1, 14, 15)
            im.putpixel((x, 13), RAIL_HI if edge else RUBBER_HI)
            im.putpixel((x, 14), RAIL if edge else (RUBBER if x % 3 else RUBBER_LO))
            im.putpixel((x, 15), RAIL_LO if edge else RUBBER_LO)
    return im

def bottom():
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = METAL_LO if (x in (0, 15) or y in (0, 15)) else (METAL if noise(x, y, 5) else METAL_HI)
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                c = BOLT
            im.putpixel((x, y), c)
    return im

def spout():
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = METAL_HI if noise(x, y, 9) < 2 else METAL
            if y in (0, 15) or x in (0, 15):
                c = METAL_LO
            if 5 <= x <= 10 and 5 <= y <= 10:   # worn trough where clumps slide
                c = (110, 92, 70, 255) if noise(x, y, 11) < 3 else (96, 98, 102, 255)
            im.putpixel((x, y), c)
    return im

def collar():
    """Opaque flange for the Water Intake's pipe collars. The 8x8 centre (4..12) is the face a pipe sits on;
    the rest is plain metal used for the collar's thin edges."""
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = METAL if noise(x, y, 13) else METAL_HI
            if 4 <= x < 12 and 4 <= y < 12:
                edge = x in (4, 11) or y in (4, 11)
                c = METAL_LO if edge else (RAIL_HI if (x + y) % 5 else RAIL)
                if (x, y) in ((5, 5), (10, 5), (5, 10), (10, 10)):
                    c = BOLT
            im.putpixel((x, y), c)
    return im

def main():
    os.makedirs(OUT, exist_ok=True)
    belt_frame(0).save(os.path.join(OUT, 'clump_belt_top.png'))
    # 4 frames, 1 px each = the 4 px slat period; 1+1+1+2 ticks = 4 px per 5 ticks = 16 px per 20 ticks,
    # exactly the clumps' speed (1 block per 20 ticks).
    anim = Image.new('RGBA', (16, 64))
    for k in range(4):
        anim.paste(belt_frame(k), (0, 16 * k))
    anim.save(os.path.join(OUT, 'clump_belt_top_moving.png'))
    meta = {"animation": {"frames": [{"index": 0, "time": 1}, {"index": 1, "time": 1},
                                     {"index": 2, "time": 1}, {"index": 3, "time": 2}]}}
    with open(os.path.join(OUT, 'clump_belt_top_moving.png.mcmeta'), 'w') as f:
        json.dump(meta, f, indent=2)
    strip('side').save(os.path.join(OUT, 'clump_belt_side.png'))
    strip('end').save(os.path.join(OUT, 'clump_belt_end.png'))
    bottom().save(os.path.join(OUT, 'clump_belt_bottom.png'))
    spout().save(os.path.join(OUT, 'clump_belt_spout.png'))
    collar().save(os.path.join(OUT, 'water_intake_collar.png'))
    print('wrote Phase 5 textures to', os.path.normpath(OUT))

if __name__ == '__main__':
    main()
