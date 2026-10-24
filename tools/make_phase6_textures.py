"""Regenerates the Phase 6 (Sluice Box) textures. Usage: python tools/make_phase6_textures.py  (needs Pillow)."""
import json, os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'holeydigsite', 'textures', 'block')

WOOD = (139, 101, 61, 255)
WOOD_HI = (163, 121, 76, 255)
WOOD_LO = (104, 74, 43, 255)
WOOD_GAP = (78, 55, 32, 255)
RAMP = (176, 140, 96, 255)
RAMP_LO = (150, 116, 76, 255)
METAL = (128, 131, 136, 255)
METAL_HI = (176, 179, 184, 255)
METAL_LO = (84, 86, 91, 255)
BOLT = (64, 66, 70, 255)
WATER = (46, 104, 196, 255)
WATER_HI = (96, 156, 232, 255)
WATER_FOAM = (190, 220, 250, 255)
WATER_STILL = (40, 88, 170, 255)
WATER_STILL_HI = (62, 112, 196, 255)

def n(x, y, k=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (k * 83492791)) & 7

def img(f):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), f(x, y))
    return im

def planks(x, y):
    """Horizontal boards 4 px high with dark gaps and a few nail heads."""
    if y % 4 == 3:
        return WOOD_GAP
    if (x + (y // 4) * 5) % 16 == 0:
        return WOOD_LO
    if (x, y % 4) in ((2, 1), (13, 1)) and (y // 4) % 2 == 0:
        return BOLT
    return WOOD_HI if n(x, y, 1) == 0 else WOOD_LO if n(x, y, 2) == 0 else WOOD

def port(x, y):
    """Planks with a round metal water port (pipe flange) in the middle, like the drill's back port."""
    dx, dy = x - 7.5, y - 7.5
    r = (dx * dx + dy * dy) ** 0.5
    if r < 2.6:
        return (34, 36, 40, 255)
    if r < 4.0:
        return METAL_HI if dx + dy < 0 else METAL
    if r < 4.9:
        return METAL_LO if n(x, y, 3) else BOLT
    return planks(x, y)

def ramp(x, y):
    if x % 6 == 5:
        return RAMP_LO
    return RAMP if n(x, y, 4) else RAMP_LO

def riffle(x, y):
    return METAL_HI if y < 5 else METAL if y < 11 else METAL_LO

def tray(x, y):
    if x in (0, 15) or y in (0, 15):
        return METAL_LO
    return METAL_HI if n(x, y, 5) == 0 else METAL

def back(x, y):
    if 3 <= x <= 12 and 1 <= y <= 4:      # inlet slot where clumps drop in
        return (40, 30, 20, 255)
    return planks(x, y)

def dirt(x, y):
    k = n(x, y, 6)
    return (101, 72, 46, 255) if k < 3 else (84, 58, 36, 255) if k < 6 else (122, 92, 60, 255)

def water_frame(shift, still=False):
    def f(x, y):
        yy = (y + shift) % 16
        if still:
            return WATER_STILL_HI if (n(x, yy, 7) == 0 and x % 3 == 1) else WATER_STILL
        streak = (x * 5 + yy * 3) % 11
        if streak == 0:
            return WATER_FOAM
        if streak in (1, 6) or n(x, yy, 8) == 0:
            return WATER_HI
        return WATER
    return img(f)

def lamp(c):
    hi = tuple(min(255, v + 70) for v in c[:3]) + (255,)
    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return BOLT
        if 5 <= x <= 7 and 5 <= y <= 7:
            return hi
        return c
    return img(f)

def main():
    os.makedirs(OUT, exist_ok=True)
    img(planks).save(os.path.join(OUT, 'sluice_wood.png'))
    img(port).save(os.path.join(OUT, 'sluice_port.png'))
    img(ramp).save(os.path.join(OUT, 'sluice_ramp.png'))
    img(riffle).save(os.path.join(OUT, 'sluice_riffle.png'))
    img(tray).save(os.path.join(OUT, 'sluice_tray.png'))
    img(back).save(os.path.join(OUT, 'sluice_back.png'))
    img(dirt).save(os.path.join(OUT, 'sluice_dirt.png'))
    water_frame(0, still=True).save(os.path.join(OUT, 'sluice_water_still.png'))
    # flowing water: 8 frames, 2 px per frame towards v=0 (the tray end), 2 ticks each
    anim = Image.new('RGBA', (16, 128))
    for k in range(8):
        anim.paste(water_frame(2 * k), (0, 16 * k))
    anim.save(os.path.join(OUT, 'sluice_water_flow.png'))
    with open(os.path.join(OUT, 'sluice_water_flow.png.mcmeta'), 'w') as f:
        json.dump({"animation": {"frametime": 2}}, f, indent=2)
    lamp((60, 200, 70, 255)).save(os.path.join(OUT, 'sluice_lamp_working.png'))
    lamp((220, 190, 50, 255)).save(os.path.join(OUT, 'sluice_lamp_idle.png'))
    lamp((210, 50, 45, 255)).save(os.path.join(OUT, 'sluice_lamp_stopped.png'))
    lamp((45, 48, 55, 255)).save(os.path.join(OUT, 'sluice_lamp_off.png'))
    print('wrote Phase 6 textures to', os.path.normpath(OUT))

if __name__ == '__main__':
    main()
