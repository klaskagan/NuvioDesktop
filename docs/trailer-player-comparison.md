# Trailer player comparison (macOS)

Two independent branches start from the same develop revision. Neither is merged:

- `codex/trailer-native-player-review`: mpv overlay and native Nuvio toolbar.
- `codex/trailer-youtube-player-review`: official YouTube embedded player for
  explicitly opened trailers. Ordinary movie playback keeps its existing engine.

Both carry the reviewed HeroTrailerSelector preference: trailer category and
official status first, then resolution tier before publication date. Resolution
ranges include 1440p and sizes above 2160p. This changes upload selection, not the
initial adaptive rendition, and adds no requests. The displayed trailer list is
not reordered by this selector. Hero and hover-card settings remain independent.

## YouTube experiment

The macOS path bypasses signed-URL extraction, stream probes, split-track muxing,
and Nuvio's playback toolbar for the explicit trailer. WKWebView displays the
YouTube IFrame Player API with YouTube's controls. Keep Nuvio's expand/close header
outside the media rectangle. Nuvio owns expansion, including the header action,
video double-click and F; the iframe does not create a separate fullscreen window.
Keep one WebView session while the Compose host changes presentation, preserving
YouTube's position, pause, speed, volume and caption/quality choices.

Use a dedicated clipped child view; the AWT resolver returns a window view, not a
component view. Update the child's bounds on movement/resize and detach/release it
on dismissal. UI updates dispatch asynchronously to Cocoa. Native creation runs
off the EDT; cancellation must still collect and close an in-flight created view.
Callbacks check session lifetime. A startup failure retires the complete WebView
session, preventing a late start from playing audio behind Retry.
`YouTubeSessionLifecycleTest` verifies failure/dismissal and fresh retry lifetimes. Escape is scoped to the active player window.

Identify the app using the actual bundle ID as the HTTPS origin/base URL, following
[YouTube's desktop Referer guidance](https://developers.google.com/youtube/terms/required-minimum-functionality).
The WebView uses a nonpersistent data store. Do not create new Nuvio windows or
an automatic browser fallback. User-activated external links open in the default
browser. Show a retryable error for private/unavailable uploads,
embedding restrictions, failed identification, or startup timeout. If autoplay is
blocked, preserve YouTube's own play button instead of replacing it with a timeout.

YouTube owns adaptive quality; its API cannot force an HD starting rendition.
Branding, recommendations and possible ads belong to the YouTube player. Some
uploaders disallow embedding. These are comparison tradeoffs, not bugs to hide.

Hero/card previews keep the shared AVPlayer frame-rendering session in both
branches. A child WebView above Compose would obscure metadata and ignore the
previews' Compose opacity/clip behavior. Preview resolution/cache keys remain
separate from explicitly opened trailers. Publish readiness only with a decoded
frame; keep artwork during loading and cancel dismissed hover work.

## Expected embedded-player interactions

Read this contract before changing the wrapper. Preserve familiar YouTube behavior;
a working image and advancing time do not verify interactive embedding.

1. An unavailable or non-embeddable upload offers Retry and an explicit **Open in
   YouTube** action for the current upload. Never open a browser automatically.
2. The YouTube logo, video-title banner, channel and external links open their
   destination in the default browser after a user action; pause embedded playback.
3. YouTube owns toolbar timing. Deliver normal pointer behavior, but its public
   IFrame API has no instant mouse-leave toolbar-hide method. Do not modify internal
   YouTube CSS/DOM to simulate that unsupported feature or obscure its controls.
4. Recommended video links load the selected upload in the existing YouTube player,
   retaining the current sheet/fullscreen presentation. Update the title/current
   upload used by retry and browser actions. Do not replace the host with a watch page.
5. Context-menu Open Link/Open in New Window/Open Image use the default browser.
   Context-menu downloads are deferred by product decision (2026-10-25); they are
   not required for this comparison. Keep copy/share and other working context
   actions available. Native WKDownload backend support does not prove that
   WebKit's legacy context-menu Download commands invoke that backend.
6. Space toggles playback, Left/Right seek five seconds immediately after opening,
   without requiring a preliminary video click. Preserve text-entry/menu behavior.
7. Escape collapses an expanded player; in the sheet it dismisses/stops playback.
   This also applies to loading and error states. Escape must not be consumed by
   the new keyboard-focus handling or fire twice through native and Compose paths.
8. Double-click on the video and F toggle Nuvio's expanded presentation; avoid
   hijacking double-clicks on links, toolbar controls, settings or recommendations.
   Keep the same player instance and its playback state through presentation changes.
9. Request captions off at startup (`cc_load_policy=0`) and keep the CC control
   working. This is YouTube's default policy, not a guaranteed force-off override
   of service preferences; do not hide caption DOM or repeatedly undo the viewer's
   own CC selection.

Regression coverage must include these user actions, including native navigation,
new-window and download delegates, keyboard focus before the first click, and
negative cases for automatic popups/unsafe URL schemes. Browser and any download backend tests
must use isolated fixtures and verify the destination/action, not just a paused
player or a received callback. Context-menu download commands remain out of scope. Report any unsupported or unverified interaction.

## Regression checks

Use `scripts/desktop-ui-tests.init.gradle` for isolated files and authentication.
`HeroTrailerSelectorTest` covers older HD vs newer SD, intermediate resolutions,
official preference, and season trailers. `YouTubeEmbedVideoIdTest` validates the
upload IDs before including them in the host page.

Build `:composeApp:createDistributableImpl --rerun` after native changes and compare
the packaged bridge checksum with `build/native/macos/<arch>/libplayer_bridge.dylib`.
Run `YouTubeEmbedLiveTest` with `NUVIO_YOUTUBE_LIVE_TEST=1`, using the packaged
resource directory. It exercises Hope (`R5jcEBVBEbQ`), The Dark Knight
(`EXeTwQWrcwY`), and Batman v Superman (`0WWzgGyAH6Y`), checking bounded geometry,
advancing time, pause/resume, and the same native handle across expand/collapse.
Add `NUVIO_YOUTUBE_LIVE_ALL=1` for all ten Hope uploads (teasers, full trailers,
BTS and clips) alongside The Dark Knight and Batman v Superman.
Times/handles alone are insufficient: also inspect visible video and controls in
the packaged isolated `TrailerReviewMain` UI through the real Details handler,
expand, Escape, Close and reopening. Keep the normal review app's MainKt entry
point; the QA entry point requires isolated storage and in-memory preferences.

`YouTubeNavigationTest` covers recommendation/browser routing and unsafe schemes;
`YouTubeSessionLifecycleTest` covers dismissal, startup failure and intentional pause.
`YouTubeInteractionLiveTest` verifies same-session recommendation playback, browser
pause intent and byte-for-byte native WKDownload transfers of isolated page/image
fixtures. Run `scripts/tests/youtube-interactions.test.mjs` with a current Node
runtime for trusted gestures, native context destinations, interactive focus,
keyboard commands, caption policy and transparent playback-layer hit testing.

The wrapper originally canceled link navigation and discarded new-window requests,
leaving YouTube links inert. Preserve user-activated destinations through both
WebKit delegates; never infer success from playback merely pausing. YouTube can
place a transparent `player-controls-background` over the video. Expansion must
recognize that playback surface while excluding actual controls and links; verify
a real video-body double-click, collapse, Escape dismissal and reopening in the
packaged UI, alongside the synthetic gesture regression tests. Resolve the Escape focus guard
inside the current sheet/fullscreen content: the sheet owns a separate Compose
window. Keep the native Escape observer in the presentation lifetime, independently of
the WebView session: failure must release the WebView/playback callbacks without
retiring the error sheet's keyboard handling. Scope it to the owning NSWindow and
release it on dismissal, replacing it when presentation moves to another window.
Return native focus before detaching a focused WebView. Check Escape after a real
non-embeddable error as well as during playback.

The packaged macOS check confirmed that Hope can still enable captions with the
default-off policy. Do not report this parameter as a guaranteed caption-off
setting. Physical speaker output and other operating systems require separate
validation.
