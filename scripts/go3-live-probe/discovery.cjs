function safeChannel(raw) {
  try { const u=new URL(raw); if(u.origin!=='https://go3.lt') return null;
    const match=u.pathname.match(/(?:^|\/)([^/]+,live-(\d+))(?:\/|$)/);
    return match ? {id:match[2],url:'https://go3.lt/watch/'+match[1]} : null;
  } catch { return null; }
}
function toEntry(row, source) {
  const channel=safeChannel(row.url);
  if(!channel || typeof row.title!=='string' || !row.title.trim()) return null;
  const title=row.title.replace(/[\u0000-\u001f]/g,' ').slice(0,500);
  const state=/GREITAI|UPCOMING/i.test(title)?'upcoming':/ARCHYVAS|ARCHIVE/i.test(title)?'archive':/TIESIOGIAI|LIVE|DABAR/i.test(title)?'live':'listed';
  return {...channel,title,state,source};
}
function chooseLive(entries,id) { return [...entries].find(e=>e.id===id && ['listed','live'].includes(e.state)); }
module.exports={safeChannel,toEntry,chooseLive};
