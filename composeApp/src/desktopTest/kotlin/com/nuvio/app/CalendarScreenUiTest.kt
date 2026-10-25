package com.nuvio.app

import androidx.compose.runtime.CompositionLocalProvider
import com.nuvio.app.navigation.LocalHistoryGesturePhaseEvents
import com.nuvio.app.navigation.HistoryGesturePhase
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.calendar.*
import com.nuvio.app.features.trakt.*
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.settings.DesktopNavigationLayout
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class CalendarScreenUiTest {
    @Test fun viewsFiltersOrientationAndEmptyStates() = runDesktopComposeUiTest(width = 1440, height = 1100) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        PosterCardStyleRepository.setCalendarLandscapeModeEnabled(false)
        PosterCardStyleRepository.setHideLabelsEnabled(true)
        val today = LocalDate.now()
        val directory = File("build/reports/calendar-screen").apply { mkdirs() }
        fun art(name: String, w: Int, h: Int, color: Color): String {
            val file = File(directory, "$name.png")
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.createGraphics().apply {
                paint = java.awt.GradientPaint(0f, 0f, color, w.toFloat(), h.toFloat(), Color(35, 25, 65))
                fillRect(0, 0, w, h); color.also { this.color = Color.WHITE }; font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 26)
                drawString(name, 15, h / 2); dispose()
            }
            ImageIO.write(image, "png", file); return file.toURI().toString()
        }
        fun media(id: String, type: String, name: String, color: Color) = CalendarMedia(id, type, name, 2026,
            art("$name portrait", 300, 450, color), art("$name landscape", 640, 360, color))
        val series = media("screen-series", "series", "Calendar Series", Color(35, 105, 140))
        val other = media("screen-other", "series", "Another Series", Color(100, 40, 120))
        val movie = media("screen-movie", "movie", "Calendar Movie", Color(100, 90, 30))
        fun episode(show: CalendarMedia, n: Int, day: Long): CalendarEpisode {
            val time = today.plusDays(day).atTime(20, n).atZone(ZoneId.systemDefault()).toInstant()
            return CalendarEpisode("${show.id}-$n", show, 1, n, "Episode $n", time.toString(), time.toEpochMilli(), if (n == 5) "season_premiere" else null)
        }
        var empty = false
        val repo = CalendarRepository({ "isolated-screen" }, { shows, _, _ ->
            if (empty) CalendarBatch() else if (shows) CalendarBatch(episodes = listOf(episode(series, 5, 0), episode(series, 6, 0), episode(other, 1, 0), episode(series, 7, 5)))
            else CalendarBatch(movies = listOf(CalendarMovie(movie, today.toString())))
        })
        var clicked: String? = null
        setContent { AppEnvironment { CalendarScreen(repository = repo, onPosterClick = { clicked = it.id }) } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("S1 · E5–6").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("calendar-view-Week").assertIsSelected()
        onNodeWithText("This week").assertExists()
        onNodeWithTag("calendar-agenda").performScrollToIndex(1)
        onNodeWithText("Next week").assertExists()
        onNodeWithText("Today").performClick()
        waitForIdle()
        onNodeWithTag("calendar-landscape").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Portrait"))
        val first = onNodeWithTag("calendar-card-episode:screen-series:1:$today").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithTag("calendar-card-episode:screen-other:1:$today").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left == second.left)
        assertTrue(first.top != second.top)
        save("week-portrait")
        onNodeWithTag("calendar-view-Day").performClick()
        waitForIdle()
        onNodeWithText("S1 · E5–6").assertExists()
        onNodeWithText("${CalendarPlatform.dayLabel(today.plusDays(1).toString())} · 0 releases").assertDoesNotExist()
        save("day-portrait")
        onNodeWithTag("calendar-filter-Movies").performClick()
        onNodeWithText("S1 · E5–6").assertDoesNotExist()
        onNodeWithText("Calendar Movie").assertExists()
        onNodeWithTag("calendar-filter-All").performClick()
        onNode(hasContentDescription("Calendar Movie") and hasClickAction()).performClick()
        assertEquals("screen-movie", clicked)
        onNodeWithTag("calendar-view-Month").performClick()
        waitForIdle()
        onNodeWithTag("calendar-date-$today").assertExists()
        onNodeWithTag("calendar-selected-day").assertIsDisplayed()
        calendarMonthRange(today.toString(), CalendarPlatform.firstWeekday()).dates.filter { !it.startsWith(today.toString().take(7)) }.forEach { outside ->
            onNodeWithTag("calendar-date-$outside").assertDoesNotExist()
        }

        onNodeWithTag("calendar-selected-day").assertExists()
        save("month-portrait")
        val tomorrow = today.plusDays(1).toString()
        onNodeWithTag("calendar-date-$tomorrow").performClick()
        onNodeWithTag("calendar-selected-day").assertIsDisplayed()
        onNodeWithText("No releases on this day.").assertExists()
        onNodeWithText(CalendarPlatform.monthLabel(tomorrow) + " ▾").performClick()
        onNodeWithTag("calendar-date-picker").assertExists()
        onNodeWithTag("calendar-picker-$today").performClick()
        onNodeWithTag("calendar-date-picker").assertDoesNotExist()
        onNodeWithTag("calendar-landscape").performClick()
        assertTrue(PosterCardStyleRepository.uiState.value.calendarLandscapeModeEnabled)
        assertTrue(PosterCardStyleRepository.uiState.value.catalogLandscapeModeEnabled)
        PosterCardStyleRepository.onProfileChanged()
        assertTrue(PosterCardStyleRepository.uiState.value.calendarLandscapeModeEnabled)
        for (view in listOf("Month", "Day", "Week")) {
            onNodeWithTag("calendar-view-$view").performClick(); waitForIdle(); save("${view.lowercase()}-landscape")
        }
        empty = true
        runOnIdle { runBlocking { repo.load(28, true, calendarWeekStart(today.toString(), CalendarPlatform.firstWeekday())) } }
        for (view in listOf("Week", "Month", "Day")) {
            onNodeWithTag("calendar-view-$view").performClick(); waitForIdle(); save("${view.lowercase()}-empty")
        }
        onNodeWithText("No releases in this date range.").assertExists()
        onAllNodes(hasTestTag("calendar-card-episode:screen-series:1:$today")).assertCountEquals(0)
        PosterCardStyleRepository.setCalendarLandscapeModeEnabled(false)
        assertTrue(PosterCardStyleRepository.uiState.value.catalogLandscapeModeEnabled)
        PosterCardStyleRepository.setHideLabelsEnabled(false)
    }
    @Test fun monthSwipeFollowsContactAndCommitsOnlyOnRelease() = runDesktopComposeUiTest(width = 1200, height = 800) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val repo = CalendarRepository({ "swipe-fixture" }, { _, _, _ -> CalendarBatch() })
        val phases = MutableSharedFlow<HistoryGesturePhase>(extraBufferCapacity = 8)
        setContent { CompositionLocalProvider(LocalHistoryGesturePhaseEvents provides phases) { AppEnvironment { CalendarScreen(repository = repo) } } }
        onNodeWithTag("calendar-view-Month").performClick()
        val today = LocalDate.now().toString()
        val heading = CalendarPlatform.monthLabel(today) + " ▾"
        onNodeWithText(heading).assertExists()
        val grid = onNodeWithTag("calendar-month-grid")
        val date = calendarMonthRange(today, CalendarPlatform.firstWeekday()).dates[10]
        val initial = onNodeWithTag("calendar-date-$date").fetchSemanticsNode().boundsInRoot.left
        grid.performTouchInput { down(center); moveTo(center.copy(x = center.x - width * .03f)) }
        onNodeWithText(heading).assertExists()
        val dragged = onNodeWithTag("calendar-date-$date").fetchSemanticsNode().boundsInRoot.left
        assertTrue(dragged < initial)
        grid.performTouchInput { up() }
        waitForIdle()
        onNodeWithText(heading).assertExists()
        grid.performTouchInput { down(center); moveTo(center.copy(x = center.x - width * .12f), delayMillis = 250) }
        onNodeWithText(heading).assertExists()
        grid.performTouchInput { up() }
        waitForIdle()
        onNodeWithText(CalendarPlatform.monthLabel(calendarMoveMonth(today, 1)) + " ▾").assertExists()
        grid.performTouchInput { swipeRight() }
        waitForIdle()
        onNodeWithText(heading).assertExists()
        onAllNodesWithText("1 " + CalendarPlatform.shortMonthLabel(today), substring = false, useUnmergedTree = true).onFirst().assertExists()
        runOnIdle { phases.tryEmit(HistoryGesturePhase.Began) }
        waitForIdle()
        grid.performMouseInput { enter(center); scroll(20f, ScrollWheel.Horizontal) }
        onNodeWithText(heading).assertExists()
        runOnIdle { phases.tryEmit(HistoryGesturePhase.Cancelled) }
        waitForIdle()
        onNodeWithText(heading).assertExists()
        runOnIdle { phases.tryEmit(HistoryGesturePhase.Began) }
        waitForIdle()
        grid.performMouseInput { scroll(20f, ScrollWheel.Horizontal) }
        onNodeWithText(heading).assertExists()
        runOnIdle { phases.tryEmit(HistoryGesturePhase.Ended) }
        waitForIdle()
        onNodeWithText(CalendarPlatform.monthLabel(calendarMoveMonth(today, 1)) + " ▾").assertExists()
        runOnIdle { phases.tryEmit(HistoryGesturePhase.Momentum) }
        grid.performMouseInput { scroll(30f, ScrollWheel.Horizontal) }
        waitForIdle()
        onNodeWithText(CalendarPlatform.monthLabel(calendarMoveMonth(today, 1)) + " ▾").assertExists()

    }

    @Test fun monthFitsSmallViewportAndSwitchingThroughWeekKeepsSelectedDate() = runDesktopComposeUiTest(width = 1200, height = 800) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCalendarLandscapeModeEnabled(false)
        val directory = File("build/reports/calendar-screen").apply { mkdirs() }
        val art = File(directory, "fit-fixture.png")
        ImageIO.write(BufferedImage(120, 180, BufferedImage.TYPE_INT_RGB), "png", art)
        val repo = CalendarRepository({ "viewport-fixture" }, { shows, start, days ->
            CalendarBatch(movies = if (shows) emptyList() else (0 until days).map { offset ->
                val date = LocalDate.parse(start).plusDays(offset.toLong()).toString()
                CalendarMovie(CalendarMedia("fit-$date", "movie", "Release $date", 2026, art.toURI().toString(), art.toURI().toString()), date)
            })
        })
        setContent { AppEnvironment { CalendarScreen(topChromePadding = 112.dp, repository = repo) } }
        onNodeWithTag("calendar-view-Month").performClick()
        val target = LocalDate.now().plusMonths(1).toString()
        val fixedControls = listOf("calendar-date-picker-button", "calendar-view-Day", "calendar-filter-All", "calendar-landscape")
        val initialControlBounds = fixedControls.associateWith { onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        onNodeWithContentDescription("Next").performClick()
        waitForIdle()
        fixedControls.forEach { tag -> assertEquals(initialControlBounds[tag], onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot, "Control shifted: $tag") }

        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Release $target").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("calendar-selected-day").assertIsDisplayed()
        onNodeWithText("Release $target").assertIsDisplayed()
        val viewport = onRoot().fetchSemanticsNode().boundsInRoot
        val shelf = onNodeWithTag("calendar-selected-day").fetchSemanticsNode().boundsInRoot
        assertTrue(shelf.bottom <= viewport.bottom + 1f)
        val cell = onNodeWithTag("calendar-date-$target").fetchSemanticsNode().boundsInRoot
        assertTrue(cell.width / cell.height < 2.2f, "Month cells stretched: $cell")
        val thumbs = onAllNodesWithTag("calendar-card-movie:fit-$target:$target", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        save("month-small-portrait")
        assertTrue(thumbs.any { it.top >= cell.top - 1 && it.bottom <= cell.bottom + 1 && it.left >= cell.left - 1 && it.right <= cell.right + 1 }, "cell=$cell cards=$thumbs")
        onNodeWithTag("calendar-landscape").performClick()
        onNodeWithText("Release $target").assertIsDisplayed()
        save("month-small-landscape")
        onNodeWithTag("calendar-view-Week").performClick()
        fixedControls.forEach { tag -> assertEquals(initialControlBounds[tag], onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot, "Control shifted switching view: $tag") }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Release $target").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("calendar-view-Day").performClick()
        fixedControls.forEach { tag -> assertEquals(initialControlBounds[tag], onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot, "Control shifted switching view: $tag") }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Release $target").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Release $target").assertIsDisplayed()
        onNodeWithTag("calendar-day-heading-$target").assertIsDisplayed()
        onNode(hasText(CalendarPlatform.dateLabel(target, "EEEE")) and hasAnyAncestor(hasTestTag("calendar-day-heading-$target"))).assertExists()
        onNode(hasText(CalendarPlatform.dateLabel(target, "MMMM")) and hasAnyAncestor(hasTestTag("calendar-day-heading-$target"))).assertExists()
        onNodeWithText("Load earlier dates").assertDoesNotExist()
        onNodeWithText("Load later dates").assertDoesNotExist()
        onNodeWithContentDescription("Next").assertDoesNotExist()
        onNodeWithContentDescription("Previous").assertDoesNotExist()
        onNodeWithTag("calendar-date-picker-button").performClick()
        onNodeWithText("Choose date").assertDoesNotExist()
        onNodeWithTag("calendar-date-picker").assertIsDisplayed()
        save("picker-aligned")
        onNodeWithTag("calendar-picker-$target").performClick()
        onNodeWithTag("calendar-view-Week").performClick()
        onNodeWithText("Today").performClick()
        waitForIdle()
        onNodeWithTag("calendar-today").assertExists()
        PosterCardStyleRepository.setCalendarLandscapeModeEnabled(false)
    }

    @Test fun emptyDayFetchesOnlyWhenScrolledWithoutLoadButtons() = runDesktopComposeUiTest(width = 1200, height = 800) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val repo = CalendarRepository({ "empty-scroll-fixture" }, { _, start, days -> requests.add("$start/$days"); CalendarBatch() })
        setContent { AppEnvironment { CalendarScreen(repository = repo) } }
        onNodeWithTag("calendar-view-Day").performClick()
        waitUntil(timeoutMillis = 10_000) { requests.isNotEmpty() }
        waitForIdle()
        onNodeWithText("Load earlier dates").assertDoesNotExist()
        onNodeWithText("Load later dates").assertDoesNotExist()
        onNodeWithText("Calendar").assertDoesNotExist()
        val before = requests.size
        val starts = requests.toSet()
        onNodeWithTag("calendar-agenda").performMouseInput { enter(center); scroll(12f, ScrollWheel.Vertical) }
        waitUntil(timeoutMillis = 10_000) { requests.size > before }
        waitForIdle()
        assertNotEquals(starts, requests.toSet())
        val after = requests.size
        waitForIdle()
        assertEquals(after, requests.size)
        onNodeWithTag("calendar-today").assertDoesNotExist()
        val browsed = calendarDatePlusDays(calendarWeekStart(LocalDate.now().toString(), CalendarPlatform.firstWeekday()), 28)
        onNodeWithText(CalendarPlatform.monthLabel(browsed) + " ▾").assertExists()
        onNodeWithTag("calendar-view-Month").performClick()
        onNodeWithTag("calendar-date-$browsed").assertIsSelected()
        onNodeWithTag("calendar-view-Day").performClick()
        val controls = onNodeWithTag("calendar-view-Day").fetchSemanticsNode().boundsInRoot
        val toggle = onNodeWithTag("calendar-landscape").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(controls.center.y - toggle.center.y) < 8f)
        assertTrue(toggle.left > controls.right)
        save("day-empty-scroll")
    }

    @Test fun dayScrollsBackwardSmoothlyAndPrependingKeepsAnchor() = runDesktopComposeUiTest(width = 1200, height = 800) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCalendarLandscapeModeEnabled(true)
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val repo = CalendarRepository({ "day-anchor-fixture" }, { shows, start, days ->
            calls.add(start)
            CalendarBatch(movies = if (shows) emptyList() else (0 until days).map { offset ->
                val date = calendarDatePlusDays(start, offset)
                CalendarMovie(CalendarMedia("anchor-$date", "movie", "Release $date", 2026), date)
            })
        })
        setContent { AppEnvironment { CalendarScreen(repository = repo) } }
        onNodeWithTag("calendar-view-Day").performClick()
        val today = LocalDate.now().toString()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("calendar-day-heading-$today").fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        onNodeWithText("Today · " + CalendarPlatform.dayLabel(today)).assertDoesNotExist()
        onNodeWithText(com.nuvio.app.core.format.formatReleaseDateForDisplay(today)).assertDoesNotExist()
        val before = onNodeWithTag("calendar-day-heading-$today").fetchSemanticsNode().boundsInRoot.top
        onNodeWithTag("calendar-agenda").performMouseInput { enter(center); scroll(-1f, ScrollWheel.Vertical) }
        waitForIdle()
        val moved = onNodeWithTag("calendar-day-heading-$today").fetchSemanticsNode().boundsInRoot.top - before
        assertTrue(moved > 0 && moved < 150, "Expected continuous scrolling; moved=$moved")
        val start = calendarWeekStart(today, CalendarPlatform.firstWeekday())
        onNodeWithTag("calendar-agenda").performScrollToIndex(0)
        val anchor = onNodeWithTag("calendar-day-heading-$start").fetchSemanticsNode().boundsInRoot.top
        val count = calls.size
        onNodeWithTag("calendar-agenda").performMouseInput { scroll(-1f, ScrollWheel.Vertical) }
        waitUntil(timeoutMillis = 15_000) { calls.size > count }
        waitForIdle()
        assertTrue(kotlin.math.abs(anchor - onNodeWithTag("calendar-day-heading-$start").fetchSemanticsNode().boundsInRoot.top) <= 1f)
        onNodeWithTag("calendar-agenda").performMouseInput { scroll(.1f, ScrollWheel.Vertical) }
        waitForIdle()
        onNodeWithTag("calendar-filter-All").performClick()
        waitForIdle()
        onNodeWithTag("calendar-day-heading-$start").assertIsDisplayed()
        save("day-prepend-anchor")
        PosterCardStyleRepository.setCalendarLandscapeModeEnabled(false)
    }

    @Test fun filterReselectKeepsDateWhenTodayHasNoReleases() = runDesktopComposeUiTest(width = 1200, height = 800) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val today = LocalDate.now().toString()
        val repo = CalendarRepository({ "filter-anchor-fixture" }, { shows, start, days ->
            CalendarBatch(movies = if (shows) emptyList() else (0 until days).map { calendarDatePlusDays(start, it) }.filter { it != today }.map { date ->
                CalendarMovie(CalendarMedia("filter-$date", "movie", "Release $date", 2026), date)
            })
        })
        setContent { AppEnvironment { CalendarScreen(repository = repo) } }
        onNodeWithTag("calendar-view-Day").performClick()
        val target = calendarDatePlusDays(today, 3)
        val start = calendarWeekStart(today, CalendarPlatform.firstWeekday())
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Release " + calendarDatePlusDays(today, 1)).fetchSemanticsNodes().isNotEmpty() }
        val index = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(target)).toInt() - 1
        onNodeWithTag("calendar-agenda").performScrollToIndex(index)
        onNodeWithTag("calendar-agenda").performMouseInput { enter(center); scroll(.1f, ScrollWheel.Vertical) }
        waitForIdle()
        onNodeWithTag("calendar-day-heading-$target").assertIsDisplayed()
        onNodeWithTag("calendar-filter-All").performClick()
        waitForIdle()
        onNodeWithTag("calendar-day-heading-$target").assertIsDisplayed()
        onNodeWithTag("calendar-today").assertDoesNotExist()
        save("day-no-today-filter")
    }

    @Test fun realCalendarCardsOpenMovieAndSeriesDetailsAndReturn() = runDesktopComposeUiTest(width = 1440, height = 1100) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        TraktAuthRepository.clearLocalState()
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.setDesktopNavigationLayout(DesktopNavigationLayout.TopBar)
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(false)
        MetaDetailsRepository.clear()
        fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
        @Suppress("UNCHECKED_CAST")
        fun seed(id: String, type: String, title: String) {
            val cache = field(MetaDetailsRepository, "cachedMetaByRequestKey") as MutableMap<String, Any>
            val ctor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry").declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
            cache["$type:$id"] = ctor.newInstance(MetaDetails(id, type, title, description = "$title detail destination", releaseInfo = "2026"), null, null)
        }
        seed("calendar-nav-show", "series", "Navigation Series")
        seed("calendar-nav-film", "movie", "Navigation Movie")
        setContent { AppEnvironment { MainAppContent(initialTab = AppScreenTab.Calendar, ownsAppRuntime = false, showLaunchOverlay = false) } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("calendar-screen").fetchSemanticsNodes().isNotEmpty() }
        val today = LocalDate.now().toString()
        val time = LocalDate.now().atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant()
        val episode = CalendarEpisode("nav-e1", CalendarMedia("calendar-nav-show", "series", "Navigation Series"), 1, 1, "Pilot", time.toString(), time.toEpochMilli())
        val movie = CalendarMovie(CalendarMedia("calendar-nav-film", "movie", "Navigation Movie", 2026), today)
        @Suppress("UNCHECKED_CAST")
        fun publishFixture() = runOnIdle {
            val session = (field(TraktCalendarRepository, "sessions") as Set<CalendarRepository>).single()
            (field(session, "mutableState") as MutableStateFlow<CalendarState>).value = CalendarState(episodes = listOf(episode), movies = listOf(movie), cards = buildCalendarCards(listOf(episode), listOf(movie), today))
        }
        publishFixture()
        onNodeWithTag("calendar-view-Day").performClick()
        for (title in listOf("Navigation Series", "Navigation Movie")) {
            onNode((hasContentDescription(title) or hasText(title)) and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("$title detail destination").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("$title detail destination").assertExists()
            onAllNodesWithContentDescription("Back").onLast().performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("calendar-screen").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("calendar-view-Day").assertIsSelected()
            publishFixture()
            waitForIdle()
        }
        save("real-navigation")
    }

    @Test fun calendarOrientationIsProfileScopedAndIndependentOfHome() {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val index = ProfileRepository.javaClass.getDeclaredField("activeProfileIndex").apply { isAccessible = true }
        val original = index.getInt(ProfileRepository)
        try {
            PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
            PosterCardStyleRepository.setCalendarLandscapeModeEnabled(true)
            index.setInt(ProfileRepository, 999)
            PosterCardStyleRepository.onProfileChanged()
            assertFalse(PosterCardStyleRepository.uiState.value.calendarLandscapeModeEnabled)
            PosterCardStyleRepository.setCalendarLandscapeModeEnabled(false)
            index.setInt(ProfileRepository, original)
            PosterCardStyleRepository.onProfileChanged()
            assertTrue(PosterCardStyleRepository.uiState.value.calendarLandscapeModeEnabled)
            assertTrue(PosterCardStyleRepository.uiState.value.catalogLandscapeModeEnabled)
            PosterCardStyleRepository.setCalendarLandscapeModeEnabled(false)
            PosterCardStyleRepository.onProfileChanged()
            assertFalse(PosterCardStyleRepository.uiState.value.calendarLandscapeModeEnabled)
            assertTrue(PosterCardStyleRepository.uiState.value.catalogLandscapeModeEnabled)
        } finally { index.setInt(ProfileRepository, original); PosterCardStyleRepository.onProfileChanged() }
    }

    private fun ComposeUiTest.save(name: String) {
        val directory = File("build/reports/calendar-screen").apply { mkdirs() }
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }
}
