"""Original dark-blue HUD artwork and small currency/mode symbols."""
from PIL import Image, ImageDraw
from pathlib import Path
out=Path('android/assets/neon')
w,h=1440,345
im=Image.new('RGBA',(w,h));d=ImageDraw.Draw(im)
for y in range(h):
 t=y/(h-1);rgb=tuple(round(a*(1-t)+b*t) for a,b in zip((42,55,94),(21,27,57)))
 d.line((0,y,w,y),fill=rgb+(255,))
d.line((0,3,w,3),fill=(107,150,200,255),width=4)
d.line((0,10,w,10),fill=(39,201,176,100),width=2)
im.save(out/'hud_panel.png')
for name in ('coin','crystal','mode_pass','mode_throw'):
 im=Image.new('RGBA',(120,120));d=ImageDraw.Draw(im)
 if name=='coin':
  d.ellipse((9,9,111,111),fill=(239,176,35),outline=(255,223,104),width=7)
  d.ellipse((24,24,96,96),outline=(193,126,20),width=4)
  d.line((61,38,61,83),fill=(255,235,135),width=9)
 elif name=='crystal':
  d.polygon([(34,13),(85,13),(112,49),(60,112),(8,49)],fill=(50,195,249),outline=(160,242,255))
  d.polygon([(34,13),(60,49),(8,49)],fill=(158,238,255))
  d.polygon([(85,13),(60,49),(112,49)],fill=(111,216,255))
  d.polygon([(60,49),(112,49),(60,112)],fill=(24,130,231))
 elif name=='mode_pass':
  d.rounded_rectangle((16,28,103,91),11,outline=(160,194,218),width=6)
  d.line((12,48,48,48),fill=(184,216,238),width=10)
  d.polygon([(48,34),(67,48),(48,62)],fill=(184,216,238))
  d.line((107,75,71,75),fill=(184,216,238),width=10)
  d.polygon([(71,61),(52,75),(71,89)],fill=(184,216,238))
 else:
  d.rounded_rectangle((25,13,86,96),9,outline=(160,194,218),width=5)
  d.rounded_rectangle((39,25,100,109),9,fill=(35,49,78),outline=(160,194,218),width=5)
  for y in (49,81): d.ellipse((62,y-6,74,y+6),fill=(184,216,238))
 im.save(out/(name+'.png'))
