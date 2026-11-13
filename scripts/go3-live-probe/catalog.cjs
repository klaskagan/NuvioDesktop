const {toEntry}=require('./discovery.cjs');
const SOURCES=['https://go3.lt/sports/sportas-tiesiogiai,section-7998051','https://go3.lt/sports','https://go3.lt/categories/tennis,category-4699653','https://go3.lt/search?query=ATP'];
async function discoverEntries(browser) {
  const entries=new Map();
  for(const source of SOURCES) {
    browser.webContents.setBackgroundThrottling(false);
    await browser.loadURL(source);
    for(let attempt=0;attempt<8;attempt++) {
      await new Promise(r=>setTimeout(r,1500));
      const rows=await browser.webContents.executeJavaScript(`Array.from(document.querySelectorAll('a[href*="live-"]')).map(a=>({url:a.href,title:(a.innerText || Array.from(a.querySelectorAll('img')).map(i=>i.alt).join(' ')).replace(/\\s+/g,' ').trim()}))`);
      for(const row of rows) {const entry=toEntry(row,source);if(entry) entries.set(entry.id+'|'+entry.title,entry);}
      // Footer links may exist before the actual catalogue has rendered.
      if(attempt>=3 && rows.some(r=>/TIESIOGIAI|GREITAI|ARCHYVAS/i.test(r.title))) break;
    }
  }
  return [...entries.values()];
}
module.exports={discoverEntries,SOURCES};
