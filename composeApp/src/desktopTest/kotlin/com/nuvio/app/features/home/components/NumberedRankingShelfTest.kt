package com.nuvio.app.features.home.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.core.ui.PosterCardStyleRepository
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.HomeRowStyle
import kotlinx.coroutines.runBlocking
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NumberedRankingShelfTest {
    @Test
    fun pickerPersistsOnlyTheOriginatingShelfAcrossReloadAndRemoteSettings() = runDesktopComposeUiTest {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val repository = HomeCatalogSettingsRepository
        val folder = CollectionFolder(id = "shared-folder", title = "Shared folder")
        val collections = listOf(
            Collection(id = "ranking-fixture-a", title = "First shelf", folders = listOf(folder)),
            Collection(id = "ranking-fixture-b", title = "Second shelf", folders = listOf(folder)),
        )
        val firstKey = "collection_ranking-fixture-a"
        val secondKey = "collection_ranking-fixture-b"

        fun style(key: String) = repository.uiState.value.items.single { it.key == key }.rowStyle

        repository.onProfileChanged()
        runBlocking { repository.syncCollections(collections, addons = emptyList()) }
        repository.setRowStyle(firstKey, HomeRowStyle.Automatic)
        var pickerOpen by mutableStateOf(true)
        try {
            setContent {
                AppEnvironment {
                    if (pickerOpen) HomeRowStylePicker(firstKey, onDismiss = { pickerOpen = false })
                }
            }
            onNodeWithText("Numbered ranking").performClick()
            onNodeWithText("Row style").assertDoesNotExist()

            runOnIdle {
                assertEquals(HomeRowStyle.NumberedRanking, style(firstKey))
                assertEquals(HomeRowStyle.Automatic, style(secondKey))
            }

            val sharedSettings = repository.exportToSyncPayload()
            repository.onProfileChanged()
            runBlocking { repository.syncCollections(collections, addons = emptyList()) }
            repository.applyFromRemote(sharedSettings)
            assertEquals(HomeRowStyle.NumberedRanking, style(firstKey))
            assertEquals(HomeRowStyle.Automatic, style(secondKey))

            runOnIdle { pickerOpen = true }
            onNodeWithText("Numbered ranking").assertIsSelected()
            onNodeWithText("Landscape").performClick()
            onNodeWithText("Medium").assertIsSelected()
            onNodeWithText("Large").performClick()
            runOnIdle {
                assertEquals(HomeRowStyle.LandscapeLarge, style(firstKey))
                pickerOpen = true
            }
            onNodeWithText("Landscape").assertIsSelected().performClick()
            onNodeWithText("Large").assertIsSelected()
            onNodeWithText("Back").performClick()
            onNodeWithText("Automatic").performClick()
            runOnIdle { assertEquals(HomeRowStyle.Automatic, style(firstKey)) }
        } finally {
            repository.setRowStyle(firstKey, HomeRowStyle.Automatic)
            repository.onProfileChanged()
        }
    }

    @Test
    fun explicitStylesControlArtworkGeometryAndBannerLabels() = runDesktopComposeUiTest(width = 1000, height = 420) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
        val art = sequenceOf(File("../nuvio-native-legacy"), File("../../nuvio-native-legacy"))
            .map { it.resolve("deploy/app/art") }
            .firstOrNull(File::isDirectory)
        val entry = MetaPreview(
            id = "style-fixture", type = "movie", name = "Style fixture", genres = listOf("Adventure"),
            poster = art?.resolve("poster/00.jpg")?.toURI()?.toString(),
            banner = art?.resolve("00.jpg")?.toURI()?.toString(),
        )
        val section = HomeCatalogSection(
            key = "fixture:movie:styles",
            title = "Shelf styles",
            subtitle = "",
            addonName = "Fixture",
            target = CatalogTarget.Addon("https://example.com/manifest.json", "movie", "styles"),
            items = listOf(entry),
        )
        var style by mutableStateOf(HomeRowStyle.Posters)
        setContent {
            AppEnvironment {
                HomeCatalogRowSection(section = section, rowStyle = style, onPosterClick = {})
            }
        }

        fun artworkBounds() = onNode(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        val poster = artworkBounds()
        assertTrue(poster.height > poster.width)
        val widths = listOf(HomeRowStyle.LandscapeSmall, HomeRowStyle.LandscapeMedium, HomeRowStyle.LandscapeLarge).map { size ->
            runOnIdle { style = size }
            val wide = artworkBounds()
            assertTrue(wide.width > wide.height)
            wide.width
        }
        assertTrue(widths[0] < widths[1] && widths[1] < widths[2])

        val report = File("build/reports/row-styles").apply { mkdirs() }
        fun capture(label: String) {
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", report.resolve("$label.png"))
        }

        runOnIdle { style = HomeRowStyle.BannerWithTitle }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("Adventure", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Adventure", substring = true).assertIsDisplayed()
        capture("banner")
        runOnIdle { style = HomeRowStyle.Round }
        val round = artworkBounds()
        assertEquals(round.width, round.height, 1f)
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(entry.name).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(entry.name).assertIsDisplayed()
        capture("round")
    }

    @Test
    fun rankedShelfUsesPortraitArtAndKeepsActionsWithDoubleDigitRanks() =
        runDesktopComposeUiTest(width = 1200, height = 420) {
            check(System.getProperty("nuvio.test.isolatedHome") == "true")
            PosterCardStyleRepository.setCatalogLandscapeModeEnabled(true)
            val art = sequenceOf(File("../nuvio-native-legacy"), File("../../nuvio-native-legacy"))
                .map { it.resolve("deploy/app/art/poster") }
                .firstOrNull(File::isDirectory)
            val entries = (0..4).map { index ->
                MetaPreview(
                    id = "fixture-$index",
                    type = "movie",
                    name = "Fixture $index",
                    poster = art?.resolve("%02d.jpg".format(index))?.toURI()?.toString(),
                    posterShape = PosterShape.Landscape,
                    catalogRank = if (index == 1) 12 else null,
                )
            }
            val section = HomeCatalogSection(
                key = "fixture:movie:rank",
                title = "Streaming Top 10 (All Services) - Movie",
                subtitle = "",
                addonName = "Fixture",
                target = CatalogTarget.Addon("https://example.com/manifest.json", "movie", "rank"),
                items = entries,
            )
            var ranked by mutableStateOf(false)
            var opened: String? = null
            setContent {
                AppEnvironment {
                    HomeCatalogRowSection(
                        section = section,
                        rowStyle = if (ranked) HomeRowStyle.NumberedRanking else HomeRowStyle.Automatic,
                        onPosterClick = { opened = it.id },
                    )
                }
            }
            onNodeWithContentDescription("Rank 1").assertDoesNotExist()
            val heading = onNodeWithText(section.title)
            val automaticHeadingBounds = heading.fetchSemanticsNode().boundsInRoot
            runOnIdle { ranked = true }
            assertEquals(automaticHeadingBounds, heading.fetchSemanticsNode().boundsInRoot)
            val firstRank = onNodeWithContentDescription("Rank 1", useUnmergedTree = true)
            val doubleDigit = onNodeWithContentDescription("Rank 12", useUnmergedTree = true)
            firstRank.assertIsDisplayed()
            doubleDigit.assertIsDisplayed()
            val first = firstRank.fetchSemanticsNode().boundsInRoot
            val next = doubleDigit.fetchSemanticsNode().boundsInRoot
            assertTrue(first.right < next.left)
            doubleDigit.performMouseInput { click(center) }
            runOnIdle { assertEquals("fixture-1", opened) }
            val report = File("build/reports/numbered-ranking").apply { mkdirs() }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", report.resolve("desktop.png"))
            runOnIdle { ranked = false }
            onNodeWithContentDescription("Rank 12").assertDoesNotExist()
        }
}
