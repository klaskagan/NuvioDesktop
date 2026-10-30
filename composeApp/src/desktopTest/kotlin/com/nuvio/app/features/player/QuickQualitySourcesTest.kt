package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.isSelectableForPlayback
import kotlin.test.*

class QuickQualitySourcesTest {
    private fun source(quality: String, id: String) = StreamItem(name = quality, title = "Comet RUS fixture", url = "https://example.test/$id", addonId = "comet", addonName = "Comet")

    @Test fun torrentioDirectLinksNeedNoNativeDebridAndIncludeDecodedUnlabelledCurrent() {
        val current = source("[TB+] Torrentio WEB-DL", "sd").copy(addonId = "torrentio", addonName = "Torrentio TB")
        val fourK = source("[TB+] Torrentio 4k DV | HDR", "4k")
        val fullHd = source("[TB+] Torrentio 1080p", "1080")
        assertTrue(current.isSelectableForPlayback(false))
        assertEquals(480, standardQualityHeight(1024, 512))
        val choices = quickQualityChoices(listOf(fourK, fourK, fullHd, current), current, 480)
        assertEquals(listOf(2160, 1080, 480), choices.map { it.height })
        assertEquals(current.url, choices.last().url)
    }
    @Test fun prefersReadyTorrentioReleaseAndDisablesDownloadOnlyTier() {
        val pending = source("[TB download] Torrentio 1080p", "pending")
        val ready = source("[TB+] Torrentio 1080p", "ready")
        assertEquals(ready, quickQualityChoices(listOf(pending, ready)).single().stream)
        assertNotNull(quickQualityChoices(listOf(pending)).single().disabledReason)
        assertNull(quickQualityChoices(listOf(pending), pending).single().disabledReason)
    }
    @Test fun deduplicatesCachedSourcesInDescendingQualityOrder() {
        val first4k = source("2160p TB Instant", "first4k")
        val first1080 = source("1080p TB Instant", "first1080")
        val sources = listOf(source("720p", "720"), first4k, source("2160p", "duplicate4k"), first1080, source("1080p", "duplicate1080"))
        val choices = quickQualityChoices(sources)
        assertEquals(listOf(2160, 1080, 720), choices.map { it.height })
        assertEquals(listOf(first4k, first1080, sources.first()), choices.map { it.stream })
        assertTrue(choices.all { it.disabledReason == null })
    }
    @Test fun currentSourceWinsItsOwnTierWithoutChangingProviderOrderElsewhere() {
        val first = source("1080p", "first")
        val current = source("1080p", "current")
        val resolvedCurrent = current.copy(description = "Already resolved playback source")
        assertEquals(resolvedCurrent, quickQualityChoices(listOf(first, current), resolvedCurrent).single().stream)
    }
    @Test fun croppedFramesUseStandardTier() {
        assertEquals(720, standardQualityHeight(1280, 688))
        assertEquals(1080, standardQualityHeight(1920, 800))
        assertEquals(2160, standardQualityHeight(3840, 1600))
        assertNull(standardQualityHeight(0, 0))
    }
    @Test fun unknownMetadataIsOmittedAndAliasesAreRecognized() {
        assertTrue(quickQualityChoices(listOf(source("Unknown", "unknown"))).isEmpty())
        assertEquals(2160, reportedStreamQuality(source("4K UHD", "4k")))
        assertEquals(720, reportedStreamQuality(source("1280x688", "cropped")))
    }
    @Test fun playbackCanPrimeTheFullCacheWithoutRefetchingOrLosingDuplicates() {
        PlayerStreamsRepository.clearAll()
        try {
            val sources = listOf(source("1080p", "one"), source("1080p", "two"))
            val selection = com.nuvio.app.features.streams.StreamsUiState(
                requestToken = com.nuvio.app.features.streams.StreamsRepository.requestToken("movie", "fixture", manualSelection = true),
                groups = listOf(com.nuvio.app.features.streams.AddonStreamGroup("comet", "Comet", streams = sources)),
            )
            assertTrue(PlayerStreamsRepository.primeSourcesFromSelection("movie", "fixture", null, null, selection))
            assertEquals(sources, PlayerStreamsRepository.sourceState.value.groups.single().streams)
            assertFalse(PlayerStreamsRepository.primeSourcesFromSelection("movie", "different-title", null, null, selection))
            // Reusing the cached request needs no stream-picker state at all.
            assertTrue(PlayerStreamsRepository.primeSourcesFromSelection("movie", "fixture", null, null,
                com.nuvio.app.features.streams.StreamsUiState()))
        } finally { PlayerStreamsRepository.clearAll() }
    }
    @Test fun deduplicationDoesNotChangeTheManualSourceList() {
        val sources = listOf(source("1080p", "one"), source("1080p", "two"))
        assertEquals(1, quickQualityChoices(sources).size)
        assertEquals(2, sources.size)
    }
}
