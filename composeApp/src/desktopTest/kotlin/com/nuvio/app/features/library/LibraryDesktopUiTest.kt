package com.nuvio.app.features.library

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.AppKeyboardShortcuts
import com.nuvio.app.core.network.NetworkCondition
import com.nuvio.app.features.cloud.CloudLibraryFile
import com.nuvio.app.features.cloud.CloudLibraryItem
import com.nuvio.app.features.cloud.CloudLibraryItemType
import com.nuvio.app.features.cloud.CloudLibraryProviderState
import com.nuvio.app.features.cloud.CloudLibraryUiState
import com.nuvio.app.features.debrid.DebridProvider
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.servers.ServerConnection
import com.nuvio.app.features.servers.ServerFailure
import com.nuvio.app.features.servers.ServerLibrary
import com.nuvio.app.features.servers.ServerLibraryRef
import com.nuvio.app.features.servers.ServerMediaKind
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assume.assumeTrue

/**
 * The desktop Library redesign, rendered from fixtures (no repositories, no account): the header
 * stays put across panes, lists open in place and Esc returns, menus and the Cloud panel own Esc
 * while open, and a failed server keeps a Retry tile. Screenshots land in build/reports/library-desktop.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryDesktopUiTest {
    private val reports = File("build/reports/library-desktop").apply { mkdirs() }

    @Test
    fun headerControlsKeepTheirPlaceAcrossPanesAndStates() = libraryTest { harness ->
        // Sources and Downloads never move; the slot beside them holds the layout toggle on
        // Saved and Refresh on Cloud, at the same place.
        val tags = listOf("library-source-Saved", "library-source-Cloud", "library-source-Servers", "library-downloads")
        fun bounds() = tags.associateWith { onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        fun slot(tag: String) = mapOf("slot" to onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
        val toggleSlot = slot("library-layout-toggle")
        harness.loaded = false
        waitForIdle()
        val loading = bounds()
        snapshot("saved-loading")
        harness.loaded = true
        waitForIdle()
        onNodeWithTag("library-board").assertIsDisplayed()
        assertBoundsEqual(loading, bounds(), "loading → loaded")
        snapshot("saved-shelves")

        onNodeWithTag("library-layout-toggle").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-grid").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(LibraryLayoutMode.VERTICAL, harness.layout)
        assertBoundsEqual(loading, bounds(), "shelves → grid")
        snapshot("saved-grid")

        onNodeWithTag("library-source-Cloud").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-cloud-list").fetchSemanticsNodes().isNotEmpty() }
        assertBoundsEqual(loading, bounds(), "grid → cloud")
        assertBoundsEqual(toggleSlot, slot("library-cloud-refresh"), "layout toggle → refresh")
        snapshot("cloud")

        onNodeWithTag("library-source-Servers").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-servers").fetchSemanticsNodes().isNotEmpty() }
        assertBoundsEqual(loading, bounds(), "cloud → servers")
        snapshot("servers")
    }

    @Test
    fun aListOpensInPlaceAndEscReturnsToTheShelves() = libraryTest { harness ->
        onNodeWithTag("library-list-row-favorites").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-grid").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("library-list-pill-favorites").assertIsSelected()
        onNodeWithTag("library-list-back").assertIsDisplayed()
        assertEquals(LibraryLayoutMode.HORIZONTAL, harness.layout, "opening a list must not change the layout setting")
        snapshot("list-opened")

        // A different list from the pills stays in the grid.
        onNodeWithTag("library-list-pill-watchlist").performClick()
        runOnIdle { assertEquals("watchlist", harness.opened) }

        runOnIdle { assertTrue(AppKeyboardShortcuts.overlayEscape?.invoke() == true) }
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-shelves").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { assertEquals(null, harness.opened) }
        assertEquals(LibraryLayoutMode.HORIZONTAL, harness.layout)

        // View all on a shelf opens that list too.
        onNodeWithTag("library-shelf-watchlist").performScrollTo()
        onAllNodesWithContentDescription("View All", useUnmergedTree = true).onFirst().performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-grid").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { assertEquals("watchlist", harness.opened) }
    }

    @Test
    fun sortMenuTakesEscBeforeTheOpenedList() = libraryTest { harness ->
        onNodeWithTag("library-list-row-watchlist").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-sort").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("library-sort").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-sort-menu").fetchSemanticsNodes().isNotEmpty() }
        snapshot("sort-menu")
        onNodeWithText("Title A–Z").performClick()
        runOnIdle { assertEquals(LibrarySortOption.TITLE_ASC, harness.sort) }
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-sort-menu").fetchSemanticsNodes().isEmpty() }

        onNodeWithTag("library-sort").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-sort-menu").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { assertTrue(AppKeyboardShortcuts.overlayEscape?.invoke() == true) }
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-sort-menu").fetchSemanticsNodes().isEmpty() }
        runOnIdle { assertEquals("watchlist", harness.opened, "Esc closed the menu, not the list") }
    }

    @Test
    fun cloudItemWithSeveralFilesOpensASidePanel() = libraryTest { harness ->
        onNodeWithTag("library-source-Cloud").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-cloud-list").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("library-cloud-row-rd:Torrent:show").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-cloud-panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("library-cloud-list").assertIsDisplayed()
        waitForIdle()
        snapshot("cloud-panel")
        onNodeWithTag("library-cloud-file-e2").performClick()
        runOnIdle { assertEquals("e2", harness.played) }

        runOnIdle { assertTrue(AppKeyboardShortcuts.overlayEscape?.invoke() == true) }
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-cloud-panel").fetchSemanticsNodes().isEmpty() }

        // A single-file item plays directly.
        onNodeWithTag("library-cloud-row-rd:Torrent:movie").performClick()
        runOnIdle { assertEquals("m1", harness.played) }
    }

    @Test
    fun failedServerKeepsARetryTile() = libraryTest { harness ->
        onNodeWithTag("library-source-Servers").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("library-message-action").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("library-message-action").performClick()
        runOnIdle { assertEquals(1, harness.serverRetries) }
    }

    // ─── Harness ────────────────────────────────────────────────────────────────────────────

    private class Harness {
        var loaded by mutableStateOf(true)
        var mode by mutableStateOf(LibraryViewMode.Saved)
        var layout by mutableStateOf(LibraryLayoutMode.HORIZONTAL)
        var opened by mutableStateOf<String?>(null)
        var section by mutableStateOf<String?>(null)
        var type by mutableStateOf<String?>(null)
        var sort by mutableStateOf(LibrarySortOption.DEFAULT)
        var cloudItem by mutableStateOf<String?>(null)
        var played: String? = null
        var serverRetries by mutableIntStateOf(0)
    }

    private fun libraryTest(block: ComposeUiTest.(Harness) -> Unit) = runDesktopComposeUiTest(width = 1440, height = 960) {
        assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
        val harness = Harness()
        setContent { AppEnvironment { LibraryHarness(harness) } }
        waitForIdle()
        try {
            block(harness)
        } finally {
            AppKeyboardShortcuts.overlayEscape = null
        }
    }

    @Composable
    private fun LibraryHarness(h: Harness) {
        val sections = librarySections()
        val ui = LibraryUiState(
            sourceMode = LibrarySourceMode.TRAKT,
            items = sections.flatMap { it.items }.distinctBy { it.id },
            sections = if (h.loaded) sections else emptyList(),
            isLoaded = h.loaded,
        )
        val listOpened = h.opened != null && h.layout == LibraryLayoutMode.HORIZONTAL
        val projection = buildLibraryVerticalProjection(
            sections = ui.sections,
            sourceMode = ui.sourceMode,
            selectedSectionKey = if (listOpened) h.opened else h.section,
            selectedType = h.type,
            sortOption = h.sort,
        )
        val display = ui.sections.map { section ->
            LibraryDisplaySection(
                source = section,
                type = section.type,
                displayTitle = section.displayTitle,
                previewEntries = section.items.map { LibraryDisplayEntry(librarySectionItemKey(section.type, it), it, section, exiting = false) },
            )
        }
        LibraryDesktopScreen(
            model = LibraryDesktopModel(
                viewMode = h.mode,
                showServers = true,
                uiState = ui,
                layoutMode = h.layout,
                sortOption = h.sort,
                displaySections = display,
                projection = projection,
                openedListKey = h.opened.takeIf { listOpened },
                watchedFilter = LibraryWatchedFilter.ALL,
                watchedKeys = emptySet(),
                fullyWatchedSeriesKeys = emptySet(),
                posterPattern = "",
                cloud = cloudState(),
                cloudProviderId = null,
                cloudType = null,
                cloudQuery = "",
                cloudItemKey = h.cloudItem,
                serverShelves = serverShelves(),
                networkCondition = NetworkCondition.Online,
                offline = false,
            ),
            actions = LibraryDesktopActions(
                selectViewMode = { h.mode = it; h.opened = null },
                toggleLayout = {
                    h.layout = if (h.layout == LibraryLayoutMode.HORIZONTAL) LibraryLayoutMode.VERTICAL else LibraryLayoutMode.HORIZONTAL
                    h.opened = null
                },
                openList = { h.opened = it.type },
                closeList = { h.opened = null },
                selectSection = { if (listOpened) h.opened = it else h.section = it },
                selectType = { h.type = it },
                selectGenre = {},
                selectYear = {},
                selectWatched = {},
                selectSort = { h.sort = it },
                retry = {},
                onPosterClick = {},
                onPosterLongClick = { _, _ -> },
                onDisintegrated = {},
                onDownloadsClick = {},
                cloudSelectProvider = {},
                cloudSelectType = {},
                cloudQueryChange = {},
                cloudSelectItem = { item ->
                    if (item.playableFiles.size == 1) h.played = item.playableFiles.first().stableKey
                    else h.cloudItem = item.stableKey
                },
                cloudPlayFile = { _, file -> h.played = file.stableKey },
                cloudCloseItem = { h.cloudItem = null },
                cloudRefresh = {},
                onConnectCloudClick = {},
                onCatalogClick = {},
                onPreviewClick = {},
                onPreviewLongClick = {},
                serversRetry = { h.serverRetries++ },
            ),
            shelvesState = rememberLazyListState(),
            topChromePadding = null,
            scrollToTopRequests = emptyFlow(),
        )
    }

    private fun ComposeUiTest.snapshot(name: String) {
        waitForIdle()
        val roots = onAllNodes(isRoot())
        ImageIO.write(roots[0].captureToImage().toAwtImage(), "png", File(reports, "$name.png"))
        // An open menu is its own popup root.
        if (roots.fetchSemanticsNodes().size > 1) ImageIO.write(roots[1].captureToImage().toAwtImage(), "png", File(reports, "$name-popup.png"))
    }

    private fun assertBoundsEqual(expected: Map<String, Rect>, actual: Map<String, Rect>, step: String) {
        expected.forEach { (tag, rect) ->
            val now = assertNotNull(actual[tag])
            assertTrue(
                kotlin.math.abs(rect.left - now.left) < 1f && kotlin.math.abs(rect.top - now.top) < 1f && kotlin.math.abs(rect.width - now.width) < 1f,
                "$tag moved on $step: $rect → $now",
            )
        }
    }

    private fun librarySections(): List<LibrarySection> {
        val titles = listOf(
            "Dune: Part Two" to 2024, "Severance" to 2022, "The Bear" to 2022, "Oppenheimer" to 2023, "Shōgun" to 2024,
            "Past Lives" to 2023, "Andor" to 2022, "Poor Things" to 2023, "Fallout" to 2024, "The Holdovers" to 2023,
            "Arcane" to 2021, "Anatomy of a Fall" to 2023, "Slow Horses" to 2022, "Civil War" to 2024,
        )
        fun item(index: Int, list: String): LibraryItem {
            val (name, year) = titles[index % titles.size]
            return LibraryItem(
                id = "tt$index",
                type = if (index % 3 == 1) "series" else "movie",
                name = name,
                releaseInfo = year.toString(),
                imdbRating = "8.${index % 9}",
                genres = listOf(if (index % 2 == 0) "Drama" else "Sci-Fi"),
                description = "A fixture title for the Library redesign test.",
                banner = "https://invalid.test/banner-$index.jpg",
                listKeys = setOf(list),
                savedAtEpochMs = 1_700_000_000_000L + index * 1_000L,
            )
        }
        return listOf(
            LibrarySection("watchlist", "Watchlist", (0 until 12).map { item(it, "watchlist") }),
            LibrarySection("favorites", "Favorites", (4 until 11).map { item(it, "favorites") }),
            LibrarySection("collection", "Collection", (2 until 9).map { item(it, "collection") }),
        )
    }

    private fun cloudState(): CloudLibraryUiState {
        val provider = DebridProvider(id = "rd", displayName = "Real-Debrid", shortName = "RD")
        val items = listOf(
            CloudLibraryItem("rd", "Real-Debrid", "movie", CloudLibraryItemType.Torrent, "Dune.Part.Two.2024.2160p.WEB-DL.mkv", sizeBytes = 24_000_000_000L,
                files = listOf(CloudLibraryFile("m1", "Dune.Part.Two.2024.2160p.WEB-DL.mkv", 24_000_000_000L))),
            CloudLibraryItem("rd", "Real-Debrid", "show", CloudLibraryItemType.Torrent, "Severance S02 1080p", sizeBytes = 21_600_000_000L,
                files = (1..6).map { CloudLibraryFile("e$it", "Severance.S02E0$it.1080p.mkv", 2_700_000_000L) }),
        )
        return CloudLibraryUiState(isLoaded = true, providers = listOf(CloudLibraryProviderState(provider, items = items)))
    }

    private fun serverShelves(): List<LibraryServerShelf> {
        val plex = ServerConnection("plex", "plex", "Home Plex", "http://invalid.test", "r", "u", "user", "cred",
            libraries = listOf(ServerLibrary("1", "Movies", ServerMediaKind.MOVIE)))
        val jelly = plex.copy(id = "jf", providerId = "jellyfin", name = "Jellyfin", libraries = listOf(ServerLibrary("2", "Shows", ServerMediaKind.SERIES)))
        val movies = ServerLibraryRef(plex, plex.libraries.first())
        val section = HomeCatalogSection(
            key = "server:plex:1", title = "Home Plex · Movies", subtitle = "", addonName = "Home Plex",
            target = movies.target as CatalogTarget,
            items = librarySections().first().items.map { it.toMetaPreview() },
        )
        return listOf(
            LibraryServerShelf(movies, section = section),
            LibraryServerShelf(ServerLibraryRef(jelly, jelly.libraries.first()), failure = ServerFailure.UNREACHABLE),
        )
    }
}
