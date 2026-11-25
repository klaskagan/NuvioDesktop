package com.nuvio.app.features.streams

import kotlin.test.assertTrue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import com.nuvio.app.AppEnvironment
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /** The status row, the resume switch, a row opened in place, merged copies and download mode. */
    @Test
    fun aPercentageResumeShowsItsTimeFromTheRuntime() = runDesktopComposeUiTest(width = 1360, height = 850) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
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
                    // Trakt progress: a percentage only; the time comes from the runtime.
                    resumePositionMs = null,
                    resumeProgressFraction = 0.38f,
                    resumeDisplayMs = 1_102_000,
                    onStreamSelected = { _, _, _ -> },
                    onStreamSelectedFromStart = {},
                    onStreamSecondaryClick = { _, _ -> },
                    onRefresh = {},
                    onBack = {},
                    actions = StreamSheetActions(onDownload = {}, onCopyLink = {}),
                )
            }
        }
        waitForIdle()
        onNodeWithText("Resume from 18:22", useUnmergedTree = true).assertIsDisplayed()

        // Hovering a row fades its Download / Copy link in without moving anything (they keep their space).
        val row = onAllNodesWithTag("stream_sheet_row")[0]
        val before = row.getUnclippedBoundsInRoot()
        val sizeBefore = onAllNodesWithTag("stream_sheet_row_size", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        row.performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(400)
        waitForIdle()
        assertEquals(before, row.getUnclippedBoundsInRoot())
        assertEquals(sizeBefore, onAllNodesWithTag("stream_sheet_row_size", useUnmergedTree = true)[0].getUnclippedBoundsInRoot())
    }

    @Test
    fun sheetShowsAddonStatusResumeSwitchOpenRowsAndDownloadMode() = runDesktopComposeUiTest(width = 1360, height = 850) {
        check(System.getProperty("nuvio.test.isolatedHome") == "true")
        val withStatus = state.copy(
            groups = state.groups.mapIndexed { index, group ->
                // Torrentio lists its 4K release twice.
                if (index == 0) group.copy(streams = group.streams + group.streams.first()) else group
            } + listOf(
                AddonStreamGroup("MediaFusion", "addon:mediafusion", emptyList(), isLoading = true),
                AddonStreamGroup("Jackettio", "addon:jackettio", emptyList()),
                AddonStreamGroup("Peerflix", "addon:peerflix", emptyList(), error = "timeout"),
            ),
        )
        val resumed = mutableListOf<Pair<String?, Long?>>()
        val fromStart = mutableListOf<String?>()
        val downloaded = mutableListOf<String?>()
        var downloadMode by mutableStateOf(false)
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
                    uiState = withStatus,
                    debridEnabled = true,
                    resumePositionMs = 1_102_000,
                    resumeProgressFraction = 0.38f,
                    onStreamSelected = { stream, position, _ -> resumed += stream.name to position },
                    onStreamSelectedFromStart = { fromStart += it.name },
                    onStreamSecondaryClick = { _, _ -> },
                    onRefresh = {},
                    onBack = {},
                    actions = StreamSheetActions(onDownload = { downloaded += it.name }, onCopyLink = {}),
                    downloadMode = downloadMode,
                )
            }
        }
        waitForIdle()
        // Every addon shows how its search went.
        onNodeWithTag("streams_sheet_addon_searching", useUnmergedTree = true).assertExists()
        onNodeWithText("· no results", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("· failed", substring = true, useUnmergedTree = true).assertExists()
        // The repeated 4K release is one row marked ×2.
        onAllNodesWithTag("stream_sheet_row").assertCountEquals(4)
        onNodeWithText("×2", substring = true, useUnmergedTree = true).assertExists()

        // Resume is on: Best match resumes. Off: it plays from the start.
        onNodeWithText("Resume from 18:22", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("streams_sheet_play_best").performClick()
        assertEquals(listOf<Pair<String?, Long?>>("best4k" to 1_102_000L), resumed)
        onNodeWithTag("streams_sheet_resume_toggle").performClick()
        waitForIdle()
        onNodeWithText("Plays from the start", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("streams_sheet_play_best").performClick()
        assertEquals(listOf<String?>("best4k"), fromStart)
        onNodeWithTag("streams_sheet_resume_toggle").performClick()
        waitForIdle()

        // Clicking a row body opens it in place instead of playing.
        onAllNodesWithTag("stream_sheet_row")[0].performClick()
        waitForIdle()
        onNodeWithTag("stream_sheet_row_details", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, resumed.size)
        onNodeWithTag("stream_row_play_start").performClick()
        assertEquals(2, fromStart.size)
        onNodeWithTag("stream_row_download_full").performClick()
        assertEquals(1, downloaded.size)
        // Esc closes the open row first and leaves the sheet up (frame "Sources · row clicked").
        runOnIdle { assertEquals(true, com.nuvio.app.AppKeyboardShortcuts.overlayEscape?.invoke()) }
        waitForIdle()
        onNodeWithTag("stream_sheet_row_details", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("streams_sheet_play_best").assertExists()
        runOnIdle { assertEquals(null, com.nuvio.app.AppKeyboardShortcuts.overlayEscape) }

        // Opened from Download: the sheet says so, and its primary actions download.
        runOnIdle { downloadMode = true }
        waitForIdle()
        // The header names the episode, the card where the file goes, and the button its quality (and size).
        onNodeWithTag("streams_sheet_title").assertTextEquals("Download S2 E3")
        onNodeWithTag("streams_sheet_download_card").assertIsDisplayed()
        assertTrue(onNodeWithTag("streams_sheet_download_folder", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString().endsWith("watch offline"))
        onNodeWithTag("streams_sheet_resume_toggle").assertDoesNotExist()
        onNodeWithText("Download 4K", substring = true).assertTextContains("Download 4K", substring = true)
        onNodeWithTag("streams_sheet_play_best").performClick()
        assertEquals(listOf<String?>("best4k", "best4k"), downloaded)
        assertEquals(1, resumed.size)
    }
}
