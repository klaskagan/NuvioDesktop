# Football lineups (desktop)

Nuvio uses PitchAPI in both the native player and the separate Go3 Electron player.
Register for a free key at https://pitchapi.dev/ and enter it in Settings →
Playback → Player → Football Lineups. Validation makes a date-list request.
The key is stored in a dedicated local Java preferences slot, outside synced
player preferences. Old API-Football credentials are never sent to PitchAPI.
Removing the key clears local configuration. Reopen Go3 playback after changing it.

The Lineups toggle keeps the current video mounted. Native playback resizes its
existing host; Go3 applies reversible CSS to its existing video element and
restores its original controls setting on close. Neither mode reloads the source.
Original starters and bench appear with formations, coaches, shirt numbers and
normalized pitch coordinates. Missing coordinates fall back to a list.
Predicted lineups are excluded: PitchAPI must mark the side confirmed.

## Data and spoiler contract

Desktop Go3 carries the exact programme title from Watch into the player.
Live Extra channels can replace the browser title with “Extra tiesiogiai”
after playback starts. Resolve the fixture from the selected programme title
when available, and use the page title for bare playback URLs.

GET /v1/date/{date}?status=all includes live and scheduled fixtures. The
default PitchAPI response is played-only, which hid confirmed live Premier
League team sheets. The Go3 resolver checks both Premier League team labels
and the league, including Go3's “Premier Leauge” spelling, Brighton's “and”
versus “&”, and Bournemouth's optional “AFC” prefix.
GET /v1/matches/{id}/lineups fetches that fixture's original team sheet.
Match IDs are opaque strings. Always send X-API-KEY and NuvioDesktop/1.0
User-Agent; the direct test initially received HTTP 403 without a User-Agent.

Only FootballMatch and FootballTeam/FootballPlayer may reach native presentation.
Go3 uses equivalent allowlisted decoders. Drop scores, status, ratings, match
events and participation flags immediately. Bench means the full original list,
without substitution annotations. Never embed provider pages, return raw errors,
log keys/response bodies or build result links. Player coordinates are clamped
by validation to finite values between zero and one.

Successful sanitized lineups are cached only for the playback session. In-flight
panel requests are cancelled or invalidated on dismissal and match changes.
No match-event polling occurs.

## Verification

FootballLineupDecoderTest checks completed fixtures, score/event exclusion,
original bench, predictions and invalid coordinates. FootballLineupsUiTest
checks visible starter/bench content and close/change-match controls.
scripts/go3-live-probe/lineups.test.cjs checks the same provider boundary and
request paths, authentication headers, User-Agent, string IDs and toggle restore.

PitchApiLiveTest is opt-in via PITCHAPI_TEST_KEY; it verifies the confirmed
Manchester United vs Roma lineup on 2026-11-03 without outputting results or keys.
The live API test verified two teams of eleven starters, bench players and
coordinates. Keep authentication/profile storage isolated when running UI tests.
Native decoded video continuity and Go3's official player layout still require
visual validation; fixture tests alone do not prove playback.

## Go3 ownership and Champions League

Go3 uses the official player in the Electron helper, rather than
PlatformPlayerSurface. Its Lineups toggle is installed by
scripts/go3-live-probe/lineups.cjs. Test both control owners when adding shared
playback actions. The helper resource copy must include lineups.cjs.

The key goes over the private parent/child pipe on Watch and remains in the helper
main process. It is never injected into Go3 page JavaScript or evidence logs.

Go3 Champions League uses the official category
https://go3.lt/categories/uefachampionsleague,category-6886205, discovered through
Sports → Football. It shares Premier League's Live/Upcoming/Replay selection and
exact programme destination handling. Regression: Go3IntegrationTest's
championsLeagueSelectionRetainsExactReplayDestination and events.test.cjs.

## Automatic selection and lineup layout

Opening Lineups resolves both team names from the playback/programme title.
Use an explicit ISO match date in the title when present; otherwise search today
and the previous fourteen dates. Only one fixture may be selected automatically.
Ambiguous or missing fixtures show an unavailable state with Retry, never a
manual team/date form or an arbitrary nearest match. Do not use unrelated page
time elements as the programme's original date.

The Go3 panel uses a grey pitch with a fixed 105:68 landscape aspect ratio, readable player labels and a
larger 38vw video (440–820px), with coach/bench columns below the video and vertical
scrolling when needed. Keep absolute player nodes out of formation-row flex rules:
a minimum-height rule on .formation > div caused shirt numbers and names to split
apart and overlap other players. Position each compact player marker as a unit.
Regression: lineups.test.cjs automatic selection plus a browser-rendered 22-player
fixture at desktop dimensions. Capture-protected Go3 video can be absent from
screenshots even while playing; do not treat that as proof of missing playback.

The centre circle uses a square aspect ratio and is centred independently of
pitch height. Do not size its width and height using unrelated percentages or
force the pitch to viewport height; both caused the vertically stretched field
and oval centre marking in the Go3 screenshot. Check computed pitch and circle
bounds at wide and tall window sizes when changing video/rail dimensions.


## Desktop native player eligibility

The native controls WebView receives Lineups availability from the playback
request's explicit `sport` or `sports` content type. The button starts hidden
until that state arrives. The native command handler checks the same eligibility,
including after a source or content type changes. Movie, episode and trailer
requests leave the action hidden. `NativePlayerControlsJsonTest` checks the
sports-only value crossing the WebView bridge.


## Go3 fullscreen ownership

Electron window fullscreen keeps the document-level Lineups action available.
For HTML element fullscreen, move the same action and open panel into the
fullscreen player container. A body-mounted button disappears behind that
container regardless of z-index. Video-only fullscreen also makes other
elements inert, including a popover. In that case the helper uses fullscreen
on the existing Electron window so the playing video and Lineups can coexist.
Test windowed playback, native window fullscreen, fullscreen containers, and
video-only fullscreen with open, restore and keyboard focus on the same video.
