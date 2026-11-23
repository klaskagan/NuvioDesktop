package com.nuvio.app.features.streams

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Desktop stream cards open their actions on right-click and on mouse press-and-hold (which
 * `combinedClickable` ignores on desktop); a hold doesn't also play the stream. */
@OptIn(ExperimentalTestApi::class)
class StreamCardActionsUiTest {
    private fun ComposeUiTest.card(onClick: () -> Unit = {}, onSecondary: (Offset) -> Unit) = setContent {
        AppEnvironment {
            StreamCard(
                stream = StreamItem(name = "Fixture 1080p", url = "https://invalid.test/a.mkv", addonName = "Fixture", addonId = "addon:fixture"),
                enabled = true,
                appendInstantServiceToDefaultName = false,
                showFileSizeBadges = false,
                showAddonLogo = false,
                badgePlacement = StreamBadgePlacement.BOTTOM,
                onClick = onClick,
                onSecondaryClick = onSecondary,
                modifier = Modifier.width(600.dp).testTag("card"),
            )
        }
    }

    @Test
    fun rightClickOpensActions() = runDesktopComposeUiTest(width = 800, height = 400) {
        var at by mutableStateOf<Offset?>(null)
        card { at = it }
        onNodeWithTag("card").performMouseInput { rightClick(center) }
        runOnIdle { assertNotNull(at) }
    }

    @Test
    fun pressAndHoldOpensActions() = runDesktopComposeUiTest(width = 800, height = 400) {
        var at by mutableStateOf<Offset?>(null)
        var clicks by mutableStateOf(0)
        card(onClick = { clicks++ }) { at = it }
        onNodeWithTag("card").performMouseInput { moveTo(center); press(); advanceEventTime(1_000); release() }
        runOnIdle {
            assertNotNull(at)
            assertEquals(0, clicks, "a hold must not also play the stream")
        }
    }

    @Test
    fun shortClickStillPlays() = runDesktopComposeUiTest(width = 800, height = 400) {
        var at by mutableStateOf<Offset?>(null)
        var clicks by mutableStateOf(0)
        card(onClick = { clicks++ }) { at = it }
        onNodeWithTag("card").performMouseInput { click(center) }
        runOnIdle {
            assertEquals(1, clicks)
            assertNull(at)
        }
    }
}
