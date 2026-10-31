"""Draws the helper and modpack logos from the mod logo (48x48 pixel art shown at 4x).

python tools/make_logo_variants.py [out_dir]
Writes logo_helper.png and logo_pack.png (192 px) plus 384 px copies, and preview.png with all three side by side.
Mod: the pan with gold and three gems (unchanged). Pack: a floating island with a tree in the pan, under a sky.
Helper: the mod logo with a cog in place of the topaz (it's the pack's setup piece).
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "resources", "holeydigsite_logo.png")


def load():
    return Image.open(SRC).convert("RGBA").resize((48, 48), Image.NEAREST)


def dark(c):
    return max(c[:3]) < 40


def water_disk(im):
    """The blue water inside the pan: its centre and radius (nuggets sit in it, so they count too)."""
    blue = [(x, y) for y in range(14, 36) for x in range(14, 36)
            if (lambda c: c[2] > c[0] + 25)(im.getpixel((x, y)))]
    cx = (min(p[0] for p in blue) + max(p[0] for p in blue)) / 2
    cy = (min(p[1] for p in blue) + max(p[1] for p in blue)) / 2
    r = max(max(p[0] for p in blue) - min(p[0] for p in blue), max(p[1] for p in blue) - min(p[1] for p in blue)) / 2 + 0.5
    return cx, cy, r


def pack_logo(im):
    rnd = random.Random(7)
    out = im.copy()
    cx, cy, r = water_disk(im)
    for y in range(48):
        for x in range(48):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                t = (y - (cy - r)) / (2 * r)
                sky = (int(70 + 60 * t), int(120 + 60 * t), int(190 + 30 * t), 255)
                out.putpixel((x, y), sky)
    # two clouds
    for (ox, oy) in ((cx - 4, cy - 7), (cx + 4, cy - 6)):
        for dx in range(-2, 3):
            for dy in range(0, 2):
                if abs(dx) + dy < 3:
                    p = (int(ox + dx), int(oy + dy))
                    if math.hypot(p[0] - cx, p[1] - cy) <= r:
                        out.putpixel(p, (235, 240, 245, 255))
    # the island: grass top, dirt, a stone point underneath
    top = int(cy) - 1
    half = 6
    for row, w in enumerate((half, half, half - 1, half - 2, half - 4, half - 5)):
        for dx in range(-w, w + 1):
            x, y = int(cx + dx), top + row
            if row == 0:
                c = (88, 170, 60) if rnd.random() > 0.25 else (110, 190, 72)
            elif row < 3:
                c = (120, 84, 52) if rnd.random() > 0.3 else (98, 68, 42)
            else:
                c = (110, 110, 112) if rnd.random() > 0.3 else (86, 86, 90)
            out.putpixel((x, y), c + (255,))
    # gold specks in the dirt
    for dx in (-4, 1, 4):
        out.putpixel((int(cx + dx), top + 1), (240, 196, 60, 255))
    # tree: trunk and a round crown
    tx = int(cx - 3)
    for y in range(top - 4, top):
        out.putpixel((tx, y), (96, 66, 36, 255))
    for dx in range(-2, 3):
        for dy in range(-2, 2):
            if dx * dx + dy * dy <= 5:
                c = (60, 140, 50) if rnd.random() > 0.3 else (44, 112, 40)
                out.putpixel((tx + dx, top - 5 + dy), c + (255,))
    # a tiny drill on the right of the island
    dx0 = int(cx + 3)
    for y in range(top - 3, top):
        for x in range(dx0, dx0 + 2):
            out.putpixel((x, y), (150, 150, 155, 255))
    out.putpixel((dx0, top - 4), (200, 60, 50, 255))
    out.putpixel((dx0 + 1, top - 4), (200, 60, 50, 255))
    return out


def helper_logo(im):
    rnd = random.Random(3)
    out = im.copy()
    topaz = {(x, y) for y in range(30, 47) for x in range(34, 47)
             if (lambda c: max(c[:3]) - min(c[:3]) > 60 and c[0] > c[2])(im.getpixel((x, y)))}
    near = {(x + dx, y + dy) for x, y in topaz for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
    x0, x1 = min(p[0] for p in topaz), max(p[0] for p in topaz)
    y0, y1 = min(p[1] for p in topaz), max(p[1] for p in topaz)
    bg = [im.getpixel((x, y)) for y in range(48) for x in range(4) if dark(im.getpixel((x, y)))]
    # cover the gem and its dark outline, nothing of the pan
    for x, y in near:
        if (x, y) in topaz or dark(im.getpixel((x, y))):
            out.putpixel((x, y), rnd.choice(bg))
    # cog
    gx, gy = (x0 + x1) / 2, (y0 + y1) / 2
    for y in range(48):
        for x in range(48):
            dx, dy = x - gx, y - gy
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            tooth = 0.5 + 0.5 * math.cos(a * 8)
            outer = 4.6 + (1.6 if tooth > 0.55 else 0)
            if 1.8 < d <= outer:
                light = 0.5 + 0.5 * (-(dx + dy) / max(d, 0.1)) * 0.7
                g = int(110 + 70 * light)
                out.putpixel((x, y), (g, g, int(g * 1.05), 255))
            elif d <= 1.8:
                out.putpixel((x, y), rnd.choice(bg))
    return out


def save(img, out_dir, name):
    big = img.resize((192, 192), Image.NEAREST)
    big.save(os.path.join(out_dir, f"{name}.png"))
    img.resize((384, 384), Image.NEAREST).save(os.path.join(out_dir, f"{name}-384.png"))
    return big


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "logos")
    os.makedirs(out_dir, exist_ok=True)
    base = load()
    tiles = [base.resize((192, 192), Image.NEAREST),
             save(helper_logo(base), out_dir, "logo_helper"),
             save(pack_logo(base), out_dir, "logo_pack")]
    sheet = Image.new("RGBA", (192 * 3 + 32, 192 + 16), (40, 40, 40, 255))
    for i, t in enumerate(tiles):
        sheet.paste(t, (8 + i * 200, 8))
    sheet.save(os.path.join(out_dir, "preview.png"))
    print("wrote", out_dir)


if __name__ == "__main__":
    main()
