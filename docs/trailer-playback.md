# Trailer playback surfaces

Details trailer cards open `TrailerPlayerPopup` in the existing sheet. Only an
expand action changes its presentation. Collapse returns to the same sheet;
Close dismisses playback. Hero previews and card hover previews keep their
existing artwork, fade, mute, and dismissal behavior.

Hero Trailer Playback (`MetaScreenSettingsRepository`) and card preview trailer,
sound, and start-position preferences (`PosterCardStyleRepository`) are separate.
Do not merge them. Both desktop previews use the same Compose frame renderer and AVPlayer on macOS.
Details retains the session above the layout branches. Hover cards mount the
renderer through `HeroTrailerPlayerSurface`. Settings → Detail Page → Trailer Player selects
YouTube (default) or Native for explicit playback. Native uses mpv and its native
HTML/JavaScript toolbar; YouTube uses the embedded player documented in
[the player-selection contract](trailer-player-comparison.md). Capture the choice
per request and preserve it across retry and presentation changes.

Desktop autoplay prints `[TrailerPreview]` when a trailer source resolves or is
unavailable, with the surface, title and YouTube watch URL.
`NUVIO_TRAILER_DEBUG=1` also logs the Details hero's selected rendition, frame
bitmap, and video canvas dimensions on the first draw and when sizes change.
AVPlayer may size its output bitmap for the surface. A 4K rendition, a smaller
frame bitmap, and the drawing canvas are three independent measurements.

## Resolve for the renderer

`TrailerPlaybackTarget` is part of the resolver cache identity. `FRAME_PREVIEW`
prefers the HLS master (which links audio), then a combined file, then compatible
AVC/AAC separate tracks. `NATIVE_PLAYER` prefers reachable separate tracks, then a playable combined file,
with HLS only as a last fallback. Changing the native preference globally previously changed the
AVPlayer inputs too, breaking shared preview consumers.

`DESKTOP_DETAILS_HERO` orders each upload's renditions by video dimensions,
accounting for theatrical crops by width. The adaptive master can choose a
different rendition from the one ranked by the resolver. The Details session
therefore serves a one-variant master over loopback with only the matching
audio group and absolute CDN URLs. The session releases this playlist when its
player closes. Startup failure advances to the next rendition and then the next
upload within one startup deadline. A 4K upload can expose only VP9 at that
resolution. The macOS AVFoundation path skips VP9 HLS renditions and tries the
highest resolution its renderer supports.

Direct Googlevideo files with a known length use the session-owned
`DesktopYoutubeStreamProxy` to translate HTTP ranges into bounded `range=` query
requests, like the Android datasource. Apply required headers to both tracks and
release endpoints and upstream calls with the player. Reachability probes must use
the same bounded query-range protocol as playback; the range adapter rejects
responses that ignore the requested byte length. HLS and ordinary URLs pass
through. Never log signed stream URLs. Publish Details hero readiness after the renderer has a decoded frame and
playback has advanced when play is requested. The desktop player wrapper
forwards frame availability from the renderer. Its media-loaded flag can
precede the first frame and would prevent fallback on an empty canvas. Play
intent can change during opening, so resume a paused HLS source before waiting
for its first frame. `TrailerPreviewLiveTest` covers starting paused and checks
changing pixels after the preview starts.

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
bundle's bridge with `build/native/macos/<arch>/libplayer_bridge.dylib` before
delivery. Signing can change the whole-file checksum. Compare the Mach-O
`__TEXT,__text` section and exported symbols if the checksums differ.

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
- `TrailerPreviewLiveTest`: changing rendered pixels in both Details layouts,
  a pinned preview resumed from a paused start, and the shared hover renderer
  using Hope. Add `NUVIO_TRAILER_LIVE_ALL=1` to include Bad Apples in the hover check.

The ordinary-media live check expects a generated 12-second AVC fixture at
`composeApp/build/qa/ordinary.mp4`; use ffmpeg's moving `testsrc2` source without
audio. Live checks never use real accounts. `TrailerReviewMain` is a test-only QA
bundle entry point requiring both isolated storage and the in-memory preferences
factory; the normal standalone review app retains the normal app entry point.

## Trailer selection

Desktop Details hero autoplay ranks the existing metadata uploads by advertised
resolution, with trailer category, official status and publish date as
tie-breakers. Hover cards and mobile hero autoplay prefer category and official
status before resolution. `DesktopHeroTrailerTest` covers upload and rendition
ordering, the single-variant master and audio group, and fallback.
`HeroTrailerSelectorTest` covers the category-first selector.

## Desktop Details hero layout

**Settings → Detail Page → Hero trailer layout** persists Backdrop or Corner per
profile. Backdrop is the default and the layout below 1000 dp. The Corner
preview sits over the upper-right artwork and its backdrop scrim. Use
NuvioTV's `ModernHomeHero` crop and 1.35× overscan for encoded letterbox
bars. Apply its fade curves only to the left 8% and bottom 10% of the video.
Keeping the backdrop scrim beneath the preview preserves the picture across
the rest of the corner. Check a letterboxed trailer such as *The Return of the
King* with the artwork still visible outside the video.

Keep the session returned by `rememberDesktopHeroTrailer` above both layout
branches in `MetaDetailsScreen`. Moving a player subtree between the backdrop
and the lazy hero can recreate the player at the breakpoint. Each host draws
the retained session and owns its fade and mute controls.
`TrailerPlayerSettingsUiTest` checks selection and persistence. In MainKt,
resize across the breakpoint and check for a size-change diagnostic with no
second player open.

## Native toolbar pointer re-entry

The native toolbar must reveal on pointer movement over the player surface after
idle hiding. Hidden toolbar elements have `pointer-events:none`, so their own hover
handlers cannot reveal them. Trailer sessions must handle the same cursor/chrome
visibility events as `PlayerScreenRuntimeUi`; preserve that behavior in both
presentations. `scripts/tests/trailer-chrome.test.mjs` checks the shared reveal path.
