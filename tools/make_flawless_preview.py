import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/holeydigsite/textures/item")
PREVIEW_DIR = os.path.join(ROOT, "docs/preview/quick1")
os.makedirs(PREVIEW_DIR, exist_ok=True)

def apply_glint(base_img):
    # Minecraft enchant glint: magenta/purple shimmer diagonally across item with screen/add blend
    w, h = base_img.size
    glint = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glint)
    # diagonal shimmer stripes in purple/magenta
    for offset in range(-w, w * 2, 6):
        gd.line([(offset, 0), (offset + h, h)], fill=(160, 60, 240, 110), width=3)
        gd.line([(offset + 2, 0), (offset + h + 2, h)], fill=(220, 140, 255, 140), width=2)
    
    # Composite glint only where base_img is non-transparent
    r, g, b, a = base_img.split()
    glint_out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x in range(w):
        for y in range(h):
            base_p = base_img.getpixel((x, y))
            if base_p[3] > 10:
                gp = glint.getpixel((x, y))
                # blend
                nr = min(255, base_p[0] + gp[0] * gp[3] // 300)
                ng = min(255, base_p[1] + gp[1] * gp[3] // 380)
                nb = min(255, base_p[2] + gp[2] * gp[3] // 250)
                glint_out.putpixel((x, y), (nr, ng, nb, base_p[3]))
    return glint_out

def render_item(name, flawless=False, scale=6):
    img_path = os.path.join(TEX_DIR, f"{name}.png")
    im = Image.open(img_path).convert("RGBA")
    if flawless:
        im = apply_glint(im)
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)

def build_preview():
    items = [
        ("ruby_large", "Ruby", "Flawless Ruby", "64c", "768c (12x)"),
        ("uncut_ruby_large", "Uncut Ruby", "Flawless Uncut Ruby", "32c", "384c (12x)"),
        ("sapphire_large", "Sapphire", "Flawless Sapphire", "96c", "1152c (12x)"),
    ]
    
    card_w = 260
    card_h = 240
    sheet_w = card_w * len(items) + 40
    sheet_h = card_h + 80
    
    sheet = Image.new("RGBA", (sheet_w, sheet_h), (35, 38, 44, 255))
    d = ImageDraw.Draw(sheet)
    
    # Title
    d.text((20, 16), "Quick 1: Flawless Gems Preview", fill=(240, 240, 240, 255))
    d.text((20, 36), "Jackpot finds with enchant glint, gold naming, and 12x sell value", fill=(170, 175, 185, 255))
    
    scale = 5
    for idx, (tex_id, normal_label, flawless_label, norm_val, flaw_val) in enumerate(items):
        cx = 20 + idx * card_w
        cy = 65
        
        # Card background
        d.rounded_rectangle([cx, cy, cx + card_w - 15, cy + card_h], radius=8, fill=(45, 49, 56, 255), outline=(60, 65, 75, 255))
        
        # Normal
        norm_img = render_item(tex_id, flawless=False, scale=scale)
        sheet.alpha_composite(norm_img, (cx + 15, cy + 20))
        d.text((cx + 15, cy + 105), normal_label, fill=(220, 220, 220, 255))
        d.text((cx + 15, cy + 125), f"Sells for {norm_val}", fill=(160, 160, 160, 255))
        
        # Flawless
        flaw_img = render_item(tex_id, flawless=True, scale=scale)
        sheet.alpha_composite(flaw_img, (cx + 130, cy + 20))
        d.text((cx + 120, cy + 105), flawless_label, fill=(255, 215, 0, 255))
        d.text((cx + 120, cy + 125), f"Sells for {flaw_val}", fill=(255, 215, 0, 255))
        
        # Divider / note
        d.line([(cx + 10, cy + 155), (cx + card_w - 25, cy + 155)], fill=(60, 65, 75, 255))
        d.text((cx + 15, cy + 165), "• Enchant glint override", fill=(180, 180, 180, 255))
        d.text((cx + 15, cy + 185), "• Gold tooltip name", fill=(180, 180, 180, 255))
        d.text((cx + 15, cy + 205), "• Preserved through Gem Cutter", fill=(180, 180, 180, 255))

    out_path = os.path.join(PREVIEW_DIR, "flawless_gem.png")
    sheet.convert("RGB").save(out_path)
    print("Saved", out_path)

if __name__ == "__main__":
    build_preview()
