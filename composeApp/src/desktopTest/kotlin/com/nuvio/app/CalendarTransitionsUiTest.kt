package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toAwtImage
import com.nuvio.app.features.calendar.*
import com.nuvio.app.features.trakt.*
import kotlinx.coroutines.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class CalendarTransitionsUiTest {
    private val output = File("build/reports/calendar-transitions").apply { mkdirs() }
    private fun artwork(): String {
        val image = BufferedImage(180, 270, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = Color(45, 105, 145); fillRect(0, 0, 180, 270); dispose() }
        return File(output, "fixture.png").also { ImageIO.write(image, "png", it) }.toURI().toString()
    }
    private fun repo(gate: AtomicReference<CompletableDeferred<Unit>?> = AtomicReference(null)): CalendarRepository {
        val art = artwork()
        return CalendarRepository({ "isolated-transitions" }, { shows, start, days ->
            gate.get()?.await()
            CalendarBatch(movies = if (shows) emptyList() else (0 until days).map { offset ->
                val date = calendarDatePlusDays(start, offset)
                CalendarMovie(CalendarMedia("transition-$date", "movie", "Movie $date", 2027, art, art), date)
            })
        })
    }
    private fun assertCellSize(expected: androidx.compose.ui.unit.IntSize, actual: androidx.compose.ui.unit.IntSize) {
        assertTrue(kotlin.math.abs(expected.width - actual.width) <= 1 && kotlin.math.abs(expected.height - actual.height) <= 1, "Cell resized: $expected to $actual")
    }
    private fun ComposeUiTest.ready() = waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("calendar-loading").fetchSemanticsNodes().isEmpty() }
    private fun ComposeUiTest.save(name: String) { ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(output, "$name.png")) }

    @Test fun monthGeometryIsStableAcrossWeekCountsLoadingAndResize() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try { runDesktopComposeUiTest(width = 1200, height = 900) {
            assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
            val gate = AtomicReference<CompletableDeferred<Unit>?>(null)
            val repository = repo(gate)
            var width by mutableStateOf(1200.dp)
            var height by mutableStateOf(900.dp)
            setContent { AppEnvironment { Box(Modifier.size(width, height)) { CalendarScreen(repository = repository, initialDate = "2027-02-16") } } }
            onNodeWithTag("calendar-view-Month").performClick(); ready(); waitForIdle()
            val grid = onNodeWithTag("calendar-month-grid").fetchSemanticsNode().boundsInRoot
            val shelf = onNodeWithTag("calendar-selected-day").fetchSemanticsNode().boundsInRoot
            val cell = onNodeWithTag("calendar-date-2027-02-01").fetchSemanticsNode().size
            val strip = onNodeWithTag("calendar-loading-strip").fetchSemanticsNode().boundsInRoot
            assertEquals(28, calendarMonthRange("2027-02-16", 0).days)
            save("four-weeks")
            var selected = "2027-02-16"
            repeat(12) { index ->
                val hold = CompletableDeferred<Unit>()
                gate.set(hold)
                selected = calendarMoveMonth(selected, 1)
                onNodeWithContentDescription("Next").performClick()
                waitUntil(timeoutMillis = 10_000) { repository.state.value.loading }
                onNodeWithText("No releases on this day.").assertDoesNotExist()
                assertEquals(grid, onNodeWithTag("calendar-month-grid").fetchSemanticsNode().boundsInRoot)
                assertEquals(shelf, onNodeWithTag("calendar-selected-day").fetchSemanticsNode().boundsInRoot)
                assertEquals(strip, onNodeWithTag("calendar-loading-strip").fetchSemanticsNode().boundsInRoot)
                assertCellSize(cell, onNodeWithTag("calendar-date-${selected.take(7)}-01").fetchSemanticsNode().size)
                if (index == 0) save("five-weeks-loading")
                gate.set(null); hold.complete(Unit); ready(); waitForIdle()
                assertEquals(grid, onNodeWithTag("calendar-month-grid").fetchSemanticsNode().boundsInRoot)
                assertEquals(shelf, onNodeWithTag("calendar-selected-day").fetchSemanticsNode().boundsInRoot)
                assertCellSize(cell, onNodeWithTag("calendar-date-${selected.take(7)}-01").fetchSemanticsNode().size)
                if (index == 0) save("five-weeks")
                if (selected == "2027-05-16") save("six-weeks")
            }
            assertEquals("2028-02-16", selected)
            onNodeWithTag("calendar-date-2028-02-29").assertExists()
            onNodeWithTag("calendar-date-2028-03-01").assertDoesNotExist()
            save("leap-february")
            runOnIdle { width = 1000.dp; height = 760.dp }
            waitForIdle()
            val resized = onNodeWithTag("calendar-month-grid").fetchSemanticsNode().boundsInRoot
            assertNotEquals(grid, resized)
            onNodeWithContentDescription("Next").performClick(); ready(); waitForIdle()
            assertEquals(resized, onNodeWithTag("calendar-month-grid").fetchSemanticsNode().boundsInRoot)
            save("resized")
        } } finally { Locale.setDefault(originalLocale) }
    }

    @Test fun loadingDoesNotMoveAnyViewOrRemoveExistingArtwork() = runDesktopComposeUiTest(width = 1200, height = 900) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val gate = AtomicReference<CompletableDeferred<Unit>?>(null)
        val repository = repo(gate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            setContent { AppEnvironment { CalendarScreen(repository = repository, initialDate = "2027-02-16") } }
            for (view in listOf("Month", "Week", "Day")) {
                onNodeWithTag("calendar-view-$view").performClick(); ready(); waitForIdle()
                val container = if (view == "Month") "calendar-month-grid" else "calendar-agenda"
                val bounds = onNodeWithTag(container).fetchSemanticsNode().boundsInRoot
                val tag = "calendar-card-movie:transition-2027-02-16:2027-02-16"
                onNodeWithTag(tag).assertIsDisplayed()
                val before = onNodeWithTag(tag).captureToImage().toAwtImage()
                val hold = CompletableDeferred<Unit>(); gate.set(hold)
                runOnIdle { scope.launch { repository.load(28, true, calendarWeekStart("2027-02-16", CalendarPlatform.firstWeekday())) } }
                waitUntil(timeoutMillis = 10_000) { repository.state.value.loading }
                assertEquals(bounds, onNodeWithTag(container).fetchSemanticsNode().boundsInRoot)
                onNodeWithTag(tag).assertIsDisplayed()
                val during = onNodeWithTag(tag).captureToImage().toAwtImage()
                assertEquals(before.width, during.width); assertEquals(before.height, during.height)
                assertContentEquals(before.getRGB(0, 0, before.width, before.height, null, 0, before.width), during.getRGB(0, 0, during.width, during.height, null, 0, during.width))
                save("${view.lowercase()}-refresh")
                gate.set(null); hold.complete(Unit); ready(); waitForIdle()
                assertEquals(bounds, onNodeWithTag(container).fetchSemanticsNode().boundsInRoot)
            }
        } finally { scope.cancel() }
    }

    @Test fun swipeFramesKeepGridAndShelfFixedForCancelAndCommit() = runDesktopComposeUiTest(width = 1200, height = 900) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val repository = repo()
        setContent { AppEnvironment { CalendarScreen(repository = repository, initialDate = "2027-02-16") } }
        onNodeWithTag("calendar-view-Month").performClick(); ready(); waitForIdle()
        val grid = onNodeWithTag("calendar-month-grid")
        val bounds = grid.fetchSemanticsNode().boundsInRoot
        val shelf = onNodeWithTag("calendar-selected-day").fetchSemanticsNode().boundsInRoot
        val cellSize = onNodeWithTag("calendar-date-2027-02-16").fetchSemanticsNode().size
        var frame = 0
        fun frame() {
            assertEquals(bounds, grid.fetchSemanticsNode().boundsInRoot)
            assertEquals(shelf, onNodeWithTag("calendar-selected-day").fetchSemanticsNode().boundsInRoot)
            save("swipe-${(frame++).toString().padStart(3, '0')}")
        }
        frame()
        grid.performTouchInput { down(center); moveTo(center.copy(x = center.x - width * .04f), delayMillis = 120) }
        assertEquals(cellSize, onNodeWithTag("calendar-date-2027-02-16").fetchSemanticsNode().size)
        frame()
        mainClock.autoAdvance = false
        grid.performTouchInput { up() }
        repeat(8) { mainClock.advanceTimeBy(40); frame() }
        mainClock.autoAdvance = true; waitForIdle()
        onNodeWithText(CalendarPlatform.monthLabel("2027-02-16") + " ▾").assertExists()
        grid.performTouchInput { down(center); moveTo(center.copy(x = center.x - width * .18f), delayMillis = 120) }
        frame()
        mainClock.autoAdvance = false
        grid.performTouchInput { up() }
        repeat(8) { mainClock.advanceTimeBy(40); frame() }
        mainClock.autoAdvance = true; ready(); waitForIdle()
        onNodeWithText(CalendarPlatform.monthLabel("2027-03-16") + " ▾").assertExists()
        assertEquals(cellSize, onNodeWithTag("calendar-date-2027-03-16").fetchSemanticsNode().size)
        frame()
    }
}
