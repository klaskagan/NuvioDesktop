// The official Go3 video stays mounted. Only its CSS presentation changes.
function installLineups(doc, programmeTitle) {
 if (doc.getElementById('nuvio-lineups-toggle')) return;
 const style=doc.createElement('style');
 style.textContent=`
 body.nuvio-lineups-open{--nuvio-video-width:clamp(440px,38vw,820px);--nuvio-video-height:clamp(247.5px,21.375vw,461.25px)}
 #nuvio-lineups-toggle{position:fixed;right:20px;top:20px;z-index:2147483647;background:#303030;color:white;border:1px solid #666;border-radius:24px;padding:12px 20px;font:600 17px system-ui;cursor:pointer}
 #nuvio-lineups-panel{--video-width:var(--nuvio-video-width);position:fixed;inset:0;box-sizing:border-box;background:#121212;color:#eee;z-index:2147483645;padding:28px;overflow-y:auto;font:clamp(17px,1.1vw,24px)/1.4 system-ui}
 #nuvio-lineups-panel *{box-sizing:border-box}
 #nuvio-lineups-panel h2{font-size:1.35em;margin:0 0 22px}
 #nuvio-lineups-panel button{background:#303030;color:white;border:1px solid #666;border-radius:20px;padding:10px 20px;font:inherit}
 #nuvio-lineups-panel .lineup-content{width:calc(100% - var(--video-width) - 28px)}
 #nuvio-lineups-panel .team-headings{display:flex;justify-content:space-between;background:#333;padding:18px 24px;border-radius:16px 16px 0 0;font-weight:600;gap:24px}
 #nuvio-lineups-panel .pitch{display:flex;position:relative;background:#292929;border:2px solid #444;aspect-ratio:105 / 68;min-height:0;container-type:inline-size}
 #nuvio-lineups-panel .pitch:before{content:'';position:absolute;left:50%;top:0;bottom:0;border-left:2px solid #444}
 #nuvio-lineups-panel .pitch:after{content:'';position:absolute;left:50%;top:50%;width:17.43%;aspect-ratio:1 / 1;height:auto;transform:translate(-50%,-50%);border:2px solid #444;border-radius:50%;pointer-events:none}
 #nuvio-lineups-panel .team{flex:1;min-width:0;position:relative}
 #nuvio-lineups-panel .team:before{content:"";position:absolute;left:0;top:30%;height:40%;width:17%;border:2px solid #444;border-left:0}
 #nuvio-lineups-panel .team:last-child:before{left:auto;right:0;border-left:2px solid #444;border-right:0}
 #nuvio-lineups-panel .formation{position:absolute;inset:28px 8px}
 #nuvio-lineups-panel .player{position:absolute!important;display:block!important;min-height:0!important;height:auto!important;width:24%;text-align:center;line-height:1.15;transform:translate(-50%,-50%);font-size:clamp(15px,.95vw,22px);z-index:1}
 #nuvio-lineups-panel .player b{display:flex;align-items:center;justify-content:center;background:#e6e6e6;color:#111;border-radius:50%;width:clamp(36px,3.2vw,64px);height:clamp(36px,3.2vw,64px);margin:0 auto 7px;font-size:1.1em}
 #nuvio-lineups-panel .player span{font-size:clamp(12px,1.7cqw,22px);display:block;min-height:2.3em;padding:0 3px;overflow-wrap:normal}
 #nuvio-lineups-panel .team-columns{position:absolute;right:28px;top:calc(var(--nuvio-video-height) + 110px);width:var(--video-width);display:grid;grid-template-columns:1fr 1fr;gap:28px;background:#202020;padding:22px 24px;border-radius:0 0 16px 16px}
 #nuvio-lineups-panel .bench-row{padding:13px 0;border-bottom:1px solid #383838;display:flex;gap:16px}
 #nuvio-lineups-panel .bench-row b{color:#aaa;min-width:2em}
 #nuvio-lineups-panel .section-label{font-size:.85em;color:#aaa;margin:16px 0}
 body.nuvio-lineups-open *{visibility:hidden!important}
 body.nuvio-lineups-open #nuvio-lineups-panel,body.nuvio-lineups-open #nuvio-lineups-panel *,body.nuvio-lineups-open #nuvio-lineups-toggle,body.nuvio-lineups-open video{visibility:visible!important}
 body.nuvio-lineups-open [data-nuvio-video-ancestor]{display:contents!important}
 body.nuvio-lineups-open video{position:fixed!important;top:82px!important;right:28px!important;left:auto!important;bottom:auto!important;width:var(--nuvio-video-width)!important;height:auto!important;aspect-ratio:16/9!important;object-fit:contain!important;z-index:2147483646!important;background:black!important}
 `;
 doc.head.append(style);
 const button=doc.createElement('button');button.id='nuvio-lineups-toggle';button.textContent='Lineups';button.setAttribute('aria-pressed','false');doc.body.append(button);
 let panel, selected, sequence=0;
 let fullscreenTransition=false;
 const fullScreenHost=()=>doc.fullscreenElement;
 let videoFullscreenInWindow=false;
 const syncFullscreen=()=>{
  const fullscreen=fullScreenHost();
  if(fullscreen?.tagName==='VIDEO') {
   // Chromium makes the rest of the document inert during video-only fullscreen.
   // Use Electron window fullscreen so the same player and its controls stay interactive.
   if(!fullscreenTransition) {
    fullscreenTransition=true;
    doc.exitFullscreen().then(()=>{
     videoFullscreenInWindow=!videoFullscreenInWindow;
     send('videoWindowFullscreen',{id:0,active:videoFullscreenInWindow});
    }).catch(()=>{}).finally(()=>{fullscreenTransition=false;});
   }
   return;
  }
  const host=fullscreen || doc.body;
  if(button.parentElement!==host)host.append(button);
  if(panel && panel.parentElement!==host)host.append(panel);
 };
 doc.addEventListener('fullscreenchange',syncFullscreen);
 const send=(operation,args)=>console.info('NUVIO_LINEUPS_REQUEST:'+JSON.stringify({operation,...args}));
 const cache=new Map();
 const savedControls=new Map();
 const el=(tag,text,parent)=>{const node=doc.createElement(tag);if(text)node.textContent=text;if(parent)parent.append(node);return node;};
 const close=()=>{
  for(const [video,{controls, ancestors}] of savedControls) {
   video.controls=controls;
   ancestors.forEach(parent=>parent.removeAttribute('data-nuvio-video-ancestor'));
  }
  savedControls.clear();
  sequence++;
  panel?.remove();
  panel=null;
  doc.body.classList.remove('nuvio-lineups-open');
  button.setAttribute('aria-pressed','false');
  button.focus?.();
 };
 const request=(operation,args)=>{
  const id=++sequence;window.__nuvioLineupsReceive=(reply)=>{
   if(!panel||id!==sequence||reply.id!==id)return;
   status.textContent=reply.error||'';
   if(reply.error)return;
   if(operation==='resolve'){
    if(reply.data.length!==1){status.textContent=reply.data.length?'More than one matching fixture was found. Original programme date is required.':'No matching fixture found for this programme.';return;}
    selected=reply.data[0];
    if(cache.has(selected.id))render(cache.get(selected.id));else request('lineups',{fixture:selected.id});
   }else{cache.set(selected.id,reply.data);render(reply.data);}

  };
  status.textContent='Loading…';
  send(operation,{id,...args});
 };
 let status;
 const automatic=()=>{
  panel.replaceChildren();
  el('h2','Football lineups',panel);
  status=el('p','Finding the original match…',panel);status.setAttribute('role','status');
  const title=programmeTitle || doc.title;
  const date=title.match(/\b\d{4}-\d{2}-\d{2}\b/)?.[0];
  const retry=el('button','Retry',panel);retry.onclick=automatic;
  request('resolve',{title,date});
 };
 const render=teams=>{
  panel.replaceChildren();
  const content=el('div','',panel);content.className='lineup-content';
  el('h2',selected.home+' — '+selected.away,content);
  if(!teams.length){status=el('p','Original confirmed lineups are not available yet.',content);const retry=el('button','Retry',content);retry.onclick=()=>request('lineups',{fixture:selected.id});return;}
  const headings=el('div','',content);headings.className='team-headings';
  teams.forEach(team=>el('div',team.name+' · '+team.formation,headings));
  const pitch=el('div','',content);pitch.className='pitch';
  teams.forEach((team,index)=>{
   const side=el('section','',pitch);side.className='team';
   const formation=el('div','',side);formation.className='formation';
   team.starters.forEach((player,i)=>{
    const item=el('div','',formation);item.className='player';
    const positioned=player.pitchX!==null&&player.pitchY!==null;
    const x=positioned?(index?1-player.pitchX:player.pitchX):(.12+Math.floor(i/4)*.3);
    const y=positioned?(index?1-player.pitchY:player.pitchY):(.15+(i%4)*.23);
    item.style.left=(3+x*94)+'%';item.style.top=(10+y*80)+'%';
    el('b',player.number,item);
    const parts=player.name.trim().split(/\s+/);const surname=parts.slice(-2).filter((p,i,a)=>i===a.length-1||/^(de|van|von|da|di|dos)$/i.test(p)).join(' ');
    el('span',surname,item);item.setAttribute('aria-label',player.number+' '+player.name);item.title=player.name;
   });
  });
  const columns=el('div','',content);columns.className='team-columns';
  teams.forEach(team=>{
   const column=el('section','',columns);
   el('div',team.coach,column);
   el('h3','Original bench',column).className='section-label';
   team.bench.forEach(p=>{const row=el('div','',column);row.className='bench-row';el('b',p.number,row);el('span',p.name,row);});
  });
  status=el('p','',panel);
 };
 const open=()=>{
  panel=el('section','',doc.body);panel.id='nuvio-lineups-panel';panel.setAttribute('aria-label','Football lineups');
  for(const video of doc.querySelectorAll('video')){
   const ancestors=[];
   for(let parent=video.parentElement;parent&&parent!==doc.body;parent=parent.parentElement) {
    if(parent===fullScreenHost())break;
    parent.setAttribute('data-nuvio-video-ancestor','');
    ancestors.push(parent);
   }
   savedControls.set(video,{controls:video.controls,ancestors});
   video.controls=true;
  }
  syncFullscreen();
  doc.body.classList.add('nuvio-lineups-open');button.setAttribute('aria-pressed','true');if(selected&&cache.has(selected.id))render(cache.get(selected.id));else automatic();
 };
 button.onclick=()=>{
  if(panel){close();return;}
  open();
 };
 syncFullscreen();
 doc.addEventListener('keydown',event=>{
  if(event.key!=='Escape')return;
  if(panel)close();
  else if(videoFullscreenInWindow) {
   videoFullscreenInWindow=false;
   send('videoWindowFullscreen',{id:0,active:false});
  } else return;
  event.preventDefault();
  event.stopImmediatePropagation();
 },true);
}
function responseData(body) {
 if(body.error || !body.data)throw Error('Football data unavailable. Check your PitchAPI key or retry.');
 return body.data;
}
function decodeMatches(body) {
 return (responseData(body).matches||[]).map(o=>({id:o.id,home:o.home_team.name,away:o.away_team.name,competition:o.league?.name||'',kickoff:o.time_utc}));
}
function decodeLineups(body) {
 const d=responseData(body);
 const coordinate=v=>typeof v==='number'&&Number.isFinite(v)&&v>=0&&v<=1?v:null;
 const player=p=>({name:p.name,number:String(p.shirt_number??''),pitchX:coordinate(p.pitch_x),pitchY:coordinate(p.pitch_y)});
 return ['home','away'].filter(side=>d[side]?.confirmed===true).map(side=>{
  const o=d[side];return {name:d[side+'_team']?.name||'',formation:o.formation||'',coach:o.coach?.name||'',
   starters:(o.starters||[]).map(player),bench:(o.subs||[]).map(player)};
 });
}
async function lookup(key,command,fetcher=fetch) {
 if(!key)throw Error('Set your free PitchAPI key in Nuvio Settings → Playback → Player → Football Lineups, then reopen Go3 playback.');
 const get=async(path)=>{
  const r=await fetcher('https://api.pitchapi.dev/v1/'+path,{headers:{'X-API-KEY':key,'User-Agent':'NuvioDesktop/1.0','Accept':'application/json'},redirect:'error',signal:AbortSignal.timeout(20000)});
  if(!r.ok)throw Error('Football data unavailable. Check your PitchAPI key or retry.');
  return r.json();
 };
 const fixtures=async date=>decodeMatches(await get('date/'+date+'?status=all'));
 if(command.operation==='resolve'&&typeof command.title==='string'&&command.title.length<=1000){
  const normalize=s=>String(s).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase()
   .replace(/man[ .-]+utd|man[ .-]+united/g,'manchester united')
   .replace(/&/g,' and ').replace(/\bafc bournemouth\b/g,'bournemouth')
   .replace(/[^a-z0-9]+/g,' ').trim();
  const title=normalize(command.title);
  // Go3 has shipped both spellings in programme titles.
  const premierTitle=command.title.match(/^premier lea(?:gue|uge)[.:]\s*(.*)$/i);
  const premierTeams=premierTitle?.[1]
   .replace(/\s+[-–—]\s+go3\b.*$|\s*\|\s*go3\s*$/i,'')
   .replace(/\s+\(?\d{4}-\d{2}-\d{2}\)?\s*$/,'')
   .split(/\s+(?:[-–—]|vs\.?|v\.?)\s+/i).map(normalize);
  const found=new Map();
  const explicit=typeof command.date==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(command.date)?command.date:null;
  const dates=explicit?[explicit]:Array.from({length:15},(_,i)=>new Date(Date.now()-i*86400000).toISOString().slice(0,10));
  for(const date of dates)for(const m of await fixtures(date)){
   const home=normalize(m.home),away=normalize(m.away);
   const matches=premierTeams
    ? premierTeams.length===2 && normalize(m.competition)==='premier league' &&
      ((premierTeams[0]===home && premierTeams[1]===away) || (premierTeams[0]===away && premierTeams[1]===home))
    : home && away && title.includes(home) && title.includes(away);
   if(matches)found.set(m.id,m);
  }
  return [...found.values()];
 }
 if(command.operation==='matches'&&typeof command.team==='string'&&command.team.length>=3&&command.team.length<=100&&/^\d{4}-\d{2}-\d{2}$/.test(command.date)){
  const team=command.team.toLowerCase();
  return (await fixtures(command.date)).filter(m=>m.home.toLowerCase().includes(team)||m.away.toLowerCase().includes(team));
 }
 if(command.operation==='lineups'&&typeof command.fixture==='string'&&/^m_[A-Za-z0-9]+$/.test(command.fixture))
  return decodeLineups(await get('matches/'+command.fixture+'/lineups'));
 throw Error('Enter a team and original match date.');
}
function safeError(error) {
 const allowed=[
  'Set your free PitchAPI key in Nuvio Settings → Playback → Player → Football Lineups, then reopen Go3 playback.',
  'Football data unavailable. Check your PitchAPI key or retry.',
  'Enter a team and original match date.'
 ];
 return allowed.includes(error?.message)?error.message:'Football data unavailable. Please retry.';
}
module.exports={installLineups,decodeMatches,decodeLineups,lookup,safeError};
