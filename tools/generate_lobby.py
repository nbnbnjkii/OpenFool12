"""High-density original lobby symbols and orange/blue bot control."""
from PIL import Image,ImageDraw,ImageFilter
from pathlib import Path
import math
out=Path('android/assets/neon'); S=3
C=lambda xs:tuple(round(x*S) for x in xs)
for name in ('planet','friends','robot','trophy','home','shop','tasks','gear','bell','check','plus'):
 im=Image.new('RGBA',(120*S,120*S));d=ImageDraw.Draw(im);white=(241,246,255)
 def ellipse(b,**kw):d.ellipse(C(b),**kw)
 def rect(b,r=0,**kw):d.rounded_rectangle(C(b),r*S,**kw)
 def line(p,fill=white,width=5):d.line(C(p),fill=fill,width=width*S)
 def poly(p,**kw):d.polygon([C(x) for x in p],**kw)
 if name=='planet':
  ellipse((29,27,94,92),fill=white)
  ellipse((10,44,111,76),outline=white,width=6*S)
  for b in [(44,38,55,48),(68,43,80,55),(48,62,62,76)]:ellipse(b,fill=(91,188,144))
 elif name=='friends':
  ellipse((21,18,56,53),fill=white);ellipse((68,25,95,52),fill=white)
  rect((10,62,67,105),18,fill=white);rect((69,63,108,102),14,fill=white)
 elif name=='robot':
  rect((26,32,95,89),15,fill=white);line((60,15,60,34));ellipse((54,9,66,21),fill=white)
  rect((31,96,90,110),6,fill=white);rect((12,47,21,77),4,fill=white);rect((100,47,109,77),4,fill=white)
  for x in (43,77):ellipse((x-5,48,x+5,58),fill=(227,123,41))
  d.arc(C((44,54,78,77)),10,170,fill=(227,123,41),width=4*S)
 elif name=='trophy':
  poly([(31,18),(90,18),(86,59),(72,76),(49,76),(35,59)],fill=white)
  d.arc(C((9,21,50,72)),70,280,fill=white,width=7*S);d.arc(C((71,21,112,72)),260,100,fill=white,width=7*S)
  rect((54,72,68,98),3,fill=white);rect((33,99,88,109),4,fill=white)
 elif name=='home':
  poly([(8,56),(60,11),(112,56),(96,56),(96,108),(70,108),(70,76),(50,76),(50,108),(24,108),(24,56)],fill=white)
 elif name=='shop':
  line((12,20,29,20,42,79,98,79),width=7);poly([(31,34),(111,34),(99,66),(40,66)],fill=white)
  for x in (49,91):ellipse((x-9,92,x+9,110),fill=white)
 elif name=='tasks':
  rect((10,35,111,102),13,fill=white);rect((40,17,81,42),5,outline=white,width=6*S)
  line((10,61,111,61),fill=(59,76,113),width=5);rect((49,51,72,74),4,fill=white,outline=(59,76,113),width=3*S)
 elif name=='gear':
  for a in range(0,360,45):
   pts=[]
   for rr,aa in [(32,a-16),(49,a-12),(49,a+12),(32,a+16)]:pts.append((60+rr*math.cos(math.radians(aa)),60+rr*math.sin(math.radians(aa))))
   poly(pts,fill=white)
  ellipse((23,23,97,97),fill=white);ellipse((43,43,77,77),fill=(29,37,73))
 elif name=='bell':
  rect((32,29,88,84),22,fill=(255,199,54));poly([(32,67),(19,94),(101,94),(88,67)],fill=(255,199,54))
  ellipse((51,99,69,113),fill=(255,199,54));ellipse((52,17,68,33),fill=(255,199,54))
 elif name=='check':line((26,62,48,84,95,33),width=13)
 elif name=='plus':line((25,60,95,60),width=12);line((60,25,60,95),width=12)
 im.save(out/('ui_'+name+'.png'))
# Rounded slider thumb with a subtle shadow.
im=Image.new('RGBA',(128,128));d=ImageDraw.Draw(im);d.ellipse((7,14,123,127),fill=(0,0,0,100));im=im.filter(ImageFilter.GaussianBlur(3));d=ImageDraw.Draw(im)
d.ellipse((5,2,121,118),fill='white');d.ellipse((24,21,102,99),fill=(194,203,214));im.save(out/'slider_knob.png')
# Currency chips and small badges.
for name,color in [('chip',(42,47,83)),('badge',(79,88,110))]:
 im=Image.new('RGBA',(600,144));d=ImageDraw.Draw(im);d.rounded_rectangle((2,2,598,142),70,fill=color,outline=(101,113,145),width=3);im.save(out/('ui_'+name+'.png'))
# Retain the blue right half; make the left segment orange, like the supplied lobby.
blue=Image.open(out/'blue.png').convert('RGBA');w,h=blue.size
orange=Image.new('RGBA',(int(w*.61),h));d=ImageDraw.Draw(orange)
for y in range(h):
 t=y/(h-1);d.line((0,y,orange.width,y),fill=(255,round(178*(1-t)+92*t),round(36*(1-t)+15*t),255))
mask=Image.new('L',orange.size);ImageDraw.Draw(mask).rounded_rectangle((3,3,orange.width-2,h-13),int(h*.29),fill=255)
orange.putalpha(mask);blue.alpha_composite(orange,(0,0));blue.save(out/'bot_button.png')
