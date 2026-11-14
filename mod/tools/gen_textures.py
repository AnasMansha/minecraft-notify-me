"""Generates the Redstone Notifier block textures (original art, GregTech-casing style).
Run from the mod folder:  python -I tools/gen_textures.py
"""
import math, os, sys
from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "notifyme", "textures", "blocks")
os.makedirs(OUT, exist_ok=True)

STEEL, HI, SHADOW, EDGE = (140, 145, 150), (184, 189, 194), (94, 99, 104), (52, 56, 61)
RIVET_HI, RIVET_LO = (214, 218, 222), (70, 74, 80)

def casing():
    img = Image.new("RGBA", (16, 16), STEEL + (255,))
    px = img.load()
    # subtle brushed-metal noise (deterministic)
    for y in range(16):
        for x in range(16):
            n = ((x * 7 + y * 13) ^ (x * y)) % 5 - 2
            r, g, b = STEEL
            px[x, y] = (r + n * 2, g + n * 2, b + n * 2, 255)
    for i in range(16):  # outer frame
        px[i, 0] = px[i, 15] = px[0, i] = px[15, i] = EDGE + (255,)
    for i in range(1, 15):  # bevel
        px[i, 1] = HI + (255,); px[1, i] = HI + (255,)
        px[i, 14] = SHADOW + (255,); px[14, i] = SHADOW + (255,)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):  # rivets
        px[x, y] = RIVET_HI + (255,)
        px[x + (1 if x < 8 else -1), y + (1 if y < 8 else -1)] = RIVET_LO + (255,)
    return img

def recess(img, x0, y0, x1, y1, fill):
    px = img.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = fill + (255,)
    for x in range(x0 - 1, x1 + 2):
        px[x, y0 - 1] = SHADOW + (255,); px[x, y1 + 1] = HI + (255,)
    for y in range(y0 - 1, y1 + 2):
        px[x0 - 1, y] = SHADOW + (255,); px[x1 + 1, y] = HI + (255,)

def side(on):
    img = casing(); px = img.load()
    recess(img, 4, 3, 11, 10, (20, 24, 30))
    cx, cy = 7.5, 9.2
    arc_on = [(95, 230, 255), (40, 160, 215)]
    arc_off = [(52, 70, 82), (40, 54, 64)]
    for y in range(3, 11):
        for x in range(4, 12):
            dx, dy = x - cx, cy - y
            d = math.hypot(dx, dy)
            if dy <= 0.5 or abs(dx) > dy * 1.25:
                continue
            for i, (lo, hi) in enumerate(((2.0, 3.0), (4.1, 5.1))):
                if lo <= d < hi:
                    px[x, y] = (arc_on if on else arc_off)[i] + (255,)
    dot = [(255, 70, 50), (255, 190, 170)] if on else [(90, 26, 26), (120, 40, 40)]
    for (x, y) in ((7, 9), (8, 9), (7, 10), (8, 10)):
        px[x, y] = dot[0] + (255,)
    px[7, 9] = dot[1] + (255,)
    # vent grille
    for x in range(4, 12):
        px[x, 12] = (SHADOW if x % 2 == 0 else EDGE) + (255,)
    # status LED top-right
    px[11, 12] = ((80, 255, 120) if on else (30, 70, 40)) + (255,)
    return img

def top(on):
    img = casing(); px = img.load()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < 3.0:
                if on:
                    c = (255, 225, 210) if d < 1.2 else (255, 90, 60) if d < 2.2 else (220, 40, 25)
                else:
                    c = (110, 38, 34) if d < 1.2 else (80, 22, 20) if d < 2.2 else (60, 16, 15)
                px[x, y] = c + (255,)
            elif d < 4.2:  # bezel ring
                px[x, y] = ((60, 64, 70) if (x + y) % 2 else (44, 48, 53)) + (255,)
            elif on and d < 5.0:  # glow spill onto casing
                r, g, b, a = px[x, y]
                px[x, y] = (min(255, r + 45), g, b, 255)
    px[6, 6] = ((255, 255, 255) if on else (150, 70, 66)) + (255,)  # lens highlight
    return img


# ---------------------------------------------------------------- compact plate (10x10 px face, pixels 3..12)
def compact_casing():
    img = Image.new("RGBA", (16, 16)); px = img.load()
    for y in range(16):
        for x in range(16):
            n = ((x * 5 + y * 11) ^ (x * y)) % 5 - 2
            px[x, y] = (STEEL[0] + n * 2, STEEL[1] + n * 2, STEEL[2] + n * 2, 255)
    for i in range(3, 13):  # edge of the 10x10 back face
        px[i, 3] = px[i, 12] = px[3, i] = px[12, i] = SHADOW + (255,)
    return img

def mini_plate(on, x0, size):
    """Draws the plate face (frame + screen + arcs + dot) into a new 16x16 image at x0..x0+size-1."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); px = img.load()
    x1 = x0 + size - 1
    for y in range(x0, x1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = STEEL + (255,)
    for i in range(x0, x1 + 1):
        px[i, x0] = px[i, x1] = px[x0, i] = px[x1, i] = EDGE + (255,)
    for i in range(x0 + 1, x1):
        px[i, x0 + 1] = HI + (255,); px[x0 + 1, i] = HI + (255,)
        px[i, x1 - 1] = SHADOW + (255,); px[x1 - 1, i] = SHADOW + (255,)
    s0, s1 = x0 + 2, x1 - 2  # screen
    for y in range(s0, s1 + 1):
        for x in range(s0, s1 + 1):
            px[x, y] = (20, 24, 30, 255)
    cx = (s0 + s1) / 2.0
    arc = [(95, 230, 255), (40, 160, 215)] if on else [(52, 70, 82), (40, 54, 64)]
    w = s1 - s0
    px_arc = [(s0 + 1, s0 + 1), (s1 - 1, s0 + 1)] + [(x, s0) for x in range(s0 + 2, s1 - 1)]  # outer arc
    for (x, y) in px_arc: px[x, y] = arc[1] + (255,)
    for x in range(s0 + 2, s1 - 1): px[x, s0 + 2] = arc[0] + (255,)  # inner arc
    dot = (255, 70, 50) if on else (90, 26, 26)
    mx = int(cx)
    for (x, y) in ((mx, s1 - 1), (mx + 1, s1 - 1), (mx, s1), (mx + 1, s1)):
        px[x, y] = dot + (255,)
    if on: px[mx, s1 - 1] = (255, 190, 170, 255)
    px[x1 - 2, x1 - 1] = ((80, 255, 120) if on else (30, 70, 40)) + (255,)  # status LED
    return img

def compact_front(on):
    base = compact_casing()
    base.alpha_composite(mini_plate(on, 3, 10))
    return base

files_compact = {
    "compact_casing.png": compact_casing(),
    "compact_front.png": compact_front(False),
    "compact_front_on.png": compact_front(True),
}
for name, im in files_compact.items():
    im.save(os.path.join(OUT, name))
ITEMS = os.path.join("src", "main", "resources", "assets", "notifyme", "textures", "items")
os.makedirs(ITEMS, exist_ok=True)
mini_plate(True, 2, 12).save(os.path.join(ITEMS, "notifier_compact.png"))

files = {
    "notifier_side.png": side(False), "notifier_side_on.png": side(True),
    "notifier_top.png": top(False), "notifier_top_on.png": top(True),
}
for name, im in files.items():
    im.save(os.path.join(OUT, name))
# preview sheet (not shipped): 8x scale
allfiles = list(files.values()) + [files_compact["compact_front.png"], files_compact["compact_front_on.png"], mini_plate(True, 2, 12)]
prev = Image.new("RGBA", (len(allfiles) * 136, 16 * 8), (30, 30, 30, 255))
for i, im in enumerate(allfiles):
    prev.paste(im.resize((128, 128), Image.NEAREST), (i * 136, 0))
prev.save(sys.argv[1] if len(sys.argv) > 1 else "texture_preview.png")
print("written", list(files))
