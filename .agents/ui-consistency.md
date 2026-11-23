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
- **Controls ease:** hover and selection colours tween (~140–220 ms), and tab indicators slide
  (`SearchScopeTabs`). Avoid default grey indication boxes on redesigned surfaces.
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
- **Home's hero tile expands; it does not toggle the window.** On the board the hero's corner
  control used to be the window-fullscreen button, which read as "expand this card". It now expands
  the tile over the whole board and plays the current title's trailer with sound
  (`HomeHeroSection(expanded, onToggleExpanded)`); the carousel holds while expanded. Window
  fullscreen stays on its shortcut and the system control. A row's lead card plays its trailer in
  place on hover under the poster hover-preview settings; ordinary posters keep their overlay card.
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
- **Filters under the merged top bar are pills, not text tabs.** Calendar and History use
  `RedesignPillGroup` / `RedesignPillButton` / `RedesignIconButton` (`core/ui/RedesignControls.kt`);
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
- **Frosted sidebars come in pairs.** A page with its own backdrop in a separate nav entry (Person)
  passes `frostedSidebar` to `DetailNavigationFrame` and insets its content, not its backdrop, by
  `LocalBackdropContentStartInset`. While two such entries cross, the one leaving hides its sidebar
  (`sidebarHandedOff`); two overlapping dims would darken the strip for the length of the transition.

Regression check: open the screen, navigate to and back from a detail page, type and refine a
query, switch tabs, and scroll one step. There should be no frame where content pops, jumps, or
flashes. A build or test is not proof; watch it in the running app.

When the user asks for a redesign, existing tests that assert the old layout (for example a pinned
side pane) describe the design being replaced. Update those tests to the new design. Don't keep the
old layout just to satisfy them. Part 3 of the Search redesign first kept the old Person side pane
for that reason, and the user rejected it.

## Stable layout through state changes

- Loading must not move controls, resize content, or change a scroll anchor.
  Reserve a predictable indicator slot or use an overlay that preserves readable
  content. Removing an indicator must preserve the same geometry as showing it.
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
- The bar and every band pinned under it (Search's field band, a service page's header, Home's
  tabs) draw one surface, `PinnedBarBackground` / `topBarSurfaceColor`, chosen in Settings →
  Layout → Top bar background (`TopBarBackground`). Never give a pinned band its own colour.
  Solid is the default: titles read through `stickyHeader` (0.72) and even 0.9–0.96 alpha.
  `TopBarBackgroundTest`.
- "Scrolled" means content has moved under the surface. Tabs report it through
  `LocalNuvioNavBarScrollState` (Search must too; it once reported nothing, so posters ran under a
  clear bar). Detail pages report it, with their backdrop's tint, through
  `LocalDetailTopBarState`. Report from a `snapshotFlow`: a `SideEffect` in the page's outer scope
  never re-ran while the list scrolled, so the service page's bar stayed clear over its rows.
- A band under the bar starts its surface below the bar, so translucent options don't double.
- Tinted takes the colour actually behind the bar: the artwork's top strip under the backdrop's
  40% scrim (`backdropTopTint`), capped so white labels stay readable. The image's dominant colour
  came out a darker, different hue than the backdrop. Tabs without artwork (Library, Calendar,
  History, Settings) get no tint; they kept the last artwork's colour. `TopBarBackgroundTest`.

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
