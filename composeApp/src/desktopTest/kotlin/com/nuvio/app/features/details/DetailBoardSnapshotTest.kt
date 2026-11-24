package com.nuvio.app.features.details

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watched.WatchedRepository
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Renders the board at the design doc's 1360 × 850 for a look; only with NUVIO_BOARD_SNAPSHOTS=<dir>. */
@OptIn(ExperimentalTestApi::class)
class DetailBoardSnapshotTest {
    @Test fun seriesAndFilmBoards() {
        val dir = System.getenv("NUVIO_BOARD_SNAPSHOTS")
        assumeTrue(dir != null)
        val series = MetaDetails(
            "snapshot-series", "series", "Severance",
            description = "Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives.",
            releaseInfo = "2022-",
            cast = listOf(MetaPerson("Dan Erickson", "Creator"), MetaPerson("Adam Scott", "Mark S.", episodeCount = 19), MetaPerson("Britt Lower", "Helly R.", episodeCount = 19), MetaPerson("Zach Cherry", "Dylan G."), MetaPerson("Alexander Skarsgård-Pettersson", "A guest with a long role name")),
            videos = (1..2).flatMap { s -> (1..9).map { e -> MetaVideo("snapshot-series:$s:$e", "Episode $e of season $s", "202${1 + s}-0$s-0$e", season = s, episode = e) } },
        )
        val film = MetaDetails(
            "snapshot-film", "movie", "Dune: Part Two", runtime = "166 min", director = listOf("Denis Villeneuve"),
            description = "Paul Atreides unites with Chani and the Fremen while on a path of revenge against the conspirators who destroyed his family.",
            cast = listOf(MetaPerson("Timothée Chalamet", "Paul Atreides"), MetaPerson("Zendaya", "Chani"), MetaPerson("Rebecca Ferguson", "Jessica")),
            collectionName = "Dune", collectionItems = listOf(MetaPreview("dune-1", "movie", "Dune", releaseInfo = "2021"), MetaPreview("snapshot-film", "movie", "Dune: Part Two", releaseInfo = "2024")),
        )
        listOf(series, film).forEach { meta ->
            MetaDetailsRepository.clear()
            val field = MetaDetailsRepository.javaClass.getDeclaredField("cachedMetaByRequestKey").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val cache = field.get(MetaDetailsRepository) as MutableMap<String, Any>
            val constructor = Class.forName("com.nuvio.app.features.details.MetaDetailsRepository\$CachedMetaEntry").declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
            cache["${meta.type}:${meta.id}"] = constructor.newInstance(meta, null, null)
            runDesktopComposeUiTest(width = 1360, height = 850) {
                setContent { AppEnvironment { MetaDetailsScreen(meta.type, meta.id, onBack = {}, onPlayManually = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }, onDownload = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }) } }
                waitUntil(timeoutMillis = 10_000) { WatchedRepository.uiState.value.isLoaded }
                mainClock.advanceTimeBy(2_000); waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "${meta.id}.png"))
                onNodeWithText("Cast & crew").performClick()
                mainClock.advanceTimeBy(1_000); waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "${meta.id}-cast.png"))
            }
        }
        MetaDetailsRepository.clear()
    }
}
