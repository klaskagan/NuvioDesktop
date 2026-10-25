# Trailer playback surfaces

Details trailer cards open `TrailerPlayerPopup` in the existing sheet. Only an
expand action changes its presentation. Collapse returns to the same sheet;
Close dismisses playback. Hero previews and card hover previews keep their
existing artwork, fade, mute, and dismissal behavior.

Hero Trailer Playback (`MetaScreenSettingsRepository`) and card preview trailer,
sound, and start-position preferences (`PosterCardStyleRepository`) are separate.
Do not merge them. The two previews share `HeroTrailerPlayerSurface` and the
AVPlayer frame renderer on macOS. Settings → Detail Page → Trailer Player selects
YouTube (default) or Native for explicit playback. Native uses mpv and its native
HTML/JavaScript toolbar; YouTube uses the embedded player documented in
[the player-selection contract](trailer-player-comparison.md). Capture the choice
per request and preserve it across retry and presentation changes.

## Resolve for the renderer

`TrailerPlaybackTarget` is part of the resolver cache identity. `FRAME_PREVIEW`
prefers the HLS master (which links audio), then a combined file, then compatible
AVC/AAC separate tracks. `NATIVE_PLAYER` prefers reachable separate tracks, then a playable combined file,
with HLS only as a last fallback. Changing the native preference globally previously changed the
AVPlayer inputs too, breaking shared preview consumers.

Direct Googlevideo files with a known length use the session-owned
`DesktopYoutubeStreamProxy` to translate HTTP ranges into bounded `range=` query
requests, like the Android datasource. Apply required headers to both tracks and
release endpoints and upstream calls with the player. Reachability probes must use
the same bounded query-range protocol as playback; the range adapter rejects
responses that ignore the requested byte length. HLS and ordinary URLs pass
through. Never log signed stream URLs. Publish preview readiness only after the
macOS frame pump has a decoded frame; metadata/duration alone is insufficient.

## Native view and controls ownership

`MacosAwtViewResolver` resolves the window view for window operations. It does not
resolve a component-local NSView. Passing that view directly to the embedded
player previously let video and its toolbar cover the entire window while the
sheet stayed logically open. Native creation and advancing time did not catch it.

Embedded YouTube playback owns a dedicated clipped child NSView, with component
bounds converted to content-view coordinates. Follow component/ancestor movement,
resize, visibility, and display-scale changes. An explicit expanded presentation
uses the full content rectangle; collapse uses the sheet player rectangle again.
Native shutdown removes the child after stopping playback and its event sources.
PiP restoration must return to that child rather than the whole window view.

Honor `useNativeController`. Desktop trailers use the native toolbar and suppress
competing Compose controls while native media is mounted. Forward pause intent,
scrubbing, speed, audio/subtitle selection, and fit mode to the trailer session.
`presentationFullscreen` overrides app-window fullscreen for this presentation.
Native embedded controls must not drag or resize the underlying app window.
Dispatch toolbar visibility updates asynchronously to Cocoa, like geometry and
control-state updates. A synchronous EDT-to-Cocoa call deadlocks when macOS
accessibility is waiting for the EDT; exercise native controls with accessibility
inspection after expand/collapse as a regression check.

Preserve position, pause intent, speed, applicable track selections, and fit mode
through presentation changes. Use native initial-position loading on desktop,
rather than opening at zero and racing a second seek. Embedded mpv seeking uses
`absolute+exact` / `relative+exact`; the ordinary movie path keeps its existing
keyframe policy. A cached target position is not proof the decoder reached it.
Do not replace the retained presentation snapshot with an unprepared zero-duration
replacement snapshot. Expanded error UI must sit
above Details content and floating headers, which use z=1/2.

## Lifetimes and regression checks

`TrailerPlaybackRequest` cancels superseded work and checks request identity.
Retry bypasses the matching renderer cache and resets playback state even when
URLs are unchanged. Dismissal invalidates pending work. Native event sinks are
request-scoped. Hero playback pauses while explicit playback owns the session;
hover resolution stops when its popup is dismissed or its item changes.

Run tests with `scripts/desktop-ui-tests.init.gradle` to isolate profile files and
macOS authentication/preferences and provide the required AWT module access.
Build `:composeApp:buildMacosPlayerBridge` before testing a changed JNI interface.
After native-only changes, force `:composeApp:createDistributableImpl --rerun`:
the distribution task can otherwise keep an older native resource. Compare the
bundle's bridge checksum with `build/native/macos/<arch>/libplayer_bridge.dylib`
before packaged verification or delivery.

- `DetailsTrailerUiTest`: real card handler, retry, expand/collapse, dismissal,
  settled error screenshots, and real pointer targets.
- `TrailerPlaybackRequestTest`: cancellation, late results, fresh retry, startup
  timeout, and paused-position retention.
- `TrailerResolverTargetTest` and `DesktopTrailerSourceSelectionTest`: renderer
  cache separation, selective refresh, source preference, and fallback.
- `TrailerSettingsIndependenceTest`: independent hero/card/sound/start settings.
- `DesktopPlaybackSourceTest`: native track propagation, byte-safe EDL quoting,
  bounded ranges/seeks, upstream cancellation, and unchanged ordinary sources.
- `TrailerPlaybackLiveTest`: `NUVIO_TRAILER_LIVE_TEST=1` checks decoded RGB frame
  changes and bounded native geometry, not only timestamps. Add
  `NUVIO_TRAILER_LIVE_ALL=1` for Hope's full trailers, clips, BTS, and teasers.
- `TrailerPreviewLiveTest`: decoded AVPlayer frame readiness and changing rendered
  pixels for Hope and Bad Apples with the live-all flag. CBS's Sheriff Country
  uploads returned no streams during verification (the season-two watch page
  reported a private video); that title remains unverified with live metadata.

The ordinary-media live check expects a generated 12-second AVC fixture at
`composeApp/build/qa/ordinary.mp4`; use ffmpeg's moving `testsrc2` source without
audio. Live checks never use real accounts. `TrailerReviewMain` is a test-only QA
bundle entry point requiring both isolated storage and the in-memory preferences
factory; the normal standalone review app retains the normal app entry point.

## Selection and pointer re-entry

Hero and hover-card autoplay share `HeroTrailerSelector`. Preserve trailer category
and official-status preferences; within a category rank resolution before date.
Use resolution ranges so 1440p and sizes above 2160p are not treated as SD. This
uses existing metadata and adds no extraction calls; metadata size is not a promise
of the player's initial adaptive rendition. `HeroTrailerSelectorTest` covers HD
before recency, intermediate sizes, and season trailers.

The native toolbar must reveal on pointer movement over the player surface after
idle hiding. Hidden toolbar elements have `pointer-events:none`, so their own hover
handlers cannot reveal them. Trailer sessions must handle the same cursor/chrome
visibility events as `PlayerScreenRuntimeUi`; preserve that behavior in both
presentations. `scripts/tests/trailer-chrome.test.mjs` checks the shared reveal path.
