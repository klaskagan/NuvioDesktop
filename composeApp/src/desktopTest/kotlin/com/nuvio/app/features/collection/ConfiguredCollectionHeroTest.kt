package com.nuvio.app.features.collection

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.home.*
import com.nuvio.app.features.home.components.HomeCollectionRowSection
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ConfiguredCollectionHeroTest {
    @Test fun pinnedControlsDoNotScrollButOffscreenCardsAndKeyboardTargetsStillDo() {
        val normal = object : androidx.compose.foundation.gestures.BringIntoViewSpec {}
        val wide = PinnedFilterBringIntoViewSpec(normal, { 66 }, 54f)
        assertEquals(0f, wide.calculateScrollDistance(-7f, 38f, 800f))
        assertEquals(0f, wide.calculateScrollDistance(-52f, 38f, 800f))
        assertEquals(138f, wide.calculateScrollDistance(900f, 38f, 800f))
        val wrapped = PinnedFilterBringIntoViewSpec(normal, { 160 }, 54f)
        assertEquals(-20f, wrapped.calculateScrollDistance(-20f, 153f, 800f),
            "A poster partially covered by a wrapped header must still scroll into view")
        val absent = PinnedFilterBringIntoViewSpec(normal, { 0 }, 54f)
        assertEquals(-7f, absent.calculateScrollDistance(-7f, 38f, 800f))
    }

    @Test fun homeCardOpensTheMatchingFolderAndGridScrollsAway() = runDesktopComposeUiTest(width = 1440, height = 900) {
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        var opened by mutableStateOf(false)
        var pages = 0
        setContent { NuvioTheme {
            if (!opened) HomeCollectionRowSection(collection, onFolderClick = { c, f ->
                assertEquals(collection.id, c); assertEquals(folder.id, f); opened = true
            }) else ConfiguredFolderContent(state.copy(tabs = listOf(tab.copy(supportsPagination = true, nextSkip = 60))),
                emptyList(), emptySet(), { opened = false }, {}, { pages++ }, {}, {})
        } }
        onNodeWithText(collection.title).assertIsDisplayed()
        save("decade-home-shelf")
        onNodeWithText(folder.title).performClick()
        onNodeWithTag("collection_background_${folder.id}").assertIsDisplayed()
        onNode(hasText(folder.title) or hasContentDescription(folder.title)).assertIsDisplayed()
        save("decade-hero-wide")
        onNodeWithTag("collection_grid").performScrollToIndex(65)
        onNodeWithTag("collection_background_${folder.id}").assertIsNotDisplayed()
        runOnIdle { assertTrue(pages > 0) }
        save("decade-scrolled")
        onNodeWithTag("catalog_back").performClick()
        onNodeWithText(collection.title).assertIsDisplayed()
    }

    @Test fun narrowLargeTextAndLoadingEmptyErrorKeepHero() = runDesktopComposeUiTest(width = 480, height = 800) {
        var current by mutableStateOf(state.copy(isLoading = true, tabs = emptyList()))
        setContent { NuvioTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                ConfiguredFolderContent(current, emptyList(), emptySet(), {}, {}, {}, {}, {})
            }
        } }
        onNodeWithTag("collection_background_${folder.id}").assertIsDisplayed()
        save("decade-narrow-loading")
        runOnIdle { current = state.copy(tabs = listOf(tab.copy(items = emptyList()))) }
        onNodeWithTag("collection_background_${folder.id}").assertIsDisplayed()
        runOnIdle { current = state.copy(tabs = listOf(tab.copy(items = emptyList(), error = "Catalog unavailable"))) }
        onNodeWithText("Catalog unavailable").assertExists()
        val title = onNode(hasText(folder.title) or hasContentDescription(folder.title)).fetchSemanticsNode().boundsInRoot
        val back = onNodeWithTag("catalog_back").fetchSemanticsNode().boundsInRoot
        assertTrue(title.top >= back.bottom)
        save("decade-narrow-error")
    }

    @Test fun studioHeroUsesItsOwnArtworkAndTabsKeepSelectionObservable() = runDesktopComposeUiTest(width = 1200, height = 800) {
        val studio = folder.copy(id = "studio", title = "Warner Bros. Pictures", titleLogoUrl = asset("studio-logo.png", ""), heroBackdropUrl = asset("studio.webp", "https://imkaptain.github.io/nuvio-assets/assets/images/91b18b9b.webp"))
        var current by mutableStateOf(state.copy(folder = studio, artworkFolder = studio, tabs = listOf(tab, tab.copy(label = "Recent movies", sourceKey = "recent"))))
        setContent { NuvioTheme { ConfiguredFolderContent(current, emptyList(), emptySet(), {}, { current = current.copy(selectedTabIndex = it) }, {}, {}, {}) } }
        onNodeWithTag("collection_background_studio").assertIsDisplayed()
        save("studio-wide")
        onNodeWithText("Recent movies").performScrollTo().performClick()
        runOnIdle { assertEquals(1, current.selectedTabIndex) }
        onNodeWithText("Recent movies").assertIsSelected()
    }

    @Test fun artworkIsScopedAndIndependentOfMovieResults() {
        assertEquals(folder, collection.artworkFolder(folder.id))
        assertNull(collection.copy(id = "personal", title = "My movies").artworkFolder(folder.id))
        assertNull(collection.copy(title = "Other community collection").artworkFolder(folder.id))
        assertNull(collection.copy(folders = listOf(folder.copy(heroBackdropUrl = ""))).artworkFolder(folder.id))
    }

    private val folder = CollectionFolder("decade", "2020s Movies", titleLogoUrl = asset("decade-logo.png", ""),
        heroBackdropUrl = asset("decade.webp", "https://imkaptain.github.io/nuvio-assets/assets/images/5380d085.webp"))
    private val collection = Collection("decades-community", "By Decade", folders = listOf(folder))
    private val movies = (1..72).map { MetaPreview("fixture-$it", "movie", "Dune", poster = asset("poster.jpg", "https://image.tmdb.org/t/p/w500/d5NXSklXo0qyIYkgV94XAgMIckC.jpg"),
        banner = asset("backdrop.jpg", "https://image.tmdb.org/t/p/w1280/jlGmlFOcfo8n5tURmhC7YVd4Iyy.jpg")) }
    private val tab = FolderTab("Popular movies", sourceKey = "popular", items = movies, isLoading = false)
    private val state = FolderDetailUiState(folder = folder, collectionId = collection.id, artworkFolder = folder, tabs = listOf(tab), isLoading = false)

    private fun asset(file: String, url: String): String = System.getenv("NUVIO_COLLECTION_TEST_ART")?.let { File(it, file).takeIf(File::exists)?.toURI()?.toString() } ?: url
    private fun ComposeUiTest.save(name: String) {
        val dir = File("build/reports/collection-artwork").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }
}
