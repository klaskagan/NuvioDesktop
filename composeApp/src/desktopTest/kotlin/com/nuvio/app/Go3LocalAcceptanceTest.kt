package com.nuvio.app

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toAwtImage
import com.nuvio.app.features.go3.Go3Catalog
import kotlinx.coroutines.runBlocking
import com.nuvio.app.features.go3.Go3PlayerLauncher
import com.nuvio.app.features.search.SearchRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** Opt-in interactive check. Uses isolated Nuvio state and the explicitly configured Go3 profile. */
@OptIn(ExperimentalTestApi::class)
class Go3LocalAcceptanceTest {
    @Test fun realCatalogueToOfficialPlayer() = runDesktopComposeUiTest(width=1200,height=900,testTimeout=1100.seconds) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome")=="true" && System.getenv("NUVIO_GO3_MANUAL_CHECK")=="1")
        require(!System.getenv("NUVIO_GO3_SESSION_DIR").isNullOrBlank())
        Go3PlayerLauncher.install()
        runBlocking {Go3Catalog.page("live",null,true)}
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(false)
        SearchRepository.reset()
        setContent { AppEnvironment { MainAppContent(initialTab=AppScreenTab.Search,ownsAppRuntime=false,showLaunchOverlay=false) } }
        waitUntil(timeoutMillis=60000) {SearchRepository.discoverUiState.value.items.isNotEmpty()}
        val item=SearchRepository.discoverUiState.value.items.first()
        waitUntil(timeoutMillis=45000) {onAllNodesWithText(item.name).fetchSemanticsNodes().isNotEmpty()}
        val directory=File("build/reports/go3").apply {mkdirs()}
        ImageIO.write(onRoot().captureToImage().toAwtImage(),"png",File(directory,"discover.png"))
        mainClock.autoAdvance=false
        onNodeWithContentDescription(item.name).performClick()
        mainClock.advanceTimeBy(600)
        onNodeWithText("Watch on Go3").performClick()
        // Human verifies protected video directly. Never capture the player.
        Thread.sleep(900000)
    }
}
