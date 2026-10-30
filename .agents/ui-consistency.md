# UI consistency and regression prevention

Read for changes to layout, loading, scrolling, gestures, or date presentation.
These are shared defaults derived from confirmed failures. Explicit user choices
take precedence; feature-specific contracts remain in feature documentation.

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
- Preserve the visible item's stable key and pixel offset when prepending data.
  Do not reveal earlier items by rebuilding the list at index zero. If an index is
  necessary, derive it from the actual rendered list, including synthetic headers.
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
- Grid row readiness must include displayed image/logo readiness, not just finished
  metadata requests. Preserve a revealed row when pagination appends members to a
  partial row, and let later visible rows wait for earlier image gates. Shared
  `PreparedArtworkStore`/`rememberGridArtworkGates` own this behavior;
  `PreparedArtworkStorePinningTest` covers both regressions.
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

- Reuse shared card components, viewport prefetch, readiness gates, and theme
  tokens. Scope a feature's style override so it does not leak into Home or alter
  collection cover shapes. Persist separate feature preferences independently
  when the product calls for independent controls.
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


## Custom detail routes and native navigation

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
