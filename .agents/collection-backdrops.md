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
  and `composeApp/src/desktopTest/kotlin/com/nuvio/app/SeasonalCatalogNavigationTest.kt`:
  presentation and real navigation regression coverage.

## Artwork and identity

- A collection page uses the same dedicated artwork as its Home banner. Resolve
  artwork from the selected collection identity, independently of movie results.
  Do not choose the first movie's backdrop as the collection background.
- Seasonal catalogs resolve `CatalogTarget.Seasonal.themeId` through
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
- Key scroll state to the destination and relevant existing filters so changing
  collections does not inherit an unrelated scroll position. Retained callbacks
  must observe the current navigation selection.
- Preserve navigation contracts, persisted settings, and database schemas unless
  the requested feature specifically requires changing them.
- The hero reference does not imply new horizontal categories or a replacement
  for the existing movie grid.

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
