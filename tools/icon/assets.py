from PIL import Image, ImageDraw
import liquid
S = liquid.S
RES = '/home/user/GamersHub/app/src/main/res'

def mark_layer():
    """the sphere + orbit + play, no background"""
    return liquid.build(False)

def bg_layer():
    return liquid.background(S)

# adaptive foreground: mark scaled into the 66dp safe zone of the 108dp canvas
mark = mark_layer().resize((int(1024*0.80), int(1024*0.80)), Image.LANCZOS)
fg = Image.new('RGBA', (1024, 1024), (0, 0, 0, 0))
fg.alpha_composite(mark, ((1024 - mark.width)//2, (1024 - mark.height)//2))
fg.resize((432, 432), Image.LANCZOS).save(f'{RES}/drawable-nodpi/ic_launcher_foreground.png')
bg_layer().convert('RGB').resize((432, 432), Image.LANCZOS).save(f'{RES}/drawable-nodpi/ic_launcher_background.png')

def rounded(size, ratio=0.235):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size-1, size-1], radius=int(size*ratio), fill=255)
    return m

def circle(size):
    m = Image.new('L', (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size-1, size-1], fill=255)
    return m

full = bg_layer(); full.alpha_composite(mark_layer())
for density, px in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    sq = full.convert('RGB').resize((px, px), Image.LANCZOS)
    sq.save(f'{RES}/mipmap-{density}/ic_launcher.png')
    rd = sq.copy(); rd.putalpha(circle(px))
    rd.save(f'{RES}/mipmap-{density}/ic_launcher_round.png')

full.convert('RGB').resize((512, 512), Image.LANCZOS).save('/home/user/GamersHub/docs/brand/icon-512.png')
full.convert('RGB').resize((512, 512), Image.LANCZOS).save('/tmp/icons/final_512.png')
print('assets written')
