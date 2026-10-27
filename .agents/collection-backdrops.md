# Collection backdrop guidance

These rules capture the approved seasonal collection treatment. Reuse them when
improving other collections if that collection is explicitly in scope. They do
not enable hero artwork for every catalog automatically.

## Existing references

Paths are relative to the repository root:

- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/SeasonalCatalogHero.kt`:
  artwork, overlays, introduction, and back control.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/CatalogScreen.kt`:
  full-span introduction, measured backdrop height, and existing grid integration.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/SeasonalTheme.kt`:
  season identities and bundled background mappings.
- `composeApp/src/desktopTest/kotlin/com/nuvio/app/features/catalog/SeasonalCatalogHeroTest.kt`
  and `composeApp/src/desktopTest/kotlin/com/nuvio/app/SeasonalCollectionNavigationTest.kt`:
  presentation and real navigation regression coverage.

## Artwork and identity

- A collection page uses the same dedicated artwork as its Home banner. Resolve
  artwork from the selected collection identity, independently of movie results.
  Do not choose the first movie's backdrop as the collection background.
- Seasonal Home Explore opens the configured season folder. Folder `seasonalThemeId`
  resolves bundled artwork and survives renaming/reordering. Legacy seasonal catalogs resolve
  `CatalogTarget.Seasonal.themeId` through
  `SeasonalTheme`. Halloween uses the supplied bundled WebP; the remaining eight
  themes use their own bundled backgrounds. Keep the season selected at navigation
  time even if the calendar changes while the page remains open.
- Unknown seasonal IDs use the standard catalog presentation. Other catalog types
  retain their current presentation unless the requested work includes them.
- Dedicated background art may be AI generated when authorized. Movie cards use
  real database artwork: prefer landscape art with titles, or a real backdrop
  with the real title/logo treatment. Do not generate movie posters or stretch
  portrait posters into landscape cards.
- Reuse bundled seasonal resources; this treatment requires no additional image
  API requests. Use Nuvio's names and descriptions, without Netflix branding.

## Configured community collections

By Decade, Studios, and the unnamed streaming collection use synced folder
`heroBackdropUrl` and `titleLogoUrl` artwork through `CollectionArtwork.kt`.
Home keeps the existing collection shelves and cover/GIF cards; do not apply the
seasonal spotlight layout there. Folder pages and expanded source
catalogs share the seasonal backdrop overlays and scrolling treatment. Preserve
collection/source identity when expanding an addon-backed collection row so its
artwork and pagination remain attached to that folder. Personal and other
community collections retain their standard presentation.

## Layout and scrolling

- Render artwork full width behind the introduction, with proportional
  crop-to-fill (`ContentScale.Crop`). Avoid a fixed image width that leaves a
  blank section at the right edge. Clip drawing to the page bounds.
- Put the existing title and description in a full-span introductory grid item.
  Use existing horizontal padding, typography, theme colors, and back component;
  do not render a duplicate catalog title/header.
- Set the introduction's minimum height to 60% of the viewport, clamped to
  280–560 dp. This is a minimum, not a maximum: allow wrapping and expansion for
  larger text. Measure the actual introduction and match the artwork's height.
- Apply a dark readability overlay and a bottom gradient ending in the actual
  app background color. Reuse the reference's theme tint and gradient treatment.
- Move the backdrop with the introduction's scroll offset. Once the introduction
  leaves the viewport, move its artwork completely out of view. The hero scrolls
  away with its title and description rather than remaining fixed behind movies.
- Keep the back control accessible above the scrolling content with its existing
  top scrim and insets. Respect native navigation ownership.
- Render the hero immediately for loading, empty, and error states; its identity
  and artwork must not depend on successfully fetching movies.

## Preserve catalog behavior

- Keep existing movie cards, column settings, loading, retry, pagination, click
  handlers, and restoration when returning from details.
- Key scroll state to the destination so changing collections does not inherit an
  unrelated scroll position. Keep seasonal query changes independent of the scroll
  state's lifetime: preserve a visible hero and only reset results under pinned
  controls after it has scrolled away. Capture the anchor before replacement layout.
  A server request restarting at page one does not mean collapsing the hero.
  Retained callbacks must observe current selection.
- Transfer the Rows/Grid anchor in the toggle click handler, before publishing the
  mode, never in a post-composition effect (the new layout would draw unanchored
  first). `SeasonalCollectionNavigationTest` checks frames right after the click
  with a manual clock.
- Match hero visibility/offset when switching Rows/Grid. Determine visibility from
  measured bounds, since index zero may remain for the gap after an offscreen hero.
  Short successful grids and folders with only a few rows need enough trailing
  space to keep a valid collapsed-hero anchor. `SeasonalCollectionNavigationTest`
  covers delayed responses, six-title results, two rows, and both hero positions.
- Reserve and clip the scrolling viewport below fixed navigation chrome. Padding
  only the sticky filters leaves the scrolling title able to overlap Back and
  creates an unnecessary gap before filters. `ConfiguredFolderContent.kt` is the
  reference; `SeasonalCollectionNavigationTest` checks the pinned control bounds.
- Seasonal artwork is a per-folder override, shared by Home and Explore. Preview
  the effective bundled/custom image and provide Restore original artwork by
  clearing that override; do not display an unrelated collection URL as its editor.
- Preserve navigation contracts, persisted settings, and database schemas unless
  the requested feature specifically requires changing them.
- Seasonal folders now use the existing collection rows/grid presentation; see
  `docs/features/seasonal-collections.md`. Other catalog heroes retain their current
  presentation. Resolve the season from stable folder metadata, never its edited title.

## Verification

- Exercise Home → Explore collection through the real click handler. Assert the
  displayed destination, title, and matching artwork. For seasonal changes,
  cover Halloween and Christmas and verify all nine theme/artwork mappings.
- Verify the hero scrolls away, pagination works, back stays usable, and returning
  from movie details restores the catalog position.
- Inspect wide and narrow windows, larger text, and loading/empty/error states.
  Confirm addon, library, server, and user-created catalogs retain their existing
  layout when they are outside the change's scope.
- Run full-app desktop UI tests using `scripts/desktop-ui-tests.init.gradle` for
  isolated profile storage. Check that tests ran rather than skipped. Do not use
  the user's profile as a fixture.
- Inspect rendered UI and capture before/after screenshots. A compilation or unit
  test alone is not visual validation. Report any unverified flows.
- When a review build is requested, rebuild the macOS distributable without
  replacing the installed app. Do not commit build output or test artifacts.
