"""Compose the 60-second film from authentic screen recordings, without changing UI pixels.

Usage: python scripts/promo/render.py --ffmpeg PATH --ffprobe PATH
Requires Pillow and numpy. Original music is synthesized here; no external music assets.
"""
from pathlib import Path
import argparse, json, math, os, subprocess, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SCRIPT_DIR = Path(__file__).resolve().parent
BUNDLED = (SCRIPT_DIR / 'classflow-icon.png').exists()
W, H, FPS = 1920, 1080, 30
args = argparse.ArgumentParser()
args.add_argument('--ffmpeg', default=os.environ.get('CLASSFLOW_FFMPEG', 'ffmpeg'))
args.add_argument('--ffprobe', default=os.environ.get('CLASSFLOW_FFPROBE', 'ffprobe'))
args.add_argument('--font', default='C:/Windows/Fonts/msyh.ttc')
args.add_argument('--output', type=Path, default=SCRIPT_DIR.parent if BUNDLED else ROOT / 'artifacts/promo')
opt = args.parse_args()
OUT = opt.output.resolve()
OUT.mkdir(parents=True, exist_ok=True)
font_cache = {}
def font(size, bold=False):
    key = (size,bold)
    if key not in font_cache:
        path = Path(opt.font)
        if bold and path.name == 'msyh.ttc': path = path.with_name('msyhbd.ttc')
        font_cache[key] = ImageFont.truetype(str(path), size)
    return font_cache[key]
INK, MUTED, BLUE, TEAL = '#102b40', '#587084', '#0879ce', '#087d73'

def write_text(image, pos, text, size=36, color=INK, bold=False, spacing=16):
    ImageDraw.Draw(image).multiline_text(pos,text,font=font(size,bold),fill=color,spacing=spacing)

yy,xx=np.mgrid[0:H,0:W]
glow=np.exp(-((xx-1580)**2+(yy-280)**2)/(2*680**2))[...,None]
base=np.array([248,251,255])[None,None,:]*(1-glow*.65)+np.array([216,238,255])[None,None,:]*glow*.65
BG=Image.fromarray(base.astype(np.uint8),'RGB')
logo=Image.open(SCRIPT_DIR/'classflow-icon.png' if BUNDLED else ROOT/'android/app/src/main/res/drawable-nodpi/classflow_launcher_icon.png').convert('RGB')

def rounded_asset(image,size,radius):
    asset=image.resize(size,Image.Resampling.LANCZOS).convert('RGBA')
    mask=Image.new('L',size);ImageDraw.Draw(mask).rounded_rectangle((0,0,size[0]-1,size[1]-1),radius,fill=255)
    asset.putalpha(mask);return asset

small_logo=rounded_asset(logo,(62,62),14)
hero_logo=rounded_asset(logo,(350,350),78)

def base_frame(section=None):
    im=BG.copy();d=ImageDraw.Draw(im)
    im.paste(small_logo,(122,66),small_logo)
    write_text(im,(203,70),'ClassFlow',40,bold=True)
    if section:write_text(im,(124,225),section,26,color=TEAL,bold=True)
    write_text(im,(125,1011),'课程 · 日程 · 学习计划',23,color=MUTED)
    d.line((125,982,1795,982),fill='#d7e7f2',width=2)
    return im

def pill(im,xy,text,color=TEAL):
    d=ImageDraw.Draw(im);width=d.textlength(text,font=font(25))+40
    d.rounded_rectangle((xy[0],xy[1],xy[0]+width,xy[1]+48),24,fill='#e4f3f1')
    write_text(im,(xy[0]+20,xy[1]+8),text,25,color=color)

def phone(im,pixels,xy=(1270,126),size=(480,800)):
    x,y=xy;w,h=size;d=ImageDraw.Draw(im)
    d.rounded_rectangle((x-13,y-7,x+w+20,y+h+20),38,fill='#d4e3ef')
    d.rounded_rectangle((x-10,y-12,x+w+10,y+h+12),36,fill='#183349')
    asset=rounded_asset(pixels,size,28);im.paste(asset,(x,y),asset)

def probe(path):
    return json.loads(subprocess.check_output([opt.ffprobe,'-v','error','-show_streams','-show_format','-of','json',str(path)]))

def original_music():
    sr=48000;n=sr*60;music=np.zeros((n,2),dtype=np.float64)
    rng=np.random.default_rng(20261009);beat=.6
    chords=[(48,60,64,67),(45,60,64,69),(41,60,65,69),(43,59,62,67)]
    def add(start,duration,notes,volume,kind='plucked',pan=0):
        offset=int(start*sr);count=min(int(duration*sr),n-offset)
        if count<=0:return
        t=np.arange(count)/sr;signal=np.zeros(count)
        for note in notes:
            f=440*2**((note-69)/12)
            signal+=np.sin(2*np.pi*f*t)+.22*np.sin(4*np.pi*f*t)+.07*np.sin(6*np.pi*f*t)
        signal/=len(notes)
        if kind=='pad':env=np.minimum(t/.2,1)*np.minimum((duration-t)/.35,1)
        else:env=np.minimum(t/.012,1)*np.exp(-t*4.1)*np.minimum((duration-t)/.04,1)
        signal*=env*volume
        music[offset:offset+count,0]+=signal*(.85-pan*.15)
        music[offset:offset+count,1]+=signal*(.85+pan*.15)
    for bar in range(25):
        chord=chords[bar%4];start=bar*4*beat
        add(start,2.6,chord[1:],.14,'pad')
        add(start,1.7,[chord[0]],.18)
        for tick in range(8):
            note=chord[1+((tick+bar)%3)]+(12 if tick in [3,7] else 0)
            add(start+tick*beat/2,.85,[note],.11 if tick%2==0 else .07,pan=(-.6 if tick%2 else .6))
        for tick in range(4):
            offset=int((start+tick*beat)*sr);length=min(3000,n-offset);t=np.arange(length)/sr
            tap=rng.normal(0,1,length)*np.exp(-t*95)*.012
            music[offset:offset+length,:]+=tap[:,None]
    fade=np.minimum(np.arange(n)/sr/1.8,1)*np.minimum((60-np.arange(n)/sr)/2.8,1)
    music*=fade[:,None]
    assert np.abs(music).max()<1
    with wave.open(str(OUT/'original-music.wav'),'wb') as audio:
        audio.setnchannels(2);audio.setsampwidth(2);audio.setframerate(sr)
        audio.writeframes((music*32767).astype('<i2').tobytes())

def web_movie():
    folder=OUT/'raw/web-final';times=json.loads((folder/'timestamps.json').read_text())
    lines=['ffconcat version 1.0']
    for i,item in enumerate(times):
        lines.append("file '"+f'{item["frame"]:05d}.png'+"'")
        lines.append('duration '+str((times[i+1]['time']-item['time']) if i+1<len(times) else .12))
    lines.append("file '"+f'{times[-1]["frame"]:05d}.png'+"'")
    concat=folder/'frames.ffconcat';concat.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    subprocess.run([opt.ffmpeg,'-y','-v','error','-safe','0','-f','concat','-i',str(concat),'-vf','fps=30','-c:v','libx264','-crf','17','-pix_fmt','yuv420p',str(OUT/'raw/web.mp4')],check=True)

class Clip:
    def __init__(self,name,duration,size=(480,800),start=.2,end_trim=0,phone_crop=True):
        path=OUT/'raw'/name;info=probe(path);source=float(info['format']['duration'])
        used=max(.1,source-start-end_trim);effective=min(duration,used)
        self.speed=used/effective;self.start=start;self.size=size;self.last=Image.new('RGB',size,'white')
        self.touches=[]
        trace=path.with_name(path.stem+'-touches.json')
        if trace.exists():self.touches=json.loads(trace.read_text(encoding='utf-8-sig'))
        # Screenrecord is variable-rate: input -ss can discard a held opening frame and jump
        # to the next UI change. Materialize the real held frames before trimming its timeline.
        vf=('crop=1080:1800:0:64,' if phone_crop else '')+f'fps={FPS},trim=start={start:.8f}:duration={used:.8f},setpts={effective/used:.8f}*(PTS-STARTPTS),fps={FPS},scale={size[0]}:{size[1]}'
        self.process=subprocess.Popen([opt.ffmpeg,'-v','error','-i',str(path),'-t',str(duration),'-an','-vf',vf,'-f','rawvideo','-pix_fmt','rgb24','pipe:1'],stdout=subprocess.PIPE)
    def frame(self):
        count=self.size[0]*self.size[1]*3
        data=self.process.stdout.read(count)
        if len(data)==count:self.last=Image.frombytes('RGB',self.size,data)
        return self.last
    def close(self):
        if self.process.poll() is None:
            self.process.terminate()
        self.process.stdout.close();self.process.wait()

def click_hint(im,clip,n,xy=(1270,126)):
    now=clip.start+n/FPS*clip.speed
    for event in clip.touches:
        age=now-event['time']
        if 0<=age<.48:
            x=xy[0]+event['x']*clip.size[0]/1080
            y=xy[1]+(event['y']-64)*clip.size[1]/1800
            r=13+17*age/.48
            ImageDraw.Draw(im).ellipse((x-r,y-r,x+r,y+r),outline='#159cdf',width=4)

def result_detail(im,pixels,box,label):
    crop=pixels.crop(box)
    size=(720,round(720*crop.height/crop.width))
    asset=rounded_asset(crop,size,16)
    im.paste(asset,(126,723),asset)
    ImageDraw.Draw(im).rounded_rectangle((126,723,126+size[0],723+size[1]),16,outline='#9fcde1',width=2)
    pill(im,(126,658),label)

scenes=[
    (5,'intro',None,'学习安排太分散？'),
    (11,'timetable','timetable.mp4','一周课程，一目了然'),
    (11,'agenda','agenda.mp4','作业和考试，集中管理'),
    (14,'study','study.mp4','把学习目标，安排进每一天'),
    (6,'cloud','web.mp4','同步版：通过 Nextcloud 管理'),
    (6,'offline','offline.mp4','离线版：无需服务器，本机使用'),
    (7,'outro',None,'ClassFlow，让学习安排更清晰'),
]

def subtitles():
    at=0;lines=[]
    def time(s):return f'00:00:{int(s):02d},000'
    for i,(duration,_,_,caption) in enumerate(scenes,1):
        lines.append(f'{i}\n{time(at)} --> {time(at+duration)}\n{caption}\n');at+=duration
    # SRT end at the next minute.
    lines[-1]=lines[-1].replace('00:00:60,000','00:01:00,000')
    (OUT/'ClassFlow-promo.srt').write_text('\n'.join(lines),encoding='utf-8-sig')

def main():
    original_music();web_movie();subtitles()
    film=OUT/'ClassFlow-promo-1080p.mp4'
    encoder=subprocess.Popen([opt.ffmpeg,'-y','-v','warning','-f','rawvideo','-pix_fmt','rgb24','-s',f'{W}x{H}','-r',str(FPS),'-i','pipe:0','-i',str(OUT/'original-music.wav'),'-map','0:v','-map','1:a','-c:v','libx264','-preset','medium','-crf','18','-pix_fmt','yuv420p','-c:a','aac','-b:a','192k','-af','loudnorm=I=-18:TP=-1.5:LRA=7','-t','60','-movflags','+faststart',str(film)],stdin=subprocess.PIPE)
    last=None;frame_count=0;details=[]
    overview=[]
    for duration,name,file,caption in scenes:
        clip=None;second_clip=None
        canvas=base_frame()
        if name in ['timetable','agenda','study','offline']:
            subtitle={'timetable':'01 / 每周课表','agenda':'02 / 校园日程','study':'03 / 学习计划','offline':'05 / 独立离线版'}[name]
            canvas=base_frame(subtitle)
            title={'timetable':'一周课程，\n一目了然。','agenda':'作业和考试，\n集中管理。','study':'把学习目标，\n安排进每一天。','offline':'无需服务器，\n打开就能用。'}[name]
            write_text(canvas,(120,327),title,85,bold=True,spacing=22)
            description={'timetable':'查看每天的课程、时间与教室。','agenda':'查看截止时间，完成后轻松勾选。','study':'安排专注时间，关联课程、作业或考试。','offline':'课表、日程和计划，保存在本机。'}[name]
            write_text(canvas,(126,574),description,32,color=MUTED)
            pill(canvas,(126,658),'真实操作录屏')
            if name=='offline':write_text(canvas,(126,743),'独立安装 · 两个版本的数据互不共用',27,color=MUTED)
            end_trim=.2
            if name=='study':
                events=json.loads((OUT/'raw/study-touches.json').read_text(encoding='utf-8-sig'))
                reopen=next((e['time'] for e in events if e['label']=='复习数学第二章'),None)
                if reopen is not None:
                    end_trim=max(0,float(probe(OUT/'raw/study.mp4')['format']['duration'])-reopen+.15)
            clip=Clip(file,3 if name=='offline' else duration,end_trim=end_trim)
        elif name=='cloud':
            write_text(canvas,(124,179),'Android 与网页端',58,bold=True)
            write_text(canvas,(125,262),'同步版通过 Nextcloud 管理',32,color=MUTED)
            clip=Clip(file,duration,size=(1138,640),start=0,phone_crop=False)
            second_clip=Clip('study.mp4',duration,size=(320,533),start=max(0,float(probe(OUT/'raw/study.mp4')['format']['duration'])-7),end_trim=.2)
        elif name=='intro':
            write_text(canvas,(125,301),'学习安排\n太分散？',110,bold=True,spacing=24)
            write_text(canvas,(132,628),'课程、作业、考试与复习计划，\n放在同一个地方。',37,color=MUTED,spacing=20)
        else:
            write_text(canvas,(125,315),'让学习安排，\n更清晰。',108,bold=True,spacing=25)
            write_text(canvas,(130,624),'ClassFlow',53,color=BLUE,bold=True)
            pill(canvas,(130,710),'Android · Nextcloud · 独立离线版')
            write_text(canvas,(133,800),'github.com/hmgvibe/ClassFlow',32,color=MUTED)
        start_frame=frame_count;previous=last
        for n in range(duration*FPS):
            im=canvas.copy();d=ImageDraw.Draw(im)
            if clip:
                pixels=clip.frame()
                if name=='cloud':
                    d.rounded_rectangle((586,316,1782,981),24,fill='#d5e5ef')
                    asset=rounded_asset(pixels,(1138,640),16);im.paste(asset,(610,329),asset)
                    phone(im,second_clip.frame(),(168,360),(320,533))
                    pill(im,(608,908),'两端界面演示 · 示例数据')
                elif name=='offline' and n>=3*FPS:
                    phone(im,widget.frame())
                else:
                    phone(im,pixels)
                    click_hint(im,clip,n)
                    if name=='study':
                        now=clip.start+n/FPS*clip.speed
                        saves=[e['time'] for e in clip.touches if e['label']=='保存']
                        opens=[e['time'] for e in clip.touches if e['label']=='复习数学第二章']
                        if saves and saves[0]+.7<now<(opens[0] if opens else 999):
                            result_detail(im,pixels,(5,548,475,699),'已保存 · 实际页面结果')
            else:
                float_y=int(12*math.sin(n/FPS*1.15))
                im.paste(hero_logo,(1270,322+float_y),hero_logo)
                if name=='intro':
                    for k,text in enumerate(['课程','作业','考试','学习计划']):
                        y=295+k*117
                        x=950+int(10*math.sin(n/FPS+k))
                        d.rounded_rectangle((x,y,x+230,y+78),22,fill='white',outline='#d4e7f2',width=2)
                        write_text(im,(x+27,y+19),text,32,color=TEAL,bold=True)
            if previous is not None and n<9:im=Image.blend(previous,im,(n+1)/9)
            if n==int(duration*FPS*.55):
                im.save(OUT/f'frame-{name}.jpg',quality=92);overview.append(im.resize((480,270)))
            encoder.stdin.write(im.tobytes());frame_count+=1;last=im
            if name=='offline' and n==3*FPS-1:widget=Clip('widgets.mp4',3,start=0,end_trim=0)
        if name=='offline':widget.close()
        if clip:clip.close()
        if second_clip:second_clip.close()
        details.append({'scene':name,'start':start_frame/FPS,'duration':duration,'source':file,'speed':None if clip is None else round(clip.speed,3)})
        print('Rendered',name,frame_count,'frames',flush=True)
    encoder.stdin.close();assert encoder.wait()==0
    assert frame_count==1800
    cover=base_frame();write_text(cover,(120,318),'让学习安排，\n更清晰。',103,bold=True,spacing=28)
    write_text(cover,(128,607),'课表 · 作业考试 · 学习计划',36,color=MUTED)
    pill(cover,(128,696),'ClassFlow · 真实操作介绍')
    preview=Clip('timetable.mp4',1,start=1, end_trim=max(0,float(probe(OUT/'raw/timetable.mp4')['format']['duration'])-2))
    phone(cover,preview.frame());preview.close();cover.save(OUT/'ClassFlow-cover.png')
    sheet=Image.new('RGB',(1920,540),'white')
    for i,im in enumerate(overview):sheet.paste(im,((i%4)*480,(i//4)*270))
    sheet.save(OUT/'contact-sheet.jpg',quality=94)
    manifest={'duration':60,'resolution':[W,H],'fps':FPS,'frames':frame_count,'scenes':details,'click_hints':'Coordinates and timestamps recorded from actual Compose touch interactions.','web_source':'Production Vue components with isolated in-memory demo API. No actual Nextcloud sync success is depicted.','android_source':'Production MainActivity, Room repository, and real launcher widget on ClassFlow_Promo AVD.','music':'Original procedural instrumental, 100 BPM, generated by render.py.','repository':'https://github.com/hmgvibe/ClassFlow'}
    (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'ffprobe.json').write_text(json.dumps(probe(film),ensure_ascii=False,indent=2),encoding='utf-8')
    subprocess.run([opt.ffmpeg,'-v','error','-i',str(film),'-f','null','-'],check=True)
    print('Complete:',film,flush=True)

if __name__=='__main__':main()
