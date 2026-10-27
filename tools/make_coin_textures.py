"""Generates the three 16x16 coin textures (run from the project folder: python tools/make_coin_textures.py).

Vanilla-style: dark outline, shaded rim, lit upper-left / shaded lower-right, embossed mark.
Copper, silver and gold differ in colour, size (10/12/14 px) and mark (square hole / ring / sparkle)."""
from PIL import Image

OUT = "src/main/resources/assets/holeydigsite/textures/item/"

# palette per metal: outline, dark, mid-dark, base, light, highlight
PALETTES = {
    "copper": [(58, 26, 14), (120, 54, 30), (160, 78, 44), (196, 104, 60), (226, 142, 92), (246, 186, 140)],
    "silver": [(44, 46, 54), (104, 108, 120), (146, 150, 162), (186, 190, 200), (218, 222, 230), (250, 250, 255)],
    "gold":   [(72, 44, 6), (160, 104, 16), (204, 146, 26), (238, 190, 42), (252, 226, 92), (255, 250, 190)],
    "platinum": [(40, 52, 66), (110, 124, 142), (158, 172, 190), (206, 220, 232), (236, 246, 252), (255, 255, 255)],
    "crown":  [(58, 22, 8), (138, 64, 14), (186, 100, 22), (222, 144, 36), (248, 188, 66), (255, 232, 150)],
}

def disc(size):
    """Pixel mask of a round coin of even diameter `size`, centred in 16x16."""
    off = (16 - size) // 2
    r = size / 2
    c = off + r - 0.5
    pts = set()
    for y in range(16):
        for x in range(16):
            if (x - c) ** 2 + (y - c) ** 2 <= (r - 0.15) ** 2:
                pts.add((x, y))
    return pts, c

def _rows(rows):
    """Mark from rows of (y, x_from, x_to) inclusive."""
    return {(x, y) for y, a, b in rows for x in range(a, b + 1)}

MARKS = {
    # square hole like an old copper cash coin
    "hole": _rows([(7, 7, 8), (8, 7, 8)]),
    # small engraved ring
    "ring": _rows([(6, 7, 8), (9, 7, 8)]) | {(6, 7), (6, 8), (9, 7), (9, 8)},
    # four-point sparkle
    "sparkle": _rows([(5, 7, 8), (6, 7, 8), (7, 5, 10), (8, 5, 10), (9, 7, 8), (10, 7, 8)]),
    # small diamond, a cut platinum stud
    "diamond": _rows([(5, 7, 8), (6, 6, 9), (7, 5, 10), (8, 5, 10), (9, 6, 9), (10, 7, 8)]),
    # the crown's ruby is painted separately
    "none": set(),
}

RUBY = [(60, 4, 16), (150, 18, 30), (206, 40, 52), (246, 96, 104)]


def ruby(img):
    """Faceted ruby, 6 px wide, set in the middle of a crown."""
    px = img.load()
    dark, mid, base, hi = RUBY
    rows = {4: (7, 8), 5: (6, 9), 6: (5, 10), 7: (5, 10), 8: (5, 10), 9: (6, 9), 10: (7, 8)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            edge = x in (a, b) or y in (4, 10)
            px[x, y] = (dark if edge else base) + (255,)
    for p in [(6, 6), (7, 6), (6, 7)]:
        px[p] = hi + (255,)
    for p in [(9, 9), (8, 9), (9, 8)]:
        px[p] = mid + (255,)
    return img

def draw(metal, size, mark):
    pal = PALETTES[metal]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    pts, c = disc(size)
    edge = {p for p in pts if any((p[0] + dx, p[1] + dy) not in pts for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)))}
    rim = {p for p in pts - edge if any((p[0] + dx, p[1] + dy) in edge for dx, dy in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(-1,-1),(1,-1),(-1,1)))}
    for (x, y) in pts:
        light = (c - x) + (c - y)  # >0 toward upper-left
        if (x, y) in edge:
            col = pal[0]
        elif (x, y) in rim:
            col = pal[4] if light > 1.5 else pal[1] if light < -1.5 else pal[2]
        else:
            col = pal[3] if light > -2 else pal[2]
            if light > 3.5:
                col = pal[4]
        px[x, y] = col + (255,)
    # specular highlight on the upper-left of the rim
    hx, hy = int(c - size / 2 + 2), int(c - size / 2 + 3)
    for (x, y) in [(hx, hy), (hx + 1, hy - 1)]:
        if (x, y) in rim:
            px[x, y] = pal[5] + (255,)
    # engraved mark in the centre: dark pixels with a lit lower-right lip (light comes from upper-left)
    face = pts - edge - rim
    mark_px = MARKS[mark]
    for p in mark_px:
        px[p] = (pal[0] if mark == "hole" else pal[1]) + (255,)
    for (x, y) in mark_px:
        for q in ((x + 1, y), (x, y + 1)):
            if q not in mark_px and q in face:
                px[q] = pal[4] + (255,)
    # inner ring just inside the rim, so the face reads as a struck coin
    for (x, y) in face:
        d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
        if size >= 12 and abs(d - (size / 2 - 2.6)) < 0.5 and (x, y) not in mark_px and px[x, y][:3] == pal[3]:
            px[x, y] = pal[2] + (255,)
    return img

if __name__ == "__main__":
    for metal, size, mark in [("copper", 10, "hole"), ("silver", 12, "ring"), ("gold", 14, "sparkle"),
                              ("platinum", 16, "diamond")]:
        draw(metal, size, mark).save(OUT + f"{metal}_coin.png")
        print("wrote", OUT + f"{metal}_coin.png")
    ruby(draw("crown", 16, "none")).save(OUT + "ruby_crown.png")
    print("wrote", OUT + "ruby_crown.png")
