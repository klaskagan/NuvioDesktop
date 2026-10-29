const {event}=require('./events.cjs');
const SOURCES={live:'https://go3.lt/sports/sportas-tiesiogiai,section-7998051',epl:'https://go3.lt/categories/premierleague,category-6886315'};
const READ=`Array.from(document.querySelectorAll('a[href*="programme-"]')).map(a=>{const i=a.querySelector('img');const art=[i?.src,i?.currentSrc].find(s=>s && !s.includes('no-image'));return {url:a.href,name:i?.alt||a.innerText.split('\\n')[0].replace(/^[^·]+·\\s*/,''),artwork:art||'',label:a.innerText}})`;
async function collectEvents(browser,kind) {
 const source=SOURCES[kind];if(!source)throw new Error('Unknown catalogue');
 browser.webContents.setBackgroundThrottling(false);
 await browser.loadURL(source);
 let rows=[];
 for(let attempt=0;attempt<24;attempt++) {
  rows=await browser.webContents.executeJavaScript(READ);
  if(rows.some(r=>/TIESIOGIAI|GREITAI|ARCHYVAS/i.test(r.label)))break;
  await new Promise(r=>setTimeout(r,250));
 }
 // Go3 loads carousel artwork on intersection. Visit rendered cards before taking
 // the snapshot, so offscreen artwork is not permanently cached as missing.
 await browser.webContents.executeJavaScript(`(async()=>{for(const a of document.querySelectorAll('a[href*="programme-"]')){a.scrollIntoView({block:'nearest',inline:'nearest'});await new Promise(r=>setTimeout(r,90));}})()`);
 const found=new Map();
 const merge=rows=>{for(const row of rows){const e=event(row);if(e){const previous=found.get(e.id);found.set(e.id,previous?.artwork && !e.artwork?{...e,artwork:previous.artwork}:e);}}};
 merge(await browser.webContents.executeJavaScript(READ));
 // Go3's carousel uses transforms and is-visible classes, not native scrolling.
 // Advance its actual next controls to materialize the remaining public posters.
 for(let page=0;page<3;page++) {
  const advanced=await browser.webContents.executeJavaScript(`(()=>{let n=0;for(const section of document.querySelectorAll('.c-section')){if(!section.querySelector('a[href*="programme-"]'))continue;const next=section.querySelector('.c-section__control--next.is-active');if(next){next.click();n++;}}return n;})()`);
  if(!advanced)break;
  await new Promise(r=>setTimeout(r,400));
  merge(await browser.webContents.executeJavaScript(READ));
 }
 if(!found.size)throw new Error('No programme cards available');
 return [...found.values()];
}
module.exports={collectEvents};
