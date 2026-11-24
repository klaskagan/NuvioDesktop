package com.nuvio.app.features.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.details.components.BoardCreditLine
import com.nuvio.app.features.details.components.BoardCredits
import com.nuvio.app.features.details.components.BoardRateControl
import com.nuvio.app.features.tracking.TrackingFavoriteState
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRatingState
import kotlin.test.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BoardRateStripUiTest {
    private val media = TrackingMediaReference(TrackingMediaKind.SHOW, "Severance")

    /** Rate opens into the star strip; keys pick with a live label; Esc folds it back. */
    @Test fun rateOpensIntoAStripThatKeysDriveAndEscCloses() = runDesktopComposeUiTest(width = 900, height = 300) {
        val opened = mutableListOf<Boolean>()
        setContent {
            AppEnvironment {
                BoardRateControl(
                    media = media,
                    rating = TrackingRatingState(TrackingProviderId.SIMKL, null),
                    favorite = TrackingFavoriteState(TrackingProviderId.TRAKT, false),
                    onOpenChange = { opened += it },
                )
            }
        }
        onNodeWithTag("detail_board_rate_idle").performClick()
        waitForIdle()
        onNodeWithTag("detail_board_rate_strip").assertIsDisplayed()
        onNodeWithTag("detail_board_rate_favorite").assertExists()
        assertEquals(true, opened.last())
        onNodeWithTag("detail_board_rate_star_7").performClick()
        waitForIdle()
        onNodeWithTag("detail_board_rate_label", useUnmergedTree = true).assertTextEquals("7 · Good")
        onNodeWithTag("detail_board_rate").requestFocus()
        onNodeWithTag("detail_board_rate").performKeyInput { pressKey(Key.Nine) }
        waitForIdle()
        onNodeWithTag("detail_board_rate_label", useUnmergedTree = true).assertTextEquals("9 · Superb")
        onNodeWithTag("detail_board_rate").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        onNodeWithTag("detail_board_rate_label", useUnmergedTree = true).assertTextEquals("8 · Great")
        onNodeWithTag("detail_board_rate_cancel").performClick()
        waitForIdle()
        onNodeWithTag("detail_board_rate_idle").assertIsDisplayed()
        assertEquals(false, opened.last())
    }

    /**
     * Without a rating source only ♥ ✕ open, in Rate's place: the row must not collapse its other
     * buttons, or the narrow strip slides out from under the pointer and flickers open and shut.
     */
    @Test fun favoriteOnlyStripOpensInPlaceWithoutCollapsingTheRow() = runDesktopComposeUiTest(width = 900, height = 300) {
        val opened = mutableListOf<Boolean>()
        setContent {
            AppEnvironment {
                BoardRateControl(
                    media = media,
                    rating = TrackingRatingState(),
                    favorite = TrackingFavoriteState(TrackingProviderId.TRAKT, false),
                    onOpenChange = { opened += it },
                )
            }
        }
        onNodeWithTag("detail_board_rate_idle").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(500); waitForIdle()
        onNodeWithTag("detail_board_rate_strip").assertIsDisplayed()
        onNodeWithTag("detail_board_rate_favorite").assertExists()
        onNodeWithTag("detail_board_rate_star_1").assertDoesNotExist()
        kotlin.test.assertTrue(opened.none { it }, "the row collapsed for a favorite-only strip: $opened")
    }

    /** A pointer that leaves for a moment (the row shifting as it opens) doesn't close the strip; leaving for good does. */
    @Test fun hoverOpenedStripSurvivesABriefExit() = runDesktopComposeUiTest(width = 900, height = 300) {
        setContent {
            AppEnvironment {
                BoardRateControl(
                    media = media,
                    rating = TrackingRatingState(TrackingProviderId.TRAKT, null),
                    favorite = TrackingFavoriteState(TrackingProviderId.TRAKT, false),
                    onOpenChange = {},
                )
            }
        }
        val root = onRoot()
        onNodeWithTag("detail_board_rate_idle").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(500); waitForIdle()
        onNodeWithTag("detail_board_rate_strip").assertExists()
        root.performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(5f, 295f)) }
        mainClock.advanceTimeBy(150); waitForIdle()
        onNodeWithTag("detail_board_rate_strip").assertExists()
        mainClock.advanceTimeBy(800); waitForIdle()
        onNodeWithTag("detail_board_rate_strip").assertDoesNotExist()
    }

    /**
     * With the strip open beside Play and three icon-only buttons, ✕ and ✓ sit inside the title
     * column's bounds. Overflowing the column clipped their semantics bounds, which the hand cursor
     * reads, so hovering them showed an arrow.
     */
    @Test fun openStripFitsInsideTheTitleColumn() = runDesktopComposeUiTest(width = 1200, height = 300) {
        setContent {
            AppEnvironment {
                androidx.compose.foundation.layout.Column(Modifier.widthIn(max = com.nuvio.app.features.details.components.BoardTitleColumnMaxWidth)) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                        // Play (~185dp) and Library, Watched, Trailer as 44dp icons.
                        Box(Modifier.width(185.dp))
                        repeat(3) { Box(Modifier.width(44.dp)) }
                        BoardRateControl(
                            media = media,
                            rating = TrackingRatingState(TrackingProviderId.TRAKT, 8.0),
                            favorite = TrackingFavoriteState(TrackingProviderId.TRAKT, true),
                            onOpenChange = {},
                            alwaysOpen = true,
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("detail_board_rate_remove").assertIsDisplayed()
        onNodeWithTag("detail_board_rate_save").assertIsDisplayed()
        val column = onRoot().fetchSemanticsNode().boundsInWindow
        val save = onNodeWithTag("detail_board_rate_save").fetchSemanticsNode().boundsInWindow
        kotlin.test.assertTrue(save.width > 30f, "✓ is clipped: $save in $column")
    }

    /** People names wrap onto another line instead of being cut off. */
    @Test fun creditLineNamesAreNeverTruncated() = runDesktopComposeUiTest(width = 400, height = 400) {
        val long = "Alexander Skarsgård-Pettersson"
        setContent {
            AppEnvironment {
                Box(Modifier.width(260.dp)) {
                    BoardCreditLine(
                        BoardCredits(lead = listOf(MetaPerson(long, "Director")), starring = listOf(MetaPerson("Another Rather Long Name", "Lead"))),
                        isSeries = false,
                        onPerson = {},
                    )
                }
            }
        }
        val node = onNodeWithTag("detail_board_credit_line")
        node.assertTextContains(long, substring = true)
        val layout = node.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.let { get ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); get(results); results.first()
            }
        // The line wraps instead of cutting a name off.
        assertEquals(false, layout.hasVisualOverflow, "lines=${layout.lineCount}")
        kotlin.test.assertTrue(layout.lineCount >= 2)
    }

    /** Resting on a name opens its portrait card; moving off the line closes it again. */
    @Test fun creditNameShowsAPortraitCardOnHover() = runDesktopComposeUiTest(width = 900, height = 500) {
        setContent {
            AppEnvironment {
                BoardCreditLine(
                    BoardCredits(lead = listOf(MetaPerson("Dan Erickson", "Creator")), starring = listOf(MetaPerson("Adam Scott", "Mark S.", episodeCount = 19))),
                    isSeries = true,
                    onPerson = {},
                )
            }
        }
        val node = onNodeWithTag("detail_board_credit_line")
        val layout = node.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.let { get ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); get(results); results.first()
            }
        val start = layout.layoutInput.text.text.indexOf("Adam Scott")
        val box = layout.getBoundingBox(start + 3)
        onNodeWithTag("detail_board_credit_card").assertDoesNotExist()
        node.performMouseInput { moveTo(box.center) }
        mainClock.advanceTimeBy(600); waitForIdle()
        onNodeWithTag("detail_board_credit_card").assertExists()
        onNodeWithText("Mark S.").assertExists()
        onNodeWithText("19 episodes").assertExists()
        node.performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(box.center.x, box.bottom + 400f)) }
        mainClock.advanceTimeBy(600); waitForIdle()
        onNodeWithTag("detail_board_credit_card").assertDoesNotExist()
    }
}
