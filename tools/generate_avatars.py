"""Ten original goofy animal portraits, supersampled vector-style Pillow artwork."""
from PIL import Image, ImageDraw
from pathlib import Path
import math
S=3
out=Path('android/assets/neon');out.mkdir(parents=True,exist_ok=True)
colors=['#f7b34b','#c9996b','#6fc05c','#9da9bb','#ae7ad8','#eab886','#f38a45','#65bfe7','#eee7e4','#f4eee0']
backs=['#413b79','#264760','#235653','#35456e','#513875','#684d58','#5e3c68','#234c75','#334c65','#505b41']
for i in range(10):
 im=Image.new('RGBA',(160*S,160*S));d=ImageDraw.Draw(im)
 def box(b):return tuple(int(v*S) for v in b)
 def el(b,fill,outline=None,w=3):d.ellipse(box(b),fill=fill,outline=outline,width=w*S)
 def rect(b,fill,r=8):d.rounded_rectangle(box(b),r*S,fill=fill)
 def poly(pts,fill):d.polygon([(int(x*S),int(y*S)) for x,y in pts],fill=fill)
 def line(pts,fill='#263249',w=3):d.line([(int(x*S),int(y*S)) for x,y in pts],fill=fill,width=w*S)
 def arc(b,start,end,fill='#263249',w=3):d.arc(box(b),start,end,fill=fill,width=w*S)
 ink='#263249';fur=colors[i]
 el((3,3,157,157),backs[i], '#99dbdf',2)
 # Confetti/sparkles around each character.
 for a in (20,120,215,310):
  x=80+64*math.cos(math.radians(a));y=80+64*math.sin(math.radians(a))
  line([(x-3,y),(x+3,y)],'#b6e7dc',2);line([(x,y-3),(x,y+3)],'#b6e7dc',2)
 el((34,110,134,185),fur,ink)
 if i in (0,6):
  poly([(31,66),(28,17),(67,43)],fur);poly([(95,43),(135,17),(132,71)],fur)
  poly([(37,48),(35,30),(54,46)],'#e98c8c');poly([(107,47),(129,30),(126,51)],'#e98c8c')
 elif i in (1,5,8):
  el((17,30,64,88),fur,ink);el((99,30,145,88),fur,ink)
  if i==8:
   el((17,30,64,88),ink);el((99,30,145,88),ink)
 elif i==3:
  poly([(30,62),(24,20),(66,48)],fur);poly([(98,48),(137,20),(132,69)],fur)
 elif i==7:poly([(62,46),(78,7),(108,54)],fur)
 if i==9:
  el((45,36,116,137),fur,ink)
  poly([(108,83),(147,94),(107,104)],'#f9b644')
  el((73,64,100,92),'white');el((85,72,97,85),ink)
  line([(63,47),(76,39),(83,45)],w=4)
  # backwards cap
  rect((40,32,107,53),'#e95e72');rect((23,44,112,57),'#e95e72')
 else:
  el((26,37,137,140),fur,ink)
  if i==3:
   poly([(29,63),(79,73),(132,61),(126,104),(80,100),(38,105)],ink)
  if i==6:
   poly([(32,88),(80,107),(130,88),(112,127),(80,140),(47,123)],'#fff0d3')
  if i==7:
   el((39,89,126,135),'#eaf6fa')
  if i==8:
   el((39,61,74,101),ink);el((91,61,125,101),ink)
  if i==2:
   el((29,27,78,83),fur,ink);el((86,27,135,83),fur,ink)
  eyes=[(54,75),(108,75)] if i!=2 else [(54,52),(109,52)]
  for j,(x,y) in enumerate(eyes):
   if i==8:arc((x-11,y-4,x+11,y+12),190,345,'#fffaf0',3)
   else:
    el((x-14,y-18,x+14,y+17),'#fff9ef')
    el((x-4+(j*3),y-5,x+5+(j*3),y+6),ink)
    el((x-3+(j*3),y-5,x+(j*3),y-2),'white',w=1)
  if i not in (2,4,7):el((74,94,90,104),ink)
  arc((55,99,111,125),0,170,ink,4)
  if i==5:
   el((31,96,65,123),'#f7d8b3');el((100,96,133,123),'#f7d8b3')
   rect((74,113,83,124),'#fffbea',2);rect((84,113,93,124),'#fffbea',2)
  if i==7:
   poly([(52,105),(61,119),(70,107),(79,123),(89,108),(100,119),(110,103)],'white')
  if i==0:
   # huge sunglasses, gold chain
   for x in (37,91):rect((x,59,x+37,84),'#202a4b',7)
   line([(73,65),(93,65)],'#202a4b',5)
   for x in (43,97):line([(x,64),(x+17,64)],'#79e5f3',3)
   arc((54,123,109,153),0,180,'#ffe394',6)
  if i==1:
   line([(40,50),(122,91)],ink,5);el((94,60,123,89),ink)
   poly([(32,46),(48,18),(105,14),(131,47)],'#353147')
   el((72,23,90,38),'#fff3db');line([(76,38),(69,44)],'#fff3db',2)
  if i==2:
   poly([(43,30),(40,9),(65,20),(82,3),(99,20),(124,9),(119,33)],'#ffcf58')
   el((77,17,87,27),'#e76889')
  if i==3:
   # stolen cookie
   el((99,114,143,153),'#d9a46b',ink,2)
   for x,y in [(112,125),(130,128),(123,144),(108,143)]:el((x-2,y-2,x+2,y+2),'#724d43',w=1)
  if i==4:
   for x in (53,109):el((x-23,50,x+23,98),None,'#ffe398',3)
   line([(76,72),(86,72)],'#ffe398',3)
   poly([(76,93),(89,93),(82,108)],'#ffca68')
   rect((97,119,132,151),'#e3edff',4);arc((120,123,143,144),260,100,'#e3edff',4)
   line([(108,112),(104,105),(110,99)],'#e8edff',2)
  if i==6:
   arc((97,62,124,85),185,345,ink,4)
   rect((34,122,118,132),'#b4e4d1',3)
  if i==8:
   poly([(28,44),(49,10),(114,15),(131,49)],'#8488c3')
   el((44,5,65,24),'#dfd9f7');line([(57,31),(108,31)],'#b7b7e7',5)
 im.resize((320,320),Image.Resampling.LANCZOS).save(out/f'avatar_{i}.png')
print('Generated ten independent avatar PNGs')
