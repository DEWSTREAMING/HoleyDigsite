"""
Generate skin textures for mining drills and sluice boxes.

Usage: python tools/make_skins.py

This script reads textures from src/main/resources/assets/holeydigsite/textures/block/
and generates recoloured versions in src/main/resources/assets/holeydigsite/textures/block/skin/<skin>/
(Fresh from the Forge), plus one worn set per wear in skin/<skin>/<wear>/ using wear style D
(apply_wear in make_skin_wear_d.py: grime at the bottom, rust streaks, chipped edges, heavier per level).
"""

import os
import sys
import hashlib
from PIL import Image, ImageEnhance

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_skin_wear_d import apply_wear  # noqa: E402

# Wear ids (crate_rules.json order) -> style D level. Fresh from the Forge is the plain skin folder.
WEARS = [("workshop_clean", 1), ("field_worn", 2), ("pit_scarred", 3), ("cave_battered", 4)]


# Skin definitions with palette functions
SKINS = {
    "rustic": {
        "name": "Rustic",
        "rarity": "common"
    },
    "rustic_weathered": {
        "name": "Rustic Weathered",
        "rarity": "common"
    },
    "brass": {
        "name": "Brass",
        "rarity": "rare"
    },
    "obsidian": {
        "name": "Obsidian",
        "rarity": "epic"
    },
    "frosted": {
        "name": "Frosted",
        "rarity": "rare"
    }
}

# Input/output paths
INPUT_DIR = "src/main/resources/assets/holeydigsite/textures/block"
OUTPUT_DIR = "src/main/resources/assets/holeydigsite/textures/block/skin"
PREVIEW_DIR = "tools/skin_previews"

# Skip patterns for functional textures
SKIP_PATTERNS = ['lamp', 'lantern', 'water', 'dirt', 'insert']


def is_skip_file(filename):
    """Check if file should be skipped based on patterns."""
    for pattern in SKIP_PATTERNS:
        if pattern in filename:
            return True
    return False


def rgb_to_hsl(r, g, b):
    """Convert RGB (0-255) to HSL (h: 0-360, s: 0-1, l: 0-1)."""
    r_norm = r / 255.0
    g_norm = g / 255.0
    b_norm = b / 255.0
    
    max_val = max(r_norm, g_norm, b_norm)
    min_val = min(r_norm, g_norm, b_norm)
    
    # Calculate luminance
    l = (max_val + min_val) / 2.0
    
    # Calculate saturation
    if max_val == min_val:
        s = 0.0
        h = 0.0
    else:
        delta = max_val - min_val
        if l > 0.5:
            s = delta / (2.0 - max_val - min_val)
        else:
            s = delta / (max_val + min_val)
        
        # Calculate hue
        if max_val == r_norm:
            h = (g_norm - b_norm) / delta
            if g_norm > b_norm:
                h = h % 6
            else:
                h = (h + 6) % 6
        elif max_val == g_norm:
            h = 2.0 + (b_norm - r_norm) / delta
        else:
            h = 4.0 + (r_norm - g_norm) / delta
        h = h * 60.0
        if h < 0:
            h = h + 360.0
    
    return h, s, l


def get_luminance(r, g, b):
    """Calculate relative luminance (0-1)."""
    return 0.299 * r + 0.587 * g + 0.114 * b


def apply_rustic_palette(r, g, b, x, y, filename):
    """Apply rustic skin palette to a pixel."""
    h, s, l = rgb_to_hsl(r, g, b)
    
    # Classify pixel
    is_near_white = (max(r, g, b) - min(r, g, b) <= 25) and l > 0.75
    # Wood: brown/orange range (15-45 degrees) with saturation > 0.25
    is_wood = 15 <= h <= 45 and s > 0.25 and not is_near_white
    # Metal: saturation < 0.2 (greys) or near-white
    is_metal = s < 0.2 or is_near_white
    
    if is_wood:
        # Wood -> desaturate by 40% and shift towards grey-brown
        # Multiply RGB by (0.85, 0.80, 0.72) after desaturating
        # Desaturate by 40% means keep 60% of saturation
        new_s = s * 0.6
        # Convert back to RGB with reduced saturation
        # Simple approach: blend towards grey
        grey = (r + g + b) / 3.0
        r = grey + (r - grey) * 0.6
        g = grey + (g - grey) * 0.6
        b = grey + (b - grey) * 0.6
        
        # Apply brownish tint
        r = r * 0.85
        g = g * 0.80
        b = b * 0.72
        
    elif is_metal:
        # Metal -> rust: map luminance L to rust colour
        luminance = get_luminance(r, g, b) / 255.0
        
        # Lerp between (70,35,20) at L=0 and (196,110,60) at L=1
        rust_low = (70, 35, 20)
        rust_high = (196, 110, 60)
        
        r = rust_low[0] + (rust_high[0] - rust_low[0]) * luminance
        g = rust_low[1] + (rust_high[1] - rust_low[1]) * luminance
        b = rust_low[2] + (rust_high[2] - rust_low[2]) * luminance
        
        # Determine if this pixel should be pitted (darker)
        # Use hash of x,y,file name for deterministic pitting
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_pitted = (hash_val % 100) < 12  # ~12% of pixels
        
        if is_pitted:
            # Darken by 25%
            r = r * 0.75
            g = g * 0.75
            b = b * 0.75
        
    # Else leave unchanged
    
    return int(r), int(g), int(b)


def apply_rustic_weathered_palette(r, g, b, x, y, filename):
    """Apply rustic weathered skin palette to a pixel."""
    h, s, l = rgb_to_hsl(r, g, b)
    
    # Classify pixel
    is_near_white = (max(r, g, b) - min(r, g, b) <= 25) and l > 0.75
    # Wood: brown/orange range (15-45 degrees) with saturation > 0.25
    is_wood = 15 <= h <= 45 and s > 0.25 and not is_near_white
    # Metal: saturation < 0.2 (greys)
    is_metal = s < 0.2 and not is_near_white
    # Gold trim: hue 45-65 degrees and saturation > 0.3
    is_gold_trim = 45 <= h <= 65 and s > 0.3 and not is_near_white
    
    if is_wood:
        # Wood -> weathered: desaturate 70%, multiply RGB by (0.78, 0.76, 0.72), lighten 8%
        new_s = s * 0.3  # 70% desaturation
        grey = (r + g + b) / 3.0
        r = grey + (r - grey) * 0.3
        g = grey + (g - grey) * 0.3
        b = grey + (b - grey) * 0.3
        
        # Apply weathered tint
        r = r * 0.78
        g = g * 0.76
        b = b * 0.72
        
        # Lighten by 8%
        r = r * 1.08
        g = g * 1.08
        b = b * 1.08
        
    elif is_metal:
        # Metal -> rust: map luminance L to rust colour
        luminance = get_luminance(r, g, b) / 255.0
        
        # Lerp between (70,35,20) at L=0 and (196,110,60) at L=1
        rust_low = (70, 35, 20)
        rust_high = (196, 110, 60)
        
        r = rust_low[0] + (rust_high[0] - rust_low[0]) * luminance
        g = rust_low[1] + (rust_high[1] - rust_low[1]) * luminance
        b = rust_low[2] + (rust_high[2] - rust_low[2]) * luminance
        
        # Determine if this pixel should be pitted (darker)
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_pitted = (hash_val % 100) < 12  # ~12% of pixels
        
        if is_pitted:
            # Darken by 25%
            r = r * 0.75
            g = g * 0.75
            b = b * 0.75
        
    elif is_gold_trim:
        # Gold trim -> rusted: map luminance L to rust colour
        luminance = get_luminance(r, g, b) / 255.0
        
        # Lerp between (70,35,20) at L=0 and (196,110,60) at L=1
        rust_low = (70, 35, 20)
        rust_high = (196, 110, 60)
        
        r = rust_low[0] + (rust_high[0] - rust_low[0]) * luminance
        g = rust_low[1] + (rust_high[1] - rust_low[1]) * luminance
        b = rust_low[2] + (rust_high[2] - rust_low[2]) * luminance
        
        # Determine if this pixel should be pitted (darker)
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_pitted = (hash_val % 100) < 12  # ~12% of pixels
        
        if is_pitted:
            # Darken by 25%
            r = r * 0.75
            g = g * 0.75
            b = b * 0.75
        
    elif is_near_white:
        # Near-white -> pale dusty tan scaled by luminance
        luminance = get_luminance(r, g, b) / 255.0
        tan_r, tan_g, tan_b = 205, 190, 165
        
        r = tan_r * luminance
        g = tan_g * luminance
        b = tan_b * luminance
    
    # Else leave unchanged
    
    return int(r), int(g), int(b)


def apply_brass_palette(r, g, b, x, y, filename):
    """Apply brass skin palette to a pixel."""
    h, s, l = rgb_to_hsl(r, g, b)
    
    # Classify pixel
    is_near_white = (max(r, g, b) - min(r, g, b) <= 25) and l > 0.75
    # Wood: brown/orange range (15-45 degrees) with saturation > 0.25
    is_wood = 15 <= h <= 45 and s > 0.25 and not is_near_white
    # Metal: saturation < 0.2 (greys) or near-white
    is_metal = s < 0.2 or is_near_white
    # Gold trim: hue 45-65 degrees and saturation > 0.3
    is_gold_trim = 45 <= h <= 65 and s > 0.3 and not is_near_white
    
    if is_wood:
        # Wood -> dark walnut: multiply RGB by (0.62, 0.50, 0.42)
        r = r * 0.62
        g = g * 0.50
        b = b * 0.42
        
    elif is_metal:
        # Metal -> polished brass: map luminance L to brass colour
        luminance = get_luminance(r, g, b) / 255.0
        
        # Check if this is one of the brightest 10% of metal pixels
        # Use hash of x,y,file name for deterministic highlight assignment
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_highlight = (hash_val % 100) < 10  # ~10% of pixels
        
        if is_highlight and luminance > 0.85:
            # Push to brightest highlight colour
            r, g, b = 255, 235, 160
        else:
            # Lerp between (90,60,20) at L=0 and (235,195,95) at L=1
            brass_low = (90, 60, 20)
            brass_high = (235, 195, 95)
            
            r = brass_low[0] + (brass_high[0] - brass_low[0]) * luminance
            g = brass_low[1] + (brass_high[1] - brass_low[1]) * luminance
            b = brass_low[2] + (brass_high[2] - brass_low[2]) * luminance
        
    # Gold trim stays unchanged (leave as-is)
    
    return int(r), int(g), int(b)


def apply_obsidian_palette(r, g, b, x, y, filename):
    """Apply obsidian skin palette to a pixel."""
    h, s, l = rgb_to_hsl(r, g, b)
    
    # Classify pixel
    is_near_white = (max(r, g, b) - min(r, g, b) <= 25) and l > 0.75
    # Wood: brown/orange range (15-45 degrees) with saturation > 0.25
    is_wood = 15 <= h <= 45 and s > 0.25 and not is_near_white
    # Metal: saturation < 0.2 (greys) or near-white
    is_metal = s < 0.2 or is_near_white
    # Gold trim: hue 45-65 degrees and saturation > 0.3
    is_gold_trim = 45 <= h <= 65 and s > 0.3 and not is_near_white
    
    if is_wood:
        # Wood -> very dark grey-purple: desaturate 80% then multiply by (0.35, 0.30, 0.42)
        # Desaturate by 80% means keep 20% of saturation
        new_s = s * 0.2
        # Convert back to RGB with reduced saturation
        # Simple approach: blend towards grey
        grey = (r + g + b) / 3.0
        r = grey + (r - grey) * 0.2
        g = grey + (g - grey) * 0.2
        b = grey + (b - grey) * 0.2
        
        # Apply dark grey-purple tint
        r = r * 0.35
        g = g * 0.30
        b = b * 0.42
        
    elif is_metal:
        # Metal -> near-black purple: map luminance L to purple colour
        luminance = get_luminance(r, g, b) / 255.0
        
        # Lerp between (12,8,20) at L=0 and (70,55,95) at L=1
        purple_low = (12, 8, 20)
        purple_high = (70, 55, 95)
        
        r = purple_low[0] + (purple_high[0] - purple_low[0]) * luminance
        g = purple_low[1] + (purple_high[1] - purple_low[1]) * luminance
        b = purple_low[2] + (purple_high[2] - purple_low[2]) * luminance
        
    elif is_gold_trim:
        # Gold trim -> glowing violet: scale by luminance
        luminance = get_luminance(r, g, b) / 255.0
        r = 170 * luminance
        g = 90 * luminance
        b = 255 * luminance
        
    # Else leave unchanged
    
    return int(r), int(g), int(b)


def apply_frosted_palette(r, g, b, x, y, filename):
    """Apply frosted skin palette to a pixel."""
    h, s, l = rgb_to_hsl(r, g, b)
    
    # Classify pixel
    is_near_white = (max(r, g, b) - min(r, g, b) <= 25) and l > 0.75
    # Wood: brown/orange range (15-45 degrees) with saturation > 0.25
    is_wood = 15 <= h <= 45 and s > 0.25 and not is_near_white
    # Metal: saturation < 0.2 (greys) or near-white
    is_metal = s < 0.2 or is_near_white
    # Gold trim: hue 45-65 degrees and saturation > 0.3
    is_gold_trim = 45 <= h <= 65 and s > 0.3 and not is_near_white
    
    if is_wood:
        # Wood -> white-washed: desaturate 60% then lerp 45% towards (230,235,240)
        # Desaturate by 60% means keep 40% of saturation
        new_s = s * 0.4
        # Convert back to RGB with reduced saturation
        grey = (r + g + b) / 3.0
        r = grey + (r - grey) * 0.4
        g = grey + (g - grey) * 0.4
        b = grey + (b - grey) * 0.4
        
        # Lerp 45% towards (230,235,240)
        target_r, target_g, target_b = 230, 235, 240
        r = r + (target_r - r) * 0.45
        g = g + (target_g - g) * 0.45
        b = b + (target_b - b) * 0.45
        
    elif is_metal:
        # Metal -> icy pale blue: map luminance L to blue colour
        luminance = get_luminance(r, g, b) / 255.0
        
        # Lerp between (70,95,125) at L=0 and (225,240,255) at L=1
        blue_low = (70, 95, 125)
        blue_high = (225, 240, 255)
        
        r = blue_low[0] + (blue_high[0] - blue_low[0]) * luminance
        g = blue_low[1] + (blue_high[1] - blue_low[1]) * luminance
        b = blue_low[2] + (blue_high[2] - blue_low[2]) * luminance
        
    elif is_gold_trim:
        # Gold trim -> silver: lerp from (110,115,125) to (235,238,245) by luminance
        luminance = get_luminance(r, g, b) / 255.0
        
        silver_low = (110, 115, 125)
        silver_high = (235, 238, 245)
        
        r = silver_low[0] + (silver_high[0] - silver_low[0]) * luminance
        g = silver_low[1] + (silver_high[1] - silver_low[1]) * luminance
        b = silver_low[2] + (silver_high[2] - silver_low[2]) * luminance
        
    # Else leave unchanged
    
    return int(r), int(g), int(b)


def process_skin(skin_id, palette_func):
    """Process all textures for a skin."""
    output_skin_dir = os.path.join(OUTPUT_DIR, skin_id)
    os.makedirs(output_skin_dir, exist_ok=True)
    
    # Get list of input files
    input_files = []
    if os.path.exists(INPUT_DIR):
        for filename in os.listdir(INPUT_DIR):
            if (filename.endswith('.png') and 
                (filename.startswith('mining_drill') or filename.startswith('sluice')) and
                not is_skip_file(filename)):
                input_files.append(filename)
    
    input_files.sort()
    
    written_count = 0
    
    for filename in input_files:
        input_path = os.path.join(INPUT_DIR, filename)
        output_path = os.path.join(output_skin_dir, filename)
        
        with Image.open(input_path) as img:
            img = img.convert('RGBA')
            pixels = img.load()
            
            new_pixels = []
            for y in range(img.height):
                row = []
                for x in range(img.width):
                    r, g, b, a = pixels[x, y]
                    new_r, new_g, new_b = palette_func(r, g, b, x, y, filename)
                    row.append((new_r, new_g, new_b, a))
                new_pixels.append(row)
            
            # Create new image
            new_img = Image.new('RGBA', img.size)
            new_pixels_flat = []
            for row in new_pixels:
                new_pixels_flat.extend(row)
            new_img.putdata(new_pixels_flat)
            
            # Save
            new_img.save(output_path)
            written_count += 1
        
        # Copy .mcmeta if exists
        mcmeta_path = input_path + '.mcmeta'
        if os.path.exists(mcmeta_path):
            with open(mcmeta_path, 'rb') as f:
                content = f.read()
            with open(output_path + '.mcmeta', 'wb') as f:
                f.write(content)
    
    return written_count, input_files


def write_wears(skin_id, input_files):
    """Writes skin/<skin>/<wear>/<texture> for each worn wear, tile by tile so animated strips keep their frames."""
    count = 0
    for wear_id, level in WEARS:
        out_dir = os.path.join(OUTPUT_DIR, skin_id, wear_id)
        os.makedirs(out_dir, exist_ok=True)
        for filename in input_files:
            src = os.path.join(OUTPUT_DIR, skin_id, filename)
            if not os.path.exists(src):
                continue
            with Image.open(src) as img:
                img = img.convert('RGBA')
                out = Image.new('RGBA', img.size)
                key = f"{skin_id}:{filename[:-4]}"
                for ty in range(0, img.height, 16):
                    for tx in range(0, img.width, 16):
                        tile = img.crop((tx, ty, tx + 16, ty + 16))
                        out.paste(apply_wear(tile, level, key), (tx, ty))
                out.save(os.path.join(out_dir, filename))
                count += 1
            meta = src + '.mcmeta'
            if os.path.exists(meta):
                with open(meta, 'rb') as f:
                    content = f.read()
                with open(os.path.join(out_dir, filename) + '.mcmeta', 'wb') as f:
                    f.write(content)
    return count


def create_preview(skin_id, input_files):
    """Create a preview sheet with original and recoloured textures side by side."""
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    
    if not input_files:
        return
    
    # Load images and prepare preview
    preview_pairs = []
    for filename in input_files:
        input_path = os.path.join(INPUT_DIR, filename)
        skin_path = os.path.join(OUTPUT_DIR, skin_id, filename)
        
        if os.path.exists(skin_path):
            with Image.open(input_path) as orig:
                with Image.open(skin_path) as recoloured:
                    # Scale up 4x with NEAREST
                    orig_large = orig.resize((orig.width * 4, orig.height * 4), Image.NEAREST)
                    recoloured_large = recoloured.resize((recoloured.width * 4, recoloured.height * 4), Image.NEAREST)
                    preview_pairs.append((orig_large, recoloured_large))
    
    if not preview_pairs:
        return
    
    # Calculate preview sheet size
    pair_width = preview_pairs[0][0].width + preview_pairs[0][1].width
    pair_height = max(preview_pairs[0][0].height, preview_pairs[0][1].height)
    
    # 8 pairs per row
    pairs_per_row = 8
    rows = (len(preview_pairs) + pairs_per_row - 1) // pairs_per_row
    
    sheet_width = pair_width * pairs_per_row
    sheet_height = pair_height * rows
    
    # Create dark grey background
    preview_img = Image.new('RGBA', (sheet_width, sheet_height), (50, 50, 50, 255))
    
    for idx, (orig, recoloured) in enumerate(preview_pairs):
        row = idx // pairs_per_row
        col = idx % pairs_per_row
        
        x_offset = col * pair_width
        y_offset = row * pair_height
        
        # Paste original on left
        preview_img.paste(orig, (x_offset, y_offset))
        # Paste recoloured on right
        preview_img.paste(recoloured, (x_offset + orig.width, y_offset))
    
    # Save preview
    preview_path = os.path.join(PREVIEW_DIR, f"{skin_id}.png")
    preview_img.save(preview_path)


def main():
    """Main entry point."""
    # Process all skins
    for skin_id in SKINS.keys():
        # Process the skin
        if skin_id == "rustic":
            palette_func = apply_rustic_palette
        elif skin_id == "rustic_weathered":
            palette_func = apply_rustic_weathered_palette
        elif skin_id == "brass":
            palette_func = apply_brass_palette
        elif skin_id == "obsidian":
            palette_func = apply_obsidian_palette
        elif skin_id == "frosted":
            palette_func = apply_frosted_palette
        else:
            palette_func = apply_rustic_palette
        
        written_count, input_files = process_skin(skin_id, palette_func)
        written_count += write_wears(skin_id, input_files)
        
        # Create preview sheet
        create_preview(skin_id, input_files)
        
        print(f"Written {written_count} textures for skin '{skin_id}'")


if __name__ == "__main__":
    main()
