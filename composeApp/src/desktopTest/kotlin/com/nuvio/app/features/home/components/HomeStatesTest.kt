package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.ui.LocalNuvioShelfHeaderStyle
import com.nuvio.app.core.ui.NuvioShelfHeaderStyle
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Home design "For you · loading" and "For you · empty, error and offline". */
@OptIn(ExperimentalTestApi::class)
class HomeStatesTest {
    @Test
    fun stateCardsShowTheDesignCopyAndActions() = runDesktopComposeUiTest {
        var addonsOpened = 0
        var retried = 0
        setContent {
            AppEnvironment {
                Column {
                    HomeStateCard(HomeStateKind.NoSources, horizontalPadding = 0.dp, onOpenAddons = { addonsOpened++ })
                    HomeStateCard(HomeStateKind.LoadFailed, horizontalPadding = 0.dp, errorMessage = "manifest: timeout", onRetry = { retried++ })
                    HomeStateCard(HomeStateKind.Offline, horizontalPadding = 0.dp, onRetry = { retried++ })
                }
            }
        }
        onNodeWithText("Add a catalog to start").assertExists()
        onNodeWithTag("home-state-nosources-action", useUnmergedTree = true).performClick()
        onNodeWithText("Couldn’t load Home").assertExists()
        // Desktop shows the design's sentence, never a raw manifest error.
        onNodeWithText("Something went wrong while loading your rows.").assertExists()
        onNodeWithText("manifest: timeout").assertDoesNotExist()
        onNodeWithTag("home-state-loadfailed-action", useUnmergedTree = true).performClick()
        onNodeWithText("You’re offline").assertExists()
        onNodeWithTag("home-state-offline-action", useUnmergedTree = true).assertDoesNotExist()
        runOnIdle {
            assertEquals(1, addonsOpened)
            assertEquals(1, retried)
        }
        // One card style, capped at the design's width.
        val card = onNodeWithTag("home-state-nosources", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(card.width <= HomeStateCardMaxWidth.value + 0.5f, "card width ${card.width}")
    }

    @Test
    fun boardSideColumnHoldsItsPlaceWhileLoading() = runDesktopComposeUiTest {
        setContent {
            AppEnvironment {
                Box(Modifier.width(1300.dp)) {
                    HomeBoard(
                        height = 420.dp,
                        expanded = false,
                        resumeItems = emptyList(),
                        onResumeClick = null,
                        onResumeLongPress = null,
                        sideLoading = true,
                    ) { HomeSkeletonHero() }
                }
            }
        }
        assertEquals(2, onAllNodesWithTag("home_board_side_skeleton", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun skeletonRowMatchesTheLoadedRowLayout() = runDesktopComposeUiTest {
        // Other tests in this JVM may leave landscape posters on; the lead card needs portrait rows.
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        val section = HomeCatalogSection(
            key = "fixture:movie:popular",
            title = "Popular",
            subtitle = "",
            addonName = "Fixture",
            target = CatalogTarget.Addon("https://example.com/manifest.json", "movie", "popular"),
            items = (0..6).map { index ->
                MetaPreview(
                    id = "fixture-$index",
                    type = "movie",
                    name = "Fixture $index",
                    poster = "https://example.invalid/$index.jpg",
                    banner = "https://example.invalid/b$index.jpg",
                    posterShape = PosterShape.Poster,
                )
            },
        )
        setContent {
            AppEnvironment {
                CompositionLocalProvider(LocalNuvioShelfHeaderStyle provides NuvioShelfHeaderStyle.Editorial) {
                    Column(Modifier.width(1300.dp)) {
                        Box(Modifier.testTag("skeleton")) { HomeSkeletonRow(horizontalPadding = 32.dp) }
                        Box(Modifier.testTag("loaded")) {
                            HomeCatalogRowSection(section = section, sectionPadding = 32.dp, eyebrow = "Fixture", leadCard = true, onPosterClick = {})
                        }
                    }
                }
            }
        }
        fun bounds(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val skeletonRow = bounds("skeleton")
        val loadedRow = bounds("loaded")
        val skeletonLead = bounds("home_skeleton_lead")
        val loadedLead = bounds("home_row_lead")
        fun close(a: Float, b: Float, what: String) = assertTrue(abs(a - b) <= 1f, "$what: skeleton $a vs loaded $b")
        close(skeletonLead.width, loadedLead.width, "lead width")
        close(skeletonLead.height, loadedLead.height, "lead height")
        close(skeletonLead.left - skeletonRow.left, loadedLead.left - loadedRow.left, "lead left")
        close(skeletonLead.top - skeletonRow.top, loadedLead.top - loadedRow.top, "lead top (header height)")
        val skeletonPoster = onAllNodesWithTag("home_skeleton_poster", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        println("HOME_STATES skeleton lead=$skeletonLead poster=$skeletonPoster row=$skeletonRow; loaded lead=$loadedLead row=$loadedRow")
        close(skeletonPoster.top, skeletonLead.top, "poster top vs lead top")
        close(skeletonPoster.left - skeletonLead.right, 10f, "gap after lead")
    }
}
