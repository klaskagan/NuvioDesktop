package com.nuvio.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.settings.NavBarStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A freshly mounted merged bar (Downloads, Details and Streams each mount one in their frame) shows
 * its underline under the selected destination at once; it used to sweep in from x = 0 every time.
 * A selection change within one bar still slides.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarIndicatorTest {
    @Test
    fun newBarPlacesTheUnderlineWithoutSlidingAndLaterChangesSlide() = runDesktopComposeUiTest(width = 1200, height = 300) {
        var selected by mutableStateOf("Library")
        mainClock.autoAdvance = false
        setContent {
            NuvioTheme {
                Box(Modifier.requiredSize(1200.dp, 200.dp)) {
                    DesktopMergedTopBar(
                        tabs = DesktopNavigationTabs(listOf("For you", "Movies", "Shows", "My lists"), selectedIndex = null, onSelect = {}),
                        items = listOf("Search", "Library", "Calendar", "History", "Settings").map { label ->
                            FloatingNavigationItem(label = label, selected = label == selected, onClick = {}, icon = Icons.Default.Home)
                        },
                        windowWidth = 1200.dp,
                        navBarStyle = NavBarStyle.EXPANDED,
                    )
                }
            }
        }
        // A few frames in: far less than the 280 ms slide.
        repeat(3) { mainClock.advanceTimeByFrame() }
        fun indicatorLeft() = onNodeWithTag("desktop_merged_top_bar_indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        fun entryLeft(label: String) = onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes()
            .minOf { it.boundsInRoot.left }
        val library = entryLeft("Library")
        assertTrue(abs(indicatorLeft() - library) < 40f, "underline started at ${indicatorLeft()}, Library sits at $library")

        val start = indicatorLeft()
        selected = "History"
        repeat(6) { mainClock.advanceTimeByFrame() }
        val midway = indicatorLeft()
        mainClock.advanceTimeBy(600)
        val end = indicatorLeft()
        assertTrue(end > start + 100f, "underline should end under History: $start → $end")
        assertTrue(midway > start + 1f && midway < end - 1f, "a change should slide: $start → $midway → $end")
    }
}
