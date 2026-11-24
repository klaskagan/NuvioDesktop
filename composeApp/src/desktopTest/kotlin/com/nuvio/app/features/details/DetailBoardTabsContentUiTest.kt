package com.nuvio.app.features.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.details.components.BoardCastGrid
import com.nuvio.app.features.details.components.BoardGallery
import com.nuvio.app.features.details.components.BoardMoreLikeThis
import com.nuvio.app.features.gallery.GalleryImage
import com.nuvio.app.features.gallery.GalleryKind
import com.nuvio.app.features.gallery.GallerySource
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** The board tabs' own content: portrait cast tiles, More like this's empty state, and the gallery viewer. */
@OptIn(ExperimentalTestApi::class)
class DetailBoardTabsContentUiTest {
    @Test fun portraitTilesWrapLongNamesInsteadOfCuttingThem() = runDesktopComposeUiTest(width = 1300, height = 800) {
        val long = "Alexander Skarsgård-Pettersson"
        setContent {
            AppEnvironment {
                Box(Modifier.width(1200.dp)) {
                    BoardCastGrid(
                        cast = listOf(MetaPerson(long, "Lead role with a long description", episodeCount = 19), MetaPerson("Ann Lee", "Ann")),
                        isSeries = true,
                        onCastClick = null,
                    )
                }
            }
        }
        onNodeWithText("19 eps.", useUnmergedTree = true).assertIsDisplayed()
        // Initials stand in for a missing photo.
        onNodeWithText("AL", useUnmergedTree = true).assertExists()
        val layout = textLayout(onNodeWithText(long, useUnmergedTree = true))
        assertEquals(false, layout.hasVisualOverflow, "lines=${layout.lineCount}")
        assertTrue(layout.lineCount >= 2)
    }

    @Test fun unwatchedOnlySaysWhenEverythingIsWatchedAndOffersShowWatched() = runDesktopComposeUiTest(width = 1300, height = 600) {
        val toggled = mutableListOf<Boolean>()
        setContent {
            AppEnvironment {
                BoardMoreLikeThis(
                    items = emptyList<MetaPreview>(),
                    unwatchedOnly = true,
                    onUnwatchedOnlyChange = { toggled += it },
                    watchedKeys = emptySet(),
                    fullyWatchedSeriesKeys = emptySet(),
                    sourceLabel = null,
                    horizontalScrollPadding = 0.dp,
                    onPosterClick = null,
                )
            }
        }
        onNodeWithTag("detail_board_seen_everything").assertIsDisplayed()
        onNodeWithText("Show watched").performClick()
        assertEquals(listOf(false), toggled)
    }

    @Test fun galleryOpensItsViewerStepsWithArrowsAndClosesWithEsc() = runDesktopComposeUiTest(width = 1360, height = 850) {
        val images = (1..5).map { GalleryImage("https://example.test/$it.jpg", "https://example.test/$it.jpg", GalleryKind.Backdrop, GallerySource.Tmdb, vote = 10.0 - it) }
        val backgrounds = mutableListOf<String>()
        setContent { AppEnvironment { BoardGallery(images, title = "Fixture", onSetBackground = { backgrounds += it }) } }
        onAllNodesWithTag("detail_gallery_tile").assertCountEquals(5)
        onAllNodesWithTag("detail_gallery_tile")[1].performClick()
        waitForIdle()
        onNodeWithTag("detail_gallery_viewer").assertExists()
        onNodeWithTag("detail_gallery_caption", useUnmergedTree = true).assertTextContains("2 / 5", substring = true)
        onNodeWithTag("detail_gallery_viewer").requestFocus()
        onNodeWithTag("detail_gallery_viewer").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithTag("detail_gallery_caption", useUnmergedTree = true).assertTextContains("3 / 5", substring = true)
        onNodeWithTag("detail_gallery_set_background").performClick()
        assertEquals(listOf("https://example.test/3.jpg"), backgrounds)
        onNodeWithTag("detail_gallery_viewer").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag("detail_gallery_viewer").assertDoesNotExist()
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    /** The Details tab: About, More and Links tiles, IMDb and TMDB links from the ids, then the overview. */
    @Test fun detailsTabShowsAboutMoreAndLinksTiles() = runDesktopComposeUiTest(width = 1300, height = 900) {
        val meta = MetaDetails(
            "tmdb:95396", "series", "Severance", imdbId = "tt11280740",
            description = "Mark leads a team of office workers.",
            status = "Returning Series", releaseInfo = "2022-02-18", genres = listOf("Drama", "Mystery"),
            networks = listOf(MetaCompany("Apple TV+")), awards = "10 Primetime Emmys",
            cast = listOf(MetaPerson("Dan Erickson", "Creator")),
            videos = (1..2).flatMap { s -> (1..3).map { e -> MetaVideo("v:$s:$e", "E$e", season = s, episode = e) } },
        )
        setContent { AppEnvironment { Box(Modifier.width(1200.dp)) { com.nuvio.app.features.details.components.BoardDetailsFacts(meta, isSeries = true) } } }
        mainClock.advanceTimeBy(500); waitForIdle()
        onNodeWithText("ABOUT").assertIsDisplayed()
        onNodeWithText("MORE").assertIsDisplayed()
        onNodeWithText("Apple TV+").assertIsDisplayed()
        onNodeWithText("Dan Erickson").assertIsDisplayed()
        onNodeWithText("2 · 6 episodes").assertIsDisplayed()
        onNodeWithText("Drama, Mystery").assertIsDisplayed()
        onNodeWithText("10 Primetime Emmys").assertIsDisplayed()
        onNodeWithTag("detail_board_link_IMDb").assertExists()
        onNodeWithTag("detail_board_link_TMDB").assertExists()
        onNodeWithText("95396").assertIsDisplayed()
        onNodeWithText("Mark leads a team of office workers.").assertIsDisplayed()
    }
}
