"""Render button artwork at 3x virtual resolution with antialiased curved edges.
Run from the repository root with Pillow installed. No enlargement of old PNGs.
"""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path('android/assets/neon')
OUT.mkdir(parents=True, exist_ok=True)
SCALE = 3
W, H = 440 * SCALE, 96 * SCALE
PALETTES = {
    'green': ((41, 239, 83), (0, 149, 89)),
    'purple': ((175, 78, 247), (93, 38, 175)),
    'blue': ((44, 181, 255), (24, 83, 210)),
    'gold': ((255, 224, 41), (249, 136, 5)),
    'slate': ((92, 125, 156), (48, 65, 101)),
}
for name, (top, bottom) in PALETTES.items():
    im = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(im)
    for y in range(H):
        t = y / (H - 1)
        rgb = tuple(round(a * (1-t) + b * t) for a, b in zip(top, bottom))
        d.line((0, y, W, y), fill=rgb + (255,))
    # Rounded shape rendered at twice final resolution, then downsampled.
    mask = Image.new('L', (W*2, H*2))
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle((6, 6, W*2-7, H*2-20), radius=56*SCALE, fill=255)
    im.putalpha(mask.resize((W, H), Image.Resampling.LANCZOS))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((4, 4, W-5, H-13), radius=28*SCALE,
                        outline=tuple(min(255, v+35) for v in top)+(220,), width=3)
    d.arc((10, 12, W-11, H+90), 185, 355, fill=(255,255,255,85), width=3)
    im.save(OUT / (name + '.png'))
