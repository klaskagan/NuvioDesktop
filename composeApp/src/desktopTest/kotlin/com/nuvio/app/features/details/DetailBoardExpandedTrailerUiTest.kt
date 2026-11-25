package com.nuvio.app.features.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.details.components.DesktopHeroTrailerClock
import com.nuvio.app.features.details.components.DetailBoard
import com.nuvio.app.features.details.components.DetailExpandedTrailer
import com.nuvio.app.features.details.components.DetailFeatureTile
import com.nuvio.app.features.details.components.handleExpandedTrailerKey
import androidx.compose.ui.input.key.Key
import kotlin.test.assertEquals
import org.junit.Test

/**
 * The expanded featured tile (design frame "A · Trailer expanded"): a clip picked from the Trailers
 * tab shows its title, play/pause, Play and its times; the hero trailer expanded from the corner keeps
 * only Back, mute and collapse.
 */
@OptIn(ExperimentalTestApi::class)
class DetailBoardExpandedTrailerUiTest {
    private val meta = MetaDetails("expanded-trailer", "series", "Severance")

    @Test
    fun aPickedClipShowsItsTitleControlsAndTimes() = runDesktopComposeUiTest(width = 1400, height = 900) {
        var paused by mutableStateOf(false)
        var played = 0
        val clock = DesktopHeroTrailerClock().apply {
            positionMs = 41_000
            durationMs = 144_000
        }
        setContent {
            AppEnvironment {
                Tile(
                    DetailExpandedTrailer(
                        title = "Season 2 Official Trailer",
                        source = "Severance · YouTube",
                        clock = clock,
                        paused = paused,
                        onPauseToggle = { paused = !paused },
                        playLabel = "Play S2 E3",
                        playEnabled = true,
                        onPlay = { played++ },
                        onPlayLongClick = null,
                    ),
                )
            }
        }
        mainClock.advanceTimeBy(600)
        onNodeWithTag("detail_board_trailer_title").assertTextEquals("Season 2 Official Trailer")
        onNodeWithText("Severance · YouTube").assertExists()
        onNodeWithTag("detail_board_trailer_time", useUnmergedTree = true).assertExists()
        onNodeWithText("0:41").assertExists()
        onNodeWithText("2:24").assertExists()

        onNodeWithTag("detail_board_trailer_pause").performClick()
        assertEquals(true, paused)
        onNodeWithTag("detail_board_trailer_play").performClick()
        assertEquals(1, played)
    }

    @Test
    fun clickingTheBarSeeksTheClip() = runDesktopComposeUiTest(width = 1400, height = 900) {
        val seeks = mutableListOf<Long>()
        val clock = DesktopHeroTrailerClock().apply {
            positionMs = 10_000
            durationMs = 100_000
            seeker = { seeks += it }
        }
        setContent { AppEnvironment { Tile(trailer(clock)) } }
        mainClock.advanceTimeBy(600)
        val bar = onNodeWithTag("detail_board_trailer_seek")
        bar.performMouseInput { moveTo(percentOffset(0.75f, 0.5f)) }
        mainClock.advanceTimeBy(200)
        onNodeWithTag("detail_board_trailer_seek_time").assertIsDisplayed()
        bar.performMouseInput { click(percentOffset(0.75f, 0.5f)) }
        mainClock.advanceTimeBy(200)
        assertEquals(1, seeks.size)
        assertEquals(75_000f, seeks.single().toFloat(), 1_000f)
        assertEquals(seeks.single() / 1_000 * 1_000, clock.positionMs)
    }

    @Test
    fun aPickedClipsKeysPauseSeekAndMute() {
        val seeks = mutableListOf<Long>()
        val clock = DesktopHeroTrailerClock().apply {
            positionMs = 5_000
            durationMs = 60_000
            seeker = { seeks += it }
        }
        var pauses = 0
        var mutes = 0
        fun press(key: Key) = handleExpandedTrailerKey(key, clock, { pauses++ }, { mutes++ })

        assertEquals(true, press(Key.Spacebar))
        assertEquals(true, press(Key.K))
        assertEquals(2, pauses)
        assertEquals(true, press(Key.M))
        assertEquals(1, mutes)
        assertEquals(true, press(Key.DirectionRight))
        assertEquals(15_000L, clock.positionMs)
        assertEquals(true, press(Key.DirectionLeft))
        assertEquals(true, press(Key.DirectionLeft))
        assertEquals(listOf(15_000L, 5_000L, 0L), seeks)
        assertEquals(false, press(Key.A))

        clock.seeker = null
        assertEquals(false, press(Key.DirectionRight), "arrows pass through when the player can't seek")
    }

    @Test
    fun theHeroTrailerExpandedKeepsOnlyBackMuteAndCollapse() = runDesktopComposeUiTest(width = 1400, height = 900) {
        setContent { AppEnvironment { Tile(null) } }
        mainClock.advanceTimeBy(600)
        onNodeWithTag("detail_board_expand").assertExists()
        onNodeWithTag("detail_board_mute").assertExists()
        onNodeWithTag("detail_board_trailer_info").assertDoesNotExist()
        onNodeWithTag("detail_board_trailer_pause").assertDoesNotExist()
        onNodeWithTag("detail_board_trailer_play").assertDoesNotExist()
        onNodeWithTag("detail_board_trailer_time").assertDoesNotExist()
    }

    @Test
    fun theExpandedBoardGrowsToHomesExpandedHeight() = runDesktopComposeUiTest(width = 1400, height = 1000) {
        var expanded by mutableStateOf(false)
        setContent {
            AppEnvironment {
                DetailBoard(expanded = expanded, hasSide = false, viewportHeight = 1000.dp, side = {}) {}
            }
        }
        assertEquals(440f, onNodeWithTag("detail_board").getUnclippedBoundsInRoot().height.value, 1f)
        expanded = true
        mainClock.advanceTimeBy(1_000)
        assertEquals(850f, onNodeWithTag("detail_board").getUnclippedBoundsInRoot().height.value, 1f)
    }

    private fun trailer(clock: DesktopHeroTrailerClock) = DetailExpandedTrailer(
        title = "Season 2 Official Trailer",
        source = "Severance · YouTube",
        clock = clock,
        paused = false,
        onPauseToggle = {},
        playLabel = "Play",
        playEnabled = true,
        onPlay = {},
        onPlayLongClick = null,
    )

    @androidx.compose.runtime.Composable
    private fun Tile(trailer: DetailExpandedTrailer?) {
        Box(Modifier.fillMaxSize()) {
            DetailFeatureTile(
                meta = meta,
                expanded = true,
                trailer = { modifier, _ -> Box(modifier) },
                expandedTrailer = trailer,
                trailerReady = true,
                trailerMuted = false,
                onMuteToggle = {},
                onToggleExpanded = {},
                onBack = {},
                onArtworkLoaded = {},
                titleBlock = {},
            )
        }
    }
}
