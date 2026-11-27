package com.nuvio.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.nuvio.app.core.ui.DesktopEscapeShortcut
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonsUiState
import com.nuvio.app.features.catalog.CatalogRepository
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.FolderDetailRepository
import com.nuvio.app.features.collection.StreamingCollection
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.search.StreamingRowId
import com.nuvio.app.features.search.StreamingRowLayoutRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The streaming collection's service folders open the service page (the one Search's provider
 * tags show), while the community collection it was copied from keeps its folder view.
 * TMDB is off here, so the page shows its retry state; its rows are covered by StreamingRowLayoutTest.
 */
@OptIn(ExperimentalTestApi::class)
class StreamingProviderNavigationTest {
    @Test fun serviceFoldersOpenTheServicePageAndTheOriginalCollectionIsUnchanged() = runDesktopComposeUiTest(width = 1440, height = 1000) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        assertNull(java.util.prefs.Preferences.userRoot().node("nuvio-isolation-probe").get("value", null))
        val community = Collection(
            "streaming-community", "ㅤ", viewMode = "FOLLOW_LAYOUT",
            folders = listOf("Trakt", "Netflix", "Prime Video").map { name ->
                CollectionFolder(name.lowercase(), name, heroBackdropUrl = "https://art.invalid/$name.jpg")
            },
        )
        val streaming = checkNotNull(StreamingCollection.seedFrom(listOf(community)))
        try {
            prepare(listOf(community, streaming))
            setContent { AppEnvironment { MainAppContent(ownsAppRuntime = false, showLaunchOverlay = false) } }
            // Both collections show a Netflix folder; the community one comes first on Home.
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Netflix").fetchSemanticsNodes().size == 2 }
            assertEquals(1, onAllNodesWithText("Trakt").fetchSemanticsNodes().size)

            onAllNodesWithText("Netflix")[1].performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("streaming_provider_Netflix").fetchSemanticsNodes().isNotEmpty() }
            assertNull(FolderDetailRepository.uiState.value.folder)
            // The page's own filter, beside the top bar's Movies tab (the bar no longer hides).
            // Home-style text tabs, not pills: picking Movies selects it in place.
            onNodeWithTag("tag_scope_tabs").assertIsDisplayed()
            onNode(hasText("Movies") and hasAnyAncestor(hasTestTag("tag_scope_tabs"))).performClick()
            onNode(hasText("Movies") and hasAnyAncestor(hasTestTag("tag_scope_tabs"))).assertIsSelected()
            onNode(hasText("All") and hasAnyAncestor(hasTestTag("tag_scope_tabs"))).performClick()
            onNodeWithTag("tag_rows_customize").assertIsDisplayed()
            save("service-page")

            // Customize rows: hiding a row is saved at once, and Esc closes the panel, not the page.
            onNodeWithTag("tag_rows_customize").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("tag_rows_panel").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Service collections").assertIsDisplayed()
            save("customize-rows")
            onNodeWithContentDescription("Popular shows").performClick()
            runOnIdle { assertTrue(StreamingRowId.PopularShows in StreamingRowLayoutRepository.layout.value.hidden) }
            runOnIdle { checkNotNull(DesktopEscapeShortcut.onBack).invoke() }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("tag_rows_panel").fetchSemanticsNodes().isEmpty() }
            onNodeWithTag("streaming_provider_Netflix").assertExists()

            runOnIdle { checkNotNull(DesktopEscapeShortcut.onBack).invoke() }
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithTag("streaming_provider_Netflix").fetchSemanticsNodes().isEmpty() }

            // The community collection's own Netflix folder still opens the folder view.
            waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Netflix").fetchSemanticsNodes().size == 2 }
            onAllNodesWithText("Netflix")[0].performScrollTo().performClick()
            waitUntil(timeoutMillis = 15_000) { FolderDetailRepository.uiState.value.folder?.id == "netflix" }
            assertEquals(0, onAllNodesWithTag("streaming_provider_Netflix").fetchSemanticsNodes().size)
            assertEquals(listOf(community, streaming), CollectionRepository.collections.value)
        } finally {
            StreamingRowLayoutRepository.reset()
            FolderDetailRepository.clear()
            CatalogRepository.clear()
        }
    }

    private fun prepare(collections: List<Collection>) {
        ThemeSettingsRepository.ensureLoaded()
        HomeCatalogSettingsRepository.setHeroEnabled(false)
        HomeCatalogSettingsRepository.setSeasonalCollectionsEnabled(false)
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        TmdbSettingsRepository.setEnabled(false)
        FolderDetailRepository.clear()
        CatalogRepository.clear()
        AddonRepository.clearLocalState()
        addonState().value = AddonsUiState(emptyList())
        AddonRepository.javaClass.getDeclaredField("initialized").apply { isAccessible = true }.setBoolean(AddonRepository, true)
        StreamingRowLayoutRepository.reset()
        CollectionRepository.setCollections(collections)
        runBlocking { HomeCatalogSettingsRepository.syncCollections(collections) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun addonState() =
        AddonRepository.javaClass.getDeclaredField("_uiState").apply { isAccessible = true }.get(AddonRepository) as MutableStateFlow<AddonsUiState>

    private fun ComposeUiTest.save(name: String) {
        val dir = File("build/reports/streaming-provider").apply { mkdirs() }
        // An open popup is a second root; the last root is whatever is on top.
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }
}
