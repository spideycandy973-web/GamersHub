"""Build every launcher resource from the supplied emblem artwork.

The user supplied one 2048x2048 render (black background, centred emblem). This
script crops it, then writes *all* icon slots — default, the three alts and the
round variants — from that single source so no stale icon can survive an update.
"""
import os
from PIL import Image, ImageDraw, ImageFilter
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
RES = os.path.join(ROOT, 'app/src/main/res')
DOCS = os.path.join(ROOT, 'docs/brand')
SRC = os.path.join(HERE, 'source-emblem.jpg')

SIZE = 1024
SFX = ['', '_bolt', '_chevron', '_gh']
DENS = [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]


def hx(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def load_emblem(square=1024, margin=0.055):
    """Tight crop of the emblem on a square transparent canvas."""
    im = Image.open(SRC).convert('RGB')
    a = np.array(im).astype(int)
    ys, xs = np.where(a.max(axis=2) > 40)
    box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    art = im.crop(box)

    # keep the emblem square and centred, with a small optical margin
    w, h = art.size
    inner = int(square * (1 - 2 * margin))
    scale = inner / max(w, h)
    art = art.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)

    canvas = Image.new('RGBA', (square, square), (0, 0, 0, 0))
    # knock the (almost) pure black backdrop out so the tile colour shows through
    arr = np.array(art).astype(int)
    alpha = np.clip(arr.max(axis=2) * 6, 0, 255).astype(np.uint8)
    art = art.convert('RGBA')
    art.putalpha(Image.fromarray(alpha))
    canvas.alpha_composite(art, ((square - art.width) // 2, (square - art.height) // 2))
    return canvas


def tile_background(size):
    """Warm near-black squircle-free backdrop (the launcher applies the mask)."""
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    t = (x + y) / (2.0 * (size - 1))
    stops = [(0.0, hx('1B1210')), (0.55, hx('100B0A')), (1.0, hx('080505'))]
    out = np.zeros((size, size, 3), np.float32)
    out[:] = stops[0][1]
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        m = (t >= t0) & (t <= t1)
        k = np.clip((t[m] - t0) / max(t1 - t0, 1e-6), 0, 1)[:, None]
        out[m] = np.array(c0)[None, :] * (1 - k) + np.array(c1)[None, :] * k
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGB')


def circle_mask(size):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def squircle(size, radius=0.235):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius), fill=255)
    return m


def legacy_art(size):
    """Static icon: emblem on the warm tile, squircle-clipped."""
    art = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    art.paste(tile_background(size), (0, 0))
    emblem = load_emblem(size, margin=0.10)
    art.alpha_composite(emblem)
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    out.paste(art, (0, 0), squircle(size))
    return out


def main():
    emblem_fg = load_emblem(SIZE, margin=0.16)     # stays inside the adaptive safe zone
    bg = tile_background(432)

    bg.save(os.path.join(RES, 'drawable-nodpi/ic_launcher_background.png'))

    mono_base = load_emblem(SIZE, margin=0.16)
    alpha = mono_base.split()[3]
    mono = Image.new('RGBA', (SIZE, SIZE), (255, 255, 255, 0))
    mono.putalpha(alpha)

    for sfx in SFX:
        emblem_fg.resize((432, 432), Image.LANCZOS).save(
            os.path.join(RES, f'drawable-nodpi/ic_launcher{sfx}_foreground.png'))
        mono.resize((432, 432), Image.LANCZOS).save(
            os.path.join(RES, f'drawable-nodpi/ic_launcher{sfx}_mono.png'))

        for dens, px in DENS:
            art = legacy_art(px)
            art.save(os.path.join(RES, f'mipmap-{dens}/ic_launcher{sfx}.png'))
            rd = art.copy()
            rd.putalpha(circle_mask(px))
            rd.save(os.path.join(RES, f'mipmap-{dens}/ic_launcher{sfx}_round.png'))

        for suffix in ('', '_round'):
            xml = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher{sfx}_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher{sfx}_mono" />
</adaptive-icon>
'''
            open(os.path.join(RES, f'mipmap-anydpi-v26/ic_launcher{sfx}{suffix}.xml'), 'w').write(xml)

    legacy_art(512).save(os.path.join(DOCS, 'icon-512.png'))
    for sfx in SFX[1:]:
        legacy_art(512).save(os.path.join(DOCS, f'icon{sfx.replace("_", "-")}-512.png'))

    # notification icon: white emblem silhouette
    notif = Image.new('RGBA', (96, 96), (255, 255, 255, 0))
    notif.putalpha(alpha.resize((96, 96), Image.LANCZOS))
    notif.save(os.path.join(RES, 'drawable-nodpi/ic_stat_gh.png'))
    print('emblem exported')


if __name__ == '__main__':
    main()
