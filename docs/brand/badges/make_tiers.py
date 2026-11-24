import sys, colorsys, math
from PIL import Image
import numpy as np
METALS = {  # dark, mid, light
 'bronze': [(0x8A,0x4B,0x22),(0xC2,0x7A,0x3E),(0xE9,0xB0,0x7A)],
 'silver': [(0x86,0x8D,0x97),(0xB9,0xC0,0xC8),(0xEC,0xEF,0xF2)],
 'gold':   [(0xC9,0x8A,0x06),(0xF4,0xBE,0x2A),(0xFF,0xE7,0x8C)],
}
def ramp(stops, t):
    t=max(0,min(1,t))
    if t<.5: a,b,u=stops[0],stops[1],t/.5
    else: a,b,u=stops[1],stops[2],(t-.5)/.5
    return [a[i]+(b[i]-a[i])*u for i in range(3)]
def face_radius(a, cx, cy, R):
    # walk inward along rays from rim; first near-white run marks the face
    rs=[]
    for k in range(72):
        th=2*math.pi*k/72
        for r in np.arange(R*0.98, R*0.5, -1):
            x,y=int(cx+r*math.cos(th)), int(cy+r*math.sin(th))
            p=a[y,x]
            if p[3]>200 and p[0]>235 and p[1]>230 and p[2]>225: rs.append(r); break
    return float(np.median(rs))
def run(src, outs, icon=False):
    im=Image.open(src).convert('RGBA'); a=np.array(im).astype(float)
    ys,xs=np.nonzero(a[:,:,3]>128)
    cx,cy=(xs.min()+xs.max())/2,(ys.min()+ys.max())/2; R=(xs.max()-xs.min()+ys.max()-ys.min())/4
    rf=face_radius(a.astype(int),cx,cy,R)
    H,W=a.shape[:2]; yy,xx=np.mgrid[0:H,0:W]; d=np.hypot(xx-cx,yy-cy)
    ring=d>rf-2
    rgb=a[:,:,:3]/255; mx=rgb.max(2); mn=rgb.min(2); L=(mx+mn)/2; sat=(mx-mn)
    # hue
    r,g,b=rgb[:,:,0],rgb[:,:,1],rgb[:,:,2]
    hue=np.degrees(np.arctan2(math.sqrt(3)*(g-b),2*r-g-b))%360
    orange=(ring|icon)&(sat>0.25)&(hue>8)&(hue<50)&(mx>0.55)
    # map orange lightness to metal ramp: brand orange L~0.55 -> mid
    t=np.clip((L-0.38)/0.42,0,1)
    for name,path in outs.items():
        o=a.copy(); st=METALS[name]
        for i in range(3):
            lo,mid,hi=st[0][i],st[1][i],st[2][i]
            ch=np.where(t<.5, lo+(mid-lo)*(t/.5), mid+(hi-mid)*((t-.5)/.5))
            o[:,:,i]=np.where(orange,ch,a[:,:,i])
        Image.fromarray(o.clip(0,255).astype('uint8')).save(path)
    return rf/R
if __name__=='__main__':
    src=sys.argv[1]; base=sys.argv[2]
    print(src, run(src,{m:f'{base}_{m}.png' for m in METALS}))
