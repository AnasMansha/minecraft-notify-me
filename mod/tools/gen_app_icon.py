"""Turns the ON block texture into the app's adaptive-icon foreground (pixel-perfect vector).
Run from the repo root:  python -I mod/tools/gen_app_icon.py"""
import os
from collections import defaultdict
from PIL import Image
SRC = os.path.join("mod", "src", "main", "resources", "assets", "notifyme", "textures", "blocks", "notifier_side_on.png")
OUT = os.path.join("app", "app", "src", "main", "res", "drawable")
img = Image.open(SRC).convert("RGBA")
PX, OFF = 3.5, 26  # 16 px * 3.5dp = 56dp, centred in the 108dp canvas (fits circle masks)
groups = defaultdict(list)
for y in range(16):
    for x in range(16):
        r, g, b, a = img.getpixel((x, y))
        if a: groups["#%02X%02X%02X" % (r, g, b)].append((x, y))
def path(cells):
    return "".join(f"M{OFF+x*PX},{OFF+y*PX}h{PX}v{PX}h-{PX}z" for x, y in cells)
head = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
fg = head + "".join(f'    <path android:fillColor="{c}" android:pathData="{path(cells)}" />\n' for c, cells in groups.items()) + "</vector>\n"
open(os.path.join(OUT, "ic_launcher_fg.xml"), "w", newline="\n").write(fg)
# monochrome: just the bright pixels (signal arcs + lamp)
bright = [(x, y) for c, cells in groups.items() for (x, y) in cells
          if (lambda r, g, b: (r > 200 and g < 120) or (b > 180 and g > 140))(*[int(c[i:i+2], 16) for i in (1, 3, 5)])]
mono = head + f'    <path android:fillColor="#FFFFFF" android:pathData="{path(bright)}" />\n</vector>\n'
open(os.path.join(OUT, "ic_launcher_mono.xml"), "w", newline="\n").write(mono)
bg = head + '    <path android:fillColor="#1A1B1F" android:pathData="M0,0h108v108h-108z" />\n</vector>\n'
open(os.path.join(OUT, "ic_launcher_bg.xml"), "w", newline="\n").write(bg)
print(len(groups), "colours,", len(bright), "mono pixels")
