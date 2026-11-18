package com.nuvio.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.calendar.CalendarAgendaToolbar
import com.nuvio.app.features.calendar.CalendarFilter
import com.nuvio.app.features.calendar.CalendarView
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import org.junit.Assume.assumeTrue

@OptIn(ExperimentalTestApi::class)
class AgendaToolbarUiTest {
    @Test fun calendarFitsNarrowWindow() = verifyToolbar(440, calendar = true)
    @Test fun calendarFitsMinimumWindow() = verifyToolbar(320, calendar = true)
    @Test fun historyFitsNarrowWindow() = verifyToolbar(440, calendar = false)
    @Test fun historyFitsMinimumWindow() = verifyToolbar(320, calendar = false)

    @Test fun historyDesktopHasFiltersWithoutDay() = runDesktopComposeUiTest(width = 1400, height = 400) {
        setContent {
            AppEnvironment {
                CalendarAgendaToolbar("2026-11-17", CalendarFilter.All, false, false, {}, {}, {}, {}, tagPrefix = "history")
            }
        }
        onNodeWithText("Day").assertDoesNotExist()
        listOf("All", "Shows", "Movies").forEach { onNodeWithTag("history-filter-$it").assertIsDisplayed() }
    }

    private fun verifyToolbar(width: Int, calendar: Boolean) = runDesktopComposeUiTest(width = width, height = 560) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        var filter by mutableStateOf(CalendarFilter.All)
        var view by mutableStateOf(CalendarView.Week)
        var landscape by mutableStateOf(false)
        var loading by mutableStateOf(false)
        var todayRequests = 0
        var pickerRequests = 0
        val prefix = if (calendar) "calendar" else "history"
        setContent {
            AppEnvironment {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        CalendarAgendaToolbar(
                            selected = "2026-11-17",
                            filter = filter,
                            landscape = landscape,
                            loading = loading,
                            openPicker = { pickerRequests++ },
                            goToday = { todayRequests++ },
                            selectFilter = { filter = it },
                            toggleOrientation = { landscape = !landscape },
                            tagPrefix = prefix,
                            view = view.takeIf { calendar },
                            selectView = { view = it },
                        )
                    }
                }
            }
        }
        val toolbar = onNodeWithTag("$prefix-toolbar").fetchSemanticsNode().boundsInRoot
        val primary = onNodeWithTag("$prefix-toolbar-primary").fetchSemanticsNode().boundsInRoot
        val secondary = onNodeWithTag("$prefix-toolbar-secondary").fetchSemanticsNode().boundsInRoot
        assertTrue(primary.bottom <= secondary.top + 1f)
        val tags = listOf("date-picker-button", "today-button", "landscape", "filter-All", "filter-Shows", "filter-Movies")
        tags.forEach { tag ->
            val bounds = onNodeWithTag("$prefix-$tag").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= toolbar.left - 1f && bounds.right <= toolbar.right + 1f, "$tag escaped the pane")
        }
        val initialReports = File("build/reports/agenda-toolbar").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(initialReports, "$prefix-$width-initial.png"))
        fun assertFilterLabelsFit() {
            listOf("All", "Shows", "Movies").forEach { label ->
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                assertFalse(layouts.single().isLineEllipsized(0), "$label was clipped at $width: size=${layouts.single().size}, width=${layouts.single().didOverflowWidth}, height=${layouts.single().didOverflowHeight}")
            }
        }
        assertFilterLabelsFit()
        onNodeWithTag("$prefix-date-picker-button").performClick()
        onNodeWithTag("$prefix-today-button").performClick()
        onNodeWithTag("$prefix-landscape").performClick()
        runOnIdle {
            assertEquals(1, pickerRequests)
            assertEquals(1, todayRequests)
            assertTrue(landscape)
        }
        onNodeWithTag("$prefix-filter-Movies").performClick().assertIsSelected()
        runOnIdle { assertEquals(CalendarFilter.Movies, filter) }
        if (calendar) {
            val picker = onNodeWithTag("calendar-view-picker")
            picker.assertIsDisplayed().performClick()
            onNodeWithText("Month").performClick()
            runOnIdle { assertEquals(CalendarView.Month, view) }
            assertFilterLabelsFit()
            picker.performClick()
            onNodeWithText("Day").performClick()
            runOnIdle { assertEquals(CalendarView.Day, view) }
        } else {
            onNodeWithText("Day").assertDoesNotExist()
            onNodeWithTag("history-view-picker").assertDoesNotExist()
        }
        runOnIdle { loading = true }
        onNodeWithTag("$prefix-loading").assertIsDisplayed()
        assertEquals(toolbar, onNodeWithTag("$prefix-toolbar").fetchSemanticsNode().boundsInRoot)
        runOnIdle { loading = false }
        val reports = File("build/reports/agenda-toolbar").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(reports, "$prefix-$width.png"))
    }
}
