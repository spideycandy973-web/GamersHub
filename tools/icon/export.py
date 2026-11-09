import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icons import *
from PIL import Image, ImageDraw

RES = '/home/user/GamersHub/app/src/main/res'
DOCS = '/home/user/GamersHub/docs/brand'
DENS = [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]

def circle_mask(size):
    m = Image.new('L',(size,size),0); ImageDraw.Draw(m).ellipse([0,0,size-1,size-1],fill=255); return m

# shared backdrop
backdrop().convert('RGB').resize((432,432), Image.LANCZOS).save(f'{RES}/drawable-nodpi/ic_launcher_background.png')

for sfx,(mk,stops,tl) in VARIANTS.items():
    mask = mk()
    full = full_rgba(mask, stops, tl).convert('RGB')
    mark = mark_rgba(mask, stops, tl)

    # adaptive foreground (mark inside the safe zone)
    side = int(1024*0.78)
    fg = Image.new('RGBA',(1024,1024),(0,0,0,0))
    fg.alpha_composite(mark.resize((side,side), Image.LANCZOS), ((1024-side)//2,(1024-side)//2))
    fg.resize((432,432), Image.LANCZOS).save(f'{RES}/drawable-nodpi/ic_launcher{sfx}_foreground.png')

    # monochrome silhouette
    mono_side = int(1024*0.62)
    mono = Image.new('L',(1024,1024),0)
    mono.paste(mask.resize((mono_side,mono_side), Image.LANCZOS), ((1024-mono_side)//2,(1024-mono_side)//2))
    mono_rgba = Image.new('RGBA',(1024,1024),(255,255,255,0))
    mono_rgba.putalpha(mono)
    mono_rgba.resize((432,432), Image.LANCZOS).save(f'{RES}/drawable-nodpi/ic_launcher{sfx}_mono.png')

    # legacy + doc art
    for d,px in DENS:
        sq = full.resize((px,px), Image.LANCZOS)
        sq.save(f'{RES}/mipmap-{d}/ic_launcher{sfx}.png')
        rd = sq.copy(); rd.putalpha(circle_mask(px)); rd.save(f'{RES}/mipmap-{d}/ic_launcher{sfx}_round.png')
    name = 'icon-512.png' if sfx=='' else f'icon{sfx.replace("_","-")}-512.png'
    full.resize((512,512), Image.LANCZOS).save(f'{DOCS}/{name}')

# notification icon: white pad silhouette
mask = mask_pad()
mono = Image.new('L',(C,C),0); s=int(C*0.80)
mono.paste(mask.resize((s,s), Image.LANCZOS), ((C-s)//2,(C-s)//2))
notif = Image.new('RGBA',(96,96),(255,255,255,0))
notif.putalpha(mono.resize((96,96), Image.LANCZOS))
notif.save(f'{RES}/drawable-nodpi/ic_stat_gh.png')

# adaptive xml per variant
for sfx in VARIANTS.keys():
    for suffix in ('', '_round'):
        xml = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher{sfx}_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher{sfx}_mono" />
</adaptive-icon>
'''
        open(f'{RES}/mipmap-anydpi-v26/ic_launcher{sfx}{suffix}.xml','w').write(xml)

# comparison sheet for docs
sheet = Image.new('RGB',(len(VARIANTS)*270+40,320),(16,15,14))
for i,(sfx) in enumerate(VARIANTS.keys()):
    im = Image.open(f'{DOCS}/icon-512.png' if sfx=='' else f'{DOCS}/icon{sfx.replace("_","-")}-512.png')
    sheet.paste(im.resize((220,220),Image.LANCZOS),(20+i*270,16))
    sheet.paste(im.resize((48,48),Image.LANCZOS),(106+i*270,252))
sheet.save(f'{DOCS}/icon-options.png')
print('exported')
