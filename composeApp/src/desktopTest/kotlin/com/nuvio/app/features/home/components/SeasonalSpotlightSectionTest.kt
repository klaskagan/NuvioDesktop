package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.SeasonalTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue

class SeasonalSpotlightSectionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun desktopBannerReservesSpaceBelowContinueWatchingAndExploreWorks() {
        var explored = false
        show(1200, onExplore = { explored = true })
        val shelf = compose.onNodeWithTag("continue_watching").fetchSemanticsNode().boundsInRoot
        val banner = compose.onNodeWithTag("seasonal").fetchSemanticsNode().boundsInRoot
        assertTrue(banner.top >= shelf.bottom + 12f, "Seasonal art must stay below the complete shelf")
        savePreview("desktop")
        compose.onNodeWithText("Explore collection").performClick()
        compose.runOnIdle { assertTrue(explored) }
    }

    @Test
    fun narrowWindowKeepsCopyAndMovieCardsAccessible() {
        var selected = false
        show(440, onPosterClick = { selected = true })
        savePreview("narrow")
        compose.onNodeWithText("Halloween Nights").assertIsDisplayed()
        compose.onNodeWithText("Explore collection").assertIsDisplayed()
        compose.onAllNodesWithText("Sample movie").onFirst().performClick()
        compose.runOnIdle { assertTrue(selected) }
    }

    @Test fun emptySpotlightKeepsItsIdentityAndExploreAction() {
        var explored = false
        show(440, onExplore = { explored = true }, empty = true)
        compose.onNodeWithText("Halloween Nights").assertIsDisplayed()
        compose.onNodeWithText("No titles available. Explore the collection to review its catalogs.").assertIsDisplayed()
        compose.onNodeWithText("Explore collection").performClick()
        compose.runOnIdle { assertTrue(explored) }
        savePreview("empty")
    }

    private fun savePreview(name: String) {
        val directory = File("build/reports/seasonal-spotlight").apply { mkdirs() }
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private fun show(width: Int, onExplore: () -> Unit = {}, onPosterClick: () -> Unit = {}, empty: Boolean = false) {
        val movies = (if (empty) emptyList() else (1..5).toList()).map { MetaPreview("tmdb:$it", "movie", if (it == 1) "Sample movie" else "Movie $it") }
        compose.setContent {
            NuvioTheme {
                Column(Modifier.width(width.dp)) {
                    Box(Modifier.fillMaxWidth().height(140.dp).testTag("continue_watching")) { Text("Continue Watching") }
                    SeasonalSpotlightSection(
                        theme = SeasonalTheme.HALLOWEEN,
                        section = HomeCatalogSection("seasonal", "Halloween Nights", "", "TMDB", CatalogTarget.Seasonal("halloween"), movies),
                        sectionPadding = 24.dp,
                        watchedKeys = emptySet(),
                        fullyWatchedSeriesKeys = emptySet(),
                        onExplore = onExplore,
                        onPosterClick = { onPosterClick() },
                        onPosterLongClick = null,
                        modifier = Modifier.padding(top = 12.dp).testTag("seasonal"),
                    )
                }
            }
        }
    }
}
