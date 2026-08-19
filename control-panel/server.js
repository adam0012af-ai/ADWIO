const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const PORT = Number(process.env.PORT || 8787);
const ADMIN_TOKEN = process.env.ADWIO_ADMIN_TOKEN || '';
const DB = path.join(__dirname, 'data', 'telemetry.json');
const ONLINE_MS = 3 * 60 * 1000;

function load(){ try { return JSON.parse(fs.readFileSync(DB,'utf8')); } catch { return {devices:{}}; } }
function save(db){ fs.mkdirSync(path.dirname(DB),{recursive:true}); fs.writeFileSync(DB, JSON.stringify(db,null,2)); }
function json(res, code, body){ res.writeHead(code, {'content-type':'application/json; charset=utf-8','cache-control':'no-store'}); res.end(JSON.stringify(body)); }
function body(req){ return new Promise((ok,bad)=>{ let s=''; req.on('data',d=>{s+=d; if(s.length>65536) req.destroy();}); req.on('end',()=>{try{ok(JSON.parse(s||'{}'))}catch(e){bad(e)}});}); }
function safeHost(v){ return String(v||'').replace(/[^a-zA-Z0-9.:-]/g,'').slice(0,180); }
function authed(req){ if(!ADMIN_TOKEN) return false; const a=Buffer.from(String(req.headers.authorization||'').replace(/^Bearer\s+/i,'')); const b=Buffer.from(ADMIN_TOKEN); return a.length===b.length && crypto.timingSafeEqual(a,b); }

const server=http.createServer(async(req,res)=>{
  if(req.method==='POST' && req.url==='/v1/heartbeat'){
    try{
      const b=await body(req); const id=String(b.installationId||'').slice(0,80); if(!id) return json(res,400,{ok:false});
      const db=load(), now=Date.now(), old=db.devices[id]||{};
      db.devices[id]={host:safeHost(b.host),playlistType:b.playlistType==='M3U'?'M3U':'XTREAM',appVersion:String(b.appVersion||'').slice(0,30),androidVersion:String(b.androidVersion||'').slice(0,30),device:String(b.device||'').slice(0,80),firstSeen:old.firstSeen||now,lastSeen:now};
      save(db); return json(res,200,{ok:true});
    }catch{return json(res,400,{ok:false});}
  }
  if(req.url==='/api/dashboard'){
    if(!authed(req)) return json(res,401,{error:'unauthorized'});
    const db=load(), now=Date.now(), devices=Object.values(db.devices||{}), online=devices.filter(x=>now-x.lastSeen<=ONLINE_MS);
    const hosts={}; for(const d of devices){ const h=d.host||'Unknown'; hosts[h] ||= {host:h,total:0,online:0,xtream:0,m3u:0,lastSeen:0}; hosts[h].total++; if(now-d.lastSeen<=ONLINE_MS) hosts[h].online++; if(d.playlistType==='M3U') hosts[h].m3u++; else hosts[h].xtream++; hosts[h].lastSeen=Math.max(hosts[h].lastSeen,d.lastSeen); }
    return json(res,200,{online:online.length,totalDevices:devices.length,activeToday:devices.filter(x=>now-x.lastSeen<=86400000).length,hosts:Object.values(hosts).sort((a,b)=>b.online-a.online||b.lastSeen-a.lastSeen),versions:Object.entries(devices.reduce((a,d)=>(a[d.appVersion]=(a[d.appVersion]||0)+1,a),{})).map(([version,count])=>({version,count}))});
  }
  if(req.url==='/' || req.url==='/index.html'){
    const html=fs.readFileSync(path.join(__dirname,'public','index.html')); res.writeHead(200,{'content-type':'text/html; charset=utf-8'}); return res.end(html);
  }
  json(res,404,{error:'not_found'});
});
server.listen(PORT,()=>console.log(`ADWIO Control Panel API on :${PORT}`));
