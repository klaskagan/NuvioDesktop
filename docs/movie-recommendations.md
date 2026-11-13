# Movie recommendations

Settings → Playback → Player interface → **Movie Recommendations** is on by default for each profile.
The native desktop player slides a **More Like This** strip with a smoked glass background over
the bottom of the full-size video and raises the existing playback controls
above it. The strip holds up to 20 titles in one row of portrait posters
in provider order. With Trakt connected, playback reads the available pages
of related movies in Trakt's order before selecting up to 20 unwatched titles.
The lookup keeps earlier pages if a later page fails and stops at the reported
last page or when a page is empty or repeats. If fewer than 20 qualify,
playback appends pages from TMDB's recommendations for that specific movie.
The same watched filter applies to both sources, including TMDB IDs from
Trakt's watched history. Disconnected profiles use the
existing metadata recommendations. The player excludes the current movie and
deduplicates using available provider IDs. A short list is shown as returned
after filtering.
Poster titles are available to assistive technology and on hover.
Scroll with the trackpad, mouse wheel, chevrons, or arrow keys.
Selecting a poster saves progress, releases playback, and opens Details.

The strip stays open while browsing. Clicking the video or empty shelf space,
pressing Escape, or using Close slides it out and restores the controls.
Dismissal leaves playback running and lasts through the current ending. Seeking
back before the trigger or starting a new playback session allows the strip to
appear again. Reduced motion disables the slide.

Empty/watched-only results, disabled settings, non-movie playback, and missing
navigation do not show a card. Controls are rendered by the existing native HTML
overlay, not a second Compose control surface. Session-qualified recommendation
actions reject stale events after a playback change.

## Timing source

Settings → Playback → Player interface → **Movie Recommendation Timing** offers **IntroDB API**
(default) or **Final 4 minutes**. API mode reuses the existing IntroDB movie
response models and IMDb/TMDB/anime ID resolver. It reads end credits from
`https://api.introdb.app/segments?imdb_id=…&is_movie=true`, then tries
`https://api.theintrodb.org/v4/media?imdb_id=…&duration_ms=…` if the primary fails,
times out, or has no valid credits for the playing duration. No read API key is
required. The backup adapter handles millisecond arrays and open-ended credits.

The strip appears five seconds after the first valid credits timestamp. The last-30-seconds and playback-ended milestones preserve dismissal for
the current ending. With no usable data, API mode falls back to the original four
minutes. Explicit four-minute mode does not request credit timestamps. Lookup is
independent of Skip Intro and auto-skip settings, bounded by timeouts, cached per
movie/duration, and cancelled when playback or timing mode changes. A pending
lookup does not flash the four-minute card before authoritative timing arrives.
The timing choice is profile-scoped and included in settings sync/transfer.

The shared primary decoder accepts a request function, allowing recommendations
(and the backup) to reuse `createApiHttpClient` with cancellable five-second
requests and bounded response bodies. Do not substitute the desktop addon HTTP
helper here: its blocking reads can outlive a coroutine timeout and delay backup
lookup. `MovieCreditsHttpTest` checks prompt cancellation against a local server.
The shared decoder uses a nullable request callback and makes the default HTTP
call directly inside its suspend body. Default suspend lambdas and references
calling the platform `expect` function triggered Kotlin's `AddContinuationLowering`
assertion during desktop compilation. Keep the default callback null; compiling
the desktop target is the regression check.

Provider formats: [IntroDB API](https://introdb.app/docs/api),
[TheIntroDB](https://theintrodb.github.io/theintrodb-npm/getting-started.html).

## Verification

Run focused Kotlin tests with the isolated desktop test init script:

```sh
./gradlew -I scripts/desktop-ui-tests.init.gradle :composeApp:desktopTest \
  --tests 'com.nuvio.app.features.player.MovieRecommendationRulesTest' \
  --tests 'com.nuvio.app.features.player.MovieRecommendationSettingsTest' \
  --tests 'com.nuvio.app.features.player.MovieRecommendationTimingUiTest' \
  --tests 'com.nuvio.app.features.player.MovieCreditsHttpTest' \
  --tests 'com.nuvio.app.features.player.skip.MovieEndCreditsLookupTest' \
  --tests 'com.nuvio.app.features.player.desktop.NativePlayerControlsJsonTest'
```

The browser regression fixture is `scripts/test-movie-recommendation-controls.cjs`;
run it with Node and Playwright available through `NODE_PATH`. It checks actual
HTML buttons, all 20 posters, scrolling, focus, timeline placement, dismissal,
PiP suppression, and resizing.

`MovieRecommendationsLiveTest` additionally exercises the native macOS player
when `NUVIO_PLAYER_LIVE_TEST=1`. Supply a local short movie at
`composeApp/build/qa/movie-recommendations.mp4` (the test JVM runs in composeApp).
It requires a GUI session and screen/input automation permission. It saves a
native screenshot under the same QA directory and verifies the recommendation
callback runs after player release. Never run native fixtures against a real profile.
