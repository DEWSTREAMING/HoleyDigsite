"""Phase 23.6 assets: Sand Head, Gravel Head, and Assayer's Pick item textures and models.

Run from project root: python tools/make_phase23_6_assets.py
"""
import json
import os
from PIL import Image

ROOT = os.path.join('src', 'main', 'resources', 'assets', 'holeydigsite')
TEX_I = os.path.join(ROOT, 'textures', 'item')
MOD_I = os.path.join(ROOT, 'models', 'item')


def img():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def save_tex(im, name):
    os.makedirs(TEX_I, exist_ok=True)
    im.save(os.path.join(TEX_I, name + '.png'))


def save_model(name, parent, layer0):
    os.makedirs(MOD_I, exist_ok=True)
    data = {
        "parent": parent,
        "textures": {
            "layer0": layer0
        }
    }
    with open(os.path.join(MOD_I, name + '.json'), 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def make_sand_head():
    im = img()
    # Sandstone / gold head colours
    c_main = (222, 185, 102, 255)
    c_hi = (248, 218, 148, 255)
    c_sh = (172, 130, 62, 255)
    c_dark = (120, 88, 40, 255)
    # Mounting socket in the centre
    sock = (95, 90, 85, 255)
    sock_d = (65, 60, 55, 255)

    # Central collar / socket
    for p in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        im.putpixel(p, sock)
    for p in [(7, 9), (8, 9)]:
        im.putpixel(p, sock_d)

    # Curved pick head arching across the top
    # Left tip pointing down-left
    arch_left = [(2, 11), (3, 10), (3, 9), (4, 8), (4, 7), (5, 6), (6, 5), (7, 5)]
    for p in arch_left:
        im.putpixel(p, c_main)
    # Right tip pointing down-right
    arch_right = [(8, 5), (9, 5), (10, 6), (11, 7), (11, 8), (12, 9), (12, 10), (13, 11)]
    for p in arch_right:
        im.putpixel(p, c_main)

    # Highlights on top edge
    for p in [(6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6)]:
        im.putpixel(p, c_hi)

    # Shadows on inner/lower edges
    for p in [(4, 8), (5, 7), (10, 7), (11, 8), (3, 10), (12, 10), (2, 11), (13, 11)]:
        im.putpixel(p, c_sh)
    for p in [(2, 12), (13, 12)]:
        im.putpixel(p, c_dark)

    # Sand gem / crystal inlaid in socket
    im.putpixel((7, 6), (255, 230, 110, 255))
    im.putpixel((8, 6), (235, 175, 45, 255))

    return im


def make_gravel_head():
    im = img()
    # Flint / riverstone grey-blue colours
    c_main = (145, 155, 168, 255)
    c_hi = (195, 205, 218, 255)
    c_sh = (98, 106, 118, 255)
    c_dark = (68, 74, 84, 255)
    # Mounting socket
    sock = (90, 85, 80, 255)
    sock_d = (60, 55, 52, 255)

    for p in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        im.putpixel(p, sock)
    for p in [(7, 9), (8, 9)]:
        im.putpixel(p, sock_d)

    # Curved pick head
    arch_left = [(2, 11), (3, 10), (3, 9), (4, 8), (4, 7), (5, 6), (6, 5), (7, 5)]
    for p in arch_left:
        im.putpixel(p, c_main)
    arch_right = [(8, 5), (9, 5), (10, 6), (11, 7), (11, 8), (12, 9), (12, 10), (13, 11)]
    for p in arch_right:
        im.putpixel(p, c_main)

    for p in [(6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6)]:
        im.putpixel(p, c_hi)

    for p in [(4, 8), (5, 7), (10, 7), (11, 8), (3, 10), (12, 10), (2, 11), (13, 11)]:
        im.putpixel(p, c_sh)
    for p in [(2, 12), (13, 12)]:
        im.putpixel(p, c_dark)

    # Sapphire / river gem inlaid in socket
    im.putpixel((7, 6), (120, 195, 255, 255))
    im.putpixel((8, 6), (65, 130, 225, 255))

    return im


def make_assayers_pick():
    im = img()
    # Wood handle with metallic winding
    wood = (120, 80, 44, 255)
    wood_d = (84, 52, 28, 255)
    wire = (210, 175, 120, 255)

    for i in range(11):
        x, y = 2 + i, 13 - i
        im.putpixel((x, y), wood if i % 3 != 1 else wire)
        if x + 1 < 16:
            im.putpixel((x + 1, y), wood_d)

    # Head: Orchremia bronze body with Silvane cyan-silver edges
    orch = (195, 95, 55, 255)
    orch_hi = (235, 135, 85, 255)
    orch_d = (135, 55, 30, 255)

    silv = (145, 220, 230, 255)
    silv_hi = (215, 250, 255, 255)
    silv_d = (90, 160, 175, 255)

    # Double curved reinforced head
    arc = [(5, 4), (6, 3), (7, 2), (8, 2), (9, 2), (10, 2), (11, 3), (12, 4), (13, 5), (13, 6), (13, 7),
           (4, 5), (3, 6), (3, 7), (6, 4), (7, 3), (12, 5), (12, 6)]
    for p in arc:
        im.putpixel(p, orch)

    # Silvane reinforced edge and tips
    for p in [(7, 2), (8, 2), (9, 2), (10, 2), (6, 3), (11, 3)]:
        im.putpixel(p, silv_hi)
    for p in [(3, 7), (13, 7), (2, 8), (14, 8)]:
        im.putpixel(p, silv)
    for p in [(3, 8), (13, 8)]:
        im.putpixel(p, silv_d)

    for p in [(4, 5), (12, 6), (6, 4), (7, 3)]:
        im.putpixel(p, orch_d)
    for p in [(8, 3), (9, 3)]:
        im.putpixel(p, orch_hi)

    # Glowing 4-facet assayer prism in the centre
    im.putpixel((8, 3), (255, 255, 210, 255))
    im.putpixel((9, 3), (130, 230, 255, 255))
    im.putpixel((8, 4), (255, 140, 190, 255))
    im.putpixel((9, 4), (210, 150, 255, 255))

    return im


def main():
    save_tex(make_sand_head(), 'sand_head')
    save_model('sand_head', 'minecraft:item/generated', 'holeydigsite:item/sand_head')
    print('Generated sand_head texture and model')

    save_tex(make_gravel_head(), 'gravel_head')
    save_model('gravel_head', 'minecraft:item/generated', 'holeydigsite:item/gravel_head')
    print('Generated gravel_head texture and model')

    save_tex(make_assayers_pick(), 'assayers_pick')
    save_model('assayers_pick', 'minecraft:item/handheld', 'holeydigsite:item/assayers_pick')
    print('Generated assayers_pick texture and model')


if __name__ == '__main__':
    main()
