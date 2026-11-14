"""Original icon-only perk controls and the 15-second warning sound."""
from PIL import Image, ImageDraw
from pathlib import Path
import math, wave, struct
out=Path('android/assets/neon');out.mkdir(exist_ok=True,parents=True)
for name in ('undo','discard','hint'):
 im=Image.new('RGBA',(180,180));d=ImageDraw.Draw(im)
 d.rounded_rectangle((4,4,176,176),40,fill=(35,57,96),outline=(105,172,218),width=5)
 if name=='undo':
  d.arc((42,45,143,142),205,510,fill='white',width=14)
  d.polygon([(25,63),(77,37),(70,90)],fill='white')
 elif name=='discard':
  d.rounded_rectangle((44,35,115,132),9,fill=(159,195,226))
  d.rounded_rectangle((61,48,137,151),9,fill='white')
  d.ellipse((82,78,119,109),outline=(47,81,125),width=7)
  d.line((88,113,114,113),fill=(47,81,125),width=5)
 else:
  d.ellipse((54,32,128,108),fill=(255,223,95))
  d.rectangle((73,100,109,125),fill=(255,223,95))
  d.line((73,136,109,136),fill='white',width=9)
  for a in range(0,360,45):
   x=91+math.cos(math.radians(a))*56;y=70+math.sin(math.radians(a))*56
   xx=91+math.cos(math.radians(a))*67;yy=70+math.sin(math.radians(a))*67
   d.line((x,y,xx,yy),fill=(255,223,95),width=5)
 im.save(out/('perk_'+name+'.png'))
Image.new('RGBA',(2,2),'white').save(out/'pixel.png')
audio=Path('android/assets/audio');audio.mkdir(exist_ok=True)
rate=22050
with wave.open(str(audio/'turn_warning.wav'),'wb') as f:
 f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate)
 samples=[]
 for i in range(int(rate*0.6)):
  t=i/rate; phase=t%0.2; env=min(1,phase/0.012)*max(0,min(1,(0.14-phase)/0.025)) if phase<0.14 else 0
  samples.append(struct.pack('<h',int(15000*env*math.sin(2*math.pi*880*t))))
 f.writeframes(b''.join(samples))
