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
import json
import math
from PIL import Image, ImageEnhance

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_skin_wear_d import apply_wear  # noqa: E402

# Wear ids (crate_rules.json order) -> style D level. Fresh from the Forge is the plain skin folder.
WEARS = [("workshop_clean", 1), ("field_worn", 2), ("pit_scarred", 3), ("cave_battered", 4)]


# Skin definitions: id -> display name and rarity (the data files and lang lines are written by make_phase31_assets.py).
SKINS = {
    "rustic": {"name": "Rustic", "rarity": "common"},
    "rustic_weathered": {"name": "Rustic Weathered", "rarity": "common"},
    "brass": {"name": "Brass", "rarity": "rare"},
    "obsidian": {"name": "Obsidian", "rarity": "epic"},
    "frosted": {"name": "Frosted", "rarity": "rare"},
    "outback": {"name": "Outback", "rarity": "common"},
    "outback_dusty": {"name": "Dusty Outback", "rarity": "common"},
    "outback_ochre": {"name": "Ochre", "rarity": "rare"},
    "opal_calm": {"name": "Calm Opal", "rarity": "rare"},
    "opal": {"name": "Opal", "rarity": "epic"},
    "opal_boulder": {"name": "Boulder Opal", "rarity": "epic"},
    "black_opal": {"name": "Black Opal", "rarity": "legendary", "animated": True},
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


def apply_opal_boulder_palette(r, g, b, x, y, filename):
    """Apply opal boulder skin palette to a pixel."""
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
        # Wood -> multiply r, g, b by (0.55, 0.40, 0.30)
        r = r * 0.55
        g = g * 0.40
        b = b * 0.30
        
    elif is_metal:
        # Metal -> lerp luminance from (55, 32, 20) to (140, 90, 60)
        luminance = get_luminance(r, g, b) / 255.0
        
        # Check if this is one of the flecks (10% of pixels)
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_fleck = (hash_val % 100) < 10
        
        if is_fleck:
            # Pick fleck colour by hash_val // 100 % 3
            fleck_index = (hash_val // 100) % 3
            flecks = [(60, 205, 140), (70, 150, 245), (40, 110, 220)]
            r, g, b = flecks[fleck_index]
        else:
            # Lerp from (55, 32, 20) to (140, 90, 60)
            ironstone_low = (55, 32, 20)
            ironstone_high = (140, 90, 60)
            
            r = ironstone_low[0] + (ironstone_high[0] - ironstone_low[0]) * luminance
            g = ironstone_low[1] + (ironstone_high[1] - ironstone_low[1]) * luminance
            b = ironstone_low[2] + (ironstone_high[2] - ironstone_low[2]) * luminance
    
    # Gold trim stays unchanged (leave as-is)
    
    return int(r), int(g), int(b)


def apply_outback_dusty_palette(r, g, b, x, y, filename):
    """Apply outback dusty skin palette to a pixel."""
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
        # Wood -> lerp luminance from (110, 70, 50) to (200, 150, 115)
        luminance = get_luminance(r, g, b) / 255.0
        
        wood_low = (110, 70, 50)
        wood_high = (200, 150, 115)
        
        r = wood_low[0] + (wood_high[0] - wood_low[0]) * luminance
        g = wood_low[1] + (wood_high[1] - wood_low[1]) * luminance
        b = wood_low[2] + (wood_high[2] - wood_low[2]) * luminance
        
    elif is_metal:
        # Metal -> lerp luminance from (120, 60, 38) to (215, 140, 100)
        luminance = get_luminance(r, g, b) / 255.0
        
        # Check if this is one of the red-dust pixels (22% of pixels)
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_red_dust = (hash_val % 100) < 22
        
        if is_red_dust:
            # Use red dust colour
            r, g, b = 165, 72, 40
        else:
            # Lerp from (120, 60, 38) to (215, 140, 100)
            steel_low = (120, 60, 38)
            steel_high = (215, 140, 100)
            
            r = steel_low[0] + (steel_high[0] - steel_low[0]) * luminance
            g = steel_low[1] + (steel_high[1] - steel_low[1]) * luminance
            b = steel_low[2] + (steel_high[2] - steel_low[2]) * luminance
        
    # Gold trim stays unchanged (leave as-is)
    
    return int(r), int(g), int(b)


def apply_outback_palette(r, g, b, x, y, filename):
    """Apply outback skin palette to a pixel."""
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
        # Wood -> sun-bleached grey wood: lerp from (95, 88, 78) at L=0 to (205, 196, 180) at L=1
        luminance = get_luminance(r, g, b) / 255.0
        
        wood_low = (95, 88, 78)
        wood_high = (205, 196, 180)
        
        r = wood_low[0] + (wood_high[0] - wood_low[0]) * luminance
        g = wood_low[1] + (wood_high[1] - wood_low[1]) * luminance
        b = wood_low[2] + (wood_high[2] - wood_low[2]) * luminance
        
    elif is_metal:
        # Metal -> red-dust steel: map luminance L to steel colour
        luminance = get_luminance(r, g, b) / 255.0
        
        # Check if this is one of the red-dust pixels (10% of pixels)
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_red_dust = (hash_val % 100) < 10
        
        if is_red_dust:
            # Use red dust colour
            r, g, b = 175, 80, 45
        else:
            # Lerp from (110, 55, 35) at L=0 to (225, 165, 125) at L=1
            steel_low = (110, 55, 35)
            steel_high = (225, 165, 125)
            
            r = steel_low[0] + (steel_high[0] - steel_low[0]) * luminance
            g = steel_low[1] + (steel_high[1] - steel_low[1]) * luminance
            b = steel_low[2] + (steel_high[2] - steel_low[2]) * luminance
        
    # Gold trim stays unchanged (leave as-is)
    
    return int(r), int(g), int(b)


def apply_outback_ochre_palette(r, g, b, x, y, filename):
    """Apply outback ochre skin palette to a pixel."""
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
        # Wood -> ochre pigment, mustard yellow in the lights to a deep golden brown
        luminance = get_luminance(r, g, b) / 255.0
        
        wood_low = (92, 58, 14)
        wood_high = (240, 190, 66)
        
        r = wood_low[0] + (wood_high[0] - wood_low[0]) * luminance
        g = wood_low[1] + (wood_high[1] - wood_low[1]) * luminance
        b = wood_low[2] + (wood_high[2] - wood_low[2]) * luminance
        
    elif is_metal:
        # Metal -> burnt orange, with darker red-dirt pixels
        luminance = get_luminance(r, g, b) / 255.0
        
        # Check if this is one of the yellow ochre pixels (8% of pixels)
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_ochre = (hash_val % 100) < 12
        
        if is_ochre:
            # red-dirt accent
            r, g, b = 112, 40, 20
        else:
            # burnt orange
            ochre_low = (120, 52, 18)
            ochre_high = (222, 118, 36)
            
            r = ochre_low[0] + (ochre_high[0] - ochre_low[0]) * luminance
            g = ochre_low[1] + (ochre_high[1] - ochre_low[1]) * luminance
            b = ochre_low[2] + (ochre_high[2] - ochre_low[2]) * luminance
    
    # Gold trim stays unchanged (leave as-is)
    
    return int(r), int(g), int(b)


def apply_opal_calm_palette(r, g, b, x, y, filename):
    """Apply opal calm skin palette to a pixel."""
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
        # Wood -> dark charcoal: multiply r, g, b by (0.42, 0.42, 0.48)
        r = r * 0.42
        g = g * 0.42
        b = b * 0.48
        
    elif is_metal:
        # Metal -> black opal: map luminance L to opal colour
        luminance = get_luminance(r, g, b) / 255.0
        
        # Check if this is one of the play-of-colour flecks (6% of pixels)
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_fleck = (hash_val % 100) < 6
        
        if is_fleck and luminance > 0.55:
            # Pick fleck colour by hash_val // 100 % 3
            fleck_index = (hash_val // 100) % 3
            flecks = [(60, 205, 120), (70, 140, 245), (235, 70, 95)]
            r, g, b = flecks[fleck_index]
        else:
            # Lerp from (18, 22, 40) at L=0 to (70, 80, 125) at L=1
            opal_low = (18, 22, 40)
            opal_high = (70, 80, 125)
            
            r = opal_low[0] + (opal_high[0] - opal_low[0]) * luminance
            g = opal_low[1] + (opal_high[1] - opal_low[1]) * luminance
            b = opal_low[2] + (opal_high[2] - opal_low[2]) * luminance
    
    # Gold trim stays unchanged (leave as-is)
    
    return int(r), int(g), int(b)


def apply_opal_palette(r, g, b, x, y, filename):
    """Apply opal skin palette to a pixel."""
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
        # Wood -> dark charcoal: multiply r, g, b by (0.42, 0.42, 0.48)
        r = r * 0.42
        g = g * 0.42
        b = b * 0.48
        
    elif is_metal:
        # Metal -> black opal: map luminance L to opal colour
        luminance = get_luminance(r, g, b) / 255.0
        
        # Check if this is one of the play-of-colour flecks (12% of pixels)
        hash_input = f"{x},{y},{filename}"
        hash_val = int(hashlib.md5(hash_input.encode()).hexdigest(), 16)
        is_fleck = (hash_val % 100) < 12
        
        if is_fleck:
            # Pick fleck colour by hash_val // 100 % 4
            fleck_index = (hash_val // 100) % 4
            flecks = [(60, 205, 120), (70, 140, 245), (235, 70, 95), (245, 205, 70)]
            r, g, b = flecks[fleck_index]
        else:
            # Lerp from (18, 22, 40) at L=0 to (70, 80, 125) at L=1
            opal_low = (18, 22, 40)
            opal_high = (70, 80, 125)
            
            r = opal_low[0] + (opal_high[0] - opal_low[0]) * luminance
            g = opal_low[1] + (opal_high[1] - opal_low[1]) * luminance
            b = opal_low[2] + (opal_high[2] - opal_low[2]) * luminance
    
    # Gold trim stays unchanged (leave as-is)
    
    return int(r), int(g), int(b)


# Black Opal animates itself: static textures get OPAL_PHASES frames that blend into each other, drill textures that already
# animate (the working strips) get OPAL_WORKING_PHASES looks of their own frames, one look after the other.
OPAL_PHASES = 8
OPAL_FRAMETIME = 8
OPAL_WORKING_PHASES = 16

FLECK_COLOURS = {"green": (60, 205, 120), "blue": (70, 140, 245), "violet": (160, 95, 240), "orange": (245, 150, 50)}


def smooth(f):
    return f * f * (3 - 2 * f)


def black_opal_fleck(hash_val, phase):
    """Colour of one fleck at a point of the loop: it walks through green, blue and violet (a quarter of the flecks add
    orange) and swells and fades on its own beat, so only a few are bright at a time."""
    keys = ["green", "blue", "violet"] + (["orange"] if (hash_val // 10000) % 4 == 0 else [])
    start = ((hash_val // 100) % 64) / 64.0
    beat = ((hash_val // 6400) % 64) / 64.0
    u = ((phase + start) % 1.0) * len(keys)
    i = int(u)
    f = smooth(u - i)
    a, b = FLECK_COLOURS[keys[i % len(keys)]], FLECK_COLOURS[keys[(i + 1) % len(keys)]]
    colour = [a[c] + (b[c] - a[c]) * f for c in range(3)]
    swell = ((1 + math.cos(2 * math.pi * (phase + beat))) / 2) ** 2
    return colour, 0.22 + 0.78 * swell


def apply_black_opal_palette(r, g, b, x, y, filename, phase=0.0):
    """Black Opal: a near-black blue-grey body, a dull gold trim and opal flecks that shimmer over the loop."""
    h, s, l = rgb_to_hsl(r, g, b)
    is_near_white = (max(r, g, b) - min(r, g, b) <= 25) and l > 0.75
    is_wood = 15 <= h <= 45 and s > 0.25 and not is_near_white
    is_metal = s < 0.2 or is_near_white
    is_gold_trim = 45 <= h <= 65 and s > 0.3 and not is_near_white
    luminance = get_luminance(r, g, b) / 255.0

    if is_wood or is_metal:
        lo, hi = ((17, 19, 28), (70, 77, 98)) if is_wood else ((13, 16, 25), (60, 68, 90))
        body = [lo[c] + (hi[c] - lo[c]) * luminance for c in range(3)]
        hash_val = int(hashlib.md5(f"{x},{y},{filename}".encode()).hexdigest(), 16)
        if (hash_val % 100) < (9 if is_wood else 16) and luminance > 0.25:
            colour, strength = black_opal_fleck(hash_val, phase)
            body = [body[c] + (colour[c] - body[c]) * strength for c in range(3)]
        return tuple(int(v) for v in body)
    if is_gold_trim:
        lo, hi = (72, 56, 22), (176, 142, 64)
        return tuple(int(lo[c] + (hi[c] - lo[c]) * luminance) for c in range(3))
    return r, g, b


def source_frames(input_path):
    """(image, frame count, frametime) of a source texture; frametime is 1 when it has no animation."""
    img = Image.open(input_path).convert('RGBA')
    frames = max(1, img.height // img.width)
    frametime = 1
    meta = input_path + '.mcmeta'
    if os.path.exists(meta):
        with open(meta, encoding='utf-8') as f:
            frametime = int(json.load(f).get('animation', {}).get('frametime', 1))
    return img, frames, frametime


def recolour(img, palette_func, filename, tile=0, phase=None):
    """One 16x16 frame of img (frame number tile) through the palette. Animated skins pass their phase as an extra argument."""
    out = Image.new('RGBA', (img.width, img.width))
    src, dst = img.load(), out.load()
    for y in range(img.width):
        for x in range(img.width):
            r, g, b, a = src[x, y + tile * img.width]
            if phase is None:
                dst[x, y] = palette_func(r, g, b, x, y, filename) + (a,)
            else:
                dst[x, y] = palette_func(r, g, b, x, y, filename, phase) + (a,)
    return out


def write_texture(skin_id, filename, palette_func, animated):
    """Writes skin/<skin>/<filename> (and its .mcmeta). A skin that animates itself gets, per frame of an animated source,
    OPAL_PHASES looks: static textures a strip of OPAL_PHASES frames that blends, animated ones (a drill at work) the source frames
    once per phase so the working cycle keeps its speed."""
    input_path = os.path.join(INPUT_DIR, filename)
    output_path = os.path.join(OUTPUT_DIR, skin_id, filename)
    img, frames, frametime = source_frames(input_path)
    if not animated:
        tiles = [recolour(img, palette_func, filename, t) for t in range(frames)]
        meta = None
        if frames > 1:
            meta = {"animation": {"frametime": frametime}}
    elif frames == 1:
        tiles = [recolour(img, palette_func, filename, 0, p / OPAL_PHASES) for p in range(OPAL_PHASES)]
        meta = {"animation": {"frametime": OPAL_FRAMETIME, "interpolate": True}}
    else:
        tiles = [recolour(img, palette_func, filename, t, p / OPAL_WORKING_PHASES)
                 for p in range(OPAL_WORKING_PHASES) for t in range(frames)]
        meta = {"animation": {"frametime": frametime}}
    out = Image.new('RGBA', (img.width, img.width * len(tiles)))
    for i, tile in enumerate(tiles):
        out.paste(tile, (0, i * img.width))
    out.save(output_path)
    mcmeta = output_path + '.mcmeta'
    if meta:
        with open(mcmeta, 'w', newline='\n') as f:
            json.dump(meta, f, indent=2)
            f.write('\n')
    elif os.path.exists(mcmeta):
        os.remove(mcmeta)


def process_skin(skin_id, palette_func, animated=False):
    """Process all textures for a skin."""
    output_skin_dir = os.path.join(OUTPUT_DIR, skin_id)
    os.makedirs(output_skin_dir, exist_ok=True)

    input_files = []
    if os.path.exists(INPUT_DIR):
        for filename in os.listdir(INPUT_DIR):
            if (filename.endswith('.png') and
                (filename.startswith('mining_drill') or filename.startswith('sluice')) and
                not filename.endswith(('_s.png', '_n.png')) and
                not is_skip_file(filename)):
                input_files.append(filename)
    input_files.sort()

    for filename in input_files:
        write_texture(skin_id, filename, palette_func, animated)
    return len(input_files), input_files


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


PALETTES = {
    "rustic": apply_rustic_palette,
    "rustic_weathered": apply_rustic_weathered_palette,
    "brass": apply_brass_palette,
    "obsidian": apply_obsidian_palette,
    "frosted": apply_frosted_palette,
    "outback": apply_outback_palette,
    "outback_dusty": apply_outback_dusty_palette,
    "outback_ochre": apply_outback_ochre_palette,
    "opal_calm": apply_opal_calm_palette,
    "opal": apply_opal_palette,
    "opal_boulder": apply_opal_boulder_palette,
    "black_opal": apply_black_opal_palette,
}


def main():
    """Main entry point: python tools/make_skins.py [skin ...]"""
    wanted = sys.argv[1:] or list(SKINS)
    for skin_id in wanted:
        written_count, input_files = process_skin(skin_id, PALETTES[skin_id], SKINS[skin_id].get("animated", False))
        written_count += write_wears(skin_id, input_files)
        create_preview(skin_id, input_files)
        print(f"Written {written_count} textures for skin '{skin_id}'")


if __name__ == "__main__":
    main()
