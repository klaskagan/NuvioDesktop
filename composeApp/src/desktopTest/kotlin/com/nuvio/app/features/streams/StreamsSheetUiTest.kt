package com.nuvio.app.features.streams

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class StreamsSheetUiTest {
    private fun torrent(name: String, description: String, addon: String = "Torrentio") = StreamItem(
        name = name,
        description = description,
        infoHash = name.hashCode().toString(16).padStart(40, '0'),
        addonName = addon,
        addonId = "addon:${addon.lowercase()}",
    )

    private fun cached(name: String, description: String, addon: String = "Torrentio") = torrent(name, description, addon).copy(
        url = "https://example.test/$name.mkv",
        infoHash = null,
        debridCacheStatus = StreamDebridCacheStatus("realdebrid", "Real-Debrid", StreamDebridCacheState.CACHED),
    )

    private val state = StreamsUiState(
        requestToken = "series::tt:2:3::2::3::true",
        groups = listOf(
            AddonStreamGroup(
                addonName = "Torrentio",
                addonId = "addon:torrentio",
                streams = listOf(
                    cached("best4k", "Severance.S02E03.2160p.ATVP.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX\n💾 7.8 GB"),
                    torrent("p2p4k", "Severance.S02E03.2160p.WEB.H265-SuccessfulCrab\n👤 61 💾 5.2 GB"),
                    cached("small1080", "Severance.S02E03.1080p.WEB.h264-ETHEL\n💾 1.4 GB"),
                ),
            ),
            AddonStreamGroup(
                addonName = "Comet",
                addonId = "addon:comet",
                streams = listOf(cached("comet1080", "Severance.S02E03.1080p.ATVP.WEB-DL.DDP5.1.Atmos.H.264-FLUX\n💾 3.9 GB", addon = "Comet")),
            ),
        ),
    )

    @Test
    fun sheetShowsBestMatchDecodedRowsAndFilters() = runDesktopComposeUiTest(width = 1360, height = 850) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val played = mutableListOf<String?>()
        var backs = 0
        setContent {
            AppEnvironment {
                StreamsSheetLayout(
                    isEpisode = true,
                    title = "Severance",
                    background = null,
                    poster = null,
                    episodeThumbnail = null,
                    seasonNumber = 2,
                    episodeNumber = 3,
                    episodeTitle = "Who Is Alive?",
                    uiState = state,
                    debridEnabled = true,
                    resumePositionMs = null,
                    resumeProgressFraction = null,
                    onStreamSelected = { stream, _, _ -> played += stream.name },
                    onStreamSecondaryClick = { _, _ -> },
                    onRefresh = {},
                    onBack = { backs++ },
                )
            }
        }
        waitForIdle()
        val dir = File("build/reports/streams-sheet").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "sheet.png"))

        onNodeWithText("Who Is Alive?").assertIsDisplayed()
        onNodeWithText("4 from 2 addons").assertIsDisplayed()
        // Rounded to the size the addon wrote, on the Best match card and on its row.
        onAllNodesWithText("7.8 GB").assertCountEquals(2)
        // Every source is listed under its resolution, with the release name kept under the labels.
        onAllNodesWithTag("stream_sheet_row").assertCountEquals(4)
        onNodeWithText("Severance.S02E03.2160p.WEB.H265-SuccessfulCrab").assertIsDisplayed()
        onNodeWithText("P2P · 61 seeds").assertIsDisplayed()

        // Best match is the 4K source that starts now, not the 4K torrent.
        onNodeWithTag("streams_sheet_play_best").performClick()
        assertEquals(listOf<String?>("best4k"), played)

        onNodeWithTag("streams_sheet_instant_only").performClick()
        waitForIdle()
        onAllNodesWithTag("stream_sheet_row").assertCountEquals(3)
        onNodeWithText("P2P · 61 seeds").assertDoesNotExist()

        onNodeWithTag("streams_sheet_back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun sheetMarksDownloadsAndLanguagesAndFiltersByLanguage() = runDesktopComposeUiTest(width = 1360, height = 850) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        fun torbox(marker: String, quality: String, description: String) = StreamItem(
            name = "[$marker] Torrentio\n$quality",
            description = description,
            url = "https://torrentio.example/resolve/${description.hashCode()}",
            addonName = "Torrentio TB",
            addonId = "addon:torrentio",
        )
        val readyState = StreamsUiState(
            requestToken = "movie::tt7798634::null::null::true",
            groups = listOf(
                AddonStreamGroup(
                    addonName = "Torrentio TB",
                    addonId = "addon:torrentio",
                    streams = listOf(
                        torbox("TB+", "4k", "Ready.or.Not.2019.2160p.MA.WEB-DL.H.265.DDP5.1-PandaQT SDR 4k UHD [GoodFilms]\n👤 17 💾 16.72 GB ⚙️ ThePirateBay"),
                        torbox(
                            "TB download", "BDRip",
                            "Я иду искать / Ready or Not [2019 BDRip-AVC] Dub (Rus) + Dub (Ukr) + Original (Eng) + Sub (Rus Ukr Eng)\n👤 36 💾 2.19 GB ⚙️ Rutracker\n🇬🇧 / 🇷🇺 / 🇺🇦",
                        ),
                    ),
                ),
            ),
        )
        StreamBadgeSettingsRepository.setSheetLanguage(null)
        try {
            setContent {
                AppEnvironment {
                    StreamsSheetLayout(
                        isEpisode = false,
                        title = "Ready or Not",
                        background = null,
                        poster = null,
                        episodeThumbnail = null,
                        seasonNumber = null,
                        episodeNumber = null,
                        episodeTitle = null,
                        uiState = readyState,
                        debridEnabled = true,
                        resumePositionMs = null,
                        resumeProgressFraction = null,
                        onStreamSelected = { _, _, _ -> },
                        onStreamSecondaryClick = { _, _ -> },
                        onRefresh = {},
                        onBack = {},
                    )
                }
            }
            waitForIdle()
            val dir = File("build/reports/streams-sheet").apply { mkdirs() }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "sheet-languages.png"))

            // The addon's markers show: one source starts now, the other must download first.
            onAllNodesWithText("Instant · TB").assertCountEquals(2) // the row and the Best match card
            onNodeWithText("Needs download · TB").assertIsDisplayed()
            onNodeWithText("RU Dub").assertIsDisplayed()
            onNodeWithText("Subs RU UK EN").assertIsDisplayed()
            onAllNodesWithTag("stream_sheet_row").assertCountEquals(2)

            // Picking a language must not move the pills: they used to reorder around the selection.
            fun pillLefts() = listOf("EN", "RU", "UK").map { onNodeWithTag("streams_sheet_language_$it").fetchSemanticsNode().boundsInRoot.left }
            val before = pillLefts()
            onNodeWithTag("streams_sheet_language_RU").performClick()
            waitForIdle()
            assertEquals(before, pillLefts(), "Language pills moved after selecting one")
            onAllNodesWithTag("stream_sheet_row").assertCountEquals(1)
            onNodeWithText("Needs download · TB").assertIsDisplayed()
            assertEquals("RU", StreamBadgeSettingsRepository.uiState.value.sheetLanguage)

            onNodeWithTag("streams_sheet_language_all").performClick()
            waitForIdle()
            onAllNodesWithTag("stream_sheet_row").assertCountEquals(2)
            assertEquals(null, StreamBadgeSettingsRepository.uiState.value.sheetLanguage)
        } finally {
            StreamBadgeSettingsRepository.setSheetLanguage(null)
        }
    }

    @Test
    fun sheetHoldsRowPlaceholdersWhileTheFirstAddonAnswers() = runDesktopComposeUiTest(width = 1360, height = 850) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        setContent {
            AppEnvironment {
                StreamsSheetLayout(
                    isEpisode = false,
                    title = "Dune: Part Two",
                    background = null,
                    poster = null,
                    episodeThumbnail = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeTitle = null,
                    uiState = StreamsUiState(
                        requestToken = "movie::tt::null::null::true",
                        isAnyLoading = true,
                        groups = listOf(AddonStreamGroup("Torrentio", "addon:torrentio", emptyList(), isLoading = true)),
                    ),
                    debridEnabled = true,
                    resumePositionMs = null,
                    resumeProgressFraction = null,
                    onStreamSelected = { _, _, _ -> },
                    onStreamSecondaryClick = { _, _ -> },
                    onRefresh = {},
                    onBack = {},
                )
            }
        }
        waitForIdle()
        onNodeWithText("Searching addons…").assertIsDisplayed()
        onNodeWithText("Looking for sources…").assertIsDisplayed()
        onAllNodesWithTag("stream_sheet_row").assertCountEquals(0)
        onNodeWithTag("streams_sheet_play_best").assertDoesNotExist()
    }
}
