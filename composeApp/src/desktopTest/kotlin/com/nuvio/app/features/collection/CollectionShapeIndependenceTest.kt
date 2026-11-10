package com.nuvio.app.features.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.home.HomeRowStyle
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCollectionRowSection
import com.nuvio.app.features.home.components.LocalLandscapeArtworkLoader
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class CollectionShapeIndependenceTest {
    private fun artwork(): String {
        val real = File("/tmp/nuvio-landscape-art/dune.jpg")
        if (real.exists()) return real.toURI().toString()
        val file = File.createTempFile("collection-shape-", ".png").apply { deleteOnExit() }
        ImageIO.write(java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", file)
        return file.toURI().toString()
    }
    private fun ComposeUiTest.save(name: String) {
        val dir = File("build/reports/collection-shape").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }
    @Test fun customCollectionCoverShapesStayIndependentOfMediaStyleIncludingImportedDecades() = runDesktopComposeUiTest(width = 1200, height = 950) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setWidthDp(100)
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        val art = artwork()
        val collections = listOf(
            Collection("decades-community", "By Decade", folders = listOf(CollectionFolder("20s", "2020s Movies", coverImageUrl = art, tileShape = "POSTER"))),
            Collection("studios-community", "Studios", folders = listOf(CollectionFolder("warner", "Warner Bros.", coverImageUrl = art, tileShape = "LANDSCAPE"))),
            Collection("personal", "Personal", folders = listOf(CollectionFolder("personal", "Personal portrait", coverImageUrl = art, tileShape = "POSTER"))),
        )
        setContent { AppEnvironment { CompositionLocalProvider(LocalDensity provides Density(1f)) {
            Column { collections.forEach { HomeCollectionRowSection(it, animateGifs = false) } }
        } } }
        fun ratio(name: String): Float {
            val bounds = onNodeWithContentDescription(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            return bounds.width / bounds.height
        }
        for (mode in listOf(false, true, false)) {
            runOnIdle { PosterCardStyleRepository.setCatalogLandscapeModeEnabled(mode) }
            waitForIdle()
            assertTrue(ratio("2020s Movies") > 1.7f, "Decade covers must stay landscape in either media style")
            assertTrue(ratio("Warner Bros.") > 1.7f)
            assertTrue(ratio("Personal portrait") < 0.8f, "Personal collections keep their configured shape too")
            save(if (mode) "media-landscape-covers-unchanged" else "media-portrait-covers-unchanged")
        }
    }

    @Test fun roundStyleClipsCollectionArtworkToACircle() = runDesktopComposeUiTest(width = 620, height = 320) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val file = File.createTempFile("collection-round-", ".png").apply { deleteOnExit() }
        val art = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = art.createGraphics()
        graphics.color = java.awt.Color.RED
        graphics.fillRect(0, 0, art.width, art.height)
        graphics.dispose()
        ImageIO.write(art, "png", file)

        val studio = CollectionFolder("studio", "Studio cover", coverImageUrl = file.toURI().toString(),
            tileShape = "SQUARE", hideTitle = true)
        val collection = Collection("round-studios", "Studios", folders = listOf(studio))
        var style by mutableStateOf(HomeRowStyle.Round)
        setContent {
            AppEnvironment {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    HomeCollectionRowSection(collection, rowStyle = style)
                }
            }
        }

        val cover = onNodeWithContentDescription(studio.title, useUnmergedTree = true)
        fun capture() = cover.captureToImage().toAwtImage()
        val red = java.awt.Color.RED.rgb
        waitUntil(timeoutMillis = 10_000) {
            val image = capture()
            image.getRGB(image.width / 2, image.height / 2) == red
        }
        val round = capture()
        assertEquals(round.width, round.height)
        assertEquals(java.awt.Color.BLACK.rgb, round.getRGB(round.width / 10, round.height / 10))
        save("round-studio-cover")

        runOnIdle { style = HomeRowStyle.Automatic }
        val square = capture()
        assertEquals(red, square.getRGB(square.width / 10, square.height / 10))
    }

    @Test fun customDecadeContentKeepsWideCardsWhenMediaStyleChanges() = runDesktopComposeUiTest(width = 1100, height = 850) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        PosterCardStyleRepository.setWidthDp(100)
        val art = artwork()
        val folder = CollectionFolder("20s", "2020s Movies", heroBackdropUrl = art)
        val state = FolderDetailUiState(folder = folder, artworkFolder = folder, collectionId = "decades-community", collectionTitle = "By Decade", isLoading = false,
            tabs = listOf(FolderTab("Popular", items = (0..9).map { MetaPreview("movie-$it", "movie", "Movie $it", poster = art, banner = art) }, isLoading = false)))
        setContent { AppEnvironment { CompositionLocalProvider(LocalDensity provides Density(1f), LocalLandscapeArtworkLoader provides { _, _, _ -> TmdbLandscapeArtwork(art, null, art) }) {
            ConfiguredFolderContent(state, emptyList(), emptySet(), {}, {}, {}, {}, {})
        } } }
        onNodeWithTag("collection_grid").performScrollToIndex(1)
        for (mode in listOf(false, true, false)) {
            runOnIdle { PosterCardStyleRepository.setCatalogLandscapeModeEnabled(mode) }
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("Movie 0").fetchSemanticsNodes().isNotEmpty() }
            val bounds = onNodeWithContentDescription("Movie 0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width / bounds.height > 1.7f)
            save(if (mode) "decade-content-landscape-mode" else "decade-content-portrait-mode")
        }
    }
}
