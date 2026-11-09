const {test}=require('node:test');const assert=require('node:assert/strict');
const {decodeMatches,decodeLineups,lookup,installLineups}=require('./lineups.cjs');
test('Go3 match picker drops results and status from PitchAPI',()=>{
 assert.deepEqual(decodeMatches({data:{matches:[{id:'m_test',time_utc:'2026-11-03',status:'finished',home_team:{name:'A'},away_team:{name:'B'},league:{name:'Cup'},score_home:9}]}}),
 [{id:'m_test',home:'A',away:'B',competition:'Cup',kickoff:'2026-11-03'}]);
});
test('Go3 accepts only confirmed lineups and drops participation and ratings',()=>{
 assert.deepEqual(decodeLineups({data:{home_team:{name:'A'},home:{confirmed:true,starters:[{name:'X',shirt_number:1,pitch_x:.5,pitch_y:.1,rating:10}],subs:[{name:'Y',shirt_number:2,substitution_minute:70}]},away:{confirmed:false,starters:[{name:'Prediction'}]}}}),
 [{name:'A',formation:'',coach:'',starters:[{name:'X',number:'1',pitchX:.5,pitchY:.1}],bench:[{name:'Y',number:'2',pitchX:null,pitchY:null}]}]);
});
test('missing key and invalid commands never send provider requests',async()=>{
 let requests=0;const fetcher=()=>{requests++;throw Error('unexpected');};
 await assert.rejects(lookup('',{operation:'matches'},fetcher),/free PitchAPI key/);
 await assert.rejects(lookup('secret',{operation:'arbitrary'},fetcher),/original match date/);
 assert.equal(requests,0);
});
test('Go3 toggle resolves the selected programme even when Extra replaces the page title',t=>{
 const messages=t.mock.method(console,'info',()=>{});
 const nodes=new Map();const classes=new Set();const listeners={};
 class Element {
  constructor(tag){this.tag=tag;this.children=[];this.attrs={};}
  append(n){n.parentElement=this;this.children.push(n);if(n.id)nodes.set(n.id,n);}
  setAttribute(k,v){this.attrs[k]=v;}
  remove(){nodes.delete(this.id);}
  replaceChildren(){this.children=[];}
 }
 const body=new Element('body');body.classList={add:k=>classes.add(k),remove:k=>classes.delete(k)};
 const doc={title:'Extra tiesiogiai | Go3',querySelectorAll:()=>[],body,head:new Element('head'),createElement:t=>new Element(t),getElementById:id=>nodes.get(id),addEventListener:(k,v)=>listeners[k]=v};
 global.window={};
 const programmeTitle='Premier League. Sunderland - Chelsea';
 installLineups(doc,programmeTitle);const button=nodes.get('nuvio-lineups-toggle');assert.ok(button);
 const fullscreen=new Element('section');
 doc.fullscreenElement=fullscreen;listeners.fullscreenchange();
 assert.equal(button.parentElement,fullscreen);
 button.onclick();assert.equal(button.attrs['aria-pressed'],'true');assert.ok(classes.has('nuvio-lineups-open'));
 const request=JSON.parse(messages.mock.calls[0].arguments[0].slice('NUVIO_LINEUPS_REQUEST:'.length));
 assert.deepEqual(request,{id:1,operation:'resolve',title:programmeTitle});
 const retry=nodes.get('nuvio-lineups-panel').children.find(node=>node.tag==='button');
 doc.title='Go3 Sport | Go3';
 retry.onclick();
 const retried=JSON.parse(messages.mock.calls[1].arguments[0].slice('NUVIO_LINEUPS_REQUEST:'.length));
 assert.equal(retried.title,programmeTitle);
 assert.equal(nodes.get('nuvio-lineups-panel').parentElement,fullscreen);
 doc.fullscreenElement=null;listeners.fullscreenchange();
 assert.equal(button.parentElement,body);
 assert.equal(nodes.get('nuvio-lineups-panel').parentElement,body);
 button.onclick();assert.equal(button.attrs['aria-pressed'],'false');assert.equal(classes.size,0);
 assert.ok(!installLineups.toString().includes('.load('));assert.ok(!installLineups.toString().includes('.src='));
});

test('provider errors never expose scores or credentials',()=>{
 const {safeError}=require('./lineups.cjs');
 assert.equal(safeError(Error('Final result 9-2 secret-key')),'Football data unavailable. Please retry.');
});

test('date and lineup lookups use PitchAPI headers, string IDs and safe models',async()=>{
 const requests=[];
 const fetcher=async(url,options)=>{
  requests.push({url,headers:options.headers});
  return {ok:true,json:async()=>url.includes('/date/')?
   {data:{matches:[{id:'m_fixture',time_utc:'2026-11-03',home_team:{name:'Manchester United'},away_team:{name:'Roma'},league:{name:'Cup'},score_home:5}]}}:
   {data:{home_team:{name:'Manchester United'},home:{confirmed:true,starters:[],subs:[]}}}};
 };
 const matches=await lookup('test-key',{operation:'matches',team:'Roma',date:'2026-11-03'},fetcher);
 assert.equal(matches[0].id,'m_fixture');
 await lookup('test-key',{operation:'lineups',fixture:matches[0].id},fetcher);
 assert.equal(requests[0].url,'https://api.pitchapi.dev/v1/date/2026-11-03?status=all');
 assert.equal(requests[1].url,'https://api.pitchapi.dev/v1/matches/m_fixture/lineups');
 assert.equal(requests[0].headers['X-API-KEY'],'test-key');
 assert.ok(requests[0].headers['User-Agent']);
 assert.equal('score_home' in matches[0],false);
});

test('automatic lookup matches both teams across original replay dates and keeps ambiguity explicit',async()=>{
 const fetcher=async url=>({ok:true,json:async()=>({data:{matches:new URL(url).pathname.endsWith('2026-11-03')?[{id:'m_auto',time_utc:'2026-11-03',home_team:{name:'Manchester United'},away_team:{name:'Roma'},league:{name:'Champions League'}}]:[]}})});
 const matches=await lookup('key',{operation:'resolve',title:'UEFA Champions League. Manchester United - Roma - Go3',date:'2026-11-03'},fetcher);
 assert.equal(matches.length,1);assert.equal(matches[0].id,'m_auto');
 assert.equal((await lookup('key',{operation:'resolve',title:'Manchester United - Milan',date:'2026-11-03'},fetcher)).length,0);
});

test('live Premier League fixture is discoverable before it becomes a played result',async()=>{
 const fetcher=async url=>({ok:true,json:async()=>({data:{matches:
  new URL(url).searchParams.get('status')==='all' ? [{
   id:'m_live',status:'not_started',home_team:{name:'Sunderland'},away_team:{name:'Chelsea'},
   league:{name:'Premier League'},time_utc:'2026-11-08T14:00:00Z',
  }] : []}})});
 const matches=await lookup('key',{operation:'resolve',title:'Premier League. Sunderland - Chelsea - Go3',date:'2026-11-08'},fetcher);
 assert.deepEqual(matches,[{id:'m_live',home:'Sunderland',away:'Chelsea',competition:'Premier League',kickoff:'2026-11-08T14:00:00Z'}]);
});

test('Go3 Premier League titles resolve the exact pair across provider spellings',async()=>{
 const fixture=(id,home,away,competition='Premier League')=>({
  id,home_team:{name:home},away_team:{name:away},league:{name:competition},
 });
 const fetcher=async()=>({ok:true,json:async()=>({data:{matches:[
  fixture('m_brighton','Brighton & Hove Albion','Brentford'),
  fixture('m_wrongLeague','Brighton & Hove Albion','Brentford','FA Cup'),
  fixture('m_women','Brighton & Hove Albion Women','Brentford'),
  fixture('m_bournemouth','Ipswich Town','AFC Bournemouth'),
 ]}})});
 const resolve=async title=>(await lookup('key',{operation:'resolve',title,date:'2026-11-08'},fetcher)).map(match=>match.id);
 assert.deepEqual(await resolve('Premier Leauge. Brighton and Hove Albion - Brentford - Go3 Sport 1 | Go3'),['m_brighton']);
 assert.deepEqual(await resolve('Premier League. Ipswich Town - Bournemouth - Go3 Sport 3 | Go3'),['m_bournemouth']);
 assert.deepEqual(await resolve('Premier League. Ipswich Town - Brighton'),[]);
});
