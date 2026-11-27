# UI consistency and regression prevention

Read for changes to layout, loading, scrolling, gestures, or date presentation.
These are shared defaults derived from confirmed failures. Explicit user choices
take precedence; feature-specific contracts remain in feature documentation.

## Smooth transitions (desktop redesign's main rule)

Every visible change animates; nothing swaps, pops, or jumps. The user has repeatedly rejected
redesign steps that changed the look but left hard cuts (results flashing skeletons on every
keystroke, the hero vanishing so the search bar jumped up, a header turning dark in one frame,
Person Details opening with no transition). Apply these to every redesigned screen:
- **Home's hero carousel is the motion reference.** Each layer's opacity follows its offset as
  it drifts (`alpha = 1 - |offset|`). Backgrounds drift little (`HERO_BACKGROUND_PARALLAX`, 5.5% of
  the width) and foreground content drifts more (`HERO_CONTENT_PARALLAX`, 18%), on the pager's
  no-bounce, medium-low-stiffness spring. Use those values instead of inventing new ones.
- **Navigation between screens** uses `Modifier.navTransitionLayer(layer, side)`
  (`core/ui/NavTransitionLayer.kt`). The nav entry switches instantly (`EnterTransition.None`), and
  each screen animates its own Background and Content layers, so the app sidebar both entries draw
  never moves. `NavSide.Underneath` screens (Search) leave to the left; `NavSide.Above` screens
  (Person) arrive from the right, mirrored on Back. Screens read the scope from `LocalNavLayerScope`
  (provided per entry in `MainAppContent.kt`); without it there's no motion, e.g. in tests.
  The style is a user setting (Settings → Layout → Page transitions, synced as
  `page_transition`): `HeroParallax` (default, Home hero values) or `FullSlide`. Screens read it
  from `LocalNavTransitionStyle`, provided with the nav scope in `MainAppContent.kt`. New screens
  get both styles by applying `navTransitionLayer` to their background and content.
  A whole page sliding a third of the screen plus a shared-element avatar flying over it was rejected
  as too busy. Don't fly individual elements across pages; let them move with their layer.
- **Placeholders are the real layout, not an approximation:** build skeletons and late-section
  placeholders from the same components and paddings as the loaded UI, with blank data. Person
  uses `PersonRailPlaceholder` (the real `DetailPosterRailSection` with blank, inert cards and the
  real title) and `PersonHeaderLayout` (one header for loading and loaded, with the bio area always
  reserving four lines plus "Show more"). A hand-made skeleton with generic poster sizes, and a
  Known For title that popped in late, both moved the page and read as motion "starting from the
  top right". Conditional gaps must not change when data arrives either: Movies' top gap used to
  appear only after Known For loaded. Regression: `PersonCreditsNavigationTest` asserts the Movies
  title keeps its exact position when Known For arrives late.
- **State changes crossfade:** loading → content → error (`AnimatedContent` keyed on the state
  class), and a late-arriving section fades in (Person Known For).
- **Lists move, they don't jump:** give list items `Modifier.animateItem()` (fade plus
  placement), so inserted or removed sections slide instead of shifting the page.
- **Refetches never flash placeholders:** keep the previous results on screen, dimmed, until new
  ones arrive (Search `heldSearch`). Skeletons are for the first load only.
  Discover does the same on a type/catalog/genre change (`discoverContent(heldItems)`): the
  grid collapsing to two skeleton rows snapped the page to the top. A placeholder also resolves
  to the final choice only: the service page's hero waits for its charts instead of showing
  Popular's first title and swapping it (`tagHeroItems`, `StreamingRowLayoutTest`).
- **Controls ease:** hover and selection colours tween (~140–220 ms), and tab indicators slide
  (`SearchScopeTabs`). Avoid default grey indication boxes on redesigned surfaces.
- **Hover that reshapes its row must not move away from the pointer:** if opening on hover
  resizes neighbours, the opened control must still cover the pointer, and hover-out closes only
  after a grace longer than the resize (Rate strip flickered open/shut when a narrow ♥ strip slid
  left; `BoardRateControl`, `hoverOpenedStripSurvivesABriefExit`).
- **Chrome reacts to real state:** a sticky bar fades its dim and blur in only once actually
  pinned.
- **The tabs shell owns the one ambient backdrop** (`MainTabsDestination.kt`, `ShellBackdrop`). A
  redesigned tab joins `ShellBackdropTabs`, reads `LocalBackdropFocusController`, and sets `base`
  inside `ScreenActivityEffect` only while active: visited tabs stay composed and would otherwise
  steer it from behind. Tabs outside the set keep the solid page; the shell fades the backdrop and
  the sidebar's frost out for them. Don't mount a second `ShellBackdrop` in a tab (Search keeps one
  only for when it is hosted alone, as in tests). `LocalShellBackdropMode` (Settings → Layout →
  Background, synced as `shell_backdrop`) is Ambient, Mesh (no artwork) or Solid (null controller,
  so the existing `controller != null` fallbacks restore the opaque look).
- **Don't make every Home row a blur source.** Home's pinned tabs first blurred the rows beneath
  them the way Search's bar does (`hazeSource` on each list item). On Home's long list of image
  rows that nearly doubled render time: `SeasonalCollectionNavigationTest.homeExploreRowsGridAndBackKeepSeasonAndShuffle`
  went from 43 s to 66–81 s and past its 1-minute limit, and back to 34 s without it. Home's bar uses
  `BackdropSurface.stickyHeaderOpaque` instead. Treat that test's runtime as the regression check
  before adding per-row effects to Home.
- **Desktop Home opens on a board, not a full-width hero** (`HomeBoard.kt`): the hero carousel is a
  tile (its height comes from `LocalHomeHeroHeightCap`, read by the skeleton too) beside a Resume
  tile and an Up next list, with the tabs pinned above. The board takes the first
  `HOME_BOARD_RESUME_ITEMS` resume items and the Continue Watching row shows the rest, so one title
  never appears in both. When the board's set changes, a title moves to the head of that row; a
  lazy row anchors on its first visible item, so `HomeScreen` puts a row still at its start back
  at its start (seen as a sliver of a card left of the row's first card). Plain poster rows open
  with `HomeRowLeadCard`; rows with a chosen style, rankings and calendar rows are left alone. The
  lead card is present from the first frame and loads its landscape art in place: showing it only
  once a banner was known made the row jump when hero metadata arrived after the posters.
- **Home's loading and empty states are the board and rows with blank data** (Home design "For you ·
  loading" / "empty, error and offline"). While the featured tile loads, the board keeps its side
  column as two placeholders (`HomeBoard(sideLoading)`), so it doesn't reshape when Resume arrives. A
  pending catalog row is the same lazy item as its loaded row and crossfades into it
  (`HomeSkeletonRow`'s Editorial branch uses the real shelf header and the lead card's size); swapping
  two composables in one item popped. Empty, failed and offline Home use `HomeStateCard` (the shared
  tile, centred, 440 dp, design copy; desktop never shows a raw manifest error; offline has no button
  and retries when the connection returns). Regression: `HomeStatesTest`, `HomeStatesCaptureTest`.
- **Home's hero tile expands; it does not toggle the window.** On the board the hero's corner
  control used to be the window-fullscreen button, which read as "expand this card". It now expands
  the tile over the whole board and plays the current title's trailer with sound
  (`HomeHeroSection(expanded, onToggleExpanded)`); the carousel holds while expanded. Window
  fullscreen stays on its shortcut and the system control. Every redesigned caller passes both:
  the service and tag pages' hero (`streamingProviderPage`) left them out and fell back to the old
  window toggle; it now grows the card through `LocalHomeHeroHeightCap`, and Esc collapses it
  (`TagHeroExpandUiTest`). It also uses Home's heights (`homeBoardHeroHeight`): starting at its old
  640 dp, expanding added only a few pixels and read as a glitch. A row's lead card plays its trailer in
  place on hover under the poster hover-preview settings. Ordinary posters have no popover: hover
  lifts them with a white ring and shows an action strip at once (in the top corner on wide
  cards, so the title stays readable); `HomePosterHoverPreview` hands the card a
  `PosterHoverOverlay` to draw inside its artwork. Only something that moves the row waits for
  the hover delay: the Inline expand style (`PosterInlineExpand`), which grids turn off because a
  cell can't widen. ⓘ, Space or press-and-hold opens Quick look (`PosterQuickLook`, one host in
  the shell; `LocalPosterPressAndHold`), and right-click keeps the poster menu. Quick look's
  trailer uses the Details board's controls (mute and Fullscreen on a dark plate) and expands in
  place, the same player continuing; Play trailer opens Quick look already expanded, and Esc or
  Space step back one layer (expanded → Quick look → closed). Never the Details bottom sheet. A list of posters passes its items
  (`quickLookRow` / `LocalPosterQuickLookRow`) so ← → can walk it, and any poster drawn outside
  `HomePosterCard` (ranking rows) wraps itself in `HomePosterHoverPreview`. Their backdrops use
  `HoverBackdrop`, which picks one image (the row's backdrop, else the details') and fades it in
  once, never swapping it; their text prefers the row item's values so it is never rewritten.
  Quick look never shifts while it loads: every late part (ratings, overview, the side column's
  Next episode and Watch on, the Play label) has a fixed slot from the first frame with a
  skeleton, the Play pill has a minimum width, and empty slots keep their size
  (`quickLookContentDoesNotShiftWhileLoading`). A new poster card draws that overlay instead of building its own hover UI
  (`PosterHoverStripTest`).
- **Window key claims never take typing:** `MediaKeysHandler` claims (Space for Quick look, a
  trailer's keys) are skipped while a text field marked `Modifier.typingField()` has focus. Mark
  any field on a screen with hoverable posters or an in-page player (`TypingFieldMediaKeysTest`).
- **The top-bar layout has one bar** (`DesktopMergedTopBar`): Home's tabs (For you, Movies, Shows,
  My lists) lead it in place of a Home item, the other destinations follow on the right, and one
  underline slides between all of them. It is the same bar on main tabs and detail pages, so a Home
  tab is reachable from Search. The tab choice lives in `HomeTabSelection`, and Home draws its own
  tab strip only for the sidebar layout (`LocalHomeTabsHostedInNavigation`). Two earlier attempts
  were rejected: a tab strip under the floating pill (tall empty band), and tabs placed beside the
  pill (still two controls). Screens start their content at `topChromePadding`; Search's idle hero
  used to ignore it and slid under the bar.
- **Each Home tab's hero matches the tab.** Movies and Shows have their own hero pools
  (`HomeUiState.movieHeroItems` / `showHeroItems`, built in `HomeRepository`); For you and My lists
  share the mixed one. A tab's catalogs come from `resolveTabHeroSourceKeys`: the user's picks for
  that tab (Settings → Home Layout → Hero catalogs, stored locally as `heroTabSources`), else the
  For you hero catalogs of that type, else the tab's first visible catalog. Filtering the mixed
  For you pool by type is not enough: with two movie catalogs picked it leaves Shows with a movie
  hero. Load hero metadata for all pools together (`HomeHeroMetaStore.load` prunes to what it is
  given). Regression: `HomeTabsTest`.
- **Filters under the merged top bar are pills, not text tabs.** Calendar, History and Library use
  `RedesignPillGroup` / `RedesignPillButton` / `RedesignIconButton` / `RedesignMenuButton` (`core/ui/RedesignControls.kt`);
  reuse them for other redesigned filters (the streaming tag page's `TagScopePills` predates them).
  A control whose label can change (a title that also opens a picker) gets a fixed measured width
  so view or month changes never shift the controls after it; `CalendarScreenUiTest`'s
  "Control shifted" assertions are the regression check.
- **Backdrop contrast adapts to the artwork.** A fixed scrim tuned for dark banners let bright
  art (Calendar over The Simpsons) wash out the bar's idle labels and muted page kickers. Every
  `ShellBackdrop` artwork layer now adds a dim from its top-weighted luminance
  (`backdropDimFor(artworkLuminance(...))`, none for dark art) and a fixed top chrome band, so
  `RedesignColors.textMuted` keeps ≥3:1 contrast. Fix contrast in the backdrop, not by restyling
  labels. Regression: `ShellBackdropContrastTest`.
- **Calendar is a backdrop tab** (`ShellBackdropTabs`): it steers the shell backdrop from the next
  release while active and draws no opaque page of its own when a controller is present.
- **Library is a backdrop tab too** (desktop only, `LibraryDesktopScreen.kt`; mobile keeps
  `LibraryScreen`'s old layout). It uses Calendar's header shape: `RedesignPageLead` (kicker, 34sp
  title, subtitle, shared with Calendar's `RedesignKicker`) with Saved / Cloud / Servers pills and
  icon buttons in reserved slots. The layout toggle's slot holds Refresh on Cloud, and the Manage
  lists slot exists only where the source can manage lists (`libraryListManagementAvailable`), so
  no control moves between panes or states and other sources show no gap. Panes (shelves, grid,
  Cloud, Servers) crossfade below the fixed header. One stable title steers the backdrop per pane:
  the latest save (`librarySpotlight`), the grid's first title, a server's first title; Cloud uses
  the mesh. A list opened from the board's Your lists or a shelf's View all opens in place in the
  grid (transient `openedListKey`, the layout setting is unchanged), and Esc or Back returns to the
  shelves at the same offset. A multi-file Cloud item opens a 352dp side panel; Esc closes it first.
  Pickers are `RedesignMenuButton` (frosted popup that claims `overlayEscape` while open); don't use
  `NuvioDropdownChip` on redesigned screens. `LibraryDesktopUiTest` covers header positions across
  panes and loading, in-place lists and Esc order, the Sort menu, the Cloud panel and server Retry.
  Cloud filters by what an item holds (All / Movies / Shows / Other, read from release names in
  `CloudLibraryPresentation.kt`, `CloudLibraryPresentationTest`), not by the provider's transport
  type; rows show files, size, provider and when it was added (`addedAtEpochMs` from TorBox
  `created_at` and Premiumize file dates), and the header says when the list was refreshed.
  A list opened from the shelves is a grid: the layout button then offers the shelves and goes back
  to them, leaving the layout setting alone (`libraryLayoutToggle`, shared with the test harness so
  the test runs the real decision). It used to switch the setting to grid, which only hid Back.
- **Downloads is redesigned on desktop** (`DownloadsDesktopScreen.kt`, mobile keeps `DownloadsScreen`):
  its route sits in `DetailNavigationFrame` with its own backdrop like a service page, Library's
  header shape with Pause all and the folder button, active transfers as one frosted list, finished
  titles as landscape cards, and a show's episodes in place (Esc returns). Actions are a frosted ⋯
  menu (`RedesignOverflowButton`), not the mobile bottom sheet. Empty and error tiles everywhere use
  `RedesignMessageTile` (core/ui), sized to the Home design's state card: 48 dp round badge, 18 dp
  title, 14 dp text (changed 26 Nov 2026 for Home, Library and Downloads together). `DownloadsDesktopUiTest` covers the lists, the show view and the menu.
  The kicker carries counts only; repeating "Downloads" above the "Downloads" title read as a
  duplicate. The folder button is a menu (Open / Change folder… / Use default), mirrored in
  Settings → Playback → Downloads (`DownloadsFolder`, desktop only). A new folder applies to new
  downloads; each `DownloadItem` keeps `downloadDirectory`, so earlier and paused downloads stay
  where they started. `DownloadsFolderTest`.
- **Desktop context actions open on right-click and on mouse press-and-hold.** Compose's
  `combinedClickable(onLongClick)` ignores a mouse hold on desktop, so a "press and hold" promise
  needs `mousePressAndHoldAt` (core/ui `SecondaryClick`, after the clickable so the hold swallows the
  click); `redesignContextMenuTrigger` pairs it with right-click. Stream cards, Downloads rows and
  cards, and Cloud rows / panel files use it; Cloud rows show `RedesignContextMenu` (the ⋯ menu's
  popup, at the pointer): Play, Download file, Copy link, or Show files for a pack. The first fix
  only swapped `onLongClick` and was never pressed in a test; `StreamCardActionsUiTest` and
  `LibraryDesktopUiTest.cloudRowsOpenTheirActionsOnRightClickAndHold` hold with the mouse. In tests,
  press at the node's centre: its top-left corner is outside a rounded clip and never hits.
- **Frosted sidebars come in pairs.** A page with its own backdrop in a separate nav entry (Person)
  passes `frostedSidebar` to `DetailNavigationFrame` and insets its content, not its backdrop, by
  `LocalBackdropContentStartInset`. While two such entries cross, the one leaving hides its sidebar
  (`sidebarHandedOff`); two overlapping dims would darken the strip for the length of the transition.
- **Desktop Details is a board** (`DetailBoard.kt`, `DetailBoardLayout.kt`; windows ≥ `DetailBoardMinWidth`):
  the title tile with the shared Back control and actions, a column of personal tiles (Up next, Your
  progress; Collection and Tonight for films; Watch it your way), then section tabs that pin with
  Back, the logo and the primary button. It draws its own `ShellBackdrop` from the title's artwork
  behind loading, error and content, so the route provides `LocalShellBackdropMode` and a frosted
  sidebar like Person. Its list starts exactly at the merged bar's bottom edge (the bar reports
  `scrolled` only for its surface), which is what lets a `stickyHeader` pin right under the bar.
  The page's top gap (`DesktopTopBarContentGap`) lives in the board item (`DetailBoardArgs.topGap`),
  not in the list padding: as padding it left a strip of backdrop between the bar and the pinned
  row, which must read as one band with the bar.
  - Sections are not laid out by the board. Every layout renders them through
    `configuredMetaSectionItems` from one `DetailSectionArgs`; a tab is an entry in
    `DetailBoardTab` listing the section keys it owns, and Settings → Detail Page still decides order
    and visibility (`detailBoardTabs`). Add a section or a tab there, not in `MetaDetailsScreen`.
  - A tab shorter than the window gets a filler (`detailBoardFillPx`) so the pinned row stays pinned
    when it is selected. Without it the row dropped back down the page on every switch.
    `detailBoardFillPx` only grows the filler once the filler item is visible, so the filler must
    never be zero high: a zero-height last item never counts as visible, and the board could not
    scroll away at all under a short tab (seen on the person board). Give it at least 1 dp, and
    leave the list's own content padding out of the viewport you pass, or the tab overshoots under
    the row. The person board (`PersonBoard.kt`) reuses this filler; regression:
    `PersonCreditsNavigationTest.shortTabStillScrollsTheBoardAwayAndPinsTheTabs`.
  - Put the list below the merged top bar with `Modifier.padding(top = …)`, never as top
    `contentPadding`: a sticky header sticks to the list's own top, so with padding the pinned
    tabs slid up behind the bar once a long tab scrolled on (person board, Career). Regression:
    `PersonCreditsNavigationTest.longCareerKeepsTheTabsPinned` (under a `DetailTopBarState`).
  - Once pinned, the row stays pinned through every tab switch. The measured filler lags a frame,
    so a tab picked from the pinned row settles with a window's room (`detailBoardSettlingFillPx`)
    and the row is held at the top until the tab's heights stop changing; the shown pin state
    includes that settling, so Back, the logo and Play never animate out. Clicking a tab also
    focuses it, and focus's bring-into-view used to scroll the list to the row's place in it
    (Episodes → Your stats lost the logo); the row drops its own bring-into-view requests while
    pinned (`keepListStillWhilePinned` in `DetailBoard.kt`). A frame-count retry is not enough.
    `DetailBoardPinnedTabsUiTest` checks every frame of each switch, flush under the bar.
  - The board item pins itself in the list (`LocalPinnableContainer`), so it stays composed while
    scrolled away under the pinned row. Unpinned, the list dropped it and rebuilt the whole board on
    the way back up, which froze scrolling from the pinned row to the top. The same test checks it
    stays composed; the trailer still pauses through `isHeroCollapsed`, not through disposal.
  - The audience cards moved to the Your stats tab, and the progress card became a tile; both hide
    with the Episodes section. `SeriesAudienceDetailsUiTest` and `DetailBoardModelTest` cover this.
  - Loading, error and content crossfade (`DetailContentState` in `MetaDetailsScreen`); the skeleton
    builds the same side column as the board for the title's type. Side tiles use
    `DetailBoardTileHeights` (series 132/136/144, film 138/130/144); the first tile takes the
    spare height and every tile keeps `DetailBoardTileBottomPadding`. Put a tile's `testTag`
    before its padding, or bounds checks measure the content box: `DetailBoardSideColumnUiTest`.
  - Watch it your way is a three-column grid of up to six actions in the user's order: On TV
    (picker popover, Manage TVs → Settings → Paired TVs), Watch party (creates the party, opens
    its lobby), Download (Sources sheet in download mode), Shuffle, Choose source, Lists. A
    missing action is left out; the grid reflows. Actions with a popover use
    `DetailSecondaryAction.popup`.
  - Episodes: season pills (watched/total, Specials last) with Posters and Row / List at the right,
    in both card styles (the List style renders through `DetailSeriesListHeader`, so board flags go
    there too). An episode click follows the Resume rules: autoplay on or a download plays, else
    Quick pick opens beside the card. Right-click opens the frosted board menu, which ends with
    that episode's Rate strip. A season pill's menu ends with the season's strip when the rating
    source rates seasons (`TrackingRatingProvider.ratesSeasons`: WeTrakr, MDBList; a reference with
    `wholeSeason`; WeTrakr, MDBList, Trakt); with Simkl it stops at the actions.
  - Rate is option B, inline: hover or click opens the ten-star strip, Library/Watched/Trailer
    collapse to icons (only for the star strip; a ♥-only strip opens in Rate's place), keys pick
    (handlers sit above the focus target), Esc/Cancel close even with the pointer still over it.
    The title column is 920dp (`BoardTitleColumnMaxWidth`; text inside stays 760dp) so the open
    strip keeps ✕ and ✓ inside it: never let a control overflow its parent, because the hand
    cursor (`ClickableHandCursor`) finds targets by parent-clipped semantics bounds and an
    overflowing button shows an arrow (`openStripFitsInsideTheTitleColumn`). An
    unrated favorite idles as "☆ Rate ♥"; icon-only chips are padded evenly. The ♥ is
    `TrackingFavorites` (Trakt, WeTrakr). Regression: `BoardRateStripUiTest`.
  - People names never truncate (credit line as one wrapping paragraph, portrait tiles with no
    `maxLines`); the redesign has no round avatars (cast tiles, the credit line's hover card and the
    person page all use `PersonPortrait`, 2:3). The hover card opens after a short rest on a name
    (pointer over the text layout, not a per-name hit box) and never takes focus.
  - The Details tab is `BoardDetailsFacts`: About, More and Links tiles of equal height, then the
    overview. Links shows IMDb and TMDB, then one link per connected tracker from
    `TrackingTitleLinks` (Trakt by id search, WeTrakr by its own id); late links fade in.
  - Reviews read from `TrackingComments` (Settings → Tracking → Reviews source), never a tracker's
    types: optimistic likes roll back on refusal; the reader and composer are frosted popups.
    `BoardReviewsUiTest`.
  - More like this "Unwatched only" and the player's end-credits strip share
    `UnwatchedRecommendations`; Gallery is a section (`GALLERY`, after Trailers) with a
    full-window viewer (TMDB episode stills for the first 60 episodes, Kinopoisk for films and
    series); Behind the film reads Wikidata once per IMDb id.
- **Desktop stream selection is Quick pick plus the Sources sheet** (windows ≥ `StreamsSheetMinWidth`).
  The sheet (`StreamsSheetLayout.kt`) replaces the two-pane list: the title, resume line and a Best
  match card on the left; every source on the right, grouped by resolution, each row showing decoded
  labels over the addon's own release name. Quick pick (`StreamQuickPick.kt`, hosted by the board as
  `DetailQuickPickOverlay`) offers Best match, Faster start and On your server, and opens the sheet.
  - Rows come from `streamRowInfo` (`StreamSheetModel.kt`), which reuses `DebridStreamMetadata.facts`.
    Don't add a second release-name parser; extend that one, or the small fallbacks beside it for
    tokens glued to a neighbour ("DDP5.1", "H.265").
  - With autoplay on, Play still starts playback and "Choose source" opens Quick pick. With autoplay
    off, Play opens Quick pick. A downloaded copy always plays directly.
  - Quick pick loads the same request as the sheet (`manualSelection = true`), so the sheet opens on
    its results. A picked source travels through `StreamPreselection` and the stream route opens it
    behind its loading screen; showing the sheet first read as a flash.
  - Whether a source starts now comes from the addon's own marker as much as from the app's cache
    check: `[TB+]` is Instant, `[TB download]` is "Needs download" (`quickQualityAvailability`). The
    first sheet showed both as "Direct link" because each carries a URL, so sources that still had
    to download looked ready. A download-only source never counts as starting now.
  - Row line one is labels, status, language chips, then voice-over and subtitle tags from the
    addon's description (`streamAudioTags`); line two is the file name. Languages come from the
    addon's flag line when it has one. A voice-over kind without a stated language stays bare:
    never guess its language. Video and audio details are text labels, not the imported logo badges.
  - Preferred languages (the sheet's remembered language filter, then Settings → Playback's audio
    languages) rank ahead of resolution among sources that start now, in Best match, Quick pick and
    the Quality sort. The filter is stored per profile, device-local (`stream_sheet_language`).
  - `StreamSheetModelTest` and `StreamsSheetUiTest` cover labels, grouping, filters, picks and the sheet.
  - Exact copies one addon lists twice merge into the best-seeded row (`mergeDuplicateStreams`, "×N"),
    and same-named rows show what tells them apart. A release's stated resolution beats a "UHD" tag;
    BDRip and Remux are labelled from the release; a bare language tag never repeats a chip.
  - The resume switch (off = from the start, a media server's position too) replaces the resume
    line; download mode swaps it for a "Saves to Downloads" card. Every addon has a status pill.
    A row body opens in place; ▶ plays; hover reveals Download and Copy link.
  - Every pick folds the sheet into the starting card (title, from-time, labels, steps); a direct
    link holds it `StartingCardHoldMs` before the player opens, so nothing flashes. The pick's
    language (the one fewest other sources have), release type and service are learned
    (`StreamPickPreference`, per profile, device-local) and rank after preferred languages and
    before resolution in Best match, Quick pick and the Quality sort; the card says what it learned.
  - On desktop the player always replaces Sources (`replaceStreamRoute`, P2P and external player
    included), and `DesktopNavigationHistory.finishPlayback` drops the popped Sources location,
    so closing the player lands on Details and Back leaves it: `DesktopNavigationHistoryTest`.

Regression check: open the screen, navigate to and back from a detail page, type and refine a
query, switch tabs, and scroll one step. There should be no frame where content pops, jumps, or
flashes. A build or test is not proof; watch it in the running app.

When the user asks for a redesign, existing tests that assert the old layout (for example a pinned
side pane) describe the design being replaced. Update those tests to the new design. Don't keep the
old layout just to satisfy them. Part 3 of the Search redesign first kept the old Person side pane
for that reason, and the user rejected it.

- **No item fade-out in a grid that scrolls itself on the same change.** Folders and See all re-pin
  the filter row (`scrollToItem`) when a filter changes. With `animateItem(fadeOutSpec = …)` the
  removed posters stayed stuck on screen behind the "Nothing matches" card (Watch status → Watched,
  first try only). Use `fadeOutSpec = null` there and let the new items fade in. Reference:
  `HomeSeeAllPage.kt`; check by picking a filter that empties the grid while the row is pinned.

## Stable layout through state changes

- Loading must not move controls, resize content, or change a scroll anchor.
  Reserve a predictable indicator slot or use an overlay that preserves readable
  content. Removing an indicator must preserve the same geometry as showing it.
- Hover-revealed actions keep their space while hidden (fade with `graphicsLayer` alpha, don't
  add them to the layout). The Sources rows added Download / Copy link on hover, which narrowed
  the chip column, re-wrapped the chips and moved the size column. Reference: `StreamSheetRow`;
  regression `StreamsSheetUiTest.aPercentageResumeShowsItsTimeFromTheRuntime` (hover keeps bounds).
- A sticky header's own focusable controls can trigger parent scrolling: the
  framework's sticky-header focus adjustment also applies to those descendants.
  Test dropdown opening as well as selection. `PinnedFilterBringIntoViewSpec` in
  `ConfiguredFolderContent.kt` exempts visible control-sized requests, while
  preserving normal popup scrolling and offscreen/card focus requests, including
  wrapped headers. `ConfiguredCollectionHeroTest` and
  `SeasonalCollectionNavigationTest` cover those boundaries.
  For grids with an intro or pinned controls, verify full-span items use the
  viewport width. Fixed-size cells can leave unused width at the end and
  shift a centered intro to the left. `PersonCreditsNavigationTest` checks
  centering and opens the pinned filter below Back.
- Keep toolbar control positions stable across related modes and label changes.
  Reserve the slots of temporarily unavailable controls without leaving invisible
  controls focusable. Account for localized labels when reserving label width.
- A reserved content area needs explicit dimensions in loading and empty states,
  too: a fixed height alone can still allow an empty column to shrink in width.
- For a paged grid that promises equal cells, derive geometry from available
  viewport space rather than item count. Current, neighboring, and settled pages
  must use the same geometry through drag, cancellation, and commit. Window
  resizing can recalculate it. The number of reserved slots is feature-specific.
- Tiles that must end flush across columns: compose each tile once in a
  `SubcomposeLayout`, measure the fixed-height ones, then give the flexible tile
  the remaining height. A probe pass that composes a tile a second time to measure
  it leaves a duplicate in the semantics tree (two nodes per text and test tag).
  A scrolling grid inside a tile shows whole rows only, never a cut-off row at its
  edge. Details' Your stats grid (`SeriesAudienceSection.kt`) is the reference;
  `SeriesAudienceDetailsUiTest` checks the columns start and end together.

Calendar's permanent loading strip, full-width selected-day shelf, and fixed
month presentation in `CalendarScreen.kt` are references. Its six-row policy is
documented in `CALENDAR.md`, not a requirement for every grid in the app.

## Data state and scroll position

- Distinguish not-yet-loaded from loaded-empty for the requested range/selection.
  Do not flash an empty message or a zero count while replacement data is pending.
  Keep successful content/artwork visible during same-account refresh; account or
  profile changes must still clear private content and invalidate stale responses.
- Totals over paged data (a month's watch count, a week's stats) appear only once
  paging covers the whole period; until then show the label alone or "–", never a
  partial number. History's header kicker and Board tiles are the reference
  (`coveredFrom` in `HistoryScreen.kt`).
- Retry must restart the failed request. Setting an error screen back to Loading
  leaves it there when the request effect is keyed only by an unchanged ID.
  Use the retry counter in `PersonDetailScreen` as the reference.
  `PersonCreditsNavigationTest` exercises failure, Retry, and the loaded destination.
- A pending/refreshing flag must always have a running request that will clear it.
  Rebuilding the addon list re-marked a failed manifest as refreshing without
  fetching it, so every empty Search showed skeleton cards forever. Decide retries
  from the missing data (`manifestUrlsNeedingRefresh`), not from the flag.
  `AddonModelsTest` covers a failed manifest that a rebuild marks pending again.
- A poster tile with no poster or a failed one (e.g. a custom poster service 404) shows
  the item's title inside the tile, never an empty background. Labels may be hidden
  and custom posters may carry the only visible text. `PosterGridTile` (PosterGrid.kt)
  and `NuvioPosterCard`'s placeholder are the references. Check: Search "simpsons"
  with btttr custom posters on (The Simpsons Quiz Show).
- Preserve the visible item's stable key and pixel offset when prepending data.
- Anchor on the item being read, not just the first visible one. LazyColumn holds its first
  visible item, often a sliver of the previous row; when that row's data loads and it grows, the
  row being read is pushed down. Calendar Week re-anchors on the row crossing a quarter of the
  viewport during composition (`KeepReadingWeekAnchor`), skipping a still-unmeasured request;
  `weekKeepsTheWeekBeingReadWhenEarlierWeeksLoad` catches a repeat.
  Do not reveal earlier items by rebuilding the list at index zero. If an index is
  necessary, derive it from the actual rendered list, including synthetic headers.
  Reserve the key of an optional initial shelf before its request completes.
  In the wide actor overview, adding Known For after Movies had already become
  the first item kept Movies at the viewport top and hid the shelf above it.
  `PersonCreditsNavigationTest` delays the request and checks the visible order.
- Wait for the requested data and list layout before revealing a target item.
  In Compose, prefer the existing deferred `requestScrollToItem` pattern when
  reacting to layout updates; forcing `scrollToItem` during measurement caused
  recursive measure/layout errors. See Home's `HomeScrollPosition.kt` and Calendar.
- Treat the selected item/date, visible anchor, and fetched range as distinct
  state. Browsing an empty range must still communicate date/range progression;
  switching views or reselecting a filter must not silently jump to a stale anchor.
- For a combined grid of server-sorted catalogs, refill each source head before
  choosing the next item. Sorting only fetched page unions breaks global order at
  page boundaries. Preserve source filters and identify unsupported provider sorts
  explicitly. `SeasonalGridPagerTest` covers later-page ordering and retry rollback.
- Posters in grids and shelves load individually. A per-row reveal gate with an
  adaptive prefetch scheduler (`PreparedArtworkStore`, `rememberGridArtworkGates`)
  was tried and removed because it hurt scroll performance. Pagination uses the
  plain `LoadMoreOnScrollEffect` near-end trigger.
- When two settings surfaces edit the same relative order, reconcile their persisted
  state explicitly. A drag that updates only one list can leave Home unchanged.
  Preserve unrelated catalog slots and reconcile pre-fix saved state once, without
  repeatedly undoing later Home edits. `SeasonalMigrationTest` covers this path.
- Keep retained callbacks and observers current, and cancel/invalidate work when
  selection, account, profile, or screen activity changes. An older response or
  restore operation must not override a newer user navigation choice.

## Gesture ownership

- A swipe preview follows the user's contact. Commit only when the contact ends
  and meets the accepted threshold; cancellation restores the original page.
  Do not let momentum commit a second page from the same gesture.
- Reuse native contact phases for desktop trackpads. A fallback wheel timeout
  must not end a native gesture while fingers are still down.
- Exclude the actual gesture-owning region from parent history gestures. Keep
  nested shelves independently scrollable and reset transient gesture work on
  disposal. `CalendarMonthPager.kt` and the shared history gesture helpers are
  references; thresholds and directions remain interaction-specific.
- A list scrolling inside a tile (Library's Your lists, the Cloud files panel) keeps its wheel and
  trackpad scroll: `Modifier.containedScroll()` before its scroller. Compose otherwise hands the
  leftover at its end to the page, which then scrolls under the pointer. A nested-scroll connection
  alone isn't enough on desktop: a wheel the list can't use bubbles to the page as a raw pointer
  event, so the modifier consumes that too. Assert the page's scroll range, not a child's bounds.
  `LibraryDesktopUiTest.yourListsKeepsItsScrollFromThePage`.
  Details' Ratings by episode adds it only while it has rows to scroll, so a grid that fits
  lets the page scroll (`SeriesAudienceDetailsUiTest`). A grid's scroll-into-view follows
  the keyboard cursor or selection, never hover: revealing the hovered cell made the
  wheel fight the resting pointer.
- A layer that takes clicks away from what's under it (Home's dimmed rows while a row is being
  moved, the lifted row's drag surface) swallows the whole press, release included, in
  `PointerEventPass.Initial`, and acts only after the release. Consuming just the press and then
  removing the layer let the release reach the poster underneath, which opened Details
  (`HomeScreen` reorder dim, `HomeRowReorder.kt`). Draw such a layer as an overlay sibling on top
  rather than intercepting inside the items, so drag handles under it still work.

## Styling, meaning, and settings

When Settings categories or parent pages change, update desktop history defaults
and retained category-name mappings as well as both responsive Settings screens.
The grouped Settings reorganization exposed stale desktop enum references during
branch integration. `settingsCategoryForName` preserves saved names;
`DesktopNavigationHistoryTest` checks parent traversal and category restoration.

- Reuse shared card components and theme tokens. Scope a feature's style
  override so it does not leak into Home or alter collection cover shapes.
  Persist separate feature preferences independently when the product calls
  for independent controls. History initially reused
  Calendar's persisted orientation and coupled their toggles. Reuse the agenda
  controls with an explicit orientation value and feature-owned setter.
  `HistoryNavigationTest` verifies independence after changing both screens and
  reloading profile preferences.
- Use the shared shelf heading style for every card layout. A ranked shelf may
  use its own numeral typography and card geometry, but its heading should
  align with neighboring shelves and use the same font, size, and color.
- Reuse the shared detail-label typography and muted color for secondary counts
  and metadata. Localize singular/plural counts. Associate an icon or toggle with
  an accessible name and current state; a neighboring text label alone is not an
  accessible association. State icons must depict the documented current/target
  state consistently for that interaction.
- Put required context where it remains visible. When a date heading already
  identifies a group, avoid repeating the same date under every card. Preserve
  required titles and episode/movie labels regardless of optional label settings.
- Convert timestamp-based releases to the device timezone before grouping and
  formatting. Preserve date-only releases without inventing a time or shifting
  their date. Distinguish today from selection/focus; skipping an empty date does
  not make the next visible date today. Follow locale week boundaries and labels.

Ambient screen backdrops show one stable artwork per screen state. The desktop
Search redesign first crossfaded the blurred backdrop to each hovered poster;
the user found that dizzying. Drive `ShellBackdrop` only from the screen's
primary title (Search: the top result; fall back to the theme mesh), never from
hover or focus. A sticky bar over the backdrop stays transparent until it is
actually pinned, then blurs what scrolls beneath it (Haze, every list item a
`hazeSource`) under a dim `BackdropSurface.stickyHeader`, so content never reads
through it as legible text. Check by sweeping the pointer across a
results grid (backdrop unchanged) and scrolling one step (no dark band while the
hero is still visible).

Every desktop Search results view uses the same layout: the All / Movies / Shows tabs,
a spotlight hero, then the content. Tag pages (Netflix, Comedy, Alien invasion…)
first showed a bare merged grid with no tabs; the user asked for them to match the
results page. A tag page is Home's rotating hero (`HomeHeroSection` with its own
`HomeHeroMetaStore`, because a shared store prunes Home's hero entries), then, for streaming
services, numbered "Top 10" rows from any installed FlixPatrol chart catalog
(`flixpatrol.<service>.…`, `HomeRowStyle.NumberedRanking`; `SearchTopTenTest`), then Home-style carousels
(`DetailPosterRailSection`, i.e. `NuvioShelfSection` + `HomePosterCard`) for
Popular, Top rated, and New, with shows and movies in each. "View all" opens the
paged grid, and Back returns to the cached carousels. One failed or empty carousel
is hidden rather than failing the page. `SearchDiscoveryUiTest`
(`tagCarouselsHideEmptyOrFailedRailsAndFeatureTheTopTitle`) covers this, and
`SearchDiscoveryTest` covers the sort filters (Top rated vote floor, 90-day New window).

The tag page is one shared implementation, `streamingProviderPage` in
`features/search/StreamingProviderPage.kt`. Search's tags and the service folders of the
`nuvio-streaming` collection (`StreamingProviderScreen`) both call it, so the two cannot drift:
add or change a row there, never in one caller. The user had asked for the streaming collection
to show what Search's Netflix tag shows, because the two used different catalog sources.

- Row order and hidden rows are one user setting for every tag (`StreamingRowLayout`,
  profile-scoped, device-local), edited from "Customize rows" in the page header: an anchored
  panel with drag handles and switches that applies live. Do not hard-code a row sequence in
  the page; add a `StreamingRowId` and let `tagPageRows` place it.
- Rows only some services have go in the **Service collections** slot: the sources of the
  service's folder in the streaming collection (Marvel, Pixar…). The slot keeps one position
  in the order and takes no space where a service has none. A row the tag lacks (no chart, no
  original films) is skipped the same way.
- A service's Originals come from TMDB networks (shows) and, only where a studio makes nothing
  but that service's originals, a production company (films); see `StreamingProviderSpec`.
- When the streaming country has no catalog for a service (TMDB has none for Lithuania on
  several), the region-filtered rails reload once for the United Kingdom
  (`STREAMING_FALLBACK_REGION`), and a note beside the filter says which catalog is shown. Top 10
  picks the chart for the streaming country, then the UK, then any. "View all" and the slot's
  rows use the region the page ended up with (`StreamingCatalogRegion`). A failed request is
  not an empty catalog and must not trigger the fallback.
- The tag page's All / Movies / Shows filter is a pill group (`TagScopePills` on the shared `RedesignPillGroup`, inside the shared
  `TagPageControls`), and "Customize rows" is an icon-only button. Underlined text tabs under
  the merged top bar read as a second copy of the bar's own For you / Movies / Shows. Search's
  results tabs with counts (`SearchScopeTabs`) stay text tabs, and the bar selects no Home tab
  on the service page (`DetailNavigationFrame(highlightHomeTab = false)`).
- The service page's header is one ~60dp row on Home's margins
  (`desktopPageHorizontalPaddingForWidth`): back, logo, filter, Customize. It floats over the
  list right under the merged bar and draws the shared pinned surface (below).
- Esc is caught at the window (`PlatformEscapeShortcut`) before any `PlatformBackHandler`, so a
  page state Back should undo first must claim `AppKeyboardShortcuts.overlayEscape` while it is
  the top route. An open in-page panel and the service page's "View all" grid both do; without
  it, Esc from the grid went to Home instead of the rows.
- Session state that outlives a page must not decide where a fresh visit opens: the service
  page reopened in the grid it was left in. `StreamingProviderSessions.sessionFor(freshVisit)`
  closes it on a new visit and keeps it when returning from a title.

`StreamingRowLayoutTest` covers the order, the slot, Originals and the fallback;
`SearchTopTenTest` the chart choice; `StreamingProviderNavigationTest` the folder-to-page
navigation and the panel; `StreamingProviderSessionsTest` the fresh-visit grid reset.

The redesign's tokens live in `core/ui/RedesignStyle.kt` (`RedesignColors`, `redesignText`,
`RedesignTypography`). Person Details (desktop, wide) is the second screen that uses them. It has
an ambient backdrop and a full-width header (avatar, department eyebrow, name, fact pills, a
Favorite placeholder tagged `person_favorite`, and the bio) that scrolls with the page. "View all"
subviews show a compact header (`actor_header_compact`). Tablets keep the side pane.
`PersonCreditsNavigationTest` covers this.

The Person timeline ("Career", `PersonCareerTimeline.kt`) has one tab per department with credits
(`CareerDepartment`, shared `RedesignTabs`). It first read only TMDB cast credits, so a director's page
showed only acting cameos. The tab for the person's known-for department comes first and is selected;
"As self" is last; one department shows no tab row. Crew rows show their jobs where cast rows show
characters. Rows sit under one label per year. Row keys include the department, so a tab switch fades
rows instead of sliding a title between lists, and the tab row must not move. The whole row opens the
title by pointer, but only the title text is the semantic button: a merged clickable row hid the
rating, type and role nodes. `PersonCreditsTest` (department mapping, tab order) and
`PersonCreditsNavigationTest.directorTimelineOpensOnDirectingAndSwitchesToActingInPlace` cover this.

Panels that own a background must also own readable foreground colors.
The football lineup and actor catalog screenshots exposed black inherited
text on dark panels. Use a Material Surface with an explicit content color,
or use the theme text tokens as the actor catalog does with the shared shelf
heading. Check rendered captures as well as semantic text existence.

## Playback control ownership

Desktop movie playback suppresses Compose controls, so its actual
`PlayerScreenRuntimeUi` caller must explicitly enable native controls. Honoring a
previously ignored `useNativeController=false` default for trailers hid the movie
WebView too, leaving video with no controls. Preserve one visible control owner
in every caller when changing the shared surface contract. Verify the actual
movie screen and native view availability (`MoviePlayerControlsLiveTest`), then
exercise visible pause/seek/menu buttons in the packaged UI. A lower-level test
that explicitly passes `true` or only checks decoding cannot catch this regression.

## Verify transitions, not only settled screens

- When an expansion keeps the same identity panel, preserve that panel and
  its data while swapping the browse content. Re-entering the actor screen
  for a catalog recreated the sidebar and replayed its loading placeholder.
  `PersonCreditsNavigationTest` checks the sidebar bounds and request count
  during catalog expansion. Clip shelf hover overdraw at the pane boundary so
  scrolled posters cannot cover the sidebar. Use the same column count for
  the grid and its layout when fitting a card into the embedded pane.
  The test compares sidebar pixels after horizontal scrolling and checks the
  first row against a width that fits four landscape cards.
  Feed the real scroll offset into the shared narrow actor profile in both
  views. Reusing its layout with a zero offset suppressed the collapse.
  The same test partially scrolls the catalog and measures the avatar.
  Match its starting bounds across the overview and expanded grid. Apply
  navigation clearance to the pinned filter bar so catalog padding does not
  move the photo, name, or biography. The test compares all three bounds.

Finish Gradle compilation and tests before launching MainKt for review. A second
build can rewrite the JAR/classes used by an already running development JVM,
causing `NoClassDefFoundError` for otherwise present classes. The Dev merge
launch exposed this with `SettingsCategory` and Trakt nested classes. Verify a
sequential `:composeApp:run` launch after the final build; do not rebuild its
runtime outputs while that review window is open.

- Use isolated profiles and in-memory authentication/preferences as required by
  `AGENTS.md`. Exercise real buttons, wheel/drag events, filters, and navigation;
  assert the displayed destination or visible anchor, not just internal state.
- For layout fixes, compare content/control bounds before, during, and after
  delayed loading, empty results, mode changes, and resizing. For variable-length
  pages, include the shortest and longest datasets. Allow only unavoidable pixel
  rounding when comparing individual weighted cells.
- Inspect intermediate gesture frames and cancellation as well as the final page.
  Compare retained artwork during refresh when a fix promises no artwork flash.
  Synthetic events and frame captures do not establish physical trackpad feel;
  report hardware behavior that was not exercised.
- `CalendarTransitionsUiTest` covers delayed loading, four/five/six-week months,
  leap February, resize, retained artwork pixels, and cancel/commit frames.
  `CalendarScreenUiTest` covers toolbar alignment, Day prepend anchors, empty-range
  browsing, filter reselection, and displayed detail destinations.

Maintain these rules under `AGENTS.md`'s automatic documentation maintenance
policy. Add only demonstrated reusable lessons; link feature details instead of
accumulating a history of individual bugs here.


## One top bar on every screen

The owner found the desktop bar different on Home, Search and detail pages: labels on Search
but icons elsewhere, a bar that auto-hid only on detail pages, and four surface colours (clear,
solid, a 72% grey dim, see-through over Search's results). Keep these together:

- The merged bar (`DesktopMergedTopBar`) is always shown, on the tabs and over detail pages
  (`DetailNavigationFrame`). There is no auto-hide and no switch to remove it.
- Its labels follow the Navigation Bar Style setting only (`desktopNavigationLabelFraction`:
  Expanded labels, Compact icons, Adaptive icons until hovered). Never vary them by screen,
  hero or scroll position. `DesktopNavigationBarTest`.
- A newly mounted bar places its underline under the selection without animating; only a change
  within one bar slides it. Downloads, Details and Streams each mount their own bar in
  `DetailNavigationFrame`, and an underline animated from x = 0 swept in from the left on every
  navigation. `TopBarIndicatorTest`.
- The bar and every band pinned under it (Search's field band, a service page's header, Home's
  tabs) draw one surface, `PinnedBarBackground` / `topBarSurfaceColor`, chosen in Settings →
  Layout → Top bar background (`TopBarBackground`). Never give a pinned band its own colour.
  Solid is the default: titles read through `stickyHeader` (0.72) and even 0.9–0.96 alpha.
  `TopBarBackgroundTest`.
- "Scrolled" means content has moved under the surface. Tabs report it through
  `LocalNuvioNavBarScrollState` (Search must too; it once reported nothing, so posters ran under a
  clear bar). Detail pages report it, with their backdrop's tint, through
  `LocalDetailTopBarState`. A tab whose header stays pinned outside its scrolling panes (Library,
  Calendar, History) reports 0: nothing passes under the bar, and Library's reported pane scroll
  once turned the bar solid over a fixed header. Report from a `snapshotFlow`: a `SideEffect` in the page's outer scope
  never re-ran while the list scrolled, so the service page's bar stayed clear over its rows.
- A detail page's floating controls start below the bar (`DesktopMergedTopBarHeight`). Person's back
  button sat at 32 dp, half inside the bar's hit area, so most clicks on it did nothing; Person also
  never reported, so the career timeline ran through a clear bar's labels
  (`PersonTopBarScrollReport`).
- A band under the bar starts its surface below the bar, so translucent options don't double.
- Tinted takes the colour actually behind the bar: the artwork's top strip under the backdrop's
  40% scrim (`backdropTopTint`), capped so white labels stay readable. The image's dominant colour
  came out a darker, different hue than the backdrop. Tabs off the backdrop (Settings)
  get no tint; they kept the last artwork's colour. `TopBarBackgroundTest`.

## Custom detail routes and native navigation

Desktop collection destinations lost their tab controls because their NavDisplay
entries bypassed the shared frame used by Details and Streams. Wrap collection
browsing and management entries in `DetailNavigationFrame`. The top bar overlays
the page at its existing position. Only the sidebar reserves content space.
`MainAppTabNavigationTest` exercises Settings → Collections → editor → Search
in both desktop layouts. `SeasonalCollectionNavigationTest` covers Home →
Explore collection and leaving the folder through the shared navigation.
The detail frame's bar is always visible (see "One top bar on every screen");
`DetailNavigationFrameTest` checks it stays after inactivity.

Provider details must reuse the actual desktop detail components, not approximate
screenshots with generic buttons or typed arrow characters. Use
`DesktopDetailBackdrop`, `DetailActionButtons`, and `NuvioBackButton` as in
`MetaDetailsScreen`: viewport-filling art/gradients and the 48 dp circular arrow
control with a 24 dp icon, 0.34 black container and existing page inset.

The Go3 review exposed both a hidden shared back button and an incorrect fix
using a text Back button and capped poster. Resolve native-header ownership with
a scoped navigation local so the shared back component remains visible; do not
replace it with an ad hoc control. Explicit user references take precedence over
prior feature-specific layout choices. `Go3IntegrationTest` checks the viewport
backdrop, 48 dp Back control, and actual navigation actions.

## macOS accessibility and native UI bridges

Do not synchronously dispatch from the AWT event thread to AppKit when registering
or removing native observers during Compose effects. Accessibility can already
be waiting on AWT, creating a circular wait (confirmed in history_gestures.mm).
Queue observer work asynchronously, retain its lifetime, and mark it inactive
before teardown so cancellation before installation is safe. Verify Home → Search
under accessibility inspection and confirm keyboard input still works; inspect
AWT/AppKit stacks if the screen paints once but then stops responding.

Desktop playback chrome idle timing belongs to the native controls WebView, whose
existing interaction guard sees hover, focus, scrubbing, and open menus. Do not run
a competing shared Compose hide timer on desktop: it hides stationary hovered
controls after 3.5 seconds. Verify toolbar hover beyond the idle delay, then pointer
leave and keyboard menu navigation. Mobile retains its shared timer.

The desktop seek preview (target time, later thumbnails) is part of that WebView
(`#seekPreview` in `player-ui/controls.*`), not Compose. It shows on bar hover and
pointer drag, not keyboard/wheel fine-seek, and maps the pointer exactly like the
range input (10px thumb inset, 1/1000 steps) so the shown time is where a click
lands. Position it absolutely over `.progress`; it must not move layout.
Pointer moves must only write its text and a `transform`: reading
`offsetWidth`/`offsetLeft` per move forced page layout and made the bubble
visibly trail the cursor in WKWebView. Cache bar geometry (re-measure on enter,
resize, chrome render) and keep fade/scale on an inner element, never a
transition on the moving one. WKWebView does not capture the pointer to the
range input, so a seek drag released over the video sent `click` to
`#playerRoot` and toggled playback; ending a seek-bar press now suppresses that
one root click (Chromium tests need the WebKit event order simulated).
Regression check: `scripts/test-seek-preview.cjs` (same-frame tracking,
drag-release), then hover-sweep and drag off the bar in the real player.

Seek-bar thumbnails (Settings → Playback → Player interface → Seek Bar
Thumbnails, `seekPreviewEnabled`) follow the mobile SeekPreviewController
policy: warm-up when the controls appear, prefetch spread over the title
(paused while buffering), latest-wins requests, idle release. Kotlin
`SeekPreviewThumbnailer` drives a headless keyframe decoder
(`native/shared/seek_thumbnailer.h`, a second mpv with `vo=null`) and pushes
frames with `NativePlayerBridge.evaluateControlsScript`. Never send per-frame
data through `updateControls`: each bridge keeps only the latest pending
controls payload, so a frame push can drop a real controls update. The page and
Kotlin share the bucket formula (`seekPreviewBucket` in both); change them
together. Checks: `SeekPreviewThumbnailerTest`, `SeekPreviewNativeLiveTest`
(`NUVIO_PLAYER_LIVE_TEST=1`), `scripts/test-seek-preview.cjs`, then hover a real
HTTP stream and compare a thumbnail with the frame after clicking there.

For formation/pitch layouts, scope flex-row rules to actual rows, not every
direct child. Go3's absolute player markers inherited a 350px minimum height from
a broad child selector, separating names from shirt numbers. Render a full
22-player fixture and verify marker/name bounds and resizing; one-player and
semantic-only tests missed the overlap. See docs/football-lineups.md.

## Card action overlay ownership

Render feature card actions through the app shell's full-window poster overlay
host, using the same haze source and stacking layer as Home. A History-local
overlay left the sidebar exposed and only dimmed readable background content.
Reuse `PosterZoomOverlayPortal`; do not create a feature-local blur source.
`HistoryNavigationTest` opens the real menu in both desktop layouts and clicks
the covered Home control to verify the scrim dismisses without navigating.

A portal-hosted overlay outlives its caller's dismissal by one frame. History's
portal lambda read its mutable anchor; dismissal cleared it, so the still-hosted
overlay recomposed with a null anchor, landed the card, and drew its fallback
preview (plain surface, no badge) over the returned card for that frame: the
recorded blank-card flicker. Home and Continue Watching compose their overlay
beside the anchor and leave in the same pass. `NuvioPosterZoomActionOverlay`
keeps the opened anchor until disposal, like its frozen actions; do not
work around this with animation delays or source snapshots (both failed).
`PosterZoomReturnTest.portalOverlayReadingClearedAnchorDoesNotCoverReturnedCard`
steps frames after dismissal and failed at the first one on the previous code.
Settled screenshots and the polled native fixture missed the one-frame gap.

The overlay also hides the card from hover events: a stationary pointer got a
hover exit while the menu was open, so the card landed unscaled and then grew
on re-entry. `PosterLiftSource.hoverHold` mirrors whether the pointer is over
the card while lifted, holds that until the next hover enter, and its live
transformed bounds give the return target. Cards that scale outside
`posterCardClickable` (`NuvioPosterCard`'s column) must share the source via
`rememberPosterLiftSource`. `hoverScaledCardReturnsWithoutShift` and
`cardLeftDuringMenuReturnsUnscaled` cover both pointer paths.

## Esc closes the innermost open layer

The desktop AWT Esc dispatcher (`DesktopKeyboardShortcutHost`) runs before Compose back
handlers, so a layer that only registers `PlatformBackHandler` never sees Esc: the page under it
navigates Back instead. The shared card menu ran History's Back (to the previous tab) while it
stayed open, and Esc on a Settings bottom sheet disposed the screen under the still-rendering
sheet and crashed the app ("RootNodeOwner is already disposed"). Every open layer (menus, panels,
card overlays, sheets, in-place lists) claims Esc with `OverlayEscapeHandler` (`AppKeyboardShortcuts`);
claims stack, so closing a card menu over Library's open list hands Esc back to the list.
`NuvioPosterZoomActionOverlay` and `NuvioModalBottomSheet` claim it themselves. Checks:
`AppKeyboardShortcutsTest`, and `HistoryNavigationTest` presses the registered Esc with the
menu open and asserts History stays.

## Desktop Search keyboard shortcuts

The desktop AWT Escape dispatcher runs before Compose key handlers. Route Escape
to the focused Search field before global Back, and scope that priority to the
active Search tab. A nonempty field clears in place, while the next Escape
releases focus. Preserve route Back when a destination covers Search.
`SearchDiscoveryUiTest` exercises the registered shortcut against the real
Search tab, including both presses and the recent-search confirmation.

Keep a browse session at the same lifetime as its scroll state when a Details
destination can dispose the tab content. Search Discovery previously restored the
query and scroll state but lost the chip and loaded pages. Reuse the shared
near-end page loader (`LoadMoreOnScrollEffect`) with the retained cursor and a bounded run of pages that
add no cards. `SearchDiscoveryUiTest` unmounts and remounts the browse content
and checks its selected filter and results.

## Right click on desktop cards

- Clickable desktop cards, including redesign tiles with their own hover look (the Home board's
  `boardClickable`), go through `posterCardClickable` (`hoverScaleEnabled = false` when the tile
  draws its own hover), never a bare `combinedClickable`. That is what gives right click and
  press-and-hold the shared zoomed options menu (`NuvioPosterZoomActionOverlay`); without its anchor
  the app falls back to the plain bottom sheet. Trigger: board Continue Watching cards ignored right
  click, then opened the bottom sheet instead of the zoomed menu. Check: `HomeBoardResumeTileTest`.
