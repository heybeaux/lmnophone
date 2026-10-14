import sys,struct
b=sys.stdin.buffer.read(); o=0
def i(): 
    global o; v=struct.unpack('>i',b[o:o+4])[0]; o+=4; return v
def l():
    global o; v=struct.unpack('>q',b[o:o+8])[0]; o+=8; return v
def z():
    global o; v=b[o]; o+=1; return v
def u():
    global o; n=struct.unpack('>H',b[o:o+2])[0]; o+=2; s=b[o:o+n]; o+=n; return s
ver=i(); rec=z()
if rec: u();u()
n=i(); days=[]
for _ in range(n):
    u();u();d=l();l();u();k=i();[i() for _ in range(k)];days.append(d)
rm=[]
if ver>=2:
    for _ in range(i()): u(); rm.append(l())
print(f"format={ver} recipient={bool(rec)} requests={n} request_days={days} removed={len(rm)} removed_days={rm} trailing={len(b)-o}")
