// App integration only: do not alter YouTube's controls, layout or hide timing.
(() => {
  const host = window.location.hostname.toLowerCase();
  const bridge = window.webkit.messageHandlers.youtube;
  const debug = !!window.__nuvioYouTubeDebug;
  if (debug) bridge.postMessage({type:'interactionReady:'+host,value:1});
  if (!(host === 'youtube.com' || host.endsWith('.youtube.com') || host === 'youtube-nocookie.com' || host.endsWith('.youtube-nocookie.com'))) return;
  const send = body => bridge.postMessage(body);
  const element = event => event.target && event.target.closest ? event.target : null;
  const editable = target => !!target && (target.isContentEditable || !!target.closest('input,textarea,select,a,button,[role="button"],[role="menuitem"],[role="menuitemradio"],[role="slider"],[contenteditable="true"]'));
  const videoArea = target => {
    if (!target || target.closest('a,button,input,textarea,select,.ytp-chrome-top,.ytp-chrome-bottom,.ytp-popup,.ytp-pause-overlay,.ytp-endscreen-content')) return false;
    const action = target.closest('[role="button"]');
    if (action && !action.matches('.html5-video-player,.ytp-cued-thumbnail-overlay,.player-controls-background')) return false;
    return target.tagName === 'VIDEO' || !!target.closest('.html5-video-player,.html5-video-container,.ytp-cued-thumbnail-overlay-image,.ytp-cued-thumbnail-overlay,.player-controls-background');
  };
  const doubleInterval = Number(window.__nuvioDoubleClickInterval) || 500;
  let previousVideoClick = null, lastExpansion = -Infinity;
  const expand = event => {
    const now = Number(event.timeStamp) || Date.now();
    if (now - lastExpansion > doubleInterval) { send({type:'toggleFullscreen',value:0}); lastExpansion = now; }
  };

  window.addEventListener('focusin', event => send({type: 'editing', value: editable(event.target) ? 1 : 0}), true);
  window.addEventListener('focusout', () => setTimeout(() => send({type: 'editing', value: editable(document.activeElement) ? 1 : 0}), 0), true);
  window.addEventListener('contextmenu', event => {
    if (!event.isTrusted) return;
    const target = element(event);
    const link = target && target.closest('a[href]');
    const image = target && target.closest('img[src]');
    send({type: 'menuContext', link: link ? link.href : '', image: image ? image.src : ''});
  }, true);
  window.addEventListener('click', event => {
    if (debug) send({type:'gestureDebug:click:'+event.isTrusted+':'+(event.target.tagName||'unknown')+':'+String(event.target.className||'').slice(0,120),value:1});
    if (!event.isTrusted || event.button !== 0) return;
    const target = element(event);
    const link = target && target.closest('a[href]');
    if (!link) {
      if (videoArea(target)) {
        const now = Number(event.timeStamp) || Date.now();
        if (previousVideoClick && now-previousVideoClick.time <= doubleInterval &&
            Math.abs(event.clientX-previousVideoClick.x) <= 8 && Math.abs(event.clientY-previousVideoClick.y) <= 8) {
          expand(event); previousVideoClick = null;
          // Let the second normal click complete, preserving play/pause intent.
        } else previousVideoClick = {time:now,x:event.clientX,y:event.clientY};
      } else previousVideoClick = null;
      return;
    }
    previousVideoClick = null;
    if (link.hasAttribute('download')) return;
    if (!/^https?:\/\//i.test(link.href)) return;
    const recommendation = !!link.closest('.ytp-suggestion-link,.ytp-videowall-still,.ytp-pause-overlay,.ytp-endscreen-content');
    const external = event.metaKey || event.ctrlKey || event.shiftKey || !!link.closest('.ytp-title-link,.ytp-youtube-button');
    send({type: 'link', url: link.href, value: external ? 1 : recommendation ? 2 : 0});
    event.preventDefault(); event.stopImmediatePropagation();
  }, true);
  window.addEventListener('dblclick', event => {
    if (debug) send({type:'gestureDebug:double:'+event.isTrusted+':'+(event.target.tagName||'unknown')+':'+String(event.target.className||'').slice(0,120),value:1});
    if (!event.isTrusted || event.button !== 0) return;
    const target = element(event);
    if (!videoArea(target)) return;
    expand(event);
    event.preventDefault(); event.stopImmediatePropagation();
  }, true);
  if (debug) send({type:"interactionHandlersReady",value:1});
})();
