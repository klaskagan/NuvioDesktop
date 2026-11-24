package com.nuvio.app.features.streams

import com.nuvio.app.features.debrid.DebridStreamResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamSheetModelTest {
    private fun torrent(name: String, description: String, hash: String = name.hashCode().toString()) = StreamItem(
        name = name,
        description = description,
        infoHash = hash,
        addonName = "Torrentio",
        addonId = "addon:torrentio",
    )

    private fun cached(name: String, description: String) = torrent(name, description).copy(
        debridCacheStatus = StreamDebridCacheStatus(providerId = "realdebrid", providerName = "Real-Debrid", state = StreamDebridCacheState.CACHED),
    )

    private fun direct(name: String, description: String) = StreamItem(
        name = name,
        description = description,
        url = "https://example.test/${name.hashCode()}.mkv",
        addonName = "Comet",
        addonId = "addon:comet",
    )

    @Test
    fun decodesAReleaseIntoLabelsAndKeepsItsName() {
        val row = streamRowInfo(
            torrent(
                "Torrentio\n4k DV | HDR",
                "Severance.S02E03.2160p.ATVP.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX\n👤 412 💾 7.8 GB ⚙️ TorrentGalaxy",
            ),
        )
        assertEquals(DebridStreamResolution.P2160, row.resolution)
        assertEquals("WEB-DL", row.labels.first())
        assertTrue(row.labels.any { "Atmos" in it && "5.1" in it }, row.labels.toString())
        assertTrue(row.labels.any { "HEVC" in it || "H.265" in it || "H265" in it || "x265" in it }, row.labels.toString())
        assertEquals(StreamAvailability.Torrent, row.availability)
        assertEquals(412, row.seeds)
        assertEquals((7.8 * 1024 * 1024 * 1024).toLong(), row.sizeBytes)
        assertEquals("Severance.S02E03.2160p.ATVP.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX", row.rawName)
    }

    @Test
    fun readsSizeAndSeedsOnlyFromWhatTheAddonWrote() {
        assertEquals(940L * 1024 * 1024, streamSizeFromText("💾 940 MB"))
        assertEquals((6.59 * 1024 * 1024 * 1024).toLong(), streamSizeFromText("👤 17 💾 6,59 GB ⚙️ Rutracker"))
        assertNull(streamSizeFromText("Severance.S02E03.1080p"))
        assertEquals(17, streamSeedsFromText("👤 17 💾 6.59 GB"))
        assertNull(streamSeedsFromText("no numbers here"))
    }

    @Test
    fun availabilityFollowsCacheStateAndLinkType() {
        assertEquals(StreamAvailability.Instant, streamRowInfo(cached("A", "Show.1080p.WEB")).availability)
        assertEquals("RD", streamRowInfo(cached("A", "Show.1080p.WEB")).service)
        assertEquals(StreamAvailability.Direct, streamRowInfo(direct("B", "Show.720p.WEB")).availability)
        val checking = torrent("C", "Show.1080p").copy(
            debridCacheStatus = StreamDebridCacheStatus("realdebrid", "Real-Debrid", StreamDebridCacheState.CHECKING),
        )
        assertEquals(StreamAvailability.Checking, streamRowInfo(checking).availability)
    }

    @Test
    fun groupsByResolutionBestFirstWithSourcesThatStartNowLeading() {
        val rows = listOf(
            torrent("t1080", "Show.1080p.WEB 👤 900"),
            cached("c1080", "Show.1080p.WEB-DL"),
            torrent("t2160", "Show.2160p.WEB 👤 5"),
            direct("d720", "Show.720p.WEB"),
        ).map(::streamRowInfo)
        val grouped = groupStreamRows(rows, StreamSheetFilter())
        assertEquals(listOf(DebridStreamResolution.P2160, DebridStreamResolution.P1080, DebridStreamResolution.P720), grouped.map { it.first })
        assertEquals(listOf("c1080", "t1080"), grouped[1].second.map { it.stream.name })
    }

    @Test
    fun filtersNarrowTheListAndCanEmptyIt() {
        val rows = listOf(torrent("t1080", "Show.1080p.WEB"), cached("c2160", "Show.2160p.WEB-DL")).map(::streamRowInfo)
        val instant = groupStreamRows(rows, StreamSheetFilter(instantOnly = true))
        assertEquals(listOf("c2160"), instant.flatMap { it.second }.map { it.stream.name })
        val p1080 = groupStreamRows(rows, StreamSheetFilter(resolution = DebridStreamResolution.P1080))
        assertEquals(listOf("t1080"), p1080.flatMap { it.second }.map { it.stream.name })
        assertTrue(groupStreamRows(rows, StreamSheetFilter(resolution = DebridStreamResolution.P1080, instantOnly = true)).isEmpty())
    }

    @Test
    fun sortBySizeAndSeedsOrdersInsideAGroup() {
        val rows = listOf(
            torrent("small", "Show.1080p 👤 900 💾 1.4 GB"),
            torrent("big", "Show.1080p 👤 20 💾 3.9 GB"),
        ).map(::streamRowInfo)
        assertEquals(listOf("big", "small"), groupStreamRows(rows, StreamSheetFilter(sort = StreamSheetSort.Size)).single().second.map { it.stream.name })
        assertEquals(listOf("small", "big"), groupStreamRows(rows, StreamSheetFilter(sort = StreamSheetSort.Seeds)).single().second.map { it.stream.name })
    }

    @Test
    fun quickPicksPreferASourceThatStartsNowOverAHigherResolutionTorrent() {
        val rows = listOf(
            torrent("t2160", "Show.2160p.WEB 👤 400 💾 7.8 GB"),
            cached("c2160", "Show.2160p.WEB-DL 💾 6.1 GB"),
            cached("c1080", "Show.1080p.WEB-DL 💾 1.4 GB"),
            cached("c1080big", "Show.1080p.WEB-DL 💾 3.9 GB"),
        ).map(::streamRowInfo)
        val picks = streamQuickPicks(rows)
        assertEquals("c2160", picks.best?.stream?.name)
        assertEquals("c1080", picks.faster?.stream?.name)
        assertNull(picks.server)
    }

    @Test
    fun fasterStartIsTheNextResolutionDownNotTheSmallestFile() {
        val rows = listOf(
            cached("c2160", "Show.2160p.WEB-DL 💾 10.8 GB"),
            cached("c1080", "Show.1080p.WEB-DL 💾 5.6 GB"),
            cached("c480", "Show.480p.x264 💾 326 MB"),
        ).map(::streamRowInfo)
        assertEquals("c1080", streamQuickPicks(rows).faster?.stream?.name)
        // With no lower resolution, a much smaller file at the same one is the faster start.
        val same = listOf(cached("big", "Show.1080p.REMUX 💾 25 GB"), cached("small", "Show.1080p.WEB 💾 3 GB")).map(::streamRowInfo)
        assertEquals("small", streamQuickPicks(same).faster?.stream?.name)
    }

    @Test
    fun quickPicksNeverRepeatASourceAndSkipWhatCannotPlay() {
        val only = listOf(cached("c1080", "Show.1080p.WEB-DL 💾 1.4 GB")).map(::streamRowInfo)
        val picks = streamQuickPicks(only)
        assertEquals("c1080", picks.best?.stream?.name)
        assertNull(picks.faster)
        val none = streamQuickPicks(only) { false }
        assertNull(none.best)
        assertNull(streamQuickPicks(emptyList()).best)
    }

    @Test
    fun aPickedSourceIsHandedOverOnceAndOnlyForItsTitle() {
        val stream = direct("picked", "Show.1080p")
        StreamPreselection.set("tt1:1:1", stream)
        assertNull(StreamPreselection.take("tt2:1:1"))
        assertNull(StreamPreselection.take("tt1:1:1"), "a pick for another title is dropped, not kept for later")
        StreamPreselection.set("tt1:1:1", stream)
        assertEquals(stream, StreamPreselection.take("tt1:1:1"))
        assertNull(StreamPreselection.take("tt1:1:1"))
    }

    // ---- Rows as Torrentio sends them for a TorBox account (from a real "Ready or Not" list).
    private fun torbox(marker: String, quality: String, description: String) = StreamItem(
        name = "[$marker] Torrentio\n$quality",
        description = description,
        url = "https://torrentio.example/resolve/${description.hashCode()}",
        addonName = "Torrentio TB",
        addonId = "addon:torrentio",
    )

    private val readyUhd = torbox(
        "TB+", "4k",
        "Ready.or.Not.2019.2160p.MA.WEB-DL.H.265.DDP5.1-PandaQT SDR 4k UHD [GoodFilms]\n👤 17 💾 16.72 GB ⚙️ ThePirateBay",
    )
    private val readyRuHybrid = torbox(
        "TB+", "4k DV | HDR",
        "Я иду искать / Ready or Not [2019 WEB-DL 2160p HDR10 Dolby Vision] [Hybrid] Dub + DVO + AVO + Ukr + Original (Eng) + Sub (Rus Ukr Eng)\n" +
            "👤 29 💾 18.44 GB ⚙️ Rutracker\n🇬🇧 / 🇷🇺 / 🇺🇦",
    )
    private val downloadRuBdrip = torbox(
        "TB download", "BDRip",
        "Я иду искать / Ready or Not [2019 BDRip-AVC] Dub (Rus) + Dub (Ukr) + Original (Eng) + Sub (Rus Ukr Eng)\n" +
            "👤 36 💾 2.19 GB ⚙️ Rutracker\n🇬🇧 / 🇷🇺 / 🇺🇦",
    )

    @Test
    fun addonMarkersDecideWhetherASourceStartsNowOrNeedsDownloading() {
        val cachedRow = streamRowInfo(readyUhd)
        assertEquals(StreamAvailability.Instant, cachedRow.availability)
        assertEquals("TB", cachedRow.service)
        assertTrue(cachedRow.startsNow)

        val download = streamRowInfo(downloadRuBdrip)
        assertEquals(StreamAvailability.Download, download.availability, "a link alone does not make it ready")
        assertEquals("TB", download.service)
        assertEquals(36, download.seeds)
        assertEquals(false, download.startsNow)
    }

    @Test
    fun flagsGiveTheAudioLanguages() {
        assertEquals(listOf("EN", "RU", "UK"), streamRowInfo(downloadRuBdrip).languages)
        assertEquals(listOf("EN", "RU", "UK"), streamLanguagesFromFlags("🇬🇧 / 🇷🇺 / 🇺🇦"))
        assertEquals(listOf("RU"), streamLanguagesFromFlags("👤 3 💾 5.18 GB ⚙️ Rutor\n🇷🇺"))
        assertEquals(emptyList(), streamLanguagesFromFlags("👤 17 💾 16.72 GB ⚙️ ThePirateBay"))
    }

    @Test
    fun descriptionBecomesVoiceOverAndSubtitleTags() {
        assertEquals(
            listOf("RU Dub", "UK Dub", "EN Original", "Subs RU UK EN"),
            streamAudioTags("Я иду искать / Ready or Not [2019 BDRip-AVC] Dub (Rus) + Dub (Ukr) + Original (Eng) + Sub (Rus Ukr Eng)"),
        )
        // A kind without a language stays bare; a lone language between plus signs is its own tag.
        assertEquals(
            listOf("Dub", "DVO", "AVO", "EN Original", "UK", "Subs RU UK EN"),
            streamAudioTags("Я иду искать / Ready or Not [2019 WEB-DL 2160p HDR10 Dolby Vision] [Hybrid] Dub + DVO + AVO + Ukr + Original (Eng) + Sub (Rus Ukr Eng)"),
        )
        // A voice-over studio in brackets is not a language.
        assertEquals(
            listOf("MVO", "Original", "UK", "Subs RU UK EN"),
            streamAudioTags("Друзья / Friends [1994-1995 HEVC SDR WEB-DL 1440p] 3 x MVO (Нота TrueDubbing Studio TVShows) + Ukr (1+1) + Original + Sub (2 x Rus Ukr Eng)"),
        )
        assertEquals(listOf("Lektor", "Dub"), streamAudioTags("Paczka filmow nr.15 na Telegram | 1900 - 2026 | Dubbing Lektor\nReady.or.Not.2019.Lektor.mp4\nDubbed"))
    }

    @Test
    fun shorthandFileNamesAndStatsNeverBecomeTags() {
        assertEquals(emptyList(), streamAudioTags("Я иду искать / Ready Or Not (2019) WEBRip 2160p | 4K | HDR | D A\n👤 3 💾 21.42 GB ⚙️ Rutor"))
        assertEquals(emptyList(), streamAudioTags("Ready.or.Not.2019.2160p.MA.WEB-DL.H.265.DDP5.1-PandaQT SDR 4k UHD [GoodFilms]"))
        assertEquals(emptyList(), streamAudioTags("Severance.S02E03.Dub.AVO.1080p.mkv"))
    }

    @Test
    fun preferredLanguagePicksTheRussianSourceThatStartsNow() {
        val rows = listOf(readyUhd, readyRuHybrid, downloadRuBdrip).map(::streamRowInfo)
        // Without a preference the two instant 4K sources tie on language; with Russian, the dubbed one leads.
        assertEquals(readyRuHybrid, streamQuickPicks(rows, preferredLanguages = listOf("RU")).best?.stream)
        // A Russian source that still has to download is never picked over one that starts now.
        val onlyDownload = listOf(readyUhd, downloadRuBdrip).map(::streamRowInfo)
        assertEquals(readyUhd, streamQuickPicks(onlyDownload, preferredLanguages = listOf("RU")).best?.stream)
    }

    @Test
    fun preferredLanguageBeatsAHigherResolutionWithoutIt() {
        val ru1080 = torbox("TB+", "1080p", "Show [WEB-DL 1080p] MVO + Original (Eng)\n👤 5 💾 6.4 GB\n🇬🇧 / 🇷🇺")
        val rows = listOf(readyUhd, ru1080).map(::streamRowInfo)
        assertEquals(ru1080, streamQuickPicks(rows, preferredLanguages = listOf("RU")).best?.stream)
        assertEquals(readyUhd, streamQuickPicks(rows).best?.stream)
    }

    @Test
    fun languageFilterAndInstantOnlyNarrowTheList() {
        val rows = listOf(readyUhd, readyRuHybrid, downloadRuBdrip).map(::streamRowInfo)
        val russian = groupStreamRows(rows, StreamSheetFilter(language = "RU")).flatMap { it.second }.map { it.stream }
        assertEquals(setOf(readyRuHybrid, downloadRuBdrip), russian.toSet())
        val russianNow = groupStreamRows(rows, StreamSheetFilter(language = "RU", instantOnly = true)).flatMap { it.second }.map { it.stream }
        assertEquals(listOf(readyRuHybrid), russianNow)
        assertEquals(listOf("RU", "EN", "UK"), streamLanguageChoices(rows, preferred = listOf("ru")))
    }

    @Test
    fun formatsSizes() {
        assertEquals("7.8 GB", formatStreamSize((7.8 * 1024 * 1024 * 1024).toLong() + 1024))
        assertEquals("4 GB", formatStreamSize(4L * 1024 * 1024 * 1024))
        assertEquals("940 MB", formatStreamSize(940L * 1024 * 1024))
    }

    @Test
    fun aBdRipIsLabelledBdRipAndSitsAtItsStatedResolution() {
        val row = streamRowInfo(torrent("Torrentio\n4k", "Dune.Part.Two.2024.UHD.BDRip.1080p.HDR.DTS-HD.MA.7.1.x265\n👤 90 💾 14.2 GB"))
        assertEquals("BDRip", row.labels.first())
        assertEquals(DebridStreamResolution.P1080, row.resolution)
        val remux = streamRowInfo(torrent("Torrentio\n4k", "Dune.Part.Two.2024.2160p.UHD.BluRay.REMUX.DV.HDR.TrueHD.7.1.Atmos\n💾 74 GB"))
        assertEquals("Remux", remux.labels.first())
        assertEquals(DebridStreamResolution.P2160, remux.resolution)
    }

    @Test
    fun aBareLanguageTagDoesNotRepeatTheLanguageChip() {
        val row = streamRowInfo(
            torrent(
                "Torrentio\n4k",
                "Ready or Not [2019 WEB-DL 2160p] Dub + Ukr + Original (Eng)\n🇬🇧 / 🇷🇺 / 🇺🇦\n💾 9 GB",
            ),
        )
        assertTrue("UK" in row.languages, row.languages.toString())
        assertTrue("UK" !in row.audioTags, row.audioTags.toString())
        assertTrue("Dub" in row.audioTags, row.audioTags.toString())
    }

    @Test
    fun exactCopiesFromOneAddonMergeIntoTheBestSeededRow() {
        val a = torrent("Torrentio\n1080p", "Show.S01E01.1080p.WEB.h264\n👤 12 💾 1.4 GB", hash = "abc")
        val b = torrent("Torrentio\n1080p", "Show.S01E01.1080p.WEB.h264\n👤 80 💾 1.4 GB", hash = "abc")
        val c = torrent("Torrentio\n1080p", "Show.S01E01.1080p.WEB.h264\n👤 5 💾 1.4 GB", hash = "abc")
        val otherAddon = b.copy(addonId = "addon:other", addonName = "Other")
        val merged = mergeDuplicateStreams(listOf(a, b, c, otherAddon).map(::streamRowInfo))
        assertEquals(2, merged.size)
        val kept = merged.first { it.stream.addonId == "addon:torrentio" }
        assertEquals(3, kept.duplicateCount)
        assertEquals(80, kept.seeds)
        assertEquals(1, merged.first { it.stream.addonId == "addon:other" }.duplicateCount)
    }

    @Test
    fun differentFilesWithTheSameNameGetSomethingToTellThemApart() {
        val small = torrent("Torrentio\n1080p", "Show.S01E01.1080p.WEB.h264\n👤 12 💾 1.4 GB", hash = "one").copy(sourceName = "TorrentGalaxy")
        val large = torrent("Torrentio\n1080p", "Show.S01E01.1080p.WEB.h264\n👤 40 💾 2.1 GB", hash = "two").copy(sourceName = "1337x")
        val merged = mergeDuplicateStreams(listOf(small, large).map(::streamRowInfo))
        assertEquals(listOf("TorrentGalaxy", "1337x"), merged.map { it.distinguisher })
        val unique = mergeDuplicateStreams(listOf(streamRowInfo(direct("Comet", "Other.Show.S01E01.720p.mkv"))))
        assertNull(unique.single().distinguisher)
    }
}
