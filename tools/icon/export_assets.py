from PIL import Image, ImageDraw
import numpy as np, ember, os

RES = '/home/user/GamersHub/app/src/main/res'
full = ember.full
mark_img = ember.layer
bg_img = ember.backdrop()

# adaptive foreground: mark inside the 66dp safe zone, transparent background
side = int(1024*0.78)
fg = Image.new('RGBA', (1024, 1024), (0,0,0,0))
fg.alpha_composite(mark_img.resize((side, side), Image.LANCZOS), ((1024-side)//2, (1024-side)//2))
fg.resize((432,432), Image.LANCZOS).save(f'{RES}/drawable-nodpi/ic_launcher_foreground.png')
bg_img.convert('RGB').resize((432,432), Image.LANCZOS).save(f'{RES}/drawable-nodpi/ic_launcher_background.png')

def circle(size):
    m = Image.new('L', (size,size), 0)
    ImageDraw.Draw(m).ellipse([0,0,size-1,size-1], fill=255)
    return m

for d, px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    sq = full.convert('RGB').resize((px,px), Image.LANCZOS)
    sq.save(f'{RES}/mipmap-{d}/ic_launcher.png')
    rd = sq.copy(); rd.putalpha(circle(px))
    rd.save(f'{RES}/mipmap-{d}/ic_launcher_round.png')

ico = full.convert('RGB').resize((512,512), Image.LANCZOS)
ico.save('/home/user/GamersHub/docs/brand/icon-512.png')
ico.resize((128,128), Image.LANCZOS).save('/home/user/GamersHub/docs/brand/icon-128.png')
print('assets ok')
