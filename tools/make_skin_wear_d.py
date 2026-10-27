"""
Generate wear level previews for skins with combined styles A and B.

Usage: python tools/make_skin_wear_d.py [skin ...]

This script reads recoloured textures from src/main/resources/assets/holeydigsite/textures/block/skin/
and generates wear level previews in tools/skin_previews/wear_d_<skin>.png

Style A: scuffs, dust, grime, scratches, rust streaks, corner dirt (from make_skin_wear.py)
Style B: edge effects (edge darkening, chips with inward step, dents) from make_skin_wear_b.py
"""

import os
import sys
import random
import zlib
from PIL import Image, ImageDraw

# Input/output paths
INPUT_DIR = "src/main/resources/assets/holeydigsite/textures/block/skin"
PREVIEW_DIR = "tools/skin_previews"

# Samples to preview
SAMPLES = ['mining_drill_front', 'mining_drill_t2_front', 'mining_drill_t3_front', 'sluice_wood', 'sluice_t2_wood']

# Wear level definitions
WEAR_LEVELS = [
    ("Fresh from the Forge", 0),
    ("Workshop Clean", 1),
    ("Field Worn", 2),
    ("Pit Scarred", 3),
    ("Cave Battered", 4)
]


def lerp(c, target, t):
    """Linear interpolation per channel."""
    return int(c + (target - c) * t)


def lighten(c, f):
    """Lighten channel by factor f."""
    return min(255, int(c * (1 + f)))


def darken(c, f):
    """Darken channel by factor f."""
    return int(c * (1 - f))


def apply_wear(img, level, key):
    """Apply wear level effect to a 16x16 RGBA Pillow image, returning a NEW image."""
    if level == 0:
        # Fresh from the Forge - unchanged
        return img.copy()
    
    img = img.convert('RGBA')
    pixels = list(img.getdata())
    new_pixels = []
    
    # Seed RNG with zlib.crc32 of key:level
    rng = random.Random(zlib.crc32(f'{key}:{level}'.encode()))
    
    for idx, (r, g, b, a) in enumerate(pixels):
        if a == 0:
            # Leave transparent pixels unchanged
            new_pixels.append((r, g, b, a))
            continue
        
        x = idx % 16
        y = idx // 16
        
        if level == 1:
            # Workshop Clean: 2 scuffs (2 horizontally adjacent pixels, lighten 0.12)
            # Pre-determine scuff positions
            scuff_positions = set()
            for _ in range(2):
                sx = rng.randint(0, 14)
                sy = rng.randint(0, 15)
                scuff_positions.add((sx, sy))
                scuff_positions.add((sx + 1, sy) if sx + 1 < 16 else (sx, sy))
            
            if (x, y) in scuff_positions:
                r = lighten(r, 0.12)
                g = lighten(g, 0.12)
                b = lighten(b, 0.12)
        
        elif level == 2:
            # Field Worn: 4 scuffs; dust on rows y>=13 with chance 0.35 lerp 0.20 towards (150,135,110)
            # Pre-determine scuff positions
            scuff_positions = set()
            for _ in range(4):
                sx = rng.randint(0, 14)
                sy = rng.randint(0, 15)
                scuff_positions.add((sx, sy))
                scuff_positions.add((sx + 1, sy) if sx + 1 < 16 else (sx, sy))
            
            if (x, y) in scuff_positions:
                r = lighten(r, 0.12)
                g = lighten(g, 0.12)
                b = lighten(b, 0.12)
            
            # Dust effect
            if y >= 13 and rng.random() < 0.35:
                target_r, target_g, target_b = 150, 135, 110
                r = lerp(r, target_r, 0.20)
                g = lerp(g, target_g, 0.20)
                b = lerp(b, target_b, 0.20)
        
        elif level == 3:
            # Pit Scarred: 6 scratches (3 diagonal pixels), grime on rows y>=12 with chance 0.5 lerp 0.30 towards (85,70,55), 1 rust streak
            # Pre-determine scratch positions
            scratch_positions = set()
            for _ in range(6):
                sx = rng.randint(0, 13)
                sy = rng.randint(0, 13)
                for i in range(3):
                    sx_i = sx + i
                    sy_i = sy + i
                    if sx_i < 16 and sy_i < 16:
                        scratch_positions.add((sx_i, sy_i))
            
            if (x, y) in scratch_positions:
                r = lighten(r, 0.15)
                g = lighten(g, 0.15)
                b = lighten(b, 0.15)
            
            # Grime effect
            if y >= 12 and rng.random() < 0.5:
                target_r, target_g, target_b = 85, 70, 55
                r = lerp(r, target_r, 0.30)
                g = lerp(g, target_g, 0.30)
                b = lerp(b, target_b, 0.30)
            
            # Rust streak: 1 streak starting at (sx, sy) in x=2..13, y=0..6, running down 3-5 pixels
            sx = rng.randint(2, 13)
            sy = rng.randint(0, 6)
            length = rng.randint(3, 5)
            for dy in range(length):
                if sy + dy < 16:
                    if x == sx and y == sy + dy:
                        # Strength decreases by 0.08 per pixel down
                        strength = 0.40 - (dy * 0.08)
                        if strength < 0:
                            strength = 0
                        target_r, target_g, target_b = 125, 62, 30
                        r = lerp(r, target_r, strength)
                        g = lerp(g, target_g, strength)
                        b = lerp(b, target_b, strength)
        
        elif level == 4:
            # Cave Battered: 8 scratches, grime on rows y>=11 with chance 0.6 lerp 0.40, 3 rust streaks of 4-7 pixels, corner dirt
            # Pre-determine scratch positions
            scratch_positions = set()
            for _ in range(8):
                sx = rng.randint(0, 13)
                sy = rng.randint(0, 13)
                for i in range(3):
                    sx_i = sx + i
                    sy_i = sy + i
                    if sx_i < 16 and sy_i < 16:
                        scratch_positions.add((sx_i, sy_i))
            
            if (x, y) in scratch_positions:
                r = lighten(r, 0.15)
                g = lighten(g, 0.15)
                b = lighten(b, 0.15)
            
            # Grime effect
            if y >= 11 and rng.random() < 0.6:
                target_r, target_g, target_b = 85, 70, 55
                r = lerp(r, target_r, 0.40)
                g = lerp(g, target_g, 0.40)
                b = lerp(b, target_b, 0.40)
            
            # Rust streaks: 3 streaks of 4-7 pixels starting at strength 0.55
            for _ in range(3):
                sx = rng.randint(2, 13)
                sy = rng.randint(0, 6)
                length = rng.randint(4, 7)
                for dy in range(length):
                    if sy + dy < 16:
                        if x == sx and y == sy + dy:
                            # Strength decreases by 0.08 per pixel down
                            strength = 0.55 - (dy * 0.08)
                            if strength < 0:
                                strength = 0
                            target_r, target_g, target_b = 125, 62, 30
                            r = lerp(r, target_r, strength)
                            g = lerp(g, target_g, strength)
                            b = lerp(b, target_b, strength)
            
            # Corner dirt: pixels within 1 of a corner lerp 0.45 towards (60,48,36)
            is_corner = (x <= 1 and y <= 1) or \
                       (x >= 14 and y <= 1) or \
                       (x <= 1 and y >= 14) or \
                       (x >= 14 and y >= 14)
            
            if is_corner:
                target_r, target_g, target_b = 60, 48, 36
                r = lerp(r, target_r, 0.45)
                g = lerp(g, target_g, 0.45)
                b = lerp(b, target_b, 0.45)
        
        new_pixels.append((r, g, b, a))
    
    # Create new image with Style A effects
    new_img = Image.new('RGBA', img.size)
    new_img.putdata(new_pixels)
    
    # Apply Style B edge effects with separate RNG
    b_rng = random.Random(zlib.crc32(f'{key}:{level}:edges'.encode()))
    
    # Helper to check if pixel is on edge
    def is_edge(x, y):
        return x in (0, 15) or y in (0, 15)
    
    # Helper to get inner pixel (one step towards center)
    def get_inner_pixel(x, y):
        cx, cy = 8, 8  # Center of 16x16 image
        dx = 0 if x == cx else (1 if x < cx else -1)
        dy = 0 if y == cy else (1 if y < cy else -1)
        return x + dx, y + dy
    
    # Helper to pick random edge pixel
    def pick_edge_pixel():
        edge_pixels = [(x, y) for x in range(16) for y in range(16) if is_edge(x, y)]
        if not edge_pixels:
            return None
        return b_rng.choice(edge_pixels)
    
    # Helper to pick random inner pixel
    def pick_inner_pixel():
        inner_pixels = [(x, y) for x in range(1, 15) for y in range(1, 15)]
        if not inner_pixels:
            return None
        return b_rng.choice(inner_pixels)
    
    # Helper to darken pixel
    def darken_pixel(px, py, dark_factor):
        r, g, b, a = new_img.getpixel((px, py))
        if a > 0:
            new_img.putpixel((px, py), (darken(r, dark_factor), darken(g, dark_factor), darken(b, dark_factor), a))
    
    # Helper to chip pixel (lighten towards worn color)
    def chip_pixel(px, py):
        r, g, b, a = new_img.getpixel((px, py))
        if a > 0:
            new_img.putpixel((px, py), (lerp(r, 195, 0.40), lerp(g, 190, 0.40), lerp(b, 180, 0.40), a))
    
    # Helper to dent (2x2 block darken)
    def dent_pixel(px, py):
        for dx in range(2):
            for dy in range(2):
                if px + dx < 16 and py + dy < 16:
                    r, g, b, a = new_img.getpixel((px + dx, py + dy))
                    if a > 0:
                        new_img.putpixel((px + dx, py + dy), (darken(r, 0.25), darken(g, 0.25), darken(b, 0.25), a))
    
    # Apply Style B effects based on level
    if level == 1:
        # Level 1: 3 chips (half of 6 rounded up = 3)
        for _ in range(3):
            pos = pick_edge_pixel()
            if pos:
                x, y = pos
                chip_pixel(x, y)
    
    elif level == 2:
        # Level 2: 7 chips (half of 14 rounded up = 7)
        for _ in range(7):
            pos = pick_edge_pixel()
            if pos:
                x, y = pos
                chip_pixel(x, y)
    
    elif level == 3:
        # Level 3: darken edges 0.10, 12 chips (half of 24 rounded up = 12)
        # Darken all edge pixels
        for x in range(16):
            for y in range(16):
                if is_edge(x, y):
                    darken_pixel(x, y, 0.10)
        
        # Chips with inward step
        for _ in range(12):
            pos = pick_edge_pixel()
            if pos:
                x, y = pos
                chip_pixel(x, y)
                
                # Inward step with 0.5 chance
                if b_rng.random() < 0.5:
                    ix, iy = get_inner_pixel(x, y)
                    if 0 <= ix < 16 and 0 <= iy < 16:
                        chip_pixel(ix, iy)
    
    elif level == 4:
        # Level 4: darken edges 0.20, 20 chips (half of 40 rounded up = 20), 2 dents
        # Darken all edge pixels
        for x in range(16):
            for y in range(16):
                if is_edge(x, y):
                    darken_pixel(x, y, 0.20)
        
        # Chips with inward step
        for _ in range(20):
            pos = pick_edge_pixel()
            if pos:
                x, y = pos
                chip_pixel(x, y)
                
                # Inward step with 0.5 chance
                if b_rng.random() < 0.5:
                    ix, iy = get_inner_pixel(x, y)
                    if 0 <= ix < 16 and 0 <= iy < 16:
                        chip_pixel(ix, iy)
        
        # Dents: 2x2 block of inner pixels darken 0.25
        for _ in range(2):
            pos = pick_inner_pixel()
            if pos:
                x, y = pos
                if x < 15 and y < 15:
                    dent_pixel(x, y)
    
    return new_img


def process_skin_wear(skin_id):
    """Process wear levels for a skin."""
    skin_dir = os.path.join(INPUT_DIR, skin_id)
    if not os.path.exists(skin_dir):
        return None
    
    # Check which samples exist
    existing_samples = []
    for sample in SAMPLES:
        sample_path = os.path.join(skin_dir, f"{sample}.png")
        if os.path.exists(sample_path):
            existing_samples.append(sample)
    
    if not existing_samples:
        return None
    
    # Create preview sheet
    rows = 5  # Wear levels
    cols = len(existing_samples)
    
    # Each texture is 64x64 after 4x scale (16x16 original)
    texture_size = 64
    gap = 8
    label_width = 140
    header_height = 20
    
    # Calculate sheet size
    sheet_width = label_width + cols * (texture_size + gap)
    sheet_height = header_height + rows * (texture_size + gap)
    
    # Create preview sheet with dark grey background
    preview_img = Image.new('RGBA', (sheet_width, sheet_height), (45, 45, 45, 255))
    draw = ImageDraw.Draw(preview_img)
    
    # Draw header row with skin id
    draw.text((label_width + 2, 2), skin_id, fill=(255, 255, 255, 255))
    
    # Process each sample
    for col_idx, sample in enumerate(existing_samples):
        input_path = os.path.join(skin_dir, f"{sample}.png")
        
        with Image.open(input_path) as base_img:
            base_img = base_img.convert('RGBA')
            
            for row_idx, (wear_name, wear_level) in enumerate(WEAR_LEVELS):
                # Draw label on left
                label_y = header_height + row_idx * (texture_size + gap)
                draw.text((2, label_y + 2), wear_name, fill=(255, 255, 255, 255))
                
                # Apply wear effect
                key = f"{skin_id}:{sample}"
                processed_img = apply_wear(base_img, wear_level, key)
                
                # Scale up 4x with NEAREST
                scaled_img = processed_img.resize((texture_size, texture_size), Image.NEAREST)
                
                # Paste into preview sheet
                paste_x = label_width + col_idx * (texture_size + gap)
                paste_y = header_height + row_idx * (texture_size + gap)
                preview_img.paste(scaled_img, (paste_x, paste_y))
    
    # Save preview
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    preview_path = os.path.join(PREVIEW_DIR, f"wear_d_{skin_id}.png")
    preview_img.save(preview_path)
    
    return preview_path


def main():
    """Main entry point."""
    # Default skins
    skins = sys.argv[1:] if len(sys.argv) > 1 else ["brass", "obsidian", "rustic"]
    
    for skin_id in skins:
        preview_path = process_skin_wear(skin_id)
        if preview_path:
            print(f"Created preview: {preview_path}")
        else:
            print(f"No textures found for skin: {skin_id}")


if __name__ == "__main__":
    main()
