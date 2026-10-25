import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';
const source = readFileSync(new URL('../../composeApp/src/desktopMain/resources/player-ui/youtube-interactions.js', import.meta.url), 'utf8');
function fixture(host = 'www.youtube.com') {
  const listeners = {}, sent = [];
  const document = {activeElement:null, addEventListener:(type, fn) => listeners[type] = fn};
  vm.runInNewContext(source, {window:{addEventListener:(type,fn)=>listeners[type]=fn,location:{hostname:host},webkit:{messageHandlers:{youtube:{postMessage:body => {if(!body.type.startsWith("interactionReady:"))sent.push(body)}}}}},document,setTimeout:fn=>fn()});
  return {listeners, sent, document};
}
function link({recommendation=false, title=false, download=false}={}) {
  return {href:'https://www.youtube.com/watch?v=EXeTwQWrcwY', hasAttribute:name=>name==='download' && download,
    closest:selector => selector==='a[href]' || recommendation && selector.includes('.ytp-suggestion-link') || title && selector.includes('.ytp-title-link') ? true : null};
}
function click(anchor, overrides={}) {
  const event = {isTrusted:true,button:0,target:{closest:selector=>selector==='a[href]'?anchor:null},prevented:false,stopped:false,
    preventDefault(){this.prevented=true},stopImmediatePropagation(){this.stopped=true},...overrides};
  return event;
}
test('trusted recommended-video click routes inside the player and cancels navigation',()=>{
  const f=fixture(), e=click(link({recommendation:true})); f.listeners.click(e);
  assert.equal(f.sent[0].type,'link');assert.equal(f.sent[0].value,2);assert.equal(e.prevented,true);assert.equal(e.stopped,true);
});
test('title and modifier clicks preserve external-browser intent',()=>{
  for (const e of [click(link({title:true})),click(link(),{metaKey:true})]) {
    const f=fixture();f.listeners.click(e);assert.equal(f.sent[0].value,1);
  }
});
test('automatic synthetic clicks and download links are not rerouted',()=>{
  for (const e of [click(link(),{isTrusted:false}),click(link({download:true}))]) {
    const f=fixture();f.listeners.click(e);assert.equal(f.sent.length,0);assert.equal(e.prevented,false);
  }
});
test('context menus retain native link and image destinations',()=>{
  const f=fixture();f.listeners.contextmenu({isTrusted:true,target:{closest:s=>s==='a[href]'?{href:'https://youtube.com/watch?v=EXeTwQWrcwY'}:{src:'https://i.ytimg.com/image.jpg'}}});
  assert.equal(f.sent[0].link,'https://youtube.com/watch?v=EXeTwQWrcwY');assert.equal(f.sent[0].image,'https://i.ytimg.com/image.jpg');
});
test('only video double-clicks request app expansion',()=>{
  for (const control of [false,true]) {
    const f=fixture();const e=click(null);e.target={tagName:'VIDEO',closest:s=>control&&s.includes('button')?{}:null};
    f.listeners.dblclick(e);assert.equal(f.sent.length,control?0:1);assert.equal(e.prevented,!control);
  }
});
test('unrelated frames receive no interception handlers',()=>{assert.deepEqual(Object.keys(fixture('example.com').listeners),[])});
test('text focus is passed to the native shortcut owner',()=>{
  const f=fixture();f.listeners.focusin({target:{isContentEditable:true}});assert.equal(f.sent[0].type,'editing');assert.equal(f.sent[0].value,1);
  f.document.activeElement={isContentEditable:false,closest:()=>null};f.listeners.focusout();assert.equal(f.sent[1].value,0);
});
test('focused buttons and menus keep their own keyboard behavior',()=>{
  const f=fixture();f.listeners.focusin({target:{isContentEditable:false,closest:s=>s.includes('[role="menuitem"]')?{}:null}});
  assert.equal(f.sent[0].value,1);
});
const native = readFileSync(new URL('../../composeApp/src/desktopMain/native/macos/player_bridge.mm',import.meta.url),'utf8');
const apiScript = native.slice(native.indexOf('var player,apiReady=false'),native.indexOf('function onYouTubeIframeAPIReady')).replace('__MUTE__','false');
test('first-click-independent shortcuts use YouTube playback and clamped five-second seeks',()=>{
  const actions=[];let state=1,time=2;
  const mock={getPlayerState:()=>state,pauseVideo:()=>actions.push('pause'),playVideo:()=>actions.push('play'),getCurrentTime:()=>time,getDuration:()=>10,seekTo:n=>actions.push(n)};
  const context=vm.createContext({window:{webkit:{messageHandlers:{youtube:{postMessage:()=>{}}}}},mock});
  vm.runInContext(apiScript+';apiReady=true;player=mock;',context);
  context.window.youTubeShortcut('space');state=2;context.window.youTubeShortcut('space');
  state=3;context.window.youTubeShortcut('space');context.window.youTubeShortcut('left');time=9;context.window.youTubeShortcut('right');
  assert.deepEqual(actions,['pause','play','pause',0,10]);
});

test('browser pause preserves the viewer mute choice and captions remain user-controlled',()=>{
  const actions=[];
  const context=vm.createContext({window:{webkit:{messageHandlers:{youtube:{postMessage:()=>{}}}}},mock:{pauseVideo:()=>actions.push('pause'),unMute:()=>actions.push('unmute')}});
  vm.runInContext(apiScript+';apiReady=true;player=mock;window.pauseYouTube();',context);
  assert.deepEqual(actions,['pause']);
  assert.match(native,/cc_load_policy:0/);
  assert.doesNotMatch(source,/unloadModule|setOption.*captions|caption-window|captions.*display/);
});

test('transparent video hit surfaces also expand, while chrome remains excluded',()=>{
  const f=fixture(),e=click(null);
  e.target={tagName:'DIV',closest:s=>s.includes('.html5-video-player')?{}:null};
  f.listeners.dblclick(e);assert.equal(f.sent[0].type,'toggleFullscreen');
});

test('two trusted video clicks expand once even when YouTube consumes dblclick',()=>{
  const f=fixture(); const target={tagName:'VIDEO',closest:()=>null};
  const a=click(null,{target,timeStamp:1000,clientX:50,clientY:60}), b=click(null,{target,timeStamp:1200,clientX:50,clientY:60});
  f.listeners.click(a); f.listeners.click(b); f.listeners.dblclick(b);
  assert.equal(f.sent.filter(e=>e.type==='toggleFullscreen').length,1);
  assert.equal(a.prevented,false);
});

test('mobile transparent playback background expands without claiming toolbar buttons',()=>{
  const f=fixture(),e=click(null);
  e.target={tagName:'DIV',closest:s=>s.includes('.player-controls-background')?{}:null};
  f.listeners.dblclick(e);assert.equal(f.sent[0].type,'toggleFullscreen');
});
