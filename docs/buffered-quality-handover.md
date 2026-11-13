# Desktop buffered quality handover

Accepted by the user on 2026-10-31 after testing real Torrentio playback and the
quality badge transition. Choices are session-local and reuse cached Sources.

## Behavior

On the ordinary macOS native player, selecting a tier closes the Quality overlay
and shows the requested tier with a breathing badge. A muted, paused offscreen
renderer follows the current position and restores playback preferences while
the original source remains active. Promotion requires decoded video, completed
seeking, and approximately two seconds of buffered output (or remaining duration
near the end). The prepared surface moves into the existing host; Compose adopts
it without loading it again. Latest pause, speed, volume/mute, tracks, and subtitle
delay are retained on a best-effort basis. Different releases may differ in edits,
duration, or available tracks; the cloud Sources picker remains the manual escape.

The hidden controls document is seeded with its target tier before attach.
WKWebView evaluation is asynchronous: updating only at promotion can reveal a
stale label before JavaScript completes. The delayed-document regression covers
all visible frames through commit. Decoded dimensions remain separate metadata.

Cancelled, failed, and timed-out preparation retains current playback. New titles
and selections invalidate late work. The switch timeout is 30 seconds. Loading
state clears before asynchronous native cleanup; hidden host ownership lasts
until native release completes. Native tracks use a single track-list snapshot,
including large releases with many languages, rather than many per-field reads.

PiP, embedded surfaces, Windows, and Linux retain controlled reload. This version
has no automatic quality adaptation or new DASH/HLS manifest variant discovery.
Unknown unplayed resolutions remain in Sources. Cached addon markers are preferred
to download-only alternatives, independently of native debrid configuration.

## Verification

Headless coverage includes cached-source parsing/ranking, Torrentio availability,
unknown/current decoded tiers, rollback/timeout/cancellation, latest pause state,
obsolete titles, state serialization, and delayed controls-document updates.
Native synthetic media tests cover delayed HTTP buffering, prepared-player
cancellation/promotion, decoded output, and full Compose surface adoption.
Fixtures: `scripts/create-quality-test-fixtures.sh`. Use
`scripts/desktop-ui-tests.init.gradle` to isolate both profile and preferences.
Set `NUVIO_QUALITY_NATIVE_TEST=1` only for the native window tests.

Real Pulp Fiction/Torrentio QA exposed the source-selection and native track-query
issues. The user subsequently confirmed quality switching and the final badge
fix in MainKt. Windows/Linux runtime results remain unverified. Desktop toolbar
hover improvements are implemented; final stationary-hover confirmation remains
separate from the accepted quality-switch result.

## Related desktop fixes

Desktop WebView owns chrome idle timing, including hover, focus, scrubbing, and
menus; a shared Compose timer must not override it. Toolbar gaps also count as
interaction, and CSS hover guards cached pointer state after native focus changes.

History-gesture observers queue installation/removal on AppKit asynchronously.
Synchronous AWT-to-AppKit dispatch deadlocked with accessibility waiting for AWT.
Observer lifetime and an inactive flag protect cancellation before installation.
