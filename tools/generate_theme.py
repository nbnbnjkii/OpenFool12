"""Generate original portrait UI textures (Pillow). No mockup photos shipped in app."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from pathlib import Path
import math, random
OUT=Path('android/assets/neon'); OUT.mkdir(parents=True,exist_ok=True)
random.seed(12)
def font(n): return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',n)
def gradient(w,h,top,bottom):
 im=Image.new('RGBA',(w,h)); p=im.load()
 for y in range(h):
  t=y/max(1,h-1)
  for x in range(w): p[x,y]=tuple(int(a*(1-t)+b*t) for a,b in zip(top,bottom))+(255,)
 return im
def save(im,name): im.save(OUT/(name+'.png'))
bg=gradient(720,1500,(19,23,55),(24,27,57)); d=ImageDraw.Draw(bg)
for i in range(65):
 x=random.randrange(720); y=random.randrange(1500); d.line((x,y,x+random.randrange(-180,180),y+random.randrange(40,250)),fill=(30,57,85),width=1)
 d.ellipse((x-2,y-2,x+2,y+2),fill=(35,95,112))
save(bg,'lobby')
for name,top,bot in [('green',(41,239,83),(0,149,89)),('purple',(175,78,247),(93,38,175)),('blue',(44,181,255),(24,83,210)),('gold',(255,224,41),(249,136,5)),('slate',(92,125,156),(48,65,101))]:
 im=gradient(660,144,top,bot); mask=Image.new('L',(660,144)); md=ImageDraw.Draw(mask); md.rounded_rectangle((2,2,657,136),42,fill=255); im.putalpha(mask); d=ImageDraw.Draw(im); d.rounded_rectangle((3,3,656,135),42,outline=tuple(min(255,v+40) for v in top)+(255,),width=3); d.arc((9,12,650,204),180,360,fill=(255,255,255,75),width=4); save(im,name)
table=bg.copy(); glow=Image.new('RGBA',table.size); d=ImageDraw.Draw(glow)
d.ellipse((-180,205,900,1315),fill=(22,75,78),outline=(101,48,121),width=45)
d.ellipse((-140,240,860,1280),fill=(18,84,84),outline=(49,255,217),width=7)
glow=Image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(15)),glow); table=Image.alpha_composite(table,glow); d=ImageDraw.Draw(table)
for i in range(18):
 box=(-112+i*3,269+i*3,832-i*3,1251-i*3)
 d.ellipse(box,outline=(24,88+i//3,87+i//3),width=2)
d.ellipse((63,350,657,1180),outline=(53,113,115),width=3)
d.ellipse((220,615,500,895),outline=(58,124,119),width=4)
d.text((360,746),'♠',font=font(120),anchor='mm',fill=(53,117,112))
d.rectangle((0,1370,720,1500),fill=(28,31,65)); save(table,'table')
# Avatar medallions, drawn as artwork rather than platform emoji.
for bot in (False,True):
 im=Image.new('RGBA',(180,180)); d=ImageDraw.Draw(im)
 d.ellipse((4,4,176,176),fill=(32,39,76),outline=(65,225,136),width=6)
 d.ellipse((15,15,165,165),fill=(101,101,193) if not bot else (134,148,169),outline=(69,181,247),width=4)
 d.ellipse((40,112,140,206),fill=(42,55,91)); d.polygon([(69,122),(90,144),(111,122)],fill='white')
 d.rounded_rectangle((62,49,121,127),25,fill=(250,177,135) if not bot else (210,218,227))
 if not bot:
  d.pieslice((53,33,127,99),175,355,fill=(96,48,36)); d.ellipse((59,34,111,62),fill=(104,53,38))
  d.ellipse((75,79,80,85),fill=(62,45,49)); d.ellipse((105,79,110,85),fill=(62,45,49)); d.arc((79,90,107,111),15,160,fill=(177,83,68),width=3)
 else: d.text((91,86),'?',font=font(51),anchor='mm',fill=(135,146,164))
 save(im,'bot' if bot else 'avatar')
banner=gradient(660,278,(21,61,95),(35,23,65)); d=ImageDraw.Draw(banner)
for i in range(55):
 x=random.randrange(660); y=random.randrange(278); d.line((330,150,x,y),fill=(32,78,108),width=1)
for name,x,angle in [('1d',212,18),('13s',281,0),('1s',347,-15)]:
 card=Image.open('android/assets/decks/int/'+name+'.png').convert('RGBA'); card.thumbnail((112,155)); card=card.rotate(angle,expand=True,resample=Image.Resampling.BICUBIC); banner.alpha_composite(card,(x,3))
d=ImageDraw.Draw(banner)
d.text((330,164),'ДУРАК',font=font(99),anchor='mm',fill=(213,251,255),stroke_width=7,stroke_fill=(13,66,160))
d.text((330,235),'DURAK',font=font(43),anchor='mm',fill=(255,204,60),stroke_width=2,stroke_fill=(153,81,26)); d.rounded_rectangle((2,2,657,275),27,outline=(83,70,117),width=4); save(banner,'banner')

# Override controls with native 3x, antialiased artwork.
import runpy
runpy.run_path("tools/generate_buttons.py")

runpy.run_path("tools/generate_theme_details.py")

runpy.run_path("tools/generate_game_extras.py")

runpy.run_path("tools/generate_hud.py")

runpy.run_path("tools/generate_lobby.py")
