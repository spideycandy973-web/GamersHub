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
# The very bitmap the floating bubble paints. Building the launcher tiles from this
# file is what makes the icon on the home screen look exactly like the floating one.
FLOAT = os.path.join(RES, 'drawable-nodpi/ic_float_emblem.png')

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


def float_base(square=SIZE):
    """The floating-bubble artwork, same framing, on a transparent square."""
    im = Image.open(FLOAT).convert('RGBA')
    return im.resize((square, square), Image.LANCZOS)


def grade(im, gain):
    """Colour grade that keeps the artwork's alpha and detail."""
    arr = np.array(im).astype(float)
    arr[..., 0] = np.clip(arr[..., 0] * gain[0], 0, 255)
    arr[..., 1] = np.clip(arr[..., 1] * gain[1], 0, 255)
    arr[..., 2] = np.clip(arr[..., 2] * gain[2], 0, 255)
    return Image.fromarray(arr.astype(np.uint8))


def white_mark(im):
    alpha = im.split()[3]
    out = Image.new('RGBA', im.size, (255, 255, 255, 0))
    out.putalpha(alpha)
    return out


def circle_mask(size):
    from PIL import ImageDraw
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def main():
    # Every launcher tile starts from the floating bubble's own bitmap.
    legacy_src = float_base(SIZE)

    VARIANTS = {
        '': legacy_src,                                     # Pad — identical to the bubble
        '_bolt': grade(legacy_src, (1.18, 0.92, 0.72)),     # GH — warmer ember wash
        '_chevron': grade(legacy_src, (1.00, 0.86, 1.22)),  # Speed — cooler violet wash
        '_gh': white_mark(legacy_src),                      # Mono — white silhouette
    }

    # Adaptive foreground: kept inside the 66% safe zone.
    fg = load_emblem(SIZE, margin=0.17)

    mono_base = load_emblem(SIZE, margin=0.17)
    alpha = mono_base.split()[3]
    mono = Image.new('RGBA', (SIZE, SIZE), (255, 255, 255, 0))
    mono.putalpha(alpha)

    for sfx in SFX:
        variant = VARIANTS[sfx]
        fg.resize((432, 432), Image.LANCZOS).save(
            os.path.join(RES, f'drawable-nodpi/ic_launcher{sfx}_foreground.png'))
        mono.resize((432, 432), Image.LANCZOS).save(
            os.path.join(RES, f'drawable-nodpi/ic_launcher{sfx}_mono.png'))

        for dens, px in DENS:
            # No circle cut: cropping the pointed wings is what made the round icon
            # look different from the floating mark.
            art = variant.resize((px, px), Image.LANCZOS)
            art.save(os.path.join(RES, f'mipmap-{dens}/ic_launcher{sfx}.png'))
            art.save(os.path.join(RES, f'mipmap-{dens}/ic_launcher{sfx}_round.png'))

        # Settings previews use plain bitmaps, so keep them in step with the icon.
        preview = {
            '': 'ic_pick_pad.png',
            '_bolt': 'ic_pick_gh.png',
            '_chevron': 'ic_pick_speed.png',
            '_gh': 'ic_pick_mono.png',
        }[sfx]
        variant.resize((192, 192), Image.LANCZOS).save(
            os.path.join(RES, 'drawable-nodpi', preview))

        # No adaptive-icon XML on purpose: a transparent background layer still makes
        # some launchers (and themed-icon packs) draw a plate behind the mark. Plain
        # PNGs keep the alpha exactly as drawn.

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
