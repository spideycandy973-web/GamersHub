from PIL import Image, ImageDraw, ImageFilter
import numpy as np, math, os

S, SS = 1024, 4
C = S*SS
cx = cy = C/2

def hx(h): h=h.lstrip('#'); return np.array([int(h[i:i+2],16) for i in (0,2,4)], dtype=np.float64)
def rgb(a): return tuple(int(v) for v in a)

AMBER   = hx('FFD68C')
ORANGE  = hx('FF7A1A')
CRIMSON = hx('A30F05')
GOLD    = hx('FFE7BE')

def tri_gradient(size, stops):
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w]
    t = xx/(w-1)*0.55 + yy/(h-1)*0.45
    out = np.zeros((h, w, 3))
    out[:] = stops[0][1]
    for i in range(len(stops)-1):
        t0, c0 = stops[i]; t1, c1 = stops[i+1]
        m = (t >= t0) & (t <= t1)
        k = np.clip((t[m]-t0)/max(t1-t0, 1e-6), 0, 1)[:, None]
        out[m] = c0[None, :]*(1-k) + c1[None, :]*k
    out[t > stops[-1][0]] = stops[-1][1]
    return Image.fromarray(out.astype(np.uint8), 'RGB')

def radial(size, r, alpha, color):
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt((xx-w/2)**2 + (yy-h/2)**2)/r
    a = np.clip(1-d, 0, 1)**2.1 * alpha
    img = np.zeros((h, w, 4)); img[..., :3] = color; img[..., 3] = a
    return Image.fromarray(img.astype(np.uint8), 'RGBA')

def hex_pts(radius, cxx=cx, cyy=cy):
    return [(cxx + radius*math.cos(math.radians(-90+i*60)),
             cyy + radius*math.sin(math.radians(-90+i*60))) for i in range(6)]

def hex_mask(radius, rnd):
    m = Image.new('L', (C, C), 0)
    ImageDraw.Draw(m).polygon(hex_pts(radius), fill=255)
    m = m.filter(ImageFilter.GaussianBlur(rnd)).point(lambda v: 255 if v > 132 else 0)
    return m.filter(ImageFilter.GaussianBlur(rnd*0.22))

def clip(layer, mask):
    out = layer.copy()
    out.putalpha(Image.composite(layer.split()[3], Image.new('L', (C, C), 0), mask))
    return out

R = C*0.352
MASK = hex_mask(R, C*0.011)

def backdrop():
    bg = tri_gradient((C, C), [(0.0, hx('1A1209')), (0.55, hx('0C0806')), (1.0, hx('040303'))]).convert('RGBA')
    bg.alpha_composite(radial((C, C), C*0.62, 44, ORANGE).transform((C,C), Image.AFFINE, (1,0,-C*0.22,0,1,-C*0.34)))
    bg.alpha_composite(radial((C, C), C*0.55, 38, CRIMSON).transform((C,C), Image.AFFINE, (1,0,-C*0.14,0,1,-C*0.18)))
    return bg

def mark():
    layer = Image.new('RGBA', (C, C), (0, 0, 0, 0))

    # ember halo
    halo = Image.new('RGBA', (C, C), (0,0,0,0))
    halo.paste(rgb(ORANGE)+(70,), (0,0), MASK.filter(ImageFilter.GaussianBlur(C*0.030)))
    layer.alpha_composite(halo)

    # body
    body = tri_gradient((C, C), [(0.0, AMBER), (0.34, ORANGE), (0.78, CRIMSON)]).convert('RGBA')
    body.putalpha(MASK)
    layer.alpha_composite(body)

    # bottom depth
    shade = Image.new('RGBA', (C, C), (0,0,0,0))
    ImageDraw.Draw(shade).rectangle([0, cy+C*0.02, C, C], fill=(38, 4, 0, 105))
    layer.alpha_composite(clip(shade.filter(ImageFilter.GaussianBlur(C*0.05)), MASK))

    # top light
    top = Image.new('RGBA', (C, C), (0,0,0,0))
    ImageDraw.Draw(top).ellipse([cx-R*1.05, cy-R*1.10, cx+R*1.05, cy+R*0.05], fill=rgb(GOLD)+(40,))
    layer.alpha_composite(clip(top.filter(ImageFilter.GaussianBlur(C*0.038)), MASK))

    # diagonal sheen
    sh = Image.new('RGBA', (C, C), (0,0,0,0))
    ImageDraw.Draw(sh).polygon([(cx-R*0.95, cy-R*0.50), (cx-R*0.10, cy-R*1.05),
                                (cx+R*0.26, cy-R*0.60), (cx-R*0.45, cy+R*0.05)], fill=(255,255,255,30))
    layer.alpha_composite(clip(sh.filter(ImageFilter.GaussianBlur(C*0.032)), MASK))

    # inner hairline (glass thickness)
    ring = Image.new('RGBA', (C, C), (0,0,0,0))
    ImageDraw.Draw(ring).polygon(hex_pts(R*0.905), outline=(255,255,255,64), width=int(C*0.0035))
    layer.alpha_composite(clip(ring.filter(ImageFilter.GaussianBlur(C*0.0022)), MASK))

    # rim
    rim = Image.new('RGBA', (C, C), (0,0,0,0))
    rm = Image.new('L', (C, C), 0)
    ImageDraw.Draw(rm).polygon(hex_pts(R), outline=255, width=int(C*0.0085))
    rim.paste((255, 236, 205, 165), (0,0), rm.filter(ImageFilter.GaussianBlur(C*0.0018)))
    layer.alpha_composite(rim)

    # play notch — punched through to the backdrop
    tri = Image.new('L', (C, C), 0)
    w, h = R*0.80, R*0.90
    ox, oy = cx + R*0.055, cy
    ImageDraw.Draw(tri).polygon([(ox-w*0.5, oy-h*0.5), (ox-w*0.5, oy+h*0.5), (ox+w*0.60, oy)], fill=255)
    tri = tri.filter(ImageFilter.GaussianBlur(C*0.004)).point(lambda v: 255 if v > 120 else 0)

    # soft contact shadow around the cut
    soft = tri.filter(ImageFilter.GaussianBlur(C*0.012))
    ring_only = Image.fromarray(np.clip(np.array(soft, dtype=np.int16) - np.array(tri, dtype=np.int16)*0.55, 0, 255).astype(np.uint8))
    shadow = Image.new('RGBA', (C, C), (52, 6, 0, 255))
    shadow.putalpha(ring_only.point(lambda v: int(v*0.85)))
    layer.alpha_composite(shadow)

    # bright lip on the leading edge of the notch
    lip = Image.new('RGBA', (C, C), (0,0,0,0))
    ImageDraw.Draw(lip).line([(ox-w*0.5+C*0.010, oy-h*0.46), (ox-w*0.5+C*0.010, oy+h*0.46)],
                             fill=(255, 226, 180, 120), width=int(C*0.005))
    layer.alpha_composite(lip.filter(ImageFilter.GaussianBlur(C*0.005)))
    return layer, tri

def compose():
    bg = backdrop()
    layer, tri = mark()
    bg.alpha_composite(layer)
    bg.paste(Image.new('RGBA', (C, C), (13, 8, 5, 255)), (0, 0), tri)
    return bg

full = compose()
layer, tri = mark()
layer.putalpha(Image.fromarray(np.minimum(np.array(layer.split()[3], dtype=np.int16),
                                         255 - np.array(tri, dtype=np.int16)).astype(np.uint8)))
full.resize((512,512), Image.LANCZOS).save('/tmp/ic2/final_512.png')
os.makedirs('/tmp/ic2', exist_ok=True)
layer.resize((512,512), Image.LANCZOS).save('/tmp/ic2/mark_512.png')
im512 = Image.open('/tmp/ic2/final_512.png')
sheet = Image.new('RGB',(600,230),(14,14,16))
x=24
for s in (192,96,64,48):
    sheet.paste(im512.resize((s,s),Image.LANCZOS),(x,(230-s)//2)); x+=s+28
sheet.save('/tmp/ic2/sheet.png')
print('ok')
