package com.nuvio.app.features.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.AppKeyboardShortcuts
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * Desktop Downloads redesign from fixtures: active transfers and finished cards, a show's episodes
 * in place (Esc returns), the ⋯ menu's actions, and the empty state's desktop instructions.
 * Screenshots land in build/reports/downloads-desktop.
 */
@OptIn(ExperimentalTestApi::class)
class DownloadsDesktopUiTest {
    private val reports = File("build/reports/downloads-desktop").apply { mkdirs() }

    @Test
    fun activeAndDownloadedRenderAndShowOpensInPlace() = downloadsTest(fixtures()) { played, details ->
        onNodeWithTag("downloads-row-active-1").assertIsDisplayed()
        onNodeWithTag("downloads-card-movie-1").assertIsDisplayed()
        onNodeWithTag("downloads-transfer-all").assertIsDisplayed()
        snapshot("downloads")

        onNodeWithTag("downloads-card-movie-1").performClick()
        runOnIdle { assertEquals("movie-1", played()) }

        onNodeWithTag("downloads-show-tt-show").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("downloads-show").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("downloads-row-ep-2").assertIsDisplayed()
        snapshot("downloads-show")
        runOnIdle { assertTrue(AppKeyboardShortcuts.overlayEscape?.invoke() == true) }
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("downloads-list").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("downloads-more-active-1").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("downloads-action-details").fetchSemanticsNodes().isNotEmpty() }
        snapshot("downloads-menu")
        onNodeWithTag("downloads-action-details").performClick()
        runOnIdle { assertEquals("active-1", details()) }

        // The folder menu: open or change the folder; "Use default" only once one was chosen.
        onNodeWithText("Saving to", substring = true).assertIsDisplayed()
        onNodeWithTag("downloads-folder").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("downloads-folder-change").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("downloads-folder-open").assertExists()
        onNodeWithTag("downloads-folder-reset").assertDoesNotExist()
        snapshot("downloads-folder-menu")
        val chosen = java.nio.file.Files.createTempDirectory("nuvio-dl").toFile()
        DownloadsFolder.picker = { chosen }
        onNodeWithTag("downloads-folder-change").performClick()
        waitUntil(timeoutMillis = 3_000) { DownloadsFolder.customPath.value == chosen.absolutePath }
        onNodeWithText(chosen.name, substring = true).assertIsDisplayed()
        onNodeWithTag("downloads-folder").performClick()
        waitUntil(timeoutMillis = 3_000) { onAllNodesWithTag("downloads-folder-reset").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("downloads-folder-reset").performClick()
        waitUntil(timeoutMillis = 3_000) { DownloadsFolder.customPath.value == null }
    }

    @Test
    fun emptyStateTellsDesktopHowToDownload() = downloadsTest(emptyList()) { _, _ ->
        onNodeWithTag("downloads-empty").assertIsDisplayed()
        onNodeWithText("right-click Play", substring = true).assertIsDisplayed()
        snapshot("downloads-empty")
    }

    private fun downloadsTest(items: List<DownloadItem>, block: ComposeUiTest.(played: () -> String?, details: () -> String?) -> Unit) =
        runDesktopComposeUiTest(width = 1440, height = 960) {
            assumeTrue(System.getProperty("nuvio.test.isolatedHome") == "true")
            var played by mutableStateOf<String?>(null)
            var details by mutableStateOf<String?>(null)
            setContent {
                AppEnvironment {
                    DownloadsDesktopScreen(
                        onBack = {},
                        onOpenDownload = { played = it.id },
                        onOpenDetails = { details = it.id },
                        fixtureItems = items,
                    )
                }
            }
            waitForIdle()
            try {
                block({ played }, { details })
            } finally {
                AppKeyboardShortcuts.clearOverlayEscapes()
                DownloadsFolder.reset()
            }
        }

    private fun ComposeUiTest.snapshot(name: String) {
        waitForIdle()
        val roots = onAllNodes(isRoot())
        ImageIO.write(roots[0].captureToImage().toAwtImage(), "png", File(reports, "$name.png"))
        if (roots.fetchSemanticsNodes().size > 1) ImageIO.write(roots[1].captureToImage().toAwtImage(), "png", File(reports, "$name-popup.png"))
    }

    private fun fixtures(): List<DownloadItem> {
        val gb = 1_000_000_000L
        fun item(
            id: String,
            title: String,
            status: DownloadStatus,
            season: Int? = null,
            episode: Int? = null,
            episodeTitle: String? = null,
            parent: String = "tt-$id",
            downloaded: Long = 0L,
            total: Long = 4 * gb,
            created: Long = 1_700_000_000_000L,
        ) = DownloadItem(
            id = id,
            contentType = if (season != null) "series" else "movie",
            parentMetaId = parent,
            parentMetaType = if (season != null) "series" else "movie",
            videoId = id,
            title = title,
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = episodeTitle,
            streamTitle = "1080p",
            providerName = "Fixture",
            sourceUrl = "https://invalid.test/$id.mkv",
            fileName = "$id.mkv",
            status = status,
            downloadedBytes = downloaded,
            totalBytes = total,
            createdAtEpochMs = created,
            updatedAtEpochMs = created,
        )
        return listOf(
            item("active-1", "Dune: Part Two", DownloadStatus.Downloading, downloaded = 9 * gb / 4, total = 6 * gb),
            item("active-2", "Past Lives", DownloadStatus.Paused, downloaded = gb, total = 3 * gb),
            item("movie-1", "Oppenheimer", DownloadStatus.Completed, downloaded = 5 * gb, total = 5 * gb, created = 1_700_000_100_000L),
            item("ep-1", "Severance", DownloadStatus.Completed, season = 2, episode = 1, episodeTitle = "Hello, Ms. Cobel", parent = "tt-show", total = 2 * gb),
            item("ep-2", "Severance", DownloadStatus.Completed, season = 2, episode = 2, episodeTitle = "Goodbye, Mrs. Selvig", parent = "tt-show", total = 2 * gb),
            item("ep-3", "Severance", DownloadStatus.Failed, season = 2, episode = 3, episodeTitle = "Who Is Alive?", parent = "tt-show", total = 2 * gb),
        )
    }
}
