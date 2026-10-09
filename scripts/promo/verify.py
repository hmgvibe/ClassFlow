"""Check delivered encoding, timing, streaming layout, decoding and audio levels."""
import argparse, json, struct, subprocess
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--ffmpeg',default='ffmpeg')
p.add_argument('--ffprobe',default='ffprobe')
script_dir=Path(__file__).resolve().parent
p.add_argument('--output',type=Path,default=script_dir.parent if (script_dir/'classflow-icon.png').exists() else script_dir.parents[1]/'artifacts/promo')
a=p.parse_args();out=a.output.resolve();movie=out/'ClassFlow-promo-1080p.mp4'
probe=json.loads(subprocess.check_output([a.ffprobe,'-v','error','-show_streams','-show_format','-of','json',str(movie)]))
v=next(x for x in probe['streams'] if x['codec_type']=='video')
audio=next(x for x in probe['streams'] if x['codec_type']=='audio')
assert (v['codec_name'],v['width'],v['height'],v['pix_fmt'],v['avg_frame_rate'],v['nb_frames'])==('h264',1920,1080,'yuv420p','30/1','1800')
assert audio['codec_name']=='aac' and audio['channels']==2
assert float(v['duration'])==60 and float(audio['duration'])==60
assert float(v['start_time'])==float(audio['start_time'])==0
atoms=[]
with movie.open('rb') as f:
    while f.tell()<movie.stat().st_size:
        at=f.tell();size,kind=struct.unpack('>I4s',f.read(8))
        if size==1:size=struct.unpack('>Q',f.read(8))[0]
        if size==0:size=movie.stat().st_size-at
        atoms.append({'type':kind.decode('ascii'),'offset':at,'size':size})
        f.seek(at+size)
assert next(x['offset'] for x in atoms if x['type']=='moov')<next(x['offset'] for x in atoms if x['type']=='mdat')
subprocess.run([a.ffmpeg,'-v','error','-i',str(movie),'-f','null','-'],check=True)
levels=subprocess.run([a.ffmpeg,'-hide_banner','-nostats','-i',str(movie),'-vn','-af','ebur128=peak=true:framelog=verbose','-f','null','-'],capture_output=True,text=True,check=True).stderr
(out/'audio-analysis.log').write_text(levels,encoding='utf-8')
black=subprocess.run([a.ffmpeg,'-hide_banner','-nostats','-i',str(movie),'-an','-vf','blackdetect=d=0.1:pix_th=0.04','-f','null','-'],capture_output=True,text=True,check=True).stderr
assert 'black_start:' not in black
(out/'video-analysis.log').write_text(black,encoding='utf-8')
report={'video':{key:v[key] for key in ('codec_name','width','height','pix_fmt','avg_frame_rate','nb_frames','duration','start_time')},'audio':{key:audio[key] for key in ('codec_name','channels','duration','start_time','sample_rate')},'faststart':True,'atoms':atoms,'full_decode':'passed','black_intervals':0,'audio_summary':levels[levels.rfind('Summary:'):].strip()}
(out/'encoding-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
