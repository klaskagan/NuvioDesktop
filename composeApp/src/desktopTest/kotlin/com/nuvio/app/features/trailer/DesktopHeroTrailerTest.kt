package com.nuvio.app.features.trailer

import com.nuvio.app.features.details.CornerTrailerStyle
import com.nuvio.app.features.details.DesktopHeroTrailerAttempt
import com.nuvio.app.features.details.DesktopHeroTrailerEnd
import com.nuvio.app.features.details.MetaTrailer
import com.nuvio.app.features.details.rankDesktopHeroTrailers
import com.nuvio.app.features.details.startDesktopHeroTrailer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopHeroTrailerTest {
    @Test fun uploadResolutionPrecedesCategoryAndOfficialStatus() {
        val uploads = listOf(
            trailer("hd", 720),
            trailer("official", 1080, official = true),
            trailer("qhd", 1440),
            trailer("uhd", 2160, type = "Teaser"),
            trailer("fan", 1080, publishedAt = "2026-01-01"),
            trailer("new-official", 1080, official = true, publishedAt = "2025-01-01"),
        )
        assertEquals(
            listOf("uhd", "qhd", "new-official", "official", "fan", "hd"),
            rankDesktopHeroTrailers(uploads).map { it.id },
        )
    }

    @Test fun pinnedPlaylistKeepsOnlyTheSelectedRenditionAndItsAudio() {
        val master = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="english",NAME="English, Original",DEFAULT=YES,URI="../audio/main.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="other",NAME="Other",URI="https://cdn.example/other.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,AUDIO="other"
            1080.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=2560x1072,AUDIO="english"
            1440.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=10000000,RESOLUTION=3840x1600,AUDIO="english",CODECS="hvc1,mp4a"
            //cdn.example/2160.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=1280x534,AUDIO="english"
            /720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=854x480,AUDIO="other"
            480.m3u8
        """.trimIndent()
        val renditions = rankHeroRenditions(parseHeroHlsRenditions("https://cdn.example/main/video/master.m3u8?token=1", master))
        assertEquals(listOf(3840, 2560, 1920, 1280, 854), renditions.map { it.width })
        val pinned = checkNotNull(renditions.first().pinnedPlaylist)
        assertEquals(
            listOf(
                "#EXTM3U",
                "#EXT-X-VERSION:6",
                """#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="english",NAME="English, Original",DEFAULT=YES,URI="https://cdn.example/main/audio/main.m3u8"""",
                """#EXT-X-STREAM-INF:BANDWIDTH=10000000,RESOLUTION=3840x1600,AUDIO="english",CODECS="hvc1,mp4a"""",
                "https://cdn.example/2160.m3u8",
            ),
            pinned.lineSequence().filter(String::isNotEmpty).toList(),
        )
        DesktopHeroPlaylist(pinned).use { playlist ->
            assertEquals(pinned, URI(playlist.url).toURL().readText())
        }
    }

    @Test fun failedAndTimedOutRenditionsFallBackBeforeTheNextUpload() = runTest {
        val attempts = mutableListOf<String>()
        val started = startDesktopHeroTrailer(
            trailers = listOf(trailer("first", 2160), trailer("second", 1080)),
            resolve = { upload ->
                if (upload.id == "first") {
                    listOf(HeroTrailerRendition("4K"), HeroTrailerRendition("1440p"))
                } else {
                    listOf(HeroTrailerRendition("1080p"), HeroTrailerRendition("720p"))
                }
            },
            tryRendition = {
                val url = it.rendition.videoUrl
                attempts.add(url)
                if (url == "4K") awaitCancellation()
                url == "1080p"
            },
        )
        assertTrue(started)
        assertEquals(listOf("4K", "1440p", "1080p"), attempts)
        assertFalse(startDesktopHeroTrailer(listOf(trailer("missing", 0)), { emptyList() }) { error("Unexpected attempt") })
    }

    @Test fun onlyAReadyRenditionCanReportCompletionAndLateEventsAreIgnored() = runTest {
        val source = trailer("trailer", 1080)
        val rendition = HeroTrailerRendition("1080p")
        val failedStartup = DesktopHeroTrailerAttempt(source, rendition)
        assertNull(failedStartup.finish(DesktopHeroTrailerEnd.Failed))
        assertFalse(failedStartup.ready.await())
        assertFalse(failedStartup.markReady())

        val original = DesktopHeroTrailerAttempt(source, rendition)
        assertTrue(original.markReady())
        assertEquals(DesktopHeroTrailerEnd.Completed, original.finish(DesktopHeroTrailerEnd.Completed))

        val replay = DesktopHeroTrailerAttempt(source, rendition)
        assertNull(original.finish(DesktopHeroTrailerEnd.Failed))
        assertFalse(replay.ready.isCompleted)
        assertTrue(replay.markReady())
        assertEquals(DesktopHeroTrailerEnd.Failed, replay.finish(DesktopHeroTrailerEnd.Failed))
        assertNull(replay.finish(DesktopHeroTrailerEnd.Completed))
    }

    @Test fun unknownCornerTrailerStyleFallsBackToTvStyle() {
        assertEquals(CornerTrailerStyle.TvStyle, CornerTrailerStyle.fromStoredValue("unknown"))
    }

    private fun trailer(
        id: String,
        size: Int,
        type: String = "Trailer",
        official: Boolean = false,
        publishedAt: String? = null,
    ) = MetaTrailer(id = id, key = id, name = id, type = type, site = "YouTube", size = size, official = official, publishedAt = publishedAt)
}
