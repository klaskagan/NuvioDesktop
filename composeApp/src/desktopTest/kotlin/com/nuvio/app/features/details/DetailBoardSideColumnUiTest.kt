package com.nuvio.app.features.details

import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import com.nuvio.app.features.details.components.BoardPopoverPlacement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.details.components.DetailBoardTileBottomPadding
import com.nuvio.app.features.details.components.DetailSecondaryAction
import com.nuvio.app.features.details.components.DetailYourWayTile
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watched.WatchedRepository
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.height
import androidx.compose.ui.semantics.getOrNull

/** The board's side column: the Your-way grid, and tiles that never run past their bottom padding. */
@OptIn(ExperimentalTestApi::class)
class DetailBoardSideColumnUiTest {
    @Test
    fun yourWayIsAThreeColumnGridInTheGivenOrderThatReflows() = runDesktopComposeUiTest(width = 600, height = 400) {
        val labels = listOf("On TV", "Watch party", "Download", "Shuffle", "Sources", "Lists")
        val shown = mutableStateOf(labels)
        setContent {
            AppEnvironment {
                DetailYourWayTile(
                    shown.value.map { DetailSecondaryAction(label = it, icon = Icons.Rounded.Star, tag = "cell-$it") },
                    Modifier.width(352.dp),
                )
            }
        }
        val bounds = labels.associateWith { onNodeWithTag("cell-$it").getUnclippedBoundsInRoot() }
        // Row one: On TV, Watch party, Download, left to right; row two below it.
        assertEquals(bounds.getValue("On TV").top, bounds.getValue("Download").top)
        assertTrue(bounds.getValue("On TV").left < bounds.getValue("Watch party").left)
        assertTrue(bounds.getValue("Watch party").left < bounds.getValue("Download").left)
        assertTrue(bounds.getValue("Shuffle").top > bounds.getValue("On TV").bottom)
        assertEquals(46.dp, bounds.getValue("On TV").height)
        val fullHeight = onNodeWithTag("detail_board_your_way").getUnclippedBoundsInRoot().height

        // Unavailable actions are left out and the grid closes up to one row.
        runOnIdle { shown.value = listOf("Shuffle", "Lists") }
        waitForIdle()
        mainClock.advanceTimeBy(600)
        onNodeWithTag("cell-On TV").assertDoesNotExist()
        assertTrue(onNodeWithTag("detail_board_your_way").getUnclippedBoundsInRoot().height < fullHeight)
    }

    @Test
    fun aYourWayPopoverOpensBesideTheSideColumnAndClosesOnTheDim() = runDesktopComposeUiTest(width = 1360, height = 850) {
        setContent {
            AppEnvironment {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    DetailYourWayTile(
                        listOf(
                            DetailSecondaryAction(
                                label = "Lists",
                                icon = Icons.Rounded.Star,
                                tag = "cell-lists",
                                popupPlacement = BoardPopoverPlacement.Beside,
                                popup = { dismiss ->
                                    androidx.compose.foundation.layout.Box(Modifier.size(400.dp, 300.dp).testTag("lists-popover"))
                                },
                            ),
                        ),
                        Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(top = 400.dp, end = 30.dp).width(352.dp),
                    )
                }
            }
        }
        onNodeWithTag("lists-popover").assertDoesNotExist()
        onNodeWithTag("cell-lists").performClick()
        mainClock.advanceTimeBy(400)
        val cell = onNodeWithTag("cell-lists").getUnclippedBoundsInRoot()
        val popover = onNodeWithTag("lists-popover", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // Just left of the action itself (as in the frame), not left of the whole column.
        assertTrue(popover.right <= cell.left && popover.right >= cell.left - 16.dp, "popover ${popover.right} should end just left of Lists ${cell.left}")
        onNodeWithTag("cell-lists").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))

        onNodeWithTag("detail_board_popover_dim", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(20f, 20f)) }
        mainClock.advanceTimeBy(600)
        waitForIdle()
        onNodeWithTag("lists-popover", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun seriesSideTilesKeepTheirBottomPadding() = checkSideTiles(
        MetaDetails(
            "side-column-series",
            "series",
            "Side column series",
            videos = (1..8).flatMap { season ->
                (1..2).map { episode ->
                    MetaVideo("side-column-series:$season:$episode", "Episode $season.$episode with a long title", "2020-0$season-01", season = season, episode = episode)
                }
            },
        ),
        listOf("detail_board_up_next", "detail_board_progress", "detail_board_your_way"),
    )

    @Test
    fun movieSideTilesKeepTheirBottomPadding() = checkSideTiles(
        MetaDetails(
            "side-column-movie",
            "movie",
            "Side column movie",
            runtime = "166 min",
            collectionName = "Fixture Collection",
            collectionItems = listOf(
                MetaPreview("side-column-movie", "movie", "Side column movie with a title that wraps", releaseInfo = "2021"),
                MetaPreview("side-column-movie-2", "movie", "The second film", releaseInfo = "2024"),
                MetaPreview("side-column-movie-3", "movie", "The third film", releaseInfo = "2026"),
            ),
        ),
        listOf("detail_board_collection", "detail_board_tonight", "detail_board_your_way"),
    )

    private fun checkSideTiles(meta: MetaDetails, tiles: List<String>) = runDesktopComposeUiTest(width = 1360, height = 850) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        MetaDetailsRepository.clear()
        seedDetails(meta)
        try {
            setContent { AppEnvironment { MetaDetailsScreen(meta.type, meta.id, onBack = {}) } }
            waitUntil(timeoutMillis = 10_000) { WatchedRepository.uiState.value.isLoaded }
            // Let the content crossfade and the column's enter animation settle before measuring.
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            val padding = DetailBoardTileBottomPadding
            tiles.forEach { tag ->
                val tile = onNodeWithTag(tag).assertExists().getUnclippedBoundsInRoot()
                val texts = onAllNodes(hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                assertTrue(texts.isNotEmpty(), "$tag has no text")
                val visible = onAllNodes(hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                texts.indices.forEach { index ->
                    val node = texts[index]
                    // Clipped bounds: rows scrolled out of a tile's own scroller are not overflow.
                    val clipped = visible[index].getBoundsInRoot()
                    if (clipped.height <= 0.dp) return@forEach
                    val bottom = clipped.bottom
                    assertTrue(
                        bottom <= tile.bottom - padding + 0.5.dp,
                        "$tag (${tile.top}–${tile.bottom}): \"${node.config.getOrNull(SemanticsProperties.Text)}\" ends at $bottom, past ${tile.bottom - padding}",
                    )
                }
            }
        } finally {
            MetaDetailsRepository.clear()
        }
    }

    private fun seedDetails(meta: MetaDetails) {
        val field = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = field.get(MetaDetailsRepository) as MutableMap<String, Any>
        val constructor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry")
            .declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
        cache["${meta.type}:${meta.id}"] = constructor.newInstance(meta, null, null)
    }
}
