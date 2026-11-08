"""GamersHub mark — custom GH ligature. One continuous bar doubles as the G spur
and the H crossbar; the H's left stem is tangent to the G ring, squaring its right side."""
from PIL import Image, ImageDraw, ImageFilter
import numpy as np, math

SS, S = 4, 1024
C = S*SS
def hx(h): h=h.lstrip('#'); return np.array([int(h[i:i+2],16) for i in (0,2,4)], float)
def rgb(a): return tuple(int(v) for v in a)

AMBER=hx('FFD27E'); GOLD=hx('FFB03A'); EMBER=hx('FF6A1E'); CRIMSON=hx('A81006'); CREAM=hx('FFF2D8')

# ---- geometry (logical 1024) ----
W      = 100.0
R      = 224.0
CX, CY = 348.0, 512.0
HS     = 262.0                      # H width between stems
HX1    = CX + R + W/2               # tangent to the G's outer edge
HX2    = HX1 + HS
HY1    = CY - R - W/2               # stem bounds — same height as the G ring
HY2    = CY + R + W/2
BAR_X0 = CX + 0.16*R

def grad(size, stops):
    w,h=size; yy,xx=np.mgrid[0:h,0:w]
    t = xx/(w-1)*0.52 + yy/(h-1)*0.48
    out=np.zeros((h,w,3)); out[:]=stops[0][1]
    for i in range(len(stops)-1):
        t0,c0=stops[i]; t1,c1=stops[i+1]; m=(t>=t0)&(t<=t1)
        k=np.clip((t[m]-t0)/max(t1-t0,1e-6),0,1)[:,None]; out[m]=c0[None,:]*(1-k)+c1[None,:]*k
    out[t>stops[-1][0]]=stops[-1][1]
    return Image.fromarray(out.astype(np.uint8),'RGB')

def mask():
    m = Image.new('L',(C,C),0); d=ImageDraw.Draw(m)
    w = W*SS; r = w/2
    d.ellipse([(CX-R)*SS,(CY-R)*SS,(CX+R)*SS,(CY+R)*SS], outline=255, width=int(w))
    for x in (HX1, HX2):
        d.rounded_rectangle([x*SS-r, HY1*SS, x*SS+r, HY2*SS], radius=r, fill=255)
    d.rounded_rectangle([BAR_X0*SS-r, CY*SS-r, HX2*SS+r, CY*SS+r], radius=r, fill=255)
    return m

def backdrop():
    bg = grad((C,C),[(0.0,hx('1B1209')),(0.52,hx('0C0806')),(1.0,hx('050303'))]).convert('RGBA')
    yy,xx = np.mgrid[0:C,0:C]
    d = np.sqrt((xx-C*0.40)**2+(yy-C*0.38)**2)/(C*0.60)
    g = np.zeros((C,C,4)); g[...,:3]=EMBER; g[...,3]=np.clip(1-d,0,1)**2.0*44
    bg.alpha_composite(Image.fromarray(g.astype(np.uint8),'RGBA'))
    return bg

def paint(mask, with_bg=True):
    base = backdrop() if with_bg else Image.new('RGBA',(C,C),(0,0,0,0))
    if with_bg:
        glow = Image.new('RGBA',(C,C),(0,0,0,0))
        glow.paste(rgb(EMBER)+(58,), (0,0), mask.filter(ImageFilter.GaussianBlur(C*0.020)))
        base.alpha_composite(glow)

    body = grad((C,C),[(0.0,AMBER),(0.38,GOLD),(0.70,EMBER),(1.0,CRIMSON)]).convert('RGBA')
    body.putalpha(mask); base.alpha_composite(body)

    # diagonal sheen (glass)
    sh = Image.new('RGBA',(C,C),(0,0,0,0))
    ImageDraw.Draw(sh).polygon([(0, C*0.30),(C, C*0.02),(C, C*0.26),(0, C*0.56)], fill=(255,245,225,38))
    sh = sh.filter(ImageFilter.GaussianBlur(C*0.03))
    sh.putalpha(Image.composite(sh.split()[3], Image.new('L',(C,C),0), mask))
    base.alpha_composite(sh)

    up = mask.transform((C,C), Image.AFFINE, (1,0,0,0,1,0,-C*0.0055))
    dn = mask.transform((C,C), Image.AFFINE, (1,0,0,0,1,0, C*0.0055))
    lit  = Image.fromarray(np.clip(np.array(mask,int)-np.array(dn,int),0,255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(C*0.0035))
    dark = Image.fromarray(np.clip(np.array(mask,int)-np.array(up,int),0,255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(C*0.0035))
    hl = Image.new('RGBA',(C,C), rgb(CREAM)+(160,)); hl.putalpha(lit.point(lambda v:int(v*0.8))); base.alpha_composite(hl)
    sd = Image.new('RGBA',(C,C),(54,6,0,255)); sd.putalpha(dark.point(lambda v:int(v*0.6))); base.alpha_composite(sd)
    return base

M = mask()
full = paint(M, True)
layer = paint(M, False).copy()
layer.putalpha(Image.fromarray(np.minimum(np.array(layer.split()[3],int), np.array(M,int)).astype(np.uint8)))

full_512 = full.convert('RGB').resize((512,512), Image.LANCZOS)
full_512.save('/tmp/ic3/final_512.png')
layer.resize((512,512), Image.LANCZOS).save('/tmp/ic3/mark_512.png')

sheet = Image.new('RGB',(600,235),(15,14,13))
x=24
for s in (192,96,64,48):
    sheet.paste(full_512.resize((s,s),Image.LANCZOS),(x,(235-s)//2)); x+=s+28
sheet.save('/tmp/ic3/sheet.png')
print('ok')
