# Continue on TV

Local-network playback handoff from Nuvio Desktop to modified Nuvio Android TV
and LG webOS clients, including Native Legacy v2.0.8. No account backend deployment or AirPlay conversion is
involved. The receiver resolves its own sources, so a 1080p desktop session can
resume using the TV's higher-quality preferences.

## Use

1. Install the receiver changes in both TV applications; stock releases cannot
   receive a handoff. Receiver patches and their exact base revisions are in
   `docs/tv-handoff/`.
2. Open **Settings → Advanced → Paired TVs**. Discover the TV or enter its local
   IP address. Device setup and editing live here, separate from playback.
3. Pair once. Approve the LG prompt or enter the Sony code. Enter the active
   network adapter's MAC address to enable Wake-on-LAN. Enable the TV's network
   wake setting as well.
4. Play a movie/episode and select the TV icon. Select a saved TV from the player’s TV
   overlay to start the transfer immediately. No setup form or second Send click
   appears. Desktop pauses at the captured position while the TV wakes and loads.
5. On the first transfer, compare the desktop code displayed on both devices
   and approve the desktop on TV. Later transfers from that desktop proceed
   automatically unless the TV already has a player open.
   If Android TV opens profile selection first, select the matching profile;
   the handoff remains pending until the receiver completes or expires.
6. The TV uses its own autoplay preferences. With manual source selection,
   choose a TV stream. Confirmed playback closes the desktop player. If the
   transfer fails, use **Resume here** or retry.

Keep both devices on the same reachable network. Different Wi-Fi bands are
fine if the router allows communication. A TV that cannot wake over the network
must be powered on manually. The desktop's local service runs during transfer;
media is fetched and played by the TV. After confirmation, desktop is no longer
needed for TV playback.

Pairing state is device-wide and stored with owner-only filesystem permissions
under `~/.nuvio/tv-handoff`. Forget removes the local device record. Native TV
remote permissions can also be revoked in the TV's own remote-device settings.

## Protocol and behavior

- Android launches `nuvio-handoff://handoff?launch=<URL-encoded JSON>` through
  the paired Android TV Remote v2 service. The dedicated scheme avoids the stock
  app intercepting a test receiver's handoff. No ADB connection is used at runtime.
- LG uses paired SSAP over WSS to launch the configured app (`space.nuvio.webos` or
  `space.nuvio.native.legacy`) with a `handoff`
  launch parameter. The JS app uses a companion service for pinned HTTPS;
  Native Legacy performs pinned HTTPS in its native network worker. Cold launch
  and webOS relaunch events share each app's receiver.
- Launch parameters contain a local HTTPS endpoint, certificate fingerprint,
  one-time bearer token, request ID and sender/target device IDs. Pairing trust
  is approved on the TV; certificate changes require approval again.
- The receiver fetches `GET /v1/handoff`, then reports states using
  `POST /v1/handoff/status`. Only the chosen TV IP and bearer token are accepted.
  Requests expire after two minutes. Payloads are bounded and never redirect.
- Version 1 sends account/profile identity, movie/episode identifiers, absolute
  position and duration in milliseconds, and an optional movie-edition hint.
  Stream URLs, account credentials and provider credentials are not transferred.
- Desktop resolves the shared account owner before opening the picker, including
  device-linked logins. The TV validates account and active profile. It never bypasses profile PINs.
  Select the matching profile and retry when necessary.
- Cached source reuse and binge-group reuse are bypassed for a handoff. Edition
  matching prefers identifiable matching cuts while retaining TV quality filters.
  Unknown editions can still differ in timeline; source names cannot prove an
  exact scene match.
- The TV seeks using the handoff position ahead of older saved progress. Success
  requires actual playing state within two seconds of the requested position.
  Desktop stops publishing progress after success. Cancellation/expiry invalidates
  pending transfers; the receivers pause cancelled playback.

## Validation

```sh
JAVA_HOME=/usr/local/opt/openjdk@17 ./gradlew :composeApp:desktopTest \
  --tests 'com.nuvio.app.features.handoff.*' --tests '*NativePlayerControlsJsonTest'
python3 -m unittest discover -s scripts/tv-handoff -p 'test_*.py'
```

Desktop tests cover identity/profile validation, expiry, state transitions,
cancellation and late callbacks (including connections arriving after cancel and
atomic cancellation versus playback confirmation), edition hints, bounded Android protocol frames,
network discovery parsing, authenticated pinned HTTPS and Wake-on-LAN packets.
The controls JSON regression test also verifies that the native player's full
payload parses with handoff enabled and disabled, including escaped labels.
Hardware adapter tests are skipped unless `NUVIO_TV_TEST_CONFIG` explicitly points
to private paired-device fixtures. Never commit that configuration.

TV patches include Android deep-link tests and LG protocol/TLS tests. The latter
verify that an incorrect certificate prevents sending credentials. Run
`node --test scripts/test-tv-handoff.mjs` in the patched LG checkout.

The Python preflight tool is development-only. Its libraries are not application
runtime dependencies. See `scripts/tv-handoff/README.md` for hardware checks.

## Desktop UI update on 2026-10-23

Pairing and device editing are restricted to Settings → Advanced → Paired TVs.
The playback TV icon opens the native player's existing rail-modal overlay,
using the same `audio-rail-panel`, `track-row` and audio-track typography as
Audio Tracks and Playback Speed. No separate playback window opens. Selecting
a TV starts handoff without a second Send action. Progress, failure guidance,
and Resume here remain in that overlay. The preparation caption is omitted,
and the empty status footer stays hidden during account readiness so opening
the selector does not shift the TV list. Pairing uses a separate Settings editor
with Nuvio's Compose theme, selected palette and fonts. Existing pairings are
preserved. Opening or dismissing the picker does not pause playback; selection
does. Escape or clicking outside dismisses the overlay and cancels a pending transfer.

Desktop attempts now own their own connections, so cancellation closes even a
connection that finishes late and stale callbacks cannot update a retry. Resume
here atomically cancels the request or observes that TV playback already won.
Shared account-owner lookup supports device-linked desktop sign-ins.

The final desktop package passed 16 automated tests; two hardware adapter tests
were skipped. Both rendered views were visually inspected. The local Intel DMG
was built with `--package-only --skip-arm`; local macOS DMG packaging now verifies the generated app and applies ad-hoc
signing when no Developer ID identity is configured. They are not notarized.
The rail-overlay fixture verified LG selection emitted exactly one transfer
command for index 1, error guidance stayed visible, Resume here emitted resume
and dismiss commands, the selector reopened, and Escape dismissed the overlay. These desktop changes have not
yet been rechecked end-to-end on physical TVs.

## Packaged macOS local-network permission

The prior package lacked `NSLocalNetworkUsageDescription` and `NSBonjourServices`.
The bundle now declares the TV connection purpose and `_androidtvremote2._tcp`
for discovery. Local-network permission is managed by macOS; Nuvio never edits
or bypasses the system permission database.

On 2026-10-23, diagnostics inside installed `/Applications/Nuvio.app` reproduced
`NoRouteToHostException` for a TV TCP connection, while the same production
libraries outside that process reached both TV ports, created a pinned HTTPS
server and reconnected to the already paired LG. The user confirmed Nuvio was
already enabled in Local Network settings. Adding the declarations and refreshing the permission alone did not restore
access. Apple's native Network framework reported unsatisfied reason
`local_network_denied` (3). The user suggested ad-hoc signing; after applying
`codesign --force --deep --sign - /Applications/Nuvio.app` and restarting, the
installed app passed HTTPS-server creation, both TV TCP connections, Sony's
pinned mutual-TLS handshake, and LG's paired registration. No Apple signing
identity was available locally, so DMG packaging now verifies the generated
bundle and applies ad-hoc signing when necessary. A configured Developer ID
identity continues to take precedence. Existing valid signatures are preserved.
The signing task ran successfully in the packaging build, and a read-only mount
verified that the actual DMG contained a valid ad-hoc signed app with the LAN
purpose declaration.
Wrapped webOS transport causes are retained so this error receives actionable
local-network guidance. Wake-on-LAN failures are best-effort and do not abort
a direct connection to an already awake TV.

## Physical checks on 2026-10-22–23

- Sony Android TV 14: standard pairing and launching Nuvio worked, including
  with the production Kotlin adapter. Initial standby wake attempts failed with WAKEUP and the stored pairing MAC;
  the final authenticated power-state-aware wake passed (see final validation).
  The test receiver displays avatars and the desktop approval code after the
  pending-link coroutine cleanup fix. The initial debug build lacked the native
  Dolby Vision compatibility module and stalled on a DV7 source. After enabling
  the module, logs confirmed `CONVERT_TO_DV81`, the real libdovi bridge loaded,
  and the hardware `c2.mtk.dvhe.st.decoder` selected. On 2026-10-23, the user
  confirmed Dolby Vision playback at the transferred timestamp.
- LG OLED42C41LA, webOS 25/10.3.1: pairing and launching worked with the production
  Kotlin adapter. After enabling Wi-Fi wake, the remote interface reconnected
  from standby in about 26 seconds. The first receiver build was installed and
  the user confirmed their selected profile and home metadata remained available.
  After correcting the test build's backend configuration, the user approved
  the matching desktop code and confirmed movie playback at the transferred
  position on the first test. A repeat test exposed intermittent timestamp loss.
  The receiver now preserves explicit timestamps through stream selection and
  confirms only the new player session after seeking and playback advance.
  On 2026-10-23, the user repeated transfers at two positions, leaving LG playing
  between attempts, and confirmed both timestamps were correct.
- Independent Sony TV seeking and physical cancellation tests remain unverified.
  Final Sony wake checks and Native Legacy repeat/cancellation checks are recorded below.

## TV build notes

Supply the existing server's client configuration in each TV checkout's private
`local.properties` before building. At minimum, `NUVIO_SUPABASE_URL` and
`NUVIO_SUPABASE_ANON_KEY` must be populated. The example file leaves them empty:
cached profiles and metadata can still appear while account verification fails.
Do not commit local configuration or use an example-configured build for TV tests.
Set Android's `AVATAR_PUBLIC_BASE_URL` to the server's public avatar image base
as well; desktop uses `<backend>/storage/v1/object/public/avatars`.
For the Sony test build, enable the upstream Dolby Vision compatibility module
with `DOVI_NATIVE_ENABLED=true`, `DOVI_EXTRACTOR_HOOK_READY=true`, and
`DOVI_ENABLE_REAL_LINK=true`. The checkout includes per-ABI libdovi archives in
`DV7/libdovi`. A build with these flags off can select native DV7 playback on a
DV-capable display even when the device cannot decode the selected profile.

The Android upstream debug variant references an unavailable release signing key.
For a separate local debug app, use an external Gradle init script to select the
standard debug signing config. This does not change the receiver patch:

```groovy
allprojects { project ->
    project.pluginManager.withPlugin('com.android.application') {
        project.extensions.getByName('androidComponents').finalizeDsl { android ->
            android.buildTypes.getByName('debug').signingConfig = android.signingConfigs.getByName('debug')
        }
    }
}
```

Build with `./gradlew -I /path/to/debug-signing.gradle :app:assembleFullDebug`.
The upstream debug application ID is `com.nuviodebug.com`. It has separate
sign-in/settings from the production app. For the Sony test device, select the
`armeabi-v7a` APK. Installing this test build does not upgrade the production app.

LG packages with `npm ci --ignore-scripts` followed by `npm run package:webos`.
An in-place developer installation uses the existing `space.nuvio.webos` app ID.

## Native Legacy receiver (v2.0.8)

`NuvioNativeLegacy.patch` targets iqui27/nuvio-native-legacy tag `v2.0.8`,
commit `87c8b8909f0d605f1d8351147fe5afb9de233b8d`.
Set the paired LG device's app ID to `space.nuvio.native.legacy`.
The native receiver uses the existing title/source router and playback engine.
It carries the requested absolute position independently of cloud progress,
ignores an old saved source during handoff, prefers a matching edition label,
and refuses to acknowledge a materially different running time. A newly opened
matching player must advance near the requested position before `PLAYING`.
The receiver does not change video decoding, audio track selection, or DTS/DV
conversion behavior. Manual TV source selection remains available when enabled.

Native webOS uses libcurl's SHA-256 SPKI pinning (`publicKeyPin` in the launch
parameters), checked during TLS before the bearer header is sent. HTTP and
redirects are forbidden. Existing Android and JS receivers continue using the
certificate fingerprint. Legacy's first-pair code is the first six characters
of the SPKI digest's Base64 encoding; desktop displays the same code.
Account owner, active profile, request ID, expiry, and content/episode identity
are checked before starting playback. Trusted senders and recent request IDs
are persisted in the application's data folder; launch tokens are not.

Retail LG SDL owns native lifecycle delivery. Its public SDL logging callback
receives the lifecycle JSON, so the adapter consumes bounded launch payloads
there and suppresses JSON system messages instead of exposing their credentials
in logs. SDL continues handling its own window/power lifecycle. No SDL symbol
interposition, additional network listener, or privileged LS2 registration is
required. Requests received during startup wait until the application is ready.

Build prerequisites are the upstream ARM SDK container, private client config,
and the upstream Rust/libass/DTS/DV dependencies. On Intel macOS, build the SDK
with `--platform linux/amd64` and the SDK's `x86_64` host archive. The patch adds
`NUVIO_RUST_LIB` so a library built inside that container can be used without
installing a second Rust toolchain on the Mac. Use `NUVIO_CTR=docker` and
`NUVIO_BUILD_PLATFORM=linux/amd64`, then `bash tools/arm.sh --high-cache --ipk --build`.
This generates a package without deploying through the upstream root-SSH path.
Install it using the configured webOS Developer Mode device and `ares-install`.
The local test package includes 300 MB texture cache, libass, Rust MKV parsing,
DTS conversion, and DV7 metadata conversion. The final local package also includes the original direct P2P motor
(nuvio-engine + libtorrent + static OpenSSL). Its pinned upstream source and
stream-window patch are unchanged. The ARM build helper accepts the host
platform and stages its scratch header correctly on Intel macOS.

Before an in-place update, preserve the installed app's `.nuvio` directory and
original package/binary. The development update retains the existing application
ID and user data. A private backup was made before updating the physical LG.
Do not distribute that backup: it contains the existing account's private data.

Native checks: `bash tests/handoff.sh` (request validation and actual pinned TLS),
`bash tests/deeplink.sh`, and `bash tests/rede_pedido.sh` with Node 20 on PATH.
The network suite also exercises body/header limits, cancellation, redirects,
request generations, and bounded buffering.

## Readiness and cancellation hardening

Desktop now waits for a receiver response, rather than treating a successful TV
launcher reply as readiness. It repeats the same launch request for up to 60
seconds while the TV wakes. Receivers deduplicate request IDs. Cancellation,
certificate failures, and local-network denial stop retries; an obsolete attempt
cannot update the new attempt's UI or keep its late connection alive.
Eight deterministic readiness tests cover delayed app startup, cancellation,
identity failures, and bounded wake retries.

Android dismisses an outstanding approval dialog when the desktop session is
cancelled or expires. Playback confirmation belongs to the newly initialized
player and requires playback advance, a rendered frame, and no buffering.
Cancellation also pauses a pending buffering player. The JS webOS receiver queues
the latest retry, cancels the superseded pending request, polls approval expiry,
and cleans up its approval timers and event handlers.

The installed signed macOS app was updated after the final build. Read-only
checks inside that actual process passed local HTTPS-server creation, both TV
TCP connections, Sony pinned mutual TLS, and the already paired LG registration.
The actual final DMG's embedded app signature and LAN declaration were verified.
The targeted desktop suite passed 31 tests; the physical tests remain opt-in.


## Final physical validation, 2026-10-23

The Native Legacy receiver passed real TV playback at requested 15:00 and 20:00
positions, including a second transfer while already playing. Measured receiver
acknowledgements included 15:00.129 and 20:00.149. Three identical launch commands
produced one received request and one playback. Cancelling an approval invalidated
the desktop endpoint (HTTP 410), and the subsequent retry played at 15:00.269.
These checks verify the native timeline and playback acknowledgement; audible
sound and subjective picture quality still need the user's confirmation.

The initial global Wake-on-LAN broadcast did not wake LG from this standby test.
Binding to the TV's LAN route and sending its directed subnet broadcast restored
the paired remote interface in 16 seconds. The TV then played the transferred
movie at 15:00.272. The desktop now sends directed and global broadcasts through
that LAN interface and repeats wake packets during readiness retries.
An unreachable sleeping TV is not treated as proof of macOS permission denial;
explicit OS permission errors still fail immediately. Final timeout guidance
covers both TV reachability and Local Network permission.

Sony's final receiver acknowledged real playback at 15:01.035 for a requested
15:00, after profile selection. A later 4K Dolby Vision file reported
`ERROR_CODE_DECODING_FAILED` despite advertised support; the receiver correctly
did not acknowledge playback or close the sender. Choosing another TV source
remains necessary for incompatible files. This is separate from transport or
resume-position confirmation. The earlier stored Sony wake MAC came from the
pairing identity; inspection of the active Wi-Fi interface showed a different
address. The saved device now uses the active adapter address. Pairing identities
must not be used as a substitute for the active network adapter's wake MAC.

Sony keeps its paired remote service available during standby but ignored the
standard WAKEUP command in physical tests. The final adapter reads the fresh
authenticated power report and sends POWER only when that report explicitly says
off. An already-on or unknown state uses the idempotent WAKEUP command. It waits
for a reported on state before launching the receiver. The installed final build
woke Sony in 1.29 seconds after standby; Android independently reported Awake and
the screen ON. Repeating the launch while already awake left it awake. Four
additional tests cover off, on, unknown, and malformed/duplicate power reports.

The final full Native Legacy package retains its existing P2P engine and account
configuration. Its protocol/TLS, content-link, and network suites passed again,
including IMDb/TMDB matching and bounded UTF-8 titles. Receiver source patches
were checked both against their clean base indexes and in reverse against the
modified checkouts. No user credentials or app data are included in the patches.

After the last rebuild, the full Native Legacy IPK was installed in place and
tested again. Three identical launch commands produced one transfer; playback
was confirmed at 15:00.148 for the requested 15:00. The installed final desktop
process again passed both paired TV connection checks. The packaged DMG was
mounted read-only, and its embedded app signature and Local Network declaration
were verified. Five development preflight tests also passed. Final package hashes
are in `build/continue-on-tv/SHA256SUMS`; that directory also contains a short
installation guide.

## Settings, discovery, and Native Legacy follow-up

Paired TVs is an inline Settings subpage under Advanced. It no longer creates a
Swing/Compose window. Saved pairings survive changes to name, MAC, or selected
receiver app; saving those changes does not repeat device pairing. Both platforms
show an app selector and an editable app ID. Sony Native Legacy uses the
separately signed Android test receiver (`space.nuvio.nativelegacy.strictdebug`),
which leaves the stock installation intact. The distinct Android handoff schemes
prevent the Nuvio and Legacy receivers from intercepting each other's transfers.

The native receiver is shared by webOS and Android. Android forwards bounded,
validated launch JSON through JNI for both cold intents and `onNewIntent`; its
worker queues launches until the main app is ready. Public-key pinning and
account/profile checks are identical on both native platforms. Android debug
builds use a separate package and the launcher label “Nuvio Legacy TV.” They
cannot replace the upstream APK because its private signing key is unavailable.

For a previously trusted desktop, a cold Native Legacy handoff can reuse the
saved, unlocked profile when it matches the requested account and profile. It
does not switch profiles silently or bypass a PIN. First-time trust, different
profiles, and locked profiles still use the normal selection/approval screens.
A real cold LG transfer with all remote-input simulation disabled confirmed
playback at 15:00.127 for a requested 15:00. Ordinary app launches retain Legacy's
normal profile chooser.

Find TVs now queries broader SSDP services on each active private LAN interface,
in addition to Android remote DNS-SD. Sony's IRCC/Scalar service advertisements
are accepted even when no Android remote DNS response arrives. DNS-SD listens
on the multicast port as well as requesting unicast responses. A bounded local
`seen-tvs.json` cache retains only names, addresses and platforms, without
credentials. Previously seen reachable addresses are checked at the control port
and clearly labeled as previously saved addresses; this is not a subnet scan.
Forget removes the pairing credentials while retaining this discovery history.
An awake LG at the same reachable address can therefore be found again even if
it does not advertise. If its address changes and multicast discovery is blocked,
manual IP entry remains necessary. Rediscovery does not restore a forgotten
pairing: pair again to authorize control.

The source checkouts now live in `~/code/NuvioTV`,
`~/code/NuvioTVSmart`, and `~/code/NuvioNativeLegacy`.
Keep modifications on their local feature branches and retain the source overlays
in this repository. `scripts/tv-handoff/receiver_updates.py` prepares fresh
upstream checkouts and applies the overlays using a three-way merge, stopping on
conflicts. Its manual GitHub Actions workflow checks compatibility and uploads a
review diff; it does not install stock packages over modified receivers. See
`scripts/tv-handoff/README.md` for commands and signing/configuration requirements.

Modified Native Legacy builds identify themselves as “Continue on TV build” in
the update settings. Their upstream installer is disabled so a stock update
cannot silently remove the receiver or make the separately signed Android test
app update the stock app. New upstream versions must go through overlay
preparation and rebuilding. This restriction is specific to the modified native
receiver; the separately installed stock Sony apps keep their own update flow.

Sony Native Legacy's Android build was installed alongside the original app.
After signing in and selecting the matching profile, a real transfer through the
production paired Android remote and pinned HTTPS server acknowledged PLAYING
at 20:00.690 for a requested 20:00. The native approval dialog's corner radii
were corrected to the renderer's normalized units, restoring a visible selected
button. Remote OK aliases (including Space and Select) are accepted alongside
Return, with a fixture exercising the actual input handler.

The final desktop Settings build passed 36 targeted tests and was installed.
A diagnostic inside that exact installed app discovered both physical TVs from
SSDP advertisements, without the saved-address fallback. The native Android
launch-parser suite passed three tests; eight Python preflight/update tests passed.
The optional upstream macOS update UI test could not compile because this Intel
Mac lacks SDL2_image headers; it is not part of the receiver's portable checks.
Source patches were exported with full Git object IDs and retained with their
base commits. Local feature-branch checkpoints preserve the TV changes independently
of temporary build directories. See `docs/tv-handoff/BUILDING-LOCAL.md` for durable
build-cache paths and package commands.

The final Android Legacy APK retained the signed-in test app's data across its
in-place update. A cold-launch transfer with no simulated remote input confirmed
PLAYING at 20:00.781 for a requested 20:00, reusing the saved unlocked profile.
All three receiver patches passed clean-base application using isolated indexes
and reverse application against their local feature checkpoints. The current
macOS artifact is `Nuvio-Continue-on-TV-settings-and-Legacy-macOS-x86_64.dmg`; the
earlier native-overlay DMG remains available as the previous build.

Final Android Legacy consent verification showed correctly rendered selected and
unselected buttons. Android DPAD_CENTER (keycode 23) approved replacement of the
active test movie; the receiver confirmed PLAYING at 20:00.667 for 20:00. The
final APK and LG IPK were installed, and their copies/checksums were refreshed
in `build/continue-on-tv/`. No upstream app update or account data migration was
performed; the user signed into the separate Android test app.


### Paired TVs polish and unexpected LG wake investigation (2026-10-23)

The settings-polish desktop build uses the same SettingsSection, SettingsGroup,
and SettingsNavigationRow components as the existing Settings pages. It removes
the extra panel title and Back footer, replaces Edit text with the shared pencil
icon, and makes discovery and Add/Edit mutually exclusive. Late discovery
results cannot reopen a form after the user changes panels. Cancel remains an
editor action; the page retains its normal Settings navigation.

The build passed 37 targeted desktop tests, including private bounded wake-log
coverage, and was installed in /Applications/Nuvio.app. The latest reinstall
artifact is Nuvio-Continue-on-TV-settings-polish-macOS-x86_64.dmg.

Wake audit: settings opening and player TV selection opening do not send magic
packets. Find TVs performs discovery/address probes, not Wake-on-LAN. Only an
active selected-TV transfer sends wake packets. Sony now tries the authenticated
remote first and sends magic packets only after a retryable connection failure
while that transfer is still current. LG retains selected-transfer wake packets.
Actual successful socket sends are logged to the bounded owner-private
~/.nuvio/tv-handoff/wake-events.log (UTC timestamps and target address/MAC only).
The log records sends, not a TV acknowledgment, and does not cover earlier builds.

The user reports that unexpected LG wakes predate these changes and have happened
with desktop Nuvio fully quit, sometimes following an iPhone update/restart. LG
probably shows LG Channels; a PS4 is connected. The iPhone has both Apple Home
and LG ThinQ registrations. Opening ThinQ without TV control did not reproduce
a wake during the controlled test. Separately opening Apple Home without TV
control also did not wake LG. No current causal attribution is established.
No wake commands or LG control-port probes were sent during this isolation.
Passive capture of LG-targeted magic packets on the Mac cannot exclude encrypted
HomeKit/AirPlay control, HDMI-CEC, or unicast traffic invisible to the Mac.

On the next unexpected wake, leave LG on and note the local time and screen.
Read-only power-reason retrieval can then be attempted without deliberately
waking it and replacing the evidence. Retail webOS may deny or lack that API.
LG lists timers and HDMI-linked devices as possible automatic power sources:
https://www.lg.com/us/support/help-library/lg-tv-why-does-my-tv-turn-on-by-itself--20153353111993
Do not disable all integrations at once; isolate one path per observation period.
