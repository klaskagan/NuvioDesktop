# Go3 desktop integration and development checks (macOS)

The built-in Go3 catalogue and playback flow is implemented in Nuvio Desktop.
The obsolete opt-in smoke window and its auto-play launch script were removed;
normal Search and Watch flows are the app entry points. The hosted-addon proposal
was dropped by user choice. No cloud service or addon is used.

Playback opens the official Go3 player in a separate Castlabs Electron window
because the native player cannot handle Go3 Widevine. The runtime is a local
prerequisite and is not committed or packaged. Production signing and
redistribution have not been validated. The helper delegates manifests, license
exchange, and playback to Go3; it does not copy Chrome data, capture license
bodies, extract keys, bypass DRM, or disable output protection.

The user-approved Go3 browser session persists only in the private local
`~/Library/Application Support/Nuvio/Go3` directory (or an explicit
`NUVIO_GO3_SESSION_DIR`). It is not part of Nuvio account synchronization.
Sanitized evidence is written to a private temporary directory; no account tokens,
cookies, signed playback URLs or license bodies are included in the repository.

Metadata and helper checks:

```sh
node --test scripts/go3-live-probe/*.test.cjs
./gradlew -I scripts/desktop-ui-tests.init.gradle :composeApp:desktopTest --tests 'com.nuvio.app.Go3IntegrationTest' --offline
```

Confirmed public discovery on 2026-10-29: the official live-sports section returned
`Go3 · ATP 500 Vienna. Singles R2 Spa. 29 · 14:30 TIESIOGIAI`, channel `10267383`.
The initial broad sports/tennis-page collector missed the lazy-loaded shelf.
The regression rule is to read the official live-sports section and wait for
programme labels, not count footer links as catalogue readiness.

## Verified live outcome — 2026-10-29

**Automatic discovery: passed. Live playback: passed in the Nuvio-managed
separate player window.** The user confirmed the Vienna entry played before
closing the window. Nuvio discovered 48 entries and automatically selected channel
10267383; the supplied URL was not inserted into the results.

After Vienna was selected again during manual channel testing, sanitized
diagnostics recorded DASH HTTP 200 and two Widevine HTTP 200 completions.
Eight video samples over 35 seconds advanced from 1901 to 1936 seconds;
resolution adapted from 1024×576 to 1920×1080, with MediaKeys attached,
readyState 4, paused false and media error 0. These samples precede the next
channel selection, so they belong to Vienna rather than the other tested channels.
No protected frames were captured.

The dedicated session directory is private (0700) and persists locally with user
permission. Reuse after a fresh application launch, session expiry, live seeking,
pause/resume and license renewal were not verified in this live test. A long
idle window lifetime does not count as continuous playback. Go3 can request
sign-in again or enforce its device/subscription limits.

This proves the requested minimal discovery-to-live-playback path on this Mac.
Embedding in the main Compose window, native mpv support, production runtime
distribution, addon-only delivery and Android TV remain outside this result.

## Built-in integration (supersedes the hosted-addon proposal)

The user chose built-in Go3 catalogues rather than a hosted addon. Nothing has
been published to GitHub or Cloudflare. The separate `../go3-feasibility/inactive-addon-prototype` skeleton is
inactive and is not used by the desktop application.

Normal macOS MainKt startup installs a local Go3 service. Search → Discover →
Sport includes **Go3 Live Events** and **Go3 Premier League** alongside existing
sources, without installing an addon or changing account/catalogue configuration.
Cards use Go3 landscape images, programme titles and availability labels. Missing
images use a Go3 text placeholder. Programme details dispatch their exact official
`/live_tv/.../programme-ID` route, including replays, to the same managed window.
Filters currently cover availability; no inferred sport or match metadata is used.
Source schedule labels are shown as provided rather than guessing absent dates.

Settings → Content & Discovery → Go3 opens official sign-in and offers a confirmed
local session reset. UI status describes local actions, not a verified account
entitlement. Go3 itself checks entitlement and expiration during playback.

Configuration for development:

- `NUVIO_GO3_ELECTRON`: absolute Castlabs Electron executable path.
- `NUVIO_GO3_HELPER`: absolute path to this repository's `scripts/go3-live-probe`.
- `NUVIO_GO3_SESSION_DIR`: private persistent browser-data folder. The explicitly
  approved investigation profile is reused when present; otherwise desktop uses
  `~/Library/Application Support/Nuvio/Go3`. No Chrome profile is copied.

One hidden browser reads public programme cards from the official live-sports and
Premier League pages. It collects only programme URLs, image URLs with allowlisted
transformation parameters, titles and labels. It excludes channel/footer links,
preserves programme identity and serializes requests to prevent competing page
navigations. Listings cache for two minutes in memory. Failed refreshes can show
previously loaded cards marked STALE; Go3 still decides availability at playback.
The catalogue currently covers cards rendered by those pages, not an asserted
complete historic archive or account-specific subscription inventory.

Observed local service check: 3 live programmes, all with artwork; 16 EPL entries
(upcoming and replay), 10 with usable artwork. Every result had a programme link.
One observed replay artwork URL returned HTTP 200 without cookies. Direct public
HTML retrieval returned 403; this integration uses the official browser runtime.
No cloud scraper is involved.

Development checks:

```
node --test scripts/go3-live-probe/events.test.cjs
NUVIO_GO3_ELECTRON=/absolute/path/to/Electron \
NUVIO_GO3_SESSION_DIR=/private/profile \
node scripts/go3-live-probe/service-check.cjs
./gradlew -I scripts/desktop-ui-tests.init.gradle :composeApp:desktopTest --tests 'com.nuvio.app.Go3IntegrationTest' --offline
```

Node 22+ is required for the test runner/runtime installer. Runtime deployment is
still a local development prerequisite; this change does not bundle Castlabs or
establish production signing/distribution. Screen sharing may black out protected
video. Keep capture protection intact.

The verified vendor runtime was cloned to the durable, ignored local directory
`../go3-feasibility/runtime/Electron.app`; its executable was compared byte-for-byte
with the tested vendor executable. Normal development startup uses that location
by default, so the working integration does not depend on `/private/tmp` surviving.
It remains a local prerequisite, not a redistributed Nuvio package.

Interactive protected-playback check (opt-in, isolated Nuvio state): set
`NUVIO_GO3_MANUAL_CHECK=1`, the three runtime/helper/session environment variables,
and run the same isolated test command with
`--tests 'com.nuvio.app.Go3LocalAcceptanceTest'`. It uses real public metadata,
captures the catalogue before playback, opens the first live programme through
its actual detail-screen action, and holds the player for a 15-minute check.
Picture/audio must be confirmed directly by the user; the test does not capture
protected frames or treat dispatch alone as visual playback success.


## Current integration verification result

The user confirmed picture/audio with **no repeated sign-in after a fresh process
launch**, then confirmed pause/resume and seeking worked. Official Go3 playback
returned DASH 200 and two Widevine 200 completions, attached MediaKeys, adapted
to 1920×1080 and reported media error 0. The user explicitly stopped the long
playback test and requested no further video testing; do not report a completed
15-minute test or verified renewal. The development player was stopped accordingly.

The real development UI displayed Sport → Go3 Live Events with landscape artwork,
required programme titles and LIVE badges. A Go3 placeholder is used for missing
artwork. The public catalogue screenshot is at
`composeApp/build/reports/go3/discover.png`; it was captured before player launch.
No protected frame was captured. Session/reset Settings tests use a mock callback
and isolated Nuvio storage; they do not sign out the real Go3 profile.

Detailed sanitized result: `../go3-feasibility/implementation-result.json`. Normal
development launch from NuvioDesktop is `./gradlew :composeApp:run --offline`.
This launches the regular development application; the installed app was not
replaced. Existing Nuvio account/addon state was not changed by validation.

Final non-video validation passed: 3 isolated integration tests, 0 failures and
0 skips, including Go3 Settings navigation, sign-in dispatch and cancellation
of session reset. Node metadata and routing checks passed; desktop compilation
and `git diff --check` passed. Task playback processes were verified stopped.


### MainKt review launch

The normal `./gradlew :composeApp:run` application was launched and left running
for user review. Gradle sets `user.dir` to `composeApp`; the helper resolver now
supports both that directory and the repository root. Both resolve to the same
canonical helper and durable runtime. Tests with explicit helper environment
variables did not expose the original working-directory issue. Preserve this
check when changing the launcher. Repository guidance now requires launching and
leaving MainKt open after visible changes, rather than handing off after tests.


## Review fixes

- Details now have an explicit stationary Back control, clipped/bounded artwork
  and scrollable content. The playback-window explanation was removed.
- Availability filters derive from the loaded catalogue; an absent Live category
  is omitted. Filters use cached snapshots synchronously without another browser
  request, including after cache expiry. Empty Go3 results no longer inherit
  pending-addon loading skeletons. The default label is All events.
- Metadata no longer waits a fixed six seconds or waits on CDM startup. The hidden
  metadata viewport covers more cards, and the collector advances the observed
  Go3 carousel next controls, preserving artwork already collected. Ordinary
  scrollIntoView alone did not materialize all posters because the carousel uses
  transforms/is-visible state. Measured collection: live 8/8 posters in 2.1 s;
  Premier League 20/20 posters in 3.4 s (startup/navigation can add latency).
- Player windows maximize before loading the selected watch page. Playback uses
  Electron's supported no-user-gesture-required autoplay policy. A narrowly
  scoped handler acknowledges only the known Lithuanian false autoplay notice
  via its normal OK button after a DRM video has started. Genuine playback,
  account and entitlement errors are not hidden. No output protection changes.
- Four focused isolated desktop tests passed, including bounded artwork, visible
  back navigation, removed explanation, immediate cached filters and omission of
  absent Live options. Five Node metadata/notice tests passed.

Autoplay policy reference: https://www.electronjs.org/docs/latest/api/structures/web-preferences
Player warning handling still needs normal user review against the live Go3 UI;
no new prolonged playback test was performed.


## Latest UI/reference correction

This supersedes the earlier capped-poster layout: Go3 details now reuse the actual
`DesktopDetailBackdrop`, `DetailActionButtons`, circular `NuvioBackButton` and
fullscreen action from desktop details. The artwork fills the viewport with the
same shared gradients. Native header ownership is scoped for the back control,
not replaced with a generic text arrow. AGENTS, UI consistency and the project
skill now require actual shared-component reuse and name the reference.

The catalogue status row and persistent refresh button were removed. Go3 data is
revalidated on returning to an active Go3 catalogue and every two minutes while
that catalogue remains active. Existing cards stay visible during revalidation;
standard error recovery remains available. Filters remain immediate/local.

The helper uses macOS accessory activation and is named Go3; its Dock entry stays
hidden even while its player is available. This avoids the need for a replacement
Dock icon. The vendor runtime bundle/signature is untouched. Player windows are
constructed hidden at the target display's work-area bounds and are shown after
loading. There is no visible maximize call/animation. Runtime verification reported
Dock visible=false, name=Go3, player hidden=true, player display-sized=true.

An explicit Watch request enables a bounded helper that clicks Go3's own visible
Watch control. The actual signed-in button/label were inspected; a no-click dry
run selected exactly one control. Login inputs, purchase/plan controls, disabled
and ambiguous actions are excluded. The helper does not copy credentials, remove
DRM or click subscription purchases. Actual streaming was not retested.

Four isolated UI tests passed (viewport backdrop, 48 dp Back, navigation, cached
filters and Settings). Seven Node tests passed for metadata and protected helper
guards. Catalogue check retained 20/20 EPL posters.

macOS helper policy reference: https://www.electronjs.org/docs/latest/api/app#appsetactivationpolicypolicy-macos

Latest Discover refinements: catalogue dropdown options whose catalogue/provider
name contains Go3 (case-insensitive) are stably pinned first, including future
catalogues. Go3 loading skeletons use 16:9. Watch snapshots Nuvio's fullscreen
state and sends it to the helper, which applies the same fullscreen state before
presenting playback. Four focused isolated UI tests and compilation passed.
