# Independent writer of the golden packs, made from PACKS.md (not from PackWriter).
# Usage: python3 golden.py <dir with console.pack and arcade.pack> [write|hex]
import struct, hashlib, sys
REG=["USA","Europe","Japan","World","Korea","China","Taiwan","Hong Kong","Asia","Brazil","Australia","Canada","UK","France","Germany","Spain","Italy","Netherlands","Sweden","Scandinavia","Russia","Belgium","Denmark","Finland","Greece","India","Latin America","Mexico","New Zealand","Norway","Poland","Portugal"]
LANG=["En","Ja","Fr","De","Es","It","Nl","Pt","Sv","No","Da","Fi","Zh","Ko","Pl","Ru","El","Ca","Hu","Cs","Tr","Ar","He","Hr","Ro","Sk","Sl","Uk","Bg","Id","Th","Vi"]
NONE=0xFFFFFFFF
class Pool:
    def __init__(s): s.b=bytearray(); s.o={}
    def ref(s,t):
        if t in s.o: return s.o[t]
        e=t.encode(); off=len(s.b); s.b+=struct.pack('<H',len(e))+e; s.o[t]=off; return off
def mask(names,table): 
    m=0
    for n in names: m|=1<<table.index(n)
    return m
def assemble(kind,sysid,ver,secs):
    h=b'PLMPACK\0'+struct.pack('<HBBI',1,kind,0,ver)+sysid.encode().ljust(16,b'\0')+struct.pack('<HH',len(secs),0)
    off=36+12*len(secs); table=b''; body=bytearray(h)
    offs=[]
    for tag,b in secs:
        off=(off+3)&~3; table+=tag.encode()+struct.pack('<II',off,len(b)); offs.append(off); off+=len(b)
    body+=table
    for (tag,b),o in zip(secs,offs):
        body+=b'\0'*(o-len(body)); body+=b
    body+=b'\0'*((-len(body))%4)
    return bytes(body)+hashlib.sha256(body).digest()
def console(sysid,ver,sources,titles,releases):
    order=sorted(range(len(titles)),key=lambda i:(titles[i]['name'].lower().encode(),i))
    new={old:n for n,old in enumerate(order)}
    rorder=sorted(range(len(releases)),key=lambda i:(new[releases[i]['title']],i))
    p=Pool()
    first={};cnt={}
    for pos,old in enumerate(rorder):
        t=new[releases[old]['title']]; first.setdefault(t,pos); cnt[t]=cnt.get(t,0)+1
    tb=b''
    for n,old in enumerate(order):
        t=titles[old]; nm=p.ref(t['name']); pb=p.ref(t['pub']) if t.get('pub') else NONE
        tb+=struct.pack('<IIHHII',nm,pb,t.get('year') or 0,0,first.get(n,0),cnt.get(n,0))
    rb=b''; crcs=[]; codes=[]
    for pos,old in enumerate(rorder):
        r=releases[old]; v=p.ref(r['variant']) if r.get('variant') else NONE
        rb+=struct.pack('<IIIIB3x',new[r['title']],mask(r.get('regions',[]),REG),mask(r.get('langs',[]),LANG),v,r.get('src',0xFF))
        for c,s in r.get('crcs',[]): crcs.append((c, NONE if s is None else s, pos))
        for c,k in r.get('codes',[]): codes.append((c.encode(),k,pos))
    crcs.sort(); codes.sort()
    cb=b''.join(struct.pack('<III',*x) for x in crcs)
    db=b''.join(struct.pack('<IB3xI',p.ref(c.decode()),k,pos) for c,k,pos in codes)
    sb=struct.pack('<I',len(sources))
    for n,v in sources: sb+=struct.pack('<II',p.ref(n),p.ref(v))
    return assemble(1,sysid,ver,[('STRS',bytes(p.b)),('SRCS',sb),('TITL',tb),('RELS',rb),('CRCS',cb),('CODE',db)])
def arcade(sysid,ver,sources,sets):
    s=sorted(sets,key=lambda x:x['short'].encode()); idx={x['short']:i for i,x in enumerate(s)}
    p=Pool(); ab=b''
    for x in s:
        a=p.ref(x['short']); b=p.ref(x['title']); m=p.ref(x['maker']) if x.get('maker') else NONE
        flags=x.get('flags',0)|(8 if x.get('parent') else 0)
        par=idx.get(x.get('parent'),-1) if x.get('parent') else -1
        ab+=struct.pack('<IIIHHiB3x',a,b,m,x.get('year') or 0,flags,par,x.get('src',0xFF))
    sb=struct.pack('<I',len(sources))
    for n,v in sources: sb+=struct.pack('<II',p.ref(n),p.ref(v))
    return assemble(2,sysid,ver,[('STRS',bytes(p.b)),('SRCS',sb),('ARCD',ab)])
# Inputs. They must match PackGoldenTest.goldenConsole() and goldenArcade() in the same order.
# Titles chosen so the canonical order (UTF-8 bytes of the lower-cased name) differs from the
# order of the input and from a case-sensitive sort: "alpha quest" sorts before "Beta Quest" but
# a case-sensitive sort puts every capital first; "Zork" and "ZORK" have equal keys and keep
# input order; the e with an acute accent is two UTF-8 bytes.
TITLES=[dict(name='Zork Quest II',year=1991,pub='Testsoft'),dict(name='Moonlit Harbor'),
        dict(name='alpha quest'),dict(name='Beta Quest'),dict(name='Zork'),dict(name='ZORK'),
        dict(name='Pok\u00e9 Zork')]
c=console('testsys',20261021,[('Test DAT','2026-10-21')],TITLES,
  [dict(title=0,regions=['USA'],langs=['En'],src=0,crcs=[(0x1234ABCD,524288)],codes=[('ZZZE',1)]),
   dict(title=0,regions=['Europe'],langs=['En','Fr'],variant='Rev 1',src=0,crcs=[(0xF0000001,None)]),
   dict(title=1,regions=['Japan'],langs=['Ja']),
   dict(title=2,regions=['Europe']),dict(title=3,regions=['USA']),
   dict(title=4,regions=['USA']),dict(title=5,regions=['World']),dict(title=6,regions=['France'])])
a=arcade('arcade',20261021,[('MAME software lists','0.270')],[
  dict(short='zorkfghtj',title='Zork Fighters (Japan)',maker='Testsoft',year=1996,parent='zorkfght',src=0),
  dict(short='zorkfght',title='Zork Fighters',maker='Testsoft',year=1996,src=0),
  dict(short='testbios',title='Test System BIOS',flags=1,src=0)])
root=sys.argv[1]
if len(sys.argv)>2 and sys.argv[2]=='write':
    open(root+'/console.pack','wb').write(c); open(root+'/arcade.pack','wb').write(a)
    print('written')
print('console', open(root+'/console.pack','rb').read()==c)
print('arcade', open(root+'/arcade.pack','rb').read()==a)
if len(sys.argv)>2 and sys.argv[2]=='hex':
    print(c.hex()); print(a.hex())
