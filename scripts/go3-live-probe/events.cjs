// Public page metadata only; never accept signed playback/CDN URLs.
function officialPage(raw) {
  try {
    const u=new URL(raw,'https://go3.lt');
    if(u.origin!=='https://go3.lt' || u.username || u.password) return null;
    if(!/^\/(?:watch|live_tv)\/[^/]+,(?:live|programme|vod)-\d+(?:\/[^/]+,programme-\d+)?$/.test(u.pathname)) return null;
    return u.origin+u.pathname;
  } catch{return null;}
}
function artwork(raw) {
  try {const u=new URL(raw); if(u.origin!=='https://static3.go3.tv' || !u.pathname.startsWith('/scale/go3/'))return null;
    const allowed=new Set(['dsth','dstw','srcmode','quality','type','srcx','srcy','srcw','srch']);
    if([...u.searchParams.keys()].some(k=>!allowed.has(k)))return null;
    u.hash='';return u.href;
  }catch{return null;}
}
function event(row) {
  const url=officialPage(row.url);
  if(!url)return null;
  const programme=url.match(/,programme-(\d+)/)?.[1];
  if(!programme)return null; // Excludes channel/footer links.
  const title=String(row.name||'').replace(/[\u0000-\u001f]/g,' ').trim().slice(0,300);
  if(!title)return null;
  const label=String(row.label||'');
  const state=/GREITAI|UPCOMING/i.test(label)?'upcoming':/ARCHYVAS|ARCHIVE/i.test(label)?'replay':/TIESIOGIAI|LIVE/i.test(label)?'live':'listed';
  return {id:'go3:programme:'+programme,title,url,artwork:artwork(row.artwork),state,scheduleLabel:label.replace(/[\u0000-\u001f]/g,' ').trim().slice(0,500)};
}
module.exports={officialPage,artwork,event};
