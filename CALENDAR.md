# Trakt Calendar screen

Desktop navigation exposes Calendar independently of the Home catalog. Week is
initially selected; Day omits empty dates, and Month shows a selected date's media
below its grid. All dates use the device timezone and regional week boundaries.
Calendar's orientation icon defaults to landscape stills for profiles without a saved
choice (redesign, Nov 2026) and is stored as `calendarLandscapeModeEnabled` in the
existing profile-scoped, synced poster style payload. The payload encodes defaults, so
profiles that saved any poster style before keep their stored (portrait) value until
toggled. `catalogLandscapeModeEnabled` continues to control Home.

## Reusable contracts

- `TraktCalendarRepository.createSession()` provides an independent observable
  `CalendarRepository.state`, `load(days, force, startDate)` and `invalidate()`.
  Dispose it with `releaseSession()`. Home retains its original observable state.
- `CalendarTransportCache` shares authenticated UTC-aligned seven-day batches
  across overlapping consumer ranges. Entries have the existing ten-minute
  freshness interval and an account identity. Invalidation rejects in-flight
  responses, including same-account reconnects. Retention is bounded to 120
  endpoint batches; screen date windows retain at most twelve weeks.
- Raw `CalendarEpisode` and `CalendarMovie` objects preserve identities, release
  precision, timestamps, and episode metadata. `buildCalendarCards()` handles
  local-date grouping and chronological presentation, including date-only movies.
- `CalendarPresentation` contains regional week/month range calculations,
  month-clamped navigation and filter/day-group mapping, independent of Compose.

## API and artwork

Requests reuse Trakt's authenticated client/header refresh and error handling:
`GET /calendars/my/shows/{start_date}/{days}?extended=full,images` and
`GET /calendars/my/movies/{start_date}/{days}?extended=full,images`.
No watched, streaming-service, DVD, or streaming-release calendar filters are added.
Raw UTC coverage is padded before filtering locally; overlapping requests reuse
shared batches. There are no individual card metadata calls.

Artwork comes from existing Trakt image normalization (show/movie poster and
landscape URLs, with episode screenshots as a landscape fallback), through the
shared Coil cache, viewport preparation and readiness gates. Calendar cards do
not request clean backdrops or overlay duplicate logos. Missing artwork uses the
standard card placeholder.

Premiere/finale badges are limited to recognized Trakt `episode_type` values.
Missing values do not produce inferred badges; movie entries never infer these
badges. Date-only movie releases have no artificial local airing time.

## Verification

Run with a full JDK and isolated preferences/profile storage:

```sh
JAVA_HOME=/usr/local/opt/openjdk@17 ./gradlew \
  -I scripts/desktop-ui-tests.init.gradle :composeApp:desktopTest \
  --tests 'com.nuvio.app.features.calendar.CalendarPresentationTest' \
  --tests 'com.nuvio.app.features.trakt.TraktCalendar*Test' \
  --tests 'com.nuvio.app.CalendarScreenUiTest' \
  --tests 'com.nuvio.app.CalendarTransitionsUiTest' \
  --tests 'com.nuvio.app.TraktCalendarUiTest' \
  --tests 'com.nuvio.app.MainAppTabNavigationTest'
```

Rendered fixture screenshots are written to
`composeApp/build/reports/calendar-screen/`. These tests use local generated
artwork and in-memory authentication/preferences, never live account mutations.

For the standalone review bundle on this Mac, use the installed Temurin JDK
(Compose packaging rejects the Homebrew JDK vendor):

```sh
JAVA_HOME=/Users/klaskagan/Library/Java/JavaVirtualMachines/temurin-17.0.6/Contents/Home \
./gradlew \
  -Dorg.gradle.java.home=/Users/klaskagan/Library/Java/JavaVirtualMachines/temurin-17.0.6/Contents/Home \
  :composeApp:signLocalMacosApp
```

The resulting review application is
`composeApp/build/compose/binaries/main/app/Nuvio.app`; this does not replace an
installed application. Mobile navigation does not expose Calendar. Android/iOS
platform date-label contracts are supplied, but this feature is validated on desktop.

## Desktop redesign (Nov 2026)

Calendar is on the redesign shell: it joins `ShellBackdropTabs` and steers the ambient
backdrop (only while active) with the next release in the visible range, or the selected
Month day's first release. Content uses Home's margins (`desktopPageHorizontalPaddingForWidth`)
and `RedesignTypography`.

- Header: `CALENDAR · N RELEASES` kicker (count for the selected day/week/month, shown only
  once the range has loaded), the month title that opens the date picker, and a subtitle
  (Week range or Day label). The title button has a fixed width measured for the widest
  month; the subtitle yields space and ellipsizes, so no view or month change moves a control.
- Controls are `RedesignPillGroup` (Day/Week/Month, All/Shows/Movies), `RedesignPillButton`
  (Today, History's month) and `RedesignIconButton` (arrows, orientation) from
  `core/ui/RedesignControls.kt`. Underlined text tabs under the merged top bar read as a second
  copy of the bar. Hidden arrow slots expose no semantics.
- Week: seven frosted tiles of equal height (`CalendarWeekColumns` draws the tiles behind the
  measured columns, avoiding intrinsic measurement of artwork cards); today's tile is brighter
  with a `TODAY · MON` label. Empty days say "No releases" only after loading.
- Month: the grid is one frosted tile; on panes at least `HomeBoardSideColumnMinWidth` wide the
  selected day's releases move into a 352 dp side column (`calendar-selected-day`), otherwise
  they stay in the shelf below. Month pages fade with their drag offset like Home's hero.
- View changes crossfade; week and day items use `animateItem()`.
- The date picker (`CalendarAgendaDatePicker`, shared with History) is a frosted 372 dp panel,
  not a Material `AlertDialog`: month title with muted year, round arrows, a fixed 42-slot grid
  that slides in the direction of travel, the chosen date as a light pill, today as a ring,
  muted out-of-month days and a Today shortcut (`<prefix>-picker-today`).

## UI behavior contract

- The single-row toolbar keeps the month/year picker, Today, view/filter controls,
  and orientation icon in stable positions across view changes. Month arrows
  occupy reserved slots even when unavailable in Day/Week. The pinned icon shows
  the current orientation and toggles Calendar's own profile-scoped preference.
- Day stacks populated date sections, each with a day-number/weekday/month
  heading and one horizontally scrolling shelf. Week stacks seven aligned day
  columns, with cards stacked vertically per day and each week below the tallest
  column. Narrow Week grids scroll horizontally as a whole.
- Day/Week load bounded adjacent ranges through scrolling, with no load buttons.
  Keep earlier loaded Day dates available. Preserve the visible key and pixel
  offset on prepend. Empty Day browsing still advances the date used by the
  toolbar and view switching. Reveal a selected date only after its data and
  actual rendered list are ready, using deferred list positioning.
- Month reserves six rows via `calendarMonthGridRange` (42 slots), independently
  of the minimal fetch range. Hide dates and borders outside the selected month.
  Cells, grid width, and the full-width selected-day shelf retain their dimensions
  across month changes; available viewport resizing can recalculate them. Keep
  the selected-day column (wide) or shelf (narrow) visible beside or below the grid and its horizontal scroll separate
  from month swipes. Bound thumbnail width by both cell width and available height.
- The month grid follows the drag. Finger-up past 8% of its width, capped at
  80 dp, commits one neighboring month; short/cancelled gestures restore the
  original. Native contact phases prevent momentum repeats. Current and preview
  pages use the same six-row geometry throughout the gesture. Do not commit a
  new date/range while contact is held.
- Reserve a permanent 4 dp loading strip in every view. Preserve successful cards
  and artwork during refresh; empty messages and zero counts wait for the requested
  range to complete. The empty/loading selected-day area retains full width and
  height. No transient loading control may change the grid or shelf bounds.
- Keep title and episode/movie labels visible independently of optional Home
  labels. Put release dates in section headings rather than repeating them under
  cards; timed releases keep their time/range badges. All release counts use
  plural resources and the same muted `labelSmall` style as card details.
- Week headings use This week / Next week, otherwise compact localized ranges.
  Month's first date includes its abbreviated month (`1 Oct`). Date-picker weekday
  labels and date cells share centered columns. Only the actual current date's
  Day heading is highlighted; if it has no matching releases, skip it without a
  synthetic Today banner or highlighting the next date instead.

These are Calendar choices. Shared prevention rules and regression expectations
live in [.agents/ui-consistency.md](.agents/ui-consistency.md).

## Transition regression references

`CalendarTransitionsUiTest` compares loading/settled bounds in all views, retained
artwork pixels, four/five/six-week months, leap February, resize, and intermediate
cancel/commit frames. Captures and the assembled fixture recording live under
`composeApp/build/reports/calendar-transitions/`.

`CalendarScreenUiTest` covers stable toolbar positions, orientation independence,
Day scroll/prepend anchors, filter reselection when today is empty, empty-range
date progression, view switching, and actual detail destinations. Synthetic events
do not establish physical trackpad feel; report hardware checks separately.

## History uses the Day agenda

History calls the shared Calendar toolbar, date picker, date heading and card.
The available width determines how many cards fit in each row. Rows wrap within
the day and share one vertical scroll with the date headings. It saves its own profile-scoped portrait/landscape preference and shows watch
counts and local watch times. Keep the episode title and season/episode code in
one caption below the artwork, with the series title above it. Resolve artwork
from the watched episode's screenshot or the same season/episode metadata
match used by Continue Watching. History does not use series artwork for
episode cards. `HistoryNavigationTest` exercises the real navigation and checks
the caption, independent orientation persistence, scroll-edge pagination and a busy day wrapping within the viewport
alongside the Calendar UI tests.

History and Calendar reuse `agendaScrollLoading` for wheel and nested-scroll edge
requests. It reads current callbacks and loading state, throttles edge requests,
and lets History stop at the last page or an error. Retry remains explicit after
an error. History initializes its separate orientation to portrait for older
profiles whose payload predates `historyLandscapeModeEnabled`.

## Narrow agenda controls

The shared agenda toolbar measures its localized labels and switches to two
rows when the wide controls no longer fit the content pane. The first row keeps
month selection, Today and the orientation toggle visible. The second row groups
All/Shows/Movies and uses the existing Nuvio dropdown chip for Calendar's
Day/Week/Month choice. The compact History toolbar omits its single-choice Day control. Month traversal
on narrow screens uses the shared date picker. The loading strip keeps its
reserved height in either layout.

`AgendaToolbarUiTest` checks both compact toolbars at 320 px and 440 px window
widths, including full filter labels, picker and Today actions, orientation,
Calendar dropdown selection, and stable geometry while loading. The wide-screen
Calendar and History tests cover the existing desktop entry points.

### History filtering and event removal

History has one day-grouped timeline and no Day selector. Calendar and History
share bounded-width segmented media filters on desktop and the two-row compact
toolbar. Calendar filter changes reveal a matching date in the loaded range;
they must not trigger unbounded searches for distant releases.

History filters query the reader by media kind, with separate timeline/request
ownership per filter. Readers without server filtering can advance empty filtered
pages until a match or the end, without requiring a scroll gesture. Never show
empty-history copy merely because the first mixed page contains no matching type.

History card actions reuse NuvioPosterZoomActionOverlay. Removal is an optional
tracker capability and deletes a single watch-event ID, preserving rewatches.
Keep the card until the request succeeds, retain it on failure, and discard
responses from an obsolete account. HistoryTimelineTest covers filtered paging
and removal failure/rewatch preservation with a second provider.
