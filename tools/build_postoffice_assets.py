"""Regenerate original approved Post Office assets; requires Pillow and an official client JAR.
No runtime plugin dependency. MINECRAFT_CLIENT_JAR can override the local asset source.
"""
from pathlib import Path
from zipfile import ZipFile
from PIL import Image, ImageDraw
import json, io, math, os
ROOT=Path(__file__).resolve().parents[1]
JAR=Path(os.environ.get('MINECRAFT_CLIENT_JAR',str(Path.home()/'AppData/Roaming/.minecraft/versions/26.3/26.3.jar')))
z = ZipFile(JAR)
def asset(path):
    return Image.open(io.BytesIO(z.read('assets/minecraft/' + path))).convert('RGBA')
glyphs = {}
for p in json.loads(z.read('assets/minecraft/font/include/default.json'))['providers']:
    if p['type'] != 'bitmap': continue
    sheet = asset('textures/' + p['file'].split(':')[1])
    rows = p['chars']; cw = sheet.width // len(rows[0]); ch = sheet.height // len(rows)
    scale = p.get('height', 8) / ch
    for row, chars in enumerate(rows):
        for col, char in enumerate(chars):
            if char == '\0': continue
            tile = sheet.crop((col*cw,row*ch,(col+1)*cw,(row+1)*ch))
            bbox = tile.getchannel('A').getbbox()
            advance = math.floor((bbox[2] if bbox else 0)*scale + .5)+1
            tile = tile.resize((round(cw*scale), round(ch*scale)), Image.Resampling.NEAREST)
            glyphs[char] = (tile, advance, 7-p.get('ascent',7))
glyphs[' '] = (Image.new('RGBA',(4,8)),4,0)
colors = {'f':'#FFFFFF','7':'#AAAAAA','e':'#FFFF55','a':'#55FF55','c':'#FF5555','5':'#AA00AA'}
def width(t): return sum(glyphs.get(c,glyphs['?'])[1] for c in t)
def text(im,t,x,y,color='#FFFFFF',shadow=False,italic=False):
    if shadow:
        rgb=tuple(int(color[i:i+2],16)//4 for i in (1,3,5))
        text(im,t,x+1,y+1,'#'+''.join(f'{c:02x}' for c in rgb),False,italic)
    for c in t:
        tile, adv, dy=glyphs.get(c,glyphs['?'])
        tinted=Image.new('RGBA',tile.size,color); tinted.putalpha(tile.getchannel('A'))
        if italic:
            skew=Image.new('RGBA',(tile.width+3,tile.height))
            for ry in range(tile.height): skew.alpha_composite(tinted.crop((0,ry,tile.width,ry+1)),(int((tile.height-ry-1)*.25),ry))
            tinted=skew
        im.alpha_composite(tinted,(int(x),int(y+dy))); x+=adv
def wrap(t,limit):
    lines=[]
    for paragraph in t.split('\n'):
        line=''
        for word in paragraph.split(' '):
            candidate=(line+' '+word).strip()
            if width(candidate)>limit and line: lines.append(line);line=word
            else: line=candidate
        lines.append(line)
    return lines
FONT={
'A':'010101111101101','B':'110101110101110','C':'011100100100011','D':'110101101101110','E':'111100110100111','F':'111100110100100','G':'011100101101011','H':'101101111101101','I':'111010010010111','J':'001001001101010','K':'101101110101101','L':'100100100100111','M':'101111111101101','N':'101111111111101','O':'010101101101010','P':'110101110100100','Q':'010101101111011','R':'110101110101101','S':'011100010001110','T':'111010010010010','U':'101101101101111','V':'101101101101010','W':'101101111111101','X':'101101010101101','Y':'101101010010010','Z':'111001010100111',
'0':'111101101101111','1':'010110010010111','2':'110001010100111','3':'110001010001110','4':'101101111001001','5':'111100110001110','6':'011100111101111','7':'111001010010010','8':'111101111101111','9':'111101111001110',' ':'000000000000000','.':'000000000000010',':':'000010000010000','-':'000000111000000','/':'001001010100100','!':'010010010000010','+':'000010111010000',"'":'010010000000000','?':'110001010000010'}
def tiny(im,s,x,y,c,center=False):
    s=s.upper();x=int(x-((len(s)*4-1)//2 if center else 0));d=ImageDraw.Draw(im)
    for ch in s:
        bits=FONT.get(ch,FONT['?'])
        for i,bit in enumerate(bits):
            if bit=='1': d.point((x+i%3,y+i//3),fill=c)
        x+=4
THEMES=[
dict(id='postoffice',name='Enthusia Post Office',tag='Recommended',bg='#690F10',slot='#901923',slotHi='#BA3841',slotLo='#410B0F',edge='#340A0C',gold='#FFCF1F',hi='#FFE9A8',lo='#B67D22',cream='#FFF1CF',paper='#FFF8E6',ink='#3A0D0A',accent='#B3201A',muted='#E6BF9B',copy='The strongest match to Enthusia: crimson recesses, gilded corners and cream postal panels. Website warmth inside the familiar menu shell.'),
dict(id='stationery',name='Express Stationery',tag='Website preview influence',bg='#142F2C',slot='#214A42',slotHi='#547D65',slotLo='#0C211E',edge='#10211E',gold='#C8A35A',hi='#F3DC9C',lo='#806537',cream='#F5F0E4',paper='#FFFCF4',ink='#223E38',accent='#994A3E',muted='#B8C6A9',copy='The green-and-cream Express website concept, translated into pixel art. Keeps the shared frame, slot spacing and navigation while giving mail its own identity.'),
dict(id='night',name='Night Dispatch',tag='Understated alternative',bg='#1C2028',slot='#2C323E',slotHi='#505866',slotLo='#11151C',edge='#0C1018',gold='#C99B59',hi='#FFE3AB',lo='#785734',cream='#E7DDC8',paper='#F3EAD8',ink='#302A28',accent='#9B403A',muted='#A7ADBA',copy='Charcoal, antique brass and a red wax seal. A quieter option that keeps the same Enthusia structure and postal details.')]
def bevel(im,box,fill,t,light=None,dark=None):
    d=ImageDraw.Draw(im);x,y,x2,y2=map(int,box)
    d.rectangle((x,y,x2,y2),fill=t['edge']);d.rectangle((x+1,y+1,x2-1,y2-1),fill=fill)
    d.line((x+1,y+1,x2-1,y+1),fill=light or t['slotHi']);d.line((x+1,y+1,x+1,y2-1),fill=light or t['slotHi'])
    d.line((x+1,y2-1,x2-1,y2-1),fill=dark or t['slotLo']);d.line((x2-1,y+1,x2-1,y2-1),fill=dark or t['slotLo'])
def fret(im,x,y,t,flipx=False,flipy=False):
    art=Image.new('RGBA',(8,8));d=ImageDraw.Draw(art);g=t['gold'];h=t['hi']
    d.line([(0,7),(0,0),(7,0),(7,4),(3,4),(3,1)],fill=g,width=1)
    d.line([(1,7),(4,7),(4,3),(1,3),(1,1),(6,1)],fill=h,width=1)
    if flipx: art=art.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    if flipy: art=art.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    im.alpha_composite(art,(x,y))
def sprite(kind,t):
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);o=t['edge'];gold=t['gold'];hi=t['hi'];paper=t['paper'];red=t['accent']
    if kind in ('letter','read'):
        d.rectangle((1,3,14,12),fill=o);d.rectangle((2,4,13,11),fill=paper);d.line([(2,4),(7,8),(8,8),(13,4)],fill=t['lo']);d.line([(2,11),(5,8)],fill=t['muted']);d.line([(13,11),(10,8)],fill=t['muted'])
        if kind=='letter':d.rectangle((7,7,9,9),fill=red);d.point((8,7),fill=hi)
        else:d.line([(9,11),(11,13),(15,8)],fill='#69B982',width=2)
    elif kind=='package':
        d.polygon([(1,4),(7,1),(14,4),(14,12),(8,15),(1,12)],fill=o);d.polygon([(2,5),(8,8),(8,14),(2,11)],fill='#B5844F');d.polygon([(8,8),(13,5),(13,11),(8,14)],fill='#8A5B39');d.polygon([(2,4),(7,2),(13,4),(8,7)],fill='#E4BB7A');d.line([(5,3),(11,6),(11,12)],fill=red,width=2);d.line([(5,6),(5,12)],fill=red,width=1);d.point((5,3),fill=hi)
    elif kind=='announcement':
        d.rectangle((3,3,12,11),fill=o);d.rectangle((4,4,11,10),fill=paper);d.line((5,5,10,5),fill=red);d.line((5,7,10,7),fill=t['lo']);d.line((5,9,8,9),fill=t['lo']);d.rectangle((1,2,14,3),fill=gold);d.rectangle((1,11,14,12),fill=gold);d.point((2,2),fill=hi)
    elif kind in ('museum','map'):
        d.rectangle((1,2,14,13),fill=o);d.rectangle((2,3,13,12),fill=gold);d.rectangle((3,4,12,11),fill='#88B7C1');d.polygon([(3,10),(7,5),(10,10)],fill='#4F6D43');d.polygon([(7,10),(10,7),(12,10)],fill='#739B59');d.rectangle((5,10,11,11),fill='#D9BB72');d.point((11,5),fill=hi)
    elif kind in ('sent','back','next'):
        if kind=='sent':
            d.rectangle((1,6,10,13),fill=o);d.rectangle((2,7,9,12),fill=paper);d.line([(2,7),(6,10),(9,7)],fill=t['lo']);d.line([(7,3),(14,3),(11,0)],fill=gold,width=2);d.line([(14,3),(11,6)],fill=gold,width=2)
        else:
            pts=[(11,3),(6,7),(11,11)] if kind=='back' else [(5,3),(10,7),(5,11)];d.line(pts,fill=gold,width=3)
    elif kind in ('check','close'):
        if kind=='check': d.line([(2,8),(6,12),(14,3)],fill='#69B982',width=3);d.line([(2,7),(6,11),(13,3)],fill='#BAF4A5',width=1)
        else: d.line((4,4,11,11),fill=red,width=3);d.line((11,4,4,11),fill=red,width=3)
    elif kind=='settings':
        d.rectangle((3,3,12,12),fill=gold);d.rectangle((6,1,9,14),fill=gold);d.rectangle((1,6,14,9),fill=gold);d.rectangle((5,5,10,10),fill=o);d.rectangle((6,6,9,9),fill=t['slotHi'])
    elif kind=='seal':
        d.polygon([(4,1),(11,1),(14,5),(14,10),(10,14),(4,14),(1,10),(1,5)],fill=o);d.polygon([(5,2),(10,2),(13,5),(13,10),(10,13),(5,13),(2,10),(2,5)],fill=red);d.line((5,4,10,4),fill='#E58A65');d.line((5,6,9,6),fill=hi);d.line((5,6,5,10),fill=hi);d.line((5,8,8,8),fill=hi);d.line((5,10,10,10),fill=hi)
    return im
def icon(im,kind,x,y,t): im.alpha_composite(sprite(kind,t),(x,y))
def card(im,box,label,t,cream=False):
    bevel(im,box,t['cream'] if cream else t['slot'],t,t['paper'] if cream else t['slotHi'],t['lo'] if cream else t['slotLo'])
    if label: tiny(im,label,(box[0]+box[2])//2,box[1]+6,t['ink'] if cream else t['hi'],True)
def base(t,rows,title):
    h=114+rows*18;im=Image.new('RGBA',(176,h),t['bg']);d=ImageDraw.Draw(im)
    d.rectangle((0,0,175,h-1),outline=t['edge'],width=2);d.rectangle((2,2,173,h-3),outline=t['gold']);d.rectangle((4,4,171,14),fill=t['edge'])
    text(im,title,(176-width(title))//2,5,t['hi'])
    for row in range(rows):
        for col in range(9): bevel(im,(7+col*18,17+row*18,24+col*18,34+row*18),t['slot'],t)
    invy=31+18*rows
    d.rectangle((4,invy-13,171,invy-5),fill=t['cream']);d.line((4,invy-13,171,invy-13),fill=t['hi'])
    for row in range(3):
        for col in range(9): bevel(im,(7+col*18,invy-1+row*18,24+col*18,invy+16+row*18),t['slotLo'],t,t['slotHi'],t['edge'])
    for col in range(9):bevel(im,(7+col*18,invy+57,24+col*18,invy+74),t['slotLo'],t,t['slotHi'],t['edge'])
    for x,y,fx,fy in [(1,1,False,False),(167,1,True,False),(1,h-9,False,True),(167,h-9,True,True)]:fret(im,x,y,t,fx,fy)
    return im

OUT=ROOT/'assets/nexo-postoffice'
tex=OUT/'pack/assets/enthusiaexpress/textures/postoffice'
tex.mkdir(parents=True,exist_ok=True)
t=THEMES[0]
backgrounds={}
home=base(t,6,'Enthusia Express')
card(home,(7,17,168,34),'YOUR POST OFFICE',t,True)
for i,label in enumerate(('PACKAGES','LETTERS','NEWS')):
    x=7+i*54;card(home,(x,35,x+53,70),'',t,True);tiny(home,label,x+27,60,t['ink'],True)
card(home,(7,71,168,88),'HANDLE WITH CARE.',t,True)
for i,label in enumerate(('SENT','HELP','MUSEUM')):
    x=7+i*54;card(home,(x,89,x+53,106),'',t);tiny(home,label,x+35,96,t['hi'],True)
card(home,(7,107,132,124),'',t,True);tiny(home,'MY SUBMISSIONS',49,114,t['ink'])
backgrounds['home']=home
for name,title in [('mailbox','Express - Inbox'),('sent','Express - Sent')]:
    im=base(t,6,title)
    for i in range(3):card(im,(7+i*54,17,60+i*54,34),'',t)
    card(im,(7,35,168,52),'',t,True)
    for i,label in enumerate(('PACKAGES','LETTERS','NEWS')):tiny(im,label,34+i*54,37,t['ink'],True)
    tiny(im,'HOVER FOR COUNTS AND PAGE',88,46,t['accent'],True)
    backgrounds[name]=im
catalog=base(t,6,'Mapart Museum');card(catalog,(7,17,168,34),'ARTWORK AND REVIEW',t,True)
card(catalog,(7,35,168,52),'HOVER FOR CREDIT AND STATUS',t,True);backgrounds['catalog']=catalog
backgrounds['museum']=base(t,6,'Express - Details')
shipping=base(t,3,'Express - Postage');card(shipping,(7,17,168,34),'',t,True)
tiny(shipping,'TO',18,24,t['ink']);tiny(shipping,'HOVER FOR DETAILS',132,24,t['ink'],True)
for slot in (11,13,15):
    x=7+(slot%9)*18;bevel(shipping,(x,35,x+17,52),t['cream'],t,t['paper'],t['lo'])
for label,x in [('CANCEL',52),('PLACE HERE',88),('CONFIRM',124)]:tiny(shipping,label,x,57,t['hi'],True)
backgrounds['shipping']=shipping
for name,im in backgrounds.items():
    padded=Image.new('RGBA',(256,256));padded.alpha_composite(im,(0,0));padded.save(tex/('bg-'+name+'.png'))
icons=('letter','read','package','announcement','museum','map','sent','settings','seal','check','close','back','next')
for name in icons:sprite(name,t).save(tex/(name+'.png'))
blank=Image.new('RGBA',(16,16));blank.save(tex/'blank.png')
for baseicon in ('letter','package','announcement'):
    im=sprite(baseicon,t);d=ImageDraw.Draw(im);d.rectangle((0,0,15,15),outline=t['gold']);d.rectangle((1,1,14,14),outline=t['hi']);im.save(tex/(baseicon+'-selected.png'))
glyph_configs=[]
banner=Image.new('RGBA',(176,42),t['bg']);d=ImageDraw.Draw(banner)
d.rectangle((1,1,174,40),outline=t['gold']);card(banner,(5,5,170,36),'',t,True)
icon(banner,'seal',12,12,t);text(banner,'Enthusia Express',33,12,t['ink']);tiny(banner,'HANDLE WITH CARE.',33,24,t['accent'])
banner.save(tex/'bg-banner.png')
glyph_configs.append('ee_postoffice_banner:\n  texture: enthusiaexpress:postoffice/bg-banner\n  font: enthusiaexpress:menus\n  height: 42\n  ascent: 7\n  is_emoji: false\n')
for name in backgrounds:
    glyph_configs.append(f'ee_postoffice_{name}:\n  texture: enthusiaexpress:postoffice/bg-{name}\n  font: enthusiaexpress:menus\n  height: 256\n  ascent: 13\n  is_emoji: false\n')
(OUT/'glyphs/enthusiaexpress').mkdir(parents=True,exist_ok=True)
(OUT/'glyphs/enthusiaexpress/postoffice.yml').write_text('\n'.join(glyph_configs),encoding='utf-8')
itemnames=list(icons)+['blank','letter-selected','package-selected','announcement-selected']
(OUT/'items').mkdir(exist_ok=True)
items=[]
for name in itemnames:
    itemid='ee_postoffice_'+name.replace('-','_')
    items.append(f'{itemid}:\n  material: PAPER\n  excludeFromInventory: true\n  Pack:\n    parent_model: item/generated\n    texture: enthusiaexpress:postoffice/{name}\n')
(OUT/'items/enthusiaexpress_postoffice.yml').write_text('\n'.join(items),encoding='utf-8')
bindings={
'chrome.blank':'blank',
'mailbox.packages':'package','mailbox.packages-selected':'package-selected',
'mailbox.letters':'letter','mailbox.letters-selected':'letter-selected',
'mailbox.announcements':'announcement','mailbox.announcements-selected':'announcement-selected',
'mailbox.previous':'back','mailbox.next':'next','mailbox.inbox':'letter','mailbox.sent':'sent',
'mailbox.page':'map','mailbox.summary':'blank','mailbox.mark-all-read':'check','mailbox.refresh':'settings','mailbox.close':'close',
'mailbox.entry.package':'package','mailbox.entry.letter':'letter','mailbox.entry.letter-read':'read',
'mailbox.entry.announcement':'announcement','mailbox.entry.announcement-read':'read',
'shipping.cancel':'close','shipping.recipient':'letter','shipping.quote':'sent','shipping.confirm':'check','shipping.placeholder':'seal',
}
for key,name in [('packages','package'),('letters','letter'),('announcements','announcement'),('sent','sent'),('settings','settings'),('map','map'),('museum','museum'),('seal','seal'),('check','check'),('back','back'),('next','next'),('control','map')]:bindings['museum.'+key]=name
# Flat paths are easy to merge with Bukkit's ConfigurationSection#set without replacing unrelated settings.
profile={'gui.nexo.enabled':True,'gui.nexo.post-office':True}
profile.update({'gui.nexo.titles.'+name:f'<shift:-8><glyph:ee_postoffice_{name}:shadow:#00000000>' for name in backgrounds})
profile.update({'gui.nexo.icons.'+key:'ee_postoffice_'+value.replace('-','_') for key,value in bindings.items()})
(OUT/'express-profile.json').write_text(json.dumps(profile,indent=2)+'\n',encoding='utf-8')
manifest={str(p.relative_to(OUT)).replace('\\','/'):__import__('hashlib').sha256(p.read_bytes()).hexdigest() for p in OUT.rglob('*') if p.is_file() and p.name!='manifest.json'}
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(f'Generated {len(backgrounds)} background glyphs and {len(itemnames)} original item textures.')
