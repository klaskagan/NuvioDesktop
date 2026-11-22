package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import com.nuvio.app.features.tracking.WatchProgressSource
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.catalog.*
import com.nuvio.app.core.network.NetworkCondition
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.nuvio.app.features.home.*
import com.nuvio.app.features.tmdb.TmdbLandscapeArtwork
import com.nuvio.app.features.tmdb.TmdbImagesResponse
import com.nuvio.app.features.tmdb.selectCleanLandscapeBackdropPath
import com.nuvio.app.features.tmdb.selectLandscapeImagePaths
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class LandscapeMediaCardTest {
    // Real, locally downloaded database art is used for review captures. CI needs no network:
    // small deterministic images still exercise Coil loading/error and title-state behavior.
    private fun asset(name: String): File {
        val real = File("/tmp/nuvio-landscape-art", name)
        if (real.exists()) return real
        return File.createTempFile("landscape-card-", ".png").also { file ->
            file.deleteOnExit()
            val image = java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            graphics.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(30, 45, 60), 320f, 180f, java.awt.Color(120, 100, 80))
            graphics.fillRect(0, 0, 320, 180)
            graphics.dispose()
            ImageIO.write(image, "png", file)
        }
    }
    private val backdrop = asset("dune.jpg").toURI().toString()
    private val otherBackdrop = asset("inception.jpg").toURI().toString()
    private val movie = MetaPreview("fixture-dune", "movie", "Dune", banner = backdrop, landscapePoster = backdrop)
    private fun prepare() {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        PosterCardStyleRepository.setHideLabelsEnabled(true)
        PosterCardStyleRepository.setWidthDp(140)
    }
    private fun ComposeUiTest.save(name: String) {
        val dir = File("build/reports/landscape-media").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }

    private fun ComposeUiTest.waitForArtwork(description: String) {
        waitUntil(timeoutMillis = 10_000) {
            val nodes = onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.isEmpty()) false else {
                val bitmap = onNodeWithContentDescription(description, useUnmergedTree = true).captureToImage().toAwtImage()
                val colors = mutableSetOf<Int>()
                for (x in 0 until bitmap.width step 8) for (y in 0 until bitmap.height / 2 step 8) colors.add(bitmap.getRGB(x, y))
                colors.size > 16
            }
        }
    }

    @Test fun cleanBackdropKeepsTextWithMissingOrFailedLogoAndDoesNotAddCaptions() = runDesktopComposeUiTest(width = 1100, height = 450) {
        prepare()
        var selected = ""
        setContent { AppEnvironment { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { id, _, _ ->
            TmdbLandscapeArtwork(backdrop, if (id == "failed") "file:///missing-logo.png" else null, backdrop)
        }) { Row {
            HomePosterCard(movie, onClick = { selected = movie.id }, isWatched = true)
            HomePosterCard(movie.copy(id = "failed", name = "Dune: Part Two"))
        } } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Dune").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Dune").assertIsDisplayed()
        onNodeWithText("Dune: Part Two").assertIsDisplayed()
        val text = onNodeWithText("Dune").fetchSemanticsNode().boundsInRoot
        val image = onNodeWithContentDescription("Dune", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(text.bottom <= image.bottom && text.top >= image.top, "Title must remain inside artwork")
        waitForArtwork("Dune")
        save("clean-text-and-failed-logo")
        onNodeWithContentDescription("Dune").performClick()
        runOnIdle { assertEquals(movie.id, selected) }
    }

    @Test fun missingOrFailedCleanBackdropPreservesOriginalWithoutDuplicateTitle() = runDesktopComposeUiTest(width = 1000, height = 450) {
        prepare()
        setContent { AppEnvironment { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { id, _, _ ->
            if (id == "bad-backdrop") TmdbLandscapeArtwork(null, null, "file:///missing-backdrop.jpg") else null
        }) { Row {
            HomePosterCard(movie.copy(logo = asset("dune-logo.png").toURI().toString()))
            HomePosterCard(movie.copy(id = "bad-backdrop", name = "Original artwork"))
        } } } }
        waitForIdle()
        onNodeWithText("Dune").assertDoesNotExist()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Original artwork").fetchSemanticsNodes().isEmpty() }
        onNodeWithContentDescription("Dune", useUnmergedTree = true).assertExists()
        waitForArtwork("Dune")
        waitForArtwork("Original artwork")
        onNodeWithText("Original artwork").assertDoesNotExist()
        save("unknown-original-artwork")
    }

    @Test fun recycledCardRejectsLateArtworkFromPreviousMovie() = runDesktopComposeUiTest(width = 600, height = 450) {
        prepare()
        var current by mutableStateOf(movie)
        val first = CompletableDeferred<TmdbLandscapeArtwork?>()
        val second = CompletableDeferred<TmdbLandscapeArtwork?>()
        setContent { AppEnvironment { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { id, _, _ ->
            if (id == movie.id) first.await() else second.await()
        }) { HomePosterCard(current) } } }
        runOnIdle { current = movie.copy(id = "second", name = "Inception", banner = otherBackdrop, landscapePoster = otherBackdrop) }
        runOnIdle { first.complete(TmdbLandscapeArtwork(backdrop, null, backdrop)) }
        onNodeWithText("Dune").assertDoesNotExist()
        onNodeWithText("Inception").assertDoesNotExist()
        runOnIdle { second.complete(TmdbLandscapeArtwork(otherBackdrop, null, otherBackdrop)) }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Inception").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Inception").assertIsDisplayed()
        waitForArtwork("Inception")
        save("recycled-card")
    }

    @Test fun airingNextNeverRequestsReplacementArtworkEvenWhenRenamed() = runDesktopComposeUiTest(width = 800, height = 450) {
        prepare()
        var calls = 0
        var expanded by mutableStateOf(false)
        val section = HomeCatalogSection("airing", "My schedule", "", "My Lists",
            CatalogTarget.Addon("https://example.invalid/manifest.json", "series", "trakt-user-shows-airing-next"),
            listOf(movie.copy(type = "series")))
        setContent { AppEnvironment { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { _, _, _ -> calls++; error("Excluded") }) {
            if (!expanded) HomeCatalogRowSection(section, onViewAllClick = { expanded = true })
            else CatalogContent(title = section.title, subtitle = "", target = section.target,
                uiState = CatalogUiState(items = section.items), gridState = rememberLazyGridState(),
                watchedKeys = emptySet(), fullyWatchedSeriesKeys = emptySet(), networkCondition = NetworkCondition.Online,
                onBack = { expanded = false }, onRetry = {})
        } } }
        waitForIdle()
        runOnIdle { assertEquals(0, calls) }
        onNodeWithText("Dune").assertDoesNotExist()
        waitForArtwork("Dune")
        save("airing-next-unchanged")
        onNodeWithContentDescription("View All").performClick()
        onNodeWithTag("catalog_grid").assertExists()
        waitForArtwork("Dune")
        runOnIdle { assertEquals(0, calls) }
        onNodeWithText("Dune").assertDoesNotExist()
        save("airing-next-expanded-unchanged")
    }

    @Test fun itemLandscapeAndDiscoverGridUseInsideTitlesWhilePortraitAndNonMediaDoNotFetch() = runDesktopComposeUiTest(width = 1100, height = 500) {
        prepare(); PosterCardStyleRepository.setCatalogLandscapeModeEnabled(false)
        val requests = mutableListOf<String>()
        setContent { AppEnvironment { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { id, _, _ ->
            requests.add(id); TmdbLandscapeArtwork(backdrop, null, backdrop)
        }) { Row {
            PosterGridRow(listOf(movie.copy(posterShape = PosterShape.Landscape)), columns = 1, modifier = Modifier.width(300.dp))
            HomePosterCard(movie.copy(id = "portrait", name = "Portrait", poster = backdrop))
            HomePosterCard(movie.copy(id = "folder", type = "collection", name = "Studio", poster = backdrop))
        } } } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("Dune").fetchSemanticsNodes().isNotEmpty() }
        runOnIdle { assertEquals(listOf(movie.id), requests) }
        onNodeWithText("Dune").assertIsDisplayed()
        waitForArtwork("Dune")
        waitForArtwork("Portrait")
        save("item-landscape-and-portrait")
    }
    @Test fun officialLogoAndBackdropAreRevealedTogether() = runDesktopComposeUiTest(width = 800, height = 450) {
        prepare()
        val logo = asset("dune-logo.png")
        setContent { AppEnvironment { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { _, _, _ ->
            TmdbLandscapeArtwork(backdrop, logo.toURI().toString(), backdrop)
        }) { HomePosterCard(movie, isWatched = true) } } }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithContentDescription("Dune logo", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                onAllNodesWithText("Dune").fetchSemanticsNodes().isEmpty()
        }
        waitForArtwork("Dune")
        save("official-logo")
    }

    @Test fun continueWatchingStylesNeverUseNewArtworkLookup() = runDesktopComposeUiTest(width = 1000, height = 700) {
        prepare()
        var style by mutableStateOf(ContinueWatchingSectionStyle.Wide)
        var calls = 0
        val item = ContinueWatchingItem("cw", "movie", "cw", "Dune", "Movie", backdrop,
            poster = backdrop, background = backdrop, resumePositionMs = 1000, durationMs = 10000, progressFraction = 0.1f)
        setContent { AppEnvironment { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { _, _, _ ->
            calls++; error("Continue Watching is excluded")
        }) { HomeContinueWatchingSection(listOf(item), style, WatchProgressSource.NUVIO_SYNC) } } }
        for (next in ContinueWatchingSectionStyle.entries) {
            runOnIdle { style = next }
            waitForIdle()
            onNodeWithText("Dune").assertExists()
            runOnIdle { assertEquals(0, calls) }
            save("continue-watching-${next.name}")
        }
    }

    @Test fun narrowLargeTextStaysInsideArtwork() = runDesktopComposeUiTest(width = 390, height = 500) {
        prepare()
        val title = "A very long movie title that must remain inside its landscape card"
        setContent { AppEnvironment {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f),
                LocalLandscapeArtworkLoader provides { _, _, _ -> TmdbLandscapeArtwork(backdrop, null, backdrop) }) {
                Box(Modifier.width(350.dp)) { HomePosterCard(movie.copy(name = title), fillWidth = true) }
            }
        } }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        waitForArtwork(title)
        val bounds = onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val image = onNodeWithContentDescription(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= image.left && bounds.right <= image.right && bounds.bottom <= image.bottom)
        save("narrow-large-text")
    }

    @Test fun reportedDecadeAndLibraryTitlesUseLogosOnSharedGridCards() = runDesktopComposeUiTest(width = 1100, height = 650) {
        prepare()
        val examples = listOf(10601 to "Peter Pan", 155 to "The Dark Knight", 1934 to "Shakespeare in Love")
        setContent { AppEnvironment { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { id, _, _ ->
            val number = id.removePrefix("tmdb:")
            val body = checkNotNull(javaClass.getResource("/tmdb/$number-images.json")).readText()
            val images = Json { ignoreUnknownKeys = true }.decodeFromString<TmdbImagesResponse>(body)
            // The real API fixture must select both a clean backdrop and an official logo.
            assertNotNull(images.selectLandscapeImagePaths("en").logo)
            assertNotNull(images.selectCleanLandscapeBackdropPath())
            TmdbLandscapeArtwork(null, asset("$number-logo.png").toURI().toString(), asset("$number-backdrop.png").toURI().toString())
        }) { Column {
            // Collection grids and the Library grid route through PosterGridRow/HomePosterCard.
            PosterGridRow(examples.map { (id, name) -> movie.copy(id = "tmdb:$id", name = name, logo = null) }, columns = 3)
            Row { examples.forEach { (id, name) -> HomePosterCard(movie.copy(id = "tmdb:$id", name = "$name library", logo = null)) } }
        } } } }
        for ((_, name) in examples) for (title in listOf(name, "$name library")) {
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("$title logo", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText(title).assertDoesNotExist()
            waitForArtwork(title)
        }
        save("decade-and-library-logos")
    }

    @Test fun reportedFamilySeriesHaveLogosOnTheirCatalogCards() = runDesktopComposeUiTest(width = 1000, height = 450) {
        prepare()
        setContent { AppEnvironment { CompositionLocalProvider(LocalLandscapeArtworkLoader provides { id, _, _ ->
            val slug = if (id == "yali") "yali" else "chavo"
            TmdbLandscapeArtwork(null, asset("$slug-logo.png").toURI().toString(), asset("$slug-backdrop.png").toURI().toString())
        }) { Row {
            HomePosterCard(movie.copy(id = "yali", name = "Yalı Çapkını"))
            HomePosterCard(movie.copy(id = "chavo", name = "El Chavo del Ocho"))
        } } } }
        for (name in listOf("Yalı Çapkını", "El Chavo del Ocho")) {
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("$name logo", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText(name).assertDoesNotExist()
            waitForArtwork(name)
        }
        save("family-series-logos")
    }

}
