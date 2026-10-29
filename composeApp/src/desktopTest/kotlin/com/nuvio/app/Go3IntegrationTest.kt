package com.nuvio.app

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import javax.imageio.ImageIO
import com.nuvio.app.features.go3.*
import com.nuvio.app.features.search.SearchRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalTestApi::class)
class Go3IntegrationTest {
    @Test fun discoverOpensProgrammeDetailsAndDispatchesExactReplay() = runDesktopComposeUiTest(width=1200,height=900) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome")=="true")
        val replay="https://go3.lt/live_tv/extra,live-6886504/sunderland-leeds,programme-12642899"
        var opened: String?=null
        var accountAction: String?=null
        Go3Catalog.accountAction={accountAction=it;true}
        Go3Playback.open={opened=it;true}
        Go3Catalog.loader={listOf(Go3Event("go3:programme:12642899","Go3 test replay",replay,null,"replay","ARCHYVAS"))}
        runBlocking {Go3Catalog.page("live",null,true)}
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(false)
        SearchRepository.reset()
        try {
            setContent { AppEnvironment { MainAppContent(initialTab=AppScreenTab.Search,ownsAppRuntime=false,showLaunchOverlay=false) } }
            waitUntil(timeoutMillis=15000) {SearchRepository.discoverUiState.value.items.isNotEmpty()}
            mainClock.autoAdvance=false
            onNodeWithText("Go3").performClick()
            mainClock.advanceTimeBy(600)
            onNodeWithContentDescription("Back").assertIsDisplayed()
            onNodeWithTag("go3-detail-backdrop").assertHeightIsEqualTo(900.dp)
            onNodeWithContentDescription("Back").assertWidthIsEqualTo(48.dp)
            onNodeWithText("Refresh Go3").assertDoesNotExist()
            onNodeWithText("Playback opens in Nuvio’s Go3 window. Your Go3 subscription applies.").assertDoesNotExist()
            val imageFile=File("build/reports/go3/details-fixed.png").apply {parentFile.mkdirs()}
            ImageIO.write(onRoot().captureToImage().toAwtImage(),"png",imageFile)
            onNodeWithText("Watch on Go3").assertExists().performClick()
            assertEquals(replay,opened)
            onNodeWithContentDescription("Back").performClick()
            mainClock.advanceTimeBy(600)
            onNodeWithContentDescription("Settings").performClick()
            mainClock.advanceTimeBy(600)
            onNodeWithText("Content & Discovery").performScrollTo().performClick()
            mainClock.advanceTimeBy(600)
            onNodeWithText("Go3").performScrollTo().performClick()
            mainClock.advanceTimeBy(600)
            onNodeWithText("Sign in / Open Go3").performClick()
            assertEquals("signin",accountAction)
            onNodeWithText("Reset Go3 session").performClick()
            mainClock.advanceTimeBy(600)
            onNodeWithText("Reset Go3 session?").assertExists()
            onNodeWithText("Cancel").performClick()
            assertEquals("signin",accountAction)
        } finally {Go3Catalog.loader=null;Go3Catalog.accountAction=null;Go3Playback.open=null;SearchRepository.reset()}
    }
    @Test fun staleListingsAreMarkedAndReplayFilteringKeepsProgrammeIdentity() = runBlocking {
        Go3Catalog.loader={listOf(
            Go3Event("go3:programme:9001","Upcoming","https://go3.lt/live_tv/a,live-1/a,programme-9001",null,"upcoming","GREITAI"),
            Go3Event("go3:programme:9002","Replay","https://go3.lt/live_tv/a,live-1/a,programme-9002",null,"replay","ARCHYVAS"))}
        try {
            val page=Go3Catalog.page("cache-test","Replay",true)
            assertEquals(listOf("go3:programme:9002"),page.map {it.id})
            Go3Catalog.loader={throw IllegalStateException("offline")}
            val stale=Go3Catalog.page("cache-test","Replay",true)
            assertEquals("stale",stale.single().status)
            assertEquals("replay",Go3Catalog.find("go3:programme:9002")?.state)
        } finally {Go3Catalog.loader=null}
    }
    @Test fun cachedAvailabilityFiltersAreImmediateAndHideAbsentLive() = runBlocking {
        var requests=0
        Go3Catalog.loader={kind -> if(kind=="epl") requests++;listOf(Go3Event("go3:programme:8001","Replay only","https://go3.lt/live_tv/a,live-1/a,programme-8001",null,"replay","ARCHYVAS"))}
        try {
            Go3Catalog.page("epl",null,true)
            assertEquals(listOf("Replay"),Go3Catalog.filters("epl"))
            SearchRepository.reset()
            SearchRepository.refreshDiscover(emptyList())
            SearchRepository.selectDiscoverCatalog("go3:epl")
            SearchRepository.selectDiscoverGenre("Replay")
            assertFalse(SearchRepository.discoverUiState.value.isLoading)
            assertEquals(listOf("go3:programme:8001"),SearchRepository.discoverUiState.value.items.map {it.id})
            SearchRepository.selectDiscoverGenre(null)
            assertFalse(SearchRepository.discoverUiState.value.isLoading)
            assertEquals(1,SearchRepository.discoverUiState.value.items.size)
            SearchRepository.selectDiscoverGenre("Live")
            assertNull(SearchRepository.discoverUiState.value.selectedGenre)
            assertEquals(1,requests)
        } finally {Go3Catalog.loader=null;SearchRepository.reset()}
    }
    @Test fun canonicalWatchValidationRejectsUntrustedRoutes() {
        assertTrue(Go3Playback.isWatchUrl("https://go3.lt/live_tv/extra,live-1/match,programme-2"))
        assertFalse(Go3Playback.isWatchUrl("https://go3.lt.evil.com/watch/a,live-1"))
        assertFalse(Go3Playback.isWatchUrl("https://go3.lt/watch/a,live-1?token=x"))
    }
}
