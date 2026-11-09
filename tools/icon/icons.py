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

def mask_bolt():
    """GH monogram — octagonal G (custom aperture + spur) locked up with a bold H.

    Deliberately not a typeset monogram: both letters are built from the same
    geometry (eight-sided ring, flat 1.09:1 stems) so they read as one mark at 48 px.
    """
    def new(): return Image.new('L', (C, C), 0)

    def rect(x0, y0, x1, y1):
        m = new()
        ImageDraw.Draw(m).rectangle([x0*C, y0*C, x1*C, y1*C], fill=255)
        return m

    def ngon(cx, cy, r, n, rot=-67.5):
        return [(cx + r*math.cos(math.radians(rot + i*360/n)),
                 cy + r*math.sin(math.radians(rot + i*360/n))) for i in range(n)]

    def poly(pts):
        m = new()
        ImageDraw.Draw(m).polygon([(x*C, y*C) for x, y in pts], fill=255)
        return m

    g = ImageChops.subtract(poly(ngon(0.300, 0.500, 0.255, 8)), poly(ngon(0.300, 0.500, 0.168, 8)))
    # mouth of the G: a wedge opened to the right
    g = ImageChops.subtract(g, poly([(0.300, 0.500), (0.620, 0.330), (0.620, 0.770)]))
    g = ImageChops.lighter(g, rect(0.300, 0.545, 0.585, 0.660))      # spur
    h = ImageChops.lighter(rect(0.650, 0.205, 0.768, 0.795), rect(0.845, 0.205, 0.963, 0.795))
    m = ImageChops.lighter(ImageChops.lighter(g, h), rect(0.650, 0.545, 0.963, 0.660))
    m = m.filter(ImageFilter.GaussianBlur(C*0.006)).point(lambda v: 255 if v > 127 else 0)
    return m.filter(ImageFilter.GaussianBlur(C*0.0015))

VARIANTS['_bolt'] = (mask_bolt, GOLD, 0.24)
