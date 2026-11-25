package com.nuvio.app.features.details

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.trakt.TraktAudienceRepository
import com.nuvio.app.features.trakt.TraktAudienceEpisode
import com.nuvio.app.features.trakt.TraktAudienceSeason
import com.nuvio.app.features.trakt.TraktAudienceStats
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SeriesAudienceDetailsUiTest {
    @Test
    fun boardShowsProgressTileAndStatsTab() = checkBoard(grouped = false)

    @Test
    fun insertsProgressAfterEpisodesOnNarrowList() = checkDetails(MetaEpisodeCardStyle.List, 850)

    /** The board has its own tabs, so the older tab grouping of sections does not change it. */
    @Test
    fun boardIgnoresTheOlderTabGrouping() = checkBoard(grouped = true)

    @Test
    fun rendersAudienceCardsAndLinksTheSelectedEpisode() = runDesktopComposeUiTest(width = 1360, height = 1050) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        assumeTrue(TraktAudienceRepository.available)
        val meta = MetaDetails(
            "trakt:900001",
            "series",
            "Audience preview",
            videos = (1..8).map { episode ->
                MetaVideo("preview:1:$episode", "Episode $episode", "2025-01-01", season = 1, episode = episode)
            },
        )
        seedDetails(meta)
        seedAudience()
        setContent { AppEnvironment { MetaDetailsScreen("series", meta.id, onBack = {}) } }
        val list = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).onFirst()
        // The audience tiles live behind the board's Your stats tab.
        onNodeWithTag("detail_board").assertIsDisplayed()
        onAllNodesWithTag("detail_stats_retention").assertCountEquals(0)
        onNodeWithText("Your stats").performClick()
        waitForIdle()
        list.performScrollToNode(hasTestTag("detail_stats_retention"))
        val summary = "E8 had 80% as many Trakt watchers as E1 (8,000 of 10,000). Biggest drop: E1 → E2, 6.0 points."
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(summary).fetchSemanticsNodes().isNotEmpty() }
        // The design's grid: Retention with its total, then Ratings and the personal tiles beside it.
        onNodeWithText("80%").assertExists()
        onNodeWithText("SEASON 1 · 80,200 PLAYS · 10,000 TRAKT WATCHERS").assertExists()
        onNodeWithText("Relative to this season").assertExists()
        onNodeWithText("Ratings by episode").assertIsDisplayed()
        onNodeWithText("Your Audience preview").assertExists()
        val retention = onNodeWithTag("detail_stats_retention").fetchSemanticsNode().boundsInRoot
        val ratingsTile = onNodeWithTag("detail_stats_ratings").fetchSemanticsNode().boundsInRoot
        val lastRight = (onAllNodesWithTag("detail_stats_next").fetchSemanticsNodes().firstOrNull()
            ?: onNodeWithTag("detail_stats_you").fetchSemanticsNode()).boundsInRoot
        assertTrue(ratingsTile.left > retention.right && retention.width > ratingsTile.width, "Retention is the wider left tile")
        assertTrue(kotlin.math.abs(ratingsTile.top - retention.top) < 1f, "Both columns start together")
        assertTrue(kotlin.math.abs(lastRight.bottom - retention.bottom) < 1f, "The right column ends flush with Retention")
        onNodeWithContentDescription("S1E2 · 8.2 out of 10", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Fingerprint · E2").fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
        val dir = File("build/reports/series-audience").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "details-wide.png"))
        // Ratings keeps only the rows in view (the S1 row, not all 31 seasons).
        onNode(hasText("S1") and hasAnyAncestor(hasTestTag("detail_stats_ratings"))).assertExists()
        onNodeWithText("S31").assertDoesNotExist()
        onNodeWithText("E65").assertExists()
        onNodeWithContentDescription("S1E2 · 94.0% of E1 · 9,400 watchers", substring = true).assertExists()
        onNodeWithContentDescription("S2E160 · 8.5 out of 10", useUnmergedTree = true).assertDoesNotExist()
        // Wheel-scrolling Ratings reaches the last season without moving the page under it.
        val pageBefore = onNodeWithTag("detail_stats_retention").fetchSemanticsNode().boundsInRoot.top
        repeat(12) {
            onNodeWithTag("detail_stats_ratings_grid").performMouseInput { moveTo(center); scroll(20f) }
            waitForIdle()
        }
        onNode(hasText("S31") and hasAnyAncestor(hasTestTag("detail_stats_ratings"))).assertExists()
        assertEquals(pageBefore, onNodeWithTag("detail_stats_retention").fetchSemanticsNode().boundsInRoot.top)
        onNodeWithText("Best S1 E8 · 8.8   Lowest S1 E1 · 8.1").assertExists()
        MetaDetailsRepository.clear()
    }

    private fun seedAudience() {
        val field = TraktAudienceRepository.javaClass.getDeclaredField("cache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = field.get(TraktAudienceRepository) as MutableMap<String, Any?>
        val initialEpisodes = (1..8).map { episode ->
            TraktAudienceEpisode(number = episode, title = "Episode $episode", firstAired = "2025-01-01",
                rating = 8 + episode / 10.0, votes = 250)
        }
        val remainingEpisodes = (9..65).map { episode ->
            cache["/shows/900001/seasons/1/episodes/$episode/stats"] = null
            TraktAudienceEpisode(number = episode, title = "Episode $episode")
        }
        val extraSeasons = (2..31).map { season ->
            TraktAudienceSeason(season, (1..if (season == 2) 160 else 1).map { episode ->
                TraktAudienceEpisode(number = episode, rating = 8.5, votes = 100)
            })
        }
        cache["/shows/900001/seasons?extended=episodes,full"] = listOf(TraktAudienceSeason(1, initialEpisodes + remainingEpisodes)) + extraSeasons
        cache["/shows/900001/stats"] = TraktAudienceStats(watchers = 10_000, plays = 90_000)
        (1..8).forEach { episode ->
            val watchers = listOf(10000L, 9400L, 9200L, 8900L, 8600L, 8400L, 8100L, 8000L)[episode - 1]
            cache["/shows/900001/seasons/1/episodes/$episode/stats"] =
                TraktAudienceStats(watchers, watchers + 1200, 20L - episode, 250L - 9L * episode)
        }
    }

    /**
     * Wide desktop: the progress tile sits on the board and opens a season in the Episodes tab, Your
     * stats holds the Your <title> tile, Cast & crew holds the networks, and all of it goes with the
     * Episodes section.
     */
    private fun checkBoard(grouped: Boolean) = runDesktopComposeUiTest(width = 1280, height = 900) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        MetaScreenSettingsRepository.ensureLoaded()
        val previous = MetaScreenSettingsRepository.uiState.value
        val fixture = MetaDetails(
            "audience-details-fixture",
            "series",
            "Audience fixture",
            videos = listOf(
                MetaVideo("audience-details-fixture:1:1", "First episode", "2024-03-01", season = 1, episode = 1),
                MetaVideo("audience-details-fixture:2:1", "Second season premiere", "2025-03-01", season = 2, episode = 1),
            ),
            networks = listOf(MetaCompany("Fixture Network")),
        )
        MetaDetailsRepository.clear()
        seedDetails(fixture)
        try {
            MetaScreenSettingsRepository.applyFromSync(
                items = previous.items.map { item ->
                    val visible = item.key == MetaScreenSectionKey.EPISODES || item.key == MetaScreenSectionKey.PRODUCTION
                    item.copy(enabled = visible, tabGroup = 1.takeIf { grouped && visible })
                },
                cinematicBackground = false,
                tabLayout = grouped,
                episodeCardStyle = MetaEpisodeCardStyle.Horizontal,
            )
            setContent { AppEnvironment { MetaDetailsScreen("series", fixture.id, onBack = {}) } }
            waitUntil(timeoutMillis = 10_000) { WatchedRepository.uiState.value.isLoaded }
            onNodeWithTag("detail_board_progress").assertIsDisplayed()
            // Once on the Up next tile, once as the episode card.
            onAllNodesWithText("First episode").assertCountEquals(2)

            // A season row on the tile opens that season's episodes.
            onNodeWithContentDescription("S2 0/1").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Second season premiere").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Second season premiere").assertIsDisplayed()

            // Your stats holds the personal tiles: episodes watched across the show.
            onNodeWithText("Your stats").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("detail_stats_you").fetchSemanticsNodes().isNotEmpty() }
            onNode(hasText("0 of 2") and hasAnyAncestor(hasTestTag("detail_stats_you"))).assertExists()
            onAllNodesWithText("Second season premiere").assertCountEquals(0)

            onNodeWithText("Cast & crew").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Fixture Network").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Fixture Network").assertIsDisplayed()

            runOnIdle { MetaScreenSettingsRepository.setEnabled(MetaScreenSectionKey.EPISODES, false) }
            waitForIdle()
            onNodeWithTag("detail_board_progress").assertDoesNotExist()
            onNodeWithText("Your stats").assertDoesNotExist()
            onNodeWithText("Fixture Network").assertIsDisplayed()
        } finally {
            MetaScreenSettingsRepository.applyFromSync(
                items = previous.items,
                cinematicBackground = false,
                heroTrailerPlayback = previous.heroTrailerPlayback,
                tabLayout = previous.tabLayout,
                episodeCardStyle = previous.episodeCardStyle,
                blurUnwatchedEpisodes = previous.blurUnwatchedEpisodes,
                backgroundMode = previous.backgroundMode,
                posterTransitionEnabled = previous.posterTransitionEnabled,
                showOverallRatings = previous.showOverallRatings,
                episodeRatingsVisibility = previous.episodeRatingsVisibility,
            )
            MetaDetailsRepository.clear()
        }
    }

    private fun checkDetails(style: MetaEpisodeCardStyle, width: Int, grouped: Boolean = false) =
        runDesktopComposeUiTest(width = width, height = 900) {
            check(System.getProperty("nuvio.test.isolatedHome") == "true")
            MetaScreenSettingsRepository.ensureLoaded()
            val previous = MetaScreenSettingsRepository.uiState.value
            val fixture = MetaDetails(
                "audience-details-fixture",
                "series",
                "Audience fixture",
                videos = listOf(
                    MetaVideo("audience-details-fixture:1:1", "First episode", "2024-03-01", season = 1, episode = 1),
                    MetaVideo("audience-details-fixture:2:1", "Second season premiere", "2025-03-01", season = 2, episode = 1),
                ),
                networks = listOf(MetaCompany("Fixture Network")),
            )
            MetaDetailsRepository.clear()
            seedDetails(fixture)
            try {
                MetaScreenSettingsRepository.applyFromSync(
                    items = previous.items.map { item ->
                        val visible = item.key == MetaScreenSectionKey.EPISODES || item.key == MetaScreenSectionKey.PRODUCTION
                        item.copy(enabled = visible, tabGroup = 1.takeIf { grouped && visible })
                    },
                    cinematicBackground = false,
                    tabLayout = grouped,
                    episodeCardStyle = style,
                )
                setContent { AppEnvironment { MetaDetailsScreen("series", fixture.id, onBack = {}) } }
                waitUntil(timeoutMillis = 10_000) { WatchedRepository.uiState.value.isLoaded }
                val list = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).onFirst()
                list.performScrollToNode(hasText("Your progress"))
                onNodeWithText("Your progress").assertIsDisplayed()
                val openSeason = onNode(SemanticsMatcher("opens season two") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open Season 2"
                })
                openSeason.performSemanticsAction(SemanticsActions.OnClick) { activate -> activate() }
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText("Second season premiere").fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText("Second season premiere").assertIsDisplayed()

                if (!grouped) {
                    list.performScrollToNode(hasText("Fixture Network"))
                    onNodeWithText("Fixture Network").assertIsDisplayed()
                }
                runOnIdle { MetaScreenSettingsRepository.setEnabled(MetaScreenSectionKey.EPISODES, false) }
                onNodeWithText("Your progress").assertDoesNotExist()
            } finally {
                MetaScreenSettingsRepository.applyFromSync(
                    items = previous.items,
                    cinematicBackground = false,
                    heroTrailerPlayback = previous.heroTrailerPlayback,
                    tabLayout = previous.tabLayout,
                    episodeCardStyle = previous.episodeCardStyle,
                    blurUnwatchedEpisodes = previous.blurUnwatchedEpisodes,
                    backgroundMode = previous.backgroundMode,
                    posterTransitionEnabled = previous.posterTransitionEnabled,
                    showOverallRatings = previous.showOverallRatings,
                    episodeRatingsVisibility = previous.episodeRatingsVisibility,
                )
                MetaDetailsRepository.clear()
            }
        }

    private fun seedDetails(meta: MetaDetails) {
        val field = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = field.get(MetaDetailsRepository) as MutableMap<String, Any>
        val constructor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry")
            .declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
        cache["series:${meta.id}"] = constructor.newInstance(meta, null, null)
    }
}
