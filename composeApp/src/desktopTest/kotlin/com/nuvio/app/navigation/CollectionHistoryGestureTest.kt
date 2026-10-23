package com.nuvio.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioTheme
import java.io.File
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class CollectionHistoryGestureTest {
    @get:Rule val compose = createComposeRule()
    private val actions = mutableListOf<HistorySwipe>()
    private val entries = mutableStateOf(listOf(1))

    @Test fun shelfHeaderAllowsHistoryBesideItsTitle() {
        showShelf()
        swipe(Offset(500f, 14f), -4f)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Back), actions) }
    }

    @Test fun blankSpaceBesideShortCollectionAllowsBackAndForward() {
        showShelf()
        swipe(Offset(500f, 70f), -4f)
        swipe(Offset(500f, 70f), 4f)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Back, HistorySwipe.Forward), actions) }
    }

    @Test fun hoveringCollectionAtEitherEndStillPreventsHistory() {
        showShelf()
        swipe(Offset(60f, 70f), -4f)
        swipe(Offset(60f, 70f), 4f)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
    }

    @Test fun gapsBetweenItemsRemainPartOfCollection() {
        entries.value = listOf(1, 2)
        showShelf()
        swipe(Offset(130f, 70f), -4f)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
    }

    @Test fun shrinkingCollectionReleasesItsFormerTail() {
        entries.value = (1..20).toList()
        showShelf()
        swipe(Offset(500f, 70f), -4f)
        compose.runOnIdle { assertEquals(emptyList(), actions); entries.value = listOf(1) }
        swipe(Offset(500f, 70f), -4f)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Back), actions) }
    }

    @Test fun rightToLeftShortRowOnlyExcludesItsOccupiedRightSide() {
        showShelf(LayoutDirection.Rtl)
        swipe(Offset(540f, 70f), -4f)
        compose.runOnIdle { assertEquals(emptyList(), actions) }
        swipe(Offset(60f, 70f), 4f)
        compose.runOnIdle { assertEquals(listOf(HistorySwipe.Forward), actions) }
    }

    private fun swipe(position: Offset, amount: Float) {
        compose.onNodeWithTag("shelf").performMouseInput {
            moveTo(position)
            advanceEventTime(600)
            scroll(amount, ScrollWheel.Horizontal)
        }
        compose.mainClock.advanceTimeBy(600)
    }

    private fun showShelf(direction: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            NuvioTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DesktopHistoryNavigationHost(
                        enabled = true,
                        canGoBack = true,
                        canGoForward = true,
                        onBack = { actions += HistorySwipe.Back },
                        onForward = { actions += HistorySwipe.Forward },
                    ) {
                        Column(Modifier.fillMaxSize().background(Color(0xFF24272B))) {
                            NuvioShelfSection(
                                title = "Latest",
                                entries = entries.value,
                                modifier = Modifier.size(600.dp, 150.dp).testTag("shelf"),
                                rowContentPadding = PaddingValues(horizontal = 16.dp),
                                itemSpacing = 28.dp,
                                state = rememberLazyListState(),
                            ) {
                                Box(Modifier.size(100.dp, 80.dp).background(Color.DarkGray))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val directory = File("build/reports/history-navigation").apply { mkdirs() }
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File(directory, "collection-${direction.name}.png"))
    }
}
