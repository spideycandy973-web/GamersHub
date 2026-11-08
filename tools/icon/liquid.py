from PIL import Image, ImageDraw, ImageFilter
S = 4096; K = S / 1024.0; C = S/2

def vgrad(size, c0, c1):
    img = Image.new('RGB', (size, size)); d = ImageDraw.Draw(img)
    for i in range(size):
        t = i / (size - 1)
        d.line([(0, i), (size, i)], fill=tuple(int(c0[j] + (c1[j]-c0[j])*t) for j in range(3)))
    return img

def radial(size, cx, cy, r, color, peak):
    g = Image.new('L', (size, size), 0)
    steps = 60
    d = ImageDraw.Draw(g)
    for i in range(steps, 0, -1):
        rr = r * i / steps
        a = int(peak * (1 - i / steps) ** 1.6)
        d.ellipse([cx-rr, cy-rr, cx+rr, cy+rr], fill=a)
    g = g.filter(ImageFilter.GaussianBlur(r*0.12))
    out = Image.new('RGBA', (size, size), (0,0,0,0))
    out.paste(Image.new('RGBA', (size, size), color + (255,)), (0,0), g)
    return out

def background(size=S):
    bg = vgrad(size, (8, 10, 18), (4, 5, 9)).convert('RGBA')
    bg.alpha_composite(radial(size, size*0.26, size*0.22, size*0.55, (124,107,255), 120))
    bg.alpha_composite(radial(size, size*0.80, size*0.78, size*0.50, (79,216,255), 95))
    bg.alpha_composite(radial(size, size*0.52, size*0.55, size*0.42, (255,140,90), 26))
    return bg

def ring_layer(size, cx, cy, rx, ry, width, angle_deg, top_col, bot_col, alpha=255):
    big = int(max(rx, ry) * 2 + width * 4)
    lay = Image.new('RGBA', (big, big), (0,0,0,0))
    ImageDraw.Draw(lay).ellipse([big/2-rx, big/2-ry, big/2+rx, big/2+ry],
                                outline=(255,255,255,alpha), width=int(width))
    g = Image.new('RGB', (big, big)); gd = ImageDraw.Draw(g)
    for i in range(big):
        t = i / (big - 1)
        gd.line([(0, i), (big, i)], fill=tuple(int(top_col[j] + (bot_col[j]-top_col[j])*t) for j in range(3)))
    tinted = Image.new('RGBA', (big, big), (0,0,0,0))
    tinted.paste(g.convert('RGBA'), (0,0), lay.split()[3])
    tinted = tinted.rotate(angle_deg, resample=Image.BICUBIC, center=(big/2, big/2))
    out = Image.new('RGBA', (size, size), (0,0,0,0))
    out.alpha_composite(tinted, (int(cx-big/2), int(cy-big/2)))
    return out

def orb(size, r):
    lay = Image.new('RGBA', (size, size), (0,0,0,0))
    base = Image.new('RGBA', (size, size), (0,0,0,0))
    ImageDraw.Draw(base).ellipse([C-r, C-r, C+r, C+r], fill=(255,255,255,22))
    body = Image.new('RGB', (size, size)); bd = ImageDraw.Draw(body)
    for i in range(size):
        t = i / (size - 1)
        bd.line([(0, i), (size, i)], fill=(int(196-96*t), int(206-104*t), int(255-40*t)))
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse([C-r, C-r, C+r, C+r], fill=70)
    base.paste(body.convert('RGBA'), (0,0), mask)
    lay.alpha_composite(base)
    spec = Image.new('L', (size, size), 0)
    ImageDraw.Draw(spec).ellipse([C-r*0.72, C-r*0.95, C+r*0.10, C-r*0.18], fill=165)
    spec = spec.filter(ImageFilter.GaussianBlur(r*0.10))
    lay.paste(Image.new('RGBA', (size,size), (255,255,255,255)), (0,0), spec)
    bounce = Image.new('L', (size, size), 0)
    ImageDraw.Draw(bounce).ellipse([C-r*0.80, C+r*0.36, C+r*0.72, C+r*0.92], fill=85)
    bounce = bounce.filter(ImageFilter.GaussianBlur(r*0.14))
    lay.paste(Image.new('RGBA', (size,size), (150,240,255,255)), (0,0), bounce)
    rim = Image.new('RGBA', (size, size), (0,0,0,0))
    ImageDraw.Draw(rim).ellipse([C-r, C-r, C+r, C+r], outline=(255,255,255,190), width=int(r*0.030))
    ImageDraw.Draw(rim).ellipse([C-r*0.955, C-r*0.955, C+r*0.955, C+r*0.955],
                                outline=(120,180,255,70), width=int(r*0.012))
    fade = Image.new('L', (size, size), 255)
    fd = ImageDraw.Draw(fade)
    for i in range(size):
        t = max(0.0, (i - C) / (size/2))
        fd.line([(0,i),(size,i)], fill=int(255*(1-t*0.88)))
    rim.putalpha(Image.composite(rim.split()[3], Image.new('L',(size,size),0), fade))
    # aurora refraction inside the glass: violet -> cyan diagonal band, clipped to the sphere
    band = Image.new('RGB', (size, size))
    bnd = ImageDraw.Draw(band)
    for i in range(size):
        t = i / (size - 1)
        bnd.line([(0, i), (size, i)],
                 fill=(int(124 + (79-124)*t), int(107 + (216-107)*t), int(255)))
    band = band.convert('RGBA')
    band.putalpha(72)
    band = band.rotate(-28, resample=Image.BICUBIC, center=(C, C))
    clip = Image.new('L', (size, size), 0)
    ImageDraw.Draw(clip).ellipse([C-r, C-r, C+r, C+r], fill=255)
    band.putalpha(Image.composite(band.split()[3], Image.new('L', (size, size), 0), clip))
    lay.alpha_composite(band)
    lay.alpha_composite(rim)
    return lay

def triangle(size, w, h):
    lay = Image.new('RGBA', (size, size), (0,0,0,0))
    pts = [(C-w*0.34, C-h/2), (C-w*0.34, C+h/2), (C+w*0.60, C)]
    glow = Image.new('RGBA', (size, size), (0,0,0,0))
    ImageDraw.Draw(glow).polygon(pts, fill=(255,255,255,140))
    glow = glow.filter(ImageFilter.GaussianBlur(26*K))
    lay.alpha_composite(glow)
    ImageDraw.Draw(lay).polygon(pts, fill=(255,255,255,240))
    return lay

def build(with_bg=True):
    base = background() if with_bg else Image.new('RGBA', (S,S), (0,0,0,0))
    base.alpha_composite(ring_layer(S, C, C, 392*K, 126*K, 20*K, -24, (124,107,255), (79,216,255), 215))
    sh = Image.new('L', (S,S), 0)
    ImageDraw.Draw(sh).ellipse([C-300*K, C+215*K, C+300*K, C+430*K], fill=110)
    sh = sh.filter(ImageFilter.GaussianBlur(60*K))
    base.paste(Image.new('RGBA',(S,S),(0,0,0,255)), (0,0), sh)
    base.alpha_composite(orb(S, 276*K))
    # warm spark where the orbit crosses in front (a little life in a cold palette)
    spark = radial(S, C - 250*K, C + 108*K, 46*K, (255,196,140), 235)
    base.alpha_composite(spark)
    front = ring_layer(S, C, C, 392*K, 126*K, 20*K, -24, (124,107,255), (79,216,255), 125)
    keep = Image.new('L', (S,S), 0)
    ImageDraw.Draw(keep).polygon([(0, C+10), (S, C-120), (S, S), (0, S)], fill=255)
    front.putalpha(Image.composite(front.split()[3], Image.new('L',(S,S),0), keep))
    base.alpha_composite(front)
    base.alpha_composite(triangle(S, 210*K, 250*K))
    return base

if __name__ == '__main__':
    full = build(True)
    full.convert('RGB').resize((512,512), Image.LANCZOS).save('liq_512.png')
    full.convert('RGB').resize((48,48), Image.LANCZOS).resize((192,192), Image.NEAREST).save('liq_small.png')
    print('ok')
