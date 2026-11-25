#!/usr/bin/env bash
# Installs the three pieces of open cArl art and rebuilds.
#
#   tools/import-art.sh splash.png icon.png logo.png
#
# splash -> drawable-nodpi/carl_splash.png   (full screen launch art)
# icon   -> mipmap-*/ic_launcher_foreground.png (adaptive icon, 66% safe zone)
#           and drawable-nodpi/carl_avatar.png  (circular crop for the title bar)
# logo   -> drawable-nodpi/carl_logo.png      (brush wordmark in the title bar)
set -euo pipefail

SPLASH=${1:?usage: import-art.sh <splash.png> <icon.png> <logo.png>}
ICON=${2:?missing icon}
LOGO=${3:?missing logo}

python3 - "$SPLASH" "$ICON" "$LOGO" <<'PY'
import os, sys
from PIL import Image, ImageDraw

splash_path, icon_path, logo_path = sys.argv[1:4]
RES = 'app/src/main/res'
NODPI = f'{RES}/drawable-nodpi'
os.makedirs(NODPI, exist_ok=True)

def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print(f'{path}  {img.size[0]}x{img.size[1]}  {os.path.getsize(path)//1024}KB')

# splash: keep detail, cap the long edge so it does not blow up the APK
splash = Image.open(splash_path).convert('RGB')
scale = min(1.0, 1800 / max(splash.size))
if scale < 1.0:
    splash = splash.resize((int(splash.size[0] * scale), int(splash.size[1] * scale)), Image.LANCZOS)
save(splash, f'{NODPI}/carl_splash.png')

# logo: trim the black margin so the wordmark fills the bar
logo = Image.open(logo_path).convert('RGB')
gray = logo.convert('L')
box = gray.point(lambda v: 255 if v > 28 else 0).getbbox()
if box:
    logo = logo.crop(box)
scale = min(1.0, 1600 / logo.size[0])
if scale < 1.0:
    logo = logo.resize((int(logo.size[0] * scale), int(logo.size[1] * scale)), Image.LANCZOS)
# key out the near-black backdrop so the wordmark sits on any surface
logo = logo.convert('RGBA')
px = logo.load()
for y in range(logo.size[1]):
    for x in range(logo.size[0]):
        r, g, b, a = px[x, y]
        if r < 26 and g < 26 and b < 26:
            px[x, y] = (r, g, b, 0)
save(logo, f'{NODPI}/carl_logo.png')

# icon: circular crop of the badge centre, used as the title-bar avatar
icon = Image.open(icon_path).convert('RGB')
side = min(icon.size)
centre = icon.crop(((icon.size[0] - side) // 2, (icon.size[1] - side) // 2,
                    (icon.size[0] + side) // 2, (icon.size[1] + side) // 2))
face_side = int(side * 0.62)
face = centre.crop(((side - face_side) // 2, (side - face_side) // 2,
                    (side + face_side) // 2, (side + face_side) // 2)).resize((512, 512), Image.LANCZOS)
mask = Image.new('L', (512, 512), 0)
ImageDraw.Draw(mask).ellipse([0, 0, 511, 511], fill=255)
avatar = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
avatar.paste(face, (0, 0), mask)
save(avatar, f'{NODPI}/carl_avatar.png')

# icon: 108dp canvas, art inside the 66dp safe zone, one PNG per density
for dpi, px in [('mdpi', 108), ('hdpi', 162), ('xhdpi', 216), ('xxhdpi', 324), ('xxxhdpi', 432)]:
    canvas = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    badge = int(px * 0.68)
    art = icon.resize((badge, badge), Image.LANCZOS).convert('RGBA')
    # round the corners so launchers that do not mask still look deliberate
    mask = Image.new('L', (badge, badge), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, badge - 1, badge - 1], radius=int(badge * 0.18), fill=255)
    canvas.paste(art, ((px - badge) // 2, (px - badge) // 2), mask)
    save(canvas, f'{RES}/mipmap-{dpi}/ic_launcher_foreground.png')
PY

echo "== rebuilding"
./gradlew :app:assembleRelease --console=plain
echo "== done: app/build/outputs/apk/release/app-release.apk"
