// Local-only playback review of the delivered file; no external assets or requests.
import { createServer } from 'node:http'
import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const movie = fileURLToPath(new URL('../../artifacts/promo/ClassFlow-promo-1080p.mp4', import.meta.url))
const html = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>ClassFlow · 成片验收</title><style>body{margin:0;background:#eff7ff;color:#153449;font:16px system-ui;text-align:center}header{padding:12px}button{background:#087d73;color:white;border:0;border-radius:10px;padding:10px 20px;font:inherit}video{display:block;max-height:calc(100vh - 105px);max-width:96vw;margin:0 auto;background:white}output{padding:8px;display:block}</style><header><button id="play">播放完整成片</button>　60 秒 · 1080p · 30 fps</header><video id="film" controls preload="auto" src="/video.mp4"></video><output id="status">等待播放</output><script>const film=document.querySelector('#film');document.querySelector('#play').onclick=()=>film.play();film.ontimeupdate=()=>document.querySelector('#status').textContent=film.currentTime.toFixed(2)+' / '+film.duration.toFixed(2)+' 秒';film.onended=()=>document.querySelector('#status').textContent='完整播放结束 · 60 秒';</script></html>`
createServer(async (req,res) => {
  if (req.url === '/') { res.setHeader('Content-Type','text/html;charset=utf-8'); res.end(html); return }
  if (req.url !== '/video.mp4') { res.writeHead(404);res.end();return }
  const {size}=await stat(movie)
  const range=/^bytes=(\d+)-(\d*)$/.exec(req.headers.range ?? '')
  const start=range ? Number(range[1]) : 0
  const end=range?.[2] ? Math.min(Number(range[2]),size-1) : size-1
  if(start> end || start>=size) {res.writeHead(416,{'Content-Range':`bytes */${size}`});res.end();return}
  res.writeHead(range ? 206 : 200,{'Content-Type':'video/mp4','Accept-Ranges':'bytes','Content-Length':end-start+1,...(range?{'Content-Range':`bytes ${start}-${end}/${size}`}:{})})
  createReadStream(movie,{start,end}).pipe(res)
}).listen(4180,'127.0.0.1',()=>console.log('Local playback review: http://127.0.0.1:4180'))
