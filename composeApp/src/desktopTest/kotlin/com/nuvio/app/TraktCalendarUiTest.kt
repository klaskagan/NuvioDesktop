package com.nuvio.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.*
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.*
import com.nuvio.app.features.home.components.calendarReleaseLabel
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.trakt.*
import com.nuvio.app.navigation.HomescreenSettingsRoute
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
class TraktCalendarUiTest {
    @AfterTest fun clearFixtures() {
        if (System.getProperty("nuvio.test.isolatedHome") != "true") return
        TraktAuthRepository.clearLocalState()
        TraktCalendarRepository.invalidate()
        AddonRepository.clearLocalState()
        HomeCatalogSettingsStorage.savePayload("{}")
        HomeCatalogSettingsRepository.clearLocalState()
        HomeRepository.clear()
        MetaDetailsRepository.clear()
        PosterCardStyleRepository.setHideLabelsEnabled(false)
    }

    @Test fun bothOrientationsKeepLabelsAndOpenRealMovieAndSeriesDetails() = runDesktopComposeUiTest(width = 1440, height = 1000) {
        prepare()
        seedCalendar()
        val releaseLabels = TraktCalendarRepository.uiState.value.cards.map(::calendarReleaseLabel)
        seedDetails("calendar-movie", "movie", "Calendar Movie", "Calendar movie detail destination")
        seedDetails("calendar-show", "series", "Calendar Series", "Calendar series detail destination")
        setContent { AppEnvironment { MainAppContent(ownsAppRuntime = false, showLaunchOverlay = false) } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("S1 · E5–7").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("S1 · E5–7").assertIsDisplayed()
        onNodeWithText("Calendar Movie").assertIsDisplayed()
        // Compact portrait cards retain identity/episode labels, but omit the date/time line.
        releaseLabels.forEach { onNodeWithText(it).assertDoesNotExist() }
        save("portrait")
        onNode(hasContentDescription("Calendar Movie") and hasClickAction()).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Calendar movie detail destination").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Calendar movie detail destination").assertExists()
        onAllNodesWithContentDescription("Back").onLast().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("S1 · E5–7").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true) }
        waitForIdle()
        onNodeWithText("S1 · E5–7").assertIsDisplayed()
        releaseLabels.forEach { onNodeWithText(it).assertIsDisplayed() }
        save("landscape")
        onNode(hasContentDescription("Calendar Series") and hasClickAction()).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Calendar series detail destination").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Calendar series detail destination").assertExists()
        onAllNodesWithContentDescription("Back").onLast().performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("S1 · E5–7").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Trakt Calendar").assertExists()
        val engine = field(TraktCalendarRepository, "repository")
        runOnIdle { state<CalendarState>(engine, "mutableState").value = CalendarState(problem = CalendarProblem.NETWORK) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Could not load your calendar. Please try again.").fetchSemanticsNodes().isNotEmpty() }
        save("error")
        runOnIdle { state<CalendarState>(engine, "mutableState").value = CalendarState() }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("No releases in the next 14 days.").fetchSemanticsNodes().isNotEmpty() }
        save("empty")
        runOnIdle { state<CalendarState>(engine, "mutableState").value = CalendarState(problem = CalendarProblem.DISCONNECTED) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Connect Trakt in Settings to see your calendar.").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { state<CalendarState>(engine, "mutableState").value = CalendarState(problem = CalendarProblem.AUTHENTICATION) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Reconnect Trakt in Settings to load your calendar.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun settingsEditorToggleReorderAndRestartPreserveNativeIdentity() = runDesktopComposeUiTest(width = 1440, height = 1000) {
        prepare()
        val addon = ManagedAddon("http://127.0.0.1:1/manifest.json", AddonManifest(
            id = "fixture", name = "Fixture", description = "Isolated", version = "1",
            resources = emptyList(), types = listOf("movie"), transportUrl = "http://127.0.0.1:1/manifest.json",
            catalogs = listOf(AddonCatalog("movie", "popular", "Other catalog")),
        ))
        state<AddonsUiState>(AddonRepository, "_uiState").value = AddonsUiState(listOf(addon))
        runBlocking { HomeCatalogSettingsRepository.syncCatalogs(listOf(addon)) }
        setContent { AppEnvironment { MainAppContent(initialRoute = HomescreenSettingsRoute("Home Layout"), ownsAppRuntime = false, showLaunchOverlay = false) } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Trakt Calendar").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Trakt Calendar").performScrollTo().performClick()
        onNode(hasSetTextAction()).performTextInput("My releases")
        waitUntil(timeoutMillis = 5_000) { HomeCatalogSettingsRepository.uiState.value.items.any { it.displayTitle == "My releases" } }
        onNode(isToggleable() and hasAnyAncestor(hasText("My releases"))).performClick()
        assertFalse(HomeCatalogSettingsRepository.uiState.value.items.single { it.key == TRAKT_CALENDAR_KEY }.enabled)
        runOnIdle {
            val oldOrder = HomeCatalogSettingsRepository.uiState.value.items.single { it.key == TRAKT_CALENDAR_KEY }.order
            HomeCatalogSettingsRepository.moveDown(TRAKT_CALENDAR_KEY)
            assertNotEquals(oldOrder, HomeCatalogSettingsRepository.uiState.value.items.single { it.key == TRAKT_CALENDAR_KEY }.order)
            val before = HomeCatalogSettingsRepository.uiState.value.items.single { it.key == TRAKT_CALENDAR_KEY }
            HomeCatalogSettingsRepository.clearLocalState()
            runBlocking { HomeCatalogSettingsRepository.syncCatalogs(listOf(addon)) }
            val after = HomeCatalogSettingsRepository.uiState.value.items.single { it.key == TRAKT_CALENDAR_KEY }
            assertEquals(before.customTitle, after.customTitle)
            assertEquals(before.order, after.order)
            assertEquals(before.enabled, after.enabled)
        }
        onAllNodesWithText("My releases").onFirst().assertExists()
        save("settings")
    }

    private fun prepare() {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        assertEquals("com.nuvio.app.core.storage.IsolatedPreferencesFactory", System.getProperty("java.util.prefs.PreferencesFactory"))
        ThemeSettingsRepository.ensureLoaded()
        TmdbSettingsRepository.setEnabled(false)
        PosterCardStyleRepository.setHoverPreviewEnabled(false)
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        PosterCardStyleRepository.setHideLabelsEnabled(true)
        AddonRepository.clearLocalState()
        state<AddonsUiState>(AddonRepository, "_uiState").value = AddonsUiState()
        AddonRepository.javaClass.getDeclaredField("initialized").apply { isAccessible = true }.setBoolean(AddonRepository, true)
        HomeRepository.clear()
        HomeCatalogSettingsStorage.savePayload("{}")
        HomeCatalogSettingsRepository.clearLocalState()
        TraktAuthRepository.clearLocalState()
        // Register through the real connected-account definition, without credentials or HTTP.
        state<TraktAuthUiState>(TraktAuthRepository, "_uiState").value = TraktAuthUiState(calendarAccountKey = "isolated", mode = TraktConnectionMode.CONNECTED)
        runBlocking { HomeCatalogSettingsRepository.syncCatalogs(emptyList()) }
        assertTrue(HomeCatalogSettingsRepository.uiState.value.items.single { it.key == TRAKT_CALENDAR_KEY }.enabled)
        TraktAuthRepository.clearLocalState()
        HomeCatalogSettingsRepository.setHeroEnabled(false)
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(false)
        HomeCatalogSettingsRepository.setHideUnreleasedContent(true)
        HomeRepository.applyCurrentSettings()
    }

    private fun seedCalendar() {
        val today = LocalDate.now()
        val time = today.plusDays(1).atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val directory = File("build/reports/trakt-calendar").apply { mkdirs() }
        fun art(name: String, width: Int, height: Int): String {
            val file = File(directory, name)
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            graphics.paint = java.awt.GradientPaint(0f, 0f, Color(35, 90, 150), width.toFloat(), height.toFloat(), Color(115, 40, 90))
            graphics.fillRect(0, 0, width, height); graphics.dispose()
            ImageIO.write(image, "png", file)
            return file.toURI().toString()
        }
        val poster = art("poster.png", 300, 450)
        val wide = art("wide.png", 800, 450)
        val show = CalendarMedia("calendar-show", "series", "Calendar Series", 2026, poster, wide)
        val episodes = (5..7).map { CalendarEpisode("e$it", show, 1, it, "Episode $it", java.time.Instant.ofEpochMilli(time).toString(), time, if (it==5) "season_premiere" else null) }
        val movies = listOf(CalendarMovie(CalendarMedia("calendar-movie", "movie", "Calendar Movie", 2026, poster, wide), today.toString()))
        val cards = buildCalendarCards(episodes, movies, today.toString())
        val repository = field(TraktCalendarRepository, "repository")
        state<CalendarState>(repository, "mutableState").value = CalendarState(episodes = episodes, movies = movies, cards = cards)
        HomeRepository.applyCurrentSettings()
    }

    @Suppress("UNCHECKED_CAST") private fun <T> state(owner: Any, name: String) = field(owner, name) as MutableStateFlow<T>
    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    @Suppress("UNCHECKED_CAST") private fun seedDetails(id: String, type: String, title: String, description: String) {
        val cache = field(MetaDetailsRepository, "cachedMetaByRequestKey") as MutableMap<String, Any>
        val constructor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry").declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
        cache["$type:$id"] = constructor.newInstance(MetaDetails(id, type, title, description = description, releaseInfo = "2026"), null, null)
    }
    private fun ComposeUiTest.save(name: String) {
        val directory = File("build/reports/trakt-calendar").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }
}
