// Acknowledge only the known false autoplay notice after playback really starts.
// Never remove overlays, alter media protections, or acknowledge account prompts.
function dismissFalseAutoplayNotice(doc) {
 const playing=Array.from(doc.querySelectorAll('video')).some(v=>v.mediaKeys && v.readyState>=2 && !v.paused && v.currentTime>0);
 if(!playing)return false;
 for(const button of doc.querySelectorAll('button,[role="button"]')) {
  if(!/^(gerai|ok)$/i.test(button.textContent.trim()))continue;
  if(button.getClientRects && !button.getClientRects().length)continue;
  let container=button.parentElement;
  for(let depth=0;container && container!==doc.body && depth<5;depth++,container=container.parentElement) {
   const text=container.textContent.replace(/\s+/g,' ').trim();
   if(text.length<600 && /Šioje naršyklėje automatinis paleidimas yra išjungtas/i.test(text)) {
    button.click();return true;
   }
  }
 }
 return false;
}
module.exports={dismissFalseAutoplayNotice};

// Only the explicit Nuvio Watch request enables this, for a short time window.
// Use Go3's own action; never submit sign-in, purchase, or entitlement forms.
function activateOfficialWatch(doc) {
 if(doc.location?.origin!=='https://go3.lt')return false;
 const visible=e=>!e.hidden && (!e.getClientRects || e.getClientRects().length>0);
 if(Array.from(doc.querySelectorAll('input[type="password"],input[type="email"],input[autocomplete="one-time-code"]')).some(visible))return false;
 if(Array.from(doc.querySelectorAll('video')).some(v=>!v.paused && v.readyState>=2))return false;
 const matches=Array.from(doc.querySelectorAll('button,[role="button"],a')).filter(e=>{
  if(!visible(e)||e.disabled||e.getAttribute?.('aria-disabled')==='true')return false;
  const label=(e.querySelector?.('.o-button__label')?.textContent||e.textContent).replace(/[\uE000-\uF8FF]/g,'').normalize('NFD').replace(/\p{M}/gu,'').trim();
  if(!/^(ziureti|watch|watch now)$/i.test(label))return false;
  if(e.tagName==='A') {try {const u=new URL(e.href,doc.location.href);if(u.origin!=='https://go3.lt'||!u.pathname.startsWith('/watch/'))return false;}catch{return false;}}
  return true;
 });
 if(matches.length!==1)return false;
 matches[0].click();return true;
}
module.exports.activateOfficialWatch=activateOfficialWatch;
