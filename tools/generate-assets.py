"""Reproducible marker + runtime map. Requires Pillow and qrcode; see README."""
from pathlib import Path
import json, math, random, sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.tools/python'))
from PIL import Image, ImageDraw, ImageFont
import qrcode

ASSETS = ROOT / 'app/src/main/assets'
DELIVERY = ROOT / 'delivery'
ASSETS.mkdir(parents=True, exist_ok=True)
DELIVERY.mkdir(exist_ok=True)
PAYLOAD = 'ARAHA:JISIK:2F:START:V1'

# Single reference rectangle with its own boundary: do not use phone-body dimensions.
im = Image.new('RGB', (720, 1080), '#ffffff')
d = ImageDraw.Draw(im)
rng = random.Random(72851)
for _ in range(190):
    x,y = rng.randrange(20,700), rng.randrange(20,1060)
    size = rng.randrange(8,60)
    shade = rng.choice(['#172834','#39536a','#839895','#ed744c','#c0a786'])
    d.polygon([(x,y),(x+size,y+rng.randrange(-35,35)),(x+rng.randrange(-25,25),y+size)],fill=shade)
font_path = Path('C:/Windows/Fonts/arialbd.ttf')
font = ImageFont.truetype(str(font_path),32) if font_path.exists() else ImageFont.load_default()
small = ImageFont.truetype(str(font_path),22) if font_path.exists() else ImageFont.load_default()
d.rectangle((36,38,684,128),fill='white')
d.text((60,62),'ARAHA  /  JISIK 2F',font=font,fill='#172834')
qr=qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M,box_size=12,border=4)
qr.add_data(PAYLOAD);qr.make(fit=True)
q=qr.make_image(fill_color='black',back_color='white').convert('RGB')
im.paste(q,((720-q.width)//2,(1080-q.height)//2))
d.rectangle((36,940,684,1040),fill='white')
d.text((60,962),'TOILET START  /  SCAN ENTIRE SIGN',font=small,fill='#172834')
im.save(ASSETS/'start-marker.png'); im.save(DELIVERY/'start-marker.png')

# Floor origin below the provisional sign centre. All coordinates remain editable.
origin=[686.0,606.0]; scale=0.05170
trace=[
 ('toilet-start',[693,606]),
 ('corridor-turn',[693,650]),
 ('corridor-west',[430,650]),
 ('ballroom-approach',[392,650]),
 ('ballroom-door',[377,680])
]
nodes=[{'id':i,'position':[round((p[0]-origin[0])*scale,5),0,round((p[1]-origin[1])*scale,5)]} for i,p in trace]
edges=[{'id':f'e{n}','fromNodeId':trace[n][0],'toNodeId':trace[n+1][0],
        'halfWidthMeters':0.32 if n == 0 else 0.55} for n in range(len(trace)-1)]
runtime={
 'schemaVersion':2,'floorId':'jisik-2f','floorName':'Jisik · Second floor',
 'calibrationStatus':'Provisional site geometry — verify on location',
 'marker':{'id':PAYLOAD,'referenceImageName':'start-marker.png','physicalWidthMeters':0.060,
           # Opposite the lower toilet doorway: screen faces east toward toilet.
           # Image right is north (-Z), normal east (+X), image down floor -Y.
           'floorFromImage':[0,0,-1,0, 1,0,0,0, 0,-1,0,0, 0,1,0,1]},
 'mapImage':{'asset':'floorplan.jpg','widthPixels':1024,'heightPixels':1024,'originPixel':origin,'metersPerPixel':scale},
 'startNodeId':'toilet-start','nodes':nodes,'edges':edges,
 'rooms':[{'id':'grand-ballroom','label':'Grand Ballroom','destinationNodeId':'ballroom-door','description':'2F · Southern entrance'}],
 'positionSnapMaxMeters':1.0,'arrivalRadiusMeters':0.65
}
(ASSETS/'hotel-floor.json').write_text(json.dumps(runtime,indent=2)+'\n',encoding='utf-8')
(ASSETS/'floorplan.jpg').write_bytes((ROOT/'jisik-2f-clean.jpg').read_bytes())
# Editable SVG calibration overlay, referencing the user's original raster.
points=' '.join(f'{p[0]},{p[1]}' for _,p in trace)
circles=''.join(f'<circle cx="{p[0]}" cy="{p[1]}" r="4"/><text x="{p[0]+8}" y="{p[1]-8}">{i}</text>' for i,p in trace)
svg=f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
<image href="../jisik-2f-clean.jpg" width="1024" height="1024"/>
<polyline points="{points}" fill="none" stroke="#FE6F42" stroke-width="3"/>
<circle cx="{origin[0]}" cy="{origin[1]}" r="5" fill="red"/><path d="M {origin[0]} {origin[1]} h 14" stroke="red" stroke-width="2"/>
<g fill="#374957" font-family="sans-serif" font-size="11">{circles}</g>
<text x="50" y="100" font-family="sans-serif" font-size="24">ARAHA — proposed toilet to ballroom route</text>
<text x="50" y="130" font-family="sans-serif" font-size="14">Provisional coordinates. Validate doorway and scale on site.</text></svg>'''
(DELIVERY/'route-overlay.svg').write_text(svg,encoding='utf-8')
length=sum(math.dist(a['position'],b['position']) for a,b in zip(nodes,nodes[1:]))
print(f'Generated reference marker, runtime graph ({len(nodes)} nodes), route overlay. Route: {length:.2f} m (provisional).')
