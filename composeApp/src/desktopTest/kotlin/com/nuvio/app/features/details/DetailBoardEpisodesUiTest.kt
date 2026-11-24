package com.nuvio.app.features.details

import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.streams.StreamAutoPlayPolicy
import com.nuvio.app.features.watched.WatchedRepository
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** The board's Episodes tab: season pills, the Row / List switch, and an episode click's Quick pick. */
@OptIn(ExperimentalTestApi::class)
class DetailBoardEpisodesUiTest {
    private val fixture = MetaDetails(
        "board-episodes-fixture",
        "series",
        "Board episodes fixture",
        videos = listOf(
            MetaVideo("board-episodes-fixture:1:1", "First episode", "2024-03-01", season = 1, episode = 1),
            MetaVideo("board-episodes-fixture:1:2", "Second episode", "2024-03-08", season = 1, episode = 2),
            MetaVideo("board-episodes-fixture:2:1", "Second season premiere", "2025-03-01", season = 2, episode = 1),
            MetaVideo("board-episodes-fixture:0:1", "A special", "2024-12-24", season = 0, episode = 1),
        ),
    )

    @Test
    fun seasonPillsSwitchSeasonsAndAnEpisodeFollowsTheResumeRules() = runDesktopComposeUiTest(width = 1360, height = 900) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        MetaScreenSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.ensureLoaded()
        val previousStyle = MetaScreenSettingsRepository.uiState.value.episodeCardStyle
        MetaDetailsRepository.clear()
        seedDetails(fixture)
        val played = mutableListOf<String>()
        val chosen = mutableListOf<String>()
        try {
            runOnIdle { MetaScreenSettingsRepository.setEpisodeCardStyle(MetaEpisodeCardStyle.Horizontal) }
            setContent {
                AppEnvironment {
                    MetaDetailsScreen(
                        "series",
                        fixture.id,
                        onBack = {},
                        onPlay = { _, videoId, _, _, _, _, _, _, _, _, _, _, _, _ -> played += videoId },
                        onPlayManually = { _, videoId, _, _, _, _, _, _, _, _, _, _, _, _ -> chosen += videoId },
                    )
                }
            }
            waitUntil(timeoutMillis = 10_000) { WatchedRepository.uiState.value.isLoaded }
            mainClock.advanceTimeBy(1_000)
            waitForIdle()

            // Pills in place of the old "Seasons" heading; Specials goes last.
            onNodeWithText("Seasons").assertDoesNotExist()
            val s1 = onNodeWithTag("detail_board_season_1").getUnclippedBoundsInRoot()
            val specials = onNodeWithTag("detail_board_season_0").getUnclippedBoundsInRoot()
            assertTrue(specials.left > s1.left)
            onNodeWithText("  0/2", useUnmergedTree = true).assertExists()

            onNodeWithTag("detail_board_season_2").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Second season premiere").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("detail_board_season_1").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Second episode").fetchSemanticsNodes().isNotEmpty() }

            // Row / List switches the card style.
            onNodeWithTag("detail_board_card_style_List").performClick()
            waitForIdle()
            assertEquals(MetaEpisodeCardStyle.List, MetaScreenSettingsRepository.uiState.value.episodeCardStyle)

            // An episode click: autoplay on plays it; off opens Quick pick beside the card.
            onAllNodesWithText("Second episode")[0].performClick()
            waitForIdle()
            if (StreamAutoPlayPolicy.isEffectivelyEnabled(PlayerSettingsRepository.uiState.value)) {
                assertEquals(1, played.size)
            } else {
                onNodeWithTag("stream_quick_pick").assertExists()
                onNodeWithText("S1 E2 · Second episode", substring = true, useUnmergedTree = true).assertExists()
                assertTrue(played.isEmpty())
                onNodeWithTag("stream_quick_pick_all").performClick()
                waitForIdle()
                assertEquals(listOf("board-episodes-fixture:1:2"), chosen)
            }
        } finally {
            MetaScreenSettingsRepository.setEpisodeCardStyle(previousStyle)
            MetaDetailsRepository.clear()
        }
    }

    private fun seedDetails(meta: MetaDetails) {
        val field = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = field.get(MetaDetailsRepository) as MutableMap<String, Any>
        val constructor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry")
            .declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
        cache["${meta.type}:${meta.id}"] = constructor.newInstance(meta, null, null)
    }
}
