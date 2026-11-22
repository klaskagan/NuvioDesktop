package com.nuvio.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.history.HistoryScreen
import com.nuvio.app.features.history.LocalHistorySource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.nuvio.app.core.ui.LocalPosterZoomOverlayHost
import com.nuvio.app.core.ui.PosterZoomOverlayHostState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.nuvio.app.features.tracking.TrackingHistorySource
import com.nuvio.app.features.tracking.TrackingHistoryRemover
import com.nuvio.app.features.history.historyLocalDate
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.DesktopNavigationLayout
import com.nuvio.app.features.settings.NavBarStyle
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.tracking.TrackingCatalogReference
import com.nuvio.app.features.tracking.TrackingEpisode
import com.nuvio.app.features.tracking.TrackingHistoryAccount
import com.nuvio.app.features.tracking.TrackingHistoryEvent
import com.nuvio.app.features.tracking.TrackingHistoryPage
import com.nuvio.app.features.tracking.TrackingHistoryProvider
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HistoryNavigationTest {
    @Test fun topBarOpensHistoryAndDetails() = verify(DesktopNavigationLayout.TopBar)
    @Test fun sidebarOpensHistoryAndDetails() = verify(DesktopNavigationLayout.Sidebar)

    @Test fun localDatesChangeAtLocalMidnight() {
        val date = LocalDate.of(2026, 11, 17)
        val midnight = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(date.minusDays(1).toString(), historyLocalDate(watch.copy(watchedAtMillis = midnight - 1)))
        assertEquals(date.toString(), historyLocalDate(watch.copy(watchedAtMillis = midnight)))
    }

    @Test fun rightClickRemovesOnlySelectedWatch() = runDesktopComposeUiTest(width = 1200, height = 900) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val session = TrackingHistoryAccount(1, 1)
        var removed: String? = null
        val reader = object : TrackingHistoryProvider, TrackingHistoryRemover {
            override val providerId = TrackingProviderId.SIMKL
            override val account = MutableStateFlow<TrackingHistoryAccount?>(session)
            override suspend fun loadPage(profileId: Int, cursor: String?) = TrackingHistoryPage(
                listOf(watch, watch.copy(id = "rewatch")), null,
            )
            override suspend fun removeEvent(profileId: Int, eventId: String) { removed = eventId }
        }
        setContent {
            AppEnvironment {
                val host = remember { PosterZoomOverlayHostState() }
                val haze = rememberHazeState()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().hazeSource(haze)) {
                        CompositionLocalProvider(
                            LocalHistorySource provides TrackingHistorySource(reader, session),
                            LocalPosterZoomOverlayHost provides host,
                        ) { HistoryScreen(null, null) }
                    }
                    host.content?.invoke(haze)
                }
            }
        }
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("history-card-event-1")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("history-card-event-1")
            .performMouseInput { click(button = MouseButton.Secondary) }
        onNodeWithText("Remove from history").assertIsDisplayed()
        val report = File("build/reports/watch-history").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(report, "history-actions.png"))
        onNodeWithText("Remove from history").performClick()
        waitUntil(timeoutMillis = 10_000) { removed == "event-1" }
        onNodeWithTag("history-card-event-1").assertDoesNotExist()
        onNodeWithTag("history-card-rewatch").assertIsDisplayed()
    }

    private fun verify(layout: DesktopNavigationLayout) = runDesktopComposeUiTest(width = 1200, height = 1100) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        ThemeSettingsRepository.ensureLoaded()
        ThemeSettingsRepository.setDesktopNavigationLayout(layout)
        ThemeSettingsRepository.setNavBarStyle(
            if (layout == DesktopNavigationLayout.Sidebar) NavBarStyle.EXPANDED else NavBarStyle.ADAPTIVE,
        )
        ThemeSettingsRepository.setDetailNavigationEnabled(true)
        ThemeSettingsRepository.setDetailNavigationAutoHide(false)
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(false)
        PosterCardStyleRepository.ensureLoaded()
        PosterCardStyleRepository.setCalendarLandscapeModeEnabled(false)
        PosterCardStyleRepository.setHistoryLandscapeModeEnabled(false)
        val report = File("build/reports/watch-history").apply { mkdirs() }
        val screenshot = File(report, "episode-fixture.png")
        val frame = BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB)
        frame.createGraphics().apply {
            color = Color(30, 150, 210)
            fillRect(0, 0, frame.width, frame.height)
            dispose()
        }
        ImageIO.write(frame, "png", screenshot)
        seedDetails(screenshot.toURI().toString())
        val busyDay = listOf(watch) + (1..8).map { index ->
            watch.copy(id = "same-day-$index", watchedAtMillis = watch.watchedAtMillis - index * 1000L)
        }
        val nextRequest = CompletableDeferred<Unit>()
        val nextPage = CompletableDeferred<TrackingHistoryPage>()
        val fake = object : TrackingHistoryProvider {
            override val providerId = TrackingProviderId.SIMKL
            override val account = MutableStateFlow<TrackingHistoryAccount?>(null)

            override suspend fun loadPage(profileId: Int, cursor: String?): TrackingHistoryPage {
                if (cursor == null) return TrackingHistoryPage(busyDay + episodeWatch, "next")
                nextRequest.complete(Unit)
                return nextPage.await()
            }
        }
        TrackingProviderRegistry.registerHistoryReader(fake)
        try {
            setContent {
                AppEnvironment { MainAppContent(ownsAppRuntime = false, showLaunchOverlay = false) }
            }
            onNodeWithContentDescription("History").assertDoesNotExist()
            runOnIdle { fake.account.value = TrackingHistoryAccount(ProfileRepository.activeProfileId, 1L) }
            onNodeWithContentDescription("History").performClick()
            onNodeWithTag("history-screen").assertIsDisplayed()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Fixture movie").fetchSemanticsNodes().isNotEmpty() }
            runOnIdle { PosterCardStyleRepository.setCalendarLandscapeModeEnabled(true) }
            val poster = onNodeWithTag("history-card-event-1")
            val portraitBounds = poster.fetchSemanticsNode().boundsInRoot
            assertTrue(onNodeWithTag("history-card-same-day-8").fetchSemanticsNode().boundsInRoot.top >= portraitBounds.bottom)
            runOnIdle { assertTrue(!PosterCardStyleRepository.uiState.value.historyLandscapeModeEnabled) }
            onNodeWithTag("history-landscape").performClick()
            runOnIdle { assertTrue(PosterCardStyleRepository.uiState.value.calendarLandscapeModeEnabled) }
            runOnIdle { PosterCardStyleRepository.setCalendarLandscapeModeEnabled(false) }
            runOnIdle { assertTrue(PosterCardStyleRepository.uiState.value.historyLandscapeModeEnabled) }
            runOnIdle { PosterCardStyleRepository.onProfileChanged() }
            runOnIdle {
                assertTrue(PosterCardStyleRepository.uiState.value.historyLandscapeModeEnabled)
                assertTrue(!PosterCardStyleRepository.uiState.value.calendarLandscapeModeEnabled)
            }
            val firstBounds = poster.fetchSemanticsNode().boundsInRoot
            assertTrue(firstBounds.width > portraitBounds.width)
            val agenda = onNodeWithTag("history-agenda")
            val lastBounds = onNodeWithTag("history-card-same-day-8").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(lastBounds.top >= firstBounds.bottom)
            val viewportRight = agenda.fetchSemanticsNode().boundsInRoot.right
            (1..8).forEach { index ->
                assertTrue(onNodeWithTag("history-card-same-day-$index").fetchSemanticsNode().boundsInRoot.right <= viewportRight)
            }
            agenda.performScrollToNode(hasTestTag("history-card-event-2"))
            onNodeWithText("S1 • E2 - Second episode").assertIsDisplayed()
            waitUntil(timeoutMillis = 10_000) {
                val image = onNodeWithTag("history-card-event-2").captureToImage().toAwtImage()
                val pixel = Color(image.getRGB(image.width / 2, image.height / 3))
                pixel.blue > 150 && pixel.green > 100 && pixel.red < 70
            }
            onNodeWithText("Today").performClick()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(report, "${layout.name}.png"))
            // The top bar has no Home item: Home's tabs lead it, and For you is Home's default.
            val homeEntry = if (layout == DesktopNavigationLayout.TopBar) "For you" else "Home"
            val homeBounds = onNodeWithContentDescription(homeEntry).fetchSemanticsNode().boundsInRoot
            onNodeWithTag("history-card-event-1").performMouseInput { click(button = MouseButton.Secondary) }
            onNodeWithText("Go to details").assertIsDisplayed()
            mainClock.advanceTimeBy(1_500)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(report, "${layout.name}-actions.png"))
            // A click where navigation sits must dismiss the full-window scrim, not navigate.
            onRoot().performMouseInput { click(homeBounds.center) }
            onNodeWithText("Go to details").assertDoesNotExist()
            onNodeWithTag("history-screen").assertIsDisplayed()
            onNode(hasAnyAncestor(hasTestTag("history-card-event-1")) and hasClickAction(), useUnmergedTree = true).performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("History fixture detail").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("History").assertIsDisplayed()
            onAllNodesWithContentDescription("Back").onLast().performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Fixture movie").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Load more").assertDoesNotExist()
            onNodeWithTag("history-agenda").performScrollToIndex(5)
            onNodeWithTag("history-agenda").performMouseInput { enter(center); scroll(100f, ScrollWheel.Vertical) }
            waitUntil(timeoutMillis = 10_000) { nextRequest.isCompleted }
            runOnIdle { fake.account.value = null }
            nextPage.complete(TrackingHistoryPage(listOf(watch), null))
            onNodeWithTag("history-screen").assertDoesNotExist()
            onNodeWithContentDescription("History").assertDoesNotExist()
            onNodeWithContentDescription(homeEntry).assertIsDisplayed()
        } finally {
            TrackingProviderRegistry.unregisterHistoryReader(fake)
            MetaDetailsRepository.clear()
        }
    }

    // Seed the normal Details cache, as in CalendarScreenUiTest, to keep navigation offline.
    @Suppress("UNCHECKED_CAST")
    private fun seedDetails(screenshotUrl: String) {
        MetaDetailsRepository.clear()
        val cacheField = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey")
        cacheField.isAccessible = true
        val cache = cacheField.get(MetaDetailsRepository) as MutableMap<String, Any>
        val entry = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry")
        val constructor = entry.declaredConstructors.single { it.parameterCount == 3 }
        constructor.isAccessible = true
        cache["movie:history-fixture"] = constructor.newInstance(
            MetaDetails("history-fixture", "movie", "Fixture movie", description = "History fixture detail"),
            null,
            null,
        )
        cache["series:history-series"] = constructor.newInstance(
            MetaDetails("history-series", "series", "Fixture series", videos = listOf(
                MetaVideo("history-series:1:2", "Second episode", thumbnail = screenshotUrl, season = 1, episode = 2),
            )),
            null,
            null,
        )
    }

    private companion object {
        val episodeWatch = TrackingHistoryEvent(
            id = "event-2",
            watchedAtMillis = 1_764_800_000_000L,
            media = TrackingMediaReference(
                kind = TrackingMediaKind.SHOW,
                title = "Fixture series",
                episode = TrackingEpisode(1, 2, "Second episode"),
                catalog = TrackingCatalogReference("history-series", "series"),
            ),
        )
        val watch = TrackingHistoryEvent(
            id = "event-1",
            watchedAtMillis = 1_765_000_000_000L,
            media = TrackingMediaReference(
                kind = TrackingMediaKind.MOVIE,
                title = "Fixture movie",
                catalog = TrackingCatalogReference("history-fixture", "movie"),
            ),
        )
    }
}
