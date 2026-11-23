package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.PosterZoomAnchorHolder
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The board's Resume tile: left click resumes, (i) opens Details, right click opens the options. */
class HomeBoardResumeTileTest {
    @get:Rule
    val compose = createComposeRule()

    private val item = ContinueWatchingItem(
        parentMetaId = "tt-in-the-grey",
        parentMetaType = "movie",
        videoId = "tt-in-the-grey",
        title = "In The Grey",
        subtitle = "",
        imageUrl = null,
        resumePositionMs = 60_000L,
        durationMs = 120_000L,
        progressFraction = 0.5f,
    )

    private val resumed = mutableListOf<String>()
    private val options = mutableListOf<String>()
    private val details = mutableListOf<String>()

    private fun setContent() {
        compose.setContent {
            HomeBoard(
                height = 420.dp,
                expanded = false,
                resumeItems = listOf(item),
                onResumeClick = { resumed += it.parentMetaId },
                onResumeLongPress = { options += it.parentMetaId },
                onResumeDetails = { details += it.parentMetaId },
            ) { Box(androidx.compose.ui.Modifier) }
        }
    }

    @Test
    fun infoButtonOpensDetailsWithoutResuming() {
        setContent()
        compose.onNodeWithTag("home_board_resume_details").performMouseInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("tt-in-the-grey"), details)
            assertEquals(emptyList(), resumed)
            assertEquals(emptyList(), options)
        }
    }

    @Test
    fun rightClickOnUpNextRowOpensOptions() {
        compose.setContent {
            HomeBoard(
                height = 420.dp,
                expanded = false,
                resumeItems = listOf(item.copy(parentMetaId = "first", videoId = "first"), item),
                onResumeClick = { resumed += it.parentMetaId },
                onResumeLongPress = { options += it.parentMetaId },
            ) { Box(androidx.compose.ui.Modifier) }
        }
        compose.onNodeWithTag("home_board_up_next").performMouseInput { click(center, button = MouseButton.Secondary) }
        compose.runOnIdle {
            assertEquals(listOf("tt-in-the-grey"), options)
            assertEquals(emptyList(), resumed)
            assertNotNull(PosterZoomAnchorHolder.consume())
        }
    }

    @Test
    fun rightClickOnTileOpensOptionsWithoutResuming() {
        setContent()
        compose.onNodeWithTag("home_board_resume").performMouseInput { click(button = MouseButton.Secondary) }
        compose.runOnIdle {
            assertEquals(listOf("tt-in-the-grey"), options)
            assertEquals(emptyList(), resumed)
            // The anchor is what opens the shared zoomed options menu instead of the bottom sheet.
            assertNotNull(PosterZoomAnchorHolder.consume())
        }
    }

    @Test
    fun leftClickOnTileStillResumes() {
        setContent()
        compose.onNodeWithTag("home_board_resume").performMouseInput { click(center.copy(y = bottom - 20f)) }
        compose.runOnIdle {
            assertEquals(listOf("tt-in-the-grey"), resumed)
            assertEquals(emptyList(), details)
        }
    }
}
