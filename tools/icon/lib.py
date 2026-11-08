from PIL import Image, ImageDraw, ImageFilter, ImageFont
import numpy as np, math

SS = 4
S  = 1024
C  = S*SS
INK = (10, 8, 6)

def hx(h): return tuple(int(h.lstrip('#')[i:i+2],16) for i in (0,2,4))

def catmull_closed(pts, samples=40):
    p = [np.array(q, float) for q in pts]
    n = len(p); out = []
    for i in range(n):
        p0, p1, p2, p3 = p[(i-1)%n], p[i], p[(i+1)%n], p[(i+2)%n]
        for j in range(samples):
            t = j/samples; t2, t3 = t*t, t*t*t
            v = 0.5*((2*p1) + (-p0+p2)*t + (2*p0-5*p1+4*p2-p3)*t2 + (-p0+3*p1-3*p2+p3)*t3)
            out.append((v[0]*C, v[1]*C))
    return out

def mask_poly(pts):
    m = Image.new('L', (C, C), 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m

def mask_rr(box, r):
    m = Image.new('L', (C, C), 0)
    ImageDraw.Draw(m).rounded_rectangle([box[0]*C, box[1]*C, box[2]*C, box[3]*C], radius=r*C, fill=255)
    return m

def mask_circle(cx, cy, r):
    m = Image.new('L', (C, C), 0)
    ImageDraw.Draw(m).ellipse([(cx-r)*C, (cy-r)*C, (cx+r)*C, (cy+r)*C], fill=255)
    return m

def union(*masks):
    a = np.zeros((C, C), np.uint8)
    for m in masks: a = np.maximum(a, np.array(m, np.uint8))
    return Image.fromarray(a)

def subtract(base, cut):
    return Image.fromarray(np.clip(np.array(base, np.int16) - np.array(cut, np.int16), 0, 255).astype(np.uint8))

def grad(stops, angle=50):
    yy, xx = np.mgrid[0:C, 0:C]
    t = (xx/(C-1))*math.sin(math.radians(angle)) + (yy/(C-1))*math.cos(math.radians(angle))
    t = np.clip((t-0.5)*1.1+0.5, 0, 1)
    out = np.zeros((C, C, 3)); out[:] = stops[0][1]
    for i in range(len(stops)-1):
        t0, c0 = stops[i]; t1, c1 = stops[i+1]
        m = (t >= t0) & (t <= t1)
        k = np.clip((t[m]-t0)/max(t1-t0, 1e-6), 0, 1)[:, None]
        out[m] = np.array(c0)[None, :]*(1-k) + np.array(c1)[None, :]*k
    out[t > stops[-1][0]] = stops[-1][1]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGB')

def backdrop():
    return grad([(0.0, hx('191108')), (0.55, hx('0B0806')), (1.0, hx('050403'))], angle=90).copy()

def paint(mask, stops, top_light=0.26, bg=None):
    base = (bg.copy() if bg is not None else backdrop()).convert('RGBA')
    g = grad(stops).convert('RGBA'); g.putalpha(mask)
    base.alpha_composite(g)
    yy = np.linspace(0, 1, C)[:, None]
    light = np.clip(1 - yy*1.9, 0, 1)**1.5 * top_light * 255
    lay = np.zeros((C, C, 4)); lay[..., :3] = 255; lay[..., 3] = light
    im = Image.fromarray(lay.astype(np.uint8), 'RGBA')
    a = np.minimum(np.array(im.split()[3], np.int16), np.array(mask, np.int16)).astype(np.uint8)
    im.putalpha(Image.fromarray(a))
    base.alpha_composite(im)
    return base.convert('RGB')
