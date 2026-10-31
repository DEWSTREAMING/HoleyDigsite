#!/usr/bin/env python3
"""
Converts a recording (GIF, MP4 or MOV) into a Field Guide frame sheet texture and updates the page's JSON frame count,
or generates the built-in 320x180 still frames and the Field Guide item icon when run with `--stills`.

Usage:
  python tools/make_guide_clips.py --stills
  python tools/make_guide_clips.py <input> <page_id> [--start S] [--end E] [--crop X,Y,W,H] [--fps FPS] [--size WxH]

Sheet layout:
  Frames are packed into a grid (not a single strip) so the texture stays at or below 4096x4096.
  Writes `src/main/resources/assets/holeydigsite/textures/gui/field_guide/<clip>.png` and updates
  `src/main/resources/assets/holeydigsite/field_guide/pages/<page_id>.json` (`frames`, `fps`, `cols`, `frame_w`, `frame_h`).
"""
import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "holeydigsite")
PAGES_DIR = os.path.join(ASSETS, "field_guide", "pages")
CLIPS_DIR = os.path.join(ASSETS, "textures", "gui", "field_guide")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
BLOCK_TEX = os.path.join(ASSETS, "textures", "block")
ITEM_MODELS = os.path.join(ASSETS, "models", "item")

MAX_SHEET = 4096
DEFAULT_W = 320
DEFAULT_H = 180


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def load_tex(folder, name):
    return Image.open(os.path.join(folder, name + ".png")).convert("RGBA")


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))),
            max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))),
            c[3] if len(c) > 3 else 255)


def make_field_guide_item():
    """Draws the 16x16 Field Guide item texture and writes its item model."""
    os.makedirs(ITEM_TEX, exist_ok=True)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cover_dark = (34, 64, 48, 255)
    cover = (52, 98, 72, 255)
    cover_hi = (74, 130, 96, 255)
    spine = (78, 50, 28, 255)
    spine_dark = (52, 32, 18, 255)
    page = (236, 224, 194, 255)
    page_edge = (198, 182, 148, 255)
    brass_dark = (166, 118, 26, 255)
    brass = (224, 178, 52, 255)
    brass_hi = (255, 224, 116, 255)
    tab = (210, 140, 42, 255)

    for y in range(2, 14):
        for x in range(3, 13):
            if x == 3:
                im.putpixel((x, y), spine_dark if y in (2, 13) else spine)
            elif y == 13:
                im.putpixel((x, y), cover_dark)
            elif x == 4 or y == 2:
                im.putpixel((x, y), cover_hi)
            else:
                im.putpixel((x, y), cover)

    for y in range(3, 13):
        im.putpixel((12, y), page if y % 2 == 0 else page_edge)
    for x in range(5, 12):
        im.putpixel((x, 12), page)

    for y in (4, 8, 11):
        im.putpixel((3, y), brass_dark)
        im.putpixel((4, y), brass)

    for p in [(10, 2), (11, 2), (11, 3), (10, 11), (11, 11)]:
        im.putpixel(p, brass)

    # Compass / pick emblem in the middle of the cover
    for x in range(6, 10):
        for y in range(5, 9):
            if x in (6, 9) and y in (5, 8):
                continue
            im.putpixel((x, y), brass_dark)
    im.putpixel((7, 6), brass_hi)
    im.putpixel((8, 6), brass)
    im.putpixel((7, 7), brass)
    im.putpixel((8, 7), brass_hi)

    # Side bookmark tab
    im.putpixel((13, 5), tab)
    im.putpixel((13, 6), tab)

    im.save(os.path.join(ITEM_TEX, "field_guide.png"))
    write_json(os.path.join(ITEM_MODELS, "field_guide.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "holeydigsite:item/field_guide"}
    })


def draw_iso_cube(d, sx, sy, s, top_c, left_c=None, right_c=None, h=1.0):
    if left_c is None:
        left_c = shade(top_c, 0.75)
    if right_c is None:
        right_c = shade(top_c, 0.58)
    hs = int(s * h)
    top = [(sx, sy - hs), (sx + s, sy + s // 2 - hs), (sx, sy + s - hs), (sx - s, sy + s // 2 - hs)]
    left = [(sx - s, sy + s // 2 - hs), (sx, sy + s - hs), (sx, sy + s), (sx - s, sy + s // 2)]
    right = [(sx + s, sy + s // 2 - hs), (sx, sy + s - hs), (sx, sy + s), (sx + s, sy + s // 2)]
    if hs > 0:
        d.polygon(left, fill=left_c)
        d.polygon(right, fill=right_c)
    d.polygon(top, fill=top_c)


def paste_sprite(canvas, sprite, x, y, scale=3):
    if sprite.height > sprite.width:
        sprite = sprite.crop((0, 0, sprite.width, sprite.width))  # the first frame of an animated strip
    w, h = sprite.width * scale, sprite.height * scale
    scaled = sprite.resize((w, h), Image.NEAREST)
    canvas.alpha_composite(scaled, (x, y))


def draw_badge(canvas, x, y, w, h, fill=(26, 20, 14, 230), border=(212, 155, 39, 255)):
    d = ImageDraw.Draw(canvas)
    d.rectangle([x, y, x + w, y + h], fill=fill, outline=border, width=2)


def draw_arrow(d, x0, y0, x1, y1, color=(243, 210, 122, 255), width=3):
    d.line([(x0, y0), (x1, y1)], fill=color, width=width)
    ang = math.atan2(y1 - y0, x1 - x0)
    head = 8
    p1 = (x1 - head * math.cos(ang - 0.5), y1 - head * math.sin(ang - 0.5))
    p2 = (x1 - head * math.cos(ang + 0.5), y1 - head * math.sin(ang + 0.5))
    d.polygon([(x1, y1), p1, p2], fill=color)


def base_scene(sky_top=(40, 52, 68), sky_bot=(24, 30, 38)):
    im = Image.new("RGBA", (DEFAULT_W, DEFAULT_H), (0, 0, 0, 255))
    d = ImageDraw.Draw(im)
    for y in range(DEFAULT_H):
        t = y / max(1, DEFAULT_H - 1)
        c = (
            int(sky_top[0] * (1 - t) + sky_bot[0] * t),
            int(sky_top[1] * (1 - t) + sky_bot[1] * t),
            int(sky_top[2] * (1 - t) + sky_bot[2] * t),
            255,
        )
        d.line([(0, y), (DEFAULT_W - 1, y)], fill=c)
    # Vignette border
    d.rectangle([0, 0, DEFAULT_W - 1, DEFAULT_H - 1], outline=(72, 56, 38, 255), width=2)
    return im


def iso_grid(d, ox, oy, s, cells):
    """cells: dict of (gx, gy, gz) -> (top_c, h)."""
    for (gx, gy, gz) in sorted(cells.keys(), key=lambda k: (k[0] + k[2], k[1])):
        col, h = cells[(gx, gy, gz)]
        sx = ox + (gx - gz) * s
        sy = oy + (gx + gz) * (s // 2) - int(gy * s)
        draw_iso_cube(d, sx, sy, s, col, h=h)


def still_dig_and_pan():
    im = base_scene((52, 78, 108), (28, 40, 56))
    d = ImageDraw.Draw(im)
    s = 18
    ox, oy = 155, 82
    cells = {}
    for x in range(5):
        for z in range(5):
            cells[(x, 0, z)] = ((120, 85, 55), 1.0)
            if 1 <= x <= 3 and 1 <= z <= 3:
                cells[(x, 1, z)] = ((54, 108, 218), 0.85)
            else:
                cells[(x, 1, z)] = ((95, 160, 60) if (x + z) % 2 == 0 else (135, 95, 65), 1.0)
    # Pan sitting in water at (2, 1, 2)
    cells[(2, 2, 2)] = ((216, 172, 48), 0.35)
    iso_grid(d, ox, oy, s, cells)

    # Callout badges showing Shovel -> Pan + Brush -> Finds
    draw_badge(im, 16, 16, 56, 56)
    paste_sprite(im, load_tex(ITEM_TEX, "wooden_shovel_full"), 24, 24, 2)
    draw_arrow(d, 76, 44, 104, 44)
    draw_badge(im, 108, 16, 92, 56)
    paste_sprite(im, load_tex(BLOCK_TEX, "pan"), 116, 24, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "brush"), 158, 24, 2)
    draw_arrow(d, 204, 44, 232, 44)
    draw_badge(im, 236, 16, 68, 56)
    paste_sprite(im, load_tex(BLOCK_TEX, "chunks_folkiron"), 242, 24, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "uncut_ruby_medium"), 268, 24, 2)
    return im


def still_sell():
    im = base_scene((58, 48, 38), (30, 24, 18))
    d = ImageDraw.Draw(im)
    s = 18
    ox, oy = 160, 88
    cells = {}
    for x in range(5):
        for z in range(4):
            cells[(x, 0, z)] = ((115, 85, 50), 1.0)
    # Sell Pad, Shop Button, Buy Pad, Money Pan behind button
    cells[(1, 1, 2)] = ((60, 195, 90), 0.3)
    cells[(2, 1, 2)] = ((215, 55, 55), 0.45)
    cells[(3, 1, 2)] = ((65, 125, 220), 0.3)
    cells[(2, 1, 1)] = ((230, 188, 44), 0.35)
    iso_grid(d, ox, oy, s, cells)

    draw_badge(im, 18, 18, 96, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "chunks_folkiron"), 26, 28, 2)
    paste_sprite(im, load_tex(BLOCK_TEX, "chunks_goldchro"), 54, 28, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "ruby_medium"), 80, 28, 2)
    draw_arrow(d, 118, 45, 152, 45)
    draw_badge(im, 156, 18, 146, 54)
    paste_sprite(im, load_tex(ITEM_TEX, "copper_coin"), 166, 28, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "silver_coin"), 212, 28, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "gold_coin"), 258, 28, 2)
    return im


def still_buy():
    im = base_scene((54, 46, 38), (28, 22, 18))
    d = ImageDraw.Draw(im)
    s = 18
    ox, oy = 160, 88
    cells = {}
    for x in range(5):
        for z in range(4):
            cells[(x, 0, z)] = ((115, 85, 50), 1.0)
    # Shop display shelf + checkout
    cells[(1, 1, 1)] = ((240, 210, 65), 0.75)
    cells[(2, 1, 2)] = ((215, 55, 55), 0.45)
    cells[(3, 1, 2)] = ((65, 125, 220), 0.3)
    cells[(2, 1, 1)] = ((230, 188, 44), 0.35)
    iso_grid(d, ox, oy, s, cells)

    draw_badge(im, 20, 18, 56, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "shop_display_top"), 32, 28, 2)
    draw_arrow(d, 80, 45, 118, 45)
    draw_badge(im, 122, 18, 56, 54)
    paste_sprite(im, load_tex(ITEM_TEX, "blueprint"), 134, 28, 2)
    draw_arrow(d, 182, 45, 220, 45)
    draw_badge(im, 224, 18, 76, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "mining_drill_lower_front"), 232, 28, 2)
    paste_sprite(im, load_tex(BLOCK_TEX, "sluice_wood"), 264, 28, 2)
    return im


def still_first_line():
    im = base_scene((46, 72, 98), (24, 38, 52))
    d = ImageDraw.Draw(im)
    s = 18
    ox, oy = 158, 92
    cells = {}
    for x in range(5):
        for z in range(5):
            cells[(x, 0, z)] = ((120, 85, 55), 1.0)
    # Pool and water intake + pipe
    cells[(1, 1, 3)] = ((54, 108, 218), 0.85)
    cells[(2, 1, 3)] = ((70, 130, 210), 0.75)
    cells[(3, 1, 3)] = ((110, 145, 185), 0.45)
    cells[(3, 1, 2)] = ((110, 145, 185), 0.45)
    # Drill at (2, 1..2, 2) facing (1, 1, 2) sluice
    cells[(2, 1, 2)] = ((210, 125, 42), 1.0)
    cells[(2, 2, 2)] = ((225, 145, 55), 0.85)
    cells[(1, 1, 2)] = ((148, 106, 62), 0.7)
    iso_grid(d, ox, oy, s, cells)

    draw_badge(im, 18, 16, 68, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "water_intake_side_active"), 24, 26, 2)
    paste_sprite(im, load_tex(BLOCK_TEX, "water_pipe_filled"), 52, 26, 2)
    draw_arrow(d, 90, 43, 122, 43)
    draw_badge(im, 126, 16, 56, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "mining_drill_lower_front"), 138, 26, 2)
    draw_arrow(d, 186, 43, 218, 43)
    draw_badge(im, 222, 16, 80, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "sluice_wood"), 230, 26, 2)
    paste_sprite(im, load_tex(BLOCK_TEX, "sluice_dirt"), 264, 26, 2)
    return im


def still_belts():
    im = base_scene((46, 64, 84), (24, 34, 46))
    d = ImageDraw.Draw(im)
    s = 16
    ox, oy = 162, 92
    cells = {}
    for x in range(6):
        for z in range(4):
            cells[(x, 0, z)] = ((120, 85, 55), 1.0)
    # Drill at x=4 -> belts x=3,2 -> sluice at x=1
    cells[(4, 1, 2)] = ((210, 125, 42), 1.0)
    cells[(4, 2, 2)] = ((225, 145, 55), 0.85)
    cells[(3, 1, 2)] = ((82, 84, 92), 0.28)
    cells[(2, 1, 2)] = ((82, 84, 92), 0.28)
    cells[(1, 1, 2)] = ((148, 106, 62), 0.7)
    cells[(3, 1, 3)] = ((70, 130, 210), 0.75)
    iso_grid(d, ox, oy, s, cells)

    draw_badge(im, 20, 16, 56, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "mining_drill_lower_front"), 32, 26, 2)
    draw_arrow(d, 80, 43, 114, 43)
    draw_badge(im, 118, 16, 84, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "clump_belt_top"), 126, 26, 2)
    paste_sprite(im, load_tex(BLOCK_TEX, "sluice_dirt"), 162, 26, 2)
    draw_arrow(d, 206, 43, 240, 43)
    draw_badge(im, 244, 16, 56, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "sluice_wood"), 256, 26, 2)
    return im


def still_metal():
    im = base_scene((72, 42, 32), (34, 20, 16))
    d = ImageDraw.Draw(im)
    s = 18
    ox, oy = 158, 92
    cells = {}
    for x in range(5):
        for z in range(4):
            cells[(x, 0, z)] = ((92, 88, 86), 1.0)
    # Lava source + Lava Intake + Forge + Crucible on top + Mold in front
    cells[(3, 1, 2)] = ((235, 105, 28), 0.85)
    cells[(2, 1, 2)] = ((82, 76, 74), 1.0)
    cells[(2, 2, 2)] = ((188, 102, 56), 0.8)
    cells[(1, 1, 2)] = ((110, 104, 96), 0.4)
    iso_grid(d, ox, oy, s, cells)

    draw_badge(im, 18, 16, 68, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "chunks_folkiron"), 24, 26, 2)
    paste_sprite(im, load_tex(BLOCK_TEX, "chunks_goldchro"), 50, 26, 2)
    draw_arrow(d, 90, 43, 122, 43)
    draw_badge(im, 126, 16, 68, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "forge_front_lit"), 132, 26, 2)
    paste_sprite(im, load_tex(BLOCK_TEX, "crucible_side"), 160, 26, 2)
    draw_arrow(d, 198, 43, 230, 43)
    draw_badge(im, 234, 16, 68, 54)
    paste_sprite(im, load_tex(BLOCK_TEX, "molten_folkiron"), 240, 26, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "smith_sword"), 266, 26, 2)
    return im


def still_land_plots():
    im = base_scene((48, 68, 54), (24, 34, 28))
    d = ImageDraw.Draw(im)
    s = 16
    ox, oy = 160, 96
    cells = {}
    # Three mini plot islands: Deep Dig, Gravel Flats, Sand Dunes
    for x in range(2):
        for z in range(3):
            cells[(x, 0, z)] = ((90, 50, 30), 1.0)
            cells[(x + 2, 0, z)] = ((130, 130, 138), 1.0)
            cells[(x + 4, 0, z)] = ((225, 200, 140), 1.0)
    cells[(0, 1, 1)] = ((245, 232, 192), 0.9)
    cells[(2, 1, 1)] = ((245, 232, 192), 0.9)
    cells[(4, 1, 1)] = ((245, 232, 192), 0.9)
    iso_grid(d, ox, oy, s, cells)

    draw_badge(im, 18, 16, 108, 54)
    paste_sprite(im, load_tex(ITEM_TEX, "land_deed_deep_dig"), 24, 26, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "land_deed_gravel_flats"), 56, 26, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "land_deed_sand_dunes"), 88, 26, 2)
    draw_arrow(d, 130, 43, 168, 43)
    draw_badge(im, 172, 16, 130, 54)
    paste_sprite(im, load_tex(ITEM_TEX, "prospectors_pick"), 180, 26, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "riffle_insert"), 220, 26, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "fine_mesh_screen"), 260, 26, 2)
    return im


def still_ledger():
    im = base_scene((58, 44, 30), (28, 20, 14))
    d = ImageDraw.Draw(im)
    # Draw a stylized Ledger window in the lower half
    wx, wy, ww, wh = 44, 78, 232, 86
    d.rectangle([wx - 2, wy - 2, wx + ww + 2, wy + wh + 2], fill=(20, 15, 10, 255))
    d.rectangle([wx, wy, wx + ww, wy + wh], fill=(59, 46, 34, 255), outline=(212, 155, 39, 255), width=1)
    # Progress bar inside ledger preview
    d.rectangle([wx + 12, wy + 10, wx + ww - 12, wy + 22], fill=(36, 29, 22, 255))
    d.rectangle([wx + 12, wy + 10, wx + 150, wy + 22], fill=(212, 155, 39, 255))
    # Milestone rows inside ledger preview
    for i in range(3):
        ry = wy + 30 + i * 17
        d.rectangle([wx + 12, ry, wx + ww - 12, ry + 14], fill=(33, 46, 32, 255) if i < 2 else (40, 32, 25, 255))
        d.rectangle([wx + 16, ry + 3, wx + 24, ry + 11], fill=(104, 216, 104, 255) if i < 2 else (122, 112, 103, 255))

    draw_badge(im, 24, 14, 56, 54)
    paste_sprite(im, load_tex(ITEM_TEX, "prospectors_ledger"), 36, 24, 2)
    draw_arrow(d, 84, 41, 122, 41)
    draw_badge(im, 126, 14, 170, 54)
    paste_sprite(im, load_tex(ITEM_TEX, "gold_coin"), 136, 24, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "smith_sword"), 176, 24, 2)
    paste_sprite(im, load_tex(BLOCK_TEX, "prospector_crate_front"), 216, 24, 2)
    paste_sprite(im, load_tex(ITEM_TEX, "field_guide"), 256, 24, 2)
    return im


STILL_BUILDERS = {
    "dig_and_pan": still_dig_and_pan,
    "sell": still_sell,
    "buy": still_buy,
    "first_line": still_first_line,
    "belts": still_belts,
    "metal": still_metal,
    "land_plots": still_land_plots,
    "ledger": still_ledger,
}


def update_page_json(page_id, frames, fps, cols, fw, fh):
    path = os.path.join(PAGES_DIR, page_id + ".json")
    if not os.path.isfile(path):
        raise FileNotFoundError(f"Page JSON not found: {path}")
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data["frames"] = frames
    data["fps"] = fps
    data["cols"] = cols
    data["frame_w"] = fw
    data["frame_h"] = fh
    write_json(path, data)
    return data.get("clip", page_id)


def generate_stills():
    make_field_guide_item()
    os.makedirs(CLIPS_DIR, exist_ok=True)
    for page_id, fn in STILL_BUILDERS.items():
        im = fn()
        clip_path = os.path.join(CLIPS_DIR, page_id + ".png")
        im.save(clip_path)
        update_page_json(page_id, 1, 10, 1, DEFAULT_W, DEFAULT_H)
        print(f"wrote still {clip_path} ({DEFAULT_W}x{DEFAULT_H})")


def read_frames_gif(path, start_s, end_s, target_fps):
    im = Image.open(path)
    raw = []
    t = 0.0
    idx = 0
    while True:
        try:
            im.seek(idx)
        except EOFError:
            break
        dur = im.info.get("duration", 100) / 1000.0
        if dur <= 0:
            dur = 0.1
        raw.append((t, im.convert("RGBA").copy()))
        t += dur
        idx += 1
    total_dur = t
    end = total_dur if end_s is None else min(total_dur, end_s)
    start = max(0.0, start_s)
    if end <= start or not raw:
        return [raw[0][1]] if raw else []
    step = 1.0 / target_fps
    out = []
    cur = start
    while cur < end - 1e-6:
        chosen = raw[0][1]
        for rt, frame in raw:
            if rt <= cur + 1e-6:
                chosen = frame
            else:
                break
        out.append(chosen)
        cur += step
    return out


def read_frames_video(path, start_s, end_s, target_fps):
    try:
        import imageio.v3 as iio
    except ImportError as e:
        raise RuntimeError("imageio with ffmpeg is required for MP4/MOV input (pip install 'imageio[ffmpeg]')") from e
    meta = iio.immeta(path, plugin="pyav" if False else None)
    src_fps = float(meta.get("fps", 30.0)) or 30.0
    raw = []
    for i, arr in enumerate(iio.imiter(path)):
        t = i / src_fps
        if end_s is not None and t > end_s + (1.0 / src_fps):
            break
        if t + 1e-6 >= start_s:
            raw.append((t, Image.fromarray(arr).convert("RGBA")))
    if not raw:
        return []
    start = raw[0][0]
    end = raw[-1][0] + (1.0 / src_fps) if end_s is None else end_s
    step = 1.0 / target_fps
    out = []
    cur = start
    while cur <= end + 1e-6:
        chosen = raw[0][1]
        for rt, frame in raw:
            if rt <= cur + 1e-6:
                chosen = frame
            else:
                break
        out.append(chosen)
        cur += step
    return out


def choose_grid(n_frames, fw, fh):
    max_cols = max(1, MAX_SHEET // fw)
    max_rows = max(1, MAX_SHEET // fh)
    max_frames = max_cols * max_rows
    if n_frames > max_frames:
        raise ValueError(f"{n_frames} frames at {fw}x{fh} exceed max sheet {MAX_SHEET}x{MAX_SHEET} (max {max_frames} frames)")
    cols = min(max_cols, max(1, int(math.ceil(math.sqrt(n_frames)))))
    rows = int(math.ceil(n_frames / cols))
    while rows > max_rows and cols < max_cols:
        cols += 1
        rows = int(math.ceil(n_frames / cols))
    return cols, rows


def convert_clip(input_path, page_id, start_s=0.0, end_s=None, crop=None, fps=10, size=(DEFAULT_W, DEFAULT_H)):
    fw, fh = size
    ext = os.path.splitext(input_path)[1].lower()
    if ext == ".gif":
        frames = read_frames_gif(input_path, start_s, end_s, fps)
    elif ext in (".mp4", ".mov", ".mkv", ".webm", ".avi"):
        frames = read_frames_video(input_path, start_s, end_s, fps)
    else:
        im = Image.open(input_path).convert("RGBA")
        frames = [im]

    if not frames:
        raise RuntimeError(f"No frames extracted from {input_path}")

    max_cols = max(1, MAX_SHEET // fw)
    max_rows = max(1, MAX_SHEET // fh)
    max_cap = max_cols * max_rows
    if len(frames) > max_cap:
        frames = frames[:max_cap]

    processed = []
    for fr in frames:
        if crop is not None:
            cx, cy, cw, ch = crop
            fr = fr.crop((cx, cy, cx + cw, cy + ch))
        if fr.size != (fw, fh):
            fr = fr.resize((fw, fh), Image.LANCZOS)
        processed.append(fr)

    cols, rows = choose_grid(len(processed), fw, fh)
    sheet_w, sheet_h = cols * fw, rows * fh
    if sheet_w > MAX_SHEET or sheet_h > MAX_SHEET:
        raise RuntimeError(f"Sheet size {sheet_w}x{sheet_h} exceeds {MAX_SHEET}x{MAX_SHEET}")

    sheet = Image.new("RGBA", (sheet_w, sheet_h), (0, 0, 0, 0))
    for idx, fr in enumerate(processed):
        c = idx % cols
        r = idx // cols
        sheet.paste(fr, (c * fw, r * fh))

    page_json_path = os.path.join(PAGES_DIR, page_id + ".json")
    clip_name = page_id
    if os.path.isfile(page_json_path):
        with open(page_json_path, "r", encoding="utf-8") as f:
            clip_name = json.load(f).get("clip", page_id)
        update_page_json(page_id, len(processed), fps, cols, fw, fh)

    os.makedirs(CLIPS_DIR, exist_ok=True)
    out_path = os.path.join(CLIPS_DIR, clip_name + ".png")
    sheet.save(out_path)
    print(f"wrote {out_path}: {len(processed)} frames ({cols}x{rows} grid, {sheet_w}x{sheet_h} px) at {fps} fps")
    return out_path


def parse_crop(s):
    if not s:
        return None
    parts = [int(x.strip()) for x in s.split(",")]
    if len(parts) != 4:
        raise argparse.ArgumentTypeError("crop must be X,Y,W,H")
    return tuple(parts)


def parse_size(s):
    parts = [int(x.strip()) for x in s.lower().split("x")]
    if len(parts) != 2 or parts[0] <= 0 or parts[1] <= 0:
        raise argparse.ArgumentTypeError("size must be WxH")
    return parts[0], parts[1]


def main():
    parser = argparse.ArgumentParser(description="Build Field Guide frame sheets from GIF/MP4/MOV or generate built-in stills.")
    parser.add_argument("input", nargs="?", help="Input GIF, MP4, MOV or image file")
    parser.add_argument("page", nargs="?", help="Page id (e.g. dig_and_pan, sell, buy, first_line, belts, metal, land_plots, ledger)")
    parser.add_argument("--stills", action="store_true", help="Generate the built-in still frame for each page and the item texture")
    parser.add_argument("--start", type=float, default=0.0, help="Start time in seconds (default: 0)")
    parser.add_argument("--end", type=float, default=None, help="End time in seconds (default: end of clip)")
    parser.add_argument("--crop", type=parse_crop, default=None, help="Crop rectangle X,Y,W,H before resizing")
    parser.add_argument("--fps", type=int, default=10, help="Frames per second (default: 10)")
    parser.add_argument("--size", type=parse_size, default=(DEFAULT_W, DEFAULT_H), help="Frame size WxH (default: 320x180)")
    args = parser.parse_args()

    if args.stills:
        generate_stills()
        return

    if not args.input or not args.page:
        parser.error("Provide <input> and <page>, or use --stills")

    in_arg, page_arg = args.input, args.page
    if not os.path.isfile(in_arg) and os.path.isfile(page_arg):
        in_arg, page_arg = page_arg, in_arg

    convert_clip(in_arg, page_arg, start_s=args.start, end_s=args.end, crop=args.crop, fps=args.fps, size=args.size)


if __name__ == "__main__":
    main()
