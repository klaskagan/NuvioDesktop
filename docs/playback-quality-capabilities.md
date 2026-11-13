# Playback quality and audio: engine capability audit

Step 1 of the quality/audio controls plan. Audited 2026-10-28 against desktop
`fad3cf3e` and Mobile Enhanced `313ca3d1`, including the working-tree sources.
This is a source inspection, not a runtime certification. No playback behavior,
settings, accounts, or installed applications were changed.

## Capability matrix

“Available” below means present in the inspected integration. “Candidate” means
engine support exists but Nuvio still needs integration and playback validation.
No engine path is certified for seamless quality changes by this audit.

| App / active engine | Quality discovery today | Audio selection today | Network measurements today | Quality-switch decision for later steps |
| --- | --- | --- | --- | --- |
| Desktop macOS / libmpv | No quality-list contract or manifest resolver in the player; media properties can describe the playing video | Native track enumeration and selection through mpv track IDs | Buffered position/loading exposed; no shared throughput sample | Controlled reload; seamless adaptation unverified |
| Desktop Windows / libmpv | Same common contract; native property getter available | Native track enumeration and selection | Buffered position/loading exposed; no shared throughput sample | Controlled reload; seamless adaptation unverified |
| Desktop Linux / libmpv | Same common contract; native property getter available | Native track enumeration and selection | Buffered position/loading exposed; no shared throughput sample | Controlled reload; seamless adaptation unverified |
| Mobile Android / ExoPlayer (Media3 1.8.0) | Engine track groups/formats available internally; shared menu instead uses the HLS resolver | Embedded/manifest track extraction, selection overrides, language preferences | `onBandwidthEstimate` receives bytes, elapsed load time and bitrate estimate, but only logs them | Candidate for native adaptation within compatible HLS/DASH track groups; separate-source changes reload |
| Mobile Android / libmpv | Shared HLS resolver; mpv track/media properties | mpv track-list enumeration and audio selection | Cache timestamp/loading/seeking read internally; no throughput exported | Controlled reload; seamless adaptation unverified |
| Mobile iOS / libmpv | Shared HLS resolver; Swift bridge exposes string properties | Logical indexes mapped to mpv IDs; bridge selects audio | Buffered position and loading exported; no throughput exported | Controlled reload; seamless adaptation unverified |

Desktop dispatches macOS, Windows and Linux to `NativePlayerSurface`, backed by
libmpv, rather than the vendored compose-media-player. Other desktop OS values
use a stub. Mobile Android Auto starts with ExoPlayer and can fall back to
libmpv; capabilities must follow the effective engine, not the preference.
Mobile iOS movie playback uses the mpv bridge, not AVPlayer. Trailer, Cast and
external-player paths are outside this audit's built-in playback scope.

## Evidence and concrete gaps

Paths in this section are relative to each application's repository root.

### Common contracts and source policy

- Both apps: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerEngine.kt`
  exposes audio/subtitle selection, seek, retry and speed, but no video variant
  enumeration, quality selection, adaptation capability or network estimate.
- Both apps: `PlayerModels.kt` in the same directory defines
  `PlayerPlaybackSnapshot` with loading, position, buffered position, speed and
  dimensions. It has no throughput, sample validity, adaptation state, or explicit
  seeking/rebuffer-reason fields. Loading alone cannot safely trigger fallback.
- Both apps: `features/streams/StreamAutoPlaySelector.kt` already filters source
  eligibility and regex matches. Quality ranking must consume the eligible pool,
  not replace these constraints. Existing `PlayerStreamsRepository` and source
  actions are the integration points for alternatives, not an engine API.
- Mobile `SubtitleAudioModels.kt` has audio index, ID, label, language and selected
  state. Role, channels and codec are not separate audio-model fields. Labels
  alone cannot prove two tracks are duplicates or preserve commentary intent.
- Both apps have `PlayerScreenRuntimeAudioPreferences.kt` and track preference
  storage; reuse their language/identity restoration rather than carrying a
  numeric index between sources.

### Mobile quality resolver

`composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerQualitySelector.kt`
and `PlayerScreenRuntimeUi.kt` show:

- Discovery is restricted to URLs containing `.m3u8`; runtime inspection also
  excludes active torrents and separate-audio URLs. Extensionless HLS and DASH
  are not covered by this resolver. A fixed file produces no alternative tiers.
- Master parsing reads dimensions, codecs, declared bandwidth and audio groups.
  It prepares a temporary master containing one video variant and its relevant
  audio renditions. This preserves audio groups but removes video adaptation.
- Auto resolves to the highest recommended variant's temporary playlist, not
  the original multi-variant master. Recommendation excludes AV1 heuristically,
  then maximizes resolution/bitrate; it does not inspect actual decoder support,
  connection throughput or display capabilities.
- Variants retain parser order; there is no resolution-tier grouping/deduplication.
- `selectPlayerQuality` in `PlayerScreenRuntimeSourceActions.kt` changes playback
  URL, clears playback dimensions, and sets `shouldPlay = true`. It is not a
  transactional switch preserving pause state with rollback.

### Android ExoPlayer

`composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt`
creates `DefaultTrackSelector` and `DefaultMediaSourceFactory`, observes tracks
and video size, and handles audio overrides. The Android dependency catalog
includes HLS and DASH modules. A five-second logging throttle surrounds
`AnalyticsListener.onBandwidthEstimate`; no estimate reaches common state.

Media3 documents bandwidth/buffer-based
[AdaptiveTrackSelection](https://developer.android.com/reference/androidx/media3/exoplayer/trackselection/AdaptiveTrackSelection).
This establishes an engine capability, not proof of seamless behavior for every
Nuvio stream. Compatible tracks in an adaptive group, decoder support, and the
full manifest are prerequisites. Current one-variant rewriting blocks that path.
An arbitrary alternative addon URL is a new media item, not an adaptive track.

### Desktop and mobile libmpv

- Desktop: `composeApp/src/desktopMain/kotlin/com/nuvio/app/features/player/PlayerEngine.desktop.kt`
  disposes the controller on source changes. `desktop/NativePlayerController.kt`
  builds snapshots without setting video width/height, although media info can
  read properties. These default to zero in the normal snapshot. Expose decoded
  dimensions for a live quality badge rather than inferring from a source title.
- Desktop native implementations: `composeApp/src/desktopMain/native/macos/player_bridge.mm`,
  `windows/player_bridge.cpp`, and `linux/player_bridge.cpp` open media through
  `loadfile`. They expose audio IDs and cache/loading properties, with no app-level
  HLS variant selector or throughput policy.
- Mobile Android uses `loadfile ... replace` for mpv source loading and reads
  track-list, seeking, cache and dimensions. Mobile iOS
  `composeApp/src/iosMain/kotlin/com/nuvio/app/features/player/PlayerEngine.ios.kt`
  delegates to `iosApp/iosApp/Player/MPVPlayerBridge.swift`, which also uses
  `loadfile ... replace` and provides `getProperty`.
- No `hls-bitrate`, `cache-speed`, or `raw-input-rate` policy integration was found
  in the inspected desktop bridges or mobile player integrations.

The [mpv manual](https://mpv.io/manual/stable/) documents `hls-bitrate` as default
stream selection, not a guarantee of bandwidth-driven seamless adaptation.
It also documents cache rate properties. These are possible telemetry hooks,
not an implementation already present in Nuvio; bundled-version support and
sample validity must be checked. Cache position and encoded video bitrate must
not be treated as measured internet bandwidth. A full cache can stop reads.

### Media-server audio

Both apps' `PlayerScreenRuntimeServerTracks.kt` expose server-provided audio
tracks. Selection may restart a server playback session and replace its URL;
mobile captures position and guards stale results. This is not inherently an
in-place track switch. Preserve the server session/subtitle lifecycle and add
transactional restoration tests rather than assuming all audio changes are instant.

## Implementation decisions and validation gates

1. Add shared capability/state contracts in the later implementation steps:
   effective engine, available tiers/candidates, actual dimensions, valid network
   samples and explicit seek/startup/rebuffer state. Probe capabilities per device;
   remove the blanket AV1 compatibility assumption. This audit adds no runtime API.
2. Use a controlled, cancellable reload transaction for every separate-source
   switch and every currently inspected mpv quality switch. Capture position,
   pause, speed, volume and track preferences; commit after load/seek success and
   restore the old source on failure. Existing URL reassignment is not sufficient.
3. Treat Android ExoPlayer adaptive HLS/DASH as the first seamless candidate.
   Preserve the full manifest in Auto, expose supported video groups and use
   track constraints for manual ceilings. Do not enable new seamless behavior
   until an actual multi-variant playback test passes. Existing engine defaults
   are not modified by step 1.
4. Keep seamless upgrades disabled for mpv integration work unless a separate
   device/manifest test establishes a supported switching mechanism. Do not
   substitute `loadfile` for seamless adaptation or promise instant switching.
5. Before automated cross-source fallback, require verified same-language audio
   availability and the existing eligible/regex-filtered pool. Unknown language
   cannot establish safety. Prefer the same release; test timeline differences.
6. Validate each enabled path using multi-quality HLS (including audio groups),
   DASH for ExoPlayer, fixed files, authenticated headers and failure fixtures.
   Shape bandwidth down/up; observe selected dimensions, buffering events, audio
   language, and whether the media item/controller restarts. Exclude startup and
   seeks from rebuffer counters. Check manual ceilings, cooldowns and stale jobs.
7. Verify paused switching, audio-description/commentary identity, unavailable
   language, server-session restarts, timeout rollback and rapid title changes.
   Exercise desktop macOS/Windows/Linux and Android ExoPlayer/libmpv plus iOS mpv;
   a result on one OS or engine does not certify the others.

## Validation performed for step 1

Read-only source inspection and official engine documentation review; report
paths and referenced symbols checked against both working trees. No playback,
network-shaping, builds or device tests were run. Runtime codec/display probing,
bundled mpv property availability and seamless switching remain unverified.
No automatic quality changes were enabled. Existing unrelated working-tree edits
were left intact.

## Desktop quick quality implementation (2026-10-30)

This section supersedes the desktop gaps in the historical audit and the earlier
strict-probing design. Quality is a fast shortcut over the already-known source
list. It does not inspect every media file or reject language/timeline differences.

- Cache stream-picker results for the current type/video/season/episode when
  playback starts. If no matching selection cache exists, fetch once in the
  background at playback start. Opening Quality never fetches sources or probes
  tracks/manifests. Source-list updates refresh the in-memory quality choices.
- Show one entry per reported resolution, descending. Preserve provider/source
  order within a tier and prefer the currently playing source for its own tier.
  Omit unknown quality metadata. Keep every original entry, including duplicates,
  in the cloud Sources menu and preserve its explicit Reload action.
- Use a separate dynamic quality badge, following Mobile Enhanced's quality pill.
  Display 4K, 1080p, 720p and other standard tiers. Classify cropped frames using
  width and height together: 1280x688 is 720p, never a new 688p tier.
- Resolve a selected debrid/server source only when needed after selection.
  Reuse the controlled reload, cancellation, and load-failure rollback path.
  Preserve position, pause, speed, volume/mute, and subtitle delay; restore matching
  track languages where available and otherwise retain the new source's defaults.
  Language, track-role and release-duration differences do not block selection.
  The user can choose a specific release through the original cloud picker.
- No automatic quality changes. Actual media loading/buffering still depends on
  the selected provider; the selector itself has no network preflight.

The old headless probing, rewritten HLS masters, and temporary manifest leases
have been removed from this feature. Resume/external/shared source URLs remain
ordinary source URLs.

Regression coverage includes cached grouping/deduplication, standard resolution
labels, source-cache ownership, synchronous reopening, playback-state restoration,
and cancellation/failure recovery. Native fixtures use synthetic media under
isolated profile/authentication storage; never use an existing watched title as
a fixture. Generate native media with `scripts/create-quality-test-fixtures.sh`.

Use `QuickQualitySourcesTest`, `PlayerRuntimeQualityTest`,
`QualitySwitchTransactionTest`, and `NativeQualityRestoreLiveTest` for focused
verification, with `scripts/desktop-ui-tests.init.gradle`. The native renderer
check is enabled by `NUVIO_QUALITY_NATIVE_TEST=1`. Windows/Linux runtime behavior
still requires separate validation.

Validation: 83 focused and existing player regressions passed with no skips on
macOS, including repeated cached menu opening, preserving the full manual list,
rejecting another title's cache, cropped-frame tier labels, and native best-effort
restoration with an unavailable previous language and a different duration.
The production HTML controls were visually checked with synthetic 720p and 4K
state: Quality shows three deduplicated tiers while the cloud picker retains all
five fixture streams and its Reload/Close controls. No real library title was
played by these checks.

## Buffered handover (accepted 2026-10-31)

macOS prepares replacement playback while the current source keeps playing. The
selected tier is seeded in the hidden controls document before loading, preventing
a stale-quality frame during promotion. The user verified successful switching
and the badge transition. See [the handover contract](buffered-quality-handover.md)
for behavior, test coverage, and platform boundaries. Windows/Linux keep the
controlled reload path; their native runtime verification remains outstanding.

### Torrentio-only configuration

Direct addon URLs qualify independently of native debrid credentials. Torrentio
labels such as `[TB+] Torrentio 4k DV | HDR` and `1080p` are grouped using the
same provider-independent parser. No addon configuration is changed. The active
source also contributes its decoded tier when its metadata is unlabeled (for
example WEB-DL at 1024×512 contributes 480p). Unknown unplayed sources remain
in Sources; WEB-DL alone does not prove a resolution. Background preparation
lets the candidate fill its cache before seeking again to follow live playback.

Real Torrentio QA: duplicate tiers now prefer addon-marked cached/ready releases
over `[TB download]` entries. A download-only alternative is disabled with an
explanation and remains selectable in Sources. Current playback is proof of
availability even if its original addon label still says download. This is based
on returned metadata, without inspecting/changing native debrid configuration.
