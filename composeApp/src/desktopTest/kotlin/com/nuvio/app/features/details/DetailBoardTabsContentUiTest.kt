package com.nuvio.app.features.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.nuvio.app.features.details.components.BoardTrailers
import com.nuvio.app.features.details.components.DetailProductionSection
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

    @Test fun castTabOpensWithANetworksPillThenTheLogosThenTheFilterThenTheCast() =
        castTabOrder(MetaDetails(id = "tt1", type = "series", name = "Severance", networks = listOf(MetaCompany("Apple TV+"))), "Networks")

    @Test fun movieCastTabOpensWithAProductionPill() =
        castTabOrder(MetaDetails(id = "tt2", type = "movie", name = "Dune", productionCompanies = listOf(MetaCompany("Legendary"))), "Production")

    private fun castTabOrder(base: MetaDetails, label: String) = runDesktopComposeUiTest(width = 1300, height = 900) {
        val meta = base.copy(cast = listOf(MetaPerson("Adam Scott", "Mark"), MetaPerson("Britt Lower", "Helly"), MetaPerson("Dan Erickson", "Creator")))
        setContent {
            AppEnvironment {
                Column(Modifier.width(1200.dp)) {
                    DetailProductionSection(meta = meta, showHeader = true, boardStyle = true)
                    BoardCastGrid(cast = meta.cast, isSeries = meta.type == "series", onCastClick = null)
                }
            }
        }
        val networks = onNodeWithTag("detail_board_networks_pill").assertIsDisplayed().getBoundsInRoot()
        // The label is a pill, not a heading, and has no count.
        onNodeWithTag("detail_board_networks_pill").assertTextEquals(label)
        val logo = onNodeWithText(meta.networks.ifEmpty { meta.productionCompanies }.first().name, useUnmergedTree = true).getBoundsInRoot()
        val filter = onNodeWithTag("detail_board_cast_filter_All").assertIsDisplayed().getBoundsInRoot()
        val tile = onAllNodesWithTag("detail_board_cast_tile")[0].getBoundsInRoot()
        assertTrue(networks.bottom <= logo.top, "pill $networks above logos $logo")
        assertTrue(logo.bottom <= filter.top, "logos $logo above filter $filter")
        assertTrue(filter.bottom <= tile.top, "filter $filter above cast $tile")
        // Both pills sit on the left edge, and the old "Cast" heading is gone (only the Cast pill says it).
        assertEquals(networks.left, filter.left)
        onAllNodesWithText("Cast", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithTag("detail_board_cast_filter_Cast").assertTextContains("2", substring = true)
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
        // Text-free only is a switch pill, on by default.
        onNodeWithTag("detail_gallery_text_free").assertIsOn()
        onNodeWithTag("detail_gallery_text_free").performClick()
        onNodeWithTag("detail_gallery_text_free").assertIsOff()
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

    @Test fun trailersFilterByTypeAndRingTheOnePlaying() = runDesktopComposeUiTest(width = 1360, height = 850) {
        val videos = listOf(
            MetaTrailer("1", "k1", "Official Trailer", "YouTube", type = "Trailer"),
            MetaTrailer("2", "k2", "Teaser", "YouTube", type = "Teaser"),
            MetaTrailer("3", "k3", "Inside the episode", "YouTube", type = "Featurette"),
        )
        val played = mutableListOf<String>()
        setContent { AppEnvironment { BoardTrailers(videos, playing = videos[1], onPlay = { played += it.key }) } }
        onAllNodesWithTag("detail_trailer_card").assertCountEquals(3)
        onNodeWithTag("detail_trailer_now_playing", useUnmergedTree = true).assertExists()
        onNodeWithTag("detail_trailers_filter_BehindTheScenes").performClick()
        mainClock.advanceTimeBy(500); waitForIdle()
        onAllNodesWithTag("detail_trailer_card").assertCountEquals(1)
        onAllNodesWithTag("detail_trailer_card")[0].performClick()
        assertEquals(listOf("k3"), played)
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
        // A series keeps its status and averages its runtime; the first aired date is the full date.
        onNodeWithText("Status").assertIsDisplayed()
        onNodeWithText("18 February 2022").assertIsDisplayed()
    }

    /**
     * A film's About (design frame "Details · Behind the film"): rating, compact budget and box
     * office, no "Released" status; More names the country, language and collection; Links lists
     * the source addon; Behind the film sits between the tiles and the overview.
     */
    @Test fun movieDetailsFollowTheFilmFrame() = runDesktopComposeUiTest(width = 1300, height = 1100) {
        val meta = MetaDetails(
            "tt15239678", "movie", "Dune: Part Two", imdbId = "tt15239678",
            description = "Follow the mythic journey of Paul Atreides.",
            status = "Released", releaseInfo = "2024", releaseDate = "2024-03-01T00:00:00.000Z",
            ageRating = "PG-13", runtime = "166 min", country = "US", language = "en",
            director = listOf("Denis Villeneuve"), collectionName = "Dune",
            budgetUsd = 190_000_000L, revenueUsd = 714_844_358L, metaSource = "Cinemeta",
        )
        val behind = BehindTheFilm(
            "Q109228991", listOf(BehindTheFilmWork("Dune", "novel", "Frank Herbert", 1965)),
            emptyList(), listOf(BehindTheFilmPerson("Hans Zimmer", 947)), emptyList(), listOf("Italy", "Jordan"),
        )
        setContent {
            AppEnvironment {
                Box(Modifier.width(1200.dp)) {
                    com.nuvio.app.features.details.components.BoardDetailsFacts(meta, isSeries = false) {
                        com.nuvio.app.features.details.components.BehindTheFilmTile(behind, isSeries = false, onPerson = null)
                    }
                }
            }
        }
        mainClock.advanceTimeBy(500); waitForIdle()
        onNodeWithText("1 March 2024").assertIsDisplayed()
        // Facts sit two to a row (the design's 1fr 1fr grid): Directed by beside Released.
        val director = onNodeWithText("Denis Villeneuve").fetchSemanticsNode().boundsInRoot
        val released = onNodeWithText("1 March 2024").fetchSemanticsNode().boundsInRoot
        assertEquals(director.top, released.top)
        assertTrue(released.left > director.right)
        onNodeWithText("2 h 46 min").assertIsDisplayed()
        onNodeWithText("PG-13").assertIsDisplayed()
        onNodeWithText("$190M").assertIsDisplayed()
        onNodeWithText("Box office").assertIsDisplayed()
        onNodeWithText("$714.8M").assertIsDisplayed()
        onNodeWithText("United States").assertIsDisplayed()
        onNodeWithText("English").assertIsDisplayed()
        onNodeWithText("Collection").assertIsDisplayed()
        onNodeWithText("Cinemeta").assertIsDisplayed()
        onNodeWithText("Status").assertDoesNotExist()
        onNodeWithText("novel · Frank Herbert, 1965").assertIsDisplayed()
        onAllNodesWithTag("detail_behind_place").assertCountEquals(2)
        val behindBottom = onNodeWithTag("detail_behind_the_film").fetchSemanticsNode().boundsInRoot.bottom
        val overviewTop = onNodeWithText("Follow the mythic journey of Paul Atreides.").fetchSemanticsNode().boundsInRoot.top
        assertTrue(behindBottom < overviewTop, "Behind the film sits above the overview")
    }
}
