package com.nuvio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.LocalNuvioShelfHeaderStyle
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.core.ui.NuvioShelfHeaderStyle
import com.nuvio.app.core.ui.RedesignTypography
import com.nuvio.app.features.home.components.HomeBoard
import com.nuvio.app.features.home.components.HomeSkeletonHero
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.features.home.components.HomeStateCard
import com.nuvio.app.features.home.components.HomeStateKind
import com.nuvio.app.features.home.components.LocalHomeHeroHeightCap
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Home design "For you · loading" and "For you · empty, error and offline" at the design's
 * 1360 × 850 dp window, with isolated storage. PNGs go to build/reports/home-states for review.
 */
@OptIn(ExperimentalTestApi::class)
class HomeStatesCaptureTest {
    private val output = File("build/reports/home-states").apply { mkdirs() }
    private fun ComposeUiTest.save(name: String) {
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(output, "$name.png"))
    }

    /** A fresh isolated profile has no addons: the real Home shows the no-sources card, centred. */
    @Test
    fun emptyProfileShowsTheNoSourcesCardCentred() = runDesktopComposeUiTest(width = 1360, height = 850) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        setContent {
            AppEnvironment {
                MainAppContent(initialTab = AppScreenTab.Home, ownsAppRuntime = false, showLaunchOverlay = false)
            }
        }
        try {
            waitUntil(timeoutMillis = 20_000) {
                onAllNodesWithTag("home-state-nosources", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            save("for-you-empty-timeout")
            throw timeout
        }
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithText("Add a catalog to start").assertExists()
        onNodeWithText("Open Addons").assertExists()
        val card = onNodeWithTag("home-state-nosources", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = onRoot().fetchSemanticsNode().boundsInRoot
        save("for-you-empty")
        assertTrue(abs(card.center.x - root.center.x) < 60f, "card centre ${card.center.x} vs window ${root.center.x}")
        assertTrue(card.top > 150f && card.bottom < root.bottom - 100f, "card is placed where the rows would be: $card")
    }

    @Test
    fun stateCards() = runDesktopComposeUiTest(width = 1360, height = 850) {
        setContent {
            AppEnvironment {
                RedesignTypography {
                    Column(Modifier.fillMaxSize().background(Color(0xFF0E0D0C)).padding(top = 60.dp)) {
                        HomeStateCard(HomeStateKind.LoadFailed, horizontalPadding = 32.dp, onRetry = {})
                        HomeStateCard(HomeStateKind.Offline, horizontalPadding = 32.dp, modifier = Modifier.padding(top = 24.dp))
                        HomeStateCard(HomeStateKind.TabEmpty, horizontalPadding = 32.dp, modifier = Modifier.padding(top = 24.dp))
                    }
                }
            }
        }
        waitForIdle()
        save("for-you-error-offline-tab")
    }

    @Test
    fun loadingBoardAndRows() = runDesktopComposeUiTest(width = 1360, height = 850) {
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        setContent {
            AppEnvironment {
                RedesignTypography {
                    CompositionLocalProvider(
                        LocalNuvioShelfHeaderStyle provides NuvioShelfHeaderStyle.Editorial,
                        LocalHomeHeroHeightCap provides 474.dp,
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF0E0D0C))) {
                            // Home's rows sit in a scrolling list: no height limit, as in the app.
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 93.dp)) {
                                HomeBoard(
                                    height = 474.dp,
                                    expanded = false,
                                    resumeItems = emptyList(),
                                    onResumeClick = null,
                                    onResumeLongPress = null,
                                    sideLoading = true,
                                    modifier = Modifier.padding(horizontal = 32.dp).padding(top = 4.dp, bottom = 18.dp),
                                ) { HomeSkeletonHero() }
                                HomeSkeletonRow(horizontalPadding = 32.dp)
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        save("for-you-loading")
        val lead = onNodeWithTag("home_skeleton_lead", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val posters = onAllNodesWithTag("home_skeleton_poster", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        println("HOME_STATES capture lead=$lead posters=${posters.take(3)}")
        assertTrue(abs(posters.first().top - lead.top) <= 1f, "posters start level with the lead block")
    }
}
