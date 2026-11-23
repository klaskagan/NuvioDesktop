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
        // The audience cards live behind the board's Your stats tab.
        onNodeWithTag("detail_board").assertIsDisplayed()
        onAllNodesWithText("Season 1 by the numbers").assertCountEquals(0)
        onNodeWithText("Your stats").performClick()
        waitForIdle()
        list.performScrollToNode(hasText("Season 1 by the numbers"))
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("E8 had 80% as many Trakt watchers as E1").fetchSemanticsNodes().isNotEmpty()
        }
        list.performScrollToNode(hasText("Ratings by episode"))
        onNodeWithText("Ratings by episode").assertIsDisplayed()
        onNodeWithText("Whole show · 90,000 plays · 10,000 Trakt watchers · 9.0 plays per watcher").assertExists()
        onNodeWithContentDescription("S1E2 · 8.2 out of 10", useUnmergedTree = true).performClick()
        list.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, 560f) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Fingerprint · E2").fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
        val dir = File("build/reports/series-audience").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "details-wide.png"))
        onNodeWithText("S14").assertIsDisplayed()
        onNodeWithText("E65").assertIsDisplayed()
        onNodeWithText("E2").assertDoesNotExist()
        onNodeWithText("S1E2 · 94.0% of E1 · 9,400 watchers").assertIsDisplayed()
        onNodeWithContentDescription("S2E160 · 8.5 out of 10", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Best · S1E8 8.8    Worst · S1E1 8.1").assertIsDisplayed()
        list.performScrollToIndex(0)
        list.performScrollToNode(hasText("Ratings by episode"))
        onNodeWithText("E8 had 80% as many Trakt watchers as E1").assertExists()
        onNodeWithText("Fingerprint · E2").assertExists()
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
     * stats holds the progress card, Cast & crew holds the networks, and all of it goes with the
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

            // Your stats holds the progress card; its season action returns to Episodes.
            onNodeWithText("Your stats").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Your progress").fetchSemanticsNodes().isNotEmpty() }
            onAllNodesWithText("Second season premiere").assertCountEquals(0)
            onAllNodesWithText("First episode").assertCountEquals(1)
            val openSeason = onNode(SemanticsMatcher("opens season one") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open Season 1"
            })
            openSeason.performSemanticsAction(SemanticsActions.OnClick) { activate -> activate() }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("First episode").fetchSemanticsNodes().size == 2 }

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
