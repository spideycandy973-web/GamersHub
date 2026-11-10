"""Launcher icons with no plate behind them.

Same emblem artwork as `emblem.py`, but nothing is painted underneath: the
launcher tile is transparent, so the mark floats on whatever the home screen
shows instead of sitting on a black square. Adaptive icons get a transparent
background layer for the same reason.
"""
import os

from PIL import Image
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
RES = os.path.join(ROOT, 'app/src/main/res')
DOCS = os.path.join(ROOT, 'docs/brand')
SRC = os.path.join(HERE, 'source-emblem.jpg')

SIZE = 1024
SFX = ['', '_bolt', '_chevron', '_gh']
DENS = [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]


def load_emblem(square=SIZE, margin=0.06):
    """Tight crop of the emblem on a square transparent canvas."""
    im = Image.open(SRC).convert('RGB')
    a = np.array(im).astype(int)
    ys, xs = np.where(a.max(axis=2) > 40)
    art = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))

    w, h = art.size
    inner = int(square * (1 - 2 * margin))
    scale = inner / max(w, h)
    art = art.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)

    arr = np.array(art).astype(int)
    # The backdrop is pure black; the emblem's own darkest plate is navy. Lifting the
    # cut-out with a small threshold keeps the armour edges and drops the backdrop.
    alpha = np.clip((arr.max(axis=2) - 4) * (255.0 / 8.0), 0, 255).astype(np.uint8)
    art = art.convert('RGBA')
    art.putalpha(Image.fromarray(alpha))

    canvas = Image.new('RGBA', (square, square), (0, 0, 0, 0))
    canvas.alpha_composite(art, ((square - art.width) // 2, (square - art.height) // 2))
    return canvas


def circle_mask(size):
    from PIL import ImageDraw
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def main():
    # Adaptive foreground: kept inside the 66% safe zone.
    fg = load_emblem(SIZE, margin=0.17)
    legacy_src = load_emblem(SIZE, margin=0.05)

    mono_base = load_emblem(SIZE, margin=0.17)
    alpha = mono_base.split()[3]
    mono = Image.new('RGBA', (SIZE, SIZE), (255, 255, 255, 0))
    mono.putalpha(alpha)

    for sfx in SFX:
        fg.resize((432, 432), Image.LANCZOS).save(
            os.path.join(RES, f'drawable-nodpi/ic_launcher{sfx}_foreground.png'))
        mono.resize((432, 432), Image.LANCZOS).save(
            os.path.join(RES, f'drawable-nodpi/ic_launcher{sfx}_mono.png'))

        for dens, px in DENS:
            art = legacy_src.resize((px, px), Image.LANCZOS)
            art.save(os.path.join(RES, f'mipmap-{dens}/ic_launcher{sfx}.png'))
            rd = art.copy()
            rd.putalpha(circle_mask(px))
            rd.save(os.path.join(RES, f'mipmap-{dens}/ic_launcher{sfx}_round.png'))

        # Settings previews use plain bitmaps, so keep them in step with the icon.
        preview = {
            '': 'ic_pick_pad.png',
            '_bolt': 'ic_pick_gh.png',
            '_chevron': 'ic_pick_speed.png',
            '_gh': 'ic_pick_mono.png',
        }[sfx]
        legacy_src.resize((192, 192), Image.LANCZOS).save(
            os.path.join(RES, 'drawable-nodpi', preview))

        for suffix in ('', '_round'):
            xml = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/transparent" />
    <foreground android:drawable="@drawable/ic_launcher{sfx}_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher{sfx}_mono" />
</adaptive-icon>
'''
            open(os.path.join(RES, f'mipmap-anydpi-v26/ic_launcher{sfx}{suffix}.xml'), 'w').write(xml)

    try:
        legacy_src.resize((512, 512), Image.LANCZOS).save(os.path.join(DOCS, 'icon-512.png'))
    except Exception:
        pass

    notif = Image.new('RGBA', (96, 96), (255, 255, 255, 0))
    notif.putalpha(alpha.resize((96, 96), Image.LANCZOS))
    notif.save(os.path.join(RES, 'drawable-nodpi/ic_stat_gh.png'))
    print('clear emblems exported')


if __name__ == '__main__':
    main()
