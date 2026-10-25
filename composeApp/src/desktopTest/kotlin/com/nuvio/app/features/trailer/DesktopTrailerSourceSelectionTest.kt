package com.nuvio.app.features.trailer

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopTrailerSourceSelectionTest {
    private fun track(audio: Boolean = false) = StreamCandidate(
        client = "fixture", priority = 0, url = "https://example.invalid/${if (audio) "audio.m4a" else "video.mp4"}",
        score = 1.0, bitrate = 100_000, mimeType = if (audio) "audio/mp4" else "video/mp4",
        hasN = false, height = if (audio) 0 else 1080, fps = 24, ext = if (audio) "m4a" else "mp4",
    )
    private val manifest = ManifestCandidate(
        "fixture", 0, "https://example.invalid/master.m3u8", "https://example.invalid/video-only.m3u8", 1080, 100_000,
    )

    @Test fun equalQualityPrefersSeparateTracksInsteadOfTheStalledHlsMaster() = runBlocking {
        val source = TrailerExtractionPlatform.buildPlaybackSource(manifest, null, track(), track(audio = true))
        assertEquals(track().url, source?.videoUrl)
        assertEquals(track(audio = true).url, source?.audioUrl)
    }

    @Test fun hlsRemainsAvailableWhenSeparateTracksAreMissing() = runBlocking {
        val source = TrailerExtractionPlatform.buildPlaybackSource(manifest, null, null, null)
        assertEquals(manifest.manifestUrl, source?.videoUrl)
        assertNull(source?.audioUrl)
    }
}
