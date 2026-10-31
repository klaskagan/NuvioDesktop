const {app, components, BrowserWindow, session, screen} = require('electron');
const fs = require('node:fs');
const readline = require('node:readline');
const path = require('node:path');
const profile = process.env.NUVIO_GO3_PROBE_DIR;
if (!profile) process.exit(2);
const sessionRoot=process.env.NUVIO_GO3_SESSION_DIR;
if(sessionRoot) {fs.mkdirSync(sessionRoot,{recursive:true,mode:0o700});fs.chmodSync(sessionRoot,0o700);}
app.setName('Go3');
if(process.platform==='darwin') app.setActivationPolicy('accessory');
app.setPath('userData', path.join(sessionRoot || profile, 'runtime'));
const emit = event => { const line=JSON.stringify({at:Math.floor(Date.now()/1000),...event})+'\n'; fs.appendFileSync(path.join(profile,'evidence.jsonl'),line,{mode:0o600}); process.stdout.write(line); };
let player, catalog, busy = false;
let drmReady;
let catalogueQueue=Promise.resolve();
let readyResolve; const runtimeReady=new Promise(r=>readyResolve=r);
const {collectEvents}=require('./event-catalog.cjs');
const {dismissFalseAutoplayNotice,activateOfficialWatch}=require('./player-page.cjs');
const entries = new Map();
const {chooseLive} = require('./discovery.cjs');
const {discoverEntries} = require('./catalog.cjs');
async function discover() {
  if(busy || !catalog || catalog.isDestroyed()) return; entries.clear(); busy=true; emit({kind:'status',message:'Discovering official Go3 sports listings…'});
  try {
    for(const entry of await discoverEntries(catalog)) entries.set(entry.id+'|'+entry.title,entry);
    emit({kind:'catalog',entries:[...entries.values()]});
    emit({kind:'status',message:`Found ${entries.size} event/channel entries. “Listed” means Go3 did not expose a live badge.`});
  } catch { emit({kind:'status',message:'Discovery failed; refresh to retry. No supplied event URL was inserted into results.'}); }
  finally {busy=false;}
}
function present(win) {if(win.isMinimized())win.restore();win.show();win.focus();}
function createWindow(show, role="player") {
  const bounds=role==='player'?screen.getDisplayNearestPoint(screen.getCursorScreenPoint()).workArea:{width:1500,height:950};
  const win=new BrowserWindow({show:false,...bounds,backgroundColor:'#000000',title:process.env.NUVIO_GO3_SERVICE?'Nuvio — Go3':'Nuvio — Go3 live test',webPreferences:{partition:sessionRoot?'persist:nuvio-go3-live-test':'nuvio-go3-live',sandbox:true,contextIsolation:true,nodeIntegration:false,zoomFactor:role==="catalog"?0.5:1,autoplayPolicy:role==="player"?"no-user-gesture-required":"document-user-activation-required"}});
  if(role==='player') win.webContents.on('did-finish-load',()=>{
    const deadline=win.go3WatchDeadline||0;
    win.webContents.executeJavaScript(`(()=>{let activated=false;const check=()=>{if(!activated && Date.now()<${deadline})activated=Array.from(document.querySelectorAll('video')).some(v=>!v.paused && v.readyState>=2)||(${activateOfficialWatch.toString()})(document);(${dismissFalseAutoplayNotice.toString()})(document);};const observer=new MutationObserver(check);observer.observe(document.documentElement,{childList:true,subtree:true});document.addEventListener('playing',check,true);document.addEventListener('timeupdate',check,true);const timer=setInterval(()=>{check();if(Date.now()>${deadline})clearInterval(timer);},250);check();})()`).catch(()=>{});
  });
  win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
  return win;
}
app.whenReady().then(async()=>{
  try {
    if(process.platform==='darwin') {app.setActivationPolicy('accessory');app.dock.hide();}
    emit({kind:"status",message:"Installing/checking Widevine component…"});
    drmReady=components.whenReady();
    player=createWindow(!process.env.NUVIO_GO3_SERVICE); catalog=createWindow(false,"catalog");
    const frame=player.getBounds(),work=screen.getDisplayMatching(frame).workArea;
    emit({kind:"runtime",app_name:app.getName(),dock_visible:process.platform==='darwin'?app.dock.isVisible():null,
      player_hidden:!player.isVisible(),player_display_sized:frame.width===work.width && frame.height===work.height});
    player.on('close',()=>{emit({kind:'status',message:'Go3 player window closed.'});});
    player.webContents.session.webRequest.onCompleted(d=>{
      let route; try {const p=new URL(d.url).pathname.toLowerCase();
        if(p.includes('/drm/widevine')) route='widevine'; else if(p.endsWith('.mpd')) route='dash'; else if(p.endsWith('.m3u8')) route='hls';
      }catch{}
      if(route) emit({kind:'network',route,status:d.statusCode});
    });
    if (!process.env.NUVIO_GO3_SERVICE) {await player.loadURL(process.env.NUVIO_GO3_WATCH_URL || 'https://go3.lt/');present(player);}
    readyResolve();
    await drmReady;
    emit({kind:'status',message:'DRM runtime ready. Sign in in the Go3 window if needed; session stays in this test profile.'});
    if (!process.env.NUVIO_GO3_WATCH_URL && !process.env.NUVIO_GO3_SERVICE) await discover();
    setInterval(async()=>{
      if(!player || player.isDestroyed()) return;
      try {
        const rows=await player.webContents.executeJavaScript(`Array.from(document.querySelectorAll('video')).map(v=>({time:Math.floor(v.currentTime||0),width:v.videoWidth,height:v.videoHeight,ready:v.readyState,error:v.error?.code||0,paused:v.paused,keys:!!v.mediaKeys}))`);
        for(const r of rows) {
          if(!['time','width','height','ready','error'].every(k=>Number.isInteger(r[k])&&r[k]>=0&&r[k]<=1000000)) continue;
          emit({kind:'video',time:r.time,width:r.width,height:r.height,ready:r.ready,error:r.error,paused:r.paused===true,keys:r.keys===true});
        }
      }catch{}
    },5000).unref();
  }catch{emit({kind:'status',message:'Go3 DRM runtime startup failed.'});}
});
readline.createInterface({input:process.stdin}).on('line',async line=>{
  try {
    const command=JSON.parse(line);
    await runtimeReady;
    if(command.action==='events') {
      try {const result=catalogueQueue.then(async()=>{const start=Date.now();const entries=await collectEvents(catalog,command.catalog);return {entries,load_ms:Date.now()-start};});catalogueQueue=result.catch(()=>{});emit({kind:'events',request:command.request,...await result});}
      catch {emit({kind:'events',request:command.request,error:'Go3 listings unavailable. Retry or sign in through Go3 settings.'});}
      return;
    }
    if(command.action==='signin') {if(!player || player.isDestroyed())player=createWindow(false);player.go3WatchDeadline=0;present(player);await player.loadURL('https://go3.lt/');return;}
    if(command.action==='signout') {const s=session.fromPartition(sessionRoot?'persist:nuvio-go3-live-test':'nuvio-go3-live');await s.clearStorageData();await s.clearAuthCache();if(player && !player.isDestroyed())await player.loadURL('https://go3.lt/');emit({kind:'status',message:'Local Go3 session cleared.'});return;}
    if(command.action==='watch') {
      await drmReady;
      const u=new URL(command.url);
      if(u.origin!=='https://go3.lt' || u.username || u.password || u.search || u.hash || !/^\/(?:watch|live_tv)\/[^/]+,(live|programme|vod)-[0-9]+(\/[^/]+,programme-[0-9]+)?$/.test(u.pathname)) return;
      if(!player || player.isDestroyed()) player=createWindow(false);
      player.setFullScreen(command.fullscreen===true);
      player.go3WatchDeadline=Date.now()+20000;
      await player.loadURL(u.href);present(player);
      emit({kind:"watch",programme:u.pathname.match(/programme-(\d+)/)?.[1]||null});
    } else if(command.action==='refresh') await discover();
    else if(command.action==='play') {
      const chosen=chooseLive(entries.values(),command.id);
      if(!chosen) {emit({kind:'status',message:'No current discovered channel with that ID.'});return;}
      if(!player || player.isDestroyed()) player=createWindow(false);
      await player.loadURL(chosen.url); present(player);
      emit({kind:'selected',id:chosen.id,title:chosen.title});
    } else if(command.action==='quit') app.quit();
  }catch{emit({kind:'status',message:'Test command failed.'});}
});
process.stdin.on('end',()=>app.quit());
