package com.nuvio.app.features.library

import com.nuvio.app.features.cloud.CloudLibraryFile
import com.nuvio.app.features.cloud.CloudLibraryItem
import com.nuvio.app.features.cloud.CloudLibraryItemType
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudLibraryPresentationTest {
    private fun item(name: String, vararg files: String) = CloudLibraryItem(
        providerId = "p", providerName = "P", id = name, type = CloudLibraryItemType.Torrent, name = name,
        files = files.map { CloudLibraryFile(it, it) },
    )

    @Test
    fun kindComesFromNames() {
        assertEquals(CloudMediaKind.Show, item("Severance S02 (2025) 1080p", "Severance.S02E01.mkv", "Severance.S02E02.mkv").mediaKind())
        assertEquals(CloudMediaKind.Show, item("Shogun.2024.S01.2160p.DSNP.WEB-DL", "a.mkv").mediaKind())
        assertEquals(CloudMediaKind.Movie, item("Dune.Part.Two.2024.2160p.WEB-DL.mkv", "Dune.Part.Two.2024.2160p.WEB-DL.mkv").mediaKind())
        assertEquals(CloudMediaKind.Other, item("The.Holdovers.Extras", "notes.txt", "cover.jpg").mediaKind())
    }

    @Test
    fun titlesDropReleaseNoise() {
        assertEquals("Severance S02", cloudDisplayTitle("Severance S02 (2025) 1080p"))
        assertEquals("Dune Part Two", cloudDisplayTitle("Dune.Part.Two.2024.2160p.WEB-DL.DDP5.1.Atmos.mkv"))
        assertEquals("Past Lives", cloudDisplayTitle("Past.Lives.2023.1080p.BluRay.x265"))
    }

    @Test
    fun episodeLabelsAndOrder() {
        assertEquals("S02E01 · Hello Ms Cobel", cloudFileLabel(CloudLibraryFile("1", "Severance.S02E01.Hello.Ms.Cobel.1080p.mkv")))
        assertEquals("S02E03", cloudFileLabel(CloudLibraryFile("3", "Severance.S02E03.1080p.WEB.mkv")))
        val files = listOf("Show.S01E10.mkv", "Show.S01E02.mkv", "Sample.mkv").map { CloudLibraryFile(it, it) }
        assertEquals(listOf("Show.S01E02.mkv", "Show.S01E10.mkv", "Sample.mkv"), files.sortedBy(::cloudFileSortKey).map { it.name })
    }
}
