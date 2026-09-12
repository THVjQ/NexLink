"""Independent reader for the §7.5.2 archive — deliberately not the app's code.

Decrypting with a separate implementation proves the format is actually
specified by its header rather than by whatever the writer happened to do.
Uses only hashlib (PBKDF2) plus a small pure-Python AES-GCM, so it needs no
third-party library and can be re-run anywhere.
"""
import hashlib, struct, sys, io, zipfile

def read_header(b):
    o = 0
    n = struct.unpack_from(">H", b, o)[0]; o += 2
    magic = b[o:o+n].decode(); o += n
    version    = struct.unpack_from(">i", b, o)[0]; o += 4
    iterations = struct.unpack_from(">i", b, o)[0]; o += 4
    sl = struct.unpack_from(">i", b, o)[0]; o += 4
    salt = b[o:o+sl]; o += sl
    nl = struct.unpack_from(">i", b, o)[0]; o += 4
    nonce = b[o:o+nl]; o += nl
    return magic, version, iterations, salt, nonce, o

# ---- minimal AES + GCM ----------------------------------------------------
SBOX=[0]*256; INV=[0]*256
p=q=1
while True:
    p = p ^ ((p<<1)&0xff) ^ (0x1b if p&0x80 else 0)
    q ^= q<<1; q ^= q<<2; q ^= q<<4; q&=0xff
    if q&0x80: q ^= 0x09
    x = q ^ ((q<<1)|(q>>7)) ^ ((q<<2)|(q>>6)) ^ ((q<<3)|(q>>5)) ^ ((q<<4)|(q>>4))
    SBOX[p] = x&0xff ^ 0x63
    if p==1: break
SBOX[0]=0x63

def xtime(a): return ((a<<1)^0x1b)&0xff if a&0x80 else a<<1
def key_expand(key):
    nk=len(key)//4; nr=nk+6
    w=[list(key[4*i:4*i+4]) for i in range(nk)]
    rcon=1
    for i in range(nk,4*(nr+1)):
        t=list(w[i-1])
        if i%nk==0:
            t=t[1:]+t[:1]; t=[SBOX[x] for x in t]; t[0]^=rcon
            rcon=xtime(rcon)
        elif nk>6 and i%nk==4:
            t=[SBOX[x] for x in t]
        w.append([w[i-nk][j]^t[j] for j in range(4)])
    return w,nr

def encrypt_block(block,w,nr):
    s=[list(block[i::4]) for i in range(4)]
    def addrk(r):
        for c in range(4):
            for j in range(4): s[j][c]^=w[r*4+c][j]
    addrk(0)
    for rnd in range(1,nr+1):
        for r in range(4):
            for c in range(4): s[r][c]=SBOX[s[r][c]]
        for r in range(1,4): s[r]=s[r][r:]+s[r][:r]
        if rnd!=nr:
            for c in range(4):
                a=[s[r][c] for r in range(4)]
                t=a[0]^a[1]^a[2]^a[3]
                for r in range(4):
                    s[r][c]^= t ^ xtime(a[r]^a[(r+1)%4])
        addrk(rnd)
    return bytes(s[r][c] for c in range(4) for r in range(4))

def gmul(x,y):
    z=0
    for i in range(127,-1,-1):
        if (y>>i)&1: z^=x
        lsb=x&1; x>>=1
        if lsb: x^=0xe1<<120
    return z

def ghash(h,data):
    y=0
    for i in range(0,len(data),16):
        blk=data[i:i+16].ljust(16,b"\0")
        y=gmul(y^int.from_bytes(blk,"big"),h)
    return y

def gcm_decrypt(key,nonce,aad,ct,tag):
    w,nr=key_expand(key)
    H=int.from_bytes(encrypt_block(b"\0"*16,w,nr),"big")
    j0=nonce+b"\0\0\0\1"
    def inc(b):
        c=int.from_bytes(b[12:],"big")+1
        return b[:12]+ (c & 0xffffffff).to_bytes(4,"big")
    out=bytearray(); ctr=j0
    for i in range(0,len(ct),16):
        ctr=inc(ctr)
        ks=encrypt_block(ctr,w,nr)
        blk=ct[i:i+16]
        out+=bytes(a^b for a,b in zip(blk,ks))
    s=ghash(H, aad+b"\0"*((-len(aad))%16) + ct+b"\0"*((-len(ct))%16)
              + (len(aad)*8).to_bytes(8,"big") + (len(ct)*8).to_bytes(8,"big"))
    t=s ^ int.from_bytes(encrypt_block(j0,w,nr),"big")
    if t.to_bytes(16,"big")!=tag:
        raise ValueError("GCM tag mismatch — wrong passphrase or tampered file")
    return bytes(out)

def main():
    path, passphrase = sys.argv[1], sys.argv[2]
    b=open(path,"rb").read()
    magic,version,iters,salt,nonce,off = read_header(b)
    print(f"  magic={magic} version={version} iterations={iters} salt={len(salt)}B nonce={len(nonce)}B")
    key=hashlib.pbkdf2_hmac("sha256", passphrase.encode(), salt, iters, 32)
    aad = magic.encode() + bytes([version]) + str(iters).encode() + salt + nonce
    body=b[off:]
    ct,tag = body[:-16], body[-16:]
    print(f"  deriving key ({iters} iterations)…")
    plain=gcm_decrypt(key,nonce,aad,ct,tag)
    print("  GCM tag verified.")
    z=zipfile.ZipFile(io.BytesIO(plain))
    names=z.namelist()
    print(f"  {len(names)} entries recovered")
    for n in names[:6]: print("    ", n)
    crypto=[n for n in names if "crypto" in n]
    if crypto:
        d=z.read(crypto[0])
        print(f"  {crypto[0]}: {len(d)} bytes, starts {d[:16]!r}")

main()
