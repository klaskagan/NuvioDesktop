package com.nuvio.app.features.details

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.DetailTopBarState
import com.nuvio.app.LocalDetailTopBarState
import com.nuvio.app.core.ui.DesktopMergedTopBarHeight
import com.nuvio.app.features.watched.WatchedRepository
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Once the board's tabs row pins under the top bar it stays pinned, flush under the bar, through
 * every tab: Back, the logo and Play never drop out. A short tab (Your stats) after a long one
 * (Episodes) used to let the list fall back onto the board for a frame, which unpinned the row.
 */
@OptIn(ExperimentalTestApi::class)
class DetailBoardPinnedTabsUiTest {
    @Test
    fun pinnedRowStaysPinnedAcrossTabs() = runDesktopComposeUiTest(width = 1280, height = 900) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        MetaScreenSettingsRepository.ensureLoaded()
        val previous = MetaScreenSettingsRepository.uiState.value
        val fixture = MetaDetails(
            "pinned-tabs-fixture",
            "series",
            "Pinned fixture",
            videos = (1..40).map { episode ->
                MetaVideo("pinned-tabs-fixture:1:$episode", "Episode $episode", "2024-03-01", season = 1, episode = episode)
            },
        )
        MetaDetailsRepository.clear()
        seedDetails(fixture)
        try {
            MetaScreenSettingsRepository.applyFromSync(
                items = previous.items.map { item ->
                    item.copy(enabled = item.key == MetaScreenSectionKey.EPISODES, tabGroup = null)
                },
                cinematicBackground = false,
                tabLayout = false,
                episodeCardStyle = MetaEpisodeCardStyle.List,
            )
            setContent {
                CompositionLocalProvider(LocalDetailTopBarState provides DetailTopBarState()) {
                    AppEnvironment { MetaDetailsScreen("series", fixture.id, onBack = {}) }
                }
            }
            waitUntil(timeoutMillis = 10_000) { WatchedRepository.uiState.value.isLoaded }
            onNodeWithTag("detail_board").assertIsDisplayed()
            val list = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).onFirst()

            // Scroll well into the Episodes list, so the row pins.
            list.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, 1400f) }
            waitForIdle()
            assertPinnedFlush(list.getBoundsInRoot().top)
            // The board stays composed under the pinned row, so scrolling back up doesn't rebuild it.
            assertEquals(1, onAllNodesWithTag("detail_board", useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = true).size, "board stays composed")

            // The long/short switch the bug showed (Episodes ↔ Your stats), checked on every frame.
            for (tab in listOf("Your stats", "Episodes", "Your stats", "Episodes")) {
                onNodeWithText(tab).performClick()
                mainClock.autoAdvance = false
                repeat(12) {
                    mainClock.advanceTimeByFrame()
                    assertPinnedFlush(list.getBoundsInRoot().top)
                }
                mainClock.autoAdvance = true
                waitForIdle()
                assertPinnedFlush(list.getBoundsInRoot().top)
            }
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

    private fun androidx.compose.ui.test.ComposeUiTest.assertPinnedFlush(listTop: androidx.compose.ui.unit.Dp) {
        assertEquals(1, onAllNodesWithTag("detail_board_pinned_back").fetchSemanticsNodes().size, "pinned Back is shown")
        assertEquals(1, onAllNodesWithTag("detail_board_pinned_play").fetchSemanticsNodes().size, "pinned Play is shown")
        val tabsTop = onNodeWithTag("detail_board_tabs").getBoundsInRoot().top
        // The list starts at the merged bar's bottom edge: no strip between the bar and the row.
        assertEquals(DesktopMergedTopBarHeight.value, listTop.value, 0.5f, "list starts at the bar's bottom edge")
        assertEquals(listTop.value, tabsTop.value, 0.5f, "tabs row sits at the list's top edge, flush under the bar")
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
