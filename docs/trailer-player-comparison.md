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
outside the media rectangle; `fs=0` reserves expansion for that explicit action.
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
The WebView uses a nonpersistent data store. Do not open external windows or an
automatic browser fallback. Show a retryable error for private/unavailable uploads,
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

Physical speaker output and other operating systems require separate validation.
