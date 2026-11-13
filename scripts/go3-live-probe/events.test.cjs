const {test}=require('node:test');const a=require('node:assert/strict');const {event,officialPage,artwork,browserWatchUrl}=require('./events.cjs');
test('programme identity and exact destination survive normalization',()=>{
 const x=event({url:'/live_tv/go3,live-10267383/vienna,programme-12669382?tracking=x',name:'Vienna',label:'TIESIOGIAI'});
 a.equal(x.id,'go3:programme:12669382');a.equal(x.state,'live');a.equal(x.url,'https://go3.lt/live_tv/go3,live-10267383/vienna,programme-12669382');
 a.equal(event({url:'/live_tv/go3,live-10267383',name:'Footer'}),null);
});
test('reject hostile origins and secret-bearing artwork',()=>{
 a.equal(officialPage('https://evil.example/watch/x,live-1'),null);
 a.equal(artwork('https://static3.go3.tv/scale/go3/a?token=SECRET'),null);
 a.equal(artwork('https://evil.example/poster.jpg'),null);
});
test('upcoming takes precedence over a live channel label',()=>{
 a.equal(event({url:'/live_tv/x,live-1/a,programme-2',name:'A',label:'GREITAI LIVE'}).state,'upcoming');
});

test('Champions League uses its official competition category rather than live-only listings',()=>{
 const {SOURCES}=require('./event-catalog.cjs');
 a.equal(SOURCES.ucl,'https://go3.lt/categories/uefachampionsleague,category-6886205');
 a.notEqual(SOURCES.ucl,SOURCES.live);
});


test('browser watch maps metadata canonical paths onto the Go3 website route', () => {
 const path = '/live_tv/go3-sport-1,live-4296024/laliga-valencia---real-madrid,programme-12705179';
 a.equal(browserWatchUrl('https://go3.lt/lt' + path).href, 'https://go3.lt' + path);
 a.equal(browserWatchUrl('https://go3.lt' + path).href, 'https://go3.lt' + path);
 a.equal(browserWatchUrl('https://go3.lt/lt' + path + '?token=private'), null);
 a.equal(browserWatchUrl('https://go3.lt/lt' + path + '#fragment'), null);
 a.equal(browserWatchUrl('https://example.com/lt' + path), null);
 a.equal(browserWatchUrl('https://user@go3.lt/lt' + path), null);
});
