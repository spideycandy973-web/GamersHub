from PIL import Image, ImageDraw
import sys
sys.path.insert(0,'/tmp/ic3')
import gh as G
RES='/home/user/GamersHub/app/src/main/res'
full = G.full
layer = G.layer

side = int(1024*0.80)
fg = Image.new('RGBA',(1024,1024),(0,0,0,0))
fg.alpha_composite(layer.resize((side,side), Image.LANCZOS), ((1024-side)//2,(1024-side)//2))
fg.resize((432,432), Image.LANCZOS).save(f'{RES}/drawable-nodpi/ic_launcher_foreground.png')
G.backdrop().convert('RGB').resize((432,432), Image.LANCZOS).save(f'{RES}/drawable-nodpi/ic_launcher_background.png')

def circle(size):
    m=Image.new('L',(size,size),0); ImageDraw.Draw(m).ellipse([0,0,size-1,size-1],fill=255); return m

for d,px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    sq = full.convert('RGB').resize((px,px), Image.LANCZOS)
    sq.save(f'{RES}/mipmap-{d}/ic_launcher.png')
    rd = sq.copy(); rd.putalpha(circle(px)); rd.save(f'{RES}/mipmap-{d}/ic_launcher_round.png')

ico = full.convert('RGB').resize((512,512), Image.LANCZOS)
ico.save('/home/user/GamersHub/docs/brand/icon-512.png')
ico.resize((128,128), Image.LANCZOS).save('/home/user/GamersHub/docs/brand/icon-128.png')

# adaptive preview check
fg2 = Image.open(f'{RES}/drawable-nodpi/ic_launcher_foreground.png').convert('RGBA').resize((432,432))
bg2 = Image.open(f'{RES}/drawable-nodpi/ic_launcher_background.png').convert('RGBA').resize((432,432))
comp = bg2.copy(); comp.alpha_composite(fg2)
out = Image.new('RGB',(430,200),(20,19,18))
for i,mk in enumerate(['circle','square']):
    m = Image.new('L',(432,432),0)
    if mk=='circle': ImageDraw.Draw(m).ellipse([0,0,431,431],fill=255)
    else: ImageDraw.Draw(m).rounded_rectangle([0,0,431,431],radius=96,fill=255)
    c=comp.copy(); c.putalpha(m); c=c.resize((180,180),Image.LANCZOS)
    out.paste(c,(20+i*200,10),c)
out.save('/tmp/ic3/adaptive.png')
print('exported')
