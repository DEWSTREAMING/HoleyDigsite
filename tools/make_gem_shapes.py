"""Generates distinct shapes for Sapphire and Topaz (cut and uncut, 3 sizes each).

Phase 21g:
- Ruby: unchanged (keeps its Phase 7 / 12 textures).
- Sapphire:
    cut: oval / cushion cut (rounded pillow, wider than tall, star of facets on top, bright table highlight)
    uncut: hexagonal barrel crystal (short 6-sided column, cloudy, rough)
- Topaz:
    cut: emerald cut (tall rectangle with clipped corners and step / parallel facets)
    uncut: long prism with a pointed end (rough, striated vertical column)

Usage:
    python tools/make_gem_shapes.py
"""
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                    'holeydigsite', 'textures', 'item')
T = (0, 0, 0, 0)

# ------------------------------------------------------------------------------------------------
# Palettes
# ------------------------------------------------------------------------------------------------
# Cut Sapphire: matches existing hue 222
SAPPHIRE_CUT = {
    'a': (5, 26, 74),       # dark border
    'f': (12, 46, 128),     # deep shadow facet
    'e': (22, 71, 186),     # base royal blue
    'd': (51, 104, 226),    # bright blue
    'b': (128, 166, 255),   # highlight shimmer
    'c': (255, 225, 230),   # specular white highlight
}

# Cut Topaz: matches existing hue 38
TOPAZ_CUT = {
    'a': (85, 56, 6),       # dark border
    'f': (147, 98, 13),     # deep shadow step facet
    'e': (213, 144, 25),    # mid amber body
    'd': (255, 183, 58),    # bright golden step facet
    'b': (255, 208, 128),   # bright highlight shine
    'c': (255, 225, 230),   # specular white table highlight
}

# Uncut Sapphire: dull cloudy blue + stone
SAPPHIRE_UNCUT = {
    'o': (0, 2, 52),        # dark outline
    '0': (28, 42, 92),      # shadow face
    '1': (44, 72, 140),     # mid rough face
    '2': (76, 108, 176),    # lit face
    '3': (150, 160, 190),   # cloudy highlight / specks
    's': (84, 80, 76),      # stone dark
    'S': (112, 107, 101),   # stone mid
    'm': (138, 132, 124),   # stone light
}

# Uncut Topaz: golden / amber rough prism + stone
TOPAZ_UNCUT = {
    'o': (70, 30, 0),       # dark outline
    '0': (110, 70, 22),     # shadow face
    '1': (168, 118, 40),    # mid amber body
    '2': (206, 158, 70),    # lit face
    '3': (214, 196, 150),   # pale striation ridge
    's': (84, 80, 76),      # stone dark
    'S': (112, 107, 101),   # stone mid
    'm': (138, 132, 124),   # stone light
}

# ------------------------------------------------------------------------------------------------
# Cut Sapphire maps: Oval / cushion cut (wider than tall, star facets, bright table highlight)
# ------------------------------------------------------------------------------------------------
SAPPHIRE_LARGE = [
    '................',
    '................',
    '....aaaaaaaa....',
    '..aadbbdeeefaa..',
    '.abccddeeeefffa.',
    '.abcdddddeeeffa.',
    '.adddbddeeffffa.',
    '.addddffdeeeffa.',
    '.addefdeeeeeffa.',
    '.addeeeeeeeeffa.',
    '.aeeffeeeeefffa.',
    '..aaffffffffaa..',
    '....aaaaaaaa....',
    '................',
    '................',
    '................',
]

SAPPHIRE_MEDIUM = [
    '................',
    '................',
    '................',
    '................',
    '.....aaaaaa.....',
    '...aabccdefaa...',
    '...abcddeeffa...',
    '...addfeefffa...',
    '...adeeeefffa...',
    '...aeeffeeffa...',
    '...affffffffa...',
    '.....aaaaaa.....',
    '................',
    '................',
    '................',
    '................',
]

SAPPHIRE_SMALL = [
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '.....aaaaa......',
    '....abbcdefa....',
    '....abcdeffa....',
    '....adeefffa....',
    '.....aaaaa......',
    '................',
    '................',
    '................',
    '................',
    '................',
]

# ------------------------------------------------------------------------------------------------
# Cut Topaz maps: Emerald cut (tall rectangle with clipped corners and step parallel facets)
# ------------------------------------------------------------------------------------------------
TOPAZ_LARGE = [
    '................',
    '.....aaaaaa.....',
    '....aaddffaa....',
    '...abbcddfffa...',
    '...abccddfeea...',
    '...abbcddfeea...',
    '...abddddfffa...',
    '...abddedfffa...',
    '...abddedfffa...',
    '...abddedfffa...',
    '...abdeeefffa...',
    '...abdeeefffa...',
    '...aeeeedfffa...',
    '....aeffffaa....',
    '.....aaaaaa.....',
    '................',
]

TOPAZ_MEDIUM = [
    '................',
    '................',
    '................',
    '......aaaa......',
    '.....aaddaa.....',
    '....abbccfea....',
    '....abccdfea....',
    '....abddffea....',
    '....abdedffa....',
    '....abdeeffa....',
    '....aedeeffa....',
    '....aeeeeffa....',
    '.....aeffaa.....',
    '......aaaa......',
    '................',
    '................',
]

TOPAZ_SMALL = [
    '................',
    '................',
    '................',
    '................',
    '................',
    '......aaa.......',
    '.....abcda......',
    '.....abcdfa.....',
    '.....abedfa.....',
    '.....adedfa.....',
    '.....adefa......',
    '......aaa.......',
    '................',
    '................',
    '................',
    '................',
]

# ------------------------------------------------------------------------------------------------
# Uncut Sapphire maps: Hexagonal barrel crystal (short six-sided column, cloudy, rough)
# ------------------------------------------------------------------------------------------------
UNCUT_SAPPHIRE_LARGE = [
    '................',
    '................',
    '.....oooooo.....',
    '...oo222322oo...',
    '..o2232211110o..',
    '.o223211131100o.',
    '.o222111111000o.',
    '.o232113111000o.',
    '.o222111113000o.',
    '.o221113111000o.',
    '.o21111111000so.',
    '..o111111100sSo.',
    '..o011111000sSo.',
    '...oo000000oo...',
    '.....oooooo.....',
    '................',
]

UNCUT_SAPPHIRE_MEDIUM = [
    '................',
    '................',
    '................',
    '.....ooooo......',
    '...oo23222oo....',
    '..o223111000o...',
    '..o221131000o...',
    '..o231111000o...',
    '..o221311000o...',
    '..o21111100so...',
    '..o11111000so...',
    '...oo11000oo....',
    '.....ooooo......',
    '................',
    '................',
    '................',
]

UNCUT_SAPPHIRE_SMALL = [
    '................',
    '................',
    '................',
    '................',
    '................',
    '.....ooooo......',
    '....o232100o....',
    '...o22131100o...',
    '...o2111100so...',
    '....o11000so....',
    '.....ooooo......',
    '................',
    '................',
    '................',
    '................',
    '................',
]

# ------------------------------------------------------------------------------------------------
# Uncut Topaz maps: Long prism with pointed end (rough, striated vertical column)
# ------------------------------------------------------------------------------------------------
UNCUT_TOPAZ_LARGE = [
    '................',
    '.......oo.......',
    '......o20o......',
    '.....o2210o.....',
    '....o231210o....',
    '....o2312100o...',
    '....o2312100o...',
    '....o2312100o...',
    '....o2132100o...',
    '....o2312100o...',
    '....o2312100o...',
    '....o2132100o...',
    '....o231210so...',
    '....o23110sSo...',
    '....o21100sSo...',
    '.....ooooooo....',
]

UNCUT_TOPAZ_MEDIUM = [
    '................',
    '................',
    '.......oo.......',
    '......o20o......',
    '.....o2310o.....',
    '....o231200o....',
    '....o231200o....',
    '....o213200o....',
    '....o231200o....',
    '....o231200o....',
    '....o21120so....',
    '....o2110sSo....',
    '.....o100soo....',
    '......oooo......',
    '................',
    '................',
]

UNCUT_TOPAZ_SMALL = [
    '................',
    '................',
    '................',
    '................',
    '.......oo.......',
    '......o20o......',
    '.....o2310o.....',
    '.....o2310o.....',
    '.....o2130o.....',
    '.....o2310o.....',
    '.....o21s0o.....',
    '......oooo......',
    '................',
    '................',
    '................',
    '................',
]


def render_map(rows, palette):
    im = Image.new('RGBA', (16, 16), T)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                c = palette[ch]
                im.putpixel((x, y), (c[0], c[1], c[2], 255))
    return im


def save(im, name):
    os.makedirs(ROOT, exist_ok=True)
    path = os.path.join(ROOT, name + '.png')
    im.save(path)
    print(f'wrote {name}.png')


def main():
    items = {
        'sapphire_large': (SAPPHIRE_LARGE, SAPPHIRE_CUT),
        'sapphire_medium': (SAPPHIRE_MEDIUM, SAPPHIRE_CUT),
        'sapphire_small': (SAPPHIRE_SMALL, SAPPHIRE_CUT),
        'topaz_large': (TOPAZ_LARGE, TOPAZ_CUT),
        'topaz_medium': (TOPAZ_MEDIUM, TOPAZ_CUT),
        'topaz_small': (TOPAZ_SMALL, TOPAZ_CUT),
        'uncut_sapphire_large': (UNCUT_SAPPHIRE_LARGE, SAPPHIRE_UNCUT),
        'uncut_sapphire_medium': (UNCUT_SAPPHIRE_MEDIUM, SAPPHIRE_UNCUT),
        'uncut_sapphire_small': (UNCUT_SAPPHIRE_SMALL, SAPPHIRE_UNCUT),
        'uncut_topaz_large': (UNCUT_TOPAZ_LARGE, TOPAZ_UNCUT),
        'uncut_topaz_medium': (UNCUT_TOPAZ_MEDIUM, TOPAZ_UNCUT),
        'uncut_topaz_small': (UNCUT_TOPAZ_SMALL, TOPAZ_UNCUT),
    }
    for name, (rows, pal) in items.items():
        im = render_map(rows, pal)
        save(im, name)


if __name__ == '__main__':
    main()
