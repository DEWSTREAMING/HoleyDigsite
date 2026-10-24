"""Generates the Phase 7 textures (16x16, vanilla style) into src/main/resources/assets/holeydigsite/textures.

    python tools/make_phase7_textures.py        (needs Pillow; output is deterministic)

- Folkiron / Goldchro chunks (small, medium, large) and Rubies (small, medium, large). The large Ruby is the RubyMod
  ruby texture (pixel map below); medium and small are smaller cuts with the same palette.
- Deep Dirtrock and Abyssal Dirtrock (tier 2 / 3 rock), Deep / Abyssal Dirt Clumps, the darker dirt in tier 2 / 3 pans.
- Sell Pad (top with the coin symbol, side, bottom).
"""
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                    'holeydigsite', 'textures')
BLOCK = os.path.join(ROOT, 'block')
ITEM = os.path.join(ROOT, 'item')
T = (0, 0, 0, 0)


def new(w=16, h=16):
    return Image.new('RGBA', (w, h), T)


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name + '.png'))


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def from_map(rows, palette, top=0):
    img = new()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y + top), rgba(palette[ch]))
    return img


# ------------------------------------------------------------------------------------------------
# Rubies: large = the RubyMod ruby, medium/small = smaller cuts with the same palette
# ------------------------------------------------------------------------------------------------
RUBY = {'a': (74, 6, 22), 'b': (255, 128, 140), 'c': (255, 225, 230), 'd': (226, 52, 78), 'e': (186, 22, 52),
        'f': (128, 12, 36)}
RUBY_LARGE = [
    '................',
    '................',
    '....aaaaaaaa....',
    '...abcddddeea...',
    '..abcddddeeefa..',
    '.addddddeeeeffa.',
    '.affffffffffffa.',
    '.adeeeeeeeeeffa.',
    '..adeeeeeeeffa..',
    '...adeeeeeffa...',
    '....adeeeffa....',
    '.....adeefa.....',
    '......adfa......',
    '.......aa.......',
]
RUBY_MEDIUM = [
    '.....aaaaaa.....',
    '....abcddeea....',
    '...acdddeeefa...',
    '...affffffffa...',
    '...adeeeeeffa...',
    '....adeeeefa....',
    '.....adeefa.....',
    '......adfa......',
    '.......aa.......',
]
RUBY_SMALL = [
    '......aaaa......',
    '.....acdefa.....',
    '.....affffa.....',
    '......adfa......',
    '.......aa.......',
]


def rubies():
    save(from_map(RUBY_LARGE, RUBY), ITEM, 'ruby_large')
    save(from_map(RUBY_MEDIUM, RUBY, top=4), ITEM, 'ruby_medium')
    save(from_map(RUBY_SMALL, RUBY, top=6), ITEM, 'ruby_small')


# ------------------------------------------------------------------------------------------------
# Metal chunks: irregular lumps, lit from the top-left, dark outline
# ------------------------------------------------------------------------------------------------
def blob_mask(seed, rx, ry, cx=7.5, cy=8.5):
    rnd = random.Random(seed)
    # radius jitter per angle sector -> lumpy outline
    import math
    jit = [1.0 + rnd.uniform(-0.18, 0.12) for _ in range(12)]
    mask = set()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - cx) / rx, (y - cy) / ry
            ang = (math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 12
            i = int(ang) % 12
            f = ang - int(ang)
            r = jit[i] * (1 - f) + jit[(i + 1) % 12] * f
            if dx * dx + dy * dy <= r * r:
                mask.add((x, y))
    return mask


def chunk(name, seed, rx, ry, pal, specks, sheen=None):
    """pal = (outline, dark, mid, light, highlight); specks = list of (colour, count); sheen = chrome streak colours."""
    rnd = random.Random(seed * 31 + 7)
    mask = blob_mask(seed, rx, ry)
    img = new()
    outline, dark, mid, light, hi = pal
    for (x, y) in mask:
        edge = any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            c = outline
        else:
            # light from the top-left: compare with the neighbour up-left / down-right
            ul = (x - 1, y - 1) in mask and (x - 2, y - 2) in mask
            dr = (x + 1, y + 1) in mask and (x + 2, y + 2) in mask
            c = mid
            if not ul:
                c = light
            elif not dr:
                c = dark
            if rnd.random() < 0.12:
                c = dark if c == mid else mid
        img.putpixel((x, y), rgba(c))
    inner = [p for p in mask if img.getpixel(p)[:3] in (mid, dark, light)]
    inner.sort()
    for colour, count in specks:
        for _ in range(count):
            if inner:
                img.putpixel(rnd.choice(inner), rgba(colour))
    # a highlight pixel or two on the lit edge
    lit = [p for p in mask if img.getpixel(p)[:3] == light]
    lit.sort()
    for p in lit[: max(1, len(lit) // 4)]:
        if rnd.random() < 0.6:
            img.putpixel(p, rgba(hi))
    if sheen:
        # chrome sheen: a short diagonal streak across the upper part
        ys = sorted({y for _, y in mask})
        top = ys[0] + max(1, len(ys) // 4)
        xs = sorted(x for x, y in mask if y == top)
        x0 = xs[len(xs) // 3] if xs else 7
        for i in range(max(2, len(ys) // 3)):
            for j, col in enumerate(sheen):
                p = (x0 + i + j, top + i)
                if p in mask and img.getpixel(p)[:3] != outline:
                    img.putpixel(p, rgba(col))
    save(img, ITEM, name)


FOLKIRON = ((44, 38, 34), (84, 75, 68), (116, 105, 95), (148, 138, 126), (182, 174, 162))
FOLK_SPECKS = [((126, 88, 60), 1), ((98, 70, 52), 1)]
GOLDCHRO = ((96, 60, 12), (178, 118, 26), (224, 168, 50), (248, 210, 96), (255, 238, 168))
GOLD_SHEEN = [(232, 236, 242), (196, 204, 214)]


def metals():
    for size, (rx, ry), sc in (('small', (3.2, 2.7), 1), ('medium', (4.8, 4.0), 2), ('large', (6.6, 5.6), 3)):
        chunk('folkiron_chunk_' + size, 100 + sc, rx, ry, FOLKIRON, [(c, n * sc) for c, n in FOLK_SPECKS])
        chunk('goldchro_chunk_' + size, 200 + sc, rx, ry, GOLDCHRO, [((200, 140, 34), sc)], GOLD_SHEEN)


# ------------------------------------------------------------------------------------------------
# Dirtrock tiers
# ------------------------------------------------------------------------------------------------
def rock(name, seed, dirt, stones, stone_cols, shadow, glints=None):
    rnd = random.Random(seed)
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgba(dirt[rnd.choice((0, 1, 1, 2, 2, 2, 3))]))
    for _ in range(7):
        x, y = rnd.randrange(16), rnd.randrange(16)
        for i in range(rnd.randint(2, 4)):
            img.putpixel(((x + i) % 16, y), rgba(dirt[0]))
    hi, mid, lo = stone_cols
    for _ in range(stones):
        sx, sy, size = rnd.randrange(16), rnd.randrange(16), rnd.choice((1, 2, 2, 3))
        for dy in range(size):
            for dx in range(size):
                if size == 3 and (dx, dy) in ((0, 0), (2, 2)):
                    continue
                shade = hi if dy == 0 else mid
                if dx + dy >= size:
                    shade = lo
                img.putpixel(((sx + dx) % 16, (sy + dy) % 16), rgba(shade))
        img.putpixel(((sx + size) % 16, (sy + size - 1) % 16), rgba(shadow))
    if glints:
        for (x, y), col in glints:
            img.putpixel((x, y), rgba(col))
    save(img, BLOCK, name)


def rocks():
    rock('deep_dirtrock', 5051,
         [(30, 22, 17), (37, 27, 20), (43, 31, 23), (50, 36, 26)], 14,
         ((88, 86, 84), (68, 66, 64), (50, 49, 48)), (20, 15, 12))
    glint = [((3, 4), (196, 214, 230)), ((12, 2), (230, 196, 120)), ((9, 11), (210, 220, 236)),
             ((1, 13), (220, 120, 140)), ((14, 9), (200, 210, 226)), ((6, 7), (150, 160, 176))]
    rock('abyssal_dirtrock', 6061,
         [(14, 12, 13), (18, 15, 16), (22, 19, 20), (27, 23, 24)], 16,
         ((58, 56, 62), (42, 41, 46), (30, 29, 33)), (8, 7, 8), glint)


# ------------------------------------------------------------------------------------------------
# Darker dirt: clump items and the dirt layer in tier 2 / 3 pans
# ------------------------------------------------------------------------------------------------
def darken(src, name, folder, factor, tint=(1.0, 1.0, 1.0)):
    img = Image.open(src).convert('RGBA')
    out = new(img.width, img.height)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            out.putpixel((x, y), (int(r * factor * tint[0]), int(g * factor * tint[1]), int(b * factor * tint[2]), a))
    save(out, folder, name)


def pan_dirt(name, seed, pal, specks):
    rnd = random.Random(seed)
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgba(pal[rnd.choice((0, 1, 1, 2, 2, 3))]))
    for (col, n) in specks:
        for _ in range(n):
            img.putpixel((rnd.randrange(16), rnd.randrange(16)), rgba(col))
    save(img, BLOCK, name)


def dirts():
    clump = os.path.join(ITEM, 'dirt_clump.png')
    darken(clump, 'dirt_clump_deep', ITEM, 0.62, (0.95, 0.95, 1.0))
    darken(clump, 'dirt_clump_abyssal', ITEM, 0.34, (0.9, 0.85, 1.0))
    # add a few glints to the abyssal clump
    img = Image.open(os.path.join(ITEM, 'dirt_clump_abyssal.png'))
    for p, c in (((6, 6), (200, 212, 230)), ((10, 9), (226, 196, 120)), ((5, 10), (210, 120, 140))):
        if img.getpixel(p)[3] > 0:
            img.putpixel(p, rgba(c))
    img.save(os.path.join(ITEM, 'dirt_clump_abyssal.png'))
    pan_dirt('pan_dirt_deep', 7071, [(52, 37, 26), (62, 44, 31), (72, 51, 36), (84, 60, 42)], [((96, 94, 90), 5)])
    pan_dirt('pan_dirt_abyssal', 8081, [(20, 17, 18), (26, 22, 23), (32, 27, 28), (40, 34, 35)],
             [((70, 68, 74), 5), ((196, 210, 228), 2), ((226, 196, 120), 1)])


# ------------------------------------------------------------------------------------------------
# Sell Pad
# ------------------------------------------------------------------------------------------------
def sell_pad():
    rnd = random.Random(9091)
    wood = [(88, 62, 36), (100, 71, 42), (112, 80, 48), (122, 88, 53)]
    trim = (60, 42, 25)
    gold = {'o': (122, 82, 14), 'd': (196, 140, 32), 'm': (236, 186, 56), 'l': (255, 226, 120), 'h': (255, 246, 200)}
    top = new()
    for y in range(16):
        for x in range(16):
            c = wood[rnd.choice((0, 1, 2, 2, 3))]
            if y % 4 == 3:
                c = wood[0]  # plank seams
            if x in (0, 15) or y in (0, 15):
                c = trim
            top.putpixel((x, y), rgba(c))
    coin = [
        '....oooooo....',
        '..oommmmmmoo..',
        '.omllmmmmmmdo.',
        '.olhmddddmmdo.',
        'omlmdmmmmdmmdo',
        'omlmdmllmdmmdo',
        'ommdmlhmmmdmdo',
        'ommdmmmmmmdmdo',
        'ommmdmmmmdmmdo',
        'ommmdmmmmdmmdo',
        '.ommmddddmmdo.',
        '.oddmmmmmmddo.',
        '..ooddddddoo..',
        '....oooooo....',
    ]
    for y, row in enumerate(coin):
        for x, ch in enumerate(row):
            if ch != '.':
                top.putpixel((x + 1, y + 1), rgba(gold[ch]))
    save(top, BLOCK, 'sell_pad_top')
    side = new()
    for y in range(16):
        for x in range(16):
            c = wood[rnd.choice((0, 1, 1, 2))]
            if y in (12, 15):
                c = trim
            if y == 13:
                c = gold['d'] if x % 4 else gold['l']
            side.putpixel((x, y), rgba(c))
    save(side, BLOCK, 'sell_pad_side')
    bottom = new()
    for y in range(16):
        for x in range(16):
            bottom.putpixel((x, y), rgba(wood[rnd.choice((0, 0, 1))]))
    save(bottom, BLOCK, 'sell_pad_bottom')


if __name__ == '__main__':
    rubies()
    metals()
    rocks()
    dirts()
    sell_pad()
    print('Phase 7 textures written to', os.path.abspath(ROOT))
