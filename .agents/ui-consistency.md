# UI consistency and regression prevention

Read for changes to layout, loading, scrolling, gestures, or date presentation.
These are shared defaults derived from confirmed failures. Explicit user choices
take precedence; feature-specific contracts remain in feature documentation.

## Stable layout through state changes

- Loading must not move controls, resize content, or change a scroll anchor.
  Reserve a predictable indicator slot or use an overlay that preserves readable
  content. Removing an indicator must preserve the same geometry as showing it.
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
