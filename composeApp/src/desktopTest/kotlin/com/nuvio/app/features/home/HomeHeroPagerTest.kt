package com.nuvio.app.features.home

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.home.components.HomeHeroSection
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.math.abs

class HomeHeroPagerTest {
    @get:Rule
    val compose = createComposeRule()

    private val items = List(8) { index ->
        MetaPreview(id = "hero-$index", type = "movie", name = "Hero $index")
    }

    @Test
    fun swipingAcrossEitherEndSelectsTheFirstAndLastTitles() {
        showHero()

        swipeRight()
        assertSettledHero(expectedIndex = 7)

        compose.onNodeWithTag("hero").performTouchInput {
            swipe(Offset(width * 0.7f, height * 0.3f), Offset(width * 0.3f, height * 0.3f))
        }
        assertSettledHero(expectedIndex = 0)
    }

    @Test
    fun automaticCyclingWrapsFromLastToFirst() {
        showHero()
        swipeRight()
        assertSettledHero(expectedIndex = 7)

        compose.mainClock.advanceTimeBy(6_000L)
        assertSettledHero(expectedIndex = 0)
    }

    @Test
    fun activePillFillsUntilAutoAdvance() {
        showHero()
        assertIndicatorProgressNear(expected = 0f, tolerance = 0.04f)

        compose.mainClock.advanceTimeBy(4_000L)
        assertIndicatorProgressNear(expected = 0.5f, tolerance = 0.04f)

        compose.mainClock.advanceTimeBy(3_800L)
        assertTrue(indicatorProgress() >= 0.95f)

        advanceUntilIndicatorProgressResets()
        compose.onNodeWithText("Hero 1").assertIsDisplayed()
        assertIndicatorProgressNear(expected = 0f, tolerance = 0.03f)
    }

    @Test
    fun swipingToAnotherPageResetsIndicatorProgress() {
        showHero()
        compose.mainClock.advanceTimeBy(4_000L)
        assertIndicatorProgressNear(expected = 0.5f, tolerance = 0.04f)

        swipeRight()
        advanceUntilIndicatorProgressResets()

        compose.onNodeWithText("Hero 7").assertIsDisplayed()
        assertIndicatorProgressNear(expected = 0f, tolerance = 0.03f)
    }

    @Test
    fun holdingHeroPausesCountdownUntilRelease() {
        showHero()
        compose.mainClock.advanceTimeBy(2_000L)
        assertIndicatorProgressNear(expected = 0.25f, tolerance = 0.04f)
        val pausedProgress = indicatorProgress()

        compose.onNodeWithTag("hero").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(10_000L)
        assertIndicatorProgressNear(expected = pausedProgress, tolerance = 0.03f)
        compose.onNodeWithText("Hero 0").assertIsDisplayed()

        compose.onNodeWithTag("hero").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(4_000L)
        assertIndicatorProgressNear(expected = 0.75f, tolerance = 0.05f)

        advanceUntilIndicatorProgressResets()
        compose.onNodeWithText("Hero 1").assertIsDisplayed()
        assertIndicatorProgressNear(expected = 0f, tolerance = 0.03f)
    }

    @Test
    fun verticalMoveKeepsHeroCountdownPausedUntilRelease() {
        showHero()
        compose.mainClock.advanceTimeBy(2_000L)
        val pausedProgress = indicatorProgress()

        compose.onNodeWithTag("hero").performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
        }
        compose.mainClock.advanceTimeBy(10_000L)
        assertIndicatorProgressNear(expected = pausedProgress, tolerance = 0.03f)
        compose.onNodeWithText("Hero 0").assertIsDisplayed()

        compose.onNodeWithTag("hero").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(4_000L)
        assertIndicatorProgressNear(expected = 0.75f, tolerance = 0.05f)
    }

    private fun showHero() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                HomeHeroSection(
                    items = items,
                    modifier = Modifier.width(960.dp).testTag("hero"),
                )
            }
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithText("Hero 0").assertIsDisplayed()
    }

    private fun swipeRight() {
        compose.onNodeWithTag("hero").performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.3f), Offset(width * 0.7f, height * 0.3f))
        }
    }

    private fun assertSettledHero(expectedIndex: Int) {
        val intermediateTitle = (1..6)
            .map { hasText("Hero $it") }
            .reduce { matcher, next -> matcher or next }
        compose.mainClock.advanceTimeBy(1_600L)
        compose.onAllNodes(intermediateTitle).assertCountEquals(0)
        compose.onNodeWithText("Hero $expectedIndex").assertIsDisplayed()
    }

    private fun indicatorProgress(): Float = compose
        .onNodeWithTag("hero-indicator-progress")
        .fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo]
        .current

    private fun assertIndicatorProgressNear(expected: Float, tolerance: Float) {
        val actual = indicatorProgress()
        assertTrue(
            abs(actual - expected) <= tolerance,
            "Expected indicator progress $expected ± $tolerance, but was $actual",
        )
    }

    private fun advanceUntilIndicatorProgressResets() {
        repeat(300) {
            compose.mainClock.advanceTimeBy(16L)
            if (indicatorProgress() <= 0.03f) return
        }
        assertTrue(indicatorProgress() <= 0.03f, "Indicator progress did not reset after the page changed")
    }
}
