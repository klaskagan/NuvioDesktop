package com.nuvio.app.features.catalog

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.SeasonalTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class SeasonalCatalogHeroTest {
    @Test fun halloweenHeroScrollsAwayWhileBackStaysAccessible() = runDesktopComposeUiTest(width = 1440, height = 900) {
        var wentBack = false
        setContent {
            NuvioTheme { fixture(CatalogTarget.Seasonal("halloween"), CatalogUiState(items = movies), onBack = { wentBack = true }) }
        }
        onNodeWithTag("seasonal_catalog_background_halloween").assertIsDisplayed()
        onNodeWithText("Halloween Nights").assertIsDisplayed()
        screenshot("halloween-top")
        onNodeWithTag("catalog_grid").performScrollToIndex(20)
        onNodeWithTag("seasonal_catalog_background_halloween").assertIsNotDisplayed()
        onNodeWithTag("catalog_back").assertIsDisplayed().performClick()
        runOnIdle { assertTrue(wentBack) }
        screenshot("halloween-scrolled")
    }

    @Test fun christmasHeroHandlesNarrowWindowsAndLargeText() = runDesktopComposeUiTest(width = 480, height = 800) {
        setContent {
            NuvioTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.8f)) {
                    fixture(CatalogTarget.Seasonal("christmas"), CatalogUiState(items = movies))
                }
            }
        }
        onNodeWithTag("seasonal_catalog_background_christmas").assertIsDisplayed()
        onNodeWithText("Christmas Together").assertIsDisplayed()
        val title = onNodeWithText("Christmas Together").fetchSemanticsNode().boundsInRoot
        val back = onNodeWithTag("catalog_back").fetchSemanticsNode().boundsInRoot
        assertTrue(title.top >= back.bottom, "Large text must remain below the back control")
        screenshot("christmas-narrow-large-text")
        onNodeWithTag("catalog_grid").performScrollToIndex(12)
        onNodeWithTag("catalog_back").assertIsDisplayed()
    }

    @Test fun heroRemainsVisibleAcrossLoadingEmptyAndErrorStates() = runDesktopComposeUiTest(width = 1200, height = 800) {
        var state by mutableStateOf(CatalogUiState(isLoading = true))
        setContent { NuvioTheme { fixture(CatalogTarget.Seasonal("halloween"), state) } }
        onNodeWithTag("seasonal_catalog_background_halloween").assertIsDisplayed()
        runOnIdle { state = CatalogUiState() }
        onNodeWithTag("seasonal_catalog_background_halloween").assertIsDisplayed()
        runOnIdle { state = CatalogUiState(errorMessage = "Unable to load seasonal movies") }
        onNodeWithTag("seasonal_catalog_background_halloween").assertIsDisplayed()
        onNodeWithText("Unable to load seasonal movies").assertIsDisplayed()
        screenshot("halloween-error")
    }

    @Test fun ordinaryAndUnknownCatalogsKeepTheirExistingHeader() = runDesktopComposeUiTest(width = 1200, height = 800) {
        var target by mutableStateOf<CatalogTarget>(CatalogTarget.Addon("https://example.invalid/manifest.json", "movie", "popular"))
        setContent { NuvioTheme { fixture(target, CatalogUiState(items = movies)) } }
        onNodeWithTag("seasonal_catalog_intro").assertDoesNotExist()
        onNodeWithText("Standard catalog").assertIsDisplayed()
        screenshot("standard-catalog")
        runOnIdle { target = CatalogTarget.Seasonal("unknown") }
        onNodeWithTag("seasonal_catalog_intro").assertDoesNotExist()
        onNodeWithText("Standard catalog").assertIsDisplayed()
    }

    @Test fun artworkMappingIsLimitedToKnownSeasonalTargets() {
        SeasonalTheme.entries.forEach { theme ->
            assertEquals(theme, CatalogTarget.Seasonal(theme.id).seasonalThemeOrNull())
            assertNotNull(theme.backgroundArtwork)
        }
        listOf(
            CatalogTarget.Addon("addon", "movie", "popular"), CatalogTarget.Library("movie", "watchlist"),
            CatalogTarget.Server("server", "library", "movie"),
            CatalogTarget.CollectionSource("collection", "folder", "source", "movie"),
            CatalogTarget.Seasonal("unknown"),
        ).forEach { assertNull(it.seasonalThemeOrNull()) }
    }

    @Composable
    private fun fixture(target: CatalogTarget, state: CatalogUiState, onBack: () -> Unit = {}) {
        val theme = target.seasonalThemeOrNull()
        CatalogContent(
            title = when (theme) { SeasonalTheme.HALLOWEEN -> "Halloween Nights"; SeasonalTheme.CHRISTMAS -> "Christmas Together"; else -> "Standard catalog" },
            subtitle = "Explore stories selected for this season. Discover favourites and something new.",
            target = target, uiState = state, gridState = rememberLazyGridState(),
            watchedKeys = emptySet(), fullyWatchedSeriesKeys = emptySet(), networkCondition = NetworkCondition.Online,
            onBack = onBack, onRetry = {},
        )
    }

    private fun ComposeUiTest.screenshot(name: String) {
        val directory = File("build/reports/seasonal-catalog").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private val movies = (1..70).map { MetaPreview("seasonal-fixture-$it", "movie", "Seasonal movie $it") }
}
