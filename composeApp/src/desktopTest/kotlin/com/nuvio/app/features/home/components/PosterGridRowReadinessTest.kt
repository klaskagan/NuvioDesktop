package com.nuvio.app.features.home.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import kotlinx.coroutines.CompletableDeferred
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class PosterGridRowReadinessTest {
    private fun artwork(): String {
        val real = File("/tmp/nuvio-landscape-art/dune.jpg")
        if (real.isFile) return real.toURI().toString()
        val file = File.createTempFile("grid-row-art-", ".png").apply { deleteOnExit() }
        val image = BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = Color(60, 90, 120); fillRect(0, 0, 320, 180); dispose() }
        ImageIO.write(image, "png", file)
        return file.toURI().toString()
    }

    @Test fun visualRowWaitsForLastCardAndResetsWhenItsContentsChange() = runDesktopComposeUiTest(width = 1000, height = 260) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        PosterCardStyleRepository.setHideLabelsEnabled(true)
        val image = artwork()
        val firstGate = CompletableDeferred<Unit>()
        val replacementGate = CompletableDeferred<Unit>()
        val requested = ConcurrentHashMap.newKeySet<String>()
        fun item(id: String) = MetaPreview(id, "movie", "Grid $id", banner = image, posterShape = PosterShape.Landscape)
        val folder = MetaPreview("folder", "collection", "Folder artwork", poster = image, posterShape = PosterShape.Square)
        var row by mutableStateOf(listOf(item("fast-a"), item("fast-b"), item("slow-a"), folder))
        setContent {
            AppEnvironment {
                CompositionLocalProvider(LocalLandscapeArtworkLoader provides { id, _, _ ->
                    requested.add(id)
                    if (id == "slow-a") firstGate.await()
                    if (id == "slow-b") replacementGate.await()
                    TmdbLandscapeArtwork(image, null, image)
                }) { PosterGridRow(items = row, columns = 4) }
            }
        }
        try {
            waitUntil(timeoutMillis = 10_000) { requested.containsAll(listOf("fast-a", "fast-b", "slow-a")) }
            onAllNodesWithContentDescription("Grid fast-a").assertCountEquals(0)
            onAllNodesWithContentDescription("Grid fast-b").assertCountEquals(0)
            onAllNodesWithContentDescription("Grid slow-a").assertCountEquals(0)
            // A collection-art card retains its existing independent rendering and requests no metadata.
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("Folder artwork").fetchSemanticsNodes().isNotEmpty() }
            assertFalse("folder" in requested)
            runOnIdle { firstGate.complete(Unit) }
            waitUntil(timeoutMillis = 10_000) {
                listOf("fast-a", "fast-b", "slow-a").all { onAllNodesWithContentDescription("Grid $it").fetchSemanticsNodes().isNotEmpty() }
            }
            runOnIdle { row = listOf(item("fast-a"), item("fast-b"), item("slow-b"), folder) }
            waitUntil(timeoutMillis = 10_000) { "slow-b" in requested }
            onAllNodesWithContentDescription("Grid fast-a").assertCountEquals(0)
            onAllNodesWithContentDescription("Grid fast-b").assertCountEquals(0)
            runOnIdle { replacementGate.complete(Unit) }
            waitUntil(timeoutMillis = 10_000) {
                listOf("fast-a", "fast-b", "slow-b").all { onAllNodesWithContentDescription("Grid $it").fetchSemanticsNodes().isNotEmpty() }
            }
        } finally {
            firstGate.complete(Unit)
            replacementGate.complete(Unit)
        }
    }
}
