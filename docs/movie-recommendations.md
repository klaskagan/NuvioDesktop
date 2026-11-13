# Movie recommendations

Settings → Playback → Player interface → **Movie Recommendations** is on by default for each profile.
The native desktop player shows up to ten
unwatched titles from its existing More Like This source. All available titles,
up to ten, fit in a five-column, two-row grid without scrolling. A fixed 10px
outer gutter keeps both edge posters inside the rounded card; poster size adapts
to smaller windows. Keep the gutter outside the items container: padding inside
a scrolling strip did not protect the right edge when additional posters overflowed.
The browser fixture checks all ten item bounds and the tenth item's click action. Selecting one saves
progress, releases playback, and opens Details without automatically playing it.

This port follows the checked-out NuvioMobile-Enhanced implementation: closing the
card suppresses its current ending stage, and it returns at 30 seconds remaining
and when playback ends. This is not a timed one-minute snooze. Seeking before the
selected timing threshold resets dismissal. The card auto-hides after ten seconds with
controls hidden; showing controls reveals it again, including after dismissal.
At playback end it stays visible unless dismissed (or another overlay owns focus).

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

The card starts at the first valid credits timestamp. The last-30-seconds and
playback-ended stages retain their dismissal behavior, but never show before
known end credits. With no usable data, API mode falls back to the original four
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
HTML buttons, focus, stage transitions, timers, PiP suppression, and resizing.

`MovieRecommendationsLiveTest` additionally exercises the native macOS player
when `NUVIO_PLAYER_LIVE_TEST=1`. Supply a local short movie at
`composeApp/build/qa/movie-recommendations.mp4` (the test JVM runs in composeApp).
It requires a GUI session and screen/input automation permission. It saves a
native screenshot under the same QA directory and verifies the recommendation
callback runs after player release. Never run native fixtures against a real profile.
