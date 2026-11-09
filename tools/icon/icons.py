import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib import *
from PIL import ImageFont
from PIL import ImageChops
import math
FONT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ArchivoBlack.ttf')

GOLD  = [(0.0,hx('FFD98E')),(0.45,hx('FFB03A')),(1.0,hx('EF5F12'))]
GOLD2 = [(0.0,hx('FFE7B4')),(0.40,hx('FFB03A')),(1.0,hx('D9450B'))]

def mask_pad():
    pts = [(0.245,0.330),(0.430,0.310),(0.570,0.310),(0.755,0.330),
           (0.845,0.410),(0.878,0.560),(0.838,0.702),(0.735,0.706),
           (0.655,0.565),(0.560,0.500),(0.440,0.500),(0.345,0.565),
           (0.265,0.706),(0.162,0.702),(0.122,0.560),(0.155,0.410)]
    body = mask_poly(catmull_closed(pts, 60))
    cx, cy, a, b = 0.335, 0.455, 0.052, 0.112
    dp = union(mask_rr((cx-b, cy-a, cx+b, cy+a), a*0.85),
               mask_rr((cx-a, cy-b, cx+a, cy+b), a*0.85))
    btn = union(*[mask_circle(0.665+dx, 0.455+dy, 0.036) for dx,dy in [(0,0.062),(0.062,0),(0,-0.062),(-0.062,0)]])
    return subtract(body, union(dp, btn))

def mask_chev():
    m = Image.new('L',(C,C),0); d = ImageDraw.Draw(m)
    w = 0.088
    for x in (0.17, 0.415, 0.66):
        ax, tip, ay, by = C*x, C*(x+0.155), C*0.285, C*0.715
        d.polygon([(ax,ay),(ax+C*w,ay),(tip,C*0.5),(ax+C*w,by),(ax,by),(tip-C*w,C*0.5)], fill=255)
    return m.filter(ImageFilter.GaussianBlur(C*0.0025)).point(lambda v: 255 if v>127 else 0)

def mask_gh():
    f = ImageFont.truetype(FONT_PATH, int(C*0.60))
    tmp = Image.new('L',(C,C),0); d = ImageDraw.Draw(tmp)
    gb = d.textbbox((0,0),'G',font=f); hb = d.textbbox((0,0),'H',font=f)
    gw, hw = gb[2]-gb[0], hb[2]-hb[0]
    track = -int(C*0.055)
    total = gw + track + hw
    x0 = (C-total)//2 - gb[0]
    asc, desc = f.getmetrics()
    y0 = (C - (asc+desc))//2
    d.text((x0, y0), 'G', font=f, fill=255)
    d.text((x0+gw+track, y0), 'H', font=f, fill=255)
    return tmp

VARIANTS = {
    '':        (mask_pad,  GOLD,  0.26),
    '_chevron':(mask_chev, GOLD2, 0.18),
    '_gh':     (mask_gh,   GOLD,  0.22),
}

def mark_rgba(mask, stops, top_light):
    """coloured mark on transparency"""
    g = grad(stops).convert('RGBA'); g.putalpha(mask)
    yy = np.linspace(0, 1, C)[:, None]
    light = np.clip(1 - yy*1.9, 0, 1)**1.5 * top_light * 255
    lay = np.zeros((C, C, 4)); lay[..., :3] = 255; lay[..., 3] = light
    im = Image.fromarray(lay.astype(np.uint8), 'RGBA')
    im.putalpha(Image.fromarray(np.minimum(np.array(im.split()[3], np.int16), np.array(mask, np.int16)).astype(np.uint8)))
    g.alpha_composite(im)
    return g

def full_rgba(mask, stops, top_light):
    base = backdrop().convert('RGBA')
    base.alpha_composite(mark_rgba(mask, stops, top_light))
    return base

# ---------------------------------------------------------------- GH lockup
# The default icon is inverted: a gold gradient tile with the monogram knocked
# out of it in near-black. The letters are typeset (Archivo Black, optically
# kerned) so curves and terminals stay clean at every density.
GH_DARK = hx('150B05')
GH_SIZE = 0.54
GH_FG_SIZE = 0.47      # smaller inside the adaptive safe zone
GH_RADIUS = 0.22


def _tile_gradient(angle=118):
    """Warm gold ramp: light top-left, ember bottom-right."""
    stops = [(0.0, hx('FFD98E')), (0.40, hx('FFA62B')), (1.0, hx('D84A08'))]
    yy, xx = np.mgrid[0:C, 0:C].astype(np.float32)
    a = math.radians(angle)
    t = xx * np.cos(a) + yy * np.sin(a)
    t = (t - t.min()) / (t.max() - t.min())
    out = np.zeros((C, C, 3), np.float32)
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        m = (t >= t0) & (t <= t1)
        k = np.clip((t[m] - t0) / max(t1 - t0, 1e-6), 0, 1)[:, None]
        out[m] = np.array(c0)[None, :] * (1 - k) + np.array(c1)[None, :] * k
    out[t < stops[0][0]] = stops[0][1]
    out[t > stops[-1][0]] = stops[-1][1]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGB')


GOLD_TILE = _tile_gradient().convert('RGBA')


def mask_gh_type(size=GH_SIZE, tracking=-0.02):
    """Knock-out mask for the GH monogram, optically centred in the tile."""
    f = ImageFont.truetype(FONT_PATH, int(C * size))
    probe = ImageDraw.Draw(Image.new('L', (8, 8), 0))
    boxes = [probe.textbbox((0, 0), ch, font=f) for ch in 'GH']
    track = tracking * C
    total = sum(b[2] - b[0] for b in boxes) + track
    x = (C - total) / 2
    asc, desc = f.getmetrics()
    y = (C - (asc + desc)) / 2
    m = Image.new('L', (C, C), 0)
    d = ImageDraw.Draw(m)
    for b, ch in zip(boxes, 'GH'):
        d.text((x - b[0], y), ch, font=f, fill=255)
        x += b[2] - b[0] + track
    return m


def inverted_foreground(size=GH_FG_SIZE):
    """Full-bleed gold with the monogram in near-black (adaptive foreground)."""
    dark = Image.new('RGBA', (C, C), GH_DARK + (255,))
    return Image.composite(dark, GOLD_TILE.copy(), mask_gh_type(size))


def inverted_tile(size=GH_SIZE, radius=GH_RADIUS):
    """Legacy/static art: gold squircle, monogram knocked out, transparent outside."""
    tile = mask_rr((0.0, 0.0, 1.0, 1.0), radius)
    out = Image.new('RGBA', (C, C), (0, 0, 0, 0))
    out.paste(inverted_foreground(size), (0, 0), tile)
    return out


def mask_bolt():
    """Silhouette of the monogram — used by the monochrome adaptive layer."""
    return mask_gh_type(GH_FG_SIZE)


VARIANTS['_bolt'] = (mask_bolt, GOLD, 0.24)
# marks rendered as a gold tile with the artwork knocked out
INVERTED = {'_bolt'}
