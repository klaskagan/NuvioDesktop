const {test}=require('node:test');const assert=require('node:assert/strict');
const {dismissFalseAutoplayNotice}=require('./player-page.cjs');
function fixture({playing=true,notice=true}={}) {
 let clicks=0;const body={};const container={textContent:notice?'Šioje naršyklėje automatinis paleidimas yra išjungtas.':'Subscription required',parentElement:body};
 const button={textContent:'GERAI',parentElement:container,click:()=>clicks++};
 const doc={body,querySelectorAll:s=>s==='video'?[{mediaKeys:{},readyState:4,paused:!playing,currentTime:10}]:[button]};return {doc,clicks:()=>clicks};
}
test('acknowledges the false autoplay warning only after licensed playback starts',()=>{const f=fixture();assert.equal(dismissFalseAutoplayNotice(f.doc),true);assert.equal(f.clicks(),1);});
test('does not dismiss genuine autoplay failures or entitlement dialogs',()=>{for(const x of [{playing:false},{notice:false}]){const f=fixture(x);assert.equal(dismissFalseAutoplayNotice(f.doc),false);assert.equal(f.clicks(),0);}});
const {activateOfficialWatch}=require('./player-page.cjs');
function watchFixture({label='ŽIŪRĖTI',login=false,disabled=false,count=1,origin='https://go3.lt'}={}) {
 let clicks=0;const button={textContent:label,disabled,hidden:false,tagName:'BUTTON',click:()=>clicks++,getClientRects:()=>[{}]};
 const doc={location:{origin,href:origin+'/live_tv/a,live-1/a,programme-2'},querySelectorAll:s=>s.startsWith('input')?(login?[button]:[]):s==='video'?[]:Array.from({length:count},()=>button)};
 return {doc,clicks:()=>clicks};
}
test('activates the normal Go3 Watch action, including Lithuanian accents',()=>{const f=watchFixture();assert.equal(activateOfficialWatch(f.doc),true);assert.equal(f.clicks(),1);});
test('does not submit login, purchase, disabled, ambiguous or non-Go3 actions',()=>{
 for(const options of [{login:true},{label:'Buy subscription'},{disabled:true},{count:2},{origin:'https://evil.example'}]) {const f=watchFixture(options);assert.equal(activateOfficialWatch(f.doc),false);assert.equal(f.clicks(),0);}
});
