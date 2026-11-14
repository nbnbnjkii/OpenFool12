"""Native high-density avatar and banner artwork; requires Pillow and DejaVu Sans."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import random

OUT = Path('android/assets/neon')
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def font(size): return ImageFont.truetype(FONT, size)

# Draw medallions at 3x their source design coordinates.
S = 3
def box(*values): return tuple(v*S for v in values)
for bot in (False, True):
    im = Image.new('RGBA', (180*S,180*S))
    d = ImageDraw.Draw(im)
    d.ellipse(box(4,4,176,176),fill=(32,39,76),outline=(65,225,136),width=6*S)
    d.ellipse(box(15,15,165,165),fill=(134,148,169) if bot else (101,101,193),outline=(69,181,247),width=4*S)
    d.ellipse(box(40,112,140,206),fill=(42,55,91))
    d.polygon([box(69,122),box(90,144),box(111,122)],fill='white')
    d.rounded_rectangle(box(62,49,121,127),25*S,fill=(210,218,227) if bot else (250,177,135))
    if bot:
        d.text(box(91,86),'?',font=font(51*S),anchor='mm',fill=(135,146,164))
    else:
        d.pieslice(box(53,33,127,99),175,355,fill=(96,48,36))
        d.ellipse(box(59,34,111,62),fill=(104,53,38))
        d.ellipse(box(75,79,80,85),fill=(62,45,49))
        d.ellipse(box(105,79,110,85),fill=(62,45,49))
        d.arc(box(79,90,107,111),15,160,fill=(177,83,68),width=3*S)
    im.save(OUT / ('bot.png' if bot else 'avatar.png'))

S = 2
banner = Image.new('RGBA',(660*S,278*S))
d = ImageDraw.Draw(banner)
for y in range(278*S):
    t=y/(278*S-1)
    rgb=tuple(round(a*(1-t)+b*t) for a,b in zip((21,61,95),(35,23,65)))
    d.line((0,y,660*S,y),fill=rgb+(255,))
random.seed(12)
for _ in range(55):
    x=random.randrange(660); y=random.randrange(278)
    d.line(box(330,150,x,y),fill=(32,78,108),width=2)
for name,x,angle in [('1d',212,18),('13s',281,0),('1s',347,-15)]:
    card=Image.open('android/assets/decks/int/'+name+'.png').convert('RGBA')
    card.thumbnail((112*S,155*S),Image.Resampling.LANCZOS)
    card=card.rotate(angle,expand=True,resample=Image.Resampling.BICUBIC)
    banner.alpha_composite(card,(x*S,3*S))
d=ImageDraw.Draw(banner)
d.text(box(330,164),'ДУРАК',font=font(99*S),anchor='mm',fill=(213,251,255),stroke_width=7*S,stroke_fill=(13,66,160))
d.text(box(330,235),'DURAK',font=font(43*S),anchor='mm',fill=(255,204,60),stroke_width=2*S,stroke_fill=(153,81,26))
d.rounded_rectangle(box(2,2,657,275),27*S,outline=(83,70,117),width=4*S)
banner.save(OUT/'banner.png')
